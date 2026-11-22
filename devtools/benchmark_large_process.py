"""Run alternating fresh-JVM steady-state benchmarks and compare engineering outputs.

Compile LargeProcessSteadyStateBenchmark once and include it, the chosen production
classes, and the dependency jars in each classpath. No wall-time gate is imposed.
"""

import argparse
import json
import math
from pathlib import Path
import statistics
import subprocess


def equivalent(left, right):
    """Recursively check identities and every numerical product-state value."""
    if isinstance(left, dict) and isinstance(right, dict):
        return left.keys() == right.keys() and all(equivalent(left[k], right[k]) for k in left)
    if isinstance(left, list) and isinstance(right, list):
        return len(left) == len(right) and all(equivalent(a, b) for a, b in zip(left, right))
    if isinstance(left, bool) or isinstance(right, bool):
        return left is right
    if isinstance(left, (float, int)) and isinstance(right, (float, int)):
        return math.isclose(left, right, rel_tol=1e-7, abs_tol=1e-10)
    return left == right


def compare(reference, candidate):
    """Compare every measured operating point, including convergence and phase state."""
    differences = []
    if len(reference["samples"]) != len(candidate["samples"]):
        differences.append("sample count changed")
    for index, (before, after) in enumerate(zip(reference["samples"], candidate["samples"])):
        for key in ("solved", "validationPassed", "modelIterations", "recycleIterations"):
            if before[key] != after[key]:
                differences.append(f"sample {index}: {key} changed")
        for key in ("feedMassKgHr", "productMassKgHr", "netDutyW", "checksum"):
            if not math.isclose(before[key], after[key], rel_tol=1e-7, abs_tol=1e-7):
                differences.append(f"sample {index}: {key} changed")
        if len(before["products"]) != len(after["products"]):
            differences.append(f"sample {index}: product count changed")
        for left, right in zip(before["products"], after["products"]):
            if not equivalent(left, right):
                differences.append(f"sample {index}: {left['name']} full product state changed")
            if left["name"] != right["name"] or left["phases"] != right["phases"]:
                differences.append(f"sample {index}: product identity/phase count changed")
            for key in ("massKgHr", "temperatureK", "pressureBara"):
                if not math.isclose(left[key], right[key], rel_tol=1e-7, abs_tol=1e-7):
                    differences.append(f"sample {index}: {left['name']} {key} changed")
    return differences


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline-classpath", required=True)
    parser.add_argument("--candidate-classpath", required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--workload", default="splitter")
    parser.add_argument("--mode", default="changed")
    parser.add_argument("--strategy", default="sequential")
    parser.add_argument("--size", type=int, default=16)
    parser.add_argument("--pairs", type=int, default=5)
    parser.add_argument("--warmups", type=int, default=40)
    parser.add_argument("--repeats", type=int, default=20)
    parser.add_argument("--processors", type=int, default=4)
    parser.add_argument("--heap", default="512m")
    parser.add_argument("--java", default="java")
    args = parser.parse_args()
    if min(args.pairs, args.repeats, args.size, args.processors) < 1 or args.warmups < 0:
        parser.error("counts must be positive and warmups nonnegative")
    args.output.mkdir(parents=True, exist_ok=True)
    summaries = []
    for pair in range(args.pairs):
        reports = {}
        order = ("baseline", "candidate") if pair % 2 == 0 else ("candidate", "baseline")
        for variant in order:
            output = args.output / f"{pair + 1}-{variant}.json"
            classpath = getattr(args, f"{variant}_classpath")
            command = [
                args.java, f"-Xms{args.heap}", f"-Xmx{args.heap}",
                f"-XX:ActiveProcessorCount={args.processors}", "-cp", classpath,
                "neqsim.process.processmodel.LargeProcessSteadyStateBenchmark",
                args.workload, args.mode, str(args.warmups), str(args.repeats),
                str(args.size), args.strategy, str(output),
            ]
            with output.with_suffix(".log").open("w") as log:
                subprocess.run(command, stdout=log, stderr=subprocess.STDOUT, check=True)
            reports[variant] = json.loads(output.read_text())
        differences = compare(reports["baseline"], reports["candidate"])
        row = {"pair": pair + 1, "order": list(order), "differences": differences}
        excluded = {"elapsedNs", "mainThreadAllocatedBytes", "unitProfile"}
        row["exactEngineeringMatch"] = [
            {k: v for k, v in sample.items() if k not in excluded}
            for sample in reports["baseline"]["samples"]
        ] == [
            {k: v for k, v in sample.items() if k not in excluded}
            for sample in reports["candidate"]["samples"]
        ]
        for variant, report in reports.items():
            samples = report["samples"]
            row[f"{variant}MedianMs"] = statistics.median(s["elapsedNs"] for s in samples) / 1e6
            row[f"{variant}MeanMs"] = statistics.mean(s["elapsedNs"] for s in samples) / 1e6
            row[f"{variant}AllocationBytes"] = statistics.median(
                s["mainThreadAllocatedBytes"] for s in samples
            )
            row[f"{variant}MaximumMassError"] = max(s["massRelativeError"] for s in samples)
            row[f"{variant}MaximumComponentError"] = max(
                s["maximumComponentRelativeError"] for s in samples
            )
            row[f"{variant}MaximumEnergyError"] = max(s["energyRelativeError"] for s in samples)
        row["gainPercent"] = 100 * (1 - row["candidateMedianMs"] / row["baselineMedianMs"])
        summaries.append(row)
        summary = {
            "workload": args.workload, "mode": args.mode, "strategy": args.strategy,
            "size": args.size, "units": reports["baseline"]["units"],
            "javaVersion": reports["baseline"]["javaVersion"], "heap": args.heap,
            "processors": args.processors, "warmups": args.warmups, "repeats": args.repeats,
            "pairs": summaries,
            "medianPairedGainPercent": statistics.median(p["gainPercent"] for p in summaries),
            "pairedWins": sum(p["gainPercent"] > 0 for p in summaries),
            "engineeringMatch": all(not p["differences"] for p in summaries),
            "exactEngineeringMatch": all(p["exactEngineeringMatch"] for p in summaries),
        }
        (args.output / "summary.json").write_text(json.dumps(summary, indent=2) + "\n")
        print(f"{args.workload}/{args.mode} pair {pair + 1}: "
              f"{row['gainPercent']:.2f}% gain; differences={len(differences)}", flush=True)
    if not summary["engineeringMatch"]:
        raise SystemExit("Engineering comparison failed; inspect summary.json")


if __name__ == "__main__":
    main()
