"""Regression tests for devtools/sync_agent_relationships.py."""
import os
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, os.path.dirname(__file__))

import agent_frontmatter as af  # noqa: E402
import sync_agent_relationships as sar  # noqa: E402

AGENT_MD = "---\nname: demo-agent\ndescription: Demo.\nrequired_skills:\n- skill-a\n---\n\n# Purpose\n"
AGENT_YAML = (
    "name: demo-agent\nrequired_skills:\n- skill-a\n"
    "context_skills:\n- skill-b\n- skill-c\ncoordinated_agents:\n- other-agent\n"
)


def _make_repo(root: Path) -> Path:
    agent_dir = root / "agents" / "demo-agent"
    agent_dir.mkdir(parents=True)
    (agent_dir / "AGENT.md").write_text(AGENT_MD, encoding="utf-8")
    (agent_dir / "agent.yaml").write_text(AGENT_YAML, encoding="utf-8")
    return root


class SyncAgentRelationshipsTest(unittest.TestCase):
    def test_check_flags_drift_and_apply_fixes_it(self):
        with tempfile.TemporaryDirectory() as tmp:
            repo = _make_repo(Path(tmp))
            self.assertEqual(1, sar.main(["--repo", str(repo), "--check"]))
            self.assertEqual(0, sar.main(["--repo", str(repo), "--apply"]))
            text = (repo / "agents" / "demo-agent" / "AGENT.md").read_text(encoding="utf-8")
            front = af.parse_frontmatter(text)
            self.assertEqual(["skill-b", "skill-c"], list(front["context_skills"]))
            self.assertEqual(["other-agent"], list(front["coordinated_agents"]))
            self.assertEqual(["skill-a"], af.extract_required_skills(text))
            self.assertTrue(text.endswith("# Purpose\n"))
            self.assertEqual(0, sar.main(["--repo", str(repo), "--check"]))

    def test_dry_run_does_not_write(self):
        with tempfile.TemporaryDirectory() as tmp:
            repo = _make_repo(Path(tmp))
            sar.main(["--repo", str(repo)])
            text = (repo / "agents" / "demo-agent" / "AGENT.md").read_text(encoding="utf-8")
            self.assertEqual(AGENT_MD, text)


if __name__ == "__main__":
    unittest.main()
