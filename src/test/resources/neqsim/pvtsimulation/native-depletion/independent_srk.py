"""Independent binary SRK tie-line and algebraic depletion audit; no NeqSim calls.

Frozen comparison: relative volume <=1e-6, mole fraction <=1e-6,
component closure <=1e-10 mol for the one-mole feed. Solvers converge <=1e-10.
Classical SRK fugacity and quadratic/linear mixing equations are documented at
https://idaes-pse.readthedocs.io/en/2.0.0/explanations/components/property_package/general/eos/cubic.html
Exact SRK critical coefficients replace the displayed rounded coefficients.
"""
import json
import argparse
import numpy as np
from scipy.optimize import least_squares
from scipy.special import expit, logit

R = 8.3144621  # Declared candidate constant; not fitted.
PARSER = argparse.ArgumentParser(description=__doc__)
PARSER.add_argument('--states', required=True)
PARSER.add_argument('--output', required=True)
PARSER.add_argument('--scan-pressure-reference', action='store_true')
ARGS = PARSER.parse_args()
with open(ARGS.states, encoding='utf-8') as stream:
    CASES = json.load(stream)
json.dumps(CASES, allow_nan=False)  # Reject non-finite inputs before reductions.
REPORT = []
SCAN_REFERENCE = ARGS.scan_pressure_reference
if [case['path'] for case in CASES] != ['DLE','CVD']:
    raise ValueError('Complete ordered DLE/CVD case matrix required')

for case in CASES:
    expected_pressures = [500, 300, 200, 100, 50] if case['path'] == 'DLE' else [500, 300, 200, 100]
    if [row['pressureBara'] for row in case['rows']] != expected_pressures:
        raise ValueError('Complete ordered pressure matrix required')
    expected_temperature = 323.15 if case['path'] == 'DLE' else 373.15
    expected_methane = .7 if case['path'] == 'DLE' else .95
    if case['temperatureK'] != expected_temperature or case['initial'] != [expected_methane, 1-expected_methane]:
        raise ValueError('Frozen temperature and one-mole feed required')
    T = case['temperatureK']
    params = np.array(case['TcK_PcBara_omega'])
    tc, pc, omega = params.T
    pc = pc * 1e5
    c = np.cbrt(2) - 1
    b = c / 3 * R * tc / pc
    m = .48 + 1.574 * omega - .176 * omega**2
    a = R**2 * tc**2 / (9*c*pc) * (1+m*(1-np.sqrt(T/tc)))**2
    aij = np.sqrt(np.outer(a,a)) * np.array([[1,1-case['kij']],[1-case['kij'],1]])

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

    n = np.array(case['initial'])
    initial = n.copy()
    cumulative = np.zeros(2)
    native_cumulative = np.zeros(2)
    target_volume = None
    if case['path']=='CVD':
        dew = least_squares(residual,[logit(.5),np.log(400e5)],args=(None,initial[0]),
            xtol=1e-13,ftol=1e-13,gtol=1e-13,max_nfev=300)
        if max(abs(dew.fun))>=1e-10 or initial[0]-expit(dew.x[0])<=.01:
            raise RuntimeError('Dew equilibrium failed or collapsed to a trivial root')
        dew_pressure = np.exp(dew.x[1])
        target_volume = phase(initial,dew_pressure,True)[1]
        if SCAN_REFERENCE:
            scan_pressure = case['scanSaturationPressureBara'] * 1e5
            pair = tie_line(scan_pressure)
            if pair is None or not pair[0][0]<initial[0]<pair[1][0]:
                raise RuntimeError('Scan reference must be a separated binary equilibrium')
            x,y = pair
            beta = (initial[0]-x[0])/(y[0]-x[0])
            target_volume = (1-beta)*phase(x,scan_pressure,False)[1]+beta*phase(y,scan_pressure,True)[1]
    errors = dict(volumeRelative=0.,compositionAbsolute=0.,inventoryAbsoluteMol=0.,closureMol=0.)
    rows = []
    for row in case['rows']:
        phase_types = [phase['type'] for phase in row['phases']]
        if not phase_types or len(set(phase_types)) != len(phase_types) or set(phase_types)-{'gas','oil'}:
            raise ValueError('One or two distinct hydrocarbon phases required')
        pressure = row['pressureBara']*1e5
        z = n/sum(n)
        pair = tie_line(pressure)
        withdrawal = np.zeros(2)
        two_phase = pair is not None and pair[0][0]<z[0]<pair[1][0]
        if two_phase:
            if set(phase_types) != {'gas','oil'}:
                raise RuntimeError('Independent two-phase classification disagrees')
            x,y = pair
            fraction = (z[0]-x[0])/(y[0]-x[0])
            nl,ng = sum(n)*(1-fraction),sum(n)*fraction
            vl,vg = phase(x,pressure,False)[1],phase(y,pressure,True)[1]
            volume = nl*vl+ng*vg
            if case['path']=='DLE':
                withdrawal = ng*y
                volume = nl*vl
            elif volume>target_volume:
                withdrawal = (volume-target_volume)/vg*y
                volume = target_volume
            for expected in row['phases']:
                pred = y if expected['type']=='gas' else x
                errors['compositionAbsolute'] = max(errors['compositionAbsolute'],max(abs(pred-np.array(expected['x']))))
        else:
            if len(row['phases'])!=1:
                raise RuntimeError('Independent phase classification disagrees')
            volume = sum(n)*phase(z,pressure,row['phases'][0]['type']=='gas')[1]
        n -= withdrawal
        cumulative += withdrawal
        native_cumulative += np.array(row['withdrawnMoles'])
        errors['volumeRelative'] = max(errors['volumeRelative'],abs(volume/row['volumeM3']-1))
        errors['inventoryAbsoluteMol'] = max(errors['inventoryAbsoluteMol'],max(abs(n-np.array(row['retainedMoles']))),max(abs(withdrawal-np.array(row['withdrawnMoles']))))
        errors['closureMol'] = max(errors['closureMol'],max(abs(initial-cumulative-n)))
        errors['closureMol'] = max(errors['closureMol'],max(abs(initial-native_cumulative-np.array(row['retainedMoles']))))
        rows.append(dict(pressureBara=row['pressureBara'],retainedMoles=n.tolist(),withdrawnMoles=withdrawal.tolist(),volumeM3=volume))
    REPORT.append(dict(path=case['path'],errors=errors,rows=rows,scanPressureReference=SCAN_REFERENCE,
        dewPressureBara=dew_pressure/1e5 if target_volume is not None else None))

with open(ARGS.output, 'w', encoding='utf-8') as stream:
    json.dump(REPORT, stream, indent=2, allow_nan=False)
    stream.write('\n')
print(json.dumps([dict(path=x['path'],errors=x['errors'],dewPressureBara=x['dewPressureBara']) for x in REPORT],indent=2))
for result in REPORT:
    limits = dict(volumeRelative=1e-6,compositionAbsolute=1e-6,inventoryAbsoluteMol=1e-6,closureMol=1e-10)
    if any(result['errors'][key]>limit for key,limit in limits.items()):
        raise RuntimeError('Frozen independent qualification failed: '+result['path'])
