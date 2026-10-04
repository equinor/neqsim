"""Regression tests for the combined cross-repo skill-ref check in
devtools/verify_agent_skill_refs.py.

Builds a synthetic multi-repo workspace so the check can be exercised without the
real sibling repos: an agent that loads a local skill, a sibling-repo skill, and
a non-existent skill. Also checks that repository mentions are not confused
with skill references, while explicit loads and missing skills remain checked.
"""
import contextlib
import io
import os
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, os.path.dirname(__file__))

import verify_agent_skill_refs as v  # noqa: E402


def _write(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


class SkillReferenceExtractionTest(unittest.TestCase):
    REPOSITORIES = (
        "neqsim-community-agents",
        "neqsim-community-skills",
        "neqsim-enterprise-agents",
        "neqsim-enterprise-skills",
    )

    def _extract(self, text):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "AGENTS.md"
            _write(path, text)
            return v.extract_skill_refs_from_file(path)

    def test_repository_names_in_prose_are_not_skills(self):
        text = "\n".join(
            '| Edit skills/agents in `{0}` or "{0}". |'.format(name)
            for name in self.REPOSITORIES
        )
        self.assertEqual(self._extract(text), set())

    def test_real_and_missing_skill_names_are_preserved(self):
        self.assertEqual(
            self._extract(
                '`neqsim-process-modeling` "neqsim-missing-skill" '
                "skills/neqsim-java8/ Load neqsim-testing"
            ),
            {"neqsim-process-modeling", "neqsim-missing-skill",
             "neqsim-java8", "neqsim-testing"},
        )

    def test_similarly_named_skills_are_not_filtered(self):
        names = {"neqsim-custom-skills", "neqsim-custom-agents",
                 "neqsim-community-skills-helper"}
        self.assertEqual(self._extract(" ".join('`{}`'.format(n) for n in names)), names)

    def test_explicit_skill_paths_are_always_checked(self):
        for name in self.REPOSITORIES:
            with self.subTest(name=name):
                self.assertEqual(
                    self._extract("`.github/skills/{}/SKILL.md`".format(name)), {name})

    def test_explicit_loads_are_always_checked(self):
        for quote in ("", "`", '"'):
            with self.subTest(quote=quote):
                self.assertEqual(
                    self._extract("Load {0}neqsim-community-skills{0}".format(quote)),
                    {"neqsim-community-skills"},
                )

    def _check(self, instruction):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            _write(root / "AGENTS.md", instruction)
            _write(
                root / ".github/skills/neqsim-testing/SKILL.md",
                '---\nname: neqsim-testing\ndescription: "USE WHEN: testing."\n---\n',
            )
            output = io.StringIO()
            with patch.object(v, "find_repo_root", return_value=root), \
                    patch.object(v, "_load_discovery_modules", return_value=(None, None)), \
                    patch.object(sys, "argv", ["verify_agent_skill_refs.py"]), \
                    contextlib.redirect_stdout(output):
                return v.main(), output.getvalue()

    def test_repository_mentions_pass_in_standalone_checkout(self):
        instruction = "`neqsim-testing`\n" + "\n".join(
            "Edit `{}`.".format(name) for name in self.REPOSITORIES)
        code, output = self._check(instruction)
        self.assertEqual(code, 0, output)
        self.assertIn("Result: PASS (0 warnings)", output)

    def test_missing_skill_still_fails_alongside_repository_mentions(self):
        code, output = self._check(
            '`neqsim-testing` `neqsim-community-skills` `neqsim-missing-skill`')
        self.assertEqual(code, 1, output)
        self.assertIn("BROKEN REF: Skill 'neqsim-missing-skill'", output)
        self.assertIn("Result: FAIL (1 errors, 0 warnings)", output)


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
