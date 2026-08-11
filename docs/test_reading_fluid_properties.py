import json
import unittest
from pathlib import Path


EXAMPLES_DIR = Path(__file__).resolve().parent / "examples"
NOTEBOOK_PATH = EXAMPLES_DIR / "ReadingFluidProperties.ipynb"
MARKDOWN_PATH = EXAMPLES_DIR / "ReadingFluidProperties.md"


class ReadingFluidPropertiesNotebookTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.notebook = json.loads(NOTEBOOK_PATH.read_text(encoding="utf-8"))
        cls.code_cells = [
            cell
            for cell in cls.notebook["cells"]
            if cell["cell_type"] == "code"
        ]
        cls.source = "\n".join(
            "".join(cell.get("source", []))
            for cell in cls.notebook["cells"]
        )

    def test_notebook_is_cleanly_executed_with_retained_outputs(self):
        execution_counts = [cell["execution_count"] for cell in self.code_cells]
        self.assertEqual(
            execution_counts,
            list(range(1, len(self.code_cells) + 1)),
        )
        self.assertTrue(all(cell.get("outputs") for cell in self.code_cells))

        outputs = [
            output
            for cell in self.code_cells
            for output in cell.get("outputs", [])
        ]
        self.assertFalse(
            any(output.get("output_type") == "error" for output in outputs)
        )
        self.assertFalse(
            any(output.get("name") == "stderr" for output in outputs)
        )
        self.assertEqual(
            sum("image/png" in output.get("data", {}) for output in outputs),
            1,
        )
        image_output = next(
            output for output in outputs if "image/png" in output.get("data", {})
        )
        self.assertIn("alt", image_output["metadata"]["image/png"])

    def test_setup_and_unit_contracts_remain_explicit(self):
        first_code_source = "".join(self.code_cells[0]["source"])
        self.assertIn('find_spec("neqsim")', first_code_source)
        self.assertIn('"pip", "install", "--quiet", "neqsim"', first_code_source)

        self.assertIn('fluid.getPressure("Pa")', self.source)
        self.assertIn('fluid.getPressure("psia")', self.source)
        self.assertIn('fluid.getTemperature("F")', self.source)
        self.assertNotIn("activateSIUnits", self.source)
        self.assertNotIn("activateFieldUnits", self.source)
        self.assertNotIn("SI: P = 50", self.source)

    def test_documentation_metadata_and_generated_page_contract(self):
        metadata = self.notebook["metadata"]["neqsim_docs"]
        self.assertEqual(metadata["title"], "Reading Fluid Properties with Python")
        self.assertFalse(metadata["show_generated_title"])

        generated = MARKDOWN_PATH.read_text(encoding="utf-8")
        self.assertIn(
            'title: "Reading Fluid Properties with Python"',
            generated,
        )
        self.assertNotIn("\n# ReadingFluidProperties\n", generated)
        self.assertNotIn("\n# Reading Fluid Properties in NeqSim\n", generated)
        self.assertIn("../thermo/reading_fluid_properties.md", generated)


if __name__ == "__main__":
    unittest.main()
