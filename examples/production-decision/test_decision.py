"""Regression checks for scientific decision and blind-scoring behavior."""

import unittest

from decision import GRIDS, HOST, assess, challenge, score


def receipt(mesh, dt, policy):
    sample = {"well_a_kg_s": 10., "well_b_kg_s": 5., "level_m": 1., "interface_m": .3,
              "riser_gas_kg_s": 3., "riser_oil_kg_s": 8., "riser_water_kg_s": 4.,
              "oil_out_kg_s": 8., "separator_bara": 54., "screening_surge_margin": .2,
              "power_kW": 500., "gas_export_kg_s": 3., "host_energy_residual_W": 1e-7}
    # Higher nominal production conceals a short fine-mesh power violation.
    if policy == "ramp" and mesh == 2:
        sample["power_kW"] = 650.
    steps = round(16 / dt)
    return {"configuration": {"mesh": mesh, "dt": dt, "duration": 16., "revision": "test",
                              "eos": "Srk", "policy": policy},
            "max_component_residual_kg": 1e-12, "max_phase_residual_kg": 1e-12,
            "well_production_kg": 16. if policy == "ramp" else 15.,
            "missing_barite_interactions": ["B0:[Ba++, SO4--]"],
            "time_series": [{"end_s": (i + 1) * dt, **{k: v for k, v in sample.items()
                             if k not in HOST or (i + 1) % round(1 / dt) == 0}} for i in range(steps)]}


class TestDecision(unittest.TestCase):
    def setUp(self):
        self.runs = [receipt(m, dt, p) for m, dt in GRIDS for p in ("fixed", "ramp")]

    def test_coarse_challenge_hides_refinement(self):
        question = challenge(self.runs, 600.)
        self.assertEqual(500., question["coarse_results"]["Srk/ramp"]["power_kW"])
        self.assertNotIn("held_out", question)
        self.assertEqual(["power_kW"], score(self.runs, 600., "ramp")["violations"])
        self.assertEqual(0., score(self.runs, 600., "fixed")["production_regret_kg"])
        self.assertTrue(score(self.runs, 400., "withhold")["appropriate_abstention"])

    def test_missing_map_requires_more_evidence(self):
        question = challenge(self.runs, 900., surge_map_available=False)
        self.assertNotIn("screening_surge_margin", question["coarse_results"]["Srk/fixed"])
        self.assertTrue(score(self.runs, 900., "withhold", False)["appropriate_abstention"])

    def test_solver_rejection_is_negative_evidence(self):
        self.runs[-1] = {"status": "rejected", "reason": "reversed phase", "configuration": self.runs[-1]["configuration"]}
        self.assertEqual("rejected_trajectory", assess(self.runs)["fluid_results"]["Srk"]["ramp"]["status"])
        self.assertIn("accepted refined trajectory", score(self.runs, 600., "ramp")["held_out"]["ramp"]["missing_evidence"])
        self.runs[-1]["configuration"]["basis"] = "installed"
        with self.assertRaises(ValueError):
            assess(self.runs)

    def test_incomplete_and_duplicate(self):
        self.assertEqual("incomplete", assess(self.runs[:-1])["screening_decisions"]["Srk"])
        with self.assertRaises(ValueError):
            assess(self.runs + self.runs[:1])
        with self.assertRaises(ValueError):
            challenge(self.runs[:-1], 600.)

    def test_reject_mixed_basis_and_short_series(self):
        self.runs[1]["configuration"]["revision"] = "different"
        with self.assertRaises(ValueError):
            assess(self.runs)
        self.runs[1]["time_series"].pop()
        with self.assertRaises(ValueError):
            challenge(self.runs, 600.)

    def test_report_carries_binding_constraint_and_uncertainty(self):
        result = assess(self.runs)
        ramp = result["fluid_results"]["Srk"]["ramp"]
        self.assertEqual(-50., ramp["quantities"]["power_kW"]["worst_margin"])
        self.assertIn("power_kW", ramp["sensitive_quantities"])
        self.assertEqual("fixed", result["screening_decisions"]["Srk"])
        self.assertEqual("withhold", result["field_recommendation"])


class TestLaboratoryManifest(unittest.TestCase):
    def test_matched_sample_and_independent_holdout(self):
        from pvt_evidence import validate_manifest
        document = {"fluid_sha256": "reference", "observations": [
            {"id": role, "phase": "gas", "density_kg_m3": 32., "temperature_K": 310.,
             "pressure_bara": p, "role": role, "tolerance_kg_m3": 1., "source": "synthetic test record"}
            for role, p in (("calibration", 50.), ("holdout", 55.))]}
        self.assertEqual(2, len(validate_manifest(document, "reference")))
        with self.assertRaises(ValueError):
            validate_manifest(document, "different")
        document["observations"][1]["pressure_bara"] = 50.
        with self.assertRaises(ValueError):
            validate_manifest(document, "reference")
