"""Executable documentation contract for S8 transfer-ledger prefix reconciliation."""

from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
GUIDE = ROOT / "docs" / "chemicalreactions" / "h2s_oxygen_s8_transfer_ledger_delta.md"
PRODUCTION = (
    ROOT
    / "src"
    / "main"
    / "java"
    / "neqsim"
    / "process"
    / "equipment"
    / "reactor"
    / "AqueousHydrogenSulfideOxidationS8TransferLedgerDelta.java"
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
    / "AqueousHydrogenSulfideOxidationS8TransferLedgerDeltaTest.java"
)


class S8TransferLedgerDeltaDocumentationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.guide = " ".join(GUIDE.read_text(encoding="utf-8").split())
        cls.production = PRODUCTION.read_text(encoding="utf-8")
        cls.test = TEST.read_text(encoding="utf-8")

    def test_exact_prefix_and_delta_contract_is_documented(self):
        for token in (
            "S8 transfer ledger prefix reconciliation",
            "read-only restart/recovery check",
            "Exact ordered-prefix contract",
            "bitwise equality of every field",
            "exact source order",
            "truncates, reorders, or replaces prior evidence fails closed",
            "deterministic after Java serialization",
            "candidate-minus-prior differences",
            "source sulfur-equivalent mass",
            "transferred S8 mass",
            "unallocated sulfur-equivalent mass",
            "cumulative mass-closure residual",
        ):
            self.assertIn(token, self.guide)

    def test_scientific_and_transaction_limits_are_documented(self):
        for token in (
            "does not append the candidate",
            "divergent writers",
            "distributed lock",
            "compare-and-swap",
            "Millero et al. (1987)",
            "https://doi.org/10.1021/es00159a003",
            "introduces no chemical coefficient",
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
            "public static Result reconcile(",
            "AqueousHydrogenSulfideOxidationS8TransferLedger.create(",
            "Candidate ledger truncates the prior state",
            "Candidate ledger does not preserve the exact prior batch prefix",
            "Double.doubleToLongBits(left) == Double.doubleToLongBits(right)",
            "getAddedBatches()",
            "getAddedBatchCount()",
            "getAddedTransferCount()",
            "isUnchanged()",
            "isStrictAppend()",
            "getSourceSulfurEquivalentMassDeltaKg()",
            "getTransferredS8MassDeltaKg()",
            "getUnallocatedSulfurEquivalentMassDeltaKg()",
            "getMassClosureResidualDeltaKg()",
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

    def test_focused_restart_and_divergence_gates_exist(self):
        for token in (
            "testUnchangedSerializedLedgerHasZeroDelta",
            "testStrictAppendReportsExactInventoryDifferences",
            "testTruncationReorderingAndReplacementFailClosed",
            "testIdentityMismatchAndMissingStatesFailClosed",
            "serializeRoundTrip",
            "assertThrows(UnsupportedOperationException.class",
        ):
            self.assertIn(token, self.test)


if __name__ == "__main__":
    unittest.main()
