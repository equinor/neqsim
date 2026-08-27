"""Contracts for engineering-diagram performance evidence validation."""

import copy
import unittest

from devtools.validate_engineering_diagram_performance import (
    BenchmarkValidationError,
    validate,
)


BUDGET = {
    "schemaVersion": "neqsim_engineering_diagram_performance_budget.v1",
    "referenceCase": "DEXPI-REF-MULTI-AREA",
    "minimumSamples": 3,
    "budgetsMilliseconds": {"render": 10.0},
}
REPORT = {
    "schemaVersion": "neqsim_engineering_diagram_performance.v1",
    "referenceCase": "DEXPI-REF-MULTI-AREA",
    "warmupRuns": 1,
    "sampleRuns": 3,
    "engineeringStatus": "PERFORMANCE_REGRESSION_EVIDENCE_ONLY",
    "approvalStatus": "REVIEW_REQUIRED",
    "fitnessForConstruction": False,
    "operations": [
        {
            "name": "render",
            "unit": "milliseconds",
            "samples": [2.0, 4.0, 3.0],
            "median": 3.0,
            "maximum": 4.0,
            "deterministic": True,
            "outputFingerprint": "a" * 64,
        }
    ],
}


class EngineeringDiagramPerformanceValidationTest(unittest.TestCase):
    def test_accepts_deterministic_evidence_within_budget(self):
        summaries = validate(REPORT, BUDGET)
        self.assertEqual(1, len(summaries))
        self.assertIn("median=3.000 ms", summaries[0])

    def test_rejects_slow_median(self):
        report = copy.deepcopy(REPORT)
        report["operations"][0]["samples"] = [12.0, 11.0, 9.0]
        report["operations"][0]["median"] = 11.0
        report["operations"][0]["maximum"] = 12.0
        with self.assertRaisesRegex(BenchmarkValidationError, "exceeds budget"):
            validate(report, BUDGET)

    def test_rejects_nondeterministic_output(self):
        report = copy.deepcopy(REPORT)
        report["operations"][0]["deterministic"] = False
        with self.assertRaisesRegex(BenchmarkValidationError, "not deterministic"):
            validate(report, BUDGET)

    def test_rejects_approval_or_sample_contract_drift(self):
        report = copy.deepcopy(REPORT)
        report["engineeringStatus"] = "ENGINEERING_APPROVED"
        with self.assertRaisesRegex(BenchmarkValidationError, "REGRESSION_EVIDENCE_ONLY"):
            validate(report, BUDGET)

        report = copy.deepcopy(REPORT)
        report["sampleRuns"] = 4
        with self.assertRaisesRegex(BenchmarkValidationError, "sample count"):
            validate(report, BUDGET)

    def test_rejects_operation_or_median_drift(self):
        report = copy.deepcopy(REPORT)
        report["operations"][0]["name"] = "unexpected"
        with self.assertRaisesRegex(BenchmarkValidationError, "exactly match"):
            validate(report, BUDGET)

        report = copy.deepcopy(REPORT)
        report["operations"][0]["median"] = 4.0
        with self.assertRaisesRegex(BenchmarkValidationError, "median does not match"):
            validate(report, BUDGET)


if __name__ == "__main__":
    unittest.main()
