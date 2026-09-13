"""Execute every Python fence in the batch, Pareto, flow and reconciliation guides.

Run ``python -m unittest devtools.test_optimizer_guide_examples -v`` after Java
compilation and Maven dependency:build-classpath with
``-Dmdep.outputFile=target/optimization-classpath.txt``. Each page runs sequentially
in a fresh process using this interpreter and the compiled repository classes.
NEQSIM_TEST_CLASSPATH can explicitly override that classpath.
"""

import argparse
import json
import math
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]
PAGES = {
    "batch-studies": 10,
    "multi-objective-optimization": 12,
    "flow-rate-optimization": 8,
    "data-reconciliation": 10,
}


def source_classpath():
    """Select source classes before dependencies, never an implicit released JAR."""
    explicit = os.environ.get("NEQSIM_TEST_CLASSPATH")
    if explicit:
        return explicit.split(os.pathsep)
    dependency_file = ROOT / "target/optimization-classpath.txt"
    if not dependency_file.is_file() or not (ROOT / "target/classes").is_dir():
        raise RuntimeError("Compile Java and generate target/optimization-classpath.txt first")
    return [str(ROOT / "target/classes")] + dependency_file.read_text().strip().split(os.pathsep)


def require_source_classpath(test_case):
    """Skip locally when the build prerequisite is missing; still fail in CI."""
    try:
        source_classpath()
    except RuntimeError as error:
        if os.environ.get("CI"):
            raise
        test_case.skipTest(
            "{0}: ./mvnw test dependency:build-classpath "
            "-Dmdep.outputFile=target/optimization-classpath.txt".format(error))


def verify_results(page, context):
    """Check that execution produced the physical/reporting result taught by the page."""
    if page == "batch-studies":
        frame = context["df_success"]
        assert len(frame) == 25 and (frame["obj_power"] > 0.0).all()
        assert all(result.getSuccessCount() == 3 for result in context["concept_results"].values())
        assert all(result.getFailureCount() == 0 for result in context["concept_results"].values())
        assert json.loads(Path("batch_results.json").read_text())["successCount"] == 25
    elif page == "multi-objective-optimization":
        feasible = [point for point in context["front"].getSolutions() if point.isFeasible()]
        assert len(feasible) >= 2, "No feasible power/throughput tradeoff was generated"
        assert all(point.getRawValue(0) > 0.0 and point.getRawValue(1) > 0.0 for point in feasible)
        assert all(math.isfinite(candidate["power"]) and candidate["power"] > 0.0
                   for candidate in context["pareto_scipy"])
        assert Path("pareto_front.csv").stat().st_size > 100
    elif page == "flow-rate-optimization":
        table = context["table"]
        assert table.countFeasiblePoints() > 0, "The documented sweep found no feasible pressure pair"
        for i in range(len(context["inlet_pressures"])):
            for j in range(len(context["outlet_pressures"])):
                point = table.getOperatingPoint(i, j)
                if point is not None and point.isFeasible():
                    assert 25000.0 <= point.getFlowRate() <= 100000.0
                    assert 0.0 < point.getTotalPower() <= 5000.0
    elif page == "data-reconciliation":
        assert context["successful_updates"] == 6, "Thirty warm-up samples must allow six of 35 updates"
        result = context["last_good_result"]
        assert result.isConverged() and result.isGlobalTestPassed() and not result.hasGrossErrors()
        variables = {str(variable.getName()): variable.getReconciledValue()
                     for variable in result.getVariables()}
        residual = variables["FI-1001"] - sum(variables[name] for name in ["FI-2001", "FI-3001", "FI-4001"])
        assert abs(residual) < 1e-6, "Reconciled separator mass balance did not close"


def run_page(page, directory):
    """Run one page in a fresh JVM; the page itself supplies the process fixtures."""
    import jpype
    import jpype.imports

    classpath = source_classpath()
    jpype.startJVM("-Xmx1g", classpath=classpath, convertStrings=False)
    os.environ["MPLBACKEND"] = "Agg"
    source = ROOT / "docs/process/optimization" / (page + ".md")
    blocks = re.findall(r"^```python\n(.*?)^```", source.read_text(encoding="utf-8"), re.M | re.S)
    assert len(blocks) == PAGES[page], "Update coverage when adding or removing Python examples"
    os.chdir(directory)
    context = {"__name__": "__main__"}
    for number, code in enumerate(blocks):
        exec(compile(code, f"{source.name}:python:{number + 1}", "exec"), context)
        print(f"PASS {source.name} Python block {number + 1}", flush=True)
    verify_results(page, context)
    print(f"PASS {page}: {len(blocks)} blocks and result assertions", flush=True)


class OptimizerGuideExamplesTest(unittest.TestCase):
    """Keep each page isolated while executing its snippets in documented order."""

    def check_page(self, page):
        require_source_classpath(self)
        with tempfile.TemporaryDirectory(prefix="neqsim-optimizer-guide-") as directory:
            result = subprocess.run(
                [sys.executable, str(Path(__file__).resolve()), "--worker", page, "--directory", directory],
                cwd=ROOT, capture_output=True, text=True, timeout=600, check=False,
            )
            self.assertEqual(0, result.returncode, result.stdout[-6000:] + result.stderr[-6000:])

    def test_batch_studies(self):
        self.check_page("batch-studies")

    def test_multi_objective_optimization(self):
        self.check_page("multi-objective-optimization")

    def test_flow_rate_optimization(self):
        self.check_page("flow-rate-optimization")

    def test_data_reconciliation(self):
        self.check_page("data-reconciliation")


if __name__ == "__main__":
    if "--worker" in sys.argv:
        parser = argparse.ArgumentParser()
        parser.add_argument("--worker", choices=PAGES, required=True)
        parser.add_argument("--directory", type=Path, required=True)
        args = parser.parse_args()
        run_page(args.worker, args.directory)
    else:
        unittest.main()
