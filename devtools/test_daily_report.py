"""Tests for the daily engineering update and the agent-launch failure reporting of living tasks."""
import json
import os

import pytest

from neqsim_continuous import agent_launch, daily_report


def _write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as handle:
        json.dump(data, handle)


def _entry(date, headline="Cause found", **extra):
    entry = {"date": date, "headline": headline, "what_changed": ["x"],
             "hypotheses": [{"id": "a", "name": "A", "old": 0.5, "new": 0.6},
                            {"id": "b", "name": "B", "old": 0.5, "new": 0.4}]}
    entry.update(extra)
    return entry


def test_validate_entry_flags_missing_headline_and_bad_sum():
    entry = _entry("2026-01-02", headline="")
    entry["hypotheses"][0]["new"] = 0.9
    problems = daily_report.validate_entry(entry)
    assert any("headline" in p for p in problems)
    assert any("sum" in p for p in problems)
    assert daily_report.validate_entry(_entry("2026-01-02")) == []


def test_template_is_filled_by_validation_only_when_edited():
    assert daily_report.validate_entry(daily_report.template("2026-01-02"))


def test_build_puts_newest_day_first_and_flags_days_without_review(tmp_path):
    pytest.importorskip("docx")
    task = str(tmp_path / "task")
    cont = os.path.join(task, "continuous")
    os.makedirs(os.path.join(cont, "cycles", "2026-01-03T0530Z@h"))
    with open(os.path.join(cont, "cycle_plan.yaml"), "w", encoding="utf-8") as handle:
        handle.write("schema_version: '1.0'\ndaily_report: {enabled: true, title: Test task}\n")
    _write(os.path.join(cont, "daily", "2026-01-01.json"), _entry("2026-01-01", "Older day"))
    _write(os.path.join(cont, "daily", "2026-01-02.json"), _entry("2026-01-02", "Newest reviewed day"))
    _write(os.path.join(cont, "cycles", "2026-01-03T0530Z@h", "cycle.json"), {
        "now": "2026-01-03T05:30:00+00:00",
        "stages": [{"name": "script:agent_review", "status": "warn", "message": "agent review FAILED"}]})

    path = daily_report.build(task)
    assert path and os.path.isfile(path)

    from docx import Document
    document = Document(path)
    text = "\n".join(p.text for p in document.paragraphs)
    cells = "\n".join(c.text for t in document.tables for r in t.rows for c in r.cells)
    assert text.index("Today") < text.index("Earlier updates")
    assert "No written review for this day" in cells
    assert "Older day" in cells and "agent review FAILED" in cells
    assert daily_report.update(task) == path


def test_update_is_silent_when_not_enabled(tmp_path):
    task = str(tmp_path / "task")
    os.makedirs(os.path.join(task, "continuous"))
    with open(os.path.join(task, "continuous", "cycle_plan.yaml"), "w", encoding="utf-8") as handle:
        handle.write("schema_version: '1.0'\n")
    assert daily_report.update(task) is None


def test_agent_failure_reason_names_unavailable_model():
    assert agent_launch.classify_failure('Execution failed: Model "x" is not available.') == "model_unavailable"
    assert agent_launch.classify_failure("429 rate limit") == "quota_or_rate_limit"
    assert agent_launch.classify_failure("fine") == ""


def test_build_command_pins_model():
    argv = agent_launch.build_command("t", "c", "go", model="m1")
    assert argv[argv.index("--model") + 1] == "m1"
    assert "--model" not in agent_launch.build_command("t", "c", "go")


def test_fallback_tries_next_model_only_when_unavailable(monkeypatch, tmp_path):
    calls = []

    def fake_launch(argv, timeout=1800, cwd=None):
        model = argv[argv.index("--model") + 1] if "--model" in argv else None
        calls.append(model)
        if model in (None, "m-old"):
            return {"status": "fail", "returncode": 1, "reason": "model_unavailable"}
        return {"status": "ok", "returncode": 0}

    monkeypatch.setattr(agent_launch, "launch", fake_launch)
    cfg = {"model": "m-old", "fallback_models": ["m-new", "m-never"]}
    result = agent_launch.run_with_fallback(str(tmp_path), str(tmp_path), "go", cfg)
    assert result["status"] == "ok" and result["model"] == "m-new"
    assert calls == ["m-old", "m-new"]
    assert [a["reason"] for a in result["attempts"]] == ["model_unavailable", ""]


def test_fallback_does_not_hide_other_failures_or_missing_output(monkeypatch, tmp_path):
    monkeypatch.setattr(agent_launch, "launch", lambda argv, timeout=1800, cwd=None: {
        "status": "fail", "returncode": 1, "reason": "not_authenticated"})
    result = agent_launch.run_with_fallback(str(tmp_path), str(tmp_path), "go", {"model": "m"})
    assert result["status"] == "fail" and len(result["attempts"]) == 1
    monkeypatch.setattr(agent_launch, "launch", lambda argv, timeout=1800, cwd=None: {"status": "ok", "returncode": 0})
    result = agent_launch.run_with_fallback(str(tmp_path), str(tmp_path), "go", {"model": "m"}, success=lambda: False)
    assert result["status"] == "fail" and result["reason"] == "no_output"


def test_model_candidates_defaults_and_unpinned_first():
    assert agent_launch.model_candidates({})[0] is None
    assert agent_launch.model_candidates({"model": "a", "fallback_models": []}) == ["a"]


def test_bulk_evidence_changes_collapse_to_one_row():
    from neqsim_continuous import figures
    changes = [{"path": "refs/f%d.txt" % i, "change": "removed", "kind": "evidence_file"} for i in range(50)]
    data = {"plan": {}, "cycles": [{"cycle_id": "c1", "now": "2026-01-01T00:00:00", "evidence": {"changes": changes}}]}
    _, rows = figures.input_summary(data)
    assert len(rows) == 1 and rows[0]["what"].startswith("50 files")


def test_make_living_scaffolds_daily_report_and_pinned_agent(tmp_path):
    import yaml
    from neqsim_continuous.living import make_living
    task = str(tmp_path / "t")
    os.makedirs(task)
    make_living(task)
    plan = yaml.safe_load(open(os.path.join(task, "continuous", "cycle_plan.yaml"), encoding="utf-8"))
    assert plan["daily_report"]["enabled"] is False and plan["agent"]["fallback_models"]
    assert os.path.isfile(os.path.join(task, "continuous", "daily", "_standing.json"))


def test_drift_reset_clears_alarm(tmp_path):
    from neqsim_continuous import cli
    task = str(tmp_path / "t")
    os.makedirs(os.path.join(task, "continuous"))
    _write(os.path.join(task, "continuous", "drift_state.json"),
           {"x": {"alarm": True, "streak": 9, "cusum_pos": 5.0, "cusum_neg": 0.0, "alarms": 1, "in_control_streak": 0}})
    cli.main(["drift", task, "--reset"])
    state = json.load(open(os.path.join(task, "continuous", "drift_state.json"), encoding="utf-8"))
    assert state["x"]["alarm"] is False and state["x"]["streak"] == 0


def test_schedule_hardening_quotes_task_name():
    from neqsim_continuous import schedule
    command = schedule.harden_command("it's a task")
    assert "'it''s a task'" in command and "StartWhenAvailable" in command
