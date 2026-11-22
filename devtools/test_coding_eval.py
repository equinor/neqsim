"""Check grading on working, broken, out-of-scope and incomplete candidates."""
import copy
import json
from pathlib import Path
import tempfile
import unittest

import coding_eval as evaluator


class CodingEvaluationTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.base = Path(self.temp.name) / "base"
        self.candidate = Path(self.temp.name) / "candidate"
        self.changed = ["devtools/verify_skills_agents.py"]
        canonical = (evaluator.EVALUATOR / "agent_frontmatter.py").read_bytes()
        self.working = (evaluator.EVALUATOR / "verify_skills_agents.py").read_text(encoding="utf-8")
        for root in (self.base, self.candidate):
            (root / "devtools").mkdir(parents=True)
            (root / "devtools/agent_frontmatter.py").write_bytes(canonical)
            (root / self.changed[0]).write_text(self.working, encoding="utf-8")
        self.case = copy.deepcopy(json.loads(evaluator.CATALOG.read_text())["cases"][0])
        self.case["base_files_sha256"] = {p: evaluator.digest(self.base / p)
                                           for p in self.case["base_files_sha256"]}

    def grade(self, **kwargs):
        return evaluator.grade(self.case, self.candidate, self.changed,
                               self.case["base_revision"], self.base, **kwargs)

    def test_working_candidate_passes_with_fingerprints(self):
        result = self.grade()
        self.assertEqual("passed", result["status"], result)
        self.assertEqual(64, len(result["acceptance_sha256"]))
        self.assertEqual(0, result["exit_code"])

    def test_broken_behavior_fails_even_if_candidate_supplies_fake_tests(self):
        (self.candidate / self.changed[0]).write_text('def parse_front_matter(text):\n    return {}\n')
        (self.candidate / "devtools/frontmatter_contract.py").write_text('raise SystemExit(0)\n')
        result = self.grade()
        self.assertEqual("failed", result["status"])
        self.assertNotEqual(0, result["exit_code"])

    def test_wrong_base_rejected(self):
        result = evaluator.grade(self.case, self.candidate, self.changed, "wrong", self.base)
        self.assertIn("pinned case", result["errors"][0])

    def test_parser_only_stub_cannot_delete_the_verifier(self):
        (self.candidate / self.changed[0]).write_text(
            'import agent_frontmatter as af\n'
            'def parse_front_matter(text):\n'
            '    return {k:v for k,v in af.parse_frontmatter(text).items()\n'
            '            if isinstance(v,str) and v}\n')
        self.assertEqual("failed", self.grade()["status"])

    def test_early_zero_exit_is_not_a_completed_acceptance_suite(self):
        (self.candidate / self.changed[0]).write_text("import os\nos._exit(0)\n")
        result = self.grade()
        self.assertEqual("failed", result["status"])
        self.assertIn("did not complete", result["errors"][0])

    def test_out_of_scope_and_empty_candidates_rejected(self):
        for changed in ([], ["pom.xml"]):
            result = evaluator.grade(self.case, self.candidate, changed,
                                     self.case["base_revision"], self.base)
            self.assertEqual("failed", result["status"])
            self.assertTrue(result["errors"])

    def test_base_input_tampering_and_unreported_dependency_change_rejected(self):
        (self.candidate / "devtools/agent_frontmatter.py").write_text("# altered\n")
        self.assertIn("Unreported change", self.grade()["errors"][0])
        (self.base / self.changed[0]).write_text("# altered base\n")
        self.assertIn("Pinned base file mismatch", self.grade()["errors"][0])

    def test_timeout_is_failure(self):
        delayed = self.working.replace("from __future__ import annotations",
                                       "from __future__ import annotations\nimport time\ntime.sleep(10)", 1)
        (self.candidate / self.changed[0]).write_text(delayed)
        result = self.grade(timeout=1)
        self.assertEqual("failed", result["status"])
        self.assertTrue(result["errors"])

    def test_direct_shared_parser_import_is_accepted(self):
        direct = self.working.replace("import agent_frontmatter as af",
                                      "from agent_frontmatter import parse_frontmatter\nimport agent_frontmatter as af")
        direct = direct.replace("af.parse_frontmatter(text).items()", "parse_frontmatter(text).items()")
        (self.candidate / self.changed[0]).write_text(direct)
        self.assertEqual("passed", self.grade()["status"])


if __name__ == "__main__":
    unittest.main()
