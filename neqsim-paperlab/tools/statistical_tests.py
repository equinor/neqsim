"""
Statistical Tests — Generates publication-ready statistical analysis for benchmark results.

Supports bootstrapped confidence intervals, Wilcoxon signed-rank tests,
effect size (Cohen's d), and formatted LaTeX/Markdown tables.

Usage::

    from tools.statistical_tests import analyze_benchmarks, print_stats_report

    report = analyze_benchmarks("papers/my_paper/")
    print_stats_report(report)
"""

import json
import math
from pathlib import Path
from typing import Dict, List, Optional, Tuple

import numpy as np
from scipy import stats as sp_stats


def _bootstrap_ci(data, n_boot=10000, ci=0.95, statistic=np.mean):
    """Compute bootstrap confidence interval for a statistic.

    Args:
        data: Array-like of observations.
        n_boot: Number of bootstrap resamples.
        ci: Confidence level (e.g. 0.95 for 95% CI).
        statistic: Function to compute (default: np.mean).

    Returns:
        Tuple of (point_estimate, ci_lower, ci_upper).
    """
    arr = np.asarray(data, dtype=float)
    arr = arr[~np.isnan(arr)]
    if len(arr) < 2:
        val = statistic(arr) if len(arr) == 1 else float("nan")
        return (val, val, val)

    rng = np.random.default_rng(42)
    boot_stats = np.array([
        statistic(rng.choice(arr, size=len(arr), replace=True))
        for _ in range(n_boot)
    ])
    alpha = 1 - ci
    lower = float(np.percentile(boot_stats, 100 * alpha / 2))
    upper = float(np.percentile(boot_stats, 100 * (1 - alpha / 2)))
    return (float(statistic(arr)), lower, upper)


def _cohens_d(a, b):
    """Compute Cohen's d effect size between two samples.

    Args:
        a: First sample (array-like).
        b: Second sample (array-like).

    Returns:
        Cohen's d value.
    """
    a = np.asarray(a, dtype=float)
    b = np.asarray(b, dtype=float)
    a = a[~np.isnan(a)]
    b = b[~np.isnan(b)]
    if len(a) < 2 or len(b) < 2:
        return float("nan")
    pooled_std = math.sqrt(((len(a) - 1) * np.var(a, ddof=1) +
                             (len(b) - 1) * np.var(b, ddof=1)) /
                            (len(a) + len(b) - 2))
    if pooled_std == 0:
        return 0.0
    return float((np.mean(a) - np.mean(b)) / pooled_std)


def _effect_size_label(d):
    """Interpret Cohen's d magnitude."""
    d = abs(d)
    if d < 0.2:
        return "negligible"
    elif d < 0.5:
        return "small"
    elif d < 0.8:
        return "medium"
    else:
        return "large"


def _wilcoxon_test(a, b):
    """Wilcoxon signed-rank test for paired samples.

    Args:
        a: First sample (array-like, paired with b).
        b: Second sample (array-like, paired with a).

    Returns:
        Dict with test statistic and p-value.
    """
    a = np.asarray(a, dtype=float)
    b = np.asarray(b, dtype=float)
    mask = ~(np.isnan(a) | np.isnan(b))
    a, b = a[mask], b[mask]
    if len(a) < 6:
        return {"statistic": None, "p_value": None, "note": "Too few paired samples (<6)"}
    try:
        stat, pval = sp_stats.wilcoxon(a, b)
        return {"statistic": float(stat), "p_value": float(pval)}
    except Exception as e:
        return {"statistic": None, "p_value": None, "note": str(e)}


def _mann_whitney_test(a, b):
    """Mann-Whitney U test for independent samples.

    Args:
        a: First sample (array-like).
        b: Second sample (array-like).

    Returns:
        Dict with U statistic and p-value.
    """
    a = np.asarray(a, dtype=float)
    b = np.asarray(b, dtype=float)
    a = a[~np.isnan(a)]
    b = b[~np.isnan(b)]
    if len(a) < 3 or len(b) < 3:
        return {"statistic": None, "p_value": None, "note": "Too few samples (<3)"}
    try:
        stat, pval = sp_stats.mannwhitneyu(a, b, alternative='two-sided')
        return {"statistic": float(stat), "p_value": float(pval)}
    except Exception as e:
        return {"statistic": None, "p_value": None, "note": str(e)}


def _load_benchmark_results(paper_dir):
    """Load all benchmark summary JSON files from a paper's results directory.

    Args:
        paper_dir: Path to the paper directory.

    Returns:
        Dict mapping summary filename to its loaded data.
    """
    paper_dir = Path(paper_dir)
    results_dir = paper_dir / "results"
    summaries = {}
    if results_dir.exists():
        for f in sorted(results_dir.glob("summary_*.json")):
            with open(f) as fh:
                summaries[f.stem] = json.load(fh)
    # Also check results.json at root
    root_results = paper_dir / "results.json"
    if root_results.exists():
        with open(root_results) as fh:
            summaries["results"] = json.load(fh)
    return summaries


def _extract_metric_arrays(summaries, metric_name="iteration_count"):
    """Extract a named metric as arrays from loaded benchmark summaries.

    Looks for `per_case` arrays within each summary, or flattened metric lists.

    Args:
        summaries: Dict of loaded summary data.
        metric_name: Name of the metric to extract per algorithm/method.

    Returns:
        Dict mapping method name to numpy array of values.
    """
    methods = {}
    for sname, data in summaries.items():
        per_case = data.get("per_case", [])
        if per_case:
            for case in per_case:
                method = case.get("method", case.get("algorithm", sname))
                val = case.get(metric_name)
                if val is not None:
                    methods.setdefault(method, []).append(float(val))

        # Also check top-level method_results
        method_results = data.get("method_results", {})
        for mname, mdata in method_results.items():
            vals = mdata.get(metric_name, [])
            if isinstance(vals, list) and vals:
                methods.setdefault(mname, []).extend([float(v) for v in vals])

    return {k: np.array(v) for k, v in methods.items()}


def analyze_benchmarks(paper_dir, metrics=None, ci_level=0.95, n_boot=10000):
    """Run statistical analysis on benchmark results in a paper directory.

    Computes per-method descriptive stats, bootstrapped CIs, pairwise Wilcoxon
    tests, and effect sizes.

    Args:
        paper_dir: Path to the paper directory.
        metrics: List of metric names to analyze (default: common set).
        ci_level: Confidence interval level (default: 0.95).
        n_boot: Number of bootstrap resamples.

    Returns:
        Analysis report dict.
    """
    paper_dir = Path(paper_dir)
    summaries = _load_benchmark_results(paper_dir)
    if not summaries:
        return {"error": f"No benchmark results found in {paper_dir}"}

    if metrics is None:
        metrics = ["iteration_count", "converged", "elapsed_ms",
                   "relative_error", "absolute_error"]

    report = {
        "paper_dir": str(paper_dir),
        "summaries_loaded": len(summaries),
        "metrics": {},
    }

    for metric in metrics:
        method_data = _extract_metric_arrays(summaries, metric)
        if not method_data:
            continue

        metric_report = {"methods": {}, "pairwise": []}

        # Descriptive stats + bootstrap CI per method
        for mname, arr in sorted(method_data.items()):
            point, ci_lo, ci_hi = _bootstrap_ci(arr, n_boot=n_boot, ci=ci_level)
            metric_report["methods"][mname] = {
                "n": len(arr),
                "mean": float(np.mean(arr)),
                "median": float(np.median(arr)),
                "std": float(np.std(arr, ddof=1)) if len(arr) > 1 else 0.0,
                "min": float(np.min(arr)),
                "max": float(np.max(arr)),
                "ci_level": ci_level,
                "ci_lower": ci_lo,
                "ci_upper": ci_hi,
            }

        # Pairwise comparisons
        method_names = sorted(method_data.keys())
        for i in range(len(method_names)):
            for j in range(i + 1, len(method_names)):
                m1, m2 = method_names[i], method_names[j]
                a, b = method_data[m1], method_data[m2]

                # Try paired test if same length, else independent
                if len(a) == len(b):
                    test_result = _wilcoxon_test(a, b)
                    test_name = "Wilcoxon signed-rank"
                else:
                    test_result = _mann_whitney_test(a, b)
                    test_name = "Mann-Whitney U"

                d = _cohens_d(a, b)
                metric_report["pairwise"].append({
                    "method_a": m1,
                    "method_b": m2,
                    "test": test_name,
                    "statistic": test_result.get("statistic"),
                    "p_value": test_result.get("p_value"),
                    "significant_005": (test_result.get("p_value") or 1.0) < 0.05,
                    "cohens_d": d,
                    "effect_size": _effect_size_label(d),
                    "note": test_result.get("note"),
                })

        report["metrics"][metric] = metric_report

    return report


def format_stats_latex(report, metric="iteration_count"):
    """Format statistical results as a LaTeX table.

    Args:
        report: Analysis report from analyze_benchmarks().
        metric: Which metric to format.

    Returns:
        LaTeX table string.
    """
    metric_data = report.get("metrics", {}).get(metric, {})
    methods = metric_data.get("methods", {})
    if not methods:
        return "% No data for metric: " + metric

    ci_level = next(iter(methods.values())).get("ci_level", 0.95)
    ci_pct = int(ci_level * 100)

    lines = [
        "\\begin{table}[htbp]",
        f"\\caption{{Descriptive statistics and {ci_pct}\\% bootstrap confidence intervals "
        f"for {metric.replace('_', ' ')}.}}",
        f"\\label{{tab:{metric}_stats}}",
        "\\centering",
        "\\begin{tabular}{lrrrrrr}",
        "\\toprule",
        f"Method & $n$ & Mean & Median & Std & CI$_{{\\text{{lo}}}}$ & CI$_{{\\text{{hi}}}}$ \\\\",
        "\\midrule",
    ]
    for mname, s in sorted(methods.items()):
        lines.append(
            f"{mname} & {s['n']} & {s['mean']:.4f} & {s['median']:.4f} & "
            f"{s['std']:.4f} & {s['ci_lower']:.4f} & {s['ci_upper']:.4f} \\\\"
        )
    lines += ["\\bottomrule", "\\end{tabular}", "\\end{table}"]
    return "\n".join(lines)


def format_stats_markdown(report, metric="iteration_count"):
    """Format statistical results as a Markdown table.

    Args:
        report: Analysis report from analyze_benchmarks().
        metric: Which metric to format.

    Returns:
        Markdown table string.
    """
    metric_data = report.get("metrics", {}).get(metric, {})
    methods = metric_data.get("methods", {})
    if not methods:
        return f"_No data for metric: {metric}_"

    ci_level = next(iter(methods.values())).get("ci_level", 0.95)
    ci_pct = int(ci_level * 100)

    lines = [
        f"| Method | n | Mean | Median | Std | {ci_pct}% CI |",
        "|--------|---|------|--------|-----|------------|",
    ]
    for mname, s in sorted(methods.items()):
        ci_str = f"[{s['ci_lower']:.4f}, {s['ci_upper']:.4f}]"
        lines.append(
            f"| {mname} | {s['n']} | {s['mean']:.4f} | {s['median']:.4f} | "
            f"{s['std']:.4f} | {ci_str} |"
        )

    # Pairwise tests
    pairwise = metric_data.get("pairwise", [])
    if pairwise:
        lines.append("")
        lines.append("| Comparison | Test | p-value | Significant | Cohen's d | Effect |")
        lines.append("|------------|------|---------|-------------|-----------|--------|")
        for pw in pairwise:
            p_str = f"{pw['p_value']:.4f}" if pw["p_value"] is not None else "N/A"
            sig = "Yes" if pw["significant_005"] else "No"
            d_str = f"{pw['cohens_d']:.3f}" if not math.isnan(pw["cohens_d"]) else "N/A"
            lines.append(
                f"| {pw['method_a']} vs {pw['method_b']} | {pw['test']} | "
                f"{p_str} | {sig} | {d_str} | {pw['effect_size']} |"
            )

    return "\n".join(lines)


def print_stats_report(report):
    """Print a formatted statistical analysis report.

    Args:
        report: Analysis report from analyze_benchmarks().
    """
    if "error" in report:
        print(f"Error: {report['error']}")
        return

    print("=" * 60)
    print("STATISTICAL ANALYSIS REPORT")
    print("=" * 60)
    print(f"  Summaries loaded: {report['summaries_loaded']}")
    print()

    for metric, mdata in report.get("metrics", {}).items():
        print(f"  METRIC: {metric}")
        print(f"  {'─' * 50}")
        methods = mdata.get("methods", {})
        for mname, s in sorted(methods.items()):
            ci_str = f"[{s['ci_lower']:.4f}, {s['ci_upper']:.4f}]"
            print(f"    {mname:20s}  mean={s['mean']:.4f}  std={s['std']:.4f}  "
                  f"n={s['n']}  {int(s['ci_level']*100)}% CI={ci_str}")

        pairwise = mdata.get("pairwise", [])
        if pairwise:
            print()
            for pw in pairwise:
                p_str = f"p={pw['p_value']:.4f}" if pw["p_value"] is not None else "p=N/A"
                d_str = f"d={pw['cohens_d']:.3f}" if not math.isnan(pw["cohens_d"]) else "d=N/A"
                sig = "*" if pw["significant_005"] else ""
                print(f"    {pw['method_a']} vs {pw['method_b']}: "
                      f"{pw['test']} {p_str}{sig}  {d_str} ({pw['effect_size']})")
        print()
