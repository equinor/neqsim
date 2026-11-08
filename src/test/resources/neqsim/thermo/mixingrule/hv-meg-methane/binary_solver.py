"""Independent binary cubic-HV diagnostic; no NeqSim import or parameter fit.

Amounts differentiate n^2*a at 60-digit precision. R is absorbed into a and b.
This is a declared-input calculation, not original-author or native qualification.
"""
import csv
import json
import math
from decimal import Decimal as D, localcontext
from pathlib import Path

ROOT = Path(__file__).resolve().parent
DATA = ROOT / 'inputs'
EOS = 'PR'
MC = [None, None]

def attraction(n, t):
    """Return n^2 a with R absorbed; differentiate this extensive polynomial numerically."""
    total = sum(n)
    x = [v / total for v in n]
    pure = PURE
    aa, bb = [], []
    for index, (tc, pc, omega) in enumerate(pure):
        tc, pc, w = map(D, (tc, pc, omega))
        q = 1-(t/tc).sqrt()
        if MC[index] is not None:
            c1, c2, c3 = map(D, MC[index])
            alpha = (1+c1*q+c2*q*q+c3*q*q*q)**2
        elif EOS == 'SRK':
            m = D('.48')+D('1.574')*w-D('.176')*w*w
            alpha = (1+m*q)**2
        else:
            m = D('.37464')+D('1.54226')*w-D('.26992')*w*w
            alpha = (1+m*q)**2
        if EOS == 'SRK':
            third_root_two = (D(2).ln()/3).exp()
            omega_a = 1/(9*(third_root_two-1))
            omega_b = (third_root_two-1)/3
        else:
            omega_a, omega_b = D('.45724333333'), D('.077803333')
        aa.append(omega_a*tc*tc/pc*alpha)
        bb.append(omega_b*tc/pc)
    tau = [[D(0), D(TUPLE[0])/t+D(TUPLE[1])], [D(TUPLE[2])/t+D(TUPLE[3]), D(0)]]
    ge = D(0)
    for j in range(2):
        weights = [x[i]*bb[i]*(-D(TUPLE[4])*tau[i][j]).exp() for i in range(2)]
        ge += x[j]*sum(weights[i]*tau[i][j] for i in range(2))/sum(weights)
    b = sum(x[i]*bb[i] for i in range(2))
    sq = D(2).sqrt()
    lam = D(2).ln() if EOS == 'SRK' else ((2+sq)/(2-sq)).ln()/(2*sq)
    a = b*(sum(x[i]*aa[i]/bb[i] for i in range(2))-t*ge/lam)
    return total*total*a, b, bb

def phi(x, temp, pressure, gas, step):
    """Use independent mole differences of n^2a and a cubic root, not native gamma derivatives."""
    with localcontext() as ctx:
        ctx.prec = 60
        n = [D(str(x[0])), 1-D(str(x[0]))]; t = D(str(temp))
        av, bv, bb = attraction(n, t)
        derivatives = []
        for i in range(2):
            plus, minus = list(n), list(n)
            plus[i] += step; minus[i] -= step
            derivatives.append((attraction(plus,t)[0]-attraction(minus,t)[0])/(2*step))
        if abs(sum(n[i]*derivatives[i] for i in range(2))/(2*av)-1)>D('1e-8'):
            raise ValueError('Homogeneous attraction derivative identity')
        a,b = float(av),float(bv)
        coeff = [float(derivatives[i]/av-bb[i]/bv) for i in range(2)]
        br = [float(v/bv) for v in bb]
    A,B = a*pressure/temp**2,b*pressure/temp
    if EOS == 'SRK':
        c2, c1, c0 = -1, A-B-B*B, -A*B
    else:
        c2,c1,c0 = B-1,A-2*B-3*B*B,-A*B+B*B+B**3
    p,q = c1-c2*c2/3,2*c2**3/27-c2*c1/3+c0
    disc = (q/2)**2+(p/3)**3
    if disc < 0:
        angle = math.acos(max(-1.,min(1.,3*q/(2*p)*math.sqrt(-3/p))))/3
        zs = [2*math.sqrt(-p/3)*math.cos(angle-2*k*math.pi/3)-c2/3 for k in range(3)]
    else:
        cube=lambda v: math.copysign(abs(v)**(1/3),v)
        zs=[cube(-q/2+math.sqrt(disc))+cube(-q/2-math.sqrt(disc))-c2/3]
    zs=sorted(z for z in zs if z>B)
    z=zs[-1] if gas else zs[0]
    for _ in range(8):
        z-=(z**3+c2*z*z+c1*z+c0)/(3*z*z+2*c2*z+c1)
    sq=math.sqrt(2)
    factor = (A/B*math.log((z+B)/z) if EOS == 'SRK' else
              A/(2*sq*B)*math.log((z+(1+sq)*B)/(z+(1-sq)*B)))
    result=[br[i]*(z-1)-math.log(z-B)-factor*coeff[i] for i in range(2)]
    v=z*temp/pressure
    denom = v*(v+b) if EOS == 'SRK' else v*v+2*b*v-b*b
    numer = 2*v+b if EOS == 'SRK' else 2*v+2*b
    slope=-temp/(v-b)**2+a*numer/denom**2
    if slope>=0: raise ValueError('Unstable root')
    eos=temp/(v-b)-a/denom
    if abs(eos-pressure)>5e-7: raise ValueError('Pressure closure')
    return result,z,abs(eos-pressure)

def solve(t,p,step):
    """Solve two log-fugacity equalities in dilute-component logarithms."""
    def residual(u):
        yw,xn=map(math.exp,u)
        if not (0<yw<.5 and 0<xn<.5): raise ValueError('Outside declared dilute domain')
        g,zg,eg=phi([yw,1-yw],t,p,True,step)
        l,zl,el=phi([1-xn,xn],t,p,False,step)
        return [math.log(yw/(1-xn))+g[0]-l[0], math.log((1-yw)/xn)+g[1]-l[1]],(yw,xn,zg,zl,max(eg,el))
    u=[math.log(.005),math.log(.0002)]
    for iteration in range(30):
        f,state=residual(u)
        if max(map(abs,f))<1e-10:return state,max(map(abs,f)),iteration
        h=1e-4; columns=[]
        for i in range(2):
            v=list(u);v[i]+=h;ff,_=residual(v);columns.append([(ff[j]-f[j])/h for j in range(2)])
        a,c=columns[0];b,d=columns[1];det=a*d-b*c
        delta=[(-d*f[0]+b*f[1])/det,(c*f[0]-a*f[1])/det]
        rate=1.
        while rate>1e-6:
            v=[u[i]+rate*delta[i] for i in range(2)]
            try: ff,_=residual(v)
            except (ValueError,OverflowError): rate*=.5;continue
            if max(map(abs,ff))<max(map(abs,f)):u=v;break
            rate*=.5
        else:raise ValueError('Newton line search failed')
    raise ValueError('Equilibrium did not converge')
