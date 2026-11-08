"""Audit complete before/after matrices against frozen numerical and physical bounds."""
import argparse
import csv
import hashlib
import json
import math
from pathlib import Path


def read_matrix(path):
    """Require the complete declared numerical matrix, retaining every raw value."""
    with path.open(newline='') as stream:
        reader = csv.DictReader(stream)
        if reader.fieldnames != ['time', 'pressure', 'derivative']:
            raise ValueError('Matrix header mismatch')
        rows = [{key: float(value) for key, value in row.items()} for row in reader]
    if len(rows) != 6278:
        raise ValueError('Incomplete or unexpected numerical matrix')
    return rows


def audit(before, after):
    """Check identity, physical shape, original limits and tighter algorithm agreement."""
    failures = []
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
            if row['time'] <= previous_time or row['pressure'] < previous_pressure:
                failures.append([label, 'time identity/pressure nondecrease', index])
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
    return {'rows': len(before), 'failures': failures, 'maxima': maxima,
            'claim': 'algorithm agreement with parent04e78817 plus physical shape and limits; independent Laplace/CT qualification remains separate'}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--before', required=True, type=Path)
    parser.add_argument('--after', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    result = audit(read_matrix(args.before), read_matrix(args.after))
    result['sha256'] = {key: hashlib.sha256(path.read_bytes()).hexdigest()
                        for key, path in [('before', args.before), ('after', args.after),
                                          ('source', Path(__file__))]}
    args.output.write_text(json.dumps(result, indent=2, allow_nan=False)+'\n')
    print(json.dumps(result, allow_nan=False))
    raise SystemExit(bool(result['failures']))


if __name__ == '__main__':
    main()
