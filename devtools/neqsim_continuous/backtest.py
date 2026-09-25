"""Backtest a living task on archived data with a simulated clock.

Cycles run day by day (or at ``step_hours``) into ``continuous/backtests/<run>/`` with
their own watermarks, drift state and ledger, so the live task is untouched. The report
compares triggers with the expected events declared in ``cycle_plan.yaml``
(``backtest.expected``) and gives detection delay, missed events, false alarms and,
optionally, reproducibility.
"""

import os
from datetime import timedelta

from .cycle import _utc, run_cycle
from .plan import continuous_dir, load_plan, read_json, write_json
from .watermarks import parse_time


def _timeline(run_dir):
    cycles_dir = os.path.join(run_dir, "cycles")
    events = []
    for name in sorted(os.listdir(cycles_dir)) if os.path.isdir(cycles_dir) else []:
        manifest = read_json(os.path.join(cycles_dir, name, "cycle.json"), {})
        for trigger in manifest.get("triggers", []):
            events.append({"time": manifest["now"], "cycle": name, "trigger": trigger})
    return events


def score(events, expected, start, end):
    """Match triggers to expected events; return per-event results and false alarms."""
    results, used = [], set()
    for item in expected:
        onset = _utc(parse_time(item["onset"]))
        limit = onset + timedelta(days=float(item.get("max_delay_days", 30)))
        hit = next((i for i, e in enumerate(events) if e["trigger"] == item["trigger"]
                    and onset <= _utc(parse_time(e["time"])) <= limit and i not in used), None)
        if hit is not None:
            used.add(hit)
            delay = (_utc(parse_time(events[hit]["time"])) - onset).total_seconds() / 86400.0
            results.append(dict(item, detected=True, detected_at=events[hit]["time"],
                                delay_days=round(delay, 2)))
        else:
            results.append(dict(item, detected=False))
    watched = {item["trigger"] for item in expected}
    false_alarms = [e for i, e in enumerate(events) if i not in used and e["trigger"] in watched]
    months = max((end - start).total_seconds() / (30.0 * 86400.0), 1e-9)
    return {"expected": results, "false_alarms": false_alarms,
            "false_alarms_per_month": round(len(false_alarms) / months, 3),
            "detected": sum(1 for r in results if r["detected"]), "missed": sum(
                1 for r in results if not r["detected"]),
            "other_triggers": [e for e in events if e["trigger"] not in watched]}


def run_backtest(task_dir, start, end, step_hours=24.0, run_name="backtest", check_reproducibility=False):
    """Run cycles from ``start`` to ``end`` on archived data and write a backtest report."""
    task_dir = os.path.abspath(str(task_dir))
    start, end = _utc(start), _utc(end)
    plan = load_plan(task_dir)
    expected = plan.get("backtest", {}).get("expected", [])

    def _run(name):
        run_dir = os.path.join(continuous_dir(task_dir), "backtests", name)
        if os.path.isdir(run_dir):
            import shutil
            shutil.rmtree(run_dir)
        os.makedirs(run_dir)
        now = start
        while now <= end:
            run_cycle(task_dir, mode="backtest", now=now, state_dir=run_dir, no_agent=True)
            now += timedelta(hours=step_hours)
        return run_dir

    run_dir = _run(run_name)
    events = _timeline(run_dir)
    report = {"schema_version": "1.0", "run": run_name, "start": start.isoformat(),
              "end": end.isoformat(), "step_hours": step_hours, "cycles": len(os.listdir(
                  os.path.join(run_dir, "cycles"))), "events": events}
    report.update(score(events, expected, start, end))
    if check_reproducibility:
        second = _run(run_name + "-repeat")
        names = sorted(os.listdir(os.path.join(run_dir, "cycles")))
        same = sum(1 for n in names if read_json(os.path.join(run_dir, "cycles", n, "kpis.json"))
                   == read_json(os.path.join(second, "cycles", n, "kpis.json")))
        report["reproducibility"] = round(same / float(len(names) or 1), 4)
    write_json(os.path.join(run_dir, "backtest_report.json"), report)
    lines = ["# Backtest {} ({} to {})".format(run_name, start.date(), end.date()), "",
             "Cycles: {}. Detected {} of {} expected events; false alarms {} ({} per month).".format(
                 report["cycles"], report["detected"], len(expected), len(report["false_alarms"]),
                 report["false_alarms_per_month"]), "",
             "| Expected trigger | Onset | Detected | Delay (days) |",
             "|------------------|-------|----------|--------------|"]
    for item in report["expected"]:
        lines.append("| {} | {} | {} | {} |".format(item["trigger"], item["onset"],
                                                   "yes" if item["detected"] else "no",
                                                   item.get("delay_days", "-")))
    if "reproducibility" in report:
        lines += ["", "Reproducibility (identical KPI files on rerun): {:.0%}".format(
            report["reproducibility"])]
    with open(os.path.join(run_dir, "backtest_report.md"), "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")
    return report
