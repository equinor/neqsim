"""End-to-end workflow: fluids -> reference -> calibration -> validation -> exports -> report."""
from __future__ import annotations

import csv
import datetime as _dt
import json
import math
import subprocess
import sys
from collections import Counter
from pathlib import Path
from typing import Any, Callable, Dict, List, Optional

from . import __version__
from . import validate as val
from .calibrate import Calibrator, ProxyEvaluator, run_references
from .config import ProjectConfig, ProxyTopology, StandardConditions, load_config
from .engine import Engine, neqsim_version
from .export import PURPOSE, flow_sequence, run_exports, write_definition
from .fluids import FluidTemplate, generate_ensemble, save_ensemble
from .optimize import ParameterSpace
from .report import figures, write_report

LIMITATIONS = [
    "Equilibrium stages only: no carry-over, entrainment or separation efficiency in reference or proxy.",
    "One common P/T set cannot reproduce recycle effects exactly for every composition; residuals are reported, "
    "not hidden.",
    "Oil volume uses the EOS liquid density at standard conditions; gas uses ideal-gas standard volumes.",
    "Water is excluded; produced-water handling must be added separately if needed.",
    "Valid inside the calibration range only; outside it the proxy is extrapolating.",
    "The proxy defines the surface process for PVT-table generation; it does not replace the production workflow "
    "(Eclipse profiles -> FluidMagic -> OLGA -> NeqSim) for molar production rates.",
]


def _git_commit() -> str:
    try:
        root = Path(__file__).resolve().parents[2]
        r = subprocess.run(["git", "-C", str(root), "rev-parse", "HEAD"], capture_output=True, text=True, timeout=10)
        return r.stdout.strip() or "unknown"
    except Exception:  # noqa: BLE001
        return "unknown"


def prepare(cfg: ProjectConfig, log: Callable = print):
    """Engine, template and fluid ensemble for a configuration."""
    if str(cfg.base_dir) not in sys.path:
        sys.path.insert(0, str(cfg.base_dir))
    engine = Engine(cfg.standard_conditions, cfg.feed)
    template = FluidTemplate.from_dict(cfg.fluids.get("template") or {}, resolve=cfg.resolve)
    e300_base = engine.resolve_template(template)
    ensemble = generate_ensemble(cfg.fluids, template, engine=engine, e300_base=e300_base)
    log("fluid ensemble %s: %d fluids, %d rejected" % (ensemble["id"], len(ensemble["cases"]), len(ensemble["rejected"])))
    return engine, ensemble


def _write_csv(path: Path, rows: List[Dict[str, Any]]) -> None:
    if not rows:
        path.write_text("", encoding="utf-8")
        return
    keys = list(rows[0].keys())
    with open(path, "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=keys)
        w.writeheader()
        for r in rows:
            w.writerow({k: ("" if isinstance(v, float) and not math.isfinite(v) else v) for k, v in r.items()})


def _num(v):
    return v if not (isinstance(v, float) and not math.isfinite(v)) else None


def build_definition(cfg: ProjectConfig, ensemble, result, metrics, cal_range, status, refs) -> Dict[str, Any]:
    """Machine-readable proxy definition."""
    std = cfg.standard_conditions
    stages = [{"name": s.name, "pressure_bara": result.params[s.name][0], "temperature_C": result.params[s.name][1],
               "pressure_fitted": not s.pressure.fixed, "temperature_fitted": not s.temperature.fixed}
              for s in cfg.proxy.stages]
    scr = None
    if cfg.proxy.scrubber is not None:
        sc = cfg.proxy.scrubber
        scr = {"name": sc.name, "pressure_bara": result.params[sc.name][0], "temperature_C": result.params[sc.name][1],
               "pressure_fitted": not sc.pressure.fixed, "temperature_fitted": not sc.temperature.fixed,
               "gas_sources": list(sc.gas_sources), "liquid_destination": sc.liquid_destination}
    std_d = {"temperature_K": std.temperature_K, "pressure_bara": std.pressure_bara, "sm3_per_kmol": std.sm3_per_kmol,
             "gas_volume_basis": "ideal gas at standard conditions"}
    n_fit = sum(1 for w in result.weights.values() if w > 0)
    return {"proxy_process": {
        "name": cfg.proxy.name, "topology": "%d_stage_separator%s" % (
            len(stages), "_with_scrubber" if scr else ""),
        "purpose": PURPOSE,
        "standard_conditions": std_d,
        "feed": {"pressure_bara": cfg.feed.pressure_bara, "temperature_C": cfg.feed.temperature_C,
                 "rate_kmol_h": cfg.feed.rate_kmol_h},
        "units": {"pressure": "bara (absolute)", "temperature": "degC", "oil_rate": "Sm3/h stock-tank liquid at standard conditions",
                  "gas_rate": "Sm3/h ideal gas at standard conditions (all gas leaving, incl. stock-tank gas)",
                  "gor": "Sm3/Sm3", "basis": "per feed rate_kmol_h"},
        "stages": stages, "scrubber": scr,
        "routing": {"liquid": "cascade stage to stage, last stage to stock tank",
                    "gas": "to product unless routed to the scrubber",
                    "scrubber_liquid": scr["liquid_destination"] if scr else None, "recycle": "none (strictly forward)"},
        "sequence": flow_sequence(stages, scr, std_d),
        "calibration": {
            "number_of_fluids": len(ensemble["cases"]), "number_fitted": n_fit, "fluid_set_id": ensemble["id"],
            "reference_process_id": cfg.reference.id, "reference_process_type": cfg.reference.type,
            "primary_targets": ["standard_oil_rate", "standard_gas_rate"],
            "diagnostic_targets": ["GOR"] if cfg.objective.gor_weight == 0 else [],
            "secondary_targets": ["GOR"] if cfg.objective.gor_weight > 0 else [],
            "objective": val.objective_summary(cfg.objective, result.norm),
            "dry_gas_residual_control": "enabled" if (cfg.objective.dry_gas_weight < 1 or cfg.objective.eps_oil_fraction > 0
                                                       or cfg.objective.loss != "squared") else "disabled",
            "optimizer": {"method": result.optimizer["method"], "seed": cfg.optimizer.seed,
                          "settings": {k: getattr(cfg.optimizer, k) for k in cfg.optimizer.__dataclass_fields__},
                          "evaluations": result.n_evaluations, "rejected_candidates": result.n_rejected,
                          "seconds": round(result.seconds, 2), "objective": result.objective,
                          "data_objective": result.data_objective,
                          "initial_objective": result.initial_objective, "success": result.optimizer["success"],
                          "message": result.optimizer["message"]},
            "identifiability": result.identifiability,
            "fit_metrics": {"oil_mape_pct": metrics["oil"]["mape_pct"], "gas_mape_pct": metrics["gas"]["mape_pct"],
                            "gor_mape_pct": metrics["gor"]["mape_pct"], "oil_p90_abs_pct": metrics["oil"]["p90_abs_pct"],
                            "n_failed": metrics["n_reference_failed"] + metrics["n_proxy_failed"]},
            "calibration_range": cal_range, "status": status,
            "fitted_at": _dt.datetime.now(_dt.timezone.utc).replace(microsecond=0).isoformat(),
            "software": {"pvt_proxy": __version__, "neqsim": neqsim_version(), "git_commit": _git_commit()},
            "config_hash": cfg.config_hash(),
        }}}


def run(cfg: ProjectConfig, out_dir: Path, resume: bool = False, log: Callable = print) -> Dict[str, Any]:
    """Run the complete workflow and write every output into ``out_dir``."""
    out_dir = Path(out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    engine, ensemble = prepare(cfg, log)
    if len(ensemble["cases"]) < 30:
        log("note: %d fluids; at least 30 are recommended for a representative calibration" % len(ensemble["cases"]))
    save_ensemble(out_dir / "fluids.json", ensemble)
    refs = run_references(cfg, engine, ensemble, cache_path=out_dir / "reference_cache.json", log=log)
    _write_csv(out_dir / "reference_results.csv", [dict(fluid_id=k, **{kk: _num(vv) for kk, vv in v.items()})
                                                  for k, v in refs.items()])
    cal = Calibrator(cfg, engine, ensemble, refs, out_dir=out_dir, log=log)
    log("calibrating %d free variables on %d fluids with %s" % (cal.space.n, len(cal.evaluator.cases), cfg.optimizer.method))
    result = cal.run(resume=resume)
    rest = [c for c in ensemble["cases"] if c.id not in result.proxies]
    proxies = dict(result.proxies)
    if rest:
        proxies.update(ProxyEvaluator(engine, ensemble["template"], rest, cfg.proxy).evaluate(result.params))
    return finish(cfg, out_dir, engine, ensemble, refs, result, proxies, log)


def finish(cfg, out_dir, engine, ensemble, refs, result, proxies, log=print) -> Dict[str, Any]:
    """Validation, definition, exports and report for a calibration result."""
    template = ensemble["template"]
    desc = val.fluid_descriptors(ensemble["cases"], template.names, engine.molar_masses(template))
    rows = val.per_fluid_rows(ensemble["cases"], refs, proxies, result.weights, result.breakdown, desc)
    metrics = val.aggregate(rows)
    cal_range = val.calibration_range(rows)
    status = val.status(metrics, result.dominance, cfg.acceptance)
    defn = build_definition(cfg, ensemble, result, metrics, cal_range, status, refs)
    written = write_definition(defn, out_dir)
    _write_csv(out_dir / "validation_per_fluid.csv", rows)
    _write_csv(out_dir / "optimization_history.csv", result.history)
    exports = run_exports(defn, cfg.exports, out_dir / "exports")
    figs = figures(rows, result.history, out_dir / "figures")
    space_vars = []
    ident = {v["key"]: v for v in result.identifiability.get("variables", [])}
    for f in ParameterSpace(cfg.proxy, cfg.standard_conditions).free:
        iv = ident.get(f.key, {})
        fitted = result.free_values.get(f.key)
        tol = 1e-3 * (f.upper - f.lower)
        at_bound = None if fitted is None else ("lower" if fitted <= f.lower + tol else
                                                 ("upper" if fitted >= f.upper - tol else ""))
        space_vars.append({"key": f.key, "lower": f.lower, "upper": f.upper, "initial": f.initial,
                           "fitted": fitted, "resolution": iv.get("resolution"),
                           "resolution_alone": iv.get("resolution_alone"),
                           "poorly_determined": iv.get("poorly_determined"), "at_bound": at_bound})
    summary = {
        "name": cfg.name, "definition": defn, "metrics": metrics, "by_class": val.by_class(rows),
        "worst": val.worst(rows), "dominance": {k: v for k, v in result.dominance.items() if k != "shares"},
        "start_sensitivity": val.start_sensitivity(result.optimizer.get("starts", []), result.objective),
        "methods": dict(Counter(c.method for c in ensemble["cases"])), "rejected": ensemble["rejected"],
        "variables": space_vars, "rows": rows, "limitations": LIMITATIONS, "figures": [str(p) for p in figs],
        "outputs": [str(p) for p in written + exports]}
    report = write_report(out_dir / "validation_report.md", summary)
    summary["outputs"].append(str(report))
    slim = {k: v for k, v in summary.items() if k not in ("rows", "definition")}
    (out_dir / "validation_summary.json").write_text(json.dumps(_clean(slim), indent=2), encoding="utf-8")
    log("status %s: oil MAPE %.2f %%, gas MAPE %.2f %%, GOR MAPE %.2f %% -> %s" % (
        status["status"], metrics["oil"]["mape_pct"], metrics["gas"]["mape_pct"], metrics["gor"]["mape_pct"], report))
    return summary


def _clean(o):
    if isinstance(o, float):
        return o if math.isfinite(o) else None
    if isinstance(o, dict):
        return {str(k): _clean(v) for k, v in o.items()}
    if isinstance(o, (list, tuple)):
        return [_clean(v) for v in o]
    return o


def topology_from_definition(defn: Dict[str, Any]) -> ProxyTopology:
    """Fixed proxy topology from a fitted proxy definition."""
    pp = defn["proxy_process"]
    block = {"name": pp["name"], "min_pressure_step_bar": 0.0,
             "stages": [{"name": s["name"], "pressure_bara": float(s["pressure_bara"]),
                         "temperature_C": float(s["temperature_C"])} for s in pp["stages"]]}
    if pp.get("scrubber"):
        sc = pp["scrubber"]
        block["scrubber"] = {"name": sc["name"], "pressure_bara": float(sc["pressure_bara"]),
                             "temperature_C": float(sc["temperature_C"]), "gas_sources": sc["gas_sources"],
                             "liquid_destination": sc["liquid_destination"]}
    return ProxyTopology.from_dict(block)


def evaluate_fluids(defn: Dict[str, Any], cfg: ProjectConfig, ensemble: Dict[str, Any], engine: Engine) -> List[Dict[str, Any]]:
    """Run a fitted proxy on (new) fluids with extrapolation warnings against the calibration range."""
    pp = defn["proxy_process"]
    std = StandardConditions(temperature_K=pp["standard_conditions"]["temperature_K"],
                             pressure_bara=pp["standard_conditions"]["pressure_bara"])
    if abs(std.temperature_K - engine.std.temperature_K) > 1e-9 or abs(std.pressure_bara - engine.std.pressure_bara) > 1e-9:
        raise ValueError("standard conditions of the proxy definition differ from the configuration")
    topo = topology_from_definition(defn)
    from .engine import fixed_parameters
    template = ensemble["template"]
    proxies = ProxyEvaluator(engine, template, ensemble["cases"], topo).evaluate(fixed_parameters(topo))
    desc = val.fluid_descriptors(ensemble["cases"], template.names, engine.molar_masses(template))
    rng = pp["calibration"]["calibration_range"]
    rows = []
    for c in ensemble["cases"]:
        p = proxies[c.id]
        values = dict(desc[c.id])
        if p["status"] == "ok":
            values["ref_gor_Sm3_Sm3"] = p["gor_Sm3_Sm3"]
        warnings = val.extrapolation_warnings(values, rng)
        rows.append({"fluid_id": c.id, "status": p["status"], "reason": p["reason"], "oil_Sm3_h": p["oil_Sm3_h"],
                     "gas_Sm3_h": p["gas_Sm3_h"], "gor_Sm3_Sm3": p["gor_Sm3_Sm3"], "extrapolation": bool(warnings),
                     "warnings": warnings})
    return rows


def run_from_file(config_path: str, out_dir: str, resume: bool = False, log: Callable = print) -> Dict[str, Any]:
    """Convenience wrapper used by the CLI."""
    return run(load_config(config_path), Path(out_dir), resume=resume, log=log)
