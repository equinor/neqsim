"""The living report: one Markdown document rewritten after every change to a living task.

``continuous/LIVING_REPORT.md`` is rebuilt from the task's own files after each cycle, solve,
backtest, promotion and ledger decision, so it always shows the current position, the goal
progress, the KPI trends, the events, the open decisions and the baseline history. It is a
view, never a source: deleting it loses nothing, and rebuilding it gives the same content.

The formal Word/HTML report (``neqsim report``) can be regenerated as well, controlled by the
plan's ``report.formal``: ``never`` (default), ``on_promote`` or ``every_cycle``. A plan may
declare ``report.prepare: step2_analysis/40_write_results.py`` (optionally ``:function``); it
runs first and turns the latest complete cycle into ``results.json`` and figures, so
``neqsim task-report <task> --formal`` is a one-command, on-request report of the current state.
"""

import csv
import logging
import os
import subprocess
import sys
from datetime import datetime, timezone
from types import SimpleNamespace

from . import figures as figure_kit
from . import user_input
from .ledger import Ledger
from .plan import continuous_dir, load_baseline, load_goal, load_plan, read_json, replace_file

logger = logging.getLogger(__name__)

REPORT_FILE = "LIVING_REPORT.md"
FIGURE_DIR = "report"
OPEN_STATUSES = ("proposed", "under_review", "accepted", "implemented")
MAX_EVENTS = 40


def _fmt(value):
    if value is None:
        return "-"
    if isinstance(value, bool):
        return str(value)
    if isinstance(value, float):
        return "{:.4g}".format(value)
    return str(value)


def _constraint_text(constraint):
    if not isinstance(constraint, dict):
        return str(constraint)
    limit = constraint.get("limit")
    return "{}: {} {} {}{}".format(
        constraint.get("name") or constraint.get("kpi"), constraint.get("kpi"), constraint.get("op", "<="),
        "NOT SET" if limit is None else _fmt(limit),
        " (hard)" if constraint.get("hard", True) else "")


def _map_driver_migration_timeline(items):
    """Return fail-closed operator rows for guarded map-to-driver receipts in the ledger."""
    from .production import _map_driver_migration_evidence_reasons

    rows = []
    withheld = 0
    ordered = sorted(items.items(), key=lambda pair: (
        str(pair[1].get("cycle") or ""), str(pair[0])))
    for item_id, item in ordered:
        receipt = item.get("map_driver_migration_evidence")
        if receipt is None:
            continue
        reasons = _map_driver_migration_evidence_reasons(
            SimpleNamespace(cycle_id=item.get("cycle")), item, required=True)
        if reasons:
            withheld += 1
            continue
        map_boundary = receipt["map_limited_slice"][
            "operating_region_boundary_refinements"][0]["infeasible_boundary_constraint"]
        evidence_state = "current"
        if item.get("evidence_current") is False:
            evidence_state = "stale since {}".format(
                item.get("evidence_invalidated_cycle") or "unknown cycle")
        if item.get("status") == "superseded":
            evidence_state = "superseded; " + evidence_state
        receipt_link = item_id
        if item.get("revalidation_of"):
            receipt_link += " (revalidates {})".format(item["revalidation_of"])
        rows.append((
            item.get("cycle"), receipt_link,
            receipt["configured_map_constraint_name"],
            "{} {} — {}; {}".format(
                _fmt(receipt["configured_map_limit"]),
                map_boundary.get("physical_unit") or "-",
                receipt["configured_map_limit_role"],
                receipt["configured_map_limit_provenance"]),
            receipt["driver_constraint_name"],
            "{} kW".format(_fmt(receipt["driver_limit_kw"])),
            "{} → {} {}".format(
                _fmt(receipt["sampled_axis_values"][0]),
                _fmt(receipt["sampled_axis_values"][1]), receipt["action_unit"]),
            evidence_state,
        ))
    return rows, withheld


STATE_TEXT = {
    "goal_met": "The goal is reached and the result is validated.",
    "converged": "Further solving gives only marginal gains, so the solver has stopped; the result is as good as it gets with the current levers.",
    "infeasible": "The goal cannot be reached within the stated constraints.",
    "blocked": "The next useful step needs a person (a decision, data or a confirmation).",
    "budget_exhausted": "The solver used up its work budget before the goal was reached.",
}

TRIGGER_TEXT = {
    "drift": "{} has moved away from its normal level (statistical drift detector)",
    "kpi_step": "{} moved more than the allowed step compared with the baseline",
    "criterion": "{} crossed a limit set in the plan",
    "constraint_breach": "constraint {} is violated",
    "constraint_margin": "constraint {} is close to its limit",
    "new_evidence": "input files changed since the last cycle",
    "brief_changed": "the task brief changed",
    "neqsim_changed": "the NeqSim version changed",
    "user_input": "a comment or restriction from a user ({}) took effect",
    "user_input_expired": "a comment or restriction from a user ({}) expired",
    "reopen": "the study should be reopened ({})",
    "calibration": "parameter tracking: {}",
}

GLOSSARY = [
    ("KPI", "A number the task tracks every cycle (a measured or calculated value such as an efficiency or a pressure)."),
    ("Baseline", "The reviewed, accepted reference values. Everything is compared against it."),
    ("Cycle", "One automatic check: read new data, recompute the KPIs, compare with the baseline, raise triggers."),
    ("Trigger", "A reason the cycle raised a flag, e.g. a KPI drifted or an input file changed. A trigger is a question, not a verdict."),
    ("Drift", "A KPI has shifted away from its normal level by more than normal noise would explain."),
    ("Solve round", "One attempt by the solver to improve the objective; several rounds are run until the goal is met or gains become marginal."),
    ("Ledger", "The list of proposed improvements and decisions, each with a status and an owner."),
    ("Backtest", "A replay over past data with known events, used to check that the monitoring would have caught them and how many false alarms it gives."),
]


def _trigger_text(trigger):
    """Explain a trigger name such as ``drift:efficiency`` in plain language."""
    kind, _, detail = str(trigger).partition(":")
    if kind.startswith("outcome_"):
        return "decision outcome {} for {}".format(kind[len("outcome_"):], detail or "-")
    if kind in TRIGGER_TEXT:
        return TRIGGER_TEXT[kind].format(detail) if "{}" in TRIGGER_TEXT[kind] else TRIGGER_TEXT[kind]
    return str(trigger)


def _kpi_meaning(plan, name):
    """Return the plan's description and unit for a KPI, if it declared them."""
    info = (plan.get("kpis") or {}).get(name) or {}
    text = info.get("describe") or info.get("description") or ""
    if info.get("unit"):
        text = "{} [{}]".format(text, info["unit"]) if text else "[{}]".format(info["unit"])
    return text or "-"


def _reading(value, ref, objective, name):
    """Say in words how the latest value compares with the baseline."""
    if not isinstance(ref, (int, float)):
        return "no baseline value"
    delta = value - ref
    if abs(delta) <= 1e-12 * max(1.0, abs(ref)):
        return "same as baseline"
    percent = " ({:+.1f}%)".format(100.0 * delta / abs(ref)) if ref else ""
    word = "above" if delta > 0 else "below"
    if objective.get("metric") == name:
        better = (delta > 0) == (objective.get("direction", "maximize") == "maximize")
        return "{} baseline{}, {}".format(word, percent, "better" if better else "worse")
    return "{} baseline{}".format(word, percent)


def _actions(goal, state, history, meta, runs, last, evidence_impact, final_report):
    actions = []
    if not goal.get("confirmed_by"):
        actions.append("Confirm the goal: set `confirmed_by` in `continuous/goal.yaml`.")
    if not runs:
        actions.append("Backtest the monitoring before relying on it (`neqsim task-backtest`).")
    if state.get("state") in ("goal_met", "converged") and history and meta.get("source_cycle") != history[-1].get("cycle"):
        actions.append("Review and promote the solved cycle: `neqsim task-promote <task> {} --reviewer NAME`."
                       .format(history[-1].get("cycle")))
    if meta.get("source_cycle") and not evidence_impact.get("changes"):
        if not final_report.get("available") or final_report.get("stale"):
            actions.append("Generate the reviewed standalone delivery: "
                           "`neqsim task-report <task> --final --reviewer NAME`.")
    if last and last.get("triggers"):
        actions.append("Triage the triggers of cycle {} (`digest.md`, `triggers.json`).".format(last["cycle_id"]))
    return actions


def _table(headers, rows):
    lines = ["| " + " | ".join(headers) + " |", "|" + "|".join("---" for _ in headers) + "|"]
    for row in rows:
        lines.append("| " + " | ".join(_fmt(c).replace("|", "/") for c in row) + " |")
    return lines


def _cycles(cont):
    folder = os.path.join(cont, "cycles")
    if not os.path.isdir(folder):
        return []
    out = []
    for name in sorted(os.listdir(folder)):
        meta = read_json(os.path.join(folder, name, "cycle.json"))
        if meta:
            meta["_kpis"] = read_json(os.path.join(folder, name, "kpis.json"), {}) or {}
            out.append(meta)
    return out


def _history(cont):
    path = os.path.join(cont, "kpi_history.csv")
    series = {}
    if os.path.exists(path):
        with open(path, newline="", encoding="utf-8") as f:
            for row in csv.DictReader(f):
                try:
                    series.setdefault(row["kpi"], []).append((row["time"], float(row["value"])))
                except (KeyError, ValueError):
                    continue
    return series


def _image(made, name, alt, note=""):
    """Markdown for one figure written by ``figures.build_all``, or nothing when it had no data."""
    if name not in made:
        return []
    return ["![{}]({})".format(alt, FIGURE_DIR + "/" + name), "", "*{}*".format(note or made[name]), ""]


def _baselines(cont, current):
    rows = []
    history = os.path.join(cont, "baseline", "history")
    if os.path.isdir(history):
        for name in sorted(os.listdir(history)):
            meta = read_json(os.path.join(history, name, "baseline.json"), {}) or {}
            rows.append(meta)
    if current:
        rows.append(dict(current, _current=True))
    return rows


def _backtests(cont):
    folder = os.path.join(cont, "backtests")
    out = []
    if os.path.isdir(folder):
        for name in sorted(os.listdir(folder)):
            report = read_json(os.path.join(folder, name, "backtest_report.json"))
            if report:
                out.append(report)
    return out


def build(task_dir):
    """Return the living report as Markdown text (and draw the trend figure)."""
    task_dir = os.path.abspath(str(task_dir))
    cont = continuous_dir(task_dir)
    goal, baseline = load_goal(task_dir) or {}, load_baseline(task_dir)
    from .evidence import analyze
    evidence_impact = analyze(task_dir, load_plan(task_dir))
    from .final_report import status as final_report_status
    final_report = final_report_status(task_dir)
    state = read_json(os.path.join(cont, "state.json"), {}) or {}
    meta, base_kpis = baseline.get("meta", {}), baseline.get("kpis", {})
    cycles = _cycles(cont)
    live = [c for c in cycles if c.get("status") == "complete"]
    last = live[-1] if live else None
    objective = goal.get("objective", {})
    now = datetime.now(timezone.utc).replace(microsecond=0).isoformat()

    out = ["# Living report: {}".format(os.path.basename(task_dir)), "",
           "Rebuilt {} from the task folder after the latest change. It is regenerated after "
           "every cycle, solve, backtest, promotion and ledger decision; do not edit it.".format(now), ""]

    plan = load_plan(task_dir)
    runs = _backtests(cont)
    history = state.get("history") or []
    series = _history(cont)
    metric = objective.get("metric")
    out += ["## In short", ""]
    if metric:
        target = objective.get("target")
        out += ["- **What is being tried:** {} `{}`{}. {}".format(
            objective.get("direction", "maximize"), metric,
            " to reach {}".format(_fmt(target)) if target is not None else "",
            ("It means: " + _kpi_meaning(plan, metric) + ". ") if _kpi_meaning(plan, metric) != "-" else "") +
            ("Constraints that must hold: " + "; ".join(_constraint_text(c) for c in goal.get("constraints", [])) + "."
             if goal.get("constraints") else "No constraints are set.")]
        best = history[-1] if history else None
        latest = series.get(metric, [None])[-1]
        first_kpis = (figure_kit.collect(task_dir)["baselines"] or [{"kpis": {}}])[0]["kpis"]
        start = first_kpis.get(metric)
        if best and best.get("value") is not None:
            out += ["- **Result so far:** the solver's latest value is {}{}; the first baseline was {} and the "
                    "current baseline is {}.".format(
                        _fmt(best.get("value")), " (validated)" if best.get("validated") else " (not yet validated)",
                        _fmt(start), _fmt(base_kpis.get(metric)))]
        elif latest:
            out += ["- **Result so far:** the latest monitored value is {} ({}).".format(
                _fmt(latest[1]), _reading(latest[1], base_kpis.get(metric), objective, metric))]
    else:
        out += ["- **What is being tried:** no objective is set yet (edit `continuous/goal.yaml`); the task only monitors its KPIs."]
    out += ["- **Status:** {}".format(STATE_TEXT.get(state.get("state"),
            "Routine monitoring; no stop condition has been reached." if not state.get("state") or state.get("state") == "monitoring"
            else "State `{}`.".format(state.get("state"))))]
    if last and last.get("triggers"):
        out += ["- **Latest cycle raised {} trigger(s):**".format(len(last["triggers"]))]
        out += ["  - " + _trigger_text(t) for t in last["triggers"]]
    elif last:
        out += ["- **Latest cycle:** no triggers, everything is within the expected range."]
    actions = _actions(goal, state, history, meta, runs, last, evidence_impact, final_report)
    out += ["", "### What needs your attention", ""]
    out += ["- " + a for a in actions] or ["- Nothing; the task is in routine monitoring."]
    out += ["", "### How to read this report", ""]
    out += _table(["Term", "Meaning"], GLOSSARY)
    out += [""]
    made = dict(figure_kit.build_all(task_dir, os.path.join(cont, FIGURE_DIR)))
    if "workflow_of_the_living_task.png" in made:
        out += ["## How the task works", ""]
        out += _image(made, "workflow_of_the_living_task.png", "Workflow of the living task",
                      "Each cycle reads new data, computes the KPIs and compares them with the reviewed baseline. "
                      "Triggers start solve rounds; a reviewer promotes a good result to the new baseline, "
                      "and the final report is issued from it.")
    if any(n in made for n in ("objective_progress_by_solve_round.png", "change_since_first_baseline.png",
                               "baseline_progression.png")):
        out += ["## How the task has improved", ""]
        out += _image(made, "objective_progress_by_solve_round.png", "Objective by solve round",
                      "Objective value after each solve round (filled marker = validated), against the first "
                      "baseline (dashed) and the target (dotted).")
        out += _image(made, "change_since_first_baseline.png", "Change since first baseline",
                      "Relative change of every KPI since the first baseline. The objective is green when it "
                      "moved in the wanted direction and red when it did not.")
        out += _image(made, "baseline_progression.png", "Baseline progression",
                      "The objective at each reviewed baseline; each step was accepted by a named reviewer.")
    out += ["## Where the task stands", "", "*The current state of the task in one table.*", ""]
    out += _table(["Item", "Value"], [
        ("State / phase", "{} / {}".format(state.get("state"), state.get("phase"))),
        ("Stop reason", state.get("reason", "-")),
        ("Baseline", "{} (promoted by {} on {})".format(meta.get("id"), meta.get("promoted_by", "-"),
                                                       str(meta.get("promoted_at", meta.get("created", "-")))[:19])),
        ("Goal", "{} {} to {} (confirmed by {})".format(
            objective.get("direction", "maximize"), objective["metric"], _fmt(objective.get("target")),
            goal.get("confirmed_by") or "NOT CONFIRMED") if objective.get("metric")
         else "not set (edit continuous/goal.yaml)"),
        ("Constraints", "; ".join(_constraint_text(c) for c in goal.get("constraints", [])) or "-"),
        ("Cycles run", "{} ({} degraded)".format(len(cycles), sum(1 for c in cycles if c.get("degraded")))),
        ("Last cycle", "{} at {} ({})".format(last["cycle_id"], last.get("now", "")[:19], last.get("mode"))
         if last else "none yet"),
    ])
    if state.get("phase") == "reopen_requested":
        out += ["", "**Reopen requested** by cycle {}: {}. Run `neqsim task-solve` to re-enter the "
                "solve loop.".format(state.get("reopen_cycle"), ", ".join(state.get("reopen_reasons", [])))]

    out += ["", "## Report views", ""]
    out += _table(["View", "Purpose", "Current artifact"], [
        ("Status", "Five-second operational position and next action", "neqsim task-status <task>"),
        ("Living Report", "Regenerated history, changes and decisions", "continuous/LIVING_REPORT.md"),
        ("Current-best Task Solver report", "Latest validated engineering result",
         "step3_report/ (neqsim report <task>)"),
        ("Final Report", "Reviewed standalone delivery without transient history",
         "{}{}".format(final_report.get("revision") or "not generated",
                       " (stale)" if final_report.get("stale") else "")),
    ])

    out += ["", "## Inputs and how they changed", "",
            "*A live task keeps reading its inputs: data from databases, historians or live feeds, evidence files, "
            "the brief and the NeqSim version. This shows what was read and what changed, so a result can be "
            "traced to the inputs it was computed from.*", ""]
    sources, input_changes = figure_kit.input_summary(figure_kit.collect(task_dir))
    if sources:
        out += _table(["Source", "Adapter", "Cycles read", "Rows received", "Problem cycles", "Last status",
                       "Newest data"], [(s["source"], s["adapter"], s["cycles"], s["rows"], s["problems"],
                                         s["last_status"], s["newest_data"] or "-") for s in sources])
        out += [""]
    else:
        out += ["No data source is declared in the plan; the task works on the files in its folder.", ""]
    out += _image(made, "how_the_inputs_changed.png", "Inputs over time")
    if input_changes:
        out += _table(["Cycle", "Time", "Input", "Change", "Kind"],
                      [(c["cycle"], c["time"], c["what"], c["change"], c["kind"]) for c in input_changes[-MAX_EVENTS:]])
        out += [""]
    out += ["", "## Evidence changes and impact", ""]
    evidence_view = evidence_impact if evidence_impact.get("changes") else ((last or {}).get("evidence") or {})
    changes = evidence_view.get("changes") or []
    if changes:
        out += _table(["Path", "Change", "Kind", "Impact rule"], [
            (row.get("path"), row.get("change"), row.get("kind"),
             ", ".join(row.get("impact_rules") or []) or "unmapped") for row in changes
        ])
        out += ["", "Affected stages: {}. Invalidated KPIs: {}. Affected conclusions: {}.".format(
            ", ".join(evidence_view.get("affected_stages") or []) or "none",
            ", ".join(evidence_view.get("affected_kpis") or []) or "none",
            ", ".join(evidence_view.get("affected_conclusions") or []) or "none")]
        provenance = evidence_view.get("provenance") or []
        if provenance:
            out += ["", "### Conclusion provenance", ""]
            out += _table(["Impact rule", "Conclusions", "Evidence version"], [
                (link.get("rule"), ", ".join(link.get("conclusions") or []),
                 "; ".join("{} @ {}".format(item.get("path"),
                                            str(item.get("sha256") or "")[:12])
                           for item in link.get("evidence") or []))
                for link in provenance
            ])
        if evidence_impact.get("changes"):
            out += ["", "Pending evidence changes have not yet been accepted. Run " +
                    "`neqsim task-update <task>`; an unmapped path causes a conservative full cycle."]
    else:
        out += ["No evidence change is pending. File-level hashes in "
                "`continuous/evidence/inventory.json` are the accepted provenance baseline."]

    series = _history(cont)
    if series:
        out += ["", "## Latest values against the baseline", "",
                "*Each KPI's most recent value next to the accepted baseline. \"Reading\" says in words whether it is "
                "above or below the baseline; only the objective is judged better or worse.*", ""]
        rows = []
        for name in sorted(series):
            points = series[name]
            value, ref = points[-1][1], base_kpis.get(name)
            delta = value - ref if isinstance(ref, (int, float)) else None
            rows.append((name, _kpi_meaning(plan, name), value, points[-1][0][:19],
                         points[-2][1] if len(points) > 1 else None, ref, delta, _reading(value, ref, objective, name)))
        out += _table(["KPI", "Meaning", "Latest", "At", "Previous", "Baseline", "Latest - baseline", "Reading"], rows)
    unmeasured = sorted(k for k in base_kpis if k not in series)
    if unmeasured:
        out += ["", "{} baseline value(s) from the study are not yet measured by any cycle: {}.".format(
            len(unmeasured), ", ".join(unmeasured))]
    monitor = [c for c in live if c.get("mode") == "monitor"]
    if monitor:
        out += ["", "Last monitor cycle {}: sources ".format(monitor[-1]["cycle_id"]) +
                (", ".join("{} {}".format(k, v) for k, v in sorted(monitor[-1].get("sources", {}).items()))
                 or "none") + "; triggers " + (", ".join(monitor[-1].get("triggers", [])) or "none") + "."]

    history = state.get("history") or []
    if history:
        out += ["", "## Goal progress (solve rounds)", "",
                "*Each row is one solver attempt to improve the objective. \"Validated\" means the result passed the "
                "validation checks; \"Action\" is what was changed in that round.*", ""]
        out += _table(["Round", "Cycle", "Value", "Validated", "Confidence", "Action"],
                      [(h.get("round"), h.get("cycle"), h.get("value"), h.get("validated"),
                        h.get("confidence"), h.get("action") or "-") for h in history])
        details = state.get("details", {})
        out += ["", "Stop state **{}**: {}.{}".format(state.get("state"), state.get("reason", ""),
                                                     " Reachable bound {}.".format(_fmt(details["upper_bound"]))
                                                     if "upper_bound" in details else "")]

    if series:
        out += ["", "## KPI trends", "",
                "*How each KPI developed cycle by cycle (dashed line: baseline). A steady line near the baseline "
                "means nothing has changed.*", ""]
        out += _image(made, "kpi_trends_with_triggers.png", "KPI per cycle, with the baseline dashed",
                      "Dotted red lines mark cycles that raised a trigger.")
        out += _table(["KPI", "Cycles", "First", "Min", "Max", "Last"],
                      [(k, len(v), v[0][1], min(x for _, x in v), max(x for _, x in v), v[-1][1])
                       for k, v in sorted(series.items())])

    events = [(c.get("now", "")[:19], c["cycle_id"], ", ".join(c.get("triggers", [])),
               "; ".join(_trigger_text(t) for t in c.get("triggers", [])))
              for c in cycles if c.get("triggers")]
    out += ["", "## Events", "", "*Cycles that raised a flag, newest first. A trigger asks for a look; it is not a verdict.*", ""]
    if events:
        out += _image(made, "monitoring_events_timeline.png", "Triggers over time")
        out += _table(["Time", "Cycle", "Triggers", "What it means"], list(reversed(events))[:MAX_EVENTS])
        if len(events) > MAX_EVENTS:
            out += ["", "{} older events are in the cycle folders.".format(len(events) - MAX_EVENTS)]
    else:
        out += ["No trigger has fired."]

    notes = [e for e in user_input.load(task_dir) if e.get("status", "active") == "active"]
    if notes:
        out += ["", "## Comments and restrictions in force", ""]
        out += ["- " + user_input.describe(e) + (" (expires {})".format(e["expires"]) if e.get("expires") else "")
                for e in notes]
        out += ["", "Add or resolve with `neqsim task-note <task> ...`; they apply from the next cycle."]

    items = Ledger(os.path.join(cont, "ledger", "events.jsonl")).current()
    out += ["", "## Improvement ledger", "",
            "*Proposed improvements and the decisions taken on them. \"Proposed\" items are waiting for a person.*", ""]
    if items:
        out += _image(made, "decision_ledger_status.png", "Ledger status")
        rows = []
        for key, item in sorted(items.items()):
            decided = [h for h in item.get("history", []) if h.get("event") == "status_changed"]
            value = item.get("value")
            evidence = "-"
            if item.get("evidence_current") is True:
                evidence = "current"
            elif item.get("evidence_current") is False:
                evidence = "stale since {}".format(item.get("evidence_invalidated_cycle") or "unknown cycle")
            rows.append((key, item.get("status"), item.get("category"), item.get("title"),
                         value.get("value") if isinstance(value, dict) else value,
                         evidence,
                         "{} {}".format(decided[-1].get("by"), str(decided[-1].get("at", ""))[:10])
                         if decided else "-"))
        out += _table(["Id", "Status", "Category", "Title", "Latest value", "Evidence",
                       "Last decision"], rows)
        waiting = [k for k, v in items.items() if v.get("status") == "proposed"]
        if waiting:
            out += ["", "Waiting for a decision: " + ", ".join(sorted(waiting)) +
                    " (`neqsim task-ledger <task> set ID STATUS --by NAME`)."]
    else:
        out += ["The ledger is empty."]

    migration_rows, withheld_migrations = _map_driver_migration_timeline(items)
    if migration_rows or withheld_migrations:
        out += ["", "## Map-to-driver bottleneck timeline", "",
                "*Cycle-bound advisory receipts showing when the rejecting common-speed boundary moved from a "
                "configured casing-map operating restriction to total driver power. Historical rows remain visible "
                "but are labelled stale or superseded; they are not current operating instructions.*", ""]
        if migration_rows:
            out += _table([
                "Cycle", "Ledger receipt", "Configured map restriction", "Map limit / basis",
                "Driver constraint", "Driver limit", "Common-speed endpoints", "Evidence state",
            ], migration_rows)
        if withheld_migrations:
            out += ["", "{} malformed or cycle-mismatched migration receipt(s) were withheld; no map-to-driver "
                    "claim was rendered from them.".format(withheld_migrations)]

    out += ["", "## Baseline history", "",
            "*Every reviewed baseline the task has had. A new baseline is only created by a named reviewer.*", ""]
    out += _table(["Baseline", "Promoted by", "When", "Source cycle", "Note"],
                  [("{}{}".format(b.get("id"), " (current)" if b.get("_current") else ""),
                    b.get("promoted_by", "-"), str(b.get("promoted_at", b.get("created", "-")))[:19],
                    b.get("source_cycle", "-"), b.get("note", "") or "-")
                   for b in _baselines(cont, meta)])

    if runs:
        out += ["", "## Backtests", "",
                "*Replays over past data with known events. \"Detected\" counts known events the monitoring caught, "
                "\"Delays\" is how many days after onset each was caught, and \"False alarms / month\" is how often it "
                "fired without a real event. Few false alarms and short delays mean the monitoring can be trusted.*", ""]
        out += _table(["Run", "Period", "Cycles", "Detected", "Delays (days)", "False alarms / month",
                       "Unscored triggers", "Reproducibility"],
                      [(r.get("run"), "{} to {}".format(str(r.get("start"))[:10], str(r.get("end"))[:10]),
                        r.get("cycles"), "{} of {}".format(r.get("detected"), len(r.get("expected", []))),
                        ", ".join("{} {}".format(e["trigger"].split(":")[-1], _fmt(e.get("delay_days")))
                                  for e in r.get("expected", [])) or "-",
                        r.get("false_alarms_per_month"), len(r.get("other_triggers", [])),
                        r.get("reproducibility")) for r in runs])
        if any(not r.get("expected") for r in runs):
            out += ["", "A run without `backtest.expected` events cannot score detection or false "
                    "alarms; its triggers are counted as unscored (`other_triggers` in the run's "
                    "backtest_report.json)."]

    return "\n".join(out) + "\n"


def update(task_dir, event="cycle"):
    """Rewrite the living report; never raises, so reporting cannot break a cycle."""
    try:
        task_dir = os.path.abspath(str(task_dir))
        cont = continuous_dir(task_dir)
        if not os.path.isdir(cont):
            return None
        path = os.path.join(cont, REPORT_FILE)
        temporary = path + ".tmp"
        with open(temporary, "w", encoding="utf-8") as f:
            f.write(build(task_dir))
        replace_file(temporary, path)
        formal = str((load_plan(task_dir).get("report") or {}).get("formal", "never"))
        if formal == "every_cycle" or (formal == "on_promote" and event == "promote"):
            regenerate_formal(task_dir)
        return path
    except Exception as error:  # noqa: BLE001 - the report is a view; the cycle result stands
        logger.warning("living report not updated: %s", error)
        return None


def prepare_results(task_dir):
    """Run the plan's ``report.prepare`` script (task-relative ``file[:function]``) to refresh results.json.

    The script turns the latest complete cycle into ``results.json`` and figures; the formal report is
    then built from it. Returns ``None`` when no script is declared, else a dict with ``ok`` and a message.
    """
    spec = (load_plan(task_dir).get("report") or {}).get("prepare")
    if not spec:
        return None
    path, _, func = str(spec).partition(":")
    full = path if os.path.isabs(path) else os.path.join(str(task_dir), path)
    if not os.path.exists(full):
        return {"ok": False, "message": "report.prepare script not found: {}".format(full)}
    if func:
        import importlib.util
        try:
            module_spec = importlib.util.spec_from_file_location("neqsim_task_report_prepare", full)
            module = importlib.util.module_from_spec(module_spec)
            module_spec.loader.exec_module(module)
            getattr(module, func)(str(task_dir))
            return {"ok": True, "message": "{}:{} ran".format(path, func)}
        except Exception as error:  # noqa: BLE001 - reporting must not raise into a cycle
            return {"ok": False, "message": "{}: {}".format(type(error).__name__, error)}
    try:
        code = subprocess.call([sys.executable, full], cwd=str(task_dir))
    except OSError as error:
        return {"ok": False, "message": str(error)}
    return {"ok": code == 0, "message": "{} exit {}".format(path, code)}


def regenerate_formal(task_dir):
    """Refresh results.json through ``report.prepare`` (if declared), then run ``neqsim report``; never raises."""
    prepared = prepare_results(task_dir)
    if prepared is not None and not prepared["ok"]:
        logger.warning("report.prepare failed, report built from the existing results.json: %s", prepared["message"])
    cli = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "neqsim_cli.py")
    try:
        return subprocess.call([sys.executable, cli, "report", str(task_dir)])
    except OSError as error:
        logger.warning("formal report not regenerated: %s", error)
        return None
