"""
CLI + __main__ entry point for NeqSim Runner.

Usage::

    python -m neqsim_runner submit my_script.py --args '{"pressure": 60}'
    python -m neqsim_runner run
    python -m neqsim_runner status
    python -m neqsim_runner status job-abc123
    python -m neqsim_runner log job-abc123
    python -m neqsim_runner cancel job-abc123

    # Convenience: submit + run in one shot
    python -m neqsim_runner go my_script.py --args '{"pressure": 60}'
"""

import argparse
import json
import logging
import sys
from pathlib import Path


def _detect_project_root():
    """Auto-detect the NeqSim project root."""
    # Walk up from this file to find pom.xml
    p = Path(__file__).resolve().parent
    for _ in range(5):
        if (p / "pom.xml").exists() and (p / "src" / "main" / "java" / "neqsim").exists():
            return str(p)
        p = p.parent
    return None


def _default_db():
    root = _detect_project_root()
    if root:
        db_dir = Path(root) / "task_solve"
        db_dir.mkdir(exist_ok=True)
        return str(db_dir / "neqsim_runner.db")
    return "neqsim_runner.db"


def _default_output():
    root = _detect_project_root()
    if root:
        return str(Path(root) / "task_solve" / "runner_output")
    return "runner_output"


def cmd_submit(args):
    """Submit a job to the queue."""
    from neqsim_runner import submit_job

    job_args = {}
    if args.args:
        job_args = json.loads(args.args)

    job_id = submit_job(
        script=args.script,
        args=job_args,
        max_retries=args.retries,
        timeout_seconds=args.timeout,
        db_path=args.db,
    )
    print(f"Submitted: {job_id}")
    return job_id


def cmd_run(args):
    """Run all pending jobs."""
    from neqsim_runner.store import JobStore
    from neqsim_runner.supervisor import Supervisor

    store = JobStore(args.db)
    project_root = args.project_root or _detect_project_root()

    supervisor = Supervisor(
        store,
        max_parallel=args.parallel,
        project_root=project_root,
        output_base=args.output,
    )
    supervisor.run()


def cmd_go(args):
    """Submit + run in one shot."""
    job_id = cmd_submit(args)
    cmd_run(args)
    return job_id


def cmd_status(args):
    """Show job status."""
    from neqsim_runner.store import JobStore

    store = JobStore(args.db)

    if args.job_id:
        job = store.get_job(args.job_id)
        if job is None:
            print(f"No job found: {args.job_id}", file=sys.stderr)
            sys.exit(1)
        _print_job_detail(job)
    else:
        jobs = store.list_jobs()
        if not jobs:
            print("No jobs in queue")
            return
        _print_job_table(jobs)


def cmd_log(args):
    """Show job event log."""
    from neqsim_runner.store import JobStore

    store = JobStore(args.db)
    entries = store.get_log(args.job_id)

    if not entries:
        print(f"No log entries for {args.job_id}")
        return

    for entry in entries:
        ts = entry.get("timestamp", "?")[:19]
        event = entry.get("event", "?")
        msg = entry.get("message", "")
        print(f"  [{ts}] {event}: {msg}")


def cmd_cancel(args):
    """Cancel a job."""
    from neqsim_runner.store import JobStore

    store = JobStore(args.db)
    store.cancel_job(args.job_id)
    print(f"Cancelled: {args.job_id}")


def _print_job_table(jobs):
    """Print a formatted table of jobs."""
    header = f"{'ID':<20} {'Status':<12} {'Attempt':<10} {'Script':<40}"
    print(header)
    print("-" * len(header))
    for j in jobs:
        script_name = Path(j.script).name if j.script else "?"
        print(f"{j.job_id:<20} {j.status.value:<12} {j.attempt}/{j.max_retries:<7} {script_name:<40}")


def _print_job_detail(job):
    """Print detailed job info."""
    print(f"Job ID:      {job.job_id}")
    print(f"Script:      {job.script}")
    print(f"Status:      {job.status.value}")
    print(f"Attempt:     {job.attempt}/{job.max_retries}")
    print(f"Args:        {json.dumps(job.args)}")
    print(f"Timeout:     {job.timeout_seconds}s")
    print(f"Created:     {job.created_at}")
    print(f"Started:     {job.started_at or '-'}")
    print(f"Finished:    {job.finished_at or '-'}")
    print(f"Result dir:  {job.result_path or '-'}")
    print(f"Checkpoint:  {job.checkpoint_path or '-'}")
    if job.error_message:
        print(f"Error:       {job.error_message}")


def main():
    parser = argparse.ArgumentParser(
        prog="neqsim_runner",
        description="NeqSim Runner — supervised, restartable simulation execution"
    )
    parser.add_argument(
        "--db", default=_default_db(),
        help="Path to SQLite database (default: task_solve/neqsim_runner.db)"
    )

    sub = parser.add_subparsers(dest="command")

    # ── submit ──
    p_submit = sub.add_parser("submit", help="Submit a job to the queue")
    p_submit.add_argument("script", help="Python script to execute")
    p_submit.add_argument("--args", default=None, help="JSON arguments for the script")
    p_submit.add_argument("--retries", type=int, default=3, help="Max retries (default: 3)")
    p_submit.add_argument("--timeout", type=int, default=3600, help="Timeout in seconds (default: 3600)")
    p_submit.set_defaults(func=cmd_submit)

    # ── run ──
    p_run = sub.add_parser("run", help="Run all pending jobs")
    p_run.add_argument("--parallel", type=int, default=1, help="Max parallel workers (default: 1)")
    p_run.add_argument("--project-root", default=None, help="NeqSim project root")
    p_run.add_argument("--output", default=_default_output(), help="Output base directory")
    p_run.set_defaults(func=cmd_run)

    # ── go (submit + run) ──
    p_go = sub.add_parser("go", help="Submit and run in one shot")
    p_go.add_argument("script", help="Python script to execute")
    p_go.add_argument("--args", default=None, help="JSON arguments for the script")
    p_go.add_argument("--retries", type=int, default=3, help="Max retries (default: 3)")
    p_go.add_argument("--timeout", type=int, default=3600, help="Timeout in seconds (default: 3600)")
    p_go.add_argument("--parallel", type=int, default=1, help="Max parallel workers (default: 1)")
    p_go.add_argument("--project-root", default=None, help="NeqSim project root")
    p_go.add_argument("--output", default=_default_output(), help="Output base directory")
    p_go.set_defaults(func=cmd_go)

    # ── status ──
    p_status = sub.add_parser("status", help="Show job status")
    p_status.add_argument("job_id", nargs="?", help="Job ID (omit for all)")
    p_status.set_defaults(func=cmd_status)

    # ── log ──
    p_log = sub.add_parser("log", help="Show job event log")
    p_log.add_argument("job_id", help="Job ID")
    p_log.set_defaults(func=cmd_log)

    # ── cancel ──
    p_cancel = sub.add_parser("cancel", help="Cancel a job")
    p_cancel.add_argument("job_id", help="Job ID")
    p_cancel.set_defaults(func=cmd_cancel)

    args = parser.parse_args()

    if not args.command:
        parser.print_help()
        sys.exit(1)

    # Setup logging
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s [%(name)s] %(levelname)s: %(message)s",
        datefmt="%H:%M:%S",
    )

    args.func(args)


if __name__ == "__main__":
    main()
