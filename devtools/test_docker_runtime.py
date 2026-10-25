"""Docker startup tests never launch Docker or change the host."""
import subprocess
import sys
from pathlib import Path
from unittest.mock import Mock

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parent))
import docker_runtime as dr


def result(code=0, stdout="linux\n", stderr=""):
    return subprocess.CompletedProcess([], code, stdout, stderr)


@pytest.fixture
def runtime(monkeypatch):
    monkeypatch.setattr(dr, "_docker_cli", lambda: "docker")
    run = Mock(return_value=result())
    start = Mock()
    monkeypatch.setattr(dr, "_run", run)
    monkeypatch.setattr(dr, "_start_local", start)
    monkeypatch.setattr(dr.time, "sleep", lambda seconds: None)
    monkeypatch.delenv("DOCKER_HOST", raising=False)
    monkeypatch.delenv("DOCKER_CONTEXT", raising=False)
    return run, start


def test_running_engine_is_not_started(runtime):
    run, start = runtime
    assert dr.ensure_docker(require_linux=True) == "docker"
    start.assert_not_called()
    assert run.call_args.args[0] == ["docker", "info", "--format", "{{.OSType}}"]


@pytest.mark.parametrize("host", ["unix:///var/run/docker.sock", "npipe:////./pipe/docker_engine"])
def test_stopped_local_engine_is_started_once_and_waited_for(runtime, host):
    run, start = runtime
    run.side_effect = [result(1), result(stdout=host), result(1), result()]
    assert dr.ensure_docker(require_linux=True) == "docker"
    start.assert_called_once()
    assert run.call_count == 4


@pytest.mark.parametrize("host", ["ssh://remote", "tcp://server:2376", ""])
def test_unhealthy_remote_or_unknown_context_does_not_start_local(runtime, host):
    run, start = runtime
    run.side_effect = [result(1), result(stdout=host)]
    with pytest.raises(dr.DockerRuntimeError, match="remote or unknown"):
        dr.ensure_docker()
    start.assert_not_called()


def test_docker_host_is_respected(runtime, monkeypatch):
    run, start = runtime
    monkeypatch.setenv("DOCKER_HOST", "tcp://server:2376")
    run.return_value = result(1)
    with pytest.raises(dr.DockerRuntimeError, match="remote"):
        dr.ensure_docker()
    assert run.call_count == 1
    start.assert_not_called()


def test_explicit_context_overrides_docker_host(runtime, monkeypatch):
    run, start = runtime
    monkeypatch.setenv("DOCKER_HOST", "tcp://server:2376")
    monkeypatch.setenv("DOCKER_CONTEXT", "desktop-linux")
    run.side_effect = [result(1), result(stdout="unix:///socket"), result()]
    dr.ensure_docker()
    start.assert_called_once()


def test_bad_context_is_reported_without_start(runtime):
    run, start = runtime
    run.side_effect = [result(1), result(1, "", "context missing")]
    with pytest.raises(dr.DockerRuntimeError, match="context missing"):
        dr.ensure_docker()
    start.assert_not_called()


@pytest.mark.parametrize("require_linux", [False, True])
def test_windows_engine_is_not_switched(runtime, require_linux):
    run, start = runtime
    run.return_value = result(stdout="windows")
    if require_linux:
        with pytest.raises(dr.DockerRuntimeError, match="requires Linux"):
            dr.ensure_docker(require_linux=True)
    else:
        assert dr.ensure_docker() == "docker"
    start.assert_not_called()


def test_unknown_engine_os_is_not_success(runtime):
    run, _ = runtime
    run.return_value = result(stdout="")
    with pytest.raises(dr.DockerRuntimeError, match="unrecognized"):
        dr.ensure_docker()


@pytest.mark.parametrize("timeout", [0, -1, float("nan"), float("inf")])
def test_invalid_timeout_is_rejected_before_commands(runtime, timeout):
    run, start = runtime
    with pytest.raises(ValueError, match="positive finite"):
        dr.ensure_docker(timeout)
    run.assert_not_called()
    start.assert_not_called()


def test_readiness_timeout_has_last_error_and_is_bounded(runtime, monkeypatch):
    run, start = runtime
    clock = [0.0]
    monkeypatch.setattr(dr.time, "monotonic", lambda: clock[0])
    monkeypatch.setattr(dr.time, "sleep", lambda seconds: clock.__setitem__(0, clock[0] + seconds))
    run.side_effect = [result(1), result(stdout="unix:///socket"), result(1, "", "still starting")]
    with pytest.raises(dr.DockerRuntimeError, match="still starting"):
        dr.ensure_docker(timeout=3)
    assert clock[0] == 3
    start.assert_called_once()
    assert run.call_count == 3


def test_transient_info_timeout_is_retried(runtime):
    run, start = runtime
    timeout = dr.DockerRuntimeError("probe timeout")
    timeout.__cause__ = subprocess.TimeoutExpired("docker info", 10)
    run.side_effect = [timeout, result(stdout="unix:///socket"), timeout, result()]
    assert dr.ensure_docker() == "docker"
    start.assert_called_once()


def test_missing_cli_is_explicit(monkeypatch):
    monkeypatch.setattr(dr.shutil, "which", lambda name: None)
    monkeypatch.setattr(dr.sys, "platform", "linux")
    with pytest.raises(dr.DockerRuntimeError, match="not found"):
        dr._docker_cli()


def test_windows_desktop_and_cli_found_off_path(monkeypatch, tmp_path):
    monkeypatch.setattr(dr.sys, "platform", "win32")
    monkeypatch.setattr(dr.shutil, "which", lambda name: None)
    monkeypatch.setenv("ProgramFiles", str(tmp_path))
    desktop = tmp_path / "Docker" / "Docker" / "Docker Desktop.exe"
    cli = desktop.parent / "resources" / "bin" / "docker.exe"
    cli.parent.mkdir(parents=True)
    cli.touch()
    desktop.touch()
    popen = Mock()
    monkeypatch.setattr(dr.subprocess, "Popen", popen)
    assert dr._docker_cli() == str(cli)
    dr._start_local(100)
    assert popen.call_args.args[0] == [str(desktop)]


def test_windows_missing_desktop_is_not_installed(monkeypatch, tmp_path):
    monkeypatch.setattr(dr.sys, "platform", "win32")
    monkeypatch.setenv("ProgramFiles", str(tmp_path))
    monkeypatch.delenv("LOCALAPPDATA", raising=False)
    with pytest.raises(dr.DockerRuntimeError, match="executable not found"):
        dr._start_local(100)


def test_windows_per_user_install_and_launch_error(monkeypatch, tmp_path):
    monkeypatch.setattr(dr.sys, "platform", "win32")
    monkeypatch.setattr(dr.shutil, "which", lambda name: None)
    monkeypatch.delenv("ProgramFiles", raising=False)
    monkeypatch.setenv("LOCALAPPDATA", str(tmp_path))
    desktop = tmp_path / "Programs" / "DockerDesktop" / "Docker Desktop.exe"
    cli = desktop.parent / "resources" / "bin" / "docker.exe"
    cli.parent.mkdir(parents=True)
    cli.touch()
    desktop.touch()
    monkeypatch.setattr(dr.subprocess, "Popen", Mock(side_effect=OSError("access denied")))
    assert dr._docker_cli() == str(cli)
    with pytest.raises(dr.DockerRuntimeError, match="access denied"):
        dr._start_local(100)


def test_healthy_remote_engine_is_reused(runtime, monkeypatch):
    monkeypatch.setenv("DOCKER_HOST", "ssh://remote")
    run, start = runtime
    assert dr.ensure_docker() == "docker"
    assert run.call_count == 1
    start.assert_not_called()


@pytest.mark.parametrize("platform,desktop,expected", [
    ("darwin", False, ["open", "-a", "Docker"]),
    ("linux", True, ["systemctl", "--user", "--no-ask-password", "start", "docker-desktop.service"]),
    ("linux", False, ["systemctl", "--no-ask-password", "start", "docker.service"]),
])
def test_platform_startup_commands(monkeypatch, platform, desktop, expected):
    monkeypatch.setattr(dr.sys, "platform", platform)
    monkeypatch.setattr(dr.shutil, "which", lambda name: name)
    run = Mock(side_effect=[
        result(stdout="docker-desktop.service enabled\n" if desktop else ""), result()
    ] if platform == "linux" else [result()])
    monkeypatch.setattr(dr, "_run", run)
    dr._start_local(100)
    assert run.call_args.args[0] == expected


def test_start_failure_is_actionable(monkeypatch):
    monkeypatch.setattr(dr.sys, "platform", "darwin")
    monkeypatch.setattr(dr, "_run", Mock(return_value=result(1, "", "application missing")))
    with pytest.raises(dr.DockerRuntimeError, match="application missing"):
        dr._start_local(100)


def test_wsl_without_systemd_is_explicit(monkeypatch):
    monkeypatch.setattr(dr.sys, "platform", "linux")
    monkeypatch.setattr(dr.shutil, "which", lambda name: None)
    with pytest.raises(dr.DockerRuntimeError, match="without systemd"):
        dr._start_local(100)


def test_subprocess_deadline_and_no_shell(monkeypatch):
    monkeypatch.setattr(dr.time, "monotonic", lambda: 95)
    run = Mock(return_value=result())
    monkeypatch.setattr(dr.subprocess, "run", run)
    dr._run(["docker", "info"], 100)
    assert run.call_args.kwargs["timeout"] == 5
    assert "shell" not in run.call_args.kwargs


@pytest.mark.parametrize("error", [OSError("denied"), subprocess.TimeoutExpired("docker", 1)])
def test_command_errors_are_explicit(monkeypatch, error):
    monkeypatch.setattr(dr.subprocess, "run", Mock(side_effect=error))
    with pytest.raises(dr.DockerRuntimeError):
        dr._run(["docker", "info"], dr.time.monotonic() + 5)


def test_cli_failure_returns_nonzero(runtime, capsys):
    run, _ = runtime
    run.return_value = result(stdout="windows")
    assert dr.main(["ensure", "--linux"]) == 1
    assert "requires Linux" in capsys.readouterr().err


def test_cli_success_passes_options(monkeypatch, capsys):
    ensure = Mock(return_value="docker")
    monkeypatch.setattr(dr, "ensure_docker", ensure)
    assert dr.main(["ensure", "--timeout", "180", "--linux"]) == 0
    ensure.assert_called_once_with(180, require_linux=True)
    assert "engine ready" in capsys.readouterr().out


def test_unified_cli_dispatch_and_toolkit_packaging(monkeypatch):
    import neqsim_cli
    import tomllib

    main = Mock(return_value=0)
    monkeypatch.setattr(dr, "main", main)
    monkeypatch.setattr(sys, "argv", ["neqsim", "docker", "ensure", "--linux"])
    monkeypatch.setattr(neqsim_cli.os, "chdir", lambda path: None)
    neqsim_cli.main()
    assert sys.argv == ["neqsim docker", "ensure", "--linux"]
    main.assert_called_once()
    manifest = tomllib.loads((Path(__file__).parent / "pyproject.toml").read_text())
    assert "docker_runtime" in manifest["tool"]["setuptools"]["py-modules"]
