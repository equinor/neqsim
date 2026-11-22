"""Route geometry, layered thermal properties and topology variants for the flow-assurance study report.

The route file describes assets (nodes) and segments (pipes). Everything unknown stays unknown: a missing coordinate is
``None`` and is never replaced by a default. Horizontal chainage, measured route length and elevation are separate
quantities (see :func:`check_route`).
"""
import copy
import math

from fa_units import GeometryError, TopologyError

NODE_KINDS = ("well", "tree", "template", "manifold", "plem", "junction", "riser_base", "host_interface", "injection_point",
              "valve", "pig_launcher", "pig_receiver", "tie_in", "waypoint")
SEGMENT_KINDS = ("jumper", "flowline", "riser", "spool", "pipeline", "gathering_line")
LENGTH_TOL_REL = 0.01
LENGTH_WARN_REL = 0.10
ROUTE_STATUSES = ("surveyed", "design", "synthetic", "schematic")


def _num(value, name, positive=False, allow_none=False):
    if value is None and allow_none:
        return None
    try:
        v = float(value)
    except (TypeError, ValueError):
        raise GeometryError("%s is not a number: %r" % (name, value))
    if not math.isfinite(v) or (positive and v <= 0.0):
        raise GeometryError("%s must be finite%s, got %r" % (name, " and positive" if positive else "", value))
    return v


def layer_radii(seg):
    """Radii (m) of the pipe: ``[r_i, r_after_layer_1, ...]``; layer 1 is the steel wall."""
    r = [0.5 * _num(seg.get("id_m"), "%s.id_m" % seg.get("id"), positive=True)]
    for layer in seg.get("layers") or []:
        r.append(r[-1] + _num(layer.get("thickness_m"), "%s layer %s thickness" % (seg.get("id"), layer.get("name")), positive=True))
    return r


def soil_resistance(z_m, r_o, k_soil):
    """Thermal resistance per metre of a buried pipe (m.K/W): shape-factor form for burial depth ``z_m`` (axis to surface)."""
    if z_m <= r_o:
        raise GeometryError("burial depth to pipe axis %.3f m must exceed the outer radius %.3f m" % (z_m, r_o))
    x = 2.0 * z_m / r_o
    return math.log(x + math.sqrt(x * x - 1.0)) / (2.0 * math.pi * k_soil)


def thermal_properties(seg):
    """Layered overall heat-transfer coefficients of a segment.

    Resistances per metre: conduction through each layer ``ln(r2/r1) / (2 pi k)``, plus an external film
    ``1 / (2 pi r_o h_ext)`` for exposed segments or the soil resistance for buried ones. The internal film is
    neglected (conservative for arrival temperature and cooldown: it makes U larger). The result is referenced to
    BOTH the inner and the outer surface; ``PipeBeggsAndBrills`` ``SPECIFIED_U`` uses the INNER area
    (pi * ID * L), so ``u_inner_w_m2k`` is the value to hand to the solver.

    Returns:
        dict with ``od_m``, ``r_total_m_k_w``, ``u_inner_w_m2k``, ``u_outer_w_m2k`` and ``resistances`` (list of
        ``{name, r_m_k_w}``) plus ``steel_mass_kg_m`` / ``steel_cp_j_kgk``.
    """
    radii = layer_radii(seg)
    layers = seg.get("layers") or []
    if not layers:
        raise GeometryError("segment %s has no wall/insulation layers" % seg.get("id"))
    res = []
    for k, layer in enumerate(layers):
        kk = _num(layer.get("k_w_mk"), "%s layer %s k" % (seg["id"], layer.get("name")), positive=True)
        res.append({"name": layer.get("name") or "layer %d" % (k + 1), "r_m_k_w": math.log(radii[k + 1] / radii[k]) / (2.0 * math.pi * kk)})
    r_o = radii[-1]
    burial = seg.get("burial")
    if burial and burial.get("depth_m") is not None:
        z = _num(burial["depth_m"], "%s burial depth" % seg["id"], positive=True)
        ks = _num(burial.get("k_soil_w_mk"), "%s soil conductivity" % seg["id"], positive=True)
        res.append({"name": "soil (buried, shape factor)", "r_m_k_w": soil_resistance(z, r_o, ks)})
    else:
        h = _num((seg.get("ambient") or {}).get("external_h_w_m2k"), "%s external film coefficient" % seg["id"], positive=True)
        res.append({"name": "external film", "r_m_k_w": 1.0 / (2.0 * math.pi * r_o * h)})
    r_total = sum(r["r_m_k_w"] for r in res)
    steel = layers[0]
    steel_area = math.pi * (radii[1] ** 2 - radii[0] ** 2)
    return {"od_m": 2.0 * r_o, "r_total_m_k_w": r_total, "u_inner_w_m2k": 1.0 / (2.0 * math.pi * radii[0] * r_total),
            "u_outer_w_m2k": 1.0 / (2.0 * math.pi * r_o * r_total), "resistances": res,
            "steel_mass_kg_m": steel_area * _num(steel.get("rho_kg_m3"), "%s steel density" % seg["id"], positive=True),
            "steel_cp_j_kgk": _num(steel.get("cp_j_kgk"), "%s steel cp" % seg["id"], positive=True),
            "inner_film_neglected": True}


def thermal_key(seg):
    """Hashable description of the thermal build-up used to detect insulation transitions between segments."""
    layers = tuple((l.get("name"), l.get("thickness_m"), l.get("k_w_mk")) for l in seg.get("layers") or [])
    b = seg.get("burial") or {}
    return (round(float(seg["id_m"]), 5), layers, b.get("depth_m"), (seg.get("ambient") or {}).get("external_h_w_m2k"))


def horizontal_length(seg, nodes):
    """Horizontal length (m) from node coordinates and optional ``plan_points``; ``None`` when coordinates are unknown."""
    a, b = nodes[seg["from"]], nodes[seg["to"]]
    for n in (a, b):
        if n.get("x_m") is None or n.get("y_m") is None:
            return None
    pts = [(a["x_m"], a["y_m"])] + [tuple(p) for p in seg.get("plan_points") or []] + [(b["x_m"], b["y_m"])]
    return sum(math.hypot(x2 - x1, y2 - y1) for (x1, y1), (x2, y2) in zip(pts[:-1], pts[1:]))


def check_route(route):
    """Validate a route dict and return ``(findings, model)``.

    ``findings`` is a list of ``{level, code, message, where}`` dicts (error/warning/info). A fatal structural problem
    raises :class:`GeometryError` (duplicate IDs, unknown end nodes, cycles, non-numeric values, no sink).
    The model has ``nodes``, ``segments`` (with derived geometry/thermal values), ``paths``, ``sources``, ``sink``,
    ``transitions`` and ``findings``.
    """
    findings = []

    def add(level, code, message, where=""):
        findings.append({"level": level, "code": code, "message": message, "where": where})

    if not isinstance(route, dict) or not route.get("segments") or not route.get("assets"):
        raise GeometryError("route needs non-empty 'assets' and 'segments'")
    if route.get("route_status") not in ROUTE_STATUSES:
        add("error", "ROUTE-STATUS", "route_status must be one of %s" % (ROUTE_STATUSES,), "route_status")
    nodes = {}
    for a in route["assets"]:
        aid = a.get("id")
        if not aid:
            raise GeometryError("an asset has no id")
        if aid in nodes:
            raise GeometryError("duplicate asset id '%s'" % aid)
        if a.get("kind") not in NODE_KINDS:
            add("error", "ASSET-KIND", "asset %s: kind '%s' is not one of %s" % (aid, a.get("kind"), NODE_KINDS), aid)
        nodes[aid] = dict(a)
        for key in ("elev_m", "x_m", "y_m"):
            if nodes[aid].get(key) is not None:
                nodes[aid][key] = _num(nodes[aid][key], "%s.%s" % (aid, key))
    segs = []
    seen = set()
    for s in route["segments"]:
        sid = s.get("id")
        if not sid:
            raise GeometryError("a segment has no id")
        if sid in seen or sid in nodes:
            raise GeometryError("duplicate id '%s' (segment ids must be unique and different from asset ids)" % sid)
        seen.add(sid)
        for end in ("from", "to"):
            if s.get(end) not in nodes:
                raise GeometryError("segment %s: %s node '%s' is not an asset" % (sid, end, s.get(end)))
        if s.get("kind") not in SEGMENT_KINDS:
            add("error", "SEG-KIND", "segment %s: kind '%s' is not one of %s" % (sid, s.get("kind"), SEGMENT_KINDS), sid)
        seg = copy.deepcopy(s)
        seg["hydraulic"] = bool(s.get("hydraulic", True))
        seg["length_m"] = _num(s.get("length_m"), "%s.length_m" % sid, positive=True)
        segs.append(seg)
    out = {}
    for s in segs:
        if not s["hydraulic"]:
            continue
        if s["from"] in out:
            raise GeometryError("node %s has more than one outgoing hydraulic segment (%s, %s); a splitting network is not supported"
                                % (s["from"], out[s["from"]]["id"], s["id"]))
        out[s["from"]] = s
    for s in segs:
        if not s["hydraulic"]:
            continue
        for end in ("from", "to"):
            if nodes[s[end]].get("elev_m") is None:
                raise GeometryError("asset %s needs elev_m (used by hydraulic segment %s)" % (s[end], s["id"]))
        a, b = nodes[s["from"]], nodes[s["to"]]
        dz = b["elev_m"] - a["elev_m"]
        s["elev_start_m"], s["elev_end_m"], s["dz_m"] = a["elev_m"], b["elev_m"], dz
        if abs(dz) > s["length_m"] * (1.0 + LENGTH_TOL_REL):
            add("error", "GEOM-LENGTH", "segment %s: |dz| = %.1f m exceeds the measured length %.1f m" % (s["id"], abs(dz), s["length_m"]), s["id"])
        hl = horizontal_length(s, nodes)
        s["horizontal_length_m"] = hl
        if hl is None:
            add("info", "GEOM-HORIZ-UNKNOWN", "segment %s: horizontal length unknown (no plan coordinates); the length check uses dz only" % s["id"], s["id"])
        else:
            chord = math.hypot(hl, dz)
            if s["length_m"] < chord * (1.0 - LENGTH_TOL_REL):
                add("error", "GEOM-LENGTH", "segment %s: measured length %.1f m is shorter than the 3D chord %.1f m (horizontal %.1f m, dz %.1f m)"
                    % (s["id"], s["length_m"], chord, hl, dz), s["id"])
            elif s["length_m"] > chord * (1.0 + LENGTH_WARN_REL) and not s.get("length_note"):
                add("warning", "GEOM-LENGTH-EXCESS", "segment %s: measured length %.1f m exceeds the 3D chord %.1f m by more than %d %% "
                    "without a length_note (route curvature or error?)" % (s["id"], s["length_m"], chord, 100 * LENGTH_WARN_REL), s["id"])
        for key in ("id_m", "roughness_m"):
            s[key] = _num(s.get(key), "%s.%s" % (s["id"], key), positive=True)
        s["thermal"] = thermal_properties(s)
        s["od_m"] = s["thermal"]["od_m"]
    sinks = [n for n in nodes if n not in out and any(s["to"] == n and s["hydraulic"] for s in segs)]
    if len(sinks) != 1:
        raise GeometryError("the hydraulic network needs exactly one sink node (host interface), found %s" % sinks)
    sources = [n for n in nodes if n in out and not any(s["to"] == n and s["hydraulic"] for s in segs)]
    if not sources:
        raise GeometryError("the hydraulic network has no source node (cycle?)")
    paths = {}
    for src in sources:
        chain, node, guard = [], src, 0
        while node in out:
            chain.append(out[node]["id"])
            node = out[node]["to"]
            guard += 1
            if guard > len(segs):
                raise GeometryError("cycle in the hydraulic network at node %s" % node)
        if node != sinks[0]:
            raise GeometryError("path from %s ends at %s, not at the sink %s" % (src, node, sinks[0]))
        paths["%s->%s" % (src, sinks[0])] = chain
    seg_by = {s["id"]: s for s in segs}
    for pid, chain in paths.items():
        ch = 0.0
        for sid in chain:
            s = seg_by[sid]
            s.setdefault("path_chainage_m", {})[pid] = {"start": ch, "end": ch + s["length_m"]}
            ch += s["length_m"]
    transitions = []
    for pid, chain in paths.items():
        for a, b in zip(chain[:-1], chain[1:]):
            if thermal_key(seg_by[a]) != thermal_key(seg_by[b]):
                transitions.append({"path": pid, "from_segment": a, "to_segment": b, "node": seg_by[a]["to"]})
    model = {"nodes": nodes, "segments": segs, "paths": paths, "sources": sources, "sink": sinks[0], "findings": findings,
             "transitions": transitions}
    return findings, model


def with_layer_overrides(model, overrides):
    """Copy of a route model with layer thicknesses replaced for named segments; thermal properties are recomputed.

    Args:
        model: model from :func:`check_route`.
        overrides: ``{segment_id: {layer_name: thickness_m}}``; an unknown segment or layer raises :class:`GeometryError`.
    """
    new = copy.deepcopy(model)
    by = {s["id"]: s for s in new["segments"]}
    for sid, layers in (overrides or {}).items():
        if sid not in by:
            raise GeometryError("layer override for unknown segment '%s'" % sid)
        for name, thickness in layers.items():
            hit = [l for l in by[sid]["layers"] if l.get("name") == name]
            if not hit:
                raise GeometryError("segment %s has no layer named '%s'" % (sid, name))
            hit[0]["thickness_m"] = _num(thickness, "%s override %s" % (sid, name), positive=True)
        by[sid]["thermal"] = thermal_properties(by[sid])
        by[sid]["od_m"] = by[sid]["thermal"]["od_m"]
    return new


def apply_variant(model, variant):
    """Resolve a topology variant to the active segment set.

    Supported operations: ``isolate_segments`` (segment ids closed by a modelled isolation point) and
    ``isolate_sources`` (source node ids taken out of the line-up). Everything keeps its stable ID.
    Unsupported (rejected with :class:`TopologyError`): any other key such as ``bypass``, ``reroute``, ``add_segments``
    or ``new_assets`` - the extension point is this function; a new variant type needs a hydraulic network that is
    rebuilt for the new topology and tests that keep the asset IDs stable.

    Returns:
        dict with ``active_segments``, ``inactive_segments``, ``active_sources``, ``inactive_sources``, ``active_paths``.
    """
    variant = variant or {"id": "DEFAULT"}
    vid = variant.get("id", "DEFAULT")
    extra = sorted(set(variant.keys()) - {"id", "description", "isolate_segments", "isolate_sources"})
    if extra:
        raise TopologyError("topology variant %s uses unsupported operation(s) %s; supported: isolate_segments, isolate_sources "
                            "(extension point: fa_geometry.apply_variant)" % (vid, extra))
    seg_ids = {s["id"] for s in model["segments"]}
    iso_seg = list(variant.get("isolate_segments") or [])
    iso_src = list(variant.get("isolate_sources") or [])
    for sid in iso_seg:
        if sid not in seg_ids:
            raise TopologyError("topology variant %s isolates unknown segment '%s'" % (vid, sid))
    for src in iso_src:
        if src not in model["sources"]:
            raise TopologyError("topology variant %s isolates '%s' which is not a hydraulic source %s" % (vid, src, model["sources"]))
    inactive_sources = set(iso_src)
    active_paths = {}
    for pid, chain in model["paths"].items():
        src = pid.split("->")[0]
        if src in inactive_sources or any(sid in iso_seg for sid in chain):
            inactive_sources.add(src)
            continue
        active_paths[pid] = chain
    if not active_paths:
        raise TopologyError("topology variant %s leaves no active path to the host interface" % vid)
    active = {sid for chain in active_paths.values() for sid in chain}
    return {"id": vid, "description": variant.get("description", ""), "active_segments": sorted(active),
            "inactive_segments": sorted(seg_ids - active), "active_sources": sorted({p.split("->")[0] for p in active_paths}),
            "inactive_sources": sorted(inactive_sources), "active_paths": active_paths}


def node_chainage(model, path_id):
    """Chainage (m) of every node on a path, from the path start."""
    chain = model["paths"][path_id]
    segs = {s["id"]: s for s in model["segments"]}
    out = {segs[chain[0]]["from"]: 0.0}
    for sid in chain:
        out[segs[sid]["to"]] = segs[sid]["path_chainage_m"][path_id]["end"]
    return out
