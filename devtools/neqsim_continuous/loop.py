"""Run monitor cycles in one long-lived process at a fixed cadence (``neqsim task-loop``).

The Task Scheduler route (``task-schedule``) starts a fresh interpreter per cycle, which is
right for daily work but wrong for a ten-minute live loop: every cycle would pay the JVM and
model build again. ``run_loop`` keeps the process (and therefore any model cached on a stage
module) alive, runs ``run_cycle`` every ``every_minutes``, and stops after ``for_hours`` or
when ``continuous/STOP`` appears. Progress is appended to ``continuous/loop.log`` and the
current state is in ``continuous/loop_state.json``.

An optional hook (``module.py:function``, task-relative) is called before each cycle with
``(task_dir, index, now)``; it is the place for a plant emulator or a test harness that
injects evidence changes. The hook module is re-loaded every cycle, like the stage scripts,
so an edit during a long run takes effect at the next cycle. Hooks and stages are isolated:
an exception in either is logged and the loop continues with the next cycle.
"""

import importlib.util
import os
import time
import traceback
from datetime import datetime, timedelta, timezone

from .cycle import run_cycle
from .plan import continuous_dir, read_json, write_json


def _load_hook(task_dir, spec):
    if not spec:
        return None
    path, _, func = spec.partition(":")
    full = path if os.path.isabs(path) else os.path.join(task_dir, path)
    module_spec = importlib.util.spec_from_file_location("neqsim_task_loop_hook", full)
    module = importlib.util.module_from_spec(module_spec)
    module_spec.loader.exec_module(module)
    return getattr(module, func or "before_cycle")


def _log(cont, line):
    stamp = datetime.now(timezone.utc).replace(microsecond=0).isoformat()
    with open(os.path.join(cont, "loop.log"), "a", encoding="utf-8") as f:
        f.write("{} {}\n".format(stamp, line))


def run_loop(task_dir, every_minutes, for_hours, mode="monitor", no_agent=False, hook=None,
             max_cycles=None, sleep=time.sleep, clock=None):
    """Run cycles every ``every_minutes`` for ``for_hours``; return a summary dict."""
    task_dir = os.path.abspath(str(task_dir))
    cont = continuous_dir(task_dir)
    os.makedirs(cont, exist_ok=True)
    clock = clock or (lambda: datetime.now(timezone.utc).replace(microsecond=0))
    _load_hook(task_dir, hook)   # fail early on a bad hook path
    period = timedelta(minutes=float(every_minutes))
    start = clock()
    end = start + timedelta(hours=float(for_hours))
    stop_file = os.path.join(cont, "STOP")
    state = {"started": start.isoformat(), "ends": end.isoformat(), "every_minutes": float(every_minutes),
             "mode": mode, "hook": hook, "cycles": [], "stopped": None}
    write_json(os.path.join(cont, "loop_state.json"), state)
    _log(cont, "loop start every {} min until {} (mode {})".format(every_minutes, end.isoformat(), mode))
    index = 0
    while True:
        now = clock()
        if os.path.exists(stop_file):
            state["stopped"] = "STOP file"
            break
        if now >= end or (max_cycles is not None and index >= int(max_cycles)):
            state["stopped"] = "time budget" if now >= end else "max cycles"
            break
        due = start + index * period
        if now < due:
            sleep(min((due - now).total_seconds(), 60.0))
            continue
        entry = {"index": index, "scheduled": due.isoformat(), "started": now.isoformat()}
        t0 = time.time()
        try:
            before = _load_hook(task_dir, hook)
            if before is not None:
                before(task_dir, index, now)
            manifest = run_cycle(task_dir, mode=mode, no_agent=no_agent)
            entry.update(cycle_id=manifest.get("cycle_id"), status=manifest.get("status"),
                         degraded=manifest.get("degraded"), triggers=manifest.get("triggers", []))
        except Exception as exc:  # noqa: BLE001 - the loop must survive a bad cycle
            entry.update(status="error", error="{}: {}".format(type(exc).__name__, exc))
            _log(cont, "cycle {} error\n{}".format(index, traceback.format_exc()))
        entry["seconds"] = round(time.time() - t0, 1)
        state["cycles"].append(entry)
        write_json(os.path.join(cont, "loop_state.json"), state)
        _log(cont, "cycle {} {} {} in {} s triggers={}".format(index, entry.get("cycle_id"), entry.get("status"),
                                                               entry["seconds"], entry.get("triggers")))
        index += 1
        if entry["seconds"] > period.total_seconds():
            _log(cont, "cycle {} overran the period ({} s > {} s)".format(index - 1, entry["seconds"],
                                                                         period.total_seconds()))
    state["finished"] = clock().isoformat()
    write_json(os.path.join(cont, "loop_state.json"), state)
    _log(cont, "loop stop: {} after {} cycle(s)".format(state["stopped"], index))
    if os.path.exists(stop_file):
        os.remove(stop_file)
    summary = {k: state[k] for k in ("started", "finished", "stopped", "every_minutes", "mode")}
    summary["cycles"] = len(state["cycles"])
    summary["errors"] = sum(1 for c in state["cycles"] if c.get("status") == "error")
    summary["degraded"] = sum(1 for c in state["cycles"] if c.get("degraded"))
    summary["overruns"] = sum(1 for c in state["cycles"] if c["seconds"] > period.total_seconds())
    return summary


def loop_status(task_dir):
    return read_json(os.path.join(continuous_dir(task_dir), "loop_state.json"), {}) or {}
