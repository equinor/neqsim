from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]
GUIDE = ROOT / "docs/chemicalreactions/h2s_oxygen_s8_stream_application_preview.md"
SOURCE = ROOT / (
    "src/main/java/neqsim/process/equipment/reactor/"
    "AqueousHydrogenSulfideOxidationS8StreamApplicationPreview.java"
)
TEST = ROOT / (
    "src/test/java/neqsim/process/equipment/reactor/"
    "AqueousHydrogenSulfideOxidationS8StreamApplicationPreviewTest.java"
)


class S8StreamApplicationPreviewDocumentationTest(unittest.TestCase):
    def test_guide_defines_detached_stream_clone_contract(self):
        text = GUIDE.read_text(encoding="utf-8")
        normalized = " ".join(text.split())
        for token in (
            "caller-owned `StreamInterface`",
            "cloned fluid does not alias the original",
            "original stream and its original fluid are never mutated",
            "fresh stream clone with a fresh fluid clone",
            "total-mole closure",
            "preservation of every non-`S8` component",
        ):
            self.assertIn(token, normalized)

    def test_guide_preserves_process_flash_and_chemistry_boundaries(self):
        text = GUIDE.read_text(encoding="utf-8")
        normalized = " ".join(text.split())
        for token in (
            "detached snapshot, not a committed stream update",
            "does not run the stream",
            "does not claim exactly-once execution",
            "no TP or solid flash",
            "measured sulfur yield",
            "#3144, #2937, #2911, pipeline",
            "#3153 remain unchanged",
        ):
            self.assertIn(token, normalized)

    def test_source_delegates_to_verified_preview_without_running_stream(self):
        text = SOURCE.read_text(encoding="utf-8")
        for token in (
            "public static Result applyToClone(",
            "StreamInterface candidateStream = independentClone(",
            "candidateStream.setFluid(",
            "public StreamInterface getCandidateStream()",
            "implements Serializable",
        ):
            self.assertIn(token, text)
        self.assertRegex(
            text,
            re.compile(
                r"AqueousHydrogenSulfideOxidationS8ComponentApplicationPreview\s*"
                r"\.applyToClone\("
            ),
        )
        self.assertRegex(
            text,
            re.compile(
                r"AqueousHydrogenSulfideOxidationS8ComponentApplicationReceipt\s*"
                r"\.verify\("
            ),
        )
        self.assertNotIn("candidateStream.run(", text)
        self.assertNotIn("runTPflash(", text)
        self.assertNotIn("ThermodynamicOperations", text)

    def test_java_tests_cover_acceptance_and_fail_closed_cases(self):
        text = TEST.read_text(encoding="utf-8")
        for method in (
            "testStrictAppendChangesOnlyDetachedCandidateStream",
            "testUnchangedTransitionPreservesExactDetachedInventory",
            "testCandidateGetterReturnsDefensiveStreamAndFluidClones",
            "testInvalidIdentityPriorAndCloneFailWithoutMutatingInput",
            "testDeterminismAndSerializationPreserveEvidenceAndCandidate",
        ):
            self.assertIn(method, text)


if __name__ == "__main__":
    unittest.main()
