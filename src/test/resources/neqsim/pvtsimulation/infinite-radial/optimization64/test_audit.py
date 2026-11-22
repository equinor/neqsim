"""Positive and corrupt-evidence admission controls for the full common matrix."""
import copy
import csv
import gzip
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parent


class AdmissionControls(unittest.TestCase):
    """Validate real outputs and deliberately damaged numerical evidence."""

    def run_audit(self, rows=None):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)
            candidate = ROOT/'common-after.csv.gz'
            if rows is not None:
                candidate = path/'candidate.csv.gz'
                with gzip.open(candidate, 'wt', newline='') as stream:
                    writer = csv.DictWriter(stream, fieldnames=['time', 'pressure', 'derivative'])
                    writer.writeheader()
                    writer.writerows(rows)
            result = subprocess.run([sys.executable, str(ROOT/'audit.py'),
                                     '--times', str(ROOT/'common-times.txt'),
                                     '--brackets', str(ROOT/'brackets.csv.gz'),
                                     '--before', str(ROOT/'common-before.csv.gz'),
                                     '--after', str(candidate), '--output', str(path/'result.json')],
                                    stdout=subprocess.PIPE, stderr=subprocess.PIPE)
            return result.returncode

    def test_branch_receipt_controls(self):
        import importlib.util
        spec = importlib.util.spec_from_file_location('matrix_audit64', ROOT/'audit.py')
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        rows = module.bracket_rows(ROOT/'brackets.csv.gz')
        times = [float(t) for t in (ROOT/'common-times.txt').read_text().splitlines()]
        self.assertEqual(module.verify_brackets(rows, times), {'bracketed': 13186, 'underflow': 1538, 'belowMIN': 12})
        changes = [lambda r: r.pop(),
                   lambda r: r[0].update(leftArgument='123'),
                   lambda r: r[0].update(leftTime='0'),
                   lambda r: r[0].update(status='underflow'),
                   lambda r: r[13108].update(frequency=str(float(r[13108]['frequency'])*1.000001))]
        for change in changes:
            damaged = copy.deepcopy(rows)
            change(damaged)
            with self.assertRaises(ValueError):
                module.verify_brackets(damaged, times)

    def test_positive_control(self):
        self.assertEqual(self.run_audit(), 0)

    def test_parent_is_a_failed_monotonicity_control(self):
        with gzip.open(ROOT/'common-before.csv.gz', 'rt', newline='') as stream:
            rows = list(csv.DictReader(stream))
        self.assertNotEqual(self.run_audit(rows), 0)

    def test_damaged_evidence_is_rejected(self):
        with gzip.open(ROOT/'common-after.csv.gz', 'rt', newline='') as stream:
            original = list(csv.DictReader(stream))
        changes = [
            lambda r: r.pop(),
            lambda r: r.append(copy.deepcopy(r[-1])),
            lambda r: r[1].update(time=float('nan')),
            lambda r: r[2].update(pressure=-1),
            lambda r: r[3].update(derivative=float('inf')),
            lambda r: r[4].update(derivative=0),
            lambda r: r[10000].update(derivative=float(r[10000]['derivative'])*.99),
            lambda r: r[20000].update(pressure=float(r[20000]['pressure'])*1.01),
            lambda r: r[50].update(time=0),
            lambda r: r.__setitem__(100, copy.deepcopy(r[99])),
        ]
        for index, change in enumerate(changes):
            with self.subTest(corruption=index):
                rows = copy.deepcopy(original)
                change(rows)
                self.assertNotEqual(self.run_audit(rows), 0)


if __name__ == '__main__':
    unittest.main()
