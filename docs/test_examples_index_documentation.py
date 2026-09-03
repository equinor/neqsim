import json
import re
import unittest
from pathlib import Path
from urllib.parse import quote
from urllib.parse import unquote


ROOT = Path(__file__).resolve().parents[1]
EXAMPLES_DIR = ROOT / "docs" / "examples"
INDEX_PATH = EXAMPLES_DIR / "index.md"


def stored_status(notebook_path):
    notebook = json.loads(notebook_path.read_text(encoding="utf-8"))
    code_cells = [
        cell for cell in notebook.get("cells", []) if cell["cell_type"] == "code"
    ]
    execution_counts = [cell.get("execution_count") for cell in code_cells]
    outputs = [
        output
        for cell in code_cells
        for output in cell.get("outputs", [])
    ]

    has_error = any(output.get("output_type") == "error" for output in outputs)
    has_stderr = any(output.get("name") == "stderr" for output in outputs)
    if has_error or has_stderr:
        raise AssertionError(
            f"{notebook_path.name} retains an exception or stderr output"
        )

    executed_count = sum(count is not None for count in execution_counts)
    if executed_count == len(code_cells):
        return "Executed"
    if executed_count:
        return "Partial"
    return "Source only"


class ExamplesIndexDocumentationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.index = INDEX_PATH.read_text(encoding="utf-8")
        cls.local_section = cls.index.split(
            "## Local notebook catalog", 1
        )[1].split("## Standalone Java source examples", 1)[0]
        cls.java_section = cls.index.split(
            "## Standalone Java source examples", 1
        )[1].split("## Other Tutorials", 1)[0]
        cls.workflow_section = cls.index.split(
            "## Maintained workflow notebooks", 1
        )[1].split("## Local notebook catalog", 1)[0]

    def test_every_local_notebook_has_truthful_status_and_destinations(self):
        notebooks = sorted(EXAMPLES_DIR.glob("*.ipynb"))
        catalog_targets = {
            unquote(target)
            for target in re.findall(
                r"\[Markdown\]\(([^)]+\.md)\)",
                self.local_section,
            )
        }
        expected_targets = {
            notebook.with_suffix(".md").name for notebook in notebooks
        }
        self.assertEqual(catalog_targets, expected_targets)

        for notebook in notebooks:
            markdown_name = notebook.with_suffix(".md").name
            quoted_markdown = quote(markdown_name)
            quoted_notebook = quote(notebook.name)
            status = stored_status(notebook)

            self.assertTrue((EXAMPLES_DIR / markdown_name).is_file())
            row = next(
                line
                for line in self.local_section.splitlines()
                if f"[Markdown]({quoted_markdown})" in line
            )
            self.assertIn(f"| **{status}** |", row)
            self.assertIn(
                "https://nbviewer.org/github/equinor/neqsim/blob/"
                f"master/docs/examples/{quoted_notebook}",
                row,
            )
            self.assertIn(
                "https://colab.research.google.com/github/equinor/"
                f"neqsim/blob/master/docs/examples/{quoted_notebook}",
                row,
            )
            self.assertEqual(
                self.local_section.count(f"[Markdown]({quoted_markdown})"),
                1,
            )

    def test_maintained_workflow_targets_resolve_in_repository(self):
        targets = re.findall(
            r"https://(?:nbviewer\.org/github/|colab\.research\.google\.com/"
            r"github/)equinor/neqsim/blob/master/([^\s)]+\.ipynb)",
            self.workflow_section,
        )
        self.assertGreaterEqual(len(targets), 16)
        for target in targets:
            self.assertTrue((ROOT / unquote(target)).is_file(), target)

    def test_every_standalone_java_source_is_disclosed_once(self):
        java_files = sorted(EXAMPLES_DIR.glob("*.java"))
        self.assertIn("outside Maven's compiled source tree", self.java_section)
        self.assertIn("legacy console output", self.java_section)
        self.assertIn(
            "build verification only, not runtime or engineering-result",
            self.java_section,
        )
        self.assertNotIn(
            "https://github.com/equinor/neqsim/blob/master/docs/examples/",
            self.java_section,
        )

        for java_file in java_files:
            link = f"[{java_file.stem}]({java_file.name})"
            self.assertEqual(self.java_section.count(link), 1)
            row = next(
                line
                for line in self.java_section.splitlines()
                if link in line
            )
            self.assertIn("| **Build-verified source** |", row)

    def test_catalog_does_not_overpromise_colab_execution(self):
        self.assertNotIn("no installation needed", self.index.lower())
        self.assertIn(
            "dependency installation and stored execution status vary",
            self.index,
        )
        self.assertIn(
            "Stored status describes the committed notebook only",
            self.index,
        )


if __name__ == "__main__":
    unittest.main()
