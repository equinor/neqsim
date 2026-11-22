"""Validation of a fitted proxy: per-fluid errors, aggregate metrics, classes and applicability."""
from __future__ import annotations

import math
from statistics import mean, median
from typing import Any, Dict, List, Optional, Sequence

from . import objective as obj
from .config import AcceptanceConfig, ObjectiveConfig
from .fluids import classify, descriptors


def _rel(p: float, r: float) -> float:
    if not (math.isfinite(p) and math.isfinite(r)) or r == 0:
        return math.nan
    return (p - r) / r


def per_fluid_rows(cases, refs, proxies, weights, breakdown: obj.Breakdown, desc: Dict[str, Dict[str, float]]) -> List[Dict[str, Any]]:
    """One row per fluid with reference, proxy, errors, weight and objective contribution."""
    rows = []
    total = breakdown.total
    for i, c in enumerate(cases):
        r, p = refs.get(c.id) or {}, proxies.get(c.id) or {}
        ok_r, ok_p = r.get("status") == "ok", p.get("status") == "ok"
        status = "ok" if ok_r and ok_p else ("reference_failed" if not ok_r else "proxy_failed")
        reason = r.get("reason") if not ok_r else (p.get("reason") if not ok_p else "")
        nan = math.nan
        row = {
            "index": i, "fluid_id": c.id, "method": c.method,
            "fluid_class": classify(r.get("gor_Sm3_Sm3") if ok_r else None),
            "status": status, "failure_reason": reason or "",
            "ref_oil_Sm3_h": r.get("oil_Sm3_h", nan) if ok_r else nan,
            "proxy_oil_Sm3_h": p.get("oil_Sm3_h", nan) if ok_p else nan,
            "ref_gas_Sm3_h": r.get("gas_Sm3_h", nan) if ok_r else nan,
            "proxy_gas_Sm3_h": p.get("gas_Sm3_h", nan) if ok_p else nan,
            "ref_gor_Sm3_Sm3": r.get("gor_Sm3_Sm3", nan) if ok_r else nan,
            "proxy_gor_Sm3_Sm3": p.get("gor_Sm3_Sm3", nan) if ok_p else nan,
            "user_weight": c.weight, "effective_weight": weights.get(c.id, 0.0),
            "objective_contribution": breakdown.contributions.get(c.id, 0.0),
            "objective_share": (breakdown.contributions.get(c.id, 0.0) / total) if total > 0 else 0.0,
        }
        row["oil_abs_err_Sm3_h"] = row["proxy_oil_Sm3_h"] - row["ref_oil_Sm3_h"]
        row["oil_rel_err"] = _rel(row["proxy_oil_Sm3_h"], row["ref_oil_Sm3_h"])
        row["gas_abs_err_Sm3_h"] = row["proxy_gas_Sm3_h"] - row["ref_gas_Sm3_h"]
        row["gas_rel_err"] = _rel(row["proxy_gas_Sm3_h"], row["ref_gas_Sm3_h"])
        row["gor_rel_err"] = _rel(row["proxy_gor_Sm3_Sm3"], row["ref_gor_Sm3_Sm3"])
        row.update(desc.get(c.id, {}))
        rows.append(row)
    return rows


def _pct(values: Sequence[float], q: float) -> float:
    vals = sorted(values)
    if not vals:
        return math.nan
    k = (len(vals) - 1) * q
    lo, hi = math.floor(k), math.ceil(k)
    return vals[lo] + (vals[hi] - vals[lo]) * (k - lo)


def _stats(rel: Sequence[float]) -> Dict[str, float]:
    rel = [v for v in rel if math.isfinite(v)]
    if not rel:
        return {"mape_pct": math.nan, "bias_pct": math.nan, "max_abs_pct": math.nan, "p50_abs_pct": math.nan,
                "p90_abs_pct": math.nan, "rms_pct": math.nan, "n": 0}
    a = [abs(v) for v in rel]
    return {"mape_pct": 100 * mean(a), "bias_pct": 100 * mean(rel), "max_abs_pct": 100 * max(a),
            "p50_abs_pct": 100 * _pct(a, 0.5), "p90_abs_pct": 100 * _pct(a, 0.9),
            "rms_pct": 100 * math.sqrt(mean([v * v for v in rel])), "n": len(rel)}


def aggregate(rows: List[Dict[str, Any]]) -> Dict[str, Any]:
    """Aggregate oil, gas and GOR metrics (reported separately)."""
    ok = [r for r in rows if r["status"] == "ok"]
    sq = [(r["oil_rel_err"] ** 2 + r["gas_rel_err"] ** 2) / 2.0 for r in ok
          if math.isfinite(r["oil_rel_err"]) and math.isfinite(r["gas_rel_err"])]
    rms_norm = math.sqrt(mean(sq)) if sq else math.nan
    return {"oil": _stats([r["oil_rel_err"] for r in ok]), "gas": _stats([r["gas_rel_err"] for r in ok]),
            "gor": _stats([r["gor_rel_err"] for r in ok]),
            "max_abs_oil_err_Sm3_h": max((abs(r["oil_abs_err_Sm3_h"]) for r in ok), default=math.nan),
            "max_abs_gas_err_Sm3_h": max((abs(r["gas_abs_err_Sm3_h"]) for r in ok), default=math.nan),
            "rms_normalized_error_pct": 100 * rms_norm,
            "n_fluids": len(rows), "n_ok": len(ok),
            "n_reference_failed": sum(r["status"] == "reference_failed" for r in rows),
            "n_proxy_failed": sum(r["status"] == "proxy_failed" for r in rows)}


def by_class(rows: List[Dict[str, Any]]) -> Dict[str, Dict[str, Any]]:
    """Oil and gas statistics per fluid class."""
    out = {}
    for cls in sorted({r["fluid_class"] for r in rows}):
        sub = [r for r in rows if r["fluid_class"] == cls and r["status"] == "ok"]
        out[cls] = {"n": len(sub), "oil": _stats([r["oil_rel_err"] for r in sub]),
                    "gas": _stats([r["gas_rel_err"] for r in sub]), "gor": _stats([r["gor_rel_err"] for r in sub])}
    return out


def worst(rows: List[Dict[str, Any]], n: int = 5) -> List[Dict[str, Any]]:
    """Worst fluids by objective contribution and by absolute oil error."""
    ok = [r for r in rows if r["status"] == "ok"]
    by_contrib = sorted(ok, key=lambda r: r["objective_contribution"], reverse=True)[:n]
    by_oil = sorted([r for r in ok if math.isfinite(r["oil_rel_err"])], key=lambda r: abs(r["oil_rel_err"]),
                    reverse=True)[:n]
    keys = ("fluid_id", "fluid_class", "ref_gor_Sm3_Sm3", "oil_rel_err", "gas_rel_err", "gor_rel_err", "objective_share")
    return [{"ranking": "objective_contribution", **{k: r[k] for k in keys}} for r in by_contrib] + \
           [{"ranking": "abs_oil_error", **{k: r[k] for k in keys}} for r in by_oil]


def calibration_range(rows: List[Dict[str, Any]]) -> Dict[str, Dict[str, float]]:
    """Min/max of the composition descriptors and reference GOR over the fitted fluids."""
    ok = [r for r in rows if r["status"] != "reference_failed" and r["effective_weight"] > 0]
    keys = ("feed_molar_mass_kg_kmol", "c1_mole_fraction", "c7plus_mole_fraction", "ref_gor_Sm3_Sm3")
    out = {}
    for k in keys:
        vals = [r[k] for r in ok if k in r and math.isfinite(r[k])]
        if vals:
            out[k] = {"min": min(vals), "max": max(vals)}
    return out


def extrapolation_warnings(values: Dict[str, float], rng: Dict[str, Dict[str, float]], rel_tol: float = 0.02) -> List[str]:
    """Warnings when a fluid lies outside the calibration range (with a small tolerance)."""
    out = []
    for k, lim in rng.items():
        v = values.get(k)
        if v is None or not math.isfinite(v):
            continue
        span = max(lim["max"] - lim["min"], abs(lim["max"]) * 1e-6, 1e-12)
        if v < lim["min"] - rel_tol * span or v > lim["max"] + rel_tol * span:
            out.append("%s = %.6g is outside the calibration range [%.6g, %.6g]; the proxy is extrapolating"
                       % (k, v, lim["min"], lim["max"]))
    return out


def start_sensitivity(starts: List[Dict[str, Any]], best_fun: float) -> Dict[str, Any]:
    """Spread of the parameters found from independent (multistart) starts with near-best objective.

    Entries of a sequential global/local run (``stage`` key) are not independent starts and are ignored.
    """
    starts = [s for s in starts if "stage" not in s]
    if len(starts) < 2:
        return {"available": False}
    near = [s for s in starts if s["fun"] <= best_fun * 1.05 + 1e-12]
    keys = sorted({k for s in starts for k in s.get("free_values", {})})
    spread = {}
    for k in keys:
        vals = [s["free_values"][k] for s in near if k in s.get("free_values", {})]
        if vals:
            spread[k] = {"min": min(vals), "max": max(vals), "range": max(vals) - min(vals)}
    return {"available": True, "n_starts": len(starts), "n_near_best": len(near),
            "objectives": [s["fun"] for s in starts], "parameter_spread_near_best": spread}


def status(metrics: Dict[str, Any], dominance: Dict[str, Any], acc: AcceptanceConfig) -> Dict[str, Any]:
    """``pass`` or ``review_required`` with the reasons."""
    reasons = []
    if not metrics["oil"]["mape_pct"] <= acc.oil_mape_pct:
        reasons.append("oil MAPE %.2f %% > %.2f %%" % (metrics["oil"]["mape_pct"], acc.oil_mape_pct))
    if not metrics["gas"]["mape_pct"] <= acc.gas_mape_pct:
        reasons.append("gas MAPE %.2f %% > %.2f %%" % (metrics["gas"]["mape_pct"], acc.gas_mape_pct))
    if not metrics["oil"]["p90_abs_pct"] <= acc.oil_p90_abs_pct:
        reasons.append("oil P90 abs error %.2f %% > %.2f %%" % (metrics["oil"]["p90_abs_pct"], acc.oil_p90_abs_pct))
    n_failed = metrics["n_reference_failed"] + metrics["n_proxy_failed"]
    if n_failed > acc.max_failed:
        reasons.append("%d failed simulation(s) > %d allowed" % (n_failed, acc.max_failed))
    if dominance.get("dominated") and not acc.allow_dominance:
        reasons.extend(dominance["flags"])
    return {"status": "pass" if not reasons else "review_required", "reasons": reasons}


def fluid_descriptors(cases, names, molar_masses) -> Dict[str, Dict[str, float]]:
    """Composition descriptors per fluid id."""
    return {c.id: descriptors(c.z, names, molar_masses) for c in cases}


def median_or_nan(values: Sequence[float]) -> float:
    """Median of finite values."""
    vals = [v for v in values if math.isfinite(v)]
    return median(vals) if vals else math.nan


def objective_summary(cfg: ObjectiveConfig, norm: obj.Normalization) -> Dict[str, Any]:
    """Exact objective definition stored with the proxy."""
    return {"formula": "J = sum_j w_j [w_o L(r_oil) + w_g L(r_gas) + w_GOR L(r_GOR)], "
                       "r_x = (Q_x,proxy - Q_x,ref) / (Q_x,ref + eps_x)",
            "loss": cfg.loss, "residual_scale": cfg.residual_scale,
            "oil_weight": cfg.oil_weight, "gas_weight": cfg.gas_weight, "gor_weight": cfg.gor_weight,
            "eps_oil_Sm3_h": norm.eps_oil, "eps_gas_Sm3_h": norm.eps_gas, "eps_gor_Sm3_Sm3": norm.eps_gor,
            "eps_fractions_of_median": {"oil": cfg.eps_oil_fraction, "gas": cfg.eps_gas_fraction,
                                        "gor": cfg.eps_gor_fraction},
            "dry_gas_gor_threshold_Sm3_Sm3": cfg.dry_gas_gor_threshold, "dry_gas_weight": cfg.dry_gas_weight,
            "failed_penalty": cfg.failed_penalty, "dominance_share": cfg.dominance_share,
            "prior_weight": cfg.prior_weight,
            "prior": "prior_weight * sum_k (u_k - u0_k)^2 over free variables scaled to [0, 1] by their bounds"}
