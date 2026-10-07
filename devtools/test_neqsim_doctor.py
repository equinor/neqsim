"""Health-check regressions for CLI installation before building the JAR."""
import os
import subprocess
import sysconfig

import pytest

import neqsim_doctor as doctor


@pytest.mark.parametrize("recovered", [True, False])
def test_java_startup_timeout_is_retried_once(monkeypatch, recovered):
    """A slow cold JVM launch gets one bounded retry; persistent timeouts still fail."""
    monkeypatch.setattr(doctor, "_results", [])
    monkeypatch.setattr(doctor.shutil, "which", lambda name: "/jdk/bin/java")
    monkeypatch.setattr(doctor, "_java_home_is_valid", lambda: (False, ""))
    monkeypatch.setattr(doctor, "_java_missing_hint", lambda: "Check the JVM")
    monkeypatch.delenv("JAVA_HOME", raising=False)
    calls = []

    def run_java(command, **kwargs):
        calls.append(kwargs["timeout"])
        if len(calls) == 1 or not recovered:
            raise subprocess.TimeoutExpired(command, kwargs["timeout"])
        return subprocess.CompletedProcess(command, 0, "", 'openjdk version "17.0.20"')

    monkeypatch.setattr(doctor.subprocess, "run", run_java)
    doctor.check_java()

    assert calls == [10, 30]
    installed = next(result for result in doctor._results if result["name"] == "Java installed")
    assert installed["passed"] is recovered
    if recovered:
        assert any(result["name"] == "Java version >= 8" and result["passed"]
                   for result in doctor._results)


def test_java_nonzero_exit_is_a_health_failure(monkeypatch):
    """A broken JVM executable must not be reported as a successful installation."""
    monkeypatch.setattr(doctor, "_results", [])
    monkeypatch.setattr(doctor.shutil, "which", lambda name: "/jdk/bin/java")
    monkeypatch.setattr(doctor, "_java_home_is_valid", lambda: (False, ""))
    monkeypatch.setattr(doctor, "_java_missing_hint", lambda: "Check the JVM")
    monkeypatch.delenv("JAVA_HOME", raising=False)
    monkeypatch.setattr(doctor.subprocess, "run", lambda command, **kwargs:
                        subprocess.CompletedProcess(command, 1, "", "Could not create the JVM"))

    doctor.check_java()

    installed = next(result for result in doctor._results if result["name"] == "Java installed")
    assert not installed["passed"]


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
