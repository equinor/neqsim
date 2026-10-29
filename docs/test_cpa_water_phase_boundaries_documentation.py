"""Source and routing contracts for the combined CPA water-boundary workflow."""

from pathlib import Path
import json
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]
GUIDE = ROOT / "docs/thermodynamicoperations/cpa_water_phase_boundaries.md"
SKILL = ROOT / ".github/skills/neqsim-cpa-water-boundaries/SKILL.md"
JAVA = ROOT / "src/main/java/neqsim/thermodynamicoperations"


class CpaWaterPhaseBoundariesDocumentationTest(unittest.TestCase):
    """Keep the advertised one-call workflow tied to existing APIs and agents."""

    def test_agents_load_the_cpa_boundary_workflow(self):
        """A CPA dew-point request must reach the three-boundary execution rule."""
        skill = SKILL.read_text()
        self.assertIn("name: neqsim-cpa-water-boundaries", skill)
        self.assertIn("vannduggpunkt med CPA", skill)
        for name in ("gas-quality", "thermo-fluid"):
            agent = (ROOT / f".github/agents/{name}.agent.md").read_text()
            metadata = agent.split("---", 2)[1]
            self.assertRegex(metadata, r"(?m)^- neqsim-cpa-water-boundaries$")
            self.assertIn("cpaWaterPhaseBoundaryTemperatures()", agent)

    def test_documented_entry_point_exists_in_the_public_facade(self):
        """The advertised operation must exist rather than be a guessed API."""
        source = (JAVA / "ThermodynamicOperations.java").read_text()
        self.assertRegex(
            source,
            r"public\s+WaterPhaseBoundaryResult\s+cpaWaterPhaseBoundaryTemperatures\s*\(",
        )
        guide = GUIDE.read_text()
        self.assertIn("new SystemSrkCPAstatoil(293.15, 50.0)", guide)
        self.assertIn("gas.setMixingRule(10)", guide)
        self.assertIn("100.0e-6", guide)

    def test_advertised_results_have_public_getters(self):
        """All three outcomes expose the diagnostics the agents are told to inspect."""
        source = (JAVA / "WaterPhaseBoundaryResult.java").read_text()
        for getter in (
            "getLiquidWater", "getIce", "getHydrate", "getTemperatureC",
            "getStatus", "isConverged", "getResidual", "getDiagnostic",
            "getPhaseReference", "getWaterActivity", "getPressureBara",
            "getModel", "getMixingRule", "getWaterMoleFraction", "getExplanation",
        ):
            self.assertRegex(source, rf"public\s+[^;{{\n]+\b{getter}\s*\(")

    def test_mcp_payload_matches_the_executed_runner_fixture(self):
        """The JSON example must use the exact input exercised by the runner regression."""
        payload = json.loads(re.search(r"```json\s*(.*?)```", GUIDE.read_text(), re.S).group(1))
        source = (
            ROOT / "src/test/java/neqsim/mcp/runners/FlowAssuranceRunnerTest.java"
        ).read_text()
        fixture = re.search(
            r"private JsonObject waterBoundaryInput\(\)\s*\{(.*?)\n\s*\}", source, re.S
        ).group(1)
        literals = re.findall(r'"(?:[^"\\]|\\.)*"', fixture)
        expected = json.loads("".join(json.loads(value) for value in literals))
        self.assertEqual(expected, payload)
        skill = SKILL.read_text()
        self.assertIn("run_flow_assurance", skill)
        self.assertIn("validateInput", skill)
        self.assertIn('analysis: "waterPhaseBoundaries"', skill)
        index = json.loads((ROOT / ".github/skills/skill-index.json").read_text())
        for keyword in ("vannduggpunkt med CPA", "CPA water phase boundaries"):
            self.assertIn("neqsim-cpa-water-boundaries", index[keyword])

    def test_guide_is_reachable_from_the_operations_and_reference_indexes(self):
        """The maintained guide must be discoverable from the documented API."""
        overview = (ROOT / "docs/thermodynamicoperations/README.md").read_text()
        index = (ROOT / "docs/REFERENCE_MANUAL_INDEX.md").read_text()
        self.assertIn("(cpa_water_phase_boundaries)", overview)
        self.assertIn("(thermodynamicoperations/cpa_water_phase_boundaries.md)", index)
        self.assertIn("cpa_water_phase_boundaries.md", SKILL.read_text())


if __name__ == "__main__":
    unittest.main()
