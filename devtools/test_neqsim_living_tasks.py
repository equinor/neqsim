"""End-to-end tests for living tasks: cycles, backtest, solve, promote, schedule, CLI.

They run on the public reference case (synthetic compressor station) and need neither Java
nor enterprise access.
"""
import json
import os
import shutil
from datetime import datetime, timezone

import pytest

import neqsim_continuous as nc
from neqsim_continuous import cli, schedule
from neqsim_continuous.cycle import list_cycles, run_cycle
from neqsim_continuous.drift import DriftMonitor
from neqsim_continuous.living import make_living, promote, read_state
from neqsim_continuous.reference_case import create_reference_task

import yaml


@pytest.fixture(scope="module")
def reference(tmp_path_factory):
    return create_reference_task(str(tmp_path_factory.mktemp("ref")))


def _copy(task, tmp_path, name):
    target = str(tmp_path / name)
    shutil.copytree(task, target)
    return target


def test_drift_monitor_detects_step_and_ignores_noise():
    import random
    rng = random.Random(1)
    monitor = DriftMonitor({"warmup": 30, "confirm": 2})
    alarms = [monitor.update("x", rng.gauss(10.0, 1.0))["new_alarm"] for _ in range(200)]
    assert not any(alarms)
    hits = [monitor.update("x", rng.gauss(14.0, 1.0))["new_alarm"] for _ in range(5)]
    assert any(hits)
    floored = DriftMonitor({"warmup": 30, "signals": {"x": {"min_sigma": 10.0}}})
    for _ in range(30):
        floored.update("x", rng.gauss(10.0, 1.0))
    assert not any(floored.update("x", rng.gauss(14.0, 1.0))["alarm"] for _ in range(20))


def test_make_living_never_overwrites(tmp_path):
    task = tmp_path / "old_task"
    task.mkdir()
    (task / "results.json").write_text(json.dumps(
        {"key_results": {"power_MW": 41.0}, "recommendations": ["Wash compressor B"]}), encoding="utf-8")
    (task / "study_config.yaml").write_text("study:\n  title: old\n", encoding="utf-8")
    before = (task / "results.json").read_text(encoding="utf-8")
    report = make_living(str(task))
    assert (task / "results.json").read_text(encoding="utf-8") == before
    assert os.path.exists(os.path.join(str(task), "continuous", "cycle_plan.yaml"))
    assert "continuous:" in (task / "study_config.yaml").read_text(encoding="utf-8")
    items = nc.Ledger(os.path.join(str(task), "continuous", "ledger", "events.jsonl")).current()
    assert [i["title"] for i in items.values()] == ["Wash compressor B"]
    assert report["created"]
    again = make_living(str(task))
    assert not again["created"]
    assert len(nc.Ledger(os.path.join(str(task), "continuous", "ledger", "events.jsonl")).current()) == 1


def test_monitor_cycle_resume_and_degraded(reference, tmp_path):
    task = _copy(reference, tmp_path, "cycle")
    now = datetime(2025, 11, 1, tzinfo=timezone.utc)
    first = run_cycle(task, now=now, no_agent=True)
    assert first["status"] == "complete" and not first["degraded"]
    kpis = json.load(open(os.path.join(task, "continuous", "cycles", first["cycle_id"], "kpis.json")))
    assert 0.79 < kpis["polytropic_efficiency"] < 0.81
    rerun = run_cycle(task, now=now, no_agent=True)
    assert rerun["cycle_id"].endswith("-r2")
    plan_path = os.path.join(task, "continuous", "cycle_plan.yaml")
    plan = yaml.safe_load(open(plan_path))
    plan["sources"]["historian"] = {"adapter": "enterprise-only-adapter"}
    yaml.safe_dump(plan, open(plan_path, "w"))
    degraded = run_cycle(task, now=datetime(2025, 11, 2, tzinfo=timezone.utc), no_agent=True)
    assert degraded["degraded"] and degraded["sources"]["historian"] == "not_installed"
    assert len(list_cycles(task)) == 3


def test_dry_run_leaves_state_unchanged(reference, tmp_path):
    task = _copy(reference, tmp_path, "dry")
    watermarks = os.path.join(task, "continuous", "watermarks.json")
    existed = os.path.exists(watermarks)
    run_cycle(task, now=datetime(2025, 11, 1, tzinfo=timezone.utc), dry_run=True, no_agent=True)
    assert os.path.exists(watermarks) == existed


def test_backtest_detects_injected_faults_without_false_alarms(reference, tmp_path):
    task = _copy(reference, tmp_path, "backtest")
    report = nc.run_backtest(task, datetime(2025, 10, 2, tzinfo=timezone.utc),
                             datetime(2026, 7, 16, tzinfo=timezone.utc))
    assert report["detected"] == 3, report["expected"]
    assert report["false_alarms"] == []
    delays = {e["trigger"]: e["delay_days"] for e in report["expected"]}
    assert delays["drift:flow_meter_mismatch_pct"] <= 5 and delays["drift:suction_pressure_bara"] <= 5
    assert delays["drift:polytropic_efficiency"] <= 45
    assert os.path.exists(os.path.join(task, "continuous", "backtests", "backtest", "backtest_report.md"))
    # The live task state is untouched by a backtest.
    assert list_cycles(task) == []


def test_backtest_is_reproducible(reference, tmp_path):
    task = _copy(reference, tmp_path, "repro")
    report = nc.run_backtest(task, datetime(2025, 10, 2, tzinfo=timezone.utc),
                             datetime(2025, 10, 12, tzinfo=timezone.utc), check_reproducibility=True)
    assert report["reproducibility"] == 1.0


def test_solve_until_goal_and_infeasible(reference, tmp_path):
    task = _copy(reference, tmp_path, "solve")
    state = nc.solve(task, until="goal", no_agent=True)
    assert state["state"] == "goal_met" and state["details"]["value"] >= 2.0
    assert read_state(task)["phase"] == "monitoring"

    hard = _copy(reference, tmp_path, "solve_hard")
    goal_path = os.path.join(hard, "continuous", "goal.yaml")
    goal = yaml.safe_load(open(goal_path))
    goal["objective"]["target"] = 5.0
    yaml.safe_dump(goal, open(goal_path, "w"))
    state = nc.solve(hard, until="goal", no_agent=True)
    assert state["state"] == "infeasible"


def test_living_report_follows_every_change(reference, tmp_path):
    task = _copy(reference, tmp_path, "report")
    report = os.path.join(task, "continuous", "LIVING_REPORT.md")
    assert os.path.exists(report), "task-living writes the first living report"
    run_cycle(task, now=datetime(2025, 11, 1, tzinfo=timezone.utc), no_agent=True)
    text = open(report, encoding="utf-8").read()
    assert "Latest values against the baseline" in text and "polytropic_efficiency" in text
    state = nc.solve(task, until="goal", no_agent=True)
    text = open(report, encoding="utf-8").read()
    assert "Goal progress" in text and "goal_met" in text and "task-promote" in text
    promote(task, state["history"][-1]["cycle"], "Reviewer", note="accepted")
    text = open(report, encoding="utf-8").read()
    assert "B-{} (current)".format(state["history"][-1]["cycle"]) in text and "Reviewer" in text
    assert "task-promote" not in text
    kpis = json.load(open(os.path.join(task, "continuous", "baseline", "kpis.json")))
    assert kpis["polytropic_efficiency"] == 0.8 and kpis["power_reduction_pct"] >= 2.0
    assert cli.main(["ledger", task, "set", "OPP-0001", "accepted", "--by", "Reviewer"]) == 0
    assert "| OPP-0001 | accepted |" in open(report, encoding="utf-8").read()
    os.remove(report)
    assert cli.main(["report", task]) == 0 and os.path.exists(report)


def test_living_report_never_breaks_a_cycle(reference, tmp_path, monkeypatch):
    from neqsim_continuous import living_report
    task = _copy(reference, tmp_path, "report_fail")
    monkeypatch.setattr(living_report, "build", lambda task_dir: 1 / 0)
    manifest = run_cycle(task, now=datetime(2025, 11, 1, tzinfo=timezone.utc), no_agent=True)
    assert manifest["status"] == "complete"


def test_living_report_regenerates_formal_report_when_configured(reference, tmp_path, monkeypatch):
    from neqsim_continuous import living_report
    task = _copy(reference, tmp_path, "report_formal")
    calls = []
    monkeypatch.setattr(living_report, "regenerate_formal", lambda task_dir: calls.append(task_dir))
    plan_path = os.path.join(task, "continuous", "cycle_plan.yaml")
    plan = yaml.safe_load(open(plan_path))
    plan["report"] = {"formal": "on_promote"}
    yaml.safe_dump(plan, open(plan_path, "w"))
    manifest = run_cycle(task, now=datetime(2025, 11, 1, tzinfo=timezone.utc), no_agent=True)
    assert calls == []
    promote(task, manifest["cycle_id"], "Reviewer")
    assert len(calls) == 1

def test_solve_refuses_unconfirmed_goal(reference, tmp_path):
    task = _copy(reference, tmp_path, "unconfirmed")
    goal_path = os.path.join(task, "continuous", "goal.yaml")
    goal = yaml.safe_load(open(goal_path))
    goal["confirmed_by"] = None
    yaml.safe_dump(goal, open(goal_path, "w"))
    with pytest.raises(ValueError, match="not confirmed"):
        nc.solve(task, no_agent=True)
    assert nc.solve(task, no_agent=True, allow_unconfirmed=True, reset=True)["state"] == "goal_met"


def test_promote_archives_previous_baseline(reference, tmp_path):
    task = _copy(reference, tmp_path, "promote")
    manifest = run_cycle(task, now=datetime(2025, 11, 1, tzinfo=timezone.utc), no_agent=True)
    result = promote(task, manifest["cycle_id"], reviewer="engineer", note="reviewed")
    assert result["id"] == "B-" + manifest["cycle_id"] and result["promoted_by"] == "engineer"
    history = os.path.join(task, "continuous", "baseline", "history")
    assert os.listdir(history)
    with pytest.raises(ValueError):
        promote(task, "no-such-cycle", reviewer="engineer")


def test_schedule_build_uses_shared_python(reference):
    spec = schedule.build(reference, daily="05:30")
    assert "task-cycle" in spec["command"] and "--mode monitor" in spec["command"]
    assert "--standard-first" in spec["command"]
    assert spec["windows"][spec["windows"].index("/ST") + 1] == "05:30"
    assert spec["cron"].startswith("30 5 * * *")


def test_cycle_standard_first_marks_degraded(reference, tmp_path, monkeypatch):
    from neqsim_continuous import standard_first
    task = _copy(reference, tmp_path, "standard_first")

    def fake_ensure(task_dir):
        return {
            "schema_version": "1.0",
            "ready": False,
            "readiness": "incomplete",
            "hard_missing": ["formal_report"],
            "checks": {"formal_report": False},
        }

    monkeypatch.setattr(standard_first, "ensure", fake_ensure)
    manifest = run_cycle(task, now=datetime(2025, 11, 1, tzinfo=timezone.utc),
                         no_agent=True, standard_first=True)
    assert manifest["degraded"]
    assert manifest["standard_first"]["hard_missing"] == ["formal_report"]
    assert "standard_first:incomplete" in manifest["triggers"]


def test_validator_checks_living_tasks_only(reference, tmp_path):
    from pathlib import Path
    from validate_task_results import check_continuous, find_results_files
    task = Path(_copy(reference, tmp_path, "validate"))
    assert check_continuous(task) == []
    assert check_continuous(tmp_path) == []  # ordinary folder: silent
    run_cycle(str(task), now=datetime(2025, 11, 1, tzinfo=timezone.utc), no_agent=True)
    (task / "continuous" / "cycles" / "x-results").mkdir()
    (task / "continuous" / "cycles" / "x-results" / "results.json").write_text("{}", encoding="utf-8")
    assert find_results_files([task]) == [task / "results.json"]
    os.remove(str(task / "continuous" / "baseline" / "baseline.json"))
    assert any("baseline.json" in w for w in check_continuous(task))


def test_cli_status_and_ledger(reference, tmp_path, capsys):
    task = _copy(reference, tmp_path, "cli")
    assert cli.main(["status", task]) == 0
    assert '"phase"' in capsys.readouterr().out
    assert cli.main(["ledger", task, "list"]) == 0
    assert "OPP-0001" in capsys.readouterr().out
    assert cli.main(["ledger", task, "set", "OPP-0001", "accepted", "--by", "engineer"]) == 0
    items = nc.Ledger(os.path.join(task, "continuous", "ledger", "events.jsonl")).current()
    assert items["OPP-0001"]["status"] == "accepted"


def test_cli_defaults_to_the_task_root(tmp_path, monkeypatch, capsys):
    root = tmp_path / "task_root"
    monkeypatch.setenv("NEQSIM_TASK_ROOT", str(root))
    monkeypatch.chdir(tmp_path)
    assert cli.task_root() == str(root)
    assert cli.main(["reference-case"]) == 0
    task = os.path.join(str(root), "reference_compressor_station")
    assert capsys.readouterr().out.strip() == task and os.path.isdir(task)
    assert cli.main(["status", "reference_compressor_station"]) == 0
    assert '"task": "reference_compressor_station"' in capsys.readouterr().out
    assert cli.main(["status"]) == 0
    assert "reference_compressor_station" in capsys.readouterr().out
    with pytest.raises(SystemExit, match="also looked in the task root"):
        cli.main(["cycle", "no_such_task"])


def test_state_schema_migrates_and_future_schema_fails_closed(reference, tmp_path):
    from neqsim_continuous.state import read_state

    task = _copy(reference, tmp_path, "state_schema")
    path = os.path.join(task, "continuous", "state.json")
    legacy = json.load(open(path, encoding="utf-8"))
    legacy.pop("schema_version", None)
    json.dump(legacy, open(path, "w", encoding="utf-8"))
    migrated = read_state(task)
    assert migrated["schema_version"] == "1.1"
    assert migrated["migrated_from"] == "1.0"
    assert json.load(open(path, encoding="utf-8"))["schema_version"] == "1.1"

    future = dict(migrated, schema_version="2.0")
    json.dump(future, open(path, "w", encoding="utf-8"))
    with pytest.raises(nc.StateSchemaError, match="Upgrade NeqSim"):
        nc.status(task)


def test_cycle_resumes_same_id_on_another_day(reference, tmp_path):
    task = _copy(reference, tmp_path, "cross_day_cycle")
    first = run_cycle(task, now=datetime(2025, 11, 1, tzinfo=timezone.utc), no_agent=True)
    cycle_path = os.path.join(task, "continuous", "cycles", first["cycle_id"], "cycle.json")
    interrupted = json.load(open(cycle_path, encoding="utf-8"))
    interrupted["status"] = "running"
    interrupted["stages"] = interrupted["stages"][:3]
    interrupted.pop("finished_at", None)
    json.dump(interrupted, open(cycle_path, "w", encoding="utf-8"))

    resumed = run_cycle(task, now=datetime(2025, 11, 2, tzinfo=timezone.utc), no_agent=True)
    assert resumed["cycle_id"] == first["cycle_id"]
    assert resumed["status"] == "complete"
    assert resumed["resume_count"] == 1
    assert resumed["recovery"]["kind"] == "interrupted_cycle"
    assert len(list_cycles(task)) == 1


def test_solve_adopts_completed_uncheckpointed_cycle(reference, tmp_path):
    from neqsim_continuous.solve import DEFAULT_SOLVE_STAGES
    from neqsim_continuous.state import write_state

    task = _copy(reference, tmp_path, "solve_recovery")
    manifest = run_cycle(task, mode="solve",
                         now=datetime(2025, 11, 1, tzinfo=timezone.utc),
                         stages=DEFAULT_SOLVE_STAGES, no_agent=True)
    write_state(task, {
        "state": "solving", "phase": "solving", "history": [], "agent_sessions": 0,
        "until": "goal",
        "solve_session": {"id": "S-test", "status": "running",
                          "started_at": "2025-11-01T00:00:00+00:00", "until": "goal"},
    })
    state = nc.solve(task, until="goal", no_agent=True)
    matching = [entry for entry in state["history"] if entry["cycle"] == manifest["cycle_id"]]
    assert len(matching) == 1
    assert matching[0]["recovered"] is True
    assert state["solve_session"]["status"] == "complete"


def test_five_second_status_and_resume_cli(reference, tmp_path, capsys):
    task = _copy(reference, tmp_path, "five_second_status")
    state = nc.solve(task, until="goal", no_agent=True)
    view = nc.status(task)
    for key in ("goal", "conclusion", "baseline_result", "best_validated", "attempts",
                "rejected_hypotheses", "blockers", "evidence", "what_changed",
                "next_action", "last_run", "next_run", "resume", "state_schema"):
        assert key in view
    assert view["attempts"] == len(state["history"])
    assert view["state_schema"] == "1.1"
    assert cli.main(["resume", task, "--no-agent"]) == 0
    payload = json.loads(capsys.readouterr().out)
    assert payload["resumed"] is False
    assert payload["status"]["state"] == "goal_met"
