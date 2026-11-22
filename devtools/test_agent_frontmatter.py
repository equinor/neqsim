"""Tests for the shared agent frontmatter reader."""
from __future__ import annotations

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import agent_frontmatter as af  # noqa: E402

CORE = (
    "---\n"
    "name: scout neqsim capabilities\n"
    'description: "Does a thing."\n'
    "required_skills:\n"
    "- neqsim-capability-map\n"
    "- neqsim-api-patterns\n"
    'argument-hint: "x"\n'
    "---\n"
    "Loaded skills: neqsim-capability-map, neqsim-api-patterns, neqsim-extra\n\n"
    "Body.\n"
)

BULLETS = (
    "---\nname: lng\ndescription: d\n---\n"
    "## Loaded skills\n\n"
    "- `neqsim-lng-liquefaction` \u2014 liquefaction cycles, refrigerant calibration\n"
    "- `neqsim-api-patterns` \u2014 fluid creation, flash\n\n"
    "## Next\n"
)


class ParseFrontmatterTest(unittest.TestCase):
    def test_scalars_and_lists(self):
        fm = af.parse_frontmatter(CORE)
        self.assertEqual(fm["name"], "scout neqsim capabilities")
        self.assertEqual(fm["description"], "Does a thing.")
        self.assertEqual(fm["required_skills"], ["neqsim-capability-map", "neqsim-api-patterns"])
        self.assertEqual(fm["argument-hint"], "x")

    def test_inline_list_and_empty_list(self):
        fm = af.parse_frontmatter("---\nname: a\nrequired_skills: [x, y]\n---\n")
        self.assertEqual(fm["required_skills"], ["x", "y"])
        fm = af.parse_frontmatter("---\nname: a\nrequired_skills: []\n---\n")
        self.assertEqual(fm["required_skills"], [])

    def test_no_frontmatter(self):
        self.assertEqual(af.parse_frontmatter("plain text"), {})


class ExtractSkillsTest(unittest.TestCase):
    def test_frontmatter_first_then_body_appended_deduped(self):
        self.assertEqual(
            af.extract_required_skills(CORE),
            ["neqsim-capability-map", "neqsim-api-patterns", "neqsim-extra"],
        )

    def test_bullet_block_takes_first_token_only(self):
        self.assertEqual(
            af.extract_required_skills(BULLETS),
            ["neqsim-lng-liquefaction", "neqsim-api-patterns"],
        )


class AgentIdTest(unittest.TestCase):
    def test_flat_and_nested_layouts(self):
        self.assertEqual(af.agent_id_for_path(Path("/x/.github/agents/capability-scout.agent.md")),
                         "capability-scout")
        self.assertEqual(af.agent_id_for_path(Path("/x/agents/hydrate-agent/AGENT.md")),
                         "hydrate-agent")
        # legacy dotted names normalise to hyphens
        self.assertEqual(af.agent_id_for_path(Path("/x/solve.task.agent.md")), "solve-task")


class SetFrontmatterListTest(unittest.TestCase):
    def test_insert_after_description_and_replace(self):
        text = "---\nname: a\ndescription: d\nargument-hint: h\n---\nbody\n"
        out = af.set_frontmatter_list(text, "required_skills", ["s1", "s2"])
        self.assertIn("description: d\nrequired_skills:\n- s1\n- s2\nargument-hint: h", out)
        again = af.set_frontmatter_list(out, "required_skills", ["s3"])
        self.assertIn("required_skills:\n- s3\nargument-hint", again)
        self.assertNotIn("- s1", again)

    def test_empty_list_renders_inline(self):
        out = af.set_frontmatter_list("---\nname: a\n---\nb\n", "required_skills", [])
        self.assertIn("required_skills: []", out)
        self.assertEqual(af.parse_frontmatter(out)["required_skills"], [])

    def test_idempotent(self):
        text = "---\nname: a\ndescription: d\n---\nbody\n"
        once = af.set_frontmatter_list(text, "required_skills", ["s1"])
        self.assertEqual(once, af.set_frontmatter_list(once, "required_skills", ["s1"]))


if __name__ == "__main__":
    unittest.main()
