"""Contracts for the Java quickstart guide."""

from pathlib import Path
import re
import unittest
from urllib.parse import unquote


ROOT = Path(__file__).resolve().parents[1]
GUIDE = ROOT / "docs/quickstart/java-quickstart.md"
POM = ROOT / "pom.xml"
JAVA_TEST = ROOT / "src/test/java/neqsim/documentation/JavaQuickstartDocumentationTest.java"
TWO_PORT = ROOT / "src/main/java/neqsim/process/equipment/TwoPortInterface.java"
STREAM = ROOT / "src/main/java/neqsim/process/equipment/stream/StreamInterface.java"
SYSTEM = ROOT / "src/main/java/neqsim/thermo/system/SystemInterface.java"


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


class JavaQuickstartDocumentationContractTest(unittest.TestCase):
    """Protect setup, executable API, units, and engineering boundaries."""

    @classmethod
    def setUpClass(cls):
        cls.guide = GUIDE.read_text(encoding="utf-8")
        cls.pom = POM.read_text(encoding="utf-8")
        cls.java_test = JAVA_TEST.read_text(encoding="utf-8")
        cls.two_port = TWO_PORT.read_text(encoding="utf-8")
        cls.stream = STREAM.read_text(encoding="utf-8")
        cls.system = SYSTEM.read_text(encoding="utf-8")

    def test_structure_and_internal_links_are_renderable(self):
        self.assertTrue(self.guide.startswith("---\n"))
        self.assertEqual(self.guide.count("# Java Quickstart"), 1)
        self.assertEqual(self.guide.count("\x60\x60\x60") % 2, 0)

        links = re.findall(r"(?<!!)\[[^\]]+\]\(([^)]+)\)", self.guide)
        for destination in links:
            if destination.startswith(("http://", "https://", "mailto:", "#")):
                continue
            target, _fragment = resolve_internal_target(GUIDE, destination)
            self.assertTrue(target.is_file())

    def test_dependency_matches_current_repository_revision(self):
        revision = re.search(r"<revision>([^<]+)</revision>", self.pom)
        self.assertIsNotNone(revision)
        version = revision.group(1)

        self.assertIn("<version>{}</version>".format(version), self.guide)
        self.assertIn(
            "com.equinor.neqsim:neqsim:{}".format(version),
            self.guide,
        )
        self.assertNotIn("<version>3.0.0</version>", self.guide)
        self.assertNotIn("neqsim:3.0.0", self.guide)
        self.assertIn("<artifactId>exec-maven-plugin</artifactId>", self.guide)
        self.assertIn(
            'mvn compile exec:java -Dexec.mainClass="FirstCalculation"',
            self.guide,
        )

    def test_java_examples_use_repository_logging_contract(self):
        fence = "\x60\x60\x60"
        java_blocks = re.findall(fence + r"java\n(.*?)" + fence, self.guide, flags=re.DOTALL)
        self.assertEqual(2, len(java_blocks))
        for block in java_blocks:
            self.assertIn("import org.apache.logging.log4j.LogManager;", block)
            self.assertIn("import org.apache.logging.log4j.Logger;", block)
            self.assertIn("private static final Logger logger =", block)
            self.assertIn("logger.info(", block)
            self.assertNotIn("System.out", block)
            self.assertNotIn("System.err", block)
            self.assertNotIn(".prettyPrint()", block)

    def test_documented_calls_have_executable_regression_coverage(self):
        for guide_token in (
            "new SystemSrkEos(298.15, 50.0)",
            'fluid.addComponent("methane", 0.85)',
            'fluid.setMixingRule("classic")',
            "operations.TPflash()",
            "fluid.initProperties()",
            'fluid.getDensity("kg/m3")',
            'new Stream("Feed", fluid)',
            'feed.setFlowRate(10000.0, "kg/hr")',
            'new Separator("HP Separator", feed)',
            "separator.setInternalDiameter(2.0)",
            'compressor.setOutletPressure(80.0, "bara")',
            "compressor.setIsentropicEfficiency(0.75)",
            "process.run()",
            'compressor.getPower("kW")',
            'compressor.getOutletStream().getTemperature("C")',
        ):
            self.assertIn(guide_token, self.guide)
            self.assertIn(guide_token, self.java_test)

        for test_token in (
            "testFirstFlashCalculation",
            "testFirstProcessSimulation",
            "assertTrue(Double.isFinite(densityKgPerCubicMetre))",
            "assertEquals(feedFlowKgPerHour, separatedFlowKgPerHour",
            'assertTrue(compressor.getPower("kW") > 0.0)',
        ):
            self.assertIn(test_token, self.java_test)

    def test_documented_overloads_exist_in_current_source(self):
        self.assertIn(
            "void setOutletPressure(double pressure, String unit)",
            self.two_port,
        )

        for token in (
            "void setFlowRate(double flowrate, String unit)",
            "double getFlowRate(String unit)",
            "double getTemperature(String unit)",
        ):
            self.assertIn(token, self.stream)

        for token in (
            "double getDensity(String unit)",
            "double getZ()",
            "void initProperties()",
        ):
            self.assertIn(token, self.system)

    def test_units_semantics_and_engineering_boundaries_are_explicit(self):
        normalized = " ".join(self.guide.split())
        for token in (
            "temperature in K and absolute pressure in bara",
            "component amounts in mol",
            "sum to 1.0 mol",
            'API unit token is `"kg/m3"`',
            "does not select or validate a density model",
            "equilibrium split at the feed state, here 50 bara",
            "does not impose a pressure reduction",
            "unit string alone does not enable Peneloux correction",
            "Constructors and single-argument process pressure setters use bara",
            "does not perform an implicit letdown",
            "not fluid-model selection, equipment sizing, process design approval, or safety certification",
            "convergence, conservation",
        ):
            self.assertIn(token, normalized)

        self.assertNotIn("flash to 20 bar", normalized)
        self.assertNotIn("Always set mixing rule", normalized)


if __name__ == "__main__":
    unittest.main()
