"""Regression tests for migration identities joined to declared replay coverage."""

import copy
from pathlib import Path
import sys
import unittest


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "devtools"))

from neqsim_continuous import production  # noqa: E402
from task_template.step3_report import generate_report  # noqa: E402


class MigrationCoverageIdentityTest(unittest.TestCase):
    """Use Java-exported identity shapes with distinct model and area names."""

    def setUp(self):
        """Declare installed map, installed shaft and registered driver evidence."""
        self.map_row = {
            "evidence_kind": "INSTALLED_EQUIPMENT",
            "equipment_id": "equipment:ten-well-field/Field/HP compressor",
            "constraint_name": "surgeMargin",
            "qualified_constraint_id": "equipment:ten-well-field/Field/HP compressor#surgeMargin",
            "statuses": ["AVAILABLE"],
            "provenance": "installed compressor map",
        }
        self.shaft_row = {
            "evidence_kind": "INSTALLED_EQUIPMENT",
            "equipment_id": "equipment:ten-well-field/Field/export shaft control",
            "constraint_name": "shaftPower",
            "qualified_constraint_id": "equipment:ten-well-field/Field/export shaft control#shaftPower",
            "statuses": ["AVAILABLE"],
            "provenance": "installed shaft rating",
        }
        self.driver_row = {
            "evidence_kind": "PLANT_CONSTRAINT",
            "equipment_id": "coupled_group:synthetic field/Host/export common shaft",
            "constraint_name": "driver-maximum-power",
            "qualified_constraint_id": (
                "coupled_group:synthetic field/Host/export common shaft#driver-maximum-power"),
            "statuses": ["AVAILABLE"],
            "provenance": "installed driver rating",
        }
        self.coverage = {
            "model_name": "ten-well-field",
            "rows": [self.map_row, self.shaft_row, self.driver_row],
        }
        self.resolvers = (
            production._migration_coverage_rows,
            generate_report._migration_coverage_report_rows,
        )

    def assert_resolution(self, name, expected):
        """Require guard and standalone report consumers to resolve the same rows."""
        for resolver in self.resolvers:
            with self.subTest(resolver=resolver.__module__, identity=name):
                self.assertEqual(expected, resolver(self.coverage, name))

    def test_resolves_area_qualified_installed_map_and_shaft_names(self):
        """ProcessAutomation qualifies display names by area, not model."""
        self.assert_resolution("Field::HP compressor/surgeMargin", [self.map_row])
        self.assert_resolution("Field::export shaft control/shaftPower", [self.shaft_row])
        self.assert_resolution("ten-well-field::HP compressor/surgeMargin", [])

    def test_preserves_exact_installed_and_registered_plant_ids(self):
        """Stable identities stay valid for both evidence kinds."""
        for row in self.coverage["rows"]:
            self.assert_resolution(row["qualified_constraint_id"], [row])

    def test_same_equipment_name_in_another_area_does_not_alias(self):
        """Only the receipt's exact process area can supply a display-name row."""
        other = copy.deepcopy(self.map_row)
        other["equipment_id"] = "equipment:ten-well-field/Other/HP compressor"
        other["qualified_constraint_id"] = other["equipment_id"] + "#surgeMargin"
        self.coverage["rows"].append(other)
        self.assert_resolution("Field::HP compressor/surgeMargin", [self.map_row])
        self.assert_resolution("Other::HP compressor/surgeMargin", [other])
        self.assert_resolution("Missing::HP compressor/surgeMargin", [])

    def test_display_names_cannot_alias_another_model_or_scope_type(self):
        """An area/equipment alias cannot cross a model or plant-scope boundary."""
        self.map_row["equipment_id"] = "equipment:another-model/Field/HP compressor"
        self.assert_resolution("Field::HP compressor/surgeMargin", [])
        self.map_row["equipment_id"] = "coupled_group:ten-well-field/Field/HP compressor"
        self.assert_resolution("Field::HP compressor/surgeMargin", [])

    def test_decodes_scope_components_before_building_the_display_name(self):
        """Escaped separators in names preserve model, area and equipment boundaries."""
        self.coverage["model_name"] = "ten/well:field"
        self.map_row["equipment_id"] = (
            "equipment:ten%2Fwell%3Afield/Field%2FA%3A1/HP%2Fcasing%3A%231")
        self.map_row["constraint_name"] = "margin/operating"
        self.assert_resolution("Field/A:1::HP/casing:#1/margin/operating", [self.map_row])

    def test_retains_ambiguous_rows_for_fail_closed_link_validation(self):
        """Duplicate matching evidence must remain visible to the uniqueness check."""
        duplicate = copy.deepcopy(self.map_row)
        self.coverage["rows"].append(duplicate)
        self.assert_resolution("Field::HP compressor/surgeMargin", [self.map_row, duplicate])

    def test_distinct_stable_ids_with_the_same_display_name_remain_ambiguous(self):
        """Separators inside area or equipment names cannot choose a row arbitrarily."""
        self.map_row["equipment_id"] = "equipment:ten-well-field/A%3A%3AB/C"
        self.map_row["qualified_constraint_id"] = self.map_row["equipment_id"] + "#surgeMargin"
        other = copy.deepcopy(self.map_row)
        other["equipment_id"] = "equipment:ten-well-field/A/B%3A%3AC"
        other["qualified_constraint_id"] = other["equipment_id"] + "#surgeMargin"
        self.coverage["rows"].append(other)
        self.assert_resolution("A::B::C/surgeMargin", [self.map_row, other])
        self.assert_resolution(self.map_row["qualified_constraint_id"], [self.map_row])
        self.assert_resolution(other["qualified_constraint_id"], [other])

    def test_report_link_rejects_missing_ambiguous_stale_and_unavailable_rows(self):
        """A report may join only independently available current-cycle evidence."""
        receipt = {
            "cycle_id": "cycle-1",
            "configured_map_constraint_name": "Field::HP compressor/surgeMargin",
            "driver_constraint_name": self.driver_row["qualified_constraint_id"],
            "configured_map_limit_provenance": "temporary configured map limit",
        }
        lifecycle = {
            "cycle_id": "cycle-1",
            "periods": [{"period_id": "cycle-1", "utilization_coverage": self.coverage}],
        }
        link, reasons = generate_report._map_driver_lifecycle_report_link(lifecycle, receipt)
        self.assertEqual([], reasons)
        self.assertIs(self.map_row, link["map_row"])
        self.assertIs(self.driver_row, link["driver_row"])
        for mutation in ("missing", "ambiguous", "stale", "unavailable", "override"):
            with self.subTest(mutation=mutation):
                changed = copy.deepcopy(lifecycle)
                rows = changed["periods"][0]["utilization_coverage"]["rows"]
                if mutation == "missing":
                    rows.pop(0)
                elif mutation == "ambiguous":
                    rows.append(copy.deepcopy(rows[0]))
                elif mutation == "stale":
                    changed["cycle_id"] = "previous-cycle"
                elif mutation == "unavailable":
                    rows[0]["statuses"] = ["MISSING"]
                else:
                    rows[0]["provenance"] = receipt["configured_map_limit_provenance"]
                link, reasons = generate_report._map_driver_lifecycle_report_link(changed, receipt)
                self.assertIsNone(link)
                self.assertTrue(reasons)


if __name__ == "__main__":
    unittest.main()
