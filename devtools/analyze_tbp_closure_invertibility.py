"""Determine which closure directions are numerically sound, and derive PNA Watson-K defaults.

Two questions have to be answered with numbers before the named ``addTBPfraction_*``
overloads can be written:

1. Which closures can be inverted for specific gravity given (Tb, M)? A correlation
   that is non-monotonic in SG over the range of interest has no unique inverse, and
   an overload that pretends otherwise would silently return one of several roots.

2. What Watson K should represent a pure paraffin, naphthene and aromatic in the
   PNA-to-Kw blend? These are derived from COMP.csv rather than quoted from memory,
   so the defaults are reproducible from data shipped with the code.

Usage:
    python devtools/analyze_tbp_closure_invertibility.py
"""

from __future__ import annotations

import csv
import math
from pathlib import Path

COMP_CSV = Path(__file__).resolve().parents[1] / "src" / "main" / "resources" / "data" / "COMP.csv"
PLACEHOLDER_NORMBOIL_C = 58.05

# Representative members of each hydrocarbon family present in COMP.csv.
PNA_FAMILIES = {
    "paraffin": [
        "n-pentane",
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
    "naphthene": ["c-hexane", "c-C5", "c-pentane", "M-cyclopentane", "M-cyclohexane", "E-cy-C5"],
    "aromatic": ["benzene", "toluene", "m-Xylene", "o-Xylene", "p-Xylene", "ethylbenzene"],
}


def molar_mass_rd1987(tb_k: float, sg: float) -> float:
    return (
        42.965
        * math.exp(2.097e-4 * tb_k - 7.78712 * sg + 2.08476e-3 * tb_k * sg)
        * tb_k**1.26007
        * sg**4.98308
    )


def molar_mass_rd1980(tb_k: float, sg: float) -> float:
    tb_r = tb_k * 1.8
    return 4.5673e-5 * tb_r**2.1962 * sg**-1.0164


def boiling_point_soreide(molar_mass: float, sg: float) -> float:
    tb_r = 1928.3 - 1.695e5 * molar_mass**-0.03522 * sg**3.266 * math.exp(
        -4.922e-3 * molar_mass - 4.7685 * sg + 3.462e-3 * molar_mass * sg
    )
    return tb_r / 1.8


def report_sg_monotonicity() -> None:
    """A closure is invertible for SG only if M varies monotonically with SG at fixed Tb."""
    print("=" * 78)
    print("Is molar mass monotonic in specific gravity at fixed boiling point?")
    print("(needed to invert a closure for SG, i.e. for the _Mw_Tb overload)")
    print("=" * 78)

    sg_grid = [0.60 + 0.02 * i for i in range(21)]  # 0.60 .. 1.00
    for label, fn in (("RD-1987", molar_mass_rd1987), ("RD-1980", molar_mass_rd1980)):
        print(f"\n{label}: dM/dSG sign across SG = 0.60..1.00")
        print(f"{'Tb/K':>8}{'signs':>26}{'verdict':>22}")
        for tb in (350.0, 400.0, 450.0, 500.0, 550.0, 600.0):
            values = [fn(tb, sg) for sg in sg_grid]
            diffs = [b - a for a, b in zip(values, values[1:])]
            positives = sum(1 for d in diffs if d > 0)
            negatives = sum(1 for d in diffs if d < 0)
            if positives and negatives:
                verdict = "NOT monotonic"
            else:
                verdict = "monotonic"
            print(f"{tb:>8.0f}{f'+{positives} / -{negatives}':>26}{verdict:>22}")

    print("\nSoreide: dTb/dSG sign across SG = 0.60..1.00 at fixed molar mass")
    print(f"{'M g/mol':>8}{'signs':>26}{'verdict':>22}")
    for molar_mass in (80.0, 120.0, 160.0, 200.0, 260.0, 320.0):
        values = [boiling_point_soreide(molar_mass, sg) for sg in sg_grid]
        diffs = [b - a for a, b in zip(values, values[1:])]
        positives = sum(1 for d in diffs if d > 0)
        negatives = sum(1 for d in diffs if d < 0)
        verdict = "NOT monotonic" if positives and negatives else "monotonic"
        print(f"{molar_mass:>8.0f}{f'+{positives} / -{negatives}':>26}{verdict:>22}")


def report_pna_watson_k() -> None:
    """Derive the Watson K of a pure paraffin, naphthene and aromatic from COMP.csv."""
    print("\n" + "=" * 78)
    print("Watson K by hydrocarbon family, computed from COMP.csv")
    print("Kw = (1.8 * Tb[K])^(1/3) / SG")
    print("=" * 78)

    rows = {}
    with COMP_CSV.open(newline="", encoding="utf-8") as handle:
        for row in csv.DictReader(handle):
            rows[row["NAME"]] = row

    family_values = {}
    for family, names in PNA_FAMILIES.items():
        print(f"\n{family}")
        values = []
        for name in names:
            row = rows.get(name)
            if row is None:
                continue
            tb_c = float(row["NORMBOIL"])
            sg = float(row["LIQDENS"])
            if abs(tb_c - PLACEHOLDER_NORMBOIL_C) < 1e-9:
                print(f"  {name:<16} skipped (placeholder boiling point)")
                continue
            watson_k = (1.8 * (tb_c + 273.15)) ** (1.0 / 3.0) / sg
            # An SG far from the family norm means the row itself is suspect.
            print(f"  {name:<16} Tb={tb_c + 273.15:7.1f} K  SG={sg:.3f}  Kw={watson_k:6.3f}")
            values.append((name, watson_k, sg))
        family_values[family] = values

    print("\nFamily summary (median is used to reject rows with a corrupt density):")
    print(f"{'family':<12}{'n':>4}{'min':>9}{'median':>9}{'max':>9}")
    for family, values in family_values.items():
        ks = sorted(v for _, v, _ in values)
        if not ks:
            continue
        median = ks[len(ks) // 2] if len(ks) % 2 else 0.5 * (ks[len(ks) // 2 - 1] + ks[len(ks) // 2])
        print(f"{family:<12}{len(ks):>4}{ks[0]:>9.3f}{median:>9.3f}{ks[-1]:>9.3f}")

    print("\nOutliers worth a second look (Kw more than 0.6 from the family median):")
    flagged = False
    for family, values in family_values.items():
        ks = sorted(v for _, v, _ in values)
        if not ks:
            continue
        median = ks[len(ks) // 2] if len(ks) % 2 else 0.5 * (ks[len(ks) // 2 - 1] + ks[len(ks) // 2])
        for name, watson_k, sg in values:
            if abs(watson_k - median) > 0.6:
                print(f"  {family:<11}{name:<16} Kw={watson_k:6.3f} vs median {median:6.3f}  (SG={sg:.3f})")
                flagged = True
    if not flagged:
        print("  none")


if __name__ == "__main__":
    report_sg_monotonicity()
    report_pna_watson_k()
