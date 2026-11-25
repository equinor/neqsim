"""Verify that equal peak pressures cannot conceal shifted pressure waves or valve delays."""

import copy
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from verify_tieback_protection import (PRESSURE_PROBES_M, assess_independent_response,
                                       assess_protection_refinement, compare_protection)


def pressure_row(time, pressure):
    return {"s": time, "riser_bara": pressure, "riser_base_bara": pressure + 8.,
            "subsea_bara": pressure + 14.}


class TestProtectionRefinement(unittest.TestCase):
    def setUp(self):
        self.base = {"case": "psd_failed", "basis": {}, "dt_s": .25, "mesh_cells": 60,
                     "pressure_probes_m": PRESSURE_PROBES_M, "duration_s": .25,
                     "events": {"outlet_blocked_s": 0., "hipps_trip_s": .1,
                                "hipv_a_seated_s": .2, "hipv_b_seated_s": .25},
                     "zones": [{"peak_bara": 73.}],
                     "trace": [pressure_row(0., 56.), pressure_row(.25, 58.)]}
        self.fine = copy.deepcopy(self.base)
        self.fine["dt_s"] = .125
        self.fine["trace"].insert(1, pressure_row(.125, 57.))

    def test_common_times_and_zone_extrema(self):
        self.assertTrue(compare_protection(self.base, self.fine)["within_tolerances"])
        self.fine["trace"][-1]["riser_bara"] -= 1.
        result = compare_protection(self.base, self.fine)
        self.assertFalse(result["within_tolerances"])
        self.assertEqual([0.], result["route_peak_differences_bar"])
        self.assertEqual({"max_abs_bar": 1., "at_s": .25, "largest_error_sampled_from": "reference"},
                         result["endpoint_history_differences"]["riser_bara"])

    def test_shifted_front_is_judged_by_timing_window(self):
        # The refined riser front arrives one scan later: same-time error 2 bar, no amplitude error.
        rows = [(0., 56.), (.25, 58.), (.5, 58.)]
        self.base["trace"] = [pressure_row(t, p) for t, p in rows]
        self.fine["trace"] = [pressure_row(t, p)
                              for t, p in ((0., 56.), (.125, 56.), (.25, 56.), (.375, 57.), (.5, 58.))]
        result = compare_protection(self.base, self.fine)
        self.assertEqual(2., result["endpoint_history_differences"]["riser_bara"]["max_abs_bar"])
        self.assertEqual(0., result["endpoint_tube_differences"]["riser_bara"]["max_abs_bar"])
        self.assertEqual(.25, result["pressure_fronts"]["riser_bara"]["difference_s"])
        self.assertTrue(result["within_tolerances"])
        # An amplitude error is not hidden by the window.
        for row in self.fine["trace"][1:]:
            row["riser_bara"] -= .75
        self.assertFalse(compare_protection(self.base, self.fine)["within_tolerances"])

    def test_riser_base_overshoot_seen_only_on_fine_grid(self):
        self.fine["trace"][1]["riser_base_bara"] = 67.
        result = compare_protection(self.base, self.fine)
        self.assertFalse(result["within_tolerances"])
        tube = result["endpoint_tube_differences"]["riser_base_bara"]
        self.assertEqual(1., tube["max_abs_bar"])
        self.assertEqual(.125, tube["at_s"])
        self.assertEqual("compared", tube["largest_error_sampled_from"])

    def test_missing_front_holds_even_with_small_pressure_error(self):
        for data, rise in ((self.base, .6), (self.fine, .4)):
            for row in data["trace"]:
                row["riser_base_bara"] = 64. + rise * row["s"] / .25
        result = compare_protection(self.base, self.fine)
        self.assertLess(result["endpoint_tube_differences"]["riser_base_bara"]["max_abs_bar"], .5)
        self.assertIsNone(result["pressure_fronts"]["riser_base_bara"]["compared_elapsed_s"])
        self.assertFalse(result["within_tolerances"])

    def test_independent_reference_requires_aligned_receipts_and_provenance(self):
        self.assertEqual("HOLD", assess_independent_response(self.fine, None)["status"])
        benchmark = copy.deepcopy(self.base)
        benchmark["provenance"] = {"method": "independent_solver", "source": "Comparator unit test",
                                   "case_id": "linear pressure ramp", "data_rights": "synthetic"}
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "reference.json"
            path.write_text(json.dumps(benchmark), encoding="utf-8")
            self.assertEqual("PASS", assess_independent_response(self.fine, path)["status"])
            benchmark["events"]["hipv_b_seated_s"] = .1
            path.write_text(json.dumps(benchmark), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "seat feedback"):
                assess_independent_response(self.fine, path)
            benchmark["provenance"].pop("source")
            path.write_text(json.dumps(benchmark), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "provenance"):
                assess_independent_response(self.fine, path)

    def test_independent_seat_feedback(self):
        self.fine["events"]["hipv_b_seated_s"] += .75
        self.assertFalse(compare_protection(self.base, self.fine)["within_tolerances"])
        del self.fine["events"]["hipv_b_seated_s"]
        with self.assertRaisesRegex(ValueError, "seat feedback"):
            compare_protection(self.base, self.fine)

    def test_finest_mesh_can_hold_a_previously_passing_screen(self):
        def receipt(path, case, dt, cells, cfl):
            data = copy.deepcopy(self.fine if dt == .125 else self.base)
            data.update(case=case, mesh_cells=cells, pipe_cfl=cfl)
            data["zones"][0]["peak_bara"] += {60: 0., 120: .36, 240: .96}[cells]
            return data

        with patch("verify_tieback_protection.load", side_effect=receipt):
            comparisons = assess_protection_refinement(Path("receipts"), self.base)
        self.assertEqual([(60, 60), (60, 120), (60, 60), (120, 240)],
                         [(c["reference_cells"], c["cells"]) for c in comparisons])
        self.assertTrue(all(c["within_tolerances"] for c in comparisons[:-1]))
        self.assertFalse(comparisons[-1]["within_tolerances"])
        self.assertAlmostEqual(.60, comparisons[-1]["route_peak_differences_bar"][0])
