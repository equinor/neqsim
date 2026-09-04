"""Correct COMP.csv rows whose properties are demonstrably another substance's.

Dry-run by default; pass --apply to write.

Each row below was adjudicated individually against the offline ``chemicals``
database. A blanket rule is not safe here, for two reasons found in this data:

  c-propane  the row's own properties match cyclopropane exactly (C3H6, MW
             42.081, Tc 124.65 C, Pc 54.92 bara). It is the CAS that is wrong,
             so the fix is the CAS, not the properties.
  MEACOO-    an ion sharing its parent amine's CAS by database convention. Its
             molar mass is correct for the carbamate and must not be replaced
             by the parent's.

TC, PC and ACSFACT are normally left alone because they may be tuned to a model.
They are corrected here only where the value is byte identical to another
component's, which is evidence of copying rather than tuning.
"""

import pathlib
import sys

DATA = pathlib.Path("src/main/resources/data")
APPLY = "--apply" in sys.argv

# component -> list of (column, expected current value, corrected value, why)
FIXES = {
    "c-propane": [
        ("CASnumber", "74-98-6", "75-19-4",
         "74-98-6 is propane; the row's C3H6 / 42.081 / 124.65 C / 54.92 bara is cyclopropane"),
    ],
    "propylbenzene": [
        ("MOLARMASS", "106.168", "120.1916", "106.168 is ethylbenzene's molar mass"),
        ("PC", "36.09", "32.0000", "36.09 is byte identical to ethylbenzene's PC"),
        ("ACSFACT", "0.304", "0.3440", "0.304 is byte identical to ethylbenzene's ACSFACT"),
        ("NORMBOIL", "58.05", "159.2000", "58.05 is the placeholder shared by 38 rows"),
    ],
    "3-M-C8": [
        ("MOLARMASS", "106.168", "128.2551", "106.168 is propylbenzene's molar mass"),
        ("FORMULA", "C", "C9H20", "formula was a bare C"),
        ("TC", "365.23", "317.0000", "365.23 is byte identical to propylbenzene's TC"),
        ("PC", "36.09", "23.4000", "36.09 is byte identical to propylbenzene's PC"),
        ("ACSFACT", "0.304", "0.4120", "0.304 is byte identical to propylbenzene's ACSFACT"),
    ],
    "2-M-C8": [
        ("MOLARMASS", "100.204", "128.2551", "100.204 is a C7 molar mass shared by 9 rows"),
        ("FORMULA", "C7H16", "C9H20", "C7H16 is heptane; 2-methyloctane is C9H20"),
        ("NORMBOIL", "58.05", "143.3000", "58.05 is the placeholder shared by 38 rows"),
    ],
    "ethylcyclohexane": [
        ("MOLARMASS", "100.204", "112.2126", "100.204 is a C7 molar mass shared by 9 rows"),
        ("NORMBOIL", "58.05", "131.9000", "58.05 is the placeholder shared by 38 rows"),
    ],
}

path = DATA / "COMP.csv"
lines = path.read_text(encoding="utf-8").replace("\r\n", "\n").rstrip("\n").split("\n")
header = [field.strip().strip('"') for field in lines[0].split(",")]
name_at = header.index("NAME")

changed = 0
for index, line in enumerate(lines[1:], start=1):
    fields = line.split(",")
    name = fields[name_at].strip()
    if name not in FIXES:
        continue
    print(name)
    for column, expected, corrected, why in FIXES[name]:
        position = header.index(column)
        actual = fields[position].strip()
        if actual != expected:
            print("     SKIP %-10s expected %s but found %s" % (column, expected, actual))
            continue
        fields[position] = corrected
        changed += 1
        print("     %-10s %-10s -> %-10s  (%s)" % (column, expected, corrected, why))
    lines[index] = ",".join(fields)

print("\ntotal edits: %d" % changed)
if APPLY:
    path.write_bytes(("\n".join(lines) + "\n").encode("utf-8"))
    print("APPLIED")
else:
    print("dry run - pass --apply to write")
