"""Tests for the generic process-report hand-off (no JVM)."""

import csv
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import psr_handoff  # noqa: E402


def _data():
    def res(mass, t):
        return {"available": True, "t_c": t, "p_bara": 40.0, "mass_kg_h": mass, "state": "gas", "z": {"methane": 0.9, "ethane": 0.1}}

    return {
        "meta": {"model_name": "synthetic", "eos": "SRK", "neqsim_version": "x", "standard_conditions": {"t_c": 15.0, "p_bara": 1.01325}},
        "cases": [{"id": "A", "label": "Base"}, {"id": "B", "label": "Turndown"}],
        "components": [{"name": "methane"}, {"name": "ethane"}],
        "streams": [
            {"id": "S1", "label": "feed", "area": "inlet", "kind": "feed", "source": None, "destination": "C1",
             "results": {"A": res(1000.0, 30.0), "B": res(500.0, 30.0)}},
            {"id": "S2", "label": "discharge", "area": "inlet", "kind": "product", "source": "C1", "destination": None,
             "results": {"A": res(1000.0, 90.0), "B": {"available": False}}},
        ],
        "equipment": [
            {"tag": "C1", "area": "inlet", "java_class": "Compressor", "category": "compressor", "inlet_streams": ["S1"],
             "outlet_streams": ["S2"], "design": {"speed": 9000}, "results": {"A": {"power_kw": 120.0, "extras": {"pressure_ratio": 2.0}}, "B": {}}},
        ],
    }


def _basis():
    return {"document": {"title": "T", "document_number": "D-1", "revision": "A", "issue_status": "Draft", "readiness": "screening"},
            "operating_cases": [{"id": "A", "label": "Base", "role": "design", "description": "base case"}],
            "assumptions": [{"id": "AS1", "text": "x"}], "data_gaps": [{"id": "G1", "item": "y"}],
            "conventions": {"units": "SI-like"}}


def test_process_basis_has_streams_equipment_cases_and_diagrams(tmp_path):
    out = str(tmp_path)
    diagrams = {"bfd": {"svg": os.path.join(out, "diagrams", "bfd.svg"), "pdf": None},
                "pfd": {"pdf": os.path.join(out, "diagrams", "pfd.pdf"),
                        "sheets": [{"number": 1, "title": "Inlet", "drawing_number": "D-1-PFD-01", "svg": os.path.join(out, "diagrams", "p1.svg")}]},
                "layout_review": {"status": "PASS", "sheets": 2, "counts": {"PASS": 2}, "dir": "x"}}
    doc = psr_handoff.build_process_basis(_data(), _basis(), [{"id": "STD1", "title": "A standard"}], {"summary": {"n_fail": 0}}, diagrams,
                                         {"status": "PASS", "checks": [{"id": "C1", "status": "PASS"}]}, out, docx_path=os.path.join(out, "r.docx"))
    assert doc["schema"] == "process_basis.v1"
    assert [c["id"] for c in doc["design_cases"]] == ["A", "B"]
    assert doc["design_cases"][0]["role"] == "design" and doc["design_cases"][1]["role"] is None
    assert doc["components"] == ["methane", "ethane"]
    s2 = [s for s in doc["streams"] if s["id"] == "S2"][0]
    assert list(s2["by_case"]) == ["A"]
    assert doc["streams"][0]["source"] == "boundary"
    assert doc["streams"][0]["by_case"]["A"]["mole_fractions"]["methane"] == 0.9
    eq = doc["equipment"][0]
    assert eq["by_case"]["A"]["power_kw"] == 120.0 and eq["design"] == {"speed": 9000}
    assert doc["diagrams"]["pfd_sheets"][0]["svg"] == "diagrams/p1.svg"
    assert doc["diagrams"]["layout_review"]["status"] == "PASS"
    assert doc["report"]["docx"] == "r.docx" and doc["report"]["validation_status"] == "PASS"
    assert doc["assumptions"][0]["id"] == "AS1"


def test_write_handoff_files_roundtrip(tmp_path):
    out = str(tmp_path)
    doc = psr_handoff.build_process_basis(_data(), _basis(), [], None, None, None, out)
    paths = psr_handoff.write_handoff(out, doc, _data())
    assert paths["json"].endswith(os.path.join("handoff", "process_basis.json"))
    with open(paths["json"], encoding="utf-8") as fh:
        assert json.load(fh)["schema"] == "process_basis.v1"
    with open(paths["stream_table"], newline="", encoding="utf-8") as fh:
        rows = list(csv.DictReader(fh))
    assert len(rows) == 3
    assert {r["stream_id"] for r in rows} == {"S1", "S2"}
    assert rows[0]["case"] == "A" and rows[0]["t_c"] == "30.0"
