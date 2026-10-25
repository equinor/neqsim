"""Geometry and topology sheets of the flow-assurance drawing package (network schematic, route plan, arrangement, cross-sections)."""
import math

import fa_draw
from fa_draw import INK, GREY, RED
from psr_diagrams import kit as K

HEX = "#444"


def _groups(route):
    """Edge groups of the hydraulic network between major assets (waypoints are collapsed) plus the non-hydraulic jumpers."""
    nodes = route["nodes"]
    out_h = {s["from"]: s for s in route["segments"] if s.get("hydraulic", True)}
    in_h = {s["to"] for s in route["segments"] if s.get("hydraulic", True)}
    groups = []
    starts = [n for n, a in nodes.items() if a["kind"] != "waypoint" and n in out_h]
    for st in starts:
        chain, node = [], st
        while node in out_h:
            seg = out_h[node]
            chain.append(seg["id"])
            node = seg["to"]
            if nodes[node]["kind"] != "waypoint":
                break
        groups.append({"from": st, "to": node, "segments": chain, "hydraulic": True})
    for s in route["segments"]:
        if not s.get("hydraulic", True):
            groups.append({"from": s["from"], "to": s["to"], "segments": [s["id"]], "hydraulic": False})
    return groups, in_h


def _depths(nodes_major, groups):
    depth = {n: 0 for n in nodes_major}
    for _ in range(len(nodes_major) + 1):
        for g in groups:
            if depth[g["to"]] < depth[g["from"]] + 1:
                depth[g["to"]] = depth[g["from"]] + 1
    return depth


def _hex(cx, cy, w, h):
    return K.polygon([(cx - w / 2, cy), (cx - w / 4, cy - h / 2), (cx + w / 4, cy - h / 2), (cx + w / 2, cy), (cx + w / 4, cy + h / 2), (cx - w / 4, cy + h / 2)], fill="#fff")


def _id_text(segs, ids):
    vals = sorted({round(segs[i]["id_m"] * 1000.0, 1) for i in ids if segs[i].get("id_m")})
    return "ID %s mm" % "/".join("%.1f" % v for v in vals) if vals else "ID n/a"


def network_schematic(sh, ds, rs, ctx):
    """Sheet A: connectivity schematic - assets, tags, flow direction, battery limits, modelled isolation/injection points, line sizes and lengths."""
    route = ds["route"]
    nodes = route["nodes"]
    segs = {s["id"]: s for s in route["segments"]}
    groups, _ = _groups(route)
    major = [n for n, a in nodes.items() if a["kind"] != "waypoint"]
    depth = _depths(major, groups)
    dmax = max(depth.values())
    x0, x1 = 34.0, 378.0
    xs = {n: x0 + depth[n] * (x1 - x0) / dmax for n in major}
    # vertical positions: hydraulic sources ordered north to south (coordinates) or in file order
    sources = [n for n in route["sources"]]
    sources.sort(key=lambda n: -(nodes[n].get("y_m") if nodes[n].get("y_m") is not None else 0.0))
    ys, top, bot = {}, 80.0, 175.0
    for i, n in enumerate(sources):
        ys[n] = top + (bot - top) * (i / max(len(sources) - 1, 1)) if len(sources) > 1 else 128.0
    for g in sorted(groups, key=lambda g: depth[g["to"]]):
        if not g["hydraulic"]:
            continue
        preds = [h["from"] for h in groups if h["hydraulic"] and h["to"] == g["to"]]
        if g["to"] not in ys:
            ys[g["to"]] = sum(ys[p] for p in preds if p in ys) / max(len([p for p in preds if p in ys]), 1)
    wells_by = {}
    for g in groups:
        if not g["hydraulic"]:
            wells_by.setdefault(g["to"], []).append(g["from"])
    for tmpl, wl in wells_by.items():
        for k, w in enumerate(sorted(wl)):
            ys[w] = ys[tmpl] + (k - (len(wl) - 1) / 2.0) * 16.0
    half = {"well": 3.2, "template": 11.0, "plem": 8.0, "riser_base": 5.0, "host_interface": 14.0}
    # battery limits
    bl1_x = xs[sources[0]] + 14.0
    host = route["sink"]
    bl2_x = xs[host] - 22.0
    for bx, label, ref in ((bl1_x, "BATTERY LIMIT 1: source boundary at the template outlets (rate and temperature specified)", "bl1"),
                           (bl2_x, "BATTERY LIMIT 2: host interface (pressure specified)", "bl2")):
        sh.add(fa_draw.patterned([(bx, 40.0), (bx, 214.0)], fa_draw.PATTERNS["longdash"], "#666", 0.45))
    sh.text(bl1_x + 2.0, 43.5, "BATTERY LIMIT 1: source boundary at the template outlets", 2.6, "start", fill="#444", layer="label")
    sh.text(bl1_x + 2.0, 47.0, "(rate and temperature specified)", 2.6, "start", fill="#444", layer="label")
    sh.text(bl2_x - 2.0, 43.5, "BATTERY LIMIT 2: host interface", 2.6, "end", fill="#444", layer="label")
    sh.text(bl2_x - 2.0, 47.0, "(pressure specified)", 2.6, "end", fill="#444", layer="label")
    # edges
    edge_info = []
    for g in groups:
        a, b = g["from"], g["to"]
        xa, ya = xs[a] + half.get(nodes[a]["kind"], 6.0), ys[a]
        xb, yb = xs[b] - half.get(nodes[b]["kind"], 6.0), ys[b]
        xm = (xb - 16.0) if g["hydraulic"] and abs(ya - yb) > 0.1 else xa + (xb - xa) * 0.5
        pts = [(xa, ya), (xm, ya), (xm, yb), (xb, yb)] if abs(ya - yb) > 0.1 else [(xa, ya), (xb, yb)]
        if g["hydraulic"]:
            sh.add(K.polyline(pts, sw=0.9))
        else:
            sh.add(fa_draw.patterned(pts, fa_draw.PATTERNS["dash"], GREY, 0.5))
        ex, ey = pts[-2]
        sh.add(K.arrowhead(xb, yb, xb - ex if xb != ex else 1.0, yb - ey))
        L = sum(segs[i]["length_m"] for i in g["segments"])
        edge = {"type": "edge", "from": a, "to": b, "segments": g["segments"], "hydraulic": g["hydraulic"], "length_m": L, "points": [[round(x, 2), round(y, 2)] for x, y in pts]}
        sh.items.append(edge)
        edge_info.append((g, pts, L))
        if g["hydraulic"]:
            run = max(((abs(p2[0] - p1[0]), p1, p2) for p1, p2 in zip(pts[:-1], pts[1:]) if abs(p2[1] - p1[1]) < 0.01), key=lambda t: t[0])
            lo = run[1][0] + (30.0 if nodes[a]["kind"] in ("template", "well") else 0.0)
            cx = (lo + run[2][0]) / 2.0
            cy = run[1][1]
            tag = g["segments"][0] if len(g["segments"]) == 1 else "%s to %s" % (g["segments"][0], g["segments"][-1])
            sh.text(cx, cy - 8.4, tag, 2.8, "middle", bold=True)
            sh.text(cx, cy - 5.2, "%s | L %s km" % (_id_text(segs, g["segments"]), "%.2f" % (L / 1000.0)), 2.6, "middle")
            sheets_ref = "profile sheet%s %s" % ("s" if len([p for p in route["paths"].values() if g["segments"][0] in p]) > 1 else "",
                                                 ", ".join(str(ctx["codes"].get("DWG-0%d" % k, "")) for k in (3, 4) if "DWG-0%d" % k in ctx["codes"]
                                                           and any(g["segments"][0] in p for p in [list(route["paths"].values())[k - 3]])))
            sh.text(cx, cy - 2.2, "see %s; plan sheet %d" % (sheets_ref, ctx["codes"]["DWG-02"]), 2.5, "middle", fill=GREY)
    # symbols
    for n in major:
        a, x, y = nodes[n], xs[n], ys[n]
        k = a["kind"]
        if k == "well":
            sh.add(K.circle(x, y, 3.2))
            sh.add(K.line(x - 2.2, y - 2.2, x + 2.2, y + 2.2, sw=0.5) + K.line(x - 2.2, y + 2.2, x + 2.2, y - 2.2, sw=0.5))
            sh.text(x, y - 5.0, n, 2.6, "middle")
            sh.raw_box((x - 3.5, y - 3.5, x + 3.5, y + 3.5), "symbol")
        elif k == "template":
            sh.add(K.rect(x - 11, y - 6, 22, 12, fill="#fff", sw=0.7))
            sh.text(x, y + 1.0, n, 3.0, "middle", bold=True)
            sh.text(x, y + 10.0, a.get("name", ""), 2.5, "middle", fill=GREY)
            sh.raw_box((x - 11, y - 6, x + 11, y + 6), "symbol")
        elif k == "plem":
            sh.add(_hex(x, y, 16, 10))
            sh.text(x, y + 1.0, n, 2.6, "middle", bold=True)
            sh.text(x, y + 9.5, "gathering junction", 2.5, "middle", fill=GREY)
            sh.raw_box((x - 8, y - 5, x + 8, y + 5), "symbol")
        elif k == "riser_base":
            sh.add(K.polygon([(x, y - 5), (x - 5, y + 4), (x + 5, y + 4)], fill="#fff", sw=0.7))
            sh.text(x, y + 9.0, n, 2.6, "middle", bold=True)
            sh.raw_box((x - 5, y - 5, x + 5, y + 4), "symbol")
        elif k == "host_interface":
            sh.add(K.rect(x - 14, y - 9, 28, 18, fill="#fff", sw=0.9))
            sh.add(K.rect(x - 12.5, y - 7.5, 25, 15, fill="none", sw=0.35))
            sh.text(x, y - 1.0, "HOST", 3.0, "middle", bold=True)
            sh.text(x, y + 3.2, n, 2.5, "middle")
            sh.raw_box((x - 14, y - 9, x + 14, y + 9), "symbol")
        sh.items.append({"type": "asset", "id": n, "kind": k, "x": round(x, 2), "y": round(y, 2)})
    # features: injection points, isolation valves, pig interfaces, crossing
    feats = route.get("features") or []
    for f in feats:
        at = f.get("at_node")
        if f["kind"] == "injection_point" and at in xs:
            x, y = xs[at] - 4.0, ys[at] - 15.5
            sh.add(K.polygon([(x - 2.2, y - 2.0), (x + 2.2, y - 2.0), (x, y + 2.2)], fill=INK))
            sh.add(K.line(x, y + 2.2, x, y + 8.5, sw=0.5))
            sh.text(x - 3.0, y - 0.2, f["id"], 2.6, "end")
            sh.raw_box((x - 2.2, y - 2.0, x + 2.2, y + 8.5), "symbol")
        elif f["kind"] == "isolation_valve" and at in xs:
            x, y = xs[at] + 17.0, ys[at]
            sh.add(K.polygon([(x - 2.2, y - 1.8), (x - 2.2, y + 1.8), (x, y)], fill=INK) + K.polygon([(x + 2.2, y - 1.8), (x + 2.2, y + 1.8), (x, y)], fill=INK))
            sh.text(x, y + 5.0, f["id"], 2.6, "middle")
            sh.raw_box((x - 2.2, y - 1.8, x + 2.2, y + 1.8), "symbol")
        elif f["kind"] in ("pig_launcher", "pig_receiver") and at in xs:
            x, y = xs[at], ys[at] - 15.0 if f["kind"] == "pig_launcher" else ys[at] + 15.0
            sh.add(K.rect(x - 6, y - 3, 12, 6, fill="#fff", sw=0.4))
            sh.add(fa_draw.patterned([(x, y + (3 if f["kind"] == "pig_launcher" else -3)), (x, ys[at] + (-5 if f["kind"] == "pig_launcher" else 9))], fa_draw.PATTERNS["dot"], GREY, 0.4))
            sh.text(x, y + 1.0, f["id"], 2.5, "middle")
            sh.text(x, y + (-4.5 if f["kind"] == "pig_launcher" else 7.0), "interface only", 2.5, "middle", fill=GREY)
            sh.raw_box((x - 6, y - 3, x + 6, y + 3), "symbol")
        elif f["kind"] == "crossing" and f.get("segment"):
            for g, pts, L in edge_info:
                if g["hydraulic"] and f["segment"] in g["segments"]:
                    before = 0.0
                    for sid in g["segments"]:
                        if sid == f["segment"]:
                            break
                        before += segs[sid]["length_m"]
                    frac = (before + float(f["along_m"])) / L
                    run = max(((abs(p2[0] - p1[0]), p1, p2) for p1, p2 in zip(pts[:-1], pts[1:]) if abs(p2[1] - p1[1]) < 0.01), key=lambda t: t[0])
                    cx, cy = run[1][0] + (run[2][0] - run[1][0]) * frac, run[1][1]
                    sh.add(K.line(cx - 2, cy - 2, cx + 2, cy + 2, sw=0.7) + K.line(cx - 2, cy + 2, cx + 2, cy - 2, sw=0.7))
                    sh.text(cx, cy + 7.0, "%s (unverified)" % f["id"], 2.6, "middle")
                    sh.raw_box((cx - 2, cy - 2, cx + 2, cy + 2), "symbol")
    # flow-direction note and legend
    variants = ", ".join(sorted(ds["topology_variants"]))
    sh.notes = ["Connectivity schematic only: positions and lengths on the sheet do not represent geography or scale (plan: sheet %d)." % ctx["codes"]["DWG-02"],
                "Line tags are the stable segment IDs of the route file; waypoints between assets are collapsed into one run (all segment IDs are listed on the run).",
                "Solid lines are modelled hydraulic segments; dashed grey jumpers are not modelled (stream aggregated at the template).",
                "Topology variants in the case matrix: %s. Isolation by XV-A/XV-B keeps all asset and segment IDs." % variants,
                "Battery limits are modelling boundaries, not facility or licence boundaries."]
    sh.legend = [("line", INK, "solid", "Modelled hydraulic run (flow direction by arrow)"), ("line", GREY, "dash", "Non-modelled jumper (aggregated)"),
                 ("line", "#666", "longdash", "Battery limit (model boundary)"), ("marker", "square", False, "Template / host (box symbols)"),
                 ("marker", "triangle", True, "MEG injection point (modelled)"), ("marker", "diamond", True, "Isolation valve (line-up variants)"),
                 ("marker", "cross", False, "Crossing (verification status stated)"), ("text", "Pig launcher/receiver: interface only, no simulation")]
    sh.scale_text = "NOT TO SCALE"
    sh.tb["scale"] = "NOT TO SCALE"
    sh.items.append({"type": "battery_limit", "id": "BL1", "x": round(bl1_x, 2)})
    sh.items.append({"type": "battery_limit", "id": "BL2", "x": round(bl2_x, 2)})


def route_plan(sh, ds, rs, ctx):
    """Sheet B: route plan from the supplied coordinates (labelled synthetic/not georeferenced as stated) or a chainage strip when none exist."""
    route = ds["route"]
    nodes, segs = route["nodes"], {s["id"]: s for s in route["segments"]}
    hyd = [s for s in route["segments"] if s.get("hydraulic", True)]
    have = all(nodes[s["from"]].get("x_m") is not None and nodes[s["from"]].get("y_m") is not None and nodes[s["to"]].get("x_m") is not None
               and nodes[s["to"]].get("y_m") is not None for s in hyd)
    geo = bool(route.get("georeferenced"))
    px0, py0, pw, ph = 36.0, 36.0, 330.0, 172.0
    if not have:
        _strip_plan(sh, ds, ctx, px0, py0, pw, ph)
        return
    allx = [nodes[n]["x_m"] for n in nodes if nodes[n].get("x_m") is not None]
    ally = [nodes[n]["y_m"] for n in nodes if nodes[n].get("y_m") is not None]
    xlo, xhi, ylo, yhi = min(allx), max(allx), min(ally), max(ally)
    # a route along one axis has no extent in the other: give it a minimum span so the plan has a valid scale
    min_span = 0.12 * max(xhi - xlo, yhi - ylo, 1.0)
    if xhi - xlo < min_span:
        xlo, xhi = 0.5 * (xlo + xhi) - 0.5 * min_span, 0.5 * (xlo + xhi) + 0.5 * min_span
    if yhi - ylo < min_span:
        ylo, yhi = 0.5 * (ylo + yhi) - 0.5 * min_span, 0.5 * (ylo + yhi) + 0.5 * min_span
    padx, pady = 0.06 * (xhi - xlo), 0.12 * (yhi - ylo)
    xlo, xhi, ylo, yhi = xlo - padx, xhi + padx, ylo - pady, yhi + pady
    s = min(pw / (xhi - xlo), ph / (yhi - ylo))  # mm per m
    ox = px0 + (pw - (xhi - xlo) * s) / 2.0
    oy = py0 + (ph - (yhi - ylo) * s) / 2.0
    X = lambda v: ox + (v - xlo) * s
    Y = lambda v: oy + (yhi - v) * s
    sh.add(K.rect(ox, oy, (xhi - xlo) * s, (yhi - ylo) * s, fill="#fff", sw=0.35))
    xt, xstep = fa_draw.nice_ticks(xlo, xhi, 6)
    yt, ystep = fa_draw.nice_ticks(ylo, yhi, 4)
    for v in xt:
        sh.add(K.line(X(v), oy, X(v), oy + (yhi - ylo) * s, stroke="#d8d8d8", sw=0.15))
        sh.text(X(v), oy - 1.8, "%d" % v, 2.5, "middle", layer="tick")
    for v in yt:
        sh.add(K.line(ox, Y(v), ox + (xhi - xlo) * s, Y(v), stroke="#d8d8d8", sw=0.15))
        sh.text(ox - 1.8, Y(v) + 0.9, "%d" % v, 2.5, "end", layer="tick")
    sh.text(ox + (xhi - xlo) * s / 2.0, oy - 6.2, "x (m) - %s" % ("grid" if not geo else route.get("crs") or "CRS"), 2.6, "middle", layer="label")
    sh.text(ox - 16.0, oy + (yhi - ylo) * s / 2.0, "y (m)", 2.6, "middle", rotate=-90, layer="label")
    nm_per_mm = 1.0 / s
    # polylines and chainage ticks
    for sg in route["segments"]:
        a, b = nodes[sg["from"]], nodes[sg["to"]]
        pts = [(X(a["x_m"]), Y(a["y_m"]))] + [(X(p[0]), Y(p[1])) for p in sg.get("plan_points") or []] + [(X(b["x_m"]), Y(b["y_m"]))]
        if sg.get("hydraulic", True):
            sh.add(K.polyline(pts, sw=0.9))
        else:
            sh.add(fa_draw.patterned(pts, fa_draw.PATTERNS["dash"], GREY, 0.4))
        sh.items.append({"type": "segment_plan", "id": sg["id"], "hydraulic": sg.get("hydraulic", True), "n_points": len(pts), "length_m": sg["length_m"],
                         "plan_length_m": sg.get("horizontal_length_m")})
    # KP marks every 5 km of chainage along each branch path and along the trunk (from the junction)
    def point_at(path_ids, chainage):
        acc = 0.0
        for sid in path_ids:
            sg = segs[sid]
            if chainage <= acc + sg["length_m"] + 1e-6:
                f = (chainage - acc) / sg["length_m"]
                a, b = nodes[sg["from"]], nodes[sg["to"]]
                pts = [(a["x_m"], a["y_m"])] + [tuple(p) for p in sg.get("plan_points") or []] + [(b["x_m"], b["y_m"])]
                tot = sum(math.hypot(p2[0] - p1[0], p2[1] - p1[1]) for p1, p2 in zip(pts[:-1], pts[1:]))
                target, run = f * tot, 0.0
                for p1, p2 in zip(pts[:-1], pts[1:]):
                    d = math.hypot(p2[0] - p1[0], p2[1] - p1[1])
                    if run + d >= target - 1e-9:
                        t = (target - run) / d if d else 0.0
                        return (p1[0] + t * (p2[0] - p1[0]), p1[1] + t * (p2[1] - p1[1]), p2[0] - p1[0], p2[1] - p1[1])
                    run += d
            acc += sg["length_m"]
        return None
    runs = []
    shared = set.intersection(*[set(c) for c in route["paths"].values()]) if len(route["paths"]) > 1 else set()
    if len(route["paths"]) == 1:
        runs.append(("KP", list(route["paths"].values())[0]))
    else:
        for pid, chain in route["paths"].items():
            pre = [c for c in chain if c not in shared]
            if pre:
                runs.append(("KP-%s" % pid.split("->")[0].replace("TMPL-", ""), pre))
        trunk = [c for c in list(route["paths"].values())[0] if c in shared] or list(route["paths"].values())[0]
        runs.append(("KPT", trunk))
    for label, ids in runs:
        total = sum(segs[i]["length_m"] for i in ids)
        k = 0.0
        while k <= total + 1e-6:
            p = point_at(ids, k)
            if p is not None and 0 < k < total:
                x, y, dx, dy = p
                n = math.hypot(dx, dy) or 1.0
                nx, ny = -dy / n, dx / n
                sh.add(K.line(X(x) - nx * 1.6, Y(y) + ny * 1.6, X(x) + nx * 1.6, Y(y) - ny * 1.6, sw=0.5))
                sh.text(X(x) + nx * 3.0 - (0 if nx >= 0 else 0), Y(y) - ny * 3.0 + 0.9, "%s %.0f" % (label, k / 1000.0), 2.5, "start" if nx >= 0 else "end")
            k += 5000.0
    # asset markers
    for n, a in nodes.items():
        if a.get("x_m") is None or a["kind"] == "waypoint" or a["kind"] == "well":
            continue
        x, y = X(a["x_m"]), Y(a["y_m"])
        if a["kind"] == "template":
            sh.add(K.rect(x - 2.5, y - 2.5, 5, 5, fill="#fff", sw=0.7))
            sh.text(x, y + 8.0, n, 2.8, "middle", bold=True)
        elif a["kind"] == "plem":
            sh.add(K.polygon([(x, y - 3), (x - 3, y + 2.5), (x + 3, y + 2.5)], fill="#fff", sw=0.7))
            sh.text(x + 4.5, y - 4.0, n, 2.8, "start", bold=True)
        elif a["kind"] == "riser_base":
            sh.add(K.circle(x, y, 1.6, fill=INK))
            sh.text(x - 2.0, y + 6.5, n, 2.8, "end", bold=True)
        elif a["kind"] == "host_interface":
            sh.add(K.rect(x - 3.2, y - 3.2, 6.4, 6.4, fill="#fff", sw=0.9))
            sh.add(K.line(x - 3.2, y - 3.2, x + 3.2, y + 3.2, sw=0.5) + K.line(x - 3.2, y + 3.2, x + 3.2, y - 3.2, sw=0.5))
            sh.text(x - 4.5, y - 4.5, n, 2.8, "end", bold=True)
        sh.items.append({"type": "asset_plan", "id": n, "x_m": a["x_m"], "y_m": a["y_m"]})
    for f in route.get("features") or []:
        if f["kind"] == "crossing" and f.get("segment"):
            chain = [c for c in route["paths"][list(route["paths"])[0]]]
            ids = [i for i in chain if i in segs]
            before = 0.0
            for sid in ids:
                if sid == f["segment"]:
                    break
                before += segs[sid]["length_m"]
            tr = [i for i in ids if i in shared] or ids
            acc = sum(segs[i]["length_m"] for i in ids[:ids.index(tr[0])])
            p = point_at(tr, before - acc + float(f["along_m"]))
            if p:
                x, y = X(p[0]), Y(p[1])
                sh.add(K.line(x - 2, y - 2, x + 2, y + 2, sw=0.7) + K.line(x - 2, y + 2, x + 2, y - 2, sw=0.7))
                sh.text(x, y + 7.5, "%s (unverified)" % f["id"], 2.6, "middle")
    # scale bar and north arrow
    bar_m = 5000.0
    bx, by = ox + 4.0, oy + (yhi - ylo) * s - 7.0
    sh.add(K.rect(bx, by, bar_m * s, 1.6, fill=INK, sw=0.3) + K.rect(bx + bar_m * s / 2, by, bar_m * s / 2, 1.6, fill="#fff", sw=0.3))
    sh.text(bx, by - 1.2, "0", 2.5, "middle")
    sh.text(bx + bar_m * s, by - 1.2, "%.0f km" % (bar_m / 1000.0), 2.5, "middle")
    nx_, ny_ = px0 + pw + 14.0, oy + 14.0
    sh.add(K.arrowhead(nx_, ny_ - 9.0, 0, -1, length=4.0, width=3.0) + K.line(nx_, ny_ - 9.0, nx_, ny_ + 2.0, sw=0.6))
    sh.text(nx_, ny_ + 6.0, "N (grid north)", 2.6, "middle")
    sh.add(K.rect(ox + 1.0, oy + (yhi - ylo) * s - 1.0, 0.01, 0.01, fill="none", stroke=None))
    banner_txt = "SYNTHETIC LOCAL GRID - NOT GEOREFERENCED" if (route.get("status") == "synthetic" and not geo) else (
        "SCHEMATIC - NOT GEOREFERENCED" if not geo else "")
    if banner_txt:
        sh.text(ox + (xhi - xlo) * s / 2.0, oy + (yhi - ylo) * s + 6.0, banner_txt, 3.6, "middle", bold=True, fill=RED, layer="banner")
    denom = int(round(nm_per_mm * 1000.0 / 100.0) * 100)
    scale_txt = "1:%d (horizontal, A3)" % denom
    sh.scale_text = scale_txt
    sh.tb["scale"] = scale_txt
    sh.items.append({"type": "plan_frame", "x_range_m": [xlo, xhi], "y_range_m": [ylo, yhi], "mm_per_m": s, "scale_denominator": nm_per_mm * 1000.0,
                     "crs": route.get("crs"), "georeferenced": geo})
    if len(route["paths"]) == 1:
        kp_note = "KP: chainage from the template outlet along the single flow path. Jumpers (dashed) are not modelled hydraulically."
    else:
        kp_note = "KP-A, KP-B: branch runs from the template outlets; KPT: trunk from the gathering PLEM. Jumpers (dashed) are not modelled hydraulically."
    has_plem = any(a["kind"] == "plem" for a in nodes.values())
    has_cross = any(f["kind"] == "crossing" for f in route.get("features") or [])
    sh.notes = ["Coordinate reference: %s. Horizontal datum: %s. Vertical datum: %s." % (route.get("crs"), route.get("horizontal_datum"), route.get("vertical_datum")),
                "Plan scale 1:%d at A3 (1 mm = %.1f m). KP marks every 5 km of measured chainage, interpolated along the plan polyline in proportion to measured length." %
                (denom, nm_per_mm),
                "Chainage convention: %s" % route["chainage"]["origin"],
                kp_note,
                ("Safety zones, third-party infrastructure and seabed hazards were not supplied and are not drawn; only the crossing marked is shown and it is unverified." if has_cross else
                 "Safety zones, third-party infrastructure, crossings and seabed hazards were not supplied and are not drawn."),
                "Pig interfaces and injection points have no plan coordinates and are shown on the connectivity schematic (sheet %d) only." % ctx["codes"]["DWG-01"]]
    sh.legend = [("line", INK, "solid", "Modelled hydraulic route (plan)"), ("line", GREY, "dash", "Non-modelled jumper"), ("marker", "square", False, "Template / host interface")]
    if has_plem:
        sh.legend.append(("marker", "triangle", False, "Gathering PLEM"))
    sh.legend.append(("marker", "circle", True, "Riser base"))
    if has_cross:
        sh.legend.append(("marker", "cross", False, "Crossing (unverified)"))
    sh.legend.append(("text", "Ticks: KP marks every 5 km of chainage"))


def _strip_plan(sh, ds, ctx, px0, py0, pw, ph):
    """Chainage strip used when plan coordinates are missing: no map accuracy is implied."""
    route = ds["route"]
    segs = {s["id"]: s for s in route["segments"]}
    paths = route["paths"]
    maxlen = max(sum(segs[i]["length_m"] for i in c) for c in paths.values())
    s = (pw - 20.0) / maxlen
    rows = list(paths.items())
    for r, (pid, chain) in enumerate(rows):
        y = py0 + 30.0 + r * (ph - 60.0) / max(len(rows) - 1, 1) if len(rows) > 1 else py0 + ph / 2.0
        x = px0 + 10.0
        sh.text(px0, y - 8.0, "path %s (chainage strip, not a map)" % pid, 2.8, "start", bold=True)
        for sid in chain:
            L = segs[sid]["length_m"]
            sh.add(K.line(x, y, x + L * s, y, sw=0.9))
            sh.add(K.line(x, y - 1.5, x, y + 1.5, sw=0.5))
            sh.text(x + L * s / 2.0, y + 5.0, sid, 2.5, "middle")
            x += L * s
        sh.add(K.line(x, y - 1.5, x, y + 1.5, sw=0.5))
    sh.text(px0 + pw / 2.0, py0 + ph + 4.0, "SCHEMATIC - NOT GEOREFERENCED (no plan coordinates supplied)", 3.6, "middle", bold=True, fill=RED, layer="banner")
    sh.scale_text = "NOT TO SCALE"
    sh.tb["scale"] = "NOT TO SCALE"
    sh.notes = ["No plan coordinates were supplied: the sheet shows chainage strips only and implies no map position or accuracy.",
                "Chainage convention: %s" % route["chainage"]["origin"]]
    sh.legend = [("line", INK, "solid", "Modelled hydraulic route (chainage strip)")]
    sh.items.append({"type": "plan_frame", "x_range_m": None, "y_range_m": None, "mm_per_m": s, "scale_denominator": None, "crs": route.get("crs"), "georeferenced": False})


def _spec_text(layers):
    return ", ".join("%s %.1f mm" % (l["name"], l["thickness_m"] * 1000.0) for l in layers)


def arrangement_detail(sh, ds, rs, ctx):
    """Sheet D: schematic PLEM tie-in (plan) and riser (elevation) with identifiers and known dimensions; no fabrication data."""
    route = ds["route"]
    nodes = route["nodes"]
    segs = {s["id"]: s for s in route["segments"]}
    # left panel: PLEM tie-in
    lx0, lx1 = 16.0, 206.0
    sh.add(K.rect(lx0, 22.0, lx1 - lx0, 200.0, fill="none", sw=0.35))
    cx, cy = 150.0, 120.0
    plem = route["sink"]
    for n, a in nodes.items():
        if a["kind"] == "plem":
            plem = n
    has_plem = any(a["kind"] == "plem" for a in nodes.values())
    if len(route["paths"]) == 1 and not has_plem:
        # single path without a gathering PLEM: show the transition into the riser (or the last segment into the host) instead
        pid0, chain0 = next(iter(route["paths"].items()))
        ridx = next((i for i, c in enumerate(chain0) if segs[c]["kind"] == "riser"), None)
        if ridx:
            plem = segs[chain0[ridx]]["from"]
            branches = [(pid0.split("->")[0], chain0[:ridx])]
            trunk = [chain0[ridx]]
        else:
            plem = segs[chain0[-1]]["to"]
            branches = [(pid0.split("->")[0], chain0)]
            trunk = [chain0[-1]]
    else:
        branches = [(pid.split("->")[0], [c for c in chain if c in set(chain) - set.intersection(*[set(c2) for c2 in route["paths"].values()])] if len(route["paths"]) > 1 else chain)
                    for pid, chain in route["paths"].items()]
        trunk = [c for c in list(route["paths"].values())[0] if all(c in ch for ch in route["paths"].values())]
    sh.text(lx0 + 2.0, 27.0, ("%s tie-in - plan schematic, NOT TO SCALE" % plem) if has_plem else "%s transition - plan schematic, NOT TO SCALE" % plem, 3.2, "start", bold=True)
    n_br = len(branches)
    for i, (src, chain) in enumerate(branches):
        yy = 120.0 + (i - (n_br - 1) / 2.0) * 76.0
        sh.add(K.polyline([(24.0, yy), (100.0, yy), (cx - 12.0, cy)], sw=1.0))
        sh.add(K.arrowhead(cx - 12.0, cy, 12.0, cy - yy if abs(cy - yy) > 0.1 else 0.0001))
        last = segs[chain[-1]]
        sh.text(24.0, yy - 10.0, src, 3.0, "start", bold=True)
        sh.text(24.0, yy - 6.4, "%s (last segment of the run)" % chain[-1], 2.6, "start")
        sh.text(24.0, yy + 6.0, "ID %.1f mm, steel wall %.1f mm" % (last["id_m"] * 1000.0, last["layers"][0]["thickness_m"] * 1000.0), 2.6, "start")
        for j, (t, _) in enumerate(K.wrap(_spec_text(last["layers"][1:]), 2.5, 74.0, max_lines=3)):
            sh.text(24.0, yy + 9.6 + j * 3.1, t, 2.5, "start")
        sh.items.append({"type": "tie_in_branch", "source": src, "segment": chain[-1], "id_mm": last["id_m"] * 1000.0})
    sh.add(K.polyline([(cx + 12.0, cy), (196.0, cy)], sw=1.4))
    sh.add(K.arrowhead(196.0, cy, 1, 0))
    sh.add(_hex(cx, cy, 24, 16))
    sh.text(cx, cy + 1.0, plem, 3.0, "middle", bold=True)
    t0 = segs[trunk[0]]
    sh.text(cx + 14.0, cy - 9.0, "%s %s" % ("trunk" if has_plem else "downstream", trunk[0]), 3.0, "start", bold=True)
    sh.text(cx + 14.0, cy - 5.6, "ID %.1f mm, steel wall %.1f mm" % (t0["id_m"] * 1000.0, t0["layers"][0]["thickness_m"] * 1000.0), 2.6, "start")
    for j, (t, _) in enumerate(K.wrap(_spec_text(t0["layers"][1:]), 2.5, 44.0, max_lines=4)):
        sh.text(cx + 14.0, cy + 7.0 + j * 3.1, t, 2.5, "start")
    def _build(sg):
        return (round(sg["id_m"], 6), [(l["name"], round(l["thickness_m"], 6)) for l in sg["layers"]])

    if any(_build(segs[b[1][-1]]) != _build(t0) for b in branches):
        transition = True
        sh.add(fa_draw.patterned([(cx + 7.0, cy - 14.0), (cx + 7.0, cy + 14.0)], fa_draw.PATTERNS["dash"], RED, 0.6))
        sh.text(cx + 8.5, cy - 15.5, "size and insulation transition", 2.6, "start", fill=RED)
    else:
        transition = False
        sh.text(cx + 8.5, cy - 15.5, "no size or insulation change at this node", 2.6, "start", fill=GREY)
    sh.text(lx0 + 2.0, 216.0, "Hydraulic tie-in only: structural, fabrication, weld and support design are outside this study; no dimensions are implied.", 2.5, "start", fill=GREY)
    for f in route.get("features") or []:
        if f["kind"] == "pig_launcher":
            sh.add(K.rect(cx - 8.0, cy + 30.0, 16.0, 7.0, fill="#fff", sw=0.4))
            sh.add(fa_draw.patterned([(cx, cy + 8.0), (cx, cy + 30.0)], fa_draw.PATTERNS["dot"], GREY, 0.4))
            sh.text(cx, cy + 35.0, f["id"], 2.6, "middle")
            sh.text(cx, cy + 42.0, "pig launcher interface (not modelled)", 2.6, "middle", fill=GREY)
    # right panel: riser elevation
    rx0, rx1 = 214.0, 406.0
    sh.add(K.rect(rx0, 22.0, rx1 - rx0, 200.0, fill="none", sw=0.35))
    sh.text(rx0 + 2.0, 27.0, "Riser - elevation schematic, NOT TO SCALE", 3.2, "start", bold=True)
    rs_seg = [s for s in route["segments"] if s["kind"] == "riser" and s.get("hydraulic", True)]
    if rs_seg:
        r = rs_seg[0]
        a, b = nodes[r["from"]], nodes[r["to"]]
        z0, z1 = a["elev_m"], b["elev_m"]
        ytop, ybot = 50.0, 200.0
        zr = (min(z0, 0.0) - 25.0, max(z1, 0.0) + 25.0)
        Z = lambda z: ybot - (z - zr[0]) / (zr[1] - zr[0]) * (ybot - ytop)
        xb, xh = 250.0, 330.0
        sh.add(fa_draw.patterned([(rx0 + 4.0, Z(0.0)), (rx1 - 4.0, Z(0.0))], fa_draw.PATTERNS["longdash"], "#2471a3", 0.5))
        sh.text(rx1 - 6.0, Z(0.0) - 1.4, "mean sea level (datum)", 2.6, "end", fill="#2471a3")
        sh.add(K.line(rx0 + 4.0, Z(z0), xb + 6.0, Z(z0), sw=0.8))
        sh.text(rx0 + 6.0, Z(z0) + 4.0, "seabed at riser base", 2.6, "start")
        sh.add(K.polyline([(xb, Z(z0)), (xh, Z(z1))], sw=1.6))
        sh.add(K.circle(xb, Z(z0), 1.6, fill=INK))
        sh.add(K.rect(xh - 3.0, Z(z1) - 3.0, 6.0, 6.0, fill="#fff", sw=0.9))
        sh.text(xb - 2.0, Z(z0) - 3.0, r["from"], 2.8, "end", bold=True)
        sh.text(xh, Z(z1) - 6.0, r["to"], 2.8, "middle", bold=True)
        # dimension lines
        dx_ = xh + 38.0
        sh.add(K.line(xh + 4.0, Z(z1), dx_ + 2.0, Z(z1), stroke=GREY, sw=0.25) + K.line(xb, Z(z0), dx_ + 2.0, Z(z0), stroke=GREY, sw=0.25))
        sh.add(K.line(dx_, Z(z1), dx_, Z(z0), sw=0.4) + K.arrowhead(dx_, Z(z1), 0, -1) + K.arrowhead(dx_, Z(z0), 0, 1))
        sh.bound_text(rs, dx_ + 2.0, (Z(z0) + Z(z1)) / 2.0 - 1.5, "seg:%s:dz_m" % r["id"], ".0f", "elevation difference ", " m", 2.6, "start", unit="m")
        sh.text(dx_ + 2.0, (Z(z0) + Z(z1)) / 2.0 + 1.8, "(%s to %s)" % (r["from"], r["to"]), 2.6, "start")
        sh.bound_text(rs, (xb + xh) / 2.0 - 4.0, Z(z0) + 14.0, "seg:%s:length_m" % r["id"], ".0f", "measured length ", " m", 2.6, "middle", unit="m")
        sh.bound_text(rs, (xb + xh) / 2.0 - 4.0, Z(z0) + 18.0, "seg:%s:horizontal_length_m" % r["id"], ".0f", "plan distance of the end nodes ", " m", 2.6, "middle", unit="m") \
            if r.get("horizontal_length_m") is not None else sh.text((xb + xh) / 2.0, Z(z0) + 18.0, "plan distance unknown", 2.6, "middle")
        sh.text(rx0 + 6.0, 215.0, "Geometry between the end nodes is not supplied: the riser is drawn as a straight chord, bends, supports and hang-off are not shown.", 2.5, "start", fill=GREY)
        sh.text(rx0 + 6.0, 42.0, "ID %.1f mm, steel wall %.1f mm; %s" % (r["id_m"] * 1000.0, r["layers"][0]["thickness_m"] * 1000.0, _spec_text(r["layers"][1:])), 2.6, "start")
        sh.items.append({"type": "riser", "segment": r["id"], "length_m": r["length_m"], "dz_m": r["dz_m"], "elev_start_m": z0, "elev_end_m": z1})
    sh.notes = ["Schematic arrangement: shows identifiers, nominal sizes, supplied elevations and layer thicknesses only.",
                "No fabrication dimensions, welds, supports, bend radii or hang-off details are shown or implied; ISO 129-1 dimension presentation is not applied (see standards register).",
                "Insulation and size transitions follow the route file; a transition at a node is reported when the thermal build-up of adjacent segments differs.",
                "Elevations are relative to the stated vertical datum (positive up)."]
    sh.legend = [("line", INK, "solid", "Pipe run")]
    if transition:
        sh.legend.append(("line", RED, "dash", "Insulation / size transition"))
    sh.legend.append(("line", "#2471a3", "longdash", "Mean sea level (datum)"))
    if any(f["kind"] == "pig_launcher" for f in route.get("features") or []):
        sh.legend.append(("line", GREY, "dot", "Interface only (not modelled)"))
    sh.scale_text = "NOT TO SCALE"
    sh.tb["scale"] = "NOT TO SCALE"


def _fit_radii(layers, r_in, r_max):
    """Drawn radii for a section: layer thicknesses scaled to fit ``r_max`` with a minimum drawn thickness (not to scale)."""
    ts = [l["thickness_m"] * 1000.0 for l in layers]
    lo, hi = 0.0, 5.0
    for _ in range(60):
        s = 0.5 * (lo + hi)
        if r_in + sum(max(t * s, 0.9) for t in ts) > r_max:
            hi = s
        else:
            lo = s
    radii = [r_in]
    for t in ts:
        radii.append(radii[-1] + max(t * lo, 0.9))
    return radii


def cross_sections(sh, ds, rs, ctx):
    """Sheet E: concentric pipe cross-sections of every distinct thermal build-up with the U-value basis."""
    route = ds["route"]
    hyd = [s for s in route["segments"] if s.get("hydraulic", True)]
    specs, order = {}, []
    for s in hyd:
        key = (round(s["id_m"], 5), tuple((l["name"], l["thickness_m"], l["k_w_mk"]) for l in s["layers"]), (s.get("burial") or {}).get("depth_m"))
        if key not in specs:
            specs[key] = []
            order.append(key)
        specs[key].append(s["id"])
    n_spec = min(len(order), 8)
    cols = 4
    big = n_spec <= 4
    cw, ch = (96.0, 112.0) if big else (96.0, 98.0)
    rmax = 24.0 if big else 14.0
    base_y = 60.0 if big else 22.0
    for i, key in enumerate(order[:8]):
        ids = specs[key]
        s = next(x for x in hyd if x["id"] == ids[0])
        r_, c_ = divmod(i, cols)
        x0, y0 = 14.0 + c_ * cw, base_y + r_ * ch
        sh.add(K.rect(x0, y0, cw - 4.0, ch - 3.0, fill="none", sw=0.35))
        tag = ids[0] if len(ids) == 1 else "%s ... %s" % (ids[0], ids[-1])
        sh.text(x0 + 2.0, y0 + 5.0, "Section %s: %s" % (chr(65 + i), tag), 3.0, "start", bold=True)
        sh.text(x0 + 2.0, y0 + 9.0, "applies to %d segment%s" % (len(ids), "s" if len(ids) > 1 else ""), 2.6, "start", fill=GREY)
        cx, cy = x0 + 28.0, y0 + (22.0 if big else 20.0) + rmax
        layers = s["layers"]
        n = len(layers)
        r_in = 0.32 * rmax
        radii = _fit_radii(layers, r_in, rmax)
        fills = ["#c9ced6", "#f2dfb6", "#fbfbf0", "#9fa8b5", "#e6e6e6"]
        for k in range(n, 0, -1):
            sh.add(K.circle(cx, cy, radii[k], fill=fills[(k - 1) % len(fills)], sw=0.3))
        sh.add(K.circle(cx, cy, radii[0], fill="#fff", sw=0.4))
        sh.text(cx, cy + 1.0, "fluid", 2.5, "middle")
        # leaders and layer labels
        lx = x0 + 58.0
        for k, l in enumerate(layers):
            rm = (radii[k] + radii[k + 1]) / 2.0
            ang = math.radians(-60 + k * 14)
            sx, sy = cx + rm * math.cos(ang), cy + rm * math.sin(ang)
            ly = y0 + 22.0 + k * 8.2
            sh.add(K.line(sx, sy, lx - 1.5, ly - 0.9, stroke=GREY, sw=0.2))
            sh.text(lx, ly, "%s" % l["name"], 2.6, "start")
            sh.text(lx, ly + 3.2, "%.1f mm | k %.2f W/mK" % (l["thickness_m"] * 1000.0, l["k_w_mk"]), 2.5, "start", fill=GREY)
        if s.get("burial"):
            gy = cy - radii[-1] - 6.0
            sh.add(K.line(x0 + 4.0, gy, x0 + 54.0, gy, sw=0.7))
            for hx in range(int(x0 + 6), int(x0 + 54), 5):
                sh.add(K.line(hx, gy, hx - 2.0, gy - 2.0, stroke=GREY, sw=0.2))
            sh.add(K.line(cx, gy, cx, cy, stroke=GREY, sw=0.3) + K.arrowhead(cx, gy, 0, -1, length=1.8, width=1.2) + K.arrowhead(cx, cy, 0, 1, length=1.8, width=1.2))
            sh.bound_text(rs, cx + 2.5, gy + 4.0, "seg:%s:burial.depth_m" % s["id"], ".1f", "burial ", " m (axis)", 2.5, "start", unit="m")
            sh.text(x0 + 4.0, gy - 3.0, "seabed / backfill surface", 2.5, "start", fill=GREY)
        ty = cy + rmax + 10.0
        sh.text(x0 + 2.0, ty, "ID", 2.6, "start", fill=GREY)
        sh.bound_text(rs, x0 + 12.0, ty, "seg:%s:id_m" % s["id"], ".1f", "", " mm", 2.6, "start", unit="mm")
        sh.text(x0 + 34.0, ty, "OD", 2.6, "start", fill=GREY)
        sh.bound_text(rs, x0 + 44.0, ty, "seg:%s:od_m" % s["id"], ".1f", "", " mm", 2.6, "start", unit="mm")
        sh.bound_text(rs, x0 + 2.0, ty + 4.6, "seg:%s:thermal.u_inner_w_m2k" % s["id"], ".2f", "U (inner-area basis) ", " W/m2K", 2.8, "start", bold=True)
        sh.bound_text(rs, x0 + 2.0, ty + 8.6, "seg:%s:thermal.u_outer_w_m2k" % s["id"], ".2f", "U (outer-area basis) ", " W/m2K", 2.6, "start")
        ext = "soil shape factor" if s.get("burial") else "external film"
        sh.text(x0 + 2.0, ty + 12.6, "external boundary: %s" % ext, 2.5, "start", fill=GREY)
        if not s.get("burial"):
            sh.bound_text(rs, x0 + 2.0, ty + 16.0, "seg:%s:ambient.external_h_w_m2k" % s["id"], ".0f", "h_ext = ", " W/m2K (input)", 2.5, "start", fill=GREY)
        sh.text(x0 + 2.0, ty + 19.8, "NOT TO SCALE (thin layers drawn thicker)", 2.5, "start", fill=RED)
        sh.items.append({"type": "section", "segments": ids, "u_inner_w_m2k": s["thermal"]["u_inner_w_m2k"], "u_outer_w_m2k": s["thermal"]["u_outer_w_m2k"], "n_layers": n})
    sh.notes = ["U-values are referenced to the INNER pipe surface (pi x ID x L), the basis used by the hydraulic solver (SPECIFIED_U); the outer-area value is shown for comparison only.",
                "Internal film resistance is neglected: U is larger than the true value, which is conservative for arrival temperature and cooldown.",
                "External boundary: seawater film coefficient (input) for exposed segments, or the buried-pipe shape factor with the stated soil conductivity.",
                "Layer thermal properties are inputs of the route file; wet-insulation degradation, field joints and end effects are not modelled.",
                "Sections are schematic and not to scale; they show no fabrication detail."]
    sh.legend = [("text", "Concentric layers from the fluid outward: steel wall, coating, insulation, sheath"), ("line", GREY, "solid", "Leader line to the layer table")]
    sh.scale_text = "NOT TO SCALE"
    sh.tb["scale"] = "NOT TO SCALE"
