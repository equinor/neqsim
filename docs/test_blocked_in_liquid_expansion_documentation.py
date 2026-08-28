"""Regression contracts for blocked-in liquid expansion documentation."""

import re
import unittest
from pathlib import Path
from urllib.parse import unquote


ROOT = Path(__file__).resolve().parents[1]
GUIDE = ROOT / "docs" / "safety" / "blocked_in_liquid_thermal_expansion.md"
SOURCE = ROOT / (
    "src/main/java/neqsim/process/util/fire/"
    "BlockedInLiquidExpansionAnalysis.java"
)
JAVA_TEST = ROOT / (
    "src/test/java/neqsim/process/util/fire/"
    "BlockedInLiquidExpansionAnalysisTest.java"
)
RELIEF_SOURCE = ROOT / (
    "src/main/java/neqsim/process/util/fire/ReliefValveSizing.java"
)


def resolve_internal_target(destination):
    target, _, fragment = unquote(destination).partition("#")
    raw_target = GUIDE.parent / target
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


class BlockedInLiquidExpansionDocumentationContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.guide = GUIDE.read_text(encoding="utf-8")
        cls.source = SOURCE.read_text(encoding="utf-8")
        cls.java_test = JAVA_TEST.read_text(encoding="utf-8")
        cls.relief_source = RELIEF_SOURCE.read_text(encoding="utf-8")

    def test_jekyll_structure_math_and_internal_links(self):
        self.assertTrue(self.guide.startswith("---\n"))
        self.assertEqual(
            self.guide.count("# Blocked-In Liquid Thermal Expansion Screening"),
            1,
        )
        self.assertEqual(self.guide.count("```") % 2, 0)
        without_fences = re.sub(
            r"```.*?```",
            "",
            self.guide,
            flags=re.DOTALL,
        )
        self.assertNotIn(r"\[", without_fences)
        self.assertNotIn(r"\]", without_fences)
        self.assertIn(
            r"$$dP=\frac{\beta}{\kappa}\,dT$$",
            without_fences,
        )
        for destination in re.findall(
            r"(?<!!)\[[^\]]+\]\(([^)]+)\)",
            self.guide,
        ):
            if destination.startswith(("http://", "https://", "mailto:")):
                continue
            target, _fragment = resolve_internal_target(destination)
            self.assertTrue(target.is_file())

    def test_public_methods_units_and_search_bounds_match_source(self):
        for signature in (
            "public static double[] computeIsochoricPressureProfile(",
            "public static double estimateThermalExpansionCoefficient(",
            "public static double estimateIsothermalCompressibility(",
            "public static double simplifiedPressureRise(",
        ):
            self.assertIn(signature, self.source)

        for source_token in (
            "PA_PER_BARA = 1.0e5",
            "DENSITY_RELATIVE_TOLERANCE = 1.0e-6",
            "MIN_SEARCH_PRESSURE_PA = 1.0e3",
            "MAX_SEARCH_PRESSURE_PA = 1.0e9",
            'state.setTemperature(temperatureK, "K")',
            'state.setPressure(pressurePa / PA_PER_BARA, "bara")',
            "SystemInterface state = template.clone()",
            "operations.TPflash()",
        ):
            self.assertIn(source_token, self.source)

        for guide_token in (
            "input temperatures as absolute K",
            "absolute pressures in Pa",
            "canonical pressure from bara to Pa",
            "relative density error below `1.0e-6`",
            "between `1.0e3` Pa and `1.0e9` Pa",
            "does not modify the supplied `SystemInterface`",
        ):
            self.assertIn(guide_token, self.guide)

    def test_executable_workflow_is_exactly_covered(self):
        for guide_token in (
            "new SystemSrkEos(referenceTemperatureK, referencePressureBara)",
            'liquid.addComponent("propane", 1.0)',
            "referenceTemperatureK + 10.0",
            "liquid, 0.5",
            "liquid, 2.0e5",
            "comparisonTemperatureRiseK = 5.0",
        ):
            self.assertIn(guide_token, self.guide)

        for test_token in (
            "testIsochoricPressureProfileIsMonotonicAndMatchesInitialState",
            "testIsochoricMarchMatchesSimplifiedBetaOverKappaEstimateNearReferenceState",
            "Double.isFinite(pressurePa)",
            "fluid.getTemperature()",
            "fluid.getPressure()",
            "relativeDifference < 0.3",
        ):
            self.assertIn(test_token, self.java_test)

    def test_phase_failure_and_validation_boundaries_are_explicit(self):
        normalized_guide = " ".join(self.guide.split())
        for guide_token in (
            "does not prove that every trial or result is a single liquid phase",
            "may prevent bracketing and raise",
            "Treat a bracket failure as a failed screen",
            "not experimental validation",
            "not a universal 30% acceptance criterion",
            "fixed mass and rigid volume",
            "pipe/vessel elasticity",
        ):
            self.assertIn(guide_token, normalized_guide)

        self.assertNotIn("accurate over large temperature spans", normalized_guide)
        self.assertNotIn(
            "within roughly 30% for moderate temperature spans",
            normalized_guide,
        )

    def test_relief_sizing_handoff_does_not_invent_flow(self):
        self.assertIn(
            "public static LiquidPSVSizingResult calculateLiquidReliefArea(",
            self.relief_source,
        )
        for source_token in (
            "@param volumeFlowRate Volume flow rate at relieving conditions [m3/s]",
            "@param liquidDensity Liquid density at relieving conditions [kg/m3]",
            "@param setPressure PSV set pressure [Pa absolute]",
            "@param backPressure Downstream/back pressure [Pa absolute]",
            "@param viscosity Dynamic viscosity [Pa*s]",
        ):
            self.assertIn(source_token, self.relief_source)

        for guide_token in (
            "supplies no heat-input model",
            "cannot be inferred from a pressure rise alone",
            "independently established liquid volume flow",
            "m³/s",
            "absolute set and back pressures in Pa",
            "viscosity in Pa·s",
        ):
            self.assertIn(guide_token, self.guide)


if __name__ == "__main__":
    unittest.main()
