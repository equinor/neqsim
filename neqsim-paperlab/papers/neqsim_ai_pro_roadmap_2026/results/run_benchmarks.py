"""
Benchmark script for NeqSim computational performance assessment.
Measures execution times for different process simulation configurations
to support the AI-PRO roadmap paper.
"""
import time
import json
import numpy as np
import sys
import os

# Setup NeqSim
try:
    from neqsim import jneqsim
    print("NeqSim loaded via pip package")
except ImportError:
    print("ERROR: neqsim not available")
    sys.exit(1)

# Java classes
SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
SystemPrEos = jneqsim.thermo.system.SystemPrEos
ThermodynamicOperations = jneqsim.thermodynamicoperations.ThermodynamicOperations
ProcessSystem = jneqsim.process.processmodel.ProcessSystem
Stream = jneqsim.process.equipment.stream.Stream
Separator = jneqsim.process.equipment.separator.Separator
ThreePhaseSeparator = jneqsim.process.equipment.separator.ThreePhaseSeparator
Compressor = jneqsim.process.equipment.compressor.Compressor
Cooler = jneqsim.process.equipment.heatexchanger.Cooler
Heater = jneqsim.process.equipment.heatexchanger.Heater
ThrottlingValve = jneqsim.process.equipment.valve.ThrottlingValve
Mixer = jneqsim.process.equipment.mixer.Mixer
Splitter = jneqsim.process.equipment.splitter.Splitter

RESULTS_DIR = os.path.join(os.path.dirname(__file__), "..", "results")
os.makedirs(RESULTS_DIR, exist_ok=True)

N_REPS = 10  # repetitions per benchmark

def create_natural_gas(T_C=25.0, P_bara=60.0):
    """Create a typical natural gas fluid."""
    fluid = SystemSrkEos(273.15 + T_C, P_bara)
    fluid.addComponent("nitrogen", 1.5)
    fluid.addComponent("CO2", 2.5)
    fluid.addComponent("methane", 80.0)
    fluid.addComponent("ethane", 8.0)
    fluid.addComponent("propane", 4.0)
    fluid.addComponent("n-butane", 2.0)
    fluid.addComponent("n-pentane", 1.0)
    fluid.addComponent("n-hexane", 0.5)
    fluid.addComponent("n-heptane", 0.5)
    fluid.setMixingRule("classic")
    fluid.setMultiPhaseCheck(True)
    return fluid

def create_oil_gas_water(T_C=80.0, P_bara=120.0):
    """Create a three-phase oil/gas/water fluid."""
    fluid = SystemSrkEos(273.15 + T_C, P_bara)
    fluid.addComponent("nitrogen", 0.5)
    fluid.addComponent("CO2", 3.0)
    fluid.addComponent("methane", 45.0)
    fluid.addComponent("ethane", 7.0)
    fluid.addComponent("propane", 5.0)
    fluid.addComponent("n-butane", 3.0)
    fluid.addComponent("n-pentane", 2.5)
    fluid.addComponent("n-hexane", 2.0)
    fluid.addComponent("n-heptane", 5.0)
    fluid.addComponent("n-octane", 4.0)
    fluid.addComponent("n-nonane", 2.0)
    fluid.addComponent("nC10", 1.5)
    fluid.addComponent("water", 19.5)
    fluid.setMixingRule("classic")
    fluid.setMultiPhaseCheck(True)
    return fluid

def create_lean_gas(T_C=15.0, P_bara=70.0):
    """Create a lean gas fluid."""
    fluid = SystemSrkEos(273.15 + T_C, P_bara)
    fluid.addComponent("nitrogen", 2.0)
    fluid.addComponent("CO2", 1.0)
    fluid.addComponent("methane", 92.0)
    fluid.addComponent("ethane", 4.0)
    fluid.addComponent("propane", 1.0)
    fluid.setMixingRule("classic")
    return fluid

def timeit(func, n_reps=N_REPS):
    """Time a function n_reps times, return mean and std in ms."""
    times = []
    for i in range(n_reps):
        t0 = time.perf_counter()
        func()
        t1 = time.perf_counter()
        times.append((t1 - t0) * 1000.0)  # ms
    return {"mean_ms": np.mean(times), "std_ms": np.std(times),
            "min_ms": np.min(times), "max_ms": np.max(times),
            "n": n_reps, "raw_ms": [float(t) for t in times]}

# ===== BENCHMARK 1: Flash calculations =====
def bench_tpflash():
    """TPflash timing for different fluid complexities."""
    results = {}

    # Lean gas (5 components)
    fluid = create_lean_gas()
    ops = ThermodynamicOperations(fluid)
    def run_flash_lean():
        ops.TPflash()
        fluid.initProperties()
    results["TPflash_lean_5comp"] = timeit(run_flash_lean)
    print(f"  Lean gas TPflash: {results['TPflash_lean_5comp']['mean_ms']:.2f} +/- {results['TPflash_lean_5comp']['std_ms']:.2f} ms")

    # Natural gas (9 components)
    fluid2 = create_natural_gas()
    ops2 = ThermodynamicOperations(fluid2)
    def run_flash_ng():
        ops2.TPflash()
        fluid2.initProperties()
    results["TPflash_natgas_9comp"] = timeit(run_flash_ng)
    print(f"  Natural gas TPflash: {results['TPflash_natgas_9comp']['mean_ms']:.2f} +/- {results['TPflash_natgas_9comp']['std_ms']:.2f} ms")

    # Oil-gas-water (13 components)
    fluid3 = create_oil_gas_water()
    ops3 = ThermodynamicOperations(fluid3)
    def run_flash_ogw():
        ops3.TPflash()
        fluid3.initProperties()
    results["TPflash_ogw_13comp"] = timeit(run_flash_ogw)
    print(f"  Oil-gas-water TPflash: {results['TPflash_ogw_13comp']['mean_ms']:.2f} +/- {results['TPflash_ogw_13comp']['std_ms']:.2f} ms")

    # Sweep: flash at multiple pressures
    pressures = [10, 20, 40, 60, 80, 100, 150, 200]
    sweep_times = []
    for P in pressures:
        fluid_sweep = create_natural_gas(T_C=25.0, P_bara=float(P))
        ops_sweep = ThermodynamicOperations(fluid_sweep)
        def run_sweep():
            ops_sweep.TPflash()
            fluid_sweep.initProperties()
        t = timeit(run_sweep, n_reps=5)
        sweep_times.append({"P_bara": P, "mean_ms": t["mean_ms"], "std_ms": t["std_ms"]})
    results["TPflash_pressure_sweep"] = sweep_times

    return results

# ===== BENCHMARK 2: Process system execution =====
def bench_single_separator():
    """Simple separator process."""
    fluid = create_natural_gas(T_C=40.0, P_bara=70.0)
    feed = Stream("feed", fluid)
    feed.setFlowRate(100000.0, "kg/hr")
    sep = Separator("HP Sep", feed)
    process = ProcessSystem()
    process.add(feed)
    process.add(sep)

    def run_sep():
        process.run()

    return timeit(run_sep)

def bench_two_stage_separation():
    """HP + LP separation with valve."""
    fluid = create_oil_gas_water(T_C=80.0, P_bara=120.0)
    feed = Stream("feed", fluid)
    feed.setFlowRate(200000.0, "kg/hr")

    hp_sep = ThreePhaseSeparator("HP Sep", feed)
    valve = ThrottlingValve("LP Valve", hp_sep.getLiquidOutStream())
    valve.setOutletPressure(15.0)
    lp_sep = ThreePhaseSeparator("LP Sep", valve.getOutletStream())

    process = ProcessSystem()
    process.add(feed)
    process.add(hp_sep)
    process.add(valve)
    process.add(lp_sep)

    def run_twostage():
        process.run()

    return timeit(run_twostage)

def bench_compression_train():
    """3-stage compression with intercooling."""
    fluid = create_lean_gas(T_C=30.0, P_bara=5.0)
    feed = Stream("feed", fluid)
    feed.setFlowRate(50000.0, "kg/hr")

    comp1 = Compressor("Comp 1", feed)
    comp1.setOutletPressure(15.0)
    cool1 = Cooler("Cooler 1", comp1.getOutletStream())
    cool1.setOutTemperature(273.15 + 35.0)

    comp2 = Compressor("Comp 2", cool1.getOutletStream())
    comp2.setOutletPressure(45.0)
    cool2 = Cooler("Cooler 2", comp2.getOutletStream())
    cool2.setOutTemperature(273.15 + 35.0)

    comp3 = Compressor("Comp 3", cool2.getOutletStream())
    comp3.setOutletPressure(120.0)
    cool3 = Cooler("Cooler 3", comp3.getOutletStream())
    cool3.setOutTemperature(273.15 + 35.0)

    process = ProcessSystem()
    process.add(feed)
    process.add(comp1)
    process.add(cool1)
    process.add(comp2)
    process.add(cool2)
    process.add(comp3)
    process.add(cool3)

    def run_comp():
        process.run()

    return timeit(run_comp)

def bench_full_process():
    """Full process: separation + compression + cooling."""
    fluid = create_oil_gas_water(T_C=70.0, P_bara=80.0)
    feed = Stream("feed", fluid)
    feed.setFlowRate(300000.0, "kg/hr")

    hp_sep = ThreePhaseSeparator("HP Sep", feed)

    # Gas path: compression
    comp1 = Compressor("Gas Comp", hp_sep.getGasOutStream())
    comp1.setOutletPressure(150.0)
    gas_cool = Cooler("Gas Cooler", comp1.getOutletStream())
    gas_cool.setOutTemperature(273.15 + 40.0)

    # Liquid path: LP separation
    valve = ThrottlingValve("LP Valve", hp_sep.getOilOutStream())
    valve.setOutletPressure(10.0)
    lp_sep = Separator("LP Sep", valve.getOutletStream())

    process = ProcessSystem()
    process.add(feed)
    process.add(hp_sep)
    process.add(comp1)
    process.add(gas_cool)
    process.add(valve)
    process.add(lp_sep)

    def run_full():
        process.run()

    return timeit(run_full)

# ===== BENCHMARK 3: Surrogate training data generation rate =====
def bench_training_data_generation():
    """How many flash evaluations per second for surrogate training data."""
    fluid = create_natural_gas()
    ops = ThermodynamicOperations(fluid)

    n_samples = 200
    T_range = np.linspace(250.0, 400.0, 20)
    P_range = np.linspace(10.0, 200.0, 10)

    t0 = time.perf_counter()
    count = 0
    for T in T_range:
        for P in P_range:
            fluid.setTemperature(float(T))
            fluid.setPressure(float(P))
            try:
                ops.TPflash()
                fluid.initProperties()
                count += 1
            except Exception:
                pass
    t1 = time.perf_counter()

    total_ms = (t1 - t0) * 1000.0
    rate = count / (total_ms / 1000.0)

    return {
        "n_samples": count,
        "total_ms": total_ms,
        "samples_per_second": rate,
        "ms_per_sample": total_ms / max(count, 1)
    }

# ===== BENCHMARK 4: RL step timing =====
def bench_rl_step():
    """Timing for a single RL step (process evaluation)."""
    fluid = create_natural_gas(T_C=40.0, P_bara=70.0)
    feed = Stream("feed-rl", fluid)
    feed.setFlowRate(100000.0, "kg/hr")
    sep = Separator("RL Sep", feed)

    process = ProcessSystem()
    process.add(feed)
    process.add(sep)
    process.run()  # Warm up

    # Simulate RL step: change a parameter and re-run
    pressures = np.linspace(30.0, 100.0, 20)
    step_times = []
    for P in pressures:
        feed.setPressure(float(P), "bara")
        t0 = time.perf_counter()
        process.run()
        t1 = time.perf_counter()
        step_times.append((t1 - t0) * 1000.0)

    return {
        "mean_ms": float(np.mean(step_times)),
        "std_ms": float(np.std(step_times)),
        "min_ms": float(np.min(step_times)),
        "max_ms": float(np.max(step_times)),
        "n_steps": len(step_times),
        "steps_per_second": 1000.0 / np.mean(step_times),
        "raw_ms": [float(t) for t in step_times]
    }


def main():
    all_results = {}

    print("=" * 60)
    print("NeqSim Computational Performance Benchmark")
    print("=" * 60)

    # 1. Flash calculations
    print("\n[1/5] Flash calculation benchmarks...")
    all_results["flash"] = bench_tpflash()

    # 2. Single separator
    print("\n[2/5] Single separator process...")
    all_results["single_separator"] = bench_single_separator()
    print(f"  Single separator: {all_results['single_separator']['mean_ms']:.2f} +/- {all_results['single_separator']['std_ms']:.2f} ms")

    # 3. Two-stage separation
    print("\n[3/5] Two-stage separation...")
    all_results["two_stage_separation"] = bench_two_stage_separation()
    print(f"  Two-stage separation: {all_results['two_stage_separation']['mean_ms']:.2f} +/- {all_results['two_stage_separation']['std_ms']:.2f} ms")

    # 4. Compression train
    print("\n[4/5] Three-stage compression...")
    all_results["compression_train"] = bench_compression_train()
    print(f"  Compression train: {all_results['compression_train']['mean_ms']:.2f} +/- {all_results['compression_train']['std_ms']:.2f} ms")

    # 5. Full process
    print("\n[5/5] Full process (separation + compression)...")
    all_results["full_process"] = bench_full_process()
    print(f"  Full process: {all_results['full_process']['mean_ms']:.2f} +/- {all_results['full_process']['std_ms']:.2f} ms")

    # 6. Training data generation rate
    print("\n[Bonus] Surrogate training data generation rate...")
    all_results["training_data"] = bench_training_data_generation()
    print(f"  Rate: {all_results['training_data']['samples_per_second']:.0f} samples/sec ({all_results['training_data']['ms_per_sample']:.2f} ms/sample)")

    # 7. RL step timing
    print("\n[Bonus] RL step timing...")
    all_results["rl_step"] = bench_rl_step()
    print(f"  RL step: {all_results['rl_step']['mean_ms']:.2f} +/- {all_results['rl_step']['std_ms']:.2f} ms ({all_results['rl_step']['steps_per_second']:.0f} steps/sec)")

    # Save results
    output_path = os.path.join(RESULTS_DIR, "benchmark_results.json")
    with open(output_path, "w") as f:
        json.dump(all_results, f, indent=2)
    print(f"\nResults saved to {output_path}")

    # Print summary table
    print("\n" + "=" * 60)
    print("SUMMARY")
    print("=" * 60)
    print(f"{'Configuration':<35} {'Mean (ms)':>10} {'Std (ms)':>10}")
    print("-" * 60)
    for key in ["flash"]:
        for subkey in ["TPflash_lean_5comp", "TPflash_natgas_9comp", "TPflash_ogw_13comp"]:
            r = all_results[key][subkey]
            print(f"  {subkey:<33} {r['mean_ms']:>10.2f} {r['std_ms']:>10.2f}")
    for key in ["single_separator", "two_stage_separation", "compression_train", "full_process"]:
        r = all_results[key]
        print(f"  {key:<33} {r['mean_ms']:>10.2f} {r['std_ms']:>10.2f}")
    print(f"  {'RL step (separator)':<33} {all_results['rl_step']['mean_ms']:>10.2f} {all_results['rl_step']['std_ms']:>10.2f}")
    print(f"  Training data: {all_results['training_data']['samples_per_second']:.0f} samples/sec")


if __name__ == "__main__":
    main()
