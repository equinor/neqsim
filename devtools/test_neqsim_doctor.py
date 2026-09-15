"""Health-check regressions for CLI installation before building the JAR."""
import os
import sysconfig

import pytest

import neqsim_doctor as doctor


@pytest.fixture
def isolated_doctor(tmp_path, monkeypatch):
    """Keep the JAR check real while isolating unrelated host diagnostics."""
    monkeypatch.setattr(doctor, "PROJECT_ROOT", str(tmp_path))
    monkeypatch.setattr(doctor, "_results", [])
    for name in (
        "check_java", "check_maven", "check_python_neqsim", "check_agent_files",
        "check_cross_tool_files", "check_devtools", "check_task_root",
        "check_report_template", "check_document_root", "check_git",
        "check_cli_on_path",
    ):
        monkeypatch.setattr(doctor, name, lambda: None)
    return tmp_path


@pytest.mark.parametrize("build_state", ["missing", "empty", "sources_only"])
def test_default_doctor_requires_a_runtime_jar(isolated_doctor, build_state):
    target = isolated_doctor / "target"
    if build_state != "missing":
        target.mkdir()
    if build_state == "sources_only":
        (target / "neqsim-3.20.0-sources.jar").touch()

    assert doctor.main([]) == 1
    assert any(result["name"] == "JAR built" and not result["passed"]
               for result in doctor._results)


def test_skip_jar_is_explicit_and_does_not_leave_stale_failures(isolated_doctor, capsys):
    assert doctor.main([]) == 1
    assert doctor.main(["--skip-jar"]) == 0
    assert len(doctor._results) == 1
    assert "Not checked (--skip-jar)" in capsys.readouterr().out


def test_skip_jar_keeps_other_health_failures_blocking(isolated_doctor, monkeypatch):
    monkeypatch.setattr(
        doctor, "check_document_root",
        lambda: doctor._check("Document root", False, "Configured folder is missing"),
    )

    assert doctor.main(["--skip-jar"]) == 1
    assert any(result["name"] == "Document root" and not result["passed"]
               for result in doctor._results)


def test_cli_not_on_path_is_reported_with_the_module_fallback(monkeypatch, capsys):
    """A working `python -m neqsim_cli` must not hide a broken `neqsim` command."""
    monkeypatch.setattr(doctor, "_results", [])
    monkeypatch.setattr(doctor.shutil, "which", lambda name: None)

    doctor.check_cli_on_path()

    result = next(r for r in doctor._results if r["name"] == "'neqsim' command")
    assert not result["passed"]
    assert "-m neqsim_cli" in result["fix_hint"]
    assert "new terminal" in capsys.readouterr().out.lower()


def test_cli_on_path_passes_and_reports_the_resolved_location(monkeypatch):
    """The check must report where the command actually resolves from."""
    monkeypatch.setattr(doctor, "_results", [])
    resolved = os.path.join(sysconfig.get_path("scripts"), "neqsim")
    monkeypatch.setattr(doctor.shutil, "which", lambda name: resolved)

    doctor.check_cli_on_path()

    names = [r["name"] for r in doctor._results]
    assert names == ["'neqsim' command"]  # no interpreter-mismatch warning
    assert doctor._results[0]["passed"]
    assert doctor._results[0]["message"] == resolved


def test_cli_from_a_different_interpreter_is_flagged(monkeypatch, tmp_path):
    """A `neqsim` belonging to another install silently shadows this one."""
    monkeypatch.setattr(doctor, "_results", [])
    monkeypatch.setattr(
        doctor.shutil, "which", lambda name: str(tmp_path / "other" / "neqsim"))

    doctor.check_cli_on_path()

    assert any(r["name"] == "'neqsim' interpreter" for r in doctor._results)
