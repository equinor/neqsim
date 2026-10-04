"""Contracts for the source-backed PVT calibration and validation workflow."""

from pathlib import Path
import re
import unittest
from urllib.parse import unquote


ROOT = Path(__file__).resolve().parents[1]
DOCS = ROOT / "docs"
GUIDE = DOCS / "pvtsimulation" / "pvt_workflow.md"
INDEX = DOCS / "REFERENCE_MANUAL_INDEX.md"
REGRESSION = ROOT / "src/main/java/neqsim/pvtsimulation/regression/PVTRegression.java"
PARAMETER = (
    ROOT / "src/main/java/neqsim/pvtsimulation/regression/RegressionParameter.java"
)
RESULT = ROOT / "src/main/java/neqsim/pvtsimulation/regression/RegressionResult.java"
REPORT = ROOT / "src/main/java/neqsim/pvtsimulation/util/PVTReportGenerator.java"
REGRESSION_TEST = (
    ROOT
    / "src/test/java/neqsim/pvtsimulation/regression/PVTRegressionTest.java"
)
DOCUMENTATION_TEST = (
    ROOT
    / "src/test/java/neqsim/pvtsimulation/PvtSimulationDocumentationTest.java"
)
PVT_OVERVIEW = DOCS / "pvtsimulation" / "README.md"


def normalized(text):
    """Collapse whitespace so source signatures remain formatting-independent."""
    return " ".join(text.split())


def heading_anchors(markdown):
    """Return the GitHub-style anchors needed by links in this guide."""
    anchors = set()
    for line in markdown.splitlines():
        match = re.match(r"^#{1,6}\s+(.+?)\s*$", line)
        if not match:
            continue
        heading = re.sub(r"<[^>]+>", "", match.group(1)).lower()
        heading = re.sub(r"[^\w\- ]", "", heading)
        anchors.add(re.sub(r"-+", "-", heading.replace(" ", "-")).strip("-"))
    return anchors


class PvtWorkflowDocumentationTest(unittest.TestCase):
    """Protect the D148 PVT workflow evidence boundary."""

    @classmethod
    def setUpClass(cls):
        cls.guide = GUIDE.read_text(encoding="utf-8")
        cls.guide_normalized = normalized(cls.guide)
        cls.regression = REGRESSION.read_text(encoding="utf-8")
        cls.regression_normalized = normalized(cls.regression)
        cls.parameter = PARAMETER.read_text(encoding="utf-8")
        cls.result = RESULT.read_text(encoding="utf-8")
        cls.result_normalized = normalized(cls.result)
        cls.report = REPORT.read_text(encoding="utf-8")
        cls.report_normalized = normalized(cls.report)
        cls.regression_test = REGRESSION_TEST.read_text(encoding="utf-8")
        cls.documentation_test = DOCUMENTATION_TEST.read_text(encoding="utf-8")
        cls.pvt_overview = PVT_OVERVIEW.read_text(encoding="utf-8")

    def test_front_matter_heading_and_code_policy(self):
        self.assertTrue(self.guide.startswith("---\n"))
        closing = self.guide.find("\n---\n", 4)
        self.assertGreater(closing, 4)
        front_matter = self.guide[4:closing]
        body = self.guide[closing + 5 :]
        self.assertRegex(front_matter, r"(?m)^title:\s*.+$")
        self.assertRegex(front_matter, r"(?m)^description:\s*.+$")
        self.assertNotRegex(body, r"(?m)^#\s+")
        self.assertNotIn("```", body)
        self.assertNotIn("System.out", body)
        self.assertNotIn("System.err", body)
        self.assertNotIn("Path.of", body)

    def test_relative_links_and_fragments_resolve(self):
        links = re.findall(r"(?<!!)\[[^\]]+\]\(([^)]+)\)", self.guide)
        self.assertGreaterEqual(len(links), 15)
        for raw_target in links:
            target = unquote(raw_target.strip().split()[0].strip("<>"))
            if target.startswith(("http://", "https://", "mailto:")):
                continue
            path_text, _, fragment = target.partition("#")
            destination = (GUIDE.parent / path_text).resolve() if path_text else GUIDE
            self.assertTrue(destination.is_file(), target)
            if fragment:
                anchors = heading_anchors(destination.read_text(encoding="utf-8"))
                self.assertIn(fragment.lower(), anchors, target)

    def test_regression_source_contract(self):
        required = [
            "public PVTRegression(SystemInterface fluid)",
            "this.baseFluid = fluid.clone();",
            "this.tunedFluid = fluid.clone();",
            (
                "public void addCCEData(double[] pressures, "
                "double[] relativeVolumes, double temperature)"
            ),
            (
                "public void addCVDData(double[] pressures, "
                "double[] liquidDropout, double[] zFactors, double temperature)"
            ),
            (
                "public void addDLEData(double[] pressures, double[] rs, "
                "double[] bo, double[] oilDensity, double temperature)"
            ),
            (
                "public void addSeparatorData(double gor, double bo, "
                "double apiGravity, double separatorPressure, "
                "double separatorTemperature, double reservoirTemperature)"
            ),
            (
                "public void addViscosityData(double[] pressures, "
                "double[] viscosities, double temperature, String phaseName)"
            ),
            (
                "public void addRegressionParameter(RegressionParameter parameter, "
                "double lowerBound, double upperBound, double initialGuess)"
            ),
            "public void setExperimentWeight(ExperimentType type, double weight)",
            "public void setMaxIterations(int maxIterations)",
            "public void setTolerance(double tolerance)",
            "public void setVerbose(boolean verbose)",
            "public RegressionResult runRegression()",
            'throw new IllegalStateException("No regression parameters defined")',
            'throw new IllegalStateException("No experimental data provided")',
        ]
        for signature in required:
            self.assertIn(normalized(signature), self.regression_normalized)

    def test_parameter_result_and_report_source_contracts(self):
        self.assertIn("public double[] getDefaultBounds()", self.parameter)

        for signature in [
            "public SystemInterface getTunedFluid()",
            "public Map<ExperimentType, Double> getObjectiveValues()",
            "public double getTotalObjective()",
            "public double getObjectiveValue(ExperimentType type)",
            "public List<RegressionParameterConfig> getParameterConfigs()",
            "public double getOptimizedValue(RegressionParameter parameter)",
            "public UncertaintyAnalysis getUncertainty()",
            "public double[] getConfidenceInterval(RegressionParameter parameter)",
            "public double getFinalChiSquare()",
        ]:
            self.assertIn(normalized(signature), self.result_normalized)

        for signature in [
            "public PVTReportGenerator(SystemInterface fluid)",
            (
                "public PVTReportGenerator setProjectInfo("
                "String projectName, String fluidName)"
            ),
            "public PVTReportGenerator setLabInfo(String labName, String sampleDate)",
            (
                "public PVTReportGenerator setReservoirConditions("
                "double pressure, double temperatureCelsius)"
            ),
            "public PVTReportGenerator addCCE(ConstantMassExpansion cce)",
            "public PVTReportGenerator addDLE(DifferentialLiberation dle)",
            "public PVTReportGenerator addCVD(ConstantVolumeDepletion cvd)",
            (
                "public PVTReportGenerator addSeparatorTest("
                "MultiStageSeparatorTest sepTest)"
            ),
            "public String generateMarkdownReport()",
        ]:
            self.assertIn(normalized(signature), self.report_normalized)

    def test_executable_evidence_remains_enabled(self):
        for source in [self.regression_test, self.documentation_test]:
            self.assertIn("@Test", source)
            self.assertNotIn("@Disabled", source)
        self.assertIn(
            "testRegressionFitsSingleCspViscosityParameter", self.regression_test
        )
        self.assertIn("PvtSeparatorQuickStart", self.pvt_overview)
        self.assertIn(
            "LogManager.getLogger(PvtSeparatorQuickStart.class)",
            self.pvt_overview,
        )
        self.assertIn("MultiStageSeparatorTest", self.documentation_test)

    def test_engineering_boundaries_and_units_are_explicit(self):
        required_phrases = [
            "calibration is not independent validation",
            "project-specific",
            "equal-length",
            "non-empty",
            "finite",
            "hold-out",
            "java 8/log4j2",
            "pressure in bar",
            "pressure in bara",
            "temperature in k",
            "temperature in °c",
            "volume %",
            "sm³/sm³",
            "m³/sm³",
            "kg/m³",
            "pa s",
            "does not provide an eclipseeosexporter api",
        ]
        lower_guide = self.guide_normalized.lower().replace("`", "")
        for phrase in required_phrases:
            self.assertIn(phrase, lower_guide)

    def test_stale_workflow_claims_do_not_return(self):
        rejected = [
            "import neqsim.blackoil.io.EclipseEOSExporter",
            "PVT_TUNED.INC",
            "PVT_FIELD.INC",
            "Typical Bounds",
            "Field X Development",
            "Well A-1 Sample",
            "Core Lab",
            "Schlumberger",
            "Intertek",
            "Complete Example",
        ]
        for phrase in rejected:
            self.assertNotIn(phrase, self.guide)

    def test_navigation_route_is_preserved(self):
        index = INDEX.read_text(encoding="utf-8")
        self.assertIn(
            "[docs/pvtsimulation/pvt_workflow.md](pvtsimulation/pvt_workflow.md)",
            index,
        )


if __name__ == "__main__":
    unittest.main()
