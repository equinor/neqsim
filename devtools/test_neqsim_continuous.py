"""Tests for the continuous task-solving foundation (stop rules, ledger, watermarks, plugins)."""
from datetime import datetime, timedelta, timezone

import pytest

import neqsim_continuous as nc
from neqsim_continuous import contracts

GOAL = {
    "objective": {"metric": "uplift_MSm3d", "direction": "maximize", "target": 3.0,
                  "confidence_required": "medium"},
    "stop": {"converged": {"window": 3, "relative": 0.02, "absolute": 0.05},
             "budget": {"iterations": 12, "agent_sessions": 6},
             "expected_improvement_cost": 0.05},
    "reopen": {"regress_margin": 0.3, "on": ["new_evidence", "brief_changed"]},
}


def _history(values, validated=True, confidence="medium"):
    return [{"value": v, "validated": validated, "confidence": confidence} for v in values]


def test_goal_met_requires_validation_and_confidence():
    assert nc.evaluate(GOAL, _history([1.0, 3.2]), candidates=[]).state == "goal_met"
    assert nc.evaluate(GOAL, _history([3.2], validated=False), candidates=None).state == "solving"
    assert nc.evaluate(GOAL, _history([3.2], confidence="low"), candidates=None).state == "solving"


def test_minimize_direction():
    goal = {"objective": {"direction": "minimize", "target": 0.3}}
    assert nc.evaluate(goal, _history([0.5, 0.25]), candidates=None).state == "goal_met"


def test_infeasible_when_reachable_bound_is_short():
    decision = nc.evaluate(GOAL, _history([1.0]), candidates=[], upper_bound=2.5)
    assert decision.state == "infeasible" and decision.stop


def test_budget_exhausted():
    decision = nc.evaluate(GOAL, _history([1.0]), candidates=None, usage={"iterations": 12})
    assert decision.state == "budget_exhausted"


def test_converged_needs_small_gains_and_no_promising_action():
    flat = _history([2.0, 2.4, 2.42, 2.43, 2.44])
    promising = [{"action": "widen search", "p_success": 0.5, "predicted_delta": 0.6}]
    weak = [{"action": "more data", "p_success": 0.2, "predicted_delta": 0.1}]
    assert nc.evaluate(GOAL, flat, candidates=promising).state == "solving"
    decision = nc.evaluate(GOAL, flat, candidates=weak)
    assert decision.state == "converged"
    assert max(decision.details["recent_gains"]) < decision.details["tolerance"]
    # A recent large gain means it is not converged even with no promising action.
    assert nc.evaluate(GOAL, _history([2.0, 2.01, 2.02, 2.6]), candidates=weak).state == "solving"


def test_blocked_and_next_action():
    needs_person = [{"action": "ask arrival-pressure owner", "needs_person": True,
                     "p_success": 0.8, "predicted_delta": 1.8}]
    decision = nc.evaluate(GOAL, _history([1.0]), candidates=needs_person,
                           blockers=["arrival pressure limit"])
    assert decision.state == "blocked"
    options = needs_person + [{"action": "refit wells", "p_success": 0.6, "predicted_delta": 0.5}]
    decision = nc.evaluate(GOAL, _history([1.0]), candidates=options, blockers=["x"])
    assert decision.state == "solving" and decision.details["next_action"] == "refit wells"


def test_should_reopen():
    assert nc.should_reopen(GOAL, current_value=2.6, promoted_value=3.1) == ["goal_regressed"]
    assert nc.should_reopen(GOAL, 3.0, 3.1, events=["new_evidence", "unrelated"]) == ["new_evidence"]


def test_ledger_lifecycle_and_merge(tmp_path):
    ledger = nc.Ledger(tmp_path / "laptop" / "events.jsonl")
    first = ledger.create("Load trains to 98 % power", "operational",
                          value={"p50": 0.41, "unit": "MSm3/d"})
    assert first["id"] == "OPP-0001" and ledger.next_id() == "OPP-0002"
    ledger.set_status("OPP-0001", "accepted", by="reviewer")
    with pytest.raises(nc.LedgerError):
        ledger.set_status("OPP-0001", "verified", by="reviewer")
    with pytest.raises(nc.LedgerError):
        ledger.create("bad", "wishful")
    ledger.update_value("OPP-0001", {"p50": 0.38}, cycle="c2")
    assert ledger.current()["OPP-0001"]["status"] == "accepted"
    assert ledger.current()["OPP-0001"]["value"] == {"p50": 0.38}

    server = nc.Ledger(tmp_path / "server" / "events.jsonl")
    for event in ledger.events():
        server._append(dict(event))
    server.create("Wash train B", "maintenance", id="OPP-0100")
    assert ledger.merge(server.path) == 1
    assert ledger.merge(server.path) == 0
    assert set(ledger.current()) == {"OPP-0001", "OPP-0100"}


def test_watermarks_only_move_forward(tmp_path):
    marks = nc.Watermarks(tmp_path / "watermarks.json")
    assert not marks.advance("historian", nc.SourceResult("stale", watermark="2026-09-25T05:00:00Z"))
    assert marks.advance("historian", nc.SourceResult("ok", watermark="2026-09-25T05:00:00Z"))
    assert not marks.advance("historian", nc.SourceResult("ok", watermark="2026-09-24T05:00:00Z"))
    since, _ = nc.Watermarks(marks.path).window("historian", until=None, overlap=timedelta(hours=2))
    assert since == datetime(2026, 9, 25, 3, 0, tzinfo=timezone.utc)


def test_file_adapter_pulls_window(tmp_path):
    drop = tmp_path / "drop"
    drop.mkdir()
    (drop / "export.csv").write_text(
        "timestamp,power_MW\n2026-09-24T23:00:00+00:00,41.0\n"
        "2026-09-25T01:00:00+00:00,42.0\n2026-09-25T06:00:00+00:00,43.0\n", encoding="utf-8")
    adapter = nc.resolve("adapters", "file")(str(drop))
    since = datetime(2026, 9, 25, 0, 0, tzinfo=timezone.utc)
    until = datetime(2026, 9, 25, 5, 0, tzinfo=timezone.utc)
    result = adapter.pull(since, until, str(tmp_path / "store"))
    assert result.status == "ok" and result.rows == 1
    assert result.watermark.startswith("2026-09-25T01:00:00")
    assert adapter.pull(since, until, str(tmp_path / "store2")).rows == 1
    assert nc.FileDropAdapter(str(tmp_path / "empty")).pull(since, until, "x").status == "stale"


def test_plugin_registry_missing_plugin_is_not_an_error():
    assert "file" in nc.available("adapters")
    assert nc.resolve("adapters", "enterprise-only-adapter") is None
    nc.register("notifiers", "test-notifier", lambda: "ok")
    assert nc.resolve("notifiers", "test-notifier")() == "ok"
    with pytest.raises(ValueError):
        nc.resolve("widgets", "x")
    with pytest.raises(ValueError):
        contracts.StageResult("replay", "maybe")
    # Dotted paths let a pip-installed plugin work without entry-point metadata.
    assert nc.resolve("adapters", "neqsim_continuous.adapters:FileDropAdapter") is nc.FileDropAdapter
    assert nc.resolve("adapters", "no_such_package.module:Adapter") is None
