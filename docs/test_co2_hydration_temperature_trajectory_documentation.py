"""Contracts for the qualified aqueous CO2 hydration temperature-trajectory guide."""

from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
GUIDE = ROOT / "docs/chemicalreactions/co2_hydration_temperature_trajectory.md"
PACKAGE_INDEX = ROOT / "docs/chemicalreactions/README.md"
TRAJECTORY = (
    ROOT
    / "src/main/java/neqsim/process/equipment/reactor/"
    / "AqueousCO2HydrationTrajectory.java"
)
JAVA_TEST = (
    ROOT
    / "src/test/java/neqsim/process/equipment/reactor/"
    / "AqueousCO2HydrationTrajectoryTest.java"
)


class AqueousCO2HydrationTrajectoryDocumentationTest(unittest.TestCase):
    """Protect evidence, equations, boundaries, and executable mirroring."""

    @classmethod
    def setUpClass(cls):
        cls.guide = GUIDE.read_text(encoding="utf-8")
        cls.package_index = PACKAGE_INDEX.read_text(encoding="utf-8")
        cls.trajectory = TRAJECTORY.read_text(encoding="utf-8")
        cls.java_test = JAVA_TEST.read_text(encoding="utf-8")
        cls.normalized = " ".join(cls.guide.split())

    def test_guide_records_primary_source_and_numerical_contract(self):
        for token in (
            "doi:10.1016/S0304-4203(02)00010-5",
            "piecewise-isothermal segments",
            "exact analytical",
            "without a numerical timestep approximation",
            "cumulative relaxation exposure",
            "segment order must be preserved",
            "288.15–305.65 K",
            "0.65 molal NaCl",
        ):
            self.assertIn(token, self.normalized)

    def test_guide_keeps_scientific_stop_boundary_explicit(self):
        for token in (
            "does not apply the van Eldik-Palmer pressure multipliers",
            "combine datasets",
            "gas-to-water transfer or water dropout",
            "bicarbonate/carbonate speciation or pH",
            "integrate a transient pipeline",
            "not Northern Lights facility calibration",
            "or a pipeline reaction source term",
        ):
            self.assertIn(token, self.normalized)

    def test_documented_api_and_conservation_are_executable(self):
        for token in (
            "public static TrajectoryResult advance(",
            "cumulativeRelaxationExposure += relaxationRate * duration",
            "initialTotalConcentration - finalTotalConcentration",
            "getCumulativeRelaxationExposure()",
            "getCarbonBalanceResidual()",
        ):
            self.assertIn(token, self.trajectory)

        for token in (
            "testSameTemperatureSegmentsMatchOneAnalyticalStep",
            "testChangingTemperatureMatchesOrderedExactUpdates",
            "testInvalidTrajectoryFailsClosed",
            "assertNotEquals",
            "getCarbonBalanceResidual()",
        ):
            self.assertIn(token, self.java_test)

    def test_guide_is_discoverable(self):
        self.assertIn("co2_hydration_temperature_trajectory", self.package_index)
        self.assertIn("CO₂ hydration temperature-trajectory guide", self.package_index)


if __name__ == "__main__":
    unittest.main()
