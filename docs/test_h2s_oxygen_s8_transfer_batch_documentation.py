"""Executable documentation contract for the immutable H2S/O2 S8 transfer batch."""

from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
GUIDE = ROOT / "docs" / "chemicalreactions" / "h2s_oxygen_s8_transfer_batch.md"
PRODUCTION = (
    ROOT
    / "src"
    / "main"
    / "java"
    / "neqsim"
    / "process"
    / "equipment"
    / "reactor"
    / "AqueousHydrogenSulfideOxidationS8TransferBatch.java"
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
    / "AqueousHydrogenSulfideOxidationS8TransferBatchTest.java"
)


class S8TransferBatchDocumentationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.guide = GUIDE.read_text(encoding="utf-8")
        cls.production = PRODUCTION.read_text(encoding="utf-8")
        cls.test = TEST.read_text(encoding="utf-8")

    def test_accounting_boundary_is_documented(self):
        for token in (
            "Immutable S8 transfer batch",
            "AqueousHydrogenSulfideOxidationS8TransferBatch.create(...)",
            "single caller-supplied S8 product-identity basis",
            "rejects duplicate downstream idempotency keys",
            "does not sum segment mass rates",
            "total source sulfur-equivalent mass",
            "transferred S8 mass",
            "unallocated sulfur-equivalent mass",
            "aggregate closure residual in kg",
            "eight floating-point units in the last place",
            "unchanged-state segment subdivision",
        ):
            self.assertIn(token, self.guide)

    def test_idempotency_and_scientific_limits_are_documented(self):
        for token in (
            "bounded duplicate-consumption guard",
            "not a persistent exactly-once service",
            "across batches, retries, process restarts, and distributed consumers",
            "apply each transferred S8 mass at most once",
            "must not also apply the original source sulfur budget as product",
            "Millero et al. (1987)",
            "https://doi.org/10.1021/es00159a003",
            "introduces no chemical coefficient",
            "does not:",
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
            self.assertIn(token, self.guide)

    def test_production_contract_is_executable(self):
        for token in (
            "public static Result create(",
            "Duplicate downstream idempotency key",
            "All S8 transfer receipts must use one product-identity basis",
            "Collections.unmodifiableList(copy)",
            "getBatchIdentifier()",
            "getComponentName()",
            "getProductIdentityBasisIdentifier()",
            "getTransfers()",
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
            "testBatchPreservesOrderAndClosesAggregateMass",
            "testDuplicateKeysAndMixedProductBasesFailClosed",
            "testSegmentSubdivisionPreservesTotalTransferredMass",
            "testBatchRoundTripsThroughSerializationAndRemainsUnmodifiable",
            "testMissingOrInvalidBatchEvidenceFailsClosed",
            "assertThrows(UnsupportedOperationException.class",
        ):
            self.assertIn(token, self.test)


if __name__ == "__main__":
    unittest.main()
