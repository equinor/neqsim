from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]
GUIDE = ROOT / "docs/chemicalreactions/h2s_oxygen_s8_stream_application_batch_receipt.md"
SOURCE = ROOT / (
    "src/main/java/neqsim/process/equipment/reactor/"
    "AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReceipt.java"
)
TEST = ROOT / (
    "src/test/java/neqsim/process/equipment/reactor/"
    "AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReceiptTest.java"
)


class S8StreamApplicationBatchReceiptDocumentationTest(unittest.TestCase):
    def test_guide_defines_ordered_fail_closed_contract(self):
        normalized = " ".join(GUIDE.read_text(encoding="utf-8").split())
        for token in (
            "ordered set of externally applied S8 additions",
            "never mutates, runs, or flashes",
            "Positive applications require distinct caller-provided before and after",
            "fails closed on null or duplicate identities",
            "preserves source order",
            "ULP-scaled tolerances",
        ):
            self.assertIn(token, normalized)

    def test_guide_preserves_scientific_and_ownership_boundaries(self):
        normalized = " ".join(GUIDE.read_text(encoding="utf-8").split())
        for token in (
            "not an executor",
            "does not call `addComponent`",
            "adds no kinetic or thermodynamic model",
            "does not claim exactly-once execution",
            "#3144, #2937, #2911, pipeline work, and #3153",
        ):
            self.assertIn(token, normalized)

    def test_source_delegates_and_exposes_aggregate_evidence(self):
        text = SOURCE.read_text(encoding="utf-8")
        for token in (
            "public static Result verify(List<Request> requests)",
            "getReceipts()",
            "getStrictAppendCount()",
            "getUnchangedCount()",
            "getObservedTotalIncrementMol()",
            "getS8ClosureResidualMol()",
            "getTotalAmountClosureResidualMol()",
            "implements Serializable",
        ):
            self.assertIn(token, text)
        self.assertRegex(
            text,
            re.compile(
                r"AqueousHydrogenSulfideOxidationS8ComponentApplicationReceipt\s*"
                r"\.verify\("
            ),
        )
        self.assertNotIn(".run(", text)
        self.assertNotIn("runTPflash(", text)
        self.assertNotIn("ProcessSystem", text)

    def test_java_tests_cover_acceptance_and_fail_closed_cases(self):
        text = TEST.read_text(encoding="utf-8")
        for method in (
            "testOrderedBatchClosesAppendAndUnchangedEntries",
            "testCallerStreamsAndCapturedSnapshotsRemainIndependent",
            "testDuplicateTargetAndApplicationIdentitiesFailClosed",
            "testWrongIdentityAndInventoryChangesFailCompleteBatch",
            "testNullAliasAndCloneFailuresAreRejected",
            "testDeterministicEvidenceAndSerializationRoundTrip",
        ):
            self.assertIn(method, text)


if __name__ == "__main__":
    unittest.main()
