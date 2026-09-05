"""Contracts for PVT, flow-assurance, and standards documentation."""

from pathlib import Path
import re
import unittest
from urllib.parse import unquote, urlsplit


DOCS = Path(__file__).resolve().parent
SCOPE_PAGES = (
    DOCS / "pvtsimulation/CO2ElectrolyzerExample.md",
    DOCS / "pvtsimulation/README.md",
    DOCS / "pvtsimulation/SolutionGasWaterRatio.md",
    DOCS / "pvtsimulation/blackoil_pvt_export.md",
    DOCS / "pvtsimulation/eclipse_e300_fluid_import.md",
    DOCS / "pvtsimulation/flow_assurance_overview.md",
    DOCS / "pvtsimulation/flowassurance/README.md",
    DOCS / "pvtsimulation/flowassurance/asphaltene_cpa_calculations.md",
    DOCS / "pvtsimulation/flowassurance/asphaltene_deboer_screening.md",
    DOCS / "pvtsimulation/flowassurance/asphaltene_method_comparison.md",
    DOCS / "pvtsimulation/flowassurance/asphaltene_modeling.md",
    DOCS / "pvtsimulation/flowassurance/asphaltene_parameter_fitting.md",
    DOCS / "pvtsimulation/flowassurance/asphaltene_validation.md",
    DOCS / "pvtsimulation/flowassurance/emulsion_viscosity_calculator.md",
    DOCS / "pvtsimulation/flowassurance/erosion_prediction.md",
    DOCS / "pvtsimulation/flowassurance/flow_assurance_screening_tools.md",
    DOCS / "pvtsimulation/fluid_characterization_mathematics.md",
    DOCS / "pvtsimulation/gas_pseudopressure_pseudocritical.md",
    DOCS / "pvtsimulation/json_fluid_format.md",
    DOCS / "pvtsimulation/mineral_scale_chemical_treatment_validation.md",
    DOCS / "pvtsimulation/mineral_scale_formation.md",
    DOCS / "pvtsimulation/ph_stabilization_corrosion.md",
    DOCS / "pvtsimulation/phase_envelope_guide.md",
    DOCS / "pvtsimulation/pvt_lab_tests.md",
    DOCS / "pvtsimulation/pvt_workflow.md",
    DOCS / "pvtsimulation/relative_permeability.md",
    DOCS / "pvtsimulation/reservoir_material_balance.md",
    DOCS / "pvtsimulation/scale_prediction_api.md",
    DOCS / "pvtsimulation/whitson_pvt_reader.md",
    DOCS / "standards/README.md",
    DOCS / "standards/astm_d6377_rvp.md",
    DOCS / "standards/dew_point_standards.md",
    DOCS / "standards/iec81346-reference-designations.md",
    DOCS / "standards/iso15403_cng_quality.md",
    DOCS / "standards/iso6578_lng_density.md",
    DOCS / "standards/iso6976_calorific_values.md",
    DOCS / "standards/oil_quality_standards.md",
    DOCS / "standards/sales_contracts.md",
)

PVT_LANDING_PAGE = DOCS / "pvtsimulation/README.md"
PVT_SEPARATOR_TEST = (
    DOCS.parent
    / "src/test/java/neqsim/pvtsimulation/PvtSimulationDocumentationTest.java"
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
    """Remove fenced examples and reject unmatched backtick or tilde fences."""
    rendered = []
    fence_character = None
    for line in source.splitlines():
        marker = re.match(r"^\s*(\x60{3,}|~{3,})", line)
        if marker is not None:
            character = marker.group(1)[0]
            if fence_character is None:
                fence_character = character
            elif character == fence_character:
                fence_character = None
            continue
        if fence_character is None:
            rendered.append(line)

    if fence_character is not None:
        raise AssertionError("Unclosed fenced code block")
    return "\n".join(rendered)


def target_candidates(source, target):
    """Return repository-source candidates for one documentation target."""
    parsed = urlsplit(target.strip().strip("<>"))
    if parsed.scheme or parsed.netloc or target.startswith("#"):
        return ()

    relative = unquote(parsed.path)
    if not relative:
        return ()
    if relative.startswith("/"):
        destination = (DOCS / relative.lstrip("/")).resolve()
    else:
        destination = (source.parent / relative).resolve()

    candidates = [destination]
    if destination.suffix == ".html":
        candidates.append(destination.with_suffix(".md"))
    if relative.endswith("/") or not destination.suffix:
        candidates.extend(
            (
                destination / "index.md",
                destination / "README.md",
                destination.with_suffix(".md"),
            )
        )
    return tuple(candidates)


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


class PvtFlowAssuranceStandardsDocumentationContractTest(unittest.TestCase):
    """Protect the frozen rotation-scope-5 documentation surface."""

    def test_pvt_separator_quickstart_matches_executable_regression(self):
        page = PVT_LANDING_PAGE.read_text(encoding="utf-8")
        java_test = PVT_SEPARATOR_TEST.read_text(encoding="utf-8")
        section = section_after_heading(
            page, "## Runnable multi-stage separator example"
        )

        self.assertNotIn("System.out", section)
        self.assertIn("LogManager.getLogger(PvtSeparatorQuickStart.class)", section)
        self.assertIn("logger.info(", section)
        self.assertIn("PvtSimulationDocumentationTest", section)

        workflow_markers = (
            'new SystemSrkEos(373.15, 300.0)',
            'setReservoirConditions(300.0, 100.0)',
            'addSeparatorStage(50.0, 40.0, "HP separator")',
            'addSeparatorStage(10.0, 30.0, "LP separator")',
            "addStockTankStage()",
            "separatorTest.run()",
        )
        for marker in workflow_markers:
            with self.subTest(marker=marker):
                self.assertIn(marker, section)
                self.assertIn(marker, java_test)

        regression_markers = (
            "assertEquals(3, stages.size())",
            'assertEquals("LP separator", stages.get(1).getStageName())',
            "Double.isFinite(separatorTest.getTotalGOR())",
            "separatorTest.getBo() > 1.0",
            "Double.isFinite(separatorTest.getStockTankOilDensity())",
            "separatorTest.getStockTankOilDensity() > 500.0",
            "separatorTest.getStockTankOilDensity() < 1200.0",
        )
        for marker in regression_markers:
            with self.subTest(regression_marker=marker):
                self.assertIn(marker, java_test)

    def test_frozen_scope_exists(self):
        self.assertEqual(38, len(SCOPE_PAGES))
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
                _title, _description, body = parse_front_matter(
                    page.read_text(encoding="utf-8")
                )
                rendered = remove_fenced_code(body)
                headings = re.findall(r"^#\s+(.+?)\s*$", rendered, re.MULTILINE)
                self.assertEqual([], headings)

    def test_pages_have_balanced_fenced_code(self):
        for page in SCOPE_PAGES:
            with self.subTest(page=page):
                _title, _description, body = parse_front_matter(
                    page.read_text(encoding="utf-8")
                )
                remove_fenced_code(body)

    def test_pages_use_supported_math_delimiters(self):
        unsupported = re.compile(r"\\\[|\\\]|\\\(|\\\)")
        for page in SCOPE_PAGES:
            with self.subTest(page=page):
                _title, _description, body = parse_front_matter(
                    page.read_text(encoding="utf-8")
                )
                self.assertIsNone(unsupported.search(remove_fenced_code(body)))

    def test_relative_source_links_resolve(self):
        markdown_link = re.compile(r"\[[^\]]*\]\(([^)\s]+)\)")
        html_link = re.compile(r"""(?:href|src)=["']([^"']+)["']""")
        for page in SCOPE_PAGES:
            _title, _description, body = parse_front_matter(
                page.read_text(encoding="utf-8")
            )
            rendered = remove_fenced_code(body)
            with self.subTest(page=page, check="single-line destinations"):
                self.assertNotRegex(rendered, r"\]\([^\n)]*\n")
            targets = markdown_link.findall(rendered)
            targets.extend(html_link.findall(rendered))
            for target in targets:
                candidates = target_candidates(page, target)
                if not candidates:
                    continue
                with self.subTest(page=page, target=target):
                    self.assertTrue(
                        any(candidate.exists() for candidate in candidates),
                        "Unresolved repository-relative target",
                    )


if __name__ == "__main__":
    unittest.main()
