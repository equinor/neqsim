"""
Generate publication-quality figures for the Implicit CPA paper.

Reads benchmark_raw.json and benchmark_summary.json from results/
and produces figures in figures/ directory.
"""

import json
import pathlib
import numpy as np
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.ticker import AutoMinorLocator

# Paths
SCRIPT_DIR = pathlib.Path(__file__).resolve().parent
PAPER_DIR = SCRIPT_DIR.parent
RESULTS_DIR = PAPER_DIR / "results"
FIGURES_DIR = PAPER_DIR / "figures"
FIGURES_DIR.mkdir(exist_ok=True)
TABLES_DIR = PAPER_DIR / "tables"
TABLES_DIR.mkdir(exist_ok=True)

# Style
plt.rcParams.update({
    "font.family": "serif",
    "font.size": 11,
    "axes.labelsize": 12,
    "axes.titlesize": 13,
    "legend.fontsize": 10,
    "xtick.labelsize": 10,
    "ytick.labelsize": 10,
    "figure.dpi": 150,
    "savefig.dpi": 300,
})

# Load data
with open(str(RESULTS_DIR / "benchmark_raw.json")) as f:
    raw_data = json.load(f)
with open(str(RESULTS_DIR / "benchmark_summary.json")) as f:
    summary_data = json.load(f)


def fig1_speedup_bar_chart():
    """Figure 1: Bar chart of median speedup by fluid system."""
    systems = []
    speedups = []
    colors = []
    paper_color = "#2196F3"
    industrial_color = "#FF9800"

    for fid, s in summary_data.items():
        if "error" in s or s.get("n_points", 0) == 0:
            continue
        systems.append(s["label"])
        speedups.append(s.get("speedup_median", 1.0))
        colors.append(paper_color if s["category"] == "paper" else industrial_color)

    fig, ax = plt.subplots(figsize=(10, 5))
    x = np.arange(len(systems))
    bars = ax.bar(x, speedups, color=colors, edgecolor="black", linewidth=0.5)

    # Reference line at 1.0
    ax.axhline(y=1.0, color="gray", linestyle="--", linewidth=0.8, label="No speedup")

    ax.set_xticks(x)
    ax.set_xticklabels(systems, rotation=35, ha="right", fontsize=9)
    ax.set_ylabel("Speedup factor (standard / implicit)")
    ax.set_title("Fully Implicit CPA: Speedup Over Standard Nested Solver")
    ax.yaxis.set_minor_locator(AutoMinorLocator())
    ax.grid(axis="y", alpha=0.3)

    # Legend
    from matplotlib.patches import Patch
    legend_elements = [
        Patch(facecolor=paper_color, edgecolor="black", label="Paper systems (Igben et al.)"),
        Patch(facecolor=industrial_color, edgecolor="black", label="Extended industrial systems"),
    ]
    ax.legend(handles=legend_elements, loc="upper left")

    # Annotate bars
    for bar, sp in zip(bars, speedups):
        ax.text(bar.get_x() + bar.get_width() / 2, bar.get_height() + 0.15,
                f"{sp:.1f}x", ha="center", va="bottom", fontsize=9, fontweight="bold")

    plt.tight_layout()
    fig.savefig(str(FIGURES_DIR / "fig1_speedup_bar.png"))
    fig.savefig(str(FIGURES_DIR / "fig1_speedup_bar.pdf"))
    plt.close(fig)
    print("  Figure 1: speedup bar chart saved")


def fig2_speedup_heatmap_pure_water():
    """Figure 2: Heatmap of speedup ratio over T-P grid for pure water."""
    data = raw_data.get("A1_pure_water", [])
    if not data:
        print("  Figure 2: no data for pure water, skipping")
        return

    T_vals = sorted(set(d["T_K"] for d in data))
    P_vals = sorted(set(d["P_bar"] for d in data))
    ratio_grid = np.full((len(P_vals), len(T_vals)), np.nan)

    for d in data:
        ti = T_vals.index(d["T_K"])
        pi = P_vals.index(d["P_bar"])
        ratio_grid[pi, ti] = d["ratio"]

    T_C = [t - 273.15 for t in T_vals]
    fig, ax = plt.subplots(figsize=(8, 6))
    im = ax.pcolormesh(T_C, P_vals, ratio_grid, cmap="RdYlGn_r",
                       vmin=0.0, vmax=1.5, shading="auto")
    ax.set_xlabel("Temperature (°C)")
    ax.set_ylabel("Pressure (bar)")
    ax.set_title("Pure Water: Implicit/Standard Time Ratio\n(< 1.0 = implicit faster)")
    cbar = plt.colorbar(im, ax=ax, label="Time ratio (implicit / standard)")
    ax.contour(T_C, P_vals, ratio_grid, levels=[1.0], colors="black",
               linewidths=1.5, linestyles="--")

    plt.tight_layout()
    fig.savefig(str(FIGURES_DIR / "fig2_heatmap_water.png"))
    fig.savefig(str(FIGURES_DIR / "fig2_heatmap_water.pdf"))
    plt.close(fig)
    print("  Figure 2: pure water T-P heatmap saved")


def fig3_speedup_heatmap_oil_gas_water_meg():
    """Figure 3: Heatmap for oil+gas+water+MEG system."""
    data = raw_data.get("B4_oil_gas_water_MEG", [])
    if not data:
        print("  Figure 3: no data, skipping")
        return

    T_vals = sorted(set(d["T_K"] for d in data))
    P_vals = sorted(set(d["P_bar"] for d in data))
    ratio_grid = np.full((len(P_vals), len(T_vals)), np.nan)

    for d in data:
        ti = T_vals.index(d["T_K"])
        pi = P_vals.index(d["P_bar"])
        ratio_grid[pi, ti] = d["ratio"]

    T_C = [t - 273.15 for t in T_vals]
    fig, ax = plt.subplots(figsize=(8, 6))
    im = ax.pcolormesh(T_C, P_vals, ratio_grid, cmap="RdYlGn_r",
                       vmin=0.0, vmax=1.5, shading="auto")
    ax.set_xlabel("Temperature (°C)")
    ax.set_ylabel("Pressure (bar)")
    ax.set_title("Oil+Gas+Water+MEG (10 comp): Implicit/Standard Time Ratio")
    plt.colorbar(im, ax=ax, label="Time ratio (implicit / standard)")
    ax.contour(T_C, P_vals, ratio_grid, levels=[1.0], colors="black",
               linewidths=1.5, linestyles="--")

    plt.tight_layout()
    fig.savefig(str(FIGURES_DIR / "fig3_heatmap_oil_gas.png"))
    fig.savefig(str(FIGURES_DIR / "fig3_heatmap_oil_gas.pdf"))
    plt.close(fig)
    print("  Figure 3: oil+gas+water+MEG heatmap saved")


def fig4_speedup_vs_association_sites():
    """Figure 4: Speedup vs number of association sites."""
    sites = []
    speedups = []
    labels = []

    for fid, s in summary_data.items():
        if "error" in s or s.get("n_points", 0) == 0:
            continue
        sites.append(s["n_sites"])
        speedups.append(s.get("speedup_median", 1.0))
        labels.append(s["label"])

    fig, ax = plt.subplots(figsize=(8, 5))
    scatter = ax.scatter(sites, speedups, s=120, c=speedups, cmap="viridis",
                        edgecolors="black", linewidth=0.5, zorder=3)
    plt.colorbar(scatter, ax=ax, label="Speedup factor")

    for x, y, lab in zip(sites, speedups, labels):
        short = lab.split("(")[0].strip()
        if len(short) > 20:
            short = short[:18] + "..."
        ax.annotate(short, (x, y), textcoords="offset points",
                   xytext=(5, 8), fontsize=8, alpha=0.8)

    ax.set_xlabel("Number of association sites ($n_s$)")
    ax.set_ylabel("Speedup factor")
    ax.set_title("Speedup vs Association Site Count")
    ax.axhline(y=1.0, color="gray", linestyle="--", linewidth=0.8)
    ax.grid(alpha=0.3)
    ax.yaxis.set_minor_locator(AutoMinorLocator())

    plt.tight_layout()
    fig.savefig(str(FIGURES_DIR / "fig4_speedup_vs_sites.png"))
    fig.savefig(str(FIGURES_DIR / "fig4_speedup_vs_sites.pdf"))
    plt.close(fig)
    print("  Figure 4: speedup vs association sites saved")


def fig5_ratio_distribution():
    """Figure 5: Histogram/violin of ratio distribution across all systems."""
    fig, ax = plt.subplots(figsize=(10, 5))

    all_labels = []
    all_ratios = []
    positions = []
    pos = 0
    colors = []
    paper_color = "#2196F3"
    industrial_color = "#FF9800"

    for fid, s in summary_data.items():
        if "error" in s or s.get("n_points", 0) == 0:
            continue
        data = raw_data.get(fid, [])
        if not data:
            continue
        ratios = [d["ratio"] for d in data]
        all_ratios.append(ratios)
        all_labels.append(s["label"])
        positions.append(pos)
        colors.append(paper_color if s["category"] == "paper" else industrial_color)
        pos += 1

    bp = ax.boxplot(all_ratios, positions=positions, widths=0.6,
                    patch_artist=True, showfliers=True, flierprops={"markersize": 3})

    for patch, color in zip(bp["boxes"], colors):
        patch.set_facecolor(color)
        patch.set_alpha(0.7)

    ax.set_xticks(positions)
    ax.set_xticklabels(all_labels, rotation=35, ha="right", fontsize=9)
    ax.set_ylabel("Time ratio (implicit / standard)")
    ax.set_title("Distribution of Timing Ratios Across T-P Conditions")
    ax.axhline(y=1.0, color="red", linestyle="--", linewidth=1.0, label="Break-even")
    ax.grid(axis="y", alpha=0.3)
    ax.legend(loc="upper right")

    plt.tight_layout()
    fig.savefig(str(FIGURES_DIR / "fig5_ratio_distribution.png"))
    fig.savefig(str(FIGURES_DIR / "fig5_ratio_distribution.pdf"))
    plt.close(fig)
    print("  Figure 5: ratio distribution boxplot saved")


def fig6_speedup_vs_ncomponents():
    """Figure 6: Speedup vs number of components."""
    ncomp = []
    speedups = []
    labels = []
    cats = []

    for fid, s in summary_data.items():
        if "error" in s or s.get("n_points", 0) == 0:
            continue
        ncomp.append(s["n_components"])
        speedups.append(s.get("speedup_median", 1.0))
        labels.append(s["label"])
        cats.append(s["category"])

    fig, ax = plt.subplots(figsize=(8, 5))

    for nc, sp, lab, cat in zip(ncomp, speedups, labels, cats):
        color = "#2196F3" if cat == "paper" else "#FF9800"
        marker = "o" if cat == "paper" else "s"
        ax.scatter(nc, sp, s=100, c=color, marker=marker,
                  edgecolors="black", linewidth=0.5, zorder=3)
        short = lab.split("(")[0].strip()
        if len(short) > 18:
            short = short[:16] + "..."
        ax.annotate(short, (nc, sp), textcoords="offset points",
                   xytext=(5, 8), fontsize=8, alpha=0.8)

    ax.set_xlabel("Number of components")
    ax.set_ylabel("Speedup factor")
    ax.set_title("Speedup vs System Complexity")
    ax.axhline(y=1.0, color="gray", linestyle="--", linewidth=0.8)
    ax.grid(alpha=0.3)

    from matplotlib.patches import Patch
    from matplotlib.lines import Line2D
    legend_elements = [
        Line2D([0], [0], marker='o', color='w', markerfacecolor='#2196F3',
               markersize=10, label='Paper systems'),
        Line2D([0], [0], marker='s', color='w', markerfacecolor='#FF9800',
               markersize=10, label='Industrial systems'),
    ]
    ax.legend(handles=legend_elements, loc="upper right")

    plt.tight_layout()
    fig.savefig(str(FIGURES_DIR / "fig6_speedup_vs_ncomp.png"))
    fig.savefig(str(FIGURES_DIR / "fig6_speedup_vs_ncomp.pdf"))
    plt.close(fig)
    print("  Figure 6: speedup vs n_components saved")


def generate_latex_table():
    """Generate LaTeX table of benchmark results."""
    lines = []
    lines.append(r"\begin{table}[htbp]")
    lines.append(r"\centering")
    lines.append(r"\caption{Benchmark results: fully implicit vs.\ standard nested CPA solver.}")
    lines.append(r"\label{tab:benchmark}")
    lines.append(r"\begin{tabular}{llccccr}")
    lines.append(r"\toprule")
    lines.append(r"System & $N_c$ & $n_s$ & Phase type & Ratio & Speedup & Match \\")
    lines.append(r"\midrule")

    for fid, s in summary_data.items():
        if "error" in s or s.get("n_points", 0) == 0:
            # mark error systems
            label = s.get("label", fid).replace("&", r"\&")
            lines.append(f"{label} & -- & -- & -- & -- & -- & -- \\\\")
            continue
        label = s["label"].replace("&", r"\&")
        nc = s["n_components"]
        ns = s["n_sites"]
        pt = s["phase_type"]
        ratio = s.get("ratio_median", 0)
        speedup = s.get("speedup_median", 0)
        match = s.get("phase_match_pct", 0)
        lines.append(f"{label} & {nc} & {ns} & {pt} & {ratio:.3f} & "
                     f"{speedup:.1f}$\\times$ & {match:.0f}\\% \\\\")

    lines.append(r"\bottomrule")
    lines.append(r"\end{tabular}")
    lines.append(r"\end{table}")

    table_text = "\n".join(lines)
    with open(str(TABLES_DIR / "table1_benchmark.tex"), "w") as f:
        f.write(table_text)
    print("  LaTeX table saved to tables/table1_benchmark.tex")


def main():
    print("Generating figures...")
    fig1_speedup_bar_chart()
    fig2_speedup_heatmap_pure_water()
    fig3_speedup_heatmap_oil_gas_water_meg()
    fig4_speedup_vs_association_sites()
    fig5_ratio_distribution()
    fig6_speedup_vs_ncomponents()
    print("\nGenerating tables...")
    generate_latex_table()
    print(f"\nAll figures saved to {FIGURES_DIR}")
    print(f"All tables saved to {TABLES_DIR}")


if __name__ == "__main__":
    main()
