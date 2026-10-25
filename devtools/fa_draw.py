"""Deterministic vector drawing helpers for the flow-assurance drawing package (SVG, A3 landscape).

Builds on the shared sheet primitives of :mod:`psr_diagrams` (``kit``): frame, title block, revision table, notes box and
SVG-to-PDF conversion. Adds a ``Sheet`` that records every annotation (text, position, size, data reference) so that the
layout can be checked automatically (bounds, overlaps, minimum text height) and compared with the dataset, and an ``Axes``
for aligned engineering plots. Output is deterministic: no random numbers, no timestamps in the drawings.
"""
import hashlib
import json
import math

from psr_diagrams import kit as K

INK, GREY, RED = K.INK, K.GREY, K.RED
CONTENT = (11.0, 17.0, 409.0, 227.0)  # x0, y0, x1, y1 of the drawing content area on an A3 sheet
SMALL_TEXT_DEFAULT_MM = 2.5

PATTERNS = {"solid": None, "dash": (4.0, 1.6), "dot": (0.9, 1.5), "dashdot": (4.0, 1.4, 0.9, 1.4), "longdash": (7.0, 2.0),
            "dashdotdot": (4.0, 1.3, 0.9, 1.3, 0.9, 1.3), "shortdash": (2.0, 1.2)}
PALETTE = [("#1f3a5f", "solid"), ("#b03a2e", "dash"), ("#1e8449", "dashdot"), ("#7d3c98", "dot"), ("#b9770e", "longdash"),
           ("#117a8b", "dashdotdot"), ("#6e4b2a", "shortdash"), ("#555555", "dot")]


def case_styles(case_ids):
    """Stable (color, pattern) per case in the order given; the same case has the same style on every sheet."""
    return {cid: PALETTE[i % len(PALETTE)] for i, cid in enumerate(case_ids)}


def patterned(pts, pattern, color=INK, sw=0.5):
    """Polyline drawn with an on/off pattern (explicit segments: dash arrays are not rendered by the PDF converter)."""
    if not pattern:
        return K.polyline(pts, stroke=color, sw=sw)
    d, idx, carry, on = [], 0, 0.0, True
    for (x1, y1), (x2, y2) in zip(pts[:-1], pts[1:]):
        seg = math.hypot(x2 - x1, y2 - y1)
        if seg <= 0:
            continue
        ux, uy, pos = (x2 - x1) / seg, (y2 - y1) / seg, 0.0
        while pos < seg - 1e-9:
            span = pattern[idx % len(pattern)] - carry
            step = min(span, seg - pos)
            if idx % 2 == 0:
                d.append("M%s %sL%s %s" % (K.fmt(x1 + ux * pos), K.fmt(y1 + uy * pos), K.fmt(x1 + ux * (pos + step)), K.fmt(y1 + uy * (pos + step))))
            pos += step
            if step >= span - 1e-9:
                idx, carry = idx + 1, 0.0
            else:
                carry += step
    return K.path(" ".join(d), stroke=color, sw=sw) if d else ""


def nice_ticks(lo, hi, target=5):
    """Round tick values covering ``[lo, hi]`` (about ``target`` intervals)."""
    if hi <= lo:
        hi = lo + 1.0
    raw = (hi - lo) / float(target)
    mag = 10 ** math.floor(math.log10(raw))
    step = next(m * mag for m in (1, 2, 2.5, 5, 10) if m * mag >= raw)
    start = math.floor(lo / step) * step
    ticks, v = [], start
    while v <= hi + 1e-9 * step:
        if v >= lo - 1e-9 * step:
            ticks.append(round(v, 10))
        v += step
    return ticks, step


def fmt_tick(v, step):
    dec = 0 if step >= 1 else int(-math.floor(math.log10(step)))
    return "%.*f" % (max(dec, 0), v)


def hash_values(values, nd=4):
    """Hash of a list of numbers (None kept) used to bind a plotted series to the dataset."""
    return hashlib.sha256(json.dumps([None if v is None else round(float(v), nd) for v in values]).encode("utf-8")).hexdigest()[:16]


class Sheet:
    """One A3 drawing sheet: collects SVG elements and a registry of annotations for automated checks."""

    def __init__(self, code, number, total, title, tb, scale_text, banner, min_text=SMALL_TEXT_DEFAULT_MM, drawing_number=None):
        self.code, self.number, self.total, self.title = code, number, total, title
        self.tb = dict(tb, scale=scale_text, format="A3 landscape")
        self.scale_text, self.banner, self.min_text = scale_text, banner, min_text
        self.drawing_number = drawing_number or "%s-%s" % (tb["document_number"], code)
        self.els = []
        self.annotations = []
        self.panels = []
        self.items = []  # data-bound geometry records (assets, segments, series)
        self.notes, self.legend = [], []

    # primitives --------------------------------------------------------------------------------
    def add(self, svg):
        if svg:
            self.els.append(svg)

    def text(self, x, y, s, size=2.8, anchor="start", bold=False, fill=INK, italic=False, rotate=None, ref=None, value_text=None, fmt=None,
             layer="annot", overlap_ok=False, aid=None):
        """Place text; the bounding box is registered. ``ref`` binds the number in ``value_text`` to a dataset reference."""
        s = K.ascii_text(s)
        w = K.text_w(s, size, bold)
        h = size * 0.9
        if rotate is None:
            x0 = x if anchor == "start" else (x - w if anchor == "end" else x - w / 2.0)
            box = (x0, y - h * 0.85, x0 + w, y + h * 0.25)
            self.add(K.text(x, y, s, size, anchor, bold, fill, italic))
        else:
            y0 = y if anchor == "start" else (y + w if anchor == "end" else y + w / 2.0)  # rotated -90: text runs upwards
            box = (x - h * 0.85, y0 - w, x + h * 0.25, y0)
            self.add(K.text(x, y, s, size, anchor, bold, fill, italic, extra=' transform="rotate(%s %s %s)"' % (K.fmt(rotate), K.fmt(x), K.fmt(y))))
        self.annotations.append({"id": aid or "T%03d" % (len(self.annotations) + 1), "type": "text", "text": s, "x": round(x, 2), "y": round(y, 2), "size": size,
                                 "bbox": [round(v, 2) for v in box], "layer": layer, "overlap_ok": overlap_ok, "ref": ref, "value_text": value_text, "fmt": fmt})
        return box

    def raw_box(self, box, kind, aid=None, overlap_ok=True):
        """Register the bounding box of a non-text element (symbol, table) for the bounds check."""
        self.annotations.append({"id": aid or "G%03d" % (len(self.annotations) + 1), "type": kind, "bbox": [round(v, 2) for v in box], "layer": "geom",
                                 "overlap_ok": overlap_ok, "size": None, "text": "", "ref": None, "value_text": None, "fmt": None})

    def bound_text(self, resolver, x, y, ref, fmt, prefix="", suffix="", size=2.8, anchor="start", bold=False, unit=None, **kw):
        """Text whose number comes from the dataset token ``ref``; the displayed number equals the resolved one by construction."""
        val = resolver.text(ref, fmt, "unit=" + unit if unit else None)
        return self.text(x, y, "%s%s%s" % (prefix, val, suffix), size, anchor, bold, ref=ref + ("|" + (fmt or "") + ("|unit=" + unit if unit else "")), value_text=val, fmt=fmt, **kw)

    # rendering ---------------------------------------------------------------------------------
    def render(self, extra_notes=None):
        out = ['<?xml version="1.0" encoding="UTF-8"?>',
               '<svg xmlns="http://www.w3.org/2000/svg" width="420mm" height="297mm" viewBox="0 0 420 297" version="1.1">',
               '<rect x="0" y="0" width="420" height="297" fill="#fff"/>']
        out.extend(K.frame())
        out.append(K.text(12.0, 13.6, "%s  %s" % (self.drawing_number, self.title), 3.6, bold=True))
        out.extend(self.els)
        if self.banner:
            out.append(K.text(CONTENT[0] + (CONTENT[2] - CONTENT[0]) / 2.0, 230.4, self.banner, 3.0, "middle", bold=True, fill=RED))
        out.extend(self._legend())
        out.extend(K.notes_box(["%d. %s" % (i + 1, n) for i, n in enumerate(self.notes + list(extra_notes or []))]))
        out.extend(K.revision_table(self.tb))
        out.extend(K.title_block(self.tb, self.drawing_number, self.number, self.total, self.title))
        out.append("</svg>")
        return "\n".join(out) + "\n"

    def _legend(self):
        x0, y0, w, h = 9.0, 233.0, 118.0, 55.0
        els = [K.rect(x0, y0, w, h, fill="#fff"), K.text(x0 + 1.5, y0 + 3.6, "LEGEND", 2.4, bold=True)]
        n = len(self.legend)
        cols = 2 if n > 6 else 1
        rows = max(1, int(math.ceil(n / float(cols))))
        cw = (w - 3.0) / cols
        rh = min(6.0, (h - 7.0) / rows)
        for i, ent in enumerate(self.legend):
            c, r = divmod(i, rows)
            x = x0 + 1.5 + c * cw
            y = y0 + 8.0 + r * rh
            kind = ent[0]
            if kind == "line":
                _, color, pattern, label = ent
                els.append(patterned([(x + 0.5, y - 0.8), (x + 11.5, y - 0.8)], PATTERNS.get(pattern), color, 0.6))
            elif kind == "marker":
                _, shape, filled, label = ent
                els.append(marker_svg(shape, x + 6.0, y - 0.8, 1.4, filled))
            else:
                label = ent[-1]
            lines = K.wrap(label, 2.0, cw - 14.5, max_lines=2)
            for j, (t, _) in enumerate(lines):
                els.append(K.text(x + 13.0, y + j * 2.2, t, 2.0))
        return els

    def spec(self, qa):
        return {"drawing_number": self.drawing_number, "code": self.code, "number": self.number, "title": self.title, "scale": self.scale_text, "format": "A3 landscape",
                "banner": self.banner, "panels": self.panels, "items": self.items, "annotations": self.annotations, "notes": self.notes,
                "legend": [list(e) for e in self.legend], "qa": qa, "title_block": {k: v for k, v in self.tb.items()}}

    def qa(self):
        """Automated layout checks: content bounds, text overlaps, text below the minimum height."""
        x0, y0, x1, y1 = CONTENT
        out_of_bounds, small, overlaps = [], [], []
        texts = [a for a in self.annotations if a["type"] == "text"]
        for a in self.annotations:
            b = a["bbox"]
            if b[0] < x0 - 0.01 or b[2] > x1 + 0.01 or b[1] < y0 - 0.01 or b[3] > y1 + 0.01:
                out_of_bounds.append({"id": a["id"], "text": a.get("text"), "bbox": b})
            if a["type"] == "text" and a["size"] is not None and a["size"] < self.min_text - 1e-9 and a["layer"] != "furniture":
                small.append({"id": a["id"], "text": a["text"], "size": a["size"]})
        for i in range(len(texts)):
            for j in range(i + 1, len(texts)):
                a, b = texts[i], texts[j]
                if a["overlap_ok"] or b["overlap_ok"]:
                    continue
                ba, bb = a["bbox"], b["bbox"]
                if ba[0] < bb[2] - 0.15 and bb[0] < ba[2] - 0.15 and ba[1] < bb[3] - 0.15 and bb[1] < ba[3] - 0.15:
                    overlaps.append({"a": a["id"], "b": b["id"], "text_a": a["text"], "text_b": b["text"]})
        return {"n_annotations": len(self.annotations), "n_text": len(texts), "out_of_bounds": out_of_bounds, "small_text": small, "text_overlaps": overlaps,
                "min_text_mm": self.min_text, "status": "PASS" if not (out_of_bounds or overlaps or small) else "FAIL"}


def marker_svg(shape, x, y, r, filled=True):
    fill = INK if filled else "#fff"
    if shape == "circle":
        return K.circle(x, y, r, fill=fill, stroke=INK, sw=0.3)
    if shape == "square":
        return K.rect(x - r, y - r, 2 * r, 2 * r, fill=fill, stroke=INK, sw=0.3)
    if shape == "triangle":
        return K.polygon([(x, y - r * 1.2), (x - r * 1.1, y + r * 0.8), (x + r * 1.1, y + r * 0.8)], fill=fill, stroke=INK, sw=0.3)
    if shape == "diamond":
        return K.polygon([(x, y - r * 1.3), (x + r * 1.1, y), (x, y + r * 1.3), (x - r * 1.1, y)], fill=fill, stroke=INK, sw=0.3)
    return K.line(x - r, y - r, x + r, y + r, sw=0.5) + K.line(x - r, y + r, x + r, y - r, sw=0.5)


class Axes:
    """Rectangular plot area with ticks, grid, series, limit lines and markers; every series is registered in the sheet spec."""

    def __init__(self, sheet, x, y, w, h, xr, yr, panel_id, title, xlabel=None, ylabel=None, show_xticks=True, y_step_target=4, x_step_target=10, units=None):
        self.s, self.x, self.y, self.w, self.h, self.xr, self.yr = sheet, x, y, w, h, xr, yr
        self.id = panel_id
        yt, ystep = nice_ticks(yr[0], yr[1], y_step_target)
        xt, xstep = nice_ticks(xr[0], xr[1], x_step_target)
        self.yr = (min(yr[0], yt[0]), max(yr[1], yt[-1]))
        sheet.add(K.rect(x, y, w, h, fill="#fff", stroke=INK, sw=0.35))
        for v in yt:
            if self.yr[0] < v < self.yr[1]:
                sheet.add(K.line(x, self.py(v), x + w, self.py(v), stroke="#d0d0d0", sw=0.15))
            sheet.add(K.line(x - 1.0, self.py(v), x, self.py(v), sw=0.3))
            sheet.text(x - 1.6, self.py(v) + 0.9, fmt_tick(v, ystep), 2.5, "end", layer="tick")
        for v in xt:
            sheet.add(K.line(self.px(v), y + h, self.px(v), y + h + 1.0, sw=0.3))
            if xr[0] < v < xr[1]:
                sheet.add(K.line(self.px(v), y, self.px(v), y + h, stroke="#e4e4e4", sw=0.15))
            if show_xticks:
                sheet.text(self.px(v), y + h + 3.8, fmt_tick(v, xstep), 2.5, "middle", layer="tick")
        sheet.text(x + 1.5, y + 3.6, title, 3.0, bold=True, layer="title", overlap_ok=False)
        if ylabel:
            sheet.text(x - 11.5, y + h / 2.0, ylabel, 2.6, "middle", rotate=-90, layer="label")
        if xlabel:
            sheet.text(x + w / 2.0, y + h + 8.4, xlabel, 2.6, "middle", layer="label")
        self.spec = {"id": panel_id, "title": title, "x0": x, "y0": y, "w": w, "h": h, "x_range": list(xr), "y_range": list(self.yr), "series": [], "x_quantity": "chainage_m",
                     "y_label": ylabel}
        sheet.panels.append(self.spec)

    def px(self, v):
        return self.x + (v - self.xr[0]) / (self.xr[1] - self.xr[0]) * self.w

    def py(self, v):
        return self.y + self.h - (v - self.yr[0]) / (self.yr[1] - self.yr[0]) * self.h

    def series(self, xs, ys, color, pattern, label, sw=0.55, meta=None):
        """Plot a polyline; ``None`` breaks the line; values outside the y range are clipped to the frame edge (and counted)."""
        runs, cur, clipped = [], [], 0
        for xv, yv in zip(xs, ys):
            if yv is None or xv is None:
                if len(cur) > 1:
                    runs.append(cur)
                cur = []
                continue
            top = self.yr[1] if getattr(self, "clip_hi", None) is None else min(self.yr[1], self.clip_hi)
            if yv < self.yr[0] or yv > top:
                clipped += 1
                yv = min(max(yv, self.yr[0]), top)
            cur.append((self.px(xv), self.py(yv)))
        if len(cur) > 1:
            runs.append(cur)
        for r in runs:
            self.s.add(patterned(r, PATTERNS.get(pattern), color, sw))
        rec = {"label": label, "n_points": len(xs), "n_plotted": sum(len(r) for r in runs), "clipped": clipped, "x_hash": hash_values(xs, 1), "y_hash": hash_values(ys), "color": color,
               "pattern": pattern}
        rec.update(meta or {})
        self.spec["series"].append(rec)

    def hline(self, yv, label, color=INK, pattern="dash", label_x=None, sw=0.45, label_anchor="end", dy=-0.8):
        if not (self.yr[0] <= yv <= self.yr[1]):
            return
        self.s.add(patterned([(self.x, self.py(yv)), (self.x + self.w, self.py(yv))], PATTERNS.get(pattern), color, sw))
        lx = self.x + self.w - 1.5 if label_x is None else label_x
        self.s.text(lx, self.py(yv) + dy, label, 2.5, label_anchor, fill=color, layer="limit")

    def vline(self, xv, color="#888", pattern="dot", sw=0.3):
        self.s.add(patterned([(self.px(xv), self.y), (self.px(xv), self.y + self.h)], PATTERNS.get(pattern), color, sw))

    def marker(self, xv, yv, shape="circle", filled=True, r=1.3, label=None, dx=2.0, dy=-1.8, anchor="start"):
        if yv is None or not (self.yr[0] <= yv <= self.yr[1]):
            return
        self.s.add(marker_svg(shape, self.px(xv), self.py(yv), r, filled))
        if label:
            self.s.text(self.px(xv) + dx, self.py(yv) + dy, label, 2.5, anchor, layer="marker")

    def hatch_below(self, yv, spacing=2.2, color="#b03a2e"):
        """Hatching over the band from the lower frame edge up to ``yv`` (an infeasible region, not colour-only)."""
        if yv <= self.yr[0]:
            return
        top = min(yv, self.yr[1])
        h = self.py(self.yr[0]) - self.py(top)
        n = int((self.w + h) / spacing)
        for i in range(n):
            x_start = self.x + i * spacing
            x1, y1 = x_start, self.py(self.yr[0])
            x2, y2 = x_start + h, self.py(top)
            if x1 > self.x + self.w:
                break
            if x2 > self.x + self.w:
                ov = x2 - (self.x + self.w)
                x2, y2 = self.x + self.w, y2 + ov
            self.s.add(K.line(x1, y1, x2, y2, stroke=color, sw=0.12))
