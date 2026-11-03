"""Deterministic PFD/BFD builder for matplotlib generators, checked by :mod:`pfd_layout_check`.

Nodes sit on a grid chosen by the author (``PfdDiagram.grid``), lines leave and enter boxes on named port
sides (``l r t b``) and are routed orthogonally, stream-number bubbles and condition labels are placed by a
collision-aware search (never on a line, box or other label), the legend/title/footer are drawn from the used
fluids, and the exact geometry is exported as a ``neqsim.pfd_layout.v1`` spec so the checker works on real
text extents. ``render`` writes the PNG (and optional SVG), the ``*.layout.json`` spec and the review.

Typical use::

    d = PfdDiagram("PFD 6 - TEG dehydration", width_in=7.0, height_in=5.2, footer="MODEL values ...")
    g = d.grid(cols=4, rows=3)
    d.node("c", "24V0003A\\ncontactor", *g(1, 0), kind="column", tag="24V0003A")
    d.node("g", "Wet gas", *g(0, 1), kind="boundary")
    d.edge("g", "r", "c", "l", fluid="gas", stream="1", label="108 bara / 30 C")
    report = d.render("figures/fig_pfd.png")   # raises nothing; inspect report.status

Coordinates: canvas is 100 units wide and ``100 * height_in / width_in`` high (square units), x to the right and
y DOWN; ``(x, y)`` of a node is its centre.
"""
from __future__ import annotations

import json
import math
import os

import pfd_layout_check as chk

FLUID_COLORS = {"gas": "#C0392B", "oil": "#7D5A1E", "water": "#2471A3", "glycol": "#7D3C98", "liquid": "#2E86C1",
                "util": "#707B7C", "mixed": "#1E8449", "other": "#333333"}
FLUID_LABELS = {"gas": "gas", "oil": "oil / liquid HC", "water": "water", "glycol": "glycol (TEG/MEG)", "liquid": "liquid",
                "util": "utility / other", "mixed": "multiphase", "other": "other"}
NAVY = "#1F3A5F"
SIDE_VEC = {"l": (-1.0, 0.0), "r": (1.0, 0.0), "t": (0.0, -1.0), "b": (0.0, 1.0)}


class PfdDiagram:
    """Collects nodes and edges, routes lines, places labels, draws and checks the diagram."""

    def __init__(self, title, width_in=7.0, height_in=5.0, kind="pfd", footer=None, subtitle=None, font_pt=6.4, label_pt=6.0,
                 intended_width_mm=165.0, line_pt=1.3, top_band=9.0, bottom_band=9.0, flow="lr", options=None):
        self.title, self.subtitle, self.footer, self.kind = title, subtitle, footer, kind
        self.width_in, self.height_in = float(width_in), float(height_in)
        self.W = 100.0
        self.H = 100.0 * self.height_in / self.width_in
        self.font_pt, self.label_pt, self.line_pt = font_pt, label_pt, line_pt
        self.intended_width_mm = intended_width_mm
        self.top, self.bottom_band = top_band + (3.0 if subtitle else 0.0), bottom_band
        self.flow = flow
        self.bub_r = 1.7
        self.options = options or {}
        self.nodes, self.edges, self.notes, self.junctions = [], [], [], []
        self._ids = {}
        self.waivers = []
        self._ax = self._fig = self._rend = None

    # ---- geometry helpers -------------------------------------------------------------------
    @property
    def content_bottom(self):
        """Lowest y available to nodes (above the legend and footer band)."""
        return self.H - self.bottom_band

    def grid(self, cols, rows, x0=3.0, x1=None, y0=None, y1=None):
        """Return ``cell(col, row, colspan=1, rowspan=1) -> (x, y)`` centres of an even grid over the content area."""
        x1 = self.W - 3.0 if x1 is None else x1
        y0 = self.top + 1.0 if y0 is None else y0
        y1 = self.content_bottom - 1.0 if y1 is None else y1
        cw, ch = (x1 - x0) / cols, (y1 - y0) / rows

        def cell(c, r, cs=1, rs=1):
            return (x0 + (c + cs / 2.0) * cw, y0 + (r + rs / 2.0) * ch)

        cell.cell_w, cell.cell_h = cw, ch
        return cell

    def _fs_units(self, pt):
        return pt / 72.0 / self.width_in * self.W

    def _setup_measure(self):
        import matplotlib

        matplotlib.use("Agg")
        import matplotlib.pyplot as plt

        if self._fig is None:
            self._fig = plt.figure(figsize=(self.width_in, self.height_in), dpi=200)
            self._ax = self._fig.add_axes([0, 0, 1, 1])
            self._ax.set_xlim(0, self.W)
            self._ax.set_ylim(self.H, 0)
            self._ax.axis("off")
            self._rend = self._fig.canvas.get_renderer()

    def text_size(self, text, pt, bold=False):
        """Width and height of a (multi-line) text in canvas units (measured with matplotlib)."""
        self._setup_measure()
        t = self._ax.text(0, 0, text, fontsize=pt, fontweight="bold" if bold else "normal", linespacing=1.15)
        bb = t.get_window_extent(self._rend)
        t.remove()
        inv = self._ax.transData.inverted()
        (x0, y0), (x1, y1) = inv.transform((bb.x0, bb.y0)), inv.transform((bb.x1, bb.y1))
        return abs(x1 - x0), abs(y1 - y0)

    # ---- building ---------------------------------------------------------------------------
    def node(self, nid, text, x, y, w=None, h=None, kind="vessel", tag=None, fill=None, pt=None, tag_text=None, dashed=None, role=None):
        """Add a node centred at ``(x, y)``. ``w``/``h`` default to the text size plus padding.

        kinds: vessel, column (vertical), hx, pump (circle), compressor, tank, valve, boundary (dashed feed/product), note.
        ``tag`` is the equipment tag (checked); ``tag_text`` draws the tag above the box for pumps/valves.
        """
        pt = pt or self.font_pt
        if kind in ("pump", "valve") and text and not tag_text:
            tag_text, text = text, ""
        tw, th = self.text_size(text, pt) if text else (0.0, 0.0)
        pad = 1.8
        if kind == "pump":
            d = max(w or 0, h or 0, 7.0)
            w = h = d
        elif kind == "valve":
            w, h = w or 6.0, h or 5.0
        else:
            w = w or tw + 2 * pad
            h = h or th + 2 * pad
            if kind == "column":
                h = max(h, w * 1.5)
        if nid in self._ids:
            raise ValueError("duplicate node id %s" % nid)
        n = {"id": nid, "text": text, "x": float(x), "y": float(y), "w": float(w), "h": float(h), "kind": kind, "tag": tag, "fill": fill, "pt": pt,
             "tag_text": tag_text, "dashed": dashed if dashed is not None else kind == "boundary", "role": role}
        self._ids[nid] = n
        self.nodes.append(n)
        return n

    def junction(self, jid, x, y):
        """Add a junction dot (tee) where streams split or merge."""
        n = {"id": jid, "text": "", "x": float(x), "y": float(y), "w": 1.6, "h": 1.6, "kind": "junction", "tag": None, "fill": None, "pt": self.font_pt,
             "tag_text": None, "dashed": False, "role": None}
        self._ids[jid] = n
        self.nodes.append(n)
        return n

    def _anchor(self, nid, side, off=0.0):
        n = self._ids[nid]
        x, y, w, h = n["x"], n["y"], n["w"], n["h"]
        if n["kind"] == "junction":
            return (x, y)
        if side == "l":
            return (x - w / 2, y + off)
        if side == "r":
            return (x + w / 2, y + off)
        if side == "t":
            return (x + off, y - h / 2)
        return (x + off, y + h / 2)

    def edge(self, a, sa, b, sb, fluid="gas", label=None, stream=None, via=None, offa=0.0, offb=0.0, mid=None, role="", label_side="auto",
             label_seg=None, eid=None, color=None, dash=None):
        """Add a stream from node ``a`` side ``sa`` to node ``b`` side ``sb``.

        ``via``: ``None`` (auto orthogonal route), ``"hv"``/``"vh"`` (L-shape) or a list of absolute waypoints.
        ``mid``: x (for l/r to l/r) or y (for t/b to t/b) of the middle leg of an auto Z-route.
        ``role``: ``wrap``/``recycle`` for intentional backward lines.
        """
        p0, p1 = self._anchor(a, sa, offa), self._anchor(b, sb, offb)
        horiz_a, horiz_b = sa in "lr", sb in "lr"
        if via is None:
            if horiz_a and horiz_b:
                if abs(p0[1] - p1[1]) < 1e-6:
                    mids = []
                else:
                    mx = mid if mid is not None else (p0[0] + p1[0]) / 2
                    mids = [(mx, p0[1]), (mx, p1[1])]
            elif not horiz_a and not horiz_b:
                if abs(p0[0] - p1[0]) < 1e-6:
                    mids = []
                else:
                    my = mid if mid is not None else (p0[1] + p1[1]) / 2
                    mids = [(p0[0], my), (p1[0], my)]
            elif horiz_a:
                mids = [(p1[0], p0[1])]
            else:
                mids = [(p0[0], p1[1])]
        elif via == "hv":
            mids = [(p1[0], p0[1])]
        elif via == "vh":
            mids = [(p0[0], p1[1])]
        else:
            mids = [tuple(p) for p in via]
        pts = chk._dedupe([p0] + mids + [p1])
        e = {"id": eid or "e%d" % (len(self.edges) + 1), "a": a, "b": b, "sa": sa, "sb": sb, "fluid": fluid, "label": label, "stream": stream, "points": pts,
             "role": role, "label_side": label_side, "label_seg": label_seg, "color": color or FLUID_COLORS.get(fluid, FLUID_COLORS["other"]), "dash": dash}
        self.edges.append(e)
        return e

    def note(self, x, y, text, pt=None, anchor="start", owner=None):
        """Free text note (kind ``note``); keep it away from lines and boxes."""
        self.notes.append({"x": x, "y": y, "text": text, "pt": pt or self.label_pt, "anchor": anchor})

    def waive(self, code, reason):
        """Record a justified waiver for a checker code (listed in the review)."""
        self.waivers.append({"code": code, "reason": reason})

    # ---- label placement ----------------------------------------------------------------------
    def _node_rects(self):
        out = []
        for n in self.nodes:
            if n["kind"] == "junction":
                continue
            out.append([n["x"] - n["w"] / 2, n["y"] - n["h"] / 2, n["w"], n["h"]])
            if n.get("tag_text") and n["kind"] in ("pump", "valve"):
                tw, th = self.text_size(n["tag_text"], n["pt"], True)
                out.append([n["x"] - tw / 2, n["y"] - n["h"] / 2 - th - 0.8, tw, th])
        return out

    def _all_segments(self):
        return [(p, q) for e in self.edges for (p, q) in zip(e["points"], e["points"][1:])]

    def _place_labels(self):
        placed = []  # rectangles of bubbles and labels already placed
        obstacles = self._node_rects()
        for e in self.edges:
            e["bubble"] = None
            e["label_box"] = None
        segs = self._all_segments()
        bub_r = self.bub_r
        # stream-number bubbles first: on the longest free segment, at the middle
        for e in self.edges:
            if not e["stream"]:
                continue
            best = None
            for i, (p, q) in enumerate(zip(e["points"], e["points"][1:])):
                length = math.hypot(q[0] - p[0], q[1] - p[1])
                for t in (0.5, 0.4, 0.6, 0.3, 0.7):
                    c = (p[0] + (q[0] - p[0]) * t, p[1] + (q[1] - p[1]) * t)
                    rect = [c[0] - bub_r, c[1] - bub_r, 2 * bub_r, 2 * bub_r]
                    penalty = sum(1 for r in obstacles + placed if chk.overlap_area(rect, r) > 0)
                    for (p2, q2) in segs:
                        if (p2, q2) == (p, q):
                            continue
                        if chk.seg_inside_rect_len(p2, q2, chk.inflate(rect, 0.3)) > 0:
                            penalty += 1
                    if length < 2 * bub_r + 2.5:
                        penalty += 2
                    cand = (penalty, -length, abs(t - 0.5), rect, c)
                    if best is None or cand[:3] < best[:3]:
                        best = cand
            if best:
                e["bubble"] = {"rect": best[3], "c": best[4]}
                placed.append(best[3])
        # condition labels
        for e in sorted(self.edges, key=lambda ed: -len(ed["label"] or "")):
            if not e["label"]:
                continue
            tw, th = self.text_size(e["label"], self.label_pt)
            gap = 0.9 + (2.0 if e.get("bubble") else 0.0)
            best = None
            seg_ids = range(len(e["points"]) - 1) if e["label_seg"] is None else [e["label_seg"]]
            for i in seg_ids:
                p, q = e["points"][i], e["points"][i + 1]
                length = math.hypot(q[0] - p[0], q[1] - p[1])
                horizontal = abs(q[0] - p[0]) >= abs(q[1] - p[1])
                for t in (0.5, 0.35, 0.65, 0.25, 0.75, 0.15, 0.85):
                    c = (p[0] + (q[0] - p[0]) * t, p[1] + (q[1] - p[1]) * t)
                    if horizontal:
                        sides = ("above", "below") if e["label_side"] == "auto" else (e["label_side"],)
                        cands = {"above": [c[0] - tw / 2, c[1] - gap - th, tw, th], "below": [c[0] - tw / 2, c[1] + gap, tw, th]}
                    else:
                        g2 = 0.9 + (2.6 if e.get("bubble") and t > 0.4 and t < 0.6 else 0.0)
                        sides = ("right", "left") if e["label_side"] == "auto" else (e["label_side"],)
                        cands = {"right": [c[0] + g2, c[1] - th / 2, tw, th], "left": [c[0] - g2 - tw, c[1] - th / 2, tw, th]}
                    for sd in sides:
                        if sd not in cands:
                            continue
                        rect = cands[sd]
                        penalty = 0.0
                        r = chk.rect_of(rect)
                        if r[0] < 1.0 or r[2] > self.W - 1.0 or r[1] < self.top - 1.0 or r[3] > self.content_bottom + 2.0:
                            penalty += 5
                        penalty += 3 * sum(1 for o in obstacles + placed if chk.overlap_area(chk.inflate(rect, 0.4), o) > 0)
                        penalty += 3 * sum(1 for (p2, q2) in segs if chk.seg_inside_rect_len(p2, q2, chk.inflate(rect, 0.5)) > 0)
                        shortfall = max(0.0, (tw if horizontal else th) - length)
                        cand = (penalty + shortfall * 0.2, 0 if length >= (tw if horizontal else th) else 1, abs(t - 0.5), sd == "below", rect)
                        if best is None or cand[:4] < best[:4]:
                            best = cand
            if best:
                e["label_box"] = best[4]
                placed.append(best[4])

    # ---- export -------------------------------------------------------------------------------
    def spec(self):
        """Return the ``neqsim.pfd_layout.v1`` spec of the laid-out diagram (call after ``_place_labels``)."""
        sc = self._fs_units
        nodes = []
        for n in self.nodes:
            nd = {"id": n["id"], "kind": n["kind"], "tag": n["tag"],
                  "bbox": [n["x"] - n["w"] / 2, n["y"] - n["h"] / 2, n["w"], n["h"]], "texts": [],
                  "style": {"shape": n["kind"], "fill": n["fill"] or "", "dash": "dashed" if n["dashed"] else ""}}
            if n["kind"] == "boundary":
                nd["kind"] = "boundary"
            if n["role"] == "note":
                nd["kind"] = "note"
            if n["text"] and n["kind"] != "pump":
                tw, th = self.text_size(n["text"], n["pt"])
                nd["texts"].append({"text": n["text"], "x": n["x"], "y": n["y"], "font_size": sc(n["pt"]), "bbox": [n["x"] - tw / 2, n["y"] - th / 2, tw, th], "kind": "inside"})
            elif n["text"]:
                tw, th = self.text_size(n["text"], n["pt"])
                nd["texts"].append({"text": n["text"], "x": n["x"], "y": n["y"] + n["h"] / 2 + th / 2 + 0.8, "font_size": sc(n["pt"]),
                                    "bbox": [n["x"] - tw / 2, n["y"] + n["h"] / 2 + 0.8, tw, th], "kind": "subtitle"})
            if n.get("tag_text") and n["kind"] in ("pump", "valve"):
                tw, th = self.text_size(n["tag_text"], n["pt"], True)
                nd["texts"].append({"text": n["tag_text"], "x": n["x"], "y": n["y"] - n["h"] / 2 - th / 2 - 0.8, "font_size": sc(n["pt"]),
                                    "bbox": [n["x"] - tw / 2, n["y"] - n["h"] / 2 - th - 0.8, tw, th], "kind": "tag_above"})
            nodes.append(nd)
        edges, labels = [], []
        for e in self.edges:
            ed = {"id": e["id"], "from": e["a"], "to": e["b"], "points": [list(p) for p in e["points"]], "arrow_end": True, "arrow_start": False,
                  "fluid": e["fluid"], "stream": e["stream"], "role": e["role"],
                  "style": {"color": e["color"], "dash": e["dash"] or "", "width": str(self.line_pt)}}
            if e.get("label_box"):
                bb = e["label_box"]
                ed["label"] = {"text": e["label"], "x": bb[0], "y": bb[1], "font_size": sc(self.label_pt), "bbox": bb, "kind": "stream"}
            elif e["label"]:
                ed["label"] = {"text": e["label"], "x": 0, "y": 0, "font_size": sc(self.label_pt), "bbox": [-50, -50, 1, 1], "kind": "stream"}
            if e.get("bubble"):
                labels.append({"text": str(e["stream"]), "x": e["bubble"]["c"][0], "y": e["bubble"]["c"][1], "font_size": sc(self.label_pt - 0.4),
                               "bbox": e["bubble"]["rect"], "kind": "streamno", "owner_edge": e["id"]})
            elif e["stream"]:
                labels.append({"text": str(e["stream"]), "x": -50, "y": -50, "font_size": sc(self.label_pt), "bbox": [-50, -50, 1, 1], "kind": "streamno", "owner_edge": e["id"]})
            edges.append(ed)
        for nt in self.notes:
            tw, th = self.text_size(nt["text"], nt["pt"])
            x0 = nt["x"] if nt["anchor"] == "start" else nt["x"] - tw / 2
            labels.append({"text": nt["text"], "x": nt["x"], "y": nt["y"], "font_size": sc(nt["pt"]), "bbox": [x0, nt["y"], tw, th], "kind": "note"})
        tw, th = self.text_size(self.title, self.font_pt + 2.0, True)
        labels.append({"text": self.title, "x": 1.5, "y": 2.2, "font_size": sc(self.font_pt + 2.0), "bbox": [1.5, 2.2 - th / 2, tw, th], "kind": "annotation"})
        if self.subtitle:
            tw, th = self.text_size(self.subtitle, self.label_pt)
            labels.append({"text": self.subtitle, "x": 1.5, "y": 5.6, "font_size": sc(self.label_pt), "bbox": [1.5, 5.6 - th / 2, tw, th], "kind": "annotation"})
        if self.footer:
            ftxt = "\n".join(self._footer_lines())
            tw, th = self.text_size(ftxt, self.label_pt - 0.4)
            labels.append({"text": ftxt, "x": 1.5, "y": self.H - 0.8 - th, "font_size": sc(self.label_pt - 0.4), "bbox": [1.5, self.H - 0.8 - th, tw, th], "kind": "annotation"})
        legend = self._legend_items()
        spec = {"schema": chk.SCHEMA, "kind": self.kind, "name": self._name, "canvas": {"width": self.W, "height": self.H, "unit": "u", "intended_width_mm": self.intended_width_mm},
                "nodes": nodes, "edges": edges, "labels": labels, "junctions": [[n["x"], n["y"]] for n in self.nodes if n["kind"] == "junction"],
                "title": bool(self.title), "legend": legend["bbox"] and {"bbox": legend["bbox"]}, "footer": bool(self.footer), "flow_direction": self.flow,
                "drawing_area": [0, self.top, self.W, self.content_bottom - self.top], "waivers": self.waivers, "options": self.options,
                "y_axis": "down", "arrow_length": 1.6}
        return spec

    def _footer_lines(self):
        """Footer text wrapped to the canvas width."""
        if not self.footer:
            return []
        pt = self.label_pt - 0.4
        words, lines, cur = str(self.footer).split(), [], ""
        for w in words:
            trial = (cur + " " + w).strip()
            if cur and self.text_size(trial, pt)[0] > self.W - 4.0:
                lines.append(cur)
                cur = w
            else:
                cur = trial
        if cur:
            lines.append(cur)
        return lines

    def _footer_height(self):
        lines = self._footer_lines()
        return self.text_size("\n".join(lines), self.label_pt - 0.4)[1] if lines else 0.0

    def _legend_items(self):
        fluids = []
        for e in self.edges:
            if e["fluid"] not in fluids:
                fluids.append(e["fluid"])
        if not fluids:
            return {"items": [], "bbox": None}
        y = self.H - 3.2 - (self._footer_height() + 0.8 if self.footer else 0.0)
        x = 1.5
        items = []
        for f in fluids:
            lab = FLUID_LABELS.get(f, f)
            tw, th = self.text_size(lab, self.label_pt)
            items.append((f, x, y, lab, tw, th))
            x += 5.5 + 1.2 + tw + 3.5
        bbox = [1.0, y - 2.0, max(8.0, x - 1.0), 4.0]
        return {"items": items, "bbox": bbox}

    # ---- rendering ------------------------------------------------------------------------------
    def render(self, path, svg=True, spec_path=True, review=True, strict_log=True):
        """Draw to ``path`` (PNG), write the SVG, ``*.layout.json`` and review next to it; return the checker report."""
        import matplotlib

        matplotlib.use("Agg")
        import matplotlib.pyplot as plt
        from matplotlib.patches import Circle, FancyArrowPatch, FancyBboxPatch, Polygon, Rectangle

        self._name = os.path.splitext(os.path.basename(path))[0]
        self._setup_measure()
        self._place_labels()
        fig, ax = self._fig, self._ax
        ax.clear()
        ax.set_xlim(0, self.W)
        ax.set_ylim(self.H, 0)
        ax.axis("off")
        sc = self._fs_units
        ax.text(1.5, 2.2, self.title, fontsize=self.font_pt + 2.0, color=NAVY, fontweight="bold", ha="left", va="center")
        if self.subtitle:
            ax.text(1.5, 5.6, self.subtitle, fontsize=self.label_pt, color="#333333", ha="left", va="center")
        for n in self.nodes:
            x, y, w, h, k = n["x"], n["y"], n["w"], n["h"], n["kind"]
            fc = n["fill"] or {"vessel": "#EAF0F7", "column": "#EAF0F7", "hx": "#E8F6F3", "pump": "#FEF9E7", "compressor": "#FBE9E7", "tank": "#F4ECF7",
                               "valve": "white", "boundary": "white"}.get(k, "#EAF0F7")
            if k in ("vessel", "column", "block"):
                ax.add_patch(FancyBboxPatch((x - w / 2, y - h / 2), w, h, boxstyle="round,pad=0,rounding_size=1.6", fc=fc, ec=NAVY, lw=1.0))
            elif k == "hx":
                ax.add_patch(Rectangle((x - w / 2, y - h / 2), w, h, fc=fc, ec=NAVY, lw=1.0))
                ax.plot([x - w / 2 + 1.2, x - w / 2 + 1.2, x + w / 2 - 1.2, x + w / 2 - 1.2][:2], [y - h / 2, y + h / 2], color=NAVY, lw=0.5)
            elif k == "pump":
                ax.add_patch(Circle((x, y), w / 2, fc=fc, ec=NAVY, lw=1.0))
                ax.plot([x - w / 2 * 0.5, x + w / 2 * 0.7, x - w / 2 * 0.5], [y - w / 2 * 0.6, y, y + w / 2 * 0.6], color=NAVY, lw=0.8)
            elif k == "compressor":
                ax.add_patch(Polygon([(x - w / 2, y - h / 2), (x - w / 2, y + h / 2), (x + w / 2, y + h * 0.28), (x + w / 2, y - h * 0.28)], fc=fc, ec=NAVY, lw=1.0))
            elif k == "tank":
                ax.add_patch(Rectangle((x - w / 2, y - h / 2), w, h, fc=fc, ec=NAVY, lw=1.0, hatch="///"))
            elif k == "valve":
                ax.add_patch(Polygon([(x - w / 2, y - h / 2), (x + w / 2, y + h / 2), (x + w / 2, y - h / 2), (x - w / 2, y + h / 2)], fc="white", ec=NAVY, lw=1.0))
            elif k == "junction":
                ax.add_patch(Circle((x, y), 0.8, fc="#222222", ec="#222222"))
            elif k == "boundary":
                ax.add_patch(Rectangle((x - w / 2, y - h / 2), w, h, fc="white", ec="#666666", lw=0.9, ls="--"))
            else:
                ax.add_patch(Rectangle((x - w / 2, y - h / 2), w, h, fc=fc, ec=NAVY, lw=1.0))
            if n["text"] and k != "pump":
                ax.text(x, y, n["text"], fontsize=n["pt"], ha="center", va="center", color="#111111", linespacing=1.15,
                        bbox=dict(fc="white", ec="none", pad=0.8, alpha=0.9) if k == "tank" else None)
            elif n["text"]:
                tw, th = self.text_size(n["text"], n["pt"])
                ax.text(x, y + h / 2 + 0.8, n["text"], fontsize=n["pt"], ha="center", va="top", color="#111111", linespacing=1.15)
            if n.get("tag_text") and k in ("pump", "valve"):
                ax.text(x, y - h / 2 - 0.8, n["tag_text"], fontsize=n["pt"], ha="center", va="bottom", color="#111111", fontweight="bold")
        for e in self.edges:
            pts = e["points"]
            for p, q in zip(pts[:-2], pts[1:-1]):
                ax.plot([p[0], q[0]], [p[1], q[1]], color=e["color"], lw=self.line_pt, solid_capstyle="butt", ls=e["dash"] or "-")
            ax.add_patch(FancyArrowPatch(pts[-2], pts[-1], arrowstyle="-|>", mutation_scale=7, color=e["color"], lw=self.line_pt, shrinkA=0, shrinkB=0))
            if e.get("bubble"):
                c = e["bubble"]["c"]
                ax.add_patch(Circle(c, self.bub_r, fc="white", ec=e["color"], lw=0.9, zorder=5))
                ax.text(c[0], c[1], str(e["stream"]), fontsize=self.label_pt - 0.4, ha="center", va="center", color="#111111", zorder=6)
            if e.get("label_box"):
                bb = e["label_box"]
                ax.text(bb[0] + bb[2] / 2, bb[1] + bb[3] / 2, e["label"], fontsize=self.label_pt, ha="center", va="center", color=e["color"], linespacing=1.15)
        for nt in self.notes:
            ax.text(nt["x"], nt["y"], nt["text"], fontsize=nt["pt"], ha=("left" if nt["anchor"] == "start" else "center"), va="top", color="#333333", linespacing=1.15)
        leg = self._legend_items()
        for f, x, y, lab, tw, th in leg["items"]:
            ax.plot([x, x + 5.5], [y, y], color=FLUID_COLORS.get(f, "#333333"), lw=self.line_pt + 0.3)
            ax.text(x + 6.7, y, lab, fontsize=self.label_pt, va="center", ha="left")
        if self.footer:
            ax.text(1.5, self.H - 0.8, "\n".join(self._footer_lines()), fontsize=self.label_pt - 0.4, color="#555555", va="bottom", ha="left", linespacing=1.15)
        os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
        fig.savefig(path, dpi=200)
        if svg:
            fig.savefig(os.path.splitext(path)[0] + ".svg")
        import matplotlib.pyplot as plt  # noqa: F811

        plt.close(fig)
        self._fig = self._ax = None
        spec = self.spec()
        base = os.path.splitext(path)[0]
        if spec_path:
            with open(base + ".layout.json", "w", encoding="utf-8") as fh:
                json.dump(spec, fh, indent=1)
        rep = chk.check_spec(spec, name=self._name)
        if review:
            with open(base + ".review.json", "w", encoding="utf-8") as fh:
                json.dump(rep.to_dict(), fh, indent=1)
        return rep
