"""Replay acceptance checks on the stored results, without a JVM."""
import csv
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2] / "target" / "stabilization-commissioning"
def read(name):
    return json.loads((ROOT/(name+".json")).read_text())
def trace(name):
    with (ROOT/(name+".csv")).open() as f:
        return [{k:float(v) for k,v in row.items()} for row in csv.DictReader(f)]

base = read("steady")
low = read("steady_turndown")
cooler = read("steady_cooler_heater")
for case,expected in [(base,6),(low,9),(cooler,6)]:
    assert len(case["recycles"]) == expected
    assert all(r["solved"] and r["kg_h"]>0 for r in case["recycles"].values())
    assert abs(case["mass_error_kg_h"])/case["streams"]["feed"]["kg_h"] < 4.e-6
    assert case["max_component_residual_mol_s"] < .001
    assert abs(case["energy_error_kW"]) < .05
    assert all(s.startswith(("water","suction_water","cond_water")) or s in ("oil_export","gas_export") for s in case["sinks"])
assert base["oil_bubble_bara_37_8C"] < cooler["oil_bubble_bara_37_8C"]
for i in range(201,204):
    name="suction"+str(i)
    relative=low["streams"][name]["actual_m3_h"]/base["streams"][name]["actual_m3_h"]
    assert abs(relative/(.76*1.12)-1.) < .00081
summary=read("summary")
assert min(summary["turndown"]["min_margin"]) > 0
assert max(summary["no_asc"]["min_margin"]) < 0
for test in ["baseline","feed_step","turndown","no_asc","single_channel"]:
    assert not summary[test]["events"]
for test,tag in [("blocked_gas","PSHH-101"),("blocked_mp","PSHH-102"),("blocked_lp","PSHH-103"),
                 ("scrubber_slug","LSHH-201"),("vibration_test","VSHH-203"),
                 ("temperature_test","TSHH-203"),("lube_test","PSLL-203L")]:
    events=summary[test]["events"]
    assert len(events)==1 and events[0][1]==tag
    d=trace(test)
    if test.endswith("_test"):
        assert events[0][0]==31.
    post=[r for r in d if r['t_s'] >= events[0][0]+4.]
    assert post and all(r["trip"] and r["inlet_sdv_pct"]==0 and r["export_sdv_pct"]==0 and r["transfer_sdv_pct"]==0 for r in post)
    assert all(all(r[f"ASV{i}_pct"]>=99.9 for i in (201,202,203)) for r in post)
assert max(r['votes'] for r in trace("single_channel")) == 1
assert max(r['mass_error_kg'] for r in summary.values() if 'mass_error_kg' in r)<1.e-6
for case in read("matched-v101"):
    assert case["max_mass_difference_kg"] < 1e-5
    assert 0 < case["native_trip_after_block_s"] < 60
    assert 0 < case["frozen_trip_after_block_s"] < 60
flare = read("flare")
assert flare["peak_total_kg_s"] > 0 and flare["peak_header_mach"] > 0
assert read("drawing-intake")["schema_and_profile_conformant"]
assert all(abs(x-y)<.2 for x,y in zip(summary["refinement"]["min_margin"],summary["turndown"]["min_margin"]))
print("PASS: three steady cases, nine active recycles at turndown, balances, all dynamic cases, first-outs, actuator travel, native V-101 replay and timestep refinement.")
