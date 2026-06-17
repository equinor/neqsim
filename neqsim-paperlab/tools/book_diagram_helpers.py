"""Reusable drawing helpers for book figures — conceptual diagrams.

Provides:
- box(ax, x, y, w, h, label, ...) — rounded rectangle with text
- arrow(ax, p1, p2, ...) — annotated arrow
- styled axis setup
- consistent palette
"""

from __future__ import annotations

import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch, FancyArrowPatch


# ── Palette (academic, muted) ────────────────────────────────────────────────
BLUE = "#2171b5"
ORANGE = "#e6550d"
GREEN = "#31a354"
PURPLE = "#756bb1"
PINK = "#e7298a"
GRAY = "#666666"

BLUE_FILL = "#dbe9f4"
GREEN_FILL = "#d9efd0"
ORANGE_FILL = "#fde2c4"
PURPLE_FILL = "#e3dfee"
GRAY_FILL = "#efefef"
CREAM = "#fbfaf6"


# ── Style ────────────────────────────────────────────────────────────────────
def setup_style():
    """Apply book-typography matplotlib defaults."""
    plt.rcParams.update({
        "font.family": "serif",
        "font.size": 9,
        "axes.labelsize": 10,
        "axes.titlesize": 10,
        "legend.fontsize": 8,
        "xtick.labelsize": 8,
        "ytick.labelsize": 8,
        "xtick.direction": "in",
        "ytick.direction": "in",
        "axes.linewidth": 0.7,
        "lines.linewidth": 1.4,
        "grid.linewidth": 0.3,
        "grid.alpha": 0.4,
        "savefig.dpi": 200,
        "figure.dpi": 150,
    })


def diagram_axes(ax, xlim=(0, 10), ylim=(0, 6)):
    """Configure an axis for a conceptual diagram (no ticks, equal aspect)."""
    ax.set_xlim(*xlim)
    ax.set_ylim(*ylim)
    ax.set_aspect("equal")
    ax.set_xticks([])
    ax.set_yticks([])
    for s in ax.spines.values():
        s.set_visible(False)


# ── Primitives ───────────────────────────────────────────────────────────────
def box(ax, x, y, w, h, label, *, fill=BLUE_FILL, edge=BLUE,
        fontsize=9, weight="normal", radius=0.08, lw=1.0,
        text_color="#1a1a1a"):
    """Draw a rounded rectangle with centered text."""
    patch = FancyBboxPatch(
        (x, y), w, h,
        boxstyle=f"round,pad=0.02,rounding_size={radius}",
        linewidth=lw, edgecolor=edge, facecolor=fill,
    )
    ax.add_patch(patch)
    ax.text(x + w / 2, y + h / 2, label,
            ha="center", va="center", fontsize=fontsize,
            weight=weight, color=text_color, wrap=True)


def label_text(ax, x, y, text, *, fontsize=8, color=GRAY, weight="normal",
               ha="center", va="center", style="normal"):
    """Add free-floating text label."""
    ax.text(x, y, text, ha=ha, va=va, fontsize=fontsize,
            color=color, weight=weight, style=style)


def arrow(ax, p1, p2, *, color=GRAY, lw=1.0, style="-|>", mutation=12,
          label=None, label_offset=(0, 0.18), label_color=None,
          label_fontsize=7.5, connectionstyle="arc3,rad=0"):
    """Draw an arrow from p1 to p2; optional label near midpoint."""
    arr = FancyArrowPatch(
        p1, p2, arrowstyle=style,
        mutation_scale=mutation, color=color, linewidth=lw,
        connectionstyle=connectionstyle,
    )
    ax.add_patch(arr)
    if label:
        mx = (p1[0] + p2[0]) / 2 + label_offset[0]
        my = (p1[1] + p2[1]) / 2 + label_offset[1]
        ax.text(mx, my, label, ha="center", va="center",
                fontsize=label_fontsize,
                color=label_color or color, style="italic")


def two_way_arrow(ax, p1, p2, **kwargs):
    """Draw a two-headed arrow."""
    kwargs.setdefault("style", "<|-|>")
    arrow(ax, p1, p2, **kwargs)


def section_title(ax, text, y=0.97, fontsize=11):
    """Add a small caption-style title at the top of the diagram."""
    ax.text(0.5, y, text, transform=ax.transAxes,
            ha="center", va="top", fontsize=fontsize,
            weight="bold", color="#1a1a1a")
