"""Independent synthetic SRK calibration/CCE/tank reference, standard library only.

Run: selected-python reference.py [decimal-precision], writing JSON to stdout.
No NeqSim dependency or reservoir-driven EOS tuning. Inputs are synthetic.
"""
from decimal import Decimal as D, getcontext
import json
import sys

getcontext().prec = int(sys.argv[1]) if len(sys.argv) > 1 else 60
R = D('8.3144621')
TC = [D('190.56'), D('305.32')]
PC = [D('45.99e5'), D('48.72e5')]
W = [D('.0115'), D('.0995')]
K = D('.02')
C = (D(2).ln() / 3).exp() - 1
B = [C / 3 * R * t / p for t, p in zip(TC, PC)]


def parameters(t, x, k):
    """Return classical mixture attraction and covolume in SI units."""
    a = []
    for tc, pc, w in zip(TC, PC, W):
        m = D('.48') + D('1.574') * w - D('.176') * w * w
        a.append(R * R * tc * tc / (9 * C * pc)
                 * (1 + m * (1 - (t / tc).sqrt())) ** 2)
    am = sum(x[i] * x[j] * (a[i] * a[j]).sqrt()
             * (1 if i == j else 1 - k) for i in range(2) for j in range(2))
    return am, sum(x[i] * B[i] for i in range(2))


def pressure(t, x, v, k):
    """Evaluate the independent SRK pressure equation, returning bara."""
    a, b = parameters(t, x, k)
    return (R * t / (v - b) - a / (v * (v + b))) / D('1e5')


def volume(t, x, p, k):
    """Bisect the single gas root in the declared supercritical binary domain."""
    _, b = parameters(t, x, k)
    lo, hi = b * (1 + D('1e-30')), R * t / (p * D('1e5')) + 10 * b
    while pressure(t, x, hi, k) > p:
        hi *= 2
    for _ in range(240):
        mid = (lo + hi) / 2
        if pressure(t, x, mid, k) > p:
            lo = mid
        else:
            hi = mid
    v = (lo + hi) / 2
    assert abs(pressure(t, x, v, k) - p) < D('1e-45')
    return v


x = [D('.9'), D('.1')]
vcal = volume(D(400), x, D(200), K)
a0, bcal = parameters(D(400), x, D(0))
ak, _ = parameters(D(400), x, D(1))
observed_a = (R * 400 / (vcal - bcal) - D('200e5')) * vcal * (vcal + bcal)
identified = (a0 - observed_a) / (a0 - ak)
assert abs(identified - K) < D('1e-45')
lab = [{'pressureBara': p, 'molarVolumeM3': volume(D(420), x, D(p), identified)}
       for p in [200, 150, 100, 50]]
v = D('1017.3')
n0 = v / vcal
n = [z * n0 for z in x]
initial = n.copy()
rows = []
for injected in [[0, 20000], [10000, 0], [0, 15000], [5000, 5000]]:
    produced = [D(0), D(0)]
    for _ in range(4):
        total = sum(n)
        removed = [D(25000) * q / total for q in n]
        n = [n[i] - removed[i] + D(injected[i]) / 4 for i in range(2)]
        produced = [produced[i] + removed[i] for i in range(2)]
    composition = [q / sum(n) for q in n]
    surface = sum(produced) * volume(D('288.15'), [q / sum(produced) for q in produced],
                                     D('1.01325'), identified)
    rows.append({'injectedMoles': injected, 'producedMoles': produced,
                 'retainedMoles': n.copy(), 'pressureBara': pressure(D(400), composition, v / sum(n), identified),
                 'surfaceGasVolumeM3': surface})
result = {'components': ['methane', 'ethane'], 'initialVolumeM3': v,
          'calibration': {'temperatureK': 400, 'pressureBara': 200,
                          'molarVolumeM3': vcal, 'identifiedKij': identified},
          'laboratoryHoldoutTemperatureK': 420, 'laboratoryHoldout': lab,
          'initialMoles': initial, 'rows': rows}
print(json.dumps(result, default=lambda q: float(q), indent=2, allow_nan=False))
