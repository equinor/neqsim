"""Independent binary SRK wet-gas fixed-cell reference; no NeqSim or fitted inputs."""
import json
import numpy as np
from scipy.optimize import least_squares
from scipy.special import expit, logit

R = 8.3144621
TC = np.array([190.55999999999997, 647.3])
PC = np.array([45.99, 220.89]) * 1e5
OMEGA = np.array([.0115, .344])
PRESSURES = [200, 150, 100, 70, 50, 30, 20, 10, 5, 2]


def model(temperature):
    """Return independent homogeneous SRK branches and coexistence prediction."""
    c = np.cbrt(2) - 1
    b = c / 3 * R * TC / PC
    m = .48 + 1.574 * OMEGA - .176 * OMEGA**2
    a = R**2 * TC**2 / (9*c*PC) * (1+m*(1-np.sqrt(temperature/TC)))**2
    aij = np.sqrt(np.outer(a, a)) * np.array([[1, .55], [.55, 1]])

    def branches(fraction, pressure):
        """Evaluate every positive real cubic-volume root, without phase labels."""
        x = np.array([fraction, 1-fraction])
        am, bm = x @ aij @ x, x @ b
        aa = am*pressure/(R*temperature)**2
        bb = bm*pressure/(R*temperature)
        roots = sorted(z.real for z in np.roots([1, -1, aa-bb-bb*bb, -aa*bb])
                       if abs(z.imag) < 1e-9 and z.real > bb)
        results = []
        for z in roots:
            phi = b/bm*(z-1)-np.log(z-bb)-aa/bb*(2*(aij@x)/am-b/bm)*np.log1p(bb/z)
            mu = np.log(x)+phi
            results.append((x @ mu, mu, z*R*temperature/pressure))
        return results

    def branch(fraction, pressure, vapor):
        """Select the declared large/small-volume branch only for coexistence."""
        values = branches(fraction, pressure)
        return values[-1] if vapor else values[0]

    def pair(pressure):
        """Locate a lower-convex-hull gap, then refine its common tangent."""
        grid = np.unique(np.r_[np.linspace(1e-12, 1-1e-12, 501),
                               np.geomspace(1e-12, .1, 120), 1-np.geomspace(1e-12, .1, 120)])
        energy = [min(item[0] for item in branches(x, pressure)) for x in grid]
        hull = []
        for i in range(len(grid)):
            while len(hull) >= 2:
                left, right = hull[-2:]
                if (energy[right]-energy[left])/(grid[right]-grid[left]) < (energy[i]-energy[right])/(grid[i]-grid[right]):
                    break
                hull.pop()
            hull.append(i)
        gaps = [(left, right) for left, right in zip(hull[:-1], hull[1:])
                if right-left > 2 and grid[right]-grid[left] > .01]
        if not gaps:
            return None
        if len(gaps) != 1:
            raise RuntimeError('Unsupported multiple miscibility gaps')
        left, right = gaps[0]

        def residual(u):
            x, y = expit(u)
            return branch(x, pressure, False)[1]-branch(y, pressure, True)[1]

        solution = least_squares(residual, logit([grid[left], grid[right]]),
                                 xtol=1e-13, ftol=1e-13, gtol=1e-13, max_nfev=1000)
        x, y = expit(solution.x)
        if max(abs(solution.fun)) > 1e-10 or y-x <= .01:
            raise RuntimeError('Unclosed/degenerate reference tangent')
        # A sampled lower-support screen checks all roots, not just root ordering.
        slope = (branch(y, pressure, True)[0]-branch(x, pressure, False)[0])/(y-x)
        base = branch(x, pressure, False)[0]
        if min(energy[i]-base-slope*(grid[i]-x) for i in range(len(grid))) < -1e-8:
            raise RuntimeError('Reference tangent is not lower support on declared grid')
        return x, y

    return branch, branches, pair



def generate(temperatures, water_fractions, sample_amounts, pressures):
    """Generate all declared complete paths; reject any unsupported reference state."""
    records = []
    for temperature in temperatures:
        branch, branches, pair = model(temperature)
        pairs = {p: pair(p*1e5) for p in pressures}
        for water in water_fractions:
            for amount in sample_amounts:
                initial = amount*np.array([1-water, water])
                inventory = initial.copy()
                cumulative = np.zeros(2)
                target = None
                rows = []
                for p in pressures:
                    pressure = p*1e5
                    z = inventory[0]/sum(inventory)
                    endpoints = pairs[p]
                    if endpoints is not None and endpoints[0] < z < endpoints[1]:
                        x, y = endpoints
                        gas = (inventory[0]-sum(inventory)*x)/(y-x)
                        amounts = np.array([gas, sum(inventory)-gas])
                        xs = np.array([[y, 1-y], [x, 1-x]])
                        volumes = np.array([branch(y, pressure, True)[2], branch(x, pressure, False)[2]])
                        phases = ['gas', 'aqueous']
                    else:
                        if endpoints is not None and z <= endpoints[0]:
                            raise RuntimeError('Reference retained gas absent')
                        homogeneous_roots = branches(z, pressure)
                        homogeneous = min(homogeneous_roots, key=lambda v: v[0])
                        if homogeneous is not homogeneous_roots[-1]:
                            raise RuntimeError('Homogeneous liquid, not gas, is stable')
                        amounts = np.array([sum(inventory)])
                        xs = np.array([[z, 1-z]])
                        volumes = np.array([homogeneous[2]])
                        phases = ['gas']
                    before = amounts @ volumes
                    if target is None:
                        target = before
                    removed_gas = (before-target)/volumes[0]
                    if removed_gas < 0 or removed_gas >= amounts[0]:
                        raise RuntimeError('Reference gas-only restoration infeasible')
                    amounts[0] -= removed_gas
                    removed = removed_gas*xs[0]
                    retained = xs.T @ amounts
                    cumulative += removed
                    if max(abs(retained+cumulative-initial)) > 1e-10*max(1, amount):
                        raise RuntimeError('Reference inventory closure')
                    if abs(amounts @ volumes/target-1) > 1e-12:
                        raise RuntimeError('Reference volume closure')
                    rows.append(dict(pressureBara=p, phaseNames=phases, phaseMoles=amounts.tolist(),
                                     phaseCompositions=xs.tolist(), retainedMoles=retained.tolist(),
                                     withdrawnMoles=removed.tolist()))
                    inventory = retained
                records.append(dict(temperatureK=temperature, waterMoleFraction=water, sampleMoles=amount,
                                    targetVolumeM3=target, rows=rows))
    return records


if __name__ == "__main__":
    records = generate([440, 450, 460], [.05, .08, .1], [.001, 1., 1000.], PRESSURES)
    print(json.dumps(records, indent=2, allow_nan=False))
