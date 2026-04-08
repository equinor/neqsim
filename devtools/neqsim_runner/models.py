"""
Data models for NeqSim Runner jobs and checkpoints.
"""

import enum
import json
import uuid
from datetime import datetime, timezone


class JobStatus(str, enum.Enum):
    """Lifecycle states for a simulation job."""
    PENDING = "pending"
    RUNNING = "running"
    SUCCESS = "success"
    FAILED = "failed"
    RETRYING = "retrying"
    TIMED_OUT = "timed_out"
    CANCELLED = "cancelled"


# Valid state transitions.  Any transition not listed here is illegal.
_VALID_TRANSITIONS = {
    JobStatus.PENDING:   {JobStatus.RUNNING, JobStatus.CANCELLED},
    JobStatus.RUNNING:   {JobStatus.SUCCESS, JobStatus.FAILED, JobStatus.TIMED_OUT, JobStatus.CANCELLED},
    JobStatus.FAILED:    {JobStatus.RETRYING, JobStatus.CANCELLED},
    JobStatus.TIMED_OUT: {JobStatus.RETRYING, JobStatus.CANCELLED},
    JobStatus.RETRYING:  {JobStatus.PENDING, JobStatus.CANCELLED},
    JobStatus.SUCCESS:   set(),     # terminal
    JobStatus.CANCELLED: set(),     # terminal
}


class InvalidTransitionError(Exception):
    """Raised when a job status transition is not allowed."""
    pass


class Job:
    """
    A simulation job — one unit of work that runs in an isolated subprocess.

    Parameters
    ----------
    script : str
        Path to the Python script to execute.
    args : dict
        Arguments passed to the script via NEQSIM_JOB_ARGS env variable.
    max_retries : int
        Maximum retry attempts on failure.
    timeout_seconds : int
        Maximum wall-clock seconds per attempt.
    checkpoint_interval : int or None
        Seconds between automatic checkpoint saves.
    workdir : str or None
        Working directory. Defaults to script's parent directory.
    job_id : str or None
        Unique ID. Auto-generated if not provided.
    """

    def __init__(self, script, args=None, max_retries=3, timeout_seconds=3600,
                 checkpoint_interval=None, workdir=None, job_id=None,
                 job_type="script"):
        self.job_id = job_id or f"job-{uuid.uuid4().hex[:12]}"
        self.script = script
        self.args = args or {}
        self.max_retries = max_retries
        self.timeout_seconds = timeout_seconds
        self.checkpoint_interval = checkpoint_interval
        self.workdir = workdir
        self.job_type = job_type  # "script" or "notebook"
        self.status = JobStatus.PENDING
        self.attempt = 0
        self.created_at = datetime.now(timezone.utc).isoformat()
        self.started_at = None
        self.finished_at = None
        self.error_message = None
        self.result_path = None
        self.checkpoint_path = None
        self.pid = None

    def to_dict(self):
        """Serialize to a dictionary for SQLite storage."""
        return {
            "job_id": self.job_id,
            "script": self.script,
            "args": json.dumps(self.args),
            "max_retries": self.max_retries,
            "timeout_seconds": self.timeout_seconds,
            "checkpoint_interval": self.checkpoint_interval,
            "workdir": self.workdir,
            "job_type": self.job_type,
            "status": self.status.value,
            "attempt": self.attempt,
            "created_at": self.created_at,
            "started_at": self.started_at,
            "finished_at": self.finished_at,
            "error_message": self.error_message,
            "result_path": self.result_path,
            "checkpoint_path": self.checkpoint_path,
            "pid": self.pid,
        }

    @classmethod
    def from_dict(cls, d):
        """Deserialize from a dictionary."""
        job = cls(
            script=d["script"],
            args=json.loads(d["args"]) if isinstance(d["args"], str) else d["args"],
            max_retries=d["max_retries"],
            timeout_seconds=d["timeout_seconds"],
            checkpoint_interval=d.get("checkpoint_interval"),
            workdir=d.get("workdir"),
            job_id=d["job_id"],
            job_type=d.get("job_type", "script"),
        )
        job.status = JobStatus(d["status"])
        job.attempt = d.get("attempt", 0)
        job.created_at = d.get("created_at")
        job.started_at = d.get("started_at")
        job.finished_at = d.get("finished_at")
        job.error_message = d.get("error_message")
        job.result_path = d.get("result_path")
        job.checkpoint_path = d.get("checkpoint_path")
        job.pid = d.get("pid")
        return job

    def is_retryable(self):
        """Check if the job can be retried."""
        return (
            self.status in (JobStatus.FAILED, JobStatus.TIMED_OUT)
            and self.attempt < self.max_retries
        )

    def set_status(self, new_status):
        """Transition to *new_status*, raising on invalid transitions.

        Parameters
        ----------
        new_status : JobStatus
            The target state.

        Raises
        ------
        InvalidTransitionError
            If the transition from the current state is not allowed.
        """
        new_status = JobStatus(new_status)
        allowed = _VALID_TRANSITIONS.get(self.status, set())
        if new_status not in allowed:
            raise InvalidTransitionError(
                f"Cannot transition from {self.status.value!r} to "
                f"{new_status.value!r} (allowed: "
                f"{sorted(s.value for s in allowed)})"
            )
        self.status = new_status

    def __repr__(self):
        return (
            f"Job(id={self.job_id!r}, script={self.script!r}, "
            f"status={self.status.value}, attempt={self.attempt}/{self.max_retries})"
        )


class Checkpoint:
    """
    A snapshot of in-progress work that allows resuming after failure.

    Scripts save checkpoints by writing JSON to the path specified in
    the NEQSIM_CHECKPOINT_PATH environment variable.
    """

    def __init__(self, job_id, iteration=0, data=None, timestamp=None):
        self.job_id = job_id
        self.iteration = iteration
        self.data = data or {}
        self.timestamp = timestamp or datetime.now(timezone.utc).isoformat()

    def to_dict(self):
        return {
            "job_id": self.job_id,
            "iteration": self.iteration,
            "data": self.data,
            "timestamp": self.timestamp,
        }

    @classmethod
    def from_dict(cls, d):
        return cls(
            job_id=d["job_id"],
            iteration=d.get("iteration", 0),
            data=d.get("data", {}),
            timestamp=d.get("timestamp"),
        )

    def save(self, path):
        """Write checkpoint to a JSON file."""
        import os
        os.makedirs(os.path.dirname(path) if os.path.dirname(path) else ".", exist_ok=True)
        with open(path, "w") as f:
            json.dump(self.to_dict(), f, indent=2)

    @classmethod
    def load(cls, path):
        """Load checkpoint from a JSON file."""
        with open(path, "r") as f:
            return cls.from_dict(json.load(f))
