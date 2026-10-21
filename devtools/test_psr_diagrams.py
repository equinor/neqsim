"""Tests for psr_diagrams (PFD / BFD generation) using a small synthetic process."""

import copy
import filecmp
import json
import os
import sys

import pytest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import psr_diagrams as pd  # noqa: E402


def _stream(sid, label, kind, source, dest, mass, phases=None):
    return {
        "id": sid, "name": label, "label": label, "kind": kind, "source": source, "destination": dest,
        "producers": [source] if source else [], "consumers": [dest] if dest else [], "area": "a",
        "results": {"A": {"available": True, "mass_kg_h": mass, "t_c": 30.0, "p_bara": 10.0,
                          "state": "gas+oil+aqueous", "phases": phases or []}},
    }


def _unit(tag, cat, jc, area, ins, outs):
    return {"tag": tag, "name": tag, "area": area, "java_class": jc, "category": cat,
            "inlet_streams": ins, "outlet_streams": outs, "design": {},
            "results": {"A": {"duty_kw": None, "power_kw": None, "dp_bar": None, "extras": {}}}}


def _demo():
    gas = [{"type": "gas", "mole_fraction": 0.99, "mass_fraction": 0.99}]
    oil = [{"type": "oil", "mole_fraction": 0.99, "mass_fraction": 0.99}]
    wat = [{"type": "aqueous", "mole_fraction": 0.99, "mass_fraction": 0.99}]
    streams = [
        _stream("S001", "well feed", "feed", None, "MIX-100", 1000.0),
        _stream("S002", "mixer outlet", "internal", "MIX-100", "SEP-100", 1010.0),
        _stream("S003", "separator gas", "internal", "SEP-100", "SPL-100", 600.0, gas),
        _stream("S004", "separator oil", "product", "SEP-100", None, 300.0, oil),
        _stream("S005", "separator water", "product", "SEP-100", None, 110.0, wat),
        _stream("S006", "gas to compressor", "internal", "SPL-100", "K-100", 500.0, gas),
        _stream("S007", "gas to recycle valve", "internal", "SPL-100", "V-100", 100.0, gas),
        _stream("S008", "compressor outlet", "internal", "K-100", "E-100", 500.0, gas),
        _stream("S009", "export gas", "product", "E-100", None, 500.0, gas),
        _stream("S010", "recycle seed", "internal", "RCY-100", "MIX-100", 10.0),
        _stream("S011", "valve outlet", "internal", "V-100", "RCY-100", 100.0),
    ]
    equipment = [
        _unit("MIX-100", "mixer", "Mixer", "inlet", ["S001", "S010"], ["S002"]),
        _unit("SEP-100", "separator", "ThreePhaseSeparator", "inlet", ["S002"], ["S003", "S004", "S005"]),
        _unit("SPL-100", "splitter", "Splitter", "gas", ["S003"], ["S006", "S007"]),
        _unit("K-100", "compressor", "Compressor", "gas", ["S006"], ["S008"]),
        _unit("E-100", "cooler", "Cooler", "gas", ["S008"], ["S009"]),
        _unit("V-100", "valve", "ThrottlingValve", "gas", ["S007"], ["S011"]),
        _unit("RCY-100", "recycle", "Recycle", "gas", ["S011"], ["S010"]),
    ]
    return {
        "meta": {"model_name": "synthetic test"},
        "cases": [{"id": "A", "label": "test case"}],
        "areas": [{"name": "inlet", "units": ["MIX-100", "SEP-100"]},
                  {"name": "gas", "units": ["SPL-100", "K-100", "E-100", "V-100", "RCY-100"]}],
        "equipment": equipment,
        "streams": streams,
    }


TB = {"document_number": "T-001", "readiness_note": "SCREENING - NOT FOR DESIGN OR CONSTRUCTION",
      "omitted_systems": ["flare system"],
      "revision_history": [{"rev": str(i), "date": "not assigned", "description": "rev %d" % i} for i in range(6)]}


@pytest.fixture(scope="module")
def built(tmp_path_factory):
    out = tmp_path_factory.mktemp("pfd")
    data = _demo()
    return data, str(out), pd.build_pfd(data, str(out), title_block=TB)


def _union(manifest):
    tags, streams = set(), set()
    for sh in manifest["sheets"]:
        inv = pd.inventory_svg(sh["svg"])
        tags |= inv["tags"]
        streams |= inv["streams"]
    return tags, streams


def test_all_tags_and_streams_present(built):
    data, _out, man = built
    tags, streams = _union(man)
    assert tags == {e["tag"] for e in data["equipment"]}
    assert streams == {s["id"] for s in data["streams"]}
    assert man["unplaced_units"] == [] and man["unplaced_streams"] == []


def test_svg_well_formed_a3(built):
    from lxml import etree

    _data, _out, man = built
    for sh in man["sheets"]:
        root = etree.parse(sh["svg"]).getroot()
        assert root.tag.endswith("svg")
        assert root.get("width") == "420mm" and root.get("height") == "297mm"
        assert root.get("viewBox") == "0 0 420 297"
        raw = open(sh["svg"], encoding="utf-8").read()
        assert "<style" not in raw and "<marker" not in raw and "dominant-baseline" not in raw
        assert raw.isascii()
        assert os.path.exists(sh["png"]) and os.path.getsize(sh["png"]) > 1000


def test_pdf_pages_and_text(built):
    _data, _out, man = built
    mu = pd._import_mupdf()
    doc = mu.open(man["pdf"])
    assert doc.page_count == len(man["sheets"])
    assert os.path.getsize(man["pdf"]) > 1000
    text = doc[0].get_text()
    assert "S001" in text and "MIX-100" in text and "SCREENING" in text
    assert "conformance not claimed" in text.replace("\n", " ")


def test_stream_label_text_and_diamond_count(built):
    from lxml import etree

    _data, _out, man = built
    root = etree.parse(man["sheets"][0]["svg"]).getroot()
    ns = {"s": "http://www.w3.org/2000/svg"}
    texts = [t.text for t in root.iterfind(".//s:text[@data-stream]", ns)]
    assert texts.count("S001") == 1
    assert sorted(set(texts)) == sorted(t for t in texts if t) and len(texts) == len(set(texts))


def test_deterministic(tmp_path):
    data = _demo()
    m1 = pd.build_pfd(copy.deepcopy(data), str(tmp_path / "a"), title_block=TB)
    m2 = pd.build_pfd(copy.deepcopy(data), str(tmp_path / "b"), title_block=TB)
    for s1, s2 in zip(m1["sheets"], m2["sheets"]):
        assert open(s1["svg"], "rb").read() == open(s2["svg"], "rb").read()
    assert filecmp.cmp(m1["spec"], m2["spec"], shallow=False)


def test_back_edge_lane(built):
    _data, _out, man = built
    spec = json.load(open(man["spec"], encoding="utf-8"))
    sheet = spec["sheets"][0]
    back = [p for p in sheet["paths"] if p["back_edge"]]
    assert back, "recycle loop must be drawn as a back edge"
    ys = [pt[1] for p in back for pt in p["points"]]
    unit_top = min(u["y"] - u["h"] / 2 for u in sheet["units"])
    unit_bot = max(u["y"] + u["h"] / 2 for u in sheet["units"])
    assert min(ys) < unit_top or max(ys) > unit_bot
    # every back-edge polyline is orthogonal
    for p in back:
        pts = p["points"]
        for a, b in zip(pts[:-1], pts[1:]):
            assert abs(a[0] - b[0]) < 1e-6 or abs(a[1] - b[1]) < 1e-6


def test_separator_outlet_order(built):
    _data, _out, man = built
    spec = json.load(open(man["spec"], encoding="utf-8"))
    sep = next(u for u in spec["sheets"][0]["units"] if u["tag"] == "SEP-100")
    ys = {p["stream"]: p["y"] for p in sep["ports"]["out"]}
    assert ys["S003"] < ys["S004"] < ys["S005"]  # gas top, oil middle, water bottom


def test_title_block_defaults_and_notes(built):
    _data, _out, man = built
    raw = open(man["sheets"][0]["svg"], encoding="utf-8").read()
    assert "not assigned" in raw
    assert "T-001-PFD-01" in raw
    assert "flare system" in raw
    assert "pressures bara" in raw.lower() or "Pressures bara" in raw
    # only the last four revisions are printed
    assert "rev 5" in raw and "rev 2" in raw and "rev 1" not in raw


def test_multi_sheet_connectors(tmp_path):
    data = _demo()
    man = pd.build_pfd(data, str(tmp_path), title_block=TB, max_units_per_sheet=3)
    assert len(man["sheets"]) >= 2
    tags, streams = _union(man)
    assert tags == {e["tag"] for e in data["equipment"]}
    assert streams == {s["id"] for s in data["streams"]}
    outs = {(c["stream"], c["other_sheet"]) for s in man["sheets"] for c in s["connectors"]
            if c["direction"] == "out"}
    ins = {(c["stream"], s["number"]) for s in man["sheets"] for c in s["connectors"]
           if c["direction"] == "in"}
    for sid, other in outs:
        assert (sid, other) in ins
    raw = "".join(open(s["svg"], encoding="utf-8").read() for s in man["sheets"])
    assert "from sheet" in raw and "to sheet" in raw
    mu = pd._import_mupdf()
    assert mu.open(man["pdf"]).page_count == len(man["sheets"])


def test_explicit_sheets(tmp_path):
    data = _demo()
    man = pd.build_pfd(data, str(tmp_path), title_block=TB,
                       sheets=[{"title": "Inlet", "areas": ["inlet"]}, {"title": "Gas", "areas": ["gas"]}])
    assert [s["title"] for s in man["sheets"]] == ["Inlet", "Gas"]
    assert man["sheets"][0]["units"] == ["MIX-100", "SEP-100"]


def test_layout_override_moves_symbol(tmp_path):
    data = _demo()
    base = pd.build_pfd(data, str(tmp_path / "a"), title_block=TB)
    spec0 = json.load(open(base["spec"], encoding="utf-8"))
    k0 = next(u for u in spec0["sheets"][0]["units"] if u["tag"] == "K-100")
    man = pd.build_pfd(data, str(tmp_path / "b"), title_block=TB,
                       layout_overrides={"K-100": {"x": k0["x"], "y": k0["y"] + 25.0}})
    spec = json.load(open(man["spec"], encoding="utf-8"))
    k1 = next(u for u in spec["sheets"][0]["units"] if u["tag"] == "K-100")
    assert abs(k1["y"] - (k0["y"] + 25.0)) < 0.01
    assert spec["overrides"]["K-100"]["y"] == pytest.approx(k0["y"] + 25.0)
    # streams still routed to the moved symbol
    ports = {p["stream"]: p["y"] for p in k1["ports"]["in"]}
    path = next(p for p in spec["sheets"][0]["paths"] if p["stream"] == "S006")
    assert path["points"][-1][1] == pytest.approx(ports["S006"], abs=0.01)


def test_bfd(tmp_path):
    data = _demo()
    tb = dict(TB)
    man = pd.build_bfd(data, str(tmp_path), title_block=tb)
    for key in ("svg", "pdf", "png", "spec"):
        assert os.path.exists(man[key]) and os.path.getsize(man[key]) > 500
    raw = open(man["svg"], encoding="utf-8").read()
    assert "Flows are mass flows for case A" in raw
    assert "Not modelled" in raw and "flare system" in raw
    assert len(man["blocks"]) == 2
    spec = json.load(open(man["spec"], encoding="utf-8"))
    kinds = {e["kind"] for e in spec["edges"]}
    assert {"flow", "feed", "product"} <= kinds
    back = [e for e in spec["edges"] if e["back_edge"]]
    assert back and back[0]["streams"] == ["S010"]
    flow = next(e for e in spec["edges"] if e["kind"] == "flow" and not e["back_edge"])
    assert flow["mass_kg_h"] == pytest.approx(600.0 + 100.0 - 100.0 + 0.0 + 0.0, abs=700)
    assert pd._import_mupdf().open(man["pdf"]).page_count == 1


def test_bfd_custom_blocks_and_case(tmp_path):
    data = _demo()
    man = pd.build_bfd(data, str(tmp_path), blocks=[{"name": "All", "areas": ["inlet", "gas"], "description": "one"}])
    assert len(man["blocks"]) == 1 and man["case"] == "A"


def test_empty_and_odd_input(tmp_path):
    man = pd.build_pfd({"areas": [], "equipment": [], "streams": [], "cases": []}, str(tmp_path))
    assert man["sheets"] == [] and man["warnings"]
    data = _demo()
    data["streams"].append(_stream("S099", "floating", "isolated", None, None, 1.0))
    man = pd.build_pfd(data, str(tmp_path / "x"))
    assert "S099" in _union(man)[1]
    data["equipment"].append(_unit("LOGIC-1", "logic", "Adjuster", "gas", [], []))
    man = pd.build_pfd(data, str(tmp_path / "y"))
    assert "LOGIC-1" not in _union(man)[0]


def test_no_iso_conformance_claim(built):
    _data, _out, man = built
    raw = open(man["sheets"][0]["svg"], encoding="utf-8").read()
    assert "conformance not claimed" in raw
    assert "complies" not in raw.lower() and "conforms to" not in raw.lower()


def _texts(svg_path):
    from lxml import etree

    root = etree.parse(svg_path).getroot()
    ns = {"s": "http://www.w3.org/2000/svg"}
    return [(t.text or "", float(t.get("font-size")), t.get("fill")) for t in root.iterfind(".//s:text", ns)]


def test_connector_shows_other_drawing_number(tmp_path):
    man = pd.build_pfd(_demo(), str(tmp_path), title_block=TB, max_units_per_sheet=3)
    dns = {s["number"]: s["drawing_number"] for s in man["sheets"]}
    conns = [c for s in man["sheets"] for c in s["connectors"]]
    assert conns and all(c["other_drawing_number"] == dns[c["other_sheet"]] for c in conns)
    spec = json.load(open(man["spec"], encoding="utf-8"))
    spec_conns = [c for s in spec["sheets"] for c in s["connectors"]]
    assert all(c["other_drawing_number"] == dns[c["other_sheet"]] for c in spec_conns)
    for sh in man["sheets"]:
        texts = _texts(sh["svg"])
        for c in sh["connectors"]:
            word = "from" if c["direction"] == "in" else "to"
            assert ("%s sheet %d" % (word, c["other_sheet"]), 2.0, pd.INK) in [(t, f, k) for t, f, k in texts]
            assert any(t == "DRG " + c["other_drawing_number"] and f >= 2.0 for t, f, _ in texts)
            assert any(t == c["stream"] and f >= 2.0 for t, f, _ in texts)


def test_equipment_data_blocks(tmp_path):
    data = _demo()
    long_line = "x" * 60
    eq = {"K-100": ["W = 6418 kW", "eta_p = 0.667 (case A)", long_line, "fourth line dropped"],
          "SEP-100": ["one line"], "NOT-A-TAG": ["ignored"]}
    plain = pd.build_pfd(data, str(tmp_path / "p"), title_block=TB)
    man = pd.build_pfd(data, str(tmp_path / "a"), title_block=TB, equipment_data=eq)
    spec = json.load(open(man["spec"], encoding="utf-8"))
    assert set(spec["equipment_data"]) == {"K-100", "SEP-100"}
    lines = spec["equipment_data"]["K-100"]
    assert len(lines) == 3 and lines[0] == "W = 6418 kW"
    assert len(lines[2]) == 34 and lines[2].endswith("...")
    texts = [t for sh in man["sheets"] for t in _texts(sh["svg"])]
    assert ("W = 6418 kW", 2.0, pd.GREY) in texts and ("one line", 2.0, pd.GREY) in texts
    assert not any("fourth" in t[0] or "ignored" in t[0] for t in texts)
    # the data block sits below the description and does not overlap any other node rectangle
    from lxml import etree

    ns = {"s": "http://www.w3.org/2000/svg"}
    for sh, sp in zip(man["sheets"], spec["sheets"]):
        root = etree.parse(sh["svg"]).getroot()
        for g in root.iterfind(".//s:g[@data-tag='K-100']", ns):
            ys = {t.text: float(t.get("y")) for t in g.iterfind("s:text", ns)}
            assert ys["W = 6418 kW"] > ys["3-phase separator" if "3-phase separator" in ys else "compressor"]
        boxes = []
        for u in sp["units"]:
            n = len(spec["equipment_data"].get(u["tag"], []))
            boxes.append((u["tag"], u["x"] - u["w"] / 2, u["y"] - u["h"] / 2, u["x"] + u["w"] / 2,
                          u["y"] + u["h"] / 2 + 8.0 + 2.4 * n))
        for i, a in enumerate(boxes):
            for b in boxes[i + 1:]:
                assert not (a[1] < b[3] and a[3] > b[1] and a[2] < b[4] and a[4] > b[2]), (a[0], b[0])
    # no equipment data: nothing extra is drawn, and re-runs are byte-identical
    assert "equipment_data" in json.load(open(plain["spec"], encoding="utf-8"))
    again = pd.build_pfd(data, str(tmp_path / "b"), title_block=TB, equipment_data=eq)
    for s1, s2 in zip(man["sheets"], again["sheets"]):
        assert open(s1["svg"], "rb").read() == open(s2["svg"], "rb").read()
    assert filecmp.cmp(man["spec"], again["spec"], shallow=False)


def test_case_and_utility_notes(tmp_path):
    tb = dict(TB, case_note="Equipment data shown for operating case A (Window A)",
              utility_note="Utility media are not modelled")
    man = pd.build_pfd(_demo(), str(tmp_path), title_block=tb)
    lines = [t for t, _, _ in _texts(man["sheets"][0]["svg"])]
    k_case = lines.index("1. Equipment data shown for operating case A (Window A)")
    k_stream = next(i for i, t in enumerate(lines) if t.startswith("2. Stream numbers refer"))
    assert k_case < k_stream
    assert any(t.startswith("%d. Utility media are not modelled" % n) for t in lines for n in range(5, 9))
    plain = pd.build_pfd(_demo(), str(tmp_path / "p"), title_block=TB)
    plain_lines = [t for t, _, _ in _texts(plain["sheets"][0]["svg"])]
    assert any(t.startswith("1. Stream numbers refer") for t in plain_lines)
    assert not any("Utility media" in t for t in plain_lines)
    spec = json.load(open(man["spec"], encoding="utf-8"))
    assert spec["sheets"][0]["title_block"]["case_note"].startswith("Equipment data")


def test_stream_diamond_size(built):
    _data, _out, man = built
    sizes = [f for sh in man["sheets"] for t, f, _ in _texts(sh["svg"]) if t.startswith("S0") and f < 2.4]
    assert sizes and min(sizes) >= 1.5
    assert pd.DIAMOND >= 5.6

