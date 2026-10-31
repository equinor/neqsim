#!/usr/bin/env python
"""Deterministic layout checker for process flow diagrams (PFD, BFD, P&ID sketches, flow diagrams).

Every flow diagram must pass this check before it is placed in a report, drawing set or deliverable
(see the ``pfd-layout-review`` agent and the ``neqsim-pfd-layout-review`` skill). The checker works on the
*geometry* of the diagram, not on pixels, so it is exact and repeatable:

* **Spec (JSON/dict)**: the generator exports nodes, edges, labels and canvas (``neqsim.pfd_layout.v1``;
  :mod:`pfd_diagram` does this for matplotlib generators). Preferred input.
* **SVG**: parsed into the same model (rect/circle/polygon/polyline/path/text; ``data-tag`` groups are nodes,
  ``data-stream`` groups are edges, as written by ``psr_diagrams``). Text boxes are estimated from font size.
* **PNG/JPG**: raster only. Structural checks are impossible, so the result is capped at WARN
  (``RASTER_ONLY``) and a visual inspection checklist is returned; only whitespace, clipping and
  resolution are measured.

Findings carry a stable ``code``, a severity (ERROR, WARN, INFO), the objects involved, the location and a
concrete fix-up. Status: FAIL if any ERROR, WARN if any WARN, else PASS. A layout score (0-100) summarises
the penalties. Waivers recorded in the spec (``"waivers": [{"code": ..., "reason": ...}]``) turn a code
into INFO and are listed in the report.

Coordinates: x to the right, y DOWN (SVG convention); boxes are ``[x, y, w, h]``.

Usage::

    python pfd_layout_check.py figure.svg other_spec.json --json review.json --md review.md
    python -m pfd_layout_check sheets/*.svg --out-dir pfd_layout_review --strict

Exit code: 0 PASS, 1 WARN, 2 FAIL (with ``--strict`` WARN is also exit 2).
"""
from __future__ import annotations

import argparse
import json
import math
import os
import re
import sys
import xml.etree.ElementTree as ET

SCHEMA = "neqsim.pfd_layout.v1"
ERROR, WARN, INFO = "ERROR", "WARN", "INFO"

DEFAULT_OPTIONS = {
    "min_font_pt_warn": 5.0,
    "min_font_pt_error": 3.5,
    "default_intended_width_mm": 160.0,
    "label_touch_margin": 0.25,  # fraction of the label height
    "attach_tol_frac": 0.006,  # fraction of the canvas diagonal
    "min_box_gap_frac": 0.012,
    "min_port_sep_frac": 0.03,
    "empty_pocket_frac": 0.14,
    "off_center_frac": 0.15,
    "quadrant_share_min": 0.08,
    "feed_left_frac": 0.5,
    "product_left_frac": 0.3,
    "backward_edge_frac": 0.34,
    "max_crossings": 0,
    "require_tags": None,  # None: PFD and P&ID sketches need tags, BFD does not
    "require_stream_ids": None,  # None: PFD and P&ID sketches need stream ids, BFD does not
    "require_legend": None,  # None: needed when two or more line styles are used
    "require_footer": True,
}

EQUIPMENT_KINDS = {"vessel", "separator", "scrubber", "column", "absorber", "contactor", "stripper", "tower",
                   "hx", "exchanger", "cooler", "heater", "reboiler", "condenser", "pump", "compressor", "expander",
                   "turbine", "tank", "filter", "coalescer", "hydrocyclone", "reactor", "furnace", "valve", "choke",
                   "drum", "mixer", "splitter", "equipment", "unit"}
NO_TAG_KINDS = {"boundary", "feed", "product", "junction", "note", "block", "node", "offpage", "connector", "terminal",
                "pipe", "legend", "title"}
COLUMN_KINDS = {"column", "absorber", "contactor", "stripper", "tower"}
GAS_FLUIDS = {"gas", "vapor", "vapour", "air", "fuelgas", "flashgas", "steam"}
LIQUID_FLUIDS = {"oil", "water", "liquid", "glycol", "teg", "meg", "condensate", "hc", "lean", "rich", "brine"}
TAG_RE = re.compile(r"\b\d{1,3}[A-Z]{1,3}[-_ ]?\d{2,5}[A-Z]{0,2}\b|\b[A-Z]{1,4}[-_]\d{2,5}[A-Z]?\b|\b[A-Z]{2,5}\d{3,5}[A-Z]?\b")
STREAM_RE = re.compile(r"^\s*(?:S|stream\s*)?\d{1,4}\s*$", re.I)

PENALTY = {ERROR: 8.0, WARN: 3.0, INFO: 0.0}


# --------------------------------------------------------------------------------------------- geometry
def rect_of(b):
    x, y, w, h = b
    return (float(x), float(y), float(x) + float(w), float(y) + float(h))


def overlap_area(a, b):
    ax0, ay0, ax1, ay1 = rect_of(a)
    bx0, by0, bx1, by1 = rect_of(b)
    return max(0.0, min(ax1, bx1) - max(ax0, bx0)) * max(0.0, min(ay1, by1) - max(ay0, by0))


def inflate(b, m):
    x, y, w, h = b
    return [x - m, y - m, w + 2 * m, h + 2 * m]


def seg_inside_rect_len(p, q, b):
    """Length of segment p-q inside the open rectangle b (Liang-Barsky)."""
    x0, y0, x1, y1 = rect_of(b)
    if x1 <= x0 or y1 <= y0:
        return 0.0
    dx, dy = q[0] - p[0], q[1] - p[1]
    t0, t1 = 0.0, 1.0
    for pp, qq in ((-dx, p[0] - x0), (dx, x1 - p[0]), (-dy, p[1] - y0), (dy, y1 - p[1])):
        if abs(pp) < 1e-12:
            if qq < 0:
                return 0.0
        else:
            r = qq / pp
            if pp < 0:
                if r > t1:
                    return 0.0
                t0 = max(t0, r)
            else:
                if r < t0:
                    return 0.0
                t1 = min(t1, r)
    if t1 <= t0:
        return 0.0
    return (t1 - t0) * math.hypot(dx, dy)


def seg_intersection(p1, p2, p3, p4):
    """Proper interior intersection point of two segments or None (parallel/touching ends excluded)."""
    d1x, d1y = p2[0] - p1[0], p2[1] - p1[1]
    d2x, d2y = p4[0] - p3[0], p4[1] - p3[1]
    den = d1x * d2y - d1y * d2x
    if abs(den) < 1e-12:
        return None
    t = ((p3[0] - p1[0]) * d2y - (p3[1] - p1[1]) * d2x) / den
    u = ((p3[0] - p1[0]) * d1y - (p3[1] - p1[1]) * d1x) / den
    eps = 1e-6
    if eps < t < 1 - eps and eps < u < 1 - eps:
        return (p1[0] + t * d1x, p1[1] + t * d1y)
    return None


def point_seg_dist(pt, a, b):
    ax, ay, bx, by = a[0], a[1], b[0], b[1]
    dx, dy = bx - ax, by - ay
    L2 = dx * dx + dy * dy
    if L2 < 1e-18:
        return math.hypot(pt[0] - ax, pt[1] - ay), 0.0
    t = max(0.0, min(1.0, ((pt[0] - ax) * dx + (pt[1] - ay) * dy) / L2))
    return math.hypot(pt[0] - (ax + t * dx), pt[1] - (ay + t * dy)), t


def point_rect_boundary_dist(pt, b):
    x0, y0, x1, y1 = rect_of(b)
    px, py = pt
    dx = max(x0 - px, 0.0, px - x1)
    dy = max(y0 - py, 0.0, py - y1)
    if dx > 0 or dy > 0:
        return math.hypot(dx, dy)
    return min(px - x0, x1 - px, py - y0, y1 - py)


def side_of(pt, b):
    """Side of rectangle b nearest to pt: 'l', 'r', 't' (smaller y) or 'b'."""
    x0, y0, x1, y1 = rect_of(b)
    d = {"l": abs(pt[0] - x0), "r": abs(pt[0] - x1), "t": abs(pt[1] - y0), "b": abs(pt[1] - y1)}
    return min(d, key=d.get)


def estimate_text_bbox(text, x, y, font_size, anchor="start", bold=False, baseline=True):
    lines = str(text).split("\n")
    cw = font_size * (0.55 if bold else 0.50)
    w = max(len(s) for s in lines) * cw
    h = font_size * 1.15 * len(lines)
    if anchor == "middle":
        x0 = x - w / 2
    elif anchor == "end":
        x0 = x - w
    else:
        x0 = x
    y0 = y - font_size * 0.85 if baseline else y
    return [x0, y0, w, h]


# --------------------------------------------------------------------------------------------- model
class Finding(dict):
    """A single check result (dict so it serialises directly)."""


class Report:
    """Result of one diagram check."""

    def __init__(self, name, kind):
        self.name = name
        self.kind = kind
        self.findings = []
        self.metrics = {}
        self.waived = []
        self.checklist = []

    def add(self, code, severity, message, fix="", objects=None, at=None):
        self.findings.append(Finding(code=code, severity=severity, message=message, fix=fix,
                                     objects=list(objects or []), at=[round(at[0], 2), round(at[1], 2)] if at else None))

    @property
    def counts(self):
        c = {ERROR: 0, WARN: 0, INFO: 0}
        for f in self.findings:
            c[f["severity"]] += 1
        return c

    @property
    def status(self):
        c = self.counts
        if c[ERROR]:
            return "FAIL"
        if c[WARN]:
            return "WARN"
        return "PASS"

    @property
    def score(self):
        pen = 0.0
        crossing_pen = 0.0
        for f in self.findings:
            if f["code"] == "CROSSING":
                crossing_pen += 2.0 * f.get("count", 1)
            else:
                pen += PENALTY[f["severity"]]
        return round(max(0.0, 100.0 - pen - min(crossing_pen, 20.0)), 1)

    def to_dict(self):
        return {"schema": "neqsim.pfd_layout_review.v1", "name": self.name, "kind": self.kind, "status": self.status,
                "score": self.score, "counts": self.counts, "metrics": self.metrics, "findings": self.findings,
                "waived": self.waived, "visual_checklist": self.checklist}

    def to_markdown(self):
        d = self.to_dict()
        out = ["### %s - %s (score %s)" % (self.name, d["status"], d["score"]),
               "", "Errors %d, warnings %d, info %d." % (d["counts"][ERROR], d["counts"][WARN], d["counts"][INFO]), ""]
        if self.findings:
            out += ["| Severity | Code | Finding | Fix-up |", "|---|---|---|---|"]
            for f in sorted(self.findings, key=lambda f: ({ERROR: 0, WARN: 1, INFO: 2}[f["severity"]], f["code"])):
                at = " @(%s, %s)" % tuple(f["at"]) if f.get("at") else ""
                out.append("| %s | %s | %s%s | %s |" % (f["severity"], f["code"], f["message"].replace("|", "/"), at, f["fix"].replace("|", "/")))
        else:
            out.append("No findings.")
        if self.waived:
            out += ["", "Waived: " + "; ".join("%s (%s)" % (w["code"], w.get("reason", "")) for w in self.waived)]
        if self.checklist:
            out += ["", "Visual inspection checklist:"] + ["- " + c for c in self.checklist]
        return "\n".join(out) + "\n"


def _norm_spec(spec):
    s = json.loads(json.dumps(spec))  # deep copy, JSON-safe
    s.setdefault("kind", "pfd")
    s.setdefault("name", s.get("title") or "diagram")
    cv = s.setdefault("canvas", {})
    if not cv.get("width") or not cv.get("height"):
        raise ValueError("spec.canvas.width/height are required")
    cv.setdefault("unit", "px")
    if "intended_width_mm" not in cv:
        cv["intended_width_mm"] = cv["width"] if cv["unit"] == "mm" else DEFAULT_OPTIONS["default_intended_width_mm"]
    for i, n in enumerate(s.setdefault("nodes", [])):
        n.setdefault("id", "n%d" % i)
        n.setdefault("kind", "equipment")
        n.setdefault("texts", [])
        for t in n["texts"]:
            _fill_text_bbox(t)
    for i, e in enumerate(s.setdefault("edges", [])):
        e.setdefault("id", "e%d" % i)
        e["points"] = _dedupe([(float(p[0]), float(p[1])) for p in e.get("points", [])])
        e.setdefault("arrow_end", False)
        e.setdefault("arrow_start", False)
        e.setdefault("arrow_mid", False)
        e.setdefault("role", "")
        if e.get("label"):
            _fill_text_bbox(e["label"])
            e["label"].setdefault("kind", "stream")
    for t in s.setdefault("labels", []):
        _fill_text_bbox(t)
        t.setdefault("kind", "label")
    s.setdefault("junctions", [])
    s.setdefault("waivers", [])
    return s


def _fill_text_bbox(t):
    fs = float(t.get("font_size", 3.0))
    t["font_size"] = fs
    if not t.get("bbox"):
        t["bbox"] = estimate_text_bbox(t.get("text", ""), float(t.get("x", 0)), float(t.get("y", 0)), fs,
                                       t.get("anchor", "start"), bool(t.get("bold")), baseline=t.get("baseline", True))
        t["est"] = True
    t["bbox"] = [float(v) for v in t["bbox"]]


def _core(t):
    """Text box used for collision tests: estimated boxes (no font metrics) are shrunk to avoid false positives."""
    x, y, w, h = t["bbox"]
    if t.get("est"):
        return [x + 0.06 * w, y + 0.10 * h, 0.84 * w, 0.80 * h]
    return [x, y, w, h]


def _dedupe(pts):
    out = []
    for p in pts:
        if not out or math.hypot(p[0] - out[-1][0], p[1] - out[-1][1]) > 1e-9:
            out.append(p)
    return out


def _segments(e):
    p = e["points"]
    return [(p[i], p[i + 1]) for i in range(len(p) - 1)]


def _flow_vector(e):
    p = e["points"]
    if len(p) < 2:
        return None
    if e.get("arrow_start") and not e.get("arrow_end"):
        return (p[0][0] - p[-1][0], p[0][1] - p[-1][1])
    return (p[-1][0] - p[0][0], p[-1][1] - p[0][1])


# --------------------------------------------------------------------------------------------- checks
def check_spec(spec, options=None, name=None):
    """Check a diagram spec (see module doc) and return a :class:`Report`."""
    o = dict(DEFAULT_OPTIONS)
    o.update(options or {})
    s = _norm_spec(spec)
    o.update(s.get("options", {}))
    kind = s["kind"]
    rep = Report(name or s["name"], kind)
    W, H = float(s["canvas"]["width"]), float(s["canvas"]["height"])
    diag = math.hypot(W, H)
    tol = max(1e-6, 0.002 * diag)
    attach_tol = max(2 * tol, o["attach_tol_frac"] * diag)
    mm_per_unit = float(s["canvas"]["intended_width_mm"]) / W
    pt_per_unit = mm_per_unit / 0.352778
    nodes = {n["id"]: n for n in s["nodes"]}
    edges = s["edges"]
    area = s.get("drawing_area") or [0, 0, W, H]
    obstacles = []  # extra rectangles (legend, title block) that nothing may overlap
    for key in ("legend", "title_block"):
        v = s.get(key)
        if isinstance(v, dict) and v.get("bbox"):
            obstacles.append((key, [float(c) for c in v["bbox"]]))
    rep.metrics.update({"nodes": len(nodes), "edges": len(edges), "labels": len(s["labels"]) + sum(1 for e in edges if e.get("label")),
                        "canvas": [W, H], "effective_font_pt_min": None})
    if not s["nodes"] and not edges:
        rep.add("NO_STRUCTURE", WARN, "No boxes or streams could be read from this drawing, so overlaps, crossings and flow direction were not checked",
                "Generate the diagram through PfdDiagram or supply a JSON spec / SVG with data-tag and data-stream groups, then view it and review it by eye", [])
    real_nodes = [n for n in s["nodes"] if n["kind"] != "junction"]
    spaced_nodes = [n for n in real_nodes if n["kind"] != "connector"]
    junction_pts = [tuple(j[:2]) for j in s["junctions"]] + [
        (n["bbox"][0] + n["bbox"][2] / 2, n["bbox"][1] + n["bbox"][3] / 2) for n in s["nodes"] if n["kind"] == "junction"]

    _connectivity(s, nodes, edges, attach_tol, tol)

    # ---- boxes
    min_gap = o["min_box_gap_frac"] * diag
    for i, a in enumerate(real_nodes):
        for b in real_nodes[i + 1:]:
            ov = overlap_area(a["bbox"], b["bbox"])
            if ov > 1e-6:
                ra, rb = rect_of(a["bbox"]), rect_of(b["bbox"])
                px = min(ra[2], rb[2]) - max(ra[0], rb[0])
                py = min(ra[3], rb[3]) - max(ra[1], rb[1])
                fix = ("Move '%s' %s by at least %.1f units (or add %.1f units of clearance) so the boxes do not overlap"
                       % (b["id"], "right" if rb[0] >= ra[0] else "left", px + min_gap, px + min_gap) if px <= py else
                       "Move '%s' %s by at least %.1f units (or add row spacing) so the boxes do not overlap"
                       % (b["id"], "down" if rb[1] >= ra[1] else "up", py + min_gap))
                rep.add("BOX_OVERLAP", ERROR, "Boxes '%s' and '%s' overlap (area %.1f)" % (a["id"], b["id"], ov), fix,
                        [a["id"], b["id"]], (max(ra[0], rb[0]), max(ra[1], rb[1])))
            else:
                gap = _rect_gap(a["bbox"], b["bbox"])
                if gap < min_gap and a in spaced_nodes and b in spaced_nodes and a["kind"] != "boundary" and b["kind"] != "boundary":
                    rep.add("BOX_TOUCH", WARN, "Boxes '%s' and '%s' touch or are only %.1f units apart (min %.1f)" % (a["id"], b["id"], gap, min_gap),
                            "Increase the spacing between the two boxes to at least %.1f units" % min_gap, [a["id"], b["id"]])
    for key, ob in obstacles:
        for n in real_nodes:
            if overlap_area(n["bbox"], ob) > 1e-6:
                rep.add("BOX_OVERLAP", ERROR, "Box '%s' overlaps the %s" % (n["id"], key), "Move the box out of the %s area" % key, [n["id"], key])

    # ---- node containment in canvas
    for n in s["nodes"]:
        r = rect_of(n["bbox"])
        if r[0] < -tol or r[1] < -tol or r[2] > W + tol or r[3] > H + tol:
            rep.add("OUTSIDE_CANVAS", ERROR, "Box '%s' extends outside the canvas" % n["id"], "Move/scale the diagram into the canvas", [n["id"]])

    # ---- labels
    labels = _collect_labels(s, edges, real_nodes)
    fonts = []
    for lb in labels:
        t = lb["t"]
        fonts.append(t["font_size"] * pt_per_unit)
        r = rect_of(_core(t))
        if r[0] < -tol or r[1] < -tol or r[2] > W + tol or r[3] > H + tol:
            rep.add("TEXT_OUTSIDE_CANVAS", ERROR, "Text '%s' is outside the canvas" % _short(t.get("text")), "Move the label inside the canvas or enlarge the canvas",
                    [lb["id"]], (r[0], r[1]))
    if fonts:
        mn = min(fonts)
        rep.metrics["effective_font_pt_min"] = round(mn, 2)
        small = [lb for lb in labels if lb["t"]["font_size"] * pt_per_unit < o["min_font_pt_warn"]]
        if mn < o["min_font_pt_error"]:
            rep.add("TEXT_TOO_SMALL", ERROR, "%d text items are below %.1f pt at the intended print width (smallest %.1f pt)" % (len(small), o["min_font_pt_error"], mn),
                    "Raise the font size to at least %.1f pt effective (or enlarge the figure)" % o["min_font_pt_warn"], [lb["id"] for lb in small[:6]])
        elif small:
            rep.add("TEXT_TOO_SMALL", WARN, "%d text items are below %.1f pt at the intended print width (smallest %.1f pt)" % (len(small), o["min_font_pt_warn"], mn),
                    "Raise the font size to at least %.1f pt effective" % o["min_font_pt_warn"], [lb["id"] for lb in small[:6]])

    # node text overflow
    for n in real_nodes:
        for t in n["texts"]:
            if overlap_area(t["bbox"], n["bbox"]) < 0.93 * t["bbox"][2] * t["bbox"][3] and n["kind"] not in ("junction",):
                if t.get("kind") in ("tag_above", "subtitle", "outside"):
                    continue
                rep.add("NODE_TEXT_OVERFLOW", WARN, "Text '%s' does not fit inside box '%s'" % (_short(t.get("text")), n["id"]),
                        "Enlarge the box, shorten the text or move it outside the box as a tag", [n["id"]], (t["bbox"][0], t["bbox"][1]))

    # label-label, label-box, label-line
    margin_f = o["label_touch_margin"]
    segs = [(e, a, b) for e in edges for (a, b) in _segments(e)]
    for i, la in enumerate(labels):
        ta = dict(la["t"], bbox=_core(la["t"]))
        ha = ta["bbox"][3]
        if la["kind"] == "annotation":
            continue
        for lb in labels[i + 1:]:
            if lb["kind"] == "annotation":
                continue
            if la["owner_node"] and la["owner_node"] == lb["owner_node"]:
                continue
            tb = dict(lb["t"], bbox=_core(lb["t"]))
            if overlap_area(ta["bbox"], tb["bbox"]) > 1e-6:
                rep.add("LABEL_LABEL", ERROR, "Labels '%s' and '%s' overlap" % (_short(ta.get("text")), _short(tb.get("text"))),
                        "Move one label (try the other side of its line) or shorten the text", [la["id"], lb["id"]], (ta["bbox"][0], ta["bbox"][1]))
            elif not (ta.get("est") or tb.get("est")) and overlap_area(inflate(ta["bbox"], margin_f * min(ha, tb["bbox"][3])), tb["bbox"]) > 1e-6:
                rep.add("LABEL_LABEL", WARN, "Labels '%s' and '%s' touch" % (_short(ta.get("text")), _short(tb.get("text"))),
                        "Add clearance between the two labels", [la["id"], lb["id"]], (ta["bbox"][0], ta["bbox"][1]))
        for n in real_nodes:
            if la["owner_node"] == n["id"]:
                continue
            if overlap_area(ta["bbox"], n["bbox"]) > 1e-6:
                rep.add("LABEL_BOX", ERROR, "Label '%s' overlaps box '%s'" % (_short(ta.get("text")), n["id"]),
                        "Move the label off the box (place it beside the line it belongs to)", [la["id"], n["id"]], (ta["bbox"][0], ta["bbox"][1]))
            elif not ta.get("est") and overlap_area(inflate(ta["bbox"], margin_f * ha), n["bbox"]) > 1e-6 and la["kind"] != "node":
                rep.add("LABEL_BOX", WARN, "Label '%s' touches box '%s'" % (_short(ta.get("text")), n["id"]),
                        "Leave at least %.1f units between label and box" % (margin_f * ha), [la["id"], n["id"]], (ta["bbox"][0], ta["bbox"][1]))
        for (e, p, q) in segs:
            if la["owner_edge"] == e["id"] and la["kind"] == "streamno":
                continue
            if seg_inside_rect_len(p, q, inflate(ta["bbox"], -0.08 * ha)) > tol:
                where = "its own line" if la["owner_edge"] == e["id"] else "line '%s'" % e["id"]
                rep.add("LABEL_LINE", ERROR, "Label '%s' lies on %s" % (_short(ta.get("text")), where),
                        "Move the label beside the line (offset by at least half a text height) or break the line around it",
                        [la["id"], e["id"]], (ta["bbox"][0], ta["bbox"][1]))
                break
            if not ta.get("est") and seg_inside_rect_len(p, q, inflate(ta["bbox"], margin_f * ha)) > tol and la["kind"] != "node":
                rep.add("LABEL_LINE", WARN, "Label '%s' touches line '%s'" % (_short(ta.get("text")), e["id"]),
                        "Offset the label from the line by at least %.1f units" % (margin_f * ha), [la["id"], e["id"]], (ta["bbox"][0], ta["bbox"][1]))
                break
        for key, ob in obstacles:
            if overlap_area(ta["bbox"], ob) > 1e-6 and la["owner_node"] is None and not la.get("in_obstacle") == key:
                rep.add("LABEL_BOX", ERROR, "Label '%s' overlaps the %s" % (_short(ta.get("text")), key), "Move the label out of the %s" % key, [la["id"]])

    # ---- lines through boxes, diagonal segments
    for e in edges:
        pts = e["points"]
        if len(pts) < 2:
            rep.add("EDGE_DEGENERATE", WARN, "Edge '%s' has fewer than two distinct points" % e["id"], "Remove or fix the edge", [e["id"]])
            continue
        for (p, q) in _segments(e):
            if abs(p[0] - q[0]) > tol and abs(p[1] - q[1]) > tol and not e.get("allow_diagonal"):
                rep.add("LINE_DIAGONAL", WARN, "Edge '%s' has a diagonal segment" % e["id"], "Route the line orthogonally with port sides and corner points", [e["id"]], p)
                break
        for n in real_nodes:
            inside = sum(seg_inside_rect_len(p, q, inflate(n["bbox"], -2 * attach_tol)) for (p, q) in _segments(e))
            if inside > 3 * tol:
                own = n["id"] in (e.get("from"), e.get("to"))
                rep.add("LINE_THROUGH_BOX", ERROR, "Edge '%s' runs through box '%s'%s" % (e["id"], n["id"], " (its own end box)" if own else ""),
                        "Re-route '%s' around '%s' through a free channel, or end it on the box edge" % (e["id"], n["id"]), [e["id"], n["id"]],
                        pts[0])

    _crossings_and_overlaps(rep, s, edges, junction_pts, tol, attach_tol, o)
    _arrows(rep, s, edges, nodes, attach_tol)
    _direction_rules(rep, s, edges, nodes, real_nodes, W, H, o, attach_tol)
    _ports(rep, s, edges, nodes, real_nodes, attach_tol, o, diag)
    _tags_and_streams(rep, s, real_nodes, edges, o, kind)
    _symbols(rep, s, real_nodes, edges, o)
    _furniture(rep, s, edges, o, kind)
    _whitespace(rep, s, real_nodes, edges, labels, W, H, area, o)

    # waivers
    waived_codes = {w["code"]: w for w in s["waivers"] if w.get("code")}
    for f in rep.findings:
        w = waived_codes.get(f["code"])
        if w and f["severity"] != INFO:
            f["message"] += " [waived: %s]" % w.get("reason", "no reason given")
            f["severity"] = INFO
            if w not in rep.waived:
                rep.waived.append(w)
    return rep


def _short(t, n=36):
    t = str(t or "").replace("\n", " / ")
    return t if len(t) <= n else t[: n - 1] + "..."


def _rect_gap(a, b):
    ax0, ay0, ax1, ay1 = rect_of(a)
    bx0, by0, bx1, by1 = rect_of(b)
    dx = max(bx0 - ax1, ax0 - bx1, 0.0)
    dy = max(by0 - ay1, ay0 - by1, 0.0)
    return math.hypot(dx, dy)


def _collect_labels(s, edges, real_nodes):
    out = []
    for n in s["nodes"]:
        for j, t in enumerate(n["texts"]):
            out.append({"id": "%s.text%d" % (n["id"], j), "t": t, "kind": "node", "owner_node": n["id"], "owner_edge": None})
    for e in edges:
        if e.get("label"):
            out.append({"id": "%s.label" % e["id"], "t": e["label"], "kind": e["label"].get("kind", "stream"), "owner_node": None, "owner_edge": e["id"]})
        for j, t in enumerate(e.get("labels", [])):
            _fill_text_bbox(t)
            out.append({"id": "%s.label%d" % (e["id"], j), "t": t, "kind": t.get("kind", "stream"), "owner_node": None, "owner_edge": e["id"]})
    for j, t in enumerate(s["labels"]):
        out.append({"id": "label%d" % j, "t": t, "kind": t.get("kind", "label"), "owner_node": t.get("owner") if t.get("owner") in {n["id"] for n in s["nodes"]} else None,
                    "owner_edge": t.get("owner_edge") or (t.get("owner")[5:] if str(t.get("owner", "")).startswith("edge:") else None), "in_obstacle": t.get("in_obstacle")})
    # annotation labels (title block, notes, legend text) are only checked against the canvas and the minimum font size
    return out


def _connectivity(s, nodes, edges, attach_tol, tol):
    """Infer edge from/to node ids from geometry when not given."""
    for e in edges:
        pts = e["points"]
        if len(pts) < 2:
            continue
        for key, pt in (("from", pts[0]), ("to", pts[-1])):
            if e.get(key) in nodes:
                continue
            best, bd = None, attach_tol
            for n in s["nodes"]:
                d = point_rect_boundary_dist(pt, n["bbox"])
                if d <= bd:
                    best, bd = n["id"], d
            e[key] = best


def _crossings_and_overlaps(rep, s, edges, junction_pts, tol, attach_tol, o):
    crossings = []
    jtol = 3 * attach_tol

    def near_junction(pt):
        return any(math.hypot(pt[0] - j[0], pt[1] - j[1]) <= jtol for j in junction_pts)

    for i, a in enumerate(edges):
        for b in edges[i:]:
            same = a is b
            for ia, (p1, p2) in enumerate(_segments(a)):
                for ib, (p3, p4) in enumerate(_segments(b)):
                    if same and ib <= ia + 1:
                        continue
                    x = seg_intersection(p1, p2, p3, p4)
                    if x and not near_junction(x):
                        crossings.append((a["id"], b["id"], x))
                    if same:
                        continue
                    # collinear overlap
                    ov = _collinear_overlap(p1, p2, p3, p4, tol)
                    if ov > 3 * tol and not (a.get("stream") and a.get("stream") == b.get("stream")) \
                            and not (a.get("bundle") and a.get("bundle") == b.get("bundle")):
                        mid = ((p1[0] + p2[0]) / 2, (p1[1] + p2[1]) / 2)
                        if not near_junction(mid):
                            rep.add("LINE_OVERLAP", ERROR, "Edges '%s' and '%s' run on top of each other for %.1f units" % (a["id"], b["id"], ov),
                                    "Separate the two lines onto parallel lanes (at least 2 line widths apart) or merge them with a junction", [a["id"], b["id"]], mid)
    # dedupe crossings
    uniq = []
    for c in crossings:
        if not any(math.hypot(c[2][0] - u[2][0], c[2][1] - u[2][1]) < 2 * tol and {c[0], c[1]} == {u[0], u[1]} for u in uniq):
            uniq.append(c)
    rep.metrics["crossings"] = len(uniq)
    if len(uniq) > o["max_crossings"]:
        pairs = sorted({"%s x %s" % (c[0], c[1]) for c in uniq})
        rep.add("CROSSING", WARN, "%d line crossings (%s)" % (len(uniq), ", ".join(pairs[:6]) + (" ..." if len(pairs) > 6 else "")),
                "Re-order nodes/rows or move the routing channel so lines do not cross; if a crossing is unavoidable add a line-jump or declare it in spec.options.max_crossings with a reason",
                sorted({c[0] for c in uniq} | {c[1] for c in uniq}), uniq[0][2])
        rep.findings[-1]["count"] = len(uniq) - o["max_crossings"]

    # T-contacts and merges without junction
    ends = []
    for e in edges:
        if len(e["points"]) >= 2:
            ends.append((e, "start", e["points"][0]))
            ends.append((e, "end", e["points"][-1]))
    reported = set()
    for e, which, pt in ends:
        on_node = any(point_rect_boundary_dist(pt, n["bbox"]) <= attach_tol for n in s["nodes"])
        if on_node or near_junction(pt):
            continue
        hit = None
        for b in edges:
            if b is e:
                continue
            for (p, q) in _segments(b):
                d, t = point_seg_dist(pt, p, q)
                if d <= attach_tol and 0.0 <= t <= 1.0:
                    # a continuation (end meets the start/end of the other edge) is not a merge
                    at_end = math.hypot(pt[0] - b["points"][0][0], pt[1] - b["points"][0][1]) <= attach_tol or \
                        math.hypot(pt[0] - b["points"][-1][0], pt[1] - b["points"][-1][1]) <= attach_tol
                    if at_end:
                        other = "start" if math.hypot(pt[0] - b["points"][0][0], pt[1] - b["points"][0][1]) <= attach_tol else "end"
                        if other != which:
                            continue  # one ends where the other starts: plain continuation
                    hit = b
                    break
            if hit:
                break
        if hit:
            key = tuple(sorted((e["id"], hit["id"])))
            if key not in reported:
                reported.add(key)
                rep.add("MERGE_WITHOUT_JUNCTION", ERROR, "Edge '%s' %s on edge '%s' without a junction marker" % (e["id"], "starts" if which == "start" else "ends", hit["id"]),
                        "Add a junction dot/tee node at (%.1f, %.1f) and split the edges there, or route '%s' to its own port" % (pt[0], pt[1], e["id"]),
                        [e["id"], hit["id"]], pt)
        else:
            dangling = not any(point_rect_boundary_dist(pt, n["bbox"]) <= attach_tol for n in s["nodes"])
            at_canvas = pt[0] <= attach_tol or pt[1] <= attach_tol or pt[0] >= float(s["canvas"]["width"]) - attach_tol or pt[1] >= float(s["canvas"]["height"]) - attach_tol
            if dangling and not at_canvas and not e.get("open_end"):
                rep.add("DANGLING_END", WARN, "Edge '%s' %s does not connect to a box, junction or another line" % (e["id"], which),
                        "Connect it to a node port or add an off-page/boundary connector", [e["id"]], pt)


def _collinear_overlap(p1, p2, p3, p4, tol):
    if abs(p1[1] - p2[1]) <= tol and abs(p3[1] - p4[1]) <= tol and abs(p1[1] - p3[1]) <= tol:
        a0, a1 = sorted((p1[0], p2[0]))
        b0, b1 = sorted((p3[0], p4[0]))
        return max(0.0, min(a1, b1) - max(a0, b0))
    if abs(p1[0] - p2[0]) <= tol and abs(p3[0] - p4[0]) <= tol and abs(p1[0] - p3[0]) <= tol:
        a0, a1 = sorted((p1[1], p2[1]))
        b0, b1 = sorted((p3[1], p4[1]))
        return max(0.0, min(a1, b1) - max(a0, b0))
    return 0.0


def _arrows(rep, s, edges, nodes, attach_tol):
    for e in edges:
        if len(e["points"]) < 2:
            continue
        has_end = e.get("arrow_end") or e.get("arrow_start")
        touches = e.get("from") or e.get("to")
        if not has_end and not e.get("arrow_mid"):
            if touches:
                rep.add("NO_ARROWHEAD", ERROR, "Edge '%s' ends on box '%s' without an arrowhead (no flow direction)" % (e["id"], e.get("to") or e.get("from")),
                        "Add an arrowhead at the downstream end", [e["id"]], e["points"][-1])
            else:
                rep.add("NO_DIRECTION", ERROR, "Edge '%s' has no arrowhead (flow direction unknown)" % e["id"], "Add an arrowhead at the downstream end", [e["id"]], e["points"][0])
        elif not has_end and e.get("arrow_mid") and e.get("to") and e.get("from"):
            rep.add("NO_ARROWHEAD", WARN, "Edge '%s' shows direction only by a mid-line arrow; its end on box '%s' has no arrowhead" % (e["id"], e.get("to")),
                    "Put the arrowhead at the box inlet", [e["id"]], e["points"][-1])
        if has_end and len(e["points"]) >= 2:
            last = math.hypot(e["points"][-1][0] - e["points"][-2][0], e["points"][-1][1] - e["points"][-2][1])
            al = float(s.get("arrow_length", 0) or 0)
            if al and last < 1.5 * al and e.get("arrow_end"):
                rep.add("SHORT_FINAL_SEGMENT", WARN, "Final segment of edge '%s' is shorter than 1.5 arrowheads" % e["id"], "Lengthen the last segment", [e["id"]], e["points"][-1])


def _flow_class(fluid):
    f = str(fluid or "").lower().replace(" ", "").replace("_", "")
    if f in GAS_FLUIDS:
        return "gas"
    if f in LIQUID_FLUIDS:
        return "liquid"
    return None


def _direction_rules(rep, s, edges, nodes, real_nodes, W, H, o, attach_tol):
    flow = s.get("flow_direction", "lr")
    if flow == "lr":
        horiz = [e for e in edges if e["role"] not in ("wrap", "recycle", "return") and _flow_vector(e)
                 and abs(_flow_vector(e)[0]) >= abs(_flow_vector(e)[1]) and abs(_flow_vector(e)[0]) > attach_tol]
        back = [e for e in horiz if _flow_vector(e)[0] < 0 and not (e.get("arrow_start") and e.get("arrow_end"))]
        rep.metrics["backward_edges"] = len(back)
        rep.metrics["horizontal_edges"] = len(horiz)
        if horiz and len(back) / float(len(horiz)) > o["backward_edge_frac"]:
            rep.add("FLOW_DIRECTION", WARN, "%d of %d horizontal streams flow right-to-left (%s)" % (len(back), len(horiz), ", ".join(e["id"] for e in back[:6])),
                    "Re-order so the process reads left to right (feed left, products right); mark intentional wrap-around lines with role='wrap' or 'recycle'",
                    [e["id"] for e in back])
        elif back:
            rep.add("FLOW_DIRECTION", INFO, "Right-to-left streams: %s" % ", ".join(e["id"] for e in back[:8]), "Use role='recycle'/'wrap' when intentional", [e["id"] for e in back])
        into = {n["id"]: 0 for n in real_nodes}
        outof = {n["id"]: 0 for n in real_nodes}
        for e in edges:
            vec = _flow_vector(e)
            if vec is None:
                continue
            a, b = (e.get("from"), e.get("to"))
            if e.get("arrow_start") and not e.get("arrow_end"):
                a, b = b, a
            if a in outof:
                outof[a] += 1
            if b in into:
                into[b] += 1
        for n in real_nodes:
            cx = n["bbox"][0] + n["bbox"][2] / 2
            if outof[n["id"]] and not into[n["id"]] and cx > (0.62 if n["kind"] == "boundary" else o["feed_left_frac"]) * W and n["kind"] not in ("junction", "connector") and not n.get("allow_position"):
                rep.add("FEED_POSITION", WARN, "Feed '%s' (no inlet stream) is at x=%.0f%% of the width, not on the left" % (n["id"], 100 * cx / W),
                        "Place feeds on the left edge of the diagram", [n["id"]])
            if into[n["id"]] and not outof[n["id"]] and cx < o["product_left_frac"] * W and n["kind"] != "connector" and not n.get("allow_position"):
                rep.add("PRODUCT_POSITION", WARN, "Product/sink '%s' (no outlet stream) is at x=%.0f%% of the width, on the far left" % (n["id"], 100 * cx / W),
                        "Place products on the right side of the diagram", [n["id"]])
    # vertical vessels and phase-side conventions
    for n in real_nodes:
        bx, by, bw, bh = n["bbox"]
        if n["kind"] in COLUMN_KINDS and bw > 1.05 * bh:
            rep.add("VESSEL_ORIENTATION", WARN, "%s '%s' is drawn horizontal (%.0f x %.0f)" % (n["kind"].capitalize(), n["id"], bw, bh),
                    "Draw columns, absorbers and contactors as vertical (portrait) boxes", [n["id"]])
    for e in edges:
        fc = _flow_class(e.get("fluid"))
        if not fc or len(e["points"]) < 2:
            continue
        vec_start_node = nodes.get(e.get("from"))
        end_node = nodes.get(e.get("to"))
        arrow_rev = e.get("arrow_start") and not e.get("arrow_end")
        src, dst = (end_node, vec_start_node) if arrow_rev else (vec_start_node, end_node)
        srcpt, dstpt = (e["points"][-1], e["points"][0]) if arrow_rev else (e["points"][0], e["points"][-1])
        if src and src["kind"] in COLUMN_KINDS | {"vessel", "separator", "scrubber", "drum"}:
            sd = side_of(srcpt, src["bbox"])
            if fc == "gas" and sd == "b":
                rep.add("PHASE_SIDE", WARN, "Gas leaves '%s' at the bottom (edge '%s')" % (src["id"], e["id"]), "Draw gas outlets at the top of vessels", [src["id"], e["id"]], srcpt)
            if fc == "liquid" and sd == "t":
                rep.add("PHASE_SIDE", WARN, "Liquid leaves '%s' at the top (edge '%s')" % (src["id"], e["id"]), "Draw liquid outlets at the bottom of vessels", [src["id"], e["id"]], srcpt)
        if dst and dst["kind"] in COLUMN_KINDS:
            sd = side_of(dstpt, dst["bbox"])
            if fc == "gas" and sd == "t":
                rep.add("PHASE_SIDE", WARN, "Gas enters column '%s' at the top (edge '%s')" % (dst["id"], e["id"]), "Gas enters a contactor/column at the bottom", [dst["id"], e["id"]], dstpt)
            if fc == "liquid" and sd == "b":
                rep.add("PHASE_SIDE", WARN, "Liquid enters column '%s' at the bottom (edge '%s')" % (dst["id"], e["id"]), "Liquid (lean solvent) enters a contactor/column at the top", [dst["id"], e["id"]], dstpt)


def _ports(rep, s, edges, nodes, real_nodes, attach_tol, o, diag):
    sep = max(0.01 * diag, 2.0 * float(s.get("arrow_length", 0) or 0))
    for n in real_nodes:
        att = []  # (edge, role, side, point)
        for e in edges:
            if len(e["points"]) < 2:
                continue
            rev = e.get("arrow_start") and not e.get("arrow_end")
            if e.get("from") == n["id"]:
                att.append((e, "in" if rev else "out", side_of(e["points"][0], n["bbox"]), e["points"][0]))
            if e.get("to") == n["id"]:
                att.append((e, "out" if rev else "in", side_of(e["points"][-1], n["bbox"]), e["points"][-1]))
        if len(att) < 2:
            continue
        for _, _, sd, pt in att:
            x0, y0, x1, y1 = rect_of(n["bbox"])
            corner_tol = min(attach_tol, 0.12 * min(n["bbox"][2], n["bbox"][3]))
            if min(math.hypot(pt[0] - cx, pt[1] - cy) for cx, cy in ((x0, y0), (x1, y0), (x0, y1), (x1, y1))) < corner_tol and n["kind"] not in ("junction", "mixer", "splitter"):
                rep.add("PORT_AT_CORNER", WARN, "A line attaches to a corner of '%s'" % n["id"], "Attach lines to the middle region of a box side", [n["id"]], pt)
                break
        sides = {}
        for e, role, sd, pt in att:
            sides.setdefault(sd, []).append((e, role, pt))
        for sd, items in sides.items():
            if n["kind"] in ("mixer", "splitter", "junction", "block", "connector"):
                break
            items_sorted = sorted(items, key=lambda it: (it[2][1], it[2][0]))
            for (e1, _, p1), (e2, _, p2) in zip(items_sorted, items_sorted[1:]):
                if e1 is not e2 and math.hypot(p1[0] - p2[0], p1[1] - p2[1]) < sep:
                    rep.add("PORT_CROWDED", WARN, "Lines '%s' and '%s' attach on the same side of '%s' only %.1f units apart" % (e1["id"], e2["id"], n["id"], math.hypot(p1[0] - p2[0], p1[1] - p2[1])),
                            "Spread the ports along the side (at least %.1f units apart) or use different sides" % sep, [e1["id"], e2["id"], n["id"]], p1)
        if n["kind"] not in COLUMN_KINDS | {"hx", "exchanger", "cooler", "heater", "reboiler", "condenser", "mixer", "splitter", "junction", "block"}:
            in_sides = {sd for _, role, sd, _ in att if role == "in"}
            out_sides = {sd for _, role, sd, _ in att if role == "out"}
            both = in_sides & out_sides
            if both and len(sides) >= 1:
                rep.add("PORT_AMBIGUOUS", WARN, "Inlet and outlet of '%s' share the %s side - no clear inlet/outlet side" % (n["id"], "/".join(sorted(both))),
                        "Put inlets on the left (or top) and outlets on the right (or bottom) of the box", [n["id"]])
        if len(sides) == 1 and len(att) >= 3 and n["kind"] not in ("mixer", "splitter", "junction", "block"):
            rep.add("PORT_ONE_SIDE", WARN, "All %d connections of '%s' are on one side" % (len(att), n["id"]), "Distribute connections over the sides of the box", [n["id"]])


def _tags_and_streams(rep, s, real_nodes, edges, o, kind):
    need_tags = o["require_tags"] if o["require_tags"] is not None else kind in ("pfd", "pid", "pid_sketch", "flow")
    need_streams = o["require_stream_ids"] if o["require_stream_ids"] is not None else kind in ("pfd", "pid", "pid_sketch")
    if need_tags:
        missing = []
        for n in real_nodes:
            if n["kind"] in NO_TAG_KINDS or n.get("no_tag"):
                continue
            txt = " ".join([str(n.get("tag") or "")] + [str(t.get("text", "")) for t in n["texts"]])
            if not (n.get("tag") or TAG_RE.search(txt)):
                missing.append(n["id"])
        if missing:
            rep.add("MISSING_TAG", WARN, "%d equipment boxes have no tag/unit ID (%s)" % (len(missing), ", ".join(missing[:8])),
                    "Give every equipment box its tag (for example 24V0003A) in the box or directly above it", missing)
    if need_streams and edges:
        unl = [e["id"] for e in edges if not (e.get("stream") or (e.get("label") and str(e["label"].get("text", "")).strip()) or e.get("labels"))]
        nostream = [e["id"] for e in edges if not e.get("stream")]
        if len(nostream) > 0.5 * len(edges):
            rep.add("MISSING_STREAM_ID", WARN, "%d of %d streams have no stream number" % (len(nostream), len(edges)),
                    "Number every process stream (marker on the line, same number as in the stream table)", nostream[:10])
        elif unl:
            rep.add("UNLABELLED_STREAM", WARN, "%d streams carry neither a label nor a stream number" % len(unl), "Label the streams", unl[:10])
    # unconnected nodes
    used = set()
    for e in edges:
        used.add(e.get("from"))
        used.add(e.get("to"))
    for n in real_nodes:
        if n["id"] not in used and n["kind"] not in ("note", "legend", "title") and not n.get("allow_unconnected"):
            rep.add("UNCONNECTED_NODE", ERROR, "Box '%s' is not connected to any stream" % n["id"], "Connect it with a stream or remove it (use kind='note' for annotations)", [n["id"]])


def _symbols(rep, s, real_nodes, edges, o):
    by_kind = {}
    for n in real_nodes:
        st = n.get("style") or {}
        sig = (st.get("shape", ""), st.get("dash", ""))
        by_kind.setdefault(n["kind"], {}).setdefault(sig, []).append(n["id"])
    for k, d in by_kind.items():
        if k == "equipment":
            continue
        if len(d) > 1 and any(sig != ("", "") for sig in d):
            rep.add("SYMBOL_INCONSISTENT", WARN, "Equipment kind '%s' is drawn with %d different symbol styles" % (k, len(d)), "Use one symbol/fill/line style per equipment kind", [i for v in d.values() for i in v][:8])
    by_fluid = {}
    sigs = {}
    for e in edges:
        st = e.get("style") or {}
        f = e.get("fluid") or ""
        sig = (st.get("color", ""), st.get("dash", ""), st.get("width", ""))
        by_fluid.setdefault(f, set()).add(sig)
        sigs.setdefault(sig, set()).add(f)
    for f, sg in by_fluid.items():
        if f and len(sg) > 1:
            rep.add("LINE_STYLE_INCONSISTENT", WARN, "Fluid '%s' is drawn with %d different line styles" % (f, len(sg)), "One colour/dash/width per fluid", [])
    for sig, fl in sigs.items():
        if len({x for x in fl if x}) > 1 and sig[0]:
            rep.add("LINE_STYLE_AMBIGUOUS", INFO, "Fluids %s share one line style" % sorted(x for x in fl if x), "Give each fluid its own colour or dash", [])
    rep.metrics["line_styles"] = len(sigs)


def _furniture(rep, s, edges, o, kind):
    styles = {(e.get("style") or {}).get("color") for e in edges if (e.get("style") or {}).get("color")}
    legend = bool(s.get("legend"))
    need_legend = o["require_legend"] if o["require_legend"] is not None else len(styles) >= 2
    if need_legend and not legend:
        rep.add("MISSING_LEGEND", WARN, "%d line colours are used but there is no legend" % len(styles), "Add a legend that explains every colour/line style and symbol", [])
    if not s.get("title"):
        rep.add("MISSING_TITLE", WARN, "No diagram title", "Add a title (number and description) at the top left", [])
    if o["require_footer"] and not s.get("footer"):
        rep.add("MISSING_FOOTER", WARN, "No status/source footer", "Add a footer with source, basis and status (for example 'screening level - not verified')", [])


def _whitespace(rep, s, real_nodes, edges, labels, W, H, area, o):
    if not real_nodes:
        return
    ax, ay, aw, ah = area
    xs0 = [n["bbox"][0] for n in real_nodes]
    ys0 = [n["bbox"][1] for n in real_nodes]
    xs1 = [n["bbox"][0] + n["bbox"][2] for n in real_nodes]
    ys1 = [n["bbox"][1] + n["bbox"][3] for n in real_nodes]
    for e in edges:
        for p in e["points"]:
            xs0.append(p[0])
            xs1.append(p[0])
            ys0.append(p[1])
            ys1.append(p[1])
    cx0, cx1, cy0, cy1 = min(xs0), max(xs1), min(ys0), max(ys1)
    left, right, top, bottom = cx0 - ax, ax + aw - cx1, cy0 - ay, ay + ah - cy1
    rep.metrics["margins"] = [round(v, 1) for v in (left, right, top, bottom)]
    if abs(left - right) > o["off_center_frac"] * aw:
        rep.add("WHITESPACE_UNBALANCED", WARN, "Content is off-centre horizontally (left margin %.0f vs right %.0f)" % (left, right), "Re-balance columns or shift the diagram", [])
    if abs(top - bottom) > o["off_center_frac"] * ah:
        rep.add("WHITESPACE_UNBALANCED", WARN, "Content is off-centre vertically (top margin %.0f vs bottom %.0f)" % (top, bottom), "Re-balance rows or shift the diagram", [])
    # empty pockets inside the content bounding box (maximal empty rectangles that are not full-width/height bands)
    gx, gy, gw, gh = cx0, cy0, max(cx1 - cx0, 1e-6), max(cy1 - cy0, 1e-6)
    if gw < 0.45 * aw and gh < 0.45 * ah and len(real_nodes) >= 3:
        rep.add("SMALL_CONTENT", WARN, "The diagram fills only %.0f%% x %.0f%% of the drawing area" % (100 * gw / aw, 100 * gh / ah),
                "Scale the diagram up or reduce the canvas", [])
    nx, ny = 48, 36
    grid = [[False] * nx for _ in range(ny)]
    occ = [n["bbox"] for n in real_nodes] + [lb["t"]["bbox"] for lb in labels if lb["kind"] != "annotation"]
    for e in edges:
        for (p, q) in _segments(e):
            occ.append([min(p[0], q[0]) - 0.5, min(p[1], q[1]) - 0.5, abs(p[0] - q[0]) + 1.0, abs(p[1] - q[1]) + 1.0])
    for b in occ:
        x0, y0, x1, y1 = rect_of(b)
        for j in range(ny):
            cy = gy + (j + 0.5) * gh / ny
            if y0 - 0.5 * gh / ny <= cy <= y1 + 0.5 * gh / ny:
                for i in range(nx):
                    cx = gx + (i + 0.5) * gw / nx
                    if x0 - 0.5 * gw / nx <= cx <= x1 + 0.5 * gw / nx:
                        grid[j][i] = True
    best, best_rect = 0, None
    heights = [0] * nx
    for j in range(ny):
        for i in range(nx):
            heights[i] = 0 if grid[j][i] else heights[i] + 1
        stack = []
        for i in range(nx + 1):
            h = heights[i] if i < nx else 0
            start = i
            while stack and stack[-1][1] >= h:
                st, sh = stack.pop()
                width = i - st
                if width < 0.9 * nx and sh < 0.9 * ny and sh * width > best:
                    best, best_rect = sh * width, (st, j - sh + 1, width, sh)
                start = st
            stack.append((start, h))
    frac = best / float(nx * ny)
    rep.metrics["largest_empty_pocket_frac"] = round(frac, 3)
    if frac >= o["empty_pocket_frac"] and best_rect and best_rect[2] >= 0.25 * nx and best_rect[3] >= 0.25 * ny:
        st, j0, w_, h_ = best_rect
        rep.add("WHITESPACE_POCKET", WARN, "Large empty pocket (%.0f%% of the diagram extent) at x=%.0f..%.0f, y=%.0f..%.0f" % (
            100 * frac, gx + st * gw / nx, gx + (st + w_) * gw / nx, gy + j0 * gh / ny, gy + (j0 + h_) * gh / ny),
            "Spread boxes over the area (use a layered grid) so the empty region is filled or the extent shrinks", [])


# --------------------------------------------------------------------------------------------- SVG loader
_NUM = re.compile(r"[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?")


def _f(v, d=0.0):
    try:
        return float(re.sub(r"[a-zA-Z%]+$", "", str(v)))
    except (TypeError, ValueError):
        return d


def _strip(tag):
    return tag.split("}", 1)[-1]


def _pts(v):
    n = [float(x) for x in _NUM.findall(v or "")]
    return [(n[i], n[i + 1]) for i in range(0, len(n) - 1, 2)]


def _path_points(d):
    toks = re.findall(r"[MmLlHhVvZzCcSsQqTtAa]|[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?", d or "")
    pts, cmd, i, cur, start = [], None, 0, (0.0, 0.0), (0.0, 0.0)
    while i < len(toks):
        if re.match(r"[A-Za-z]", toks[i]):
            cmd = toks[i]
            i += 1
            if cmd in "Zz":
                if pts:
                    pts.append(start)
                continue
        if cmd is None:
            break
        try:
            if cmd in "ML":
                cur = (float(toks[i]), float(toks[i + 1]))
                i += 2
            elif cmd in "ml":
                cur = (cur[0] + float(toks[i]), cur[1] + float(toks[i + 1]))
                i += 2
            elif cmd == "H":
                cur = (float(toks[i]), cur[1])
                i += 1
            elif cmd == "h":
                cur = (cur[0] + float(toks[i]), cur[1])
                i += 1
            elif cmd == "V":
                cur = (cur[0], float(toks[i]))
                i += 1
            elif cmd == "v":
                cur = (cur[0], cur[1] + float(toks[i]))
                i += 1
            else:
                return pts  # curves/arcs: not supported, keep what was read
        except (ValueError, IndexError):
            break
        if cmd in "Mm" and not pts:
            start = cur
        pts.append(cur)
        if cmd == "M":
            cmd = "L"
        elif cmd == "m":
            cmd = "l"
    return pts


def load_svg(path, kind=None, name=None):
    """Parse an SVG drawing into a diagram spec (implemented in :mod:`pfd_layout_svg`)."""
    import pfd_layout_svg

    return pfd_layout_svg.load_svg(path, kind=kind, name=name)


# --------------------------------------------------------------------------------------------- raster loader
def check_raster(path, name=None, options=None):
    """Raster-only review: whitespace, clipping, resolution. Structural checks need the source geometry."""
    from PIL import Image  # noqa: WPS433

    o = dict(DEFAULT_OPTIONS)
    o.update(options or {})
    im = Image.open(path).convert("L")
    w, h = im.size
    rep = Report(name or os.path.splitext(os.path.basename(path))[0], "raster")
    px = im.load()
    step = max(1, min(w, h) // 600)
    xs0, ys0, xs1, ys1 = w, h, 0, 0
    nx, ny = 8, 6
    cell = [[0] * nx for _ in range(ny)]
    total = 0
    for y in range(0, h, step):
        for x in range(0, w, step):
            if px[x, y] < 215:
                total += 1
                xs0, xs1 = min(xs0, x), max(xs1, x)
                ys0, ys1 = min(ys0, y), max(ys1, y)
                cell[min(ny - 1, y * ny // h)][min(nx - 1, x * nx // w)] += 1
    rep.metrics.update({"width_px": w, "height_px": h})
    rep.add("RASTER_ONLY", WARN, "Only a raster image is available: overlaps, crossings, arrowheads and tags cannot be verified",
            "Re-generate the figure from its source geometry (spec/SVG) and check that, then keep the PNG only as a rendering", [])
    rep.checklist = [
        "No boxes overlap or touch; no text overlaps a box or a line",
        "Every line ends in an arrowhead at a box edge; flow reads left to right (feed left, products right)",
        "Stream labels sit beside lines (never on them); stream numbers/tags are present",
        "Lines do not run through boxes; crossings are avoided; merges have a junction dot",
        "Legend, title and status footer present; whitespace balanced; text legible at print size",
    ]
    if total == 0:
        rep.add("BLANK_IMAGE", ERROR, "Image is blank", "Re-render the figure", [])
        return rep
    if w < 900:
        rep.add("LOW_RESOLUTION", WARN, "Image is only %d px wide" % w, "Render at 200 dpi or more", [])
    if xs0 <= 1 or ys0 <= 1 or xs1 >= w - 2 or ys1 >= h - 2:
        rep.add("TEXT_OUTSIDE_CANVAS", ERROR, "Content touches the image border (likely clipped)", "Add margins or enlarge the canvas", [])
    left, right, top, bottom = xs0, w - 1 - xs1, ys0, h - 1 - ys1
    rep.metrics["margins_px"] = [left, right, top, bottom]
    if abs(left - right) > o["off_center_frac"] * w:
        rep.add("WHITESPACE_UNBALANCED", WARN, "Content is off-centre horizontally (margins %d / %d px)" % (left, right), "Re-balance the layout", [])
    empty = sum(1 for r in cell for v in r if v < 0.002 * total)
    rep.metrics["empty_cells_of_48"] = empty
    if empty > 0.34 * nx * ny:
        rep.add("WHITESPACE_POCKET", WARN, "%d of %d image regions are empty" % (empty, nx * ny), "Spread the layout over the whole area or crop the canvas", [])
    return rep


# --------------------------------------------------------------------------------------------- review driver
def review(path_or_spec, options=None, kind=None, name=None):
    """Check a spec dict, a ``.json`` spec file, an ``.svg`` drawing or a raster image."""
    if isinstance(path_or_spec, dict):
        return check_spec(path_or_spec, options, name)
    p = str(path_or_spec)
    ext = os.path.splitext(p)[1].lower()
    if ext == ".json":
        with open(p, "r", encoding="utf-8") as fh:
            return check_spec(json.load(fh), options, name or os.path.splitext(os.path.basename(p))[0])
    if ext == ".svg":
        return check_spec(load_svg(p, kind=kind, name=name), options, name)
    if ext in (".png", ".jpg", ".jpeg", ".bmp", ".gif"):
        return check_raster(p, name, options)
    raise ValueError("unsupported diagram file: %s" % p)


def review_many(paths, out_dir=None, options=None, kind=None):
    """Review many files; optionally write one JSON per file, ``summary.json`` and ``review.md``. Returns reports."""
    reports = []
    for p in paths:
        try:
            reports.append((p, review(p, options, kind)))
        except Exception as exc:  # report the failure instead of hiding the figure
            r = Report(os.path.basename(str(p)), "error")
            r.add("CHECK_ERROR", ERROR, "Checker could not read the diagram: %s" % exc, "Provide an SVG, JSON spec or raster", [])
            reports.append((p, r))
    if out_dir:
        os.makedirs(out_dir, exist_ok=True)
        summary = []
        md = ["# PFD layout review", "", "| Diagram | Status | Score | Errors | Warnings |", "|---|---|---|---|---|"]
        for p, r in reports:
            base = re.sub(r"[^A-Za-z0-9_.-]+", "_", os.path.splitext(os.path.basename(str(p)))[0])
            with open(os.path.join(out_dir, base + ".review.json"), "w", encoding="utf-8") as fh:
                json.dump(r.to_dict(), fh, indent=1)
            c = r.counts
            summary.append({"file": str(p), "status": r.status, "score": r.score, "errors": c[ERROR], "warnings": c[WARN]})
            md.append("| %s | %s | %s | %d | %d |" % (os.path.basename(str(p)), r.status, r.score, c[ERROR], c[WARN]))
        md.append("")
        for p, r in reports:
            md.append(r.to_markdown())
        with open(os.path.join(out_dir, "summary.json"), "w", encoding="utf-8") as fh:
            json.dump({"schema": "neqsim.pfd_layout_summary.v1", "diagrams": summary}, fh, indent=1)
        with open(os.path.join(out_dir, "review.md"), "w", encoding="utf-8") as fh:
            fh.write("\n".join(md))
    return reports


def worst_status(reports):
    rank = {"PASS": 0, "WARN": 1, "FAIL": 2}
    return max((r.status for _, r in reports), key=lambda s: rank[s], default="PASS")


def main(argv=None):
    ap = argparse.ArgumentParser(description="Deterministic layout check for flow diagrams (SVG, JSON spec, raster).")
    ap.add_argument("files", nargs="+", help="SVG, JSON spec, PNG/JPG files or glob patterns")
    ap.add_argument("--kind", help="pfd, bfd, pid_sketch or flow (default from file)")
    ap.add_argument("--json", help="write the first report as JSON to this path")
    ap.add_argument("--md", help="write all reports as Markdown to this path")
    ap.add_argument("--out-dir", help="write one review JSON per diagram plus summary.json and review.md")
    ap.add_argument("--strict", action="store_true", help="exit 2 on WARN as well as FAIL")
    ap.add_argument("--intended-width-mm", type=float, help="print width used for the font size check (raster/px specs)")
    ap.add_argument("--quiet", action="store_true")
    a = ap.parse_args(argv)
    import glob

    paths = []
    for f in a.files:
        hits = sorted(glob.glob(f))
        paths += hits or [f]
    opts = {}
    reports = review_many(paths, a.out_dir, opts, a.kind)
    if a.json:
        with open(a.json, "w", encoding="utf-8") as fh:
            json.dump(reports[0][1].to_dict() if len(reports) == 1 else [r.to_dict() for _, r in reports], fh, indent=1)
    if a.md:
        with open(a.md, "w", encoding="utf-8") as fh:
            fh.write("\n".join(r.to_markdown() for _, r in reports))
    if not a.quiet:
        for p, r in reports:
            c = r.counts
            print("%-5s score %5.1f  E%-2d W%-2d  %s" % (r.status, r.score, c[ERROR], c[WARN], os.path.basename(str(p))))
            if len(reports) == 1:
                for f in r.findings:
                    print("   %-5s %-22s %s\n         fix: %s" % (f["severity"], f["code"], f["message"], f["fix"]))
    worst = worst_status(reports)
    return {"PASS": 0, "WARN": 2 if a.strict else 1, "FAIL": 2}[worst]


if __name__ == "__main__":
    sys.exit(main())
