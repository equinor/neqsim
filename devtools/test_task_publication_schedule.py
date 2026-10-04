"""Failure-path coverage for the task-to-upstream and scheduling workflows."""
import json

import pytest

import file_neqsim_issue as filing
from neqsim_continuous import schedule


def test_pr_dry_run_has_no_subprocesses(tmp_path, monkeypatch):
    (tmp_path / "results.json").write_text(json.dumps({"improvements": [
        {"target": "neqsim", "gap": "Synthetic API gap"}
    ]}), encoding="utf-8")

    def forbidden(*args, **kwargs):
        raise AssertionError("dry-run must not call Git, gh, or a browser")

    monkeypatch.setattr(filing.subprocess, "run", forbidden)
    monkeypatch.setattr(filing, "open_in_browser", forbidden)
    assert filing.main([str(tmp_path), "--dry-run", "--pr"]) == 0


@pytest.mark.parametrize("branch_result", [(1, "", "not a repository"), (0, "HEAD", "")])
def test_pr_rejects_unknown_or_detached_branch(monkeypatch, branch_result):
    calls = []

    def run(cmd, cwd=None):
        calls.append(cmd)
        return branch_result

    monkeypatch.setattr(filing, "run_command", run)
    assert filing.create_pull_request("/repo", "equinor/neqsim", "fix", "master",
                                      "title", "body", ["src/main/java/neqsim/Foo.java"], False) is None
    assert len(calls) == 1


def test_pr_stops_after_failed_commit(monkeypatch):
    calls = []

    def run(cmd, cwd=None):
        calls.append(cmd)
        if cmd[:2] == ["git", "rev-parse"]:
            return 0, "feature", ""
        if cmd[:2] == ["git", "commit"]:
            return 1, "", "hook rejected commit"
        return 0, "", ""

    monkeypatch.setattr(filing, "run_command", run)
    assert filing.create_pull_request("/repo", "equinor/neqsim", "fix", "master",
                                      "title", "body", ["src/main/java/neqsim/Foo.java"], False) is None
    assert not any(cmd[:2] == ["git", "push"] or cmd[0] == "gh" for cmd in calls)
    commit = next(cmd for cmd in calls if cmd[:2] == ["git", "commit"])
    assert "--only" in commit  # Unrelated staged files must remain uncommitted.
    assert commit[commit.index("--") + 1:] == ["src/main/java/neqsim/Foo.java"]


@pytest.mark.parametrize("hours", [0, -1, 1.5, 5, 25, float("nan"), float("inf")])
def test_schedule_rejects_intervals_cron_cannot_represent(tmp_path, hours):
    with pytest.raises(ValueError):
        schedule.build(tmp_path, every_hours=hours)


@pytest.mark.parametrize("daily", ["24:00", "12:60", "99:99", "1:00"])
def test_schedule_rejects_invalid_clock_time(tmp_path, daily):
    with pytest.raises(ValueError):
        schedule.build(tmp_path, daily=daily)


def test_schedule_preserves_positional_api(tmp_path):
    spec = schedule.build(tmp_path, "05:30", "monitor", "/python", False)
    assert spec["command"].startswith('"/python"')
    assert "--standard-first" not in spec["command"]


def test_daily_and_interval_are_mutually_exclusive(tmp_path):
    with pytest.raises(ValueError):
        schedule.build(tmp_path, daily="06:00", every_hours=2)


def test_daily_interval_and_spaced_windows_path(tmp_path):
    spec = schedule.build(tmp_path / "task with spaces", every_hours=24)
    assert spec["cron"].startswith("0 0 * * * ")
    assert spec["windows"][spec["windows"].index("/TR") + 1] == '"' + spec["wrapper_path"] + '"'
    assert spec["windows"][spec["windows"].index("/ST") + 1] == "00:00"


def test_issue_body_does_not_publish_local_path():
    body = filing.issue_body({"body": "Synthetic gap"}, "/private/customer/study", "example")
    assert "/private" not in body


def test_missing_gh_with_pr_uses_only_form_link(tmp_path, monkeypatch):
    (tmp_path / "results.json").write_text(json.dumps({"improvements": [
        {"target": "neqsim", "gap": "Synthetic API gap"}
    ]}), encoding="utf-8")
    monkeypatch.setattr(filing, "gh_available", lambda: False)

    def forbidden(*args, **kwargs):
        raise AssertionError("missing gh must not attempt local Git publication")

    monkeypatch.setattr(filing, "run_command", forbidden)
    assert filing.main([str(tmp_path), "--pr", "--yes", "--no-browser"]) == 0


def test_windows_install_rejects_overlong_wrapper_without_side_effects(tmp_path, monkeypatch):
    task = tmp_path / ("long" * 35) / ("long" * 35)
    spec = schedule.build(task)
    monkeypatch.setattr(schedule.platform, "system", lambda: "Windows")

    def forbidden(*args, **kwargs):
        raise AssertionError("overlong wrapper must not invoke schtasks")

    monkeypatch.setattr(schedule.subprocess, "run", forbidden)
    assert schedule.install(spec)["status"] == "fail"
    assert not task.exists()
