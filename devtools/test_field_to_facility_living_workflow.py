"""Execute the Java ten-well lifecycle through the persistent production workflow.

This is the source acceptance path for issue #4188. It deliberately calls the test-scope
Java fixture, consumes only its exact schema-v8 JSON, and exercises the existing #4187
adapter, guard, ledger, promotion, living report, and canonical report implementation.
"""

import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

import jpype
import yaml


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "devtools"))

from neqsim_continuous.cycle import run_cycle  # noqa: E402
from neqsim_continuous.ledger import Ledger  # noqa: E402
from neqsim_continuous.living import make_living, promote  # noqa: E402


OPTIMIZE_STAGE = '''"""Source-backed ten-well field-to-facility lifecycle stage."""
import json

import jpype

from neqsim_continuous.production import proposal_from_lifecycle_json


def run(ctx):
    acceptance = jpype.JClass(
        "neqsim.process.util.optimizer.FieldToFacilityOptimizationAcceptanceTest")
    encoded = str(acceptance.createTenWellLifecycleEvidenceJson(ctx.cycle_id))
    evidence = json.loads(encoded)
    current = next(period for period in evidence["periods"]
                   if period["period_id"] == ctx.cycle_id)
    envelope = current["envelope_evidence"]
    proposal = proposal_from_lifecycle_json(
        encoded,
        cycle_id=ctx.cycle_id,
        title="Review validated ten-well field operating point",
        objective_kpi="validated_field_operating_point",
        baseline_value=0.0,
        expected_gain=1.0,
        predicted={},
    )
    return {
        "proposals": [proposal],
        "kpis": {
            "lifecycle_period_count": evidence["period_count"],
            "lifecycle_evaluation_count": evidence["total_evaluation_count"],
            "lifecycle_runtime_seconds": evidence["envelope_runtime_seconds"],
            "validated_field_operating_point": 1.0,
            "current_envelope_evaluation_count": envelope["total_evaluation_count"],
        },
    }
'''


def source_classpath():
    """Return current main/test classes before Maven dependencies."""
    explicit = os.environ.get("NEQSIM_TEST_CLASSPATH")
    if explicit:
        return explicit.split(os.pathsep)
    dependency_file = ROOT / "target" / "optimization-classpath.txt"
    required = [ROOT / "target" / "classes", ROOT / "target" / "test-classes", dependency_file]
    if not all(path.exists() for path in required):
        raise RuntimeError(
            "Compile test classes and generate target/optimization-classpath.txt first")
    dependencies = dependency_file.read_text(encoding="utf-8").strip().split(os.pathsep)
    return [str(ROOT / "target" / "classes"), str(ROOT / "target" / "test-classes")] + dependencies


def create_task(parent):
    """Create the minimal production living task used by this acceptance workflow."""
    task = Path(parent) / "field_to_facility_living_acceptance"
    task.mkdir()
    (task / "study_config.yaml").write_text(
        "study:\n  title: Field-to-facility living acceptance\n  task_type: B\n",
        encoding="utf-8",
    )
    (task / "results.json").write_text(json.dumps({
        "task_statement": (
            "Validate a synthetic ten-well field-to-facility operating point without "
            "weakening physical constraints."),
        "key_results": {"validated_field_operating_point": 0.0},
        "recommendations": [
            "Treat the synthetic result as workflow acceptance, not operating approval."],
    }, indent=2), encoding="utf-8")
    make_living(str(task), template="production")

    continuous = task / "continuous"
    (continuous / "stages" / "optimize.py").write_text(OPTIMIZE_STAGE, encoding="utf-8")
    plan_path = continuous / "cycle_plan.yaml"
    plan = yaml.safe_load(plan_path.read_text(encoding="utf-8"))
    plan["scripts"] = {
        "optimize": {"file": "continuous/stages/optimize.py", "function": "run"},
    }
    plan["production"]["require_envelope_evidence"] = True
    plan["production"]["require_lifecycle_envelope_evidence"] = True
    plan["stages"] = [
        "script:optimize", "kpis", "gates", "constraints", "guard", "ledger", "digest",
    ]
    plan_path.write_text(yaml.safe_dump(plan, sort_keys=False), encoding="utf-8")

    goal_path = continuous / "goal.yaml"
    goal = yaml.safe_load(goal_path.read_text(encoding="utf-8"))
    goal["objective"] = {
        "metric": "validated_field_operating_point",
        "direction": "maximize",
        "target": 1.0,
        "confidence_required": "medium",
        "source_section": "synthetic acceptance",
    }
    goal["constraints"] = []
    goal_path.write_text(yaml.safe_dump(goal, sort_keys=False), encoding="utf-8")
    return task


class FieldToFacilityLivingWorkflowTest(unittest.TestCase):
    """Require one exact Java-to-report lifecycle receipt."""

    @classmethod
    def setUpClass(cls):
        """Start one JVM with current main and test classes."""
        try:
            classpath = source_classpath()
        except RuntimeError as error:
            if os.environ.get("CI"):
                raise
            raise unittest.SkipTest(str(error))
        if not jpype.isJVMStarted():
            jpype.startJVM("-Xmx2g", classpath=classpath, convertStrings=True)

    def test_java_lifecycle_is_guarded_promoted_and_reported(self):
        """Run the complete acceptance workflow without rebuilding Java evidence in Python."""
        with tempfile.TemporaryDirectory(prefix="neqsim-field-living-") as directory:
            task = create_task(directory)
            manifest = run_cycle(str(task), no_agent=True)
            self.assertEqual("complete", manifest["status"])
            self.assertFalse(manifest["degraded"], manifest)
            statuses = {row["name"]: row["status"] for row in manifest["stages"]}
            self.assertTrue(all(statuses[name] == "ok" for name in (
                "script:optimize", "guard", "ledger", "digest")))

            cycle_id = manifest["cycle_id"]
            cycle_dir = task / "continuous" / "cycles" / cycle_id
            guard = json.loads((cycle_dir / "guard.json").read_text(encoding="utf-8"))
            self.assertEqual(["Review validated ten-well field operating point"], guard["accepted"])
            self.assertEqual([], guard["withheld"])

            items = [item for item in Ledger(
                task / "continuous" / "ledger" / "events.jsonl").current().values()
                     if item.get("cycle") == cycle_id]
            self.assertEqual(1, len(items))
            evidence = items[0]["lifecycle_envelope_evidence"]
            self.assertEqual(8, evidence["schema_version"])
            self.assertEqual(cycle_id, evidence["cycle_id"])
            self.assertEqual(2, evidence["period_count"])
            self.assertEqual(12, evidence["requested_point_count"])
            self.assertEqual(12, evidence["completed_point_count"])
            self.assertGreater(evidence["total_evaluation_count"], 12)
            self.assertGreaterEqual(evidence["envelope_runtime_seconds"], 0.0)
            self.assertEqual(1, len(evidence["bottleneck_transitions"]))
            current = next(period for period in evidence["periods"]
                           if period["period_id"] == cycle_id)
            optimizer = current["envelope_evidence"]["optimizer_evidence"]
            for field in (
                    "simulation_converged", "candidate_feasible", "candidate_finite",
                    "constraint_evidence_complete", "state_restore_complete",
                    "accepted_point_replayed", "actions_complete"):
                self.assertIs(True, optimizer[field], field)

            promoted = promote(
                str(task), cycle_id, "Synthetic acceptance reviewer",
                note="Automated source acceptance only; no operating approval.")
            self.assertEqual(cycle_id, promoted["source_cycle"])
            results = json.loads((task / "results.json").read_text(encoding="utf-8"))
            snapshot = json.loads((task / "continuous" / "baseline" /
                                   "results_snapshot.json").read_text(encoding="utf-8"))
            self.assertEqual(evidence, results["lifecycle_envelope_evidence"])
            self.assertEqual(results, snapshot)
            living_report = (task / "continuous" / "LIVING_REPORT.md").read_text(encoding="utf-8")
            self.assertIn(cycle_id, living_report)

            generator = ROOT / "devtools" / "task_template" / "step3_report" / "generate_report.py"
            completed = subprocess.run([
                sys.executable, str(generator), "--task-dir", str(task), "--no-template",
                "--title", "Field-to-facility living acceptance",
            ], cwd=ROOT, capture_output=True, text=True, timeout=180, check=False)
            self.assertEqual(0, completed.returncode, completed.stdout[-4000:] + completed.stderr[-4000:])
            report = next((task / "step3_report").glob("*.html")).read_text(encoding="utf-8")
            self.assertIn("Lifecycle Operating Envelope", report)
            self.assertIn("Early life", report)
            self.assertIn("Current life", report)
            self.assertIn("shared plant power", report.lower())


if __name__ == "__main__":
    unittest.main(verbosity=2)
