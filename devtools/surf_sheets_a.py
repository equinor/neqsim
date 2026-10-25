"""Layout, network, process and profile sheets of the SURF drawing package (A3 landscape)."""
import math

import fa_draw
import surf_design
import surf_draw as D
from fa_draw import INK, GREY, RED
from psr_diagrams import kit as K

AREA = (14.0, 26.0, 406.0, 218.0)


def _design_case(ds):
    return next(c["id"] for c in ds["cases"] if c["role"] == "design")


def _clip_segment(p, q, rect):
    """Liang-Barsky clip of segment p-q to ``rect`` (x0, y0, x1, y1); returns the clipped segment or None."""
    x0, y0, x1, y1 = rect
    dx, dy = q[0] - p[0], q[1] - p[1]
    t0, t1 = 0.0, 1.0
    for pp, qq in ((-dx, p[0] - x0), (dx, x1 - p[0]), (-dy, p[1] - y0), (dy, y1 - p[1])):
        if pp == 0:
            if qq < 0:
                return None
            continue
        r = qq / pp
        if pp < 0:
            if r > t1:
                return None
            t0 = max(t0, r)
        else:
            if r < t0:
                return None
            t1 = min(t1, r)
    return (p[0] + t0 * dx, p[1] + t0 * dy), (p[0] + t1 * dx, p[1] + t1 * dy)


def _plan_lines(sh, ds, pm, rect, label_lines):
    """Draw every concept line inside ``rect`` (clipped); label the long ones near the middle of their visible part."""
    ref = ds["reference_concept"]
    lines, geom = D.concept_edges(ds, ref), ds["geometry"][ref]
    for eid, r in lines.items():
        pts = [pm.pt(p[0], p[1]) for p in geom[eid]["points"]]
        segs = []
        for a, b in zip(pts[:-1], pts[1:]):
            c = _clip_segment(a, b, rect)
            if c:
                segs.append(c)
        for a, b in segs:
            D.polyline(sh, [a, b], D.COLORS[r["service"]], 0.7 if r["kind"] in ("trunkline", "flowline", "riser") else 0.5, None if r["service"] == "production" else "dash")
        if segs and label_lines(r):
            a, b = max(segs, key=lambda s: math.hypot(s[1][0] - s[0][0], s[1][1] - s[0][1]))
            if math.hypot(b[0] - a[0], b[1] - a[1]) > 25.0:
                sh.text((a[0] + b[0]) / 2.0, (a[1] + b[1]) / 2.0 + (-7.4 if r["service"] == "production" else 10.0),
                        "%s  %.0f x %.1f mm  L %.2f km" % (r["tag"], r["od_mm"] or 0.0, r["wall_mm"] or 0.0, r["installed_length_m"] / 1000.0) if r["wall_mm"] else "%s  ID %.0f mm  L %.2f km" % (r["tag"], r["id_mm"], r["installed_length_m"] / 1000.0),
                        2.5, "middle", fill=D.COLORS[r["service"]], bold=True)


def _kp_marks(sh, ds, pm):
    """KP ticks (km from the host inlet) along the riser and trunk of the production path."""
    ref = ds["reference_concept"]
    lines, geom = D.concept_edges(ds, ref), ds["geometry"][ref]
    riser = next((r for r in lines.values() if r["kind"] == "riser" and r["service"] == "production"), None)
    trunk = next((r for r in lines.values() if r["kind"] == "trunkline"), None)
    if not (riser and trunk):
        return
    g = geom[trunk["id"]]
    ch = g["chainage_m"]
    scale = trunk["installed_length_m"] / ch[-1]
    for km in range(2, int((riser["installed_length_m"] + trunk["installed_length_m"]) // 1000) + 1, 2):
        dd = trunk["installed_length_m"] - (km * 1000.0 - riser["installed_length_m"])
        if dd < 0 or dd > trunk["installed_length_m"]:
            continue
        for i in range(len(ch) - 1):
            if ch[i] * scale <= dd <= ch[i + 1] * scale:
                t = (dd - ch[i] * scale) / max((ch[i + 1] - ch[i]) * scale, 1e-9)
                p0, p1 = g["points"][i], g["points"][i + 1]
                x, y = pm.pt(p0[0] + t * (p1[0] - p0[0]), p0[1] + t * (p1[1] - p0[1]))
                sh.add(K.line(x, y - 1.6, x, y + 1.6, sw=0.4))
                sh.text(x, y - 3.6, "KP %d" % km, 2.3, "middle", fill=GREY)
                break


def field_plan(sh, ds, rs, ctx):
    """DWG-01: overall field layout plan of the reference concept (overall strip and template-area detail; lines to scale, symbols exaggerated)."""
    ref = ds["reference_concept"]
    nodes, lines, geom = D.concept_nodes(ds, ref), D.concept_edges(ds, ref), ds["geometry"][ref]
    allp = [(n["x_m"], n["y_m"]) for n in nodes.values()] + [(p[0], p[1]) for g in geom.values() for p in g["points"]]
    rect_a = (22.0, 52.0, 400.0, 104.0)
    pm_a = D.PlanMap(D.bbox_of(allp, 150.0), rect_a)
    tpl = [(n["x_m"], n["y_m"]) for n in nodes.values() if n.get("structure")]
    bb = D.bbox_of(tpl, 260.0)
    rect_b = (22.0, 132.0, 400.0, 206.0)
    pm_b = D.PlanMap(bb, rect_b)
    den_a, den_b = int(round(pm_a.scale_denominator, -2)), int(round(pm_b.scale_denominator, -2))
    sh.scale_text = "overall 1:%s, detail 1:%s (equal x and y); symbols not to scale" % (format(den_a, ",d").replace(",", " "), format(den_b, ",d").replace(",", " "))
    sh.tb["scale"] = sh.scale_text
    sh.text(22.0, 31.0, "Reference concept %s (%s). Coordinates: local synthetic grid (m); vertical datum mean sea level." % (ref, ds["concepts"][ref]["label"]), 2.8, bold=True)
    sh.text(22.0, 40.0, "OVERALL PLAN 1:%s" % format(den_a, ",d").replace(",", " "), 2.8, bold=True)
    sh.text(22.0, 120.0, "TEMPLATE AREA DETAIL 1:%s" % format(den_b, ",d").replace(",", " "), 2.8, bold=True)
    for eid, r in lines.items():
        sh.items.append({"type": "line", "id": eid, "tag": r["tag"], "service": r["service"], "length_m": r["installed_length_m"]})
    _plan_lines(sh, ds, pm_a, (rect_a[0] - 2, rect_a[1] - 12, rect_a[2] + 2, rect_a[3] + 14), lambda r: r["kind"] == "trunkline" or (r["service"] == "injection" and r["kind"] == "flowline" and r["installed_length_m"] > 8000))
    _plan_lines(sh, ds, pm_b, (rect_b[0], rect_b[1], rect_b[2], rect_b[3]), lambda r: r["kind"] in ("flowline", "trunkline"))
    # umbilical: functional connection only (route not defined)
    umb = ds["umbilical"]["spec"]
    host = next(n for n in nodes.values() if n["kind"] == "host")
    hx, hy = pm_a.pt(host["x_m"], host["y_m"])
    prev = (hx, hy + 16.0)
    for t in umb["terminations"]:
        n = nodes[t["node"]]
        px, py = pm_a.pt(n["x_m"], n["y_m"])
        D.polyline(sh, [prev, (px - 4.0, py + 16.0)], D.COLORS["umbilical"], 0.5, "dot")
        prev = (px - 4.0, py + 16.0)
    sh.text(pm_a.pt(5200.0, host["y_m"])[0], hy + 24.0, "UMBILICAL (functional connection only: route not defined, length from the specification)", 2.5, "middle", fill=D.COLORS["umbilical"])
    for n in nodes.values():
        if n["kind"] in ("plem", "plet", "host", "injection_source"):
            x, y = pm_a.pt(n["x_m"], n["y_m"])
            D.symbol(sh, "host" if n["kind"] == "host" else n["kind"], x, y, 3.0)
            above = n["kind"] in ("host", "plem")
            dy = {"host": -9.0, "plem": -5.4, "injection_source": 9.0, "plet": 12.6}[n["kind"]]
            sh.text(x + (-5.0 if n["kind"] in ("host", "injection_source") else 4.0), y + dy, "%s (%s)" % (n["tag"], n["id"]), 2.5, "start", bold=True)
            sh.items.append({"type": "asset", "id": n["id"], "kind": n["kind"], "tag": n["tag"]})
    for st in ds["structures"]:
        members = [n["id"] for n in nodes.values() if n.get("structure") == st["id"]]
        x, y = pm_a.pt(st["x_m"], st["y_m"])
        sh.add(K.rect(x - 4.0, y - 3.0, 8.0, 6.0, fill="#f4f4f4", sw=0.5))
        sh.text(x, y - 6.0, st["id"], 2.6, "middle", bold=True)
        sh.raw_box((x - 4.0, y - 3.0, x + 4.0, y + 3.0), "template")
        sh.items.append({"type": "asset", "id": st["id"], "kind": "template", "members": members})
        a = pm_b.pt(st["x_m"] - st["length_m"] / 2.0, st["y_m"] + st["width_m"] / 2.0)
        b = pm_b.pt(st["x_m"] + st["length_m"] / 2.0, st["y_m"] - st["width_m"] / 2.0)
        sh.add(K.rect(a[0], a[1], b[0] - a[0], b[1] - a[1], fill="#f4f4f4", sw=0.5))
        sh.text((a[0] + b[0]) / 2.0, a[1] - 2.2, "%s %s (see DWG-08)" % (st["id"], st["name"]), 2.6, "middle", bold=True)
        sh.text((a[0] + b[0]) / 2.0, b[1] + 4.6, ", ".join(sorted(members)), 2.3, "middle", fill=GREY)
        for n in nodes.values():
            if n.get("structure") == st["id"] and n["kind"] in ("manifold", "injection_manifold"):
                px, py = pm_b.pt(n["x_m"], n["y_m"])
                D.symbol(sh, n["kind"], px, py, 1.4)
            if n.get("structure") == st["id"] and n["kind"] in ("production_well", "injection_well"):
                px, py = pm_b.pt(n["x_m"], n["y_m"])
                sh.add(K.circle(px, py, 0.9, fill=INK, sw=0.2))
    for n in nodes.values():
        if n["kind"] not in ("plem", "plet", "host", "injection_source"):
            sh.items.append({"type": "asset", "id": n["id"], "kind": n["kind"], "tag": n["tag"]})
    _kp_marks(sh, ds, pm_a)
    D.north_arrow(sh, 396.0, 188.0)
    D.scale_bar(sh, 30.0, 112.0, pm_a, 2000.0)
    D.scale_bar(sh, 330.0, 214.0, pm_b, 500.0)
    sh.text(240.0, 112.0, "KP = kilometres of production route from the host inlet (riser then trunk)", 2.4, fill=GREY)
    sh.legend += [("line", D.COLORS["production"], "solid", "production flowline, jumper, riser"), ("line", D.COLORS["injection"], "dash", "injection flowline, jumper, riser"),
                  ("line", D.COLORS["umbilical"], "dot", "umbilical (functional)"), ("marker", "square", False, "template footprint (to scale in the detail)"), ("marker", "diamond", False, "PLEM / PLET / host symbols (exaggerated)")]
    sh.notes += ["Plan view of the reference concept from the canonical dataset; line coordinates are the specification route points (synthetic, not surveyed).",
                 "The umbilical is drawn as a functional connection between the host and the template terminations; its route and cross-section are not defined.",
                 "Symbols are exaggerated for legibility; positions are accurate only for line geometry and template footprints at the stated scales."]


def _levels(edges_dir, nodes):
    lvl = {n: 0 for n in nodes}
    for _ in range(len(nodes)):
        for a, b in edges_dir:
            if lvl[b] < lvl[a] + 1:
                lvl[b] = lvl[a] + 1
    return lvl


def graph_layout(ds, concept, domain, x_rng, y_rng):
    """Left-to-right layered layout of one service graph; returns ``{node: (x, y)}``, the edge rows (pumps included) and the nodes."""
    nodes = {k: n for k, n in D.concept_nodes(ds, concept).items() if n["service"] == domain}
    lines = D.service_edges(ds, concept, domain)
    lvl = _levels([(r["from"], r["to"]) for r in lines], nodes)
    top = max(lvl.values()) or 1
    ys = {}
    order = sorted(nodes, key=lambda n: (lvl[n], nodes[n].get("structure") or "", n))
    by_level = {}
    for n in order:
        by_level.setdefault(lvl[n], []).append(n)
    for lv, ns_ in by_level.items():
        for i, n in enumerate(ns_):
            ys[n] = y_rng[0] + (y_rng[1] - y_rng[0]) * ((i + 0.5) / len(ns_))
    pos = {n: (x_rng[0] + (x_rng[1] - x_rng[0]) * lvl[n] / top, ys[n]) for n in nodes}
    return pos, lines, nodes


def draw_graph(sh, ds, rs, ctx, domain, y_rng, overlay, case=None, x_rng=(30.0, 385.0)):
    """Draw one service graph with tags, sizes, flow arrows and (optionally) the design-case operating overlay."""
    ref = ds["reference_concept"]
    pos, lines, nodes = graph_layout(ds, ref, domain, x_rng, y_rng)
    case = case or _design_case(ds)
    rec_key = "production" if domain == "production" else "injection"
    res = ds["results"][ref][case][rec_key]
    out_count = {}
    for r in lines:
        out_count[r["from"]] = out_count.get(r["from"], 0) + 1
    for r in lines:
        (x1, y1), (x2, y2) = pos[r["from"]], pos[r["to"]]
        color = D.COLORS[domain]
        branch = abs(y1 - y2) > 0.1
        xm = (x1 + 16.0 if out_count[r["from"]] > 1 else x2 - 14.0) if branch else None
        pts = [(x1 + 4.5, y1), (xm, y1), (xm, y2), (x2 - 4.5, y2)] if xm else [(x1 + 4.5, y1), (x2 - 4.5, y2)]
        D.polyline(sh, pts, color, 0.8)
        ex, ey = pts[-2]
        sh.add(K.arrowhead(x2 - 4.5, y2, (x2 - 4.5) - ex, y2 - ey))
        sh.items.append({"type": "line", "id": r["id"], "tag": r["tag"], "service": domain, "length_m": r["installed_length_m"]})
        if r["kind"] == "pump":
            mx = (x1 + x2) / 2.0
            D.symbol(sh, "pump", mx, y1, 2.4)
            sh.text(mx, y1 - 5.2, "%s (%s)" % (r["tag"], r["id"]), 2.5, "middle", bold=True)
            sh.text(mx, y1 + 7.6, "discharge %.0f bara" % r["discharge_pressure_bara"], 2.4, "middle")
            continue
        runs = [(abs(b[0] - a[0]), a, b) for a, b in zip(pts[:-1], pts[1:]) if abs(b[1] - a[1]) < 0.01]
        seg = (runs[-1] if (branch and out_count[r["from"]] > 1) else max(runs, key=lambda t: t[0])) if runs else None
        if seg and seg[0] > 18.0 and (r["installed_length_m"] > 150.0 or not overlay):
            cx = (seg[1][0] + seg[2][0]) / 2.0
            cy = seg[1][1]
            sh.text(cx, cy - 4.4, "%s (%s)" % (r["tag"], r["id"]), 2.5, "middle", bold=True)
            sh.text(cx, cy - 1.6, "ID %.0f mm | L %.2f km" % (r["id_mm"], r["installed_length_m"] / 1000.0), 2.4, "middle")
            if overlay:
                sh.bound_text(rs, cx, cy + 3.0, "ds:results.%s.%s.%s.edges.%s.flow_kg_s" % (ref, case, rec_key, r["id"]), ".1f", prefix="m ", suffix=" kg/s", size=2.4, anchor="middle")
    for n, (x, y) in pos.items():
        k = nodes[n]["kind"]
        D.symbol(sh, k, x, y, 2.6)
        sh.text(x, y - 5.2, "%s" % nodes[n]["tag"], 2.5, "middle", bold=True)
        sh.text(x, y + (6.6 if k != "injection_well" else 8.2), n, 2.3, "middle", fill=GREY)
        if overlay and n in res["nodes"]:
            sh.bound_text(rs, x, y + (9.4 if k != "injection_well" else 11.0), "ds:results.%s.%s.%s.nodes.%s.pressure_bara" % (ref, case, rec_key, n), ".1f", suffix=" bara", size=2.4, anchor="middle")
        sh.items.append({"type": "asset", "id": n, "kind": k, "tag": nodes[n]["tag"]})
    return pos


def network_schematic(sh, ds, rs, ctx):
    """DWG-02: actual modelled connectivity of both services (flow direction, tags, sizes, lengths, battery limits)."""
    ref = ds["reference_concept"]
    sh.text(22.0, 31.0, "PRODUCTION NETWORK (concept %s)" % ref, 3.0, bold=True)
    pos_p = draw_graph(sh, ds, rs, ctx, "production", (50.0, 120.0), False)
    sh.text(22.0, 134.0, "INJECTION NETWORK", 3.0, bold=True)
    pos_i = draw_graph(sh, ds, rs, ctx, "injection", (150.0, 205.0), False)
    host = pos_p["HOST-IN"]
    D.polyline(sh, [(host[0] - 14.0, 36.0), (host[0] - 14.0, 124.0)], "#666", 0.4, "longdash")
    sh.text(host[0] - 16.0, 39.0, "BATTERY LIMIT 2: host inlet (pressure specified)", 2.4, "end", fill="#444")
    srcx = pos_i["INJ-SRC"][0]
    D.polyline(sh, [(srcx + 12.0, 140.0), (srcx + 12.0, 212.0)], "#666", 0.4, "longdash")
    sh.text(srcx + 14.0, 143.0, "BATTERY LIMIT 1: injection water supply (pressure specified)", 2.4, fill="#444")
    # pump symbol on the injection chain
    x = (pos_i["INJ-SRC"][0] + pos_i["INJ-HEAD"][0]) / 2.0
    sh.legend += [("line", D.COLORS["production"], "solid", "production line (arrow = modelled flow direction)"), ("line", D.COLORS["injection"], "solid", "injection line"), ("marker", "diamond", False, "well / manifold / PLEM / PLET / host symbols")]
    sh.notes += ["Connectivity is read from the generated NeqSim topology (FieldNetworkTopology over LoopedPipeNetwork); each tag and size is the canonical dataset value.",
                 "Production chokes, isolation valves and instrumentation are not modelled and are not shown; see the functional sheets for omitted systems.",
                 "The two services are separate hydraulic domains of the same canonical identity registry (different fluids)."]


def process_sheet(sh, ds, rs, ctx, domain):
    """DWG-03/04: functional process-flow sheet of one service with the design-case overlay and the operating table of every case."""
    ref = ds["reference_concept"]
    d_case = _design_case(ds)
    rec_key = "production" if domain == "production" else "injection"
    sh.text(22.0, 31.0, "%s - design case %s (%s)" % (domain.upper(), d_case, next(c["label"] for c in ds["cases"] if c["id"] == d_case)), 3.0, bold=True)
    draw_graph(sh, ds, rs, ctx, domain, (46.0, 116.0), True, d_case)
    nodes = [n for n in D.concept_nodes(ds, ref).values() if n["service"] == domain and n["kind"] in (("manifold", "plem", "host", "production_well") if domain == "production" else ("injection_manifold", "plet", "injection_well", "injection_source"))]
    header = ["Node", "Tag"]
    for c in ds["cases"]:
        header += ["%s p" % c["id"], "%s T" % c["id"]]
    rows = []
    for n in nodes:
        row = [n["id"], n["tag"]]
        for c in ds["cases"]:
            rec = ds["results"][ref][c["id"]][rec_key]
            node = rec["nodes"].get(n["id"], {})
            p = node.get("pressure_bara")
            t = node.get("temperature_c")
            row += ["%.1f" % p if p is not None else "n/a", "%.1f" % t if t is not None else "n/a"]
        rows.append(row)
    widths = [24.0, 22.0] + [22.0, 22.0] * len(ds["cases"])
    D.table(sh, 22.0, 140.0, widths, header, rows[:9], size=2.3, row_h=4.0, title="Operating conditions at the modelled nodes: p in bara, T in degC (solved values, all cases)")
    omitted = ["Production/injection chokes and tree valves (master, wing, swab): not modelled", "Isolation valves, ESD and depressurisation valves: not modelled",
               "Pressure, temperature and flow instrumentation and the control system: not modelled", "Chemical injection points and the umbilical service tubes: see DWG-10",
               "Safety functions, SIL, valve closure times and cause-and-effect: not defined"]
    sh.add(K.rect(272.0, 140.0, 134.0, 40.0, fill="#fff", sw=0.4))
    sh.text(274.0, 144.4, "OMITTED SYSTEMS (functional schematic, not an issued P&ID)", 2.7, bold=True)
    for i, o in enumerate(omitted):
        D.fit(sh, 274.0, 149.6 + i * 5.4, "- " + o, 129.0, 2.4)
    sh.raw_box((272.0, 140.0, 406.0, 180.0), "box")
    sh.legend += [("line", D.COLORS[domain], "solid", "modelled line with flow direction"), ("marker", "circle", False, "modelled node symbols; values are the %s case" % d_case)]
    sh.notes += ["Functional process-flow schematic: only items present in the generated model are drawn; omitted systems are listed on the sheet.",
                 "This is not an issued-for-construction P&ID: instrumentation, valves and safety design are absent from the study scope."]


def production_process(sh, ds, rs, ctx):
    process_sheet(sh, ds, rs, ctx, "production")


def injection_process(sh, ds, rs, ctx):
    process_sheet(sh, ds, rs, ctx, "injection")


def _paths(ds, concept, domain, start_kinds, end_kind):
    """All simple directed paths (list of edge ids) from nodes of ``start_kinds`` to the first node of ``end_kind``."""
    lines = D.service_edges(ds, concept, domain)
    nodes = D.concept_nodes(ds, concept)
    out_edges = {}
    for r in lines:
        out_edges.setdefault(r["from"], []).append((r["id"], r["to"], r["installed_length_m"]))
    paths = []

    def dfs(node, acc, total):
        if nodes[node]["kind"] == end_kind:
            paths.append((total, list(acc)))
            return
        for eid, nxt, ln in out_edges.get(node, []):
            acc.append(eid)
            dfs(nxt, acc, total + ln)
            acc.pop()

    for n in nodes.values():
        if n["kind"] in start_kinds and n["service"] == domain:
            dfs(n["id"], [], 0.0)
    return sorted(paths, key=lambda t: -t[0])


def profile_sheet(sh, ds, rs, ctx, domain):
    """DWG-05/06: longitudinal profile of the longest path of one service with aligned pressure, temperature and (production) velocity panels."""
    ref = ds["reference_concept"]
    geom, lines = ds["geometry"][ref], D.concept_edges(ds, ref)
    if domain == "production":
        paths = _paths(ds, ref, "production", ("production_well",), "host")
    else:
        paths = _paths(ds, ref, "injection", ("injection_source",), "injection_well")
    total, path = paths[0]
    rec_key = "production" if domain == "production" else "injection"
    chain, offs = [], {}
    cur = 0.0
    for eid in path:
        offs[eid] = cur
        cur += lines[eid]["installed_length_m"] if eid in lines else 0.0
    path_len = cur
    sh.scale_text = "horizontal: chainage 1:%s; vertical exaggerated (see axes)" % format(int(round(path_len * 1000.0 / 330.0, -3)), ",d").replace(",", " ")
    sh.tb["scale"] = sh.scale_text
    sh.text(22.0, 31.0, "Path %s (longest %s path; chainage measured in the flow direction from the first node of the path)" % (" > ".join(path), domain), 2.8, bold=True)
    x0, w = 42.0, 352.0
    xr = (0.0, path_len / 1000.0)
    # elevation panel
    z_x, z_y = [], []
    for eid in path:
        if eid not in geom or eid not in lines:
            continue
        g = geom[eid]
        sc = lines[eid]["installed_length_m"] / g["chainage_m"][-1]
        for c, p in zip(g["chainage_m"], g["points"]):
            z_x.append((offs[eid] + c * sc) / 1000.0)
            z_y.append(p[2])
    zr = (math.floor(min(z_y) / 50.0) * 50.0 - 20.0, 60.0)
    ax0 = fa_draw.Axes(sh, x0, 40.0, w, 40.0, xr, zr, "P-ELEV", "Pipe centreline elevation (synthetic seabed; surface-laid lines on the seabed)", ylabel="z (m MSL)", show_xticks=False, y_step_target=3)
    ax0.series(z_x, z_y, INK, "solid", "centreline / seabed", meta={"quantity": "elevation_m"})
    ax0.hline(0.0, "sea surface", color="#1f78b4", pattern="dash", label_x=x0 + w - 2.0)
    nodes = D.concept_nodes(ds, ref)
    labelled_x = []
    for eid in path:
        r = lines.get(eid)
        n_end = nodes.get(r["to"]) if r else None
        if n_end:
            xk = (offs[eid] + r["installed_length_m"]) / 1000.0
            if xr[0] <= xk <= xr[1] and n_end["kind"] in ("manifold", "plem", "plet", "host", "injection_manifold", "injection_well", "production_well"):
                last = xk > xr[1] - 0.05 * (xr[1] - xr[0])
                crowded = any(abs(xk - lx) < 0.06 * (xr[1] - xr[0]) for lx in labelled_x)
                if not crowded:
                    labelled_x.append(xk)
                ax0.marker(xk, max(min(n_end["z_m"], zr[1]), zr[0]), "square", True, 1.0, label=None if crowded else n_end["id"], dx=-1.6 if last else 1.6, dy=4.2 if last else -2.0,
                           anchor="end" if last else "start")
    # pressure, temperature and velocity panels
    series_p, series_t, series_v = {}, {}, {}
    for c in ds["cases"]:
        rec = ds["results"][ref][c["id"]][rec_key]
        xs, ps, ts, vs = [], [], [], []
        for eid in path:
            if eid not in rec["edges"]:
                continue
            e = rec["edges"][eid]
            prof = e.get("profile")
            if not prof:
                continue
            for pos, p, t in zip(prof["position_m"], prof["pressure_bara"], prof["temperature_c"]):
                xs.append((offs[eid] + pos) / 1000.0)
                ps.append(p)
                ts.append(t)
            if domain == "production" and prof["kind"] == "distributed":
                rho = (e.get("outlet_fluid") or {}).get("density_kg_m3")
                ve = ds["solver"]["erosional_c_factor_si"] / math.sqrt(rho)
                for a, b in zip(prof["vsg_m_s"], prof["vsl_m_s"]):
                    vs.append(((a or 0.0) + (b or 0.0)) / ve)
            elif domain == "production":
                vs.extend([None] * len(prof["position_m"]))
        series_p[c["id"]], series_t[c["id"]], series_v[c["id"]] = (xs, ps), (xs, ts), (xs, vs)
    pmax = max(max(v for v in s[1] if v is not None) for s in series_p.values())
    pmin = min(min(v for v in s[1] if v is not None) for s in series_p.values())
    ax1 = fa_draw.Axes(sh, x0, 92.0, w, 38.0, xr, (math.floor(pmin / 10.0) * 10.0, math.ceil(pmax / 10.0) * 10.0), "P-PRES", "Pressure by case", ylabel="p (bara)", show_xticks=False, y_step_target=4)
    for c in ds["cases"]:
        col, pat = ctx["styles"][c["id"]]
        ax1.series(series_p[c["id"]][0], series_p[c["id"]][1], col, pat, c["id"], meta={"quantity": "pressure_bara", "case": c["id"]})
    tmax = max(max(v for v in s[1] if v is not None) for s in series_t.values())
    tmin = min(min(v for v in s[1] if v is not None) for s in series_t.values())
    curve = ds["hydrate_curves"].get(next(c["production_fluid"] for c in ds["cases"] if c["id"] == _design_case(ds))) if domain == "production" else None
    th_vals = []
    if curve:
        d_case = _design_case(ds)
        xs, ps = series_p[d_case]
        th_vals = [surf_design.t_hyd(curve, p) if p is not None else None for p in ps]
        lo = min([tmin] + [t for t in th_vals if t is not None])
    else:
        lo = tmin
    ax2 = fa_draw.Axes(sh, x0, 142.0, w, 38.0, xr, (math.floor((lo - 2) / 10.0) * 10.0, math.ceil(tmax / 10.0) * 10.0 + 10.0), "P-TEMP", "Temperature by case%s" % (" and hydrate equilibrium temperature (design case)" if curve else ""),
                       ylabel="T (degC)", show_xticks=domain != "production", xlabel=None if domain == "production" else "chainage (km)", y_step_target=4)
    for c in ds["cases"]:
        col, pat = ctx["styles"][c["id"]]
        ax2.series(series_t[c["id"]][0], series_t[c["id"]][1], col, pat, c["id"], meta={"quantity": "temperature_c", "case": c["id"]})
    if curve:
        ax2.series(series_p[_design_case(ds)][0], th_vals, RED, "dashdot", "T hydrate", meta={"quantity": "hydrate_temperature_c"})
    if domain == "production":
        vmax = max([v for s in series_v.values() for v in s[1] if v is not None] + [0.2])
        ax3 = fa_draw.Axes(sh, x0, 192.0, w, 26.0, xr, (0.0, max(1.2, math.ceil(vmax * 10) / 10.0 + 0.1)), "P-EROS", "Mixture velocity / erosional velocity (limit 1.0)", xlabel="chainage (km)", ylabel="ratio",
                           y_step_target=3)
        for c in ds["cases"]:
            col, pat = ctx["styles"][c["id"]]
            ax3.series(series_v[c["id"]][0], series_v[c["id"]][1], col, pat, c["id"], meta={"quantity": "erosional_ratio", "case": c["id"]})
        ax3.hline(1.0, "limit", color=RED, pattern="dash")
    else:
        sh.text(x0 + w / 2.0, 206.0, "Single-phase liquid injection: profiles show the end values of each edge (the network gives the distributed profile only for multiphase edges).", 2.6, "middle", fill=GREY)
    sh.legend += [("line", ctx["styles"][c["id"]][0], ctx["styles"][c["id"]][1], "%s: %s" % (c["id"], c["label"])) for c in ds["cases"]]
    if curve:
        sh.legend.append(("line", RED, "dashdot", "hydrate equilibrium temperature (CPA, uninhibited)"))
    sh.notes += ["Profiles are extracted once from the solved cases of the reference concept; the same chainage runs through all panels.",
                 "Hydraulic and thermal profiles are steady-state screening results (Beggs and Brill multiphase); the seabed profile is synthetic, not surveyed.",
                 "Horizontal scale is the chainage axis; the vertical scale of each panel is stated by its axis and is exaggerated."]
    for eid in path:
        sh.items.append({"type": "line", "id": eid, "service": domain})


def production_profile(sh, ds, rs, ctx):
    profile_sheet(sh, ds, rs, ctx, "production")


def injection_profile(sh, ds, rs, ctx):
    profile_sheet(sh, ds, rs, ctx, "injection")
