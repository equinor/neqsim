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


def inherited(row, defaults, key):
    """Return a row-specific provenance field or its manifest default."""
    return row.get(key, defaults.get(key))


def main():
    """Check all compiled rows and every selected source value."""
    root = Path(__file__).resolve().parents[1]
    data = root / "src/main/resources/data"
    rows = list(csv.DictReader((data / "COMP.csv").open(encoding="utf-8")))
    manifest = json.loads((data / "HenryWaterSource.json").read_text())
    selected = {row["name"]: row for row in manifest["rows"]}
    assert len(selected) == len(manifest["rows"]), "Duplicate selected component"
    reference_manifest = json.loads(
        (data / "HenryWaterReferencePoints.json").read_text())
    reference_points = {
        row["name"]: row for row in reference_manifest["rows"]}
    assert len(reference_points) == len(reference_manifest["rows"]), (
        "Duplicate reference-point component")
    assert len({row["cas"] for row in reference_points.values()}) == len(
        reference_points), "Duplicate reference-point CAS"
    coverage = {
        row["name"]: row
        for row in csv.DictReader(
            (data / "HenryWaterCoverage.csv").open(encoding="utf-8"))
    }
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
    component_rows = {row["NAME"]: row for row in rows}
    reviewed_correlations = {"n-pentane", "i-pentane", "mercury"}
    for name in reviewed_correlations:
        provenance = selected[name]
        component = component_rows[name]
        assert float(component["IONICCHARGE"]) == 0.0, name
        assert component["CASnumber"] == provenance["cas"], name
        assert component["InChIKey"] == provenance["source_inchikey"], name
        assert provenance["solvent"] == "water", name
        assert provenance["henry_definition"] == (
            "neutral molecular Hsbp = molality / partial pressure"), name
        for field in (
                "identity_match_basis", "original_reference",
                "original_reference_access", "original_reference_rights",
                "uncertainty", "selection_rationale", "temperature_scope"):
            assert provenance[field].strip(), f"Missing {field}: {name}"
        assert coverage[name]["status"] == (
            "imported_local_temperature_expression"), name
        if source_lines is not None:
            cas_marker = f"! casrn:    {provenance['cas']}"
            source_index = source_lines.index(cas_marker)
            identity_block = source_lines[source_index:source_index + 3]
            assert any(provenance["source_inchikey"] in line
                       for line in identity_block), name
    aliases = {
        name: provenance["alias_of"]
        for name, provenance in selected.items()
        if "alias_of" in provenance
    }
    for name, canonical_name in aliases.items():
        assert canonical_name in selected, f"Unknown canonical component: {name}"
        assert "alias_of" not in selected[canonical_name], f"Alias chain: {name}"
        alias_row = component_rows[name]
        canonical_row = component_rows[canonical_name]
        for field in ("CASnumber", "FORMULA", "InChIKey"):
            assert alias_row[field] == canonical_row[field], (
                f"Alias identity mismatch for {name}: {field}")
        assert selected[name]["cas"] == selected[canonical_name]["cas"], name
        assert selected[name]["solvent"] == "water", name
        assert selected[name]["henry_definition"] == (
            "neutral molecular Hsbp = molality / partial pressure"), name
        assert selected[name]["reference"] == selected[canonical_name]["reference"], name
        assert selected[name]["Hsbp_mol_kg_atm"] == (
            selected[canonical_name]["Hsbp_mol_kg_atm"]), name
        assert selected[name]["B_K"] == selected[canonical_name]["B_K"], name
        alias_parameters = [
            float(alias_row[f"HenryCoef{i}"]) for i in range(1, 5)]
        canonical_parameters = [
            float(canonical_row[f"HenryCoef{i}"]) for i in range(1, 5)]
        assert alias_parameters == canonical_parameters, (
            f"Alias correlation mismatch: {name}")
        assert coverage[name]["status"] == "imported_exact_identity_alias", name
    dispatched = sum(
        row["status"] in {
            "imported_local_temperature_expression",
            "imported_exact_identity_alias",
        }
        for row in coverage.values())
    assert dispatched == len(selected), "Coverage/source manifest mismatch"
    brockbank_names = {
        "4-methylheptane", "cis-2-pentene", "cis-2-heptene",
        "nC7-Benzene", "nC8-Benzene", "nC9-Benzene",
    }
    mackay_shiu_names = {"4-ethyltoluene"}
    reference_counts = {"3673": 0, "3518": 0, "479": 0}
    for name, provenance in reference_points.items():
        assert name not in selected, f"Reference point also dispatched as correlation: {name}"
        assert name in component_rows, f"Reference point component absent from database: {name}"
        component = component_rows[name]
        assert float(component["IONICCHARGE"]) == 0.0, f"Reference point on ion: {name}"
        assert component["CASnumber"] == provenance["cas"], name
        assert coverage[name]["status"] == "qualified_reference_point_only", name
        reference = provenance["reference"]
        assert reference in reference_counts, f"Unexpected reference family: {name}"
        reference_counts[reference] += 1
        for field in (
                "status", "solvent", "convention", "source",
                "compilation_license", "original_reference",
                "original_reference_url", "original_reference_rights",
                "identity_basis", "uncertainty", "temperature_scope"):
            assert inherited(provenance, reference_manifest, field).strip(), (
                f"Missing {field}: {name}")
        assert inherited(provenance, reference_manifest, "status") == (
            "reference_point_only"), name
        assert inherited(provenance, reference_manifest, "solvent") == "water", name
        assert inherited(provenance, reference_manifest, "reference_temperature_K") == 298.15, name
        reference_pressure = inherited(
            provenance, reference_manifest, "reference_pressure_MPa")
        if name in mackay_shiu_names:
            assert reference_pressure is None, name
        else:
            assert reference_pressure == 0.1, name
        kh = provenance["Hsbp_mol_kg_atm"]
        assert kh > 0.0 and math.isfinite(kh), name
        if name in brockbank_names:
            source_inchikey = provenance["source_inchikey"]
            assert source_inchikey[:14] == component["InChIKey"][:14], (
                f"Connectivity identity mismatch: {name}")
            assert reference == "3518", name
            assert provenance["original_reference_doi"] == "", name
            assert inherited(provenance, reference_manifest, "original_reference_url") == (
                "https://scholarsarchive.byu.edu/etd/3691/"), name
        elif name in mackay_shiu_names:
            assert provenance["source_inchikey"] == component["InChIKey"], name
            assert reference == "479", name
            assert provenance["original_reference_doi"] == "10.1063/1.555654", name
            assert inherited(provenance, reference_manifest, "original_reference_url") == (
                "https://doi.org/10.1063/1.555654"), name
        else:
            assert reference == "3673", name
            assert inherited(provenance, reference_manifest, "original_reference_doi") == (
                "10.1016/S0016-7037(99)00330-0"), name
        if source_lines is not None:
            cas_marker = f"! casrn:    {provenance['cas']}"
            source_index = source_lines.index(cas_marker)
            identity_block = source_lines[source_index:source_index + 3]
            if name in brockbank_names or name in mackay_shiu_names:
                assert any(provenance["source_inchikey"] in line
                           for line in identity_block), name
            cas = provenance["cas"].replace("-", "_")
            pattern = re.compile(r"Hsbp_+" + cas + r"\s*=\s*([\d.E+\-]+)"
                                 r"\s+!.*type: L, ref: (\S+)")
            matches = [pattern.match(line) for line in source_lines if "EXP(" not in line]
            assert any(match and float(match[1]) == kh
                       and match[2] == reference
                       for match in matches), name
    assert reference_counts == {"3673": 28, "3518": 6, "479": 1}
    qualified = sum(
        row["status"] == "qualified_reference_point_only"
        for row in coverage.values())
    assert qualified == len(reference_points), "Coverage/reference manifest mismatch"
    print(f"PASS: {len(rows)} rows; {len(found)} sourced correlations; "
          f"{len(reference_points)} qualified reference points; "
          f"{len(rows) - len(found)} rows without dispatched correlations; "
          "no nonzero unattributed rows.")


if __name__ == "__main__":
    main()
