"""Drawing package orchestration for the flow-assurance study report.

``build_package(ds, out_dir, ...)`` writes SVG (editable source), one vector PDF with all sheets, PNG previews and a
machine-readable layout specification (``layout_spec.json``) from the canonical dataset - it never calls the solver.

Sheets (A3 landscape, title block and revision table from the shared kit):

=====  ==================================================================  =====
Code   Content                                                              Type
=====  ==================================================================  =====
DWG-01 Field / network schematic (connectivity, battery limits)             A
DWG-02 Pipeline route plan (coordinates, KP marks, scale; or schematic)     B
DWG-03 Longitudinal profile and aligned P/T plots, first path               C
DWG-04 Longitudinal profile and aligned P/T plots, second path              C
DWG-05 Riser arrangement and PLEM tie-in detail (schematic)                 D
DWG-06 Pipe and thermal cross-sections with U-value basis                   E
DWG-07 Velocity, erosional ratio and holdup along the route                 F
DWG-08 Hydrate margin and critical sections                                 F
DWG-09 Operating-limit summary by case                                      F
DWG-10 Transient / event sheet (cooldown, external hand-offs)               G
=====  ==================================================================  =====
"""
import json
import os

import fa_draw
import fa_sheets_net
import fa_sheets_results
from psr_diagrams import kit as K


def title_block(ds, readiness_label, readiness_note):
    """Title-block fields from the dataset document; checker and approver stay as supplied (``not assigned`` by default)."""
    d = ds["document"]
    return {"company": d["company"], "project": d["project"], "title": d["title"], "document_number": d["document_number"], "revision": str(d["revision"]),
            "issue_status": readiness_label, "date": d["date"], "drawn_by": d.get("prepared_by", "not assigned"),
            "checked_by": d.get("checked_by", "not assigned"), "approved_by": d.get("approved_by", "not assigned"),
            "revision_history": [{"rev": r.get("rev"), "date": r.get("date"), "description": r.get("description")} for r in d.get("revision_history", [])],
            "readiness_note": readiness_note}


def sheet_plan(ds):
    """Sheet list (code, title, builder) for a dataset; the second profile sheet exists only for a second path."""
    paths = list(ds["route"]["paths"])
    plan = [("DWG-01", "Field and network schematic (connectivity)", fa_sheets_net.network_schematic),
            ("DWG-02", "Pipeline route plan", fa_sheets_net.route_plan),
            ("DWG-03", "Longitudinal profile with pressure and temperature - path %s" % paths[0], lambda s, ds_, r, ctx: fa_sheets_results.profile_sheet(s, ds_, r, ctx, 0))]
    if len(paths) > 1:
        plan.append(("DWG-04", "Longitudinal profile with pressure and temperature - path %s" % paths[1],
                     lambda s, ds_, r, ctx: fa_sheets_results.profile_sheet(s, ds_, r, ctx, 1)))
    plan += [("DWG-05", "Riser arrangement and PLEM tie-in detail (schematic)", fa_sheets_net.arrangement_detail),
             ("DWG-06", "Pipe and thermal cross-sections", fa_sheets_net.cross_sections),
             ("DWG-07", "Velocity, erosional ratio and liquid holdup along the route", fa_sheets_results.velocity_holdup_sheet),
             ("DWG-08", "Hydrate margin and critical sections", fa_sheets_results.hydrate_sheet),
             ("DWG-09", "Operating-limit summary by case", fa_sheets_results.limits_sheet),
             ("DWG-10", "Transient and event sheet (cooldown, external hand-offs)", fa_sheets_results.event_sheet)]
    return plan


def build_package(ds, resolver, out_dir, readiness_label, readiness_note, min_text=fa_draw.SMALL_TEXT_DEFAULT_MM, dpi=130, log=None):
    """Write all drawings; returns the manifest ``{sheets, svg, pdf, png, spec, qa_status, ...}``."""
    say = log or (lambda m: None)
    os.makedirs(out_dir, exist_ok=True)
    for sub in ("svg", "png"):
        os.makedirs(os.path.join(out_dir, sub), exist_ok=True)
    tb = title_block(ds, readiness_label, readiness_note)
    banner = "%s - %s" % (readiness_label, readiness_note)
    plan = sheet_plan(ds)
    ctx = {"styles": fa_draw.case_styles([c["id"] for c in ds["cases"]]), "codes": {code: i + 1 for i, (code, _t, _b) in enumerate(plan)},
           "total": len(plan), "min_text": min_text}
    sheets, svgs, specs = [], [], []
    for i, (code, title, builder) in enumerate(plan):
        sh = fa_draw.Sheet(code, i + 1, len(plan), title, tb, "see sheet notes", banner, min_text=min_text)
        builder(sh, ds, resolver, ctx)
        qa = sh.qa()
        svg_path = os.path.join(out_dir, "svg", "%s_%s.svg" % (ds["meta"]["document_number"], code))
        K.write_text(svg_path, sh.render(["Issue purpose: %s; readiness %s (assigned by the toolkit gates, not by a person)." % (ds["document"]["issue_status"], readiness_label),
                                          "Run reference: dataset %s, model %s, NeqSim %s." % (ds["meta"]["dataset_hash"], ds["meta"]["model"]["name"], ds["meta"]["neqsim_version"]),
                                          "Units: pressure bara, temperature degC, lengths m, rates kg/s, time h."]))
        svgs.append(svg_path)
        specs.append(sh.spec(qa))
        sheets.append({"code": code, "number": i + 1, "title": title, "svg": svg_path, "qa": qa["status"], "drawing_number": sh.drawing_number})
        say("%s: %d annotations, QA %s" % (code, qa["n_annotations"], qa["status"]))
    pdf = os.path.join(out_dir, "%s_drawings.pdf" % ds["meta"]["document_number"])
    pngs = [os.path.join(out_dir, "png", "%s_%s.png" % (ds["meta"]["document_number"], s["code"])) for s in sheets]
    warnings = []
    K.to_pdf(svgs, pdf, pngs, warnings, dpi=dpi)
    for s, p in zip(sheets, pngs):
        s["png"] = p
    spec_path = os.path.join(out_dir, "layout_spec.json")
    with open(spec_path, "w", encoding="utf-8") as fh:
        json.dump({"version": 1, "dataset_hash": ds["meta"]["dataset_hash"], "format": "A3 landscape", "min_text_mm": min_text, "sheets": specs}, fh, indent=1)
    return {"sheets": sheets, "pdf": pdf, "spec": spec_path, "warnings": warnings, "qa_status": "PASS" if all(s["qa"] == "PASS" for s in sheets) else "FAIL",
            "specs": specs, "readiness": readiness_label}
