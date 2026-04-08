"""
NeqSim Runner — Supervised, restartable simulation execution engine.

Eliminates manual kernel restarts by running each NeqSim job in an isolated
subprocess with its own JVM. A supervisor monitors jobs, retries failures,
and resumes from checkpoints.

Quick start::

    from neqsim_runner import submit_job, run_supervisor

    job_id = submit_job(
        script="my_simulation.py",
        args={"pressure": 60.0, "temperature": 25.0},
        max_retries=3,
    )
    run_supervisor()  # blocks, runs all queued jobs

CLI::

    python -m neqsim_runner submit my_simulation.py --args '{"pressure": 60}'
    python -m neqsim_runner run          # run all pending jobs
    python -m neqsim_runner status       # show job queue
    python -m neqsim_runner status JOB1  # show single job
"""

from neqsim_runner.models import Job, JobStatus, InvalidTransitionError
from neqsim_runner.store import JobStore
from neqsim_runner.supervisor import Supervisor
from neqsim_runner.agent_bridge import AgentBridge
from neqsim_runner.progress import TaskProgress

__all__ = ["submit_job", "run_supervisor", "job_status", "list_jobs",
           "Job", "JobStatus", "InvalidTransitionError",
           "JobStore", "Supervisor", "AgentBridge",
           "TaskProgress"]

# Default store location — next to the devtools directory
_DEFAULT_DB = None


def _get_store(db_path=None):
    """Get or create the default JobStore."""
    global _DEFAULT_DB
    if db_path:
        return JobStore(db_path)
    if _DEFAULT_DB is None:
        from pathlib import Path
        default_dir = Path(__file__).resolve().parent.parent.parent / "task_solve"
        default_dir.mkdir(exist_ok=True)
        _DEFAULT_DB = str(default_dir / "neqsim_runner.db")
    return JobStore(_DEFAULT_DB)


def submit_job(script, args=None, max_retries=3, timeout_seconds=3600,
               checkpoint_interval=None, workdir=None, db_path=None):
    """
    Submit a simulation job to the queue.

    Parameters
    ----------
    script : str
        Path to the Python script to execute.
    args : dict, optional
        Arguments passed to the script as JSON via environment variable.
    max_retries : int
        Maximum number of retry attempts on failure.
    timeout_seconds : int
        Maximum wall-clock time per attempt.
    checkpoint_interval : int, optional
        Seconds between checkpoint saves (script must support it).
    workdir : str, optional
        Working directory for the job. Defaults to script's parent.
    db_path : str, optional
        Path to the SQLite database.

    Returns
    -------
    str
        The job ID.
    """
    store = _get_store(db_path)
    job = Job(
        script=script,
        args=args or {},
        max_retries=max_retries,
        timeout_seconds=timeout_seconds,
        checkpoint_interval=checkpoint_interval,
        workdir=workdir,
    )
    store.save_job(job)
    return job.job_id


def run_supervisor(db_path=None, max_parallel=1):
    """
    Run the supervisor loop — processes all pending/retryable jobs.

    Parameters
    ----------
    db_path : str, optional
        Path to the SQLite database.
    max_parallel : int
        Max concurrent worker processes (default 1 — JVM is heavy).
    """
    store = _get_store(db_path)
    supervisor = Supervisor(store, max_parallel=max_parallel)
    supervisor.run()


def job_status(job_id, db_path=None):
    """Get the current status of a job."""
    store = _get_store(db_path)
    job = store.get_job(job_id)
    if job is None:
        raise KeyError(f"No job with ID {job_id}")
    return job


def list_jobs(db_path=None, status=None):
    """List all jobs, optionally filtered by status."""
    store = _get_store(db_path)
    return store.list_jobs(status=status)
