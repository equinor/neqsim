#!/usr/bin/env python3
"""Run matched HV/classical workloads in separate JVMs with an identical-binary control.

This orchestrates HuronVidalClonePerformanceBenchmark without changing its fixtures,
equilibrium gates or measured work. It never downloads or compiles code. Timing is
characterization, not a CI assertion or an experimental-accuracy acceptance test.
"""

import argparse
import csv
import hashlib
import itertools
import json
import math
import os
from pathlib import Path
import platform
import shutil
import statistics
import subprocess
import struct
import time


COMPONENT = Path("neqsim/thermo/component/ComponentGENRTLmodifiedHV.class")
HARNESS = "neqsim.thermo.mixingrule.HuronVidalClonePerformanceBenchmark"


def digest(path):
    """Return the SHA-256 fingerprint of one immutable input."""
    return hashlib.sha256(path.read_bytes()).hexdigest()


def read_csv(path):
    """Read a complete CSV and reject empty evidence."""
    with path.open(newline="", encoding="utf-8") as stream:
        rows = list(csv.DictReader(stream))
    if not rows:
        raise ValueError(f"Empty report: {path}")
    return rows


def write_csv(path, rows):
    """Write normalized combined evidence with a stable column order."""
    temporary = path.with_name(path.name + ".tmp")
    with temporary.open("w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, fieldnames=list(rows[0]), lineterminator="\n")
        writer.writeheader()
        writer.writerows(rows)
    temporary.replace(path)


def main():
    """Validate inputs, execute alternating independent JVMs and retain every round."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--project", type=Path, required=True)
    parser.add_argument("--baseline", type=Path, required=True)
    parser.add_argument("--candidate", type=Path, required=True)
    parser.add_argument("--dependencies", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--base-sha", required=True)
    parser.add_argument("--resume", action="store_true", help="Resume only complete, input-matched JVM receipts")
    parser.add_argument("--cpu", type=int)
    parser.add_argument("--forks", type=int, default=3)
    parser.add_argument("--warmups", type=int, default=30)
    parser.add_argument("--rounds", type=int, default=10)
    parser.add_argument("--eos", nargs="+", choices=["SRK", "PR"], default=["SRK", "PR"])
    parser.add_argument("--components", nargs="+", type=int, choices=[4, 20], default=[4, 20])
    parser.add_argument("--operations", nargs="+", choices=["phase", "flash"], default=["phase", "flash"])
    parser.add_argument("--initializations", nargs="+", type=int, choices=[1, 3], default=[1, 3])
    args = parser.parse_args()
    if args.forks < 2 or args.warmups < 1 or args.rounds < 2:
        parser.error("Require multiple independent forks, positive warmup and multiple rounds")
    project = args.project.resolve()
    variants = {key: getattr(args, key).resolve() for key in ("baseline", "candidate")}
    inputs = {key: digest(path / COMPONENT) for key, path in variants.items()}
    versions = {key: struct.unpack(">H", (path / COMPONENT).read_bytes()[6:8])[0] for key, path in variants.items()}
    if len(set(versions.values())) != 1:
        parser.error("Baseline and candidate class-file versions differ; rebuild with identical compiler options")
    dependencies = args.dependencies.read_text(encoding="utf-8").strip()
    java = shutil.which("java")
    if not java:
        parser.error("java must already be installed")
    prefix = []
    if args.cpu is not None:
        if not shutil.which("taskset"):
            parser.error("Requested CPU affinity requires taskset")
        prefix = ["taskset", "-c", str(args.cpu)]
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=args.resume)
    environment = {
        "base_sha": args.base_sha,
        "utc_started": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "platform": platform.platform(),
        "cpu_affinity": args.cpu,
        "affinity_is_exclusive": False,
        "java": subprocess.run([java, "-version"], capture_output=True, text=True, check=True).stderr,
        "jvm_options": ["-Xms512m", "-Xmx512m", "-Xbatch"],
        "component_class_sha256": inputs,
        "component_class_versions": versions,
        "harness_class_sha256": digest(project / "target/test-classes" / Path(HARNESS.replace(".", "/") + ".class")),
        "forks": args.forks,
        "warmup_batches": args.warmups,
        "measurement_batches": args.rounds,
        "phase_iterations": {"4": 2000, "20": 200},
        "flash_iterations": {"4": 20, "20": 4},
        "notes": "Same harness and remaining classes; control uses the exact baseline component binary. "
                 "One EOS/scenario per JVM. Host isolation is not asserted. Different mixing rules can do different solver work.",
    }
    if args.resume:
        previous = json.loads((output / "environment.json").read_text(encoding="utf-8"))
        for key in ("base_sha", "component_class_sha256", "component_class_versions", "harness_class_sha256",
                    "forks", "warmup_batches", "measurement_batches", "cpu_affinity", "jvm_options"):
            if previous[key] != environment[key]:
                raise ValueError(f"Cannot resume changed input: {key}")
        environment = previous
    (output / "environment.json").write_text(json.dumps(environment, indent=2) + "\n", encoding="utf-8")
    cells = list(itertools.product(args.eos, args.components, args.operations, args.initializations))
    combined = read_csv(output / "rounds.csv") if args.resume else []
    states = read_csv(output / "states.csv") if args.resume else []
    reference_states = {}
    for row in states:
        key = (row["eos"], row["components"], row["operation"], row["init"], row["rule"])
        reference_states.setdefault(key, []).append(row)
    summaries = read_csv(output / "forks.csv") if args.resume else []
    completed = {(str(row["fork"]), row["eos"], str(row["components"]), row["operation"],
                  str(row["init"]), row["arm"]) for row in summaries}
    completed_labels = {f"f{fork}-{eos}-{size}-{operation}-i{init}-{arm}"
                        for fork, eos, size, operation, init, arm in completed}
    combined = [row for row in combined if row["sample"] in completed_labels]
    for fork in range(args.forks):
        for eos, size, operation, init in (cells if fork % 2 == 0 else reversed(cells)):
            arms = [("baseline", 4), ("candidate", 4), ("control", 4), ("classical", 2)]
            shift = fork % len(arms)
            arms = arms[shift:] + arms[:shift]
            if fork % 2:
                arms.reverse()
            for arm, rule in arms:
                if (str(fork), eos, str(size), operation, str(init), arm) in completed:
                    continue
                variant = "candidate" if arm == "candidate" else "baseline"
                if digest(variants[variant] / COMPONENT) != inputs[variant]:
                    raise RuntimeError("Input component binary changed during measurement")
                label = f"f{fork}-{eos}-{size}-{operation}-i{init}-{arm}"
                report = output / (label + ".csv")
                if args.resume:
                    for partial in (report, Path(str(report) + ".states.csv")):
                        if partial.exists():
                            partial.unlink()
                classpath = os.pathsep.join([str(variants[variant]), str(project / "target/classes"),
                                            str(project / "target/test-classes"), dependencies])
                iterations = (2000 if size == 4 else 200) if operation == "phase" else (20 if size == 4 else 4)
                command = prefix + [java, "-Xms512m", "-Xmx512m", "-Xbatch", "-cp", classpath, HARNESS, label,
                                    str(report), eos, str(size), operation, str(init), str(rule),
                                    str(args.warmups), str(args.rounds), str(iterations)]
                with (output / (label + ".log")).open("w", encoding="utf-8") as log:
                    subprocess.run(command, cwd=project, stdout=log, stderr=log, check=True, timeout=600)
                rows = read_csv(report)
                if len(rows) != args.rounds:
                    raise ValueError("Incomplete measurement rounds")
                for row in rows:
                    for quantity in ("ns_per_op", "checksum", "max_balance", "max_logfugacity"):
                        if not math.isfinite(float(row[quantity])):
                            raise ValueError(f"Non-finite {quantity}: {label}")
                    if float(row["max_balance"]) > 1e-10 or float(row["max_logfugacity"]) > 1e-8:
                        raise ValueError(f"Equilibrium residual failure: {label}")
                    combined.append({"fork": fork, "arm": arm, **row})
                state_rows = read_csv(Path(str(report) + ".states.csv"))
                normalized = [{key: value for key, value in row.items() if key != "sample"} for row in state_rows]
                key = (eos, str(size), operation, str(init), str(rule))
                if key in reference_states and normalized != reference_states[key]:
                    raise ValueError(f"Final state changed between arms/forks: {label}")
                if key not in reference_states:
                    reference_states[key] = normalized
                    states.extend(normalized)
                summary = {"fork": fork, "arm": arm, "eos": eos, "components": size,
                           "operation": operation, "init": init, "rule": rule,
                           "median_ns_per_op": statistics.median(float(row["ns_per_op"]) for row in rows),
                           "min_ns_per_op": min(float(row["ns_per_op"]) for row in rows),
                           "max_ns_per_op": max(float(row["ns_per_op"]) for row in rows)}
                summaries.append(summary)
                write_csv(output / "rounds.csv", combined)
                write_csv(output / "states.csv", states)
                write_csv(output / "forks.csv", summaries)
                print(json.dumps(summary), flush=True)
    environment["utc_finished"] = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
    environment["jvms"] = len(summaries)
    environment["state_groups"] = len(reference_states)
    environment["state_equality"] = "Exact CSV equality for all baseline/candidate/control forks within each EOS/size/scenario/init/rule"
    (output / "environment.json").write_text(json.dumps(environment, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
