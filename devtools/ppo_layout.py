"""Hierarchical layout for the one-sheet process overview.

Two levels, both deterministic:

1. **Sections** (process areas / trains) are placed as blocks in a left-to-right layered drawing of the
   section graph (longest-path layering after DFS cycle removal; sections sharing a layer are stacked
   vertically in barycentre order, so parallel trains sit above each other).
2. **Inside each section** the units are laid out with the Sugiyama engine of :mod:`psr_diagrams`
   (:func:`psr_diagrams.compute_layout`): layering, crossing reduction, port ordering and orthogonal channel
   routing. Streams that cross a section border end at invisible port nodes on the section's left/right
   edge; the inter-section connections are then routed orthogonally through the column gaps, through a free
   horizontal corridor for long forward connections, and through lanes above/below the content for recycles
   and returns.

A section can also be drawn as a single **block** (``collapsed``) with a reference to its detail sheet; the
orchestrator uses this when the complete detail does not fit the sheet at readable text sizes. Text size is
never reduced to make a layout fit: the symbol/gap scale is reduced down to ``style["scales"][-1]`` and,
below that, sections are collapsed.

The result (:class:`Layout`) carries absolute page coordinates in millimetres for sections, nodes, ports,
routed edges, annotations and junction dots, plus a ``neqsim.pfd_layout.v1`` spec for :mod:`pfd_layout_check`.
"""
from __future__ import annotations

import math

import psr_diagrams as _psr
from ppo_render import MM_PER_PT, make_symbol

SERVICE_RANK = {"gas": 0, "multiphase": 1, "oil": 1, "unknown": 1, "utility": 1, "water": 2}


class Layout:
    """Result container (absolute mm coordinates, y down)."""

    def __init__(self):
        self.sections = []  # dicts: id, name, x, y, w, h, layer, row, collapsed, members, ref
        self.nodes = {}  # node id -> dict
        self.edges = []  # dicts: id, stream, src, dst, pts, external, back, arrow_end
        self.dots = []  # (x, y, stream)
        self.annotations = []  # dicts: stream, lines, bbox (x0, y0, x1, y1), x, y
        self.mid_arrows = []  # (x, y, ux, uy, stream)
        self.bbox = (0.0, 0.0, 0.0, 0.0)
        self.scale = 1.0
        self.fits = True
        self.warnings = []
        self.obstacles = []
        self.collapsed = []
        self.unplaced_annotations = []
        self.bubbles = []  # dicts: stream, x, y, bbox, text (stream-number marks and bundle counts on lines)
        self.bundles = {}
        self.content_size = (0.0, 0.0)
        self.rows = 1


# ---------------------------------------------------------------------------
# node building
# ---------------------------------------------------------------------------
def _fmt_power(kw):
    if kw is None:
        return None
    a = abs(kw)
    if a >= 1000.0:
        return "%.1f MW" % (kw / 1000.0)
    if a >= 10.0:
        return "%.0f kW" % kw
    return "%.1f kW" % kw


def _fmt_mass(kg_h):
    if kg_h is None:
        return None
    if abs(kg_h) >= 1000.0:
        return "%.1f t/h" % (kg_h / 1000.0)
    return "%.0f kg/h" % kg_h


def _fmt_std_gas(sm3_h):
    if sm3_h is None:
        return None
    d = sm3_h * 24.0
    if abs(d) >= 1.0e6:
        return "%.2f MSm3/d" % (d / 1.0e6)
    return "%.0f kSm3/d" % (d / 1.0e3)


def _desc(e):
    cat, jc = e["category"], e.get("java_class") or ""
    if cat == "separator":
        if "ThreePhase" in jc:
            return "3-phase separator"
        if "Scrubber" in jc:
            return "scrubber"
        if "Tank" in jc:
            return "tank"
        return "separator"
    if cat == "other":
        return jc or "equipment"
    return cat


def unit_values_lines(e, level):
    """Short value lines shown under a unit (power / duty) for the information level."""
    v = e.get("values") or {}
    out = []
    if e["category"] in ("compressor", "pump", "expander") and v.get("power_kw") is not None:
        out.append("P = " + _fmt_power(v["power_kw"]))
    elif e["category"] in ("heater", "cooler", "exchanger") and v.get("duty_kw") is not None:
        out.append("Q = " + _fmt_power(v["duty_kw"]))
    if level == "engineering" and e["category"] == "compressor" and v.get("dp_bar") is not None:
        out.append("dP = %.1f bar" % (-v["dp_bar"]))
    return out


def stream_annotation_lines(st, level, std_conditions=None):
    """Annotation text lines for a stream (id, flow, T, P); presentation level keeps one line."""
    v = st.get("values") or {}
    parts = []
    m = _fmt_mass(v.get("mass_kg_h"))
    if m:
        parts.append(m)
    if level != "presentation":
        if v.get("t_c") is not None:
            parts.append("%.0f C" % v["t_c"])
        elif v.get("t_c_range"):
            parts.append("%.0f-%.0f C" % tuple(v["t_c_range"]))
        if v.get("p_bara") is not None:
            parts.append("%.1f bara" % v["p_bara"])
        elif v.get("p_bara_range"):
            parts.append("%.0f-%.0f bara" % tuple(v["p_bara_range"]))
    head = st["id"] if not st.get("grouped") else "%s (x%d)" % (st["id"], len(st.get("members") or []))
    if level == "presentation":
        return [" ".join([head] + parts)] if parts else [head]
    return [head, "  ".join(parts)] if parts else [head]


def _unit_node(e, graph, fonts, style, scale):
    pt = style["pt"]
    n_in, n_out = len(e["ports"]["in"]), len(e["ports"]["out"])
    sym = make_symbol(e["category"], e.get("java_class", ""), scale, n_in, n_out, style.get("port_pitch", 5.5))
    tag_text = e["id"] if not e.get("represents") else "%s  x%d" % (e["id"], e["represents"]["count"])
    tag_lines = fonts.wrap(tag_text, pt["tag"], max(sym.w * 1.6, 30.0), bold=True, max_lines=2)
    desc = _desc(e)
    if e.get("represents"):
        desc = "%s, %d x (%s)" % (desc, e["represents"]["count"], "/".join(e["represents"].get("trains") or []))
    vals = unit_values_lines(e, graph["level"])
    tag_w = max(fonts.width(t, pt["tag"], True) for t in tag_lines)
    desc_w = fonts.width(desc, pt["desc"])
    val_w = max([fonts.width(v, pt["value"]) for v in vals], default=0.0)
    w = max(sym.w, tag_w, desc_w, val_w) + 2.0
    lh_tag = fonts.height(pt["tag"]) * 1.05
    above = sym.h / 2.0 + len(tag_lines) * lh_tag + 1.0
    lh_d = fonts.height(pt["desc"]) * 1.05
    below = sym.h / 2.0 + lh_d + 1.0 + len(vals) * fonts.height(pt["value"]) * 1.05 + (0.6 if vals else 0.0)
    node = _psr._Node("U:" + e["id"], "unit", w, sym.h, above, below, sym=sym,
                      payload={"tag": e["id"], "tag_lines": tag_lines, "desc": desc, "values": vals, "equipment": e})
    if e["category"] == "separator":
        node.fixed_out = True
        sidx = {s["id"]: s for s in graph["streams"]}
        for sid in e["ports"]["out"]:
            node.out_rank[sid] = SERVICE_RANK.get(sidx[sid]["service"], 1) if sid in sidx else 1
    return node


def _block_node(sec, graph, fonts, style, ref):
    pt = style["pt"]
    name_lines = fonts.wrap(sec["name"], pt["tag"], 64.0, bold=True, max_lines=3)
    sub = "%d units - sheet %s" % (len(sec["members"]), ref) if ref else "%d units" % len(sec["members"])
    w = max(max(fonts.width(t, pt["tag"], True) for t in name_lines), fonts.width(sub, pt["desc"])) + 8.0
    n_ports = max(len(sec.get("_in", [])), len(sec.get("_out", [])), 1)
    h = max(16.0, len(name_lines) * fonts.height(pt["tag"]) * 1.1 + fonts.height(pt["desc"]) * 1.1 + 5.0,
            (n_ports - 1) * 5.0 + 8.0)
    return _psr._Node("B:" + sec["id"], "block", w, h, h / 2.0 + 0.5, h / 2.0 + 0.5,
                      payload={"section": sec["id"], "name_lines": name_lines, "sub": sub, "pitch": 5.0})


def _boundary_node(st, kind, fonts, style, annotated=False, level="overview"):
    pt = style["pt"]
    label = st["label"]
    lines = fonts.wrap(label, pt["desc"], style.get("boundary_label_w", 30.0), max_lines=2)
    w = max(16.0, max(fonts.width(t, pt["desc"]) for t in lines) + 2.0)
    lh = fonts.height(pt["desc"]) * 1.05
    ann = stream_annotation_lines(st, level) if annotated else []
    ann = ann[1:] if (ann and len(ann) > 1) else ann  # the stream number is drawn in the bubble position / first line
    below = 1.6
    if ann:
        w = max(w, max(fonts.width(t, pt["stream"]) for t in ann) + 2.0)
        below = 1.2 + len(ann) * fonts.height(pt["stream"]) * 1.05 + 0.4 + 2.5
    return _psr._Node(("F:" if kind == "feed" else "P:") + st["id"], kind, w, 0.0, len(lines) * lh + 1.2, below,
                      payload={"stream": st["id"], "lines": lines, "pseudo": False, "annotation": ann})


def _port_node(sid, kind):
    return _psr._Node(("PI:" if kind == "feed" else "PO:") + sid, kind, 1.0, 0.0, 1.2, 1.2,
                      payload={"stream": sid, "pseudo": True})


# ---------------------------------------------------------------------------
# section-internal layout
# ---------------------------------------------------------------------------
def _section_internal(graph, sec, fonts, style, scale, collapsed, ref, annotate=(), skip=()):
    """Layered layout of one section; returns (psr layout, node list). ``skip`` lists bundled streams not drawn."""
    sidx = {s["id"]: s for s in graph["streams"]}
    eidx = {e["id"]: e for e in graph["equipment"]}
    members = list(sec["members"])
    mset = set(members)
    nodes, edges = [], []
    nmap = {}
    ecount = {}

    def add_edge(sid, a, b):
        i = ecount.get(sid, 0)
        ecount[sid] = i + 1
        edges.append(_psr._Edge("E:%s:%d" % (sid, i), sid, a.id, b.id))

    po, pi = {}, {}

    def port(sid, kind):
        store = pi if kind == "feed" else po
        if sid not in store:
            store[sid] = _port_node(sid, kind)
            nodes.append(store[sid])
        return store[sid]

    if collapsed:
        skip = set(skip)
        ins = sorted({s["id"] for s in graph["streams"] if any(t in mset for t in s["to"]) and (s["from"] not in mset)
                      and s["id"] not in skip})
        outs = sorted({s["id"] for s in graph["streams"] if s["from"] in mset and (any(t not in mset for t in s["to"]) or not s["to"])
                       and s["id"] not in skip})
        sec["_in"], sec["_out"] = ins, outs
        blk = _block_node(sec, graph, fonts, style, ref)
        nodes.append(blk)
        for sid in ins:
            st = sidx[sid]
            src = _boundary_node(st, "feed", fonts, style, st["id"] in annotate, graph["level"]) if st["from"] is None else port(sid, "feed")
            if st["from"] is None:
                nodes.append(src)
            add_edge(sid, src, blk)
        for sid in outs:
            st = sidx[sid]
            if not st["to"]:
                dst = _boundary_node(st, "product", fonts, style, st["id"] in annotate, graph["level"])
                nodes.append(dst)
            else:
                dst = port(sid, "product")
            add_edge(sid, blk, dst)
    else:
        for tag in members:
            n = _unit_node(eidx[tag], graph, fonts, style, scale)
            nodes.append(n)
            nmap[tag] = n
        for st in graph["streams"]:
            sid = st["id"]
            cons_here = [t for t in st["to"] if t in mset]
            if st["from"] in mset:
                src = nmap[st["from"]]
                for c in cons_here:
                    add_edge(sid, src, nmap[c])
                if any(t not in mset for t in st["to"]):
                    add_edge(sid, src, port(sid, "product"))
                if not st["to"]:
                    dst = _boundary_node(st, "product", fonts, style, st["id"] in annotate, graph["level"])
                    nodes.append(dst)
                    add_edge(sid, src, dst)
            elif cons_here:
                if st["from"] is None:
                    src = _boundary_node(st, "feed", fonts, style, st["id"] in annotate, graph["level"])
                    nodes.append(src)
                else:
                    src = port(sid, "feed")
                for c in cons_here:
                    add_edge(sid, src, nmap[c])
    cfg = {"stack_pitch": 0.0, "gap_base": style["layout"]["gap_base"] * max(scale, 0.75), "ordering": "refined",
           "max_stretch": style["layout"]["max_stretch"]}
    lay = _psr.compute_layout(nodes, edges, cfg, target_w=None)
    if lay.crossings > 0:
        nodes2 = [_clone_node(n) for n in nodes]
        edges2 = [_psr._Edge(e.eid, e.key, e.src, e.dst) for e in edges]
        alt = _psr.compute_layout(nodes2, edges2, dict(cfg, ordering="barycentre"), target_w=None)
        if alt.crossings < lay.crossings:
            lay = alt
    return lay


def _clone_node(n):
    c = _psr._Node(n.id, n.kind, n.w, n.h, n.above, n.below, sym=n.sym, payload=dict(n.payload))
    c.fixed_out = n.fixed_out
    c.out_rank = dict(n.out_rank)
    return c


# ---------------------------------------------------------------------------
# section graph
# ---------------------------------------------------------------------------
def _fas_order(ids, edges):
    """Eades-Lin-Smyth greedy feedback-arc-set ordering on weighted section edges ``[(a, b, weight)]``.

    Sources go first, sinks last; the rest is ordered by out-weight minus in-weight so that the heavy process flow
    runs left to right and only light return streams point backwards.
    """
    ids = list(ids)
    adj = {i: {} for i in ids}
    for a, b, w in edges:
        if a == b or a not in adj or b not in adj:
            continue
        adj[a][b] = adj[a].get(b, 0.0) + w
    remaining = set(ids)
    left, right = [], []
    while remaining:
        changed = True
        while changed:
            changed = False
            for i in sorted(remaining, key=ids.index):
                outs = [b for b in adj[i] if b in remaining]
                ins = [a for a in remaining if i in adj[a]]
                if not outs:
                    right.insert(0, i)
                    remaining.discard(i)
                    changed = True
                elif not ins:
                    left.append(i)
                    remaining.discard(i)
                    changed = True
        if remaining:
            def delta(i):
                o = sum(adj[i][b] for b in adj[i] if b in remaining)
                n = sum(adj[a][i] for a in remaining if i in adj[a])
                return (o - n, -ids.index(i))
            pick = max(remaining, key=delta)
            left.append(pick)
            remaining.discard(pick)
    return left + right


def _section_layers(graph, sections):
    """Layer index per section id and the set of back-edge (src, dst) section pairs.

    Sections are ordered by the mass-flow-weighted feedback-arc-set heuristic (:func:`_fas_order`); an edge pointing
    against that order is a back edge (return / recycle). Layers are longest paths over the forward edges.
    """
    ids = [s["id"] for s in sections]
    weight = {}
    for st in graph["streams"]:
        a = st.get("section_from")
        for b in st.get("sections_to") or []:
            if a and b and a != b and a in ids and b in ids:
                m = (st.get("values") or {}).get("mass_kg_h")
                weight[(a, b)] = weight.get((a, b), 0.0) + (float(m) if m else 0.0) + 1.0
    order = _fas_order(ids, [(a, b, w) for (a, b), w in weight.items()])
    pos = {i: k for k, i in enumerate(order)}
    back_pairs = {(a, b) for (a, b) in weight if pos[b] <= pos[a]}
    preds = {i: [] for i in ids}
    succs = {i: [] for i in ids}
    for (a, b) in weight:
        if (a, b) not in back_pairs:
            preds[b].append(a)
            succs[a].append(b)
    layer = {}
    for i in order:
        layer[i] = 1 + max([layer[p] for p in preds[i]], default=0)
    for i in reversed(order):
        if not preds[i] and succs[i]:
            layer[i] = max(1, min(layer[s] for s in succs[i]) - 1)
    lo = min(layer.values()) if layer else 1
    for i in ids:
        layer[i] -= lo
    return layer, back_pairs, preds, succs

def section_order(graph):
    """Section ids in drawing order (layer, then vertical position) - the process reading order."""
    sections = [dict(s) for s in graph["sections"]]
    layer, back_pairs, preds, succs = _section_layers(graph, sections)
    order = _order_rows(sections, layer, preds, succs)
    out = []
    for Lx in sorted(order):
        out.extend(order[Lx])
    return out


def contiguous_groups(ids, weights, k):
    """Split an ordered id list into ``k`` contiguous groups balancing the summed weights (linear partition)."""
    rows = _partition([float(weights.get(i, 1.0)) for i in ids], 0.0, k)
    return [[ids[j] for j in row] for row in rows]


def _order_rows(sections, layer, preds, succs):
    """Vertical order of the sections of each layer (barycentre of neighbours, 3 sweeps)."""
    cols = {}
    for s in sections:
        cols.setdefault(layer[s["id"]], []).append(s["id"])
    order = {L: list(v) for L, v in cols.items()}
    pos = {}
    for L in sorted(order):
        for i, sid in enumerate(order[L]):
            pos[sid] = i
    L_list = sorted(order)
    for sweep in range(3):
        seq = L_list if sweep % 2 == 0 else list(reversed(L_list))
        for L in seq:
            def bary(sid):
                nb = preds[sid] if sweep % 2 == 0 else succs[sid]
                vals = [pos[n] for n in nb if n in pos and layer[n] != L]
                return (sum(vals) / len(vals)) if vals else pos[sid]
            order[L].sort(key=lambda sid: (bary(sid), pos[sid]))
            for i, sid in enumerate(order[L]):
                pos[sid] = i
    return order


# ---------------------------------------------------------------------------
# composition (sections in rows of layers, inter-section routing)
# ---------------------------------------------------------------------------
def _shift_internal(lay, dx, dy):
    _psr._shift(lay, dx, dy)


def _external_streams(graph, sec_index):
    """``[(stream id, src section, dst section)]`` for every connection between two drawn sections."""
    out = []
    for st in graph["streams"]:
        a = st.get("section_from")
        if not a or a not in sec_index:
            continue
        for b in st.get("sections_to") or []:
            if b != a and b in sec_index:
                out.append((st["id"], a, b))
    return out


def _partition(col_w, base_gap, k):
    """Split the layer sequence into ``k`` contiguous rows minimising the widest row (linear partition DP)."""
    n = len(col_w)
    k = max(1, min(k, n))
    cost = [w + base_gap for w in col_w]
    pre = [0.0]
    for c in cost:
        pre.append(pre[-1] + c)
    INF = float("inf")
    dp = [[INF] * (k + 1) for _ in range(n + 1)]
    cut = [[0] * (k + 1) for _ in range(n + 1)]
    dp[0][0] = 0.0
    for j in range(1, k + 1):
        for i in range(1, n + 1):
            for m in range(j - 1, i):
                v = max(dp[m][j - 1], pre[i] - pre[m])
                if v < dp[i][j]:
                    dp[i][j], cut[i][j] = v, m
    rows = []
    i = n
    for j in range(k, 0, -1):
        m = cut[i][j]
        rows.append(list(range(m, i)))
        i = m
    rows.reverse()
    return [r for r in rows if r]


def _gkey(r, k):
    """Physical gap key: the left-most gap of every row shares the same x range, other gaps are per row."""
    return (-1, 0) if k == 0 else (r, k)


class _Grid:
    """Positions of layers (columns) in rows, physical gaps and corridors."""

    def __init__(self, rows, col_w, sst, gap_demand, lane_heights, row_h, margin_lanes):
        self.rows = rows
        self.row_of, self.pos_of = {}, {}
        for r, row in enumerate(rows):
            for k, L in enumerate(row):
                self.row_of[L], self.pos_of[L] = r, k
        pitch = sst["channel_pitch"]
        self.gap_x = {}  # gap key -> (left, right)
        self.col_x = {}
        self.row_w = []
        for r, row in enumerate(rows):
            x = 0.0
            for k, L in enumerate(row):
                g = sst["gap_x"] * (0.5 if k == 0 else 1.0) + pitch * max(gap_demand.get(_gkey(r, k), 0) - 1, 0)
                self.gap_x[_gkey(r, k)] = (x, x + g)
                x += g
                self.col_x[L] = x
                x += col_w[L]
            g = sst["gap_x"] * 0.5 + pitch * max(gap_demand.get((r, len(row)), 0) - 1, 0)
            self.gap_x[(r, len(row))] = (x, x + g)
            self.row_w.append(x + g)
        self.width = max(self.row_w) if self.row_w else 0.0
        # corridors: c = 0 above row 0, c = r between rows r-1 and r, c = n below the last row; lane heights may differ
        lp = sst["lane_pitch"]
        n = len(rows)
        self.lane_heights = {c: list(lane_heights.get(c, [])) for c in range(n + 1)}
        self.corridor_h = {}
        for c in range(n + 1):
            hs = self.lane_heights[c]
            inner = sum(hs) + (lp if hs else 0.0)
            self.corridor_h[c] = inner + (sst.get("corridor_gap", sst["gap_y"]) if 0 < c < n else 0.0)
        self.row_y = []
        y = self.corridor_h[0]
        for r in range(n):
            self.row_y.append(y)
            y += row_h[r]
            y += self.corridor_h[r + 1]
        self.height = y
        self.row_h = row_h
        self.margin_x = -lp * (margin_lanes + 1) if margin_lanes else 0.0
        self.lp = lp
        self.gap_y = sst["gap_y"]
        self.corridor_gap = sst.get("corridor_gap", sst["gap_y"])
        self.line_pos = "center"

    def corridor_y(self, c, lane):
        """y of lane ``lane`` in corridor ``c`` (c = 0 above the first row, c = n below the last row)."""
        n = len(self.rows)
        hs = self.lane_heights[c]
        if lane >= len(hs):  # provisional grid without lane heights: corridor centre line
            return self.corridor_mid(c)
        if self.line_pos == "center":
            off = sum(hs[:lane]) + hs[lane] / 2.0 + self.lp / 2.0
        else:  # line at the bottom of its slot so a label fits above it
            off = sum(hs[:lane]) + hs[lane]
        if c == 0:
            return off
        top = self.row_y[c - 1] + self.row_h[c - 1]
        if c == n:
            return top + off
        return top + self.corridor_gap / 2.0 + off

    def corridor_mid(self, c):
        """Approximate y of corridor ``c`` (its centre), independent of lane assignment."""
        n = len(self.rows)
        if c == 0:
            return self.corridor_h[0] / 2.0
        top = self.row_y[c - 1] + self.row_h[c - 1]
        if c == n:
            return top + max(self.corridor_h[c], self.lp) / 2.0
        return top + self.corridor_h[c] / 2.0


def layout_overview(graph, fonts, style, area, scale=1.0, collapsed=(), refs=None, annotate=None, max_rows=None):
    """Lay out the whole overview at one symbol scale.

    The sections are laid out internally once; the composition is then evaluated for 1..n rows of layers and the
    most compact arrangement that fits ``area`` is kept (or the least overflowing one).

    Args:
        graph: ``neqsim.process_overview.graph/1`` dict.
        fonts: :class:`ppo_render.Fonts` (text metrics).
        style: style dict (:func:`ppo_render.make_style`).
        area: ``(x, y, w, h)`` drawing area on the page in mm.
        scale: symbol scale (1.0 = base symbol sizes).
        collapsed: section ids drawn as a single block.
        refs: ``{section id: drawing number}`` of detail sheets referenced by collapsed blocks / titles.
        annotate: stream ids that receive an annotation (their corridor lanes get room for a stream-number bubble).
        max_rows: optional cap on the number of layer rows.

    Returns:
        :class:`Layout`; ``fits`` tells whether the content fits ``area``.
    """
    refs = refs or {}
    annotate = set(annotate if annotate is not None else graph["display"]["annotate"])
    sections = [dict(s) for s in graph["sections"]]
    sec_index = {s["id"]: s for s in sections}
    layer, back_pairs, preds, succs = _section_layers(graph, sections)
    order = _order_rows(sections, layer, preds, succs)
    sst = style["section"]
    # streams between two collapsed blocks are bundled into one drawn connection (count shown, members recorded)
    bundles = _bundle_block_streams(graph, set(collapsed))
    skip = {m for b in bundles.values() for m in b["members"][1:]}
    internals, warnings = {}, []
    for s in sections:
        internals[s["id"]] = _section_internal(graph, s, fonts, style, scale, s["id"] in collapsed, refs.get(s["id"]), annotate, skip)
        for w in internals[s["id"]].warnings:
            warnings.append("%s: %s" % (s["name"], w))
    title_h = sst["title_h"]
    size = {}
    for s in sections:
        lay = internals[s["id"]]
        if s["id"] in collapsed:  # the block carries the name; no frame, title band or padding around it
            size[s["id"]] = (lay.width + 2.0, lay.height + 2.0)
            continue
        name_w = fonts.width(s["name"], style["pt"]["section"], True) + 6.0
        if refs.get(s["id"]):
            name_w += fonts.width("detail: " + refs[s["id"]], style["pt"]["desc"]) + 4.0
        size[s["id"]] = (max(lay.width + 2 * sst["pad_x"], name_w), lay.height + 2 * sst["pad_y"] + title_h)
    n_layers = max(layer.values()) + 1 if layer else 1
    col_w = [max([size[s][0] for s in order.get(Lx, [])], default=0.0) for Lx in range(n_layers)]
    col_h = [sum(size[s][1] for s in order.get(Lx, [])) + sst["gap_y"] * max(len(order.get(Lx, [])) - 1, 0) for Lx in range(n_layers)]
    ext = [(sid, a, b) for sid, a, b in _external_streams(graph, sec_index) if sid not in skip]
    if style["annotation"].get("style", "bubble") == "bubble":
        ann_h = style["annotation"].get("bubble_h", 4.8) + 1.4
    else:
        ann_h = fonts.height(style["pt"]["stream"]) * 1.05 + 2 * style["annotation"]["pad"] + sst["lane_pitch"] / 2.0 + 1.0
    ctx = {"graph": graph, "sections": sections, "sec_index": sec_index, "layer": layer, "order": order, "internals": internals,
           "size": size, "col_w": col_w, "col_h": col_h, "ext": ext, "ann_h": ann_h, "annotate": annotate, "sst": sst,
           "style": style, "refs": refs, "collapsed": list(collapsed), "title_h": title_h, "bundles": bundles}
    ax, ay, aw, ah = area
    best, best_score = None, None
    kmax = n_layers if max_rows is None else max(1, min(max_rows, n_layers))
    for k in range(1, kmax + 1):
        rows = _partition(col_w, sst["gap_x"], k)
        if len(rows) != k:
            continue
        L = _compose(ctx, rows)
        cw, ch = L.content_size
        L.fits = cw <= aw + 1e-6 and ch <= ah + 1e-6
        score = (0 if L.fits else 1, max(cw / aw, ch / ah) if not L.fits else cw * ch / (aw * ah), k)
        if best is None or score < best_score:
            best, best_score = L, score
        if L.fits:
            break
    L = best
    L.scale = scale
    L.collapsed = list(collapsed)
    L.bundles = bundles
    L.warnings = warnings + L.warnings
    cw, ch = L.content_size
    bx0, by0, bx1, by1 = L.bbox
    dx = ax + (aw - cw) / 2.0 - bx0
    dy = ay + (ah - ch) / 2.0 - by0
    if not L.fits:
        dx, dy = ax - bx0, ay - by0
    _translate(L, dx, dy)
    L.bbox = (bx0 + dx, by0 + dy, bx1 + dx, by1 + dy)
    return L


def _place(ctx, grid, rows):
    """Section positions and node/edge coordinates for a grid; returns (sections, nodes, edges, dots)."""
    sst, size, order, col_w, col_h = ctx["sst"], ctx["size"], ctx["order"], ctx["col_w"], ctx["col_h"]
    internals, sec_index, title_h = ctx["internals"], ctx["sec_index"], ctx["title_h"]
    row_h = grid.row_h
    sections, offsets = [], {}
    for Lx in range(len(col_w)):
        r = grid.row_of[Lx]
        y = grid.row_y[r] + (row_h[r] - col_h[Lx]) / 2.0
        for sid in order.get(Lx, []):
            s = sec_index[sid]
            w, h = size[sid]
            bx = grid.col_x[Lx] + (col_w[Lx] - w) / 2.0
            lay = internals[sid]
            coll = sid in ctx["collapsed"]
            offsets[sid] = (bx + (w - lay.width) / 2.0, y + (1.0 if coll else title_h + sst["pad_y"]))
            sections.append({"id": sid, "name": s["name"], "x": bx, "y": y, "w": w, "h": h, "layer": Lx, "row_index": r,
                             "row": order[Lx].index(sid), "collapsed": sid in ctx["collapsed"], "members": list(s["members"]),
                             "ref": ctx["refs"].get(sid), "represents": s.get("represents"), "model_area": s.get("model_area"),
                             "model_areas": s.get("model_areas"), "merged_from": s.get("merged_from")})
            y += h + sst["gap_y"]
    nodes, edges, dots = {}, [], []
    for sid, lay in internals.items():
        dx, dy = offsets[sid]

        def qual(nid, _sid=sid):
            return nid + "@" + _sid if nid.startswith(("PI:", "PO:")) else nid

        for n in lay.real:
            info = {"id": qual(n.id), "kind": n.kind, "x": n.cx + dx, "y": n.cy + dy, "w": n.w, "h": n.h, "above": n.above,
                    "below": n.below, "section": sid, "payload": n.payload, "sym": n.sym, "ports": {}}
            for side in ("in", "out"):
                for k in n.order[side]:
                    px, py = n.pxy(side, k)
                    info["ports"][(side, k)] = (px + dx, py + dy)
            nodes[info["id"]] = info
        for e in lay.edges:
            dst = lay.nodes[e.dst]
            edges.append({"id": e.eid + "@" + sid, "stream": e.key, "src": qual(e.src), "dst": qual(e.dst),
                          "pts": [(x + dx, y + dy) for x, y in e.pts], "external": False, "back": bool(e.back), "section": sid,
                          "arrow_end": not (dst.kind == "product" and dst.payload.get("pseudo"))})
        dots.extend((x + dx, y + dy, k) for x, y, k in lay.dots)
    return sections, nodes, edges, dots


def _compose(ctx, rows):
    """Place the sections for one row partition (without mutating the section-internal layouts) and route.

    Two passes: a provisional grid gives the port positions from which corridor tracks and gap channels are assigned
    by interval colouring (runs that do not overlap share a track); the final grid is sized from that demand.
    """
    sst, col_w, col_h, layer = ctx["sst"], ctx["col_w"], ctx["col_h"], ctx["layer"]
    L = Layout()
    L.rows = len(rows)
    row_h = [max([col_h[Lx] for Lx in row], default=0.0) for row in rows]
    grid0 = _Grid(rows, col_w, sst, {}, {}, row_h, 0)
    _, nodes0, edges0, _ = _place(ctx, grid0, rows)
    _snap_port_legs_edges(edges0, nodes0)
    plan = _plan_routes(ctx["ext"], layer, grid0, nodes0, sst, ctx["annotate"], ctx["ann_h"])
    grid = _Grid(rows, col_w, sst, plan["gap_demand"], plan["lane_heights"], row_h, plan["margin_lanes"])
    # second pass on the sized grid: gap widths change the x extents that decide track sharing
    _, nodes1, edges1, _ = _place(ctx, grid, rows)
    _snap_port_legs_edges(edges1, nodes1)
    plan = _plan_routes(ctx["ext"], layer, grid, nodes1, sst, ctx["annotate"], ctx["ann_h"])
    grid = _Grid(rows, col_w, sst, plan["gap_demand"], plan["lane_heights"], row_h, plan["margin_lanes"])
    grid.line_pos = "center" if ctx["style"]["annotation"].get("style", "bubble") == "bubble" else "bottom"
    L.sections, L.nodes, L.edges, L.dots = _place(ctx, grid, rows)
    _snap_port_legs(L)
    _route_external(L, plan, grid, sst)
    xs = [p[0] for e in L.edges for p in e["pts"]] + [s["x"] for s in L.sections] + [s["x"] + s["w"] for s in L.sections]
    ys = [p[1] for e in L.edges for p in e["pts"]] + [s["y"] for s in L.sections] + [s["y"] + s["h"] for s in L.sections]
    bx0, bx1 = (min(xs), max(xs)) if xs else (0.0, 0.0)
    by0, by1 = (min(ys), max(ys)) if ys else (0.0, 0.0)
    L.bbox = (bx0, by0, bx1, by1)
    L.content_size = (bx1 - bx0, by1 - by0)
    return L


def _bundle_block_streams(graph, collapsed):
    """Streams between two collapsed sections grouped per (from, to) pair: ``{rep stream: {"members": [...], ...}}``.

    The representative is the largest flow; the members list keeps every bundled stream id so the mapping stays
    explicit. Boundary streams and streams touching a detailed section are never bundled.
    """
    groups = {}
    for st in graph["streams"]:
        a = st.get("section_from")
        if not a or a not in collapsed:
            continue
        for b in st.get("sections_to") or []:
            if b in collapsed and b != a:
                groups.setdefault((a, b), []).append(st)
    out = {}
    for (a, b), sts in sorted(groups.items()):
        if len(sts) < 2:
            continue
        sts.sort(key=lambda s: (-((s.get("values") or {}).get("mass_kg_h") or 0.0), s["id"]))
        out[sts[0]["id"]] = {"from": a, "to": b, "members": [s["id"] for s in sts], "count": len(sts)}
    return out


def _translate(L, dx, dy):
    for s in L.sections:
        s["x"] += dx
        s["y"] += dy
    for n in L.nodes.values():
        n["x"] += dx
        n["y"] += dy
        n["ports"] = {k: (x + dx, y + dy) for k, (x, y) in n["ports"].items()}
    for e in L.edges:
        e["pts"] = [(x + dx, y + dy) for x, y in e["pts"]]
    L.dots = [(x + dx, y + dy, k) for x, y, k in L.dots]


def _colour_intervals(items, clearance):
    """Greedy interval colouring: ``items`` are ``(lo, hi, key)``; returns ``{key: track}`` and the track count.

    Items are taken in the given order; each goes to the first track whose last interval ends before ``lo - clearance``.
    """
    tracks_end = []
    out = {}
    for lo, hi, key in items:
        placed = None
        for t, end in enumerate(tracks_end):
            if end + clearance <= lo:
                placed = t
                break
        if placed is None:
            tracks_end.append(hi)
            placed = len(tracks_end) - 1
        else:
            tracks_end[placed] = hi
        out[key] = placed
    return out, len(tracks_end)


def _plan_routes(ext, layer, grid, nodes, sst, annotate=(), ann_h=None):
    """Decide gap channels, corridor tracks and margin lanes of every inter-section connection on a provisional grid."""
    lp = sst["lane_pitch"]
    ann_h = ann_h or lp
    routes = []
    for sid, a, b in ext:
        po = nodes.get("PO:%s@%s" % (sid, a))
        pi = nodes.get("PI:%s@%s" % (sid, b))
        if po is None or pi is None:
            continue
        la, lb = layer[a], layer[b]
        ra, rb = grid.row_of[la], grid.row_of[lb]
        ka, kb = grid.pos_of[la] + 1, grid.pos_of[lb]
        r = {"stream": sid, "a": a, "b": b, "la": la, "lb": lb, "ra": ra, "rb": rb, "gap_a": _gkey(ra, ka), "gap_b": _gkey(rb, kb),
             "p0": po["ports"].get(("in", sid), (po["x"], po["y"])), "p1": pi["ports"].get(("out", sid), (pi["x"], pi["y"])),
             "via_margin": False, "corridors": [], "annotated": sid in annotate}
        if ra == rb and lb == la + 1:
            r["kind"] = "adjacent"
            r["gap_b"] = r["gap_a"]
        elif ra == rb:
            r["kind"] = "forward" if lb > la else "back"
            row_top, row_bot = grid.row_y[ra], grid.row_y[ra] + grid.row_h[ra]
            mid = (r["p0"][1] + r["p1"][1]) / 2.0
            r["corridors"] = [ra if (mid - row_top) < (row_bot - mid) else ra + 1]
        elif rb > ra:
            r["kind"] = "down"
            r["corridors"] = [ra + 1] if rb == ra + 1 else [ra + 1, rb]
            r["via_margin"] = rb > ra + 1
        else:
            r["kind"] = "up"
            r["corridors"] = [ra] if rb == ra - 1 else [ra, rb + 1]
            r["via_margin"] = rb < ra - 1
        routes.append(r)
    # corridor tracks: horizontal runs coloured by x extent (provisional gap centres)
    gx = {g: sum(v) / 2.0 for g, v in grid.gap_x.items()}
    margin_x = -lp
    runs = {}
    for r in routes:
        if r["kind"] == "adjacent":
            continue
        xa, xb = gx[r["gap_a"]], gx[r["gap_b"]]
        if r["via_margin"]:
            runs.setdefault(r["corridors"][0], []).append((min(xa, margin_x), max(xa, margin_x), (r["stream"], r["a"], r["b"], 0)))
            runs.setdefault(r["corridors"][1], []).append((min(xb, margin_x), max(xb, margin_x), (r["stream"], r["a"], r["b"], 1)))
        else:
            runs.setdefault(r["corridors"][0], []).append((min(xa, xb), max(xa, xb), (r["stream"], r["a"], r["b"], 0)))
    lane_heights = {}
    track_of = {}
    for c, items in runs.items():
        items.sort(key=lambda it: (it[0], it[1], it[2]))
        tr, n = _colour_intervals(items, clearance=4.0)
        heights = [lp] * n
        for lo, hi, key in items:
            sid = key[0]
            if sid in annotate:
                heights[tr[key]] = max(heights[tr[key]], ann_h)
        lane_heights[c] = heights
        track_of.update(tr)
    for r in routes:
        r["lanes"] = [(c, track_of[(r["stream"], r["a"], r["b"], i)]) for i, c in enumerate(r["corridors"])]
    # margin lanes (vertical runs left of the content) coloured by y extent
    margin_items = []
    for r in routes:
        if r["via_margin"]:
            y0 = grid.row_y[r["ra"]]
            y1 = grid.row_y[r["rb"]]
            margin_items.append((min(y0, y1), max(y0, y1), (r["stream"], r["a"], r["b"])))
    margin_items.sort()
    mtr, n_margin = _colour_intervals(margin_items, clearance=6.0)
    for r in routes:
        if r["via_margin"]:
            r["margin_lane"] = mtr[(r["stream"], r["a"], r["b"])]
    # gap channels: vertical runs coloured by y extent; departing before arriving, direction rule as in psr_diagrams
    gap_items = {}
    for r in routes:
        if r["kind"] == "adjacent":
            gap_items.setdefault(r["gap_a"], []).append((r, "single"))
        else:
            gap_items.setdefault(r["gap_a"], []).append((r, "out"))
            gap_items.setdefault(r["gap_b"], []).append((r, "in"))
    gap_demand = {}
    keys = sorted(gap_items)
    members = {k: [k] for k in keys}
    merged_items = {k: gap_items[k] for k in keys}
    for g, items in merged_items.items():
        def extent(item):
            r, role = item
            if role == "single":
                return r["p0"][1], r["p1"][1]
            ly = grid.corridor_y(*r["lanes"][0 if role == "out" else -1]) if r["lanes"] else r["p0"][1]
            return (r["p0"][1], ly) if role == "out" else (ly, r["p1"][1])

        def key(item):
            r, role = item
            ys, ye = extent(item)
            down = ye > ys
            return (1 if role == "in" else 0, 0 if down else 1, -ys if down else ys, r["stream"])
        items.sort(key=key)
        # one channel per (stream, role, section) group; groups keep their sort order and share channels when disjoint in y
        groups = []
        for item in items:
            r, role = item
            gk = (r["stream"], "out" if role != "in" else "in", r["a"] if role != "in" else r["b"])
            found = next((gr for gr in groups if gr["key"] == gk), None)
            if found is None:
                groups.append({"key": gk, "items": [item], "lo": min(extent(item)), "hi": max(extent(item))})
            else:
                found["items"].append(item)
                found["lo"] = min(found["lo"], min(extent(item)))
                found["hi"] = max(found["hi"], max(extent(item)))
        tr, n = _colour_intervals([(gr["lo"], gr["hi"], gr["key"]) for gr in groups], clearance=5.0)
        gap_demand[g] = n
        for gr in groups:
            for r, role in gr["items"]:
                r.setdefault("ch", {})[role] = tr[gr["key"]]
    return {"routes": routes, "gap_demand": gap_demand, "lane_heights": lane_heights, "margin_lanes": n_margin}


def _snap_port_legs_edges(edges, nodes):
    """Variant of :func:`_snap_port_legs` on plain node/edge containers (provisional pass)."""
    L = Layout()
    L.nodes, L.edges = nodes, edges
    _snap_port_legs(L)


def _snap_port_legs(L, tol=2.5):
    """Remove the tiny Z-step the layered router leaves between a unit port and a section port pseudo node.

    A port pseudo node with a single leg is moved onto the y of the unit port it serves and the leg becomes straight;
    the external route then starts/ends exactly on that line.
    """
    count = {}
    for e in L.edges:
        if e["external"]:
            continue
        for nid in (e["src"], e["dst"]):
            if nid.startswith(("PI:", "PO:")):
                count[nid] = count.get(nid, 0) + 1
    for e in L.edges:
        if e["external"] or len(e["pts"]) < 3:
            continue
        for end, nid in (("src", e["src"]), ("dst", e["dst"])):
            if not nid.startswith(("PI:", "PO:")) or count.get(nid) != 1:
                continue
            pts = e["pts"]
            p_port, p_unit = (pts[0], pts[-1]) if end == "src" else (pts[-1], pts[0])
            if abs(p_port[1] - p_unit[1]) >= tol:
                continue
            xs = [p[0] for p in pts]
            if xs != sorted(xs) and xs != sorted(xs, reverse=True):
                continue
            new_port = (p_port[0], p_unit[1])
            e["pts"] = [new_port, p_unit] if end == "src" else [p_unit, new_port]
            node = L.nodes[nid]
            dy = new_port[1] - node["y"]
            node["y"] = new_port[1]
            node["ports"] = {k: (x, y + dy) for k, (x, y) in node["ports"].items()}


def _channel_x(r, role, grid, plan, pitch):
    """x of the channel used by route ``r`` in role ``single`` / ``out`` / ``in`` (clamped into its gap)."""
    gkey = r["gap_a"] if role != "in" else r["gap_b"]
    left, right = grid.gap_x[gkey]
    n = max(plan["gap_demand"].get(gkey, 1), 1)
    start = left + ((right - left) - (n - 1) * pitch) / 2.0
    x = start + r["ch"][role] * pitch
    return min(max(x, left + 0.6), right - 0.6)


def _route_pts(r, L, grid, plan, sst):
    """Polyline of one planned connection on the final grid."""
    pitch = sst["channel_pitch"]
    p0, p1 = r["p0"], r["p1"]
    if r["kind"] == "adjacent":
        x = _channel_x(r, "single", grid, plan, pitch)
        pts = [p0, p1] if abs(p0[1] - p1[1]) < 0.15 else [p0, (x, p0[1]), (x, p1[1]), p1]
    else:
        xa, xb = _channel_x(r, "out", grid, plan, pitch), _channel_x(r, "in", grid, plan, pitch)
        ly = [grid.corridor_y(c, i) for c, i in r["lanes"]]
        if r["kind"] in ("forward", "back"):
            y_band = _in_row_band(L, r, xa, xb, sst) if r["kind"] == "forward" else None
            yy = y_band if y_band is not None else ly[0]
            pts = [p0, (xa, p0[1]), (xa, yy), (xb, yy), (xb, p1[1]), p1]
        elif not r["via_margin"]:
            pts = [p0, (xa, p0[1]), (xa, ly[0]), (xb, ly[0]), (xb, p1[1]), p1]
        else:
            xl = grid.margin_x + sst["lane_pitch"] * r["margin_lane"]
            pts = [p0, (xa, p0[1]), (xa, ly[0]), (xl, ly[0]), (xl, ly[1]), (xb, ly[1]), (xb, p1[1]), p1]
    return _psr._simplify(pts)


def _segments(pts):
    out = []
    for i, (p, q) in enumerate(zip(pts[:-1], pts[1:])):
        if abs(p[1] - q[1]) < 1e-6:
            out.append(("h", p[1], min(p[0], q[0]), max(p[0], q[0]), i))
        elif abs(p[0] - q[0]) < 1e-6:
            out.append(("v", p[0], min(p[1], q[1]), max(p[1], q[1]), i))
    return out


def _repair_routes(routes, L, grid, plan, sst, max_iter=30):
    """Resolve coincident verticals (different gaps, same x) and collinear horizontals by moving/swapping channels."""
    pitch = sst["channel_pitch"]
    for _ in range(max_iter):
        changed = False
        segs = []
        for r in routes:
            if r.get("skip"):
                continue
            for s in _segments(r["pts"]):
                segs.append((r, s))
        for i, (ra, sa) in enumerate(segs):
            for rb, sb in segs[i + 1:]:
                if ra is rb or sa[0] != sb[0]:
                    continue
                ov = min(sa[3], sb[3]) - max(sa[2], sb[2])
                if ov <= 0.3 or abs(sa[1] - sb[1]) > 0.8 * pitch:
                    continue
                # which route can move? prefer the one whose segment is a channel vertical (role known)
                for r, s, other in ((rb, sb, ra), (ra, sa, rb)):
                    role = _segment_role(r, s)
                    if role is None:
                        continue
                    gkey = r["gap_a"] if role != "in" else r["gap_b"]
                    n = max(plan["gap_demand"].get(gkey, 1), 1)
                    cur = r["ch"][role]
                    others = [x for x in routes if x is not r and not x.get("skip")]
                    for cand in sorted([c for c in range(n + 1) if c != cur], key=lambda c: (abs(c - cur), c)):
                        r["ch"][role] = cand
                        if cand >= n:  # one more channel in this gap: all channels of the gap re-centre
                            plan["gap_demand"][gkey] = n + 1
                            for x in routes:
                                if not x.get("skip"):
                                    x["pts"] = _route_pts(x, L, grid, plan, sst)
                        new_pts = _route_pts(r, L, grid, plan, sst)
                        if not any(_conflicts(new_pts, o["pts"], pitch) for o in others):
                            r["pts"] = new_pts
                            changed = True
                            break
                        r["ch"][role] = cur
                        if cand >= n:
                            plan["gap_demand"][gkey] = n
                            for x in routes:
                                if not x.get("skip"):
                                    x["pts"] = _route_pts(x, L, grid, plan, sst)
                    if changed:
                        break
                if changed:
                    break
            if changed:
                break
        if not changed:
            return


def _segment_role(r, s):
    """Role (``single``/``out``/``in``) of a vertical channel segment, or the role whose horizontal leg it is."""
    if r["kind"] == "adjacent":
        return "single"
    xa = r["pts"][1][0] if len(r["pts"]) > 1 else None
    xb = r["pts"][-2][0] if len(r["pts"]) > 1 else None
    if s[0] == "v":
        if xa is not None and abs(s[1] - xa) < 1e-6:
            return "out"
        if xb is not None and abs(s[1] - xb) < 1e-6:
            return "in"
        return None
    # horizontal: the first leg belongs to the departing channel, the last to the arriving one
    if s[4] == 0:
        return "out"
    if s[4] == len(r["pts"]) - 2:
        return "in"
    return None


def _conflicts(pts_a, pts_b, pitch):
    for sa in _segments(pts_a):
        for sb in _segments(pts_b):
            if sa[0] != sb[0]:
                continue
            ov = min(sa[3], sb[3]) - max(sa[2], sb[2])
            if ov > 0.3 and abs(sa[1] - sb[1]) <= 0.8 * pitch:
                return True
    return False


def _route_external(L, plan, grid, sst):
    """Build the orthogonal polylines of the planned inter-section connections on the final grid."""
    routes = plan["routes"]
    for r in routes:
        po = L.nodes.get("PO:%s@%s" % (r["stream"], r["a"]))
        pi = L.nodes.get("PI:%s@%s" % (r["stream"], r["b"]))
        if po is None or pi is None:
            L.warnings.append("external stream %s between %s and %s has no port nodes" % (r["stream"], r["a"], r["b"]))
            r["skip"] = True
            continue
        r["p0"] = po["ports"].get(("in", r["stream"]), (po["x"] + po["w"] / 2.0, po["y"]))
        r["p1"] = pi["ports"].get(("out", r["stream"]), (pi["x"] - pi["w"] / 2.0, pi["y"]))
        r["pts"] = _route_pts(r, L, grid, plan, sst)
    _repair_routes(routes, L, grid, plan, sst)
    by_stream = {}
    for r in routes:
        if r.get("skip"):
            continue
        eid = "X:%s:%s:%s" % (r["stream"], r["a"], r["b"])
        L.edges.append({"id": eid, "stream": r["stream"], "src": "PO:%s@%s" % (r["stream"], r["a"]),
                        "dst": "PI:%s@%s" % (r["stream"], r["b"]), "pts": r["pts"], "external": True,
                        "back": r["kind"] in ("back", "up"), "arrow_end": False, "sections": (r["a"], r["b"]),
                        "role": "wrap" if r["kind"] in ("down", "up") else ""})
        by_stream.setdefault(r["stream"], []).append(r["pts"])
    # junction dots where one stream fans out to several sections (shared leading run)
    for sid, plist in by_stream.items():
        if len(plist) < 2:
            continue
        first = plist[0]
        for other in plist[1:]:
            shared = None
            for p, q in zip(first, other):
                if abs(p[0] - q[0]) < 1e-6 and abs(p[1] - q[1]) < 1e-6:
                    shared = p
                else:
                    break
            if shared is not None and shared != first[0]:
                L.dots.append((shared[0], shared[1], sid))


def _in_row_band(L, r, xa, xb, sst):
    """Free horizontal band between stacked sections across the columns a forward skip passes (same row)."""
    lo_x, hi_x = min(xa, xb), max(xa, xb)
    blocked = sorted((s["y"], s["y"] + s["h"]) for s in L.sections
                     if s["row_index"] == r["ra"] and s["x"] < hi_x and s["x"] + s["w"] > lo_x and s["layer"] not in (r["la"], r["lb"]))
    if not blocked:
        return (r["p0"][1] + r["p1"][1]) / 2.0 if abs(r["p0"][1] - r["p1"][1]) < 0.15 else None
    ymid = (r["p0"][1] + r["p1"][1]) / 2.0
    need = 2.0 * sst["lane_pitch"]
    best = None
    for (y0a, y1a), (y0b, y1b) in zip(blocked[:-1], blocked[1:]):
        if y0b - y1a >= need:
            c = (y1a + y0b) / 2.0
            d = abs(c - ymid)
            if best is None or d < best[0]:
                best = (d, c)
    return best[1] if best else None


# ---------------------------------------------------------------------------
# annotations and arrows (collision aware; reuses psr_diagrams placement)
# ---------------------------------------------------------------------------
class _E:
    def __init__(self, eid, key, pts):
        self.eid, self.key, self.pts = eid, key, pts


def node_rects(L, fonts, style):
    """Obstacle rectangles of all drawn nodes (symbols, tags, descriptions, boundary labels)."""
    rects = []
    pt = style["pt"]
    for n in L.nodes.values():
        if n["kind"] == "unit":
            sym = n["sym"]
            rects.append((n["x"] - sym.w / 2, n["y"] - sym.h / 2, n["x"] + sym.w / 2, n["y"] + sym.h / 2))
            rects.append((n["x"] - n["w"] / 2, n["y"] - n["above"], n["x"] + n["w"] / 2, n["y"] - sym.h / 2))
            rects.append((n["x"] - n["w"] / 2, n["y"] + sym.h / 2, n["x"] + n["w"] / 2, n["y"] + n["below"]))
        elif n["kind"] == "block":
            rects.append((n["x"] - n["w"] / 2, n["y"] - n["h"] / 2, n["x"] + n["w"] / 2, n["y"] + n["h"] / 2))
        elif n["kind"] in ("feed", "product") and not n["payload"].get("pseudo"):
            lines = n["payload"]["lines"]
            tw = max(fonts.width(t, pt["desc"]) for t in lines)
            if n["kind"] == "feed":
                x0 = n["x"] - n["w"] / 2 + 0.5
            else:
                x0 = n["x"] + n["w"] / 2 - 0.5 - tw
            rects.append((x0, n["y"] - n["above"], x0 + tw, n["y"] - 0.6))
    for s in L.sections:
        t = style["section"]["title_h"]
        rects.append((s["x"], s["y"], s["x"] + s["w"], s["y"] + t))
        rects.append((s["x"] - 0.3, s["y"], s["x"] + 0.3, s["y"] + s["h"]))
        rects.append((s["x"] + s["w"] - 0.3, s["y"], s["x"] + s["w"] + 0.3, s["y"] + s["h"]))
        rects.append((s["x"], s["y"] + s["h"] - 0.3, s["x"] + s["w"], s["y"] + s["h"] + 0.3))
    return rects


def place_annotations(L, graph, fonts, style, annotate, area):
    """Place stream data: boundary streams get their data under the boundary label; other annotated streams get a
    stream-number bubble on the line (``style["annotation"]["style"] == "bubble"``, data in the key-stream table) or a
    collision-free label box beside the line (``"box"``)."""
    pt = style["pt"]["stream"]
    pad = style["annotation"]["pad"]
    mode = style["annotation"].get("style", "bubble")
    sidx = {s["id"]: s for s in graph["streams"]}
    edges = [_E(e["id"], e["stream"], e["pts"]) for e in L.edges]
    rects = node_rects(L, fonts, style) + _psr._end_rects(edges)
    L.obstacles = rects
    lh = fonts.height(pt) * 1.05
    ax, ay, aw, ah = area
    # attached boundary annotations
    attached = set()
    for n in L.nodes.values():
        if n["kind"] in ("feed", "product") and not n["payload"].get("pseudo") and n["payload"].get("annotation"):
            ann = n["payload"]["annotation"]
            tw = max(fonts.width(t, pt) for t in ann)
            if n["kind"] == "feed":
                x0 = n["x"] - n["w"] / 2.0 + 0.8
            else:
                x0 = n["x"] + n["w"] / 2.0 - 0.8 - tw
            y0 = n["y"] + 1.2
            r = (x0, y0, x0 + tw, y0 + len(ann) * lh + 0.4)
            L.annotations.append({"stream": n["payload"]["stream"], "lines": ann, "bbox": r, "x": x0, "y": y0, "attached": True,
                                  "fallback": False})
            rects.append(r)
            attached.add(n["payload"]["stream"])
    marks, texts = [], {}
    bw, bh = style["annotation"].get("bubble_w", 8.0), style["annotation"].get("bubble_h", 4.8)
    badge_pt = style["pt"]["badge"]
    for rep, b in sorted(L.bundles.items()):
        own = [e for e in edges if e.key == rep and any(x["id"] == e.eid and x.get("external") for x in L.edges)]
        if not own:
            continue
        label = "x%d" % b["count"]
        w = fonts.width(label, badge_pt, True) + 2.4
        marks.append({"key": "bundle:" + rep, "edges": own, "variants": [{"w": w, "h": bh, "mode": "center"}], "fallback_last": False})
        texts["bundle:" + rep] = ("bundle", label, rep)
    for sid in annotate:
        st = sidx.get(sid)
        if st is None or sid in attached:
            continue
        own = [e for e in edges if e.key == sid]
        if not own:
            continue
        own.sort(key=lambda e: -max((abs(q[0] - p[0]) for p, q in zip(e.pts[:-1], e.pts[1:])), default=0.0))
        if mode == "bubble":
            w = max(bw, fonts.width(sid, style["pt"]["badge"], True) + 2.0)
            marks.append({"key": sid, "edges": own, "variants": [{"w": w, "h": bh, "mode": "center"}], "fallback_last": False})
            texts[sid] = None
        else:
            lines = stream_annotation_lines(st, graph["level"], graph["source"].get("standard_conditions"))
            wide = max(fonts.width(t, pt) for t in lines) + 2 * pad
            tall = len(lines) * lh + 2 * pad
            one = [" ".join(lines)]
            w1, h1 = fonts.width(one[0], pt) + 2 * pad, lh + 2 * pad
            marks.append({"key": sid, "edges": own, "fallback_last": False,
                          "variants": [{"w": wide, "h": tall, "mode": "above"}, {"w": wide, "h": tall, "mode": "below"},
                                       {"w": w1, "h": h1, "mode": "above"}, {"w": w1, "h": h1, "mode": "below"}]})
            texts[sid] = (lines, one)
    placed, fallback = _psr._place_marks(marks, edges, rects, 10.0, 6.0, "above", step=1.0)
    for key_, (x, y, r, vi) in placed.items():
        if key_ in fallback:
            continue  # a forced, overlapping mark is worse than none; reported by the validator
        if r[0] < ax - 1 or r[2] > ax + aw + 1 or r[1] < ay - 1 or r[3] > ay + ah + 1:
            L.warnings.append("annotation %s lies outside the drawing area" % key_)
        t = texts[key_]
        if isinstance(t, tuple) and t and t[0] == "bundle":
            L.bubbles.append({"stream": t[2], "x": x, "y": y, "bbox": r, "text": t[1], "bundle": True})
        elif t is None:
            L.bubbles.append({"stream": key_, "x": x, "y": y, "bbox": r, "text": key_})
        else:
            lines, one = t
            L.annotations.append({"stream": key_, "lines": lines if vi < 2 else one, "bbox": r, "x": x, "y": y, "attached": False,
                                  "fallback": False})
    L.unplaced_annotations = [k for k in fallback if not k.startswith("bundle:")]
    for sid in fallback:
        L.warnings.append("stream %s: no collision-free position for its %s; not drawn" % (sid, "bubble" if mode == "bubble" else "label"))
    L.mid_arrows = _psr._mid_arrows(edges, rects, [a["bbox"] for a in L.annotations] + [b["bbox"] for b in L.bubbles],
                                    min_h=22.0, min_v=26.0)
    return L


# ---------------------------------------------------------------------------
# pfd_layout_check spec
# ---------------------------------------------------------------------------
def merged_edges(L):
    """Edges as complete unit-to-unit (or boundary) polylines: internal legs joined through the port pseudo nodes.

    Returns a list of dicts ``{id, stream, src, dst, pts, back, arrow_end, parts}`` where ``src``/``dst`` are node ids
    of real nodes (``U:``, ``B:``, ``F:``, ``P:``).
    """
    into_po = {}  # PO node id -> internal edge ending there
    from_pi = {}  # PI node id -> [internal edges starting there]
    for e in L.edges:
        if e["external"]:
            continue
        if e["dst"].startswith("PO:"):
            into_po[e["dst"]] = e
        if e["src"].startswith("PI:"):
            from_pi.setdefault(e["src"], []).append(e)
    out = []
    used = set()
    for x in L.edges:
        if not x["external"]:
            continue
        head = into_po.get(x["src"])
        tails = from_pi.get(x["dst"], [])
        if head is None or not tails:
            continue
        used.add(head["id"])
        for t in tails:
            used.add(t["id"])
            pts = _psr._simplify(list(head["pts"]) + list(x["pts"]) + list(t["pts"]))
            out.append({"id": "M:%s:%s>%s" % (x["stream"], head["src"], t["dst"]), "stream": x["stream"], "src": head["src"],
                        "dst": t["dst"], "pts": pts, "back": x["back"] or head["back"] or t["back"], "arrow_end": t["arrow_end"],
                        "parts": [head["id"], x["id"], t["id"]], "external": True, "role": x.get("role", "")})
        used.add(x["id"])
    for e in L.edges:
        if e["id"] in used or e["external"]:
            continue
        if e["src"].startswith(("PI:", "PO:")) or e["dst"].startswith(("PI:", "PO:")):
            continue  # leg of an unmatched connection; reported by the validator
        out.append(dict(e, parts=[e["id"]]))
    return out


def to_layout_spec(L, graph, fonts, style, page, name, extra_bboxes=None, waivers=None):
    """``neqsim.pfd_layout.v1`` spec of the drawn geometry (page mm coordinates)."""
    pt = style["pt"]
    sidx = {s["id"]: s for s in graph["streams"]}
    nodes = []
    for n in L.nodes.values():
        if n["kind"] == "unit":
            sym = n["sym"]
            kind = {"sep2": "separator", "sep3": "separator", "scrubber": "scrubber", "column": "column", "compressor": "compressor",
                    "expander": "expander", "pump": "pump", "heater": "heater", "cooler": "cooler", "exchanger": "exchanger",
                    "valve": "valve", "mixer": "mixer", "splitter": "splitter", "generic": "equipment"}.get(sym.key, "equipment")
            texts = []
            lh = fonts.height(pt["tag"]) * 1.05
            y = n["y"] - sym.h / 2.0 - 1.0 - (len(n["payload"]["tag_lines"]) - 1) * lh
            for t in n["payload"]["tag_lines"]:
                tw = fonts.width(t, pt["tag"], True)
                texts.append({"text": t, "x": n["x"], "y": y, "font_size": pt["tag"] * MM_PER_PT, "anchor": "middle", "bold": True,
                              "kind": "tag_above", "bbox": [n["x"] - tw / 2.0, y - fonts.cap(pt["tag"]), tw, lh]})
                y += lh
            nodes.append({"id": n["payload"]["tag"], "kind": kind, "tag": n["payload"]["tag"],
                          "bbox": [n["x"] - sym.w / 2.0, n["y"] - sym.h / 2.0, sym.w, sym.h], "texts": texts})
        elif n["kind"] == "block":
            nodes.append({"id": n["payload"]["section"], "kind": "block",
                          "bbox": [n["x"] - n["w"] / 2.0, n["y"] - n["h"] / 2.0, n["w"], n["h"]], "texts": []})
        elif n["kind"] in ("feed", "product") and not n["payload"].get("pseudo"):
            tw = max(fonts.width(t, pt["desc"]) for t in n["payload"]["lines"])
            x0 = n["x"] - n["w"] / 2.0 if n["kind"] == "feed" else n["x"] + n["w"] / 2.0 - tw
            nodes.append({"id": "B:" + n["payload"]["stream"], "kind": "boundary",
                          "bbox": [x0, n["y"] - n["above"], max(tw, 2.0), n["above"] + 0.6], "texts": []})

    def spec_id(nid):
        if nid.startswith("U:"):
            return nid[2:]
        if nid.startswith("B:"):
            return nid[2:]
        if nid[:2] in ("F:", "P:"):
            return "B:" + nid[2:]
        return None

    edges = []
    for e in merged_edges(L):
        st = sidx.get(e["stream"], {})
        rec = {"id": e["id"], "from": spec_id(e["src"]), "to": spec_id(e["dst"]),
               "points": [[round(x, 3), round(y, 3)] for x, y in e["pts"]],
               "fluid": st.get("service", "other"), "stream": e["stream"], "arrow_end": bool(e.get("arrow_end")),
               "role": "recycle" if (e.get("back") or st.get("role") == "recycle") else e.get("role", ""),
               "arrow_mid": any(a[4] == e["stream"] for a in L.mid_arrows)}
        edges.append(rec)
    labels = []
    for a in L.annotations:
        x0, y0, x1, y1 = a["bbox"]
        lab = {"text": " / ".join(a["lines"]), "bbox": [x0, y0, x1 - x0, y1 - y0], "font_size": pt["stream"] * MM_PER_PT,
               "kind": "stream", "stream": a["stream"]}
        if a.get("attached"):
            lab["owner"] = "B:" + a["stream"]
        labels.append(lab)
    spec = {"schema": _psr_check_schema(), "kind": "pfd", "name": name, "title": name,
            "canvas": {"width": page["w"], "height": page["h"], "unit": "mm", "intended_width_mm": page["w"]},
            "nodes": nodes, "edges": edges, "labels": labels,
            "junctions": [[x, y] for x, y, _ in L.dots], "waivers": list(waivers or []),
            "options": {"require_footer": False, "min_port_sep_frac": 4.0 / math.hypot(page["w"], page["h"])},
            "legend": {"present": True}}
    if extra_bboxes:
        for key in ("legend", "title_block"):
            if extra_bboxes.get(key):
                x0, y0, x1, y1 = extra_bboxes[key]
                spec[key] = {"bbox": [x0, y0, x1 - x0, y1 - y0]}
    return spec


def _psr_check_schema():
    try:
        import pfd_layout_check
        return pfd_layout_check.SCHEMA
    except Exception:  # noqa: BLE001
        return "neqsim.pfd_layout.v1"
