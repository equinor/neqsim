"""Cross-check NeqSim's UNIFAC subgroup R and Q against the DDBST published values.

Read-only. Reference values transcribed from
https://www.ddbst.com/published-parameters-unifac.html
("List of Sub Groups and their Group Surfaces and Volumes"), which is the same
source NeqSim's UNIFACGroupParam.csv cites via "Hansen1991".

Only subgroups that exist in the published original-UNIFAC set are compared. The
PSRK gas groups (120-128, 134) and the UMR-PRU groups (135-140) have no entry
there and are reported as not comparable.
"""

import csv
import pathlib

DATA = pathlib.Path("src/main/resources/data")
TOLERANCE = 5e-4

# subgroup number -> (name, main group, R, Q) from DDBST published original UNIFAC
DDBST = {
    1: ("CH3", 1, 0.9011, 0.8480),
    2: ("CH2", 1, 0.6744, 0.5400),
    3: ("CH", 1, 0.4469, 0.2280),
    4: ("C", 1, 0.2195, 0.0000),
    5: ("CH2=CH", 2, 1.3454, 1.1760),
    6: ("CH=CH", 2, 1.1167, 0.8670),
    7: ("CH2=C", 2, 1.1173, 0.9880),
    8: ("CH=C", 2, 0.8886, 0.6760),
    9: ("ACH", 3, 0.5313, 0.4000),
    10: ("AC", 3, 0.3652, 0.1200),
    11: ("ACCH3", 4, 1.2663, 0.9680),
    12: ("ACCH2", 4, 1.0396, 0.6600),
    13: ("ACCH", 4, 0.8121, 0.3480),
    14: ("OH", 5, 1.0000, 1.2000),
    15: ("CH3OH", 6, 1.4311, 1.4320),
    16: ("H2O", 7, 0.9200, 1.4000),
    17: ("ACOH", 8, 0.8952, 0.6800),
    18: ("CH3CO", 9, 1.6724, 1.4880),
    19: ("CH2CO", 9, 1.4457, 1.1800),
    20: ("CHO", 10, 0.9980, 0.9480),
    21: ("CH3COO", 11, 1.9031, 1.7280),
    22: ("CH2COO", 11, 1.6764, 1.4200),
    23: ("HCOO", 12, 1.2420, 1.1880),
    24: ("CH3O", 13, 1.1450, 1.0880),
    25: ("CH2O", 13, 0.9183, 0.7800),
    26: ("CHO", 13, 0.6908, 0.4680),
    27: ("THF", 13, 0.9183, 1.1000),
    28: ("CH3NH2", 14, 1.5959, 1.5440),
    29: ("CH2NH2", 14, 1.3692, 1.2360),
    30: ("CHNH2", 14, 1.1417, 0.9240),
    31: ("CH3NH", 15, 1.4337, 1.2440),
    32: ("CH2NH", 15, 1.2070, 0.9360),
    33: ("CHNH", 15, 0.9795, 0.6240),
    34: ("CH3N", 16, 1.1865, 0.9400),
    35: ("CH2N", 16, 0.9597, 0.6320),
    36: ("ACNH2", 17, 1.0600, 0.8160),
    37: ("C5H5N", 18, 2.9993, 2.1130),
    38: ("C5H4N", 18, 2.8332, 1.8330),
    39: ("C5H3N", 18, 2.6670, 1.5530),
    40: ("CH3CN", 19, 1.8701, 1.7240),
    41: ("CH2CN", 19, 1.6434, 1.4160),
    42: ("COOH", 20, 1.3013, 1.2240),
    43: ("HCOOH", 20, 1.5280, 1.5320),
    44: ("CH2CL", 21, 1.4654, 1.2640),
    45: ("CHCL", 21, 1.2380, 0.9520),
    46: ("CCL", 21, 1.0106, 0.7240),
    47: ("CH2CL2", 22, 2.2564, 1.9880),
    48: ("CHCL2", 22, 2.0606, 1.6840),
    49: ("CCL2", 22, 1.8016, 1.4480),
    50: ("CHCL3", 23, 2.8700, 2.4100),
    51: ("CCL3", 23, 2.6401, 2.1840),
    52: ("CCL4", 24, 3.3900, 2.9100),
    53: ("ACCL", 25, 1.1562, 0.8440),
    54: ("CH3NO2", 26, 2.0086, 1.8680),
    55: ("CH2NO2", 26, 1.7818, 1.5600),
    56: ("CHNO2", 26, 1.5544, 1.2480),
    57: ("ACNO2", 27, 1.4199, 1.1040),
    58: ("CS2", 28, 2.0570, 1.6500),
    59: ("CH3SH", 29, 1.8770, 1.6760),
    60: ("CH2SH", 29, 1.6510, 1.3680),
    61: ("FURFURAL", 30, 3.1680, 2.4840),
    62: ("DOH", 31, 2.4088, 2.2480),
    63: ("I", 32, 1.2640, 0.9920),
    64: ("BR", 33, 0.9492, 0.8320),
    70: ("C=C", 2, 0.6605, 0.4850),
}


def main():
    """Compare every comparable NeqSim subgroup against the DDBST published value."""
    with (DATA / "UNIFACGroupParam.csv").open(encoding="utf-8", newline="") as handle:
        rows = list(csv.DictReader(handle))

    agree = 0
    problems = []
    skipped = []
    for row in rows:
        secondary = int(row["Secondary"])
        reference = DDBST.get(secondary)
        if reference is None:
            skipped.append((secondary, row["Name"], row["Reference"]))
            continue
        ref_name, ref_main, ref_r, ref_q = reference
        r_value = float(row["VolumeR"])
        q_value = float(row["SurfAreaQ"])
        issues = []
        if abs(r_value - ref_r) > TOLERANCE:
            issues.append("R %.4f vs DDBST %.4f" % (r_value, ref_r))
        if abs(q_value - ref_q) > TOLERANCE:
            issues.append("Q %.4f vs DDBST %.4f" % (q_value, ref_q))
        if int(row["Main"]) != ref_main:
            issues.append("main %s vs DDBST %d" % (row["Main"], ref_main))
        if issues:
            problems.append((secondary, row["Name"], ref_name, issues))
        else:
            agree += 1

    print("comparable subgroups : %d" % (agree + len(problems)))
    print("  agree with DDBST   : %d" % agree)
    print("  DISAGREE           : %d" % len(problems))
    print("not in published set : %d  (PSRK gas / UMR-PRU groups)" % len(skipped))

    print("\n--- DISAGREEMENTS ---")
    for secondary, name, ref_name, issues in problems:
        label = name if name.upper() == ref_name.upper() else "%s (DDBST: %s)" % (name, ref_name)
        print("  sub%-4d %-16s %s" % (secondary, label, "; ".join(issues)))

    print("\n--- not comparable ---")
    for secondary, name, source in skipped:
        print("  sub%-4d %-12s ref=%s" % (secondary, name, source))


main()
