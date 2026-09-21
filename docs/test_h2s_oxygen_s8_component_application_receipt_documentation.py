from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
GUIDE = ROOT / "docs/chemicalreactions/h2s_oxygen_s8_component_application_receipt.md"
SOURCE = ROOT / (
    "src/main/java/neqsim/process/equipment/reactor/"
    "AqueousHydrogenSulfideOxidationS8ComponentApplicationReceipt.java"
)
TEST = ROOT / (
    "src/test/java/neqsim/process/equipment/reactor/"
    "AqueousHydrogenSulfideOxidationS8ComponentApplicationReceiptTest.java"
)


class S8ComponentApplicationReceiptDocumentationTest(unittest.TestCase):
    def test_guide_defines_identity_and_numeric_contract(self):
        text = GUIDE.read_text(encoding="utf-8")
        for token in (
            "observed target-state identifier",
            "application idempotency key",
            "normalized component identity",
            "8-ULP comparison gate",
            "SystemInterface.getTotalNumberOfMoles()",
            "compensated summation",
            "double application",
        ):
            self.assertIn(token, text)

    def test_guide_preserves_executor_and_chemistry_boundaries(self):
        text = GUIDE.read_text(encoding="utf-8")
        for token in (
            "does not authenticate the target",
            "does not claim exactly-once execution",
            "does not call `SystemInterface.addComponent`",
            "not an executor",
            "not a measured sulfur yield",
            "#3144, #2937, #2911, pipeline work, and #3153",
        ):
            self.assertIn(token, text)

    def test_source_reads_systems_without_mutating_them(self):
        text = SOURCE.read_text(encoding="utf-8")
        for token in (
            "public static Result verify(",
            "SystemInterface priorTarget",
            "SystemInterface candidateTarget",
            "getNumberOfComponents()",
            "getNumberOfmoles()",
            "getTotalNumberOfMoles()",
            "TreeMap<String, Double>",
            "getPlanApplicationResidualMol()",
            "getMaximumNonS8InventoryResidualMol()",
            "implements Serializable",
        ):
            self.assertIn(token, text)
        for forbidden in (
            ".addComponent(",
            "ThermodynamicOperations",
            "TPSolidflash",
            "SulfurDepositionAnalyser",
            "SulfurFilter",
        ):
            self.assertNotIn(forbidden, text)

    def test_java_tests_cover_acceptance_and_fail_closed_cases(self):
        text = TEST.read_text(encoding="utf-8")
        for method in (
            "testStrictAppendVerifiesExternalApplicationWithoutMutation",
            "testUnchangedPlanAcceptsExactSameInventory",
            "testReorderedComponentInventoryIsAccepted",
            "testWrongIdentityAndInventoryChangesFailClosed",
            "testPositiveApplicationRejectsAliasedBeforeAndAfterSystem",
            "testReceiptDeterminismAndSerializationPreserveBitwiseEvidence",
        ):
            self.assertIn(method, text)


if __name__ == "__main__":
    unittest.main()
