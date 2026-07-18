"""Contract and committed-execution checks for the offshore engineering study."""

import ast
import json
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
NOTEBOOK = ROOT / "examples" / "notebooks" / "complete_offshore_process_engineering_study.ipynb"
GUIDE = ROOT / "docs" / "integration" / "complete-offshore-process-engineering-study.md"


class CompleteOffshoreEngineeringStudyTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.notebook = json.loads(NOTEBOOK.read_text(encoding="utf-8"))
        cls.code_cells = [
            cell for cell in cls.notebook["cells"] if cell.get("cell_type") == "code"
        ]
        cls.source = "\n".join("".join(cell.get("source", [])) for cell in cls.notebook["cells"])
        cls.guide = GUIDE.read_text(encoding="utf-8")

    def test_committed_notebook_is_syntactically_valid_and_fully_executed(self):
        self.assertEqual(16, len(self.code_cells))
        for index, cell in enumerate(self.code_cells, start=1):
            with self.subTest(code_cell=index):
                ast.parse("".join(cell.get("source", [])), filename=f"code-cell-{index}")
                self.assertIsNotNone(cell.get("execution_count"))

        cells_with_outputs = sum(bool(cell.get("outputs")) for cell in self.code_cells)
        self.assertGreaterEqual(cells_with_outputs, 10)

    def test_notebook_covers_the_complete_process_topology(self):
        required_tags = (
            "20-VA-01", "20-VA-02", "20-VA-03", "23-VG-01", "23-VG-02",
            "23-VG-03", "24-VG-01", "25-VG-01", "23-KA-01", "23-KA-02",
            "23-KA-03", "27-KA-01", "21-PA-01", "EXPORT-GAS-LINE",
            "EXPORT-OIL-LINE", "fuel gas", "LP oil recycle",
        )
        for tag in required_tags:
            with self.subTest(tag=tag):
                self.assertIn(tag, self.source)

    def test_notebook_exercises_every_engineering_workstream(self):
        required_apis = (
            "ProcessToEngineeringSimulator", "EquipmentDesignCalculations$Separator",
            "EquipmentDesignCalculations$Compressor", "EquipmentDesignCalculations$Pump",
            "EquipmentDesignCalculations$HeatExchanger", "PipingNetworkDesignCalculation",
            "ValveInstrumentDesignCalculations$Valve", "ValveInstrumentDesignCalculations$Instrument",
            "SafetyScenarioEngineCalculation", "MaterialsMechanicalDesignCalculations$MaterialSelection",
            "MaterialsMechanicalDesignCalculations$PreliminaryMechanical",
            "EngineeringDeliverableCompiler", "EngineeringProductionReadinessAssessment",
        )
        for api in required_apis:
            with self.subTest(api=api):
                self.assertIn(api, self.source)

    def test_results_visuals_and_governance_are_explicit(self):
        self.assertGreaterEqual(self.source.count("plt.subplots"), 3)
        self.assertGreaterEqual(self.source.count("**Figure discussion"), 3)
        self.assertIn("Gas export rate [kmol/h]", self.source)
        self.assertIn("Total rotating power [kW]", self.source)
        self.assertIn("fitnessForConstruction", self.source)
        self.assertIn("assert not bool(readiness_map.get('fitnessForConstruction'))", self.source)
        errors = [
            output for cell in self.notebook["cells"] for output in cell.get("outputs", [])
            if output.get("output_type") == "error"
        ]
        self.assertFalse(errors)

    def test_guide_contains_benchmark_and_accountability_boundary(self):
        for phrase in (
            "5,111.624 kmol/h", "2,753.353 kmol/h", "10,376.1 kW",
            "Seven isolated steady cases", "SCREENING-CREDIBILITY-ASSUMPTION-NOT-HAZOP-APPROVED",
            "fitnessForConstruction=false", "vendor guarantees", "HAZOP, LOPA and SRS",
        ):
            with self.subTest(phrase=phrase):
                self.assertIn(phrase, self.guide)


if __name__ == "__main__":
    unittest.main()
