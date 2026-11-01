"""Run alternating CLASSICAL/HV controls in fresh, serial JVMs.

Compile the two selected-pair diagnostic test sources and resolve the Maven test classpath
first. This runner preserves every round and process log; it makes no speedup or
physical-accuracy acceptance decision. Do not run it alongside another workload.
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
import subprocess
import time
from benchmark_huron_vidal_columns import artifact_fingerprint


ROOT = Path(__file__).resolve().parents[1]
BENCHMARK = "neqsim.thermo.mixingrule.HuronVidalSelectedPairPerformanceBenchmark"


def selections(forks, profiles, scenarios, sizes):
    """Alternate pair order and reverse profile/scenario order on odd forks."""
    for fork in range(forks):
        cases = [(profile, scenario, size) for profile in profiles for scenario in scenarios for size in sizes]
        if fork % 2:
            cases.reverse()
        for profile, scenario, size in cases:
            for rule in ((2, 4) if fork % 2 == 0 else (4, 2)):
                yield fork, profile, scenario, size, rule


def validate_evidence(rows, states, profile, rule, scenario, size):
    """Reconstruct every component balance and equilibrium residual; reject incomplete evidence."""
    names_order = ["water", "CO2", "methane", "methanol", "MEG", "ethane", "propane", "i-butane",
                   "n-butane", "i-pentane", "n-pentane", "n-hexane", "n-heptane", "n-octane", "nitrogen"]
    amounts = [.3, .2, .4, .05, .05] if size == 5 else [.3, .1, .3, .03, .02] + [.025] * 10
    expected_feed = dict(zip(names_order, amounts))
    if len(rows) != 10 or [int(row["round"]) for row in rows] != list(range(10)):
        raise ValueError("Incomplete measured rounds")
    for row in rows:
        if (int(row["profile"]), int(row["rule"]), row["scenario"], int(row["components"])) != (profile, rule, scenario, size):
            raise ValueError("Changed benchmark configuration")
        for key, bound in (("balance", 1e-10), ("normalization", 1e-12), ("log_fugacity", 1e-8)):
            value = float(row[key])
            if not math.isfinite(value) or not 0 <= value <= bound:
                raise ValueError("Invalid numerical receipt: " + key)
        if int(row["elapsed_ns"]) <= 0 or int(row["operations"]) != (50 if scenario == "flash" else 2000):
            raise ValueError("Invalid measured work")
        if int(row["allocated_bytes"]) < -1:
            raise ValueError("Invalid allocation receipt")
        expected_temperature = 353.15 + (.2 if scenario == "flash" else 0)
        if abs(float(row["T_K"]) - expected_temperature) > 1e-12 or float(row["P_bara"]) != 50:
            raise ValueError("Changed thermodynamic state")
        for key in ("T_K", "P_bara", "rich_water", "aqueous_CO2"):
            if not math.isfinite(float(row[key])):
                raise ValueError("Nonfinite endpoint receipt")
        topology = row["topology"].split("/")
        subset = [state for state in states if int(state["round"]) == int(row["round"])]
        if len(topology) < 2 or len(subset) != size * len(topology):
            raise ValueError("Incomplete phase/component roster")
        names = None
        phases = []
        for phase, phase_type in enumerate(topology):
            group = [state for state in subset if int(state["phase"]) == phase]
            roster = {state["component"] for state in group}
            if len(group) != size or len(roster) != size or (names is not None and roster != names):
                raise ValueError("Incomplete or duplicate components")
            names = roster
            if roster != set(expected_feed):
                raise ValueError("Changed component roster")
            values = {}
            for state in group:
                beta, z, x, logphi = [float(state[key]) for key in ("beta", "z", "x", "logphi")]
                if state["type"] != phase_type or not all(math.isfinite(v) for v in (beta, z, x, logphi)):
                    raise ValueError("Invalid phase evidence")
                if not 0 < beta < 1 or not 0 < x <= 1 or not 0 < z <= 1:
                    raise ValueError("Invalid physical fraction")
                if abs(z - expected_feed[state["component"]]) > 1e-15:
                    raise ValueError("Changed feed")
                values[state["component"]] = (z, x, logphi)
            betas = {float(state["beta"]) for state in group}
            if len(betas) != 1 or abs(math.fsum(v[1] for v in values.values()) - 1) > 1e-12:
                raise ValueError("Phase normalization failed")
            phases.append((beta, values))
        if abs(math.fsum(phase[0] for phase in phases) - 1) > 1e-12:
            raise ValueError("Beta normalization failed")
        if float(row["beta0"]) != phases[0][0]:
            raise ValueError("Changed beta receipt")
        for name in names:
            feed = {phase[1][name][0] for phase in phases}
            if len(feed) != 1:
                raise ValueError("Inconsistent feed")
            if abs(math.fsum(beta * values[name][1] for beta, values in phases) - next(iter(feed))) > 1e-10:
                raise ValueError("Component balance failed")
            logs = [math.log(values[name][1]) + values[name][2] for _, values in phases]
            if max(logs) - min(logs) > 1e-8:
                raise ValueError("Fugacity equality failed")
    if {int(state["round"]) for state in states} != set(range(10)):
        raise ValueError("Unexpected state round")


def main():
    """Save an execution manifest after every JVM, including failed processes."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--classpath-file", type=Path, required=True)
    parser.add_argument("--source-head", required=True, help="Verified source commit SHA; not inferred")
    parser.add_argument("--output", type=Path, required=True, help="New evidence directory")
    parser.add_argument("--forks", type=int, default=4)
    parser.add_argument("--profiles", type=int, nargs="+", default=[0, 1, 2], choices=[0, 1, 2])
    parser.add_argument("--scenarios", nargs="+", default=["init1", "init3", "flash"],
                        choices=["init1", "init3", "flash"])
    parser.add_argument("--sizes", type=int, nargs="+", default=[5, 15], choices=[5, 15])
    parser.add_argument("--plan-only", action="store_true", help="Inspect ordering without timing")
    args = parser.parse_args()
    if not re.fullmatch(r"[0-9a-f]{40}", args.source_head) or args.forks < 1:
        parser.error("Use a verified 40-character source SHA and positive fork count")
    if len(set(args.profiles)) != len(args.profiles) or len(set(args.scenarios)) != len(args.scenarios) or len(set(args.sizes)) != len(args.sizes):
        parser.error("Duplicate profiles or scenarios are not independent replications")
    plan = list(selections(args.forks, args.profiles, args.scenarios, args.sizes))
    if args.plan_only:
        print(json.dumps({"jvms": len(plan), "plan": plan}, indent=2))
        return
    classpath = os.pathsep.join([str(ROOT / "target/classes"), str(ROOT / "target/test-classes"),
                                 args.classpath_file.resolve().read_text().strip()])
    hashes = {}
    for name in ("HuronVidalCo2SourceDiagnosticTest", "HuronVidalSelectedPairPerformanceBenchmark"):
        for parent, suffix in (("src/test/java", ".java"), ("target/test-classes", ".class")):
            path = ROOT / parent / "neqsim/thermo/mixingrule" / (name + suffix)
            hashes[str(path.relative_to(ROOT))] = hashlib.sha256(path.read_bytes()).hexdigest()
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=False)
    manifest = {"declared_source_head": args.source_head, "source_and_class_sha256": hashes,
                "classpath_sha256": artifact_fingerprint(classpath.split(os.pathsep)),
                "matrix": {"forks": args.forks, "profiles": args.profiles,
                           "scenarios": args.scenarios, "sizes": args.sizes},
                "start_utc": datetime.now(timezone.utc).isoformat(), "platform": platform.platform(),
                "logical_cpus": os.cpu_count(), "planned_jvms": len(plan), "runs": [],
                "java_version": subprocess.run(["java", "-version"], capture_output=True,
                                               text=True, check=True).stderr.strip(),
                "interpretation": "Different model endpoints imply different solver work; no speedup acceptance"}
    java = ["java", "-Xms512m", "-Xmx512m", "-Xbatch", "-XX:CICompilerCount=2",
            "-cp", classpath, BENCHMARK]
    for fork, profile, scenario, size, rule in plan:
        stem = "fork%d_profile%d_%s_n%d_rule%d" % (fork, profile, scenario, size, rule)
        path = output / (stem + ".csv")
        command = java + [str(profile), str(rule), scenario, str(path), str(size)]
        start = time.monotonic()
        record = {"fork": fork, "profile": profile, "scenario": scenario, "rule": rule,
                  "components": size, "command": command}
        try:
            with (output / (stem + ".log")).open("w", encoding="utf-8") as log:
                result = subprocess.run(command, cwd=ROOT, stdout=log,
                                        stderr=subprocess.STDOUT, timeout=600, check=False)
            record["exit"] = result.returncode
            if result.returncode:
                raise RuntimeError("Failed benchmark " + stem)
            with path.open(newline="", encoding="utf-8") as stream:
                rows = list(csv.DictReader(stream))
            state_path = Path(str(path) + ".states.csv")
            with state_path.open(newline="", encoding="utf-8") as stream:
                states = list(csv.DictReader(stream))
            validate_evidence(rows, states, profile, rule, scenario, size)
            record["state_sha256"] = hashlib.sha256(state_path.read_bytes()).hexdigest()
            record["csv_sha256"] = hashlib.sha256(path.read_bytes()).hexdigest()
        except Exception as error:
            record["failure"] = str(error)
            raise
        finally:
            record["wall_seconds_including_startup"] = time.monotonic() - start
            manifest["runs"].append(record)
            (output / "execution.json").write_text(json.dumps(manifest, indent=2) + "\n")
        print("%d/%d %s" % (len(manifest["runs"]), len(plan), stem), flush=True)
    manifest["finish_utc"] = datetime.now(timezone.utc).isoformat()
    (output / "execution.json").write_text(json.dumps(manifest, indent=2) + "\n")


if __name__ == "__main__":
    main()
