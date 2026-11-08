"""Independent high-precision ORIGINAL recurrence arithmetic, with explicit scale/protocol identity."""
from decimal import Decimal, localcontext
import gzip
import hashlib
import itertools
import json
import math
from pathlib import Path
import struct

ROOT = Path(__file__).resolve().parent
EXPONENTS = [-320, -300, -200, -100, -70, -60, -50, -20, 0, 20, 100, 200, 300]
SUPPORT = [0.0, 1e-200, 1.0, 1e200]
SCALES = [1e-200, 1.0, 1e200]
PROFILES = ['step', 'ramp', 'recovery']
COUNTS = [1, 4, 16, 64]


def require(condition, message):
    if not condition:
        raise ValueError(message)


def bits(value):
    return struct.pack('>d', float(value))


def decimal(value):
    return Decimal.from_float(float(value))


def load(path):
    with gzip.open(path, 'rt') as stream:
        return json.load(stream)


def protocol(row, identity, identifier):
    exponent, support, scale, profile, count = identity[:5]
    radius = identity[5] if len(identity) > 5 else math.inf
    require(bits(row['radius']) == bits(radius), 'Changed radius identity')
    require(row['id'] == identifier and row['timeExponent'] == exponent and
            bits(row['supportM3PerBar']) == bits(support) and bits(row['dropScaleBar']) == bits(scale)
            and row['profile'] == profile and row['intervals'] == count, 'Changed case identity')
    end = float.fromhex('0x0.0000000000001p-1022') if exponent == -2147483648 else float('1e' + str(exponent))
    times, drops = [0.0], [0.0]
    for index in range(1, count + 1):
        fraction = index / count
        times.append(end * fraction)
        value = 1.0 if profile == 'step' else fraction
        if profile == 'recovery':
            value = 1.0 if fraction <= .25 else 0.0 if fraction <= .5 else -.25 if fraction < 1 else 1.0
        drops.append(scale * value)
    require(len(row['time']) == len(row['dropBar']) == count + 1, 'Incomplete input history')
    require([bits(t) for t in row['time']] == [bits(t) for t in times], 'Changed native time grid')
    require([bits(p) for p in row['dropBar']] == [bits(p) for p in drops], 'Changed pressure protocol')
    require(all(b > a for a, b in zip(times, times[1:])), 'Unresolved time mesh')
    require(len(row['pressure']) == len(row['derivative']) == count + 1, 'Incomplete influence history')
    require(float(row['pressure'][0]) == float(row['derivative'][0]) == 0.0, 'Origin convention')
    require(all(math.isfinite(float(p)) and float(p) > 0
                for key in ['pressure', 'derivative'] for p in row[key][1:]), 'Invalid influence values')
    return times, drops


def original_recurrence(row):
    """Evaluate the original rational recurrence, NOT the candidate normalized algebra."""
    require(('exception' in row) != ('waterM3' in row), 'Ambiguous native output/exception form')
    values = [Decimal(0)]
    rounded = [0.0]
    with localcontext() as context:
        context.prec = 100
        support = decimal(row['supportM3PerBar'])
        for index in range(1, row['intervals'] + 1):
            time, previous_time = decimal(row['time'][index]), decimal(row['time'][index - 1])
            pressure, derivative = decimal(row['pressure'][index]), decimal(row['derivative'][index])
            denominator = pressure - previous_time * derivative
            require(denominator != 0, 'Singular original denominator')
            if math.isinf(float(row['radius'])):
                require(denominator > 0, 'Infinite-spectrum original denominator must be positive')
            water = values[-1] + (time - previous_time) * (
                support * decimal(row['dropBar'][index]) - values[-1] * derivative) / denominator
            water = max(Decimal(0), water)
            converted = float(water)
            if not math.isfinite(converted):
                return values, rounded, index
            values.append(water)
            rounded.append(converted)
    return values, rounded, None


def compare_water(row, rounded):
    water = row.get('waterM3', [])
    require(len(water) == len(rounded), 'Incomplete native output history')
    scale = max(abs(value) for value in rounded)
    failures, checked, maximum = [], 0, 0.0
    for index, (value, ref) in enumerate(zip(water, rounded)):
        checked += 1
        actual = float(value)
        if not math.isfinite(actual) or actual < 0:
            failures.append({'id': row['id'], 'index': index, 'reason': 'Nonfinite/negative water'})
            break
        error = abs(decimal(actual) - decimal(ref))
        tolerance = Decimal('2e-12') * abs(decimal(ref)) + Decimal('2e-14') * decimal(scale)
        if scale > 0:
            maximum = max(maximum, float(error / decimal(scale)))
        if error > tolerance:
            failures.append({'id': row['id'], 'index': index, 'reason': 'Frozen arithmetic bound',
                             'actual': str(value), 'roundedDecimalReference': repr(ref),
                             'absoluteError': str(error), 'tolerance': str(tolerance)})
            break
    require(bits(water[0]) == bits(0.0), 'Initial water convention')
    return checked, maximum, failures


def matrix(data, identities=None):
    identities = list(itertools.product(EXPONENTS, SUPPORT, SCALES, PROFILES, COUNTS)) if identities is None else identities
    require(data['java'] == '1.8.0_512-b09', 'Unexpected native runtime')
    require(len(data['rows']) == len(identities), 'Incomplete matrix')
    failures, unsupported = [], []
    checked = 0
    maximum = 0.0
    for identifier, (row, identity) in enumerate(zip(data['rows'], identities)):
        protocol(row, identity, identifier)
        exact, rounded, overflow = original_recurrence(row)
        if overflow is not None:
            unsupported.append({'id': identifier, 'firstUnrepresentableIndex': overflow})
            if row.get('exception') != 'java.lang.ArithmeticException' or f'index {overflow}' not in row.get('message', ''):
                failures.append({'id': identifier, 'reason': 'Unrepresentable positive volume not explicitly rejected'})
            continue
        if 'exception' in row:
            failures.append({'id': identifier, 'reason': 'Representable history rejected', 'exception': row['exception']})
            continue
        case_checked, case_maximum, case_failures = compare_water(row, rounded)
        checked += case_checked
        maximum = max(maximum, case_maximum)
        failures.extend(case_failures)
    return {'cases': len(identities), 'checkedEndpoints': checked,
            'unrepresentableCases': unsupported, 'maxErrorOverCaseVolumeScale': maximum, 'failures': failures}


def analytic(data):
    identities = [(e, 1.0, 1.0, p, n) for e in [-200, -70] for p in ['step', 'ramp']
                  for n in [32, 64, 128, 256, 512, 1024]]
    arithmetic = matrix({'java': data['java'], 'rows': data['analyticLimits']}, identities)
    groups, failures = [], []
    for offset in range(0, 24, 6):
        rows = data['analyticLimits'][offset:offset + 6]
        exponent, profile = identities[offset][0], identities[offset][3]
        end = float('1e' + str(exponent))
        reference = 2 * math.sqrt(math.pi) / (3 if profile == 'step' else 5) * math.sqrt(end)
        previous, errors = math.inf, []
        for row in rows:
            value = float(row.get('waterM3', ['NaN'])[-1])
            error = abs(value / reference - 1)
            if not math.isfinite(error) or error >= previous:
                failures.append({'timeExponent': exponent, 'profile': profile,
                                 'intervals': row['intervals'], 'reason': 'Analytic mesh convergence'})
            errors.append({'intervals': row['intervals'], 'waterM3': repr(value), 'relativeError': error})
            previous = error
        if previous > .02:
            failures.append({'timeExponent': exponent, 'profile': profile, 'reason': 'Finest analytic limit'})
        groups.append({'timeExponent': exponent, 'profile': profile, 'analyticM3': repr(reference), 'meshes': errors})
    return {'arithmetic': arithmetic, 'groups': groups, 'failures': failures}


def bounded(data):
    identities = [(e, 1e200, 1e200, 'step', 1, r)
                  for e in [-2147483648, -320, -200, -70] for r in [3.0, 10.0, 100.0]]
    return matrix({'java': data['java'], 'rows': data['boundedArithmetic']}, identities)


def transitions(data):
    rows = data['transitions']
    require(len(rows) == 2, 'Incomplete transition controls')
    specifications = [([0.0, 1e-200, 1e200], [0.0, 1e100, 1e100], 1e100, 'later-overflow'),
                      ([0.0, 1e-300, 1e100], [0.0, 1e200, -1e200], 1e200, 'negative-projection')]
    for identifier, (row, spec) in enumerate(zip(rows, specifications)):
        times, drops, support, name = spec
        require(row['id'] == identifier and row['profile'] == name and row['intervals'] == 2 and
                bits(row['radius']) == bits(math.inf) and bits(row['supportM3PerBar']) == bits(support)
                and bits(row['dropScaleBar']) == bits(support),
                'Changed transition identity')
        require([bits(t) for t in row['time']] == [bits(t) for t in times] and
                [bits(d) for d in row['dropBar']] == [bits(d) for d in drops], 'Changed transition inputs')
    for row in rows:
        require(len(row['pressure']) == len(row['derivative']) == 3, 'Incomplete transition influence')
        require(float(row['pressure'][0]) == float(row['derivative'][0]) == 0, 'Transition origin')
        require(all(math.isfinite(float(v)) and float(v) > 0
                    for key in ['pressure', 'derivative'] for v in row[key][1:]), 'Invalid transition influence')
    _, first, overflow = original_recurrence(rows[0])
    require(overflow == 2 and first[1] > 0, 'Later-overflow reference identity')
    _, second, overflow2 = original_recurrence(rows[1])
    require(overflow2 is None and second[1] > 0 and second[2] == 0, 'Projection reference identity')
    failures = []
    if rows[0].get('exception') != 'java.lang.ArithmeticException' or 'index 2' not in rows[0].get('message', ''):
        failures.append('Later positive overflow not explicitly rejected at index2')
    if 'exception' in rows[1]:
        failures.append('Valid pressure-recovery projection rejected')
    else:
        _, _, water_failures = compare_water(rows[1], second)
        failures.extend(water_failures)
    return {'laterOverflowIndex': 2, 'projectedFinalM3': 0.0, 'failures': failures}


def main():
    before, after = load(ROOT / 'before.json.gz'), load(ROOT / 'after.json.gz')
    for family in ['rows', 'analyticLimits', 'boundedArithmetic', 'transitions']:
        require(len(before[family]) == len(after[family]), 'Changed family extent')
        for a, b in zip(before[family], after[family]):
            for key in ['time', 'dropBar', 'pressure', 'derivative']:
                require([bits(v) for v in a[key]] == [bits(v) for v in b[key]], 'Changed input/kernel bits')
    result = {'claim': '100-digit original-recurrence arithmetic on native influence; separate from measured or independent-spectrum accuracy',
              'parent': before['source'], 'before': matrix(before), 'after': matrix(after),
              'analyticBefore': analytic(before), 'analyticAfter': analytic(after),
              'boundedBefore': bounded(before), 'boundedAfter': bounded(after),
              'transitionsBefore': transitions(before), 'transitionsAfter': transitions(after),
              'sourceSha256': hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
              'inputSha256': {p.name: hashlib.sha256(p.read_bytes()).hexdigest()
                              for p in [ROOT / 'before.json.gz', ROOT / 'after.json.gz']}}
    payload = (json.dumps(result, indent=2, allow_nan=False) + '\n').encode()
    (ROOT / 'comparison.json.gz').write_bytes(gzip.compress(payload, mtime=0))
    summary = {'candidateCases': result['after']['cases'],
               'candidateCheckedEndpoints': result['after']['checkedEndpoints'],
               'candidateFailures': len(result['after']['failures']),
               'unrepresentableCases': len(result['after']['unrepresentableCases']),
               'maximumErrorOverCaseVolumeScale': result['after']['maxErrorOverCaseVolumeScale'],
               'parentFailedCases': len(result['before']['failures']),
               'analyticHistories': 24,
               'maximumFinestAnalyticRelativeError': max(g['meshes'][-1]['relativeError']
                                                        for g in result['analyticAfter']['groups']),
               'boundedArithmeticCases': result['boundedAfter']['cases'],
               'boundedArithmeticFailures': len(result['boundedAfter']['failures']),
               'transitionFailures': len(result['transitionsAfter']['failures']),
               'fullComparisonPayloadSha256': hashlib.sha256(payload).hexdigest()}
    (ROOT / 'summary.json').write_text(json.dumps(summary, indent=2, allow_nan=False) + '\n')
    require(bool(result['before']['failures']), 'Parent negative control unexpectedly passed')
    require(not result['after']['failures'], 'Candidate arithmetic qualification failed; full failures retained')
    require(not result['analyticAfter']['failures'] and not result['analyticAfter']['arithmetic']['failures'],
            'Candidate analytic qualification failed')
    require(not result['boundedAfter']['failures'] and not result['transitionsAfter']['failures'],
            'Candidate bounded-policy arithmetic or transition control failed')


if __name__ == '__main__':
    main()
