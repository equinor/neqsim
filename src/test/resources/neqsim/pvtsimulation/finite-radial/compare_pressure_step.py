"""Quantify CT approximation/refinement against historical unit-pressure influx."""

from argparse import ArgumentParser
import csv
import hashlib
import json
import math
from pathlib import Path


RADII = (2., 10.)
MESHES = (4, 16, 64)
TIMES = (1e-6, 1e-4, .01, 1., 100., 10000.)


def unique(records, key):
    result = {}
    for record in records:
        identity = key(record)
        if identity in result:
            raise ValueError(f'Repeated point {identity}')
        result[identity] = record
    return result


def main():
    parser = ArgumentParser(description=__doc__)
    parser.add_argument('diagnostic', type=Path)
    parser.add_argument('reference', type=Path)
    parser.add_argument('output', type=Path)
    args = parser.parse_args()
    refs = unique(json.loads(args.reference.read_text()), lambda r: (float(r['radius']), float(r['time'])))
    lines = [line.removeprefix('pressureStep:') for line in args.diagnostic.read_text().splitlines()
             if line.startswith('pressureStep:')]
    values = unique(csv.DictReader(lines), lambda r: (float(r['radius']), int(r['stepsPerDecade']), float(r['time'])))
    required_reference = {(radius, time) for radius in RADII for time in TIMES}
    required_values = {(radius, mesh, time) for radius in RADII for mesh in MESHES for time in TIMES}
    if set(refs) != required_reference or set(values) != required_values:
        raise ValueError('Unexpected comparison point matrix')
    rows = []
    for radius in RADII:
        for time in TIMES:
            reference = float(refs[radius, time]['influx'])
            if not math.isfinite(reference) or reference <= 0:
                raise ValueError('Invalid pressure-step reference')
            record = {'radius': radius, 'time': time, 'unitPressureReference': refs[radius, time]['influx']}
            for name in ('zeroOrigin', 'shortTimeCtStartup'):
                influx = [float(values[radius, mesh, time][name]) for mesh in MESHES]
                if not all(math.isfinite(value) and value > 0 for value in influx):
                    raise ValueError('Invalid CT influx')
                record[name] = {'influx': influx,
                                'relativeDifferenceFromReference': [(value - reference) / reference for value in influx],
                                'relativeRefinementDifferences': [(b - a) / reference for a, b in zip(influx, influx[1:])]}
            rows.append(record)
    result = {
        'radialModel': 'Unit terminal rate influence for sealed annulus, used in the Carter–Tracy recurrence',
        'referenceModel': 'Run70 exact unit-pressure-step influx for sealed annulus from Laplace inversion',
        'timeGrid': 'Logarithmic t=10^(-6+j/m) with j=0..10m followed in order, preceded by initialized time zero',
        'stepsPerDecade': MESHES,
        'startupControl': 'Independent replay of recurrence with W(1e-6)=(2 sqrt(pi)/3) sqrt(1e-6), the early-time continuum CT asymptote',
        'differences': 'Signed fractions with exact unit-pressure-step reference as denominator',
        'interpretation': 'Compare approximation and mesh sensitivity. No comparison tolerance is assigned to the CT discrepancy.',
        'diagnosticSha256': hashlib.sha256(args.diagnostic.read_bytes()).hexdigest(),
        'referenceSha256': hashlib.sha256(args.reference.read_bytes()).hexdigest(),
        'rows': rows,
    }
    args.output.write_text(json.dumps(result, indent=2, allow_nan=False) + '\n', encoding='utf-8')


if __name__ == '__main__':
    main()
