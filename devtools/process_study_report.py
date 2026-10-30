"""Process-study report pipeline: extraction, balances, diagrams, Word report, validation.

Command line (``python -m process_study_report --help``)::

    init-basis DIR                        copy basis and standards-register templates
    build --data process_data.json --basis basis.yaml --standards standards.yaml --out OUT
    validate --data ... --basis ... --standards ... --out OUT   (pre-build checks only)
    results-json --results R --summary S  add a process_study_report block to a task results.json
    demo OUT                              synthetic end-to-end example (needs the JVM)

Python API: :func:`run_pipeline`.
"""
import argparse
import json
import os
import shutil
import sys
import time

import psr_balances
import psr_basis
import psr_connectivity
import psr_diagrams
import psr_extract
import psr_report
import psr_validate


def default_equipment_data(data, case):
    """Short operating-data lines per equipment tag for one case (printed on the PFDs)."""
    streams = {s["id"]: s for s in data["streams"]}
    out = {}
    for u in data["equipment"]:
        r = u["results"].get(case, {})
        ex = r.get("extras") or {}
        ins = [streams[s]["results"][case] for s in u["inlet_streams"] if streams[s]["results"][case].get("available")]
        outs = [streams[s]["results"][case] for s in u["outlet_streams"] if streams[s]["results"][case].get("available")]
        lines = []
        cat = u["category"]
        if cat == "compressor" and r.get("power_kw") is not None:
            lines.append("W = %s kW" % psr_extract.fmt_value(r["power_kw"], 4))
            if ex.get("polytropic_efficiency") is not None:
                lines.append("eta_p = %s" % psr_extract.fmt_value(ex["polytropic_efficiency"], 3))
            if ex.get("pressure_ratio") is not None:
                lines.append("PR = %s" % psr_extract.fmt_value(ex["pressure_ratio"], 3))
        elif cat == "pump" and r.get("power_kw") is not None:
            lines.append("W = %s kW" % psr_extract.fmt_value(r["power_kw"], 3))
        elif cat in ("heater", "cooler", "exchanger") and r.get("duty_kw") is not None:
            lines.append("Q = %s kW" % psr_extract.fmt_value(r["duty_kw"], 4))
            if outs:
                lines.append("T out = %s C" % psr_extract.fmt_value(outs[0].get("t_c"), 3))
        elif cat == "separator" and ins:
            lines.append("P = %s bara" % psr_extract.fmt_value(ins[0].get("p_bara"), 4))
            lines.append("T = %s C" % psr_extract.fmt_value(ins[0].get("t_c"), 3))
        elif cat == "valve" and r.get("dp_bar") is not None:
            lines.append("dP = %s bar" % psr_extract.fmt_value(r["dp_bar"], 3))
        if lines:
            out[u["tag"]] = lines
    for tag, notes in psr_connectivity.diagram_notes(psr_connectivity.audit_connectivity(data)).items():
        out[tag] = list(out.get(tag, [])) + notes
    return out


def _title_block(basis, drawing_kind, case_note=None):
    d = basis["document"]
    return {"project": d["project"], "company": d["company"], "title": d["title"], "document_number": d["document_number"],
            "revision": d["revision"], "issue_status": d["issue_status"], "date": d["date"],
            "readiness_note": "%s - %s" % (d["readiness"].upper(), d.get("readiness_note", "not for design or construction")),
            "revision_history": [{"rev": r.get("rev"), "date": r.get("date"), "description": r.get("description")} for r in d.get("revision_history", [])],
            "omitted_systems": (basis.get("boundaries") or {}).get("unmodelled_systems", []),
            "notes": basis.get("diagram_notes", []), "drawn_by": d.get("prepared_by", "not assigned"),
            "checked_by": d.get("checked_by", "not assigned"), "approved_by": d.get("approved_by", "not assigned"),
            "case_note": case_note, "utility_note": "Utility media and drivers are not modelled; utility interfaces are generic"}


def build_diagrams(data, basis, out_dir, max_units_per_sheet=14, dpi=220):
    """Build BFD and PFD sheets; return the merged manifest used by the report and the validator."""
    ddir = os.path.join(out_dir, "diagrams")
    fdir = os.path.join(out_dir, "figures")
    os.makedirs(fdir, exist_ok=True)
    bfd = psr_diagrams.build_bfd(data, os.path.join(ddir, "bfd"), title_block=_title_block(basis, "BFD"), blocks=basis.get("bfd_blocks"))
    case = basis.get("diagram_equipment_case") or data["cases"][0]["id"]
    label = [c["label"] for c in data["cases"] if c["id"] == case][0]
    pfd = psr_diagrams.build_pfd(data, os.path.join(ddir, "pfd"),
                                 title_block=_title_block(basis, "PFD", "Equipment data shown for operating case %s (%s)" % (case, label)),
                                 sheets=basis.get("pfd_sheets"), max_units_per_sheet=max_units_per_sheet,
                                 layout_overrides=basis.get("layout_overrides"),
                                 equipment_data=default_equipment_data(data, case))
    pages = []
    bfd_pngs = psr_report.render_pdf_pages(bfd["pdf"], fdir, "bfd", dpi=dpi)
    pages.append((bfd_pngs[0], "Block flow diagram (drawing %s-BFD-01)" % basis["document"]["document_number"]))
    n = len(pfd["sheets"])
    pfd_pngs = psr_report.render_pdf_pages(pfd["pdf"], fdir, "pfd", dpi=dpi)
    for sheet, png in zip(pfd["sheets"], pfd_pngs):
        pages.append((png, "PFD sheet %s of %d: %s (drawing %s)" % (sheet["number"], n, sheet["title"], sheet["drawing_number"])))
    return {"bfd": bfd, "pfd": pfd, "pages": pages, "layout_review": review_diagram_layout(bfd, pfd, os.path.join(ddir, "layout_review"))}


def review_diagram_layout(bfd, pfd, review_dir):
    """Mandatory layout review of every BFD/PFD sheet (``pfd_layout_check``); returns a summary, never raises.

    Writes one review JSON per sheet plus ``summary.json`` and ``review.md`` to ``review_dir``. The agent
    ``pfd-layout-review`` must see PASS (or justified waivers) for every sheet before the report is issued.
    """
    try:
        import pfd_layout_check
    except ImportError:  # toolkit without the checker: report instead of failing the report build
        return {"status": "NOT_RUN", "reason": "pfd_layout_check not importable"}
    paths = [p for p in [bfd.get("svg")] + [s.get("svg") for s in pfd.get("sheets", [])] if p]
    reports = pfd_layout_check.review_many(paths, review_dir, kind="pfd")
    counts = {"PASS": 0, "WARN": 0, "FAIL": 0}
    for _, rep in reports:
        counts[rep.status] += 1
    return {"status": pfd_layout_check.worst_status(reports), "sheets": len(reports), "counts": counts, "dir": review_dir}


def run_pipeline(data, basis, standards, out_dir, use_word=True, max_units_per_sheet=14, tolerances=None, allow_fail=True):
    """Run the whole pipeline and return a summary dict (paths, validation status).

    Args:
        data: merged data set from :mod:`psr_extract`.
        basis: engineering basis dict (see ``templates/basis_template.yaml``).
        standards: standards register (list of dicts).
        out_dir: output folder (created).
        use_word: update fields and export a PDF through Microsoft Word when available.
        max_units_per_sheet: PFD packing limit.
        tolerances: balance-tolerance overrides.
        allow_fail: when False a failing validation raises ``RuntimeError``.
    """
    os.makedirs(out_dir, exist_ok=True)
    clock = [time.time()]
    timings = {}

    def lap(name):
        now = time.time()
        timings[name] = round(now - clock[0], 2)
        clock[0] = now

    data_dir = os.path.join(out_dir, "data")
    exports = psr_extract.write_outputs(data, data_dir)
    psr_connectivity.write_audit(psr_connectivity.audit_connectivity(data), data_dir)
    lap("exports")
    balances = psr_balances.compute_balances(data, tolerances or basis.get("tolerances"))
    with open(os.path.join(data_dir, "balances.json"), "w", encoding="utf-8") as fh:
        json.dump(balances, fh, indent=1)
    lap("balances")
    diagrams = build_diagrams(data, basis, out_dir, max_units_per_sheet=max_units_per_sheet)
    lap("diagrams")
    report_dir = os.path.join(out_dir, "report")
    os.makedirs(report_dir, exist_ok=True)
    docx_path = os.path.join(report_dir, "%s_rev%s.docx" % (basis["document"]["document_number"], basis["document"]["revision"]))
    fig_dir = os.path.join(out_dir, "figures")
    builder = psr_report.ReportBuilder(data, basis, standards, balances, diagrams, validation=None)
    builder.build(docx_path, fig_dir)
    lap("report")
    facts_path = os.path.join(report_dir, "report_facts.json")
    first = psr_validate.validate_all(data, basis, standards, balances, diagrams, docx_path, facts_path, data_dir)
    builder = psr_report.ReportBuilder(data, basis, standards, balances, diagrams, validation=first)
    builder.build(docx_path, fig_dir)
    final = psr_validate.validate_all(data, basis, standards, balances, diagrams, docx_path, facts_path, data_dir)
    pdf_path = os.path.join(report_dir, os.path.splitext(os.path.basename(docx_path))[0] + ".pdf")
    word_ok = psr_report.word_update_and_export(docx_path, pdf_path) if use_word else False
    if word_ok:
        final = psr_validate.validate_all(data, basis, standards, balances, diagrams, docx_path, facts_path, data_dir)
    jp, mp = psr_validate.write_validation(final, report_dir)
    lap("validation_and_pdf")
    summary = {"docx": docx_path, "pdf": pdf_path if word_ok else None, "validation": final["status"], "validation_json": jp,
               "validation_md": mp, "exports": exports, "diagrams": {"bfd": diagrams["bfd"]["svg"], "pfd_pdf": diagrams["pfd"]["pdf"],
               "pfd_sheets": [s["svg"] for s in diagrams["pfd"]["sheets"]], "pfd_spec": diagrams["pfd"]["spec"],
                            "crossings_total": diagrams["pfd"].get("crossings_total", 0),
                            "sparse_sheets": diagrams["pfd"].get("sparse_sheets", []),
                            "layout_review": diagrams.get("layout_review")},
               "balances_summary": balances["summary"], "word_updated": word_ok, "timings_s": timings,
               "validation_checks": {c["id"]: c["status"] for c in final["checks"]}}
    if not allow_fail and final["n_fail"]:
        raise RuntimeError("validation failed: %s" % [c["id"] for c in final["checks"] if c["status"] == "FAIL"])
    return summary


def merge_into_results(results_path, summary, task_dir=None):
    """Add a ``process_study_report`` block (paths relative to ``task_dir``, validation status) to a task ``results.json``.

    Args:
        results_path: path of the task ``results.json`` (created when missing).
        summary: dict returned by :func:`run_pipeline`.
        task_dir: folder used to relativise paths (default: folder of ``results_path``).

    Returns:
        The block that was written.
    """
    base = task_dir or os.path.dirname(os.path.abspath(results_path))

    def rel(path):
        return os.path.relpath(path, base).replace("\\", "/") if path else None

    block = {"skill": "process-study-report", "validation_status": summary["validation"], "report_docx": rel(summary["docx"]),
             "report_pdf": rel(summary.get("pdf")), "validation_report": rel(summary["validation_json"]),
             "exports": {k: rel(v) for k, v in summary["exports"].items()},
             "diagrams": {"bfd": rel(summary["diagrams"]["bfd"]), "pfd_pdf": rel(summary["diagrams"]["pfd_pdf"]),
                          "pfd_sheets": [rel(p) for p in summary["diagrams"]["pfd_sheets"]], "pfd_layout_spec": rel(summary["diagrams"]["pfd_spec"])},
             "balances_summary": summary["balances_summary"], "word_fields_updated": summary["word_updated"]}
    results = {}
    if os.path.exists(results_path):
        with open(results_path, encoding="utf-8") as fh:
            results = json.load(fh)
    results["process_study_report"] = block
    with open(results_path, "w", encoding="utf-8") as fh:
        json.dump(results, fh, indent=2)
    return block


def _load_inputs(args):
    with open(args.data, encoding="utf-8") as fh:
        data = json.load(fh)
    basis = psr_basis.load_structured(args.basis)
    standards = psr_basis.load_structured(args.standards)
    if isinstance(standards, dict):
        standards = standards.get("standards", [])
    return data, basis, standards


def main(argv=None):
    parser = argparse.ArgumentParser(prog="process_study_report", description=__doc__.split("\n")[0])
    sub = parser.add_subparsers(dest="cmd", required=True)
    p = sub.add_parser("init-basis")
    p.add_argument("dir")
    for name in ("build", "validate"):
        p = sub.add_parser(name)
        p.add_argument("--data", required=True)
        p.add_argument("--basis", required=True)
        p.add_argument("--standards", required=True)
        p.add_argument("--out", required=True)
        p.add_argument("--no-word", action="store_true")
        p.add_argument("--max-units-per-sheet", type=int, default=14)
    p = sub.add_parser("results-json")
    p.add_argument("--results", required=True)
    p.add_argument("--summary", required=True, help="run_summary.json written by the pipeline")
    p = sub.add_parser("demo")
    p.add_argument("out")
    args = parser.parse_args(argv)
    if args.cmd == "init-basis":
        src = psr_basis.templates_dir()
        os.makedirs(args.dir, exist_ok=True)
        for name in (n for n in os.listdir(src) if n.endswith(".yaml")):
            shutil.copy(os.path.join(src, name), os.path.join(args.dir, name))
        sys.stdout.write("templates copied to %s\n" % args.dir)
        return 0
    if args.cmd == "results-json":
        with open(args.summary, encoding="utf-8") as fh:
            merge_into_results(args.results, json.load(fh))
        return 0
    if args.cmd == "demo":
        import psr_demo

        model = psr_demo.build_demo_model()
        data = psr_extract.extract_process(model, "A", "Demo case", model_name="synthetic demo flowsheet")
        src = psr_basis.templates_dir()
        basis = psr_basis.load_structured(os.path.join(src, "basis_demo.yaml"))
        standards = psr_basis.load_structured(os.path.join(src, "standards_register_template.yaml"))["standards"]
        res = run_pipeline(data, basis, standards, args.out, use_word=False)
        sys.stdout.write(json.dumps(res, indent=1) + "\n")
        return 0 if res["validation"] != "FAIL" else 1
    data, basis, standards = _load_inputs(args)
    if args.cmd == "validate":
        balances = psr_balances.compute_balances(data, basis.get("tolerances"))
        result = psr_validate.validate_all(data, basis, standards, balances, None, None, None, None)
        psr_validate.write_validation(result, args.out)
        sys.stdout.write("%s: %d fail, %d warn\n" % (result["status"], result["n_fail"], result["n_warn"]))
        return 1 if result["n_fail"] else 0
    res = run_pipeline(data, basis, standards, args.out, use_word=not args.no_word, max_units_per_sheet=args.max_units_per_sheet)
    sys.stdout.write(json.dumps(res, indent=1) + "\n")
    return 1 if res["validation"] == "FAIL" else 0


if __name__ == "__main__":
    sys.exit(main())
