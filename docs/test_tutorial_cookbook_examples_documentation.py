"""Contracts for tutorials, cookbook, troubleshooting, and examples."""

from pathlib import Path
import re
import unittest
from urllib.parse import unquote


DOCS = Path(__file__).resolve().parent
SCOPE_PAGES = (
    DOCS / "cookbook/adsorption-recipes.md",
    DOCS / "cookbook/index.md",
    DOCS / "cookbook/pipeline-recipes.md",
    DOCS / "cookbook/process-recipes.md",
    DOCS / "cookbook/thermodynamics-recipes.md",
    DOCS / "cookbook/unit-conversion-recipes.md",
    DOCS / "examples/AIPlatformIntegration.md",
    DOCS / "examples/AdvancedRiskFramework_Tutorial.md",
    DOCS / "examples/BeerBrewing_BioProcess_Simulation.md",
    DOCS / "examples/ESP_Pump_Tutorial.md",
    DOCS / "examples/FieldDevelopmentWorkflow.md",
    DOCS / "examples/GERG2008_NH3_Ammonia_Properties.md",
    DOCS / "examples/GraphBasedProcessSimulation.md",
    DOCS / "examples/H2S_Distribution_Modeling.md",
    DOCS / "examples/IntegratedProductionRiskAnalysis.md",
    DOCS / "examples/LoopedPipelineNetworkExample.md",
    DOCS / "examples/MPC_Integration_Tutorial.md",
    DOCS / "examples/MercuryRemoval_LNG_Pretreatment.md",
    DOCS / "examples/MultiScenarioVFP_Tutorial.md",
    DOCS / "examples/MultiphaseFlowPipelineRiser_Interactive.md",
    DOCS / "examples/NeqSim_Python_Optimization.md",
    DOCS / "examples/NetworkSolverTutorial.md",
    DOCS / "examples/NorwegianEmissionMethods_Comparison.md",
    DOCS / "examples/PRODUCTION_OPTIMIZATION_GUIDE.md",
    DOCS / "examples/PVT_Simulation_and_Tuning.md",
    DOCS / "examples/ProducedWaterEmissions_Tutorial.md",
    DOCS / "examples/ProductionOptimizer_Tutorial.md",
    DOCS / "examples/ProductionSystem_BottleneckAnalysis.md",
    DOCS / "examples/ReadingFluidProperties.md",
    DOCS / "examples/ReservoirToMarket_DebottleneckPortfolio.md",
    DOCS / "examples/SeparatorEfficiency_GasScrubber_ThreePhase.md",
    DOCS / "examples/TVP_RVP_Study.md",
    DOCS / "examples/TwoFluidPipe_Tutorial.md",
    DOCS / "examples/autosize_and_optimize_workflows.md",
    DOCS / "examples/comparesimulations_quickstart.md",
    DOCS / "examples/index.md",
    DOCS / "examples/oilgas_production_energy_optimization.md",
    DOCS / "examples/process equipmentutl.md",
    DOCS / "examples/processmodel_plant_optimization.md",
    DOCS / "examples/reservoir_to_market_optimization.md",
    DOCS / "examples/selective-logic-execution.md",
    DOCS / "examples/transient_slug_separator_control_example.md",
    DOCS / "troubleshooting/index.md",
    DOCS / "tutorials/gosp_tutorial.md",
    DOCS / "tutorials/index.md",
    DOCS / "tutorials/learning-paths.md",
    DOCS / "tutorials/solve-engineering-task.md",
    DOCS / "tutorials/teg_dehydration_tutorial.md",
)

INDEX_PAGES = (
    DOCS / "REFERENCE_MANUAL_INDEX.md",
    DOCS / "cookbook/index.md",
    DOCS / "examples/index.md",
    DOCS / "troubleshooting/index.md",
    DOCS / "tutorials/index.md",
)

TASK_SOLVING_TUTORIAL = DOCS / "tutorials/solve-engineering-task.md"
PYTHON_QUICKSTART = DOCS / "quickstart/python-quickstart.md"
DEVTOOLS_PYPROJECT = DOCS.parent / "devtools/pyproject.toml"
NEQSIM_CLI = DOCS.parent / "devtools/neqsim_cli.py"
PROCESS_COMPARISON_PAGE = DOCS / "examples/comparesimulations_quickstart.md"
COMBINED_MODELS_TEST = (
    DOCS.parent
    / "src/test/java/neqsim/process/processmodel/CombinedModelsTest.java"
)

NORWEGIAN_COMPARISON_PAGE = DOCS / "examples/NorwegianEmissionMethods_Comparison.md"
PRODUCED_WATER_GUIDE = DOCS / "process/equipment/util/produced_water_degassing.md"
EMISSIONS_SOURCE = (
    DOCS.parent / "src/main/java/neqsim/process/equipment/util/EmissionsCalculator.java"
)
PRODUCED_WATER_SOURCE = (
    DOCS.parent
    / "src/main/java/neqsim/process/equipment/util/ProducedWaterDegassingSystem.java"
)
EMISSIONS_TEST = (
    DOCS.parent / "src/test/java/neqsim/process/equipment/util/EmissionsCalculatorTest.java"
)
PRODUCED_WATER_DOC_TEST = (
    DOCS.parent
    / "src/test/java/neqsim/process/equipment/util/ProducedWaterDegassingDocumentationTest.java"
)


def metadata_value(metadata, name):
    """Return a plain scalar, including folded YAML front-matter values."""
    prefix = name + ":"
    lines = metadata.splitlines()
    for index, line in enumerate(lines):
        if not line.startswith(prefix):
            continue
        value = line[len(prefix) :].strip()
        if value in {">", ">-", "|", "|-"}:
            parts = []
            for continuation in lines[index + 1 :]:
                if continuation and not continuation[0].isspace():
                    break
                if continuation.strip():
                    parts.append(continuation.strip())
            return " ".join(parts)
        return value.strip().strip("'\"")
    return ""


def parse_front_matter(source):
    """Return title, description, and body from one Markdown page."""
    match = re.match(r"\A---\n(?P<metadata>.*?)\n---\n(?P<body>.*)\Z", source, re.DOTALL)
    if match is None:
        raise AssertionError("Missing Jekyll front matter")
    metadata = match.group("metadata")
    return (
        metadata_value(metadata, "title"),
        metadata_value(metadata, "description"),
        match.group("body"),
    )


def remove_fenced_code(source):
    """Remove backtick and tilde code fences before inspecting rendered Markdown."""
    rendered = []
    marker = None
    for line in source.splitlines():
        stripped = line.lstrip()
        if marker is None and (
            stripped.startswith(("\x60\x60\x60", "~~~"))
        ):
            marker = stripped[:3]
            continue
        if marker is not None:
            if stripped.startswith(marker):
                marker = None
            continue
        rendered.append(line)
    return "\n".join(rendered)


def link_candidates(page, raw_target):
    """Return repository-source candidates for one relative documentation link."""
    target = unquote(raw_target.split("#", 1)[0].split("?", 1)[0].strip().strip("<>"))
    if target.startswith("/"):
        candidate = DOCS / target.lstrip("/")
    else:
        candidate = page.parent / target
    if candidate.suffix:
        stem = candidate.with_suffix("")
        return (
            candidate,
            stem / "README.md",
            stem / "index.md",
        )
    return (
        candidate.with_suffix(".md"),
        candidate / "README.md",
        candidate / "index.md",
    )


def section_after_heading(source, heading):
    """Return the Markdown section that starts at an exact level-two heading."""
    match = re.search(
        rf"^{re.escape(heading)}\n(?P<section>.*?)(?=^## |\Z)",
        source,
        re.MULTILINE | re.DOTALL,
    )
    if match is None:
        raise AssertionError(f"Missing section: {heading}")
    return match.group("section")


class TutorialCookbookExamplesDocumentationContractTest(unittest.TestCase):
    """Protect the frozen rotation-scope-6 documentation surface."""

    def test_task_solving_pull_request_example_keeps_further_reading_rendered(self):
        page = TASK_SOLVING_TUTORIAL.read_text(encoding="utf-8")
        rendered = remove_fenced_code(page)
        contribution = section_after_heading(page, "## After the Task: Contributing Back")
        command = (
            'gh pr create --title "Add hydrate formation analysis" '
            '--body "From task-solving workflow"'
        )

        self.assertIn(command, contribution)
        self.assertNotIn(command, rendered)
        self.assertIn("## Further Reading", rendered)
        self.assertIn(
            "[Agentic Workflow Examples](../integration/ai_workflow_examples)",
            rendered,
        )

    def test_task_solving_runtime_and_report_commands_match_current_sources(self):
        page = TASK_SOLVING_TUTORIAL.read_text(encoding="utf-8")
        quickstart = PYTHON_QUICKSTART.read_text(encoding="utf-8")
        pyproject = DEVTOOLS_PYPROJECT.read_text(encoding="utf-8")
        cli = NEQSIM_CLI.read_text(encoding="utf-8")
        prerequisites = section_after_heading(page, "## Prerequisites")
        report = section_after_heading(page, "### Step 3: Generate Reports")

        for marker in (
            "| Python 3.11+ |",
            "| Java JDK 17+ |",
            "Source compatibility is not the same as the runtime prerequisite.",
        ):
            with self.subTest(prerequisite=marker):
                self.assertIn(marker, prerequisites)

        self.assertIn('requires-python = ">=3.11"', pyproject)
        self.assertIn("requires Java 17 or newer", quickstart)
        self.assertIn("neqsim report .", report)
        self.assertIn('print("Usage: neqsim report [TASK_DIR]', cli)

        for stale in (
            "Python 3.8+",
            "Java JDK 8+",
            "python step3_report/generate_report.py",
            "python -m pip install python-docx matplotlib",
        ):
            with self.subTest(stale=stale):
                self.assertNotIn(stale, page)

    def test_process_comparison_quickstart_matches_executable_regression(self):
        page = PROCESS_COMPARISON_PAGE.read_text(encoding="utf-8")
        java_test = COMBINED_MODELS_TEST.read_text(encoding="utf-8")
        section = section_after_heading(page, "## Step-by-step Java example")

        self.assertNotIn("System.out", section)
        self.assertIn("ProcessComparisonQuickStart", section)
        self.assertIn("LogManager.getLogger(ProcessComparisonQuickStart.class)", section)
        self.assertIn("logger.info(", section)
        self.assertIn("testProcessComparisonQuickStart", java_test)

        workflow_markers = (
            "new SystemSrkEos(310.0, 50.0)",
            'setFlowRate(10.0, "MSm3/day")',
            'new ThreePhaseSeparator("1st stage separator", wellStreamHP)',
            "firstStageSeparator.getGasOutStream()",
            'setOutletPressure(100.0, "bara")',
            "combinedProcess.setRunStep(true)",
            "combinedProcess.run()",
            "reporter.generateJsonReport()",
        )
        for marker in workflow_markers:
            with self.subTest(marker=marker):
                self.assertIn(marker, section)
                self.assertIn(marker, java_test)

        regression_markers = (
            "Double.isFinite(gasFlow)",
            "gasFlow > 0.0",
            "gasFlow <= 10.0",
            "Double.isFinite(outletTemperature)",
            "outletTemperature > 36.85",
            "outletTemperature < 300.0",
            "Assertions.assertFalse(jsonReport.isEmpty())",
            "Assertions.assertEquals(combinedProcess.getReport_json(), jsonReport)",
        )
        for marker in regression_markers:
            with self.subTest(regression_marker=marker):
                self.assertIn(marker, java_test)


    def test_norwegian_comparison_reuses_executable_supported_example(self):
        page = NORWEGIAN_COMPARISON_PAGE.read_text(encoding="utf-8")
        supported_guide = PRODUCED_WATER_GUIDE.read_text(encoding="utf-8")
        java_test = PRODUCED_WATER_DOC_TEST.read_text(encoding="utf-8")
        section = section_after_heading(page, "## Thermodynamic screening calculation")
        supported_section = section_after_heading(supported_guide, "## Executable Java example")
        fence = re.compile(r"```java\n(?P<source>.*?)```", re.DOTALL)
        page_example = fence.search(section)
        supported_example = fence.search(supported_section)

        self.assertIsNotNone(page_example)
        self.assertIsNotNone(supported_example)
        self.assertEqual(supported_example.group("source"), page_example.group("source"))
        self.assertNotIn("System.out", section)
        self.assertNotIn("System.err", section)
        self.assertIn("supportedGuideExampleCompilesAndRuns", java_test)
        self.assertIn(
            'Paths.get("docs/process/equipment/util/produced_water_degassing.md")',
            java_test,
        )
        self.assertIn('example.getMethod("runExample").invoke(null)', java_test)

    def test_norwegian_comparison_matches_current_source_and_tests(self):
        page = NORWEGIAN_COMPARISON_PAGE.read_text(encoding="utf-8")
        emissions_source = EMISSIONS_SOURCE.read_text(encoding="utf-8")
        produced_water_source = PRODUCED_WATER_SOURCE.read_text(encoding="utf-8")
        emissions_test = EMISSIONS_TEST.read_text(encoding="utf-8")

        emissions_markers = (
            "HANDBOOK_F_CH4 = 14.0",
            "HANDBOOK_F_NMVOC = 3.5",
            "GWP_METHANE = 28.0",
            "GWP_NMVOC = 2.2",
            "calculateConventionalCH4(double producedWaterVolume_m3, double pressureDrop_bar)",
            "calculateConventionalNMVOC(double producedWaterVolume_m3, double pressureDrop_bar)",
            "calculateConventionalEmissions(double producedWaterVolume_m3,",
        )
        for marker in emissions_markers:
            with self.subTest(emissions_marker=marker):
                self.assertIn(marker, page)
                self.assertIn(marker, emissions_source)

        produced_water_markers = (
            "mixing rule 10",
            "Salinity is reporting metadata only",
            "public String getMethodComparisonReport()",
            "public Map<String, Object> getValidationResults()",
            "public Map<String, Object> toMap()",
        )
        for marker in produced_water_markers:
            with self.subTest(produced_water_marker=marker):
                self.assertIn(marker, produced_water_source)

        for marker in (
            "testConventionalHandbookMethods",
            "testMethodComparisonReport",
            'report.contains("Aktivitetsforskriften")',
        ):
            with self.subTest(test_marker=marker):
                self.assertIn(marker, emissions_test)

    def test_norwegian_comparison_has_safe_claims_and_provenance(self):
        page = NORWEGIAN_COMPARISON_PAGE.read_text(encoding="utf-8")
        for marker in (
            "screening and software-integration guide",
            "does not establish field accuracy",
            "not a regulatory decision",
            "must not be transferred to this convenience builder",
            "`setSalinity` stores metadata only",
            "representative pressurized-water sampling",
        ):
            with self.subTest(required_boundary=marker):
                self.assertIn(marker, page)

        for marker in (
            "System.out",
            "System.err",
            "TEGAbsorber",
            "TEGRegeneration",
            "±3.6%",
            "±7.4%",
            "11,000 tonnes",
            "4,700 tonnes",
            "✅ More accurate",
            "jNeqSim",
        ):
            with self.subTest(forbidden_claim=marker):
                self.assertNotIn(marker, page)

        link_markers = (
            "(ProducedWaterEmissions_Tutorial.md)",
            "(index.md)",
            "(../REFERENCE_MANUAL_INDEX.md#chapter-43-sustainability--emissions)",
            "(../process/equipment/util/produced_water_degassing.md)",
        )
        for marker in link_markers:
            with self.subTest(link_marker=marker):
                self.assertIn(marker, page)

    def test_frozen_scope_exists(self):
        self.assertEqual(48, len(SCOPE_PAGES))
        for page in SCOPE_PAGES:
            with self.subTest(page=page):
                self.assertTrue(page.is_file())

    def test_pages_have_searchable_front_matter(self):
        for page in SCOPE_PAGES:
            with self.subTest(page=page):
                title, description, _body = parse_front_matter(
                    page.read_text(encoding="utf-8")
                )
                self.assertTrue(title)
                self.assertGreaterEqual(len(description.split()), 5)

    def test_front_matter_title_is_not_repeated_as_h1(self):
        for page in SCOPE_PAGES:
            with self.subTest(page=page):
                title, _description, body = parse_front_matter(
                    page.read_text(encoding="utf-8")
                )
                rendered = remove_fenced_code(body)
                headings = re.findall(r"^#\s+(.+?)\s*$", rendered, re.MULTILINE)
                self.assertNotIn(title, headings)

    def test_pages_are_discoverable_from_repository_indexes(self):
        link_pattern = re.compile(r"(?<!!)\[[^\]\n]+\]\(([^)\n]+)\)")
        scheme = re.compile(r"^[a-z][a-z0-9+.-]*:", re.IGNORECASE)
        discovered = set()
        for index_page in INDEX_PAGES:
            rendered = remove_fenced_code(index_page.read_text(encoding="utf-8"))
            for match in link_pattern.finditer(rendered):
                raw_target = match.group(1).strip().split()[0]
                if raw_target.startswith("#") or scheme.match(raw_target):
                    continue
                for candidate in link_candidates(index_page, raw_target):
                    if candidate.is_file():
                        discovered.add(candidate.resolve())

        for page in SCOPE_PAGES:
            with self.subTest(page=page):
                self.assertIn(page.resolve(), discovered)

    def test_pages_use_supported_math_delimiters(self):
        unsupported = re.compile(r"\\\[|\\\]|\\\(|\\\)")
        for page in SCOPE_PAGES:
            with self.subTest(page=page):
                _title, _description, body = parse_front_matter(
                    page.read_text(encoding="utf-8")
                )
                self.assertIsNone(unsupported.search(remove_fenced_code(body)))

    def test_relative_source_links_resolve(self):
        link_pattern = re.compile(r"(?<!!)\[[^\]\n]+\]\(([^)\n]+)\)")
        scheme = re.compile(r"^[a-z][a-z0-9+.-]*:", re.IGNORECASE)
        for page in SCOPE_PAGES:
            rendered = remove_fenced_code(page.read_text(encoding="utf-8"))
            with self.subTest(page=page, check="single-line destinations"):
                self.assertNotRegex(rendered, r"\]\([^\n)]*\n")
            for match in link_pattern.finditer(rendered):
                raw_target = match.group(1).strip().split()[0]
                if raw_target.startswith("#") or scheme.match(raw_target):
                    continue
                candidates = link_candidates(page, raw_target)
                with self.subTest(page=page, target=raw_target):
                    self.assertTrue(
                        any(candidate.is_file() for candidate in candidates),
                        msg="Missing target; tried: "
                        + ", ".join(str(candidate) for candidate in candidates),
                    )


if __name__ == "__main__":
    unittest.main()
