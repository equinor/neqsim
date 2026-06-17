"""Quick benchmark for B7 (Natural gas + Water + TEG) system only."""
import json
import os
import sys
import time
import pathlib
import numpy as np

PROJECT_ROOT = pathlib.Path(__file__).resolve().parents[4]
sys.path.insert(0, str(PROJECT_ROOT / "devtools"))
from neqsim_dev_setup import neqsim_init, neqsim_classes
import jpype

ns = neqsim_init(project_root=str(PROJECT_ROOT), recompile=False, verbose=True)
ns = neqsim_classes(ns)

SystemSrkCPAstatoil = jpype.JClass("neqsim.thermo.system.SystemSrkCPAstatoil")
SystemSrkCPAstatoilFullyImplicit = jpype.JClass(
    "neqsim.thermo.system.SystemSrkCPAstatoilFullyImplicit"
)
ThermodynamicOperations = jpype.JClass(
    "neqsim.thermodynamicoperations.ThermodynamicOperations"
)

SCRIPT_DIR = pathlib.Path(__file__).resolve().parent
RESULTS_DIR = SCRIPT_DIR.parent / "results"

N_REPS = 3
N_WARMUP = 2
T_SUBSET = np.linspace(273.15 + 10, 273.15 + 150, 6)
P_SUBSET = np.linspace(1.0, 300.0, 6)

B7 = {
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
}


def run_single_flash(system_class, T, P, comp_dict, multiPhaseCheck=False):
    sys_obj = system_class(float(T), float(P))
    for name, frac in comp_dict.items():
        sys_obj.addComponent(str(name), float(frac))
    sys_obj.setMixingRule(10)
    if multiPhaseCheck:
        sys_obj.setMultiPhaseCheck(True)
    ops = ThermodynamicOperations(sys_obj)
    t0 = time.perf_counter_ns()
    ops.TPflash()
    elapsed = time.perf_counter_ns() - t0
    n_phases = int(sys_obj.getNumberOfPhases())
    dens = float(sys_obj.getPhase(0).getDensity("kg/m3"))
    z = float(sys_obj.getPhase(0).getZ())
    return elapsed, n_phases, dens, z


def main():
    comps = B7["components"]
    T_arr = B7["T_range"]
    P_arr = B7["P_range"]
    mpc = B7["multiPhaseCheck"]

    print("Benchmarking: Natural gas + Water + TEG (B7)")
    print(f"  Components: {len(comps)}, Grid: {len(T_arr)}x{len(P_arr)}")

    results = []
    n_points = len(T_arr) * len(P_arr)
    idx = 0

    for T in T_arr:
        for P in P_arr:
            idx += 1
            print(f"  Point {idx}/{n_points}...", end="\r")

            for _ in range(N_WARMUP):
                try:
                    run_single_flash(SystemSrkCPAstatoil, T, P, comps, mpc)
                    run_single_flash(SystemSrkCPAstatoilFullyImplicit, T, P, comps, mpc)
                except Exception:
                    pass

            std_times = []
            impl_times = []
            std_ok = impl_ok = True
            std_dens = std_z = impl_dens = impl_z = 0.0
            std_nph = impl_nph = 0

            for rep in range(N_REPS):
                try:
                    t, nph, dens, z = run_single_flash(SystemSrkCPAstatoil, T, P, comps, mpc)
                    std_times.append(t)
                    if rep == N_REPS - 1:
                        std_nph, std_dens, std_z = nph, dens, z
                except Exception:
                    std_ok = False
                    break
                try:
                    t, nph, dens, z = run_single_flash(SystemSrkCPAstatoilFullyImplicit, T, P, comps, mpc)
                    impl_times.append(t)
                    if rep == N_REPS - 1:
                        impl_nph, impl_dens, impl_z = nph, dens, z
                except Exception:
                    impl_ok = False
                    break

            if std_ok and impl_ok and std_times and impl_times:
                std_median = float(np.median(std_times))
                impl_median = float(np.median(impl_times))
                ratio = impl_median / std_median if std_median > 0 else float("inf")
                dens_err = abs(std_dens - impl_dens) / max(abs(std_dens), 1e-10) * 100 if std_dens != 0 else 0.0
                results.append({
                    "T_K": float(T), "P_bar": float(P),
                    "std_time_ns": std_median, "impl_time_ns": impl_median,
                    "ratio": ratio, "std_nph": std_nph, "impl_nph": impl_nph,
                    "dens_err_pct": dens_err,
                    "z_err_pct": abs(std_z - impl_z) / max(abs(std_z), 1e-10) * 100 if std_z != 0 else 0.0,
                    "phase_match": std_nph == impl_nph,
                })

    print(f"\n  Completed: {len(results)} valid points out of {n_points}")

    if results:
        ratios = [r["ratio"] for r in results]
        dens_errs = [r["dens_err_pct"] for r in results]
        phase_matches = [r["phase_match"] for r in results]
        summary = {
            "n_points": len(results),
            "ratio_median": float(np.median(ratios)),
            "speedup_median": float(1.0 / np.median(ratios)),
            "max_dens_err_pct": float(np.max(dens_errs)),
            "phase_match_pct": float(np.mean(phase_matches) * 100),
        }
        print(f"\n  Ratio median: {summary['ratio_median']:.3f}")
        print(f"  Speedup:      {summary['speedup_median']:.2f}x")
        print(f"  Max err:      {summary['max_dens_err_pct']:.6f}%")
        print(f"  Phase match:  {summary['phase_match_pct']:.1f}%")

        # Merge into existing raw and summary files
        raw_path = str(RESULTS_DIR / "benchmark_raw.json")
        sum_path = str(RESULTS_DIR / "benchmark_summary.json")

        with open(raw_path) as f:
            raw_data = json.load(f)
        raw_data["B7_natgas_water_TEG"] = results
        with open(raw_path, "w") as f:
            json.dump(raw_data, f, indent=2)

        with open(sum_path) as f:
            sum_data = json.load(f)
        sum_data["B7_natgas_water_TEG"] = {
            "label": B7["label"],
            "category": B7["category"],
            "n_sites": B7["n_sites"],
            "phase_type": B7["phase_type"],
            "n_components": len(B7["components"]),
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
        with open(sum_path, "w") as f:
            json.dump(sum_data, f, indent=2)

        print(f"\nResults merged into {raw_path} and {sum_path}")


if __name__ == "__main__":
    main()
