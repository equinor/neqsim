"""
Flash Benchmark Runner — Batch execution of flash experiments.

Generates test cases, runs baseline/candidate algorithms, and produces
structured result files for paper-quality benchmarking.

Usage:
    # In-process (single JVM — good for < 500 cases)
    python flash_benchmark.py run --config benchmark_config.json --algorithm baseline --output results/

    # Distributed via neqsim_runner (isolated JVM per chunk — good for 500+ cases)
    python flash_benchmark.py run-distributed --config benchmark_config.json --algorithm baseline \
        --output results/ --chunk-size 200 --retries 3

    python flash_benchmark.py generate --config benchmark_config.json --output cases.jsonl
    python flash_benchmark.py summarize --results results/raw/baseline_results.jsonl
"""

import json
import time
import sys
import os
import argparse
import platform
import subprocess
import statistics
from pathlib import Path
from dataclasses import dataclass, asdict
from typing import Dict, List, Optional

import numpy as np


def generate_cases(config):
    """Generate all benchmark test cases from config.

    Args:
        config: Benchmark configuration dict (from benchmark_config.json)

    Returns:
        List of case dicts with case_id, family, components, T_K, P_bara
    """
    cases = []
    case_counter = 0

    np.random.seed(config.get("random_seed", 42))

    for family in config["families"]:
        base = family["base_composition"]
        names = list(base.keys())
        base_fracs = np.array([base[n] for n in names])

        # Dirichlet sampling for composition variants
        concentration = family.get("dirichlet_concentration", 50)
        alpha = base_fracs * concentration
        n_variants = family.get("n_composition_variants", 10)

        compositions = [base]  # Always include base composition
        for _ in range(n_variants - 1):
            x = np.random.dirichlet(alpha)
            comp = {n: round(float(xi), 8) for n, xi in zip(names, x)}
            compositions.append(comp)

        # PT grid
        T_range = family["T_range_K"]
        P_range = family["P_range_bara"]
        n_T = family.get("n_T", 15)
        n_P = family.get("n_P", 15)

        T_vals = np.linspace(T_range[0], T_range[1], n_T)
        P_vals = np.logspace(np.log10(P_range[0]), np.log10(P_range[1]), n_P)

        prefix = family["name"][:2].upper()

        for comp in compositions:
            for T in T_vals:
                for P in P_vals:
                    cases.append({
                        "case_id": f"{prefix}-{case_counter:06d}",
                        "family": family["name"],
                        "components": comp,
                        "T_K": round(float(T), 4),
                        "P_bara": round(float(P), 4),
                    })
                    case_counter += 1

    # Add stress cases
    stress_config = config.get("stress_cases", {})
    if stress_config.get("near_critical", 0) > 0:
        # Near-critical pure CO2 cases
        for i in range(stress_config["near_critical"]):
            T = 304.13 + np.random.uniform(-10, 10)  # CO2 Tc ± 10K
            P = 73.77 + np.random.uniform(-15, 15)   # CO2 Pc ± 15 bar
            cases.append({
                "case_id": f"NC-{case_counter:06d}",
                "family": "near_critical",
                "components": {"CO2": 0.95, "methane": 0.05},
                "T_K": round(float(T), 4),
                "P_bara": round(float(P), 4),
            })
            case_counter += 1

    return cases


def run_single_case(jneqsim, case, eos_name="SRK", timing_repeats=3):
    """Run a single TP flash case and return metrics.

    Args:
        jneqsim: NeqSim Java gateway object
        case: Case dict with components, T_K, P_bara
        eos_name: Equation of state name
        timing_repeats: Number of timing repetitions

    Returns:
        Result dict with convergence and timing metrics
    """
    times_ns = []
    last_converged = False
    last_n_phases = -1
    last_beta = -1.0
    last_error = None

    for rep in range(timing_repeats):
        # Create fresh fluid each time
        if eos_name == "SRK":
            fluid = jneqsim.thermo.system.SystemSrkEos(
                case["T_K"], case["P_bara"]
            )
        elif eos_name == "PR":
            fluid = jneqsim.thermo.system.SystemPrEos(
                case["T_K"], case["P_bara"]
            )
        else:
            raise ValueError(f"Unsupported EOS: {eos_name}")

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


def run_benchmark(config, algorithm_name, results_dir, progress_interval=50):
    """Run the complete benchmark suite.

    Args:
        config: Benchmark configuration dict
        algorithm_name: Name for this algorithm version
        results_dir: Output directory
        progress_interval: Print progress every N cases

    Returns:
        Summary dict
    """
    from tools.neqsim_bootstrap import get_jneqsim
    jneqsim = get_jneqsim()

    cases = generate_cases(config)
    eos_name = config["eos_models"][0]
    timing_repeats = config.get("timing_repeats", 3)

    raw_dir = Path(results_dir) / "raw"
    raw_dir.mkdir(parents=True, exist_ok=True)

    output_file = raw_dir / f"{algorithm_name}_results.jsonl"
    n_converged = 0
    n_total = 0
    failures = []
    all_times = []
    family_stats = {}

    print(f"Running benchmark: {algorithm_name}")
    print(f"  Cases: {len(cases)}")
    print(f"  EOS: {eos_name}")
    print(f"  Timing repeats: {timing_repeats}")
    print()

    t_start = time.time()

    with open(output_file, "w") as f:
        for i, case in enumerate(cases):
            result = run_single_case(jneqsim, case, eos_name, timing_repeats)
            result["algorithm"] = algorithm_name
            f.write(json.dumps(result) + "\n")

            n_total += 1
            if result["converged"]:
                n_converged += 1
                all_times.append(result["cpu_time_ms"])
            else:
                failures.append(result)

            # Track per-family stats
            fam = case["family"]
            if fam not in family_stats:
                family_stats[fam] = {"total": 0, "converged": 0, "times": []}
            family_stats[fam]["total"] += 1
            if result["converged"]:
                family_stats[fam]["converged"] += 1
                family_stats[fam]["times"].append(result["cpu_time_ms"])

            if (i + 1) % progress_interval == 0:
                elapsed = time.time() - t_start
                rate = (i + 1) / elapsed
                remaining = (len(cases) - i - 1) / rate
                print(
                    f"  [{i+1:5d}/{len(cases)}] "
                    f"conv={n_converged}/{n_total} "
                    f"({100*n_converged/n_total:.1f}%) "
                    f"~{remaining:.0f}s remaining"
                )

    # Build summary
    by_family = {}
    for fam, stats in family_stats.items():
        fam_summary = {
            "total": stats["total"],
            "converged": stats["converged"],
            "convergence_rate_pct": round(
                100.0 * stats["converged"] / stats["total"], 2
            ),
        }
        if stats["times"]:
            fam_summary["median_cpu_ms"] = round(
                statistics.median(stats["times"]), 4
            )
        by_family[fam] = fam_summary

    summary = {
        "algorithm": algorithm_name,
        "eos": eos_name,
        "total_cases": n_total,
        "converged": n_converged,
        "failed": n_total - n_converged,
        "convergence_rate_pct": round(100.0 * n_converged / n_total, 2),
        "median_cpu_ms": round(statistics.median(all_times), 4) if all_times else None,
        "mean_cpu_ms": round(statistics.mean(all_times), 4) if all_times else None,
        "by_family": by_family,
    }

    # Write summary
    summary_file = Path(results_dir) / f"summary_{algorithm_name}.json"
    with open(summary_file, "w") as f:
        json.dump(summary, f, indent=2)

    # Write failures
    failures_file = Path(results_dir) / f"failures_{algorithm_name}.json"
    with open(failures_file, "w") as f:
        json.dump(failures, f, indent=2)

    # Write metadata
    record_metadata(results_dir)

    elapsed_total = time.time() - t_start
    print(f"\nCompleted in {elapsed_total:.1f}s")
    print(f"  Convergence: {n_converged}/{n_total} ({summary['convergence_rate_pct']}%)")
    print(f"  Median time: {summary['median_cpu_ms']} ms")
    print(f"  Results: {output_file}")

    return summary


def record_metadata(results_dir):
    """Record benchmark environment metadata."""
    metadata = {
        "date": time.strftime("%Y-%m-%d %H:%M:%S"),
        "hostname": platform.node(),
        "os": platform.platform(),
        "python": platform.python_version(),
        "cpu": platform.processor(),
        "random_seed": 42,
    }

    try:
        result = subprocess.run(
            ["git", "rev-parse", "HEAD"],
            capture_output=True, text=True, timeout=5
        )
        metadata["git_commit"] = result.stdout.strip()
    except Exception:
        metadata["git_commit"] = "unknown"

    meta_file = Path(results_dir) / "benchmark_metadata.json"
    with open(meta_file, "w") as f:
        json.dump(metadata, f, indent=2)


def summarize_results(results_file):
    """Print summary statistics from a results JSONL file."""
    records = []
    with open(results_file) as f:
        for line in f:
            records.append(json.loads(line))

    total = len(records)
    converged = sum(1 for r in records if r["converged"])
    failed = total - converged
    times = [r["cpu_time_ms"] for r in records if r["converged"]]

    print(f"Results: {results_file}")
    print(f"  Total cases: {total}")
    print(f"  Converged:   {converged} ({100*converged/total:.1f}%)")
    print(f"  Failed:      {failed}")
    if times:
        print(f"  Median time: {statistics.median(times):.4f} ms")
        print(f"  Mean time:   {statistics.mean(times):.4f} ms")
        print(f"  P95 time:    {np.percentile(times, 95):.4f} ms")

    # By family
    families = set(r["family"] for r in records)
    print(f"\n  By family:")
    for fam in sorted(families):
        fam_records = [r for r in records if r["family"] == fam]
        fam_conv = sum(1 for r in fam_records if r["converged"])
        print(f"    {fam:20s}: {fam_conv}/{len(fam_records)} "
              f"({100*fam_conv/len(fam_records):.1f}%)")


# ══════════════════════════════════════════════════════════
# Distributed execution via neqsim_runner
# ══════════════════════════════════════════════════════════

def _chunk_list(lst, chunk_size):
    """Split a list into chunks of at most chunk_size."""
    for i in range(0, len(lst), chunk_size):
        yield lst[i:i + chunk_size]


def run_distributed(config, algorithm_name, results_dir,
                    chunk_size=200, max_retries=3, timeout_seconds=3600):
    """Run benchmark distributed across isolated subprocesses via neqsim_runner.

    Each chunk of cases runs in its own JVM. If a chunk fails (JVM crash,
    timeout), the runner retries it automatically. Results are merged into
    the same JSONL + summary format as ``run_benchmark()``.

    Args:
        config: Benchmark configuration dict.
        algorithm_name: Name for this algorithm version.
        results_dir: Output directory (same layout as run_benchmark).
        chunk_size: Number of cases per subprocess (default 200).
        max_retries: Retry count per chunk (default 3).
        timeout_seconds: Timeout per chunk in seconds (default 3600).

    Returns:
        Summary dict (same schema as run_benchmark).
    """
    # Lazy import — only needed for distributed mode
    try:
        from neqsim_runner.agent_bridge import AgentBridge
    except ImportError:
        print("ERROR: neqsim_runner not installed. Run: pip install -e devtools/")
        print("Falling back to in-process mode...")
        return run_benchmark(config, algorithm_name, results_dir)

    cases = generate_cases(config)
    eos_name = config["eos_models"][0]
    timing_repeats = config.get("timing_repeats", 3)

    chunks = list(_chunk_list(cases, chunk_size))
    print(f"Distributed benchmark: {algorithm_name}")
    print(f"  Total cases: {len(cases)}")
    print(f"  Chunks: {len(chunks)} (size {chunk_size})")
    print(f"  Retries per chunk: {max_retries}")
    print(f"  Timeout per chunk: {timeout_seconds}s")
    print()

    # Resolve worker script path
    worker_script = str(Path(__file__).parent / "benchmark_chunk_worker.py")

    # Set up the runner bridge with output under results_dir
    bridge = AgentBridge(
        task_dir=Path(results_dir).resolve(),
        queue_warn=len(chunks) + 10,
        queue_limit=len(chunks) * 2,
    )

    # Submit one job per chunk
    job_ids = []
    for i, chunk in enumerate(chunks):
        job_id = bridge.submit_script(
            script_path=worker_script,
            args={
                "cases": chunk,
                "eos_name": eos_name,
                "algorithm_name": algorithm_name,
                "timing_repeats": timing_repeats,
                "chunk_index": i,
            },
            max_retries=max_retries,
            timeout_seconds=timeout_seconds,
        )
        job_ids.append(job_id)

    # Run all chunks (blocking — supervisor handles retry)
    print(f"Running {len(job_ids)} chunks...")
    bridge.run_all()

    # Merge results
    raw_dir = Path(results_dir) / "raw"
    raw_dir.mkdir(parents=True, exist_ok=True)
    merged_file = raw_dir / f"{algorithm_name}_results.jsonl"

    all_records = []
    n_succeeded = 0
    n_failed_chunks = 0

    for job_id in job_ids:
        chunk_result = bridge.get_results(job_id)
        if chunk_result is None:
            n_failed_chunks += 1
            print(f"  WARNING: chunk {job_id} produced no results")
            continue

        n_succeeded += 1
        # Read the JSONL file from the chunk output
        results_file = chunk_result.get("results_file")
        if results_file and Path(results_file).exists():
            with open(results_file, "r", encoding="utf-8") as f:
                for line in f:
                    if line.strip():
                        all_records.append(json.loads(line))

    # Write merged JSONL
    with open(merged_file, "w", encoding="utf-8") as f:
        for record in all_records:
            f.write(json.dumps(record) + "\n")

    print(f"\nMerged {len(all_records)} results from {n_succeeded} chunks "
          f"({n_failed_chunks} failed)")

    # Build summary (same schema as run_benchmark)
    n_total = len(all_records)
    n_converged = sum(1 for r in all_records if r.get("converged"))
    all_times = [r["cpu_time_ms"] for r in all_records if r.get("converged")]
    failures = [r for r in all_records if not r.get("converged")]

    family_stats = {}
    for r in all_records:
        fam = r.get("family", "unknown")
        if fam not in family_stats:
            family_stats[fam] = {"total": 0, "converged": 0, "times": []}
        family_stats[fam]["total"] += 1
        if r.get("converged"):
            family_stats[fam]["converged"] += 1
            family_stats[fam]["times"].append(r["cpu_time_ms"])

    by_family = {}
    for fam, stats in family_stats.items():
        fam_summary = {
            "total": stats["total"],
            "converged": stats["converged"],
            "convergence_rate_pct": round(
                100.0 * stats["converged"] / stats["total"], 2
            ) if stats["total"] > 0 else 0,
        }
        if stats["times"]:
            fam_summary["median_cpu_ms"] = round(
                statistics.median(stats["times"]), 4
            )
        by_family[fam] = fam_summary

    summary = {
        "algorithm": algorithm_name,
        "eos": eos_name,
        "total_cases": n_total,
        "converged": n_converged,
        "failed": n_total - n_converged,
        "convergence_rate_pct": round(100.0 * n_converged / n_total, 2) if n_total > 0 else 0,
        "median_cpu_ms": round(statistics.median(all_times), 4) if all_times else None,
        "mean_cpu_ms": round(statistics.mean(all_times), 4) if all_times else None,
        "by_family": by_family,
        "execution_mode": "distributed",
        "n_chunks": len(chunks),
        "chunk_size": chunk_size,
        "chunks_succeeded": n_succeeded,
        "chunks_failed": n_failed_chunks,
    }

    # Write summary
    summary_file = Path(results_dir) / f"summary_{algorithm_name}.json"
    with open(summary_file, "w", encoding="utf-8") as f:
        json.dump(summary, f, indent=2)

    # Write failures
    failures_file = Path(results_dir) / f"failures_{algorithm_name}.json"
    with open(failures_file, "w", encoding="utf-8") as f:
        json.dump(failures, f, indent=2)

    # Write metadata
    record_metadata(results_dir)

    print(f"  Convergence: {n_converged}/{n_total} ({summary['convergence_rate_pct']}%)")
    if summary["median_cpu_ms"]:
        print(f"  Median time: {summary['median_cpu_ms']} ms")
    print(f"  Results: {merged_file}")

    return summary


def main():
    parser = argparse.ArgumentParser(description="Flash Benchmark Runner")
    subparsers = parser.add_subparsers(dest="command")

    # Generate command
    gen = subparsers.add_parser("generate", help="Generate test cases")
    gen.add_argument("--config", required=True, help="Benchmark config JSON")
    gen.add_argument("--output", default="cases.jsonl", help="Output JSONL file")

    # Run command (in-process, single JVM)
    run = subparsers.add_parser("run", help="Run benchmark suite (single JVM)")
    run.add_argument("--config", required=True, help="Benchmark config JSON")
    run.add_argument("--algorithm", required=True, help="Algorithm name")
    run.add_argument("--output", default="results/", help="Output directory")

    # Run-distributed command (isolated JVM per chunk via neqsim_runner)
    dist = subparsers.add_parser(
        "run-distributed",
        help="Run benchmark distributed via neqsim_runner (isolated JVM per chunk)"
    )
    dist.add_argument("--config", required=True, help="Benchmark config JSON")
    dist.add_argument("--algorithm", required=True, help="Algorithm name")
    dist.add_argument("--output", default="results/", help="Output directory")
    dist.add_argument("--chunk-size", type=int, default=200,
                      help="Cases per subprocess (default: 200)")
    dist.add_argument("--retries", type=int, default=3,
                      help="Retries per chunk (default: 3)")
    dist.add_argument("--timeout", type=int, default=3600,
                      help="Timeout per chunk in seconds (default: 3600)")

    # Summarize command
    summ = subparsers.add_parser("summarize", help="Summarize results")
    summ.add_argument("--results", required=True, help="Results JSONL file")

    args = parser.parse_args()

    if args.command == "generate":
        with open(args.config) as f:
            config = json.load(f)
        cases = generate_cases(config)
        with open(args.output, "w") as f:
            for case in cases:
                f.write(json.dumps(case) + "\n")
        print(f"Generated {len(cases)} cases -> {args.output}")

    elif args.command == "run":
        with open(args.config) as f:
            config = json.load(f)
        run_benchmark(config, args.algorithm, args.output)

    elif args.command == "run-distributed":
        with open(args.config) as f:
            config = json.load(f)
        run_distributed(
            config, args.algorithm, args.output,
            chunk_size=args.chunk_size,
            max_retries=args.retries,
            timeout_seconds=args.timeout,
        )

    elif args.command == "summarize":
        summarize_results(args.results)

    else:
        parser.print_help()


if __name__ == "__main__":
    main()
