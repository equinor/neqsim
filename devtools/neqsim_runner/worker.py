"""
Isolated subprocess worker for NeqSim simulations.

Each job runs in a fresh Python process with its own JVM. The worker:
1. Reads the job spec from a JSON file (path in NEQSIM_JOB_FILE env)
2. Starts the JVM via neqsim_dev_setup or pip neqsim
3. Executes the user's simulation script
4. Writes results to the output directory
5. Exits cleanly (JVM dies with the process)

If the script supports checkpointing, it reads/writes checkpoint files
at the path in NEQSIM_CHECKPOINT_PATH.
"""

import json
import os
import subprocess
import sys
import signal
from datetime import datetime, timezone
from pathlib import Path

from neqsim_runner.models import Job, JobStatus


# ── Bootstrap script loading ──
# Bootstrap scripts live in bootstrap/ as real .py files (lintable, testable).
# They are loaded at module import time and written into each job's working
# directory before the subprocess is launched.

_BOOTSTRAP_DIR = Path(__file__).parent / "bootstrap"


def _load_bootstrap(name):
    """Load a bootstrap script from the bootstrap/ directory."""
    path = _BOOTSTRAP_DIR / name
    if path.exists():
        return path.read_text(encoding="utf-8")
    raise FileNotFoundError(f"Bootstrap script not found: {path}")


_WORKER_BOOTSTRAP = _load_bootstrap("script_executor.py")
_NOTEBOOK_EXECUTOR = _load_bootstrap("notebook_executor.py")


class WorkerProcess:
    """
    Manages a single worker subprocess that executes a NeqSim job.

    Parameters
    ----------
    job : Job
        The job to execute.
    project_root : str or Path
        Path to the NeqSim project root (for devtools mode).
    output_base : str or Path
        Base directory for job outputs.
    """

    def __init__(self, job, project_root=None, output_base=None):
        self.job = job
        self.project_root = str(project_root) if project_root else None
        self.output_base = Path(output_base) if output_base else Path("task_solve") / "runner_output"
        self.process = None
        self._bootstrap_file = None
        self._log_stdout = None
        self._log_stderr = None

    def _prepare(self):
        """Prepare the job's working directory and spec file."""
        job_dir = self.output_base / self.job.job_id
        job_dir.mkdir(parents=True, exist_ok=True)

        # Choose the bootstrap script based on job type
        if self.job.job_type == "notebook":
            self._bootstrap_file = job_dir / "_notebook_executor.py"
            self._bootstrap_file.write_text(_NOTEBOOK_EXECUTOR, encoding="utf-8")
        else:
            self._bootstrap_file = job_dir / "_worker_bootstrap.py"
            self._bootstrap_file.write_text(_WORKER_BOOTSTRAP, encoding="utf-8")

        # Write the job spec
        spec = {
            "script": os.path.abspath(self.job.script),
            "args": self.job.args,
            "output_dir": str(job_dir / "output"),
            "checkpoint_path": str(job_dir / "checkpoint.json"),
            "timeout": self.job.timeout_seconds,
        }
        if self.project_root:
            spec["project_root"] = self.project_root

        spec_file = job_dir / "_job_spec.json"
        with open(spec_file, "w", encoding="utf-8") as f:
            json.dump(spec, f, indent=2)

        # Store paths on the job
        self.job.result_path = str(job_dir / "output")
        self.job.checkpoint_path = str(job_dir / "checkpoint.json")

        return spec_file, job_dir

    def start(self):
        """
        Launch the worker subprocess.

        Returns
        -------
        subprocess.Popen
            The running worker process.
        """
        spec_file, job_dir = self._prepare()

        env = os.environ.copy()
        env["NEQSIM_JOB_FILE"] = str(spec_file)
        # Disable interactive prompts
        env["PYTHONUNBUFFERED"] = "1"

        # Determine working directory
        workdir = self.job.workdir
        if not workdir:
            workdir = str(Path(self.job.script).resolve().parent)

        log_stdout = open(job_dir / "stdout.log", "w", encoding="utf-8")
        log_stderr = open(job_dir / "stderr.log", "w", encoding="utf-8")

        try:
            # On Windows: CREATE_NEW_PROCESS_GROUP lets taskkill /T work.
            # On Linux/macOS: start_new_session=True creates a new process
            # group so os.killpg() can kill the child tree without hitting
            # the parent (equivalent to setsid).
            popen_kwargs = dict(
                env=env,
                cwd=workdir,
                stdout=log_stdout,
                stderr=log_stderr,
            )
            if sys.platform == "win32":
                popen_kwargs["creationflags"] = subprocess.CREATE_NEW_PROCESS_GROUP
            else:
                popen_kwargs["start_new_session"] = True

            self.process = subprocess.Popen(
                [sys.executable, str(self._bootstrap_file)],
                **popen_kwargs,
            )
            self._log_stdout = log_stdout
            self._log_stderr = log_stderr
        except Exception:
            log_stdout.close()
            log_stderr.close()
            raise

        self.job.pid = self.process.pid
        self.job.status = JobStatus.RUNNING
        self.job.started_at = datetime.now(timezone.utc).isoformat()

        return self.process

    def wait(self, timeout=None):
        """
        Wait for the worker to finish.

        Parameters
        ----------
        timeout : float, optional
            Max seconds to wait. Uses job.timeout_seconds if not given.

        Returns
        -------
        int
            Exit code (0 = success).
        """
        if self.process is None:
            raise RuntimeError("Worker not started")

        effective_timeout = timeout or self.job.timeout_seconds
        try:
            exit_code = self.process.wait(timeout=effective_timeout)
        except subprocess.TimeoutExpired:
            self._kill()
            self.job.status = JobStatus.TIMED_OUT
            self.job.error_message = f"Timed out after {effective_timeout}s"
            self.job.finished_at = datetime.now(timezone.utc).isoformat()
            return -1
        finally:
            self._close_logs()

        self.job.finished_at = datetime.now(timezone.utc).isoformat()

        if exit_code == 0:
            self.job.status = JobStatus.SUCCESS
        else:
            self.job.status = JobStatus.FAILED
            self.job.error_message = f"Worker exited with code {exit_code}"
            # Try to read error details from the status file
            status_file = Path(self.job.result_path) / "_status.json"
            if status_file.exists():
                try:
                    with open(status_file, encoding="utf-8") as f:
                        status_data = json.load(f)
                    if "error" in status_data:
                        self.job.error_message = status_data["error"]
                except (json.JSONDecodeError, IOError):
                    pass

        return exit_code

    def _close_logs(self):
        """Close log file handles to flush output and release resources."""
        for handle in (self._log_stdout, self._log_stderr):
            if handle is not None:
                try:
                    handle.flush()
                    handle.close()
                except OSError:
                    pass
        self._log_stdout = None
        self._log_stderr = None

    def _kill(self):
        """Kill the worker process tree."""
        if self.process is None:
            return
        try:
            if sys.platform == "win32":
                # On Windows, kill the process tree
                subprocess.run(
                    ["taskkill", "/F", "/T", "/PID", str(self.process.pid)],
                    capture_output=True,
                    timeout=10,
                )
            else:
                os.killpg(os.getpgid(self.process.pid), signal.SIGKILL)
        except (OSError, subprocess.SubprocessError):
            try:
                self.process.kill()
            except OSError:
                pass

    def get_output(self):
        """Read the job's output files."""
        if not self.job.result_path:
            return None
        output_dir = Path(self.job.result_path)
        if not output_dir.exists():
            return None

        result = {}
        for f in output_dir.iterdir():
            if f.suffix == ".json":
                try:
                    with open(f) as fh:
                        result[f.name] = json.load(fh)
                except (json.JSONDecodeError, IOError):
                    result[f.name] = f"<unreadable: {f}>"
        return result

    def get_logs(self):
        """Read stdout/stderr logs."""
        job_dir = self.output_base / self.job.job_id
        logs = {}
        for name in ("stdout.log", "stderr.log"):
            log_file = job_dir / name
            if log_file.exists():
                logs[name] = log_file.read_text()
        return logs
