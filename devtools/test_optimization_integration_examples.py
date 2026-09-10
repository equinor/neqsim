"""Execute every Python fence in the external optimizer and pipeline guides.

Run ``python -m unittest devtools.test_optimization_integration_examples -v`` in
the Python environment containing neqsim, JPype, NumPy, SciPy and optional NLopt.
Set NEQSIM_TEST_CLASSPATH to compiled workspace classes plus dependencies to test
current source. Without it, the installed neqsim package supplies the Java JAR.
"""

import contextlib
import io
import os
from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]


def blocks(path):
    return re.findall(r"^```python\n(.*?)^```", path.read_text(encoding="utf-8"), re.M | re.S)


class OptimizationIntegrationExamplesTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        classpath = os.environ.get("NEQSIM_TEST_CLASSPATH")
        if classpath:
            import jpype

            if jpype.isJVMStarted():
                raise RuntimeError("Start a fresh Python process to select the Java classpath")
            jpype.startJVM(classpath=classpath.split(os.pathsep))
        cls.external = blocks(ROOT / "docs/integration/EXTERNAL_OPTIMIZER_INTEGRATION.md")
        cls.network = blocks(ROOT / "docs/process/pipeline_network_optimization.md")
        if len(cls.external) != 27 or len(cls.network) != 1:
            raise AssertionError("Update the execution groups to cover every published Python fence")
        groups = [list(range(6)) + [7, 8, 9, 10, 24, 25], [0, 1, 2] + list(range(11, 24)), [26, 6]]
        if set().union(*(set(group) for group in groups)) != set(range(len(cls.external))):
            raise AssertionError("Every external optimizer fence must belong to an execution group")

    def execute(self, source, indices, label):
        namespace = {}
        capture = io.StringIO()
        for index in indices:
            try:
                with contextlib.redirect_stdout(capture):
                    exec(compile(source[index], f"{label} Python fence {index}", "exec"), namespace)
            except Exception as error:
                self.fail(f"{label} Python fence {index}: {error}\n{capture.getvalue()}")
        return namespace

    def test_basic_scipy_nlopt_and_custom_setters(self):
        namespace = self.execute(self.external, list(range(6)) + [7, 8, 9, 10, 24, 25], "external")
        self.assertAlmostEqual(40000.0, namespace["x_opt"][0], delta=0.01)
        self.assertEqual(2, namespace["evaluator"].getParameterCount())
        self.assertEqual(2, len(namespace["problem"]["constraints"]))

    def test_quality_actions_allocations_and_paired_study_ranking(self):
        namespace = self.execute(self.external, [0, 1, 2] + list(range(11, 24)), "external")
        quality = namespace["quality_result"]
        self.assertAlmostEqual(1.0, quality.getObjectiveGradient()[0], delta=1.0e-6)
        candidate = namespace["candidate"]
        self.assertTrue(candidate.isBaselineRestored())
        self.assertTrue(candidate.isBaselineSimulationConverged())
        self.assertAlmostEqual(namespace["total_rate"], sum(namespace["best_rates"]), delta=1.0e-5)
        self.assertAlmostEqual(200.0, namespace["best_delta"], delta=1.0e-6)
        self.assertEqual("separator-gas-1200", str(namespace["best_alternative_id"]))
        self.assertEqual(3, len(namespace["ranked_rows"]))
        self.assertFalse(namespace["rejected_rows"])

    def test_complete_compression_and_multiobjective_examples(self):
        namespace = self.execute(self.external, [26, 6], "external")
        final = namespace["final_evaluation"]
        self.assertTrue(final.isFeasible())
        self.assertTrue(final.isSimulationConverged())
        self.assertGreater(final.getObjective(), 438.0)
        self.assertLess(final.getObjective(), 440.0)
        self.assertAlmostEqual(10000.0, namespace["optimal_values"][0], delta=0.01)
        self.assertAlmostEqual(60.0, namespace["optimal_values"][1], delta=1.0e-5)
        self.assertEqual(5, len(namespace["pareto_points"]))
        self.assertTrue(all(point["scaled_objectives"][0] > 0.0 for point in namespace["pareto_points"]))

    def test_pipeline_network_example(self):
        namespace = self.execute(self.network, [0], "pipeline network")
        result = namespace["result"]
        self.assertTrue(result.converged)
        self.assertGreater(result.totalProductionKgHr, 0.0)
        self.assertLess(result.totalProductionKgHr, 1.0e9)


if __name__ == "__main__":
    unittest.main()
