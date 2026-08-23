#!/usr/bin/env python3
"""Run, aggregate, and validate the industrial S/M optimization benchmark."""

from __future__ import annotations

import argparse
import copy
import hashlib
import json
import math
import statistics
import subprocess
import sys
import time
from datetime import date
from pathlib import Path
from typing import Any, Dict, Iterable, List, Mapping, Sequence, Tuple


ROOT = Path(__file__).resolve().parent.parent
RAW_SCHEMA_VERSION = "1.0"
AGGREGATE_SCHEMA_VERSION = "2.0"
GENERATOR = "devtools/industrial_sm_benchmark.py"
TEST_CLASS = "neqsim.process.util.optimizer.IndustrialPlantOptimizationBaselineTest"
SUCCESS_MODES = {
    "S": ["cold", "unchanged", "nearby-state", "constraint-change"],
    "M": [
        "cold",
        "unchanged",
        "unchanged",
        "unchanged",
        "unchanged",
        "unchanged",
        "nearby-state",
        "constraint-change",
        "discrete-line-up",
        "restored-line-up",
    ],
}


class BenchmarkValidationError(ValueError):
    """Raised when benchmark evidence violates the frozen contract."""


def _require(condition: bool, message: str) -> None:
    if not condition:
        raise BenchmarkValidationError(message)


def _load_json(path: Path) -> Dict[str, Any]:
    def reject_constant(value: str) -> None:
        raise BenchmarkValidationError(f"{path}: non-finite JSON token {value}")

    with path.open(encoding="utf-8") as stream:
        value = json.load(stream, parse_constant=reject_constant)
    _require(isinstance(value, dict), f"{path}: root must be an object")
    return value


def _finite_number(value: Any, label: str) -> float:
    _require(
        isinstance(value, (int, float)) and not isinstance(value, bool),
        f"{label} must be numeric",
    )
    number = float(value)
    _require(math.isfinite(number), f"{label} must be finite")
    return number


def _case_map(report: Mapping[str, Any], label: str) -> Dict[str, Mapping[str, Any]]:
    cases = report.get("cases")
    _require(isinstance(cases, list), f"{label}.cases must be an array")
    result: Dict[str, Mapping[str, Any]] = {}
    for case in cases:
        _require(isinstance(case, dict), f"{label}.cases entries must be objects")
        case_id = case.get("caseId")
        _require(case_id in {"S", "M"}, f"{label}: unexpected caseId {case_id!r}")
        _require(case_id not in result, f"{label}: duplicate caseId {case_id}")
        result[str(case_id)] = case
    _require(set(result) == {"S", "M"}, f"{label}: cases must be exactly S and M")
    return result


def _validate_success_mode(mode: Mapping[str, Any], label: str) -> None:
    _require(mode.get("outcome") == "SUCCESS", f"{label}.outcome must be SUCCESS")
    _require(mode.get("processSolved") is True, f"{label}.processSolved must be true")
    _finite_number(mode.get("elapsedMs"), f"{label}.elapsedMs")
    _finite_number(mode.get("feedMassRateKgPerHr"), f"{label}.feedMassRateKgPerHr")
    _finite_number(mode.get("productMassRateKgPerHr"), f"{label}.productMassRateKgPerHr")

    execution = mode.get("executionWork")
    _require(isinstance(execution, dict), f"{label}.executionWork must be an object")
    calls = _finite_number(execution.get("equipmentCalls"), f"{label}.executionWork.equipmentCalls")
    _require(calls >= 0.0, f"{label}.executionWork.equipmentCalls must be non-negative")
    equipment = execution.get("equipment")
    _require(isinstance(equipment, dict) and equipment, f"{label}.executionWork.equipment is required")
    for name, observation in equipment.items():
        _require(isinstance(observation, dict), f"{label}.executionWork.equipment.{name} must be an object")
        _finite_number(observation.get("elapsedMs"), f"{label}.executionWork.equipment.{name}.elapsedMs")
        _finite_number(observation.get("calls"), f"{label}.executionWork.equipment.{name}.calls")
    for unavailable_name in ("areaRuns", "flashAndPropertyWork"):
        unavailable = execution.get(unavailable_name)
        _require(
            isinstance(unavailable, dict) and unavailable.get("status") == "UNAVAILABLE" and unavailable.get("reason"),
            f"{label}.executionWork.{unavailable_name} must explain unavailability",
        )

    mass_balance = mode.get("massBalance")
    _require(isinstance(mass_balance, dict), f"{label}.massBalance must be an object")
    residual = _finite_number(mass_balance.get("maximumAbsoluteError"), f"{label}.massBalance.maximumAbsoluteError")
    _require(residual <= 0.1, f"{label}: mass residual {residual} kg/hr exceeds 0.1 kg/hr")
    _require(mass_balance.get("unit") == "kg/hr", f"{label}.massBalance.unit must be kg/hr")

    bottleneck = mode.get("bottleneck")
    _require(isinstance(bottleneck, dict), f"{label}.bottleneck must be an object")
    _require(bottleneck.get("status") == "AVAILABLE", f"{label}.bottleneck must be available")
    _require(bottleneck.get("finiteEvidence") is True, f"{label}.bottleneck evidence must be finite")
    _finite_number(bottleneck.get("utilization"), f"{label}.bottleneck.utilization")
    for required in ("equipment", "constraint", "unit", "type", "severity", "provenance"):
        _require(required in bottleneck, f"{label}.bottleneck.{required} is required")

    run_status = mode.get("runStatus")
    _require(isinstance(run_status, dict), f"{label}.runStatus must be an object")
    _require(run_status.get("completed") is True, f"{label}.runStatus.completed must be true")
    _require(run_status.get("success") is True, f"{label}.runStatus.success must be true")
    _require(
        _finite_number(mode.get("serializedObservationBytes"), f"{label}.serializedObservationBytes") > 0.0,
        f"{label}.serializedObservationBytes must be positive",
    )
    _require(
        mode.get("utilizationSnapshotSchemaVersion") == RAW_SCHEMA_VERSION,
        f"{label}.utilizationSnapshotSchemaVersion must be {RAW_SCHEMA_VERSION}",
    )


def validate_raw_report(report: Mapping[str, Any], baseline_commit: str, label: str) -> None:
    """Validate one exact harness report against the frozen S/M contract."""

    _require(report.get("schemaVersion") == RAW_SCHEMA_VERSION, f"{label}: raw schema must be {RAW_SCHEMA_VERSION}")
    _require(report.get("neqsimCommit") == baseline_commit, f"{label}: baseline commit mismatch")
    _require(isinstance(report.get("environment"), dict), f"{label}.environment must be an object")
    _require(
        isinstance(report.get("measurementCoverage"), dict),
        f"{label}.measurementCoverage must be an object",
    )

    cases = _case_map(report, label)
    _require(cases["S"].get("unitCount") == 3, f"{label}: case S must have 3 units")
    medium_units = _finite_number(cases["M"].get("unitCount"), f"{label}.M.unitCount")
    _require(25.0 <= medium_units <= 50.0, f"{label}: case M must have 25-50 units")
    _require(cases["M"].get("recycleCount", 0) >= 1, f"{label}: case M must contain a recycle")

    for case_id, expected_modes in SUCCESS_MODES.items():
        modes = cases[case_id].get("modes")
        _require(isinstance(modes, list), f"{label}.{case_id}.modes must be an array")
        successful = [mode for mode in modes if mode.get("mode") != "invalid-candidate"]
        _require(
            [mode.get("mode") for mode in successful] == expected_modes,
            f"{label}.{case_id}: mode sequence does not match the frozen matrix",
        )
        for index, mode in enumerate(successful):
            _require(isinstance(mode, dict), f"{label}.{case_id}.modes[{index}] must be an object")
            _validate_success_mode(mode, f"{label}.{case_id}.{mode.get('mode')}[{index}]")

    small_modes = cases["S"]["modes"]
    invalid = [mode for mode in small_modes if mode.get("mode") == "invalid-candidate"]
    _require(len(invalid) == 1, f"{label}.S must contain one invalid-candidate record")
    _require(invalid[0].get("outcome") == "REJECTED_BEFORE_MUTATION", f"{label}.S invalid candidate must fail closed")
    _require(invalid[0].get("finiteEvidence") is False, f"{label}.S invalid candidate must record non-finite evidence")
    _require(
        invalid[0].get("restoration") == "NOT_REQUIRED_STATE_UNCHANGED",
        f"{label}.S invalid candidate must preserve live state",
    )
    _require(invalid[0].get("rejectionReason"), f"{label}.S invalid candidate needs a rejection reason")

    restored = [mode for mode in cases["M"]["modes"] if mode.get("mode") == "restored-line-up"]
    _require(len(restored) == 1, f"{label}.M must contain one restored-line-up record")
    restoration_difference = _finite_number(
        restored[0].get("repeatabilityAbsoluteDifferenceKgPerHr"),
        f"{label}.M.restored-line-up.repeatabilityAbsoluteDifferenceKgPerHr",
    )
    _require(restoration_difference <= 0.1, f"{label}.M restoration exceeds 0.1 kg/hr")
    _require(restored[0].get("restoration") == "FULL_REPLAY_COMPLETED", f"{label}.M restoration must be complete")


def _successful_modes(report: Mapping[str, Any], case_id: str) -> List[Mapping[str, Any]]:
    case = _case_map(report, "report")[case_id]
    return [mode for mode in case["modes"] if mode.get("mode") != "invalid-candidate"]


def _mode_values(reports: Sequence[Mapping[str, Any]], case_id: str, mode_name: str, field: str) -> List[float]:
    values: List[float] = []
    for report in reports:
        for mode in _successful_modes(report, case_id):
            if mode.get("mode") == mode_name:
                values.append(_finite_number(mode.get(field), f"{case_id}.{mode_name}.{field}"))
    return values


def _summary(values: Sequence[float]) -> Dict[str, Any]:
    _require(bool(values), "cannot summarize an empty sample")
    median = float(statistics.median(values))
    deviations = [abs(value - median) for value in values]
    return {
        "sampleCount": len(values),
        "median": median,
        "medianAbsoluteDeviation": float(statistics.median(deviations)),
        "minimum": min(values),
        "maximum": max(values),
    }


def _identity_sequence(report: Mapping[str, Any]) -> List[Tuple[str, str, int, str]]:
    sequence: List[Tuple[str, str, int, str]] = []
    for case_id in ("S", "M"):
        for mode in _successful_modes(report, case_id):
            sequence.append(
                (
                    case_id,
                    str(mode["mode"]),
                    int(mode["repetition"]),
                    str(mode["calculationIdentity"]),
                )
            )
    return sequence


def build_aggregate(
    reports: Sequence[Mapping[str, Any]],
    wall_times_ms: Sequence[float],
    baseline_commit: str,
    generated_date: str,
) -> Dict[str, Any]:
    """Build deterministic aggregate evidence while preserving each raw report verbatim."""

    _require(len(reports) == len(wall_times_ms), "report and wall-time counts differ")
    _require(len(reports) >= 5, "at least five independent forks are required")
    for index, report in enumerate(reports, start=1):
        validate_raw_report(report, baseline_commit, f"fork-{index}")
    finite_wall_times = [_finite_number(value, f"fork-{index}.wallTimeMs") for index, value in enumerate(wall_times_ms, 1)]
    _require(all(value > 0.0 for value in finite_wall_times), "wall times must be positive")

    expected_identities = _identity_sequence(reports[0])
    for index, report in enumerate(reports[1:], start=2):
        _require(
            _identity_sequence(report) == expected_identities,
            f"fork-{index}: deterministic calculation identities changed",
        )

    small_unchanged = _mode_values(reports, "S", "unchanged", "repeatabilityAbsoluteDifferenceKgPerHr")
    medium_unchanged = _mode_values(reports, "M", "unchanged", "repeatabilityAbsoluteDifferenceKgPerHr")
    restoration = _mode_values(reports, "M", "restored-line-up", "repeatabilityAbsoluteDifferenceKgPerHr")
    medium_modes = [mode for report in reports for mode in _successful_modes(report, "M")]
    medium_residuals = [
        _finite_number(mode["massBalance"]["maximumAbsoluteError"], "M.massBalance.maximumAbsoluteError")
        for mode in medium_modes
    ]
    medium_sizes = [
        _finite_number(mode["serializedObservationBytes"], "M.serializedObservationBytes")
        for mode in medium_modes
    ]
    equipment_calls = [
        _finite_number(mode["executionWork"]["equipmentCalls"], "executionWork.equipmentCalls")
        for report in reports
        for case_id in ("S", "M")
        for mode in _successful_modes(report, case_id)
    ]

    forks: List[Dict[str, Any]] = []
    for index, (report, wall_time) in enumerate(zip(reports, finite_wall_times), start=1):
        raw_copy = copy.deepcopy(report)
        canonical_payload = json.dumps(
            raw_copy, allow_nan=False, separators=(",", ":"), sort_keys=True
        ).encode("utf-8")
        forks.append(
            {
                "fork": index,
                "externalWallTimeMs": wall_time,
                "canonicalRawReportBytes": len(canonical_payload),
                "canonicalRawReportSha256": hashlib.sha256(canonical_payload).hexdigest(),
                "rawReport": raw_copy,
            }
        )

    return {
        "schemaVersion": AGGREGATE_SCHEMA_VERSION,
        "evidenceKind": "industrial-process-optimization-SM-five-fork-baseline",
        "baselineCommit": baseline_commit,
        "generatedDate": generated_date,
        "aggregation": {
            "generator": GENERATOR,
            "rawReportSchemaVersion": RAW_SCHEMA_VERSION,
            "forkCount": len(reports),
            "rawReportsPreserved": True,
            "validationStatus": "PASS",
        },
        "externalWallTimeMs": finite_wall_times,
        "statistics": {
            "externalWallTimeMs": _summary(finite_wall_times),
            "smallColdElapsedMs": _summary(_mode_values(reports, "S", "cold", "elapsedMs")),
            "smallUnchangedElapsedMs": _summary(_mode_values(reports, "S", "unchanged", "elapsedMs")),
            "mediumColdElapsedMs": _summary(_mode_values(reports, "M", "cold", "elapsedMs")),
            "mediumUnchangedElapsedMs": _summary(_mode_values(reports, "M", "unchanged", "elapsedMs")),
            "smallUnchangedDifferenceKgPerHr": _summary(small_unchanged),
            "mediumUnchangedDifferenceKgPerHr": _summary(medium_unchanged),
            "mediumRestorationDifferenceKgPerHr": _summary(restoration),
            "mediumMaximumUnitMassResidualKgPerHr": _summary(medium_residuals),
            "mediumSerializedObservationBytes": _summary(medium_sizes),
            "successfulModeEquipmentCalls": _summary(equipment_calls),
        },
        "acceptance": {
            "allForksValidated": True,
            "successfulModeCount": len(equipment_calls),
            "invalidCandidateCount": len(reports),
            "allUnchangedProductDifferencesAtMostKgPerHr": 0.1,
            "allMassResidualsAtMostKgPerHr": 0.1,
            "allRestorationDifferencesAtMostKgPerHr": 0.1,
        },
        "forks": forks,
    }


def validate_aggregate(aggregate: Mapping[str, Any]) -> None:
    """Validate an aggregate and prove its summary is derived from preserved raw reports."""

    _require(aggregate.get("schemaVersion") == AGGREGATE_SCHEMA_VERSION, "aggregate schema mismatch")
    baseline_commit = aggregate.get("baselineCommit")
    generated_date = aggregate.get("generatedDate")
    _require(isinstance(baseline_commit, str) and baseline_commit, "aggregate baselineCommit is required")
    _require(isinstance(generated_date, str) and generated_date, "aggregate generatedDate is required")
    forks = aggregate.get("forks")
    _require(isinstance(forks, list), "aggregate forks must be an array")
    reports = []
    wall_times = []
    for expected_index, fork in enumerate(forks, start=1):
        _require(isinstance(fork, dict), f"fork-{expected_index} must be an object")
        _require(fork.get("fork") == expected_index, f"fork-{expected_index} index mismatch")
        report = fork.get("rawReport")
        _require(isinstance(report, dict), f"fork-{expected_index}.rawReport must be an object")
        canonical_payload = json.dumps(
            report, allow_nan=False, separators=(",", ":"), sort_keys=True
        ).encode("utf-8")
        _require(
            fork.get("canonicalRawReportBytes") == len(canonical_payload),
            f"fork-{expected_index}.canonicalRawReportBytes mismatch",
        )
        _require(
            fork.get("canonicalRawReportSha256") == hashlib.sha256(canonical_payload).hexdigest(),
            f"fork-{expected_index}.canonicalRawReportSha256 mismatch",
        )
        reports.append(report)
        wall_times.append(fork.get("externalWallTimeMs"))

    rebuilt = build_aggregate(reports, wall_times, baseline_commit, generated_date)
    _require(dict(aggregate) == rebuilt, "aggregate fields or statistics do not match preserved raw reports")


def _write_json(path: Path, value: Mapping[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, allow_nan=False) + "\n", encoding="utf-8")


def _read_fork_inputs(raw_dir: Path, fork_count: int) -> Tuple[List[Dict[str, Any]], List[float]]:
    reports: List[Dict[str, Any]] = []
    wall_times: List[float] = []
    for index in range(1, fork_count + 1):
        reports.append(_load_json(raw_dir / f"fork-{index}.json"))
        wall_path = raw_dir / f"fork-{index}.wall-ms"
        wall_times.append(float(wall_path.read_text(encoding="utf-8").strip()))
    return reports, wall_times


def _aggregate_command(args: argparse.Namespace) -> None:
    reports, wall_times = _read_fork_inputs(args.raw_dir, args.forks)
    aggregate = build_aggregate(reports, wall_times, args.baseline_commit, args.generated_date)
    validate_aggregate(aggregate)
    _write_json(args.output, aggregate)
    print(f"validated {len(reports)} forks and wrote {args.output}")


def _run_command(args: argparse.Namespace) -> None:
    raw_dir = args.raw_dir.resolve()
    raw_dir.mkdir(parents=True, exist_ok=True)
    wrapper = ROOT / ("mvnw.cmd" if sys.platform.startswith("win") else "mvnw")
    for index in range(1, args.forks + 1):
        raw_path = raw_dir / f"fork-{index}.json"
        command = [
            str(wrapper),
            "-Dgroups=slow",
            "-DexcludedTestGroups=",
            "-Djacoco.skip=true",
            f"-Dtest={TEST_CLASS}",
            f"-Dneqsim.optimization.baseline.commit={args.baseline_commit}",
            f"-Dneqsim.optimization.baseline.output={raw_path}",
            "test",
        ]
        started = time.perf_counter()
        subprocess.run(command, cwd=ROOT, check=True)
        wall_time_ms = (time.perf_counter() - started) * 1000.0
        (raw_dir / f"fork-{index}.wall-ms").write_text(f"{wall_time_ms:.3f}\n", encoding="utf-8")
        validate_raw_report(_load_json(raw_path), args.baseline_commit, f"fork-{index}")
    _aggregate_command(args)


def _validate_command(args: argparse.Namespace) -> None:
    aggregate = _load_json(args.input)
    validate_aggregate(aggregate)
    print(f"validated {args.input}")


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)

    common = argparse.ArgumentParser(add_help=False)
    common.add_argument("--baseline-commit", required=True, help="exact unmodified NeqSim commit")
    common.add_argument("--forks", type=int, default=5, help="independent JVM fork count (minimum 5)")
    common.add_argument("--raw-dir", type=Path, required=True, help="directory for fork JSON and wall times")
    common.add_argument("--output", type=Path, required=True, help="aggregate JSON output path")
    common.add_argument("--generated-date", default=date.today().isoformat(), help="ISO date stored in the aggregate")

    run_parser = subparsers.add_parser("run", parents=[common], help="execute Maven forks, aggregate, and validate")
    run_parser.set_defaults(handler=_run_command)

    aggregate_parser = subparsers.add_parser(
        "aggregate", parents=[common], help="aggregate and validate existing fork outputs"
    )
    aggregate_parser.set_defaults(handler=_aggregate_command)

    validate_parser = subparsers.add_parser("validate", help="validate a checked aggregate")
    validate_parser.add_argument("--input", type=Path, required=True)
    validate_parser.set_defaults(handler=_validate_command)
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = _parser().parse_args(argv)
    try:
        args.handler(args)
    except (BenchmarkValidationError, FileNotFoundError, json.JSONDecodeError, ValueError) as exc:
        print(f"industrial S/M benchmark validation failed: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
