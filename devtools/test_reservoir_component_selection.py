"""Exact conserved-scenario checks at the reservoir source/tank boundary."""

from copy import deepcopy
from fractions import Fraction as Q
from itertools import product
import json
from pathlib import Path

import pytest

from pvt_data import PVTStudyError, identity
from reservoir_history_intake import select_component_history

ROOT = Path(__file__).resolve().parents[1]


def inputs():
    """Load the source and explicit caller-selected scenario."""
    path = ROOT / "devtools/pvt_examples"
    return (json.loads((path / "reservoir_component_intake.json").read_text()),
            json.loads((path / "reservoir_component_selection.json").read_text()))


def select(study, scenario):
    """Rebind intentionally changed test inputs without concealing production identity checks."""
    scenario["source_sha256"] = identity(study)
    return select_component_history(study, scenario)


def test_documented_exact_selection_and_ownership():
    study, scenario = inputs()
    result = select_component_history(study, scenario)
    step = result["steps"][0]
    assert step["produced_moles"] == ["8.5", "1.5", "0"]
    assert step["injected_moles"] == ["0", "0", "2"]
    assert step["duration_seconds"] == 86400
    assert step["pressure_bara"] == "199"
    assert step["sigma_bara"] == "0.2"
    assert not step["holdout"]
    for stream in step["streams"].values():
        assert sum(map(Q, stream["component_moles"].values())) == Q(stream["total_moles"])
    assert result["source_sha256"] == identity(study)
    assert result["scenario_sha256"] == identity(scenario)
    scenario["component_order"].clear()
    study["streams"].clear()
    assert result["raw_input"] == inputs()[0]
    assert result["raw_scenario"] == inputs()[1]


def test_source_identity_and_incompatible_marginal_endpoints():
    study, scenario = inputs()
    scenario["source_sha256"] = "stale"
    with pytest.raises(PVTStudyError, match="identity"):
        select_component_history(study, scenario)
    scenario["source_sha256"] = identity(study)
    scenario["windows"][0]["streams"]["well-A gas"]["mole_fractions"].update(methane="0.9", ethane="0.2")
    with pytest.raises(PVTStudyError, match="exactly to one"):
        select_component_history(study, scenario)


@pytest.mark.parametrize("change", [
    lambda s: s["windows"][0].update(end_row="wrong"),
    lambda s: s["windows"][0]["streams"].pop("well-B water"),
    lambda s: s["windows"][0]["streams"]["well-A gas"]["mole_fractions"].pop("ethane"),
    lambda s: s.update(component_order=["methane", "water"]),
    lambda s: s.update(component_order=["methane", "ethane", "water", "nitrogen"]),
    lambda s: s.update(component_order=["methane", "ethane", "water", "water"]),
    lambda s: s["windows"][0].update(pressure_bara="198"),
    lambda s: s["windows"][0].update(sigma_bara="0.3"),
    lambda s: s["windows"][0]["streams"]["well-A gas"].update(total_moles="12"),
])
def test_reject_incomplete_or_out_of_bounds_selections(change):
    study, scenario = inputs()
    change(scenario)
    with pytest.raises(PVTStudyError):
        select_component_history(study, scenario)


def test_raw_cumulative_bounds_beat_outward_rounding():
    study, scenario = inputs()
    study["rows"][0]["cumulative"]["well-A gas"].update(lower="0.0000000000000000000000000000000000001",
                                                       upper="0.0000000000000000000000000000000000001")
    scenario["windows"][0]["streams"]["well-A gas"]["total_moles"] = "11"
    with pytest.raises(PVTStudyError, match="stream total"):
        select(study, scenario)


def test_pressure_roles_order_and_zero_species():
    study, scenario = inputs()
    study["rows"][1]["pressure"]["role"] = "holdout"
    scenario["component_order"] = ["water", "ethane", "methane"]
    step = select(study, scenario)["steps"][0]
    assert step["holdout"]
    assert step["produced_moles"] == ["0", "1.5", "8.5"]
    assert step["injected_moles"] == ["2", "0", "0"]
    study["rows"][1]["pressure"]["kind"] = "local_shut_in"
    with pytest.raises(PVTStudyError, match="field-average"):
        select(study, scenario)


def test_missing_flow_and_exact_decimal_products():
    study, scenario = inputs()
    study["rows"][0]["cumulative"].pop("well-A gas")
    with pytest.raises(PVTStudyError, match="eligible component"):
        select(study, scenario)
    study, scenario = inputs()
    chosen = scenario["windows"][0]["streams"]["well-A gas"]
    chosen["total_moles"] = "10.0000000000000000000000000000000000001"
    values = select(study, scenario)["steps"][0]["produced_moles"]
    assert list(map(Q, values)) == [Q(chosen["total_moles"])*Q(".85"),
                                  Q(chosen["total_moles"])*Q(".15"), Q(0)]


def two_windows():
    """Construct shared cumulative intervals without negative source increments."""
    study, scenario = inputs()
    last = deepcopy(study["rows"][-1])
    last.update(id="holdout", date="2024-03-01")
    last["pressure"]["role"] = "holdout"
    study["rows"].append(last)
    study["rows"][0]["cumulative"]["well-A gas"].update(lower="1", upper="2")
    study["rows"][1]["cumulative"]["well-A gas"].update(lower="3", upper="5")
    study["rows"][2]["cumulative"]["well-A gas"].update(lower="6", upper="8")
    last["cumulative"]["well-B water"].update(lower="4", upper="4")
    selected = deepcopy(scenario["windows"][0])
    selected["end_row"] = "holdout"
    scenario["windows"].append(selected)
    return study, scenario


def test_shared_endpoints_against_enumerated_origin():
    study, scenario = two_windows()
    passed = failed = 0
    for first, second in product(range(1, 5), range(1, 6)):
        scenario["windows"][0]["streams"]["well-A gas"]["total_moles"] = str(first)
        scenario["windows"][1]["streams"]["well-A gas"]["total_moles"] = str(second)
        feasible = [origin for origin in (Q(1), Q(2))
                    if 3 <= origin+first <= 5 and 6 <= origin+first+second <= 8]
        if feasible:
            result = select(study, scenario)
            lo, hi = map(Q, result["cumulative_origin_bounds"]["well-A gas"])
            assert all(lo <= v <= hi for v in feasible)
            assert result["steps"][1]["holdout"]
            passed += 1
        else:
            with pytest.raises(PVTStudyError, match="shared cumulative"):
                select(study, scenario)
            failed += 1
    assert passed and failed


def test_documentation_example(monkeypatch, capsys):
    doc = ROOT / "docs/pvtsimulation/reservoir_history_intake.md"
    fence = chr(96)*3
    code = doc.read_text().split(fence+"python\n")[3].split(fence)[0]
    monkeypatch.chdir(ROOT)
    exec(compile(code, str(doc), "exec"), {})
    assert capsys.readouterr().out.strip() == "['8.5', '1.5', '0']"


def test_reduced_power_of_two_singleton_and_component_products():
    study, scenario = inputs()
    tiny = "0.00000095367431640625"
    pressure = study["rows"][1]["pressure"]["value"]
    pressure.update(lower=tiny, upper=tiny)
    scenario["windows"][0]["pressure_bara"] = tiny
    composition = study["rows"][1]["interval_compositions"]["well-A gas"]["components"]
    composition["ethane"].update(lower="0", upper="0.2")
    chosen = scenario["windows"][0]["streams"]["well-A gas"]
    chosen["mole_fractions"].update(methane="0.99999904632568359375", ethane=tiny)
    result = select(study, scenario)["steps"][0]
    assert Q(result["pressure_bara"]) == Q(1, 2**20)
    assert list(map(Q, result["produced_moles"])) == [10*(1-Q(1, 2**20)), 10*Q(1, 2**20), 0]
    assert sum(map(Q, result["produced_moles"])) == 10


@pytest.mark.parametrize("power", [1, 20, 40, 200, 400])
def test_exact_binary_denominator_serialization(power):
    from reservoir_history_intake import _exact_decimal

    for value in (Q(1, 2**power), Q(1, 5**power), Q(1234567, 2**power*5**power)):
        assert Q(_exact_decimal(value)) == value
    with pytest.raises(PVTStudyError, match="finite-decimal"):
        _exact_decimal(Q(1, 3))


def test_multiple_named_producers_aggregate_without_netting_injection():
    study, scenario = inputs()
    study["streams"]["well-C gas"] = deepcopy(study["streams"]["well-A gas"])
    for row in study["rows"]:
        row["cumulative"]["well-C gas"] = deepcopy(row["cumulative"]["well-A gas"])
    row = study["rows"][1]
    row["interval_compositions"]["well-C gas"] = deepcopy(row["interval_compositions"]["well-A gas"])
    selections = scenario["windows"][0]["streams"]
    selections["well-C gas"] = deepcopy(selections["well-A gas"])
    selections["well-C gas"]["total_moles"] = "11"
    step = select(study, scenario)["steps"][0]
    assert list(map(Q, step["produced_moles"])) == [Q("17.85"), Q("3.15"), Q(0)]
    assert step["injected_moles"] == ["0", "0", "2"]
    assert set(step["streams"]) == set(study["streams"])
    assert sum(map(Q, step["produced_moles"])) == 21
