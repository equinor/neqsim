"""Executable documentation contract for S8 transfer-ledger integrity checkpoints."""

from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
GUIDE = (
    ROOT
    / "docs"
    / "chemicalreactions"
    / "h2s_oxygen_s8_transfer_ledger_checkpoint.md"
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
    / "AqueousHydrogenSulfideOxidationS8TransferLedgerCheckpoint.java"
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
    / "AqueousHydrogenSulfideOxidationS8TransferLedgerCheckpointTest.java"
)


class S8TransferLedgerCheckpointDocumentationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.guide = " ".join(GUIDE.read_text(encoding="utf-8").split())
        cls.production = PRODUCTION.read_text(encoding="utf-8")
        cls.test = TEST.read_text(encoding="utf-8")

    def test_canonical_integrity_contract_is_documented(self):
        for token in (
            "S8 transfer ledger integrity checkpoints",
            "versioned canonical binary encoding",
            "length-prefixed UTF-8",
            "Double.doubleToLongBits",
            "64-character lowercase hexadecimal fingerprint",
            "independent of Java object-serialization bytes",
            "constant-time digest comparison",
            "fit-path change",
            "idempotency-key change",
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
            "does not qualify elemental sulfur as the oxidation product",
            "does not add `S8` to a system",
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
            'SCHEMA_IDENTIFIER = "neqsim-s8-transfer-ledger-checkpoint-v1"',
            "public static Result create(",
            "public static boolean verify(",
            ".create(ledger.getBatches(), ledger.getLedgerIdentifier())",
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
            "testSerializedLedgerProducesIdenticalCheckpoint",
            "testAppendReorderAndReplacementChangeCheckpoint",
            "testCheckpointReceiptIsImmutableAndSerializable",
            "testMissingInputsFailClosed",
            'matches("[0-9a-f]{64}")',
            "assertNotSame(first, second)",
        ):
            self.assertIn(token, self.test)


if __name__ == "__main__":
    unittest.main()
