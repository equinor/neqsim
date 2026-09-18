"""Executable documentation contract for checkpoint-linked S8 ledger transitions."""

from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
GUIDE = (
    ROOT
    / "docs"
    / "chemicalreactions"
    / "h2s_oxygen_s8_transfer_ledger_transition.md"
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
    / "AqueousHydrogenSulfideOxidationS8TransferLedgerTransition.java"
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
    / "AqueousHydrogenSulfideOxidationS8TransferLedgerTransitionTest.java"
)


class S8TransferLedgerTransitionDocumentationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.guide = " ".join(GUIDE.read_text(encoding="utf-8").split())
        cls.production = PRODUCTION.read_text(encoding="utf-8")
        cls.test = TEST.read_text(encoding="utf-8")

    def test_checkpoint_linked_transition_is_documented(self):
        for token in (
            "S8 transfer ledger transition receipts",
            "prior and candidate SHA-256 state fingerprints",
            "unchanged or strict-append classification",
            "added batch and transfer counts",
            "cumulative closure-residual delta",
            "versioned canonical transition encoding",
            "one exact state transition",
            "constant time",
            "fails closed",
        ):
            self.assertIn(token, self.guide)

    def test_audit_and_scientific_limits_are_documented(self):
        for token in (
            "not a database transaction",
            "digital signature",
            "optimistic-concurrency primitive",
            "compare-and-swap",
            "exactly-once delivery",
            "No new chemistry is introduced",
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
            'SCHEMA_IDENTIFIER = "neqsim-s8-transfer-ledger-transition-v1"',
            "public static Result create(",
            "public static boolean verify(",
            ".reconcile(prior, candidate)",
            ".create(prior)",
            ".create(candidate)",
            "MessageDigest.isEqual",
            "Double.doubleToLongBits",
            "getPriorCheckpointHex()",
            "getCandidateCheckpointHex()",
            "getTransitionDigestHex()",
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

    def test_transition_acceptance_gates_exist(self):
        for token in (
            "testUnchangedSerializedStatesProduceZeroTransition",
            "testStrictAppendLinksCheckpointsAndExactDeltas",
            "testDivergentAndMismatchedTransitionsFailClosed",
            "testReceiptSerializationAndDefensiveDigestCopy",
            'matches("[0-9a-f]{64}")',
            "assertNotSame(firstDigest, secondDigest)",
        ):
            self.assertIn(token, self.test)


if __name__ == "__main__":
    unittest.main()
