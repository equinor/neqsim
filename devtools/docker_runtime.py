"""Start an installed local Docker runtime on demand and verify engine readiness."""
from __future__ import annotations

import argparse
import math
import os
from pathlib import Path
import shutil
import subprocess
import sys
import time


class DockerRuntimeError(RuntimeError):
    """Docker is missing, inaccessible, or could not be started."""


def _windows_desktops() -> list[Path]:
    """Return standard machine-wide and per-user Desktop executable locations."""
    candidates = []
    program_files = os.environ.get("ProgramFiles")
    local_app_data = os.environ.get("LOCALAPPDATA")
    if program_files:
        candidates.append(Path(program_files) / "Docker" / "Docker" / "Docker Desktop.exe")
    if local_app_data:
        root = Path(local_app_data)
        candidates.extend([
            root / "Programs" / "DockerDesktop" / "Docker Desktop.exe",
            root / "Programs" / "Docker" / "Docker" / "Docker Desktop.exe",
            root / "Docker" / "Docker" / "Docker Desktop.exe",
        ])
    return candidates


def _docker_cli() -> str:
    """Find the CLI, including standard Docker Desktop installs off PATH."""
    executable = shutil.which("docker")
    if executable:
        return executable
    candidates = []
    if sys.platform == "win32":
        candidates.extend(
            desktop.parent / "resources" / "bin" / "docker.exe"
            for desktop in _windows_desktops())
    elif sys.platform == "darwin":
        candidates.extend([
            Path("/Applications/Docker.app/Contents/Resources/bin/docker"),
            Path.home() / "Applications" / "Docker.app" / "Contents" / "Resources" / "bin" / "docker",
        ])
    for candidate in candidates:
        if candidate.is_file():
            return str(candidate)
    raise DockerRuntimeError(
        "Docker CLI not found. Install Docker Desktop (Windows/macOS) or Docker Engine "
        "(Linux), then retry. No software was installed automatically.")


def _run(args: list[str], deadline: float) -> subprocess.CompletedProcess:
    """Run a bounded Docker/service command without a shell or elevation."""
    remaining = deadline - time.monotonic()
    if remaining <= 0:
        raise DockerRuntimeError("Docker readiness deadline expired.")
    try:
        return subprocess.run(
            args, capture_output=True, text=True, timeout=min(10, remaining), check=False)
    except subprocess.TimeoutExpired as exc:
        raise DockerRuntimeError("Docker command timed out: " + " ".join(args)) from exc
    except OSError as exc:
        raise DockerRuntimeError("Could not execute {}: {}".format(args[0], exc)) from exc


def _local_endpoint(docker: str, deadline: float) -> None:
    """Refuse to start a local runtime for a remote or unresolved Docker context."""
    host = os.environ.get("DOCKER_HOST", "")
    if os.environ.get("DOCKER_CONTEXT") or not host:
        context = _run(
            [docker, "context", "inspect", "--format", "{{.Endpoints.docker.Host}}"], deadline)
        if context.returncode:
            raise DockerRuntimeError(
                "Cannot resolve the selected Docker context: " + context.stderr.strip())
        host = context.stdout.strip()
    if not host.startswith(("unix://", "npipe://")):
        raise DockerRuntimeError(
            "Selected Docker endpoint is remote or unknown ({}). Restore its connectivity; "
            "a local runtime will not be started or the context changed.".format(host))


def _start_local(deadline: float) -> None:
    """Launch installed Desktop or start a Linux service without sudo."""
    if sys.platform == "win32":
        desktop = next((path for path in _windows_desktops() if path.is_file()), None)
        if desktop is None:
            raise DockerRuntimeError(
                "Docker Desktop executable not found in standard installation locations. "
                "Open your installed Docker Desktop manually, then retry.")
        try:
            subprocess.Popen([str(desktop)], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        except OSError as exc:
            raise DockerRuntimeError("Could not start Docker Desktop: {}".format(exc)) from exc
        return
    if sys.platform == "darwin":
        command = ["open", "-a", "Docker"]
    elif sys.platform.startswith("linux"):
        systemctl = shutil.which("systemctl")
        if not systemctl:
            raise DockerRuntimeError(
                "No supported Docker service launcher found. Start your installed runtime "
                "manually (for example in WSL without systemd), then retry.")
        desktop = _run(
            [systemctl, "--user", "list-unit-files", "docker-desktop.service", "--no-legend"],
            deadline)
        if desktop.returncode == 0 and any(
                line.split() and line.split()[0] == "docker-desktop.service"
                for line in desktop.stdout.splitlines()):
            command = [systemctl, "--user", "--no-ask-password", "start", "docker-desktop.service"]
        else:
            command = [systemctl, "--no-ask-password", "start", "docker.service"]
    else:
        raise DockerRuntimeError("Automatic Docker startup is unsupported on " + sys.platform)
    result = _run(command, deadline)
    if result.returncode:
        raise DockerRuntimeError(
            "Docker startup failed: {}. Complete any Desktop first-run, licence, WSL, "
            "or service-permission setup manually, then retry.".format(
                result.stderr.strip() or result.stdout.strip()))


def ensure_docker(timeout: float = 120, *, require_linux: bool = False) -> str:
    """Return the usable CLI path, starting an installed local runtime if necessary.

    Never installs Docker, elevates privileges, switches contexts/container modes,
    pulls images, or stops a runtime. The timeout bounds startup and readiness.
    """
    if not math.isfinite(timeout) or timeout <= 0:
        raise ValueError("Docker readiness timeout must be a positive finite number.")
    deadline = time.monotonic() + timeout
    docker = _docker_cli()

    def probe() -> subprocess.CompletedProcess:
        """Read the engine OS, not merely the CLI version."""
        try:
            return _run([docker, "info", "--format", "{{.OSType}}"], deadline)
        except DockerRuntimeError as exc:
            if isinstance(exc.__cause__, subprocess.TimeoutExpired):
                return subprocess.CompletedProcess([docker, "info"], 1, "", str(exc))
            raise

    result = probe()
    if result.returncode:
        _local_endpoint(docker, deadline)
        _start_local(deadline)
        while result.returncode:
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                raise DockerRuntimeError(
                    "Docker did not become ready within {:g}s. Last engine error: {}. "
                    "Check Docker Desktop first-run/licence prompts, WSL and Docker socket "
                    "permissions; Docker has been left running.".format(
                        timeout, result.stderr.strip() or result.stdout.strip()))
            time.sleep(min(2, remaining))
            if time.monotonic() >= deadline:
                continue
            result = probe()
    engine_os = result.stdout.strip()
    if engine_os not in ("linux", "windows"):
        raise DockerRuntimeError("Docker returned an unrecognized engine OS: " + repr(engine_os))
    if require_linux and engine_os != "linux":
        raise DockerRuntimeError(
            "Docker is running Windows containers; this task requires Linux containers. "
            "Switch to Linux containers manually, then retry. No mode was changed.")
    return docker


def main(argv: list[str] | None = None) -> int:
    """Expose readiness checks as ``neqsim docker ensure``."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=["ensure"])
    parser.add_argument("--timeout", type=float, default=120, help="Readiness deadline in seconds")
    parser.add_argument("--linux", action="store_true", help="Require a Linux container engine")
    args = parser.parse_args(argv)
    try:
        executable = ensure_docker(args.timeout, require_linux=args.linux)
    except (DockerRuntimeError, ValueError) as exc:
        print("Docker unavailable: {}".format(exc), file=sys.stderr)
        return 1
    print("Docker engine ready. CLI: " + executable)
    return 0


if __name__ == "__main__":
    sys.exit(main())
