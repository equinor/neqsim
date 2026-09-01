"""Hermetic documentation contract for the mercury-removal notebook and guide."""

import json
import re
import unittest
from pathlib import Path


DOCS = Path(__file__).resolve().parent
NOTEBOOK = DOCS / "examples" / "MercuryRemoval_LNG_Pretreatment.ipynb"
GUIDE = DOCS / "examples" / "MercuryRemoval_LNG_Pretreatment.md"


def source_text(cell):
    source = cell.get("source", "")
    return "".join(source) if isinstance(source, list) else source


def output_text(output):
    if output.get("output_type") == "stream":
        text = output.get("text", "")
        return "".join(text) if isinstance(text, list) else text
    data = output.get("data", {})
    text = data.get("text/plain", "")
    return "".join(text) if isinstance(text, list) else text


class MercuryRemovalNotebookDocumentationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.notebook = json.loads(NOTEBOOK.read_text(encoding="utf-8"))
        cls.guide = GUIDE.read_text(encoding="utf-8")
        cls.cells = cls.notebook["cells"]
        cls.code_cells = [cell for cell in cls.cells if cell["cell_type"] == "code"]
        cls.markdown = "\n".join(
            source_text(cell) for cell in cls.cells if cell["cell_type"] == "markdown"
        )
        cls.code = "\n".join(source_text(cell) for cell in cls.code_cells)
        cls.outputs = "\n".join(
            output_text(output)
            for cell in cls.code_cells
            for output in cell.get("outputs", [])
        )

    def test_saved_execution_is_complete_and_error_free(self):
        self.assertEqual(17, len(self.code_cells))
        self.assertEqual(
            list(range(1, 18)),
            [cell.get("execution_count") for cell in self.code_cells],
        )
        errors = [
            output
            for cell in self.code_cells
            for output in cell.get("outputs", [])
            if output.get("output_type") == "error"
        ]
        self.assertEqual([], errors)

    def test_saved_figures_have_accessible_metadata(self):
        figures = []
        for cell in self.code_cells:
            for output in cell.get("outputs", []):
                if "image/png" in output.get("data", {}):
                    figures.append((cell, output))
        self.assertEqual(5, len(figures))
        for cell, output in figures:
            alt = output.get("metadata", {}).get("image/png", {}).get("alt", "")
            self.assertTrue(alt.strip())
            self.assertEqual(cell.get("metadata", {}).get("alt"), alt)

    def test_colab_setup_and_runtime_versions_are_visible(self):
        self.assertIn('%pip install -q "neqsim==3.17.0"', self.code)
        self.assertIn('find_spec("neqsim")', self.code)
        self.assertIn("Python: 3.12.13", self.outputs)
        self.assertIn("Java: 17.0.19", self.outputs)
        self.assertIn("NeqSim: 3.17.0", self.outputs)

    def test_normal_volume_basis_and_saved_mercury_result_are_consistent(self):
        self.assertIn("0 °C and 1.01325 bar", self.markdown)
        self.assertIn("44.615 mol/Nm³", self.markdown)
        self.assertIn("Mercury mole fraction: 2.010050e-08", self.outputs)
        self.assertIn("179.89 µg/Nm³", self.outputs)

    def test_model_outputs_and_verification_are_saved(self):
        expected = [
            "Removal efficiency: 99.87 %",
            "Capacity-based lifetime: 2.71 years",
            "Replacement utilisation input: 0.50",
            "PASS: steady removal is bounded",
            "PASS: pressure drop is finite and non-negative",
            "PASS: transient time is strictly increasing",
            "PASS: transient utilisation is non-decreasing",
            "PASS: degradation scenarios do not increase removal",
            "PASS: lifetime increases with replacement utilisation",
            "PASS: lifetime increases with configured capacity",
            "PASS: normal-basis mercury conversion is reproducible",
        ]
        for text in expected:
            with self.subTest(text=text):
                self.assertIn(text, self.outputs)

    def test_engineering_boundaries_and_traceable_sources_are_present(self):
        required = [
            "educational screening workflow",
            "not a vendor guarantee",
            "not a named field or LNG train",
            "not condition-monitoring estimates",
            "ASME Section VIII compliance",
            "Unindexed nominal-USD screening",
            "https://matthey.com/products-and-markets/chemicals/mercury-removal-absorbents",
            "https://doi.org/10.1021/ie990758v",
            "MercuryRemovalBed.java",
        ]
        for text in required:
            with self.subTest(text=text):
                self.assertIn(text, self.markdown + self.code)

    def test_stale_or_overstated_claims_are_absent(self):
        combined = self.markdown + self.code + self.outputs + self.guide
        stale = [
            "1.005e-09",
            "9/10 parameters",
            "matches ASME",
            "Published Case Studies",
            "typical SE Asian",
            "Australian NWS",
            "0.01 µg/Nm³ for LNG product",
            "TLV-TWA",
        ]
        for text in stale:
            with self.subTest(text=text):
                self.assertNotIn(text, combined)
        self.assertEqual(1, self.markdown.count("Part 10:"))
        self.assertEqual(1, self.markdown.count("Part 11:"))

    def test_markdown_math_delimiters_are_balanced_and_colab_safe(self):
        self.assertEqual(0, self.markdown.count("$$") % 2)
        self.assertNotRegex(self.markdown, r"\\[\[\]()]")
        self.assertNotRegex(self.markdown, r"\\\\(?:mathrm|text|frac|rightarrow)")

    def test_guide_front_matter_navigation_and_heading_are_clean(self):
        self.assertTrue(self.guide.startswith("---\nlayout: default\n"))
        self.assertIn('title: "Mercury Removal in LNG Pre-Treatment"', self.guide)
        self.assertIn("parent: Examples", self.guide)
        self.assertIn("open it in Google Colab", self.guide)
        body = self.guide.split("\n---\n", 1)[1]
        prose_only = re.sub(r"```.*?```", "", body, flags=re.DOTALL)
        self.assertNotRegex(prose_only, r"(?m)^# ")
        self.assertEqual(1, self.guide.count("## Purpose and engineering boundary"))
        self.assertIn("PASS: normal-basis mercury conversion is reproducible", self.guide)


if __name__ == "__main__":
    unittest.main()
