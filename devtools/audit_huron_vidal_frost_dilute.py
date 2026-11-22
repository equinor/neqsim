"""Check the staged PR-HV aqueous methane behavior in the dilute and low-pressure limits."""

import argparse
import csv
import hashlib
import json
import math
import sys
from decimal import Decimal
from pathlib import Path
from statistics import mean

root = (
    Path(__file__).resolve().parents[1] / "src/test/resources/neqsim/thermo/mixingrule"
)
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument(
    "source", type=Path, help="NIST ThermoML JSON for DOI 10.1021/je9007958"
)
args = parser.parse_args()
source = args.source.read_bytes()
if (
    hashlib.sha256(source).hexdigest()
    != "43a0ca9a752b967cc8239277d823f0971e14e191690ab38f102d755a404f5a3a"
):
    raise ValueError("Unexpected ThermoML source bytes")
sys.path.insert(0, str(root / "hv-meg-methane"))
import binary_solver as model

model.EOS = "PR"
model.PURE = [("647.30", "220.89", ".344"), ("190.56", "45.99", ".0115")]
model.TUPLE = ("-1500", "11", "4050", "-5.2", ".16")
step = Decimal("1e-10")


def iapws(t):
    """Return water saturation pressure and methane Henry constant in bar using G7-04."""
    tr = t / 647.096
    tau = 1 - tr
    coefs = (-7.85951783, 1.84408259, -11.7866497, 22.6807411, -15.9618719, 1.80122502)
    ps = 220.64 * math.exp(
        sum(a * tau**b for a, b in zip(coefs, (1, 1.5, 3, 3.5, 4, 7.5))) / tr
    )
    return ps, ps * math.exp(
        (-10.44708 + 4.66491 * tau**0.355) / tr + 12.12986 * tr**-0.41 * math.exp(tau)
    )


for t, want in [(300, 1.4034), (400, 1.7946), (500, 1.0342), (600, -0.2209)]:
    if abs(math.log(iapws(t)[1] / 1e4) - want) >= 5e-5:
        raise ValueError("IAPWS Table 6 check failed")
print("IAPWS Table 6 self-check passed")
for t in (283.89, 298.31, 313.11, 323.56, 373.15, 423.15):
    p, reference = iapws(t)
    h = []
    for dilute in (1e-5, 1e-6, 1e-7):
        phi, _, _ = model.phi([1 - dilute, dilute], t, p, False, step)
        h.append(p * math.exp(phi[1]))
    if abs(h[-1] / h[-2] - 1) >= 1e-4:
        raise ValueError(f"Dilute limit did not converge at {t} K")
    print("Henry", t, p, reference, h[-1], h[-1] / reference)
with (root / "hv-frost/observations-and-reference.csv").open() as f:
    rows = [r for r in csv.DictReader(f) if r["profile"] == "water_journal"]
for t in sorted({float(r["T_K"]) for r in rows}):
    values = []
    for r in rows:
        if float(r["T_K"]) != t:
            continue
        p, x, y = map(
            float, (r["P_bara"], r["observed_liquid_x_CH4"], r["observed_gas_y_polar"])
        )
        g, _, _ = model.phi([y, 1 - y], t, p, True, step)
        l, _, _ = model.phi([1 - x, x], t, p, False, step)
        g2, _, _ = model.phi([y, 1 - y], t, p, True, step / 2)
        l2, _, _ = model.phi([1 - x, x], t, p, False, step / 2)
        if max(abs(a - b) for a, b in zip(g + l, g2 + l2)) >= 1e-8:
            raise ValueError(
                f"Measured-state fugacity did not converge at {t} K and {p} bar"
            )
        values.append(
            [math.log(y / (1 - x)) + g[0] - l[0], math.log((1 - y) / x) + g[1] - l[1]]
        )
    print(
        "Measured endpoint log fugacity gas minus liquid",
        t,
        [(min(c), max(c)) for c in zip(*values)],
    )

dataset = json.loads(source)["PureOrMixtureData"][0]
if [x["RegNum"]["nOrgNum"] for x in dataset["Component"]] != [1, 3] or len(
    dataset["NumValues"]
) != 18:
    raise ValueError("Unexpected methane-water dataset")

results = []
for row in dataset["NumValues"]:
    coordinates = {v["nVarNumber"]: v["nVarValue"] for v in row["VariableValue"]}
    t, p = coordinates[1], coordinates[2] / 100
    prop = row["PropertyValue"][0]
    x = prop["nPropValue"]
    u = prop["CombinedUncertainty"]["nCombExpandUncertValue"]
    first, error1, _ = model.solve(t, p, step)
    second, error2, _ = model.solve(t, p, step / 2)
    if (
        max(abs(a / b - 1) for a, b in zip(first[:4], second[:4])) >= 1e-8
        or first[2] <= first[3]
    ):
        raise ValueError(f"Low-pressure endpoint did not converge at {t} K and {p} bar")
    results.append((t, p, x, u, first[1], first[0], max(error1, error2)))
print("low P residual", max(r[-1] for r in results))
print(
    "low P AARD",
    mean(abs(r[4] / r[2] - 1) for r in results),
    "ratio",
    min(r[4] / r[2] for r in results),
    max(r[4] / r[2] for r in results),
    "U95",
    sum(abs(r[4] - r[2]) <= r[3] for r in results),
)
print("ranges", [(min(c), max(c)) for c in zip(*results)])
for row in results:
    print("low P row", *row)
