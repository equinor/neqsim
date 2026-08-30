import json
import re
import tempfile
import unittest
from pathlib import Path
from urllib.parse import quote

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
                "source": [f"# {title}\n"],
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
            self.assertNotIn("# Notebook title", generated_content)

    def test_converter_can_strip_notebook_h1_from_generated_page(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            examples_dir = Path(temp_dir)
            notebook_path = examples_dir / "Curated.ipynb"
            markdown_path = examples_dir / "Curated.md"
            write_notebook(
                notebook_path,
                "Notebook title",
                {
                    "title": "Curated page title",
                    "show_generated_title": False,
                    "strip_first_h1": True,
                },
            )

            convert_all_notebooks(examples_dir)

            generated_content = markdown_path.read_text(encoding="utf-8")
            self.assertIn('title: "Curated page title"', generated_content)
            self.assertNotIn("# Curated page title", generated_content)
            self.assertNotIn("# Notebook title", generated_content)

    def test_converter_preserves_legacy_mercury_guide_contract(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            examples_dir = Path(temp_dir)
            notebook_path = (
                examples_dir / "MercuryRemoval_LNG_Pretreatment.ipynb"
            )
            markdown_path = (
                examples_dir / "MercuryRemoval_LNG_Pretreatment.md"
            )
            write_notebook(
                notebook_path,
                "Mercury Removal in LNG Pre-Treatment — NeqSim Tutorial",
            )

            convert_all_notebooks(examples_dir)

            generated_content = markdown_path.read_text(encoding="utf-8")
            self.assertIn(
                'title: "Mercury Removal in LNG Pre-Treatment"',
                generated_content,
            )
            self.assertIn(
                "Executable NeqSim mercury-removal screening with transient",
                generated_content,
            )
            self.assertIn("open it in Google Colab", generated_content)
            body = generated_content.split("\n---\n", 1)[1]
            self.assertNotRegex(body, r"(?m)^# ")

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
            self.assertIn(
                f"title: {json.dumps(title, ensure_ascii=False)}",
                generated_content,
            )
            self.assertIn(
                "field-life depletion, well and flowline hydraulics",
                generated_content,
            )
            self.assertNotIn(f"# {title}", generated_content)
            self.assertNotIn("# process equipmentutl", generated_content)
            self.assertIn(
                "docs/examples/process%20equipmentutl.ipynb",
                generated_content,
            )
            self.assertNotIn(
                "docs/examples/process equipmentutl.ipynb",
                generated_content,
            )

    def test_converter_uses_notebook_title_as_default_page_metadata(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            examples_dir = Path(temp_dir)
            notebook_path = examples_dir / "Generated.ipynb"
            markdown_path = examples_dir / "Generated.md"
            write_notebook(notebook_path, "Current notebook title")

            convert_all_notebooks(examples_dir)

            generated_content = markdown_path.read_text(encoding="utf-8")
            self.assertIn(
                'title: "Current notebook title"',
                generated_content,
            )
            self.assertIn(
                "Notebook for Current notebook title, including NeqSim "
                "Python examples and workflow context.",
                generated_content,
            )
            body = generated_content.split("\n---\n", 1)[1]
            self.assertNotRegex(body, r"(?m)^# ")
            self.assertIn("open in Google Colab", generated_content)

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
            self.assertIn(
                'title: "Notebook title"',
                generated_content,
            )
            self.assertIn(
                "Notebook for Notebook title, including NeqSim Python "
                "examples and workflow context.",
                generated_content,
            )

    def test_committed_generated_pages_use_front_matter_title_only(self):
        docs_dir = Path(__file__).resolve().parent
        examples_dir = docs_dir / "examples"

        generated_pages = []
        for notebook_path in sorted(examples_dir.glob("*.ipynb")):
            markdown_path = notebook_path.with_suffix(".md")
            if markdown_path.exists():
                generated_pages.append(markdown_path)

        self.assertGreater(len(generated_pages), 20)
        for markdown_path in generated_pages:
            with self.subTest(path=markdown_path.name):
                generated_content = markdown_path.read_text(encoding="utf-8")
                self.assertTrue(generated_content.startswith("---\n"))
                self.assertNotIn(
                    'description: "Jupyter notebook tutorial for NeqSim"',
                    generated_content,
                )
                body = generated_content.split("\n---\n", 1)[1]
                body_without_fences = re.sub(
                    r"[\x60]{3}.*?[\x60]{3}",
                    "",
                    body,
                    flags=re.DOTALL,
                )
                self.assertNotRegex(body_without_fences, r"(?m)^# ")

    def test_index_preserves_curated_notebooks(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            index_path = Path(temp_dir) / "index.md"

            create_examples_index(temp_dir)

            generated_content = index_path.read_text(encoding="utf-8")
            for entry in CURATED_NOTEBOOKS:
                self.assertIn(entry["title"], generated_content)
                self.assertIn(entry["path"], generated_content)
            self.assertNotIn("\n# NeqSim Examples\n", generated_content)

    def test_index_preserves_energy_network_collection_and_source_guide(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            index_path = Path(temp_dir) / "index.md"

            create_examples_index(temp_dir)

            generated_content = index_path.read_text(encoding="utf-8")
            self.assertIn(
                "../integration/complete-offshore-process-engineering-study.md",
                generated_content,
            )
            self.assertEqual(
                generated_content.count(
                    "examples/notebooks/energy_networks/"
                ),
                8,
            )
            for notebook_number in ("01_", "02_", "03_", "04_"):
                self.assertIn(
                    "energy_networks/" + notebook_number,
                    generated_content,
                )

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

    def test_index_preserves_validation_contract_sections(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            examples_dir = Path(temp_dir)
            notebook_path = examples_dir / "Curated.ipynb"
            java_path = examples_dir / "Example.java"
            write_notebook(notebook_path, "Notebook title")
            java_path.write_text("class Example {}\n", encoding="utf-8")

            create_examples_index(examples_dir)

            generated_content = (
                examples_dir / "index.md"
            ).read_text(encoding="utf-8")
            self.assertIn("## Maintained workflow notebooks", generated_content)
            self.assertIn("## Local notebook catalog", generated_content)
            self.assertIn(
                "## Standalone Java source examples",
                generated_content,
            )
            self.assertIn(
                "| **Executed** | **Notebook title** | "
                "Notebook for Notebook title, including NeqSim Python "
                "examples and workflow context. |",
                generated_content,
            )
            self.assertNotIn("See notebook for details", generated_content)
            self.assertIn(
                "[Example](Example.java) | **Source only** |",
                generated_content,
            )
            self.assertNotIn("no installation needed", generated_content.lower())
            self.assertIn(
                "dependency installation and stored execution status vary",
                generated_content,
            )

    def test_committed_index_matches_companion_page_metadata(self):
        docs_dir = Path(__file__).resolve().parent
        examples_dir = docs_dir / "examples"
        index_content = (examples_dir / "index.md").read_text(
            encoding="utf-8",
        )
        catalog_rows = [
            line
            for line in index_content.splitlines()
            if line.startswith("| **") and "[Markdown](" in line
        ]

        companion_pages = sorted(examples_dir.glob("*.ipynb"))
        self.assertGreater(len(companion_pages), 20)
        self.assertEqual(len(catalog_rows), len(companion_pages))
        self.assertNotIn("See notebook for details", index_content)

        for notebook_path in companion_pages:
            markdown_path = notebook_path.with_suffix(".md")
            with self.subTest(path=markdown_path.name):
                page_content = markdown_path.read_text(encoding="utf-8")
                title_match = re.search(
                    r"(?m)^title:\s+(.+)$",
                    page_content,
                )
                description_match = re.search(
                    r"(?m)^description:\s+(.+)$",
                    page_content,
                )
                self.assertIsNotNone(title_match)
                self.assertIsNotNone(description_match)

                title = json.loads(title_match.group(1))
                description = json.loads(description_match.group(1))
                encoded_markdown_name = quote(markdown_path.name, safe="")
                link = f"[Markdown]({encoded_markdown_name})"
                row = next(
                    (line for line in catalog_rows if link in line),
                    None,
                )
                self.assertIsNotNone(row)
                normalized_title = " ".join(str(title).split()).replace(
                    "|",
                    r"\|",
                )
                normalized_description = (
                    " ".join(str(description).split()).replace("|", r"\|")
                )
                self.assertIn(f"**{normalized_title}**", row)
                self.assertIn(normalized_description, row)


if __name__ == "__main__":
    unittest.main()
