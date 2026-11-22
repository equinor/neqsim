"""Contracts for the canonical controls benchmark documentation."""

import re
import unittest
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


class ControlsBenchmarkDocumentationTest(unittest.TestCase):
    """Keep the controls guide in the documentation workflow's unittest gate."""

    def test_controls_page_metadata_and_math_rendering(self) -> None:
        page = _text(PAGE)
        self.assertTrue(page.startswith('---\ntitle: "Canonical Controls Benchmark"\ndescription:'))
        self.assertNotIn("\n# Canonical Controls Benchmark\n", page)
        for delimiter in ("\\[", "\\]", "\\(", "\\)"):
            with self.subTest(delimiter=delimiter):
                self.assertNotIn(delimiter, page)
        self.assertIn("$$\\tau\\frac{dy}{dt}=y_{\\mathrm{target}}(u,d)-y$$", page)
        self.assertIn("$$C\\frac{dh}{dt}=q_{\\mathrm{in}}-q_{\\mathrm{out}}$$", page)
        self.assertEqual(page.count("$$"), 4)

    def test_controls_page_defines_units_and_evidence_class(self) -> None:
        # Markdown prose may wrap without changing its rendered meaning.
        page = " ".join(_text(PAGE).split())
        for token in (
            "$t$ is time [s]",
            "$u$ is controller output [%]",
            "$\\tau$ is the case time constant [s]",
            "$C=25\\ \\mathrm{s}$",
            "case-specific surrogate units",
            "deterministic regression qualification",
            "not an independent plant or vendor benchmark",
        ):
            with self.subTest(token=token):
                self.assertIn(token, page)

    def test_controls_java_api_and_published_values_are_executable_contracts(self) -> None:
        page = _text(PAGE)
        source = _text(SOURCE)
        java_test = _text(JAVA_TEST)
        for method in (
            "runCanonicalSuite",
            "getCases",
            "getCase",
            "getMetrics",
            "getTimeSeconds",
            "getProcessValue",
            "getSetPoint",
            "getControllerOutput",
            "getAgentBenchmarkReport",
            "isPassed",
        ):
            with self.subTest(method=method):
                self.assertIn(method, page)
                self.assertIn(method, source)
        prose = " ".join(page.split())
        self.assertIn("unmodifiable list of the six `CaseResult` objects", prose)
        self.assertIn("return copies of the uniformly sampled trace arrays", prose)
        self.assertIn("public List<CaseResult> getCases()", source)
        self.assertIn("return Collections.unmodifiableList(cases);", source)
        for case_id in CASE_IDS:
            with self.subTest(case_id=case_id):
                self.assertIn(case_id, page)
                self.assertIn(case_id, source)
                self.assertIn(case_id, java_test)
        self.assertIn("publishedReferenceValuesRemainCurrent", page)
        self.assertIn("void publishedReferenceValuesRemainCurrent()", java_test)
        for value in ("62.000", "143.093", "114.233", "147.880", "2.333", "476.566"):
            with self.subTest(value=value):
                self.assertIn(value, page)
                self.assertIn(value, java_test)

    def test_controls_page_is_discoverable_from_both_indexes(self) -> None:
        section_index = _text(SECTION_INDEX)
        reference_index = _text(REFERENCE_INDEX)
        self.assertIn("[Controller dynamics](controls_benchmark.md)", section_index)
        self.assertIn(
            "[docs/benchmarks/controls_benchmark.md](benchmarks/controls_benchmark.md)",
            reference_index,
        )
        self.assertIn("Deterministic source-linked regression", section_index)

    def test_scoped_relative_links_resolve(self) -> None:
        for source in (PAGE, SECTION_INDEX):
            text = _text(source)
            for target in re.findall(r"\[[^\]]+\]\(([^)]+)\)", text):
                if target.startswith(("http://", "https://", "#")):
                    continue
                relative = target.split("#", 1)[0]
                if not relative:
                    continue
                with self.subTest(source=source, target=target):
                    self.assertTrue((source.parent / relative).resolve().exists())


if __name__ == "__main__":
    unittest.main()
