"""Rebuild the missing_unifac_row entries in the UNIFAC integrity baseline.

UnifacDatabaseIntegrityTest records every COMP.csv component that can be decomposed
into groups but has no row in a given UNIFAC table. After rows are added the baseline
has to shrink to match, otherwise the test fails on entries whose defect is fixed.

Only the missing_unifac_row category is regenerated. Every other entry is carried
across untouched, so this cannot silently accept an unrelated finding.

Usage:
    python devtools/sync_unifac_baseline.py
"""

from __future__ import annotations

import csv
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "src" / "main" / "resources" / "data"
BASELINE = ROOT / "src" / "test" / "resources" / "data" / "unifac_known_issues.tsv"

CATEGORY = "missing_unifac_row"

# Kept in step with NON_MOLECULAR_TYPES in UnifacDatabaseIntegrityTest.
NON_MOLECULAR_TYPES = {"ion", "ice", "seawater", "salt", "asphaltene"}

# Only UMR-PRU is in active use, and only its table is maintained. A component missing
# from the classic table is not a gap to be filled, so it is not recorded as one.
TABLES = [("UNIFACcompUMRPRU.csv", "UNIFACcompUMRPRU")]


def main() -> None:
    decomposable = []
    for row in csv.DictReader(DATA.joinpath("COMP.csv").open(encoding="utf-8")):
        name = row["NAME"].strip()
        if row["COMPTYPE"].strip() in NON_MOLECULAR_TYPES or name == "default":
            continue
        decomposable.append(name)

    entries = [
        line for line in BASELINE.read_text(encoding="utf-8").splitlines()
        if line.strip() and not line.startswith(CATEGORY + "\t")
    ]
    other = len(entries)

    missing = 0
    for filename, table in TABLES:
        present = {row["Name"].strip() for row in csv.DictReader(DATA.joinpath(filename).open(encoding="utf-8"))}
        for name in decomposable:
            if name not in present:
                entries.append(f"{CATEGORY}\t{table}/{name}")
                missing += 1

    entries = sorted(set(entries))
    BASELINE.write_bytes(("\n".join(entries) + "\n").encode("utf-8"))

    print(f"group-decomposable components : {len(decomposable)}")
    print(f"entries in other categories   : {other}")
    print(f"{CATEGORY} entries           : {missing}")
    print(f"baseline total                : {len(entries)}")


if __name__ == "__main__":
    main()
