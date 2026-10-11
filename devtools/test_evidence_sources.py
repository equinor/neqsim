"""Evidence-source check: cues are published by installed agents, not hard-coded here."""

import tempfile
import unittest
from pathlib import Path
from unittest import mock

import validate_task_results as validator

FENCE = "`" * 3
BLOCK = (
    FENCE + "evidence-source-cues\n"
    "demo-data-agent :: \\bdemo[ -]data\\b|\\bwidget\\b\n"
    "demo-new-agent :: \\bgadget\\b\n"
    "not a cue line\n"
    "broken-agent :: (unclosed\n"
    "alias demo-old-agent = demo-new-agent\n" + FENCE + "\n"
)


class EvidenceSourceTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.task = Path(self.temp.name)
        self.results = {"uncertainty": {"method": "sensitivity"}}
        parsed = validator.parse_evidence_cue_block(BLOCK)
        patcher = mock.patch.object(validator, "load_evidence_source_cues", return_value=parsed)
        patcher.start()
        self.addCleanup(patcher.stop)

    def warnings_for(self, prompt):
        (self.task / "user_input.md").write_text(prompt, encoding="utf-8")
        return validator.check_evidence_sources(self.task, self.results)

    def test_block_parsing_skips_prose_and_invalid_regex(self):
        cues, aliases = validator.parse_evidence_cue_block("intro\n" + BLOCK + "outro")
        self.assertEqual([a for a, _ in cues], ["demo-data-agent", "demo-new-agent"])
        self.assertEqual(aliases, {"demo-old-agent": "demo-new-agent"})
        self.assertEqual(validator.parse_evidence_cue_block("no block here"), ([], {}))

    def test_cue_in_task_text_is_reported(self):
        self.assertIn("demo-data-agent", " ".join(self.warnings_for("Review the widget data")))
        self.assertEqual(self.warnings_for("unrelated pump sizing"), [])

    def test_recorded_status_or_alias_accounts_for_the_cue(self):
        for status in validator.EVIDENCE_STATUS:
            with self.subTest(status=status):
                self.results["agent_workflow_plan"] = {"evidence_sources": [
                    {"agent": "demo-data-agent", "status": status, "reason": "Recorded decision"}]}
                self.assertEqual(self.warnings_for("widget"), [])
        self.results["agent_workflow_plan"] = {"agents_used": ["demo-data-agent"]}
        self.assertEqual(self.warnings_for("widget"), [])
        self.results["agent_workflow_plan"] = {"evidence_sources": [
            {"agent": "demo-old-agent", "status": "no_access", "reason": "no session"}]}
        self.assertEqual(self.warnings_for("gadget"), [])

    def test_unknown_status_is_reported(self):
        self.results["agent_workflow_plan"] = {"evidence_sources": [
            {"agent": "demo-data-agent", "status": "maybe"}]}
        self.assertIn("unknown status", " ".join(self.warnings_for("widget")))

    def test_quick_tasks_do_not_warn(self):
        self.results = {}
        self.assertEqual(self.warnings_for("widget"), [])

    def test_task_spec_is_checked_and_only_missing_agent_is_reported(self):
        step1 = self.task / "step1_scope_and_research"
        step1.mkdir()
        (step1 / "task_spec.md").write_text("widget and gadget", encoding="utf-8")
        self.results["agent_workflow_plan"] = {"agents_used": [{"name": "demo-data-agent"}]}
        warnings = " ".join(validator.check_evidence_sources(self.task, self.results))
        self.assertIn("demo-new-agent", warnings)
        self.assertNotIn("demo-data-agent", warnings)


class EvidenceCueDiscoveryTest(unittest.TestCase):
    def test_cues_are_read_from_an_installed_agent_and_absent_without_one(self):
        with tempfile.TemporaryDirectory() as home, tempfile.TemporaryDirectory() as empty:
            agent = Path(home) / "demo-router"
            agent.mkdir()
            (agent / "AGENT.md").write_text("# Router\n" + BLOCK, encoding="utf-8")
            roots = [(Path(home), "installed", "*/AGENT.md", True)]
            with mock.patch("agent_search._discover_roots", return_value=roots):
                cues, aliases = validator.load_evidence_source_cues()
            self.assertEqual([a for a, _ in cues], ["demo-data-agent", "demo-new-agent"])
            self.assertEqual(aliases, {"demo-old-agent": "demo-new-agent"})
            with mock.patch("agent_search._discover_roots",
                            return_value=[(Path(empty), "installed", "*/AGENT.md", True)]):
                self.assertEqual(validator.load_evidence_source_cues(), ([], {}))


if __name__ == "__main__":
    unittest.main()
