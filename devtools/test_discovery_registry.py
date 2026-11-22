"""Tests for devtools/discovery_registry.py: one registry behind agent and skill search."""
import os
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

sys.path.insert(0, os.path.dirname(__file__))

import agent_search  # noqa: E402
import discovery_registry as registry  # noqa: E402
import skill_search  # noqa: E402


def _write(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


def _skill(path: Path, name: str, desc: str = "does a thing", version: str = "") -> None:
    extra = "version: {}\n".format(version) if version else ""
    _write(path / "SKILL.md", "---\nname: {}\ndescription: \"{}\"\n{}---\n".format(name, desc, extra))


def _env(root: Path, installed: Path = None, plugins: Path = None):
    return mock.patch.dict(os.environ, {
        "NEQSIM_AGENTS_HOME": str(root / "no_agents"),
        "NEQSIM_SKILLS_HOME": str(installed or root / "no_skills"),
        "NEQSIM_AGENT_PLUGINS_HOME": str(plugins or root / "no_plugins"),
    })


def _plugin(plugins: Path, name: str, version: str = "1.2.3") -> Path:
    pdir = plugins / "github.com" / "equinor" / "repo" / name
    _write(pdir / "plugin.json", '{{"name": "{}", "version": "{}"}}'.format(name, version))
    return pdir


class PluginSkillDiscoveryTest(unittest.TestCase):
    def test_plugin_only_skill_is_found_with_source_and_version(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            plugins = root / "plugins"
            pdir = _plugin(plugins, "neqsim-enterprise", "1.0.25")
            _skill(pdir / "skills" / "enterprise-pepr-actions", "enterprise-pepr-actions")
            with _env(root, plugins=plugins):
                entries = [e for e, _ in registry.skill_entries(root / "neqsim" / ".github" / "skills")]
                tuples = skill_search._load_skills(root / "neqsim" / ".github" / "skills")
            self.assertEqual([(e.id, e.source, e.version) for e in entries],
                             [("enterprise-pepr-actions", "plugin:neqsim-enterprise", "1.0.25")])
            self.assertEqual([t[0] for t in tuples], ["enterprise-pepr-actions"])

    def test_unrelated_skills_folder_is_not_a_plugin(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            plugins = root / "plugins"
            _skill(plugins / "a" / "b" / "c" / "d" / "skills" / "stray", "stray")
            with _env(root, plugins=plugins):
                self.assertEqual(registry.skill_entries(root / "neqsim" / ".github" / "skills"), [])

    def test_installed_skills_are_found_and_primary_wins(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            skills_root = root / "neqsim" / ".github" / "skills"
            _skill(skills_root / "neqsim-a", "neqsim-a")
            installed = root / "installed"
            _skill(installed / "neqsim-a", "neqsim-a")
            _skill(installed / "enterprise-b", "enterprise-b")
            with _env(root, installed=installed):
                entries = {e.id: e for e, _ in registry.skill_entries(skills_root)}
            self.assertEqual(entries["neqsim-a"].source, "neqsim")
            self.assertEqual(len(entries["neqsim-a"].other_locations), 1)
            self.assertEqual(entries["enterprise-b"].source, "installed")


class AgentAvailabilityTest(unittest.TestCase):
    def test_agent_skill_resolved_only_through_plugin(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            plugins = root / "plugins"
            pdir = _plugin(plugins, "neqsim-enterprise")
            _write(pdir / "com.github.copilot" / "agents" / "demo-agent.agent.md",
                   "---\nname: demo-agent\ndescription: \"Demo.\"\nrequired_skills:\n"
                   "- enterprise-here\n- enterprise-gone\n---\n")
            _skill(pdir / "skills" / "enterprise-here", "enterprise-here")
            with _env(root, plugins=plugins):
                agents = registry.agent_entries(root / "nonexistent_repo")
            agent = next(a for a in agents if a.id == "demo-agent")
            self.assertEqual(agent.source, "plugin:neqsim-enterprise")
            self.assertEqual(agent.version, "1.2.3")
            self.assertEqual(agent.missing, ["enterprise-gone"])
            self.assertFalse(agent.available)

    def test_search_payload_reports_missing_skills(self):
        rec = ("demo", "demo", "/x/AGENT.md", ["have", "lack"], "repo", "demo")
        payload = agent_search._results_to_payload("q", [(1.0, rec)], {"have"})
        self.assertEqual(payload["results"][0]["missing_skills"], ["lack"])
        self.assertNotIn("missing_skills",
                         agent_search._results_to_payload("q", [(1.0, rec)])["results"][0])


if __name__ == "__main__":
    unittest.main()
