"""Regression tests for the file-issue/PR agentic gap-filing workflow."""
import json
import os

import pytest

import file_neqsim_issue as fni


SAMPLE_NIP_MD = """# NeqSim Improvement Proposals

## 1. Delivered

### NIP-01: Add JT coefficient helper

**Gap:** No method computes the Joule-Thomson coefficient directly.
**Impact on task:** Had to differentiate temperature manually in Python.
**Priority:** Medium

Some further implementation detail text.

### NIP-02: Add relief valve orifice sizing

**Gap:** No API 520 orifice sizing helper exists.
**Impact on task:** Screening done in a spreadsheet instead of NeqSim.
**Priority:** High

**GitHub issue:** https://github.com/equinor/neqsim/issues/999
"""


def test_parse_nips_extracts_fields_and_existing_issue_marker():
    items = fni.parse_nips(SAMPLE_NIP_MD)
    assert [item["id"] for item in items] == ["NIP-01", "NIP-02"]

    first = items[0]
    assert first["title"] == "Add JT coefficient helper"
    assert "Joule-Thomson" in first["gap"]
    assert first["priority"] == "Medium"
    assert first["issue_url"] is None

    second = items[1]
    assert second["issue_url"] == "https://github.com/equinor/neqsim/issues/999"


def test_parse_nips_empty_input_returns_empty_list():
    assert fni.parse_nips(None) == []
    assert fni.parse_nips("") == []


def test_load_result_improvements_only_targets_neqsim(tmp_path):
    results = {
        "improvements": [
            {"target": "neqsim", "gap": "no wax margin check",
             "change": "added WaxMarginCalculator", "evidence": "3 tests green"},
            {"target": "skill", "gap": "missing cross-reference"},
        ]
    }
    (tmp_path / "results.json").write_text(json.dumps(results), encoding="utf-8")

    items = fni.load_result_improvements(str(tmp_path))
    assert len(items) == 1
    assert items[0]["kind"] == "result"
    assert items[0]["id"] == "RESULT-0"
    assert "wax margin" in items[0]["gap"]


def test_load_result_improvements_missing_file_is_empty(tmp_path):
    assert fni.load_result_improvements(str(tmp_path)) == []


def _write_task(tmp_path, nip_md=SAMPLE_NIP_MD, results=None):
    step1 = tmp_path / "step1_scope_and_research"
    step1.mkdir()
    (step1 / "neqsim_improvements.md").write_text(nip_md, encoding="utf-8")
    if results is not None:
        (tmp_path / "results.json").write_text(json.dumps(results), encoding="utf-8")
    return tmp_path


def test_collect_items_filters_already_filed_unless_forced(tmp_path):
    _write_task(tmp_path)

    default_items = fni.collect_items(str(tmp_path))
    assert [item["id"] for item in default_items] == ["NIP-01"]

    forced_items = fni.collect_items(str(tmp_path), force=True)
    assert [item["id"] for item in forced_items] == ["NIP-01", "NIP-02"]


def test_confirm_dry_run_and_yes_never_prompt(monkeypatch):
    def _boom(_prompt):
        raise AssertionError("input() must not be called")

    monkeypatch.setattr("builtins.input", _boom)
    assert fni.confirm("do it?", assume_yes=False, dry_run=True) is True
    assert fni.confirm("do it?", assume_yes=True, dry_run=False) is True


def test_confirm_respects_typed_answer(monkeypatch):
    monkeypatch.setattr("builtins.input", lambda _prompt: "y")
    assert fni.confirm("do it?", assume_yes=False, dry_run=False) is True

    monkeypatch.setattr("builtins.input", lambda _prompt: "n")
    assert fni.confirm("do it?", assume_yes=False, dry_run=False) is False


def test_create_github_issue_dry_run_never_calls_gh(monkeypatch, capsys):
    def _boom(*_args, **_kwargs):
        raise AssertionError("run_command must not be called in dry-run")

    monkeypatch.setattr(fni, "run_command", _boom)
    url = fni.create_github_issue("equinor/neqsim", "title", "body", ["nip"], dry_run=True)
    assert url is None
    assert "dry-run" in capsys.readouterr().out


def test_create_github_issue_returns_url_on_success(monkeypatch):
    monkeypatch.setattr(fni, "run_command",
                         lambda cmd, cwd=None: (0, "https://github.com/equinor/neqsim/issues/42\n", ""))
    url = fni.create_github_issue("equinor/neqsim", "title", "body", ["nip"], dry_run=False)
    assert url == "https://github.com/equinor/neqsim/issues/42"


def test_create_github_issue_reports_failure(monkeypatch, capsys):
    monkeypatch.setattr(fni, "run_command", lambda cmd, cwd=None: (1, "", "boom"))
    url = fni.create_github_issue("equinor/neqsim", "title", "body", ["nip"], dry_run=False)
    assert url is None
    assert "Could not create the issue" in capsys.readouterr().out


def test_existing_open_issue_matches_exact_title(monkeypatch):
    payload = json.dumps([{"number": 7, "title": "[NIP] slug: Add JT coefficient helper"}])
    monkeypatch.setattr(fni, "run_command", lambda cmd, cwd=None: (0, payload, ""))
    number = fni.existing_open_issue("equinor/neqsim", "[NIP] slug: Add JT coefficient helper")
    assert number == 7

    number = fni.existing_open_issue("equinor/neqsim", "[NIP] slug: Something else")
    assert number is None


def test_record_issue_url_inserts_marker_into_nip_file(tmp_path):
    _write_task(tmp_path)
    items = fni.parse_nips(SAMPLE_NIP_MD)
    first = items[0]

    fni.record_issue_url(str(tmp_path), first, "https://github.com/equinor/neqsim/issues/123")

    text = (tmp_path / "step1_scope_and_research" / "neqsim_improvements.md").read_text(
        encoding="utf-8")
    assert "**GitHub issue:** https://github.com/equinor/neqsim/issues/123" in text
    assert "NIP-01" in text.split("**GitHub issue:** https://github.com/equinor/neqsim/issues/123")[0][-40:]


def test_record_issue_url_updates_results_json(tmp_path):
    results = {"improvements": [{"target": "neqsim", "gap": "no wax margin check"}]}
    _write_task(tmp_path, results=results)
    item = fni.load_result_improvements(str(tmp_path))[0]

    fni.record_issue_url(str(tmp_path), item, "https://github.com/equinor/neqsim/issues/55")

    data = json.loads((tmp_path / "results.json").read_text(encoding="utf-8"))
    assert data["improvements"][0]["issue_url"] == "https://github.com/equinor/neqsim/issues/55"


def test_changed_java_files_filters_to_neqsim_java(monkeypatch):
    porcelain = "\n".join([
        " M src/main/java/neqsim/thermo/Foo.java",
        "?? src/test/java/neqsim/thermo/FooTest.java",
        " M README.md",
        " M src/main/resources/data.csv",
    ])
    monkeypatch.setattr(fni, "run_command", lambda cmd, cwd=None: (0, porcelain, ""))
    files = fni.changed_java_files("/repo")
    assert files == [
        "src/main/java/neqsim/thermo/Foo.java",
        "src/test/java/neqsim/thermo/FooTest.java",
    ]


def test_changed_java_files_empty_when_git_fails(monkeypatch):
    monkeypatch.setattr(fni, "run_command", lambda cmd, cwd=None: (1, "", "not a git repo"))
    assert fni.changed_java_files("/repo") == []


def test_run_quality_checks_dry_run_skips_commands(monkeypatch):
    def _boom(*_args, **_kwargs):
        raise AssertionError("run_command must not be called in dry-run")

    monkeypatch.setattr(fni, "run_command", _boom)
    assert fni.run_quality_checks("/repo", dry_run=True) is True


def test_run_quality_checks_stops_on_first_failure(monkeypatch):
    calls = []

    def _fake(cmd, cwd=None):
        calls.append(cmd)
        return (1, "out", "err") if "spotless:check" in cmd else (0, "", "")

    monkeypatch.setattr(fni, "run_command", _fake)
    ok = fni.run_quality_checks("/repo", dry_run=False)
    assert ok is False
    # Stops after spotless:check fails -- never reaches checkstyle:check.
    assert not any("checkstyle:check" in cmd for cmd in calls)


def test_main_list_mode_never_touches_gh(tmp_path, monkeypatch, capsys):
    _write_task(tmp_path)

    def _boom(*_args, **_kwargs):
        raise AssertionError("gh must not be probed in --list mode")

    monkeypatch.setattr(fni, "gh_available", _boom)
    monkeypatch.setattr(fni, "gh_authenticated", _boom)

    code = fni.main([str(tmp_path), "--list"])
    assert code == 0
    out = capsys.readouterr().out
    assert "NIP-01" in out


def test_main_dry_run_end_to_end(tmp_path, monkeypatch, capsys):
    _write_task(tmp_path)
    monkeypatch.setattr(fni, "existing_open_issue", lambda repo, title: None)

    code = fni.main([str(tmp_path), "--dry-run"])
    assert code == 0
    out = capsys.readouterr().out
    assert "dry-run" in out


def test_github_new_issue_url_encodes_and_truncates():
    url = fni.github_new_issue_url("equinor/neqsim", "a title", "a body", ["nip", "enhancement"])
    assert url.startswith("https://github.com/equinor/neqsim/issues/new?")
    assert "title=a%20title" in url
    assert "labels=nip%2Cenhancement" in url

    long_body = "x" * (fni.MAX_URL_BODY_LEN + 500)
    truncated_url = fni.github_new_issue_url("equinor/neqsim", "t", long_body)
    assert "truncated" in truncated_url


def test_offer_browser_fallback_dry_run_never_opens_browser(monkeypatch, capsys):
    def _boom(_url):
        raise AssertionError("open_in_browser must not be called in dry-run")

    monkeypatch.setattr(fni, "open_in_browser", _boom)
    url = fni.offer_browser_fallback("equinor/neqsim", "title", "body", ["nip"],
                                      assume_yes=True, dry_run=True)
    assert url.startswith("https://github.com/equinor/neqsim/issues/new?")
    assert "dry-run" in capsys.readouterr().out


def test_offer_browser_fallback_respects_no_browser_flag(monkeypatch):
    def _boom(_url):
        raise AssertionError("open_in_browser must not be called with open_browser=False")

    monkeypatch.setattr(fni, "open_in_browser", _boom)
    url = fni.offer_browser_fallback("equinor/neqsim", "title", "body", ["nip"],
                                      assume_yes=True, dry_run=False, open_browser=False)
    assert url.startswith("https://github.com/equinor/neqsim/")


def test_offer_browser_fallback_opens_when_confirmed(monkeypatch):
    opened = []
    monkeypatch.setattr(fni, "open_in_browser", lambda url: opened.append(url) or True)
    fni.offer_browser_fallback("equinor/neqsim", "title", "body", ["nip"],
                                assume_yes=True, dry_run=False, open_browser=True)
    assert len(opened) == 1


def test_main_falls_back_to_browser_link_when_gh_missing(tmp_path, monkeypatch, capsys):
    """This is the plugin-user path: no local checkout, no `gh` -- must still work."""
    _write_task(tmp_path)
    monkeypatch.setattr(fni, "gh_available", lambda: False)

    def _boom(*_args, **_kwargs):
        raise AssertionError("gh must never be invoked once it is known to be unavailable")

    monkeypatch.setattr(fni, "run_command", _boom)
    opened = []
    monkeypatch.setattr(fni, "open_in_browser", lambda url: opened.append(url) or True)

    code = fni.main([str(tmp_path), "--yes"])
    assert code == 0
    out = capsys.readouterr().out
    assert "not installed" in out
    assert "Falling back to a pre-filled" in out
    assert len(opened) == 1
    assert opened[0].startswith("https://github.com/equinor/neqsim/issues/new?")

    # A form link is not a created issue -- must never be recorded as "filed".
    text = (tmp_path / "step1_scope_and_research" / "neqsim_improvements.md").read_text(
        encoding="utf-8")
    assert text == SAMPLE_NIP_MD


def test_main_no_browser_flag_never_opens_one(tmp_path, monkeypatch):
    _write_task(tmp_path)
    monkeypatch.setattr(fni, "gh_available", lambda: False)

    def _boom(_url):
        raise AssertionError("open_in_browser must not be called with --no-browser")

    monkeypatch.setattr(fni, "open_in_browser", _boom)

    code = fni.main([str(tmp_path), "--yes", "--no-browser"])
    assert code == 0


def test_main_falls_back_when_gh_create_fails_mid_run(tmp_path, monkeypatch, capsys):
    """gh installed and authenticated, but the create call itself errors out."""
    _write_task(tmp_path)
    monkeypatch.setattr(fni, "gh_available", lambda: True)
    monkeypatch.setattr(fni, "gh_authenticated", lambda: True)
    monkeypatch.setattr(fni, "existing_open_issue", lambda repo, title: None)
    monkeypatch.setattr(fni, "run_command", lambda cmd, cwd=None: (1, "", "rate limited"))
    opened = []
    monkeypatch.setattr(fni, "open_in_browser", lambda url: opened.append(url) or True)

    code = fni.main([str(tmp_path), "--yes"])
    assert code == 0
    assert len(opened) == 1
    assert "Could not create the issue" in capsys.readouterr().out


def test_main_no_items_is_a_clean_no_op(tmp_path, capsys):
    code = fni.main([str(tmp_path)])
    assert code == 0
    assert "No unfiled NeqSim gaps" in capsys.readouterr().out


def test_main_rejects_missing_folder(tmp_path):
    missing = str(tmp_path / "does-not-exist")
    assert fni.main([missing]) == 2
