from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]
GUIDE = ROOT / "docs/chemicalreactions/h2s_oxygen_s8_stream_application_batch_preview.md"
SOURCE = ROOT / (
    "src/main/java/neqsim/process/equipment/reactor/"
    "AqueousHydrogenSulfideOxidationS8StreamApplicationBatchPreview.java"
)
TEST = ROOT / (
    "src/test/java/neqsim/process/equipment/reactor/"
    "AqueousHydrogenSulfideOxidationS8StreamApplicationBatchPreviewTest.java"
)


class S8StreamApplicationBatchPreviewDocumentationTest(unittest.TestCase):
    def test_guide_defines_ordered_fail_closed_contract(self):
        normalized = " ".join(GUIDE.read_text(encoding="utf-8").split())
        for token in (
            "ordered set of independently verified S8 stream additions",
            "caller-owned streams and fluids are never mutated",
            "Source order is preserved",
            "target-state identifiers must be unique",
            "application idempotency keys must be unique",
            "fails the complete batch without exposing a partial result",
            "ULP-scaled tolerance",
        ):
            self.assertIn(token, normalized)

    def test_guide_preserves_process_and_scientific_boundaries(self):
        normalized = " ".join(GUIDE.read_text(encoding="utf-8").split())
        for token in (
            "not an atomic transaction",
            "does not run or flash streams",
            "does not claim exactly-once execution",
            "adds no product yield",
            "#3144, #2937, #2911, pipeline work, and #3153",
        ):
            self.assertIn(token, normalized)

    def test_source_delegates_and_exposes_aggregate_evidence(self):
        text = SOURCE.read_text(encoding="utf-8")
        for token in (
            "public static Result applyToClones(List<Request> requests)",
            "getStrictAppendCount()",
            "getUnchangedCount()",
            "getPlannedS8IncrementMol()",
            "getObservedS8IncrementMol()",
            "getClosureResidualMol()",
            "implements Serializable",
        ):
            self.assertIn(token, text)
        self.assertRegex(
            text,
            re.compile(
                r"AqueousHydrogenSulfideOxidationS8StreamApplicationPreview\s*"
                r"\.applyToClone\("
            ),
        )
        self.assertNotIn(".run(", text)
        self.assertNotIn("runTPflash(", text)
        self.assertNotIn("ProcessSystem", text)

    def test_java_tests_cover_acceptance_and_fail_closed_cases(self):
        text = TEST.read_text(encoding="utf-8")
        for method in (
            "testOrderedBatchClosesOneAppendAndOneUnchangedEntry",
            "testCallerStreamsAndResultCandidatesRemainIndependent",
            "testDuplicateTargetAndApplicationIdentitiesFailClosed",
            "testInvalidBatchEntryAndCloneFailWithoutMutation",
            "testMismatchedObservedIdentityFailsCompleteBatch",
            "testDeterministicEvidenceAndSerializationRoundTrip",
        ):
            self.assertIn(method, text)


if __name__ == "__main__":
    unittest.main()
