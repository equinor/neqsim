from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
GUIDE = ROOT / "docs/chemicalreactions/h2s_oxygen_s8_component_application_preview.md"
SOURCE = ROOT / (
    "src/main/java/neqsim/process/equipment/reactor/"
    "AqueousHydrogenSulfideOxidationS8ComponentApplicationPreview.java"
)
TEST = ROOT / (
    "src/test/java/neqsim/process/equipment/reactor/"
    "AqueousHydrogenSulfideOxidationS8ComponentApplicationPreviewTest.java"
)


class S8ComponentApplicationPreviewDocumentationTest(unittest.TestCase):
    def test_guide_defines_clone_and_verification_contract(self):
        text = GUIDE.read_text(encoding="utf-8")
        for token in (
            "independent deep clone",
            "caller-owned `SystemInterface`",
            "fresh defensive clone",
            "adds only the plan's verified `S8` amount in mol",
            "total-mole closure",
            "component identity and amount",
        ):
            self.assertIn(token, text)

    def test_guide_preserves_flash_execution_and_chemistry_boundaries(self):
        text = GUIDE.read_text(encoding="utf-8")
        for token in (
            "not a committed application",
            "injection model",
            "does not claim exactly-once execution",
            "does not run a TP or solid flash",
            "measured sulfur",
            "#3144, #2937, #2911, pipeline work, and #3153",
        ):
            self.assertIn(token, text)

    def test_source_applies_only_to_clone_and_returns_defensive_snapshot(self):
        text = SOURCE.read_text(encoding="utf-8")
        for token in (
            "public static Result applyToClone(",
            "source.clone()",
            "candidateTarget.addComponent(",
            "candidateTarget.init(0)",
            "AqueousHydrogenSulfideOxidationS8ComponentApplicationReceipt.verify(",
            "public SystemInterface getCandidateTarget()",
            "implements Serializable",
        ):
            self.assertIn(token, text)
        self.assertNotIn("ThermodynamicOperations", text)

    def test_java_tests_cover_acceptance_and_fail_closed_cases(self):
        text = TEST.read_text(encoding="utf-8")
        for method in (
            "testStrictAppendPreviewMutatesOnlyIndependentCandidate",
            "testUnchangedPreviewPreservesExactInventory",
            "testCandidateGetterReturnsDefensiveClones",
            "testInvalidIdentityPriorAndCloneFailWithoutMutatingInput",
            "testPreviewDeterminismAndSerializationPreserveEvidenceAndCandidate",
        ):
            self.assertIn(method, text)


if __name__ == "__main__":
    unittest.main()
