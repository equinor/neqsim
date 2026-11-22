"""Failure controls for the optional independent finite-scenario posterior audit."""
import copy
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
HERE = ROOT / 'src/test/resources/neqsim/pvtsimulation/discrete-posterior'
SPEC = importlib.util.spec_from_file_location('discrete_posterior_audit', HERE / 'audit.py')
AUDIT = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(AUDIT)


class DiscretePosteriorAuditTest(unittest.TestCase):
    """Pin complete evidence and ensure corruption cannot become accepted output."""

    def setUp(self):
        self.data = json.loads((HERE / 'native.json').read_text())

    def assess(self, changed):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'native.json'
            path.write_text(json.dumps(changed))
            return AUDIT.audit(path)

    def test_complete_native_matrix(self):
        result = self.assess(self.data)
        self.assertEqual([], result['failures'])
        self.assertEqual(1635, result['endpoints'])
        self.assertEqual(10, result['cases'])

    def test_nonfinite_numbers_are_rejected(self):
        fields = ['prior', 'weights', 'chiSquare', 'means', 'shifts']
        for field in fields:
            for invalid in [float('nan'), float('inf'), -float('inf')]:
                with self.subTest(field=field, invalid=invalid):
                    changed = copy.deepcopy(self.data)
                    changed[0][field][0] = invalid
                    self.assertTrue(self.assess(changed)['failures'])
        for field in ['effectiveCount', 'logEvidence', 'rejectedMass', 'seconds', 'quantileSeconds']:
            for invalid in [float('nan'), float('inf'), -float('inf')]:
                with self.subTest(field=field, invalid=invalid):
                    changed = copy.deepcopy(self.data)
                    changed[0][field] = invalid
                    self.assertTrue(self.assess(changed)['failures'])

    def test_runtime_requires_finite_nonnegative_evidence(self):
        for field in ['seconds', 'quantileSeconds']:
            for invalid in [None, -1., True, '1.0']:
                with self.subTest(field=field, invalid=invalid):
                    changed = copy.deepcopy(self.data)
                    changed[0][field] = invalid
                    self.assertTrue(self.assess(changed)['failures'])
            changed = copy.deepcopy(self.data)
            del changed[0][field]
            self.assertTrue(self.assess(changed)['failures'])

    def test_endpoint_columns_and_summaries_are_independently_checked(self):
        for column, offset in enumerate([1., 1., 1., 1., 1000.]):
            with self.subTest(column=column):
                changed = copy.deepcopy(self.data)
                changed[0]['rows'][0][-1][column] += offset
                self.assertTrue(self.assess(changed)['failures'])
        for field in ['parameterQuantiles', 'pressureQuantiles', 'correlations']:
            with self.subTest(field=field):
                changed = copy.deepcopy(self.data)
                changed[0][field][0][0] += 1.
                self.assertTrue(self.assess(changed)['failures'])
        changed = copy.deepcopy(self.data)
        changed[0]['weights'][0] += .01
        self.assertTrue(self.assess(changed)['failures'])

    def test_incomplete_and_wrong_identity_evidence_is_rejected(self):
        changes = []
        changes.append(self.data[:-1])
        changed = copy.deepcopy(self.data)
        changed[0]['name'] = 'wrong-case'
        changes.append(changed)
        changed = copy.deepcopy(self.data)
        changed[0]['rows'][0] = []
        changes.append(changed)
        changed = copy.deepcopy(self.data)
        changed[0]['rows'][0][0] = [0.]
        changes.append(changed)
        changed = copy.deepcopy(self.data)
        changed[0]['parameterQuantiles'] = []
        changes.append(changed)
        changed = copy.deepcopy(self.data)
        changed[0]['rejected'] = ['invented failure']
        changes.append(changed)
        for index, invalid in enumerate(changes):
            with self.subTest(index=index):
                self.assertTrue(self.assess(invalid)['failures'])

    def test_reference_inverse_cdf_preserves_positive_upper_tail(self):
        quantile = AUDIT.REFERENCE.quantile
        self.assertEqual(1100., quantile([900., 1100.], [1., 1e-20], 1.))
        self.assertEqual(900., quantile([900., 1100.], [1., 0.], 1.))
        self.assertEqual(900., quantile([1100., 900.], [.5, .5], .5))

    def test_cli_rejects_fault_with_assertions_disabled(self):
        changed = copy.deepcopy(self.data)
        changed[0]['weights'][0] = float('nan')
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'invalid.json'
            path.write_text(json.dumps(changed))
            result = subprocess.run([sys.executable, '-O', str(HERE / 'audit.py'), str(path)],
                                    capture_output=True, text=True)
            self.assertNotEqual(0, result.returncode)
            self.assertTrue(json.loads(result.stdout)['failures'])


if __name__ == '__main__':
    unittest.main()
