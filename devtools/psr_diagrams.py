"""Process flow diagram (PFD) and block flow diagram (BFD) generator.

Generates professional vector drawings (SVG, PDF, PNG preview and an editable
layout-spec JSON) from an extracted process data dictionary.  The drawings use
schematic symbols following ISO 10628-2 / ISO 14617 conventions; conformance is
not claimed.  Layout is a Sugiyama-style left-to-right layered drawing with
orthogonal channel routing, back-edge lane routing, port ordering and
collision-aware placement of stream-number diamonds.

Public API:
    build_pfd(data, out_dir, ...)   -> manifest dict
    build_bfd(data, out_dir, ...)   -> manifest dict
    inventory_svg(path)             -> {'tags': set, 'streams': set}

The SVG output uses only presentation attributes (no CSS, markers, filters or
dash arrays) so that PyMuPDF renders it faithfully; dashed lines are drawn as
explicit short segments.  Output is deterministic.
"""

from __future__ import annotations

import json
import logging
import math
import os
import unicodedata
from xml.sax.saxutils import escape as _xml_escape

log = logging.getLogger(__name__)

# ---------------------------------------------------------------------------
# constants
# ---------------------------------------------------------------------------
PAGE_W = 420.0
PAGE_H = 297.0
INK = "#111"
GREY = "#666"
RED = "#c00000"
SW = 0.35
FONT = "Arial, Helvetica, sans-serif"
TAG_FS = 2.6
DESC_FS = 2.0
LABEL_FS = 2.2
DIAMOND = 5.6  # stream-number diamond height (mm)
DIAMOND_W = 7.0  # stream-number diamond width (mm); wider than tall so the number stays legible
DIAMOND_S = (5.6, 4.6)  # compact diamond (w, h) used where the full-size one would collide
DIAMOND_FS = 2.3
EQ_FS = 2.0  # font size of the optional equipment data block
EQ_PITCH = 2.4
EQ_MAX_LINES = 3
EQ_MAX_CHARS = 34
ARROW_L = 2.2
ARROW_W = 1.5
PORT_PITCH = 4.5
CH_PITCH = 2.2
AREA = (11.0, 17.0, 398.0, 207.0)  # x, y, w, h of the process area on an A3 sheet
MIN_SCALE = 0.6
SCALES = (1.5, 1.3, 1.15, 1.0, 0.9, 0.8, 0.7, 0.6)
NOTE_ISO = (
    "schematic symbols following ISO 10628-2 / ISO 14617 conventions; conformance not claimed"
)

# approximate Arial advance widths (1/1000 em)
_CHAR_W = {
    " ": 278, "!": 278, '"': 355, "#": 556, "$": 556, "%": 889, "&": 667, "'": 191,
    "(": 333, ")": 333, "*": 389, "+": 584, ",": 278, "-": 333, ".": 278, "/": 278,
    ":": 278, ";": 278, "<": 584, "=": 584, ">": 584, "?": 556, "@": 1015,
    "A": 667, "B": 667, "C": 722, "D": 722, "E": 667, "F": 611, "G": 778, "H": 722,
    "I": 278, "J": 500, "K": 667, "L": 556, "M": 833, "N": 722, "O": 778, "P": 667,
    "Q": 778, "R": 722, "S": 667, "T": 611, "U": 722, "V": 667, "W": 944, "X": 667,
    "Y": 667, "Z": 611, "[": 278, "\\": 278, "]": 278, "^": 469, "_": 556, "`": 333,
    "a": 556, "b": 556, "c": 500, "d": 556, "e": 556, "f": 278, "g": 556, "h": 556,
    "i": 222, "j": 222, "k": 500, "l": 222, "m": 833, "n": 556, "o": 556, "p": 556,
    "q": 556, "r": 333, "s": 500, "t": 278, "u": 556, "v": 500, "w": 722, "x": 500,
    "y": 500, "z": 500, "{": 334, "|": 260, "}": 334, "~": 584,
}
for _d in "0123456789":
    _CHAR_W[_d] = 556


# ---------------------------------------------------------------------------
# small helpers
# ---------------------------------------------------------------------------
def _f(v):
    """Format a number compactly (2 decimals, no trailing zeros)."""
    s = "%.2f" % v
    if "." in s:
        s = s.rstrip("0").rstrip(".")
    return "0" if s in ("-0", "") else s


def _ascii(s):
    """Return an ASCII-only version of ``s`` (transliterated where possible)."""
    if s is None:
        return ""
    s = str(s)
    for a, b in (("\u2013", "-"), ("\u2014", "-"), ("\u00b0", "deg"), ("\u00b5", "u"),
                 ("\u2019", "'"), ("\u2018", "'"), ("\u00d8", "O"), ("\u00f8", "o"),
                 ("\u00c5", "A"), ("\u00e5", "a"), ("\u00c6", "AE"), ("\u00e6", "ae")):
        s = s.replace(a, b)
    s = unicodedata.normalize("NFKD", s)
    return s.encode("ascii", "ignore").decode("ascii")


def _esc(s):
    """XML-escape text and attribute values."""
    return _xml_escape(str(s), {'"': "&quot;"})


def text_w(s, size, bold=False):
    """Estimate the width in mm of string ``s`` at font size ``size`` (mm)."""
    w = 0.0
    for ch in s:
        w += _CHAR_W.get(ch, 556)
    w = w * size / 1000.0
    return w * 1.07 if bold else w


def _wrap(s, size, max_w, bold=False, max_lines=3):
    """Wrap ``s`` to ``max_w`` mm; returns list of (text, trailing_space)."""
    s = _ascii(s)
    words = s.split(" ")
    lines = []
    cur = ""
    pieces = []
    for w in words:
        # break over-long words at separators or by character
        while text_w(w, size, bold) > max_w and len(w) > 1:
            cut = len(w) - 1
            while cut > 1 and text_w(w[:cut], size, bold) > max_w:
                cut -= 1
            for sep in ("-", "/", "_"):
                k = w.rfind(sep, 1, cut)
                if k >= 2:
                    cut = k + 1
                    break
            pieces.append((w[:cut], False))
            w = w[cut:]
        pieces.append((w, True))
    for word, space_after in pieces:
        trial = word if not cur else cur + " " + word
        if cur and text_w(trial, size, bold) > max_w:
            lines.append((cur, True))
            cur = word
        else:
            cur = trial
        if not space_after:
            lines.append((cur, False))
            cur = ""
    if cur:
        lines.append((cur, False))
    if len(lines) > max_lines:
        # merge the overflow into the last line (tag text is never truncated)
        head = lines[: max_lines - 1]
        tail = " ".join(t for t, _ in lines[max_lines - 1:])
        lines = head + [(tail, False)]
    return lines


def _mean(v):
    return sum(v) / len(v) if v else 0.0


def _short(s, n):
    s = _ascii(s)
    return s if len(s) <= n else s[: n - 3] + "..."


# ---------------------------------------------------------------------------
# SVG primitives
# ---------------------------------------------------------------------------
def _line(x1, y1, x2, y2, stroke=INK, sw=SW):
    return '<line x1="%s" y1="%s" x2="%s" y2="%s" stroke="%s" stroke-width="%s"/>' % (
        _f(x1), _f(y1), _f(x2), _f(y2), stroke, _f(sw))


def _polyline(pts, stroke=INK, sw=SW):
    return '<polyline points="%s" fill="none" stroke="%s" stroke-width="%s" stroke-linejoin="round"/>' % (
        " ".join("%s,%s" % (_f(x), _f(y)) for x, y in pts), stroke, _f(sw))


def _polygon(pts, fill="#fff", stroke=INK, sw=SW):
    return '<polygon points="%s" fill="%s" stroke="%s" stroke-width="%s" stroke-linejoin="round"/>' % (
        " ".join("%s,%s" % (_f(x), _f(y)) for x, y in pts), fill, stroke, _f(sw))


def _rect(x, y, w, h, fill="#fff", stroke=INK, sw=SW, rx=0.0):
    r = ' rx="%s" ry="%s"' % (_f(rx), _f(rx)) if rx else ""
    st = ' stroke="%s" stroke-width="%s"' % (stroke, _f(sw)) if stroke else ' stroke="none"'
    return '<rect x="%s" y="%s" width="%s" height="%s"%s fill="%s"%s/>' % (
        _f(x), _f(y), _f(w), _f(h), r, fill, st)


def _circle(cx, cy, r, fill="#fff", stroke=INK, sw=SW):
    st = ' stroke="%s" stroke-width="%s"' % (stroke, _f(sw)) if stroke else ' stroke="none"'
    return '<circle cx="%s" cy="%s" r="%s" fill="%s"%s/>' % (_f(cx), _f(cy), _f(r), fill, st)


def _path(d, fill="none", stroke=INK, sw=SW):
    st = ' stroke="%s" stroke-width="%s"' % (stroke, _f(sw)) if stroke else ' stroke="none"'
    return '<path d="%s" fill="%s"%s stroke-linejoin="round"/>' % (d, fill, st)


def _dashed_poly(pts, dash=1.5, gap=1.0, stroke=INK, sw=SW):
    """Open polyline drawn as explicit dashes (dash arrays are not rendered by PyMuPDF)."""
    d = []
    carry = 0.0
    on = True
    for (x1, y1), (x2, y2) in zip(pts[:-1], pts[1:]):
        seg = math.hypot(x2 - x1, y2 - y1)
        if seg <= 0:
            continue
        ux, uy = (x2 - x1) / seg, (y2 - y1) / seg
        pos = 0.0
        while pos < seg - 1e-9:
            span = (dash if on else gap) - carry
            step = min(span, seg - pos)
            if on:
                d.append("M%s %sL%s %s" % (_f(x1 + ux * pos), _f(y1 + uy * pos),
                                           _f(x1 + ux * (pos + step)), _f(y1 + uy * (pos + step))))
            pos += step
            if step >= span - 1e-9:
                on = not on
                carry = 0.0
            else:
                carry += step
    return _path(" ".join(d), stroke=stroke, sw=sw)


def _dashed_circle(cx, cy, r, ndash=10, stroke=INK, sw=SW):
    """Circle drawn as ``ndash`` explicit arc dashes."""
    d = []
    step = 2 * math.pi / ndash
    for i in range(ndash):
        a0 = i * step
        a1 = a0 + step * 0.58
        x0, y0 = cx + r * math.cos(a0), cy + r * math.sin(a0)
        x1, y1 = cx + r * math.cos(a1), cy + r * math.sin(a1)
        d.append("M%s %sA%s %s 0 0 1 %s %s" % (_f(x0), _f(y0), _f(r), _f(r), _f(x1), _f(y1)))
    return _path(" ".join(d), stroke=stroke, sw=sw)


def _text(x, y, s, size, anchor="start", bold=False, fill=INK, italic=False, extra=""):
    s = _ascii(s)
    return ('<text x="%s" y="%s" font-family="%s" font-size="%s" text-anchor="%s"%s%s fill="%s" '
            'stroke="none"%s>%s</text>') % (
        _f(x), _f(y), FONT, _f(size), anchor,
        ' font-weight="bold"' if bold else "", ' font-style="italic"' if italic else "",
        fill, extra, _esc(s))


def _text_lines(x, y0, lines, size, line_h, anchor="middle", bold=False, fill=INK, extra=""):
    """Multi-line text as one <text> with absolutely positioned tspans.

    ``lines`` is a list of (text, trailing_space); the text content concatenated
    equals the original string.
    """
    if len(lines) == 1:
        return _text(x, y0, lines[0][0], size, anchor, bold, fill, extra=extra)
    parts = []
    for i, (t, sp) in enumerate(lines):
        parts.append('<tspan x="%s" y="%s">%s</tspan>' % (
            _f(x), _f(y0 + i * line_h), _esc(t + (" " if sp and i < len(lines) - 1 else ""))))
    return ('<text font-family="%s" font-size="%s" text-anchor="%s"%s fill="%s" stroke="none"%s>%s</text>' % (
        FONT, _f(size), anchor, ' font-weight="bold"' if bold else "", fill, extra, "".join(parts)))


def _arrowhead(x, y, dx, dy, length=ARROW_L, width=ARROW_W):
    """Filled triangle with its tip at (x, y) pointing along (dx, dy)."""
    n = math.hypot(dx, dy) or 1.0
    dx, dy = dx / n, dy / n
    bx, by = x - dx * length, y - dy * length
    px, py = -dy * width / 2.0, dx * width / 2.0
    return _polygon([(x, y), (bx + px, by + py), (bx - px, by - py)], fill=INK, stroke=INK, sw=0.1)


def _g(children, **attrs):
    a = "".join(' %s="%s"' % (k.replace("_", "-"), _esc(v)) for k, v in attrs.items())
    return "<g%s>%s</g>" % (a, "".join(children))


# ---------------------------------------------------------------------------
# equipment symbols
# ---------------------------------------------------------------------------
LEGEND_NAMES = {
    "sep2": "Separator (horizontal vessel)",
    "sep3": "Three-phase separator (weir)",
    "scrubber": "Gas scrubber (vertical, demister)",
    "compressor": "Compressor (centrifugal)",
    "expander": "Expander",
    "pump": "Pump",
    "heater": "Heater (heating utility)",
    "cooler": "Cooler (cooling utility)",
    "exchanger": "Heat exchanger",
    "valve": "Valve",
    "mixer": "Mixer / header",
    "splitter": "Splitter / tee",
    "ejector": "Ejector",
    "column": "Column",
    "recycle": "Numerical recycle element, not physical equipment",
    "pipe": "Pipe",
    "reactor": "Reactor",
    "generic": "Other equipment",
}


class _Sym:
    """A schematic equipment symbol: size, shape, ports and drawing."""

    def __init__(self, key, w, h, shape, extra=None):
        self.key = key
        self.w = w
        self.h = h
        self.shape = shape
        self.extra = extra  # 'heating utility' | 'cooling utility' | 'utility side' | 'power' | None

    def edge_half(self, dy):
        """Half-width of the symbol outline at vertical offset ``dy`` from its centre."""
        w, h, s = self.w, self.h, self.shape
        dy = abs(dy)
        if s == "circle":
            r = w / 2.0
            return math.sqrt(max(r * r - dy * dy, 0.0)) if dy < r else 0.0
        if s == "caph":
            r = h / 2.0
            if dy >= r:
                return w / 2.0 - r
            return w / 2.0 - (r - math.sqrt(max(r * r - dy * dy, 0.0)))
        if s == "capv":
            r = w / 2.0
            straight = h / 2.0 - r
            if dy <= straight:
                return r
            d = dy - straight
            return math.sqrt(max(r * r - d * d, 0.0)) if d < r else 0.0
        if s == "bus":
            return 0.0
        return w / 2.0

    def offsets(self, side, ranks):
        """Vertical port offsets (from the centre) for ``len(ranks)`` ports in order."""
        m = len(ranks)
        if m == 0:
            return []
        if self.key in ("sep2", "sep3", "scrubber") and side == "out":
            if m == 1:
                return [-0.25 * self.h if ranks[0] == 0 else 0.25 * self.h]
            top = self.h / 2.0 - 2.5
            return [-top + i * (2 * top) / (m - 1) for i in range(m)]
        if m == 1:
            return [0.0]
        return [(i - (m - 1) / 2.0) * PORT_PITCH for i in range(m)]

    def draw(self, cx, cy):
        """Return SVG strings drawing the symbol centred at (cx, cy)."""
        w, h, k = self.w, self.h, self.key
        e = []
        if k in ("sep2", "sep3"):
            e.append(_rect(cx - w / 2, cy - h / 2, w, h, rx=h / 2.0))
            if k == "sep3":
                wx = cx + 0.2 * w
                e.append(_line(wx, cy + h / 2, wx, cy - 0.12 * h))
        elif k == "scrubber":
            e.append(_rect(cx - w / 2, cy - h / 2, w, h, rx=w / 2.0))
            y0 = cy - 0.22 * h
            n = 5
            span = w - 3.0
            pts = [(cx - span / 2 + i * span / n, y0 + (0.9 if i % 2 else -0.9)) for i in range(n + 1)]
            e.append(_polyline(pts))
        elif k == "column":
            e.append(_rect(cx - w / 2, cy - h / 2, w, h, rx=1.2))
            n = 5
            for i in range(n):
                y = cy - h / 2 + (i + 1) * h / (n + 1)
                if i % 2 == 0:
                    e.append(_line(cx - w / 2, y, cx + w * 0.15, y))
                else:
                    e.append(_line(cx - w * 0.15, y, cx + w / 2, y))
        elif k in ("compressor", "expander"):
            r = w / 2.0
            e.append(_circle(cx, cy, r))
            sg = 1.0 if k == "compressor" else -1.0
            xa = cx - sg * r * 0.574
            xb = cx + sg * r * 0.94
            e.append(_line(xa, cy - r * 0.819, xb, cy - r * 0.342))
            e.append(_line(xa, cy + r * 0.819, xb, cy + r * 0.342))
        elif k == "pump":
            r = w / 2.0
            e.append(_circle(cx, cy, r))
            e.append(_polygon([(cx + r * 0.62, cy), (cx - r * 0.38, cy - r * 0.6),
                               (cx - r * 0.38, cy + r * 0.6)], fill="#fff"))
        elif k in ("heater", "cooler"):
            r = w / 2.0
            e.append(_circle(cx, cy, r))
            zz = [(-0.78, 0), (-0.5, -0.5), (-0.17, 0.5), (0.17, -0.5), (0.5, 0.5), (0.78, 0)]
            e.append(_polyline([(cx + a * r, cy + b * r) for a, b in zz]))
        elif k == "exchanger":
            e.append(_rect(cx - w / 2, cy - h / 2, w, h, rx=1.0))
            zz = [(-0.8, 0), (-0.5, -0.45), (-0.17, 0.45), (0.17, -0.45), (0.5, 0.45), (0.8, 0)]
            e.append(_polyline([(cx + a * w / 2, cy + b * h / 2) for a, b in zz]))
        elif k == "valve":
            e.append(_polygon([(cx - w / 2, cy - h / 2), (cx + w / 2, cy + h / 2),
                               (cx + w / 2, cy - h / 2), (cx - w / 2, cy + h / 2)]))
        elif k == "mixer":
            e.append(_rect(cx - w / 2, cy - h / 2, w, h, fill=INK, rx=0.3))
        elif k == "splitter":
            if self.shape == "bus":
                e.append(_line(cx, cy - h / 2, cx, cy + h / 2, sw=0.5))
                e.append(_circle(cx, cy, 1.0, fill=INK, stroke=INK, sw=0.1))
            else:
                e.append(_rect(cx - w / 2, cy - h / 2, w, h, fill=INK, rx=0.3))
        elif k == "ejector":
            hw, hh, th = w / 2.0, h / 2.0, h * 0.17
            e.append(_polygon([(cx - hw, cy - hh), (cx - hw * 0.35, cy - th), (cx + hw * 0.1, cy - th),
                               (cx + hw, cy - hh), (cx + hw, cy + hh), (cx + hw * 0.1, cy + th),
                               (cx - hw * 0.35, cy + th), (cx - hw, cy + hh)]))
        elif k == "recycle":
            r = w / 2.0
            e.append(_circle(cx, cy, r, stroke=None))
            e.append(_dashed_circle(cx, cy, r))
            e.append(_text(cx, cy + 1.05, "R", 3.0, "middle", bold=True))
        elif k == "pipe":
            e.append(_rect(cx - w / 2, cy - h / 2, w, h))
        elif k == "reactor":
            e.append(_rect(cx - w / 2, cy - h / 2, w, h, rx=2.0))
            e.append(_line(cx - w / 2, cy - h * 0.2, cx + w / 2, cy - h * 0.2))
            e.append(_line(cx - w / 2, cy + h * 0.2, cx + w / 2, cy + h * 0.2))
        else:
            e.append(_rect(cx - w / 2, cy - h / 2, w, h))
        return e

    def extra_below(self):
        """Vertical space (mm) below the symbol used by utility / driver interfaces."""
        if self.extra in ("heating utility", "cooling utility", "utility side"):
            return 8.9
        if self.extra == "power":
            return 10.2
        return 0.0

    def extra_width(self):
        """Width (mm) of the text used by the utility / driver interface."""
        if self.extra == "power":
            return max(text_w("driver (not modelled)", DESC_FS), 2 * 3.3 + text_w("shaft power", DESC_FS))
        if self.extra:
            return text_w(self.extra, DESC_FS) + 1.0
        return 0.0

    def draw_extras(self, cx, cy):
        """Dashed utility stubs / shaft-power stub with labels; returns (elements, rect)."""
        if not self.extra:
            return [], None
        b = cy + self.h / 2.0
        e = []
        if self.extra == "power":
            top = cy + self.edge_below(0.0)
            e.append(_dashed_poly([(cx, top), (cx, b + 3.6)]))
            e.append(_circle(cx, b + 5.4, 1.8))
            e.append(_text(cx, b + 6.1, "W", 1.9, "middle", bold=True))
            e.append(_text(cx + 3.3, b + 6.0, "shaft power", DESC_FS, "start", fill=GREY))
            e.append(_text(cx, b + 9.8, "driver (not modelled)", DESC_FS, "middle", fill=GREY, italic=True))
            wd = self.extra_width()
            return e, (cx - wd / 2, b, cx + wd / 2, b + 10.5)
        dx = min(self.w * 0.22, 3.6)
        for sx, up in ((cx - dx, True), (cx + dx, False)):
            y0 = cy + self.hx_bottom(sx - cx)
            y1 = b + 5.5
            e.append(_dashed_poly([(sx, y0), (sx, y1)]))
            if up:
                e.append(_arrowhead(sx, y0, 0, -1, 1.7, 1.2))
            else:
                e.append(_arrowhead(sx, y1, 0, 1, 1.7, 1.2))
        e.append(_text(cx, b + 8.5, self.extra, DESC_FS, "middle", fill=GREY, italic=True))
        wd = self.extra_width()
        return e, (cx - wd / 2, b, cx + wd / 2, b + 9.2)

    def edge_below(self, dx):
        """Vertical offset of the lower outline at horizontal offset ``dx``."""
        if self.shape == "circle":
            r = self.w / 2.0
            return math.sqrt(max(r * r - dx * dx, 0.0))
        return self.h / 2.0

    def hx_bottom(self, dx):
        """Lower outline offset used for utility stubs."""
        return self.edge_below(dx)


def make_symbol(category, java_class="", scale=1.0, n_in=1, n_out=1):
    """Create the schematic symbol for an equipment category.

    Args:
        category: equipment category string (valve, mixer, ... other).
        java_class: NeqSim class name (used to distinguish separator types).
        scale: symbol scale factor (0.6 .. 1.0).
        n_in: number of inlet streams (drives mixer bar height).
        n_out: number of outlet streams.

    Returns:
        A ``_Sym`` instance.
    """
    s = scale
    cat = category
    jc = java_class or ""
    nmax = max(n_in, n_out, 1)
    need = (nmax - 1) * PORT_PITCH + 5.0
    if cat == "separator":
        if "ThreePhase" in jc:
            return _Sym("sep3", 28 * s, max(16 * s, need), "caph")
        if "Scrubber" in jc:
            return _Sym("scrubber", 12 * s, max(28 * s, need), "capv")
        return _Sym("sep2", 26 * s, max(12 * s, need), "caph")
    if cat == "column":
        return _Sym("column", 12 * s, max(36 * s, need), "rect")
    if cat == "compressor":
        return _Sym("compressor", 14 * s, 14 * s, "circle", "power")
    if cat == "expander":
        return _Sym("expander", 14 * s, 14 * s, "circle", "power")
    if cat == "pump":
        return _Sym("pump", 12 * s, 12 * s, "circle", "power")
    if cat == "heater":
        return _Sym("heater", 14 * s, 14 * s, "circle", "heating utility")
    if cat == "cooler":
        return _Sym("cooler", 14 * s, 14 * s, "circle", "cooling utility")
    if cat == "exchanger":
        return _Sym("exchanger", 18 * s, max(12 * s, need), "rect", "utility side")
    if cat == "valve":
        return _Sym("valve", 10 * s, max(7 * s, need if nmax > 1 else 0), "valve")
    if cat == "mixer":
        return _Sym("mixer", 1.8, max(8.0, need), "bar")
    if cat == "splitter":
        n = max(n_out, 1)
        if n <= 3:
            return _Sym("splitter", 1.6, max(4.0, (n - 1) * PORT_PITCH + 1.5), "bus")
        return _Sym("splitter", 1.8, max(8.0, need), "bar")
    if cat == "ejector":
        return _Sym("ejector", 20 * s, max(8 * s, need), "rect")
    if cat == "recycle":
        return _Sym("recycle", 6.4, 6.4, "circle")
    if cat == "pipe":
        return _Sym("pipe", 22 * s, 3.6, "rect")
    if cat == "reactor":
        return _Sym("reactor", 16 * s, max(16 * s, need), "rect")
    return _Sym("generic", 14 * s, max(10 * s, need), "rect")


# ---------------------------------------------------------------------------
# generic layered layout engine
# ---------------------------------------------------------------------------
class _Node:
    """Layout node: unit/block, boundary feed/product, connector or dummy."""

    def __init__(self, nid, kind, w=0.0, h=0.0, above=0.0, below=0.0, sym=None, payload=None):
        self.id = nid
        self.kind = kind
        self.w = w
        self.h = h
        self.above = above
        self.below = below
        self.sym = sym
        self.payload = payload or {}
        self.layer = 0
        self.cx = 0.0
        self.cy = 0.0
        self.fixed_out = False
        self.out_rank = {}
        self.port_dy = {}
        self.order = {"in": [], "out": []}

    @property
    def real(self):
        return self.kind != "dummy"

    def port_x(self, side, dy=0.0):
        """Absolute x of the port on the given side at vertical offset ``dy``."""
        if self.kind == "feed" and side == "out":
            return self.cx - self.w / 2.0
        if self.kind == "product" and side == "in":
            return self.cx + self.w / 2.0
        hw = self.sym.edge_half(dy) if self.sym is not None else self.w / 2.0
        return self.cx + hw if side == "out" else self.cx - hw

    def port_y(self, side, key):
        """Absolute y of the port used by stream ``key``."""
        return self.cy + self.port_dy.get((side, key), 0.0)

    def pxy(self, side, key):
        """Absolute (x, y) of the port used by stream ``key``."""
        dy = self.port_dy.get((side, key), 0.0)
        return self.port_x(side, dy), self.cy + dy


class _Edge:
    """A directed connection (one stream towards one target)."""

    def __init__(self, eid, key, src, dst, meta=None):
        self.eid = eid
        self.key = key
        self.src = src
        self.dst = dst
        self.meta = meta or {}
        self.chain = []
        self.back = False
        self.lane_y = None
        self.lane_side = None
        self.pts = []


class _Layout:
    """Container for a layered layout."""

    def __init__(self, nodes, edges, cfg):
        self.nodes = {n.id: n for n in nodes}
        self.real = list(nodes)
        self.edges = edges
        self.cfg = cfg
        self.layers = []
        self.gap_w = []
        self.lx = []
        self.rx = []
        self.dots = []
        self.warnings = []
        self.top = 0.0
        self.bottom = 0.0
        self.width = 0.0
        self.node_edges = {}


def _pava(vals, wts):
    """Weighted isotonic (non-decreasing) regression."""
    blocks = []
    for v, w in zip(vals, wts):
        blocks.append([w, w * v, 1])
        while len(blocks) > 1 and blocks[-2][1] / blocks[-2][0] > blocks[-1][1] / blocks[-1][0]:
            b = blocks.pop()
            blocks[-1][0] += b[0]
            blocks[-1][1] += b[1]
            blocks[-1][2] += b[2]
    out = []
    for sw_, swv, c in blocks:
        out.extend([swv / sw_] * c)
    return out


def _cycle_removal(units, edges):
    """DFS cycle removal; returns (topological order, set of back-edge ids)."""
    uid = {n.id for n in units}
    by_src = {}
    for e in edges:
        if e.src in uid and e.dst in uid:
            by_src.setdefault(e.src, []).append(e)
    idx = {n.id: i for i, n in enumerate(units)}
    nodes = {n.id: n for n in units}
    for lst in by_src.values():
        lst.sort(key=lambda e: (nodes[e.src].out_rank.get(e.key, 1) if nodes[e.src].fixed_out else 0,
                                idx[e.dst], e.eid))
    has_in = set()
    for lst in by_src.values():
        for e in lst:
            if e.src != e.dst:
                has_in.add(e.dst)
    roots = [n.id for n in units if n.id not in has_in] + [n.id for n in units]
    color = {}
    post = []
    back = set()

    def visit(u):
        color[u] = 1
        for e in by_src.get(u, []):
            c = color.get(e.dst, 0)
            if c == 1:
                back.add(e.eid)
            elif c == 0:
                visit(e.dst)
        color[u] = 2
        post.append(u)

    for r in roots:
        if color.get(r, 0) == 0:
            visit(r)
    return list(reversed(post)), back


def _assign_layers(layout, units, topo, back):
    """Longest-path layering with promotion of source-like units."""
    nodes = layout.nodes
    uid = {n.id for n in units}
    preds = {u: [] for u in uid}
    succs = {u: [] for u in uid}
    for e in layout.edges:
        if e.src in uid and e.dst in uid and e.eid not in back and e.src != e.dst:
            preds[e.dst].append(e.src)
            succs[e.src].append(e.dst)
    layer = {}
    for u in topo:
        layer[u] = 1 + max([layer[p] for p in preds[u]], default=0)
    for u in reversed(topo):
        if not preds[u] and succs[u]:
            layer[u] = max(1, min(layer[s] for s in succs[u]) - 1)
    for u in uid:
        nodes[u].layer = layer[u]
    others = [n for n in layout.real if n.id not in uid]
    for n in others:
        if n.kind in ("feed", "conn_in"):
            ds = [nodes[e.dst].layer for e in layout.edges if e.src == n.id and e.dst in uid]
            n.layer = (min(ds) - 1) if ds else 0
    for n in others:
        if n.kind in ("product", "conn_out"):
            ss = [nodes[e.src].layer for e in layout.edges if e.dst == n.id]
            n.layer = (max(ss) + 1) if ss else 1
    lo = min(n.layer for n in layout.real) if layout.real else 0
    for n in layout.real:
        n.layer -= lo


def _build_chains(layout, back):
    """Create dummy nodes for long edges; mark back edges."""
    nodes = layout.nodes
    n_dummy = 0
    for e in layout.edges:
        s, d = nodes[e.src], nodes[e.dst]
        if e.eid in back or d.layer <= s.layer:
            e.back = True
            e.chain = [s, d]
            continue
        chain = [s]
        for L in range(s.layer + 1, d.layer):
            n_dummy += 1
            dn = _Node("D:%d" % n_dummy, "dummy", 0.0, 0.0, 1.3, 1.3)
            dn.layer = L
            nodes[dn.id] = dn
            chain.append(dn)
        chain.append(d)
        e.chain = chain
    nl = max((n.layer for n in nodes.values()), default=0) + 1
    layout.layers = [[] for _ in range(nl)]
    for n in layout.real:
        layout.layers[n.layer].append(n)
    for n in nodes.values():
        if n.kind == "dummy":
            layout.layers[n.layer].append(n)
    ne = {}
    for e in layout.edges:
        ne.setdefault((e.src, "out"), []).append(e)
        ne.setdefault((e.dst, "in"), []).append(e)
    layout.node_edges = ne
    # unique stream keys per side, creation order
    for n in layout.real:
        for side in ("in", "out"):
            keys = []
            for e in ne.get((n.id, side), []):
                if e.key not in keys:
                    keys.append(e.key)
            n.order[side] = keys


def _seg_list(layout):
    segs = []
    for e in layout.edges:
        if e.back:
            continue
        for a, b in zip(e.chain[:-1], e.chain[1:]):
            segs.append((a, b, e))
    return segs


def _frac(node, key, side):
    keys = node.order[side]
    if not node.real or len(keys) < 2:
        return 0.0
    if side == "out" and node.fixed_out:
        ranked = sorted(keys, key=lambda k: (node.out_rank.get(k, 1), keys.index(k)))
        r = ranked.index(key)
    else:
        r = keys.index(key) if key in keys else 0
    return ((r + 0.5) / len(keys) - 0.5) * 0.8


def _count_crossings(layers, pos, segs_by_gap):
    total = 0
    for segs in segs_by_gap:
        for i in range(len(segs)):
            a1, b1 = segs[i]
            for j in range(i + 1, len(segs)):
                a2, b2 = segs[j]
                if (a1 - a2) * (b1 - b2) < 0:
                    total += 1
    return total


def _order_layers(layout, sweeps=12):
    """Barycentre crossing reduction (keeps the best ordering found)."""
    layers = layout.layers
    nl = len(layers)
    segs = _seg_list(layout)
    up = {}
    down = {}
    for a, b, e in segs:
        down.setdefault(a.id, []).append((b, 0.0))
        # a predecessor contributes its out-port fraction to the barycentre
        up.setdefault(b.id, []).append((a, _frac(a, e.key, "out")))
    pos = {}

    def refresh():
        for layer in layers:
            for i, n in enumerate(layer):
                pos[n.id] = i

    def crossings():
        by_gap = [[] for _ in range(max(nl - 1, 0))]
        for a, b, e in segs:
            by_gap[a.layer].append((pos[a.id] + _frac(a, e.key, "out"), pos[b.id]))
        return _count_crossings(layers, pos, by_gap)

    refresh()
    best = (crossings(), [list(l) for l in layers])
    for sweep in range(sweeps):
        direction = "down" if sweep % 2 == 0 else "up"
        rng = range(1, nl) if direction == "down" else range(nl - 2, -1, -1)
        nbr = up if direction == "down" else down
        for li in rng:
            layer = layers[li]
            keys = {}
            for n in layer:
                ns = nbr.get(n.id)
                if ns:
                    keys[n.id] = sum(pos[m.id] + f for m, f in ns) / len(ns)
            movable = sorted([n for n in layer if n.id in keys], key=lambda n: (keys[n.id], pos[n.id]))
            new = [None] * len(layer)
            for n in layer:
                if n.id not in keys:
                    new[pos[n.id]] = n
            it = iter(movable)
            for i in range(len(new)):
                if new[i] is None:
                    new[i] = next(it)
            layers[li] = new
            for i, n in enumerate(new):
                pos[n.id] = i
        c = crossings()
        if c < best[0]:
            best = (c, [list(l) for l in layers])
        if c == 0:
            break
    layout.layers = best[1]
    layers[:] = layout.layers
    return best[0]


def _node_off(node, side, key):
    return node.port_dy.get((side, key), 0.0) if node.real else 0.0


def _assign_ports(layout, final=False):
    """Order and place ports on each real node by the y of their neighbours."""
    for n in layout.real:
        for side in ("in", "out"):
            keys = n.order[side]
            if not keys:
                continue
            ys = {}
            for e in layout.node_edges.get((n.id, side), []):
                if e.back:
                    other = layout.nodes[e.dst if side == "out" else e.src]
                    ny = e.lane_y if (e.lane_y is not None and final) else other.cy
                else:
                    ny = (e.chain[1] if side == "out" else e.chain[-2]).cy
                ys.setdefault(e.key, []).append(ny)
            base = list(n.order[side])
            if side == "out" and n.fixed_out:
                order = sorted(base, key=lambda k: (n.out_rank.get(k, 1), base.index(k)))
            else:
                order = sorted(base, key=lambda k: (_mean(ys.get(k, [n.cy])), base.index(k)))
            n.order[side] = order
            if n.sym is not None:
                ranks = [n.out_rank.get(k, 1) for k in order]
                offs = n.sym.offsets(side, ranks)
            elif len(order) > 1:
                pitch = n.payload.get("pitch", PORT_PITCH)
                offs = [(i - (len(order) - 1) / 2.0) * pitch for i in range(len(order))]
            else:
                offs = [0.0]
            for k, o in zip(order, offs):
                n.port_dy[(side, k)] = o


def _stack_layer(layer, margin=1.5):
    ys = []
    y = 0.0
    prev = None
    for n in layer:
        if prev is not None:
            y += _sep(prev, n, margin)
        ys.append(y)
        prev = n
    mid = (ys[0] + ys[-1]) / 2.0 if ys else 0.0
    for n, v in zip(layer, ys):
        n.cy = v - mid


def _sep(a, b, margin=1.5):
    if a.kind in ("feed", "product") and b.kind in ("feed", "product"):
        return max(a.below + b.above, PORT_PITCH)
    if a.kind == "dummy" or b.kind == "dummy":
        return a.below + b.above + 0.6
    return a.below + b.above + margin


def _relax_layer(layout, li, direction, preds, succs):
    layer = layout.layers[li]
    if not layer:
        return
    desired = []
    weights = []
    for n in layer:
        targets = []
        if direction in ("down", "both"):
            for a, e in preds.get(n.id, []):
                w = 3.0 if not a.real else 1.0
                targets.append((a.cy + _node_off(a, "out", e.key) - _node_off(n, "in", e.key), w))
        if direction in ("up", "both"):
            for b, e in succs.get(n.id, []):
                w = 3.0 if not b.real else 1.0
                targets.append((b.cy + _node_off(b, "in", e.key) - _node_off(n, "out", e.key), w))
        if targets:
            tw = sum(w for _, w in targets)
            desired.append(sum(v * w for v, w in targets) / tw)
            weights.append(tw)
        else:
            desired.append(n.cy)
            weights.append(0.05)
    cum = [0.0]
    for a, b in zip(layer[:-1], layer[1:]):
        cum.append(cum[-1] + _sep(a, b))
    z = [d - c for d, c in zip(desired, cum)]
    zs = _pava(z, weights)
    for n, v, c in zip(layer, zs, cum):
        n.cy = v + c


def _assign_y(layout, iters=8):
    layers = layout.layers
    segs = _seg_list(layout)
    preds = {}
    succs = {}
    for a, b, e in segs:
        preds.setdefault(b.id, []).append((a, e))
        succs.setdefault(a.id, []).append((b, e))
    for layer in layers:
        _stack_layer(layer)
    for _ in range(iters):
        _assign_ports(layout)
        for direction in ("down", "up"):
            order = list(range(len(layers)))
            if direction == "up":
                order.reverse()
            for li in order:
                _relax_layer(layout, li, direction, preds, succs)
    for _ in range(2):
        _assign_ports(layout)
        for li in range(len(layers)):
            _relax_layer(layout, li, "both", preds, succs)
    _assign_ports(layout)


def _straighten(layout, passes=3, tol=3.0):
    """Remove small jogs by shifting nodes so that most of their ports align with neighbours."""
    layers = layout.layers
    nbrs = {}
    for a, b, e in _seg_list(layout):
        nbrs.setdefault(a.id, []).append((b, "out", "in", e.key))
        nbrs.setdefault(b.id, []).append((a, "in", "out", e.key))
    for p in range(passes):
        order = list(range(len(layers)))
        if p % 2:
            order.reverse()
        for li in order:
            layer = layers[li]
            for idx, n in enumerate(layer):
                ds = []
                for m, mine, other, key in nbrs.get(n.id, []):
                    ds.append((m.cy + _node_off(m, other, key)) - (n.cy + _node_off(n, mine, key)))
                if not ds:
                    continue
                aligned0 = sum(1 for d in ds if abs(d) < 0.02)
                best = (aligned0, 0.0)
                for d in ds:
                    if abs(d) < 0.02 or abs(d) > tol:
                        continue
                    cnt = sum(1 for c in ds if abs(c - d) < 0.02)
                    if cnt > best[0] or (cnt == best[0] and best[1] != 0.0 and abs(d) < abs(best[1])):
                        best = (cnt, d)
                shift = best[1]
                if shift == 0.0:
                    continue
                lo = layer[idx - 1].cy + _sep(layer[idx - 1], n) if idx > 0 else -1e9
                hi = layer[idx + 1].cy - _sep(n, layer[idx + 1]) if idx + 1 < len(layer) else 1e9
                if lo - 1e-6 <= n.cy + shift <= hi + 1e-6:
                    n.cy += shift


def _content_bounds(layout):
    top = min((n.cy - n.above for n in layout.real), default=0.0)
    bot = max((n.cy + n.below for n in layout.real), default=0.0)
    return top, bot


def _assign_lanes(layout):
    """Assign lane y-coordinates to back edges (above / below the content)."""
    backs = [e for e in layout.edges if e.back]
    top, bot = _content_bounds(layout)
    layout.top, layout.bottom = top, bot
    if not backs:
        return
    mid = (top + bot) / 2.0
    backs.sort(key=lambda e: (layout.nodes[e.src].layer - layout.nodes[e.dst].layer, e.eid))
    n_up = n_dn = 0
    for e in backs:
        ym = (layout.nodes[e.src].cy + layout.nodes[e.dst].cy) / 2.0
        side = "up" if ym < mid else "dn"
        if side == "up" and n_up > n_dn + 1:
            side = "dn"
        elif side == "dn" and n_dn > n_up + 1:
            side = "up"
        e.lane_side = side
        if side == "up":
            e.lane_y = top - 4.0 - 3.0 * n_up
            n_up += 1
        else:
            e.lane_y = bot + 4.0 + 3.0 * n_dn
            n_dn += 1
    layout.top = top - (4.0 + 3.0 * (n_up - 1) + 2.0 if n_up else 0.0)
    layout.bottom = bot + (4.0 + 3.0 * (n_dn - 1) + 2.0 if n_dn else 0.0)


class _Bundle:
    def __init__(self, key, yl):
        self.key = key
        self.yl = yl
        self.yrs = []
        self.segs = []
        self.x = None

    def finish(self):
        ys = [self.yl] + self.yrs
        self.ymin = min(ys)
        self.ymax = max(ys)


def _seg_y(e, a, b, side_a="out", side_b="in"):
    ya = a.port_y("out", e.key) if a.real else a.cy
    yb = b.port_y("in", e.key) if b.real else b.cy
    return ya, yb


def _plan_bundles(layout):
    """Group routed segments into per-gap channel bundles and order them."""
    nl = len(layout.layers)
    raw = {g: [] for g in range(nl + 1)}
    for e in layout.edges:
        if e.back:
            s, d = layout.nodes[e.src], layout.nodes[e.dst]
            ya = s.port_y("out", e.key)
            yb = d.port_y("in", e.key)
            raw[s.layer + 1].append((e, "bs", ya, e.lane_y, ("bs", e.eid)))
            raw[d.layer].append((e, "bd", e.lane_y, yb, ("bd", e.eid)))
            continue
        for i, (a, b) in enumerate(zip(e.chain[:-1], e.chain[1:])):
            ya, yb = _seg_y(e, a, b)
            raw[b.layer].append((e, i, ya, yb, ("f", e.key, round(ya, 2), a.id if a.real else e.eid)))
    out = {}
    straight = {}
    for g in range(nl + 1):
        bundles = {}
        order = []
        st = []
        for e, i, ya, yb, gk in raw[g]:
            if abs(ya - yb) < 0.15:
                st.append((e, i, ya))
                continue
            bd = bundles.get(gk)
            if bd is None:
                bd = _Bundle(e.key, ya)
                bundles[gk] = bd
                order.append(bd)
            bd.yrs.append(yb)
            bd.segs.append((e, i, ya, yb))
        for bd in order:
            bd.finish()
        out[g] = _order_bundles(order)
        straight[g] = st
    return out, straight


def _cross_cost(a, b):
    """Crossing penalty if bundle ``a`` is drawn left of bundle ``b``."""
    eps = 0.05
    c = 0
    for yr in a.yrs:
        if b.ymin + eps < yr < b.ymax - eps:
            c += 1
        if abs(yr - b.yl) < 0.3:
            c += 4
    if a.ymin + eps < b.yl < a.ymax - eps:
        c += 1
    return c


def _order_bundles(bundles):
    if len(bundles) < 2:
        return list(bundles)

    def key(b):
        down = _mean(b.yrs) > b.yl
        return (0 if down else 1, -b.yl if down else b.yl)

    seq = sorted(bundles, key=key)
    placed = []
    for b in seq:
        best_p, best_c = len(placed), None
        for p in range(len(placed), -1, -1):
            c = sum(_cross_cost(x, b) for x in placed[:p]) + sum(_cross_cost(b, x) for x in placed[p:])
            if best_c is None or c < best_c:
                best_c, best_p = c, p
        placed.insert(best_p, b)
    for _ in range(4):
        improved = False
        for i in range(len(placed) - 1):
            a, b = placed[i], placed[i + 1]
            if _cross_cost(b, a) < _cross_cost(a, b):
                placed[i], placed[i + 1] = b, a
                improved = True
        if not improved:
            break
    return placed


def _assign_x(layout, target_w=None):
    """Determine layer widths, gap widths and node x-coordinates."""
    cfg = layout.cfg
    nl = len(layout.layers)
    bundles, straight = _plan_bundles(layout)
    layout.bundles = bundles
    lw = []
    for layer in layout.layers:
        reals = [n for n in layer if n.real]
        lw.append(max((n.w for n in reals), default=0.0))
    gaps = []
    stack_pitch = cfg.get("stack_pitch", 0.0)
    for g in range(nl + 1):
        n = len(bundles[g])
        if g == 0 or g == nl:
            need = 3.0 + (CH_PITCH * n + 3.0 if n else 0.0)
        else:
            need = cfg.get("gap_base", 10.0) + CH_PITCH * max(n - 1, 0)
            if stack_pitch and straight[g]:
                ys = sorted(s[2] for s in straight[g])
                best = 0
                for i, y in enumerate(ys):
                    c = sum(1 for j, y2 in enumerate(ys) if j != i and abs(y2 - y) < DIAMOND)
                    best = max(best, c)
                need += stack_pitch * min(best, 2)
            lab = cfg.get("gap_label")
            if lab:
                need = max(need, lab(g, bundles[g], straight[g]) + 6.0 + CH_PITCH * max(n - 1, 0))
        gaps.append(need)
    total = sum(gaps) + sum(lw)
    layout.natural_w = total
    if target_w and total < target_w and nl > 1:
        extra = min((target_w - total) / (nl - 1), cfg.get("max_stretch", 12.0))
        for g in range(1, nl):
            gaps[g] += extra
        total = sum(gaps) + sum(lw)
    layout.gap_w = gaps
    layout.layer_w = lw
    x = 0.0
    layout.lx, layout.rx = [], []
    for L in range(nl):
        x += gaps[L]
        layout.lx.append(x)
        layout.rx.append(x + lw[L])
        for n in layout.layers[L]:
            n.cx = x + lw[L] / 2.0
            if not n.real or n.kind in ("feed", "product"):
                n.w = lw[L]
        x += lw[L]
    x += gaps[nl]
    layout.width = x


def _layer_bounds(layout, L):
    reals = [n for n in layout.layers[L] if n.real]
    if reals:
        return min(n.cx - n.w / 2 for n in reals), max(n.cx + n.w / 2 for n in reals)
    return layout.lx[L], layout.rx[L]


def _route(layout):
    """Compute channel x-positions and polylines from current node positions."""
    nl = len(layout.layers)
    bundles, _ = _plan_bundles(layout)
    layout.bundles = bundles
    layout.dots = []
    for g in range(nl + 1):
        bl = bundles[g]
        if not bl:
            continue
        if g > 0:
            xl = _layer_bounds(layout, g - 1)[1]
        else:
            xl = _layer_bounds(layout, 0)[0] - layout.gap_w[0]
        if g < nl:
            xr = _layer_bounds(layout, g)[0]
        else:
            xr = _layer_bounds(layout, nl - 1)[1] + layout.gap_w[nl]
        n = len(bl)
        usable = xr - xl
        if usable < 2 * 2.0 + (n - 1) * 1.0:
            layout.warnings.append("gap %d too narrow for %d channels" % (g, n))
            xs = [xl + usable * (i + 1) / (n + 1) for i in range(n)]
        else:
            margin = min(3.5, (usable - (n - 1) * CH_PITCH) / 2.0)
            if usable - 2 * margin >= (n - 1) * CH_PITCH:
                pitch = CH_PITCH
                start = xl + (usable - (n - 1) * pitch) / 2.0
            else:
                pitch = (usable - 2 * margin) / (n - 1) if n > 1 else 0.0
                start = xl + margin
            xs = [start + i * pitch for i in range(n)]
        for b, x in zip(bl, xs):
            b.x = x
            if len(b.segs) > 1:
                layout.dots.append((x, b.yl, b.key))
    # lookup: (edge eid, seg index or kind) -> channel x
    chx = {}
    for g in range(nl + 1):
        for b in bundles[g]:
            for e, i, ya, yb in b.segs:
                chx[(e.eid, i)] = b.x
    for e in layout.edges:
        s, d = layout.nodes[e.src], layout.nodes[e.dst]
        if e.back:
            xs_ = chx.get((e.eid, "bs"))
            xd_ = chx.get((e.eid, "bd"))
            p0 = s.pxy("out", e.key)
            p1 = d.pxy("in", e.key)
            pts = [p0, (xs_, p0[1]), (xs_, e.lane_y), (xd_, e.lane_y), (xd_, p1[1]), p1]
        else:
            x0, y0 = s.pxy("out", e.key)
            pts = [(x0, y0)]
            for i, (a, b) in enumerate(zip(e.chain[:-1], e.chain[1:])):
                ya, yb = _seg_y(e, a, b)
                x = chx.get((e.eid, i))
                if x is not None:
                    pts.append((x, ya))
                    pts.append((x, yb))
                elif abs(ya - yb) >= 0.15:
                    pts.append((b.cx - b.w / 2, ya))
                    pts.append((b.cx - b.w / 2, yb))
            xe, ye = d.pxy("in", e.key)
            pts.append((xe, ye))
        e.pts = _simplify(pts)


def _simplify(pts):
    snapped = []
    for p in pts:
        if snapped:
            q = snapped[-1]
            if abs(p[1] - q[1]) < 0.15 and abs(p[0] - q[0]) >= 0.15:
                p = (p[0], q[1])
            elif abs(p[0] - q[0]) < 0.15 and abs(p[1] - q[1]) >= 0.15:
                p = (q[0], p[1])
        snapped.append(p)
    out = []
    for p in snapped:
        if out and abs(out[-1][0] - p[0]) < 1e-6 and abs(out[-1][1] - p[1]) < 1e-6:
            continue
        out.append(p)
    res = []
    for p in out:
        while len(res) >= 2:
            a, b = res[-2], res[-1]
            if (abs(a[0] - b[0]) < 1e-6 and abs(b[0] - p[0]) < 1e-6) or \
                    (abs(a[1] - b[1]) < 1e-6 and abs(b[1] - p[1]) < 1e-6):
                res.pop()
            else:
                break
        res.append(p)
    return res


def _shift(layout, dx, dy):
    for n in layout.nodes.values():
        n.cx += dx
        n.cy += dy
    for e in layout.edges:
        e.pts = [(x + dx, y + dy) for x, y in e.pts]
        if e.lane_y is not None:
            e.lane_y += dy
    layout.dots = [(x + dx, y + dy, k) for x, y, k in layout.dots]
    layout.lx = [v + dx for v in layout.lx]
    layout.rx = [v + dx for v in layout.rx]
    layout.top += dy
    layout.bottom += dy


def compute_layout(nodes, edges, cfg, target_w=None):
    """Run the full layered layout for the given nodes and edges.

    The result has node positions in a local frame whose x origin is the left
    edge of the drawing and whose y origin is the top of the drawing
    (including back-edge lanes).
    """
    layout = _Layout(nodes, edges, cfg)
    units = [n for n in nodes if n.kind in ("unit", "block")]
    rank_cfg = cfg.get("rank")
    if rank_cfg:
        uids = {n.id for n in units}
        topo = sorted((n.id for n in units), key=lambda u: rank_cfg[u])
        back = {e.eid for e in edges if e.src in uids and e.dst in uids and rank_cfg[e.dst] <= rank_cfg[e.src]}
    else:
        topo, back = _cycle_removal(units, edges)
    _assign_layers(layout, units, topo, back)
    _build_chains(layout, back)
    # initial order: topological position for units, creation order for the rest
    rank = {u: i for i, u in enumerate(topo)}
    for layer in layout.layers:
        layer.sort(key=lambda n: (rank.get(n.id, 10 ** 6), 0))
    # stable: keep creation order for non-units (sort is stable on equal keys)
    _order_layers(layout, cfg.get("sweeps", 12))
    _assign_y(layout)
    _straighten(layout)
    _assign_lanes(layout)
    _assign_ports(layout, final=True)
    _assign_x(layout, target_w)
    _route(layout)
    # normalise: top-left of bounding box at (0, 0)
    _shift(layout, 0.0, -layout.top)
    layout.height = layout.bottom - layout.top
    return layout


# ---------------------------------------------------------------------------
# collision-aware mark placement (stream diamonds, edge labels, mid arrows)
# ---------------------------------------------------------------------------
def _axis_segments(edges):
    """All axis-aligned segments of all edges as tuples."""
    segs = []
    for e in edges:
        for i, (p, q) in enumerate(zip(e.pts[:-1], e.pts[1:])):
            if abs(p[1] - q[1]) < 1e-6:
                segs.append((e.eid, e.key, "h", p[1], min(p[0], q[0]), max(p[0], q[0]), i))
            else:
                segs.append((e.eid, e.key, "v", p[0], min(p[1], q[1]), max(p[1], q[1]), i))
    return segs


def _rects_overlap(a, b, m=0.0):
    return a[0] < b[2] + m and a[2] > b[0] - m and a[1] < b[3] + m and a[3] > b[1] - m


def _rect_free(r, rects, segs, host=None, host_key=None, host_c=None, margin=0.5):
    for o in rects:
        if _rects_overlap(r, o, margin):
            return False
    eps = 0.05
    for s in segs:
        if host is not None and s[0] == host[0] and s[6] == host[6]:
            continue
        orient, c, lo, hi = s[2], s[3], s[4], s[5]
        if orient == "h":
            if host_key is not None and s[1] == host_key and host_c is not None and abs(c - host_c) < 0.05:
                continue
            if r[1] + eps < c < r[3] - eps and lo < r[2] - eps and hi > r[0] + eps:
                return False
        else:
            if r[0] + eps < c < r[2] - eps and lo < r[3] - eps and hi > r[1] + eps:
                return False
    return True


def _overlap_cost(r, rects, segs, host):
    """Penalty of putting rectangle ``r`` on top of obstacles (fallback placement)."""
    c = 0.0
    for o in rects:
        ox = min(r[2], o[2]) - max(r[0], o[0])
        oy = min(r[3], o[3]) - max(r[1], o[1])
        if ox > 0 and oy > 0:
            c += ox * oy
    for s in segs:
        if s[0] == host[0] and s[6] == host[6]:
            continue
        if s[2] == "h":
            if r[1] < s[3] < r[3] and s[4] < r[2] and s[5] > r[0]:
                c += 6.0
        elif r[0] < s[3] < r[2] and s[4] < r[3] and s[5] > r[1]:
            c += 6.0
    return c


def _place_marks(marks, edges, rects, w, h, mode="center", step=0.5, edge_margin=0.8):
    """Place one rectangular mark per entry on the longest free horizontal segment.

    Args:
        marks: list of dicts with ``key``, ``edges`` (preference order) and optional
            ``w``/``h``/``side`` overrides.
        edges: all routed edges (obstacle lines).
        rects: static obstacle rectangles.
        w, h: default mark size.
        mode: ``center`` (mark centred on the line) or ``above`` / ``below``.

    Returns:
        (dict key -> (x, y, rect), list of keys that had to use a fallback)
    """
    segs = _axis_segments(edges)
    placed = []
    out = {}
    fallback = []
    for m in marks:
        variants = m.get("variants") or [{"w": m.get("w", w), "h": m.get("h", h), "mode": m.get("mode", mode)}]
        found = None
        for vi, var in enumerate(variants):
            mw, mh, md = var["w"], var["h"], var["mode"]
            for e in m["edges"]:
                hs = [s for s in segs if s[0] == e.eid and s[2] == "h" and s[5] - s[4] >= mw + 2 * edge_margin]
                hs.sort(key=lambda s: (-(s[5] - s[4]), s[6]))
                for s in hs:
                    mid = (s[4] + s[5]) / 2.0
                    n = int((s[5] - s[4] - mw - 2 * edge_margin) / step) + 1
                    xs = [s[4] + mw / 2 + edge_margin + i * step for i in range(n)]
                    xs.sort(key=lambda x: (abs(x - mid), x))
                    for x in xs:
                        y = s[3]
                        if md == "center":
                            r = (x - mw / 2, y - mh / 2, x + mw / 2, y + mh / 2)
                            ok = _rect_free(r, rects + placed, segs, host=s, host_key=e.key, host_c=y)
                        else:
                            r = (x - mw / 2, y - mh - 0.5, x + mw / 2, y - 0.5) if md == "above" else \
                                (x - mw / 2, y + 0.5, x + mw / 2, y + mh + 0.5)
                            ok = _rect_free(r, rects + placed, segs)
                        if ok:
                            found = (x, y, r, vi)
                            break
                    if found:
                        break
                if found:
                    break
            if found:
                break
        fv = len(variants) - 1 if m.get("fallback_last") else 0
        mw = variants[fv]["w"]
        mh = variants[fv]["h"]
        if found is None:
            fallback.append(m["key"])
            best_c = None
            for e in m["edges"]:
                for s in segs:
                    if s[0] != e.eid or s[2] != "h":
                        continue
                    n = max(int((s[5] - s[4]) / 1.0), 1)
                    for i in range(n + 1):
                        x = s[4] + (s[5] - s[4]) * i / float(n)
                        y = s[3]
                        if variants[fv]["mode"] == "center":
                            r = (x - mw / 2, y - mh / 2, x + mw / 2, y + mh / 2)
                        else:
                            r = (x - mw / 2, y - mh - 0.5, x + mw / 2, y - 0.5)
                        c = _overlap_cost(r, rects + placed, segs, s)
                        if best_c is None or c < best_c:
                            best_c, found = c, (x, y, r, fv)
        if found is not None:
            out[m["key"]] = found
            placed.append(found[2])
    return out, fallback


def _end_rects(edges):
    """Rectangles covered by the arrowhead at the end of every path."""
    out = []
    for e in edges:
        (x1, y1), (x2, y2) = e.pts[-2], e.pts[-1]
        n = math.hypot(x2 - x1, y2 - y1) or 1.0
        ux, uy = (x2 - x1) / n, (y2 - y1) / n
        bx, by = x2 - ux * (ARROW_L + 0.6), y2 - uy * (ARROW_L + 0.6)
        out.append((min(bx, x2) - 0.8 * abs(uy), min(by, y2) - 0.8 * abs(ux),
                    max(bx, x2) + 0.8 * abs(uy), max(by, y2) + 0.8 * abs(ux)))
    return out


def _mid_arrows(edges, rects, marks_rects, min_h=22.0, min_v=26.0):
    """Choose positions for additional mid-segment flow arrows."""
    segs = _axis_segments(edges)
    obstacles = rects + marks_rects
    arrows = []
    seen = set()
    for e in edges:
        for i, (p, q) in enumerate(zip(e.pts[:-1], e.pts[1:])):
            horiz = abs(p[1] - q[1]) < 1e-6
            length = abs(q[0] - p[0]) if horiz else abs(q[1] - p[1])
            if length < (min_h if horiz else min_v):
                continue
            ux = (1 if q[0] > p[0] else -1) if horiz else 0
            uy = 0 if horiz else (1 if q[1] > p[1] else -1)
            found = None
            fr = [0.5, 0.42, 0.58, 0.34, 0.66, 0.28, 0.72]
            for f in fr:
                x, y = p[0] + (q[0] - p[0]) * f, p[1] + (q[1] - p[1]) * f
                if horiz:
                    r = (x - 1.4, y - 1.2, x + 1.4, y + 1.2)
                else:
                    r = (x - 1.2, y - 1.4, x + 1.2, y + 1.4)
                host = (e.eid, None, None, None, None, None, i)
                if _rect_free(r, obstacles, segs, host=host, host_key=e.key, host_c=(y if horiz else x)):
                    found = (x, y)
                    break
            if found:
                key = (round(found[0], 1), round(found[1], 1), ux, uy)
                if key not in seen:
                    seen.add(key)
                    arrows.append((found[0] + ux * ARROW_L / 2.0, found[1] + uy * ARROW_L / 2.0, ux, uy, e.key))
    return arrows


# ---------------------------------------------------------------------------
# data model
# ---------------------------------------------------------------------------
class _Unit:
    def __init__(self, e):
        self.tag = str(e.get("tag"))
        self.name = e.get("name") or self.tag
        self.area = e.get("area")
        self.java_class = e.get("java_class") or ""
        self.category = e.get("category") or "other"
        self.ins = list(e.get("inlet_streams") or [])
        self.outs = list(e.get("outlet_streams") or [])


class _Stream:
    def __init__(self, s):
        self.id = str(s.get("id"))
        self.name = s.get("name") or self.id
        self.label = s.get("label") or self.name
        self.kind = s.get("kind") or "internal"
        self.producers = []
        self.consumers = []
        self.results = s.get("results") or {}
        self.area = s.get("area")
        self.raw = s

    @property
    def producer(self):
        return self.producers[0] if self.producers else None


class _Model:
    """Normalised process data used by the drawing builders."""

    def __init__(self, data):
        self.warnings = []
        self.cases = data.get("cases") or [{"id": "A", "label": "A"}]
        self.case0 = self.cases[0].get("id", "A")
        self.meta = data.get("meta") or {}
        units = [_Unit(e) for e in data.get("equipment", []) if e.get("category") != "logic"]
        self.unit = {}
        for u in units:
            if u.tag in self.unit:
                self.warnings.append("duplicate tag %s ignored" % u.tag)
                continue
            self.unit[u.tag] = u
        self.areas = []
        assigned = set()
        names = []
        for a in data.get("areas", []):
            tags = []
            for t in a.get("units", []):
                if t in self.unit and t not in assigned:
                    tags.append(t)
                    assigned.add(t)
            names.append(a.get("name"))
            self.areas.append([a.get("name"), tags])
        rest = [t for t in self.unit if t not in assigned]
        for t in rest:
            an = self.unit[t].area
            tgt = None
            for a in self.areas:
                if a[0] == an:
                    tgt = a
                    break
            if tgt is None:
                tgt = next((a for a in self.areas if a[0] == "unassigned"), None)
                if tgt is None:
                    tgt = ["unassigned", []]
                    self.areas.append(tgt)
            tgt[1].append(t)
        self.areas = [a for a in self.areas if a[1]]
        self.streams = []
        self.stream = {}
        for s in data.get("streams", []):
            st = _Stream(s)
            if st.id in self.stream:
                self.warnings.append("duplicate stream %s ignored" % st.id)
                continue
            prod = [t for t in (s.get("producers") or []) if t in self.unit]
            if not prod and s.get("source") in self.unit:
                prod = [s.get("source")]
            cons = [t for t in (s.get("consumers") or []) if t in self.unit]
            if not cons and s.get("destination") in self.unit:
                cons = [s.get("destination")]
            st.producers = prod
            st.consumers = cons
            self.streams.append(st)
            self.stream[st.id] = st
        for u in self.unit.values():
            for sid in u.outs:
                st = self.stream.get(sid)
                if st is not None and u.tag not in st.producers:
                    st.producers.append(u.tag)
            for sid in u.ins:
                st = self.stream.get(sid)
                if st is not None and u.tag not in st.consumers:
                    st.consumers.append(u.tag)
        for st in self.streams:
            if len(st.producers) > 1:
                self.warnings.append("stream %s has several producers; only the first is drawn" % st.id)
        # per-unit ordered stream lists
        self.unit_in = {t: [] for t in self.unit}
        self.unit_out = {t: [] for t in self.unit}
        for st in self.streams:
            if st.producer:
                self.unit_out[st.producer].append(st.id)
            for c in st.consumers:
                if st.id not in self.unit_in[c]:
                    self.unit_in[c].append(st.id)

    def phase_rank(self, sid):
        """Dominant phase rank of a stream: 0 gas, 1 oil/other, 2 aqueous."""
        st = self.stream[sid]
        res = (st.results or {}).get(self.case0) or next(iter((st.results or {}).values()), None) or {}
        phases = res.get("phases") or []
        best = None
        for ph in phases:
            v = ph.get("mass_fraction")
            if v is None:
                v = ph.get("mole_fraction")
            if v is None:
                continue
            if best is None or v > best[0]:
                best = (v, ph.get("type"))
        if best is not None:
            return {"gas": 0, "oil": 1, "aqueous": 2}.get(best[1], 1)
        state = str(res.get("state") or "")
        if state == "gas":
            return 0
        if state in ("aqueous", "water"):
            return 2
        return 1


class _Plan:
    """Assignment of units and streams to sheets."""

    def __init__(self, model, parts):
        self.sheet_of = {}
        for i, p in enumerate(parts):
            for t in p["tags"]:
                self.sheet_of[t] = i
        self.home = {}
        self.cons_sheets = {}
        for st in model.streams:
            cs = sorted({self.sheet_of[c] for c in st.consumers if c in self.sheet_of})
            self.cons_sheets[st.id] = cs
            if st.producer in self.sheet_of:
                self.home[st.id] = self.sheet_of[st.producer]
            elif cs:
                first = next(self.sheet_of[c] for c in st.consumers if c in self.sheet_of)
                self.home[st.id] = first
            else:
                self.home[st.id] = 0


def _desc_word(u):
    if u.category == "separator":
        if "ThreePhase" in u.java_class:
            return "3-phase separator"
        if "Scrubber" in u.java_class:
            return "gas scrubber"
        return "separator"
    if u.category == "recycle":
        return "recycle (numerical)"
    return u.category


# ---------------------------------------------------------------------------
# sheet graph construction and layout
# ---------------------------------------------------------------------------
CONN_W = 24.0
CONN_H = 10.0


def _clean_equipment_data(equipment_data):
    """Normalise ``{tag: [lines]}``: ASCII, at most 3 lines of 34 characters, empty lines dropped."""
    out = {}
    for tag, lines in (equipment_data or {}).items():
        if isinstance(lines, str):
            lines = [lines]
        try:
            items = list(lines)
        except TypeError:
            continue
        clean = [_short(x, EQ_MAX_CHARS).strip() for x in items if x is not None]
        clean = [x for x in clean if x][:EQ_MAX_LINES]
        if clean:
            out[str(tag)] = clean
    return out


def _unit_node(model, u, scale):
    sym = make_symbol(u.category, u.java_class, scale, len(model.unit_in[u.tag]), len(model.unit_out[u.tag]))
    lines = _wrap(u.tag, TAG_FS, 26.0, bold=True)
    tag_w = max(text_w(t, TAG_FS, True) for t, _ in lines)
    desc = _desc_word(u)
    data_lines = (getattr(model, "eq_data", None) or {}).get(u.tag, [])
    data_w = max([text_w(t, EQ_FS) for t in data_lines], default=0.0)
    w = max(sym.w, tag_w, text_w(desc, DESC_FS), sym.extra_width(), data_w) + 2.0
    above = sym.h / 2.0 + len(lines) * 3.1 + 0.9
    below = sym.h / 2.0 + sym.extra_below() + 3.8 + (len(data_lines) * EQ_PITCH + 0.6 if data_lines else 0.0)
    n = _Node("U:" + u.tag, "unit", w, sym.h, above, below, sym=sym,
              payload={"tag": u.tag, "lines": lines, "desc": desc, "unit": u, "tag_w": tag_w,
                       "data_lines": data_lines})
    if u.category == "separator":
        n.fixed_out = True
        for sid in model.unit_out[u.tag]:
            n.out_rank[sid] = model.phase_rank(sid)
    return n


def _build_graph(model, tags, k, plan, scale, sheet_labels):
    """Nodes and edges of sheet ``k`` (unit tags ``tags``)."""
    tagset = set(tags)
    nodes = []
    edges = []
    nmap = {}
    for t in tags:
        n = _unit_node(model, model.unit[t], scale)
        nodes.append(n)
        nmap[t] = n

    def boundary(kind, sid, label):
        lab = _short(label, 28)
        w = max(14.0, text_w(lab, LABEL_FS) + 2.0)
        nd = _Node(("F:" if kind == "feed" else "P:") + sid, kind, w, 0.0, 3.3, 1.2,
                   payload={"stream": sid, "label": lab})
        nodes.append(nd)
        return nd

    def conn(kind, sid, other, suffix=""):
        word = "from" if kind == "conn_in" else "to"
        dn_fn = getattr(model, "dn_fn", None)
        other_dn = dn_fn(other + 1) if dn_fn else "sheet %s" % sheet_labels(other)
        text = "%s sheet %s" % (word, sheet_labels(other))
        drg = "DRG %s" % other_dn
        w = max(CONN_W, max(text_w(text, DESC_FS, True), text_w(drg, DESC_FS), text_w(sid, DESC_FS)) + 6.4)
        nd = _Node(("CI:" if kind == "conn_in" else "CO:") + sid + suffix, kind, w, CONN_H,
                   CONN_H / 2.0, CONN_H / 2.0,
                   payload={"stream": sid, "other": other, "text": text, "drg": drg, "other_dn": other_dn})
        nodes.append(nd)
        return nd

    ecount = {}

    def add_edge(sid, a, b):
        i = ecount.get(sid, 0)
        ecount[sid] = i + 1
        edges.append(_Edge("E:%s:%d" % (sid, i), sid, a.id, b.id))

    for st in model.streams:
        home = plan.home[st.id]
        cons_here = [c for c in st.consumers if c in tagset]
        src = None
        owner_here = False
        if st.producer and st.producer in tagset:
            src = nmap[st.producer]
            owner_here = True
        elif st.producer is None and home == k:
            src = boundary("feed", st.id, st.label)
            owner_here = True
        elif cons_here:
            src = conn("conn_in", st.id, home)
        else:
            continue
        for c in cons_here:
            add_edge(st.id, src, nmap[c])
        if owner_here:
            for j in plan.cons_sheets[st.id]:
                if j != k:
                    add_edge(st.id, src, conn("conn_out", st.id, j, ":%d" % j))
            if not st.consumers:
                add_edge(st.id, src, boundary("product", st.id, st.label))
    return nodes, edges


def _layout_sheet(model, tags, k, plan, scale, sheet_labels):
    nodes, edges = _build_graph(model, tags, k, plan, scale, sheet_labels)
    cfg = {"stack_pitch": DIAMOND + 0.6, "gap_base": 10.0}
    layout = compute_layout(nodes, edges, cfg, target_w=AREA[2])
    return layout


def _fits(layout, scale=1.0):
    if scale > 1.0 and (layout.natural_w > 0.7 * AREA[2] or layout.height > 0.55 * AREA[3]):
        return False
    return layout.width <= AREA[2] + 1e-6 and layout.height <= AREA[3] + 1e-6


def _topo_tags(model, tags):
    """Topological order of unit tags within a sheet (cycle-removed DFS)."""
    tagset = set(tags)
    nodes = {t: _Node(t, "unit") for t in tags}
    edges = []
    for st in model.streams:
        if st.producer in tagset:
            for c in st.consumers:
                if c in tagset:
                    edges.append(_Edge("E:%s:%s" % (st.id, c), st.id, st.producer, c))
    topo, _ = _cycle_removal([nodes[t] for t in tags], edges)
    return topo


def _split_parts(model, parts, idx):
    p = parts[idx]
    order = _topo_tags(model, p["tags"])
    h = len(order) // 2
    a = {"tags": order[:h], "title": p["title"], "areas": p.get("areas", [])}
    b = {"tags": order[h:], "title": p["title"], "areas": p.get("areas", [])}
    return parts[:idx] + [a, b] + parts[idx + 1:]


def _plan_sheets(model, sheets, max_units):
    """Initial partition of units over sheets."""
    parts = []
    if sheets:
        used = set()
        for sh in sheets:
            tags = []
            for an in sh.get("areas", []):
                for a in model.areas:
                    if a[0] == an:
                        tags.extend(t for t in a[1] if t not in used)
            used.update(tags)
            if tags:
                parts.append({"tags": tags, "title": sh.get("title") or ", ".join(sh.get("areas", [])),
                              "areas": list(sh.get("areas", [])), "explicit": True})
        rest = [t for a in model.areas for t in a[1] if t not in used]
        if rest:
            model.warnings.append("units not in explicit sheets were placed on an extra sheet")
            parts.append({"tags": rest, "title": "Other units", "areas": [], "explicit": True})
        return parts
    cur = None
    for name, tags in model.areas:
        if cur is not None and len(cur["tags"]) + len(tags) > max_units:
            parts.append(cur)
            cur = None
        if cur is None:
            cur = {"tags": [], "areas": [], "title": ""}
        cur["tags"].extend(tags)
        cur["areas"].append(name)
    if cur is not None:
        parts.append(cur)
    for p in parts:
        p["title"] = _area_title(p["areas"])
    return parts


def _area_title(names):
    names = [str(n) for n in names if n]
    if not names:
        return "Process"
    names = [n[:1].upper() + n[1:] for n in names]
    if len(names) > 3:
        return "%s to %s (%d areas)" % (names[0], names[-1], len(names))
    t = " / ".join(names)
    return t if len(t) <= 80 else t[:77] + "..."


def _fit_partition(model, parts, warnings):
    """Split sheets until every sheet fits the drawing area at a readable scale."""
    label = lambda other: str(other + 1)
    for _round in range(12):
        plan = _Plan(model, parts)
        bad = []
        layouts = []
        for i, p in enumerate(parts):
            chosen = None
            lay = None
            for s in SCALES:
                lay = _layout_sheet(model, p["tags"], i, plan, s, label)
                if _fits(lay, s):
                    chosen = s
                    break
            layouts.append((chosen if chosen is not None else SCALES[-1], lay))
            if chosen is None:
                if len(p["tags"]) > 1:
                    bad.append(i)
                else:
                    warnings.append("sheet %d does not fit at the minimum readable scale" % (i + 1))
        if not bad:
            return parts, layouts, plan
        for i in reversed(bad):
            parts = _split_parts(model, parts, i)
    warnings.append("sheet splitting stopped after 12 rounds; some sheets may not fit")
    plan = _Plan(model, parts)
    layouts = [(SCALES[-1], _layout_sheet(model, p["tags"], i, plan, SCALES[-1], label))
               for i, p in enumerate(parts)]
    return parts, layouts, plan


# ---------------------------------------------------------------------------
# page furniture
# ---------------------------------------------------------------------------
def _resolve_tb(tb, data, kind):
    tb = dict(tb or {})
    meta = (data.get("meta") or {})
    na = "not assigned"
    out = {
        "project": tb.get("project") or meta.get("model_name") or na,
        "title": tb.get("title") or ("Block flow diagram" if kind == "BFD" else "Process flow diagram"),
        "document_number": str(tb.get("document_number") or "DRAFT"),
        "revision": str(tb.get("revision") or na),
        "issue_status": tb.get("issue_status") or "DRAFT",
        "date": tb.get("date") or na,
        "drawn_by": tb.get("drawn_by") or na,
        "checked_by": tb.get("checked_by") or na,
        "approved_by": tb.get("approved_by") or na,
        "company": tb.get("company") or na,
        "readiness_note": tb.get("readiness_note") or "",
        "revision_history": list(tb.get("revision_history") or []),
        "notes": [str(x) for x in (tb.get("notes") or [])],
        "sheet_number_prefix": tb.get("sheet_number_prefix") or kind,
        "omitted_systems": [str(x) for x in (tb.get("omitted_systems") or [])],
        "case_note": str(tb.get("case_note") or ""),
        "utility_note": str(tb.get("utility_note") or ""),
    }
    return out


def _safe_name(s):
    return "".join(c if (c.isalnum() or c in "._-") else "_" for c in s)


def _frame_els():
    return [_rect(5, 5, 410, 287, fill="none", sw=0.5), _rect(7, 7, 406, 283, fill="none", sw=SW)]


def _fit_text(s, max_w, size, bold=False, min_size=1.8):
    s = _ascii(s)
    while size > min_size and text_w(s, size, bold) > max_w:
        size -= 0.1
    if text_w(s, size, bold) > max_w:
        while len(s) > 4 and text_w(s + "...", size, bold) > max_w:
            s = s[:-1]
        s += "..."
    return s, size


TB_X, TB_Y, TB_W = 263.0, 253.0, 148.0


def _title_block_els(tb, drawing_number, sheet_i, sheet_n, title):
    els = []
    rh = 7.0

    def cell(x, y, cw, label, value, size=2.8, bold=False):
        els.append(_rect(x, y, cw, rh, fill="#fff"))
        els.append(_text(x + 1.0, y + 2.5, label, 1.8, fill=GREY))
        v, sz = _fit_text(value, cw - 2.0, size, bold)
        els.append(_text(x + 1.0, y + 5.9, v, sz, bold=bold))

    x0, y0 = TB_X, TB_Y
    cell(x0, y0, 74, "Company", tb["company"])
    cell(x0 + 74, y0, 74, "Project", tb["project"])
    cell(x0, y0 + rh, TB_W, "Title", "%s - %s" % (tb["title"], title) if title else tb["title"], 3.4, True)
    cell(x0, y0 + 2 * rh, 74, "Drawing number", drawing_number, 3.0, True)
    cell(x0 + 74, y0 + 2 * rh, 24, "Revision", tb["revision"])
    cell(x0 + 98, y0 + 2 * rh, 50, "Status", tb["issue_status"], 2.8, True)
    cell(x0, y0 + 3 * rh, 37, "Sheet", "Sheet %d of %d" % (sheet_i, sheet_n))
    cell(x0 + 37, y0 + 3 * rh, 37, "Date", tb["date"])
    cell(x0 + 74, y0 + 3 * rh, 37, "Scale", "not to scale")
    cell(x0 + 111, y0 + 3 * rh, 37, "Format", "A3 landscape")
    cell(x0, y0 + 4 * rh, 49, "Drawn by", tb["drawn_by"])
    cell(x0 + 49, y0 + 4 * rh, 49, "Checked by", tb["checked_by"])
    cell(x0 + 98, y0 + 4 * rh, 50, "Approved by", tb["approved_by"])
    return els


def _rev_table_els(tb):
    els = []
    x0, y0 = TB_X, 233.0
    rh = 4.0
    cols = [14.0, 28.0, 106.0]
    hist = tb["revision_history"][-4:]
    if not hist:
        hist = [{"rev": "-", "date": "not assigned", "description": "No revision history supplied"}]
    rows = [("Rev", "Date", "Description")] + [
        (str(h.get("rev", "")), str(h.get("date", "")), str(h.get("description", ""))) for h in hist]
    els.append(_rect(x0, y0, TB_W, rh * 5, fill="#fff"))
    for i, row in enumerate(rows):
        y = y0 + rh * (i + 1) - 1.2
        x = x0
        for cw, val in zip(cols, row):
            v, sz = _fit_text(val, cw - 1.6, 2.0, bold=(i == 0), min_size=1.6)
            els.append(_text(x + 0.8, y, v, sz, bold=(i == 0)))
            x += cw
        if i > 0:
            els.append(_line(x0, y0 + rh * i, x0 + TB_W, y0 + rh * i, sw=0.2))
    xx = x0
    for cw in cols[:-1]:
        xx += cw
        els.append(_line(xx, y0, xx, y0 + rh * 5, sw=0.2))
    return els


def _notes_els(lines, x0=129.0, y0=233.0, w=132.0, h=55.0):
    els = [_rect(x0, y0, w, h, fill="#fff"), _text(x0 + 1.5, y0 + 3.6, "GENERAL NOTES", 2.4, bold=True)]
    y = y0 + 7.0
    pitch = 2.75
    for ln in lines:
        wrapped = _wrap(ln, DESC_FS, w - 4.5, max_lines=6)
        for j, (t, _) in enumerate(wrapped):
            if y > y0 + h - 1.0:
                els.append(_text(x0 + w - 1.5, y0 + h - 1.2, "...", DESC_FS, "end"))
                return els
            els.append(_text(x0 + 1.5 + (0 if j == 0 else 3.0), y, t, DESC_FS))
            y += pitch
        y += 0.5
    return els


def _legend_els(entries, x0=9.0, y0=233.0, w=118.0, h=55.0):
    els = [_rect(x0, y0, w, h, fill="#fff"), _text(x0 + 1.5, y0 + 3.6, "LEGEND", 2.4, bold=True)]
    n = len(entries)
    cols = 2 if n <= 12 else 3
    rows = max(1, int(math.ceil(n / float(cols))))
    cw = (w - 3.0) / cols
    rh = min(8.0, (h - 6.5) / rows)
    for i, ent in enumerate(entries):
        c, r = divmod(i, rows)
        x = x0 + 1.5 + c * cw
        y = y0 + 6.0 + r * rh
        cx, cy = x + 7.0, y + rh / 2.0 - 0.2
        kind, key, label = ent
        if kind == "sym":
            sym = _mini_symbol(key)
            els.extend(sym.draw(cx, cy))
        elif key == "process":
            els.append(_line(x + 1.5, cy, x + 12.5, cy))
            els.append(_arrowhead(x + 12.5, cy, 1, 0))
        elif key == "diamond":
            els.extend(_diamond_els(cx, cy, "S001"))
        elif key == "utility":
            els.append(_dashed_poly([(x + 1.5, cy), (x + 12.5, cy)]))
        elif key == "offpage":
            els.append(_polygon([(x + 1.5, cy - 2.4), (x + 10.0, cy - 2.4), (x + 12.5, cy),
                                 (x + 10.0, cy + 2.4), (x + 1.5, cy + 2.4)]))
        elif key == "boundary":
            els.append(_line(x + 1.5, cy, x + 12.5, cy))
            els.append(_arrowhead(x + 12.5, cy, 1, 0))
            els.append(_text(x + 1.5, cy - 1.0, "label", 1.8, fill=GREY))
        elif key == "block":
            els.append(_rect(x + 1.5, cy - 3.0, 11.0, 6.0, rx=1.0, sw=0.5))
        elif key == "flow":
            els.append(_line(x + 1.5, cy, x + 12.5, cy))
            els.append(_arrowhead(x + 12.5, cy, 1, 0))
            els.append(_text(x + 1.5, cy - 1.0, "S001 1.2e5", 1.8, fill=GREY))
        elif key == "backflow":
            els.append(_polyline([(x + 12.5, cy + 2.0), (x + 12.5, cy - 2.2), (x + 2.0, cy - 2.2), (x + 2.0, cy + 1.2)]))
            els.append(_arrowhead(x + 2.0, cy + 2.0, 0, 1))
        elif key == "oconn":
            els.append(_circle(x + 7.0, cy, 3.0))
            els.append(_text(x + 7.0, cy + 1.0, "A", 2.8, "middle", bold=True))
        elif key == "notmodelled":
            els.append(_dashed_poly([(x + 1.5, cy - 2.5), (x + 12.5, cy - 2.5), (x + 12.5, cy + 2.5),
                                     (x + 1.5, cy + 2.5), (x + 1.5, cy - 2.5)], dash=1.5, gap=1.0))
        lines = _wrap(label, DESC_FS, cw - 17.0, max_lines=3)
        ty = cy + 0.7 - (len(lines) - 1) * 1.2
        for j, (t, _) in enumerate(lines):
            els.append(_text(x + 15.0, ty + j * 2.4, t, DESC_FS))
    return els


_MINI = {}


def _mini_symbol(key):
    """Small symbol for the legend (cached)."""
    if key in _MINI:
        return _MINI[key]
    spec = {
        "sep2": ("separator", ""), "sep3": ("separator", "ThreePhaseSeparator"),
        "scrubber": ("separator", "GasScrubber"), "compressor": ("compressor", ""),
        "expander": ("expander", ""), "pump": ("pump", ""), "heater": ("heater", ""),
        "cooler": ("cooler", ""), "exchanger": ("exchanger", ""), "valve": ("valve", ""),
        "mixer": ("mixer", ""), "splitter": ("splitter", ""), "ejector": ("ejector", ""),
        "column": ("column", ""), "recycle": ("recycle", ""), "pipe": ("pipe", ""),
        "reactor": ("reactor", ""), "generic": ("other", ""),
    }[key]
    big = make_symbol(spec[0], spec[1], 1.0, 2, 2)
    sc = min(0.5, 6.4 / max(big.w, big.h)) if key not in ("mixer", "splitter") else 1.0
    sym = make_symbol(spec[0], spec[1], sc, 2 if key == "mixer" else 1, 2 if key == "splitter" else 1)
    if key in ("mixer",):
        sym.h = 6.0
    if key == "splitter":
        sym.h = 6.0
    sym.extra = None
    _MINI[key] = sym
    return sym


def _diamond_font(sid, w=None, h=None):
    """Largest font size (<= DIAMOND_FS) whose text corners stay inside a ``w`` x ``h`` rhombus."""
    w = DIAMOND_W if w is None else w
    h = DIAMOND if h is None else h
    em = max(text_w(sid, 1.0), 0.5)
    margin = 0.88
    return max(1.0, min(DIAMOND_FS, margin / (em / w + 0.716 / h)))


def _diamond_els(cx, cy, sid, attrs=None, size=None):
    w, h = size if size else (DIAMOND_W, DIAMOND)
    fs = _diamond_font(sid, w, h)
    w, h = w / 2.0, h / 2.0
    extra = ' data-stream="%s"' % _esc(attrs) if attrs else ""
    return [_polygon([(cx, cy - h), (cx + w, cy), (cx, cy + h), (cx - w, cy)]),
            _text(cx, cy + 0.36 * fs, sid, fs, "middle", extra=extra)]


# ---------------------------------------------------------------------------
# sheet rendering
# ---------------------------------------------------------------------------
def _render_unit(n):
    u = n.payload["unit"]
    sym = n.sym
    cx, cy = n.cx, n.cy
    els = sym.draw(cx, cy)
    rects = [(cx - sym.w / 2, cy - sym.h / 2, cx + sym.w / 2, cy + sym.h / 2)]
    lines = n.payload["lines"]
    y_last = cy - sym.h / 2.0 - 0.9
    y0 = y_last - (len(lines) - 1) * 3.1
    els.append(_text_lines(cx, y0, lines, TAG_FS, 3.1, "middle", bold=True))
    tw = n.payload["tag_w"]
    rects.append((cx - tw / 2, y0 - 2.0, cx + tw / 2, y_last + 0.6))
    ex, er = sym.draw_extras(cx, cy)
    els.extend(ex)
    if er:
        rects.append(er)
    dy = cy + sym.h / 2.0 + sym.extra_below() + 3.0
    els.append(_text(cx, dy, n.payload["desc"], DESC_FS, "middle", fill=GREY))
    dw = text_w(n.payload["desc"], DESC_FS)
    rects.append((cx - dw / 2, dy - 1.8, cx + dw / 2, dy + 0.6))
    dl = n.payload.get("data_lines") or []
    for i, t in enumerate(dl):
        ty = dy + EQ_PITCH * (i + 1)
        els.append(_text(cx, ty, t, EQ_FS, "middle", fill=GREY))
        tw2 = text_w(t, EQ_FS)
        rects.append((cx - tw2 / 2, ty - 1.6, cx + tw2 / 2, ty + 0.6))
    return _g(els, data_tag=u.tag), rects


def _render_boundary(n, layout):
    left = n.cx - n.w / 2.0
    y = n.cy
    lab = n.payload["label"]
    els = [_text(left + 0.8, y - 0.9, lab, LABEL_FS)]
    r = (left + 0.8, y - 3.0, left + 0.8 + text_w(lab, LABEL_FS), y - 0.4)
    return _g(els, data_stream=n.payload["stream"]), [r]


def _render_conn(n):
    l, r = n.cx - n.w / 2.0, n.cx + n.w / 2.0
    t, b = n.cy - n.h / 2.0, n.cy + n.h / 2.0
    cxm = l + (r - 3.0 - l) / 2.0
    els = [_polygon([(l, t), (r - 3.0, t), (r, n.cy), (r - 3.0, b), (l, b)]),
           _text(cxm, n.cy - 2.0, n.payload["text"], DESC_FS, "middle", bold=True),
           _text(cxm, n.cy + 0.6, n.payload["drg"], DESC_FS, "middle"),
           _text(cxm, n.cy + 3.2, n.payload["stream"], DESC_FS, "middle", fill=GREY)]
    return _g(els, data_stream=n.payload["stream"]), [(l, t, r, b)]


def _legend_entries(model, tags, nodes):
    keys = []
    for n in nodes:
        if n.kind == "unit" and n.sym.key not in keys:
            keys.append(n.sym.key)
    order = list(LEGEND_NAMES.keys())
    keys.sort(key=order.index)
    entries = [("sym", k, LEGEND_NAMES[k]) for k in keys]
    entries.append(("line", "process", "Process stream, arrow shows flow direction"))
    entries.append(("line", "diamond", "Stream number (see stream tables)"))
    if any(n.kind in ("feed", "product") for n in nodes):
        entries.append(("line", "boundary", "Boundary feed / product, stream label above arrow"))
    if any(n.kind in ("conn_in", "conn_out") for n in nodes):
        entries.append(("line", "offpage", "Off-page connector, other sheet number and drawing number given"))
    if any(n.sym is not None and n.sym.extra for n in nodes if n.kind == "unit"):
        entries.append(("line", "utility", "Utility / shaft-power interface (dashed, not modelled)"))
    return entries


def _notes_lines(tb, extra_note=None):
    body = []
    if tb.get("case_note"):
        body.append(tb["case_note"])
    body.extend([
        "Stream numbers refer to the stream tables (appendices); equipment tags to the equipment register.",
        "Symbols: " + NOTE_ISO + ".",
        "Pressures bara.",
        "PFD shows simulated topology only.",
    ])
    if tb["omitted_systems"]:
        body.append("Omitted / not modelled systems: " + "; ".join(tb["omitted_systems"]) + ".")
    body.extend(tb["notes"])
    if tb.get("utility_note"):
        body.append(tb["utility_note"])
    if extra_note:
        body.append(extra_note)
    return ["%d. %s" % (i + 1, x) for i, x in enumerate(body)]


def _sheet_geometry(layout, model):
    """Render all nodes; returns (node svg list, obstacle rects)."""
    els = []
    rects = []
    for n in layout.real:
        if n.kind == "unit":
            g, r = _render_unit(n)
        elif n.kind in ("feed", "product"):
            g, r = _render_boundary(n, layout)
        else:
            g, r = _render_conn(n)
        els.append(g)
        rects.extend(r)
    return els, rects


def _render_sheet(sh, tb, n_sheets, model, warnings):
    """Compose the full SVG document for a sheet; fills sh['marks'] / sh['arrows']."""
    layout = sh["layout"]
    node_els, rects = _sheet_geometry(layout, model)
    by_key = {}
    for e in layout.edges:
        by_key.setdefault(e.key, []).append(e)
    variants = [{"w": DIAMOND_W, "h": DIAMOND, "mode": "center"},
                {"w": DIAMOND_S[0], "h": DIAMOND_S[1], "mode": "center"}]
    marks = [{"key": k, "edges": v, "variants": variants, "fallback_last": True} for k, v in by_key.items()]
    rects = rects + _end_rects(layout.edges)
    placed, fallback = _place_marks(marks, layout.edges, rects, DIAMOND_W, DIAMOND, "center")
    for k in fallback:
        warnings.append("sheet %d: stream %s diamond overlaps other drawing elements" % (sh["number"], k))
    arrows = _mid_arrows(layout.edges, rects, [v[2] for v in placed.values()])
    sh["marks"] = {k: (v[0], v[1]) for k, v in placed.items()}
    sh["mark_sizes"] = {k: (variants[v[3]]["w"], variants[v[3]]["h"]) for k, v in placed.items()}
    sh["arrow_count"] = len(arrows)
    out = ['<?xml version="1.0" encoding="UTF-8"?>',
           '<svg xmlns="http://www.w3.org/2000/svg" width="420mm" height="297mm" '
           'viewBox="0 0 420 297" version="1.1">',
           '<rect x="0" y="0" width="420" height="297" fill="#fff"/>']
    out.extend(_frame_els())
    heading = "%s  %s" % (sh["drawing_number"], sh["title"])
    out.append(_text(12.0, 13.6, heading, 3.6, bold=True))
    stream_g = []
    for k, edges in by_key.items():
        ch = []
        for e in edges:
            ch.append(_polyline(e.pts))
            (x1, y1), (x2, y2) = e.pts[-2], e.pts[-1]
            ch.append(_arrowhead(x2, y2, x2 - x1, y2 - y1))
        for x, y, key in layout.dots:
            if key == k:
                ch.append(_circle(x, y, 0.55, fill=INK, stroke=INK, sw=0.1))
        for ax, ay, ux, uy, key in arrows:
            if key == k:
                ch.append(_arrowhead(ax, ay, ux, uy))
        stream_g.append(_g(ch, data_stream=k))
    out.append(_g(stream_g, id="streams"))
    out.append(_g(node_els, id="equipment"))
    diamonds = []
    for k, (x, y) in sh["marks"].items():
        diamonds.append(_g(_diamond_els(x, y, k, attrs=k, size=sh["mark_sizes"][k]), data_stream=k))
    out.append(_g(diamonds, id="stream-numbers"))
    if tb["readiness_note"]:
        out.append(_text(AREA[0] + AREA[2] / 2.0, 229.4, tb["readiness_note"], 3.2, "middle", bold=True,
                         fill=RED))
    out.extend(_legend_els(_legend_entries(model, sh["tags"], layout.real)))
    out.extend(_notes_els(_notes_lines(tb)))
    out.extend(_rev_table_els(tb))
    out.extend(_title_block_els(tb, sh["drawing_number"], sh["number"], n_sheets, sh["title"]))
    out.append("</svg>")
    return "\n".join(out) + "\n"


def _finalize_sheet(layout, overrides, warnings, number):
    """Centre the layout on the page and apply optional symbol overrides."""
    dx = AREA[0] + (AREA[2] - layout.width) / 2.0
    dy = AREA[1] + (AREA[3] - layout.height) / 2.0
    _shift(layout, dx, dy)
    applied = {}
    for tag, pos in (overrides or {}).items():
        node = layout.nodes.get("U:" + str(tag))
        if node is None:
            continue
        if "x" in pos:
            node.cx = float(pos["x"])
        if "y" in pos:
            node.cy = float(pos["y"])
        applied[str(tag)] = {"x": node.cx, "y": node.cy}
    if applied:
        _assign_lanes(layout)
        _assign_ports(layout, final=True)
        _route(layout)
        for n in layout.real:
            if n.cx - n.w / 2 < 6 or n.cx + n.w / 2 > 414 or n.cy - n.above < 6 or n.cy + n.below > 232:
                warnings.append("sheet %d: override moved %s outside the drawing area" % (number, n.id))
    return applied


def _sheet_spec(sh, tb):
    layout = sh["layout"]
    units = []
    boundary = []
    conns = []
    for n in layout.real:
        if n.kind == "unit":
            u = n.payload["unit"]
            ports = {"in": [], "out": []}
            for side in ("in", "out"):
                for k in n.order[side]:
                    x, y = n.pxy(side, k)
                    ports[side].append({"stream": k, "x": round(x, 2), "y": round(y, 2)})
            units.append({"tag": u.tag, "category": u.category, "java_class": u.java_class,
                          "symbol": n.sym.key, "x": round(n.cx, 2), "y": round(n.cy, 2),
                          "w": round(n.sym.w, 2), "h": round(n.sym.h, 2), "ports": ports,
                          "data_lines": list(n.payload.get("data_lines") or [])})
        elif n.kind in ("feed", "product"):
            boundary.append({"stream": n.payload["stream"], "kind": n.kind, "label": n.payload["label"],
                             "x": round(n.cx, 2), "y": round(n.cy, 2), "w": round(n.w, 2)})
        else:
            conns.append({"stream": n.payload["stream"],
                          "direction": "in" if n.kind == "conn_in" else "out",
                          "other_sheet": n.payload["other"] + 1, "other_drawing_number": n.payload["other_dn"],
                          "text": n.payload["text"],
                          "x": round(n.cx, 2), "y": round(n.cy, 2)})
    paths = []
    for e in layout.edges:
        paths.append({"stream": e.key, "edge": e.eid, "from": e.src, "to": e.dst, "back_edge": bool(e.back),
                      "points": [[round(x, 2), round(y, 2)] for x, y in e.pts]})
    return {
        "number": sh["number"], "drawing_number": sh["drawing_number"], "title": sh["title"],
        "symbol_scale": sh["scale"], "drawing_area": list(AREA),
        "units": units, "boundary": boundary, "connectors": conns, "paths": paths,
        "diamonds": [{"stream": k, "x": round(x, 2), "y": round(y, 2)} for k, (x, y) in sh["marks"].items()],
        "title_block": {k: v for k, v in tb.items()},
    }


def _import_mupdf():
    try:
        import pymupdf  # type: ignore
        return pymupdf
    except ImportError:  # pragma: no cover
        import fitz  # type: ignore
        return fitz


def _svgs_to_pdf(svg_paths, pdf_path, png_paths, warnings, dpi=150):
    """Merge SVG sheets into one PDF and write PNG previews (rendered from the PDF)."""
    mu = _import_mupdf()
    doc = mu.open()
    for p in svg_paths:
        src = mu.open(p)
        sub = mu.open("pdf", src.convert_to_pdf())
        doc.insert_pdf(sub)
        sub.close()
        src.close()
    doc.save(pdf_path, deflate=True)
    for i, png in enumerate(png_paths):
        page = doc[i]
        if not page.get_text().strip():
            warnings.append("PDF page %d has no extractable text" % (i + 1))
        page.get_pixmap(dpi=dpi).save(png)
    doc.close()


def inventory_svg(path):
    """Parse ``data-tag`` / ``data-stream`` attributes of an SVG.

    Returns:
        dict with ``tags`` and ``streams`` sets.
    """
    from lxml import etree

    root = etree.parse(str(path)).getroot()
    tags, streams = set(), set()
    for el in root.iter():
        if not isinstance(el.tag, str):
            continue
        v = el.get("data-tag")
        if v is not None:
            tags.add(v)
        v = el.get("data-stream")
        if v is not None:
            streams.add(v)
    return {"tags": tags, "streams": streams}


def _write(path, text):
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(text)


def build_pfd(data, out_dir, title_block=None, sheets=None, max_units_per_sheet=14, layout_overrides=None,
              equipment_data=None):
    """Build the PFD drawing set (A3 landscape SVG sheets, PDF, PNG previews, layout spec).

    Args:
        data: extracted process data dict (areas, equipment, streams, cases).
        out_dir: output directory (created if missing).
        title_block: optional dict of title-block fields (see module docs); ``case_note`` is printed as
            the first general note and ``utility_note`` is appended to the general notes.
        sheets: optional explicit sheets ``[{'title': str, 'areas': [names]}]``.
        max_units_per_sheet: greedy packing limit for the default partition.
        layout_overrides: optional ``{tag: {'x': mm, 'y': mm}}`` symbol positions.
        equipment_data: optional ``{tag: [str, ...]}``; at most 3 short lines (34 characters, truncated
            with ``...``) printed in grey under the equipment description.

    Returns:
        Manifest dict with sheets, pdf, spec, unplaced_units, unplaced_streams, warnings.
    """
    model = _Model(data)
    warnings = list(model.warnings)
    tb = _resolve_tb(title_block, data, "PFD")
    eq_data = {t: v for t, v in _clean_equipment_data(equipment_data).items() if t in model.unit}
    model.eq_data = eq_data
    model.dn_fn = lambda number: "%s-%s-%02d" % (tb["document_number"], tb["sheet_number_prefix"], number)
    sheets_dir = os.path.join(out_dir, "sheets")
    prev_dir = os.path.join(out_dir, "previews")
    os.makedirs(sheets_dir, exist_ok=True)
    os.makedirs(prev_dir, exist_ok=True)
    parts = _plan_sheets(model, sheets, max_units_per_sheet)
    warnings.extend(w for w in model.warnings if w not in warnings)
    manifest = {"sheets": [], "pdf": None, "spec": None, "unplaced_units": [], "unplaced_streams": [],
                "warnings": warnings}
    if not parts:
        warnings.append("no equipment to draw")
        return manifest
    parts, layouts, _plan = _fit_partition(model, parts, warnings)
    # unique sheet titles
    counts = {}
    for p in parts:
        counts[p["title"]] = counts.get(p["title"], 0) + 1
    seen = {}
    n_sheets = len(parts)
    svg_paths, png_paths = [], []
    all_units, all_streams = set(), set()
    spec = {"version": 1, "kind": "pfd", "document_number": tb["document_number"],
            "overrides": {}, "equipment_data": eq_data, "sheets": []}
    for i, (p, (scale, layout)) in enumerate(zip(parts, layouts)):
        title = p["title"]
        if counts[title] > 1:
            seen[title] = seen.get(title, 0) + 1
            title = "%s (%d/%d)" % (title, seen[title], counts[title])
        number = i + 1
        dn = "%s-%s-%02d" % (tb["document_number"], tb["sheet_number_prefix"], number)
        applied = _finalize_sheet(layout, layout_overrides, warnings, number)
        spec["overrides"].update(applied)
        sh = {"number": number, "drawing_number": dn, "title": title, "tags": p["tags"], "layout": layout,
              "scale": scale}
        svg = _render_sheet(sh, tb, n_sheets, model, warnings)
        fname = _safe_name(dn)
        svg_path = os.path.join(sheets_dir, fname + ".svg")
        png_path = os.path.join(prev_dir, fname + ".png")
        _write(svg_path, svg)
        svg_paths.append(svg_path)
        png_paths.append(png_path)
        for w in layout.warnings:
            warnings.append("sheet %d: %s" % (number, w))
        streams_here = []
        for n in layout.real:
            if n.kind in ("feed", "product", "conn_in", "conn_out") and n.payload["stream"] not in streams_here:
                streams_here.append(n.payload["stream"])
        for e in layout.edges:
            if e.key not in streams_here:
                streams_here.append(e.key)
        conns = [{"stream": n.payload["stream"], "direction": "in" if n.kind == "conn_in" else "out",
                  "other_sheet": n.payload["other"] + 1, "other_drawing_number": n.payload["other_dn"]}
                 for n in layout.real if n.kind in ("conn_in", "conn_out")]
        all_units.update(p["tags"])
        all_streams.update(streams_here)
        manifest["sheets"].append({"number": number, "drawing_number": dn, "title": title, "svg": svg_path,
                                   "png": png_path, "units": list(p["tags"]), "streams": streams_here,
                                   "connectors": conns})
        spec["sheets"].append(_sheet_spec(sh, tb))
    pdf_path = os.path.join(out_dir, "pfd_drawing_set.pdf")
    _svgs_to_pdf(svg_paths, pdf_path, png_paths, warnings)
    log.info("PFD: %d sheets written to %s (%d warnings)", n_sheets, out_dir, len(warnings))
    spec_path = os.path.join(out_dir, "pfd_layout_spec.json")
    _write(spec_path, json.dumps(spec, indent=1, ensure_ascii=True))
    manifest["pdf"] = pdf_path
    manifest["spec"] = spec_path
    manifest["unplaced_units"] = [t for t in model.unit if t not in all_units]
    manifest["unplaced_streams"] = [s.id for s in model.streams if s.id not in all_streams]
    return manifest


# ---------------------------------------------------------------------------
# block flow diagram
# ---------------------------------------------------------------------------
BOX_W = 36.0
BLOCK_PITCH = 8.5
ROW_GAP = 9.0


def _fmt_flow(m):
    """Format a mass flow as ``1.23e5 kg/h``."""
    if not m:
        return "0 kg/h"
    e = int(math.floor(math.log10(abs(m))))
    if 0 <= e < 4:
        return "%.4g kg/h" % m
    mant = m / (10.0 ** e)
    if round(mant, 2) >= 10.0:
        mant /= 10.0
        e += 1
    return "%.2fe%d kg/h" % (mant, e)


def _stream_mass(st, case):
    res = (st.results or {}).get(case) or {}
    v = res.get("mass_kg_h")
    try:
        return float(v) if v is not None else 0.0
    except (TypeError, ValueError):
        return 0.0


def _flow_label(sids, total, n_show=3):
    """Three label lines: stream ids, count, mass flow."""
    n = len(sids)
    l1 = ",".join(sids[:n_show])
    if n == 1:
        return [l1, _fmt_flow(total)]
    cnt = "(%d streams)" % n
    l2 = ("+%d " % (n - n_show) if n > n_show else "") + cnt
    return [l1, l2, _fmt_flow(total)]


_MAIN_RANK = ["separator", "compressor", "pump", "column", "reactor", "expander", "exchanger", "cooler",
              "heater", "ejector", "mixer", "splitter", "valve", "pipe", "recycle", "other"]


def _main_tags(model, tags):
    def rk(t):
        c = model.unit[t].category
        return _MAIN_RANK.index(c) if c in _MAIN_RANK else len(_MAIN_RANK)

    order = sorted(range(len(tags)), key=lambda i: (rk(tags[i]), i))
    return [tags[i] for i in order]


def _block_node(model, bi, blk):
    tags = blk["tags"]
    main = _main_tags(model, tags)[:4]
    more = len(tags) - len(main)
    txt = ", ".join(main) + (" +%d more" % more if more > 0 else "")
    name_lines = _wrap(blk["name"][:1].upper() + blk["name"][1:], 3.0, BOX_W - 4.0, bold=True, max_lines=2)
    tag_lines = _wrap(txt, 2.1, BOX_W - 4.0, max_lines=4)
    desc_lines = _wrap(blk.get("description") or "", 2.0, BOX_W - 4.0, max_lines=2) if blk.get("description") else []
    h = 3.0 + len(name_lines) * 3.6 + 3.2 + len(tag_lines) * 2.9 + len(desc_lines) * 2.7 + 3.0
    h = max(h, 18.0)
    return _Node("B:%d" % bi, "block", BOX_W, h, h / 2.0, h / 2.0,
                 payload={"pitch": BLOCK_PITCH, "name": blk["name"], "name_lines": name_lines, "tag_lines": tag_lines,
                          "desc_lines": desc_lines, "n_units": len(tags), "tags": tags, "index": bi})


def _render_block(n):
    p = n.payload
    x0, y0 = n.cx - n.w / 2.0, n.cy - n.h / 2.0
    els = [_rect(x0, y0, n.w, n.h, fill="#fff", sw=0.5, rx=1.5)]
    span = (len(p["name_lines"]) - 1) * 3.6 + 3.6 + 3.5 + max(len(p["tag_lines"]) - 1, 0) * 2.9
    if p["desc_lines"]:
        span += 2.9 + (len(p["desc_lines"]) - 1) * 2.7
    y = n.cy - span / 2.0 + 0.8
    els.append(_text_lines(n.cx, y, p["name_lines"], 3.0, 3.6, "middle", bold=True))
    y += (len(p["name_lines"]) - 1) * 3.6 + 3.6
    els.append(_text(n.cx, y, "%d unit%s" % (p["n_units"], "" if p["n_units"] == 1 else "s"), 2.2,
                     "middle", fill=GREY))
    y += 3.5
    for t, _sp in p["tag_lines"]:
        els.append(_text(n.cx, y, t, 2.1, "middle"))
        y += 2.9
    for t, _sp in p["desc_lines"]:
        els.append(_text(n.cx, y, t, 2.0, "middle", fill=GREY, italic=True))
        y += 2.7
    return _g(els, data_block=p["name"]), [(x0, y0, x0 + n.w, y0 + n.h)]


def _render_oconn(n):
    """On-page connector: lettered circle with a 'to/from <block>' note."""
    p = n.payload
    r = 3.2
    if n.kind == "conn_out":
        cx = n.cx - n.w / 2.0 + r
        anchor, tx = "start", n.cx - n.w / 2.0
    else:
        cx = n.cx + n.w / 2.0 - r
        anchor, tx = "end", n.cx + n.w / 2.0
    els = [_circle(cx, n.cy, r), _text(cx, n.cy + 1.05, p["letter"], 3.0, "middle", bold=True)]
    rects = []
    lab = p.get("label", [])
    for j, t in enumerate(lab):
        yy = n.cy - r - 0.8 - (len(lab) - 1 - j) * 2.6
        els.append(_text(tx, yy, t, LABEL_FS, anchor))
        tw = text_w(t, LABEL_FS)
        rects.append((tx if anchor == "start" else tx - tw, yy - 2.0, tx + tw if anchor == "start" else tx, yy + 0.4))
    ty = n.cy + r + 3.0
    rects.append((cx - r, n.cy - r, cx + r, n.cy + r))
    for j, (t, _sp) in enumerate(p["lines"]):
        els.append(_text(tx, ty + j * 2.5, t, 2.0, anchor, fill=GREY))
        tw = text_w(t, 2.0)
        rects.append((tx if anchor == "start" else tx - tw, ty + j * 2.5 - 1.8,
                      tx + tw if anchor == "start" else tx, ty + j * 2.5 + 0.6))
    return _g(els), rects


def _bfd_blocks(model, blocks):
    out = []
    if blocks:
        used = set()
        for b in blocks:
            tags = []
            for an in b.get("areas", []):
                for a in model.areas:
                    if a[0] == an:
                        tags.extend(t for t in a[1] if t not in used)
            used.update(tags)
            if tags:
                out.append({"name": b.get("name") or ", ".join(b.get("areas", [])), "tags": tags,
                            "description": b.get("description", "")})
        rest = [t for a in model.areas for t in a[1] if t not in used]
        if rest:
            out.append({"name": "Other units", "tags": rest, "description": ""})
        return out
    for name, tags in model.areas:
        out.append({"name": str(name), "tags": list(tags), "description": ""})
    return out


def _bfd_flows(model, blist, case):
    """Aggregate streams into block-to-block flows, boundary feeds and products."""
    block_of = {}
    for i, b in enumerate(blist):
        for t in b["tags"]:
            block_of[t] = i
    flows, feeds, prods = {}, {}, {}
    for st in model.streams:
        pb = block_of.get(st.producer) if st.producer else None
        cbs = []
        for c in st.consumers:
            b = block_of.get(c)
            if b is not None and b not in cbs:
                cbs.append(b)
        m = _stream_mass(st, case)
        if st.producer is None:
            if cbs:
                feeds.setdefault(cbs[0], []).append((st.id, m))
            continue
        if not st.consumers:
            prods.setdefault(pb, []).append((st.id, m))
            continue
        for cb in cbs:
            if cb != pb:
                flows.setdefault((pb, cb), []).append((st.id, m))
    return block_of, flows, feeds, prods


def _letter(i):
    return chr(65 + i % 26) + (str(i // 26) if i >= 26 else "")


def _bfd_row_graph(model, blist, flows, feeds, prods, members, letters):
    """Nodes and edges for the blocks in ``members`` (cross-row flows become connectors)."""
    mem = set(members)
    nodes = [_block_node(model, b, blist[b]) for b in members]
    bn = {n.payload["index"]: n for n in nodes}
    edges = []

    def mk_edge(eid, key, a, b, kind, items, conn=None):
        sids = [s for s, _ in items]
        total = sum(m for _, m in items)
        lines = _flow_label(sids, total)
        e = _Edge(eid, key, a.id, b.id, {"lines": lines, "total": total, "streams": sids, "kind": kind,
                                         "lines1": [" ".join([lines[0], "(%d stream%s)" % (len(sids), "" if len(sids) == 1 else "s"), lines[-1]])]})
        lw = max(text_w(t, LABEL_FS) for t in lines) + 1.0
        e.meta["label_w"] = lw
        if conn is not None:
            e.meta["label_w"] = 0.0
            e.meta["conn"] = True
            conn.payload["label"] = lines
            conn.w = max(conn.w, lw)
            conn.above = 3.2 + 0.8 + 2.6 * len(lines) + 0.6
        edges.append(e)
        return e

    for (a, b), items in flows.items():
        if a in mem and b in mem:
            mk_edge("BE:%d_%d" % (a, b), "F%d_%d" % (a, b), bn[a], bn[b], "flow", items)
        elif a in mem:
            nm = blist[b]["name"]
            ls = _wrap("to " + nm, 2.0, 22.0, max_lines=2)
            w = max(7.0, max(text_w(t, 2.0) for t, _ in ls) + 0.5)
            cn = _Node("CO:%d_%d" % (a, b), "conn_out", w, 6.4, 4.6, 3.2 + 1.0 + 2.5 * len(ls),
                       payload={"letter": letters[(a, b)], "lines": ls})
            nodes.append(cn)
            mk_edge("BE:%d_%d" % (a, b), "F%d_%d" % (a, b), bn[a], cn, "flow", items, conn=cn)
        elif b in mem:
            nm = blist[a]["name"]
            ls = _wrap("from " + nm, 2.0, 22.0, max_lines=2)
            w = max(7.0, max(text_w(t, 2.0) for t, _ in ls) + 0.5)
            cn = _Node("CI:%d_%d" % (a, b), "conn_in", w, 6.4, 4.6, 3.2 + 1.0 + 2.5 * len(ls),
                       payload={"letter": letters[(a, b)], "lines": ls})
            nodes.append(cn)
            mk_edge("BE:%d_%d" % (a, b), "F%d_%d" % (a, b), cn, bn[b], "flow", items, conn=cn)
    for b in members:
        if b in feeds:
            items = feeds[b]
            total = sum(m for _, m in items)
            if len(items) <= 3:
                l1 = ",".join(s for s, _ in items) + (" feed" if len(items) == 1 else " feeds")
            else:
                l1 = "%d feed streams" % len(items)
            lines = [l1, "total " + _fmt_flow(total)]
            w = max(text_w(t, LABEL_FS) for t in lines) + 2.0
            fn = _Node("F:%d" % b, "feed", max(14.0, w), 0.0, 6.6, 1.2, payload={"lines": lines, "stream": "FD%d" % b})
            nodes.append(fn)
            e = _Edge("BE:feed%d" % b, "FD%d" % b, fn.id, bn[b].id,
                      {"lines": lines, "total": total, "streams": [s for s, _ in items], "kind": "feed"})
            e.meta["label_w"] = 0.0
            edges.append(e)
        if b in prods:
            items = prods[b]
            lines = _flow_label([s for s, _ in items], sum(m for _, m in items))
            w = max(text_w(t, LABEL_FS) for t in lines) + 2.0
            pn = _Node("P:%d" % b, "product", max(14.0, w), 0.0, 3.0 + 2.6 * len(lines), 1.2,
                       payload={"lines": lines, "stream": "PD%d" % b})
            nodes.append(pn)
            e = _Edge("BE:prod%d" % b, "PD%d" % b, bn[b].id, pn.id,
                      {"lines": lines, "total": sum(m for _, m in items),
                       "streams": [s for s, _ in items], "kind": "product"})
            e.meta["label_w"] = 0.0
            edges.append(e)
    for n in nodes:
        if n.kind != "block":
            continue
        n_in = sum(1 for e in edges if e.dst == n.id)
        n_out = sum(1 for e in edges if e.src == n.id)
        need = (max(n_in, n_out, 1) - 1) * BLOCK_PITCH + 7.0
        if need > n.h:
            n.h = need
            n.above = n.below = need / 2.0
    return nodes, edges


def _bfd_gap_label(g, bundles, straight):
    w = 0.0
    for b in bundles:
        for e, _i, _ya, _yb in b.segs:
            w = max(w, e.meta.get("label_w", 0.0))
    for e, _i, _y in straight:
        w = max(w, e.meta.get("label_w", 0.0))
    return w


def _bfd_row_layout(model, blist, flows, feeds, prods, members, letters, boost=0.0):
    nodes, edges = _bfd_row_graph(model, blist, flows, feeds, prods, members, letters)
    cfg = {"gap_base": 10.0,
           "gap_label": lambda g, b, s: (_bfd_gap_label(g, b, s) + boost) if _bfd_gap_label(g, b, s) > 0 else 0.0,
           "rank": {"B:%d" % b: b for b in members}}
    return compute_layout(nodes, edges, cfg)


def _row_groups(layer_of, n_rows):
    """Split blocks into ``n_rows`` contiguous layer groups."""
    uniq = sorted(set(layer_of.values()))
    per = int(math.ceil(len(uniq) / float(n_rows)))
    groups = []
    for r in range(n_rows):
        ls = set(uniq[r * per:(r + 1) * per])
        mem = sorted(b for b, l in layer_of.items() if l in ls)
        if mem:
            groups.append(mem)
    return groups


def build_bfd(data, out_dir, title_block=None, blocks=None, case=None):
    """Build the block flow diagram (one block per area or per user block).

    Large diagrams are folded into several rows joined by lettered on-page
    connectors so that text stays readable on an A3 sheet.

    Args:
        data: extracted process data dict.
        out_dir: output directory (created if missing).
        title_block: optional title-block fields (see module docs); ``omitted_systems``
            is printed in a dashed 'Not modelled' box.
        blocks: optional ``[{'name', 'areas', 'description'}]``.
        case: case id whose mass flows are shown (default: first case).

    Returns:
        Manifest dict with svg, pdf, png, spec, blocks, edges, warnings.
    """
    model = _Model(data)
    warnings = list(model.warnings)
    tb = _resolve_tb(title_block, data, "BFD")
    os.makedirs(out_dir, exist_ok=True)
    case = case or model.case0
    blist = _bfd_blocks(model, blocks)
    manifest = {"svg": None, "pdf": None, "png": None, "spec": None, "blocks": [], "edges": [],
                "case": case, "warnings": warnings}
    if not blist:
        warnings.append("no blocks to draw")
        return manifest
    _block_of, flows, feeds, prods = _bfd_flows(model, blist, case)
    letters = {}
    for k, pair in enumerate(flows.keys()):
        letters[pair] = _letter(k)
    omitted = tb["omitted_systems"]
    om_lines = []
    om_h = 0.0
    if omitted:
        om_lines = _wrap("Not modelled: " + "; ".join(omitted), DESC_FS + 0.4, AREA[2] - 6.0, max_lines=4)
        om_h = 4.0 + 3.2 * len(om_lines)
    area_h = AREA[3] - (om_h + 3.0 if om_h else 0.0)
    attempts = []
    for boost in (0.0, 5.0, 10.0, 15.0):
        everyone = list(range(len(blist)))
        full = _bfd_row_layout(model, blist, flows, feeds, prods, everyone, letters, boost)
        layer_of = {n.payload["index"]: n.layer for n in full.real if n.kind == "block"}
        options = []
        wl = []
        n_fb = 0
        for n_rows in range(1, 5):
            if n_rows == 1:
                lays = [(everyone, full)]
            else:
                groups = _row_groups(layer_of, n_rows)
                if len(groups) < n_rows:
                    continue
                lays = [(g, _bfd_row_layout(model, blist, flows, feeds, prods, g, letters, boost)) for g in groups]
            w = max(l.width for _, l in lays)
            h = sum(l.height for _, l in lays) + ROW_GAP * (len(lays) - 1)
            zoom = min(AREA[2] * 0.97 / w, area_h * 0.95 / h, 1.35)
            options.append((zoom, n_rows, lays, w, h))
        chosen = None
        for opt in options:
            if opt[0] >= 0.92:
                chosen = opt
                break
        if chosen is None:
            chosen = max(options, key=lambda o: (o[0], -o[1]))
            wl.append("BFD content scaled by %.2f to fit the sheet" % chosen[0])
        zoom, n_rows, lays, tot_w, tot_h = chosen
        dx = AREA[0] + (AREA[2] - tot_w * zoom) / 2.0
        dy = AREA[1] + (area_h - tot_h * zoom) / 2.0
        dn = "%s-BFD-01" % tb["document_number"]
        inner = []
        all_edges = []
        y_off = 0.0
        rows_spec = []
        for members, layout in lays:
            _shift(layout, 0.0, y_off)
            y_off += layout.height + ROW_GAP
            node_els = []
            rects = []
            for n in layout.real:
                if n.kind == "block":
                    g, r = _render_block(n)
                    node_els.append(g)
                    rects.extend(r)
                elif n.kind in ("conn_in", "conn_out"):
                    g, r = _render_oconn(n)
                    node_els.append(g)
                    rects.extend(r)
                else:
                    lab = n.payload["lines"]
                    left = n.cx - n.w / 2.0
                    els = []
                    for j, t in enumerate(lab):
                        yy = n.cy - 0.9 - (len(lab) - 1 - j) * 2.6
                        els.append(_text(left + 0.8, yy, t, LABEL_FS))
                        rects.append((left + 0.8, yy - 2.0, left + 0.8 + text_w(t, LABEL_FS), yy + 0.4))
                    node_els.append(_g(els))
            marks = []
            for e in layout.edges:
                if e.meta["kind"] == "flow" and not e.meta.get("conn"):
                    lines = e.meta["lines"]
                    w3 = e.meta["label_w"]
                    h3 = 2.6 * len(lines) + 0.4
                    w1 = text_w(e.meta["lines1"][0], LABEL_FS) + 1.0
                    first = "below" if (e.back and e.lane_side == "dn") else "above"
                    second = "above" if first == "below" else "below"
                    marks.append({"key": e.eid, "edges": [e], "variants": [
                        {"w": w3, "h": h3, "mode": first, "lines": lines},
                        {"w": w3, "h": h3, "mode": second, "lines": lines},
                        {"w": w1, "h": 3.0, "mode": first, "lines": e.meta["lines1"]},
                        {"w": w1, "h": 3.0, "mode": second, "lines": e.meta["lines1"]}]})
            placed, fallback = _place_marks(marks, layout.edges, rects, 10.0, 6.0, "above", step=1.0,
                                            edge_margin=0.8)
            n_fb += len(fallback)
            for k in fallback:
                wl.append("BFD label of %s overlaps other elements" % k)
            for e in layout.edges:
                ch = [_polyline(e.pts)]
                (x1, y1), (x2, y2) = e.pts[-2], e.pts[-1]
                ch.append(_arrowhead(x2, y2, x2 - x1, y2 - y1))
                if e.eid in placed:
                    x, y, r, vi = placed[e.eid]
                    vlines = next(m for m in marks if m["key"] == e.eid)["variants"][vi]["lines"]
                    for j, t in enumerate(vlines):
                        ch.append(_text(x, r[1] + 2.0 + j * 2.6, t, LABEL_FS, "middle"))
                inner.append(_g(ch, data_edge=e.eid))
            for x, y, _key in layout.dots:
                inner.append(_circle(x, y, 0.55, fill=INK, stroke=INK, sw=0.1))
            inner.extend(node_els)
            all_edges.extend(layout.edges)
            for w_ in layout.warnings:
                wl.append("BFD: %s" % w_)
            rows_spec.append(members)
        attempts.append({"zoom": zoom, "n_rows": n_rows, "inner": inner, "edges": all_edges,
                         "rows": rows_spec, "dx": dx, "dy": dy, "fb": n_fb, "wl": wl})
        if n_fb == 0 and zoom >= 0.8:
            break
    best = min(attempts, key=lambda r: r["fb"] * 0.04 + (1.0 - r["zoom"]))
    zoom, n_rows, inner, all_edges = best["zoom"], best["n_rows"], best["inner"], best["edges"]
    rows_spec, dx, dy = best["rows"], best["dx"], best["dy"]
    warnings.extend(best["wl"])
    out = ['<?xml version="1.0" encoding="UTF-8"?>',
           '<svg xmlns="http://www.w3.org/2000/svg" width="420mm" height="297mm" '
           'viewBox="0 0 420 297" version="1.1">',
           '<rect x="0" y="0" width="420" height="297" fill="#fff"/>']
    out.extend(_frame_els())
    out.append(_text(12.0, 13.6, "%s  Block flow diagram" % dn, 3.6, bold=True))
    out.append('<g transform="translate(%s %s) scale(%s)">%s</g>' % (_f(dx), _f(dy), _f(zoom), "".join(inner)))
    if om_lines:
        bx, by, bw = AREA[0], AREA[1] + AREA[3] - om_h, AREA[2]
        out.append(_dashed_poly([(bx, by), (bx + bw, by), (bx + bw, by + om_h), (bx, by + om_h), (bx, by)],
                                dash=2.0, gap=1.2))
        for j, (t, _sp) in enumerate(om_lines):
            out.append(_text(bx + 3.0, by + 4.6 + j * 3.2, t, DESC_FS + 0.4))
    if tb["readiness_note"]:
        out.append(_text(AREA[0] + AREA[2] / 2.0, 229.4, tb["readiness_note"], 3.2, "middle", bold=True, fill=RED))
    legend = [("line", "block", "Process block (area): name, number of units, main equipment tags"),
              ("line", "flow", "Aggregated material flow between blocks: stream numbers, mass flow"),
              ("line", "boundary", "Boundary feed (left) and product (right) arrows"),
              ("line", "backflow", "Back flow / recycle drawn as routed line above or below the blocks")]
    if n_rows > 1:
        legend.append(("line", "oconn", "On-page connector: same letter continues the flow in another row"))
    if om_lines:
        legend.append(("line", "notmodelled", "Systems not modelled"))
    out.extend(_legend_els(legend))
    case_label = next((c.get("label") for c in model.cases if c.get("id") == case), "")
    notes = ["1. Flows are mass flows for case %s%s." % (case, " (%s)" % case_label if case_label else ""),
             "2. Blocks are the process areas of the model; stream numbers refer to the stream tables (appendices).",
             "3. Flow between blocks is the sum of the mass flow of all streams crossing from one block to the next.",
             "4. Boundary arrows aggregate all feed streams per receiving block."]
    if omitted:
        notes.append("5. Omitted / not modelled systems: " + "; ".join(omitted) + ".")
    for x in tb["notes"]:
        notes.append("%d. %s" % (len(notes) + 1, x))
    out.extend(_notes_els(notes))
    out.extend(_rev_table_els(tb))
    out.extend(_title_block_els(tb, dn, 1, 1, ""))
    out.append("</svg>")
    log.info("BFD: %d rows, zoom %.2f, %d warnings", n_rows, zoom, len(warnings))
    svg_path = os.path.join(out_dir, "bfd.svg")
    pdf_path = os.path.join(out_dir, "bfd.pdf")
    png_path = os.path.join(out_dir, "bfd.png")
    spec_path = os.path.join(out_dir, "bfd_layout_spec.json")
    _write(svg_path, "\n".join(out) + "\n")
    _svgs_to_pdf([svg_path], pdf_path, [png_path], warnings)
    spec = {"version": 1, "kind": "bfd", "case": case, "zoom": round(zoom, 4), "origin": [round(dx, 2), round(dy, 2)],
            "rows": [[blist[b]["name"] for b in m] for m in rows_spec],
            "edges": [{"id": e.eid, "from": e.src, "to": e.dst, "kind": e.meta["kind"], "back_edge": bool(e.back),
                       "streams": e.meta["streams"], "mass_kg_h": e.meta["total"],
                       "points": [[round(x, 2), round(y, 2)] for x, y in e.pts]} for e in all_edges],
            "title_block": {k: v for k, v in tb.items()}}
    _write(spec_path, json.dumps(spec, indent=1, ensure_ascii=True))
    manifest.update({"svg": svg_path, "pdf": pdf_path, "png": png_path, "spec": spec_path, "rows": n_rows,
                     "blocks": [{"name": b["name"], "units": b["tags"]} for b in blist],
                     "edges": [{"id": e.eid, "kind": e.meta["kind"], "streams": e.meta["streams"],
                                "mass_kg_h": e.meta["total"]} for e in all_edges]})
    return manifest

