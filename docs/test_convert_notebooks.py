import json
import tempfile
import unittest
from pathlib import Path

from convert_notebooks import (
    CURATED_NOTEBOOKS,
    convert_all_notebooks,
    create_examples_index,
)


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
            curated_title = 'Curated "quoted" \\ tutorial – Å'
            curated_description = "Curated engineering description\nwith detail"
            write_notebook(
                notebook_path,
                "Notebook title",
                {
                    "title": curated_title,
                    "description": curated_description,
                    "show_generated_title": False,
                },
            )

            convert_all_notebooks(examples_dir)

            generated_content = markdown_path.read_text(encoding="utf-8")
            self.assertIn(
                f"title: {json.dumps(curated_title, ensure_ascii=False)}",
                generated_content,
            )
            self.assertIn(
                f"description: {json.dumps(curated_description, ensure_ascii=False)}",
                generated_content,
            )
            self.assertNotIn(f"# {curated_title}", generated_content)
            self.assertEqual(generated_content.count("# Notebook title"), 1)

    def test_converter_repairs_legacy_process_equipment_guide(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            examples_dir = Path(temp_dir)
            notebook_path = examples_dir / "process equipmentutl.ipynb"
            markdown_path = examples_dir / "process equipmentutl.md"
            title = (
                "Reservoir-to-Market Optimisation with NeqSim Process Equipment"
            )
            write_notebook(notebook_path, title)

            convert_all_notebooks(examples_dir)

            generated_content = markdown_path.read_text(encoding="utf-8")
            self.assertIn(f"title: {json.dumps(title)}", generated_content)
            self.assertIn(
                "field-life depletion, well and flowline hydraulics",
                generated_content,
            )
            self.assertEqual(generated_content.count(f"# {title}"), 1)
            self.assertNotIn("# process equipmentutl", generated_content)
            self.assertIn(
                "docs/examples/process%20equipmentutl.ipynb",
                generated_content,
            )
            self.assertNotIn(
                "docs/examples/process equipmentutl.ipynb",
                generated_content,
            )

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

    def test_converter_ignores_non_mapping_documentation_metadata(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            examples_dir = Path(temp_dir)
            notebook_path = examples_dir / "InvalidMetadata.ipynb"
            markdown_path = examples_dir / "InvalidMetadata.md"
            write_notebook(
                notebook_path,
                "Notebook title",
                "invalid metadata",
            )

            convert_all_notebooks(examples_dir)

            generated_content = markdown_path.read_text(encoding="utf-8")
            self.assertIn('title: "InvalidMetadata"', generated_content)
            self.assertIn(
                'description: "Jupyter notebook tutorial for NeqSim"',
                generated_content,
            )


    def test_index_preserves_curated_notebooks(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            index_path = Path(temp_dir) / "index.md"

            create_examples_index(temp_dir)

            generated_content = index_path.read_text(encoding="utf-8")
            for entry in CURATED_NOTEBOOKS:
                self.assertIn(entry["title"], generated_content)
                self.assertIn(entry["path"], generated_content)
            self.assertNotIn("\n# NeqSim Examples\n", generated_content)

    def test_index_uses_metadata_and_encodes_space_in_links(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            examples_dir = Path(temp_dir)
            notebook_path = examples_dir / "process equipmentutl.ipynb"
            write_notebook(
                notebook_path,
                "Notebook title",
                {
                    "title": "Process equipment utilities",
                    "description": "Equipment | utilities\nworkflow",
                },
            )

            create_examples_index(examples_dir)

            generated_content = (
                examples_dir / "index.md"
            ).read_text(encoding="utf-8")
            self.assertIn(
                "**Process equipment utilities**",
                generated_content,
            )
            self.assertIn(
                r"Equipment \| utilities workflow",
                generated_content,
            )
            self.assertIn(
                "[Markdown](process%20equipmentutl.md)",
                generated_content,
            )
            self.assertIn(
                "docs/examples/process%20equipmentutl.ipynb",
                generated_content,
            )
            self.assertNotIn(
                "(process equipmentutl.md)",
                generated_content,
            )


if __name__ == "__main__":
    unittest.main()
