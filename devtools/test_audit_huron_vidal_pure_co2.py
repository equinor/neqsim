"""Exercise retained-data rejection without modifying source data or thermodynamics."""
import csv
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "src/test/resources/neqsim/thermo/mixingrule/hv-co2-source"


class PureCo2AuditTest(unittest.TestCase):
    """Check valid coverage and corrupt residuals hidden by aggregate maxima."""

    def run_audit(self, column=None, value=None, row_index=-1):
        """Run the actual auditor on a detached CSV with one optional corruption."""
        with (DATA / "pure-co2-native.csv").open(newline="") as stream:
            rows = list(csv.DictReader(stream))
        if column is not None:
            rows[row_index][column] = value
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "native.csv"
            with path.open("w", newline="") as stream:
                writer = csv.DictWriter(stream, fieldnames=list(rows[0]))
                writer.writeheader()
                writer.writerows(rows)
            return subprocess.run(
                [sys.executable, str(ROOT / "devtools/audit_huron_vidal_pure_co2.py"),
                 "--native", str(path), "--output", str(Path(directory) / "independent.csv")],
                cwd=ROOT, capture_output=True, text=True, check=False,
            )

    def test_retained_native_matrix_passes(self):
        """Preserve all 384 valid source rows and their existing numerical limits."""
        result = self.run_audit()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(json.loads(result.stdout)["native"]["flashes"], 384)

    def test_each_residual_rejects_nonfinite_negative_and_out_of_limit_values(self):
        """Reject corrupt first/middle/last rows before calculating maxima."""
        for column, limit in (("max_EOS_pressure_error_bara", "5e-7"),
                              ("log_fugacity_error", "1e-8")):
            for value in ("nan", "inf", "-inf", "-1", limit):
                for row_index in (0, 192, -1):
                    with self.subTest(column=column, value=value, row_index=row_index):
                        result = self.run_audit(column, value, row_index)
                        self.assertNotEqual(result.returncode, 0, result.stdout)
                        self.assertIn(column, result.stderr)


if __name__ == "__main__":
    unittest.main()
