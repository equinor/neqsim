"""Execute the Java ten-well lifecycle through the persistent production workflow.

This is the source acceptance path for issue #4188. It deliberately calls the test-scope
Java fixture, consumes its exact schema-v17 lifecycle JSON plus the exact configured
casing-map-to-driver migration receipt, and exercises the existing #4187 adapter, guard,
ledger, promotion, living report, and canonical report implementation.
"""

import copy
import json
import os
from pathlib import Path
import sys
import tempfile
from types import SimpleNamespace
import unittest

import jpype


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "devtools"))

from field_to_facility_living_example import (  # noqa: E402
    create_task,
    promote_cycle,
    release_own_completed_cycle_lease,
    render_report,
    run_initial_cycle,
    run_revised_cycle,
    source_classpath,
)
from neqsim_continuous.evidence import analyze  # noqa: E402
from neqsim_continuous.ledger import Ledger  # noqa: E402
from neqsim_continuous.plan import load_plan  # noqa: E402
from neqsim_continuous import production  # noqa: E402


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
            manifest = run_initial_cycle(task)
            self.assertEqual("complete", manifest["status"])
            self.assertFalse(manifest["degraded"], manifest)
            statuses = {row["name"]: row["status"] for row in manifest["stages"]}
            self.assertTrue(all(statuses[name] == "ok" for name in (
                "script:optimize", "guard", "ledger", "digest")))

            cycle_id = manifest["cycle_id"]
            cycle_dir = task / "continuous" / "cycles" / cycle_id
            guard = json.loads((cycle_dir / "guard.json").read_text(encoding="utf-8"))
            self.assertEqual(["Review validated ten-well field operating point"], guard["accepted"], guard)
            self.assertEqual([], guard["withheld"])

            items = [item for item in Ledger(
                task / "continuous" / "ledger" / "events.jsonl").current().values()
                     if item.get("cycle") == cycle_id]
            self.assertEqual(1, len(items))
            first_item = items[0]
            first_item_id = first_item["id"]
            evidence = items[0]["lifecycle_envelope_evidence"]
            migration = items[0]["map_driver_migration_evidence"]
            self.assertEqual(17, evidence["schema_version"])
            self.assertEqual(cycle_id, evidence["cycle_id"])
            self.assertEqual(2, evidence["period_count"])
            self.assertEqual(64, evidence["requested_point_count"])
            self.assertEqual(64, evidence["completed_point_count"])
            self.assertGreater(evidence["total_evaluation_count"], 64)
            self.assertGreaterEqual(evidence["envelope_runtime_seconds"], 0.0)
            self.assertEqual(1, len(evidence["bottleneck_transitions"]))
            self.assertEqual(1, migration["receipt_version"])
            self.assertEqual(cycle_id, migration["cycle_id"])
            self.assertEqual("CONFIGURED_OPERATING",
                             migration["configured_map_limit_role"])
            self.assertTrue(migration["configured_map_constraint_name"].endswith(
                ("/surgeMargin", "/stonewallMargin")))
            self.assertNotEqual(migration["configured_map_constraint_name"],
                                migration["driver_constraint_name"])
            self.assertTrue(migration["driver_constraint_name"].endswith(
                ("#driver-maximum-power", "::export shaft control/shaftPower")))
            self.assertIs(True, migration["map_operating_limit_cleared"])
            self.assertIs(True, migration["state_restore_complete"])
            self.assertIs(True, migration["advisory"])
            self.assertEqual(
                [migration["action_id"]],
                migration["map_limited_slice"]["axis_action_ids"],
            )
            self.assertEqual(
                [migration["action_id"]],
                migration["driver_limited_slice"]["axis_action_ids"],
            )
            self.assertEqual(
                migration["map_limited_slice"]["axis_values"],
                migration["driver_limited_slice"]["axis_values"],
            )
            self.assertTrue(all(
                "constraint_opportunity_summary" in period for period in evidence["periods"]))
            current = next(period for period in evidence["periods"]
                           if period["period_id"] == cycle_id)
            coverage = current["utilization_coverage"]
            self.assertIs(True, coverage["complete"])
            self.assertEqual([], coverage["diagnostics"])
            self.assertEqual(39, len(coverage["expected_constraint_ids"]))
            self.assertEqual(10, len(coverage["expected_plant_constraint_ids"]))
            self.assertEqual(49, len(coverage["rows"]))
            self.assertTrue(all(row["statuses"] == ["AVAILABLE"]
                                for row in coverage["rows"]))
            self.assertTrue(any(row["evidence_kind"] == "PLANT_CONSTRAINT"
                                and row["qualified_constraint_id"].endswith(
                                    "#driver-maximum-power")
                                for row in coverage["rows"]))
            self.assertTrue(any(row["evidence_kind"] == "PLANT_CONSTRAINT"
                                and row["qualified_constraint_id"].endswith(
                                    "#total-shaft-power")
                                for row in coverage["rows"]))
            self.assertTrue(any(row["provenance"] ==
                                "synthetic lifecycle separation limit"
                                for row in coverage["rows"]))
            link, link_reasons = production._map_driver_lifecycle_link(
                SimpleNamespace(cycle_id=cycle_id), first_item, required=True)
            self.assertEqual([], link_reasons)
            self.assertEqual(cycle_id, link["period_id"])
            self.assertEqual(["AVAILABLE"], link["map_row"]["statuses"])
            self.assertEqual(["AVAILABLE"], link["driver_row"]["statuses"])
            self.assertNotEqual(link["map_row"]["qualified_constraint_id"],
                                link["driver_row"]["qualified_constraint_id"])
            self.assertNotEqual(migration["configured_map_limit_provenance"],
                                link["map_row"]["provenance"])
            optimizer = current["envelope_evidence"]["optimizer_evidence"]
            self.assertEqual("validated gas export", optimizer["objective_name"])
            self.assertEqual("MAXIMIZE", optimizer["objective_direction"])
            self.assertEqual("kg/hr", optimizer["objective_unit"])
            self.assertIn(optimizer["termination_reason"], (
                "CONVERGED_WITH_REPLAYED_FEASIBLE_CANDIDATE",
                "BUDGET_EXHAUSTED_WITH_REPLAYED_FEASIBLE_CANDIDATE",
            ))
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
            opportunities = current["constraint_opportunity_summary"]
            self.assertEqual(2, opportunities["summary_version"])
            qualified = [
                candidate for candidate in opportunities["candidates"]
                if candidate["paired_relief_study_status"] == "QUALIFIED"
            ]
            self.assertEqual(2, len(qualified))
            receipts = [candidate["paired_relief_study_receipt"] for candidate in qualified]
            self.assertEqual(
                {"Field::HP separator/gas handling",
                 "Field::LP suction scrubber/gas handling"},
                {receipt["qualified_constraint_name"] for receipt in receipts},
            )
            self.assertEqual({2}, {receipt["receipt_version"] for receipt in receipts})
            self.assertEqual({"accepted-relief-portfolio"}, {
                receipt["portfolio_ranking"]["portfolio_id"] for receipt in receipts
            })
            self.assertEqual({2}, {
                receipt["portfolio_ranking"]["candidate_count"] for receipt in receipts
            })
            self.assertEqual({0, 1}, {
                receipt["portfolio_ranking"]["input_index"] for receipt in receipts
            })
            self.assertEqual({1}, {
                receipt["portfolio_ranking"]["rank"] for receipt in receipts
            })
            for receipt in receipts:
                self.assertEqual(
                    current["envelope_evidence"]["accepted_candidate_values"],
                    receipt["baseline_action_values"],
                )
                self.assertEqual(
                    optimizer["accepted_objective_value"],
                    receipt["objective"]["baseline_value"],
                )
                self.assertLessEqual(
                    receipt["objective"]["baseline_binding_residual"],
                    receipt["objective"]["baseline_binding_tolerance"],
                )
                self.assertIs(True, receipt["capacity_restored"])
                self.assertIs(True, receipt["process_state_restored"])
            combination = current["qualified_relief_combination"]
            self.assertEqual("accepted-relief-portfolio", combination["portfolio_id"])
            self.assertEqual(2, len(combination["members"]))
            self.assertEqual(
                {receipt["alternative_id"] for receipt in receipts},
                {member["alternative_id"] for member in combination["members"]},
            )
            self.assertAlmostEqual(
                combination["objective"]["combined_delta"]
                - combination["objective"]["independent_delta_sum"],
                combination["objective"]["interaction_delta"],
            )
            self.assertIs(True, combination["capacity_restored"])
            self.assertIs(True, combination["process_state_restored"])
            self.assertIn("baseline_bottleneck", combination["bottleneck_migration"])
            self.assertIn("combined_bottleneck", combination["bottleneck_migration"])
            relief_envelope = current["qualified_relief_envelope"]
            self.assertEqual(2, relief_envelope["receipt_version"])
            self.assertEqual(7, relief_envelope["slice_pair_count"])
            self.assertEqual(8, relief_envelope["sampled_cell_comparison_count"])
            self.assertIs(True, relief_envelope["capacity_restored"])
            coupled_pairs = [pair for pair in relief_envelope["slice_pairs"]
                             if len(pair["axis_action_ids"]) == 2]
            self.assertEqual(3, len(coupled_pairs))
            self.assertEqual(
                [2, 2, 4],
                sorted(len(pair["sampled_cell_comparisons"]) for pair in coupled_pairs),
            )
            self.assertTrue(all(
                comparison["interpolated"] is False
                and comparison["baseline_cell"]["sampled_cell_only"] is True
                for pair in coupled_pairs
                for comparison in pair["sampled_cell_comparisons"]))
            early = next(period for period in evidence["periods"]
                         if period["period_id"] == "early-life")
            self.assertGreaterEqual(
                early["qualified_relief_envelope"][
                    "sampled_point_identity_change_count"], 1)
            self.assertGreaterEqual(
                early["qualified_relief_envelope"][
                    "continuous_boundary_comparison_count"], 1)
            tampered = copy.deepcopy(first_item)
            tampered_current = next(
                period for period in tampered["lifecycle_envelope_evidence"]["periods"]
                if period["period_id"] == cycle_id)
            tampered_receipt = next(
                candidate["paired_relief_study_receipt"]
                for candidate in tampered_current["constraint_opportunity_summary"]["candidates"]
                if candidate["paired_relief_study_status"] == "QUALIFIED")
            tampered_receipt["portfolio_ranking"]["rank"] = 2
            self.assertIn(
                "lifecycle_period_1_invalid_qualified_relief_portfolio",
                production._lifecycle_evidence_reasons(
                    SimpleNamespace(cycle_id=cycle_id), tampered, required=True),
            )
            tampered_combination = copy.deepcopy(first_item)
            tampered_current = next(
                period for period in tampered_combination["lifecycle_envelope_evidence"]["periods"]
                if period["period_id"] == cycle_id)
            tampered_current["qualified_relief_combination"]["objective"][
                "interaction_delta"] += 1.0
            self.assertIn(
                "lifecycle_period_1_invalid_qualified_relief_combination",
                production._lifecycle_evidence_reasons(
                    SimpleNamespace(cycle_id=cycle_id), tampered_combination, required=True),
            )
            tampered_envelope = copy.deepcopy(first_item)
            tampered_current = next(
                period for period in tampered_envelope["lifecycle_envelope_evidence"]["periods"]
                if period["period_id"] == cycle_id)
            tampered_current["qualified_relief_envelope"][
                "sampled_point_identity_change_count"] += 1
            self.assertIn(
                "lifecycle_period_1_invalid_qualified_relief_envelope",
                production._lifecycle_evidence_reasons(
                    SimpleNamespace(cycle_id=cycle_id), tampered_envelope, required=True),
            )
            tampered_cell = copy.deepcopy(first_item)
            tampered_current = next(
                period for period in tampered_cell["lifecycle_envelope_evidence"]["periods"]
                if period["period_id"] == cycle_id)
            tampered_coupled_pair = next(
                pair for pair in tampered_current["qualified_relief_envelope"]["slice_pairs"]
                if len(pair["axis_action_ids"]) == 2)
            tampered_coupled_pair["sampled_cell_comparisons"][0]["baseline_cell"][
                "classification"] = "ALL_CORNERS_FEASIBLE"
            self.assertIn(
                "lifecycle_period_1_invalid_qualified_relief_envelope",
                production._lifecycle_evidence_reasons(
                    SimpleNamespace(cycle_id=cycle_id), tampered_cell, required=True),
            )
            tampered_migration = copy.deepcopy(first_item)
            tampered_migration["map_driver_migration_evidence"][
                "configured_map_limit_role"] = "DESIGN_RATED"
            self.assertIn(
                "invalid_map_driver_migration_evidence",
                production._map_driver_migration_evidence_reasons(
                    SimpleNamespace(cycle_id=cycle_id),
                    tampered_migration,
                    required=True,
                ),
            )
            tampered_link = copy.deepcopy(first_item)
            tampered_current = next(
                period for period in tampered_link["lifecycle_envelope_evidence"]["periods"]
                if period["period_id"] == cycle_id)
            tampered_map_row = next(
                row for row in tampered_current["utilization_coverage"]["rows"]
                if row["qualified_constraint_id"] == link["map_row"]["qualified_constraint_id"])
            tampered_map_row["provenance"] = migration["configured_map_limit_provenance"]
            self.assertIn(
                "map_driver_lifecycle_override_not_cleared",
                production._map_driver_lifecycle_link_reasons(
                    SimpleNamespace(cycle_id=cycle_id), tampered_link, required=True),
            )
            tampered_termination = copy.deepcopy(first_item)
            tampered_current = next(
                period for period in tampered_termination["lifecycle_envelope_evidence"]["periods"]
                if period["period_id"] == cycle_id)
            tampered_current["envelope_evidence"]["optimizer_evidence"][
                "termination_reason"] = "UNCONVERGED_PARTIAL_RESULT"
            self.assertIn(
                "lifecycle_period_1_invalid_optimizer_termination_reason",
                production._lifecycle_evidence_reasons(
                    SimpleNamespace(cycle_id=cycle_id), tampered_termination, required=True),
            )
            for field in (
                    "simulation_converged", "candidate_feasible", "candidate_finite",
                    "constraint_evidence_complete", "state_restore_complete",
                    "accepted_point_replayed", "actions_complete"):
                self.assertIs(True, optimizer[field], field)

            promoted = promote_cycle(task, cycle_id)
            self.assertEqual(cycle_id, promoted["source_cycle"])
            results = json.loads((task / "results.json").read_text(encoding="utf-8"))
            snapshot = json.loads((task / "continuous" / "baseline" /
                                   "results_snapshot.json").read_text(encoding="utf-8"))
            self.assertEqual(evidence, results["lifecycle_envelope_evidence"])
            self.assertEqual(migration, results["map_driver_migration_evidence"])
            self.assertEqual(results, snapshot)
            living_report = (task / "continuous" / "LIVING_REPORT.md").read_text(encoding="utf-8")
            self.assertIn(cycle_id, living_report)
            self.assertIn("Map-to-driver bottleneck timeline", living_report)
            self.assertIn(migration["configured_map_constraint_name"], living_report)
            self.assertIn(migration["driver_constraint_name"], living_report)
            self.assertIn("CONFIGURED_OPERATING", living_report)
            self.assertIn(migration["configured_map_limit_provenance"], living_report)
            self.assertIn("Accepted replay: map current / limit", living_report)
            self.assertIn(link["map_row"]["qualified_constraint_id"], living_report)
            self.assertIn(link["driver_row"]["qualified_constraint_id"], living_report)
            self.assertIn(optimizer["termination_reason"], living_report)

            report = render_report(task).read_text(encoding="utf-8")
            self.assertIn("Lifecycle Operating Envelope", report)
            self.assertIn("Early life", report)
            self.assertIn("Current life", report)
            self.assertIn("shared plant power", report.lower())
            self.assertIn("Declared accepted-replay constraint coverage", report)
            self.assertIn("PLANT_CONSTRAINT", report)
            self.assertIn("synthetic lifecycle separation limit", report)
            self.assertIn("Qualified paired sampled", report)
            self.assertIn("Portfolio rank 1/2", report)
            self.assertIn("Changed / compared cells", report)
            self.assertIn("Map-to-driver Bottleneck Migration", report)
            self.assertIn(migration["configured_map_constraint_name"], report)
            self.assertIn(migration["driver_constraint_name"], report)
            self.assertIn("same sampled endpoints", report)
            self.assertIn("Accepted-replay comparison", report)
            self.assertIn(link["map_row"]["qualified_constraint_id"], report)
            self.assertIn(link["driver_row"]["qualified_constraint_id"], report)
            self.assertIn("Validated optimization termination", report)
            self.assertIn(optimizer["termination_reason"], report)
            self.assertIn("advisory", report)

            revised, impact, stages = run_revised_cycle(task, 1.02)
            plan = load_plan(str(task))
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
            self.assertIn("script:optimize", stages)
            self.assertIn("guard", stages)
            self.assertIn("ledger", stages)
            self.assertNotIn("script:unchanged", stages)

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
            revised_migration = revised_item["map_driver_migration_evidence"]
            self.assertEqual(revised_cycle_id, revised_evidence["cycle_id"])
            self.assertEqual(revised_cycle_id, revised_migration["cycle_id"])
            self.assertNotEqual(migration["cycle_id"], revised_migration["cycle_id"])
            self.assertEqual(migration["configured_map_constraint_name"],
                             revised_migration["configured_map_constraint_name"])
            self.assertEqual(migration["driver_constraint_name"],
                             revised_migration["driver_constraint_name"])
            revised_living_report = (
                task / "continuous" / "LIVING_REPORT.md").read_text(encoding="utf-8")
            self.assertIn(cycle_id, revised_living_report)
            self.assertIn(revised_cycle_id, revised_living_report)
            self.assertIn("superseded; stale since {}".format(revised_cycle_id),
                          revised_living_report)
            self.assertIn(revised_migration["configured_map_constraint_name"],
                          revised_living_report)
            self.assertIn(revised_migration["driver_constraint_name"],
                          revised_living_report)
            revised_current = next(
                period for period in revised_evidence["periods"]
                if period["period_id"] == revised_cycle_id)
            revised_optimizer = revised_current["envelope_evidence"]["optimizer_evidence"]
            self.assertEqual(1.0e-8, revised_optimizer["replay_relative_tolerance"])
            self.assertEqual(
                "synthetic coupled network solver repeatability qualification",
                revised_optimizer["replay_tolerance_provenance"],
            )
            replay_scale = max(1.0, abs(first_accepted_objective),
                               abs(revised_optimizer["initial_objective_value"]))
            self.assertLessEqual(
                abs(first_accepted_objective
                    - revised_optimizer["initial_objective_value"]),
                revised_optimizer["replay_relative_tolerance"] * replay_scale,
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


class CompletedCycleLeaseTest(unittest.TestCase):
    """Keep the example's lock recovery limited to its own completed cycle."""

    def setUp(self):
        """Create one minimal living-task state directory."""
        self.temporary_directory = tempfile.TemporaryDirectory(
            prefix="neqsim-field-living-lock-")
        self.task = Path(self.temporary_directory.name)
        (self.task / "continuous").mkdir()
        self.lock_path = self.task / "continuous" / "LOCK"

    def tearDown(self):
        """Release the temporary task directory."""
        self.temporary_directory.cleanup()

    def write_lock(self, pid):
        """Write the lease fields consumed by the production lock."""
        self.lock_path.write_text(json.dumps({
            "host": "acceptance-host",
            "pid": pid,
            "expires": "2099-01-01T00:00:00+00:00",
        }), encoding="utf-8")

    def test_releases_only_own_completed_cycle_lease(self):
        """Remove an exact completed-process lease and report the cleanup."""
        self.write_lock(os.getpid())

        released = release_own_completed_cycle_lease(
            self.task, {"status": "complete"})

        self.assertIs(True, released)
        self.assertFalse(self.lock_path.exists())

    def test_rejects_foreign_or_incomplete_cycle_lease(self):
        """Never weaken locking for another process or an incomplete run."""
        for pid, status in ((os.getpid() + 1, "complete"),
                            (os.getpid(), "running")):
            with self.subTest(pid=pid, status=status):
                self.write_lock(pid)
                with self.assertRaisesRegex(RuntimeError, "Refusing to clear"):
                    release_own_completed_cycle_lease(
                        self.task, {"status": status})
                self.assertTrue(self.lock_path.exists())


if __name__ == "__main__":
    unittest.main(verbosity=2)


