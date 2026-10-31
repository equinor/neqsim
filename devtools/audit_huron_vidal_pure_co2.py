"""Audit declared CO2 cubic roots against a reference correlation and optional native results."""
import argparse
import csv
import hashlib
import json
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "src/test/resources/neqsim/thermo/mixingrule/hv-co2-source"
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--native", type=Path, help="Native pure-co2.csv produced by the JUnit fixture")
parser.add_argument("--output", type=Path, default=ROOT / "target/hv-co2-native/pure-co2-independent.csv")
args = parser.parse_args()
REF = json.loads((DATA / "pure-co2-reference.json").read_text())
C = 2.0 ** (1.0 / 3.0)
PROFILES = [
    ("PR2016_library", "PR", 304.19, 73.815, .45724333333, .077803333, None),
    ("SRK2017_declared", "SRK", 304.1282, 73.773, 1/(9*(C-1)), (C-1)/3,
     (.046115, .91388, 6.8838)),
    ("PR2017_declared", "PR", 304.1282, 73.773, .4572355289213822,
     .07779607390388846, (.022762, .92848, 7.7217)),
]
TEMPERATURES = [216.592, 220., 230., 250., 273.15, 290., 298.15, 301.]
def reference(t):
    assert REF["Tmin"] <= t <= REF["Tmax"]
    theta = 1 - t/REF["T_r"]
    return REF["reducing_value"]/1e5 * math.exp(
        REF["T_r"]/t * sum(n*theta**s for n,s in zip(REF["n"],REF["t"])))
def alpha(t, tc, parameters):
    tr = t/tc
    if parameters is None:
        w = .2276
        m = .37464 + 1.54226*w - .26992*w*w
        return (1+m*(1-math.sqrt(tr)))**2
    l,m,n = parameters
    return tr**(n*(m-1))*math.exp(l*(1-tr**(m*n)))
def roots(c2,c1,c0):
    p,q = c1-c2*c2/3, 2*c2**3/27-c2*c1/3+c0
    if p >= 0 or (q/2)**2+(p/3)**3 >= 0:
        return []
    angle = math.acos(max(-1.,min(1.,3*q/(2*p)*math.sqrt(-3/p))))/3
    return sorted(2*math.sqrt(-p/3)*math.cos(angle-2*k*math.pi/3)-c2/3
                  for k in range(3))
def eos_pressure(v,t,a,b,kind):
    den = v*(v+b) if kind=="SRK" else v*v+2*b*v-b*b
    return t/(v-b)-a/den
def dpdv(v,t,a,b,kind):
    if kind=="SRK":
        den,dend = v*(v+b),2*v+b
    else:
        den,dend = v*v+2*b*v-b*b,2*v+2*b
    return -t/(v-b)**2+a*dend/den**2
def lnphi(z,A,B,kind):
    if kind=="SRK":
        return z-1-math.log(z-B)-A/B*math.log1p(B/z)
    s=math.sqrt(2.)
    return z-1-math.log(z-B)-A/(2*s*B)*math.log((z+(1+s)*B)/(z+(1-s)*B))
def endpoint(t,p,a,b,kind):
    A,B = a*p/t**2,b*p/t
    zs = roots(-1,A-B-B*B,-A*B) if kind=="SRK" else roots(
        B-1,A-2*B-3*B*B,-A*B+B*B+B**3)
    if len(zs)!=3 or zs[0]<=B:
        return None
    lo,hi = zs[0],zs[-1]
    vs=[lo*t/p,hi*t/p]
    if min(vs)<=b or any(dpdv(v,t,a,b,kind)>=0 for v in vs):
        return None
    return lnphi(lo,A,B,kind)-lnphi(hi,A,B,kind),vs
def saturation(t,profile):
    name,kind,tc,pc,oa,ob,par=profile
    a,b=oa*tc*tc/pc*alpha(t,tc,par),ob*tc/pc
    pref=reference(t)
    previous=None
    bracket=None
    for i in range(6001):
        p=pref*(.5+i/6000)
        result=endpoint(t,p,a,b,kind)
        if result is not None:
            if previous is not None and previous[1]*result[0]<=0:
                bracket=(previous[0],p)
                break
            previous=(p,result[0])
    assert bracket is not None, (name,t,"No saturation bracket")
    lo,hi=bracket
    for _ in range(70):
        mid=(lo+hi)/2
        fl=endpoint(t,lo,a,b,kind)[0]
        fm=endpoint(t,mid,a,b,kind)[0]
        if fl*fm<=0:hi=mid
        else:lo=mid
    p=(lo+hi)/2
    residual,vs=endpoint(t,p,a,b,kind)
    closure=max(abs(eos_pressure(v,t,a,b,kind)/p-1) for v in vs)
    refinement=0.
    for v in vs:
        vl,vh=v*(1-1e-5),v*(1+1e-5)
        assert vl>b
        assert (eos_pressure(vl,t,a,b,kind)-p)*(eos_pressure(vh,t,a,b,kind)-p)<0
        for _ in range(60):
            vm=(vl+vh)/2
            if eos_pressure(vm,t,a,b,kind)>p:vl=vm
            else:vh=vm
        refinement=max(refinement,abs(((vl+vh)/2)/v-1))
    assert abs(residual)<1e-12 and closure<1e-10 and refinement<1e-10
    assert vs[1]>vs[0]*1.01
    return dict(profile=name,T_K=t,P_bara=p,reference_P_bara=pref,
                relative_error=p/pref-1,liquid_v_over_b=vs[0]/b,
                vapor_v_over_b=vs[1]/b,log_fugacity_residual=abs(residual),
                relative_EOS_pressure_error=closure,
                relative_volume_refinement_error=refinement)
rows=[saturation(t,p) for p in PROFILES for t in TEMPERATURES]
args.output.parent.mkdir(parents=True, exist_ok=True)
with args.output.open("w",newline="") as stream:
    writer=csv.DictWriter(stream,fieldnames=list(rows[0]),lineterminator="\n")
    writer.writeheader()
    writer.writerows(rows)
with (DATA / "pure-co2-reference.csv").open(newline="") as stream:
    projection = list(csv.DictReader(stream))
assert len(projection) == len(rows) == 24
for actual, expected in zip(projection, rows):
    assert int(actual["profile"]) == [p[0] for p in PROFILES].index(expected["profile"])
    for a, e in (("T_K", "T_K"), ("independent_bara", "P_bara"),
                 ("reference_bara", "reference_P_bara")):
        assert float(actual[a]) == expected[e], (a, actual, expected)
native_summary = None
if args.native:
    with args.native.open(newline="") as stream:
        native = list(csv.DictReader(stream))
    assert len(native) == 384
    expected_order = []
    for index, row in enumerate(rows):
        nearby = rows[index - 7 if index % 8 == 7 else index + 1]
        for rule in (2, 4):
            for start in (.1, 100.0):
                for history in range(4):
                    state = nearby if history == 2 else row
                    expected_order.append(([p[0] for p in PROFILES].index(state["profile"]),
                                           state["T_K"], rule, start, history))
    actual_order = [(int(row["profile"]), float(row["T_K"]), int(row["rule"]),
                     float(row["start_bara"]), int(row["history"])) for row in native]
    assert actual_order == expected_order, "Native matrix order/coverage differs"
    maximum = 0.0
    for observed in native:
        expected = next(row for row in rows
                        if row["profile"] == PROFILES[int(observed["profile"])][0]
                        and row["T_K"] == float(observed["T_K"]))
        assert int(observed["rule"]) in (2, 4)
        assert float(observed["start_bara"]) in (.1, 100.0)
        assert int(observed["history"]) in range(4)
        assert float(observed["independent_bara"]) == expected["P_bara"]
        assert float(observed["reference_bara"]) == expected["reference_P_bara"]
        for column, limit in (("max_EOS_pressure_error_bara", 5e-7),
                              ("log_fugacity_error", 1e-8)):
            residual = float(observed[column])
            assert math.isfinite(residual) and 0 <= residual < limit, (column, residual)
        error = abs(float(observed["native_bara"]) / expected["P_bara"] - 1)
        assert error < 5e-8
        maximum = max(maximum, error)
    pressure_error = max(float(row["max_EOS_pressure_error_bara"]) for row in native)
    fugacity_error = max(float(row["log_fugacity_error"]) for row in native)
    assert pressure_error < 5e-7 and fugacity_error < 1e-8
    native_summary = {"flashes": len(native), "maximum_relative_root_difference": maximum,
                      "max_EOS_pressure_error_bara": pressure_error,
                      "max_log_fugacity_error": fugacity_error,
                      "SHA256": hashlib.sha256(args.native.read_bytes()).hexdigest()}
summary={p[0]:{"points":len(TEMPERATURES),
              "AARD_percent":100*sum(abs(r["relative_error"]) for r in rows
                                     if r["profile"]==p[0])/len(TEMPERATURES),
              "max_absolute_relative_error":max(abs(r["relative_error"]) for r in rows
                                               if r["profile"]==p[0])}
         for p in PROFILES}
print(json.dumps({"profiles":summary,
    "max_log_fugacity_residual":max(r["log_fugacity_residual"] for r in rows),
    "max_relative_EOS_pressure_error":max(r["relative_EOS_pressure_error"] for r in rows),
    "max_relative_volume_refinement_error":max(r["relative_volume_refinement_error"] for r in rows),
    "reference_is_measurement":False,
    "reference_max_abserror_percentage_as_declared":REF["max_abserror_percentage"],
    "native_NeqSim_run":bool(args.native),
    "native":native_summary,
    "source_author_input_reproduction":False,
    "results_SHA256":hashlib.sha256(args.output.read_bytes()).hexdigest()
},indent=2))
