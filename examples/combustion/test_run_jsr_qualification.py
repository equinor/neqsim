"""Regression tests for quantitative JSR comparison reporting."""

import unittest

from run_jsr_qualification import summarize_results


class JsrQualificationReportTest(unittest.TestCase):
    def test_summary_preserves_signed_residuals_and_reports_absolute_extent(self):
        results = [
            {
                "residualPpmv": {"CO": -2.0, "CO2": 3.0},
                "normalizedResidual": {"CO": -1.0, "CO2": 1.5},
            },
            {
                "residualPpmv": {"CO": 4.0, "CO2": -1.0},
                "normalizedResidual": {"CO": 2.0, "CO2": -0.5},
            },
        ]
        summary = summarize_results(results)
        self.assertEqual(summary["pointCount"], 2)
        self.assertAlmostEqual(summary["species"]["CO"]["rmsePpmv"], 10.0**0.5)
        self.assertAlmostEqual(
            summary["species"]["CO2"]["rmsNormalizedResidual"],
            1.25**0.5,
        )
        self.assertEqual(summary["overallMaxAbsoluteNormalizedResidual"], 2.0)


if __name__ == "__main__":
    unittest.main()
