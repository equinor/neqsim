"""End-to-end regression for the pre-event outlet reversal in issue 5117."""

import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


@unittest.skipUnless(os.environ.get("NEQSIM_CLASSPATH"), "requires compiled NeqSim and JPype")
class TestInstalledHandoff(unittest.TestCase):
    def test_frozen_peng_robinson_reaches_restart(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "pr.json"
            solver = Path(__file__).with_name("tieback.py")
            proc = subprocess.run([sys.executable, str(solver), "--eos", "Pr", "--basis", "installed",
                                   "--policy", "fixed", "--mesh", "1", "--dt", ".25", "--duration", "21",
                                   "--revision", "regression", "--output", str(path)], check=False)
            self.assertTrue(path.is_file())
            result = json.loads(path.read_text())
            self.assertEqual(0, proc.returncode, result.get("reason"))
            self.assertEqual("completed", result["status"])
            self.assertLess(abs(result["steady_handoff"]["well_residual_kg_s"]), 1.e-9)
            self.assertLess(abs(result["steady_handoff"]["choke_residual_bar"]), 1.e-9)
            self.assertLess(result["max_component_residual_kg"], 1.e-7)
            rows = result["time_series"]
            self.assertEqual(21., rows[-1]["end_s"])
            self.assertEqual(0., next(r for r in rows if r["end_s"] == 4.25)["well_b_kg_s"])
            self.assertGreater(next(r for r in rows if r["end_s"] == 12.25)["well_b_kg_s"], 0.)


    def test_conservative_guard_keeps_pipe_and_vessel_inventories(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "srk.json"
            proc = subprocess.run([sys.executable, str(Path(__file__).with_name("tieback.py")),
                                   "--energy-audit", "--duration", "21", "--revision", "regression",
                                   "--output", str(path)], check=False)
            result = json.loads(path.read_text())
            # Successful physical resolution of this previously rejected case is also valid.
            if result["status"] == "completed":
                self.assertEqual(0, proc.returncode)
                self.assertEqual(21., result["time_series"][-1]["end_s"])
                return
            self.assertNotEqual(0, proc.returncode)
            evidence = result["outlet_diagnostic"]
            self.assertFalse(evidence["separator_advanced_for_interval"])
            self.assertGreater(evidence["separator_mass_before_handoff_kg"], 0.)
            self.assertGreater(evidence["accepted_pipe_substeps"], 0)
            self.assertAlmostEqual(evidence["end_s"] - evidence["start_s"], evidence["pipe_interval_s"])
            for key in ("pipe_initial_phase_inventory_kg", "pipe_final_phase_inventory_kg"):
                self.assertEqual({"oil", "gas", "water"}, set(evidence[key]))
                self.assertTrue(all(mass >= 0. for mass in evidence[key].values()))
