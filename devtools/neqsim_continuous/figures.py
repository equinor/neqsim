"""Figures and diagrams that show how a living task has improved.

Both the living report and the final report use these builders, so a reader sees the same
picture in both: how the task works (workflow diagram), how the objective improved round by
round, how every KPI changed since the first baseline, how the KPIs behaved cycle by cycle
with the triggers marked, when and why the monitoring raised flags, and how the proposed
improvements were decided.

Every builder reads only the task folder and returns ``None`` when it has nothing to show
(or matplotlib is missing), so a young task simply gets fewer figures. Each figure is a
``(filename, caption)`` pair in ``CATALOG``; ``build_all`` writes the available ones.
"""

import csv
import logging
import os
from datetime import datetime

from .ledger import Ledger
from .plan import continuous_dir, load_baseline, load_goal, load_plan, read_json

logger = logging.getLogger(__name__)

GOOD, BAD, NEUTRAL, GREY = "#2e7d32", "#c62828", "#1565c0", "#9e9e9e"
CATALOG = (
    ("workflow_of_the_living_task.png", "How the living task works and where it stands now"),
    ("how_the_inputs_changed.png", "Data read each cycle and the input changes detected over time"),
    ("objective_progress_by_solve_round.png",
     "Objective after each solve round, compared with the baseline and the target"),
    ("baseline_progression.png", "Objective at each reviewed baseline"),
    ("change_since_first_baseline.png", "Change of every KPI since the first baseline"),
    ("kpi_trends_with_triggers.png", "KPI per cycle against the baseline; dotted lines mark cycles that raised a trigger"),
    ("monitoring_events_timeline.png", "When and why the monitoring raised flags"),
    ("decision_ledger_status.png", "Status of the proposed improvements"),
)


def _pyplot():
    try:
        import matplotlib
        matplotlib.use("Agg")
        import matplotlib.pyplot as plt
        return plt
    except ImportError:
        return None


def _save(fig, plt, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    fig.savefig(path, dpi=130, bbox_inches="tight")
    plt.close(fig)
    return path


def _time(text):
    try:
        return datetime.fromisoformat(str(text).replace("Z", "+00:00")).replace(tzinfo=None)
    except ValueError:
        return None


def collect(task_dir):
    """Read everything the figures need from the task folder."""
    task_dir = os.path.abspath(str(task_dir))
    cont = continuous_dir(task_dir)
    baseline = load_baseline(task_dir)
    series = {}
    path = os.path.join(cont, "kpi_history.csv")
    if os.path.exists(path):
        with open(path, newline="", encoding="utf-8") as handle:
            for row in csv.DictReader(handle):
                try:
                    series.setdefault(row["kpi"], []).append((row["time"], float(row["value"])))
                except (KeyError, ValueError):
                    continue
    cycles, folder = [], os.path.join(cont, "cycles")
    if os.path.isdir(folder):
        for name in sorted(os.listdir(folder)):
            meta = read_json(os.path.join(folder, name, "cycle.json"))
            if meta:
                meta["_refresh"] = read_json(os.path.join(folder, name, "refresh_report.json"), {}) or {}
                cycles.append(meta)
    baselines, history = [], os.path.join(cont, "baseline", "history")
    if os.path.isdir(history):
        for name in sorted(os.listdir(history)):
            meta = read_json(os.path.join(history, name, "baseline.json"), {}) or {}
            kpis = read_json(os.path.join(history, name, "kpis.json"), {}) or {}
            if meta:
                baselines.append({"id": meta.get("id", name), "at": meta.get("promoted_at"), "kpis": kpis})
    if baseline["meta"]:
        baselines.append({"id": baseline["meta"].get("id"), "at": baseline["meta"].get("promoted_at"),
                          "kpis": baseline["kpis"] or {}, "current": True})
    state = read_json(os.path.join(cont, "state.json"), {}) or {}
    goal = load_goal(task_dir) or {}
    return {"task_dir": task_dir, "cont": cont, "plan": load_plan(task_dir), "goal": goal,
            "objective": goal.get("objective") or {}, "baseline": baseline, "baselines": baselines,
            "series": series, "cycles": cycles, "history": state.get("history") or [], "state": state,
            "ledger": Ledger(os.path.join(cont, "ledger", "events.jsonl")).current()}


def _better(objective, delta):
    return (delta > 0) == (objective.get("direction", "maximize") == "maximize")


def input_summary(data):
    """Summarise every input the task reads: its sources and the changes seen over time.

    Returns ``(sources, changes)``. ``sources`` has one row per declared data source
    (database, historian, files, live feed) with the number of cycles that read it, the rows
    received, how often it failed or went stale and the newest data time. ``changes`` lists
    each input change a cycle detected: evidence files, the brief and the NeqSim version.
    """
    declared = data["plan"].get("sources") or {}
    sources = {}
    for cycle in data["cycles"]:
        for name, report in (cycle.get("_refresh") or {}).items():
            row = sources.setdefault(name, {
                "source": name, "adapter": (declared.get(name) or {}).get("adapter") or report.get("adapter") or "-",
                "cycles": 0, "rows": 0, "problems": 0, "last_status": "-", "newest_data": None, "gaps": 0})
            row["cycles"] += 1
            row["rows"] += int(report.get("new_rows") or 0)
            row["problems"] += 0 if report.get("status") == "ok" else 1
            row["last_status"] = report.get("status", "-")
            row["gaps"] += len(report.get("gaps") or [])
            if report.get("watermark"):
                row["newest_data"] = max(str(report["watermark"]), row["newest_data"] or "")
    changes = []
    for cycle in data["cycles"]:
        when = str(cycle.get("now", ""))[:19]
        for item in (cycle.get("evidence") or {}).get("changes") or []:
            changes.append({"cycle": cycle.get("cycle_id"), "time": when, "what": item.get("path"),
                            "change": item.get("change"), "kind": item.get("kind") or "file"})
        for trigger in cycle.get("triggers", []):
            if trigger in ("brief_changed", "neqsim_changed"):
                changes.append({"cycle": cycle.get("cycle_id"), "time": when,
                                "what": "task brief" if trigger == "brief_changed" else "NeqSim version",
                                "change": "changed", "kind": trigger.split("_")[0]})
    return sorted(sources.values(), key=lambda r: r["source"]), changes


def inputs_over_time(data, path):
    """Rows read per cycle for each source, and when evidence, brief or version changes arrived."""
    plt = _pyplot()
    sources, changes = input_summary(data)
    if plt is None or not (sources or changes):
        return None
    fig, (top, bottom) = plt.subplots(2, 1, figsize=(9, 4.6), sharex=True, gridspec_kw={"height_ratios": [2, 1]})
    index = {c.get("cycle_id"): i + 1 for i, c in enumerate(data["cycles"])}
    for row in sources:
        points = [(index[c.get("cycle_id")], (c.get("_refresh") or {}).get(row["source"]))
                  for c in data["cycles"] if row["source"] in (c.get("_refresh") or {})]
        values = [int(r.get("new_rows") or 0) for _, r in points]
        top.plot([i for i, _ in points], values, lw=1.0, marker="o", ms=4, label=row["source"])
        bad = [(i, int(r.get("new_rows") or 0)) for i, r in points if r.get("status") != "ok"]
        if bad:
            top.scatter([b[0] for b in bad], [b[1] for b in bad], marker="x", s=60, color=BAD, zorder=3)
    top.set_ylabel("new rows per cycle", fontsize=8)
    top.set_title("Data read each cycle (red x: source failed, stale or incomplete)", fontsize=9)
    if sources:
        top.legend(fontsize=7, loc="best")
    top.grid(True, alpha=0.3)
    kinds = sorted({c["kind"] for c in changes}) or ["no change"]
    for change in changes:
        if change["cycle"] in index:
            bottom.scatter([index[change["cycle"]]], [kinds.index(change["kind"])], marker="|", s=140, color=NEUTRAL)
    bottom.set_yticks(range(len(kinds)))
    bottom.set_yticklabels(kinds, fontsize=7)
    bottom.set_ylim(-0.6, len(kinds) - 0.4)
    bottom.set_xlim(0.5, len(data["cycles"]) + 0.5)
    bottom.xaxis.get_major_locator().set_params(integer=True)
    bottom.set_xlabel("cycle number")
    bottom.set_title("Input changes detected (evidence files by kind, brief, NeqSim version)", fontsize=9)
    bottom.grid(True, alpha=0.3)
    fig.tight_layout()
    return _save(fig, plt, path)


def workflow_diagram(data, path):
    """Boxes-and-arrows view of the task's loop with the real counts and the current position."""
    plt = _pyplot()
    if plt is None or not data["cycles"]:
        return None
    from .final_report import status as final_status
    triggers = sum(len(c.get("triggers", [])) for c in data["cycles"])
    meta = data["baseline"]["meta"]
    final = final_status(data["task_dir"])
    if data.get("final_revision"):
        final = {"revision": data["final_revision"] + " (this report)", "available": True}
    steps = [
        ("1. Data and model", "{} source(s)\nread each cycle".format(len(data["plan"].get("sources") or {})), True),
        ("2. Cycle", "{} run:\nKPIs vs baseline".format(len(data["cycles"])), True),
        ("3. Triggers", "{} raised".format(triggers), triggers > 0),
        ("4. Solve rounds", "{} attempted".format(len(data["history"])), bool(data["history"])),
        ("5. Review and\npromote", "baseline\n{}".format(str(meta.get("id") or "none").split("@")[0]),
         bool(meta.get("source_cycle"))),
        ("6. Final report", str(final.get("revision") or "not issued").replace(" (", "\n("), bool(final.get("available"))),
    ]
    done = [i for i, s in enumerate(steps) if s[2]]
    current = (max(done) + 1) if done and max(done) + 1 < len(steps) else None
    fig, ax = plt.subplots(figsize=(11, 3.0))
    ax.set_xlim(0, len(steps) * 1.6)
    ax.set_ylim(0, 2.2)
    ax.axis("off")
    for i, (title, sub, ok) in enumerate(steps):
        x = i * 1.6 + 0.05
        colour = "#c8e6c9" if ok else ("#ffe0b2" if i == current else "#eeeeee")
        ax.add_patch(plt.Rectangle((x, 0.55), 1.3, 1.2, facecolor=colour, edgecolor="#546e7a", lw=1.2))
        ax.text(x + 0.65, 1.5, title, ha="center", va="center", fontsize=8.5, fontweight="bold")
        ax.text(x + 0.65, 1.0, sub, ha="center", va="center", fontsize=7.5)
        if i < len(steps) - 1:
            ax.annotate("", xy=(x + 1.6, 1.15), xytext=(x + 1.3, 1.15),
                        arrowprops=dict(arrowstyle="->", color="#546e7a"))
    ax.annotate("", xy=(0.7, 0.5), xytext=(len(steps) * 1.6 - 1.0, 0.5),
                arrowprops=dict(arrowstyle="->", color="#90a4ae", ls="--"))
    ax.text(len(steps) * 0.8, 0.25, "the reviewed baseline becomes the reference for the next cycles",
            ha="center", fontsize=7.5, style="italic", color="#546e7a")
    ax.text(0, 2.0, "green = done, orange = next step, grey = not reached yet", fontsize=7.5, color="#546e7a")
    return _save(fig, plt, path)


def objective_progress(data, path):
    """The objective after each solve round, against the baseline and the target."""
    plt = _pyplot()
    objective = data["objective"]
    rows = [(h.get("round"), h.get("value"), bool(h.get("validated"))) for h in data["history"]
            if isinstance(h.get("value"), (int, float))]
    if plt is None or not objective.get("metric") or len(rows) < 1:
        return None
    first = data["baselines"][0]["kpis"].get(objective["metric"]) if data["baselines"] else None
    fig, ax = plt.subplots(figsize=(8, 3.6))
    xs = [r[0] for r in rows]
    ax.plot(xs, [r[1] for r in rows], color=NEUTRAL, lw=1.4, zorder=2)
    for x, value, validated in rows:
        ax.scatter([x], [value], s=46, zorder=3, facecolor=GOOD if validated else "white", edgecolor=GOOD
                   if validated else GREY, lw=1.5)
    if isinstance(first, (int, float)):
        ax.axhline(first, color=GREY, ls="--", lw=1.0, label="first baseline ({})".format(_short(first)))
    if isinstance(objective.get("target"), (int, float)):
        ax.axhline(objective["target"], color=BAD, ls=":", lw=1.2, label="target ({})".format(_short(objective["target"])))
    best = rows[-1][1]
    if isinstance(first, (int, float)) and first:
        gain = 100.0 * (best - first) / abs(first)
        ax.annotate("{:+.1f}% vs first baseline".format(gain), (xs[-1], best), textcoords="offset points",
                    xytext=(-6, 9), ha="right", fontsize=8, color=GOOD if _better(objective, best - first) else BAD)
    ax.set_xlabel("solve round")
    ax.set_ylabel(objective["metric"])
    ax.set_xticks(xs)
    ax.grid(True, alpha=0.3)
    ax.set_title("{} the objective: filled marker = validated".format(objective.get("direction", "maximize").capitalize()),
                 fontsize=9)
    ax.legend(fontsize=7, loc="best")
    return _save(fig, plt, path)


def baseline_progression(data, path):
    """The objective at each reviewed baseline, oldest first."""
    plt = _pyplot()
    metric = data["objective"].get("metric")
    points = [(b["id"], b["kpis"].get(metric)) for b in data["baselines"]
              if isinstance(b["kpis"].get(metric), (int, float))]
    if plt is None or not metric or len(points) < 2:
        return None
    fig, ax = plt.subplots(figsize=(8, 3.2))
    names = [str(p[0]).replace("B-", "") for p in points]
    values = [p[1] for p in points]
    ax.bar(range(len(values)), values, color=[NEUTRAL] * (len(values) - 1) + [GOOD])
    for i, value in enumerate(values):
        ax.text(i, value, _short(value), ha="center", va="bottom", fontsize=7)
    ax.set_xticks(range(len(values)))
    ax.set_xticklabels(names, rotation=30, ha="right", fontsize=7)
    ax.set_ylabel(metric)
    ax.set_title("Reviewed baselines, oldest to newest (green = current)", fontsize=9)
    ax.grid(True, axis="y", alpha=0.3)
    return _save(fig, plt, path)


def change_since_first_baseline(data, path):
    """Relative change of each KPI between the first and the current reviewed baseline."""
    plt = _pyplot()
    if plt is None or len(data["baselines"]) < 2:
        return None
    start = data["baselines"][0]["kpis"]
    latest = data["baselines"][-1]["kpis"]
    rows = []
    for name in sorted(start):
        a, b = start.get(name), latest.get(name)
        if isinstance(a, (int, float)) and isinstance(b, (int, float)):
            # A baseline of zero has no relative change; show it as a full-scale step.
            change = 100.0 * (b - a) / abs(a) if a else (0.0 if b == a else (100.0 if b > 0 else -100.0))
            rows.append((name, change, "{} to {}".format(_short(a), _short(b))))
    if not rows or all(abs(r[1]) < 1e-9 for r in rows):
        return None
    rows.sort(key=lambda r: abs(r[1]))
    objective = data["objective"]
    colours = [(GOOD if _better(objective, r[1]) else BAD) if r[0] == objective.get("metric") else NEUTRAL
               for r in rows]
    fig, ax = plt.subplots(figsize=(8, max(2.2, 0.38 * len(rows) + 1.0)))
    ax.barh([r[0] for r in rows], [r[1] for r in rows], color=colours)
    for position, row in enumerate(rows):
        ax.annotate(row[2], (row[1], position), textcoords="offset points", fontsize=7, va="center",
                    xytext=(4 if row[1] >= 0 else -4, 0), ha="left" if row[1] >= 0 else "right")
    ax.margins(x=0.25)
    ax.axvline(0, color="black", lw=0.8)
    ax.set_xlabel("change since the first baseline (%)")
    ax.set_title("Objective in green/red (better/worse), other KPIs in blue", fontsize=9)
    ax.grid(True, axis="x", alpha=0.3)
    ax.tick_params(axis="y", labelsize=7)
    return _save(fig, plt, path)


def kpi_trends(data, path):
    """Each KPI per cycle with the baseline dashed and triggering cycles marked."""
    plt = _pyplot()
    series = data["series"]
    if plt is None or not series:
        return None
    base = data["baseline"]["kpis"] or {}
    marks = [_time(c.get("now")) for c in data["cycles"] if c.get("triggers")]
    names = sorted(series)
    fig, axes = plt.subplots(len(names), 1, figsize=(9, 2.3 * len(names)), squeeze=False)
    for ax, name in zip(axes[:, 0], names):
        times = [_time(t) for t, _ in series[name]]
        ax.plot(times, [v for _, v in series[name]], lw=1.0, marker="." if len(times) < 40 else None)
        if isinstance(base.get(name), (int, float)):
            ax.axhline(base[name], color=GREY, ls="--", lw=0.8, label="baseline")
            ax.legend(fontsize=7, loc="best")
        for mark in marks:
            if mark:
                ax.axvline(mark, color=BAD, ls=":", lw=0.6, alpha=0.6)
        ax.set_ylabel(name, fontsize=8)
        ax.grid(True, alpha=0.3)
    axes[0, 0].set_title("KPI per cycle (dotted red: a trigger was raised)")
    fig.autofmt_xdate()
    fig.tight_layout()
    return _save(fig, plt, path)


def events_timeline(data, path):
    """One row per trigger family, one mark per cycle that raised it."""
    plt = _pyplot()
    points = []
    for cycle in data["cycles"]:
        when = _time(cycle.get("now"))
        for trigger in cycle.get("triggers", []):
            if when:
                points.append((when, trigger))
    if plt is None or not points:
        return None
    labels = sorted({t for _, t in points})
    fig, ax = plt.subplots(figsize=(9, max(2.2, 0.35 * len(labels) + 1.0)))
    ax.scatter([p[0] for p in points], [labels.index(p[1]) for p in points], marker="|", s=140, color=BAD)
    ax.set_yticks(range(len(labels)))
    ax.set_yticklabels(labels, fontsize=7)
    ax.set_title("Triggers over time (one mark per cycle that raised it)", fontsize=9)
    ax.grid(True, alpha=0.3)
    fig.autofmt_xdate()
    return _save(fig, plt, path)


def decision_status(data, path):
    """How many improvement proposals sit in each status."""
    plt = _pyplot()
    items = data["ledger"]
    if plt is None or not items:
        return None
    order = ["proposed", "under_review", "accepted", "implemented", "rejected", "deferred", "superseded"]
    counts = {}
    for item in items.values():
        counts[item.get("status")] = counts.get(item.get("status"), 0) + 1
    labels = [s for s in order if s in counts] + [s for s in counts if s not in order]
    fig, ax = plt.subplots(figsize=(7, 2.6))
    ax.bar(labels, [counts[s] for s in labels], color=[GOOD if s in ("accepted", "implemented") else NEUTRAL
                                                       for s in labels])
    ax.set_ylabel("proposals")
    ax.set_title("Improvement ledger (proposed = waiting for a person)", fontsize=9)
    ax.grid(True, axis="y", alpha=0.3)
    return _save(fig, plt, path)


def _short(value):
    return "{:.4g}".format(value)


BUILDERS = {
    "workflow_of_the_living_task.png": workflow_diagram,
    "how_the_inputs_changed.png": inputs_over_time,
    "objective_progress_by_solve_round.png": objective_progress,
    "baseline_progression.png": baseline_progression,
    "change_since_first_baseline.png": change_since_first_baseline,
    "kpi_trends_with_triggers.png": kpi_trends,
    "monitoring_events_timeline.png": events_timeline,
    "decision_ledger_status.png": decision_status,
}


def build_all(task_dir, out_dir, final_revision=None, prefix=""):
    """Write every figure that has data into ``out_dir``; return ``[(filename, caption)]`` for those written.

    ``prefix`` is put in front of each filename (the final report uses ``living_`` so these
    derived figures are recognisable); ``final_revision`` marks the final report in the diagram.
    """
    data = collect(task_dir)
    data["final_revision"] = final_revision
    written = []
    for filename, caption in CATALOG:
        target = os.path.join(out_dir, prefix + filename)
        try:
            made = BUILDERS[filename](data, target)
        except Exception as error:  # noqa: BLE001 - one broken picture must not block the report
            logger.warning("figure %s not drawn: %s", filename, error)
            made = None
        if made:
            written.append((prefix + filename, caption))
        elif os.path.exists(target):
            os.remove(target)
    return written
