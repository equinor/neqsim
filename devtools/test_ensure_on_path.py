"""Regressions for the PATH advice printed at the end of an install.

The install prints one line that the user acts on. When that line claims
success it is believed, so a wrong claim costs far more than a missing one.
"""
import os

import pytest

import ensure_on_path


def _venv_scripts(venv):
    """Return the venv sub-directory that holds console scripts on this OS."""
    return os.path.join(venv, "Scripts" if os.name == "nt" else "bin")


@pytest.fixture
def active_venv(tmp_path, monkeypatch):
    """Point VIRTUAL_ENV at a scratch venv that holds the console script."""
    venv = str(tmp_path / "venv")
    scripts = _venv_scripts(venv)
    os.makedirs(scripts)
    monkeypatch.setenv("VIRTUAL_ENV", venv)
    monkeypatch.setattr(ensure_on_path, "find_script_dir", lambda: scripts)
    return venv


def test_venv_not_actually_activated_is_not_reported_as_working(
        active_venv, monkeypatch, capsys):
    """VIRTUAL_ENV set without activation must not be called 'on PATH'.

    VS Code sets VIRTUAL_ENV for a detected interpreter without running the
    activate script, so the Scripts folder can be absent from PATH.
    """
    monkeypatch.setattr(ensure_on_path, "_dir_on_path", lambda d: False)

    ensure_on_path.main()

    out = capsys.readouterr().out
    assert "NOT on PATH" in out
    assert "already on PATH" not in out
    assert "Activate it" in out
    assert "Restarting the machine will not help." in out


def test_activated_venv_states_the_per_terminal_scope(
        active_venv, monkeypatch, capsys):
    """A venv install works per terminal; say so instead of implying it is global."""
    monkeypatch.setattr(ensure_on_path, "_dir_on_path", lambda d: True)

    ensure_on_path.main()

    out = capsys.readouterr().out
    assert "is on PATH" in out
    assert "ONLY in terminals where this virtualenv is activated" in out
    assert "Restarting the machine does not change this" in out


def test_venv_location_is_never_written_to_the_user_path(
        active_venv, monkeypatch):
    """An ephemeral venv path must not be persisted to the user PATH."""
    def _fail(directory):
        raise AssertionError("persisted venv path: " + directory)

    monkeypatch.setattr(ensure_on_path, "_dir_on_path", lambda d: False)
    monkeypatch.setattr(ensure_on_path, "_add_to_windows_user_path", _fail)
    monkeypatch.setattr(ensure_on_path, "_add_to_posix_path", _fail)

    ensure_on_path.main()


def test_activate_command_covers_both_windows_shells(tmp_path):
    """Picking the wrong activate script fails quietly, so show both."""
    command = ensure_on_path._activate_command(str(tmp_path))

    if os.name == "nt":
        assert "Activate.ps1" in command and "activate.bat" in command
    else:
        assert command.startswith("source ")


def test_print_script_dir_emits_only_the_path(monkeypatch, capsys):
    """The installers parse this, so it must stay a single bare line."""
    monkeypatch.setattr(ensure_on_path, "find_script_dir", lambda: r"C:\some\Scripts")
    monkeypatch.setattr(ensure_on_path.sys, "argv",
                        ["ensure_on_path.py", "--print-script-dir"])

    ensure_on_path.main()

    assert capsys.readouterr().out.strip() == r"C:\some\Scripts"


def test_print_script_dir_prints_nothing_when_not_installed(monkeypatch, capsys):
    """An empty result must not become a PATH entry in the calling installer."""
    monkeypatch.setattr(ensure_on_path, "find_script_dir", lambda: None)
    monkeypatch.setattr(ensure_on_path.sys, "argv",
                        ["ensure_on_path.py", "--print-script-dir"])

    ensure_on_path.main()

    assert capsys.readouterr().out.strip() == ""
