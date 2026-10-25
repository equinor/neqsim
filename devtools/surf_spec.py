"""SURF study specification (``surf_study.v1``): loading, unit-explicit validation and normalisation.

The specification is one YAML/JSON document. Every physical quantity carries its unit in the key suffix
(``_m``, ``_mm``, ``_bara``, ``_c``, ``_kg_s``, ``_w_m2k``); gauge pressures, free-form units and values that read
"unavailable" are rejected, so no silent unit conversion can occur. :func:`check_spec` returns findings and a normalised
model with derived geometry (route length from the 3D polyline, hydraulic inside diameter from OD and wall, elevation
profile), concept edge sets and resolved cases. Nothing here starts the JVM.
"""
import math
import re

import fa_units
from fa_units import FlowAssuranceError

SCHEMA = "surf_study.v1"
ID_RE = re.compile(r"^[A-Za-z0-9_\-]+$")
INPUT_CLASSES = ("specified", "measured", "calculated", "estimated", "synthetic", "unavailable")
READINESS = ("NOT_READY", "SCREENING", "REVIEWED")
NODE_KINDS = ("production_well", "injection_well", "manifold", "injection_manifold", "plem", "plet", "junction", "host", "injection_source",
              "riser_base", "tie_in")
EDGE_KINDS = ("jumper", "flowline", "trunkline", "riser", "pipeline", "pump", "header", "tie_in")
SERVICES = ("production", "injection")
HYDRAULIC_MODELS = ("beggs_brill", "darcy_weisbach", "two_fluid")
STEEL_DENSITY = 7850.0
SEAWATER_DENSITY = 1025.0
UNAVAILABLE_RE = re.compile(r"unavailable|to be confirmed", re.I)
REQUIRED_TOP = ("schema", "document", "study", "sources", "fluids", "host", "nodes", "edges", "concepts", "cases", "acceptance_criteria", "solver",
                "assumptions", "data_gaps")
REQUIRED_DOC = ("title", "document_number", "revision", "issue_status", "readiness", "date", "company", "project")


class SpecError(FlowAssuranceError):
    """The study specification cannot be used (structural problem)."""


def finding(level, code, message, where=""):
    """One input finding: ``level`` is ``error`` or ``warning``."""
    return {"level": level, "code": code, "message": message, "where": where}


def _num(v):
    return isinstance(v, (int, float)) and not isinstance(v, bool) and math.isfinite(v)


def _walk(obj, path=""):
    """Yield ``(path, key, value)`` for every leaf of a nested structure."""
    if isinstance(obj, dict):
        for k, v in obj.items():
            yield from _walk(v, "%s.%s" % (path, k) if path else str(k))
    elif isinstance(obj, list):
        for i, v in enumerate(obj):
            yield from _walk(v, "%s[%d]" % (path, i))
    else:
        yield path, path.rsplit(".", 1)[-1], obj


def scan_values(spec):
    """Findings for gauge pressures, unavailable placeholders and non-finite numbers anywhere in the specification."""
    out = []
    for path, key, val in _walk(spec):
        if key.endswith("_barg") or key.endswith("_psig"):
            out.append(finding("error", "UNIT-GAUGE", "gauge pressure key '%s' is not accepted; give absolute pressure as *_bara" % path, path))
        if isinstance(val, str) and UNAVAILABLE_RE.search(val) and not key.startswith(("note", "description", "basis", "gap", "label")):
            out.append(finding("error", "VALUE-UNAVAILABLE", "'%s' is not filled in (%s)" % (path, val[:60]), path))
        if isinstance(val, float) and not math.isfinite(val):
            out.append(finding("error", "VALUE-NONFINITE", "'%s' is not finite" % path, path))
    return out


def route_polyline(points):
    """Cumulative 3D path length (m) at each point of an ``[[x, y, z], ...]`` polyline and the total."""
    cum = [0.0]
    for a, b in zip(points[:-1], points[1:]):
        cum.append(cum[-1] + math.sqrt(sum((b[i] - a[i]) ** 2 for i in range(3))))
    return cum


def hydraulic_id_m(edge):
    """Hydraulic inside diameter in metres: ``od - 2 wall`` for rigid pipe, ``id_mm`` for flexible pipe."""
    if edge.get("construction", "rigid") == "flexible":
        return edge["id_mm"] / 1000.0
    return (edge["od_mm"] - 2.0 * edge["wall_mm"]) / 1000.0


def check_spec(spec):
    """Validate ``spec`` and return ``(findings, model)``; ``model`` is None when a structural error prevents normalisation."""
    f = []
    if not isinstance(spec, dict):
        raise SpecError("the study specification must be a mapping")
    if spec.get("schema") != SCHEMA:
        f.append(finding("error", "SCHEMA", "schema must be '%s', got %r" % (SCHEMA, spec.get("schema")), "schema"))
    for key in REQUIRED_TOP:
        if key not in spec:
            f.append(finding("error", "MISSING-SECTION", "section '%s' is missing" % key, key))
    if any(x["code"] == "MISSING-SECTION" for x in f):
        return f, None
    f.extend(scan_values(spec))
    doc = spec["document"]
    for key in REQUIRED_DOC:
        if doc.get(key) in (None, ""):
            f.append(finding("error", "DOC-FIELD", "document.%s is missing" % key, "document." + key))
    if doc.get("readiness") not in READINESS:
        f.append(finding("error", "DOC-READINESS", "document.readiness must be one of %s" % (READINESS,), "document.readiness"))
    if doc.get("readiness") == "REVIEWED" and not (spec.get("review") or {}).get("record"):
        f.append(finding("error", "DOC-REVIEWED", "readiness REVIEWED needs a recorded human review (review.record)", "document.readiness"))
    src_ids = set()
    for s in spec["sources"]:
        if s.get("input_class") not in INPUT_CLASSES:
            f.append(finding("error", "SOURCE-CLASS", "source %s input_class must be one of %s" % (s.get("id"), INPUT_CLASSES), "sources"))
        src_ids.add(s.get("id"))
    fluids = spec["fluids"]
    for fid, fd in fluids.items():
        comps = fd.get("components") or {}
        total = sum(float(v) for v in comps.values()) if comps else 0.0
        if not comps or abs(total - 1.0) > 1.0e-6:
            f.append(finding("error", "FLUID-SUM", "fluid %s mole fractions sum to %.8f, expected 1" % (fid, total), "fluids." + fid))
        if fd.get("source") not in src_ids:
            f.append(finding("error", "FLUID-SOURCE", "fluid %s cites unknown source %r" % (fid, fd.get("source")), "fluids." + fid))
    nodes, edges = {}, {}
    for n in spec["nodes"]:
        nid = str(n.get("id"))
        if not ID_RE.match(nid):
            f.append(finding("error", "ID-CHARS", "node id %r contains characters other than letters, digits, '_' and '-'" % nid, nid))
        if nid in nodes:
            f.append(finding("error", "ID-DUPLICATE", "duplicate node id %s" % nid, nid))
        if n.get("kind") not in NODE_KINDS:
            f.append(finding("error", "NODE-KIND", "node %s kind must be one of %s" % (nid, NODE_KINDS), nid))
        if n.get("service") not in SERVICES:
            f.append(finding("error", "NODE-SERVICE", "node %s service must be production or injection" % nid, nid))
        for axis in ("x_m", "y_m", "z_m"):
            if not _num(n.get(axis)):
                f.append(finding("error", "GEOMETRY-MISSING", "node %s has no numeric %s (route geometry is required)" % (nid, axis), nid))
        if n.get("source") not in src_ids:
            f.append(finding("error", "NODE-SOURCE", "node %s cites unknown source %r" % (nid, n.get("source")), nid))
        nodes[nid] = n
    for e in spec["edges"]:
        eid = str(e.get("id"))
        if not ID_RE.match(eid):
            f.append(finding("error", "ID-CHARS", "edge id %r contains characters other than letters, digits, '_' and '-'" % eid, eid))
        if eid in edges or eid in nodes:
            f.append(finding("error", "ID-DUPLICATE", "duplicate identifier %s" % eid, eid))
        if e.get("kind") not in EDGE_KINDS:
            f.append(finding("error", "EDGE-KIND", "edge %s kind must be one of %s" % (eid, EDGE_KINDS), eid))
        for end in ("from", "to"):
            if e.get(end) not in nodes:
                f.append(finding("error", "EDGE-ENDPOINT", "edge %s %s-node %r does not exist" % (eid, end, e.get(end)), eid))
        edges[eid] = e
    if any(x["level"] == "error" for x in f):
        return f, None
    model = {"nodes": nodes, "edges": {}, "structures": {s["id"]: s for s in spec.get("structures", [])}}
    for eid, e in edges.items():
        f.extend(_check_edge(e, nodes, model, src_ids))
    f.extend(_check_ports(edges))
    concepts = _resolve_concepts(spec, edges, nodes, f)
    model["concepts"] = concepts
    model["cases"] = _resolve_cases(spec, f)
    f.extend(_check_umbilical(spec, nodes))
    f.extend(_check_criteria(spec))
    return f, model


def _check_edge(e, nodes, model, src_ids):
    f, eid = [], e["id"]
    a, b = nodes[e["from"]], nodes[e["to"]]
    pts = [[a["x_m"], a["y_m"], a["z_m"]]] + [list(map(float, p)) for p in (e.get("route_points") or [])] + [[b["x_m"], b["y_m"], b["z_m"]]]
    if any(len(p) != 3 or not all(_num(v) for v in p) for p in pts):
        f.append(finding("error", "GEOMETRY-MISSING", "edge %s route_points must be [x, y, z] triples" % eid, eid))
        return f
    cum = route_polyline(pts)
    route_len = cum[-1]
    allowance = float(e.get("allowance_fraction", 0.0))
    if allowance < 0.0 or (allowance > 0.0 and not e.get("allowance_basis")):
        f.append(finding("error", "ALLOWANCE", "edge %s: a length allowance needs allowance_basis and must be >= 0 (no double counting)" % eid, eid))
    installed = route_len * (1.0 + allowance)
    declared = e.get("declared_length_m")
    if declared is not None and abs(declared - route_len) > fa_units_len_tol(route_len):
        f.append(finding("error", "LENGTH-CONFLICT", "edge %s declared_length_m %.1f disagrees with the route polyline %.1f m (1%% tolerance)" % (eid, declared, route_len), eid))
    if e["kind"] == "pump":
        rec = {"id": eid, "points": pts, "chainage_m": cum, "route_length_m": 0.0, "installed_length_m": 0.0, "id_m": None, "od_m": None}
        pump = e.get("pump") or {}
        if not _num(pump.get("discharge_pressure_bara")) or not (0 < pump.get("efficiency", 0) <= 1):
            f.append(finding("error", "PUMP-INPUT", "pump edge %s needs pump.discharge_pressure_bara and 0 < efficiency <= 1" % eid, eid))
        model["edges"][eid] = dict(e, **rec)
        return f
    if route_len <= 0.0:
        f.append(finding("error", "GEOMETRY-ZERO", "edge %s has zero route length" % eid, eid))
    construction = e.get("construction", "rigid")
    if construction not in ("rigid", "flexible"):
        f.append(finding("error", "CONSTRUCTION", "edge %s construction must be rigid or flexible" % eid, eid))
    od_m = id_m = None
    try:
        if construction == "rigid":
            od, wt = float(e["od_mm"]), float(e["wall_mm"])
            if not (od > 0 and 0 < wt < od / 2.0):
                raise KeyError("wall")
            id_calc = (od - 2.0 * wt) / 1000.0
            if e.get("id_mm") is not None and abs(e["id_mm"] / 1000.0 - id_calc) > 0.0005:
                f.append(finding("error", "DIMENSION-CONFLICT", "edge %s id_mm %.1f conflicts with OD %.1f - 2 x wall %.1f = %.1f mm" % (eid, e["id_mm"], od, wt, id_calc * 1000), eid))
            od_m, id_m = od / 1000.0, id_calc
        else:
            id_m = float(e["id_mm"]) / 1000.0
            od_m = float(e["od_mm"]) / 1000.0 if e.get("od_mm") else None
    except (KeyError, TypeError, ValueError):
        f.append(finding("error", "DIMENSION-MISSING", "edge %s needs od_mm and wall_mm (rigid) or id_mm (flexible) with 0 < wall < OD/2" % eid, eid))
    h = e.get("hydraulic") or {}
    if h.get("model") not in HYDRAULIC_MODELS:
        f.append(finding("error", "HYDRAULIC-MODEL", "edge %s hydraulic.model must be one of %s" % (eid, HYDRAULIC_MODELS), eid))
    if not isinstance(h.get("segments"), int) or h["segments"] < 1:
        f.append(finding("error", "HYDRAULIC-SEGMENTS", "edge %s hydraulic.segments must be an integer >= 1" % eid, eid))
    th = e.get("thermal") or {}
    if not _num(th.get("ambient_c")):
        f.append(finding("error", "THERMAL-MISSING", "edge %s needs thermal.ambient_c" % eid, eid))
    if construction == "flexible" and not _num(th.get("u_value_w_m2k")):
        f.append(finding("error", "THERMAL-FLEX", "flexible edge %s needs a supplier u_value_w_m2k (not derived from the steel layer stack)" % eid, eid))
    if construction == "rigid" and not (e.get("insulation") or {}).get("material") and not _num(th.get("u_value_w_m2k")):
        f.append(finding("error", "THERMAL-RIGID", "rigid edge %s needs insulation.material or an explicit u_value_w_m2k" % eid, eid))
    dsg = e.get("design") or {}
    if not (_num(dsg.get("pressure_bara")) and _num(dsg.get("temperature_c"))):
        f.append(finding("warning", "DESIGN-LIMITS", "edge %s has no design.pressure_bara/temperature_c; the rating check is not assessed" % eid, eid))
    if e.get("source") not in src_ids:
        f.append(finding("error", "EDGE-SOURCE", "edge %s cites unknown source %r" % (eid, e.get("source")), eid))
    if a["service"] != b["service"] or e.get("service") != a["service"]:
        f.append(finding("error", "SERVICE-MISMATCH", "edge %s service %r does not match its end nodes (%s, %s)" % (eid, e.get("service"), a["service"], b["service"]), eid))
    model["edges"][eid] = dict(e, points=pts, chainage_m=cum, route_length_m=route_len, installed_length_m=installed, id_m=id_m, od_m=od_m)
    return f


def fa_units_len_tol(length):
    """Length agreement tolerance for a declared length: 1 % of the length."""
    return 0.01 * max(length, 1.0)


def _check_ports(edges):
    f, seen = [], {}
    for e in edges.values():
        for end, port in (("from", e.get("from_port")), ("to", e.get("to_port"))):
            if not port:
                f.append(finding("error", "PORT-MISSING", "edge %s has no %s_port" % (e["id"], end), e["id"]))
                continue
            key = (e[end], port)
            if key in seen and seen[key] != e["id"]:
                f.append(finding("error", "PORT-DUPLICATE", "port '%s' on node %s is used by both %s and %s" % (port, e[end], seen[key], e["id"]), e["id"]))
            seen[key] = e["id"]
    return f


def _resolve_concepts(spec, edges, nodes, f):
    out = {}
    connected_any = {e["from"] for e in edges.values()} | {e["to"] for e in edges.values()}
    for n in nodes:
        if n not in connected_any:
            f.append(finding("error", "TOPOLOGY-ORPHAN", "node %s is not connected to any edge (disconnected port or missing line)" % n, n))
    for c in spec["concepts"]:
        cid = c.get("id")
        if not cid or not ID_RE.match(str(cid)):
            f.append(finding("error", "CONCEPT-ID", "concept id %r is invalid" % cid, "concepts"))
            continue
        active = [eid for eid, e in edges.items() if not e.get("concepts") or cid in e["concepts"]]
        overrides = c.get("edge_overrides") or {}
        for eid in overrides:
            if eid not in edges:
                f.append(finding("error", "CONCEPT-OVERRIDE", "concept %s overrides unknown edge %s" % (cid, eid), cid))
        used = {edges[eid]["from"] for eid in active} | {edges[eid]["to"] for eid in active}
        f.extend(_connectivity(cid, edges, nodes, active, used))
        out[cid] = {"id": cid, "label": c.get("label", cid), "description": c.get("description", ""), "edges": active, "nodes": sorted(used), "overrides": overrides,
                    "feasibility": c.get("feasibility_note", "")}
    if len(out) < 2:
        f.append(finding("error", "CONCEPTS", "at least two design alternatives (concepts) are required for concept selection", "concepts"))
    return out


def _connectivity(cid, edges, nodes, active, used):
    """Every used node must reach a host or sit on a path from an injection source (undirected connectivity per service)."""
    f = []
    adj = {n: set() for n in used}
    for eid in active:
        a, b = edges[eid]["from"], edges[eid]["to"]
        adj[a].add(b)
        adj[b].add(a)
    for service, anchor_kind in (("production", "host"), ("injection", "injection_source")):
        group = [n for n in used if nodes[n]["service"] == service]
        anchors = [n for n in group if nodes[n]["kind"] == anchor_kind]
        if not group:
            continue
        if not anchors:
            f.append(finding("error", "TOPOLOGY-ANCHOR", "concept %s has no %s node in the %s service" % (cid, anchor_kind, service), cid))
            continue
        seen, stack = set(anchors[:1]), list(anchors[:1])
        while stack:
            for m in adj[stack.pop()]:
                if m not in seen:
                    seen.add(m)
                    stack.append(m)
        for n in group:
            if n not in seen:
                f.append(finding("error", "TOPOLOGY-DISCONNECTED", "concept %s: node %s is not connected to the %s anchor" % (cid, n, service), cid))
    return f


def _resolve_cases(spec, f):
    out, seen = {}, set()
    for c in spec["cases"]:
        cid = c.get("id")
        if not cid or not ID_RE.match(str(cid)) or cid in seen:
            f.append(finding("error", "CASE-ID", "case id %r is invalid or duplicated" % cid, "cases"))
            continue
        seen.add(cid)
        if c.get("production_fluid") not in spec["fluids"]:
            f.append(finding("error", "CASE-FLUID", "case %s production_fluid %r is not a defined fluid" % (cid, c.get("production_fluid")), cid))
        for key in ("host_pressure_bara", "production_reservoir_pressure_bara", "injection_pump_discharge_bara", "injection_reservoir_pressure_bara"):
            if not _num(c.get(key)) or c[key] <= 0:
                f.append(finding("error", "CASE-INPUT", "case %s needs positive %s" % (cid, key), cid))
        out[cid] = dict(c, productivity_scale=float(c.get("productivity_scale", 1.0)))
    if not any(c.get("role") == "design" for c in out.values()):
        f.append(finding("error", "CASE-ROLE", "one case must have role 'design'", "cases"))
    return out


def _check_umbilical(spec, nodes):
    f, umb = [], spec.get("umbilical")
    if not umb:
        f.append(finding("warning", "UMBILICAL", "no umbilical specified: chemical distribution is not assessed", "umbilical"))
        return f
    if not _num(umb.get("length_m")) or umb["length_m"] <= 0:
        f.append(finding("error", "UMBILICAL-LENGTH", "umbilical.length_m must be positive", "umbilical"))
    for s in umb.get("services", []):
        if s.get("kind") not in ("chemical", "hydraulic", "power", "signal"):
            f.append(finding("error", "UMBILICAL-KIND", "umbilical service %s has an unknown kind" % s.get("id"), "umbilical"))
        if s.get("consumer") not in nodes and s.get("kind") in ("chemical", "hydraulic"):
            f.append(finding("error", "UMBILICAL-CONSUMER", "umbilical service %s consumer %r is not a node" % (s.get("id"), s.get("consumer")), "umbilical"))
        if s.get("kind") == "chemical" and s.get("fluid") not in spec["fluids"]:
            f.append(finding("error", "UMBILICAL-FLUID", "chemical service %s fluid %r is not defined" % (s.get("id"), s.get("fluid")), "umbilical"))
    return f


def _check_criteria(spec):
    f = []
    for c in spec["acceptance_criteria"]:
        if c.get("operator") not in (">=", "<=", ">", "<") or not _num(c.get("value")) or not c.get("quantity") or not c.get("source"):
            f.append(finding("error", "CRITERION", "criterion %s needs quantity, operator, numeric value and source" % c.get("id"), "acceptance_criteria"))
    return f


def load_spec(path):
    """Load a study file (YAML or JSON) and return ``(spec, findings, model)``; raises :class:`SpecError` when unreadable."""
    import psr_basis

    try:
        spec = psr_basis.load_structured(path)
    except Exception as exc:  # noqa: BLE001
        raise SpecError("cannot read %s: %s" % (path, exc))
    findings, model = check_spec(spec)
    return spec, findings, model


def case_for_concept(model, spec, concept_id):
    """Edges of one concept with the concept overrides merged (a new dict per edge; the specification is not mutated)."""
    cdef = model["concepts"][concept_id]
    out = {}
    for eid in cdef["edges"]:
        e = dict(model["edges"][eid])
        for k, v in (cdef["overrides"].get(eid) or {}).items():
            if isinstance(v, dict) and isinstance(e.get(k), dict):
                e[k] = dict(e[k], **v)
            else:
                e[k] = v
        if e["kind"] != "pump":
            e["id_m"] = hydraulic_id_m(e)
            e["od_m"] = e["od_mm"] / 1000.0 if e.get("od_mm") else None
        out[eid] = e
    return out


def spec_hash(spec, n=16):
    """Stable short hash of the specification content."""
    import hashlib
    import json

    return hashlib.sha256(json.dumps(spec, sort_keys=True, default=str).encode("utf-8")).hexdigest()[:n]
