"""Compare frozen modified-NRTL binaries with alternating independent JVM controls.

Uses the existing selected-pair harness and numerical auditor. No timing threshold
or empirical accuracy acceptance is inferred from a model-cost comparison.
"""

import argparse
import csv
from datetime import datetime, timezone
import hashlib
import json
import math
import os
from pathlib import Path
import platform
import re
import statistics
import subprocess

from benchmark_huron_vidal_columns import artifact_fingerprint
from run_huron_vidal_selected_pair_benchmark import validate_evidence

BENCHMARK = "neqsim.thermo.mixingrule.HuronVidalSelectedPairPerformanceBenchmark"
COMPONENT_CLASS = "neqsim/thermo/component/ComponentGENRTLmodifiedHV.class"
FLAGS = ["-Xms512m", "-Xmx512m", "-Xbatch", "-XX:CICompilerCount=2"]


def plan(matrix):
    """Freeze every process, reversing case and binary order on alternate forks."""
    for fork in range(matrix["forks"]):
        cases = [(profile, size, scenario, rule)
                 for profile in matrix["profiles"] for size in matrix["sizes"]
                 for scenario in matrix["scenarios"] for rule in (2, 4)]
        if fork % 2:
            cases.reverse()
        for profile, size, scenario, rule in cases:
            labels = ("base", "candidate", "control") if fork % 2 == 0 else ("control", "candidate", "base")
            for label in labels:
                yield fork, profile, size, scenario, rule, label


def stem(key):
    """Return a unique process identity without relying on caller file names."""
    fork, profile, size, scenario, rule, label = key
    return "f%d_p%d_n%d_%s_r%d_%s" % (fork, profile, size, scenario, rule, label)


def sha(data):
    """Hash bytes with the recorded SHA256 convention."""
    return hashlib.sha256(data).hexdigest()


def audit(manifest, read):
    """Validate the complete ordered process roster, hashes, work and numerical states."""
    matrix = manifest["matrix"]
    if matrix["forks"] < 2 or not re.fullmatch(r"[0-9a-f]{40}", manifest["baseline_head"]):
        raise ValueError("Invalid replication or baseline identity")
    for field, allowed in (("profiles", {0, 1, 2}), ("sizes", {5, 15}),
                           ("scenarios", {"init1", "init3", "flash"})):
        values = matrix[field]
        if not values or len(values) != len(set(values)) or not set(values) <= allowed:
            raise ValueError("Invalid or duplicate matrix dimensions")
    hashes = manifest["component_class_sha256"]
    if (set(hashes) != {"base", "candidate", "control"}
            or any(not re.fullmatch(r"[0-9a-f]{64}", value) for value in hashes.values())
            or hashes["base"] != hashes["control"]):
        raise ValueError("Invalid identical-binary control")
    classpaths = manifest["classpath_sha256"]
    if (set(classpaths) != {"base", "candidate", "control"}
            or any(not re.fullmatch(r"[0-9a-f]{64}", value) for value in classpaths.values())
            or classpaths["base"] != classpaths["control"]
            or manifest["flags"] != FLAGS
            or not re.fullmatch(r"[0-9a-f]{64}", manifest["driver_sha256"])):
        raise ValueError("Invalid frozen runtime identity")
    expected = list(plan(matrix))
    runs = manifest["runs"]
    if ("finish_utc" not in manifest or manifest["planned_jvms"] != len(expected)
            or [tuple(run["key"]) for run in runs] != expected):
        raise ValueError("Incomplete or reordered process roster")
    centers = {}
    endpoints = {}
    maxima = dict(balance=0.0, normalization=0.0, log_fugacity=0.0)
    operations = 0
    state_rows = 0
    max_difference = 0.0
    for key, run in zip(expected, runs):
        if run["exit"] != 0:
            raise ValueError("Failed process")
        raw = read(stem(key) + ".csv")
        raw_states = read(stem(key) + ".csv.states.csv")
        log = read(stem(key) + ".log")
        for field, data in (("csv_sha256", raw), ("states_sha256", raw_states), ("log_sha256", log)):
            if sha(data) != run[field]:
                raise ValueError("Changed process evidence")
        rows = list(csv.DictReader(raw.decode().splitlines()))
        states = list(csv.DictReader(raw_states.decode().splitlines()))
        fork, profile, size, scenario, rule, label = key
        validate_evidence(rows, states, profile, rule, scenario, size)
        state_key = profile, size, scenario, rule
        vector = {(int(x["round"]), int(x["phase"]), x["type"], x["component"]):
                  tuple(float(x[field]) for field in ("beta", "z", "x", "logphi")) for x in states}
        if state_key in endpoints:
            reference = endpoints[state_key]
            if set(reference) != set(vector):
                raise ValueError("Changed same-case phase/component roster")
            difference = max(abs(a - b) for name in reference for a, b in zip(reference[name], vector[name]))
            if not math.isfinite(difference) or difference > 1e-12:
                raise ValueError("Changed baseline/candidate/control equilibrium")
            max_difference = max(max_difference, difference)
        else:
            endpoints[state_key] = vector
        centers[key] = {
            "ns": statistics.median(int(x["elapsed_ns"]) / int(x["operations"]) for x in rows),
            "bytes": statistics.median(int(x["allocated_bytes"]) / int(x["operations"]) for x in rows)
            if all(int(x["allocated_bytes"]) >= 0 for x in rows) else None}
        for field in maxima:
            maxima[field] = max(maxima[field], max(float(x[field]) for x in rows))
        operations += sum(int(x["operations"]) for x in rows)
        state_rows += len(states)
    cells = []
    for profile in matrix["profiles"]:
        for size in matrix["sizes"]:
            for scenario in matrix["scenarios"]:
                for rule in (2, 4):
                    groups = {label: [centers[fork, profile, size, scenario, rule, label]
                                      for fork in range(matrix["forks"])]
                              for label in ("base", "candidate", "control")}
                    ratios = {a + "_over_" + b: [x["ns"] / y["ns"] for x, y in zip(groups[a], groups[b])]
                              for a, b in (("candidate", "base"), ("candidate", "control"), ("control", "base"))}
                    cells.append({"profile": profile, "size": size, "scenario": scenario, "rule": rule,
                                  "centers": groups, "ratios": ratios})
    return {"jvms": len(expected), "rounds": len(expected) * 10, "operations": operations,
            "phase_component_rows": state_rows, "max_residuals": maxima,
            "max_same_case_difference": max_difference, "cells": cells,
            "interpretation": "Bounded code comparisons; retain fork/control variability; no empirical accuracy gate"}


def main():
    """Run or re-audit the same frozen matrix without overwriting prior evidence."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--audit", type=Path)
    parser.add_argument("--baseline-classes", type=Path)
    parser.add_argument("--candidate-classes", type=Path)
    parser.add_argument("--test-classes", type=Path)
    parser.add_argument("--classpath-file", type=Path)
    parser.add_argument("--baseline-head")
    parser.add_argument("--output", type=Path)
    parser.add_argument("--forks", type=int, default=2)
    parser.add_argument("--profiles", type=int, nargs="+", default=[0, 1, 2])
    parser.add_argument("--sizes", type=int, nargs="+", default=[5, 15])
    parser.add_argument("--scenarios", nargs="+", default=["init1", "init3", "flash"])
    args = parser.parse_args()
    if args.audit:
        directory = args.audit.resolve()
        manifest = json.loads((directory / "execution.json").read_text())
    else:
        if any(value is None for value in (args.baseline_classes, args.candidate_classes, args.test_classes,
                                          args.classpath_file, args.baseline_head, args.output)):
            parser.error("Supply frozen class directories, classpath file, baseline head and new output directory")
        if args.forks < 2 or not re.fullmatch(r"[0-9a-f]{40}", args.baseline_head):
            parser.error("Require at least two forks and a verified baseline SHA")
        matrix = {"forks": args.forks, "profiles": args.profiles, "sizes": args.sizes, "scenarios": args.scenarios}
        for field, allowed in (("profiles", {0, 1, 2}), ("sizes", {5, 15}),
                               ("scenarios", {"init1", "init3", "flash"})):
            values = matrix[field]
            if not values or len(values) != len(set(values)) or not set(values) <= allowed:
                parser.error("Invalid or duplicate matrix dimensions")
        classes = {"base": args.baseline_classes.resolve(), "candidate": args.candidate_classes.resolve(),
                   "control": args.baseline_classes.resolve()}
        hashes = {label: sha((path / COMPONENT_CLASS).read_bytes()) for label, path in classes.items()}
        dependencies = args.classpath_file.resolve().read_text().strip()
        classpaths = {label: [str(path), str(args.test_classes.resolve())] + dependencies.split(os.pathsep)
                      for label, path in classes.items()}
        fingerprints = {label: artifact_fingerprint(paths) for label, paths in classpaths.items()}
        directory = args.output.resolve()
        directory.mkdir(parents=True, exist_ok=False)
        manifest = {"baseline_head": args.baseline_head, "matrix": matrix, "flags": FLAGS,
                    "component_class_sha256": hashes, "classpath_sha256": fingerprints,
                    "platform": platform.platform(), "java": subprocess.check_output(
                        ["java", "-version"], stderr=subprocess.STDOUT, text=True),
                    "driver_sha256": sha(Path(__file__).read_bytes()),
                    "start_utc": datetime.now(timezone.utc).isoformat(),
                    "planned_jvms": len(list(plan(matrix))), "runs": []}
        (directory / "execution.json").write_text(json.dumps(manifest, indent=2) + "\n")
        for key in plan(matrix):
            fork, profile, size, scenario, rule, label = key
            if sha((classes[label] / COMPONENT_CLASS).read_bytes()) != hashes[label]:
                raise ValueError("Changed binary during experiment")
            path = directory / (stem(key) + ".csv")
            command = ["java"] + FLAGS + ["-cp", os.pathsep.join(classpaths[label]), BENCHMARK,
                      str(profile), str(rule), scenario, str(path), str(size)]
            try:
                with (directory / (stem(key) + ".log")).open("wb") as log:
                    result = subprocess.run(command, stdout=log, stderr=subprocess.STDOUT, timeout=600)
            except subprocess.TimeoutExpired:
                manifest["runs"].append({"key": key, "exit": None, "failure": "JVM exceeded 600 seconds"})
                (directory / "execution.json").write_text(json.dumps(manifest, indent=2) + "\n")
                raise
            run = {"key": key, "exit": result.returncode}
            manifest["runs"].append(run)
            if result.returncode == 0:
                for field, suffix in (("csv_sha256", ".csv"), ("states_sha256", ".csv.states.csv"),
                                      ("log_sha256", ".log")):
                    run[field] = sha((directory / (stem(key) + suffix)).read_bytes())
                try:
                    validate_evidence(list(csv.DictReader(path.open())), list(csv.DictReader(
                        Path(str(path) + ".states.csv").open())), profile, rule, scenario, size)
                except Exception as error:
                    run["failure"] = str(error)
                    (directory / "execution.json").write_text(json.dumps(manifest, indent=2) + "\n")
                    raise
            (directory / "execution.json").write_text(json.dumps(manifest, indent=2) + "\n")
            if result.returncode:
                raise RuntimeError("Failed process: " + stem(key))
            print(json.dumps({"completed": len(manifest["runs"]), "planned": manifest["planned_jvms"],
                              "case": key}), flush=True)
        if fingerprints != {label: artifact_fingerprint(paths) for label, paths in classpaths.items()}:
            raise ValueError("Changed full classpath during experiment")
        manifest["finish_utc"] = datetime.now(timezone.utc).isoformat()
        (directory / "execution.json").write_text(json.dumps(manifest, indent=2) + "\n")
    summary = audit(manifest, lambda name: (directory / name).read_bytes())
    (directory / "summary.json").write_text(json.dumps(summary, indent=2) + "\n")
    print(json.dumps({key: value for key, value in summary.items() if key != "cells"}, indent=2))


if __name__ == "__main__":
    main()
