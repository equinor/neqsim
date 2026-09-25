"""The living report: one Markdown document rewritten after every change to a living task.

``continuous/LIVING_REPORT.md`` is rebuilt from the task's own files after each cycle, solve,
backtest, promotion and ledger decision, so it always shows the current position, the goal
progress, the KPI trends, the events, the open decisions and the baseline history. It is a
view, never a source: deleting it loses nothing, and rebuilding it gives the same content.

The formal Word/HTML report (``neqsim report``) can be regenerated as well, controlled by the
plan's ``report.formal``: ``never`` (default), ``on_promote`` or ``every_cycle``.
"""

import csv
import logging
import os
import subprocess
import sys
from datetime import datetime, timezone

from .ledger import Ledger
from .plan import continuous_dir, load_baseline, load_goal, load_plan, read_json, replace_file

logger = logging.getLogger(__name__)

REPORT_FILE = "LIVING_REPORT.md"
FIGURE_FILE = os.path.join("report", "kpi_trends.png")
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


def _trend_figure(cont, series, baseline_kpis):
    """Draw the KPI trends; return the relative figure path, or None without matplotlib."""
    if not series:
        return None
    try:
        import matplotlib
        matplotlib.use("Agg")
        import matplotlib.pyplot as plt
    except ImportError:
        return None
    names = sorted(series)
    fig, axes = plt.subplots(len(names), 1, figsize=(9, 2.3 * len(names)), squeeze=False)
    for ax, name in zip(axes[:, 0], names):
        times = [datetime.fromisoformat(t) for t, _ in series[name]]
        ax.plot(times, [v for _, v in series[name]], lw=1.0, marker="." if len(times) < 40 else None)
        if isinstance(baseline_kpis.get(name), (int, float)):
            ax.axhline(baseline_kpis[name], color="grey", ls="--", lw=0.8, label="baseline")
            ax.legend(fontsize=7, loc="best")
        ax.set_ylabel(name, fontsize=8)
        ax.grid(True, alpha=0.3)
    axes[0, 0].set_title("KPI per cycle")
    fig.autofmt_xdate()
    fig.tight_layout()
    path = os.path.join(cont, FIGURE_FILE)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    fig.savefig(path, dpi=120, bbox_inches="tight")
    plt.close(fig)
    return FIGURE_FILE.replace(os.sep, "/")


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
    out += ["## Where the task stands", ""]
    out += _table(["Item", "Value"], [
        ("State / phase", "{} / {}".format(state.get("state"), state.get("phase"))),
        ("Stop reason", state.get("reason", "-")),
        ("Baseline", "{} (promoted by {} on {})".format(meta.get("id"), meta.get("promoted_by", "-"),
                                                       str(meta.get("promoted_at", meta.get("created", "-")))[:19])),
        ("Goal", "{} {} {} (confirmed by {})".format(objective.get("direction", "-"), objective.get("metric", "-"),
                                                   objective.get("target", "-"), goal.get("confirmed_by") or "NOT CONFIRMED")),
        ("Constraints", "; ".join(goal.get("constraints", [])) or "-"),
        ("Cycles run", "{} ({} degraded)".format(len(cycles), sum(1 for c in cycles if c.get("degraded")))),
        ("Last cycle", "{} at {} ({})".format(last["cycle_id"], last.get("now", "")[:19], last.get("mode"))
         if last else "none yet"),
    ])
    if state.get("phase") == "reopen_requested":
        out += ["", "**Reopen requested** by cycle {}: {}. Run `neqsim task-solve` to re-enter the "
                "solve loop.".format(state.get("reopen_cycle"), ", ".join(state.get("reopen_reasons", [])))]

    series = _history(cont)
    if series or base_kpis:
        out += ["", "## Latest values against the baseline", ""]
        rows = []
        for name in sorted(set(series) | set(base_kpis)):
            points = series.get(name, [])
            value = points[-1][1] if points else None
            ref = base_kpis.get(name)
            delta = value - ref if value is not None and isinstance(ref, (int, float)) else None
            rows.append((name, value, points[-1][0][:19] if points else None,
                         points[-2][1] if len(points) > 1 else None, ref, delta))
        out += _table(["KPI", "Latest", "At", "Previous", "Baseline", "Latest - baseline"], rows)
    monitor = [c for c in live if c.get("mode") == "monitor"]
    if monitor:
        out += ["", "Last monitor cycle {}: sources ".format(monitor[-1]["cycle_id"]) +
                (", ".join("{} {}".format(k, v) for k, v in sorted(monitor[-1].get("sources", {}).items()))
                 or "none") + "; triggers " + (", ".join(monitor[-1].get("triggers", [])) or "none") + "."]

    history = state.get("history") or []
    if history:
        out += ["", "## Goal progress (solve rounds)", ""]
        out += _table(["Round", "Cycle", "Value", "Validated", "Confidence", "Action"],
                      [(h.get("round"), h.get("cycle"), h.get("value"), h.get("validated"),
                        h.get("confidence"), h.get("action") or "-") for h in history])
        details = state.get("details", {})
        out += ["", "Stop state **{}**: {}.{}".format(state.get("state"), state.get("reason", ""),
                                                     " Reachable bound {}.".format(_fmt(details["upper_bound"]))
                                                     if "upper_bound" in details else "")]

    figure = _trend_figure(cont, series, base_kpis)
    if series:
        out += ["", "## KPI trends", ""]
        if figure:
            out += ["![KPI per cycle, with the baseline dashed]({})".format(figure), ""]
        out += _table(["KPI", "Cycles", "First", "Min", "Max", "Last"],
                      [(k, len(v), v[0][1], min(x for _, x in v), max(x for _, x in v), v[-1][1])
                       for k, v in sorted(series.items())])

    events = [(c.get("now", "")[:19], c["cycle_id"], ", ".join(c.get("triggers", [])))
              for c in cycles if c.get("triggers")]
    out += ["", "## Events", ""]
    if events:
        out += _table(["Time", "Cycle", "Triggers"], list(reversed(events))[:MAX_EVENTS])
        if len(events) > MAX_EVENTS:
            out += ["", "{} older events are in the cycle folders.".format(len(events) - MAX_EVENTS)]
    else:
        out += ["No trigger has fired."]

    items = Ledger(os.path.join(cont, "ledger", "events.jsonl")).current()
    out += ["", "## Improvement ledger", ""]
    if items:
        rows = []
        for key, item in sorted(items.items()):
            decided = [h for h in item.get("history", []) if h.get("event") == "status_changed"]
            value = item.get("value")
            rows.append((key, item.get("status"), item.get("category"), item.get("title"),
                         value.get("value") if isinstance(value, dict) else value,
                         "{} {}".format(decided[-1].get("by"), str(decided[-1].get("at", ""))[:10])
                         if decided else "-"))
        out += _table(["Id", "Status", "Category", "Title", "Latest value", "Last decision"], rows)
        waiting = [k for k, v in items.items() if v.get("status") == "proposed"]
        if waiting:
            out += ["", "Waiting for a decision: " + ", ".join(sorted(waiting)) +
                    " (`neqsim task-ledger <task> set ID STATUS --by NAME`)."]
    else:
        out += ["The ledger is empty."]

    out += ["", "## Baseline history", ""]
    out += _table(["Baseline", "Promoted by", "When", "Source cycle", "Note"],
                  [("{}{}".format(b.get("id"), " (current)" if b.get("_current") else ""),
                    b.get("promoted_by", "-"), str(b.get("promoted_at", b.get("created", "-")))[:19],
                    b.get("source_cycle", "-"), b.get("note", "") or "-")
                   for b in _baselines(cont, meta)])

    runs = _backtests(cont)
    if runs:
        out += ["", "## Backtests", ""]
        out += _table(["Run", "Period", "Cycles", "Detected", "Delays (days)", "False alarms / month",
                       "Reproducibility"],
                      [(r.get("run"), "{} to {}".format(str(r.get("start"))[:10], str(r.get("end"))[:10]),
                        r.get("cycles"), "{} of {}".format(r.get("detected"), len(r.get("expected", []))),
                        ", ".join("{} {}".format(e["trigger"].split(":")[-1], _fmt(e.get("delay_days")))
                                  for e in r.get("expected", [])) or "-",
                        r.get("false_alarms_per_month"), r.get("reproducibility")) for r in runs])

    out += ["", "## Next actions", ""]
    actions = []
    if not goal.get("confirmed_by"):
        actions.append("Confirm the goal: set `confirmed_by` in `continuous/goal.yaml`.")
    if not runs:
        actions.append("Backtest the monitoring before relying on it (`neqsim task-backtest`).")
    if state.get("state") in ("goal_met", "converged") and history and meta.get("source_cycle") != history[-1].get("cycle"):
        actions.append("Review and promote the solved cycle: `neqsim task-promote <task> {} --reviewer NAME`."
                       .format(history[-1].get("cycle")))
    if last and last.get("triggers"):
        actions.append("Triage the triggers of cycle {} (`digest.md`, `triggers.json`).".format(last["cycle_id"]))
    out += ["- " + a for a in actions] or ["- None; the task is in routine monitoring."]
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


def regenerate_formal(task_dir):
    """Run ``neqsim report`` for the task; return the exit code (never raises)."""
    cli = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "neqsim_cli.py")
    try:
        return subprocess.call([sys.executable, cli, "report", str(task_dir)])
    except OSError as error:
        logger.warning("formal report not regenerated: %s", error)
        return None
