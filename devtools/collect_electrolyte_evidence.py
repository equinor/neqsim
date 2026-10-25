#!/usr/bin/env python3
"""Retain allowlisted #4234 assessment outputs without changing their contents.

This is an evidence-packaging check, not a physical-accuracy qualification. It
records missing, skipped and failed producers explicitly, including on failed
Maven runs. No directory-wide collection or numerical tolerance is introduced.
"""

import argparse
import csv
import hashlib
import io
import json
import os
from pathlib import Path
import platform
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET


PACKAGE = "neqsim.thermodynamicoperations.flashops.saturationops."
SOURCE_DIR = "src/test/java/neqsim/thermodynamicoperations/flashops/saturationops/"
FIXTURE_DIR = "src/test/resources/data/chemistry_benchmarks/"
ASSESSMENTS = (
    ("CO2KClHydrateAccuracyAssessmentTest",
     "co2-kcl-hydrate-dholabhai1993-assessment.csv", 28,
     "co2_kcl_hydrate_dholabhai1993.csv"),
    ("KClWaterActivityReferenceAssessmentTest",
     "kcl-water-activity-archer-assessment.csv", 10, None),
    ("KClCO2SolubilityReferenceAssessmentTest",
     "kcl-co2-solubility-he-morse1993-assessment.csv", 26,
     "co2_kcl_solubility_he_morse1993.csv"),
    ("KClCO2OstwaldReferenceAssessmentTest",
     "kcl-co2-ostwald-yasunishi-yoshida1979-assessment.csv", 7,
     "co2_kcl_ostwald_yasunishi_yoshida1979.csv"),
    ("CO2BrineHydrateReferenceAssessmentTest",
     "co2-brine-hydrate-reference-assessment.csv", 12,
     "co2_brine_hydrate_phase_state_burgass2023.csv"),
    ("CO2WaterBaselineReferenceAssessmentTest",
     "co2-water-baseline-carroll1991-assessment.csv", 57,
     "co2_water_carroll1991_evaluation.csv"),
    ("CO2BrineHydrateFullFamilyAssessmentTest",
     "co2-brine-hydrate-dholabhai1993-full-assessment.csv", 110,
     "co2_brine_hydrate_dholabhai1993_full.csv"),
    ("CO2BrineHydrateMeasuredStateAssessmentTest",
     "co2-brine-hydrate-measured-state-assessment.csv", 110,
     "co2_brine_hydrate_dholabhai1993_full.csv"),
)
PROVENANCE = "docs/thermo/co2_kcl_interaction_provenance.md"


def digest(path):
    """Hash one regular, non-symlink file without interpreting its data."""
    if path.is_symlink() or not path.is_file():
        raise ValueError("Not a regular evidence file: {}".format(path))
    return hashlib.sha256(path.read_bytes()).hexdigest()


def csv_shape(path):
    """Read structural metadata only; preserve NaN/not-applicable fields."""
    rows = list(csv.reader(io.StringIO(path.read_bytes().decode("utf-8-sig"))))
    if not rows or not rows[0] or any(not name for name in rows[0]):
        raise ValueError("CSV header is missing or empty")
    if len(set(rows[0])) != len(rows[0]):
        raise ValueError("CSV has duplicate column names")
    if any(len(row) != len(rows[0]) for row in rows[1:]):
        raise ValueError("CSV row width differs from its header")
    return {"columns": rows[0], "data_rows": len(rows) - 1}


def producer_result(path, class_name):
    """Read the exact Surefire suite, never infer success from a CSV alone."""
    if path.is_symlink():
        raise ValueError("Surefire report is a symlink")
    if not path.is_file():
        return {"status": "not_completed"}
    suite = ET.parse(path).getroot()
    if suite.tag != "testsuite" or suite.get("name") != class_name:
        raise ValueError("Surefire suite identity does not match producer")
    counts = {key: int(suite.attrib[key])
              for key in ("tests", "failures", "errors", "skipped")}
    if any(value < 0 for value in counts.values()):
        raise ValueError("Surefire counts must be non-negative")
    if counts["failures"] or counts["errors"]:
        status = "failed"
    elif counts["tests"] == 0 or counts["skipped"]:
        status = "not_fully_executed"
    else:
        status = "passed"
    runtime = {item.get("name"): item.get("value")
               for item in suite.findall("./properties/property")
               if item.get("name") in ("java.version", "java.vendor", "java.vm.name", "os.name")}
    return dict(counts, status=status, runtime=runtime)


def collect(root, output, selected, context, checkout_sha):
    """Create one new shard bundle and return its manifest and packaging errors."""
    if not re.fullmatch(r"[0-9a-f]{40}", checkout_sha):
        raise ValueError("Actual checkout SHA must be a full Git commit SHA")
    # Never reuse a previous bundle: stale evidence must not survive a rerun.
    output.mkdir(parents=True, exist_ok=False)
    errors = []
    manifest = {
        "schema_version": 1,
        "checkout_sha": checkout_sha,
        "context": context,
        "collector_python": platform.python_version(),
        "selection_available": selected is not None,
        "selected_test_classes": sorted(selected) if selected is not None else None,
        "qualification": "not_assessed",
        "assessments": [],
    }
    for producer, filename, expected_rows, fixture in ASSESSMENTS:
        class_name = PACKAGE + producer
        entry = {
            "producer": class_name, "output": filename,
            "expected_data_rows": expected_rows,
            "selected": None if selected is None else class_name in selected,
            "output_present": False,
        }
        manifest["assessments"].append(entry)
        if selected is None or class_name not in selected:
            entry["producer_result"] = {"status": "selection_unknown" if selected is None
                                        else "not_selected"}
            # Ignore leftover outputs and reports from tests not in this shard.
            continue
        try:
            xml_path = root / "target/surefire-reports" / ("TEST-" + class_name + ".xml")
            entry["producer_result"] = producer_result(xml_path, class_name)
            if xml_path.is_file():
                entry["surefire_sha256"] = digest(xml_path)
            source = SOURCE_DIR + producer + ".java"
            entry["producer_source"] = {"path": source, "sha256": digest(root / source)}
            entry["provenance"] = {"path": PROVENANCE, "sha256": digest(root / PROVENANCE)}
            if fixture:
                fixture_path = FIXTURE_DIR + fixture
                entry["fixture"] = {"path": fixture_path,
                                    "sha256": digest(root / fixture_path)}
            else:
                entry["fixture"] = {"path": source, "basis": "inline reference values"}
            report = root / "target" / filename
            if report.is_symlink():
                raise ValueError("Assessment output is a symlink")
            if report.is_file():
                # Copy before structural validation to retain malformed/partial
                # output on failed runs too. Never rewrite its values or header.
                shutil.copyfile(report, output / filename)
                entry.update(output_present=True, sha256=digest(report),
                             size_bytes=report.stat().st_size)
                entry.update(csv_shape(report))
                if entry["data_rows"] != expected_rows:
                    raise ValueError("Expected {} data rows, found {}".format(
                        expected_rows, entry["data_rows"]))
            elif entry["producer_result"]["status"] == "passed":
                raise ValueError("Passed assessment did not produce its CSV")
        except (ValueError, OSError, UnicodeError, csv.Error, ET.ParseError, KeyError) as exc:
            entry["packaging_error"] = str(exc)
            errors.append("{}: {}".format(producer, exc))

    selected_entries = [entry for entry in manifest["assessments"] if entry["selected"]]
    manifest["selected_evidence_complete"] = (
        selected is not None and all(
            entry.get("producer_result", {}).get("status") == "passed"
            and entry["output_present"] and "packaging_error" not in entry
            for entry in selected_entries
        )
    )
    manifest["selected_assessment_count"] = len(selected_entries)
    manifest["packaging_errors"] = errors
    (output / "manifest.json").write_text(
        json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    return manifest, errors


def main():
    """Collect only fixed #4234 outputs from the current checked-out repository."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--selected-tests", type=Path, required=True)
    parser.add_argument("--shard", required=True)
    args = parser.parse_args()
    root = Path(__file__).resolve().parent.parent
    checkout_sha = subprocess.check_output(
        ["git", "rev-parse", "HEAD"], cwd=root, text=True).strip()
    selected = (set(args.selected_tests.read_text(encoding="utf-8").splitlines())
                if args.selected_tests.is_file() else None)
    context = {key: os.environ.get(key) for key in (
        "GITHUB_REPOSITORY", "GITHUB_SHA", "GITHUB_EVENT_NAME", "GITHUB_REF",
        "GITHUB_RUN_ID", "GITHUB_RUN_ATTEMPT", "GITHUB_JOB", "JAVA_HOME",
        "NEQSIM_PR_HEAD_SHA")}
    context["shard"] = args.shard
    try:
        _, errors = collect(root, root / "target/electrolyte-evidence", selected,
                            context, checkout_sha)
    except (ValueError, OSError) as exc:
        print("Evidence collection failed: {}".format(exc), file=sys.stderr)
        return 1
    for error in errors:
        print(error, file=sys.stderr)
    return 1 if errors else 0


if __name__ == "__main__":
    raise SystemExit(main())
