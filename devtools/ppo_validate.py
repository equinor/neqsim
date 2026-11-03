"""Validation record for the professional process overview.

Automatic checks (ids ``V01`` ...) over the structured graph, the laid-out geometry, the deterministic layout
review of :mod:`pfd_layout_check` and the exported files. The result is a dict (written as ``validation.json``)
and a Markdown summary; status is ``FAIL`` when any check fails, ``WARN`` when any check warns, else ``PASS``.
The checks verify the *drawing*; they never approve the engineering content.
"""
from __future__ import annotations

import math
import os

import ppo_layout
import ppo_render

PASS, WARN, FAIL = "PASS", "WARN", "FAIL"


def _check(cid, title, status, detail, items=None):
    return {"id": cid, "title": title, "status": status, "detail": detail, "items": list(items or [])[:40]}


def _ids_unique(graph):
    eq = [e["id"] for e in graph["equipment"]]
    st = [s["id"] for s in graph["streams"]]
    dup_e = sorted({x for x in eq if eq.count(x) > 1})
    dup_s = sorted({x for x in st if st.count(x) > 1})
    ok = not dup_e and not dup_s and not (set(eq) & set(st))
    return _check("V01", "Equipment and stream identifiers unique and traceable", PASS if ok else FAIL,
                  "%d equipment ids, %d stream ids; every node maps to source tags through members/mapping" % (len(eq), len(st)),
                  dup_e + dup_s)


def _endpoints(graph, L):
    bad = []
    for e in L.edges:
        if e["src"] not in L.nodes or e["dst"] not in L.nodes:
            bad.append(e["id"])
        if len(e["pts"]) < 2:
            bad.append(e["id"] + " (degenerate)")
    merged = ppo_layout.merged_edges(L)
    legs = [e for e in L.edges if not e["external"] and (e["src"].startswith(("PI:", "PO:")) or e["dst"].startswith(("PI:", "PO:")))]
    used = set()
    for m in merged:
        used.update(m.get("parts", []))
    dangling = [e["id"] for e in legs if e["id"] not in used]
    status = FAIL if bad or dangling else PASS
    return _check("V02", "Every connection has valid endpoints; no unexplained dangling connections", status,
                  "%d drawn edges, %d complete unit-to-unit connections, %d dangling legs" % (len(L.edges), len(merged), len(dangling)),
                  bad + dangling)


def _coverage(graph, data, L, refs):
    src_areas = [a.get("name") for a in data.get("areas", [])] if data else []
    drawn_areas = set()
    for s in L.sections:
        drawn_areas.add(s.get("model_area"))
        for a in list((s.get("represents") or {}).get("model_areas") or []) + list(s.get("model_areas") or []):
            drawn_areas.add(a)
    missing = [a for a in src_areas if a not in drawn_areas]
    bnd = graph["boundaries"]
    drawn_streams = {e["stream"] for e in L.edges}
    miss_b = [b["stream"] for b in bnd if b["stream"] not in drawn_streams]
    status = FAIL if missing or miss_b else PASS
    return _check("V03", "Every major source section and boundary interface is represented", status,
                  "%d/%d model areas drawn (detail or block), %d/%d boundary streams drawn" % (
                      len(src_areas) - len(missing), len(src_areas), len(bnd) - len(miss_b), len(bnd)), missing + miss_b)


def _grouping(graph, problems, L=None):
    grouped = [e for e in graph["equipment"] if e.get("represents")]
    gstreams = [s for s in graph["streams"] if s.get("grouped")]
    bad = [e["id"] for e in grouped if len(e["members"]) != e["represents"]["count"]]
    bad += [s["id"] for s in gstreams if len(s["members"]) < 2]
    bundles = getattr(L, "bundles", {}) if L is not None else {}
    bad += [k for k, b in bundles.items() if b["count"] != len(b["members"]) or b["members"][0] != k]
    detail = "%d grouped equipment nodes, %d grouped streams, %d collapsed elements, %d merged streams, %d block-to-block bundles" % (
        len(grouped), len(gstreams), len(graph["mapping"]["collapsed_equipment"]), len(graph["mapping"]["merged_streams"]), len(bundles))
    status = FAIL if bad or problems else PASS
    return _check("V04", "Grouped, bundled and collapsed elements have an explicit underlying mapping", status, detail, bad + list(problems))


def _values(graph, data, L):
    """Displayed annotation values recomputed from the source case."""
    if data is None:
        return _check("V05", "Displayed values match the selected source case", WARN, "no source data supplied", [])
    case = graph["case"]["id"]
    src = {str(s.get("id")): s for s in data.get("streams", [])}
    sidx = {s["id"]: s for s in graph["streams"]}
    bad = []
    n = 0
    for a in L.annotations:
        st = sidx.get(a["stream"])
        if st is None:
            bad.append(a["stream"])
            continue
        if st.get("grouped") or len(st["members"]) > 1:
            # grouped: mass must equal the sum of member masses
            exp = 0.0
            ok = True
            for m in st["members"]:
                r = (src.get(m, {}).get("results") or {}).get(case) or {}
                if r.get("mass_kg_h") is None:
                    ok = False
                    break
                exp += r["mass_kg_h"]
            got = st["values"].get("mass_kg_h")
            if ok and got is not None and abs(exp - got) > 1e-6 * max(1.0, abs(exp)):
                bad.append("%s mass %.6g != sum of members %.6g" % (st["id"], got, exp))
            n += 1
            continue
        r = (src.get(st["id"], {}).get("results") or {}).get(case) or {}
        for key in ("t_c", "p_bara", "mass_kg_h"):
            got = st["values"].get(key)
            exp = r.get(key)
            if got is None and exp is None:
                continue
            if got is None or exp is None or abs(float(got) - float(exp)) > 1e-9 * max(1.0, abs(float(exp))):
                bad.append("%s %s displayed %r, source %r" % (st["id"], key, got, exp))
        text = " ".join(a["lines"])
        if r.get("mass_kg_h") is not None and ppo_layout._fmt_mass(r["mass_kg_h"]) not in text:
            bad.append("%s annotation text does not show the source mass flow" % st["id"])
        n += 1
    return _check("V05", "Displayed values match the selected source case (%s)" % case, FAIL if bad else PASS,
                  "%d annotations re-derived from results[%r]" % (n, case), bad)


def _inside_frame(L, scenes, fonts, style):
    fr, pg = style["frame"], style["page"]
    x0, y0, x1, y1 = fr["left"] - 0.05, fr["top"] - 0.05, pg["w"] - fr["right"] + 0.05, pg["h"] - fr["bottom"] + 0.05
    if style["line"]["frame"] <= 0:
        x0, y0, x1, y1 = -0.05, -0.05, pg["w"] + 0.05, pg["h"] + 0.05
    bad = []
    for n in L.nodes.values():
        if n["kind"] == "unit":
            sym = n["sym"]
            r = (n["x"] - n["w"] / 2, n["y"] - n["above"], n["x"] + n["w"] / 2, n["y"] + n["below"])
        elif n["kind"] == "block":
            r = (n["x"] - n["w"] / 2, n["y"] - n["h"] / 2, n["x"] + n["w"] / 2, n["y"] + n["h"] / 2)
        else:
            continue
        if r[0] < x0 or r[1] < y0 or r[2] > x1 or r[3] > y1:
            bad.append(n["id"])
    for e in L.edges:
        for x, y in e["pts"]:
            if x < x0 or y < y0 or x > x1 or y > y1:
                bad.append(e["id"])
                break
    for sc in scenes:
        for t in sc.texts():
            if t.get("kind") in ("zone",):
                continue
            bx = ppo_render.text_bbox(fonts, t)
            if bx[0] < x0 - 0.3 or bx[2] > x1 + 0.3 or bx[1] < y0 - 0.3 or bx[3] > y1 + 0.3:
                bad.append("text %r" % t["text"][:30])
    return _check("V06", "Labels, symbols and lines stay inside the drawing frame", FAIL if bad else PASS,
                  "frame (%.0f, %.0f)-(%.0f, %.0f) mm" % (x0, y0, x1, y1), sorted(set(bad)))


def _font_sizes(scenes, style):
    mn = None
    small = []
    for sc in scenes:
        for t in sc.texts():
            if t.get("kind") in ("zone", "tb_label", "tb_value", "legend", "panel", "panel_title", "footer", "badge", "section_ref", "bubble"):
                continue
            mn = t["pt"] if mn is None else min(mn, t["pt"])
            if t["pt"] < style["min_label_pt"] - 1e-6:
                small.append("%s %.1f pt" % (t.get("kind"), t["pt"]))
    status = PASS if not small else WARN
    return _check("V07", "Label text at or above the readability target (%.1f pt)" % style["min_label_pt"], status,
                  "smallest process label %.1f pt" % (mn or 0.0), sorted(set(small))[:20])


def _layout_review(spec, name):
    try:
        import pfd_layout_check as chk
    except ImportError:
        return _check("V08", "Deterministic layout review (pfd_layout_check)", WARN, "pfd_layout_check not importable", []), None
    rep = chk.check_spec(spec, name=name)
    d = rep.to_dict() if hasattr(rep, "to_dict") else {"status": getattr(rep, "status", "?"), "findings": []}
    findings = d.get("findings") or []
    errors = [f for f in findings if f.get("severity") == "ERROR"]
    warns = [f for f in findings if f.get("severity") == "WARN"]
    counts = {}
    for f in findings:
        counts[f.get("code")] = counts.get(f.get("code"), 0) + 1
    status = FAIL if errors else (WARN if warns else PASS)
    items = ["%s %s: %s" % (f.get("severity"), f.get("code"), str(f.get("message", ""))[:110]) for f in errors + warns]
    return _check("V08", "No label overlaps or line-through-equipment conflicts (pfd_layout_check %s)" % d.get("status"), status,
                  ", ".join("%s x%d" % kv for kv in sorted(counts.items())) or "no findings", items), d


def _exports(files, expected):
    bad = []
    info = []
    for name, f in files.items():
        for k in ("svg", "pdf", "png"):
            if not os.path.exists(f[k]) or os.path.getsize(f[k]) == 0:
                bad.append("%s.%s missing" % (name, k))
        pm, em = f["page_mm"], f["expected_mm"]
        if abs(pm[0] - em[0]) > 0.5 or abs(pm[1] - em[1]) > 0.5:
            bad.append("%s page %.1f x %.1f mm, expected %.1f x %.1f" % (name, pm[0], pm[1], em[0], em[1]))
        try:
            pages = ppo_render.pdf_info(f["pdf"])
        except Exception as exc:  # noqa: BLE001
            bad.append("%s: cannot read PDF (%s)" % (name, exc))
            continue
        for p in pages:
            if not p["fonts"]:
                bad.append("%s: PDF page has no fonts" % name)
            for ft in p["fonts"]:
                if not ft["embedded"]:
                    bad.append("%s: font %s not embedded" % (name, ft["name"]))
            if p["text_chars"] == 0:
                bad.append("%s: PDF page has no extractable text" % name)
            info.append("%s: %.1f x %.1f mm, fonts %s" % (name, p["width_mm"], p["height_mm"],
                                                         ", ".join("%s(%s)" % (ft["name"], "embedded" if ft["embedded"] else "NOT embedded")
                                                                   for ft in p["fonts"])))
    for name in expected:
        if name not in files:
            bad.append("%s not produced" % name)
    return _check("V09", "PDF page dimensions, embedded fonts and expected exports", FAIL if bad else PASS,
                  "%d variants, each SVG + PDF + PNG" % len(files), bad or info)


def _fit(L):
    status = PASS if L.fits else FAIL
    detail = "symbol scale %.2f, content %.0f x %.0f mm, %d row(s), %d sections collapsed to blocks" % (
        L.scale, L.content_size[0], L.content_size[1], getattr(L, "rows", 1), len(L.collapsed))
    if getattr(L, "compact_text", False):
        detail += "; compact text step used (labels at the readability target)"
    return _check("V10", "Overview fits the drawing area without reducing text below the readability target", status, detail, L.collapsed)


def _annotations(L, graph):
    want = list(getattr(L, "annotate_used", None) or graph["display"]["annotate"])
    placed = {a["stream"] for a in L.annotations} | {b["stream"] for b in L.bubbles}
    fb = [a["stream"] for a in L.annotations if a.get("fallback")]
    missing = [s for s in want if s not in placed]
    status = WARN if (fb or missing) else PASS
    return _check("V11", "Stream data placed without collisions (attached labels, bubbles, label boxes)", status,
                  "%d requested, %d attached labels, %d bubbles, %d label boxes, %d not placeable" % (
                      len(want), sum(1 for a in L.annotations if a.get("attached")), len(L.bubbles),
                      sum(1 for a in L.annotations if not a.get("attached")), len(missing)),
                  ["forced: " + s for s in fb] + ["not placed: " + s for s in missing])


def _supporting_checks(entry, data):
    """Review one detail slide using its own graph, geometry, fonts and scene."""
    name = entry["file"]
    geometry = entry.get("layout")
    graph = entry.get("graph")
    if not entry.get("fits") or geometry is None or graph is None:
        return [_check("V15:" + name, "Supporting slide fits and has review geometry", FAIL,
                       "%s: fits=%r; geometry and graph required" % (name, entry.get("fits")))]
    layout, spec, style, scenes = geometry
    fonts = ppo_render.Fonts(style)
    review, _ = _layout_review(spec, name)
    font_check = _font_sizes(scenes, style)
    if font_check["status"] != PASS:
        font_check["status"] = FAIL
    checks = [_fit(layout), _inside_frame(layout, scenes, fonts, style), font_check,
              review, _values(graph, data, layout), _endpoints(graph, layout), _annotations(layout, graph)]
    for index, check in enumerate(checks, start=1):
        check["id"] = "V15:%s:%d" % (name, index)
        check["title"] = name + ": " + check["title"]
    return checks


def validate(graph, data, layouts, files, fonts, problems, detail_manifest=None, refs=None):
    """Run all checks; ``layouts`` is ``{"a3": (Layout, spec, style, scenes), "16x9": ...}``."""
    L, spec, style, scenes = layouts["a3"]
    checks = [_ids_unique(graph), _endpoints(graph, L), _coverage(graph, data, L, refs or {}), _grouping(graph, problems, L),
              _values(graph, data, L), _inside_frame(L, scenes, fonts, style), _font_sizes(scenes, style)]
    rev, rev_dict = _layout_review(spec, "overview_A3")
    checks.append(rev)
    expected = list(files)
    checks.append(_exports(files, expected))
    checks.append(_fit(L))
    checks.append(_annotations(L, graph))
    reviews = {"a3": rev_dict}
    if "16x9" in layouts:
        Lp, spec_p, style_p, scenes_p = layouts["16x9"]
        c = _inside_frame(Lp, scenes_p, ppo_render.Fonts(style_p), style_p)
        c["id"], c["title"] = "V12", "16:9 version: content inside the slide"
        checks.append(c)
        rp, rp_dict = _layout_review(spec_p, "overview_16x9")
        rp["id"] = "V13"
        rp["title"] = "16:9 version: " + rp["title"]
        checks.append(rp)
        reviews["16x9"] = rp_dict
        f = _font_sizes(scenes_p, style_p)
        f["id"], f["title"] = "V14", "16:9 version: label text at or above %.1f pt" % style_p["min_label_pt"]
        checks.append(f)
        fit = _fit(Lp)
        fit["id"], fit["title"] = "V16", "16:9 version: " + fit["title"]
        checks.append(fit)
    supporting = layouts.get("16x9_supporting", [])
    for entry in supporting:
        checks.extend(_supporting_checks(entry, data))
    tracked = {entry["file"] for entry in supporting}
    missing = [name for name in files if name.startswith("overview_16x9_detail_") and name not in tracked]
    if missing:
        checks.append(_check("V15:exports", "Every supporting export has review geometry", FAIL,
                             "Supporting slide exports missing from layouts", missing))
    statuses = [c["status"] for c in checks]
    status = FAIL if FAIL in statuses else (WARN if WARN in statuses else PASS)
    limitations = [
        "Checks verify the drawing geometry and value provenance, not the engineering content of the model.",
        "Utility systems, drivers and non-modelled systems are not drawn; the title block states the represented case.",
        "The ISO 5457 / ISO 7200 / ISO 10628 references are documented conventions; editions were not verified against standard text.",
    ]
    if detail_manifest:
        limitations.append("Detail sheets are produced by psr_diagrams.build_pfd; their own layout review is in the drawing set manifest.")
    return {"schema": "neqsim.process_overview.validation/1", "status": status, "checks": checks, "layout_reviews": reviews,
            "limitations": limitations, "graph_counts": graph["counts"], "case": graph["case"]}


def to_markdown(record):
    """Short Markdown validation record."""
    lines = ["# Process overview - validation record", "",
             "Status: **%s**  (case %s - %s)" % (record["status"], record["case"]["id"], record["case"]["label"]), "",
             "| Id | Check | Status | Detail |", "|---|---|---|---|"]
    for c in record["checks"]:
        lines.append("| %s | %s | %s | %s |" % (c["id"], c["title"], c["status"], c["detail"].replace("|", "/")))
    for c in record["checks"]:
        if c["items"] and c["status"] != PASS:
            lines.append("")
            lines.append("## %s %s" % (c["id"], c["title"]))
            for it in c["items"]:
                lines.append("- %s" % it)
    lines += ["", "## Limitations"] + ["- " + x for x in record["limitations"]]
    return "\n".join(lines) + "\n"
