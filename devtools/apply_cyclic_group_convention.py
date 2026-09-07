"""Apply the cyclic group convention to the naphthenes in the UMR-PRU table.

UMR-PRU carries dedicated subgroups for ring carbons: cCH2 (136), cCH (137) and
cC (138), from Voutsas. Their volume and surface area parameters are identical to
the acyclic CH2, CH and C, so the only thing they change is which row of the
interaction matrix applies. That is also the entire point of them: the UMR-PRU
parameterisation gives ring carbons different interactions with water, CO2, CH4,
N2, H2S and C2H6 than chain carbons. A naphthene assigned plain CH2 silently
discards that.

Main groups 66, 67 and 68 have interaction rows only in UNIFACInterParamA_UMRMC,
which is what PhaseGEUnifacUMRPRU reads. They have none in UNIFACInterParam, so
the classic UNIFACcomp.csv table must keep using CH2, CH and C. This script
therefore touches UNIFACcompUMRPRU.csv only.

Only ring atoms convert. A substituent stays acyclic, so ethylcyclohexane keeps
one chain CH2 while five ring CH2 become cCH2, and the long-chain alkylcyclo-
pentanes keep the whole chain acyclic. The ring and chain counts below were
worked out per molecule from its name and checked against the carbon and
hydrogen totals of the row being replaced.

Usage:
    python devtools/apply_cyclic_group_convention.py --check
    python devtools/apply_cyclic_group_convention.py
"""

from __future__ import annotations

import argparse
import csv
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TABLE = ROOT / "src" / "main" / "resources" / "data" / "UNIFACcompUMRPRU.csv"

SUBGROUP_COLUMNS = 140

# Carbon and hydrogen contributed by each subgroup, used to prove the rewrite
# conserves the molecular formula.
ATOMS = {1: (1, 3), 2: (1, 2), 3: (1, 1), 4: (1, 0), 136: (1, 2), 137: (1, 1), 138: (1, 0)}

# name -> corrected assignment. Ring atoms use 136/137/138, substituents keep 1/2/3/4.
CORRECTED = {
    # Unsubstituted rings: every carbon is a ring carbon.
    "c-C7": {136: 7},
    "c-C8": {136: 8},
    "c-C8de": {136: 8},
    "cy-C9": {136: 9},
    # Monoalkyl cyclopentanes and cyclohexanes: ring CH2 plus the ring CH bearing
    # the substituent, with the substituent itself left acyclic.
    "M-cy-C5": {1: 1, 136: 4, 137: 1},
    "M-cy-C6": {1: 1, 136: 5, 137: 1},
    "E-cy-C5": {1: 1, 2: 1, 136: 4, 137: 1},
    "ethylcyclohexane": {1: 1, 2: 1, 136: 5, 137: 1},
    "i-p-cy-C5": {1: 2, 3: 1, 136: 4, 137: 1},
    "nC10-cy-C5": {1: 1, 2: 9, 136: 4, 137: 1},
    "nC12-cy-C5": {1: 1, 2: 11, 136: 4, 137: 1},
    "nC14-cy-C5": {1: 1, 2: 13, 136: 4, 137: 1},
    "nC17-cy-C5": {1: 1, 2: 16, 136: 4, 137: 1},
    # Dimethyl cyclopentanes: three ring CH2 and two ring CH.
    "1.2-DM-cyC5": {1: 2, 136: 3, 137: 2},
    "1.3-dDM-cyC5": {1: 2, 136: 3, 137: 2},
    "trans-12-DM-cy-C5": {1: 2, 136: 3, 137: 2},
    "cis-13-DM-cy-C5": {1: 2, 136: 3, 137: 2},
    "trans-13-DM-cy-C5": {1: 2, 136: 3, 137: 2},
    # Geminal dimethyl: the substituted ring carbon is quaternary.
    "11-DM-cy-C5": {1: 2, 136: 4, 138: 1},
    # Dimethyl cyclohexanes: four ring CH2 and two ring CH.
    "cis-12-DM-cy-C6": {1: 2, 136: 4, 137: 2},
    "trans-12-DM-cy-C6": {1: 2, 136: 4, 137: 2},
    "cis-13-DM-cy-C6": {1: 2, 136: 4, 137: 2},
    # Trimethyl rings.
    "113-TM-cy-C5": {1: 3, 136: 3, 137: 1, 138: 1},
    "1.cis-2.trans-4-TMcyC5": {1: 3, 136: 2, 137: 3},
    "1.1.3-TM-cy-C6": {1: 3, 136: 4, 137: 1, 138: 1},
    "1.2.3-TMcyC6": {1: 3, 136: 3, 137: 3},
    "1.2.4-TMcyC6": {1: 3, 136: 3, 137: 3},
}


def assignment_of(row: dict) -> dict:
    groups = {}
    for key, value in row.items():
        if not key or not key.startswith("sub"):
            continue
        try:
            count = int(float(value))
        except (TypeError, ValueError):
            continue
        if count > 0:
            groups[int(key[3:])] = count
    return groups


def atom_totals(groups: dict):
    carbon = sum(ATOMS[s][0] * n for s, n in groups.items())
    hydrogen = sum(ATOMS[s][1] * n for s, n in groups.items())
    return carbon, hydrogen


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="report without writing")
    args = parser.parse_args()

    with TABLE.open(newline="", encoding="utf-8") as handle:
        rows = list(csv.DictReader(handle))
    current = {row["Name"].strip(): assignment_of(row) for row in rows}

    unknown = [name for name in CORRECTED if name not in current]
    if unknown:
        raise SystemExit(f"not present in {TABLE.name}: {unknown}")

    changes = []
    for name, corrected in CORRECTED.items():
        existing = current[name]
        if existing == corrected:
            continue
        before = atom_totals(existing)
        after = atom_totals(corrected)
        if before != after:
            raise SystemExit(
                f"{name}: rewrite changes the molecular formula, C{before[0]}H{before[1]} "
                f"becomes C{after[0]}H{after[1]}"
            )
        changes.append((name, existing, corrected, before))

    print(f"naphthene rows to correct: {len(changes)}")
    print(f"{'component':<24}{'C':>3}{'H':>4}  {'before':<32}{'after'}")
    for name, existing, corrected, (carbon, hydrogen) in changes:
        print(f"{name:<24}{carbon:>3}{hydrogen:>4}  {str(existing):<32}{corrected}")

    if args.check or not changes:
        return

    raw = TABLE.read_bytes().decode("utf-8")
    trailing_newline = raw.endswith("\n")
    lines = raw.split("\n")
    if lines and lines[-1] == "":
        lines = lines[:-1]

    corrected_by_name = {name: corrected for name, _e, corrected, _t in changes}
    for index, line in enumerate(lines[1:], start=1):
        fields = line.split(",")
        name = fields[1].strip('"')
        if name not in corrected_by_name:
            continue
        counts = ["0"] * SUBGROUP_COLUMNS
        for subgroup, count in corrected_by_name[name].items():
            counts[subgroup - 1] = str(count)
        lines[index] = ",".join(fields[:4] + counts)

    TABLE.write_bytes(("\n".join(lines) + ("\n" if trailing_newline else "")).encode("utf-8"))

    with TABLE.open(newline="", encoding="utf-8") as handle:
        written = {row["Name"].strip(): assignment_of(row) for row in csv.DictReader(handle)}
    for name, corrected in corrected_by_name.items():
        if written[name] != corrected:
            raise AssertionError(f"{name} read back as {written[name]}, expected {corrected}")
    print(f"\nwrote and verified {len(changes)} rows")


if __name__ == "__main__":
    main()
