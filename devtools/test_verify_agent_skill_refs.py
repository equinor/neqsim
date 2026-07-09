"""Regression tests for the combined cross-repo skill-ref check in
devtools/verify_agent_skill_refs.py.

Builds a synthetic multi-repo workspace so the check can be exercised without the
real sibling repos: an agent that loads a local skill, a sibling-repo skill, and
a non-existent skill. Asserts the sibling skill is counted as cross-repo (not
broken) and the missing skill is flagged as a broken reference.
"""
import os
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, os.path.dirname(__file__))

import verify_agent_skill_refs as v  # noqa: E402


def _write(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


class CombinedSkillRefTest(unittest.TestCase):
    def _build(self, tmp: Path) -> Path:
        neqsim = tmp / "neqsim"
        # A neqsim agent that loads three skills.
        _write(
            neqsim / ".github" / "agents" / "demo.agent.md",
            "---\nname: demo agent\ndescription: \"demo\"\n"
            "required_skills:\n- local-skill\n- sibling-skill\n- ghost-skill\n---\n",
        )
        # local skill lives in neqsim/.github/skills
        _write(
            neqsim / ".github" / "skills" / "local-skill" / "SKILL.md",
            "---\nname: local-skill\ndescription: \"x\"\n---\n",
        )
        # sibling skill lives only in a *-skills repo
        _write(
            tmp / "neqsim-community-skills" / "skills" / "cat" / "sibling-skill" / "SKILL.md",
            "---\nname: sibling-skill\ndescription: \"y\"\n---\n",
        )
        return neqsim

    def test_classifies_cross_repo_vs_broken(self):
        with tempfile.TemporaryDirectory() as tmp:
            neqsim = self._build(Path(tmp))
            errors, warnings, cross_repo = v.check_combined_skill_refs(
                neqsim, {"local-skill"})
            # sibling-skill is present in a sibling repo -> counted, not broken
            self.assertGreaterEqual(cross_repo, 1)
            # ghost-skill exists nowhere -> exactly one broken-ref warning
            broken = [w for w in warnings if "ghost-skill" in w]
            self.assertEqual(len(broken), 1)
            # local-skill must NOT be flagged as broken
            self.assertFalse(any("local-skill" in w for w in warnings))

    def test_missing_discovery_modules_is_safe(self):
        # If discovery modules cannot be imported the check degrades to empty.
        orig = v._load_discovery_modules
        try:
            v._load_discovery_modules = lambda: (None, None)
            errors, warnings, cross = v.check_combined_skill_refs(Path("."), set())
            self.assertEqual((errors, warnings, cross), ([], [], 0))
        finally:
            v._load_discovery_modules = orig


class UseWhenTriggerTest(unittest.TestCase):
    def _meta(self, tmp, desc):
        md = Path(tmp) / "s" / "SKILL.md"
        md.parent.mkdir(parents=True, exist_ok=True)
        md.write_text(
            "---\nname: s\ndescription: \"{}\"\n---\n".format(desc), encoding="utf-8")
        return v.extract_skill_metadata(md)

    def test_uppercase_use_when(self):
        with tempfile.TemporaryDirectory() as tmp:
            meta = self._meta(tmp, "Does x. USE WHEN: a task needs x.")
            self.assertIn("use_when", meta)

    def test_lowercase_use_when_is_recognised(self):
        # Many skills write "Use when:" (title case) — must be matched too.
        with tempfile.TemporaryDirectory() as tmp:
            meta = self._meta(tmp, "Does x. Use when: a task needs x.")
            self.assertIn("use_when", meta)

    def test_no_trigger_when_absent(self):
        with tempfile.TemporaryDirectory() as tmp:
            meta = self._meta(tmp, "A narrative description with no trigger phrase")
            self.assertNotIn("use_when", meta)


if __name__ == "__main__":
    unittest.main()
