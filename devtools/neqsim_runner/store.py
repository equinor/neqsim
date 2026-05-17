"""
SQLite-based persistent job store for NeqSim Runner.

All job state is persisted to disk so the supervisor can crash and
resume without losing track of in-flight or queued work.
"""

import sqlite3
import threading
from contextlib import contextmanager

from neqsim_runner.models import Job, JobStatus


_CREATE_TABLE = """
CREATE TABLE IF NOT EXISTS jobs (
    job_id          TEXT PRIMARY KEY,
    script          TEXT NOT NULL,
    args            TEXT NOT NULL DEFAULT '{}',
    max_retries     INTEGER NOT NULL DEFAULT 3,
    timeout_seconds INTEGER NOT NULL DEFAULT 3600,
    checkpoint_interval INTEGER,
    workdir         TEXT,
    job_type        TEXT NOT NULL DEFAULT 'script',
    status          TEXT NOT NULL DEFAULT 'pending',
    attempt         INTEGER NOT NULL DEFAULT 0,
    created_at      TEXT,
    started_at      TEXT,
    finished_at     TEXT,
    error_message   TEXT,
    result_path     TEXT,
    checkpoint_path TEXT,
    pid             INTEGER
);
"""

_CREATE_LOG_TABLE = """
CREATE TABLE IF NOT EXISTS job_log (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    job_id      TEXT NOT NULL,
    timestamp   TEXT NOT NULL,
    event       TEXT NOT NULL,
    message     TEXT,
    FOREIGN KEY (job_id) REFERENCES jobs(job_id)
);
"""


class JobStore:
    """
    Thread-safe SQLite store for job state and event logs.

    Parameters
    ----------
    db_path : str
        Path to the SQLite database file.
    """

    def __init__(self, db_path):
        self.db_path = db_path
        self._local = threading.local()
        # Create tables on first use
        with self._conn() as conn:
            conn.execute(_CREATE_TABLE)
            conn.execute(_CREATE_LOG_TABLE)
            # Migrate: add job_type column if upgrading from older schema
            try:
                conn.execute("SELECT job_type FROM jobs LIMIT 1")
            except sqlite3.OperationalError:
                conn.execute(
                    "ALTER TABLE jobs ADD COLUMN job_type TEXT NOT NULL DEFAULT 'script'"
                )

    @contextmanager
    def _conn(self):
        """Get a thread-local database connection."""
        if not hasattr(self._local, "conn") or self._local.conn is None:
            self._local.conn = sqlite3.connect(
                self.db_path, timeout=30.0
            )
            self._local.conn.row_factory = sqlite3.Row
            self._local.conn.execute("PRAGMA journal_mode=WAL;")
        yield self._local.conn
        self._local.conn.commit()

    def save_job(self, job):
        """Insert or update a job."""
        d = job.to_dict()
        columns = ", ".join(d.keys())
        placeholders = ", ".join(["?"] * len(d))
        updates = ", ".join(f"{k}=excluded.{k}" for k in d if k != "job_id")
        sql = (
            f"INSERT INTO jobs ({columns}) VALUES ({placeholders}) "
            f"ON CONFLICT(job_id) DO UPDATE SET {updates}"
        )
        with self._conn() as conn:
            conn.execute(sql, list(d.values()))

    def get_job(self, job_id):
        """Retrieve a job by ID, or None if not found."""
        with self._conn() as conn:
            row = conn.execute(
                "SELECT * FROM jobs WHERE job_id = ?", (job_id,)
            ).fetchone()
        if row is None:
            return None
        return Job.from_dict(dict(row))

    def list_jobs(self, status=None):
        """List jobs, optionally filtered by status."""
        with self._conn() as conn:
            if status:
                if isinstance(status, JobStatus):
                    status = status.value
                rows = conn.execute(
                    "SELECT * FROM jobs WHERE status = ? ORDER BY created_at",
                    (status,)
                ).fetchall()
            else:
                rows = conn.execute(
                    "SELECT * FROM jobs ORDER BY created_at"
                ).fetchall()
        return [Job.from_dict(dict(r)) for r in rows]

    def get_pending_jobs(self):
        """Get jobs that are ready to run (pending or retryable)."""
        with self._conn() as conn:
            rows = conn.execute(
                "SELECT * FROM jobs WHERE status IN ('pending', 'retrying') "
                "ORDER BY created_at"
            ).fetchall()
        return [Job.from_dict(dict(r)) for r in rows]

    def get_running_jobs(self):
        """Get currently running jobs."""
        with self._conn() as conn:
            rows = conn.execute(
                "SELECT * FROM jobs WHERE status = 'running' "
                "ORDER BY started_at"
            ).fetchall()
        return [Job.from_dict(dict(r)) for r in rows]

    def log_event(self, job_id, event, message=None):
        """Append an event to the job log."""
        from datetime import datetime, timezone
        ts = datetime.now(timezone.utc).isoformat()
        with self._conn() as conn:
            conn.execute(
                "INSERT INTO job_log (job_id, timestamp, event, message) "
                "VALUES (?, ?, ?, ?)",
                (job_id, ts, event, message),
            )

    def get_log(self, job_id):
        """Get all log entries for a job."""
        with self._conn() as conn:
            rows = conn.execute(
                "SELECT * FROM job_log WHERE job_id = ? ORDER BY id",
                (job_id,)
            ).fetchall()
        return [dict(r) for r in rows]

    def cancel_job(self, job_id):
        """Mark a job as cancelled."""
        with self._conn() as conn:
            conn.execute(
                "UPDATE jobs SET status = 'cancelled' WHERE job_id = ?",
                (job_id,),
            )
            self.log_event(job_id, "cancelled", "Job cancelled by user")

    def cleanup_stale_running(self):
        """
        Reset jobs stuck in 'running' state (e.g., after supervisor crash).
        Marks them as retrying if retries remain, otherwise failed.
        """
        running = self.get_running_jobs()
        for job in running:
            # Check if the worker process is actually alive
            if job.pid:
                import os
                try:
                    os.kill(job.pid, 0)  # signal 0 = check existence
                    continue  # process still alive, skip
                except OSError:
                    pass  # process is dead
            if job.is_retryable():
                job.status = JobStatus.RETRYING
                self.save_job(job)
                self.log_event(job.job_id, "stale_reset",
                               f"Reset stale running job to retrying (attempt {job.attempt})")
            else:
                job.status = JobStatus.FAILED
                job.error_message = "Supervisor restart: job was running but worker process is gone"
                self.save_job(job)
                self.log_event(job.job_id, "stale_failed",
                               "Job failed: worker process gone after supervisor restart")
