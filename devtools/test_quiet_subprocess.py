"""Tests for quiet_subprocess: hidden console windows for toolkit child processes."""

import subprocess

import quiet_subprocess


def _install_with_recorder(monkeypatch, visible):
    calls = []

    def recorder(self, *args, **kwargs):
        calls.append(kwargs)

    monkeypatch.setattr(subprocess.Popen, "__init__", recorder)
    monkeypatch.setattr(quiet_subprocess.os, "name", "nt")
    monkeypatch.setattr(quiet_subprocess, "_installed", False)
    monkeypatch.setattr(quiet_subprocess, "_has_visible_console", lambda: visible)
    monkeypatch.delenv("NEQSIM_SHOW_CONSOLE_WINDOWS", raising=False)
    return calls, quiet_subprocess.install()


def test_headless_process_gets_no_window_flag(monkeypatch):
    calls, active = _install_with_recorder(monkeypatch, visible=False)
    assert active
    subprocess.Popen(["tool"])
    subprocess.Popen(["tool"], creationflags=0x10)
    assert calls[0]["creationflags"] == quiet_subprocess.CREATE_NO_WINDOW
    assert calls[1]["creationflags"] == 0x10


def test_visible_console_is_left_untouched(monkeypatch):
    calls, active = _install_with_recorder(monkeypatch, visible=True)
    assert not active
    subprocess.Popen(["tool"])
    assert "creationflags" not in calls[0]


def test_opt_out_environment_variable(monkeypatch):
    calls, _ = _install_with_recorder(monkeypatch, visible=False)
    monkeypatch.setattr(quiet_subprocess, "_installed", False)
    monkeypatch.setenv("NEQSIM_SHOW_CONSOLE_WINDOWS", "1")
    assert not quiet_subprocess.install()
