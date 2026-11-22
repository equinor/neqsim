"""Audit complete before/after matrices against frozen numerical and physical bounds."""
import argparse
import csv
import hashlib
import gzip
import json
import math
from pathlib import Path


def read_matrix(path, times):
    """Require the complete declared numerical matrix, retaining every raw value."""
    opener = gzip.open if path.name.endswith('.gz') else open
    with opener(path, mode='rt', newline='') as stream:
        reader = csv.DictReader(stream)
        if reader.fieldnames != ['time', 'pressure', 'derivative']:
            raise ValueError('Matrix header mismatch')
        rows = [{key: float(value) for key, value in row.items()} for row in reader]
    if len(rows) != 51216 or [r['time'] for r in rows] != times:
        raise ValueError('Incomplete or changed common input vector')
    return rows


def audit(before, after):
    """Check identity, physical shape, original limits and tighter algorithm agreement."""
    failures = []
    baseline_decreases = []
    maxima = {'pressureAbsoluteDifference': 0., 'pressureRelativeDifference': 0.,
              'derivativeAbsoluteDifference': 0., 'derivativeRelativeDifference': 0.}
    for label, rows in [('before', before), ('after', after)]:
        if rows[0]['time'] != float.fromhex('0x0.0000000000001p-1022') or \
                rows[-1]['time'] != float.fromhex('0x1.fffffffffffffp+1023'):
            failures.append([label, 'missing extreme times'])
        previous_time, previous_pressure = 0., 0.
        for index, row in enumerate(rows):
            if any(not math.isfinite(row[k]) or row[k] <= 0 for k in row):
                failures.append([label, 'nonfinite/nonpositive value', index])
            if row['time'] <= previous_time:
                failures.append([label, 'time identity', index])
            if row['pressure'] < previous_pressure:
                if label == 'before':
                    baseline_decreases.append({'index': index, 'time': row['time'],
                                               'previousPressure': previous_pressure,
                                               'pressure': row['pressure'],
                                               'ulps': (row['pressure']-previous_pressure)/math.ulp(previous_pressure)})
                else:
                    failures.append([label, 'pressure nondecrease', index])
            previous_time, previous_pressure = row['time'], row['pressure']
        root = math.sqrt(rows[0]['time'])
        checks = [(rows[0]['pressure']/root, 2/math.sqrt(math.pi)),
                  (rows[0]['derivative']*root, 1/math.sqrt(math.pi)),
                  (rows[-1]['derivative']*rows[-1]['time'], .5)]
        if any(not math.isfinite(a) or abs(a-b) > 1e-12 for a, b in checks):
            failures.append([label, 'scaled diffusion limit'])
    for index, (a, b) in enumerate(zip(before, after)):
        if a['time'] != b['time']:
            failures.append(['changed input time', index])
        for quantity in ['pressure', 'derivative']:
            difference = abs(a[quantity]-b[quantity])
            relative = abs(b[quantity]/a[quantity]-1) if a[quantity] > 0 else math.inf
            maxima[quantity+'AbsoluteDifference'] = max(maxima[quantity+'AbsoluteDifference'], difference)
            maxima[quantity+'RelativeDifference'] = max(maxima[quantity+'RelativeDifference'], relative)
            if not math.isfinite(difference) or difference > 1e-12+1e-12*abs(a[quantity]):
                failures.append(['algorithm agreement', index, quantity])
    return {'rows': len(before), 'failures': failures, 'baselinePressureDecreases': baseline_decreases, 'maxima': maxima,
            'claim': 'algorithm agreement with parent04e78817 plus physical shape and limits; independent Laplace/CT qualification remains separate'}



EXPECTED_TIMES_SHA256 = '4dd584c2fe1e556cea6a7e7355ff773fe0d1af1e4bce8b7f0cbdec62dbbcbbd6'
EXPECTED_BRACKET_IDENTITIES_SHA256 = '11f4d08e68ac460ddbea38b741f5f0b059b913e92103662a320b6d3acd48f5d8'

def bracket_rows(path):
    """Read complete native floating-arithmetic coverage records."""
    with gzip.open(path, 'rt', newline='') as stream:
        return list(csv.DictReader(stream))


def verify_brackets(rows, times):
    """Independently repeat the native branch arithmetic and reject missing coverage."""
    if len(rows) != 14736:
        raise ValueError('Incomplete native branch-coverage matrix')
    canonical = '\n'.join(','.join(r[k] for k in ['tail', 'frequency', 'cutoff']) for r in rows)+'\n'
    if hashlib.sha256(canonical.encode()).hexdigest() != EXPECTED_BRACKET_IDENTITIES_SHA256:
        raise ValueError('Frozen native grid identities changed')
    time_set = set(times)
    identities = set()
    counts = {'bracketed': 0, 'underflow': 0, 'belowMIN': 0}
    minimum = float.fromhex('0x0.0000000000001p-1022')
    for row in rows:
        if row['tail'] not in ['true', 'false']:
            raise ValueError('Unknown branch arithmetic')
        tail = row['tail'] == 'true'
        frequency, cutoff = float(row['frequency']), float(row['cutoff'])
        if not math.isfinite(frequency) or frequency <= 0 or cutoff not in [.001, 80.]:
            raise ValueError('Invalid branch identity')
        identity = (tail, frequency, cutoff)
        if identity in identities:
            raise ValueError('Duplicate branch record')
        identities.add(identity)
        def argument(t):
            if not tail:
                return t*frequency
            scaled = math.sqrt(t)*frequency
            return scaled*scaled
        def lower(a):
            return a <= cutoff if cutoff == .001 else a < cutoff
        status = row['status']
        if status not in counts:
            raise ValueError('Unknown branch-coverage status')
        counts[status] += 1
        if status == 'underflow':
            if lower(argument(minimum)):
                raise ValueError('A claimed underflow hides a positive-time branch')
            continue
        left, right = float(row['leftTime']), float(row['rightTime'])
        if left not in time_set or right not in time_set or not (0 < left < right):
            raise ValueError('Unretained branch endpoints')
        a, b = argument(left), argument(right)
        if a != float(row['leftArgument']) or b != float(row['rightArgument']) or lower(b):
            raise ValueError('Native branch arithmetic does not reproduce')
        if status == 'bracketed' and not lower(a):
            raise ValueError('Lower branch not represented')
        if status == 'belowMIN' and (left != minimum or lower(a)):
            raise ValueError('Incorrect below-MIN declaration')
    if counts != {'bracketed': 13186, 'underflow': 1538, 'belowMIN': 12}:
        raise ValueError('Changed frozen coverage counts')
    return counts


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--brackets', required=True, type=Path)
    parser.add_argument('--times', required=True, type=Path)
    parser.add_argument('--before', required=True, type=Path)
    parser.add_argument('--after', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    if hashlib.sha256(args.times.read_bytes()).hexdigest() != EXPECTED_TIMES_SHA256:
        raise ValueError('Frozen common time identity changed')
    times = [float(t) for t in args.times.read_text().splitlines()]
    if len(times) != 51216 or any(not math.isfinite(t) or t <= 0 for t in times) or \
            any(y <= x for x, y in zip(times, times[1:])):
        raise ValueError('Invalid frozen common input vector')
    result = audit(read_matrix(args.before, times), read_matrix(args.after, times))
    result['branchCoverage'] = verify_brackets(bracket_rows(args.brackets), times)
    result['sha256'] = {key: hashlib.sha256(path.read_bytes()).hexdigest()
                        for key, path in [('before', args.before), ('after', args.after),
                                          ('source', Path(__file__)), ('times', args.times), ('brackets', args.brackets)]}
    args.output.write_text(json.dumps(result, indent=2, allow_nan=False)+'\n')
    print(json.dumps({'rows': result['rows'], 'candidateFailures': len(result['failures']),
                      'baselinePressureDecreases': len(result['baselinePressureDecreases']),
                      'maxima': result['maxima'], 'branchCoverage': result['branchCoverage']}, allow_nan=False))
    raise SystemExit(bool(result['failures']))


if __name__ == '__main__':
    main()
