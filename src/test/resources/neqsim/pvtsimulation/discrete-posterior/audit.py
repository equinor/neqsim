"""Check every native endpoint and finite-scenario output against the frozen reference."""
import argparse
from decimal import Decimal as D, localcontext
import importlib.util
import json
import math
from pathlib import Path

HERE = Path(__file__).resolve().parent
SPEC = importlib.util.spec_from_file_location('independent_posterior_reference', HERE / 'reference.py')
REFERENCE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(REFERENCE)


def audit(native_path, reference_path=HERE / 'reference.json'):
    """Return complete failures/maxima; never rely on Python assertions or native summaries."""
    expected = json.loads(reference_path.read_text())
    native = json.loads(native_path.read_text())
    failures, maxima = [], {}
    endpoints = 0

    def fail(message):
        failures.append(message)

    def check(label, actual, target, tolerance):
        if target is None:
            if actual is not None:
                fail(label + ': expected undefined/null')
            return
        if type(actual) not in (int, float) or not math.isfinite(actual):
            fail(label + ': expected finite number')
            return
        error = abs(actual - target)
        maxima[label.split('/')[0]] = max(maxima.get(label.split('/')[0], 0.), error)
        if error > tolerance:
            fail(f'{label}: error {error} exceeds {tolerance}')

    def vector(label, actual, target, tolerances):
        if not isinstance(actual, list) or len(actual) != len(target):
            fail(label + ': wrong vector shape')
            return
        for j, (a, b) in enumerate(zip(actual, target)):
            check(f'{label}/{j}', a, b, tolerances[j] if isinstance(tolerances, list) else tolerances)

    if not isinstance(native, list) or len(native) != len(expected['cases']):
        return {'failures': ['wrong case count/type'], 'endpoints': 0, 'maxima': {}}
    for index, (case, source) in enumerate(zip(native, expected['cases'])):
        name = source['name']
        if not isinstance(case, dict) or case.get('name') != name:
            fail(f'case {index}: identity/order mismatch')
            continue
        rows = case.get('rows')
        if not isinstance(rows, list) or len(rows) != len(source['rows']):
            fail(name + ': wrong trajectory count')
            continue
        valid = True
        for i, (trajectory, target, candidate) in enumerate(zip(rows, source['rows'], source['candidates'])):
            if target is None:
                if trajectory is not None:
                    fail(f'{name}/{i}: infeasible inventory accepted')
                continue
            if not isinstance(trajectory, list) or len(trajectory) != 5:
                fail(f'{name}/{i}: wrong endpoint count')
                valid = False
                continue
            for t, (row, ref) in enumerate(zip(trajectory, target)):
                if not isinstance(row, list) or len(row) != 5 or any(type(x) not in (int, float) or not math.isfinite(x) for x in row):
                    fail(f'{name}/{i}/{t}: endpoint must have five finite numbers')
                    valid = False
                    continue
                for column, (field, tolerance) in enumerate(zip(
                        ['timeSeconds', 'pressureBara', 'waterM3', 'aquiferPressureBara', 'inventoryMol'],
                        [1e-8, 2e-5, 2e-4, 2e-5, 1e-6])):
                    check(field + '/' + name + '/' + str(i) + '/' + str(t), row[column], ref[column], tolerance)
                endpoints += 1
                volume = candidate['volume']
                if row[1] <= 0 or not 0 <= row[2] < volume or row[3] <= 0 or row[4] <= 0:
                    fail(f'{name}/{i}/{t}: physical state bound')
                    continue
                with localcontext() as context:
                    context.prec = 60
                    p = float(REFERENCE.pressure((D(str(volume)) - D(str(row[2]))) / D(str(row[4])), candidate['eos']))
                check('eosClosure', row[1], p, 2e-5)
                check('storageClosure', row[3], 200 - row[2] / candidate['capacity'], 2e-5)
        target = source['expected']
        if target['cdfMargin'] <= 1e-5:
            fail(name + ': reference CDF crossing is too close for support-value comparison')
        for field, tolerance in [('prior', 2e-6), ('weights', 2e-6), ('chiSquare', 1e-3),
                                 ('means', [1e-3, 1e-12, 1e-12, 1e-8, 1e-9]), ('shifts', 2e-5)]:
            vector(field, case.get(field), target[field], tolerance)
        for field, tolerance in [('effectiveCount', 2e-4), ('logEvidence', 2e-5), ('rejectedMass', 2e-6)]:
            check(field, case.get(field), target[field], tolerance)
        if case.get('best') != target['best']:
            fail(name + ': best likelihood index mismatch')
        for field, tolerance in [('correlations', 2e-5), ('parameterQuantiles', [1e-3, 1e-12, 1e-12, 1e-8, 1e-9]),
                                 ('pressureQuantiles', 2e-5)]:
            actual = case.get(field)
            if not isinstance(actual, list) or len(actual) != len(target[field]):
                fail(name + '/' + field + ': wrong matrix shape')
                continue
            for i, (a, b) in enumerate(zip(actual, target[field])):
                vector(field + '/' + str(i), a, b, tolerance)
        holdout = [((r[3][1] - source['observations'][2]) / source['sigma']) ** 2 if r else None for r in source['rows']]
        vector('holdoutChiSquare', case.get('holdoutChiSquare'), holdout, 1e-3)
        rejected = case.get('rejected')
        if not isinstance(rejected, list) or len(rejected) != sum(r is None for r in source['rows']):
            fail(name + ': wrong rejection diagnostics')
        for field in ['seconds', 'quantileSeconds']:
            seconds = case.get(field)
            if type(seconds) not in (int, float) or not math.isfinite(seconds) or seconds < 0.:
                fail(name + ': ' + field + ' must be finite and nonnegative')
        if valid:
            # Reconstruct likelihood from retained native predictions as a separate integrity check.
            calculated = REFERENCE.summarize(source['candidates'], rows, source['observations'], source['sigma'])
            vector('recomputedWeights', case.get('weights'), calculated['weights'], 2e-12)
    return {'failures': failures, 'endpoints': endpoints, 'cases': len(expected['cases']), 'maxima': maxima}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('native', type=Path)
    parser.add_argument('--reference', type=Path, default=HERE / 'reference.json')
    args = parser.parse_args()
    result = audit(args.native, args.reference)
    print(json.dumps(result, indent=2))
    raise SystemExit(bool(result['failures']))
