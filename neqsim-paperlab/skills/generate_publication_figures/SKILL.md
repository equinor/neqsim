# Skill: Generate Publication-Quality Figures

## Purpose

Create matplotlib figures that meet journal submission standards: correct fonts,
compact sizes, consistent styling, readable labels, and high DPI. Based on
lessons learned from the CPA and TPflash papers (Fluid Phase Equilibria 2026).

## When to Use

- Creating figures for any scientific paper in the paperlab
- Regenerating figures after data or style revisions
- Setting up a new `02_generate_figures.py` for a paper project

## Core Setup (Copy-Paste Starter)

Every figure script should start with this rc configuration:

```python
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import matplotlib.ticker as ticker
import numpy as np
import json
from pathlib import Path

# ── Publication-quality defaults ──────────────────────────────────
plt.rcParams.update({
    "font.family": "serif",
    "font.serif": ["Times New Roman", "DejaVu Serif"],
    "font.size": 9,
    "axes.titlesize": 10,
    "axes.labelsize": 9,
    "xtick.labelsize": 8,
    "ytick.labelsize": 8,
    "legend.fontsize": 8,
    "figure.dpi": 300,
    "savefig.dpi": 300,
    "savefig.bbox_inches": "tight",
    "axes.linewidth": 0.6,
    "xtick.direction": "in",
    "ytick.direction": "in",
    "xtick.major.size": 3,
    "ytick.major.size": 3,
    "xtick.minor.size": 1.5,
    "ytick.minor.size": 1.5,
    "grid.linewidth": 0.3,
    "grid.alpha": 0.4,
    "lines.linewidth": 1.0,
    "lines.markersize": 4,
})

# Consistent color palette
BLUE = "#2171b5"
ORANGE = "#e6550d"
GREEN = "#31a354"
GREY = "#636363"
PALETTE = [BLUE, ORANGE, GREEN, "#756bb1", "#e7298a", "#66a61e"]

# Output directory
FIGURES_DIR = Path(__file__).parent.parent / "figures"
FIGURES_DIR.mkdir(exist_ok=True)
```

## Journal Column Widths

Size figures for the target journal's column width:

| Journal | Single column | Double column | Aspect ratio |
|---------|--------------|---------------|-------------|
| Elsevier (FPE, CACE, CES) | 3.5 in (88 mm) | 7.0 in (178 mm) | 0.75–1.0 |
| ACS (IECR) | 3.25 in | 7.0 in | 0.75–1.0 |
| Wiley (AIChE J.) | 3.4 in | 7.0 in | 0.75–1.0 |

```python
# Figure size templates
FIG_SINGLE = (3.5, 2.8)    # Single-column: width, height
FIG_SINGLE_TALL = (3.5, 3.5)
FIG_DOUBLE = (7.0, 3.5)    # Double-column
FIG_DOUBLE_TALL = (7.0, 5.0)
```

## Common Figure Patterns

### Pattern 1: Bar Chart (Comparison Across Categories)

```python
fig, ax = plt.subplots(figsize=FIG_DOUBLE)
x = np.arange(len(categories))
w = 0.35
bars1 = ax.bar(x - w/2, values_a, w, color=BLUE, edgecolor="white", lw=0.3, label="Method A")
bars2 = ax.bar(x + w/2, values_b, w, color=ORANGE, edgecolor="white", lw=0.3, label="Method B")

ax.set_xticks(x)
ax.set_xticklabels(short_labels, rotation=0)  # NEVER rotate > 30°
ax.set_ylabel("Metric (unit)")
ax.legend(frameon=False, ncol=2)
ax.grid(axis="y", ls="--")
ax.set_axisbelow(True)
fig.savefig(FIGURES_DIR / "fig1_comparison.png")
plt.close()
```

**Key rule:** If category names are long, use short IDs (A1, A2, B1...) with a
legend mapping below the figure or in the caption.

### Pattern 2: Heatmap / Contour (2D Parameter Space)

```python
fig, (ax1, ax2) = plt.subplots(1, 2, figsize=FIG_DOUBLE)

# Use pcolormesh for discrete data, contourf for smooth
im1 = ax1.pcolormesh(T_grid, P_grid, metric_grid_a, cmap="RdYlGn", shading="auto")
im2 = ax2.pcolormesh(T_grid, P_grid, metric_grid_b, cmap="RdYlGn", shading="auto")

for ax, title in zip([ax1, ax2], ["Method A", "Method B"]):
    ax.set_xlabel("$T$ (K)")
    ax.set_ylabel("$P$ (bar)")
    ax.set_title(title, fontsize=9)

# Shared colorbar
fig.colorbar(im2, ax=[ax1, ax2], label="Iteration count", shrink=0.8, pad=0.02)
fig.savefig(FIGURES_DIR / "fig2_heatmap.png")
plt.close()
```

**Key rule:** NEVER use contour lines on noisy gridded data — they create
ugly loops. Use `pcolormesh` instead. If overlaying contours, use very few
levels (3–5) and ensure the data is smooth.

### Pattern 3: Scatter with Per-Point Labels (Scaling)

When points cluster at the same x-value, labels will overlap. Use these
techniques:

```python
fig, ax = plt.subplots(figsize=FIG_SINGLE_TALL)

# 1. Define manual offsets per data point to prevent overlap
#    Format: {system_id: (dx, dy)} in data coordinates or points
offsets = {
    "A1": (5, -8), "A2": (5, 5), "B1": (-40, 5),
    "B2": (5, 3), "C1": (5, -8), "C2": (5, 5),
}

# 2. Jitter x-coordinates to separate clustered points
np.random.seed(42)
x_jitter = x_values + np.random.uniform(-0.15, 0.15, len(x_values))

ax.scatter(x_jitter, y_values, s=30, color=BLUE, zorder=5)

for i, (sid, xj, yv) in enumerate(zip(system_ids, x_jitter, y_values)):
    dx, dy = offsets.get(sid, (5, 3))
    ax.annotate(sid, (xj, yv), textcoords="offset points",
                xytext=(dx, dy), fontsize=7, color=GREY,
                arrowprops=dict(arrowstyle="-", color=GREY, lw=0.3) if abs(dx) > 10 else None)

ax.set_xlabel("Component count $N_c$")
ax.set_ylabel("Speedup factor")
ax.grid(True, ls="--")
fig.savefig(FIGURES_DIR / "fig4_scaling.png")
plt.close()
```

**Key rules:**
- ALWAYS check for label overlap visually — automated layouts (adjust_text) often fail
- For ≤15 points, define manual `offsets` dict during review
- Use short system IDs (A1, B3) not full names — put the legend in the caption

### Pattern 4: Box Plot with Extreme Outliers

When data has a wide range (e.g., 1× to 30×), standard box plots compress
the majority of the data. Solution: **log scale**.

```python
fig, ax = plt.subplots(figsize=FIG_DOUBLE)

bp = ax.boxplot(data_by_group, labels=short_labels,
                patch_artist=True, showfliers=True, widths=0.5,
                medianprops=dict(color=ORANGE, lw=1.2),
                flierprops=dict(marker="o", ms=3, mfc="none", mec=GREY))

for patch in bp["boxes"]:
    patch.set_facecolor(BLUE)
    patch.set_alpha(0.35)

ax.set_yscale("log")  # Critical for wide-range data
ax.yaxis.set_major_formatter(ticker.FuncFormatter(lambda v, _: f"{v:.1f}" if v < 10 else f"{v:.0f}"))
ax.set_ylabel("Speedup factor")
ax.axhline(y=1.0, color="grey", ls="--", lw=0.6, label="Parity")
ax.grid(True, axis="y", ls="--")
ax.legend(frameon=False)
fig.savefig(FIGURES_DIR / "fig5_boxplot.png")
plt.close()
```

**Key rule:** Use log scale whenever `max/min > 10`. The linear scale will
squash all boxes into a thin band at the bottom.

### Pattern 5: Line Plot with Confidence Bands

```python
fig, ax = plt.subplots(figsize=FIG_SINGLE)

ax.plot(x, y_mean, color=BLUE, lw=1.2, label="Mean")
ax.fill_between(x, y_low, y_high, color=BLUE, alpha=0.15, label="95% CI")
ax.plot(x_ref, y_ref, "o", color=ORANGE, ms=4, label="Reference data")

ax.set_xlabel("Temperature (K)")
ax.set_ylabel("Density (kg/m$^3$)")
ax.legend(frameon=False)
ax.grid(True, ls="--")
fig.savefig(FIGURES_DIR / "fig3_profile.png")
plt.close()
```

### Pattern 6: Parity Plot (Calculated vs Reference)

```python
fig, ax = plt.subplots(figsize=FIG_SINGLE)

ax.scatter(ref_values, calc_values, s=15, c=BLUE, alpha=0.6, edgecolors="none")

# Parity line
lims = [min(min(ref_values), min(calc_values)), max(max(ref_values), max(calc_values))]
ax.plot(lims, lims, "k--", lw=0.6, label="Parity")
# ±10% bands
ax.plot(lims, [l * 1.1 for l in lims], color=GREY, ls=":", lw=0.4)
ax.plot(lims, [l * 0.9 for l in lims], color=GREY, ls=":", lw=0.4)

ax.set_xlabel("Reference value")
ax.set_ylabel("Calculated value")
ax.set_aspect("equal")
ax.legend(frameon=False)
ax.grid(True, ls="--")
fig.savefig(FIGURES_DIR / "fig_parity.png")
plt.close()
```

## Short System ID Convention

For papers with multiple test systems, use short IDs in figures and map them
in a table in the paper:

| ID | System | N_c |
|----|--------|-----|
| A1 | Methane/Ethane | 2 |
| A2 | Methane/Propane/CO₂ | 3 |
| B1 | Lean gas (5-comp) | 5 |
| B2 | Rich gas (8-comp) | 8 |
| C1 | Gas condensate (10-comp) | 10 |
| C2 | Oil (15-comp) | 15 |

Place the full table in the paper (usually Section 4: Benchmark Design) and
reference it from figure captions: "System IDs are defined in Table 1."

## Dual Output (PNG + PDF)

Always save both raster (PNG) and vector (PDF) versions. Journals prefer
vector for line art but PNG is needed for Word embedding:

```python
fig.savefig(FIGURES_DIR / "fig1_comparison.png")
fig.savefig(FIGURES_DIR / "fig1_comparison.pdf")
```

## Figure Quality Checklist

Before finalizing any figure:

- [ ] **Readable at print size**: Text ≥ 7pt when printed at journal column width
- [ ] **No label overlap**: Check scatter plots, bar charts, annotations
- [ ] **Log scale if range > 10×**: Box plots, timing, any wide-range data
- [ ] **Consistent colors**: Same color = same method/category across all figures
- [ ] **Axes labeled with units**: Every axis has label + unit in parentheses
- [ ] **Grid lines**: Dashed, alpha < 0.5, behind data
- [ ] **No excessive whitespace**: Use `bbox_inches="tight"` and compact figsize
- [ ] **Legend readable**: `frameon=False`, placed to minimize overlap with data
- [ ] **300 DPI minimum**: Set in both rcParams and savefig
- [ ] **Serif font**: Times New Roman for Elsevier and most journals
- [ ] **Inward tick marks**: Cleaner look, standard for Elsevier
- [ ] **Short IDs for many points**: Use A1/B1/C1 with caption legend

## Common Mistakes Caught from CPA Paper

1. **Rotated bar labels**: Never rotate > 30°. Use short IDs instead.
2. **Contour lines on noisy data**: Creates ugly loops. Use pcolormesh.
3. **Linear scale box plots with outliers**: One 30× outlier squashes all
   other boxes to a 1-pixel line. Always use log scale for wide ranges.
4. **Full system names as point labels**: "Methane/Ethane/Propane/n-Butane"
   overlaps with neighbors. Use "B1" and define in table.
5. **Large figure sizes**: 10×8 inch figures waste journal space. Use
   3.5×2.8 (single) or 7.0×3.5 (double column).
6. **Inconsistent annotation offsets**: When 4 points cluster at the same
   x-value, automated label placement fails. Use manual offsets dict.
7. **Missing parity/reference lines**: Always add y=1 line on speedup plots,
   parity line on comparison plots.
