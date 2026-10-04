"""Engineer comments and restrictions for a living task: ``continuous/user_input.yaml``.

The cycle reads this file at the start of every run (stage ``inputs``), so a comment, a
restriction or a new fact written between two cycles changes what the next cycle does,
without editing code or the plan. Each entry has free text (always shown in the digest and
the living report, so the engineer and the agent see it) and optional machine-readable effects:

* ``lever_bound``   {lever, lo, hi}    replace the plan bounds of an optimiser lever (narrow or widen);
* ``lever_freeze``  {lever}            keep a lever at its current value;
* ``constraint``    {name, kpi, op, limit, margin, warn, hard, source}   set or add a goal constraint;
* ``setting``       {key, value}       set a plan value, e.g. ``production.rvp_bias_bara``;
* ``gate``          {name, abs_max|max|min}   change a model-validity gate.

Entries can expire (``expires``) or be resolved; a new or changed entry raises the trigger
``user_input:<id>``, an expired one ``user_input_expired:<id>``. Optimiser scripts call
``lever_limits`` to honour lever restrictions.
"""

import hashlib
import json
import os
from datetime import date, datetime

from .contracts import StageResult, register
from .plan import continuous_dir, read_json, write_json

FILE = "user_input.yaml"
KINDS = ("lever_bound", "lever_freeze", "constraint", "setting", "gate")
HEADER = """# Comments and restrictions from the engineers. Read at the start of every cycle.
# Add entries with `neqsim task-note <task> "text" [--lever NAME --lo X --hi Y] ...` or edit by hand.
# status: active | resolved. expires: YYYY-MM-DD (optional). effects are optional; the text is always shown.
"""


def path(task_dir):
    return os.path.join(continuous_dir(task_dir), FILE)


def load(task_dir):
    """All entries (any status); an empty list when the file is missing."""
    if not os.path.isfile(path(task_dir)):
        return []
    import yaml

    with open(path(task_dir), "r", encoding="utf-8") as f:
        data = yaml.safe_load(f) or {}
    entries = data.get("entries") if isinstance(data, dict) else data
    return [e for e in (entries or []) if isinstance(e, dict)]


def _save(task_dir, entries):
    import yaml

    os.makedirs(os.path.dirname(path(task_dir)), exist_ok=True)
    with open(path(task_dir), "w", encoding="utf-8") as f:
        f.write(HEADER)
        yaml.safe_dump({"entries": entries}, f, sort_keys=False, allow_unicode=True)


def _next_id(entries):
    numbers = [int(e["id"].split("-")[1]) for e in entries if str(e.get("id", "")).startswith("U-")
               and str(e["id"]).split("-")[1].isdigit()]
    return "U-{:03d}".format(max(numbers, default=0) + 1)


def add(task_dir, text, by="", effects=None, expires=None):
    """Append an entry and return it. ``effects`` is a list of effect mappings (see module doc)."""
    for effect in effects or []:
        if effect.get("kind") not in KINDS:
            raise ValueError("Unknown effect kind '{}'. Valid: {}".format(effect.get("kind"), ", ".join(KINDS)))
    entries = load(task_dir)
    entry = {"id": _next_id(entries), "date": date.today().isoformat(), "by": by or "", "status": "active",
             "text": text}
    if expires:
        entry["expires"] = str(expires)
    if effects:
        entry["effects"] = list(effects)
    entries.append(entry)
    _save(task_dir, entries)
    return entry


def resolve(task_dir, entry_id, by=""):
    """Mark an entry resolved; its effects stop applying from the next cycle."""
    entries = load(task_dir)
    for entry in entries:
        if entry.get("id") == entry_id:
            entry["status"], entry["resolved_by"], entry["resolved_on"] = "resolved", by, date.today().isoformat()
            _save(task_dir, entries)
            return entry
    raise ValueError("Unknown entry '{}'".format(entry_id))


def _expired(entry, today):
    value = entry.get("expires")
    if not value:
        return False
    try:
        return datetime.strptime(str(value)[:10], "%Y-%m-%d").date() < today
    except ValueError:
        return False


def _fingerprint(entry):
    return hashlib.sha256(json.dumps(entry, sort_keys=True, default=str).encode("utf-8")).hexdigest()[:16]


def describe(entry):
    """One line for the digest and the living report."""
    parts = []
    for fx in entry.get("effects") or []:
        kind = fx.get("kind")
        if kind == "lever_bound":
            parts.append("{} bounds {}..{}".format(fx.get("lever"), fx.get("lo", "plan"), fx.get("hi", "plan")))
        elif kind == "lever_freeze":
            parts.append("{} frozen".format(fx.get("lever")))
        elif kind == "constraint":
            parts.append("{} {} {}".format(fx.get("name") or fx.get("kpi"), fx.get("op", ""), fx.get("limit", "")).strip())
        elif kind == "setting":
            parts.append("{} = {}".format(fx.get("key"), fx.get("value")))
        elif kind == "gate":
            parts.append("gate {}".format(fx.get("name")))
    text = " ".join(str(entry.get("text", "")).split())
    return "{} ({}): {}{}".format(entry.get("id"), entry.get("by") or "unknown", text[:160],
                                 " [{}]".format("; ".join(parts)) if parts else "")


def lever_limits(ctx, name, lo, hi):
    """Plan bounds of lever ``name`` after user restrictions: (lo, hi, frozen)."""
    rule = (getattr(ctx, "user_levers", {}) or {}).get(str(name).lower())
    if not rule:
        return lo, hi, False
    return rule.get("lo", lo), rule.get("hi", hi), bool(rule.get("frozen"))


def _set_path(tree, dotted, value):
    keys = dotted.split(".")
    for key in keys[:-1]:
        tree = tree.setdefault(key, {})
    tree[keys[-1]] = value


def _apply(ctx, entry, warnings):
    applied = []
    for fx in entry.get("effects") or []:
        kind = fx.get("kind")
        if kind in ("lever_bound", "lever_freeze"):
            rule = ctx.user_levers.setdefault(str(fx.get("lever", "")).lower(), {})
            if kind == "lever_freeze":
                rule["frozen"] = True
            else:
                for bound in ("lo", "hi"):
                    if isinstance(fx.get(bound), (int, float)):
                        rule[bound] = float(fx[bound])
            applied.append("{}:{}".format(kind, fx.get("lever")))
        elif kind == "constraint":
            rows = ctx.goal.setdefault("constraints", [])
            name = fx.get("name") or fx.get("kpi")
            row = next((c for c in rows if isinstance(c, dict) and (c.get("name") or c.get("kpi")) == name), None)
            if row is None:
                if not fx.get("kpi"):
                    warnings.append("{}: constraint '{}' is new and needs a kpi".format(entry.get("id"), name))
                    continue
                row = {"name": name}
                rows.append(row)
            row.update({k: v for k, v in fx.items() if k != "kind"})
            row.setdefault("source", "user_input {}".format(entry.get("id")))
            applied.append("constraint:{}".format(name))
        elif kind == "setting":
            if not fx.get("key"):
                warnings.append("{}: setting without key".format(entry.get("id")))
                continue
            _set_path(ctx.plan, str(fx["key"]), fx.get("value"))
            applied.append("setting:{}".format(fx["key"]))
        elif kind == "gate":
            gate = next((g for g in ctx.plan.get("gates") or [] if g.get("name") == fx.get("name")), None)
            if gate is None:
                warnings.append("{}: no gate named '{}'".format(entry.get("id"), fx.get("name")))
                continue
            gate.update({k: v for k, v in fx.items() if k in ("abs_max", "max", "min")})
            applied.append("gate:{}".format(fx["name"]))
        elif kind is not None:
            warnings.append("{}: unknown effect kind '{}'".format(entry.get("id"), kind))
    return applied


def inputs(ctx, spec):
    """Apply the engineers' comments and restrictions for this cycle."""
    ctx.user_levers = {}
    entries = load(ctx.task_dir)
    seen_path = os.path.join(ctx.state_dir, "user_input_seen.json")
    seen = read_json(seen_path, {}) or {}
    today, triggers, warnings, active, applied = ctx.now.date(), [], [], [], {}
    for entry in entries:
        key, status = str(entry.get("id")), entry.get("status", "active")
        if status == "active" and _expired(entry, today):
            status = "expired"
        marker = "{}:{}".format(status, _fingerprint(entry))
        if seen.get(key) != marker:
            if status == "active":
                triggers.append("user_input:" + key)
            elif status == "expired" and not str(seen.get(key, "")).startswith("expired"):
                triggers.append("user_input_expired:" + key)
            seen[key] = marker
        if status == "active":
            active.append(entry)
            applied[key] = _apply(ctx, entry, warnings)
    if not ctx.dry_run:
        write_json(seen_path, seen)
    write_json(os.path.join(ctx.cycle_dir, "user_input.json"), {"active": active, "applied": applied, "warnings": warnings})
    ctx.sections.append("User input in force: " + ("; ".join(describe(e) for e in active) if active else "none"))
    if warnings:
        ctx.sections.append("User input warnings: " + "; ".join(warnings))
    return StageResult("inputs", "warn" if warnings else "ok", outputs=["user_input.json"], triggers=triggers,
                       message="{} active".format(len(active)))


register("stages", "inputs", inputs)

__all__ = ["add", "resolve", "load", "describe", "lever_limits", "inputs", "FILE", "KINDS"]
