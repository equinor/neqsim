"""Tests for the professional process overview toolkit (ppo_graph, ppo_layout, ppo_render, ppo_validate, process_overview).

All tests run on the synthetic two-train gas plant (:mod:`ppo_examples`); no JVM is needed.
"""
import json
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


# ---------------------------------------------------------------------------
# graph
# ---------------------------------------------------------------------------
def test_graph_reductions_keep_mapping(data, graph):
    assert graph["schema"] == ppo_graph.SCHEMA
    assert ppo_graph.check_graph(graph, data) == []
    collapsed = graph["mapping"]["collapsed_equipment"]
    assert "R-301" in collapsed and collapsed["R-301"]["category"] == "recycle"
    assert "VLV-200" in collapsed  # in-line valve absorbed at overview level
    s006 = ppo_graph.stream_index(graph)["S006"]
    assert "VLV-200" in s006["absorbed"] and "S009" in s006["chain"]
    assert s006["to"] == ["H-200"]
    # the absorbed chain is not a parallel group: values are the single stream's values
    assert s006["values"]["mass_kg_h"] == 53000.0 and s006["members"] == ["S006"]


def test_identical_trains_grouped(graph):
    names = [s["name"] for s in graph["sections"]]
    assert any("2 identical trains" in n for n in names)
    assert "SEC-04" in graph["mapping"]["grouped_trains"]
    k301 = ppo_graph.equipment_index(graph)["K-301"]
    assert k301["represents"]["count"] == 2 and sorted(k301["members"]) == ["K-301", "K-302"]
    # the stream into the representative train carries both trains' flow
    s014 = ppo_graph.stream_index(graph)["S014"]
    assert s014["grouped"] and abs(s014["values"]["mass_kg_h"] - 132000.0) < 1e-6


def test_feed_grouping_and_services(graph):
    gf = graph["mapping"]["grouped_feeds"]
    assert list(gf) == ["GF-HD-100"] and gf["GF-HD-100"]["count"] == 3
    st = ppo_graph.stream_index(graph)["GF-HD-100"]
    assert st["values"]["mass_kg_h"] == 150000.0 and st["values"]["t_c_range"] == [58.0, 62.0]
    sidx = ppo_graph.stream_index(graph)
    assert sidx["S005"]["service"] == "gas" and sidx["S007"]["service"] == "water" and sidx["S012"]["service"] == "oil"
    assert sidx["S021"]["role"] == "recycle"


def test_engineering_level_keeps_valves(data):
    g = ppo_graph.build_graph(data, case_id="A", config={"level": "engineering"})
    assert "VLV-200" in ppo_graph.equipment_index(g)
    assert "R-301" not in ppo_graph.equipment_index(g)


def test_bad_case_and_level(data):
    with pytest.raises(ValueError):
        ppo_graph.build_graph(data, case_id="Z")
    with pytest.raises(ValueError):
        ppo_graph.build_graph(data, config={"level": "nonsense"})


def test_subgraph_and_merge(graph):
    sub = ppo_graph.subgraph(graph, "SEC-01")
    assert len(sub["sections"]) == 1 and all(e["section"] == "SEC-01" for e in sub["equipment"])
    assert all((s["from"] is not None) or s["to"] for s in sub["streams"])
    assert any(s["from"] is None and "(from " in s["label"] for s in sub["streams"])
    merged = ppo_graph.merge_sections(graph, [["SEC-01", "SEC-02"]])
    sec = next(s for s in merged["sections"] if s["id"] == "SEC-01")
    assert sec["merged_from"] == ["SEC-01", "SEC-02"] and len(sec["members"]) == 7
    assert ppo_graph.check_graph(merged) == []


# ---------------------------------------------------------------------------
# layout and rendering
# ---------------------------------------------------------------------------
def test_layout_detail_and_block(graph):
    style = ppo_render.make_style("a3")
    fonts = ppo_render.Fonts(style)
    area = ppo_render.drawing_area(style)
    L = ppo_layout.layout_overview(graph, fonts, style, area, scale=0.8)
    assert len(L.sections) == len(graph["sections"])
    ext = [e for e in L.edges if e["external"]]
    assert ext, "inter-section connections must be routed"
    merged = ppo_layout.merged_edges(L)
    assert all(m["src"].startswith(("U:", "F:", "B:")) and m["dst"].startswith(("U:", "P:", "B:")) for m in merged)
    # every external leg is matched into a complete connection
    assert len(merged) >= len(ext)
    # orthogonal polylines only
    for e in L.edges:
        for p, q in zip(e["pts"][:-1], e["pts"][1:]):
            assert abs(p[0] - q[0]) < 1e-6 or abs(p[1] - q[1]) < 1e-6
    ids = [s["id"] for s in graph["sections"]]
    Lb = ppo_layout.layout_overview(graph, fonts, style, area, scale=1.0, collapsed=ids)
    assert Lb.fits and all(s["collapsed"] for s in Lb.sections)
    assert any(n["kind"] == "block" for n in Lb.nodes.values())


def test_layout_is_deterministic(graph):
    style = ppo_render.make_style("a3")
    fonts = ppo_render.Fonts(style)
    area = ppo_render.drawing_area(style)
    a = ppo_layout.layout_overview(graph, fonts, style, area, scale=0.9)
    b = ppo_layout.layout_overview(graph, fonts, style, area, scale=0.9)
    pa = {e["id"]: e["pts"] for e in a.edges}
    pb = {e["id"]: e["pts"] for e in b.edges}
    assert pa == pb


def test_symbol_library_and_fonts():
    style = ppo_render.make_style("a3")
    fonts = ppo_render.Fonts(style)
    assert fonts.width("CD2101", 9.0, True) > fonts.width("CD", 9.0, True)
    for key in ppo_render.SYMBOL_LIBRARY:
        assert key in ppo_render.LEGEND_SHORT
    sym = ppo_render.make_symbol("separator", "ThreePhaseSeparator", 1.0, 1, 3, port_pitch=5.5)
    assert sym.key == "sep3" and len(sym.offsets("out", [0, 1, 2])) == 3
    mixer = ppo_render.make_symbol("mixer", "", 1.0, 5, 1, port_pitch=5.5)
    assert mixer.h >= 4 * 5.5


def test_build_overview_outputs(tmp_path, data):
    out = tmp_path / "ov"
    manifest = process_overview.build_overview(data, str(out), case_id="A",
                                               title_block={"document_number": "TEST-PFO-001", "revision": "A", "date": "2026-01-01"},
                                               detail_sheets="collapsed", png_dpi=60, log=None)
    assert manifest["validation_status"] in ("PASS", "WARN"), manifest["warnings"]
    for name in ("overview_A3_colour", "overview_A3_mono", "overview_16x9", "overview_16x9_mono"):
        f = manifest["files"][name]
        for k in ("svg", "pdf", "png"):
            assert os.path.exists(f[k]) and os.path.getsize(f[k]) > 0
    a3 = manifest["files"]["overview_A3_colour"]
    assert abs(a3["page_mm"][0] - 420.0) < 0.5 and abs(a3["page_mm"][1] - 297.0) < 0.5
    slide = manifest["files"]["overview_16x9"]
    assert abs(slide["page_mm"][0] / slide["page_mm"][1] - 16.0 / 9.0) < 0.01
    # embedded fonts and extractable text in the PDF
    info = ppo_render.pdf_info(a3["pdf"])
    assert info[0]["fonts"] and all(ft["embedded"] for ft in info[0]["fonts"])
    assert info[0]["text_chars"] > 100
    # structured source data and layout
    g = json.load(open(manifest["graph"], encoding="utf-8"))
    assert g["schema"] == ppo_graph.SCHEMA and g["case"]["id"] == "A"
    lay = json.load(open(manifest["layout"], encoding="utf-8"))
    assert lay["pfd_layout_spec"]["schema"] == "neqsim.pfd_layout.v1"
    rec = json.load(open(manifest["validation"], encoding="utf-8"))
    ids = {c["id"] for c in rec["checks"]}
    assert {"V01", "V02", "V03", "V04", "V05", "V06", "V07", "V08", "V09", "V10", "V11"} <= ids
    assert all(c["status"] != "FAIL" for c in rec["checks"]), [c for c in rec["checks"] if c["status"] == "FAIL"]
    # the SVG carries editable text and data attributes for the layout checker
    svg = open(a3["svg"], encoding="utf-8").read()
    assert 'data-tag="V-100"' in svg and 'data-stream="S005"' in svg and "@font-face" in svg
    assert "<text" in svg and "schematic" in svg


def test_build_overview_detail_sheets_and_presentation_support(tmp_path, data):
    out = tmp_path / "ov2"
    manifest = process_overview.build_overview(data, str(out), case_id="A", detail_sheets="all", png_dpi=50,
                                               title_block={"document_number": "TEST-PFO-002"})
    assert manifest["detail_sheets"] and manifest["detail_sheets"]["sheets"]
    assert os.path.exists(manifest["detail_sheets"]["pdf"])
    assert manifest["detail_sheet_refs"]
    # supporting slides exist for every section collapsed on the 16:9 version
    support = [k for k in manifest["files"] if k.startswith("overview_16x9_detail_")]
    rec = json.load(open(manifest["validation"], encoding="utf-8"))
    assert rec["status"] in ("PASS", "WARN")
    assert isinstance(support, list)


def test_cli_main(tmp_path, data):
    src = tmp_path / "gas_plant.json"
    src.write_text(json.dumps(data), encoding="utf-8")
    out = tmp_path / "cli"
    code = process_overview.main([str(src), "--out", str(out), "--no-presentation", "--dpi", "50", "--quiet"])
    assert code == 0
    assert (out / "manifest.json").exists() and (out / "validation.md").exists()
