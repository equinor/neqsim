"""Versioned evidence inventory, change impact, and selective rerun planning.

Evidence remains inside the task folder.  Inventories use task-relative paths and
content hashes so they survive a machine or checkout change.  Unmapped changes fail
closed to a full cycle; selective reruns are used only when every changed path has an
explicit impact rule.
"""

import fnmatch
import glob
import hashlib
import os
from datetime import datetime, timezone

from .plan import continuous_dir, read_json, write_json

SCHEMA_VERSION = "1.0"
DEFAULT_INCLUDE = [
    "step1_scope_and_research/references/**/*",
    "step1_scope_and_research/SOURCES.md",
    "step1_scope_and_research/analysis.md",
    "step1_scope_and_research/capability_assessment.md",
    "user_input.md",
    "study_config.yaml",
    "continuous/goal.yaml",
    "continuous/user_input.yaml",
]
CONTROL_STAGES = ("kpis", "goal", "diff", "ledger", "digest", "notify", "agent")


class EvidenceSchemaError(ValueError):
    """Raised when an evidence inventory needs a newer or incompatible runner."""


def _now():
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat()


def _sha256(path):
    digest = hashlib.sha256()
    with open(path, "rb") as evidence_file:
        for chunk in iter(lambda: evidence_file.read(65536), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _kind(path):
    extension = os.path.splitext(path)[1].lower()
    return {
        ".csv": "measurement_data", ".tsv": "measurement_data", ".json": "structured_data",
        ".yaml": "requirements_or_configuration", ".yml": "requirements_or_configuration",
        ".pdf": "technical_document", ".doc": "technical_document", ".docx": "technical_document",
        ".xls": "datasheet", ".xlsx": "datasheet", ".dwg": "drawing", ".dxf": "drawing",
        ".ipynb": "model_or_calculation", ".py": "model_or_calculation",
        ".java": "model_or_calculation", ".md": "technical_document",
    }.get(extension, "evidence_file")


def inventory(task_dir, config=None):
    """Return a deterministic content inventory for configured task-local evidence."""
    task_dir = os.path.abspath(str(task_dir))
    root = os.path.realpath(task_dir)
    config = dict(config or {})
    includes = list(config.get("include") or DEFAULT_INCLUDE)
    excludes = list(config.get("exclude") or [])
    found = {}
    for pattern in includes:
        absolute_pattern = os.path.join(task_dir, str(pattern).replace("/", os.sep))
        for path in glob.glob(absolute_pattern, recursive=True):
            if not os.path.isfile(path):
                continue
            real = os.path.realpath(path)
            if os.path.commonpath([root, real]) != root:
                continue
            relative = os.path.relpath(path, task_dir).replace(os.sep, "/")
            if any(fnmatch.fnmatch(relative, item) for item in excludes):
                continue
            stat = os.stat(path)
            found[relative] = {
                "sha256": _sha256(path),
                "bytes": stat.st_size,
                "kind": _kind(relative),
            }
    return {"schema_version": SCHEMA_VERSION, "captured_at": _now(),
            "files": dict(sorted(found.items()))}


def _changes(previous, current):
    before = (previous or {}).get("files") or {}
    after = (current or {}).get("files") or {}
    rows = []
    for path in sorted(set(before) | set(after)):
        old, new = before.get(path), after.get(path)
        if old is None:
            change = "added"
        elif new is None:
            change = "removed"
        elif old.get("sha256") != new.get("sha256"):
            change = "modified"
        else:
            continue
        rows.append({"path": path, "change": change,
                     "kind": (new or old).get("kind"),
                     "before_sha256": old.get("sha256") if old else None,
                     "after_sha256": new.get("sha256") if new else None})
    return rows


def _patterns(rule):
    value = rule.get("match") or rule.get("matches") or []
    return [value] if isinstance(value, str) else list(value)


def _validated_inventory(data):
    if not data:
        return {}
    if not isinstance(data, dict):
        raise EvidenceSchemaError("continuous/evidence/inventory.json must contain a JSON object")
    version = str(data.get("schema_version") or "")
    if version != SCHEMA_VERSION:
        raise EvidenceSchemaError(
            "continuous/evidence/inventory.json uses schema {} but this runner supports {}. "
            "Upgrade NeqSim before processing evidence changes.".format(
                version or "(missing)", SCHEMA_VERSION))
    if not isinstance(data.get("files"), dict):
        raise EvidenceSchemaError("continuous/evidence/inventory.json: files must be an object")
    return data


def analyze(task_dir, plan):
    """Compare current evidence with the accepted inventory and derive impact."""
    config = dict((plan or {}).get("evidence") or {})
    path = os.path.join(continuous_dir(task_dir), "evidence", "inventory.json")
    previous = _validated_inventory(read_json(path, {}) or {})
    current = inventory(task_dir, config)
    initialized = not bool(previous.get("schema_version"))
    changes = [] if initialized else _changes(previous, current)
    affected_stages, affected_kpis = set(), set()
    conclusions, matched_rules, unmapped = set(), set(), []
    provenance = {}
    rules = list(config.get("rules") or [])
    for row in changes:
        matched = []
        for index, rule in enumerate(rules):
            if any(fnmatch.fnmatch(row["path"], pattern) for pattern in _patterns(rule)):
                matched.append(rule)
                rule_name = rule.get("name") or "rule-{}".format(index + 1)
                matched_rules.add(rule_name)
                affected_stages.update(rule.get("stages") or [])
                affected_kpis.update(rule.get("kpis") or [])
                conclusions.update(rule.get("conclusions") or [])
                link = provenance.setdefault(rule_name, {
                    "rule": rule_name,
                    "stages": list(rule.get("stages") or []),
                    "kpis": list(rule.get("kpis") or []),
                    "conclusions": list(rule.get("conclusions") or []),
                    "evidence": [],
                })
                link["evidence"].append({
                    "path": row["path"], "change": row["change"],
                    "sha256": row.get("after_sha256") or row.get("before_sha256"),
                })
        row["impact_rules"] = [r.get("name") or "rule" for r in matched]
        if not matched:
            unmapped.append(row["path"])
    full_rerun = bool(changes and unmapped)
    if full_rerun:
        affected_stages.update(plan.get("stages") or [])
        conclusions.add("all validated conclusions (unmapped evidence change)")
    return {
        "schema_version": SCHEMA_VERSION,
        "status": "initialized" if initialized else ("changed" if changes else "unchanged"),
        "compared_at": _now(),
        "previous_captured_at": previous.get("captured_at"),
        "changes": changes,
        "changed_files": len(changes),
        "affected_stages": sorted(affected_stages),
        "affected_kpis": sorted(affected_kpis),
        "affected_conclusions": sorted(conclusions),
        "matched_rules": sorted(matched_rules),
        "provenance": [provenance[name] for name in sorted(provenance)],
        "unmapped_paths": unmapped,
        "full_rerun": full_rerun,
        "inventory": current,
    }


def initialize(task_dir, plan):
    """Create the accepted starting inventory without inventing a change event."""
    result = analyze(task_dir, plan)
    if result["status"] == "initialized":
        accepted = dict(result["inventory"], accepted_at=_now(), accepted_by="task-living")
        write_json(os.path.join(continuous_dir(task_dir), "evidence", "inventory.json"), accepted)
    return result


def selected_stages(plan, analysis):
    """Return plan-ordered stages for a conservative impact-selected cycle."""
    planned = list(plan.get("stages") or [])
    if analysis.get("full_rerun"):
        selected = set(planned)
    else:
        selected = set(analysis.get("affected_stages") or [])
        selected.update(stage for stage in CONTROL_STAGES if stage in planned)
        dependencies = dict((plan.get("evidence") or {}).get("dependencies") or {})
        changed = True
        while changed:
            changed = False
            for stage in list(selected):
                for dependency in dependencies.get(stage, []) or []:
                    if dependency not in selected:
                        selected.add(dependency)
                        changed = True
    unknown = sorted(stage for stage in selected if stage not in planned)
    if unknown:
        raise ValueError("evidence impact rules name stages not present in cycle_plan.yaml: {}"
                         .format(", ".join(unknown)))
    ordered = [stage for stage in planned if stage in selected]
    if "sense" not in ordered:
        ordered.insert(0, "sense")
    insert_at = ordered.index("sense") + 1
    if "evidence" not in ordered:
        ordered.insert(insert_at, "evidence")
    return ordered


def accept(task_dir, analysis, cycle_id):
    """Advance the accepted inventory after a successful impact-selected cycle."""
    accepted = dict(analysis["inventory"], accepted_at=_now(), accepted_cycle=cycle_id)
    write_json(os.path.join(continuous_dir(task_dir), "evidence", "inventory.json"), accepted)
    return accepted


__all__ = ["SCHEMA_VERSION", "DEFAULT_INCLUDE", "EvidenceSchemaError", "accept", "analyze",
           "initialize", "inventory", "selected_stages"]
