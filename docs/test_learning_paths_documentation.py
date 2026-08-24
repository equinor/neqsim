import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
LEARNING_PATH = ROOT / "docs" / "tutorials" / "learning-paths.md"
EXAMPLES_INDEX = ROOT / "docs" / "examples" / "index.md"


class LearningPathsDocumentationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.page = LEARNING_PATH.read_text(encoding="utf-8")
        cls.catalog = EXAMPLES_INDEX.read_text(encoding="utf-8")

    def test_front_matter_title_is_not_duplicated_as_h1(self):
        body = self.page.split("---", 2)[2]
        self.assertNotRegex(body, r"(?m)^# ")

    def test_role_links_match_stable_heading_anchors(self):
        expected = {
            "pvt-engineer-path": "## PVT engineer path",
            "process-engineer-path": "## Process engineer path",
            "developer-path": "## Developer path",
        }
        for anchor, heading in expected.items():
            self.assertIn(f"](#" + anchor + ")", self.page)
            self.assertIn(heading, self.page)

    def test_internal_links_are_source_safe_and_resolve(self):
        targets = re.findall(r"\]\((\.\./[^)]+)\)", self.page)
        self.assertGreaterEqual(len(targets), 47)
        for target in targets:
            path_text = target.split("#", 1)[0]
            self.assertTrue(path_text.endswith(".md"), target)
            resolved = (LEARNING_PATH.parent / path_text).resolve()
            self.assertTrue(resolved.is_file(), target)

    def test_notebook_statuses_match_the_examples_catalog(self):
        expected = {
            "ReadingFluidProperties.ipynb": "Executed",
            "PVT_Simulation_and_Tuning.ipynb": "Source only",
            "NetworkSolverTutorial.ipynb": "Source only",
            "ProductionOptimizer_Tutorial.ipynb": "Source only",
        }
        for notebook, status in expected.items():
            catalog_row = next(
                line for line in self.catalog.splitlines() if notebook in line
            )
            page_line = next(
                line for line in self.page.splitlines() if notebook in line
            )
            self.assertIn(f"| **{status}** |", catalog_row)
            self.assertIn(status, page_line)

        self.assertIn(
            "Rerun either kind against the current `master` branch", self.page
        )
        self.assertNotIn("**Run**: [PVT Simulation", self.page)
        self.assertNotIn("**Run**: [Network Solver", self.page)
        self.assertNotIn("**Run**: [Production Optimizer", self.page)

    def test_model_selection_keeps_specialized_models_bounded(self):
        self.assertIn("consider `SystemPrDanesh`", self.page)
        self.assertIn("it is not a general multiphase-VLE default", self.page)
        self.assertNotIn("| Heavy oil | PR, CPA |", self.page)
        self.assertNotIn("| CO₂ systems | SRK-CPA, GERG-2008 |", self.page)


if __name__ == "__main__":
    unittest.main()
