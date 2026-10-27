"""Check strict completeness and result identity in alternate benchmark subsets."""

import contextlib
import csv
import io
import json
from pathlib import Path
import shutil
import tempfile
import unittest

from summarize_huron_vidal_clone import summarize


class HuronVidalSummaryTest(unittest.TestCase):
    """Retain strict provenance checks when selecting a smaller, longer-warmed matrix."""

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.directory = Path(self.temp.name)
        self.rounds = []
        self.states = []
        for sample in ("baseline-0", "candidate-1", "candidate-2", "baseline-3"):
            cell = dict(eos="SRK", components="1", operation="phase", init="3", rule="4", sample=sample)
            for round_id in range(3):
                self.rounds.append(dict(cell, round=round_id, ns_per_op=100,
                                        allocated_bytes_per_op=32, max_balance=0, max_logfugacity=0))
            self.states.append(dict(cell, phase="gas", component="methane", beta=1, x=1, logphi=0))

    def write(self):
        """Write fresh raw evidence after each controlled corruption."""
        for name, rows in (("rounds.csv", self.rounds), ("rounds.csv.states.csv", self.states)):
            with (self.directory / name).open("w", newline="") as output:
                writer = csv.DictWriter(output, fieldnames=list(rows[0]))
                writer.writeheader()
                writer.writerows(rows)

    def test_explicit_subset_has_correct_counts_and_unchanged_defaults(self):
        self.write()
        with contextlib.redirect_stdout(io.StringIO()):
            summarize(self.directory, expected_cells=1, expected_rounds=3)
        result = json.loads((self.directory / "numerical-summary.json").read_text())
        self.assertEqual((result["cells"], result["jvms"], result["raw_rounds"]), (1, 4, 12))
        with self.assertRaisesRegex(ValueError, "Incomplete matrix"):
            summarize(self.directory)

    def test_missing_sample_is_rejected(self):
        self.rounds.pop()
        self.write()
        with self.assertRaisesRegex(ValueError, "Incomplete matrix"):
            summarize(self.directory, 1, 3)

    def test_duplicate_round_identity_is_rejected(self):
        self.rounds[1] = self.rounds[0].copy()
        self.write()
        with self.assertRaisesRegex(ValueError, "Duplicate"):
            summarize(self.directory, 1, 3)

    def test_changed_thermodynamic_result_is_rejected(self):
        self.states[-1]["logphi"] = 1e-5
        self.write()
        with self.assertRaisesRegex(ValueError, "thermodynamic result changed"):
            summarize(self.directory, 1, 3)

    def test_duplicate_state_sample_is_rejected(self):
        self.states[-1] = self.states[0].copy()
        self.write()
        with self.assertRaisesRegex(ValueError, "topology differs"):
            summarize(self.directory, 1, 3)

    def test_nonfinite_state_is_rejected(self):
        self.states[0]["logphi"] = "NaN"
        self.write()
        with self.assertRaisesRegex(ValueError, "Non-finite"):
            summarize(self.directory, 1, 3)

    def test_original_published_default_matrix_remains_supported(self):
        root = Path(__file__).resolve().parents[1]
        fixture = root / "src/test/resources/neqsim/thermo/mixingrule/hv-clone-performance"
        for name in ("rounds.csv.gz", "states.csv.gz"):
            shutil.copyfile(fixture / name, self.directory / name)
        with contextlib.redirect_stdout(io.StringIO()):
            summarize(self.directory)
        result = json.loads((self.directory / "numerical-summary.json").read_text())
        self.assertEqual((result["cells"], result["jvms"], result["raw_rounds"]), (64, 256, 2048))
        self.assertEqual(result["max_baseline_candidate_state_difference"], dict(beta=0.0, x=0.0, logphi=0.0))


if __name__ == "__main__":
    unittest.main()
