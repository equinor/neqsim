"""Tests for the learning helpers of living tasks: ensemble updates, VOI, calibrate stage, drift tuning."""
import json
import os
import types

import numpy as np
import pytest

import neqsim_continuous as nc
from neqsim_continuous import backtest, calibrate
from neqsim_continuous.contracts import resolve


def _linear_case(seed, members, p=4, sigma=0.3):
    rng = np.random.default_rng(seed)
    truth = rng.normal(0.0, 1.0, p)
    prior = rng.normal(0.0, 1.0, (members, p))
    h = np.eye(p)                      # each observation sees one parameter
    measured = h @ truth + rng.normal(0.0, sigma, p)
    return truth, prior, prior @ h.T, measured, sigma


def test_enkf_moves_towards_truth_and_shrinks_spread():
    truth, prior, pred, measured, sigma = _linear_case(1, 200)
    post = nc.ensemble_update(prior, pred, measured, sigma, method="enkf", seed=2)
    assert np.linalg.norm(post.mean(0) - truth) < np.linalg.norm(prior.mean(0) - truth)
    assert np.all(post.std(0) < prior.std(0))
    # Linear-Gaussian with sigma=0.3 and prior std 1: posterior std ~ 0.29
    assert np.allclose(post.std(0), 1 / np.sqrt(1 + 1 / sigma ** 2), atol=0.08)


def test_enif_with_full_graph_matches_enkf_posterior_moments():
    truth, prior, pred, measured, sigma = _linear_case(3, 400)
    a = nc.ensemble_update(prior, pred, measured, sigma, method="enkf", seed=5)
    b = nc.ensemble_update(prior, pred, measured, sigma, method="enif", seed=5)
    assert np.allclose(a.mean(0), b.mean(0), atol=0.05)
    assert np.allclose(a.std(0), b.std(0), atol=0.05)


def test_enif_sparse_structure_blocks_spurious_update():
    # Two independent parameters, only the first is observed; 8 members give a strong
    # spurious sample correlation that EnKF propagates to the unobserved parameter.
    rng = np.random.default_rng(11)
    prior = rng.normal(0.0, 1.0, (8, 2))
    prior[:, 1] = 0.8 * prior[:, 0] + 0.6 * rng.normal(0.0, 1.0, 8)  # accidental correlation
    pred = prior[:, :1]
    measured = [3.0]
    enkf = nc.ensemble_update(prior, pred, measured, 0.1, method="enkf", seed=1)
    enif = nc.ensemble_update(prior, pred, measured, 0.1, method="enif", seed=1,
                              graph={0: [], 1: []}, obs_depends={0: [0]})
    assert abs(enkf.mean(0)[1] - prior.mean(0)[1]) > 0.5
    assert abs(enif.mean(0)[1] - prior.mean(0)[1]) < 0.05
    assert abs(enif.mean(0)[0] - 3.0) < 0.2


def test_sparse_precision_recovers_chain_structure():
    rng = np.random.default_rng(4)
    x0 = rng.normal(0, 1, 5000)
    x1 = 0.7 * x0 + rng.normal(0, 1, 5000)
    x2 = 0.5 * x1 + rng.normal(0, 1, 5000)
    prec = nc.sparse_precision(np.column_stack([x0, x1, x2]), graph={0: [0, 1], 1: [0, 1, 2], 2: [1, 2]})
    assert prec[0, 2] == 0.0 and prec[2, 0] == 0.0
    assert prec[0, 1] < 0 and prec[1, 2] < 0
    assert np.all(np.linalg.eigvalsh(prec) > 0)


def test_ensemble_update_validation_and_bounds():
    prior = np.zeros((5, 2)) + np.arange(5)[:, None] * 0.1
    with pytest.raises(ValueError):
        nc.ensemble_update(prior[:2], prior[:2, :1], [0.0], 1.0)
    with pytest.raises(ValueError):
        nc.ensemble_update(prior, prior[:, :1], [0.0], 0.0)
    with pytest.raises(ValueError):
        nc.ensemble_update(prior, prior[:, :1], [0.0], 1.0, method="kalman")
    post = nc.ensemble_update(prior, prior[:, :1], [10.0], 0.01, bounds=[(0.0, 0.5), (None, None)], seed=0)
    assert post[:, 0].max() <= 0.5


def test_identifiability_reports_rank_and_direction():
    j = np.array([[1.0, 1.0, 0.0], [2.0, 2.0, 0.0], [0.0, 0.0, 1.0]])  # p0 and p1 only as a sum
    report = nc.identifiability(j, names=["eta", "fouling", "bias"])
    assert report["rank"] == 2 and not report["identifiable"]
    direction = report["unidentifiable_directions"][0]
    assert set(direction) >= {"eta", "fouling"}
    assert nc.identifiability(np.eye(3))["identifiable"]


def test_candidate_from_samples_feeds_stop_rules():
    rng = np.random.default_rng(0)
    cand = nc.candidate_from_samples("raise 2nd stage 0.2 bar", rng.normal(0.4, 0.3, 2000), cost=0.1,
                                     needs_person=False)
    assert 0.8 < cand["p_success"] < 0.9
    assert cand["predicted_delta"] > 0.4
    goal = {"objective": {"metric": "x", "direction": "maximize", "target": 10.0},
            "stop": {"expected_improvement_cost": 0.1}}
    decision = nc.evaluate(goal, [{"value": 1.0, "validated": True}], candidates=[cand])
    assert decision.state == "solving" and decision.details["next_action"] == cand["action"]


def test_value_of_information_recommends_and_flags_more_evaluation():
    rng = np.random.default_rng(2)
    certain = {"a": rng.normal(1.0, 0.01, 500), "b": rng.normal(0.2, 0.01, 500)}
    out = nc.value_of_information(certain, cost=0.0, simulation_cost=0.05)
    assert out["recommended_action"] == "a" and not out["evaluate_more"]
    uncertain = {"a": rng.normal(0.5, 1.0, 500), "b": rng.normal(0.4, 1.0, 500)}
    out = nc.value_of_information(uncertain, cost=0.0, simulation_cost=0.05)
    assert out["evpi"] > out["simulation_cost"] and out["evaluate_more"]
    assert abs(sum(out["p_best"].values()) - 1.0) < 1e-9
    hopeless = {"a": rng.normal(-1.0, 0.01, 200)}
    assert nc.value_of_information(hopeless)["recommended_action"] is None


def _read_json(path):
    with open(path) as handle:
        return json.load(handle)


def _ctx(tmp_path, plan, kpis, dry_run=False, cycle="c1"):
    task = tmp_path / "task"
    (task / "continuous" / "stages").mkdir(parents=True, exist_ok=True)
    cycle_dir = task / "continuous" / "cycles" / cycle
    cycle_dir.mkdir(parents=True, exist_ok=True)
    (task / "continuous" / "stages" / "predict.py").write_text(
        "def predict(ctx, members):\n"
        "    return [{'T_meas': 20.0 + 5.0 * m['fouling'], 'P_meas': 50.0 * m['eta']} for m in members]\n")
    return types.SimpleNamespace(task_dir=str(task), state_dir=str(task / "continuous"),
                                 cycle_dir=str(cycle_dir), cycle_id=cycle, plan=plan, kpis=dict(kpis),
                                 dry_run=dry_run, triggers=[])


PLAN = {"calibration": {"method": "enif", "members": 60, "seed": 3,
                        "parameters": {"fouling": {"initial": 0.0, "std": 0.3, "lo": 0.0, "hi": 2.0},
                                       "eta": {"initial": 0.7, "std": 0.05, "lo": 0.5, "hi": 0.95}},
                        "observations": {"T_meas": {"std": 0.2, "depends_on": ["fouling"]},
                                         "P_meas": {"std": 0.5, "depends_on": ["eta"]}},
                        "predict": {"file": "continuous/stages/predict.py"}}}


def test_calibrate_stage_tracks_parameters_across_cycles(tmp_path):
    stage = resolve("stages", "calibrate")
    ctx = _ctx(tmp_path, PLAN, {"T_meas": 24.0, "P_meas": 40.0})   # fouling 0.8, eta 0.8
    result = stage(ctx, {"name": "calibrate"})
    assert result.status == "ok" and result.triggers == []
    assert abs(ctx.kpis["fouling_mean"] - 0.8) < 0.15
    assert abs(ctx.kpis["eta_mean"] - 0.8) < 0.03
    assert ctx.calibrated["eta"] == ctx.kpis["eta_mean"]
    state = _read_json(os.path.join(ctx.state_dir, calibrate.STATE_FILE))
    assert state["cycles"] == 1 and len(state["members"]) == 60
    report = _read_json(os.path.join(ctx.cycle_dir, "calibration.json"))
    assert report["identifiability"]["identifiable"] and "T_meas_resid" in ctx.kpis
    # second cycle continues from the posterior and tightens
    ctx2 = _ctx(tmp_path, PLAN, {"T_meas": 24.0, "P_meas": 40.0})
    stage(ctx2, {"name": "calibrate"})
    assert ctx2.kpis["fouling_std"] < ctx.kpis["fouling_std"]


def test_calibrate_stage_dry_run_and_missing_data(tmp_path):
    stage = resolve("stages", "calibrate")
    ctx = _ctx(tmp_path, PLAN, {}, dry_run=True)
    assert stage(ctx, {"name": "calibrate"}).status == "warn"
    ctx = _ctx(tmp_path, PLAN, {"T_meas": 24.0, "P_meas": 40.0}, dry_run=True)
    assert stage(ctx, {"name": "calibrate"}).status == "ok"
    assert not os.path.exists(os.path.join(ctx.state_dir, calibrate.STATE_FILE))
    assert resolve("stages", "calibrate")(_ctx(tmp_path, {}, {"T_meas": 1.0}), {"name": "calibrate"}).status == "fail"


def test_calibrate_flags_unidentifiable_once(tmp_path):
    plan = json.loads(json.dumps(PLAN))
    plan["calibration"]["observations"] = {"T_meas": {"std": 0.2}}   # P_meas gone: eta unobserved
    stage = resolve("stages", "calibrate")
    first = stage(_ctx(tmp_path, plan, {"T_meas": 24.0}), {"name": "calibrate"})
    assert first.status == "warn" and first.triggers == ["calibration:unidentifiable"]
    second = stage(_ctx(tmp_path, plan, {"T_meas": 24.0}), {"name": "calibrate"})
    assert second.triggers == []


def test_innovation_gate_rejects_gross_error():
    rng = np.random.default_rng(1)
    pred = rng.normal([20.0, 40.0], [0.5, 0.5], (50, 2))
    keep, z = nc.innovation_gate(pred, [20.3, 90.0], [0.2, 0.5], gate_sigma=4.0)
    assert keep.tolist() == [True, False] and abs(z[1]) > 20
    assert nc.innovation_gate(pred, [20.3, 90.0], 0.5, gate_sigma=None)[0].all()


def test_calibrate_gates_outlier_and_keeps_other_update(tmp_path):
    stage = resolve("stages", "calibrate")
    ctx = _ctx(tmp_path, PLAN, {"T_meas": 24.0, "P_meas": 400.0})   # P is a frozen/wrong-unit tag
    result = stage(ctx, {"name": "calibrate"})
    assert result.status == "warn" and "1 rejected" in result.message
    assert ctx.kpis["calibration_rejected"] == 1.0 and abs(ctx.kpis["P_meas_z"]) > 4
    assert abs(ctx.kpis["fouling_mean"] - 0.8) < 0.15          # T still used
    assert abs(ctx.kpis["eta_mean"] - 0.7) < 0.02              # eta untouched by the bad P
    report = _read_json(os.path.join(ctx.cycle_dir, "calibration.json"))
    assert report["rejected"] == ["P_meas"] and report["observations"]["T_meas"]["used"]
    ctx2 = _ctx(tmp_path, PLAN, {"T_meas": 240.0, "P_meas": 400.0})
    assert "calibration:all_rejected" in stage(ctx2, {"name": "calibrate"}).triggers


def test_inflation_and_walk_prevent_collapse_over_many_cycles(tmp_path):
    plan = json.loads(json.dumps(PLAN))
    plan["calibration"]["parameters"]["fouling"]["walk_std"] = 0.02
    plan["calibration"]["inflation"] = 1.02
    stage = resolve("stages", "calibrate")
    rng = np.random.default_rng(5)
    stds = []
    for c in range(40):
        fouling = 0.2 + 0.015 * c                         # the plant is slowly fouling
        ctx = _ctx(tmp_path, plan, {"T_meas": 20.0 + 5.0 * fouling + rng.normal(0, 0.2),
                                    "P_meas": 40.0 + rng.normal(0, 0.5)}, cycle="c{}".format(c))
        stage(ctx, {"name": "calibrate"})
        stds.append(ctx.kpis["fouling_std"])
    assert stds[-1] > 0.01                                 # spread survives 40 cycles
    assert abs(ctx.kpis["fouling_mean"] - fouling) < 0.1   # and the ramp is tracked
    plan_static = json.loads(json.dumps(PLAN))             # no walk, no inflation: collapses
    for c in range(40):
        ctx_s = _ctx(tmp_path / "s", plan_static, {"T_meas": 24.0 + rng.normal(0, 0.2),
                                                   "P_meas": 40.0 + rng.normal(0, 0.5)}, cycle="c{}".format(c))
        stage(ctx_s, {"name": "calibrate"})
    assert ctx_s.kpis["fouling_std"] < stds[-1]


def test_mda_steps_track_nonlinear_model(tmp_path):
    plan = json.loads(json.dumps(PLAN))
    plan["calibration"]["mda_steps"] = 4
    plan["calibration"]["method"] = "enkf"
    plan["calibration"]["gate_sigma"] = None           # a deliberately poor prior must not be gated here
    plan["calibration"]["parameters"]["fouling"] = {"initial": 0.1, "std": 0.4, "lo": 0.0, "hi": 3.0}
    plan["calibration"]["observations"] = {"T_meas": {"std": 0.05}, "P_meas": {"std": 0.5}}
    stage = resolve("stages", "calibrate")
    ctx = _ctx(tmp_path, plan, {"T_meas": 20.0 + 5.0 * 1.5 ** 2, "P_meas": 40.0})  # truth fouling 1.5
    (tmp_path / "task" / "continuous" / "stages" / "predict.py").write_text(
        "CALLS = []\n"
        "def predict(ctx, members):\n"
        "    CALLS.append(len(members))\n"
        "    return [{'T_meas': 20.0 + 5.0 * m['fouling'] ** 2, 'P_meas': 50.0 * m['eta']} for m in members]\n")
    result = stage(ctx, {"name": "calibrate"})
    assert result.status == "ok" and "4 MDA" in result.message
    assert abs(ctx.kpis["fouling_mean"] - 1.5) < 0.1
    assert nc.mda_std([0.1, 0.2], 4).tolist() == pytest.approx([0.2, 0.4])
    with pytest.raises(ValueError):
        nc.mda_std(0.1, 0)


def test_enif_without_graph_needs_members():
    prior = np.random.default_rng(0).normal(0, 1, (4, 5))
    with pytest.raises(ValueError):
        nc.ensemble_update(prior, prior[:, :1], [0.0], 1.0, method="enif")
    with pytest.raises(ValueError):
        nc.ensemble_update(prior, prior[:, :1], [0.0], 1.0, inflation=0.9)


def test_enkf_localisation_blocks_spurious_update():
    rng = np.random.default_rng(11)
    prior = rng.normal(0.0, 1.0, (8, 2))
    prior[:, 1] = 0.8 * prior[:, 0] + 0.6 * rng.normal(0.0, 1.0, 8)
    pred = prior[:, :1]
    plain = nc.ensemble_update(prior, pred, [3.0], 0.1, method="enkf", seed=1)
    local = nc.ensemble_update(prior, pred, [3.0], 0.1, method="enkf", seed=1, obs_depends={0: [0]})
    assert abs(plain.mean(0)[1] - prior.mean(0)[1]) > 0.5
    assert abs(local.mean(0)[1] - prior.mean(0)[1]) < 1e-9
    assert abs(local.mean(0)[0] - 3.0) < 0.2


def test_noise_std_from_series_is_robust_to_trend_steps_and_spikes():
    rng = np.random.default_rng(2)
    clean = 10.0 + 0.01 * np.arange(200) + rng.normal(0, 0.05, 200)   # trend + 0.05 noise
    dirty = clean.copy()
    dirty[100:] += 2.0          # step
    dirty[50] += 30.0           # spike
    assert nc.noise_std_from_series(clean) == pytest.approx(0.05, rel=0.3)
    assert nc.noise_std_from_series(dirty) == pytest.approx(0.05, rel=0.35)
    assert nc.noise_std_from_series([1.0] * 5) is None
    assert nc.noise_std_from_series([1.0] * 20, min_std=0.3) == 0.3


def test_replenish_keeps_moments():
    rng = np.random.default_rng(0)
    survivors = rng.multivariate_normal([1.0, -2.0], [[0.04, 0.01], [0.01, 0.09]], 30)
    full = nc.replenish(survivors, 60, rng)
    assert full.shape == (60, 2)
    assert np.allclose(full.mean(0), survivors.mean(0), atol=0.1)
    assert np.allclose(full.std(0), survivors.std(0), atol=0.08)
    assert nc.replenish(survivors, 10, rng).shape == (30, 2)


def test_calibrate_drops_failed_members_and_replenishes(tmp_path):
    plan = json.loads(json.dumps(PLAN))
    plan["calibration"]["min_members"] = 20
    plan["calibration"]["parameters"]["fouling"] = {"initial": 0.5, "std": 0.3, "lo": 0.0, "hi": 2.0}
    ctx = _ctx(tmp_path, plan, {"T_meas": 24.0, "P_meas": 40.0})
    (tmp_path / "task" / "continuous" / "stages" / "predict.py").write_text(
        "N = [0]\n"
        "def predict(ctx, members):\n"
        "    out = []\n"
        "    for i, m in enumerate(members):\n"
        "        first_call = N[0] == 0\n"
        "        if first_call and i % 4 == 0:\n"
        "            out.append(None)                     # solver failed for this member\n"
        "        elif first_call and i % 4 == 1:\n"
        "            out.append({'T_meas': float('nan'), 'P_meas': 1.0})\n"
        "        else:\n"
        "            out.append({'T_meas': 20.0 + 5.0 * m['fouling'], 'P_meas': 50.0 * m['eta']})\n"
        "    N[0] += 1\n"
        "    return out\n")
    stage = resolve("stages", "calibrate")
    result = stage(ctx, {"name": "calibrate"})
    assert result.status == "warn" and "30 failed member(s) replenished" in result.message
    assert ctx.kpis["calibration_failed_members"] == 30
    assert abs(ctx.kpis["fouling_mean"] - 0.8) < 0.15 and abs(ctx.kpis["eta_mean"] - 0.8) < 0.03
    state = _read_json(os.path.join(ctx.state_dir, calibrate.STATE_FILE))
    assert len(state["members"]) == 60
    plan["calibration"]["min_members"] = 40
    ctx2 = _ctx(tmp_path / "b", plan, {"T_meas": 24.0, "P_meas": 40.0})
    (tmp_path / "b" / "task" / "continuous" / "stages" / "predict.py").write_text(
        "def predict(ctx, members):\n    return [None] * len(members)\n")
    assert stage(ctx2, {"name": "calibrate"}).status == "fail"


def test_calibrate_auto_std_from_history_and_pending(tmp_path):
    plan = json.loads(json.dumps(PLAN))
    plan["calibration"]["observations"]["P_meas"] = {"std": "auto", "min_std": 0.1, "depends_on": ["eta"]}
    stage = resolve("stages", "calibrate")
    ctx = _ctx(tmp_path, plan, {"T_meas": 24.0, "P_meas": 40.0})
    result = stage(ctx, {"name": "calibrate"})         # no history yet: P_meas skipped
    assert result.status == "warn" and "1 skipped" in result.message
    report = _read_json(os.path.join(ctx.cycle_dir, "calibration.json"))
    assert report["skipped_observations"] == {"P_meas": "std_pending"}
    rng = np.random.default_rng(1)
    with open(os.path.join(ctx.state_dir, "kpi_history.csv"), "w", newline="") as f:
        f.write("cycle_id,time,kpi,value\n")
        for i in range(30):
            f.write("c{},2026-01-{:02d}T00:00:00+00:00,P_meas,{}\n".format(i, i % 28 + 1, 40.0 + rng.normal(0, 0.4)))
    ctx2 = _ctx(tmp_path, plan, {"T_meas": 24.0, "P_meas": 40.0}, cycle="c2")
    result2 = stage(ctx2, {"name": "calibrate"})
    report2 = _read_json(os.path.join(ctx2.cycle_dir, "calibration.json"))
    assert report2["observations"]["P_meas"]["std_source"] == "history"
    assert 0.2 < report2["observations"]["P_meas"]["std"] < 0.7
    assert result2.status == "ok"


def test_calibrate_reinitialises_prior_after_persistent_rejection(tmp_path):
    plan = json.loads(json.dumps(PLAN))
    plan["calibration"]["parameters"]["fouling"] = {"initial": 0.0, "std": 0.02, "lo": 0.0, "hi": 5.0}
    plan["calibration"]["observations"] = {"T_meas": {"std": 0.1, "depends_on": ["fouling"]}}
    plan["calibration"]["reinit_after"] = 3
    stage = resolve("stages", "calibrate")
    results = []
    for c in range(8):
        ctx = _ctx(tmp_path, plan, {"T_meas": 30.0}, cycle="c{}".format(c))   # truth fouling 2.0, prior says 0.0
        results.append((stage(ctx, {"name": "calibrate"}), dict(ctx.kpis)))
    triggers = [r.triggers for r, _ in results]
    assert all("calibration:all_rejected" in t for t in triggers[:3])
    reinit_cycles = [i for i, t in enumerate(triggers) if "calibration:reinitialised" in t]
    assert reinit_cycles and reinit_cycles[0] == 3
    assert abs(results[-1][1]["fouling_mean"] - 2.0) < 0.1     # converges after widening
    assert "calibration:all_rejected" not in triggers[-1]
    state = _read_json(os.path.join(ctx.state_dir, calibrate.STATE_FILE))
    assert state["reinitialisations"] >= 1 and state["all_rejected_streak"] == 0


def _fake_backtest(tmp_path, values, onset_index):
    task = tmp_path / "task"
    run = task / "continuous" / "backtests" / "bt"
    (run / "cycles").mkdir(parents=True)
    import yaml
    (task / "continuous").mkdir(exist_ok=True)
    (task / "continuous" / "cycle_plan.yaml").write_text(yaml.safe_dump({
        "schema_version": "1.0", "stages": ["kpis"],
        "drift": {"signals": {"eff": {"kpi": "eff", "min_sigma": 0.002}},
                  "settings": {"lambda": 0.2, "L": 3.5, "h": 6.0, "warmup": 30, "confirm": 2}},
        "backtest": {"expected": [{"trigger": "drift:eff", "onset": "2026-01-01T00:00:00+00:00",
                                   "max_delay_days": 20}]}}))
    from datetime import datetime, timedelta, timezone
    t0 = datetime(2026, 1, 1, tzinfo=timezone.utc) - timedelta(days=onset_index)
    for i, v in enumerate(values):
        d = run / "cycles" / "c{:04d}".format(i)
        d.mkdir()
        now = (t0 + timedelta(days=i)).isoformat()
        (d / "cycle.json").write_text(json.dumps({"now": now, "triggers": []}))
        (d / "kpis.json").write_text(json.dumps({"eff": v}))
    (run / "backtest_report.json").write_text(json.dumps({
        "start": t0.isoformat(), "end": (t0 + timedelta(days=len(values) - 1)).isoformat()}))
    return task


def test_tune_drift_prefers_settings_that_detect_without_false_alarms(tmp_path):
    rng = np.random.default_rng(7)
    values = list(0.80 + rng.normal(0, 0.001, 120))
    values += list(0.80 - 0.004 * np.arange(1, 61) / 60.0 - 0.006 + rng.normal(0, 0.001, 60))
    task = _fake_backtest(tmp_path, values, onset_index=120)
    out = nc.tune_drift(task, run_name="bt", grid={"lambda": [0.2], "L": [3.0, 3.5], "h": [6.0],
                                                   "confirm": [1, 2], "min_sigma_scale": [1.0, 50.0]})
    assert out["candidates_evaluated"] == 8
    best = out["best"]
    assert best["detected"] == 1 and best["false_alarms"] == 0
    assert best["settings"]["min_sigma_scale"] == 1.0   # a 0.1 floor would hide the drift
    assert out["recommended_drift_settings"]["signals"]["eff"]["min_sigma"] == pytest.approx(0.002)
    assert os.path.exists(os.path.join(str(task), "continuous", "backtests", "bt", "drift_tuning.json"))


def test_tune_drift_requires_expected_events(tmp_path):
    task = _fake_backtest(tmp_path, [0.8] * 40, onset_index=10)
    import yaml
    plan_path = task / "continuous" / "cycle_plan.yaml"
    plan = yaml.safe_load(plan_path.read_text())
    plan["backtest"] = {"expected": []}
    plan_path.write_text(yaml.safe_dump(plan))
    with pytest.raises(ValueError):
        backtest.tune_drift(task, run_name="bt")


def test_run_loop_cadence_hook_and_stop(tmp_path, monkeypatch):
    import yaml
    from datetime import datetime, timedelta, timezone
    from neqsim_continuous import loop as loop_mod
    task = tmp_path / "task"
    (task / "continuous").mkdir(parents=True)
    (task / "continuous" / "cycle_plan.yaml").write_text(yaml.safe_dump({"schema_version": "1.0", "stages": ["kpis"]}))
    (task / "hook.py").write_text("CALLS = []\ndef before_cycle(task_dir, index, now):\n    CALLS.append(index)\n"
                                  "    open(task_dir + '/continuous/hook_%d' % index, 'w').close()\n"
                                  "    if index == 2:\n        raise RuntimeError('hook failure must not stop the loop')\n")
    t = [datetime(2026, 1, 1, tzinfo=timezone.utc)]
    seen = []
    monkeypatch.setattr(loop_mod, "run_cycle", lambda task_dir, mode, no_agent: (
        seen.append(mode) or {"cycle_id": "c%d" % len(seen), "status": "complete", "degraded": False, "triggers": []}))
    summary = loop_mod.run_loop(task, every_minutes=10, for_hours=1.0, hook="hook.py:before_cycle",
                                clock=lambda: t[0], sleep=lambda s: t.__setitem__(0, t[0] + timedelta(seconds=s)))
    assert summary["cycles"] == 6 and summary["stopped"] == "time budget" and summary["errors"] == 1
    assert len(seen) == 5                                   # cycle 2's hook raised before run_cycle
    assert all((task / "continuous" / ("hook_%d" % i)).exists() for i in range(6))
    state = _read_json(task / "continuous" / "loop_state.json")
    assert [c["index"] for c in state["cycles"]] == list(range(6))
    assert state["cycles"][2]["status"] == "error"
    t[0] = datetime(2026, 1, 2, tzinfo=timezone.utc)
    (task / "continuous" / "STOP").touch()
    summary = loop_mod.run_loop(task, every_minutes=10, for_hours=1.0, clock=lambda: t[0], sleep=lambda s: None)
    assert summary["stopped"] == "STOP file" and summary["cycles"] == 0
    assert not (task / "continuous" / "STOP").exists()


def test_drift_monitor_recovers_and_alarms_again():
    monitor = nc.DriftMonitor({"warmup": 10, "confirm": 2, "min_sigma": 0.01, "L": 3.0, "h": 4.0})
    rng = np.random.default_rng(3)
    series = list(rng.normal(1.0, 0.005, 10)) + [0.9] * 10 + list(rng.normal(1.0, 0.005, 40)) + [0.9] * 10
    events = [monitor.update("x", v) for v in series]
    new = [i for i, e in enumerate(events) if e.get("new_alarm")]
    rec = [i for i, e in enumerate(events) if e.get("recovered")]
    assert len(new) == 2 and new[0] < rec[0] < new[1]
    assert events[-1]["alarms_total"] == 2
    persistent = nc.DriftMonitor({"warmup": 10, "confirm": 2, "min_sigma": 0.01})
    flags = [persistent.update("y", v).get("new_alarm") for v in [1.0] * 10 + [0.9] * 60]
    assert sum(1 for f in flags if f) == 1               # a persistent shift stays one alarm


def test_tune_drift_holdout_validates_out_of_sample(tmp_path):
    import yaml
    from datetime import timedelta
    rng = np.random.default_rng(9)
    # Two drifts: one at day 120 (train) and one at day 300 (holdout), each recovering after 60 days.
    values = list(0.80 + rng.normal(0, 0.001, 120))
    values += list(0.80 - 0.012 + rng.normal(0, 0.001, 60))
    values += list(0.80 + rng.normal(0, 0.001, 120))
    values += list(0.80 - 0.012 + rng.normal(0, 0.001, 60))
    task = _fake_backtest(tmp_path, values, onset_index=120)
    plan_path = task / "continuous" / "cycle_plan.yaml"
    plan = yaml.safe_load(plan_path.read_text())
    first_onset = plan["backtest"]["expected"][0]["onset"]
    from neqsim_continuous.watermarks import parse_time
    second = (parse_time(first_onset) + timedelta(days=180)).isoformat()
    plan["backtest"]["expected"].append({"trigger": "drift:eff", "onset": second, "max_delay_days": 20})
    plan_path.write_text(yaml.safe_dump(plan))
    split = (parse_time(first_onset) + timedelta(days=90)).isoformat()
    out = nc.tune_drift(task, run_name="bt", holdout_from=split,
                        grid={"lambda": [0.2], "L": [3.5], "h": [6.0], "confirm": [2], "min_sigma_scale": [1.0, 50.0]})
    assert out["train_events"] == 1 and out["holdout_events"] == 1
    assert out["best"]["detected"] == 1 and out["best"]["holdout"]["detected"] == 1
    assert out["validated"] is True
    assert out["candidates_evaluated"] == 4          # 2 min_sigma scales x 2 recover values
    with pytest.raises(ValueError):
        nc.tune_drift(task, run_name="bt", holdout_from=first_onset)   # no train events left
    in_sample = nc.tune_drift(task, run_name="bt", grid={"lambda": [0.2], "L": [3.5], "h": [6.0], "confirm": [2]})
    assert in_sample["validated"] is False and "in-sample" in in_sample["note"]
