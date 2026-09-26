"""Built-in generic cycle stages. Each stage is ``stage(ctx, spec) -> StageResult``.

Task-type specific work (a NeqSim replay, an estimator, an optimiser) plugs in either as
a task-local ``script:<name>`` stage or as an installed stage plugin; the runner does not
know the difference.
"""

import csv
import importlib.util
import os
import re
import subprocess
from datetime import timedelta

from . import agent_launch, notify
from .contracts import StageResult, register, resolve
from .drift import DriftMonitor
from .ledger import Ledger
from .plan import file_sha256, read_json, write_json
from .stop_rules import should_reopen
from .watermarks import Watermarks, parse_time

AGGREGATES = {
    "mean": lambda v: sum(v) / len(v),
    "min": min,
    "max": max,
    "last": lambda v: v[-1],
    "sum": sum,
    "count": len,
}
EVENT_TRIGGERS = {"brief_changed": "brief_changed", "new_evidence": "new_evidence",
                  "neqsim_changed": "neqsim_capability"}


def _number(value):
    try:
        return float(value)
    except (TypeError, ValueError):
        return value


def neqsim_commit():
    """Return the NeqSim source commit in use, or None when it cannot be determined."""
    root = os.environ.get("NEQSIM_PROJECT_ROOT") or os.path.dirname(
        os.path.dirname(os.path.abspath(__file__)))
    try:
        out = subprocess.run(["git", "rev-parse", "HEAD"], cwd=root, capture_output=True,
                             text=True, timeout=10)
        if out.returncode != 0:
            return None
        return out.stdout.strip() or None
    except (OSError, subprocess.TimeoutExpired):
        return None


def sense(ctx, spec):
    info = {"brief_sha256": None, "references_sha256": None, "neqsim_commit": neqsim_commit()}
    triggers = []
    brief = ctx.goal.get("brief")
    if brief:
        info["brief_sha256"] = file_sha256(os.path.join(ctx.task_dir, brief))
        if ctx.goal.get("brief_sha256") and info["brief_sha256"] != ctx.goal["brief_sha256"]:
            triggers.append("brief_changed")
    manifest = os.path.join(ctx.task_dir, "step1_scope_and_research", "references",
                            "collection_manifest.json")
    info["references_sha256"] = file_sha256(manifest)
    meta = ctx.baseline.get("meta", {})
    if meta.get("references_sha256") and info["references_sha256"] != meta["references_sha256"]:
        triggers.append("new_evidence")
    base_commit = meta.get("versions", {}).get("neqsim_commit")
    if base_commit and info["neqsim_commit"] and base_commit != info["neqsim_commit"]:
        triggers.append("neqsim_changed")
    if ctx.mode == "backtest":
        triggers = []  # the files describe today, not the simulated day
    ctx.versions.update({"neqsim_commit": info["neqsim_commit"]})
    write_json(os.path.join(ctx.cycle_dir, "sense.json"), info)
    return StageResult("sense", "ok", outputs=["sense.json"], triggers=triggers)


def _load_rows(ctx, source, paths, time_column, after):
    rows = []
    for path in paths:
        with open(path, "r", encoding="utf-8-sig", newline="") as f:
            for row in csv.DictReader(f):
                if after is None or parse_time(row[time_column]) > after:
                    rows.append({k: _number(v) if k != time_column else v for k, v in row.items()})
    ctx.new_rows[source] = rows
    return rows


def refresh(ctx, spec):
    sources = ctx.plan.get("sources", {})
    if not sources:
        return StageResult("refresh", "ok", message="no sources declared")
    marks = Watermarks(os.path.join(ctx.state_dir, "watermarks.json"))
    report, outputs, statuses = {}, [], []
    for name, source in sources.items():
        factory = resolve("adapters", source.get("adapter", ""))
        if factory is None:
            report[name] = {"status": "not_installed", "adapter": source.get("adapter")}
            statuses.append("not_installed")
            continue
        options = dict(source.get("options", {}))
        if "folder" in options and not os.path.isabs(options["folder"]):
            options["folder"] = os.path.join(ctx.task_dir, options["folder"])
        time_column = options.get("time_column", "timestamp")
        old = marks.get(name)
        since, until = marks.window(
            name, ctx.now, overlap=timedelta(hours=float(source.get("overlap_hours", 0))),
            default_since=ctx.now - timedelta(days=float(source.get("initial_lookback_days", 1))))
        try:
            result = factory(**options).pull(since, until, ctx.data_dir)
        except Exception as error:  # an adapter failure degrades one source, not the cycle
            report[name] = {"status": "failed", "message": str(error)}
            statuses.append("failed")
            continue
        rows = _load_rows(ctx, name, result.outputs, time_column, parse_time(old) if old else None) \
            if result.outputs else [dict((k, _number(v)) for k, v in r.items()) for r in result.records]
        ctx.new_rows[name] = rows
        status = result.status
        stale_after = source.get("stale_after_hours")
        if status == "ok" and not rows and stale_after and old:
            if ctx.now - parse_time(old) > timedelta(hours=float(stale_after)):
                status = "stale"
        if not ctx.dry_run:
            marks.advance(name, result)
        report[name] = dict(result.to_dict(), status=status, new_rows=len(rows))
        outputs += result.outputs
        statuses.append(status)
    write_json(os.path.join(ctx.cycle_dir, "refresh_report.json"), report)
    ctx.sources = report
    if statuses and all(s in ("failed", "not_installed") for s in statuses):
        state = "fail"
    else:
        state = "ok" if all(s == "ok" for s in statuses) else "warn"
    return StageResult("refresh", state, outputs=["refresh_report.json"] + outputs)


def restore_refresh(ctx, result):
    """Reload the rows of a completed refresh stage when a cycle resumes."""
    report = read_json(os.path.join(ctx.cycle_dir, "refresh_report.json"), {})
    ctx.sources = report
    for name, source in ctx.plan.get("sources", {}).items():
        paths = [p for p in report.get(name, {}).get("outputs", []) if os.path.exists(p)]
        time_column = source.get("options", {}).get("time_column", "timestamp")
        _load_rows(ctx, name, paths, time_column, None)


def script(ctx, spec):
    name = spec["name"].split(":", 1)[1]
    entry = ctx.plan.get("scripts", {}).get(name)
    if not entry:
        return StageResult(spec["name"], "fail", message="scripts.{} is not declared".format(name))
    path = os.path.join(ctx.task_dir, entry["file"])
    module_spec = importlib.util.spec_from_file_location("neqsim_task_stage_" + name, path)
    module = importlib.util.module_from_spec(module_spec)
    module_spec.loader.exec_module(module)
    output = getattr(module, entry.get("function", "run"))(ctx) or {}
    ctx.kpis.update({k: v for k, v in output.get("kpis", {}).items() if v is not None})
    ctx.proposals += output.get("proposals", [])
    if "solve" in output:
        ctx.solve = output["solve"]
    return StageResult(spec["name"], output.get("status", "ok"),
                       outputs=output.get("outputs", []), kpis=output.get("kpis", {}),
                       triggers=output.get("triggers", []), message=output.get("message", ""))


def kpis(ctx, spec):
    results = read_json(os.path.join(ctx.task_dir, "results.json"), {}) or {}
    missing = []
    for name, kpi in ctx.plan.get("kpis", {}).items():
        if name in ctx.kpis:
            continue
        if kpi.get("from") == "results":
            value = results.get("key_results", {}).get(kpi.get("key", name))
            if isinstance(value, (int, float)):
                ctx.kpis[name] = float(value)
            continue
        values = [r.get(kpi.get("column", name)) for r in ctx.new_rows.get(kpi.get("source"), [])]
        values = [v for v in values if isinstance(v, float)]
        if values:
            ctx.kpis[name] = AGGREGATES[kpi.get("agg", "mean")](values)
        else:
            missing.append(name)
    return StageResult("kpis", "warn" if missing else "ok", kpis=dict(ctx.kpis),
                       message="no data for: " + ", ".join(missing) if missing else "")


def drift(ctx, spec):
    config = ctx.plan.get("drift", {})
    signals = config.get("signals", {})
    if isinstance(signals, list):
        signals = {name: {"kpi": name} for name in signals}
    settings = dict(config.get("settings") or {})
    overrides = dict(settings.get("signals") or {})
    for name, signal in signals.items():
        extra = {k: v for k, v in signal.items() if k != "kpi"}
        if extra:
            overrides[name] = dict(overrides.get(name, {}), **extra)
    settings["signals"] = overrides
    monitor = DriftMonitor.load(os.path.join(ctx.state_dir, "drift_state.json"), settings)
    statuses, triggers = {}, []
    for name, signal in signals.items():
        value = ctx.kpis.get(signal.get("kpi", name))
        if value is None:
            continue
        status = monitor.update(name, value)
        statuses[name] = status
        if status["new_alarm"]:
            triggers.append("drift:" + name)
    if not ctx.dry_run:
        monitor.save(os.path.join(ctx.state_dir, "drift_state.json"))
    write_json(os.path.join(ctx.cycle_dir, "validation.json"), statuses)
    return StageResult("drift", "ok", outputs=["validation.json"], triggers=triggers)


def goal(ctx, spec):
    metric = ctx.goal.get("objective", {}).get("metric")
    if not metric:
        return StageResult("goal", "ok", message="no goal metric")
    current = ctx.kpis.get(metric)
    promoted = ctx.baseline.get("kpis", {}).get(metric)
    events = [EVENT_TRIGGERS[t] for t in ctx.triggers if t in EVENT_TRIGGERS]
    reasons = should_reopen(ctx.goal, current, promoted, events)
    return StageResult("goal", "ok", kpis={metric: current} if current is not None else {},
                       triggers=["reopen:" + r for r in reasons])


_CRITERION = re.compile(r"^\s*(>=|<=|>|<)\s*(-?[0-9.eE+-]+)\s*$")


def _criterion_hit(value, text):
    match = _CRITERION.match(str(text))
    if not match or value is None:
        return False
    op, limit = match.group(1), float(match.group(2))
    return {">": value > limit, "<": value < limit, ">=": value >= limit, "<=": value <= limit}[op]


def diff(ctx, spec):
    base = ctx.baseline.get("kpis", {})
    previous = ctx.previous_kpis or {}
    rows, triggers = {}, []
    for name, value in ctx.kpis.items():
        rows[name] = {"value": value,
                      "baseline": base.get(name),
                      "delta_vs_baseline": value - base[name] if isinstance(base.get(name), (int, float)) else None,
                      "previous": previous.get(name),
                      "delta_vs_previous": value - previous[name] if isinstance(previous.get(name), (int, float)) else None}
    rules = ctx.plan.get("triggers", {})
    # Step and criterion triggers fire on crossing, so a persistent state raises one trigger.
    for name, threshold in rules.get("kpi_step", {}).items():
        delta = rows.get(name, {}).get("delta_vs_baseline")
        before = previous.get(name)
        was = isinstance(before, (int, float)) and isinstance(base.get(name), (int, float)) \
            and abs(before - base[name]) > float(threshold)
        if delta is not None and abs(delta) > float(threshold) and not was:
            triggers.append("kpi_step:" + name)
    for name, text in rules.get("criteria", {}).items():
        if _criterion_hit(ctx.kpis.get(name), text) and not _criterion_hit(previous.get(name), text):
            triggers.append("criterion:" + name)
    write_json(os.path.join(ctx.cycle_dir, "diff_vs_baseline.json"), rows)
    return StageResult("diff", "ok", outputs=["diff_vs_baseline.json"], triggers=triggers)


def ledger(ctx, spec):
    book = Ledger(os.path.join(ctx.state_dir, "ledger", "events.jsonl"))
    items = book.current()
    open_titles = {v.get("title") for v in items.values()
                   if v.get("status") not in ("rejected", "superseded", "verified")}
    created, updated = [], []
    for proposal in ctx.proposals:
        if proposal.get("title") in open_titles:
            continue
        created.append(proposal.get("title"))
        if not ctx.dry_run:
            fields = {k: v for k, v in proposal.items() if k not in ("title", "category")}
            book.create(proposal["title"], proposal.get("category", "operational"),
                        cycle=ctx.cycle_id, **fields)
    for key, item in items.items():
        kpi = item.get("kpi")
        if kpi and kpi in ctx.kpis and item.get("status") not in ("rejected", "superseded"):
            value = {"metric": kpi, "value": ctx.kpis[kpi], "cycle": ctx.cycle_id}
            if not ctx.dry_run:
                book.update_value(key, value, cycle=ctx.cycle_id)
            updated.append(key)
    ctx.new_proposals = created
    return StageResult("ledger", "ok", message="{} new, {} updated".format(len(created), len(updated)))


def _fmt(value):
    return "{:.4g}".format(value) if isinstance(value, float) else str(value)


def digest(ctx, spec):
    triggers = list(dict.fromkeys(ctx.triggers))
    degraded = [n for n, r in ctx.stage_results.items() if r.status in ("fail", "skipped", "not_installed")]
    ctx.summary = {"needs_decision": bool(triggers or ctx.new_proposals), "triggers": triggers,
                   "stop_state": ctx.stop_state, "degraded": degraded}
    base = ctx.baseline.get("kpis", {})
    lines = ["# {} - cycle {}".format(os.path.basename(ctx.task_dir), ctx.cycle_id), "",
             "Mode: {}. Baseline: {}. Status: {}.".format(
                 ctx.mode, ctx.baseline.get("meta", {}).get("id", "none"),
                 "degraded ({})".format(", ".join(degraded)) if degraded else "complete"), ""]
    if ctx.sources:
        lines.append("Sources: " + "; ".join("{} {} ({} rows)".format(
            n, s.get("status"), s.get("new_rows", 0)) for n, s in ctx.sources.items()))
        lines.append("")
    if ctx.kpis:
        lines += ["| KPI | Value | Baseline |", "|-----|-------|----------|"]
        lines += ["| {} | {} | {} |".format(k, _fmt(v), _fmt(base.get(k, "-"))) for k, v in sorted(ctx.kpis.items())]
        lines.append("")
    lines.append("Triggers: " + (", ".join(triggers) if triggers else "none"))
    if ctx.new_proposals:
        lines.append("New ledger items: " + "; ".join(ctx.new_proposals))
    if ctx.stop_state:
        lines.append("Solve loop: " + ctx.stop_state)
    lines += ["", "Needs a decision: " + ("yes" if ctx.summary["needs_decision"] else "no")]
    ctx.digest_text = "\n".join(lines) + "\n"
    with open(os.path.join(ctx.cycle_dir, "digest.md"), "w", encoding="utf-8") as f:
        f.write(ctx.digest_text)
    return StageResult("digest", "ok", outputs=["digest.md"])


def _channels(ctx):
    specs = ctx.plan.get("notify", {}).get("channels", [])
    return [{"type": c} if isinstance(c, str) else dict(c) for c in specs]


def notify_stage(ctx, spec):
    results = []
    subject = "{}: {}".format(os.path.basename(ctx.task_dir),
                              "needs a decision" if ctx.summary.get("needs_decision") else "no action")
    for channel in _channels(ctx):
        kind = channel.get("type", "file")
        sender = resolve("notifiers", kind)
        if sender is None:
            results.append({"channel": kind, "status": "not_installed"})
        elif not notify.wanted(channel, ctx.summary):
            results.append({"channel": kind, "status": "filtered"})
        elif kind != "file" and (ctx.dry_run or ctx.mode == "backtest"):
            results.append({"channel": kind, "status": "suppressed"})
        else:
            try:
                results.append(sender(channel, subject, ctx.digest_text, ctx.summary, ctx.cycle_dir))
            except Exception as error:
                results.append({"channel": kind, "status": "fail", "message": str(error)})
    ctx.notifications = results
    failed = [r for r in results if r.get("status") == "fail"]
    return StageResult("notify", "warn" if failed else "ok", message="; ".join(
        "{} {}".format(r["channel"], r["status"]) for r in results))


def agent(ctx, spec):
    config = ctx.plan.get("agent", {})
    triggers = list(dict.fromkeys(ctx.triggers))
    if not triggers or not config.get("enabled"):
        ctx.agent_run = {"requested": bool(triggers), "ran": False}
        return StageResult("agent", "ok", message="not requested")
    argv = agent_launch.build_command(ctx.task_dir, ctx.cycle_dir,
                                      agent_launch.triage_prompt(ctx.cycle_id, triggers),
                                      agent=config.get("agent", "continuous-improvement"),
                                      executable=config.get("executable", "copilot"),
                                      deny=config.get("deny"))
    if ctx.no_agent or ctx.dry_run or ctx.mode == "backtest":
        ctx.agent_run = {"requested": True, "ran": False, "command": argv, "status": "suppressed"}
        return StageResult("agent", "ok", message="suppressed")
    result = agent_launch.launch(argv, timeout=int(config.get("timeout_s", 1800)), cwd=ctx.task_dir)
    ctx.agent_run = dict(result, requested=True, ran=result.get("status") == "ok", command=argv)
    return StageResult("agent", "ok" if result.get("status") == "ok" else "warn",
                       message=result.get("status", ""))


for _name, _stage in (("sense", sense), ("refresh", refresh), ("script", script), ("kpis", kpis),
                      ("drift", drift), ("goal", goal), ("diff", diff), ("ledger", ledger),
                      ("digest", digest), ("notify", notify_stage), ("agent", agent)):
    register("stages", _name, _stage)
