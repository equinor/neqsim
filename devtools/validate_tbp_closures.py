"""Validate candidate Tb <-> molar mass <-> specific gravity closure correlations.

The named ``addTBPfraction_*`` overloads need a closure that turns the two supplied
properties into the third. This script checks each candidate correlation against the
pure-component data already in ``COMP.csv``: for components whose molar mass, normal
boiling point and liquid density are all known, it predicts molar mass from
(Tb, SG) and reports the deviation.

A correlation whose published coefficients have been transcribed correctly reproduces
paraffins to a few percent. A transcription error shows up as a deviation of tens of
percent or worse, so this doubles as a guard against mis-remembered constants.

Usage:
    python devtools/validate_tbp_closures.py
"""

from __future__ import annotations

import csv
import math
from pathlib import Path

COMP_CSV = Path(__file__).resolve().parents[1] / "src" / "main" / "resources" / "data" / "COMP.csv"

# Components with well-established Tb/SG/M used as the validation set. Grouped by
# family so that a correlation biased towards paraffins is visible.
VALIDATION_SET = {
    "paraffin": [
        "n-hexane",
        "n-heptane",
        "n-octane",
        "n-nonane",
        "nC10",
        "nC11",
        "nC12",
        "nC13",
        "nC14",
        "nC15",
        "nC16",
    ],
    "naphthene": ["c-hexane", "c-pentane", "c-heptane"],
    "aromatic": ["benzene", "toluene", "m-Xylene", "o-Xylene", "p-Xylene", "ethylbenzene"],
}

# NORMBOIL is stored in degrees Celsius. A number of rows carry this value as a
# placeholder rather than a measurement; they must not enter a correlation check.
PLACEHOLDER_NORMBOIL_C = 58.05


def molar_mass_rd1980(tb_k: float, sg: float) -> float:
    """Riazi-Daubert (1980), M = 4.5673e-5 * Tb^2.1962 * SG^-1.0164, Tb in degrees Rankine."""
    tb_r = tb_k * 1.8
    return 4.5673e-5 * tb_r**2.1962 * sg**-1.0164


def molar_mass_rd1987(tb_k: float, sg: float) -> float:
    """Riazi-Daubert (1987) extended form, Tb in K.

    M = 42.965 * exp(2.097e-4*Tb - 7.78712*SG + 2.08476e-3*Tb*SG) * Tb^1.26007 * SG^4.98308
    """
    return (
        42.965
        * math.exp(2.097e-4 * tb_k - 7.78712 * sg + 2.08476e-3 * tb_k * sg)
        * tb_k**1.26007
        * sg**4.98308
    )


def molar_mass_neqsim_k_form(tb_k: float, sg: float) -> float:
    """The Riazi-Daubert style form already used by TBPfractionModel.calcTB, Tb in K.

    Tb = (M / 5.805e-5 * SG^0.9371)^(1/2.3776)  ->  M = 5.805e-5 * Tb^2.3776 * SG^-0.9371
    """
    return 5.805e-5 * tb_k**2.3776 * sg**-0.9371


def boiling_point_soreide(molar_mass: float, sg: float) -> float:
    """Soreide (1989) as implemented in PedersenTBPModelPR2.calcTB. Returns Tb in K."""
    tb_r = 1928.3 - 1.695e5 * molar_mass**-0.03522 * sg**3.266 * math.exp(
        -4.922e-3 * molar_mass - 4.7685 * sg + 3.462e-3 * molar_mass * sg
    )
    return tb_r / 1.8


def molar_mass_soreide(tb_k: float, sg: float) -> float:
    """Invert the Soreide Tb correlation for molar mass by bisection."""
    lo, hi = 10.0, 800.0
    f_lo = boiling_point_soreide(lo, sg) - tb_k
    f_hi = boiling_point_soreide(hi, sg) - tb_k
    if f_lo * f_hi > 0.0:
        return float("nan")
    for _ in range(200):
        mid = 0.5 * (lo + hi)
        f_mid = boiling_point_soreide(mid, sg) - tb_k
        if f_lo * f_mid <= 0.0:
            hi, f_hi = mid, f_mid
        else:
            lo, f_lo = mid, f_mid
    return 0.5 * (lo + hi)


CORRELATIONS = {
    "RD-1980": molar_mass_rd1980,
    "RD-1987": molar_mass_rd1987,
    "NeqSim K-form": molar_mass_neqsim_k_form,
    "Soreide (inverted)": molar_mass_soreide,
}


def load_components() -> dict:
    rows = {}
    with COMP_CSV.open(newline="", encoding="utf-8") as handle:
        for row in csv.DictReader(handle):
            rows[row["NAME"]] = row
    return rows


def main() -> None:
    rows = load_components()
    print(f"COMP.csv rows: {len(rows)}\n")

    # Establish the unit convention of NORMBOIL and LIQDENS from a component whose
    # values are beyond doubt, rather than assuming them.
    probe = rows["n-heptane"]
    print("Unit probe on n-heptane (accepted: M 100.2 g/mol, Tb 371.6 K, SG 0.684):")
    print(f"  MOLARMASS = {probe['MOLARMASS']}")
    print(f"  NORMBOIL  = {probe['NORMBOIL']}")
    print(f"  LIQDENS   = {probe['LIQDENS']}\n")

    results = {name: [] for name in CORRELATIONS}
    print(f"{'component':<16}{'family':<11}{'Tb/K':>8}{'SG':>8}{'M':>8}" + "".join(f"{n:>20}" for n in CORRELATIONS))

    for family, names in VALIDATION_SET.items():
        for name in names:
            row = rows.get(name)
            if row is None:
                print(f"{name:<16}{family:<11}  -- not found in COMP.csv --")
                continue
            molar_mass = float(row["MOLARMASS"])
            tb_c = float(row["NORMBOIL"])
            sg = float(row["LIQDENS"])
            if abs(tb_c - PLACEHOLDER_NORMBOIL_C) < 1e-9:
                print(f"{name:<16}{family:<11}  -- NORMBOIL is the {PLACEHOLDER_NORMBOIL_C} placeholder, skipped --")
                continue
            tb = tb_c + 273.15
            if sg > 10.0:
                sg /= 1000.0

            cells = ""
            for corr_name, fn in CORRELATIONS.items():
                predicted = fn(tb, sg)
                dev = 100.0 * (predicted - molar_mass) / molar_mass
                results[corr_name].append((name, family, dev))
                cells += f"{predicted:>11.1f} ({dev:+5.1f}%)"
            print(f"{name:<16}{family:<11}{tb:>8.1f}{sg:>8.3f}{molar_mass:>8.1f}{cells}")

    print("\nAbsolute average deviation in molar mass:")
    print(f"{'correlation':<22}{'all':>10}{'paraffin':>12}{'naphthene':>12}{'aromatic':>12}")
    for corr_name, entries in results.items():
        if not entries:
            continue

        def aad(family=None, entries=entries):
            vals = [abs(d) for _, f, d in entries if family is None or f == family]
            return sum(vals) / len(vals) if vals else float("nan")

        print(
            f"{corr_name:<22}{aad():>9.1f}%{aad('paraffin'):>11.1f}%"
            f"{aad('naphthene'):>11.1f}%{aad('aromatic'):>11.1f}%"
        )

    print(
        "\nA correlation with correctly transcribed coefficients should land in the\n"
        "single-digit percent range on paraffins. Anything far larger indicates a\n"
        "wrong constant, not a limitation of the correlation."
    )


if __name__ == "__main__":
    main()
