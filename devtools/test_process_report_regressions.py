"""Regressions for report issues #4793, #4794 and #4795 (synthetic data only)."""
import copy
import os
import sys

import pytest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import pfd_layout_check as chk
import ppo_examples
import ppo_graph
import ppo_layout
import ppo_render
import ppo_validate
import process_overview
import psr_diagrams


@pytest.mark.parametrize("value,expected", [
    (0.0, "0 kg/h"), (-0.0, "0 kg/h"), (0.04, "0.04 kg/h"),
    (-0.04, "-0.04 kg/h"), (0.001, "0.001 kg/h"),
    (0.00001, "<0.001 kg/h"), (-0.00001, ">-0.001 kg/h"),
    (999.0, "999 kg/h"), (1000.0, "1 t/h"), (-1000.0, "-1 t/h"),
    (None, None), (float("nan"), None), (float("inf"), None),
])
def test_mass_labels_preserve_nonzero_and_unavailable(value, expected):
    assert ppo_layout._fmt_mass(value) == expected


def test_power_annotations_are_external_but_still_checked(tmp_path):
    manifest = psr_diagrams.build_pfd(
        ppo_examples.gas_plant_data(), str(tmp_path), max_units_per_sheet=6,
    )
    reports = chk.review_many([s["svg"] for s in manifest["sheets"]], str(tmp_path / "review"))
    assert not [f for _, r in reports for f in r.findings
                if f["code"] == "NODE_TEXT_OVERFLOW" and "shaft power" in f["message"]]
    for kind, jc in (("pump", "Pump"), ("compressor", "Compressor")):
        sym = psr_diagrams.make_symbol(kind, jc)
        assert sym.extra_width() >= 2 * (3.3 + psr_diagrams.text_w("shaft power", psr_diagrams.DESC_FS))
    sheet = next(s for s in manifest["sheets"] if "P-200" in s["units"])
    spec = chk.load_svg(sheet["svg"])
    node = next(n for n in spec["nodes"] if n["id"] == "P-200")
    label = next(t for t in node["texts"] if t["text"] == "shaft power")
    assert label["kind"] == "outside"
    broken = copy.deepcopy(spec)
    pump = next(n for n in broken["nodes"] if n["id"] == "P-200")
    pump["texts"].append(dict(label, kind="inside", text="real overflow", bbox=[0, 0, 40, 4]))
    assert any(f["code"] == "NODE_TEXT_OVERFLOW" for f in chk.check_spec(broken).findings)
    # External labels retain collision, line-through-text, font and arrow checks.
    broken["labels"].append(dict(label, text="collision"))
    broken["edges"].append({"id": "test-line", "stream": "S999", "points": [
        [label["x"] - 10, label["y"] - 0.5], [label["x"] + 30, label["y"] - 0.5],
    ], "arrow_end": False})
    pump["texts"].append(dict(label, text="tiny", font_size=0.1))
    codes = {f["code"] for f in chk.check_spec(broken).findings}
    assert {"LABEL_LABEL", "LABEL_LINE", "TEXT_TOO_SMALL", "NO_ARROWHEAD"} <= codes


@pytest.mark.parametrize("mass,expected", [
    (22.0, "0.022"), (0.04, "4e-05"), (-0.04, "-4e-05"),
    (0.00001, "<1e-06"), (-0.00001, ">-1e-06"), (0.0, "0"), (None, "-"),
])
def test_key_stream_table_retains_small_flows(mass, expected):
    data = ppo_examples.gas_plant_data()
    water = next(s for s in data["streams"] if s["id"] == "S037")
    water["results"]["A"]["mass_kg_h"] = mass
    original = copy.deepcopy(data)
    graph = ppo_graph.build_graph(data, case_id="A")
    style = ppo_render.make_style("a3")
    fonts = ppo_render.Fonts(style)
    layout = process_overview.fit_layout(graph, fonts, style, {})
    scene = ppo_render.Scene(420, 297)
    process_overview._draw_stream_table(scene, layout, graph, fonts, style,
                                       ppo_render.PALETTE_COLOUR, 10, 10, 142, 120)
    assert expected in [t["text"] for t in scene.texts()]
    assert data == original


def dense_area_data():
    """A single uncollapsible chain larger than a 16:9 detail frame."""
    data = ppo_examples.gas_plant_data()
    units, streams = [], []
    for i in range(18):
        tag = "K-%03d" % i
        units.append(ppo_examples._unit(tag, "compressor", "Compressor", "dense",
                                       ["S%03d" % i], ["S%03d" % (i + 1)], power_kw=100))
        streams.append(ppo_examples._stream(
            "S%03d" % i, "Chain %d" % i, "feed" if i == 0 else "internal",
            None if i == 0 else "K-%03d" % (i - 1), tag, 1000, 30, 50,
            ppo_examples._phases(gas=1), "dense",
        ))
    streams.append(ppo_examples._stream("S018", "Product", "product", "K-017", None,
                                        1000, 30, 50, ppo_examples._phases(gas=1), "dense"))
    data.update(equipment=units, streams=streams, areas=[{"name": "dense", "units": [u["tag"] for u in units]}])
    return data


def test_dense_supporting_slide_is_blocked_before_export(tmp_path):
    with pytest.raises(process_overview.OverviewError, match="supporting slide"):
        process_overview.build_overview(dense_area_data(), str(tmp_path), detail_sheets="none", png_dpi=40)
    assert not list(tmp_path.glob("overview_16x9_detail_*.pdf"))


def test_main_overviews_can_omit_support_but_keep_a3_details(tmp_path):
    manifest = process_overview.build_overview(
        dense_area_data(), str(tmp_path), supporting_slides=False,
        detail_sheets="all", mono=False, png_dpi=40,
    )
    assert "overview_A3_colour" in manifest["files"] and "overview_16x9" in manifest["files"]
    assert manifest["detail_sheets"]["sheets"]
    assert not any(k.startswith("overview_16x9_detail_") for k in manifest["files"])
    args = process_overview.build_parser().parse_args(["data.json", "--out", "out", "--no-supporting-slides"])
    assert args.no_supporting_slides


@pytest.mark.parametrize("fits", [False, True])
def test_validator_rejects_nonfitting_or_untracked_support(fits):
    data = ppo_examples.gas_plant_data()
    graph = ppo_graph.build_graph(data, case_id="A")
    style = ppo_render.make_style("a3")
    fonts = ppo_render.Fonts(style)
    layout = process_overview.fit_layout(graph, fonts, style, {})
    spec = ppo_layout.to_layout_spec(layout, graph, fonts, style, style["page"], "test")
    layouts = {"a3": (layout, spec, style, []), "16x9_supporting": [
        {"section": "dense", "file": "overview_16x9_detail_01", "fits": fits},
    ]}
    result = ppo_validate.validate(graph, data, layouts, {}, fonts, [])
    assert result["status"] == "FAIL"
    assert any(c["id"].startswith("V15") and c["status"] == "FAIL" for c in result["checks"])


@pytest.mark.parametrize("defect", ["clipped", "font", "arrow", "value"])
def test_supporting_slide_runs_geometry_font_arrow_and_value_gates(defect):
    data = ppo_examples.gas_plant_data()
    graph = ppo_graph.build_graph(data, case_id="A", config={"level": "presentation"})
    section = next(s for s in graph["sections"] if s["name"] == "gas export")
    sub = ppo_graph.subgraph(graph, section["id"])
    style = ppo_render.make_style("16x9")
    fonts = ppo_render.Fonts(style)
    layout = process_overview.fit_layout(sub, fonts, style, {}, max_collapse=0)
    ppo_layout.place_annotations(layout, sub, fonts, style, sub["display"]["annotate"],
                                 ppo_render.drawing_area(style))
    tb = ppo_render.resolve_title_block({}, sub)
    scene, extra = process_overview.draw_sheet(layout, sub, fonts, style,
                                              ppo_render.PALETTE_COLOUR, False, tb, "slide")
    spec = ppo_layout.to_layout_spec(layout, sub, fonts, style, style["page"], "test", extra_bboxes=extra)
    entry = {"file": "overview_16x9_detail_01", "fits": True, "graph": sub,
             "layout": (layout, spec, style, [scene])}
    assert not [c for c in ppo_validate._supporting_checks(entry, data) if c["status"] == "FAIL"]
    if defect == "clipped":
        scene.text(style["page"]["w"] + 10, 20, "clipped title", 12)
    elif defect == "font":
        scene.text(20, 20, "tiny process label", 1, kind="tag")
    elif defect == "arrow":
        for edge in spec["edges"]:
            edge.update(arrow_start=False, arrow_mid=False, arrow_end=False)
    else:
        assert layout.annotations
        stream = next(s for s in sub["streams"] if s["id"] == layout.annotations[0]["stream"])
        stream["values"]["mass_kg_h"] += 1.0
    assert any(c["status"] == "FAIL" for c in ppo_validate._supporting_checks(entry, data))


def test_slide_text_outside_page_is_a_validation_failure():
    style = ppo_render.make_style("16x9")
    fonts = ppo_render.Fonts(style)
    graph = ppo_graph.build_graph(ppo_examples.gas_plant_data(), case_id="A")
    layout = process_overview.fit_layout(graph, fonts, style, {})
    scene = ppo_render.Scene(style["page"]["w"], style["page"]["h"])
    scene.text(style["page"]["w"] + 10, 20, "clipped title", 12)
    assert ppo_validate._inside_frame(layout, [scene], fonts, style)["status"] == "FAIL"
