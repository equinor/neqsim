"""Unit tests for industrial S/M benchmark aggregation and validation."""

import copy
import json
import unittest

from devtools import industrial_sm_benchmark as benchmark


BASELINE = "0123456789abcdef0123456789abcdef01234567"


def unavailable(reason="not attributable"):
    return {"status": "UNAVAILABLE", "reason": reason}


def success_mode(name, repetition=1, repeatability=None):
    mode = {
        "mode": name,
        "repetition": repetition,
        "outcome": "SUCCESS",
        "processSolved": True,
        "calculationIdentity": f"identity-{name}-{repetition}",
        "elapsedMs": 2.0 + repetition,
        "usedHeapBytesBefore": 100,
        "usedHeapBytesAfter": 120,
        "usedHeapDeltaBytes": 20,
        "feedMassRateKgPerHr": 1000.0,
        "productMassRateKgPerHr": 999.99,
        "executionWork": {
            "equipmentCalls": 2,
            "equipment": {
                "Feed": {"elapsedMs": 0.1, "calls": 1},
                "Separator": {"elapsedMs": 0.2, "calls": 1},
            },
            "areaRuns": unavailable(),
            "flashAndPropertyWork": unavailable(),
        },
        "massBalance": {
            "maximumAbsoluteError": 0.01,
            "maximumPercentError": 0.001,
            "unit": "kg/hr",
            "worstUnit": "Separator",
            "energyBalance": unavailable(),
        },
        "bottleneck": {
            "status": "AVAILABLE",
            "equipment": "Separator",
            "constraint": "gasLoad",
            "utilization": 0.8,
            "finiteEvidence": True,
            "unit": "m/s",
            "type": "HARD",
            "severity": "HARD",
            "provenance": "synthetic benchmark design basis",
        },
        "runStatus": {"schemaVersion": "1.0", "completed": True, "success": True},
        "serializedObservationBytes": 1000,
        "utilizationSnapshotSchemaVersion": "1.0",
    }
    if repeatability is not None:
        mode["repeatabilityAbsoluteDifferenceKgPerHr"] = repeatability
    if name == "discrete-line-up":
        mode["availabilityAction"] = "train unavailable"
    if name == "restored-line-up":
        mode["restoration"] = "FULL_REPLAY_COMPLETED"
    return mode


def raw_report():
    small_modes = [
        success_mode("cold"),
        success_mode("unchanged", repeatability=0.0),
        success_mode("nearby-state"),
        success_mode("constraint-change"),
        {
            "mode": "invalid-candidate",
            "outcome": "REJECTED_BEFORE_MUTATION",
            "finiteEvidence": False,
            "restoration": "NOT_REQUIRED_STATE_UNCHANGED",
            "rejectionReason": "decision variable must be finite",
        },
    ]
    medium_modes = [success_mode("cold")]
    medium_modes.extend(success_mode("unchanged", repetition=index, repeatability=0.0) for index in range(1, 6))
    medium_modes.extend(
        [
            success_mode("nearby-state"),
            success_mode("constraint-change"),
            success_mode("discrete-line-up"),
            success_mode("restored-line-up", repeatability=0.02),
        ]
    )
    return {
        "schemaVersion": "1.0",
        "neqsimCommit": BASELINE,
        "environment": {"javaVersion": "17"},
        "measurementCoverage": {"convergenceAndRunStatus": "AVAILABLE"},
        "cases": [
            {
                "caseSchemaVersion": "1.0",
                "caseId": "S",
                "unitCount": 3,
                "recycleCount": 0,
                "modes": small_modes,
            },
            {
                "caseSchemaVersion": "1.0",
                "caseId": "M",
                "unitCount": 27,
                "recycleCount": 1,
                "modes": medium_modes,
            },
        ],
    }


class IndustrialSmBenchmarkTest(unittest.TestCase):
    def test_checked_current_master_aggregate_validates(self):
        path = (
            benchmark.ROOT
            / "docs"
            / "process"
            / "optimization"
            / "benchmarks"
            / "industrial-sm-baseline-f3a2cf5f.json"
        )
        with path.open(encoding="utf-8") as stream:
            aggregate = json.load(stream)

        benchmark.validate_aggregate(aggregate)

    def test_aggregate_preserves_raw_reports_and_recomputes_statistics(self):
        reports = [raw_report() for _ in range(5)]
        aggregate = benchmark.build_aggregate(reports, [10, 11, 12, 13, 14], BASELINE, "2026-08-23")

        benchmark.validate_aggregate(aggregate)

        self.assertEqual(aggregate["schemaVersion"], "2.0")
        self.assertEqual(aggregate["forks"][0]["rawReport"], reports[0])
        self.assertEqual(aggregate["statistics"]["externalWallTimeMs"]["median"], 12.0)
        self.assertEqual(aggregate["statistics"]["mediumUnchangedElapsedMs"]["sampleCount"], 25)
        self.assertEqual(aggregate["acceptance"]["successfulModeCount"], 70)

    def test_missing_per_equipment_execution_work_is_rejected(self):
        report = raw_report()
        del report["cases"][0]["modes"][0]["executionWork"]

        with self.assertRaisesRegex(benchmark.BenchmarkValidationError, "executionWork"):
            benchmark.validate_raw_report(report, BASELINE, "fork-1")

    def test_non_finite_constraint_evidence_is_rejected(self):
        report = raw_report()
        report["cases"][1]["modes"][0]["bottleneck"]["utilization"] = float("nan")

        with self.assertRaisesRegex(benchmark.BenchmarkValidationError, "must be finite"):
            benchmark.validate_raw_report(report, BASELINE, "fork-1")

    def test_tampered_summary_is_rejected(self):
        reports = [raw_report() for _ in range(5)]
        aggregate = benchmark.build_aggregate(reports, [10, 11, 12, 13, 14], BASELINE, "2026-08-23")
        tampered = copy.deepcopy(aggregate)
        tampered["statistics"]["externalWallTimeMs"]["median"] = 99.0

        with self.assertRaisesRegex(benchmark.BenchmarkValidationError, "do not match"):
            benchmark.validate_aggregate(tampered)

    def test_changed_calculation_identity_between_forks_is_rejected(self):
        reports = [raw_report() for _ in range(5)]
        reports[3]["cases"][1]["modes"][0]["calculationIdentity"] = "different"

        with self.assertRaisesRegex(benchmark.BenchmarkValidationError, "identities changed"):
            benchmark.build_aggregate(reports, [10, 11, 12, 13, 14], BASELINE, "2026-08-23")


if __name__ == "__main__":
    unittest.main()
