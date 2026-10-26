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
from types import SimpleNamespace

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


def _objective_evidence_reasons(ctx, proposal, required=False):
    """Bind proposal economics to exact source objective and reviewed baseline evidence."""
    if not required:
        return []
    evidence = proposal.get("optimizer_evidence")
    fields = ("objective_name", "objective_direction", "objective_unit",
              "initial_objective_value", "accepted_objective_value")
    if not isinstance(evidence, dict) or not any(field in evidence for field in fields):
        return ["missing_objective_evidence"]
    if any(field not in evidence for field in fields):
        return ["incomplete_objective_evidence"]

    objective = (ctx.goal or {}).get("objective") or {}
    metric = objective.get("metric")
    source_name = objective.get("source_name")
    direction = objective.get("direction")
    unit = objective.get("unit")
    initial = evidence.get("initial_objective_value")
    accepted = evidence.get("accepted_objective_value")
    baseline = (ctx.baseline.get("kpis") or {}).get(metric) if isinstance(ctx.baseline, dict) else None
    reasons = []
    if not all(_text(value) for value in (metric, source_name, direction, unit)):
        reasons.append("incomplete_task_objective_metadata")
    if evidence.get("objective_name") != source_name:
        reasons.append("objective_name_mismatch")
    if str(evidence.get("objective_direction", "")).lower() != direction:
        reasons.append("objective_direction_mismatch")
    if evidence.get("objective_unit") != unit:
        reasons.append("objective_unit_mismatch")
    if not all(_number(value) for value in (initial, accepted, baseline)):
        reasons.append("non_finite_objective_evidence")
        return reasons
    if float(initial) != float(baseline):
        reasons.append("objective_baseline_mismatch")
    if proposal.get("objective_kpi") != metric:
        reasons.append("objective_kpi_mismatch")
    predicted = proposal.get("predicted")
    if (not isinstance(predicted, dict) or not _number(predicted.get(metric))
            or float(predicted[metric]) != float(accepted)):
        reasons.append("objective_prediction_mismatch")
    expected_gain = (float(accepted) - float(initial)) * (
        -1.0 if direction == "minimize" else 1.0)
    if not _number(_gain_value(proposal)) or float(_gain_value(proposal)) != expected_gain:
        reasons.append("objective_gain_mismatch")
    return reasons


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
    schema_version = evidence.get("schema_version")
    if schema_version not in (1, 2):
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
        if ("axis_action_semantics" in row or "operating_region_transitions" in row):
            if not _valid_operating_region_transitions(row, axes, points):
                reasons.append(prefix + "_operating_region_transition_mismatch")
        if ("operating_region_boundary_refinements" in row
                or "maximum_boundary_refinement_evaluations" in row):
            if not _valid_operating_region_boundary_refinements(row):
                reasons.append(prefix + "_operating_region_boundary_refinement_mismatch")

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
    if schema_version == 2:
        expected_summary = _cross_slice_constraint_summary(
            slices, evidence.get("cross_slice_constraint_summary"))
        if (expected_summary is None
                or evidence.get("cross_slice_constraint_summary") != expected_summary):
            reasons.append("envelope_constraint_summary_mismatch")
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


def _cross_slice_constraint_summary(slices, persisted):
    """Rebuild the exact schema-v2 constraint summary from sampled and endpoint rows."""
    if (not isinstance(slices, list) or not isinstance(persisted, dict)
            or persisted.get("summary_version") != 1
            or not _number(persisted.get("maximum_normalized_margin"))
            or persisted["maximum_normalized_margin"] < 0
            or not _text(persisted.get("threshold_provenance"))):
        return None
    maximum_margin = float(persisted["maximum_normalized_margin"])
    accumulators = []

    def observe(evidence, role, axes):
        if evidence is None:
            return True
        if _bottleneck_name(evidence) is False:
            return False
        identity = (
            evidence.get("source"), evidence.get("qualified_constraint_name"),
            evidence.get("applicable_limit"), evidence.get("physical_unit"),
            evidence.get("applicable_limit_role"),
            evidence.get("applicable_limit_provenance"),
        )
        accumulator = next((item for item in accumulators
                            if item["identity"] == identity), None)
        if accumulator is None:
            accumulator = {
                "identity": identity,
                "source": evidence["source"],
                "qualified_constraint_name": evidence["qualified_constraint_name"],
                "applicable_limit": evidence.get("applicable_limit"),
                "physical_unit": evidence["physical_unit"],
                "applicable_limit_role": evidence["applicable_limit_role"],
                "applicable_limit_provenance": evidence["applicable_limit_provenance"],
                "slice_axes": [],
                "sampled_point_count": 0,
                "feasible_boundary_count": 0,
                "infeasible_boundary_count": 0,
                "feasible_evidence_count": 0,
                "rejecting_evidence_count": 0,
                "minimum_normalized_margin": None,
                "representative_feasible_constraint": None,
                "representative_rejecting_constraint": None,
            }
            accumulators.append(accumulator)
        axes_copy = list(axes)
        if axes_copy not in accumulator["slice_axes"]:
            accumulator["slice_axes"].append(axes_copy)
        count_field = {
            "sampled_point": "sampled_point_count",
            "feasible_boundary": "feasible_boundary_count",
            "infeasible_boundary": "infeasible_boundary_count",
        }.get(role)
        if count_field is None:
            return False
        accumulator[count_field] += 1
        if evidence.get("feasible") is True:
            accumulator["feasible_evidence_count"] += 1
            margin = evidence.get("normalized_margin")
            representative = accumulator["representative_feasible_constraint"]
            representative_margin = (representative.get("normalized_margin")
                                     if isinstance(representative, dict) else None)
            if (representative is None
                    or (_number(margin)
                        and (not _number(representative_margin)
                             or float(margin) < float(representative_margin)))):
                accumulator["representative_feasible_constraint"] = evidence
            if (_number(margin)
                    and (accumulator["minimum_normalized_margin"] is None
                         or float(margin) < accumulator["minimum_normalized_margin"])):
                accumulator["minimum_normalized_margin"] = float(margin)
        else:
            accumulator["rejecting_evidence_count"] += 1
            if accumulator["representative_rejecting_constraint"] is None:
                accumulator["representative_rejecting_constraint"] = evidence
        return True

    for envelope_slice in slices:
        if not isinstance(envelope_slice, dict):
            return None
        axes = envelope_slice.get("axis_action_ids")
        points = envelope_slice.get("points")
        refinements = envelope_slice.get("operating_region_boundary_refinements", [])
        if (not isinstance(axes, list) or not isinstance(points, list)
                or not isinstance(refinements, list)):
            return None
        for point in points:
            if (not isinstance(point, dict)
                    or not observe(point.get("leading_constraint"), "sampled_point", axes)):
                return None
        for refinement in refinements:
            if (not isinstance(refinement, dict)
                    or not observe(refinement.get("feasible_boundary_constraint"),
                                   "feasible_boundary", axes)
                    or not observe(refinement.get("infeasible_boundary_constraint"),
                                   "infeasible_boundary", axes)):
                return None

    ranked = [item for item in accumulators
              if (item["rejecting_evidence_count"] > 0
                  or (item["minimum_normalized_margin"] is not None
                      and item["minimum_normalized_margin"] <= maximum_margin))]

    def rank_key(item):
        limit_key = ((0, float(item["applicable_limit"]))
                     if _number(item["applicable_limit"]) else (1, 0.0))
        identity_tail = (
            item["qualified_constraint_name"], item["source"],
            limit_key, item["physical_unit"],
            item["applicable_limit_role"], item["applicable_limit_provenance"],
        )
        if item["rejecting_evidence_count"] > 0:
            return ((0, -item["rejecting_evidence_count"],
                     -item["infeasible_boundary_count"]) + identity_tail)
        return ((1, item["minimum_normalized_margin"],
                 -item["feasible_boundary_count"],
                 -item["feasible_evidence_count"]) + identity_tail)

    try:
        ranked.sort(key=rank_key)
    except (TypeError, ValueError):
        return None
    entries = []
    for rank, item in enumerate(ranked, 1):
        entries.append({
            "rank": rank,
            "classification": ("REJECTING" if item["rejecting_evidence_count"] > 0
                               else "NEAR_ACTIVE"),
            "source": item["source"],
            "qualified_constraint_name": item["qualified_constraint_name"],
            "applicable_limit": item["applicable_limit"],
            "physical_unit": item["physical_unit"],
            "applicable_limit_role": item["applicable_limit_role"],
            "applicable_limit_provenance": item["applicable_limit_provenance"],
            "slice_count": len(item["slice_axes"]),
            "slice_axes": item["slice_axes"],
            "sampled_point_count": item["sampled_point_count"],
            "feasible_boundary_count": item["feasible_boundary_count"],
            "infeasible_boundary_count": item["infeasible_boundary_count"],
            "feasible_evidence_count": item["feasible_evidence_count"],
            "rejecting_evidence_count": item["rejecting_evidence_count"],
            "minimum_normalized_margin": item["minimum_normalized_margin"],
            "representative_feasible_constraint": item["representative_feasible_constraint"],
            "representative_rejecting_constraint": item["representative_rejecting_constraint"],
        })
    return {
        "summary_version": 1,
        "maximum_normalized_margin": persisted["maximum_normalized_margin"],
        "threshold_provenance": persisted["threshold_provenance"],
        "observed_constraint_count": len(accumulators),
        "ranked_constraint_count": len(entries),
        "excluded_constraint_count": len(accumulators) - len(entries),
        "ranked_constraints": entries,
        "advisory": True,
        "interpretation": (
            "Exact sampled/refined evidence; not a shadow price, causal attribution, "
            "global active set, or approval"),
    }


def _sampled_constraint_name(value):
    """Return one sampled leading-constraint identity, or None when explicitly absent."""
    if value is None:
        return None
    if not isinstance(value, dict) or not _text(value.get("qualified_constraint_name")):
        return False
    return value["qualified_constraint_name"]


def _sampled_constraint_endpoint(value):
    """Return an exact sampled constraint (source, identity), or None when absent."""
    if value is None:
        return None
    if (not isinstance(value, dict)
            or value.get("source") not in (
                "INSTALLED_EQUIPMENT", "REQUIRED_HYDRAULIC", "PLANT_CONSTRAINT",
                "REGISTERED_MODEL_CONSTRAINT")
            or not _text(value.get("qualified_constraint_name"))):
        return False
    return value["source"], value["qualified_constraint_name"]


def _expected_operating_region_transition(axis_id, semantics, previous, current):
    """Derive one Java-compatible sampled-edge event, or None when nothing changed."""
    previous_constraint = _sampled_constraint_endpoint(previous.get("leading_constraint"))
    current_constraint = _sampled_constraint_endpoint(current.get("leading_constraint"))
    if previous_constraint is False or current_constraint is False:
        return False
    if (not isinstance(previous.get("feasible"), bool)
            or not isinstance(current.get("feasible"), bool)
            or not _text(previous.get("outcome")) or not _text(current.get("outcome"))):
        return False
    if previous["feasible"] and not current["feasible"]:
        kind = "BECAME_INFEASIBLE"
    elif not previous["feasible"] and current["feasible"]:
        kind = "BECAME_FEASIBLE"
    elif previous["outcome"] != current["outcome"]:
        kind = "EVALUATION_OUTCOME_CHANGED"
    elif previous_constraint != current_constraint:
        kind = "LEADING_CONSTRAINT_CHANGED"
    else:
        return None
    previous_value = previous["axis_values"]
    current_value = current["axis_values"]
    return {
        "axis_action_id": axis_id,
        "action_value_semantics": semantics,
        "kind": kind,
        "from_sequence_index": previous["sequence_index"],
        "to_sequence_index": current["sequence_index"],
        "from_axis_value": previous_value,
        "to_axis_value": current_value,
        "sampled_interval_width": abs(float(current_value) - float(previous_value)),
        "from_feasible": previous["feasible"],
        "to_feasible": current["feasible"],
        "from_outcome": previous["outcome"],
        "to_outcome": current["outcome"],
        "from_constraint_source": None if previous_constraint is None else previous_constraint[0],
        "to_constraint_source": None if current_constraint is None else current_constraint[0],
        "from_constraint": None if previous_constraint is None else previous_constraint[1],
        "to_constraint": None if current_constraint is None else current_constraint[1],
        "interpolated": False,
        "sampled_event_only": True,
    }


def _valid_operating_region_transitions(row, axes, points):
    """Recompute optional sampled operating-region evidence and reject tampering."""
    semantics = row.get("axis_action_semantics")
    transitions = row.get("operating_region_transitions")
    grids = row.get("axis_values")
    if (not isinstance(axes, list) or len(axes) not in (1, 2)
            or not isinstance(semantics, list) or len(semantics) != len(axes)
            or any(value not in ("CONTINUOUS", "DISCRETE") for value in semantics)
            or not isinstance(transitions, list) or not isinstance(points, list)
            or not isinstance(grids, list) or len(grids) != len(axes)
            or any(not isinstance(grid, list) or not grid for grid in grids)):
        return False
    requested_points = math.prod(len(grid) for grid in grids)
    if len(points) != requested_points:
        return False

    by_grid = {}
    ordered = []
    for point in points:
        if not isinstance(point, dict):
            return False
        indexes = point.get("grid_indexes")
        values = point.get("axis_values")
        sequence = point.get("sequence_index")
        if (not isinstance(indexes, list) or len(indexes) != len(axes)
                or any(not _non_negative_integer(value) for value in indexes)
                or not isinstance(values, list) or len(values) != len(axes)
                or any(not _number(value) for value in values)
                or not _non_negative_integer(sequence)):
            return False
        key = tuple(int(value) for value in indexes)
        if key in by_grid or any(key[axis] >= len(grids[axis]) for axis in range(len(axes))):
            return False
        sampled_values = [grids[axis][key[axis]] for axis in range(len(axes))]
        if (any(not _number(value) for value in sampled_values)
                or any(float(sampled_values[axis]) != float(values[axis])
                       for axis in range(len(axes)))):
            return False
        by_grid[key] = point
        ordered.append(point)
    ordered.sort(key=lambda point: int(point["sequence_index"]))
    if [int(point["sequence_index"]) for point in ordered] != list(range(len(ordered))):
        return False

    expected = []
    for current in ordered:
        indexes = tuple(int(value) for value in current["grid_indexes"])
        adjacent = []
        if len(axes) == 1 and indexes[0] > 0:
            adjacent.append((0, (indexes[0] - 1,)))
        elif len(axes) == 2:
            if indexes[1] > 0:
                adjacent.append((1, (indexes[0], indexes[1] - 1)))
            if indexes[0] > 0:
                adjacent.append((0, (indexes[0] - 1, indexes[1])))
        for axis, previous_key in adjacent:
            previous = by_grid.get(previous_key)
            if previous is None:
                return False
            previous_edge = dict(previous, axis_values=previous["axis_values"][axis])
            current_edge = dict(current, axis_values=current["axis_values"][axis])
            transition = _expected_operating_region_transition(
                axes[axis], semantics[axis], previous_edge, current_edge)
            if transition is False:
                return False
            if transition is not None:
                expected.append(transition)
    return transitions == expected


def _valid_operating_region_boundary_refinements(row):
    """Recompute fail-closed continuous bisection receipts from persisted midpoint evidence."""
    transitions = row.get("operating_region_transitions")
    refinements = row.get("operating_region_boundary_refinements")
    points = row.get("points")
    maximum = row.get("maximum_boundary_refinement_evaluations")
    relative_tolerance = row.get("boundary_refinement_relative_tolerance")
    provenance = row.get("boundary_refinement_tolerance_provenance")
    if (not isinstance(transitions, list) or not isinstance(refinements, list)
            or not _non_negative_integer(maximum)
            or not _number(relative_tolerance) or relative_tolerance <= 0
            or not _text(provenance)):
        return False

    allowed_rejections = {
        "HYDRAULIC_CONSTRAINT_VIOLATED",
        "OTHER_MODEL_CONSTRAINT_VIOLATED",
        "EVIDENCE_OUTSIDE_VALIDITY_RANGE",
    }
    eligible = [transition for transition in transitions
                if isinstance(transition, dict)
                and transition.get("action_value_semantics") == "CONTINUOUS"
                and transition.get("kind") in ("BECAME_FEASIBLE", "BECAME_INFEASIBLE")]
    eligible = [transition for transition in eligible
                if ((transition.get("from_feasible") is True
                     and transition.get("from_outcome") == "FEASIBLE"
                     and transition.get("to_outcome") in allowed_rejections)
                    or (transition.get("to_feasible") is True
                        and transition.get("to_outcome") == "FEASIBLE"
                        and transition.get("from_outcome") in allowed_rejections))]
    if (int(maximum) == 0 and refinements):
        return False
    if int(maximum) > 0 and len(refinements) != len(eligible):
        return False

    for receipt, transition in zip(refinements, eligible):
        if not isinstance(receipt, dict):
            return False
        identity_fields = (
            ("axis_action_id", "axis_action_id"),
            ("from_sequence_index", "from_sequence_index"),
            ("to_sequence_index", "to_sequence_index"),
        )
        if any(receipt.get(left) != transition.get(right) for left, right in identity_fields):
            return False
        if (receipt.get("action_value_semantics") != "CONTINUOUS"
                or receipt.get("sampled_from_axis_value") != transition.get("from_axis_value")
                or receipt.get("sampled_to_axis_value") != transition.get("to_axis_value")
                or receipt.get("maximum_evaluations") != maximum
                or receipt.get("relative_tolerance") != relative_tolerance
                or receipt.get("tolerance_provenance") != provenance
                or receipt.get("interpolated") is not False
                or receipt.get("advisory") is not True):
            return False
        lower = receipt.get("action_lower_bound")
        upper = receipt.get("action_upper_bound")
        absolute_tolerance = receipt.get("absolute_tolerance")
        if (not _number(lower) or not _number(upper) or upper <= lower
                or not _number(absolute_tolerance) or absolute_tolerance <= 0
                or not math.isclose(float(absolute_tolerance),
                                    float(relative_tolerance) * (float(upper) - float(lower)),
                                    rel_tol=1.0e-12, abs_tol=0.0)):
            return False
        from_value = transition.get("from_axis_value")
        to_value = transition.get("to_axis_value")
        if (not _number(from_value) or not _number(to_value)
                or not (float(lower) <= float(from_value) <= float(upper))
                or not (float(lower) <= float(to_value) <= float(upper))):
            return False
        if transition.get("from_feasible") is True:
            feasible_value, infeasible_value = float(from_value), float(to_value)
        elif transition.get("to_feasible") is True:
            feasible_value, infeasible_value = float(to_value), float(from_value)
        else:
            return False

        has_feasible_boundary = "feasible_boundary_constraint" in receipt
        has_infeasible_boundary = "infeasible_boundary_constraint" in receipt
        if has_feasible_boundary != has_infeasible_boundary:
            return False
        feasible_constraint = None
        infeasible_constraint = None
        if has_feasible_boundary:
            from_index = transition.get("from_sequence_index")
            to_index = transition.get("to_sequence_index")
            if (not isinstance(points, list)
                    or not _non_negative_integer(from_index)
                    or not _non_negative_integer(to_index)
                    or from_index >= len(points) or to_index >= len(points)
                    or not isinstance(points[from_index], dict)
                    or not isinstance(points[to_index], dict)):
                return False
            from_constraint = points[from_index].get("leading_constraint")
            to_constraint = points[to_index].get("leading_constraint")
            if transition.get("from_feasible") is True:
                feasible_constraint, infeasible_constraint = from_constraint, to_constraint
            else:
                feasible_constraint, infeasible_constraint = to_constraint, from_constraint

        steps = receipt.get("steps")
        if (not isinstance(steps, list) or len(steps) > int(maximum)
                or receipt.get("evaluation_count") != len(steps)):
            return False
        derived_status = None
        for sequence, step in enumerate(steps):
            if not isinstance(step, dict) or step.get("sequence_index") != sequence:
                return False
            midpoint = feasible_value + 0.5 * (infeasible_value - feasible_value)
            if (not _number(step.get("axis_value"))
                    or float(step["axis_value"]) != midpoint
                    or not isinstance(step.get("candidate_values"), list)
                    or any(not _number(value) for value in step["candidate_values"])
                    or step.get("baseline_restored") is not True
                    or step.get("baseline_simulation_converged") is not True):
                return False
            if step.get("feasible") is True:
                if (step.get("outcome") != "FEASIBLE"
                        or step.get("candidate_simulation_converged") is not True
                        or step.get("candidate_evaluator_feasible") is not True):
                    return False
                feasible_value = midpoint
                feasible_constraint = step.get("leading_constraint")
            elif (step.get("candidate_simulation_converged") is True
                  and step.get("outcome") in allowed_rejections):
                infeasible_value = midpoint
                infeasible_constraint = step.get("leading_constraint")
            else:
                derived_status = "INELIGIBLE_EVALUATION"
                if sequence != len(steps) - 1:
                    return False
        width = abs(infeasible_value - feasible_value)
        if (receipt.get("feasible_axis_value") != feasible_value
                or receipt.get("infeasible_axis_value") != infeasible_value
                or receipt.get("bracket_width") != width):
            return False
        if (has_feasible_boundary
                and (receipt.get("feasible_boundary_constraint") != feasible_constraint
                     or receipt.get("infeasible_boundary_constraint") != infeasible_constraint)):
            return False
        if derived_status is None:
            if width <= float(absolute_tolerance):
                derived_status = "CONVERGED"
            elif len(steps) == int(maximum):
                derived_status = "MAXIMUM_EVALUATIONS_REACHED"
            else:
                derived_status = "NUMERICAL_STAGNATION"
        if (receipt.get("status") != derived_status
                or receipt.get("converged") != (derived_status == "CONVERGED")):
            return False
    return True


def _has_boundary_constraint_headroom(slices):
    """Return whether every persisted refinement reports both exact endpoint constraints."""
    return (isinstance(slices, list)
            and all(isinstance(envelope_slice, dict)
                    and isinstance(envelope_slice.get(
                        "operating_region_boundary_refinements"), list)
                    and all(isinstance(refinement, dict)
                            and "feasible_boundary_constraint" in refinement
                            and "infeasible_boundary_constraint" in refinement
                            for refinement in envelope_slice.get(
                                "operating_region_boundary_refinements", []))
                    for envelope_slice in slices))


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


def _exact_list_difference(first, second):
    """Return ordered exact rows in first but not second, preserving multiplicity."""
    remaining = list(second)
    difference = []
    for row in first:
        try:
            index = remaining.index(row)
        except ValueError:
            difference.append(row)
        else:
            remaining.pop(index)
    return difference


def _constraint_summary_identities(envelope):
    """Return exact ranked rejecting/near-active identities from a schema-v2 envelope."""
    if not isinstance(envelope, dict):
        return None
    summary = envelope.get("cross_slice_constraint_summary")
    ranked = summary.get("ranked_constraints") if isinstance(summary, dict) else None
    if not isinstance(ranked, list):
        return None
    identities = []
    for row in ranked:
        if (not isinstance(row, dict)
                or row.get("classification") not in ("REJECTING", "NEAR_ACTIVE")
                or row.get("source") not in (
                    "INSTALLED_EQUIPMENT", "REQUIRED_HYDRAULIC", "PLANT_CONSTRAINT",
                    "REGISTERED_MODEL_CONSTRAINT")
                or not _text(row.get("qualified_constraint_name"))
                or (row.get("applicable_limit") is not None
                    and not _number(row.get("applicable_limit")))
                or not isinstance(row.get("physical_unit"), str)
                or not isinstance(row.get("applicable_limit_role"), str)
                or not isinstance(row.get("applicable_limit_provenance"), str)):
            return None
        identity = {
            "classification": row["classification"],
            "source": row["source"],
            "qualified_constraint_name": row["qualified_constraint_name"],
            "applicable_limit": row.get("applicable_limit"),
            "physical_unit": row["physical_unit"],
            "applicable_limit_role": row["applicable_limit_role"],
            "applicable_limit_provenance": row["applicable_limit_provenance"],
        }
        if identity in identities:
            return None
        identities.append(identity)
    return identities


def _period_change_diagnostics(periods, action_ids, time_unit, state_metric_ids=None,
                               require_operating_transitions=False,
                               require_boundary_refinements=False,
                               require_constraint_summary_identities=False):
    """Derive adjacent-period diagnostics from persisted sampled and accepted-state evidence."""
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
        state_changes = None
        if state_metric_ids is not None:
            from_metrics = from_period.get("accepted_state_metrics")
            to_metrics = to_period.get("accepted_state_metrics")
            if (not isinstance(from_metrics, list) or not isinstance(to_metrics, list)
                    or len(from_metrics) != len(state_metric_ids)
                    or len(to_metrics) != len(state_metric_ids)):
                return None
            state_changes = []
            for metric_id, from_metric, to_metric in zip(state_metric_ids, from_metrics, to_metrics):
                if (not isinstance(from_metric, dict) or not isinstance(to_metric, dict)
                        or from_metric.get("metric_id") != metric_id
                        or to_metric.get("metric_id") != metric_id
                        or from_metric.get("unit") != to_metric.get("unit")
                        or not _number(from_metric.get("value"))
                        or not _number(to_metric.get("value"))):
                    return None
                state_changes.append({
                    "metric_id": metric_id,
                    "unit": from_metric.get("unit"),
                    "from_value": from_metric.get("value"),
                    "to_value": to_metric.get("value"),
                    "delta": float(to_metric.get("value")) - float(from_metric.get("value")),
                })

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
            slice_change = {
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
            }
            from_operating_transitions = from_slice.get("operating_region_transitions")
            to_operating_transitions = to_slice.get("operating_region_transitions")
            if (require_operating_transitions
                    and (from_operating_transitions is None or to_operating_transitions is None)):
                return None
            if from_operating_transitions is not None or to_operating_transitions is not None:
                if (not isinstance(from_operating_transitions, list)
                        or not isinstance(to_operating_transitions, list)):
                    return None
                removed = _exact_list_difference(from_operating_transitions, to_operating_transitions)
                added = _exact_list_difference(to_operating_transitions, from_operating_transitions)
                slice_change.update({
                    "from_operating_region_transition_count": len(from_operating_transitions),
                    "to_operating_region_transition_count": len(to_operating_transitions),
                    "retained_operating_region_transition_count": (
                        len(from_operating_transitions) - len(removed)),
                    "removed_operating_region_transitions": removed,
                    "added_operating_region_transitions": added,
                })
            from_refinements = from_slice.get("operating_region_boundary_refinements")
            to_refinements = to_slice.get("operating_region_boundary_refinements")
            if (require_boundary_refinements
                    and (from_refinements is None or to_refinements is None)):
                return None
            if from_refinements is not None or to_refinements is not None:
                if not isinstance(from_refinements, list) or not isinstance(to_refinements, list):
                    return None
                removed = _exact_list_difference(from_refinements, to_refinements)
                added = _exact_list_difference(to_refinements, from_refinements)
                slice_change.update({
                    "from_operating_region_boundary_refinement_count": len(from_refinements),
                    "to_operating_region_boundary_refinement_count": len(to_refinements),
                    "retained_operating_region_boundary_refinement_count": (
                        len(from_refinements) - len(removed)),
                    "removed_operating_region_boundary_refinements": removed,
                    "added_operating_region_boundary_refinements": added,
                })
            slice_changes.append(slice_change)
        diagnostic = {
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
        }
        if state_changes is not None:
            diagnostic["accepted_state_changes"] = state_changes
        if require_constraint_summary_identities:
            from_identities = _constraint_summary_identities(from_envelope)
            to_identities = _constraint_summary_identities(to_envelope)
            if from_identities is None or to_identities is None:
                return None
            removed_identities = _exact_list_difference(from_identities, to_identities)
            added_identities = _exact_list_difference(to_identities, from_identities)
            diagnostic.update({
                "from_ranked_constraint_identity_count": len(from_identities),
                "to_ranked_constraint_identity_count": len(to_identities),
                "retained_ranked_constraint_identity_count": (
                    len(from_identities) - len(removed_identities)),
                "removed_ranked_constraint_identities": removed_identities,
                "added_ranked_constraint_identities": added_identities,
            })
        diagnostics.append(diagnostic)
    return diagnostics


def _finite_json_object(evidence_json):
    """Decode one finite JSON object without accepting JavaScript numeric extensions."""
    if isinstance(evidence_json, bytes):
        encoded = evidence_json.decode("utf-8")
    else:
        encoded = str(evidence_json)

    def reject_constant(value):
        raise ValueError("Lifecycle evidence contains non-finite JSON constant: {}".format(value))

    try:
        evidence = json.loads(encoded, parse_constant=reject_constant)
        # Python's JSON decoder accepts overflowing exponents as infinity. Re-encoding with
        # allow_nan=False rejects those values anywhere in the nested evidence block.
        json.dumps(evidence, allow_nan=False)
    except (TypeError, UnicodeDecodeError, json.JSONDecodeError, ValueError) as error:
        raise ValueError("Lifecycle evidence is not finite valid JSON: {}".format(error)) from error
    if not isinstance(evidence, dict):
        raise ValueError("Lifecycle evidence must be a JSON object")
    return evidence


def _current_lifecycle_envelope(evidence, cycle_id):
    """Return the unique current-cycle envelope or fail closed."""
    if not _text(cycle_id):
        raise ValueError("Lifecycle proposal requires a current cycle identifier")
    current_cycle = cycle_id.strip()
    if evidence.get("cycle_id") != current_cycle:
        raise ValueError("Lifecycle evidence is not bound to cycle {}".format(current_cycle))
    periods = evidence.get("periods")
    current_periods = ([period for period in periods
                        if isinstance(period, dict) and period.get("period_id") == current_cycle]
                       if isinstance(periods, list) else [])
    if len(current_periods) != 1:
        raise ValueError("Lifecycle evidence must contain exactly one current-cycle period")
    envelope = current_periods[0].get("envelope_evidence")
    if not isinstance(envelope, dict) or envelope.get("cycle_id") != current_cycle:
        raise ValueError("Current lifecycle period has no cycle-bound accepted envelope")
    return envelope


def proposal_from_lifecycle_json(evidence_json, cycle_id, title, objective_kpi,
                                 baseline_value, expected_gain, predicted,
                                 category="operational"):
    """Build one guard-ready proposal from exact Java lifecycle JSON.

    The JSON is expected to come from
    ``ProcessModelOperatingEnvelopeLifecycleStudy.toEvidenceJson(cycle_id)``. This adapter does
    not repair, relabel, or partially accept evidence. It selects the unique current-cycle period,
    derives setpoints and the accepted envelope from that exact block, then replays the existing
    optimizer, envelope, and lifecycle validators before returning a proposal to a task-local
    ``scripts.optimize`` stage.
    """
    current_cycle = cycle_id.strip() if _text(cycle_id) else cycle_id
    if not _text(title) or not _text(category) or not _text(objective_kpi):
        raise ValueError("Lifecycle proposal requires title, category, and objective KPI")
    if not _number(baseline_value) or not _number(expected_gain):
        raise ValueError("Lifecycle proposal baseline and expected gain must be finite")
    if (not isinstance(predicted, dict)
            or any(not _text(key) or not _number(value) for key, value in predicted.items())):
        raise ValueError("Lifecycle proposal predictions must be a finite KPI mapping")

    evidence = _finite_json_object(evidence_json)
    envelope = _current_lifecycle_envelope(evidence, current_cycle)
    action_ids = envelope.get("accepted_action_ids")
    values = envelope.get("accepted_candidate_values")
    if (not isinstance(action_ids, list) or not action_ids
            or len(set(action_ids)) != len(action_ids)
            or any(not _text(action_id) for action_id in action_ids)
            or not isinstance(values, list) or len(values) != len(action_ids)
            or any(not _number(value) for value in values)):
        raise ValueError("Current lifecycle envelope has invalid accepted action values")

    proposal = {
        "title": title,
        "category": category,
        "setpoints": dict(zip(action_ids, values)),
        "expected_gain": {"value": float(expected_gain), "kpi": objective_kpi},
        "predicted": dict(predicted),
        "objective_kpi": objective_kpi,
        "baseline_value": float(baseline_value),
        "optimizer_evidence": envelope.get("optimizer_evidence"),
        "accepted_point_envelope_evidence": envelope,
        "lifecycle_envelope_evidence": evidence,
    }
    validation_context = SimpleNamespace(cycle_id=current_cycle)
    reasons = _optimizer_evidence_reasons(validation_context, proposal)
    reasons.extend(_envelope_evidence_reasons(validation_context, proposal, required=True))
    reasons.extend(_lifecycle_evidence_reasons(validation_context, proposal, required=True))
    if reasons:
        raise ValueError("Lifecycle evidence failed proposal validation: {}".format(
            ", ".join(sorted(set(reasons)))))
    return proposal


def proposal_from_lifecycle_context(evidence_json, ctx, title, predicted=None,
                                    category="operational"):
    """Build a lifecycle proposal from exact optimizer and reviewed task metadata.

    The selected objective name, direction, unit, initial value, and accepted value come from
    the current Java optimizer evidence. The public KPI identity and reviewed baseline come from
    the task's ``goal.yaml`` and baseline snapshot. Any identity, unit, direction, or baseline
    mismatch fails before a proposal is returned.
    """
    evidence = _finite_json_object(evidence_json)
    envelope = _current_lifecycle_envelope(evidence, getattr(ctx, "cycle_id", None))
    optimizer = envelope.get("optimizer_evidence")
    objective = (getattr(ctx, "goal", {}) or {}).get("objective") or {}
    baseline = (getattr(ctx, "baseline", {}) or {}).get("kpis") or {}
    metric = objective.get("metric")
    source_name = objective.get("source_name")
    direction = objective.get("direction")
    unit = objective.get("unit")
    if not all(_text(value) for value in (metric, source_name, direction, unit)):
        raise ValueError(
            "Task objective requires metric, source_name, direction, and unit metadata")
    if direction not in ("maximize", "minimize"):
        raise ValueError("Task objective direction must be maximize or minimize")
    if not isinstance(optimizer, dict):
        raise ValueError("Lifecycle optimizer objective evidence is missing")
    if optimizer.get("objective_name") != source_name:
        raise ValueError("Task objective source name does not match lifecycle evidence")
    if str(optimizer.get("objective_direction", "")).lower() != direction:
        raise ValueError("Task objective direction does not match lifecycle evidence")
    if optimizer.get("objective_unit") != unit:
        raise ValueError("Task objective unit does not match lifecycle evidence")
    initial = optimizer.get("initial_objective_value")
    accepted = optimizer.get("accepted_objective_value")
    task_baseline = baseline.get(metric)
    if not all(_number(value) for value in (initial, accepted, task_baseline)):
        raise ValueError("Lifecycle and task objective values must be finite")
    if float(initial) != float(task_baseline):
        raise ValueError("Reviewed task baseline does not match lifecycle search baseline")
    gain = (float(accepted) - float(initial)) * (1.0 if direction == "maximize" else -1.0)
    predictions = dict(predicted or {})
    if metric in predictions and (not _number(predictions[metric])
                                  or float(predictions[metric]) != float(accepted)):
        raise ValueError("Task objective prediction does not match lifecycle accepted value")
    predictions[metric] = float(accepted)
    return proposal_from_lifecycle_json(
        evidence_json,
        cycle_id=ctx.cycle_id,
        title=title,
        objective_kpi=metric,
        baseline_value=float(task_baseline),
        expected_gain=gain,
        predicted=predictions,
        category=category,
    )


def _lifecycle_evidence_reasons(ctx, proposal, required=False):
    """Validate persisted lifecycle envelopes and bind the current period to the proposal."""
    if "lifecycle_envelope_evidence" not in proposal:
        return ["missing_lifecycle_envelope_evidence"] if required else []
    evidence = proposal.get("lifecycle_envelope_evidence")
    if not isinstance(evidence, dict):
        return ["invalid_lifecycle_envelope_evidence"]

    reasons = []
    schema_version = evidence.get("schema_version")
    if schema_version not in (1, 2, 3, 4, 5, 6, 7, 8, 9):
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
    state_metric_ids = None
    if (schema_version == 3
            or (schema_version in (4, 5, 6, 7, 8, 9) and "accepted_state_metric_ids" in evidence)):
        state_metric_ids = _action_ids(evidence.get("accepted_state_metric_ids"))
        if state_metric_ids is None:
            reasons.append("invalid_lifecycle_state_metric_ids")
            state_metric_ids = []
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
    state_metric_units = None
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
        if state_metric_ids is not None:
            metrics = row.get("accepted_state_metrics")
            valid_metrics = (isinstance(metrics, list)
                             and len(metrics) == len(state_metric_ids))
            metric_units = []
            if valid_metrics:
                for metric_id, metric in zip(state_metric_ids, metrics):
                    if (not isinstance(metric, dict)
                            or metric.get("metric_id") != metric_id
                            or not _text(metric.get("metric_name"))
                            or not _number(metric.get("value"))
                            or not _text(metric.get("unit"))
                            or not _text(metric.get("provenance"))):
                        valid_metrics = False
                        break
                    metric_units.append(metric.get("unit"))
            if not valid_metrics:
                reasons.append(prefix + "_invalid_accepted_state_metrics")
            elif state_metric_units is None:
                state_metric_units = metric_units
            elif state_metric_units != metric_units:
                reasons.append(prefix + "_accepted_state_metric_unit_mismatch")

        envelope = row.get("envelope_evidence")
        if not isinstance(envelope, dict):
            reasons.append(prefix + "_invalid_envelope")
            continue
        if (row.get("envelope_study_id") != envelope.get("study_id")
                or row.get("candidate_evaluator_id") != envelope.get("candidate_evaluator_id")):
            reasons.append(prefix + "_identity_mismatch")
        if envelope.get("accepted_action_ids") != action_ids:
            reasons.append(prefix + "_action_ids_mismatch")
        if schema_version in (4, 5, 6, 7, 8, 9):
            slices = envelope.get("slices")
            if (not isinstance(slices, list)
                    or any(not isinstance(envelope_slice, dict)
                           or "axis_action_semantics" not in envelope_slice
                           or "operating_region_transitions" not in envelope_slice
                           for envelope_slice in slices)):
                reasons.append(prefix + "_missing_operating_region_transition_evidence")
            if (schema_version in (5, 6, 7, 8, 9)
                    and (not isinstance(slices, list)
                         or any(not isinstance(envelope_slice, dict)
                                or "operating_region_boundary_refinements" not in envelope_slice
                                or "maximum_boundary_refinement_evaluations" not in envelope_slice
                                for envelope_slice in slices))):
                reasons.append(prefix + "_missing_operating_region_boundary_refinement_evidence")
            if schema_version in (6, 7, 8, 9) and not _has_boundary_constraint_headroom(slices):
                reasons.append(prefix + "_missing_boundary_constraint_headroom_evidence")
            if (schema_version in (7, 8, 9)
                    and (envelope.get("schema_version") != 2
                         or "cross_slice_constraint_summary" not in envelope)):
                reasons.append(prefix + "_missing_cross_slice_constraint_summary")
        if schema_version == 9:
            reasons.extend(prefix + "_" + reason
                           for reason in _utilization_coverage_reasons(
                               row.get("utilization_coverage")))

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
    if schema_version in (2, 3, 4, 5, 6, 7, 8, 9):
        expected_period_changes = _period_change_diagnostics(
            periods, action_ids, evidence.get("time_unit"), state_metric_ids,
            require_operating_transitions=schema_version in (4, 5, 6, 7, 8, 9),
            require_boundary_refinements=schema_version in (5, 6, 7, 8, 9),
            require_constraint_summary_identities=schema_version in (8, 9))
        if (expected_period_changes is None
                or evidence.get("period_change_diagnostics") != expected_period_changes):
            reasons.append("lifecycle_period_change_diagnostics_mismatch")
    if evidence.get("advisory") is not True:
        reasons.append("lifecycle_envelope_not_advisory")
    return reasons


def _utilization_coverage_reasons(coverage):
    """Validate one complete declared-scope coverage block without trusting its summary flag."""
    if not isinstance(coverage, dict):
        return ["missing_utilization_coverage"]
    reasons = []
    if (coverage.get("schema_version") != "1.0"
            or coverage.get("coverage_scope") != "DECLARED_EQUIPMENT_AND_CONSTRAINTS"
            or not _text(coverage.get("model_name"))
            or not isinstance(coverage.get("registry_identity_digest"), str)):
        reasons.append("invalid_utilization_coverage_identity")
    expected_equipment = coverage.get("expected_equipment_ids")
    expected_constraints = coverage.get("expected_constraint_ids")
    required_constraints = coverage.get("required_constraint_ids")
    if (not isinstance(expected_equipment, list) or not expected_equipment
            or any(not _text(value) for value in expected_equipment)
            or len(set(expected_equipment)) != len(expected_equipment)):
        reasons.append("invalid_utilization_coverage_equipment_scope")
        expected_equipment = []
    if (not isinstance(expected_constraints, list) or not expected_constraints
            or any(not _text(value) for value in expected_constraints)
            or len(set(expected_constraints)) != len(expected_constraints)):
        reasons.append("invalid_utilization_coverage_constraint_scope")
        expected_constraints = []
    if (not isinstance(required_constraints, list)
            or any(not _text(value) for value in required_constraints)
            or len(set(required_constraints)) != len(required_constraints)):
        reasons.append("invalid_utilization_coverage_required_scope")
        required_constraints = []
    rows = coverage.get("rows")
    seen = set()
    seen_equipment = set()
    if not isinstance(rows, list) or not rows:
        reasons.append("invalid_utilization_coverage_rows")
        rows = []
    for row in rows:
        if not isinstance(row, dict):
            reasons.append("invalid_utilization_coverage_row")
            continue
        identity = row.get("qualified_constraint_id")
        statuses = row.get("statuses")
        available = statuses == ["AVAILABLE"]
        disabled = statuses == ["DISABLED"]
        if (not _text(identity) or identity in seen
                or not _text(row.get("equipment_id"))
                or not _text(row.get("constraint_name"))
                or not isinstance(row.get("enabled"), bool)
                or row.get("origin") not in ("DIRECT", "STRATEGY", "UNKNOWN")
                or not isinstance(row.get("minimum_constraint"), bool)
                or not identity.startswith(row.get("equipment_id", "") + "#")
                or not isinstance(statuses, list)
                or not (available or disabled)):
            reasons.append("invalid_utilization_coverage_row")
            continue
        seen.add(identity)
        seen_equipment.add(row["equipment_id"])
        if available and (row.get("enabled") is not True
                          or not _text(row.get("unit"))
                          or not _text(row.get("basis"))
                          or not _text(row.get("provenance"))
                          or not _number(row.get("current_value"))
                          or not _number(row.get("applicable_limit"))
                          or row.get("applicable_limit") <= 0
                          or not _number(row.get("normalized_utilization"))):
            reasons.append("invalid_available_utilization_coverage_row")
        if disabled and (row.get("enabled") is not False
                         or row.get("current_value") is not None
                         or row.get("normalized_utilization") is not None):
            reasons.append("invalid_disabled_utilization_coverage_row")
    if (set(expected_constraints) - seen
            or set(required_constraints) != seen
            or set(expected_equipment) != seen_equipment):
        reasons.append("utilization_coverage_scope_mismatch")
    if coverage.get("complete") is not True or coverage.get("diagnostics") != []:
        reasons.append("incomplete_utilization_coverage")
    return sorted(set(reasons))


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
        reasons.extend(_objective_evidence_reasons(
            ctx, proposal, required=settings.get("require_objective_evidence") is True))
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
