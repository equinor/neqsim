"""Deliberate evidence-corruption controls for the optional native qualification."""
import csv
import os
from pathlib import Path
import tempfile
import unittest

import qualify

EVIDENCE = Path(os.environ.get('NEQSIM_PHYSICAL_EVIDENCE', '.'))


class QualificationControls(unittest.TestCase):
    """Audit integrity rather than mirror production conversion arithmetic."""

    def setUp(self):
        self.rows = qualify.read(EVIDENCE / 'q71-1-after.csv')

    def reject_read(self, rows):
        """Reject changed native identity even when rows remain parseable CSV."""
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / 'native.csv'
            with path.open('w') as stream:
                writer = csv.DictWriter(stream, fieldnames=self.rows[0].keys())
                writer.writeheader()
                writer.writerows(rows)
            with self.assertRaises(ValueError):
                qualify.read(path)

    def test_positive_and_all_loss_of_identity_controls(self):
        self.assertEqual([], qualify.audit(self.rows)['failures'])
        self.reject_read(self.rows[:-1])
        self.reject_read(self.rows + [self.rows[-1]])
        reordered = [dict(r) for r in self.rows]
        reordered[0], reordered[1] = reordered[1], reordered[0]
        self.reject_read(reordered)
        altered = [dict(r) for r in self.rows]
        altered[0]['inputs'] = '1;1;.2;1;1;1'
        self.reject_read(altered)

    def test_finite_results_cannot_be_zero_or_nonfinite(self):
        for value in ('0', '-1', 'NaN', 'Infinity'):
            rows = [dict(r) for r in self.rows]
            rows[0]['result'] = value
            self.assertTrue(qualify.audit(rows)['failures'])

    def test_exception_and_output_forms_are_exclusive(self):
        rows = [dict(r) for r in self.rows]
        rows[0]['error'] = 'ArithmeticException'
        self.assertTrue(qualify.audit(rows)['failures'])
        rejected = next(i for i, r in enumerate(rows) if r['error'] == 'IllegalArgumentException')
        rows = [dict(r) for r in self.rows]
        rows[rejected]['result'] = '0'
        self.assertTrue(qualify.audit(rows)['failures'])
        rows[rejected]['error'] = ''
        self.assertTrue(qualify.audit(rows)['failures'])

    def test_exact_midpoint_classification_cannot_be_relabelled(self):
        for i, row in enumerate(self.rows):
            expected_error, expected_value = qualify.expected(row)
            if expected_error == 'ArithmeticException':
                rows = [dict(r) for r in self.rows]
                rows[i]['result'] = '4.9e-324'
                rows[i]['error'] = ''
                self.assertTrue(qualify.audit(rows)['failures'])

    def test_runtime_identity_and_checksums_are_mandatory(self):
        paths = [EVIDENCE / ('q71-' + str(i) + '-' + kind + '-runtime.csv')
                 for i, kind in enumerate(('parent', 'after', 'after', 'parent'))]
        self.assertTrue(qualify.runtime(paths)['accepted'])
        with paths[0].open() as stream:
            original = list(csv.DictReader(stream))
        for mutation in ('missing', 'nonpositive', 'checksum'):
            with tempfile.TemporaryDirectory() as temporary:
                path = Path(temporary) / 'runtime.csv'
                rows = [dict(r) for r in original]
                if mutation == 'missing':
                    rows.pop()
                elif mutation == 'nonpositive':
                    rows[0]['nanos'] = '0'
                else:
                    rows[0]['checksum'] = '1'
                with path.open('w') as stream:
                    writer = csv.DictWriter(stream, fieldnames=original[0].keys())
                    writer.writeheader()
                    writer.writerows(rows)
                with self.assertRaises(ValueError):
                    qualify.runtime([path] + paths[1:])


if __name__ == '__main__':
    unittest.main()
