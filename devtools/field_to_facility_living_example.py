"""Run the source-backed ten-well field-to-facility living acceptance workflow.

This engineer-facing example reuses the exact Java fixture and #4187 production
guard exercised by ``test_field_to_facility_living_workflow``. It requires a
source checkout with compiled main/test classes because the synthetic facility
is deliberately maintained as acceptance evidence, not as a packaged plant API.
"""

import argparse
import json
import math
import os
from pathlib import Path
import subprocess
import sys
from types import SimpleNamespace

import jpype
import yaml


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "devtools"))

from neqsim_continuous.cycle import run_cycle  # noqa: E402
from neqsim_continuous.evidence import accept, analyze, selected_stages  # noqa: E402
from neqsim_continuous.ledger import Ledger  # noqa: E402
from neqsim_continuous.living import make_living, promote  # noqa: E402
from neqsim_continuous.plan import load_plan  # noqa: E402
from neqsim_continuous import production  # noqa: E402


ACCEPTED_TERMINATION_REASONS = (
    "CONVERGED_WITH_REPLAYED_FEASIBLE_CANDIDATE",
    "BUDGET_EXHAUSTED_WITH_REPLAYED_FEASIBLE_CANDIDATE",
)

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
    migration = json.loads(str(
        acceptance.createMapToDriverMigrationEvidenceJson(ctx.cycle_id)))
    current = next(period for period in evidence["periods"]
                   if period["period_id"] == ctx.cycle_id)
    envelope = current["envelope_evidence"]
    proposal = proposal_from_lifecycle_context(
        encoded,
        ctx=ctx,
        title="Review validated ten-well field operating point",
        predicted={},
    )
    proposal["map_driver_migration_evidence"] = migration
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


def ensure_jvm():
    """Start JPype against the exact compiled source checkout when needed."""
    if not jpype.isJVMStarted():
        jpype.startJVM("-Xmx2g", classpath=source_classpath(), convertStrings=True)


def create_task(parent, shared_power_limit_multiplier=1.01):
    """Create the production living task used by the acceptance workflow."""
    if not math.isfinite(shared_power_limit_multiplier) or shared_power_limit_multiplier <= 1.0:
        raise ValueError("Shared-power multiplier must be finite and greater than 1.0")
    acceptance = jpype.JClass(
        "neqsim.process.util.optimizer.FieldToFacilityOptimizationAcceptanceTest")
    baseline_export = float(acceptance.createTenWellBaselineGasExportKgHr())
    task = Path(parent).resolve() / "field_to_facility_living_acceptance"
    task.mkdir(parents=True)
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
        "shared_power_limit_multiplier: {}\n".format(shared_power_limit_multiplier),
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
    plan["production"]["require_map_driver_migration_evidence"] = True
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


def release_own_completed_cycle_lease(task, manifest):
    """Remove only a completed cycle lease still owned by this Python process."""
    lock_path = Path(task) / "continuous" / "LOCK"
    if not lock_path.exists():
        return False
    lease = json.loads(lock_path.read_text(encoding="utf-8"))
    if manifest.get("status") != "complete" or lease.get("pid") != os.getpid():
        raise RuntimeError(
            "Refusing to clear a living-task lease not owned by this completed cycle")
    lock_path.unlink()
    return True


def run_initial_cycle(task):
    """Execute the initial complete living-task cycle."""
    manifest = run_cycle(str(task), no_agent=True)
    release_own_completed_cycle_lease(task, manifest)
    return manifest


def run_revised_cycle(task, shared_power_limit_multiplier):
    """Change one declared limit and selectively recompute affected evidence."""
    if not math.isfinite(shared_power_limit_multiplier) or shared_power_limit_multiplier <= 1.0:
        raise ValueError("Shared-power multiplier must be finite and greater than 1.0")
    input_path = Path(task) / "continuous" / "user_input.yaml"
    input_path.write_text(
        "shared_power_limit_multiplier: {}\n".format(shared_power_limit_multiplier),
        encoding="utf-8",
    )
    plan = load_plan(str(task))
    impact = analyze(str(task), plan)
    stages = selected_stages(plan, impact)
    manifest = run_cycle(
        str(task),
        stages=stages,
        no_agent=True,
        evidence_analysis=impact,
    )
    release_own_completed_cycle_lease(task, manifest)
    return manifest, impact, stages


def cycle_item(task, cycle_id):
    """Return the unique ledger item emitted by one workflow cycle."""
    items = [
        item for item in Ledger(
            Path(task) / "continuous" / "ledger" / "events.jsonl").current().values()
        if item.get("cycle") == cycle_id
    ]
    if len(items) != 1:
        raise RuntimeError("Expected exactly one ledger item for cycle {}".format(cycle_id))
    return items[0]


def validate_cycle(task, manifest):
    """Fail closed unless a cycle has one fully qualified accepted proposal."""
    if manifest.get("status") != "complete" or manifest.get("degraded"):
        raise RuntimeError("Living cycle did not complete without degradation")
    cycle_id = manifest["cycle_id"]
    cycle_dir = Path(task) / "continuous" / "cycles" / cycle_id
    guard = json.loads((cycle_dir / "guard.json").read_text(encoding="utf-8"))
    if guard.get("accepted") != ["Review validated ten-well field operating point"]:
        raise RuntimeError("Production guard did not accept the expected proposal")
    if guard.get("withheld"):
        raise RuntimeError("Production guard withheld field-to-facility evidence")

    item = cycle_item(task, cycle_id)
    lifecycle_reasons = production._lifecycle_evidence_reasons(
        SimpleNamespace(cycle_id=cycle_id), item, required=True)
    migration_reasons = production._map_driver_migration_evidence_reasons(
        SimpleNamespace(cycle_id=cycle_id), item, required=True)
    _, link_reasons = production._map_driver_lifecycle_link(
        SimpleNamespace(cycle_id=cycle_id), item, required=True)
    reasons = lifecycle_reasons + migration_reasons + link_reasons
    if reasons:
        raise RuntimeError("Invalid accepted evidence: {}".format(", ".join(reasons)))

    evidence = item["lifecycle_envelope_evidence"]
    current = next(period for period in evidence["periods"]
                   if period["period_id"] == cycle_id)
    optimizer = current["envelope_evidence"]["optimizer_evidence"]
    flags = (
        "simulation_converged", "candidate_feasible", "candidate_finite",
        "constraint_evidence_complete", "state_restore_complete",
        "accepted_point_replayed", "actions_complete",
    )
    if any(optimizer.get(name) is not True for name in flags):
        raise RuntimeError("Accepted optimizer evidence is incomplete")
    if optimizer.get("termination_reason") not in ACCEPTED_TERMINATION_REASONS:
        raise RuntimeError("Accepted optimizer termination reason is not qualified")
    if not math.isfinite(float(optimizer["accepted_objective_value"])):
        raise RuntimeError("Accepted objective is non-finite")
    return item


def promote_cycle(task, cycle_id):
    """Promote one guard-approved cycle through the existing reviewed path."""
    promoted = promote(
        str(task),
        cycle_id,
        "Synthetic acceptance reviewer",
        note="Automated source acceptance only; no operating approval.",
    )
    manifest_path = Path(task) / "continuous" / "cycles" / cycle_id / "cycle.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    release_own_completed_cycle_lease(task, manifest)
    return promoted


def render_report(task):
    """Generate and return the canonical HTML report path."""
    generator = ROOT / "devtools" / "task_template" / "step3_report" / "generate_report.py"
    completed = subprocess.run([
        sys.executable,
        str(generator),
        "--task-dir",
        str(task),
        "--no-template",
        "--title",
        "Field-to-facility living acceptance",
    ], cwd=ROOT, capture_output=True, text=True, timeout=180, check=False)
    if completed.returncode != 0:
        detail = completed.stdout[-4000:] + completed.stderr[-4000:]
        raise RuntimeError("Canonical report generation failed:\n{}".format(detail))
    reports = sorted((Path(task) / "step3_report").glob("*.html"))
    if not reports:
        raise RuntimeError("Canonical report generation produced no HTML file")
    return reports[-1]


def cycle_summary(item, shared_power_limit_multiplier):
    """Return compact engineer-facing evidence for one accepted cycle."""
    evidence = item["lifecycle_envelope_evidence"]
    current = next(period for period in evidence["periods"]
                   if period["period_id"] == item["cycle"])
    optimizer = current["envelope_evidence"]["optimizer_evidence"]
    migration = item["map_driver_migration_evidence"]
    return {
        "cycle_id": item["cycle"],
        "shared_power_limit_multiplier": shared_power_limit_multiplier,
        "validated_gas_export_kg_hr": optimizer["accepted_objective_value"],
        "termination_reason": optimizer["termination_reason"],
        "search_evaluation_count": optimizer["evaluation_count"],
        "search_runtime_seconds": optimizer["runtime_seconds"],
        "lifecycle_completed_point_count": evidence["completed_point_count"],
        "lifecycle_total_evaluation_count": evidence["total_evaluation_count"],
        "lifecycle_runtime_seconds": evidence["envelope_runtime_seconds"],
        "configured_map_constraint": migration["configured_map_constraint_name"],
        "driver_constraint": migration["driver_constraint_name"],
    }


def run_acceptance(output_parent, initial_multiplier=1.01, revised_multiplier=1.02):
    """Run, revalidate, promote and report the complete two-cycle acceptance."""
    ensure_jvm()
    task = create_task(output_parent, initial_multiplier)
    initial_manifest = run_initial_cycle(task)
    initial_item = validate_cycle(task, initial_manifest)
    promote_cycle(task, initial_manifest["cycle_id"])

    revised_manifest, impact, stages = run_revised_cycle(task, revised_multiplier)
    revised_item = validate_cycle(task, revised_manifest)
    if revised_item.get("revalidation_of") != initial_item.get("id"):
        raise RuntimeError("Revised cycle is not linked to the superseded incumbent")
    promote_cycle(task, revised_manifest["cycle_id"])
    report = render_report(task)

    summary = {
        "task_directory": str(task),
        "advisory": True,
        "initial": cycle_summary(initial_item, initial_multiplier),
        "revised": cycle_summary(revised_item, revised_multiplier),
        "selective_revalidation": {
            "status": impact["status"],
            "full_rerun": impact["full_rerun"],
            "selected_stages": stages,
            "retained_stages": revised_manifest["selective_rerun"]["retained_stages"],
        },
        "living_report": str(task / "continuous" / "LIVING_REPORT.md"),
        "canonical_report": str(report),
        "limitation": (
            "Synthetic source acceptance only; not a reservoir forecast, equipment "
            "qualification, operating approval or global-optimality proof."),
    }
    summary_path = task / "field_to_facility_acceptance_summary.json"
    summary_path.write_text(json.dumps(summary, indent=2), encoding="utf-8")
    summary["summary_file"] = str(summary_path)
    return summary


def main(argv=None):
    """Run the command-line example and print its compact result summary."""
    parser = argparse.ArgumentParser(
        description="Run the synthetic ten-well field-to-facility living acceptance")
    parser.add_argument(
        "--output-parent",
        required=True,
        help="Existing or new parent directory for the generated living-task folder",
    )
    parser.add_argument(
        "--initial-shared-power-multiplier",
        type=float,
        default=1.01,
    )
    parser.add_argument(
        "--revised-shared-power-multiplier",
        type=float,
        default=1.02,
    )
    args = parser.parse_args(argv)
    summary = run_acceptance(
        args.output_parent,
        initial_multiplier=args.initial_shared_power_multiplier,
        revised_multiplier=args.revised_shared_power_multiplier,
    )
    print(json.dumps(summary, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
