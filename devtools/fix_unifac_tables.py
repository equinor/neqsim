"""Repair the NeqSim UNIFAC data tables.

Dry-run by default; pass --apply to write.

Fixes, each independently justified:

UNIFACGroupParam.csv - six R/Q values disagree with the DDBST published original
UNIFAC table (https://www.ddbst.com/published-parameters-unifac.html), which is
the source this file already cites via "Hansen1991". The error fingerprints are
a block copy (CH2Cl/CHCl took CH2Cl2/CHCl2's R), digit transpositions (CS2,
HCOO) and a row shift (Furfural took CS2's Q).

UNIFACcomp.csv / UNIFACcompUMRPRU.csv - stray groups and duplicate component
names. Duplicates are resolved by molar mass and skeleton, never by position.
"""

import csv
import pathlib
import sys

DATA = pathlib.Path("src/main/resources/data")
APPLY = "--apply" in sys.argv

# secondary subgroup -> (column, current value, corrected value, justification)
GROUP_PARAM_FIXES = [
    ("19", "VolumeR", "1.335700000", "1.445700000", "CH2CO R; DDBST 1.4457"),
    ("23", "SurfAreaQ", "1.189800000", "1.188000000", "HCOO Q; DDBST 1.1880"),
    ("44", "VolumeR", "2.256400000", "1.465400000", "CH2Cl R; had CH2Cl2's 2.2564, DDBST 1.4654"),
    ("45", "VolumeR", "2.060600000", "1.238000000", "CHCl R; had CHCl2's 2.0606, DDBST 1.2380"),
    ("58", "VolumeR", "2.507000000", "2.057000000", "CS2 R; digit transposition, DDBST 2.0570"),
]

# MEG, DEG and TEG are deliberately left byte identical to master. sub61
# (Furfural) is referenced by exactly one component in the database, MEG, so
# correcting its Q would in practice be a MEG change. That correction, the stray
# Furfural group on MEG and the duplicate TEG row are recorded in the baseline.

# (file, component name, subgroup column, justification) - stray group to clear
STRAY_GROUPS = [
    ("UNIFACcompUMRPRU.csv", "cis-12-DM-cy-C6", "sub16", "water group in a C8H16 naphthene"),
    ("UNIFACcompUMRPRU.csv", "trans-12-DM-cy-C6", "sub16", "water group in a C8H16 naphthene"),
    ("UNIFACcomp.csv", "cis-12-DM-cy-C6", "sub16", "water group in a C8H16 naphthene"),
    ("UNIFACcomp.csv", "trans-12-DM-cy-C6", "sub16", "water group in a C8H16 naphthene"),
]

# (file, component name, subgroup column, expected current value, new value, justification)
SET_GROUPS = [
    ("UNIFACcomp.csv", "oxygen", "sub125", "1", "0", "oxygen was assigned the N2 group"),
    ("UNIFACcomp.csv", "oxygen", "sub123", "0", "1", "oxygen must use the O2 group"),
    ("UNIFACcompUMRPRU.csv", "oxygen", "sub125", "1", "0", "oxygen was assigned the N2 group"),
    ("UNIFACcompUMRPRU.csv", "oxygen", "sub123", "0", "1", "oxygen must use the O2 group"),
    ("UNIFACcomp.csv", "E-cy-C5", "sub2", "6", "5", "ethylcyclopentane C7H14: CH3+5*CH2+CH, not CH3+6*CH2"),
    ("UNIFACcomp.csv", "E-cy-C5", "sub3", "0", "1", "ring carbon bearing the ethyl group is a CH"),
    ("UNIFACcompUMRPRU.csv", "E-cy-C5", "sub2", "6", "5", "ethylcyclopentane C7H14: CH3+5*CH2+CH, not CH3+6*CH2"),
    ("UNIFACcompUMRPRU.csv", "E-cy-C5", "sub3", "0", "1", "ring carbon bearing the ethyl group is a CH"),
]

# (file, component name, CompNumber to DROP, justification)
DROP_ROWS = [
    ("UNIFACcomp.csv", "c-C7", "133", "exact duplicate of CompNumber 69"),
    ("UNIFACcomp.csv", "c-C8", "132", "exact duplicate of CompNumber 73"),
    ("UNIFACcompUMRPRU.csv", "c-C7", "133", "exact duplicate of CompNumber 72"),
    (
        "UNIFACcompUMRPRU.csv",
        "23-DM-C5",
        "134",
        "CH3x4+CH2x2+Cx1 has a quaternary carbon; 2,3-dimethylpentane has two CH (CompNumber 31)",
    ),
]


# Alkylbenzene rows that put an alkyl-substituted aromatic carbon in the bare AC
# group. Hansen et al. (1991) name main group 4 "aromatic carbon-alkane" and give
# ethylbenzene as 5*ACH + 1*ACCH2 + 1*CH3; AC is reserved for a ring carbon whose
# substituent is not an alkane group (styrene, naphthalene fusion). Converting
# AC + CH2 into ACCH2 preserves molar mass exactly.
ALKYLBENZENE_FIXES = [
    ("nC5-Benzene", 4),
    ("nC6-Benzene", 5),
    ("nC7-Benzene", 6),
    ("nC8-Benzene", 7),
    ("nC9-Benzene", 8),
    ("nC10-Benzene", 9),
]

# 1,2,3-trimethylbenzene has the same defect in the ACCH3 form: three methyls
# attached through bare AC. m-Xylene in the same table is already ACH x4 +
# ACCH3 x2. Converting CH3 + AC into ACCH3 preserves molar mass exactly.
METHYLBENZENE_FIXES = [
    ("1.2.3-TM-Benzene", 3),
]

# Component names that use commas where COMP.csv uses dots. The lookup is
# "WHERE Name = '<COMP.csv name>'", so these rows are unreachable and the
# component silently ends up with zero UNIFAC groups.
RENAMES = [
    "1,1,3-TM-cy-C6",
    "1,2,3-TM-Benzene",
    "1,2,3-TMcyC6",
    "1,2,4-TMcyC6",
    "1,2-DM-cyC5",
    "1,3-dDM-cyC5",
    "1,cis-2,trans-4-TMcyC5",
    "2,2-DM-C7",
    "2,3-DM-C6",
    "2,6-DM-C7",
    "3-M-4,4-DE-heptane",
]


def read_lines(path):
    """Return the file's lines without terminators, preserving content exactly."""
    return path.read_text(encoding="utf-8").replace("\r\n", "\n").split("\n")


def write_lines(path, lines):
    """Write lines back with LF terminators and no BOM."""
    path.write_bytes("\n".join(lines).encode("utf-8"))


def header_index(header_line, column):
    """Return the position of a column in a quoted CSV header line."""
    fields = [f.strip().strip('"') for f in header_line.split(",")]
    return fields.index(column)


def fix_group_params():
    """Correct the six R/Q values that disagree with DDBST."""
    path = DATA / "UNIFACGroupParam.csv"
    lines = read_lines(path)
    secondary_at = header_index(lines[0], "Secondary")
    changed = 0
    for secondary, column, old, new, why in GROUP_PARAM_FIXES:
        column_at = header_index(lines[0], column)
        for index, line in enumerate(lines[1:], start=1):
            if not line.strip():
                continue
            fields = line.split(",")
            if fields[secondary_at].strip().strip('"') != secondary:
                continue
            actual = fields[column_at].strip()
            if actual != old:
                print("  SKIP sub%-4s %-10s expected %s but found %s" % (secondary, column, old, actual))
                break
            fields[column_at] = new
            lines[index] = ",".join(fields)
            changed += 1
            print("  sub%-4s %-10s %s -> %s   (%s)" % (secondary, column, old, new, why))
            break
    return path, lines, changed


def fix_component_table(filename):
    """Clear stray groups and drop duplicate rows in one component table."""
    path = DATA / filename
    lines = read_lines(path)
    name_at = header_index(lines[0], "Name")
    number_at = header_index(lines[0], "CompNumber")
    changed = 0

    for target_file, name, column, why in STRAY_GROUPS:
        if target_file != filename:
            continue
        column_at = header_index(lines[0], column)
        for index, line in enumerate(lines[1:], start=1):
            if not line.strip():
                continue
            fields = line.split(",")
            if fields[name_at].strip().strip('"') != name:
                continue
            if fields[column_at].strip() in ("", "0"):
                print("  SKIP %-20s %s already clear" % (name, column))
                break
            print("  %-20s clear %s (was %s)   (%s)" % (name, column, fields[column_at].strip(), why))
            fields[column_at] = "0"
            lines[index] = ",".join(fields)
            changed += 1
            break

    for target_file, name, column, old, new, why in SET_GROUPS:
        if target_file != filename:
            continue
        column_at = header_index(lines[0], column)
        for index, line in enumerate(lines[1:], start=1):
            if not line.strip():
                continue
            fields = line.split(",")
            if fields[name_at].strip().strip('"') != name:
                continue
            actual = fields[column_at].strip()
            if actual != old:
                print("  SKIP %-20s %-7s expected %s but found %s" % (name, column, old, actual))
                break
            print("  %-20s %-7s %s -> %s   (%s)" % (name, column, old, new, why))
            fields[column_at] = new
            lines[index] = ",".join(fields)
            changed += 1
            break

    drop = set()
    for target_file, name, number, why in DROP_ROWS:
        if target_file != filename:
            continue
        for index, line in enumerate(lines[1:], start=1):
            if not line.strip():
                continue
            fields = line.split(",")
            if fields[name_at].strip().strip('"') != name:
                continue
            if fields[number_at].strip().strip('"') != number:
                continue
            drop.add(index)
            print("  drop row CompNumber=%-5s %-20s (%s)" % (number, name, why))
            changed += 1
            break

    lines = [line for index, line in enumerate(lines) if index not in drop]

    ch2_at = header_index(lines[0], "sub2")
    ac_at = header_index(lines[0], "sub10")
    acch2_at = header_index(lines[0], "sub12")
    for name, ch2_count in ALKYLBENZENE_FIXES:
        for index, line in enumerate(lines[1:], start=1):
            fields = line.split(",")
            if fields[name_at].strip().strip('"') != name:
                continue
            if fields[ac_at].strip() != "1" or fields[ch2_at].strip() != str(ch2_count):
                print("  SKIP %-20s expected AC=1 CH2=%d, found AC=%s CH2=%s"
                      % (name, ch2_count, fields[ac_at].strip(), fields[ch2_at].strip()))
                break
            fields[ac_at] = "0"
            fields[acch2_at] = "1"
            fields[ch2_at] = str(ch2_count - 1)
            lines[index] = ",".join(fields)
            print("  %-20s AC x1 + CH2 x%d  ->  ACCH2 x1 + CH2 x%d   (main group 4 is aromatic carbon-alkane)"
                  % (name, ch2_count, ch2_count - 1))
            changed += 1
            break

    ch3_at = header_index(lines[0], "sub1")
    acch3_at = header_index(lines[0], "sub11")
    for name, methyl_count in METHYLBENZENE_FIXES:
        for index, line in enumerate(lines[1:], start=1):
            fields = line.split(",")
            if fields[name_at].strip().strip('"') != name:
                continue
            expected = str(methyl_count)
            if fields[ac_at].strip() != expected or fields[ch3_at].strip() != expected:
                print("  SKIP %-20s expected AC=CH3=%d, found AC=%s CH3=%s"
                      % (name, methyl_count, fields[ac_at].strip(), fields[ch3_at].strip()))
                break
            fields[ac_at] = "0"
            fields[ch3_at] = "0"
            fields[acch3_at] = expected
            lines[index] = ",".join(fields)
            print("  %-20s AC x%d + CH3 x%d  ->  ACCH3 x%d   (main group 4 is aromatic carbon-alkane)"
                  % (name, methyl_count, methyl_count, methyl_count))
            changed += 1
            break

    for old_name in RENAMES:
        new_name = old_name.replace(",", ".")
        quoted_old = '"%s"' % old_name
        quoted_new = '"%s"' % new_name
        for index, line in enumerate(lines[1:], start=1):
            if quoted_old not in line:
                continue
            lines[index] = line.replace(quoted_old, quoted_new, 1)
            print("  rename %-24s -> %-24s (unreachable: COMP.csv uses dots)" % (old_name, new_name))
            changed += 1
            break

    return path, lines, changed


print("UNIFACGroupParam.csv")
pending = [fix_group_params()]
for filename in ("UNIFACcomp.csv", "UNIFACcompUMRPRU.csv"):
    print("\n%s" % filename)
    pending.append(fix_component_table(filename))

total = sum(count for _, _, count in pending)
print("\ntotal changes: %d" % total)
if APPLY:
    for path, lines, count in pending:
        if count:
            write_lines(path, lines)
    print("APPLIED")
else:
    print("dry run - pass --apply to write")
