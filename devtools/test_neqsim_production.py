"""Tests for the production-optimisation stages (gates, constraints, guard, outcome)."""
import copy
import json
import math
import os
import pathlib
from types import SimpleNamespace

import pytest
import yaml

import neqsim_continuous as nc
from neqsim_continuous import production
from neqsim_continuous.cycle import run_cycle
from neqsim_continuous.ledger import Ledger
from neqsim_continuous.living import make_living

RVP = {"name": "rvp", "kpi": "rvp_bara", "op": "<=", "limit": 0.70, "margin": 0.05, "warn": 0.03, "hard": True}


def test_slack_uses_margin_and_direction():
    assert production.slack(RVP, 0.60) == pytest.approx(0.05)
    assert production.slack(RVP, 0.66) < 0
    floor = {"kpi": "x", "op": ">=", "limit": 10.0, "margin": 1.0}
    assert production.slack(floor, 12.0) == pytest.approx(1.0)
    assert production.slack({"kpi": "x", "limit": None}, 1.0) is None


def test_evaluate_constraints_statuses():
    rows = {r["name"]: r for r in production.evaluate_constraints(
        [RVP, dict(RVP, name="a", limit=None), dict(RVP, name="b", kpi="missing")], {"rvp_bara": 0.63})}
    assert rows["rvp"]["status"] == "low_margin"
    assert rows["a"]["status"] == "unconfirmed" and rows["b"]["status"] == "no_data"
    assert production.evaluate_constraints([RVP], {"rvp_bara": 0.7})[0]["status"] == "breach"


def test_demonstrated_limit_is_larger_of_design_and_quantile():
    values = list(range(1, 101))
    result = nc.demonstrated_limit(values, quantile=0.99, design=95.0)
    assert result["demonstrated"] == pytest.approx(99.01) and result["limit"] == pytest.approx(99.01)
    assert result["basis"] == "demonstrated"
    assert nc.demonstrated_limit(values, quantile=0.5, design=95.0)["limit"] == 95.0
    few = nc.demonstrated_limit([1.0, 2.0], design=5.0)
    assert few["limit"] == 5.0 and "too few" in few["basis"]


STAGE_UPDATE = '''
def run(ctx):
    return {"kpis": {"rvp_bara": 0.60, "oil_Sm3d": 13000.0, "resid_K": ctx.plan["_resid"]}}
'''

STAGE_OPTIMIZE = '''
def run(ctx):
    optimizer_evidence = {
        "cycle_id": ctx.cycle_id,
        "simulation_converged": True,
        "candidate_feasible": True,
        "candidate_finite": True,
        "constraint_evidence_complete": True,
        "state_restore_complete": True,
        "accepted_point_replayed": True,
        "actions_complete": True,
        "evaluation_count": 12,
        "runtime_seconds": 1.25,
    }
    point = {
        "candidate_values": [0.5],
        "baseline_restored": True,
        "baseline_simulation_converged": True,
    }
    envelope_evidence = {
        "schema_version": 1,
        "cycle_id": ctx.cycle_id,
        "study_id": "field-envelope",
        "candidate_evaluator_id": "field-actions",
        "optimizer_evidence": optimizer_evidence,
        "accepted_action_ids": ["p3_barg"],
        "accepted_candidate_values": [0.5],
        "slice_count": 1,
        "requested_point_count": 1,
        "completed_point_count": 1,
        "envelope_evaluation_count": 1,
        "envelope_runtime_seconds": 0.1,
        "total_evaluation_count": 13,
        "slices": [{
            "study_id": "field-envelope",
            "candidate_evaluator_id": "field-actions",
            "axis_action_ids": ["p3_barg"],
            "anchor_values": [0.5],
            "requested_point_count": 1,
            "completed_point_count": 1,
            "evaluation_count": 1,
            "runtime_seconds": 0.1,
            "outcome": "COMPLETE",
            "complete": True,
            "points": [point],
        }],
        "advisory": True,
    }
    base = {"title": "%s", "category": "operational", "setpoints": {"p3_barg": 0.5},
            "expected_gain": {"value": 300.0, "kpi": "oil_Sm3d"},
            "objective_kpi": "oil_Sm3d", "baseline_value": 12700.0,
            "optimizer_evidence": optimizer_evidence,
            "accepted_point_envelope_evidence": envelope_evidence}
    return {"proposals": [
        dict(base, title="safe", predicted={"rvp_bara": 0.63}),
        dict(base, title="off spec", predicted={"rvp_bara": 0.68}),
        dict(base, title="no prediction"),
        dict(base, title="no gain", expected_gain={"value": 0.0}, predicted={"rvp_bara": 0.6}),
    ]}
'''


def _task(tmp_path, resid=1.0, limit=0.70):
    task = tmp_path / "t"
    (task / "continuous" / "stages").mkdir(parents=True)
    make_living(str(task), template="production")
    cont = task / "continuous"
    (cont / "stages" / "model_update.py").write_text(STAGE_UPDATE.replace('ctx.plan["_resid"]', str(resid)))
    (cont / "stages" / "optimize.py").write_text(STAGE_OPTIMIZE)
    plan = yaml.safe_load((cont / "cycle_plan.yaml").read_text())
    plan["gates"] = [{"name": "resid", "kpi": "resid_K", "abs_max": 3.0}]
    plan["production"]["require_envelope_evidence"] = True
    plan["stages"] = [s for s in plan["stages"] if s not in ("sense", "refresh", "agent")]
    (cont / "cycle_plan.yaml").write_text(yaml.safe_dump(plan))
    goal = yaml.safe_load((cont / "goal.yaml").read_text())
    goal["objective"] = {"metric": "oil_Sm3d", "direction": "maximize", "target": None}
    goal["constraints"] = [dict(RVP, limit=limit)]
    (cont / "goal.yaml").write_text(yaml.safe_dump(goal))
    return str(task)


def _guard(task, manifest):
    path = os.path.join(task, "continuous", "cycles", manifest["cycle_id"], "guard.json")
    with open(path) as f:
        return json.load(f)


def _guard_context(tmp_path, proposal):
    return SimpleNamespace(
        cycle_id="C-1",
        cycle_dir=str(tmp_path),
        goal={"constraints": [RVP]},
        guard_blocks=[],
        plan={"production": {"min_gain": 0.0}},
        proposals=[proposal],
        sections=[],
    )


def _valid_proposal():
    proposal = {
        "title": "candidate",
        "setpoints": {"p3_barg": 0.5},
        "expected_gain": {"value": 300.0, "kpi": "oil_Sm3d"},
        "predicted": {"rvp_bara": 0.63},
        "objective_kpi": "oil_Sm3d",
        "baseline_value": 12700.0,
        "optimizer_evidence": {
            "cycle_id": "C-1",
            "simulation_converged": True,
            "candidate_feasible": True,
            "candidate_finite": True,
            "constraint_evidence_complete": True,
            "state_restore_complete": True,
            "accepted_point_replayed": True,
            "actions_complete": True,
            "evaluation_count": 12,
            "runtime_seconds": 1.25,
        },
    }
    proposal["accepted_point_envelope_evidence"] = _valid_envelope_evidence(
        proposal["optimizer_evidence"])
    return proposal


def _valid_envelope_evidence(optimizer_evidence):
    point = {
        "candidate_values": [0.5],
        "baseline_restored": True,
        "baseline_simulation_converged": True,
    }
    return {
        "schema_version": 1,
        "cycle_id": "C-1",
        "study_id": "field-envelope",
        "candidate_evaluator_id": "field-actions",
        "optimizer_evidence": optimizer_evidence,
        "accepted_action_ids": ["p3_barg"],
        "accepted_candidate_values": [0.5],
        "slice_count": 1,
        "requested_point_count": 1,
        "completed_point_count": 1,
        "envelope_evaluation_count": 1,
        "envelope_runtime_seconds": 0.1,
        "total_evaluation_count": 13,
        "slices": [{
            "study_id": "field-envelope",
            "candidate_evaluator_id": "field-actions",
            "axis_action_ids": ["p3_barg"],
            "anchor_values": [0.5],
            "requested_point_count": 1,
            "completed_point_count": 1,
            "evaluation_count": 1,
            "runtime_seconds": 0.1,
            "outcome": "COMPLETE",
            "complete": True,
            "points": [point],
        }],
        "advisory": True,
    }


def test_cycle_keeps_only_safe_proposals(tmp_path):
    task = _task(tmp_path)
    manifest = run_cycle(task, no_agent=True)
    guard = _guard(task, manifest)
    assert guard["accepted"] == ["safe"]
    reasons = {w["title"]: w["reasons"] for w in guard["withheld"]}
    assert reasons["off spec"] == ["violates:rvp"]
    assert reasons["no prediction"] == ["no_prediction:rvp"]
    assert reasons["no gain"] == ["gain_not_above_0.0"]
    assert "proposals_withheld" in manifest["triggers"]
    items = list(Ledger(os.path.join(task, "continuous", "ledger", "events.jsonl")).current().values())
    titles = [v["title"] for v in items]
    assert "safe" in titles and "off spec" not in titles
    safe = next(item for item in items if item["title"] == "safe")
    assert safe["optimizer_evidence"]["cycle_id"] == manifest["cycle_id"]
    assert safe["optimizer_evidence"]["accepted_point_replayed"] is True
    envelope = safe["accepted_point_envelope_evidence"]
    assert envelope["cycle_id"] == manifest["cycle_id"]
    assert envelope["accepted_action_ids"] == ["p3_barg"]
    assert envelope["accepted_candidate_values"] == [0.5]
    assert envelope["total_evaluation_count"] == 13


@pytest.mark.parametrize("field, reason", [
    ("simulation_converged", "simulation_not_converged"),
    ("candidate_feasible", "infeasible_candidate"),
    ("candidate_finite", "non_finite_candidate"),
    ("constraint_evidence_complete", "constraint_evidence_incomplete"),
    ("state_restore_complete", "state_restore_incomplete"),
    ("accepted_point_replayed", "accepted_point_not_replayed"),
    ("actions_complete", "partial_action_application"),
])
def test_guard_rejects_incomplete_optimizer_evidence(tmp_path, field, reason):
    proposal = _valid_proposal()
    proposal["optimizer_evidence"][field] = False
    ctx = _guard_context(tmp_path, proposal)
    production.guard(ctx, {})
    assert ctx.proposals == []
    assert json.loads((tmp_path / "guard.json").read_text())["withheld"][0]["reasons"] == [reason]


def test_guard_rejects_missing_and_stale_optimizer_evidence(tmp_path):
    missing = _valid_proposal()
    missing.pop("optimizer_evidence")
    missing.pop("accepted_point_envelope_evidence")
    stale = _valid_proposal()
    stale["title"] = "stale"
    stale["optimizer_evidence"]["cycle_id"] = "C-0"
    ctx = _guard_context(tmp_path, missing)
    ctx.proposals.append(stale)
    production.guard(ctx, {})
    reasons = {row["title"]: row["reasons"]
               for row in json.loads((tmp_path / "guard.json").read_text())["withheld"]}
    assert reasons == {"candidate": ["missing_optimizer_evidence"], "stale": ["stale_candidate"]}


def test_guard_keeps_optimizer_only_proposal_when_envelope_is_optional(tmp_path):
    proposal = _valid_proposal()
    proposal.pop("accepted_point_envelope_evidence")
    ctx = _guard_context(tmp_path, proposal)
    production.guard(ctx, {})
    assert [row["title"] for row in ctx.proposals] == ["candidate"]


def test_guard_requires_envelope_when_configured(tmp_path):
    proposal = _valid_proposal()
    proposal.pop("accepted_point_envelope_evidence")
    ctx = _guard_context(tmp_path, proposal)
    ctx.plan["production"]["require_envelope_evidence"] = True
    production.guard(ctx, {})
    assert ctx.proposals == []
    assert json.loads((tmp_path / "guard.json").read_text())["withheld"][0]["reasons"] == [
        "missing_accepted_point_envelope_evidence"
    ]


def test_guard_rejects_stale_or_internally_inconsistent_envelope(tmp_path):
    variants = []

    stale = _valid_proposal()
    stale["title"] = "stale envelope"
    stale["accepted_point_envelope_evidence"]["cycle_id"] = "C-0"
    variants.append(stale)

    optimizer_mismatch = _valid_proposal()
    optimizer_mismatch["title"] = "optimizer mismatch"
    optimizer_mismatch["accepted_point_envelope_evidence"]["optimizer_evidence"] = dict(
        optimizer_mismatch["optimizer_evidence"], evaluation_count=11)
    variants.append(optimizer_mismatch)

    setpoint_mismatch = _valid_proposal()
    setpoint_mismatch["title"] = "setpoint mismatch"
    setpoint_mismatch["accepted_point_envelope_evidence"]["accepted_candidate_values"] = [0.6]
    variants.append(setpoint_mismatch)

    partial_slice = _valid_proposal()
    partial_slice["title"] = "partial slice"
    partial_slice["accepted_point_envelope_evidence"]["slices"][0]["complete"] = False
    variants.append(partial_slice)

    partial_recovery = _valid_proposal()
    partial_recovery["title"] = "partial recovery"
    partial_recovery["accepted_point_envelope_evidence"]["slices"][0]["points"][0][
        "baseline_restored"] = False
    variants.append(partial_recovery)

    count_mismatch = _valid_proposal()
    count_mismatch["title"] = "count mismatch"
    count_mismatch["accepted_point_envelope_evidence"]["completed_point_count"] = 2
    variants.append(count_mismatch)

    duplicate_axes = _valid_proposal()
    duplicate_axes["title"] = "duplicate axes"
    duplicate_slice = copy.deepcopy(duplicate_axes["accepted_point_envelope_evidence"]["slices"][0])
    duplicate_axes["accepted_point_envelope_evidence"].update({
        "slice_count": 2,
        "requested_point_count": 2,
        "completed_point_count": 2,
        "envelope_evaluation_count": 2,
        "envelope_runtime_seconds": 0.2,
        "total_evaluation_count": 14,
    })
    duplicate_axes["accepted_point_envelope_evidence"]["slices"].append(duplicate_slice)
    variants.append(duplicate_axes)

    ctx = _guard_context(tmp_path, variants[0])
    ctx.plan["production"]["require_envelope_evidence"] = True
    ctx.proposals.extend(variants[1:])
    production.guard(ctx, {})
    assert ctx.proposals == []
    reasons = {row["title"]: row["reasons"]
               for row in json.loads((tmp_path / "guard.json").read_text())["withheld"]}
    assert "stale_envelope_evidence" in reasons["stale envelope"]
    assert "envelope_optimizer_evidence_mismatch" in reasons["optimizer mismatch"]
    assert "envelope_setpoints_mismatch" in reasons["setpoint mismatch"]
    assert "envelope_slice_0_incomplete" in reasons["partial slice"]
    assert "envelope_slice_0_point_recovery_incomplete" in reasons["partial recovery"]
    assert "envelope_aggregate_count_mismatch" in reasons["count mismatch"]
    assert "duplicate_envelope_axes" in reasons["duplicate axes"]


def test_guard_fails_closed_for_malformed_envelope_types(tmp_path):
    malformed = _valid_proposal()
    malformed["title"] = "malformed envelope"
    malformed["accepted_point_envelope_evidence"] = []
    malformed_actions = _valid_proposal()
    malformed_actions["title"] = "malformed actions"
    malformed_actions["accepted_point_envelope_evidence"]["accepted_action_ids"] = 7
    malformed_optimizer = _valid_proposal()
    malformed_optimizer["title"] = "malformed optimizer"
    malformed_optimizer["optimizer_evidence"] = "not a mapping"

    ctx = _guard_context(tmp_path, malformed)
    ctx.plan["production"]["require_envelope_evidence"] = True
    ctx.proposals.extend([malformed_actions, malformed_optimizer])
    production.guard(ctx, {})
    assert ctx.proposals == []
    reasons = {row["title"]: row["reasons"]
               for row in json.loads((tmp_path / "guard.json").read_text())["withheld"]}
    assert reasons["malformed envelope"] == ["invalid_accepted_point_envelope_evidence"]
    assert "invalid_accepted_action_vector" in reasons["malformed actions"]
    assert "missing_optimizer_evidence" in reasons["malformed optimizer"]


@pytest.mark.parametrize("field, value, reason", [
    ("evaluation_count", 0, "invalid_evaluation_count"),
    ("evaluation_count", 1.5, "invalid_evaluation_count"),
    ("runtime_seconds", math.nan, "invalid_runtime_seconds"),
    ("runtime_seconds", -0.1, "invalid_runtime_seconds"),
])
def test_guard_rejects_invalid_performance_evidence(tmp_path, field, value, reason):
    proposal = _valid_proposal()
    proposal["optimizer_evidence"][field] = value
    ctx = _guard_context(tmp_path, proposal)
    production.guard(ctx, {})
    assert ctx.proposals == []
    assert reason in json.loads((tmp_path / "guard.json").read_text())["withheld"][0]["reasons"]


@pytest.mark.parametrize("field, value, reason", [
    ("setpoint", math.nan, "non_finite_setpoint:p3_barg"),
    ("setpoint", math.inf, "non_finite_setpoint:p3_barg"),
    ("prediction", math.nan, "non_finite_prediction:rvp_bara"),
    ("gain", math.inf, "gain_not_above_0.0"),
    ("baseline", -math.inf, "non_finite_baseline"),
])
def test_guard_rejects_non_finite_candidate_data(tmp_path, field, value, reason):
    proposal = _valid_proposal()
    if field == "setpoint":
        proposal["setpoints"]["p3_barg"] = value
    elif field == "prediction":
        proposal["predicted"]["rvp_bara"] = value
    elif field == "gain":
        proposal["expected_gain"]["value"] = value
    else:
        proposal["baseline_value"] = value
    ctx = _guard_context(tmp_path, proposal)
    production.guard(ctx, {})
    assert ctx.proposals == []
    assert reason in json.loads((tmp_path / "guard.json").read_text())["withheld"][0]["reasons"]


def test_guard_rejects_invalid_limit_and_minimum_gain(tmp_path):
    proposal = _valid_proposal()
    ctx = _guard_context(tmp_path, proposal)
    ctx.plan["production"]["min_gain"] = math.nan
    ctx.goal["constraints"][0] = dict(RVP, margin=math.inf)
    assert production.effective_limit(ctx.goal["constraints"][0]) is None
    production.guard(ctx, {})
    assert ctx.proposals == []
    assert json.loads((tmp_path / "guard.json").read_text())["withheld"][0]["reasons"] == [
        "invalid_min_gain"
    ]


def test_failed_gate_withholds_everything(tmp_path):
    task = _task(tmp_path, resid=5.0)
    manifest = run_cycle(task, no_agent=True)
    guard = _guard(task, manifest)
    assert guard["accepted"] == [] and "model_gate:resid" in guard["blocks"]
    assert all("model_gate:resid" in w["reasons"] for w in guard["withheld"])
    assert "model_gate:resid" in manifest["triggers"]


def test_unconfirmed_hard_constraint_withholds_advice(tmp_path):
    task = _task(tmp_path, limit=None)
    guard = _guard(task, run_cycle(task, no_agent=True))
    assert guard["accepted"] == [] and "constraint_unconfirmed:rvp" in guard["blocks"]


def test_breach_of_current_operation_is_a_trigger(tmp_path):
    task = _task(tmp_path, limit=0.60)
    manifest = run_cycle(task, no_agent=True)
    assert "constraint_breach:rvp" in manifest["triggers"]


def test_outcome_compares_realised_with_predicted_gain(tmp_path):
    task = _task(tmp_path)
    book = Ledger(os.path.join(task, "continuous", "ledger", "events.jsonl"))
    key = book.create("applied", "operational", expected_gain={"value": 400.0}, objective_kpi="oil_Sm3d", baseline_value=12700.0)["id"]
    book.set_status(key, "accepted", by="eng")
    book.set_status(key, "implemented", by="eng")
    manifest = run_cycle(task, no_agent=True)
    assert "outcome_confirmed:" + key in manifest["triggers"]  # 13000 - 12700 = 300 >= 50 % of 400


def test_user_input_effects_and_triggers(tmp_path):
    from neqsim_continuous import user_input

    task = pathlib.Path(_task(tmp_path, limit=None))
    plan_path = task / "continuous" / "cycle_plan.yaml"
    plan = yaml.safe_load(plan_path.read_text())
    plan["stages"].insert(0, "inputs")
    plan_path.write_text(yaml.safe_dump(plan))
    assert (task / "continuous" / "user_input.yaml").exists()

    # No limit set: every proposal is withheld by the unconfirmed hard constraint.
    first = run_cycle(str(task), now=None, no_agent=True)
    guard = json.loads((task / "continuous" / "cycles" / first["cycle_id"] / "guard.json").read_text())
    assert guard["accepted"] == [] and "constraint_unconfirmed:rvp" in guard["blocks"]

    # The engineer supplies the limit and a lever restriction; the next cycle acts on it.
    user_input.add(str(task), "Spec is 0.70 bara RVP per the lab, keep the 3rd stage as is.", by="eng",
                   effects=[{"kind": "constraint", "name": "rvp", "limit": 0.70, "margin": 0.05},
                            {"kind": "lever_freeze", "lever": "3rd stage"},
                            {"kind": "setting", "key": "production.rvp_bias_bara", "value": 0.02}])
    second = run_cycle(str(task), now=None, no_agent=True)
    cycle = task / "continuous" / "cycles" / second["cycle_id"]
    guard = json.loads((cycle / "guard.json").read_text())
    assert guard["accepted"] == ["safe"] and "user_input:U-001" in second["triggers"]
    applied = json.loads((cycle / "user_input.json").read_text())["applied"]["U-001"]
    assert set(applied) == {"constraint:rvp", "lever_freeze:3rd stage", "setting:production.rvp_bias_bara"}
    assert "User input in force" in (cycle / "digest.md").read_text()

    # Unchanged entry: no repeated trigger. Resolved entry: effects stop applying.
    third = run_cycle(str(task), now=None, no_agent=True)
    assert "user_input:U-001" not in third["triggers"]
    user_input.resolve(str(task), "U-001", by="eng")
    fourth = run_cycle(str(task), now=None, no_agent=True)
    guard = json.loads((task / "continuous" / "cycles" / fourth["cycle_id"] / "guard.json").read_text())
    assert guard["accepted"] == [] and "constraint_unconfirmed:rvp" in guard["blocks"]


def test_user_input_lever_limits_and_expiry(tmp_path):
    from datetime import datetime, timezone

    from neqsim_continuous import user_input

    class Ctx(object):
        user_levers = {}

    ctx = Ctx()
    ctx.user_levers = {"20b-va01": {"lo": 28.0, "hi": 30.0}, "vigdis hp wells": {"frozen": True}}
    assert nc.lever_limits(ctx, "20B-VA01", 27.0, 31.0) == (28.0, 30.0, False)
    assert nc.lever_limits(ctx, "Vigdis HP wells", -5, 10) == (-5, 10, True)
    assert nc.lever_limits(ctx, "other", 1, 2) == (1, 2, False)

    task = tmp_path / "x"
    (task / "continuous").mkdir(parents=True)
    entry = user_input.add(str(task), "temporary", by="eng", expires="2026-01-01")
    assert entry["id"] == "U-001" and user_input.add(str(task), "second")["id"] == "U-002"
    assert user_input._expired(entry, datetime(2026, 2, 1, tzinfo=timezone.utc).date())
    with pytest.raises(ValueError):
        user_input.add(str(task), "bad", effects=[{"kind": "nonsense"}])
