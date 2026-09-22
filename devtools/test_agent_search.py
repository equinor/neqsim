"""Regression tests for devtools/agent_search.py semantic agent discovery.

These tests are hermetic — they build synthetic agent trees in a temp dir so
they do not depend on the sibling *-agents repos being checked out. They lock in
the two bugs fixed in the agent-discovery work:

  1. Cross-repo dedup must key on (repo, name) so a community agent and its
     enterprise policy-gated counterpart with the SAME name are both indexed.
  2. The invocation handle (@handle) must be surfaced — for neqsim it is the
     ``<handle>.agent.md`` stem, for community/enterprise it is the
     ``agents/<handle>/AGENT.md`` parent directory name.
"""
import os
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

sys.path.insert(0, os.path.dirname(__file__))

import agent_search  # noqa: E402


def _write(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


def _isolated_fallback_roots(root: Path, installed: Path = None, plugins: Path = None):
    """Point the installed-agents and plugin roots at temp dirs.

    Both default to real locations under ``Path.home()``, so without this a test
    silently indexes the developer's own catalog - which is how the synthetic
    ``asset-economics-agent`` fixture collided with the genuine installed one.
    """
    return mock.patch.dict(os.environ, {
        "NEQSIM_AGENTS_HOME": str(installed or root / "no_installed_agents_here"),
        "NEQSIM_AGENT_PLUGINS_HOME": str(plugins or root / "no_plugins_here"),
    })


class HandleDerivationTest(unittest.TestCase):
    def test_neqsim_flat_agent_handle_strips_agent_suffix(self):
        p = Path("/x/.github/agents/capability-scout.agent.md")
        self.assertEqual(agent_search._handle_for_path(p), "capability-scout")

    def test_nested_agent_handle_is_parent_dir(self):
        p = Path("/x/agents/hydrate-margin-agent/AGENT.md")
        self.assertEqual(agent_search._handle_for_path(p), "hydrate-margin-agent")


class FrontMatterTest(unittest.TestCase):
    def test_parses_name_description_and_skills(self):
        text = (
            "---\n"
            "name: demo-agent\n"
            'description: "Does a demo thing."\n'
            "required_skills:\n"
            "- skill-a\n"
            "- skill-b\n"
            "version: 0.1.0\n"
            "---\n\n# Body\n"
        )
        fm = agent_search._parse_front_matter(text)
        self.assertEqual(fm["name"], "demo-agent")
        self.assertEqual(fm["description"], "Does a demo thing.")
        self.assertEqual(fm["required_skills"], ["skill-a", "skill-b"])

    def test_no_front_matter_returns_none(self):
        self.assertIsNone(agent_search._parse_front_matter("# no front matter\n"))


class LoadedSkillsBodyTest(unittest.TestCase):
    def test_inline_loaded_skills_line(self):
        text = "body\nLoaded skills: neqsim-a, neqsim-b\nmore\n"
        self.assertEqual(
            agent_search._extract_loaded_skills_body(text), ["neqsim-a", "neqsim-b"])

    def test_skills_to_load_heading(self):
        text = "## Skills to Load\n- neqsim-a\n- `neqsim-b`\n\n## Next\n"
        self.assertEqual(
            agent_search._extract_loaded_skills_body(text), ["neqsim-a", "neqsim-b"])

    def test_loaded_skills_heading_variant(self):
        # The '## Loaded skills' heading is a third convention some agents use.
        text = "## Loaded skills\n- neqsim-consequence-analysis\n- paperlab_multi_x\n"
        self.assertEqual(
            agent_search._extract_loaded_skills_body(text),
            ["neqsim-consequence-analysis", "paperlab_multi_x"])


class CrossRepoDedupTest(unittest.TestCase):
    def test_same_name_in_two_repos_both_indexed(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            body = (
                "---\nname: asset-economics-agent\n"
                'description: "NPV screening."\n---\n'
            )
            # Two sibling repos, same agent name.
            _write(root / "community" / "agents" / "asset-economics-agent" / "AGENT.md", body)
            _write(root / "enterprise" / "agents" / "asset-economics-agent" / "AGENT.md", body)
            # Redirect the default "installed" root (normally ~/.neqsim/agents) to an
            # empty temp dir so this test stays hermetic even on a machine that has
            # a real agent installed under that same name (e.g. asset-economics-agent
            # is a genuine community agent many dev machines have installed).
            with _isolated_fallback_roots(root):
                recs = agent_search._load_agents(
                    root / "nonexistent_repo",  # no neqsim .github/agents here
                    extra=[root / "community", root / "enterprise"],
                )
            repos = sorted(r[4] for r in recs if r[0] == "asset-economics-agent")
            self.assertEqual(repos, ["community", "enterprise"])


class InstalledAgentsRootTest(unittest.TestCase):
    """Locks in the fix for agents installed only via `neqsim agent install`
    (~/.neqsim/agents), which previously required the *-agents repos to be
    cloned as siblings and were otherwise invisible to this search — see
    CHANGELOG_AGENT_NOTES.md."""

    def test_installed_only_agent_is_indexed_by_default(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            installed_root = root / "installed_agents"
            body = (
                "---\nname: olga-simulation-agent\n"
                'description: "Runs the OLGA transient multiphase flow simulator."\n'
                "required_skills:\n- neqsim-olga-multiphase-simulator\n---\n"
            )
            _write(installed_root / "olga-simulation-agent" / "AGENT.md", body)
            with _isolated_fallback_roots(root, installed=installed_root):
                self.assertEqual(agent_search._installed_agents_root(), installed_root)
                recs = agent_search._load_agents(root / "nonexistent_repo")
            names = [r[0] for r in recs]
            self.assertIn("olga-simulation-agent", names)

    def test_default_installed_root_without_override(self):
        with mock.patch.dict(os.environ, {}, clear=False):
            os.environ.pop("NEQSIM_AGENTS_HOME", None)
            self.assertEqual(
                agent_search._installed_agents_root(), Path.home() / ".neqsim" / "agents"
            )

    def test_installed_copy_of_a_cloned_agent_is_not_listed_twice(self):
        # A maintainer has the sibling clone *and* runs `neqsim agent install --all`;
        # without the fallback rule the same agent burns two of the --top N slots.
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            body = (
                "---\nname: olga-simulation-agent\n"
                'description: "Runs the OLGA transient multiphase flow simulator."\n---\n'
            )
            _write(root / "community" / "agents" / "olga-simulation-agent" / "AGENT.md", body)
            installed_root = root / "installed_agents"
            _write(installed_root / "olga-simulation-agent" / "AGENT.md", body)
            with _isolated_fallback_roots(root, installed=installed_root):
                recs = agent_search._load_agents(
                    root / "nonexistent_repo", extra=[root / "community"])
            hits = [r for r in recs if r[0] == "olga-simulation-agent"]
            self.assertEqual([r[4] for r in hits], ["community"])


class PluginAgentRootTest(unittest.TestCase):
    """A marketplace plugin install is the only agent source on many machines:
    ~/.neqsim/agents stays empty and the agents ship inside the plugin package."""

    @staticmethod
    def _plugin_agent(plugins_root, plugin, name, description):
        _write(plugins_root / "github.com" / "equinor" / "neqsim-copilot-plugin" / plugin /
               "com.github.copilot" / "agents" / (name + ".agent.md"),
               "---\nname: {n}\ndescription: \"{d}\"\n---\n".format(n=name, d=description))

    def test_plugin_only_agent_is_indexed_and_labelled_per_plugin(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            plugins = root / "agent-plugins"
            self._plugin_agent(plugins, "neqsim-community", "olga-simulation-agent",
                               "Runs the OLGA transient multiphase flow simulator.")
            with _isolated_fallback_roots(root, plugins=plugins):
                recs = agent_search._load_agents(root / "nonexistent_repo")
            hits = [r for r in recs if r[0] == "olga-simulation-agent"]
            self.assertEqual([r[4] for r in hits], ["plugin:neqsim-community"])

    def test_community_and_enterprise_plugin_copies_both_survive(self):
        # Same handle in two plugins is the deliberate community/enterprise pair.
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            plugins = root / "agent-plugins"
            for plugin in ("neqsim-community", "neqsim-enterprise"):
                self._plugin_agent(plugins, plugin, "asset-economics-agent", "NPV screening.")
            with _isolated_fallback_roots(root, plugins=plugins):
                recs = agent_search._load_agents(root / "nonexistent_repo")
            repos = sorted(r[4] for r in recs if r[0] == "asset-economics-agent")
            self.assertEqual(repos, ["plugin:neqsim-community", "plugin:neqsim-enterprise"])

    def test_plugin_copy_of_a_cloned_agent_is_not_listed_twice(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            plugins = root / "agent-plugins"
            self._plugin_agent(plugins, "neqsim-community", "olga-simulation-agent", "OLGA.")
            _write(root / "community" / "agents" / "olga-simulation-agent" / "AGENT.md",
                   "---\nname: olga-simulation-agent\ndescription: \"OLGA.\"\n---\n")
            with _isolated_fallback_roots(root, plugins=plugins):
                recs = agent_search._load_agents(
                    root / "nonexistent_repo", extra=[root / "community"])
            hits = [r for r in recs if r[0] == "olga-simulation-agent"]
            self.assertEqual([r[4] for r in hits], ["community"])

    def test_user_data_cache_copy_of_a_plugin_is_not_a_second_root(self):
        # VS Code also caches each plugin under %APPDATA%/Code/agentPlugins in a
        # version-hash folder; keyed on the folder name that copy looked like a
        # separate plugin and listed every one of its agents again.
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            plugins = root / "agent-plugins"
            self._plugin_agent(plugins, "neqsim-community", "olga-simulation-agent", "OLGA.")
            _write(plugins / "github.com" / "equinor" / "neqsim-copilot-plugin" /
                   "neqsim-community" / "plugin.json", '{"name": "neqsim-community"}\n')
            cache = plugins / "file-neqsim-community" / "1a0c50d1a81"
            _write(cache / "plugin.json", '{"name": "neqsim-community"}\n')
            _write(cache / "com.github.copilot" / "agents" / "olga-simulation-agent.agent.md",
                   "---\nname: olga-simulation-agent\ndescription: \"OLGA.\"\n---\n")
            with _isolated_fallback_roots(root, plugins=plugins):
                labels = [label for _, label, _, _ in agent_search._plugin_agent_roots()]
                recs = agent_search._load_agents(root / "nonexistent_repo")
            self.assertEqual(labels, ["plugin:neqsim-community"])
            self.assertEqual(len([r for r in recs if r[0] == "olga-simulation-agent"]), 1)


class PayloadTest(unittest.TestCase):
    def test_payload_includes_handle_and_skills(self):
        rec = ("demo-agent", "demo-agent demo hay", "/x/agents/demo-agent/AGENT.md",
               ["skill-a"], "community", "demo-agent")
        payload = agent_search._results_to_payload("q", [(0.5, rec)])
        entry = payload["results"][0]
        self.assertEqual(entry["handle"], "demo-agent")
        self.assertEqual(entry["repo"], "community")
        self.assertEqual(entry["loads_skills"], ["skill-a"])


class LiveCoverageTest(unittest.TestCase):
    """Best-effort: if the neqsim repo agents are present, index is non-empty."""

    def test_neqsim_agents_indexed(self):
        repo_root = Path(__file__).resolve().parent.parent
        if not (repo_root / ".github" / "agents").is_dir():
            self.skipTest("neqsim .github/agents not present")
        recs = agent_search._load_agents(repo_root)
        self.assertGreater(len(recs), 0)
        # Every record must carry a non-empty handle.
        self.assertTrue(all(r[5] for r in recs))


if __name__ == "__main__":
    unittest.main()
