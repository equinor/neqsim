"""Flow-assurance study report: one command from route, basis and cases to a validated report and drawing package.

Command line (``python -m flow_assurance_report --help`` or ``neqsim flow-assurance-report``)::

    flow_assurance_report.py ROUTE.yaml --basis basis.yaml --cases cases.yaml --out DIR [--standards register.yaml] [--strict]
    flow_assurance_report.py STUDY.json --basis basis.yaml --out DIR          re-render from a canonical study dataset (no model run)
    flow_assurance_report.py --init DIR                                       write templates and stop (no results are fabricated)
    flow_assurance_report.py --demo --out DIR                                 synthetic two-template tie-back (needs the JVM)

Exit codes: 0 ok, 1 strict validation failure, 2 input/usage error. The pipeline runs every case once, extracts one
dataset (``dataset/flow_assurance_study.json``) and builds drawings, tables, plots and narrative from it.
"""
import argparse
import json
import os
import shutil
import sys
import time

import fa_diagrams
import fa_handoff
import fa_report
import fa_study
import fa_validate
from fa_units import FlowAssuranceError

HERE = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.dirname(HERE)
DEFAULT_OUT = "flow_assurance_report"
DEMO_DIR = os.path.join(HERE, "fa_examples", "offshore_tieback")
TEMPLATES_DIR = os.path.join(HERE, "fa_templates")
MESH_TOL = {"p_bar": 0.5, "t_k": 0.5, "margin_k": 0.5}


def say_factory(quiet):
    return (lambda m: None) if quiet else (lambda m: sys.stdout.write(str(m) + "\n"))


def detect_source(path):
    """Return ``route`` (YAML route for the built-in model), ``dataset`` (canonical study JSON) or raise for unsupported inputs."""
    if not path or not os.path.isfile(path):
        raise FlowAssuranceError("source not found: %s" % path)
    low = path.lower()
    if low.endswith((".yaml", ".yml")):
        return "route"
    if low.endswith(".json"):
        with open(path, encoding="utf-8") as fh:
            head = fh.read(4000)
        if '"flow_assurance_study.v1"' in head:
            return "dataset"
        if '"positionM"' in head or '"pressureBara"' in head:
            raise FlowAssuranceError("a TwoFluidPipeResponse JSON is not accepted as the study source: its profile is normalised by "
                                     "fa_handoff.two_fluid_profile (Python API) for comparison; the report needs a route file with the built-in model or a canonical study dataset")
        raise FlowAssuranceError("unrecognised JSON source (expected schema flow_assurance_study.v1)")
    raise FlowAssuranceError("unsupported source type: %s (use a route .yaml or a study .json)" % path)


def init_templates(out_dir, say):
    """Write the input templates and stop: no study result, drawing or report is produced."""
    os.makedirs(out_dir, exist_ok=True)
    for name in ("basis_template.yaml", "route_template.yaml", "cases_template.yaml", "standards_register.yaml", "README.md"):
        src = os.path.join(TEMPLATES_DIR, name)
        shutil.copy(src, os.path.join(out_dir, name.replace("_template", "")))
    say("Templates written to %s (basis.yaml, route.yaml, cases.yaml, standards_register.yaml, README.md)." % out_dir)
    say("Every value that must come from a source reads 'unavailable - TO BE CONFIRMED'; no result has been created. Fill the files, then run:")
    say("  neqsim flow-assurance-report %s --basis %s --cases %s --out <dir> --strict" % (os.path.join(out_dir, "route.yaml"), os.path.join(out_dir, "basis.yaml"), os.path.join(out_dir, "cases.yaml")))
    return 0


def word_pdf(docx_path, pdf_path):
    """PDF through Microsoft Word (also updating fields) or LibreOffice when present; returns True when a PDF was written."""
    if os.environ.get("NEQSIM_REPORT_FIELD_UPDATE", "1") != "0":
        import psr_report

        if psr_report.word_update_and_export(docx_path, pdf_path):
            return True
    soffice = shutil.which("soffice") or shutil.which("libreoffice")
    if soffice:
        import subprocess

        try:
            subprocess.run([soffice, "--headless", "--convert-to", "pdf", "--outdir", os.path.dirname(pdf_path), docx_path], check=True, capture_output=True, timeout=240)
            produced = os.path.splitext(docx_path)[0] + ".pdf"
            if produced != pdf_path and os.path.isfile(produced):
                os.replace(produced, pdf_path)
            return os.path.isfile(pdf_path)
        except Exception:  # noqa: BLE001
            return False
    return False


def run_pipeline(inputs, out_dir, use_word=True, strict=False, quiet=False, run_mesh=True, results_dir=None, dataset=None, timings=None):
    """Run the whole workflow for loaded inputs (see :func:`fa_study.load_inputs`); returns the summary dict.

    Args:
        inputs: dict from :func:`fa_study.load_inputs` (basis, route_model, cases, variants, standards ...).
        out_dir: output folder (created).
        use_word: export the PDF through Word/LibreOffice when available.
        strict: raise ``FlowAssuranceError`` after writing the package when the validation status is FAIL.
        run_mesh: run the spatial-sensitivity gate (re-solves the first design case at several increments).
        results_dir: optional folder with ``<event>.json`` external results.
        dataset: a ready dataset (re-render mode); the solver is then not called.
    """
    say = say_factory(quiet)
    t_start = time.time()
    timings = dict(timings or {})
    os.makedirs(out_dir, exist_ok=True)
    basis, route_model, cases, variants = inputs["basis"], inputs["route_model"], inputs["cases"], inputs["variants"]
    standards = inputs.get("standards")
    input_findings = inputs["input_findings"]
    ddir = os.path.join(out_dir, "dataset")
    os.makedirs(ddir, exist_ok=True)
    if dataset is None:
        import fa_benchmarks
        import fa_solver

        t0 = time.time()
        results, solve_t = fa_study.execute_cases(basis, route_model, cases, variants, cache_dir=os.path.join(out_dir, "cache"), log=say)
        timings["solve_all_cases"] = round(time.time() - t0, 2)
        timings["per_case"] = solve_t
        ns = fa_solver.neqsim_handle()
        t0 = time.time()
        first_fluid = basis["fluids"][cases[0]["fluid"]]
        bms = fa_benchmarks.run_all(ns, dict(first_fluid, id=cases[0]["fluid"]), log=say)
        timings["benchmarks"] = round(time.time() - t0, 2)
        mesh = None
        if run_mesh:
            t0 = time.time()
            incs = basis["solver"].get("mesh_sensitivity_increments") or [basis["solver"]["increments"]]
            if basis["solver"]["increments"] not in incs:
                incs = sorted(set(incs) | {basis["solver"]["increments"]})
            if len(incs) >= 2:
                # the gate runs on the design/scenario case with the smallest hydrate margin (the case the decision depends on)
                margins = []
                for c in cases:
                    if c["role"] in ("design", "scenario") and results[c["id"]]["status"] == "converged":
                        m = fa_study.derive_case(results[c["id"]], route_model, c, basis)["extrema"]["hyd_margin_min"]["value"]
                        if m is not None:
                            margins.append((m, c["id"]))
                gate_case = min(margins)[1] if margins else cases[0]["id"]
                mesh = fa_study.mesh_sensitivity(basis, route_model, cases, variants, gate_case, incs, MESH_TOL, log=say)
            timings["mesh_sensitivity"] = round(time.time() - t0, 2)
        t0 = time.time()
        ds = fa_study.build_dataset(inputs, results, bms, mesh, timings, REPO_ROOT, cache_info={"dir": os.path.join(out_dir, "cache")})
        timings["extraction"] = round(time.time() - t0, 2)
    else:
        ds = dataset
        say("re-render mode: using the supplied dataset %s without running the solver" % ds["meta"]["dataset_hash"])
    handoffs = fa_handoff.write_handoffs(ds, out_dir)
    for ev in ds["events"]:
        if ev["id"] in handoffs:
            ev["handoff"] = handoffs[ev["id"]]
    attached = fa_handoff.attach_external_results(ds, results_dir)
    pre_checks = fa_validate.check_inputs(ds, input_findings, standards)
    readiness = fa_validate.compute_readiness(ds, pre_checks, basis["document"])
    say("pre-validation readiness: %s" % readiness["label"])
    final = None
    for attempt in range(2):
        t0 = time.time()
        from fa_study import FaResolver

        rs = FaResolver(ds)
        manifest = fa_diagrams.build_package(ds, rs, os.path.join(out_dir, "drawings"), readiness["label"], readiness["note"], log=say)
        timings["drawings"] = round(time.time() - t0, 2)
        t0 = time.time()
        figs = fa_report.make_figures(ds, os.path.join(out_dir, "figures"))
        rdir = os.path.join(out_dir, "report")
        os.makedirs(rdir, exist_ok=True)
        docx = os.path.join(rdir, "%s_rev%s.docx" % (ds["meta"]["document_number"], ds["meta"]["revision"]))
        builder = fa_report.FaReportBuilder(ds, basis, standards or [], manifest, readiness, validation=None, figures=figs)
        info = builder.build(docx, os.path.join(out_dir, "figures"))
        timings["report_render"] = round(time.time() - t0, 2)
        facts = info["facts"]
        pdf = os.path.splitext(docx)[0] + ".pdf"
        checks = list(pre_checks) + fa_validate.check_drawings(ds, manifest, rs) + fa_validate.check_document(ds, info, docx, facts, None)
        interim = fa_validate.summarise(checks, readiness)
        new_ready = fa_validate.compute_readiness(ds, checks, basis["document"])
        if new_ready["label"] != readiness["label"] and attempt == 0:
            say("readiness changed from %s to %s after drawing/report checks; rebuilding with the final label" % (readiness["label"], new_ready["label"]))
            readiness = new_ready
            continue
        readiness = new_ready
        # second pass: embed the validation appendix, export the PDF, re-validate the final files
        builder = fa_report.FaReportBuilder(ds, basis, standards or [], manifest, readiness, validation=fa_validate.summarise(checks, readiness), figures=figs)
        info = builder.build(docx, os.path.join(out_dir, "figures"))
        word_ok = word_pdf(docx, pdf) if use_word else False
        checks = list(pre_checks) + fa_validate.check_drawings(ds, manifest, rs) + fa_validate.check_document(ds, info, docx, info["facts"], pdf if word_ok else None)
        final = fa_validate.summarise(checks, fa_validate.compute_readiness(ds, checks, basis["document"]))
        if final["readiness"]["label"] != readiness["label"] and attempt == 0:
            readiness = final["readiness"]
            continue
        break
    jp, mp = fa_validate.write_validation(final, os.path.join(out_dir, "report"))
    ds["validation"] = {k: v for k, v in final.items() if k != "checks"}
    ds["validation"]["checks"] = {c["id"]: c["status"] for c in final["checks"]}
    ds["drawings"] = [{k: s[k] for k in ("code", "number", "title", "drawing_number", "qa")} for s in manifest["sheets"]]
    ds["artifacts"] = {"docx": os.path.relpath(docx, out_dir), "pdf": os.path.relpath(pdf, out_dir) if word_ok else None, "drawings_pdf": os.path.relpath(manifest["pdf"], out_dir),
                       "svg": [os.path.relpath(s["svg"], out_dir) for s in manifest["sheets"]], "png": [os.path.relpath(s["png"], out_dir) for s in manifest["sheets"]],
                       "layout_spec": os.path.relpath(manifest["spec"], out_dir), "validation": os.path.relpath(jp, out_dir), "handoffs": handoffs, "external_results_attached": attached}
    timings["total"] = round(time.time() - t_start, 2)
    ds["timings_s"] = timings
    ds_path = os.path.join(ddir, "flow_assurance_study.json")
    with open(ds_path, "w", encoding="utf-8") as fh:
        json.dump(ds, fh, indent=1, default=str)
    summary = {"docx": docx, "pdf": pdf if word_ok else None, "dataset": ds_path, "dataset_hash": ds["meta"]["dataset_hash"], "validation": final["status"], "validation_json": jp, "validation_md": mp,
               "readiness": final["readiness"], "drawings_pdf": manifest["pdf"], "layout_spec": manifest["spec"], "svg": [s["svg"] for s in manifest["sheets"]], "handoffs": handoffs,
               "criteria": {c["id"]: c["overall"] for c in ds["criteria"]}, "threats": {t["id"]: t["status"] for t in ds["threats"]},
               "key_results": {cid: {"hyd_margin_min_k": r["extrema"]["hyd_margin_min"]["value"], "source_pressure_max_bara": r["extrema"]["source_pressure_max"]["value"],
                                     "cooldown_time_min_h": r["extrema"]["cooldown_time_min"]["value"], "host_arrival_t_c": r["extrema"]["host_arrival_t"]["value"]} for cid, r in ds["results"].items()},
               "timings_s": timings, "validation_checks": {c["id"]: c["status"] for c in final["checks"]}, "n_checks": final["n_checks"], "n_fail": final["n_fail"], "n_warn": final["n_warn"]}
    with open(os.path.join(out_dir, "run_summary.json"), "w", encoding="utf-8") as fh:
        json.dump(summary, fh, indent=1, default=str)
    if strict and final["status"] == "FAIL":
        summary["strict_failure"] = True
    return summary


def merge_into_results(results_path, summary, task_dir=None):
    """Add a ``flow_assurance_report`` block (paths relative to ``task_dir``) to a task ``results.json`` (created when missing)."""
    base = task_dir or os.path.dirname(os.path.abspath(results_path))

    def rel(p):
        return os.path.relpath(p, base).replace("\\", "/") if p else None

    block = {"skill": "neqsim-flow-assurance-study-report", "validation_status": summary["validation"], "readiness": summary["readiness"]["label"], "readiness_note": summary["readiness"]["note"],
             "report_docx": rel(summary["docx"]), "report_pdf": rel(summary.get("pdf")), "dataset": rel(summary["dataset"]), "dataset_hash": summary["dataset_hash"],
             "drawings_pdf": rel(summary["drawings_pdf"]), "layout_spec": rel(summary["layout_spec"]), "validation_report": rel(summary["validation_json"]),
             "criteria": summary["criteria"], "threats": summary["threats"], "key_results": summary["key_results"], "external_handoffs": {k: v for k, v in summary["handoffs"].items()},
             "timings_s": {k: v for k, v in summary["timings_s"].items() if k != "per_case"}}
    results = {}
    if os.path.exists(results_path):
        with open(results_path, encoding="utf-8") as fh:
            results = json.load(fh)
    results["flow_assurance_report"] = block
    with open(results_path, "w", encoding="utf-8") as fh:
        json.dump(results, fh, indent=2)
    return block


def build_parser():
    p = argparse.ArgumentParser(prog="neqsim flow-assurance-report", description=__doc__.split("\n")[0])
    p.add_argument("source", nargs="?", help="route.yaml (built-in NeqSim model) or a canonical study dataset .json")
    p.add_argument("--basis", help="engineering basis (basis.yaml)")
    p.add_argument("--cases", help="cases.yaml (cases, topology variants, events)")
    p.add_argument("--standards", help="standards applicability register (default: the shipped seed register - verify before issue)")
    p.add_argument("--out", help="output folder (default: <task>/deliverables/flow_assurance_report, else ./flow_assurance_report)")
    p.add_argument("--task", help="task folder whose results.json receives the summary")
    p.add_argument("--external-results", help="folder with <event>.json results supplied from an external simulator")
    p.add_argument("--init", metavar="DIR", nargs="?", const=".", help="write input templates to DIR and stop (no results are created)")
    p.add_argument("--demo", action="store_true", help="run the synthetic two-template tie-back demonstration")
    p.add_argument("--no-word", action="store_true", help="do not use Microsoft Word/LibreOffice for fields and the PDF export")
    p.add_argument("--no-mesh", action="store_true", help="skip the spatial-sensitivity gate (the validation then warns)")
    p.add_argument("--no-results-json", action="store_true", help="do not merge the summary into the task results.json")
    p.add_argument("--strict", action="store_true", help="exit with code 1 when the validation status is FAIL")
    p.add_argument("--quiet", action="store_true", help="print only errors and the final summary lines")
    return p


def main(argv=None):
    """Entry point; returns the exit code (0 ok, 1 strict validation failure, 2 input or usage error)."""
    args = build_parser().parse_args(argv)
    say = say_factory(args.quiet)
    try:
        if args.init is not None:
            return init_templates(args.init, say)
        source, basis_p, cases_p = args.source, args.basis, args.cases
        if args.demo:
            source = source or os.path.join(DEMO_DIR, "route.yaml")
            basis_p = basis_p or os.path.join(DEMO_DIR, "basis.yaml")
            cases_p = cases_p or os.path.join(DEMO_DIR, "cases.yaml")
        if not source:
            sys.stderr.write("error: give a route .yaml or a study .json (or use --demo / --init)\n")
            return 2
        kind = detect_source(source)
        if not basis_p:
            raise FlowAssuranceError("--basis is required")
        standards_p = args.standards or os.path.join(TEMPLATES_DIR, "standards_register.yaml")
        task_dir = os.path.abspath(args.task) if args.task else None
        if not task_dir:
            try:
                import process_report

                task_dir = process_report.find_task_dir(os.path.abspath(source))
            except Exception:  # noqa: BLE001
                task_dir = None
        out = os.path.abspath(args.out) if args.out else (os.path.join(task_dir, "deliverables", DEFAULT_OUT) if task_dir else os.path.abspath(DEFAULT_OUT))
        say("source: %s (%s); output: %s" % (source, kind, out))
        if kind == "route":
            if not cases_p:
                raise FlowAssuranceError("--cases is required with a route file")
            inputs, findings = fa_study.load_inputs(basis_p, source, cases_p, standards_p)
            inputs["input_findings"] = findings
            summary = run_pipeline(inputs, out, use_word=not args.no_word, strict=args.strict, quiet=args.quiet, run_mesh=not args.no_mesh, results_dir=args.external_results)
        else:
            with open(source, encoding="utf-8") as fh:
                ds = json.load(fh)
            import psr_basis

            basis = psr_basis.load_structured(basis_p)
            standards = psr_basis.load_structured(standards_p)
            standards = standards.get("standards", []) if isinstance(standards, dict) else standards
            inputs = {"basis": basis, "standards": standards, "route_model": None, "cases": ds["cases"], "variants": ds["topology_variants"], "input_findings": fa_study.check_basis(basis)}
            summary = run_pipeline(inputs, out, use_word=not args.no_word, strict=args.strict, quiet=args.quiet, dataset=ds, results_dir=args.external_results)
    except FlowAssuranceError as exc:
        sys.stderr.write("error: %s\n" % exc)
        return 2
    results_path = os.path.join(task_dir, "results.json") if task_dir else None
    if results_path and not args.no_results_json and os.path.exists(results_path):
        merge_into_results(results_path, summary, task_dir)
        say("results.json updated: %s" % results_path)
    sys.stdout.write("Validation: %s (%d checks, %d failed, %d warnings) - %s\n" % (summary["validation"], summary["n_checks"], summary["n_fail"], summary["n_warn"], summary["validation_md"]))
    sys.stdout.write("Readiness: %s - %s\n" % (summary["readiness"]["label"], summary["readiness"]["note"]))
    sys.stdout.write("Report: %s\n" % summary["docx"])
    if summary.get("pdf"):
        sys.stdout.write("PDF: %s\n" % summary["pdf"])
    sys.stdout.write("Drawings: %s (%d SVG sheets)\nDataset: %s (hash %s)\nTiming (s): %s\n" % (summary["drawings_pdf"], len(summary["svg"]), summary["dataset"], summary["dataset_hash"],
                                                                                       {k: v for k, v in summary["timings_s"].items() if k != "per_case"}))
    if summary.get("strict_failure"):
        sys.stdout.write("Validation FAILED; exiting non-zero (--strict). The package is a NOT_READY diagnostic.\n")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
