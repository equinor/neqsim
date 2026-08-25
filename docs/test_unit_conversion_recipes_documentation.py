"""Regression contracts for the unit-conversion cookbook page."""

from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]
GUIDE = ROOT / "docs" / "cookbook" / "unit-conversion-recipes.md"
JAVA_TEST = (
    ROOT
    / "src"
    / "test"
    / "java"
    / "neqsim"
    / "util"
    / "unit"
    / "UnitConversionRecipesDocumentationTest.java"
)


class UnitConversionRecipesDocumentationContracts(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.guide = GUIDE.read_text(encoding="utf-8")

    def test_front_matter_and_heading_structure(self):
        self.assertTrue(self.guide.startswith("---\n"))
        self.assertEqual(0, len(re.findall(r"^# ", self.guide, re.MULTILINE)))
        self.assertEqual(0, len(re.findall(r"^```", self.guide, re.MULTILINE)) % 2)

    def test_unit_bearing_getters_replace_manual_conversions(self):
        for token in (
            'getTemperature("C")',
            'getPressure("psia")',
            'getDensity("kg/m3")',
            'getFlowRate("kg/sec")',
        ):
            self.assertIn(token, self.guide)
        self.assertNotIn("getTemperature() - 273.15", self.guide)
        self.assertNotIn("getPressure() * 14.504", self.guide)

    def test_source_backed_case_sensitive_scalar_units(self):
        self.assertIn('PowerUnit(1.0, "hp")', self.guide)
        self.assertNotIn('PowerUnit(1.0, "HP")', self.guide)
        self.assertIn('LengthUnit(1.0, "m").getValue("ft")', self.guide)

        power_source = (
            ROOT / "src/main/java/neqsim/util/unit/PowerUnit.java"
        ).read_text(encoding="utf-8")
        length_source = (
            ROOT / "src/main/java/neqsim/util/unit/LengthUnit.java"
        ).read_text(encoding="utf-8")
        self.assertIn('case "hp":', power_source)
        self.assertNotIn('case "HP":', power_source)
        self.assertIn('"ft".equals(name)', length_source)

    def test_flow_and_pressure_basis_boundaries_are_explicit(self):
        for phrase in (
            "absolute from gauge pressure",
            "actual from standard volume",
            "standard-state temperature and pressure",
            "composition, molar mass, density, or standard conditions",
        ):
            self.assertIn(phrase, self.guide)

    def test_global_profiles_are_not_described_as_calculation_defaults(self):
        self.assertIn("process-wide static symbol map", self.guide)
        self.assertIn("global mutable state", self.guide)
        self.assertIn("Units.activateDefaultUnits()", self.guide)
        self.assertNotIn("After setting, display methods use these units", self.guide)

    def test_links_and_executable_regression_are_present(self):
        for target in (
            ROOT / "docs/thermo/reading_fluid_properties.md",
            ROOT / "docs/troubleshooting/index.md",
        ):
            self.assertTrue(target.is_file(), target)
        java = JAVA_TEST.read_text(encoding="utf-8")
        for token in (
            'getTemperature("C")',
            'getPressure("bara")',
            'getFlowRate("kg/sec")',
            'new LengthUnit(1.0, "m")',
            'new PowerUnit(1.0, "hp")',
            "activateDefaultUnits()",
        ):
            self.assertIn(token, java)


if __name__ == "__main__":
    unittest.main()
