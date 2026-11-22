"""Negative admission controls for retained native numerical qualification data."""
import copy
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parent


class AdmissionControls(unittest.TestCase):
    """Exercise positive control and deliberate loss/corruption of actual outputs."""

    def run_audit(self, data):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)
            (path/'native.json').write_text(json.dumps(data))
            return subprocess.run([sys.executable, str(ROOT/'qualify_carter_tracy.py'),
                                   '--native', str(path/'native.json'),
                                   '--reference', str(ROOT/'reference.json'),
                                   '--output', str(path/'audit.json')],
                                  stdout=subprocess.PIPE, stderr=subprocess.PIPE).returncode

    def test_positive_control(self):
        self.assertEqual(self.run_audit(json.loads((ROOT/'native-java8.json').read_text())), 0)

    def test_corrupted_and_incomplete_evidence_is_rejected(self):
        original = json.loads((ROOT/'native-java8.json').read_text())
        changes = [
            lambda d: d['referenceComparisons'].pop(),
            lambda d: d['referenceComparisons'][3].update(pressure=0),
            lambda d: d['referenceComparisons'][4].update(derivative=float('nan')),
            lambda d: d['unitStepCarterTracy'].pop(),
            lambda d: d['unitStepCarterTracy'][1].update(intervals=32),
            lambda d: d['unitStepCarterTracy'][0]['history'].__setitem__(3, -1),
            lambda d: d['unitStepCarterTracy'][0]['history'].__setitem__(4, float('inf')),
            lambda d: d['unitStepCarterTracy'][0]['time'].__setitem__(3, 0),
            lambda d: d['unitStepCarterTracy'][0]['time'].__setitem__(3, float('nan')),
            lambda d: d['unitStepCarterTracy'][0]['time'].__setitem__(3, d['unitStepCarterTracy'][0]['time'][3]*1.01),
            lambda d: d['unitStepCarterTracy'][0].update(influx=123),
            lambda d: d['unitStepCarterTracy'][0]['history'].pop(),
        ]
        for index, change in enumerate(changes):
            with self.subTest(corruption=index):
                data = copy.deepcopy(original)
                change(data)
                self.assertNotEqual(self.run_audit(data), 0)


if __name__ == '__main__':
    unittest.main()
