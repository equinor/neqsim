"""Hermetic evidence routing tests with a synthetic installed agent catalog.

The cue declarations below are test inputs, not a copy of an enterprise catalog.
Exercise real discovery, parsing and validation without depending on optional agents
installed on the developer's machine or on the CI runner.
"""

import os
import tempfile
import unittest
from contextlib import ExitStack
from pathlib import Path
from unittest import mock

import validate_task_results as validator


PORTAL = "fixture-equipment-portal-agent"
SEMI = "fixture-vendor-documents-agent"
GEOX = "fixture-prospect-agent"
GEOX_ALIAS = "fixture-prospect-legacy-agent"
STID = "fixture-plant-documents-agent"

# Deliberately small patterns for the contract cases below. Real routing policy
# belongs to the provider's agent definitions, never to this public validator.
TIEBACK_CUE = r"\b(?:vpbo|tie-?back|development solution)\b"
CATALOG = {
    PORTAL: (r"\b(?:equipment[ -]portal|u[14]00|sps handbook|surf (?:capex|cost)|"
             r"(?:satellite|template) cost|flowline (?:reference price|technology)|"
             r"subsea-system technology|design-to-cost)\b|" + TIEBACK_CUE),
    SEMI: (r"\b(?:vendor[ -](?:data[ -]?sheet|manual)|manufacturer (?:catalogue|document)|"
           r"semi[ -](?:library|search)|semi\.equinor\.com|demister k-factor|inlet device)\b"
           r"|(?:smi|sdi)[_-]\d+"),
    GEOX: r"\bapbo\b",
    STID: TIEBACK_CUE,
}


class EvidenceSourceTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.task = self.root / "task"
        self.task.mkdir()
        self.agents = self.root / "installed-agents"
        self.plugins = self.root / "plugins"
        self.repo = self.root / "workspace" / "neqsim"
        contexts = ExitStack()
        self.addCleanup(contexts.close)
        contexts.enter_context(mock.patch.dict(os.environ, {
            "NEQSIM_AGENTS_HOME": str(self.agents),
            "NEQSIM_AGENT_PLUGINS_HOME": str(self.plugins),
        }))
        # The validator derives checkout/sibling roots from its own file path.
        # Redirect that boundary too, leaving discovery and cue loading intact.
        contexts.enter_context(mock.patch.object(
            validator, "__file__", str(self.repo / "devtools" / "validate_task_results.py")))
        for agent, cue in CATALOG.items():
            declaration = "{} :: {}\n".format(agent, cue)
            if agent == GEOX:
                declaration += "alias {} = {}\n".format(GEOX_ALIAS, GEOX)
            self.write_agent(self.agents / agent / "AGENT.md", declaration)
        self.results = {"uncertainty": {"method": "sensitivity"}}

    def write_agent(self, path, declaration):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text("# Synthetic test agent\n\n```evidence-source-cues\n" +
                        declaration + "```\n", encoding="utf-8")

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

    def test_geox_cue_accepts_either_geox_agent_name(self):
        for name in (GEOX, GEOX_ALIAS):
            with self.subTest(name=name):
                self.results["agent_workflow_plan"] = {"evidence_sources": [
                    {"agent": name, "status": "no_access", "reason": "needs a signed-in desktop session"}]}
                self.assertEqual(self.warnings_for("APbo prospect chance of success"), [])
        self.results["agent_workflow_plan"] = {}
        self.assertIn(GEOX, " ".join(self.warnings_for("APbo prospect")))

    def test_tieback_and_vpbo_tasks_cue_portal_and_stid(self):
        for prompt in ("Mistral Nord VPbo onboarding", "subsea tie-back to a host", "tieback of a discovery"):
            with self.subTest(prompt=prompt):
                warnings = " ".join(self.warnings_for(prompt))
                self.assertIn(PORTAL, warnings)
                self.assertIn(STID, warnings)
        self.assertIn(PORTAL, " ".join(self.warnings_for("development solution for a discovery")))

    def test_task_spec_is_checked_and_only_missing_agent_is_reported(self):
        step1 = self.task / "step1_scope_and_research"
        step1.mkdir()
        (step1 / "task_spec.md").write_text("SURF cost and vendor manual", encoding="utf-8")
        self.results["agent_workflow_plan"] = {"agents_used": [{"name": PORTAL}]}
        warnings = " ".join(validator.check_evidence_sources(self.task, self.results))
        self.assertIn(SEMI, warnings)
        self.assertNotIn(PORTAL, warnings)

    def test_no_installed_catalog_has_no_provider_warnings(self):
        with mock.patch.dict(os.environ, {"NEQSIM_AGENTS_HOME": str(self.root / "absent")}):
            self.assertEqual(validator.load_evidence_source_cues(), ([], {}))
            self.assertEqual(self.warnings_for("SURF cost, vendor manual and APbo prospect"), [])

    def test_plugin_only_catalog_supplies_cues_and_aliases(self):
        agent_path = (self.plugins / "fixture-plugin" / "com.github.copilot" /
                      "agents" / "fixture.agent.md")
        self.write_agent(agent_path, "{} :: {}\nalias {} = {}\n".format(
            GEOX, CATALOG[GEOX], GEOX_ALIAS, GEOX))
        with mock.patch.dict(os.environ, {"NEQSIM_AGENTS_HOME": str(self.root / "absent")}):
            self.assertEqual(validator.load_evidence_source_cues(),
                             ([(GEOX, CATALOG[GEOX])], {GEOX_ALIAS: GEOX}))
            self.assertIn(GEOX, " ".join(self.warnings_for("APbo prospect")))
            self.results["agent_workflow_plan"] = {"agents_used": [GEOX_ALIAS]}
            self.assertEqual(self.warnings_for("APbo prospect"), [])

    def test_duplicate_cues_and_invalid_regex_do_not_hide_valid_warning(self):
        self.write_agent(self.repo / ".github" / "agents" / "fixture.agent.md",
                         "invalid-agent :: [\n{} :: {}\n".format(PORTAL, CATALOG[PORTAL]))
        cues, _ = validator.load_evidence_source_cues()
        self.assertEqual(cues.count((PORTAL, CATALOG[PORTAL])), 1)
        self.assertNotIn("invalid-agent", [agent for agent, _ in cues])
        self.assertEqual(" ".join(self.warnings_for("SURF cost")).count(PORTAL), 1)

    def test_unknown_status_still_warns_for_accounted_agent(self):
        self.results["agent_workflow_plan"] = {"evidence_sources": [
            {"agent": PORTAL, "status": "unknown", "reason": "Synthetic invalid decision"},
        ]}
        warnings = " ".join(self.warnings_for("SURF cost"))
        self.assertIn("unknown status", warnings)
        self.assertIn(PORTAL, warnings)

    def test_unavailable_discovery_is_best_effort(self):
        with mock.patch("agent_search._discover_roots", side_effect=OSError("unavailable")):
            self.assertEqual(validator.load_evidence_source_cues(), ([], {}))
            self.assertEqual(self.warnings_for("SURF cost"), [])


if __name__ == "__main__":
    unittest.main()
