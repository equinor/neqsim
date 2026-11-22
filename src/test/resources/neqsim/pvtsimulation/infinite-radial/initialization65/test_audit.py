"""Positive controls and deliberately damaged identity/certificate evidence."""
import copy
import json
import math
import unittest
import gzip
from pathlib import Path
import tempfile

import audit


class AdmissionTest(unittest.TestCase):
    def test_matrix_identity_and_corruptions(self):
        before = audit.read_rows(audit.ROOT / 'before.csv.gz')
        after = audit.read_rows(audit.ROOT / 'after.csv.gz')
        times = [float(t) for t in (audit.ROOT.parent / 'optimization64/common-times.txt').read_text().splitlines()]
        audit.matrices(before, after, times)
        damaged = copy.deepcopy(after)
        damaged[10000]['pressure'] = str(math.nextafter(float(damaged[10000]['pressure']), math.inf))
        with self.assertRaises(ValueError):
            audit.matrices(before, damaged, times)
        damaged = copy.deepcopy(after)
        damaged[10000]['time'] = 'NaN'
        with self.assertRaises(ValueError):
            audit.matrices(before, damaged, times)
        with self.assertRaises(ValueError):
            audit.matrices(before, after[:-1], times)

    def test_grid_identity_and_certificate_corruptions(self):
        before = audit.read_rows(audit.ROOT / 'grid-before.csv.gz')
        after = audit.read_rows(audit.ROOT / 'grid-after.csv.gz')
        audit.grids(before, after)
        for index, key, value in [(12, 'prefixFrequency', '1.0'),
                                   (767, 'prefixBound', '0.0'),
                                   (1000, 'weight', '0.0'),
                                   (1000, 'suffix', 'NaN'),
                                   (0, 'prefixBound', 'inf')]:
            damaged = copy.deepcopy(after)
            damaged[index][key] = value
            with self.assertRaises(ValueError):
                audit.grids(before, damaged)

    def test_conflicting_dual_container(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'qualification.json'
            path.write_bytes(b'{"value":1}\n')
            path.with_suffix('.json.gz').write_bytes(gzip.compress(path.read_bytes(), mtime=0))
            self.assertEqual({'value': 1}, audit.native_json(path))
            path.write_bytes(b'{"value":2}\n')
            with self.assertRaises(ValueError):
                audit.native_json(path)

    def test_execution_and_container_corruptions(self):
        runs = json.loads((audit.ROOT / 'runtime-executions.json').read_text())
        audit.executions(runs)
        damaged = copy.deepcopy(runs)
        damaged[4]['uncompressedSha256'] = '0' * 64
        with self.assertRaises(ValueError):
            audit.executions(damaged)
        damaged = copy.deepcopy(runs)
        damaged[4]['output'] = '0-before-qualification.json.gz'
        with self.assertRaises(ValueError):
            audit.executions(damaged)

    def test_timing_sample_corruptions(self):
        data = audit.native_json(audit.ROOT / '1-after-qualification.json')
        key = 'carterTracy1000IntervalsNs'
        audit.timing_samples(data, key)
        for value in [-1, float('nan'), float('inf')]:
            damaged = copy.deepcopy(data)
            damaged[key][0] = value
            with self.assertRaises(ValueError):
                audit.timing_samples(damaged, key)
        data[key].pop()
        with self.assertRaises(ValueError):
            audit.timing_samples(data, key)

    def test_native_and_concurrent_corruptions(self):
        before = audit.native_json(audit.ROOT / '0-before-qualification.json')
        after = audit.native_json(audit.ROOT / '1-after-qualification.json')
        reference = json.loads((audit.ROOT.parent / 'reference.json').read_text())
        audit.native_identity(before, after, reference)
        damaged = copy.deepcopy(after)
        damaged['unitStepCarterTracy'][10]['history'][2] *= 1.01
        with self.assertRaises(ValueError):
            audit.native_identity(before, damaged, reference)
        damaged = copy.deepcopy(after)
        damaged['unitStepCarterTracy'][0]['history'][0] = -0.0
        with self.assertRaises(ValueError):
            audit.native_identity(before, damaged, reference)
        concurrent = json.loads((audit.ROOT / '1-after-startup-concurrent.json').read_text())
        ordinary = concurrent['ordinaryBeforeFallback']
        audit.startup(concurrent, 'concurrent', ordinary)
        concurrent['firstValues'].pop()
        with self.assertRaises(ValueError):
            audit.startup(concurrent, 'concurrent', ordinary)
        for mode, first in [('unexpected', []), ('concurrent', [[-1.0, -2.0]] * 8)]:
            damaged = json.loads((audit.ROOT / '1-after-startup-concurrent.json').read_text())
            damaged['mode'], damaged['firstValues'] = mode, first
            with self.assertRaises(ValueError):
                audit.startup(damaged, 'concurrent', ordinary)
        damaged = json.loads((audit.ROOT / '1-after-startup-1.0.json').read_text())
        damaged['firstValues'] = [[-1.0, -2.0]]
        with self.assertRaises(ValueError):
            audit.startup(damaged, '1.0', ordinary)


if __name__ == '__main__':
    unittest.main()
