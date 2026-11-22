"""Executable documentation contract for S8 batch-reconciliation checkpoints."""

from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
GUIDE = (
    ROOT
    / "docs"
    / "chemicalreactions"
    / "h2s_oxygen_s8_stream_application_batch_reconciliation_checkpoint.md"
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
    / "AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpoint.java"
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
    / "AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointTest.java"
)


class S8StreamApplicationBatchReconciliationCheckpointDocumentationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.guide = " ".join(GUIDE.read_text(encoding="utf-8").split())
        cls.production = PRODUCTION.read_text(encoding="utf-8")
        cls.test = TEST.read_text(encoding="utf-8")

    def test_canonical_integrity_contract_is_documented(self):
        for token in (
            "S8 stream-application batch-reconciliation checkpoints",
            "versioned canonical binary encoding",
            "length-prefixed UTF-8",
            "Double.doubleToLongBits",
            "64-character lowercase hexadecimal fingerprint",
            "independent of Java object-serialization bytes",
            "constant-time digest comparison",
            "Entry reordering",
            "bitwise floating-point change",
        ):
            self.assertIn(token, self.guide)

    def test_transaction_and_scientific_limits_are_documented(self):
        for token in (
            "not a digital signature or authentication mechanism",
            "durably persist",
            "does not write a database",
            "compare-and-swap",
            "exactly-once delivery",
            "does not qualify elemental sulfur as the H2S oxidation product",
            "does not mutate a fluid or stream",
            "`TPSolidflash`",
            "`SulfurDepositionAnalyser`",
            "`SulfurFilter`",
            "#3144",
            "#2937",
            "#2911",
            "#3153",
        ):
            self.assertIn(token, self.guide)

    def test_production_contract_is_executable(self):
        for token in (
            'DIGEST_ALGORITHM = "SHA-256"',
            '"neqsim-s8-stream-application-batch-reconciliation-checkpoint-v1"',
            "public static Result create(",
            "public static boolean verify(",
            "MessageDigest.isEqual",
            "StandardCharsets.UTF_8",
            "Double.doubleToLongBits(value)",
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

    def test_focused_determinism_and_divergence_gates_exist(self):
        for token in (
            "testDeterministicSerializedReconciliationCheckpoint",
            "testReorderingAndDistinctEvidenceChangeCheckpoint",
            "testCheckpointIsImmutableAndSerializable",
            "testMissingEvidenceFailsClosed",
            'matches("[0-9a-f]{64}")',
            "assertNotSame(first, second)",
        ):
            self.assertIn(token, self.test)


if __name__ == "__main__":
    unittest.main()
