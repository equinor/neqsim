"""Run isolated, alternating JVMs for the manual HV ownership/cost matrix.

Compile the same benchmark harness once and supply baseline/candidate main-class
directories built with the same dependencies. This runner performs no remote access
and does not treat elapsed time as a CI assertion or physical-accuracy criterion.
"""

import argparse
import hashlib
import itertools
import json
import os
from pathlib import Path
import platform
import subprocess
import time


def main():
    """Execute every selected cell in ABBA/BAAB order and preserve raw rounds."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline-classes", type=Path, required=True)
    parser.add_argument("--candidate-classes", type=Path, default=Path("target/classes"))
    parser.add_argument("--test-classes", type=Path, default=Path("target/test-classes"))
    parser.add_argument("--classpath-file", type=Path, default=Path("target/hv-classpath.txt"))
    parser.add_argument("--output", type=Path, default=Path("target/hv-clone-performance/full"))
    parser.add_argument("--baseline-label", required=True)
    parser.add_argument("--warmups", type=int, default=16)
    parser.add_argument("--rounds", type=int, default=8)
    parser.add_argument("--init-iterations", type=int, default=200)
    parser.add_argument("--flash-iterations", type=int, default=8)
    parser.add_argument("--eos", nargs="+", choices=["SRK", "PR"], default=["SRK", "PR"])
    parser.add_argument("--sizes", nargs="+", type=int, choices=[4, 20], default=[4, 20])
    parser.add_argument("--operations", nargs="+", choices=["clone", "phase", "flash", "clone_flash"],
                        default=["clone", "phase", "flash", "clone_flash"])
    parser.add_argument("--init-levels", nargs="+", type=int, choices=[1, 3], default=[1, 3])
    parser.add_argument("--rules", nargs="+", type=int, choices=[2, 4], default=[2, 4])
    args = parser.parse_args()
    if min(args.warmups, args.init_iterations, args.flash_iterations) < 1 or args.rounds < 2:
        parser.error("Positive warmups/iterations and at least two measured rounds are required")
    args.output.mkdir(parents=True, exist_ok=True)
    if (args.output / "rounds.csv").exists():
        parser.error("Output already has rounds.csv; use a new directory to preserve run provenance")
    dependencies = args.classpath_file.read_text().strip()
    roots = {"baseline": args.baseline_classes, "candidate": args.candidate_classes}
    for directory in list(roots.values()) + [args.test_classes]:
        if not directory.is_dir():
            parser.error("Missing compiled class directory: " + str(directory))
    harness = args.test_classes / "neqsim/thermo/mixingrule/HuronVidalClonePerformanceBenchmark.class"
    if not harness.is_file():
        parser.error("Missing compiled benchmark harness: " + str(harness))
    fixture_paths = [
        "src/main/java/neqsim/thermo/component/ComponentGENRTLmodifiedHV.java",
        "src/main/java/neqsim/thermo/component/attractiveeosterm/AttractiveTermBaseClass.java",
        "src/main/java/neqsim/thermo/mixingrule/EosMixingRuleHandler.java",
        "src/main/java/neqsim/thermo/phase/PhaseEos.java",
        "src/main/java/neqsim/thermo/phase/PhaseGENRTLmodifiedHV.java",
        "src/test/java/neqsim/thermo/mixingrule/HuronVidalClonePerformanceBenchmark.java",
    ]
    manifest = {
        "baseline": args.baseline_label,
        "java_options": ["-Xms128m", "-Xmx512m"],
        "java_version": subprocess.run(
            ["java", "-version"], capture_output=True, text=True, check=True
        ).stderr.strip(),
        "platform": platform.platform(),
        "warmups": args.warmups,
        "rounds": args.rounds,
        "jvms_per_configuration": 4,
        "benchmark_class_sha256": hashlib.sha256(harness.read_bytes()).hexdigest(),
        "candidate_sources_sha256": {
            path: hashlib.sha256(Path(path).read_bytes()).hexdigest()
            for path in fixture_paths
        },
        "results": [],
    }
    configs = list(itertools.product(
        args.eos, args.sizes, args.operations, args.init_levels, args.rules
    ))
    start = time.monotonic()
    for index, (eos, size, operation, init, rule) in enumerate(configs):
        order = ["baseline", "candidate", "candidate", "baseline"]
        if index % 2:
            order = ["candidate", "baseline", "baseline", "candidate"]
        iterations = args.init_iterations if operation in ("clone", "phase") else args.flash_iterations
        for fork, label in enumerate(order):
            key = f"{eos}-{size}-{operation}-{init}-{rule}-{fork}-{label}"
            classpath = os.pathsep.join([
                str(roots[label].resolve()), str(args.test_classes.resolve()), dependencies
            ])
            command = [
                "java", *manifest["java_options"], "-cp", classpath,
                "neqsim.thermo.mixingrule.HuronVidalClonePerformanceBenchmark",
                f"{label}-{fork}", str((args.output / "rounds.csv").resolve()),
                eos, str(size), operation, str(init), str(rule),
                str(args.warmups), str(args.rounds), str(iterations),
            ]
            with (args.output / (key + ".log")).open("w") as log:
                result = subprocess.run(command, stdout=log, stderr=subprocess.STDOUT, timeout=240)
            manifest["results"].append({"key": key, "exit": result.returncode, "iterations": iterations})
            (args.output / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
            if result.returncode:
                raise SystemExit("Benchmark failed; inspect " + key + ".log")
            if not (args.output / "rounds.csv.states.csv").exists():
                raise SystemExit("Harness did not record final states; rebuild it from the current source")
        print(
            f"{index + 1}/{len(configs)} {eos} {size} {operation} init{init} rule{rule} "
            f"elapsed={time.monotonic() - start:.1f}s", flush=True
        )


if __name__ == "__main__":
    main()
