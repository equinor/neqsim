#!/usr/bin/env python3
"""Tests for task_roots.py and task_corpus.py.

These cover the two behaviours that silently broke the corpus before: tooling
that only looked in <repo>/task_solve while tasks were written somewhere else,
and task folders vendoring their own copy of the report generator.
"""
import json
import os
import sys

import pytest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import task_corpus  # noqa: E402
import task_roots  # noqa: E402


def make_task(root, name, results=None, study_title=None):
    """Create a minimal but recognisable task folder and return its path."""
    task = root / name
    (task / "step1_scope_and_research").mkdir(parents=True)
    (task / "step2_analysis").mkdir()
    (task / "step3_report").mkdir()
    if results is not None:
        (task / "results.json").write_text(json.dumps(results), encoding="utf-8")
    title = study_title or name.replace("_", " ")
    (task / "study_config.yaml").write_text(
        'study:\n  title: "{}"\n  task_type: "B"\n'.format(title), encoding="utf-8")
    return task


@pytest.fixture
def isolated(tmp_path, monkeypatch):
    """Point the resolver at a temporary settings file and clear the env."""
    monkeypatch.setattr(task_roots, "SETTINGS_FILE", tmp_path / "task_defaults.json")
    monkeypatch.delenv("NEQSIM_TASK_ROOT", raising=False)
    monkeypatch.setattr(task_roots, "REPO_ROOT", tmp_path / "repo")
    return tmp_path


def test_saved_root_is_used_when_tasks_live_outside_the_repo(isolated):
    external = isolated / "elsewhere"
    external.mkdir()
    make_task(external, "2026-01-01_outside_task")
    task_roots.SETTINGS_FILE.write_text(
        json.dumps({"task_root": str(external)}), encoding="utf-8")

    roots = task_roots.resolve_task_roots()
    assert external.resolve() in roots
    names = [folder.name for folder in task_roots.find_task_folders(roots)]
    assert names == ["2026-01-01_outside_task"]


def test_environment_overrides_saved_setting(isolated, monkeypatch):
    saved, env = isolated / "saved", isolated / "env"
    saved.mkdir()
    env.mkdir()
    make_task(saved, "2026-01-01_saved")
    make_task(env, "2026-01-02_env")
    task_roots.SETTINGS_FILE.write_text(
        json.dumps({"task_root": str(saved)}), encoding="utf-8")
    monkeypatch.setenv("NEQSIM_TASK_ROOT", str(env))

    roots = task_roots.resolve_task_roots()
    assert roots[0] == env.resolve()
    # Both corpora stay searchable so no prior work is invisible.
    assert saved.resolve() in roots


def test_several_roots_are_searched_as_one_corpus(isolated):
    first, second = isolated / "a", isolated / "b"
    first.mkdir()
    second.mkdir()
    make_task(first, "2026-01-01_alpha")
    make_task(second, "2026-01-02_beta")

    folders = task_roots.find_task_folders(
        task_roots.resolve_task_roots([str(first), str(second)]))
    assert [folder.name for folder in folders] == ["2026-01-01_alpha",
                                                   "2026-01-02_beta"]


def test_duplicate_folder_names_are_reported_once_and_flagged(isolated):
    first, second = isolated / "a", isolated / "b"
    first.mkdir()
    second.mkdir()
    make_task(first, "2026-01-01_same")
    make_task(second, "2026-01-01_same")

    roots = task_roots.resolve_task_roots([str(first), str(second)])
    assert len(task_roots.find_task_folders(roots)) == 1
    duplicates = task_roots.duplicate_task_folders(roots)
    assert "2026-01-01_same" in duplicates
    assert len(duplicates["2026-01-01_same"]) == 2


def test_clutter_folders_are_not_mistaken_for_tasks(isolated):
    root = isolated / "root"
    (root / "TASK_TEMPLATE").mkdir(parents=True)
    (root / "__pycache__").mkdir()
    (root / "notes").mkdir()
    make_task(root, "2026-01-01_real")

    folders = task_roots.find_task_folders(task_roots.resolve_task_roots([str(root)]))
    assert [folder.name for folder in folders] == ["2026-01-01_real"]


def test_index_lists_every_task_with_its_headline_results(isolated):
    root = isolated / "root"
    root.mkdir()
    make_task(root, "2026-01-01_alpha", results={
        "objective": "Screen the export line for hydrate risk.",
        "key_results": {"margin_C": 4.2},
        "standards_applied": [{"standard": "NORSOK P-002"}],
        "benchmark_validation": {"tests": []},
    }, study_title="Hydrate margin, export line")
    make_task(root, "2026-01-02_beta")

    records = [task_corpus.summarize_task(folder)
               for folder in task_roots.find_task_folders([root])]
    index = task_corpus.render_index(records, [root])

    assert "Hydrate margin, export line" in index
    assert "2026-01-02_beta" in index
    assert len(records) == 2
    alpha = next(r for r in records if r["folder"] == "2026-01-01_alpha")
    assert alpha["has_results"] is True
    assert alpha["headline"] == "margin_C"
    assert alpha["standards"] == ["NORSOK P-002"]
    assert alpha["benchmark_validation"] is True
    beta = next(r for r in records if r["folder"] == "2026-01-02_beta")
    assert beta["has_results"] is False


def test_relink_is_dry_run_by_default(isolated):
    root = isolated / "root"
    root.mkdir()
    task = make_task(root, "2026-01-01_fork")
    vendored = task / "step3_report" / "generate_report.py"
    original = "# a full vendored generator\n" + "x = 1\n" * 500
    vendored.write_text(original, encoding="utf-8")

    forked, relinked, _, _, _ = task_corpus.relink_reports(
        task_roots.find_task_folders([root]))
    assert len(forked) == 1
    assert relinked == []
    assert vendored.read_text(encoding="utf-8") == original


def test_relink_replaces_the_fork_and_preserves_hand_written_sections(isolated):
    root = isolated / "root"
    root.mkdir()
    task = make_task(root, "2026-01-01_fork")
    vendored = task / "step3_report" / "generate_report.py"
    vendored.write_text(
        'MANUAL_SECTIONS = {\n'
        '    "executive_summary": "The bed removes 99.99% of the mercury.",\n'
        '    "conclusions": "[Placeholder]",\n'
        '}\n' + "filler = 1\n" * 500,
        encoding="utf-8")

    _, relinked, preserved, _, _ = task_corpus.relink_reports(
        task_roots.find_task_folders([root]), apply_changes=True)
    assert len(relinked) == 1
    assert preserved == ["2026-01-01_fork"]

    launcher = vendored.read_text(encoding="utf-8")
    assert task_corpus.LAUNCHER_MARKER in launcher
    assert len(launcher) < 10000

    sections = json.loads(
        (task / "step3_report" / "report_sections.json").read_text(encoding="utf-8"))
    manual = sections["manual_sections"]
    assert manual["executive_summary"].startswith("The bed removes")
    # Placeholders are not content and must not be carried over.
    assert "conclusions" not in manual


def test_relink_leaves_tasks_with_custom_report_sections_alone(isolated):
    root = isolated / "root"
    root.mkdir()
    task = make_task(root, "2026-01-01_custom")
    vendored = task / "step3_report" / "generate_report.py"
    original = (
        'MANUAL_SECTIONS = {\n'
        '    "tank_specification": "DN500 cylinder, 250 bar design.",\n'
        '}\n' + "filler = 1\n" * 500)
    vendored.write_text(original, encoding="utf-8")

    _, relinked, _, custom, _ = task_corpus.relink_reports(
        task_roots.find_task_folders([root]), apply_changes=True)
    assert relinked == []
    assert custom and custom[0][0] == "2026-01-01_custom"
    assert "tank_specification" in " ".join(custom[0][1])
    assert vendored.read_text(encoding="utf-8") == original


def test_env_stamp_records_the_software_that_produced_the_results(isolated):
    root = isolated / "root"
    root.mkdir()
    task = make_task(root, "2026-01-01_alpha", results={"key_results": {"a": 1}})

    assert task_corpus.stamp_environment(task) == "written"

    results = json.loads((task / "results.json").read_text(encoding="utf-8"))
    env = results["environment"]
    assert env["schema"] == task_corpus.ENVIRONMENT_SCHEMA
    assert env["python"].startswith(str(sys.version_info.major))
    assert env["recorded"]
    # A ${revision} placeholder is not a version anyone can reproduce from.
    assert "${" not in env.get("neqsim_version", "")
    # The results themselves must be untouched.
    assert results["key_results"] == {"a": 1}


def test_env_stamp_keeps_an_existing_block_and_reports_missing_results(isolated):
    root = isolated / "root"
    root.mkdir()
    task = make_task(root, "2026-01-01_alpha", results={"key_results": {"a": 1}})
    assert task_corpus.stamp_environment(task) == "written"
    first = json.loads((task / "results.json").read_text(encoding="utf-8"))

    # A second pass must not silently rewrite the recorded environment.
    assert task_corpus.stamp_environment(task) == "kept"
    assert json.loads(
        (task / "results.json").read_text(encoding="utf-8")) == first
    assert task_corpus.stamp_environment(task, force=True) == "written"

    bare = make_task(root, "2026-01-02_no_results")
    assert task_corpus.stamp_environment(bare) == "no results.json"


if __name__ == "__main__":
    raise SystemExit(pytest.main([__file__, "-q"]))
