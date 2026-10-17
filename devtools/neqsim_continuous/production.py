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

import json
import math
import os

from .contracts import StageResult, register
from .ledger import Ledger
from .plan import write_json

UPPER = ("<=", "<")
LOWER = (">=", ">")


def _number(value):
    if not isinstance(value, (int, float)) or isinstance(value, bool):
        return False
    try:
        return math.isfinite(float(value))
    except OverflowError:
        return False


OPTIMIZER_EVIDENCE = (
    ("simulation_converged", "simulation_not_converged"),
    ("candidate_feasible", "infeasible_candidate"),
    ("candidate_finite", "non_finite_candidate"),
    ("constraint_evidence_complete", "constraint_evidence_incomplete"),
    ("state_restore_complete", "state_restore_incomplete"),
    ("accepted_point_replayed", "accepted_point_not_replayed"),
    ("actions_complete", "partial_action_application"),
)


def effective_limit(constraint):
    """Limit tightened by the safety margin, or None when the limit is not set."""
    limit = constraint.get("limit")
    margin = constraint.get("margin", 0.0)
    op = constraint.get("op", "<=")
    if not _number(limit) or not _number(margin) or op not in UPPER + LOWER:
        return None
    return limit - margin if op in UPPER else limit + margin


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
    clean = sorted(float(v) for v in values if _number(v))
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


def _optimizer_evidence_reasons(ctx, proposal):
    """Return fail-closed reasons for incomplete or stale optimizer validation evidence."""
    return _validate_optimizer_evidence(proposal.get("optimizer_evidence"), ctx.cycle_id)


def _validate_optimizer_evidence(evidence, expected_cycle_id):
    """Validate optimizer evidence for an explicitly identified engineering cycle."""
    if not isinstance(evidence, dict):
        return ["missing_optimizer_evidence"]
    reasons = []
    if evidence.get("cycle_id") != expected_cycle_id:
        reasons.append("stale_candidate")
    for field, reason in OPTIMIZER_EVIDENCE:
        if evidence.get(field) is not True:
            reasons.append(reason)
    evaluation_count = evidence.get("evaluation_count")
    if (not _number(evaluation_count) or evaluation_count < 1
            or float(evaluation_count) != int(evaluation_count)):
        reasons.append("invalid_evaluation_count")
    runtime_seconds = evidence.get("runtime_seconds")
    if not _number(runtime_seconds) or runtime_seconds < 0:
        reasons.append("invalid_runtime_seconds")
    return reasons


def _positive_integer(value):
    """Return true only for a positive, finite integer value."""
    return _number(value) and value >= 1 and float(value) == int(value)


def _non_negative_integer(value):
    """Return true only for a non-negative, finite integer value."""
    return _number(value) and value >= 0 and float(value) == int(value)


def _same_finite_vector(first, second):
    """Return true for equal-length, exactly equal finite numeric vectors."""
    if not isinstance(first, list) or not isinstance(second, list) or len(first) != len(second):
        return False
    return all(_number(left) and _number(right) and float(left) == float(right)
               for left, right in zip(first, second))


def _envelope_evidence_reasons(ctx, proposal, required=False):
    """Validate a persisted accepted-point envelope without trusting its Java origin."""
    if "accepted_point_envelope_evidence" not in proposal:
        return ["missing_accepted_point_envelope_evidence"] if required else []
    evidence = proposal.get("accepted_point_envelope_evidence")
    if not isinstance(evidence, dict):
        return ["invalid_accepted_point_envelope_evidence"]

    return _validate_envelope_evidence(
        evidence, proposal.get("optimizer_evidence"), proposal.get("setpoints"), ctx.cycle_id)


def _validate_envelope_evidence(evidence, optimizer_evidence, setpoints, expected_cycle_id):
    """Validate one complete accepted-point envelope against its owning cycle and proposal."""

    reasons = []
    if evidence.get("schema_version") != 1:
        reasons.append("unsupported_envelope_schema")
    if evidence.get("cycle_id") != expected_cycle_id:
        reasons.append("stale_envelope_evidence")
    if evidence.get("optimizer_evidence") != optimizer_evidence:
        reasons.append("envelope_optimizer_evidence_mismatch")

    action_ids = evidence.get("accepted_action_ids")
    accepted = evidence.get("accepted_candidate_values")
    valid_actions = (isinstance(action_ids, list) and action_ids
                     and all(isinstance(item, str) and item for item in action_ids)
                     and len(set(action_ids)) == len(action_ids))
    accepted_ids = action_ids if valid_actions else []
    if not valid_actions or not isinstance(accepted, list) or len(accepted) != len(accepted_ids):
        reasons.append("invalid_accepted_action_vector")
    elif (not isinstance(setpoints, dict) or set(setpoints) != set(accepted_ids)
          or not _same_finite_vector([setpoints.get(action_id) for action_id in accepted_ids], accepted)):
        reasons.append("envelope_setpoints_mismatch")

    slices = evidence.get("slices")
    slice_count = evidence.get("slice_count")
    if not _positive_integer(slice_count) or not isinstance(slices, list) or len(slices) != int(slice_count or 0):
        reasons.append("invalid_envelope_slice_count")
        return reasons

    requested_total = 0
    completed_total = 0
    evaluation_total = 0
    runtime_total = 0.0
    axis_signatures = set()
    for index, row in enumerate(slices):
        prefix = "envelope_slice_{}".format(index)
        if not isinstance(row, dict):
            reasons.append(prefix + "_invalid")
            continue
        if (row.get("study_id") != evidence.get("study_id")
                or row.get("candidate_evaluator_id") != evidence.get("candidate_evaluator_id")):
            reasons.append(prefix + "_identity_mismatch")
        if row.get("complete") is not True or row.get("outcome") != "COMPLETE":
            reasons.append(prefix + "_incomplete")
        if not _same_finite_vector(row.get("anchor_values"), accepted):
            reasons.append(prefix + "_anchor_mismatch")

        axes = row.get("axis_action_ids")
        if (not isinstance(axes, list) or not axes
                or any(not isinstance(axis, str) or axis not in accepted_ids for axis in axes)
                or len(set(axes)) != len(axes)):
            reasons.append(prefix + "_invalid_axes")
        else:
            signature = tuple(axes)
            if signature in axis_signatures:
                reasons.append("duplicate_envelope_axes")
            axis_signatures.add(signature)

        requested = row.get("requested_point_count")
        completed = row.get("completed_point_count")
        evaluations = row.get("evaluation_count")
        runtime = row.get("runtime_seconds")
        points = row.get("points")
        if (not _positive_integer(requested) or not _positive_integer(completed)
                or int(requested) != int(completed)
                or not _positive_integer(evaluations)
                or not _number(runtime) or runtime < 0
                or not isinstance(points, list) or len(points) != int(completed or 0)):
            reasons.append(prefix + "_invalid_counts")
        else:
            requested_total += int(requested)
            completed_total += int(completed)
            evaluation_total += int(evaluations)
            runtime_total += float(runtime)
        if isinstance(points, list):
            for point in points:
                if (not isinstance(point, dict) or point.get("baseline_restored") is not True
                        or point.get("baseline_simulation_converged") is not True):
                    reasons.append(prefix + "_point_recovery_incomplete")
                    break
                if (not isinstance(point.get("candidate_values"), list)
                        or len(point["candidate_values"]) != len(accepted_ids)
                        or any(not _number(value) for value in point["candidate_values"])):
                    reasons.append(prefix + "_invalid_point_candidate")
                    break

    if (evidence.get("requested_point_count") != requested_total
            or evidence.get("completed_point_count") != completed_total
            or evidence.get("envelope_evaluation_count") != evaluation_total):
        reasons.append("envelope_aggregate_count_mismatch")
    envelope_runtime = evidence.get("envelope_runtime_seconds")
    if (not _number(envelope_runtime) or envelope_runtime < 0
            or not math.isclose(float(envelope_runtime), runtime_total, rel_tol=1.0e-12, abs_tol=1.0e-12)):
        reasons.append("envelope_runtime_mismatch")
    optimizer = optimizer_evidence
    optimizer_count = optimizer.get("evaluation_count") if isinstance(optimizer, dict) else None
    total_count = evidence.get("total_evaluation_count")
    if (not _non_negative_integer(total_count) or not _positive_integer(optimizer_count)
            or int(total_count) != int(optimizer_count) + evaluation_total):
        reasons.append("envelope_total_evaluation_count_mismatch")
    if evidence.get("advisory") is not True:
        reasons.append("envelope_not_advisory")
    return reasons


def _text(value):
    """Return true only for non-blank text."""
    return isinstance(value, str) and bool(value.strip())


def _action_ids(value):
    """Return a validated ordered action-identifier list, or None."""
    if (not isinstance(value, list) or not value
            or any(not _text(item) for item in value)
            or len(set(value)) != len(value)):
        return None
    return value


def _bottleneck_name(value):
    """Return the qualified bottleneck identity, or None for valid absent evidence."""
    if value is None:
        return None
    if not isinstance(value, dict):
        return False
    if (value.get("source") not in (
            "INSTALLED_EQUIPMENT", "REQUIRED_HYDRAULIC", "PLANT_CONSTRAINT",
            "REGISTERED_MODEL_CONSTRAINT")
            or not _text(value.get("qualified_constraint_name"))
            or not isinstance(value.get("feasible"), bool)):
        return False
    for field in ("normalized_utilization", "normalized_margin", "current_value",
                  "applicable_limit", "physical_margin"):
        if value.get(field) is not None and not _number(value.get(field)):
            return False
    for field in ("physical_unit", "diagnostic", "applicable_limit_role",
                  "applicable_limit_provenance"):
        if not isinstance(value.get(field), str):
            return False
    return value["qualified_constraint_name"]


def _sampled_constraint_name(value):
    """Return one sampled leading-constraint identity, or None when explicitly absent."""
    if value is None:
        return None
    if not isinstance(value, dict) or not _text(value.get("qualified_constraint_name")):
        return False
    return value["qualified_constraint_name"]


def _exact_vector_key(values, expected_size=None):
    """Return a bit-preserving finite numeric-vector key, or None."""
    if (not isinstance(values, list)
            or (expected_size is not None and len(values) != expected_size)
            or any(not _number(value) for value in values)):
        return None
    return tuple(float(value).hex() for value in values)


def _exact_grid_key(values, axis_count):
    """Return a bit-preserving sampled-grid key, or None."""
    if not isinstance(values, list) or len(values) != axis_count:
        return None
    rows = tuple(_exact_vector_key(row) for row in values)
    if any(row is None or not row for row in rows):
        return None
    return rows


def _period_change_diagnostics(periods, action_ids, time_unit):
    """Derive schema-v2 adjacent-period diagnostics from persisted sampled evidence."""
    diagnostics = []
    for from_period, to_period in zip(periods, periods[1:]):
        from_envelope = from_period.get("envelope_evidence")
        to_envelope = to_period.get("envelope_evidence")
        if not isinstance(from_envelope, dict) or not isinstance(to_envelope, dict):
            return None
        from_values = from_envelope.get("accepted_candidate_values")
        to_values = to_envelope.get("accepted_candidate_values")
        if (not isinstance(from_values, list) or not isinstance(to_values, list)
                or any(not _number(value) for value in from_values)
                or any(not _number(value) for value in to_values)
                or len(from_values) != len(action_ids)
                or len(to_values) != len(action_ids)):
            return None
        action_changes = [{
            "action_id": action_id,
            "from_value": from_value,
            "to_value": to_value,
            "delta": float(to_value) - float(from_value),
        } for action_id, from_value, to_value in zip(action_ids, from_values, to_values)]

        from_slices = from_envelope.get("slices")
        to_slices = to_envelope.get("slices")
        if not isinstance(from_slices, list) or not isinstance(to_slices, list):
            return None
        from_by_axes = {}
        to_by_axes = {}
        for row in from_slices:
            axes = row.get("axis_action_ids") if isinstance(row, dict) else None
            if not isinstance(axes, list) or not axes or any(not _text(axis) for axis in axes):
                return None
            from_by_axes[tuple(axes)] = row
        for row in to_slices:
            axes = row.get("axis_action_ids") if isinstance(row, dict) else None
            if not isinstance(axes, list) or not axes or any(not _text(axis) for axis in axes):
                return None
            to_by_axes[tuple(axes)] = row
        if len(from_by_axes) != len(from_slices) or len(to_by_axes) != len(to_slices):
            return None

        slice_changes = []
        for axes, from_slice in from_by_axes.items():
            to_slice = to_by_axes.get(axes)
            if to_slice is None:
                continue
            from_points = from_slice.get("points")
            to_points = to_slice.get("points")
            if not isinstance(from_points, list) or not isinstance(to_points, list):
                return None
            from_grid = _exact_grid_key(from_slice.get("axis_values"), len(axes))
            to_grid = _exact_grid_key(to_slice.get("axis_values"), len(axes))
            if from_grid is None or to_grid is None:
                return None
            from_by_value = {}
            to_by_value = {}
            for point in from_points:
                values = point.get("axis_values") if isinstance(point, dict) else None
                key = _exact_vector_key(values, len(axes))
                if key is None:
                    return None
                from_by_value[key] = point
            for point in to_points:
                values = point.get("axis_values") if isinstance(point, dict) else None
                key = _exact_vector_key(values, len(axes))
                if key is None:
                    return None
                to_by_value[key] = point
            if len(from_by_value) != len(from_points) or len(to_by_value) != len(to_points):
                return None
            common = [key for key in from_by_value if key in to_by_value]
            became_feasible = 0
            became_infeasible = 0
            constraint_changes = 0
            for key in common:
                from_point = from_by_value[key]
                to_point = to_by_value[key]
                if not isinstance(from_point.get("feasible"), bool) or not isinstance(to_point.get("feasible"), bool):
                    return None
                if not from_point["feasible"] and to_point["feasible"]:
                    became_feasible += 1
                elif from_point["feasible"] and not to_point["feasible"]:
                    became_infeasible += 1
                from_constraint = _sampled_constraint_name(from_point.get("leading_constraint"))
                to_constraint = _sampled_constraint_name(to_point.get("leading_constraint"))
                if from_constraint is False or to_constraint is False:
                    return None
                if from_constraint != to_constraint:
                    constraint_changes += 1
            from_feasible = sum(point.get("feasible") is True for point in from_points)
            to_feasible = sum(point.get("feasible") is True for point in to_points)
            from_transitions = from_slice.get("bottleneck_transitions")
            to_transitions = to_slice.get("bottleneck_transitions")
            if not isinstance(from_transitions, list) or not isinstance(to_transitions, list):
                return None
            slice_changes.append({
                "axis_action_ids": list(axes),
                "grid_identical": from_grid == to_grid,
                "from_requested_point_count": from_slice.get("requested_point_count"),
                "to_requested_point_count": to_slice.get("requested_point_count"),
                "from_feasible_point_count": from_feasible,
                "to_feasible_point_count": to_feasible,
                "feasible_point_count_delta": to_feasible - from_feasible,
                "comparable_point_count": len(common),
                "from_only_point_count": len(from_points) - len(common),
                "to_only_point_count": len(to_points) - len(common),
                "became_feasible_count": became_feasible,
                "became_infeasible_count": became_infeasible,
                "leading_constraint_change_count": constraint_changes,
                "from_bottleneck_transition_count": len(from_transitions),
                "to_bottleneck_transition_count": len(to_transitions),
            })
        diagnostics.append({
            "from_period_id": from_period.get("period_id"),
            "to_period_id": to_period.get("period_id"),
            "from_elapsed_time": from_period.get("elapsed_time"),
            "to_elapsed_time": to_period.get("elapsed_time"),
            "time_unit": time_unit,
            "accepted_action_changes": action_changes,
            "comparable_slice_count": len(slice_changes),
            "removed_slice_axes": [list(axes) for axes in from_by_axes if axes not in to_by_axes],
            "added_slice_axes": [list(axes) for axes in to_by_axes if axes not in from_by_axes],
            "slice_changes": slice_changes,
            "advisory": True,
        })
    return diagnostics


def _lifecycle_evidence_reasons(ctx, proposal, required=False):
    """Validate persisted lifecycle envelopes and bind the current period to the proposal."""
    if "lifecycle_envelope_evidence" not in proposal:
        return ["missing_lifecycle_envelope_evidence"] if required else []
    evidence = proposal.get("lifecycle_envelope_evidence")
    if not isinstance(evidence, dict):
        return ["invalid_lifecycle_envelope_evidence"]

    reasons = []
    schema_version = evidence.get("schema_version")
    if schema_version not in (1, 2):
        reasons.append("unsupported_lifecycle_envelope_schema")
    if evidence.get("cycle_id") != ctx.cycle_id:
        reasons.append("stale_lifecycle_envelope_evidence")
    for field in ("lifecycle_study_id", "lifecycle_study_name",
                  "lifecycle_study_provenance", "time_unit"):
        if not _text(evidence.get(field)):
            reasons.append("invalid_lifecycle_{}".format(field))

    action_ids = _action_ids(evidence.get("accepted_action_ids"))
    if action_ids is None:
        reasons.append("invalid_lifecycle_action_ids")
        action_ids = []
    periods = evidence.get("periods")
    period_count = evidence.get("period_count")
    if (not _positive_integer(period_count) or not isinstance(periods, list)
            or len(periods) != int(period_count or 0)):
        reasons.append("invalid_lifecycle_period_count")
        return reasons

    requested_total = 0
    completed_total = 0
    evaluation_total = 0
    runtime_total = 0.0
    seen_period_ids = set()
    previous_elapsed = None
    period_rows = []
    current_envelope = None
    for index, row in enumerate(periods):
        prefix = "lifecycle_period_{}".format(index)
        if not isinstance(row, dict):
            reasons.append(prefix + "_invalid")
            continue
        period_id = row.get("period_id")
        if not _text(period_id) or period_id in seen_period_ids:
            reasons.append(prefix + "_invalid_id")
        else:
            seen_period_ids.add(period_id)
        if (not _text(row.get("period_name"))
                or not _text(row.get("configuration_provenance"))):
            reasons.append(prefix + "_invalid_metadata")
        elapsed = row.get("elapsed_time")
        if (not _number(elapsed) or elapsed < 0
                or (previous_elapsed is not None and elapsed <= previous_elapsed)):
            reasons.append(prefix + "_invalid_elapsed_time")
        else:
            previous_elapsed = float(elapsed)
        if row.get("time_unit") != evidence.get("time_unit"):
            reasons.append(prefix + "_time_unit_mismatch")

        envelope = row.get("envelope_evidence")
        if not isinstance(envelope, dict):
            reasons.append(prefix + "_invalid_envelope")
            continue
        if (row.get("envelope_study_id") != envelope.get("study_id")
                or row.get("candidate_evaluator_id") != envelope.get("candidate_evaluator_id")):
            reasons.append(prefix + "_identity_mismatch")
        if envelope.get("accepted_action_ids") != action_ids:
            reasons.append(prefix + "_action_ids_mismatch")

        accepted = envelope.get("accepted_candidate_values")
        optimizer = envelope.get("optimizer_evidence")
        period_setpoints = ({action_id: value for action_id, value in zip(action_ids, accepted)}
                            if isinstance(accepted, list) and len(accepted) == len(action_ids)
                            else {})
        period_reasons = _validate_envelope_evidence(
            envelope, optimizer, period_setpoints, period_id)
        period_reasons.extend(_validate_optimizer_evidence(optimizer, period_id))
        reasons.extend(prefix + "_" + reason for reason in period_reasons)

        requested = envelope.get("requested_point_count")
        completed = envelope.get("completed_point_count")
        evaluations = envelope.get("total_evaluation_count")
        runtime = envelope.get("envelope_runtime_seconds")
        if (_non_negative_integer(requested) and _non_negative_integer(completed)
                and _non_negative_integer(evaluations) and _number(runtime) and runtime >= 0):
            requested_total += int(requested)
            completed_total += int(completed)
            evaluation_total += int(evaluations)
            runtime_total += float(runtime)

        bottleneck = row.get("accepted_bottleneck")
        bottleneck_name = _bottleneck_name(bottleneck)
        if bottleneck_name is False:
            reasons.append(prefix + "_invalid_bottleneck")
            bottleneck_name = None
        if bottleneck != envelope.get("accepted_point_bottleneck"):
            reasons.append(prefix + "_bottleneck_mismatch")
        period_rows.append((period_id, elapsed, bottleneck_name))
        if period_id == ctx.cycle_id:
            current_envelope = envelope

    if current_envelope is None:
        reasons.append("missing_current_lifecycle_period")
    elif current_envelope != proposal.get("accepted_point_envelope_evidence"):
        reasons.append("lifecycle_current_period_envelope_mismatch")

    if (evidence.get("requested_point_count") != requested_total
            or evidence.get("completed_point_count") != completed_total
            or evidence.get("total_evaluation_count") != evaluation_total):
        reasons.append("lifecycle_aggregate_count_mismatch")
    runtime = evidence.get("envelope_runtime_seconds")
    if (not _number(runtime) or runtime < 0
            or not math.isclose(float(runtime), runtime_total,
                                rel_tol=1.0e-12, abs_tol=1.0e-12)):
        reasons.append("lifecycle_runtime_mismatch")

    expected_transitions = []
    for previous, current in zip(period_rows, period_rows[1:]):
        if previous[2] != current[2]:
            expected_transitions.append({
                "from_period_id": previous[0],
                "to_period_id": current[0],
                "from_elapsed_time": previous[1],
                "to_elapsed_time": current[1],
                "time_unit": evidence.get("time_unit"),
                "from_constraint": previous[2],
                "to_constraint": current[2],
            })
    if evidence.get("bottleneck_transitions") != expected_transitions:
        reasons.append("lifecycle_bottleneck_transitions_mismatch")
    if schema_version == 2:
        expected_period_changes = _period_change_diagnostics(periods, action_ids, evidence.get("time_unit"))
        if (expected_period_changes is None
                or evidence.get("period_change_diagnostics") != expected_period_changes):
            reasons.append("lifecycle_period_change_diagnostics_mismatch")
    if evidence.get("advisory") is not True:
        reasons.append("lifecycle_envelope_not_advisory")
    return reasons


def _non_finite_fields(values):
    """Return sorted keys whose proposed numeric values are absent or non-finite."""
    if not isinstance(values, dict):
        return ["<mapping>"]
    return sorted(str(key) for key, value in values.items() if not _number(value))


def _proposal_snapshot(proposal):
    """Bind publication to exact JSON content, including evidence boolean types."""
    return json.dumps(proposal, sort_keys=True, separators=(",", ":"))


def guard(ctx, spec):
    """Keep only proposals that are safe to show; record why the others were withheld."""
    ctx.guarded_proposals = set()
    settings = ctx.plan.get("production", {}) or {}
    configured_min_gain = settings.get("min_gain", 0.0)
    invalid_min_gain = not _number(configured_min_gain)
    min_gain = float(configured_min_gain) if not invalid_min_gain else 0.0
    hard = [c for c in (ctx.goal.get("constraints") or [])
            if isinstance(c, dict) and c.get("kpi") and c.get("hard", True) and effective_limit(c) is not None]
    accepted, withheld = [], []
    for proposal in ctx.proposals:
        reasons = list(ctx.guard_blocks)
        if invalid_min_gain:
            reasons.append("invalid_min_gain")
        setpoints = proposal.get("setpoints")
        if not isinstance(setpoints, dict) or not setpoints:
            reasons.append("no_setpoints")
        else:
            reasons.extend("non_finite_setpoint:{}".format(name)
                           for name in _non_finite_fields(setpoints))
        gain = _gain_value(proposal)
        if not _number(gain) or gain <= min_gain:
            reasons.append("gain_not_above_{}".format(min_gain))
        predicted = proposal.get("predicted")
        if not isinstance(predicted, dict):
            predicted = {}
        reasons.extend("non_finite_prediction:{}".format(name)
                       for name in _non_finite_fields(predicted))
        if "baseline_value" in proposal and not _number(proposal.get("baseline_value")):
            reasons.append("non_finite_baseline")
        elif "baseline_value" not in proposal:
            reasons.append("missing_baseline")
        if not isinstance(proposal.get("objective_kpi"), str) or not proposal.get("objective_kpi"):
            reasons.append("missing_objective_kpi")
        reasons.extend(_optimizer_evidence_reasons(ctx, proposal))
        reasons.extend(_envelope_evidence_reasons(
            ctx, proposal, required=settings.get("require_envelope_evidence") is True))
        reasons.extend(_lifecycle_evidence_reasons(
            ctx, proposal,
            required=settings.get("require_lifecycle_envelope_evidence") is True))
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
    ctx.guarded_proposals = {_proposal_snapshot(proposal) for proposal in accepted}
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
