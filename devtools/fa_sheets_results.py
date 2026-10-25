"""Result sheets of the flow-assurance drawing package: profiles, velocity/holdup, hydrate, operating limits, events."""
import math

import fa_draw
from fa_draw import Axes, INK, GREY, RED
from psr_diagrams import kit as K

PX0, PW = 44.0, 292.0  # shared left edge and width of aligned panels (so panels with the same chainage axis line up)
COL_X = 342.0


def _path_info(ds, k):
    route = ds["route"]
    pid = list(route["paths"])[k]
    chain = route["paths"][pid]
    segs = {s["id"]: s for s in route["segments"]}
    length = sum(segs[i]["length_m"] for i in chain)
    return pid, chain, segs, length


def _x_range(length, ds=None):
    """Common chainage range of the study: the longest path (so panels on different sheets and paths share one axis)."""
    if ds is not None:
        length = max(sum(s["length_m"] for s in ds["route"]["segments"] if s["id"] in chain) for chain in ds["route"]["paths"].values())
    ticks, step = fa_draw.nice_ticks(0.0, length, 6)
    return (0.0, max(ticks[-1], length))


def _profile_cases(ds, pid, roles=("design", "scenario")):
    return [c for c in ds["cases"] if c["role"] in roles and pid in ds["results"][c["id"]]["profiles"]]


def _y_range(values, pad=0.06, floor=None, ceil=None, head=0.16):
    """Axis range with ``head`` extra headroom above the data so the panel title never sits on a curve."""
    vals = [v for v in values if v is not None]
    lo, hi = min(vals), max(vals)
    span = (hi - lo) or 1.0
    lo, hi = lo - pad * span, hi + pad * span
    if floor is not None:
        lo = max(lo, floor)
    if ceil is not None:
        hi = min(hi, ceil)
    hi += head * ((hi - lo) or 1.0)
    return (lo, hi)


def _case_legend(sh, cases, styles):
    for c in cases:
        col, pat = styles[c["id"]]
        sh.legend.append(("line", col, pat, "%s - %s" % (c["id"], c["label"])))


def _plot_cases(ax, ds, cases, pid, key, styles, source="profiles"):
    for c in cases:
        res = ds["results"][c["id"]]
        prof = res["profiles"][pid]
        ys = prof[key] if source == "profiles" else res["profiles_derived"][pid][key]
        col, pat = styles[c["id"]]
        ax.series(prof["chainage_m"], ys, col, pat, c["id"], meta={"case": c["id"], "path": pid, "quantity": key, "source": source})


def profile_sheet(sh, ds, rs, ctx, k):
    """Sheet C: elevation profile with segment boundaries, burial, low/high points and aligned pressure/temperature plots."""
    pid, chain, segs, length = _path_info(ds, k)
    nodes = ds["route"]["nodes"]
    xr = _x_range(length, ds)
    styles = ctx["styles"]
    cases = _profile_cases(ds, pid)
    # elevation points
    pts = [(0.0, nodes[segs[chain[0]]["from"]]["elev_m"], segs[chain[0]]["from"])]
    for sid in chain:
        pts.append((segs[sid]["path_chainage_m"][pid]["end"], nodes[segs[sid]["to"]]["elev_m"], segs[sid]["to"]))
    ymin, ymax = min(p[1] for p in pts), max(p[1] for p in pts)
    has_burial = any(segs[sid].get("burial") for sid in chain)
    lo_pad = 0.12 + (0.32 if has_burial else 0.0)
    el = Axes(sh, PX0, 26.0, PW, 62.0, xr, (ymin - lo_pad * (ymax - ymin), ymax + 0.18 * (ymax - ymin)), "E%d" % (k + 1), "Seabed and pipe centreline elevation - path %s" % pid,
              None, "elevation (m, MSL)", show_xticks=False)
    el.series([p[0] for p in pts], [p[1] for p in pts], INK, "solid", "seabed / pipe centreline", sw=0.9, meta={"path": pid, "quantity": "elevation_m", "source": "route"})
    # segment boundaries, labels (two rows), burial band, riser transition
    for i, sid in enumerate(chain):
        c0, c1 = segs[sid]["path_chainage_m"][pid]["start"], segs[sid]["path_chainage_m"][pid]["end"]
        el.vline(c1) if i < len(chain) - 1 else None
        lw = K.text_w(sid, 2.5, segs[sid]["kind"] == "riser")
        lx = min(max(el.px((c0 + c1) / 2.0), PX0 + lw / 2.0 + 0.5), PX0 + PW - lw / 2.0 - 0.5)
        sh.text(lx, el.y + 9.0 + (i % 2) * 3.6, sid, 2.5, "middle", bold=(segs[sid]["kind"] == "riser"), layer="label")
        if segs[sid].get("burial"):
            ya, yb = (nodes[segs[sid]["from"]]["elev_m"], nodes[segs[sid]["to"]]["elev_m"])
            xa, xb = el.px(c0), el.px(c1)
            sh.add(K.polygon([(xa, el.py(ya)), (xb, el.py(yb)), (xb, el.py(yb) + 3.5), (xa, el.py(ya) + 3.5)], fill="#ece3d0", stroke=GREY, sw=0.2))
            sh.text((xa + xb) / 2.0, max(el.py(ya), el.py(yb)) + 8.0, "trenched", 2.5, "middle", fill=GREY)
            sh.bound_text(rs, (xa + xb) / 2.0, max(el.py(ya), el.py(yb)) + 11.3, "seg:%s:burial.depth_m" % sid, ".1f", "", " m to axis", 2.5, "middle", unit="m", fill=GREY)
        if segs[sid]["kind"] == "riser":
            sh.text(el.px(c0) - 2.0, el.y + 20.0, "riser base %s" % segs[sid]["from"], 2.5, "end")
    # low and high points (interior)
    for j in range(1, len(pts) - 1):
        a, b, c = pts[j - 1][1], pts[j][1], pts[j + 1][1]
        if (b < a and b < c) or (b > a and b > c):
            kind = "low" if b < a else "high"
            el.marker(pts[j][0], b, "diamond", True, 1.2)
            label_w = K.text_w("%s %.0f m" % (kind, b), 2.5, False)
            right_edge = PX0 + PW - 1.0
            near_right = el.px(pts[j][0]) + 1.8 + label_w > right_edge
            sh.bound_text(rs, el.px(pts[j][0]) + (-1.8 if near_right else 1.8), el.py(b) + (4.2 if kind == "low" else -2.4), "basis:route.nodes.%s.elev_m" % pts[j][2], ".0f", "%s " % kind, " m", 2.5,
                          "end" if near_right else "start")
            sh.items.append({"type": "extremum_point", "node": pts[j][2], "kind": kind, "chainage_m": pts[j][0], "elev_m": pts[j][1]})
    hz = (xr[1] - xr[0]) / PW  # m per mm horizontally
    vz = (el.yr[1] - el.yr[0]) / el.h
    ve = hz / vz
    sh.items.append({"type": "scale_info", "path": pid, "horizontal_m_per_mm": hz, "vertical_m_per_mm": vz, "vertical_exaggeration": ve})
    sh.text(PX0 + PW - 1.5, el.y + 3.6, "H 1 mm = %.0f m | V 1 mm = %.1f m | vertical exaggeration %.1f x" % (hz, vz, ve), 2.5, "end", fill=GREY)
    # aligned pressure and temperature
    allp = [v for c in cases for v in ds["results"][c["id"]]["profiles"][pid]["p_bara"]]
    allt = [v for c in cases for v in ds["results"][c["id"]]["profiles"][pid]["t_c"]]
    pa = Axes(sh, PX0, 102.0, PW, 52.0, xr, _y_range(allp), "P%d" % (k + 1), "Pressure along the route (steady state)", None, "pressure (bara)", show_xticks=False)
    _plot_cases(pa, ds, cases, pid, "p_bara", styles)
    ta = Axes(sh, PX0, 166.0, PW, 48.0, xr, _y_range(allt), "T%d" % (k + 1), "Temperature along the route (steady state)", "chainage along path from the template outlet (m)", "temperature (degC)")
    _plot_cases(ta, ds, cases, pid, "t_c", styles)
    # critical annotations: arrival values from the dataset
    host = ds["route"]["sink"]
    for c in cases:
        col, pat = styles[c["id"]]
    sh.legend = []
    _case_legend(sh, cases, styles)
    sh.legend.append(("marker", "diamond", True, "Interior low / high point of the seabed profile"))
    sh.notes = ["Path %s (%s). Chainage is measured (as-laid) route length from the template outlet, along the flow direction; horizontal projection and elevation are separate quantities." % (pid, "source to host interface"),
                "Pressure and temperature are steady-state Beggs and Brill results (%s); the three panels share the same chainage axis." % ds["meta"]["model"]["name"],
                "Elevation is the seabed/pipe centreline at the route nodes joined by straight lines; elevations between nodes are not supplied. Burial is shown where the route file states it.",
                "Cases plotted: design and scenario cases whose line-up includes this path; mitigation cases are on sheets %d-%d." % (ctx["codes"]["DWG-07"], ctx["codes"]["DWG-08"]),
                "The host-interface pressure is the specified boundary; source pressures are calculated (backward solve)."]
    sh.scale_text = "H 1:%d V 1:%d" % (int(round(hz * 1000.0 / 100.0) * 100), int(round(vz * 1000.0 / 10.0) * 10))
    sh.tb["scale"] = sh.scale_text


def _critical_column(sh, rs, ds, ctx, title, entries):
    """Right-hand text column listing governing cases and locations; ``entries`` = list of (bold heading, [(token-bound text parts)])."""
    sh.add(K.rect(COL_X - 3.0, 34.0, 408.0 - COL_X + 1.0, 180.0, fill="none", sw=0.3))
    y = 39.5
    sh.text(COL_X, y, title, 2.9, "start", bold=True)
    y += 5.0
    for head, lines in entries:
        sh.text(COL_X, y, head, 2.6, "start", bold=True)
        y += 3.6
        for ln in lines:
            for t in K.wrap(ln, 2.5, 408.0 - COL_X - 3.0, max_lines=3):
                sh.text(COL_X + 1.0, y, t[0], 2.5, "start")
                y += 3.1
        y += 1.8


def _loc_line(rs, case, ext):
    """Text for a governing extremum: case, value, chainage and segment taken from the dataset through tokens."""
    v = rs.text("ext:%s:%s:value" % (case, ext), ".1f")
    ch = rs.text("ext:%s:%s:chainage_m" % (case, ext), ".1f", "unit=km")
    seg = rs.value("ext:%s:%s:segment_id" % (case, ext))[0]
    return v, ch, seg


def velocity_holdup_sheet(sh, ds, rs, ctx):
    """Sheet F1: mixture velocity, erosional ratio and holdup along the first path with the project limits."""
    pid, chain, segs, length = _path_info(ds, 0)
    xr = _x_range(length, ds)
    styles = ctx["styles"]
    cases = _profile_cases(ds, pid, roles=("design", "scenario", "mitigation"))
    pw = 288.0
    allv = [v for c in cases for v in ds["results"][c["id"]]["profiles"][pid]["v_mix_m_s"]]
    allr = [v for c in cases for v in ds["results"][c["id"]]["profiles_derived"][pid]["erosional_ratio"] if v is not None]
    allh = [v for c in cases for v in ds["results"][c["id"]]["profiles"][pid]["holdup"]]
    c_v = next(c for c in ds["criteria"] if c["quantity"] == "mixture_velocity_max")
    c_e = next(c for c in ds["criteria"] if c["quantity"] == "erosional_ratio_max")
    a1 = Axes(sh, PX0, 36.0, pw, 50.0, xr, (0.0, max(max(allv) * 1.1, c_v["value"] * 1.1)), "V1", "Mixture velocity (vsg + vsl)", None, "velocity (m/s)", show_xticks=False)
    _plot_cases(a1, ds, cases, pid, "v_mix_m_s", styles)
    a1.hline(c_v["value"], "%s limit %s m/s" % (c_v["id"], rs.text("crit:%s:value" % c_v["id"], ".0f")), "#444", "dash")
    a2 = Axes(sh, PX0, 100.0, pw, 50.0, xr, (0.0, max(max(allr) * 1.15, c_e["value"] * 1.15)), "V2", "Mixture-to-erosional velocity ratio", None, "ratio (-)", show_xticks=False)
    _plot_cases(a2, ds, cases, pid, "erosional_ratio", styles, source="profiles_derived")
    a2.hline(c_e["value"], "%s limit %s" % (c_e["id"], rs.text("crit:%s:value" % c_e["id"], ".1f")), "#444", "dash")
    a3 = Axes(sh, PX0, 164.0, pw, 50.0, xr, (0.0, min(1.0, max(allh) * 1.15)), "V3", "Steady liquid holdup (not a slug volume)", "chainage along path from the template outlet (m)", "liquid holdup (-)")
    _plot_cases(a3, ds, cases, pid, "holdup", styles)
    for ax in (a1, a2, a3):
        pass
    entries = []
    for c in cases:
        v, ch, seg = _loc_line(rs, c["id"], "v_mix_max")
        r, chr_, segr = _loc_line(rs, c["id"], "erosional_ratio_max")
        h, chh, segh = _loc_line(rs, c["id"], "holdup_max")
        entries.append((c["id"], ["v_mix max %s m/s at KP %s, %s" % (v, ch, seg), "ratio max %s at KP %s, %s" % (r, chr_, segr), "holdup max %s at KP %s, %s" % (h, chh, segh)]))
        sh.items.append({"type": "critical_section", "case": c["id"], "extrema": ["v_mix_max", "erosional_ratio_max", "holdup_max"]})
    _critical_column(sh, rs, ds, ctx, "Critical sections (any path)", entries)
    sh.legend = []
    _case_legend(sh, cases, styles)
    sh.legend.append(("line", "#444", "dash", "Project limit (sourced criterion)"))
    sh.notes = ["Path %s shown; downstream of the PLEM the other path is identical, branch values of the other path are in the dataset." % pid,
                "Velocity limits are project criteria (basis, acceptance_criteria); the erosional velocity uses the project C-factor and is not a DNV-RP-O501 assessment.",
                "Holdup is the steady Beggs and Brill value; it must not be read as transient slug volume or as a liquid-handling capacity check.",
                "Critical-section column lists the maximum over all active paths of each case (KP = chainage in km along the stated path)."]
    sh.scale_text = "chainage axis aligned with sheets %d-%d" % (ctx["codes"]["DWG-03"], ctx["codes"].get("DWG-04", ctx["codes"]["DWG-03"]))
    sh.tb["scale"] = "NOT TO SCALE"


def hydrate_sheet(sh, ds, rs, ctx):
    """Sheet F2: hydrate margin of every case along both paths, the temperature against the hydrate curves, and critical sections."""
    styles = ctx["styles"]
    crit = next(c for c in ds["criteria"] if c["quantity"] == "hydrate_margin_min")
    lim = float(crit["value"])
    paths = list(ds["route"]["paths"])
    heights = {1: 50.0, 2: 38.0}
    n = len(paths)
    h = heights.get(n, 30.0)
    y = 36.0
    pw = 288.0
    govern = None
    for case in ds["cases"]:
        ex = ds["results"][case["id"]]["extrema"]["hyd_margin_min"]
        if ex["value"] is not None and case["role"] in ("design", "scenario") and (govern is None or ex["value"] < govern[1]):
            govern = (case["id"], ex["value"], ex["path"])
    for k, pid in enumerate(paths):
        _p, chain, segs, length = _path_info(ds, k)
        xr = _x_range(length, ds)
        cases = _profile_cases(ds, pid, roles=("design", "scenario", "mitigation"))
        vals = [v for c in cases for v in ds["results"][c["id"]]["profiles_derived"][pid]["margin_k"] if v is not None]
        clip_top = lim + 30.0
        rng = _y_range(vals + [lim], ceil=clip_top)
        ax = Axes(sh, PX0, y, pw, h, xr, rng, "H%d" % (k + 1), "Hydrate margin (T - T_hyd, inhibited) - path %s (curves clipped at %.0f K)" % (pid, min(clip_top, rng[1])),
                  None, "margin (K)", show_xticks=False)
        ax.clip_hi = clip_top
        ax.hatch_below(lim)
        _plot_cases(ax, ds, cases, pid, "margin_k", styles, source="profiles_derived")
        ax.hline(lim, "%s criterion %s K" % (crit["id"], rs.text("crit:%s:value" % crit["id"], ".0f")), "#444", "dash", dy=-0.9)
        for c in cases:
            ex = ds["results"][c["id"]]["extrema"]["hyd_margin_min"]
            if ex["path"] == pid and ex["value"] is not None:
                ax.marker(ex["chainage_m"], ex["value"], "circle", crit["by_case"][c["id"]]["status"] == "PASS", 1.3)
        y += h + 14.0
    # temperature against the hydrate curves, governing case first path
    gcase, _gv, gpath = govern if govern else (ds["cases"][0]["id"], None, paths[0])
    k = paths.index(gpath) if gpath in paths else 0
    pid, chain, segs, length = _path_info(ds, k)
    xr = _x_range(length, ds)
    res = ds["results"][gcase]
    prof, der = res["profiles"][pid], res["profiles_derived"][pid]
    vals = [v for v in prof["t_c"] + der["t_hyd_c"] + der["t_hyd_unin_c"] if v is not None]
    bottom_h = 214.0 - y - 8.0
    ax = Axes(sh, PX0, y, pw, bottom_h, xr, _y_range(vals), "H3", "Temperature against hydrate equilibrium - case %s, path %s" % (gcase, pid),
              "chainage along path from the template outlet (m)", "temperature (degC)")
    ax.series(prof["chainage_m"], prof["t_c"], INK, "solid", "fluid temperature", sw=0.8, meta={"case": gcase, "path": pid, "quantity": "t_c", "source": "profiles"})
    ax.series(prof["chainage_m"], der["t_hyd_c"], "#b03a2e", "dash", "hydrate T, inhibited", meta={"case": gcase, "path": pid, "quantity": "t_hyd_c", "source": "profiles_derived"})
    ax.series(prof["chainage_m"], der["t_hyd_unin_c"], "#7d3c98", "dot", "hydrate T, no inhibitor", meta={"case": gcase, "path": pid, "quantity": "t_hyd_unin_c", "source": "profiles_derived"})
    xs_ = prof["chainage_m"]

    def _inline(series, frac, text, dy, anchor="start"):
        i = int(frac * (len(xs_) - 1))
        if series[i] is not None:
            sh.text(ax.px(xs_[i]) + (1.5 if anchor == "start" else -1.5), ax.py(series[i]) + dy, text, 2.5, anchor, layer="label")

    _inline(prof["t_c"], 0.10, "fluid temperature", -2.0)
    _inline(der["t_hyd_unin_c"], 0.02, "hydrate T, no inhibitor (reference)", -1.6)
    _inline(der["t_hyd_c"], 0.02, "hydrate T, inhibited", 4.2)
    entries = []
    for c in ds["cases"]:
        if c["id"] not in crit["by_case"] or crit["by_case"][c["id"]]["status"] == "NOT APPLICABLE":
            continue
        v, ch, seg = _loc_line(rs, c["id"], "hyd_margin_min")
        st = rs.text("crit:%s:%s:status" % (crit["id"], c["id"]))
        entries.append((c["id"], ["min margin %s K at KP %s, %s" % (v, ch, seg), "%s %s (limit %s K)" % (crit["id"], st, rs.text("crit:%s:value" % crit["id"], ".0f"))]))
        sh.items.append({"type": "critical_section", "case": c["id"], "extrema": ["hyd_margin_min"], "criterion": crit["id"]})
    _critical_column(sh, rs, ds, ctx, "Critical sections and criterion", entries)
    sh.legend = []
    _case_legend(sh, [c for c in ds["cases"] if c["role"] in ("design", "scenario", "mitigation")], styles)
    sh.legend += [("line", "#444", "dash", "Project hydrate-margin criterion (hatched = below)"), ("marker", "circle", True, "Minimum margin, criterion PASS (filled)"),
                  ("marker", "circle", False, "Minimum margin, criterion FAIL (hollow)")]
    sh.notes = ["Margin = fluid temperature minus the CPA hydrate equilibrium temperature at the local pressure and the aqueous inhibitor mass fraction of the local stream (equilibrium, not plugging time).",
                "The inhibitor mass fraction follows the injection rate balance; losses to gas and condensate are not modelled (upper bound on the concentration).",
                "The %g K margin is a project criterion (source: %s), not a code value. The hatched band is the region below the criterion." % (lim, crit.get("source", "see basis")),
                "Bottom panel: governing case %s; line styles are labelled on the panel and do not follow the case colours of the legend; the uninhibited curve is a reference, not an operating mode." % gcase,
                "Hydrate temperatures are interpolated in ln(p) on a pressure grid; the interpolation check is reported in the validation appendix."]
    sh.scale_text = "NOT TO SCALE"
    sh.tb["scale"] = "NOT TO SCALE"


def limits_sheet(sh, ds, rs, ctx):
    """Sheet F3: acceptance criteria by case (value, status word and marker) and the threat applicability list."""
    cases = ds["cases"]
    crits = ds["criteria"]
    x0, y0 = 14.0, 30.0
    cw0, cw = 78.0, (408.0 - x0 - 78.0) / max(len(cases), 1)
    rh = 13.0
    sh.add(K.rect(x0, y0, cw0 + cw * len(cases), 12.0 + rh * len(crits), fill="none", sw=0.4))
    sh.text(x0 + 2.0, y0 + 7.5, "Criterion (limit)", 2.8, "start", bold=True)
    for j, c in enumerate(cases):
        xc = x0 + cw0 + j * cw
        sh.add(K.line(xc, y0, xc, y0 + 12.0 + rh * len(crits), sw=0.25))
        sh.text(xc + cw / 2.0, y0 + 5.0, c["id"], 2.6, "middle", bold=True)
        sh.text(xc + cw / 2.0, y0 + 9.2, c["role"], 2.5, "middle", fill=GREY)
    sh.add(K.line(x0, y0 + 12.0, x0 + cw0 + cw * len(cases), y0 + 12.0, sw=0.4))
    for i, cr in enumerate(crits):
        yy = y0 + 12.0 + i * rh
        sh.add(K.line(x0, yy + rh, x0 + cw0 + cw * len(cases), yy + rh, sw=0.2))
        sh.text(x0 + 2.0, yy + 5.0, cr["id"], 2.8, "start", bold=True)
        sh.text(x0 + 2.0, yy + 8.6, ("%s %s %s %s" % (cr["quantity"].replace("_", " "), cr["operator"], rs.text("crit:%s:value" % cr["id"], "g"), cr["unit"])).replace(" -", "").strip(), 2.5, "start", fill=GREY)
        sh.text(x0 + 2.0, yy + 11.8, "overall: %s" % rs.text("crit:%s:overall" % cr["id"]), 2.5, "start", bold=True)
        for j, c in enumerate(cases):
            xc = x0 + cw0 + j * cw
            by = cr["by_case"].get(c["id"], {"status": "NOT APPLICABLE"})
            st = by["status"]
            if st in ("PASS", "FAIL"):
                sh.text(xc + cw / 2.0, yy + 5.0, rs.text("crit:%s:%s:value" % (cr["id"], c["id"]), ".1f"), 2.8, "middle")
                sh.add(fa_draw.marker_svg("circle" if st == "PASS" else "triangle", xc + 5.0, yy + 9.4, 1.4, st == "PASS"))
                sh.text(xc + 8.5, yy + 10.3, rs.text("crit:%s:%s:status" % (cr["id"], c["id"])), 2.6, "start", bold=True)
            else:
                sh.text(xc + cw / 2.0, yy + 8.6, st.lower(), 2.5, "middle", fill=GREY)
        sh.items.append({"type": "criterion_row", "id": cr["id"]})
    ty = y0 + 12.0 + rh * len(crits) + 12.0
    sh.text(x0, ty, "Threat applicability matrix (status of every threat; an open or unsupported threat is never a safe result)", 3.0, "start", bold=True)
    thr = ds["threats"]
    colw = (408.0 - x0) / 2.0
    for i, t in enumerate(thr):
        col, row = divmod(i, int(math.ceil(len(thr) / 2.0)))
        x = x0 + col * colw
        yy = ty + 6.0 + row * 7.2
        sh.add(fa_draw.marker_svg({"calculated": "circle", "not_applicable": "square", "open": "triangle", "unsupported": "cross"}[t["status"]], x + 2.0, yy - 0.9, 1.3,
                                  t["status"] == "calculated"))
        sh.text(x + 6.0, yy, "%s  %s" % (t["id"], t["status"].upper().replace("_", " ")), 2.6, "start", bold=True)
        sh.text(x + 62.0, yy, K.wrap(t["threat"], 2.5, colw - 66.0, max_lines=1)[0][0], 2.5, "start")
        sh.items.append({"type": "threat_row", "id": t["id"], "status": t["status"]})
    sh.legend = [("marker", "circle", True, "PASS / calculated"), ("marker", "triangle", False, "FAIL / open"), ("marker", "square", False, "Not applicable (reason stated)"),
                 ("marker", "cross", False, "Unsupported (external hand-off)"), ("text", "Status is always also written as a word")]
    sh.notes = ["Values are the governing (worst) values over the whole route for each case; locations are on sheets %d and %d." % (ctx["codes"]["DWG-07"], ctx["codes"]["DWG-08"]),
                "Criteria are project inputs with a stated source (basis); none is presented as a code requirement. A criterion that cannot be assessed is shown as unavailable.",
                "Threats that are open or unsupported have no result and are not shown as passed; see the report for reasons and hand-offs.",
                "Cases: role design, scenario or mitigation as stated in the case matrix."]
    sh.scale_text = "NOT TO SCALE"
    sh.tb["scale"] = "NOT TO SCALE"


def event_sheet(sh, ds, rs, ctx):
    """Sheet G: lumped cooldown time histories at the governing location, event register and qualification statement."""
    styles = ctx["styles"]
    cases = [c for c in ds["cases"] if ds["results"][c["id"]].get("cooldown") and ds["results"][c["id"]]["cooldown"]["histories"]]
    duration = max((ds["results"][c["id"]]["cooldown"]["duration_h"] for c in cases), default=48.0)
    tmax = min(duration, 36.0)
    allt = [v for c in cases for v in ds["results"][c["id"]]["cooldown"]["histories"][0]["t_c"]]
    ax = Axes(sh, 36.0, 40.0, 168.0, 150.0, (0.0, tmax), _y_range(allt, floor=0.0), "G1", "Cooldown after shut-in at the governing location of each case",
              "time after shut-in (h); t = 0 at closure, initial state = steady operation", "temperature (degC)")
    for c in cases:
        hist = ds["results"][c["id"]]["cooldown"]["histories"][0]
        keep = [i for i, t in enumerate(hist["t_h"]) if t <= tmax]
        col, pat = styles[c["id"]]
        ax.series([hist["t_h"][i] for i in keep], [hist["t_c"][i] for i in keep], col, pat, c["id"], meta={"case": c["id"], "path": hist["path"], "quantity": "cooldown_t_c", "source": "cooldown"})
        tcrit = hist["t_crit_c"]
        if tcrit is not None:
            ax.hline(tcrit, "", col, "dot", sw=0.25)
        if hist["time_to_criterion_h"] is not None and 0 < hist["time_to_criterion_h"] <= tmax:
            ax.marker(hist["time_to_criterion_h"], tcrit, "circle", False, 1.4)
    ax.spec["x_quantity"] = "time_h"
    sh.legend = []
    _case_legend(sh, cases, styles)
    sh.legend += [("marker", "circle", False, "Time to the hydrate equilibrium temperature (dotted line of the same style)")]
    # event register
    x0, y = 214.0, 40.0
    sh.add(K.rect(x0 - 2.0, 34.0, 408.0 - x0 + 3.0, 186.0, fill="none", sw=0.3))
    sh.text(x0, y, "Event register (time origin: t = 0 at the stated event)", 2.9, "start", bold=True)
    y += 5.5
    for ev in ds["events"]:
        status = {"calculated": "CALCULATED (screening, lumped cooldown)", "external_open": "EXTERNAL HAND-OFF - NOT SIMULATED - RESULT OPEN", "open": "OPEN"}[ev["status"]]
        sh.text(x0, y, "%s | case %s | %s" % (ev["id"], ev["case"], ev["type"]), 2.6, "start", bold=True)
        y += 3.4
        sh.text(x0 + 1.0, y, status, 2.5, "start", bold=True, fill=(RED if ev["status"] != "calculated" else INK))
        y += 3.2
        if ev["status"] == "calculated" and ev["result"]["governing_time_h"] is not None:
            gt = ev["result"]["governing_time_h"]
            sh.text(x0 + 1.0, y, "governing time to the hydrate temperature: %.1f h at KP %.1f km" % (gt, ev["result"]["chainage_m"] / 1000.0), 2.5, "start")
            sh.items.append({"type": "event_result", "event": ev["id"], "time_h": gt, "chainage_m": ev["result"]["chainage_m"]})
            y += 3.2
        for t in K.wrap(ev["description"], 2.5, 408.0 - x0 - 4.0, max_lines=2):
            sh.text(x0 + 1.0, y, t[0], 2.5, "start", fill=GREY)
            y += 3.0
        if ev["status"] == "external_open":
            sh.text(x0 + 1.0, y, "hand-off package: handoff/%s.json" % ev["id"], 2.5, "start")
            y += 3.0
        y += 2.0
    y += 1.0
    sh.text(x0, y, "Qualification basis of the calculated events", 2.7, "start", bold=True)
    y += 3.6
    for ln in ["Lumped exponential cooldown (fluid + steel). Numerically verified against RK4 and the closed form;",
               "NOT experimentally qualified. Result = time to the equilibrium temperature, not time to plug.",
               "No simulated slug, pig or restart output is shown: those analyses are open (external simulator)."]:
        sh.text(x0 + 1.0, y, ln, 2.5, "start")
        y += 3.1
    sh.notes = ["Initial condition is the steady operating temperature profile of the case; ambient is the segment ambient of the route file.",
                "Settle-out pressure and the retained inhibitor mass fraction determine the criterion temperature of each case (basis: shut_in, assumption A-05).",
                "Event chronology: shut-in events start at t = 0; external events (restart, pigging) carry their own time origin in the hand-off package.",
                "A restart, pigging or slug-receiving result requires a separately qualified simulator; none is simulated or implied here."]
    sh.scale_text = "NOT TO SCALE"
    sh.tb["scale"] = "NOT TO SCALE"
