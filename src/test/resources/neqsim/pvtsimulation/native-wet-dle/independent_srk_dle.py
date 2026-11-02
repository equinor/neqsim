"""Independent three-phase classic SRK wet DLE reference; no NeqSim calls or fitted inputs.

R=8.3144621 J/(mol K), Tc K, Pc bara, omega and kij are declared shared model inputs.
Solve six log-fugacity equalities and three material balances at each pressure.
Remove the complete equilibrium gas; retain oil and aqueous component inventories.
Frozen comparison bounds: volume relative 1e-8, phase composition/amount absolute 1e-8.
This is synthetic model consistency, not measured water/hydrocarbon accuracy.
"""
import json
import numpy as np
from scipy.optimize import least_squares
from scipy.special import softmax
R=8.3144621; T=350.
tc=np.array([190.55999999999997,617.7,647.3]);pc=np.array([45.99,21.1,220.89])*1e5
omega=np.array([.0115,.4923,.344]);kij=np.array([[0,.041099999,.45],[.041099999,0,0],[.45,0,0]])
c=np.cbrt(2)-1;b=c/3*R*tc/pc;m=.48+1.574*omega-.176*omega**2
a=R**2*tc**2/(9*c*pc)*(1+m*(1-np.sqrt(T/tc)))**2;aij=np.sqrt(np.outer(a,a))*(1-kij)

def phase(x,p,vapor):
    """Return log fugacities and molar volume from the stated cubic root."""
    am=x@aij@x;bm=x@b;A=am*p/(R*T)**2;B=bm*p/(R*T)
    roots=sorted(z.real for z in np.roots([1,-1,A-B-B*B,-A*B]) if abs(z.imag)<1e-9 and z.real>B)
    Z=roots[-1] if vapor else roots[0]
    phi=b/bm*(Z-1)-np.log(Z-B)-A/B*(2*(aij@x)/am-b/bm)*np.log1p(B/Z)
    return np.log(x)+phi,Z*R*T/p

def compositions(u):
    """Normalize two independent log-ratios for each phase without clipping."""
    return [softmax([*u[i:i+2],0]) for i in [0,2,4]]

def residual(u,p):
    """Impose oil/gas and aqueous/gas equality for all three components."""
    gas,oil,water=compositions(u);g=phase(gas,p,True)[0]
    return np.concatenate([phase(oil,p,False)[0]-g,phase(water,p,False)[0]-g])

records=[]
amounts=np.array([.6,.2,.2])
for pressure in [200,100,50]:
    p=pressure*1e5
    initial=[np.log(.99/.003),np.log(.007/.003),np.log(.3/.05),np.log(.65/.05),np.log(1e-5),np.log(1e-16)]
    solution=least_squares(residual,initial,args=(p,),bounds=(-60,60),xtol=1e-13,ftol=1e-13,gtol=1e-13,max_nfev=1000)
    if max(abs(solution.fun))>1e-10:raise RuntimeError('Unclosed independent equilibrium')
    xs=compositions(solution.x)
    phase_amounts=np.linalg.solve(np.array(xs).T,amounts)
    if min(phase_amounts)<=0:raise RuntimeError('Unsupported phase disappearance')
    gas=phase_amounts[0]*xs[0]
    retained=sum(n*x for n,x in zip(phase_amounts[1:],xs[1:]))
    if max(abs(retained+gas-amounts))>1e-12:raise RuntimeError('Unclosed withdrawal')
    eqvolume=sum(n*phase(x,p,i==0)[1] for i,(n,x) in enumerate(zip(phase_amounts,xs)))
    retvolume=sum(n*phase(x,p,False)[1] for n,x in zip(phase_amounts[1:],xs[1:]))
    records.append(dict(temperatureK=T,pressureBara=pressure,equilibriumVolumeM3=eqvolume,retainedVolumeM3=retvolume,withdrawnMoles=gas.tolist(),retainedMoles=retained.tolist(),phaseMoles=phase_amounts.tolist(),phaseCompositions=[x.tolist() for x in xs]))
    amounts=retained
print(json.dumps(records,indent=2,allow_nan=False))
