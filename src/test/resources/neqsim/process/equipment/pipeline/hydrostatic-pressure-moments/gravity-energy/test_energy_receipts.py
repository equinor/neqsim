"""Reject incomplete or corrupted engineering/performance receipts, also under Python -O."""
import csv
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

import qualify

ROOT = Path(__file__).resolve().parent


def read_rows(name):
    """Load retained valid evidence without reconstructing the producer's calculations."""
    with (ROOT / name).open(newline="") as stream:
        return list(csv.reader(stream))


def corruptions(valid):
    """Exercise shape, every numeric column, omission, repetition, and identity order."""
    bad = [valid[:1], valid[:-1], valid + [valid[-1]], [["wrong"]] + valid[1:]]
    for index in sorted({1, (len(valid) - 1) // 2 or 1, len(valid) - 1}):
        for column in range(len(valid[0])):
            for replacement in ["NaN", "Infinity", "-Infinity", "invalid", ""]:
                rows = [row[:] for row in valid]
                rows[index][column] = replacement
                bad.append(rows)
        for row in [valid[index][:-1], valid[index] + ["extra"]]:
            rows = [row[:] for row in valid]
            rows[index] = row
            bad.append(rows)
    if len(valid) > 2:
        swapped = [row[:] for row in valid]
        swapped[1], swapped[-1] = swapped[-1], swapped[1]
        bad.append(swapped)
        repeated = [row[:] for row in valid]
        repeated[-1] = repeated[1][:]
        bad.append(repeated)
    return bad


class EnergyReceiptTest(unittest.TestCase):
    """Bind every published static budget and timing sample to independently checked work."""

    def evaluate(self, name, rows, evaluator):
        """Write a candidate CSV and exercise the actual evidence reader."""
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / name
            with path.open("w", newline="") as stream:
                csv.writer(stream).writerows(rows)
            return evaluator(path)

    def test_native_and_static_receipts(self):
        """Keep valid controls and reject corrupt shape, identities, values and energy signs."""
        references = read_rows("reference.csv")[1:]
        budget = qualify.budget_reference()
        matrices = [
            ("native.csv", lambda path: qualify.audit_native(path, references)),
            ("two-cell-budget.csv", lambda path: qualify.audit_budget(path, budget)),
        ]
        for name, evaluator in matrices:
            valid = read_rows(name)
            self.evaluate(name, valid, evaluator)
            bad = corruptions(valid)
            for column in range(3 if name == "two-cell-budget.csv" else 6):
                rows = [row[:] for row in valid]
                rows[-1][column] = str(float(rows[-1][column]) + 1.0)
                bad.append(rows)
            for index, rows in enumerate(bad):
                with self.subTest(receipt=name, corruption=index):
                    with self.assertRaises((ValueError, TypeError)):
                        self.evaluate(name, rows, evaluator)

    def test_timing_receipt_work_and_checksum(self):
        """Validate work/checksum by a roundoff bound, without a speed acceptance threshold."""
        native = qualify.read_numeric_csv(ROOT / "native.csv", qualify.FIELDS, 162)
        evaluator = lambda path: qualify.audit_timing(path, native)
        valid = read_rows("timing.csv")
        self.assertEqual(7, len(self.evaluate("timing.csv", valid, evaluator)))
        bad = corruptions(valid)
        for column, replacement in [(0, "7"), (1, "199999"), (1, "200000.5"),
                                    (2, "0"), (2, "-1"), (3, "0"), (3, "1")]:
            rows = [row[:] for row in valid]
            rows[-1][column] = replacement
            bad.append(rows)
        for index, rows in enumerate(bad):
            with self.subTest(corruption=index):
                with self.assertRaises((ValueError, TypeError)):
                    self.evaluate("timing.csv", rows, evaluator)
        # Runtime is observational: changing a positive finite timing never alters qualification.
        slow = [row[:] for row in valid]
        slow[1][2] = "1e12"
        self.assertEqual(1e12, self.evaluate("timing.csv", slow, evaluator)[0])

    def test_full_replay_rejects_corrupt_probe_without_stale_success(self):
        """Compile real probes and require the CLI to reject each corrupted result artifact."""
        mutations = [
            ("round < 7", "round < 1", "receipt schema: timing.csv"),
            ('initial + ","', 'Double.NaN + ","', "Nonfinite receipt: two-cell-budget.csv"),
            ("(initial - redistributed)", "(initial - redistributed + 1)", "reference gate failed"),
            ('d + "," + gas', '(d + 1) + "," + gas', "Reordered native identity"),
        ]
        probe = (ROOT / "GravityEnergyProbe.java").read_text()
        flags = ["-O"] if sys.flags.optimize else []
        target = qualify.REPO / "target"
        target.mkdir(exist_ok=True)
        for old, new, diagnostic in mutations:
            self.assertEqual(1, probe.count(old))
            with self.subTest(mutation=old), tempfile.TemporaryDirectory(dir=target) as temporary:
                folder = Path(temporary)
                energy = folder / "gravity-energy"
                coupling = folder / "coupling"
                output = folder / "output"
                energy.mkdir()
                coupling.mkdir()
                output.mkdir()
                shutil.copy(ROOT / "qualify.py", energy)
                for name in ["holdup-reference.csv", "qualify_holdup.py"]:
                    shutil.copy(ROOT.parent / "coupling" / name, coupling)
                (energy / "GravityEnergyProbe.java").write_text(probe.replace(old, new))
                (output / "summary.json").write_text('{"scope":"stale success"}\n')
                result = subprocess.run(
                    [sys.executable, *flags, str(energy / "qualify.py"), "--output-dir", str(output)],
                    capture_output=True, text=True)
                self.assertNotEqual(0, result.returncode, result.stdout)
                self.assertIn(diagnostic, result.stderr)
                self.assertFalse((output / "summary.json").exists())


if __name__ == "__main__":
    unittest.main()
