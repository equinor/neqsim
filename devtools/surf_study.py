"""SURF design study: one command from a study specification to a generated NeqSim model, design results, drawings and report.

Command line (``python -m surf_study --help`` or ``neqsim surf-study``)::

    surf_study.py STUDY.yaml --out DIR [--strict] [--stage all|build-model|run|report]
    surf_study.py --init DIR                       write the specification template and stop (no result is created)
    surf_study.py --demo --out DIR                 the synthetic four-well tie-back example (needs the JVM; several minutes)

Stages share one study/run identity: ``build-model`` writes the generated builders, topology definitions and mapping manifest;
``run`` solves every concept and case once (cached on specification, concept, case, mesh, NeqSim and toolkit version) and writes
the canonical dataset; ``report`` renders drawings, DOCX/PDF and the validation report from the retained dataset without
calling the solver, and refuses a dataset that no longer matches the specification. Exit codes: 0 ok, 1 strict validation
failure, 2 input/usage error.
"""
import argparse
import json
import os
import shutil
import sys
import time

import surf_dataset
import surf_design
import surf_model
import surf_quantities
import surf_run
import surf_spec
from fa_units import FlowAssuranceError

HERE = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.dirname(HERE)
DEMO_SPEC = os.path.join(HERE, "surf_examples", "four_well_tieback", "study.yaml")
TEMPLATES_DIR = os.path.join(HERE, "surf_templates")
STAGES = ("all", "build-model", "run", "report")


def say_factory(quiet):
    return (lambda m: None) if quiet else (lambda m: sys.stdout.write(str(m) + "\n"))


def init_templates(out_dir, say):
    """Write the specification template and the standards register and stop."""
    os.makedirs(out_dir, exist_ok=True)
    for name, target in (("study_template.yaml", "study.yaml"), ("standards_register.yaml", "standards_register.yaml"), ("README.md", "README.md")):
        shutil.copy(os.path.join(TEMPLATES_DIR, name), os.path.join(out_dir, target))
    say("Templates written to %s (study.yaml, standards_register.yaml, README.md)." % out_dir)
    say("Every value that must come from a source reads 'unavailable - TO BE CONFIRMED'; the command refuses to run on an unfilled template.")
    say("The complete synthetic example is devtools/surf_examples/four_well_tieback/study.yaml.")
    return 0


def load_standards(path=None):
    import psr_basis

    reg = psr_basis.load_structured(path or os.path.join(TEMPLATES_DIR, "standards_register.yaml"))
    return reg["standards"] if isinstance(reg, dict) else reg


def build_handoffs(spec, model, ref_concept):
    """External specialist hand-off packages (inputs prepared by this study; results come back from the specialist)."""
    riser_edges = [e for e in model["edges"].values() if e["kind"] == "riser"]
    return [
        {"id": "HO-01", "discipline": "Riser global analysis (DNV-ST-F201, API 17J for the flexible option)", "status": "open",
         "needed": "static and dynamic response, touchdown, VIV and fatigue, interference, coupled host/mooring motions", "prepared_inputs": "riser concept polylines, fluid states per case, design pressure/temperature",
         "edges": [e["id"] for e in riser_edges]},
        {"id": "HO-02", "discipline": "On-bottom stability, free span, global buckling and pipe-soil (DNV-RP-F109/F105/F110/F114)", "status": "open",
         "needed": "metocean, geotechnical and survey inputs; submerged weight and response models", "prepared_inputs": "route polylines, OD/wall/insulation and operating temperature/pressure envelopes",
         "edges": [e["id"] for e in model["edges"].values() if e["kind"] in ("flowline", "trunkline")]},
        {"id": "HO-03", "discipline": "Umbilical manufacturer cross-section and dynamic analysis (API 17E)", "status": "open",
         "needed": "tube, armour and cable construction; dynamic and installation analysis; hydraulic response", "prepared_inputs": "service register, chemical demand and delivery pressures",
         "edges": []},
        {"id": "HO-04", "discipline": "Electrical power and communication (voltage drop, ampacity, protection)", "status": "open",
         "needed": "cable definition, supply/consumer voltage, protection and communication design", "prepared_inputs": "power demand and channel counts", "edges": []},
        {"id": "HO-05", "discipline": "Marine operations, lay and lifting (DNV-ST-N001)", "status": "open",
         "needed": "vessel capacity, lay tension and bend response, lifting, sea fastening, marine warranty acceptance", "prepared_inputs": "installation sequence and asset positions", "edges": []},
        {"id": "HO-06", "discipline": "Control, safety functions and cause-and-effect", "status": "open",
         "needed": "SIL, valve closure times, shutdown sequences and approved cause-and-effect", "prepared_inputs": "functional schematic and the list of omitted systems", "edges": []},
        {"id": "HO-07", "discipline": "Transient flow assurance (start-up, restart, depressurisation, pigging, slugging)", "status": "open",
         "needed": "qualified transient simulation (OLGA or a qualified NeqSim two-fluid network)", "prepared_inputs": "boundary conditions of the steady cases; geometry and thermal data", "edges": []},
        {"id": "HO-08", "discipline": "Vendor datasheets (trees, manifolds, PLETs/PLEMs, jumpers, pump)", "status": "open",
         "needed": "dimensions, weights, ratings, ROV interfaces and general arrangements", "prepared_inputs": "functional datasheets with sourced design conditions", "edges": []}]


def build_model_stage(spec, model, out_dir, jp, say):
    """Write the generated builders, operation plans, topology definitions and the mapping manifest for every concept (design case)."""
    mdir = os.path.join(out_dir, "model")
    os.makedirs(os.path.join(mdir, "builders"), exist_ok=True)
    os.makedirs(os.path.join(mdir, "plans"), exist_ok=True)
    design_case = next(c for c, v in model["cases"].items() if v["role"] == "design")
    manifests = {}
    for cid in model["concepts"]:
        domains = {}
        for dom in ("production", "injection"):
            plan = surf_model.build_plan(spec, model, cid, design_case, dom)
            with open(os.path.join(mdir, "plans", "%s_%s_%s.json" % (cid, design_case, dom)), "w", encoding="utf-8") as fh:
                json.dump(plan, fh, indent=1)
            surf_model.emit_python(plan, spec, cid, design_case, os.path.join(mdir, "builders", "build_%s_%s.py" % (cid, dom)))
            d = surf_model.execute_plan(plan, jp)
            with open(os.path.join(mdir, "topology_%s_%s.json" % (cid, dom)), "w", encoding="utf-8") as fh:
                fh.write(str(d.topology.toJson()))
            domains[dom] = d
            say("model %s/%s: %d operations, topology definition written" % (cid, dom, len(plan)))
        manifests[cid] = surf_model.mapping_manifest(spec, model, cid, domains)
    with open(os.path.join(mdir, "mapping_manifest.json"), "w", encoding="utf-8") as fh:
        json.dump(manifests, fh, indent=1)
    return manifests


def run_stage(spec, model, findings, out_dir, say, timings, spec_hash):
    """Solve every concept and case once, assess, cost and assemble the canonical dataset (JSON-serialisable)."""
    jp = surf_model.start_jvm()
    cache = os.path.join(out_dir, "cache")
    concepts, cases = list(model["concepts"]), list(model["cases"])
    t0 = time.time()
    manifests = build_model_stage(spec, model, out_dir, jp, say)
    timings["model_generation"] = round(time.time() - t0, 2)
    t0 = time.time()
    umb = surf_run.run_umbilical(spec, model, cache, say)
    fluids = sorted({model["cases"][c]["production_fluid"] for c in cases})
    curves = surf_design.hydrate_curves(spec, fluids, say)
    timings["umbilical_and_hydrate"] = round(time.time() - t0, 2)
    results = {c: {} for c in concepts}
    t0 = time.time()
    for cid in concepts:
        for case in cases:
            inj = surf_run.run_domain_case(spec, model, cid, case, "injection", 1, cache, say)
            prod = surf_run.run_domain_case(spec, model, cid, case, "production", 1, cache, say)
            results[cid][case] = {"production": prod, "injection": inj}
    timings["solve_all_cases"] = round(time.time() - t0, 2)
    t0 = time.time()
    mesh = None
    ms = spec["solver"].get("mesh_sensitivity")
    if ms:
        design_case = next(c for c, v in model["cases"].items() if v["role"] == "design")
        cid = concepts[0]
        fine = surf_run.run_domain_case(spec, model, cid, design_case, "production", int(ms["factor"]), cache, say)
        mesh = surf_design.mesh_sensitivity(spec, results[cid][design_case]["production"], fine, surf_spec.case_for_concept(model, spec, cid))
        mesh["concept"], mesh["case"] = cid, design_case
    timings["mesh_sensitivity"] = round(time.time() - t0, 2)
    t0 = time.time()
    structural = {c: surf_design.structural_screening(spec, model, c, jp) for c in concepts}
    cost = {c: surf_quantities.cost(spec, model, c, jp) for c in concepts}
    timings["structural_and_cost"] = round(time.time() - t0, 2)
    standards = load_standards()
    run_data = {"results": results, "umbilical": umb, "curves": curves, "structural": structural, "cost": cost, "mesh": mesh, "mapping": manifests,
                "handoffs": build_handoffs(spec, model, concepts[0])}
    ds = surf_dataset.build_dataset(spec, model, findings, run_data, standards, surf_model.neqsim_version(), timings, spec_hash)
    ddir = os.path.join(out_dir, "dataset")
    os.makedirs(ddir, exist_ok=True)
    with open(os.path.join(ddir, "surf_study.json"), "w", encoding="utf-8") as fh:
        json.dump(ds, fh, indent=1, default=str)
    return ds


def word_pdf(docx_path, pdf_path):
    """PDF through Microsoft Word (updating fields) or LibreOffice when present; True when a PDF was written."""
    import flow_assurance_report

    return flow_assurance_report.word_pdf(docx_path, pdf_path)


def write_handoffs(ds, out_dir):
    """Write one JSON package per specialist hand-off (prepared inputs from the dataset; results come back from the specialist)."""
    hdir = os.path.join(out_dir, "handoff")
    os.makedirs(hdir, exist_ok=True)
    ref = ds["reference_concept"]
    lines = {r["id"]: r for r in ds["registers"][ref]["lines"]}
    paths = {}
    for h in ds["handoffs"]:
        payload = {"schema": "surf_external_handoff.v1", "id": h["id"], "discipline": h["discipline"], "status": h["status"], "needed": h["needed"], "prepared_inputs": h["prepared_inputs"],
                   "study": ds["meta"]["study_id"], "dataset_hash": ds["meta"]["dataset_hash"], "synthetic_inputs": ds["meta"]["synthetic"], "edges": []}
        for eid in h["edges"]:
            if eid not in lines:
                continue
            states = {}
            for case, rec in ds["results"][ref].items():
                dom = "production" if lines[eid]["service"] == "production" else "injection"
                e = rec[dom]["edges"][eid]
                states[case] = {"inlet_pressure_bara": e["inlet_pressure_bara"], "outlet_pressure_bara": e["outlet_pressure_bara"], "outlet_temperature_c": e["outlet_temperature_c"], "flow_kg_s": e["flow_kg_s"]}
            payload["edges"].append({"id": eid, "tag": lines[eid]["tag"], "route_points_xyz_m": ds["geometry"][ref][eid]["points"], "od_mm": lines[eid]["od_mm"], "wall_mm": lines[eid]["wall_mm"],
                                     "id_mm": lines[eid]["id_mm"], "design_pressure_bara": lines[eid]["design_pressure_bara"], "design_temperature_c": lines[eid]["design_temperature_c"], "case_states": states})
        if h["id"] == "HO-03":
            payload["services"] = ds["umbilical"]["services"]
            payload["chemical_results"] = ds["umbilical"]["results"]
        p = os.path.join(hdir, "%s.json" % h["id"])
        with open(p, "w", encoding="utf-8") as fh:
            json.dump(payload, fh, indent=1)
        paths[h["id"]] = os.path.relpath(p, out_dir)
    return paths


def report_stage(ds, spec, out_dir, say, use_word=True, strict=False, timings=None):
    """Render drawings, report and validation from the retained dataset (no solver call); returns the summary."""
    import surf_diagrams
    import surf_report
    import surf_validate

    timings = timings if timings is not None else {}
    handoffs = write_handoffs(ds, out_dir)
    for h in ds["handoffs"]:
        h["file"] = handoffs.get(h["id"])
    pre = surf_validate.check_data(ds)
    readiness = surf_validate.compute_readiness(ds, pre)
    final, info, manifest, docx, pdf, word_ok, rs = None, None, None, None, None, False, None
    for attempt in range(2):
        t0 = time.time()
        rs = surf_dataset.SurfResolver(ds)
        manifest = surf_diagrams.build_package(ds, rs, os.path.join(out_dir, "drawings"), readiness["label"], readiness["note"], log=say)
        timings["drawings"] = round(time.time() - t0, 2)
        t0 = time.time()
        figs = surf_report.make_figures(ds, os.path.join(out_dir, "figures"))
        rdir = os.path.join(out_dir, "report")
        os.makedirs(rdir, exist_ok=True)
        docx = os.path.join(rdir, "%s_rev%s.docx" % (ds["meta"]["document_number"], ds["meta"]["revision"]))
        pdf = os.path.splitext(docx)[0] + ".pdf"
        builder = surf_report.SurfReportBuilder(ds, spec, manifest, readiness, validation=None, figures=figs)
        info = builder.build(docx, os.path.join(out_dir, "figures"))
        timings["report_render"] = round(time.time() - t0, 2)
        checks = list(pre) + surf_validate.check_drawings(ds, manifest) + surf_validate.check_document(ds, info, docx, None)
        new_ready = surf_validate.compute_readiness(ds, checks)
        if new_ready["label"] != readiness["label"] and attempt == 0:
            say("readiness changed from %s to %s after drawing/report checks; rebuilding" % (readiness["label"], new_ready["label"]))
            readiness = new_ready
            continue
        readiness = new_ready
        builder = surf_report.SurfReportBuilder(ds, spec, manifest, readiness, validation=surf_validate.summarise(checks, readiness), figures=figs)
        info = builder.build(docx, os.path.join(out_dir, "figures"))
        word_ok = word_pdf(docx, pdf) if use_word else False
        checks = list(pre) + surf_validate.check_drawings(ds, manifest) + surf_validate.check_document(ds, info, docx, pdf if word_ok else None)
        final = surf_validate.summarise(checks, surf_validate.compute_readiness(ds, checks))
        if final["readiness"]["label"] != readiness["label"] and attempt == 0:
            readiness = final["readiness"]
            continue
        break
    jp_, mp = surf_validate.write_validation(final, os.path.join(out_dir, "report"))
    ds["validation"] = {k: v for k, v in final.items() if k != "checks"}
    ds["validation"]["checks"] = {c["id"]: c["status"] for c in final["checks"]}
    ds["drawings"] = [{k: s[k] for k in ("code", "number", "title", "drawing_number", "qa")} for s in manifest["sheets"]]
    ds["artifacts"] = {"docx": os.path.relpath(docx, out_dir), "pdf": os.path.relpath(pdf, out_dir) if word_ok else None, "drawings_pdf": os.path.relpath(manifest["pdf"], out_dir),
                       "svg": [os.path.relpath(s["svg"], out_dir) for s in manifest["sheets"]], "png": [os.path.relpath(s["png"], out_dir) for s in manifest["sheets"]],
                       "layout_spec": os.path.relpath(manifest["spec"], out_dir), "validation": os.path.relpath(jp_, out_dir)}
    ds["timings_s"].update(timings)
    os.makedirs(os.path.join(out_dir, "dataset"), exist_ok=True)
    with open(os.path.join(out_dir, "dataset", "surf_study.json"), "w", encoding="utf-8") as fh:
        json.dump(ds, fh, indent=1, default=str)
    summary = {"docx": docx, "pdf": pdf if word_ok else None, "dataset": os.path.join(out_dir, "dataset", "surf_study.json"), "dataset_hash": ds["meta"]["dataset_hash"],
               "validation": final["status"], "readiness": final["readiness"], "drawings_pdf": manifest["pdf"], "n_sheets": len(manifest["sheets"]), "n_checks": final["n_checks"],
               "n_fail": final["n_fail"], "n_warn": final["n_warn"], "selected_concept": ds["selected_concept"], "timings_s": ds["timings_s"],
               "validation_checks": {c["id"]: c["status"] for c in final["checks"]}, "strict_failure": bool(strict and final["status"] == "FAIL")}
    with open(os.path.join(out_dir, "run_summary.json"), "w", encoding="utf-8") as fh:
        json.dump(summary, fh, indent=1, default=str)
    return summary


def merge_into_results(results_path, summary, task_dir=None):
    """Add a ``surf_study`` block (paths relative to ``task_dir``) to a task ``results.json`` (created when missing)."""
    base = task_dir or os.path.dirname(os.path.abspath(results_path))

    def rel(p):
        return os.path.relpath(p, base).replace("\\", "/") if p else None

    block = {"skill": "neqsim-surf-design-study", "validation_status": summary["validation"], "readiness": summary["readiness"]["label"], "report_docx": rel(summary["docx"]),
             "report_pdf": rel(summary.get("pdf")), "dataset": rel(summary["dataset"]), "dataset_hash": summary["dataset_hash"], "drawings_pdf": rel(summary["drawings_pdf"]),
             "selected_concept": summary["selected_concept"], "timings_s": summary["timings_s"]}
    results = {}
    if os.path.exists(results_path):
        with open(results_path, encoding="utf-8") as fh:
            results = json.load(fh)
    results["surf_study"] = block
    with open(results_path, "w", encoding="utf-8") as fh:
        json.dump(results, fh, indent=2)
    return block


def run_pipeline(spec_path, out_dir, stage="all", strict=False, quiet=False, use_word=True, task_dir=None):
    """Run the requested stage(s) for a specification file; returns ``(exit_code, summary_or_dataset)``."""
    say = say_factory(quiet)
    spec, findings, model = surf_spec.load_spec(spec_path)
    errors = [f for f in findings if f["level"] == "error"]
    if errors or model is None:
        for f in errors:
            say("INPUT ERROR [%s] %s" % (f["code"], f["message"]))
        raise surf_spec.SpecError("%d input error(s); no model was generated" % len(errors))
    for f in findings:
        if f["level"] == "warning":
            say("input warning [%s] %s" % (f["code"], f["message"]))
    spec_hash = surf_spec.spec_hash(spec)
    os.makedirs(out_dir, exist_ok=True)
    shutil.copy(spec_path, os.path.join(out_dir, "study_input" + os.path.splitext(spec_path)[1]))
    timings = {}
    ds_path = os.path.join(out_dir, "dataset", "surf_study.json")
    t_total = time.time()
    ds = None
    if stage == "build-model":
        manifests = build_model_stage(spec, model, out_dir, surf_model.start_jvm(), say)
        return 0, manifests
    if stage in ("all", "run"):
        ds = run_stage(spec, model, findings, out_dir, say, timings, spec_hash)
        if stage == "run":
            return 0, ds
    if stage == "report":
        if not os.path.isfile(ds_path):
            raise surf_spec.SpecError("no dataset at %s: run the 'run' stage first" % ds_path)
        with open(ds_path, encoding="utf-8") as fh:
            ds = json.load(fh)
        if ds["meta"]["spec_hash"] != spec_hash:
            raise surf_spec.SpecError("stale dataset: the specification changed since the dataset was produced (%s vs %s); run the 'run' stage again"
                                      % (ds["meta"]["spec_hash"], spec_hash))
        timings = dict(ds.get("timings_s") or {})
    summary = report_stage(ds, spec, out_dir, say, use_word=use_word, strict=strict, timings=timings)
    summary["timings_s"]["total"] = round(time.time() - t_total, 2)
    if task_dir:
        merge_into_results(os.path.join(task_dir, "results.json"), summary, task_dir)
    return (1 if summary["strict_failure"] else 0), summary


def build_parser():
    p = argparse.ArgumentParser(prog="neqsim surf-study", description=__doc__.split("\n")[0])
    p.add_argument("source", nargs="?", help="study specification (surf_study.v1 YAML or JSON)")
    p.add_argument("--out", help="output folder (default: ./surf_study)")
    p.add_argument("--stage", choices=STAGES, default="all", help="stage to run; stages share the study identity and never reuse stale results")
    p.add_argument("--task", help="task folder whose results.json receives the summary")
    p.add_argument("--init", metavar="DIR", nargs="?", const=".", help="write the specification template to DIR and stop")
    p.add_argument("--demo", action="store_true", help="run the synthetic four-well tie-back example")
    p.add_argument("--no-word", action="store_true", help="do not use Microsoft Word/LibreOffice for fields and the PDF export")
    p.add_argument("--strict", action="store_true", help="exit with code 1 when the validation status is FAIL")
    p.add_argument("--quiet", action="store_true", help="print only errors and the final summary")
    return p


def main(argv=None):
    args = build_parser().parse_args(argv)
    say = say_factory(args.quiet)
    try:
        if args.init is not None:
            return init_templates(args.init, say)
        source = DEMO_SPEC if args.demo else args.source
        if not source:
            build_parser().print_usage(sys.stderr)
            sys.stderr.write("error: give a study specification, --init DIR or --demo\n")
            return 2
        out = args.out or (os.path.join(args.task, "deliverables", "surf_study") if args.task else "surf_study")
        code, res = run_pipeline(source, out, args.stage, args.strict, args.quiet, not args.no_word, args.task)
        if isinstance(res, dict) and "docx" in res:
            say("validation: %s (%d checks, %d fail, %d warn); readiness: %s" % (res["validation"], res["n_checks"], res["n_fail"], res["n_warn"], res["readiness"]["label"]))
            say("report: %s" % res["docx"])
            say("drawings: %s (%d sheets)" % (res["drawings_pdf"], res["n_sheets"]))
        return code
    except (FlowAssuranceError, surf_spec.SpecError) as exc:
        sys.stderr.write("surf-study: %s\n" % exc)
        return 2


if __name__ == "__main__":
    sys.exit(main())
