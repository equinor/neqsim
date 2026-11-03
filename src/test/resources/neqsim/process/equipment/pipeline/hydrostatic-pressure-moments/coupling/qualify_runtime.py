"""Run the RHS-only benchmark with the classpath from the completed focused Maven test.

This is observational runtime evidence, not a full timestep or phase-transition benchmark.
Optional --override-classes lets a separately compiled original operator precede current classes.
"""
import argparse
import csv
import json
import os
from pathlib import Path
import statistics
import subprocess
import tempfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent
REPO = next(path for path in ROOT.parents if (path / "pom.xml").is_file())


def main():
    """Retain every timing sample and summarize disabled and enabled operator costs."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--override-classes", type=Path)
    parser.add_argument("--prefix", default="runtime")
    args = parser.parse_args()
    report = REPO / "target/surefire-reports/TEST-neqsim.process.equipment.pipeline.twophasepipe.StratifiedPressureMomentCouplingTest.xml"
    properties = {node.get("name"): node.get("value") for node in ET.parse(report).getroot().find("properties")}
    classpath = properties["java.class.path"]
    if args.override_classes:
        classpath = str(args.override_classes.resolve()) + os.pathsep + classpath
    csv_path = ROOT / (args.prefix + ".csv")
    with tempfile.TemporaryDirectory() as temporary:
        subprocess.run(["java", "--module", "jdk.compiler/com.sun.tools.javac.Main", "--release", "8",
                        "-cp", classpath, "-d", temporary, str(ROOT / "PressureMomentRuntimeProbe.java")], check=True)
        subprocess.run(["java", "-XX:-UsePerfData", "-cp", temporary + os.pathsep + classpath,
                        "PressureMomentRuntimeProbe", str(csv_path)], check=True)
    with csv_path.open() as stream:
        rows = list(csv.DictReader(stream))
    if len(rows) != 18:
        raise ValueError("Runtime matrix incomplete")
    summary = {"cells": 24, "warmup_per_mode": 1500, "repetitions_per_sample": 1000,
               "samples_per_mode": 9, "scope": "RHS only; observational, no complete-timestep speed claim"}
    for mode in ["false", "true"]:
        values = [float(row["ns_per_rhs"]) for row in rows if row["enabled"] == mode]
        summary["enabled_" + mode + "_ns"] = {"minimum": min(values), "median": statistics.median(values),
                                              "maximum": max(values)}
    (ROOT / (args.prefix + "-summary.json")).write_text(json.dumps(summary, indent=2) + "\n")
    print(json.dumps(summary, indent=2))


if __name__ == "__main__":
    main()
