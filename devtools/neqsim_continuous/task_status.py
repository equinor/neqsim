"""Compact, durable status view for a continuous engineering task."""

import os

from .ledger import Ledger
from .plan import continuous_dir, load_baseline, load_goal, read_json
from .state import STATE_SCHEMA_VERSION, read_state


def build(task_dir):
    """Return the five-second status view from persisted task-folder evidence."""
    from .cycle import find_incomplete_cycle, list_cycles, load_cycle

    task_dir = os.path.abspath(str(task_dir))
    cycles = list_cycles(task_dir)
    last = load_cycle(task_dir, cycles[-1]) if cycles else {}
    items = Ledger(os.path.join(continuous_dir(task_dir), "ledger", "events.jsonl")).current()
    open_items = [k for k, v in items.items()
                  if v.get("status") in ("proposed", "under_review", "accepted", "implemented")]
    rejected = [{"id": key, "title": value.get("title")}
                for key, value in sorted(items.items()) if value.get("status") == "rejected"]

    state = read_state(task_dir)
    goal = load_goal(task_dir) or {}
    objective = goal.get("objective") or {}
    baseline = load_baseline(task_dir)
    baseline_meta, baseline_kpis = baseline.get("meta", {}), baseline.get("kpis", {})
    history = list(state.get("history") or [])
    valid = [entry for entry in history
             if entry.get("validated") and isinstance(entry.get("value"), (int, float))]
    best = None
    if valid:
        reverse = objective.get("direction", "maximize") != "minimize"
        winner = sorted(valid, key=lambda entry: float(entry["value"]), reverse=reverse)[0]
        best = {"value": winner.get("value"), "cycle": winner.get("cycle"),
                "confidence": winner.get("confidence")}

    blockers = []
    for value in (state.get("details", {}).get("blockers"),
                  history[-1].get("blockers") if history else None):
        for blocker in value or []:
            if blocker not in blockers:
                blockers.append(blocker)

    diff = read_json(os.path.join(continuous_dir(task_dir), "cycles",
                                  last.get("cycle_id", ""), "diff_vs_baseline.json"), {}) or {}
    changed_kpis = [{"kpi": name, "delta_vs_previous": row.get("delta_vs_previous")}
                    for name, row in sorted(diff.items())
                    if row.get("delta_vs_previous") not in (None, 0, 0.0)]
    incomplete = find_incomplete_cycle(task_dir)
    session = state.get("solve_session") or {}

    if incomplete:
        next_action = "resume incomplete {} cycle {}".format(
            incomplete.get("mode", "monitor"), incomplete.get("cycle_id"))
    elif state.get("phase") == "solving":
        next_action = "resume solve session {}".format(session.get("id") or "(unidentified)")
    elif state.get("phase") == "reopen_requested":
        next_action = "resume solving after {}".format(
            ", ".join(state.get("reopen_reasons") or ["reopen request"]))
    elif not goal.get("confirmed_by") and objective.get("metric"):
        next_action = "confirm goal in continuous/goal.yaml"
    elif state.get("details", {}).get("next_action"):
        next_action = state["details"]["next_action"]
    elif state.get("state") == "blocked" and blockers:
        next_action = "resolve blocker: {}".format(blockers[0])
    elif any(items[key].get("status") == "proposed" for key in open_items):
        key = next(key for key in open_items if items[key].get("status") == "proposed")
        next_action = "review ledger item {}".format(key)
    elif last and last.get("triggers"):
        next_action = "triage triggers from {}".format(last.get("cycle_id"))
    else:
        next_action = "routine monitoring"

    schedule_meta = read_json(os.path.join(continuous_dir(task_dir), "schedule.json"), {}) or {}
    if incomplete:
        next_run = {"kind": "resume", "cycle": incomplete.get("cycle_id"), "at": None}
    elif state.get("phase") == "solving":
        next_run = {"kind": "resume", "session": session.get("id"), "at": None}
    elif schedule_meta:
        next_run = schedule_meta
    else:
        next_run = {"kind": "external_schedule", "at": None,
                    "message": "next scheduled time is not recorded in the task folder"}

    latest_valid = valid[-1] if valid else {}
    return {
        "task": os.path.basename(task_dir),
        "state": state.get("state"),
        "phase": state.get("phase"),
        "baseline": baseline_meta.get("id"),
        "cycles": len(cycles),
        "last_cycle": last.get("cycle_id"),
        "last_status": ("degraded" if last.get("degraded") else last.get("status"))
        if last else None,
        "last_triggers": last.get("triggers", []) if last else [],
        "open_ledger_items": len(open_items),
        "state_schema": state.get("schema_version", STATE_SCHEMA_VERSION),
        "state_revision": state.get("state_revision"),
        "conclusion": {"reason": state.get("reason"), "details": state.get("details", {})},
        "goal": {"metric": objective.get("metric"), "direction": objective.get("direction"),
                 "target": objective.get("target"), "confirmed_by": goal.get("confirmed_by")},
        "baseline_result": {"metric": objective.get("metric"),
                            "value": baseline_kpis.get(objective.get("metric"))},
        "best_validated": best,
        "attempts": len(history),
        "rejected_hypotheses": rejected,
        "blockers": blockers,
        "evidence": {"last_cycle_degraded": bool(last.get("degraded")) if last else None,
                     "sources": last.get("sources", {}) if last else {},
                     "objective_validated": latest_valid.get("validated"),
                     "objective_confidence": latest_valid.get("confidence"),
                     "standard_first": (last.get("standard_first") or {}).get("readiness")
                     if last else None,
                     "baseline_references_sha256": baseline_meta.get("references_sha256")},
        "what_changed": {"triggers": last.get("triggers", []) if last else [],
                         "kpi_deltas": changed_kpis,
                         "recovery": last.get("recovery") if last else None},
        "next_action": next_action,
        "last_run": {"cycle": last.get("cycle_id"), "mode": last.get("mode"),
                     "at": last.get("now"), "finished_at": last.get("finished_at"),
                     "status": last.get("status"), "degraded": last.get("degraded")}
        if last else None,
        "next_run": next_run,
        "resume": {"available": bool(incomplete or state.get("phase") in
                                     ("solving", "reopen_requested")),
                   "incomplete_cycle": incomplete.get("cycle_id") if incomplete else None,
                   "solve_session": session.get("id"),
                   "last_writer": state.get("last_writer")},
    }


__all__ = ["build"]
