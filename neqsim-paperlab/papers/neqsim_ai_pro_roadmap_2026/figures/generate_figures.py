"""
Generate all figures for the NeqSim AI-PRO Roadmap paper.
Uses real benchmark data from run_benchmarks.py.
"""
import json
import os
import numpy as np
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch, FancyArrowPatch
import matplotlib.patches as mpatches

PAPER_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FIGURES_DIR = os.path.join(PAPER_DIR, "figures")
RESULTS_DIR = os.path.join(PAPER_DIR, "results")
os.makedirs(FIGURES_DIR, exist_ok=True)

# Load benchmark results
with open(os.path.join(RESULTS_DIR, "benchmark_results.json")) as f:
    bench = json.load(f)

# Style settings
plt.rcParams.update({
    'font.size': 11,
    'font.family': 'serif',
    'axes.labelsize': 12,
    'axes.titlesize': 13,
    'xtick.labelsize': 10,
    'ytick.labelsize': 10,
    'legend.fontsize': 9,
    'figure.dpi': 150,
    'savefig.dpi': 300,
    'savefig.bbox': 'tight',
})

COLORS = {
    'reservoir': '#2196F3',
    'wellbore': '#FF9800',
    'process': '#4CAF50',
    'ai': '#9C27B0',
    'rl': '#E91E63',
    'uq': '#00BCD4',
    'existing': '#4CAF50',
    'partial': '#FF9800',
    'gap': '#F44336',
    'primary': '#1565C0',
    'secondary': '#43A047',
    'tertiary': '#E65100',
}


# ========================================================================
# Figure 1: Framework Architecture Diagram
# ========================================================================
def fig1_framework_architecture():
    fig, ax = plt.subplots(1, 1, figsize=(12, 7))
    ax.set_xlim(0, 12)
    ax.set_ylim(0, 7)
    ax.axis('off')
    ax.set_title('AI-PRO Framework Architecture with Open-Source Simulation Backbone',
                 fontsize=14, fontweight='bold', pad=15)

    # Domain boxes (top row)
    domains = [
        (0.5, 5.0, 3.0, 1.5, 'Reservoir Domain\n(OPM Flow)', COLORS['reservoir']),
        (4.5, 5.0, 3.0, 1.5, 'Wellbore/Pipeline\n(NeqSim PipeBeggsAndBrills)', COLORS['wellbore']),
        (8.5, 5.0, 3.0, 1.5, 'Process Facilities\n(NeqSim ProcessSystem)', COLORS['process']),
    ]
    for x, y, w, h, label, color in domains:
        rect = FancyBboxPatch((x, y), w, h, boxstyle="round,pad=0.1",
                              facecolor=color, alpha=0.25, edgecolor=color, linewidth=2)
        ax.add_patch(rect)
        ax.text(x + w/2, y + h/2, label, ha='center', va='center', fontsize=10, fontweight='bold')

    # Interface arrows between domains
    for x_start, x_end in [(3.5, 4.5), (7.5, 8.5)]:
        ax.annotate('', xy=(x_end, 5.75), xytext=(x_start, 5.75),
                    arrowprops=dict(arrowstyle='<->', color='#333', lw=2))
    ax.text(4.0, 5.2, 'P, T, Q,\ncomp', ha='center', va='top', fontsize=8, color='#555')
    ax.text(8.0, 5.2, 'P, T, Q,\ncomp', ha='center', va='top', fontsize=8, color='#555')

    # AI Surrogate layer (middle)
    ai_rect = FancyBboxPatch((0.5, 3.0), 11.0, 1.5, boxstyle="round,pad=0.1",
                             facecolor=COLORS['ai'], alpha=0.15, edgecolor=COLORS['ai'],
                             linewidth=2, linestyle='--')
    ax.add_patch(ai_rect)
    ax.text(6.0, 3.75, 'AI Surrogate Layer: PINN Surrogates + Conservation Enforcement + Hybrid Fallback',
            ha='center', va='center', fontsize=11, fontweight='bold', color=COLORS['ai'])

    # Arrows from domains to AI layer
    for x_center in [2.0, 6.0, 10.0]:
        ax.annotate('', xy=(x_center, 4.5), xytext=(x_center, 5.0),
                    arrowprops=dict(arrowstyle='<->', color=COLORS['ai'], lw=1.5, ls='--'))

    # RL Agent (bottom left)
    rl_rect = FancyBboxPatch((0.5, 0.5), 4.5, 2.0, boxstyle="round,pad=0.1",
                             facecolor=COLORS['rl'], alpha=0.2, edgecolor=COLORS['rl'], linewidth=2)
    ax.add_patch(rl_rect)
    ax.text(2.75, 1.5, 'RL Optimization Agent\n(GymEnvironment API)\nState → Action → Reward',
            ha='center', va='center', fontsize=10, fontweight='bold')

    # UQ Module (bottom right)
    uq_rect = FancyBboxPatch((5.5, 0.5), 3.5, 2.0, boxstyle="round,pad=0.1",
                             facecolor=COLORS['uq'], alpha=0.2, edgecolor=COLORS['uq'], linewidth=2)
    ax.add_patch(uq_rect)
    ax.text(7.25, 1.5, 'Uncertainty Quantification\nBayesian / Ensemble / Conformal\nP10/P50/P90 Bounds',
            ha='center', va='center', fontsize=10, fontweight='bold')

    # MCP / LLM Access (bottom far right)
    mcp_rect = FancyBboxPatch((9.5, 0.5), 2.0, 2.0, boxstyle="round,pad=0.1",
                              facecolor='#FFD54F', alpha=0.3, edgecolor='#F9A825', linewidth=2)
    ax.add_patch(mcp_rect)
    ax.text(10.5, 1.5, 'MCP Server\nLLM Access\n(Natural Lang.)',
            ha='center', va='center', fontsize=9, fontweight='bold')

    # Arrows from AI layer down
    ax.annotate('', xy=(2.75, 2.5), xytext=(2.75, 3.0),
                arrowprops=dict(arrowstyle='<->', color='#333', lw=1.5))
    ax.annotate('', xy=(7.25, 2.5), xytext=(7.25, 3.0),
                arrowprops=dict(arrowstyle='<->', color='#333', lw=1.5))
    ax.annotate('', xy=(10.5, 2.5), xytext=(10.5, 3.0),
                arrowprops=dict(arrowstyle='<->', color='#333', lw=1.5))

    plt.savefig(os.path.join(FIGURES_DIR, 'fig1_framework_architecture.png'), dpi=300, bbox_inches='tight')
    plt.close()
    print("  Fig 1: Framework architecture")


# ========================================================================
# Figure 2: Capability Coverage Heatmap
# ========================================================================
def fig2_capability_heatmap():
    categories = [
        'Equations of State',
        'Flash Calculations',
        'Transport Properties',
        'Separators',
        'Compressors',
        'Heat Exchangers',
        'Pipelines (multiphase)',
        'Valves',
        'Distillation',
        'Reservoir Coupling',
        'RL Environment API',
        'Surrogate Registry',
        'Training Data Gen.',
        'MCP / LLM Access',
        'Bottleneck Analysis',
        'Dynamic Simulation',
        'Multi-area ProcessModel',
    ]

    # Coverage levels: 3=full, 2=partial, 1=minimal, 0=gap
    coverage = [3, 3, 3, 3, 3, 3, 3, 3, 3, 2, 3, 2, 2, 3, 2, 2, 3]

    labels = ['Gap', 'Minimal', 'Partial', 'Full']
    cmap_colors = ['#F44336', '#FF9800', '#FFC107', '#4CAF50']
    cmap = matplotlib.colors.ListedColormap(cmap_colors)
    bounds = [-0.5, 0.5, 1.5, 2.5, 3.5]
    norm = matplotlib.colors.BoundaryNorm(bounds, cmap.N)

    fig, ax = plt.subplots(1, 1, figsize=(8, 9))

    # Create heatmap data (single column)
    data = np.array(coverage).reshape(-1, 1)
    im = ax.imshow(data, cmap=cmap, norm=norm, aspect=0.3)

    ax.set_yticks(range(len(categories)))
    ax.set_yticklabels(categories, fontsize=10)
    ax.set_xticks([0])
    ax.set_xticklabels(['Coverage Level'], fontsize=11)
    ax.set_title('NeqSim Capability Coverage for AI-PRO Requirements', fontsize=13, fontweight='bold', pad=15)

    # Add text labels
    for i, val in enumerate(coverage):
        ax.text(0, i, labels[val], ha='center', va='center', fontsize=10,
                fontweight='bold', color='white' if val < 2 else 'black')

    # Legend
    patches = [mpatches.Patch(color=c, label=l) for c, l in zip(cmap_colors, labels)]
    ax.legend(handles=patches, loc='lower right', fontsize=9)

    plt.savefig(os.path.join(FIGURES_DIR, 'fig2_capability_heatmap.png'), dpi=300, bbox_inches='tight')
    plt.close()
    print("  Fig 2: Capability heatmap")


# ========================================================================
# Figure 3: Execution Time Bar Chart (from benchmarks)
# ========================================================================
def fig3_execution_times():
    configs = [
        'TPflash\n(5 comp)',
        'TPflash\n(9 comp)',
        'TPflash\n(13 comp)',
        'Single\nSeparator',
        'Two-stage\nSeparation',
        'Compression\nTrain (3-stage)',
        'Full Process\n(Sep+Comp)',
        'RL Step\n(re-run)',
    ]

    means = [
        bench['flash']['TPflash_lean_5comp']['mean_ms'],
        bench['flash']['TPflash_natgas_9comp']['mean_ms'],
        bench['flash']['TPflash_ogw_13comp']['mean_ms'],
        bench['single_separator']['mean_ms'],
        bench['two_stage_separation']['mean_ms'],
        bench['compression_train']['mean_ms'],
        bench['full_process']['mean_ms'],
        bench['rl_step']['mean_ms'],
    ]

    stds = [
        bench['flash']['TPflash_lean_5comp']['std_ms'],
        bench['flash']['TPflash_natgas_9comp']['std_ms'],
        bench['flash']['TPflash_ogw_13comp']['std_ms'],
        bench['single_separator']['std_ms'],
        bench['two_stage_separation']['std_ms'],
        bench['compression_train']['std_ms'],
        bench['full_process']['std_ms'],
        bench['rl_step']['std_ms'],
    ]

    colors = ['#1565C0', '#1565C0', '#1565C0', '#43A047', '#43A047', '#43A047', '#E65100', '#9C27B0']

    fig, ax = plt.subplots(1, 1, figsize=(11, 5.5))
    x = np.arange(len(configs))
    bars = ax.bar(x, means, yerr=stds, capsize=4, color=colors, alpha=0.85, edgecolor='#333', linewidth=0.5)

    ax.set_xticks(x)
    ax.set_xticklabels(configs, fontsize=9)
    ax.set_ylabel('Execution Time (ms)', fontsize=12)
    ax.set_title('NeqSim Computational Performance: Execution Time by Configuration', fontsize=13, fontweight='bold')
    ax.set_yscale('log')
    ax.set_ylim(0.5, 100)
    ax.grid(axis='y', alpha=0.3, linestyle='--')

    # Add value labels
    for bar, mean in zip(bars, means):
        ax.text(bar.get_x() + bar.get_width()/2., bar.get_height() * 1.15,
                f'{mean:.1f}', ha='center', va='bottom', fontsize=9, fontweight='bold')

    # Legend for categories
    patches = [
        mpatches.Patch(color='#1565C0', label='Flash Calculations'),
        mpatches.Patch(color='#43A047', label='Process Equipment'),
        mpatches.Patch(color='#E65100', label='Full Flowsheet'),
        mpatches.Patch(color='#9C27B0', label='RL Step'),
    ]
    ax.legend(handles=patches, loc='upper left', fontsize=9)

    plt.savefig(os.path.join(FIGURES_DIR, 'fig3_execution_times.png'), dpi=300, bbox_inches='tight')
    plt.close()
    print("  Fig 3: Execution times")


# ========================================================================
# Figure 4: Training Data Generation Rate
# ========================================================================
def fig4_training_data_rate():
    rate = bench['training_data']['samples_per_second']
    ms_per = bench['training_data']['ms_per_sample']

    # Project training dataset size at this rate
    dataset_sizes = [1000, 5000, 10000, 50000, 100000, 500000, 1000000]
    times_sec = [n / rate for n in dataset_sizes]
    times_min = [t / 60 for t in times_sec]
    times_hr = [t / 3600 for t in times_sec]

    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(12, 5))

    # Left: Dataset generation time
    ax1.plot(dataset_sizes, times_min, 'o-', color=COLORS['primary'], linewidth=2, markersize=6)
    ax1.set_xscale('log')
    ax1.set_yscale('log')
    ax1.set_xlabel('Training Dataset Size (samples)', fontsize=12)
    ax1.set_ylabel('Generation Time (minutes)', fontsize=12)
    ax1.set_title('Surrogate Training Data Generation Time', fontsize=13, fontweight='bold')
    ax1.grid(True, alpha=0.3, linestyle='--')

    # Add reference lines
    ax1.axhline(y=60, color='red', linestyle='--', alpha=0.5, label='1 hour')
    ax1.axhline(y=480, color='darkred', linestyle='--', alpha=0.5, label='8 hours')
    ax1.legend(fontsize=9)

    # Annotate key points
    for n, t in zip(dataset_sizes, times_min):
        if n in [10000, 100000, 1000000]:
            if t < 60:
                label = f'{t:.1f} min'
            elif t < 480:
                label = f'{t/60:.1f} hr'
            else:
                label = f'{t/60:.1f} hr'
            ax1.annotate(label, (n, t), textcoords="offset points",
                        xytext=(10, 10), fontsize=8, fontweight='bold')

    # Right: Throughput comparison
    operations = ['TPflash\n(5 comp)', 'TPflash\n(9 comp)', 'TPflash\n(13 comp)',
                  'Full Process\nEvaluation']
    rates = [
        1000.0 / bench['flash']['TPflash_lean_5comp']['mean_ms'],
        1000.0 / bench['flash']['TPflash_natgas_9comp']['mean_ms'],
        1000.0 / bench['flash']['TPflash_ogw_13comp']['mean_ms'],
        1000.0 / bench['full_process']['mean_ms'],
    ]

    bars = ax2.barh(range(len(operations)), rates, color=[COLORS['primary']]*3 + [COLORS['tertiary']],
                    alpha=0.85, edgecolor='#333', linewidth=0.5)
    ax2.set_yticks(range(len(operations)))
    ax2.set_yticklabels(operations, fontsize=10)
    ax2.set_xlabel('Throughput (evaluations/second)', fontsize=12)
    ax2.set_title('Simulation Throughput', fontsize=13, fontweight='bold')
    ax2.grid(axis='x', alpha=0.3, linestyle='--')

    for bar, rate_val in zip(bars, rates):
        ax2.text(bar.get_width() + 5, bar.get_y() + bar.get_height()/2.,
                f'{rate_val:.0f}/s', ha='left', va='center', fontsize=10, fontweight='bold')

    plt.tight_layout()
    plt.savefig(os.path.join(FIGURES_DIR, 'fig4_training_data_rate.png'), dpi=300, bbox_inches='tight')
    plt.close()
    print("  Fig 4: Training data rate")


# ========================================================================
# Figure 5: RL Environment Step Timing Distribution
# ========================================================================
def fig5_rl_step_distribution():
    step_times = bench['rl_step']['raw_ms']

    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(12, 5))

    # Left: Histogram
    ax1.hist(step_times, bins=15, color=COLORS['rl'], alpha=0.75, edgecolor='#333', linewidth=0.5)
    ax1.axvline(np.mean(step_times), color='red', linestyle='--', linewidth=2, label=f'Mean: {np.mean(step_times):.2f} ms')
    ax1.axvline(np.median(step_times), color='blue', linestyle='--', linewidth=2, label=f'Median: {np.median(step_times):.2f} ms')
    ax1.set_xlabel('Step Execution Time (ms)', fontsize=12)
    ax1.set_ylabel('Count', fontsize=12)
    ax1.set_title('RL Step Time Distribution', fontsize=13, fontweight='bold')
    ax1.legend(fontsize=9)
    ax1.grid(axis='y', alpha=0.3, linestyle='--')

    # Right: Steps per episode vs episode length
    steps_per_sec = 1000.0 / np.mean(step_times)
    episode_lengths = [50, 100, 200, 500, 1000, 2000]
    episode_times_sec = [n / steps_per_sec for n in episode_lengths]

    episodes_per_hour = [3600 / t for t in episode_times_sec]

    ax2_twin = ax2.twinx()
    l1, = ax2.plot(episode_lengths, episode_times_sec, 'o-', color=COLORS['primary'], linewidth=2, markersize=6, label='Episode time (s)')
    l2, = ax2_twin.plot(episode_lengths, episodes_per_hour, 's--', color=COLORS['secondary'], linewidth=2, markersize=6, label='Episodes per hour')

    ax2.set_xlabel('Episode Length (steps)', fontsize=12)
    ax2.set_ylabel('Episode Duration (seconds)', fontsize=12, color=COLORS['primary'])
    ax2_twin.set_ylabel('Episodes per Hour', fontsize=12, color=COLORS['secondary'])
    ax2.set_title('RL Training Feasibility', fontsize=13, fontweight='bold')
    ax2.grid(True, alpha=0.3, linestyle='--')

    lines = [l1, l2]
    labels = [l.get_label() for l in lines]
    ax2.legend(lines, labels, fontsize=9, loc='center right')

    # Add annotation for typical RL training
    ax2.annotate('Typical DRL:\n1M steps ≈ 25 min', xy=(500, 500/steps_per_sec),
                xytext=(800, 1.5), fontsize=9, fontweight='bold',
                arrowprops=dict(arrowstyle='->', color='#333'),
                bbox=dict(boxstyle='round,pad=0.3', facecolor='#FFF9C4'))

    plt.tight_layout()
    plt.savefig(os.path.join(FIGURES_DIR, 'fig5_rl_step_distribution.png'), dpi=300, bbox_inches='tight')
    plt.close()
    print("  Fig 5: RL step distribution")


# ========================================================================
# Figure 6: Gap Analysis Roadmap
# ========================================================================
def fig6_gap_roadmap():
    fig, ax = plt.subplots(1, 1, figsize=(14, 7))
    ax.set_xlim(0, 37)
    ax.set_ylim(-0.5, 9.5)
    ax.set_xlabel('Project Month', fontsize=12)
    ax.set_title('AI-PRO Development Roadmap: NeqSim Enhancement Timeline', fontsize=14, fontweight='bold')

    # Work items
    items = [
        # (label, start_month, duration, category, y_pos)
        ('WP0: Project Management', 1, 36, '#9E9E9E', 9),
        ('WP1: Data Integration & Baselines', 1, 10, COLORS['reservoir'], 8),
        ('Equipment Utilization API', 3, 8, COLORS['process'], 7),
        ('WP2: PINN Surrogates (Reservoir)', 4, 20, COLORS['reservoir'], 6),
        ('WP2: PINN Surrogates (Process/NeqSim)', 6, 18, COLORS['process'], 5),
        ('Surrogate Registry + Hybrid Fallback', 8, 12, COLORS['ai'], 4),
        ('WP3: Digital Twin API + UQ', 14, 16, COLORS['uq'], 3),
        ('GymEnvironment Extensions', 16, 10, COLORS['rl'], 2),
        ('WP4: RL Agent Training', 20, 16, COLORS['rl'], 1),
        ('WP4: NCS Case Study & Demo', 28, 8, COLORS['tertiary'], 0),
    ]

    for label, start, dur, color, y in items:
        ax.barh(y, dur, left=start, height=0.6, color=color, alpha=0.7,
                edgecolor='#333', linewidth=0.5)
        # Place label inside if bar is long enough, else to the right
        if dur > 10:
            ax.text(start + dur/2, y, label, ha='center', va='center', fontsize=8.5, fontweight='bold')
        else:
            ax.text(start + dur + 0.5, y, label, ha='left', va='center', fontsize=8.5, fontweight='bold')

    # Milestone markers
    milestones = [
        (10, 'D1.3\nBaseline', '#333'),
        (18, 'D2.1\nRes. Surrogate', COLORS['reservoir']),
        (24, 'D2.3\nProc. Surrogate', COLORS['process']),
        (30, 'D3.1\nDT Library', COLORS['uq']),
        (36, 'D4.4\nOpen-source\nRelease', COLORS['rl']),
    ]
    for month, label, color in milestones:
        ax.axvline(month, color=color, linestyle=':', alpha=0.5, linewidth=1)
        ax.text(month, -0.3, label, ha='center', va='top', fontsize=7, color=color, fontweight='bold')

    ax.set_yticks([])
    ax.set_xticks([0, 6, 12, 18, 24, 30, 36])
    ax.grid(axis='x', alpha=0.3, linestyle='--')

    plt.savefig(os.path.join(FIGURES_DIR, 'fig6_gap_roadmap.png'), dpi=300, bbox_inches='tight')
    plt.close()
    print("  Fig 6: Gap roadmap")


# ========================================================================
# Figure 7: Surrogate Integration Architecture
# ========================================================================
def fig7_surrogate_architecture():
    fig, ax = plt.subplots(1, 1, figsize=(11, 6))
    ax.set_xlim(0, 11)
    ax.set_ylim(0, 6)
    ax.axis('off')
    ax.set_title('Hybrid Physics-AI Simulation Architecture', fontsize=14, fontweight='bold', pad=15)

    # ProcessSystem box
    ps_rect = FancyBboxPatch((0.3, 3.5), 10.4, 2.2, boxstyle="round,pad=0.15",
                             facecolor='#E3F2FD', edgecolor=COLORS['primary'], linewidth=2)
    ax.add_patch(ps_rect)
    ax.text(5.5, 5.5, 'ProcessSystem.run()', ha='center', va='center',
            fontsize=12, fontweight='bold', color=COLORS['primary'])

    # Equipment boxes inside ProcessSystem
    equips = [
        (0.7, 3.7, 2.3, 1.3, 'Stream\n(Fluid + EOS)', '#BBDEFB'),
        (3.3, 3.7, 2.3, 1.3, 'Separator\n(Physics)', '#C8E6C9'),
        (5.9, 3.7, 2.3, 1.3, 'Compressor\n(Surrogate?)', '#FFF9C4'),
        (8.5, 3.7, 2.3, 1.3, 'HeatExchanger\n(Physics)', '#C8E6C9'),
    ]
    for x, y, w, h, label, color in equips:
        rect = FancyBboxPatch((x, y), w, h, boxstyle="round,pad=0.08",
                              facecolor=color, edgecolor='#666', linewidth=1)
        ax.add_patch(rect)
        ax.text(x + w/2, y + h/2, label, ha='center', va='center', fontsize=9)

    # Arrows between equipment
    for x1, x2 in [(3.0, 3.3), (5.6, 5.9), (8.2, 8.5)]:
        ax.annotate('', xy=(x2, 4.35), xytext=(x1, 4.35),
                    arrowprops=dict(arrowstyle='->', color='#333', lw=1.5))

    # SurrogateModelRegistry box (below)
    sr_rect = FancyBboxPatch((2.5, 1.0), 6.0, 2.0, boxstyle="round,pad=0.12",
                             facecolor='#F3E5F5', edgecolor=COLORS['ai'], linewidth=2, linestyle='--')
    ax.add_patch(sr_rect)
    ax.text(5.5, 2.6, 'SurrogateModelRegistry', ha='center', va='center',
            fontsize=11, fontweight='bold', color=COLORS['ai'])

    # Sub-elements in registry
    sub_items = [
        (3.0, 1.2, 'Neural Net\nPredictor'),
        (5.0, 1.2, 'Physics\nValidator'),
        (7.0, 1.2, 'Fallback to\nPhysics Model'),
    ]
    for x, y, label in sub_items:
        rect = FancyBboxPatch((x, y), 1.6, 0.9, boxstyle="round,pad=0.05",
                              facecolor='white', edgecolor='#999', linewidth=1)
        ax.add_patch(rect)
        ax.text(x + 0.8, y + 0.45, label, ha='center', va='center', fontsize=8)

    # Arrow from compressor to registry
    ax.annotate('predict()', xy=(5.5, 3.0), xytext=(7.0, 3.7),
                arrowprops=dict(arrowstyle='->', color=COLORS['ai'], lw=1.5, ls='--'),
                fontsize=9, color=COLORS['ai'], fontweight='bold')

    # Training data flow
    ax.annotate('TrainingDataCollector', xy=(2.0, 2.0), xytext=(0.3, 1.5),
                arrowprops=dict(arrowstyle='->', color=COLORS['secondary'], lw=1.5),
                fontsize=9, color=COLORS['secondary'], fontweight='bold')

    # Legend
    legend_items = [
        ('#C8E6C9', 'Physics (rigorous)'),
        ('#FFF9C4', 'Surrogate-eligible'),
        ('#F3E5F5', 'AI infrastructure'),
    ]
    for i, (color, label) in enumerate(legend_items):
        rect = FancyBboxPatch((0.5, 0.1 + i * 0.3), 0.3, 0.2, boxstyle="round,pad=0.02",
                              facecolor=color, edgecolor='#666', linewidth=0.5)
        ax.add_patch(rect)
        ax.text(1.0, 0.2 + i * 0.3, label, ha='left', va='center', fontsize=8)

    plt.savefig(os.path.join(FIGURES_DIR, 'fig7_surrogate_architecture.png'), dpi=300, bbox_inches='tight')
    plt.close()
    print("  Fig 7: Surrogate architecture")


# ========================================================================
# Figure 8: Educational Integration Diagram
# ========================================================================
def fig8_educational_integration():
    fig, ax = plt.subplots(1, 1, figsize=(10, 7))
    ax.set_xlim(0, 10)
    ax.set_ylim(0, 7)
    ax.axis('off')
    ax.set_title('AI-PRO: Industry-Education Feedback Loop', fontsize=14, fontweight='bold', pad=15)

    # Industry side (left)
    ind_rect = FancyBboxPatch((0.3, 3.5), 3.5, 3.0, boxstyle="round,pad=0.15",
                              facecolor='#E3F2FD', edgecolor=COLORS['primary'], linewidth=2.5)
    ax.add_patch(ind_rect)
    ax.text(2.05, 6.0, 'INDUSTRY', ha='center', va='center', fontsize=13, fontweight='bold', color=COLORS['primary'])
    ax.text(2.05, 5.3, 'Equinor + Solution Seeker', ha='center', va='center', fontsize=9)
    items = ['NCS Field Data', 'Operational Workflows', 'Benchmarking Platform', 'Co-supervision']
    for i, item in enumerate(items):
        ax.text(2.05, 4.7 - i*0.4, f'• {item}', ha='center', va='center', fontsize=8.5)

    # Education side (right)
    edu_rect = FancyBboxPatch((6.2, 3.5), 3.5, 3.0, boxstyle="round,pad=0.15",
                              facecolor='#E8F5E9', edgecolor=COLORS['secondary'], linewidth=2.5)
    ax.add_patch(edu_rect)
    ax.text(7.95, 6.0, 'EDUCATION', ha='center', va='center', fontsize=13, fontweight='bold', color=COLORS['secondary'])
    ax.text(7.95, 5.3, 'NTNU (IGV + EPT)', ha='center', va='center', fontsize=9)
    edu_items = ['AI for PSE Course', 'MSc/PhD Thesis', 'Open-source Library', 'Teaching Modules']
    for i, item in enumerate(edu_items):
        ax.text(7.95, 4.7 - i*0.4, f'• {item}', ha='center', va='center', fontsize=8.5)

    # Central circle: Research
    from matplotlib.patches import Circle
    circle = Circle((5.0, 5.0), 1.0, facecolor='#FFF3E0', edgecolor=COLORS['tertiary'], linewidth=2.5)
    ax.add_patch(circle)
    ax.text(5.0, 5.2, 'AI-PRO', ha='center', va='center', fontsize=12, fontweight='bold', color=COLORS['tertiary'])
    ax.text(5.0, 4.8, 'Research', ha='center', va='center', fontsize=10, color=COLORS['tertiary'])

    # Arrows: Industry -> Research -> Education
    ax.annotate('', xy=(4.0, 5.0), xytext=(3.8, 5.0),
                arrowprops=dict(arrowstyle='->', color=COLORS['primary'], lw=2.5))
    ax.annotate('', xy=(6.2, 5.0), xytext=(6.0, 5.0),
                arrowprops=dict(arrowstyle='->', color=COLORS['secondary'], lw=2.5))

    # Bottom: Outputs
    out_rect = FancyBboxPatch((1.5, 0.5), 7.0, 2.5, boxstyle="round,pad=0.15",
                              facecolor='#FAFAFA', edgecolor='#9E9E9E', linewidth=1.5)
    ax.add_patch(out_rect)
    ax.text(5.0, 2.6, 'OUTPUTS & IMPACT', ha='center', va='center', fontsize=12, fontweight='bold', color='#555')

    outputs = [
        '6 journal papers + 2 conference papers',
        'Open-source AI optimization library (research + teaching)',
        '2 trained PhDs at engineering-AI interface',
        'New course module: AI for Production Systems Engineering',
        'External funding applications (RCN, Horizon Europe)',
    ]
    for i, item in enumerate(outputs):
        ax.text(5.0, 2.1 - i*0.35, f'{i+1}. {item}', ha='center', va='center', fontsize=8.5)

    # Arrow from research circle to outputs
    ax.annotate('', xy=(5.0, 3.0), xytext=(5.0, 4.0),
                arrowprops=dict(arrowstyle='->', color='#555', lw=2))

    plt.savefig(os.path.join(FIGURES_DIR, 'fig8_educational_integration.png'), dpi=300, bbox_inches='tight')
    plt.close()
    print("  Fig 8: Educational integration")


# ========================================================================
# Figure 9: Flash Timing vs Number of Components
# ========================================================================
def fig9_flash_scaling():
    n_comps = [5, 9, 13]
    flash_means = [
        bench['flash']['TPflash_lean_5comp']['mean_ms'],
        bench['flash']['TPflash_natgas_9comp']['mean_ms'],
        bench['flash']['TPflash_ogw_13comp']['mean_ms'],
    ]
    flash_stds = [
        bench['flash']['TPflash_lean_5comp']['std_ms'],
        bench['flash']['TPflash_natgas_9comp']['std_ms'],
        bench['flash']['TPflash_ogw_13comp']['std_ms'],
    ]

    fig, ax = plt.subplots(1, 1, figsize=(7, 5))
    ax.errorbar(n_comps, flash_means, yerr=flash_stds, fmt='o-', color=COLORS['primary'],
                linewidth=2, markersize=8, capsize=5, capthick=2)

    # Fit quadratic trend
    coeffs = np.polyfit(n_comps, flash_means, 2)
    x_fit = np.linspace(3, 20, 50)
    y_fit = np.polyval(coeffs, x_fit)
    ax.plot(x_fit, y_fit, '--', color=COLORS['primary'], alpha=0.4, label=f'Quadratic fit')

    ax.set_xlabel('Number of Components', fontsize=12)
    ax.set_ylabel('TPflash Execution Time (ms)', fontsize=12)
    ax.set_title('Flash Calculation Scaling with System Complexity', fontsize=13, fontweight='bold')
    ax.grid(True, alpha=0.3, linestyle='--')
    ax.legend(fontsize=9)

    # Annotate projected values
    for n, m in zip(n_comps, flash_means):
        ax.annotate(f'{m:.1f} ms', (n, m), textcoords="offset points",
                   xytext=(10, 10), fontsize=10, fontweight='bold')

    # Add projected 20-component time
    projected_20 = np.polyval(coeffs, 20)
    ax.plot(20, projected_20, 'x', color='red', markersize=10, markeredgewidth=2)
    ax.annotate(f'Projected: {projected_20:.0f} ms\n(20 comp)', (20, projected_20),
               textcoords="offset points", xytext=(-60, 10), fontsize=9,
               fontweight='bold', color='red',
               arrowprops=dict(arrowstyle='->', color='red'))

    plt.savefig(os.path.join(FIGURES_DIR, 'fig9_flash_scaling.png'), dpi=300, bbox_inches='tight')
    plt.close()
    print("  Fig 9: Flash scaling")


# ========================================================================
# Figure 10: Requirements-Capability Gap Matrix
# ========================================================================
def fig10_gap_matrix():
    requirements = [
        'Steady-state EOS flash',
        'Transport properties',
        'Process equipment models',
        'Multiphase pipe flow',
        'Equipment utilization metrics',
        'Dynamic simulation',
        'RL Gymnasium API',
        'Surrogate model registry',
        'Physics constraint validation',
        'Training data collector',
        'Reservoir coupling (OPM)',
        'PINN integration interface',
        'Conservation enforcement layer',
        'Real-time data connector',
        'Multi-fidelity switching',
    ]

    # Current status: 3=ready, 2=exists needs enhancement, 1=partial/planned, 0=missing
    current = [3, 3, 3, 3, 2, 2, 3, 2, 2, 2, 1, 1, 1, 1, 1]

    # AI-PRO enhancement needed: 0=none, 1=minor, 2=moderate, 3=major
    enhancement = [0, 0, 0, 0, 2, 2, 1, 2, 1, 1, 3, 3, 3, 2, 2]

    fig, ax = plt.subplots(1, 1, figsize=(10, 8))

    y = np.arange(len(requirements))
    width = 0.35

    bars1 = ax.barh(y + width/2, current, width, color=COLORS['secondary'], alpha=0.8,
                    label='Current Status', edgecolor='#333', linewidth=0.5)
    bars2 = ax.barh(y - width/2, enhancement, width, color=COLORS['gap'], alpha=0.7,
                    label='Enhancement Needed', edgecolor='#333', linewidth=0.5)

    ax.set_yticks(y)
    ax.set_yticklabels(requirements, fontsize=9)
    ax.set_xlabel('Level (0=Missing, 1=Partial, 2=Moderate, 3=Full)', fontsize=11)
    ax.set_title('Requirements vs. Current Capability: Gap Analysis', fontsize=13, fontweight='bold')
    ax.legend(fontsize=10, loc='lower right')
    ax.grid(axis='x', alpha=0.3, linestyle='--')
    ax.set_xlim(0, 3.5)
    ax.invert_yaxis()

    plt.savefig(os.path.join(FIGURES_DIR, 'fig10_gap_matrix.png'), dpi=300, bbox_inches='tight')
    plt.close()
    print("  Fig 10: Gap matrix")


# ========================================================================
# Run all figures
# ========================================================================
if __name__ == "__main__":
    print("Generating figures for AI-PRO Roadmap paper...")
    print("=" * 50)
    fig1_framework_architecture()
    fig2_capability_heatmap()
    fig3_execution_times()
    fig4_training_data_rate()
    fig5_rl_step_distribution()
    fig6_gap_roadmap()
    fig7_surrogate_architecture()
    fig8_educational_integration()
    fig9_flash_scaling()
    fig10_gap_matrix()
    print("=" * 50)
    print(f"All figures saved to {FIGURES_DIR}")
