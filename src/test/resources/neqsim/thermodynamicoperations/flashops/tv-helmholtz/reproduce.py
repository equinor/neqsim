# Requires NumPy/SciPy; independent cubic Helmholtz minimization, no NeqSim imports.
from pathlib import Path
import csv,json
import numpy as np
from scipy.optimize import minimize
w=Path(__file__).resolve().parent
rows=list(csv.DictReader((w/'inputs.csv').open()))
c=[r for r in rows if r['kind']=='component'];ph=[[r for r in rows if r['kind']=='phase' and int(r['i'])==p] for p in (0,1)]
n=np.array([float(r['n']) for r in c]);nt=n.sum();z=n/nt
ai=np.array([float(r['a']) for r in c]);at=np.array([float(r['aT']) for r in c]);bi=np.array([float(r['b']) for r in c]);k=np.zeros((4,4))
for r in rows:
 if r['kind']=='kij':k[int(r['i']),int(r['j'])]=float(r['a'])
aij=np.sqrt(ai[:,None]*ai[None,:])*(1-k)
atij=aij*.5*(at[:,None]/ai[:,None]+at[None,:]/ai[None,:])
bij=((np.sqrt(bi[:,None])+np.sqrt(bi[None,:]))*.5)**2
T=float(ph[0][0]['T']);R=float(ph[0][0]['R']);rt=R*T
V=sum(float(p[0]['V']) for p in ph);d1=1+np.sqrt(2);d2=1-np.sqrt(2)
def props(m,v):
 N=m.sum();A=m@aij@m;B=(m@bij@m)/N;AT=m@atij@m
 P=rt*N/(v-B)-A/((v+d1*B)*(v+d2*B))
 F=(m*(np.log(m/v)-1)).sum()-N*np.log(1-B/v)-A/(rt*B*(d1-d2))*np.log((v+d1*B)/(v+d2*B))
 H=rt*N*(P*v/(N*rt)-1)+(T*AT-A)/(B*(d1-d2))*np.log((v+d1*B)/(v+d2*B))
 return F,H,P,A,AT,B
def gaps(q):
 l=nt*q[:4];g=n-l;vl=V*q[4];vg=V-vl
 return np.array([(vl-(l@bij@l)/l.sum())/V,(vg-(g@bij@g)/g.sum())/V])
def objective(q):
 if min(gaps(q).real)<=0:return 1e3+1e6*(np.minimum(gaps(q).real,0)**2).sum()
 l=nt*q[:4];g=n-l;vl=V*q[4];vg=V-vl
 return (props(l,vl)[0]+props(g,vg)[0])/nt
def jac(fun,q):
 vals=[]
 for i in range(5):
  c=q.astype(complex);c[i]+=1e-20j;vals.append(np.imag(fun(c))/1e-20)
 return np.array(vals).T
candidate=np.array([float(r['n'])/nt for r in ph[1]]+[float(ph[1][0]['V'])/V])
reported=[]
for p in ph:
 m=np.array([float(r['n']) for r in p]);v=float(p[0]['V']);pp=props(m,v)
 reported.append(dict(independent_F_RT=float(pp[0]),independent_Hres_J=float(pp[1]),reported_Hres_J=float(p[0]['Hres']),P_bar=float(pp[2]),A=float(pp[3]),reported_A=float(p[0]['a']),AT=float(pp[4]),reported_AT=float(p[0]['aT']),B=float(pp[5]),reported_B=float(p[0]['b'])))
one=props(n,V);Fone=one[0];Fcand=objective(candidate)*nt
bounds=[(1e-12,zi-1e-12) for zi in z]+[(1e-10,1-1e-10)]
trials=[]
for scale in [.4,.7,1,1.3,1.6]:
 q=candidate*scale;q[:4]=np.minimum(q[:4],z*.9)
 sol=minimize(objective,q,jac=lambda q:jac(objective,q),bounds=bounds,constraints=[{'type':'ineq','fun':gaps,'jac':lambda q:jac(gaps,q)}],method='SLSQP',options={'ftol':1e-13,'maxiter':1000})
 trials.append(dict(success=bool(sol.success),message=sol.message,F_RT=float(sol.fun*nt),q=sol.x.tolist(),nit=sol.nit))
result=dict(T=T,V_internal=V,n=n.tolist(),phase_checks=reported,one_phase_F_RT=float(Fone),candidate_F_RT=float(Fcand),candidate_minus_one_phase_A_J=float((Fcand-Fone)*rt),one_phase_Hres_J=float(one[1]),candidate_Hres_J=float(sum(r['independent_Hres_J'] for r in reported)),candidate_total_H_J=float(ph[0][0]['H']),trials=trials)
print(json.dumps(result,indent=2))
