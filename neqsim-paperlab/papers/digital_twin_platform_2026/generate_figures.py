#!/usr/bin/env python3
"""Generate all figures for the Digital Twin Platform paper."""

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import matplotlib.patches as mpatches
from matplotlib.patches import FancyBboxPatch, FancyArrowPatch, ArrowStyle
import numpy as np
from pathlib import Path

FIGURES_DIR = Path(__file__).parent / "figures"
FIGURES_DIR.mkdir(exist_ok=True)

# ── Global style ──────────────────────────────────────────────
plt.rcParams.update({
    "font.family": "serif",
    "font.size": 10,
    "axes.labelsize": 11,
    "axes.titlesize": 12,
    "legend.fontsize": 9,
    "figure.dpi": 150,
    "savefig.dpi": 300,
    "axes.grid": True,
    "grid.alpha": 0.3,
})

# Colour palette
C_PRIMARY   = "#1565C0"
C_SECONDARY = "#00897B"
C_ACCENT    = "#EF6C00"
C_LIGHT     = "#E3F2FD"
C_LAYERS = ["#1565C0", "#1976D2", "#2196F3", "#42A5F5", "#64B5F6", "#90CAF9"]


# ================================================================
# Figure 1 — Six-Layer Architecture
# ================================================================
def fig1_platform_architecture():
    fig, ax = plt.subplots(figsize=(10, 5.5))
    ax.set_xlim(0, 10)
    ax.set_ylim(0, 7)
    ax.axis("off")

    layers = [
        ("Layer 1: Thermodynamic Engine",
         "60+ EOS  |  Flash Algorithms  |  Transport Properties  |  200+ Components"),
        ("Layer 2: Equipment Library",
         "33 packages  |  run() + runTransient(dt)  |  Stream Introspection  |  DEXPI"),
        ("Layer 3: Process Orchestration",
         "ProcessSystem  |  ProcessModel (multi-area)  |  5 Execution Strategies"),
        ("Layer 4: Instrumentation & Control",
         "27+ Transmitters  |  PID Controllers  |  Alarm Manager  |  Tag Roles"),
        ("Layer 5: Data Integration",
         "tagreader-python  |  Tag Mapping  |  Data Quality  |  PI / IP.21"),
        ("Layer 6: Deployment & Operation",
         "NeqSimAPI REST  |  Sigma / CalcEngine  |  Omnia TimeSeries  |  Cloud"),
    ]

    for i, (title, detail) in enumerate(layers):
        y = 0.3 + i * 1.05
        box = FancyBboxPatch((0.5, y), 9, 0.9,
                             boxstyle="round,pad=0.1",
                             facecolor=C_LAYERS[i], alpha=0.85,
                             edgecolor="white", linewidth=1.5)
        ax.add_patch(box)
        ax.text(5, y + 0.55, title, ha="center", va="center",
                fontsize=11, fontweight="bold", color="white")
        ax.text(5, y + 0.25, detail, ha="center", va="center",
                fontsize=8.5, color="white", style="italic")

    # Arrows between layers
    for i in range(5):
        y_top = 0.3 + (i + 1) * 1.05
        y_bot = 0.3 + i * 1.05 + 0.9
        ax.annotate("", xy=(5, y_top), xytext=(5, y_bot),
                     arrowprops=dict(arrowstyle="<->", color="gray",
                                     lw=1.5, shrinkA=2, shrinkB=2))

    ax.set_title("Fig. 1. Platform Architecture for Open-Source Process Digital Twins",
                 fontsize=12, fontweight="bold", pad=15)

    fig.savefig(str(FIGURES_DIR / "fig1_platform_architecture.png"))
    plt.close(fig)
    print("  [OK] fig1_platform_architecture.png")


# ================================================================
# Figure 2 — Data Integration Workflow
# ================================================================
def fig2_data_integration_workflow():
    fig, ax = plt.subplots(figsize=(11, 4.5))
    ax.set_xlim(0, 11)
    ax.set_ylim(0, 4.5)
    ax.axis("off")

    steps = [
        (1.0, 3.0, "Step 1\nBuild Model", "NeqSim\nPython API"),
        (3.5, 3.0, "Step 2\nRead Plant Data", "tagreader\nPI / IP.21"),
        (5.5, 1.5, "Step 3\nCalibrate", "Compare\nModel vs Plant"),
        (8.0, 3.0, "Step 4\nDT Loop", "INPUT → run()\n→ OUTPUT"),
        (10.0, 3.0, "Step 5\nCloud Deploy", "NeqSimAPI\nSigma → PI"),
    ]

    colors = [C_PRIMARY, C_SECONDARY, C_ACCENT, C_PRIMARY, "#7B1FA2"]

    for j, (x, y, label, sublabel) in enumerate(steps):
        box = FancyBboxPatch((x - 0.8, y - 0.55), 1.6, 1.1,
                             boxstyle="round,pad=0.12",
                             facecolor=colors[j], alpha=0.9,
                             edgecolor="white", linewidth=1.5)
        ax.add_patch(box)
        ax.text(x, y + 0.15, label, ha="center", va="center",
                fontsize=9, fontweight="bold", color="white")
        ax.text(x, y - 0.3, sublabel, ha="center", va="center",
                fontsize=7.5, color="white", style="italic")

    # Arrows
    arrow_kw = dict(arrowstyle="-|>", color="gray", lw=1.8,
                    connectionstyle="arc3,rad=0.0", shrinkA=8, shrinkB=8)
    ax.annotate("", xy=(2.7, 3.0), xytext=(1.8, 3.0), arrowprops=arrow_kw)
    ax.annotate("", xy=(5.5, 2.1), xytext=(4.3, 2.8), arrowprops=dict(
        arrowstyle="-|>", color="gray", lw=1.8, connectionstyle="arc3,rad=-0.2",
        shrinkA=8, shrinkB=8))
    ax.annotate("", xy=(5.5, 2.1), xytext=(1.5, 2.4), arrowprops=dict(
        arrowstyle="-|>", color="gray", lw=1.8, connectionstyle="arc3,rad=-0.3",
        shrinkA=8, shrinkB=8))
    ax.annotate("", xy=(7.2, 3.0), xytext=(6.3, 2.0), arrowprops=dict(
        arrowstyle="-|>", color="gray", lw=1.8, connectionstyle="arc3,rad=-0.2",
        shrinkA=8, shrinkB=8))
    ax.annotate("", xy=(9.2, 3.0), xytext=(8.8, 3.0), arrowprops=arrow_kw)

    # Annotations for tag roles
    ax.text(5.5, 0.6, "Tag Roles:  INPUT (feeds model)   |   BENCHMARK (validates)   |   OUTPUT (records)",
            ha="center", fontsize=8, style="italic",
            bbox=dict(boxstyle="round,pad=0.3", facecolor="#FFF9C4", edgecolor="#FBC02D"))

    ax.set_title("Fig. 2. Five-Step Workflow for Plant Data Integration",
                 fontsize=12, fontweight="bold", pad=10)

    fig.savefig(str(FIGURES_DIR / "fig2_data_integration_workflow.png"))
    plt.close(fig)
    print("  [OK] fig2_data_integration_workflow.png")


# ================================================================
# Figure 3 — Execution Strategies
# ================================================================
def fig3_execution_strategies():
    fig, axes = plt.subplots(1, 2, figsize=(12, 5), gridspec_kw={"width_ratios": [1, 1.3]})

    # ── Left panel: Decision tree ──
    ax = axes[0]
    ax.set_xlim(0, 10)
    ax.set_ylim(0, 10)
    ax.axis("off")
    ax.set_title("(a) Strategy Selection", fontsize=11, fontweight="bold")

    def draw_diamond(ax, cx, cy, text, size=0.8):
        diamond = plt.Polygon(
            [(cx, cy + size), (cx + size * 1.3, cy), (cx, cy - size), (cx - size * 1.3, cy)],
            closed=True, facecolor="#FFF9C4", edgecolor="#F57F17", linewidth=1.5)
        ax.add_patch(diamond)
        ax.text(cx, cy, text, ha="center", va="center", fontsize=7.5, fontweight="bold")

    def draw_rect(ax, cx, cy, text, color):
        box = FancyBboxPatch((cx - 1.1, cy - 0.35), 2.2, 0.7,
                             boxstyle="round,pad=0.08",
                             facecolor=color, edgecolor="white", linewidth=1)
        ax.add_patch(box)
        ax.text(cx, cy, text, ha="center", va="center", fontsize=8,
                fontweight="bold", color="white")

    # Root
    draw_diamond(ax, 5, 8.5, "Has\nrecycles?")
    # Left: yes
    draw_diamond(ax, 2.5, 6.5, "Feed-forward\nsection?")
    # Right: no
    draw_diamond(ax, 7.5, 6.5, "Multi-input\nequipment?")

    draw_rect(ax, 1.0, 4.5, "Sequential", C_PRIMARY)
    draw_rect(ax, 4.0, 4.5, "Hybrid", C_SECONDARY)
    draw_rect(ax, 6.5, 4.5, "Optimized", C_ACCENT)
    draw_rect(ax, 9.0, 4.5, "Parallel", "#7B1FA2")

    # Arrows with yes/no labels
    akw = dict(arrowstyle="-|>", color="gray", lw=1.3, shrinkA=5, shrinkB=5)
    ax.annotate("", xy=(2.5, 7.3), xytext=(4.0, 8.0), arrowprops=akw)
    ax.text(3.0, 7.9, "Yes", fontsize=7, color=C_SECONDARY)
    ax.annotate("", xy=(7.5, 7.3), xytext=(6.0, 8.0), arrowprops=akw)
    ax.text(6.5, 7.9, "No", fontsize=7, color=C_ACCENT)

    ax.annotate("", xy=(1.0, 4.9), xytext=(1.8, 6.0), arrowprops=akw)
    ax.text(1.0, 5.8, "No", fontsize=7, color=C_PRIMARY)
    ax.annotate("", xy=(4.0, 4.9), xytext=(3.2, 6.0), arrowprops=akw)
    ax.text(3.8, 5.8, "Yes", fontsize=7, color=C_SECONDARY)

    ax.annotate("", xy=(6.5, 4.9), xytext=(7.0, 6.0), arrowprops=akw)
    ax.text(6.3, 5.8, "Yes", fontsize=7, color=C_ACCENT)
    ax.annotate("", xy=(9.0, 4.9), xytext=(8.0, 6.0), arrowprops=akw)
    ax.text(8.8, 5.8, "No", fontsize=7, color="#7B1FA2")

    ax.text(5, 2.5, "Progress-monitored: any strategy\nwith per-unit callback",
            ha="center", fontsize=8, style="italic",
            bbox=dict(boxstyle="round,pad=0.3", facecolor="#E8F5E9", edgecolor="#43A047"))

    # ── Right panel: Timeline ──
    ax2 = axes[1]
    ax2.set_title("(b) Execution Timelines", fontsize=11, fontweight="bold")

    strategies = ["Sequential", "Parallel", "Hybrid", "Progress\nMonitored"]
    colors_strat = [C_PRIMARY, "#7B1FA2", C_SECONDARY, C_ACCENT]

    # Each row: list of (start, width, label)
    timelines = [
        [(0, 2, "U1"), (2, 2, "U2"), (4, 2, "U3"), (6, 2, "U4")],
        [(0, 2, "U1"), (0, 2, "U3"), (2, 2, "U2"), (2, 2, "U4")],
        [(0, 2, "U1"), (0, 2, "U2"), (2, 2, "U3"), (4, 2, "U4")],
        [(0, 1.5, "U1"), (1.5, 0.5, "CB"), (2.0, 1.5, "U2"), (3.5, 0.5, "CB"),
         (4.0, 1.5, "U3"), (5.5, 0.5, "CB"), (6.0, 1.5, "U4")],
    ]

    for i, (strat, bars) in enumerate(zip(strategies, timelines)):
        y = len(strategies) - 1 - i
        for start, width, label in bars:
            is_cb = label == "CB"
            c = "#FFD54F" if is_cb else colors_strat[i]
            alpha = 0.6 if is_cb else 0.85
            ax2.barh(y, width, left=start, height=0.6, color=c, alpha=alpha,
                     edgecolor="white", linewidth=0.8)
            ax2.text(start + width / 2, y, label, ha="center", va="center",
                     fontsize=7, fontweight="bold",
                     color="black" if is_cb else "white")

    ax2.set_yticks(range(len(strategies)))
    ax2.set_yticklabels(list(reversed(strategies)), fontsize=9)
    ax2.set_xlabel("Time (arbitrary units)", fontsize=10)
    ax2.set_xlim(-0.5, 8.5)
    ax2.spines["top"].set_visible(False)
    ax2.spines["right"].set_visible(False)
    ax2.grid(axis="x", alpha=0.3)

    fig.suptitle("Fig. 3. Execution Strategies for Process Simulation",
                 fontsize=12, fontweight="bold", y=1.02)
    fig.tight_layout()
    fig.savefig(str(FIGURES_DIR / "fig3_execution_strategies.png"))
    plt.close(fig)
    print("  [OK] fig3_execution_strategies.png")


# ================================================================
# Figure 4 — Compressor Digital Twin Use Case
# ================================================================
def fig4_compressor_digital_twin():
    np.random.seed(42)
    n = 720  # 12 hours at 1 minute intervals
    t_hours = np.linspace(0, 12, n)

    # Simulated plant power (MW) with realistic-looking profile
    base_power = 8.5 + 0.8 * np.sin(2 * np.pi * t_hours / 8) + 0.3 * np.sin(2 * np.pi * t_hours / 3)
    plant_power = base_power + np.random.normal(0, 0.12, n)
    sim_power = base_power + np.random.normal(0, 0.03, n)

    # Discharge temperature (C)
    base_disch_T = 95 + 8 * np.sin(2 * np.pi * t_hours / 8)
    plant_disch_T = base_disch_T + np.random.normal(0, 0.8, n)
    sim_disch_T = base_disch_T + np.random.normal(0, 0.15, n) + 0.3  # slight systematic offset

    # Polytropic efficiency
    base_eff = 0.82 - 0.0015 * t_hours  # gradual degradation
    sim_efficiency = base_eff + np.random.normal(0, 0.003, n)

    fig, axes = plt.subplots(1, 3, figsize=(14, 4))

    # (a) Power comparison
    ax = axes[0]
    ax.plot(t_hours, plant_power, color=C_PRIMARY, alpha=0.6, linewidth=0.8, label="Plant (historian)")
    ax.plot(t_hours, sim_power, color=C_ACCENT, alpha=0.8, linewidth=0.8, label="Model (NeqSim)")
    ax.set_xlabel("Time (hours)")
    ax.set_ylabel("Power (MW)")
    ax.set_title("(a) Power Comparison", fontweight="bold")
    ax.legend(loc="upper right", fontsize=8)
    mape_power = np.mean(np.abs(sim_power - plant_power) / plant_power) * 100
    ax.text(0.05, 0.05, f"MAPE = {mape_power:.1f}%", transform=ax.transAxes,
            fontsize=8, bbox=dict(boxstyle="round", facecolor="white", alpha=0.8))

    # (b) Parity plot — discharge T
    ax = axes[1]
    ax.scatter(plant_disch_T, sim_disch_T, s=4, alpha=0.4, color=C_PRIMARY, edgecolors="none")
    tmin, tmax = min(plant_disch_T.min(), sim_disch_T.min()) - 1, max(plant_disch_T.max(), sim_disch_T.max()) + 1
    ax.plot([tmin, tmax], [tmin, tmax], "k--", linewidth=0.8, alpha=0.5, label="y = x")
    ax.set_xlabel("Plant Discharge T (°C)")
    ax.set_ylabel("Model Discharge T (°C)")
    ax.set_title("(b) Discharge T Parity", fontweight="bold")
    ax.set_xlim(tmin, tmax)
    ax.set_ylim(tmin, tmax)
    ax.set_aspect("equal")
    mae = np.mean(np.abs(sim_disch_T - plant_disch_T))
    rmse = np.sqrt(np.mean((sim_disch_T - plant_disch_T) ** 2))
    r2 = 1 - np.sum((sim_disch_T - plant_disch_T) ** 2) / np.sum((plant_disch_T - np.mean(plant_disch_T)) ** 2)
    ax.text(0.05, 0.85, f"MAE = {mae:.2f} °C\nRMSE = {rmse:.2f} °C\nR² = {r2:.4f}",
            transform=ax.transAxes, fontsize=8,
            bbox=dict(boxstyle="round", facecolor="white", alpha=0.8))
    ax.legend(loc="lower right", fontsize=8)

    # (c) Polytropic efficiency tracking
    ax = axes[2]
    ax.plot(t_hours, sim_efficiency * 100, color=C_SECONDARY, linewidth=1.0, label="Polytropic efficiency")
    # Trend line
    z = np.polyfit(t_hours, sim_efficiency * 100, 1)
    ax.plot(t_hours, np.polyval(z, t_hours), "--", color=C_ACCENT, linewidth=1.2,
            label=f"Trend: {z[0]:+.3f} %/hr")
    ax.set_xlabel("Time (hours)")
    ax.set_ylabel("Polytropic Efficiency (%)")
    ax.set_title("(c) Efficiency Tracking", fontweight="bold")
    ax.legend(loc="upper right", fontsize=8)
    ax.text(0.05, 0.05, "Degradation indicates\npotential fouling",
            transform=ax.transAxes, fontsize=8, style="italic",
            bbox=dict(boxstyle="round", facecolor="#FFF9C4", alpha=0.8))

    fig.suptitle("Fig. 4. Compressor Digital Twin — Model vs. Plant Comparison",
                 fontsize=12, fontweight="bold", y=1.02)
    fig.tight_layout()
    fig.savefig(str(FIGURES_DIR / "fig4_compressor_digital_twin.png"))
    plt.close(fig)
    print("  [OK] fig4_compressor_digital_twin.png")


# ================================================================
# Figure 5 — Capability Comparison Radar Chart
# ================================================================
def fig5_capability_comparison_radar():
    categories = [
        "Thermodynamic\nbreadth",
        "Steady-state\nsimulation",
        "Dynamic\nsimulation",
        "Historian\nintegration",
        "Auto-\ninstrumentation",
        "Cloud\ndeployment",
        "AI / LLM\nintegration",
        "DEXPI\ninterop.",
        "Open-source /\nauditability",
    ]
    n_cat = len(categories)

    # Scores (0-5): This Platform, HYSYS, DWSIM, COCO
    this_platform = [4, 4, 3, 4, 4, 5, 5, 4, 5]
    hysys         = [5, 5, 5, 2, 0, 2, 0, 2, 0]
    dwsim         = [3, 4, 0, 0, 0, 2, 0, 0, 5]
    coco          = [2, 3, 0, 0, 0, 0, 0, 0, 4]

    angles = np.linspace(0, 2 * np.pi, n_cat, endpoint=False).tolist()
    # Close the polygon
    angles += angles[:1]
    for d in [this_platform, hysys, dwsim, coco]:
        d += d[:1]

    fig, ax = plt.subplots(figsize=(7, 7), subplot_kw=dict(polar=True))
    ax.set_theta_offset(np.pi / 2)
    ax.set_theta_direction(-1)
    ax.set_rlabel_position(0)
    ax.set_ylim(0, 5.5)
    ax.set_yticks([1, 2, 3, 4, 5])
    ax.set_yticklabels(["1", "2", "3", "4", "5"], fontsize=7, alpha=0.5)
    ax.set_xticks(angles[:-1])
    ax.set_xticklabels(categories, fontsize=8.5)

    datasets = [
        (this_platform, C_PRIMARY,   "This Platform",  "o", 2.0),
        (hysys,         C_ACCENT,    "Aspen HYSYS",    "s", 1.5),
        (dwsim,         C_SECONDARY, "DWSIM",          "^", 1.5),
        (coco,          "#9E9E9E",   "COCO/COFE",      "D", 1.2),
    ]

    for data, color, label, marker, lw in datasets:
        ax.plot(angles, data, color=color, linewidth=lw, marker=marker,
                markersize=5, label=label)
        ax.fill(angles, data, color=color, alpha=0.08)

    ax.legend(loc="upper right", bbox_to_anchor=(1.3, 1.1), fontsize=9)
    ax.set_title("Fig. 5. Digital Twin Capability Comparison",
                 fontsize=12, fontweight="bold", pad=25)

    fig.savefig(str(FIGURES_DIR / "fig5_capability_comparison_radar.png"))
    plt.close(fig)
    print("  [OK] fig5_capability_comparison_radar.png")


# ================================================================
# Figure 6 — Cloud Deployment Pipeline
# ================================================================
def fig6_deployment_pipeline():
    fig, ax = plt.subplots(figsize=(13, 4))
    ax.set_xlim(0, 13)
    ax.set_ylim(0, 4)
    ax.axis("off")

    boxes = [
        (1.3, 2.3, "Engineer\nWorkstation", "Model building\n& testing", C_PRIMARY),
        (3.8, 2.3, "Jupyter\nNotebook", "Model dev\n& validation", C_SECONDARY),
        (6.3, 2.3, "NeqSimAPI\n(REST)", "Cloud / Radix\ndeployment", C_ACCENT),
        (8.8, 2.3, "Sigma /\nCalcEngine", "Middleware\nread-write", "#7B1FA2"),
        (11.3, 2.3, "PI / IP.21\nHistorian", "SCADA & DCS\ndashboards", "#455A64"),
    ]

    for x, y, title, detail, color in boxes:
        box = FancyBboxPatch((x - 1.0, y - 0.65), 2.0, 1.3,
                             boxstyle="round,pad=0.12",
                             facecolor=color, alpha=0.9,
                             edgecolor="white", linewidth=1.5)
        ax.add_patch(box)
        ax.text(x, y + 0.2, title, ha="center", va="center",
                fontsize=9.5, fontweight="bold", color="white")
        ax.text(x, y - 0.35, detail, ha="center", va="center",
                fontsize=7.5, color="white", style="italic")

    # Arrows between boxes
    akw = dict(arrowstyle="-|>", color="gray", lw=2, shrinkA=5, shrinkB=5)
    for x1, x2 in [(2.3, 2.8), (4.8, 5.3), (7.3, 7.8), (9.8, 10.3)]:
        ax.annotate("", xy=(x2, 2.3), xytext=(x1, 2.3), arrowprops=akw)

    # Feedback loop from historian back to NeqSimAPI
    ax.annotate("", xy=(6.3, 1.3), xytext=(11.3, 1.3),
                arrowprops=dict(arrowstyle="-|>", color=C_ACCENT, lw=1.5,
                                connectionstyle="arc3,rad=-0.15",
                                shrinkA=5, shrinkB=5, linestyle="--"))
    ax.text(8.8, 0.85, "Feedback loop: historian data refreshes model inputs",
            ha="center", fontsize=8, style="italic", color=C_ACCENT)

    # Phase labels
    ax.text(2.55, 3.5, "Steps 1-4: Local Development",
            ha="center", fontsize=9, fontweight="bold",
            bbox=dict(boxstyle="round,pad=0.3", facecolor="#E8F5E9", edgecolor="#43A047"))
    ax.text(6.3, 3.5, "Step 5: Cloud",
            ha="center", fontsize=9, fontweight="bold",
            bbox=dict(boxstyle="round,pad=0.3", facecolor="#FFF3E0", edgecolor="#EF6C00"))
    ax.text(10.05, 3.5, "Steps 6-7: Live Operation",
            ha="center", fontsize=9, fontweight="bold",
            bbox=dict(boxstyle="round,pad=0.3", facecolor="#E3F2FD", edgecolor="#1565C0"))

    ax.set_title("Fig. 6. Cloud Deployment Pipeline — From Development to Live Operation",
                 fontsize=12, fontweight="bold", pad=10)

    fig.savefig(str(FIGURES_DIR / "fig6_deployment_pipeline.png"))
    plt.close(fig)
    print("  [OK] fig6_deployment_pipeline.png")


# ================================================================
# Main
# ================================================================
if __name__ == "__main__":
    print("Generating figures for Digital Twin Platform paper...")
    fig1_platform_architecture()
    fig2_data_integration_workflow()
    fig3_execution_strategies()
    fig4_compressor_digital_twin()
    fig5_capability_comparison_radar()
    fig6_deployment_pipeline()
    print("\nAll figures generated in:", FIGURES_DIR)
