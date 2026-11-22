"""Markdown validation report and diagnostic figures."""
from __future__ import annotations

import math
from pathlib import Path
from typing import Any, Dict, List


def _f(v: Any, fmt: str = "%.4g") -> str:
    if v is None:
        return "-"
    if isinstance(v, float):
        return "-" if not math.isfinite(v) else fmt % v
    return str(v)


def _pct(v: Any) -> str:
    return "-" if v is None or (isinstance(v, float) and not math.isfinite(v)) else "%.2f" % (100.0 * v)


def figures(rows: List[Dict[str, Any]], history: List[Dict[str, float]], out_dir: Path) -> List[Path]:
    """Diagnostic plots (skipped quietly when matplotlib is unavailable)."""
    try:
        import matplotlib
        matplotlib.use("Agg")
        import matplotlib.pyplot as plt
    except ImportError:
        return []
    out_dir.mkdir(parents=True, exist_ok=True)
    ok = [r for r in rows if r["status"] == "ok"]
    paths = []
    for key, label in (("oil", "Oil rate (Sm3/h)"), ("gas", "Gas rate (Sm3/h)"), ("gor", "GOR (Sm3/Sm3)")):
        fig, ax = plt.subplots(figsize=(5.5, 5))
        suffix = "Sm3_Sm3" if key == "gor" else "Sm3_h"
        pts = [(r["ref_%s_%s" % (key, suffix)], r["proxy_%s_%s" % (key, suffix)]) for r in ok]
        pts = [(a, b) for a, b in pts if math.isfinite(a) and math.isfinite(b) and (key != "gor" or (a > 0 and b > 0))]
        x = [a for a, _ in pts]
        y = [b for _, b in pts]
        ax.scatter(x, y, s=18)
        if x:
            lo, hi = min(x + y), max(x + y)
            ax.plot([lo, hi], [lo, hi], "k--", lw=0.8, label="1:1")
        if key == "gor":
            ax.set_xscale("log")
            ax.set_yscale("log")
        ax.set_xlabel("Reference " + label)
        ax.set_ylabel("Proxy " + label)
        ax.set_title("Proxy versus reference: %s" % ("GOR" if key == "gor" else key))
        ax.legend()
        fig.tight_layout()
        p = out_dir / ("parity_%s.png" % key)
        fig.savefig(p, dpi=130)
        plt.close(fig)
        paths.append(p)
    fig, ax = plt.subplots(figsize=(8, 4))
    idx = [r["index"] for r in ok]
    ax.plot(idx, [100 * r["oil_rel_err"] if math.isfinite(r["oil_rel_err"]) else math.nan for r in ok], "o-", ms=3,
            label="oil")
    ax.plot(idx, [100 * r["gas_rel_err"] for r in ok], "s-", ms=3, label="gas")
    ax.axhline(0, color="k", lw=0.6)
    ax.set_xlabel("Fluid index")
    ax.set_ylabel("Relative error (%)")
    ax.set_title("Proxy error per fluid")
    ax.legend()
    fig.tight_layout()
    p = out_dir / "error_vs_fluid.png"
    fig.savefig(p, dpi=130)
    plt.close(fig)
    paths.append(p)
    fig, ax = plt.subplots(figsize=(8, 4))
    dry = [r["fluid_class"] == "dry_gas" for r in ok]
    ax.bar(idx, [100 * r["objective_share"] for r in ok], color=["tab:red" if d else "tab:blue" for d in dry])
    ax.set_xlabel("Fluid index (red = dry gas)")
    ax.set_ylabel("Share of objective (%)")
    ax.set_title("Contribution to the objective per fluid")
    fig.tight_layout()
    p = out_dir / "objective_contribution.png"
    fig.savefig(p, dpi=130)
    plt.close(fig)
    paths.append(p)
    if history:
        fig, ax = plt.subplots(figsize=(6, 4))
        ax.semilogy([h["evaluation"] for h in history], [max(h["objective"], 1e-16) for h in history], "o-", ms=3)
        ax.set_xlabel("Objective evaluation")
        ax.set_ylabel("Best objective")
        ax.set_title("Optimizer convergence")
        fig.tight_layout()
        p = out_dir / "convergence.png"
        fig.savefig(p, dpi=130)
        plt.close(fig)
        paths.append(p)
    return paths


def write_report(path: Path, s: Dict[str, Any]) -> Path:
    """Write the Markdown validation report from the run summary ``s``."""
    pp = s["definition"]["proxy_process"]
    cal = pp["calibration"]
    m = s["metrics"]
    L: List[str] = []
    a = L.append
    a("# PVT forward-proxy calibration: %s" % s["name"])
    a("")
    st = cal["status"]
    a("**Status: %s.** %s" % (st["status"].upper().replace("_", " "),
                               "; ".join(st["reasons"]) if st["reasons"] else "All acceptance criteria met."))
    a("")
    a("Oil MAPE %.2f %%, gas MAPE %.2f %%, GOR MAPE %.2f %% (diagnostic) over %d of %d fluids; %d reference and %d "
      "proxy failures." % (m["oil"]["mape_pct"], m["gas"]["mape_pct"], m["gor"]["mape_pct"], m["n_ok"], m["n_fluids"],
                           m["n_reference_failed"], m["n_proxy_failed"]))
    a("")
    a("> Purpose: %s" % pp["purpose"])
    a("")
    a("## 1. Fitted proxy (one common set for all fluids)")
    a("")
    a("| # | Unit | Kind | P (bara) | T (degC) | Liquid to | Vapour to |")
    a("|---|---|---|---|---|---|---|")
    for u in pp["sequence"]:
        a("| %d | %s | %s | %.3f | %.2f | %d | %d |" % (u["number"], u["name"], u["kind"], u["pressure_bara"],
                                                      u["temperature_C"], u["liquid_destination"], u["vapour_destination"]))
    a("")
    a("Destination 0 means the product stream. The stock tank is the flash to standard conditions (%.2f K, %.5f bara)."
      % (pp["standard_conditions"]["temperature_K"], pp["standard_conditions"]["pressure_bara"]))
    a("")
    a("## 2. Input data and basis")
    a("")
    a("- Fluid set: `%s`, %d fluids (%s). Generation methods: %s." % (
        cal["fluid_set_id"], cal["number_of_fluids"], ", ".join("%s %d" % kv for kv in sorted(s["methods"].items())),
        ", ".join(sorted(s["methods"]))))
    if s["rejected"]:
        a("- Rejected fluid candidates: %d (%s)." % (len(s["rejected"]), "; ".join(
            "%s: %s" % (r["id"], r["reason"]) for r in s["rejected"][:5])))
    if cal["number_of_fluids"] < 30:
        a("- **Note:** fewer than 30 fluids; the specification recommends at least 30 for a representative calibration.")
    a("- Reference process: `%s` (type %s). Feed %.2f bara, %.2f degC, %.4g kmol/h for every fluid." % (
        cal["reference_process_id"], cal["reference_process_type"], pp["feed"]["pressure_bara"],
        pp["feed"]["temperature_C"], pp["feed"]["rate_kmol_h"]))
    a("- Oil: stock-tank liquid volume at standard conditions (Sm3/h). Gas: all gas leaving the process incl. "
      "stock-tank gas, ideal-gas standard volume %.4f Sm3/kmol. Water is excluded from the fluid set." %
      pp["standard_conditions"]["sm3_per_kmol"])
    a("- Reference topology and proxy topology: the reference may contain recycle; the proxy has none "
      "(scrubber liquid goes forward only).")
    a("")
    a("## 3. Optimization")
    a("")
    a("| Variable | Lower | Upper | Initial | Fitted | At bound | Resolution, others re-fit | Resolution, others fixed | Determined by data |")
    a("|---|---|---|---|---|---|---|---|---|")

    def _res(v, key):
        x = v.get(key)
        if x is None:
            return "-"
        return "> range" if (not math.isfinite(x) or x > v["upper"] - v["lower"]) else "%.3g" % x

    for v in s["variables"]:
        det = "-" if v.get("poorly_determined") is None else ("**poorly**" if v["poorly_determined"] else "yes")
        a("| %s | %s | %s | %s | %s | %s | %s | %s | %s |" % (
            v["key"], _f(v["lower"]), _f(v["upper"]), _f(v["initial"]), _f(v["fitted"]),
            ("**%s**" % v["at_bound"]) if v.get("at_bound") else "-", _res(v, "resolution"),
            _res(v, "resolution_alone"), det))
    bound = [v["key"] for v in s["variables"] if v.get("at_bound")]
    if bound:
        a("")
        a("Variables at a bound: %s. The optimum lies on the bound; widen the bound only if the wider value is "
          "physically realistic for the surface process, otherwise accept the bound or fix the variable." %
          ", ".join(bound))
    ident = cal.get("identifiability") or {}
    if ident.get("available"):
        a("")
        a("Identifiability: Jacobian condition number %.3g over %d residuals. Resolution is the change of one "
          "variable that raises the rms rate residual by 0.1 %% (with the other variables re-fitted, or held fixed). %s" % (
              ident["condition_number"], ident["n_residuals"],
              ("Poorly determined: %s - several combinations of these conditions give practically the same oil "
               "and gas rates; report them with their resolution, fix them from the design basis, or use "
               "`objective.prior_weight` to stay near the design values." % ", ".join(ident["poorly_determined"]))
              if ident["poorly_determined"] else "All free variables are determined by the rate data."))
    o = cal["objective"]
    a("")
    a("Objective: `%s`; loss `%s` (scale %.3g); weights oil %.3g, gas %.3g, GOR %.3g; eps oil %.4g Sm3/h, "
      "gas %.4g Sm3/h. Dry-gas control: fluids with GOR > %.0f weighted x%.3g. Prior weight %.3g." % (
          o["formula"], o["loss"], o["residual_scale"], o["oil_weight"], o["gas_weight"], o["gor_weight"],
          o["eps_oil_Sm3_h"], o["eps_gas_Sm3_h"], o["dry_gas_gor_threshold_Sm3_Sm3"], o["dry_gas_weight"],
          o["prior_weight"]))
    opt = cal["optimizer"]
    a("")
    a("Optimizer `%s` (seed %s): %d objective evaluations, %d rejected candidates (invalid pressure sequence), "
      "%.1f s; objective %.6g (data part %.6g; initial %.6g); converged: %s." % (
          opt["method"], opt["seed"], opt["evaluations"], opt["rejected_candidates"], opt["seconds"],
          opt["objective"], opt["data_objective"], opt["initial_objective"], opt["success"]))
    sens = s["start_sensitivity"]
    if sens.get("available"):
        a("")
        a("Sensitivity to start: %d starts, %d within 5 %% of the best objective. Parameter spread near best: %s." % (
            sens["n_starts"], sens["n_near_best"], ", ".join("%s %.3g" % (k, v["range"])
                                                             for k, v in sens["parameter_spread_near_best"].items())))
    a("")
    a("## 4. Error metrics")
    a("")
    a("| Quantity | MAPE % | Bias % | P50 abs % | P90 abs % | Max abs % | RMS % |")
    a("|---|---|---|---|---|---|---|")
    for k in ("oil", "gas", "gor"):
        q = m[k]
        a("| %s%s | %s | %s | %s | %s | %s | %s |" % (k, " (diagnostic)" if k == "gor" else "", _f(q["mape_pct"], "%.2f"),
                                                     _f(q["bias_pct"], "%.2f"), _f(q["p50_abs_pct"], "%.2f"),
                                                     _f(q["p90_abs_pct"], "%.2f"), _f(q["max_abs_pct"], "%.2f"),
                                                     _f(q["rms_pct"], "%.2f")))
    a("")
    a("Root-mean-square normalized error %.2f %%. Maximum absolute errors: oil %.4g Sm3/h, gas %.4g Sm3/h." % (
        m["rms_normalized_error_pct"], m["max_abs_oil_err_Sm3_h"], m["max_abs_gas_err_Sm3_h"]))
    a("")
    a("### By fluid class")
    a("")
    a("| Class | n | Oil MAPE % | Oil bias % | Gas MAPE % | GOR MAPE % |")
    a("|---|---|---|---|---|---|")
    for cls, q in s["by_class"].items():
        a("| %s | %d | %s | %s | %s | %s |" % (cls, q["n"], _f(q["oil"]["mape_pct"], "%.2f"), _f(q["oil"]["bias_pct"], "%.2f"),
                                              _f(q["gas"]["mape_pct"], "%.2f"), _f(q["gor"]["mape_pct"], "%.2f")))
    a("")
    a("### Dry-gas dominance")
    a("")
    d = s["dominance"]
    a("Dry-gas fluids: %d, share of objective %.1f %%. %s" % (
        len(d["dry_gas_fluids"]), 100 * d["dry_gas_share"],
        ("Flags: " + "; ".join(d["flags"])) if d["flags"] else "No single fluid or dry-gas group dominates the fit."))
    a("")
    a("### Worst-fit fluids")
    a("")
    a("| Ranking | Fluid | Class | Ref GOR | Oil err % | Gas err % | GOR err % | Share of J % |")
    a("|---|---|---|---|---|---|---|---|")
    for w in s["worst"]:
        a("| %s | %s | %s | %s | %s | %s | %s | %s |" % (w["ranking"], w["fluid_id"], w["fluid_class"],
                                                        _f(w["ref_gor_Sm3_Sm3"], "%.0f"), _pct(w["oil_rel_err"]),
                                                        _pct(w["gas_rel_err"]), _pct(w["gor_rel_err"]),
                                                        _pct(w["objective_share"])))
    failed = [r for r in s["rows"] if r["status"] != "ok"]
    a("")
    a("### Failed cases")
    a("")
    if failed:
        for r in failed:
            a("- %s: %s - %s" % (r["fluid_id"], r["status"], r["failure_reason"]))
    else:
        a("None.")
    a("")
    a("## 5. Applicability range")
    a("")
    a("| Descriptor | Min | Max |")
    a("|---|---|---|")
    for k, v in cal["calibration_range"].items():
        a("| %s | %.5g | %.5g |" % (k, v["min"], v["max"]))
    a("")
    a("Fluids outside this range are evaluated with an explicit extrapolation warning (`pvt_proxy evaluate`).")
    a("")
    a("## 6. Limitations")
    a("")
    for lim in s["limitations"]:
        a("- " + lim)
    if s["figures"]:
        a("")
        a("## 7. Figures")
        a("")
        for p in s["figures"]:
            a("![%s](%s)" % (Path(p).stem.replace("_", " "), Path(p).relative_to(path.parent).as_posix()))
            a("")
    path.write_text("\n".join(L) + "\n", encoding="utf-8")
    return path
