"""Verify that equal peak pressures cannot conceal shifted pressure waves or valve delays."""

import copy
import unittest

from verify_tieback_protection import compare_protection


class TestProtectionRefinement(unittest.TestCase):
    def setUp(self):
        self.base = {"case": "psd_failed", "basis": {}, "dt_s": .25, "mesh_cells": 60,
                     "events": {"hipps_trip_s": 39., "hipv_a_seated_s": 41., "hipv_b_seated_s": 42.},
                     "zones": [{"peak_bara": 73.}],
                     "trace": [{"s": 0., "subsea_bara": 70., "riser_bara": 56.},
                               {"s": .25, "subsea_bara": 71., "riser_bara": 58.}]}
        self.fine = copy.deepcopy(self.base)
        self.fine["dt_s"] = .125
        self.fine["trace"].insert(1, {"s": .125, "subsea_bara": 70.5, "riser_bara": 57.})

    def test_common_times_and_zone_extrema(self):
        self.assertTrue(compare_protection(self.base, self.fine)["within_tolerances"])
        self.fine["trace"][-1]["riser_bara"] -= 1.
        result = compare_protection(self.base, self.fine)
        self.assertFalse(result["within_tolerances"])
        self.assertEqual([0.], result["route_peak_differences_bar"])
        self.assertEqual({"max_abs_bar": 1., "at_s": .25}, result["endpoint_history_differences"]["riser_bara"])

    def test_shifted_front_is_judged_by_timing_window(self):
        # The refined riser front arrives one scan later: same-time error 2 bar, no amplitude error.
        rows = [(0., 56.), (.25, 58.), (.5, 58.)]
        self.base["trace"] = [{"s": t, "subsea_bara": 70., "riser_bara": p} for t, p in rows]
        self.fine["trace"] = [{"s": t, "subsea_bara": 70., "riser_bara": p}
                              for t, p in ((0., 56.), (.125, 56.), (.25, 56.), (.375, 57.), (.5, 58.))]
        result = compare_protection(self.base, self.fine)
        self.assertEqual(2., result["endpoint_history_differences"]["riser_bara"]["max_abs_bar"])
        self.assertEqual(0., result["endpoint_tube_differences"]["riser_bara"]["max_abs_bar"])
        self.assertTrue(result["within_tolerances"])
        # An amplitude error is not hidden by the window.
        for row in self.fine["trace"][1:]:
            row["riser_bara"] -= .75
        self.assertFalse(compare_protection(self.base, self.fine)["within_tolerances"])

    def test_independent_seat_feedback(self):
        self.fine["events"]["hipv_b_seated_s"] += .75
        self.assertFalse(compare_protection(self.base, self.fine)["within_tolerances"])
        del self.fine["events"]["hipv_b_seated_s"]
        with self.assertRaisesRegex(ValueError, "seat feedback"):
            compare_protection(self.base, self.fine)
