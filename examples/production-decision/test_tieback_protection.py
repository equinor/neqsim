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

    def test_independent_seat_feedback(self):
        self.fine["events"]["hipv_b_seated_s"] += .75
        self.assertFalse(compare_protection(self.base, self.fine)["within_tolerances"])
        del self.fine["events"]["hipv_b_seated_s"]
        with self.assertRaisesRegex(ValueError, "seat feedback"):
            compare_protection(self.base, self.fine)
