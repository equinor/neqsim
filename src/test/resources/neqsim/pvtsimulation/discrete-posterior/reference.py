"""Independent Decimal cubic-EOS/discrete-aquifer and finite Bayes reference.

Public synthetic methane; no NeqSim import, calibration, or measured-data claim.
Run at 60 and 75 digits and compare the complete JSON bytes.
"""
import argparse
from decimal import Decimal as D, localcontext
import json
import math
from pathlib import Path


def coefficients(eos):
    """Return a(T), b in SI, using explicitly specified synthetic EOS constants."""
    gas, tc, pc, omega, temperature = map(D, ['8.3144621', '190.56', '4599000', '.0115', '400'])
    if eos == 'SRK':
        c = D(2) ** (D(1) / 3) - 1
        b = c / 3 * gas * tc / pc
        m = D('.48') + D('1.574') * omega - D('.176') * omega ** 2
        a = gas ** 2 * tc ** 2 / (9 * c * pc)
    else:
        b = D('.077803333') * gas * tc / pc
        m = D('.37464') + D('1.54226') * omega - D('.26992') * omega ** 2
        a = D('.45724333333') * gas ** 2 * tc ** 2 / pc
    return a * (1 + m * (1 - (temperature / tc).sqrt())) ** 2, b


def pressure(molar_volume, eos):
    """Explicit SRK/PR pressure, bara, with volume in m3/mol."""
    a, b = coefficients(eos)
    denominator = molar_volume * (molar_volume + b)
    if eos == 'PR':
        denominator = molar_volume ** 2 + 2 * b * molar_volume - b ** 2
    return (D('8.3144621') * 400 / (molar_volume - b) - a / denominator) / 100000


def density(eos):
    """Molar density of the stable supercritical initial state at 200 bara."""
    _, b = coefficients(eos)
    lo, hi = b * D('1.0000000000001'), D('.01')
    for _ in range(180):
        mid = (lo + hi) / 2
        if pressure(mid, eos) > 200:
            lo = mid
        else:
            hi = mid
    return 2 / (lo + hi)


def replay(candidate, molar_densities):
    """Solve each discrete implicit linear-pressure interval independently in water volume."""
    volume, capacity, productivity, scale = [D(str(candidate[k])) for k in ['volume', 'capacity', 'j', 'flowScale']]
    eos = candidate['eos']
    initial = volume * molar_densities[eos]
    withdrawal = D(1000) * molar_densities['SRK'] * D('.04') * scale
    water, previous = D(0), D(200)
    rows = [[0., 200., 0., 200., float(initial)]]
    x = productivity * 2500 / capacity
    loss = 1 - (-x).exp()
    ramp = 1 - loss / x
    for step in range(1, 5):
        amount = initial - withdrawal * step
        if amount <= 0:
            return None
        pa = 200 - water / capacity
        _, b = coefficients(eos)
        lo = D(0)
        hi = min(volume - b * amount * D('1.0000000000001'), capacity * 200)
        for _ in range(180):
            mid = (lo + hi) / 2
            endpoint = pressure((volume - mid) / amount, eos)
            residual = mid - water - capacity * ((pa - previous) * loss - (endpoint - previous) * ramp)
            if residual > 0:
                hi = mid
            else:
                lo = mid
        water = (lo + hi) / 2
        previous = pressure((volume - water) / amount, eos)
        rows.append([2500. * step, float(previous), float(water), float(200 - water / capacity), float(amount)])
    return rows


def quantile(values, weights, q):
    """Generalized inverse of the positive-mass discrete CDF; endpoints use its support."""
    pairs = sorted((v, w) for v, w in zip(values, weights) if w > 0)
    if q == 1.:
        return pairs[-1][0]
    cumulative = 0.
    for value, mass in pairs:
        cumulative += mass
        if cumulative >= q:
            return value
    return pairs[-1][0]


def moments(parameters, mass):
    """Population means and covariance, using a direct independent summation."""
    mean = [math.fsum(w * p[j] for p, w in zip(parameters, mass)) for j in range(5)]
    covariance = [[math.fsum(w * (p[j] - mean[j]) * (p[k] - mean[k]) for p, w in zip(parameters, mass))
                   for k in range(5)] for j in range(5)]
    return mean, covariance


def summarize(candidates, rows, observations, sigma):
    """Independent finite probability sums; only the first two pressures calibrate."""
    prior_sum = math.fsum(c['mass'] for c in candidates)
    prior = [c['mass'] / prior_sum for c in candidates]
    chi = [math.fsum(((r[i + 1][1] - observations[i]) / sigma) ** 2 for i in range(2)) if r else None
           for r in rows]
    best = min((i for i, value in enumerate(chi) if value is not None), key=lambda i: chi[i])
    relative = [p * math.exp(-.5 * (value - chi[best])) if value is not None else 0.
                for value, p in zip(chi, prior)]
    normalizer = math.fsum(relative)
    weights = [w / normalizer for w in relative]
    parameters = [[c['volume'], 1., 0., c['capacity'], c['j']] for c in candidates]
    mean, cov = moments(parameters, weights)
    before, prior_cov = moments(parameters, prior)
    varying = [len(set(p[j] for p in parameters)) > 1 for j in range(5)]
    correlations = [[cov[j][k] / math.sqrt(cov[j][j] * cov[k][k]) if varying[j] and varying[k] else None
                     for k in range(5)] for j in range(5)]
    shifts = [(mean[j] - before[j]) / math.sqrt(prior_cov[j][j]) if varying[j] else None for j in range(5)]
    pressures = [[r[t][1] if r else 0. for r in rows] for t in range(5)]
    probabilities = [0., .1, .5, .9, 1.]
    quantiles = [[quantile([p[j] for p in parameters], weights, q) for j in range(5)] for q in probabilities]
    pressure_quantiles = [[quantile(p, weights, q) for p in pressures] for q in probabilities]
    # Interior CDF distances protect support-value comparisons from roundoff at jumps.
    margins = []
    for values in [[p[j] for p in parameters] for j in range(5)] + pressures[1:]:
        pairs = sorted((v, w) for v, w in zip(values, weights) if w > 0)
        cdf = []
        total = 0.
        for index, (value, weight) in enumerate(pairs):
            total += weight
            if index + 1 < len(pairs) and value != pairs[index + 1][0]:
                cdf.append(total)
        margins.extend(abs(q - value) for q in probabilities[1:-1] for value in cdf)
    return {'prior': prior, 'weights': weights, 'chiSquare': chi, 'best': best, 'means': mean,
            'correlations': correlations, 'shifts': shifts, 'parameterQuantiles': quantiles,
            'pressureQuantiles': pressure_quantiles, 'effectiveCount': 1 / math.fsum(w * w for w in weights),
            'logEvidence': -.5 * chi[best] + math.log(normalizer),
            'rejectedMass': math.fsum(p for p, r in zip(prior, rows) if r is None),
            'cdfMargin': min(margins)}


def prior_cell(vlo, vhi, jlo, jhi):
    """Three-point tensor Gauss integral of an explicit conflicting correlated prior density."""
    nodes = [-math.sqrt(3 / 5), 0., math.sqrt(3 / 5)]
    weights = [5 / 9, 8 / 9, 5 / 9]
    terms = []
    for nv, wv in zip(nodes, weights):
        v = (vlo + vhi + nv * (vhi - vlo)) / 2
        for nj, wj in zip(nodes, weights):
            j = (jlo + jhi + nj * (jhi - jlo)) / 2
            z = (v - 1130.) / 70.
            y = (j - .0008) / .00015
            terms.append(wv * wj * math.exp(-.5 * (z * z + y * y - 1.2 * z * y) / .64))
    return math.fsum(terms) * (vhi - vlo) * (jhi - jlo) / 4


def grid(size, capacity=5., conflict=False, uncertain=False):
    """Complete joint midpoint hypotheses with integrated uniform or explicit prior masses."""
    result = []
    for i in range(size):
        vlo, vhi = 800. + i * 400. / size, 800. + (i + 1) * 400. / size
        for j in range(size):
            jlo, jhi = .0001 + j * .0008 / size, .0001 + (j + 1) * .0008 / size
            mass = prior_cell(vlo, vhi, jlo, jhi) if conflict else (vhi - vlo) * (jhi - jlo)
            for eos in (['SRK', 'PR'] if uncertain else ['SRK']):
                for scale in ([.9, 1.1] if uncertain else [1.]):
                    result.append({'volume': (vlo + vhi) / 2, 'capacity': capacity, 'j': (jlo + jhi) / 2,
                                   'mass': mass / (4 if uncertain else 1), 'eos': eos, 'flowScale': scale})
    return result


def build():
    """Freeze observations once and retain every independently predicted candidate endpoint."""
    densities = {eos: density(eos) for eos in ['SRK', 'PR']}
    truth = {'volume': 1000., 'capacity': 5., 'j': .0005, 'flowScale': 1., 'eos': 'SRK', 'mass': 1.}
    truth_rows = replay(truth, densities)
    observations = [truth_rows[1][1] + .3, truth_rows[2][1] - .2, truth_rows[3][1] + .1, None]
    joint_capacity = [dict(c, mass=c['mass'] / 3) for capacity in [2.5, 5., 10.] for c in grid(3, capacity=capacity)]
    definitions = [('uniform-3', grid(3)), ('uniform-5', grid(5)), ('uniform-9', grid(9)),
                   ('conflicting-prior-9', grid(9, conflict=True)), ('capacity-2.5', grid(5, capacity=2.5)),
                   ('capacity-10', grid(5, capacity=10.)), ('joint-capacity-3', joint_capacity),
                   ('eos-flow-3', grid(3, uncertain=True))]
    rejection = grid(3) + [dict(truth, volume=.01, mass=.16 / 9)]
    definitions += [('rejected-inventory', rejection), ('changed-holdout', grid(3))]
    cases = []
    for name, candidates in definitions:
        observed = list(observations)
        if name == 'changed-holdout':
            observed[2] += 100.
        rows = [replay(c, densities) for c in candidates]
        expected = summarize(candidates, rows, observed, 1.)
        cases.append({'name': name, 'sigma': 1., 'observations': observed, 'candidates': candidates,
                      'rows': rows, 'expected': expected})
    return {'sourceRevision': '863f12f4bfb58026e523748aefab14ecaf2288aa',
            'claim': 'independent discrete gas-only synthetic model and finite probability integration',
            'initialMolarDensities': {k: float(v) for k, v in densities.items()},
            'probabilities': [0., .1, .5, .9, 1.], 'truth': truth, 'truthRows': truth_rows, 'cases': cases}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--precision', type=int, default=60)
    parser.add_argument('--output', type=Path, required=True)
    arguments = parser.parse_args()
    with localcontext() as context:
        context.prec = arguments.precision
        data = build()
    arguments.output.write_text(json.dumps(data, indent=2, allow_nan=False) + '\n')
