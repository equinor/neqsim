"""Drawing package orchestration for the SURF study.

``build_package(ds, resolver, out_dir, ...)`` writes SVG (editable source), one vector PDF with every sheet, PNG previews and a
machine-readable layout specification (``layout_spec.json``) from the canonical dataset - it never calls the solver.

Sheets (A3 landscape, shared title block and revision table):

=====  =====================================================================  ====
Code   Content                                                                Type
=====  =====================================================================  ====
DWG-01 Overall field layout plan (route plan, KP marks, scale)                Plan
DWG-02 SURF process/network schematic (modelled connectivity, both services)  Schematic
DWG-03 Production process-flow and functional sheet with operating overlay    Functional
DWG-04 Injection process-flow and functional sheet with operating overlay     Functional
DWG-05 Production route longitudinal profile (elevation, p, T, erosion)       Profile
DWG-06 Injection route longitudinal profile                                   Profile
DWG-07 Riser configuration and elevation (concept schematic, not analysed)    Detail
DWG-08 Template, manifold, PLEM and PLET arrangement (plan, isometric)        Detail
DWG-09 Jumper and tie-in details                                              Detail
DWG-10 Umbilical architecture and conceptual cross-section                    Detail
DWG-11 Flowline and riser thermal cross-sections                              Detail
DWG-12 Installation and tie-in sequence                                       Sequence
DWG-13 Design-result sheet (criteria, envelopes, arrival conditions)          Results
DWG-14 Concept comparison                                                     Results
DWG-15 Quantities and interface overview, drawing register                    Register
=====  =====================================================================  ====
"""
import json
import os

import fa_draw
import surf_sheets_a as A
import surf_sheets_b as B
from psr_diagrams import kit as K


def title_block(ds, readiness_label, readiness_note):
    """Title-block fields from the dataset document; checker and approver stay ``not assigned`` until a person is recorded."""
    d = ds["document"]
    return {"company": d["company"], "project": d["project"], "title": d["title"], "document_number": d["document_number"], "revision": str(d["revision"]),
            "issue_status": readiness_label, "date": d["date"], "drawn_by": d.get("prepared_by", "not assigned"), "checked_by": d.get("checked_by", "not assigned"),
            "approved_by": d.get("approved_by", "not assigned"),
            "revision_history": [{"rev": r.get("rev"), "date": r.get("date"), "description": r.get("description")} for r in d.get("revision_history", [])], "readiness_note": readiness_note}


def sheet_plan(ds):
    """Sheet list (code, title, builder)."""
    return [("DWG-01", "Overall field layout plan", A.field_plan), ("DWG-02", "SURF process and network schematic", A.network_schematic),
            ("DWG-03", "Production process-flow and functional sheet", A.production_process), ("DWG-04", "Injection process-flow and functional sheet", A.injection_process),
            ("DWG-05", "Production route longitudinal profile", A.production_profile), ("DWG-06", "Injection route longitudinal profile", A.injection_profile),
            ("DWG-07", "Riser configuration and elevation (concept schematic)", B.riser_sheet), ("DWG-08", "Template, manifold, PLEM and PLET arrangement", B.arrangement_sheet),
            ("DWG-09", "Jumper and tie-in details", B.jumper_sheet), ("DWG-10", "Umbilical architecture and conceptual cross-section", B.umbilical_sheet),
            ("DWG-11", "Flowline and riser thermal cross-sections", B.thermal_sheet), ("DWG-12", "Installation and tie-in sequence (conceptual)", B.installation_sheet),
            ("DWG-13", "Design-result sheet", B.results_sheet), ("DWG-14", "Concept comparison", B.comparison_sheet), ("DWG-15", "Quantities, interfaces and drawing register", B.quantities_sheet)]


MIN_TEXT_MM = 2.2


def build_package(ds, resolver, out_dir, readiness_label, readiness_note, min_text=MIN_TEXT_MM, dpi=130, log=None):
    """Write all drawings; returns the manifest ``{sheets, pdf, spec, qa_status, ...}``."""
    say = log or (lambda m: None)
    os.makedirs(out_dir, exist_ok=True)
    for sub in ("svg", "png"):
        os.makedirs(os.path.join(out_dir, sub), exist_ok=True)
    tb = title_block(ds, readiness_label, readiness_note)
    banner = "%s - %s" % (readiness_label, readiness_note)
    plan = sheet_plan(ds)
    ds["drawings_plan"] = [{"code": c, "title": t} for c, t, _b in plan]
    ds["reconciliation_summary"] = {"n": len(ds["registers"][ds["reference_concept"]]["reconciliation"]),
                                    "n_pass": sum(1 for r in ds["registers"][ds["reference_concept"]]["reconciliation"] if r["status"] == "PASS")}
    ctx = {"styles": fa_draw.case_styles([c["id"] for c in ds["cases"]]), "codes": {code: i + 1 for i, (code, _t, _b) in enumerate(plan)}, "total": len(plan), "min_text": min_text}
    sheets, svgs, specs = [], [], []
    for i, (code, title, builder) in enumerate(plan):
        sh = fa_draw.Sheet(code, i + 1, len(plan), title, tb, "see sheet notes", banner, min_text=min_text)
        builder(sh, ds, resolver, ctx)
        sh.tb["scale"] = sh.scale_text
        qa = sh.qa()
        svg_path = os.path.join(out_dir, "svg", "%s_%s.svg" % (ds["meta"]["document_number"], code))
        K.write_text(svg_path, sh.render(["Issue purpose: %s; readiness %s (assigned by the toolkit gates, not by a person)." % (ds["document"]["issue_status"], readiness_label),
                                          "Run reference: dataset %s, NeqSim %s, study %s." % (ds["meta"]["dataset_hash"], ds["meta"]["neqsim_version"], ds["meta"]["study_id"]),
                                          "Units: pressure bara, temperature degC, lengths m, rates kg/s. Source/model version: surf-study %s." % ds["meta"]["tool_version"]]))
        svgs.append(svg_path)
        specs.append(sh.spec(qa))
        sheets.append({"code": code, "number": i + 1, "title": title, "svg": svg_path, "qa": qa["status"], "drawing_number": sh.drawing_number, "items": sh.items})
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
    return {"sheets": sheets, "pdf": pdf, "spec": spec_path, "warnings": warnings, "qa_status": "PASS" if all(s["qa"] == "PASS" for s in sheets) else "FAIL", "specs": specs,
            "readiness": readiness_label}
