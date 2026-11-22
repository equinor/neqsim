"""Tests for the connectivity audit and the wiring list of the process-study-report toolkit (no JVM needed)."""

import os
import sys

import pytest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import process_report  # noqa: E402
import psr_connectivity as pc  # noqa: E402
import psr_wiring  # noqa: E402


def _stream(sid, label, area="A", producers=(), consumers=(), mass=100.0):
    kind = ("feed" if not producers else "internal") if consumers else ("product" if producers else "isolated")
    return {"id": sid, "name": label, "label": label, "area": area, "producers": list(producers), "consumers": list(consumers),
            "kind": kind, "results": {"A": {"mass_kg_h": mass}}}


def _unit(tag, cat, ins, outs, area="A", cls="X"):
    return {"tag": tag, "category": cat, "inlet_streams": list(ins), "outlet_streams": list(outs), "area": area, "java_class": cls}


def _data(units, streams):
    return {"equipment": units, "streams": streams, "cases": [{"id": "A"}]}


def test_clean_model_passes():
    data = _data([_unit("v", "valve", ["S1"], ["S2"])],
                 [_stream("S1", "feed", consumers=["v"]), _stream("S2", "out", producers=["v"])])
    audit = pc.audit_connectivity(data)
    assert audit["status"] == "PASS"
    assert audit["findings"] == []


def test_open_recycle_column_and_isolated_stream_are_errors_with_candidates():
    units = [_unit("sep", "separator", ["S1"], ["S2", "S3"]),
             _unit("liq recycle", "recycle", [], ["S4"]),
             _unit("mix", "mixer", ["S4"], ["S5"]),
             _unit("col", "column", [], ["S6"]),
             _unit("heater", "heater", [], [])]
    streams = [_stream("S1", "feed", consumers=["sep"]), _stream("S2", "gas out", producers=["sep"]),
               _stream("S3", "liquid out for liq", producers=["sep"]),
               _stream("S4", "liq recycle_guess", producers=["liq recycle"], consumers=["mix"]),
               _stream("S5", "mixed", producers=["mix"]),
               _stream("S6", "col gas out", producers=["col"]),
               _stream("S7", "orphan", mass=5.0)]
    audit = pc.audit_connectivity(_data(units, streams))
    codes = {(f["code"], f["name"]) for f in audit["findings"]}
    assert audit["status"] == "FAIL"
    assert ("RECYCLE-NO-INLET", "liq recycle") in codes
    assert ("COLUMN-NO-FEED", "col") in codes
    assert ("UNIT-UNCONNECTED", "heater") in codes
    assert ("MIXER-SINGLE-INLET", "mix") in codes
    assert ("STREAM-ISOLATED", "S7") in codes
    recycle = [f for f in audit["findings"] if f["name"] == "liq recycle"][0]
    assert recycle["candidates"][0]["stream"] == "S3"
    assert all(c["stream"] != "S4" for c in recycle["candidates"])


def test_zero_flow_isolated_stream_is_a_warning_and_notes_are_short():
    units = [_unit("liq recycle", "recycle", [], ["S1"])]
    streams = [_stream("S1", "t", producers=["liq recycle"], consumers=["x"]), _stream("S2", "z", mass=0.0)]
    audit = pc.audit_connectivity(_data(units, streams))
    iso = [f for f in audit["findings"] if f["code"] == "STREAM-ISOLATED"][0]
    assert iso["severity"] == pc.SEVERITY_WARNING
    assert pc.diagram_notes(audit)["liq recycle"] == ["NOT CONNECTED: recycle has no inlet"]
    assert pc.diagram_notes(audit)["x"] == ["inlet from open recycle"]


def test_audit_files_are_written(tmp_path):
    audit = pc.audit_connectivity(_data([_unit("c", "column", [], ["S1"])], [_stream("S1", "o", producers=["c"])]))
    jp, cp = pc.write_audit(audit, str(tmp_path))
    assert os.path.getsize(jp) > 0
    with open(cp, encoding="utf-8") as fh:
        assert "COLUMN-NO-FEED" in fh.read()


def test_wiring_list_is_validated_and_kept_in_cases_options():
    cases, options = process_report.parse_cases({"cases": [{"id": "A"}],
                                                 "wiring": [{"unit": "a::r", "inlet": "a::v.outlet"}]})
    assert options["wiring"][0]["unit"] == "a::r"
    with pytest.raises(process_report.ReportError):
        process_report.parse_cases({"cases": [{"id": "A"}], "wiring": [{"unit": "a::r"}]})
    with pytest.raises(psr_wiring.WiringError):
        psr_wiring.validate_entries("not a list")
