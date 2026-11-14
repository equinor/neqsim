"""Independent exact-rational controls for the laboratory DLE audit."""

from copy import deepcopy
from decimal import Decimal
from fractions import Fraction

import pytest

from pvt_data import PVTStudyError, identity
from pvt_dle_mass import UNITS, audit_dle_mass


def measured(value, unit, half_width="0"):
    """Build a positive physical interval in explicit source-converted units."""
    center, width = Decimal(value), Decimal(half_width)
    return {"unit": unit, "lower": str(center - width), "upper": str(center + width)}


def example(gor):
    """Known synthetic withdrawal, with both standard state and measured stage Z."""
    volume = (Fraction("8.31446261815324") * 300 / 100000) * Fraction("0.9")
    released = volume * 20 / Fraction("0.02")
    # The oil loses 20 kg per reference m3. Round the observed GOR inside a declared interval.
    observed = Decimal(gor) if gor is not None else Decimal(released.numerator) / Decimal(released.denominator)
    return {
        "protocol": "DLE",
        "sample_reference": "synthetic hydrocarbon",
        "experiment_reference": "synthetic isothermal DLE",
        "source_reference": "test_pvt_dle_mass.py",
        "data_use_reference": "original synthetic numbers",
        "residual_oil_reference": "final differential residual oil at declared reference",
        "bounds_reference": "declared synthetic closed bounds",
        "experiment_temperature": measured("380", "K"),
        "standard_gas": {
            "temperature": measured("300", "K", "0.0001"),
            "pressure": measured("100000", "Pa", "0.001"),
            "source_reference": "explicit synthetic standard basis",
        },
        "stages": [
            {"id": "Pb", "source_reference": "initial", "pressure": measured("200", UNITS["pressure"]),
             "oil_volume_ratio": measured("1.25", UNITS["oil_volume_ratio"], "0.00001"),
             "oil_density": measured("800", UNITS["oil_density"], "0.01"),
             "solution_gor": measured(observed + 5, UNITS["solution_gor"], "0.0001")},
            {"id": "D1", "source_reference": "withdrawal", "pressure": measured("180", UNITS["pressure"]),
             "oil_volume_ratio": measured("1.225", UNITS["oil_volume_ratio"], "0.00001"),
             "oil_density": measured("800", UNITS["oil_density"], "0.01"),
             "solution_gor": measured("5", UNITS["solution_gor"], "0.0001"),
             "released_gas_molar_mass": measured("0.02", UNITS["released_gas_molar_mass"], "0.0000001"),
             "standard_gas_z": measured("0.9", UNITS["standard_gas_z"], "0.000001")},
        ],
    }


def reference(study):
    """Calculate exact extreme vertices directly from the conservation identities."""
    def ends(row, key):
        return [Fraction(row[key][bound]) for bound in ("lower", "upper")]

    previous, current = study["stages"][:2]
    prior = [v*r for v in ends(previous, "oil_volume_ratio") for r in ends(previous, "oil_density")]
    later = [v*r for v in ends(current, "oil_volume_ratio") for r in ends(current, "oil_density")]
    loss = [a - b for a in prior for b in later]
    gas = [a - b for a in ends(previous, "solution_gor") for b in ends(current, "solution_gor")]
    products = [v*m for v in gas for m in ends(current, "released_gas_molar_mass")]
    implied = [a/b for a in (min(products), max(products)) for b in (min(loss), max(loss))]
    standard = study["standard_gas"]
    amounts = [a*p/(Fraction("8.31446261815324")*t*z)
               for a in (min(products), max(products)) for p in ends(standard, "pressure")
               for t in ends(standard, "temperature") for z in ends(current, "standard_gas_z")]
    residual = [a-b for a in (min(loss), max(loss)) for b in (min(amounts), max(amounts))]
    return {"oil_mass_loss_kg_per_m3_reference": (min(loss), max(loss)),
            "released_gas_sm3_per_m3_reference": (min(gas), max(gas)),
            "implied_standard_molar_volume_m3_per_mol": (min(implied), max(implied)),
            "mass_residual_kg_per_m3_reference": (min(residual), max(residual))}


@pytest.mark.parametrize("status,offset", [(s, i) for s in ("compatible", "incompatible") for i in range(8)])
def test_exact_vertex_bounds(status, offset):
    study = example(None if status == "compatible" else str(30 + offset))
    study["stages"][1]["solution_gor"] = measured(str(5 + offset), UNITS["solution_gor"], "0.0001")
    previous = study["stages"][0]["solution_gor"]
    for bound in ("lower", "upper"):
        previous[bound] = str(Decimal(previous[bound]) + offset)
    original = deepcopy(study)
    result = audit_dle_mass(study)
    assert result["input_sha256"] == identity(original)
    assert result["raw_input"] == original == study
    row = result["transitions"][0]
    assert row["status"] == status
    for key, (low, high) in reference(study).items():
        a, b = map(Fraction, row[key])
        assert a <= low <= high <= b
        assert max(low-a, b-high) / max(abs(low), abs(high)) < Fraction("1e-24")
    study["stages"][1]["id"] = "changed"
    assert result["raw_input"] == original


def test_missing_endpoint_and_missing_z_keep_distinct_evidence():
    study = example(None)
    del study["stages"][1]["standard_gas_z"]
    end = {"id": "last isothermal row", "source_reference": "source's blank GOR",
           "pressure": measured("1.03", "bara")}
    study["stages"].append(end)
    release, missing = audit_dle_mass(study)["transitions"]
    assert release["reason"] == "missing_standard_state_or_stage_z"
    assert "implied_standard_molar_volume_m3_per_mol" in release
    assert "mass_residual_kg_per_m3_reference" not in release
    assert missing["status"] == "unresolved"
    assert "current.solution_gor" in missing["missing"]


def test_zero_and_uncertain_withdrawal():
    study = example(None)
    before, after = study["stages"]
    for field in ("oil_volume_ratio", "oil_density", "solution_gor"):
        before[field] = after[field] = measured("1", UNITS[field])
    row = audit_dle_mass(study)["transitions"][0]
    assert row["reason"] == "zero_or_indeterminate_withdrawal"
    assert "implied_standard_molar_volume_m3_per_mol" not in row
    after["solution_gor"] = measured("1", UNITS["solution_gor"], "0.01")
    row = audit_dle_mass(study)["transitions"][0]
    assert row["reason"] == "zero_or_indeterminate_withdrawal"
    assert Decimal(row["mass_residual_kg_per_m3_reference"][0]) < 0


def test_negative_release_incompatible_without_standard_basis():
    study = example(None)
    study.pop("standard_gas")
    study["stages"][1].pop("standard_gas_z")
    study["stages"][1]["solution_gor"] = measured("80", UNITS["solution_gor"])
    assert audit_dle_mass(study)["transitions"][0]["status"] == "incompatible"


@pytest.mark.parametrize("damage,match", [
    (lambda s: s.update(protocol="CVD"), "DLE protocol"),
    (lambda s: s.update(bounds_reference=" "), "bounds_reference"),
    (lambda s: s.pop("residual_oil_reference"), "residual_oil_reference"),
    (lambda s: s["stages"][1].update(id="Pb"), "Duplicate"),
    (lambda s: s["stages"][1].update(pressure=measured("200", "bara")), "decrease"),
    (lambda s: s["stages"][1]["pressure"].update(unit="barg"), "bara"),
    (lambda s: s["stages"][1]["solution_gor"].update(lower=0), "decimal strings"),
    (lambda s: s["stages"][1]["oil_density"].update(lower="NaN"), "finite"),
    (lambda s: s["stages"][1]["oil_density"].update(lower="801"), "order"),
    (lambda s: s.pop("standard_gas"), "standard gas state"),
])
def test_invalid_provenance_units_or_stages_are_rejected(damage, match):
    study = example(None)
    damage(study)
    with pytest.raises(PVTStudyError, match=match):
        audit_dle_mass(study)


def test_documented_example():
    """Execute the same JSON handoff used in the workflow guide."""
    import json
    from pathlib import Path

    path = Path(__file__).with_name("pvt_examples") / "dle_mass_audit.json"
    report = audit_dle_mass(json.loads(path.read_text(encoding="utf-8")))
    assert [row["status"] for row in report["transitions"]] == ["compatible"]
