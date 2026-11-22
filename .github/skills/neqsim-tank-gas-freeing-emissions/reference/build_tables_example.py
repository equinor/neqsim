"""Precompute NeqSim tables for the gas-freeing model.

For each crude basis (base = Njord A export oil S137 case A; volatile = light ends scaled so that TVP(40 C) = 1.5 bara,
the upper TVP figure quoted in the Njord B cargo system description 18-1B-BE-C15-00007, Table 6-8) and each
weathering pressure P_w (the residual/heel liquid is the liquid left after the storage-temperature flash of the crude at P_w),
store the heel liquid composition x0 and the effective saturation pressures Psat_eff_i(T) over a temperature grid.
"""
import json
import os

import numpy as np

import njb_lib as L

OUT = os.path.join(L.DATA, "psat_tables.json")
T_STORE = 45.0
PW_GRID = [0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0, 1.1, 1.2]
T_GRID = [0.0, 5.0, 10.0, 15.0, 20.0, 25.0, 30.0, 35.0, 40.0, 45.0, 50.0]
LIGHT = ["C1", "C2", "C3", "iC4", "nC4", "iC5", "nC5"]


def volatile_basis(z, tvp_target=1.5, T=40.0):
    def scaled(f):
        w = dict(z)
        for n in LIGHT:
            w[n] = z[n] * f
        s = sum(w.values())
        return {k: v / s for k, v in w.items()}

    lo, hi = 1.0, 4.0
    for _ in range(30):
        mid = 0.5 * (lo + hi)
        if L.bubble_pressure(scaled(mid), T) < tvp_target:
            lo = mid
        else:
            hi = mid
    f = 0.5 * (lo + hi)
    return scaled(f), f


bases = {}
z0 = L.s137_composition("A")
zv, fv = volatile_basis(z0)
bases["base"] = z0
bases["volatile"] = zv
tables = {"T_store_C": T_STORE, "pw_grid": PW_GRID, "T_grid": T_GRID, "names": L.NAMES, "bases": {}}
for bname, z in bases.items():
    entry = {"feed": z, "tvp_C": {str(T): L.bubble_pressure(z, T) for T in (10, 20, 37.8, 40, 45, 47, 50)}, "pw": {}}
    if bname == "volatile":
        entry["light_end_scale_factor"] = fv
    for pw in PW_GRID:
        r = L.tp_flash(z, T_STORE, pw)
        x = r["x"] if r["x"] else z
        ps = {}
        pbs = {}
        for T in T_GRID:
            p, pb = L.psat_eff(x, T)
            ps[str(T)] = p
            pbs[str(T)] = pb
        entry["pw"][str(pw)] = {"beta_flash": r["beta"], "x0": x, "psat_eff": ps, "tvp": pbs}
    tables["bases"][bname] = entry
    print(bname, "TVP", {k: round(v, 3) for k, v in entry["tvp_C"].items()})
with open(OUT, "w", encoding="utf-8") as fh:
    json.dump(tables, fh)
print("written", OUT)
