"""Production-optimisation support for living tasks.

A living task that recommends operating changes needs four things the generic cycle does not
give by itself: hard constraints that are checked every cycle (product spec, equipment
limits), a validity gate on the model, a guard that withholds recommendations that are not
safe to show, and a check of what an implemented recommendation actually delivered.

Stages registered here (all optional, all driven by ``goal.yaml`` and ``cycle_plan.yaml``):

* ``gates``       model-validity gates on KPIs such as separator-temperature residuals;
* ``constraints`` slack of every goal constraint, with breach and low-margin triggers;
* ``guard``       drops proposals that violate a hard constraint, miss a prediction, or
                  arrive while a gate or an unconfirmed hard constraint blocks advice;
* ``outcome``     compares the realised gain of implemented ledger items with the prediction.

The optimiser itself stays a task-local ``script:`` stage that returns ``proposals``.
"""

import os

from .contracts import StageResult, register
from .ledger import Ledger
from .plan import write_json

UPPER = ("<=", "<")
LOWER = (">=", ">")


def _number(value):
    return isinstance(value, (int, float)) and not isinstance(value, bool)


def effective_limit(constraint):
    """Limit tightened by the safety margin, or None when the limit is not set."""
    limit = constraint.get("limit")
    if not _number(limit):
        return None
    margin = float(constraint.get("margin", 0.0) or 0.0)
    return limit - margin if constraint.get("op", "<=") in UPPER else limit + margin


def slack(constraint, value):
    """Distance to the effective limit; negative means the constraint is violated."""
    limit = effective_limit(constraint)
    if limit is None or not _number(value):
        return None
    return limit - value if constraint.get("op", "<=") in UPPER else value - limit


def evaluate_constraints(constraints, values):
    """Evaluate goal constraints against KPI values; one row per constraint.

    Status is ``unconfirmed`` (limit not set), ``no_data``, ``breach``, ``low_margin``
    (slack below ``warn``) or ``ok``.
    """
    rows = []
    for entry in constraints or []:
        if not isinstance(entry, dict) or not entry.get("kpi"):
            continue
        name = entry.get("name") or entry["kpi"]
        value = values.get(entry["kpi"])
        room = slack(entry, value)
        if effective_limit(entry) is None:
            status = "unconfirmed"
        elif room is None:
            status = "no_data"
        elif room < 0:
            status = "breach"
        elif _number(entry.get("warn")) and room < float(entry["warn"]):
            status = "low_margin"
        else:
            status = "ok"
        rows.append({"name": name, "kpi": entry["kpi"], "op": entry.get("op", "<="),
                     "limit": entry.get("limit"), "margin": entry.get("margin", 0.0),
                     "hard": bool(entry.get("hard", True)), "source": entry.get("source"),
                     "value": value, "slack": room, "status": status})
    return rows


def demonstrated_limit(values, quantile=0.99, design=None, min_samples=48):
    """Equipment limit from operating experience: the larger of design and a high quantile.

    ``values`` are historian samples of the limiting quantity (power, speed, K-factor, flow).
    With fewer than ``min_samples`` finite samples the design limit is used and the basis says so.
    """
    clean = sorted(float(v) for v in values if _number(v) and v == v)
    result = {"design": design, "samples": len(clean), "quantile": quantile}
    if len(clean) < min_samples:
        result.update({"limit": design, "demonstrated": None, "basis": "design (too few samples)"})
        return result
    position = quantile * (len(clean) - 1)
    low = int(position)
    high = min(low + 1, len(clean) - 1)
    demonstrated = clean[low] + (position - low) * (clean[high] - clean[low])
    limit = demonstrated if design is None else max(design, demonstrated)
    result.update({"limit": limit, "demonstrated": demonstrated,
                   "basis": "demonstrated" if design is None or demonstrated > design else "design"})
    return result


def _section(ctx, title, lines):
    ctx.sections.append("{}: {}".format(title, "; ".join(lines) if lines else "none"))


def gates(ctx, spec):
    """Stop recommendations while the model does not match the plant."""
    rows, blocks = [], []
    for gate in ctx.plan.get("gates") or []:
        name = gate.get("name") or gate.get("kpi")
        value = ctx.kpis.get(gate.get("kpi"))
        reason = ""
        if not _number(value):
            reason = "no_data"
        elif _number(gate.get("abs_max")) and abs(value) > float(gate["abs_max"]):
            reason = "abs>{}".format(gate["abs_max"])
        elif _number(gate.get("max")) and value > float(gate["max"]):
            reason = ">{}".format(gate["max"])
        elif _number(gate.get("min")) and value < float(gate["min"]):
            reason = "<{}".format(gate["min"])
        rows.append({"name": name, "kpi": gate.get("kpi"), "value": value, "passed": not reason, "reason": reason})
        if reason:
            blocks.append("model_gate:{}".format(name))
    ctx.guard_blocks += blocks
    write_json(os.path.join(ctx.cycle_dir, "gates.json"), rows)
    _section(ctx, "Model gates failed", ["{} ({})".format(r["name"], r["reason"]) for r in rows if not r["passed"]])
    return StageResult("gates", "warn" if blocks else "ok", outputs=["gates.json"], triggers=blocks)


def constraints(ctx, spec):
    """Slack of every goal constraint. A breach of a hard constraint is a trigger."""
    rows = evaluate_constraints(ctx.goal.get("constraints"), ctx.kpis)
    triggers = []
    for row in rows:
        if row["slack"] is not None:
            ctx.kpis["slack_" + row["name"]] = float(row["slack"])
        if row["status"] == "breach" and row["hard"]:
            triggers.append("constraint_breach:{}".format(row["name"]))
        elif row["status"] == "low_margin":
            triggers.append("constraint_margin:{}".format(row["name"]))
        elif row["status"] == "unconfirmed" and row["hard"]:
            ctx.guard_blocks.append("constraint_unconfirmed:{}".format(row["name"]))
        elif row["status"] == "no_data" and row["hard"]:
            ctx.guard_blocks.append("constraint_no_data:{}".format(row["name"]))
    write_json(os.path.join(ctx.cycle_dir, "constraints.json"), rows)
    _section(ctx, "Constraint slack", ["{} {} ({})".format(
        r["name"], "n/a" if r["slack"] is None else "{:.4g}".format(r["slack"]), r["status"]) for r in rows])
    return StageResult("constraints", "warn" if any(t.startswith("constraint_breach") for t in triggers) else "ok",
                       outputs=["constraints.json"], kpis={k: v for k, v in ctx.kpis.items() if k.startswith("slack_")},
                       triggers=triggers)


def _gain_value(proposal):
    gain = proposal.get("expected_gain")
    return gain.get("value") if isinstance(gain, dict) else gain


def guard(ctx, spec):
    """Keep only proposals that are safe to show; record why the others were withheld."""
    settings = ctx.plan.get("production", {}) or {}
    min_gain = float(settings.get("min_gain", 0.0) or 0.0)
    hard = [c for c in (ctx.goal.get("constraints") or [])
            if isinstance(c, dict) and c.get("kpi") and c.get("hard", True) and effective_limit(c) is not None]
    accepted, withheld = [], []
    for proposal in ctx.proposals:
        reasons = list(ctx.guard_blocks)
        if not proposal.get("setpoints"):
            reasons.append("no_setpoints")
        gain = _gain_value(proposal)
        if not _number(gain) or gain <= min_gain:
            reasons.append("gain_not_above_{}".format(min_gain))
        predicted = proposal.get("predicted") or {}
        for constraint in hard:
            name = constraint.get("name") or constraint["kpi"]
            room = slack(constraint, predicted.get(constraint["kpi"]))
            if room is None:
                reasons.append("no_prediction:{}".format(name))
            elif room < 0:
                reasons.append("violates:{}".format(name))
        if reasons:
            withheld.append({"title": proposal.get("title"), "reasons": reasons})
        else:
            accepted.append(dict(proposal, requires_approval=True, guard="passed"))
    ctx.proposals = accepted
    write_json(os.path.join(ctx.cycle_dir, "guard.json"), {"accepted": [p.get("title") for p in accepted],
                                                            "withheld": withheld, "blocks": list(ctx.guard_blocks)})
    _section(ctx, "Proposals withheld", ["{} ({})".format(w["title"], ", ".join(w["reasons"])) for w in withheld])
    triggers = ["proposals_withheld"] if withheld else []
    return StageResult("guard", "ok", outputs=["guard.json"], triggers=triggers,
                       message="{} accepted, {} withheld".format(len(accepted), len(withheld)))


def outcome(ctx, spec):
    """Compare the realised gain of implemented items with the predicted gain."""
    tolerance = float((ctx.plan.get("production", {}) or {}).get("outcome_tolerance", 0.5))
    book = Ledger(os.path.join(ctx.state_dir, "ledger", "events.jsonl"))
    sign = -1.0 if (ctx.goal.get("objective", {}) or {}).get("direction") == "minimize" else 1.0
    rows, triggers = [], []
    for key, item in book.current().items():
        kpi = item.get("objective_kpi")
        if item.get("status") != "implemented" or not kpi or not _number(item.get("baseline_value")):
            continue
        predicted, current = _gain_value(item), ctx.kpis.get(kpi)
        if not _number(predicted) or not _number(current) or predicted == 0:
            continue
        realised = sign * (current - float(item["baseline_value"]))
        ratio = realised / predicted
        verdict = "confirmed" if ratio >= tolerance else "miss"
        rows.append({"item": key, "kpi": kpi, "predicted": predicted, "realised": realised,
                     "ratio": ratio, "verdict": verdict})
        triggers.append("outcome_{}:{}".format(verdict, key))
        if not ctx.dry_run:
            book.update_value(key, {"metric": kpi, "realised_gain": realised, "predicted_gain": predicted,
                                    "cycle": ctx.cycle_id}, cycle=ctx.cycle_id)
    write_json(os.path.join(ctx.cycle_dir, "outcome.json"), rows)
    _section(ctx, "Outcome of implemented changes", ["{} {} ({:.0%} of predicted)".format(
        r["item"], r["verdict"], r["ratio"]) for r in rows])
    return StageResult("outcome", "ok", outputs=["outcome.json"], triggers=triggers)


for _name, _stage in (("gates", gates), ("constraints", constraints), ("guard", guard), ("outcome", outcome)):
    register("stages", _name, _stage)

__all__ = ["effective_limit", "slack", "evaluate_constraints", "demonstrated_limit", "gates", "constraints",
           "guard", "outcome"]
