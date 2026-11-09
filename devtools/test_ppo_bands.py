"""Tests for the phase-band layout of the process overview (TR2329 SR-15794, ISO 10628:1997 5.3.3).

Synthetic two-train gas plant from :mod:`ppo_examples`; no JVM is needed.
"""
import copy
import os
import sys

import pytest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import ppo_examples  # noqa: E402
import ppo_graph  # noqa: E402
import ppo_layout  # noqa: E402
import ppo_render  # noqa: E402
import process_overview  # noqa: E402

pymupdf = pytest.importorskip("pymupdf")


@pytest.fixture(scope="module")
def data():
    return ppo_examples.gas_plant_data()


@pytest.fixture(scope="module")
def graph(data):
    return ppo_graph.build_graph(data, case_id="A", config={"level": "overview"})


def _layout(graph, assign, scale=0.7, collapsed=()):
    style = ppo_render.make_style("a3")
    fonts = ppo_render.Fonts(style)
    area = ppo_render.drawing_area(style)
    bands = {"assign": assign, "gutter": process_overview.BAND_GUTTER}
    return ppo_layout.layout_overview(graph, fonts, style, area, scale=scale, collapsed=collapsed, bands=bands), fonts, style


def test_assign_bands_classifies_by_dominant_fluid(graph):
    got = ppo_graph.assign_bands(graph, True)
    names = {s["id"]: s["name"] for s in graph["sections"]}
    by_name = {names[k]: v for k, v in got.items()}
    assert by_name["inlet separation"] == "oil"  # separator with gas, oil and water outlets fed by a multiphase stream
    assert by_name["oil stabilisation"] == "oil"
    assert by_name["gas export"] == "gas"
    assert by_name["produced water"] == "water"
    assert [v for k, v in by_name.items() if k.startswith("compression")] == ["gas"]


def test_assign_bands_explicit_mapping_and_validation(graph):
    got = ppo_graph.assign_bands(graph, {"oil": ["gas export"], "gas": ["SEC-06"]})
    assert got["SEC-05"] == "oil" and got["SEC-06"] == "gas" and got["SEC-01"] == "oil"
    with pytest.raises(ValueError):
        ppo_graph.bands_config({"steam": ["x"]})
    assert ppo_graph.bands_config(None) is None and ppo_graph.bands_config(False) is None and ppo_graph.bands_config(True) == {}


def test_band_rows_stack_gas_oil_water_and_flow_left_to_right(graph):
    assign = ppo_graph.assign_bands(graph, True)
    L, _, _ = _layout(graph, assign)
    assert [b["id"] for b in L.bands] == ["gas", "oil", "water"]
    lanes = L.bands
    for a, b in zip(lanes[:-1], lanes[1:]):  # lanes touch, top to bottom
        assert abs(a["y"] + a["h"] - b["y"]) < 1e-6
    secs = {s["id"]: s for s in L.sections}
    row = {"gas": 0, "oil": 1, "water": 2}
    for sid, band in assign.items():
        s = secs[sid]
        lane = lanes[row[band]]
        assert lane["y"] - 1e-6 <= s["y"] and s["y"] + s["h"] <= lane["y"] + lane["h"] + 1e-6, (sid, band)
        assert s["band"] == band
    # inside a band the sections follow the flow: inlet separation left of oil stabilisation
    assert secs["SEC-01"]["x"] < secs["SEC-02"]["x"]
    # a row that is fed from another row starts to the right of the feeding column
    assert secs["SEC-03"]["x"] >= secs["SEC-01"]["x"] + secs["SEC-01"]["w"] - 1e-6
    # lane labels stay clear of the content (gutter at the left of the drawing area)
    gutter_right = lanes[0]["x"] + process_overview.BAND_GUTTER
    assert all(s["x"] >= gutter_right - 1e-6 for s in L.sections)


def test_band_layout_passes_layout_review(graph):
    import pfd_layout_check as chk

    assign = ppo_graph.assign_bands(graph, True)
    style = ppo_render.make_style("a3")
    fonts = ppo_render.Fonts(style)
    L = process_overview.fit_layout(graph, fonts, style, {}, bands=True)
    assert L.fits and [b["id"] for b in L.bands]
    graph = getattr(L, "graph_used", graph)
    ppo_layout.place_annotations(L, graph, fonts, style, getattr(L, "annotate_used", graph["display"]["annotate"]), ppo_render.drawing_area(style))
    spec = ppo_layout.to_layout_spec(L, graph, fonts, style, style["page"], "bands-test")
    assert [b["id"] for b in spec["bands"]] == ["gas", "oil", "water"]
    assert any(n.get("band") == "gas" for n in spec["nodes"])
    rep = chk.check_spec(spec, name="bands-test").to_dict()
    errors = [f for f in rep["findings"] if f["severity"] == "ERROR"]
    assert not errors, errors


def test_product_of_another_phase_ends_in_its_band_terminal(graph):
    assign = ppo_graph.assign_bands(graph, {"oil": ["produced water"]})  # water section forced into the oil band
    L, _, _ = _layout(graph, assign)
    terms = [s for s in L.sections if s["terminal"]]
    assert [t["band"] for t in terms] == ["water"] and terms[0]["id"] == "TERM-S037"
    lane = next(b for b in L.bands if b["id"] == "water")
    assert lane["y"] <= terms[0]["y"] and terms[0]["y"] + terms[0]["h"] <= lane["y"] + lane["h"]
    edge = [e for e in L.edges if e["stream"] == "S037" and e["external"]]
    assert len(edge) == 1 and edge[0]["dst"] == "PI:S037@TERM-S037"
    assert not any(n["id"] == "P:S037" and n["section"] != "TERM-S037" for n in L.nodes.values())


def test_default_layout_is_unchanged_by_the_band_option(graph):
    style = ppo_render.make_style("a3")
    fonts = ppo_render.Fonts(style)
    area = ppo_render.drawing_area(style)
    a = ppo_layout.layout_overview(graph, fonts, style, area, scale=0.9)
    b = ppo_layout.layout_overview(graph, fonts, style, area, scale=0.9, bands=None)
    assert a.bands == [] and not any(s["terminal"] for s in a.sections)
    assert {e["id"]: e["pts"] for e in a.edges} == {e["id"]: e["pts"] for e in b.edges}


def test_build_overview_with_bands(tmp_path, data):
    import json

    out = tmp_path / "bands"
    manifest = process_overview.build_overview(data, str(out), case_id="A", bands=True, presentation=False, png_dpi=50,
                                               title_block={"document_number": "TEST-BANDS-001"})
    assert manifest["validation_status"] in ("PASS", "WARN"), manifest["warnings"]
    assert set(manifest["bands"].values()) <= {"gas", "oil", "water"} and manifest["bands"]
    spec = json.load(open(out / "pfd_layout_spec_A3.json", encoding="utf-8"))
    assert [b["id"] for b in spec["bands"]] == ["gas", "oil", "water"]
    rec = json.load(open(manifest["validation"], encoding="utf-8"))
    assert all(c["status"] != "FAIL" for c in rec["checks"]), [c for c in rec["checks"] if c["status"] == "FAIL"]
    svg = open(manifest["files"]["overview_A3_colour"]["svg"], encoding="utf-8").read()
    assert 'data-band="gas"' in svg and ">WATER<" in svg
    # the config key switches the same layout on; without it nothing is banded
    out2 = tmp_path / "cfg"
    m2 = process_overview.build_overview(data, str(out2), case_id="A", config={"bands": True}, presentation=False, png_dpi=50)
    assert m2["bands"] == manifest["bands"]
    m3 = process_overview.build_overview(data, str(tmp_path / "plain"), case_id="A", presentation=False, png_dpi=50)
    assert m3["bands"] is None


def test_header_texts_never_overlap(graph, data):
    style = ppo_render.make_style("a3")
    fonts = ppo_render.Fonts(style)
    g = copy.deepcopy(graph)
    g["case"]["label"] = "Current operation (PDM 90-day mean, metered gas, long description of the displayed case)"
    tb = ppo_render.resolve_title_block({"document_number": "TEST-HEAD-001",
                                         "title": "A very long process flow diagram title from well to gas injection and oil export"}, g)
    L = process_overview.fit_layout(g, fonts, style, {})
    sc, _ = process_overview.draw_sheet(L, g, fonts, style, ppo_render.PALETTE_COLOUR, False, tb, "a3")
    texts = {t["kind"]: ppo_render.text_bbox(fonts, t) for t in sc.texts() if t["kind"] in ("title", "subtitle")}
    assert "title" in texts and "subtitle" in texts
    t, s = texts["title"], texts["subtitle"]
    assert t[2] <= s[0] - 1.0 or s[2] <= t[0] or t[3] <= s[1] or s[3] <= t[1]
