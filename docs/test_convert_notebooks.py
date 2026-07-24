import json
import tempfile
import unittest
from pathlib import Path

from convert_notebooks import convert_all_notebooks


def write_notebook(path: Path, title: str, documentation_metadata=None) -> None:
    notebook = {
        "cells": [
            {
                "cell_type": "markdown",
                "metadata": {},
                "source": [f"# {title}\\n"],
            }
        ],
        "metadata": {
            "language_info": {"name": "python"},
            "neqsim_docs": documentation_metadata or {},
        },
        "nbformat": 4,
        "nbformat_minor": 5,
    }
    path.write_text(json.dumps(notebook), encoding="utf-8")


class ConvertNotebooksTest(unittest.TestCase):
    def test_converter_uses_curated_page_metadata(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            examples_dir = Path(temp_dir)
            notebook_path = examples_dir / "Curated.ipynb"
            markdown_path = examples_dir / "Curated.md"
            write_notebook(
                notebook_path,
                "Notebook title",
                {
                    "title": "Curated tutorial",
                    "description": "Curated engineering description",
                    "show_generated_title": False,
                },
            )

            convert_all_notebooks(examples_dir)

            generated_content = markdown_path.read_text(encoding="utf-8")
            self.assertIn('title: "Curated tutorial"', generated_content)
            self.assertIn(
                'description: "Curated engineering description"',
                generated_content,
            )
            self.assertEqual(generated_content.count("# Curated tutorial"), 0)
            self.assertEqual(generated_content.count("# Notebook title"), 1)

    def test_converter_keeps_default_metadata_behavior(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            examples_dir = Path(temp_dir)
            notebook_path = examples_dir / "Generated.ipynb"
            markdown_path = examples_dir / "Generated.md"
            write_notebook(notebook_path, "Current notebook title")

            convert_all_notebooks(examples_dir)

            generated_content = markdown_path.read_text(encoding="utf-8")
            self.assertIn('title: "Generated"', generated_content)
            self.assertIn("# Generated", generated_content)
            self.assertIn("# Current notebook title", generated_content)


if __name__ == "__main__":
    unittest.main()
