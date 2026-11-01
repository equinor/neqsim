"""SVG loader for :mod:`pfd_layout_check`: turns a drawing into the ``neqsim.pfd_layout.v1`` spec.

Understands the SVG written by ``psr_diagrams`` (``data-tag``/``data-block`` node groups, ``data-stream``/
``data-edge`` edge groups, arrowhead triangles, stream-number diamonds, boundary and on-page connectors,
``translate``/``scale`` transforms) and, generically, rect/circle/ellipse/polygon/polyline/line/path(M,L,H,V,Z)
and text elements. Text boxes are estimated from the font size (no font metrics are available).
"""
from __future__ import annotations

import math
import os
import re
import xml.etree.ElementTree as ET

from pfd_layout_check import (EQUIPMENT_KINDS, SCHEMA, STREAM_RE, _f, _path_points, _pts, _strip, estimate_text_bbox,
                              point_seg_dist)

NODE_ATTRS = ("data-tag", "data-block")
EDGE_ATTRS = ("data-stream", "data-edge")
_TF = re.compile(r"(translate|scale)\(([^)]*)\)")


def _compose(m, tf):
    """Compose transform ``m`` = (s, tx, ty) with an SVG transform string (translate/scale only)."""
    s, tx, ty = m
    for kind, args in _TF.findall(tf or ""):
        v = [float(x) for x in re.findall(r"[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?", args)]
        if kind == "translate" and v:
            tx, ty = tx + s * v[0], ty + s * (v[1] if len(v) > 1 else 0.0)
        elif kind == "scale" and v:
            s *= v[0]
    return (s, tx, ty)


def _apply(m, p):
    return (m[1] + m[0] * p[0], m[2] + m[0] * p[1])


class _Loader:
    def __init__(self, root, path, kind, name):
        vb = [float(v) for v in re.split(r"[ ,]+", root.get("viewBox").strip()) if v] if root.get("viewBox") else None
        wattr, hattr = root.get("width"), root.get("height")
        self.W, self.H = (vb[2], vb[3]) if vb else (_f(wattr, 1000.0), _f(hattr, 700.0))
        unit, intended = "px", None
        if wattr and str(wattr).strip().endswith("mm"):
            unit, intended = "mm", _f(wattr)
        elif wattr and str(wattr).strip().endswith("pt"):
            intended = _f(wattr) * 0.352778
        elif wattr:
            intended = _f(wattr) * 0.264583
        self.spec = {"schema": SCHEMA, "kind": kind or "pfd", "name": name or os.path.splitext(os.path.basename(path))[0],
                     "canvas": {"width": self.W, "height": self.H, "unit": unit, "intended_width_mm": intended or (self.W if unit == "mm" else 160.0)},
                     "nodes": [], "edges": [], "labels": [], "junctions": [], "source": os.path.basename(path), "y_axis": "down"}
        self.free_texts, self.free_rects = [], []
        self.flags = {"legend": False, "title": False, "footer": False}
        self.counter = 0
        self.markers = []

    # ---- primitives
    def text(self, el, m):
        spans = [s for s in el if _strip(s.tag) == "tspan"]
        if spans:
            parts = ["".join(s.itertext()).strip() for s in spans]
            txt = "\n".join(p for p in parts if p)
            src = spans[0] if el.get("x") is None else el
            ys = [_f(s.get("y"), _f(el.get("y"))) for s in spans if s.get("y") is not None]
            y_raw = min(ys) if ys else _f(el.get("y"))
            x_raw = _f(src.get("x"), _f(el.get("x")))
        else:
            txt = "".join(el.itertext()).strip()
            x_raw, y_raw = _f(el.get("x")), _f(el.get("y"))
        if not txt:
            return None
        fs = _f(el.get("font-size"), 3.0) * m[0]
        x, y = _apply(m, (x_raw, y_raw))
        return {"text": txt, "x": x, "y": y, "font_size": fs, "anchor": el.get("text-anchor", "start"),
                "bold": (el.get("font-weight") or "") == "bold", "fill": el.get("fill", ""),
                "layout_kind": el.get("data-layout-kind")}

    def shapes(self, el, m):
        out = []
        for sub in el.iter():
            tg = _strip(sub.tag)
            if tg == "rect":
                x0, y0 = _apply(m, (_f(sub.get("x")), _f(sub.get("y"))))
                out.append(("rect", [x0, y0, _f(sub.get("width")) * m[0], _f(sub.get("height")) * m[0]], sub, None))
            elif tg in ("circle", "ellipse"):
                rx = _f(sub.get("r") if tg == "circle" else sub.get("rx"))
                ry = rx if tg == "circle" else _f(sub.get("ry"))
                cx, cy = _apply(m, (_f(sub.get("cx")), _f(sub.get("cy"))))
                out.append(("circle", [cx - rx * m[0], cy - ry * m[0], 2 * rx * m[0], 2 * ry * m[0]], sub, None))
            elif tg in ("polygon", "polyline", "line", "path"):
                if tg == "line":
                    pts = [(_f(sub.get("x1")), _f(sub.get("y1"))), (_f(sub.get("x2")), _f(sub.get("y2")))]
                elif tg == "path":
                    pts = _path_points(sub.get("d"))
                else:
                    pts = _pts(sub.get("points"))
                pts = [_apply(m, p) for p in pts]
                if pts:
                    xs, ys = [p[0] for p in pts], [p[1] for p in pts]
                    out.append((tg, [min(xs), min(ys), max(xs) - min(xs), max(ys) - min(ys)], sub, pts))
        return out

    # ---- walk
    def walk(self, el, m, gid=None):
        for ch in list(el):
            tg = _strip(ch.tag)
            m2 = _compose(m, ch.get("transform")) if ch.get("transform") else m
            cid = (ch.get("id") or "").lower()
            if tg == "g" and any(ch.get(a) is not None for a in NODE_ATTRS):
                self.node_group(ch, m2)
            elif tg == "g" and any(ch.get(a) is not None for a in EDGE_ATTRS):
                self.edge_group(ch, m2, gid)
            elif tg == "g":
                if "legend" in cid:
                    self.flags["legend"] = True
                if not cid and any(_strip(c.tag) == "circle" for c in ch) and not any(_strip(c.tag) in ("polyline", "line", "g") for c in ch):
                    self.counter += 1
                    self.marker_group(ch, m2, "c%d" % self.counter, self.shapes(ch, m2),
                                      [t for t in (self.text(sub, m2) for sub in ch.iter() if _strip(sub.tag) == "text") if t])
                else:
                    self.walk(ch, m2, cid or gid)
            elif tg == "text":
                it = self.text(ch, m2)
                if it:
                    self.free_texts.append(it)
            elif tg == "rect":
                x0, y0 = _apply(m2, (_f(ch.get("x")), _f(ch.get("y"))))
                self.free_rects.append([x0, y0, _f(ch.get("width")) * m2[0], _f(ch.get("height")) * m2[0], ch.get("fill", ""), ch.get("stroke", "")])

    def node_group(self, g, m):
        shapes = self.shapes(g, m)
        body = [sh for sh in shapes if sh[0] in ("rect", "circle", "polygon", "path")]
        lines = [sh for sh in shapes if sh[0] in ("line", "polyline")]
        use = body or lines
        if not use:
            return
        x0 = min(sh[1][0] for sh in use)
        y0 = min(sh[1][1] for sh in use)
        x1 = max(sh[1][0] + sh[1][2] for sh in use)
        y1 = max(sh[1][1] + sh[1][3] for sh in use)
        texts = [self.text(sub, m) for sub in g.iter() if _strip(sub.tag) == "text"]
        texts = [t for t in texts if t]
        sub_kind = ""
        for t in texts:
            if t["fill"].lower() in ("#666", "#666666", "gray", "grey") and not t["bold"] and t["text"]:
                sub_kind = t["text"].lower().split()[0]
        w, h = x1 - x0, y1 - y0
        node_kind = sub_kind if sub_kind in EQUIPMENT_KINDS | {"pipe", "mixer", "splitter", "valve"} else ""
        if g.get("data-block") is not None:
            node_kind = "block"
        elif not node_kind:
            node_kind = "equipment"
        if node_kind == "splitter" or (use[0][0] == "circle" and max(w, h) <= 3.0 * m[0]) or (not body and max(w, h) <= 8.0 * m[0]):
            node_kind = "junction"
            pad = 1.2 * m[0]
            x0, y0, w, h = x0 - pad, y0 - pad, w + 2 * pad, h + 2 * pad
        n_texts = []
        for t in texts:
            tb = estimate_text_bbox(t["text"], t["x"], t["y"], t["font_size"], t["anchor"], t["bold"])
            outside = tb[1] + tb[3] <= y0 + 0.05 * h or tb[1] >= y0 + h - 0.05 * h
            ov = max(0.0, min(tb[0] + tb[2], x0 + w) - max(tb[0], x0)) * max(0.0, min(tb[1] + tb[3], y0 + h) - max(tb[1], y0)) / max(tb[2] * tb[3], 1e-9)
            kind_t = "tag_above" if tb[1] + tb[3] <= y0 + 0.2 * h and node_kind != "block" else ("subtitle" if outside and node_kind != "block" else ("outside" if ov < 0.3 else "inside"))
            # Generator metadata identifies external driver/utility text. It remains
            # owned by this node and participates in all other collision checks.
            if t.get("layout_kind") == "outside":
                kind_t = "outside"
            n_texts.append(dict(t, kind=kind_t, bbox=tb))
        first = body[0][2] if body else None
        style = {"shape": use[0][0], "fill": (first.get("fill") if first is not None else "") or "", "dash": (first.get("stroke-dasharray") if first is not None else "") or ""}
        self.counter += 1
        tag = g.get("data-tag") or g.get("data-block")
        self.spec["nodes"].append({"id": tag or "n%d" % self.counter, "tag": g.get("data-tag"), "kind": node_kind,
                                   "bbox": [x0, y0, max(w, 0.1), max(h, 0.1)], "texts": n_texts, "style": style})

    def edge_group(self, g, m, gid):
        sid = g.get("data-stream") or g.get("data-edge")
        shapes = self.shapes(g, m)
        polys = [sh for sh in shapes if sh[0] in ("polyline", "line", "path") and sh[3] and len(sh[3]) >= 2]
        texts = [t for t in (self.text(sub, m) for sub in g.iter() if _strip(sub.tag) == "text") if t]
        if not polys:
            self.marker_group(g, m, sid, shapes, texts)
            return
        heads = [sh for sh in shapes if sh[0] == "polygon" and len(sh[3]) == 3]
        for k, sh in enumerate(polys):
            pts = sh[3]
            e = {"id": "%s%s" % (sid, "" if len(polys) == 1 else "_%d" % k), "stream": sid, "points": [list(p) for p in pts], "arrow_end": False,
                 "arrow_start": False, "arrow_mid": False,
                 "open_end": bool(re.search(r"feed|prod|open", str(sid), re.I) or g.get("data-open-end") is not None),
                 "style": {"color": sh[2].get("stroke", "#111"), "dash": sh[2].get("stroke-dasharray", ""), "width": sh[2].get("stroke-width", "")}}
            for hd in heads:
                tip = _arrow_tip(hd[3], pts)
                if tip is None:
                    continue
                if math.hypot(tip[0] - pts[-1][0], tip[1] - pts[-1][1]) < 1.0 * m[0]:
                    e["arrow_end"] = True
                elif math.hypot(tip[0] - pts[0][0], tip[1] - pts[0][1]) < 1.0 * m[0]:
                    e["arrow_start"] = True
                elif any(point_seg_dist(tip, pts[i], pts[i + 1])[0] < 1.0 * m[0] for i in range(len(pts) - 1)):
                    e["arrow_mid"] = True
            if heads:
                self.spec["arrow_length"] = max(self.spec.get("arrow_length", 0.0), _extent(heads[0][3]))
            if k == 0 and texts:
                e["open_end"] = True  # a labelled stub is a boundary feed/product arrow (defined in the legend)
                bbs = [estimate_text_bbox(t["text"], t["x"], t["y"], t["font_size"], t["anchor"], t["bold"]) for t in texts]
                x0 = min(b[0] for b in bbs)
                y0 = min(b[1] for b in bbs)
                x1 = max(b[0] + b[2] for b in bbs)
                y1 = max(b[1] + b[3] for b in bbs)
                e["label"] = {"text": "\n".join(t["text"] for t in texts), "font_size": min(t["font_size"] for t in texts), "bbox": [x0, y0, x1 - x0, y1 - y0], "kind": "stream"}
            self.spec["edges"].append(e)

    def marker_group(self, g, m, sid, shapes, texts):
        """Stream-number diamond, boundary arrow or on-page connector (a group without a line)."""
        circ = [sh for sh in shapes if sh[0] == "circle"]
        polys = [sh for sh in shapes if sh[0] == "polygon" and sh[3]]
        if not circ and not polys and texts:
            for t in texts:
                self.free_texts.append(dict(t, stub_of=sid))
            return
        if circ:
            c = circ[0]
            self.counter += 1
            n_texts = []
            for t in texts:
                n_texts.append(dict(t, kind="inside", bbox=estimate_text_bbox(t["text"], t["x"], t["y"], t["font_size"], t["anchor"], t["bold"])))
            inner = [t for t in n_texts if abs(t["x"] - (c[1][0] + c[1][2] / 2)) < c[1][2] and c[1][1] <= t["y"] <= c[1][1] + c[1][3]]
            outer = [t for t in n_texts if t not in inner]
            self.spec["nodes"].append({"id": "conn_%s" % sid, "kind": "connector", "bbox": c[1], "texts": inner, "style": {"shape": "circle"}})
            for t in outer:
                self.spec["labels"].append({"text": t["text"], "x": t["x"], "y": t["y"], "font_size": t["font_size"], "anchor": t["anchor"], "bold": t["bold"],
                                            "bbox": t["bbox"], "kind": "label", "owner_edge": None})
            return
        if not polys:
            return
        pg = max(polys, key=lambda sh: len(sh[3]))
        if len(pg[3]) == 4 and len(texts) == 1 and STREAM_RE.match(texts[0]["text"]):
            t = texts[0]
            self.spec["labels"].append({"text": t["text"], "x": t["x"], "y": t["y"], "font_size": t["font_size"], "anchor": t["anchor"], "bold": t["bold"],
                                        "bbox": pg[1], "kind": "streamno", "owner_edge": sid})
            return
        self.counter += 1
        n_texts = [dict(t, kind="inside", bbox=estimate_text_bbox(t["text"], t["x"], t["y"], t["font_size"], t["anchor"], t["bold"])) for t in texts]
        self.spec["nodes"].append({"id": "bnd_%s_%d" % (sid, self.counter), "kind": "boundary", "bbox": pg[1], "texts": n_texts, "style": {"shape": "polygon"},
                                   "stream": sid})

    # ---- finish
    def finish(self):
        W, H = self.W, self.H
        spec = self.spec
        frames = [r for r in self.free_rects if r[2] >= 0.9 * W and r[3] >= 0.9 * H and r[5] not in ("", "none")]
        blocks = [r for r in self.free_rects if not (r[2] >= 0.9 * W and r[3] >= 0.9 * H) and 0.004 * W * H < r[2] * r[3] < 0.6 * W * H and r[5] not in ("none", "")]
        inner = sorted(frames, key=lambda r: r[2] * r[3])[0] if frames else None
        ay0, ax0, aw = (inner[1] if inner else 0.0), (inner[0] if inner else 0.0), (inner[2] if inner else W)
        low = [b for b in blocks if b[1] > 0.55 * H]
        ay1 = min([b[1] for b in low] or [(inner[1] + inner[3]) if inner else H])
        spec["drawing_area"] = [ax0, ay0, aw, max(10.0, ay1 - ay0)]
        area = spec["drawing_area"]
        if low:
            x0 = min(b[0] for b in low)
            y0 = min(b[1] for b in low)
            spec["title_block"] = {"bbox": [x0, y0, max(b[0] + b[2] for b in low) - x0, max(b[1] + b[3] for b in low) - y0]}
        stubs = {t["stub_of"] for t in self.free_texts if t.get("stub_of")}
        for e in spec["edges"]:
            if e.get("stream") in stubs:
                e["open_end"] = True
        for t in self.free_texts:
            bb = estimate_text_bbox(t["text"], t["x"], t["y"], t["font_size"], t["anchor"], t["bold"])
            in_area = area[0] <= bb[0] and bb[0] + bb[2] <= area[0] + area[2] and area[1] <= bb[1] and bb[1] + bb[3] <= area[1] + area[3]
            if re.search(r"^\s*legend\s*$", t["text"], re.I):
                self.flags["legend"] = True
            if not in_area and re.search(r"draft|screening|status|not for|unverified|revision", t["text"], re.I):
                self.flags["footer"] = True
            if t["bold"] and t["y"] < area[1] + 0.12 * area[3] and t["font_size"] >= 3.0:
                self.flags["title"] = True
            if re.search(r"^title$", t["text"], re.I):
                self.flags["title"] = True
            kind_t = "streamno" if (in_area and STREAM_RE.match(t["text"])) else ("label" if in_area else "annotation")
            spec["labels"].append({"text": t["text"], "x": t["x"], "y": t["y"], "font_size": t["font_size"], "anchor": t["anchor"], "bold": t["bold"], "kind": kind_t, "bbox": bb})
        spec["title"], spec["legend"], spec["footer"] = bool(self.flags["title"]), bool(self.flags["legend"]), bool(self.flags["footer"])
        # unattached line ends with a text label next to them are boundary feed/product stubs
        for e in spec["edges"]:
            if e.get("open_end") or len(e["points"]) < 2:
                continue
            for pt in (e["points"][0], e["points"][-1]):
                if any(not STREAM_RE.match(t["text"]) and chk_dist(pt, t["bbox"]) <= 12.0 * (self.W / 420.0) for t in spec["labels"]):
                    e["open_end"] = True
        for n in spec["nodes"]:
            for t in n["texts"]:
                t["est"] = True
        for e in spec["edges"]:
            if e.get("label"):
                e["label"]["est"] = True
        for t in spec["labels"]:
            t["est"] = True
        return spec


def chk_dist(pt, bbox):
    """Distance from a point to a text box."""
    from pfd_layout_check import point_rect_boundary_dist

    return point_rect_boundary_dist(pt, bbox)


def _extent(pts):
    xs, ys = [p[0] for p in pts], [p[1] for p in pts]
    return max(max(xs) - min(xs), max(ys) - min(ys))


def _arrow_tip(head, line_pts):
    """Tip of an arrowhead triangle: the vertex nearest a polyline end, else the apex of a mid-line triangle."""
    best, bd = None, 1e9
    for p in head:
        for q in (line_pts[0], line_pts[-1]):
            d = math.hypot(p[0] - q[0], p[1] - q[1])
            if d < bd:
                best, bd = p, d
    if best is not None and bd <= 0.5:
        return best
    if len(head) >= 3:
        return max(head, key=lambda p: sum(math.hypot(p[0] - q[0], p[1] - q[1]) for q in head))
    return None


def load_svg(path, kind=None, name=None):
    """Parse an SVG drawing into a diagram spec."""
    root = ET.parse(path).getroot()
    ld = _Loader(root, path, kind, name)
    ld.walk(root, (1.0, 0.0, 0.0))
    return ld.finish()
