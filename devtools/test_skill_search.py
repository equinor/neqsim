"""Regression tests for devtools/skill_search.py sibling-repo skill indexing.

Locks in the cross-repo behavior added so skill_search stays symmetric with
agent_search: sibling *-skills repos are indexed, and dedup is by front-matter
``name`` (not folder name) because the neqsim ``.github/skills`` mirror renames
folders with a neqsim-/enterprise- prefix while sibling repos keep them plain.
"""
import os
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, os.path.dirname(__file__))

import skill_search  # noqa: E402


def _write(path: Path, name: str, desc: str) -> None:
    path.mkdir(parents=True, exist_ok=True)
    (path / "SKILL.md").write_text(
        "---\nname: {}\ndescription: \"{}\"\n---\n\n# Body\n".format(name, desc),
        encoding="utf-8",
    )


class LoadSkillsTest(unittest.TestCase):
    def _build_workspace(self, tmp: Path) -> Path:
        """Create <ws>/neqsim/.github/skills + a sibling skills repo."""
        skills_root = tmp / "neqsim" / ".github" / "skills"
        # neqsim mirror uses a prefixed folder name.
        _write(skills_root / "neqsim-hydrate-margin-check",
               "neqsim-hydrate-margin-check", "hydrate margin")
        # sibling community repo: unprefixed folder, SAME front-matter name,
        # plus a sibling-only enterprise skill.
        sib = tmp / "neqsim-community-skills" / "skills"
        _write(sib / "flow-assurance" / "hydrate-margin-check",
               "neqsim-hydrate-margin-check", "hydrate margin")
        ent = tmp / "neqsim-enterprise-skills" / "skills"
        _write(ent / "integration" / "enterprise-pepr-actions",
               "enterprise-pepr-actions", "read PEPR actions")
        return skills_root

    def test_sibling_only_skill_indexed_and_no_duplicate(self):
        with tempfile.TemporaryDirectory() as tmp:
            skills_root = self._build_workspace(Path(tmp))
            skills = skill_search._load_skills(skills_root)
            names = [s[0] for s in skills]
            # sibling-only enterprise skill is discoverable
            self.assertIn("enterprise-pepr-actions", names)
            # the mirrored skill appears exactly once (dedup by name)
            self.assertEqual(names.count("neqsim-hydrate-margin-check"), 1)
            # and the kept copy is the primary neqsim .github/skills one
            kept = next(s for s in skills if s[0] == "neqsim-hydrate-margin-check")
            self.assertIn(".github", kept[2])

    def test_missing_skills_root_does_not_crash(self):
        with tempfile.TemporaryDirectory() as tmp:
            # No neqsim/.github/skills, no siblings — must return [] not raise.
            fake = Path(tmp) / "neqsim" / ".github" / "skills"
            self.assertEqual(skill_search._load_skills(fake), [])


if __name__ == "__main__":
    unittest.main()
