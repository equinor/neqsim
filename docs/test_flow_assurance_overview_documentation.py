"""Contracts for the integrated flow-assurance overview."""

from pathlib import Path
import re
import unittest
from urllib.parse import unquote


ROOT = Path(__file__).resolve().parents[1]
GUIDE = ROOT / "docs/pvtsimulation/flow_assurance_overview.md"
JAVA_TEST = (
    ROOT
    / "src/test/java/neqsim/pvtsimulation/flowassurance/"
    / "FlowAssuranceDocumentationTest.java"
)
DE_BOER_SOURCE = (
    ROOT
    / "src/main/java/neqsim/pvtsimulation/flowassurance/"
    / "DeBoerAsphalteneScreening.java"
)
SCALE_SOURCE = (
    ROOT
    / "src/main/java/neqsim/pvtsimulation/flowassurance/"
    / "ScalePredictionCalculator.java"
)


def resolve_internal_target(source, destination):
    target, _, fragment = unquote(destination).partition("#")
    raw_target = source.parent / target
    candidates = [raw_target]
    if not Path(target).suffix:
        candidates.extend(
            (
                Path("{}.md".format(raw_target)),
                raw_target / "README.md",
                raw_target / "index.md",
            )
        )
    for candidate in candidates:
        if candidate.is_file():
            return candidate.resolve(), fragment
    raise AssertionError("Unresolved guide link: {}".format(destination))


class FlowAssuranceOverviewDocumentationContractTest(unittest.TestCase):
    """Protect rendering, executable API, units, and engineering boundaries."""

    @classmethod
    def setUpClass(cls):
        cls.guide = GUIDE.read_text(encoding="utf-8")
        cls.java_test = JAVA_TEST.read_text(encoding="utf-8")
        cls.de_boer_source = DE_BOER_SOURCE.read_text(encoding="utf-8")
        cls.scale_source = SCALE_SOURCE.read_text(encoding="utf-8")

    def test_structure_math_and_internal_links_are_renderable(self):
        self.assertTrue(self.guide.startswith("---\n"))
        self.assertEqual(self.guide.count("# Flow Assurance Overview"), 0)
        self.assertEqual(self.guide.count("```") % 2, 0)
        without_fences = re.sub(
            r"```.*?```",
            "",
            self.guide,
            flags=re.DOTALL,
        )
        self.assertNotIn(r"\[", without_fences)
        self.assertNotIn(r"\]", without_fences)

        math_parts = without_fences.split("$$")
        self.assertEqual(1, len(math_parts) % 2)
        for equation in math_parts[1::2]:
            self.assertEqual(equation, equation.strip())
            self.assertNotIn("\n", equation)

        links = re.findall(r"(?<!!)\[[^\]]+\]\(([^)]+)\)", self.guide)
        for destination in links:
            if destination.startswith(("http://", "https://", "mailto:", "#")):
                continue
            target, _fragment = resolve_internal_target(GUIDE, destination)
            self.assertTrue(target.is_file())

    def test_java_example_uses_repository_logging_contract(self):
        for token in (
            "import org.apache.logging.log4j.LogManager;",
            "import org.apache.logging.log4j.Logger;",
            "private static final Logger logger =",
            "LogManager.getLogger(FlowAssuranceScreen.class)",
            'logger.info(',
        ):
            self.assertIn(token, self.guide)

        self.assertNotIn("System.out", self.guide)
        self.assertNotIn("System.err", self.guide)
        self.assertNotIn('"%n"', self.guide)

    def test_documented_calls_have_executable_regression_coverage(self):
        for guide_token in (
            "new SystemElectrolyteCPAstatoil(273.15 + 10.0, 50.0)",
            "hydrateOps.hydrateFormationTemperature()",
            "new DeBoerAsphalteneScreening(350.0, 150.0, 750.0)",
            "new ScalePredictionCalculator()",
            "scaleScreen.enableAutoPH()",
            "scaleScreen.calculate()",
        ):
            self.assertIn(guide_token, self.guide)

        for test_token in (
            "testDeBoerQuickStart",
            "testIntegratedOverviewHydrateAndScaleScreen",
            "hydrateOps.hydrateFormationTemperature()",
            "Double.isFinite(hydrateTemperatureC)",
            "scaleScreen.calculate()",
            "getCaCO3SaturationIndex()",
            "getBaSO4SaturationIndex()",
            "scaleScreen.toJson()",
        ):
            self.assertIn(test_token, self.java_test)

        for source_token in (
            "evaluateRisk()",
            "calculateRiskIndex()",
        ):
            self.assertIn(source_token, self.de_boer_source)

        for source_token in (
            "setPressureBara(double pressBar)",
            "enableAutoPH()",
            "getCaCO3SaturationIndex()",
            "getBaSO4SaturationIndex()",
            "hasScalingRisk()",
        ):
            self.assertIn(source_token, self.scale_source)

    def test_equations_define_symbols_units_and_standard_state(self):
        normalized = " ".join(self.guide.split())
        for token in (
            r"$$\Delta T_{\mathrm{sub}}=T_{\mathrm{eq}}-T_{\mathrm{op}}$$",
            r"$$SI=\log_{10}\left(\frac{IAP}{K_{sp}}\right)$$",
            "both expressed on the same K or °C scale",
            "same numerical increment in K and °C",
            "dimensionless ion-activity product",
            "dimensionless thermodynamic solubility product",
            "same standard-state basis",
            "oil density in kg/m³",
            "produced-water chemistry in mg/L",
        ):
            self.assertIn(token, normalized)

    def test_screening_and_failure_boundaries_are_explicit(self):
        normalized = " ".join(self.guide.split())
        for token in (
            "no single result establishes that a line is safe",
            "not an engineering approval",
            "does not establish",
            "Do not transfer them to another fluid or water analysis",
            "lets calculation errors propagate",
            "not evidence that no hydrate or scale risk exists",
            "not nucleation time",
            "not predict how quickly a mineral precipitates",
            "required expert review",
        ):
            self.assertIn(token, normalized)


if __name__ == "__main__":
    unittest.main()
