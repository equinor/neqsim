"""
Benchmark Chunk Worker — runs a slice of flash benchmarks in an isolated subprocess.

This script is submitted by flash_benchmark.py ``run_distributed`` mode via
the neqsim_runner.  Each invocation runs a subset of cases (a "chunk") in its
own JVM, writes incremental JSONL results, and saves a final summary via
``job_helpers.save_result()``.

It is NOT intended to be called directly — flash_benchmark.py handles
chunking, submission, and result merging.

Expected job args (passed via neqsim_runner)::

    {
        "cases": [...],           # list of case dicts
        "eos_name": "SRK",
        "algorithm_name": "baseline",
        "timing_repeats": 3,
        "chunk_index": 0
    }
"""

import json
import time
import statistics
import sys
import os
from pathlib import Path

# ── neqsim_runner helpers (available inside runner subprocess) ──
from neqsim_runner.job_helpers import (
    get_args,
    get_output_dir,
    save_checkpoint,
    load_checkpoint,
    save_result,
    report_progress,
)


def run_single_case(jneqsim, case, eos_name, timing_repeats):
    """Run one TP flash case and return metric dict."""
    times_ns = []
    last_converged = False
    last_n_phases = -1
    last_beta = -1.0
    last_error = None

    for _rep in range(timing_repeats):
        if eos_name == "SRK":
            fluid = jneqsim.thermo.system.SystemSrkEos(
                case["T_K"], case["P_bara"]
            )
        elif eos_name == "PR":
            fluid = jneqsim.thermo.system.SystemPrEos(
                case["T_K"], case["P_bara"]
            )
        else:
            fluid = jneqsim.thermo.system.SystemSrkEos(
                case["T_K"], case["P_bara"]
            )

        for comp_name, frac in case["components"].items():
            fluid.addComponent(comp_name, float(frac))
        fluid.setMixingRule("classic")

        ops = jneqsim.thermodynamicoperations.ThermodynamicOperations(fluid)

        t0 = time.perf_counter_ns()
        try:
            ops.TPflash()
            elapsed = time.perf_counter_ns() - t0
            times_ns.append(elapsed)
            fluid.initProperties()
            last_converged = True
            last_n_phases = int(fluid.getNumberOfPhases())
            last_beta = float(fluid.getBeta(0)) if last_n_phases > 0 else 0.0
            last_error = None
        except Exception as e:
            elapsed = time.perf_counter_ns() - t0
            times_ns.append(elapsed)
            last_converged = False
            last_error = str(e)

    median_time_ms = round(statistics.median(times_ns) / 1e6, 4)
    return {
        "case_id": case["case_id"],
        "family": case["family"],
        "eos": eos_name,
        "T_K": case["T_K"],
        "P_bara": case["P_bara"],
        "converged": last_converged,
        "cpu_time_ms": median_time_ms,
        "n_phases": last_n_phases,
        "beta_vapor": round(last_beta, 8) if last_beta >= 0 else None,
        "error": last_error,
    }


def main():
    args = get_args()
    cases = args["cases"]
    eos_name = args.get("eos_name", "SRK")
    algorithm_name = args.get("algorithm_name", "baseline")
    timing_repeats = args.get("timing_repeats", 3)
    chunk_index = args.get("chunk_index", 0)

    # Resume from checkpoint if available
    checkpoint = load_checkpoint()
    start_index = 0
    results = []
    if checkpoint:
        start_index = checkpoint.get("completed", 0)
        results = checkpoint.get("results", [])
        print(f"Resuming chunk {chunk_index} from case {start_index}/{len(cases)}")

    # Bootstrap NeqSim
    from tools.neqsim_bootstrap import get_jneqsim
    jneqsim = get_jneqsim()

    n_converged = 0
    failures = []
    all_times = []

    # Count already-completed results for stats
    for r in results:
        if r["converged"]:
            n_converged += 1
            all_times.append(r["cpu_time_ms"])
        else:
            failures.append(r)

    output_dir = Path(get_output_dir())
    jsonl_file = output_dir / f"chunk_{chunk_index}.jsonl"

    # Open in append mode to keep results from checkpoint
    mode = "a" if start_index > 0 else "w"
    with open(jsonl_file, mode, encoding="utf-8") as f:
        for i in range(start_index, len(cases)):
            case = cases[i]
            result = run_single_case(jneqsim, case, eos_name, timing_repeats)
            result["algorithm"] = algorithm_name
            results.append(result)

            f.write(json.dumps(result) + "\n")
            f.flush()

            if result["converged"]:
                n_converged += 1
                all_times.append(result["cpu_time_ms"])
            else:
                failures.append(result)

            # Checkpoint every 50 cases
            if (i + 1) % 50 == 0:
                save_checkpoint({
                    "completed": i + 1,
                    "results": results,
                })
                report_progress(i + 1, len(cases))

    # Summary for this chunk
    n_total = len(results)
    summary = {
        "chunk_index": chunk_index,
        "algorithm": algorithm_name,
        "eos": eos_name,
        "total_cases": n_total,
        "converged": n_converged,
        "failed": n_total - n_converged,
        "convergence_rate_pct": round(100.0 * n_converged / n_total, 2) if n_total > 0 else 0,
        "median_cpu_ms": round(statistics.median(all_times), 4) if all_times else None,
        "results_file": str(jsonl_file),
        "failures": failures,
    }

    save_result(summary)
    print(f"Chunk {chunk_index} complete: {n_converged}/{n_total} converged")


if __name__ == "__main__":
    main()
