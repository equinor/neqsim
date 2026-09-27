#!/usr/bin/env python3
"""Audit Henry data provenance and conversion; optionally verify Sander's source ZIP.

Usage: python3 devtools/check_henry_water_data.py [henry_5.0.0_f90.zip]
The optional archive is obtained from https://www.henrys-law.org/henry/download.html.
No network access or third-party Python package is required by this checker.
"""

import csv
import json
import math
from pathlib import Path
import re
import sys
import zipfile


def main():
    """Check all compiled rows and every selected source value."""
    root = Path(__file__).resolve().parents[1]
    data = root / "src/main/resources/data"
    rows = list(csv.DictReader((data / "COMP.csv").open(encoding="utf-8")))
    manifest = json.loads((data / "HenryWaterSource.json").read_text())
    selected = {row["name"]: row for row in manifest["rows"]}
    assert len(selected) == len(manifest["rows"]), "Duplicate selected component"
    source_lines = None
    if len(sys.argv) > 1:
        with zipfile.ZipFile(sys.argv[1]) as archive:
            source_lines = archive.read("Hsbp.f90").decode().splitlines()
    found = set()
    for row in rows:
        name = row["NAME"]
        parameters = [float(row[f"HenryCoef{i}"]) for i in range(1, 5)]
        if name not in selected:
            assert parameters == [0.0] * 4, f"Unattributed Henry row: {name}"
            continue
        found.add(name)
        provenance = selected[name]
        assert float(row["IONICCHARGE"]) == 0.0, f"Neutral data on ion: {name}"
        assert row["CASnumber"] == provenance["cas"], name
        kh = provenance["Hsbp_mol_kg_atm"]
        slope = provenance["B_K"]
        assert kh > 0.0 and math.isfinite(kh) and math.isfinite(slope), name
        expected = [math.log(1.01325 / kh / 1.802) + slope / 298.15, -slope, 0.0, 0.0]
        assert all(math.isclose(a, b, rel_tol=1e-12, abs_tol=1e-12)
                   for a, b in zip(parameters, expected)), name
        if source_lines is not None:
            cas = provenance["cas"].replace("-", "_")
            pattern = re.compile(r"Hsbp_+" + cas + r"\s*=\s*([\d.E+\-]+)"
                                 r" \* EXP\(\s*([\d.\-]+).*type: L, ref: (\S+)")
            matches = [pattern.match(line) for line in source_lines]
            assert any(match and float(match[1]) == kh and float(match[2]) == slope
                       and match[3] == provenance["reference"] for match in matches), name
    assert found == set(selected), "Manifest contains absent database components"
    print(f"PASS: {len(rows)} rows; {len(found)} sourced correlations; "
          f"{len(rows) - len(found)} explicitly unavailable; no nonzero unattributed rows.")


if __name__ == "__main__":
    main()
