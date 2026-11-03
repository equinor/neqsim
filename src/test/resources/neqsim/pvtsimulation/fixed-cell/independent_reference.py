"""Independent fixed-cell gas-withdrawal reference; no NeqSim calls or fitted inputs.

Classic unshifted SRK at declared T/P and component/model constants. This is
synthetic numerical consistency, not a dew reference or laboratory wet-CVD
accuracy claim. Solve fugacity equalities and component lever rules, set the
cell to the first equilibrium occupied volume, then withdraw gas only.
"""
import json
import numpy as np
from scipy.optimize import least_squares
from scipy.special import softmax

R = 8.3144621
TC = np.array([190.55999999999997, 617.7, 647.3])
PC = np.array([45.99, 21.1, 220.89]) * 1e5
OMEGA = np.array([.0115, .4923, .344])
KIJ = np.array([[0, .041099999, .45], [.041099999, 0, 0], [.45, 0, 0]])


def reference(temperature, feed, amount, family):
    """Solve a named phase family without inventing absent-phase properties."""
    indices = [0, 1, 2] if family == 'gas-oil-aqueous' else ([0, 1] if family == 'gas-oil' else [0, 2])
    names = [['methane', 'n-decane', 'water'][i] for i in indices]
    tc = TC[indices]
    pc = PC[indices]
    omega = OMEGA[indices]
    kij = KIJ[np.ix_(indices, indices)]
    c = np.cbrt(2) - 1
    b = c / 3 * R * tc / pc
    m = .48 + 1.574 * omega - .176 * omega**2
    a = R**2 * tc**2 / (9 * c * pc) * (1 + m * (1 - np.sqrt(temperature / tc)))**2
    aij = np.sqrt(np.outer(a, a)) * (1 - kij)
    ncomponent = len(indices)

    def phase(x, pressure, vapor):
        """Evaluate fugacity and molar volume on the stated SRK root."""
        am = x @ aij @ x
        bm = x @ b
        aa = am * pressure / (R * temperature)**2
        bb = bm * pressure / (R * temperature)
        roots = sorted(z.real for z in np.roots([1, -1, aa-bb-bb*bb, -aa*bb])
                       if abs(z.imag) < 1e-9 and z.real > bb)
        z = roots[-1] if vapor else roots[0]
        phi = b/bm*(z-1) - np.log(z-bb) - aa/bb*(2*(aij@x)/am-b/bm)*np.log1p(bb/z)
        return np.log(x) + phi, z * R * temperature / pressure

    def compositions(u):
        """Normalize log ratios for each named phase."""
        return np.array([softmax([*u[i:i+ncomponent-1], 0])
                         for i in range(0, len(u), ncomponent-1)])

    def residual(u, pressure):
        """Impose each present liquid's component fugacities equal to gas."""
        xs = compositions(u)
        gas = phase(xs[0], pressure, True)[0]
        return np.concatenate([phase(x, pressure, False)[0]-gas for x in xs[1:]])

    phase_names = ['gas', 'oil', 'aqueous'] if ncomponent == 3 else (['gas', 'oil'] if family == 'gas-oil' else ['gas', 'aqueous'])
    guess = ([np.log(.99/.003), np.log(.007/.003), np.log(.3/.05), np.log(.65/.05),
              np.log(1e-5), np.log(1e-16)] if ncomponent == 3 else [np.log(.997/.003), np.log(.3/.7) if family == 'gas-oil' else np.log(1e-5)])
    initial = np.array(feed) * amount
    inventory = initial.copy()
    cumulative = np.zeros(ncomponent)
    target = None
    rows = []
    for pressure_bara in [200, 100, 50]:
        pressure = pressure_bara * 1e5
        solution = least_squares(residual, guess, args=(pressure,), bounds=(-60, 60),
                                 xtol=1e-13, ftol=1e-13, gtol=1e-13, max_nfev=1000)
        if max(abs(solution.fun)) > 1e-10:
            raise RuntimeError('Unclosed equilibrium')
        xs = compositions(solution.x)
        phase_moles = np.linalg.solve(xs.T, inventory)
        if min(phase_moles) <= 0:
            raise RuntimeError('Unsupported phase state')
        if min(np.sum(abs(xs[i]-xs[j])) for i in range(len(xs)) for j in range(i)) <= 1e-8:
            raise RuntimeError('Degenerate phase pair')
        molar_volumes = np.array([phase(x, pressure, i == 0)[1] for i, x in enumerate(xs)])
        before_volume = phase_moles @ molar_volumes
        if target is None:
            target = before_volume
        gas_removed = (before_volume-target) / molar_volumes[0]
        if gas_removed < 0 or gas_removed >= phase_moles[0]:
            raise RuntimeError('Gas-only withdrawal cannot restore cell')
        phase_moles[0] -= gas_removed
        removed = gas_removed * xs[0]
        retained = xs.T @ phase_moles
        cumulative += removed
        if max(abs(retained+cumulative-initial)) > 1e-12:
            raise RuntimeError('Unclosed component balance')
        if abs(phase_moles @ molar_volumes / target - 1) > 1e-12:
            raise RuntimeError('Unclosed cell')
        rows.append(dict(temperatureK=temperature, pressureBara=pressure_bara,
                         beforeVolumeM3=before_volume, retainedVolumeM3=phase_moles @ molar_volumes,
                         withdrawnMoles=removed.tolist(), retainedMoles=retained.tolist(),
                         phaseMoles=phase_moles.tolist(), phaseCompositions=xs.tolist()))
        inventory = retained
    return dict(temperatureK=temperature, feed=feed, sampleMoles=amount, family=family,
                componentNames=names, phaseNames=phase_names, targetVolumeM3=target, rows=rows)


cases = []
for temperature in [340., 350., 360.]:
    for family, feeds in [('gas-oil-aqueous', [[.5, .3, .2], [.6, .2, .2], [.6, .1, .3]]),
                          ('gas-aqueous', [[.8, .2], [.6, .4], [.9, .1]]),
                          ('gas-oil', [[.6, .4], [.8, .2]])]:
        for feed in feeds:
            for amount in [.001, 1., 1000.]:
                cases.append(reference(temperature, feed, amount, family))
print(json.dumps(cases, indent=2, allow_nan=False))
