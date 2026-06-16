#!/usr/bin/env python3
"""Generate clean conceptual chapter-opener illustrations for the NeqSim book.

Why this tool
-------------
The HTML renderer (``tools/book_render_html.py``) picks a *hero* illustration for
each chapter from that chapter's ``figures/`` directory. When no purpose-made
illustration exists it falls back to the alphabetically-first image — which is
usually a *generated data plot* (e.g. a compression-power bar chart). A data
plot belongs in the body of the chapter next to the calculation that produced
it, not as the topic opener.

This tool draws a tasteful, topic-specific *schematic* for every chapter using
matplotlib only (offline, reproducible, no external image services) in the
book's navy/teal palette, and writes it as ``<chNN>_overview.png``. The name
matches the renderer's ``_overview`` hero hint, so it is preferred over plots
while the original plots remain in the chapter body.

Usage
-----
    python tools/book_chapter_illustrations.py BOOK_DIR            # all chapters
    python tools/book_chapter_illustrations.py BOOK_DIR --only ch18,ch19
"""

import argparse
import re
import sys
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import (
    Arc,
    Circle,
    FancyArrowPatch,
    FancyBboxPatch,
    Polygon,
    Rectangle,
    Wedge,
)
from matplotlib.path import Path as MplPath

# ── Book palette ──────────────────────────────────────────────────────────
NAVY = "#0d3b66"
TEAL = "#1a5276"
STEEL = "#4878a8"
SKY = "#5ba3b8"
ORANGE = "#d4763a"
SAND = "#e8c9a0"
INK = "#22303c"
PAPER = "#f7fafc"
LINE = "#c7d4de"
WHITE = "#ffffff"

FIG_W, FIG_H = 8.0, 4.5  # inches → 1600x900 at dpi 200
DPI = 200


# ── Primitive drawing helpers ─────────────────────────────────────────────
def _ax():
    fig, ax = plt.subplots(figsize=(FIG_W, FIG_H), dpi=DPI)
    ax.set_xlim(0, 16)
    ax.set_ylim(0, 9)
    ax.axis("off")
    fig.patch.set_facecolor(WHITE)
    ax.add_patch(
        FancyBboxPatch(
            (0.25, 0.25),
            15.5,
            8.5,
            boxstyle="round,pad=0,rounding_size=0.35",
            linewidth=1.2,
            edgecolor=LINE,
            facecolor=PAPER,
            zorder=0,
        )
    )
    return fig, ax


def box(ax, x, y, w, h, label="", fc=WHITE, ec=TEAL, tc=INK, fs=11, lw=1.6, z=3):
    ax.add_patch(
        FancyBboxPatch(
            (x, y),
            w,
            h,
            boxstyle="round,pad=0,rounding_size=0.22",
            linewidth=lw,
            edgecolor=ec,
            facecolor=fc,
            zorder=z,
        )
    )
    if label:
        ax.text(
            x + w / 2,
            y + h / 2,
            label,
            ha="center",
            va="center",
            fontsize=fs,
            color=tc,
            zorder=z + 1,
            weight="bold",
        )


def arrow(ax, x1, y1, x2, y2, color=NAVY, lw=2.2, z=2, style="-|>", ms=12):
    ax.add_patch(
        FancyArrowPatch(
            (x1, y1),
            (x2, y2),
            arrowstyle=style,
            mutation_scale=ms,
            linewidth=lw,
            color=color,
            zorder=z,
            shrinkA=0,
            shrinkB=0,
        )
    )


def caption(ax, text):
    ax.text(
        8,
        0.95,
        text,
        ha="center",
        va="center",
        fontsize=10.5,
        color=TEAL,
        style="italic",
        zorder=5,
    )


def title_band(ax, text):
    ax.text(
        8,
        8.05,
        text,
        ha="center",
        va="center",
        fontsize=15,
        color=NAVY,
        weight="bold",
        zorder=5,
    )
    ax.plot([6.6, 9.4], [7.55, 7.55], color=TEAL, lw=2.2, zorder=5)


def cylinder(ax, x, y, w, h, fc=SKY, ec=TEAL, z=3):
    ax.add_patch(Rectangle((x, y), w, h, facecolor=fc, edgecolor="none", zorder=z))
    ax.add_patch(
        Arc((x + w / 2, y + h), w, 0.9, theta1=0, theta2=360, edgecolor=ec, lw=1.6, zorder=z + 1)
    )
    ax.add_patch(Wedge((x + w / 2, y + h), w / 2, 0, 360, width=0.001, zorder=z))
    ax.add_patch(
        Arc((x + w / 2, y + h), w, 0.9, theta1=180, theta2=360, edgecolor=ec, lw=1.6, zorder=z + 1)
    )
    ax.add_patch(
        Arc((x + w / 2, y), w, 0.9, theta1=180, theta2=360, edgecolor=ec, lw=1.6, zorder=z + 1)
    )
    ax.plot([x, x], [y, y + h], color=ec, lw=1.6, zorder=z + 1)
    ax.plot([x + w, x + w], [y, y + h], color=ec, lw=1.6, zorder=z + 1)


# ── Per-chapter motifs ────────────────────────────────────────────────────
def m_quickstart(ax):
    title_band(ax, "30-Minute Quickstart")
    # play triangle
    ax.add_patch(Polygon([[2.4, 3.6], [2.4, 6.4], [4.6, 5.0]], closed=True, facecolor=ORANGE, edgecolor="none", zorder=3))
    steps = ["Install", "Build\nfluid", "Run\nprocess"]
    xs = [6.0, 9.2, 12.4]
    for i, (sx, lab) in enumerate(zip(xs, steps)):
        box(ax, sx, 4.0, 2.4, 2.0, lab, fc=WHITE, ec=TEAL, fs=11)
        if i < len(xs) - 1:
            arrow(ax, sx + 2.4, 5.0, sx + 3.2, 5.0)
    arrow(ax, 4.7, 5.0, 5.9, 5.0)
    caption(ax, "A fast path from install to a running flowsheet")


def m_intro(ax):
    title_band(ax, "Python meets NeqSim")
    box(ax, 2.2, 4.0, 3.2, 2.2, "Python", fc="#eaf2fb", ec=STEEL, tc=NAVY, fs=14)
    box(ax, 10.6, 4.0, 3.2, 2.2, "NeqSim\n(Java)", fc="#eef6f3", ec=TEAL, tc=TEAL, fs=13)
    # bridge
    ax.add_patch(FancyBboxPatch((5.8, 4.6), 4.4, 1.0, boxstyle="round,pad=0,rounding_size=0.25", facecolor=SAND, edgecolor=ORANGE, lw=1.6, zorder=3))
    ax.text(8.0, 5.1, "jpype bridge", ha="center", va="center", fontsize=11, color=INK, weight="bold", zorder=4)
    arrow(ax, 5.45, 5.1, 5.75, 5.1, color=NAVY)
    arrow(ax, 10.25, 5.1, 10.55, 5.1, color=NAVY)
    caption(ax, "Why drive a mature Java thermodynamics engine from Python")


def m_architecture(ax):
    title_band(ax, "Layered Java Architecture")
    layers = [("Thermodynamics", SKY), ("Physical properties", STEEL), ("Process equipment", TEAL), ("Process system", NAVY)]
    for i, (lab, c) in enumerate(layers):
        y = 2.6 + i * 1.15
        box(ax, 4.4, y, 7.2, 1.0, lab, fc=c, ec=c, tc=WHITE, fs=12)
    caption(ax, "Seven base modules stacked from EOS to flowsheet")


def m_package(ax):
    title_band(ax, "The neqsim-python Package")
    box(ax, 5.6, 4.0, 4.8, 2.2, "neqsim", fc="#eef6f3", ec=TEAL, tc=TEAL, fs=15)
    subs = ["thermo", "process", "pvt"]
    xs = [2.2, 6.4, 10.6]
    for sx, lab in zip(xs, subs):
        box(ax, sx, 1.2, 4.0, 1.2, lab, fc=WHITE, ec=STEEL, tc=NAVY, fs=11)
        arrow(ax, sx + 2.0, 2.4, 8.0, 3.95, color=LINE, lw=1.8)
    caption(ax, "High-level helpers wrapping the Java classes")


def m_java_access(ax):
    title_band(ax, "Direct Java Access")
    box(ax, 2.2, 4.0, 3.4, 2.2, "import\njneqsim", fc="#eaf2fb", ec=STEEL, tc=NAVY, fs=12)
    ax.add_patch(Polygon([[7.0, 3.6], [7.0, 6.6], [9.4, 5.1]], closed=True, facecolor=NAVY, edgecolor="none", zorder=3))
    ax.text(7.55, 5.1, "JVM", ha="center", va="center", fontsize=10, color=WHITE, weight="bold", zorder=4)
    box(ax, 10.4, 4.0, 3.4, 2.2, "Full\nJava API", fc="#eef6f3", ec=TEAL, tc=TEAL, fs=12)
    arrow(ax, 5.6, 5.1, 6.9, 5.1)
    arrow(ax, 9.5, 5.1, 10.35, 5.1)
    caption(ax, "Complete control over every NeqSim class and method")


def m_fluid(ax):
    title_band(ax, "Fluid Creation")
    # beaker
    ax.add_patch(Polygon([[6.4, 2.2], [9.6, 2.2], [9.2, 6.4], [6.8, 6.4]], closed=True, facecolor="none", edgecolor=TEAL, lw=2.0, zorder=3))
    ax.add_patch(Polygon([[6.62, 3.0], [9.38, 3.0], [9.18, 5.2], [6.82, 5.2]], closed=True, facecolor="#dceefb", edgecolor="none", zorder=2))
    import numpy as np
    rng = np.random.default_rng(7)
    for _ in range(16):
        cx = 6.9 + rng.random() * 2.1
        cy = 3.2 + rng.random() * 1.7
        ax.add_patch(Circle((cx, cy), 0.12, facecolor=ORANGE if rng.random() > 0.5 else STEEL, edgecolor="none", zorder=4))
    box(ax, 1.6, 4.4, 3.0, 1.4, "components", fc=WHITE, ec=STEEL, tc=NAVY, fs=11)
    arrow(ax, 4.6, 5.1, 6.5, 4.6, color=NAVY)
    box(ax, 11.4, 4.4, 3.2, 1.4, "EOS +\nmixing rule", fc=WHITE, ec=TEAL, tc=TEAL, fs=11)
    arrow(ax, 9.5, 4.6, 11.3, 5.1, color=NAVY)
    caption(ax, "Components, an equation of state, and a mixing rule")


def m_characterization(ax):
    title_band(ax, "Characterization & Plus Fractions")
    import numpy as np
    x = np.linspace(3.0, 13.0, 100)
    y = 2.8 + 2.8 * (1 - np.exp(-(x - 3.0) / 3.2))
    ax.plot(x, y, color=NAVY, lw=2.4, zorder=3)
    for cx in [4.2, 5.6, 7.0, 8.4]:
        ax.plot([cx, cx], [2.8, 2.8 + 2.8 * (1 - np.exp(-(cx - 3.0) / 3.2))], color=STEEL, lw=1.4, zorder=2)
        ax.add_patch(Circle((cx, 2.8), 0.13, facecolor=STEEL, edgecolor="none", zorder=4))
    ax.add_patch(FancyBboxPatch((9.4, 2.8), 3.4, 2.4, boxstyle="round,pad=0,rounding_size=0.2", facecolor="#fbeee2", edgecolor=ORANGE, lw=1.6, zorder=2))
    ax.text(11.1, 4.0, "C7+\nplus fraction", ha="center", va="center", fontsize=11, color=ORANGE, weight="bold", zorder=4)
    caption(ax, "Lumping the heavy tail into characterized pseudo-components")


def m_pvt(ax):
    title_band(ax, "PVT Phase Behaviour")
    import numpy as np
    t = np.linspace(0, 2 * np.pi, 200)
    x = 8.0 + 3.6 * np.cos(t) * (0.7 + 0.3 * np.sin(t))
    y = 4.6 + 2.0 * np.sin(t)
    ax.plot(x, y, color=NAVY, lw=2.6, zorder=3)
    ax.add_patch(Circle((8.0, 5.4), 0.16, facecolor=ORANGE, edgecolor="none", zorder=4))
    ax.text(8.0, 5.9, "critical pt", ha="center", fontsize=9, color=ORANGE, zorder=4)
    ax.text(5.2, 3.4, "bubble", fontsize=10, color=TEAL, zorder=4)
    ax.text(10.6, 6.0, "dew", fontsize=10, color=STEEL, zorder=4)
    caption(ax, "CME, CVD, and depletion across the phase envelope")


def m_units(ax):
    title_band(ax, "Unit Operations")
    # separator (vessel)
    box(ax, 2.0, 3.6, 2.6, 2.4, "", fc="#eef6f3", ec=TEAL)
    ax.text(3.3, 4.8, "Separator", ha="center", fontsize=10, color=TEAL, weight="bold", zorder=4)
    # valve (bowtie)
    ax.add_patch(Polygon([[6.4, 4.0], [6.4, 5.8], [7.6, 4.9]], closed=True, facecolor=STEEL, edgecolor="none", zorder=3))
    ax.add_patch(Polygon([[8.8, 4.0], [8.8, 5.8], [7.6, 4.9]], closed=True, facecolor=STEEL, edgecolor="none", zorder=3))
    ax.text(7.6, 3.5, "Valve", ha="center", fontsize=10, color=NAVY, weight="bold", zorder=4)
    # pump (circle)
    ax.add_patch(Circle((11.8, 4.9), 1.1, facecolor="#eaf2fb", edgecolor=STEEL, lw=1.8, zorder=3))
    ax.add_patch(Polygon([[11.8, 4.9], [12.9, 4.9], [11.8, 5.6]], closed=True, facecolor=NAVY, edgecolor="none", zorder=4))
    ax.text(11.8, 3.5, "Pump", ha="center", fontsize=10, color=NAVY, weight="bold", zorder=4)
    caption(ax, "Building blocks: separators, valves, pumps, exchangers")


def m_process(ax):
    title_band(ax, "Steady-State Process Models")
    labs = ["Feed", "Separator", "Compressor", "Export"]
    xs = [1.4, 4.8, 8.4, 12.2]
    ws = [2.4, 3.0, 3.0, 2.4]
    cs = ["#eaf2fb", "#eef6f3", "#eef6f3", "#fbeee2"]
    es = [STEEL, TEAL, TEAL, ORANGE]
    for i, (x, w, lab, c, e) in enumerate(zip(xs, ws, labs, cs, es)):
        box(ax, x, 4.0, w, 2.0, lab, fc=c, ec=e, tc=NAVY, fs=11)
        if i < len(xs) - 1:
            arrow(ax, x + w, 5.0, xs[i + 1], 5.0)
    caption(ax, "Streams wired into a converging flowsheet (PFD)")


def m_distill(ax):
    title_band(ax, "Distillation & Columns")
    box(ax, 6.6, 2.0, 2.8, 5.0, "", fc="#eef6f3", ec=TEAL, lw=2.0)
    for i in range(6):
        yy = 2.6 + i * 0.72
        ax.plot([6.7, 9.3], [yy, yy], color=TEAL, lw=1.1, zorder=4)
    arrow(ax, 4.6, 4.5, 6.55, 4.5, color=STEEL)  # feed
    arrow(ax, 9.45, 6.6, 11.4, 6.6, color=NAVY)  # tops
    arrow(ax, 9.45, 2.4, 11.4, 2.4, color=ORANGE)  # bottoms
    ax.text(3.6, 4.5, "feed", ha="center", fontsize=10, color=STEEL, zorder=4)
    ax.text(12.2, 6.6, "tops", ha="center", fontsize=10, color=NAVY, zorder=4)
    ax.text(12.4, 2.4, "bottoms", ha="center", fontsize=10, color=ORANGE, zorder=4)
    caption(ax, "Vapour-liquid contacting across equilibrium stages")


def m_dynamic(ax):
    title_band(ax, "Dynamic Process Simulation")
    import numpy as np
    x = np.linspace(2.2, 13.4, 200)
    y = 4.6 + 1.7 * (1 - np.exp(-(x - 2.2) / 1.6) * np.cos((x - 2.2) * 1.5))
    ax.plot(x, y, color=NAVY, lw=2.6, zorder=3)
    ax.plot([2.2, 13.6], [6.3, 6.3], color=ORANGE, ls="--", lw=1.4, zorder=2)
    ax.text(13.0, 6.6, "setpoint", ha="right", fontsize=9, color=ORANGE, zorder=4)
    box(ax, 2.2, 2.2, 3.0, 1.2, "controller", fc="#eaf2fb", ec=STEEL, tc=NAVY, fs=10)
    caption(ax, "Transients, PID control, and event-driven trips over time")


def m_automation(ax):
    title_band(ax, "Automation API & Plant Data")
    box(ax, 5.8, 5.4, 4.4, 1.4, '"Unit.property"', fc="#eef6f3", ec=TEAL, tc=TEAL, fs=12)
    taps = ["read", "write", "evaluate"]
    xs = [2.4, 6.4, 10.4]
    for sx, lab in zip(xs, taps):
        box(ax, sx, 2.6, 3.2, 1.3, lab, fc=WHITE, ec=STEEL, tc=NAVY, fs=11)
        arrow(ax, sx + 1.6, 3.9, 8.0, 5.35, color=LINE, lw=1.8)
    # gear
    ax.add_patch(Circle((8.0, 6.1), 0.0, zorder=1))
    caption(ax, "String-addressable variables for agents and optimisers")


def m_reporting(ax):
    title_band(ax, "Reporting & Visualization")
    # document
    ax.add_patch(FancyBboxPatch((2.4, 2.6), 4.2, 4.0, boxstyle="round,pad=0,rounding_size=0.15", facecolor=WHITE, edgecolor=STEEL, lw=1.8, zorder=3))
    for i in range(5):
        ax.plot([2.9, 6.1], [5.9 - i * 0.55, 5.9 - i * 0.55], color=LINE, lw=1.6, zorder=4)
    # bar chart
    import numpy as np
    heights = [1.2, 2.4, 1.8, 3.0]
    cols = [STEEL, ORANGE, SKY, NAVY]
    for i, (h, c) in enumerate(zip(heights, cols)):
        ax.add_patch(Rectangle((8.6 + i * 1.3, 2.6), 1.0, h, facecolor=c, edgecolor="none", zorder=3))
    caption(ax, "Turning results into tables, figures, and JSON reports")


def m_apis(ax):
    title_band(ax, "Web APIs & Operations Interfaces")
    # cloud
    ax.add_patch(Circle((7.0, 5.6), 1.2, facecolor="#eaf2fb", edgecolor=STEEL, lw=1.6, zorder=3))
    ax.add_patch(Circle((8.4, 5.9), 1.0, facecolor="#eaf2fb", edgecolor=STEEL, lw=1.6, zorder=3))
    ax.add_patch(Circle((9.4, 5.5), 0.9, facecolor="#eaf2fb", edgecolor=STEEL, lw=1.6, zorder=3))
    ax.add_patch(Rectangle((6.6, 4.9), 3.6, 0.9, facecolor="#eaf2fb", edgecolor="none", zorder=3))
    ax.text(8.2, 5.5, "{ REST API }", ha="center", va="center", fontsize=11, color=NAVY, weight="bold", zorder=5)
    box(ax, 2.0, 4.8, 2.8, 1.3, "client", fc=WHITE, ec=TEAL, tc=TEAL, fs=11)
    box(ax, 11.4, 4.8, 2.8, 1.3, "NeqSim", fc="#eef6f3", ec=TEAL, tc=TEAL, fs=11)
    arrow(ax, 4.8, 5.45, 6.0, 5.45)
    arrow(ax, 10.4, 5.45, 11.35, 5.45)
    caption(ax, "Serving NeqSim over JSON to operations runtimes")


def m_compressor(ax):
    title_band(ax, "Compressors, Curves & Anti-Surge")
    # compressor trapezoid
    ax.add_patch(Polygon([[2.2, 4.2], [4.6, 3.4], [4.6, 6.4], [2.2, 5.6]], closed=True, facecolor="#eef6f3", edgecolor=TEAL, lw=1.8, zorder=3))
    ax.text(3.4, 4.9, "comp", ha="center", fontsize=10, color=TEAL, weight="bold", zorder=4)
    import numpy as np
    x = np.linspace(6.4, 13.2, 120)
    for k, c in [(0.0, SKY), (0.6, STEEL), (1.2, NAVY)]:
        y = 6.4 - 0.05 * (x - 6.4 + k) ** 2 + k * 0.2
        ax.plot(x, np.clip(y, 3.0, 7), color=c, lw=2.0, zorder=3)
    ax.plot([6.6, 8.4], [3.4, 6.6], color=ORANGE, ls="--", lw=1.8, zorder=4)
    ax.text(7.0, 6.7, "surge", fontsize=9, color=ORANGE, zorder=4)
    caption(ax, "Performance maps, speed lines, and the surge margin")


def m_distill_deep(ax):
    title_band(ax, "Rigorous Distillation Columns")
    box(ax, 6.4, 1.7, 3.2, 5.4, "", fc="#eef6f3", ec=TEAL, lw=2.0)
    for i in range(9):
        yy = 2.2 + i * 0.55
        ax.plot([6.5, 9.5], [yy, yy], color=TEAL, lw=1.0, zorder=4)
        ax.add_patch(Circle((8.0, yy + 0.05), 0.06, facecolor=STEEL, edgecolor="none", zorder=5))
    arrow(ax, 9.6, 7.0, 11.2, 7.0, color=NAVY)
    arrow(ax, 11.2, 6.3, 9.6, 6.0, color=STEEL)  # reflux
    ax.text(12.0, 7.0, "distillate", fontsize=9, color=NAVY, zorder=4)
    ax.text(2.6, 4.4, "specs &\nsolvers", ha="center", fontsize=11, color=TEAL, weight="bold", zorder=4)
    caption(ax, "Stage-by-stage equilibrium with specifications and solvers")


def m_advanced(ax):
    title_band(ax, "Advanced & Custom Equipment")
    # gear-like
    ax.add_patch(Circle((3.6, 5.0), 1.2, facecolor="#eef6f3", edgecolor=TEAL, lw=1.8, zorder=3))
    ax.add_patch(Circle((3.6, 5.0), 0.45, facecolor=PAPER, edgecolor=TEAL, lw=1.4, zorder=4))
    box(ax, 6.4, 4.0, 3.2, 2.0, "custom\nunit", fc=WHITE, ec=STEEL, tc=NAVY, fs=11)
    cylinder(ax, 11.2, 3.6, 2.6, 2.4, fc="#eaf2fb", ec=STEEL)
    ax.text(12.5, 4.7, "DB", ha="center", fontsize=11, color=NAVY, weight="bold", zorder=6)
    arrow(ax, 4.85, 5.0, 6.35, 5.0)
    arrow(ax, 9.6, 5.0, 11.15, 5.0)
    caption(ax, "Extending NeqSim with new units and parameter databases")


def m_optimization(ax):
    title_band(ax, "Process Optimization")
    import numpy as np
    xs = np.linspace(3.0, 13.0, 60)
    ys = np.linspace(2.4, 7.0, 40)
    X, Y = np.meshgrid(xs, ys)
    Z = (X - 9.5) ** 2 / 9 + (Y - 5.2) ** 2 / 4
    ax.contour(X, Y, Z, levels=8, colors=[SKY, STEEL, NAVY], linewidths=1.0, zorder=2)
    path = [(3.6, 3.0), (5.2, 4.0), (6.9, 4.7), (8.2, 5.0), (9.5, 5.2)]
    px = [p[0] for p in path]
    py = [p[1] for p in path]
    ax.plot(px, py, color=ORANGE, lw=2.4, marker="o", ms=5, zorder=4)
    ax.add_patch(Circle((9.5, 5.2), 0.18, facecolor=ORANGE, edgecolor="none", zorder=5))
    ax.text(9.5, 5.7, "optimum", ha="center", fontsize=9, color=ORANGE, zorder=5)
    caption(ax, "Searching the decision space toward a feasible optimum")


def m_offshore(ax):
    title_band(ax, "Integrated Offshore Process Model")
    # platform deck
    ax.add_patch(Rectangle((2.0, 5.6), 12.0, 0.5, facecolor=NAVY, edgecolor="none", zorder=3))
    for lx in [3.0, 7.5, 12.5]:
        ax.plot([lx, lx], [2.4, 5.6], color=NAVY, lw=2.2, zorder=2)
    ax.plot([2.0, 14.0], [2.4, 2.4], color=SKY, lw=2.0, zorder=1)
    stages = ["Sep.", "Recompr.", "Export"]
    xs = [2.6, 6.4, 10.6]
    cs = ["#eef6f3", "#eaf2fb", "#fbeee2"]
    es = [TEAL, STEEL, ORANGE]
    for i, (x, lab, c, e) in enumerate(zip(xs, stages, cs, es)):
        box(ax, x, 6.3, 3.2, 1.2, lab, fc=c, ec=e, tc=NAVY, fs=10)
        if i < len(xs) - 1:
            arrow(ax, x + 3.2, 6.9, xs[i + 1], 6.9)
    caption(ax, "Separation, recompression, and export on one platform")


def m_colab(ax):
    title_band(ax, "Learning from the Colab Notebooks")
    for i in range(3):
        y = 5.4 - i * 1.5
        ax.add_patch(FancyBboxPatch((3.0, y), 1.2, 1.1, boxstyle="round,pad=0,rounding_size=0.12", facecolor=ORANGE if i == 0 else "#eaf2fb", edgecolor=STEEL, lw=1.4, zorder=3))
        ax.text(3.6, y + 0.55, "[ ]", ha="center", va="center", fontsize=11, color=NAVY, zorder=4)
        ax.add_patch(FancyBboxPatch((4.6, y), 8.4, 1.1, boxstyle="round,pad=0,rounding_size=0.1", facecolor=WHITE, edgecolor=LINE, lw=1.2, zorder=3))
        for j in range(3):
            ax.plot([5.0, 12.4], [y + 0.78 - j * 0.3, y + 0.78 - j * 0.3], color=LINE, lw=1.2, zorder=4)
    caption(ax, "Runnable notebooks as a guided tour of NeqSim")


# ── Registry ──────────────────────────────────────────────────────────────
MOTIFS = {
    "ch00": m_quickstart,
    "ch01": m_intro,
    "ch02": m_architecture,
    "ch03": m_package,
    "ch04": m_java_access,
    "ch05": m_fluid,
    "ch06": m_characterization,
    "ch07": m_pvt,
    "ch08": m_units,
    "ch09": m_process,
    "ch10": m_distill,
    "ch11": m_dynamic,
    "ch12": m_automation,
    "ch13": m_reporting,
    "ch14": m_apis,
    "ch15": m_compressor,
    "ch16": m_distill_deep,
    "ch17": m_advanced,
    "ch18": m_optimization,
    "ch19": m_offshore,
    "ch20": m_colab,
}


def _prefix(dirname: str) -> str:
    m = re.match(r"(ch\d+)", dirname)
    return m.group(1) if m else dirname


def generate(book_dir: Path, only=None) -> int:
    chapters_dir = book_dir / "chapters"
    if not chapters_dir.is_dir():
        print(f"ERROR: no chapters/ in {book_dir}", file=sys.stderr)
        return 2
    made = 0
    for ch in sorted(chapters_dir.iterdir()):
        if not ch.is_dir():
            continue
        prefix = _prefix(ch.name)
        if only and prefix not in only:
            continue
        motif = MOTIFS.get(prefix)
        if motif is None:
            print(f"skip {ch.name} (no motif)")
            continue
        fig_dir = ch / "figures"
        fig_dir.mkdir(exist_ok=True)
        out = fig_dir / f"{prefix}_overview.png"
        fig, ax = _ax()
        motif(ax)
        fig.savefig(out, dpi=DPI, facecolor=WHITE, bbox_inches="tight", pad_inches=0.12)
        plt.close(fig)
        made += 1
        print(f"wrote {out.relative_to(book_dir)}")
    print(f"\nGenerated {made} chapter illustration(s).")
    return 0


def main(argv=None) -> int:
    p = argparse.ArgumentParser(description="Generate chapter-opener illustrations.")
    p.add_argument("book_dir", type=Path, help="Path to the book directory")
    p.add_argument("--only", help="Comma-separated chapter prefixes, e.g. ch18,ch19")
    args = p.parse_args(argv)
    only = set(s.strip() for s in args.only.split(",")) if args.only else None
    return generate(args.book_dir, only)


if __name__ == "__main__":
    raise SystemExit(main())
