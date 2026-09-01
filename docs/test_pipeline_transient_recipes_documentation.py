"""Contracts for the TwoFluidPipe transient and slug-tracking cookbook batch."""

from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]
GUIDE = ROOT / "docs" / "cookbook" / "pipeline-recipes.md"
PIPELINE_SOURCE = (
    ROOT
    / "src"
    / "main"
    / "java"
    / "neqsim"
    / "process"
    / "equipment"
    / "pipeline"
)
TWO_FLUID_SOURCE = PIPELINE_SOURCE / "TwoFluidPipe.java"
MASS_REPORT_SOURCE = PIPELINE_SOURCE / "TwoFluidMassBalanceReport.java"
LAGRANGIAN_SOURCE = (
    PIPELINE_SOURCE
    / "twophasepipe"
    / "LagrangianSlugTracker.java"
)
JAVA_REGRESSION = (
    ROOT
    / "src"
    / "test"
    / "java"
    / "neqsim"
    / "process"
    / "equipment"
    / "pipeline"
    / "PipelineTransientRecipesDocumentationTest.java"
)


class PipelineTransientRecipesDocumentationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.guide = GUIDE.read_text(encoding="utf-8")
        cls.two_fluid_source = TWO_FLUID_SOURCE.read_text(encoding="utf-8")
        cls.mass_report_source = MASS_REPORT_SOURCE.read_text(encoding="utf-8")
        cls.lagrangian_source = LAGRANGIAN_SOURCE.read_text(encoding="utf-8")
        cls.java_regression = JAVA_REGRESSION.read_text(encoding="utf-8")

    def test_front_matter_has_no_duplicate_h1(self):
        self.assertTrue(self.guide.startswith("---\ntitle:"))
        body = self.guide.split("---", 2)[2]
        body_without_fences = re.sub(
            r"[\x60]{3}.*?[\x60]{3}",
            "",
            body,
            flags=re.DOTALL,
        )
        self.assertNotRegex(body_without_fences, r"(?m)^# ")

    def test_transient_recipe_uses_java_uuid_overload(self):
        self.assertIn(
            "public void runTransient(double dt, UUID id)",
            self.two_fluid_source,
        )
        self.assertIn("from java.util import UUID", self.guide)
        self.assertIn("UUID.randomUUID()", self.guide)
        self.assertNotIn("str(uuid.uuid4())", self.guide)
        self.assertNotIn("import uuid", self.guide)

    def test_transient_recipe_fails_closed_and_checks_mass_balance(self):
        for token in (
            "isSteadyStateConverged()",
            "isSteadyStatePressureFloorLimited()",
            "isSteadyStateWallClockLimited()",
            "getLastMassBalanceReport()",
            "MassPhase.TOTAL",
            "isWithinTolerance(",
        ):
            self.assertIn(token, self.guide)
        for token in (
            "getRelativeResidual(Phase phase)",
            "isWithinTolerance(Phase phase",
        ):
            self.assertIn(token, self.mass_report_source)

    def test_lagrangian_mode_uses_matching_tracker(self):
        for token in (
            "SlugTrackingMode.LAGRANGIAN",
            "getLagrangianSlugTracker()",
        ):
            self.assertIn(token, self.guide)
            self.assertIn(token.split("()", 1)[0], self.two_fluid_source)
        for token in (
            "getSlugCount()",
            "getAverageSlugLength()",
            "getSlugFrequency()",
        ):
            self.assertIn(token, self.guide)
            self.assertIn(token, self.lagrangian_source)
        self.assertNotIn("getSlugTracker().getSlugCount()", self.guide)

    def test_adaptive_step_contract_matches_source(self):
        self.assertIn("ADAPTIVE_DT_GROWTH = 1.05", self.two_fluid_source)
        self.assertIn("×1.05 growth", self.guide)
        self.assertIn(
            "setAdaptiveMaxPressure(200.0)  # bara",
            self.guide,
        )
        self.assertNotIn("x1.02 growth", self.guide)

    def test_comparison_has_no_universal_acceptance_ratio(self):
        self.assertIn(
            "Do not apply a universal ratio",
            self.guide,
        )
        for stale_claim in ("ratio 0.8-1.3", "~0.98"):
            self.assertNotIn(stale_claim, self.guide)

    def test_java_regression_executes_documented_contracts(self):
        for token in (
            "documentedTransientUsesJavaUuidAndClosesMassBalance",
            "documentedLagrangianModeUsesMatchingTracker",
            "documentedThreePhaseProfilesAreBounded",
        ):
            self.assertIn(token, self.java_regression)


if __name__ == "__main__":
    unittest.main()
