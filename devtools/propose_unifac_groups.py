"""Propose UNIFAC group assignments for COMP.csv components missing from the UMR-PRU table.

Read-only: writes a proposal file for human review, never edits a NeqSim data table.

Method
------
1. Resolve the component's CAS (from COMP.csv) to an InChI key via ``chemicals``.
2. Look the InChI key up in the DDBST published UNIFAC group assignments shipped
   by ``thermo``. This is the same source NeqSim's own UNIFACGroupParam.csv cites
   ("Hansen1991"), so the subgroup numbering is directly comparable.
3. Gate every hit on an INDEPENDENT physical check: the molar mass implied by the
   summed group formulas must match COMP.csv MOLARMASS.

Anything that fails a step is reported as a failure, never as a best guess. A
component with no DDBST entry is left for a human, because an unverified UNIFAC
assignment silently produces NaN rather than an error (see ComponentGEUnifac).
"""

import csv
import pathlib

import chemicals
import thermo.unifac as unifac

DATA = pathlib.Path("src/main/resources/data")
OUTPUT = pathlib.Path("devtools/unifac_missing_proposal.tsv")

MASS_TOLERANCE = 0.05  # g/mol

# Molar mass of each subgroup, from its formula. Only groups whose composition is
# unambiguous are listed; a component using anything else cannot be mass-checked.
SUBGROUP_MASS = {
    "1": 15.0345,  # CH3
    "2": 14.0266,  # CH2
    "3": 13.0186,  # CH
    "4": 12.0110,  # C
    "5": 27.0453,  # CH2=CH
    "6": 26.0373,  # CH=CH
    "7": 26.0373,  # CH2=C
    "8": 25.0294,  # CH=C
    "70": 24.0220,  # C=C
    "9": 13.0186,  # ACH
    "10": 12.0110,  # AC
    "11": 27.0453,  # ACCH3
    "12": 26.0373,  # ACCH2
    "13": 25.0294,  # ACCH
    "136": 14.0266,  # cCH2
    "137": 13.0186,  # cCH
    "138": 12.0110,  # cC
}
PLACEHOLDER_CAS = {"74-82-8", "0-0-0-0", "", "1", "2", "3"}


def group_names():
    """Return {secondary subgroup number as str: subgroup name}."""
    with (DATA / "UNIFACGroupParam.csv").open(encoding="utf-8", newline="") as handle:
        return {row["Secondary"]: row["Name"] for row in csv.DictReader(handle)}


def umrpru_names():
    """Return the set of component names already present in the UMR-PRU table."""
    with (DATA / "UNIFACcompUMRPRU.csv").open(encoding="utf-8", newline="") as handle:
        return {row["Name"].strip() for row in csv.DictReader(handle)}


def comp_rows():
    """Return the COMP.csv rows as dictionaries."""
    with (DATA / "COMP.csv").open(encoding="utf-8", newline="") as handle:
        return list(csv.DictReader(handle))


def describe(assignment, names):
    """Render a {subgroup: count} assignment as a readable group list."""
    return ", ".join(
        "%s x%d" % (names.get(sub, "sec" + sub), count)
        for sub, count in sorted(assignment.items(), key=lambda kv: int(kv[0]))
    )


def implied_mass(assignment):
    """Return the molar mass implied by an assignment, or None if not checkable."""
    total = 0.0
    for subgroup, count in assignment.items():
        if subgroup not in SUBGROUP_MASS:
            return None
        total += SUBGROUP_MASS[subgroup] * count
    return total


unifac.load_group_assignments_DDBST()
DDBST = unifac.DDBST_UNIFAC_assignments

names = group_names()
present = umrpru_names()

proposed = []
failures = {"bad_cas": [], "no_inchi": [], "no_ddbst": [], "mass_mismatch": [], "unverifiable": []}

for row in comp_rows():
    name = row["NAME"].strip()
    if name in present:
        continue
    if (row.get("COMPTYPE") or row.get("Comptype") or "").strip().lower() not in ("hc", ""):
        pass  # keep everything; type column naming varies

    cas = (row["CASnumber"] or "").strip()
    if cas in PLACEHOLDER_CAS:
        failures["bad_cas"].append((name, cas))
        continue
    try:
        key = chemicals.identifiers.search_chemical(cas).InChI_key
    except Exception:
        failures["no_inchi"].append((name, cas))
        continue
    reference = DDBST.get(key)
    if reference is None:
        failures["no_ddbst"].append((name, cas))
        continue

    assignment = {str(k): v for k, v in reference.items()}
    unknown = [sub for sub in assignment if sub not in names]
    if unknown:
        failures["unverifiable"].append((name, cas, "subgroup(s) not in UNIFACGroupParam: %s" % unknown))
        continue

    expected = implied_mass(assignment)
    declared = float(row["MOLARMASS"])
    if expected is None:
        failures["unverifiable"].append((name, cas, "non-hydrocarbon groups, no mass check"))
        continue
    if abs(expected - declared) > MASS_TOLERANCE:
        failures["mass_mismatch"].append((name, cas, "%.4f vs COMP %.4f" % (expected, declared)))
        continue
    proposed.append((name, cas, assignment, expected, declared))

print("components missing from UNIFACcompUMRPRU.csv : %d" % (len(proposed) + sum(len(v) for v in failures.values())))
print("  PROPOSED (DDBST hit + molar mass verified) : %d" % len(proposed))
for label, entries in failures.items():
    print("  rejected - %-14s : %d" % (label, len(entries)))

print("\n--- PROPOSED ROWS (verified, still require human sign-off) ---")
for name, cas, assignment, expected, declared in proposed:
    print("  %-30s CAS=%-12s MW %.3f/%.3f  %s" % (name, cas, expected, declared, describe(assignment, names)))

print("\n--- REJECTED: CAS is a placeholder (fix COMP.csv first) ---")
for name, cas in failures["bad_cas"][:40]:
    print("  %-30s CAS=%s" % (name, cas or "<empty>"))
if len(failures["bad_cas"]) > 40:
    print("  ... and %d more" % (len(failures["bad_cas"]) - 40))

print("\n--- REJECTED: molar mass disagrees (wrong substance somewhere) ---")
for name, cas, detail in failures["mass_mismatch"]:
    print("  %-30s CAS=%-12s %s" % (name, cas, detail))

print("\n--- REJECTED: no DDBST entry (needs manual assignment) ---")
for name, cas in failures["no_ddbst"][:30]:
    print("  %-30s CAS=%s" % (name, cas))
if len(failures["no_ddbst"]) > 30:
    print("  ... and %d more" % (len(failures["no_ddbst"]) - 30))

with OUTPUT.open("w", encoding="utf-8", newline="") as handle:
    writer = csv.writer(handle, delimiter="\t", lineterminator="\n")
    writer.writerow(["status", "name", "cas", "implied_MW", "comp_MW", "groups"])
    for name, cas, assignment, expected, declared in proposed:
        writer.writerow(["PROPOSED", name, cas, "%.4f" % expected, "%.4f" % declared, describe(assignment, names)])
    for label, entries in failures.items():
        for entry in entries:
            writer.writerow([label.upper(), entry[0], entry[1], "", "", entry[2] if len(entry) > 2 else ""])
print("\nwrote %s" % OUTPUT)
