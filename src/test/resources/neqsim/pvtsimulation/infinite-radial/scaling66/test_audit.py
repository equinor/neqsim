"""Admit complete positive controls and reject altered physics/protocol/output evidence."""
import copy
import json
import unittest

import audit
import qualify_runtime


class AdmissionTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.data = audit.load(audit.ROOT / 'after.json.gz')

    def test_complete_positive_matrix_and_missing_case(self):
        self.assertFalse(audit.matrix(self.data)['failures'])
        damaged = copy.deepcopy(self.data)
        damaged['rows'].pop()
        with self.assertRaises(ValueError):
            audit.matrix(damaged)

    def test_protocol_and_water_corruptions(self):
        source = next(row for row in self.data['rows'] if row['timeExponent'] == -70
                      and row['supportM3PerBar'] == row['dropScaleBar'] == 1
                      and row['profile'] == 'step' and row['intervals'] == 4)
        source = copy.deepcopy(source)
        source['id'] = 0
        identity = [(-70, 1.0, 1.0, 'step', 4)]
        for key, index, value in [('waterM3', 1, '0.0'), ('waterM3', 1, '-1.0'),
                                   ('waterM3', 1, 'NaN')]:
            damaged = copy.deepcopy(source)
            damaged[key][index] = value
            self.assertTrue(audit.matrix({'java': self.data['java'], 'rows': [damaged]}, identity)['failures'])
        for key, value in [('supportM3PerBar', 2), ('radius', '100')]:
            damaged = copy.deepcopy(source)
            damaged[key] = value
            with self.assertRaises(ValueError):
                audit.matrix({'java': self.data['java'], 'rows': [damaged]}, identity)
        damaged = copy.deepcopy(source)
        damaged['time'][1] = '1e-80'
        with self.assertRaises(ValueError):
            audit.matrix({'java': self.data['java'], 'rows': [damaged]}, identity)

    def test_complete_transition_history_corruptions(self):
        self.assertFalse(audit.transitions(self.data)['failures'])
        for value in ['0.0', '-1.0', 'NaN']:
            damaged = copy.deepcopy(self.data)
            damaged['transitions'][1]['waterM3'][1] = value
            self.assertTrue(audit.transitions(damaged)['failures'])
        damaged = copy.deepcopy(self.data)
        damaged['transitions'][1]['waterM3'][0] = '-123'
        with self.assertRaises(ValueError):
            audit.transitions(damaged)
        damaged = copy.deepcopy(self.data)
        damaged['transitions'][0]['message'] = 'index 1'
        self.assertTrue(audit.transitions(damaged)['failures'])
        damaged = copy.deepcopy(self.data)
        damaged['transitions'][1]['derivative'].pop()
        with self.assertRaises(ValueError):
            audit.transitions(damaged)

    def test_ambiguous_exception_and_output(self):
        damaged = copy.deepcopy(self.data['transitions'][0])
        damaged['waterM3'] = ['0.0', '1.0', '2.0']
        with self.assertRaises(ValueError):
            audit.original_recurrence(damaged)

    def test_bounded_and_analytic_corruptions(self):
        self.assertFalse(audit.bounded(self.data)['failures'])
        damaged = copy.deepcopy(self.data)
        damaged['boundedArithmetic'][2]['waterM3'][1] = '0.0'
        self.assertTrue(audit.bounded(damaged)['failures'])
        damaged = copy.deepcopy(self.data)
        damaged['analyticLimits'][1]['waterM3'][-1] = '0.0'
        self.assertTrue(audit.analytic(damaged)['failures'])

    def test_original_history_corruptions(self):
        rows = audit.load(audit.ROOT / '1-after-qualification.json.gz')['unitStepCarterTracy']
        qualify_runtime.compare_histories(rows, rows)
        with self.assertRaises(ValueError):
            qualify_runtime.compare_histories(rows, rows[:-1])
        damaged = copy.deepcopy(rows)
        damaged[10]['history'][2] = float('nan')
        with self.assertRaises(ValueError):
            qualify_runtime.compare_histories(rows, damaged)


if __name__ == '__main__':
    unittest.main()
