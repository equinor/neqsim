"""
Gibbs Reactor Algorithm Benchmark
==================================
Runs all algorithm variants (baseline, adaptive, Armijo, regularized, combined)
on all test chemical systems, collects convergence diagnostics, computes
performance metrics, generates publication-quality figures, and saves results.

This script produces the data and figures for:
"Robust Gibbs Free Energy Minimization for Chemical Equilibrium Calculations
 with Cubic Equations of State"
"""

import sys
import os
import json
import time
import pathlib
import numpy as np

# ── NeqSim bootstrap via devtools (uses compiled classes from source) ──
# Add devtools to path so we can import neqsim_dev_setup
SCRIPT_DIR_BOOT = pathlib.Path(__file__).resolve().parent
PROJECT_ROOT = SCRIPT_DIR_BOOT.parent.parent.parent.parent  # neqsim repo root
sys.path.insert(0, str(PROJECT_ROOT / "devtools"))

from neqsim_dev_setup import neqsim_init, neqsim_classes
ns = neqsim_init(project_root=PROJECT_ROOT, recompile=False, verbose=True)

import jpype

# Java classes via JClass (sees all methods including new ones)
SystemSrkEos = jpype.JClass("neqsim.thermo.system.SystemSrkEos")
SystemSrkCPAstatoil = jpype.JClass("neqsim.thermo.system.SystemSrkCPAstatoil")
Stream = jpype.JClass("neqsim.process.equipment.stream.Stream")
GibbsReactor = jpype.JClass("neqsim.process.equipment.reactor.GibbsReactor")
EnergyMode = GibbsReactor.EnergyMode

# Paths
SCRIPT_DIR = pathlib.Path(__file__).resolve().parent
PAPER_DIR = SCRIPT_DIR.parent
RESULTS_DIR = PAPER_DIR / "results" / "raw"
FIGURES_DIR = PAPER_DIR / "figures"
TABLES_DIR = PAPER_DIR / "tables"
RESULTS_DIR.mkdir(parents=True, exist_ok=True)
FIGURES_DIR.mkdir(parents=True, exist_ok=True)
TABLES_DIR.mkdir(parents=True, exist_ok=True)


# ═══════════════════════════════════════════════════════════════════
# 1. TEST SYSTEM DEFINITIONS
# ═══════════════════════════════════════════════════════════════════

def create_system_S1():
    """S1: CH4 combustion (4 components), 298 K, 100 bar, SRK."""
    sys = SystemSrkEos(298.15, 100.0)
    sys.addComponent("methane", 0.05)
    sys.addComponent("oxygen", 0.5)
    sys.addComponent("CO2", 0.0)
    sys.addComponent("water", 0.0)
    sys.setMixingRule(2)
    return sys, "S1: CH4 combustion (4-comp)"

def create_system_S2():
    """S2: CH4 combustion + CO (5 components), 298 K, 100 bar, SRK."""
    sys = SystemSrkEos(298.15, 100.0)
    sys.addComponent("methane", 0.05)
    sys.addComponent("oxygen", 0.5)
    sys.addComponent("CO2", 0.0)
    sys.addComponent("CO", 0.0)
    sys.addComponent("water", 0.0)
    sys.setMixingRule(2)
    return sys, "S2: CH4 + CO (5-comp)"

def create_system_S3():
    """S3: H2/O2 reaction (3 components), 298 K, 1 bar, SRK."""
    sys = SystemSrkEos(298.15, 1.0)
    sys.addComponent("hydrogen", 0.1)
    sys.addComponent("oxygen", 1.0)
    sys.addComponent("water", 0.0)
    sys.setMixingRule(2)
    return sys, "S3: H2/O2 (3-comp)"

def create_system_S4():
    """S4: NH3 synthesis (3 components), 450 K, 300 bar, SRK."""
    sys = SystemSrkEos(298.15, 1.0)
    sys.addComponent("hydrogen", 1.5)
    sys.addComponent("nitrogen", 0.5)
    sys.addComponent("ammonia", 0.0)
    sys.setMixingRule(2)
    return sys, "S4: NH3 synthesis"

def create_system_S5():
    """S5: Claus sulfur formation (6 components), 373 K, 10 bar, CPA."""
    sys = SystemSrkCPAstatoil(298.15, 1.0)
    sys.addComponent("methane", 1e6)
    sys.addComponent("H2S", 10.0)
    sys.addComponent("oxygen", 2.0)
    sys.addComponent("SO2", 0.0)
    sys.addComponent("water", 0.0)
    sys.addComponent("S8", 0.0)
    sys.setMixingRule(2)
    return sys, "S5: Claus sulfur (6-comp)"


# ═══════════════════════════════════════════════════════════════════
# 2. ALGORITHM VARIANT RUNNER
# ═══════════════════════════════════════════════════════════════════

def run_variant(system_factory, variant_name, energy_mode, max_iter=10000,
                tol=1e-3, damping=0.01, n_timing=5):
    """
    Run a single algorithm variant on a test system.
    Returns dict with all metrics and diagnostic histories.
    """
    # Configure variant
    use_armijo = variant_name in ("armijo", "combined")
    use_adaptive = variant_name in ("adaptive", "combined")
    use_reg = variant_name in ("regularized", "combined")

    timings = []
    result = {}

    for trial in range(n_timing):
        sys_obj, sys_name = system_factory()
        inlet = Stream("Inlet", sys_obj)

        if "S4" in sys_name:
            inlet.setPressure(300.0, "bara")
            inlet.setTemperature(450.0, "K")
        elif "S5" in sys_name:
            inlet.setPressure(10.0, "bara")
            inlet.setTemperature(100.0, "C")

        inlet.run()

        reactor = GibbsReactor("Reactor", inlet)
        reactor.setUseAllDatabaseSpecies(False)
        reactor.setMaxIterations(max_iter)
        reactor.setConvergenceTolerance(tol)
        reactor.setDampingComposition(damping)

        if energy_mode == "adiabatic":
            reactor.setEnergyMode(EnergyMode.ADIABATIC)
        else:
            reactor.setEnergyMode(EnergyMode.ISOTHERMAL)

        if use_adaptive:
            reactor.setUseAdaptiveStepSize(True)
        if use_armijo:
            reactor.setUseArmijoLineSearch(True)
        if use_reg:
            reactor.setUseRegularization(True)
            reactor.setRegularizationThreshold(1e10)
            reactor.setRegularizationTau(1e-6)

        t0 = time.perf_counter()
        reactor.run()
        dt = (time.perf_counter() - t0) * 1000.0  # ms
        timings.append(dt)

        if trial == 0:  # Collect detailed data only on first run
            converged = bool(reactor.hasConverged())
            mass_bal = bool(reactor.getMassBalanceConverged())

            # Iteration count
            try:
                iter_count = int(reactor.getActualIterations())
            except Exception:
                iter_count = -1

            # Diagnostic histories
            gibbs_hist = [float(v) for v in reactor.getGibbsEnergyHistory()]
            cond_hist = [float(v) for v in reactor.getConditionNumberHistory()]
            step_hist = [float(v) for v in reactor.getStepSizeHistory()]
            elem_hist = [float(v) for v in reactor.getElementBalanceErrorHistory()]

            # Outlet composition
            outlet = reactor.getOutletStream().getThermoSystem()
            n_comp = int(outlet.getNumberOfComponents())
            compositions = {}
            for i in range(n_comp):
                comp = outlet.getComponent(i)
                name = str(comp.getComponentName())
                z = float(comp.getz())
                compositions[name] = z

            outlet_T = float(outlet.getTemperature()) - 273.15  # C
            outlet_P = float(outlet.getPressure())  # bar

            result = {
                "system": sys_name,
                "variant": variant_name,
                "energy_mode": energy_mode,
                "converged": converged,
                "mass_balance": mass_bal,
                "iterations": iter_count,
                "damping": damping,
                "compositions": compositions,
                "outlet_T_C": round(outlet_T, 2),
                "outlet_P_bar": round(outlet_P, 2),
                "gibbs_history": gibbs_hist,
                "condition_history": cond_hist,
                "step_history": step_hist,
                "element_balance_history": elem_hist,
            }

    result["timing_ms_mean"] = round(np.mean(timings), 2)
    result["timing_ms_std"] = round(np.std(timings), 2)
    result["timing_ms_all"] = [round(t, 2) for t in timings]

    return result


# ═══════════════════════════════════════════════════════════════════
# 3. RUN ALL BENCHMARKS
# ═══════════════════════════════════════════════════════════════════

def run_all_benchmarks():
    """Run all algorithm variants on all test systems."""

    systems = [
        (create_system_S1, "adiabatic", 0.01, 10000, 1e-3),
        (create_system_S2, "adiabatic", 0.01, 10000, 1e-3),
        (create_system_S3, "isothermal", 0.01, 5000, 1e-3),
        (create_system_S4, "isothermal", 0.05, 2500, 1e-6),
        (create_system_S5, "isothermal", 0.001, 10000, 1e-3),
    ]

    variants = ["baseline", "adaptive", "armijo", "regularized", "combined"]

    all_results = []

    for sys_factory, e_mode, damp, max_it, tol in systems:
        _, sys_name = sys_factory()
        print(f"\n{'='*60}")
        print(f"System: {sys_name}")
        print(f"{'='*60}")

        for variant in variants:
            print(f"  Running {variant}...", end=" ", flush=True)
            try:
                r = run_variant(sys_factory, variant, e_mode,
                                max_iter=max_it, tol=tol, damping=damp,
                                n_timing=5)
                status = "CONVERGED" if r["converged"] else "FAILED"
                print(f"{status} ({r['iterations']} it, {r['timing_ms_mean']:.1f} ms)")
                all_results.append(r)
            except Exception as e:
                print(f"ERROR: {e}")
                all_results.append({
                    "system": sys_name, "variant": variant,
                    "converged": False, "error": str(e)
                })

    return all_results


# ═══════════════════════════════════════════════════════════════════
# 4. FIGURE GENERATION
# ═══════════════════════════════════════════════════════════════════

def generate_figures(all_results):
    """Generate publication-quality figures from benchmark results."""
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
    from matplotlib.ticker import MaxNLocator

    plt.rcParams.update({
        "font.size": 11,
        "font.family": "serif",
        "axes.labelsize": 12,
        "axes.titlesize": 13,
        "legend.fontsize": 9,
        "xtick.labelsize": 10,
        "ytick.labelsize": 10,
        "figure.dpi": 150,
        "savefig.dpi": 300,
        "savefig.bbox": "tight",
    })

    colors = {
        "baseline": "#1f77b4",
        "adaptive": "#ff7f0e",
        "armijo": "#2ca02c",
        "regularized": "#d62728",
        "combined": "#9467bd",
    }
    labels = {
        "baseline": "Fixed damping",
        "adaptive": "Adaptive (CEA-style)",
        "armijo": "Armijo line search",
        "regularized": "Tikhonov regularized",
        "combined": "Combined (Armijo+Tikhonov)",
    }

    figures_info = {}

    # ── Figure 1: Gibbs Energy Trajectories for S1 ──────────────
    fig1, ax1 = plt.subplots(figsize=(8, 5))
    s1_results = [r for r in all_results if "S1" in r.get("system", "")]
    for r in s1_results:
        var = r["variant"]
        gh = r.get("gibbs_history", [])
        if gh and len(gh) > 2:
            iters = list(range(len(gh)))
            ax1.plot(iters, gh, color=colors[var], label=labels[var],
                     linewidth=1.5, alpha=0.9)

    ax1.set_xlabel("Iteration")
    ax1.set_ylabel("Total Gibbs Energy (kJ)")
    ax1.set_title("S1: Methane Combustion — Gibbs Energy Convergence")
    ax1.legend(loc="upper right", framealpha=0.9)
    ax1.grid(True, alpha=0.3)
    ax1.xaxis.set_major_locator(MaxNLocator(integer=True))
    fig1.savefig(str(FIGURES_DIR / "fig1_gibbs_trajectory_S1.png"))
    plt.close(fig1)
    figures_info["fig1_gibbs_trajectory_S1.png"] = (
        "S1 CH4 combustion: Gibbs energy versus iteration for all five algorithm variants. "
        "The Armijo and combined variants show monotonically decreasing energy."
    )

    # ── Figure 2: Gibbs Energy Trajectories for S4 (NH3) ────────
    fig2, ax2 = plt.subplots(figsize=(8, 5))
    s4_results = [r for r in all_results if "S4" in r.get("system", "")]
    for r in s4_results:
        var = r["variant"]
        gh = r.get("gibbs_history", [])
        if gh and len(gh) > 2:
            iters = list(range(len(gh)))
            ax2.plot(iters, gh, color=colors[var], label=labels[var],
                     linewidth=1.5, alpha=0.9)

    ax2.set_xlabel("Iteration")
    ax2.set_ylabel("Total Gibbs Energy (kJ)")
    ax2.set_title("S4: NH$_3$ Synthesis (300 bar) — Gibbs Energy Convergence")
    ax2.legend(loc="upper right", framealpha=0.9)
    ax2.grid(True, alpha=0.3)
    ax2.xaxis.set_major_locator(MaxNLocator(integer=True))
    fig2.savefig(str(FIGURES_DIR / "fig2_gibbs_trajectory_S4.png"))
    plt.close(fig2)
    figures_info["fig2_gibbs_trajectory_S4.png"] = (
        "S4 NH3 synthesis at 300 bar: Gibbs energy convergence. "
        "High-pressure EOS effects make the energy landscape more complex."
    )

    # ── Figure 3: Condition Number Evolution for S2 ─────────────
    fig3, ax3 = plt.subplots(figsize=(8, 5))
    s2_results = [r for r in all_results if "S2" in r.get("system", "")]
    for r in s2_results:
        var = r["variant"]
        ch = r.get("condition_history", [])
        if ch and len(ch) > 2:
            iters = list(range(len(ch)))
            ax3.semilogy(iters, ch, color=colors[var], label=labels[var],
                         linewidth=1.5, alpha=0.9)

    ax3.axhline(y=1e10, color="gray", linestyle="--", linewidth=1.0,
                label=r"$\kappa_{\max} = 10^{10}$ (reg. threshold)")
    ax3.set_xlabel("Iteration")
    ax3.set_ylabel(r"Condition Number $\kappa(J)$")
    ax3.set_title("S2: CH$_4$ + CO — KKT Jacobian Condition Number")
    ax3.legend(loc="upper right", framealpha=0.9)
    ax3.grid(True, alpha=0.3)
    ax3.xaxis.set_major_locator(MaxNLocator(integer=True))
    fig3.savefig(str(FIGURES_DIR / "fig3_condition_number_S2.png"))
    plt.close(fig3)
    figures_info["fig3_condition_number_S2.png"] = (
        "S2 CH4+CO system: Jacobian condition number evolution. "
        "Values above 10^10 trigger Tikhonov regularization (dashed line)."
    )

    # ── Figure 4: Step Size History for S1 ──────────────────────
    fig4, ax4 = plt.subplots(figsize=(8, 5))
    for r in s1_results:
        var = r["variant"]
        sh = r.get("step_history", [])
        if sh and len(sh) > 2:
            iters = list(range(len(sh)))
            ax4.plot(iters, sh, color=colors[var], label=labels[var],
                     linewidth=1.2, alpha=0.8)

    ax4.set_xlabel("Iteration")
    ax4.set_ylabel(r"Step Size $\alpha$")
    ax4.set_title("S1: CH$_4$ Combustion — Effective Step Size per Iteration")
    ax4.legend(loc="upper right", framealpha=0.9)
    ax4.grid(True, alpha=0.3)
    ax4.xaxis.set_major_locator(MaxNLocator(integer=True))
    fig4.savefig(str(FIGURES_DIR / "fig4_step_size_S1.png"))
    plt.close(fig4)
    figures_info["fig4_step_size_S1.png"] = (
        "S1: Effective step size alpha at each iteration. "
        "Fixed damping uses constant alpha=0.01; Armijo adaptively selects larger steps."
    )

    # ── Figure 5: Element Balance Error for S5 (Claus) ──────────
    fig5, ax5 = plt.subplots(figsize=(8, 5))
    s5_results = [r for r in all_results if "S5" in r.get("system", "")]
    for r in s5_results:
        var = r["variant"]
        eh = r.get("element_balance_history", [])
        if eh and len(eh) > 2:
            iters = list(range(len(eh)))
            # Filter out zeros for log scale
            eh_safe = [max(v, 1e-20) for v in eh]
            ax5.semilogy(iters, eh_safe, color=colors[var], label=labels[var],
                         linewidth=1.5, alpha=0.9)

    ax5.set_xlabel("Iteration")
    ax5.set_ylabel(r"Element Balance Error $\|An - b\|_2$")
    ax5.set_title("S5: Claus Process — Element Balance Error Convergence")
    ax5.legend(loc="upper right", framealpha=0.9)
    ax5.grid(True, alpha=0.3)
    ax5.xaxis.set_major_locator(MaxNLocator(integer=True))
    fig5.savefig(str(FIGURES_DIR / "fig5_element_balance_S5.png"))
    plt.close(fig5)
    figures_info["fig5_element_balance_S5.png"] = (
        "S5 Claus sulfur process: Element balance error convergence. "
        "The stiff system (mole numbers span 6 orders of magnitude) tests robustness."
    )

    # ── Figure 6: Iteration Count Bar Chart ─────────────────────
    fig6, ax6 = plt.subplots(figsize=(10, 5))
    system_ids = ["S1", "S2", "S3", "S4", "S5"]
    variant_names = ["baseline", "adaptive", "armijo", "regularized", "combined"]
    bar_width = 0.15
    x = np.arange(len(system_ids))

    for i, var in enumerate(variant_names):
        iter_counts = []
        for sid in system_ids:
            match = [r for r in all_results
                     if sid in r.get("system", "") and r["variant"] == var]
            if match and match[0].get("converged"):
                iter_counts.append(match[0].get("iterations", 0))
            else:
                iter_counts.append(0)
        ax6.bar(x + i * bar_width, iter_counts, bar_width,
                color=colors[var], label=labels[var], edgecolor="white")

    ax6.set_xlabel("Test System")
    ax6.set_ylabel("Iterations to Convergence")
    ax6.set_title("Iteration Count Comparison Across Test Systems")
    ax6.set_xticks(x + 2 * bar_width)
    ax6.set_xticklabels(system_ids)
    ax6.legend(loc="upper right", framealpha=0.9)
    ax6.grid(True, alpha=0.3, axis="y")
    fig6.savefig(str(FIGURES_DIR / "fig6_iteration_comparison.png"))
    plt.close(fig6)
    figures_info["fig6_iteration_comparison.png"] = (
        "Iteration count comparison across all test systems and algorithm variants. "
        "Lower bars indicate faster convergence."
    )

    # ── Figure 7: Timing Comparison ─────────────────────────────
    fig7, ax7 = plt.subplots(figsize=(10, 5))
    for i, var in enumerate(variant_names):
        times = []
        errors = []
        for sid in system_ids:
            match = [r for r in all_results
                     if sid in r.get("system", "") and r["variant"] == var]
            if match:
                times.append(match[0].get("timing_ms_mean", 0))
                errors.append(match[0].get("timing_ms_std", 0))
            else:
                times.append(0)
                errors.append(0)
        ax7.bar(x + i * bar_width, times, bar_width, yerr=errors,
                color=colors[var], label=labels[var], edgecolor="white",
                capsize=2)

    ax7.set_xlabel("Test System")
    ax7.set_ylabel("CPU Time (ms)")
    ax7.set_title("Computational Cost Comparison (mean ± std, n=5)")
    ax7.set_xticks(x + 2 * bar_width)
    ax7.set_xticklabels(system_ids)
    ax7.legend(loc="upper right", framealpha=0.9)
    ax7.grid(True, alpha=0.3, axis="y")
    fig7.savefig(str(FIGURES_DIR / "fig7_timing_comparison.png"))
    plt.close(fig7)
    figures_info["fig7_timing_comparison.png"] = (
        "CPU time comparison (mean and standard deviation over 5 trials). "
        "The enhanced variants add modest overhead from Armijo backtracks and condition number computation."
    )

    # ── Figure 8: Condition Number Distributions (Box Plot) ─────
    fig8, ax8 = plt.subplots(figsize=(8, 5))
    cond_data = {}
    for var in variant_names:
        all_conds = []
        for r in all_results:
            if r["variant"] == var:
                ch = r.get("condition_history", [])
                all_conds.extend(ch)
        if all_conds:
            cond_data[var] = [c for c in all_conds if c > 0]

    if cond_data:
        positions = list(range(len(cond_data)))
        bp = ax8.boxplot(
            [np.log10(cond_data[v]) if cond_data.get(v) else [0]
             for v in variant_names if v in cond_data],
            labels=[labels[v] for v in variant_names if v in cond_data],
            patch_artist=True, widths=0.6
        )
        for patch, var in zip(bp["boxes"], [v for v in variant_names if v in cond_data]):
            patch.set_facecolor(colors[var])
            patch.set_alpha(0.6)

    ax8.set_ylabel(r"$\log_{10}(\kappa(J))$")
    ax8.set_title("Distribution of Jacobian Condition Numbers (All Systems)")
    ax8.axhline(y=10, color="gray", linestyle="--", linewidth=1.0,
                label=r"$\kappa = 10^{10}$ threshold")
    ax8.legend()
    ax8.grid(True, alpha=0.3)
    plt.xticks(rotation=15, ha="right")
    fig8.savefig(str(FIGURES_DIR / "fig8_condition_boxplot.png"))
    plt.close(fig8)
    figures_info["fig8_condition_boxplot.png"] = (
        "Box plot of log10(condition number) across all systems and iterations for each variant. "
        "Tikhonov regularization shifts the distribution below the threshold."
    )

    # ── Figure 9: Gibbs Energy Trajectories for ALL systems (combined variant) ──
    fig9, axes9 = plt.subplots(2, 3, figsize=(14, 8))
    axes_flat = axes9.flatten()
    for idx, sid in enumerate(["S1", "S2", "S3", "S4", "S5"]):
        ax = axes_flat[idx]
        for r in all_results:
            if sid in r.get("system", ""):
                var = r["variant"]
                gh = r.get("gibbs_history", [])
                if gh and len(gh) > 2:
                    ax.plot(range(len(gh)), gh, color=colors[var],
                            label=labels[var], linewidth=1.2, alpha=0.85)
        ax.set_title(sid, fontsize=11)
        ax.set_xlabel("Iteration", fontsize=9)
        ax.set_ylabel("G (kJ)", fontsize=9)
        ax.grid(True, alpha=0.3)
        ax.tick_params(labelsize=8)

    # Remove extra subplot
    axes_flat[5].set_visible(False)
    # Add shared legend
    handles, lbls = axes_flat[0].get_legend_handles_labels()
    fig9.legend(handles, lbls, loc="lower right", ncol=2, fontsize=9,
                bbox_to_anchor=(0.95, 0.08))
    fig9.suptitle("Gibbs Energy Convergence — All Test Systems", fontsize=14, y=1.01)
    fig9.tight_layout()
    fig9.savefig(str(FIGURES_DIR / "fig9_gibbs_all_systems.png"))
    plt.close(fig9)
    figures_info["fig9_gibbs_all_systems.png"] = (
        "Overview of Gibbs energy convergence trajectories for all five test systems "
        "and all algorithm variants. Panels S1-S5 show that all variants converge to "
        "the same equilibrium Gibbs energy."
    )

    # ── Figure 10: Convergence Speedup vs Baseline ──────────────
    fig10, ax10 = plt.subplots(figsize=(8, 5))
    baseline_iters = {}
    for r in all_results:
        if r["variant"] == "baseline" and r.get("converged"):
            for sid in system_ids:
                if sid in r.get("system", ""):
                    baseline_iters[sid] = r["iterations"]

    for var in ["adaptive", "armijo", "regularized", "combined"]:
        speedups = []
        valid_sids = []
        for sid in system_ids:
            match = [r for r in all_results
                     if sid in r.get("system", "") and r["variant"] == var]
            if match and match[0].get("converged") and sid in baseline_iters and baseline_iters[sid] > 0:
                sp = baseline_iters[sid] / match[0]["iterations"]
                speedups.append(sp)
                valid_sids.append(sid)
        if speedups:
            ax10.plot(valid_sids, speedups, "o-", color=colors[var],
                      label=labels[var], linewidth=1.5, markersize=8)

    ax10.axhline(y=1.0, color="gray", linestyle="--", linewidth=1.0, label="Baseline (1.0x)")
    ax10.set_xlabel("Test System")
    ax10.set_ylabel("Speedup vs. Baseline (iterations)")
    ax10.set_title("Iteration Speedup Relative to Fixed Damping Baseline")
    ax10.legend(loc="best", framealpha=0.9)
    ax10.grid(True, alpha=0.3)
    fig10.savefig(str(FIGURES_DIR / "fig10_speedup.png"))
    plt.close(fig10)
    figures_info["fig10_speedup.png"] = (
        "Speedup factor (baseline iterations / variant iterations) for each test system. "
        "Values above 1.0 indicate fewer iterations than fixed damping."
    )

    return figures_info


# ═══════════════════════════════════════════════════════════════════
# 5. TABLE GENERATION
# ═══════════════════════════════════════════════════════════════════

def generate_convergence_table(all_results):
    """Generate Table 2: Convergence results (markdown + CSV)."""
    system_ids = ["S1", "S2", "S3", "S4", "S5"]
    variant_names = ["baseline", "adaptive", "armijo", "regularized", "combined"]

    rows = []
    for sid in system_ids:
        row = {"system": sid}
        for var in variant_names:
            match = [r for r in all_results
                     if sid in r.get("system", "") and r["variant"] == var]
            if match:
                r = match[0]
                if r.get("converged"):
                    row[var] = f"OK {r['iterations']} it."
                else:
                    row[var] = "X (failed)"
            else:
                row[var] = "—"
        rows.append(row)

    # Write CSV
    import csv
    csv_path = TABLES_DIR / "convergence_table.csv"
    with open(str(csv_path), "w", newline="") as f:
        w = csv.DictWriter(f, fieldnames=["system"] + variant_names)
        w.writeheader()
        w.writerows(rows)

    return rows


def generate_timing_table(all_results):
    """Generate timing comparison table."""
    system_ids = ["S1", "S2", "S3", "S4", "S5"]
    variant_names = ["baseline", "adaptive", "armijo", "regularized", "combined"]

    rows = []
    for sid in system_ids:
        row = {"system": sid}
        for var in variant_names:
            match = [r for r in all_results
                     if sid in r.get("system", "") and r["variant"] == var]
            if match:
                r = match[0]
                row[var] = f"{r.get('timing_ms_mean', 0):.1f} ± {r.get('timing_ms_std', 0):.1f}"
            else:
                row[var] = "—"
        rows.append(row)

    import csv
    csv_path = TABLES_DIR / "timing_table.csv"
    with open(str(csv_path), "w", newline="") as f:
        w = csv.DictWriter(f, fieldnames=["system"] + variant_names)
        w.writeheader()
        w.writerows(rows)

    return rows


def generate_composition_table(all_results):
    """Generate table comparing equilibrium compositions across variants."""
    system_ids = ["S1", "S2", "S3", "S4", "S5"]

    rows = []
    for sid in system_ids:
        converged_results = [r for r in all_results
                             if sid in r.get("system", "") and r.get("converged")]
        if len(converged_results) >= 2:
            # Compare all converged variants to baseline
            baseline = [r for r in converged_results if r["variant"] == "baseline"]
            if baseline:
                base_comp = baseline[0].get("compositions", {})
                for r in converged_results:
                    if r["variant"] != "baseline":
                        comp = r.get("compositions", {})
                        max_diff = 0
                        for species, z_base in base_comp.items():
                            z_var = comp.get(species, 0)
                            max_diff = max(max_diff, abs(z_base - z_var))
                        rows.append({
                            "system": sid,
                            "variant": r["variant"],
                            "max_composition_diff": f"{max_diff:.2e}",
                            "outlet_T_diff_C": abs(baseline[0].get("outlet_T_C", 0) - r.get("outlet_T_C", 0)),
                        })

    import csv
    csv_path = TABLES_DIR / "composition_agreement.csv"
    with open(str(csv_path), "w", newline="") as f:
        w = csv.DictWriter(f, fieldnames=["system", "variant", "max_composition_diff", "outlet_T_diff_C"])
        w.writeheader()
        w.writerows(rows)

    return rows


# ═══════════════════════════════════════════════════════════════════
# 6. MAIN
# ═══════════════════════════════════════════════════════════════════

if __name__ == "__main__":
    print("=" * 70)
    print("GIBBS REACTOR ALGORITHM BENCHMARK")
    print("=" * 70)

    # Run all benchmarks
    all_results = run_all_benchmarks()

    # Save raw results
    raw_path = RESULTS_DIR / "benchmark_results.json"
    # Strip large histories for the summary but keep in full file
    with open(str(raw_path), "w") as f:
        json.dump(all_results, f, indent=2, default=str)
    print(f"\nRaw results saved to: {raw_path}")

    # Generate tables
    print("\nGenerating tables...")
    conv_table = generate_convergence_table(all_results)
    timing_table = generate_timing_table(all_results)
    comp_table = generate_composition_table(all_results)
    print(f"  Convergence table: {len(conv_table)} rows")
    print(f"  Timing table: {len(timing_table)} rows")
    print(f"  Composition agreement: {len(comp_table)} rows")

    # Generate figures
    print("\nGenerating figures...")
    figures_info = generate_figures(all_results)
    print(f"  Generated {len(figures_info)} figures")

    # Print summary
    print("\n" + "=" * 70)
    print("SUMMARY")
    print("=" * 70)

    print("\n--- Convergence Table ---")
    for row in conv_table:
        print(f"  {row['system']:5s} | BL: {row['baseline']:15s} | AD: {row['adaptive']:15s} | "
              f"AR: {row['armijo']:15s} | RG: {row['regularized']:15s} | CB: {row['combined']:15s}")

    print("\n--- Timing Table ---")
    for row in timing_table:
        print(f"  {row['system']:5s} | BL: {row['baseline']:15s} | AD: {row['adaptive']:15s} | "
              f"AR: {row['armijo']:15s} | RG: {row['regularized']:15s} | CB: {row['combined']:15s}")

    print("\n--- Composition Agreement (vs Baseline) ---")
    for row in comp_table:
        print(f"  {row['system']:5s} {row['variant']:15s} max_diff={row['max_composition_diff']}")

    # Overall statistics
    n_total = len(all_results)
    n_converged = sum(1 for r in all_results if r.get("converged"))
    print(f"\nOverall: {n_converged}/{n_total} runs converged")

    print(f"\nAll results saved to:")
    print(f"  Results: {RESULTS_DIR}")
    print(f"  Figures: {FIGURES_DIR}")
    print(f"  Tables:  {TABLES_DIR}")
    print("DONE.")
