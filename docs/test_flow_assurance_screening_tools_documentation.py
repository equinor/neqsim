"""Source-anchored contracts for the flow-assurance screening documentation."""

from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]
GUIDE = ROOT / "docs/pvtsimulation/flowassurance/flow_assurance_screening_tools.md"
LANDING = ROOT / "docs/pvtsimulation/flowassurance/README.md"
JAVA_TEST = (
    ROOT
    / "src/test/java/neqsim/pvtsimulation/flowassurance/FlowAssuranceDocumentationTest.java"
)
SOURCES = {
    "cooldown": ROOT
    / "src/main/java/neqsim/pvtsimulation/flowassurance/PipelineCooldownCalculator.java",
    "corrosion": ROOT
    / "src/main/java/neqsim/pvtsimulation/flowassurance/DeWaardMilliamsCorrosion.java",
    "scale": ROOT
    / "src/main/java/neqsim/pvtsimulation/flowassurance/ScalePredictionCalculator.java",
    "wax": ROOT
    / "src/main/java/neqsim/pvtsimulation/flowassurance/WaxCurveCalculator.java",
}


class FlowAssuranceScreeningToolsDocumentationTest(unittest.TestCase):
    """Protect the documented APIs, units, links, and engineering boundaries."""

    @classmethod
    def setUpClass(cls):
        cls.guide = GUIDE.read_text(encoding="utf-8")
        cls.landing = LANDING.read_text(encoding="utf-8")
        cls.java_test = JAVA_TEST.read_text(encoding="utf-8")
        cls.sources = {
            name: path.read_text(encoding="utf-8") for name, path in SOURCES.items()
        }

    def test_current_api_names_and_units_are_source_anchored(self):
        expected = {
            "cooldown": [
                "setInitialFluidTemperature(double temperatureK)",
                "getTimeToReachTemperature(double targetTemperatureK)",
                "getTimeConstantHours()",
            ],
            "corrosion": [
                "setCO2PartialPressure(double pressureBar)",
                "setFlowVelocity(double velocityMs)",
                "calculateCorrosionRate()",
            ],
            "scale": [
                "setPressureBara(double pressBar)",
                "getScaleRisks()",
                "hasScalingRisk()",
            ],
            "wax": [
                "WaxCurveCalculator(SystemInterface fluid)",
                "setTemperatureRange(double startC, double endC, double stepC)",
                "getWaxAppearanceTemperatureC()",
                "getMonotonicityCorrections()",
                "getFailCount()",
            ],
        }
        for source_name, signatures in expected.items():
            for signature in signatures:
                with self.subTest(source=source_name, signature=signature):
                    self.assertIn(signature, self.sources[source_name])
                    self.assertIn(signature.split("(")[0].split()[-1], self.guide)

        for stale in (
            "new WaxCurveCalculator(fluid, 50.0)",
            "getWAT()",
            "getMonotonicityViolationCount()",
            "System.out",
        ):
            self.assertNotIn(stale, self.guide + self.landing)

    def test_rendering_structure_and_internal_links_are_safe(self):
        self.assertTrue(self.guide.startswith("---\n"))
        self.assertNotIn("\n# Flow Assurance Screening Tools\n", self.guide)
        math_parts = self.guide.split("$$")
        self.assertEqual(1, len(math_parts) % 2)
        for equation in math_parts[1::2]:
            self.assertEqual(equation, equation.strip())

        for document, source in ((self.guide, GUIDE), (self.landing, LANDING)):
            links = re.findall(r"\[[^\]]+\]\(([^)]+)\)", document)
            for target in links:
                if target.startswith(("http://", "https://", "#")):
                    continue
                path_part = target.split("#", 1)[0]
                self.assertTrue(path_part.endswith(".md"), target)
                source_path = (source.parent / path_part).resolve()
                self.assertTrue(source_path.exists(), target)

    def test_engineering_boundaries_and_executable_coverage_are_explicit(self):
        required = (
            "Not a complete NORSOK M-506 calculation",
            "does not use them",
            "not precipitation rate",
            "failed flash",
            "degrees Celsius, not Kelvin",
            "not as infinite no-touch",
            "input provenance",
        )
        for phrase in required:
            self.assertIn(phrase, self.guide)

        for api in (
            "PipelineCooldownCalculator",
            "DeWaardMilliamsCorrosion",
            "ScalePredictionCalculator",
            "WaxCurveCalculator",
            "enforceNonDecreasing",
        ):
            self.assertIn(api, self.java_test)

    def test_landing_points_to_one_canonical_screening_example(self):
        self.assertIn(
            "[screening-tools guide](flow_assurance_screening_tools.md)", self.landing
        )
        self.assertIn("FlowAssuranceDocumentationTest", self.landing)
        self.assertNotIn("## Quick start: De Boer", self.landing)


if __name__ == "__main__":
    unittest.main()
