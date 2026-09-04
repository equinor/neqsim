"""Screen the NeqSim UNIFAC data tables for internal and cross-table inconsistencies.

Read-only. Use --tsv to emit ``category<TAB>subject`` lines suitable as the
baseline consumed by UnifacDatabaseIntegrityTest.

Checks
------
group_param_mismatch  R, Q or main group disagrees with the DDBST published
                      original-UNIFAC table, which UNIFACGroupParam.csv already
                      cites via "Hansen1991".
duplicate_component   the same component name appears twice in one table; which
                      row wins is then decided by database row order.
unknown_subgroup      a subN column is populated for a subgroup that has no row
                      in UNIFACGroupParam.csv.
component_no_groups   a component row exists but assigns no groups at all, which
                      yields R = Q = 0 and NaN activity coefficients.
molar_mass_mismatch   the molar mass summed from the assigned groups disagrees
                      with COMP.csv MOLARMASS.
not_in_comp          a UNIFAC row whose name has no COMP.csv component, so it
                      can never be reached from addComponent.
aromatic_group_convention
                      an alkyl chain is attached to the ring through the bare AC
                      group. Hansen et al. (1991) name main group 4 "aromatic
                      carbon-alkane" and reserve AC for a ring carbon whose
                      substituent is not an alkane group.
ring_group_convention
                      a ring component uses the aliphatic CH2/CH/C groups rather
                      than the UMR-PRU cyclic cCH2/cCH/cC groups. Only applies to
                      UNIFACcompUMRPRU.csv; original UNIFAC has no cyclic groups,
                      so UNIFACcomp.csv is correct to use the aliphatic ones.
                      Recorded, not fixed: the two differ only against water,
                      CO2, CH4, N2, H2S, C2H6, Hg and TEG, and no citable source
                      states which applies to every naphthene.
"""

import collections
import csv
import pathlib
import sys

DATA = pathlib.Path("src/main/resources/data")
COMPONENT_TABLES = ("UNIFACcomp.csv", "UNIFACcompUMRPRU.csv")
RQ_TOLERANCE = 5e-4
MASS_TOLERANCE = 0.05

# subgroup -> (name, main group, R, Q) from the DDBST published original UNIFAC set.
DDBST_GROUPS = {
    1: ("CH3", 1, 0.9011, 0.8480), 2: ("CH2", 1, 0.6744, 0.5400),
    3: ("CH", 1, 0.4469, 0.2280), 4: ("C", 1, 0.2195, 0.0000),
    5: ("CH2=CH", 2, 1.3454, 1.1760), 6: ("CH=CH", 2, 1.1167, 0.8670),
    7: ("CH2=C", 2, 1.1173, 0.9880), 8: ("CH=C", 2, 0.8886, 0.6760),
    9: ("ACH", 3, 0.5313, 0.4000), 10: ("AC", 3, 0.3652, 0.1200),
    11: ("ACCH3", 4, 1.2663, 0.9680), 12: ("ACCH2", 4, 1.0396, 0.6600),
    13: ("ACCH", 4, 0.8121, 0.3480), 14: ("OH", 5, 1.0000, 1.2000),
    15: ("CH3OH", 6, 1.4311, 1.4320), 16: ("H2O", 7, 0.9200, 1.4000),
    17: ("ACOH", 8, 0.8952, 0.6800), 18: ("CH3CO", 9, 1.6724, 1.4880),
    19: ("CH2CO", 9, 1.4457, 1.1800), 20: ("CHO", 10, 0.9980, 0.9480),
    21: ("CH3COO", 11, 1.9031, 1.7280), 22: ("CH2COO", 11, 1.6764, 1.4200),
    23: ("HCOO", 12, 1.2420, 1.1880), 24: ("CH3O", 13, 1.1450, 1.0880),
    25: ("CH2O", 13, 0.9183, 0.7800), 26: ("CHO", 13, 0.6908, 0.4680),
    27: ("THF", 13, 0.9183, 1.1000), 28: ("CH3NH2", 14, 1.5959, 1.5440),
    29: ("CH2NH2", 14, 1.3692, 1.2360), 30: ("CHNH2", 14, 1.1417, 0.9240),
    31: ("CH3NH", 15, 1.4337, 1.2440), 32: ("CH2NH", 15, 1.2070, 0.9360),
    33: ("CHNH", 15, 0.9795, 0.6240), 34: ("CH3N", 16, 1.1865, 0.9400),
    35: ("CH2N", 16, 0.9597, 0.6320), 36: ("ACNH2", 17, 1.0600, 0.8160),
    37: ("C5H5N", 18, 2.9993, 2.1130), 38: ("C5H4N", 18, 2.8332, 1.8330),
    39: ("C5H3N", 18, 2.6670, 1.5530), 40: ("CH3CN", 19, 1.8701, 1.7240),
    41: ("CH2CN", 19, 1.6434, 1.4160), 42: ("COOH", 20, 1.3013, 1.2240),
    43: ("HCOOH", 20, 1.5280, 1.5320), 44: ("CH2CL", 21, 1.4654, 1.2640),
    45: ("CHCL", 21, 1.2380, 0.9520), 46: ("CCL", 21, 1.0106, 0.7240),
    47: ("CH2CL2", 22, 2.2564, 1.9880), 48: ("CHCL2", 22, 2.0606, 1.6840),
    49: ("CCL2", 22, 1.8016, 1.4480), 50: ("CHCL3", 23, 2.8700, 2.4100),
    51: ("CCL3", 23, 2.6401, 2.1840), 52: ("CCL4", 24, 3.3900, 2.9100),
    53: ("ACCL", 25, 1.1562, 0.8440), 54: ("CH3NO2", 26, 2.0086, 1.8680),
    55: ("CH2NO2", 26, 1.7818, 1.5600), 56: ("CHNO2", 26, 1.5544, 1.2480),
    57: ("ACNO2", 27, 1.4199, 1.1040), 58: ("CS2", 28, 2.0570, 1.6500),
    59: ("CH3SH", 29, 1.8770, 1.6760), 60: ("CH2SH", 29, 1.6510, 1.3680),
    61: ("FURFURAL", 30, 3.1680, 2.4840), 62: ("DOH", 31, 2.4088, 2.2480),
    63: ("I", 32, 1.2640, 0.9920), 64: ("BR", 33, 0.9492, 0.8320),
    70: ("C=C", 2, 0.6605, 0.4850),
}

# Molar mass implied by each subgroup, in g/mol. Groups whose composition is not
# unambiguous are omitted; a component using one of those is not mass-checked.
SUBGROUP_MASS = {
    "1": 15.0345, "2": 14.0266, "3": 13.0186, "4": 12.0110,
    "5": 27.0453, "6": 26.0373, "7": 26.0373, "8": 25.0294, "70": 24.0220,
    "9": 13.0186, "10": 12.0110, "11": 27.0453, "12": 26.0373, "13": 25.0294,
    "14": 17.0073, "15": 32.0419, "16": 18.0153,
    "24": 31.0339, "25": 30.0260, "26": 29.0180, "62": 62.0678,
    "120": 17.0305, "121": 44.0095, "122": 16.0425, "123": 31.9988,
    "124": 39.9480, "125": 28.0134, "126": 34.0809, "127": 2.0159,
    "128": 28.0101, "134": 30.0690,
    "136": 14.0266, "137": 13.0186, "138": 12.0110,
    "139": 62.0678, "140": 150.1730,
}


ALIPHATIC_SUBGROUPS = ("1", "2", "3", "4")
CYCLIC_SUBGROUPS = ("136", "137", "138")
AROMATIC_ALKANE_SUBGROUPS = ("11", "12", "13")
BARE_AROMATIC_CARBON = "10"
RING_NAME_MARKERS = ("cy-c", "cyc", "cychexane", "-cy", "c-c")


def read_table(filename):
    """Return the rows of a NeqSim data table as dictionaries."""
    with (DATA / filename).open(encoding="utf-8", newline="") as handle:
        return list(csv.DictReader(handle))


def assignment_of(row):
    """Return {subgroup number as str: count} for a component table row."""
    groups = {}
    for column, value in row.items():
        if column and column.startswith("sub") and value and value.strip() not in ("", "0"):
            groups[column[3:]] = int(value)
    return groups


def looks_like_ring(name):
    """Return True when the component name marks it as a naphthene in this database."""
    lowered = name.lower()
    if lowered.startswith("c-c") or lowered.startswith("cy-c"):
        return True
    return any(marker in lowered for marker in ("cy-c", "cyc", "chexane"))


def screen():
    """Return the list of (category, subject) findings across the UNIFAC tables."""
    findings = []

    known = {}
    for row in read_table("UNIFACGroupParam.csv"):
        known[row["Secondary"]] = row
        reference = DDBST_GROUPS.get(int(row["Secondary"]))
        if reference is None:
            continue
        _, ref_main, ref_r, ref_q = reference
        if (
            abs(float(row["VolumeR"]) - ref_r) > RQ_TOLERANCE
            or abs(float(row["SurfAreaQ"]) - ref_q) > RQ_TOLERANCE
            or int(row["Main"]) != ref_main
        ):
            findings.append(("group_param_mismatch", "sub%s %s" % (row["Secondary"], row["Name"])))

    comp = {row["NAME"].strip(): row for row in read_table("COMP.csv")}

    for filename in COMPONENT_TABLES:
        table = filename.replace(".csv", "")
        rows = read_table(filename)
        counts = collections.Counter(row["Name"].strip() for row in rows)
        for name, count in sorted(counts.items()):
            if count > 1:
                findings.append(("duplicate_component", "%s/%s" % (table, name)))

        for row in rows:
            name = row["Name"].strip()
            groups = assignment_of(row)
            if not groups:
                findings.append(("component_no_groups", "%s/%s" % (table, name)))
                continue
            for subgroup in sorted(groups, key=int):
                if subgroup not in known:
                    findings.append(("unknown_subgroup", "%s/%s sub%s" % (table, name, subgroup)))
            record = comp.get(name)
            if record is None:
                findings.append(("not_in_comp", "%s/%s" % (table, name)))
                continue
            if any(subgroup not in SUBGROUP_MASS for subgroup in groups):
                continue
            implied = sum(SUBGROUP_MASS[sub] * n for sub, n in groups.items())
            if abs(implied - float(record["MOLARMASS"])) > MASS_TOLERANCE:
                findings.append(("molar_mass_mismatch", "%s/%s" % (table, name)))

        for row in rows:
            name = row["Name"].strip()
            groups = assignment_of(row)
            has_bare_aromatic = groups.get(BARE_AROMATIC_CARBON, 0) > 0
            has_aromatic_alkane = any(groups.get(sub, 0) > 0 for sub in AROMATIC_ALKANE_SUBGROUPS)
            has_aliphatic = any(groups.get(sub, 0) > 0 for sub in ALIPHATIC_SUBGROUPS)
            if has_bare_aromatic and has_aliphatic and not has_aromatic_alkane:
                findings.append(("aromatic_group_convention", "%s/%s" % (table, name)))
            if looks_like_ring(name) and has_aliphatic and table == "UNIFACcompUMRPRU" and not any(
                groups.get(sub, 0) > 0 for sub in CYCLIC_SUBGROUPS
            ):
                findings.append(("ring_group_convention", "%s/%s" % (table, name)))

    return sorted(set(findings))


def main():
    """Print the findings, either as a human report or as baseline TSV."""
    findings = screen()
    if "--tsv" in sys.argv:
        for category, subject in findings:
            sys.stdout.write("%s\t%s\n" % (category, subject))
        return
    grouped = collections.OrderedDict()
    for category, subject in findings:
        grouped.setdefault(category, []).append(subject)
    print("total findings: %d" % len(findings))
    for category in sorted(grouped):
        print("\n%s (%d)" % (category, len(grouped[category])))
        for subject in grouped[category]:
            print("   %s" % subject)


main()
