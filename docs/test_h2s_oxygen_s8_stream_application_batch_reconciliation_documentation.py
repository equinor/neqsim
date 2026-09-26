from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]
GUIDE = ROOT / (
    "docs/chemicalreactions/h2s_oxygen_s8_stream_application_batch_reconciliation.md"
)
SOURCE = ROOT / (
    "src/main/java/neqsim/process/equipment/reactor/"
    "AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliation.java"
)
TEST = ROOT / (
    "src/test/java/neqsim/process/equipment/reactor/"
    "AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationTest.java"
)


class S8StreamApplicationBatchReconciliationDocumentationTest(unittest.TestCase):
    def test_guide_defines_ordered_fail_closed_contract(self):
        normalized = " ".join(GUIDE.read_text(encoding="utf-8").split())
        for token in (
            "matches the previously qualified detached batch preview",
            "never mutates, runs, or flashes",
            "same non-zero size and source order",
            "Reordering fails closed",
            "fresh unmodifiable ordered entry list",
            "ULP-scaled tolerances",
        ):
            self.assertIn(token, normalized)

    def test_guide_preserves_scientific_and_ownership_boundaries(self):
        normalized = " ".join(GUIDE.read_text(encoding="utf-8").split())
        for token in (
            "not an executor",
            "does not call `addComponent`",
            "adds no kinetic or thermodynamic model",
            "claim exactly-once execution",
            "#3144, #2937, #2911, pipeline work, and #3153",
        ):
            self.assertIn(token, normalized)

    def test_source_reconciles_identity_and_numerical_evidence(self):
        text = SOURCE.read_text(encoding="utf-8")
        for token in (
            "public static Result reconcile(",
            "requireSameProvenance(expected, observed)",
            "getTransitionDigestHex()",
            "getCandidateS8ResidualMol()",
            "getTotalAmountResidualMol()",
            "getMaximumEntryTotalAmountResidualMol()",
            "implements Serializable",
        ):
            self.assertIn(token, text)
        self.assertNotIn("addComponent(", text)
        self.assertNotIn(".run(", text)
        self.assertNotIn("runTPflash(", text)

    def test_java_tests_cover_acceptance_and_fail_closed_cases(self):
        text = TEST.read_text(encoding="utf-8")
        for method in (
            "testOrderedPreviewAndApplicationReconcile",
            "testReorderedApplicationFailsClosed",
            "testDifferentTransitionAndInvalidEvidenceFailClosed",
            "testResultIsImmutableDeterministicAndSerializable",
        ):
            self.assertRegex(text, re.compile(r"void\s+" + method + r"\s*\("))


if __name__ == "__main__":
    unittest.main()
