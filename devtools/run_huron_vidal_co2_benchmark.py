"""Run alternating CLASSICAL/HV controls in fresh, serial JVMs.

Compile the two CO2 diagnostic test sources and resolve the Maven test classpath
first. This runner preserves every round and process log; it makes no speedup or
physical-accuracy acceptance decision. Do not run it alongside another workload.
"""

import argparse
import csv
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import platform
import re
import subprocess
import time


ROOT = Path(__file__).resolve().parents[1]
BENCHMARK = "neqsim.thermo.mixingrule.HuronVidalCo2SourcePerformanceBenchmark"


def selections(forks, profiles, scenarios):
    """Alternate pair order and reverse profile/scenario order on odd forks."""
    for fork in range(forks):
        cases = [(profile, scenario) for profile in profiles for scenario in scenarios]
        if fork % 2:
            cases.reverse()
        for profile, scenario in cases:
            for rule in ((2, 4) if fork % 2 == 0 else (4, 2)):
                yield fork, profile, scenario, rule


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
    parser.add_argument("--plan-only", action="store_true", help="Inspect ordering without timing")
    args = parser.parse_args()
    if not re.fullmatch(r"[0-9a-f]{40}", args.source_head) or args.forks < 1:
        parser.error("Use a verified 40-character source SHA and positive fork count")
    if len(set(args.profiles)) != len(args.profiles) or len(set(args.scenarios)) != len(args.scenarios):
        parser.error("Duplicate profiles or scenarios are not independent replications")
    plan = list(selections(args.forks, args.profiles, args.scenarios))
    if args.plan_only:
        print(json.dumps({"jvms": len(plan), "plan": plan}, indent=2))
        return
    classpath = os.pathsep.join([str(ROOT / "target/classes"), str(ROOT / "target/test-classes"),
                                 args.classpath_file.resolve().read_text().strip()])
    hashes = {}
    for name in ("HuronVidalCo2SourceDiagnosticTest", "HuronVidalCo2SourcePerformanceBenchmark"):
        for parent, suffix in (("src/test/java", ".java"), ("target/test-classes", ".class")):
            path = ROOT / parent / "neqsim/thermo/mixingrule" / (name + suffix)
            hashes[str(path.relative_to(ROOT))] = hashlib.sha256(path.read_bytes()).hexdigest()
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=False)
    manifest = {"declared_source_head": args.source_head, "source_and_class_sha256": hashes,
                "start_utc": datetime.now(timezone.utc).isoformat(), "platform": platform.platform(),
                "logical_cpus": os.cpu_count(), "planned_jvms": len(plan), "runs": [],
                "java_version": subprocess.run(["java", "-version"], capture_output=True,
                                               text=True, check=True).stderr.strip(),
                "interpretation": "Different model endpoints imply different solver work; no speedup acceptance"}
    java = ["java", "-Xms512m", "-Xmx512m", "-Xbatch", "-XX:CICompilerCount=2",
            "-cp", classpath, BENCHMARK]
    for fork, profile, scenario, rule in plan:
        stem = "fork%d_profile%d_%s_rule%d" % (fork, profile, scenario, rule)
        path = output / (stem + ".csv")
        command = java + [str(profile), str(rule), scenario, str(path)]
        start = time.monotonic()
        record = {"fork": fork, "profile": profile, "scenario": scenario, "rule": rule,
                  "command": command}
        try:
            with (output / (stem + ".log")).open("w", encoding="utf-8") as log:
                result = subprocess.run(command, cwd=ROOT, stdout=log,
                                        stderr=subprocess.STDOUT, timeout=180, check=False)
            record["exit"] = result.returncode
            if result.returncode:
                raise RuntimeError("Failed benchmark " + stem)
            with path.open(newline="", encoding="utf-8") as stream:
                rows = list(csv.DictReader(stream))
            if len(rows) != 10 or [int(row["round"]) for row in rows] != list(range(10)):
                raise ValueError("Incomplete measured rounds: " + stem)
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
