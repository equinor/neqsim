"""Task-level regression tests for equipment evidence routing warnings."""

import tempfile
import unittest
from pathlib import Path

import validate_task_results as validator


PORTAL = "enterprise-equipment-portal-agent"
SEMI = "enterprise-semi-search-agent"


class EvidenceSourceTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.task = Path(self.temp.name)
        self.results = {"uncertainty": {"method": "sensitivity"}}

    def warnings_for(self, prompt):
        (self.task / "user_input.md").write_text(prompt, encoding="utf-8")
        return validator.check_evidence_sources(self.task, self.results)

    def test_equipment_portal_task_cues(self):
        for prompt in (
            "Equipment Portal U100 reference prices",
            "equipment-portal U400",
            "SPS handbook",
            "flowline reference price per km",
            "SURF CAPEX estimate",
            "satellite cost estimate",
            "template cost estimate",
            "flowline technology guidance",
            "subsea-system technology guidance",
            "design-to-cost study",
        ):
            with self.subTest(prompt=prompt):
                self.assertIn(PORTAL, " ".join(self.warnings_for(prompt)))

    def test_vendor_document_task_cues(self):
        for prompt in (
            "vendor datasheet for a pump",
            "vendor-data-sheet for a valve",
            "manufacturer catalogue",
            "manufacturer document",
            "SEMI library search",
            "SEMI-search for a pump manual",
            "semi.equinor.com",
            "pump_SMI-123",
            "SDI_456",
            "demister K-factor limit",
            "inlet device product limits",
        ):
            with self.subTest(prompt=prompt):
                self.assertIn(SEMI, " ".join(self.warnings_for(prompt)))

    def test_unrelated_words_do_not_trigger_new_agents(self):
        for prompt in ("semiconductor", "semi-implicit solver", "template costingx",
                       "surface cost", "redesignXtoYcost", "u1000"):
            with self.subTest(prompt=prompt):
                warnings = " ".join(self.warnings_for(prompt))
                self.assertNotIn(PORTAL, warnings)
                self.assertNotIn(SEMI, warnings)

    def test_accounted_sources_and_quick_tasks_do_not_warn(self):
        for status in validator.EVIDENCE_STATUS:
            with self.subTest(status=status):
                self.results["agent_workflow_plan"] = {"evidence_sources": [
                    {"agent": PORTAL, "status": status, "reason": "Recorded decision"},
                    {"agent": SEMI, "status": status, "reason": "Recorded decision"},
                ]}
                self.assertEqual(self.warnings_for("SURF cost and vendor manual"), [])
        self.results["agent_workflow_plan"] = {"agents_used": [PORTAL, {"name": SEMI}]}
        self.assertEqual(self.warnings_for("SURF cost and vendor manual"), [])
        self.results = {}
        self.assertEqual(self.warnings_for("SURF cost and vendor manual"), [])

    def test_task_spec_is_checked_and_only_missing_agent_is_reported(self):
        step1 = self.task / "step1_scope_and_research"
        step1.mkdir()
        (step1 / "task_spec.md").write_text("SURF cost and vendor manual", encoding="utf-8")
        self.results["agent_workflow_plan"] = {"agents_used": [{"name": PORTAL}]}
        warnings = " ".join(validator.check_evidence_sources(self.task, self.results))
        self.assertIn(SEMI, warnings)
        self.assertNotIn(PORTAL, warnings)


if __name__ == "__main__":
    unittest.main()
