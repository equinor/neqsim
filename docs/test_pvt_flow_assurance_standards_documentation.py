"""Contracts for PVT, flow-assurance, and standards documentation."""

from pathlib import Path
import re
import unittest


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
    target = raw_target.split("#", 1)[0].split("?", 1)[0].strip().strip("<>")
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


class PvtFlowAssuranceStandardsDocumentationContractTest(unittest.TestCase):
    """Protect the frozen rotation-scope-5 documentation surface."""

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
                title, _description, body = parse_front_matter(
                    page.read_text(encoding="utf-8")
                )
                rendered = remove_fenced_code(body)
                headings = re.findall(r"^#\s+(.+?)\s*$", rendered, re.MULTILINE)
                self.assertNotIn(title, headings)

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
