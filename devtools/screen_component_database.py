"""Screen the NeqSim pure-component database for internal inconsistencies.

Reads ``src/main/resources/data/COMP.csv`` and reports rows whose columns
disagree with one another or carry values that cannot be measurements. It is a
triage aid, not an authority: a finding means two fields disagree, not which one
is wrong. The ``FORMULA`` column in particular is unreliable for some rows, so a
``formula_mass_mismatch`` may indicate a bad formula rather than a bad mass.

The dominant defect in the database is rows created by copying a template row
and editing only some fields, which leaves the untouched columns carrying the
template's values. Rather than hard-coding the resulting sentinel values, the
``over_shared_*`` checks find them by looking for any value shared by more
components than physical coincidence explains, so a template introduced by a
future import is caught without changing this script.

Findings are emitted as ``category<TAB>subject`` with ``--tsv`` so they can be
diffed against the baseline in ``src/test/resources/data/comp_known_issues.tsv``.
Every subject is a raw string taken from the CSV rather than a formatted number,
so this script and the Java gate cannot disagree over rounding.
``ComponentDatabaseIntegrityTest`` applies the same checks and is the CI gate;
this script exists for human triage and prints the detail behind each finding.

Usage::

    python devtools/screen_component_database.py
    python devtools/screen_component_database.py --category critical_below_boiling
    python devtools/screen_component_database.py --tsv > baseline.tsv
"""

import argparse
import csv
import os
import re
import sys
from collections import defaultdict

# A CAS number identifies a substance, so reuse across unrelated components is
# suspect. Charge and spin variants of one parent are exempt (see CHARGE_SUFFIX).
MAX_COMPONENTS_PER_CAS = 1

# Trailing markers that turn a parent component into a modelled ion or carbamate,
# and leading markers for spin/positional isomers. These share the parent's CAS
# by design, so a group that collapses to a single base name is not a finding.
CHARGE_SUFFIX = re.compile(r"(?:COO|[+-])+$")
ISOMER_PREFIX = re.compile(r"^(?:ortho|para|meta)-", re.IGNORECASE)

# A CAS registry number is 2-7 digits, 2 digits, then a check digit.
CAS_PATTERN = re.compile(r"^(\d{2,7})-(\d{2})-(\d)$")

# Explicit "unknown" marker used by rows with no real registry number, such as
# ions and pseudo-components. Exempt from the check-digit test by design.
UNKNOWN_CAS = "0-0-0-0"

# Physical property values do coincide between components, but not across more
# than a handful. Above this the value is a template's rather than a measurement.
MAX_COMPONENTS_PER_PROPERTY_VALUE = 3

PROPERTY_COLUMNS = ("MOLARMASS", "LIQDENS", "NORMBOIL", "FORMULA")

ATOMIC_MASS = {
    "C": 12.011, "H": 1.008, "N": 14.007, "O": 15.999, "S": 32.06,
    "He": 4.0026, "Ne": 20.180, "Ar": 39.948, "Kr": 83.798, "Xe": 131.29,
    "F": 18.998, "Cl": 35.45, "Br": 79.904, "I": 126.90,
    "Na": 22.990, "K": 39.098, "Mg": 24.305, "Ca": 40.078,
    "Ba": 137.33, "Sr": 87.62, "Li": 6.94, "Fe": 55.845,
}

FORMULA_TOKEN = re.compile(r"([A-Z][a-z]?)(\d*)")

# Deviation of the formula-derived mass from MOLARMASS that counts as a finding.
FORMULA_MASS_TOLERANCE = 0.02

# Watson characterisation factor band for hydrocarbons. Paraffins sit near 13
# and aromatics near 10; outside 9-14 the boiling point and density disagree.
WATSON_K_MIN = 9.0
WATSON_K_MAX = 14.0

# Below this boiling point in degC the Watson correlation is not meaningful.
WATSON_MIN_NORMBOIL_C = 0.0

# Abbreviated substituted-alkane names such as 223-TM-C4 state the molecule, so
# the molar mass is derivable from the name and any disagreement is a defect.
# The abbreviation itself is deliberately not trusted: TM reads equally well as
# trimethyl or tetramethyl, so the locant count decides the substituent count.
SUBSTITUENT_NAME = re.compile(
    r"^(?P<locants>[\d.]+)-(?P<abbrev>[A-Za-z]+)-(?P<ring>cy-)?C(?P<base>\d+)$",
    re.IGNORECASE)
METHYL_ABBREVIATIONS = frozenset(("M", "DM", "TM", "TRM", "TEM", "TETM"))
CH2_MASS = 14.02658
H2_MASS = 2.01588
# Comfortably below one CH2 group, so a wrong substituent count cannot slip past.
SUBSTITUENT_MASS_TOLERANCE = 0.6
MAX_METHYL_GROUPS = 6


def parse_float(text):
    """Parse a CSV field as a float.

    :param text: raw field value, possibly empty or non-numeric
    :return: the value as float, or None when it cannot be parsed
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


def formula_mass(formula):
    """Compute molar mass in g/mol from a chemical formula.

    :param formula: formula string such as ``C7H16``
    :return: molar mass in g/mol, or None when the formula cannot be parsed
    """
    if not formula:
        return None
    formula = formula.strip()
    if not formula or not formula[0].isupper():
        return None
    total = 0.0
    consumed = 0
    for element, count in FORMULA_TOKEN.findall(formula):
        if element not in ATOMIC_MASS:
            return None
        total += ATOMIC_MASS[element] * (int(count) if count else 1)
        consumed += len(element) + len(count)
    if consumed != len(formula) or total <= 0.0:
        return None
    return total


def watson_k(normboil_c, liqdens):
    """Compute the Watson characterisation factor.

    :param normboil_c: normal boiling point in degC
    :param liqdens: relative density (specific gravity), water = 1
    :return: Watson K, or None when the inputs are unusable
    """
    if normboil_c is None or liqdens is None or liqdens <= 0.0:
        return None
    rankine = (normboil_c + 273.15) * 1.8
    if rankine <= 0.0:
        return None
    return rankine ** (1.0 / 3.0) / liqdens


def count_locants(locants):
    """Count the substituent positions written in an abbreviated name.

    ``2.2.3`` and ``223`` both denote three positions.

    :param locants: str locant fragment taken from the component name
    :return: int number of substituent positions
    """
    if "." in locants:
        return len([part for part in locants.split(".") if part])
    return len(locants)


def methyls_from_mass(molar_mass, base_carbons, is_ring):
    """Infer how many methyl groups a molar mass implies.

    :param molar_mass: float molar mass in g/mol
    :param base_carbons: int carbons in the parent chain or ring
    :param is_ring: bool, True when the parent is a cycloalkane
    :return: int methyl count, or None when no integer count fits the mass
    """
    for methyls in range(0, MAX_METHYL_GROUPS + 1):
        carbons = base_carbons + methyls
        expected = CH2_MASS * carbons + (0.0 if is_ring else H2_MASS)
        if abs(molar_mass - expected) <= SUBSTITUENT_MASS_TOLERANCE:
            return methyls
    return None


def one_parent(names):
    """Report whether every name is a charge or isomer variant of one parent.

    Such a group shares the parent's CAS number deliberately (for example
    ``MEA``, ``MEA+`` and ``MEACOO-``) and is therefore not a data defect.

    :param names: component names sharing one CAS number
    :return: True when all names reduce to the same base name
    """
    bases = set()
    for name in names:
        base = ISOMER_PREFIX.sub("", name)
        bases.add(CHARGE_SUFFIX.sub("", base).strip().lower())
    return len(bases) == 1


def cas_is_valid(cas):
    """Verify a CAS registry number against its trailing check digit.

    The check digit is the sum of the preceding digits weighted by their
    position counted from the right, modulo ten. This detects transcription
    errors and fabricated numbers without consulting any external source.

    :param cas: registry number in ``XXXXXXX-YY-Z`` form
    :return: True when well formed and the check digit agrees
    """
    match = CAS_PATTERN.match(cas)
    if match is None:
        return False
    body = match.group(1) + match.group(2)
    total = sum(int(digit) * (position + 1)
                for position, digit in enumerate(reversed(body)))
    return total % 10 == int(match.group(3))


def screen(rows):
    """Apply every consistency check to the database rows.

    :param rows: list of dicts, one per COMP.csv row
    :return: list of (category, subject, detail) tuples, sorted and deduplicated
    """
    findings = []
    by_cas = defaultdict(list)
    by_property = defaultdict(lambda: defaultdict(list))
    by_triplet = defaultdict(list)

    for row in rows:
        name = (row.get("NAME") or "").strip()
        if not name:
            continue
        comptype = (row.get("COMPTYPE") or "").strip().lower()
        molar_mass = parse_float(row.get("MOLARMASS"))
        liqdens = parse_float(row.get("LIQDENS"))
        normboil = parse_float(row.get("NORMBOIL"))
        tc = parse_float(row.get("TC"))
        formula = (row.get("FORMULA") or "").strip()

        if any(ord(char) > 127 for char in name):
            findings.append((
                "non_ascii_name", name,
                "name contains characters that cannot be typed from code"))

        derived = formula_mass(formula)
        if derived is not None and molar_mass and molar_mass > 0.0:
            deviation = abs(derived - molar_mass) / molar_mass
            if deviation > FORMULA_MASS_TOLERANCE:
                findings.append((
                    "formula_mass_mismatch", name,
                    "FORMULA %s implies %.3f g/mol but MOLARMASS is %.3f"
                    % (formula, derived, molar_mass)))

        if tc is not None and normboil is not None and tc <= normboil:
            findings.append((
                "critical_below_boiling", name,
                "TC %.2f degC is not above NORMBOIL %.2f degC" % (tc, normboil)))

        substituted = SUBSTITUENT_NAME.match(name)
        if (substituted is not None
                and substituted.group("abbrev").upper() in METHYL_ABBREVIATIONS
                and molar_mass is not None):
            locants = count_locants(substituted.group("locants"))
            implied = methyls_from_mass(
                molar_mass, int(substituted.group("base")),
                substituted.group("ring") is not None)
            if implied != locants:
                findings.append((
                    "substituent_count_mismatch", name,
                    "name states %d substituent position(s) but MOLARMASS %.3f "
                    "implies %s"
                    % (locants, molar_mass,
                       "no whole number of methyl groups" if implied is None
                       else "%d" % implied)))

        if comptype == "hc" and normboil is not None and normboil > WATSON_MIN_NORMBOIL_C:
            kw = watson_k(normboil, liqdens)
            if kw is not None and not (WATSON_K_MIN <= kw <= WATSON_K_MAX):
                findings.append((
                    "implausible_watson_k", name,
                    "NORMBOIL %.2f degC with LIQDENS %s gives Watson K %.2f"
                    % (normboil, (row.get("LIQDENS") or "").strip(), kw)))

        cas = (row.get("CASnumber") or "").strip()
        if cas:
            by_cas[cas].append(name)
            if cas != UNKNOWN_CAS and not cas_is_valid(cas):
                findings.append((
                    "cas_checksum_invalid", name,
                    "CASnumber %s is not a valid registry number" % cas))
        for column in PROPERTY_COLUMNS:
            value = (row.get(column) or "").strip()
            if value:
                by_property[column][value].append(name)

        if comptype != "ion" and molar_mass and normboil is not None and liqdens is not None:
            raw_triplet = "%s|%s|%s" % (
                (row.get("MOLARMASS") or "").strip(),
                (row.get("NORMBOIL") or "").strip(),
                (row.get("LIQDENS") or "").strip())
            by_triplet[raw_triplet].append(name)

    # Group findings are keyed on the group rather than its members so that
    # adding one component to an existing group does not churn every entry.
    for cas in sorted(by_cas):
        names = sorted(by_cas[cas])
        if len(names) > MAX_COMPONENTS_PER_CAS and not one_parent(names):
            findings.append((
                "shared_cas_number", cas, "shared by %s" % ", ".join(names)))

    for column in PROPERTY_COLUMNS:
        for value in sorted(by_property[column]):
            names = sorted(by_property[column][value])
            if len(names) > MAX_COMPONENTS_PER_PROPERTY_VALUE:
                findings.append((
                    "over_shared_" + column.lower(), value,
                    "shared by %d components: %s" % (len(names), ", ".join(names))))

    for triplet in sorted(by_triplet):
        names = sorted(by_triplet[triplet])
        if len(names) > 1:
            findings.append((
                "duplicate_property_triplet", triplet,
                "MOLARMASS|NORMBOIL|LIQDENS shared by %s" % ", ".join(names)))

    return sorted(set(findings))


def read_database(path):
    """Read COMP.csv.

    :param path: absolute path to the CSV file
    :return: list of dicts, one per row
    """
    with open(path, "r", encoding="utf-8-sig", newline="") as handle:
        return list(csv.DictReader(handle))


def default_database_path():
    """Locate COMP.csv relative to this script.

    :return: absolute path to the component database
    """
    here = os.path.dirname(os.path.abspath(__file__))
    return os.path.abspath(
        os.path.join(here, os.pardir, "src", "main", "resources", "data", "COMP.csv"))


def main(argv):
    """Run the screening and print the findings.

    :param argv: command line arguments excluding the program name
    :return: process exit code, always 0
    """
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--database", help="path to COMP.csv")
    parser.add_argument("--category", help="show only this finding category")
    parser.add_argument("--tsv", action="store_true",
                        help="emit the two-column category/subject baseline form")
    args = parser.parse_args(argv)

    path = os.path.abspath(args.database) if args.database else default_database_path()
    rows = read_database(path)
    findings = screen(rows)
    if args.category:
        findings = [f for f in findings if f[0] == args.category]

    if args.tsv:
        for category, subject, _detail in findings:
            print("%s\t%s" % (category, subject))
        return 0

    print("database: %s" % path)
    print("rows: %d   findings: %d" % (len(rows), len(findings)))
    by_category = defaultdict(list)
    for category, subject, detail in findings:
        by_category[category].append((subject, detail))
    for category in sorted(by_category):
        entries = by_category[category]
        print("")
        print("=== %s (%d) ===" % (category, len(entries)))
        for subject, detail in entries:
            print("    %-34s %s" % (subject, detail))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
