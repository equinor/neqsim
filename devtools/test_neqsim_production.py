"""Tests for the production-optimisation stages (gates, constraints, guard, outcome)."""
import json
import os
import pathlib

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
    base = {"title": "%s", "category": "operational", "setpoints": {"p3_barg": 0.5},
            "expected_gain": {"value": 300.0, "kpi": "oil_Sm3d"}}
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
    titles = [v["title"] for v in Ledger(os.path.join(task, "continuous", "ledger", "events.jsonl")).current().values()]
    assert "safe" in titles and "off spec" not in titles


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
