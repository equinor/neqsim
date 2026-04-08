"""
Supervisor — monitors worker processes and retries failed jobs.

The supervisor is the "watchdog" layer. It:
1. Picks up pending/retryable jobs from the store
2. Launches them as isolated worker subprocesses
3. Monitors for completion/timeout/crash
4. Retries failed jobs up to max_retries
5. Logs everything to the store for diagnostics
6. On startup, recovers stale jobs from a previous supervisor crash
"""

import time
import logging
from datetime import datetime, timezone
from pathlib import Path

from neqsim_runner.models import JobStatus
from neqsim_runner.store import JobStore
from neqsim_runner.worker import WorkerProcess

logger = logging.getLogger("neqsim_runner.supervisor")


def _slog(level, event, **fields):
    """Structured log helper — emits *event* with key=value fields."""
    parts = [event] + [f"{k}={v!r}" for k, v in fields.items()]
    getattr(logger, level)(" | ".join(parts))


class Supervisor:
    """
    Job execution supervisor with automatic retry and recovery.

    Parameters
    ----------
    store : JobStore
        The persistent job store.
    max_parallel : int
        Max concurrent workers (default 1 — JVM is memory-heavy).
    poll_interval : float
        Seconds between queue checks.
    project_root : str or Path, optional
        NeqSim project root for devtools mode.
    output_base : str or Path, optional
        Base directory for worker outputs.
    """

    def __init__(self, store, max_parallel=1, poll_interval=2.0,
                 project_root=None, output_base=None):
        self.store = store
        self.max_parallel = max_parallel
        self.poll_interval = poll_interval
        self.project_root = project_root
        self.output_base = output_base
        self._active_workers = {}  # job_id -> WorkerProcess
        self._mono_starts = {}     # job_id -> time.monotonic() when launched
        self._should_stop = False

    def run(self):
        """
        Main supervisor loop. Blocks until all jobs are done or stop() is called.

        Lifecycle:
        1. Recover stale jobs from previous supervisor crash
        2. Process pending jobs
        3. Monitor running workers
        4. Retry failed jobs
        5. Repeat until queue is empty
        """
        _slog("info", "supervisor_starting", max_parallel=self.max_parallel)
        self._should_stop = False

        # ── Recover from previous crash ──
        self.store.cleanup_stale_running()

        while not self._should_stop:
            # ── Check running workers ──
            self._check_workers()

            # ── Launch new workers if capacity available ──
            if len(self._active_workers) < self.max_parallel:
                pending = self.store.get_pending_jobs()
                for job in pending:
                    if len(self._active_workers) >= self.max_parallel:
                        break
                    if job.job_id in self._active_workers:
                        continue
                    self._launch_worker(job)

            # ── Are we done? ──
            if not self._active_workers:
                pending = self.store.get_pending_jobs()
                if not pending:
                    _slog("info", "supervisor_done")
                    break

            time.sleep(self.poll_interval)

        # Final status report
        self._report()

    def stop(self):
        """Signal the supervisor to stop after current work completes."""
        _slog("info", "supervisor_stop_requested")
        self._should_stop = True

    def _launch_worker(self, job):
        """Launch a worker subprocess for a job."""
        job.attempt += 1
        job.status = JobStatus.RUNNING
        job.started_at = datetime.now(timezone.utc).isoformat()
        job.error_message = None
        self.store.save_job(job)
        self.store.log_event(
            job.job_id, "started",
            f"Attempt {job.attempt}/{job.max_retries} starting"
        )

        _slog("info", "worker_launching", job_id=job.job_id,
              attempt=job.attempt, max_retries=job.max_retries,
              script=job.script)

        worker = WorkerProcess(
            job,
            project_root=self.project_root,
            output_base=self.output_base,
        )

        try:
            worker.start()
            self.store.save_job(job)  # save PID
            self._active_workers[job.job_id] = worker
            self._mono_starts[job.job_id] = time.monotonic()
        except Exception as e:
            job.status = JobStatus.FAILED
            job.error_message = f"Failed to start worker: {e}"
            job.finished_at = datetime.now(timezone.utc).isoformat()
            self.store.save_job(job)
            self.store.log_event(job.job_id, "start_failed", str(e))
            _slog("error", "worker_start_failed", job_id=job.job_id, error=str(e))

    def _check_workers(self):
        """Check all active workers for completion."""
        finished = []
        for job_id, worker in self._active_workers.items():
            if worker.process is None:
                finished.append(job_id)
                continue

            # Non-blocking poll
            exit_code = worker.process.poll()
            if exit_code is None:
                # Still running — check timeout using monotonic clock
                job = worker.job
                mono_start = self._mono_starts.get(job_id)
                if mono_start is not None and job.timeout_seconds:
                    elapsed = time.monotonic() - mono_start
                    if elapsed > job.timeout_seconds:
                        _slog("warning", "job_timed_out", job_id=job_id,
                              elapsed_s=round(elapsed, 1),
                              limit_s=job.timeout_seconds)
                        worker._kill()
                        job.status = JobStatus.TIMED_OUT
                        job.error_message = f"Timed out after {elapsed:.0f}s (limit: {job.timeout_seconds}s)"
                        job.finished_at = datetime.now(timezone.utc).isoformat()
                        self.store.save_job(job)
                        self.store.log_event(job_id, "timed_out", job.error_message)
                        self._maybe_retry(job)
                        finished.append(job_id)
                continue

            # Process finished
            job = worker.job
            job.finished_at = datetime.now(timezone.utc).isoformat()
            job.pid = None

            if exit_code == 0:
                job.status = JobStatus.SUCCESS
                self.store.save_job(job)
                self.store.log_event(job_id, "success",
                                     f"Completed on attempt {job.attempt}")
                _slog("info", "job_succeeded", job_id=job_id, attempt=job.attempt)
            else:
                job.status = JobStatus.FAILED
                # Read error from status file
                status_file = Path(job.result_path or "") / "_status.json"
                if status_file.exists():
                    try:
                        import json
                        with open(status_file, encoding="utf-8") as f:
                            status_data = json.load(f)
                        job.error_message = status_data.get("error", f"Exit code {exit_code}")
                    except Exception:
                        job.error_message = f"Exit code {exit_code}"
                else:
                    job.error_message = f"Exit code {exit_code}"

                self.store.save_job(job)
                self.store.log_event(
                    job_id, "failed",
                    f"Attempt {job.attempt} failed: {job.error_message}"
                )
                _slog("warning", "job_failed", job_id=job_id,
                      attempt=job.attempt, error=job.error_message)
                self._maybe_retry(job)

            finished.append(job_id)

        for job_id in finished:
            self._active_workers.pop(job_id, None)
            self._mono_starts.pop(job_id, None)

    def _maybe_retry(self, job):
        """Schedule a retry if the job has retries remaining."""
        if job.attempt < job.max_retries:
            job.status = JobStatus.RETRYING
            self.store.save_job(job)
            self.store.log_event(
                job.job_id, "retry_scheduled",
                f"Will retry (attempt {job.attempt + 1}/{job.max_retries})"
            )
            _slog("info", "retry_scheduled", job_id=job.job_id,
                  next_attempt=job.attempt + 1, max_retries=job.max_retries)
        else:
            _slog("error", "retries_exhausted", job_id=job.job_id,
                  max_retries=job.max_retries)
            self.store.log_event(
                job.job_id, "retries_exhausted",
                f"All {job.max_retries} attempts failed"
            )

    def _report(self):
        """Print a summary of all jobs."""
        all_jobs = self.store.list_jobs()
        if not all_jobs:
            logger.info("No jobs in queue")
            return

        counts = {}
        for job in all_jobs:
            counts[job.status.value] = counts.get(job.status.value, 0) + 1

        _slog("info", "job_summary", **counts)

        failed = [j for j in all_jobs if j.status in (JobStatus.FAILED, JobStatus.TIMED_OUT)]
        for j in failed:
            _slog("warning", "final_failed_job", job_id=j.job_id,
                  error=j.error_message)
