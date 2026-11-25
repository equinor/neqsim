"""Independent bounded binary PR equilibrium and tank oracle; validation only."""
import json
import math
from functools import lru_cache

R = 8.3144621
TC = (190.56, 540.2)
PC = (45.99e5, 27.4e5)
OMEGA = (0.0115, 0.349)
OA, OB = 0.45724333333, 0.077803333


def roots(a, b, c):
    """Isolate real monic cubic roots using derivative extrema and bisection."""
    def f(z):
        return ((z+a)*z+b)*z+c
    edge = 1+max(abs(a), abs(b), abs(c))
    cuts = [-edge, edge]
    d = a*a-3*b
    if d > 0:
        cuts = [-edge, (-a-math.sqrt(d))/3, (-a+math.sqrt(d))/3, edge]
    result = []
    for lo, hi in zip(cuts, cuts[1:]):
        if f(lo)*f(hi) > 0:
            continue
        for _ in range(70):
            mid = (lo+hi)/2
            if f(lo)*f(mid) <= 0:
                hi = mid
            else:
                lo = mid
        result.append((lo+hi)/2)
    return result


def phase(t, p, x, liquid, kij=0.02):
    """Return molar volume and log fugacities on a declared cubic phase root."""
    if not all(math.isfinite(v) for v in (t,p,x,kij)) or t <= 0 or p <= 0 or not 0 < x < 1:
        raise ValueError('Invalid phase input')
    q = (x, 1-x)
    bi = [OB*R*tc/pc for tc, pc in zip(TC, PC)]
    ai = []
    for tc, pc, w in zip(TC, PC, OMEGA):
        m = 0.37464+1.54226*w-0.26992*w*w
        ai.append(OA*R*R*tc*tc/pc*(1+m*(1-math.sqrt(t/tc)))**2)
    cross = math.sqrt(ai[0]*ai[1])*(1-kij)
    am = q[0]**2*ai[0]+2*q[0]*q[1]*cross+q[1]**2*ai[1]
    bm = sum(z*b for z,b in zip(q,bi))
    A, B = am*p/(R*t)**2, bm*p/(R*t)
    zs = [z for z in roots(B-1, A-3*B*B-2*B, B**3+B*B-A*B) if z > B]
    if not zs:
        raise ValueError('No admissible phase root')
    z = min(zs) if liquid else max(zs)
    mix = [q[0]*ai[0]+q[1]*cross, q[0]*cross+q[1]*ai[1]]
    logterm = math.log((z+(1+math.sqrt(2))*B)/(z+(1-math.sqrt(2))*B))
    volume = z*R*t/p
    calculated = R*t/(volume-bm)-am/(volume*volume+2*bm*volume-bm*bm)
    if not math.isfinite(calculated) or abs(calculated/p-1) > 1e-9:
        raise ValueError('Cubic EOS pressure residual failed')
    lnphi = [bi[i]/bm*(z-1)-math.log(z-B)-A/(2*math.sqrt(2)*B)
             *(2*mix[i]/am-bi[i]/bm)*logterm for i in range(2)]
    return z*R*t/p, [math.log(q[i])+lnphi[i] for i in range(2)]


@lru_cache(maxsize=4096)
def tie(t, p, kij=0.02):
    """Find separated binary liquid/vapor compositions by damped Newton."""
    x, y = 0.25, 0.995
    def residual(a, b):
        vl, fl = phase(t,p,a,True,kij)
        vg, fg = phase(t,p,b,False,kij)
        return [fl[i]-fg[i] for i in range(2)], vl, vg
    for _ in range(60):
        f, vl, vg = residual(x,y)
        norm = max(map(abs,f))
        if norm < 2e-11:
            if y-x < 0.1 or vg <= vl:
                raise ValueError('Degenerate or reversed tie line')
            return x, y, vl, vg
        h = 1e-6
        fx = residual(x+h,y)[0]
        fy = residual(x,y+h)[0]
        a,c = [(fx[i]-f[i])/h for i in range(2)]
        b,d = [(fy[i]-f[i])/h for i in range(2)]
        det = a*d-b*c
        if abs(det) < 1e-14:
            raise ValueError('Singular tie-line Jacobian')
        dx,dy = (-d*f[0]+b*f[1])/det, (c*f[0]-a*f[1])/det
        scale = 1.0
        while scale > 1e-10:
            xx, yy = x+scale*dx, y+scale*dy
            if 0 < xx < yy < 1 and max(map(abs,residual(xx,yy)[0])) < norm:
                x,y = xx,yy
                break
            scale /= 2
        else:
            raise ValueError('Tie-line Newton failed')
    raise ValueError('Tie line did not converge')


def equilibrium(t, p, n):
    """Use the binary lever rule; reject compositions outside two-phase domain."""
    x,y,vl,vg = tie(t,p)
    total = sum(n)
    beta = (n[0]/total-x)/(y-x)
    if not 0 < beta < 1:
        raise ValueError('Outside qualified two-phase domain')
    return total*((1-beta)*vl+beta*vg), beta, x, y


def tv(t, n, volume, previous):
    """Bisect pressure on the local qualified two-phase volume branch."""
    lo,hi = previous*0.8,previous*1.2
    if not equilibrium(t,lo,n)[0] > volume > equilibrium(t,hi,n)[0]:
        raise ValueError('Fixed-volume root not bracketed')
    for _ in range(42):
        mid = (lo+hi)/2
        if equilibrium(t,mid,n)[0] > volume:
            lo = mid
        else:
            hi = mid
    return (lo+hi)/2


def generate(substeps=4):
    """Generate prescribed phase schedules with independently predicted products."""
    if isinstance(substeps,bool) or not isinstance(substeps,int) or substeps <= 0:
        raise ValueError('Positive integer subdivisions required')
    t,p,v = 323.15,80e5,100.0
    x,y,vl,vg = tie(t,p)
    n = [v*0.7/vg*y+v*0.3/vl*x, v*0.7/vg*(1-y)+v*0.3/vl*(1-x)]
    return replay(n,substeps)


def replay(initial_moles, substeps=4):
    """Replay the frozen schedule for supplied two-phase initial component mol."""
    if isinstance(substeps,bool) or not isinstance(substeps,int) or substeps <= 0:
        raise ValueError('Positive integer subdivisions required')
    if len(initial_moles) != 2 or any(not math.isfinite(n) or n <= 0 for n in initial_moles):
        raise ValueError('Two positive finite initial component amounts required')
    t,p,v = 323.15,80e5,100.0
    n = list(initial_moles)
    if abs(equilibrium(t,p,n)[0]/v-1) > 1e-9:
        raise ValueError('Initial component amounts do not occupy the frozen tank volume')
    initial = n[:]
    rows = []
    for _ in range(4):
        previous = n[:]
        produced = [0.0,0.0]
        for _ in range(substeps):
            _,beta,x,y = equilibrium(t,p,n)
            if 8000/substeps > beta*sum(n) or 2000/substeps > (1-beta)*sum(n):
                raise ValueError('Requested phase inventory exhausted')
            removed = [(8000*y+2000*x)/substeps,(8000*(1-y)+2000*(1-x))/substeps]
            n = [n[i]+(200,10)[i]/substeps-removed[i] for i in range(2)]
            produced = [produced[i]+removed[i] for i in range(2)]
            p = tv(t,n,v,p)
        for i in range(2):
            if abs(n[i]+produced[i]-previous[i]-(200,10)[i])/sum(previous) > 1e-9:
                raise ValueError('Component conservation failed')
        volume,beta,x,y = equilibrium(t,p,n)
        if abs(volume/v-1) > 1e-9:
            raise ValueError('Volume closure failed')
        sv,sbeta,sx,sy = equilibrium(288.15,1.01325e5,produced)
        _,_,sl,sg = tie(288.15,1.01325e5)
        gor = sbeta*sg/((1-sbeta)*sl)
        rows.append(dict(pressure_bara=p/1e5,retained_moles=n[:],produced_moles=produced,
                         produced_methane_fraction=produced[0]/sum(produced),gor=gor))
    return dict(initial_volume_m3=v,initial_moles=initial,substeps=substeps,rows=rows)


def laboratory():
    """Identify only kij algebraically from an independent supercritical volume."""
    t,p,z = 600.0,200e5,0.8
    v,_ = phase(t,p,z,False)
    bi = [OB*R*tc/pc for tc,pc in zip(TC,PC)]
    ai = []
    for tc,pc,w in zip(TC,PC,OMEGA):
        m = 0.37464+1.54226*w-0.26992*w*w
        ai.append(OA*R*R*tc*tc/pc*(1+m*(1-math.sqrt(t/tc)))**2)
    b = z*bi[0]+(1-z)*bi[1]
    observed_a = (R*t/(v-b)-p)*(v*v+2*b*v-b*b)
    cross = 2*z*(1-z)*math.sqrt(ai[0]*ai[1])
    identified = (z*z*ai[0]+(1-z)**2*ai[1]+cross-observed_a)/cross
    return dict(temperature_k=t,pressure_pa=p,molar_volume_m3=v,identified_kij=identified,
                holdout_temperature_k=650.0,
                holdout=[dict(pressure_pa=q,molar_volume_m3=phase(650.0,q,z,False)[0])
                         for q in (200e5,150e5,100e5,50e5)])


if __name__ == '__main__':
    result = generate()
    result['laboratory'] = laboratory()
    print(json.dumps(result,indent=2,allow_nan=False))
