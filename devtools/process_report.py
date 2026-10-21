"""One-step process-study report: ``neqsim process-report``.

Takes a NeqSim model from any of four sources, solves it for one or more operating cases, extracts every stream
and unit, drafts (or loads) the engineering basis, builds the BFD/PFD drawings, the Word report and the
fail-closed validation report, and merges the result into a task ``results.json``::

    neqsim process-report model.py                      # Python file defining build() / model
    neqsim process-report model.py --cases cases.yaml   # several operating cases without extra Python
    neqsim process-report process.json                  # NeqSim JSON process definition (system or areas)
    neqsim process-report plant.neqsim                  # serialized ProcessSystem / ProcessModel
    neqsim process-report process_data.json             # data extracted earlier by psr_extract
    neqsim process-report model.py --init               # only write the draft basis and cases template

Model-file contract (Python): the file defines ``build(case)`` (or ``build()``) returning a ``ProcessSystem`` or a
multi-area ``ProcessModel`` that is solved or runnable, or a module-level ``model`` / ``process`` object. ``case`` is a
dict ``{"id", "label", "parameters"}``; ``build`` is called once per operating case. The tool runs the model after the
case overrides (``set`` in cases.yaml, applied through ``getAutomation().setVariableValue``) have been applied.
"""
import argparse
import importlib.util
import inspect
import json
import os
import re
import sys
import time

import process_study_report as psr
import psr_balances
import psr_basis
import psr_draft
import psr_extract

SOURCE_TYPES = ("python", "json", "neqsim", "data")
DATA_SCHEMA_PREFIX = "neqsim.process_study_report.data"
DEFAULT_OUT_NAME = "process_report"
TASK_MARKERS = ("step1_scope_and_research", "study_config.yaml", "user_input.md")


class ReportError(Exception):
    """A usage or model problem that is reported to the user without a traceback."""


# ── source detection ─────────────────────────────────────────

def _read_json_head(path):
    with open(path, encoding="utf-8") as fh:
        return json.load(fh)


def classify_json(root):
    """Return ``data`` for pre-extracted process data, ``json`` for a NeqSim process definition, else None."""
    if not isinstance(root, dict):
        return None
    if str(root.get("schema", "")).startswith(DATA_SCHEMA_PREFIX):
        return "data"
    if "areas" in root or (("process" in root) and ("fluid" in root or "fluids" in root)):
        return "json"
    return None


def detect_source(path, forced=None):
    """Detect the model source type of a file or folder.

    Args:
        path: a ``.py``, ``.json`` or ``.neqsim`` file, or a folder that contains exactly one obvious candidate
            (``process_data.json``, ``*.neqsim``, ``model.py`` / ``build_model.py`` / ``process_model.py``,
            ``process.json``).
        forced: one of :data:`SOURCE_TYPES` to skip auto-detection.

    Returns:
        ``(source_type, file_path)``.

    Raises:
        ReportError: when nothing usable is found or the file type is not recognised.
    """
    path = os.path.abspath(os.path.expanduser(path))
    if os.path.isdir(path):
        path = _pick_in_folder(path)
    if not os.path.isfile(path):
        raise ReportError("model source not found: %s" % path)
    if forced:
        if forced not in SOURCE_TYPES:
            raise ReportError("--source-type must be one of %s" % ", ".join(SOURCE_TYPES))
        return forced, path
    low = path.lower()
    if low.endswith(".py"):
        return "python", path
    if low.endswith(".neqsim"):
        return "neqsim", path
    if low.endswith(".json"):
        try:
            kind = classify_json(_read_json_head(path))
        except (OSError, ValueError) as exc:
            raise ReportError("cannot read JSON %s: %s" % (path, exc))
        if kind:
            return kind, path
        raise ReportError("%s is neither a NeqSim process definition (needs 'process' and 'fluid', or 'areas') nor "
                          "process data written by psr_extract (schema %s)" % (path, DATA_SCHEMA_PREFIX))
    raise ReportError("cannot tell the model source type of %s; use --source-type python|json|neqsim|data" % path)


def _pick_in_folder(folder):
    """Best single candidate model file in a folder (task folders are searched in their usual places)."""
    roots = [folder] + [os.path.join(folder, sub) for sub in ("step2_analysis", "models", "model")]
    candidates = []
    for root in roots:
        if not os.path.isdir(root):
            continue
        for name in sorted(os.listdir(root)):
            full = os.path.join(root, name)
            low = name.lower()
            if not os.path.isfile(full):
                continue
            if low in ("process_data.json",) or low.endswith(".neqsim") or low in ("model.py", "build_model.py", "process_model.py"):
                candidates.append(full)
            elif low in ("process.json", "process_definition.json"):
                candidates.append(full)
    if not candidates:
        raise ReportError("no model found in %s (looked for model.py, build_model.py, process_model.py, process.json, *.neqsim, "
                          "process_data.json); pass the model file explicitly" % folder)
    if len(candidates) > 1:
        raise ReportError("several candidate models in %s: %s; pass one explicitly" % (folder, ", ".join(candidates)))
    return candidates[0]


# ── cases ────────────────────────────────────────────────────

CASE_ID_RE = re.compile(r"^[A-Za-z0-9_\-]{1,24}$")


def _parse_value(address, raw):
    """Normalise a ``set`` entry to ``(float value, unit string)``."""
    unit = ""
    value = raw
    if isinstance(raw, dict):
        value, unit = raw.get("value"), raw.get("unit", "")
    elif isinstance(raw, (list, tuple)):
        if len(raw) not in (1, 2):
            raise ReportError("set %r: use [value, unit]" % address)
        value, unit = raw[0], (raw[1] if len(raw) == 2 else "")
    elif isinstance(raw, str):
        parts = raw.strip().split(None, 1)
        value, unit = parts[0], (parts[1] if len(parts) > 1 else "")
    try:
        return float(value), str(unit or "")
    except (TypeError, ValueError):
        raise ReportError("set %r: %r is not a number" % (address, raw))


def default_cases():
    """The single default operating case."""
    return [{"id": "A", "label": "Base case", "set": {}, "parameters": {}}]


def parse_cases(root):
    """Validate a cases mapping (``{"cases": [...], "options": {...}}`` or a bare list).

    Returns:
        ``(cases, options)``; every case has ``id``, ``label``, ``set`` (address -> (value, unit)), ``parameters``.
    """
    options = {}
    if isinstance(root, dict):
        options = dict(root.get("options") or {})
        root = root.get("cases")
    if not isinstance(root, list) or not root:
        raise ReportError("cases file needs a non-empty list under 'cases'")
    cases, seen = [], set()
    for i, raw in enumerate(root):
        if not isinstance(raw, dict):
            raise ReportError("cases[%d] must be a mapping" % i)
        cid = str(raw.get("id") or chr(ord("A") + i))
        if not CASE_ID_RE.match(cid):
            raise ReportError("cases[%d].id %r must be 1-24 letters, digits, '_' or '-'" % (i, cid))
        if cid in seen:
            raise ReportError("duplicate case id %r" % cid)
        seen.add(cid)
        sets = {str(a): _parse_value(a, v) for a, v in (raw.get("set") or {}).items()}
        case = {"id": cid, "label": str(raw.get("label") or "Case %s" % cid), "set": sets,
                "parameters": dict(raw.get("parameters") or {})}
        for key in ("role", "description", "data_source"):
            if raw.get(key):
                case[key] = str(raw[key])
        cases.append(case)
    return cases, options


def load_cases(path):
    """Load ``cases.yaml``/``.json`` (None -> the single default case)."""
    if not path:
        return default_cases(), {}
    if not os.path.isfile(path):
        raise ReportError("cases file not found: %s" % path)
    try:
        return parse_cases(psr_basis.load_structured(path))
    except ReportError:
        raise
    except Exception as exc:  # noqa: BLE001 - YAML/JSON syntax errors
        raise ReportError("cannot read cases file %s: %s" % (path, exc))


def cases_to_plain(cases):
    """YAML-friendly copy of parsed cases (sets as ``[value, unit]``)."""
    out = []
    for c in cases:
        row = {k: v for k, v in c.items() if k not in ("set", "parameters")}
        row["set"] = {a: [v, u] for a, (v, u) in c["set"].items()}
        if c.get("parameters"):
            row["parameters"] = c["parameters"]
        out.append(row)
    return out


# ── building and solving models ─────────────────────────────

def _is_java_model(obj):
    return hasattr(obj, "getUnitOperations") or hasattr(obj, "getAreaNames")


def _unwrap(obj, where):
    if _is_java_model(obj):
        return obj
    for name in ("process_model", "model", "process", "plant", "system"):
        inner = getattr(obj, name, None)
        if inner is not None and _is_java_model(inner):
            return inner
    raise ReportError("%s returned %s, not a ProcessSystem or ProcessModel" % (where, type(obj).__name__))


def load_python_module(path):
    """Import a model file by path; its folder is importable while it loads."""
    folder = os.path.dirname(path)
    name = "psr_model_" + re.sub(r"\W", "_", os.path.splitext(os.path.basename(path))[0])
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    sys.path.insert(0, folder)
    try:
        spec.loader.exec_module(module)
    except Exception as exc:  # noqa: BLE001
        raise ReportError("model file %s failed to import: %s: %s" % (path, type(exc).__name__, exc))
    finally:
        if sys.path and sys.path[0] == folder:
            sys.path.pop(0)
    return module


def _python_build(module, case, path):
    fn = getattr(module, "build", None)
    if callable(fn):
        try:
            takes_case = len(inspect.signature(fn).parameters) > 0
        except (TypeError, ValueError):
            takes_case = False
        arg = {"id": case["id"], "label": case["label"], "parameters": dict(case.get("parameters") or {})}
        try:
            result = fn(arg) if takes_case else fn()
        except Exception as exc:  # noqa: BLE001
            raise ReportError("build() of %s failed for case %s: %s: %s" % (path, case["id"], type(exc).__name__, exc))
        return _unwrap(result, "build()")
    for name in ("model", "process", "plant"):
        if hasattr(module, name):
            obj = getattr(module, name)
            if callable(obj) and not _is_java_model(obj):
                obj = obj()
            return _unwrap(obj, name)
    raise ReportError("%s defines neither build(case) nor a module-level 'model' or 'process'" % path)


def _build_json(text, path):
    from jneqsim import neqsim

    root = json.loads(text)
    if "areas" in root:
        return neqsim.process.processmodel.ProcessModel.fromJson(text)
    result = neqsim.process.processmodel.ProcessSystem.fromJson(text)
    if not result.isSuccess():
        errors = []
        for err in result.getErrors():
            errors.append("[%s] %s (fix: %s)" % (err.getCode(), err.getMessage(), err.getRemediation()))
        raise ReportError("NeqSim JSON %s did not build:\n  %s" % (path, "\n  ".join(errors) or "unknown error"))
    system = result.getProcessSystem()
    built = {str(u.getName()) for u in system.getUnitOperations()}
    dropped = [str(p.get("name")) for p in root.get("process", []) if isinstance(p, dict) and str(p.get("name")) not in built]
    if dropped:
        raise ReportError("NeqSim JSON %s did not build: these process entries were not created (unknown type or bad inlet?): %s"
                          % (path, ", ".join(dropped)))
    return system


def _open_neqsim(path):
    from jneqsim import neqsim

    try:
        obj = neqsim.util.serialization.NeqSimXtream.openNeqsim(path)
    except Exception as exc:  # noqa: BLE001
        raise ReportError("cannot open serialized process %s: %s" % (path, exc))
    return _unwrap(obj, "openNeqsim")


def apply_overrides(model, sets, case_id):
    """Apply ``{address: (value, unit)}`` through the process automation facade."""
    if not sets:
        return
    auto = model.getAutomation()
    for address, (value, unit) in sets.items():
        try:
            auto.setVariableValue(address, float(value), unit)
        except Exception as exc:  # noqa: BLE001
            try:
                units = [str(u) for u in auto.getUnitList()][:12]
            except Exception:  # noqa: BLE001
                units = []
            raise ReportError("case %s: cannot set %r to %s %s: %s. Units in the model: %s. Addresses are 'unit.property' "
                              "(multi-area models: 'Area::unit.property'), e.g. feed.flowRate in kg/hr or "
                              "compressor.outletPressure in bara" % (case_id, address, value, unit, exc, ", ".join(units)))


def solve_case(source_type, path, case, cache, rerun=True):
    """Build a fresh model for one case, apply the overrides and solve it."""
    if source_type == "python":
        if "module" not in cache:
            cache["module"] = load_python_module(path)
        model = _python_build(cache["module"], case, path)
    elif source_type == "json":
        if "text" not in cache:
            with open(path, encoding="utf-8") as fh:
                cache["text"] = fh.read()
        model = _build_json(cache["text"], path)
    elif source_type == "neqsim":
        model = _open_neqsim(path)
    else:
        raise ReportError("source type %s cannot be solved" % source_type)
    apply_overrides(model, case["set"], case["id"])
    if rerun or case["set"] or source_type == "json":
        model.run()
    return model


def extract_cases(source_type, path, cases, std_t_c, std_p_bara, model_name, rerun=True, progress=None):
    """Solve and extract every case; returns the merged data set."""
    cache = {}
    datasets = []
    for case in cases:
        if progress:
            progress("case %s: build and solve" % case["id"])
        model = solve_case(source_type, path, case, cache, rerun)
        data = psr_extract.extract_process(model, case["id"], case["label"], std_t_c, std_p_bara, model_name=model_name)
        if not data["equipment"]:
            raise ReportError("the model has no unit operations (case %s)" % case["id"])
        datasets.append(data)
    try:
        return psr_extract.merge_cases(datasets)
    except ValueError as exc:
        raise ReportError("the cases do not share one topology (%s). build(case) and the overrides may change values but "
                          "must not add or remove equipment" % exc)


def load_data_source(path, cases_given, cases):
    """Pre-extracted process data; case labels/roles from cases.yaml are applied by id."""
    data = _read_json_head(path)
    have = [c["id"] for c in data["cases"]]
    if cases_given:
        unknown = [c["id"] for c in cases if c["id"] not in have]
        if unknown:
            raise ReportError("cases %s are not in %s (it holds %s); extracted data cannot be recomputed" % (unknown, path, have))
        label = {c["id"]: c for c in cases}
        for case in data["cases"]:
            if case["id"] in label:
                case["label"] = label[case["id"]]["label"]
    return data


# ── task folders ─────────────────────────────────────────────

def find_task_dir(start):
    """The task folder that contains ``start`` (searching up to 6 levels), or None."""
    cur = os.path.abspath(start)
    if os.path.isfile(cur):
        cur = os.path.dirname(cur)
    for _ in range(7):
        if any(os.path.exists(os.path.join(cur, m)) for m in TASK_MARKERS):
            return cur
        parent = os.path.dirname(cur)
        if parent == cur:
            break
        cur = parent
    return None


# ── cases file next to the model ──

CONVENTIONAL_MODEL_NAMES = ("model.py", "build_model.py", "process_model.py", "process.json", "process_definition.json")


def find_side_cases(model_path):
    """Cases file that belongs to a model: ``<model>.cases.yaml`` / ``<model>_cases.yaml``, or ``cases.yaml`` beside a
    conventionally named model file (a folder with one model). Any other layout needs ``--cases``."""
    folder, name = os.path.split(model_path)
    stem = os.path.splitext(name)[0]
    names = ["%s.cases.yaml" % stem, "%s_cases.yaml" % stem]
    if name.lower() in CONVENTIONAL_MODEL_NAMES:
        names.append("cases.yaml")
    for candidate in names:
        path = os.path.join(folder, candidate)
        if os.path.isfile(path):
            return path
    return None


# ── inputs written for the user ──────────────────────────────

BASIS_HEADER = (
    "# Draft engineering basis written by `neqsim process-report`.\n"
    "# Every value that could not be derived from the model or the task folder reads 'unavailable - TO BE CONFIRMED'.\n"
    "# Edit those items (and add data_classification, assumptions, requirement_matrices as needed), then delete the\n"
    "# 'basis_status: draft' and 'drafted_fields' keys, set document.readiness, and re-run with --basis <this file>.\n"
    "# Never fill an item with a value you cannot source: leave it unavailable and keep its data_gaps entry.\n")

CASES_HEADER = (
    "# Operating cases for `neqsim process-report`.\n"
    "# id: short identifier (A, B, ...). label/role/description/data_source feed the basis.\n"
    "# set: overrides applied to a fresh model before it is run, as address: [value, unit]. Addresses are\n"
    "#   'unit.property' for a ProcessSystem and 'Area::unit.property' for a ProcessModel, e.g.\n"
    "#   'feed.flowRate': [20000, kg/hr]   'export compressor.outletPressure': [95, bara]\n"
    "# parameters: free values handed to build(case) of a Python model file (case['parameters']).\n"
    "# Topology must be identical in all cases: overrides change values, never equipment.\n")


def _dump_yaml(path, header, obj):
    import yaml

    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as fh:
        fh.write(header)
        yaml.safe_dump(obj, fh, sort_keys=False, allow_unicode=True, width=120, default_flow_style=False)
    return path


def write_inputs(inputs_dir, basis, cases, standards_src):
    """Write the editable inputs (basis, cases, standards register) and return their paths."""
    import shutil

    paths = {}
    if basis is not None:
        paths["basis"] = _dump_yaml(os.path.join(inputs_dir, "basis.yaml"), BASIS_HEADER, basis)
    paths["cases"] = _dump_yaml(os.path.join(inputs_dir, "cases.yaml"), CASES_HEADER, {"cases": cases_to_plain(cases)})
    if standards_src and os.path.exists(standards_src):
        dst = os.path.join(inputs_dir, "standards_register.yaml")
        shutil.copyfile(standards_src, dst)
        paths["standards"] = dst
    return paths


# ── main workflow ────────────────────────────────────────────

def _log(quiet):
    def emit(msg):
        if not quiet:
            sys.stdout.write(msg + "\n")
            sys.stdout.flush()
    return emit


def _load_standards(path):
    standards = psr_basis.load_structured(path)
    if isinstance(standards, dict):
        standards = standards.get("standards", [])
    return standards


def run(args):
    """Run the whole workflow for parsed arguments; returns the process exit code."""
    say = _log(args.quiet)
    cwd_source = args.source or "."
    source_type, src_path = detect_source(cwd_source, args.source_type)
    task_dir = os.path.abspath(args.task) if args.task else find_task_dir(src_path)
    if args.out:
        out_dir = os.path.abspath(args.out)
    elif task_dir:
        out_dir = os.path.join(task_dir, "deliverables", DEFAULT_OUT_NAME)
    else:
        out_dir = os.path.abspath(DEFAULT_OUT_NAME)
    say("source: %s (%s)" % (src_path, source_type))
    say("output: %s%s" % (out_dir, "  (task folder %s)" % task_dir if task_dir else ""))

    cases_path = args.cases
    if not cases_path and source_type != "data":
        cases_path = find_side_cases(src_path)
        if cases_path:
            say("cases: using %s next to the model" % cases_path)
    cases, case_options = load_cases(cases_path)
    cases_given = bool(cases_path)

    basis = None
    if args.basis:
        if not os.path.isfile(args.basis):
            raise ReportError("basis file not found: %s" % args.basis)
        basis = psr_basis.load_structured(args.basis)
        basis_cases = basis.get("operating_cases") or []
        if not cases_given and source_type != "data":
            if len(basis_cases) > 1:
                raise ReportError("the basis defines %d operating cases but no cases file was given; pass --cases (or put cases.yaml "
                                  "next to the model) with matching ids" % len(basis_cases))
            if len(basis_cases) == 1:
                cases = [{"id": str(basis_cases[0]["id"]), "label": basis_cases[0].get("label") or "Base case", "set": {}, "parameters": {}}]

    std = (basis or {}).get("conventions", {}).get("standard_conditions") or {}
    std_t = float(std.get("t_c", args.std_temperature))
    std_p = float(std.get("p_bara", args.std_pressure))
    model_name = os.path.basename(src_path)
    started = time.time()
    if source_type == "data":
        data = load_data_source(src_path, cases_given, cases)
        cases = [{"id": c["id"], "label": c["label"], "set": {}, "parameters": {}} for c in data["cases"]]
    else:
        data = extract_cases(source_type, src_path, cases, std_t, std_p, model_name, rerun=not args.no_rerun,
                             progress=None if args.quiet else say)
    t_extract = time.time() - started
    if basis is not None:
        basis_ids = sorted(str(c.get("id")) for c in basis.get("operating_cases") or [])
        data_ids = sorted(str(c["id"]) for c in data["cases"])
        if basis_ids != data_ids:
            raise ReportError("operating_cases of the basis %s do not match the cases of the model run %s; align the ids in basis.yaml "
                              "and cases.yaml" % (basis_ids, data_ids))
    say("extracted %d units, %d streams, %d components, %d case(s) in %.1f s" % (
        len(data["equipment"]), len(data["streams"]), len(data["components"]), len(data["cases"]), t_extract))

    standards_path = args.standards or os.path.join(psr_basis.templates_dir(), "standards_register_template.yaml")
    standards = _load_standards(standards_path)
    default_standards = not args.standards

    options = {"title": args.title, "doc_no": args.doc_no, "revision": args.revision, "status": args.status, "author": args.author,
               "company": args.company, "project": args.project}
    task_ctx = psr_draft.read_task_context(task_dir)
    drafted = basis is None
    if drafted:
        source_info = {"type": source_type, "path": src_path, "name": os.path.basename(src_path), "sha256": psr_draft.sha256_prefix(src_path)}
        basis = psr_draft.draft_basis(data, cases, source_info, options, task_ctx)
        n_open = psr_draft.count_unconfirmed(basis)
        say("basis: drafted from the model%s; %d item(s) marked TO BE CONFIRMED -> readiness %s - %s" % (
            " and the task folder" if task_ctx.get("files") else "", n_open, basis["document"]["readiness"],
            basis["document"]["readiness_note"]))
    else:
        for key, field in (("title", "title"), ("doc_no", "document_number"), ("revision", "revision"), ("status", "issue_status")):
            if options[key]:
                basis["document"][field] = options[key]
        if options["author"]:
            basis["document"]["prepared_by"] = options["author"]
    if default_standards:
        basis["standards_register_source"] = "toolkit default register template (verify before issue)"
    inputs_dir = os.path.join(out_dir, "inputs")
    paths = write_inputs(inputs_dir, basis if drafted else None, cases, standards_path if default_standards else None)
    if args.init:
        os.makedirs(os.path.join(out_dir, "data"), exist_ok=True)
        psr_extract.write_outputs(data, os.path.join(out_dir, "data"))
        say("")
        say("Draft inputs written to %s" % inputs_dir)
        for key, value in paths.items():
            say("  %-9s %s" % (key, value))
        say("Edit them, then run:")
        say("  neqsim process-report %s --basis %s --cases %s --out %s" % (
            args.source or src_path, paths.get("basis", "<basis.yaml>"), paths["cases"], out_dir))
        return 0

    summary = psr.run_pipeline(data, basis, standards, out_dir, use_word=not args.no_word, max_units_per_sheet=args.max_units_per_sheet)
    summary["timings_s"]["extract"] = round(t_extract, 2)
    summary.update({"source": src_path, "source_type": source_type, "basis_drafted": drafted, "inputs": paths,
                    "readiness": "%s - %s" % (basis["document"]["readiness"].upper(), basis["document"].get("readiness_note", "")),
                    "n_streams": len(data["streams"]), "n_units": len(data["equipment"]), "n_cases": len(data["cases"])})
    with open(os.path.join(out_dir, "run_summary.json"), "w", encoding="utf-8") as fh:
        json.dump(summary, fh, indent=1)
    merged = None
    results_path = os.path.join(task_dir, "results.json") if task_dir else None
    if results_path and not args.no_results_json and os.path.exists(results_path):
        psr.merge_into_results(results_path, summary, task_dir)
        merged = results_path
    say("")
    say("Validation: %s (%s)" % (summary["validation"], summary["validation_md"]))
    say("Readiness label: %s" % summary["readiness"])
    say("Report: %s" % summary["docx"])
    if summary.get("pdf"):
        say("PDF:    %s" % summary["pdf"])
    say("Diagrams: %s ; %d PFD sheet(s), %d line crossings" % (os.path.join(out_dir, "diagrams"), len(summary["diagrams"]["pfd_sheets"]),
                                                              summary["diagrams"].get("crossings_total", 0)))
    say("Balances: %s" % summary["balances_summary"])
    say("Timing (s): %s" % summary["timings_s"])
    if merged:
        say("results.json updated: %s" % merged)
    elif results_path and not args.no_results_json:
        say("No results.json in the task folder yet; merge later with: python -m process_study_report results-json --results %s --summary %s"
            % (results_path, os.path.join(out_dir, "run_summary.json")))
    if drafted:
        say("The basis is a DRAFT. Edit %s and re-run with --basis to confirm it." % paths["basis"])
    if default_standards:
        say("The standards register is the toolkit default: re-verify editions before issue.")
    if summary["validation"] == "FAIL":
        say("Validation FAILED; see the validation report. %s" % ("Exiting non-zero (--strict)." if args.strict else "Use --strict to fail the exit code."))
        return 1 if args.strict else 0
    return 0


def build_parser():
    """Argument parser of ``neqsim process-report``."""
    p = argparse.ArgumentParser(prog="neqsim process-report", description=__doc__.split("\n")[0],
                                formatter_class=argparse.RawDescriptionHelpFormatter,
                                epilog="Sources: Python file (build()/model), NeqSim JSON definition, .neqsim file, process_data.json.")
    p.add_argument("source", nargs="?", help="model file or a folder that holds one (default: current folder)")
    p.add_argument("--source-type", choices=SOURCE_TYPES, help="skip auto-detection of the source type")
    p.add_argument("--cases", help="cases.yaml with named operating cases and parameter overrides (default: <model>.cases.yaml, or cases.yaml beside model.py / process.json)")
    p.add_argument("--basis", help="confirmed engineering basis (basis.yaml); without it a draft is written and used")
    p.add_argument("--standards", help="standards register (default: the verified template shipped with the skill - verify before issue)")
    p.add_argument("--out", help="output folder (default: <task>/deliverables/process_report, else ./process_report)")
    p.add_argument("--task", help="task folder (default: detected from the model location)")
    p.add_argument("--title", help="report title")
    p.add_argument("--doc-no", help="document number")
    p.add_argument("--revision", help="revision (default A)")
    p.add_argument("--status", help="issue status text")
    p.add_argument("--author", help="prepared-by name")
    p.add_argument("--company", help="company on the cover (else unavailable in a draft)")
    p.add_argument("--project", help="project on the cover (else the task title, else unavailable)")
    p.add_argument("--init", action="store_true", help="only write the draft basis, cases template and standards copy, then stop")
    p.add_argument("--no-word", action="store_true", help="do not use Microsoft Word for field update and PDF export")
    p.add_argument("--max-units-per-sheet", type=int, default=14, help="PFD packing limit (default 14)")
    p.add_argument("--no-rerun", action="store_true", help="do not call run() when no case override was applied")
    p.add_argument("--no-results-json", action="store_true", help="do not merge the summary into the task results.json")
    p.add_argument("--strict", action="store_true", help="exit with code 1 when the validation status is FAIL")
    p.add_argument("--std-temperature", type=float, default=psr_extract.DEFAULT_STD_T_C, help="standard temperature in degC (default 15)")
    p.add_argument("--std-pressure", type=float, default=psr_extract.DEFAULT_STD_P_BARA, help="standard pressure in bara (default 1.01325)")
    p.add_argument("--quiet", action="store_true", help="print only errors")
    return p


def main(argv=None):
    """Entry point; returns the exit code (0 ok, 1 strict validation failure, 2 usage or model error)."""
    args = build_parser().parse_args(argv)
    try:
        return run(args)
    except ReportError as exc:
        sys.stderr.write("ERROR: %s\n" % exc)
        return 2


if __name__ == "__main__":
    sys.exit(main())
