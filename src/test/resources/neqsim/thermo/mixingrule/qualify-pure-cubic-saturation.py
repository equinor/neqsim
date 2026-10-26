# Requires NumPy; standalone independent reference, no NeqSim imports.
import csv,json,math
from pathlib import Path
import numpy as np
from numpy.polynomial import Polynomial as Poly
work=Path(__file__).resolve().parent
results=[]
for row in json.loads((work/'hv-pure-cubic-saturation-reference.json').read_text())['inputs']:
    t,a,b,d1,d2,r=(float(row[k]) for k in ('T_K','a','b','delta1','delta2','R'))
    e,f=d1+d2,d1*d2
    astar=a/(r*t*b)
    y=Poly([0,1]); one=Poly([1])
    spin=(one+e*y+f*y*y)**2-astar*(one-y)**2*(2*y+e*y*y)
    ys=sorted(z.real for z in spin.roots() if abs(z.imag)<1e-9 and 0<z.real<1)
    assert len(ys)==2,(row,ys)
    def pressure(v):
        return r*t/b*(v/(1-v)-astar*v*v/(1+e*v+f*v*v))
    pmin,pmax=sorted(pressure(v) for v in ys)
    lo=max(pmin,pmax*1e-10); hi=pmax
    lo+=(hi-lo)*1e-7;hi-=(hi-lo)*1e-7
    def values(p):
        aa=a*p/(r*t)**2;bb=b*p/(r*t)
        roots=np.roots([1,(e-1)*bb-1,aa+f*bb*bb-e*bb-e*bb*bb,-f*bb*bb*(1+bb)-aa*bb])
        roots=sorted(z.real for z in roots if abs(z.imag)<1e-9 and z.real>bb)
        assert len(roots)==3,(row,p,roots)
        zl,zv=roots[0],roots[-1]
        assert zv-zl>1e-7*max(zv,zl)
        def phi(z):
            return z-1-math.log(z-bb)-aa/(bb*(d1-d2))*math.log((z+d1*bb)/(z+d2*bb))
        return phi(zv)-phi(zl),zl,zv
    flo=values(lo)[0]; fhi=values(hi)[0]
    assert flo*fhi<0,(row,flo,fhi)
    initial=[lo,hi]
    for iteration in range(100):
        mid=(lo+hi)/2
        fm,zl,zv=values(mid)
        if fm*flo>0:lo=mid;flo=fm
        else:hi=mid
        if hi-lo < 1e-12*max(1,mid):break
    p=(lo+hi)/2; residual,zl,zv=values(p)
    closures=[]
    for z in (zl,zv):
        v=z*r*t/p
        peos=r*t/(v-b)-a/((v+d1*b)*(v+d2*b))
        dpdv=-r*t/(v-b)**2+a*(2*v+e*b)/((v+d1*b)**2*(v+d2*b)**2)
        assert dpdv<0
        closures.append(abs(peos-p)/max(1,p))
    results.append(dict(eos=row['eos'],component=row['component'],T_K=t,P_bar=p,Z_liquid=zl,Z_vapor=zv,log_fugacity_residual=residual,max_relative_pressure_residual=max(closures),iterations=iteration+1,initial_bracket_bar=initial))
# Compare this output against the committed independent reference; do not overwrite it.
print(json.dumps(results,indent=2))
