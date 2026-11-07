"""Synthetic unequal-sigma bounded inversion, using independent Decimal SRK.

Run with selected Python: weighted_reference.py [decimal-precision].
No NeqSim dependency. No EOS fitting against reservoir observations.
"""
from decimal import Decimal as D
import json
from reference import generate, pressure, volume, K

BASE = generate()
VCAL = BASE['calibration']['molarVolumeM3']
OFFSETS = [D('-.2'), D('.4'), D('-.1'), D(0)]
OBSERVED = [r['pressureBara'] + d for r, d in zip(BASE['rows'], OFFSETS)]


def replay(v, products=False):
    """Predict four finite-protocol intervals at a supplied initial volume, m3."""
    n = [D('.9') * v / VCAL, D('.1') * v / VCAL]
    rows = []
    for base in BASE['rows']:
        injected = [D(q) for q in base['injectedMoles']]
        produced = [D(0), D(0)]
        for _ in range(4):
            removed = [D(25000) * q / sum(n) for q in n]
            n = [n[i] - removed[i] + injected[i] / 4 for i in range(2)]
            produced = [produced[i] + removed[i] for i in range(2)]
        row = {'pressureBara': pressure(D(400), [q / sum(n) for q in n], v / sum(n), K)}
        if products:
            row.update(producedMoles=produced, injectedMoles=injected)
            row['surfaceGasVolumeM3'] = sum(produced) * volume(
                D('288.15'), [q / sum(produced) for q in produced], D('1.01325'), K)
        rows.append(row)
    return rows


def objective(v, sigmas):
    """Return calibration-only chi-square, excluding the fourth interval."""
    return sum(((row['pressureBara'] - OBSERVED[i]) / sigmas[i]) ** 2
               for i, row in enumerate(replay(v)[:3]))


def minimize(sigmas, lower, upper):
    """Use direct physical-volume golden section, with explicit endpoint candidates."""
    left, right = lower, upper
    ratio = (D(5).sqrt() - 1) / 2
    x1, x2 = right - ratio * (right - left), left + ratio * (right - left)
    y1, y2 = objective(x1, sigmas), objective(x2, sigmas)
    for _ in range(180):
        if y1 < y2:
            right, x2, y2 = x2, x1, y1
            x1 = right - ratio * (right - left)
            y1 = objective(x1, sigmas)
        else:
            left, x1, y1 = x1, x2, y2
            x2 = left + ratio * (right - left)
            y2 = objective(x2, sigmas)
    return min([lower, upper, x1, x2], key=lambda v: objective(v, sigmas))


def reference():
    """Generate all fixed-input scenarios, complete residuals and predicted products."""
    output = []
    cases = [('unequal', ['.1', '.7', '.25'], 1300, 0),
             ('equal', ['.5', '.5', '.5'], 1300, 0),
             ('scaled', ['.3', '2.1', '.75'], 1300, 0),
             ('holdout shifted', ['.1', '.7', '.25'], 1300, 5),
             ('upper1000 interior', ['.1', '.7', '.25'], 1000, 0),
             ('upper980 bound', ['.1', '.7', '.25'], 980, 0)]
    for name, sigma, upper, shift in cases:
        sigmas = [D(s) for s in sigma] + [D('.5')]
        fit = minimize(sigmas, D(800), D(upper))
        rows = replay(fit, products=True)
        observations = OBSERVED[:3] + [OBSERVED[3] + D(shift)]
        residuals = [(p - r['pressureBara']) / s for r, p, s in zip(rows, observations, sigmas)]
        output.append({'name': name, 'sigmasBara': sigmas, 'observedPressuresBara': observations,
                       'lowerVolumeM3': 800, 'upperVolumeM3': upper, 'fittedVolumeM3': fit,
                       'calibrationChiSquare': sum(r * r for r in residuals[:3]),
                       'holdoutChiSquare': residuals[3] ** 2, 'standardizedResiduals': residuals,
                       'boundLimited': fit in [D(800), D(upper)], 'rows': rows})
    return {'provenance': 'synthetic perturbations; independently evaluated classic SRK; no measured uncertainty',
            'cases': output}


if __name__ == '__main__':
    print(json.dumps(reference(), default=lambda q: float(q), indent=2, allow_nan=False))
