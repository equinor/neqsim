"""Independent six-state binary SRK closed-mass reference; no NeqSim calls or fitting.

Equations use the already qualified native-depletion SRK reference; fixed inputs
and six nondegenerate coexistence states. This is software consistency, not
measured laboratory accuracy. Generate from repository root with Python3.
"""
import json
import numpy as np
from scipy.optimize import least_squares
from scipy.special import expit, logit

R = 8.3144621
records = []
for T, methane in [(323.15, .7), (373.15, .95)]:
    tc = np.array([190.55999999999997, 617.7])
    pc = np.array([45.99, 21.1]) * 1e5
    omega = np.array([.0115, .4923])
    c = np.cbrt(2) - 1
    b = c / 3 * R * tc / pc
    m = .48 + 1.574 * omega - .176 * omega**2
    a = R**2 * tc**2 / (9*c*pc) * (1+m*(1-np.sqrt(T/tc)))**2
    aij = np.sqrt(np.outer(a,a)) * np.array([[1, 1-.041099999], [1-.041099999, 1]])
    def phase(composition, pressure, vapor):
        """Cubic roots and departure-function fugacity at one phase composition."""
        am = composition @ aij @ composition
        bm = composition @ b
        A = am*pressure/(R*T)**2
        B = bm*pressure/(R*T)
        roots = np.roots([1,-1,A-B-B*B,-A*B])
        roots = sorted(z.real for z in roots if abs(z.imag)<1e-9 and z.real>B)
        Z = roots[-1] if vapor else roots[0]
        lnphi = b/bm*(Z-1)-np.log(Z-B)-A/B*(2*(aij@composition)/am-b/bm)*np.log1p(B/Z)
        return lnphi, Z*R*T/pressure

    def residual(u, pressure, y_fixed=None):
        """Equality of both log component fugacities, with optional fixed dew gas."""
        x0 = expit(u[0])
        y0 = expit(u[1]) if y_fixed is None else y_fixed
        if y_fixed is not None:
            pressure = np.exp(u[1])
        x = np.array([x0,1-x0])
        y = np.array([y0,1-y0])
        return np.log(x)+phase(x,pressure,False)[0]-np.log(y)-phase(y,pressure,True)[0]

    def tie_line(pressure):
        """Find a separated, physically ordered binary coexistence pair."""
        for x0 in [.1,.4,.7,.9]:
            for y0 in [.95,.995,.99999]:
                solution = least_squares(residual,logit([x0,y0]),args=(pressure,),
                    xtol=1e-13,ftol=1e-13,gtol=1e-13,max_nfev=300)
                x0,y0 = expit(solution.x)
                if y0-x0>.01 and max(abs(solution.fun))<1e-10:
                    return np.array([x0,1-x0]),np.array([y0,1-y0])
        return None


    for pressure_bara in [200, 100, 50]:
        pressure = pressure_bara * 1e5
        pair = tie_line(pressure)
        if pair is None:
            raise RuntimeError('No separated binary coexistence root')
        x, y = pair
        beta = (methane-x[0])/(y[0]-x[0])
        if not 0 < beta < 1:
            raise RuntimeError('Reference feed is outside the two-phase tie line')
        volume = (1-beta)*phase(x, pressure, False)[1]+beta*phase(y, pressure, True)[1]
        records.append(dict(temperatureK=T, methane=methane, pressureBara=pressure_bara,
            volumeM3=volume, gasMoleFraction=beta, liquidComposition=x.tolist(), gasComposition=y.tolist()))
print(json.dumps(records, indent=2, allow_nan=False))
