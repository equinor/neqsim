"""Vector renderer for the professional process overview: styles, fonts, symbols, sheet furniture, exporters.

The drawing is first built as a *scene* (a list of primitives in millimetres: lines, polygons, rectangles,
circles, text, groups) and then exported deterministically to SVG (editable text, ``data-tag`` /
``data-stream`` groups so :mod:`pfd_layout_check` can read it back), to PDF with the TrueType font embedded
(PyMuPDF), and to PNG rendered from the PDF page. All text widths are measured with the embedded font, so
label boxes and collision tests use real metrics.

Standards basis (documented conventions, conformance not claimed):

* ISO 5457 - A3 landscape 420 x 297 mm trimmed sheet; drawing frame with a 20 mm filing margin on the left
  and 10 mm elsewhere, grid reference (zone) letters/numbers and centring marks (``style["frame"]``).
* ISO 7200 - title-block data fields: legal owner, identification (document) number, title, revision index,
  date of issue, sheet number / number of sheets, document type, document status, responsible department,
  approval/creator fields (``style["title_block"]``). Fields that have not happened read "not assigned".
* ISO 10628-2 / ISO 14617 - the symbol shapes follow these conventions (horizontal vessel, vertical scrubber
  with demister, compressor/expander, pump, heater/cooler, exchanger, valve, column, mixer/splitter bars,
  ejector, reactor, generic box).

Colour is a project convention (Okabe-Ito colour-blind-safe palette), defined in the legend; the monochrome
variant encodes the service by dash pattern instead.
"""
from __future__ import annotations

import base64
import copy
import io
import math
import os
from xml.sax.saxutils import escape as _xml_escape

import psr_diagrams as _psr

MM_PER_PT = 25.4 / 72.0
PT_PER_MM = 72.0 / 25.4

# ---------------------------------------------------------------------------
# styles (all lengths in mm, fonts in pt)
# ---------------------------------------------------------------------------
PALETTE_COLOUR = {
    "ink": "#111111", "grey": "#5F6B73", "navy": "#1F3A5F",
    "multiphase": "#111111", "gas": "#D55E00", "oil": "#009E73", "water": "#0072B2", "utility": "#7F7F7F",
    "unknown": "#444444", "recycle": None,  # None: service colour, dashed
    "section_fill": "#F3F5F7", "section_stroke": "#9AA5AE", "section_title": "#1F3A5F",
    "block_fill": "#FFFFFF", "annotation_fill": "#FFFFFF", "annotation_stroke": "#9AA5AE",
    "status": "#C00000",
}
PALETTE_MONO = dict(PALETTE_COLOUR, multiphase="#111111", gas="#111111", oil="#111111", water="#111111",
                    utility="#555555", unknown="#111111", section_fill="#F6F6F6", section_stroke="#8C8C8C",
                    section_title="#111111", annotation_stroke="#8C8C8C")
#: Dash patterns (mm) by service; colour mode dashes only utility and recycle lines, mono mode all services.
DASHES = {"multiphase": None, "gas": None, "oil": [4.5, 1.5], "water": [4.0, 1.2, 0.8, 1.2], "utility": [1.4, 1.0],
          "unknown": [2.0, 2.0], "recycle": [1.6, 1.3]}
SERVICE_LABELS = {"multiphase": "Well fluid / multiphase", "gas": "Gas", "oil": "Oil / condensate", "water": "Produced water",
                  "utility": "Utility interface (not modelled)", "unknown": "Unclassified stream", "recycle": "Recycle / return"}
SERVICE_SHORT = {"multiphase": "Multiphase", "gas": "Gas", "oil": "Oil / condensate", "water": "Produced water",
                 "utility": "Utility (dashed)", "unknown": "Unclassified", "recycle": "Recycle (dashed)"}
LEGEND_SHORT = {
    "sep2": "Separator", "sep3": "3-phase separator", "scrubber": "Scrubber / KO drum", "column": "Column", "compressor": "Compressor",
    "expander": "Expander", "pump": "Pump", "heater": "Heater", "cooler": "Cooler", "exchanger": "Heat exchanger", "valve": "Valve",
    "mixer": "Mixer / header", "splitter": "Splitter / tee", "ejector": "Ejector", "recycle": "Numerical recycle", "pipe": "Pipe",
    "reactor": "Reactor", "generic": "Other equipment",
}

STYLE_A3 = {
    "name": "A3 landscape engineering drawing",
    "page": {"w": 420.0, "h": 297.0, "label": "A3"},
    "frame": {"left": 20.0, "right": 10.0, "top": 10.0, "bottom": 10.0, "zones": True, "centring_marks": True,
              "zone_cols": 8, "zone_rows": 6},
    "title_block": {"w": 140.0, "h": 44.0},
    "panels": {"legend_w": 100.0, "streams_w": 142.0, "h": 44.0},
    "pt": {"title": 14.0, "section": 10.5, "tag": 9.0, "desc": 7.5, "value": 7.5, "stream": 8.0, "note": 7.0,
           "legend": 7.5, "tb_label": 6.0, "tb_value": 9.0, "zone": 7.0, "status": 10.0, "badge": 7.0, "table": 7.0},
    "line": {"main": 0.75, "process": 0.4, "utility": 0.3, "frame": 0.7, "frame_inner": 0.35, "symbol": 0.45,
             "section": 0.3, "panel": 0.3},
    "arrow": {"l": 3.0, "w": 2.1},
    "symbol_scale": 1.0,
    "port_pitch": 5.5,
    "scales": (1.0, 0.9, 0.8, 0.7, 0.6),
    "section": {"pad_x": 5.0, "pad_y": 3.5, "title_h": 7.0, "gap_x": 11.0, "gap_y": 4.0, "corridor_gap": 3.0, "channel_pitch": 2.2,
                "lane_pitch": 2.4, "fill": True},
    "layout": {"gap_base": 9.0, "max_stretch": 6.0},
    "annotation": {"style": "bubble", "pad": 0.9, "line_gap": 0.5, "max_lines": 2, "bubble_w": 8.5, "bubble_h": 4.2},
    "compact_pt": {"tag": 8.0, "desc": 7.0, "value": 7.0, "stream": 7.5, "section": 9.5, "badge": 6.5},
    "notes_lines": 2,
    "font": {"regular": None, "bold": None, "family": "DejaVu Sans"},
    "min_label_pt": 7.0,
}
STYLE_16X9 = copy.deepcopy(STYLE_A3)
STYLE_16X9.update({
    "name": "16:9 presentation adaptation",
    "page": {"w": 338.67, "h": 190.5, "label": "16:9 (13.33 x 7.5 in)"},
    "frame": {"left": 8.0, "right": 8.0, "top": 18.0, "bottom": 12.0, "zones": False, "centring_marks": False},
    "title_block": {"w": 0.0, "h": 0.0},
    "panels": {"legend_w": 0.0, "streams_w": 0.0, "h": 0.0},
    "pt": {"title": 20.0, "section": 13.0, "tag": 11.0, "desc": 9.0, "value": 9.0, "stream": 9.5, "note": 8.0,
           "legend": 9.0, "tb_label": 7.0, "tb_value": 9.0, "zone": 7.0, "status": 10.0, "badge": 8.0, "table": 8.0},
    "line": {"main": 1.0, "process": 0.5, "utility": 0.35, "frame": 0.0, "frame_inner": 0.0, "symbol": 0.5,
             "section": 0.35, "panel": 0.3},
    "arrow": {"l": 3.6, "w": 2.6},
    "section": {"pad_x": 5.0, "pad_y": 3.5, "title_h": 9.0, "gap_x": 11.0, "gap_y": 6.0, "corridor_gap": 3.5, "channel_pitch": 2.4,
                "lane_pitch": 2.8, "fill": True},
    "annotation": {"style": "bubble", "pad": 0.9, "line_gap": 0.5, "max_lines": 2, "bubble_w": 9.5, "bubble_h": 5.4},
    "notes_lines": 0,
    "boundary_label_w": 44.0,
    "scales": (1.3, 1.15, 1.0, 0.9, 0.8, 0.7, 0.6),
    "compact_pt": {"tag": 10.0, "desc": 9.0, "value": 9.0, "stream": 9.0, "section": 11.5, "badge": 7.5},
    "min_label_pt": 9.0,
})
STYLES = {"a3": STYLE_A3, "16x9": STYLE_16X9}


def make_style(base="a3", overrides=None):
    """Deep copy of a named style with nested overrides applied."""
    st = copy.deepcopy(STYLES[base])
    for k, v in (overrides or {}).items():
        if isinstance(v, dict) and isinstance(st.get(k), dict):
            st[k].update(v)
        else:
            st[k] = v
    return st


# ---------------------------------------------------------------------------
# fonts
# ---------------------------------------------------------------------------
def _mpl_font(name):
    try:
        import matplotlib
        p = os.path.join(os.path.dirname(matplotlib.__file__), "mpl-data", "fonts", "ttf", name)
        return p if os.path.exists(p) else None
    except Exception:  # noqa: BLE001
        return None


def find_fonts(style):
    """Resolve the regular and bold TrueType files (style override, else DejaVu Sans shipped with matplotlib)."""
    reg = style["font"].get("regular") or _mpl_font("DejaVuSans.ttf")
    bold = style["font"].get("bold") or _mpl_font("DejaVuSans-Bold.ttf") or reg
    if reg is None:
        raise RuntimeError("no TrueType font found: set style['font']['regular'] to a .ttf file")
    return reg, bold


class Fonts:
    """Text metrics from the embedded fonts (PyMuPDF ``Font``)."""

    def __init__(self, style):
        import pymupdf  # noqa: F401 - fail early with a clear message when the renderer is missing

        self.regular_path, self.bold_path = find_fonts(style)
        self.family = style["font"].get("family") or "DejaVu Sans"
        self._font = {False: pymupdf.Font(fontfile=self.regular_path), True: pymupdf.Font(fontfile=self.bold_path)}
        self._cache = {}

    def width(self, text, pt, bold=False):
        """Advance width of ``text`` in mm at ``pt`` points."""
        key = (text, round(pt, 3), bold)
        if key not in self._cache:
            self._cache[key] = self._font[bold].text_length(text, fontsize=pt) * MM_PER_PT
        return self._cache[key]

    def height(self, pt):
        """Line height (ascender to descender) in mm."""
        f = self._font[False]
        return (f.ascender - f.descender) * pt * MM_PER_PT

    def cap(self, pt):
        """Cap height in mm (used for vertical centring)."""
        return 0.72 * pt * MM_PER_PT

    def wrap(self, text, pt, max_w, bold=False, max_lines=3):
        """Greedy word wrap; the last line is truncated with ``...`` when the text does not fit."""
        words = str(text).split()
        lines, cur = [], ""
        for w in words:
            cand = (cur + " " + w).strip()
            if cur and self.width(cand, pt, bold) > max_w:
                lines.append(cur)
                cur = w
            else:
                cur = cand
        if cur:
            lines.append(cur)
        if len(lines) > max_lines:
            lines = lines[:max_lines]
            last = lines[-1]
            while last and self.width(last + "...", pt, bold) > max_w:
                last = last[:-1]
            lines[-1] = last + "..."
        out = []
        for ln in lines:
            while ln and self.width(ln, pt, bold) > max_w and len(ln) > 3:
                ln = ln[:-4] + "..."
            out.append(ln)
        return out or [""]


# ---------------------------------------------------------------------------
# scene
# ---------------------------------------------------------------------------
class Scene:
    """Ordered list of drawing primitives (mm, y down) with optional attribute groups."""

    def __init__(self, w, h):
        self.w, self.h = float(w), float(h)
        self.items = []
        self._stack = []

    def begin(self, **attrs):
        g = {"t": "group", "attrs": attrs, "items": []}
        self._add(g)
        self._stack.append(g)
        return g

    def end(self):
        self._stack.pop()

    def _add(self, item):
        (self._stack[-1]["items"] if self._stack else self.items).append(item)

    def line(self, pts, sw, color, dash=None):
        self._add({"t": "line", "pts": [(float(x), float(y)) for x, y in pts], "sw": sw, "color": color, "dash": dash})

    def poly(self, pts, fill="#fff", stroke="#111", sw=0.4, dash=None):
        self._add({"t": "poly", "pts": [(float(x), float(y)) for x, y in pts], "fill": fill, "stroke": stroke, "sw": sw,
                   "dash": dash})

    def rect(self, x, y, w, h, fill="#fff", stroke="#111", sw=0.4, rx=0.0, dash=None):
        self._add({"t": "rect", "x": float(x), "y": float(y), "w": float(w), "h": float(h), "fill": fill, "stroke": stroke,
                   "sw": sw, "rx": float(rx), "dash": dash})

    def circle(self, cx, cy, r, fill="#fff", stroke="#111", sw=0.4):
        self._add({"t": "circle", "cx": float(cx), "cy": float(cy), "r": float(r), "fill": fill, "stroke": stroke, "sw": sw})

    def text(self, x, y, text, pt, anchor="start", bold=False, color="#111", italic=False, kind="label"):
        """Text with baseline at ``y``."""
        self._add({"t": "text", "x": float(x), "y": float(y), "text": str(text), "pt": float(pt), "anchor": anchor,
                   "bold": bool(bold), "color": color, "italic": bool(italic), "kind": kind})

    def arrow(self, x, y, ux, uy, l, w, color):
        n = math.hypot(ux, uy) or 1.0
        ux, uy = ux / n, uy / n
        bx, by = x - ux * l, y - uy * l
        self.poly([(x, y), (bx - uy * w / 2.0, by + ux * w / 2.0), (bx + uy * w / 2.0, by - ux * w / 2.0)],
                  fill=color, stroke=color, sw=0.1)

    def walk(self):
        """Flat iterator over primitives with the group attribute chain."""
        def rec(items, chain):
            for it in items:
                if it["t"] == "group":
                    for x in rec(it["items"], chain + [it["attrs"]]):
                        yield x
                else:
                    yield it, chain
        return rec(self.items, [])

    def texts(self):
        return [it for it, _ in self.walk() if it["t"] == "text"]


def text_bbox(fonts, item):
    """Bounding box ``(x0, y0, x1, y1)`` in mm of a text primitive."""
    w = fonts.width(item["text"], item["pt"], item["bold"])
    cap = fonts.cap(item["pt"])
    desc = 0.22 * item["pt"] * MM_PER_PT
    x = item["x"]
    if item["anchor"] == "middle":
        x -= w / 2.0
    elif item["anchor"] == "end":
        x -= w
    return (x, item["y"] - cap, x + w, item["y"] + desc)


# ---------------------------------------------------------------------------
# symbols
# ---------------------------------------------------------------------------
SYMBOL_LIBRARY = {
    "sep2": "Separator, horizontal vessel (two-phase)",
    "sep3": "Three-phase separator, horizontal vessel with weir",
    "scrubber": "Gas scrubber / knock-out drum, vertical vessel with demister",
    "column": "Column / contactor with trays",
    "compressor": "Compressor (centrifugal)",
    "expander": "Expander / turbine",
    "pump": "Pump",
    "heater": "Heater (heating utility)",
    "cooler": "Cooler (cooling utility)",
    "exchanger": "Heat exchanger (two process sides)",
    "valve": "Valve / choke / letdown",
    "mixer": "Mixer / header",
    "splitter": "Splitter / tee",
    "ejector": "Ejector",
    "recycle": "Numerical recycle element (not physical equipment)",
    "pipe": "Pipe / flowline segment",
    "reactor": "Reactor",
    "generic": "Equipment without a dedicated symbol (NeqSim class given)",
}


class Symbol:
    """Scene-drawing wrapper around the :mod:`psr_diagrams` symbol geometry (size, ports, outline).

    ``port_pitch`` (mm) replaces the fixed pitch of the report engine so that neighbouring ports stay at least one
    checker tolerance apart on the overview sheet; the symbol is enlarged when the ports need more room.
    """

    def __init__(self, psr_sym, port_pitch=5.5, n_ports=1):
        self.base = psr_sym
        self.key = psr_sym.key
        self.port_pitch = float(port_pitch)
        need = (max(n_ports, 1) - 1) * self.port_pitch + (3.0 if psr_sym.shape in ("bar", "bus") else 5.0)
        if psr_sym.shape not in ("circle",) and need > psr_sym.h:
            psr_sym.h = need
        self.w = psr_sym.w
        self.h = psr_sym.h

    def edge_half(self, dy):
        return self.base.edge_half(dy)

    def offsets(self, side, ranks):
        """Vertical port offsets (from the centre) for ``len(ranks)`` ports in order."""
        m = len(ranks)
        if m == 0:
            return []
        if self.key in ("sep2", "sep3", "scrubber") and side == "out":
            return self.base.offsets(side, ranks)
        if m == 1:
            return [0.0]
        return [(i - (m - 1) / 2.0) * self.port_pitch for i in range(m)]

    def draw(self, scene, cx, cy, ink, sw, fill="#fff"):
        """Draw the symbol centred at ``(cx, cy)``."""
        w, h, k = self.w, self.h, self.key
        if k in ("sep2", "sep3"):
            scene.rect(cx - w / 2, cy - h / 2, w, h, fill, ink, sw, rx=h / 2.0)
            if k == "sep3":
                wx = cx + 0.2 * w
                scene.line([(wx, cy + h / 2), (wx, cy - 0.12 * h)], sw, ink)
        elif k == "scrubber":
            scene.rect(cx - w / 2, cy - h / 2, w, h, fill, ink, sw, rx=w / 2.0)
            y0 = cy - 0.22 * h
            span = w - 3.0
            n = 5
            scene.line([(cx - span / 2 + i * span / n, y0 + (0.9 if i % 2 else -0.9)) for i in range(n + 1)], sw * 0.8, ink)
        elif k == "column":
            scene.rect(cx - w / 2, cy - h / 2, w, h, fill, ink, sw, rx=1.2)
            for i in range(5):
                y = cy - h / 2 + (i + 1) * h / 6.0
                if i % 2 == 0:
                    scene.line([(cx - w / 2, y), (cx + w * 0.15, y)], sw * 0.8, ink)
                else:
                    scene.line([(cx - w * 0.15, y), (cx + w / 2, y)], sw * 0.8, ink)
        elif k in ("compressor", "expander"):
            r = w / 2.0
            scene.circle(cx, cy, r, fill, ink, sw)
            sg = 1.0 if k == "compressor" else -1.0
            xa, xb = cx - sg * r * 0.574, cx + sg * r * 0.94
            scene.line([(xa, cy - r * 0.819), (xb, cy - r * 0.342)], sw, ink)
            scene.line([(xa, cy + r * 0.819), (xb, cy + r * 0.342)], sw, ink)
        elif k == "pump":
            r = w / 2.0
            scene.circle(cx, cy, r, fill, ink, sw)
            scene.poly([(cx + r * 0.62, cy), (cx - r * 0.38, cy - r * 0.6), (cx - r * 0.38, cy + r * 0.6)], fill, ink, sw)
        elif k in ("heater", "cooler"):
            r = w / 2.0
            scene.circle(cx, cy, r, fill, ink, sw)
            zz = [(-0.78, 0), (-0.5, -0.5), (-0.17, 0.5), (0.17, -0.5), (0.5, 0.5), (0.78, 0)]
            scene.line([(cx + a * r, cy + b * r) for a, b in zz], sw, ink)
        elif k == "exchanger":
            scene.rect(cx - w / 2, cy - h / 2, w, h, fill, ink, sw, rx=1.0)
            zz = [(-0.8, 0), (-0.5, -0.45), (-0.17, 0.45), (0.17, -0.45), (0.5, 0.45), (0.8, 0)]
            scene.line([(cx + a * w / 2, cy + b * h / 2) for a, b in zz], sw, ink)
        elif k == "valve":
            scene.poly([(cx - w / 2, cy - h / 2), (cx + w / 2, cy + h / 2), (cx + w / 2, cy - h / 2), (cx - w / 2, cy + h / 2)],
                       fill, ink, sw)
        elif k in ("mixer", "splitter"):
            if self.base.shape == "bus":
                scene.line([(cx, cy - h / 2), (cx, cy + h / 2)], sw * 1.4, ink)
                scene.circle(cx, cy, 1.0, ink, ink, 0.1)
            else:
                scene.rect(cx - w / 2, cy - h / 2, w, h, ink, ink, 0.1, rx=0.3)
        elif k == "ejector":
            hw, hh, th = w / 2.0, h / 2.0, h * 0.17
            scene.poly([(cx - hw, cy - hh), (cx - hw * 0.35, cy - th), (cx + hw * 0.1, cy - th), (cx + hw, cy - hh),
                        (cx + hw, cy + hh), (cx + hw * 0.1, cy + th), (cx - hw * 0.35, cy + th), (cx - hw, cy + hh)], fill, ink, sw)
        elif k == "recycle":
            r = w / 2.0
            scene.circle(cx, cy, r, fill, ink, sw)
            scene.text(cx, cy + 1.0, "R", 7.0, "middle", True, ink)
        elif k == "reactor":
            scene.rect(cx - w / 2, cy - h / 2, w, h, fill, ink, sw, rx=2.0)
            scene.line([(cx - w / 2, cy - h * 0.2), (cx + w / 2, cy - h * 0.2)], sw * 0.8, ink)
            scene.line([(cx - w / 2, cy + h * 0.2), (cx + w / 2, cy + h * 0.2)], sw * 0.8, ink)
        else:
            scene.rect(cx - w / 2, cy - h / 2, w, h, fill, ink, sw)


def make_symbol(category, java_class="", scale=1.0, n_in=1, n_out=1, port_pitch=5.5):
    """Overview symbol for an equipment category (geometry shared with :func:`psr_diagrams.make_symbol`).

    Mixers and splitters are sized by the larger of inlet and outlet count so that a header with several inlets and
    several outlets keeps every port on the bar.
    """
    n = max(n_in, n_out, 1)
    if category in ("mixer", "splitter"):
        if category == "splitter" and n > 3:
            return Symbol(_psr.make_symbol("mixer", java_class, scale, n, n), port_pitch, n)
        return Symbol(_psr.make_symbol(category, java_class, scale, n, n), port_pitch, n)
    return Symbol(_psr.make_symbol(category, java_class, scale, n_in, n_out), port_pitch, n)


# ---------------------------------------------------------------------------
# sheet furniture
# ---------------------------------------------------------------------------
def drawing_area(style):
    """``(x, y, w, h)`` of the free process area inside the frame, above the title block / panels."""
    fr, pg = style["frame"], style["page"]
    x0, y0 = fr["left"], fr["top"]
    x1, y1 = pg["w"] - fr["right"], pg["h"] - fr["bottom"]
    bottom_band = max(style["title_block"]["h"], style["panels"]["h"])
    # A3: heading line (+ note lines) inside the frame above the process area; slide: the title sits in the top band
    heading = 0.0
    if style["title_block"]["h"]:
        heading = style["pt"]["title"] * MM_PER_PT * 1.9 + style.get("notes_lines", 0) * style["pt"]["note"] * MM_PER_PT * 1.3 + 1.0
    return (x0 + 2.0, y0 + heading, (x1 - x0) - 4.0, (y1 - y0) - heading - bottom_band - (3.0 if bottom_band else 1.0))


def draw_frame(scene, style, pal):
    """ISO 5457-style frame: trimmed-sheet edge, drawing frame, zones and centring marks."""
    fr, pg = style["frame"], style["page"]
    if style["line"]["frame"] <= 0:
        return
    x0, y0, x1, y1 = fr["left"], fr["top"], pg["w"] - fr["right"], pg["h"] - fr["bottom"]
    scene.rect(x0, y0, x1 - x0, y1 - y0, "none", pal["ink"], style["line"]["frame"])
    if fr.get("zones"):
        cols, rows = int(fr.get("zone_cols", 8)), int(fr.get("zone_rows", 6))
        band = 5.0
        scene.rect(x0 - band, y0 - band, (x1 - x0) + 2 * band, (y1 - y0) + 2 * band, "none", pal["grey"],
                   style["line"]["frame_inner"])
        cw, rh = (x1 - x0) / cols, (y1 - y0) / rows
        for i in range(1, cols):
            x = x0 + i * cw
            scene.line([(x, y0 - band), (x, y0)], style["line"]["frame_inner"], pal["grey"])
            scene.line([(x, y1), (x, y1 + band)], style["line"]["frame_inner"], pal["grey"])
        for i in range(cols):
            x = x0 + (i + 0.5) * cw
            scene.text(x, y0 - band + 3.6, str(i + 1), style["pt"]["zone"], "middle", False, pal["grey"], kind="zone")
            scene.text(x, y1 + band - 1.3, str(i + 1), style["pt"]["zone"], "middle", False, pal["grey"], kind="zone")
        for j in range(1, rows):
            y = y0 + j * rh
            scene.line([(x0 - band, y), (x0, y)], style["line"]["frame_inner"], pal["grey"])
            scene.line([(x1, y), (x1 + band, y)], style["line"]["frame_inner"], pal["grey"])
        for j in range(rows):
            y = y0 + (j + 0.5) * rh + 1.0
            scene.text(x0 - band / 2.0, y, chr(65 + j), style["pt"]["zone"], "middle", False, pal["grey"], kind="zone")
            scene.text(x1 + band / 2.0, y, chr(65 + j), style["pt"]["zone"], "middle", False, pal["grey"], kind="zone")
    if fr.get("centring_marks"):
        mx, my = (x0 + x1) / 2.0, (y0 + y1) / 2.0
        for pts in (((mx, 0.0), (mx, y0 - 5.0 + 4.0)), ((mx, pg["h"]), (mx, y1 + 5.0 - 4.0)),
                    ((0.0, my), (x0 - 5.0 + 4.0, my)), ((pg["w"], my), (x1 + 5.0 - 4.0, my))):
            scene.line(pts, style["line"]["frame"], pal["ink"])


TB_FIELDS = (
    # ISO 7200-style data fields; (key, label, column span of 6, bold)
    ("company", "Legal owner / company", 3, False), ("project", "Project / facility", 3, False),
    ("title", "Title", 6, True),
    ("document_number", "Identification (document) number", 3, True), ("revision", "Rev.", 1, True),
    ("issue_status", "Document status", 2, True),
    ("sheet", "Sheet", 1, False), ("date", "Date of issue", 1, False), ("doc_type", "Document type", 2, False),
    ("case", "Represented case", 2, False),
    ("drawn_by", "Created by", 2, False), ("checked_by", "Checked by", 2, False), ("approved_by", "Approved by", 2, False),
)


def resolve_title_block(tb, graph, kind="Process flow diagram (overview)"):
    """Complete title-block fields from user input and graph provenance; nothing is implied as approved."""
    tb = dict(tb or {})
    na = "not assigned"
    src = graph.get("source") or {}
    case = graph.get("case") or {}
    out = {
        "company": tb.get("company") or na,
        "project": tb.get("project") or src.get("model_name") or na,
        "title": tb.get("title") or "%s - %s" % (src.get("model_name") or "Process", kind),
        "document_number": str(tb.get("document_number") or "DRAFT"),
        "revision": str(tb.get("revision") or na),
        "issue_status": tb.get("issue_status") or "DRAFT - not reviewed",
        "date": tb.get("date") or na,
        "doc_type": tb.get("doc_type") or kind,
        "case": tb.get("case") or "%s - %s" % (case.get("id"), case.get("label")),
        "drawn_by": tb.get("drawn_by") or na,
        "checked_by": tb.get("checked_by") or na,
        "approved_by": tb.get("approved_by") or na,
        "model_source": tb.get("model_source") or "%s%s" % (src.get("model_name") or "model",
                                                             (" / " + src["data_file"]) if src.get("data_file") else ""),
        "notes": [str(x) for x in (tb.get("notes") or [])],
        "readiness_note": tb.get("readiness_note") or "",
        "scale": "schematic - not to scale",
        "sheet_prefix": tb.get("sheet_prefix") or "PFO",
    }
    return out


def draw_title_block(scene, fonts, style, pal, tb, sheet_i, sheet_n, extra_lines=()):
    """ISO 7200-style title block in the lower right corner; returns its bbox."""
    tbs = style["title_block"]
    if tbs["w"] <= 0:
        return None
    fr, pg = style["frame"], style["page"]
    x1, y1 = pg["w"] - fr["right"], pg["h"] - fr["bottom"]
    x0, y0 = x1 - tbs["w"], y1 - tbs["h"]
    scene.rect(x0, y0, tbs["w"], tbs["h"], "#fff", pal["ink"], style["line"]["frame_inner"])
    rows = [[], [], [], [], []]
    for f in TB_FIELDS:
        idx = {"company": 0, "project": 0, "title": 1, "document_number": 2, "revision": 2, "issue_status": 2,
               "sheet": 3, "date": 3, "doc_type": 3, "case": 3, "drawn_by": 4, "checked_by": 4, "approved_by": 4}[f[0]]
        rows[idx].append(f)
    rh = tbs["h"] / 5.0
    lab_pt, val_pt = style["pt"]["tb_label"], style["pt"]["tb_value"]
    values = dict(tb)
    values["sheet"] = "%d / %d" % (sheet_i, sheet_n)
    for r, fields in enumerate(rows):
        y = y0 + r * rh
        x = x0
        for key, label, span, bold in fields:
            cw = tbs["w"] * span / 6.0
            scene.rect(x, y, cw, rh, "#fff", pal["ink"], 0.2)
            scene.text(x + 1.0, y + lab_pt * MM_PER_PT + 0.6, label, lab_pt, "start", False, pal["grey"], kind="tb_label")
            val = str(values.get(key, ""))
            pt = val_pt
            while pt > 5.5 and fonts.width(val, pt, bold) > cw - 2.0:
                pt -= 0.5
            if fonts.width(val, pt, bold) > cw - 2.0:
                while len(val) > 3 and fonts.width(val + "...", pt, bold) > cw - 2.0:
                    val = val[:-1]
                val += "..."
            scene.text(x + 1.0, y + rh - 1.6, val, pt, "start", bold, pal["ink"], kind="tb_value")
            x += cw
    return (x0, y0, x1, y1)


def draw_panel(scene, fonts, style, pal, x, y, w, h, title, lines, pt=None):
    """Framed text panel (notes, legend text, summary); returns the bbox used."""
    pt = pt or style["pt"]["note"]
    scene.rect(x, y, w, h, "#fff", pal["ink"], style["line"]["panel"])
    scene.text(x + 1.5, y + 4.0, title, pt + 0.5, "start", True, pal["ink"], kind="panel_title")
    yy = y + 4.0 + fonts.height(pt) * 1.15
    pitch = fonts.height(pt) * 1.12
    for ln in lines:
        for j, part in enumerate(fonts.wrap(ln, pt, w - 3.5, max_lines=4)):
            if yy > y + h - 1.0:
                return (x, y, x + w, y + h)
            scene.text(x + 1.5 + (0.0 if j == 0 else 2.5), yy, part, pt, "start", False, pal["ink"], kind="panel")
            yy += pitch
    return (x, y, x + w, y + h)


# ---------------------------------------------------------------------------
# exporters
# ---------------------------------------------------------------------------
def _hex_rgb(c):
    c = c.lstrip("#")
    if len(c) == 3:
        c = "".join(ch * 2 for ch in c)
    return tuple(int(c[i:i + 2], 16) / 255.0 for i in (0, 2, 4))


def _subset_font_b64(path, texts):
    """Base64 of a TrueType subset covering the glyphs used (fontTools); None when unavailable."""
    try:
        from fontTools import subset
        from fontTools.ttLib import TTFont
    except Exception:  # noqa: BLE001
        return None
    chars = set("".join(texts)) | set(" 0123456789.,-/:()%")
    try:
        import logging

        logging.getLogger("fontTools").setLevel(logging.ERROR)
        font = TTFont(path)
        opts = subset.Options()
        opts.name_IDs = ["*"]
        opts.notdef_outline = True
        opts.drop_tables = list(opts.drop_tables) + ["FFTM"]
        sub = subset.Subsetter(options=opts)
        sub.populate(text="".join(sorted(chars)))
        sub.subset(font)
        buf = io.BytesIO()
        font.save(buf)
        return base64.b64encode(buf.getvalue()).decode("ascii")
    except Exception:  # noqa: BLE001
        return None


def _svg_dash(dash):
    return ' stroke-dasharray="%s"' % " ".join("%.2f" % d for d in dash) if dash else ""


def to_svg(scene, fonts, embed_font=True, metadata=None):
    """Serialise the scene to an SVG document string (mm user units, editable text)."""
    out = ['<?xml version="1.0" encoding="UTF-8"?>',
           '<svg xmlns="http://www.w3.org/2000/svg" width="%.2fmm" height="%.2fmm" viewBox="0 0 %.2f %.2f" version="1.1">'
           % (scene.w, scene.h, scene.w, scene.h)]
    if metadata:
        out.append("<title>%s</title>" % _xml_escape(str(metadata.get("title", ""))))
        out.append("<desc>%s</desc>" % _xml_escape("; ".join("%s=%s" % (k, v) for k, v in sorted(metadata.items()))))
    family = fonts.family
    if embed_font:
        texts = [t["text"] for t in scene.texts()]
        css = []
        for bold, path in ((False, fonts.regular_path), (True, fonts.bold_path)):
            b64 = _subset_font_b64(path, texts)
            if b64:
                css.append("@font-face{font-family:'%s';font-weight:%s;src:url(data:font/ttf;base64,%s) format('truetype');}"
                           % (family, "bold" if bold else "normal", b64))
        if css:
            out.append("<style>%s</style>" % "".join(css))
    out.append('<rect x="0" y="0" width="%.2f" height="%.2f" fill="#fff"/>' % (scene.w, scene.h))

    def emit(items, depth):
        for it in items:
            t = it["t"]
            if t == "group":
                attrs = "".join(' %s="%s"' % (k.replace("_", "-"), _xml_escape(str(v))) for k, v in it["attrs"].items())
                out.append("<g%s>" % attrs)
                emit(it["items"], depth + 1)
                out.append("</g>")
            elif t == "line":
                pts = " ".join("%.2f,%.2f" % p for p in it["pts"])
                out.append('<polyline points="%s" fill="none" stroke="%s" stroke-width="%.2f" stroke-linejoin="round" '
                           'stroke-linecap="round"%s/>' % (pts, it["color"], it["sw"], _svg_dash(it["dash"])))
            elif t == "poly":
                pts = " ".join("%.2f,%.2f" % p for p in it["pts"])
                out.append('<polygon points="%s" fill="%s" stroke="%s" stroke-width="%.2f" stroke-linejoin="round"%s/>'
                           % (pts, it["fill"], it["stroke"] or "none", it["sw"], _svg_dash(it["dash"])))
            elif t == "rect":
                rx = ' rx="%.2f" ry="%.2f"' % (it["rx"], it["rx"]) if it["rx"] else ""
                out.append('<rect x="%.2f" y="%.2f" width="%.2f" height="%.2f"%s fill="%s" stroke="%s" stroke-width="%.2f"%s/>'
                           % (it["x"], it["y"], it["w"], it["h"], rx, it["fill"], it["stroke"] or "none", it["sw"], _svg_dash(it["dash"])))
            elif t == "circle":
                out.append('<circle cx="%.2f" cy="%.2f" r="%.2f" fill="%s" stroke="%s" stroke-width="%.2f"/>'
                           % (it["cx"], it["cy"], it["r"], it["fill"], it["stroke"] or "none", it["sw"]))
            elif t == "text":
                extra = ' font-weight="bold"' if it["bold"] else ""
                extra += ' font-style="italic"' if it["italic"] else ""
                out.append('<text x="%.2f" y="%.2f" font-family="%s, Arial, Helvetica, sans-serif" font-size="%.3f" text-anchor="%s" '
                           'fill="%s"%s>%s</text>' % (it["x"], it["y"], family, it["pt"] * MM_PER_PT, it["anchor"], it["color"], extra,
                                                       _xml_escape(it["text"])))
    emit(scene.items, 0)
    out.append("</svg>")
    return "\n".join(out) + "\n"


def to_pdf(scenes, fonts, path, metadata=None):
    """Write one PDF page per scene with the TrueType fonts embedded; returns the page sizes in mm."""
    import pymupdf

    doc = pymupdf.open()
    sizes = []
    for scene in scenes:
        page = doc.new_page(width=scene.w * PT_PER_MM, height=scene.h * PT_PER_MM)
        page.insert_font(fontname="F0", fontfile=fonts.regular_path)
        page.insert_font(fontname="F1", fontfile=fonts.bold_path)
        shape = page.new_shape()
        pending_text = []

        def P(x, y):
            return pymupdf.Point(x * PT_PER_MM, y * PT_PER_MM)

        def finish(stroke, fill, sw, dash=None, close=False):
            dashes = None
            if dash:
                dashes = "[%s] 0" % " ".join("%.2f" % (d * PT_PER_MM) for d in dash)
            shape.finish(color=_hex_rgb(stroke) if stroke and stroke != "none" else None,
                         fill=_hex_rgb(fill) if fill and fill != "none" else None,
                         width=max(sw, 0.05) * PT_PER_MM, dashes=dashes, closePath=close, lineJoin=1, lineCap=1)

        for it, _chain in scene.walk():
            t = it["t"]
            if t == "line":
                pts = it["pts"]
                for a, b in zip(pts[:-1], pts[1:]):
                    shape.draw_line(P(*a), P(*b))
                finish(it["color"], None, it["sw"], it["dash"])
            elif t == "poly":
                shape.draw_polyline([P(*p) for p in it["pts"]] + [P(*it["pts"][0])])
                finish(it["stroke"], it["fill"], it["sw"], it["dash"], close=True)
            elif t == "rect":
                r = pymupdf.Rect(P(it["x"], it["y"]), P(it["x"] + it["w"], it["y"] + it["h"]))
                if it["rx"] > 0:
                    rad = min(it["rx"] / min(it["w"], it["h"]), 0.5) if min(it["w"], it["h"]) > 0 else 0.0
                    shape.draw_rect(r, radius=rad)
                else:
                    shape.draw_rect(r)
                finish(it["stroke"], it["fill"], it["sw"], it["dash"], close=True)
            elif t == "circle":
                shape.draw_circle(P(it["cx"], it["cy"]), it["r"] * PT_PER_MM)
                finish(it["stroke"], it["fill"], it["sw"], None, close=True)
            elif t == "text":
                pending_text.append(it)
        shape.commit()
        for it in pending_text:
            w = fonts.width(it["text"], it["pt"], it["bold"])
            x = it["x"]
            if it["anchor"] == "middle":
                x -= w / 2.0
            elif it["anchor"] == "end":
                x -= w
            page.insert_text(P(x, it["y"]), it["text"], fontsize=it["pt"], fontname="F1" if it["bold"] else "F0",
                             color=_hex_rgb(it["color"]))
        sizes.append((page.rect.width / PT_PER_MM, page.rect.height / PT_PER_MM))
    md = {"producer": "neqsim process-overview (PyMuPDF)", "creator": "NeqSim devtools"}
    for k, v in (metadata or {}).items():
        if k in ("title", "subject", "author", "keywords"):
            md[k] = str(v)
    doc.set_metadata(md)
    doc.save(path, deflate=True, garbage=3)
    doc.close()
    return sizes


def pdf_to_png(pdf_path, png_path, page=0, dpi=150):
    """Rasterise one PDF page to PNG; returns ``(width_px, height_px)``."""
    import pymupdf

    doc = pymupdf.open(pdf_path)
    pix = doc[page].get_pixmap(dpi=dpi)
    pix.save(png_path)
    size = (pix.width, pix.height)
    doc.close()
    return size


def pdf_info(pdf_path):
    """Page sizes (mm) and font embedding status of a PDF (for the validation record)."""
    import pymupdf

    doc = pymupdf.open(pdf_path)
    pages = []
    for page in doc:
        fonts = []
        for f in page.get_fonts(full=True):
            xref = f[0]
            try:
                name, ext, ftype, buf = doc.extract_font(xref)[:4]
                embedded = bool(buf)
            except Exception:  # noqa: BLE001
                name, ext, ftype, embedded = f[3], f[1], f[2], f[1] not in ("n/a", "")
            fonts.append({"name": name, "type": ftype, "embedded": embedded})
        pages.append({"width_mm": round(page.rect.width / PT_PER_MM, 2), "height_mm": round(page.rect.height / PT_PER_MM, 2),
                      "fonts": fonts, "text_chars": len(page.get_text())})
    doc.close()
    return pages
