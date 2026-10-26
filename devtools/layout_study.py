"""Physical plant layout screening: checked alternatives, drawings and study report.

Run ``neqsim layout-study --demo onshore --out DIR`` or pass study YAML/JSON.
Restart stages refuse datasets whose study or implementation fingerprint changed.
Exit codes: 0 completed, 1 strict engineering incompleteness/failure, 2 input error.
"""
import argparse
from collections import Counter
from copy import deepcopy
import csv
import hashlib
import importlib.metadata
import json
import os
from pathlib import Path
import platform
import sys
import tempfile
import time

from layout_model import normalize_study, study_fingerprint
from layout_engine import generate_layouts, check_layout
from layout_examples import demo_study

TOOLKIT_VERSION = "plant-layout-screening.v1"
DATASET_SCHEMA = "plant_layout_dataset.v1"
STAGES = ("all", "place", "check", "drawings", "report")
IMPLEMENTATION_MODULES = ("layout_study", "layout_model", "layout_engine", "layout_examples",
                          "layout_process", "layout_drawings", "layout_report", "layout_cad")


def _json_write(path, value):
    """Atomically write finite, deterministic JSON in its destination directory."""
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    data = json.dumps(value, indent=2, sort_keys=True, allow_nan=False) + "\n"
    handle, temporary = tempfile.mkstemp(prefix="." + path.name, suffix=".tmp", dir=path.parent)
    try:
        with os.fdopen(handle, "w", encoding="utf-8") as stream:
            stream.write(data)
        os.replace(temporary, path)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


def _load_source(source):
    """Load a study dictionary or UTF-8 YAML/JSON file without executing code."""
    if isinstance(source, dict):
        return deepcopy(source)
    path = Path(source)
    text = path.read_text(encoding="utf-8")
    if path.suffix.lower() == ".json":
        value = json.loads(text)
    else:
        try:
            import yaml
        except ImportError as exc:
            raise ValueError("PyYAML is required for YAML input; use JSON or install the declared toolkit dependencies") from exc
        value = yaml.safe_load(text)
    if not isinstance(value, dict):
        raise ValueError("study input must be a mapping")
    return value


def _tool_fingerprint():
    """Hash the implemented modules; stale cached calculations are never reused."""
    directory = Path(__file__).resolve().parent
    hashes = {}
    for name in IMPLEMENTATION_MODULES:
        path = directory / (name + ".py")
        hashes[name] = hashlib.sha256(path.read_bytes()).hexdigest() if path.exists() else "unavailable"
    encoded = json.dumps({"modules": hashes, "versions": _versions()}, sort_keys=True).encode("utf-8")
    return hashlib.sha256(encoded).hexdigest(), hashes


def _versions():
    """Record installed adapter versions without importing optional native libraries."""
    result = {"python": platform.python_version(), "toolkit": TOOLKIT_VERSION}
    for distribution in ("neqsim", "cadquery", "ezdxf", "reportlab", "python-docx", "PyMuPDF", "ortools"):
        try:
            result[distribution] = importlib.metadata.version(distribution)
        except importlib.metadata.PackageNotFoundError:
            result[distribution] = "unavailable"
    return result


def _alternative_status(checks):
    """Derive geometry/evidence status without assigning an approval state."""
    required = [c for c in checks if c.get("required", True)]
    if any(c.get("status") == "FAIL" for c in required):
        return "INFEASIBLE"
    if any(c.get("status") == "NOT_EVALUATED" for c in required):
        return "INCOMPLETE"
    return "FEASIBLE"


def _results_fingerprint(alternatives):
    """Bind retained coordinates, metrics and checks to the calculation result."""
    encoded = json.dumps(alternatives, sort_keys=True, allow_nan=False).encode("utf-8")
    return hashlib.sha256(encoded).hexdigest()


def _refresh_checks(study, alternatives):
    """Reassess every alternative from the authoritative study and coordinates."""
    for alternative in alternatives:
        alternative["checks"] = check_layout(study, alternative)
        alternative["status"] = _alternative_status(alternative["checks"])
    return alternatives


def _write_csvs(dataset, out_dir):
    """Publish coordinates and source-linked check results with explicit units."""
    directory = Path(out_dir) / "dataset"
    directory.mkdir(parents=True, exist_ok=True)
    coordinates = directory / "coordinates.csv"
    fields = ["alternative", "tag", "deck", "x_m", "y_m", "rotation_deg"]
    with coordinates.open("w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, fieldnames=fields)
        writer.writeheader()
        for alternative in dataset["alternatives"]:
            for placement in alternative["placements"]:
                writer.writerow({"alternative": alternative["id"], **{k: placement.get(k, "") for k in fields[1:]}})
    checks = directory / "checks.csv"
    fields = ["alternative", "id", "kind", "status", "required", "equipment", "message", "measured", "limit", "unit", "source"]
    with checks.open("w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, fieldnames=fields)
        writer.writeheader()
        for alternative in dataset["alternatives"]:
            for check in alternative["checks"]:
                row = {key: check.get(key, "") for key in fields[1:]}
                for key in ("equipment", "measured", "limit", "source"):
                    if isinstance(row[key], (dict, list, tuple)):
                        row[key] = json.dumps(row[key], sort_keys=True, allow_nan=False)
                writer.writerow({"alternative": alternative["id"], **row})
    return {"coordinates": str(coordinates), "checks": str(checks)}


def _manifest_paths(value):
    """Collect paths from the documented artifact lists, not arbitrary output files."""
    if not isinstance(value, dict):
        return []
    paths = []
    for entry in value.get("files", []):
        path = entry.get("path") if isinstance(entry, dict) else entry
        if isinstance(path, str):
            paths.append(path)
    for key in ("pdf", "html", "docx", "spec", "coordinates", "checks"):
        if isinstance(value.get(key), str):
            paths.append(value[key])
    return paths


def _write_manifest(out_dir, dataset, dataset_path, tables):
    """Hash the current generation's artifacts; do not include a stale directory scan."""
    out_dir = Path(out_dir)
    paths = [str(dataset_path), str(out_dir / "inputs" / "study.json"),
             str(out_dir / "inputs" / "source_study.json")] + list(tables.values())
    for value in dataset.get("artifacts", {}).values():
        paths.extend(_manifest_paths(value))
    files = []
    for item in sorted(set(paths)):
        path = Path(item).resolve()
        if not path.is_file():
            continue
        try:
            relative = str(path.relative_to(out_dir.resolve()))
        except ValueError as exc:
            raise ValueError("artifact path escapes output directory: %s" % path) from exc
        files.append({"path": relative, "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
                      "bytes": path.stat().st_size})
    manifest = {"schema_version": "plant_layout_artifacts.v1", "study_fingerprint": dataset["meta"]["study_fingerprint"],
                "tool_fingerprint": dataset["meta"]["tool_fingerprint"], "files": files}
    target = out_dir / "artifact_manifest.json"
    _json_write(target, manifest)
    return str(target)


def _merge_task(task, summary, study):
    """Retain a study summary in the requested task without discarding other work."""
    path = Path(task) / "results.json"
    value = json.loads(path.read_text(encoding="utf-8")) if path.exists() else {}
    if not isinstance(value, dict):
        raise ValueError("task results.json must be an object")
    value["plant_layout_study"] = summary
    value.setdefault("assumptions", study.get("assumptions", []))
    value.setdefault("data_gaps", study.get("data_gaps", []))
    _json_write(path, value)


def _delivery_assessment(stage, dataset, out_dir, dataset_path, tables, artifact_manifest):
    """Check requested files and adapter evidence separately from engineering.

    Model feasibility never certifies report/CAD completeness. Explicitly skipped
    CAD is not applicable, while an unavailable requested adapter remains a
    partial delivery. DOCX is an optional report companion; HTML/PDF are required.
    """
    checks, warnings = [], []

    def record(identifier, status, message, path=None):
        check = {"id": identifier, "status": status, "message": message}
        if path is not None:
            check["path"] = str(path)
        checks.append(check)

    def present(identifier, path, description):
        if not isinstance(path, (str, Path)) or not str(path):
            record(identifier, "INCOMPLETE", description + " was not produced")
            return
        candidate = Path(path)
        if not candidate.is_file() or candidate.stat().st_size == 0:
            record(identifier, "INCOMPLETE", description + " is absent or empty", candidate)
            return
        record(identifier, "PASS", description + " exists", candidate)

    base_files = {"DATASET": dataset_path, "SOURCE": Path(out_dir) / "inputs" / "source_study.json",
                  "STUDY": Path(out_dir) / "inputs" / "study.json", "COORDINATES": tables["coordinates"],
                  "CHECKS": tables["checks"], "MANIFEST": artifact_manifest}
    for identifier, path in base_files.items():
        present("DELIVERY-" + identifier, path, identifier.lower().replace("_", " "))

    def assess_adapter(name, manifest, warnings_incomplete=False):
        manifest = manifest if isinstance(manifest, dict) else {}
        declared_status = manifest.get("status", manifest.get("qa_status"))
        if declared_status in ("FAIL", "ERROR"):
            record("DELIVERY-" + name + "-STATUS", "FAIL", "Adapter reported " + declared_status)
        elif declared_status in ("INCOMPLETE", "UNAVAILABLE", "PARTIAL"):
            record("DELIVERY-" + name + "-STATUS", "INCOMPLETE", "Adapter reported " + declared_status)
        for index, item in enumerate(manifest.get("checks", [])):
            if isinstance(item, dict) and item.get("status") in ("FAIL", "ERROR"):
                record("DELIVERY-%s-CHECK-%d" % (name, index), "FAIL", item.get("message", "Adapter check failed"))
        errors = manifest.get("errors", [])
        if errors:
            record("DELIVERY-" + name + "-ERRORS", "FAIL", str(errors))
        adapter_warnings = manifest.get("warnings", [])
        warnings.extend("%s: %s" % (name.lower(), message) for message in adapter_warnings)
        if adapter_warnings and warnings_incomplete:
            record("DELIVERY-" + name + "-WARNINGS", "INCOMPLETE", "Requested adapter reported delivery warnings")
        for index, path in enumerate(_manifest_paths(manifest)):
            present("DELIVERY-%s-FILE-%d" % (name, index), path, name.lower() + " declared artifact")
        return manifest

    artifacts = dataset.get("artifacts", {})
    if stage in ("all", "drawings", "report"):
        drawings = assess_adapter("DRAWINGS", artifacts.get("drawings"), warnings_incomplete=True)
        present("DELIVERY-DRAWINGS-PDF", drawings.get("pdf"), "Drawing PDF set")
        sheets = drawings.get("sheets", [])
        if not sheets:
            record("DELIVERY-DRAWING-SHEETS", "INCOMPLETE", "No drawing sheets were produced")
        for index, sheet in enumerate(sheets):
            present("DELIVERY-DRAWING-SVG-%d" % index, sheet.get("svg"), "Vector drawing sheet")
            if sheet.get("qa") == "FAIL":
                record("DELIVERY-DRAWING-QA-%d" % index, "FAIL", "Drawing sheet failed visual-geometry QA")
        required_pairs = {(a["id"], d["id"]) for a in dataset["alternatives"] for d in dataset["study"]["decks"]}
        produced_pairs = {(s.get("alternative"), s.get("deck")) for s in sheets if isinstance(s, dict)}
        for alternative, deck in sorted(required_pairs - produced_pairs):
            record("DELIVERY-PLAN-%s-%s" % (alternative, deck), "INCOMPLETE", "Required alternative/deck plan missing")
        cad = artifacts.get("cad", {})
        if cad.get("status") == "NOT_APPLICABLE" and cad.get("requested") is False:
            record("DELIVERY-CAD", "NOT_APPLICABLE", "CAD explicitly skipped by --no-cad")
        else:
            cad = assess_adapter("CAD", cad, warnings_incomplete=True)
            if not cad.get("files"):
                record("DELIVERY-CAD-FILES", "INCOMPLETE", "Requested CAD outputs were not produced")
    if stage in ("all", "report"):
        report = assess_adapter("REPORT", artifacts.get("report"))
        present("DELIVERY-REPORT-HTML", report.get("html"), "HTML study report")
        present("DELIVERY-REPORT-PDF", report.get("pdf"), "PDF study report")
    status = ("FAIL" if any(c["status"] == "FAIL" for c in checks)
              else "INCOMPLETE" if any(c["status"] == "INCOMPLETE" for c in checks) else "PASS")
    return {"status": status, "stage": stage, "checks": checks, "warnings": warnings}


def run_study(study, out_dir, stage="all", **options):
    """Run a screening study or resume a matching retained dataset.

    ``process_data`` optionally enriches the study through the explicit process
    adapter. ``no_cad=True`` skips optional CAD exports and is recorded visibly.
    ``task`` optionally receives a results.json summary. The function returns
    honest status; the CLI's ``--strict`` converts incompleteness to exit code 1.
    """
    if stage not in STAGES:
        raise ValueError("unsupported stage: %s" % stage)
    raw = _load_source(study)
    original = deepcopy(raw)
    if options.get("process_data") is not None:
        from layout_process import import_process
        raw = import_process(options["process_data"], raw)
    spec = normalize_study(raw)
    fingerprint = study_fingerprint(spec)
    tool_fingerprint, modules = _tool_fingerprint()
    out_dir = Path(out_dir).resolve()
    out_dir.mkdir(parents=True, exist_ok=True)
    dataset_path = out_dir / "dataset" / "plant_layout.json"
    started = time.perf_counter()
    if stage in ("all", "place"):
        _json_write(out_dir / "inputs" / "source_study.json", original)
        _json_write(out_dir / "inputs" / "study.json", spec)
        alternatives = _refresh_checks(spec, generate_layouts(spec))
        dataset = {"meta": {"schema_version": DATASET_SCHEMA, "study_fingerprint": fingerprint,
                            "tool_fingerprint": tool_fingerprint, "module_sha256": modules,
                            "source_study_fingerprint": study_fingerprint(original),
                            "toolkit_version": TOOLKIT_VERSION, "versions": _versions(),
                            "stage": stage, "elapsed_s": 0.0},
                   "study": spec, "alternatives": alternatives, "artifacts": {}}
    else:
        if not dataset_path.is_file():
            raise ValueError("no retained dataset; run --stage place or --stage all first")
        dataset = json.loads(dataset_path.read_text(encoding="utf-8"))
        meta = dataset.get("meta", {})
        if meta.get("schema_version") != DATASET_SCHEMA:
            raise ValueError("retained dataset schema is incompatible; rerun placement")
        if meta.get("study_fingerprint") != fingerprint or meta.get("tool_fingerprint") != tool_fingerprint:
            raise ValueError("retained dataset is stale: study or implementation changed; rerun --stage place or all")
        if study_fingerprint(dataset.get("study", {})) != fingerprint:
            raise ValueError("retained dataset study content does not match its fingerprint")
        alternatives = dataset.get("alternatives")
        if not isinstance(alternatives, list):
            raise ValueError("retained dataset has no valid alternative list")
        if meta.get("results_fingerprint") != _results_fingerprint(alternatives):
            raise ValueError("retained dataset results changed; rerun placement from reviewed inputs")
        _refresh_checks(spec, alternatives)
    if stage in ("all", "drawings", "report"):
        from layout_drawings import build_drawings
        dataset["artifacts"]["drawings"] = build_drawings(spec, alternatives, str(out_dir))
        if options.get("no_cad", False):
            dataset["artifacts"]["cad"] = {"status": "NOT_APPLICABLE", "requested": False,
                                            "files": [], "warnings": ["Optional CAD exports skipped by --no-cad."]}
        else:
            from layout_cad import export_cad
            dataset["artifacts"]["cad"] = export_cad(spec, alternatives, str(out_dir))
    if stage in ("all", "report"):
        from layout_report import build_report
        dataset["artifacts"]["report"] = build_report(
            spec, alternatives, str(out_dir), drawings=dataset["artifacts"].get("drawings"),
            cad=dataset["artifacts"].get("cad"))
    dataset["meta"]["stage"] = stage
    dataset["meta"]["elapsed_s"] = round(time.perf_counter() - started, 6)
    dataset["meta"]["results_fingerprint"] = _results_fingerprint(alternatives)
    _json_write(dataset_path, dataset)
    tables = _write_csvs(dataset, out_dir)
    artifact_manifest = _write_manifest(out_dir, dataset, dataset_path, tables)
    delivery = _delivery_assessment(stage, dataset, out_dir, dataset_path, tables, artifact_manifest)
    counts = Counter(check["status"] for alternative in alternatives for check in alternative["checks"])
    statuses = [alternative.get("status", "INCOMPLETE") for alternative in alternatives]
    overall = ("INCOMPLETE" if not alternatives
               else "INFEASIBLE" if all(s == "INFEASIBLE" for s in statuses)
               else "INCOMPLETE" if any(s != "FEASIBLE" for s in statuses) else "FEASIBLE")
    summary = {"status": overall, "readiness": "SCREENING — REVIEW REQUIRED", "stage": stage,
               "delivery_status": delivery["status"], "delivery": delivery,
               "project_id": spec["project"]["id"], "study_fingerprint": fingerprint,
               "dataset": str(dataset_path), "artifact_manifest": artifact_manifest,
               "n_alternatives": len(alternatives), "check_counts": dict(sorted(counts.items())),
               "alternatives": [{"id": a["id"], "status": a["status"], "metrics": a.get("metrics", {}),
                                 "solver": a.get("solver", {})} for a in alternatives],
               "artifacts": dataset["artifacts"], "tables": tables,
               "elapsed_s": dataset["meta"]["elapsed_s"]}
    _json_write(out_dir / "summary.json", summary)
    if options.get("task"):
        _merge_task(options["task"], summary, spec)
    return summary


def init_study(directory):
    """Write an explicitly synthetic starter without overwriting existing files."""
    directory = Path(directory).resolve()
    names = ("study.yaml", "standards_register.yaml", "README.md")
    if any((directory / name).exists() for name in names):
        raise ValueError("starter destination already contains a managed file; choose an empty directory")
    directory.mkdir(parents=True, exist_ok=True)
    study = demo_study("onshore")
    study["project"]["id"] = "LAYOUT-TEMPLATE"
    study["project"]["title"] = "Synthetic starter — replace and verify project inputs"
    try:
        import yaml
    except ImportError as exc:
        raise ValueError("PyYAML is required to initialize YAML templates") from exc
    (directory / "study.yaml").write_text(yaml.safe_dump(study, sort_keys=False), encoding="utf-8")
    (directory / "standards_register.yaml").write_text(yaml.safe_dump({"standards": study["standards"]}, sort_keys=False), encoding="utf-8")
    (directory / "README.md").write_text(
        "# Plant layout screening starter\n\nAll dimensions, masses, corridors and thresholds are SYNTHETIC assumptions. "
        "Replace them with project-controlled evidence. Standards entries are catalogue metadata only; "
        "the study does not implement a standard-derived spacing table.\n\n"
        "Run `neqsim layout-study study.yaml --out deliverables`. Required missing safety evidence "
        "produces INCOMPLETE; `--strict` exits 1. Adopted standards are the entries in study.yaml; "
        "standards_register.yaml is a review companion and is not loaded automatically.\n",
        encoding="utf-8")
    return {"directory": str(directory), "files": [str(directory / name) for name in names],
            "status": "SYNTHETIC TEMPLATE — INPUT REVIEW REQUIRED"}


def build_parser():
    """Create the CLI parser without importing optional geometry/report libraries."""
    parser = argparse.ArgumentParser(prog="neqsim layout-study", description=__doc__.split("\n")[0])
    parser.add_argument("source", nargs="?", help="plant_layout_study.v1 YAML or JSON")
    parser.add_argument("--demo", choices=("onshore", "offshore"), help="run a public synthetic example")
    parser.add_argument("--init", metavar="DIR", help="write a synthetic starter and standards register, then stop")
    parser.add_argument("--out", help="output directory, default <task>/deliverables/plant_layout or ./plant_layout")
    parser.add_argument("--stage", choices=STAGES, default="all")
    parser.add_argument("--task", help="task directory receiving the study summary")
    parser.add_argument("--process-data", help="explicit process_data JSON input to enrich the study through the adapter")
    parser.add_argument("--no-cad", action="store_true", help="skip optional STEP/GLB/DXF adapters visibly")
    parser.add_argument("--strict", action="store_true", help="exit 1 for any INCOMPLETE/INFEASIBLE alternative")
    parser.add_argument("--quiet", action="store_true", help="print only the final machine-readable summary")
    return parser


def main(argv=None):
    """Run the command and return documented success/strict/input-error codes."""
    parser = build_parser()
    args = parser.parse_args(argv)
    try:
        if args.init:
            if args.source or args.demo:
                raise ValueError("--init cannot be combined with a source or --demo")
            print(json.dumps(init_study(args.init), indent=2))
            return 0
        if bool(args.source) == bool(args.demo):
            raise ValueError("give exactly one study source or --demo onshore|offshore")
        source = demo_study(args.demo) if args.demo else args.source
        out = args.out or str(Path(args.task or ".") / "deliverables" / "plant_layout" if args.task else Path("plant_layout"))
        summary = run_study(source, out, args.stage, no_cad=args.no_cad, task=args.task, process_data=args.process_data)
        if args.quiet:
            print(json.dumps(summary, sort_keys=True, allow_nan=False))
        else:
            print("layout-study: %s; delivery %s; %d alternatives; %s" % (
                summary["status"], summary["delivery_status"], summary["n_alternatives"], summary["readiness"]))
            print("dataset: %s" % summary["dataset"])
            print("artifact manifest: %s" % summary["artifact_manifest"])
            report = summary["artifacts"].get("report", {})
            for key in ("pdf", "html", "docx"):
                if report.get(key):
                    print("report %s: %s" % (key, report[key]))
        incomplete = any(a["status"] != "FEASIBLE" for a in summary["alternatives"])
        return 1 if args.strict and (incomplete or not summary["n_alternatives"] or summary["delivery_status"] != "PASS") else 0
    except (ValueError, OSError, json.JSONDecodeError) as exc:
        sys.stderr.write("layout-study: %s\n" % exc)
        return 2


if __name__ == "__main__":
    sys.exit(main())
