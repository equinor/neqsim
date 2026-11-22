"""Tests for the balance bookkeeping added for stream saturators, two-stream exchangers and column duties (no JVM)."""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import psr_balances as pb  # noqa: E402


def _stream(sid, mass, water, h=0.0):
    return {"id": sid, "results": {"A": {"available": True, "mass_kg_h": mass, "H_kw": h, "cp_kj_kg_k": 2.0,
                                          "flow_kg_h": {"methane": mass - water, "water": water}}}}


TOL = {"mass_abs_kg_h": 1e-3, "mass_rel": 1e-4, "component_rel": 1e-4, "energy_abs_kw": 1.0, "energy_rel": 1e-3,
       "recycle_mass_rel": 1e-3, "energy_dt_bookkeeping_k": 2.0, "energy_rel_bookkeeping": 0.02}


def test_saturator_residual_is_a_note_when_it_equals_the_added_water():
    streams = {"S1": _stream("S1", 1000.0, 0.0, -50.0), "S2": _stream("S2", 1010.0, 10.0, -20.0)}
    unit = {"tag": "sat", "area": "a", "category": "other", "java_class": "StreamSaturatorUtil",
            "inlet_streams": ["S1"], "outlet_streams": ["S2"], "results": {"A": {}}}
    rec = pb.unit_balance(unit, streams, "A", ["methane", "water"], TOL)
    assert rec["mass_status"] == "NOTE"
    assert rec["component_status"] == "NOTE"
    assert rec["energy_status"] == "NOTE"
    assert abs(rec["energy_res_kw"] - 30.0) < 1e-9


def test_other_units_with_a_mass_gain_still_fail():
    streams = {"S1": _stream("S1", 1000.0, 0.0), "S2": _stream("S2", 1010.0, 10.0)}
    unit = {"tag": "v", "area": "a", "category": "valve", "java_class": "ThrottlingValve",
            "inlet_streams": ["S1"], "outlet_streams": ["S2"], "results": {"A": {}}}
    rec = pb.unit_balance(unit, streams, "A", ["methane", "water"], TOL)
    assert rec["mass_status"] == "FAIL"


def test_two_stream_exchanger_duty_is_internal_and_heater_duty_is_external():
    hx = {"category": "exchanger", "inlet_streams": ["a", "b"], "outlet_streams": ["c", "d"], "results": {"A": {"duty_kw": -500.0}}}
    one_sided = {"category": "exchanger", "inlet_streams": ["a"], "outlet_streams": ["c"], "results": {"A": {"duty_kw": -500.0}}}
    heater = {"category": "heater", "inlet_streams": ["a"], "outlet_streams": ["c"], "results": {"A": {"duty_kw": 100.0}}}
    assert pb._ext_duty(hx, "A") == 0.0
    assert pb._ext_duty(one_sided, "A") == -500.0
    assert pb._ext_duty(heater, "A") == 100.0
