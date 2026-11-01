"""Independent regression tests for the concept plant-layout study capability.

Run with the selected interpreter: ``python devtools/test_layout_study.py -q``.
The suite uses standard-library unittest and requires
no JVM, optimizer, CAD kernel or package installation for the core checks.
"""

from __future__ import annotations

import copy
import csv
import hashlib
import importlib.util
import json
import math
from pathlib import Path
import subprocess
import struct
import sys
import tempfile
import tomllib
import unittest
from unittest import mock

DEVTOOLS = Path(__file__).resolve().parent
sys.path.insert(0, str(DEVTOOLS))

import layout_engine  # noqa: E402
import layout_examples  # noqa: E402
import layout_inventory  # noqa: E402
import layout_model  # noqa: E402
import layout_process  # noqa: E402
import layout_study  # noqa: E402


def fixture():
    """A small project-basis fixture with independently measurable geometry."""
    return {
        "schema_version": "plant_layout_study.v1",
        "project": {
            "id": "TEST-PLANT", "title": "Independent layout regression",
            "revision": "A", "maturity": "SCREENING",
            "facility_type": "onshore", "jurisdiction": "synthetic",
        },
        "coordinate_system": {
            "length_unit": "m", "mass_unit": "kg", "origin": [0, 0, 0],
            "north_deg": 0, "elevation_datum": "project",
        },
        "decks": [{"id": "D1", "width_m": 20, "depth_m": 20,
                   "elevation_m": 0, "height_limit_m": 10}],
        "equipment": [
            {"tag": tag, "kind": "package", "size_m": [2, 2, 2],
             "mass_kg": 10, "allowed_decks": ["D1"], "rotations_deg": [0, 90],
             "dimension_source": {"kind": "synthetic_assumption",
                                  "reference": "Independent synthetic test basis"},
             "envelopes": []}
            for tag in ("A", "B")
        ],
        "connections": [{"id": "L1", "from_tag": "A", "to_tag": "B",
                         "service": "synthetic gas"}],
        "corridors": [{"id": "E1", "deck": "D1", "bounds_m": [0, 0, 20, 2],
                       "kind": "escape", "source": "Synthetic test corridor"}],
        "exits": [{"id": "X1", "deck": "D1", "x_m": 0, "y_m": 1},
                  {"id": "X2", "deck": "D1", "x_m": 20, "y_m": 1}],
        "obstacles": [], "rules": [], "standards": [],
        "assumptions": ["All dimensions and criteria are synthetic test data."],
        "data_gaps": [],
        "options": {"alternatives": 3, "grid_m": 1, "max_candidates": 10000,
                    "backend": "deterministic"},
    }


def placement(tag, x, y, deck="D1", rotation=0):
    """Declare a placement without reusing the optimizer's calculations."""
    return {"tag": tag, "deck": deck, "x_m": x, "y_m": y,
            "rotation_deg": rotation}


def checks(study, positions):
    """Run the independent verifier on manually specified positions."""
    return layout_engine.check_layout(
        layout_model.normalize_study(study), {"id": "MANUAL", "placements": positions})


def project_source():
    """A declared synthetic requirement, not a fabricated standards clause."""
    return {"kind": "project_requirement", "reference": "Synthetic test basis R1",
            "access_status": "verified_full_text"}


def physical_source(status="CONTROLLED_PROJECT_INPUT"):
    """A controlled synthetic physical-input source for inventory tests."""
    return {"kind": "synthetic_assumption", "reference": "Synthetic route and inventory basis",
            "revision": "A", "evidence_status": status}


def inventory_fixture():
    """Return a complete synthetic physical route and isolation inventory."""
    study = fixture()
    study["physical_inventory_scope"] = {
        "id": "TEST-INVENTORY", "revision": "A", "source_reference": "Synthetic scope A",
        "required_connection_ids": ["L1"], "required_equipment_tags": ["A"],
        "required_isolation_section_ids": ["ISO-1"],
    }
    study["physical_routes"] = [{
        "id": "ROUTE-1", "connection_id": "L1", "line_tag": "L-100",
        "internal_diameter_m": 2.0,
        "centerline_m": [[0, 0, 0], [3, 4, 0], [3, 4, 12]],
        "source": physical_source(),
    }]
    study["equipment_volumes"] = [{
        "id": "A-INTERNAL", "equipment_tag": "A", "volume_m3": 3.0,
        "source": physical_source(),
    }]
    study["isolation_devices"] = [
        {"id": "ISO-A", "tag": "XV-100A", "route_id": "ROUTE-1", "chainage_m": 0.0,
         "device_type": "emergency_shutdown_valve", "normal_state": "OPEN",
         "fail_state": "CLOSED", "source": physical_source()},
        {"id": "ISO-B", "tag": "XV-100B", "route_id": "ROUTE-1", "chainage_m": 17.0,
         "device_type": "emergency_shutdown_valve", "normal_state": "OPEN",
         "fail_state": "CLOSED", "source": physical_source()},
    ]
    study["isolation_sections"] = [{
        "id": "ISO-1", "route_ids": ["ROUTE-1"],
        "equipment_volume_ids": ["A-INTERNAL"], "boundary_device_ids": ["ISO-A", "ISO-B"],
        "source": physical_source(),
    }]
    return study


class LayoutModelTests(unittest.TestCase):
    def test_normalization_owns_data_and_fingerprint_tracks_geometry(self):
        original = fixture()
        frozen = copy.deepcopy(original)
        normalized = layout_model.normalize_study(original)
        self.assertEqual(original, frozen)
        self.assertIsNot(normalized, original)
        before = layout_model.study_fingerprint(normalized)
        normalized["equipment"][0]["size_m"][0] += 1
        self.assertEqual(original, frozen)
        self.assertNotEqual(before, layout_model.study_fingerprint(normalized))

    def test_non_si_units_cannot_be_silently_interpreted_as_metres(self):
        study = fixture()
        study["coordinate_system"]["length_unit"] = "mm"
        with self.assertRaises(ValueError):
            layout_model.normalize_study(study)

    def test_duplicate_equipment_tags_are_rejected(self):
        study = fixture()
        study["equipment"][1]["tag"] = "A"
        with self.assertRaises(ValueError):
            layout_model.normalize_study(study)

    def test_nonfinite_or_nonpositive_dimensions_are_rejected(self):
        for value in (math.nan, math.inf, -math.inf, 0, -1):
            with self.subTest(value=value):
                study = fixture()
                study["equipment"][0]["size_m"][0] = value
                with self.assertRaises(ValueError):
                    layout_model.normalize_study(study)

    def test_unknown_allowed_deck_is_rejected(self):
        study = fixture()
        study["equipment"][0]["allowed_decks"] = ["MISSING"]
        with self.assertRaises(ValueError):
            layout_model.normalize_study(study)

    def test_envelope_identity_is_unique_across_equipment(self):
        study = fixture()
        for item in study["equipment"]:
            item["envelopes"] = [{"id": "DUPLICATED-ENVELOPE", "kind": "maintenance",
                                  "bounds_m": [0, 0, 0, 2, 2, 2]}]
        with self.assertRaises(ValueError):
            layout_model.normalize_study(study)

    def test_rotated_physical_and_withdrawal_bounds_use_one_transform(self):
        study = fixture()
        item = study["equipment"][0]
        item["size_m"] = [4, 2, 3]
        deck = study["decks"][0]
        deck["elevation_m"] = 5
        pos = placement("A", 10, 20, rotation=90)
        physical = layout_model.equipment_bounds(item, pos, deck)
        self.assertEqual(list(physical), [10, 20, 5, 12, 24, 8])
        envelope = {"kind": "maintenance", "bounds_m": [4, 0, 0, 7, 2, 3]}
        withdrawal = layout_model.equipment_bounds(item, pos, deck, envelope)
        self.assertEqual(list(withdrawal), [10, 24, 5, 12, 27, 8])


class PhysicalInventoryTests(unittest.TestCase):
    def test_routed_length_and_isolated_volume_match_analytical_geometry(self):
        study = layout_model.normalize_study(inventory_fixture())
        inventory = layout_inventory.build_inventory(study)
        self.assertEqual(inventory["status"], "COMPLETE")
        self.assertTrue(inventory["ready_for_dynamic_safeguarding_design"])
        self.assertAlmostEqual(inventory["routes"][0]["length_m"], 17.0, places=12)
        self.assertAlmostEqual(inventory["routes"][0]["pipe_volume_m3"], 17.0 * math.pi, places=12)
        section = inventory["isolation_sections"][0]
        self.assertAlmostEqual(section["total_isolated_volume_m3"], 17.0 * math.pi + 3.0, places=12)
        self.assertFalse(inventory["blockers"])

    def test_inventory_changes_invalidate_the_study_fingerprint(self):
        study = layout_model.normalize_study(inventory_fixture())
        before = layout_model.study_fingerprint(study)
        study["physical_routes"][0]["centerline_m"][1][0] += 0.25
        self.assertNotEqual(before, layout_model.study_fingerprint(study))

    def test_declared_missing_inventory_remains_explicit_blockers(self):
        study = fixture()
        study["physical_inventory_scope"] = {
            "id": "MISSING-EVIDENCE", "revision": "A", "source_reference": "Synthetic scope A",
            "required_connection_ids": ["L1"], "required_equipment_tags": ["A"],
            "required_isolation_section_ids": ["ISO-MISSING"],
        }
        inventory = layout_inventory.build_inventory(layout_model.normalize_study(study))
        self.assertEqual(inventory["status"], "INCOMPLETE")
        self.assertFalse(inventory["ready_for_dynamic_safeguarding_design"])
        self.assertEqual({item["code"] for item in inventory["blockers"]}, {
            "LAYOUT-PIPE-ROUTE-MISSING", "LAYOUT-EQUIPMENT-VOLUME-MISSING",
            "LAYOUT-ISOLATION-SECTION-MISSING",
        })

    def test_uncontrolled_required_route_is_a_blocker_not_a_silent_pass(self):
        study = inventory_fixture()
        study["physical_routes"][0]["source"] = physical_source("REVIEW_REQUIRED")
        inventory = layout_inventory.build_inventory(layout_model.normalize_study(study))
        self.assertEqual(inventory["status"], "INCOMPLETE")
        self.assertIn("LAYOUT-PIPE-ROUTE-REVIEW",
                      {item["code"] for item in inventory["blockers"]})

    def test_unknown_boundary_fail_state_remains_a_design_blocker(self):
        study = inventory_fixture()
        study["isolation_devices"][1]["fail_state"] = "UNKNOWN"
        inventory = layout_inventory.build_inventory(layout_model.normalize_study(study))
        self.assertEqual(inventory["status"], "INCOMPLETE")
        self.assertIn("LAYOUT-ISOLATION-FAIL-STATE-MISSING",
                      {item["code"] for item in inventory["blockers"]})

    def test_device_chainage_cannot_exceed_supplied_route(self):
        study = inventory_fixture()
        study["isolation_devices"][1]["chainage_m"] = 17.01
        with self.assertRaises(ValueError):
            layout_model.normalize_study(study)

    def test_isolation_boundaries_must_belong_to_the_section_route_set(self):
        study = inventory_fixture()
        study["connections"].append({"id": "L2", "from_tag": "A", "to_tag": "B"})
        study["physical_routes"].append({
            "id": "ROUTE-2", "connection_id": "L2", "line_tag": "L-200",
            "internal_diameter_m": 0.2, "centerline_m": [[0, 0, 0], [1, 0, 0]],
            "source": physical_source(),
        })
        study["isolation_devices"][1]["route_id"] = "ROUTE-2"
        with self.assertRaises(ValueError):
            layout_model.normalize_study(study)

    def test_study_delivery_retains_inventory_json_csv_and_report_evidence(self):
        with tempfile.TemporaryDirectory() as directory:
            summary = layout_study.run_study(inventory_fixture(), directory, stage="all", no_cad=True)
            self.assertEqual(summary["physical_inventory_status"], "COMPLETE")
            self.assertTrue(summary["physical_inventory_ready_for_dynamic_safeguarding_design"])
            for key in ("physical_inventory", "physical_routes", "isolation_sections"):
                self.assertTrue(Path(summary["tables"][key]).is_file())
            html_report = Path(summary["artifacts"]["report"]["html"]).read_text(encoding="utf-8")
            self.assertIn("Physical piping and isolation inventory", html_report)
            self.assertIn("ISO-1", html_report)


class LayoutCheckTests(unittest.TestCase):
    def assertFinding(self, findings, kind, status):
        self.assertTrue(any(f["kind"] == kind and f["status"] == status for f in findings),
                        (kind, status, findings))

    def assertNoFailure(self, findings, kind):
        self.assertFalse(any(f["kind"] == kind and f["status"] == "FAIL" for f in findings),
                         (kind, findings))

    def test_deliberate_physical_clash_is_detected(self):
        found = checks(fixture(), [placement("A", 4, 4), placement("B", 5, 5)])
        self.assertFinding(found, "physical_clash", "FAIL")

    def test_touching_faces_do_not_become_a_volume_collision(self):
        found = checks(fixture(), [placement("A", 4, 4), placement("B", 6, 4)])
        self.assertNoFailure(found, "physical_clash")

    def test_same_plan_coordinates_on_separate_decks_are_not_a_clash(self):
        study = fixture()
        study["decks"].append({"id": "D2", "width_m": 20, "depth_m": 20,
                               "elevation_m": 4})
        study["equipment"][1]["allowed_decks"] = ["D2"]
        found = checks(study, [placement("A", 4, 4), placement("B", 4, 4, "D2")])
        self.assertNoFailure(found, "physical_clash")

    def test_deck_identity_does_not_hide_true_vertical_collision(self):
        study = fixture()
        study["decks"].append({"id": "D2", "width_m": 20, "depth_m": 20,
                               "elevation_m": 1})
        study["equipment"][1]["allowed_decks"] = ["D2"]
        found = checks(study, [placement("A", 4, 4), placement("B", 4, 4, "D2")])
        self.assertFinding(found, "physical_clash", "FAIL")

    def test_maintenance_volume_is_checked_against_other_equipment(self):
        study = fixture()
        study["equipment"][0]["envelopes"] = [
            {"kind": "maintenance", "bounds_m": [2, 0, 0, 5, 2, 2]}]
        found = checks(study, [placement("A", 4, 4), placement("B", 7, 4)])
        self.assertNoFailure(found, "physical_clash")
        self.assertFinding(found, "envelope_clash", "FAIL")

    def test_equipment_does_not_collide_with_its_own_envelope(self):
        study = fixture()
        study["equipment"][0]["envelopes"] = [
            {"kind": "operation", "bounds_m": [-1, -1, 0, 3, 3, 3]}]
        found = checks(study, [placement("A", 4, 4), placement("B", 12, 12)])
        self.assertNoFailure(found, "envelope_clash")

    def test_reserved_escape_corridor_obstruction_fails(self):
        found = checks(fixture(), [placement("A", 4, 0), placement("B", 12, 12)])
        self.assertFinding(found, "corridor_obstruction", "FAIL")

    def test_corridor_outside_its_deck_cannot_pass_escape_connectivity(self):
        study = fixture()
        study["corridors"][0]["bounds_m"] = [-1, 0, 20, 2]
        found = checks(study, [placement("A", 4, 4), placement("B", 12, 12)])
        self.assertFinding(found, "corridor_boundary", "FAIL")
        self.assertFinding(found, "escape_connectivity", "FAIL")

    def test_reserved_escape_corridor_is_checked_against_fixed_obstacles(self):
        study = fixture()
        study["obstacles"] = [{"id": "WALL", "deck": "D1",
                               "bounds_m": [4, 0, 0, 5, 2, 3]}]
        found = checks(study, [placement("A", 4, 4), placement("B", 12, 12)])
        self.assertNoFailure(found, "physical_clash")
        self.assertFinding(found, "corridor_obstruction", "FAIL")

    def test_envelope_above_or_below_deck_volume_fails(self):
        for z_min, z_max in ((-1, 1), (9, 11)):
            with self.subTest(z_min=z_min, z_max=z_max):
                study = fixture()
                study["equipment"][0]["envelopes"] = [
                    {"kind": "lifting", "bounds_m": [0, 0, z_min, 2, 2, z_max]}]
                found = checks(study, [placement("A", 4, 4), placement("B", 12, 12)])
                self.assertFinding(found, "envelope_boundary", "FAIL")

    def test_outside_deck_and_missing_equipment_are_not_valid_layouts(self):
        outside = checks(fixture(), [placement("A", 19, 4), placement("B", 12, 12)])
        self.assertFinding(outside, "deck_boundary", "FAIL")
        missing = checks(fixture(), [placement("A", 4, 4)])
        self.assertFinding(missing, "placement", "FAIL")

    def test_fixed_equipment_cannot_move_during_verification(self):
        study = fixture()
        study["equipment"][0]["fixed"] = placement("A", 4, 4)
        found = checks(study, [placement("A", 5, 4), placement("B", 12, 12)])
        self.assertFinding(found, "fixed_placement", "FAIL")

    def test_supplied_deck_mass_limit_uses_sum_of_equipment_mass(self):
        study = fixture()
        study["decks"][0]["mass_limit_kg"] = 15
        found = checks(study, [placement("A", 4, 4), placement("B", 12, 12)])
        self.assertFinding(found, "deck_mass", "FAIL")

    def test_minimum_distance_uses_footprint_edges_and_source_gate(self):
        study = fixture()
        study["rules"] = [{"id": "SEP", "kind": "minimum_separation", "required": True,
                           "from_tag": "A", "to_tag": "B", "minimum_m": 3,
                           "source": project_source()}]
        # A occupies x=4..6 and B x=8..10, so edge separation is 2 m.
        found = checks(study, [placement("A", 4, 4), placement("B", 8, 4)])
        self.assertFinding(found, "minimum_separation", "FAIL")
        study["rules"][0]["source"] = {
            "kind": "standard", "reference": "Uninspected catalogue metadata",
            "access_status": "metadata_only", "edition": "2024"}
        found = checks(study, [placement("A", 4, 4), placement("B", 8, 4)])
        self.assertFinding(found, "minimum_separation", "NOT_EVALUATED")
        self.assertNoFailure(found, "minimum_separation")

    def test_inapplicable_rule_is_explicitly_not_applicable(self):
        study = fixture()
        study["rules"] = [{"id": "OFFSHORE-ONLY", "kind": "minimum_separation",
                           "required": True, "from_tag": "A", "to_tag": "B",
                           "minimum_m": 50, "source": project_source(),
                           "applicability": {"facility_types": ["offshore_floating"]}}]
        found = checks(study, [placement("A", 4, 4), placement("B", 8, 4)])
        self.assertFinding(found, "minimum_separation", "NOT_APPLICABLE")

    def test_required_evidence_cannot_pass_from_source_metadata_alone(self):
        study = fixture()
        study["rules"] = [{"id": "QRA", "kind": "required_evidence", "required": True,
                           "source": project_source()}]
        found = checks(study, [placement("A", 4, 4), placement("B", 12, 12)])
        self.assertFinding(found, "required_evidence", "NOT_EVALUATED")


class LayoutSolverTests(unittest.TestCase):
    def test_repeated_solution_preserves_placements_and_input(self):
        study = layout_model.normalize_study(fixture())
        original = copy.deepcopy(study)
        first = layout_engine.generate_layouts(study)
        second = layout_engine.generate_layouts(study)
        self.assertEqual(study, original)
        self.assertTrue(first)
        self.assertEqual([a["placements"] for a in first],
                         [a["placements"] for a in second])
        for alternative in first:
            self.assertNotEqual(alternative["status"], "INFEASIBLE")
            self.assertFalse(any(c["required"] and c["status"] == "FAIL"
                                 for c in alternative["checks"]), alternative)

    def test_fixed_placement_is_preserved_by_solver(self):
        study = fixture()
        fixed = placement("A", 10, 10, rotation=90)
        study["equipment"][0]["fixed"] = fixed
        alternatives = layout_engine.generate_layouts(layout_model.normalize_study(study))
        self.assertTrue(alternatives)
        for alternative in alternatives:
            actual = next(p for p in alternative["placements"] if p["tag"] == "A")
            for key in ("deck", "x_m", "y_m", "rotation_deg"):
                self.assertEqual(actual[key], fixed[key])

    def test_impossible_equipment_never_reports_feasible(self):
        study = fixture()
        study["equipment"][0]["size_m"] = [30, 30, 2]
        alternatives = layout_engine.generate_layouts(layout_model.normalize_study(study))
        self.assertTrue(alternatives, "Infeasibility must have an explicit result.")
        self.assertTrue(all(a["status"] == "INFEASIBLE" for a in alternatives))

    def test_envelope_height_limit_is_a_placement_constraint(self):
        study = fixture()
        study["equipment"][0]["envelopes"] = [
            {"kind": "lifting", "bounds_m": [0, 0, 0, 2, 2, 11]}]
        alternatives = layout_engine.generate_layouts(layout_model.normalize_study(study))
        self.assertTrue(alternatives)
        self.assertTrue(all(a["status"] == "INFEASIBLE" for a in alternatives))


class LayoutProcessTests(unittest.TestCase):
    def process_data(self):
        return {"equipment": [{"tag": "A", "type": "Separator"},
                               {"tag": "B", "type": "Compressor"}],
                "streams": [{"id": "S-feed", "producers": [], "consumers": ["A"]},
                            {"id": "S-gas", "producers": ["A"], "consumers": ["B"]},
                            {"id": "S-export", "producers": ["B"], "consumers": []}]}

    def test_process_import_preserves_tags_topology_and_input_identity(self):
        study = fixture()
        source = self.process_data()
        before_study, before_source = copy.deepcopy(study), copy.deepcopy(source)
        actual = layout_process.import_process(source, study)
        self.assertEqual(study, before_study)
        self.assertEqual(source, before_source)
        self.assertEqual({u["tag"] for u in actual["equipment"]}, {"A", "B"})
        self.assertEqual({(c["from_tag"], c["to_tag"]) for c in actual["connections"]},
                         {("A", "B")})
        self.assertEqual(actual["equipment"][0]["size_m"], [2, 2, 2])

    def test_json_process_import_has_same_connectivity_as_mapping(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "process_data.json"
            path.write_text(json.dumps(self.process_data()), encoding="utf-8")
            direct = layout_process.import_process(self.process_data(), fixture())
            from_file = layout_process.import_process(str(path), fixture())
            def topology(result):
                return [(c["id"], c["from_tag"], c["to_tag"], c["service"])
                        for c in result["connections"]]
            self.assertEqual(topology(direct), topology(from_file))

    def test_unmapped_process_equipment_cannot_be_silently_lost(self):
        source = self.process_data()
        source["equipment"].append({"tag": "UNMAPPED", "type": "Pump"})
        with self.assertRaises(ValueError):
            layout_process.import_process(source, fixture())

    def mechanical_design(self, complete=True, length=4):
        summary = mock.Mock()
        summary.getName.return_value = "A"
        summary.getLength.return_value = length
        summary.getWidth.return_value = 3
        summary.getHeight.return_value = 2
        summary.getWeight.return_value = 50
        calculation = mock.Mock()
        calculation.isComplete.return_value = complete
        optional = mock.Mock()
        optional.isPresent.return_value = True
        optional.get.return_value = calculation
        design = mock.Mock()
        design.getLastCalculationResult.return_value = optional
        design.getEquipmentList.return_value = [summary]
        return design

    def test_mechanical_import_uses_numeric_getters_and_preliminary_provenance(self):
        study = fixture()
        design = self.mechanical_design()
        result = layout_process.import_mechanical_design(design, study)
        item = next(u for u in result["equipment"] if u["tag"] == "A")
        self.assertEqual(item["size_m"], [4, 3, 2])
        self.assertEqual(item["mass_kg"], 50)
        self.assertEqual(item["dimension_source"]["kind"], "calculated")
        self.assertEqual(item["dimension_source"]["maturity"], "PRELIMINARY_ESTIMATE")
        self.assertEqual(study["equipment"][0]["size_m"], [2, 2, 2])
        self.assertTrue(result["data_gaps"])
        design.toJson.assert_not_called()

    def test_incomplete_or_unset_mechanical_dimensions_are_rejected(self):
        for design in (self.mechanical_design(complete=False),
                       self.mechanical_design(length=0)):
            with self.subTest(design=design):
                with self.assertRaises(ValueError):
                    layout_process.import_mechanical_design(design, fixture())

    def test_live_import_does_not_reflash_for_placement(self):
        fake_extractor = mock.Mock()
        fake_extractor.extract_process.return_value = self.process_data()
        process = mock.Mock(spec=["getRunStatus"])
        run_status = mock.Mock()
        run_status.isCompleted.return_value = True
        run_status.isSuccess.return_value = True
        run_status.isConverged.return_value = True
        process.getRunStatus.return_value = run_status
        with mock.patch.dict(sys.modules, {"psr_extract": fake_extractor}):
            result = layout_process.import_live_process(process, fixture(), case_id="NORMAL")
        fake_extractor.extract_process.assert_called_once_with(
            process, case_id="NORMAL", std_flash=False)
        self.assertEqual(result["process_import"]["equipment_tags"], ["A", "B"])

    def test_live_import_rejects_unsolved_process_before_extraction(self):
        fake_extractor = mock.Mock()
        process = mock.Mock(spec=["getRunStatus"])
        process.getRunStatus.return_value.isCompleted.return_value = False
        with mock.patch.dict(sys.modules, {"psr_extract": fake_extractor}):
            with self.assertRaises(ValueError):
                layout_process.import_live_process(process, fixture())
        fake_extractor.extract_process.assert_not_called()

    def test_old_calculated_geometry_is_not_current_mechanical_result_coverage(self):
        study = fixture()
        for item in study["equipment"]:
            item["dimension_source"] = {"kind": "calculated",
                                         "reference": "Stale preliminary sizing from an older run"}
        process = mock.Mock(spec=["getRunStatus"])
        run_status = mock.Mock()
        run_status.isCompleted.return_value = True
        run_status.isSuccess.return_value = True
        run_status.isConverged.return_value = True
        process.getRunStatus.return_value = run_status
        extractor = mock.Mock()
        extractor.extract_process.return_value = self.process_data()
        # The current result covers A only. B's old calculated label is not coverage.
        with mock.patch.dict(sys.modules, {"psr_extract": extractor}):
            with self.assertRaises(ValueError):
                layout_process.import_live_process(
                    process, study, mechanical_design=self.mechanical_design())


class LayoutStudyIntegrationTests(unittest.TestCase):
    def test_demo_models_are_fresh_and_offshore_has_three_decks(self):
        onshore = layout_examples.demo_study("onshore")
        frozen = copy.deepcopy(onshore)
        onshore["equipment"][0]["size_m"][0] += 1
        self.assertEqual(layout_examples.demo_study("onshore"), frozen)
        offshore = layout_examples.demo_study("offshore")
        self.assertEqual(len(offshore["decks"]), 3)
        for study in (frozen, offshore):
            self.assertTrue(layout_model.normalize_study(study))
            self.assertTrue(study["assumptions"])
            self.assertTrue(study["data_gaps"])

    def test_stage_restart_rejects_changed_source_geometry(self):
        with tempfile.TemporaryDirectory() as directory:
            study = layout_examples.demo_study("onshore")
            layout_study.run_study(study, directory, stage="place", no_cad=True)
            changed = copy.deepcopy(study)
            changed["equipment"][0]["size_m"][0] += 0.5
            with self.assertRaises(ValueError):
                layout_study.run_study(changed, directory, stage="check", no_cad=True)

    def test_stage_restart_rejects_tampered_retained_placements(self):
        with tempfile.TemporaryDirectory() as directory:
            study = layout_examples.demo_study("onshore")
            layout_study.run_study(study, directory, stage="place", no_cad=True)
            path = Path(directory) / "dataset" / "plant_layout.json"
            retained = json.loads(path.read_text(encoding="utf-8"))
            retained["alternatives"][0]["placements"][0]["x_m"] += 1
            path.write_text(json.dumps(retained), encoding="utf-8")
            with self.assertRaises(ValueError):
                layout_study.run_study(study, directory, stage="check", no_cad=True)

    def test_end_to_end_package_contains_data_checks_drawings_and_report(self):
        with tempfile.TemporaryDirectory() as directory:
            study = layout_examples.demo_study("onshore")
            summary = layout_study.run_study(study, directory, stage="all", no_cad=True)
            self.assertIsInstance(summary, dict)
            dataset = Path(directory) / "dataset" / "plant_layout.json"
            self.assertTrue(dataset.is_file())
            data = json.loads(dataset.read_text(encoding="utf-8"))
            self.assertEqual(data["meta"]["study_fingerprint"],
                             layout_model.study_fingerprint(layout_model.normalize_study(study)))
            self.assertEqual(len(data["alternatives"]), 3)
            self.assertEqual(len({json.dumps(a["placements"], sort_keys=True)
                                  for a in data["alternatives"]}), 3)
            self.assertTrue(all(a["status"] == "INCOMPLETE" for a in data["alternatives"]),
                            "Synthetic examples retain missing safety qualification.")
            self.assertFalse(any(c["status"] == "FAIL" and c["required"]
                                 for a in data["alternatives"] for c in a["checks"]))
            coordinates = Path(directory) / "dataset" / "coordinates.csv"
            findings = Path(directory) / "dataset" / "checks.csv"
            with coordinates.open(encoding="utf-8", newline="") as handle:
                rows = list(csv.DictReader(handle))
            self.assertEqual(len(rows), 3 * len(study["equipment"]))
            self.assertTrue(findings.is_file())
            self.assertTrue(list(Path(directory).rglob("*.svg")))
            self.assertTrue(list(Path(directory).rglob("*.html")))
            report = summary["artifacts"]["report"]
            drawings = summary["artifacts"]["drawings"]
            if importlib.util.find_spec("reportlab"):
                self.assertTrue(Path(report["pdf"]).is_file())
            else:
                self.assertIsNone(report["pdf"])
                self.assertEqual(summary["delivery_status"], "INCOMPLETE")
            if importlib.util.find_spec("pymupdf") or importlib.util.find_spec("fitz"):
                self.assertTrue(Path(drawings["pdf"]).is_file())
            else:
                self.assertIsNone(drawings["pdf"])
                self.assertEqual(summary["delivery_status"], "INCOMPLETE")
            for pdf in Path(directory).rglob("*.pdf"):
                self.assertEqual(pdf.read_bytes()[:4], b"%PDF")
            manifest = json.loads((Path(directory) / "artifact_manifest.json").read_text(
                encoding="utf-8"))
            self.assertTrue(manifest["files"])
            for entry in manifest["files"]:
                artifact = Path(directory) / entry["path"]
                self.assertTrue(artifact.is_file())
                self.assertEqual(hashlib.sha256(artifact.read_bytes()).hexdigest(), entry["sha256"])
                self.assertEqual(artifact.stat().st_size, entry["bytes"])

    def test_cli_is_dispatched_by_unified_entrypoint(self):
        process = subprocess.run(
            [sys.executable, str(DEVTOOLS / "neqsim_cli.py"), "layout-study", "--help"],
            capture_output=True, text=True, check=False)
        self.assertEqual(process.returncode, 0, process.stderr)
        self.assertIn("--demo", process.stdout)

    def test_cli_strict_mode_preserves_incomplete_result_and_nonzero_status(self):
        with tempfile.TemporaryDirectory() as directory:
            status = layout_study.main(["--demo", "onshore", "--stage", "place",
                                        "--out", directory, "--strict", "--no-cad"])
            self.assertEqual(status, 1)
            self.assertTrue((Path(directory) / "dataset" / "plant_layout.json").is_file())

    def test_missing_requested_report_pdf_is_delivery_incomplete_even_when_geometry_passes(self):
        import layout_report

        study = fixture()
        study["decks"][0]["mass_limit_kg"] = 1000

        def report_without_pdf(spec, alternatives, out_dir, drawings=None, cad=None):
            report_dir = Path(out_dir) / "reports"
            report_dir.mkdir(parents=True, exist_ok=True)
            path = report_dir / "plant_layout_report.html"
            path.write_text("<html><body>Current report: PDF adapter unavailable</body></html>",
                            encoding="utf-8")
            return {"html": str(path), "pdf": None, "docx": None, "files": [],
                    "warnings": ["PDF adapter unavailable"], "status": "INCOMPLETE"}

        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / "study.json"
            source.write_text(json.dumps(study), encoding="utf-8")
            output = Path(directory) / "output"
            with mock.patch.object(layout_report, "build_report", side_effect=report_without_pdf):
                status = layout_study.main([str(source), "--out", str(output),
                                            "--stage", "all", "--strict", "--no-cad"])
            self.assertEqual(status, 1)
            summary = json.loads((output / "summary.json").read_text(encoding="utf-8"))
            self.assertEqual(summary["status"], "FEASIBLE")
            self.assertEqual(summary["delivery_status"], "INCOMPLETE")
            self.assertIsNone(summary["artifacts"]["report"]["pdf"])

    def test_all_runtime_modules_are_in_installed_distribution(self):
        project = tomllib.loads((DEVTOOLS / "pyproject.toml").read_text(encoding="utf-8"))
        modules = set(project["tool"]["setuptools"]["py-modules"])
        expected = {"layout_model", "layout_engine", "layout_process", "layout_inventory", "layout_examples",
                    "layout_study", "layout_drawings", "layout_report", "layout_cad"}
        self.assertTrue(expected <= modules, expected - modules)


class LayoutReportFreshnessTests(unittest.TestCase):
    def test_unavailable_builders_cannot_publish_old_or_partial_pdf_and_docx(self):
        import layout_report

        study = layout_model.normalize_study(fixture())
        alternatives = layout_engine.generate_layouts(study)

        def unavailable_builder(path, *args, **kwargs):
            self.assertFalse(Path(path).exists(), "Previous report must be retired before generation.")
            Path(path).write_bytes(b"partial artifact from failed generation")
            raise ImportError("Synthetic missing report adapter")

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory) / "reports"
            root.mkdir()
            pdf = root / "plant_layout_report.pdf"
            docx = root / "plant_layout_report.docx"
            pdf.write_bytes(b"old PDF from a different study")
            docx.write_bytes(b"old DOCX from a different study")
            with mock.patch.object(layout_report, "_pdf_report", side_effect=unavailable_builder):
                with mock.patch.object(layout_report, "_docx_report", side_effect=unavailable_builder):
                    manifest = layout_report.build_report(study, alternatives, directory)
            self.assertIsNone(manifest["pdf"])
            self.assertIsNone(manifest["docx"])
            self.assertFalse(pdf.exists())
            self.assertFalse(docx.exists())
            self.assertEqual(manifest["status"], "INCOMPLETE")
            published = {Path(item["path"]) for item in manifest["files"]}
            self.assertNotIn(pdf, published)
            self.assertNotIn(docx, published)
            self.assertTrue(Path(manifest["html"]).is_file())


@unittest.skipUnless(importlib.util.find_spec("cadquery") and importlib.util.find_spec("ezdxf"),
                     "Optional CadQuery/ezdxf dependencies are not installed")
class LayoutCadExchangeTests(unittest.TestCase):
    def test_cad_round_trip_keeps_rotated_dimensions_units_and_tags(self):
        """Reopen exports with CAD readers and independently specified dimensions."""
        import cadquery as cq
        import ezdxf
        import layout_cad

        study = fixture()
        study["equipment"][0]["size_m"] = [4, 2, 3]
        study = layout_model.normalize_study(study)
        alternative = {"id": "CAD-TEST", "label": "Independent exchange test",
                       "placements": [placement("A", 4, 4, rotation=90),
                                      placement("B", 12, 12)]}
        with tempfile.TemporaryDirectory() as directory:
            manifest = layout_cad.export_cad(study, [alternative], directory)
            self.assertFalse([c for c in manifest["checks"] if c["status"] == "FAIL"],
                             manifest)
            formats = {entry["format"]: Path(entry["path"]) for entry in manifest["files"]}
            self.assertTrue({"STEP", "GLB", "DXF"} <= set(formats), manifest)
            step = cq.importers.importStep(str(formats["STEP"]))
            observed = []
            for solid in step.solids().vals():
                bounds = solid.BoundingBox()
                observed.append((bounds.xmin, bounds.ymin, bounds.zmin,
                                 bounds.xmax, bounds.ymax, bounds.zmax))
            expected = [(0, 0, -100, 20000, 20000, 0),
                        (4000, 4000, 0, 6000, 8000, 3000),
                        (12000, 12000, 0, 14000, 14000, 2000)]
            self.assertEqual(len(observed), len(expected))
            for actual, target in zip(sorted(observed), sorted(expected)):
                for a, b in zip(actual, target):
                    self.assertAlmostEqual(a, b, delta=0.05)  # STEP coordinates are mm.
            dxf = ezdxf.readfile(formats["DXF"])
            self.assertEqual(dxf.units, 6)  # DXF metres, per published INSUNITS enum.
            tagged = {}
            for entity in dxf.modelspace().query("LWPOLYLINE"):
                if entity.has_xdata("NEQSIM_LAYOUT"):
                    tag = entity.get_xdata("NEQSIM_LAYOUT")[0].value
                    points = list(entity.get_points("xy"))
                    tagged[tag] = (min(p[0] for p in points), min(p[1] for p in points),
                                   max(p[0] for p in points), max(p[1] for p in points))
            self.assertEqual(sorted(tagged.values()), [(4, 4, 6, 8), (12, 12, 14, 14)])
            blob = formats["GLB"].read_bytes()
            self.assertEqual(blob[:4], b"glTF")
            size, chunk = struct.unpack_from("<II", blob, 12)
            self.assertEqual(chunk, 0x4E4F534A)
            gltf = json.loads(blob[20:20 + size])
            equipment_tags = {node.get("extras", {}).get("neqsim_tag")
                              for node in gltf["nodes"]
                              if node.get("extras", {}).get("neqsim_kind") == "equipment"}
            self.assertEqual(equipment_tags, {"A", "B"})
            self.assertEqual(gltf["asset"]["extras"]["length_unit"], "m")


class LayoutOptionalAdapterTests(unittest.TestCase):
    def test_missing_optional_cad_adapters_remain_explicitly_incomplete(self):
        import layout_cad

        study = layout_model.normalize_study(fixture())
        alternative = {"id": "NO-CAD", "label": "Optional dependencies absent",
                       "placements": [placement("A", 4, 4), placement("B", 12, 12)]}
        with tempfile.TemporaryDirectory() as directory:
            with mock.patch("layout_cad.importlib.import_module", side_effect=ImportError("Test adapter absent")):
                manifest = layout_cad.export_cad(study, [alternative], directory)
            self.assertEqual(manifest["status"], "INCOMPLETE")
            self.assertTrue(all(a["status"] == "UNAVAILABLE"
                                for a in manifest["adapters"].values()))
            self.assertFalse({"STEP", "GLB", "DXF"} &
                             {entry["format"] for entry in manifest["files"]})
            self.assertTrue(manifest["warnings"])


if __name__ == "__main__":
    unittest.main()
