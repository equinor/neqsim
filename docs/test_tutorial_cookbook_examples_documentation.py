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


class TutorialCookbookExamplesDocumentationContractTest(unittest.TestCase):
    """Protect the frozen rotation-scope-6 documentation surface."""

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
