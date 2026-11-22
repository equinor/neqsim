"""Protect the design-case execution-policy and acceptance documentation."""

from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]
PAGE = ROOT / "docs/engineering/design-cases-and-envelopes.md"
REGRESSION = ROOT / (
    "src/test/java/neqsim/process/engineering/designcase/EngineeringCaseRunnerTest.java"
)


class DesignCaseAcceptanceDocumentationTest(unittest.TestCase):
    """Keep software acceptance distinct from engineering approval and coverage."""

    def test_front_matter_and_heading_contract(self):
        page = PAGE.read_text(encoding="utf-8")
        self.assertTrue(page.startswith('---\ntitle: "'))
        self.assertIn('\ndescription: "', page)
        body = page.split("---", 2)[2]
        self.assertIsNone(re.search(r"^# ", body, re.MULTILINE))

    def test_execution_policy_boundaries_are_explicit(self):
        page = PAGE.read_text(encoding="utf-8")
        for required in (
            "requireConvergence(false)",
            "ProcessSystem.solved()",
            "DesignCaseResult.isConverged()",
            "disabled required case",
            "required=false",
            "approval metadata",
            "not insertion order",
            "lexicographic case ID",
        ):
            with self.subTest(required=required):
                self.assertIn(required, page)
        self.assertNotIn("Process converged and all declared metrics were evaluated", page)

    def test_exact_example_and_boundary_regressions_remain_connected(self):
        page = PAGE.read_text(encoding="utf-8")
        self.assertEqual(1, len(re.findall(r"^```java$", page, re.MULTILINE)))
        self.assertEqual(2, len(re.findall(r"^```", page, re.MULTILINE)))
        regression = REGRESSION.read_text(encoding="utf-8")
        for method in (
            "documentationExampleCompilesAndRunsWithAssertionsEnabled",
            "convergenceBypassIsPolicyAcceptanceNotPhysicalConvergence",
            "disabledRequiredCaseDoesNotBlockAnOtherwiseAcceptedEnvelope",
            "optionalCaseFailuresStillMakeTheEnvelopeIncomplete",
            "governingTiesUsePriorityThenIdNotInsertionOrApproval",
        ):
            with self.subTest(method=method):
                self.assertIn(method, regression)
        self.assertIn("EngineeringCaseRunnerTest.java", page)

    def test_relative_links_are_explicit_existing_source_files(self):
        page = PAGE.read_text(encoding="utf-8")
        for target in re.findall(r"\[[^\]]*\]\(([^)\s]+)\)", page):
            if "://" in target or target.startswith("#"):
                continue
            with self.subTest(target=target):
                path = target.split("#", 1)[0]
                self.assertIn(Path(path).suffix, (".md", ".java"))
                self.assertTrue((PAGE.parent / path).is_file())


if __name__ == "__main__":
    unittest.main()
