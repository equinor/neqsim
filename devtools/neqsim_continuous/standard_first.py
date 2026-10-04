"""Standard-task readiness gate for scheduled living-task cycles.

Living tasks are most useful after an ordinary Standard task has produced a
traceable first answer. This module checks that basis, tries to generate the
first report/work record when possible, and writes a task-local status file that
scheduled cycles can reference before treating their output as operational.
"""

import json
import os
import subprocess
import sys
from datetime import datetime, timezone

from .plan import continuous_dir, read_json, write_json

DEVTOOLS = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CLI = os.path.join(DEVTOOLS, "neqsim_cli.py")
CONSISTENCY_CHECKER = os.path.join(DEVTOOLS, "consistency_checker.py")
SOURCES_GENERATOR = os.path.join(DEVTOOLS, "generate_sources_md.py")
STATUS_FILE = "standard_first_status.json"


def _now():
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat()


def _nonempty(path):
    return os.path.isfile(path) and os.path.getsize(path) > 0


def _any_file(folder, suffixes):
    if not os.path.isdir(folder):
        return False
    for root, _, files in os.walk(folder):
        for name in files:
            if name.lower().endswith(suffixes) and not name.startswith("."):
                return True
    return False


def _formal_report_exists(task_dir):
    report_dir = os.path.join(task_dir, "step3_report")
    if not os.path.isdir(report_dir):
        return False
    for name in os.listdir(report_dir):
        lower = name.lower()
        if lower == "work_record.md":
            continue
        if lower.endswith((".docx", ".html")):
            return True
    return False


def _has_data_gaps(results):
    for key in ("data_gaps", "evidence_gaps"):
        value = results.get(key)
        if isinstance(value, list) and value:
            return True
    grouped = results.get("assumptions_and_gaps")
    if isinstance(grouped, dict) and grouped.get("data_gaps"):
        return True
    return False


def _has_assumptions(results):
    value = results.get("assumptions")
    grouped = results.get("assumptions_and_gaps")
    return bool(value) or bool(isinstance(grouped, dict) and grouped.get("assumptions"))


def assess(task_dir):
    """Return a Standard-first readiness assessment for ``task_dir``.

    Parameters
    ----------
    task_dir : str
        Task folder path.

    Returns
    -------
    dict
        Machine-readable readiness report.
    """
    task_dir = os.path.abspath(str(task_dir))
    step1 = os.path.join(task_dir, "step1_scope_and_research")
    step2 = os.path.join(task_dir, "step2_analysis")
    step3 = os.path.join(task_dir, "step3_report")
    results_path = os.path.join(task_dir, "results.json")
    results = read_json(results_path, {}) or {}
    has_results = isinstance(results, dict) and bool(results)
    data_gaps = _has_data_gaps(results) if has_results else False

    checks = {
        "task_spec": _nonempty(os.path.join(step1, "task_spec.md")),
        "capability_assessment": _nonempty(os.path.join(step1, "capability_assessment.md")),
        "research_notes": _nonempty(os.path.join(step1, "notes.md")),
        "analysis": _nonempty(os.path.join(step1, "analysis.md")),
        "source_inventory": _nonempty(os.path.join(step1, "references", "collection_manifest.json"))
        or _nonempty(os.path.join(step1, "references", "SOURCES.md")),
        "controlled_documents": _any_file(os.path.join(step1, "references"), (".pdf",)),
        "runnable_step2_artifact": _any_file(step2, (".py", ".ipynb", ".java")),
        "results_json": has_results,
        "assumptions_recorded": _has_assumptions(results) if has_results else False,
        "data_gaps_recorded": data_gaps,
        "consistency_report": _nonempty(os.path.join(task_dir, "consistency_report.json")),
        "formal_report": _formal_report_exists(task_dir),
        "work_record": _nonempty(os.path.join(step3, "WORK_RECORD.md")),
    }

    missing = [name for name, ok in checks.items() if not ok]
    hard_missing = []
    for name in ("task_spec", "capability_assessment", "results_json", "formal_report",
                 "work_record"):
        if not checks[name]:
            hard_missing.append(name)
    if not checks["runnable_step2_artifact"] and not data_gaps:
        hard_missing.append("runnable_step2_artifact")
    if not checks["analysis"] and not data_gaps:
        hard_missing.append("analysis")

    readiness = "ready" if not hard_missing else ("blocked" if data_gaps else "incomplete")
    return {
        "schema_version": "1.0",
        "checked_at": _now(),
        "task": os.path.basename(task_dir),
        "readiness": readiness,
        "ready": readiness == "ready",
        "checks": checks,
        "missing": missing,
        "hard_missing": hard_missing,
        "data_gaps_allow_blocked_basis": data_gaps,
        "message": (
            "Initial Standard task solve is ready for scheduled living cycles"
            if readiness == "ready"
            else "Initial Standard task solve is not fully ready; see hard_missing"
        ),
    }


def _run(command, cwd=None):
    completed = subprocess.run(command, cwd=cwd or DEVTOOLS, capture_output=True, text=True)
    return {
        "command": " ".join('"{}"'.format(part) if " " in part else part for part in command),
        "returncode": completed.returncode,
        "stdout_tail": (completed.stdout or "")[-2000:],
        "stderr_tail": (completed.stderr or "")[-2000:],
    }


def _auto_retrieve_documents(task_dir):
    """Run the automatic document retriever when it has not succeeded recently.

    A successful retrieval is reused for 24 h; an unsuccessful one is retried
    after 6 h so an hourly schedule does not hammer a failing backend.
    """
    out_dir = os.path.join(task_dir, "step1_scope_and_research", "references", "stid")
    previous = read_json(os.path.join(out_dir, "retrieval_status.json"), {}) or {}
    checked = previous.get("checked_at")
    if checked:
        try:
            age_h = (datetime.now(timezone.utc) - datetime.fromisoformat(checked)).total_seconds() / 3600.0
        except ValueError:
            age_h = None
        if age_h is not None:
            if previous.get("status") == "ok" and age_h < 24.0:
                return None
            if previous.get("status") != "ok" and age_h < 6.0:
                return None
    if DEVTOOLS not in sys.path:
        sys.path.insert(0, DEVTOOLS)
    try:
        import doc_retriever
        status = doc_retriever.retrieve_for_task(task_dir, quiet=True)
    except Exception as exc:  # noqa: BLE001
        status = {"status": "error", "message": str(exc)[:300]}
    action = {"kind": "document_retrieval", "status": status.get("status"),
              "documents_selected": status.get("documents_selected", 0),
              "documents_downloaded": status.get("documents_downloaded", 0),
              "documents_failed": status.get("documents_failed", 0),
              "message": status.get("message", "")}
    if status.get("status") == "ok" and os.path.isfile(SOURCES_GENERATOR):
        action["sources_index"] = _run([sys.executable, SOURCES_GENERATOR, task_dir, "--organize"])[
            "returncode"]
    return action


def ensure(task_dir, generate=True, run_consistency=True, retrieve_documents=True):
    """Assess and, when possible, complete the first Standard-task report basis.

    Parameters
    ----------
    task_dir : str
        Task folder path.
    generate : bool
        If true, run existing report/work-record tools when those artifacts are
        missing.
    run_consistency : bool
        If true, run the consistency checker when available.
    retrieve_documents : bool
        If true, run the automatic document retriever (``doc_retriever``) so
        controlled documents are fetched before the report basis is judged.

    Returns
    -------
    dict
        Status written to ``continuous/standard_first_status.json``.
    """
    task_dir = os.path.abspath(str(task_dir))
    actions = []
    if generate and retrieve_documents:
        retrieval = _auto_retrieve_documents(task_dir)
        if retrieval:
            actions.append(retrieval)
    before = assess(task_dir)
    if generate:
        if run_consistency and os.path.isfile(CONSISTENCY_CHECKER):
            actions.append(dict(kind="consistency", **_run([sys.executable, CONSISTENCY_CHECKER, task_dir])))
        if not before["checks"].get("formal_report") and os.path.isfile(CLI):
            actions.append(dict(kind="report", **_run([sys.executable, CLI, "report", task_dir])))
        after_report = assess(task_dir)
        if not after_report["checks"].get("work_record") and os.path.isfile(CLI):
            actions.append(dict(kind="work_record", **_run([sys.executable, CLI, "work-record", task_dir])))
    after = assess(task_dir)
    status = dict(after)
    status["attempted_at"] = _now()
    status["before"] = before
    status["actions"] = actions
    status["operational_for_scheduled_cycles"] = after["ready"]
    os.makedirs(continuous_dir(task_dir), exist_ok=True)
    write_json(os.path.join(continuous_dir(task_dir), STATUS_FILE), status)
    return status
