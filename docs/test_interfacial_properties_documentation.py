"""Regression contracts for the interfacial-properties guide."""

from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]
GUIDE = ROOT / "docs" / "physical_properties" / "interfacial_properties.md"
INTERFACE_PROPERTIES = (
    ROOT
    / "src/main/java/neqsim/physicalproperties/interfaceproperties"
    / "InterfaceProperties.java"
)
SYSTEM_THERMO = ROOT / "src/main/java/neqsim/thermo/system/SystemThermo.java"
ISOTHERM_TYPE = (
    ROOT
    / "src/main/java/neqsim/physicalproperties/interfaceproperties"
    / "solidadsorption/IsothermType.java"
)
JAVA_REGRESSION = (
    ROOT
    / "src/test/java/neqsim/physicalproperties/interfaceproperties"
    / "InterfacialPropertiesDocumentationTest.java"
)


class InterfacialPropertiesDocumentationContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.guide = GUIDE.read_text(encoding="utf-8")
        cls.interface_source = INTERFACE_PROPERTIES.read_text(encoding="utf-8")
        cls.system_source = SYSTEM_THERMO.read_text(encoding="utf-8")
        cls.isotherm_source = ISOTHERM_TYPE.read_text(encoding="utf-8")
        cls.java_regression = JAVA_REGRESSION.read_text(encoding="utf-8")

    def test_search_metadata_and_local_links_are_valid(self):
        self.assertTrue(self.guide.startswith("---\ntitle:"))
        front_matter = self.guide.split("---", 2)[1]
        self.assertIn("description:", front_matter)
        self.assertIn("keywords:", front_matter)
        body = self.guide.split("---", 2)[2]
        body_without_fences = re.sub(
            r"```.*?```", "", body, flags=re.DOTALL
        )
        self.assertNotRegex(body_without_fences, r"(?m)^# ")

        for target in re.findall(r"\[[^\]]+\]\(([^)]+)\)", self.guide):
            target_path = target.split("#", 1)[0]
            if not target_path or "://" in target_path:
                continue
            resolved = (GUIDE.parent / target_path).resolve()
            with self.subTest(target=target):
                self.assertTrue(resolved.is_file(), resolved)

    def test_model_names_and_ordered_interface_pairs_match_source(self):
        model_names = (
            "Parachor",
            "Weinaug-Katz",
            "Full Gradient Theory",
            "Simple Gradient Theory",
            "Linear Gradient Theory",
            "cDFT",
            "Classical DFT",
            "Firozabadi Ramley",
        )
        for model in model_names:
            with self.subTest(model=model):
                self.assertIn(f'"{model}"', self.interface_source)
                self.assertIn(f'`{model}`', self.guide)

        for pair in (
            '("gas", "oil")',
            '("gas", "aqueous")',
            '("oil", "aqueous")',
        ):
            with self.subTest(pair=pair):
                self.assertIn(pair, self.guide)

        self.assertIn("exact, case-sensitive model names", self.guide)
        self.assertIn("unknown model name currently", self.guide)
        self.assertIn("unknown or reversed interface pair", self.guide)

    def test_units_and_dispatch_boundaries_are_explicit(self):
        self.assertIn("TODO: add unit conversion", self.interface_source)
        self.assertIn("return val;", self.interface_source)
        self.assertIn("does not convert", self.guide)
        self.assertIn("sigmaNPerM * 1000.0", self.guide)
        self.assertIn("pass the gas phase first", self.guide)
        self.assertIn("Do not enumerate arbitrary `i, j`", self.guide)
        self.assertNotIn('getSurfaceTension(0, 1, "mN/m")', self.guide)

    def test_missing_phase_behavior_and_safe_example_match_system_api(self):
        for token in (
            "if (hasPhaseType(phase1) && hasPhaseType(phase2))",
            "return Double.NaN;",
        ):
            self.assertIn(token, self.system_source)
        for token in (
            'hasPhaseType("gas")',
            'getPhaseNumberOfPhase("gas")',
            "Double.isFinite(sigmaNPerM)",
            "operations.bubblePointPressureFlash(false)",
        ):
            self.assertIn(token, self.guide)

    def test_numbered_sets_match_current_implementation(self):
        for model_set in range(6):
            self.assertIn(
                f"interfacialTensionModel == {model_set}",
                self.interface_source,
            )
            self.assertRegex(self.guide, rf"(?m)^\| {model_set} \|")
        self.assertIn("does not select a model from pressure", self.guide)
        self.assertIn("Values outside 0-5 select Parachor", self.guide)

    def test_adsorption_is_separated_and_all_enum_choices_are_visible(self):
        for isotherm in (
            "DRA",
            "LANGMUIR",
            "EXTENDED_LANGMUIR",
            "FREUNDLICH",
            "BET",
            "SIPS",
        ):
            with self.subTest(isotherm=isotherm):
                self.assertIn(isotherm, self.isotherm_source)
                self.assertIn(f"`{isotherm}`", self.guide)
        self.assertIn("do not calculate fluid-fluid IFT", self.guide)
        self.assertIn("parameter provenance", self.guide)

    def test_complete_workflow_has_executable_java_regression(self):
        for token in (
            "class InterfacialPropertiesDocumentationTest",
            "documentedParachorWorkflowUsesNamedPhaseOrder",
            "documentedCDFTAliasesSelectCDFT",
            "missingNamedPhaseReturnsNaN",
        ):
            self.assertIn(token, self.java_regression)


if __name__ == "__main__":
    unittest.main()

