"""Objective function for the forward-proxy calibration.

Default (oil and gas rates matched directly; GOR is a diagnostic only)::

    J(theta) = sum_j w_j [ w_o L(r_oil,j) + w_g L(r_gas,j) + w_GOR L(r_GOR,j) ]
    r_oil,j  = (Q_oil,proxy - Q_oil,ref) / (Q_oil,ref + eps_o)
    r_gas,j  = (Q_gas,proxy - Q_gas,ref) / (Q_gas,ref + eps_g)

``L`` is ``r^2`` (``squared``), the robust ``soft_l1`` loss ``2 c^2 (sqrt(1 + (r/c)^2) - 1)``
(quadratic for small r, linear for large r) or a capped square ``min(r^2, c^2)`` (``cap``),
with ``c = residual_scale``.

Dry-gas protection (do not let a super-dry fluid with a huge GOR dominate while an
oil-volume error at moderate GOR matters more):

* ``eps_o`` is a fraction of the median reference oil rate, so a fluid with almost no oil cannot
  produce a huge relative oil residual;
* fluids with reference GOR above ``dry_gas_gor_threshold`` get weight ``dry_gas_weight``;
* the robust loss limits the pull of any single residual;
* the oil weight defaults to twice the gas weight.

Optional ``prior_weight`` adds ``prior_weight * sum_k (u_k - u0_k)^2`` (unit box) so that, along a
flat valley where several stage-condition combinations give the same rates, the fit stays near the
design-based initial guess and is reproducible.

Each fluid's share of J is reported and dominance is flagged.
"""
from __future__ import annotations

import math
from dataclasses import dataclass, field
from statistics import median
from typing import Any, Dict, List, Optional, Sequence

from .config import ObjectiveConfig


@dataclass
class Normalization:
    """Absolute normalization floors (same units as the rates)."""

    eps_oil: float
    eps_gas: float
    eps_gor: float


@dataclass
class Breakdown:
    """Objective value and per-fluid contributions."""

    total: float
    contributions: Dict[str, float] = field(default_factory=dict)
    residuals: Dict[str, Dict[str, float]] = field(default_factory=dict)
    n_failed: int = 0


def _ok(rec: Optional[Dict[str, Any]]) -> bool:
    return bool(rec) and rec.get("status") == "ok"


def normalization(refs: Dict[str, Dict[str, Any]], cfg: ObjectiveConfig) -> Normalization:
    """Normalization floors from the median of the successful reference results."""
    good = [r for r in refs.values() if _ok(r)]
    if not good:
        raise ValueError("no successful reference results to normalize against")
    oil = median([r["oil_Sm3_h"] for r in good])
    gas = median([r["gas_Sm3_h"] for r in good])
    gors = [r["gor_Sm3_Sm3"] for r in good if math.isfinite(r["gor_Sm3_Sm3"])]
    gor = median(gors) if gors else 0.0
    return Normalization(cfg.eps_oil_fraction * oil, cfg.eps_gas_fraction * gas, cfg.eps_gor_fraction * gor)


def effective_weights(cases: Sequence[Any], refs: Dict[str, Dict[str, Any]], cfg: ObjectiveConfig) -> Dict[str, float]:
    """User fluid weight times the dry-gas factor (fluids without a reference get weight 0)."""
    out = {}
    for c in cases:
        r = refs.get(c.id)
        if not _ok(r):
            out[c.id] = 0.0
            continue
        w = float(c.weight)
        if r["gor_Sm3_Sm3"] > cfg.dry_gas_gor_threshold:
            w *= cfg.dry_gas_weight
        out[c.id] = w
    return out


def loss(r: float, cfg: ObjectiveConfig) -> float:
    """Per-residual loss."""
    c = cfg.residual_scale
    if cfg.loss == "squared":
        return r * r
    if cfg.loss == "cap":
        return min(r * r, c * c)
    return 2.0 * c * c * (math.sqrt(1.0 + (r / c) ** 2) - 1.0)


def residuals(ref: Dict[str, Any], prox: Dict[str, Any], norm: Normalization) -> Dict[str, float]:
    """Normalized oil, gas and GOR residuals of one fluid."""
    r_oil = (prox["oil_Sm3_h"] - ref["oil_Sm3_h"]) / (ref["oil_Sm3_h"] + norm.eps_oil)
    r_gas = (prox["gas_Sm3_h"] - ref["gas_Sm3_h"]) / (ref["gas_Sm3_h"] + norm.eps_gas)
    if math.isfinite(prox["gor_Sm3_Sm3"]) and math.isfinite(ref["gor_Sm3_Sm3"]):
        r_gor = (prox["gor_Sm3_Sm3"] - ref["gor_Sm3_Sm3"]) / (ref["gor_Sm3_Sm3"] + norm.eps_gor)
    else:
        r_gor = math.inf
    return {"oil": r_oil, "gas": r_gas, "gor": r_gor}


def evaluate(refs: Dict[str, Dict[str, Any]], proxies: Dict[str, Dict[str, Any]], weights: Dict[str, float],
             cfg: ObjectiveConfig, norm: Normalization) -> Breakdown:
    """Objective value with per-fluid contributions. Failed proxy runs add ``failed_penalty``."""
    total = 0.0
    out = Breakdown(0.0)
    for fid, w in weights.items():
        if w <= 0:
            continue
        ref, prox = refs[fid], proxies.get(fid)
        if not _ok(prox):
            contrib = w * cfg.failed_penalty
            out.n_failed += 1
        else:
            r = residuals(ref, prox, norm)
            contrib = cfg.oil_weight * loss(r["oil"], cfg) + cfg.gas_weight * loss(r["gas"], cfg)
            if cfg.gor_weight > 0:
                contrib += cfg.gor_weight * (loss(r["gor"], cfg) if math.isfinite(r["gor"]) else cfg.failed_penalty)
            contrib *= w
            out.residuals[fid] = r
        out.contributions[fid] = contrib
        total += contrib
    out.total = total
    return out


def dominance(breakdown: Breakdown, refs: Dict[str, Dict[str, Any]], cfg: ObjectiveConfig,
              negligible_mean_contribution: float = 1.0e-6) -> Dict[str, Any]:
    """Share of the objective per fluid and a dominance flag.

    Shares of a negligible objective (mean contribution per fluid below
    ``negligible_mean_contribution``, about 0.1 % rms residual) are not flagged: they are noise.
    """
    total = breakdown.total
    shares = {k: (v / total if total > 0 else 0.0) for k, v in breakdown.contributions.items()}
    n = len(shares)
    dry = [k for k in shares if _ok(refs.get(k)) and refs[k]["gor_Sm3_Sm3"] > cfg.dry_gas_gor_threshold]
    dry_share = sum(shares[k] for k in dry)
    top = sorted(shares.items(), key=lambda kv: kv[1], reverse=True)[:5]
    flags = []
    material = n > 0 and total / n > negligible_mean_contribution
    if material and n >= 4 and top and top[0][1] > cfg.dominance_share:
        flags.append("fluid %s contributes %.0f %% of the objective" % (top[0][0], 100 * top[0][1]))
    if material and dry and dry_share > max(cfg.dominance_share, 2.0 * len(dry) / n):
        flags.append("dry-gas fluids (GOR > %.0f) contribute %.0f %% of the objective while being %d of %d fluids"
                     % (cfg.dry_gas_gor_threshold, 100 * dry_share, len(dry), n))
    return {"shares": shares, "top_contributors": top, "dry_gas_fluids": dry, "dry_gas_share": dry_share,
            "flags": flags, "dominated": bool(flags), "objective_material": material}


def objective_components(refs: Dict[str, Dict[str, Any]], proxies: Dict[str, Dict[str, Any]],
                         weights: Dict[str, float], cfg: ObjectiveConfig, norm: Normalization) -> List[float]:
    """Signed components ``f_i`` with ``sum f_i^2 == evaluate(...).total`` (fixed length).

    ``f = sign(r) sqrt(w_j w_x L(r))`` per fluid and target; a failed fluid contributes
    ``sqrt(w_j failed_penalty)`` in its oil slot. Least-squares optimizers minimise exactly J.
    """
    out: List[float] = []
    use_gor = cfg.gor_weight > 0
    for fid, w in weights.items():
        if w <= 0:
            continue
        prox = proxies.get(fid)
        if not _ok(prox):
            out.extend([math.sqrt(w * cfg.failed_penalty), 0.0] + ([0.0] if use_gor else []))
            continue
        r = residuals(refs[fid], prox, norm)
        for key, wx in (("oil", cfg.oil_weight), ("gas", cfg.gas_weight)):
            out.append(math.copysign(math.sqrt(w * wx * loss(r[key], cfg)), r[key]))
        if use_gor:
            if math.isfinite(r["gor"]):
                out.append(math.copysign(math.sqrt(w * cfg.gor_weight * loss(r["gor"], cfg)), r["gor"]))
            else:
                out.append(math.sqrt(w * cfg.gor_weight * cfg.failed_penalty))
    return out


def residual_vector(refs: Dict[str, Dict[str, Any]], proxies: Dict[str, Dict[str, Any]], weights: Dict[str, float],
                    cfg: ObjectiveConfig, norm: Normalization) -> List[float]:
    """Weighted normalized residuals (sqrt(w_j w_o) r_oil, sqrt(w_j w_g) r_gas, ...) for identifiability."""
    out: List[float] = []
    for fid, w in weights.items():
        if w <= 0:
            continue
        prox = proxies.get(fid)
        if not _ok(prox):
            out.extend([math.nan, math.nan])
            continue
        r = residuals(refs[fid], prox, norm)
        out.append(math.sqrt(w * cfg.oil_weight) * r["oil"])
        out.append(math.sqrt(w * cfg.gas_weight) * r["gas"])
        if cfg.gor_weight > 0 and math.isfinite(r["gor"]):
            out.append(math.sqrt(w * cfg.gor_weight) * r["gor"])
    return out
