"""Independent decimal-arithmetic audit of the small factual table-18 extract.

Run from any directory. No NeqSim, EOS fit or third-party Python dependency.
Bounds are printed-resolution half widths, not experimental uncertainty.
"""

import csv
import json
from decimal import Decimal, getcontext
from pathlib import Path

getcontext().prec = 50
D = Decimal
R = D("8.31446261815324")
PSI = D("6894.757293168")
T = D("379.15")
DP = D("0.5")
DT = D("0.5")
DZ = D("0.0005")
DM = D("0.005")
DRHO = D("0.0005")


def audit():
    """Return raw-unit independent interval and nominal diagnostics in kg/m3."""
    rows = []
    with Path(__file__).with_name("duvernay-table18.csv").open() as source:
        for row in csv.DictReader(source):
            p, z, m, rho = (D(row[key]) for key in (
                "pressurePsia", "gasZ", "molarMassGPerMol", "densityGPerCc"
            ))
            nominal = p * PSI * m / (z * R * T) / 1000
            lower = (p - DP) * PSI * (m - DM) / ((z + DZ) * R * (T + DT)) / 1000
            upper = (p + DP) * PSI * (m + DM) / ((z - DZ) * R * (T - DT)) / 1000
            compatible = (rho - DRHO) * 1000 <= upper and (rho + DRHO) * 1000 >= lower
            rows.append({
                "rowId": row["row"],
                "impliedDensityKgPerM3": float(nominal),
                "impliedLowerKgPerM3": float(lower),
                "impliedUpperKgPerM3": float(upper),
                "compatible": compatible,
            })
    return {"method": "50-digit Decimal; direct raw-unit interval arithmetic", "rows": rows}


if __name__ == "__main__":
    print(json.dumps(audit(), indent=2))
