"""
Agent integration bridge — connects LLM task-solving agents to the runner.

This module provides the glue between the solve-task agent workflow
(``task_solve/`` folders, notebooks, results.json) and the neqsim_runner
execution engine. Instead of running NeqSim notebooks interactively
(which requires manual kernel restarts on failure), the agent can:

1. Convert notebook cells into a standalone job script
2. Submit the job to the runner
3. Poll for completion
4. Read results back

Usage from an LLM agent::

    from neqsim_runner.agent_bridge import AgentBridge

    bridge = AgentBridge(task_dir="task_solve/2026-04-08_my_task")

    # Submit the analysis notebook as a runner job
    job_id = bridge.submit_notebook(
        "step2_analysis/01_analysis.ipynb",
        max_retries=3,
        timeout_seconds=3600,
    )

    # Or submit a standalone script
    job_id = bridge.submit_script(
        "step2_analysis/run_simulation.py",
        args={"pressure": 60.0, "temperature": 25.0},
    )

    # Run all pending jobs (blocking)
    bridge.run_all()

    # Check results
    status = bridge.get_status(job_id)
    results = bridge.get_results(job_id)

    # Get results for a multi-job sweep
    all_results = bridge.collect_results()
"""

import json
import os
import sys
import time
from pathlib import Path

import logging

from neqsim_runner.models import Job, JobStatus
from neqsim_runner.store import JobStore
from neqsim_runner.supervisor import Supervisor
from neqsim_runner.progress import TaskProgress

logger = logging.getLogger("neqsim_runner.agent_bridge")

# Default queue-size limits for rate limiting.
_QUEUE_WARN_THRESHOLD = 100
_QUEUE_HARD_LIMIT = 1000


def _detect_project_root():
    """Auto-detect the NeqSim project root."""
    p = Path(__file__).resolve().parent
    for _ in range(5):
        if (p / "pom.xml").exists():
            return p
        p = p.parent
    return None


class AgentBridge:
    """
    High-level interface for LLM agents to submit and monitor NeqSim jobs.

    Connects the task_solve workflow (task_spec → notebook → results.json)
    to the neqsim_runner execution engine. Each job runs in an isolated
    subprocess — no kernel restarts needed.

    Parameters
    ----------
    task_dir : str or Path
        Path to the task_solve folder (e.g., ``task_solve/2026-04-08_my_task``).
    project_root : str or Path, optional
        NeqSim project root. Auto-detected if not provided.
    db_path : str, optional
        Path to SQLite database. Defaults to ``<task_dir>/runner.db``.
    """

    def __init__(self, task_dir, project_root=None, db_path=None,
                 queue_warn=_QUEUE_WARN_THRESHOLD,
                 queue_limit=_QUEUE_HARD_LIMIT):
        self.task_dir = Path(task_dir).resolve()
        self.project_root = Path(project_root) if project_root else _detect_project_root()
        self.output_dir = self.task_dir / "runner_output"
        self.output_dir.mkdir(parents=True, exist_ok=True)
        self._queue_warn = queue_warn
        self._queue_limit = queue_limit

        if db_path is None:
            db_path = str(self.task_dir / "runner.db")
        self.store = JobStore(db_path)
        self.progress = TaskProgress(self.task_dir)
        self._job_ids = []

    def submit_notebook(self, notebook_path, max_retries=3, timeout_seconds=3600,
                        extra_args=None, mode="execute"):
        """
        Submit a Jupyter notebook for execution.

        Two modes are available:

        - ``"execute"`` (default): Runs the notebook via nbconvert in a
          Jupyter kernel subprocess. Produces an executed ``.ipynb`` with
          all cell outputs (plots, tables, print statements) preserved.
          This is the same notebook you would get from "Run All" in VS Code.

        - ``"script"``: Converts the notebook to a ``.py`` script (code
          cells only) and runs it as a plain Python job. Faster and lighter,
          but you only get JSON results and logs — no executed notebook.

        Parameters
        ----------
        notebook_path : str or Path
            Path to the .ipynb file (absolute or relative to task_dir).
        max_retries : int
            Max retry attempts.
        timeout_seconds : int
            Max wall-clock time per attempt.
        extra_args : dict, optional
            Additional arguments available to the script via get_args().
        mode : str
            ``"execute"`` (default) to produce an executed ``.ipynb``, or
            ``"script"`` to convert to ``.py`` and run headlessly.

        Returns
        -------
        str
            The job ID.
        """
        nb_path = Path(notebook_path)
        if not nb_path.is_absolute():
            nb_path = self.task_dir / nb_path

        if not nb_path.exists():
            raise FileNotFoundError(f"Notebook not found: {nb_path}")

        if mode == "execute":
            # Submit the .ipynb directly — the worker will use nbconvert
            return self._submit(
                script=str(nb_path),
                args=extra_args or {},
                max_retries=max_retries,
                timeout_seconds=timeout_seconds,
                workdir=str(nb_path.parent),
                job_type="notebook",
            )
        elif mode == "script":
            # Convert notebook to .py and run as a regular script job
            script_path = _notebook_to_script(nb_path)
            return self._submit(
                script=str(script_path),
                args=extra_args or {},
                max_retries=max_retries,
                timeout_seconds=timeout_seconds,
                workdir=str(nb_path.parent),
                job_type="script",
            )
        else:
            raise ValueError(f"Unknown mode: {mode!r}. Use 'execute' or 'script'.")

    def submit_script(self, script_path, args=None, max_retries=3,
                      timeout_seconds=3600):
        """
        Submit a Python script as a runner job.

        Parameters
        ----------
        script_path : str or Path
            Path to the .py file.
        args : dict, optional
            Arguments available via NEQSIM_JOB_ARGS / get_args().
        max_retries : int
            Max retry attempts.
        timeout_seconds : int
            Max wall-clock time per attempt.

        Returns
        -------
        str
            The job ID.
        """
        sp = Path(script_path)
        if not sp.is_absolute():
            sp = self.task_dir / sp

        if not sp.exists():
            raise FileNotFoundError(f"Script not found: {sp}")

        return self._submit(
            script=str(sp),
            args=args or {},
            max_retries=max_retries,
            timeout_seconds=timeout_seconds,
            workdir=str(sp.parent),
        )

    def submit_parametric_sweep(self, script_path, parameter_sets,
                                max_retries=2, timeout_seconds=1800):
        """
        Submit multiple jobs with different parameters (sensitivity study).

        Each parameter set becomes a separate job, all running the same script.

        Parameters
        ----------
        script_path : str or Path
            Python script to execute for each case.
        parameter_sets : list of dict
            List of argument dicts, one per job.
        max_retries : int
            Max retries per individual job.
        timeout_seconds : int
            Max time per individual job.

        Returns
        -------
        list of str
            Job IDs.
        """
        job_ids = []
        for i, params in enumerate(parameter_sets):
            params["_case_index"] = i
            jid = self.submit_script(
                script_path, args=params,
                max_retries=max_retries, timeout_seconds=timeout_seconds,
            )
            job_ids.append(jid)
        return job_ids

    def run_all(self, max_parallel=1):
        """
        Run all pending jobs (blocking).

        Parameters
        ----------
        max_parallel : int
            Max concurrent workers.
        """
        supervisor = Supervisor(
            self.store,
            max_parallel=max_parallel,
            project_root=str(self.project_root) if self.project_root else None,
            output_base=str(self.output_dir),
        )
        supervisor.run()

    def get_status(self, job_id):
        """
        Get the current status of a job.

        Returns
        -------
        dict
            Status info: {job_id, status, attempt, error, result_path}.
        """
        job = self.store.get_job(job_id)
        if job is None:
            return {"job_id": job_id, "status": "not_found"}
        return {
            "job_id": job.job_id,
            "status": job.status.value,
            "attempt": job.attempt,
            "max_retries": job.max_retries,
            "error": job.error_message,
            "result_path": job.result_path,
            "started_at": job.started_at,
            "finished_at": job.finished_at,
        }

    def get_results(self, job_id):
        """
        Read the results.json from a completed job.

        Returns
        -------
        dict or None
            The parsed results, or None if not available.
        """
        job = self.store.get_job(job_id)
        if job is None or not job.result_path:
            return None
        results_file = Path(job.result_path) / "results.json"
        if not results_file.exists():
            return None
        with open(results_file, "r", encoding="utf-8") as f:
            return json.load(f)

    def get_logs(self, job_id):
        """
        Read stdout/stderr logs from a job.

        Returns
        -------
        dict
            {"stdout": "...", "stderr": "..."}.
        """
        job = self.store.get_job(job_id)
        if job is None:
            return {"stdout": "", "stderr": ""}
        job_dir = self.output_dir / job.job_id
        logs = {}
        for name in ("stdout.log", "stderr.log"):
            log_file = job_dir / name
            if log_file.exists():
                logs[name.replace(".log", "")] = log_file.read_text(encoding="utf-8")
            else:
                logs[name.replace(".log", "")] = ""
        return logs

    def collect_results(self):
        """
        Collect results from all submitted jobs.

        Returns
        -------
        list of dict
            Each entry: {job_id, status, args, results}.
        """
        collected = []
        for jid in self._job_ids:
            job = self.store.get_job(jid)
            entry = {
                "job_id": jid,
                "status": job.status.value if job else "not_found",
                "args": job.args if job else {},
                "results": self.get_results(jid),
            }
            collected.append(entry)
        return collected

    def summary(self):
        """
        Print a summary of all jobs managed by this bridge.

        Returns
        -------
        dict
            Counts by status.
        """
        all_jobs = self.store.list_jobs()
        counts = {}
        for job in all_jobs:
            s = job.status.value
            counts[s] = counts.get(s, 0) + 1

        total = len(all_jobs)
        succeeded = counts.get("success", 0)
        failed = counts.get("failed", 0) + counts.get("timed_out", 0)

        return {
            "total": total,
            "succeeded": succeeded,
            "failed": failed,
            "pending": counts.get("pending", 0) + counts.get("retrying", 0),
            "detail": counts,
        }

    def copy_results_to_task(self, job_id, filename="results.json"):
        """
        Copy a job's results.json into the task_solve root (overwriting).

        This is the final step — makes the runner output available to
        the report generator.
        """
        results = self.get_results(job_id)
        if results is None:
            raise RuntimeError(f"No results available for {job_id}")
        dest = self.task_dir / filename
        with open(dest, "w", encoding="utf-8") as f:
            json.dump(results, f, indent=2)
        return str(dest)

    def _submit(self, script, args, max_retries, timeout_seconds, workdir=None,
                job_type="script"):
        """Internal: create and save a job, with queue-size checks."""
        # Rate limiting — refuse if the queue is dangerously large.
        pending_count = len(self.store.get_pending_jobs())
        if pending_count >= self._queue_limit:
            raise RuntimeError(
                f"Queue size ({pending_count}) reached hard limit "
                f"({self._queue_limit}). Run or cancel existing jobs first."
            )
        if pending_count >= self._queue_warn:
            logger.warning(
                "Queue size (%d) exceeds warning threshold (%d)",
                pending_count, self._queue_warn,
            )

        job = Job(
            script=script,
            args=args,
            max_retries=max_retries,
            timeout_seconds=timeout_seconds,
            workdir=workdir,
            job_type=job_type,
        )
        self.store.save_job(job)
        self._job_ids.append(job.job_id)
        self.progress.add_job_id(job.job_id)
        return job.job_id

    def get_executed_notebook(self, job_id):
        """
        Get the path to the executed notebook (.ipynb with outputs).

        Only available for jobs submitted with ``mode="execute"``.

        Returns
        -------
        Path or None
            Path to the executed .ipynb, or None if unavailable.
        """
        job = self.store.get_job(job_id)
        if job is None or not job.result_path:
            return None
        # The notebook executor saves the executed .ipynb in the output dir
        output_dir = Path(job.result_path)
        nb_name = Path(job.script).name
        executed = output_dir / nb_name
        if executed.exists():
            return executed
        # Also check for partial execution
        partial = output_dir / ("PARTIAL_" + nb_name)
        if partial.exists():
            return partial
        return None


def _notebook_to_script(notebook_path):
    """
    Convert a Jupyter notebook (.ipynb) to a standalone Python script.

    - Code cells are extracted as-is
    - Markdown cells become block comments
    - IPython magics (%matplotlib, !) are commented out
    - The dual-boot setup cell is replaced with direct pip import
    - Interactive display calls are replaced with file saves

    Parameters
    ----------
    notebook_path : Path
        Path to the .ipynb file.

    Returns
    -------
    Path
        Path to the generated .py script.
    """
    with open(notebook_path, "r", encoding="utf-8") as f:
        nb = json.load(f)

    cells = nb.get("cells", [])
    lines = [
        '"""',
        f"Auto-generated from: {notebook_path.name}",
        "Converted by neqsim_runner for headless execution.",
        '"""',
        "",
    ]

    for i, cell in enumerate(cells):
        cell_type = cell.get("cell_type", "code")
        source = "".join(cell.get("source", []))

        if cell_type == "markdown":
            # Convert markdown to block comment
            lines.append(f"# {'='*60}")
            lines.append(f"# Cell {i+1} (markdown)")
            for line in source.split("\n"):
                lines.append(f"# {line}")
            lines.append("")

        elif cell_type == "code":
            lines.append(f"# --- Cell {i+1} ---")
            processed = _process_code_cell(source)
            lines.append(processed)
            lines.append("")

    # Write the script
    script_path = notebook_path.with_suffix(".py")
    script_path.write_text("\n".join(lines), encoding="utf-8")
    return script_path


def _process_code_cell(source):
    """
    Process a code cell for headless execution.

    Handles:
    - IPython magics (% lines → comments)
    - Shell commands (! lines → comments)
    - Interactive Display calls
    - matplotlib inline → agg backend
    - The dual-boot setup cell → simplified import
    """
    lines = source.split("\n")
    output = []

    # Detect if this is the dual-boot setup cell
    is_setup_cell = any("neqsim_dev_setup" in line or "NEQSIM_MODE" in line
                        for line in lines)

    if is_setup_cell:
        # Replace with a simpler headless-compatible version
        output.append("# -- NeqSim setup (headless) --")
        output.append("import os, sys, subprocess")
        output.append("try:")
        output.append("    # Try devtools first")
        output.append("    project_root = os.environ.get('NEQSIM_PROJECT_ROOT')")
        output.append("    if project_root:")
        output.append("        sys.path.insert(0, os.path.join(project_root, 'devtools'))")
        output.append("        from neqsim_dev_setup import neqsim_init, neqsim_classes")
        output.append("        ns = neqsim_init(project_root=project_root, recompile=False, verbose=False)")
        output.append("        ns = neqsim_classes(ns)")
        output.append("        print('NeqSim loaded via devtools')")
        output.append("    else:")
        output.append("        raise ImportError('no devtools')")
        output.append("except (ImportError, Exception):")
        output.append("    try:")
        output.append("        import neqsim")
        output.append("    except ImportError:")
        output.append("        subprocess.check_call([sys.executable, '-m', 'pip', 'install', '-q', 'neqsim'])")
        output.append("    from neqsim import jneqsim")
        output.append("    print('NeqSim loaded via pip')")
        return "\n".join(output)

    for line in lines:
        stripped = line.strip()
        # Comment out IPython magics
        if stripped.startswith("%"):
            if "matplotlib" in stripped:
                output.append("import matplotlib; matplotlib.use('Agg')")
                output.append("import matplotlib.pyplot as plt")
            else:
                output.append(f"# [magic] {line}")
        # Comment out shell commands
        elif stripped.startswith("!"):
            output.append(f"# [shell] {line}")
        # Replace plt.show() with savefig hint
        elif stripped == "plt.show()":
            output.append("# plt.show()  # headless: use savefig instead")
        else:
            output.append(line)

    return "\n".join(output)
