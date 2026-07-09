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
import os
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


if __name__ == "__main__":
    unittest.main()
