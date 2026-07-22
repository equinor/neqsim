"""
Generate publication-quality figures for the Implicit CPA paper.

Reads benchmark_raw.json and benchmark_summary.json from results/
and produces figures in figures/ directory.

Targeting Fluid Phase Equilibria — single-column (8.5 cm) or
double-column (17.5 cm) widths at 300 DPI.
"""

import json
import pathlib
import numpy as np
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.ticker import AutoMinorLocator, MultipleLocator
from matplotlib.patches import Patch
from matplotlib.lines import Line2D

# ── Paths ─────────────────────────────────────────────────────────────────
SCRIPT_DIR = pathlib.Path(__file__).resolve().parent
PAPER_DIR = SCRIPT_DIR.parent
RESULTS_DIR = PAPER_DIR / "results"
FIGURES_DIR = PAPER_DIR / "figures"
FIGURES_DIR.mkdir(exist_ok=True)
TABLES_DIR = PAPER_DIR / "tables"
TABLES_DIR.mkdir(exist_ok=True)

# ── Journal Style ─────────────────────────────────────────────────────────
# FPE uses serif fonts; target single-column ~3.35 in, double ~6.9 in
plt.rcParams.update({
    "font.family": "serif",
    "font.serif": ["Times New Roman", "DejaVu Serif", "serif"],
    "mathtext.fontset": "dejavuserif",
    "font.size": 9,
    "axes.labelsize": 10,
    "axes.titlesize": 10,
    "axes.titleweight": "bold",
    "legend.fontsize": 8,
    "legend.framealpha": 0.9,
    "legend.edgecolor": "0.7",
    "xtick.labelsize": 8,
    "ytick.labelsize": 8,
    "xtick.direction": "in",
    "ytick.direction": "in",
    "xtick.minor.visible": True,
    "ytick.minor.visible": True,
    "axes.linewidth": 0.6,
    "grid.linewidth": 0.4,
    "grid.alpha": 0.35,
    "figure.dpi": 150,
    "savefig.dpi": 300,
    "savefig.bbox": "tight",
    "savefig.pad_inches": 0.05,
})

# ── Colour Palette ────────────────────────────────────────────────────────
BLUE = "#1f77b4"      # paper systems (Igben et al.)
ORANGE = "#ff7f0e"    # extended industrial systems
GREY_LINE = "#888888"

# ── Short labels for compact axis tick marks ──────────────────────────────
SHORT_LABELS = {
    "A1_pure_water":               "A1\nWater",
    "A2_water_methanol_50":        "A2\nW\u2013MeOH\n50/50",
    "A2_water_methanol_80":        "A2b\nW\u2013MeOH\n80/20",
    "A3_water_ethanol_aceticacid": "A3\nW\u2013EtOH\n\u2013AcOH",
    "B1_natgas_water":             "B1\nNG\u2013W",
    "B2_natgas_water_MEG":         "B2\nNG\u2013W\n\u2013MEG",
    "B3_gas_condensate_water":     "B3\nGC\u2013W",
    "B4_oil_gas_water_MEG":        "B4\nOGWM",
    "B5_CO2_water":                "B5\nCO\u2082\u2013W",
    "B6_MEG_water":                "B6\nMEG\u2013W",
    "B7_natgas_water_TEG":         "B7\nNG\u2013W\n\u2013TEG",
}

# ── Load data ─────────────────────────────────────────────────────────────
with open(str(RESULTS_DIR / "benchmark_raw.json")) as f:
    raw_data = json.load(f)
with open(str(RESULTS_DIR / "benchmark_summary.json")) as f:
    summary_data = json.load(f)


def _system_order():
    """Return system IDs in the canonical display order."""
    order = []
    for fid, s in summary_data.items():
        if "error" in s or s.get("n_points", 0) == 0:
            continue
        order.append(fid)
    return order


# ======================================================================
# Figure 1: Bar chart of median speedup
# ======================================================================
def fig1_speedup_bar_chart():
    order = _system_order()
    labels = [SHORT_LABELS.get(fid, fid) for fid in order]
    speedups = [summary_data[fid].get("speedup_median", 1.0) for fid in order]
    cats = [summary_data[fid]["category"] for fid in order]
    colors = [BLUE if c == "paper" else ORANGE for c in cats]

    fig, ax = plt.subplots(figsize=(7.0, 3.5))
    x = np.arange(len(labels))
    bars = ax.bar(x, speedups, width=0.72, color=colors,
                  edgecolor="black", linewidth=0.4)

    # Reference line
    ax.axhline(y=1.0, color=GREY_LINE, linestyle="--", linewidth=0.7, zorder=1)

    # Annotate bar values
    for bar, sp in zip(bars, speedups):
        y_pos = bar.get_height() + 0.3
        ax.text(bar.get_x() + bar.get_width() / 2, y_pos,
                "{:.1f}\u00d7".format(sp), ha="center", va="bottom",
                fontsize=7.5, fontweight="bold")

    ax.set_xticks(x)
    ax.set_xticklabels(labels, fontsize=7, linespacing=0.9)
    ax.set_ylabel("Speedup factor")
    ax.set_title("Median speedup: fully implicit vs. standard nested CPA")
    ax.yaxis.set_minor_locator(AutoMinorLocator())
    ax.grid(axis="y")
    ax.set_xlim(-0.6, len(labels) - 0.4)

    # Legend
    legend_elements = [
        Patch(facecolor=BLUE, edgecolor="black", linewidth=0.4,
              label="Reference systems [10]"),
        Patch(facecolor=ORANGE, edgecolor="black", linewidth=0.4,
              label="Extended industrial systems"),
    ]
    ax.legend(handles=legend_elements, loc="upper left", frameon=True)

    fig.savefig(str(FIGURES_DIR / "fig1_speedup_bar.png"))
    fig.savefig(str(FIGURES_DIR / "fig1_speedup_bar.pdf"))
    plt.close(fig)
    print("  Fig 1: speedup bar chart")


# ======================================================================
# Figure 2: T-P heatmap for pure water
# ======================================================================
def fig2_speedup_heatmap_pure_water():
    data = raw_data.get("A1_pure_water", [])
    if not data:
        print("  Fig 2: no data, skipping")
        return

    T_vals = sorted(set(d["T_K"] for d in data))
    P_vals = sorted(set(d["P_bar"] for d in data))
    ratio_grid = np.full((len(P_vals), len(T_vals)), np.nan)
    for d in data:
        ti = T_vals.index(d["T_K"])
        pi = P_vals.index(d["P_bar"])
        ratio_grid[pi, ti] = d["ratio"]

    T_C = [t - 273.15 for t in T_vals]

    fig, ax = plt.subplots(figsize=(4.5, 3.8))
    im = ax.pcolormesh(T_C, P_vals, ratio_grid, cmap="RdYlGn_r",
                       vmin=0.0, vmax=1.5, shading="nearest")
    ax.set_xlabel("Temperature (\u00b0C)")
    ax.set_ylabel("Pressure (bar)")
    ax.set_title("Pure water (A1): implicit/standard time ratio")

    cbar = fig.colorbar(im, ax=ax, pad=0.02, aspect=25)
    cbar.set_label("Time ratio", fontsize=9)
    cbar.ax.tick_params(labelsize=8)

    # Clean contour at ratio = 1.0 (break-even)
    try:
        cs = ax.contour(T_C, P_vals, ratio_grid, levels=[1.0],
                        colors="black", linewidths=1.0, linestyles="-")
        ax.clabel(cs, fmt="1.0", fontsize=7)
    except ValueError:
        pass  # all values < 1 or > 1

    fig.savefig(str(FIGURES_DIR / "fig2_heatmap_water.png"))
    fig.savefig(str(FIGURES_DIR / "fig2_heatmap_water.pdf"))
    plt.close(fig)
    print("  Fig 2: pure water T-P heatmap")


# ======================================================================
# Figure 3: T-P heatmap for oil+gas+water+MEG (10 components)
# ======================================================================
def fig3_speedup_heatmap_oil_gas_water_meg():
    data = raw_data.get("B4_oil_gas_water_MEG", [])
    if not data:
        print("  Fig 3: no data, skipping")
        return

    T_vals = sorted(set(d["T_K"] for d in data))
    P_vals = sorted(set(d["P_bar"] for d in data))
    ratio_grid = np.full((len(P_vals), len(T_vals)), np.nan)
    for d in data:
        ti = T_vals.index(d["T_K"])
        pi = P_vals.index(d["P_bar"])
        ratio_grid[pi, ti] = d["ratio"]

    T_C = [t - 273.15 for t in T_vals]

    fig, ax = plt.subplots(figsize=(4.5, 3.8))
    im = ax.pcolormesh(T_C, P_vals, ratio_grid, cmap="RdYlGn_r",
                       vmin=0.0, vmax=1.5, shading="nearest")
    ax.set_xlabel("Temperature (\u00b0C)")
    ax.set_ylabel("Pressure (bar)")
    ax.set_title("Oil+gas+water+MEG, 10 comp. (B4): time ratio")

    cbar = fig.colorbar(im, ax=ax, pad=0.02, aspect=25)
    cbar.set_label("Time ratio", fontsize=9)
    cbar.ax.tick_params(labelsize=8)

    try:
        cs = ax.contour(T_C, P_vals, ratio_grid, levels=[1.0],
                        colors="black", linewidths=1.0, linestyles="-")
        ax.clabel(cs, fmt="1.0", fontsize=7)
    except ValueError:
        pass

    fig.savefig(str(FIGURES_DIR / "fig3_heatmap_oil_gas.png"))
    fig.savefig(str(FIGURES_DIR / "fig3_heatmap_oil_gas.pdf"))
    plt.close(fig)
    print("  Fig 3: oil+gas+water+MEG T-P heatmap")


# ======================================================================
# Figure 4: Speedup vs association site count
# ======================================================================
def fig4_speedup_vs_association_sites():
    order = _system_order()
    sites = []
    speedups = []
    ids = []
    cats = []

    for fid in order:
        s = summary_data[fid]
        sites.append(s["n_sites"])
        speedups.append(s.get("speedup_median", 1.0))
        ids.append(fid.split("_")[0].upper())   # A1, A2, A3, B1, ...
        cats.append(s["category"])

    # Manual annotation offsets (dx, dy in points) to avoid label overlap
    # Clusters: n_s=4 has A1(2.0), B1(1.9), B3(2.4), B5(5.7)
    #           n_s=6 has A2(7.7), A2(6.9)
    #           n_s=8 has A3(32.8), B2(2.8), B4(3.3), B6(8.4), B7(3.6)
    OFFSETS = {
        "A1": (5, 6),    "B1": (-18, 6),   "B3": (-18, -12), "B5": (5, 5),
        "A2": (5, 6),                                         # both A2
        "A3": (-18, -5), "B2": (5, -12),    "B4": (5, 5),
        "B6": (5, 5),    "B7": (-18, 5),
    }
    offset_used = {}  # track which A2 gets which offset

    fig, ax = plt.subplots(figsize=(4.5, 3.8))

    # Plot with jitter to separate overlapping points
    rng = np.random.RandomState(42)
    jitter = rng.uniform(-0.15, 0.15, len(sites))
    x_jittered = np.array(sites, dtype=float) + jitter

    for xi, yi, label, cat in zip(x_jittered, speedups, ids, cats):
        color = BLUE if cat == "paper" else ORANGE
        marker = "o" if cat == "paper" else "s"
        ax.scatter(xi, yi, s=55, c=color, marker=marker,
                   edgecolors="black", linewidth=0.4, zorder=3)
        # Handle duplicate A2 labels
        key = label
        if label in offset_used:
            key = label + "b"
            off = (5, -8)  # second A2 gets different offset
        else:
            off = OFFSETS.get(label, (4, 5))
        offset_used[label] = True
        ax.annotate(key.replace("b", ""), (xi, yi), textcoords="offset points",
                    xytext=off, fontsize=7, color="0.25")

    ax.axhline(y=1.0, color=GREY_LINE, linestyle="--", linewidth=0.7, zorder=1)
    ax.set_xlabel("Number of association sites ($n_s$)")
    ax.set_ylabel("Speedup factor")
    ax.set_title("Speedup vs. association site count")
    ax.xaxis.set_major_locator(MultipleLocator(2))
    ax.yaxis.set_minor_locator(AutoMinorLocator())
    ax.grid(True)

    legend_elements = [
        Line2D([0], [0], marker="o", color="w", markerfacecolor=BLUE,
               markeredgecolor="black", markeredgewidth=0.4, markersize=7,
               label="Reference [10]"),
        Line2D([0], [0], marker="s", color="w", markerfacecolor=ORANGE,
               markeredgecolor="black", markeredgewidth=0.4, markersize=7,
               label="Industrial"),
    ]
    ax.legend(handles=legend_elements, loc="upper left")

    fig.savefig(str(FIGURES_DIR / "fig4_speedup_vs_sites.png"))
    fig.savefig(str(FIGURES_DIR / "fig4_speedup_vs_sites.pdf"))
    plt.close(fig)
    print("  Fig 4: speedup vs association sites")


# ======================================================================
# Figure 5: Box plot of timing ratio distributions
# ======================================================================
def fig5_ratio_distribution():
    order = _system_order()
    labels = []
    all_ratios = []
    colors = []

    for fid in order:
        s = summary_data[fid]
        data = raw_data.get(fid, [])
        if not data:
            continue
        ratios = [d["ratio"] for d in data]
        all_ratios.append(ratios)
        labels.append(SHORT_LABELS.get(fid, fid))
        colors.append(BLUE if s["category"] == "paper" else ORANGE)

    fig, ax = plt.subplots(figsize=(7.0, 3.5))
    positions = np.arange(len(all_ratios))

    bp = ax.boxplot(all_ratios, positions=positions, widths=0.55,
                    patch_artist=True, showfliers=True,
                    flierprops=dict(marker="o", markersize=2.5,
                                    markerfacecolor="none",
                                    markeredgecolor="0.5",
                                    markeredgewidth=0.4),
                    medianprops=dict(color="black", linewidth=1.0),
                    whiskerprops=dict(linewidth=0.6),
                    capprops=dict(linewidth=0.6),
                    boxprops=dict(linewidth=0.5))

    for patch, color in zip(bp["boxes"], colors):
        patch.set_facecolor(color)
        patch.set_alpha(0.75)

    ax.axhline(y=1.0, color="red", linestyle="--", linewidth=0.8,
               zorder=1, label="Break-even (ratio = 1)")

    ax.set_xticks(positions)
    ax.set_xticklabels(labels, fontsize=7, linespacing=0.9)
    ax.set_ylabel("Time ratio (implicit / standard)")
    ax.set_title("Distribution of timing ratios across $(T, P)$ conditions")

    # Use log scale so the wide-range systems are visible
    ax.set_yscale("log")
    ax.set_ylim(0.005, 40)
    # Custom tick labels for log axis
    ax.set_yticks([0.01, 0.1, 1.0, 10])
    ax.set_yticklabels(["0.01", "0.1", "1.0", "10"])
    ax.yaxis.set_minor_locator(matplotlib.ticker.LogLocator(
        base=10.0, subs=np.arange(2, 10) * 0.1, numticks=12))
    ax.grid(axis="y")

    legend_elements = [
        Patch(facecolor=BLUE, edgecolor="black", linewidth=0.4,
              alpha=0.75, label="Reference [10]"),
        Patch(facecolor=ORANGE, edgecolor="black", linewidth=0.4,
              alpha=0.75, label="Industrial"),
        Line2D([0], [0], color="red", linestyle="--", linewidth=0.8,
               label="Break-even"),
    ]
    ax.legend(handles=legend_elements, loc="upper right", fontsize=7)
    ax.set_xlim(-0.6, len(all_ratios) - 0.4)

    fig.savefig(str(FIGURES_DIR / "fig5_ratio_distribution.png"))
    fig.savefig(str(FIGURES_DIR / "fig5_ratio_distribution.pdf"))
    plt.close(fig)
    print("  Fig 5: ratio distribution boxplot")


# ======================================================================
# Figure 6: Speedup vs number of components
# ======================================================================
def fig6_speedup_vs_ncomponents():
    order = _system_order()
    ncomp = []
    speedups = []
    ids = []
    cats = []

    for fid in order:
        s = summary_data[fid]
        ncomp.append(s["n_components"])
        speedups.append(s.get("speedup_median", 1.0))
        ids.append(fid.split("_")[0].upper())
        cats.append(s["category"])

    # Manual annotation offsets for N_c clusters
    # N_c=2 cluster: A2(7.7), A2(6.9), B5(5.7), B6(8.4)
    # N_c=6 cluster: B2(2.8), B7(3.6)
    OFFSETS_6 = {
        "A1": (5, 6),
        "A2": (5, 5),    # first A2
        "A3": (5, 5),
        "B1": (5, 6),
        "B2": (-18, 5),  "B7": (5, -10),
        "B3": (5, -10),
        "B4": (5, 5),
        "B5": (-18, -10), "B6": (-18, 5),
    }
    offset_used_6 = {}

    fig, ax = plt.subplots(figsize=(4.5, 3.8))

    # Jitter x to separate overlapping points
    rng = np.random.RandomState(7)
    jitter = rng.uniform(-0.2, 0.2, len(ncomp))
    x_jittered = np.array(ncomp, dtype=float) + jitter

    for xi, yi, label, cat in zip(x_jittered, speedups, ids, cats):
        color = BLUE if cat == "paper" else ORANGE
        marker = "o" if cat == "paper" else "s"
        ax.scatter(xi, yi, s=55, c=color, marker=marker,
                   edgecolors="black", linewidth=0.4, zorder=3)
        key = label
        if label in offset_used_6:
            off = (5, -10)  # second A2
        else:
            off = OFFSETS_6.get(label, (4, 5))
        offset_used_6[label] = True
        ax.annotate(label, (xi, yi), textcoords="offset points",
                    xytext=off, fontsize=7, color="0.25")

    ax.axhline(y=1.0, color=GREY_LINE, linestyle="--", linewidth=0.7, zorder=1)
    ax.set_xlabel("Number of components ($N_c$)")
    ax.set_ylabel("Speedup factor")
    ax.set_title("Speedup vs. system complexity")
    ax.xaxis.set_major_locator(MultipleLocator(2))
    ax.yaxis.set_minor_locator(AutoMinorLocator())
    ax.grid(True)

    legend_elements = [
        Line2D([0], [0], marker="o", color="w", markerfacecolor=BLUE,
               markeredgecolor="black", markeredgewidth=0.4, markersize=7,
               label="Reference [10]"),
        Line2D([0], [0], marker="s", color="w", markerfacecolor=ORANGE,
               markeredgecolor="black", markeredgewidth=0.4, markersize=7,
               label="Industrial"),
    ]
    ax.legend(handles=legend_elements, loc="upper right")

    fig.savefig(str(FIGURES_DIR / "fig6_speedup_vs_ncomp.png"))
    fig.savefig(str(FIGURES_DIR / "fig6_speedup_vs_ncomp.pdf"))
    plt.close(fig)
    print("  Fig 6: speedup vs n_components")


# ======================================================================
# LaTeX Table
# ======================================================================
def generate_latex_table():
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
    print("  LaTeX table saved")


# ======================================================================
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
    print(f"\nAll outputs saved to {FIGURES_DIR}")


if __name__ == "__main__":
    main()
