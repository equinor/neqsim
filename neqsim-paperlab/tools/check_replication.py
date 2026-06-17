"""Check a PaperLab replication report against ``claims_manifest.json``."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any, Dict, List


def _load_json(path: Path) -> Dict[str, Any]:
    """Load a JSON object from ``path``."""
    with path.open(encoding="utf-8") as handle:
        data = json.load(handle)
    if not isinstance(data, dict):
        raise ValueError(f"{path} must contain a JSON object")
    return data


def _number_from_results(results: Dict[str, Any], key: str) -> float:
    """Read a numeric key result."""
    value = results.get("key_results", {}).get(key)
    if not isinstance(value, (int, float)):
        raise ValueError(f"results.json key_results.{key} is not numeric")
    return float(value)


def _check_declared_values(
    checks: List[Dict[str, Any]], results: Dict[str, Any]
) -> List[str]:
    """Validate explicit replication checks declared in claims_manifest.json."""
    failures = []
    for check in checks:
        metric = check.get("metric")
        if not metric:
            failures.append("replication check missing metric")
            continue
        expected = check.get("expected")
        if expected is None:
            expected = _number_from_results(results, metric)
        if not isinstance(expected, (int, float)):
            failures.append(f"replication check {metric} expected value is not numeric")
            continue
        actual = check.get("actual")
        if actual is None:
            actual = _number_from_results(results, metric)
        if not isinstance(actual, (int, float)):
            failures.append(f"replication check {metric} actual value is not numeric")
            continue
        tolerance_abs = float(check.get("tolerance_abs", 0.0))
        tolerance_rel = float(check.get("tolerance_rel", 0.0))
        tolerance = max(tolerance_abs, abs(float(expected)) * tolerance_rel)
        delta = abs(float(actual) - float(expected))
        if delta > tolerance:
            failures.append(
                f"{metric}: actual {actual} differs from expected {expected} by {delta}, "
                f"exceeding tolerance {tolerance}"
            )
    return failures


def check_replication(paper_dir: Path, report_path: Path) -> List[str]:
    """Return replication failures for ``paper_dir``."""
    report = _load_json(report_path)
    results = _load_json(paper_dir / "results.json")
    manifest = _load_json(paper_dir / "claims_manifest.json")

    failures: List[str] = []
    if report.get("status") == "FAILED":
        failures.append("replication report status is FAILED")
    failed_experiments = [
        entry for entry in report.get("experiments", []) if entry.get("status") == "FAIL"
    ]
    for entry in failed_experiments:
        failures.append(f"experiment {entry.get('id', '<unnamed>')} failed")

    unlinked_claims = manifest.get("unlinked_claims", [])
    if unlinked_claims:
        failures.append(f"claims_manifest.json contains {len(unlinked_claims)} unlinked claims")

    failures.extend(_check_declared_values(manifest.get("replication_checks", []), results))

    claims = manifest.get("claims", [])
    if claims and report.get("status") == "SKIPPED":
        failures.append("claims are declared but plan.json has no replication experiments")

    return failures


def main(argv: List[str] | None = None) -> int:
    """CLI entry point."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--paper-dir", required=True, help="Paper or book directory")
    parser.add_argument("--report", required=True, help="Replication report JSON")
    args = parser.parse_args(argv)

    paper_dir = Path(args.paper_dir).resolve()
    report_path = Path(args.report).resolve()
    failures = check_replication(paper_dir, report_path)
    if failures:
        print("Replication check failed:")
        for failure in failures:
            print(f"- {failure}")
        return 1
    print("Replication check passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())