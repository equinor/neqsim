"""Drawing helpers for the SURF sheets: plan mapping, equipment symbols, tables and arrows on top of the shared ``fa_draw.Sheet``.

All geometry is deterministic vector output. Symbols are exaggerated and therefore never carry dimensions; every sheet states
its scale or NOT TO SCALE. Text goes through ``Sheet.text`` so that bounds, overlaps and minimum height are checked.
"""
import math

import fa_draw
from fa_draw import INK, GREY
from psr_diagrams import kit as K

COLORS = {"production": "#1f3a5f", "injection": "#117a8b", "umbilical": "#7d3c98"}
HEX = "#444"


def fit(sh, x, y, text, max_w, size=2.6, anchor="start", bold=False, fill=INK, **kw):
    """Text shortened (size first, then ellipsis) to ``max_w`` mm and placed with the registered bounding box."""
    s, sz = K.fit_text(text, max_w, size, bold)
    return sh.text(x, y, s, sz, anchor, bold=bold, fill=fill, **kw)


class PlanMap:
    """Equal-scale world (x, y in m) to sheet (mm) mapping that fits a bounding box into a sheet rectangle; north is up."""

    def __init__(self, bbox, rect):
        x0, y0, x1, y1 = bbox
        rx0, ry0, rx1, ry1 = rect
        sx = (rx1 - rx0) / max(x1 - x0, 1e-9)
        sy = (ry1 - ry0) / max(y1 - y0, 1e-9)
        self.s = min(sx, sy)
        self.ox = rx0 + ((rx1 - rx0) - (x1 - x0) * self.s) / 2.0
        self.oy = ry1 - ((ry1 - ry0) - (y1 - y0) * self.s) / 2.0
        self.x0, self.y0 = x0, y0
        self.scale_denominator = 1000.0 / self.s

    def pt(self, x, y):
        return (self.ox + (x - self.x0) * self.s, self.oy - (y - self.y0) * self.s)


def bbox_of(points, pad=0.0):
    xs, ys = [p[0] for p in points], [p[1] for p in points]
    return (min(xs) - pad, min(ys) - pad, max(xs) + pad, max(ys) + pad)


def symbol(sh, kind, x, y, size=3.0):
    """Equipment symbol centred at (x, y); registers its box. Kinds: well, manifold, plem, plet, junction, host, pump, source."""
    r = size
    if kind in ("production_well", "injection_well", "well"):
        sh.add(K.circle(x, y, r, fill="#fff", sw=0.45))
        sh.add(K.line(x - r * 0.7, y - r * 0.7, x + r * 0.7, y + r * 0.7, sw=0.4) + K.line(x - r * 0.7, y + r * 0.7, x + r * 0.7, y - r * 0.7, sw=0.4))
        if kind == "injection_well":
            sh.add(K.polygon([(x - r * 0.5, y + r * 1.25), (x + r * 0.5, y + r * 1.25), (x, y + r * 1.9)], fill=INK, sw=0.2))
    elif kind in ("manifold", "injection_manifold"):
        sh.add(K.rect(x - r, y - r * 0.8, 2 * r, 1.6 * r, fill="#fff", sw=0.55))
        sh.add(K.line(x - r, y, x + r, y, sw=0.35))
    elif kind == "plem":
        sh.add(K.polygon([(x - r * 1.3, y), (x - r * 0.65, y - r * 0.9), (x + r * 0.65, y - r * 0.9), (x + r * 1.3, y), (x + r * 0.65, y + r * 0.9), (x - r * 0.65, y + r * 0.9)], fill="#fff", sw=0.5))
    elif kind == "plet":
        sh.add(K.polygon([(x, y - r), (x + r, y), (x, y + r), (x - r, y)], fill="#fff", sw=0.5))
    elif kind == "host":
        sh.add(K.rect(x - r * 1.6, y - r * 1.2, r * 3.2, r * 2.4, fill="#e8e8e8", sw=0.7))
        sh.add(K.line(x - r * 1.6, y + r * 0.2, x + r * 1.6, y + r * 0.2, sw=0.4))
    elif kind in ("pump", "injection_source"):
        sh.add(K.circle(x, y, r, fill="#fff", sw=0.5))
        sh.add(K.polygon([(x - r * 0.5, y - r * 0.6), (x - r * 0.5, y + r * 0.6), (x + r * 0.7, y)], fill=INK, sw=0.2))
    else:
        sh.add(K.circle(x, y, r * 0.45, fill=INK, sw=0.2))
    sh.raw_box((x - r * 1.6, y - r * 1.3, x + r * 1.6, y + r * 1.9), "symbol")


def arrow(sh, x1, y1, x2, y2, color=INK, sw=0.5, pattern=None):
    """Line with a filled arrowhead at (x2, y2)."""
    sh.add(fa_draw.patterned([(x1, y1), (x2, y2)], fa_draw.PATTERNS.get(pattern), color, sw))
    sh.add(K.arrowhead(x2, y2, x2 - x1, y2 - y1))


def polyline(sh, pts, color=INK, sw=0.5, pattern=None):
    sh.add(fa_draw.patterned(pts, fa_draw.PATTERNS.get(pattern), color, sw))


def table(sh, x, y, widths, header, rows, size=2.5, row_h=4.2, bold_first=False, title=None, shade=True):
    """Ruled table; each cell text is fitted to its column and registered. Returns the y coordinate under the table."""
    cy = y
    if title:
        sh.text(x, cy + 2.8, title, 2.9, bold=True, layer="title")
        cy += 5.0
    total = sum(widths)
    sh.add(K.rect(x, cy, total, row_h, fill="#dde3ec" if shade else "#fff", sw=0.3))
    cx = x
    for w, h in zip(widths, header):
        fit(sh, cx + 1.0, cy + row_h - 1.2, h, w - 2.0, size, bold=True)
        cx += w
    cy += row_h
    for r in rows:
        sh.add(K.rect(x, cy, total, row_h, fill="#fff", sw=0.2))
        cx = x
        for i, (w, v) in enumerate(zip(widths, r)):
            fit(sh, cx + 1.0, cy + row_h - 1.2, str(v), w - 2.0, size, bold=bold_first and i == 0)
            cx += w
        cy += row_h
    cx = x
    for w in widths[:-1]:
        cx += w
        sh.add(K.line(cx, y + (5.0 if title else 0.0), cx, cy, sw=0.15, stroke="#888"))
    sh.raw_box((x, y, x + total, cy), "table")
    return cy


def scale_bar(sh, x, y, pm, length_m, label=None):
    """Scale bar of ``length_m`` metres for a :class:`PlanMap`."""
    w = length_m * pm.s
    sh.add(K.rect(x, y, w, 1.4, fill=INK, sw=0.2))
    sh.add(K.rect(x + w / 2.0, y, w / 2.0, 1.4, fill="#fff", sw=0.2))
    sh.text(x, y - 1.2, "0", 2.4)
    sh.text(x + w, y - 1.2, label or ("%g m" % length_m if length_m < 1000 else "%g km" % (length_m / 1000.0)), 2.4, "middle")
    sh.raw_box((x, y, x + w, y + 1.4), "scalebar")


def north_arrow(sh, x, y):
    sh.add(K.arrowhead(x, y - 8.0, 0, -1, length=3.5, width=2.4))
    sh.add(K.line(x, y - 8.0, x, y, sw=0.5))
    sh.text(x, y + 3.2, "N", 3.0, "middle", bold=True)
    sh.raw_box((x - 2.0, y - 9.0, x + 2.0, y + 4.0), "north")


def register_items(sh, ds, concept, kind_filter=None):
    """Record the asset identities drawn on a sheet (used by the model/drawing agreement check)."""
    return sh


def concept_edges(ds, concept):
    return {r["id"]: r for r in ds["registers"][concept]["lines"]}


def concept_nodes(ds, concept):
    return {n["id"]: n for n in ds["registers"][concept]["equipment"]}


def pump_edges(ds, concept):
    """Pump edges of the concept (from the register) keyed by identity."""
    return {p["id"]: p for p in ds["registers"][concept].get("pumps", [])}


def service_edges(ds, concept, domain):
    """Line rows of one service plus pump rows as pseudo-lines (kind ``pump``, zero length) in registration order."""
    rows = [r for r in ds["registers"][concept]["lines"] if r["service"] == domain]
    for p in ds["registers"][concept].get("pumps", []):
        if p["service"] == domain:
            rows.append(dict(p, kind="pump", installed_length_m=0.0, route_length_m=0.0, id_mm=None))
    return rows
