"""Independent binary SRK fixed-cell phase-transition predictor (no NeqSim calls).

The inputs and tolerances are declared before candidate comparison. Common
chemical-potential tangents select gas/oil coexistence; a binary lever rule and
endpoint vapor withdrawal restore the first occupied volume. EOS inputs are
shared declared constants, not independent measurements or fitted parameters.
"""
import argparse
import json
from functools import lru_cache

import numpy as np
from scipy.optimize import least_squares, minimize_scalar
from scipy.special import expit, logit

R = 8.3144621
TC = np.array([190.55999999999997, 425.12])
PC = np.array([45.99, 37.96]) * 1e5
OMEGA = np.array([.0115, .2002])
KIJ = .01289789
COARSE = [100, 60, 50, 30, 20, 5, 2]
FINE = [100, 80, 65, 60, 55, 50, 40, 30, 25, 20, 10, 5, 3, 2]


class BinarySrk:
    """Evaluate cubic roots and binary chemical-potential common tangents."""

    def __init__(self, temperature):
        self.temperature = temperature
        c = np.cbrt(2) - 1
        self.b = c / 3 * R * TC / PC
        m = .48 + 1.574 * OMEGA - .176 * OMEGA**2
        a = R**2 * TC**2 / (9 * c * PC) * (1 + m * (1 - np.sqrt(temperature / TC)))**2
        self.aij = np.sqrt(np.outer(a, a)) * np.array([[1, 1-KIJ], [1-KIJ, 1]])

    def phase(self, methane, pressure, vapor):
        """Return log fugacity/p and molar volume on an admissible cubic root."""
        x = np.array([methane, 1-methane])
        am = x @ self.aij @ x
        bm = x @ self.b
        aa = am * pressure / (R * self.temperature)**2
        bb = bm * pressure / (R * self.temperature)
        roots = sorted(z.real for z in np.roots([1, -1, aa-bb-bb*bb, -aa*bb])
                       if abs(z.imag) < 1e-9 and z.real > bb)
        if not roots:
            raise RuntimeError('No admissible cubic root')
        z = roots[-1] if vapor else roots[0]
        lnphi = (self.b/bm*(z-1) - np.log(z-bb)
                 - aa/bb*(2*(self.aij@x)/am-self.b/bm)*np.log1p(bb/z))
        return np.log(x) + lnphi, z * R * self.temperature / pressure

    @lru_cache(None)
    def tie_line(self, pressure):
        """Solve the separated binary common tangent independently of feed."""
        def residual(u):
            x, y = expit(u)
            return self.phase(x, pressure, False)[0] - self.phase(y, pressure, True)[0]

        pairs = []
        for x in [.1, .4, .7, .9]:
            for y in [.95, .995, .99999]:
                sol = least_squares(residual, logit([x, y]), bounds=(-30, 30),
                                    xtol=1e-13, ftol=1e-13, gtol=1e-13, max_nfev=400)
                xl, yg = expit(sol.x)
                # This reference covers well-separated dry phases, not critical roots.
                if yg-xl > .01 and max(abs(sol.fun)) < 1e-11:
                    pairs.append((xl, yg))
        if not pairs:
            raise RuntimeError('Frozen pressure range requires a nondegenerate tie line')
        pair = pairs[0]
        if any(max(abs(np.array(other)-pair)) > 1e-8 for other in pairs):
            raise RuntimeError('Multiple separated binary tangents require further qualification: '+str((self.temperature,pressure,pairs)))
        return pair

    def stability_minimum(self, chemical_potential, pressure):
        """Search both roots over a fixed grid and refine every local minimum.

        This is a bounded one-dimensional numerical stability audit, not a
        general proof for arbitrary mixtures. Endpoints and tangent wells are
        resolved separately from the production flash.
        """
        grid = np.linspace(1e-10, 1-1e-10, 257)
        minimum = np.inf
        for vapor in [False, True]:
            def tpd(x):
                comp = np.array([x, 1-x])
                return comp @ (self.phase(x, pressure, vapor)[0]-chemical_potential)
            values = np.array([tpd(x) for x in grid])
            minimum = min(minimum, min(values))
            for i in range(1, len(grid)-1):
                if values[i] <= values[i-1] and values[i] <= values[i+1]:
                    sol = minimize_scalar(tpd, bounds=(grid[i-1], grid[i+1]), method='bounded',
                                          options={'xatol': 1e-14})
                    minimum = min(minimum, sol.fun)
        if minimum < -1e-9:
            raise RuntimeError('Independent endpoint has a negative tangent-plane minimum')
        return float(minimum)

    def state(self, n, pressure):
        """Select a positive gas-bearing endpoint by the binary lever rule."""
        total = sum(n)
        z = n[0] / total
        x, y = self.tie_line(pressure)
        if x < z < y:
            ng = total * (z-x) / (y-x)
            nl = total-ng
            mug, vg = self.phase(y, pressure, True)
            mul, vl = self.phase(x, pressure, False)
            phases = [dict(type='gas', moles=ng, x=[y, 1-y], molarVolumeM3=vg),
                      dict(type='oil', moles=nl, x=[x, 1-x], molarVolumeM3=vl)]
            mu = mug
            if max(abs(mug-mul)) > 1e-11:
                raise RuntimeError('Unclosed independent equilibrium')
        elif z >= y:
            mu, vg = self.phase(z, pressure, True)
            phases = [dict(type='gas', moles=total, x=[z, 1-z], molarVolumeM3=vg)]
        else:
            raise RuntimeError('Gas absent; outside frozen experiment domain')
        return phases, mu


def reference(eos, methane, pressures):
    """Predict a one-mole experiment without reading candidate states."""
    n = np.array([methane, 1-methane])
    initial = n.copy()
    cumulative = np.zeros(2)
    rows = []
    target = None
    for pressure_bara in pressures:
        pressure = pressure_bara * 1e5
        phases, _ = eos.state(n, pressure)
        volume = sum(p['moles'] * p['molarVolumeM3'] for p in phases)
        if target is None:
            target = volume
        gas = phases[0]
        removed = (volume-target) / gas['molarVolumeM3']
        if removed < -1e-12 or removed >= gas['moles']:
            raise RuntimeError('Reference cannot be restored by nonnegative gas withdrawal')
        withdrawn = removed * np.array(gas['x'])
        n -= withdrawn
        cumulative += withdrawn
        phases, mu = eos.state(n, pressure)
        volume = sum(p['moles'] * p['molarVolumeM3'] for p in phases)
        tpd = eos.stability_minimum(mu, pressure)
        if abs(volume/target-1) > 1e-12 or max(abs(initial-cumulative-n)) > 1e-12:
            raise RuntimeError('Unclosed independent experiment')
        rows.append(dict(pressureBara=pressure_bara, volumeM3=volume,
                         retainedMoles=n.tolist(), withdrawnMoles=withdrawn.tolist(),
                         phases=phases, minimumTpd=tpd))
    return dict(temperatureK=eos.temperature, methaneFraction=methane,
                referenceVolumeM3=target, rows=rows)


def main():
    """Emit a deterministic complete prediction matrix, with no fitted input."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', required=True)
    parser.add_argument('--limits-output')
    args = parser.parse_args()
    cases = []
    for temperature in [272.5, 275, 277.5]:
        eos = BinarySrk(temperature)
        for methane in [.94, .95, .96]:
            for schedule in [COARSE, FINE, COARSE[1:]]:
                cases.append(reference(eos, methane, schedule))
    if args.limits_output:
        eos = BinarySrk(275)
        y = .95
        def residual(u):
            x = expit(u[0])
            pressure = np.exp(u[1])
            return eos.phase(x, pressure, False)[0] - eos.phase(y, pressure, True)[0]
        sol = least_squares(residual, [logit(.15), np.log(30e5)],
                            xtol=1e-13, ftol=1e-13, gtol=1e-13, max_nfev=400)
        boundary = np.exp(sol.x[1]) / 1e5
        if max(abs(sol.fun)) > 1e-11 or not 20 < boundary < 50:
            raise RuntimeError('Independent lower-dew boundary did not close')
        limits = []
        for delta in [.1, .01, .001]:
            for sign in [-1, 1]:
                result = reference(eos, y, [60, boundary + sign*delta])
                result['boundaryPressureBara'] = boundary
                result['offsetBara'] = sign*delta
                limits.append(result)
        with open(args.limits_output, 'w', encoding='utf-8') as stream:
            json.dump(limits, stream, indent=2, allow_nan=False)
            stream.write('\n')
    with open(args.output, 'w', encoding='utf-8') as stream:
        json.dump(cases, stream, indent=2, allow_nan=False)
        stream.write('\n')
    print(json.dumps(dict(paths=len(cases), states=sum(len(c['rows']) for c in cases),
                          minimumTpd=min(r['minimumTpd'] for c in cases for r in c['rows']))))


if __name__ == '__main__':
    main()
