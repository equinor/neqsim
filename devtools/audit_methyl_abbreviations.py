"""Audit the methyl-substituent abbreviations used in COMP.csv component names.

Names such as ``223-TM-C4`` encode a substituted alkane, but the abbreviation
``TM`` is ambiguous in isolation: it reads equally well as *tri*methyl or
*tetra*methyl. This script decides, per name and from the data alone, how many
methyl groups the row's molar mass actually implies, and compares that with the
number of locants written in the name.

The molar mass is the arbiter, not the abbreviation:

    open-chain alkane   MW = 14.027 * (base carbons + methyls) + 2.016
    cycloalkane         MW = 14.027 * (base carbons + methyls)

Verdicts:

``consistent``
    The locant count and the molar mass agree on the number of methyl groups.
    The name is decodable without relying on what ``TM`` is supposed to mean.

``locant/mass conflict``
    They disagree. Either the name or the molar mass is wrong, and the
    abbreviation cannot be trusted to settle it.

``ambiguous abbreviation``
    The abbreviation admits more than one reading and the molar mass does not
    single one out.

This script is read-only.

Usage::

    python audit_methyl_abbreviations.py
"""

from __future__ import annotations

import argparse
import collections
import csv
import os
import re
import sys

CH2 = 14.02658
H2 = 2.01588
MASS_TOLERANCE = 0.6  # g/mol; comfortably below one CH2 group

# Abbreviation -> the methyl counts it could plausibly denote.
ABBREVIATION_READINGS = {
    "M": (1,),
    "DM": (2,),
    "TM": (3, 4),  # trimethyl or tetramethyl - the ambiguity under review
    "TRM": (3,),
    "TEM": (4,),
    "TETM": (4,),
}

# <locants>-<abbrev>-[cy-]C<n>, locants separated by dots or run together.
NAME_PATTERN = re.compile(
    r"^(?P<locants>[\d.]+)-(?P<abbrev>[A-Za-z]+)-(?P<ring>cy-)?C(?P<base>\d+)$",
    re.IGNORECASE,
)

CONSISTENT = "consistent"
CONFLICT = "locant/mass conflict"
AMBIGUOUS = "ambiguous abbreviation"


def parse_float(text):
    """Convert a CSV cell to float, returning None when it is not numeric.

    :param text: str or None, the raw cell contents.
    :returns: float value, or None when empty or unparseable.
    """
    if text is None:
        return None
    text = text.strip()
    if not text:
        return None
    try:
        return float(text)
    except ValueError:
        return None


def count_locants(locants):
    """Count the substituent positions written in a name fragment.

    ``2.2.3`` and ``223`` both denote three positions.

    :param locants: str locant fragment from the component name.
    :returns: int number of positions.
    """
    if "." in locants:
        return len([part for part in locants.split(".") if part])
    return len(locants)


def methyls_from_mass(molar_mass, base_carbons, is_ring):
    """Infer the number of methyl groups implied by a molar mass.

    :param molar_mass: float molar mass in g/mol.
    :param base_carbons: int carbons in the parent chain or ring.
    :param is_ring: bool, True for a cycloalkane parent.
    :returns: int methyl count, or None when no integer count fits within
        MASS_TOLERANCE.
    """
    for methyls in range(0, 7):
        carbons = base_carbons + methyls
        expected = CH2 * carbons + (0.0 if is_ring else H2)
        if abs(molar_mass - expected) <= MASS_TOLERANCE:
            return methyls
    return None


def audit(rows):
    """Audit every abbreviated methyl name in the database.

    :param rows: list of dict rows read from COMP.csv.
    :returns: list of dict findings, one per parsed name.
    """
    findings = []
    for row in rows:
        name = (row.get("NAME") or "").strip()
        match = NAME_PATTERN.match(name)
        if not match:
            continue

        abbrev = match.group("abbrev").upper()
        if abbrev not in ABBREVIATION_READINGS:
            continue

        base_carbons = int(match.group("base"))
        is_ring = match.group("ring") is not None
        locants = count_locants(match.group("locants"))
        molar_mass = parse_float(row.get("MOLARMASS"))
        from_mass = (
            None
            if molar_mass is None
            else methyls_from_mass(molar_mass, base_carbons, is_ring)
        )

        readings = ABBREVIATION_READINGS[abbrev]
        if from_mass is None:
            verdict = CONFLICT
        elif from_mass != locants:
            verdict = CONFLICT
        elif len(readings) > 1 and from_mass not in readings:
            verdict = AMBIGUOUS
        else:
            verdict = CONSISTENT

        findings.append(
            {
                "name": name,
                "cas": (row.get("CASnumber") or "").strip(),
                "abbrev": abbrev,
                "locants": locants,
                "methyls_from_mass": from_mass,
                "molar_mass": molar_mass,
                "base_carbons": base_carbons,
                "ring": is_ring,
                "verdict": verdict,
            }
        )
    return findings


def main(argv=None):
    """Command line entry point.

    :param argv: optional list of str arguments; defaults to sys.argv[1:].
    :returns: int process exit code, 0 on success.
    """
    here = os.path.dirname(os.path.abspath(__file__))
    default_comp = os.path.normpath(
        os.path.join(here, os.pardir, "src", "main", "resources", "data", "COMP.csv")
    )

    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--comp", default=default_comp)
    args = parser.parse_args(argv)

    with open(args.comp, "r", encoding="utf-8-sig", newline="") as handle:
        rows = list(csv.DictReader(handle))

    findings = audit(rows)
    print("abbreviated methyl names parsed: %d\n" % len(findings))

    by_abbrev = collections.defaultdict(collections.Counter)
    for finding in findings:
        by_abbrev[finding["abbrev"]][finding["methyls_from_mass"]] += 1

    print("how many methyl groups the molar mass implies, per abbreviation:")
    for abbrev in sorted(by_abbrev):
        counts = by_abbrev[abbrev]
        detail = ", ".join(
            "%s methyl -> %d rows" % ("unresolved" if k is None else k, n)
            for k, n in sorted(counts.items(), key=lambda kv: (kv[0] is None, kv[0]))
        )
        readings = "/".join(str(r) for r in ABBREVIATION_READINGS[abbrev])
        print("  %-5s (could mean %s): %s" % (abbrev, readings, detail))

    verdicts = collections.Counter(f["verdict"] for f in findings)
    print("\nverdicts:")
    for verdict in (CONSISTENT, CONFLICT, AMBIGUOUS):
        print("  %-22s %3d" % (verdict, verdicts.get(verdict, 0)))

    problems = [f for f in findings if f["verdict"] != CONSISTENT]
    if problems:
        print("\nnames whose locant count and molar mass disagree:")
        print(
            "  %-20s %-11s %-6s %-8s %-8s %s"
            % ("name", "cas", "abbrev", "locants", "by mass", "MW")
        )
        for finding in sorted(problems, key=lambda f: f["name"]):
            print(
                "  %-20s %-11s %-6s %-8d %-8s %s"
                % (
                    finding["name"],
                    finding["cas"],
                    finding["abbrev"],
                    finding["locants"],
                    "?" if finding["methyls_from_mass"] is None else finding["methyls_from_mass"],
                    finding["molar_mass"],
                )
            )

    return 0


if __name__ == "__main__":
    sys.exit(main())
