"""Verify that coupled-aquifer acceptance independently rejects corrupted evidence."""
import copy
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
BENCHMARK = ROOT / 'src/test/resources/neqsim/pvtsimulation/coupled-aquifer'
SPEC = importlib.util.spec_from_file_location('coupled_aquifer_audit', BENCHMARK / 'audit.py')
AUDIT = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(AUDIT)


class CoupledAquiferAuditTest(unittest.TestCase):
    """Exercise real retained endpoints and distinct corruption modes."""

    def setUp(self):
        self.data = json.loads((BENCHMARK / 'native.json').read_text())

    def evaluate(self, mutation):
        """Write a copied fault fixture and return its independently checked result."""
        data = copy.deepcopy(self.data)
        mutation(data)
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'fault.json'
            path.write_text(json.dumps(data))
            return AUDIT.audit(path)

    def test_retained_complete_matrix_passes(self):
        result = AUDIT.audit(BENCHMARK / 'native.json')
        self.assertEqual([], result['failures'])
        self.assertEqual(1920, result['endpoints'])
        self.assertEqual(64, result['replays'])
        self.assertEqual(json.loads((BENCHMARK / 'summary.json').read_text())['cases'], result['cases'])

    def test_nonfinite_metrics_and_endpoints_are_rejected(self):
        for value in [float('nan'), float('inf'), -float('inf')]:
            for metric in AUDIT.METRICS + ['seconds']:
                with self.subTest(value=value, metric=metric):
                    self.assertTrue(self.evaluate(lambda rows: rows[3].__setitem__(metric, value))['failures'])
            for column in range(5):
                with self.subTest(value=value, column=column):
                    self.assertTrue(self.evaluate(lambda rows: rows[3]['rows'][0].__setitem__(column, value))['failures'])

    def test_all_endpoint_columns_are_independently_checked(self):
        for mesh in [0, 1, 2, 3]:
            for column in range(5):
                with self.subTest(mesh=mesh, column=column):
                    def corrupt(rows):
                        rows[mesh]['rows'][0][column] += 1000
                    self.assertTrue(self.evaluate(corrupt)['failures'])

    def test_incomplete_matrix_and_endpoint_shapes_are_rejected(self):
        mutations = [lambda rows: rows.pop(), lambda rows: rows[3].__setitem__('rows', []),
                     lambda rows: rows[3]['rows'][0].pop(), lambda rows: rows[3].pop('maxWaterErrorM3')]
        for mutation in mutations:
            self.assertTrue(self.evaluate(mutation)['failures'])

    def test_identity_order_and_mesh_are_checked(self):
        mutations = [lambda rows: rows[3].__setitem__('capacityRatio', .3),
                     lambda rows: rows[3].__setitem__('fraction', .3),
                     lambda rows: rows[3].__setitem__('intervals', 32),
                     lambda rows: rows.__setitem__(slice(0, 4), copy.deepcopy(rows[4:8]))]
        for mutation in mutations:
            self.assertTrue(self.evaluate(mutation)['failures'])

    def test_forged_and_negative_diagnostics_are_rejected(self):
        for key in ['maxPressureErrorBara', 'maxWaterErrorM3']:
            self.assertTrue(self.evaluate(lambda rows: rows[3].__setitem__(key, 0))['failures'])
        for key in AUDIT.METRICS + ['seconds']:
            self.assertTrue(self.evaluate(lambda rows: rows[3].__setitem__(key, -1))['failures'])

    def test_native_pore_diagnostic_remains_a_finite_bounded_native_check(self):
        self.assertTrue(self.evaluate(lambda rows: rows[3].__setitem__('finalPoreClosureM3', .001))['failures'])

    def test_command_rejects_corruption_with_python_optimization(self):
        data = copy.deepcopy(self.data)
        data[3]['rows'] = []
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'fault.json'
            path.write_text(json.dumps(data))
            result = subprocess.run([sys.executable, '-O', str(BENCHMARK / 'audit.py'), str(path)],
                                    capture_output=True, text=True, check=False)
            self.assertEqual(1, result.returncode)
            self.assertTrue(json.loads(result.stdout)['failures'])


if __name__ == '__main__':
    unittest.main()
