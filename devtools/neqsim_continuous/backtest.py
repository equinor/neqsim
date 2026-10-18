"""Backtest a living task on archived data with a simulated clock.

Cycles run day by day (or at ``step_hours``) into ``continuous/backtests/<run>/`` with
their own watermarks, drift state and ledger, so the live task is untouched. The report
compares triggers with the expected events declared in ``cycle_plan.yaml``
(``backtest.expected``) and gives detection delay, missed events, false alarms and,
optionally, reproducibility.
"""

import itertools
import os
from datetime import timedelta

from .cycle import _utc, run_cycle
from .drift import DriftMonitor
from .plan import continuous_dir, load_plan, read_json, write_json
from .watermarks import parse_time

TUNE_GRID = {"lambda": [0.1, 0.2, 0.3], "L": [2.5, 3.0, 3.5, 4.0], "h": [4.0, 6.0, 8.0],
             "confirm": [1, 2, 3], "recover": [2, 6], "min_sigma_scale": [0.5, 1.0, 2.0, 4.0]}


def _timeline(run_dir):
    cycles_dir = os.path.join(run_dir, "cycles")
    events = []
    for name in sorted(os.listdir(cycles_dir)) if os.path.isdir(cycles_dir) else []:
        manifest = read_json(os.path.join(cycles_dir, name, "cycle.json"), {})
        for trigger in manifest.get("triggers", []):
            events.append({"time": manifest["now"], "cycle": name, "trigger": trigger})
    return events


def _kpi_series(run_dir):
    cycles_dir = os.path.join(run_dir, "cycles")
    series = []
    for name in sorted(os.listdir(cycles_dir)) if os.path.isdir(cycles_dir) else []:
        manifest = read_json(os.path.join(cycles_dir, name, "cycle.json"), {})
        kpis = read_json(os.path.join(cycles_dir, name, "kpis.json"), {}) or {}
        if manifest.get("now"):
            series.append((manifest["now"], name, kpis))
    return series


def _drift_signals(plan):
    config = plan.get("drift", {}) or {}
    signals = config.get("signals", {}) or {}
    if isinstance(signals, list):
        signals = {(s.get("name") or s.get("kpi")) if isinstance(s, dict) else s:
                   ({k: v for k, v in s.items() if k != "name"} if isinstance(s, dict) else {"kpi": s})
                   for s in signals}
    settings = dict(config.get("settings") or {})
    overrides = dict(settings.get("signals") or {})
    for name, signal in signals.items():
        extra = {k: v for k, v in (signal or {}).items() if k != "kpi"}
        if extra:
            overrides[name] = dict(overrides.get(name, {}), **extra)
    settings["signals"] = overrides
    return signals, settings


def replay_drift(series, signals, settings):
    """Re-run the drift monitor over recorded KPI series; return ``drift:<name>`` events."""
    monitor = DriftMonitor(settings)
    events = []
    for now, cycle, kpis in series:
        for name, signal in signals.items():
            value = kpis.get((signal or {}).get("kpi", name))
            if isinstance(value, (int, float)):
                if monitor.update(name, value)["new_alarm"]:
                    events.append({"time": now, "cycle": cycle, "trigger": "drift:" + name})
    return events


def _candidate_settings(base, combo):
    settings = {k: v for k, v in base.items() if k != "signals"}
    settings.update({k: v for k, v in combo.items() if k != "min_sigma_scale"})
    scale = float(combo.get("min_sigma_scale", 1.0))
    settings["signals"] = {name: dict(over, min_sigma=float(over.get("min_sigma", 0.0)) * scale)
                           for name, over in (base.get("signals") or {}).items()}
    return settings


def tune_drift(task_dir, run_name="backtest", grid=None, false_alarm_weight=0.5, delay_weight=0.25,
               top=10, holdout_from=None):
    """Choose drift settings from a finished backtest by replaying its KPI series.

    Drift alarms are a pure function of the recorded KPI values, so every candidate in the
    grid is scored against ``backtest.expected`` without rerunning NeqSim. The objective is
    detections minus weighted false alarms and normalised delay. With ``holdout_from`` the
    grid is ranked on events before that date and every candidate is also scored on the
    events from that date on, so the recommendation is validated out of sample; without a
    holdout the result is in-sample and ``validated`` is false. The plan is not edited; the
    recommended ``drift.settings`` block is written to ``drift_tuning.json`` for review.
    """
    task_dir = os.path.abspath(str(task_dir))
    plan = load_plan(task_dir)
    expected = [e for e in plan.get("backtest", {}).get("expected", [])
                if str(e.get("trigger", "")).startswith("drift:")]
    run_dir = os.path.join(continuous_dir(task_dir), "backtests", run_name)
    report = read_json(os.path.join(run_dir, "backtest_report.json"), {}) or {}
    if not report:
        raise ValueError("no backtest report in {}; run task-backtest first".format(run_dir))
    if not expected:
        raise ValueError("backtest.expected has no drift:* events; nothing to tune against")
    start, end = _utc(parse_time(report["start"])), _utc(parse_time(report["end"]))
    split = _utc(parse_time(holdout_from)) if holdout_from else None
    train = [e for e in expected if split is None or _utc(parse_time(e["onset"])) < split]
    hold = [e for e in expected if split is not None and _utc(parse_time(e["onset"])) >= split]
    if split is not None and (not train or not hold):
        raise ValueError("holdout_from must leave expected events on both sides of the split")
    series = _kpi_series(run_dir)
    signals, base = _drift_signals(plan)
    grid = dict(TUNE_GRID, **(grid or {}))
    if not any(float((o or {}).get("min_sigma", 0.0)) > 0 for o in (base.get("signals") or {}).values()):
        grid["min_sigma_scale"] = [1.0]   # no floors to scale: the axis would only duplicate candidates
    if len(expected) < 2:
        grid["recover"] = [grid["recover"][0]] if isinstance(grid.get("recover"), list) else [2]
        # recovery only matters when a signal must alarm twice
    keys = list(grid)

    def _score(settings, events_expected, window_start, window_end):
        scored = score([e for e in replay_drift(series, signals, settings)
                        if window_start <= _utc(parse_time(e["time"])) < window_end],
                       events_expected, window_start, window_end)
        delay = sum(float(r.get("delay_days", 0.0)) / float(r.get("max_delay_days", 30) or 30)
                    for r in scored["expected"] if r["detected"])
        detected = scored["detected"]
        return {"objective": round(detected - false_alarm_weight * len(scored["false_alarms"])
                                   - delay_weight * delay, 4),
                "detected": detected, "missed": scored["missed"], "false_alarms": len(scored["false_alarms"]),
                "false_alarms_per_month": scored["false_alarms_per_month"],
                "mean_delay_days": round(sum(float(r.get("delay_days", 0.0)) for r in scored["expected"]
                                             if r["detected"]) / max(detected, 1), 2)}

    far_future = end + timedelta(days=1)
    train_end = split if split is not None else far_future
    results = []
    for values in itertools.product(*[grid[k] for k in keys]):
        combo = dict(zip(keys, values))
        settings = _candidate_settings(base, combo)
        entry = {"settings": combo}
        entry.update(_score(settings, train, start, train_end))
        if split is not None:
            entry["holdout"] = _score(settings, hold, split, far_future)
        results.append(entry)
    results.sort(key=lambda r: (-r["objective"], r["false_alarms"], r["mean_delay_days"]))
    current = {"settings": base}
    current.update(_score(base, train, start, train_end))
    if split is not None:
        current["holdout"] = _score(base, hold, split, far_future)
    best = results[0]
    recommended = _candidate_settings(base, best["settings"])
    validated = bool(split is not None and best["holdout"]["missed"] == 0
                     and best["holdout"]["false_alarms"] <= current["holdout"]["false_alarms"])
    out = {"schema_version": "1.1", "run": run_name, "expected_events": len(expected),
           "train_events": len(train), "holdout_events": len(hold),
           "holdout_from": split.isoformat() if split is not None else None,
           "candidates_evaluated": len(results), "weights": {"false_alarm": false_alarm_weight,
                                                             "delay": delay_weight},
           "current": current, "best": best, "recommended_drift_settings": recommended,
           "validated": validated,
           "note": ("recommendation validated on held-out events" if validated else
                    "in-sample only: validate on a later period or rerun with holdout_from"
                    if split is None else "held-out score did not confirm the recommendation; keep current settings"),
           "ranking": results[:top]}
    write_json(os.path.join(run_dir, "drift_tuning.json"), out)
    return out


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
    from .living_report import update
    update(task_dir, event="backtest")
    return report
