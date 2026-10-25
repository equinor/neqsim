"""Detail, umbilical, thermal, installation, results and register sheets of the SURF drawing package (A3 landscape)."""
import math

import fa_draw
import surf_draw as D
from fa_draw import INK, GREY, RED
from psr_diagrams import kit as K
from surf_sheets_a import _design_case


def riser_sheet(sh, ds, rs, ctx):
    """DWG-07: riser configuration and elevation. The shape is the specification polyline and is NOT an analysed equilibrium."""
    ref = ds["reference_concept"]
    lines = D.concept_edges(ds, ref)
    geom = ds["geometry"][ref]
    nodes = D.concept_nodes(ds, ref)
    d_case = _design_case(ds)
    risers = [r for r in lines.values() if r["kind"] == "riser"]
    sh.scale_text = "NOT TO SCALE (concept schematic; polyline from the specification, geometry not analysed)"
    sh.tb["scale"] = sh.scale_text
    for i, r in enumerate(risers):
        x0 = 20.0 + i * 200.0
        g = geom[r["id"]]["points"]
        pm = D.PlanMap((min(p[0] for p in g) - 40.0, min(p[2] for p in g) - 60.0, max(p[0] for p in g) + 40.0, max(p[2] for p in g) + 50.0), (x0 + 12.0, 56.0, x0 + 150.0, 150.0))
        sh.text(x0, 33.0, "%s %s (%s, %s)" % (r["tag"], r["id"], "flexible" if r["construction"] == "flexible" else "steel", r["service"]), 3.0, bold=True)
        sh.text(x0, 39.0, "CONCEPT SCHEMATIC - GEOMETRY NOT ANALYZED", 2.9, bold=True, fill=RED)
        pts = [pm.pt(p[0] if r["service"] == "injection" or True else p[0], p[2]) for p in g]
        if r["service"] == "production":
            pts = [pm.pt(p[0], p[2]) for p in g]
        D.polyline(sh, pts, D.COLORS[r["service"]], 1.0)
        sea_y = pm.pt(0.0, 0.0)[1]
        D.polyline(sh, [(x0 + 4.0, sea_y), (x0 + 156.0, sea_y)], "#1f78b4", 0.5, "dash")
        sh.text(x0 + 6.0, sea_y - 1.6, "sea surface (z = 0 m)", 2.4, fill="#1f78b4")
        seabed = pm.pt(0.0, g[0][2])[1] if g[0][2] < g[-1][2] else pm.pt(0.0, g[-1][2])[1]
        z_sea = min(p[2] for p in g)
        D.polyline(sh, [(x0 + 4.0, pm.pt(0.0, z_sea)[1] + 1.0), (x0 + 156.0, pm.pt(0.0, z_sea)[1] + 1.0)], "#8b6b3d", 0.7)
        sh.text(x0 + 6.0, pm.pt(0.0, z_sea)[1] + 5.0, "seabed (synthetic)", 2.4, fill="#8b6b3d")
        top = pts[0] if g[0][2] > g[-1][2] else pts[-1]
        base = pts[-1] if g[0][2] > g[-1][2] else pts[0]
        sh.add(K.rect(top[0] - 8.0, top[1] - 7.0, 16.0, 7.0, fill="#e8e8e8", sw=0.6))
        sh.text(top[0], top[1] - 8.4, "host hang-off", 2.4, "middle")
        D.symbol(sh, "plem" if r["service"] == "production" else "plet", base[0], base[1], 2.2)
        end_n = nodes[r["from"]] if g[0][2] < g[-1][2] else nodes[r["to"]]
        sh.text(base[0], base[1] + 6.6, end_n["tag"], 2.4, "middle", bold=True)
        sh.items.append({"type": "line", "id": r["id"], "tag": r["tag"], "service": r["service"], "length_m": r["installed_length_m"]})
        horiz = math.hypot(g[-1][0] - g[0][0], g[-1][1] - g[0][1])
        sh.text(x0 + 4.0, 156.0, "Dimensions (from the specification): installed length %.0f m; horizontal offset %.0f m; vertical span %.0f m;" % (r["installed_length_m"], horiz, abs(g[-1][2] - g[0][2])), 2.5)
        sh.text(x0 + 4.0, 160.4, "bore %.0f mm; design %.0f bara / %.0f degC; touchdown, buoyancy and hang-off loads: not defined (analysis hand-off HO-01)." % (r["id_mm"], r["design_pressure_bara"], r["design_temperature_c"]), 2.5)
        rec = ds["results"][ref][d_case]["production" if r["service"] == "production" else "injection"]["edges"][r["id"]]
        rows = [["Inlet pressure (bara)", "%.1f" % rec["inlet_pressure_bara"]], ["Outlet pressure (bara)", "%.1f" % rec["outlet_pressure_bara"]], ["Outlet temperature (degC)", "%.1f" % rec["outlet_temperature_c"]],
                ["Flow (kg/s, %s case)" % d_case, "%.1f" % rec["flow_kg_s"]]]
        D.table(sh, x0 + 4.0, 168.0, [60.0, 26.0], ["Fluid state at the riser (interface load input)", "Value"], rows, size=2.4, row_h=4.2)
    sh.legend += [("line", D.COLORS["production"], "solid", "production riser polyline"), ("line", D.COLORS["injection"], "solid", "injection riser polyline"), ("line", "#1f78b4", "dash", "sea surface")]
    sh.notes += ["The riser shapes are the specification polylines. They are not an equilibrium solution: a lazy-wave or catenary label does not define the geometry.",
                 "Static and dynamic response, VIV and fatigue, interference and coupled host/mooring analysis require a qualified external solver (hand-off HO-01).",
                 "Fluid states are the solved NeqSim values and are inputs to that analysis."]


def _arrangement(sh, ds, ref, st, x0, y0, w, h, scale_denom):
    """Plan of one template: footprint, nodes, jumpers, hub tags and an indicative ROV envelope (schematic, not a vendor GA)."""
    nodes = D.concept_nodes(ds, ref)
    lines = D.concept_edges(ds, ref)
    members = [n for n in nodes.values() if n.get("structure") == st["id"]]
    pm = D.PlanMap((st["x_m"] - st["length_m"] / 2.0 - 25.0, st["y_m"] - st["width_m"] / 2.0 - 25.0, st["x_m"] + st["length_m"] / 2.0 + 25.0, st["y_m"] + st["width_m"] / 2.0 + 25.0), (x0, y0, x0 + w, y0 + h))
    a = pm.pt(st["x_m"] - st["length_m"] / 2.0, st["y_m"] + st["width_m"] / 2.0)
    b = pm.pt(st["x_m"] + st["length_m"] / 2.0, st["y_m"] - st["width_m"] / 2.0)
    sh.add(K.rect(a[0], a[1], b[0] - a[0], b[1] - a[1], fill="#f4f4f4", sw=0.6))
    ea, eb = pm.pt(st["x_m"] - st["length_m"] / 2.0 - 20.0, st["y_m"] + st["width_m"] / 2.0 + 20.0), pm.pt(st["x_m"] + st["length_m"] / 2.0 + 20.0, st["y_m"] - st["width_m"] / 2.0 - 20.0)
    D.polyline(sh, [(ea[0], ea[1]), (eb[0], ea[1]), (eb[0], eb[1]), (ea[0], eb[1]), (ea[0], ea[1])], GREY, 0.4, "dash")
    sh.text(ea[0] + 1.0, ea[1] + 3.0, "indicative ROV envelope (schematic)", 2.3, fill=GREY)
    sh.text(a[0], a[1] - 2.0, "%s  %.0f x %.0f m footprint (specification)" % (st["name"], st["length_m"], st["width_m"]), 2.5, bold=True)
    for r in lines.values():
        if r["from"] in [m["id"] for m in members] and r["to"] in [m["id"] for m in members]:
            p, q = nodes[r["from"]], nodes[r["to"]]
            (x1, y1), (x2, y2) = pm.pt(p["x_m"], p["y_m"]), pm.pt(q["x_m"], q["y_m"])
            D.polyline(sh, [(x1, y1), (x2, y2)], D.COLORS[r["service"]], 0.8, None if r["service"] == "production" else "dash")
            mx, my = (x1 + x2) / 2.0, (y1 + y2) / 2.0
            ln = math.hypot(x2 - x1, y2 - y1) or 1.0
            nx, ny = (y2 - y1) / ln, -(x2 - x1) / ln
            if ny > 0.0 or (abs(ny) < 1e-6 and nx < 0.0):
                nx, ny = -nx, -ny
            anc = "start" if nx > 0.3 else ("end" if nx < -0.3 else "middle")
            sh.text(mx + nx * 4.2, my + ny * 4.2 - (0.0 if abs(ny) > 0.3 else -1.0), "%s %.0f m" % (r["tag"], r["installed_length_m"]), 2.3, anc)
            sh.items.append({"type": "line", "id": r["id"], "tag": r["tag"], "service": r["service"], "length_m": r["installed_length_m"]})
    for n in members:
        x, y = pm.pt(n["x_m"], n["y_m"])
        D.symbol(sh, n["kind"], x, y, 2.4)
        sh.text(x, y + (8.4 if n["kind"] == "injection_well" else 6.4), "%s" % n["tag"], 2.4, "middle", bold=True)
        sh.items.append({"type": "asset", "id": n["id"], "kind": n["kind"], "tag": n["tag"]})
    hubs = []
    for r in lines.values():
        if r["from"] in [m["id"] for m in members] and r["to"] not in [m["id"] for m in members]:
            hubs.append("hub '%s' > %s" % (_port(ds, r["id"], "from"), r["tag"]))
        if r["to"] in [m["id"] for m in members] and r["from"] not in [m["id"] for m in members]:
            hubs.append("hub '%s' < %s" % (_port(ds, r["id"], "to"), r["tag"]))
    return pm, hubs


def _port(ds, eid, end):
    return ds["ports"][eid][end] if "ports" in ds and eid in ds["ports"] else "-"


def arrangement_sheet(sh, ds, rs, ctx):
    """DWG-08: template, manifold, PLEM and PLET arrangement (plan, elevation and isometric); schematic envelopes, not vendor general arrangements."""
    ref = ds["reference_concept"]
    sts = ds["structures"]
    for i, st in enumerate(sts[:2]):
        x0 = 20.0 + i * 198.0
        sh.text(x0, 31.0, "%s: plan" % st["id"], 3.0, bold=True)
        pm, hubs = _arrangement(sh, ds, ref, st, x0, 40.0, 180.0, 96.0, 500)
        if i == 0:
            sh.scale_text = "plan panels 1:%d (equal x and y); elevation and isometric NOT TO SCALE" % int(round(pm.scale_denominator, -1))
            sh.tb["scale"] = sh.scale_text
        sh.text(x0, 140.0, "Plan scale about 1:%d on the A3 sheet; schematic envelope only, not a vendor general arrangement." % int(round(pm.scale_denominator, -1)), 2.4, fill=GREY)
        sh.text(x0, 144.6, "Hub connections: " + "; ".join(hubs), 2.3, fill=GREY)
    # elevation + isometric of the first template
    st = sts[0]
    nodes = [n for n in D.concept_nodes(ds, ref).values() if n.get("structure") == st["id"]]
    sh.text(20.0, 152.0, "%s: isometric (NOT TO SCALE)" % st["id"], 3.0, bold=True)
    cx, cy = 90.0, 186.0
    k = 0.55
    def iso(n):
        dx, dy, dz = n["x_m"] - st["x_m"], n["y_m"] - st["y_m"], n["z_m"] - st["z_m"]
        return (cx + (dx - dy) * 0.866 * k, cy - (dx + dy) * 0.5 * k - dz * k)
    corners = [(-st["length_m"] / 2, -st["width_m"] / 2), (st["length_m"] / 2, -st["width_m"] / 2), (st["length_m"] / 2, st["width_m"] / 2), (-st["length_m"] / 2, st["width_m"] / 2)]
    poly = [(cx + (a - b) * 0.866 * k, cy - (a + b) * 0.5 * k) for a, b in corners]
    D.polyline(sh, poly + [poly[0]], INK, 0.5)
    for n in nodes:
        x, y = iso(n)
        D.symbol(sh, n["kind"], x, y, 1.8)
        sh.text(x, y + 5.4, n["id"], 2.2, "middle")
    lines = D.concept_edges(ds, ref)
    nd = {n["id"]: n for n in nodes}
    for r in lines.values():
        if r["from"] in nd and r["to"] in nd:
            D.polyline(sh, [iso(nd[r["from"]]), iso(nd[r["to"]])], D.COLORS[r["service"]], 0.7, None if r["service"] == "production" else "dash")
    sh.text(20.0, 218.0, "All %s equipment stands on the seabed at z = %.0f m; hub orientations and heights are not defined (vendor data)." % (st["id"], st["z_m"]), 2.4, fill=GREY)
    # PLEM and PLET arrangement
    sh.text(212.0, 152.0, "PLEM / PLET connection schematic (NOT TO SCALE)", 3.0, bold=True)
    cases = ds["results"][ref]
    py = 176.0
    for j, n in enumerate([n for n in D.concept_nodes(ds, ref).values() if n["kind"] in ("plem", "plet")]):
        px = 240.0 + j * 100.0
        D.symbol(sh, n["kind"], px, py, 5.0)
        sh.text(px, py - 9.0, "%s (%s)" % (n["tag"], n["id"]), 2.6, "middle", bold=True)
        sh.text(px, py + 14.0, "z = %.0f m; elevation head, hubs and mud-mat not defined" % n["z_m"], 2.3, "middle", fill=GREY)
        conn = [r for r in lines.values() if r["from"] == n["id"] or r["to"] == n["id"]]
        for q, r in enumerate(conn):
            sh.text(px, py + 18.6 + q * 3.6, "%s %s %s" % (r["tag"], "in" if r["to"] == n["id"] else "out", "port '%s'" % _port(ds, r["id"], "to" if r["to"] == n["id"] else "from")), 2.3, "middle")
        sh.items.append({"type": "asset", "id": n["id"], "kind": n["kind"], "tag": n["tag"]})
    sh.legend += [("marker", "diamond", False, "well, manifold, PLEM, PLET symbols (exaggerated)"), ("line", D.COLORS["production"], "solid", "production jumper"), ("line", D.COLORS["injection"], "dash", "injection jumper"),
                  ("line", GREY, "dash", "indicative ROV envelope")]
    sh.notes += ["Positions and jumper lengths are specification values; footprints and envelopes are schematic and distinct from vendor general arrangements and fabrication drawings.",
                 "Hub orientation, slot geometry, maintenance and ROV envelopes are not defined; they remain vendor and installation inputs.", "Dimensions are presented as the lengths of the registered lines (ISO 129-1 style annotation without tolerances)."]


def jumper_sheet(sh, ds, rs, ctx):
    """DWG-09: jumper and tie-in details (chord between the specified end points; no bends, supports or tolerances invented)."""
    ref = ds["reference_concept"]
    lines = D.concept_edges(ds, ref)
    geom = ds["geometry"][ref]
    nodes = D.concept_nodes(ds, ref)
    jumpers = [r for r in lines.values() if r["kind"] == "jumper"]
    pick = [next(r for r in jumpers if r["service"] == "production"), next(r for r in jumpers if r["service"] == "injection")]
    sh.scale_text = "NOT TO SCALE (end-point chords; dimensions from the specification)"
    sh.tb["scale"] = sh.scale_text
    for i, r in enumerate(pick):
        x0 = 20.0 + i * 198.0
        g = geom[r["id"]]["points"]
        a, b = nodes[r["from"]], nodes[r["to"]]
        sh.text(x0, 31.0, "%s %s: %s to %s (%s)" % (r["tag"], r["id"], a["tag"], b["tag"], r["service"]), 3.0, bold=True)
        # plan view
        L = math.hypot(g[-1][0] - g[0][0], g[-1][1] - g[0][1])
        sh.text(x0, 40.0, "Plan view", 2.8, bold=True)
        pm = D.PlanMap((min(p[0] for p in g) - 15, min(p[1] for p in g) - 15, max(p[0] for p in g) + 15, max(p[1] for p in g) + 15), (x0 + 8.0, 44.0, x0 + 150.0, 90.0))
        p1, p2 = pm.pt(g[0][0], g[0][1]), pm.pt(g[-1][0], g[-1][1])
        D.polyline(sh, [p1, p2], D.COLORS[r["service"]], 1.1)
        D.symbol(sh, a["kind"], p1[0], p1[1], 2.4)
        D.symbol(sh, b["kind"], p2[0], p2[1], 2.4)
        up1 = p1[1] <= p2[1]
        sh.text(p1[0], p1[1] - 5.0 if up1 else p1[1] + 8.0, a["tag"], 2.4, "middle", bold=True)
        sh.text(p2[0], p2[1] + 8.0 if up1 else p2[1] - 5.0, b["tag"], 2.4, "middle", bold=True)
        sh.text(x0 + 8.0, 97.0, "horizontal chord %.1f m; installed length %.1f m" % (L, r["installed_length_m"]), 2.4)
        # elevation view
        sh.text(x0, 104.0, "Elevation view", 2.8, bold=True)
        dz = g[-1][2] - g[0][2]
        sh.add(K.line(x0 + 8.0, 126.0, x0 + 150.0, 126.0, sw=0.4, stroke="#8b6b3d"))
        sh.text(x0 + 10.0, 130.4, "seabed z = %.0f m (both end points at the same elevation)" % g[0][2] if abs(dz) < 0.01 else "end-point elevation difference %.1f m" % dz, 2.4, fill="#8b6b3d")
        D.polyline(sh, [(x0 + 30.0, 118.0), (x0 + 128.0, 118.0 - dz)], D.COLORS[r["service"]], 1.1)
        D.symbol(sh, a["kind"], x0 + 30.0, 118.0, 2.0)
        D.symbol(sh, b["kind"], x0 + 128.0, 118.0 - dz, 2.0)
        D.table(sh, x0 + 4.0, 140.0, [64.0, 80.0], ["Item", "Value (source: specification)"],
                [["OD x wall (mm)", "%.1f x %.1f, %s" % (r["od_mm"], r["wall_mm"], r["grade"])], ["Inside diameter (mm)", "%.1f" % r["id_mm"]], ["Insulation", r["insulation"]],
                 ["Design pressure / temperature", "%.0f bara / %.0f degC" % (r["design_pressure_bara"], r["design_temperature_c"])], ["Installation", str(r["installation"])],
                 ["Bends, supports, weld numbers", "not defined (no as-built metrology)"], ["Connector hubs and tolerances", "not defined (vendor data)"]], size=2.4, row_h=4.2)
        sh.items.append({"type": "line", "id": r["id"], "tag": r["tag"], "service": r["service"], "length_m": r["installed_length_m"]})
    sh.legend += [("line", D.COLORS["production"], "solid", "production jumper"), ("line", D.COLORS["injection"], "solid", "injection jumper")]
    sh.notes += ["Conceptual end-point drawing: the jumper is a straight chord between the specified end points; bends, spans, supports and stresses are not defined.",
                 "No as-built metrology, weld numbering, stress results or fabrication tolerances are shown or implied."]


def umbilical_sheet(sh, ds, rs, ctx):
    """DWG-10: umbilical architecture (services, terminations, consumers) and a conceptual cross-section."""
    umb = ds["umbilical"]["spec"]
    res = ds["umbilical"]["results"]
    services = ds["umbilical"]["services"]
    ref = ds["reference_concept"]
    sh.scale_text = "NOT TO SCALE (architecture); cross-section tube diameters to a stated scale"
    sh.tb["scale"] = sh.scale_text
    sh.text(22.0, 31.0, "%s %s - host to template umbilical terminations (functional service specification)" % (umb["tag"], umb["id"]), 3.0, bold=True)
    sh.add(K.rect(24.0, 50.0, 40.0, 24.0, fill="#e8e8e8", sw=0.7))
    sh.text(44.0, 60.0, "HOST", 2.8, "middle", bold=True)
    sh.text(44.0, 66.0, "HPU / MEG / chemical pumps", 2.3, "middle")
    x = 130.0
    prev = (64.0, 62.0)
    for t in umb["terminations"]:
        sh.add(K.rect(x, 50.0, 36.0, 24.0, fill="#fff", sw=0.7))
        sh.text(x + 18.0, 60.0, t["id"], 2.8, "middle", bold=True)
        sh.text(x + 18.0, 66.0, "to %s, route %.1f km" % (t["node"], t["route_length_m"] / 1000.0), 2.3, "middle")
        D.polyline(sh, [prev, (x, 62.0)], D.COLORS["umbilical"], 1.0)
        prev = (x + 36.0, 62.0)
        x += 90.0
    sh.items.append({"type": "asset", "id": umb["id"], "kind": "umbilical", "tag": umb["tag"]})
    rows = []
    for s in services:
        r = res.get(s["id"])
        rows.append([s["id"], s["kind"], s.get("consumer") or "-", ("%.0f mm tube, %.2f kg/s" % (s["tube_id_mm"], s["rate_kg_s"])) if s["kind"] == "chemical" else
                     (("%.0f mm tube" % s["tube_id_mm"]) if s["kind"] == "hydraulic" else ("%.0f kW" % s["demand_kw"] if s["kind"] == "power" else "%d channels" % s["channels"])),
                     ("%.1f bara delivered" % r["delivery_pressure_bara"]) if r else "not assessed", s["assessment_owner"]])
    D.table(sh, 22.0, 86.0, [24.0, 20.0, 18.0, 48.0, 38.0, 128.0], ["Service", "Kind", "Consumer", "Basis", "Result", "Assessment owner"], rows, size=2.3, row_h=4.2, title="Service register (functional sizing; manufacturer-qualified construction is a hand-off)")
    # conceptual cross-section
    cx, cy, R = 340.0, 168.0, 36.0
    sh.text(cx, 130.0, "Conceptual cross-section (NOT manufacturer-qualified)", 2.8, "middle", bold=True)
    sh.add(K.circle(cx, cy, R, fill="#f4f4f4", sw=0.8))
    sh.add(K.circle(cx, cy, R - 4.0, fill="#fff", sw=0.3))
    scale = 0.8
    ring = [s for s in services if s["kind"] in ("chemical", "hydraulic")]
    for i, s in enumerate(ring):
        ang = 2 * math.pi * i / max(len(ring), 1)
        rr = (R - 4.0) * 0.64
        x, y = cx + rr * math.cos(ang), cy + rr * math.sin(ang)
        r = s["tube_id_mm"] * scale / 2.0
        sh.add(K.circle(x, y, r + 0.8, fill="#fff", sw=0.4))
        sh.add(K.circle(x, y, r, fill="#dce6f2", sw=0.25))
        sh.text(x, y + r + 3.4, s["id"], 2.2, "middle")
    sh.add(K.circle(cx, cy, 5.5, fill="#f6e0c8", sw=0.4))
    sh.text(cx, cy - 0.3, "PWR", 2.2, "middle")
    sh.text(cx, cy + 2.6, "SIG", 2.2, "middle")
    sh.text(cx, cy + R + 5.0, "Tube inside diameters drawn at %.1f mm per mm; armour, packing and cable construction are not defined." % scale, 2.3, "middle", fill=GREY)
    sh.text(cx, cy + R + 9.0, "Provenance: specification tube IDs (synthetic); layout is a conceptual arrangement.", 2.3, "middle", fill=GREY)
    sh.legend += [("line", D.COLORS["umbilical"], "solid", "umbilical (functional)"), ("marker", "circle", True, "tubes and conductors in the conceptual section")]
    sh.notes += ["Chemical delivery pressure comes from a NeqSim single-phase Darcy-Weisbach solve of each tube network; hydraulic, power and signal services are registered, not modelled.",
                 "Electrical voltage drop, ampacity, protection, dynamic mechanics and the manufacturer cross-section require specialist tools (hand-offs HO-03 and HO-04)."]


def thermal_sheet(sh, ds, rs, ctx):
    """DWG-11: flowline and riser cross-sections drawn to scale with the U-value basis (bore area)."""
    ref = ds["reference_concept"]
    d_case = _design_case(ds)
    lines = D.concept_edges(ds, ref)
    seen, items = {}, []
    for r in lines.values():
        key = (r["construction"], r["od_mm"], r["wall_mm"], r["insulation"], r["id_mm"])
        if key not in seen:
            seen[key] = [r]
            items.append(key)
        else:
            seen[key].append(r)
    scale = 0.16
    sh.scale_text = "cross-sections 0.16 mm per mm (about 1:6)"
    sh.tb["scale"] = sh.scale_text
    for i, key in enumerate(items[:8]):
        r0 = seen[key][0]
        cx = 62.0 + (i % 4) * 98.0
        cy = 84.0 + (i // 4) * 90.0
        od = (key[1] or key[4] * 1.3)
        ins = float(r0["insulation"].split()[1]) if r0["insulation"].split()[0] not in ("none",) else 0.0
        R_ins = od / 2.0 + ins
        if key[0] == "flexible":
            sh.add(K.circle(cx, cy, od / 2.0 * scale, fill="#e6e6e6", sw=0.6))
            sh.add(K.circle(cx, cy, key[4] / 2.0 * scale, fill="#fff", sw=0.5))
        else:
            sh.add(K.circle(cx, cy, R_ins * scale, fill="#fff2cc", sw=0.5))
            sh.add(K.circle(cx, cy, od / 2.0 * scale, fill="#9aa5b1", sw=0.5))
            sh.add(K.circle(cx, cy, key[4] / 2.0 * scale, fill="#fff", sw=0.5))
        sh.raw_box((cx - R_ins * scale, cy - R_ins * scale, cx + R_ins * scale, cy + R_ins * scale), "section")
        ids = ", ".join(x["id"] for x in seen[key])
        top = cy - R_ins * scale
        D.fit(sh, cx, top - 12.0, ids, 92.0, 2.6, "middle", True)
        D.fit(sh, cx, top - 8.0, "ID %.1f | OD %s | wall %s" % (key[4], "%.1f mm" % key[1] if key[1] else "not defined", "%.1f mm" % key[2] if key[2] else "not defined (flexible)"), 92.0, 2.4, "middle")
        D.fit(sh, cx, top - 4.4, "insulation: %s" % r0["insulation"], 92.0, 2.4, "middle")
        rec = ds["results"][ref][d_case]["production" if r0["service"] == "production" else "injection"]["edges"][r0["id"]]
        bottom = cy + R_ins * scale
        D.fit(sh, cx, bottom + 4.8, "U = %.2f W/m2K on the bore area" % rec["u_value_w_m2k"], 92.0, 2.5, "middle")
        D.fit(sh, cx, bottom + 8.6, ("flexible: layers not drawn; ambient %.0f degC" if key[0] == "flexible" else "ambient %.0f degC") % rec["ambient_c"], 92.0, 2.4, "middle", fill=GREY)
        for x in seen[key]:
            sh.items.append({"type": "line", "id": x["id"], "tag": x["tag"], "service": x["service"], "length_m": x["installed_length_m"]})
    sh.legend += [("marker", "square", False, "steel wall (grey), insulation (yellow), bore (white)")]
    sh.notes += ["Cross-sections are drawn to the stated scale from the specification OD, wall and insulation thickness; coatings and field joints are not drawn.",
                 "The U-value is referenced to the inner (bore) area, the basis used by the hydraulic network; for the flexible riser it is a supplier-style value from the specification.",
                 "A thermal conductivity of soil is not a geotechnical resistance model and no burial effect is assumed beyond the stated ambient."]


STAGES = [("S1", "Install templates and manifolds", ["T1", "T2"], "lift and set; seabed preparation not assessed"),
          ("S2", "Install PLEM and PLET, lay trunk and infield flowlines", None, "pipelay, pull-in and tie-in methods open (HO-05)"),
          ("S3", "Install risers and umbilical", None, "riser and dynamic umbilical installation analysis open (HO-01, HO-03)"),
          ("S4", "Install jumpers (ROV tie-in)", None, "metrology and spool fabrication open (HO-08)"),
          ("S5", "Hydrotest, clean, gauge and dewater", None, "pre-commissioning procedures not defined"),
          ("S6", "Commission injection, then production", None, "start-up sequence requires transient analysis (HO-07)")]


def installation_sheet(sh, ds, rs, ctx):
    """DWG-12: installation and tie-in sequence (conceptual stages with the asset positions on a strip plan)."""
    ref = ds["reference_concept"]
    lines = D.concept_edges(ds, ref)
    geom = ds["geometry"][ref]
    nodes = D.concept_nodes(ds, ref)
    allp = [(n["x_m"], n["y_m"]) for n in nodes.values()]
    pm = D.PlanMap(D.bbox_of(allp, 200.0), (150.0, 0.0, 330.0, 10.0))
    sh.scale_text = "NOT TO SCALE (conceptual sequence; strip plan positions from the specification)"
    sh.tb["scale"] = sh.scale_text
    sets = {"S1": [], "S2": [r["id"] for r in lines.values() if r["kind"] in ("flowline", "trunkline", "pipeline")], "S3": [r["id"] for r in lines.values() if r["kind"] == "riser"],
            "S4": [r["id"] for r in lines.values() if r["kind"] == "jumper"], "S5": list(lines), "S6": list(lines)}
    y = 34.0
    for code, title, assets, note in STAGES:
        sh.add(K.rect(22.0, y, 384.0, 26.0, fill="#fff", sw=0.4))
        sh.text(25.0, y + 6.0, "%s  %s" % (code, title), 2.9, bold=True)
        D.fit(sh, 25.0, y + 11.6, "Interfaces and open analysis: %s" % note, 124.0, 2.4, fill=RED)
        ids = (assets or sets[code])
        D.fit(sh, 25.0, y + 17.2, "Assets: %s" % ", ".join(ids), 124.0, 2.3)
        D.fit(sh, 25.0, y + 22.2, "Status: CONCEPTUAL SEQUENCE - marine-operation analyses open", 124.0, 2.3, fill=GREY)
        # strip plan
        top = y + 8.0
        strip = D.PlanMap(D.bbox_of(allp, 200.0), (170.0, top, 395.0, top + 10.0))
        for rid, r in lines.items():
            hi = rid in sets[code] or (code == "S1" and False)
            pts = [strip.pt(p[0], p[1]) for p in geom[rid]["points"]]
            D.polyline(sh, pts, INK if hi else "#cfcfcf", 0.8 if hi else 0.3)
        for n in nodes.values():
            if n["kind"] in ("manifold", "plem", "plet", "host", "injection_manifold"):
                x, yy = strip.pt(n["x_m"], n["y_m"])
                hi = (code == "S1" and n.get("structure") in ("T1", "T2")) or (code == "S2" and n["kind"] in ("plem", "plet")) or code in ("S5", "S6")
                sh.add(K.circle(x, yy, 1.1, fill=INK if hi else "#fff", sw=0.3))
        y += 29.0
    sh.legend += [("line", INK, "solid", "assets installed or connected in the stage"), ("line", "#cfcfcf", "solid", "other assets")]
    sh.notes += ["Conceptual sequence only: vessel capacity, lay tension and bend response, lifting, sea fastening and marine warranty acceptance are open specialist analyses (HO-05).",
                 "The strip plans show asset positions from the specification; no vessel, weather window or schedule is implied.", "Pre-commissioning (testing, cleaning, drying) and commissioning steps are listed for scope only."]


def results_sheet(sh, ds, rs, ctx):
    """DWG-13: design-result sheet: criteria by case and concept, governing envelopes and arrival conditions."""
    ref = ds["reference_concept"]
    sh.text(22.0, 31.0, "Acceptance criteria by concept and case (value | status); governing location in the report", 2.9, bold=True)
    header = ["Criterion"]
    cases = [c["id"] for c in ds["cases"]]
    for cid in ds["concepts"]:
        for c in cases:
            header.append("%s/%s" % (cid, c))
    rows = []
    for ci, crit in enumerate(ds["criteria_spec"]):
        row = ["%s %s %s" % (crit["id"], crit["operator"], ("%g" % crit["value"]) + ("" if crit["unit"] == "-" else " " + crit["unit"]))]
        for cid in ds["concepts"]:
            cr = ds["criteria"][cid][ci]
            for c in cases:
                b = cr["by_case"][c]
                row.append(("%.1f %s" % (b["value"], b["status"])) if b["value"] is not None else b["status"])
        rows.append(row)
    D.table(sh, 22.0, 36.0, [46.0] + [56.0 * 6 / max(len(header) - 1, 1)] * (len(header) - 1), header, rows, size=2.2, row_h=4.2)
    # envelope table
    d_case = _design_case(ds)
    env = []
    for r in ds["registers"][ref]["lines"]:
        pe = [ds["results"][ref][c]["derived"]["per_edge"][r["id"]] for c in cases]
        env.append([r["id"], r["tag"], "%.1f" % max(p["p_max_bara"] for p in pe), "%.1f" % min(p["p_min_bara"] for p in pe), "%.1f" % max(p["t_max_c"] for p in pe), "%.1f" % min(p["t_min_c"] for p in pe),
                    "%.0f / %.0f" % (r["design_pressure_bara"], r["design_temperature_c"])])
    D.table(sh, 22.0, 100.0, [26.0, 22.0, 20.0, 20.0, 20.0, 20.0, 30.0], ["Line", "Tag", "p max", "p min", "T max", "T min", "design p/T"], env[:13], size=2.2, row_h=4.0,
            title="Operating envelope over all cases, reference concept %s (bara, degC)" % ref)
    arr = []
    for c in cases:
        h = ds["results"][ref][c]["production"]["host"]
        arr.append([c, "%.1f" % h["pressure_bara"], "%.1f" % h["temperature_c"], "%.1f" % h["mass_rate_kg_s"], "%.1f" % h["phases"]["gas_kg_s"], "%.1f" % h["phases"]["oil_kg_s"], "%.1f" % h["phases"]["aqueous_kg_s"],
                    "%.0f" % ds["results"][ref][c]["injection"]["pump_power_kw"]])
    D.table(sh, 228.0, 100.0, [18.0, 20.0, 20.0, 22.0, 20.0, 20.0, 22.0, 24.0], ["Case", "p bara", "T degC", "m kg/s", "gas", "oil", "water", "pump kW"], arr, size=2.2, row_h=4.2, title="Host arrival conditions and injection pump power (kg/s per phase)")
    sh.notes += ["Values are the solved steady NeqSim results of each concept and case; PASS/FAIL follows the specification criteria and a non-converged case is never PASS.",
                 "A criterion that could not be assessed is shown as NOT ASSESSED and blocks ranking of the concept."]


def comparison_sheet(sh, ds, rs, ctx):
    """DWG-14: concept comparison: strip plans, lengths, feasibility and CAPEX of every alternative."""
    ref = ds["reference_concept"]
    sh.scale_text = "strip plans NOT TO SCALE in the transverse direction"
    sh.tb["scale"] = sh.scale_text
    y = 36.0
    for cid, c in ds["concepts"].items():
        lines = D.concept_edges(ds, cid)
        geom = ds["geometry"][cid]
        nodes = D.concept_nodes(ds, cid)
        allp = [(n["x_m"], n["y_m"]) for n in nodes.values()]
        sh.add(K.rect(22.0, y, 384.0, 58.0, fill="#fff", sw=0.4))
        sh.text(25.0, y + 6.0, "%s" % c["label"], 3.0, bold=True)
        D.fit(sh, 25.0, y + 11.0, c["description"], 340.0, 2.5)
        pm = D.PlanMap(D.bbox_of(allp, 200.0), (30.0, y + 24.0, 250.0, y + 50.0))
        for rid, r in lines.items():
            if r["service"] == "production":
                D.polyline(sh, [pm.pt(p[0], p[1]) for p in geom[rid]["points"]], D.COLORS["production"], 1.0)
                sh.items.append({"type": "line", "id": rid, "tag": r["tag"], "service": "production", "length_m": r["installed_length_m"]})
        for n in nodes.values():
            if n["service"] == "production" and n["kind"] in ("manifold", "plem", "host"):
                x, yy = pm.pt(n["x_m"], n["y_m"])
                D.symbol(sh, n["kind"], x, yy, 1.6)
                anchor = {"host": "end", "plem": "start"}.get(n["kind"], "middle")
                sh.text(x + {"end": -3.0, "start": 3.0}.get(anchor, 0.0), yy - 3.6 if n["kind"] != "manifold" else yy + 6.4, n["id"], 2.3, anchor)
                sh.items.append({"type": "asset", "id": n["id"], "kind": n["kind"], "tag": n["tag"]})
        row = next(r for r in ds["comparison"]["rows"] if r["concept"] == cid)
        sel = " (SELECTED)" if ds["selected_concept"] == cid else ""
        prod_km = sum(r["installed_length_m"] for r in lines.values() if r["service"] == "production") / 1000.0
        txt = ["Feasibility: %s%s" % (row["feasibility"].upper(), sel), "SURF CAPEX: %.0f MUSD (%s, %s, class %s screening)" % ((row["capex_usd"] or 0) / 1e6, ds["cost"][cid]["currency"], ds["cost"][cid]["cost_year"], ds["cost"][cid]["estimate_class"]),
               "Production line length: %.1f km" % prod_km, "Failed criteria: %s" % (", ".join(row["failed"][:3]) or "none"), "Open criteria: %s" % (", ".join(row["open"][:3]) or "none")]
        for i, t in enumerate(txt):
            D.fit(sh, 262.0, y + 22.0 + i * 5.6, t, 140.0, 2.6, bold=(i == 0))
        y += 62.0
    sh.notes += ["Feasibility is assessed before cost: a concept is ranked on cost only if every acceptance criterion passes in every case.",
                 "CAPEX is the existing SURFCostEstimator screening estimate; wells and host modifications are outside the scope and cost is not an estimate-class claim."]


def quantities_sheet(sh, ds, rs, ctx):
    """DWG-15: line and equipment registers, MTO and the drawing cross-reference of the reference concept."""
    ref = ds["reference_concept"]
    rows = [[r["id"], r["tag"], r["kind"], r["service"], "%.0f" % r["route_length_m"], "%.0f" % r["installed_length_m"], "%.1f x %.1f" % (r["od_mm"], r["wall_mm"]) if r["wall_mm"] else "ID %.0f" % r["id_mm"],
             "%.0f" % r["id_mm"], r["insulation"]] for r in ds["registers"][ref]["lines"]]
    D.table(sh, 22.0, 32.0, [20.0, 18.0, 18.0, 20.0, 18.0, 20.0, 24.0, 14.0, 40.0], ["Line", "Tag", "Kind", "Service", "Route m", "Installed m", "OD x WT mm", "ID mm", "Insulation"], rows, size=2.2, row_h=4.0,
            title="Line register (reference concept %s)" % ref)
    eq = ds["registers"][ref]["mto"]["equipment_counts"]
    erows = [[k.replace("_", " "), str(v)] for k, v in sorted(eq.items())] + [["templates", str(ds["registers"][ref]["mto"]["structure_count"])]]
    D.table(sh, 22.0, 120.0, [50.0, 20.0], ["Equipment kind", "Count"], erows, size=2.3, row_h=4.0, title="Equipment counts (specification = design basis = cost basis)")
    mto = ds["registers"][ref]["mto"]
    mrows = [["steel pipe (derived)", "%.0f t" % (mto["total_steel_kg"] / 1000.0)], ["insulation (derived)", "%.0f m3" % mto["total_insulation_m3"]], ["umbilical", "%.1f km" % (mto["umbilical_length_m"] / 1000.0)]]
    D.table(sh, 22.0, 170.0, [50.0, 30.0], ["MTO item", "Quantity"], mrows, size=2.3, row_h=4.0, title="Material take-off summary (geometry-derived)")
    drows = [[ds["drawings_plan"][i]["code"], ds["drawings_plan"][i]["title"]] for i in range(len(ds["drawings_plan"]))]
    D.table(sh, 218.0, 32.0, [18.0, 120.0], ["Sheet", "Title"], drows, size=2.2, row_h=3.9, title="Drawing register (this package)")
    rec = ds["reconciliation_summary"]
    sh.text(212.0, 205.0, "Reconciliation of model, register, design basis and cost: %d checks, %d PASS." % (rec["n"], rec["n_pass"]), 2.6, bold=True)
    sh.notes += ["Every quantity is derived from the canonical specification geometry; installed length = route length x (1 + stated allowance), used once.",
                 "The cost basis and the line register agree on counts and lengths (see the validation report); the estimate itself is a Class 4 screening value from the existing estimator."]
