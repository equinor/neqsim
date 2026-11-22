"""The ``calibrate`` stage: track uncertain model parameters from measurements every cycle.

Plan block::

    calibration:
      method: enif            # or enkf
      members: 40
      seed: 7
      mda_steps: 1            # >1: ES-MDA, re-linearises a nonlinear model (costs one predict per step)
      gate_sigma: 4.0         # reject a measurement whose normalised innovation exceeds this
      inflation: 1.02         # posterior anomaly inflation against ensemble collapse
      min_members: 20         # fewest members the model must solve for; failed members are dropped and
                              # the ensemble replenished from the survivors (default: half)
      reinit_after: 3         # cycles with every measurement rejected before the prior is re-drawn
      reinit_scale: 2.0       # ... with this factor on the prior std, centred on the current mean
      parameters:             # prior and bounds; walk_std = per-cycle random walk for moving parameters;
        fouling_20A: {initial: 0.0, std: 0.2, lo: 0.0, hi: 1.0, walk_std: 0.01, neighbours: [eta_A]}
        eta_A:       {initial: 0.78, std: 0.02, lo: 0.6, hi: 0.9}   # neighbours = conditionally dependent (enif)
      observations:           # KPI holding the measurement; depends_on = parameters it responds to
        T_20A_meas: {std: 0.5, depends_on: [fouling_20A]}
        power_A_MW: {std: auto, min_std: 0.1, depends_on: [eta_A], measured: power_A_meas_MW}
      predict: {file: continuous/stages/predict.py, function: predict}

``std: auto`` estimates the measurement noise from the KPI's own history
(``continuous/kpi_history.csv``, robust MAD of first differences) with ``min_std`` as a
floor; until eight values exist the observation is skipped and reported as ``std_pending``.
``depends_on`` sparsifies the observation operator for ``enif`` and localises the gain for
``enkf``.

``predict(ctx, members)`` receives a list of ``{parameter: value}`` dicts and returns one
``{observation: value}`` dict per member (run NeqSim inside; batch so the model is built
once). A member the model cannot solve may be returned as ``None`` or with non-finite
values; it is dropped for the cycle and reported under ``failed_members``. The posterior
ensemble lives in ``continuous/calibration_state.json``; each cycle
writes ``calibration.json``, publishes ``<parameter>_mean`` / ``<parameter>_std``,
``<observation>_resid`` / ``<observation>_z``, ``calibration_rank``,
``calibration_rejected`` and ``calibration_failed_members`` KPIs, exposes ``ctx.calibrated``
(posterior means) to later script stages and raises ``calibration:unidentifiable`` once when
the measurements stop determining the parameters, ``calibration:all_rejected`` when every
measurement failed the innovation gate, and ``calibration:reinitialised`` when the prior was
re-drawn after ``reinit_after`` such cycles. Place it after ``kpis`` and before the model
script.
"""

import csv
import importlib.util
import os

import numpy as np

from .contracts import StageResult, register
from .inference import (ensemble_update, identifiability, innovation_gate, mda_std, noise_std_from_series,
                        replenish)
from .plan import read_json, write_json

STATE_FILE = "calibration_state.json"


def _history_values(state_dir, kpi, max_points=200):
    path = os.path.join(state_dir, "kpi_history.csv")
    if not os.path.exists(path):
        return []
    values = []
    with open(path, "r", encoding="utf-8", newline="") as f:
        for row in csv.DictReader(f):
            if row.get("kpi") == kpi:
                try:
                    values.append(float(row.get("value")))
                except (TypeError, ValueError):
                    continue
    return values[-int(max_points):]


def _observation_std(ctx, obs, item):
    """Return (std, source) or (None, reason) for one observation."""
    spec = (item or {}).get("std", 1.0)
    if isinstance(spec, (int, float)) and not isinstance(spec, bool):
        return (float(spec), "plan") if float(spec) > 0 else (None, "std_not_positive")
    if str(spec).lower() == "auto":
        est = noise_std_from_series(_history_values(ctx.state_dir, (item or {}).get("measured", obs)),
                                    min_std=float((item or {}).get("min_std", 0.0) or 0.0))
        return (est, "history") if est else (None, "std_pending")
    return (None, "std_invalid")


def _load_predict(ctx, entry):
    path = os.path.join(ctx.task_dir, entry["file"])
    spec = importlib.util.spec_from_file_location("neqsim_task_calibration_predict", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return getattr(module, entry.get("function", "predict"))


def _prior(parameters, names, members, rng, centre=None, scale=1.0):
    draws = np.column_stack([
        rng.normal(float(parameters[n].get("initial", 0.0)) if centre is None else float(centre[i]),
                   float(parameters[n].get("std", 1.0)) * float(scale), members)
        for i, n in enumerate(names)])
    return _clip(draws, parameters, names)


def _clip(ensemble, parameters, names):
    lo = np.array([parameters[n].get("lo", -np.inf) if parameters[n].get("lo") is not None else -np.inf
                   for n in names], dtype=float)
    hi = np.array([parameters[n].get("hi", np.inf) if parameters[n].get("hi") is not None else np.inf
                   for n in names], dtype=float)
    return np.clip(ensemble, lo, hi)


def calibrate(ctx, spec):
    config = ctx.plan.get("calibration") or {}
    parameters = config.get("parameters") or {}
    observations = config.get("observations") or {}
    if not parameters or not observations or not config.get("predict"):
        return StageResult("calibrate", "fail",
                           message="calibration needs parameters, observations and predict")
    names = list(parameters)
    members = int(config.get("members", 40))
    rng = np.random.default_rng(config.get("seed"))
    state_path = os.path.join(ctx.state_dir, STATE_FILE)
    state = read_json(state_path, {}) or {}
    if state.get("parameters") != names or not state.get("members"):
        ensemble = _prior(parameters, names, members, rng)
        state = {"parameters": names, "cycles": 0, "unidentifiable": False}
    else:
        ensemble = np.asarray(state["members"], dtype=float)
    cycle_seed = None if config.get("seed") is None else int(config["seed"]) + int(state.get("cycles", 0))

    measured, used, obs_std, skipped = [], [], [], {}
    for obs, item in observations.items():
        value = ctx.kpis.get((item or {}).get("measured", obs))
        if not isinstance(value, (int, float)) or isinstance(value, bool):
            continue
        std, source = _observation_std(ctx, obs, item)
        if std is None:
            skipped[obs] = source
            continue
        measured.append(float(value))
        used.append(obs)
        obs_std.append(std)
    if not used:
        reason = "; ".join("{}: {}".format(k, v) for k, v in skipped.items())
        return StageResult("calibrate", "warn", message="no usable measurements this cycle" +
                           (" (" + reason + ")" if reason else ""))

    reinitialised = False
    reinit_after = int(config.get("reinit_after", 3) or 0)
    if reinit_after and int(state.get("all_rejected_streak", 0)) >= reinit_after:
        # Every measurement has been outside the prior predictive for reinit_after cycles: the
        # prior, not the data, is the likelier culprit. Re-draw it wider around the current mean.
        reinit_rng = np.random.default_rng(None if cycle_seed is None else cycle_seed * 31 + 1)
        # Widen enough that the rejected measurements become plausible: the prior-predictive spread
        # scales with the parameter spread, so the factor follows the largest normalised innovation.
        gate = float(config.get("gate_sigma") or 4.0)
        needed = 3.0 * float(state.get("last_max_z", 0.0)) / gate
        factor = max(float(config.get("reinit_scale", 2.0)), needed)
        current_std = ensemble.std(axis=0, ddof=1)
        prior_std = np.array([float(parameters[n].get("std", 1.0)) for n in names])
        new_std = np.maximum(prior_std * float(config.get("reinit_scale", 2.0)), current_std * factor)
        centre = ensemble.mean(axis=0)
        ensemble = _clip(centre + reinit_rng.normal(0.0, 1.0, (members, len(names))) * new_std, parameters, names)
        reinitialised = True

    walk = np.array([float(parameters[n].get("walk_std", 0.0) or 0.0) for n in names])
    if np.any(walk > 0) and state.get("cycles") and not reinitialised:
        # Parameter evolution model: a random walk keeps spread for slowly moving quantities.
        # Drawn from the per-cycle seed; the plan seed alone would repeat the same perturbation every cycle.
        walk_rng = np.random.default_rng(None if cycle_seed is None else cycle_seed * 7919)
        ensemble = _clip(ensemble + walk_rng.normal(0.0, 1.0, ensemble.shape) * walk, parameters, names)
    prior_ensemble = ensemble.copy()

    predict = _load_predict(ctx, config["predict"])
    steps = max(int(config.get("mda_steps", 1)), 1)
    gate_sigma = config.get("gate_sigma", 4.0)
    min_members = int(config.get("min_members", max(3, members // 2)))
    graph = {i: set() for i in range(len(names))}
    for i, name in enumerate(names):
        for other in parameters[name].get("neighbours") or []:
            if other in names and other != name:
                graph[i].add(names.index(other))
                graph[names.index(other)].add(i)   # conditional dependence is symmetric
    graph = {i: sorted(v) for i, v in graph.items()}
    depends_all = {}
    for i, obs in enumerate(used):
        listed = (observations[obs] or {}).get("depends_on")
        depends_all[i] = [names.index(n) for n in listed if n in names] if listed else list(range(len(names)))

    prior_pred = ident = None
    keep = np.ones(len(used), dtype=bool)
    z_scores = np.zeros(len(used))
    failed_members = 0
    for step in range(steps):
        outputs = predict(ctx, [dict(zip(names, row)) for row in ensemble.tolist()]) or []
        if len(outputs) != ensemble.shape[0]:
            return StageResult("calibrate", "fail", message="predict returned {} rows for {} members".format(
                len(outputs), ensemble.shape[0]))
        rows, ok = [], []
        for row in outputs:
            try:
                values = [float(row[obs]) for obs in used] if row is not None else None
            except (KeyError, TypeError, ValueError) as exc:
                return StageResult("calibrate", "fail", message="predict output incomplete: {}".format(exc))
            good = values is not None and all(np.isfinite(values))
            ok.append(good)
            rows.append(values if good else [np.nan] * len(used))
        ok = np.asarray(ok, dtype=bool)
        failed_members += int((~ok).sum())
        if int(ok.sum()) < min_members:
            return StageResult("calibrate", "fail", message="model solved for only {} of {} members (min_members {})"
                               .format(int(ok.sum()), len(ok), min_members))
        if not ok.all():
            # Drop the members the model could not solve, then restore the ensemble size from the survivors.
            fill_rng = np.random.default_rng(None if cycle_seed is None else cycle_seed * 53 + step)
            ensemble = _clip(replenish(ensemble[ok], ensemble.shape[0], fill_rng), parameters, names)
            survivors = np.asarray(rows, dtype=float)[ok]
            fitted = ensemble[len(survivors):]
            # Replenished members have no model output yet: predict them before continuing.
            extra = predict(ctx, [dict(zip(names, r)) for r in fitted.tolist()]) or []
            try:
                extra_rows = np.array([[float(r[obs]) for obs in used] for r in extra], dtype=float)
            except (KeyError, TypeError, ValueError):
                extra_rows = np.full((0, len(used)), np.nan)
            if extra_rows.shape[0] != fitted.shape[0] or not np.all(np.isfinite(extra_rows)):
                # Keep the reduced ensemble if the refill also fails; it is still a valid sample.
                ensemble = ensemble[:len(survivors)]
                predictions = survivors
            else:
                predictions = np.vstack([survivors, extra_rows])
        else:
            predictions = np.asarray(rows, dtype=float)
        if step == 0:
            prior_ensemble = ensemble.copy()
            prior_pred = predictions.mean(axis=0)
            prior_pred_std = predictions.std(axis=0, ddof=1)
            keep, z_scores = innovation_gate(predictions, measured, obs_std, gate_sigma)
            xa = ensemble - ensemble.mean(axis=0)
            ya = predictions[:, keep] - predictions[:, keep].mean(axis=0)
            jacobian = np.linalg.lstsq(xa, ya, rcond=None)[0].T if keep.any() else np.zeros((0, len(names)))
            ident = identifiability(jacobian, names, parameter_scale=ensemble.std(axis=0, ddof=1) + 1e-12)
            if not keep.any():
                break
        kept = [i for i in range(len(used)) if keep[i]]
        depends = {k: depends_all[i] for k, i in enumerate(kept)}
        ensemble = ensemble_update(
            ensemble, predictions[:, kept], [measured[i] for i in kept],
            mda_std([obs_std[i] for i in kept], steps), method=config.get("method", "enkf"),
            graph=graph, obs_depends=depends, seed=None if cycle_seed is None else cycle_seed * 101 + step,
            inflation=float(config.get("inflation", 1.0)) if step == steps - 1 else 1.0)
        ensemble = _clip(ensemble, parameters, names)
    posterior = ensemble

    means, stds = posterior.mean(axis=0), posterior.std(axis=0, ddof=1)
    kpis, triggers = {}, []
    for i, name in enumerate(names):
        kpis[name + "_mean"], kpis[name + "_std"] = float(means[i]), float(stds[i])
    for i, obs in enumerate(used):
        kpis[obs + "_resid"] = float(measured[i] - prior_pred[i])
        kpis[obs + "_z"] = float(z_scores[i])
    kpis["calibration_rank"] = float(ident["rank"])
    kpis["calibration_rejected"] = float(len(used) - int(keep.sum()))
    kpis["calibration_failed_members"] = float(failed_members)
    unidentifiable = not ident["identifiable"]
    if unidentifiable and not state.get("unidentifiable"):
        triggers.append("calibration:unidentifiable")
    all_rejected = not keep.any()
    if all_rejected:
        triggers.append("calibration:all_rejected")
    if reinitialised:
        triggers.append("calibration:reinitialised")
    ctx.kpis.update(kpis)
    ctx.calibrated = {name: float(means[i]) for i, name in enumerate(names)}

    rejected = [used[i] for i in range(len(used)) if not keep[i]]
    report = {"schema_version": "1.1", "cycle_id": ctx.cycle_id, "method": config.get("method", "enkf"),
              "members": int(posterior.shape[0]), "members_configured": members, "mda_steps": steps,
              "gate_sigma": gate_sigma, "inflation": float(config.get("inflation", 1.0)), "seed": cycle_seed,
              "state_cycles_before": int(state.get("cycles", 0)),
              "reinitialised": reinitialised, "failed_members": failed_members,
              "skipped_observations": skipped,
              "observations": {obs: {"measured": measured[i], "std": obs_std[i],
                                     "std_source": _observation_std(ctx, obs, observations[obs])[1],
                                     "prior_predicted_mean": float(prior_pred[i]),
                                     "prior_predicted_std": float(prior_pred_std[i]),
                                     "z": float(z_scores[i]), "used": bool(keep[i])}
                               for i, obs in enumerate(used)},
              "rejected": rejected,
              "parameters": {name: {"prior_mean": float(prior_ensemble[:, i].mean()),
                                    "prior_std": float(prior_ensemble[:, i].std(ddof=1)),
                                    "posterior_mean": float(means[i]), "posterior_std": float(stds[i]),
                                    "walk_std": float(walk[i])}
                             for i, name in enumerate(names)},
              "identifiability": ident}
    write_json(os.path.join(ctx.cycle_dir, "calibration.json"), report)
    if not ctx.dry_run:
        write_json(state_path, {"parameters": names, "members": posterior.tolist(),
                                "cycles": int(state.get("cycles", 0)) + 1, "unidentifiable": unidentifiable,
                                "all_rejected_streak": (int(state.get("all_rejected_streak", 0)) + 1
                                                        if all_rejected and not reinitialised else 0),
                                "last_max_z": float(np.max(np.abs(z_scores))) if len(z_scores) else 0.0,
                                "reinitialisations": int(state.get("reinitialisations", 0)) + int(reinitialised),
                                "last_cycle": ctx.cycle_id})
    message = "{} observation(s) ({} rejected{}), {} parameter(s), rank {}, {} MDA step(s){}{}".format(
        len(used), len(rejected), ", {} skipped".format(len(skipped)) if skipped else "",
        len(names), ident["rank"], steps,
        ", {} failed member(s) replenished".format(failed_members) if failed_members else "",
        "; prior re-initialised" if reinitialised else "")
    status = "warn" if (unidentifiable or rejected or skipped or failed_members or reinitialised) else "ok"
    return StageResult("calibrate", status, outputs=["calibration.json"],
                       kpis=kpis, triggers=triggers, message=message)


register("stages", "calibrate", calibrate)
