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
