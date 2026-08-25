"""Focused tests for the skill and agent verifier."""

import tempfile
import unittest
from pathlib import Path

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


if __name__ == "__main__":
    unittest.main()