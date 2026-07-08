"""Benchmark CVD (Constant Volume Depletion) simulation speed for reservoir
fluids with multiphase flash enabled vs disabled.

Runs the same CVD simulation repeatedly with system.setMultiPhaseCheck(true)
and system.setMultiPhaseCheck(false), and reports timing statistics.
"""

import os
import statistics
import sys
import time
from pathlib import Path


def find_neqsim_project_root():
    env_root = os.environ.get("NEQSIM_PROJECT_ROOT")
    candidates = []
    if env_root:
        candidates.append(Path(env_root).resolve())
    cwd = Path.cwd().resolve()
    candidates.extend([cwd] + list(cwd.parents))
    for candidate in candidates:
        if (candidate / "pom.xml").exists() and (
            candidate / "devtools" / "neqsim_dev_setup.py"
        ).exists():
            return candidate
    raise RuntimeError("Could not find NeqSim project root. Set NEQSIM_PROJECT_ROOT.")


PROJECT_ROOT = find_neqsim_project_root()
sys.path.insert(0, str(PROJECT_ROOT / "devtools"))

from neqsim_dev_setup import neqsim_init, neqsim_classes  # noqa: E402

ns = neqsim_init(project_root=PROJECT_ROOT, recompile=False, verbose=True)
ns = neqsim_classes(ns)


def build_reservoir_fluid():
    """Build a representative gas-condensate reservoir fluid (SRK, MR2, C11+)."""
    fluid = ns.SystemSrkEos(298.0, 211.0)
    fluid.addComponent("nitrogen", 0.34)
    fluid.addComponent("CO2", 3.59)
    fluid.addComponent("methane", 67.42)
    fluid.addComponent("ethane", 9.02)
    fluid.addComponent("propane", 4.31)
    fluid.addComponent("i-butane", 0.93)
    fluid.addComponent("n-butane", 1.71)
    fluid.addComponent("i-pentane", 0.74)
    fluid.addComponent("n-pentane", 0.85)
    fluid.addComponent("n-hexane", 1.38)
    fluid.addTBPfraction("C7", 1.5, 109.00 / 1000.0, 0.6912)
    fluid.addTBPfraction("C8", 1.69, 120.20 / 1000.0, 0.7255)
    fluid.addTBPfraction("C9", 1.14, 129.5 / 1000.0, 0.7454)
    fluid.addTBPfraction("C10", 0.8, 135.3 / 1000.0, 0.7864)
    fluid.addPlusFraction("C11", 4.58, 256.2 / 1000.0, 0.8398)
    fluid.createDatabase(True)
    fluid.setMixingRule(2)
    fluid.init(0)
    fluid.init(1)
    return fluid


PRESSURES = [400.0, 350.0, 300.0, 250.0, 200.0, 175.0, 150.0, 125.0, 100.0, 75.0, 50.0]
TEMPERATURE_K = 315.0


def run_cvd(multiphase):
    """Build a fresh fluid, run one CVD simulation, return (elapsed_s, rel_vol)."""
    fluid = build_reservoir_fluid()
    fluid.setMultiPhaseCheck(bool(multiphase))
    cvd = ns.JClass("neqsim.pvtsimulation.simulation.ConstantVolumeDepletion")(fluid)
    cvd.setTemperature(TEMPERATURE_K)
    cvd.setPressures(list(PRESSURES))
    t0 = time.perf_counter()
    cvd.runCalc()
    elapsed = time.perf_counter() - t0
    rel_vol = list(cvd.getRelativeVolume())
    return elapsed, rel_vol


def benchmark(multiphase, repeats, warmup=1):
    for _ in range(warmup):
        run_cvd(multiphase)
    times = []
    last_rel = None
    for _ in range(repeats):
        elapsed, last_rel = run_cvd(multiphase)
        times.append(elapsed)
    return times, last_rel


def summarize(label, times):
    mean = statistics.mean(times)
    median = statistics.median(times)
    stdev = statistics.stdev(times) if len(times) > 1 else 0.0
    print(
        f"{label:28s} mean={mean*1000:8.2f} ms  "
        f"median={median*1000:8.2f} ms  "
        f"min={min(times)*1000:8.2f} ms  "
        f"max={max(times)*1000:8.2f} ms  std={stdev*1000:6.2f} ms"
    )
    return mean, median


def main():
    repeats = int(os.environ.get("CVD_REPEATS", "20"))
    print(f"\nCVD multiphase-flash speed benchmark")
    print(f"Fluid: gas-condensate (SRK EOS, MR2, 15 comps incl. C11+)")
    print(f"CVD stages: {len(PRESSURES)} pressures, T = {TEMPERATURE_K} K")
    print(f"Repeats: {repeats} (+1 warmup each)\n")

    # Verify both configs produce equivalent relative-volume results
    t_false, rel_false = benchmark(False, repeats)
    t_true, rel_true = benchmark(True, repeats)

    mean_false, med_false = summarize("multiPhaseCheck = FALSE", t_false)
    mean_true, med_true = summarize("multiPhaseCheck = TRUE", t_true)

    print("\nRelative volume (last stage): "
          f"false={rel_false[-1]:.6f}  true={rel_true[-1]:.6f}  "
          f"diff={abs(rel_false[-1]-rel_true[-1]):.2e}")

    slowdown_mean = mean_true / mean_false if mean_false else float("nan")
    slowdown_med = med_true / med_false if med_false else float("nan")
    print(
        f"\nMultiphase overhead: "
        f"{(slowdown_mean-1)*100:+.1f}% (mean), "
        f"{(slowdown_med-1)*100:+.1f}% (median)  "
        f"=> multiphase is {slowdown_mean:.2f}x the single-flash time"
    )


if __name__ == "__main__":
    main()
