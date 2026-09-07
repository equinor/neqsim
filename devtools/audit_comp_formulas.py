"""Audit the FORMULA column in COMP.csv against the CAS number on the same row.

The molecular formula is checked two ways, and both have to agree before anything is
proposed as a correction:

  1. CAS -> formula, from the curated identifier database in the `chemicals` package.
  2. The molar mass implied by that formula, against the MOLARMASS column already in
     COMP.csv.

The second check is what makes the first safe. If the stored formula is wrong but the
molar mass matches the CAS, the formula string is the error. If the molar mass does
*not* match either, the row has a deeper identity problem and is reported rather than
corrected, because it is then unclear which field is authoritative.

Rows whose FORMULA is a name rather than a formula (MEA, propyleneglycol, default)
are reported separately; they are a labelling choice, not an error.

Usage:
    python devtools/audit_comp_formulas.py               # report only
    python devtools/audit_comp_formulas.py --write       # apply the safe corrections
"""

from __future__ import annotations

import argparse
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
COMP_CSV = ROOT / "src" / "main" / "resources" / "data" / "COMP.csv"

# Tolerance on molar mass agreement, g/mol. Loose enough for isotope-averaged tables,
# tight enough that a wrong molecule cannot slip through.
MASS_TOLERANCE = 0.05

# Rows where the reference differs only by Hill notation, which orders carbon and
# hydrogen first and the rest alphabetically. COMP.csv uses conventional chemical
# notation, which is more readable for these, so the difference is a house style
# rather than an error and is left alone.
HILL_NOTATION_ONLY = frozenset(["NaCl", "ammonia", "NaNO2", "NH2SO3H", "NaHSO4"])

ATOMIC_MASSES = {
    "H": 1.00794, "He": 4.002602, "Li": 6.941, "B": 10.811, "C": 12.0107, "N": 14.0067,
    "O": 15.9994, "F": 18.9984032, "Ne": 20.1797, "Na": 22.98976928, "Mg": 24.305,
    "Al": 26.9815386, "Si": 28.0855, "P": 30.973762, "S": 32.065, "Cl": 35.453,
    "Ar": 39.948, "K": 39.0983, "Ca": 40.078, "Fe": 55.845, "Br": 79.904, "Kr": 83.798,
    "Sr": 87.62, "Ag": 107.8682, "I": 126.90447, "Xe": 131.293, "Ba": 137.327,
    "Hg": 200.59, "Pb": 207.2,
}

FORMULA_TOKEN = re.compile(r"([A-Z][a-z]?)(\d*)")


def molar_mass_of(formula: str):
    """Molar mass in g/mol for a plain element-count formula, or None if it cannot be parsed."""
    if not formula or not re.fullmatch(r"(?:[A-Z][a-z]?\d*)+", formula):
        return None
    total = 0.0
    for element, count in FORMULA_TOKEN.findall(formula):
        if element not in ATOMIC_MASSES:
            return None
        total += ATOMIC_MASSES[element] * (int(count) if count else 1)
    return total


def normalise(formula: str) -> str:
    return (formula or "").strip()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--write", action="store_true", help="apply the safe corrections")
    args = parser.parse_args()

    try:
        from chemicals.identifiers import search_chemical
    except ImportError:
        raise SystemExit("the 'chemicals' package is required for this audit")

    raw = COMP_CSV.read_bytes().decode("utf-8")
    lines = raw.split("\n")
    header = lines[0].split(",")
    name_col = header.index("NAME")
    cas_col = header.index("CASnumber")
    formula_col = header.index("FORMULA")
    mass_col = header.index("MOLARMASS")

    corrections = []
    conflicts = []
    labels = []
    unresolved = 0
    agreed = 0

    for index, line in enumerate(lines[1:], start=1):
        if not line.strip():
            continue
        fields = line.split(",")
        if len(fields) <= formula_col:
            continue
        name = fields[name_col].strip()
        cas = fields[cas_col].strip()
        stored_formula = normalise(fields[formula_col])
        try:
            stored_mass = float(fields[mass_col])
        except (ValueError, IndexError):
            continue

        if not cas or cas == "0-0-0-0":
            unresolved += 1
            continue
        try:
            reference = search_chemical(cas)
        except Exception:  # noqa: BLE001 - unresolvable CAS is an expected outcome
            unresolved += 1
            continue

        reference_formula = normalise(reference.formula)
        if stored_formula == reference_formula:
            agreed += 1
            continue

        if molar_mass_of(stored_formula) is None:
            labels.append((name, cas, stored_formula, reference_formula))
            continue

        reference_mass = molar_mass_of(reference_formula)
        if reference_mass is not None and abs(reference_mass - stored_mass) <= MASS_TOLERANCE:
            if name in HILL_NOTATION_ONLY:
                labels.append((name, cas, stored_formula, reference_formula + " (Hill notation only)"))
                continue
            # The molar mass backs the CAS, so the formula string is the odd one out.
            corrections.append((index, name, cas, stored_formula, reference_formula, stored_mass))
        else:
            conflicts.append(
                (name, cas, stored_formula, reference_formula, stored_mass, reference_mass)
            )

    print(f"rows agreeing with their CAS      : {agreed}")
    print(f"rows with an unresolvable CAS     : {unresolved}")
    print(f"rows whose FORMULA is a name      : {len(labels)}")
    print(f"rows safe to correct              : {len(corrections)}")
    print(f"rows in conflict, NOT corrected   : {len(conflicts)}")

    if corrections:
        print("\nSAFE CORRECTIONS (molar mass confirms the CAS, so the formula string is wrong)")
        print(f"  {'component':<20}{'CAS':<14}{'stored':<14}{'correct':<14}{'MOLARMASS':>10}")
        for _i, name, cas, stored, correct, mass in corrections:
            print(f"  {name:<20}{cas:<14}{stored:<14}{correct:<14}{mass:>10.4f}")

    if conflicts:
        print("\nCONFLICTS (molar mass does not match the CAS either - needs a human)")
        print(f"  {'component':<20}{'CAS':<14}{'stored':<12}{'CAS says':<12}{'MOLARMASS':>10}{'implied':>10}")
        for name, cas, stored, correct, mass, ref_mass in conflicts:
            shown = "n/a" if ref_mass is None else f"{ref_mass:.4f}"
            print(f"  {name:<20}{cas:<14}{stored:<12}{correct:<12}{mass:>10.4f}{shown:>10}")

    if labels:
        print("\nFORMULA HOLDS A NAME RATHER THAN A FORMULA (labelling choice, left alone)")
        for name, cas, stored, correct in labels:
            print(f"  {name:<20}{cas:<14}{stored:<22}CAS says {correct}")

    if args.write and corrections:
        for index, _name, _cas, _stored, correct, _mass in corrections:
            fields = lines[index].split(",")
            fields[formula_col] = correct
            lines[index] = ",".join(fields)
        COMP_CSV.write_bytes("\n".join(lines).encode("utf-8"))
        print(f"\nwrote {len(corrections)} corrections to {COMP_CSV.name}")
    elif args.write:
        print("\nnothing to write")


if __name__ == "__main__":
    main()
