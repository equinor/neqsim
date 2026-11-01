"""Exercise fixed-matrix corruption and optimized-Python numerical rejection."""
import csv
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from validate_native import read_native

ROOT = Path(__file__).resolve().parent


class AuditTest(unittest.TestCase):
    """Prevent reduced or malformed output from qualifying a pressure-moment matrix."""

    def test_matrix_corruption(self):
        """Keep valid data and reject omission, repetition, alteration and every nonfinite field."""
        inputs = (ROOT / 'inputs.csv').read_text()
        with (ROOT / 'native.csv').open() as stream:
            valid = list(csv.reader(stream))
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / 'native.csv'
            def evaluate(rows):
                """Write one candidate matrix and apply the production evidence reader."""
                with path.open('w', newline='') as output:
                    csv.writer(output).writerows(rows)
                return read_native(path, inputs)
            self.assertEqual(222, len(evaluate(valid)))
            bad = [valid[:2], valid[:-1], valid + [valid[-1]], valid[:1] + valid[2:],
                   valid[:2] + valid[1:2] + valid[3:]]
            for index in [1, 112, 222]:
                for column in range(6):
                    for value in ['NaN', 'Infinity', '-Infinity', 'invalid']:
                        rows = [row[:] for row in valid]
                        rows[index][column] = value
                        bad.append(rows)
                rows = [row[:] for row in valid]
                rows[index] = rows[index][:-1]
                bad.append(rows)
                rows = [row[:] for row in valid]
                rows[index].append('extra')
                bad.append(rows)
                rows = [row[:] for row in valid]
                rows[index][0] = str(float(rows[index][0]) * 1.01)
                bad.append(rows)
            bad.append([['wrong']]+valid[1:])
            swapped = [row[:] for row in valid]
            swapped[1], swapped[2] = swapped[2], swapped[1]
            bad.append(swapped)
            for rows in bad:
                with self.subTest(rows=len(rows), first=rows[1] if len(rows)>1 else None):
                    with self.assertRaises((ValueError, TypeError)):
                        evaluate(rows)
            self.assertEqual(88, len(bad))

    def test_optimized_end_to_end(self):
        """Reject a truncated probe and numerical corruption even when assertions are disabled."""
        source = (ROOT / 'audit.py').read_text()
        repo = next(path for path in ROOT.parents if (path / 'pom.xml').exists())
        (repo / 'target').mkdir(exist_ok=True)
        mutations = [
            ('while ((line = input.readLine()) != null)', 'if ((line = input.readLine()) != null)'),
            ('value.getLiquidMoment() +', '(value.getLiquidMoment() + (depth > 0.0 && depth < diameter ? 1.0 : 0.0)) +'),
            ('e62e0a03a8eeb92d9cbad04b109142501a0903a4', '0000000000000000000000000000000000000000'),
        ]
        for old, new in mutations:
            self.assertIn(old, source)
            with tempfile.TemporaryDirectory(dir=repo / 'target') as temporary:
                folder = Path(temporary)
                (folder / 'audit.py').write_text(source.replace(old, new))
                (folder / 'validate_native.py').write_text((ROOT / 'validate_native.py').read_text())
                result = subprocess.run([sys.executable, '-O', str(folder / 'audit.py')], capture_output=True, text=True)
                self.assertNotEqual(0, result.returncode, result.stdout)
                expected = 'Native matrix is incomplete' if old.startswith('while') else 'Moment tolerance failed' if old.startswith('value') else 'Unexpected pressure-moment source blob'
                self.assertIn(expected, result.stderr)


if __name__ == '__main__':
    unittest.main()
