"""Contract checks for the process-to-engineering documentation bundle."""

import json
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent


class EngineeringDocumentationTest(unittest.TestCase):
    def test_workflow_guide_covers_coordinated_compiler_outputs(self):
        guide = (ROOT / "docs" / "integration" / "process-to-engineering-workflow.md").read_text(encoding="utf-8")
        required_artifacts = (
            "engineering-model.json",
            "engineering-connectivity.json",
            "engineering-calculation-dag.json",
            "engineering-design-case-matrix.json",
            "engineering-discipline-package.json",
            "engineering-approval-ledger.json",
            "engineering-dexpi-roundtrip-report.json",
            "engineering-automation-plan.json",
            "engineering-validation-report.json",
        )
        for artifact in required_artifacts:
            with self.subTest(artifact=artifact):
                self.assertIn(artifact, guide)
        self.assertIn("EngineeringDeliverableCompiler", guide)
        self.assertIn("not fit for construction", guide.lower())

    def test_pid_notebook_is_executed_and_figures_are_committed(self):
        notebook_path = ROOT / "examples" / "notebooks" / "dexpi_pid_visualization.ipynb"
        notebook = json.loads(notebook_path.read_text(encoding="utf-8"))
        code_cells = [cell for cell in notebook["cells"] if cell["cell_type"] == "code"]
        self.assertGreaterEqual(len(code_cells), 5)
        for index, cell in enumerate(code_cells, start=1):
            with self.subTest(code_cell=index):
                self.assertIsNotNone(cell.get("execution_count"))
                self.assertFalse(any(output.get("output_type") == "error" for output in cell.get("outputs", [])))

        figures = ROOT / "examples" / "notebooks" / "figures"
        for filename in ("dexpi_engineering_pid_roundtrip.png", "dexpi_engineering_pid_roundtrip.svg"):
            with self.subTest(figure=filename):
                figure = figures / filename
                self.assertTrue(figure.is_file())
                self.assertGreater(figure.stat().st_size, 1000)
