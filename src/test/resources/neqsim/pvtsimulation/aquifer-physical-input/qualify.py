"""Independent exact-rational admission for physical aquifer conversion evidence."""
import argparse
import csv
from fractions import Fraction
import hashlib
import math
from pathlib import Path
import statistics
import json


def read(path):
    """Read the complete declared native input matrix, including rejected rows."""
    with Path(path).open() as stream:
        rows = list(csv.DictReader(stream))
    if len(rows) != 351 or [int(r['id']) for r in rows] != list(range(351)):
        raise ValueError('Missing, duplicated or reordered native cases')
    identity = '\n'.join(','.join(r[k] for k in ('id', 'method', 'inputs')) for r in rows) + '\n'
    if hashlib.sha256(identity.encode()).hexdigest() != INPUT_SHA256:
        raise ValueError('Physical input identity changed')
    return rows


def expected(row):
    """Classify physical domains and correctly round an exact rational of binary inputs."""
    x = [float(v) for v in row['inputs'].split(';')]
    if not all(math.isfinite(v) for v in x):
        return 'IllegalArgumentException', None
    if row['method'] == 'time':
        k, t, phi, mu, ct, radius = x
        valid = k >= 0 and t >= 0 and 0 < phi <= 1 and mu > 0 and ct > 0 and radius > 0
        numerator = [k, t]
        denominator = [phi, mu, ct, radius, radius]
    elif row['method'] == 'storage':
        phi, ct, height, radius, angle = x
        valid = 0 <= phi <= 1 and ct >= 0 and height >= 0 and radius > 0 and 0 <= angle <= 360
        numerator = [2 * math.pi, angle, phi, ct, height, radius, radius]
        denominator = [360.]
    else:
        raise ValueError('Undeclared method')
    if not valid:
        return 'IllegalArgumentException', None
    if any(v == 0 for v in numerator):
        return '', 0.
    ratio = Fraction(1)
    for v in numerator:
        ratio *= Fraction.from_float(v)
    for v in denominator:
        ratio /= Fraction.from_float(v)
    try:
        value = float(ratio)
    except OverflowError:
        value = math.inf
    return ('ArithmeticException', None) if value == 0 or not math.isfinite(value) else ('', value)


def audit(rows):
    """Audit actual outcomes without trusting precomputed errors or dropping nonfinite rows."""
    failures = []
    maximum = 0.
    categories = {'finite': 0, 'zero': 0, 'invalid': 0, 'unrepresentable': 0}
    for row in rows:
        error, value = expected(row)
        category = 'invalid' if error == 'IllegalArgumentException' else 'unrepresentable' if error else 'zero' if value == 0 else 'finite'
        categories[category] += 1
        if row['error'] != error or bool(row['result']) == bool(error):
            failures.append({'id': row['id'], 'reason': 'outcome', 'expected_error': error,
                             'actual_error': row['error'], 'actual_result': row['result']})
            continue
        if error:
            continue
        actual = float(row['result'])
        if not math.isfinite(actual) or actual < 0 or (value > 0 and actual == 0):
            failures.append({'id': row['id'], 'reason': 'nonfinite/nonpositive'})
            continue
        fraction = abs(actual - value) / (1e-12 * abs(value) + math.ulp(value))
        maximum = max(maximum, fraction)
        if fraction > 1 or (value < float.fromhex('0x1p-1022') and actual != value):
            failures.append({'id': row['id'], 'reason': 'arithmetic', 'expected': value,
                             'actual': actual, 'budget_fraction': fraction})
    return {'failures': failures, 'categories': categories, 'max_budget_fraction': maximum}


def runtime(paths):
    """Validate full ABBA sample identities and bounded-workflow checksum/cost controls."""
    if len(paths) != 4:
        raise ValueError('Four ABBA execution tables required')
    medians = []
    checksums = {}
    for path in paths:
        with Path(path).open() as stream:
            rows = list(csv.DictReader(stream))
        identities = [(r['workload'], int(r['sample'])) for r in rows]
        expected_ids = [(w, i) for w in ('transformations', 'histories') for i in range(-3, 7)]
        if identities != expected_ids:
            raise ValueError('Runtime samples missing or reordered')
        values = {}
        for workload in ('transformations', 'histories'):
            group = [r for r in rows if r['workload'] == workload]
            if any(not math.isfinite(float(r['checksum'])) or int(r['nanos']) <= 0 for r in group):
                raise ValueError('Invalid runtime sample')
            checksums.setdefault(workload, set()).update(r['checksum'] for r in group)
            values[workload] = statistics.median(int(r['nanos']) for r in group if int(r['sample']) >= 0)
        medians.append(values)
    if any(len(values) != 1 for values in checksums.values()):
        raise ValueError('Ordinary physical-input/radial-history checksum changed')
    ratio = max(medians[i]['histories'] for i in (1, 2)) / min(medians[i]['histories'] for i in (0, 3))
    return {'medians_ns': medians, 'complete_history_worst_ratio': ratio, 'ceiling': 1.20,
            'accepted': ratio <= 1.20, 'checksums': {k: sorted(v) for k, v in checksums.items()}}


def main():
    """Recompute admission from raw native and runtime tables."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--before', required=True)
    parser.add_argument('--after', required=True)
    parser.add_argument('--output', required=True)
    parser.add_argument('--runtime', nargs=4)
    args = parser.parse_args()
    before, after = read(args.before), read(args.after)
    result = {'rows': len(after), 'before': audit(before), 'after': audit(after),
              'sha256': {p: hashlib.sha256(Path(p).read_bytes()).hexdigest() for p in (args.before, args.after)}}
    if args.runtime:
        result['runtime'] = runtime(args.runtime)
    Path(args.output).write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps({'rows': len(after), 'before_failures': len(result['before']['failures']),
                      'after_failures': len(result['after']['failures']), 'runtime': result.get('runtime')}, indent=2))
    raise SystemExit(bool(result['after']['failures']) or not result.get('runtime', {'accepted': True})['accepted'])


INPUT_SHA256 = '073de0c6e2af2844ca9f1fff92b0203ae2d44693b195be1fc4e676d091178718'

if __name__ == '__main__':
    main()
