"""Contracts for engineering, safety, risk, and field-development documentation."""

from pathlib import Path
import re
import unittest


DOCS = Path(__file__).resolve().parent
SCOPE_PAGES = (
    DOCS / "examples/FieldDevelopmentWorkflow.md",
    DOCS / "fielddevelopment/API_GUIDE.md",
    DOCS / "fielddevelopment/DECISION_ENGINE_WORKFLOWS.md",
    DOCS / "fielddevelopment/DIGITAL_FIELD_TWIN.md",
    DOCS / "fielddevelopment/FIELD_DEVELOPMENT_STRATEGY.md",
    DOCS / "fielddevelopment/HOST_TIE_IN_CAPACITY.md",
    DOCS / "fielddevelopment/INTEGRATED_FIELD_DEVELOPMENT_FRAMEWORK.md",
    DOCS / "fielddevelopment/INTEGRATED_PRODUCTION_MODELLING.md",
    DOCS / "fielddevelopment/LATE_LIFE_OPERATIONS.md",
    DOCS / "fielddevelopment/MATHEMATICAL_REFERENCE.md",
    DOCS / "fielddevelopment/MULTI_SCENARIO_PRODUCTION_OPTIMIZATION.md",
    DOCS / "fielddevelopment/README.md",
    DOCS / "process/DESIGN_FRAMEWORK.md",
    DOCS / "process/design/templates_guide.md",
    DOCS / "process/mechanical_design.md",
    DOCS / "process/mechanical_design/tema_standard_guide.md",
    DOCS / "process/mechanical_design/thermal_hydraulic_design.md",
    DOCS / "process/mechanical_design/two_phase_heat_transfer.md",
    DOCS / "process/mechanical_design_database.md",
    DOCS / "process/mechanical_design_standards.md",
    DOCS / "process/safety/README.md",
    DOCS / "process/safety/release-dispersion-scenarios.md",
    DOCS / "process/safety/scenario-generation.md",
    DOCS / "risk/PHYSICS_BASED_RISK_INTEGRATION.md",
    DOCS / "risk/README.md",
    DOCS / "risk/RELIABILITY_DATA_GUIDE.md",
    DOCS / "risk/api-reference.md",
    DOCS / "risk/bowtie-analysis.md",
    DOCS / "risk/condition-based.md",
    DOCS / "risk/degraded-operation.md",
    DOCS / "risk/dependency-analysis.md",
    DOCS / "risk/dynamic-simulation.md",
    DOCS / "risk/equipment-failure.md",
    DOCS / "risk/index.md",
    DOCS / "risk/mathematical-reference.md",
    DOCS / "risk/monte-carlo.md",
    DOCS / "risk/overview.md",
    DOCS / "risk/production-impact.md",
    DOCS / "risk/risk-matrix.md",
    DOCS / "risk/sis-integration.md",
    DOCS / "risk/stid-tagging.md",
    DOCS / "risk/topology.md",
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
        return (candidate,)
    return (
        candidate.with_suffix(".md"),
        candidate / "README.md",
        candidate / "index.md",
    )


class EngineeringSafetyFieldDocumentationContractTest(unittest.TestCase):
    """Protect the frozen rotation-scope-4 documentation surface."""

    def test_frozen_scope_exists(self):
        self.assertEqual(42, len(SCOPE_PAGES))
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
                self.assertGreaterEqual(5, len(description.split()))

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
