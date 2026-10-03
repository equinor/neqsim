"""Make a task living, promote a cycle to the baseline, and read the loop state."""

import json
import os
import shutil
from datetime import datetime, timezone

from .ledger import CATEGORIES, Ledger
from .plan import (GOAL_FILE, PLAN_FILE, continuous_dir, file_sha256, load_baseline, load_goal,
                   read_json, write_json)
from .stages import neqsim_commit

PLAN_TEMPLATE = """# Living-task cycle plan (neqsim_continuous schema 1.0). Every section is optional.
schema_version: "1.0"
# data_root: "C:/neqsim-data/<task>"   # keep large partitions off synced folders
sources: {}
#  historian:
#    adapter: file                      # file (built in), tagreader (community), ots (enterprise), ...
#    options: {folder: data_drop, time_column: timestamp, pattern: "*.csv"}
#    initial_lookback_days: 1
#    stale_after_hours: 36
kpis: {}
#  suction_pressure_bara: {source: historian, column: suction_pressure_bara, agg: mean}
#  headroom_MSm3d: {from: results, key: headroom_to_98pct_power_MSm3d}
scripts: {}
#  model: {file: continuous/stages/model.py, function: run}   # use as stage "script:model"
drift:
  signals: []
  settings: {lambda: 0.2, L: 3.5, k: 0.5, h: 6.0, warmup: 30}
triggers:
  kpi_step: {}
  criteria: {}
stages: [sense, refresh, kpis, drift, goal, diff, ledger, digest, notify, agent]
notify:
  channels: [file]
#   - {type: email, host: smtp.example.com, to: [you@example.com], user: you, password_env: NEQSIM_SMTP_PASSWORD, when: [needs_decision]}
#   - {type: teams, url_env: NEQSIM_TEAMS_WEBHOOK, when: [needs_decision, stop_state]}
agent: {enabled: false, agent: continuous-improvement, executable: copilot}
solve: {}
#  stages: [sense, refresh, "script:solver", kpis, goal, diff, ledger, digest]
#  max_rounds: 12
#  max_branches: 3
#  critic: {enabled: false}
backtest: {}
#  expected: [{trigger: "drift:polytropic_efficiency", onset: "2026-02-01", max_delay_days: 30}]
# continuous/LIVING_REPORT.md is always rewritten; formal also regenerates the Word/HTML report
report: {formal: never}                 # never | on_promote | every_cycle
"""

CATEGORY_HINTS = (
    ("model", ("re-fit", "refit", "calibrat", "model")),
    ("maintenance", ("wash", "monitor", "inspect", "maintenance", "clean")),
    ("modification", ("uprat", "modif", "retrofit", "screening", "vendor", "install")),
    ("data", ("data", "measure", "sample")),
)


def _now():
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat()


def _guess_category(text):
    lower = text.lower()
    for category, words in CATEGORY_HINTS:
        if any(w in lower for w in words):
            return category
    return "operational"


def _brief_sections(text):
    return [line.lstrip("#").strip() for line in text.splitlines() if line.startswith("#")]


def _find_brief(task_dir, brief):
    if brief:
        return brief
    config = os.path.join(task_dir, "study_config.yaml")
    if os.path.exists(config):
        import yaml
        with open(config, "r", encoding="utf-8") as f:
            data = yaml.safe_load(f) or {}
        relative = (data.get("inputs") or {}).get("prompt_file")
        if relative:
            return os.path.join(task_dir, relative)
    return None


def make_living(task_dir, brief=None):
    """Scaffold ``continuous/`` for an existing task. Never overwrites existing files."""
    task_dir = os.path.abspath(str(task_dir))
    cont = continuous_dir(task_dir)
    report = {"created": [], "kept": []}
    for sub in ("baseline", "cycles", "ledger", "stages"):
        os.makedirs(os.path.join(cont, sub), exist_ok=True)

    def _write(path, writer):
        if os.path.exists(path):
            report["kept"].append(os.path.relpath(path, task_dir))
        else:
            writer(path)
            report["created"].append(os.path.relpath(path, task_dir))

    def _plan(path):
        with open(path, "w", encoding="utf-8") as f:
            f.write(PLAN_TEMPLATE)

    _write(os.path.join(cont, PLAN_FILE), _plan)

    brief_path = _find_brief(task_dir, brief)
    if brief_path and not os.path.isabs(brief_path):
        brief_path = os.path.abspath(brief_path)
    if brief_path and os.path.isfile(brief_path):
        manual = os.path.join(task_dir, "step1_scope_and_research", "references", "manual")
        if os.path.dirname(os.path.abspath(brief_path)) != os.path.abspath(manual):
            os.makedirs(manual, exist_ok=True)
            target = os.path.join(manual, os.path.basename(brief_path))
            if not os.path.exists(target):
                shutil.copy2(brief_path, target)
            brief_path = target

    def _goal(path):
        import yaml
        try:
            from new_task import read_prompt_file
        except ImportError:  # used outside devtools: plain text briefs only
            def read_prompt_file(path):
                with open(path, "r", encoding="utf-8-sig") as f:
                    return f.read()
        sections, relative, digest = [], None, None
        if brief_path and os.path.isfile(brief_path):
            sections = _brief_sections(read_prompt_file(brief_path))
            relative = os.path.relpath(brief_path, task_dir).replace("\\", "/")
            digest = file_sha256(brief_path)
        goal = {"schema_version": "1.0", "brief": relative, "brief_sha256": digest,
                "brief_sections": sections, "confirmed_by": None,
                "objective": {"metric": None, "direction": "maximize", "target": None,
                              "confidence_required": "medium", "source_section": None},
                "secondary_metrics": [], "constraints": [],
                "stop": {"goal_met": {"validated": True},
                         "converged": {"window": 3, "relative": 0.02, "absolute": 0.0},
                         "budget": {"iterations": 12, "agent_sessions": 6},
                         "expected_improvement_cost": 0.0},
                "reopen": {"regress_margin": None,
                           "on": ["new_evidence", "brief_changed", "neqsim_capability",
                                  "reviewer_request"]}}
        with open(path, "w", encoding="utf-8") as f:
            f.write("# Compiled from the task brief. The agent fills objective/constraints; the\n"
                    "# engineer sets confirmed_by before a solve loop may use it.\n")
            yaml.safe_dump(goal, f, sort_keys=False)

    _write(os.path.join(cont, GOAL_FILE), _goal)

    results = read_json(os.path.join(task_dir, "results.json"), {}) or {}
    base = os.path.join(cont, "baseline")

    def _baseline(path):
        kpis = {k: float(v) for k, v in (results.get("key_results") or {}).items()
                if isinstance(v, (int, float)) and not isinstance(v, bool)}
        write_json(os.path.join(base, "kpis.json"), kpis)
        write_json(os.path.join(base, "results_snapshot.json"), results)
        write_json(path, {"schema_version": "1.0", "id": "B-{}".format(_now()[:10]),
                          "promoted_at": _now(), "promoted_by": "task-living (initial baseline)",
                          "source": "results.json", "versions": {"neqsim_commit": neqsim_commit()},
                          "references_sha256": file_sha256(os.path.join(
                              task_dir, "step1_scope_and_research", "references",
                              "collection_manifest.json"))})

    _write(os.path.join(base, "baseline.json"), _baseline)

    ledger_path = os.path.join(cont, "ledger", "events.jsonl")

    def _ledger(path):
        book = Ledger(path)
        for text in results.get("recommendations") or []:
            if isinstance(text, str) and text.strip():
                book.create(text.strip()[:200], _guess_category(text), description=text.strip(),
                            evidence=["results.json#recommendations"])
        if not os.path.exists(path):
            open(path, "w").close()

    _write(ledger_path, _ledger)
    _write(os.path.join(cont, "state.json"),
           lambda p: write_json(p, {"schema_version": "1.0", "state": "draft",
                                    "phase": "draft", "updated": _now()}))

    config = os.path.join(task_dir, "study_config.yaml")
    if os.path.exists(config):
        with open(config, "r", encoding="utf-8") as f:
            text = f.read()
        if "\ncontinuous:" not in text and not text.startswith("continuous:"):
            with open(config, "a", encoding="utf-8") as f:
                f.write("\ncontinuous:\n  enabled: true\n  plan: continuous/cycle_plan.yaml\n")
            report["created"].append("study_config.yaml: continuous block")
    from .living_report import update
    if update(task_dir, event="living"):
        report["living_report"] = "continuous/LIVING_REPORT.md"
    return report


def promote(task_dir, cycle_id, reviewer, note=""):
    """Promote a complete cycle to the baseline; the previous baseline is archived."""
    task_dir = os.path.abspath(str(task_dir))
    if not reviewer:
        raise ValueError("A named reviewer is required to promote a baseline")
    cont = continuous_dir(task_dir)
    cycle_dir = os.path.join(cont, "cycles", cycle_id)
    manifest = read_json(os.path.join(cycle_dir, "cycle.json"))
    if not manifest or manifest.get("status") != "complete":
        raise ValueError("Cycle {} is not complete".format(cycle_id))
    base = os.path.join(cont, "baseline")
    old = load_baseline(task_dir)["meta"]
    old_kpis = load_baseline(task_dir)["kpis"] or {}
    if old.get("id"):
        archive = os.path.join(base, "history", old["id"])
        os.makedirs(archive, exist_ok=True)
        for name in os.listdir(base):
            if os.path.isfile(os.path.join(base, name)):
                shutil.copy2(os.path.join(base, name), os.path.join(archive, name))
    # A solve round reports only the objective; keep the baseline of every KPI it did not report.
    write_json(os.path.join(base, "kpis.json"),
               dict(old_kpis, **(read_json(os.path.join(cycle_dir, "kpis.json"), {}) or {})))
    candidate = os.path.join(cycle_dir, "results.json")
    if os.path.exists(candidate):
        shutil.copy2(candidate, os.path.join(task_dir, "results.json"))
        shutil.copy2(candidate, os.path.join(base, "results_snapshot.json"))
    sense = read_json(os.path.join(cycle_dir, "sense.json"), {}) or {}
    meta = {"schema_version": "1.0", "id": "B-" + cycle_id, "promoted_at": _now(),
            "promoted_by": reviewer, "note": note, "source_cycle": cycle_id,
            "previous": old.get("id"), "versions": manifest.get("versions", {}),
            "references_sha256": sense.get("references_sha256")}
    write_json(os.path.join(base, "baseline.json"), meta)
    from .living_report import update
    update(task_dir, event="promote")
    return meta


STATE_SCHEMA_VERSION = "1.0"


def read_state(task_dir):
    """Read persisted task state and validate its schema version."""
    state = read_json(os.path.join(continuous_dir(task_dir), "state.json"), {}) or {}
    schema = str(state.get("schema_version") or STATE_SCHEMA_VERSION)
    if schema != STATE_SCHEMA_VERSION:
        raise ValueError("Unsupported continuous task state schema {} (expected {})".format(
            schema, STATE_SCHEMA_VERSION))
    state.setdefault("schema_version", STATE_SCHEMA_VERSION)
    return state


def write_state(task_dir, state):
    state = dict(state, schema_version=STATE_SCHEMA_VERSION, updated=_now())
    write_json(os.path.join(continuous_dir(task_dir), "state.json"), state)
    return state


def note_reopen(task_dir, manifest):
    """Mark a stopped task for reopening when a monitor cycle raised a reopen trigger."""
    reasons = [t.split(":", 1)[1] for t in manifest.get("triggers", []) if t.startswith("reopen:")]
    state = read_state(task_dir)
    if reasons and state.get("phase") == "monitoring":
        state.update({"phase": "reopen_requested", "reopen_reasons": reasons,
                      "reopen_cycle": manifest.get("cycle_id")})
        write_state(task_dir, state)
        from .living_report import update
        update(task_dir, event="reopen")
    return reasons


def status(task_dir):
    """Return a five-second status summary that is sufficient to resume work."""
    from .cycle import interrupted_cycles, list_cycles, load_cycle
    from .schedule import schedule_status

    task_dir = os.path.abspath(str(task_dir))
    cycles = list_cycles(task_dir)
    manifests = [load_cycle(task_dir, cycle_id) or {} for cycle_id in cycles]
    last = manifests[-1] if manifests else {}
    completed = [m for m in manifests if m.get("status") == "complete"]
    running = interrupted_cycles(task_dir)

    items = Ledger(os.path.join(continuous_dir(task_dir), "ledger", "events.jsonl")).current()
    open_items = [k for k, v in items.items()
                  if v.get("status") in ("proposed", "under_review", "accepted", "implemented")]
    rejected = [{"id": key, "title": item.get("title"), "status": item.get("status")}
                for key, item in sorted(items.items())
                if item.get("status") in ("rejected", "superseded")]

    state = read_state(task_dir)
    goal = load_goal(task_dir) or {}
    objective = goal.get("objective") or {}
    baseline = load_baseline(task_dir)
    baseline_meta = baseline.get("meta") or {}
    baseline_kpis = baseline.get("kpis") or {}
    metric = objective.get("metric")
    history = state.get("history") or []
    validated = [h for h in history
                 if h.get("validated") and isinstance(h.get("value"), (int, float))]
    best = None
    if validated:
        reverse = objective.get("direction", "maximize") != "minimize"
        row = sorted(validated, key=lambda h: h.get("value"), reverse=reverse)[0]
        best = {key: row.get(key) for key in
                ("round", "cycle", "value", "confidence", "action")}

    solve = (last.get("solve") or {}) if last else {}
    details = state.get("details") or {}
    standard = (last.get("standard_first") or {}) if last else {}
    blockers = [str(x) for x in (solve.get("blockers") or []) if x]
    blockers += [str(x) for x in (details.get("blockers") or []) if x]
    blockers += ["standard_first:" + str(x) for x in (standard.get("hard_missing") or [])]
    if state.get("state") == "blocked" and state.get("reason"):
        blockers.append(str(state.get("reason")))
    blockers = list(dict.fromkeys(blockers))

    next_action = details.get("next_action")
    if not next_action and running:
        next_action = "Resume interrupted cycle {} with task-cycle.".format(
            running[0].get("cycle_id"))
    if not next_action and not goal.get("confirmed_by"):
        next_action = "Confirm continuous/goal.yaml before the next solve loop."
    if not next_action and state.get("phase") == "reopen_requested":
        next_action = "Run task-solve to reassess the reopened task."
    if not next_action and last.get("triggers"):
        next_action = "Triage the latest cycle triggers before changing the baseline."
    if not next_action:
        next_action = "Run the next monitoring cycle."

    latest_validation = None
    if history:
        latest_validation = {"validated": bool(history[-1].get("validated")),
                             "confidence": history[-1].get("confidence"),
                             "cycle": history[-1].get("cycle")}
    changed = list(last.get("triggers", []) if last else [])
    if last.get("resume_count"):
        changed.append("resumed_interrupted_cycle")
    schedule = schedule_status(task_dir)

    return {
        "schema_version": STATE_SCHEMA_VERSION,
        "task": os.path.basename(task_dir),
        "state": state.get("state"),
        "phase": state.get("phase"),
        "conclusion": state.get("reason"),
        "goal": {"metric": metric, "direction": objective.get("direction", "maximize"),
                 "target": objective.get("target"), "confirmed_by": goal.get("confirmed_by")},
        "baseline": baseline_meta.get("id"),
        "baseline_detail": {"id": baseline_meta.get("id"),
                            "promoted_at": baseline_meta.get("promoted_at"),
                            "promoted_by": baseline_meta.get("promoted_by"),
                            "objective_value": baseline_kpis.get(metric) if metric else None},
        "best_validated": best,
        "attempts": len(history) if history else len(completed),
        "rejected_hypotheses": rejected,
        "blockers": blockers,
        "evidence": {"baseline_references_sha256": baseline_meta.get("references_sha256"),
                     "standard_first": standard.get("readiness"),
                     "latest_validation": latest_validation},
        "changed_since_last_run": changed,
        "next_action": next_action,
        "last_run": {"cycle": last.get("cycle_id"), "mode": last.get("mode"),
                     "status": ("degraded" if last.get("degraded") else last.get("status")),
                     "started_at": last.get("started_at"), "finished_at": last.get("finished_at"),
                     "host": last.get("host")} if last else None,
        "next_run": schedule.get("next_run"),
        "schedule": schedule,
        "interrupted_runs": [{"cycle": m.get("cycle_id"), "mode": m.get("mode"),
                              "started_at": m.get("started_at"), "host": m.get("host"),
                              "resume_count": m.get("resume_count", 0)} for m in running],
        "cycles": len(cycles),
        "last_cycle": last.get("cycle_id"),
        "last_status": ("degraded" if last.get("degraded") else last.get("status")) if last else None,
        "last_triggers": last.get("triggers", []) if last else [],
        "open_ledger_items": len(open_items),
    }


def dumps(data):
    return json.dumps(data, indent=2, sort_keys=True, default=str)


__all__ = ["make_living", "promote", "read_state", "write_state", "note_reopen", "status",
           "CATEGORIES"]
