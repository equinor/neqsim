"""Run every Python example in the optimization overview as published.

Set NEQSIM_TEST_CLASSPATH to use workspace Java classes, as for
test_practical_optimization_examples. Otherwise the installed neqsim JAR is used.
"""

import contextlib
import io
import os
from pathlib import Path
import re
import unittest


class OptimizationEntryExamplesTest(unittest.TestCase):
    """Check the actual examples against independent mass and power relations."""

    @classmethod
    def setUpClass(cls):
        import jpype

        classpath = os.environ.get("NEQSIM_TEST_CLASSPATH")
        if classpath and not jpype.isJVMStarted():
            jpype.startJVM(classpath=classpath.split(os.pathsep))

    def test_all_overview_python_blocks_and_headroom(self):
        document = Path(__file__).resolve().parents[1] / "docs/process/optimization/OPTIMIZATION_OVERVIEW.md"
        blocks = re.findall(r"```python\n(.*?)```", document.read_text(encoding="utf-8"), re.S)
        self.assertEqual(2, len(blocks), "Execute every published Python block")
        state = {"__name__": "__main__"}
        with contextlib.redirect_stdout(io.StringIO()):
            exec(compile(blocks[0], str(document) + ":python:0", "exec"), state)
        maximum_flow = state["result"].getOptimalValue()
        compressor = state["compressor"]
        feed = state["feed"]
        outlet = state["outlet"]
        maximum_power = compressor.getPower("kW")
        self.assertGreater(maximum_power, 3999.0)
        self.assertLessEqual(maximum_power, 4000.0)
        self.assertAlmostEqual(maximum_flow, feed.getFlowRate("kg/hr"), delta=1e-6)
        self.assertAlmostEqual(maximum_flow, outlet.getFlowRate("kg/hr"), delta=1e-6)
        self.assertAlmostEqual(150.0, outlet.getPressure("bara"), delta=1e-8)
        with contextlib.redirect_stdout(io.StringIO()):
            exec(compile(blocks[1], str(document) + ":python:1", "exec"), state)
        selected_flow = state["result"].getOptimalRate()
        selected_power = compressor.getPower("kW")
        self.assertTrue(state["result"].isFeasible())
        self.assertGreater(selected_power, 3799.0)
        self.assertLessEqual(selected_power, 3800.0)
        self.assertAlmostEqual(0.95 * maximum_flow, selected_flow, delta=1.5)
        self.assertAlmostEqual(selected_flow, feed.getFlowRate("kg/hr"), delta=1e-6)
        self.assertAlmostEqual(selected_flow, outlet.getFlowRate("kg/hr"), delta=1e-6)


if __name__ == "__main__":
    unittest.main()
