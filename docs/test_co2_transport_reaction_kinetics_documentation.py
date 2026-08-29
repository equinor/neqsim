"""Contracts for the CO2 transport reaction-kinetics guide."""

from pathlib import Path
import re
import unittest
from urllib.parse import unquote


ROOT = Path(__file__).resolve().parents[1]
GUIDE = ROOT / "docs/chemicalreactions/co2_transport_reaction_kinetics.md"
PACKAGE_INDEX = ROOT / "docs/chemicalreactions/README.md"
REFERENCE_INDEX = ROOT / "docs/REFERENCE_MANUAL_INDEX.md"
DIAGNOSTICS = (
    ROOT
    / "src/main/java/neqsim/process/equipment/reactor/"
    / "KineticReactionDiagnostics.java"
)
QUALIFICATION = (
    ROOT
    / "src/main/java/neqsim/process/equipment/reactor/"
    / "KineticReactionQualification.java"
)
SYSTEM_INTERFACE = ROOT / "src/main/java/neqsim/thermo/system/SystemInterface.java"
JAVA_TEST = (
    ROOT
    / "src/test/java/neqsim/documentation/"
    / "CO2TransportReactionKineticsDocumentationTest.java"
)


def resolve_internal_target(source, destination):
    """Resolve one repository-internal Markdown target."""
    target, _, _fragment = unquote(destination).partition("#")
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
            return candidate.resolve()
    raise AssertionError("Unresolved guide link: {}".format(destination))


class CO2TransportReactionKineticsDocumentationContractTest(unittest.TestCase):
    """Protect rendering, source truth, discoverability, and executable mirroring."""

    @classmethod
    def setUpClass(cls):
        cls.guide = GUIDE.read_text(encoding="utf-8")
        cls.package_index = PACKAGE_INDEX.read_text(encoding="utf-8")
        cls.reference_index = REFERENCE_INDEX.read_text(encoding="utf-8")
        cls.diagnostics = DIAGNOSTICS.read_text(encoding="utf-8")
        cls.qualification = QUALIFICATION.read_text(encoding="utf-8")
        cls.system_interface = SYSTEM_INTERFACE.read_text(encoding="utf-8")
        cls.java_test = JAVA_TEST.read_text(encoding="utf-8")
        cls.normalized = " ".join(cls.guide.split())

    def test_front_matter_math_fences_and_links_are_renderable(self):
        self.assertTrue(self.guide.startswith("---\n"))
        self.assertNotIn("# CO2 transport reaction kinetics", self.guide)
        self.assertEqual(2, self.guide.count("```"))
        self.assertNotIn(r"\[", self.guide)
        self.assertNotIn(r"\]", self.guide)
        self.assertIn(r"\mathrm{Da}", self.guide)

        links = re.findall(r"(?<!!)\[[^\]]+\]\(([^)]+)\)", self.guide)
        self.assertEqual(3, len(links))
        for destination in links:
            self.assertTrue(resolve_internal_target(GUIDE, destination).is_file())

    def test_complete_java_helper_uses_logging_and_fail_closed_order(self):
        for token in (
            "import org.apache.logging.log4j.LogManager;",
            "import org.apache.logging.log4j.Logger;",
            "public final class QualifiedKineticsScreen",
            "private static final Logger logger =",
            "qualification.requireValidatedAt(fluid.getTemperature(), fluid.getPressure());",
            "KineticReactionDiagnostics.evaluate(",
            "logger.info(",
            "return diagnostic;",
        ):
            self.assertIn(token, self.guide)

        self.assertNotIn("System.out", self.guide)
        self.assertNotIn("System.err", self.guide)
        self.assertLess(
            self.guide.index("qualification.requireValidatedAt"),
            self.guide.index("KineticReactionDiagnostics.evaluate"),
        )

    def test_source_contracts_and_units_match_current_api(self):
        for token in (
            "public static KineticReactionDiagnostics evaluate(",
            "residence time must be finite and non-negative",
            "transport timescale diagnostic currently requires a VOLUME rate basis",
            "double damkohler = residenceTimeSeconds / reactionTime;",
            "if (damkohler < 0.1)",
            "else if (damkohler > 10.0)",
            "Math.abs(rate)",
            "Double.POSITIVE_INFINITY",
        ):
            self.assertIn(token, self.diagnostics)

        for token in (
            "public void requireValidatedAt(double temperatureK, double pressureBara)",
            "validationStatus != ChemicalReactionValidationStatus.VALIDATED",
            "temperatureK >= minimumTemperatureK",
            "pressureBara >= minimumPressureBara",
        ):
            self.assertIn(token, self.qualification)

        self.assertIn("@return pressure in unit bara", self.system_interface)
        self.assertIn("@return temperature in unit Kelvin", self.system_interface)
        for token in (
            "getTemperature() returns K",
            "getPressure() returns bara",
            "residenceTimeSeconds is in s",
            "finite and non-negative",
            "active phase index",
        ):
            self.assertIn(token, self.normalized)

    def test_limitations_and_boundary_behavior_are_explicit(self):
        for token in (
            "do not supply a qualified kinetic dataset",
            "do not apply reaction source terms",
            "not automatically bound",
            "accepts only KineticReaction.RateBasis.VOLUME",
            "exactly 0.1 and 10 are COUPLED",
            "timescale uses its absolute value",
            "missing or zero-concentration reactant",
            "does not update composition",
            "does not qualify a model",
            "default constants are experimental and illustrative",
            "Do not use them as design correlations",
        ):
            self.assertIn(token, self.normalized)

    def test_guide_is_discoverable_from_both_indexes(self):
        package_target = "co2_transport_reaction_kinetics.md"
        reference_target = "chemicalreactions/co2_transport_reaction_kinetics.md"
        self.assertIn(package_target, self.package_index)
        self.assertIn(reference_target, self.reference_index)
        self.assertIn("Damköhler", self.package_index)
        self.assertIn("Damköhler", self.reference_index)

    def test_documented_calls_have_executable_java_coverage(self):
        for token in (
            "qualification.requireValidatedAt(fluid.getTemperature(), fluid.getPressure())",
            "KineticReactionDiagnostics.evaluate(",
            "diagnostic.getReactionName()",
            "diagnostic.getDamkohlerNumber()",
            "diagnostic.getRegime()",
            "diagnostic.getLimitingReactant()",
        ):
            self.assertIn(token, self.guide)
            self.assertIn(token, self.java_test)

        for token in (
            "testQualifiedScreenExecutesDocumentedWorkflow",
            "assertEquals(1.0, diagnostic.getDamkohlerNumber(), 1.0e-10)",
            "assertThrows(IllegalStateException.class",
            "assertThrows(IllegalArgumentException.class",
        ):
            self.assertIn(token, self.java_test)


if __name__ == "__main__":
    unittest.main()
