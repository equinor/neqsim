"""Tests for the task correctness gate and the `neqsim report` command.

Covers the two guarantees that make the quality gate meaningful:
  * a Standard/Comprehensive task without independent validation fails under
    ``--enterprise-gate`` but only warns in advisory mode;
  * the canonical report generator can be pointed at any task folder, so a fix
    in devtools reaches tasks that vendored an older generator.
"""
import json
import os
import subprocess
import sys

import pytest

DEVTOOLS = os.path.dirname(os.path.abspath(__file__))
VALIDATOR = os.path.join(DEVTOOLS, "validate_task_results.py")
CLI = os.path.join(DEVTOOLS, "neqsim_cli.py")


def _write_task(root, results):
    """Create a minimal task folder and return its path."""
    task = root / "2026-01-01_gate_test"
    (task / "step1_scope_and_research").mkdir(parents=True)
    (task / "step3_report").mkdir(parents=True)
    (task / "step3_report" / "Report.docx").write_bytes(b"stub")
    (task / "results.json").write_text(json.dumps(results), encoding="utf-8")
    return task


def _run_validator(task, *extra):
    return subprocess.run(
        [sys.executable, VALIDATOR, str(task)] + list(extra),
        capture_output=True, text=True,
    )


BASE_RESULTS = {
    "key_results": {"outlet_temperature_C": 25.0},
    "validation": {"mass_balance_error_pct": 0.01},
    "approach": "SRK EOS flash at fixed T and P.",
    "conclusions": "Outlet temperature is within spec.",
    "uncertainty": {"method": "Monte Carlo", "p50": 1.0},
}


def test_enterprise_gate_fails_unvalidated_standard_task(tmp_path):
    task = _write_task(tmp_path, BASE_RESULTS)

    advisory = _run_validator(task)
    assert advisory.returncode == 0
    assert "engineering validation" in advisory.stdout

    strict = _run_validator(task, "--enterprise-gate")
    assert strict.returncode == 1
    assert "ERROR   engineering validation" in strict.stdout
    assert "enterprise-gate" in strict.stdout


def test_enterprise_gate_accepts_benchmarked_task(tmp_path):
    results = dict(BASE_RESULTS)
    results["benchmark_validation"] = [
        {"case": "NIST methane density", "status": "PASS", "deviation_pct": 0.3}
    ]
    task = _write_task(tmp_path, results)

    strict = _run_validator(task, "--enterprise-gate")
    assert strict.returncode == 0
    assert "engineering validation" not in strict.stdout


def test_enterprise_gate_skips_quick_tasks(tmp_path):
    results = {k: v for k, v in BASE_RESULTS.items() if k != "uncertainty"}
    task = tmp_path / "2026-01-01_quick"
    task.mkdir()
    (task / "results.json").write_text(json.dumps(results), encoding="utf-8")

    strict = _run_validator(task, "--enterprise-gate")
    assert strict.returncode == 0
    assert "engineering validation" not in strict.stdout


def test_report_command_rejects_non_task_folder(tmp_path):
    proc = subprocess.run(
        [sys.executable, CLI, "report", str(tmp_path)],
        capture_output=True, text=True,
    )
    assert proc.returncode == 2
    assert "does not look like a task folder" in proc.stdout


@pytest.mark.skipif(
    not os.path.isfile(
        os.path.join(DEVTOOLS, "task_template", "step3_report", "generate_report.py")
    ),
    reason="report generator template not present",
)
def test_report_command_generates_into_external_task_folder(tmp_path):
    pytest.importorskip("docx")
    task = _write_task(tmp_path, BASE_RESULTS)
    os.remove(str(task / "step3_report" / "Report.docx"))

    proc = subprocess.run(
        [sys.executable, CLI, "report", str(task), "--no-template"],
        capture_output=True, text=True,
    )
    assert proc.returncode == 0, proc.stdout + proc.stderr
    assert (task / "step3_report" / "Report.docx").is_file()
    assert (task / "step3_report" / "Report.html").is_file()
