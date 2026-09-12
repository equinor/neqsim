"""Task output destination regression tests, isolated from user settings."""
import json
import sys
from pathlib import Path

import pytest

import new_task


@pytest.fixture
def defaults(tmp_path, monkeypatch):
    config = tmp_path / "settings" / "task_defaults.json"
    monkeypatch.setattr(new_task, "task_defaults_path", lambda: str(config))
    monkeypatch.setattr(new_task, "TASK_SOLVE_DIR", str(tmp_path / "fallback"))
    monkeypatch.delenv("NEQSIM_TASK_ROOT", raising=False)
    monkeypatch.delenv("NEQSIM_REPORT_TEMPLATE", raising=False)
    monkeypatch.delenv("NEQSIM_DOCUMENT_ROOT", raising=False)
    return config


def test_task_root_precedence(defaults, tmp_path, monkeypatch):
    assert new_task.resolve_task_root() == str(tmp_path / "fallback")
    saved = new_task.save_default_task_root(str(tmp_path / "saved tasks"))
    assert json.loads(defaults.read_text())["task_root"] == saved
    assert new_task.resolve_task_root() == saved
    monkeypatch.setenv("NEQSIM_TASK_ROOT", str(tmp_path / "environment"))
    assert new_task.resolve_task_root() == str(tmp_path / "environment")
    assert new_task.resolve_task_root(str(tmp_path / "explicit")) == str(tmp_path / "explicit")


def test_create_and_list_external_task(defaults, tmp_path, capsys):
    root = new_task.save_default_task_root(str(tmp_path / "external tasks"))
    task = Path(new_task.create_task("External destination", prompt="Original request"))
    assert task.parent == Path(root)
    assert (task / "study_config.yaml").is_file()
    assert (task / "step3_report" / "generate_report.py").is_file()
    assert (task / "step1_scope_and_research" / "references").is_dir()
    assert not (tmp_path / "fallback").exists()
    new_task.list_tasks()
    assert root in capsys.readouterr().out


def test_invalid_default_fails_closed(defaults):
    defaults.parent.mkdir()
    defaults.write_text('{"task_root": 123}')
    with pytest.raises(ValueError, match="non-empty path"):
        new_task.resolve_task_root()


def test_cli_settings_and_override(defaults, tmp_path, monkeypatch, capsys):
    saved = str(tmp_path / "saved")
    monkeypatch.setattr(sys, "argv", ["new_task", "--set-default-folder", saved])
    new_task.main()
    monkeypatch.setattr(sys, "argv", ["new_task", "--show-task-root"])
    new_task.main()
    assert saved in capsys.readouterr().out
    explicit = str(tmp_path / "one off")
    monkeypatch.setattr(sys, "argv", ["new_task", "CLI task", "--task-root", explicit,
                                     "--type", "A", "--scale", "quick"])
    new_task.main()
    tasks = list(Path(explicit).glob("*_cli_task"))
    assert len(tasks) == 1
    assert "quick" in (tasks[0] / "study_config.yaml").read_text(encoding="utf-8")
    assert new_task.resolve_task_root() == saved
    monkeypatch.setattr(sys, "argv", ["new_task", "--reset-default-folder"])
    new_task.main()
    assert not defaults.exists()
    assert tasks[0].exists()


def test_unified_cli_resolves_relative_destination_from_caller(defaults, tmp_path,
                                                             monkeypatch):
    import neqsim_cli

    monkeypatch.chdir(tmp_path)
    monkeypatch.setattr(sys, "argv", ["neqsim", "new-task", "--set-default-folder",
                                     "relative tasks"])
    neqsim_cli.main()
    assert new_task.resolve_task_root() == str(tmp_path / "relative tasks")


def test_top_level_task_root_command(defaults, tmp_path, monkeypatch, capsys):
    import neqsim_cli

    chosen = tmp_path / "engineering tasks"
    monkeypatch.setattr(sys, "argv", ["neqsim", "--set-task-root", str(chosen)])
    with pytest.raises(SystemExit) as exit_info:
        neqsim_cli.main()
    assert exit_info.value.code == 0
    assert new_task.resolve_task_root() == str(chosen)

    monkeypatch.setattr(sys, "argv", ["neqsim", "--show-task-root"])
    with pytest.raises(SystemExit):
        neqsim_cli.main()
    assert str(chosen) in capsys.readouterr().out

    monkeypatch.setattr(sys, "argv", ["neqsim", "--reset-task-root"])
    with pytest.raises(SystemExit):
        neqsim_cli.main()
    assert not defaults.exists()


def test_default_can_follow_terminal_folder(defaults, tmp_path, monkeypatch):
    assert new_task.save_default_task_root("cwd") == "."
    first = tmp_path / "project a"
    second = tmp_path / "project b"
    for folder in (first, second):
        folder.mkdir()
        monkeypatch.chdir(folder)
        assert new_task.resolve_task_root() == str(folder)
    new_task.create_task("Terminal folder task")
    assert list(second.glob("*_terminal_folder_task"))


def test_report_template_precedence_and_validation(defaults, tmp_path, monkeypatch):
    company = tmp_path / "company template.docx"
    company.write_bytes(b"PK")
    assert new_task.resolve_report_template() is None
    assert new_task.save_default_report_template(str(company)) == str(company)
    assert new_task.resolve_report_template() == str(company)

    other = tmp_path / "other.dotx"
    other.write_bytes(b"PK")
    monkeypatch.setenv("NEQSIM_REPORT_TEMPLATE", str(other))
    assert new_task.resolve_report_template() == str(other)
    monkeypatch.delenv("NEQSIM_REPORT_TEMPLATE")

    with pytest.raises(ValueError, match="not found"):
        new_task.resolve_report_template(str(tmp_path / "missing.docx"))
    with pytest.raises(ValueError, match=r"\.docx or \.dotx"):
        new_task.resolve_report_template(str(tmp_path / "template.pdf"))


def test_report_template_and_task_root_settings_coexist(defaults, tmp_path, monkeypatch):
    import neqsim_cli

    template = tmp_path / "brand.docx"
    template.write_bytes(b"PK")
    saved_root = new_task.save_default_task_root(str(tmp_path / "tasks"))

    monkeypatch.setattr(sys, "argv", ["neqsim", "--set-report-template", str(template)])
    with pytest.raises(SystemExit) as exit_info:
        neqsim_cli.main()
    assert exit_info.value.code == 0

    assert json.loads(defaults.read_text()) == {
        "task_root": saved_root, "report_template": str(template)}

    monkeypatch.setattr(sys, "argv", ["neqsim", "--reset-task-root"])
    with pytest.raises(SystemExit):
        neqsim_cli.main()
    assert new_task.resolve_report_template() == str(template)

    monkeypatch.setattr(sys, "argv", ["neqsim", "--reset-report-template"])
    with pytest.raises(SystemExit):
        neqsim_cli.main()
    assert new_task.resolve_report_template() is None
    assert not defaults.exists()


def test_report_template_cli_rejects_unusable_path(defaults, tmp_path, monkeypatch, capsys):
    import neqsim_cli

    monkeypatch.setattr(sys, "argv", ["neqsim", "--set-report-template",
                                      str(tmp_path / "missing.docx")])
    with pytest.raises(SystemExit) as exit_info:
        neqsim_cli.main()
    assert exit_info.value.code == 2
    assert "not found" in capsys.readouterr().out
    assert not defaults.exists()


def test_document_root_precedence_and_validation(defaults, tmp_path, monkeypatch):
    documents = tmp_path / "engineering documents"
    (documents / "stid").mkdir(parents=True)
    assert new_task.resolve_document_root() is None
    assert new_task.save_default_document_root(str(documents)) == str(documents)
    assert json.loads(defaults.read_text())["document_root"] == str(documents)
    assert new_task.resolve_document_root() == str(documents)

    other = tmp_path / "shared documents"
    other.mkdir()
    monkeypatch.setenv("NEQSIM_DOCUMENT_ROOT", str(other))
    assert new_task.resolve_document_root() == str(other)
    monkeypatch.delenv("NEQSIM_DOCUMENT_ROOT")

    with pytest.raises(ValueError, match="not found"):
        new_task.resolve_document_root(str(tmp_path / "missing documents"))


def test_find_documents_walks_subfolders(defaults, tmp_path):
    documents = tmp_path / "documents"
    (documents / "stid" / "pid").mkdir(parents=True)
    (documents / "datasheet.pdf").write_text("a", encoding="utf-8")
    (documents / "stid" / "pid" / "C-12345.pdf").write_text("b", encoding="utf-8")
    (documents / ".hidden.pdf").write_text("c", encoding="utf-8")
    new_task.save_default_document_root(str(documents))

    assert new_task.find_documents() == [
        str(documents / "datasheet.pdf"),
        str(documents / "stid" / "pid" / "C-12345.pdf"),
    ]
    assert new_task.find_documents("c-123") == [
        str(documents / "stid" / "pid" / "C-12345.pdf")]
    assert new_task.find_documents(limit=1) == [str(documents / "datasheet.pdf")]


def test_document_root_cli_and_listing(defaults, tmp_path, monkeypatch, capsys):
    import neqsim_cli

    documents = tmp_path / "documents"
    (documents / "vendor").mkdir(parents=True)
    (documents / "vendor" / "curve.pdf").write_text("x", encoding="utf-8")

    monkeypatch.setattr(sys, "argv", ["neqsim", "--set-document-root", str(documents)])
    with pytest.raises(SystemExit) as exit_info:
        neqsim_cli.main()
    assert exit_info.value.code == 0
    assert new_task.resolve_document_root() == str(documents)

    monkeypatch.setattr(sys, "argv", ["neqsim", "documents", "curve"])
    with pytest.raises(SystemExit) as exit_info:
        neqsim_cli.main()
    assert exit_info.value.code == 0
    assert "curve.pdf" in capsys.readouterr().out

    monkeypatch.setattr(sys, "argv", ["neqsim", "--reset-document-root"])
    with pytest.raises(SystemExit):
        neqsim_cli.main()
    assert new_task.resolve_document_root() is None


def test_document_root_cli_rejects_missing_folder(defaults, tmp_path, monkeypatch, capsys):
    import neqsim_cli

    monkeypatch.setattr(sys, "argv", ["neqsim", "--set-document-root",
                                      str(tmp_path / "missing")])
    with pytest.raises(SystemExit) as exit_info:
        neqsim_cli.main()
    assert exit_info.value.code == 2
    assert "not found" in capsys.readouterr().out
    assert not defaults.exists()


def test_setting_accepts_bare_flag_and_unquoted_path(defaults, tmp_path, monkeypatch):
    import neqsim_cli

    documents = tmp_path / "equinor work" / "TR and standards"
    documents.mkdir(parents=True)
    # A path typed without quotes reaches the CLI as several arguments.
    monkeypatch.setattr(sys, "argv",
                        ["neqsim", "set-document-root"] + str(documents).split(" "))
    with pytest.raises(SystemExit) as exit_info:
        neqsim_cli.main()
    assert exit_info.value.code == 0
    assert new_task.resolve_document_root() == str(documents)

    monkeypatch.setattr(sys, "argv", ["neqsim", "set-task-root"] + str(tmp_path / "my tasks").split(" "))
    with pytest.raises(SystemExit):
        neqsim_cli.main()
    assert new_task.resolve_task_root() == str(tmp_path / "my tasks")


def test_task_records_document_root_when_set_and_unset(defaults, tmp_path):
    documents = tmp_path / "documents"
    documents.mkdir()
    new_task.save_default_task_root(str(tmp_path / "tasks"))

    unset = Path(new_task.create_task("No document library"))
    config = (unset / "study_config.yaml").read_text(encoding="utf-8")
    assert 'document_root: ""' in config

    new_task.save_default_document_root(str(documents))
    configured = Path(new_task.create_task("With document library"))
    config = (configured / "study_config.yaml").read_text(encoding="utf-8")
    assert 'document_root: "{}"'.format(str(documents).replace("\\", "\\\\")) in config