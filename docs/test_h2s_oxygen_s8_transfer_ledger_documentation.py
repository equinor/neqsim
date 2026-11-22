"""Executable documentation contract for the persistent H2S/O2 S8 transfer ledger."""

from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
GUIDE = ROOT / "docs" / "chemicalreactions" / "h2s_oxygen_s8_transfer_ledger.md"
PRODUCTION = (
    ROOT
    / "src"
    / "main"
    / "java"
    / "neqsim"
    / "process"
    / "equipment"
    / "reactor"
    / "AqueousHydrogenSulfideOxidationS8TransferLedger.java"
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
    / "AqueousHydrogenSulfideOxidationS8TransferLedgerTest.java"
)


class S8TransferLedgerDocumentationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.guide = GUIDE.read_text(encoding="utf-8")
        cls.guide_words = " ".join(cls.guide.split())
        cls.production = PRODUCTION.read_text(encoding="utf-8")
        cls.test = TEST.read_text(encoding="utf-8")

    def test_accounting_boundary_is_documented(self):
        for token in (
            "Persistent S8 transfer accounting ledger",
            "AqueousHydrogenSulfideOxidationS8TransferLedger.create(...)",
            "one shared caller-supplied S8 product-identity basis",
            "rejects duplicate batch identifiers",
            "rejects duplicate downstream idempotency keys across all batches",
            "does not sum segment or batch mass rates",
            "total source sulfur-equivalent mass",
            "transferred S8 mass",
            "unallocated sulfur-equivalent mass",
            "cumulative closure residual in kg",
            "eight floating-point units in the last place",
            "Unchanged-state segment subdivision",
        ):
            self.assertIn(token, self.guide_words)

    def test_restart_and_scientific_limits_are_documented(self):
        for token in (
            "After a caller durably persists and restores it",
            "AqueousHydrogenSulfideOxidationS8TransferLedger.append(...)",
            "before the process restart is therefore rejected",
            "does not write a database",
            "distributed lock",
            "transactional store",
            "applied at most once",
            "must not also be applied as product",
            "Millero et",
            "https://doi.org/10.1021/es00159a003",
            "introduces no chemical",
            "calculate S8 moles",
            "qualify elemental sulfur as the oxidation product",
            "add `S8` to a thermodynamic system or stream",
            "execute `TPSolidflash`",
            "`SulfurDepositionAnalyser`",
            "`SulfurFilter`",
            "#3144",
            "#2937",
            "#2911",
            "#3153",
        ):
            self.assertIn(token, self.guide_words)

    def test_production_contract_is_executable(self):
        for token in (
            "public static Result create(",
            "public static Result append(",
            "Duplicate S8 transfer batch identifier",
            "Duplicate downstream idempotency key across S8 transfer batches",
            "All S8 transfer batches must use one product-identity basis",
            "Collections.unmodifiableList(copy)",
            "Collections.unmodifiableSet(new HashSet<String>(idempotencyKeys))",
            "getLedgerIdentifier()",
            "getBatches()",
            "getDownstreamIdempotencyKeys()",
            "containsDownstreamIdempotencyKey(",
            "getBatchCount()",
            "getTransferCount()",
            "getTotalSourceSulfurEquivalentMassKg()",
            "getTotalTransferredS8MassKg()",
            "getTotalUnallocatedSulfurEquivalentMassKg()",
            "getMassClosureResidualKg()",
            "implements Serializable",
            "8.0 * Math.ulp(closureScale)",
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

    def test_focused_numerical_and_immutability_gates_exist(self):
        for token in (
            "testLedgerPreservesOrderKeysAndCumulativeClosure",
            "testDuplicateBatchKeysAndMixedProductBasesFailClosed",
            "testSerializedLedgerAppendRejectsPreviouslyConsumedKey",
            "testSegmentSubdivisionPreservesLedgerMassTotals",
            "testMissingOrInvalidLedgerEvidenceFailsClosed",
            "assertThrows(UnsupportedOperationException.class",
        ):
            self.assertIn(token, self.test)


if __name__ == "__main__":
    unittest.main()
