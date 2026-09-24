"""Regression tests for the Solution Workflow report section.

Tests the canonical report generator
(devtools/task_template/step3_report/generate_report.py) that real tasks run —
specifically the ``agent_workflow_plan`` → "Solution Workflow" rendering added so
the report documents *how* a task was solved (discovered/used agents + workflow).

Importing the canonical module requires python-docx (matplotlib is optional). If
python-docx is not installed the module calls sys.exit at import, so these tests
skip cleanly when the dependency is absent.
"""
import importlib.util
import sys
import unittest
from pathlib import Path

CANONICAL = (
    Path(__file__).resolve().parent
    / "task_template" / "step3_report" / "generate_report.py"
)


def _load_generate_report():
    """Import the canonical generate_report module, or return None if unavailable."""
    if not CANONICAL.is_file():
        return None
    spec = importlib.util.spec_from_file_location("canonical_generate_report", CANONICAL)
    mod = importlib.util.module_from_spec(spec)
    saved_argv = sys.argv
    sys.argv = ["generate_report.py"]
    try:
        spec.loader.exec_module(mod)
    except SystemExit:
        # module exits at import if python-docx is missing
        return None
    finally:
        sys.argv = saved_argv
    return mod


GR = _load_generate_report()

_PLAN = {
    "agent_workflow_plan": {
        "workflow_type": "composition_pattern",
        "workflow": "process.model -> mechanical.design",
        "discovery": {
            "skill_search": "devtools/skill_search.py",
            "agent_search": "step1_scope_and_research/agent_plan.json",
        },
        "agents_used": [
            {"handle": "process.model", "repo": "neqsim", "role": "build flowsheet",
             "loads_skills": ["neqsim-process-modeling"]},
            {"handle": "mechanical.design", "repo": "neqsim", "role": "size equipment",
             "loads_skills": ["neqsim-api-patterns"]},
        ],
        "rationale": "Two-stage composition then mechanical design.",
    },
    "key_results": {"outlet_T_C": 25.3},
}


@unittest.skipIf(GR is None, "canonical generate_report not importable (python-docx missing)")
class WorkflowHtmlTest(unittest.TestCase):
    def test_populated_renders_agents_and_workflow(self):
        html = GR.format_workflow_html(_PLAN)
        self.assertIn("composition_pattern", html)
        self.assertIn("mechanical.design", html)
        self.assertIn("neqsim-process-modeling", html)
        self.assertIn("Two-stage composition", html)

    def test_empty_plan_renders_nothing(self):
        self.assertEqual(GR.format_workflow_html({}), "")
        self.assertEqual(GR.format_workflow_html({"agent_workflow_plan": {}}), "")

    def test_handle_falls_back_to_name(self):
        plan = {"agent_workflow_plan": {"agents_used": [{"name": "field.development",
                                                         "repo": "neqsim"}]}}
        html = GR.format_workflow_html(plan)
        self.assertIn("field.development", html)


@unittest.skipIf(GR is None, "canonical generate_report not importable (python-docx missing)")
class WorkflowSectionTest(unittest.TestCase):
    def test_section_present_when_plan_exists(self):
        sections = GR.build_sections(_PLAN, None)
        wf = [s for s in sections if s.get("has_workflow")]
        self.assertEqual(len(wf), 1)
        self.assertTrue(wf[0]["heading"].endswith("Solution Workflow"))

    def test_section_absent_when_no_plan(self):
        sections = GR.build_sections({"key_results": {"x": 1.0}}, None)
        self.assertFalse(any(s.get("has_workflow") for s in sections))


@unittest.skipIf(GR is None, "canonical generate_report not importable (python-docx missing)")
class ReproducibilityAppendixTest(unittest.TestCase):
    _REPRO = {"key_results": {"x": 1.0}, "reproducibility": {
        "environment": ["Python 3.11", "NeqSim 3.1"],
        "steps": ["python 01_profiles.py", "python 02_study.py"],
        "checks": ["2026 bottleneck inlet scrubber at 117 %"],
    }}

    def test_dict_renders_numbered_steps_and_checks(self):
        text = GR.format_reproducibility_text(self._REPRO)
        self.assertIn("1. python 01_profiles.py", text)
        self.assertIn("2. python 02_study.py", text)
        self.assertIn("- 2026 bottleneck inlet scrubber at 117 %", text)

    def test_appendix_added_and_absent_without_block(self):
        headings = [s["heading"] for s in GR.build_sections(self._REPRO, None)]
        self.assertTrue(any("Reproducing the Results" in h for h in headings))
        headings = [s["heading"] for s in GR.build_sections({"key_results": {"x": 1.0}}, None)]
        self.assertFalse(any("Reproducing the Results" in h for h in headings))


@unittest.skipIf(GR is None, "canonical generate_report not importable (python-docx missing)")
class FigureCaptionTest(unittest.TestCase):

    def caption(self, name, results=None):
        return GR.get_figure_caption(name, results or {}, 1)

    def test_discussion_title_is_used(self):
        results = {"figure_discussion": [{"figure": "fig02_heatmap.png", "title": "Utilisation by year"}]}
        self.assertEqual(self.caption("fig02_heatmap.png", results), "Figure 1: Utilisation by year")

    def test_only_fig_prefix_is_stripped(self):
        self.assertEqual(self.caption("fig03_top_constraints.png"), "Figure 1: Top constraints")
        self.assertEqual(self.caption("Figure_12-pressure.png"), "Figure 1: Pressure")
        self.assertEqual(self.caption("2026_profile.png"), "Figure 1: 2026 profile")
        self.assertEqual(self.caption("3d_view.png"), "Figure 1: 3d view")


@unittest.skipIf(GR is None, "canonical generate_report not importable (python-docx missing)")
class ValidationListTest(unittest.TestCase):

    def test_list_rows_keep_failures_and_duplicates(self):
        data = GR._normalize_validation({"validation": [
            {"check": "mass balance error", "status": "PASS"},
            {"check": "flow", "status": "FAILED"},
            {"check": "flow", "status": "WARN"},
            {"check": "power", "passed": False},
        ]})
        self.assertEqual(data["validation"], {
            "mass balance error": "PASS", "flow": "FAIL", "flow (3)": "WARN", "power": "FAIL"})
        self.assertEqual(sorted(GR._validation_failures(data)),
                         sorted([GR._label_from_key("flow"), GR._label_from_key("power")]))


if __name__ == "__main__":
    unittest.main()
