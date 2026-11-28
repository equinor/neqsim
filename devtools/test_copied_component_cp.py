"""Check issue #5313 reference points and parity of the two shipped CSVs."""

import csv
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CP_FIELDS = ("CPA", "CPB", "CPC", "CPD", "CPE")


def test_copied_component_cp():
    """Check the stored files directly, before the extended loader overlays standard rows."""
    inventory = ROOT / "docs/thermo/data/copied_cp_references.csv"
    with inventory.open(encoding="utf-8", newline="") as stream:
        references = {row["name"]: row for row in csv.DictReader(stream)}
    assert len(references) == 34

    tables = []
    for filename in ("COMP.csv", "COMP_EXT.csv"):
        path = ROOT / "src/main/resources/data" / filename
        selected = {}
        with path.open(encoding="utf-8-sig", newline="") as stream:
            for row in csv.DictReader(stream):
                name = row["NAME"]
                if name not in references:
                    continue
                reference = references[name]
                assert name not in selected
                assert row["CASnumber"] == reference["CAS"]
                coefficients = tuple(float(row[field]) for field in CP_FIELDS)
                selected[name] = coefficients
                for index in (1, 2, 3):
                    temperature = float(reference[f"T{index}_K"])
                    expected = float(reference[f"Cp{index}_J_mol_K"])
                    actual = sum(c * temperature**i for i, c in enumerate(coefficients))
                    # Allow for rounding of the reference, residual, and stored coefficients.
                    tolerance = expected * float(reference["max_fit_error_pct"]) / 100 + 0.001
                    assert abs(actual - expected) <= tolerance, (filename, name, temperature)
        assert selected.keys() == references.keys()
        tables.append(selected)
    assert tables[0] == tables[1]
