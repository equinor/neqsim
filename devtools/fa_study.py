"""Canonical study dataset of the flow-assurance study report.

One normalised dictionary (``flow_assurance_study.v1``) holds everything the drawings, tables, plots and narrative
show. This module loads and checks the inputs, runs the cases ONCE through :mod:`fa_solver` (with a complete-key cache),
derives margins, extrema (with location, case and basis), cooldown, criteria status, the threat matrix and mitigation
comparison, and provides the ``[[token]]`` resolver that ties every reported number back to the dataset.

Token syntax (``|format`` and ``|unit=U`` are optional; a unit request converts or raises)::

    [[res:CASE:streams.HOST-IN.t_c|.1f]]       case result by dotted path
    [[ext:CASE:hyd_margin_min:value|.1f]]       derived extremum field (value, unit, path, chainage_m, segment_id, case, basis)
    [[crit:C-HYD-01:overall]]                   criterion field; [[crit:C-HYD-01:BASE:status]] per case
    [[seg:FL-T-01:thermal.u_inner_w_m2k|.2f]]   route segment property
    [[segres:CASE:SEG:dp_bar|.1f]]              per-case segment result
    [[bm:BM-HYDROSTATIC:value]]                 benchmark field
    [[fact:key]]  [[meta:key]]  [[basis:dotted.path]]
"""
import copy
import datetime
import hashlib
import json
import math
import os
import re
import time

import fa_geometry
import fa_methods
import fa_solver
import fa_thermal
from fa_units import FlowAssuranceError, UnitError, check_rate, convert, suffix_unit

SCHEMA = "flow_assurance_study.v1"
TOOL_VERSION = "1.0"
ID_RE = re.compile(r"^[A-Za-z0-9_\-]+$")
FA_READINESS = ("NOT_READY", "SCREENING", "REVIEWED")
INPUT_CLASSES = ("measured", "specified", "calculated", "estimated", "assumed", "synthetic", "unavailable")
OPERATORS = (">=", "<=", ">", "<")
REQUIRED_TOP = ("document", "study", "conventions", "sources", "fluids", "solver", "acceptance_criteria", "assumptions",
                "exclusions", "data_gaps", "model")
REQUIRED_DOCUMENT = ("title", "document_number", "revision", "issue_status", "readiness", "date", "company", "project")
QUANTITIES = {  # criterion quantity -> (extremum id, unit)
    "hydrate_margin_min": ("hyd_margin_min", "K"), "source_pressure_max": ("source_pressure_max", "bara"),
    "mixture_velocity_max": ("v_mix_max", "m/s"), "erosional_ratio_max": ("erosional_ratio_max", "-"),
    "cooldown_time_min": ("cooldown_time_min", "h"), "host_arrival_temperature_min": ("host_arrival_t", "C")}


def _finding(level, code, message, where=""):
    return {"level": level, "code": code, "message": message, "where": where}


def canonical_hash(obj, n=16):
    """Short SHA-256 of the canonical JSON of ``obj`` (sorted keys, floats as repr)."""
    return hashlib.sha256(json.dumps(obj, sort_keys=True, default=str).encode("utf-8")).hexdigest()[:n]


# --- input checks -----------------------------------------------------------------------------------
def check_basis(basis):
    """Check the flow-assurance basis for completeness and honesty; returns findings (never raises)."""
    import psr_basis

    out = []
    if not isinstance(basis, dict):
        return [_finding("error", "BASIS-TYPE", "basis is not a mapping")]
    for key in REQUIRED_TOP:
        if basis.get(key) in (None, "", [], {}):
            out.append(_finding("error", "BASIS-MISSING", "required basis item '%s' is missing or empty" % key, key))
    doc = basis.get("document") or {}
    for key in REQUIRED_DOCUMENT:
        if not doc.get(key):
            out.append(_finding("error", "BASIS-DOC", "document.%s is missing" % key, "document." + key))
    if doc.get("readiness") and str(doc["readiness"]) not in FA_READINESS:
        out.append(_finding("error", "BASIS-READINESS", "document.readiness must be one of %s" % (FA_READINESS,), "document.readiness"))
    review = doc.get("review") or {}
    if str(doc.get("readiness")) == "REVIEWED" and not (review.get("reviewer") and review.get("date") and review.get("record")):
        out.append(_finding("error", "BASIS-REVIEW", "readiness REVIEWED needs document.review.reviewer, date and record supplied by a person", "document.review"))
    study = basis.get("study") or {}
    for key in ("objective", "decisions", "maturity", "battery_limits", "field_life_stages"):
        if not study.get(key):
            out.append(_finding("error", "BASIS-STUDY", "study.%s is missing" % key, "study." + key))
    conv = basis.get("conventions") or {}
    for key in ("units", "pressure_reference", "composition_basis", "rate_basis", "standard_conditions", "chainage", "vertical_datum"):
        if not conv.get(key):
            out.append(_finding("error", "BASIS-CONVENTION", "conventions.%s is not defined" % key, "conventions." + key))
    std = conv.get("standard_conditions") or {}
    if isinstance(std, dict) and not ("t_c" in std and "p_bara" in std):
        out.append(_finding("error", "BASIS-STDCOND", "conventions.standard_conditions needs t_c and p_bara", "conventions.standard_conditions"))
    src_ids = set()
    for i, s in enumerate(basis.get("sources") or []):
        for key in ("id", "title", "document", "revision", "location", "input_class", "provenance", "status"):
            if not s.get(key):
                out.append(_finding("error", "BASIS-SOURCE", "sources[%d].%s missing" % (i, key), "sources[%d]" % i))
        if s.get("input_class") and s["input_class"] not in INPUT_CLASSES:
            out.append(_finding("error", "BASIS-SOURCE", "sources[%d].input_class must be one of %s" % (i, INPUT_CLASSES), "sources[%d]" % i))
        src_ids.add(s.get("id"))
    for fid, f in (basis.get("fluids") or {}).items():
        for key in ("eos_hydraulics", "eos_hydrate", "components", "pvt_evidence", "source"):
            if not f.get(key):
                out.append(_finding("error", "BASIS-FLUID", "fluids.%s.%s is missing" % (fid, key), "fluids." + fid))
        if f.get("source") and f["source"] not in src_ids:
            out.append(_finding("error", "BASIS-FLUID", "fluids.%s.source '%s' is not in the source register" % (fid, f["source"]), "fluids." + fid))
    seen = set()
    for i, c in enumerate(basis.get("acceptance_criteria") or []):
        for key in ("id", "text", "quantity", "operator", "value", "unit", "applies_to", "source", "requirement_class", "applicability"):
            if c.get(key) in (None, "", []):
                out.append(_finding("error", "BASIS-CRITERIA", "acceptance_criteria[%d].%s missing" % (i, key), "acceptance_criteria[%d]" % i))
        if c.get("id") in seen:
            out.append(_finding("error", "BASIS-CRITERIA", "duplicate criterion id %s" % c.get("id"), "acceptance_criteria[%d]" % i))
        seen.add(c.get("id"))
        if c.get("operator") and c["operator"] not in OPERATORS:
            out.append(_finding("error", "BASIS-CRITERIA", "acceptance_criteria[%d].operator must be one of %s" % (i, OPERATORS), "acceptance_criteria[%d]" % i))
        try:
            if not math.isfinite(float(c.get("value"))):
                raise ValueError
        except (TypeError, ValueError):
            out.append(_finding("error", "BASIS-CRITERIA", "acceptance_criteria[%d].value is not a finite number" % i, "acceptance_criteria[%d]" % i))
        if c.get("requirement_class") and c["requirement_class"] not in psr_basis.REQUIREMENT_CLASSES:
            out.append(_finding("error", "BASIS-CRITERIA", "acceptance_criteria[%d].requirement_class must be one of %s" % (i, psr_basis.REQUIREMENT_CLASSES),
                                "acceptance_criteria[%d]" % i))
    for i, gap in enumerate(basis.get("data_gaps") or []):
        for key in ("id", "item", "effect_on_conclusions"):
            if not gap.get(key):
                out.append(_finding("error", "BASIS-GAP", "data_gaps[%d].%s missing (the effect on conclusions must be stated)" % (i, key), "data_gaps[%d]" % i))
    model = basis.get("model") or {}
    for key in ("name", "version", "simulator", "eos", "mixing_rule", "convergence"):
        if not model.get(key):
            out.append(_finding("error", "BASIS-MODEL", "model.%s is missing" % key, "model." + key))
    for tid, t in (basis.get("threats") or {}).items():
        if t.get("status") not in fa_methods.THREAT_STATUSES or not t.get("reason"):
            out.append(_finding("error", "BASIS-THREAT", "threats.%s needs a status from %s and a reason" % (tid, fa_methods.THREAT_STATUSES), "threats." + tid))
        if t.get("status") == "calculated":
            out.append(_finding("error", "BASIS-THREAT", "threats.%s: 'calculated' cannot be asserted in the basis; it follows from the results" % tid, "threats." + tid))
    for path, text in psr_basis._walk_strings(basis):  # noqa: SLF001 - shared string walker
        hits = psr_basis.placeholder_hits(text)
        if hits:
            out.append(_finding("error", "BASIS-PLACEHOLDER", "placeholder text %s" % hits[:3], path))
    return out


def check_standards_register(register):
    """Check a flow-assurance standards register (shared checks plus the verification fields of the toolkit)."""
    import psr_basis

    out = psr_basis.check_standards(register)
    extra = ("amendment", "clause_used", "implemented_scope", "evidence", "assessment_status")
    for row in register or []:
        for key in extra:
            if row.get(key) in (None, ""):
                out.append(_finding("error", "STD-FIELD", "%s: field '%s' missing" % (row.get("reference"), key), row.get("reference", "")))
        if row.get("text_available") in (False, "no") and str(row.get("clause_used", "")).lower() not in ("none (text not read)", "none"):
            out.append(_finding("error", "STD-OVERCLAIM", "%s: a clause is cited although the text was not available" % row.get("reference"), row.get("reference", "")))
    return out


def check_cases(doc, route_model, basis):
    """Validate the cases document; returns ``(findings, cases, variants)`` with variants resolved to active segment sets."""
    findings = []
    cases = list(doc.get("cases") or [])
    if not cases:
        raise FlowAssuranceError("cases.yaml defines no cases")
    vdefs = {v["id"]: v for v in (doc.get("topology_variants") or [{"id": "DEFAULT"}])}
    variants = {vid: fa_geometry.apply_variant(route_model, v) for vid, v in vdefs.items()}
    ids = [c.get("id") for c in cases]
    if len(set(ids)) != len(ids):
        raise FlowAssuranceError("duplicate case ids %s" % sorted({i for i in ids if ids.count(i) > 1}))
    fluids = (basis.get("fluids") or {})
    for c in cases:
        cid = c.get("id")
        if not cid or not ID_RE.match(str(cid)):
            raise FlowAssuranceError("case id %r is missing or contains characters other than letters, digits, '_' and '-'" % cid)
        for key in ("label", "role", "stage", "fluid", "sources", "boundary"):
            if not c.get(key):
                findings.append(_finding("error", "CASE-FIELD", "case %s: '%s' is missing" % (cid, key), cid))
        if c.get("fluid") not in fluids:
            findings.append(_finding("error", "CASE-FLUID", "case %s: fluid '%s' is not in the basis" % (cid, c.get("fluid")), cid))
        vid = c.get("topology_variant", "DEFAULT")
        if vid not in variants:
            raise FlowAssuranceError("case %s uses unknown topology variant '%s'" % (cid, vid))
        for node, spec in (c.get("sources") or {}).items():
            if node not in route_model["sources"]:
                findings.append(_finding("error", "CASE-SOURCE", "case %s: '%s' is not a hydraulic source of the route %s" % (cid, node, route_model["sources"]), cid))
                continue
            try:
                check_rate(spec.get("hc_rate"), "%s.%s.hc_rate" % (cid, node))
                if spec.get("water_rate"):
                    check_rate(spec["water_rate"], "%s.%s.water_rate" % (cid, node))
            except FlowAssuranceError as exc:
                findings.append(_finding("error", "CASE-RATE", str(exc), cid))
        for node in variants[vid]["active_sources"]:
            if node not in (c.get("sources") or {}):
                findings.append(_finding("error", "CASE-SOURCE", "case %s: active source %s has no definition" % (cid, node), cid))
        if c.get("baseline_case") and c["baseline_case"] not in ids:
            findings.append(_finding("error", "CASE-BASELINE", "case %s: baseline_case '%s' is not a case" % (cid, c["baseline_case"]), cid))
    for ev in doc.get("events") or []:
        if ev.get("case") not in ids:
            findings.append(_finding("error", "EVENT-CASE", "event %s refers to unknown case %s" % (ev.get("id"), ev.get("case")), ev.get("id", "")))
        if ev.get("analysis") not in ("lumped_cooldown", "external"):
            findings.append(_finding("error", "EVENT-ANALYSIS", "event %s: analysis must be lumped_cooldown or external" % ev.get("id"), ev.get("id", "")))
        if ev.get("analysis") == "external":
            for key in ("required_outputs", "comparison_criteria", "description"):
                if not ev.get(key):
                    findings.append(_finding("error", "EVENT-HANDOFF", "external event %s needs %s for the hand-off package" % (ev.get("id"), key), ev.get("id", "")))
    return findings, cases, variants


# --- execution --------------------------------------------------------------------------------------
def execute_cases(basis, route_model, cases, variants, cache_dir=None, log=None, hydrate_cache=None):
    """Run each case once (cache hit when the complete key matches); returns ``({case_id: result}, timings)``."""
    say = log or (lambda m: None)
    ns = fa_solver.neqsim_handle()
    nv = fa_solver.neqsim_version()
    solver = dict(basis["solver"])
    results, timings = {}, {}
    hyd_cache = hydrate_cache if hydrate_cache is not None else {}
    for case in cases:
        t0 = time.time()
        model = fa_geometry.with_layer_overrides(route_model, case.get("layer_overrides")) if case.get("layer_overrides") else route_model
        variant = variants[case.get("topology_variant", "DEFAULT")]
        fluid_def = dict(basis["fluids"][case["fluid"]], id=case["fluid"])
        key = fa_solver.cache_key({"fluid": fluid_def, "segments": [(s["id"], s.get("length_m"), s.get("dz_m"), s.get("id_m"), s.get("roughness_m"), s.get("layers"),
                                                                        s.get("burial"), s.get("ambient"), s.get("hydraulic")) for s in model["segments"]],
                                   "solver": solver, "neqsim_version": nv}, case, extra={"variant": variant["id"]})
        cpath = os.path.join(cache_dir, "case_%s_%s.json" % (case["id"], key)) if cache_dir else None
        if cpath and os.path.isfile(cpath):
            with open(cpath, encoding="utf-8") as fh:
                results[case["id"]] = json.load(fh)
            results[case["id"]]["cache"] = {"hit": True, "key": key}
            timings[case["id"]] = {"solve_s": 0.0, "cache_hit": True}
            say("case %s: cache hit (%s)" % (case["id"], key))
            continue
        res = fa_solver.run_case(ns, model, variant, fluid_def, case, solver, hydrate_cache=hyd_cache, log=say)
        res["cache"] = {"hit": False, "key": key}
        results[case["id"]] = res
        timings[case["id"]] = {"solve_s": round(time.time() - t0, 2), "cache_hit": False}
        if cpath:
            os.makedirs(cache_dir, exist_ok=True)
            with open(cpath, "w", encoding="utf-8") as fh:
                json.dump(res, fh, default=str)
    return results, timings


def mesh_sensitivity(basis, route_model, cases, variants, case_id, increments, tol, log=None):
    """Re-run one case at several increments per segment and compare against the finest mesh (spatial sensitivity)."""
    say = log or (lambda m: None)
    case = [c for c in cases if c["id"] == case_id][0]
    ns = fa_solver.neqsim_handle()
    rows = []
    hyd_cache = {}
    for inc in increments:
        solver = dict(basis["solver"], increments=inc)
        fluid_def = dict(basis["fluids"][case["fluid"]], id=case["fluid"])
        t0 = time.time()
        res = fa_solver.run_case(ns, route_model, variants[case.get("topology_variant", "DEFAULT")], fluid_def, case, solver, hydrate_cache=hyd_cache)
        marg = derive_case(res, route_model, case, basis)
        rows.append({"increments": inc, "status": res["status"], "host_arrival_t_c": res["streams"].get(res["sink"], {}).get("t_c"),
                     "source_pressure_max_bara": marg["extrema"]["source_pressure_max"]["value"],
                     "hyd_margin_min_k": marg["extrema"]["hyd_margin_min"]["value"], "wall_s": round(time.time() - t0, 1)})
        say("mesh %d: %s" % (inc, rows[-1]))
    fine = rows[-1]
    for r in rows:
        r["d_source_p_bar"] = None if None in (r["source_pressure_max_bara"], fine["source_pressure_max_bara"]) else r["source_pressure_max_bara"] - fine["source_pressure_max_bara"]
        r["d_arrival_t_k"] = None if None in (r["host_arrival_t_c"], fine["host_arrival_t_c"]) else r["host_arrival_t_c"] - fine["host_arrival_t_c"]
        r["d_margin_k"] = None if None in (r["hyd_margin_min_k"], fine["hyd_margin_min_k"]) else r["hyd_margin_min_k"] - fine["hyd_margin_min_k"]
    used = [r for r in rows if r["increments"] == basis["solver"]["increments"]]
    ok = bool(used) and all(abs(used[0][k]) <= tol[t] for k, t in (("d_source_p_bar", "p_bar"), ("d_arrival_t_k", "t_k"), ("d_margin_k", "margin_k"))
                            if used[0][k] is not None)
    return {"case": case_id, "rows": rows, "tolerance": tol, "reference_increments": fine["increments"], "status": "PASS" if ok else "FAIL",
            "note": "deviation of the study mesh from the finest mesh; tolerances declared before the comparison"}


# --- derivation -------------------------------------------------------------------------------------
def _curve_key(res, pid, seg_id):
    src = pid.split("->")[0]
    if res.get("junction") is None or seg_id in res["branch_segments"].get(pid, []):
        return "source:%s" % src
    return "mixed:%s" % res["junction"]


def _extremum(eid, quantity, unit, mode, items, basis_text):
    """``items`` = list of dicts with value/path/chainage_m/segment_id/case; returns the record (value ``None`` when no data)."""
    vals = [i for i in items if i.get("value") is not None]
    if not vals:
        return {"id": eid, "quantity": quantity, "value": None, "unit": unit, "case": None, "path": None, "chainage_m": None, "segment_id": None,
                "time_s": None, "basis": basis_text, "status": "unavailable"}
    pick = min(vals, key=lambda i: i["value"]) if mode == "min" else max(vals, key=lambda i: i["value"])
    return {"id": eid, "quantity": quantity, "value": pick["value"], "unit": unit, "case": pick.get("case"), "path": pick.get("path"),
            "chainage_m": pick.get("chainage_m"), "segment_id": pick.get("segment_id"), "time_s": None, "basis": basis_text, "status": "calculated"}


def derive_case(res, route_model, case, basis):
    """Derived quantities of one case: hydrate margins, erosional ratio, extrema, cooldown, summary. Pure Python.

    Adds nothing to ``res``; returns ``{"profile_extra", "extrema", "cooldown", "summary", "findings"}``.
    """
    segs = {s["id"]: s for s in route_model["segments"]}
    cid = case["id"]
    c_si = float(basis["solver"].get("erosional_c_si", 0) or 0)
    findings = []
    extra, ext_items = {}, {k: [] for k in ("hyd_margin_min", "hyd_margin_unin_min", "v_mix_max", "erosional_ratio_max", "holdup_max")}
    if res["status"] != "converged":
        empty = {k: _extremum(k, k, "", "min", [], "case did not converge") for k in
                 ("hyd_margin_min", "hyd_margin_unin_min", "v_mix_max", "erosional_ratio_max", "holdup_max", "source_pressure_max", "cooldown_time_min",
                  "host_arrival_t")}
        return {"profile_extra": {}, "extrema": empty, "cooldown": None, "summary": {}, "findings": [_finding("error", "CASE-FAILED", "case %s did not converge" % cid, cid)]}
    curves = res["hydrate_curves"]
    n_unavail = 0
    for pid, prof in res["profiles"].items():
        n = len(prof["chainage_m"])
        arr = {k: [] for k in ("t_hyd_c", "t_hyd_unin_c", "aq_inhibitor_wt", "margin_k", "margin_unin_k", "erosional_v_m_s", "erosional_ratio")}
        for i in range(n):
            key = _curve_key(res, pid, prof["segment_id"][i])
            p, t = prof["p_bara"][i], prof["t_c"][i]
            th = fa_solver.interp_log_p(curves[key], p) if curves.get(key) else None
            tu = fa_solver.interp_log_p(curves["uninhibited"], p) if curves.get("uninhibited") else None
            if th is None or tu is None:
                n_unavail += 1
            arr["t_hyd_c"].append(th)
            arr["t_hyd_unin_c"].append(tu)
            arr["aq_inhibitor_wt"].append(curves[key]["wt"] if curves.get(key) else None)
            arr["margin_k"].append(None if th is None else t - th)
            arr["margin_unin_k"].append(None if tu is None else t - tu)
            ve = c_si / math.sqrt(prof["rho_mix_kg_m3"][i]) if c_si > 0 and prof["rho_mix_kg_m3"][i] > 0 else None
            arr["erosional_v_m_s"].append(ve)
            arr["erosional_ratio"].append(None if ve is None else prof["v_mix_m_s"][i] / ve)
        extra[pid] = arr

        def loc(series, mode, scale=None):
            idx = [i for i, v in enumerate(series) if v is not None]
            if not idx:
                return {"value": None}
            i = (min if mode == "min" else max)(idx, key=lambda j: series[j])
            return {"value": series[i], "path": pid, "chainage_m": prof["chainage_m"][i], "segment_id": prof["segment_id"][i], "case": cid}

        ext_items["hyd_margin_min"].append(loc(arr["margin_k"], "min"))
        ext_items["hyd_margin_unin_min"].append(loc(arr["margin_unin_k"], "min"))
        ext_items["v_mix_max"].append(loc(prof["v_mix_m_s"], "max"))
        ext_items["erosional_ratio_max"].append(loc(arr["erosional_ratio"], "max"))
        ext_items["holdup_max"].append(loc(prof["holdup"], "max"))
    if n_unavail:
        findings.append(_finding("warning", "HYDRATE-GAPS", "case %s: %d profile points have no hydrate temperature (outside the grid or solver unavailable); margins there are unavailable" % (cid, n_unavail), cid))
    ex = {
        "hyd_margin_min": _extremum("hyd_margin_min", "minimum hydrate margin (inhibited, operating)", "K", "min", ext_items["hyd_margin_min"],
                                    "min over profile points of T - T_hyd(p_local, aqueous inhibitor wt of the local stream)"),
        "hyd_margin_unin_min": _extremum("hyd_margin_unin_min", "minimum hydrate margin if uninhibited (reference)", "K", "min", ext_items["hyd_margin_unin_min"],
                                         "min over profile points of T - T_hyd(p_local, no inhibitor); not an operating mode"),
        "v_mix_max": _extremum("v_mix_max", "maximum mixture velocity", "m/s", "max", ext_items["v_mix_max"], "max over profile points of v_sg + v_sl (Beggs and Brill superficial velocities)"),
        "erosional_ratio_max": _extremum("erosional_ratio_max", "maximum mixture-to-erosional velocity ratio", "-", "max", ext_items["erosional_ratio_max"],
                                         "max over profile points of v_mix / (C_SI / sqrt(rho_mix)), C_SI = %.1f (project input)" % c_si),
        "holdup_max": _extremum("holdup_max", "maximum steady liquid holdup", "-", "max", ext_items["holdup_max"], "max over profile points; steady holdup, not a slug volume"),
    }
    src_items = [{"value": res["streams"][n]["p_bara"], "path": "%s->%s" % (n, res["sink"]), "chainage_m": 0.0, "segment_id": None, "case": cid}
                 for n in res["sources"] if n in res["streams"] and "%s->%s" % (n, res["sink"]) in res["active_paths"]]
    ex["source_pressure_max"] = _extremum("source_pressure_max", "maximum calculated source flowing pressure", "bara", "max", src_items,
                                          "backward solve from the specified host-interface pressure")
    ex["host_arrival_t"] = _extremum("host_arrival_t", "host-interface arrival temperature", "C", "min",
                                     [{"value": res["streams"][res["sink"]]["t_c"], "path": "all", "chainage_m": None, "segment_id": None, "case": cid}],
                                     "temperature of the mixed stream at the host interface")
    cool = derive_cooldown(res, route_model, case, extra, segs)
    ex["cooldown_time_min"] = _extremum("cooldown_time_min", "minimum time to hydrate equilibrium temperature after shut-in", "h", "min",
                                        cool["items"] if cool else [], "min over profile points of the lumped cooldown time to T_hyd at the settle-out pressure")
    inv = sum(r["liquid_inventory_m3"] for r in res["segments"].values())
    sink = res["streams"][res["sink"]]
    act = [n for n in res["sources"] if "%s->%s" % (n, res["sink"]) in res["active_paths"]]
    summary = {"host_arrival_p_bara": sink["p_bara"], "host_arrival_t_c": sink["t_c"], "host_arrival_mass_kg_s": sink["mass_kg_s"],
               "host_arrival_gas_kg_s": sink["gas_kg_s"], "host_arrival_oil_kg_s": sink["oil_kg_s"], "host_arrival_aqueous_kg_s": sink["aqueous_kg_s"],
               "liquid_inventory_m3": inv, "n_active_segments": len(res["segments"]), "std_gas_total_sm3_d": _std_gas_sm3_d(res),
               "lean_inhibitor_total_kg_h": 3600.0 * sum(res["sources"][n]["lean_inhibitor_kg_s"] for n in act),
               "water_total_kg_h": 3600.0 * sum(res["sources"][n]["water_kg_s"] for n in act),
               "hc_total_kg_s": sum(res["sources"][n]["hc_kg_s"] for n in act),
               "source_pressures_bara": {n: res["streams"][n]["p_bara"] for n in res["sources"] if n in res["streams"]}}
    return {"profile_extra": extra, "extrema": ex, "cooldown": cool["block"] if cool else None, "summary": summary, "findings": findings}


def _std_gas_sm3_d(res):
    rho = res.get("std_gas_density_kg_sm3")
    if not rho:
        return None
    gas = sum(s["hc_kg_s"] for n, s in res["sources"].items() if "%s->%s" % (n, res["sink"]) in res["active_paths"])
    return gas * 86400.0 / rho


def derive_cooldown(res, route_model, case, extra, segs):
    """Lumped cooldown time to the hydrate equilibrium temperature at the settle-out pressure for every profile point."""
    shut = case.get("shut_in")
    if not shut:
        return None
    p_so = float(shut["settle_out_pressure_bara"])
    curves = res["hydrate_curves"]
    per_path, items, histories = {}, [], []
    for pid, prof in res["profiles"].items():
        n = len(prof["chainage_m"])
        tau_h, t_crit, t_h, t_h_unin = [], [], [], []
        for i in range(n):
            sid = prof["segment_id"][i]
            seg, sr = segs[sid], res["segments"][sid]
            tau = fa_thermal.tau_s(sr["rho_mix_mean_kg_m3"], sr["cp_j_kgk"], seg["id_m"], seg["thermal"]["steel_mass_kg_m"],
                                   seg["thermal"]["steel_cp_j_kgk"], sr["u_inner_w_m2k"])
            key = _curve_key(res, pid, sid)
            tc = fa_solver.interp_log_p(curves[key], p_so) if curves.get(key) else None
            tc_u = fa_solver.interp_log_p(curves["uninhibited"], p_so) if curves.get("uninhibited") else None
            t0, t_amb = prof["t_c"][i], sr["ambient_t_c"]
            tm = None if tc is None else fa_thermal.time_to_temperature_s(t0, t_amb, tc, tau)
            tmu = None if tc_u is None else fa_thermal.time_to_temperature_s(t0, t_amb, tc_u, tau)
            tau_h.append(tau / 3600.0)
            t_crit.append(tc)
            t_h.append(None if tm is None or math.isinf(tm) else tm / 3600.0)
            t_h_unin.append(None if tmu is None or math.isinf(tmu) else tmu / 3600.0)
            if tm is not None:
                items.append({"value": (None if math.isinf(tm) else tm / 3600.0), "path": pid, "chainage_m": prof["chainage_m"][i], "segment_id": sid, "case": case["id"]})
        per_path[pid] = {"chainage_m": prof["chainage_m"], "segment_id": prof["segment_id"], "tau_h": tau_h, "t_crit_c": t_crit, "time_h": t_h, "time_uninhibited_h": t_h_unin}
    valid = [it for it in items if it["value"] is not None]
    gov = min(valid, key=lambda it: it["value"]) if valid else None
    duration = float(shut.get("duration_h", 72.0))
    if gov:
        prof = res["profiles"][gov["path"]]
        idx = prof["chainage_m"].index(gov["chainage_m"])
        pp = per_path[gov["path"]]
        sid = prof["segment_id"][idx]
        t0, tau = prof["t_c"][idx], pp["tau_h"][idx] * 3600.0
        amb = res["segments"][sid]["ambient_t_c"]
        times = [i * 0.5 for i in range(int(duration / 0.5) + 1)]
        histories.append({"label": "governing location", "path": gov["path"], "chainage_m": gov["chainage_m"], "segment_id": sid, "t_h": times,
                          "t_c": [fa_thermal.temperature_at(t0, amb, tau, h * 3600.0) for h in times], "t_crit_c": pp["t_crit_c"][idx],
                          "time_to_criterion_h": gov["value"], "time_origin": "t = 0 at shut-in; steady operating temperature as initial condition"})
    block = {"settle_out_pressure_bara": p_so, "duration_h": duration, "per_path": per_path, "histories": histories,
             "governing": gov, "method": "M-COOL-LUMP",
             "assumptions": ["fluid properties frozen at the steady operating state", "inhibitor retained in place with its operating aqueous mass fraction",
                             "insulation thermal mass excluded (conservative)", "result is the time to reach the hydrate equilibrium temperature, not the time to plug"]}
    return {"block": block, "items": items}


def evaluate_criteria(criteria, derived, case_roles):
    """Status of every criterion per case and overall; values come from the derived extrema (location retained)."""
    out = []
    for c in criteria:
        qid = QUANTITIES.get(c["quantity"])
        row = {"id": c["id"], "text": c["text"], "quantity": c["quantity"], "operator": c["operator"], "value": float(c["value"]), "unit": c["unit"],
               "source": c["source"], "requirement_class": c["requirement_class"], "applicability": c["applicability"], "applies_to": list(c["applies_to"]),
               "by_case": {}}
        for cid, d in derived.items():
            if cid not in c["applies_to"]:
                row["by_case"][cid] = {"status": "NOT APPLICABLE"}
                continue
            if qid is None:
                row["by_case"][cid] = {"status": "NOT ASSESSED", "reason": "no calculator for quantity '%s'" % c["quantity"]}
                continue
            ex = d["extrema"][qid[0]]
            if ex["value"] is None:
                row["by_case"][cid] = {"status": "UNAVAILABLE", "reason": ex.get("status", "no result"), "extremum": qid[0]}
                continue
            try:
                v = convert(ex["value"], ex["unit"], c["unit"]) if ex["unit"] not in ("-", "") else ex["value"]
            except UnitError as exc:
                row["by_case"][cid] = {"status": "UNIT ERROR", "reason": str(exc), "extremum": qid[0]}
                continue
            lim = float(c["value"])
            ok = {">=": v >= lim, "<=": v <= lim, ">": v > lim, "<": v < lim}[c["operator"]]
            row["by_case"][cid] = {"status": "PASS" if ok else "FAIL", "value": v, "limit_margin": (v - lim) if c["operator"] in (">=", ">") else (lim - v),
                                   "extremum": qid[0], "path": ex["path"], "chainage_m": ex["chainage_m"], "segment_id": ex["segment_id"]}
        sts = [v["status"] for v in row["by_case"].values()]
        row["overall"] = ("FAIL" if "FAIL" in sts else "UNAVAILABLE" if any(s in ("UNAVAILABLE", "UNIT ERROR", "NOT ASSESSED") for s in sts)
                          else "PASS" if "PASS" in sts else "NOT APPLICABLE")
        out.append(row)
    return out


def build_threat_matrix(basis, derived, results, events):
    """Applicability matrix: every threat is calculated (with evidence), not applicable (with reason) or open/unsupported."""
    declared = basis.get("threats") or {}
    all_ok = all(r["status"] == "converged" for r in results.values())
    rows = []
    for tid, name, default, methods in fa_methods.THREATS:
        decl = declared.get(tid)
        if decl and decl["status"] == "not_applicable":
            status, reason, ev = "not_applicable", decl["reason"], []
        elif default == "calculated":
            if all_ok:
                status, reason = "calculated", "calculated for every case with the methods listed; see the evidence and the method limits"
            else:
                status, reason = "open", "at least one case did not converge; the threat cannot be reported as assessed"
            ev = {"T-HYD-STEADY": ["v_mix_max", "holdup_max"], "T-CAP": ["source_pressure_max"], "T-HYDRATE": ["hyd_margin_min", "hyd_margin_unin_min"],
                  "T-THERMAL": ["host_arrival_t"], "T-COOLDOWN": ["cooldown_time_min"], "T-EROSION-VEL": ["erosional_ratio_max", "v_mix_max"]}.get(tid, [])
        else:
            status = default
            reason = (decl or {}).get("reason") or fa_methods.DEFAULT_OPEN_REASONS.get(tid, "no evidence was supplied")
            ev = []
        crit = [c for c in basis["acceptance_criteria"] if QUANTITIES.get(c["quantity"], (None,))[0] in ev]
        rows.append({"id": tid, "threat": name, "status": status, "reason": reason, "methods": methods, "evidence_extrema": ev,
                     "criteria": [c["id"] for c in crit], "external_events": [e["id"] for e in events if e.get("analysis") == "external" and tid in
                                                                              {"EVT-RESTART": "T-STARTUP", "EVT-PIG": "T-PIGGING"}.get(e["id"].rsplit("-", 1)[0], "")]})
    return rows


def compare_mitigations(basis, derived, results):
    """Baseline-versus-option deltas for the mitigation options declared in the basis."""
    metrics = [("hyd_margin_min", "minimum hydrate margin", "K"), ("host_arrival_t", "host arrival temperature", "C"),
               ("source_pressure_max", "maximum source pressure", "bara"), ("cooldown_time_min", "minimum cooldown time to hydrate temperature", "h")]
    out = []
    for m in basis.get("mitigation_options") or []:
        b, o = derived.get(m["baseline"]), derived.get(m["case"])
        if b is None or o is None:
            out.append(dict(m, status="open", deltas=[], note="baseline or option case missing"))
            continue
        deltas = []
        for eid, label, unit in metrics:
            bv, ov = b["extrema"][eid]["value"], o["extrema"][eid]["value"]
            deltas.append({"metric": eid, "label": label, "unit": unit, "baseline": bv, "option": ov, "delta": None if None in (bv, ov) else ov - bv})
        bs, os_ = b["summary"], o["summary"]
        deltas.append({"metric": "lean_inhibitor_total_kg_h", "label": "lean MEG rate", "unit": "kg/h", "baseline": bs["lean_inhibitor_total_kg_h"],
                       "option": os_["lean_inhibitor_total_kg_h"], "delta": os_["lean_inhibitor_total_kg_h"] - bs["lean_inhibitor_total_kg_h"]})
        out.append({"id": m["id"], "title": m["title"], "case": m["case"], "baseline": m["baseline"], "status": "calculated", "deltas": deltas,
                    "remaining_uncertainty": m.get("uncertainty", "not stated")})
    return out


def _dotget(obj, path):
    cur = obj
    for part in str(path).split("."):
        if isinstance(cur, dict) and part in cur:
            cur = cur[part]
        elif isinstance(cur, list) and part.lstrip("-").isdigit() and -len(cur) <= int(part) < len(cur):
            cur = cur[int(part)]
        else:
            return None
    return cur


def build_dataset(inputs, results, benchmarks, mesh, timings, repo_root, cache_info=None):
    """Assemble the canonical dataset from loaded inputs and case results (no JVM needed)."""
    basis, route_model, cases = inputs["basis"], inputs["route_model"], inputs["cases"]
    cases_doc = inputs["cases_doc"]
    derived = {cid: derive_case(res, route_model, next(c for c in cases if c["id"] == cid), basis) for cid, res in results.items()}
    findings = []
    for cid, d in derived.items():
        findings.extend(d["findings"])
        findings.extend(dict(f, where=cid) for f in results[cid].get("findings", []))
    for f in route_model["findings"]:
        findings.append(dict(f, source="route"))
    res_out = {}
    for cid, r in results.items():
        r2 = copy.deepcopy(r)
        d = derived[cid]
        r2["profiles_derived"] = d["profile_extra"]
        r2["extrema"] = d["extrema"]
        r2["summary"] = d["summary"]
        r2["cooldown"] = d["cooldown"]
        res_out[cid] = r2
    events = []
    for ev in cases_doc.get("events") or []:
        e = dict(ev)
        if ev["analysis"] == "lumped_cooldown":
            gov = (derived[ev["case"]]["cooldown"] or {}).get("governing")
            e["status"] = "calculated" if gov else "open"
            e["result"] = {"governing_time_h": gov["value"] if gov else None, "path": gov["path"] if gov else None, "chainage_m": gov["chainage_m"] if gov else None}
        else:
            e["status"] = "external_open"
            e["result"] = None
            e["note"] = "no qualified solver in this toolkit; hand-off package written; external result NOT supplied (open)"
        events.append(e)
    criteria = evaluate_criteria(basis["acceptance_criteria"], derived, {c["id"]: c["role"] for c in cases})
    qual_rows, qual_note = fa_methods.parse_evidence_matrix(repo_root)
    seg_out = []
    for s in route_model["segments"]:
        s2 = {k: v for k, v in s.items() if k not in ("plan_points",)}
        seg_out.append(s2)
    dataset = {
        "schema": SCHEMA,
        "meta": {"title": basis["document"]["title"], "document_number": basis["document"]["document_number"], "revision": basis["document"]["revision"],
                 "created": datetime.datetime.now().replace(microsecond=0).isoformat(), "tool_version": TOOL_VERSION, "solver_version": fa_solver.SOLVER_VERSION,
                 "neqsim_version": fa_solver.neqsim_version(), "model": basis["model"], "standard_conditions": basis["conventions"]["standard_conditions"],
                 "input_hashes": {"basis": canonical_hash(basis), "route": canonical_hash(inputs["route"]), "cases": canonical_hash(cases_doc)},
                 "cache": cache_info or {}},
        "document": basis["document"], "study": basis["study"], "conventions": basis["conventions"], "sources": basis["sources"], "fluids": basis["fluids"],
        "solver": basis["solver"],
        "route": {"status": inputs["route"].get("route_status"), "georeferenced": bool(inputs["route"].get("georeferenced")), "crs": inputs["route"].get("crs"),
                  "horizontal_datum": inputs["route"].get("horizontal_datum"), "vertical_datum": inputs["route"].get("vertical_datum"), "chainage": inputs["route"].get("chainage"),
                  "nodes": route_model["nodes"], "segments": seg_out, "paths": route_model["paths"], "sources": route_model["sources"], "sink": route_model["sink"],
                  "features": inputs["route"].get("features") or [], "transitions": _dedupe_transitions(route_model["transitions"]), "findings": route_model["findings"]},
        "topology_variants": inputs["variants"],
        "cases": [{k: v for k, v in c.items() if k in ("id", "label", "role", "stage", "description", "topology_variant", "baseline_case", "fluid", "layer_overrides",
                                                      "sources", "boundary", "inhibitor", "shut_in")} for c in cases],
        "results": res_out, "criteria": criteria, "events": events,
        "threats": build_threat_matrix(basis, derived, results, events), "mitigations": compare_mitigations(basis, derived, results),
        "benchmarks": benchmarks, "mesh_sensitivity": mesh,
        "qualification": {"methods": fa_methods.METHODS, "twofluid_matrix": qual_rows, "twofluid_matrix_note": qual_note},
        "assumptions": basis["assumptions"], "exclusions": basis["exclusions"], "data_gaps": basis["data_gaps"], "findings": findings,
        "timings_s": timings, "validation": None, "drawings": [], "artifacts": {}}
    dataset["facts"] = compute_facts(dataset)
    dataset["meta"]["dataset_hash"] = dataset_hash(dataset)
    return dataset


def _dedupe_transitions(transitions):
    seen, out = set(), []
    for t in transitions:
        k = (t["from_segment"], t["to_segment"])
        if k not in seen:
            seen.add(k)
            out.append(t)
    return out


def dataset_hash(dataset):
    """Hash of the content that the report shows (excludes validation, drawings, artifacts, timings, created time and the hash itself)."""
    skip = {"validation", "drawings", "artifacts", "timings_s"}
    body = {k: v for k, v in dataset.items() if k not in skip}
    body["meta"] = {k: v for k, v in dataset["meta"].items() if k not in ("dataset_hash", "created", "cache")}
    return canonical_hash(body, 24)


def compute_facts(ds):
    """Derived counts and roll-ups used by ``[[fact:key]]`` tokens."""
    segs = ds["route"]["segments"]
    hyd = [s for s in segs if s.get("hydraulic", True)]
    f = {"n_cases": len(ds["cases"]), "n_segments": len(segs), "n_hydraulic_segments": len(hyd), "n_assets": len(ds["route"]["nodes"]),
         "n_paths": len(ds["route"]["paths"]), "n_criteria": len(ds["criteria"]), "n_events": len(ds["events"]),
         "n_cases_converged": sum(1 for r in ds["results"].values() if r["status"] == "converged"),
         "route_length_km": max((sum(s["length_m"] for s in segs if s["id"] in chain) for chain in ds["route"]["paths"].values()), default=0.0) / 1000.0,
         "total_hydraulic_length_km": sum(s["length_m"] for s in hyd) / 1000.0,
         "n_criteria_fail": sum(1 for c in ds["criteria"] if c["overall"] == "FAIL"), "n_criteria_pass": sum(1 for c in ds["criteria"] if c["overall"] == "PASS"),
         "n_criteria_unavailable": sum(1 for c in ds["criteria"] if c["overall"] in ("UNAVAILABLE", "NOT ASSESSED")),
         "n_data_gaps": len(ds["data_gaps"]), "n_assumptions": len(ds["assumptions"])}
    for st in fa_methods.THREAT_STATUSES:
        f["n_threats_" + st] = sum(1 for t in ds["threats"] if t["status"] == st)
    f["n_threats"] = len(ds["threats"])
    f["n_benchmarks_pass"] = sum(1 for b in ds["benchmarks"] if b["status"] == "PASS")
    f["n_benchmarks_fail"] = sum(1 for b in ds["benchmarks"] if b["status"] == "FAIL")
    for cid, r in ds["results"].items():
        f["n_findings_" + cid] = len(r.get("findings", []))
    return f


# --- token resolver ---------------------------------------------------------------------------------
TOKEN_RE = re.compile(r"\[\[([^\]|]+)(?:\|([^\]|]*))?(?:\|([^\]]*))?\]\]")


# --- loading ----------------------------------------------------------------------------------------
def load_inputs(basis_path, route_path, cases_path, standards_path=None):
    """Load and check the four input files; returns the inputs dict and the list of input findings.

    Structural problems in the route or cases raise :class:`FlowAssuranceError` subclasses (the caller reports them and
    exits with code 2). Findings (errors/warnings) from the basis, route and cases checks are returned for the validator.
    """
    import psr_basis

    for label, path in (("basis", basis_path), ("route", route_path), ("cases", cases_path)):
        if not path or not os.path.isfile(path):
            raise FlowAssuranceError("%s file not found: %s" % (label, path))
    basis = psr_basis.load_structured(basis_path)
    route = psr_basis.load_structured(route_path)
    cases_doc = psr_basis.load_structured(cases_path)
    findings = check_basis(basis)
    route_findings, route_model = fa_geometry.check_route(route)
    for coll, label in ((route.get("assets") or [], "asset"), (route.get("segments") or [], "segment")):
        for item in coll:
            if not ID_RE.match(str(item.get("id"))):
                findings.append(_finding("error", "ID-CHARS", "%s id %r contains characters other than letters, digits, '_' and '-'" % (label, item.get("id")), str(item.get("id"))))
    cases_findings, cases, variants = check_cases(cases_doc, route_model, basis)
    standards = None
    if standards_path:
        standards = psr_basis.load_structured(standards_path)
        standards = standards.get("standards", []) if isinstance(standards, dict) else standards
        findings.extend(check_standards_register(standards))
    inputs = {"basis": basis, "route": route, "route_model": route_model, "cases_doc": cases_doc, "cases": cases, "variants": variants, "standards": standards,
              "paths": {"basis": basis_path, "route": route_path, "cases": cases_path, "standards": standards_path}}
    return inputs, findings + cases_findings + route_findings


class FaResolver:
    """Resolves ``[[...]]`` tokens against the dataset; records every use, error and unit problem."""

    def __init__(self, dataset):
        self.ds = dataset
        self.used, self.errors, self.unit_errors = [], [], []
        self._segs = {s["id"]: s for s in dataset["route"]["segments"]}
        self._crit = {c["id"]: c for c in dataset["criteria"]}
        self._bm = {b["id"]: b for b in dataset["benchmarks"]}

    def value(self, ref):
        """Return ``(value, unit)`` for a reference or raise ``KeyError`` when it does not resolve."""
        parts = ref.split(":")
        kind = parts[0]
        ds = self.ds
        try:
            if kind == "res":
                path = parts[2]
                val = _dotget(ds["results"][parts[1]], path)
                return val, suffix_unit(path.split(".")[-1])
            if kind == "ext":
                rec = ds["results"][parts[1]]["extrema"][parts[2]]
                field = parts[3]
                return rec[field], (rec["unit"] if field == "value" else ("m" if field == "chainage_m" else None))
            if kind == "crit":
                c = self._crit[parts[1]]
                if len(parts) == 3:
                    return c[parts[2]], (c["unit"] if parts[2] == "value" else None)
                return c["by_case"][parts[2]][parts[3]], (c["unit"] if parts[3] in ("value", "limit_margin") else None)
            if kind == "seg":
                path = parts[2]
                return _dotget(self._segs[parts[1]], path), suffix_unit(path.split(".")[-1])
            if kind == "segres":
                path = parts[3]
                return ds["results"][parts[1]]["segments"][parts[2]][path], suffix_unit(path)
            if kind == "bm":
                return self._bm[parts[1]][parts[2]], self._bm[parts[1]].get("unit") if parts[2] in ("value", "reference", "deviation") else None
            if kind == "fact":
                return ds["facts"][parts[1]], None
            if kind == "meta":
                return _dotget(ds["meta"], parts[1]), None
            if kind == "basis":
                return _dotget(ds, parts[1]), None
        except (KeyError, IndexError, TypeError):
            raise KeyError(ref)
        raise KeyError(ref)

    def text(self, ref, fmt=None, unit_spec=None):
        try:
            val, unit = self.value(ref)
        except KeyError:
            self.errors.append(ref)
            return "n/a"
        if unit_spec:
            want = unit_spec.replace("unit=", "").strip()
            try:
                if isinstance(val, (int, float)) and not isinstance(val, bool):
                    if unit is None:
                        raise UnitError("no unit is recorded for %s" % ref)
                    val = convert(val, unit, want)
                    unit = want
            except UnitError as exc:
                self.unit_errors.append({"ref": ref, "message": str(exc)})
                return "n/a"
        if val is None:
            self.used.append({"ref": ref, "value": None, "text": "unavailable", "unit": unit})
            return "unavailable"
        if isinstance(val, bool):
            out = "yes" if val else "no"
        elif isinstance(val, (int, float)):
            if not math.isfinite(val):
                out = "n/a"
            elif fmt and fmt.startswith("sig"):
                from psr_report import significant

                out = significant(float(val), int(fmt[3:]))
            elif fmt:
                out = format(val, fmt)
            else:
                from psr_extract import fmt_value

                out = "%d" % val if float(val).is_integer() and abs(val) < 1e7 else fmt_value(float(val), 4)
        else:
            out = str(val)
        self.used.append({"ref": ref, "value": val if isinstance(val, (int, float, str, bool)) else str(val), "text": out, "unit": unit, "format": fmt})
        return out

    def expand(self, text):
        return TOKEN_RE.sub(lambda m: self.text(m.group(1), (m.group(2) or None), (m.group(3) or None)), str(text or ""))
