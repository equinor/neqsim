"""Regression contracts for the source-verified thermodynamic workflow guide."""

from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
GUIDE = ROOT / "docs" / "thermo" / "thermodynamic_workflows.md"


class ThermodynamicWorkflowsDocumentationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.guide = GUIDE.read_text(encoding="utf-8")

    def test_front_matter_is_complete_without_duplicate_h1(self):
        self.assertTrue(self.guide.startswith("---\ntitle:"))
        front_matter = self.guide.split("---", 2)[1]
        self.assertIn('description: "', front_matter)
        self.assertIn('keywords: "', front_matter)
        self.assertNotRegex(self.guide.split("---", 2)[2], r"(?m)^# ")

    def test_tbp_arguments_and_units_match_system_interface(self):
        self.assertIn(
            'fluid.addTBPfraction("C10", 0.10, 0.134, 0.792);',
            self.guide,
        )
        self.assertIn("molar mass [kg/mol], specific gravity [-]", self.guide)
        self.assertNotIn('addTBPfraction("C7+", 0.10, 0.45, 8.0)', self.guide)

        source = (
            ROOT / "src/main/java/neqsim/thermo/system/SystemInterface.java"
        ).read_text(encoding="utf-8")
        self.assertIn(
            "addTBPfraction(String componentName, double numberOfMoles, "
            "double molarMass, double density)",
            source.replace("\n", " "),
        )

    def test_database_and_plus_fraction_semantics_are_explicit(self):
        for contract in (
            "temporary component and interaction tables",
            "does not infer the molar mass or density",
            "Use `addPlusFraction(...)` for an unresolved plus fraction",
            "do not have the same characterization semantics",
        ):
            with self.subTest(contract=contract):
                self.assertIn(contract, self.guide)
        self.assertNotIn("enable access to component data", self.guide)
        self.assertNotIn("Always call `createDatabase(true)` before", self.guide)

    def test_mixing_rule_guidance_matches_current_enum(self):
        enum_source = (
            ROOT / "src/main/java/neqsim/thermo/mixingrule/EosMixingRuleType.java"
        ).read_text(encoding="utf-8")
        self.assertIn("NO(1)", enum_source)
        self.assertIn("CLASSIC(2)", enum_source)
        self.assertIn('setMixingRule("classic")', self.guide)
        self.assertIn("legacy value 1 is the", self.guide)
        self.assertIn("all binary interaction parameters set to zero", self.guide)
        for stale_mapping in (
            "`setMixingRule(2)`: Huron",
            "`setMixingRule(4)`: Wong",
            "`setMixingRule(7)`: Simplified",
        ):
            with self.subTest(stale_mapping=stale_mapping):
                self.assertNotIn(stale_mapping, self.guide)

    def test_flash_operations_are_anchored_in_current_source(self):
        source = (
            ROOT
            / "src/main/java/neqsim/thermodynamicoperations/ThermodynamicOperations.java"
        ).read_text(encoding="utf-8")
        for signature in (
            "void TPflash()",
            "void PHflash(double Hspec, String enthalpyUnit)",
            "void PSflash(double Sspec, String unit)",
            "void dewPointTemperatureFlash()",
            "void bubblePointPressureFlash()",
            "void calcPTphaseEnvelope()",
        ):
            with self.subTest(signature=signature):
                self.assertIn(signature, source)
        self.assertNotIn("calcPseudocriticalTemperature()", self.guide)
        self.assertIn(
            "there is no\ngeneral `ThermodynamicOperations.calcChemicalEquilibrium()`",
            self.guide,
        )

    def test_units_state_mutation_and_validation_boundaries_are_explicit(self):
        for contract in (
            'PHflash(hSpec, "J/kg")',
            'PSflash(sSpec, "J/kgK")',
            "use total extensive specifications",
            "operations update\nthat fluid's state",
            "phase indexes can change",
            "Molar mass and Z-factor alone do not validate",
        ):
            with self.subTest(contract=contract):
                self.assertIn(contract, self.guide)

    def test_clone_is_independent_and_not_presented_as_json_export(self):
        for contract in (
            "SystemInterface sweepCase = fluid.clone();",
            'sweepCase.setTemperature(280.0, "K");',
            'sweepCase.setPressure(10.0, "bara");',
            "Cloning is not JSON export",
            "original remains at its prior temperature and pressure",
        ):
            with self.subTest(contract=contract):
                self.assertIn(contract, self.guide)
        self.assertNotIn("Export an EOS state to JSON", self.guide)

    def test_complete_example_and_clone_have_executable_java_regressions(self):
        source = (
            ROOT
            / "src/test/java/neqsim/thermo/ThermodynamicWorkflowsDocumentationTest.java"
        ).read_text(encoding="utf-8")
        for contract in (
            "buildFlashAndReadExample",
            "cloneSweepKeepsOriginalState",
            'addTBPfraction("C10", 0.10, 0.134, 0.792)',
            'setMixingRule("classic")',
            "operations.TPflash()",
            "sweepOperations.TPflash()",
        ):
            with self.subTest(contract=contract):
                self.assertIn(contract, source)

    def test_changed_internal_links_resolve(self):
        for relative_target in (
            "fluid_creation_guide.md",
            "../pvtsimulation/phase_envelope_guide.md",
            "reactive_flash.md",
            "reading_fluid_properties.md",
        ):
            resolved = (GUIDE.parent / relative_target).resolve()
            with self.subTest(target=relative_target):
                self.assertTrue(resolved.is_file())


if __name__ == "__main__":
    unittest.main()
