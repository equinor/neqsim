"""Qualify fail-closed admission to the staged binary diagnostic, not physical accuracy."""
import csv
import hashlib
import importlib.util
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

FIXTURE = Path(__file__).resolve().parents[1] / "src/test/resources/neqsim/thermo/mixingrule/hv-meg-methane"
spec = importlib.util.spec_from_file_location("meg_methane_replay", FIXTURE / "reproduce_reference.py")
REPLAY = importlib.util.module_from_spec(spec)
with patch.object(sys, "path", [str(FIXTURE)] + sys.path):
    spec.loader.exec_module(REPLAY)


class ReplayAdmissionTest(unittest.TestCase):
    """Keep direction, units, literal observations and nonfinite handling independent of solver parity."""

    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        for name in ("binary_solver.py", "reproduce_reference.py", "declared-input-reference.csv"):
            shutil.copyfile(FIXTURE / name, self.root / name)
        with (self.root / "declared-input-reference.csv").open(newline="") as stream:
            self.rows = list(csv.reader(stream))

    def write(self):
        """Write one deliberately corrupted copy; repository inputs remain read-only."""
        with (self.root / "declared-input-reference.csv").open("w", newline="") as stream:
            csv.writer(stream).writerows(self.rows)
        return self.root / "declared-input-reference.csv"

    def test_complete_reference_is_retained(self):
        self.assertEqual(6, len(REPLAY.read_references(self.write())))

    def test_nonfinite_reference_cells_are_rejected(self):
        for value in ("NaN", "sNaN", "-NaN", "Infinity", "-Infinity"):
            for column in range(8):
                with self.subTest(value=value, column=column):
                    previous = self.rows[1][column]
                    self.rows[1][column] = value
                    with self.assertRaisesRegex(ValueError, "nonfinite"):
                        REPLAY.read_references(self.write())
                    self.rows[1][column] = previous

    def test_prediction_domain_is_enforced(self):
        for value in ("0", "-0.1", "1", "1.1"):
            for column in range(4, 8):
                with self.subTest(value=value, column=column):
                    previous = self.rows[1][column]
                    self.rows[1][column] = value
                    with self.assertRaisesRegex(ValueError, "strictly between"):
                        REPLAY.read_references(self.write())
                    self.rows[1][column] = previous

    def test_invalid_decimal_is_explicit(self):
        for value in ("", "missing"):
            self.rows[1][4] = value
            with self.assertRaisesRegex(ValueError, "invalid decimal"):
                REPLAY.read_references(self.write())

    def test_schema_preserves_direction_units_and_alpha(self):
        for column, value in ((0, "T_C"), (1, "P_MPa"), (2, "observed_y_MEG"),
                              (4, "independent_x_CH4_Soave")):
            previous = self.rows[0][column]
            self.rows[0][column] = value
            with self.assertRaisesRegex(ValueError, "header"):
                REPLAY.read_references(self.write())
            self.rows[0][column] = previous

    def test_ragged_rows_are_rejected(self):
        previous = self.rows[1]
        for row in (previous[:-1], previous + ["0"]):
            self.rows[1] = row
            with self.assertRaisesRegex(ValueError, "eight fields"):
                REPLAY.read_references(self.write())

    def test_missing_duplicate_and_reordered_observations_are_rejected(self):
        self.rows.pop()
        with self.assertRaisesRegex(ValueError, "six retained"):
            REPLAY.read_references(self.write())
        self.rows.append(self.rows[-1])
        with self.assertRaisesRegex(ValueError, "Retained observation"):
            REPLAY.read_references(self.write())
        self.rows[1], self.rows[2] = self.rows[2], self.rows[1]
        with self.assertRaisesRegex(ValueError, "Retained observation"):
            REPLAY.read_references(self.write())

    def test_observation_and_compiler_band_cannot_be_silently_changed(self):
        for column in range(4):
            previous = self.rows[1][column]
            self.rows[1][column] = "-1"
            with self.assertRaisesRegex(ValueError, "Retained observation"):
                REPLAY.read_references(self.write())
            self.rows[1][column] = previous

    def test_cli_rejects_nan_before_output_with_and_without_assertions(self):
        self.rows[1][4] = "NaN"
        self.write()
        output = self.root.parent / (self.root.name + "-report.json")
        self.addCleanup(lambda: output.unlink(missing_ok=True))
        for flags in ([], ["-O"]):
            output.write_text("prior evidence\n")
            result = subprocess.run([sys.executable] + flags + [str(self.root / "reproduce_reference.py"),
                                    "--output", str(output)], capture_output=True, text=True)
            self.assertNotEqual(0, result.returncode)
            self.assertIn("nonfinite", result.stderr)
            self.assertEqual("prior evidence\n", output.read_text())

    def test_cli_protects_read_only_fixture(self):
        before = (self.root / "declared-input-reference.csv").read_bytes()
        result = subprocess.run([sys.executable, str(self.root / "reproduce_reference.py"), "--output",
                                 str(self.root / "declared-input-reference.csv")], capture_output=True, text=True)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("read-only fixture", result.stderr)
        self.assertEqual(before, (self.root / "declared-input-reference.csv").read_bytes())

    def test_nonfinite_or_invalid_solver_output_never_writes_a_report(self):
        output = self.root / "mock-report.json"
        methane = REPLAY.read_references(FIXTURE / "declared-input-reference.csv")[0][4]
        cases = [([float("nan"), methane], 0.0, "endpoint"),
                 ([.001, float("inf")], 0.0, "endpoint"),
                 ([0.0, methane], 0.0, "endpoint"),
                 ([.001, .01], float("nan"), "residual"),
                 ([.001, .01], float("inf"), "residual"),
                 ([.001, .01], -1.0, "residual"),
                 ([.001, .01], 1e-5, "residual")]
        for state, residual, message in cases:
            with self.subTest(state=state, residual=residual):
                with patch.object(sys, "argv", ["replay", "--output", str(output)]), patch.object(
                        REPLAY.solver, "solve", return_value=(state, residual, 1)):
                    with self.assertRaisesRegex(ValueError, message):
                        REPLAY.main()
                self.assertFalse(output.exists())

    def test_valid_cli_preserves_both_alpha_endpoint_reports(self):
        output = self.root.parent / (self.root.name + "-valid.json")
        self.addCleanup(lambda: output.unlink(missing_ok=True))
        fingerprints = []
        for flags in ([], ["-O"]):
            subprocess.run([sys.executable] + flags + [str(self.root / "reproduce_reference.py"),
                           "--output", str(output)], check=True, capture_output=True, text=True)
            fingerprints.append(hashlib.sha256(output.read_bytes()).hexdigest())
        self.assertEqual(["86b3d91ed8874477c60ddbe31c0efd607f841c8a5f874355cf1391d5d96b28a7"] * 2,
                         fingerprints)


if __name__ == "__main__":
    unittest.main()
