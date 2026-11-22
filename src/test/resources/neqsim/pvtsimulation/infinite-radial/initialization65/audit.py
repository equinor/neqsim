"""Audit initialization-only changes with strict identity and unchanged independent bounds."""
import csv
import gzip
import hashlib
import importlib.util
import json
import math
from pathlib import Path
import statistics
import struct

ROOT = Path(__file__).resolve().parent


def require(condition, message):
    if not condition:
        raise ValueError(message)


def bits(value):
    return struct.pack('>d', float(value))


def native_json(path):
    compressed = path.with_suffix(path.suffix + '.gz')
    raw = path.read_bytes() if path.is_file() else None
    payload = gzip.decompress(compressed.read_bytes()) if compressed.is_file() else None
    require(raw is not None or payload is not None, 'Missing native payload')
    if raw is not None and payload is not None:
        require(raw == payload, 'Conflicting raw and compressed native payloads')
    return json.loads(payload if payload is not None else raw)


def read_rows(path):
    with gzip.open(path, 'rt', newline='') as stream:
        return list(csv.DictReader(stream))


def matrices(before, after, times):
    require(len(before) == len(after) == len(times) == 51216, 'Incomplete matrix')
    previous = 0.0
    for index, (a, b, time) in enumerate(zip(before, after, times)):
        require(set(a) == set(b) == {'time', 'pressure', 'derivative'}, 'Matrix schema')
        require(bits(a['time']) == bits(b['time']) == bits(time), 'Changed time identity')
        require(math.isfinite(time) and time > 0, 'Invalid time')
        for key in ['pressure', 'derivative']:
            value = float(b[key])
            require(math.isfinite(value) and value > 0, 'Invalid response')
            require(bits(a[key]) == bits(b[key]), f'Changed {key} bits at {index}')
        require(float(b['pressure']) >= previous, 'Pressure decrease')
        previous = float(b['pressure'])
    tiny = math.sqrt(times[0])
    require(abs(float(after[0]['pressure']) / tiny - 2 / math.sqrt(math.pi)) <= 1e-12,
            'Small-time pressure')
    require(abs(float(after[0]['derivative']) * tiny - 1 / math.sqrt(math.pi)) <= 1e-12,
            'Small-time derivative')
    require(abs(float(after[-1]['derivative']) * times[-1] - .5) <= 1e-12,
            'Late-time derivative')
    return {'rows': len(after), 'changedOutputBits': 0, 'pressureDecreases': 0}


def grids(before, after):
    require(len(before) == len(after) == 6656, 'Incomplete full grid')
    suffix = 0.0
    for index in reversed(range(6656)):
        a, b = before[index], after[index]
        require(int(a['index']) == int(b['index']) == index, 'Changed grid order')
        for key in ['frequency', 'weight', 'suffix']:
            require(bits(a[key]) == bits(b[key]), 'Changed full grid bits')
            require(math.isfinite(float(b[key])) and float(b[key]) > 0, 'Invalid full grid')
        suffix = max(suffix, float(b['weight']))
        require(bits(suffix) == bits(b['suffix']), 'Invalid full suffix maximum')
        if index < 768:
            require(bits(b['prefixFrequency']) == bits(b['frequency']), 'Changed prefix frequency')
            require(bits(b['prefixWeight']) == bits(b['weight']), 'Changed prefix weight')
            require(math.isfinite(float(b['prefixBound'])) and float(b['prefixBound']) > 0,
                    'Invalid prefix bound')
            require(float(b['prefixBound']) >= suffix, 'Prefix bound misses full suffix')
        else:
            require(all(b[key] == '' for key in ['prefixFrequency', 'prefixWeight', 'prefixBound']),
                    'Unspecified prefix extent')
    bound = float(after[767]['prefixBound'])
    omitted_max = max(float(row['weight']) for row in after[768:])
    require(bound >= omitted_max, 'Deferred weight exceeds certificate')
    return {'fullNodes': 6656, 'prefixNodes': 768, 'changedNodeWeightBits': 0,
            'conservativeBound': bound, 'maximumDeferredWeight': omitted_max,
            'boundToMaximumRatio': bound / omitted_max}


def identical_payload(before, after):
    require(type(before) is type(after), 'Changed payload type')
    if isinstance(before, dict):
        require(before.keys() == after.keys(), 'Changed payload keys')
        for key in before:
            identical_payload(before[key], after[key])
    elif isinstance(before, list):
        require(len(before) == len(after), 'Changed payload extent')
        for a, b in zip(before, after):
            identical_payload(a, b)
    elif isinstance(before, float):
        require(math.isfinite(before) and math.isfinite(after) and bits(before) == bits(after),
                'Changed payload bits')
    else:
        require(before == after, 'Changed payload value')


def timing_samples(data, key):
    samples = data[key] if isinstance(data[key], list) else [data[key]]
    require(len(samples) == (1 if key == 'coldFirstQueryNs' else 7), 'Incomplete timing samples')
    require(all(type(v) in [int, float] and math.isfinite(v) and v > 0 for v in samples),
            'Invalid timing sample')
    return samples


def native_identity(before, after, reference):
    for key in ['referenceComparisons', 'unitStepCarterTracy', 'checksum']:
        identical_payload(before[key], after[key])
    require(len(after['referenceComparisons']) == len(reference['rows']) == 20,
            'Incomplete independent comparisons')
    for row, ref in zip(after['referenceComparisons'], reference['rows']):
        require(bits(row['time']) == bits(ref['time']), 'Changed MP time identity')
        for key, absolute, relative in [('pressure', 1e-7, 1e-6), ('derivative', 1e-6, 1e-5)]:
            target = float(ref[key]['digits35'])
            require(math.isfinite(row[key]) and abs(row[key] - target) <= absolute + relative * target,
                    'Independent MP comparison')
    require(len(after['unitStepCarterTracy']) == 42, 'Incomplete CT histories')


def startup(data, expected_mode, expected_ordinary):
    require(data['java'] == '1.8.0_512-b09', 'Changed Java8 runtime')
    require(data['mode'] == expected_mode, 'Changed startup mode')
    require(expected_mode in ['1.0', '4.9E-324', 'concurrent'], 'Unknown startup mode')
    pairs = data['firstValues'] + [data['tinyValues'], data['ordinaryBeforeFallback'], data['ordinaryAfterFallback']]
    require(all(isinstance(row, list) and len(row) == 2 and
                all(type(v) in [int, float] and math.isfinite(v) and v > 0 for v in row)
                for row in pairs), 'Invalid startup response')
    require(data['ordinaryBeforeFallback'] == expected_ordinary, 'Changed ordinary startup output')
    expected_count = 8 if expected_mode == 'concurrent' else 1
    require(len(data['firstValues']) == expected_count, 'Missing startup worker')
    expected_first = expected_ordinary if expected_mode == '1.0' else data['tinyValues']
    require(all(row == expected_first for row in data['firstValues']), 'Changed first startup response')
    require(all(type(data[key]) in [int, float] and math.isfinite(data[key]) and data[key] > 0
                for key in ['coldNs', 'deferredTinyNs']), 'Invalid startup timing')
    require(data['ordinaryBeforeFallback'] == data['ordinaryAfterFallback'], 'Initialization changes result')
    tiny = math.sqrt(float.fromhex('0x0.0000000000001p-1022'))
    p, d = data['tinyValues']
    require(abs(p / tiny - 2 / math.sqrt(math.pi)) <= 1e-12, 'Tiny pressure limit')
    require(abs(d * tiny - 1 / math.sqrt(math.pi)) <= 1e-12, 'Tiny derivative limit')
    if data['mode'] == 'concurrent':
        require(len(data['firstValues']) == 8, 'Missing concurrent worker')
        require(all(row == data['tinyValues'] for row in data['firstValues']), 'Concurrent result changed')


def executions(runs):
    require(len(runs) == 16 and all(run['exit'] == 0 for run in runs), 'Incomplete or failed native executions')
    kinds = ['qualification', 'startup-1.0', 'startup-4.9E-324', 'startup-concurrent']
    for index, label in enumerate(['before', 'after', 'after', 'before']):
        for offset, kind in enumerate(kinds):
            run = runs[index * 4 + offset]
            suffix = '.json.gz' if kind == 'qualification' else '.json'
            expected = f'{index}-{label}-{kind}' + suffix
            require(run['label'] == label and run['kind'] == kind and run['output'] == expected,
                    'Changed ABBA execution identity')
            if kind == 'qualification':
                payload = gzip.decompress((ROOT / expected).read_bytes())
                require(hashlib.sha256(payload).hexdigest() == run['uncompressedSha256'],
                        'Changed native container payload')
    return runs


def main():
    prior = ROOT.parent / 'optimization64'
    times_file = prior / 'common-times.txt'
    require(hashlib.sha256(times_file.read_bytes()).hexdigest() ==
            '4dd584c2fe1e556cea6a7e7355ff773fe0d1af1e4bce8b7f0cbdec62dbbcbbd6', 'Changed frozen vector')
    times = [float(t) for t in times_file.read_text().splitlines()]
    require(all(b > a for a, b in zip(times, times[1:])), 'Unordered frozen vector')
    report = {'matrix': matrices(read_rows(ROOT / 'before.csv.gz'), read_rows(ROOT / 'after.csv.gz'), times),
              'grid': grids(read_rows(ROOT / 'grid-before.csv.gz'), read_rows(ROOT / 'grid-after.csv.gz'))}
    spec = importlib.util.spec_from_file_location('prior_audit', prior / 'audit.py')
    original = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(original)
    report['originalShortcutCoverage'] = original.verify_brackets(
        original.bracket_rows(prior / 'brackets.csv.gz'), times)
    reference = json.loads((ROOT.parent / 'reference.json').read_text())
    runs = json.loads((ROOT / 'runtime-executions.json').read_text())
    executions(runs)
    qualifications = []
    for index, label in enumerate(['before', 'after', 'after', 'before']):
        data = native_json(ROOT / f'{index}-{label}-qualification.json')
        require(data['java'] == '1.8.0_512-b09', 'Changed qualification runtime')
        qualifications.append(data)
        native_identity(qualifications[0], data, reference)
        for mode in ['1.0', '4.9E-324', 'concurrent']:
            ordinary = next(row for row in qualifications[0]['referenceComparisons'] if row['time'] == 1.0)
            startup(json.loads((ROOT / f'{index}-{label}-startup-{mode}.json').read_text()), mode,
                    [ordinary['pressure'], ordinary['derivative']])
    metrics = {}
    for key in ['coldFirstQueryNs', 'separatePressureDerivative20000QueriesNs', 'carterTracy1000IntervalsNs']:
        for q in qualifications:
            timing_samples(q, key)
        values = [statistics.median(q[key]) if isinstance(q[key], list) else q[key] for q in qualifications]
        before = [values[0], values[3]]
        after = values[1:3]
        if key == 'coldFirstQueryNs':
            ratio = min(before) / max(after)
            require(ratio >= 2.0, 'Frozen cold speedup target failed')
        else:
            ratio = max(after) / min(before)
            require(ratio <= 1.20, 'Frozen warm regression target failed')
        metrics[key] = {'before': before, 'after': after, 'conservativeRatio': ratio}
    report['performance'] = metrics
    report['native'] = {'java': qualifications[0]['java'], 'identicalMPOutputs': 40,
                        'identicalCompleteCTHistories': 42, 'concurrentWorkersPerRun': 8}
    report['sha256'] = {p.name: hashlib.sha256(p.read_bytes()).hexdigest()
                        for p in sorted(ROOT.iterdir()) if p.is_file() and p.name not in ['audit.json', 'validation.json']}
    report['claim'] = 'Initialization-only identity and runtime evidence; no new physical-domain admission'
    (ROOT / 'audit.json').write_text(json.dumps(report, indent=2, allow_nan=False) + '\n')


if __name__ == '__main__':
    main()
