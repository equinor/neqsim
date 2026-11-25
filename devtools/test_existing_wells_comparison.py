"""Tests of the existing_wells_comparison check: overpressure studies must compare a new source with existing wells/inputs and check blocked outlet against current inflow."""

import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import validate_task_results as v  # noqa: E402

BLOCK = {
    "existing_inputs": [{"well": "A-1", "riser_pressure_bara": 90.0, "siwhp_bara": 120.0, "gas_kgs": 12.0, "choke_dp_bar": 40.0}],
    "relief_basis": [{"item": "separator PSV", "basis": "not documented", "capacity_kgs": 176.0}],
    "ratios": {"new_to_approved_source_pressure": 1.74},
    "relief_effect": [{"case": "new source without PAHH", "psv_relief_kgs": 283.0, "ratio_to_approved": 1.65}],
    "blocked_outlet": {"governs": False, "statement": "Not governing at current inflow; stationary estimate.",
                       "inflow_cases": [{"case": "last 90 days", "inflow_kgs": 18.6, "margin": 9.5}]},
}

OVERPRESSURE_RESULTS = {
    "task_statement": "Overpressure study: can a high-pressure well be produced against an LP header with PSV relief and PAHH?",
    "approach": "Inadvertent valve opening with choke collapse (IVO), blocked outlet and relief sizing.",
    "key_results": {"Peak pressure_barg": 45.8},
    "validation": {},
    "conclusions": "PSV alone is not acceptable; a PAHH is required.",
}


def test_valid_block_has_no_errors_or_warnings():
    errors, warnings = v._validate_existing_wells_comparison(BLOCK)
    assert errors == [] and warnings == []


def test_block_must_be_object():
    errors, _ = v._validate_existing_wells_comparison([1])
    assert errors


def test_not_applicable_needs_reason():
    assert v._validate_existing_wells_comparison({"not_applicable": "no existing producers"}) == ([], [])
    _, warnings = v._validate_existing_wells_comparison({"not_applicable": ""})
    assert warnings


def test_missing_parts_warn():
    _, warnings = v._validate_existing_wells_comparison({"blocked_outlet": {"statement": "x"}})
    text = " ".join(warnings)
    for part in ("existing_inputs", "relief_basis", "ratios", "relief_effect", "governs", "inflow_cases"):
        assert part in text


def test_overpressure_study_without_block_is_warned():
    _, warnings = v.validate(dict(OVERPRESSURE_RESULTS))
    assert any("existing_wells_comparison" in w for w in warnings)
    results = dict(OVERPRESSURE_RESULTS, existing_wells_comparison=BLOCK)
    _, warnings = v.validate(results)
    assert not any("existing_wells_comparison" in w for w in warnings)


def test_other_tasks_are_not_warned():
    results = {"task_statement": "Sizing of a heat exchanger with one PSV mention.", "approach": "LMTD method", "key_results": {"UA_kW/K": 10.0},
               "validation": {}, "conclusions": "Area 12 m2."}
    _, warnings = v.validate(results)
    assert not any("existing_wells_comparison" in w for w in warnings)
