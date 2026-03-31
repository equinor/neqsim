"""
Figure generation script for the AI-PRO roadmap paper.

Generates all 10 figures from benchmark_results.json and paper content.
Output: figures/ directory with PNG files at 300 dpi.

Usage:
    python generate_figures.py
"""

import json
import numpy as np
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import matplotlib.patches as mpatches
from matplotlib.patches import FancyBboxPatch, FancyArrowPatch
from pathlib import Path

# --- Configuration ---
PAPER_DIR = Path(__file__).parent
FIGURES_DIR = PAPER_DIR / "figures"
RESULTS_DIR = PAPER_DIR / "results"
FIGURES_DIR.mkdir(exist_ok=True)

DPI = 300
FIGSIZE_SINGLE = (7, 4.5)
FIGSIZE_DOUBLE = (7, 6)
FIGSIZE_WIDE = (10, 5)
FIGSIZE_ARCH = (10, 7)

# Elsevier-friendly style
plt.rcParams.update({
    'font.family': 'serif',
    'font.serif': ['Times New Roman', 'DejaVu Serif'],
    'font.size': 9,
    'axes.labelsize': 10,
    'axes.titlesize': 11,
    'xtick.labelsize': 8,
    'ytick.labelsize': 8,
    'legend.fontsize': 8,
    'figure.dpi': DPI,
    'savefig.dpi': DPI,
    'savefig.bbox': 'tight',
    'axes.grid': True,
    'grid.alpha': 0.3,
    'axes.axisbelow': True,
})

# Color palette (colorblind-accessible)
C_BLUE = '#2166ac'
C_RED = '#b2182b'
C_GREEN = '#1b7837'
C_ORANGE = '#e08214'
C_PURPLE = '#762a83'
C_GRAY = '#636363'
C_LIGHT_BLUE = '#92c5de'
C_LIGHT_GREEN = '#a6dba0'
C_LIGHT_ORANGE = '#fdb863'
C_LIGHT_PURPLE = '#c2a5cf'


def load_benchmark_data():
    """Load benchmark results JSON."""
    with open(RESULTS_DIR / "benchmark_results.json") as f:
        return json.load(f)


# =========================================================================
# Figure 1: Framework Architecture (conceptual diagram)
# =========================================================================
def fig1_framework_architecture():
    """AI-PRO framework architecture diagram."""
    fig, ax = plt.subplots(1, 1, figsize=(10, 7.5))
    ax.set_xlim(0, 10)
    ax.set_ylim(0, 8)
    ax.axis('off')

    def box(x, y, w, h, label, color, alpha=0.85, fontsize=8, bold=False):
        rect = FancyBboxPatch((x, y), w, h, boxstyle="round,pad=0.1",
                              facecolor=color, edgecolor='#333333', linewidth=1.0, alpha=alpha)
        ax.add_patch(rect)
        weight = 'bold' if bold else 'normal'
        ax.text(x + w/2, y + h/2, label, ha='center', va='center',
                fontsize=fontsize, fontweight=weight, wrap=True,
                color='white' if color in [C_BLUE, C_RED, C_PURPLE, C_GREEN, '#333333'] else 'black')

    def arrow(x1, y1, x2, y2, color='#555555', style='->', lw=1.2):
        ax.annotate('', xy=(x2, y2), xytext=(x1, y1),
                    arrowprops=dict(arrowstyle=style, color=color, lw=lw))

    def label_arrow(x1, y1, x2, y2, label, color='#555555'):
        midx, midy = (x1+x2)/2, (y1+y2)/2
        arrow(x1, y1, x2, y2, color=color)
        ax.text(midx, midy + 0.15, label, ha='center', va='bottom', fontsize=6.5, color=color, style='italic')

    # Title
    ax.text(5, 7.7, "AI-PRO Framework Architecture", ha='center', fontsize=13, fontweight='bold')

    # --- Layer 1: Physical Domains (bottom) ---
    ax.text(5, 0.15, "Physical Domain Layer", ha='center', fontsize=9, fontweight='bold', color=C_GRAY)
    box(0.2, 0.4, 2.8, 1.3, "Reservoir\nSimulator\n(OPM Flow)", C_BLUE, fontsize=8)
    box(3.6, 0.4, 2.8, 1.3, "Wellbore &\nPipeline\n(Multiphase Flow)", C_BLUE, fontsize=8)
    box(7.0, 0.4, 2.8, 1.3, "Process\nFacilities\n(Open-source Sim.)", C_BLUE, fontsize=8)

    # Coupling arrows between domains
    label_arrow(3.0, 1.05, 3.6, 1.05, "IPR", C_BLUE)
    label_arrow(6.4, 1.05, 7.0, 1.05, "P, T, q, z", C_BLUE)

    # --- Layer 2: API / Interface (middle-low) ---
    ax.text(5, 2.0, "Interface Layer", ha='center', fontsize=9, fontweight='bold', color=C_GRAY)
    box(0.5, 2.3, 2.0, 0.9, "Reservoir\nCoupling I/F", C_GREEN, fontsize=7.5)
    box(4.0, 2.3, 2.0, 0.9, "JSON Process\nAPI", C_GREEN, fontsize=7.5)
    box(7.5, 2.3, 2.0, 0.9, "MCP Server\n(6 tools)", C_GREEN, fontsize=7.5)

    arrow(1.5, 1.7, 1.5, 2.3, C_GREEN)
    arrow(5.0, 1.7, 5.0, 2.3, C_GREEN)
    arrow(8.5, 1.7, 8.5, 2.3, C_GREEN)

    # --- Layer 3: AI Capabilities (middle-high) ---
    ax.text(5, 3.55, "AI Integration Layer", ha='center', fontsize=9, fontweight='bold', color=C_GRAY)
    box(0.2, 3.85, 2.2, 1.1, "Surrogate\nEquipment\n(ONNX)", C_ORANGE, fontsize=7.5)
    box(2.7, 3.85, 2.2, 1.1, "RL Environment\n(Gymnasium)", C_ORANGE, fontsize=7.5)
    box(5.2, 3.85, 2.2, 1.1, "Training Data\nGenerator\n(BatchRunner)", C_ORANGE, fontsize=7.5)
    box(7.7, 3.85, 2.1, 1.1, "UQ Module\n(Conformal\nPrediction)", C_ORANGE, fontsize=7.5)

    # Connections interface -> AI layer
    arrow(1.5, 3.2, 1.3, 3.85, C_ORANGE)
    arrow(5.0, 3.2, 3.8, 3.85, C_ORANGE)
    arrow(5.0, 3.2, 6.3, 3.85, C_ORANGE)
    arrow(8.5, 3.2, 8.75, 3.85, C_ORANGE)

    # --- Layer 4: AI Methods (top) ---
    ax.text(5, 5.3, "AI Methods Layer", ha='center', fontsize=9, fontweight='bold', color=C_GRAY)
    box(0.3, 5.6, 2.6, 1.0, "PINN / DeepONet\nSurrogates", C_PURPLE, fontsize=8)
    box(3.35, 5.6, 2.6, 1.0, "PPO / SAC\nRL Optimization", C_PURPLE, fontsize=8)
    box(6.4, 5.6, 3.1, 1.0, "LLM Agents\n(Claude / GPT via MCP)", C_PURPLE, fontsize=8)

    arrow(1.3, 4.95, 1.6, 5.6, C_PURPLE)
    arrow(3.8, 4.95, 4.65, 5.6, C_PURPLE)
    arrow(6.3, 4.95, 6.3, 5.6, C_PURPLE)
    arrow(8.75, 4.95, 7.95, 5.6, C_PURPLE)

    # --- Feedback arrows ---
    ax.annotate('', xy=(2.7, 6.1), xytext=(3.35, 6.1),
                arrowprops=dict(arrowstyle='<->', color=C_RED, lw=1.5, linestyle='--'))
    ax.text(3.03, 6.35, "model\nupdate", ha='center', fontsize=6, color=C_RED, style='italic')

    # Legend
    legend_elements = [
        mpatches.Patch(facecolor=C_BLUE, label='Physical domain', alpha=0.85),
        mpatches.Patch(facecolor=C_GREEN, label='Interface / API', alpha=0.85),
        mpatches.Patch(facecolor=C_ORANGE, label='AI integration (proposed)', alpha=0.85),
        mpatches.Patch(facecolor=C_PURPLE, label='AI methods', alpha=0.85),
    ]
    ax.legend(handles=legend_elements, loc='upper left', frameon=True, fontsize=7.5,
              bbox_to_anchor=(0.0, 0.95), ncol=2)

    fig.savefig(FIGURES_DIR / "fig1_framework_architecture.png", dpi=DPI, bbox_inches='tight')
    plt.close(fig)
    print("  fig1_framework_architecture.png")


# =========================================================================
# Figure 2: Capability Coverage Heatmap
# =========================================================================
def fig2_capability_heatmap():
    """Capability coverage heatmap."""
    categories = [
        "Equations of State\n(60+ classes)",
        "Flash Calculations\n(TP, PH, PS, ...)",
        "Process Equipment\n(33 types)",
        "Multiphase Pipe\nFlow",
        "Bottleneck /\nUtilization",
        "Dynamic\nSimulation",
        "JSON Process\nAPI",
        "MCP Server\n(LLM access)",
        "Surrogate\nEquipment I/F",
        "RL Environment\nWrapper",
        "Batch Training\nData Generator",
        "Reservoir-Process\nCoupling",
        "Auto-\nDifferentiation",
    ]

    # Maturity levels: 0=missing, 1=concept, 2=partial, 3=full
    maturity = [3, 3, 3, 2.5, 3, 2, 3, 3, 0, 0.5, 0.5, 1, 0]

    fig, ax = plt.subplots(figsize=(7, 5.5))

    colors = []
    for m in maturity:
        if m >= 2.5:
            colors.append(C_GREEN)
        elif m >= 1.5:
            colors.append(C_LIGHT_GREEN)
        elif m >= 0.5:
            colors.append(C_LIGHT_ORANGE)
        else:
            colors.append(C_RED)

    y_pos = np.arange(len(categories))
    bars = ax.barh(y_pos, maturity, color=colors, edgecolor='#444444', linewidth=0.5, height=0.7)

    ax.set_yticks(y_pos)
    ax.set_yticklabels(categories, fontsize=8)
    ax.set_xlabel("Maturity Level", fontsize=10)
    ax.set_xlim(0, 3.5)
    ax.set_xticks([0, 1, 2, 3])
    ax.set_xticklabels(["Missing", "Concept", "Partial", "Full"], fontsize=8)
    ax.invert_yaxis()
    ax.set_title("Platform Capability Maturity for AI Integration", fontsize=11, fontweight='bold')

    # Add value labels
    for bar, m in zip(bars, maturity):
        label = {0: "Gap", 0.5: "Concept", 1: "Concept", 1.5: "Partial",
                 2: "Partial", 2.5: "Mature", 3: "Full"}[m]
        ax.text(bar.get_width() + 0.05, bar.get_y() + bar.get_height()/2,
                label, va='center', fontsize=7, color=C_GRAY)

    # Divider between existing and proposed
    ax.axhline(y=7.5, color=C_RED, linestyle='--', linewidth=1.0, alpha=0.7)
    ax.text(3.3, 7.5, "Existing ↑\nProposed ↓", ha='center', va='center', fontsize=7,
            color=C_RED, fontweight='bold')

    plt.tight_layout()
    fig.savefig(FIGURES_DIR / "fig2_capability_heatmap.png", dpi=DPI, bbox_inches='tight')
    plt.close(fig)
    print("  fig2_capability_heatmap.png")


# =========================================================================
# Figure 3: Execution Times Bar Chart
# =========================================================================
def fig3_execution_times(data):
    """Execution times across configurations."""
    configs = [
        ("Flash\n(5 comp)", data['flash']['TPflash_lean_5comp']),
        ("Flash\n(9 comp)", data['flash']['TPflash_natgas_9comp']),
        ("Flash\n(13 comp)", data['flash']['TPflash_ogw_13comp']),
        ("Single\nSeparator", data['single_separator']),
        ("2-Stage\nSeparation", data['two_stage_separation']),
        ("Compression\nTrain", data['compression_train']),
        ("Full\nProcess", data['full_process']),
        ("RL Step", data['rl_step']),
    ]

    labels = [c[0] for c in configs]
    means = [c[1]['mean_ms'] for c in configs]
    stds = [c[1]['std_ms'] for c in configs]

    # Warm-JVM means (excluding first evaluation)
    warm_means = []
    for _, d in configs:
        raw = d.get('raw_ms', d.get('raw_ms', [d['mean_ms']]))
        if len(raw) > 1:
            warm_means.append(np.mean(raw[1:]))
        else:
            warm_means.append(d['mean_ms'])

    color_map = [C_BLUE]*3 + [C_GREEN]*4 + [C_PURPLE]

    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(10, 4.5), gridspec_kw={'width_ratios': [3, 2]})

    # Left: All configurations, log scale
    x = np.arange(len(labels))
    bars = ax1.bar(x, means, yerr=stds, capsize=3, color=color_map,
                   edgecolor='#333333', linewidth=0.5, width=0.65, alpha=0.85)

    # Add warm-JVM markers
    ax1.scatter(x, warm_means, marker='D', color='white', edgecolor='black',
                s=25, zorder=5, label='Warm JVM')

    ax1.set_yscale('log')
    ax1.set_xticks(x)
    ax1.set_xticklabels(labels, fontsize=7.5)
    ax1.set_ylabel("Execution Time (ms)", fontsize=10)
    ax1.set_title("(a) Execution Time by Configuration", fontsize=10, fontweight='bold')
    ax1.legend(fontsize=7.5)

    # Add mean labels
    for i, (m, w) in enumerate(zip(means, warm_means)):
        ax1.text(i, m * 1.4, f"{m:.1f}", ha='center', va='bottom', fontsize=6.5, color=C_GRAY)

    # Legend for color coding
    legend_patches = [
        mpatches.Patch(color=C_BLUE, label='Flash calculations'),
        mpatches.Patch(color=C_GREEN, label='Process equipment'),
        mpatches.Patch(color=C_PURPLE, label='RL step'),
    ]
    ax1.legend(handles=legend_patches + [plt.Line2D([0], [0], marker='D', color='w',
               markerfacecolor='white', markeredgecolor='black', markersize=5, label='Warm JVM')],
               fontsize=7, loc='upper left')

    # Right: Throughput (evaluations per second)
    throughputs = [1000.0/w for w in warm_means]
    bars2 = ax2.barh(x, throughputs, color=color_map, edgecolor='#333333',
                     linewidth=0.5, height=0.65, alpha=0.85)
    ax2.set_yticks(x)
    ax2.set_yticklabels(labels, fontsize=7.5)
    ax2.set_xlabel("Throughput (evaluations/s)", fontsize=10)
    ax2.set_title("(b) Sustained Throughput", fontsize=10, fontweight='bold')
    ax2.set_xscale('log')
    ax2.invert_yaxis()

    for i, t in enumerate(throughputs):
        ax2.text(t * 1.15, i, f"{t:.0f}/s", va='center', fontsize=6.5, color=C_GRAY)

    plt.tight_layout()
    fig.savefig(FIGURES_DIR / "fig3_execution_times.png", dpi=DPI, bbox_inches='tight')
    plt.close(fig)
    print("  fig3_execution_times.png")


# =========================================================================
# Figure 4: Training Data Generation Rate
# =========================================================================
def fig4_training_data_rate(data):
    """Training data generation projections."""
    td = data['training_data']
    rate_per_sec = td['samples_per_second']

    # Dataset sizes
    n_samples = np.logspace(3, 7, 50)  # 1e3 to 1e7

    # Time at different rates
    flash_rate = rate_per_sec  # ~1235/s for lean flash
    process_rate = 50.0  # ~50/s for full process (warm JVM ~20ms)
    rl_rate = data['rl_step']['mean_ms']  # ~669/s

    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(10, 4.5))

    # Left: Generation time vs dataset size
    ax1.loglog(n_samples, n_samples / flash_rate / 60, '-', color=C_BLUE, lw=2, label=f'Flash ({flash_rate:.0f}/s)')
    ax1.loglog(n_samples, n_samples / process_rate / 60, '-', color=C_ORANGE, lw=2, label=f'Full process ({process_rate:.0f}/s)')

    # Reference lines
    ax1.axhline(y=60, color=C_GRAY, linestyle='--', alpha=0.5)
    ax1.text(1.2e3, 65, '1 hour', fontsize=7, color=C_GRAY)
    ax1.axhline(y=480, color=C_GRAY, linestyle='--', alpha=0.5)
    ax1.text(1.2e3, 520, '8 hours', fontsize=7, color=C_GRAY)

    # Target markers
    for target, label in [(1e5, '10$^5$'), (1e6, '10$^6$')]:
        ax1.axvline(x=target, color=C_GREEN, linestyle=':', alpha=0.5)
        ax1.text(target * 1.1, 0.15, label, fontsize=7, color=C_GREEN, rotation=90)

    ax1.set_xlabel("Number of Training Samples", fontsize=10)
    ax1.set_ylabel("Generation Time (minutes)", fontsize=10)
    ax1.set_title("(a) Training Data Generation Time", fontsize=10, fontweight='bold')
    ax1.legend(fontsize=8)
    ax1.set_xlim(1e3, 1e7)
    ax1.set_ylim(0.1, 5000)

    # Right: Throughput comparison
    configs = ['Flash\n(5-comp)', 'Flash\n(9-comp)', 'Flash\n(13-comp)',
               'Separator', '2-Stage\nSep.', 'Compression', 'Full\nProcess']

    # Warm JVM throughputs
    warm_times = []
    for key in ['TPflash_lean_5comp', 'TPflash_natgas_9comp', 'TPflash_ogw_13comp']:
        raw = data['flash'][key]['raw_ms']
        warm_times.append(np.mean(raw[1:]))
    for key in ['single_separator', 'two_stage_separation', 'compression_train', 'full_process']:
        raw = data[key]['raw_ms']
        warm_times.append(np.mean(raw[1:]))

    throughputs = [1000.0/t for t in warm_times]

    colors2 = [C_BLUE]*3 + [C_GREEN]*4
    y = np.arange(len(configs))
    ax2.barh(y, throughputs, color=colors2, edgecolor='#333333', linewidth=0.5, height=0.6, alpha=0.85)
    ax2.set_yticks(y)
    ax2.set_yticklabels(configs, fontsize=7.5)
    ax2.set_xlabel("Throughput (samples/second)", fontsize=10)
    ax2.set_title("(b) Throughput by Complexity", fontsize=10, fontweight='bold')
    ax2.set_xscale('log')
    ax2.invert_yaxis()

    for i, t in enumerate(throughputs):
        ax2.text(t * 1.2, i, f"{t:.0f}", va='center', fontsize=7, color=C_GRAY)

    plt.tight_layout()
    fig.savefig(FIGURES_DIR / "fig4_training_data_rate.png", dpi=DPI, bbox_inches='tight')
    plt.close(fig)
    print("  fig4_training_data_rate.png")


# =========================================================================
# Figure 5: RL Step Distribution
# =========================================================================
def fig5_rl_step_distribution(data):
    """RL step time distribution and training feasibility."""
    rl = data['rl_step']
    raw_ms = rl['raw_ms']
    steps_per_sec = rl['steps_per_second']

    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(10, 4))

    # Left: Step time distribution
    ax1.hist(raw_ms, bins=12, color=C_PURPLE, edgecolor='white', alpha=0.85)
    ax1.axvline(x=rl['mean_ms'], color=C_RED, linestyle='--', lw=1.5, label=f"Mean = {rl['mean_ms']:.2f} ms")
    ax1.axvline(x=rl['mean_ms'] + rl['std_ms'], color=C_ORANGE, linestyle=':', lw=1,
                label=f"$\\pm 1\\sigma$ = {rl['std_ms']:.2f} ms")
    ax1.axvline(x=rl['mean_ms'] - rl['std_ms'], color=C_ORANGE, linestyle=':', lw=1)
    ax1.set_xlabel("Step Execution Time (ms)", fontsize=10)
    ax1.set_ylabel("Count", fontsize=10)
    ax1.set_title("(a) RL Step Time Distribution", fontsize=10, fontweight='bold')
    ax1.legend(fontsize=7.5)

    # Right: Training time vs budget
    budgets = [1e4, 5e4, 1e5, 5e5, 1e6, 5e6, 1e7]
    times_min = [b / steps_per_sec / 60 for b in budgets]

    ax2.semilogy(range(len(budgets)), times_min, 'o-', color=C_PURPLE, lw=2, markersize=6)
    ax2.set_xticks(range(len(budgets)))
    ax2.set_xticklabels([f"$10^{{{int(np.log10(b))}}}$" if b in [1e4, 1e5, 1e6, 1e7]
                          else f"$5 \\times 10^{{{int(np.log10(b/5))}}}$"
                          for b in budgets], fontsize=8)
    ax2.set_xlabel("Training Budget (environment steps)", fontsize=10)
    ax2.set_ylabel("Training Time (minutes)", fontsize=10)
    ax2.set_title("(b) RL Training Time at 669 steps/s", fontsize=10, fontweight='bold')

    # Add reference lines
    ax2.axhline(y=60, color=C_GRAY, linestyle='--', alpha=0.5)
    ax2.text(0.1, 70, '1 hour', fontsize=7, color=C_GRAY)
    ax2.axhline(y=480, color=C_GRAY, linestyle='--', alpha=0.5)
    ax2.text(0.1, 550, '8 hours', fontsize=7, color=C_GRAY)

    # Annotate key points
    for i, (b, t) in enumerate(zip(budgets, times_min)):
        if b in [1e5, 1e6, 1e7]:
            ax2.annotate(f"{t:.1f} min", (i, t), textcoords="offset points",
                         xytext=(10, 5), fontsize=7, color=C_PURPLE)

    plt.tight_layout()
    fig.savefig(FIGURES_DIR / "fig5_rl_step_distribution.png", dpi=DPI, bbox_inches='tight')
    plt.close(fig)
    print("  fig5_rl_step_distribution.png")


# =========================================================================
# Figure 6: Development Roadmap (Gantt-style)
# =========================================================================
def fig6_gap_roadmap():
    """Development roadmap Gantt chart."""
    fig, ax = plt.subplots(figsize=(10, 4.5))

    # Development areas with start/end months and dependencies
    tasks = [
        ("DA1: Surrogate Equipment I/F", 3, 15, C_RED, "Critical"),
        ("DA2: RL Environment Wrapper", 6, 18, C_RED, "Critical"),
        ("DA3: Reservoir-Process Coupling", 6, 24, C_ORANGE, "High"),
        ("DA4: Auto-Differentiation", 15, 30, C_BLUE, "Medium"),
        ("DA5: Batch Execution", 12, 24, C_BLUE, "Medium"),
    ]

    # AI-PRO work packages (background)
    wps = [
        ("WP1: Surrogates", 1, 24, C_LIGHT_BLUE),
        ("WP2: Hybrid Architecture", 6, 30, C_LIGHT_GREEN),
        ("WP3: Bottleneck Metrics", 3, 24, C_LIGHT_ORANGE),
        ("WP4: RL Optimization", 12, 36, C_LIGHT_PURPLE),
    ]

    ax.set_xlim(0, 38)
    ax.set_ylim(-0.5, len(tasks) + len(wps) + 0.5)

    # Draw WP bands in background
    for i, (name, start, end, color) in enumerate(wps):
        y = len(tasks) + i
        ax.barh(y, end - start, left=start, height=0.5, color=color, alpha=0.4,
                edgecolor='#999999', linewidth=0.5)
        ax.text(start + (end-start)/2, y, name, ha='center', va='center', fontsize=7, color=C_GRAY)

    # Draw development area bars
    for i, (name, start, end, color, priority) in enumerate(tasks):
        ax.barh(i, end - start, left=start, height=0.65, color=color, alpha=0.85,
                edgecolor='#333333', linewidth=0.8)
        ax.text(0.5, i, name, ha='left', va='center', fontsize=8, fontweight='bold',
                color='black')
        ax.text(end + 0.5, i, f"[{priority}]", ha='left', va='center', fontsize=7, color=color)

    # Dependency arrows
    ax.annotate('', xy=(6, 1), xytext=(15, 0),
                arrowprops=dict(arrowstyle='->', color=C_GRAY, lw=1, linestyle='--'))

    # Milestones
    milestones = [(12, "M1: Prototype"), (24, "M2: Integration"), (36, "M3: Validation")]
    for month, label in milestones:
        ax.axvline(x=month, color=C_GRAY, linestyle=':', alpha=0.5)
        ax.text(month, len(tasks) + len(wps) + 0.3, label, ha='center', fontsize=7,
                color=C_GRAY, fontweight='bold')

    ax.set_yticks([])
    ax.set_xlabel("Project Month", fontsize=10)
    ax.set_title("Development Roadmap Aligned with AI-PRO Work Packages", fontsize=11, fontweight='bold')
    ax.set_xticks([0, 6, 12, 18, 24, 30, 36])

    # Separator line
    ax.axhline(y=len(tasks) - 0.5, color='#999999', linestyle='-', linewidth=0.5)
    ax.text(37, len(tasks) - 0.3, "AI-PRO WPs ↑", fontsize=6, color=C_GRAY, va='bottom')

    plt.tight_layout()
    fig.savefig(FIGURES_DIR / "fig6_gap_roadmap.png", dpi=DPI, bbox_inches='tight')
    plt.close(fig)
    print("  fig6_gap_roadmap.png")


# =========================================================================
# Figure 7: Surrogate Architecture Detail
# =========================================================================
def fig7_surrogate_architecture():
    """Hybrid physics-AI simulation architecture."""
    fig, ax = plt.subplots(figsize=(10, 6))
    ax.set_xlim(0, 10)
    ax.set_ylim(0, 7)
    ax.axis('off')

    def box(x, y, w, h, label, color, fontsize=8, alpha=0.85):
        rect = FancyBboxPatch((x, y), w, h, boxstyle="round,pad=0.08",
                              facecolor=color, edgecolor='#333333', linewidth=0.8, alpha=alpha)
        ax.add_patch(rect)
        tc = 'white' if color in [C_BLUE, C_RED, C_PURPLE, C_GREEN] else 'black'
        ax.text(x + w/2, y + h/2, label, ha='center', va='center', fontsize=fontsize, color=tc)

    def arrow(x1, y1, x2, y2, color='#555555', lw=1.2):
        ax.annotate('', xy=(x2, y2), xytext=(x1, y1),
                    arrowprops=dict(arrowstyle='->', color=color, lw=lw))

    ax.text(5, 6.7, "Hybrid Physics-AI Simulation Architecture", ha='center', fontsize=12, fontweight='bold')

    # ProcessSystem execution flow
    ax.text(0.5, 6.1, "ProcessSystem.run() execution loop:", fontsize=9, fontweight='bold', color=C_GRAY)

    # Equipment chain (top row)
    box(0.3, 4.8, 1.5, 0.9, "Feed\nStream", C_BLUE, 7.5)
    box(2.2, 4.8, 1.5, 0.9, "Separator\n(Physics)", C_GREEN, 7.5)
    box(4.1, 4.8, 1.8, 0.9, "Compressor\n(Surrogate)", C_ORANGE, 7.5)
    box(6.3, 4.8, 1.5, 0.9, "Cooler\n(Physics)", C_GREEN, 7.5)
    box(8.2, 4.8, 1.5, 0.9, "Export\nStream", C_BLUE, 7.5)

    arrow(1.8, 5.25, 2.2, 5.25, C_GRAY)
    arrow(3.7, 5.25, 4.1, 5.25, C_GRAY)
    arrow(5.9, 5.25, 6.3, 5.25, C_GRAY)
    arrow(7.8, 5.25, 8.2, 5.25, C_GRAY)

    # Surrogate model detail (middle)
    ax.text(5, 4.15, "SurrogateEquipment Detail", fontsize=9, fontweight='bold', color=C_ORANGE)

    box(0.3, 2.4, 1.6, 1.3, "Input\nExtraction\n(T, P, q, z)", C_LIGHT_ORANGE, 7)
    box(2.3, 2.4, 1.6, 1.3, "Normalize\n(stored\nscaling)", C_LIGHT_ORANGE, 7)
    box(4.3, 2.4, 1.6, 1.3, "ONNX\nModel\nForward", C_ORANGE, 7.5)
    box(6.3, 2.4, 1.6, 1.3, "Conservation\nCorrection\n(mass/energy)", C_RED, 7)
    box(8.3, 2.4, 1.4, 1.3, "Output to\nOutlet\nStream", C_LIGHT_ORANGE, 7)

    arrow(1.9, 3.05, 2.3, 3.05, C_ORANGE)
    arrow(3.9, 3.05, 4.3, 3.05, C_ORANGE)
    arrow(5.9, 3.05, 6.3, 3.05, C_ORANGE)
    arrow(7.9, 3.05, 8.3, 3.05, C_ORANGE)

    # Connection from process chain to detail
    arrow(5.0, 4.8, 5.0, 4.15)
    ax.plot([4.1, 5.9], [4.8, 4.8], color=C_ORANGE, linestyle='--', alpha=0.5)

    # SurrogateModelRegistry (bottom)
    box(2.5, 0.5, 5.0, 1.3, "SurrogateModelRegistry\n\n• Model loading (ONNX)\n"
        "• Domain validation\n• Physics fallback\n• Training data collection",
        C_PURPLE, 7.5)

    arrow(5.0, 2.4, 5.0, 1.8, C_PURPLE)
    arrow(7.1, 2.4, 6.5, 1.8, C_PURPLE)

    # Legend
    legend_elements = [
        mpatches.Patch(facecolor=C_GREEN, label='Physics-based equipment', alpha=0.85),
        mpatches.Patch(facecolor=C_ORANGE, label='Surrogate equipment', alpha=0.85),
        mpatches.Patch(facecolor=C_RED, label='Conservation enforcement', alpha=0.85),
        mpatches.Patch(facecolor=C_PURPLE, label='Model registry', alpha=0.85),
    ]
    ax.legend(handles=legend_elements, loc='lower left', frameon=True, fontsize=7.5,
              bbox_to_anchor=(0.0, 0.0))

    fig.savefig(FIGURES_DIR / "fig7_surrogate_architecture.png", dpi=DPI, bbox_inches='tight')
    plt.close(fig)
    print("  fig7_surrogate_architecture.png")


# =========================================================================
# Figure 8: Educational Integration
# =========================================================================
def fig8_educational_integration():
    """Industry-education feedback loop."""
    fig, ax = plt.subplots(figsize=(8, 5.5))
    ax.set_xlim(0, 8)
    ax.set_ylim(0, 6)
    ax.axis('off')

    def box(x, y, w, h, label, color, fontsize=8):
        rect = FancyBboxPatch((x, y), w, h, boxstyle="round,pad=0.1",
                              facecolor=color, edgecolor='#333333', linewidth=1.0, alpha=0.85)
        ax.add_patch(rect)
        tc = 'white' if color in [C_BLUE, C_PURPLE, C_GREEN] else 'black'
        ax.text(x + w/2, y + h/2, label, ha='center', va='center', fontsize=fontsize, color=tc)

    def curved_arrow(x1, y1, x2, y2, color, label="", connectionstyle="arc3,rad=0.3"):
        ax.annotate('', xy=(x2, y2), xytext=(x1, y1),
                    arrowprops=dict(arrowstyle='->', color=color, lw=1.5,
                                    connectionstyle=connectionstyle))
        if label:
            mx, my = (x1+x2)/2, (y1+y2)/2
            ax.text(mx, my, label, ha='center', fontsize=7, color=color, style='italic')

    ax.text(4, 5.6, "AI-PRO Industry-Education Feedback Loop", ha='center', fontsize=12, fontweight='bold')

    # Three main nodes
    box(0.3, 3.3, 2.4, 1.4, "Industry\n\nEquinor\nSolution Seeker", C_BLUE, 8)
    box(2.8, 0.5, 2.4, 1.4, "Open-Source\nSimulation\nPlatform", C_GREEN, 8)
    box(5.3, 3.3, 2.4, 1.4, "Education\n\nNTNU\n2 PhD + M.Sc.", C_PURPLE, 8)

    # Circular arrows
    curved_arrow(2.7, 3.8, 5.3, 3.8, C_ORANGE, "Field data &\noperational\nworkflows")
    curved_arrow(5.5, 3.3, 4.2, 1.9, C_PURPLE, "AI/ML\nresearch")
    curved_arrow(3.8, 0.5, 1.5, 3.3, C_GREEN, "Simulation\nresults")

    curved_arrow(6.5, 3.3, 6.5, 1.9, C_BLUE, "", "arc3,rad=-0.5")
    ax.text(7.3, 2.6, "Trained\nmodels", fontsize=7, color=C_BLUE, style='italic')

    curved_arrow(1.5, 3.3, 3.0, 1.9, C_BLUE, "", "arc3,rad=0.3")
    ax.text(1.2, 2.5, "Data\nrequirements", fontsize=7, color=C_BLUE, style='italic')

    curved_arrow(5.2, 1.5, 5.5, 3.3, C_GREEN, "", "arc3,rad=-0.3")
    ax.text(6.0, 2.4, "Teaching\ntools", fontsize=7, color=C_GREEN, style='italic')

    fig.savefig(FIGURES_DIR / "fig8_educational_integration.png", dpi=DPI, bbox_inches='tight')
    plt.close(fig)
    print("  fig8_educational_integration.png")


# =========================================================================
# Figure 9: Flash Scaling
# =========================================================================
def fig9_flash_scaling(data):
    """Flash execution time vs number of components."""
    flash = data['flash']

    # Component counts and warm-JVM times
    points = [
        (5, flash['TPflash_lean_5comp']),
        (9, flash['TPflash_natgas_9comp']),
        (13, flash['TPflash_ogw_13comp']),
    ]

    n_comps = [p[0] for p in points]
    means = [np.mean(p[1]['raw_ms'][1:]) for p in points]  # warm JVM
    stds = [np.std(p[1]['raw_ms'][1:]) for p in points]

    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(10, 4))

    # Left: Scaling with component count
    ax1.errorbar(n_comps, means, yerr=stds, fmt='o-', color=C_BLUE, lw=2,
                 markersize=8, capsize=5, label='Measured (warm JVM)')

    # Quadratic fit
    nc_fit = np.linspace(3, 16, 50)
    coeffs = np.polyfit(n_comps, means, 2)
    ax1.plot(nc_fit, np.polyval(coeffs, nc_fit), '--', color=C_RED, lw=1.5,
             label=f'Quadratic fit: {coeffs[0]:.3f}$n_c^2$ + ...')

    ax1.set_xlabel("Number of Components ($n_c$)", fontsize=10)
    ax1.set_ylabel("Flash Execution Time (ms)", fontsize=10)
    ax1.set_title("(a) Flash Time vs. System Complexity", fontsize=10, fontweight='bold')
    ax1.legend(fontsize=8)
    ax1.set_xlim(3, 16)
    ax1.set_ylim(0, max(means) * 1.5)

    # Right: Pressure sweep (if available)
    if 'TPflash_pressure_sweep' in flash:
        p_sweep = flash['TPflash_pressure_sweep']
        pressures = [p['P_bara'] for p in p_sweep]
        p_means = [p['mean_ms'] for p in p_sweep]
        p_stds = [p['std_ms'] for p in p_sweep]

        ax2.errorbar(pressures, p_means, yerr=p_stds, fmt='s-', color=C_GREEN,
                     lw=2, markersize=6, capsize=4)
        ax2.set_xlabel("Pressure (bara)", fontsize=10)
        ax2.set_ylabel("Flash Execution Time (ms)", fontsize=10)
        ax2.set_title("(b) Flash Time vs. Pressure (5-comp)", fontsize=10, fontweight='bold')
        ax2.set_ylim(0, max(p_means) * 1.8)
    else:
        # Raw timing violin for 5-comp flash
        ax2.violinplot([flash['TPflash_lean_5comp']['raw_ms'][1:]],
                       positions=[1], showmeans=True, showmedians=True)
        ax2.set_title("(b) Flash Time Distribution (5-comp)", fontsize=10, fontweight='bold')

    plt.tight_layout()
    fig.savefig(FIGURES_DIR / "fig9_flash_scaling.png", dpi=DPI, bbox_inches='tight')
    plt.close(fig)
    print("  fig9_flash_scaling.png")


# =========================================================================
# Figure 10: Gap Analysis Matrix
# =========================================================================
def fig10_gap_matrix():
    """Requirements vs capabilities gap matrix."""
    requirements = [
        "R1.1 Throughput", "R1.2 Parametric var.", "R1.3 Output complete.",
        "R1.4 Phys. consistency", "R1.5 Failure modes",
        "R2.1 State observation", "R2.2 Action application",
        "R2.3 Reward computation", "R2.4 Episode mgmt.",
        "R2.5 Constraint encoding",
        "R3.1 Surrogate I/F", "R3.2 Conservation",
        "R3.3 Selective fidelity", "R3.4 Model interchange",
        "R4.1 Interface contract", "R4.2 Iterative coupling",
        "R4.3 State serialization", "R4.4 Programmatic API",
        "R4.5 Structured results",
        "R5.1 Tool discovery", "R5.2 NL bridge",
        "R5.3 Examples", "R5.4 Validation",
        "R5.5 Standardized protocol"
    ]

    # Current capability on 0-3 scale
    current = [2, 3, 3, 3, 3,  # R1
               2, 2, 2, 2, 3,  # R2
               0, 3, 0, 0,     # R3
               3, 3, 3, 3, 3,  # R4
               3, 3, 3, 3, 3]  # R5

    # Enhancement needed (3 - current, capped)
    needed = [max(0, 3 - c) for c in current]

    # Category boundaries
    cat_bounds = [0, 5, 10, 14, 19, 24]
    cat_labels = ["R1: Surrogate\nTraining", "R2: RL\nEnvironment",
                  "R3: Hybrid\nSimulation", "R4: Digital\nTwin", "R5: Agentic\nAI"]
    cat_colors = [C_BLUE, C_PURPLE, C_RED, C_GREEN, C_ORANGE]

    fig, ax = plt.subplots(figsize=(10, 7))

    y = np.arange(len(requirements))

    # Stacked bar: current (green) + needed (red)
    ax.barh(y, current, height=0.6, color=C_GREEN, alpha=0.7, label='Current capability',
            edgecolor='white', linewidth=0.5)
    ax.barh(y, needed, left=current, height=0.6, color=C_RED, alpha=0.5,
            label='Enhancement needed', edgecolor='white', linewidth=0.5)

    ax.set_yticks(y)
    ax.set_yticklabels(requirements, fontsize=7.5)
    ax.set_xlabel("Capability Level (0 = Missing, 3 = Full)", fontsize=10)
    ax.set_title("Requirements vs. Current Capabilities", fontsize=11, fontweight='bold')
    ax.set_xlim(0, 3.5)
    ax.set_xticks([0, 1, 2, 3])
    ax.legend(fontsize=8, loc='lower right')
    ax.invert_yaxis()

    # Category separators and labels
    for i in range(len(cat_bounds) - 1):
        start, end = cat_bounds[i], cat_bounds[i+1]
        mid = (start + end) / 2 - 0.5

        # Horizontal separator
        if i > 0:
            ax.axhline(y=start - 0.5, color='#999999', linewidth=0.5, linestyle='-')

        # Category background band
        for j in range(start, end):
            ax.axhspan(j - 0.5, j + 0.5, color=cat_colors[i], alpha=0.05)

    plt.tight_layout()
    fig.savefig(FIGURES_DIR / "fig10_gap_matrix.png", dpi=DPI, bbox_inches='tight')
    plt.close(fig)
    print("  fig10_gap_matrix.png")


# =========================================================================
# Main
# =========================================================================
def main():
    print("Generating figures for AI-PRO roadmap paper...")
    print(f"Output directory: {FIGURES_DIR}")
    print()

    data = load_benchmark_data()

    print("Creating figures:")
    fig1_framework_architecture()
    fig2_capability_heatmap()
    fig3_execution_times(data)
    fig4_training_data_rate(data)
    fig5_rl_step_distribution(data)
    fig6_gap_roadmap()
    fig7_surrogate_architecture()
    fig8_educational_integration()
    fig9_flash_scaling(data)
    fig10_gap_matrix()

    print()
    print(f"All 10 figures saved to {FIGURES_DIR}/")
    print("Done.")


if __name__ == "__main__":
    main()
