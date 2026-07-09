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

sys.path.insert(0, os.path.dirname(__file__))

import agent_search  # noqa: E402


def _write(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


class HandleDerivationTest(unittest.TestCase):
    def test_neqsim_flat_agent_handle_strips_agent_suffix(self):
        p = Path("/x/.github/agents/capability.scout.agent.md")
        self.assertEqual(agent_search._handle_for_path(p), "capability.scout")

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
            recs = agent_search._load_agents(
                root / "nonexistent_repo",  # no neqsim .github/agents here
                extra=[root / "community", root / "enterprise"],
            )
            repos = sorted(r[4] for r in recs if r[0] == "asset-economics-agent")
            self.assertEqual(repos, ["community", "enterprise"])


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
