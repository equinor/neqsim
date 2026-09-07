"""Which hydrocarbons in COMP.csv still have no row in the UMR-PRU group table.

The requirement is that every hydrocarbon usable with UMR-PRU has a group assignment,
so this reports the remaining gap and why each one is still open.

Usage:
    python devtools/report_hc_umrpru_gap.py
"""

from __future__ import annotations

import csv
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "src" / "main" / "resources" / "data"

# Names that are placeholders or duplicates rather than distinct substances.
NOT_A_SUBSTANCE = {"default"}


def main() -> None:
    umrpru = {row["Name"].strip() for row in csv.DictReader((DATA / "UNIFACcompUMRPRU.csv").open(encoding="utf-8"))}

    hydrocarbons = []
    for row in csv.DictReader((DATA / "COMP.csv").open(encoding="utf-8")):
        if row["COMPTYPE"].strip() != "HC":
            continue
        hydrocarbons.append(row)

    missing = [r for r in hydrocarbons if r["NAME"].strip() not in umrpru]

    print(f"HC components in COMP.csv        : {len(hydrocarbons)}")
    print(f"of which present in UMR-PRU table: {len(hydrocarbons) - len(missing)}")
    print(f"still missing                    : {len(missing)}")
    print()
    print(f"{'component':<24}{'CAS':<14}{'formula':<10}{'MW':>10}   note")
    for row in sorted(missing, key=lambda r: r["NAME"].strip()):
        name = row["NAME"].strip()
        note = ""
        if name in NOT_A_SUBSTANCE:
            note = "placeholder, not a substance"
        elif "PVTsim" in name:
            note = "duplicate of a canonical row"
        print(
            f"{name:<24}{row['CASnumber'].strip():<14}{row['FORMULA'].strip():<10}"
            f"{float(row['MOLARMASS']):>10.3f}   {note}"
        )


if __name__ == "__main__":
    main()
