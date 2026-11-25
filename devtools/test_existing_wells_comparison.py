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


SCENARIO_REGISTER = {"scenarios": [
    {"id": "S1", "study_status": "calculated", "valves": "riser EV failed open, one choke collapsed, PV 100 % open", "limit": "stored gas and PV Cv",
     "relief": "171 kg/s", "governing": "yes"},
    {"id": "S7", "study_status": "simple estimate", "valves": "PV closed, riser EV open", "limit": "well deliverability", "relief": "8-13 kg/s", "governing": "no"}]}
VALVE_CV_INPUTS = {"inputs": [
    {"element": "topside choke 16-HV-0564/0565", "value": "100 datasheet / 178 tuned", "source": "STID 11-4A-REE-C54-10035 rev 05H note 6.6", "grade": "A"},
    {"element": "subsea choke", "value": "not found", "source": "ASSUMED not limiting", "grade": "D"}]}
CV_SENSITIVITY = {"ivo_cc": {"choke_total_cv": [{"cv": 178, "relief_kgs": 182}]}, "blocked_outlet": {"tune": [{"cv": 25, "q_kgs": 7.5}]},
                  "findings": {"depends_on": "PV Cv and stored gas", "not_dependent_on": "choke Cv above about 750"}}


def _with_cv_blocks(results):
    return dict(results, existing_wells_comparison=BLOCK, scenario_register=SCENARIO_REGISTER, valve_cv_inputs=VALVE_CV_INPUTS, cv_sensitivity=CV_SENSITIVITY)


def test_overpressure_study_without_cv_blocks_is_warned():
    _, warnings = v.validate(dict(OVERPRESSURE_RESULTS, existing_wells_comparison=BLOCK))
    text = " ".join(warnings)
    for key in ("scenario_register", "valve_cv_inputs", "cv_sensitivity"):
        assert key in text


def test_complete_cv_blocks_have_no_warnings():
    errors, warnings = v._validate_scenario_cv_blocks(_with_cv_blocks(OVERPRESSURE_RESULTS))
    assert errors == [] and warnings == []
    _, warnings = v.validate(_with_cv_blocks(OVERPRESSURE_RESULTS))
    assert not any(k in w for w in warnings for k in ("scenario_register", "valve_cv_inputs", "cv_sensitivity"))


def test_incomplete_rows_and_one_sided_sensitivity_warn():
    results = dict(OVERPRESSURE_RESULTS, scenario_register={"scenarios": [{"id": "S1", "study_status": "calculated"}]},
                   valve_cv_inputs={"inputs": [{"element": "choke", "value": "100"}]}, cv_sensitivity={"ivo_cc": {"x": 1}})
    _, warnings = v._validate_scenario_cv_blocks(results)
    text = " ".join(warnings)
    for part in ("valves", "governing", "source", "grade", "blocked_outlet", "findings"):
        assert part in text


def test_cv_blocks_accept_not_applicable_and_reject_wrong_type():
    na = {"not_applicable": "no valves limit the flow"}
    errors, warnings = v._validate_scenario_cv_blocks(dict(OVERPRESSURE_RESULTS, scenario_register=na, valve_cv_inputs=na, cv_sensitivity=na))
    assert errors == [] and warnings == []
    errors, _ = v._validate_scenario_cv_blocks(dict(OVERPRESSURE_RESULTS, scenario_register="x", valve_cv_inputs=na, cv_sensitivity=na))
    assert errors


def test_other_tasks_are_not_warned_about_cv_blocks():
    results = {"task_statement": "Sizing of a heat exchanger with one PSV mention.", "approach": "LMTD method", "key_results": {"UA_kW/K": 10.0},
               "validation": {}, "conclusions": "Area 12 m2."}
    _, warnings = v.validate(results)
    assert not any("scenario_register" in w or "valve_cv_inputs" in w or "cv_sensitivity" in w for w in warnings)
