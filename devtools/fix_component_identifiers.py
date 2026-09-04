#!/usr/bin/env python
"""Repair wrong CAS numbers and molecular formulas in the component database.

Many rows in ``COMP.csv`` were created by copying a template row and editing
only some fields, leaving another component's identifiers behind. The most
common artefact is methane's CAS number ``74-82-8`` on 70 unrelated rows.

Only ``CASnumber`` and ``FORMULA`` are touched. Both are pure metadata:
``CASnumber`` is stored, exposed through ``getCASNumbers()`` and compared in
``Component.equals``; ``FORMULA`` is surfaced by ``ComponentQuery``. Neither
feeds a thermodynamic calculation, so these corrections cannot move a
simulation result. ``TC``, ``PC``, ``ACSFACT``, ``LIQDENS`` and ``NORMBOIL``
are deliberately left alone -- several are demonstrably wrong too, but they do
change results and need a regression baseline before being altered.

Every replacement is gated on an independent molar-mass check: a candidate is
accepted only when the reference substance's molar mass agrees with the row's
own ``MOLARMASS``. This is what stops a plausible-looking but wrong match, for
example ``search_chemical("MDEA")`` returning methylenedioxyamphetamine
(207 g/mol) rather than methyldiethanolamine (119 g/mol). Ambiguity fails; it
does not fall back to the closest hit.

Rows that cannot be verified but still carry methane's CAS are set to the
``0-0-0-0`` sentinel already used for ions in this file: an explicit unknown is
safer than a valid identifier belonging to a different substance.

Usage::

    python devtools/fix_component_identifiers.py            # report only
    python devtools/fix_component_identifiers.py --apply    # rewrite COMP.csv

Requires the ``chemicals`` package (MIT) for the reference lookup.
"""

import argparse
import csv
import io
import os
import re
import sys

# CAS values that mark a row as unidentified: methane's number used as filler,
# the file's own unknown sentinel, and a few single-digit stubs.
PLACEHOLDER_CAS = frozenset(["74-82-8", "0-0-0-0", "1", "2", "3"])

# Sentinel written when a row cannot be verified. Already used by the ion rows.
UNKNOWN_CAS = "0-0-0-0"

# Relative molar-mass agreement required before a reference match is accepted.
MOLAR_MASS_TOLERANCE = 0.005

# Deviation of the formula-derived mass from MOLARMASS above which the existing
# formula is considered wrong and eligible for replacement.
FORMULA_MASS_TOLERANCE = 0.02

# NeqSim uses in-house abbreviations that no reference database recognises.
# These map to an unambiguous search term; the molar-mass gate still applies,
# so a wrong mapping is rejected rather than written.
NAME_ALIASES = {
    "nC34": "tetratriacontane",
    "nC39": "nonatriacontane",
    "c-hexane": "cyclohexane",
    "c-propane": "cyclopropane",
    "R12": "dichlorodifluoromethane",
    "R134a": "1,1,1,2-tetrafluoroethane",
    "SF6": "sulfur hexafluoride",
    "CaCl2": "calcium chloride",
    "ice": "water",
    "MDEA": "methyldiethanolamine",
    "DEA": "diethanolamine",
    "Piperazine": "piperazine",
    "ethanolPVTsim": "ethanol",
    "methanolPVTsim": "methanol",
    "propanePVTsim": "propane",
    "nbutanePVTsim": "butane",
    "MEGPVTsim18": "ethylene glycol",
    "MEGPVTsim19": "ethylene glycol",
    "24-DM-C5": "2,4-dimethylpentane",
    "23-DM-C5": "2,3-dimethylpentane",
    "33-DM-C5": "3,3-dimethylpentane",
    "22-DM-C6": "2,2-dimethylhexane",
    "24-DM-C6": "2,4-dimethylhexane",
    "25-DM-C6": "2,5-dimethylhexane",
    "224-TM-C5": "2,2,4-trimethylpentane",
    "3-E-C6": "3-ethylhexane",
    "E-cy-C5": "ethylcyclopentane",
    "11-DM-cy-C5": "1,1-dimethylcyclopentane",
    "1.2-DM-cyC5": "1,2-dimethylcyclopentane",
    "113-TM-cy-C5": "1,1,3-trimethylcyclopentane",
    "1.2.4-TMcyC6": "1,2,4-trimethylcyclohexane",
    "o-E-toluene": "2-ethyltoluene",
    "nC5-Benzene": "pentylbenzene",
    "nC6-Benzene": "hexylbenzene",
    "nC7-Benzene": "heptylbenzene",
    "nC8-Benzene": "octylbenzene",
    "nC9-Benzene": "nonylbenzene",
    "nC10-Benzene": "decylbenzene",
    "nC10-cy-C5": "decylcyclopentane",
}

ATOMIC_MASS = {
    "C": 12.011, "H": 1.008, "N": 14.007, "O": 15.999, "S": 32.06,
    "He": 4.0026, "Ne": 20.180, "Ar": 39.948, "Kr": 83.798, "Xe": 131.29,
    "F": 18.998, "Cl": 35.45, "Br": 79.904, "I": 126.90,
    "Na": 22.990, "K": 39.098, "Mg": 24.305, "Ca": 40.078,
    "Ba": 137.33, "Sr": 87.62, "Li": 6.94, "Fe": 55.845,
}

FORMULA_TOKEN = re.compile(r"([A-Z][a-z]?)(\d*)")


def formula_mass(formula):
    """Compute the molar mass implied by a molecular formula.

    :param formula: formula such as ``C3H8``, without charge or phase markers
    :return: mass in g/mol, or None when the formula cannot be parsed
    """
    text = formula.strip()
    if not text:
        return None
    total = 0.0
    consumed = 0
    for element, count in FORMULA_TOKEN.findall(text):
        if element not in ATOMIC_MASS:
            return None
        total += ATOMIC_MASS[element] * (int(count) if count else 1)
        consumed += len(element) + len(count)
    if consumed != len(text) or total <= 0.0:
        return None
    return total


def parse_float(text):
    """Convert a database cell to a float.

    :param text: raw cell contents
    :return: the value, or None when the cell is blank or not numeric
    """
    try:
        return float((text or "").strip())
    except (TypeError, ValueError):
        return None


def lookup(name, molar_mass, search_chemical):
    """Resolve a component name to a reference substance.

    The match is accepted only when the reference molar mass agrees with the
    row's own value, so an ambiguous abbreviation is rejected rather than
    resolved to the nearest hit.

    :param name: component name as it appears in the database
    :param molar_mass: the row's MOLARMASS in g/mol, or None
    :param search_chemical: reference lookup function taking a search term
    :return: tuple of (metadata, reason) where exactly one is None
    """
    if molar_mass is None or molar_mass <= 0.0:
        return None, "no molar mass in row"
    terms = []
    if name in NAME_ALIASES:
        terms.append(NAME_ALIASES[name])
    terms.append(name)
    for term in terms:
        try:
            hit = search_chemical(term)
        except Exception:
            continue
        deviation = abs(hit.MW - molar_mass) / molar_mass
        if deviation < MOLAR_MASS_TOLERANCE:
            return hit, None
        return None, "molar mass %.3f vs row %.3f" % (hit.MW, molar_mass)
    return None, "no reference match"


def repair(rows, search_chemical):
    """Correct CAS numbers and formulas in place.

    :param rows: database rows as dictionaries, modified in place
    :param search_chemical: reference lookup function taking a search term
    :return: tuple of (verified, blanked, skipped) change records
    """
    verified = []
    blanked = []
    skipped = []
    for row in rows:
        name = (row.get("NAME") or "").strip()
        cas = (row.get("CASnumber") or "").strip()
        if cas not in PLACEHOLDER_CAS or name.lower() == "methane":
            continue
        molar_mass = parse_float(row.get("MOLARMASS"))
        hit, reason = lookup(name, molar_mass, search_chemical)
        if hit is None:
            if cas == "74-82-8":
                row["CASnumber"] = UNKNOWN_CAS
                blanked.append((name, reason))
            else:
                skipped.append((name, cas, reason))
            continue
        changes = ["CAS %s -> %s" % (cas, hit.CASs)]
        row["CASnumber"] = hit.CASs
        # Replace the formula only when the existing one contradicts MOLARMASS
        # and the reference formula does not, so correct entries are untouched.
        existing = (row.get("FORMULA") or "").strip()
        existing_mass = formula_mass(existing)
        wrong = existing_mass is None or (
            molar_mass and abs(existing_mass - molar_mass) / molar_mass > FORMULA_MASS_TOLERANCE)
        reference_mass = formula_mass(hit.formula or "")
        good = reference_mass is not None and molar_mass and (
            abs(reference_mass - molar_mass) / molar_mass <= FORMULA_MASS_TOLERANCE)
        if wrong and good and existing != hit.formula:
            changes.append("FORMULA %s -> %s" % (existing or "(blank)", hit.formula))
            row["FORMULA"] = hit.formula
        verified.append((name, "; ".join(changes)))
    return verified, blanked, skipped


def database_path():
    """Locate COMP.csv relative to this script.

    :return: absolute path to the component database
    """
    here = os.path.dirname(os.path.abspath(__file__))
    return os.path.join(here, os.pardir, "src", "main", "resources", "data", "COMP.csv")


def main(argv=None):
    """Report or apply component identifier corrections.

    :param argv: command line arguments, defaulting to ``sys.argv[1:]``
    :return: process exit status
    """
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--database", default=database_path(), help="path to COMP.csv")
    parser.add_argument("--apply", action="store_true", help="write the corrections")
    args = parser.parse_args(argv)

    try:
        from chemicals.identifiers import search_chemical
    except ImportError:
        sys.stderr.write("the 'chemicals' package is required: pip install chemicals\n")
        return 2

    path = os.path.abspath(args.database)
    with io.open(path, encoding="utf-8-sig", newline="") as handle:
        reader = csv.DictReader(handle)
        fieldnames = reader.fieldnames
        rows = list(reader)

    verified, blanked, skipped = repair(rows, search_chemical)

    print("verified against reference and corrected: %d" % len(verified))
    for name, change in verified:
        print("   %-18s %s" % (name, change))
    print()
    print("methane's CAS removed, left as %s: %d" % (UNKNOWN_CAS, len(blanked)))
    for name, reason in blanked:
        print("   %-18s %s" % (name, reason))
    print()
    print("left untouched: %d" % len(skipped))
    for name, cas, reason in skipped:
        print("   %-18s %-10s %s" % (name, cas, reason))

    if not args.apply:
        print("\nreport only; pass --apply to write %s" % path)
        return 0

    with io.open(path, "w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames, lineterminator="\n")
        writer.writeheader()
        writer.writerows(rows)
    print("\nwrote %s" % path)
    return 0


if __name__ == "__main__":
    sys.exit(main())
