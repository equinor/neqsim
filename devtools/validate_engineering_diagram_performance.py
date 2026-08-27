#!/usr/bin/env python3
"""Validate deterministic engineering-diagram benchmark evidence and CI budgets."""

from __future__ import annotations

import argparse
import json
import math
import re
from pathlib import Path
from typing import Any


REPORT_SCHEMA = "neqsim_engineering_diagram_performance.v1"
BUDGET_SCHEMA = "neqsim_engineering_diagram_performance_budget.v1"


class BenchmarkValidationError(ValueError):
    """Raised when benchmark evidence violates the frozen regression contract."""


def _load(path: Path) -> dict[str, Any]:
    with path.open(encoding="utf-8") as stream:
        value = json.load(stream)
    if not isinstance(value, dict):
        raise BenchmarkValidationError(f"{path} must contain a JSON object")
    return value


def validate(report: dict[str, Any], budget: dict[str, Any]) -> list[str]:
    """Validate one benchmark report and return human-readable operation summaries."""
    if report.get("schemaVersion") != REPORT_SCHEMA:
        raise BenchmarkValidationError("unexpected report schemaVersion")
    if budget.get("schemaVersion") != BUDGET_SCHEMA:
        raise BenchmarkValidationError("unexpected budget schemaVersion")
    if report.get("referenceCase") != budget.get("referenceCase"):
        raise BenchmarkValidationError("report and budget referenceCase differ")
    if report.get("engineeringStatus") != "PERFORMANCE_REGRESSION_EVIDENCE_ONLY":
        raise BenchmarkValidationError(
            "benchmark must remain PERFORMANCE_REGRESSION_EVIDENCE_ONLY"
        )
    if report.get("approvalStatus") != "REVIEW_REQUIRED":
        raise BenchmarkValidationError("benchmark must remain REVIEW_REQUIRED")
    if report.get("fitnessForConstruction") is not False:
        raise BenchmarkValidationError("benchmark must not claim fitness for construction")

    minimum_samples = budget.get("minimumSamples")
    sample_runs = report.get("sampleRuns")
    warmup_runs = report.get("warmupRuns")
    budgets = budget.get("budgetsMilliseconds")
    operations = report.get("operations")
    if not isinstance(minimum_samples, int) or minimum_samples < 1:
        raise BenchmarkValidationError("minimumSamples must be a positive integer")
    if not isinstance(sample_runs, int) or sample_runs < minimum_samples:
        raise BenchmarkValidationError("sampleRuns must satisfy minimumSamples")
    if not isinstance(warmup_runs, int) or warmup_runs < 1:
        raise BenchmarkValidationError("warmupRuns must be a positive integer")
    if not isinstance(budgets, dict) or not budgets:
        raise BenchmarkValidationError("budgetsMilliseconds must be a non-empty object")
    if not isinstance(operations, list):
        raise BenchmarkValidationError("operations must be an array")

    by_name: dict[str, dict[str, Any]] = {}
    for operation in operations:
        if not isinstance(operation, dict) or not isinstance(operation.get("name"), str):
            raise BenchmarkValidationError("every operation must be a named object")
        name = operation["name"]
        if name in by_name:
            raise BenchmarkValidationError(f"duplicate operation: {name}")
        by_name[name] = operation
    if set(by_name) != set(budgets):
        raise BenchmarkValidationError("report operations do not exactly match controlled budgets")

    summaries: list[str] = []
    for name in sorted(budgets):
        operation = by_name[name]
        samples = operation.get("samples")
        median = operation.get("median")
        maximum = operation.get("maximum")
        limit = budgets[name]
        if operation.get("unit") != "milliseconds":
            raise BenchmarkValidationError(f"{name}: unit must be milliseconds")
        if operation.get("deterministic") is not True:
            raise BenchmarkValidationError(f"{name}: output was not deterministic")
        fingerprint = operation.get("outputFingerprint")
        if not isinstance(fingerprint, str) or re.fullmatch(r"[0-9a-f]{64}", fingerprint) is None:
            raise BenchmarkValidationError(f"{name}: invalid output fingerprint")
        if not isinstance(samples, list) or len(samples) != sample_runs:
            raise BenchmarkValidationError(f"{name}: sample count does not match sampleRuns")
        numeric = [float(value) for value in samples]
        if any(not math.isfinite(value) or value < 0.0 for value in numeric):
            raise BenchmarkValidationError(f"{name}: samples must be finite and non-negative")
        if not isinstance(median, (int, float)) or not math.isfinite(float(median)):
            raise BenchmarkValidationError(f"{name}: median must be finite")
        if not isinstance(maximum, (int, float)) or not math.isfinite(float(maximum)):
            raise BenchmarkValidationError(f"{name}: maximum must be finite")
        if not isinstance(limit, (int, float)) or not math.isfinite(float(limit)) or float(limit) <= 0.0:
            raise BenchmarkValidationError(f"{name}: budget must be finite and positive")
        ordered = sorted(numeric)
        calculated_median = ordered[len(ordered) // 2]
        if not math.isclose(float(median), calculated_median, rel_tol=1.0e-12, abs_tol=1.0e-9):
            raise BenchmarkValidationError(f"{name}: reported median does not match samples")
        if not math.isclose(float(maximum), max(numeric), rel_tol=1.0e-12, abs_tol=1.0e-9):
            raise BenchmarkValidationError(f"{name}: reported maximum does not match samples")
        if float(median) > float(limit):
            raise BenchmarkValidationError(
                f"{name}: median {float(median):.3f} ms exceeds budget {float(limit):.3f} ms"
            )
        summaries.append(
            f"{name}: median={float(median):.3f} ms, maximum={float(maximum):.3f} ms, "
            f"budget={float(limit):.3f} ms"
        )
    return summaries


def main() -> int:
    """Command-line entry point."""
    parser = argparse.ArgumentParser()
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--budget", type=Path, required=True)
    args = parser.parse_args()
    try:
        summaries = validate(_load(args.report), _load(args.budget))
    except (OSError, json.JSONDecodeError, BenchmarkValidationError) as exception:
        print(f"engineering diagram benchmark validation failed: {exception}")
        return 1
    for summary in summaries:
        print(summary)
    print("engineering diagram benchmark validation passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
