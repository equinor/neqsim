"""Tests for the deterministic PFD layout checker (pfd_layout_check), the SVG loader and the PfdDiagram builder.

Good and bad examples: synthetic specs for each rule, the defective Norne TEG PFD (negative case, reconstructed from the
original generator geometry) and its re-laid-out version (positive case), small SVG drawings and raster files.
"""
import copy
import json
import os
import sys

import pytest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import pfd_layout_check as chk  # noqa: E402

EXAMPLES = os.path.join(HERE, "pfd_layout_examples")


def node(nid, x, y, w=14, h=8, kind="vessel", text=None, **kw):
    n = {"id": nid, "kind": kind, "bbox": [x, y, w, h], "tag": kw.pop("tag", None), "style": {"shape": kind}, "texts": []}
    if text:
        n["texts"].append({"text": text, "x": x + w / 2, "y": y + h / 2, "font_size": 2.2, "anchor": "middle", "baseline": False,
                           "bbox": [x + 2, y + h / 2 - 1.2, w - 4, 2.4], "kind": "inside"})
    n.update(kw)
    return n


def edge(eid, pts, a=None, b=None, fluid="gas", stream=None, label=None, **kw):
    e = {"id": eid, "points": [list(p) for p in pts], "arrow_end": True, "fluid": fluid, "from": a, "to": b, "stream": stream,
         "style": {"color": "#C0392B" if fluid == "gas" else "#2471A3", "width": "1"}}
    if label:
        e["label"] = {"text": label[0], "font_size": 2.0, "bbox": list(label[1]), "kind": "stream"}
    e.update(kw)
    return e


def good_spec():
    """Feed -> separator -> compressor, clean layout with tags, stream ids, legend, title and footer."""
    return {"schema": chk.SCHEMA, "kind": "pfd", "name": "good", "canvas": {"width": 100, "height": 60, "unit": "u", "intended_width_mm": 165},
            "nodes": [node("feed", 4, 24, 14, 8, "boundary", text="Feed gas"),
                      node("v1", 36, 22, 16, 12, "vessel", text="20V0001\nseparator", tag="20V0001"),
                      node("k1", 74, 24, 14, 8, "compressor", text="23K0001\ncompressor", tag="23K0001")],
            "edges": [edge("e1", [(18, 28), (36, 28)], "feed", "v1", stream="1", label=("30 bara", (22, 23.5, 10, 2.4))),
                      edge("e2", [(52, 28), (74, 28)], "v1", "k1", stream="2", label=("28 bara", (56, 23.5, 10, 2.4)))],
            "labels": [{"text": "1", "bbox": [26, 27, 3.4, 3.4], "kind": "streamno", "owner_edge": "e1", "font_size": 2.0},
                       {"text": "2", "bbox": [61, 27, 3.4, 3.4], "kind": "streamno", "owner_edge": "e2", "font_size": 2.0}],
            "title": True, "legend": True, "footer": True, "junctions": [], "arrow_length": 1.6}


def codes(rep, severity=None):
    return {f["code"] for f in rep.findings if severity is None or f["severity"] == severity}


def test_good_diagram_passes():
    rep = chk.check_spec(good_spec())
    assert rep.status == "PASS", rep.to_markdown()
    assert rep.score == 100.0


def test_report_markdown_and_dict():
    rep = chk.check_spec(good_spec())
    d = rep.to_dict()
    assert d["status"] == "PASS" and d["schema"] == "neqsim.pfd_layout_review.v1"
    assert "PASS" in rep.to_markdown()


def test_box_overlap_and_touch():
    s = good_spec()
    s["nodes"].append(node("v2", 44, 26, 16, 12, "vessel", text="20V0002", tag="20V0002"))
    s["edges"].append(edge("e3", [(60, 32), (74, 32)], "v2", "k1", stream="3"))
    rep = chk.check_spec(s)
    assert "BOX_OVERLAP" in codes(rep, "ERROR") and rep.status == "FAIL"
    f = [x for x in rep.findings if x["code"] == "BOX_OVERLAP"][0]
    assert "Move" in f["fix"]
    s2 = good_spec()
    s2["nodes"].append(node("v2", 52.2, 22, 16, 12, "vessel", text="20V0002", tag="20V0002"))
    s2["edges"].append(edge("e3", [(68.2, 28), (74, 28)], "v2", "k1", stream="3"))
    assert "BOX_TOUCH" in codes(chk.check_spec(s2), "WARN")


def test_label_collisions():
    s = good_spec()
    s["edges"][0]["label"]["bbox"] = [22, 27.2, 10, 2.4]  # sits on its own line
    assert "LABEL_LINE" in codes(chk.check_spec(s), "ERROR")
    s = good_spec()
    s["edges"][1]["label"]["bbox"] = [48, 23.5, 10, 2.4]  # overlaps the separator box
    assert "LABEL_BOX" in codes(chk.check_spec(s), "ERROR")
    s = good_spec()
    s["edges"][0]["label"]["bbox"] = [60, 23.5, 10, 2.4]
    s["edges"][1]["label"]["bbox"] = [64, 23.5, 10, 2.4]
    assert "LABEL_LABEL" in codes(chk.check_spec(s), "ERROR")


def test_line_through_box_and_crossing():
    s = good_spec()
    s["edges"][0]["points"] = [[18, 28], [30, 28], [30, 40], [60, 40], [60, 28], [74, 28]]
    s["edges"][0]["to"] = "k1"
    s["nodes"].append(node("tank", 40, 36, 12, 8, "tank", text="21T tank", tag="21T"))
    rep = chk.check_spec(s)
    assert "LINE_THROUGH_BOX" in codes(rep, "ERROR")
    s = good_spec()
    s["nodes"].append(node("a", 21, 4, 12, 8, "vessel", text="20V0010", tag="20V0010"))
    s["nodes"].append(node("b", 21, 44, 12, 8, "vessel", text="20V0011", tag="20V0011"))
    s["edges"].append(edge("e3", [(27, 12), (27, 44)], "a", "b", stream="3"))
    rep = chk.check_spec(s)
    assert "CROSSING" in codes(rep) and rep.metrics["crossings"] >= 1


def test_crossing_with_junction_is_not_a_crossing_and_merge_needs_junction():
    s2 = good_spec()
    s2["edges"][0]["points"] = [[18, 28], [30, 28]]
    s2["edges"][0]["to"] = None
    s2["edges"].append(edge("e3", [(30, 44), (30, 28), (36, 28)], None, "v1", stream="3"))
    rep2 = chk.check_spec(s2)
    assert "MERGE_WITHOUT_JUNCTION" in codes(rep2, "ERROR"), rep2.to_markdown()
    s3 = copy.deepcopy(s2)
    s3["junctions"] = [[30, 28]]
    assert "MERGE_WITHOUT_JUNCTION" not in codes(chk.check_spec(s3))


def test_collinear_overlap():
    s = good_spec()
    s["edges"].append(edge("e3", [(40, 28), (80, 28)], None, None, stream="3", open_end=True))
    assert "LINE_OVERLAP" in codes(chk.check_spec(s), "ERROR")


def test_arrowheads_and_direction():
    s = good_spec()
    s["edges"][0]["arrow_end"] = False
    rep = chk.check_spec(s)
    assert "NO_ARROWHEAD" in codes(rep, "ERROR")
    s = good_spec()
    s["edges"][0]["arrow_end"] = False
    s["edges"][0]["arrow_mid"] = True
    assert "NO_ARROWHEAD" in codes(chk.check_spec(s), "WARN")


def test_unconnected_and_missing_tag_and_streams():
    s = good_spec()
    s["nodes"].append(node("lonely", 40, 44, 12, 8, "vessel", text="spare vessel"))
    rep = chk.check_spec(s)
    assert "UNCONNECTED_NODE" in codes(rep, "ERROR") and "MISSING_TAG" in codes(rep, "WARN")
    s = good_spec()
    for e in s["edges"]:
        e["stream"] = None
    for lb in s["labels"]:
        lb["owner_edge"] = None
    assert "MISSING_STREAM_ID" in codes(chk.check_spec(s), "WARN")
    s["kind"] = "bfd"
    assert "MISSING_STREAM_ID" not in codes(chk.check_spec(s))


def test_canvas_and_font_size():
    s = good_spec()
    s["labels"].append({"text": "far away", "bbox": [120, 5, 8, 2.4], "font_size": 2.0, "kind": "note"})
    assert "TEXT_OUTSIDE_CANVAS" in codes(chk.check_spec(s), "ERROR")
    s = good_spec()
    s["edges"][0]["label"]["font_size"] = 0.9
    s["canvas"]["intended_width_mm"] = 60
    assert "TEXT_TOO_SMALL" in codes(chk.check_spec(s))


def test_flow_direction_and_phase_sides_and_orientation():
    s = good_spec()
    s["nodes"][0]["bbox"] = [80, 24, 14, 8]
    s["nodes"][2]["bbox"] = [4, 24, 14, 8]
    s["edges"] = [edge("e1", [(80, 28), (52, 28)], "feed", "v1", stream="1"), edge("e2", [(36, 28), (18, 28)], "v1", "k1", stream="2")]
    for lb in s["labels"]:
        lb["owner_edge"] = None
    rep = chk.check_spec(s)
    assert "FLOW_DIRECTION" in codes(rep, "WARN")
    s = good_spec()
    s["nodes"][1]["kind"] = "column"
    s["nodes"][1]["bbox"] = [36, 26, 16, 8]  # horizontal column
    assert "VESSEL_ORIENTATION" in codes(chk.check_spec(s), "WARN")
    s = good_spec()
    s["edges"][1]["points"] = [[44, 34], [44, 46], [74, 46], [74, 32]]
    s["edges"][1]["fluid"] = "gas"
    s["edges"][1]["to"] = "k1"
    assert "PHASE_SIDE" in codes(chk.check_spec(s), "WARN")


def test_ports_and_diagonal_and_corner():
    s = good_spec()
    s["nodes"].append(node("w", 4, 44, 14, 8, "vessel", text="44V0001", tag="44V0001"))
    s["edges"].append(edge("e3", [(36, 32), (25, 32), (25, 48), (18, 48)], "v1", "w", stream="3"))
    rep = chk.check_spec(s)
    assert "PORT_AMBIGUOUS" in codes(rep, "WARN"), rep.to_markdown()
    s = good_spec()
    s["nodes"].append(node("w", 4, 4, 14, 8, "boundary", text="Second feed"))
    s["edges"].append(edge("e3", [(18, 29), (36, 29)], "w", "v1", stream="3"))
    assert "PORT_CROWDED" in codes(chk.check_spec(s), "WARN")
    s["edges"][0]["points"] = [[18, 28], [36, 28.0], [36, 28]]
    s["edges"][1]["points"] = [[52, 28], [60, 26], [74, 28]]
    rep = chk.check_spec(s)
    assert "LINE_DIAGONAL" in codes(rep, "WARN")


def test_missing_furniture_and_waiver():
    s = good_spec()
    s["title"] = False
    s["footer"] = False
    s["edges"][0]["style"]["color"] = "#2471A3"
    s["legend"] = False
    rep = chk.check_spec(s)
    assert {"MISSING_TITLE", "MISSING_FOOTER", "MISSING_LEGEND"} <= codes(rep, "WARN")
    s["waivers"] = [{"code": "MISSING_LEGEND", "reason": "single-sheet sketch"}]
    rep = chk.check_spec(s)
    assert "MISSING_LEGEND" not in codes(rep, "WARN") and rep.waived


def test_whitespace_pocket():
    s = good_spec()
    s["nodes"] = [node("n%d" % i, 4 + 20 * (i % 5), 4 + 12 * (i // 5), 12, 6, "vessel", text="2%dV000%d" % (i, i), tag="2%dV000%d" % (i, i)) for i in range(5)]
    s["nodes"] += [node("m%d" % i, 4 + 20 * i, 52, 12, 6, "vessel", text="3%dV000%d" % (i, i), tag="3%dV000%d" % (i, i)) for i in range(2)]
    s["edges"] = [edge("e%d" % i, [(16 + 20 * i, 7), (24 + 20 * i, 7)], "n%d" % i, "n%d" % (i + 1), stream=str(i)) for i in range(4)]
    s["edges"].append(edge("e9", [(10, 10), (10, 52)], "n0", "m0", stream="9"))
    s["edges"].append(edge("e10", [(30, 58), (24, 58)], "m1", "m0", stream="10"))
    s["labels"] = []
    s["canvas"]["height"] = 66
    rep = chk.check_spec(s)
    assert "WHITESPACE_POCKET" in codes(rep, "WARN"), rep.to_markdown()


def test_negative_norne_teg_example_fails_and_fixed_passes():
    bad = chk.review(os.path.join(EXAMPLES, "teg_defective.json"))
    assert bad.status == "FAIL"
    assert {"BOX_OVERLAP", "CROSSING", "VESSEL_ORIENTATION", "PHASE_SIDE", "MISSING_STREAM_ID", "LINE_DIAGONAL"} <= codes(bad)
    assert bad.score < 70
    good = chk.review(os.path.join(EXAMPLES, "teg_fixed.json"))
    assert good.status == "PASS" and good.score == 100.0


SVG_GOOD = """<svg xmlns="http://www.w3.org/2000/svg" width="420mm" height="297mm" viewBox="0 0 420 297">
<rect x="0" y="0" width="420" height="297" fill="#fff"/><rect x="5" y="5" width="410" height="287" fill="none" stroke="#111"/>
<text x="12" y="14" font-size="3.6" font-weight="bold" fill="#111">NOR-PFD-01 Test sheet</text>
<g id="equipment"><g data-tag="20V0001"><rect x="100" y="100" width="30" height="20" fill="#fff" stroke="#111"/>
<text x="115" y="97" font-size="2.6" text-anchor="middle" font-weight="bold" fill="#111">20V0001</text></g>
<g data-tag="23K0001"><rect x="200" y="100" width="30" height="20" fill="#fff" stroke="#111"/>
<text x="215" y="97" font-size="2.6" text-anchor="middle" font-weight="bold" fill="#111">23K0001</text></g></g>
<g id="streams"><g data-stream="S001"><polyline points="130,110 200,110" fill="none" stroke="#111"/><polygon points="200,110 197.8,110.8 197.8,109.2" fill="#111"/></g></g>
<rect x="10" y="240" width="120" height="45" fill="none" stroke="#111"/><text x="14" y="246" font-size="3" font-weight="bold">LEGEND</text>
<rect x="260" y="240" width="150" height="45" fill="none" stroke="#111"/><text x="265" y="250" font-size="3">Draft - screening level</text>
</svg>"""


def test_svg_loader_good_and_bad(tmp_path):
    p = tmp_path / "good.svg"
    p.write_text(SVG_GOOD, encoding="utf-8")
    spec = chk.load_svg(str(p))
    assert len(spec["nodes"]) == 2 and len(spec["edges"]) == 1 and spec["edges"][0]["arrow_end"]
    rep = chk.review(str(p))
    assert rep.status in ("PASS", "WARN") and "BOX_OVERLAP" not in codes(rep)
    bad = SVG_GOOD.replace('x="200" y="100"', 'x="120" y="105"').replace('points="130,110 200,110"', 'points="130,110 200,110"').replace('<polygon points="200,110 197.8,110.8 197.8,109.2" fill="#111"/>', "")
    q = tmp_path / "bad.svg"
    q.write_text(bad, encoding="utf-8")
    rep = chk.review(str(q))
    assert rep.status == "FAIL" and "BOX_OVERLAP" in codes(rep)
    assert {"NO_ARROWHEAD", "NO_DIRECTION"} & codes(rep)


def test_svg_without_structure_is_not_a_vacuous_pass(tmp_path):
    p = tmp_path / "plain.svg"
    p.write_text('<svg xmlns="http://www.w3.org/2000/svg" width="297mm" height="210mm" viewBox="0 0 297 210">'
                 '<text x="10" y="20" font-size="4">Map sheet</text></svg>', encoding="utf-8")
    rep = chk.review(str(p))
    assert rep.status == "WARN" and "NO_STRUCTURE" in codes(rep)


def test_raster_is_capped_at_warn(tmp_path):
    PIL = pytest.importorskip("PIL.Image")
    im = PIL.new("RGB", (1000, 700), "white")
    for x in range(100, 900):
        for y in range(300, 304):
            im.putpixel((x, y), (0, 0, 0))
    p = tmp_path / "x.png"
    im.save(p)
    rep = chk.review(str(p))
    assert rep.status == "WARN" and "RASTER_ONLY" in codes(rep) and rep.checklist
    blank = tmp_path / "blank.png"
    PIL.new("RGB", (1000, 700), "white").save(blank)
    assert chk.review(str(blank)).status == "FAIL"


def test_cli_exit_codes_and_outputs(tmp_path):
    good = tmp_path / "good.json"
    good.write_text(json.dumps(good_spec()), encoding="utf-8")
    bad = os.path.join(EXAMPLES, "teg_defective.json")
    out = tmp_path / "rev"
    assert chk.main([str(good), "--quiet", "--out-dir", str(out)]) == 0
    assert (out / "summary.json").exists() and (out / "review.md").exists()
    assert chk.main([bad, "--quiet"]) == 2
    s = good_spec()
    s["title"] = False
    w = tmp_path / "warn.json"
    w.write_text(json.dumps(s), encoding="utf-8")
    assert chk.main([str(w), "--quiet"]) == 1
    assert chk.main([str(w), "--quiet", "--strict"]) == 2


def test_diagram_builder_produces_passing_png_svg_and_spec(tmp_path):
    pytest.importorskip("matplotlib")
    import pfd_diagram

    d = pfd_diagram.PfdDiagram("PFD 1 - Test separation", width_in=7.0, height_in=3.4, footer="Test model; screening level")
    d.node("feed", "Feed gas", 12, 22, kind="boundary")
    d.node("v1", "20V0001\nseparator", 50, 22, kind="vessel", tag="20V0001")
    d.node("k1", "23K0001\ncompressor", 88, 22, kind="compressor", tag="23K0001")
    d.edge("feed", "r", "v1", "l", fluid="gas", stream="1", label="30 bara\n25 \u00b0C")
    d.edge("v1", "r", "k1", "l", fluid="gas", stream="2", label="29 bara\n25 \u00b0C")
    out = tmp_path / "fig.png"
    rep = d.render(str(out))
    assert rep.status == "PASS", rep.to_markdown()
    assert out.exists() and (tmp_path / "fig.svg").exists() and (tmp_path / "fig.layout.json").exists()
    again = chk.review(str(tmp_path / "fig.layout.json"))
    assert again.status == "PASS"
    d2 = pfd_diagram.PfdDiagram("PFD 2 - Overlap", width_in=7.0, height_in=3.4, footer="x")
    d2.node("a", "20V0001\nseparator", 30, 22, kind="vessel", tag="20V0001")
    d2.node("b", "20V0002\nseparator", 36, 22, kind="vessel", tag="20V0002")
    d2.edge("a", "r", "b", "l", fluid="gas", stream="1")
    assert d2.render(str(tmp_path / "bad.png")).status == "FAIL"
