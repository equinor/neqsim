"""Audit of Frost thesis data using NeqSim's retained independent binary solver."""

import argparse
import csv
import hashlib
import json
import math
import re
import subprocess
import sys
from decimal import Decimal
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FIXTURE = ROOT / "src/test/resources/neqsim/thermo/mixingrule"
sys.path.insert(0, str(FIXTURE / "hv-meg-methane"))
import binary_solver as solver

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("source_directory", type=Path)
parser.add_argument("output", type=Path)
args = parser.parse_args()
source = args.source_directory
expected = {
    "frost.pdf": "5fb799e334e4b7d7c08428c9a4dc311ceac23f3a16c45249ef717a6fe340d802",
    "frost.json": "a78159d0ab6cfbadebb4028c590dffff5ccdf0acba9e4938bbcbc62379b9fe4a",
}
for name, digest in expected.items():
    if hashlib.sha256((source / name).read_bytes()).hexdigest() != digest:
        raise ValueError(f"Unexpected source bytes: {name}")
thesis = subprocess.check_output(
    ["pdftotext", "-layout", str(source / "frost.pdf"), "-"], text=True
)
thermoml = json.loads((source / "frost.json").read_text())
if args.output.resolve().is_relative_to(FIXTURE) or args.output.resolve() in {
    (source / name).resolve() for name in expected
}:
    raise ValueError("Output must be outside the fixture directory")
step = Decimal("1e-10")


def table(number, count):
    begin = thesis.index(f"Table 2.{number}: Results obtained in the analytical study")
    rows = re.findall(
        r"^\s*(\d{3}) +([\d.]+) +([\d.]+) +([\d.]+|---) *$",
        thesis[begin : begin + 2600],
        re.M,
    )
    if len(rows) != count:
        raise ValueError((number, rows))
    return [tuple(Decimal(v) if v != "---" else None for v in r) for r in rows]


def use_profile(profile):
    solver.EOS = "SRK" if profile.startswith("meg") else "PR"
    methane = ("190.56", "45.99", ".0115")
    if solver.EOS == "PR":
        solver.PURE = [("647.30", "220.89", ".344"), methane]
        solver.TUPLE = ("-1500", "11", "4050", "-5.2", ".16")
        solver.MC = [None, None]
    else:
        solver.PURE = [("720", "82", ".5347"), methane]
        solver.TUPLE = ("181", "0", "2274", "0", ".07")
        solver.MC = [
            ("1.1121", ".9679", "-1.42") if profile == "meg_mc" else None,
            None,
        ]


def solve(temp, p):
    first, residual, _ = solver.solve(temp, p, step)
    second, other_residual, _ = solver.solve(temp, p, step / 2)
    if not all(math.isfinite(v) for v in (*first, *second, residual, other_residual)):
        raise ValueError("nonfinite endpoint")
    if max(abs(a / b - 1) for a, b in zip(first[:4], second[:4])) >= 1e-8:
        raise ValueError("step parity")
    if first[2] <= first[3] or max(residual, other_residual) >= 1e-10:
        raise ValueError("invalid endpoint")
    return {
        "pred_y": first[0],
        "pred_x": first[1],
        "gas_z": first[2],
        "liquid_z": first[3],
        "log_residual": max(residual, other_residual),
        "pressure_residual_bar": max(first[4], second[4]),
    }


def pure_water(temp):
    theta = 1 - temp / 647.096
    coefs = (-7.85951783, 1.84408259, -11.7866497, 22.6807411, -15.9618719, 1.80122502)
    iapws = 220.64 * math.exp(
        647.096
        / temp
        * sum(c * theta**e for c, e in zip(coefs, (1, 1.5, 3, 3.5, 4, 7.5)))
    )
    lo, hi = iapws * 0.2, iapws * 3

    def r(p):
        g, zg, _ = solver.phi([1.0, 0.0], temp, p, True, step)
        l, zl, _ = solver.phi([1.0, 0.0], temp, p, False, step)
        if zg <= zl:
            raise ValueError("Pure water roots collapsed")
        return g[0] - l[0]

    if not r(lo) < 0 < r(hi):
        raise ValueError("Pure water bracket")
    for _ in range(45):
        mid = (lo + hi) / 2
        if r(mid) < 0:
            lo = mid
        else:
            hi = mid
    sat = (lo + hi) / 2
    return {
        "temperature_K": temp,
        "p_ref_bara": iapws,
        "p_PR_bara": sat,
        "error_percent": 100 * (sat / iapws - 1),
        "log_residual": abs(r(sat)),
    }


out = {}
for profile in ["meg_mc", "meg_soave", "water_pr"]:
    use_profile(profile)
    meg = profile.startswith("meg")
    denom = Decimal(10) ** (6 if meg else 3)
    out[profile] = []
    for temp, p, x, y in table(9 if meg else 6, 11 if meg else 23):
        data = dict(
            temperature_K=float(temp),
            pressure_bara=float(p * 10),
            obs_x=float(x / denom),
            obs_y=None if y is None else float(y / denom),
        )
        data.update(solve(float(temp), float(p * 10)))
        out[profile].append(data)

use_profile("water_pr")
p_rows, y_rows = [p["NumValues"] for p in thermoml["PureOrMixtureData"][:2]]


def key(row):
    return tuple(
        v["nVarValue"]
        for v in sorted(row["VariableValue"], key=lambda v: v["nVarNumber"])
    )


def value(row):
    v = row["PropertyValue"][0]
    return v["nPropValue"], v["CombinedUncertainty"]["nCombExpandUncertValue"]


if len(p_rows) != 22 or len(y_rows) != 23 or len({key(r) for r in y_rows}) != 23:
    raise ValueError("Unexpected Frost ThermoML dataset shape")
by_y = {key(r): value(r) for r in y_rows}
out["water_journal"] = []
for row in p_rows:
    x, t = key(row)
    p, up = value(row)
    y, uy = by_y[x, t]
    data = dict(
        temperature_K=t,
        pressure_bara=p / 100,
        obs_x=x,
        obs_y=y,
        compiler_u95_p_bar=up / 100,
        compiler_u95_y=uy,
    )
    data.update(solve(t, p / 100))
    out["water_journal"].append(data)
out["water_pure"] = [
    pure_water(t)
    for t in sorted(
        {r["temperature_K"] for r in out["water_pr"] + out["water_journal"]}
    )
]
for name, rows in out.items():
    if name == "water_pure":
        print(name, [(r["temperature_K"], round(r["error_percent"], 4)) for r in rows])
        continue
    for t in [None, *sorted(set(r["temperature_K"] for r in rows))]:
        group = [r for r in rows if t is None or r["temperature_K"] == t]
        print(name, t, len(group), end=" ")
        for prop in ("x", "y"):
            errors = [
                100 * (r["pred_" + prop] / r["obs_" + prop] - 1)
                for r in group
                if r["obs_" + prop] is not None
            ]
            print(
                prop,
                len(errors),
                [
                    round(v, 5)
                    for v in (
                        sum(map(abs, errors)) / len(errors),
                        min(errors),
                        max(errors),
                    )
                ],
                end=" ",
            )
        print()
    print("max log residual", max(r["log_residual"] for r in rows))
    if name == "water_journal":
        print(
            "inside compiler y U95",
            sum(abs(r["pred_y"] - r["obs_y"]) <= r["compiler_u95_y"] for r in rows),
        )

# Sensitivity only: match IAPWS pure water pressure at each coordinate using one
# local alpha value. Keep all mixture coefficients and methane inputs frozen.
# This temperature-local control is not an alpha correlation or candidate profile.
local_water = []
for row in out["water_journal"]:
    temp, p = row["temperature_K"], row["pressure_bara"]
    pure = next(r for r in out["water_pure"] if r["temperature_K"] == temp)
    pref = pure["p_ref_bara"]
    lo, hi = Decimal("0.4"), Decimal("1.2")

    def match(m):
        solver.MC = [(str(m), "0", "0"), None]
        g, zg, _ = solver.phi([1.0, 0.0], temp, pref, True, step)
        l, zl, _ = solver.phi([1.0, 0.0], temp, pref, False, step)
        if zg <= zl:
            raise ValueError("Pure-water roots collapsed")
        return g[0] - l[0]

    if not match(lo) < 0 < match(hi):
        raise ValueError("Local water control bracket")
    for _ in range(45):
        mid = (lo + hi) / 2
        if match(mid) < 0:
            lo = mid
        else:
            hi = mid
    m = (lo + hi) / 2
    residual = abs(match(m))
    result = {
        **row,
        **solve(temp, p),
        "water_alpha_local_m": float(m),
        "pure_water_log_residual": residual,
    }
    local_water.append(result)
out["water_journal_pure_pressure_control"] = local_water
for prop in ("x", "y"):
    print(
        "Water pure-pressure control",
        prop,
        sum(100 * abs(r["pred_" + prop] / r["obs_" + prop] - 1) for r in local_water)
        / len(local_water),
    )
print("Max pure closure", max(r["pure_water_log_residual"] for r in local_water))
print(
    "Max pressure residual",
    max(
        r["pressure_residual_bar"]
        for k, rs in out.items()
        if k != "water_pure"
        for r in rs
    ),
)

use_profile("water_pr")
print("Compiler-coordinate-only gas row:", sorted(set(by_y) - {key(r) for r in p_rows}))
print(
    "323.56 K rows: liquid methane x and water vapor y comparison across compiler P intervals"
)
out["water_journal_pressure_sensitivity"] = []
for row in out["water_journal"]:
    if row["temperature_K"] != 323.56:
        continue
    t, p, u = row["temperature_K"], row["pressure_bara"], row["compiler_u95_p_bar"]
    for coordinate in (p - u, p + u):
        endpoint = solve(t, coordinate)
        out["water_journal_pressure_sensitivity"].append(
            {**row, "test_pressure_bara": coordinate, **endpoint}
        )
        print(
            p,
            coordinate,
            endpoint["pred_x"] / row["obs_x"],
            endpoint["pred_y"] / row["obs_y"],
        )
args.output.write_text(json.dumps(out, indent=2, allow_nan=False) + "\n")

# The Java fixture is an exact projection of the four assessed source/profile series.
with (FIXTURE / "hv-frost/observations-and-reference.csv").open(newline="") as file:
    retained = list(csv.DictReader(file))
columns = (
    ("T_K", "temperature_K"),
    ("P_bara", "pressure_bara"),
    ("observed_liquid_x_CH4", "obs_x"),
    ("observed_gas_y_polar", "obs_y"),
    ("compiler_U95_P_bara", "compiler_u95_p_bar"),
    ("compiler_U95_y_polar", "compiler_u95_y"),
    ("independent_x_CH4", "pred_x"),
    ("independent_y_polar", "pred_y"),
)
expected_rows = [
    (profile, row)
    for profile in ("water_journal", "water_pr", "meg_mc", "meg_soave")
    for row in out[profile]
]
if len(retained) != len(expected_rows):
    raise ValueError("Unexpected native fixture row count")
for saved, (profile, result) in zip(retained, expected_rows):
    if saved["profile"] != profile:
        raise ValueError("Native fixture source version or order changed")
    for column, key in columns:
        value = None if not saved[column] else float(saved[column])
        target = result.get(key)
        if (
            value is None
            or target is None
            or column.startswith(("observed_", "compiler_"))
        ):
            matches = value == target
        else:
            matches = math.isfinite(value) and math.isclose(
                value, target, rel_tol=5e-10
            )
        if not matches:
            raise ValueError(f"Native fixture mismatch in {profile} {column}")
args.output.write_text(json.dumps(out, indent=2, allow_nan=False) + "\n")
