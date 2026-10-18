"""Bayesian building blocks for living tasks: ensemble updates, identifiability, value of information.

The runner keeps the loop programmed; these helpers make the *learning* parts of that loop
statistical instead of hand-typed:

* ``ensemble_update`` tracks uncertain model parameters (fouling, efficiency, meter bias)
  from measurements with an ensemble Kalman update (``enkf``) or an information-form update
  with a sparse precision matrix (``enif``, after the Ensemble Information Filter). The
  sparse variant needs a Markov graph of which parameters are conditionally dependent and
  which observations each parameter affects; with that structure a small ensemble no longer
  updates parameters through spurious sample correlations.
* ``identifiability`` tells whether the measurements determine the parameters at all.
* ``candidate_from_samples`` and ``value_of_information`` turn posterior predictive samples
  of a candidate's gain into the ``p_success``/``predicted_delta`` the stop rules consume,
  and say whether another simulation is worth its cost before an action is proposed.

Everything is numpy-only, seeded and returns plain Python types for the cycle folder.
"""

import math

import numpy as np


def _as_2d(values, name):
    arr = np.asarray(values, dtype=float)
    if arr.ndim != 2:
        raise ValueError("{} must be a 2-D array (members x variables)".format(name))
    return arr


def _obs_std(obs_std, m):
    std = np.asarray(obs_std, dtype=float)
    if std.ndim == 0:
        std = np.full(m, float(std))
    if std.shape != (m,):
        raise ValueError("obs_std must be a scalar or one value per observation")
    if np.any(std <= 0):
        raise ValueError("obs_std must be positive")
    return std


def _enkf(params, predictions, measured, std, rng, obs_depends=None):
    n, p = params.shape
    noise = rng.normal(0.0, 1.0, size=predictions.shape) * std
    perturbed = measured + noise
    xa = params - params.mean(axis=0)
    ya = predictions - predictions.mean(axis=0)
    cxy = xa.T @ ya / (n - 1)
    cyy = ya.T @ ya / (n - 1) + np.diag(std ** 2)
    gain = cxy @ np.linalg.solve(cyy, np.eye(cyy.shape[0]))
    if obs_depends is not None:
        # Domain localisation: a declared structure zeroes gain entries that only sampling noise could fill.
        mask = np.zeros((p, predictions.shape[1]))
        for i in range(predictions.shape[1]):
            for j in obs_depends.get(i, range(p)):
                mask[j, i] = 1.0
        gain = gain * mask
    return params + (perturbed - predictions) @ gain.T


def sparse_precision(samples, graph=None, ridge=1e-8):
    """Estimate a precision matrix with a known sparsity pattern by node-wise regression.

    ``graph`` maps each variable index to the indices it is conditionally dependent on; a
    missing entry means fully connected (which recovers the dense sample precision). The
    result is symmetrised so it is a valid information matrix.
    """
    samples = _as_2d(samples, "samples")
    n, p = samples.shape
    centred = samples - samples.mean(axis=0)
    precision = np.zeros((p, p))
    for j in range(p):
        neighbours = [k for k in (graph.get(j, range(p)) if graph is not None else range(p)) if k != j]
        y = centred[:, j]
        if neighbours:
            x = centred[:, neighbours]
            beta = np.linalg.solve(x.T @ x + ridge * np.eye(len(neighbours)), x.T @ y)
            residual = y - x @ beta
        else:
            beta, residual = np.zeros(0), y
        var = max(float(residual @ residual) / max(n - 1 - len(neighbours), 1), ridge)
        precision[j, j] = 1.0 / var
        for k, b in zip(neighbours, beta):
            precision[j, k] = -b / var
    precision = 0.5 * (precision + precision.T)
    eig = np.linalg.eigvalsh(precision)
    if eig[0] <= ridge * max(eig[-1], 1.0):
        # Symmetrisation of node-wise estimates can lose positive-definiteness; project only then.
        w, v = np.linalg.eigh(precision)
        precision = (v * np.maximum(w, ridge * max(w[-1], 1.0))) @ v.T
    return precision


def _enif(params, predictions, measured, std, rng, graph, obs_depends):
    n, p = params.shape
    m = predictions.shape[1]
    if graph is None and n <= p + 1:
        raise ValueError("enif without a parameter graph needs more members than parameters; "
                         "declare neighbours or use more members")
    index_graph = None
    if graph is not None:
        index_graph = {j: sorted(set([j] + list(graph.get(j, [])))) for j in range(p)}
    precision = sparse_precision(params, index_graph)
    xa = params - params.mean(axis=0)
    ya = predictions - predictions.mean(axis=0)
    h = np.zeros((m, p))
    for i in range(m):
        cols = sorted(obs_depends.get(i, range(p))) if obs_depends is not None else list(range(p))
        if not cols:
            continue
        x = xa[:, cols]
        h[i, cols] = np.linalg.solve(x.T @ x + 1e-8 * np.eye(len(cols)), x.T @ ya[:, i])
    r_inv = np.diag(1.0 / std ** 2)
    posterior_precision = precision + h.T @ r_inv @ h
    noise = rng.normal(0.0, 1.0, size=predictions.shape) * std
    innovation = (measured + noise) - predictions
    return params + np.linalg.solve(posterior_precision, (h.T @ r_inv @ innovation.T)).T


def ensemble_update(params, predictions, measured, obs_std, method="enkf", graph=None,
                    obs_depends=None, seed=None, bounds=None, inflation=1.0):
    """Update a parameter ensemble from measured values of the model predictions.

    Parameters
    ----------
    params : array (members x parameters)
    predictions : array (members x observations)
        Model output for each member at the current parameter values.
    measured : sequence (observations,)
    obs_std : float or sequence
        Measurement standard deviation(s).
    method : ``"enkf"`` or ``"enif"``
    graph : dict or None
        For ``enif``: parameter index -> indices of conditionally dependent parameters.
    obs_depends : dict or None
        Observation index -> parameter indices it depends on. For ``enif`` it sparsifies the
        observation operator; for ``enkf`` it localises the gain (entries outside the
        declared structure are zeroed), which matters when members are few.
    seed : int or None
    bounds : sequence of (lo, hi) or None
        Posterior members are clipped to these bounds.
    inflation : float
        Multiplicative inflation of the posterior anomalies (1.0 = none). A value of
        1.01-1.05 keeps a small ensemble from collapsing over many cycles.
    """
    params = _as_2d(params, "params")
    predictions = _as_2d(predictions, "predictions")
    if params.shape[0] != predictions.shape[0]:
        raise ValueError("params and predictions need the same number of members")
    if params.shape[0] < 3:
        raise ValueError("at least three ensemble members are required")
    measured = np.asarray(measured, dtype=float).reshape(-1)
    if measured.shape[0] != predictions.shape[1]:
        raise ValueError("measured needs one value per observation")
    std = _obs_std(obs_std, measured.shape[0])
    rng = np.random.default_rng(seed)
    if method == "enkf":
        posterior = _enkf(params, predictions, measured, std, rng, obs_depends)
    elif method == "enif":
        posterior = _enif(params, predictions, measured, std, rng, graph, obs_depends)
    else:
        raise ValueError("method must be 'enkf' or 'enif'")
    if float(inflation) < 1.0:
        raise ValueError("inflation must be >= 1.0")
    if float(inflation) > 1.0:
        mean = posterior.mean(axis=0)
        posterior = mean + float(inflation) * (posterior - mean)
    if bounds is not None:
        lo = np.array([b[0] if b[0] is not None else -np.inf for b in bounds], dtype=float)
        hi = np.array([b[1] if b[1] is not None else np.inf for b in bounds], dtype=float)
        posterior = np.clip(posterior, lo, hi)
    return posterior


def innovation_gate(predictions, measured, obs_std, gate_sigma=4.0):
    """Flag measurements inconsistent with the prior predictive distribution.

    Returns ``(keep, z)``: a boolean mask and the normalised innovations
    ``(y - mean_pred) / sqrt(var_pred + obs_std**2)``. An observation with ``|z|`` above
    ``gate_sigma`` is a gross error (frozen tag, spike, wrong unit) or a model defect; either
    way it must not update the parameters this cycle. ``gate_sigma=None`` keeps everything.
    """
    predictions = _as_2d(predictions, "predictions")
    measured = np.asarray(measured, dtype=float).reshape(-1)
    std = _obs_std(obs_std, measured.shape[0])
    spread = np.sqrt(predictions.var(axis=0, ddof=1) + std ** 2)
    z = (measured - predictions.mean(axis=0)) / spread
    keep = np.ones(measured.shape[0], dtype=bool) if gate_sigma is None else np.abs(z) <= float(gate_sigma)
    return keep, z


def mda_std(obs_std, steps):
    """Observation std for one ES-MDA step: ``sqrt(steps)`` times the real std.

    Assimilating the same data ``steps`` times with inflated noise (Emerick and Reynolds,
    2013) is equivalent to one Gaussian update for a linear model and far more stable for
    a nonlinear one, because each step re-linearises around the moved ensemble.
    """
    steps = int(steps)
    if steps < 1:
        raise ValueError("steps must be >= 1")
    return np.asarray(obs_std, dtype=float) * math.sqrt(steps)


def identifiability(jacobian, names=None, parameter_scale=None, noise_level=1e-3):
    """SVD of the scaled sensitivity Jacobian (observations x parameters).

    A singular value below ``noise_level`` (relative to the largest) marks a parameter
    combination that the observations do not determine.
    """
    j = _as_2d(jacobian, "jacobian")
    p = j.shape[1]
    names = list(names) if names is not None else ["p{}".format(i) for i in range(p)]
    if len(names) != p:
        raise ValueError("names must match the number of parameters")
    scale = np.ones(p) if parameter_scale is None else np.asarray(parameter_scale, dtype=float)
    scaled = j * scale
    u, s, vt = np.linalg.svd(scaled, full_matrices=False)
    largest = float(s[0]) if s.size and s[0] > 0 else 1.0
    rank = int(np.sum(s > noise_level * largest))
    directions = []
    for row in vt[rank:]:
        directions.append({names[i]: round(float(row[i]), 4) for i in np.argsort(-np.abs(row))[:3]})
    return {"singular_values": [float(v) for v in s],
            "condition_number": float(s[0] / s[-1]) if s.size and s[-1] > 0 else math.inf,
            "rank": rank, "n_parameters": p, "identifiable": rank == p,
            "unidentifiable_directions": directions,
            "sensitivity_norm": {names[i]: float(np.linalg.norm(scaled[:, i])) for i in range(p)}}


def noise_std_from_series(values, min_std=0.0, max_points=200):
    """Robust measurement-noise estimate from a KPI history: MAD of first differences / sqrt(2).

    First differences remove a slow trend, the median absolute deviation ignores steps and
    spikes, and ``1.4826`` rescales to a Gaussian sigma. Returns ``None`` with fewer than
    eight finite points; the caller then needs an explicit ``std``.
    """
    arr = np.asarray([v for v in values if v is not None], dtype=float)
    arr = arr[np.isfinite(arr)][-int(max_points):]
    if arr.size < 8:
        return None
    diffs = np.diff(arr)
    mad = float(np.median(np.abs(diffs - np.median(diffs))))
    sigma = 1.4826 * mad / math.sqrt(2.0)
    if sigma <= 0:
        sigma = float(np.std(diffs, ddof=1)) / math.sqrt(2.0)
    return max(float(sigma), float(min_std))


def replenish(ensemble, size, rng, ridge=1e-12):
    """Grow an ensemble back to ``size`` members by sampling its Gaussian fit.

    Used after members were dropped because the model failed for them; the surviving
    members define the mean and covariance so the spread is neither lost nor invented.
    """
    ensemble = _as_2d(ensemble, "ensemble")
    need = int(size) - ensemble.shape[0]
    if need <= 0:
        return ensemble
    mean = ensemble.mean(axis=0)
    cov = np.cov(ensemble, rowvar=False, ddof=1)
    cov = np.atleast_2d(cov) + ridge * np.eye(ensemble.shape[1])
    return np.vstack([ensemble, rng.multivariate_normal(mean, cov, size=need)])


def candidate_from_samples(action, delta_samples, cost=0.0, **extra):
    """Build a stop-rule candidate from posterior predictive samples of its gain.

    ``p_success`` is the probability the gain exceeds its cost and ``predicted_delta`` the
    expected gain given success. The stop rule's ``p_success * predicted_delta - cost`` is
    then the expected net improvement of an evaluate-then-adopt step: the cost is paid to
    try the action, and it is only adopted when the simulated gain is positive.
    """
    samples = np.asarray(delta_samples, dtype=float).reshape(-1)
    if samples.size == 0:
        raise ValueError("delta_samples must not be empty")
    success = samples > cost
    p = float(success.mean())
    delta = float(samples[success].mean()) if p > 0 else 0.0
    out = {"action": action, "p_success": round(p, 4), "predicted_delta": round(delta, 6),
           "cost": float(cost), "expected_delta": round(float(samples.mean()), 6),
           "delta_std": round(float(samples.std(ddof=1)) if samples.size > 1 else 0.0, 6),
           "p10": round(float(np.percentile(samples, 10)), 6),
           "p90": round(float(np.percentile(samples, 90)), 6), "n_samples": int(samples.size)}
    out.update(extra)
    return out


def value_of_information(delta_samples_by_action, cost=0.0, simulation_cost=0.0):
    """Expected value of perfect information over a set of candidate actions.

    ``delta_samples_by_action`` maps an action to equally long sample vectors of its gain
    (one sample per posterior member, so samples are paired across actions). Doing nothing
    is always allowed. ``evpi`` is the expected gain from knowing the true outcome before
    choosing; when it exceeds ``simulation_cost`` another evaluation is worth running
    before proposing the current best action.
    """
    if not delta_samples_by_action:
        raise ValueError("at least one action is required")
    actions = list(delta_samples_by_action)
    costs = cost if isinstance(cost, dict) else {a: float(cost) for a in actions}
    matrix = np.column_stack([np.asarray(delta_samples_by_action[a], dtype=float).reshape(-1)
                              - float(costs.get(a, 0.0)) for a in actions])
    expected_net = matrix.mean(axis=0)
    best = int(np.argmax(expected_net))
    value_now = max(0.0, float(expected_net[best]))
    value_perfect = float(np.maximum(matrix.max(axis=1), 0.0).mean())
    evpi = max(0.0, value_perfect - value_now)
    return {"recommended_action": actions[best] if value_now > 0 else None,
            "expected_net_improvement": {a: round(float(v), 6) for a, v in zip(actions, expected_net)},
            "p_best": {a: round(float((matrix.argmax(axis=1) == i).mean()), 4)
                       for i, a in enumerate(actions)},
            "value_now": round(value_now, 6), "value_perfect_information": round(value_perfect, 6),
            "evpi": round(evpi, 6), "simulation_cost": float(simulation_cost),
            "evaluate_more": bool(evpi > float(simulation_cost))}
