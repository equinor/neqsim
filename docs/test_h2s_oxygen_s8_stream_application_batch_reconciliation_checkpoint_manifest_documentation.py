"""Executable documentation contract for S8 reconciliation-checkpoint manifests."""

from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
GUIDE = (
    ROOT
    / "docs"
    / "chemicalreactions"
    / "h2s_oxygen_s8_stream_application_batch_reconciliation_checkpoint_manifest.md"
)
PRODUCTION = (
    ROOT
    / "src"
    / "main"
    / "java"
    / "neqsim"
    / "process"
    / "equipment"
    / "reactor"
    / "AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest.java"
)
TEST = (
    ROOT
    / "src"
    / "test"
    / "java"
    / "neqsim"
    / "process"
    / "equipment"
    / "reactor"
    / "AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTest.java"
)


class S8ReconciliationCheckpointManifestDocumentationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.guide = " ".join(GUIDE.read_text(encoding="utf-8").split())
        cls.production = PRODUCTION.read_text(encoding="utf-8")
        cls.test = TEST.read_text(encoding="utf-8")

    def test_ordered_integrity_contract_is_documented(self):
        for token in (
            "S8 reconciliation-checkpoint manifests",
            "versioned canonical binary encoding",
            "length-prefixed UTF-8",
            "Double.doubleToLongBits",
            "duplicate reconciliation identifiers",
            "duplicate checkpoint digests",
            "64-character lowercase hexadecimal fingerprint",
            "constant-time digest comparison",
            "fresh unmodifiable entry list",
        ):
            self.assertIn(token, self.guide)

    def test_persistence_and_scientific_limits_are_documented(self):
        for token in (
            "not a durable ledger, database, digital signature, authentication mechanism",
            "compare-and-swap transaction",
            "exactly-once guarantee",
            "does not retain or replay",
            "does not qualify elemental sulfur as the H2S oxidation product",
            "does not mutate a fluid or stream",
            "deliberately does not sum sulfur masses",
            "#3144",
            "#2937",
            "#2911",
            "#3153",
        ):
            self.assertIn(token, self.guide)

    def test_production_contract_is_executable(self):
        for token in (
            'DIGEST_ALGORITHM = "SHA-256"',
            '"neqsim-s8-stream-application-batch-reconciliation-checkpoint-manifest-v1"',
            "public static Entry entry(",
            "public static Result create(",
            "public static boolean verify(",
            "MessageDigest.isEqual",
            "StandardCharsets.UTF_8",
            "Double.doubleToLongBits(value)",
            "Collections.unmodifiableList",
            "getDigestHex()",
            "getDigestBytes()",
            "implements Serializable",
        ):
            self.assertIn(token, self.production)

        for forbidden in (
            "SystemInterface",
            "StreamInterface",
            "addComponent(",
            "TPSolidflash(",
            "SulfurDepositionAnalyser(",
            "SulfurFilter(",
        ):
            self.assertNotIn(forbidden, self.production)

    def test_focused_manifest_gates_exist(self):
        for token in (
            "testDeterministicSerializedManifest",
            "testOrderIdentityAndDuplicateEvidenceFailClosed",
            "testManifestCollectionsAndDigestAreDefensive",
            "testMissingInputsFailClosed",
            'matches("[0-9a-f]{64}")',
            "assertNotSame(firstDigest, secondDigest)",
        ):
            self.assertIn(token, self.test)


if __name__ == "__main__":
    unittest.main()
