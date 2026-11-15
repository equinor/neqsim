"""Independent exact-rational and protocol tests for the serial separator audit."""

import json
import re
from copy import deepcopy
from fractions import Fraction
from itertools import product
from pathlib import Path

import pytest

from pvt_data import PVTStudyError, identity
from pvt_separator_mass import UNITS, audit_separator_mass


EXAMPLE = Path(__file__).with_name("pvt_examples") / "separator_mass_audit.json"
MASS = "mass_balance_kg_per_m3_stock_tank_oil"
GOR = "gor_balance_sm3_per_m3_stock_tank_oil"


def example():
    """Read the documented synthetic example."""
    return json.loads(EXAMPLE.read_text(encoding="utf-8"))


def bound(lower, upper, unit):
    """Build a measured interval without binary floating-point conversion."""
    return {"lower": str(lower), "upper": str(upper), "unit": unit}


def ends(record, field):
    """Get exact rational input endpoints for independent enumeration."""
    return [Fraction(record[field][side]) for side in ("lower", "upper")]


def require_enclosure(result, vertices):
    """Check zero classification, full enclosure and conservative tightness."""
    exact_lower, exact_upper = min(vertices), max(vertices)
    lower, upper = map(Fraction, result["residual"])
    assert lower <= exact_lower <= exact_upper <= upper
    assert max(exact_lower - lower, upper - exact_upper) <= max(
        abs(exact_lower), abs(exact_upper), 1
    ) * Fraction("1e-24")
    status = "compatible" if exact_lower <= 0 <= exact_upper else "incompatible"
    assert result["status"] == status


def test_documented_example_and_input_identity():
    study = example()
    original = deepcopy(study)
    result = audit_separator_mass(study)
    assert result["input_sha256"] == identity(original)
    assert study == original == result["raw_input"]
    assert result["stock_tank_endpoint"]["oil_volume_ratio"]["required"] == "1"
    for check in [*result["transitions"], result["whole_train"]]:
        for quantity in (MASS, GOR):
            require_enclosure(check[quantity], [Fraction(0)])
    study["stages"][2]["id"] = "changed"
    assert result["raw_input"] == original


def test_documented_code_runs(monkeypatch, capsys):
    root = EXAMPLE.parents[2]
    guide = root / "docs/pvtsimulation/separator_mass_audit.md"
    code, = re.findall(r"```python\n(.*?)```", guide.read_text(encoding="utf-8"), re.DOTALL)
    monkeypatch.chdir(root)
    exec(compile(code, str(guide), "exec"), {})
    assert capsys.readouterr().out.count("'compatible'") == 2


@pytest.mark.parametrize("offset", range(8))
def test_conservative_exact_rational_vertices(offset):
    study = example()
    feed, separator, tank = study["stages"]
    feed["oil_density"] = bound(749 + offset, 751 + offset, UNITS["oil_density"])
    separator["oil_volume_ratio"] = bound("1.049", "1.051", UNITS["oil_volume_ratio"])
    separator["released_gas"] = bound("59.99", "60.01", UNITS["released_gas"])
    separator["standard_gas_density"] = bound("0.997", "1.003", UNITS["standard_gas_density"])
    tank["released_gas"] = bound("39.98", "40.02", UNITS["released_gas"])
    tank.pop("standard_gas_density")
    tank["released_gas_molar_mass"] = bound("0.0233", "0.0235", UNITS["released_gas_molar_mass"])
    tank["standard_gas_z"] = bound("0.987", "0.994", UNITS["standard_gas_z"])
    standard = study["standard_gas"]
    standard["temperature"] = bound("288.1", "288.2", "K")
    standard["pressure"] = bound("101320", "101330", "Pa")

    v0 = [v*rho for v, rho in product(ends(feed, "oil_volume_ratio"), ends(feed, "oil_density"))]
    v1 = [v*rho for v, rho in product(ends(separator, "oil_volume_ratio"), ends(separator, "oil_density"))]
    v2 = ends(tank, "oil_density")  # Unit final volume ratio.
    q1, q2 = ends(separator, "released_gas"), ends(tank, "released_gas")
    gas1 = [q*rho for q, rho in product(q1, ends(separator, "standard_gas_density"))]
    gas2 = [q*m*p / (Fraction("8.31446261815324")*t*z) for q, m, p, t, z in
            product(q2, ends(tank, "released_gas_molar_mass"), ends(standard, "pressure"),
                    ends(standard, "temperature"), ends(tank, "standard_gas_z"))]
    r0, r1 = ends(feed, "solution_gor"), ends(separator, "solution_gor")
    result = audit_separator_mass(study)
    one, two = result["transitions"]
    total = result["whole_train"]
    require_enclosure(one[MASS], [a-b-c for a, b, c in product(v0, v1, gas1)])
    require_enclosure(two[MASS], [a-b-c for a, b, c in product(v1, v2, gas2)])
    require_enclosure(total[MASS], [a-b-c-d for a, b, c, d in product(v0, v2, gas1, gas2)])
    require_enclosure(one[GOR], [a-b-c for a, b, c in product(r0, r1, q1)])
    require_enclosure(two[GOR], [a-b for a, b in product(r1, q2)])
    require_enclosure(total[GOR], [a-b-c for a, b, c in product(r0, q1, q2)])


def test_bad_endpoint_retains_raw_measurement_and_prevents_false_mass_closure():
    study = example()
    final = study["stages"][-1]
    final["oil_volume_ratio"] = bound("1.299", "1.301", UNITS["oil_volume_ratio"])
    result = audit_separator_mass(study)
    assert result["raw_input"]["stages"][-1] == final
    assert result["stock_tank_endpoint"]["oil_volume_ratio"]["status"] == "incompatible"
    assert result["stock_tank_endpoint"]["solution_gor"]["status"] == "compatible"
    assert result["transitions"][1][MASS]["status"] == "unresolved"
    assert result["transitions"][1][GOR]["status"] == "compatible"
    assert result["whole_train"][MASS]["status"] == "unresolved"


def test_telescoping_uses_independent_feed_final_and_releases():
    study = example()
    intermediate = study["stages"][1]
    for name in ("solution_gor", "oil_density", "oil_volume_ratio"):
        intermediate.pop(name)
    result = audit_separator_mass(study)
    assert all(row[MASS]["status"] == row[GOR]["status"] == "unresolved"
               for row in result["transitions"])
    for quantity in (MASS, GOR):
        require_enclosure(result["whole_train"][quantity], [Fraction(0)])


def test_gor_stage_conflict_survives_closed_total():
    study = example()
    study["stages"][1]["solution_gor"] = bound(42, 42, UNITS["solution_gor"])
    result = audit_separator_mass(study)
    for row in result["transitions"]:
        assert row[GOR]["status"] == "incompatible"
        assert row[MASS]["status"] == "compatible"
    assert result["whole_train"][GOR]["status"] == "compatible"


def test_molar_mass_alone_cannot_validate_positive_release():
    study = example()
    first_withdrawal = study["stages"][1]
    first_withdrawal.pop("standard_gas_density")
    first_withdrawal["released_gas_molar_mass"] = bound("0.025", "0.026", UNITS["released_gas_molar_mass"])
    result = audit_separator_mass(study)
    assert result["transitions"][0]["gas"]["missing"] == ["standard_gas_density_or_molar_mass_and_z"]
    assert result["transitions"][0][GOR]["status"] == "compatible"
    assert result["whole_train"][MASS]["status"] == "unresolved"


def test_complete_gas_routes_check_intersection_and_disagreement():
    study = example()
    stage = study["stages"][1]
    stage["released_gas_molar_mass"] = bound("0.023", "0.025", UNITS["released_gas_molar_mass"])
    stage["standard_gas_z"] = bound("0.99", "1.01", UNITS["standard_gas_z"])
    gas = audit_separator_mass(study)["transitions"][0]["gas"]
    assert set(gas["density_routes_kg_per_sm3"]) == {"reported_density", "molar_mass_and_z"}
    assert list(map(Fraction, gas["mass_kg_per_m3_stock_tank_oil"])) == [60, 60]
    stage["standard_gas_z"] = bound("0.49", "0.51", UNITS["standard_gas_z"])
    result = audit_separator_mass(study)
    assert result["transitions"][0]["gas"]["reason"] == "standard_gas_density_routes_disagree"
    assert result["transitions"][0][MASS]["status"] == "unresolved"
    assert result["whole_train"][MASS]["status"] == "unresolved"


def test_explicit_zero_release_requires_no_gas_properties():
    study = example()
    feed, middle, _ = study["stages"]
    middle.pop("standard_gas_density")
    middle["released_gas"] = bound(0, 0, UNITS["released_gas"])
    for key in ("oil_density", "oil_volume_ratio", "solution_gor"):
        middle[key] = feed[key]
    check = audit_separator_mass(study)["transitions"][0]
    assert check["gas"]["reason"] == "reported_zero_release"
    require_enclosure(check[MASS], [Fraction(0)])
    middle.pop("released_gas")
    check = audit_separator_mass(study)["transitions"][0]
    assert check["gas"]["status"] == check[MASS]["status"] == check[GOR]["status"] == "unresolved"


@pytest.mark.parametrize("change,match", [
    (lambda s: s.update(protocol="DLE"), "SEPARATOR protocol"),
    (lambda s: s.pop("standard_gas"), "standard gas state"),
    (lambda s: s["standard_gas"]["pressure"].update(unit="bar"), "unit Pa"),
    (lambda s: s.pop("stock_tank_oil_reference"), "stock_tank_oil_reference"),
    (lambda s: s.update(stages=s["stages"][:1]), "feed and at least one"),
    (lambda s: s["stages"][0].update(released_gas=s["stages"][1]["released_gas"]), "follow the feed"),
    (lambda s: s["stages"][1].pop("oil_density_reference"), "oil_density_reference"),
    (lambda s: s["stages"][1].pop("standard_gas_property_reference"), "standard_gas_property_reference"),
    (lambda s: s["stages"][1].pop("temperature"), "temperature"),
    (lambda s: s["stages"][1].update(id="feed"), "Duplicate"),
    (lambda s: s["stages"][1].update(pressure=s["stages"][0]["pressure"]), "decrease"),
    (lambda s: s["stages"][1]["released_gas"].update(lower="-1"), "order or sign"),
    (lambda s: s["stages"][1]["oil_volume_ratio"].update(unit="m3/m3 residual oil"), "stock tank oil"),
])
def test_invalid_observations(change, match):
    study = example()
    change(study)
    with pytest.raises(PVTStudyError, match=match):
        audit_separator_mass(study)


def test_implied_gas_density_is_a_diagnostic():
    study = example()
    stage = study["stages"][1]
    stage.pop("standard_gas_density")
    stage["released_gas"] = bound("59.99", "60.01", UNITS["released_gas"])
    result = audit_separator_mass(study)["transitions"][0]
    assert result[MASS]["status"] == "unresolved"
    lo, hi = map(Fraction, result["implied_gas_density_kg_per_sm3"])
    assert lo <= Fraction(60) / Fraction("60.01") < Fraction(1) < Fraction(60) / Fraction("59.99") <= hi


def test_increasing_oil_mass_is_incompatible_without_gas_properties():
    study = example()
    stage = study["stages"][1]
    stage.pop("standard_gas_density")
    stage["oil_density"] = bound(900, 901, UNITS["oil_density"])
    result = audit_separator_mass(study)["transitions"][0]
    assert result[MASS] == {"status": "incompatible", "reason": "oil_mass_increases"}
    assert "implied_gas_density_kg_per_sm3" not in result
