"""Contracts for the TP-flash algorithm reference."""

from pathlib import Path
import re
import unittest
from urllib.parse import unquote


ROOT = Path(__file__).resolve().parents[1]
GUIDE = ROOT / "docs/thermodynamicoperations/TPflash_algorithm.md"
JAVA_TEST = (
    ROOT
    / "src/test/java/neqsim/documentation/"
    / "TPflashAlgorithmDocumentationTest.java"
)
SYSTEM_INTERFACE = ROOT / "src/main/java/neqsim/thermo/system/SystemInterface.java"


def resolve_internal_target(source, destination):
    target, _, fragment = unquote(destination).partition("#")
    raw_target = source.parent / target
    candidates = [raw_target]
    if not Path(target).suffix:
        candidates.extend(
            (
                Path("{}.md".format(raw_target)),
                raw_target / "README.md",
                raw_target / "index.md",
            )
        )
    for candidate in candidates:
        if candidate.is_file():
            return candidate.resolve(), fragment
    raise AssertionError("Unresolved guide link: {}".format(destination))


class TPflashAlgorithmDocumentationContractTest(unittest.TestCase):
    """Protect rendering, the executable example, and phase/reaction boundaries."""

    @classmethod
    def setUpClass(cls):
        cls.guide = GUIDE.read_text(encoding="utf-8")
        cls.java_test = JAVA_TEST.read_text(encoding="utf-8")
        cls.system_interface = SYSTEM_INTERFACE.read_text(encoding="utf-8")
        cls.usage = cls.guide.split("## Usage Example", 1)[1].split(
            "### Large-volatility hydrocarbon endpoint refinement", 1
        )[0]

    def test_structure_math_and_internal_links_are_renderable(self):
        self.assertTrue(self.guide.startswith("---\n"))
        self.assertNotIn("# TPflash Algorithm Documentation", self.guide)
        self.assertEqual(self.guide.count("```") % 2, 0)

        without_fences = re.sub(
            r"```.*?```",
            "",
            self.guide,
            flags=re.DOTALL,
        )
        self.assertNotIn(r"\[", without_fences)
        self.assertNotIn(r"\]", without_fences)

        links = re.findall(r"(?<!!)\[[^\]]+\]\(([^)]+)\)", self.guide)
        for destination in links:
            if destination.startswith(("http://", "https://", "mailto:", "#")):
                continue
            target, _fragment = resolve_internal_target(GUIDE, destination)
            self.assertTrue(target.is_file())

    def test_public_example_uses_repository_logging_contract(self):
        for token in (
            "import org.apache.logging.log4j.LogManager;",
            "import org.apache.logging.log4j.Logger;",
            "private static final Logger logger =",
            "LogManager.getLogger(TpFlashExample.class)",
            'logger.info("Number of phases: {}",',
            'logger.info("Vapor fraction: {}",',
        ):
            self.assertIn(token, self.usage)

        self.assertNotIn("System.out", self.usage)
        self.assertNotIn("System.err", self.usage)
        self.assertNotIn("system.display()", self.usage)

    def test_vapor_fraction_is_resolved_by_phase_type(self):
        for token in (
            "system.hasPhaseType(PhaseType.GAS)",
            "system.getPhaseNumberOfPhase(PhaseType.GAS)",
            "system.getBeta(gasPhaseNumber)",
            "active phase zero is not a universal vapor-phase contract",
        ):
            self.assertIn(token, self.usage)

        self.assertNotIn("system.getBeta(0)", self.usage)

        for source_token in (
            "boolean hasPhaseType(PhaseType pt)",
            "int getPhaseNumberOfPhase(PhaseType pt)",
            "double getBeta(int phaseNum)",
        ):
            self.assertIn(source_token, self.system_interface)

    def test_documented_calls_have_executable_regression_coverage(self):
        for token in (
            "new SystemSrkEos(298.15, 10.0)",
            'system.addComponent("methane", 0.7)',
            'system.addComponent("ethane", 0.2)',
            'system.addComponent("propane", 0.1)',
            'system.setMixingRule("classic")',
            "system.setMultiPhaseCheck(true)",
            "operations.TPflash()",
            "system.hasPhaseType(PhaseType.GAS)",
            "system.getPhaseNumberOfPhase(PhaseType.GAS)",
            "system.getBeta(gasPhaseNumber)",
        ):
            self.assertIn(token, self.usage)
            self.assertIn(token, self.java_test)

        for test_token in (
            "testPhaseTypedHydrocarbonUsageExample",
            "assertEquals(1.0, betaTotal, 1.0e-12)",
            "assertTrue(vaporFraction >= 0.0)",
            "assertTrue(vaporFraction <= 1.0)",
        ):
            self.assertIn(test_token, self.java_test)

    def test_units_model_and_reaction_boundaries_are_explicit(self):
        normalized = " ".join(self.usage.split())
        for token in (
            "temperature in K and absolute pressure in bara",
            "amounts in mol",
            "sum to 1.0 mol",
            "does not select or validate the thermodynamic model",
            "does not guarantee that a gas phase exists",
            "not evidence that the classic SRK parameterization is accurate",
            "Check convergence, material balance, phase stability",
            "does **not** discover reaction products",
            "simultaneous chemical and phase equilibrium",
            "[reactive-flash workflow](../thermo/reactive_flash.md)",
            "standard-state, charge-balance, and validation boundaries",
        ):
            self.assertIn(token, normalized)

        self.assertNotIn("Automatically solves chemical equilibrium", self.usage)


if __name__ == "__main__":
    unittest.main()
