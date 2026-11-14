"""Independent Laplace inversion of sealed-annulus wall pressure and slope."""

from argparse import ArgumentParser
from concurrent.futures import ProcessPoolExecutor
from decimal import Decimal, localcontext
from functools import lru_cache
import hashlib
import json
from pathlib import Path
import sys

import mpmath
from mpmath import mp


RADII = ('2', '3.5', '6.25', '10')
WORK_PRECISIONS = (32, 44)
AGREEMENT_ABSOLUTE = '1e-20'
AGREEMENT_RELATIVE = '1e-20'


def grid():
    """Declare the entire matrix before either inversion."""
    for radius in RADII:
        with localcontext() as context:
            context.prec = 60
            switch = (Decimal(radius) - 1) ** 2 / 64
            times = [
                ('early-1e-6', Decimal('1e-6')),
                ('early-1e-4', Decimal('1e-4')),
                ('early-1e-2', Decimal('.01')),
                ('switch-below', switch * Decimal('.999')),
                ('switch', switch),
                ('switch-above', switch * Decimal('1.001')),
                ('time-1', Decimal(1)),
                ('time-100', Decimal(100)),
                ('time-10000', Decimal(10000)),
            ]
        for label, time in sorted(times, key=lambda item: item[1]):
            yield radius, label, str(time)


def invert(task):
    """Invert the boundary-value resolvent directly, without using modal roots."""
    radius_text, label, time_text = task
    precision_results = []
    for digits in WORK_PRECISIONS:
        with mp.workdps(digits):
            radius, time = mp.mpf(radius_text), mp.mpf(time_text)

            @lru_cache(maxsize=None)
            def derivative_transform(s):
                root = mp.sqrt(s)
                alpha = mp.besselk(1, radius * root) / mp.besseli(1, radius * root)
                wall = mp.besselk(0, root) + alpha * mp.besseli(0, root)
                flux_coefficient = mp.besselk(1, root) - alpha * mp.besseli(1, root)
                return wall / (root * flux_coefficient)

            pressure = mp.invertlaplace(lambda s: derivative_transform(s) / s, time, method='dehoog')
            derivative = mp.invertlaplace(derivative_transform, time, method='dehoog')
            if not all(mp.isfinite(v) and v > 0 for v in (pressure, derivative)):
                raise ArithmeticError(f'Nonpositive or nonfinite inversion at R={radius_text}, t={time_text}')
            precision_results.append({
                'digits': digits,
                'pressure': mp.nstr(pressure, digits),
                'derivative': mp.nstr(derivative, digits),
            })
    with mp.workdps(80):
        low, high = precision_results
        errors = {}
        accepted = True
        for quantity in ('pressure', 'derivative'):
            reference = mp.mpf(high[quantity])
            error = abs(mp.mpf(low[quantity]) - reference)
            bound = mp.mpf(AGREEMENT_ABSOLUTE) + mp.mpf(AGREEMENT_RELATIVE) * abs(reference)
            errors[quantity] = {'absolute': mp.nstr(error, 12), 'relative': mp.nstr(error / reference, 12)}
            accepted &= error <= bound
    return {
        'radius': radius_text,
        'label': label,
        'time': time_text,
        'timeBinary64Hex': float(time_text).hex(),
        'pressure': precision_results[-1]['pressure'],
        'derivative': precision_results[-1]['derivative'],
        'precisionResults': precision_results,
        'precisionAgreement': errors,
        'accepted': bool(accepted),
    }


def late_limits():
    with mp.workdps(60):
        result = []
        for radius_text in RADII:
            radius = mp.mpf(radius_text)
            capacity = (radius**2 - 1) / 2
            intercept = radius**4 * mp.log(radius) / (radius**2 - 1)**2
            intercept -= (3 * radius**2 - 1) / (4 * (radius**2 - 1))
            result.append({'radius': radius_text, 'capacity': mp.nstr(capacity, 50),
                           'lateSlope': mp.nstr(1 / capacity, 50),
                           'lateIntercept': mp.nstr(intercept, 50)})
        return result


def main():
    parser = ArgumentParser(description=__doc__)
    parser.add_argument('--workers', type=int, default=4)
    parser.add_argument('--output', type=Path, default=Path(__file__).with_suffix('.json'))
    arguments = parser.parse_args()
    if arguments.workers < 1:
        parser.error('--workers must be positive')
    points = list(grid())
    if len(points) != 36 or len(set(points)) != 36:
        raise ValueError('Independent input matrix changed')
    rows = []
    with ProcessPoolExecutor(max_workers=arguments.workers) as pool:
        for row in pool.map(invert, points):
            rows.append(row)
            print(f"{row['radius']} {row['label']} accepted={row['accepted']}", flush=True)
    result = {
        'equation': 'P_t=(r P_r)_r/r, 1<r<R, P(r,0)=0, -P_r(1,t)=1, P_r(R,t)=0',
        'wallQuantities': 'rate-driven wall pressure P(1,t) and its dimensionless time derivative',
        'transforms': {
            'z': 'sqrt(s), principal branch for Re(s)>0',
            'alpha': 'K1(R*z)/I1(R*z)',
            'V': 'K0(z)+alpha*I0(z)',
            'F': 'K1(z)-alpha*I1(z)',
            'pressure': 'V/(s*z*F)',
            'derivative': 'V/(z*F)',
        },
        'method': 'mpmath de Hoog inversion on a positive-real-part contour',
        'mpmathVersion': mpmath.__version__,
        'pythonVersion': sys.version.split()[0],
        'sourceSha256': hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
        'precisions': WORK_PRECISIONS,
        'agreementAbsolute': AGREEMENT_ABSOLUTE,
        'agreementRelative': AGREEMENT_RELATIVE,
        'lateLimits': late_limits(),
        'rows': rows,
        'failures': [{'radius': r['radius'], 'time': r['time']} for r in rows if not r['accepted']],
    }
    arguments.output.write_text(json.dumps(result, indent=2, allow_nan=False) + '\n', encoding='utf-8')
    raise SystemExit(bool(result['failures']))


if __name__ == '__main__':
    main()
