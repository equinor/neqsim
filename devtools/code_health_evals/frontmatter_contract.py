"""Trusted acceptance test; candidate location comes from the grading runner."""
import ast
import importlib.util
import json
import os
from pathlib import Path
import sys
import unittest
from unittest import mock


class FrontmatterContract(unittest.TestCase):
    """Check scalar behavior and use of the canonical implementation."""

    @classmethod
    def setUpClass(cls):
        cls.root = Path(os.environ["NEQSIM_EVAL_CANDIDATE"])
        sys.path.insert(0, str(cls.root / "devtools"))
        spec = importlib.util.spec_from_file_location(
            "candidate_verifier", cls.root / "devtools/verify_skills_agents.py")
        cls.module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(cls.module)

    def test_preserves_quoted_content(self):
        text = "---\nname: example\ndescription: \"Keep the ending 'quote'\"\n---\nbody"
        self.assertEqual("Keep the ending 'quote'", self.module.parse_front_matter(text)["description"])

    def test_scalar_metadata_and_folded_description(self):
        text = "---\nname: example\ndescription: >\n  First line\n  second line\nrequired_skills:\n- one\n- two\n---\n"
        self.assertEqual({"name": "example", "description": "First line second line"},
                         self.module.parse_front_matter(text))
        self.assertEqual({}, self.module.parse_front_matter("No frontmatter"))

    def test_uses_shared_parser(self):
        """Observe delegation without prescribing an import or call spelling."""
        with mock.patch("agent_frontmatter.parse_frontmatter",
                        return_value={"name": "delegated", "skills": ["one"], "empty": ""}) as shared:
            spec = importlib.util.spec_from_file_location(
                "candidate_probe", self.root / "devtools/verify_skills_agents.py")
            module = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(module)
            self.assertEqual({"name": "delegated"}, module.parse_front_matter("metadata probe"))
            shared.assert_called_once_with("metadata probe")

    def test_preserves_the_verifier_and_its_existing_checks(self):
        """A parser-only stub must not pass by deleting the surrounding program."""
        base = Path(os.environ["NEQSIM_EVAL_BASE"])
        previous = ast.parse((base / "devtools/verify_skills_agents.py").read_text(encoding="utf-8"))
        current = ast.parse((self.root / "devtools/verify_skills_agents.py").read_text(encoding="utf-8"))
        functions = lambda tree: {n.name: ast.dump(n, include_attributes=False) for n in tree.body
                                  if isinstance(n, (ast.FunctionDef, ast.ClassDef))
                                  and n.name != "parse_front_matter"}
        self.assertTrue(functions(previous), "Pinned baseline must contain the actual verifier")
        self.assertEqual(functions(previous), functions(current),
                         "This bounded parser refactor must preserve every other verifier check and CLI")
        self.assertEqual(self.module.af.FRONT_MATTER_RE.pattern, self.module.FRONT_MATTER_RE.pattern)


if __name__ == "__main__":
    suite = unittest.defaultTestLoader.loadTestsFromTestCase(FrontmatterContract)
    result = unittest.TextTestRunner().run(suite)
    completion = os.environ.get("NEQSIM_EVAL_COMPLETION")
    if completion:
        Path(completion).write_text(json.dumps({"tests_run": result.testsRun,
                                               "successful": result.wasSuccessful(),
                                               "skipped": len(result.skipped)}), encoding="utf-8")
    sys.exit(0 if result.wasSuccessful() else 1)
