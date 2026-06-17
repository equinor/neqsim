"""
Example: Monte Carlo simulation with checkpointing.

This script demonstrates how to write a NeqSim simulation job that:
1. Reads parameters from the job runner
2. Resumes from checkpoint on restart
3. Saves periodic checkpoints
4. Writes structured results

Run with::

    python -m neqsim_runner go example_monte_carlo.py \
        --args '{"n_iterations": 200, "base_pressure_bara": 60}' \
        --retries 3 --timeout 7200
"""

import random
import json
import os
import sys

# ── Import job helpers (works both inside runner and standalone) ──
try:
    from neqsim_runner.job_helpers import (
        get_args, get_output_dir, save_checkpoint, load_checkpoint,
        save_result, report_progress
    )
except ImportError:
    # Fallback for standalone execution
    import os
    get_args = lambda: json.loads(os.environ.get("NEQSIM_JOB_ARGS", "{}"))
    get_output_dir = lambda: __import__("pathlib").Path(".")
    save_checkpoint = lambda data: None
    load_checkpoint = lambda: None
    save_result = lambda data, **kw: print(json.dumps(data, indent=2))
    report_progress = lambda c, t, **kw: print(f"Progress: {c}/{t}")


def run_single_simulation(pressure_bara, temperature_c, flow_kg_hr):
    """
    Run a single NeqSim process simulation.

    This is where the actual NeqSim work happens — each call gets a fresh
    JVM context (the worker subprocess handles JVM lifecycle).
    """
    runtime_ns = globals().get("ns")
    if runtime_ns is None:
        project_root = os.environ.get("NEQSIM_PROJECT_ROOT")
        if not project_root:
            raise RuntimeError(
                "Run this example through NeqSim Runner from the repository or set NEQSIM_PROJECT_ROOT."
            )
        sys.path.insert(0, os.path.join(project_root, "devtools"))
        from neqsim_dev_setup import neqsim_init, neqsim_classes
        runtime_ns = neqsim_classes(
            neqsim_init(project_root=project_root, recompile=False, verbose=True))

    # Build fluid
    fluid = runtime_ns.SystemSrkEos(273.15 + temperature_c, pressure_bara)
    fluid.addComponent("methane", 0.85)
    fluid.addComponent("ethane", 0.10)
    fluid.addComponent("propane", 0.05)
    fluid.setMixingRule("classic")

    # Build process
    ProcessSystem = runtime_ns.ProcessSystem
    Stream = runtime_ns.Stream
    Separator = runtime_ns.Separator
    Compressor = runtime_ns.Compressor

    process = ProcessSystem()

    feed = Stream("feed", fluid)
    feed.setFlowRate(flow_kg_hr, "kg/hr")
    process.add(feed)

    sep = Separator("HP Sep", feed)
    process.add(sep)

    gas_out = sep.getGasOutStream()
    comp = Compressor("Compressor", gas_out)
    comp.setOutletPressure(pressure_bara * 2.0)
    process.add(comp)

    process.run()

    return {
        "outlet_temperature_C": float(comp.getOutletStream().getTemperature() - 273.15),
        "compressor_power_kW": float(comp.getPower("kW")),
        "gas_density_kg_m3": float(gas_out.getFluid().getDensity("kg/m3")),
    }


def main():
    # ── Read job arguments ──
    args = get_args()
    n_iterations = args.get("n_iterations", 50)
    base_pressure = args.get("base_pressure_bara", 60.0)
    base_temperature = args.get("base_temperature_C", 25.0)
    base_flow = args.get("base_flow_kg_hr", 50000.0)

    # ── Resume from checkpoint ──
    checkpoint = load_checkpoint()
    start_iter = 0
    results_list = []

    if checkpoint:
        start_iter = checkpoint.get("iteration", 0)
        results_list = checkpoint.get("results", [])
        print(f"Resuming from checkpoint: iteration {start_iter}/{n_iterations}")

    # ── Monte Carlo loop ──
    for i in range(start_iter, n_iterations):
        # Perturb parameters
        pressure = base_pressure * (1.0 + random.gauss(0, 0.1))
        temperature = base_temperature + random.gauss(0, 5.0)
        flow = base_flow * (1.0 + random.gauss(0, 0.15))

        try:
            result = run_single_simulation(pressure, temperature, flow)
            result["iteration"] = i
            result["input_pressure"] = pressure
            result["input_temperature"] = temperature
            result["input_flow"] = flow
            results_list.append(result)
        except Exception as e:
            print(f"  Iteration {i} failed: {e} — skipping")
            results_list.append({"iteration": i, "error": str(e)})

        # Checkpoint every 10 iterations
        if (i + 1) % 10 == 0:
            save_checkpoint({
                "iteration": i + 1,
                "results": results_list,
            })
            report_progress(i + 1, n_iterations)

    # ── Compute statistics ──
    valid = [r for r in results_list if "error" not in r]
    if valid:
        powers = [r["compressor_power_kW"] for r in valid]
        temps = [r["outlet_temperature_C"] for r in valid]

        powers_sorted = sorted(powers)
        temps_sorted = sorted(temps)
        n = len(powers_sorted)

        summary = {
            "n_iterations": n_iterations,
            "n_successful": len(valid),
            "n_failed": len(results_list) - len(valid),
            "compressor_power_kW": {
                "p10": powers_sorted[int(n * 0.10)],
                "p50": powers_sorted[int(n * 0.50)],
                "p90": powers_sorted[int(n * 0.90)],
                "mean": sum(powers) / n,
            },
            "outlet_temperature_C": {
                "p10": temps_sorted[int(n * 0.10)],
                "p50": temps_sorted[int(n * 0.50)],
                "p90": temps_sorted[int(n * 0.90)],
                "mean": sum(temps) / n,
            },
        }
    else:
        summary = {"error": "All iterations failed", "n_failed": len(results_list)}

    # ── Save final results ──
    save_result({
        "summary": summary,
        "all_results": results_list,
    })

    print(f"\nDone — {len(valid)}/{n_iterations} successful iterations")
    if valid:
        print(f"  Power P50: {summary['compressor_power_kW']['p50']:.1f} kW")
        print(f"  Temp  P50: {summary['outlet_temperature_C']['p50']:.1f} C")


if __name__ == "__main__":
    main()
