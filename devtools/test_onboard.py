"""Regression tests for the onboarding wizard."""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))

import onboard


def test_step_verify_fallback_script_does_not_format_inner_density_placeholder(monkeypatch):
    """Fallback script generation should not crash before _run_cmd executes it."""
    calls = []

    def fake_run_cmd(cmd, cwd=None, timeout=300):
        calls.append(cmd)
        if cmd[:3] == [sys.executable, "-c", "import neqsim"]:
            return False, "", "No Java runtime"
        if len(cmd) >= 3 and cmd[0] == sys.executable and cmd[1] == "-c":
            return False, "", "No Java runtime"
        return False, "", "unexpected command"

    monkeypatch.setattr(onboard, "_run_cmd", fake_run_cmd)

    ok = onboard.step_verify(check_only=False)

    assert not ok
    assert len(calls) == 2
    assert "SUCCESS density=" in calls[1][2]
    assert "{d:.2f}" not in calls[1][2]
