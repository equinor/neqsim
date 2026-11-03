"""Regression contracts for the dew-point standards guide."""

from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]
GUIDE = ROOT / "docs" / "standards" / "dew_point_standards.md"
REFERENCE_INDEX = ROOT / "docs" / "REFERENCE_MANUAL_INDEX.md"
STANDARD_WATER_SOURCE = (
    ROOT
    / "src"
    / "main"
    / "java"
    / "neqsim"
    / "standards"
    / "gasquality"
    / "Standard_ISO18453.java"
)
DRAFT_WATER_SOURCE = STANDARD_WATER_SOURCE.with_name("Draft_ISO18453.java")
HYDROCARBON_SOURCE = STANDARD_WATER_SOURCE.with_name(
    "BestPracticeHydrocarbonDewPoint.java"
)
JAVA_REGRESSION = (
    ROOT
    / "src"
    / "test"
    / "java"
    / "neqsim"
    / "standards"
    / "gasquality"
    / "DewPointStandardsDocumentationTest.java"
)
GAS_QUALITY_AGENT = ROOT / ".github" / "agents" / "gas-quality.agent.md"
STANDARDS_SKILL = (
    ROOT / ".github" / "skills" / "neqsim-standards-lookup" / "SKILL.md"
)
FLOW_ASSURANCE_SKILL = STANDARDS_SKILL.parent.parent / "neqsim-flow-assurance" / "SKILL.md"
CAPABILITY_MAP = STANDARDS_SKILL.parent.parent / "neqsim-capability-map" / "SKILL.md"
INPUT_VALIDATION_SKILL = STANDARDS_SKILL.parent.parent / "neqsim-input-validation" / "SKILL.md"
OPERATIONS_SOURCE = (
    ROOT / "src" / "main" / "java" / "neqsim" / "thermodynamicoperations"
    / "ThermodynamicOperations.java"
)


class DewPointStandardsDocumentationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.guide = GUIDE.read_text(encoding="utf-8")
        cls.reference_index = REFERENCE_INDEX.read_text(encoding="utf-8")
        cls.water_source = STANDARD_WATER_SOURCE.read_text(encoding="utf-8")
        cls.draft_source = DRAFT_WATER_SOURCE.read_text(encoding="utf-8")
        cls.hydrocarbon_source = HYDROCARBON_SOURCE.read_text(encoding="utf-8")
        cls.java_regression = JAVA_REGRESSION.read_text(encoding="utf-8")

    def test_water_agent_routes_to_the_model_setup_rule(self):
        agent = GAS_QUALITY_AGENT.read_text(encoding="utf-8")
        front_matter = agent.split("---", 2)[1]
        self.assertIn("- neqsim-standards-lookup", front_matter)
        self.assertIn("ISO 18453", front_matter)
        self.assertIn("GERG-water", front_matter)
        iso_row = next(
            line for line in agent.splitlines() if line.startswith("| ISO 18453 |")
        )
        self.assertIn("Water dew point from water content", iso_row)
        self.assertNotIn("Hydrocarbon", iso_row)
        self.assertIn("ISO 18453 / GERG-water execution rule", agent)
        self.assertIn("setPressure(pressureBara)", agent)

    def test_agent_setup_matches_the_wrapper_and_direct_public_api(self):
        skill = STANDARDS_SKILL.read_text(encoding="utf-8")
        operations = OPERATIONS_SOURCE.read_text(encoding="utf-8")
        rule = skill.split("### ISO 18453 / GERG-water execution rule", 1)[1]
        rule = rule.split("\n### ", 1)[0]
        for token in (
            "Standard_ISO18453",
            "SystemGERGwaterEos",
            "setMixingRule(8)",
            "setPressure(pressureBara)",
            'getValue("dewPointTemperature", "C")',
            "waterDewPointTemperatureFlash()",
        ):
            self.assertIn(token, rule)
        self.assertIn("new SystemGERGwaterEos(", self.water_source)
        self.assertIn("setMixingRule(8)", self.water_source)
        self.assertIn("public void waterDewPointTemperatureFlash()", operations)
        self.assertIn("wet-gas or dry-gas basis", rule)
        self.assertIn("273.16 K", rule)
        self.assertIn("ice reference", rule)
        self.assertIn("unknown correlation uncertainty", rule)

    def test_related_agent_routes_do_not_restore_the_old_solver_warning(self):
        flow_skill = FLOW_ASSURANCE_SKILL.read_text(encoding="utf-8")
        capability_map = CAPABILITY_MAP.read_text(encoding="utf-8")
        self.assertNotIn("SystemGERGwaterEos.waterDewPointTemperatureFlash()", flow_skill)
        self.assertNotIn("is unreliable below 0 C", flow_skill)
        self.assertIn("former non-convergent solver", flow_skill)
        self.assertIn("setMixingRule(8)", flow_skill)
        self.assertNotIn("ops.waterDewPointTemperature()", capability_map)
        self.assertIn("ops.waterDewPointTemperatureFlash()", capability_map)
        for source in (GAS_QUALITY_AGENT, STANDARDS_SKILL):
            text = source.read_text(encoding="utf-8")
            targets = re.findall(r"\[[^\]]+\]\(([^)]+)\)", text)
            dew_point_targets = [
                target for target in targets if "dew_point_standards.md" in target
            ]
            self.assertTrue(dew_point_targets, source)
            for target in dew_point_targets:
                self.assertEqual((source.parent / target).resolve(), GUIDE.resolve())

    def test_input_validation_preserves_the_named_iso_model_basis(self):
        skill = INPUT_VALIDATION_SKILL.read_text(encoding="utf-8")
        mixing_rule = skill.split("### Mixing Rule", 1)[1].split("\n## ", 1)[0]
        self.assertIn("SystemGERGwaterEos", mixing_rule)
        self.assertIn("setMixingRule(8)", mixing_rule)
        self.assertIn("governing named standard", mixing_rule)
        self.assertNotIn('Always call `fluid.setMixingRule("classic")`', skill)
        self.assertNotIn("When in doubt, CPA", skill)
        self.assertIn("../neqsim-standards-lookup/SKILL.md#", mixing_rule)
        iso_rows = [
            line for line in skill.splitlines()
            if line.startswith("| ISO 18453 / GERG-water")
        ]
        self.assertEqual(2, len(iso_rows))
        for row in iso_rows:
            self.assertIn("SystemGERGwaterEos", row)

    def test_guide_has_searchable_front_matter_without_duplicate_h1(self):
        self.assertTrue(self.guide.startswith("---\ntitle:"))
        front_matter = self.guide.split("---", 2)[1]
        self.assertIn("description:", front_matter)
        self.assertIn("keywords:", front_matter)
        body = self.guide.split("---", 2)[2]
        body_without_fences = re.sub(
            r"```.*?```", "", body, flags=re.DOTALL
        )
        self.assertNotRegex(body_without_fences, r"(?m)^# ")

    def test_water_path_uses_current_source_api_and_marks_legacy_class(self):
        for token in (
            "public class Standard_ISO18453",
            "public void setPressure(double pressureBara)",
            "waterDewPointTemperatureFlash()",
            '"K".equals(returnUnit)',
            '"F".equals(returnUnit)',
        ):
            self.assertIn(token, self.water_source)
        self.assertIn("public class Draft_ISO18453", self.draft_source)
        self.assertIn("new direct calculations should use", self.guide)
        self.assertIn("Standard_ISO18453", self.guide)
        self.assertIn("Draft_ISO18453", self.guide)

    def test_hydrocarbon_pressure_and_model_boundaries_match_source(self):
        for token in (
            "double specPressure = 50.0;",
            "new SystemSrkEos(initTemperature, specPressure)",
            "setMixingRule(2)",
            "setPressure(specPressure)",
            'equals("water")',
        ):
            self.assertIn(token, self.hydrocarbon_source)
        self.assertIn("fixed `specPressure` of 50.0 bara", self.guide)
        self.assertIn(
            "`setReferencePressure(double)` method does not change",
            self.guide,
        )
        self.assertNotIn("for (double P : pressures)", self.guide)
        self.assertIn("A dew point at 50 bara is not the cricondentherm", self.guide)

    def test_compliance_guidance_fails_closed_on_current_is_on_spec_behavior(self):
        self.assertIn(
            "getSalesContract().getWaterDewPointTemperature()",
            self.hydrocarbon_source,
        )
        self.assertIn(
            "Do not use `BestPracticeHydrocarbonDewPoint.isOnSpec()`",
            self.guide,
        )
        self.assertIn("Double.isFinite(waterDewPointC)", self.guide)
        self.assertIn("Double.isFinite(hydrocarbonDewPointC)", self.guide)
        self.assertNotIn("hcDP.isOnSpec()", self.guide)
        self.assertNotRegex(self.guide, r"Typical .* Specifications")

    def test_complete_examples_are_protected_by_executable_java_regressions(self):
        for token in (
            "class DewPointStandardsDocumentationTest",
            "documentedWaterDewPointWorkflow",
            "documentedHydrocarbonDewPointWorkflow",
            "hydrocarbonReferencePressureSetterDoesNotChangeFixedPressure",
        ):
            self.assertIn(token, self.java_regression)
        self.assertIn("Standard_ISO18453 waterDewPoint", self.guide)
        self.assertIn(
            "BestPracticeHydrocarbonDewPoint hydrocarbonDewPoint",
            self.guide,
        )

    def test_related_links_resolve_and_guide_remains_indexed(self):
        for target in re.findall(r"\[[^\]]+\]\(([^)]+)\)", self.guide):
            target_path = target.split("#", 1)[0]
            if "://" in target_path or target_path.startswith("#"):
                continue
            resolved = (GUIDE.parent / target_path).resolve()
            with self.subTest(target=target):
                self.assertTrue(resolved.is_file(), resolved)
        self.assertIn(
            "standards/dew_point_standards.md", self.reference_index
        )


if __name__ == "__main__":
    unittest.main()
