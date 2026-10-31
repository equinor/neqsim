"""Structured process graph for the professional process overview (``neqsim.process_overview.graph/1``).

Turns the case-aware data set written by :mod:`psr_extract` (``neqsim.process_study_report.data/1``) into a
drawing-independent graph: stable equipment and stream identifiers, equipment types and ports, process-section
membership and train grouping, operating values of **one identified case** with provenance, and the display
settings the renderer needs. The reduction rules that make a one-sheet overview readable (numerical recycle
elements removed, in-line valves absorbed into their stream, many parallel feeds grouped, identical parallel
trains grouped) are applied here, and every reduction is recorded in ``graph["mapping"]`` so the underlying
equipment stays traceable.

Nothing in this module touches a simulation model; it works on plain dictionaries.

Typical use::

    data = json.load(open("process_data.json"))
    graph = build_graph(data, case_id="A", config={"level": "overview"})
    problems = check_graph(graph)          # [] when the graph is consistent
"""
from __future__ import annotations

import copy
import re

SCHEMA = "neqsim.process_overview.graph/1"
LEVELS = ("overview", "engineering", "presentation")
SERVICES = ("multiphase", "gas", "oil", "water", "utility", "unknown")

#: Reductions applied per information level (keys of :data:`REDUCTIONS`).
DEFAULT_COLLAPSE = {
    "overview": ["recycle", "valve_inline"],
    "engineering": ["recycle"],
    "presentation": ["recycle", "valve_inline", "pipe_inline"],
}
REDUCTIONS = {
    "recycle": "numerical recycle element removed; its inlet stream is drawn to the consumers of its outlet",
    "valve_inline": "valve with one inlet and one internal outlet absorbed into the inlet stream",
    "pipe_inline": "pipe segment with one inlet and one internal outlet absorbed into the inlet stream",
}
DEFAULT_CONFIG = {
    "level": "overview",
    "collapse": None,  # None: DEFAULT_COLLAPSE[level]
    "group_feeds_min": 3,  # boundary feeds into one unit are grouped from this count
    "group_trains": "identical",  # identical | never
    "train_pattern": r"^(?P<base>.+?)\s*[-_ ]\s*(?:train|tog|line)\s*(?P<train>[A-Z]|\d{1,2})\s*$",
    "main_fraction": 0.15,  # a stream is a main-path stream when its mass flow >= fraction * largest flow
    "annotate": "auto",  # auto | all | none | [stream ids]
    "annotate_max": {"overview": 20, "engineering": 60, "presentation": 8},
    "sections": None,  # optional explicit {section name: [tags]} (replaces the model areas)
    "section_labels": None,  # optional {model area name: display name}
    "section_order": None,  # optional [section names] fixing the left-to-right order
}

_TRAIN_FLAGS = re.I


# ---------------------------------------------------------------------------
# helpers
# ---------------------------------------------------------------------------
def _cfg(config):
    out = copy.deepcopy(DEFAULT_CONFIG)
    for k, v in (config or {}).items():
        if k in ("annotate_max",) and isinstance(v, dict):
            out[k].update(v)
        else:
            out[k] = v
    if out["level"] not in LEVELS:
        raise ValueError("level must be one of %s" % (LEVELS,))
    if out["collapse"] is None:
        out["collapse"] = list(DEFAULT_COLLAPSE[out["level"]])
    unknown = [c for c in out["collapse"] if c not in REDUCTIONS]
    if unknown:
        raise ValueError("unknown reductions %s (known: %s)" % (unknown, sorted(REDUCTIONS)))
    return out


def _case_results(rec, case_id):
    res = (rec.get("results") or {}).get(case_id)
    return res if isinstance(res, dict) else {}


def classify_service(stream_res):
    """Dominant service of a stream from its phase split: multiphase, gas, oil, water or unknown."""
    if not stream_res or not stream_res.get("available", True):
        return "unknown"
    phases = stream_res.get("phases") or []
    shares = {}
    for ph in phases:
        v = ph.get("mass_fraction")
        if v is None:
            v = ph.get("mole_fraction")
        if v is None:
            continue
        key = {"gas": "gas", "oil": "oil", "aqueous": "water", "water": "water"}.get(str(ph.get("type")), "other")
        shares[key] = shares.get(key, 0.0) + float(v)
    if shares:
        total = sum(shares.values()) or 1.0
        dom = max(shares.items(), key=lambda kv: kv[1])
        if dom[1] / total >= 0.9 and dom[0] in ("gas", "oil", "water"):
            return dom[0]
        return "multiphase"
    state = str(stream_res.get("state") or "")
    if state == "gas":
        return "gas"
    if state in ("aqueous", "water"):
        return "water"
    if state == "oil":
        return "oil"
    if "+" in state:
        return "multiphase"
    return "unknown"


def _values(stream_res):
    keys = ("t_c", "p_bara", "mass_kg_h", "mole_kmol_h", "std_vol_ideal_sm3_h", "std_gas_sm3_h", "std_oil_sm3_h",
            "std_water_sm3_h")
    out = {}
    for k in keys:
        v = stream_res.get(k) if stream_res else None
        if isinstance(v, (int, float)):
            out[k] = float(v)
    return out


def _unit_values(unit_res):
    out = {}
    for k in ("duty_kw", "power_kw", "dp_bar"):
        v = unit_res.get(k) if unit_res else None
        if isinstance(v, (int, float)):
            out[k] = float(v)
    return out


def _sum_values(items):
    """Aggregate values of grouped streams: flows summed, T/P as min-max ranges."""
    out = {}
    for key in ("mass_kg_h", "mole_kmol_h", "std_vol_ideal_sm3_h", "std_gas_sm3_h", "std_oil_sm3_h", "std_water_sm3_h"):
        vals = [v.get(key) for v in items if v.get(key) is not None]
        if vals and len(vals) == len(items):
            out[key] = sum(vals)
    for key in ("t_c", "p_bara"):
        vals = [v.get(key) for v in items if v.get(key) is not None]
        if vals:
            lo, hi = min(vals), max(vals)
            out[key] = lo if abs(hi - lo) < 1e-9 else None
            out[key + "_range"] = [lo, hi]
    return out


def _detect_train(name, pattern):
    m = re.match(pattern, name or "", _TRAIN_FLAGS)
    if not m:
        return None, None
    return m.group("base").strip(), m.group("train").upper()


def _sec_id(i):
    return "SEC-%02d" % (i + 1)


def _safe_id(s):
    return "".join(c if (c.isalnum() or c in "-_") else "_" for c in str(s))


# ---------------------------------------------------------------------------
# graph construction
# ---------------------------------------------------------------------------
class _Work:
    """Mutable working copy of equipment and streams while reductions are applied."""

    def __init__(self, data, case_id):
        self.case = case_id
        self.units = {}
        self.unit_order = []
        for e in data.get("equipment", []):
            if e.get("category") == "logic":
                continue
            tag = str(e.get("tag"))
            if tag in self.units:
                continue
            self.units[tag] = {
                "id": tag, "label": e.get("name") or tag, "category": e.get("category") or "other",
                "java_class": e.get("java_class") or "", "area": e.get("area"),
                "ins": [str(s) for s in (e.get("inlet_streams") or [])],
                "outs": [str(s) for s in (e.get("outlet_streams") or [])],
                "values": _unit_values(_case_results(e, case_id)), "design": dict(e.get("design") or {}),
                "members": [tag], "represents": None,
            }
            self.unit_order.append(tag)
        self.streams = {}
        self.stream_order = []
        for s in data.get("streams", []):
            sid = str(s.get("id"))
            if sid in self.streams:
                continue
            prod = [t for t in (s.get("producers") or []) if t in self.units]
            if not prod and s.get("source") in self.units:
                prod = [s.get("source")]
            cons = [t for t in (s.get("consumers") or []) if t in self.units]
            if not cons and s.get("destination") in self.units:
                cons = [s.get("destination")]
            res = _case_results(s, case_id)
            self.streams[sid] = {
                "id": sid, "label": s.get("label") or s.get("name") or sid, "name": s.get("name") or sid,
                "kind": s.get("kind") or "internal", "from": prod[0] if prod else None, "to": list(cons),
                "service": classify_service(res), "role": "process", "values": _values(res),
                "members": [sid], "absorbed": [], "area": s.get("area"),
            }
            self.stream_order.append(sid)
        # unit port lists are authoritative for connectivity
        for tag in self.unit_order:
            u = self.units[tag]
            for sid in u["outs"]:
                st = self.streams.get(sid)
                if st is not None and st["from"] is None:
                    st["from"] = tag
            for sid in u["ins"]:
                st = self.streams.get(sid)
                if st is not None and tag not in st["to"]:
                    st["to"].append(tag)
        self.mapping = {"collapsed_equipment": {}, "merged_streams": {}, "grouped_feeds": {}, "grouped_products": {},
                        "grouped_trains": {}}

    # -- connectivity helpers
    def inlets(self, tag):
        return [sid for sid in self.stream_order if sid in self.streams and tag in self.streams[sid]["to"]]

    def outlets(self, tag):
        return [sid for sid in self.stream_order if sid in self.streams and self.streams[sid]["from"] == tag]

    def drop_stream(self, sid):
        self.streams.pop(sid, None)
        self.stream_order = [s for s in self.stream_order if s != sid]

    def drop_unit(self, tag):
        self.units.pop(tag, None)
        self.unit_order = [t for t in self.unit_order if t != tag]

    def bypass(self, tag, reason, role=None):
        """Remove ``tag`` (one inlet, one outlet) and draw its inlet stream to the outlet's consumers."""
        ins, outs = self.inlets(tag), self.outlets(tag)
        if len(ins) != 1 or len(outs) != 1:
            return False
        s_in, s_out = self.streams[ins[0]], self.streams[outs[0]]
        if not s_out["to"] and s_out["kind"] != "product":
            return False
        s_in["to"] = [t for t in s_in["to"] if t != tag] + [t for t in s_out["to"] if t not in s_in["to"]]
        s_in["absorbed"].append(tag)
        s_in.setdefault("chain", []).extend([s_out["id"]] + list(s_out.get("chain") or []))
        if not s_out["to"]:
            s_in["kind"] = "product"
            s_in["label"] = s_out["label"]
        if role:
            s_in["role"] = role
        self.mapping["collapsed_equipment"][tag] = {"into_stream": s_in["id"], "reason": reason,
                                                     "removed_stream": s_out["id"], "category": self.units[tag]["category"]}
        self.mapping["merged_streams"][s_out["id"]] = {"into": s_in["id"], "reason": reason}
        self.drop_stream(s_out["id"])
        self.drop_unit(tag)
        return True


def _collapse(work, kinds):
    for tag in list(work.unit_order):
        u = work.units.get(tag)
        if u is None:
            continue
        if u["category"] == "recycle" and "recycle" in kinds:
            ins, outs = work.inlets(tag), work.outlets(tag)
            if len(outs) == 1 and ins:
                s_out = work.streams[outs[0]]
                for sid in ins:
                    s_in = work.streams[sid]
                    s_in["to"] = [t for t in s_in["to"] if t != tag] + [t for t in s_out["to"] if t not in s_in["to"]]
                    s_in["role"] = "recycle"
                    s_in["absorbed"].append(tag)
                    s_in.setdefault("chain", []).extend([s_out["id"]] + list(s_out.get("chain") or []))
                work.mapping["collapsed_equipment"][tag] = {"into_stream": ins[0], "reason": REDUCTIONS["recycle"],
                                                            "removed_stream": s_out["id"], "category": "recycle"}
                work.mapping["merged_streams"][s_out["id"]] = {"into": ins[0], "reason": REDUCTIONS["recycle"]}
                work.drop_stream(s_out["id"])
                work.drop_unit(tag)
        elif u["category"] == "valve" and "valve_inline" in kinds:
            ins, outs = work.inlets(tag), work.outlets(tag)
            if len(ins) == 1 and len(outs) == 1 and work.streams[outs[0]]["to"] and work.streams[ins[0]]["from"]:
                work.bypass(tag, REDUCTIONS["valve_inline"])
        elif u["category"] == "pipe" and "pipe_inline" in kinds:
            ins, outs = work.inlets(tag), work.outlets(tag)
            if len(ins) == 1 and len(outs) == 1 and work.streams[outs[0]]["to"] and work.streams[ins[0]]["from"]:
                work.bypass(tag, REDUCTIONS["pipe_inline"])


def _group_boundaries(work, min_count):
    """Group boundary feeds entering (or products leaving) the same unit."""
    for tag in list(work.unit_order):
        feeds = [sid for sid in work.inlets(tag) if work.streams[sid]["from"] is None]
        if len(feeds) >= min_count:
            gid = "GF-%s" % _safe_id(tag)
            members = []
            for sid in feeds:
                members.extend(work.streams[sid]["members"])
            items = [work.streams[sid] for sid in feeds]
            label = "%d feeds (%s ... %s)" % (len(feeds), items[0]["label"], items[-1]["label"])
            new = {"id": gid, "label": label, "name": label, "kind": "feed", "from": None, "to": [tag],
                   "service": _dominant_service(items), "role": "process", "values": _sum_values([s["values"] for s in items]),
                   "members": members, "absorbed": [], "area": work.units[tag]["area"], "grouped": True,
                   "group_labels": [s["label"] for s in items]}
            first = work.stream_order.index(feeds[0])
            for sid in feeds:
                work.drop_stream(sid)
            work.streams[gid] = new
            work.stream_order.insert(first, gid)
            work.mapping["grouped_feeds"][gid] = {"unit": tag, "streams": feeds, "count": len(feeds)}
        prods = [sid for sid in work.outlets(tag) if not work.streams[sid]["to"]]
        if len(prods) >= min_count:
            gid = "GP-%s" % _safe_id(tag)
            items = [work.streams[sid] for sid in prods]
            members = []
            for sid in prods:
                members.extend(work.streams[sid]["members"])
            label = "%d products (%s ... %s)" % (len(prods), items[0]["label"], items[-1]["label"])
            new = {"id": gid, "label": label, "name": label, "kind": "product", "from": tag, "to": [],
                   "service": _dominant_service(items), "role": "process", "values": _sum_values([s["values"] for s in items]),
                   "members": members, "absorbed": [], "area": work.units[tag]["area"], "grouped": True,
                   "group_labels": [s["label"] for s in items]}
            first = work.stream_order.index(prods[0])
            for sid in prods:
                work.drop_stream(sid)
            work.streams[gid] = new
            work.stream_order.insert(first, gid)
            work.mapping["grouped_products"][gid] = {"unit": tag, "streams": prods, "count": len(prods)}


def _dominant_service(items):
    counts = {}
    for s in items:
        counts[s["service"]] = counts.get(s["service"], 0) + 1
    return max(counts.items(), key=lambda kv: (kv[1], kv[0] == "multiphase"))[0]


def _sections(work, data, cfg):
    """Ordered sections ``[{id, name, model_area, members, train_group, train}]`` covering every unit."""
    labels = cfg.get("section_labels") or {}
    explicit = cfg.get("sections")
    raw = []
    if explicit:
        for name, tags in explicit.items():
            raw.append((name, [str(t) for t in tags]))
    else:
        for a in data.get("areas", []):
            raw.append((a.get("name"), [str(t) for t in a.get("units", [])]))
    assigned = set()
    sections = []
    for name, tags in raw:
        members = [t for t in tags if t in work.units and t not in assigned]
        assigned.update(members)
        sections.append({"model_area": name, "name": labels.get(name, name), "members": members})
    for tag in work.unit_order:
        if tag in assigned:
            continue
        area = work.units[tag]["area"]
        tgt = next((s for s in sections if s["model_area"] == area), None)
        if tgt is None:
            tgt = {"model_area": area or "unassigned", "name": labels.get(area, area or "unassigned"), "members": []}
            sections.append(tgt)
        tgt["members"].append(tag)
        assigned.add(tag)
    sections = [s for s in sections if s["members"]]
    order = cfg.get("section_order")
    if order:
        rank = {n: i for i, n in enumerate(order)}
        sections.sort(key=lambda s: (rank.get(s["name"], rank.get(s["model_area"], len(rank))),))
    for i, s in enumerate(sections):
        s["id"] = _sec_id(i)
        base, train = _detect_train(s["name"], cfg["train_pattern"])
        s["train_group"] = base
        s["train"] = train
        s["grouped_trains"] = None
        s["represents"] = None
    return sections


def _train_signature(work, members):
    cats = sorted((work.units[t]["category"], work.units[t]["java_class"]) for t in members)
    mset = set(members)
    internal = sum(1 for sid in work.stream_order
                   if work.streams[sid]["from"] in mset and any(t in mset for t in work.streams[sid]["to"]))
    return tuple(cats), internal


def _topo_members(work, members):
    """Deterministic order of a train's units: longest path from the train inlet, then category, then tag."""
    mset = set(members)
    preds = {t: set() for t in members}
    for sid in work.stream_order:
        st = work.streams[sid]
        if st["from"] in mset:
            for t in st["to"]:
                if t in mset and t != st["from"]:
                    preds[t].add(st["from"])
    depth = {}

    def d(t, seen):
        if t in depth:
            return depth[t]
        if t in seen:
            return 0
        seen = seen | {t}
        depth[t] = 1 + max([d(p, seen) for p in preds[t]], default=0)
        return depth[t]

    for t in members:
        d(t, frozenset())
    return sorted(members, key=lambda t: (depth[t], work.units[t]["category"], t))


def _group_trains(work, sections):
    groups = {}
    for s in sections:
        if s["train_group"]:
            groups.setdefault(s["train_group"], []).append(s)
    removed = []
    for base, secs in groups.items():
        if len(secs) < 2:
            continue
        sigs = {_train_signature(work, s["members"]) for s in secs}
        if len(sigs) != 1:
            continue
        rep = secs[0]
        rep_order = _topo_members(work, rep["members"])
        pairs = {}
        ok = True
        for other in secs[1:]:
            o_order = _topo_members(work, other["members"])
            for a, b in zip(rep_order, o_order):
                if work.units[a]["category"] != work.units[b]["category"]:
                    ok = False
                pairs[b] = a
        if not ok:
            continue
        trains = [s["train"] for s in secs]
        for other in secs[1:]:
            oset = set(other["members"])
            for sid in list(work.stream_order):
                st = work.streams.get(sid)
                if st is None:
                    continue
                f_in, t_in = st["from"] in oset, any(t in oset for t in st["to"])
                if not (f_in or t_in):
                    continue
                new_from = pairs.get(st["from"], st["from"]) if st["from"] else None
                new_to = []
                for t in st["to"]:
                    nt = pairs.get(t, t)
                    if nt not in new_to:
                        new_to.append(nt)
                twin = next((x for x in work.stream_order if x != sid and work.streams[x]["from"] == new_from
                             and sorted(work.streams[x]["to"]) == sorted(new_to)), None)
                if twin is not None:
                    tw = work.streams[twin]
                    tw["members"].extend(m for m in st["members"] if m not in tw["members"])
                    tw["values"] = _sum_values([tw["values"], st["values"]]) if tw.get("values") and st.get("values") else tw["values"]
                    tw["grouped"] = True
                    work.mapping["merged_streams"][sid] = {"into": twin, "reason": "parallel identical train %s grouped" % other["train"]}
                    work.drop_stream(sid)
                else:
                    st["from"], st["to"] = new_from, new_to
            for t in other["members"]:
                rep_tag = pairs[t]
                work.units[rep_tag]["members"].append(t)
                work.drop_unit(t)
            removed.append(other["id"])
            work.mapping["grouped_trains"][other["id"]] = {"represented_by": rep["id"], "train": other["train"],
                                                            "pairs": {b: a for b, a in pairs.items() if b in other["members"]}}
        for t in rep_order:
            n = len(work.units[t]["members"])
            if n > 1:
                work.units[t]["represents"] = {"count": n, "trains": trains}
        rep["grouped_trains"] = trains
        rep["represents"] = {"count": len(secs), "trains": trains, "sections": [s["id"] for s in secs],
                             "model_areas": [s["model_area"] for s in secs]}
        rep["name"] = "%s - %d identical trains (%s), train %s shown" % (base, len(secs), "/".join(trains), rep["train"])
    return [s for s in sections if s["id"] not in removed]


def _pick_annotations(work, sections, cfg, section_of):
    mode = cfg["annotate"]
    if isinstance(mode, (list, tuple)):
        return [s for s in mode if s in work.streams]
    if mode == "none":
        return []
    if mode == "all":
        return list(work.stream_order)
    limit = cfg["annotate_max"][cfg["level"]]
    level = cfg["level"]
    scored = []
    for sid in work.stream_order:
        st = work.streams[sid]
        if not st["values"]:
            continue
        boundary = st["from"] is None or not st["to"]
        cross = st["from"] is not None and any(section_of.get(t) != section_of.get(st["from"]) for t in st["to"])
        main = bool(st.get("main"))
        if level == "presentation":
            take = boundary
        elif level == "overview":
            take = boundary or (cross and main)
        else:
            take = boundary or cross or main
        if not take:
            continue
        score = (3 if boundary else 0) + (2 if cross else 0) + (1 if main else 0)
        scored.append((-score, -(st["values"].get("mass_kg_h") or 0.0), sid))
    scored.sort()
    return [sid for _, _, sid in scored[:limit]]


def build_graph(data, case_id=None, config=None, source_path=None):
    """Build the structured process graph for one operating case.

    Args:
        data: ``neqsim.process_study_report.data/1`` dict (``psr_extract.extract_process`` / ``merge_cases``).
        case_id: id of the case whose values are displayed (default: first case).
        config: overrides of :data:`DEFAULT_CONFIG`.
        source_path: path of the data file, recorded for provenance.

    Returns:
        Graph dict (schema :data:`SCHEMA`).
    """
    cfg = _cfg(config)
    cases = data.get("cases") or [{"id": "A", "label": "A"}]
    ids = [str(c.get("id")) for c in cases]
    case_id = str(case_id) if case_id is not None else ids[0]
    if case_id not in ids:
        raise ValueError("case %r not in data (cases: %s)" % (case_id, ids))
    case = next(c for c in cases if str(c.get("id")) == case_id)
    work = _Work(data, case_id)
    _collapse(work, cfg["collapse"])
    _group_boundaries(work, int(cfg["group_feeds_min"]))
    sections = _sections(work, data, cfg)
    if cfg["group_trains"] == "identical":
        sections = _group_trains(work, sections)
    section_of = {}
    for s in sections:
        s["members"] = [t for t in s["members"] if t in work.units]
        for t in s["members"]:
            section_of[t] = s["id"]
    sections = [s for s in sections if s["members"]]
    # main path flag
    flows = [st["values"].get("mass_kg_h") for st in work.streams.values() if st["values"].get("mass_kg_h")]
    fmax = max(flows) if flows else 0.0
    for st in work.streams.values():
        m = st["values"].get("mass_kg_h")
        st["main"] = bool(fmax and m is not None and m >= cfg["main_fraction"] * fmax)
    equipment = []
    for tag in work.unit_order:
        u = work.units[tag]
        equipment.append({
            "id": tag, "label": u["label"], "category": u["category"], "java_class": u["java_class"],
            "section": section_of.get(tag), "ports": {"in": work.inlets(tag), "out": work.outlets(tag)},
            "values": u["values"], "design": u["design"], "members": u["members"], "represents": u["represents"],
        })
    streams = []
    boundaries = []
    for sid in work.stream_order:
        st = work.streams[sid]
        rec = {k: st[k] for k in ("id", "label", "name", "kind", "from", "to", "service", "role", "values", "members",
                                  "absorbed", "main")}
        rec["chain"] = list(st.get("chain") or [])
        rec["grouped"] = bool(st.get("grouped"))
        if st.get("group_labels"):
            rec["group_labels"] = st["group_labels"]
        rec["section_from"] = section_of.get(st["from"]) if st["from"] else None
        rec["sections_to"] = sorted({section_of[t] for t in st["to"] if t in section_of})
        streams.append(rec)
        if st["from"] is None and st["to"]:
            boundaries.append({"stream": sid, "kind": "feed", "label": st["label"], "service": st["service"],
                               "section": section_of.get(st["to"][0]), "unit": st["to"][0]})
        elif st["from"] is not None and not st["to"]:
            boundaries.append({"stream": sid, "kind": "product", "label": st["label"], "service": st["service"],
                               "section": section_of.get(st["from"]), "unit": st["from"]})
    annotate = _pick_annotations(work, sections, cfg, section_of)
    meta = data.get("meta") or {}
    summary = _summary(work, boundaries)
    graph = {
        "schema": SCHEMA,
        "source": {"data_schema": data.get("schema"), "model_name": meta.get("model_name"), "data_file": meta.get("data_file"),
                   "generated_utc": meta.get("generated_utc"), "neqsim_version": meta.get("neqsim_version"),
                   "eos": meta.get("eos"), "path": source_path,
                   "standard_conditions": meta.get("standard_conditions") or {"t_c": 15.0, "p_bara": 1.01325}},
        "case": {"id": case_id, "label": case.get("label") or case_id, "all_cases": ids},
        "provenance": "every value in this graph is results[%r] of the source data set; no design ratings" % case_id,
        "level": cfg["level"],
        "config": cfg,
        "sections": sections,
        "equipment": equipment,
        "streams": streams,
        "boundaries": boundaries,
        "mapping": work.mapping,
        "summary": summary,
        "display": {"annotate": annotate},
    }
    graph["counts"] = {"equipment": len(equipment), "streams": len(streams), "sections": len(sections),
                       "source_equipment": len([e for e in data.get("equipment", []) if e.get("category") != "logic"]),
                       "source_streams": len(data.get("streams", []))}
    return graph


def _summary(work, boundaries):
    feeds = [b for b in boundaries if b["kind"] == "feed"]
    prods = [b for b in boundaries if b["kind"] == "product"]

    def mass(items):
        vals = [work.streams[b["stream"]]["values"].get("mass_kg_h") for b in items]
        return sum(v for v in vals if v is not None) if any(v is not None for v in vals) else None

    power = sum(u["values"].get("power_kw") or 0.0 for u in work.units.values()
                if u["category"] in ("compressor", "pump") and u["values"].get("power_kw") is not None)
    heat = sum(u["values"].get("duty_kw") or 0.0 for u in work.units.values()
               if u["category"] in ("heater",) and u["values"].get("duty_kw") is not None)
    cool = sum(u["values"].get("duty_kw") or 0.0 for u in work.units.values()
               if u["category"] in ("cooler",) and u["values"].get("duty_kw") is not None)
    return {"feed_mass_kg_h": mass(feeds), "product_mass_kg_h": mass(prods), "n_feeds": len(feeds), "n_products": len(prods),
            "shaft_power_kw": power, "heating_kw": heat, "cooling_kw": cool,
            "products": [{"stream": b["stream"], "label": b["label"], "service": b["service"],
                          "mass_kg_h": work.streams[b["stream"]]["values"].get("mass_kg_h")} for b in prods]}


# ---------------------------------------------------------------------------
# consistency checks
# ---------------------------------------------------------------------------
def check_graph(graph, data=None):
    """Structural checks of a graph; returns a list of problem strings (empty when consistent).

    With ``data`` the mapping is verified against the source: every source unit must appear as a node, a grouped
    member or a collapsed element and every source stream as a stream or a merged member.
    """
    problems = []
    eq_ids = [e["id"] for e in graph["equipment"]]
    if len(set(eq_ids)) != len(eq_ids):
        problems.append("duplicate equipment ids")
    st_ids = [s["id"] for s in graph["streams"]]
    if len(set(st_ids)) != len(st_ids):
        problems.append("duplicate stream ids")
    eq = set(eq_ids)
    for s in graph["streams"]:
        if s["from"] is not None and s["from"] not in eq:
            problems.append("stream %s: producer %s is not drawn" % (s["id"], s["from"]))
        for t in s["to"]:
            if t not in eq:
                problems.append("stream %s: consumer %s is not drawn" % (s["id"], t))
        if s["from"] is None and not s["to"]:
            problems.append("stream %s has neither producer nor consumer" % s["id"])
    sec_of = {}
    for sec in graph["sections"]:
        for t in sec["members"]:
            if t in sec_of:
                problems.append("unit %s is in sections %s and %s" % (t, sec_of[t], sec["id"]))
            sec_of[t] = sec["id"]
    for t in eq_ids:
        if t not in sec_of:
            problems.append("unit %s has no section" % t)
    for e in graph["equipment"]:
        if e.get("represents") and len(e["members"]) != e["represents"]["count"]:
            problems.append("grouped unit %s: members (%d) do not match represented count (%d)"
                            % (e["id"], len(e["members"]), e["represents"]["count"]))
    if data is not None:
        covered = set()
        for e in graph["equipment"]:
            covered.update(e["members"])
        covered.update(graph["mapping"]["collapsed_equipment"])
        for e in data.get("equipment", []):
            if e.get("category") != "logic" and str(e.get("tag")) not in covered:
                problems.append("source unit %s is neither drawn, grouped nor recorded as collapsed" % e.get("tag"))
        s_cov = set()
        for s in graph["streams"]:
            s_cov.update(s["members"])
            s_cov.update(s.get("chain") or [])
        s_cov.update(graph["mapping"]["merged_streams"])
        for g in list(graph["mapping"]["grouped_feeds"].values()) + list(graph["mapping"]["grouped_products"].values()):
            s_cov.update(g["streams"])
        for s in data.get("streams", []):
            if str(s.get("id")) not in s_cov:
                problems.append("source stream %s is not represented" % s.get("id"))
    return problems


def stream_index(graph):
    """``{stream id: stream dict}``."""
    return {s["id"]: s for s in graph["streams"]}


def equipment_index(graph):
    """``{equipment id: equipment dict}``."""
    return {e["id"]: e for e in graph["equipment"]}


def merge_sections(graph, groups):
    """Merge groups of sections into combined sections (names joined, members united, model areas listed).

    Used as the last fitting fallback: fewer blocks, fewer connections. Every merged section records
    ``merged_from`` (section ids) and ``model_areas`` so the coverage of the source model stays traceable.
    """
    g = copy.deepcopy(graph)
    by_id = {s["id"]: s for s in g["sections"]}
    remap = {}
    new_sections = []
    used = set()
    for grp in groups:
        grp = [i for i in grp if i in by_id]
        if len(grp) < 2:
            continue
        first = by_id[grp[0]]
        areas = []
        members = []
        names = []
        origin = []
        for i in grp:
            s = by_id[i]
            members.extend(s["members"])
            names.append(s["name"].split(" - ")[0])
            areas.extend((s.get("represents") or {}).get("model_areas") or s.get("model_areas") or [s.get("model_area")])
            origin.extend(s.get("merged_from") or [i])
            used.add(i)
            remap[i] = first["id"]
        new_sections.append({"id": first["id"], "name": " + ".join(names), "model_area": first.get("model_area"), "members": members,
                             "train_group": None, "train": None, "grouped_trains": None, "represents": None,
                             "merged_from": origin, "model_areas": [a for a in areas if a]})
    for s in g["sections"]:
        if s["id"] not in used:
            new_sections.append(s)
    order = {s["id"]: k for k, s in enumerate(graph["sections"])}
    new_sections.sort(key=lambda s: min(order.get(i, 10 ** 6) for i in (s.get("merged_from") or [s["id"]] + [s["id"]])))
    g["sections"] = new_sections
    sec_of = {}
    for s in new_sections:
        for t in s["members"]:
            sec_of[t] = s["id"]
    for e in g["equipment"]:
        e["section"] = sec_of.get(e["id"])
    for st in g["streams"]:
        st["section_from"] = sec_of.get(st["from"]) if st["from"] else None
        st["sections_to"] = sorted({sec_of[t] for t in st["to"] if t in sec_of})
    for b in g["boundaries"]:
        b["section"] = sec_of.get(b["unit"])
    g["counts"]["sections"] = len(new_sections)
    g.setdefault("mapping", {})["merged_sections"] = {s["id"]: s["merged_from"] for s in new_sections if s.get("merged_from")}
    return g


def section_pair_weights(graph):
    """``{(a, b): total mass flow + count}`` of streams between sections (both directions summed)."""
    w = {}
    for st in graph["streams"]:
        a = st.get("section_from")
        for b in st.get("sections_to") or []:
            if a and b and a != b:
                key = tuple(sorted((a, b)))
                w[key] = w.get(key, 0.0) + ((st.get("values") or {}).get("mass_kg_h") or 0.0) + 1.0
    return w


def subgraph(graph, section_id):
    """Graph restricted to one section; streams crossing its border become labelled boundary streams.

    Used for supporting detail slides. Stream ids and equipment ids are unchanged; a stream entering from another
    section keeps its id and gets ``from = None`` with the label ``"<label> (from <section name>)"``.
    """
    sec = next(s for s in graph["sections"] if s["id"] == section_id)
    names = {s["id"]: s["name"] for s in graph["sections"]}
    mset = set(sec["members"])
    g = copy.deepcopy(graph)
    g["sections"] = [copy.deepcopy(sec)]
    g["equipment"] = [e for e in g["equipment"] if e["id"] in mset]
    streams = []
    boundaries = []
    for st in g["streams"]:
        f_in = st["from"] in mset
        cons = [t for t in st["to"] if t in mset]
        if not f_in and not cons:
            continue
        st = dict(st)
        if not f_in:
            src_name = names.get(st.get("section_from"), "other section")
            st["label"] = "%s (from %s)" % (st["label"], src_name)
            st["from"], st["section_from"] = None, None
        if f_in and len(cons) < len(st["to"]):
            outs = sorted({names.get(s, s) for s in st.get("sections_to", []) if s != section_id})
            st["label"] = "%s (to %s)" % (st["label"], ", ".join(outs) if outs else "other section")
        st["to"] = cons
        st["sections_to"] = [section_id] if cons else []
        streams.append(st)
        if st["from"] is None and cons:
            boundaries.append({"stream": st["id"], "kind": "feed", "label": st["label"], "service": st["service"],
                               "section": section_id, "unit": cons[0]})
        elif st["from"] is not None and not cons:
            boundaries.append({"stream": st["id"], "kind": "product", "label": st["label"], "service": st["service"],
                               "section": section_id, "unit": st["from"]})
    g["streams"] = streams
    g["boundaries"] = boundaries
    for e in g["equipment"]:
        e["ports"] = {"in": [s["id"] for s in streams if e["id"] in s["to"]], "out": [s["id"] for s in streams if s["from"] == e["id"]]}
    g["display"] = {"annotate": [s["id"] for s in streams if s["id"] in set(graph["display"]["annotate"])]}
    g["counts"] = dict(graph["counts"], equipment=len(g["equipment"]), streams=len(streams), sections=1)
    g["subgraph_of"] = section_id
    return g
