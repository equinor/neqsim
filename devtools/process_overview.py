"""Professional process overview: one A3 process flow diagram and a coordinated 16:9 presentation version.

Orchestrates the shared modules :mod:`ppo_graph` (structured graph), :mod:`ppo_layout` (hierarchical layout),
:mod:`ppo_render` (styles, symbols, exporters), :mod:`ppo_validate` (validation record) and reuses
:func:`psr_diagrams.build_pfd` for subsystem detail sheets. Input is the case-aware data set written by
``neqsim process-report`` / :mod:`psr_extract` (``process_data.json``) or a model source that
:mod:`process_report` can solve.

Command line::

    neqsim process-overview deliverables/data/process_data.json --case A --out deliverables/overview \\
        --title-block title_block.yaml --level overview --detail-sheets collapsed

Python::

    from process_overview import build_overview
    manifest = build_overview(data, "out/overview", case_id="A", title_block={...}, level="overview")

Outputs (``out_dir``): ``process_graph.json`` (structured source data), ``overview_A3_colour.svg/.pdf/.png``,
``overview_A3_mono.svg/.pdf/.png``, ``overview_16x9.svg/.pdf/.png`` (+ ``_mono``), ``layout_A3.json`` and
``layout_16x9.json`` (geometry + ``neqsim.pfd_layout.v1`` spec), ``detail_sheets/`` (A3 PFD sheets of
collapsed or all sections), ``validation.json`` / ``validation.md`` and ``manifest.json`` with the exact
regeneration command. Drawing generation never modifies the simulation model.
"""
from __future__ import annotations

import argparse
import json
import os
import sys
import time

import ppo_graph
import ppo_layout
import ppo_render
import ppo_validate
from ppo_render import MM_PER_PT, Scene

LEVEL_NOTES = {
    "overview": "Overview level: numerical recycle elements removed, in-line valves absorbed into their stream, parallel feeds grouped.",
    "engineering": "Engineering level: all modelled equipment except numerical recycle elements; valves shown.",
    "presentation": "Presentation level: in-line valves and pipe segments absorbed, few numerical annotations.",
}
STANDARDS_NOTE = ("Conventions: ISO 5457 sheet/frame, ISO 7200 title block, ISO 10628-2 / ISO 14617 symbols (editions not verified, "
                  "conformance not claimed). Schematic, not to scale.")


class OverviewError(Exception):
    """Raised for unusable input or impossible layouts."""


# ---------------------------------------------------------------------------
# drawing
# ---------------------------------------------------------------------------
def _service_style(service, role, main, back, style, pal, mono):
    """Colour, width and dash of a stream line; only true recycle/return streams are dashed (not geometric back edges)."""
    color = pal.get(service) or pal["ink"]
    sw = style["line"]["main"] if main else style["line"]["process"]
    dash = None
    if service == "utility":
        dash, sw = ppo_render.DASHES["utility"], style["line"]["utility"]
    elif role == "recycle":
        dash = ppo_render.DASHES["recycle"]
    elif mono:
        dash = ppo_render.DASHES.get(service)
    return color, sw, dash


def draw_sheet(L, graph, fonts, style, pal, mono, tb, kind="a3", sheet=(1, 1), refs=None, validation_note=None):
    """Compose the full page scene for a laid-out overview."""
    pg = style["page"]
    sc = Scene(pg["w"], pg["h"])
    pt = style["pt"]
    sidx = {s["id"]: s for s in graph["streams"]}
    ppo_render.draw_frame(sc, style, pal)
    fr = style["frame"]
    x0, x1 = fr["left"], pg["w"] - fr["right"]
    extra = {}
    # heading
    if kind == "a3":
        head = "%s  %s" % (tb["document_number"], tb["title"])
        yh = fr["top"] + pt["title"] * MM_PER_PT + 1.0
        sc.text(x0 + 2.0, yh, head, pt["title"], "start", True, pal["ink"], kind="title")
        if tb.get("readiness_note"):
            sc.text(x1 - 2.0, yh, tb["readiness_note"], pt["status"], "end", True, pal["status"], kind="status")
        else:
            sub = "Case %s: %s  |  level: %s  |  schematic - not to scale" % (graph["case"]["id"], graph["case"]["label"], graph["level"])
            sc.text(x1 - 2.0, yh, sub, pt["desc"], "end", False, pal["grey"], kind="subtitle")
        n_lines = int(style.get("notes_lines", 0))
        if n_lines:
            notes = _notes(L, graph, tb, refs_present=any(s.get("ref") for s in L.sections))
            text = "NOTES: " + "  ".join("%d. %s" % (i + 1, n) for i, n in enumerate(notes))
            lines = fonts.wrap(text, pt["note"], (x1 - x0) - 4.0, max_lines=n_lines)
            yy = yh + pt["note"] * MM_PER_PT * 1.6
            for ln in lines:
                sc.text(x0 + 2.0, yy, ln, pt["note"], "start", False, pal["grey"], kind="panel")
                yy += pt["note"] * MM_PER_PT * 1.3
    else:
        bar_h = fr["top"] - 3.0
        sc.rect(0, 0, pg["w"], bar_h, pal["navy"] if not mono else "#333333", "none", 0.0)
        title = tb["title"]
        while fonts.width(title, pt["title"], True) > (x1 - x0) and len(title) > 20:
            title = title[:-5] + "..."
        sc.text(x0, bar_h * 0.52, title, pt["title"], "start", True, "#FFFFFF", kind="title")
        sub = "Presentation adaptation of drawing %s rev. %s  |  case %s: %s  |  %s" % (
            tb["document_number"], tb["revision"], graph["case"]["id"], graph["case"]["label"], tb["issue_status"])
        sc.text(x0, bar_h - 2.2, sub, pt["desc"], "start", False, "#FFFFFF", kind="subtitle")
    # sections (a collapsed section is drawn as its block only; the frame would repeat the name)
    sc.begin(id="sections")
    for s in L.sections:
        if s["collapsed"]:
            continue
        fill = pal["section_fill"] if style["section"]["fill"] else "none"
        sc.begin(data_section=s["id"])
        sc.rect(s["x"], s["y"], s["w"], s["h"], fill, pal["section_stroke"], style["line"]["section"], rx=1.5)
        th = style["section"]["title_h"]
        sc.line([(s["x"], s["y"] + th), (s["x"] + s["w"], s["y"] + th)], style["line"]["section"] * 0.8, pal["section_stroke"])
        title = s["name"]
        tw = fonts.width(title, pt["section"], True)
        sc.text(s["x"] + 2.0, s["y"] + th - 2.0, title, pt["section"], "start", True, pal["section_title"], kind="section")
        if s.get("ref") and not s["collapsed"]:
            sc.text(s["x"] + 2.0 + tw + 3.0, s["y"] + th - 2.0, "detail: %s" % s["ref"], pt["desc"], "start", False, pal["grey"],
                    kind="section_ref")
        sc.end()
    sc.end()
    # edges
    sc.begin(id="streams")
    by_stream = {}
    for e in L.edges:
        by_stream.setdefault(e["stream"], []).append(e)
    arrow_l, arrow_w = style["arrow"]["l"], style["arrow"]["w"]
    for sid, edges in by_stream.items():
        st = sidx.get(sid, {})
        color, sw, dash = _service_style(st.get("service", "unknown"), st.get("role"), st.get("main"),
                                         any(e["back"] for e in edges), style, pal, mono)
        sc.begin(data_stream=sid)
        for e in edges:
            if len(e["pts"]) >= 2:
                sc.line(e["pts"], sw, color, dash)
                if e["arrow_end"]:
                    (xa, ya), (xb, yb) = e["pts"][-2], e["pts"][-1]
                    sc.arrow(xb, yb, xb - xa, yb - ya, arrow_l, arrow_w, color)
        for x, y, ux, uy, key in L.mid_arrows:
            if key == sid:
                sc.arrow(x, y, ux, uy, arrow_l * 0.85, arrow_w * 0.85, color)
        for x, y, key in L.dots:
            if key == sid:
                sc.circle(x, y, 0.9 if st.get("main") else 0.75, color, color, 0.1)
        sc.end()
    sc.end()
    # nodes
    sc.begin(id="equipment")
    ink = pal["ink"]
    for n in L.nodes.values():
        if n["kind"] == "unit":
            p = n["payload"]
            sc.begin(data_tag=p["tag"])
            n["sym"].draw(sc, n["x"], n["y"], ink, style["line"]["symbol"])
            lh = fonts.height(pt["tag"]) * 1.05
            y = n["y"] - n["sym"].h / 2.0 - 1.0 - (len(p["tag_lines"]) - 1) * lh
            for t in p["tag_lines"]:
                sc.text(n["x"], y, t, pt["tag"], "middle", True, ink, kind="tag")
                y += lh
            y = n["y"] + n["sym"].h / 2.0 + fonts.height(pt["desc"]) * 0.95 + 0.4
            sc.text(n["x"], y, p["desc"], pt["desc"], "middle", False, pal["grey"], kind="desc")
            for v in p["values"]:
                y += fonts.height(pt["value"]) * 1.05
                sc.text(n["x"], y, v, pt["value"], "middle", False, ink, kind="value")
            sc.end()
        elif n["kind"] == "block":
            p = n["payload"]
            sc.begin(data_block=p["section"])
            sc.rect(n["x"] - n["w"] / 2.0, n["y"] - n["h"] / 2.0, n["w"], n["h"], pal["section_fill"], pal["section_title"],
                    style["line"]["symbol"] * 1.4, rx=2.0)
            lh = fonts.height(pt["tag"]) * 1.1
            total = len(p["name_lines"]) * lh + fonts.height(pt["desc"]) * 1.1
            y = n["y"] - total / 2.0 + fonts.cap(pt["tag"]) + 0.5
            for t in p["name_lines"]:
                sc.text(n["x"], y, t, pt["tag"], "middle", True, ink, kind="tag")
                y += lh
            sc.text(n["x"], y, p["sub"], pt["desc"], "middle", False, pal["grey"], kind="desc")
            sc.end()
        elif n["kind"] in ("feed", "product") and not n["payload"].get("pseudo"):
            p = n["payload"]
            lines = p["lines"]
            lh = fonts.height(pt["desc"]) * 1.05
            if n["kind"] == "feed":
                xx, anchor = n["x"] - n["w"] / 2.0 + 0.5, "start"
            else:
                xx, anchor = n["x"] + n["w"] / 2.0 - 0.5, "end"
            y = n["y"] - 1.2 - (len(lines) - 1) * lh
            sc.begin(data_stream=p["stream"])
            for t in lines:
                sc.text(xx, y, t, pt["desc"], anchor, True, ink, kind="boundary")
                y += lh
            sc.end()
    sc.end()
    # annotations (attached boundary data as plain text, label boxes beside lines) and stream-number bubbles
    sc.begin(id="annotations")
    lh = fonts.height(pt["stream"]) * 1.05
    pad = style["annotation"]["pad"]
    for a in L.annotations:
        x0_, y0_, x1_, y1_ = a["bbox"]
        sc.begin(data_annotation=a["stream"])
        if a.get("attached"):
            y = y0_ + fonts.cap(pt["stream"]) + 0.2
            for t in a["lines"]:
                sc.text(x0_, y, t, pt["stream"], "start", False, ink, kind="stream")
                y += lh
        else:
            sc.rect(x0_, y0_, x1_ - x0_, y1_ - y0_, pal["annotation_fill"], pal["annotation_stroke"], 0.2, rx=0.6)
            y = y0_ + pad + fonts.cap(pt["stream"]) + 0.2
            for i, t in enumerate(a["lines"]):
                sc.text(x0_ + pad, y, t, pt["stream"], "start", i == 0, ink, kind="stream")
                y += lh
        sc.end()
    for b in L.bubbles:
        x0_, y0_, x1_, y1_ = b["bbox"]
        w, h = x1_ - x0_, y1_ - y0_
        sc.begin(data_stream=b["stream"])
        sc.poly([(b["x"] - w / 2, b["y"]), (b["x"], b["y"] - h / 2), (b["x"] + w / 2, b["y"]), (b["x"], b["y"] + h / 2)],
                "#fff", ink, 0.3)
        sc.text(b["x"], b["y"] + fonts.cap(pt["badge"]) / 2.0, b.get("text", b["stream"]), pt["badge"], "middle", True, ink, kind="bubble")
        sc.end()
    sc.end()
    # bottom band: legend, notes, summary, title block
    if kind == "a3":
        extra.update(_draw_a3_panels(sc, L, graph, fonts, style, pal, mono, tb, sheet, validation_note))
    else:
        _draw_slide_footer(sc, L, graph, fonts, style, pal, mono, tb)
    return sc, extra


def _legend_entries(L, graph, mono):
    keys = []
    for n in L.nodes.values():
        if n["kind"] == "unit" and n["sym"].key not in keys:
            keys.append(n["sym"].key)
    order = list(ppo_render.SYMBOL_LIBRARY)
    keys.sort(key=order.index)
    services = []
    sidx = {s["id"]: s for s in graph["streams"]}
    for e in L.edges:
        sv = sidx.get(e["stream"], {}).get("service", "unknown")
        if sv not in services:
            services.append(sv)
    roles = {"recycle"} if any(e["back"] or sidx.get(e["stream"], {}).get("role") == "recycle" for e in L.edges) else set()
    return keys, services, roles


def _draw_a3_panels(sc, L, graph, fonts, style, pal, mono, tb, sheet, validation_note):
    pg, fr = style["page"], style["frame"]
    x0, x1, y1 = fr["left"], pg["w"] - fr["right"], pg["h"] - fr["bottom"]
    h = style["panels"]["h"]
    y0 = y1 - h
    pt = style["pt"]
    extra = {}
    # legend (left)
    lw = style["panels"]["legend_w"]
    sc.rect(x0, y0, lw, h, "#fff", pal["ink"], style["line"]["panel"])
    sc.text(x0 + 1.5, y0 + 4.0, "LEGEND", pt["legend"] + 0.5, "start", True, pal["ink"], kind="panel_title")
    keys, services, roles = _legend_entries(L, graph, mono)
    entries = [("sym", k, ppo_render.LEGEND_SHORT[k]) for k in keys]
    for sv in services:
        entries.append(("line", sv, ppo_render.SERVICE_SHORT.get(sv, sv)))
    for r in sorted(roles):
        entries.append(("line", r, ppo_render.SERVICE_SHORT[r]))
    entries.append(("line", "main", "Main path (heavy)"))
    entries.append(("misc", "dot", "Junction dot"))
    entries.append(("misc", "ann", "Stream no. (see table)" if style["annotation"].get("style", "bubble") == "bubble" else "Stream no., flow, T, P"))
    if any(n["kind"] == "unit" and n["payload"]["equipment"].get("represents") for n in L.nodes.values()):
        entries.append(("misc", "badge", "Tag xN = N trains"))
    cols = 2 if len(entries) <= 14 else 3
    rows = max(1, -(-len(entries) // cols))
    cw = (lw - 3.0) / cols
    rh = min(6.5, (h - 6.5) / rows)
    lpt = min(pt["legend"], max(6.0, rh * 0.72 * PT_PER_MM_SAFE))
    for i, (kind, key, label) in enumerate(entries):
        c, r = divmod(i, rows)
        x = x0 + 1.5 + c * cw
        y = y0 + 6.0 + r * rh
        cx, cy = x + 7.0, y + rh / 2.0
        if kind == "sym":
            sym = ppo_render.make_symbol(*_sym_spec(key), scale=0.32, n_in=1, n_out=1)
            sym.draw(sc, cx, cy, pal["ink"], 0.3)
        elif kind == "line":
            if key == "main":
                sc.line([(x + 1.5, cy), (x + 12.5, cy)], style["line"]["main"], pal["ink"])
            else:
                color, sw, dash = _service_style(key if key != "recycle" else "gas", "recycle" if key == "recycle" else "process",
                                                 False, False, style, pal, mono)
                if key == "recycle":
                    color = pal["ink"]
                sc.line([(x + 1.5, cy), (x + 12.5, cy)], sw, color, dash)
            sc.arrow(x + 12.5, cy, 1, 0, 2.2, 1.5, pal["ink"])
        elif key == "dot":
            sc.line([(x + 1.5, cy), (x + 12.5, cy)], style["line"]["process"], pal["ink"])
            sc.line([(x + 7.0, cy), (x + 7.0, cy - 2.5)], style["line"]["process"], pal["ink"])
            sc.circle(x + 7.0, cy, 0.75, pal["ink"], pal["ink"], 0.1)
        elif key == "ann":
            if style["annotation"].get("style", "bubble") == "bubble":
                sc.line([(x + 1.0, cy), (x + 13.0, cy)], style["line"]["process"], pal["ink"])
                sc.poly([(x + 2.6, cy), (x + 7.0, cy - 2.3), (x + 11.4, cy), (x + 7.0, cy + 2.3)], "#fff", pal["ink"], 0.3)
            else:
                sc.rect(x + 1.5, cy - 2.2, 11.0, 4.4, "#fff", pal["annotation_stroke"], 0.2, rx=0.5)
            sc.text(x + 7.0, cy + 0.9, "S012", 5.5, "middle", True, pal["ink"], kind="legend")
        elif key == "badge":
            sc.text(cx, cy + fonts.cap(6.5) / 2.0, "K-1 x2", 6.5, "middle", True, pal["ink"], kind="legend")
        lines = fonts.wrap(label, lpt, cw - 16.0, max_lines=2)
        ty = cy + fonts.cap(lpt) / 2.0 - (len(lines) - 1) * fonts.height(lpt) * 0.5
        for j, t in enumerate(lines):
            sc.text(x + 15.0, ty + j * fonts.height(lpt), t, lpt, "start", False, pal["ink"], kind="legend")
    extra["legend"] = (x0, y0, x0 + lw, y1)
    # key streams table (right of legend): every annotated stream + totals, values of the displayed case
    tbw = style["title_block"]["w"]
    xs = x0 + lw + 2.0
    sw_ = min(style["panels"]["streams_w"], (x1 - tbw - 2.0) - xs)
    extra["streams"] = _draw_stream_table(sc, L, graph, fonts, style, pal, xs, y0, sw_, h)
    tbb = ppo_render.draw_title_block(sc, fonts, style, pal, tb, sheet[0], sheet[1])
    extra["title_block"] = tbb
    return extra


def key_stream_rows(L, graph):
    """Rows of the key-stream table: annotated streams (feeds, products, then main cross-section streams by flow)."""
    sidx = {s["id"]: s for s in graph["streams"]}
    drawn = {a["stream"] for a in L.annotations} | {b["stream"] for b in L.bubbles}
    rows = []
    for sid in graph["display"]["annotate"]:
        st = sidx.get(sid)
        if st is None:
            continue
        v = st.get("values") or {}
        kind = "feed" if st["from"] is None else ("product" if not st["to"] else "internal")
        label = st["label"]
        if st.get("grouped"):
            label = "%s (x%d)" % (label.split(" (")[0], len(st.get("members") or []))
        rows.append({"id": sid, "label": label, "kind": kind, "service": st["service"], "mass_kg_h": v.get("mass_kg_h"),
                     "t_c": v.get("t_c"), "t_range": v.get("t_c_range"), "p_bara": v.get("p_bara"), "p_range": v.get("p_bara_range"),
                     "drawn": sid in drawn})
    order = {"feed": 0, "product": 1, "internal": 2}
    rows.sort(key=lambda r: (order[r["kind"]], -(r["mass_kg_h"] or 0.0), r["id"]))
    return rows


def _draw_stream_table(sc, L, graph, fonts, style, pal, x, y, w, h):
    pt = style["pt"]["table"]
    sc.rect(x, y, w, h, "#fff", pal["ink"], style["line"]["panel"])
    sc.text(x + 1.5, y + 4.0, "KEY STREAMS (case %s, model results; mass flow, T, P)" % graph["case"]["id"], pt + 0.5, "start", True,
            pal["ink"], kind="panel_title")
    rows = key_stream_rows(L, graph)
    rh = fonts.height(pt) * 1.12
    no_w = max([fonts.width(r["id"], pt, True) for r in rows] + [fonts.width("No.", pt, True)]) + 2.0
    cols = [("No.", no_w, "start"), ("Stream", w - no_w - 16.0 - 14.0 - 15.0 - 3.0, "start"), ("t/h", 16.0, "end"), ("C", 14.0, "end"),
            ("bara", 15.0, "end")]
    yy = y + 4.0 + rh * 1.3
    xx = x + 1.5
    for name, cw, anchor in cols:
        sc.text(xx + (cw - 0.8 if anchor == "end" else 0.0), yy, name, pt, anchor, True, pal["grey"], kind="panel")
        xx += cw
    sc.line([(x + 1.0, yy + 0.8), (x + w - 1.0, yy + 0.8)], 0.2, pal["grey"])
    yy += rh
    summ = graph["summary"]
    totals = []
    if summ.get("shaft_power_kw"):
        totals.append("Shaft power %s" % ppo_layout._fmt_power(summ["shaft_power_kw"]))
    if summ.get("heating_kw"):
        totals.append("heating %s" % ppo_layout._fmt_power(summ["heating_kw"]))
    if summ.get("cooling_kw"):
        totals.append("cooling %s" % ppo_layout._fmt_power(summ["cooling_kw"]))
    n_fit = int((y + h - 1.5 - yy - (rh if totals else 0.0)) / rh)
    shown = rows[:max(n_fit, 0)]
    for r in shown:
        xx = x + 1.5
        vals = [r["id"] + ("" if r["drawn"] else " *"), fonts.wrap(r["label"], pt, cols[1][1] - 1.0, max_lines=1)[0],
                ("%.1f" % (r["mass_kg_h"] / 1000.0)) if r["mass_kg_h"] is not None else "-",
                ("%.0f" % r["t_c"]) if r["t_c"] is not None else ("%.0f-%.0f" % tuple(r["t_range"]) if r.get("t_range") else "-"),
                ("%.1f" % r["p_bara"]) if r["p_bara"] is not None else ("%.0f-%.0f" % tuple(r["p_range"]) if r.get("p_range") else "-")]
        for (name, cw, anchor), v in zip(cols, vals):
            sc.text(xx + (cw - 0.8 if anchor == "end" else 0.0), yy, v, pt, anchor, name == "No.", pal["ink"], kind="panel")
            xx += cw
        yy += rh
    if len(rows) > len(shown):
        sc.text(x + w - 1.5, yy, "+%d more in layout_A3.json" % (len(rows) - len(shown)), pt - 0.5, "end", False, pal["grey"], kind="panel")
        yy += rh
    if totals:
        foot = "; ".join(totals) + "."
        if any(not r["drawn"] for r in shown):
            foot += "  * no number mark on the drawing (space)."
        sc.text(x + 1.5, y + h - 1.8, foot, pt, "start", False, pal["ink"], kind="panel")
    elif any(not r["drawn"] for r in shown):
        sc.text(x + 1.5, y + h - 1.8, "* no number mark on the drawing (space).", pt, "start", False, pal["ink"], kind="panel")
    L.table_rows = shown
    return (x, y, x + w, y + h)


PT_PER_MM_SAFE = 72.0 / 25.4


def _sym_spec(key):
    return {"sep2": ("separator", ""), "sep3": ("separator", "ThreePhaseSeparator"), "scrubber": ("separator", "GasScrubber"),
            "column": ("column", ""), "compressor": ("compressor", ""), "expander": ("expander", ""), "pump": ("pump", ""),
            "heater": ("heater", ""), "cooler": ("cooler", ""), "exchanger": ("exchanger", ""), "valve": ("valve", ""),
            "mixer": ("mixer", ""), "splitter": ("splitter", ""), "ejector": ("ejector", ""), "recycle": ("recycle", ""),
            "pipe": ("pipe", ""), "reactor": ("reactor", ""), "generic": ("other", "")}[key]


def _notes(L, graph, tb, refs_present):
    sc = graph["source"]["standard_conditions"]
    notes = ["Case %s (%s): values are model results of this case, std. %s C / %s bara, no design ratings." % (
        graph["case"]["id"], graph["case"]["label"], sc.get("t_c"), sc.get("p_bara"))]
    notes.append(LEVEL_NOTES[graph["level"]] + " Mapping: process_graph.json.")
    m = graph["mapping"]
    if m["grouped_feeds"]:
        notes.append("Grouped feeds: " + "; ".join("%s = %d streams" % (k, v["count"]) for k, v in sorted(m["grouped_feeds"].items())) + ".")
    if m["grouped_trains"]:
        notes.append("Identical trains grouped: " + "; ".join("%s shown by %s" % (k, v["represented_by"])
                                                             for k, v in sorted(m["grouped_trains"].items())) + ".")
    coll = [s for s in L.sections if s["collapsed"]]
    if coll:
        notes.append("Blocks (detail on sheets %s<nn> of the PFD drawing set): " % tb.get("ref_prefix", "") + "; ".join(
            "%s (%d units%s)" % (s["name"], len(s["members"]), ", " + s["ref"] if s.get("ref") else "") for s in coll) + ".")
    elif refs_present:
        notes.append("Section titles reference sheets %s<nn> of the PFD drawing set." % tb.get("ref_prefix", ""))
    notes.append("Utilities, drivers and non-modelled systems not shown. Source: %s." % tb["model_source"])
    notes.append(STANDARDS_NOTE)
    notes.extend(tb.get("notes") or [])
    return notes


def _draw_slide_footer(sc, L, graph, fonts, style, pal, mono, tb):
    pg, fr = style["page"], style["frame"]
    x0, x1 = fr["left"], pg["w"] - fr["right"]
    y = pg["h"] - 4.0
    pt = style["pt"]
    keys, services, roles = _legend_entries(L, graph, mono)
    x = x0
    for sv in services + sorted(roles):
        color, sw, dash = _service_style(sv if sv != "recycle" else "gas", "recycle" if sv == "recycle" else "process", False, False,
                                         style, pal, mono)
        if sv == "recycle":
            color = pal["ink"]
        sc.line([(x, y - 1.6), (x + 10.0, y - 1.6)], sw, color, dash)
        label = ppo_render.SERVICE_LABELS.get(sv, sv)
        sc.text(x + 11.5, y - 0.2, label, pt["legend"] - 1.0, "start", False, pal["ink"], kind="legend")
        x += 11.5 + fonts.width(label, pt["legend"] - 1.0) + 6.0
    foot = "%s rev. %s | %s | source: %s | schematic - not to scale | tags and stream numbers as on the A3 drawing" % (
        tb["document_number"], tb["revision"], tb["issue_status"], tb["model_source"])
    while fonts.width(foot, pt["note"] - 1.0) > (x1 - x - 4.0) and len(foot) > 40:
        foot = foot[:-5] + "..."
    sc.text(x1, y - 0.2, foot, pt["note"] - 1.0, "end", False, pal["grey"], kind="footer")


# ---------------------------------------------------------------------------
# fitting
# ---------------------------------------------------------------------------
def fit_layout(graph, fonts, style, refs, collapsed=(), max_collapse=None, log=None, annotate=None):
    """Try symbol scales; when nothing fits, collapse the section whose block representation helps most, and repeat.

    Text sizes are never reduced below the readability target. Fallback order when the complete drawing does not fit:
    symbol scale, section blocks (largest gain first, single-unit sections last), stream bubbles limited to boundary
    streams, compact text step. Returns the first fitting :class:`ppo_layout.Layout` or the least overflowing one.
    """
    area = ppo_render.drawing_area(style)
    collapsed = list(collapsed)
    attempts = []
    annotate = list(graph["display"]["annotate"]) if annotate is None else list(annotate)

    def overflow(L):
        cw, ch = L.content_size
        return max(cw / area[2], ch / area[3])

    while True:
        best = None
        for scale in style["scales"]:
            L = ppo_layout.layout_overview(graph, fonts, style, area, scale=scale, collapsed=collapsed, refs=refs, annotate=annotate)
            attempts.append({"scale": scale, "collapsed": list(collapsed), "content_mm": [round(v, 1) for v in L.content_size],
                             "rows": L.rows, "fits": L.fits, "annotate": len(annotate)})
            if log:
                log("layout: scale %.2f, %d collapsed, %d row(s) -> %.0f x %.0f mm (%s)" % (
                    scale, len(collapsed), L.rows, L.content_size[0], L.content_size[1], "fits" if L.fits else "too large"))
            if L.fits:
                best = L
                break
            if best is None or overflow(L) < overflow(best):
                best = L
        best.annotate_used = list(annotate)
        if best.fits:
            best.attempts = attempts
            return best
        candidates = [s["id"] for s in graph["sections"] if s["id"] not in collapsed and len(s["members"]) > 1]
        if not candidates:  # last resort before the compact text step: single-unit sections become blocks too
            candidates = [s["id"] for s in graph["sections"] if s["id"] not in collapsed]
        if not candidates or (max_collapse is not None and len(collapsed) >= max_collapse):
            boundary = {b["stream"] for b in graph["boundaries"]}
            if any(a not in boundary for a in annotate):
                if log:
                    log("bubble step: stream-number bubbles limited to boundary streams (table keeps all key streams)")
                Lb = fit_layout(graph, fonts, style, refs, collapsed=collapsed, max_collapse=max_collapse, log=log,
                                annotate=[a for a in annotate if a in boundary])
                Lb.attempts = attempts + getattr(Lb, "attempts", [])
                return Lb
            # last resort: the compact text step (still at or above the readability target), recorded in the layout
            compact = style.get("compact_pt")
            if compact and not style.get("_compact_used"):
                style["pt"].update(compact)
                style["_compact_used"] = True
                fonts_c = ppo_render.Fonts(style)
                if log:
                    log("compact text step: %s" % ", ".join("%s %.1f pt" % kv for kv in sorted(compact.items())))
                Lc = fit_layout(graph, fonts_c, style, refs, collapsed=collapsed, max_collapse=max_collapse, log=log, annotate=annotate)
                Lc.compact_text = True
                Lc.attempts = attempts + getattr(Lc, "attempts", [])
                return Lc
            # final fallback: group the sections in process order into fewer combined blocks (mapping kept); the
            # smallest reduction that fits wins
            n_sec = len(graph["sections"])
            if n_sec > 2 and not graph.get("mapping", {}).get("auto_merged") and (max_collapse is None or max_collapse > 0):
                order_ids = ppo_layout.section_order(graph)
                weights = {s["id"]: len(s["members"]) for s in graph["sections"]}
                best_m = None
                for k in range(n_sec - 1, 1, -1):
                    groups = [g for g in ppo_layout.contiguous_groups(order_ids, weights, k) if len(g) > 1]
                    if not groups:
                        continue
                    merged = ppo_graph.merge_sections(graph, groups)
                    merged["mapping"]["auto_merged"] = True
                    coll = [s["id"] for s in merged["sections"]]
                    refs_m = dict(refs)
                    for s in merged["sections"]:
                        if s.get("merged_from"):
                            parts = [refs.get(i) for i in s["merged_from"] if refs.get(i)]
                            if parts:
                                refs_m[s["id"]] = " / ".join(parts)
                    Lm = fit_layout(merged, fonts, style, refs_m, collapsed=coll, max_collapse=0, log=None, annotate=annotate)
                    Lm.graph_used = merged
                    if log:
                        log("merge step: %d groups -> %.0f x %.0f mm (%s)" % (k, Lm.content_size[0], Lm.content_size[1],
                                                                             "fits" if Lm.fits else "too large"))
                    if Lm.fits:
                        Lm.attempts = attempts + getattr(Lm, "attempts", [])
                        return Lm
                    if best_m is None or overflow(Lm) < overflow(best_m):
                        best_m = Lm
                if best_m is not None and overflow(best_m) < overflow(best):
                    best_m.attempts = attempts + getattr(best_m, "attempts", [])
                    best_m.warnings.append("overview does not fit the drawing area even with sections merged into blocks")
                    return best_m
            best.attempts = attempts
            best.warnings.append("overview does not fit the drawing area even with %d sections collapsed" % len(collapsed))
            return best
        # choose the section whose collapse reduces the overflow most (evaluated at the smallest scale)
        scored = []
        for c in candidates:
            Lc = ppo_layout.layout_overview(graph, fonts, style, area, scale=style["scales"][-1], collapsed=collapsed + [c], refs=refs)
            scored.append((0 if Lc.fits else 1, overflow(Lc), c))
        scored.sort()
        collapsed.append(scored[0][2])
        if log:
            log("collapse: %s (overflow %.2f)" % (scored[0][2], scored[0][1]))


# ---------------------------------------------------------------------------
# build
# ---------------------------------------------------------------------------
def _write_json(path, obj):
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        json.dump(obj, fh, indent=1, ensure_ascii=True, default=_json_default)


def _json_default(o):
    if isinstance(o, tuple):
        return list(o)
    if hasattr(o, "key") and hasattr(o, "w"):
        return {"symbol": o.key, "w": o.w, "h": o.h}
    return str(o)


def _write(path, text):
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(text)


def _layout_record(L, spec):
    secs = [{k: v for k, v in s.items()} for s in L.sections]
    nodes = []
    for n in L.nodes.values():
        rec = {"id": n["id"], "kind": n["kind"], "section": n["section"], "x": round(n["x"], 2), "y": round(n["y"], 2),
               "w": round(n["w"], 2), "h": round(n["h"], 2),
               "ports": [{"side": k[0], "stream": k[1], "x": round(x, 2), "y": round(y, 2)} for k, (x, y) in sorted(n["ports"].items())]}
        if n.get("sym") is not None:
            rec["symbol"] = n["sym"].key
        if n["kind"] == "unit":
            rec["tag"] = n["payload"]["tag"]
        elif n["kind"] in ("feed", "product"):
            rec["stream"] = n["payload"]["stream"]
            rec["pseudo"] = bool(n["payload"].get("pseudo"))
        nodes.append(rec)
    edges = [{"id": e["id"], "stream": e["stream"], "src": e["src"], "dst": e["dst"], "external": e["external"], "back": e["back"],
              "arrow_end": e["arrow_end"], "points": [[round(x, 2), round(y, 2)] for x, y in e["pts"]]} for e in L.edges]
    return {"schema": "neqsim.process_overview.layout/1", "scale": L.scale, "fits": L.fits, "collapsed": L.collapsed,
            "content_mm": list(L.content_size), "bbox": list(L.bbox), "sections": secs, "nodes": nodes, "edges": edges,
            "junctions": [[round(x, 2), round(y, 2), k] for x, y, k in L.dots],
            "annotations": [{"stream": a["stream"], "lines": a["lines"], "bbox": [round(v, 2) for v in a["bbox"]]} for a in L.annotations],
            "bundles": L.bundles, "bubbles": [{"stream": b["stream"], "text": b.get("text"), "x": round(b["x"], 2), "y": round(b["y"], 2)} for b in L.bubbles],
            "warnings": L.warnings, "attempts": getattr(L, "attempts", []), "pfd_layout_spec": spec}


def _apply_section_groups(graph, groups, log):
    """Merge sections per explicit user groups ``[{"name": str, "sections": [names or ids]}]`` (or plain lists)."""
    if not groups:
        return graph
    resolved = []
    names = {}
    for grp in groups:
        members = grp.get("sections", []) if isinstance(grp, dict) else list(grp)
        ids = []
        for name in members:
            hit = next((s["id"] for s in graph["sections"] if name in (s["id"], s["name"], s.get("model_area"))), None)
            if hit:
                ids.append(hit)
            else:
                log("section group: %r is not a section" % name)
        if len(ids) >= 2:
            resolved.append(ids)
            if isinstance(grp, dict) and grp.get("name"):
                names[ids[0]] = grp["name"]
    if not resolved:
        return graph
    merged = ppo_graph.merge_sections(graph, resolved)
    for s in merged["sections"]:
        if s["id"] in names:
            s["name"] = names[s["id"]]
    return merged


def build_detail_sheets(data, out_dir, tb_detail, sections, log=None):
    """A3 detail PFD sheets (one explicit sheet per section) through :func:`psr_diagrams.build_pfd`.

    Returns ``(manifest, refs)`` with ``refs = {section id: "PFD-01 / PFD-02"}`` (sheet numbers without the common
    document-number prefix; the drawing set's document number is stated in the notes).
    """
    import psr_diagrams

    sheets = []
    for s in sections:
        areas = (s.get("represents") or {}).get("model_areas") or [s["model_area"]]
        sheets.append({"title": s["name"], "areas": [a for a in areas if a]})
    if not sheets:
        return None, {}
    manifest = psr_diagrams.build_pfd(data, out_dir, title_block=tb_detail, sheets=sheets)
    prefix = "%s-" % tb_detail["document_number"]
    refs = {}
    for s in sections:
        mem = set(s["members"])
        dns = [sh["drawing_number"] for sh in manifest["sheets"] if mem & set(sh["units"])]
        if dns:
            refs[s["id"]] = " / ".join(dn[len(prefix):] if dn.startswith(prefix) else dn for dn in dns)
    manifest["ref_prefix"] = prefix
    if log:
        log("detail sheets: %d sheets in %s" % (len(manifest["sheets"]), out_dir))
    return manifest, refs


def build_overview(data, out_dir, case_id=None, title_block=None, level="overview", config=None, detail_sheets="collapsed",
                   style_overrides=None, collapse_sections=(), source_path=None, embed_svg_font=True, png_dpi=150,
                   mono=True, presentation=True, log=None, command=None):
    """Generate the A3 overview (colour + mono), the 16:9 presentation version, detail sheets and the validation record.

    Args:
        data: ``neqsim.process_study_report.data/1`` dict.
        out_dir: output folder (created).
        case_id: displayed operating case (default first case).
        title_block: title-block fields (company, project, title, document_number, revision, issue_status, date, drawn_by,
            checked_by, approved_by, notes, readiness_note, model_source).
        level: ``overview`` | ``engineering`` | ``presentation`` (A3 information level; the slide always uses presentation).
        config: :data:`ppo_graph.DEFAULT_CONFIG` overrides (collapse, group_feeds_min, group_trains, sections, ...).
        detail_sheets: ``collapsed`` (sheets for sections drawn as blocks), ``all`` or ``none``.
        style_overrides: nested overrides of :data:`ppo_render.STYLE_A3` (fonts, line weights, palette, ...).
        collapse_sections: section names or ids forced to block representation.
        source_path: data-file path recorded for provenance.
        embed_svg_font: embed a subset of the TrueType font in the SVG files.
        png_dpi: raster preview resolution.
        mono: also write monochrome variants.
        presentation: also write the 16:9 presentation adaptation.
        log: optional callable for progress lines.
        command: the command line that regenerates the output (recorded in the manifest).

    Returns:
        Manifest dict (files, warnings, validation status).
    """
    t0 = time.time()
    log = log or (lambda m: None)
    os.makedirs(out_dir, exist_ok=True)
    cfg = dict(config or {})
    cfg["level"] = level
    graph = ppo_graph.build_graph(data, case_id=case_id, config=cfg, source_path=source_path)
    graph = _apply_section_groups(graph, (config or {}).get("section_groups"), log)
    problems = ppo_graph.check_graph(graph, data)
    if problems:
        log("graph problems: %s" % "; ".join(problems[:5]))
    style = ppo_render.make_style("a3", style_overrides)
    fonts = ppo_render.Fonts(style)
    tb = ppo_render.resolve_title_block(title_block, graph)
    forced = []
    for name in collapse_sections:
        hit = next((s["id"] for s in graph["sections"] if name in (s["id"], s["name"], s.get("model_area"))), None)
        if hit:
            forced.append(hit)
        else:
            log("collapse_sections: %r is not a section" % name)
    # first pass without references to find what must be collapsed
    L0 = fit_layout(graph, fonts, style, {}, collapsed=forced, log=log)
    collapsed = list(L0.collapsed)
    detail_manifest, refs = None, {}
    if detail_sheets != "none":
        wanted = [s for s in graph["sections"] if detail_sheets == "all" or s["id"] in collapsed]
        if wanted:
            tb_detail = {"project": tb["project"], "title": tb["title"].replace("(overview)", "").strip() + " - detail",
                         "document_number": tb["document_number"], "revision": tb["revision"], "issue_status": tb["issue_status"],
                         "date": tb["date"], "drawn_by": tb["drawn_by"], "checked_by": tb["checked_by"],
                         "approved_by": tb["approved_by"], "company": tb["company"], "sheet_number_prefix": "PFD",
                         "case_note": "Equipment data shown for case %s (%s)" % (graph["case"]["id"], graph["case"]["label"]),
                         "readiness_note": tb.get("readiness_note", "")}
            detail_manifest, refs = build_detail_sheets(data, os.path.join(out_dir, "detail_sheets"), tb_detail, wanted, log=log)
            tb["ref_prefix"] = detail_manifest["ref_prefix"] if detail_manifest else ""
    L = fit_layout(graph, fonts, style, refs, collapsed=collapsed, log=log) if refs else L0
    if refs and L.collapsed != collapsed:
        log("note: layout with references needed %d collapsed sections" % len(L.collapsed))
    graph_full = graph
    graph = getattr(L, "graph_used", graph)  # the merge fallback draws a graph with combined sections (mapping kept)
    area = ppo_render.drawing_area(style)
    ppo_layout.place_annotations(L, graph, fonts, style, getattr(L, "annotate_used", graph["display"]["annotate"]), area)
    files = {}
    pal_c, pal_m = ppo_render.PALETTE_COLOUR, ppo_render.PALETTE_MONO
    meta = {"title": tb["title"], "subject": "Process flow diagram overview, case %s" % graph["case"]["id"],
            "keywords": "NeqSim process overview %s" % tb["document_number"]}
    sc_c, extra = draw_sheet(L, graph, fonts, style, pal_c, False, tb, "a3")
    variants = [("overview_A3_colour", sc_c, "A3 colour")]
    if mono:
        sc_m, _ = draw_sheet(L, graph, fonts, style, pal_m, True, tb, "a3")
        variants.append(("overview_A3_mono", sc_m, "A3 monochrome"))
    spec = ppo_layout.to_layout_spec(L, graph, fonts, style, style["page"], tb["document_number"], extra_bboxes=extra)
    layouts = {"a3": (L, spec, style, [v[1] for v in variants])}
    # presentation (16:9): same graph reduced to presentation level; sections that do not fit become blocks with a
    # supporting slide each
    variants_p, fonts_p = [], None
    if presentation:
        cfg_p = dict(cfg, level="presentation")
        graph_p = ppo_graph.build_graph(data, case_id=case_id, config=cfg_p, source_path=source_path)
        graph_p = _apply_section_groups(graph_p, (config or {}).get("presentation_groups"), log)
        so = style_overrides or {}
        style_p = ppo_render.make_style("16x9", so.get("16x9") if isinstance(so.get("16x9"), dict) else None)
        fonts_p = ppo_render.Fonts(style_p)
        area_p = ppo_render.drawing_area(style_p)
        Lp = fit_layout(graph_p, fonts_p, style_p, refs, collapsed=list(L.collapsed), log=log)
        graph_p = getattr(Lp, "graph_used", graph_p)
        ppo_layout.place_annotations(Lp, graph_p, fonts_p, style_p, getattr(Lp, "annotate_used", graph_p["display"]["annotate"]), area_p)
        tb_p = dict(tb, title=tb["title"])
        sc_p, _ = draw_sheet(Lp, graph_p, fonts_p, style_p, pal_c, False, tb_p, "slide")
        variants_p = [("overview_16x9", sc_p, "16:9 colour")]
        if mono:
            sc_pm, _ = draw_sheet(Lp, graph_p, fonts_p, style_p, pal_m, True, tb_p, "slide")
            variants_p.append(("overview_16x9_mono", sc_pm, "16:9 monochrome"))
        spec_p = ppo_layout.to_layout_spec(Lp, graph_p, fonts_p, style_p, style_p["page"], tb["document_number"] + "-16x9")
        layouts["16x9"] = (Lp, spec_p, style_p, [v[1] for v in variants_p])
        _write_json(os.path.join(out_dir, "layout_16x9.json"), _layout_record(Lp, spec_p))
        _write_json(os.path.join(out_dir, "process_graph_presentation.json"), graph_p)
        supporting = []
        for k, sid in enumerate(Lp.collapsed, start=1):
            sub = ppo_graph.subgraph(graph_p, sid)
            Ls = fit_layout(sub, fonts_p, style_p, {}, collapsed=[], max_collapse=0, log=log)
            ppo_layout.place_annotations(Ls, sub, fonts_p, style_p, getattr(Ls, "annotate_used", sub["display"]["annotate"]), area_p)
            name = next(s["name"] for s in graph_p["sections"] if s["id"] == sid)
            tb_s = dict(tb, title="%s - %s" % (tb["title"], name))
            sc_s, _ = draw_sheet(Ls, sub, fonts_p, style_p, pal_c, False, tb_s, "slide")
            variants_p.append(("overview_16x9_detail_%02d" % k, sc_s, "16:9 supporting slide: %s" % name))
            supporting.append({"section": sid, "name": name, "file": "overview_16x9_detail_%02d" % k, "fits": Ls.fits})
        layouts["16x9_supporting"] = supporting
    # export
    for name, scene, label in variants:
        files[name] = _export(scene, fonts, out_dir, name, embed_svg_font, png_dpi, meta, label)
    for name, scene, label in variants_p:
        files[name] = _export(scene, fonts_p, out_dir, name, embed_svg_font, png_dpi, dict(meta, subject=meta["subject"] + " (presentation)"), label)
    _write_json(os.path.join(out_dir, "process_graph.json"), graph_full)
    if graph is not graph_full:
        _write_json(os.path.join(out_dir, "process_graph_drawn.json"), graph)
    _write_json(os.path.join(out_dir, "layout_A3.json"), _layout_record(L, spec))
    _write_json(os.path.join(out_dir, "pfd_layout_spec_A3.json"), spec)  # direct input for `neqsim pfd-check`
    if presentation:
        _write_json(os.path.join(out_dir, "pfd_layout_spec_16x9.json"), layouts["16x9"][1])
    # validation
    record = ppo_validate.validate(graph, data, layouts, files, fonts, problems, detail_manifest, refs)
    _write_json(os.path.join(out_dir, "validation.json"), record)
    _write(os.path.join(out_dir, "validation.md"), ppo_validate.to_markdown(record))
    manifest = {
        "schema": "neqsim.process_overview.manifest/1", "generated_utc": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "case": graph["case"], "level": level, "document_number": tb["document_number"], "revision": tb["revision"],
        "files": files, "graph": os.path.join(out_dir, "process_graph.json"), "layout": os.path.join(out_dir, "layout_A3.json"),
        "validation": os.path.join(out_dir, "validation.json"), "validation_status": record["status"],
        "collapsed_sections": L.collapsed, "detail_sheet_refs": refs, "symbol_scale": L.scale, "fits": L.fits,
        "detail_sheets": detail_manifest and {"pdf": detail_manifest["pdf"], "sheets": [(s["drawing_number"], s["title"]) for s in detail_manifest["sheets"]]},
        "warnings": list(L.warnings) + (["graph: " + p for p in problems]),
        "command": command, "seconds": round(time.time() - t0, 1),
        "counts": graph["counts"],
    }
    _write_json(os.path.join(out_dir, "manifest.json"), manifest)
    log("overview written to %s (%s, %.0f s)" % (out_dir, record["status"], time.time() - t0))
    return manifest


def _export(scene, fonts, out_dir, name, embed_font, dpi, meta, label):
    svg = os.path.join(out_dir, name + ".svg")
    pdf = os.path.join(out_dir, name + ".pdf")
    png = os.path.join(out_dir, name + ".png")
    _write(svg, ppo_render.to_svg(scene, fonts, embed_font=embed_font, metadata=dict(meta, variant=label)))
    sizes = ppo_render.to_pdf([scene], fonts, pdf, metadata=meta)
    px = ppo_render.pdf_to_png(pdf, png, 0, dpi)
    return {"label": label, "svg": svg, "pdf": pdf, "png": png, "page_mm": [round(v, 2) for v in sizes[0]], "png_px": list(px),
            "expected_mm": [round(scene.w, 2), round(scene.h, 2)]}


# ---------------------------------------------------------------------------
# command line
# ---------------------------------------------------------------------------
def _load_title_block(path):
    if not path:
        return {}
    with open(path, "r", encoding="utf-8") as fh:
        text = fh.read()
    if path.lower().endswith((".yaml", ".yml")):
        import yaml
        return yaml.safe_load(text) or {}
    return json.loads(text)


def load_data(path, cases_path=None, log=None):
    """Load ``process_data.json`` directly or solve a model source through :mod:`process_report`."""
    low = path.lower()
    if low.endswith(".json"):
        with open(path, "r", encoding="utf-8") as fh:
            head = json.load(fh)
        if head.get("schema", "").startswith("neqsim.process_study_report.data"):
            return head
    import process_report as pr

    source_type, src = pr.detect_source(path)
    if source_type == "data":
        return pr.load_data_source(src, False, [])
    cases = pr.load_cases(cases_path) if cases_path else pr.default_cases()
    model_name = os.path.splitext(os.path.basename(src))[0]
    return pr.extract_cases(source_type, src, cases, 15.0, 1.01325, model_name, progress=log)


def build_parser():
    p = argparse.ArgumentParser(prog="neqsim process-overview",
                                description="One-sheet A3 process overview and 16:9 presentation version from NeqSim process data")
    p.add_argument("source", help="process_data.json (neqsim process-report) or a model source (.py / .json / .neqsim)")
    p.add_argument("--out", required=True, help="output folder")
    p.add_argument("--case", default=None, help="operating case id to display (default: first case)")
    p.add_argument("--cases", default=None, help="cases.yaml when solving a model source")
    p.add_argument("--level", default="overview", choices=list(ppo_graph.LEVELS), help="A3 information level")
    p.add_argument("--title-block", default=None, help="title block fields (YAML or JSON)")
    p.add_argument("--config", default=None, help="graph configuration overrides (JSON: collapse, group_feeds_min, sections, ...)")
    p.add_argument("--style", default=None, help="style overrides (JSON: pt, line, section, font, 16x9)")
    p.add_argument("--detail-sheets", default="collapsed", choices=["collapsed", "all", "none"])
    p.add_argument("--collapse", action="append", default=[], help="section name forced to block representation (repeatable)")
    p.add_argument("--no-mono", action="store_true", help="skip the monochrome variants")
    p.add_argument("--no-presentation", action="store_true", help="skip the 16:9 version")
    p.add_argument("--no-embed-font", action="store_true", help="do not embed the font subset in the SVG files")
    p.add_argument("--dpi", type=int, default=150, help="PNG preview resolution")
    p.add_argument("--quiet", action="store_true")
    return p


def main(argv=None):
    args = build_parser().parse_args(argv)
    log = (lambda m: None) if args.quiet else (lambda m: print(m, flush=True))
    try:
        data = load_data(args.source, args.cases, log)
        config = json.load(open(args.config, encoding="utf-8")) if args.config else None
        style = json.load(open(args.style, encoding="utf-8")) if args.style else None
        cmd = "neqsim process-overview " + " ".join(argv if argv is not None else sys.argv[1:])
        manifest = build_overview(data, args.out, case_id=args.case, title_block=_load_title_block(args.title_block), level=args.level,
                                  config=config, detail_sheets=args.detail_sheets, style_overrides=style, collapse_sections=args.collapse,
                                  source_path=os.path.abspath(args.source), embed_svg_font=not args.no_embed_font, png_dpi=args.dpi,
                                  mono=not args.no_mono, presentation=not args.no_presentation, log=log, command=cmd)
    except (OverviewError, ValueError, FileNotFoundError) as exc:
        print("process-overview: %s" % exc, file=sys.stderr)
        return 2
    print("status: %s  (%s)" % (manifest["validation_status"], manifest["validation"]))
    return 0 if manifest["validation_status"] != "FAIL" else 1


if __name__ == "__main__":
    sys.exit(main())
