"""Cross-check NeqSim's UMR-PRU UNIFAC group table against the DDBST published assignments.

Read-only. Purpose is to measure how well an automated source reproduces the rows
NeqSim ALREADY has, before that source is trusted to fill in the missing ones.

Source of truth for the comparison:
  DDBST published UNIFAC / modified-UNIFAC group assignments, as shipped by the
  MIT-licensed ``thermo`` package (thermo.unifac.DDBST_*_assignments), keyed by
  InChI key. NeqSim's own UNIFACGroupParam.csv cites the same origin
  ("Hansen1991"), so the subgroup numbering is directly comparable.

Nothing is written. Disagreements are reported, never auto-resolved.
"""

import csv
import pathlib

import chemicals
import thermo.unifac as unifac

DATA = pathlib.Path("src/main/resources/data")

# NeqSim subgroups that have no counterpart in the published UNIFAC set. A
# component using any of these cannot be compared and is reported separately.
NEQSIM_ONLY_SUBGROUPS = {
    "120",  # NH3    (PSRK, Holderbaum 1991)
    "121",  # CO2
    "122",  # CH4
    "123",  # O2
    "124",  # Ar
    "125",  # N2
    "126",  # H2S
    "127",  # H2
    "128",  # CO
    "134",  # C2H6
    "135",  # Hg
    "136",  # cCH2   (UMR-PRU cyclic groups, Voutsas)
    "137",  # cCH
    "138",  # cC
    "139",  # MEG
    "140",  # TEG
}
# UMR-PRU cyclic groups map onto the plain aliphatic groups when comparing
# against published UNIFAC, which has no separate ring groups.
CYCLIC_TO_ALIPHATIC = {"136": "2", "137": "3", "138": "4"}


def load_group_names():
    """Return {secondary subgroup number as str: subgroup name}."""
    with (DATA / "UNIFACGroupParam.csv").open(encoding="utf-8", newline="") as handle:
        return {row["Secondary"]: row["Name"] for row in csv.DictReader(handle)}


def load_neqsim_table(filename):
    """Return {component name: {subgroup number as str: count}} for a NeqSim UNIFAC table."""
    table = {}
    with (DATA / filename).open(encoding="utf-8", newline="") as handle:
        for row in csv.DictReader(handle):
            assignment = {}
            for column, value in row.items():
                if not column or not column.startswith("sub"):
                    continue
                if value and value.strip() not in ("", "0"):
                    assignment[column[3:]] = int(value)
            table[row["Name"].strip()] = assignment
    return table


def comp_cas():
    """Return {component name: CAS} from COMP.csv."""
    with (DATA / "COMP.csv").open(encoding="utf-8", newline="") as handle:
        return {row["NAME"].strip(): (row["CASnumber"] or "").strip() for row in csv.DictReader(handle)}


def inchi_key(cas):
    """Return the InChI key for a CAS number, or None when it cannot be resolved."""
    try:
        return chemicals.identifiers.search_chemical(cas).InChI_key
    except Exception:
        return None


def normalise(assignment):
    """Fold UMR-PRU cyclic groups onto their aliphatic equivalents for comparison."""
    folded = {}
    for subgroup, count in assignment.items():
        key = CYCLIC_TO_ALIPHATIC.get(subgroup, subgroup)
        folded[key] = folded.get(key, 0) + count
    return folded


def describe(assignment, names):
    """Render an assignment as a readable group list."""
    if not assignment:
        return "<none>"
    return ", ".join(
        "%s x%d" % (names.get(sub, "sec" + sub), count) for sub, count in sorted(assignment.items(), key=lambda kv: int(kv[0]))
    )


unifac.load_group_assignments_DDBST()
DDBST = unifac.DDBST_UNIFAC_assignments

names = load_group_names()
neqsim = load_neqsim_table("UNIFACcompUMRPRU.csv")
cas_of = comp_cas()

agree = []
differ = []
no_reference = []
not_comparable = []

for component, assignment in sorted(neqsim.items()):
    if any(sub in NEQSIM_ONLY_SUBGROUPS and sub not in CYCLIC_TO_ALIPHATIC for sub in assignment):
        not_comparable.append(component)
        continue
    cas = cas_of.get(component)
    key = inchi_key(cas) if cas else None
    reference = DDBST.get(key) if key else None
    if reference is None:
        no_reference.append((component, cas))
        continue
    reference = {str(k): v for k, v in reference.items()}
    if normalise(assignment) == reference:
        agree.append(component)
    else:
        differ.append((component, normalise(assignment), reference))

total = len(agree) + len(differ)
print("=" * 78)
print("Validation of the DDBST source against rows NeqSim ALREADY has")
print("=" * 78)
print("comparable rows        : %d" % total)
print("  agree with DDBST     : %d" % len(agree))
print("  DISAGREE             : %d" % len(differ))
print("no DDBST reference     : %d" % len(no_reference))
print("not comparable (gas / cyclic-only NeqSim groups) : %d" % len(not_comparable))
if total:
    print("\nreproduction rate: %.1f%%" % (100.0 * len(agree) / total))

print("\n--- DISAGREEMENTS (NeqSim vs DDBST) ---")
for component, mine, theirs in differ:
    print("  %s" % component)
    print("      NeqSim : %s" % describe(mine, names))
    print("      DDBST  : %s" % describe(theirs, names))

print("\n--- no DDBST reference (cannot be checked automatically) ---")
for component, cas in no_reference:
    print("  %-26s CAS=%s" % (component, cas or "<none>"))
