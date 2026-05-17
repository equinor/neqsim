"""
Comprehensive Benchmark Suite: Fully Implicit vs Nested CPA-EOS
================================================================

Reproduces the fluid systems from Igben et al. (2026) and extends
to industrial gas-liquid and gas-liquid-liquid equilibrium cases.

Fluid categories:
  A. Paper systems (Igben et al. 2026):
     1. Pure water (4 association sites)
     2. Water-methanol binary (various compositions)
     3. Water-ethanol-acetic acid ternary
  B. Extended industrial systems:
     4. Natural gas + water (VLE with association)
     5. Natural gas + water + MEG (VLE/LLE, flow assurance)
     6. Gas condensate + water (VLE, pipeline)
     7. Oil + gas + water + MEG (VLLE, separator conditions)
     8. CO2 + water (CCS applications)

Output: JSON results, CSV data, matplotlib figures.
"""

import json
import os
import sys
import time
import pathlib
import numpy as np

# NeqSim setup via devtools (uses local target/ build)
PROJECT_ROOT = pathlib.Path(__file__).resolve().parents[4]  # neqsim3 root
sys.path.insert(0, str(PROJECT_ROOT / "devtools"))
from neqsim_dev_setup import neqsim_init, neqsim_classes
import jpype

ns = neqsim_init(project_root=str(PROJECT_ROOT), recompile=False, verbose=True)
ns = neqsim_classes(ns)

# Load CPA-specific classes via JClass (including new implicit class)
SystemSrkCPAstatoil = jpype.JClass("neqsim.thermo.system.SystemSrkCPAstatoil")
SystemSrkCPAstatoilFullyImplicit = jpype.JClass(
    "neqsim.thermo.system.SystemSrkCPAstatoilFullyImplicit"
)
ThermodynamicOperations = jpype.JClass(
    "neqsim.thermodynamicoperations.ThermodynamicOperations"
)

# Output paths
SCRIPT_DIR = pathlib.Path(__file__).resolve().parent
PAPER_DIR = SCRIPT_DIR.parent
FIGURES_DIR = PAPER_DIR / "figures"
TABLES_DIR = PAPER_DIR / "tables"
RESULTS_DIR = PAPER_DIR / "results"
for d in [FIGURES_DIR, TABLES_DIR, RESULTS_DIR]:
    d.mkdir(exist_ok=True)

# -------------------------------------------------------------------
# Configuration
# -------------------------------------------------------------------
N_REPS = 3           # repetitions per (T,P) point for timing
N_WARMUP = 2         # JIT warmup repetitions (not timed)
T_RANGE_K = np.linspace(273.15 + 5, 273.15 + 200, 10)     # 5C to 200C
P_RANGE_BAR = np.linspace(1.0, 500.0, 10)                   # 1 to 500 bar
T_SUBSET = np.linspace(273.15 + 10, 273.15 + 150, 6)       # for heavy cases
P_SUBSET = np.linspace(1.0, 300.0, 6)

# -------------------------------------------------------------------
# Fluid system definitions
# -------------------------------------------------------------------

FLUID_SYSTEMS = {
    # -- Paper systems (Igben et al.) --
    "A1_pure_water": {
        "label": "Pure water",
        "category": "paper",
        "n_sites": 4,
        "phase_type": "single-phase liquid",
        "components": {"water": 1.0},
        "T_range": T_RANGE_K,
        "P_range": P_RANGE_BAR,
        "multiPhaseCheck": False,
    },
    "A2_water_methanol_50": {
        "label": "Water-Methanol (50/50 mol%)",
        "category": "paper",
        "n_sites": 6,
        "phase_type": "VLE binary",
        "components": {"water": 0.5, "methanol": 0.5},
        "T_range": T_RANGE_K,
        "P_range": P_RANGE_BAR,
        "multiPhaseCheck": False,
    },
    "A2_water_methanol_80": {
        "label": "Water-Methanol (80/20 mol%)",
        "category": "paper",
        "n_sites": 6,
        "phase_type": "VLE binary",
        "components": {"water": 0.8, "methanol": 0.2},
        "T_range": T_RANGE_K,
        "P_range": P_RANGE_BAR,
        "multiPhaseCheck": False,
    },
    "A3_water_ethanol_aceticacid": {
        "label": "Water-Ethanol-Acetic Acid (33/33/34)",
        "category": "paper",
        "n_sites": 8,
        "phase_type": "VLE ternary",
        "components": {"water": 0.33, "ethanol": 0.33, "acetic acid": 0.34},
        "T_range": T_RANGE_K,
        "P_range": P_RANGE_BAR,
        "multiPhaseCheck": False,
    },
    # -- Extended industrial systems --
    "B1_natgas_water": {
        "label": "Natural gas + Water",
        "category": "industrial",
        "n_sites": 4,
        "phase_type": "VLE (gas + aqueous)",
        "components": {
            "methane": 0.85, "ethane": 0.06, "propane": 0.03,
            "n-butane": 0.01, "water": 0.05,
        },
        "T_range": T_SUBSET,
        "P_range": P_SUBSET,
        "multiPhaseCheck": True,
    },
    "B2_natgas_water_MEG": {
        "label": "Natural gas + Water + MEG",
        "category": "industrial",
        "n_sites": 8,
        "phase_type": "VLE/LLE (gas + aqueous + MEG)",
        "components": {
            "methane": 0.80, "ethane": 0.06, "propane": 0.03,
            "n-butane": 0.01, "water": 0.08, "MEG": 0.02,
        },
        "T_range": T_SUBSET,
        "P_range": P_SUBSET,
        "multiPhaseCheck": True,
    },
    "B3_gas_condensate_water": {
        "label": "Gas condensate + Water",
        "category": "industrial",
        "n_sites": 4,
        "phase_type": "VLE (gas + condensate + aqueous)",
        "components": {
            "methane": 0.70, "ethane": 0.07, "propane": 0.05,
            "n-butane": 0.03, "n-pentane": 0.02,
            "n-hexane": 0.01, "n-heptane": 0.005,
            "water": 0.065,
        },
        "T_range": T_SUBSET,
        "P_range": P_SUBSET,
        "multiPhaseCheck": True,
    },
    "B4_oil_gas_water_MEG": {
        "label": "Oil + Gas + Water + MEG",
        "category": "industrial",
        "n_sites": 8,
        "phase_type": "VLLE (gas + oil + aqueous)",
        "components": {
            "methane": 0.40, "ethane": 0.05, "propane": 0.04,
            "n-butane": 0.03, "n-pentane": 0.03, "n-hexane": 0.04,
            "n-heptane": 0.08, "n-octane": 0.05,
            "water": 0.25, "MEG": 0.03,
        },
        "T_range": T_SUBSET,
        "P_range": P_SUBSET,
        "multiPhaseCheck": True,
    },
    "B5_CO2_water": {
        "label": "CO2 + Water",
        "category": "industrial",
        "n_sites": 4,
        "phase_type": "VLE (CO2 + aqueous)",
        "components": {"CO2": 0.50, "water": 0.50},
        "T_range": T_SUBSET,
        "P_range": P_SUBSET,
        "multiPhaseCheck": True,
    },
    "B6_MEG_water": {
        "label": "MEG + Water",
        "category": "industrial",
        "n_sites": 8,
        "phase_type": "single-phase aqueous",
        "components": {"MEG": 0.4, "water": 0.6},
        "T_range": T_SUBSET,
        "P_range": P_SUBSET,
        "multiPhaseCheck": False,
    },
    "B7_natgas_water_TEG": {
        "label": "Natural gas + Water + TEG",
        "category": "industrial",
        "n_sites": 8,
        "phase_type": "VLE (gas + aqueous/TEG)",
        "components": {
            "methane": 0.80, "ethane": 0.06, "propane": 0.03,
            "n-butane": 0.01, "water": 0.05, "TEG": 0.05,
        },
        "T_range": T_SUBSET,
        "P_range": P_SUBSET,
        "multiPhaseCheck": True,
    },
}


def create_system(system_class, T, P, comp_dict, multiPhaseCheck=False):
    """Create a CPA system with given components."""
    sys = system_class(float(T), float(P))
    for name, frac in comp_dict.items():
        sys.addComponent(str(name), float(frac))
    sys.setMixingRule(10)
    if multiPhaseCheck:
        sys.setMultiPhaseCheck(True)
    return sys


def run_single_flash(system_class, T, P, comp_dict, multiPhaseCheck=False):
    """Run a TPflash and return (time_ns, n_phases, density_ph0, z_ph0)."""
    sys = create_system(system_class, T, P, comp_dict, multiPhaseCheck)
    ops = ThermodynamicOperations(sys)

    t0 = time.perf_counter_ns()
    ops.TPflash()
    elapsed = time.perf_counter_ns() - t0

    n_phases = int(sys.getNumberOfPhases())
    dens = float(sys.getPhase(0).getDensity("kg/m3"))
    z = float(sys.getPhase(0).getZ())
    return elapsed, n_phases, dens, z


def benchmark_system(fluid_id, fluid_config):
    """Run benchmark for one fluid system across T,P grid."""
    label = fluid_config["label"]
    comps = fluid_config["components"]
    T_arr = fluid_config["T_range"]
    P_arr = fluid_config["P_range"]
    mpc = fluid_config.get("multiPhaseCheck", False)

    print(f"\n{'='*60}")
    print(f"  Benchmarking: {label} ({fluid_id})")
    print(f"  Components: {len(comps)}, T points: {len(T_arr)}, P points: {len(P_arr)}")
    print(f"{'='*60}")

    results = []
    n_points = len(T_arr) * len(P_arr)
    idx = 0

    for T in T_arr:
        for P in P_arr:
            idx += 1
            if idx % 10 == 0:
                print(f"  Point {idx}/{n_points}...", end="\r")

            # Warmup
            for _ in range(N_WARMUP):
                try:
                    run_single_flash(SystemSrkCPAstatoil, T, P, comps, mpc)
                    run_single_flash(SystemSrkCPAstatoilFullyImplicit, T, P, comps, mpc)
                except Exception:
                    pass

            # Timed runs
            std_times = []
            impl_times = []
            std_ok = True
            impl_ok = True
            std_dens = std_z = impl_dens = impl_z = 0.0
            std_nph = impl_nph = 0

            for rep in range(N_REPS):
                try:
                    t, nph, dens, z = run_single_flash(
                        SystemSrkCPAstatoil, T, P, comps, mpc
                    )
                    std_times.append(t)
                    if rep == N_REPS - 1:
                        std_nph, std_dens, std_z = nph, dens, z
                except Exception:
                    std_ok = False
                    break

                try:
                    t, nph, dens, z = run_single_flash(
                        SystemSrkCPAstatoilFullyImplicit, T, P, comps, mpc
                    )
                    impl_times.append(t)
                    if rep == N_REPS - 1:
                        impl_nph, impl_dens, impl_z = nph, dens, z
                except Exception:
                    impl_ok = False
                    break

            if std_ok and impl_ok and len(std_times) > 0 and len(impl_times) > 0:
                std_median = float(np.median(std_times))
                impl_median = float(np.median(impl_times))
                ratio = impl_median / std_median if std_median > 0 else float("inf")

                dens_err = (
                    abs(std_dens - impl_dens) / max(abs(std_dens), 1e-10) * 100
                    if std_dens != 0
                    else 0.0
                )
                z_err = (
                    abs(std_z - impl_z) / max(abs(std_z), 1e-10) * 100
                    if std_z != 0
                    else 0.0
                )

                results.append({
                    "T_K": float(T),
                    "P_bar": float(P),
                    "std_time_ns": std_median,
                    "impl_time_ns": impl_median,
                    "ratio": ratio,
                    "std_nph": std_nph,
                    "impl_nph": impl_nph,
                    "dens_err_pct": dens_err,
                    "z_err_pct": z_err,
                    "phase_match": std_nph == impl_nph,
                })

    print(f"\n  Completed: {len(results)} valid points out of {n_points}")
    return results


def compute_summary(results):
    """Compute summary statistics for a benchmark run."""
    if not results:
        return {"n_points": 0, "error": "no valid points"}
    ratios = [r["ratio"] for r in results]
    dens_errs = [r["dens_err_pct"] for r in results]
    phase_matches = [r["phase_match"] for r in results]
    return {
        "n_points": len(results),
        "ratio_mean": float(np.mean(ratios)),
        "ratio_median": float(np.median(ratios)),
        "ratio_p10": float(np.percentile(ratios, 10)),
        "ratio_p90": float(np.percentile(ratios, 90)),
        "speedup_mean": float(1.0 / np.mean(ratios)),
        "speedup_median": float(1.0 / np.median(ratios)),
        "max_dens_err_pct": float(np.max(dens_errs)),
        "mean_dens_err_pct": float(np.mean(dens_errs)),
        "phase_match_pct": float(np.mean(phase_matches) * 100),
    }


def main():
    all_results = {}
    all_summaries = {}

    for fluid_id, fluid_config in FLUID_SYSTEMS.items():
        try:
            results = benchmark_system(fluid_id, fluid_config)
            summary = compute_summary(results)
            all_results[fluid_id] = results
            all_summaries[fluid_id] = {
                "label": fluid_config["label"],
                "category": fluid_config["category"],
                "n_sites": fluid_config["n_sites"],
                "phase_type": fluid_config["phase_type"],
                "n_components": len(fluid_config["components"]),
                **summary,
            }
            print(f"  Summary: ratio={summary.get('ratio_median', 'N/A'):.3f}, "
                  f"speedup={summary.get('speedup_median', 'N/A'):.2f}x, "
                  f"max_dens_err={summary.get('max_dens_err_pct', 'N/A'):.6f}%")
        except Exception as e:
            print(f"  ERROR in {fluid_id}: {e}")
            all_summaries[fluid_id] = {"label": fluid_config["label"], "error": str(e)}

    # Save results
    with open(str(RESULTS_DIR / "benchmark_raw.json"), "w") as f:
        json.dump(all_results, f, indent=2)

    with open(str(RESULTS_DIR / "benchmark_summary.json"), "w") as f:
        json.dump(all_summaries, f, indent=2)

    # Print summary table
    print("\n" + "=" * 90)
    print("  SUMMARY: Fully Implicit CPA vs Standard Nested")
    print("=" * 90)
    print(f"{'System':<45} {'Nc':>3} {'ns':>3} {'Ratio':>7} {'Speedup':>8} "
          f"{'MaxErr%':>8} {'PhMatch':>8}")
    print("-" * 90)
    for fid, s in all_summaries.items():
        if "error" in s:
            print(f"{s['label']:<45} {'ERROR':>40}")
        else:
            print(f"{s['label']:<45} {s['n_components']:>3} {s['n_sites']:>3} "
                  f"{s.get('ratio_median', 0):>7.3f} {s.get('speedup_median', 0):>7.2f}x "
                  f"{s.get('max_dens_err_pct', 0):>8.5f} {s.get('phase_match_pct', 0):>7.1f}%")
    print("=" * 90)

    print(f"\nResults saved to {RESULTS_DIR}")


if __name__ == "__main__":
    main()
