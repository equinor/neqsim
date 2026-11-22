# Standard-library Decimal reference; no NeqSim or third-party imports.
from pathlib import Path
from decimal import Decimal as D, getcontext
import csv,json
getcontext().prec=80
w=Path(__file__).resolve().parent
rows=list(csv.DictReader((w/'qualified.csv').open()));results=[]
for row in rows:
 p,t,a,b,d1,d2,r=[D(row[k]) for k in ['P','T','a','b','delta1','delta2','R']]
 rt=r*t;y=b/D(row['V']);e=d1+d2;f=d1*d2;strength=a/(rt*b)
 def residual(y):return rt/b*(y/(1-y)-strength*y*y/(1+e*y+f*y*y))-p
 def slope(y):return rt/b*(1/(1-y)**2-strength*(2*y+e*y*y)/(1+e*y+f*y*y)**2)
 assert 0<y<1
 for iteration in range(30):
  error=residual(y)
  if abs(error)<D('1e-60')*max(D(1),p):break
  y-=error/slope(y)
  assert 0<y<1
 assert abs(residual(y))<D('1e-60')*max(D(1),p)
 assert slope(y)>0
 v=b/y;relative=abs((D(row['V'])-v)/v)
 results.append({'eos':row['eos'],'component':row['component'],'T_K':float(t),'P_bar':float(p),'requested_root':row['root'],'reference_volume_internal':str(v),'candidate_volume_internal':float(row['V']),'relative_volume_error':float(relative),'reference_relative_pressure_residual':float(abs(residual(y))/max(D(1),p))})
report={'source_head':'9863adc6d40a8029d8286e3885e56b034f22bcc7','candidate_method':'Largest Cardano root followed by stable quadratic deflation; discard excluded-volume roots','independent_method':'Python standard-library Decimal80-digit Newton root in reduced density; positive dP/dy required; no NeqSim imports','scope':'Pure SRK/PR water373.15K, CO2 280K, methane150K, nC16 350K; eight pressures and both requested endpoints. Includes metastable EOS roots; not saturation/experimental accuracy qualification.','units':{'T':'K','P':'bara','volume_internal':'physical m3/mol multiplied by1e5','b':'same internal molar-volume units','a':'bara times squared internal molar-volume units','R':'J/(mol K)'},'max_relative_volume_error':max(x['relative_volume_error'] for x in results),'max_relative_pressure_residual':max(x['reference_relative_pressure_residual'] for x in results),'results':results}
print(json.dumps({k:v for k,v in report.items() if k!='results'},indent=2))
