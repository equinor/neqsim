"""Add reviewed component rows to the UNIFAC group-assignment tables.

Every assignment below was taken from DDBST, keyed by the CAS number in COMP.csv,
and then checked by hand against the molecular structure. The list is written out
explicitly rather than generated at run time so that the diff of this file is the
review record for what entered the data.

Two properties of the target files matter and are preserved exactly:
UNIFACcomp.csv ends with a newline, UNIFACcompUMRPRU.csv does not, and both use
bare LF with no byte order mark. The files are therefore written as bytes; letting
PowerShell redirection touch them corrupts the encoding.

Usage:
    python devtools/add_unifac_component_rows.py --only propylbenzene
    python devtools/add_unifac_component_rows.py            # all reviewed rows
    python devtools/add_unifac_component_rows.py --check    # report, change nothing
"""

from __future__ import annotations

import argparse
import csv
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "src" / "main" / "resources" / "data"

# UMR-PRU takes priority, and its assignments follow NTUA/Voutsas rather than DDBST.
# UNIFACcomp.csv serves the original UNIFAC and UNIQUAC models and is left alone.
TARGET_FILES = ["UNIFACcompUMRPRU.csv"]

SUBGROUP_COLUMNS = 140
FIXED_COLUMNS = 4  # CompNumber, Name, qUNIQUAQ, rUNIQUAQ

# name -> (CAS, {subgroup: count}, note explaining the structure)
REVIEWED_ROWS = {
    "propylbenzene": ("103-65-1", {1: 1, 2: 1, 9: 5, 12: 1}, "C6H5-CH2CH2CH3; ring CH2 is ACCH2, chain CH2 is plain"),
    "naphthalene": ("91-20-3", {9: 8, 10: 2}, "fused ring, 8 aromatic CH and 2 bridgehead carbons"),
    "propene": ("115-07-1", {1: 1, 5: 1}, "CH3-CH=CH2"),
    "cis-butene": ("590-18-1", {1: 2, 6: 1}, "cis-2-butene; UNIFAC does not distinguish cis from trans"),
    "trans-butene": ("624-64-6", {1: 2, 6: 1}, "trans-2-butene; same groups as the cis isomer"),
    "iso-butene": ("115-11-7", {1: 2, 7: 1}, "2-methylpropene, CH2=C(CH3)2"),
    "nC20": ("112-95-8", {1: 2, 2: 18}, "n-eicosane"),
    "nC21": ("629-94-7", {1: 2, 2: 19}, "n-heneicosane"),
    "nC24": ("646-31-1", {1: 2, 2: 22}, "n-tetracosane"),
    "nC25": ("629-99-2", {1: 2, 2: 23}, "n-pentacosane"),
    "nC26": ("630-01-3", {1: 2, 2: 24}, "n-hexacosane"),
    "nC28": ("630-02-4", {1: 2, 2: 26}, "n-octacosane"),
    "nC29": ("630-03-5", {1: 2, 2: 27}, "n-nonacosane"),
    "formic acid": ("64-18-6", {43: 1}, "HCOOH has its own group"),
    "ethanol": ("64-17-5", {1: 1, 2: 1, 14: 1}, "CH3-CH2-OH"),
    "1-propanol": ("71-23-8", {1: 1, 2: 2, 14: 1}, "CH3-CH2-CH2-OH"),
    "i-propanol": ("67-63-0", {1: 2, 3: 1, 14: 1}, "(CH3)2CH-OH"),
    "MEA": ("141-43-5", {2: 1, 14: 1, 29: 1}, "monoethanolamine, HO-CH2-CH2-NH2"),
    "PG": ("57-55-6", {1: 1, 2: 1, 3: 1, 14: 2}, "propylene glycol, CH3-CH(OH)-CH2-OH"),
    # Naphthenes. Ring carbons take the Voutsas2017 cyclic groups cCH2 (136) and cCH (137),
    # which is also what the DDBST modified-UNIFAC assignment set does: it gives cyclohexane
    # {CY-CH2: 6}, methylcyclohexane {CH3: 1, CY-CH2: 5, CY-CH: 1} and n-butylcyclohexane
    # {CH3: 1, CH2: 3, CY-CH2: 5, CY-CH: 1}.
    "c-propane": ("75-19-4", {136: 3}, "cyclopropane; DDBST modified UNIFAC gives {CY-CH2: 3}"),
    "c-C4": ("287-23-0", {136: 4}, "cyclobutane; DDBST modified UNIFAC gives {CY-CH2: 4}"),
    "n-Bcychexane": ("1678-93-9", {1: 1, 2: 3, 136: 5, 137: 1}, "n-butylcyclohexane; matches DDBST"),
    "Pent-CC6": ("4292-92-6", {1: 1, 2: 4, 136: 5, 137: 1}, "pentylcyclohexane; matches DDBST"),
    "cis-14-DM-cy-C6": ("624-29-3", {1: 2, 136: 4, 137: 2}, "cis-1,4-dimethylcyclohexane"),
    "trans-14-DM-cy-C6": ("2207-04-7", {1: 2, 136: 4, 137: 2}, "trans isomer; UNIFAC has no stereochemistry"),
    # Gases carried as a single dedicated group, as methane, N2, CO2 and H2S already are.
    "argon": ("7440-37-1", {124: 1}, "Ar group 124, Fisher1995"),
    "hydrogen": ("1333-74-0", {127: 1}, "H2 group 127, Holderbaum1991"),
    "ortho-hydrogen": ("1333-74-0", {127: 1}, "spin isomer of H2; identical structure, same group"),
    "para-hydrogen": ("1333-74-0", {127: 1}, "spin isomer of H2; identical structure, same group"),
    # Alias rows kept in COMP.csv for PVTsim compatibility; same substance, same groups.
    "propanePVTsim": ("74-98-6", {1: 2, 2: 1}, "alias of propane"),
    "nbutanePVTsim": ("106-97-8", {1: 2, 2: 2}, "alias of n-butane"),
}


def read_lines(path: Path) -> list:
    return path.read_bytes().decode("utf-8").split("\n")


def existing_names(lines: list) -> set:
    names = set()
    for line in lines[1:]:
        if not line.strip():
            continue
        fields = line.split(",")
        if len(fields) > 1:
            names.add(fields[1].strip('"'))
    return names


def next_component_number(lines: list) -> int:
    highest = 0
    for line in lines[1:]:
        if not line.strip():
            continue
        try:
            highest = max(highest, int(line.split(",")[0].strip('"')))
        except (ValueError, IndexError):
            continue
    return highest + 1


def build_row(component_number: int, name: str, groups: dict) -> str:
    counts = ["0"] * SUBGROUP_COLUMNS
    for subgroup, count in groups.items():
        if not 1 <= subgroup <= SUBGROUP_COLUMNS:
            raise ValueError(f"subgroup {subgroup} for {name} is outside 1..{SUBGROUP_COLUMNS}")
        counts[subgroup - 1] = str(count)
    # q and r are stored as zero throughout these tables; they are derived from the
    # group assignment at run time rather than read from the file.
    return ",".join([str(component_number), f'"{name}"', "0.000000000", "0.000000000"] + counts)


def verify_written(path: Path, name: str, groups: dict) -> None:
    with path.open(newline="", encoding="utf-8") as handle:
        for row in csv.DictReader(handle):
            if row["Name"] != name:
                continue
            found = {}
            for key, value in row.items():
                if key and key.startswith("sub"):
                    try:
                        count = int(float(value))
                    except (TypeError, ValueError):
                        continue
                    if count > 0:
                        found[int(key[3:])] = count
            if found != groups:
                raise AssertionError(f"{path.name}: {name} read back as {found}, expected {groups}")
            return
    raise AssertionError(f"{path.name}: {name} not found after writing")


def process(filename: str, wanted: dict, check_only: bool) -> None:
    path = DATA / filename
    raw = path.read_bytes()
    trailing_newline = raw.endswith(b"\n")
    lines = read_lines(path)
    if lines and lines[-1] == "":
        lines = lines[:-1]

    present = existing_names(lines)
    header_columns = len(lines[0].split(","))
    if header_columns != FIXED_COLUMNS + SUBGROUP_COLUMNS:
        raise AssertionError(f"{filename}: expected {FIXED_COLUMNS + SUBGROUP_COLUMNS} columns, found {header_columns}")

    to_add = [(name, groups) for name, (_cas, groups, _note) in wanted.items() if name not in present]
    skipped = [name for name in wanted if name in present]

    print(f"\n{filename}")
    print(f"  rows now      : {len(lines) - 1}")
    print(f"  already there : {len(skipped)}" + (f"  {sorted(skipped)}" if skipped else ""))
    print(f"  to add        : {len(to_add)}")

    if check_only or not to_add:
        return

    number = next_component_number(lines)
    for name, groups in to_add:
        lines.append(build_row(number, name, groups))
        print(f"    + {number:>4}  {name}")
        number += 1

    body = "\n".join(lines) + ("\n" if trailing_newline else "")
    path.write_bytes(body.encode("utf-8"))

    for name, groups in to_add:
        verify_written(path, name, groups)
    print(f"  written and read back clean ({len(to_add)} rows)")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--only", nargs="*", help="restrict to these component names")
    parser.add_argument("--check", action="store_true", help="report what would change, write nothing")
    args = parser.parse_args()

    wanted = REVIEWED_ROWS
    if args.only:
        unknown = [name for name in args.only if name not in REVIEWED_ROWS]
        if unknown:
            raise SystemExit(f"not in the reviewed list: {unknown}")
        wanted = {name: REVIEWED_ROWS[name] for name in args.only}

    print(f"reviewed rows available : {len(REVIEWED_ROWS)}")
    print(f"selected                : {len(wanted)}")
    for filename in TARGET_FILES:
        process(filename, wanted, args.check)


if __name__ == "__main__":
    main()
