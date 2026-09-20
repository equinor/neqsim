"""Executable documentation contract for verified S8 component-amount projection."""

from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
GUIDE = (
    ROOT
    / "docs"
    / "chemicalreactions"
    / "h2s_oxygen_s8_component_amount_projection.md"
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
    / "AqueousHydrogenSulfideOxidationS8ComponentAmountProjection.java"
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
    / "AqueousHydrogenSulfideOxidationS8ComponentAmountProjectionTest.java"
)


class S8ComponentAmountProjectionDocumentationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.guide = " ".join(GUIDE.read_text(encoding="utf-8").split())
        cls.production = PRODUCTION.read_text(encoding="utf-8")
        cls.test = TEST.read_text(encoding="utf-8")

    def test_verified_projection_contract_is_documented(self):
        for token in (
            "dimensional seam",
            "re-verifies the complete prior/candidate transition receipt",
            "fails closed before conversion",
            "0.25648 kg/mol",
            "`S8` row in NeqSim's `COMP.csv` component database",
            "mass [kg]",
            "amount [mol and kmol]",
            "mass-closure residual",
            "strict floating-point bound",
            "exact zero mol and kmol",
        ):
            self.assertIn(token, self.guide)

    def test_scientific_and_integration_limits_are_documented(self):
        for token in (
            "does not qualify the product selection as a measured yield",
            "does not infer a rate or residence time",
            "does not call `SystemInterface.addComponent`",
            "`TPSolidflash`",
            "`SulfurDepositionAnalyser`",
            "`SulfurFilter`",
            "not authentication",
            "compare-and-swap",
            "exactly-once guarantee",
            "#3144",
            "#2937",
            "#2911",
            "#3153",
        ):
            self.assertIn(token, self.guide)

    def test_production_api_and_numerical_gates_exist(self):
        for token in (
            "public static final double S8_MOLAR_MASS_KG_PER_MOL = 0.25648",
            'MOLECULAR_WEIGHT_BASIS_IDENTIFIER = "NeqSim-COMP.csv-S8-256.48-g-per-mol"',
            "public static Result project(",
            "AqueousHydrogenSulfideOxidationS8TransferLedgerTransition.verify",
            "getTransferredS8MassDeltaKg()",
            "amountMol / 1000.0",
            "8.0 * Math.ulp",
            "getTransferredS8AmountMol()",
            "getTransferredS8AmountKmol()",
            "getMassClosureResidualKg()",
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

    def test_focused_acceptance_tests_exist(self):
        for token in (
            "testUnchangedTransitionProjectsExactZero",
            "testStrictAppendProjectsDatabaseBasisAmounts",
            "testMismatchedTransitionReceiptFailsClosed",
            "testProjectionSerializationPreservesBitwiseEvidence",
            "Double.doubleToLongBits",
            "assertThrows(IllegalArgumentException.class",
        ):
            self.assertIn(token, self.test)


if __name__ == "__main__":
    unittest.main()
