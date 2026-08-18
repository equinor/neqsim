#!/usr/bin/env python3
"""Validate a generated NeqSim DEXPI package with optional external importers."""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET


DEXPI_VIEWER_REPOSITORY = "https://github.com/ToniaPedersen/DEXPIViewer"
DEXPI_VIEWER_COMMIT = "18a17b1e38ba15a1a6ba49dd8265ddcff7c766ad"


def sha256_file(path: Path) -> str:
    """Return a lowercase SHA-256 digest for a file."""
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def parse_dexpi_viewer_csv(path: Path) -> list[dict]:
    """Parse DEXPIViewer's documented CSV format into deterministic findings."""
    findings = []
    with path.open("r", encoding="utf-8-sig", newline="") as stream:
        reader = csv.DictReader(stream)
        required = {
            "Object ID",
            "Object Type",
            "Rule ID",
            "Severity",
            "Rule Description",
            "Location (XPath)",
            "Profile Source",
            "Suggested Correction",
        }
        missing = sorted(required.difference(reader.fieldnames or []))
        if missing:
            raise ValueError(f"DEXPIViewer CSV is missing columns: {', '.join(missing)}")
        for row in reader:
            finding = {
                "objectId": row["Object ID"],
                "objectType": row["Object Type"],
                "ruleId": row["Rule ID"],
                "severity": row["Severity"],
                "description": row["Rule Description"],
                "location": row["Location (XPath)"],
                "profileSource": row["Profile Source"],
                "suggestedCorrection": row["Suggested Correction"],
            }
            line_number = row.get("Line Number", "").strip()
            if line_number:
                try:
                    finding["lineNumber"] = int(line_number)
                except ValueError:
                    finding["lineNumber"] = line_number
            findings.append(finding)
    severity_order = {"Error": 0, "Warning": 1, "Info": 2}
    return sorted(
        findings,
        key=lambda item: (
            severity_order.get(item["severity"], 3),
            item["ruleId"],
            item["objectId"],
            item["location"],
        ),
    )


def _checkout_head(repository: Path) -> str:
    try:
        result = subprocess.run(
            ["git", "-C", str(repository), "rev-parse", "HEAD"],
            check=False,
            capture_output=True,
            text=True,
        )
    except OSError as exc:
        raise ValueError("git is required to verify the DEXPIViewer checkout") from exc
    if result.returncode != 0:
        raise ValueError("DEXPIViewer directory must be a Git checkout with a readable HEAD")
    return result.stdout.strip().lower()


def run_dexpi_viewer(native: Path, repository: Path, expected_commit: str) -> dict:
    """Run the pinned DEXPIViewer CLI and retain its complete structured findings."""
    repository = repository.resolve()
    cli = repository / "validate-cli.js"
    if not cli.is_file():
        raise ValueError(f"DEXPIViewer CLI was not found: {cli}")
    actual_commit = _checkout_head(repository)
    expected_commit = expected_commit.lower()
    if actual_commit != expected_commit:
        raise ValueError(
            "DEXPIViewer checkout mismatch: "
            f"expected {expected_commit}, found {actual_commit}"
        )
    with tempfile.TemporaryDirectory(prefix="neqsim-dexpi-viewer-") as temporary:
        output_directory = Path(temporary)
        try:
            completed = subprocess.run(
                [
                    "node",
                    str(cli),
                    str(native.resolve()),
                    "--out",
                    str(output_directory),
                ],
                cwd=repository,
                check=False,
                capture_output=True,
                text=True,
            )
        except OSError as exc:
            raise ValueError("Node.js is required to run DEXPIViewer") from exc
        if completed.returncode not in (0, 1):
            detail = (completed.stderr or completed.stdout).strip()
            raise ValueError(
                f"DEXPIViewer execution failed with exit code {completed.returncode}: {detail}"
            )
        csv_path = output_directory / f"{native.stem}.csv"
        if not csv_path.is_file():
            raise ValueError("DEXPIViewer did not produce its documented CSV report")
        findings = parse_dexpi_viewer_csv(csv_path)
        counts = {
            severity.lower() + "s": sum(
                1 for finding in findings if finding["severity"] == severity
            )
            for severity in ("Error", "Warning", "Info")
        }
        if completed.returncode == 0 and counts["errors"] != 0:
            raise ValueError("DEXPIViewer exit code and CSV error count disagree")
        if completed.returncode == 1 and counts["errors"] == 0:
            raise ValueError("DEXPIViewer failed without reporting a validation error")
        return {
            "status": "PASSED" if counts["errors"] == 0 else "ISSUES_FOUND",
            "repository": DEXPI_VIEWER_REPOSITORY,
            "commit": actual_commit,
            "inputSha256": sha256_file(native),
            "csvSha256": sha256_file(csv_path),
            "issueCounts": counts,
            "findings": findings,
        }


def compare_dexpi_viewer_baseline(result: dict, baseline_path: Path) -> dict:
    """Compare pinned-tool provenance and severity counts with a reviewed baseline."""
    baseline = json.loads(baseline_path.read_text(encoding="utf-8"))
    required = {"repository", "commit", "inputSha256", "expectedIssueCounts"}
    missing = sorted(required.difference(baseline))
    if missing:
        raise ValueError(f"DEXPIViewer baseline is missing: {', '.join(missing)}")
    differences = []
    for key in ("repository", "commit", "inputSha256"):
        if baseline[key] != result[key]:
            differences.append(
                {"field": key, "expected": baseline[key], "actual": result[key]}
            )
    for severity, expected in baseline["expectedIssueCounts"].items():
        actual = result["issueCounts"].get(severity)
        if actual != expected:
            differences.append(
                {
                    "field": f"issueCounts.{severity}",
                    "expected": expected,
                    "actual": actual,
                }
            )
    return {
        "file": str(baseline_path),
        "status": "MATCHED" if not differences else "CHANGED",
        "differences": differences,
    }


def validate_package(
    package: Path,
    commercial_result: Path | None,
    native_file: Path | None = None,
    dexpi_viewer: Path | None = None,
    dexpi_viewer_commit: str = DEXPI_VIEWER_COMMIT,
    dexpi_viewer_baseline: Path | None = None,
) -> dict:
    native = native_file if native_file is not None else package / "plant.dexpi.xml"
    proteus = package / "plant-pydexpi.xml"
    report = {
        "profile": "neqsim_dexpi_interoperability.v2",
        "nativeDexpi": {"file": str(native), "status": "FAILED"},
        "dexpiViewer": {
            "status": "NOT_RUN",
            "repository": DEXPI_VIEWER_REPOSITORY,
            "commit": dexpi_viewer_commit,
        },
        "pyDexpi": {"file": proteus.name, "status": "NOT_AVAILABLE"},
        "commercialCae": {"status": "QUALIFICATION_REQUIRED"},
    }
    root = ET.parse(native).getroot()
    if root.tag != "Model":
        raise ValueError(f"Unexpected native DEXPI root: {root.tag}")
    imports = {
        item.attrib.get("prefix"): item.attrib.get("source")
        for item in root.findall("Import")
    }
    if not imports.get("Core") or not imports.get("Plant"):
        raise ValueError("Native DEXPI document does not import Core and Plant models")
    report["nativeDexpi"] = {
        "file": str(native),
        "status": "STRUCTURE_PASSED",
        "sha256": sha256_file(native),
        "objectCount": len(root.findall(".//Object")),
    }

    if dexpi_viewer is not None:
        viewer_result = run_dexpi_viewer(native, dexpi_viewer, dexpi_viewer_commit)
        if dexpi_viewer_baseline is not None:
            viewer_result["baseline"] = compare_dexpi_viewer_baseline(
                viewer_result, dexpi_viewer_baseline
            )
        report["dexpiViewer"] = viewer_result

    if proteus.is_file():
        try:
            from pydexpi.loaders import ProteusSerializer
        except ImportError:
            pass
        else:
            ProteusSerializer().load(str(package), proteus.name)
            report["pyDexpi"] = {
                "file": proteus.name,
                "status": "IMPORT_PASSED",
                "importer": "pyDEXPI ProteusSerializer",
            }

    if commercial_result is not None:
        evidence = json.loads(commercial_result.read_text(encoding="utf-8"))
        required = {"tool", "version", "importStatus", "roundTripStatus", "evidenceReference"}
        missing = sorted(required.difference(evidence))
        if missing:
            raise ValueError(f"Commercial CAE evidence is missing: {', '.join(missing)}")
        if evidence["importStatus"] != "PASSED" or evidence["roundTripStatus"] != "PASSED":
            raise ValueError("Commercial CAE import and round-trip statuses must both be PASSED")
        report["commercialCae"] = evidence
    return report


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("package", type=Path)
    parser.add_argument("--native-file", type=Path)
    parser.add_argument("--commercial-cae-result", type=Path)
    parser.add_argument("--dexpi-viewer", type=Path)
    parser.add_argument("--dexpi-viewer-commit", default=DEXPI_VIEWER_COMMIT)
    parser.add_argument("--dexpi-viewer-baseline", type=Path)
    parser.add_argument("--require-dexpi-viewer", action="store_true")
    parser.add_argument("--require-dexpi-viewer-clean", action="store_true")
    parser.add_argument("--require-dexpi-viewer-baseline", action="store_true")
    parser.add_argument("--require-pydexpi", action="store_true")
    parser.add_argument("--require-commercial-cae", action="store_true")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    try:
        if (args.require_dexpi_viewer or args.require_dexpi_viewer_clean) and not args.dexpi_viewer:
            raise ValueError("DEXPIViewer is required; provide --dexpi-viewer")
        if args.require_dexpi_viewer_baseline and not args.dexpi_viewer_baseline:
            raise ValueError(
                "DEXPIViewer baseline is required; provide --dexpi-viewer-baseline"
            )
        report = validate_package(
            args.package,
            args.commercial_cae_result,
            native_file=args.native_file,
            dexpi_viewer=args.dexpi_viewer,
            dexpi_viewer_commit=args.dexpi_viewer_commit,
            dexpi_viewer_baseline=args.dexpi_viewer_baseline,
        )
        viewer = report["dexpiViewer"]
        if args.require_dexpi_viewer and viewer["status"] == "NOT_RUN":
            raise ValueError("DEXPIViewer validation is required")
        if args.require_dexpi_viewer_clean:
            counts = viewer.get("issueCounts", {})
            if counts.get("errors") != 0 or counts.get("warnings") != 0:
                raise ValueError("DEXPIViewer clean validation requires zero errors and warnings")
        if args.require_dexpi_viewer_baseline:
            if viewer.get("baseline", {}).get("status") != "MATCHED":
                raise ValueError("DEXPIViewer findings changed from the reviewed baseline")
        if args.require_pydexpi and report["pyDexpi"]["status"] != "IMPORT_PASSED":
            raise ValueError("pyDEXPI is required but is not installed")
        if args.require_commercial_cae and report["commercialCae"].get("status") != "PASSED":
            if report["commercialCae"].get("roundTripStatus") != "PASSED":
                raise ValueError("Passing commercial CAE evidence is required")
        rendered = json.dumps(report, indent=2) + "\n"
        if args.output:
            args.output.write_text(rendered, encoding="utf-8")
        else:
            print(rendered, end="")
        return 0
    except (OSError, ET.ParseError, ValueError, json.JSONDecodeError) as exc:
        print(f"DEXPI interoperability validation failed: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
