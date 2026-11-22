"""Execute the Python blocks in PRACTICAL_EXAMPLES.md with NeqSim installed.

Run with ``python -m unittest devtools.test_practical_optimization_examples -v``.
Set NEQSIM_TEST_CLASSPATH to compiled workspace classes plus dependencies to test
the current Java source; otherwise the installed neqsim package supplies the JAR.
"""

import contextlib
import importlib
import io
import os
from pathlib import Path
import re
import runpy
import sys
import tempfile
import unittest


class PracticalOptimizationExamplesTest(unittest.TestCase):
    """Protect copy/paste execution and the physical meaning of the outputs."""

    @classmethod
    def setUpClass(cls):
        import matplotlib

        matplotlib.use("Agg")
        classpath = os.environ.get("NEQSIM_TEST_CLASSPATH")
        if classpath:
            import jpype

            if jpype.isJVMStarted():
                raise RuntimeError("Start a fresh Python process to select the test classpath")
            jpype.startJVM(classpath=classpath.split(os.pathsep))

        source = Path(__file__).resolve().parents[1] / "docs/process/optimization/PRACTICAL_EXAMPLES.md"
        blocks = re.findall(r"```python\n(.*?)```", source.read_text(encoding="utf-8"), re.S)
        if len(blocks) != 3:
            raise AssertionError("The test must execute every Python example on the page")
        cls.temp = tempfile.TemporaryDirectory()
        cls.directory = Path(cls.temp.name)
        cls.names = [
            "basic_process_optimization",
            "lift_curve_generation",
            "equipment_constraint_analysis",
        ]
        for name, code in zip(cls.names, blocks):
            (cls.directory / (name + ".py")).write_text(code, encoding="utf-8")
        sys.path.insert(0, str(cls.directory))
        cls.basic, cls.lift, cls.constraints = [importlib.import_module(name) for name in cls.names]

    @classmethod
    def tearDownClass(cls):
        sys.path.remove(str(cls.directory))
        for name in cls.names:
            sys.modules.pop(name, None)
        cls.temp.cleanup()

    def test_all_script_entry_points_and_output_files(self):
        import matplotlib.pyplot as plt
        import numpy as np
        import pandas as pd

        original_directory = Path.cwd()
        try:
            os.chdir(self.directory)
            for name in self.names:
                with self.subTest(script=name), contextlib.redirect_stdout(io.StringIO()):
                    runpy.run_path(str(self.directory / (name + ".py")), run_name="__main__")
            sweep = pd.read_csv("lift_curve_data.csv")
            self.assertEqual(50, len(sweep))
            self.assertTrue(np.isfinite(sweep[["power", "efficiency", "overall_utilization"]]).all().all())
            self.assertTrue(sweep["feasible"].any())
            self.assertTrue((~sweep["feasible"]).any())
            self.assertTrue((sweep.loc[sweep["feasible"], "power"] <= 4000.0).all())
            self.assertTrue((sweep.loc[~sweep["feasible"], "power"] > 4000.0).all())
            self.assertTrue(np.allclose(sweep["efficiency"], 0.78))
            constraints = pd.read_csv("constraint_analysis.csv")
            self.assertFalse(constraints.empty)
            self.assertNotIn("surgeMargin", constraints["constraint"].tolist())
            for filename in ["operating_envelope.png", "constraint_dashboard.png"]:
                self.assertGreater(Path(filename).stat().st_size, 1000)
        finally:
            plt.close("all")
            os.chdir(original_directory)

    def test_optimum_matches_power_limit_mass_balance_and_live_process(self):
        process = self.basic.create_compression_process()
        compressor = process.getUnit("Export Compressor")
        expected_flow = 50000.0 * 4000.0 / compressor.getPower("kW")
        result = self.basic.optimize_throughput(process)
        self.assertTrue(result["feasible"])
        self.assertAlmostEqual(expected_flow, result["optimal_flow_rate"], delta=1.0)
        self.assertLessEqual(result["total_power"], 4000.0)
        self.assertGreater(result["total_power"], 3999.0)
        self.assertEqual("Export Compressor", result["bottleneck"])
        self.assertAlmostEqual(result["optimal_flow_rate"], process.getUnit("feed").getFlowRate("kg/hr"), delta=1e-6)
        self.assertAlmostEqual(result["optimal_flow_rate"], process.getUnit("Gas Export").getFlowRate("kg/hr"), delta=1e-6)
        self.assertAlmostEqual(150.0, process.getUnit("Gas Export").getPressure("bara"))
        self.assertTrue(all(row["within_limits"] for row in self.basic.evaluate_constraints(process)))

    def test_empty_constraint_dashboard(self):
        process = self.basic.ProcessSystem()
        frame = self.constraints.analyze_equipment_constraints(process)
        self.assertTrue(frame.empty)
        with contextlib.redirect_stdout(io.StringIO()):
            self.assertIsNone(self.constraints.plot_constraint_dashboard(frame))

    def test_sweep_does_not_hide_missing_equipment_as_infeasibility(self):
        with self.assertRaises(AttributeError):
            self.lift.generate_lift_curve_data(self.basic.ProcessSystem(), [50.0], [150.0], [50000.0])


if __name__ == "__main__":
    unittest.main()
