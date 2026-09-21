from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
GUIDE = ROOT / "docs/chemicalreactions/h2s_oxygen_s8_component_addition_plan.md"
SOURCE = ROOT / (
    "src/main/java/neqsim/process/equipment/reactor/"
    "AqueousHydrogenSulfideOxidationS8ComponentAdditionPlan.java"
)
TEST = ROOT / (
    "src/test/java/neqsim/process/equipment/reactor/"
    "AqueousHydrogenSulfideOxidationS8ComponentAdditionPlanTest.java"
)


class S8ComponentAdditionPlanDocumentationTest(unittest.TestCase):
    def test_guide_defines_inputs_and_numeric_contract(self):
        text = GUIDE.read_text(encoding="utf-8")
        for token in (
            "complete prior/candidate ledger transition",
            "target-state identifier",
            "application idempotency key",
            "expected prior `S8` amount",
            "SystemInterface.addComponent(String, double)",
            "positive increment that disappears",
            "unchanged ledger transition preserves the prior amount exactly",
        ):
            self.assertIn(token, text)

    def test_guide_preserves_executor_and_chemistry_boundaries(self):
        text = GUIDE.read_text(encoding="utf-8")
        for token in (
            "does not authenticate the target",
            "does not call `SystemInterface.addComponent`",
            "atomically consume the application key",
            "apply the molar increment once",
            "reflash and validate material closure",
            "not a measured sulfur yield",
            "#3144, #2937, #2911, pipeline work, and #3153",
        ):
            self.assertIn(token, text)

    def test_source_is_non_mutating_and_exposes_evidence(self):
        text = SOURCE.read_text(encoding="utf-8")
        for token in (
            "public static Result create(",
            "AqueousHydrogenSulfideOxidationS8ComponentAmountProjection",
            ".project(prior, candidate, transition)",
            "Positive transferred S8 amount is not representable",
            "8.0 * Math.max(Math.ulp",
            "getTargetStateIdentifier()",
            "getApplicationIdempotencyKey()",
            "getCandidateS8AmountMol()",
            "getAmountClosureResidualMol()",
            "implements Serializable",
        ):
            self.assertIn(token, text)
        for forbidden in (
            "SystemInterface",
            "StreamInterface",
            ".addComponent(",
            "ThermodynamicOperations",
            "TPSolidflash",
            "SulfurDepositionAnalyser",
            "SulfurFilter",
        ):
            self.assertNotIn(forbidden, text)

    def test_java_tests_cover_contract_and_fail_closed_cases(self):
        text = TEST.read_text(encoding="utf-8")
        for method in (
            "testUnchangedTransitionPreservesExactPriorAmount",
            "testStrictAppendProducesClosedTargetAmountPlan",
            "testMismatchedTransitionAndInvalidInputsFailClosed",
            "testPositiveIncrementLostAtTargetScaleFailsClosed",
            "testDeterminismAndSerializationPreserveBitwiseEvidence",
        ):
            self.assertIn(method, text)


if __name__ == "__main__":
    unittest.main()
