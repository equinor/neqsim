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
from neqsim_continuous.evidence import accept, analyze, selected_stages  # noqa: E402
from neqsim_continuous.ledger import Ledger  # noqa: E402
from neqsim_continuous.living import make_living, promote  # noqa: E402
from neqsim_continuous.plan import load_plan  # noqa: E402


OPTIMIZE_STAGE = '''"""Source-backed ten-well field-to-facility lifecycle stage."""
import json
import os

import jpype
import yaml

from neqsim_continuous.production import proposal_from_lifecycle_context


def run(ctx):
    acceptance = jpype.JClass(
        "neqsim.process.util.optimizer.FieldToFacilityOptimizationAcceptanceTest")
    with open(os.path.join(ctx.task_dir, "continuous", "user_input.yaml"),
              encoding="utf-8") as input_file:
        task_input = yaml.safe_load(input_file) or {}
    multiplier = float(task_input["shared_power_limit_multiplier"])
    snapshot_path = os.path.join(
        ctx.task_dir,
        "continuous",
        "baseline",
        "results_snapshot.json",
    )
    reviewed_incumbent = None
    if os.path.exists(snapshot_path):
        with open(snapshot_path, encoding="utf-8") as snapshot_file:
            snapshot = json.load(snapshot_file)
        previous = snapshot.get("lifecycle_envelope_evidence")
        source_cycle = (ctx.baseline.get("meta") or {}).get("source_cycle")
        if isinstance(previous, dict) and source_cycle:
            previous_period = next(
                period for period in previous["periods"]
                if period["period_id"] == source_cycle)
            reviewed_incumbent = previous_period["envelope_evidence"][
                "accepted_candidate_values"]
    if reviewed_incumbent is None:
        encoded = str(acceptance.createTenWellLifecycleEvidenceJson(
            ctx.cycle_id,
            multiplier,
        ))
    else:
        incumbent_array = jpype.JArray(jpype.JDouble)(reviewed_incumbent)
        encoded = str(acceptance.createTenWellLifecycleEvidenceJson(
            ctx.cycle_id,
            multiplier,
            incumbent_array,
        ))
    evidence = json.loads(encoded)
    current = next(period for period in evidence["periods"]
                   if period["period_id"] == ctx.cycle_id)
    envelope = current["envelope_evidence"]
    proposal = proposal_from_lifecycle_context(
        encoded,
        ctx=ctx,
        title="Review validated ten-well field operating point",
        predicted={},
    )
    return {
        "proposals": [proposal],
        "kpis": {
            "lifecycle_period_count": evidence["period_count"],
            "lifecycle_evaluation_count": evidence["total_evaluation_count"],
            "lifecycle_runtime_seconds": evidence["envelope_runtime_seconds"],
            "validated_gas_export_kg_hr": proposal["predicted"][
                "validated_gas_export_kg_hr"],
            "current_envelope_evaluation_count": envelope["total_evaluation_count"],
            "shared_power_limit_multiplier": multiplier,
        },
    }
'''


UNCHANGED_STAGE = '''"""Independent scope used to prove selective lifecycle recomputation."""


def run(ctx):
    return {"kpis": {"unchanged_scope_marker": 1.0}}
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
    acceptance = jpype.JClass(
        "neqsim.process.util.optimizer.FieldToFacilityOptimizationAcceptanceTest")
    baseline_export = float(acceptance.createTenWellBaselineGasExportKgHr())
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
        "key_results": {"validated_gas_export_kg_hr": baseline_export},
        "recommendations": [
            "Treat the synthetic result as workflow acceptance, not operating approval."],
    }, indent=2), encoding="utf-8")
    make_living(str(task), template="production")

    continuous = task / "continuous"
    (continuous / "stages" / "optimize.py").write_text(OPTIMIZE_STAGE, encoding="utf-8")
    (continuous / "stages" / "unchanged.py").write_text(UNCHANGED_STAGE, encoding="utf-8")
    (continuous / "user_input.yaml").write_text(
        "shared_power_limit_multiplier: 1.01\n",
        encoding="utf-8",
    )
    plan_path = continuous / "cycle_plan.yaml"
    plan = yaml.safe_load(plan_path.read_text(encoding="utf-8"))
    plan["scripts"] = {
        "optimize": {"file": "continuous/stages/optimize.py", "function": "run"},
        "unchanged": {"file": "continuous/stages/unchanged.py", "function": "run"},
    }
    plan["production"]["require_envelope_evidence"] = True
    plan["production"]["require_lifecycle_envelope_evidence"] = True
    plan["production"]["require_objective_evidence"] = True
    plan["production"]["evidence_sensitive_stages"] = ["script:optimize"]
    plan["evidence"] = {
        "rules": [{
            "name": "shared-power-operating-limit",
            "match": "continuous/user_input.yaml",
            "stages": [
                "script:optimize", "kpis", "gates", "constraints", "guard", "ledger",
                "digest",
            ],
            "kpis": [
                "lifecycle_evaluation_count", "lifecycle_runtime_seconds",
                "validated_gas_export_kg_hr", "current_envelope_evaluation_count",
                "shared_power_limit_multiplier",
            ],
            "conclusions": ["validated field operating point and active bottleneck"],
        }],
        "dependencies": {},
    }
    plan["stages"] = [
        "script:unchanged", "script:optimize", "kpis", "gates", "constraints", "guard",
        "ledger", "digest",
    ]
    plan_path.write_text(yaml.safe_dump(plan, sort_keys=False), encoding="utf-8")

    goal_path = continuous / "goal.yaml"
    goal = yaml.safe_load(goal_path.read_text(encoding="utf-8"))
    goal["objective"] = {
        "metric": "validated_gas_export_kg_hr",
        "source_name": "validated gas export",
        "unit": "kg/hr",
        "direction": "maximize",
        "target": None,
        "confidence_required": "medium",
        "source_section": "current ten-well Java objective evidence",
    }
    goal["constraints"] = []
    goal_path.write_text(yaml.safe_dump(goal, sort_keys=False), encoding="utf-8")
    setup_plan = load_plan(str(task))
    accept(str(task), analyze(str(task), setup_plan), "task-setup")
    return task


class FieldToFacilityLivingWorkflowTest(unittest.TestCase):
    """Require exact Java-to-report receipts across a selective limit revision."""

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
            first_item = items[0]
            first_item_id = first_item["id"]
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
            self.assertEqual("validated gas export", optimizer["objective_name"])
            self.assertEqual("MAXIMIZE", optimizer["objective_direction"])
            self.assertEqual("kg/hr", optimizer["objective_unit"])
            self.assertEqual(
                optimizer["initial_objective_value"],
                items[0]["baseline_value"],
            )
            self.assertGreater(
                optimizer["accepted_objective_value"],
                optimizer["initial_objective_value"],
            )
            self.assertEqual(
                optimizer["accepted_objective_value"],
                items[0]["predicted"]["validated_gas_export_kg_hr"],
            )
            first_accepted_objective = optimizer["accepted_objective_value"]
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

            input_path = task / "continuous" / "user_input.yaml"
            input_path.write_text(
                "shared_power_limit_multiplier: 1.02\n",
                encoding="utf-8",
            )
            plan = load_plan(str(task))
            impact = analyze(str(task), plan)
            self.assertEqual("changed", impact["status"])
            self.assertFalse(impact["full_rerun"])
            self.assertEqual(1, impact["changed_files"])
            self.assertEqual(
                "continuous/user_input.yaml",
                impact["changes"][0]["path"],
            )
            self.assertEqual(
                ["validated field operating point and active bottleneck"],
                impact["affected_conclusions"],
            )
            stages = selected_stages(plan, impact)
            self.assertIn("script:optimize", stages)
            self.assertIn("guard", stages)
            self.assertIn("ledger", stages)
            self.assertNotIn("script:unchanged", stages)

            revised = run_cycle(
                str(task),
                stages=stages,
                no_agent=True,
                evidence_analysis=impact,
            )
            self.assertEqual("complete", revised["status"])
            self.assertFalse(revised["degraded"], revised)
            self.assertTrue(revised["selective_rerun"]["enabled"])
            self.assertIn("script:unchanged", revised["selective_rerun"]["retained_stages"])
            revised_stage_names = [row["name"] for row in revised["stages"]]
            self.assertNotIn("script:unchanged", revised_stage_names)

            revised_cycle_id = revised["cycle_id"]
            revised_cycle_dir = task / "continuous" / "cycles" / revised_cycle_id
            revised_kpis = json.loads(
                (revised_cycle_dir / "kpis.json").read_text(encoding="utf-8"))
            self.assertEqual(1.0, revised_kpis["unchanged_scope_marker"])
            self.assertEqual(1.02, revised_kpis["shared_power_limit_multiplier"])
            revised_guard = json.loads(
                (revised_cycle_dir / "guard.json").read_text(encoding="utf-8"))
            self.assertEqual(
                ["Review validated ten-well field operating point"],
                revised_guard["accepted"],
            )
            self.assertEqual([], revised_guard["withheld"])

            current_items = Ledger(
                task / "continuous" / "ledger" / "events.jsonl").current()
            stale_item = current_items[first_item_id]
            self.assertEqual("superseded", stale_item["status"])
            self.assertIs(False, stale_item["evidence_current"])
            self.assertEqual(revised_cycle_id, stale_item["evidence_invalidated_cycle"])
            revised_items = [item for item in current_items.values()
                             if item.get("cycle") == revised_cycle_id]
            self.assertEqual(1, len(revised_items))
            revised_item = revised_items[0]
            self.assertEqual(first_item_id, revised_item["revalidation_of"])
            self.assertIs(True, revised_item["evidence_current"])
            revised_evidence = revised_item["lifecycle_envelope_evidence"]
            self.assertEqual(revised_cycle_id, revised_evidence["cycle_id"])
            revised_current = next(
                period for period in revised_evidence["periods"]
                if period["period_id"] == revised_cycle_id)
            revised_optimizer = revised_current["envelope_evidence"]["optimizer_evidence"]
            self.assertEqual(
                first_accepted_objective,
                revised_optimizer["initial_objective_value"],
            )
            self.assertGreater(
                revised_optimizer["accepted_objective_value"],
                revised_optimizer["initial_objective_value"],
            )
            self.assertGreaterEqual(
                revised_optimizer["accepted_objective_value"],
                first_accepted_objective,
            )
            self.assertEqual(
                revised_optimizer["accepted_objective_value"],
                revised_item["predicted"]["validated_gas_export_kg_hr"],
            )
            self.assertEqual("unchanged", analyze(str(task), plan)["status"])


if __name__ == "__main__":
    unittest.main(verbosity=2)
