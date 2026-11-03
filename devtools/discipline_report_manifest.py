"""Emit a ``discipline_report.v1`` hand-off folder from a solved NeqSim task.

Project-development workflows (energy-project-agents) ingest a specialist's work from
``<case>/reports/<report_type>/`` containing ``report_manifest.json`` and
``engineering_results.jsonl`` (``engineering_result.v1`` records). The NeqSim report
generators write ``results.json`` plus a Word/PDF/HTML report; this module turns that
output into the hand-off folder without re-running any calculation.

Usage::

    neqsim report-manifest TASK_DIR --type pvt-fluid-report --person "Name" \\
        --project-id TURMALINA --concept C1 --out CASE/reports/pvt-fluid-report

Rules: values are copied from ``key_results`` unchanged; validation status defaults to
``screening`` and is never raised above ``validated``; a missing ``results.json`` or an
empty ``key_results`` is reported as a failure, never replaced by invented values.
"""
import argparse
import datetime
import json
import re
import shutil
import sys
from pathlib import Path

REPORT_EXTENSIONS = (".docx", ".pdf", ".html", ".md")
STATUSES = ("mock", "unvalidated", "screening", "validated", "verified", "data_gap")
DISCIPLINES = ("reservoir", "pvt", "wells", "surf", "facilities", "process", "power", "safety", "cost",
               "economics", "emissions", "schedule", "risk", "commercial", "governance", "integration")
DEFAULT_DISCIPLINE = {
    "reservoir-report": "reservoir", "pvt-fluid-report": "pvt", "well-planning-report": "wells",
    "surf-design-report": "surf", "flow-assurance-report": "surf", "process-design-report": "process",
    "utilities-report": "facilities", "power-emissions-report": "power", "safety-report": "safety",
    "area-infrastructure-report": "facilities", "host-capacity-report": "facilities",
    "facilities-cost-report": "cost", "economics-report": "economics",
    "technology-assessment-report": "facilities", "concept-screening-report": "integration",
    "standards-review-report": "governance",
}


def _slug(text, pattern=r"[^A-Za-z0-9._:-]+"):
    """Return text reduced to identifier-safe characters.

    @param text any string
    @param pattern characters to replace
    @return a safe identifier
    """
    return re.sub(pattern, "_", str(text)).strip("_.-:") or "x"


def _agent_name(text):
    """Return a lower-case kebab-case agent name.

    @param text any string
    @return a name matching the responsible_agent pattern
    """
    return re.sub(r"-+", "-", re.sub(r"[^a-z0-9]+", "-", str(text).lower())).strip("-") or "neqsim-task"


def _load_results(task_dir):
    """Read results.json from a task folder.

    @param task_dir task folder
    @return the parsed results
    @raises ValueError when the file is missing, unreadable or has no key_results
    """
    path = Path(task_dir) / "results.json"
    if not path.is_file():
        raise ValueError("{} not found: solve the task and save results.json first".format(path))
    try:
        results = json.loads(path.read_text(encoding="utf-8"))
    except ValueError as exc:
        raise ValueError("{} is not valid JSON: {}".format(path, exc))
    key_results = results.get("key_results")
    if not isinstance(key_results, dict) or not key_results:
        raise ValueError("{} has no key_results: nothing to hand off".format(path))
    return results


def _result_records(results, args, now):
    """Build ``engineering_result.v1`` records from ``key_results``.

    @param results parsed results.json
    @param args parsed command-line arguments
    @param now ISO timestamp
    @return list of records
    """
    assumptions = [str(a) if not isinstance(a, dict) else json.dumps(a, sort_keys=True)
                   for a in (results.get("assumptions") or [])]
    method = str(results.get("approach") or "NeqSim calculation")[:240] or "NeqSim calculation"
    discipline = args.discipline or DEFAULT_DISCIPLINE.get(args.type, "integration")
    records = []
    for name, raw in results["key_results"].items():
        unit, note, value = "-", "", raw
        if isinstance(raw, dict) and "value" in raw:
            value, unit, note = raw["value"], str(raw.get("unit") or "-"), str(raw.get("label") or "")
        elif isinstance(name, str):
            match = re.search(r"_(C|K|bara|barg|bar|kW|MW|kg_h|kg_s|Sm3_d|m3_h|m|mm|pct|percent)$", name)
            unit = {"pct": "%", "percent": "%", "kg_h": "kg/h", "kg_s": "kg/s", "Sm3_d": "Sm3/d",
                    "m3_h": "m3/h"}.get(match.group(1), match.group(1)) if match else "-"
        record = {
            "schema": "engineering_result.v1",
            "result_id": "{}.{}.{}".format(_slug(args.project_id), _slug(args.type), _slug(name)),
            "project_id": _slug(args.project_id, r"[^A-Za-z0-9._-]+"),
            "concept_id": args.concept, "scenario_id": args.scenario, "revision": args.revision,
            "discipline": discipline, "quantity": str(name), "value": value, "unit": unit,
            "source": {"kind": "simulation", "reference": "{} (results.json key_results.{})".format(
                Path(args.task).name, name)},
            "assumptions": assumptions, "method": method,
            "tool_version": str(results.get("tool_version") or "neqsim"),
            "uncertainty": {"kind": "none"}, "validation_status": args.status,
            "responsible_agent": _agent_name(args.agent), "created_at": now,
        }
        if note:
            record["notes"] = note
        records.append(record)
    return records


def _find_report(task_dir):
    """Find the main report file in the task's step3_report folder.

    @param task_dir task folder
    @return the path of the newest Word, PDF, HTML or markdown report, or None
    """
    folder = Path(task_dir) / "step3_report"
    if not folder.is_dir():
        return None
    candidates = [p for p in folder.iterdir() if p.is_file() and p.suffix.lower() in REPORT_EXTENSIONS
                  and not p.name.startswith(("~", "WORK_RECORD"))]
    order = {ext: i for i, ext in enumerate(REPORT_EXTENSIONS)}
    candidates.sort(key=lambda p: (order[p.suffix.lower()], -p.stat().st_mtime))
    return candidates[0] if candidates else None


def emit(args):
    """Write the hand-off folder.

    @param args parsed command-line arguments
    @return the manifest dictionary that was written
    @raises ValueError when the task has no usable results
    """
    results = _load_results(args.task)
    now = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    out = Path(args.out) if args.out else Path(args.task) / "discipline_report" / args.type
    out.mkdir(parents=True, exist_ok=True)
    records = _result_records(results, args, now)
    with (out / "engineering_results.jsonl").open("w", encoding="utf-8") as handle:
        for record in records:
            handle.write(json.dumps(record, ensure_ascii=False) + "\n")
    report = _find_report(args.task)
    files = {"results": "engineering_results.jsonl"}
    if report is not None:
        shutil.copy2(report, out / report.name)
        files["report"] = report.name
    gaps = [str(g) if not isinstance(g, dict) else str(g.get("gap") or g.get("description") or g)
            for g in (results.get("data_gaps") or [])]
    manifest = {
        "schema": "discipline_report.v1", "report_type": args.type,
        "title": args.title or str((results.get("study") or {}).get("title") or args.type),
        "agent": _agent_name(args.agent), "person": args.person, "revision": args.revision, "date": now[:10],
        "validation_status": args.status, "files": files,
        "summary": str(results.get("conclusions") or "")[:600], "gaps": gaps,
    }
    if args.discipline:
        manifest["discipline"] = args.discipline
    (out / "report_manifest.json").write_text(json.dumps(manifest, indent=2, ensure_ascii=False), encoding="utf-8")
    return manifest


def build_parser():
    """Create the argument parser.

    @return the parser
    """
    parser = argparse.ArgumentParser(prog="neqsim report-manifest", description=__doc__.split("\n\n")[0])
    parser.add_argument("task", help="solved NeqSim task folder (results.json, step3_report/)")
    parser.add_argument("--type", required=True, help="report type, e.g. pvt-fluid-report, surf-design-report")
    parser.add_argument("--project-id", default="PROJECT", help="project id for the engineering results")
    parser.add_argument("--concept", default="-", help="concept id, or - for concept-independent data")
    parser.add_argument("--scenario", default="-", help="scenario id, or -")
    parser.add_argument("--revision", default="01", help="document revision")
    parser.add_argument("--person", default="", help="named responsible person (needed for status 50 at the gate)")
    parser.add_argument("--agent", default="neqsim-task-solve", help="responsible agent name (kebab-case)")
    parser.add_argument("--status", default="screening", choices=STATUSES, help="validation status of the results")
    parser.add_argument("--discipline", choices=DISCIPLINES, help="engineering_result discipline override")
    parser.add_argument("--title", default="", help="report title override")
    parser.add_argument("--out", default="", help="output folder (default TASK/discipline_report/TYPE)")
    return parser


def main(argv=None):
    """Command-line entry point.

    @param argv argument list, or None for sys.argv
    @return 0 on success, 2 when the task has no usable results
    """
    args = build_parser().parse_args(argv)
    try:
        manifest = emit(args)
    except ValueError as exc:
        sys.stderr.write("report-manifest: {}\n".format(exc))
        return 2
    sys.stdout.write("Wrote {} ({} files, status {})\n".format(
        args.out or Path(args.task) / "discipline_report" / args.type, len(manifest["files"]), args.status))
    return 0


if __name__ == "__main__":
    sys.exit(main())
