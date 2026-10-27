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
                                        allocated_bytes_per_op=32, phases=1, max_balance=0, max_logfugacity=0))
            self.states.append(dict(cell, phase="gas", component="methane", beta=1, x=1, logphi=0))
        self.state_fields = list(self.states[0])

    def write(self):
        """Write fresh raw evidence after each controlled corruption."""
        for name, rows in (("rounds.csv", self.rounds), ("rounds.csv.states.csv", self.states)):
            with (self.directory / name).open("w", newline="") as output:
                fields = list(rows[0]) if rows else self.state_fields
                writer = csv.DictWriter(output, fieldnames=fields)
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

    def test_empty_final_states_are_rejected(self):
        self.states.clear()
        self.write()
        with self.assertRaisesRegex(ValueError, "Incomplete final-state"):
            summarize(self.directory, 1, 3)

    def test_rejected_states_do_not_publish_partial_summary(self):
        self.states[-1]["logphi"] = 1e-5
        self.write()
        with self.assertRaisesRegex(ValueError, "thermodynamic result changed"):
            summarize(self.directory, 1, 3)
        self.assertFalse((self.directory / "summary.csv").exists())
        self.assertFalse((self.directory / "numerical-summary.json").exists())

    def test_missing_entire_cell_is_rejected(self):
        self.rounds += [dict(row, eos="PR") for row in self.rounds]
        self.write()
        with self.assertRaisesRegex(ValueError, "Incomplete final-state"):
            summarize(self.directory, 2, 3)

    def test_missing_entire_component_is_rejected(self):
        for row in self.rounds + self.states:
            row["components"] = "2"
        self.write()
        with self.assertRaisesRegex(ValueError, "component roster"):
            summarize(self.directory, 1, 3)

    def test_missing_entire_phase_is_rejected(self):
        for row in self.rounds:
            row["phases"] = 2
        self.write()
        with self.assertRaisesRegex(ValueError, "phase roster"):
            summarize(self.directory, 1, 3)

    def test_changed_phase_count_between_rounds_is_rejected(self):
        self.rounds[-1]["phases"] = 2
        self.write()
        with self.assertRaisesRegex(ValueError, "phase count"):
            summarize(self.directory, 1, 3)

    def test_valid_multiphase_multicomponent_roster_is_supported(self):
        for row in self.rounds + self.states:
            row["components"] = "2"
        for row in self.rounds:
            row["phases"] = 2
        self.states += [dict(row, component="water") for row in self.states]
        self.states += [dict(row, phase="aqueous") for row in self.states]
        for row in self.states:
            row["beta"] = 0.5
            row["x"] = 0.5
        self.write()
        with contextlib.redirect_stdout(io.StringIO()):
            summarize(self.directory, 1, 3)
        result = json.loads((self.directory / "numerical-summary.json").read_text())
        self.assertEqual(result["state_rows"], 16)

    def test_all_published_allocation_matrices_remain_supported(self):
        root = Path(__file__).resolve().parents[1]
        fixture = root / "src/test/resources/neqsim/thermo/mixingrule/hv-allocation-performance"
        configurations = [("", 64, 8), ("phase-quiet", 16, 16),
                          ("clone-flash-quiet", 4, 16), ("clone-flash-pinned", 4, 16),
                          ("identity-pinned", 1, 16)]
        for name, cells, rounds in configurations:
            with self.subTest(matrix=name or "full"):
                for filename in ("rounds.csv.gz", "states.csv.gz"):
                    shutil.copyfile(fixture / name / filename, self.directory / filename)
                with contextlib.redirect_stdout(io.StringIO()):
                    summarize(self.directory, cells, rounds)
                result = json.loads((self.directory / "numerical-summary.json").read_text())
                self.assertEqual(result["jvms"], 4 * cells)
                self.assertEqual(result["max_baseline_candidate_state_difference"],
                                 dict(beta=0.0, x=0.0, logphi=0.0))

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
