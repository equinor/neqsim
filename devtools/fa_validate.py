"""Validation gates and readiness of the flow-assurance study report (machine-readable, fails closed).

``validate_all`` returns one entry per check (PASS / FAIL / WARN / SKIP) and never raises for a failed check. The readiness
label follows the checks: ``NOT_READY`` (any FAIL: diagnostic package only), ``SCREENING`` (no FAIL; evidence gaps or
synthetic inputs remain) or ``REVIEWED`` (only when a person supplied a review record, no gaps remain and the data is not
synthetic). Software never assigns a reviewer, a checker or an approval.
"""
import datetime
import json
import math
import os
import re

import fa_draw
import fa_study
from fa_units import suffix_unit

STD_MAX_AGE_DAYS = 90
TOL_MASS = 1.0e-6
TOL_ENERGY = 1.0e-6
TOL_BOUNDARY_BAR = 0.1
INTERP_TOL_K = 0.5
REQUIRED_HEADINGS = ["1 Executive summary", "2 Scope", "3 Design and operating basis", "4 System description", "5 Fluid and PVT", "6 Methods", "7 Case matrix",
                     "8 Steady-state hydraulics", "9 Transient and event", "10 Hydrate management", "11 Wax, asphaltene", "12 Operating envelope", "13 Mitigation alternatives",
                     "14 Sensitivity", "15 Conclusions", "16 Appendices"]
BAD_TOKENS = re.compile(r"\b(nan|NaN|inf|None)\b|\[\[|\]\]")


def _check(cid, description, status, detail=""):
    return {"id": cid, "description": description, "status": status, "detail": detail}


def _fail_warn(cid, desc, errors, warnings, ok_detail=""):
    if errors:
        return _check(cid, desc, "FAIL", "; ".join(errors[:10]))
    if warnings:
        return _check(cid, desc, "WARN", "; ".join(warnings[:10]))
    return _check(cid, desc, "PASS", ok_detail)


def _finite_series(vals):
    return all(v is None or (isinstance(v, (int, float)) and math.isfinite(v)) for v in vals)


def check_inputs(ds, input_findings, standards):
    """Checks that need only the dataset and the input findings (run before drawings are built)."""
    checks = []
    errs = [f for f in input_findings if f["level"] == "error"]
    warns = [f for f in input_findings if f["level"] == "warning"]
    checks.append(_fail_warn("FA-INPUTS", "Basis, route, cases and standards register are complete and consistent", ["%s %s" % (f["code"], f["message"]) for f in errs],
                             ["%s %s" % (f["code"], f["message"]) for f in warns], "%d input findings, none blocking" % len(input_findings)))
    ids = [a for a in ds["route"]["nodes"]] + [s["id"] for s in ds["route"]["segments"]]
    dup = sorted({i for i in ids if ids.count(i) > 1})
    checks.append(_check("FA-IDS", "Asset, segment and case identifiers are unique and token-safe", "FAIL" if dup or len({c["id"] for c in ds["cases"]}) != len(ds["cases"]) else "PASS",
                         "duplicates: %s" % dup if dup else "%d assets/segments, %d cases" % (len(ids), len(ds["cases"]))))
    n_geo_err = [f for f in ds["route"]["findings"] if f["level"] == "error"]
    n_geo_warn = [f for f in ds["route"]["findings"] if f["level"] == "warning"]
    checks.append(_fail_warn("FA-GEOM", "Route geometry: measured length versus 3D chord, elevations, connectivity, single sink", ["%s %s" % (f["code"], f["message"]) for f in n_geo_err],
                             ["%s %s" % (f["code"], f["message"]) for f in n_geo_warn], "%d paths to %s; information items: %d" % (len(ds["route"]["paths"]), ds["route"]["sink"],
                                                                                                                            len([f for f in ds["route"]["findings"] if f["level"] == "info"])))
    errs, warns = [], []
    for vid, v in ds["topology_variants"].items():
        if not v["active_paths"]:
            errs.append("variant %s has no active path" % vid)
    for c in ds["cases"]:
        v = ds["topology_variants"][c.get("topology_variant", "DEFAULT")]
        r = ds["results"].get(c["id"])
        if r and sorted(r["active_paths"]) != sorted(v["active_paths"]):
            errs.append("case %s: solved paths differ from the variant %s" % (c["id"], v["id"]))
    checks.append(_fail_warn("FA-TOPO", "Topology variants resolve to stable asset IDs and the solved paths match the variant", errs, warns, "%d variants" % len(ds["topology_variants"])))
    errs, warns = [], []
    known = {c["id"] for c in ds["cases"]}
    for cr in ds["criteria"]:
        for cid in cr["applies_to"]:
            if cid not in known:
                errs.append("%s applies to unknown case %s" % (cr["id"], cid))
        if cr["quantity"] not in fa_study.QUANTITIES:
            warns.append("%s: no calculator for quantity %s (reported as not assessed)" % (cr["id"], cr["quantity"]))
    calc = {t["id"]: t for t in ds["threats"]}
    for tid, qty in (("T-HYDRATE", "hydrate_margin_min"), ("T-COOLDOWN", "cooldown_time_min"), ("T-CAP", "source_pressure_max")):
        if calc.get(tid, {}).get("status") == "calculated" and not any(c["quantity"] == qty for c in ds["criteria"]):
            errs.append("threat %s is calculated but no acceptance criterion with quantity %s exists (missing required criterion)" % (tid, qty))
    checks.append(_fail_warn("FA-CRITERIA", "Required acceptance criteria exist with source, unit, inequality and applicability", errs, warns, "%d criteria" % len(ds["criteria"])))
    errs, warns = [], []
    for fid, f in ds["fluids"].items():
        tot = sum(c["mole_fraction"] for c in f["components"])
        if abs(tot - 1.0) > 1e-6 or any(c["mole_fraction"] < 0 for c in f["components"]):
            errs.append("fluid %s: mole fractions sum to %.8f" % (fid, tot))
    for cid, r in ds["results"].items():
        for n, s in r["sources"].items():
            if not (0.0 <= s["aqueous_inhibitor_wt"] < 1.0):
                errs.append("case %s source %s: aqueous inhibitor fraction %.3f outside [0,1)" % (cid, n, s["aqueous_inhibitor_wt"]))
            if s["lean_inhibitor_kg_s"] > 0 and s["purity_wt"] is not None and s["aqueous_inhibitor_wt"] > s["purity_wt"] + 1e-9:
                errs.append("case %s source %s: aqueous fraction exceeds the lean purity" % (cid, n))
    checks.append(_fail_warn("FA-FLUID", "Composition normalisation and water/inhibitor mass basis", errs, warns, "compositions normalised; inhibitor mass fractions within [0, purity]"))
    # solver
    errs, warns = [], []
    for cid, r in ds["results"].items():
        if r["status"] != "converged":
            errs.append("case %s did not converge" % cid)
        for s in r["solves"]:
            if not s["converged"]:
                errs.append("case %s: %s did not reach its tolerance" % (cid, s["what"]))
        for f in r.get("findings", []):
            if f["level"] == "error":
                errs.append("case %s: %s %s" % (cid, f["code"], f["message"]))
            elif f["level"] == "warning":
                warns.append("case %s: %s" % (cid, f["code"]))
    checks.append(_fail_warn("FA-SOLVER", "All cases converged within the declared tolerances; no domain or timeout findings", errs, warns, "%d cases converged" % len(ds["results"])))
    errs = []
    for cid, r in ds["results"].items():
        for pid, prof in r["profiles"].items():
            for key in ("p_bara", "t_c", "holdup", "chainage_m"):
                if not _finite_series(prof[key]):
                    errs.append("case %s path %s: non-finite %s" % (cid, pid, key))
            if prof["p_bara"] and min(prof["p_bara"]) <= 0:
                errs.append("case %s path %s: non-positive pressure" % (cid, pid))
            if prof["holdup"] and (min(prof["holdup"]) < -1e-9 or max(prof["holdup"]) > 1.0 + 1e-9):
                errs.append("case %s path %s: holdup outside [0, 1]" % (cid, pid))
            if any(b < a for a, b in zip(prof["chainage_m"][:-1], prof["chainage_m"][1:])):
                errs.append("case %s path %s: chainage not monotonic" % (cid, pid))
    checks.append(_check("FA-FINITE", "Positive finite pressures and properties, holdup in [0, 1], monotonic chainage", "FAIL" if errs else "PASS", "; ".join(errs[:8]) or "all profiles finite and physical"))
    errs, info = [], []
    for cid, r in ds["results"].items():
        jb = r.get("junction_balance")
        if jb:
            if jb["mass_rel_err"] > TOL_MASS:
                errs.append("case %s junction mass imbalance %.2e" % (cid, jb["mass_rel_err"]))
            if jb["enthalpy_rel_err"] > TOL_ENERGY:
                errs.append("case %s junction enthalpy imbalance %.2e" % (cid, jb["enthalpy_rel_err"]))
        tot_src = sum(s["hc_kg_s"] + s["water_kg_s"] + s["lean_inhibitor_kg_s"] for n, s in r["sources"].items() if n in r["streams"])
        sink = r["streams"].get(r["sink"])
        if sink and abs(sink["mass_kg_s"] - tot_src) / max(tot_src, 1e-12) > 1e-4:
            errs.append("case %s: host mass %.4f kg/s differs from the sum of sources %.4f kg/s" % (cid, sink["mass_kg_s"], tot_src))
    checks.append(_check("FA-BALANCE", "Steady mass balance (sources to host) and junction mass/enthalpy balance", "FAIL" if errs else "PASS",
                         "; ".join(errs[:8]) or "mass closes to 1e-4 overall and to %.0e at the junction; junction enthalpy to %.0e (pipe energy balance is part of the solver, not re-derived)" % (TOL_MASS, TOL_ENERGY)))
    errs = []
    for cid, r in ds["results"].items():
        sink = r["streams"].get(r["sink"])
        if sink and abs(sink["p_bara"] - r["host_p_bara"]) > TOL_BOUNDARY_BAR:
            errs.append("case %s: host pressure %.3f differs from the boundary %.3f" % (cid, sink["p_bara"], r["host_p_bara"]))
    checks.append(_check("FA-BOUNDARY", "Calculated host-interface pressure equals the specified boundary", "FAIL" if errs else "PASS", "; ".join(errs[:6]) or "within %.2f bar" % TOL_BOUNDARY_BAR))
    # benchmarks
    bm = ds["benchmarks"]
    an = [b for b in bm if b["kind"] == "analytic_verification"]
    ind = [b for b in bm if b["kind"] == "independent_validation"]
    errs = ["%s %s" % (b["id"], b["note"]) for b in an if b["status"] != "PASS"]
    checks.append(_check("FA-ANALYTIC", "Analytic checks: hydrostatics, single-phase friction, inner-area heat loss, cooldown ODE", "FAIL" if errs or not an else "PASS",
                         "; ".join(errs) or "%d analytic checks pass with predeclared tolerances" % len(an)))
    fails = [b["id"] for b in ind if b["status"] == "FAIL"]
    sec = [b["id"] for b in ind if "secondary" in str(b["provenance"])]
    checks.append(_check("FA-VALIDATION", "Independent benchmark with provenance and predeclared tolerance", "FAIL" if fails else ("WARN" if (sec or not ind) else "PASS"),
                         ("failed: %s" % fails) if fails else ("only secondary-source benchmarks (%s); no calibration and no field or laboratory validation (separate from verification)" % ", ".join(sec) if sec else "no independent benchmark")))
    ms = ds.get("mesh_sensitivity")
    checks.append(_check("FA-MESH", "Spatial sensitivity of the study mesh against a finer mesh", ("PASS" if ms and ms["status"] == "PASS" else "FAIL" if ms else "WARN"),
                         ("%s: study mesh vs finest within %s" % (ms["status"], json.dumps(ms["tolerance"]))) if ms else "not run"))
    errs, warns = [], []
    for cid, r in ds["results"].items():
        for k, c in r["hydrate_curves"].items():
            if c["n_available"] < 2:
                errs.append("case %s curve %s: fewer than 2 hydrate points" % (cid, k))
            if c.get("interpolation_check_k") is not None and c["interpolation_check_k"] > INTERP_TOL_K:
                warns.append("case %s curve %s: interpolation check %.2f K" % (cid, k, c["interpolation_check_k"]))
        if any(f["code"].startswith("HYDRATE") for f in r.get("findings", [])):
            warns.append("case %s: hydrate solver findings" % cid)
    checks.append(_fail_warn("FA-HYDRATE-GRID", "Hydrate curves available on the pressure grid with a ln(p) interpolation check below %.1f K" % INTERP_TOL_K, errs, warns,
                             "interpolation within %.1f K for all curves" % INTERP_TOL_K))
    # derived values and criteria recomputed independently
    errs = []
    for cid, r in ds["results"].items():
        for pid, prof in r["profiles"].items():
            der = r["profiles_derived"][pid]
            for i in range(len(prof["t_c"])):
                if der["t_hyd_c"][i] is not None and abs((prof["t_c"][i] - der["t_hyd_c"][i]) - (der["margin_k"][i])) > 1e-9:
                    errs.append("case %s path %s point %d: margin does not equal T - T_hyd" % (cid, pid, i))
                    break
        ex = r["extrema"]["hyd_margin_min"]
        vals = [(v, pid, prof["chainage_m"][i]) for pid, prof in r["profiles"].items() for i, v in enumerate(r["profiles_derived"][pid]["margin_k"]) if v is not None]
        if vals:
            mn = min(vals)
            if abs(mn[0] - ex["value"]) > 1e-9 or abs(mn[2] - ex["chainage_m"]) > 1e-6:
                errs.append("case %s: stored minimum hydrate margin or its chainage differs from the profile" % cid)
    for cr in ds["criteria"]:
        recomputed = fa_study.evaluate_criteria([{k: cr[k] for k in ("id", "text", "quantity", "operator", "value", "unit", "source", "requirement_class", "applicability", "applies_to")}],
                                                {cid: {"extrema": r["extrema"]} for cid, r in ds["results"].items()}, {})[0]
        if {k: v["status"] for k, v in recomputed["by_case"].items()} != {k: v["status"] for k, v in cr["by_case"].items()}:
            errs.append("criterion %s status differs from the recomputation" % cr["id"])
    checks.append(_check("FA-EXTREMA", "Margins, extrema (with location) and criteria status recompute from the stored profiles", "FAIL" if errs else "PASS", "; ".join(errs[:6]) or "recomputed values equal the stored ones"))
    # threats and events
    errs = []
    for t in ds["threats"]:
        if t["status"] not in ("calculated", "not_applicable", "open", "unsupported") or not t["reason"]:
            errs.append("threat %s has no valid status or reason" % t["id"])
        if t["status"] == "calculated" and not t["evidence_extrema"]:
            errs.append("threat %s is calculated without evidence" % t["id"])
    checks.append(_check("FA-THREATS", "Every threat is calculated with evidence, not applicable with a reason, or open/unsupported", "FAIL" if errs else "PASS",
                         "; ".join(errs[:6]) or "%d threats; %d open, %d unsupported are never reported as safe" % (len(ds["threats"]), ds["facts"]["n_threats_open"], ds["facts"]["n_threats_unsupported"])))
    errs = []
    for e in ds["events"]:
        if e["analysis"] == "external" and e["status"] == "external_open" and not e.get("handoff"):
            errs.append("event %s has no hand-off package" % e["id"])
    checks.append(_check("FA-EVENTS", "External events have a hand-off package; missing external results stay open", "FAIL" if errs else "PASS", "; ".join(errs) or "%d events" % len(ds["events"])))
    # standards
    errs, warns = [], []
    now = datetime.date.today()
    for r in standards or []:
        try:
            age = (now - datetime.date.fromisoformat(str(r["date_checked"])[:10])).days
        except ValueError:
            warns.append("%s: not checked (%s)" % (r["reference"], r["date_checked"]))
            continue
        if age > STD_MAX_AGE_DAYS:
            warns.append("%s: verification older than %d days" % (r["reference"], STD_MAX_AGE_DAYS))
        if str(r.get("assessment_status")) == "UNVERIFIED":
            warns.append("%s: UNVERIFIED" % r["reference"])
    checks.append(_fail_warn("FA-STD-VERIFY", "Standards editions verified recently; unverified rows are listed", errs, warns, "all rows verified within %d days" % STD_MAX_AGE_DAYS))
    return checks


def check_drawings(ds, manifest, resolver):
    """Drawing checks: layout QA, data agreement, alignment, files and banners."""
    checks = []
    bad = [s["code"] for s in manifest["sheets"] if s["qa"] != "PASS"]
    detail = []
    for sp in manifest["specs"]:
        q = sp["qa"]
        if q["status"] != "PASS":
            detail.append("%s: %d overlaps, %d out of bounds, %d small texts" % (sp["code"], len(q["text_overlaps"]), len(q["out_of_bounds"]), len(q["small_text"])))
    checks.append(_check("FA-DRAW-QA", "Drawing layout: no text overlaps, nothing outside the content area, minimum text height %.1f mm" % fa_draw.SMALL_TEXT_DEFAULT_MM, "FAIL" if bad else "PASS",
                         "; ".join(detail) or "%d sheets, %d annotations checked" % (len(manifest["sheets"]), sum(sp["qa"]["n_annotations"] for sp in manifest["specs"]))))
    errs = []
    n_bound = 0
    for sp in manifest["specs"]:
        for a in sp["annotations"]:
            if a.get("ref"):
                n_bound += 1
                ref, fmt, unit = (a["ref"].split("|") + ["", ""])[:3]
                txt = resolver.text(ref, fmt or None, unit or None)
                if txt != a["value_text"] or a["value_text"] not in a["text"]:
                    errs.append("%s %s: shows %r but the dataset gives %r" % (sp["code"], a["id"], a["value_text"], txt))
        for it in sp["items"]:
            if it["type"] == "edge":
                segs = {s["id"]: s for s in ds["route"]["segments"]}
                tot = sum(segs[i]["length_m"] for i in it["segments"])
                if abs(tot - it["length_m"]) > 1e-6:
                    errs.append("%s edge %s length differs from the route" % (sp["code"], it["segments"]))
            if it["type"] == "asset" and it["id"] not in ds["route"]["nodes"]:
                errs.append("%s draws unknown asset %s" % (sp["code"], it["id"]))
        for pn in sp["panels"]:
            for s in pn["series"]:
                src = s.get("source")
                if src in ("profiles", "profiles_derived") and s.get("case"):
                    r = ds["results"][s["case"]]
                    arr = (r["profiles"] if src == "profiles" else r["profiles_derived"])[s["path"]][s["quantity"]]
                    if fa_draw.hash_values(arr) != s["y_hash"]:
                        errs.append("%s panel %s series %s: plotted values differ from the dataset" % (sp["code"], pn["id"], s["label"]))
                if src == "cooldown":
                    h = ds["results"][s["case"]]["cooldown"]["histories"][0]
                    keep = [i for i, t in enumerate(h["t_h"]) if t <= pn["x_range"][1]]
                    if fa_draw.hash_values([h["t_c"][i] for i in keep]) != s["y_hash"]:
                        errs.append("%s panel %s: cooldown series differs from the dataset" % (sp["code"], pn["id"]))
    checks.append(_check("FA-DRAW-DATA", "Drawing annotations and plotted series agree with the dataset (%d number-bound annotations)" % n_bound, "FAIL" if errs else "PASS", "; ".join(errs[:6]) or "all bound numbers and series equal the dataset"))
    errs = []
    for sp in manifest["specs"]:
        ch = [p for p in sp["panels"] if p["x_quantity"] == "chainage_m"]
        for p in ch[1:]:
            if abs(p["x0"] - ch[0]["x0"]) > 1e-9 or abs(p["w"] - ch[0]["w"]) > 1e-9 or p["x_range"] != ch[0]["x_range"]:
                errs.append("%s: panels %s and %s are not aligned on the same chainage axis" % (sp["code"], ch[0]["id"], p["id"]))
    checks.append(_check("FA-DRAW-ALIGN", "Plots that share the chainage axis use identical x position, width and range", "FAIL" if errs else "PASS", "; ".join(errs) or "aligned"))
    errs = []
    for s in manifest["sheets"]:
        for key in ("svg", "png"):
            if not os.path.isfile(s[key]) or os.path.getsize(s[key]) < 1000:
                errs.append("%s missing or empty: %s" % (key, s[key]))
    try:
        import pymupdf

        doc = pymupdf.open(manifest["pdf"])
        if len(doc) != len(manifest["sheets"]):
            errs.append("PDF has %d pages for %d sheets" % (len(doc), len(manifest["sheets"])))
        for i, page in enumerate(doc):
            if not page.get_text().strip():
                errs.append("PDF page %d has no text" % (i + 1))
            if not page.get_fonts() and page.get_text().strip():
                errs.append("PDF page %d lists no font" % (i + 1))
        for s in manifest["sheets"]:
            pix = pymupdf.Pixmap(s["png"])
            samples = pix.samples
            if len(set(samples[::997])) < 3:
                errs.append("PNG %s looks empty" % os.path.basename(s["png"]))
    except Exception as exc:  # noqa: BLE001
        errs.append("PDF/PNG inspection failed: %s" % exc)
    checks.append(_check("FA-DRAW-FILES", "SVG sources, vector PDF (text and fonts) and PNG previews exist and are not empty", "FAIL" if errs else "PASS", "; ".join(errs[:6]) or "%d SVG, 1 PDF, %d PNG" % (len(manifest["sheets"]), len(manifest["sheets"]))))
    errs = []
    sp2 = next((sp for sp in manifest["specs"] if sp["code"] == "DWG-02"), None)
    if not ds["route"]["georeferenced"] and sp2 and not any("NOT GEOREFERENCED" in a.get("text", "") for a in sp2["annotations"]):
        errs.append("plan sheet lacks the NOT GEOREFERENCED banner")
    for sp in manifest["specs"]:
        if not sp["banner"] or ds["document"]["readiness"] and manifest["readiness"] not in sp["banner"]:
            errs.append("%s: banner does not state the readiness %s" % (sp["code"], manifest["readiness"]))
        if sp["title_block"].get("checked_by") != ds["document"].get("checked_by", "not assigned") or sp["title_block"].get("approved_by") != ds["document"].get("approved_by", "not assigned"):
            errs.append("%s: checker/approver fields differ from the document control" % sp["code"])
    checks.append(_check("FA-DRAW-FRONT", "Banners state readiness and not-georeferenced status; checker and approver fields are only what a person supplied", "FAIL" if errs else "PASS", "; ".join(errs[:6]) or "banners and title blocks consistent"))
    return checks


def _docx_text(path):
    from docx import Document

    doc = Document(path)
    parts = [p.text for p in doc.paragraphs]
    for t in doc.tables:
        for r in t.rows:
            parts.append(" ".join(c.text for c in r.cells))
    heads = [p.text for p in doc.paragraphs if p.style is not None and p.style.name.startswith("Heading")]
    return "\n".join(parts), heads


def check_document(ds, report_info, docx_path, facts_path, pdf_path, resolver_class=fa_study.FaResolver):
    """Report checks: references, stale facts, sections, figures/tables, hidden open items, placeholders, PDF."""
    checks = []
    with open(facts_path, encoding="utf-8") as fh:
        facts = json.load(fh)
    errs = list(facts["unresolved_references"]) + [e["ref"] for e in facts["unit_errors"]]
    checks.append(_check("FA-DOC-REFS", "Every report token resolves and every requested unit conversion is compatible", "FAIL" if errs else "PASS", "unresolved: %s" % errs[:8] if errs else "%d tokens resolved" % len(facts["resolved_references"])))
    errs = []
    if facts["dataset_hash"] != ds["meta"]["dataset_hash"]:
        errs.append("report was built from dataset %s, current dataset is %s (stale facts)" % (facts["dataset_hash"], ds["meta"]["dataset_hash"]))
    rs = resolver_class(ds)
    for u in facts["resolved_references"]:
        txt = rs.text(u["ref"], u.get("format"), u.get("unit_req"))
        if txt != u["text"]:
            errs.append("%s: report shows %r, dataset now gives %r" % (u["ref"], u["text"], txt))
            if len(errs) > 5:
                break
    checks.append(_check("FA-DOC-FACTS", "Report facts equal the current dataset (no stale values)", "FAIL" if errs else "PASS", "; ".join(errs) or "dataset hash and %d resolved values match" % len(facts["resolved_references"])))
    text, heads = _docx_text(docx_path)
    missing = [h for h in REQUIRED_HEADINGS if not any(x.startswith(h) for x in heads)]
    checks.append(_check("FA-DOC-SECTIONS", "All 16 report chapters exist", "FAIL" if missing else "PASS", "missing: %s" % missing if missing else "%d headings" % len(heads)))
    hidden = [t["id"] for t in ds["threats"] if t["status"] in ("open", "unsupported") and t["id"] not in text]
    checks.append(_check("FA-DOC-OPEN", "Open and unsupported threats and external events are visible in the report", "FAIL" if hidden else "PASS", "not shown: %s" % hidden if hidden else "all open items listed"))
    bad = sorted({m.group(0) for m in BAD_TOKENS.finditer(text)})
    from psr_basis import placeholder_hits

    ph = placeholder_hits(text)
    checks.append(_check("FA-DOC-CLEAN", "No nan/inf/None, unexpanded tokens or placeholder text in the report", "FAIL" if (bad or ph) else "PASS", "found: %s" % (bad + ph)[:8] if (bad or ph) else "clean"))
    from docx import Document
    from docx.oxml.ns import qn

    doc = Document(docx_path)
    n_pic = len(doc.element.body.findall(".//" + qn("w:drawing")))
    n_alt = sum(1 for d in doc.element.body.findall(".//" + qn("wp:docPr")) if d.get("descr"))
    caps = [p.text for p in doc.paragraphs if p.style is not None and p.style.name == "Caption"]
    n_fig_cap = sum(1 for c in caps if c.startswith("Figure"))
    n_tab_cap = sum(1 for c in caps if c.startswith("Table"))
    errs = []
    if n_alt < n_pic:
        errs.append("%d of %d figures lack alt text" % (n_pic - n_alt, n_pic))
    if n_fig_cap != n_pic:
        errs.append("%d figures but %d figure captions" % (n_pic, n_fig_cap))
    if n_tab_cap < len(doc.tables) * 0.9:
        errs.append("%d tables but only %d table captions" % (len(doc.tables), n_tab_cap))
    checks.append(_check("FA-DOC-FIGURES", "Figures have alt text and numbered captions; tables have numbered captions", "FAIL" if errs else "PASS", "; ".join(errs) or "%d figures, %d tables" % (n_pic, len(doc.tables))))
    checks.append(_check("FA-DOC-PDF", "PDF export of the report exists", "PASS" if pdf_path and os.path.isfile(pdf_path) else "WARN",
                         "PDF written" if pdf_path and os.path.isfile(pdf_path) else "no PDF: Microsoft Word or LibreOffice was not available for the export (the DOCX is complete)"))
    return checks


def compute_readiness(ds, checks, basis_doc):
    """Readiness label from the checks, the declared review record and the evidence gaps (never assigned by software alone)."""
    fails = [c for c in checks if c["status"] == "FAIL"]
    open_t = [t["id"] for t in ds["threats"] if t["status"] in ("open", "unsupported")]
    synthetic = ds["route"]["status"] in ("synthetic", "schematic") or any(s["input_class"] == "synthetic" for s in ds["sources"])
    review = basis_doc.get("review") or {}
    if fails:
        return {"label": "NOT_READY", "note": "diagnostic package - %d validation check(s) failed; results must not be used" % len(fails), "publication_ready": False,
                "explanation": "Validation failed (%s). The package shows the failed and open findings and is not a deliverable." % ", ".join(c["id"] for c in fails[:6]), "reasons": [c["id"] for c in fails]}
    reasons = []
    if synthetic:
        reasons.append("synthetic or schematic inputs")
    if open_t:
        reasons.append("%d open or unsupported threats" % len(open_t))
    if ds["data_gaps"]:
        reasons.append("%d data gaps" % len(ds["data_gaps"]))
    if any(c["status"] == "WARN" for c in checks):
        reasons.append("validation warnings")
    requested = str(basis_doc.get("readiness", "SCREENING"))
    if requested == "REVIEWED" and review.get("reviewer") and review.get("record") and not reasons:
        return {"label": "REVIEWED", "note": "independently reviewed by %s (record %s); reviewer assertion, not software certification" % (review["reviewer"], review["record"]),
                "publication_ready": True, "explanation": "A review record was supplied by a person; the software only checked that no blocking evidence gap remains.", "reasons": []}
    note = "synthetic demonstration; not for design or construction" if synthetic else "screening results; evidence gaps remain open"
    return {"label": "SCREENING", "note": note, "publication_ready": False, "reasons": reasons,
            "explanation": "Screening only (%s). No independent review is recorded and the software assigns none." % "; ".join(reasons) if reasons else "Screening only; no independent review is recorded."}


def summarise(checks, readiness):
    n_fail = sum(1 for c in checks if c["status"] == "FAIL")
    n_warn = sum(1 for c in checks if c["status"] == "WARN")
    status = "FAIL" if n_fail else ("WARN" if n_warn else "PASS")
    return {"status": status, "n_checks": len(checks), "n_pass": sum(1 for c in checks if c["status"] == "PASS"), "n_fail": n_fail, "n_warn": n_warn, "checks": checks,
            "readiness": readiness, "generated": datetime.datetime.now().replace(microsecond=0).isoformat()}


def write_validation(result, out_dir):
    """Write ``validation_report.json`` and ``validation_report.md``; returns both paths."""
    os.makedirs(out_dir, exist_ok=True)
    jp = os.path.join(out_dir, "validation_report.json")
    with open(jp, "w", encoding="utf-8") as fh:
        json.dump(result, fh, indent=1)
    mp = os.path.join(out_dir, "validation_report.md")
    lines = ["# Flow-assurance study validation", "", "Overall: **%s** - readiness **%s**: %s" % (result["status"], result["readiness"]["label"], result["readiness"]["note"]), "",
             "| Check | Result | Detail |", "|---|---|---|"]
    for c in result["checks"]:
        lines.append("| %s | %s | %s |" % (c["id"], c["status"], c["detail"].replace("|", "/")[:300]))
    with open(mp, "w", encoding="utf-8") as fh:
        fh.write("\n".join(lines) + "\n")
    return jp, mp
