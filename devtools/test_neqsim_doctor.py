"""Health-check regressions for CLI installation before building the JAR."""
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
