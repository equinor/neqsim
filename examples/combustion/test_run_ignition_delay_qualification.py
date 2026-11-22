"""Regression tests for ignition-delay qualification reporting."""

import unittest

from run_ignition_delay_qualification import summarize_results


class IgnitionDelayQualificationReportTest(unittest.TestCase):
    def test_summary_preserves_signed_residuals_and_refinement_extent(self):
        results = [
            {
                "residualUs": -2.0,
                "normalizedResidual": -1.0,
                "samplingDifferenceUs": 0.25,
                "samplingDifferenceFractionOfObserved": 0.0025,
            },
            {
                "residualUs": 4.0,
                "normalizedResidual": 2.0,
                "samplingDifferenceUs": -0.5,
                "samplingDifferenceFractionOfObserved": 0.001,
            },
        ]
        summary = summarize_results(results)
        self.assertEqual(summary["pointCount"], 2)
        self.assertAlmostEqual(summary["rmseUs"], 10.0**0.5)
        self.assertAlmostEqual(summary["rmsNormalizedResidual"], 2.5**0.5)
        self.assertEqual(summary["maxAbsoluteNormalizedResidual"], 2.0)
        self.assertEqual(summary["maxAbsoluteSamplingDifferenceUs"], 0.5)
        self.assertEqual(summary["maxSamplingDifferenceFractionOfObserved"], 0.0025)


if __name__ == "__main__":
    unittest.main()
