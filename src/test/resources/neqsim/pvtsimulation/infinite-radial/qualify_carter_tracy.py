"""Independent Carter-Tracy ODE reference; this is not exact pressure-step diffusion."""
import argparse
import hashlib
import json
import math
from pathlib import Path
import time
import numpy as np
from scipy import __version__ as scipy_version
from scipy.integrate import solve_ivp
from scipy.special import j1, y1, erfc

# Frozen before native comparisons: finest relative error <= 0.02; errors decrease
# on each of the six meshes. ODE-start refinement <= 1e-8 + 1e-7*abs(reference).
# Pressure/derivative retain the separately frozen inverse-Laplace bounds.
LOWER, UPPER = -42., 80.
nodes, weights = np.polynomial.legendre.leggauss(12)
panels = math.ceil((math.log(UPPER) - LOWER) / .25)
width = (math.log(UPPER) - LOWER) / panels
u = np.exp(np.array([LOWER + width * (k + .5 * (v + 1))
                     for k in range(panels) for v in nodes]))
measure = np.tile(weights, panels) * width / 2 * 4 / (math.pi**2 * u**2 * (j1(u)**2 + y1(u)**2))
COEFFICIENTS = [1., -3/8, 63/128, -1899/1024, 543483/32768]


def influence(t):
    """Different quadrature and SciPy Bessel implementation, independently MP-checked."""
    root = math.sqrt(t)
    z = UPPER * root
    tail_p = tail_d = 0.
    if z > 26:
        tail_p = sum(c / ((2*k+1) * UPPER**(2*k+1)) for k, c in enumerate(COEFFICIENTS))
    else:
        exponential = math.exp(-z*z)
        loss = -math.expm1(-z*z)
        integral = math.sqrt(math.pi) / (2*root) * erfc(z)
        for k, c in enumerate(COEFFICIENTS):
            power = 2*k+1
            inv = UPPER**(-power)
            tail_p += c * (loss*inv + 2*t*integral) / power
            tail_d += c*integral
            integral = (exponential*inv - 2*t*integral) / power
    # Only used through t=1e12; omitted next low-frequency term < 1e-49.
    p = .5*t*math.exp(2*LOWER) + float(np.sum(measure * -np.expm1(-t*u*u)))
    d = .5*math.exp(2*LOWER) + float(np.sum(measure * u*u*np.exp(-t*u*u)))
    return p + 2/math.pi*tail_p, d + 2/math.pi*tail_d


def ode_reference(start, targets):
    """Integrate the limiting CT ODE in log time with an analytic origin limit."""
    def rhs(log_time, water):
        t = math.exp(log_time)
        p, d = influence(t)
        return t * (1 - water*d) / (p-t*d)
    initial = 2*math.sqrt(math.pi)*math.sqrt(start)/3
    solution = solve_ivp(rhs, (math.log(start), math.log(max(targets))), [initial],
                         method='DOP853', rtol=2e-11, atol=1e-13,
                         t_eval=np.log(targets))
    if not solution.success or solution.y.shape != (1, len(targets)):
        raise ValueError('Independent ODE integration incomplete: ' + solution.message)
    return solution.y[0].tolist()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--native', required=True, type=Path)
    parser.add_argument('--reference', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    started = time.perf_counter()
    native = json.loads(args.native.read_text())
    fixtures = json.loads(args.reference.read_text())
    failures, spectral = [], []
    expected_times = [1e-12, 1e-8, 1e-6, 3e-5, 1e-4, .003, .009999, .01,
                      .010001, .3, 1, 7.5, 100, 199.999, 200, 200.001,
                      8000, 10000, 1e8, 1e12]
    if fixtures['failures'] or [float(r['time']) for r in fixtures['rows']] != expected_times:
        raise ValueError('Incomplete/unexpected inverse-Laplace fixtures')
    if [r['time'] for r in native['referenceComparisons']] != expected_times:
        raise ValueError('Incomplete/unexpected native pressure/derivative rows')
    for row, fixture in zip(native['referenceComparisons'], fixtures['rows']):
        for quantity, absolute, relative in [('pressure', 1e-7, 1e-6), ('derivative', 1e-6, 1e-5)]:
            value, ref = row[quantity], float(fixture[quantity]['digits35'])
            if not math.isfinite(value) or value <= 0 or abs(value-ref) > absolute+relative*abs(ref):
                failures.append(['native inverse-Laplace comparison', row['time'], quantity])
    for row in fixtures['rows']:
        t = float(row['time'])
        p, d = influence(t)
        pr, dr = float(row['pressure']['digits35']), float(row['derivative']['digits35'])
        accepted = abs(p-pr) <= 1e-7+1e-6*pr and abs(d-dr) <= 1e-6+1e-5*dr
        spectral.append({'time': t, 'pressure': p, 'derivative': d,
                         'pressureAbsoluteError': abs(p-pr), 'derivativeAbsoluteError': abs(d-dr),
                         'accepted': bool(accepted)})
        if not accepted:
            failures.append(['independent spectrum', t])
    targets = sorted({r['lastTime'] for r in native['unitStepCarterTracy']})
    if targets != [1e-8, 1e-6, .0001, .01, 1, 100, 10000]:
        raise ValueError('Missing/unexpected native CT target times')
    first, refined = ode_reference(1e-14, targets), ode_reference(1e-16, targets)
    comparisons = []
    for target, a, b in zip(targets, first, refined):
        if not math.isfinite(b) or b <= 0 or abs(a-b) > 1e-8+1e-7*abs(b):
            failures.append(['ODE refinement', target])
        rows = sorted([r for r in native['unitStepCarterTracy'] if r['lastTime'] == target],
                      key=lambda r: r['intervals'])
        if [r['intervals'] for r in rows] != [32, 64, 128, 256, 512, 1024]:
            raise ValueError('Missing/unexpected native CT meshes')
        previous = math.inf
        errors = []
        for row in rows:
            water, ts = row['history'], row['time']
            if len(water) != row['intervals']+1 or len(ts) != len(water) or \
                    water[0] != 0 or ts[0] != 0 or water[-1] != row['influx'] or \
                    abs(ts[-1]/target-1) > 1e-13 or \
                    any(not math.isfinite(t) for t in ts) or \
                    any(not math.isfinite(w) or w < 0 for w in water) or \
                    any(y <= x for x, y in zip(ts, ts[1:])) or \
                    any(y < x for x, y in zip(water, water[1:])):
                failures.append(['native history shape/positivity/monotonicity', target, row['intervals']])
            for index, t in enumerate(ts[1:], 1):
                expected = math.exp(math.log(1e-16) + (math.log(target)-math.log(1e-16))*index/row['intervals'])
                if not math.isfinite(t) or abs(t/expected-1) > 1e-13:
                    failures.append(['native mesh identity', target, row['intervals'], index])
            relative = abs(row['influx']/b-1)
            if relative >= previous:
                failures.append(['nonconverging native CT mesh', target, row['intervals']])
            previous = relative
            errors.append({'intervals': row['intervals'], 'influx': row['influx'], 'relativeError': relative})
        if errors[-1]['relativeError'] > .02:
            failures.append(['finest CT error', target])
        comparisons.append({'lastTime': target, 'odeStart1e14': a, 'odeStart1e16': b,
                            'startRefinementDifference': abs(a-b), 'meshes': errors})
    result = {'claim': 'mathematical infinite-acting CT recurrence/limiting-ODE qualification, not measured accuracy or exact pressure-step diffusion',
              'equation': 'dW/dt=(1-W*Pprime)/(P-t*Pprime), U=1 reservoir m3/bar, pressure step=1bar',
              'earlyTimeCTtoExactPressureStepRatio': math.pi/3,
              'versions': {'numpy': np.__version__, 'scipy': scipy_version},
              'independentSpectrumMPComparisons': spectral, 'ctComparisons': comparisons,
              'failures': failures, 'seconds': time.perf_counter()-started,
              'sourceSha256': hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
              'nativeSha256': hashlib.sha256(args.native.read_bytes()).hexdigest()}
    args.output.write_text(json.dumps(result, indent=2, allow_nan=False)+'\n')
    print(json.dumps({'failures': failures, 'maxFinestRelativeError': max(r['meshes'][-1]['relativeError'] for r in comparisons), 'seconds': result['seconds']}))
    raise SystemExit(bool(failures))


if __name__ == '__main__':
    main()
