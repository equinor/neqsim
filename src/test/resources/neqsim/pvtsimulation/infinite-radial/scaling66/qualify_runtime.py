"""Retain ordinary-workload cost and unchanged original independent-reference gates."""
import importlib.util
import json
import math
from pathlib import Path
import statistics

ROOT = Path(__file__).resolve().parent


def require(condition, message):
    if not condition:
        raise ValueError(message)


def compare_histories(original_rows, current_rows):
    require(len(original_rows) == len(current_rows) == 42, 'Incomplete original CT cases')
    maximum = 0.0
    for original, row in zip(original_rows, current_rows):
        require(original['lastTime'] == row['lastTime'] and original['intervals'] == row['intervals']
                and original['time'] == row['time'], 'Changed original CT protocol')
        require(len(row['history']) == len(original['history']) == row['intervals'] + 1,
                'Incomplete original CT output')
        for a, b in zip(original['history'], row['history']):
            require(math.isfinite(b) and b >= 0 and abs(a - b) <= 1e-12 + 1e-12 * abs(a),
                    'Original CT numerical agreement')
            maximum = max(maximum, abs(a - b) / max(1.0, abs(a)))
    return maximum


def main():
    source = ROOT.parent / 'initialization65/audit.py'
    spec = importlib.util.spec_from_file_location('prior_identity', source)
    prior = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(prior)
    prior.ROOT = ROOT
    runs = json.loads((ROOT / 'runtime-executions.json').read_text())
    prior.executions(runs)
    samples = []
    for index, label in enumerate(['before', 'after', 'after', 'before']):
        data = prior.native_json(ROOT / f'{index}-{label}-qualification.json')
        if samples:
            prior.identical_payload(samples[0]['referenceComparisons'], data['referenceComparisons'])
        samples.append(data)
    prior.identical_payload(samples[0]['unitStepCarterTracy'], samples[3]['unitStepCarterTracy'])
    prior.identical_payload(samples[1]['unitStepCarterTracy'], samples[2]['unitStepCarterTracy'])
    maximum = max(compare_histories(samples[0]['unitStepCarterTracy'], current['unitStepCarterTracy'])
                  for current in samples[1:])
    ordinary = next(row for row in samples[0]['referenceComparisons'] if row['time'] == 1.0)
    for index, label in enumerate(['before', 'after', 'after', 'before']):
        for mode in ['1.0', '4.9E-324', 'concurrent']:
            prior.startup(json.loads((ROOT / f'{index}-{label}-startup-{mode}.json').read_text()), mode,
                          [ordinary['pressure'], ordinary['derivative']])
    metrics = {}
    for key in ['coldFirstQueryNs', 'separatePressureDerivative20000QueriesNs', 'carterTracy1000IntervalsNs']:
        values = [statistics.median(prior.timing_samples(data, key)) for data in samples]
        before, after = [values[0], values[3]], values[1:3]
        ratio = max(after) / min(before)
        if key == 'carterTracy1000IntervalsNs':
            require(ratio <= 1.20, 'Frozen ordinary CT runtime target failed')
        metrics[key] = {'before': before, 'after': after, 'maximumCandidateToMinimumParent': ratio}
    result = {'claim': 'Ordinary-workload ABBA cost and original CT agreement; not a general speedup',
              'java': samples[0]['java'], 'originalHistories': 42,
              'maximumOriginalHistoryDifferenceOverMaxOneVolume': maximum, 'metrics': metrics}
    (ROOT / 'runtime-comparison.json').write_text(json.dumps(result, indent=2, allow_nan=False) + '\n')


if __name__ == '__main__':
    main()
