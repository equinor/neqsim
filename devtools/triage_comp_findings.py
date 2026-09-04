"""Triage the remaining COMP.csv integrity findings by risk of changing results.

Read-only. Splits the baseline findings into:

  * identity-only  - FORMULA / CASnumber / NAME text; no thermodynamic effect
  * result-changing - MOLARMASS / LIQDENS / NORMBOIL / TC / PC / ACSFACT

For every ``formula_mass_mismatch`` it asks the offline ``chemicals`` database
which of the two disagreeing fields (FORMULA or MOLARMASS) is the wrong one, so
the safe subset can be separated from the subset that needs sign-off.
"""

import builtins
import collections
import csv
import io
import pathlib
import re

import chemicals

_REPORT = io.StringIO()


def print(*args, **kwargs):  # noqa: A001 - deliberate: capture the report as UTF-8
    """Write a report line to the in-memory buffer instead of the console.

    The Windows console codec mangles the non-ASCII component names, so the
    report is buffered and written to disk as UTF-8 at the end.
    """
    kwargs["file"] = _REPORT
    builtins.print(*args, **kwargs)

ROOT = pathlib.Path(__file__).resolve().parents[1]
DATABASE = ROOT / "src" / "main" / "resources" / "data" / "COMP.csv"
BASELINE = ROOT / "src" / "test" / "resources" / "data" / "comp_known_issues.tsv"

ATOMIC_MASS = {
    "C": 12.011,
    "H": 1.008,
    "O": 15.999,
    "N": 14.007,
    "S": 32.06,
    "Ar": 39.948,
    "He": 4.0026,
    "Cl": 35.45,
    "Na": 22.9898,
    "K": 39.0983,
    "Ca": 40.078,
    "Mg": 24.305,
    "Fe": 55.845,
    "Ba": 137.327,
    "Sr": 87.62,
    "Br": 79.904,
    "F": 18.998,
    "I": 126.904,
    "Li": 6.94,
    "Zn": 65.38,
    "Mn": 54.938,
    "Si": 28.085,
}
FORMULA_TOKEN = re.compile(r"([A-Z][a-z]?)(\d*)")
CHARGE_SUFFIX = re.compile(r"(?:COO|[+-])+$")

IDENTITY_ONLY = {
    "formula_mass_mismatch",  # split further below
    "non_ascii_name",
    "cas_checksum_invalid",
    "shared_cas_number",
    "substituent_count_mismatch",
}
RESULT_CHANGING = {
    "duplicate_property_triplet",
    "over_shared_liqdens",
    "over_shared_formula",
    "over_shared_normboil",
    "over_shared_molarmass",
    "implausible_watson_k",
    "critical_below_boiling",
}


def formula_mass(formula):
    """Return the molar mass in g/mol implied by a formula string, or None."""
    stripped = CHARGE_SUFFIX.sub("", (formula or "").strip())
    if not stripped:
        return None
    total = 0.0
    consumed = 0
    for element, count in FORMULA_TOKEN.findall(stripped):
        if element not in ATOMIC_MASS:
            return None
        total += ATOMIC_MASS[element] * (int(count) if count else 1)
        consumed += len(element) + len(count)
    if consumed != len(stripped):
        return None
    return total


def reference_for(cas):
    """Return (common_name, formula, MW) from the chemicals database, or None."""
    try:
        hit = chemicals.identifiers.search_chemical(cas)
    except Exception:
        return None
    return (hit.common_name, hit.formula, hit.MW)


rows = {}
with DATABASE.open(encoding="utf-8", newline="") as handle:
    for row in csv.DictReader(handle):
        rows[row["NAME"].strip()] = row

findings = collections.defaultdict(list)
for line in BASELINE.read_text(encoding="utf-8").splitlines():
    if not line.strip():
        continue
    category, subject = line.split("\t", 1)
    findings[category].append(subject)

print("=" * 78)
print("formula_mass_mismatch - which field is wrong?")
print("=" * 78)
buckets = collections.defaultdict(list)
for subject in sorted(findings.get("formula_mass_mismatch", [])):
    row = rows.get(subject)
    if row is None:
        buckets["row not found"].append((subject, "", ""))
        continue
    declared = float(row["MOLARMASS"])
    implied = formula_mass(row["FORMULA"])
    reference = reference_for((row["CASnumber"] or "").strip())
    if reference is None:
        buckets["no CAS reference - manual"].append(
            (subject, row["FORMULA"], "MOLARMASS=%.3f implied=%s" % (declared, implied))
        )
        continue
    ref_name, ref_formula, ref_mw = reference
    mass_ok = abs(declared - ref_mw) < 0.02 * max(1.0, abs(ref_mw)) / 100.0 * 100.0
    mass_ok = abs(declared - ref_mw) < 0.05
    formula_ok = (row["FORMULA"] or "").strip().upper() == (ref_formula or "").upper()
    if mass_ok and not formula_ok:
        buckets["FORMULA wrong (identity-only, SAFE)"].append(
            (subject, "%s -> %s" % (row["FORMULA"], ref_formula), ref_name[:34])
        )
    elif formula_ok and not mass_ok:
        buckets["MOLARMASS wrong (result-changing)"].append(
            (subject, "%.4f -> %.4f" % (declared, ref_mw), ref_name[:34])
        )
    elif not mass_ok and not formula_ok:
        buckets["BOTH wrong - wrong substance?"].append(
            (subject, "%s/%.3f vs %s/%.3f" % (row["FORMULA"], declared, ref_formula, ref_mw), ref_name[:34])
        )
    else:
        buckets["reference agrees - check tolerance"].append((subject, row["FORMULA"], ref_name[:34]))

for bucket in sorted(buckets):
    print("\n%s  (%d)" % (bucket, len(buckets[bucket])))
    for subject, detail, note in buckets[bucket]:
        print("   %-28s %-34s %s" % (subject, detail, note))

print()
print("=" * 78)
print("remaining categories")
print("=" * 78)
for category in sorted(findings):
    if category == "formula_mass_mismatch":
        continue
    kind = "identity-only" if category in IDENTITY_ONLY else "RESULT-CHANGING"
    print("\n%-28s %-16s (%d)" % (category, kind, len(findings[category])))
    for subject in sorted(findings[category]):
        print("   %s" % subject)

REPORT_PATH = ROOT / "devtools" / "comp_findings_triage.txt"
REPORT_PATH.write_bytes(_REPORT.getvalue().encode("utf-8"))
builtins.print("wrote %s" % REPORT_PATH)
