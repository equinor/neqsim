"""Independent exact-vertex qualification of interval-average component intake."""

from fractions import Fraction as Q
from itertools import product
import json
from pathlib import Path

import pytest

from pvt_data import PVTStudyError, identity
from reservoir_history_intake import audit_component_moles

ROOT = Path(__file__).resolve().parents[1]


def example():
    """Read the source fixture afresh."""
    path = ROOT / "devtools/pvt_examples/reservoir_component_intake.json"
    return json.loads(path.read_text(encoding="utf-8"))


def test_named_directions_ownership_and_composition():
    study = example()
    report = audit_component_moles(study)
    assert report["input_sha256"] == identity(study)
    assert report["raw_input"] == study
    window = report["windows"][0]
    assert window["eligible_for_component_handoff"]
    production = window["component_streams"]["well-A gas"]
    injection = window["component_streams"]["well-B water"]
    assert production["direction"] == "production"
    assert production["component_moles"]["methane"]["bounds"] == ["8.0", "9.9"]
    assert production["component_moles"]["ethane"]["bounds"] == ["1.0", "2.2"]
    assert injection["direction"] == "injection"
    assert injection["component_moles"]["water"]["bounds"] == ["2", "2"]
    study["rows"][1]["interval_compositions"].clear()
    assert report["raw_input"] == example()


def test_exact_zero_and_missing_composition():
    study = example()
    study["rows"][1]["cumulative"]["well-B water"].update(lower="0", upper="0")
    report = audit_component_moles(study)["windows"][0]
    assert report["component_streams"]["well-B water"]["component_moles"]["water"]["bounds"] == ["0", "0"]
    study["rows"][1]["interval_compositions"].pop("well-B water")
    report = audit_component_moles(study)["windows"][0]
    assert not report["eligible_for_component_handoff"]
    assert report["component_streams"]["well-B water"]["reasons"] == ["missing_interval_composition"]


def test_no_volume_conversion_or_gap_bridging():
    study = example()
    study["streams"]["well-A gas"]["unit"] = "Sm3"
    for row in study["rows"]:
        row["cumulative"]["well-A gas"]["unit"] = "Sm3"
    result = audit_component_moles(study)["windows"][0]["component_streams"]["well-A gas"]
    assert result["reasons"] == ["requires_total_moles"]
    assert result["component_moles"] == {}
    study = example()
    study["rows"][1]["date"] = "2025-02-30"
    assert not audit_component_moles(study)["windows"][0]["eligible_for_component_handoff"]


def test_no_renormalization():
    study = example()
    values = study["rows"][1]["interval_compositions"]["well-A gas"]["components"]
    values["methane"].update(lower="0.1", upper="0.2")
    result = audit_component_moles(study)["windows"][0]["component_streams"]["well-A gas"]
    assert result["status"] == "incompatible"
    assert result["reasons"] == ["incompatible_composition_sum"]
    assert result["component_moles"] == {}


@pytest.mark.parametrize("bad", [None, {}, {"unit": "mass/mass", "lower": "0", "upper": "1"},
                               {"unit": "mol/mol", "lower": "0", "upper": "1.01"}])
def test_invalid_components(bad):
    study = example()
    study["rows"][1]["interval_compositions"]["well-A gas"]["components"]["methane"] = bad
    with pytest.raises(PVTStudyError):
        audit_component_moles(study)


def test_mismatched_basis():
    study = example()
    study["rows"][1]["interval_compositions"]["well-A gas"]["basis_reference"] = "separator dry gas"
    with pytest.raises(PVTStudyError, match="bases must match"):
        audit_component_moles(study)


def test_exact_simplex_vertices():
    """Enumerate ternary box/simplex vertices independently of marginal tightening."""
    study = example()
    composition = study["rows"][1]["interval_compositions"]["well-A gas"]
    cases = 0
    for lo1, hi1, lo2, hi2 in product(range(3), range(3, 6), range(3), range(3, 6)):
        bounds = [(Q(lo1, 10), Q(hi1, 10)), (Q(lo2, 10), Q(hi2, 10)), (Q(1, 10), Q(8, 10))]
        vertices = []
        for free in range(3):
            fixed = [i for i in range(3) if i != free]
            for values in product(*(bounds[i] for i in fixed)):
                point = dict(zip(fixed, values))
                point[free] = 1 - sum(values)
                if bounds[free][0] <= point[free] <= bounds[free][1]:
                    vertices.append([point[i] for i in range(3)])
        composition["components"] = {
            name: {"unit": "mol/mol", "lower": str(float(lo)), "upper": str(float(hi))}
            for name, (lo, hi) in zip(("methane", "ethane", "propane"), bounds)
        }
        result = audit_component_moles(study)["windows"][0]["component_streams"]["well-A gas"]
        assert vertices
        for i, name in enumerate(("methane", "ethane", "propane")):
            lower, upper = map(Q, result["component_moles"][name]["bounds"])
            targets = [n * point[i] for n in (10, 11) for point in vertices]
            assert lower <= min(targets) <= max(targets) <= upper
            assert min(targets) - lower <= Q("1e-31")
            assert upper - max(targets) <= Q("1e-31")
        cases += 1
    assert cases == 81


def test_documented_example(monkeypatch, capsys):
    doc = ROOT / "docs/pvtsimulation/reservoir_history_intake.md"
    fence = chr(96) * 3
    code = doc.read_text(encoding="utf-8").split(fence + "python\n")[2].split(fence)[0]
    monkeypatch.chdir(ROOT)
    exec(compile(code, str(doc), "exec"), {})
    assert capsys.readouterr().out.strip() == "['methane', 'ethane']"


@pytest.mark.parametrize("averaging", [None, "time_weighted"])
def test_averaging_requires_mole_basis(averaging):
    study = example()
    study["rows"][1]["interval_compositions"]["well-A gas"]["averaging"] = averaging
    with pytest.raises(PVTStudyError, match="averaging"):
        audit_component_moles(study)


def test_constant_composition_and_tight_decimal_boundary():
    study = example()
    composition = study["rows"][1]["interval_compositions"]["well-A gas"]
    composition["averaging"] = "constant"
    composition["components"] = {
        "methane": {"unit": "mol/mol", "lower": "0.9999999999999999999999999999999999999",
                    "upper": "1"},
        "ethane": {"unit": "mol/mol", "lower": "0.0000000000000000000000000000000000001",
                   "upper": "0.0000000000000000000000000000000000001"},
    }
    value = audit_component_moles(study)["windows"][0]["component_streams"]["well-A gas"]
    assert value["status"] == "eligible"
    for name, fraction in (("methane", 1-Q("1e-37")), ("ethane", Q("1e-37"))):
        lo, hi = map(Q, value["component_moles"][name]["bounds"])
        assert lo <= 10*fraction <= 11*fraction <= hi
        assert 10*fraction-lo <= Q("1e-31")
        assert hi-11*fraction <= Q("1e-31")
    composition["components"]["methane"]["lower"] = "1"
    value = audit_component_moles(study)["windows"][0]["component_streams"]["well-A gas"]
    assert value["status"] == "incompatible"
    assert value["component_moles"] == {}
