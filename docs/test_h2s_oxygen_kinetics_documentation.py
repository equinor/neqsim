"""Contracts for the primary-source aqueous H2S/O2 kinetics guide."""

from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
GUIDE = ROOT / "docs/chemicalreactions/h2s_oxygen_kinetics.md"
PACKAGE_INDEX = ROOT / "docs/chemicalreactions/README.md"
IMPLEMENTATION = (
    ROOT
    / "src/main/java/neqsim/process/equipment/reactor/"
    / "AqueousHydrogenSulfideOxidationKinetics.java"
)
JAVA_TEST = (
    ROOT
    / "src/test/java/neqsim/process/equipment/reactor/"
    / "AqueousHydrogenSulfideOxidationKineticsTest.java"
)


class HydrogenSulfideOxygenKineticsDocumentationTest(unittest.TestCase):
    """Protect source fidelity, validity limits, and integration boundaries."""

    @classmethod
    def setUpClass(cls):
        cls.guide = GUIDE.read_text(encoding="utf-8")
        cls.package_index = PACKAGE_INDEX.read_text(encoding="utf-8")
        cls.implementation = IMPLEMENTATION.read_text(encoding="utf-8")
        cls.java_test = JAVA_TEST.read_text(encoding="utf-8")
        cls.normalized = " ".join(cls.guide.split())

    def test_source_equation_and_units_are_explicit(self):
        for token in (
            "doi:10.1021/es00159a003",
            r"\log_{10} k = 10.50 + 0.16",
            r"\frac{3000}{T} + 0.44\sqrt{I}",
            "kg-water mol-1 h-1 basis",
            "0.44",
            "0.49",
            "secondary value is not used",
            "0.18",
            "10^0.18 = 1.51356",
        ):
            self.assertIn(token, self.normalized)

    def test_published_domain_and_example_are_documented(self):
        for token in (
            "278.15–338.15 K",
            "pH 4–8",
            "0–6 mol/kg water",
            "air-saturated water/NaCl/seawater",
            "25 +/- 5 micromol/kg water",
            "atmospheric-pressure evidence",
            "123.6175 kg water/(mol h)",
            "22.4288 h",
            "example input",
            "not a new solubility correlation",
        ):
            self.assertIn(token, self.normalized)

    def test_implementation_mirrors_source_contract(self):
        for token in (
            'SOURCE_IDENTIFIER = "doi:10.1021/es00159a003"',
            "MINIMUM_TEMPERATURE_K = 278.15",
            "MAXIMUM_TEMPERATURE_K = 338.15",
            "MINIMUM_PH = 4.0",
            "MAXIMUM_PH = 8.0",
            "MAXIMUM_IONIC_STRENGTH_MOL_PER_KG_WATER = 6.0",
            "PUBLISHED_INITIAL_TOTAL_SULFIDE_MOLALITY = 25.0e-6",
            "PUBLISHED_INITIAL_TOTAL_SULFIDE_SPREAD = 5.0e-6",
            "LOG10_RATE_STANDARD_DEVIATION = 0.18",
            "SQRT_IONIC_STRENGTH_COEFFICIENT = 0.44",
            "public static double secondOrderRateConstant(",
            "public static RateConstantRange secondOrderRateConstantRange(",
            "public static ScreeningResult screenAirSaturatedExposure(",
            "Math.expm1(-exposure)",
        ):
            self.assertIn(token, self.implementation)

    def test_numerical_behavior_has_executable_java_coverage(self):
        for token in (
            "testPrimarySourceMetadataAndReferenceEquation",
            "testPublishedBoundariesAreInclusiveAndExtrapolationFailsClosed",
            "testRateIsMonotonicWithinThePublishedCorrelation",
            "testReportedLogRateScatterIsAppliedMultiplicatively",
            "testConstantOxygenExposureUsesExactPseudoFirstOrderSolution",
            "testLongExposureRemainsBoundedAndInputValidationFailsClosed",
        ):
            self.assertIn(token, self.java_test)

    def test_stop_boundary_prevents_pipeline_overclaim(self):
        for token in (
            "does not assign products or consume oxygen",
            "does not:",
            "calculate pH, H2S/HS- speciation",
            "calculate oxygen solubility",
            "qualify elevated pressure",
            "bind the correlation to experimental R1–R8 constants",
            "mutate a thermodynamic system",
            "issue #3144",
            "#2937",
            "#2911",
            "#3153",
            "requires separate phase, pressure, mass-transfer, and composition evidence",
        ):
            self.assertIn(token, self.normalized)

    def test_guide_is_discoverable(self):
        self.assertIn("h2s_oxygen_kinetics", self.package_index)
        self.assertIn("aqueous H2S/O2 kinetics guide", self.package_index)


if __name__ == "__main__":
    unittest.main()
