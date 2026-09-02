"""Contracts for the qualified CO2 impurity-kinetics execution guide."""

from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
GUIDE = ROOT / "docs/chemicalreactions/co2_impurity_qualified_execution.md"
PACKAGE_INDEX = ROOT / "docs/chemicalreactions/README.md"
IMPLEMENTATION = (
    ROOT
    / "src/main/java/neqsim/process/equipment/reactor/"
    / "QualifiedCO2ImpurityKineticReactor.java"
)
JAVA_TEST = (
    ROOT
    / "src/test/java/neqsim/process/equipment/reactor/"
    / "QualifiedCO2ImpurityKineticReactorTest.java"
)


class QualifiedCO2ImpurityExecutionDocumentationTest(unittest.TestCase):
    """Protect fail-closed semantics, discoverability, and scientific limits."""

    @classmethod
    def setUpClass(cls):
        cls.guide = GUIDE.read_text(encoding="utf-8")
        cls.package_index = PACKAGE_INDEX.read_text(encoding="utf-8")
        cls.implementation = IMPLEMENTATION.read_text(encoding="utf-8")
        cls.java_test = JAVA_TEST.read_text(encoding="utf-8")
        cls.normalized = " ".join(cls.guide.split())

    def test_guide_distinguishes_illustrative_and_qualified_paths(self):
        for token in (
            "illustrative Arrhenius parameters",
            "not validated engineering correlations",
            "fail-closed execution path",
            "R1, R2, R3A, R3B, and R4-R7",
            "R8CS",
            "R8SS",
            "public source",
            "validation status",
        ):
            self.assertIn(token, self.normalized)

    def test_parameter_mutation_and_material_rules_are_documented(self):
        for token in (
            "automatically removes the qualification",
            "currently selected material family",
            "requires its own evidence",
            "Both `run()` and `run(UUID)`",
            "inlet temperature and absolute pressure",
        ):
            self.assertIn(token, self.normalized)

    def test_scientific_stop_boundary_is_explicit(self):
        for token in (
            "adds no kinetic constant",
            "Merely registering metadata does not make the illustrative defaults valid",
            "does not calculate gas-to-water transfer",
            "free-water appearance",
            "electrolyte activities",
            "pipeline source terms",
            "Facility-specific Northern Lights data must not be inserted",
        ):
            self.assertIn(token, self.normalized)

    def test_documented_behavior_has_executable_java_coverage(self):
        for token in (
            "public void setReactionQualification(",
            "public String[] getUnqualifiedReactionIds(",
            "public void requireValidatedKineticsAt(",
            "qualifications.remove(normalizedId)",
            "public void run(UUID id)",
        ):
            self.assertIn(token, self.implementation)

        for token in (
            "testEmptyRegistryFailsClosedWithAllRequiredIds",
            "testCompleteValidatedRegistryPassesDeterministically",
            "testChangingConstantsInvalidatesOnlyReplacedParameterization",
            "testMaterialSelectionUsesOnlySelectedR8Family",
        ):
            self.assertIn(token, self.java_test)

    def test_guide_is_discoverable(self):
        self.assertIn("co2_impurity_qualified_execution", self.package_index)
        self.assertIn("qualified CO₂ impurity execution guide", self.package_index)


if __name__ == "__main__":
    unittest.main()
