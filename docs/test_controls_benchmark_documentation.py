"""Contracts for the canonical controls benchmark documentation."""

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PAGE = ROOT / "docs" / "benchmarks" / "controls_benchmark.md"
SECTION_INDEX = ROOT / "docs" / "benchmarks" / "index.md"
REFERENCE_INDEX = ROOT / "docs" / "REFERENCE_MANUAL_INDEX.md"
SOURCE = (
    ROOT
    / "src"
    / "main"
    / "java"
    / "neqsim"
    / "process"
    / "controllerdevice"
    / "ControlsBenchmarkSuite.java"
)
JAVA_TEST = (
    ROOT
    / "src"
    / "test"
    / "java"
    / "neqsim"
    / "process"
    / "controllerdevice"
    / "ControlsBenchmarkSuiteTest.java"
)

CASE_IDS = (
    "control_level_setpoint",
    "control_pressure_disturbance",
    "control_cascade_temperature",
    "control_split_range",
    "control_anti_surge",
    "control_speed_recycle_coordination",
)


def _text(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def test_controls_page_metadata_and_math_rendering() -> None:
    page = _text(PAGE)
    assert page.startswith("---\ntitle: \"Canonical Controls Benchmark\"\ndescription:")
    assert "\n# Canonical Controls Benchmark\n" not in page
    assert "\\[" not in page and "\\]" not in page
    assert "\\(" not in page and "\\)" not in page
    assert "$$\\tau\\frac{dy}{dt}=y_{\\mathrm{target}}(u,d)-y$$" in page
    assert "$$C\\frac{dh}{dt}=q_{\\mathrm{in}}-q_{\\mathrm{out}}$$" in page
    assert page.count("$$") == 4


def test_controls_page_defines_units_and_evidence_class() -> None:
    page = _text(PAGE)
    for token in (
        "$t$ is time [s]",
        "$u$ is controller output [%]",
        "$\\tau$ is the case time constant [s]",
        "$C=25\\ \\mathrm{s}$",
        "case-specific surrogate units",
        "deterministic regression qualification",
        "not an independent plant or vendor benchmark",
    ):
        assert token in page


def test_controls_java_api_and_published_values_are_executable_contracts() -> None:
    page = _text(PAGE)
    source = _text(SOURCE)
    java_test = _text(JAVA_TEST)
    for method in (
        "runCanonicalSuite",
        "getCases",
        "getCase",
        "getMetrics",
        "getAgentBenchmarkReport",
        "isPassed",
    ):
        assert method in page
        assert method in source
    for case_id in CASE_IDS:
        assert case_id in page
        assert case_id in source
        assert case_id in java_test
    assert "publishedReferenceValuesRemainCurrent" in page
    assert "void publishedReferenceValuesRemainCurrent()" in java_test
    for value in ("62.000", "143.093", "114.233", "147.880", "2.333", "476.566"):
        assert value in page
        assert value in java_test


def test_controls_page_is_discoverable_from_both_indexes() -> None:
    section_index = _text(SECTION_INDEX)
    reference_index = _text(REFERENCE_INDEX)
    assert "[Controller dynamics](controls_benchmark.md)" in section_index
    assert "[docs/benchmarks/controls_benchmark.md](benchmarks/controls_benchmark.md)" in reference_index
    assert "Deterministic source-linked regression" in section_index


def test_scoped_relative_links_resolve() -> None:
    for source in (PAGE, SECTION_INDEX):
        text = _text(source)
        for target in re.findall(r"\[[^\]]+\]\(([^)]+)\)", text):
            if target.startswith(("http://", "https://", "#")):
                continue
            relative = target.split("#", 1)[0]
            if not relative:
                continue
            assert (source.parent / relative).resolve().exists(), f"{source}: {target}"
