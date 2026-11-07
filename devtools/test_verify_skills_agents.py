"""Focused tests for the skill and agent verifier."""

import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import verify_skills_agents as verifier


class PythonRuntimeInstructionTests(unittest.TestCase):
    """Verify shared Python runtime conflict detection."""

    def test_rejects_bare_launcher_and_environment_creation(self) -> None:
        """Bare launch commands and per-agent environments are errors."""
        with tempfile.TemporaryDirectory() as temp_dir:
            agent_path = Path(temp_dir) / "example.agent.md"
            agent_path.write_text(
                "Run `python devtools/task.py`.\nCreate a virtual environment first.\n",
                encoding="utf-8",
            )
            errors = verifier.check_python_runtime_instructions(agent_path)
        self.assertEqual(2, len(errors))

    def test_accepts_shared_runtime_policy_and_python_prose(self) -> None:
        """Approved policy and non-command Python prose remain valid."""
        with tempfile.TemporaryDirectory() as temp_dir:
            agent_path = Path(temp_dir) / "example.agent.md"
            agent_path.write_text(
                "Use C:\\appl\\neqsim-venv\\Scripts\\python.exe or sys.executable.\n"
                "Never invoke bare python or activate a per-agent environment.\n"
                "No `pip install neqsim` is allowed for repository notebooks.\n"
                "The installed Python package provides the bridge.\n",
                encoding="utf-8",
            )
            errors = verifier.check_python_runtime_instructions(agent_path)
        self.assertEqual([], errors)


class ParseFrontMatterTests(unittest.TestCase):
    def test_plain_scalars_ignore_comments_and_body(self):
        text = (
            "---\n"
            "# ignored: comment\n"
            "name: example-skill\n"
            "description: Route requests: keep # content\n"
            "---\n"
            "description: body text\n"
        )
        self.assertEqual(
            verifier.parse_front_matter(text),
            {
                "name": "example-skill",
                "description": "Route requests: keep # content",
            },
        )

    def test_missing_or_incomplete_frontmatter_returns_empty_dict(self):
        for text in ("", "name: example-skill\n", "---\nname: example-skill\n"):
            with self.subTest(text=text):
                self.assertEqual(verifier.parse_front_matter(text), {})

    def test_folded_and_literal_descriptions_keep_following_scalars(self):
        for marker in (">", "|"):
            with self.subTest(marker=marker):
                text = (
                    "---\n"
                    f"description: {marker}\n"
                    "  First line with 'quotes'.\n"
                    "\n"
                    '  Second line with "quotes".\n'
                    "name: example-skill\n"
                    "---\n"
                )
                self.assertEqual(
                    verifier.parse_front_matter(text),
                    {
                        "description": 'First line with \'quotes\'. Second line with "quotes".',
                        "name": "example-skill",
                    },
                )

    def test_only_matching_outer_quotes_are_removed(self):
        cases = (
            ('"\'quoted\'"', "'quoted'"),
            ("'\"quoted\"'", '"quoted"'),
            ('""quoted""', '"quoted"'),
            ("'''quoted'''", "''quoted''"),
            ("Don't trim apostrophes", "Don't trim apostrophes"),
            ("trailing'", "trailing'"),
            ('"leading', '"leading'),
        )
        for source, expected in cases:
            with self.subTest(source=source):
                text = f"---\ndescription: {source}\n---\n"
                self.assertEqual(
                    verifier.parse_front_matter(text), {"description": expected}
                )

    def test_lists_stay_out_of_scalar_dictionary(self):
        for declaration in (
            "required_skills: [alpha-skill, beta-skill]",
            "required_skills:\n  - alpha-skill\n  - beta-skill",
            "required_skills: []",
        ):
            with self.subTest(declaration=declaration):
                text = f"---\nname: example-skill\n{declaration}\n---\n"
                self.assertEqual(
                    verifier.parse_front_matter(text), {"name": "example-skill"}
                )
        self.assertEqual(
            verifier.parse_front_matter('---\ndescription: "[quoted content]"\n---\n'),
            {"description": "[quoted content]"},
        )

    def test_empty_values_remain_omitted(self):
        self.assertEqual(
            verifier.parse_front_matter(
                '---\nname: example-skill\nempty: ""\nblank: \'\'\nblock: >\n---\n'
            ),
            {"name": "example-skill"},
        )


class CheckAgentsTests(unittest.TestCase):
    def test_dependency_lists_are_validated_separately(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            skills = root / "skills"
            agents = root / "agents"
            agents.mkdir()
            for name in ("alpha-skill", "beta-skill"):
                (skills / name).mkdir(parents=True)
            agent = agents / "example-agent.agent.md"
            for declaration, body, stale in (
                ("required_skills: [alpha-skill]", "Loaded skills: alpha-skill", False),
                ("required_skills:\n  - alpha-skill", "Loaded skills: alpha-skill", False),
                ("required_skills: []", "", False),
                ("required_skills: [alpha-skill]", "Loaded skills: beta-skill", True),
            ):
                with self.subTest(declaration=declaration, body=body):
                    agent.write_text(
                        "---\n"
                        "name: Example agent\n"
                        "description: Check scalar metadata and required skill declarations.\n"
                        f"{declaration}\n"
                        "---\n"
                        f"{body}\n",
                        encoding="utf-8",
                    )
                    with patch.object(verifier, "SKILLS_DIR", skills), patch.object(
                        verifier, "AGENTS_DIR", agents
                    ):
                        errors, warnings = verifier.check_agents()
                    self.assertEqual(warnings, [])
                    if stale:
                        self.assertEqual(len(errors), 1)
                        self.assertIn("required_skills is stale", errors[0])
                    else:
                        self.assertEqual(errors, [])



if __name__ == "__main__":
    unittest.main()