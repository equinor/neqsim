import argparse
import tempfile
import unittest
from pathlib import Path
from unittest import mock

import paperlab_install


class TestPaperLabInstall(unittest.TestCase):
    """Tests for PaperLab VS Code export helpers."""

    def test_iter_paperlab_assets_find_definitions(self):
        """PaperLab source folders expose agents and skills."""
        agent_names = {name for name, _ in paperlab_install.iter_paperlab_agents()}
        skill_names = {name for name, _ in paperlab_install.iter_paperlab_skills()}

        self.assertIn("paper-opportunity-miner", agent_names)
        self.assertIn("hypothesis-benchmark-compiler", agent_names)
        self.assertIn("paperlab_publication_opportunity_mining", skill_names)
        self.assertIn("paperlab_hypothesis_to_benchmark_matrix", skill_names)

    def test_cmd_install_exports_to_explicit_vscode_dirs(self):
        """The install command exports PaperLab agents and skills to VS Code dirs."""
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            agents_dir = root / "prompts"
            skills_dir = root / "skills"
            args = argparse.Namespace(
                vscode=True,
                agents_only=False,
                skills_only=False,
                vscode_scope="user",
                vscode_agents_dir=str(agents_dir),
                vscode_skills_dir=str(skills_dir),
                dry_run=False,
            )

            with mock.patch("builtins.print"):
                paperlab_install.cmd_install(args)

            self.assertTrue((agents_dir / "paper-opportunity-miner.agent.md").exists())
            self.assertTrue(
                (skills_dir / "paperlab_publication_opportunity_mining" / "SKILL.md").exists())


if __name__ == "__main__":
    unittest.main()