"""Run one cycle of a living task.

A cycle runs the planned stages in order, writes an immutable folder
``continuous/cycles/<cycle_id>/`` and a ``cycle.json`` manifest, and never raises for a
stage failure: the failure is recorded, dependants are skipped and the cycle is marked
``degraded``. An interrupted cycle resumes at its first incomplete stage.
"""

import csv
import json
import os
import platform
import re
import sys
import time
from datetime import datetime, timedelta, timezone

from . import stages as _builtin_stages  # noqa: F401 - registers the built-in stages
from . import notify as _builtin_notifiers  # noqa: F401 - registers the built-in notifiers
from .contracts import StageResult, resolve
from .plan import continuous_dir, load_baseline, load_goal, load_plan, read_json, write_json

SCHEMA_VERSION = "1.0"
NEEDS = {"drift": ["kpis"], "diff": ["kpis"], "goal": ["kpis"], "notify": ["digest"]}
LOCK_HOURS = 6.0


def _utc(value=None):
    if value is None:
        return datetime.now(timezone.utc).replace(microsecond=0)
    if value.tzinfo is None:
        return value.replace(tzinfo=timezone.utc)
    return value.astimezone(timezone.utc)


def _host():
    return re.sub(r"[^A-Za-z0-9_-]", "", os.environ.get("NEQSIM_CONTINUOUS_HOST", "")
                  or platform.node().split(".")[0] or "host").lower() or "host"


class CycleContext(object):
    """Everything a stage may read or write during one cycle."""

    def __init__(self, task_dir, plan, goal, baseline, now, mode, cycle_id, cycle_dir,
                 state_dir, data_dir, dry_run=False, no_agent=False, next_action=None):
        self.task_dir = str(task_dir)
        self.plan, self.goal, self.baseline = plan, goal, baseline
        self.now, self.mode, self.cycle_id, self.cycle_dir = now, mode, cycle_id, cycle_dir
        self.state_dir, self.data_dir = state_dir, data_dir
        self.dry_run, self.no_agent, self.next_action = dry_run, no_agent, next_action
        self.new_rows, self.kpis, self.sources = {}, {}, {}
        self.triggers, self.proposals, self.new_proposals = [], [], []
        self.stage_results, self.versions, self.summary = {}, {}, {}
        self.solve, self.stop_state, self.digest_text = {}, None, ""
        self.notifications, self.agent_run, self.previous_kpis = [], {}, {}


class _Lock(object):
    def __init__(self, state_dir, now):
        self.path = os.path.join(state_dir, "LOCK")
        self.now = now

    def __enter__(self):
        existing = read_json(self.path)
        if existing and existing.get("expires", "") > _utc().isoformat():
            raise RuntimeError("Task is locked by {} (pid {}) until {}".format(
                existing.get("host"), existing.get("pid"), existing.get("expires")))
        write_json(self.path, {"host": _host(), "pid": os.getpid(),
                               "expires": (_utc() + timedelta(hours=LOCK_HOURS)).isoformat()})
        return self

    def __exit__(self, *args):
        if os.path.exists(self.path):
            os.remove(self.path)


def _stage_name(entry):
    return entry if isinstance(entry, str) else entry.get("name")


def _previous_kpis(cycles_dir, current):
    if not os.path.isdir(cycles_dir):
        return {}
    for name in sorted(os.listdir(cycles_dir), reverse=True):
        if name == current:
            continue
        meta = read_json(os.path.join(cycles_dir, name, "cycle.json"), {})
        if meta.get("status") == "complete":
            return read_json(os.path.join(cycles_dir, name, "kpis.json"), {})
    return {}


def _new_cycle_id(cycles_dir, now, mode):
    base = "bt-{:%Y%m%d}".format(now) if mode == "backtest" else "{:%Y-%m-%dT%H%MZ}@{}".format(now, _host())
    cycle_id, rerun = base, 2
    while os.path.exists(os.path.join(cycles_dir, cycle_id, "cycle.json")):
        cycle_id, rerun = "{}-r{}".format(base, rerun), rerun + 1
    return cycle_id


def _running_cycles(cycles_dir, mode=None):
    """Return persisted running-cycle manifests, newest first."""
    if not os.path.isdir(cycles_dir):
        return []
    running = []
    for name in os.listdir(cycles_dir):
        meta = read_json(os.path.join(cycles_dir, name, "cycle.json"), {}) or {}
        if meta.get("status") != "running":
            continue
        if mode and meta.get("mode") != mode:
            continue
        meta = dict(meta)
        meta.setdefault("cycle_id", name)
        running.append(meta)
    return sorted(running, key=lambda x: (x.get("started_at") or x.get("now") or "",
                                         x.get("cycle_id") or ""), reverse=True)


def _resumable_cycle(cycles_dir, mode):
    """Return the newest interrupted cycle that this runner can safely resume."""
    running = _running_cycles(cycles_dir, mode=mode)
    if not running:
        return None
    latest = running[0]
    schema = str(latest.get("schema_version") or "")
    if schema != SCHEMA_VERSION:
        raise RuntimeError(
            "Cannot resume cycle {} with schema {}; runner supports {}. "
            "Migrate the task state or start a new cycle explicitly.".format(
                latest.get("cycle_id"), schema or "<missing>", SCHEMA_VERSION))
    return latest


def _checkpoint(ctx, stage_name):
    """Persist the JSON-safe runner context after one completed stage."""
    return {
        "schema_version": SCHEMA_VERSION,
        "cycle_id": ctx.cycle_id,
        "stage": stage_name,
        "kpis": ctx.kpis,
        "sources": ctx.sources,
        "triggers": ctx.triggers,
        "proposals": ctx.proposals,
        "new_proposals": ctx.new_proposals,
        "versions": ctx.versions,
        "summary": ctx.summary,
        "solve": ctx.solve,
        "stop_state": ctx.stop_state,
        "digest_text": ctx.digest_text,
        "notifications": ctx.notifications,
        "agent_run": ctx.agent_run,
        "previous_kpis": ctx.previous_kpis,
    }


def _restore_checkpoint(ctx):
    """Restore persisted context from an interrupted cycle before continuing."""
    checkpoint = read_json(os.path.join(ctx.cycle_dir, "checkpoint.json"), {}) or {}
    if not checkpoint:
        return
    schema = str(checkpoint.get("schema_version") or "")
    if schema != SCHEMA_VERSION:
        raise RuntimeError(
            "Cannot resume checkpoint for cycle {} with schema {}; runner supports {}.".format(
                ctx.cycle_id, schema or "<missing>", SCHEMA_VERSION))
    for name in ("kpis", "sources", "versions", "summary", "solve", "previous_kpis"):
        value = checkpoint.get(name)
        if isinstance(value, dict):
            getattr(ctx, name).update(value)
    for name in ("triggers", "proposals", "new_proposals", "notifications"):
        value = checkpoint.get(name)
        if isinstance(value, list):
            setattr(ctx, name, list(value))
    if checkpoint.get("stop_state") is not None:
        ctx.stop_state = checkpoint.get("stop_state")
    if checkpoint.get("digest_text") is not None:
        ctx.digest_text = checkpoint.get("digest_text")
    if isinstance(checkpoint.get("agent_run"), dict):
        ctx.agent_run = dict(checkpoint.get("agent_run"))


def _append_kpi_history(state_dir, cycle_id, now, kpis):
    path = os.path.join(state_dir, "kpi_history.csv")
    new = not os.path.exists(path)
    with open(path, "a", encoding="utf-8", newline="") as f:
        writer = csv.writer(f)
        if new:
            writer.writerow(["cycle_id", "time", "kpi", "value"])
        for name, value in sorted(kpis.items()):
            writer.writerow([cycle_id, now.isoformat(), name, value])


def run_cycle(task_dir, mode="monitor", now=None, stages=None, dry_run=False, no_agent=False,
              state_dir=None, next_action=None, standard_first=False, resume=True):
    """Run one cycle and return its manifest (the content of ``cycle.json``)."""
    task_dir = os.path.abspath(str(task_dir))
    plan = load_plan(task_dir)
    now = _utc(now)
    state_dir = state_dir or continuous_dir(task_dir)
    data_root = plan.get("data_root")
    data_dir = data_root if data_root and mode != "backtest" else os.path.join(state_dir, "data")
    cycles_dir = os.path.join(state_dir, "cycles")
    resumable = None
    if resume and not dry_run and mode != "backtest":
        resumable = _resumable_cycle(cycles_dir, mode)
    cycle_id = resumable.get("cycle_id") if resumable else _new_cycle_id(cycles_dir, now, mode)
    cycle_dir = os.path.join(cycles_dir, cycle_id)
    os.makedirs(cycle_dir, exist_ok=True)
    previous = read_json(os.path.join(cycle_dir, "cycle.json"), {}) or {}
    done = {s["name"]: s for s in previous.get("stages", []) if s["status"] in ("ok", "warn")}

    standard_status = previous.get("standard_first") if previous else None
    ctx = CycleContext(task_dir, plan, load_goal(task_dir), load_baseline(task_dir), now, mode,
                       cycle_id, cycle_dir, state_dir, data_dir, dry_run, no_agent, next_action)
    ctx.previous_kpis = _previous_kpis(cycles_dir, cycle_id)
    if previous:
        _restore_checkpoint(ctx)
    current_host = _host()
    manifest = {"schema_version": SCHEMA_VERSION, "cycle_id": cycle_id,
                "task": os.path.basename(task_dir), "mode": mode, "host": current_host,
                "origin_host": previous.get("origin_host", previous.get("host", current_host)),
                "now": now.isoformat(),
                "started_at": previous.get("started_at") or _utc().isoformat(), "dry_run": dry_run,
                "baseline_id": ctx.baseline.get("meta", {}).get("id"), "status": "running",
                "stages": [], "versions": dict(previous.get("versions") or {},
                                                python=sys.version.split()[0]),
                "resume_count": int(previous.get("resume_count", 0)) + (1 if previous else 0)}
    if previous:
        manifest["resumed_at"] = _utc().isoformat()
        manifest["resumed_from_host"] = previous.get("host")
    write_json(os.path.join(cycle_dir, "cycle.json"), manifest)

    if standard_first and mode != "backtest" and not dry_run:
        from .standard_first import ensure
        standard_status = ensure(task_dir)
        if not standard_status.get("ready"):
            ctx.triggers.append("standard_first:{}".format(standard_status.get(
                "readiness", "incomplete")))
        manifest["standard_first"] = standard_status
        if not standard_status.get("ready"):
            manifest.setdefault("triggers", []).append("standard_first:{}".format(
                standard_status.get("readiness", "incomplete")))
        write_json(os.path.join(cycle_dir, "cycle.json"), manifest)

    with _Lock(state_dir, now):
        for entry in stages or plan["stages"]:
            name = _stage_name(entry)
            spec = entry if isinstance(entry, dict) else {"name": name}
            spec = dict(spec, name=name)
            started = time.time()
            if name in done:
                result = StageResult(**{k: v for k, v in done[name].items() if k in (
                    "name", "status", "outputs", "kpis", "triggers", "message")})
                if name == "refresh":
                    _builtin_stages.restore_refresh(ctx, result)
                if name.startswith("script:") or name == "kpis":
                    ctx.kpis.update(result.kpis)
            elif any(ctx.stage_results.get(n) is None or ctx.stage_results[n].status in ("fail", "skipped")
                     for n in NEEDS.get(name, []) if n in [_stage_name(e) for e in (stages or plan["stages"])]):
                result = StageResult(name, "skipped", message="a required stage did not complete")
            else:
                stage = resolve("stages", "script" if name.startswith("script:") else name)
                if stage is None:
                    result = StageResult(name, "not_installed", message="stage plugin not installed")
                else:
                    try:
                        result = stage(ctx, spec)
                    except Exception as error:
                        result = StageResult(name, "fail", message="{}: {}".format(
                            type(error).__name__, error))
            ctx.stage_results[name] = result
            ctx.triggers += [t for t in result.triggers if t not in ctx.triggers]
            record = result.to_dict()
            record["seconds"] = round(time.time() - started, 3)
            manifest["stages"].append(record)
            write_json(os.path.join(cycle_dir, "checkpoint.json"), _checkpoint(ctx, name))
            write_json(os.path.join(cycle_dir, "cycle.json"), manifest)

    manifest["versions"].update(ctx.versions)
    manifest["triggers"] = list(ctx.triggers)
    manifest["sources"] = {k: v.get("status") for k, v in ctx.sources.items()}
    manifest["notifications"] = ctx.notifications
    manifest["agent_review"] = ctx.agent_run
    manifest["standard_first"] = standard_status
    manifest["degraded"] = any(s["status"] in ("fail", "skipped", "not_installed")
                               for s in manifest["stages"]) or any(
        v not in ("ok",) for v in manifest["sources"].values()) or (
            bool(standard_first) and bool(standard_status) and not standard_status.get("ready"))
    manifest["solve"] = ctx.solve
    manifest["finished_at"] = _utc().isoformat()
    manifest["status"] = "complete"
    write_json(os.path.join(cycle_dir, "kpis.json"), ctx.kpis)
    write_json(os.path.join(cycle_dir, "triggers.json"), {"triggers": ctx.triggers})
    if not dry_run:
        _append_kpi_history(state_dir, cycle_id, now, ctx.kpis)
    write_json(os.path.join(cycle_dir, "cycle.json"), manifest)
    if not dry_run and mode != "backtest" and os.path.abspath(state_dir) == os.path.abspath(
            continuous_dir(task_dir)):
        from .living_report import update
        update(task_dir, event="cycle")
    return manifest


def interrupted_cycles(task_dir, state_dir=None):
    """Return persisted running cycles so recovery state is explicit to callers."""
    state_dir = state_dir or continuous_dir(task_dir)
    return _running_cycles(os.path.join(state_dir, "cycles"))


def load_cycle(task_dir, cycle_id, state_dir=None):
    state_dir = state_dir or continuous_dir(task_dir)
    return read_json(os.path.join(state_dir, "cycles", cycle_id, "cycle.json"))


def list_cycles(task_dir, state_dir=None):
    cycles_dir = os.path.join(state_dir or continuous_dir(task_dir), "cycles")
    if not os.path.isdir(cycles_dir):
        return []
    return sorted(n for n in os.listdir(cycles_dir)
                  if os.path.exists(os.path.join(cycles_dir, n, "cycle.json")))


def dumps(manifest):
    return json.dumps(manifest, indent=2, sort_keys=True, default=str)
