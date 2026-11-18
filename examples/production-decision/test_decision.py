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
        self.runs[-1]["host_diagnostic"] = {"condition": "no_online_export_duty", "export_kg_s": 0.}
        result = assess(self.runs)["fluid_results"]["Srk"]["ramp"]
        self.assertEqual("rejected_trajectory", result["status"])
        self.assertEqual(0., result["rejections"][0]["host_diagnostic"]["export_kg_s"])
        self.assertIn("accepted refined trajectory", score(self.runs, 600., "ramp")["held_out"]["ramp"]["missing_evidence"])
        self.runs[-1]["configuration"]["basis"] = "installed"
        with self.assertRaises(ValueError):
            assess(self.runs)

    def test_rejected_refinement_keeps_known_violations_from_other_fluids(self):
        # The PR fine mesh completes at 650 kW. A separate SRK trajectory rejects.
        pr = [receipt(m, dt, p) for m, dt in GRIDS for p in ("fixed", "ramp")]
        for run in pr:
            run["configuration"]["eos"] = "Pr"
        for run in self.runs:
            if run["configuration"]["policy"] == "ramp" and run["configuration"]["mesh"] == 2:
                run.update(status="rejected", reason="outlet backflow")
        scored = score(self.runs + pr, 600., "ramp")
        ramp = scored["held_out"]["ramp"]
        self.assertEqual(["power_kW"], scored["violations"])
        self.assertEqual([{"fluid": "Pr", "mesh_cells": 120, "dt_s": .25, "quantity": "power_kW",
                           "value": 650., "direction": "max", "limit": 600.}], ramp["violation_evidence"])
        self.assertEqual(["outlet backflow"], ramp["rejections"])
        self.assertIn("accepted refined trajectory", ramp["missing_evidence"])
        self.assertIsNone(ramp["worst_production_kg"])
        self.assertIsNone(scored["production_regret_kg"])

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


class TestRestartHoldout(unittest.TestCase):
    def setUp(self):
        self.run = receipt(1, .25, "fixed")
        self.run["configuration"]["event_id"] = "test-start"
        for i, row in enumerate(self.run["time_series"]):
            row["manifold_bara"] = 70. + i
            row["riser_water_kg_s"] = 2. + i
        self.points = [{"quantity": "manifold_bara", "tag": "PT1", "start_s": .5, "end_s": .5,
                        "value": 71., "tolerance": .1, "unit": "bara"},
                       {"quantity": "riser_water_kg_s", "tag": "FT1", "start_s": 0., "end_s": .5,
                        "value": 2.5, "tolerance": .01, "unit": "kg/s"}]
        self.doc = {"event_id": "test-start", "source": "analytical control", "policy": "fixed",
                    "origin": "synthetic", "observations": self.points}

    def test_points_and_volume_integrated_rate(self):
        from restart_observations import compare
        result = compare(self.run, self.doc)
        self.assertEqual("within_supplied_tolerances", result["status"])
        self.assertEqual(0., result["by_quantity"]["riser_water_kg_s"]["mae"])
        self.points[0]["value"] += 1.
        self.assertEqual("outside_tolerance", compare(self.run, self.doc)["status"])

    def test_missing_sensor_or_time_is_retained(self):
        from restart_observations import compare
        self.points[0].update(start_s=.375, end_s=.375)
        result = compare(self.run, self.doc)
        self.assertEqual("insufficient_coverage", result["status"])
        self.assertEqual(0, result["by_quantity"]["manifold_bara"]["paired"])
        self.assertFalse(result["complete_coverage"])
        self.assertEqual({"requested": 1, "paired": 0}, result["by_quantity"]["manifold_bara"]["coverage_by_tag"]["PT1"])

    def test_event_units_duplicate_and_solver_rejection(self):
        from restart_observations import compare
        for changes in ({"event_id": "other"}, {"origin": "unknown"}, {"policy": "ramp"},
                        {"observations": self.points * 2}):
            with self.assertRaises(ValueError):
                compare(self.run, {**self.doc, **changes})
        self.points[1]["unit"] = "Sm3/d"
        with self.assertRaises(ValueError):
            compare(self.run, self.doc)
        self.run.update(status="rejected", reason="physical guard")
        self.assertEqual("rejected_trajectory", compare(self.run, self.doc)["status"])

    def test_handoff_protocol_and_rejected_coarse_block_scoring(self):
        runs = [receipt(m, dt, p) for m, dt in GRIDS for p in ("fixed", "ramp")]
        runs[0]["configuration"]["handoff_protocol"] = "coupled-steady-v2"
        with self.assertRaises(ValueError):
            challenge(runs, 600.)
        del runs[0]["configuration"]["handoff_protocol"]
        runs[0].update(status="rejected", reason="coarse failure")
        scored = score(runs, 600., "fixed")
        self.assertIsNone(scored["production_regret_kg"])
        self.assertIn("accepted coarse trajectory", scored["held_out"]["fixed"]["missing_evidence"])


class TestWaterInjectionDomain(unittest.TestCase):
    def test_fracture_threshold_holdouts_stay_visible(self):
        import tempfile
        from pathlib import Path
        from field_history import injection_holdout

        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "water.csv"
            path.write_text("date,role,bottomhole_bara,reservoir_bara,water_kg_s,fracture_bara,source\n"
                            "2026-01-01,calibration,150,140,1,190,control\n"
                            "2026-01-02,calibration,160,140,2,190,control\n"
                            "2026-01-03,calibration,170,140,3,190,control\n"
                            "2026-01-04,holdout,190,140,5,190,control\n"
                            "2026-01-05,holdout,200,140,6,190,control\n")
            result = injection_holdout(path, None)
            self.assertEqual(2, result["requested"])
            self.assertEqual(0, result["predicted"])
            self.assertIsNone(result["errors_kg_s"])
            self.assertEqual([0., -10.], [r["fracture_margin_bar"] for r in result["holdout"]])


class TestHostChart(unittest.TestCase):
    def test_map_provenance_and_bracketing_lines(self):
        from pathlib import Path
        from unittest.mock import Mock
        from host_chart import assess_point, load_chart

        chart, digest = load_chart(Path(__file__).with_name("synthetic_host_chart.json"))
        self.assertEqual(64, len(digest))
        self.assertEqual("synthetic", chart["origin"])
        machine, suction = Mock(), Mock()
        machine.getSpeed.return_value = 11000.
        machine.getPolytropicHead.return_value = 82.
        machine.getOutletStream.return_value.getPressure.return_value = 100.
        suction.getPressure.return_value = 54.
        suction.getTemperature.return_value = 310.
        suction.getFluid.return_value.getMolarMass.return_value = .02
        point = assess_point(machine, chart, suction, 400.)
        self.assertAlmostEqual(253., point["surge_flow_actual_m3_h"])
        with self.assertRaisesRegex(ValueError, "bracketing"):
            assess_point(machine, chart, suction, 240.)
        suction.getPressure.return_value = 45.
        with self.assertRaisesRegex(ValueError, "suction"):
            assess_point(machine, chart, suction, 400.)
