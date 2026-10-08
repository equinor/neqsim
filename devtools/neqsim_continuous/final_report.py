"""Create immutable final-report revisions through the canonical Task Solver pipeline.

A final report is not a second report architecture.  This module validates the latest
promoted continuous-task baseline, invokes ``neqsim report`` with a separate output
directory, and records the resulting Word/HTML files as an immutable ``FR-###`` revision.
The ordinary report in ``step3_report/`` remains the current-best Task Solver report.
"""

import hashlib
import json
import os
import re
import subprocess
import sys
from datetime import datetime, timezone

from .plan import continuous_dir, file_sha256, load_baseline, load_plan, read_json, write_json

FINAL_REPORT_SCHEMA_VERSION = "1.0"
_SUPPORTED_SCHEMA = (1, 0)
_REVISION_PATTERN = re.compile(r"^FR-(\d{3})$")
_CURRENT_FILE = "final_report.json"
_HISTORY_DIR = "final_reports"


class FinalReportError(ValueError):
    """Raised when a task is not ready for a trustworthy final report."""


def _iso_now(value=None):
    if value is None:
        value = datetime.now(timezone.utc)
    if isinstance(value, str):
        value = datetime.fromisoformat(value.replace("Z", "+00:00"))
    if value.tzinfo is None:
        value = value.replace(tzinfo=timezone.utc)
    return value.astimezone(timezone.utc).replace(microsecond=0).isoformat()


def _schema_version(value):
    text = str(value or "").strip()
    try:
        major, minor = text.split(".", 1)
        return int(major), int(minor)
    except (TypeError, ValueError):
        raise FinalReportError("Invalid final-report schema_version '{}'".format(text))


def _validate_manifest(data, path):
    if not isinstance(data, dict):
        raise FinalReportError("{} must contain a JSON object".format(path))
    version = _schema_version(data.get("schema_version"))
    if version[0] != _SUPPORTED_SCHEMA[0] or version > _SUPPORTED_SCHEMA:
        raise FinalReportError(
            "{} uses final-report schema {} but this runner supports up to {}. "
            "Upgrade NeqSim before reading or replacing the final report.".format(
                path, data.get("schema_version"), FINAL_REPORT_SCHEMA_VERSION))
    return data


def _canonical_json_hash(path, ignored_keys=()):
    if not os.path.isfile(path):
        return None
    with open(path, "r", encoding="utf-8-sig") as handle:
        data = json.load(handle)
    if isinstance(data, dict):
        data = {key: value for key, value in data.items() if key not in ignored_keys}
    payload = json.dumps(data, sort_keys=True, separators=(",", ":"), ensure_ascii=False)
    return hashlib.sha256(payload.encode("utf-8")).hexdigest()


def _relative_files(task_dir, folder, suffixes=None):
    root = os.path.join(task_dir, folder)
    rows = []
    if not os.path.isdir(root):
        return rows
    for current, directories, filenames in os.walk(root):
        directories[:] = sorted(name for name in directories if not name.startswith("."))
        for name in sorted(filenames):
            if suffixes and not name.lower().endswith(suffixes):
                continue
            path = os.path.join(current, name)
            rows.append({"path": os.path.relpath(path, task_dir).replace(os.sep, "/"),
                         "sha256": file_sha256(path)})
    return rows


def _source_descriptor(task_dir):
    """Return report-affecting inputs with task-relative paths and stable hashes."""
    task_dir = os.path.abspath(str(task_dir))
    cont = continuous_dir(task_dir)
    baseline_dir = os.path.join(cont, "baseline")
    report_dir = os.path.join(task_dir, "step3_report")
    paths = {
        "study_config": os.path.join(task_dir, "study_config.yaml"),
        "task_spec": os.path.join(task_dir, "step1_scope_and_research", "task_spec.md"),
        "report_sections": os.path.join(report_dir, "report_sections.json"),
        "work_record": os.path.join(report_dir, "WORK_RECORD.md"),
        "baseline": os.path.join(baseline_dir, "baseline.json"),
        "baseline_results": os.path.join(baseline_dir, "results_snapshot.json"),
        "evidence_inventory": os.path.join(cont, "evidence", "inventory.json"),
        "sources": os.path.join(task_dir, "step1_scope_and_research", "references", "SOURCES.md"),
        "collection_manifest": os.path.join(
            task_dir, "step1_scope_and_research", "references", "collection_manifest.json"),
    }
    descriptor = {
        "results": {
            "path": "results.json",
            # The canonical generator refreshes build-environment provenance.  That field
            # is not an engineering-result change and must not create a spurious FR revision.
            "content_sha256": _canonical_json_hash(
                os.path.join(task_dir, "results.json"), ignored_keys=("environment",)),
            "file_sha256": file_sha256(os.path.join(task_dir, "results.json")),
        },
        "files": {},
        "figures": _relative_files(task_dir, "figures"),
    }
    for key, path in paths.items():
        descriptor["files"][key] = {
            "path": os.path.relpath(path, task_dir).replace(os.sep, "/"),
            "sha256": file_sha256(path),
        }
    return descriptor


def _input_fingerprint(descriptor):
    stable = json.loads(json.dumps(descriptor))
    # Raw results bytes include a refreshed environment stamp.  The canonical content
    # hash above represents the validated engineering result for staleness decisions.
    stable.get("results", {}).pop("file_sha256", None)
    payload = json.dumps(stable, sort_keys=True, separators=(",", ":"), ensure_ascii=False)
    return hashlib.sha256(payload.encode("utf-8")).hexdigest()


def _manifest_path(task_dir):
    return os.path.join(continuous_dir(task_dir), _CURRENT_FILE)


def load_manifest(task_dir):
    """Return and validate the current final-report pointer, or ``{}`` when absent."""
    path = _manifest_path(task_dir)
    data = read_json(path, {}) or {}
    return _validate_manifest(data, path) if data else {}


def _output_status(task_dir, manifest):
    rows = []
    valid = True
    for item in manifest.get("outputs") or []:
        item = item if isinstance(item, dict) else {}
        relative = str(item.get("path") or "")
        task_root = os.path.realpath(task_dir)
        path = os.path.realpath(os.path.join(task_root, relative))
        contained = bool(relative and not os.path.isabs(relative)
                         and os.path.commonpath((task_root, path)) == task_root)
        actual = file_sha256(path) if contained else None
        ok = bool(actual and actual == item.get("sha256"))
        rows.append(dict(item, exists=contained and os.path.isfile(path), hash_matches=ok))
        valid = valid and ok
    return rows, bool(rows) and valid


def status(task_dir):
    """Return final-report availability and staleness without mutating the task."""
    task_dir = os.path.abspath(str(task_dir))
    manifest = load_manifest(task_dir)
    if not manifest:
        return {"available": False, "revision": None, "stale": None,
                "outputs_valid": False, "outputs": []}
    descriptor = _source_descriptor(task_dir)
    fingerprint = _input_fingerprint(descriptor)
    outputs, outputs_valid = _output_status(task_dir, manifest)
    return {
        "available": outputs_valid,
        "revision": manifest.get("revision"),
        "finalized_at": manifest.get("finalized_at"),
        "reviewer": manifest.get("reviewer"),
        "baseline": (manifest.get("source") or {}).get("baseline_id"),
        "stale": fingerprint != manifest.get("input_fingerprint"),
        "outputs_valid": outputs_valid,
        "outputs": outputs,
        "manifest": os.path.relpath(_manifest_path(task_dir), task_dir).replace(os.sep, "/"),
    }


def _preflight(task_dir, reviewer):
    from .cycle import find_incomplete_cycle
    from .evidence import analyze

    if not reviewer or not str(reviewer).strip():
        raise FinalReportError("A named reviewer is required to generate a final report")
    if find_incomplete_cycle(task_dir):
        raise FinalReportError("Resume or complete the interrupted cycle before finalizing")
    impact = analyze(task_dir, load_plan(task_dir))
    if impact.get("changes"):
        raise FinalReportError(
            "Evidence changed after the accepted inventory. Run `neqsim task-update <task>` "
            "and promote the validated result before finalizing.")
    baseline = load_baseline(task_dir)
    meta = baseline.get("meta") or {}
    cycle_id = meta.get("source_cycle")
    if not cycle_id:
        raise FinalReportError(
            "The current baseline has not been promoted from a reviewed cycle. "
            "Run `neqsim task-promote <task> <cycle> --reviewer NAME` first.")
    cycle = read_json(os.path.join(continuous_dir(task_dir), "cycles", cycle_id, "cycle.json"), {}) or {}
    if cycle.get("status") != "complete" or cycle.get("degraded"):
        raise FinalReportError(
            "Baseline cycle {} is not a complete, non-degraded result".format(cycle_id))
    results_path = os.path.join(task_dir, "results.json")
    snapshot_path = os.path.join(continuous_dir(task_dir), "baseline", "results_snapshot.json")
    if not os.path.isfile(results_path) or not os.path.isfile(snapshot_path):
        raise FinalReportError("The promoted results.json snapshot is missing")
    current_hash = _canonical_json_hash(results_path, ignored_keys=("environment",))
    snapshot_hash = _canonical_json_hash(snapshot_path, ignored_keys=("environment",))
    if current_hash != snapshot_hash:
        raise FinalReportError(
            "results.json differs from the promoted baseline snapshot. Validate and promote it "
            "before generating a final report.")
    return {"baseline": meta, "cycle": cycle, "evidence": impact}


def _next_revision(task_dir):
    folder = os.path.join(continuous_dir(task_dir), _HISTORY_DIR)
    output_root = os.path.join(task_dir, "step3_report", "final")
    highest = 0
    for candidate in (folder, output_root):
        if not os.path.isdir(candidate):
            continue
        for name in os.listdir(candidate):
            match = _REVISION_PATTERN.match(os.path.splitext(name)[0])
            if match:
                highest = max(highest, int(match.group(1)))
    return "FR-{:03d}".format(highest + 1)


def _study_title(task_dir):
    try:
        import yaml
        with open(os.path.join(task_dir, "study_config.yaml"), "r", encoding="utf-8") as handle:
            config = yaml.safe_load(handle) or {}
        title = (config.get("study") or {}).get("title")
        if title:
            return str(title)
    except (ImportError, OSError, ValueError, AttributeError):
        pass
    return os.path.basename(task_dir).replace("_", " ")


def _default_runner(task_dir, output_dir, metadata_path, title, pdf):
    cli = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "neqsim_cli.py")
    command = [sys.executable, cli, "report", task_dir, "--output-dir", output_dir,
               "--final-metadata", metadata_path, "--title", title]
    if pdf:
        command.append("--pdf")
    return subprocess.call(command)


def finalize(task_dir, reviewer, note="", pdf=False, now=None, runner=None):
    """Generate or reuse an immutable final-report revision.

    The same validated inputs, reviewer and note are idempotent.  A changed promoted
    baseline or report input produces the next ``FR-###`` revision; earlier revisions
    remain untouched.
    """
    task_dir = os.path.abspath(str(task_dir))
    gate = _preflight(task_dir, reviewer)
    before = _source_descriptor(task_dir)
    fingerprint = _input_fingerprint(before)
    current = load_manifest(task_dir)
    if current and current.get("input_fingerprint") == fingerprint \
            and current.get("reviewer") == str(reviewer).strip() \
            and current.get("note", "") == str(note or ""):
        _outputs, valid = _output_status(task_dir, current)
        if valid:
            return dict(current, reused=True)

    revision = _next_revision(task_dir)
    finalized_at = _iso_now(now)
    history_dir = os.path.join(continuous_dir(task_dir), _HISTORY_DIR)
    output_dir = os.path.join(task_dir, "step3_report", "final", revision)
    os.makedirs(history_dir, exist_ok=True)
    os.makedirs(output_dir, exist_ok=False)
    pending_path = os.path.join(history_dir, ".{}.pending.json".format(revision))
    source = {
        "baseline_id": gate["baseline"].get("id"),
        "source_cycle": gate["baseline"].get("source_cycle"),
        "promoted_by": gate["baseline"].get("promoted_by"),
        "promoted_at": gate["baseline"].get("promoted_at"),
    }
    audit = {
        "work_record": "step3_report/WORK_RECORD.md",
        "living_report": "continuous/LIVING_REPORT.md",
        "ledger": "continuous/ledger/events.jsonl",
        "cycle": "continuous/cycles/{}/cycle.json".format(source["source_cycle"]),
    }
    pending = {
        "schema_version": FINAL_REPORT_SCHEMA_VERSION,
        "revision": revision,
        "status": "generating",
        "finalized_at": finalized_at,
        "reviewer": str(reviewer).strip(),
        "note": str(note or ""),
        "source": source,
        "audit_trail": audit,
    }
    write_json(pending_path, pending)
    title = "{} - Final Report".format(_study_title(task_dir))
    report_runner = runner or _default_runner
    try:
        exit_code = report_runner(task_dir, output_dir, pending_path, title, pdf)
    finally:
        try:
            os.remove(pending_path)
        except OSError:
            pass
    if exit_code:
        raise FinalReportError("Canonical Task Solver report generation failed with exit code {}"
                               .format(exit_code))

    output_index = read_json(os.path.join(output_dir, ".report_outputs.json"), {}) or {}
    names = output_index.get("files") or []
    required = (".docx", ".html") + ((".pdf",) if pdf else ())
    outputs = []
    for name in names:
        path = os.path.join(output_dir, os.path.basename(name))
        if os.path.isfile(path):
            outputs.append({"path": os.path.relpath(path, task_dir).replace(os.sep, "/"),
                            "format": os.path.splitext(path)[1].lstrip(".").lower(),
                            "sha256": file_sha256(path), "bytes": os.path.getsize(path)})
    formats = {"." + item["format"] for item in outputs}
    missing = [suffix for suffix in required if suffix not in formats]
    if missing:
        raise FinalReportError("Canonical generator did not produce required final format(s): {}"
                               .format(", ".join(missing)))

    after = _source_descriptor(task_dir)
    manifest = {
        "schema_version": FINAL_REPORT_SCHEMA_VERSION,
        "revision": revision,
        "status": "complete",
        "finalized_at": finalized_at,
        "reviewer": str(reviewer).strip(),
        "note": str(note or ""),
        "source": source,
        "source_files": after,
        "input_fingerprint": _input_fingerprint(after),
        "audit_trail": dict(audit, hashes={
            key: file_sha256(os.path.join(task_dir, value)) for key, value in audit.items()}),
        "pipeline": {
            "kind": "canonical_task_solver_report",
            "generator": "devtools/task_template/step3_report/generate_report.py",
        },
        "outputs": outputs,
    }
    revision_manifest = os.path.join(history_dir, revision + ".json")
    write_json(revision_manifest, manifest)
    write_json(os.path.join(output_dir, "FINAL_REPORT_MANIFEST.json"), manifest)
    write_json(_manifest_path(task_dir), manifest)
    from .living_report import update
    update(task_dir, event="final")
    return dict(manifest, reused=False)


__all__ = ["FINAL_REPORT_SCHEMA_VERSION", "FinalReportError", "finalize", "load_manifest",
           "status"]
