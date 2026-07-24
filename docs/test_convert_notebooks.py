import json
import tempfile
import unittest
from pathlib import Path

from convert_notebooks import convert_all_notebooks, should_preserve_markdown


def write_notebook(path: Path, title: str) -> None:
    notebook = {
        "cells": [
            {
                "cell_type": "markdown",
                "metadata": {},
                "source": [f"# {title}\\n"],
            }
        ],
        "metadata": {"language_info": {"name": "python"}},
        "nbformat": 4,
        "nbformat_minor": 5,
    }
    path.write_text(json.dumps(notebook), encoding="utf-8")


class ConvertNotebooksTest(unittest.TestCase):
    def test_converter_preserves_explicitly_curated_markdown(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            examples_dir = Path(temp_dir)
            notebook_path = examples_dir / "Curated.ipynb"
            markdown_path = examples_dir / "Curated.md"
            curated_content = """---
title: "Curated tutorial"
notebook_conversion: preserve
---

Curated engineering content.
"""
            write_notebook(notebook_path, "Notebook title")
            markdown_path.write_text(curated_content, encoding="utf-8")

            self.assertTrue(should_preserve_markdown(markdown_path))

            convert_all_notebooks(examples_dir)

            self.assertEqual(
                markdown_path.read_text(encoding="utf-8"),
                curated_content,
            )

    def test_converter_still_updates_unmarked_markdown(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            examples_dir = Path(temp_dir)
            notebook_path = examples_dir / "Generated.ipynb"
            markdown_path = examples_dir / "Generated.md"
            write_notebook(notebook_path, "Current notebook title")
            markdown_path.write_text("stale content\\n", encoding="utf-8")

            self.assertFalse(should_preserve_markdown(markdown_path))

            convert_all_notebooks(examples_dir)

            generated_content = markdown_path.read_text(encoding="utf-8")
            self.assertIn('title: "Generated"', generated_content)
            self.assertIn("# Current notebook title", generated_content)
            self.assertNotIn("stale content", generated_content)


if __name__ == "__main__":
    unittest.main()
