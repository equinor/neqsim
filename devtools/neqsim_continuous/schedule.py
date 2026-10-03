"""Schedule monitor cycles: Windows Task Scheduler (schtasks) or a cron line for servers."""

import os
import platform
import re
import subprocess
import sys
from datetime import datetime, timedelta

from .plan import continuous_dir, read_json, write_json

DEVTOOLS = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SCHEDULE_FILE = "schedule.json"
SCHEMA_VERSION = "1.0"


def task_name(task_dir):
    return "NeqSim living task " + re.sub(r"[^A-Za-z0-9_.-]", "_", os.path.basename(
        os.path.abspath(str(task_dir))))[:180]


def build(task_dir, daily="05:00", mode="monitor", python=None, standard_first=True):
    """Return the scheduled command for Windows and a cron line for Linux servers."""
    if not re.match(r"^\d{2}:\d{2}$", daily):
        raise ValueError("daily must be HH:MM")
    python = python or sys.executable
    task_dir = os.path.abspath(str(task_dir))
    cli = os.path.join(DEVTOOLS, "neqsim_cli.py")
    command = '"{}" "{}" task-cycle "{}" --mode {}'.format(python, cli, task_dir, mode)
    if standard_first:
        command += " --standard-first"
    hour, minute = daily.split(":")
    name = task_name(task_dir)
    return {"name": name, "command": command,
            "windows": ["schtasks", "/Create", "/F", "/SC", "DAILY", "/TN", name,
                        "/TR", command, "/ST", daily],
            "cron": "{} {} * * * {}".format(int(minute), int(hour), command)}


def _schedule_path(task_dir):
    return os.path.join(continuous_dir(task_dir), SCHEDULE_FILE)


def record(task_dir, daily, mode, result):
    """Persist scheduler intent/status so task-status can show the next expected run."""
    now = datetime.now().astimezone().replace(microsecond=0)
    data = {"schema_version": SCHEMA_VERSION, "daily": daily, "mode": mode,
            "status": result.get("status"), "message": result.get("message", ""),
            "platform": platform.system(), "updated_at": now.isoformat()}
    write_json(_schedule_path(task_dir), data)
    return data


def clear_record(task_dir):
    """Remove the persisted scheduler record after unscheduling."""
    path = _schedule_path(task_dir)
    if os.path.exists(path):
        os.remove(path)


def schedule_status(task_dir, now=None):
    """Return persisted scheduler state and the next local run when it is known."""
    data = read_json(_schedule_path(task_dir), {}) or {}
    if not data:
        return {"status": "not_recorded", "next_run": None}
    result = dict(data)
    result["next_run"] = None
    if data.get("status") != "ok" or not data.get("daily"):
        return result
    now = now or datetime.now().astimezone()
    hour, minute = (int(v) for v in data["daily"].split(":"))
    candidate = now.replace(hour=hour, minute=minute, second=0, microsecond=0)
    if candidate <= now:
        candidate += timedelta(days=1)
    result["next_run"] = candidate.isoformat()
    return result


def install(spec):
    if platform.system() != "Windows":
        return {"status": "manual", "message": "add this line with `crontab -e`: " + spec["cron"]}
    completed = subprocess.run(spec["windows"], capture_output=True, text=True)
    return {"status": "ok" if completed.returncode == 0 else "fail",
            "message": (completed.stdout or completed.stderr).strip()}


def remove(task_dir):
    if platform.system() != "Windows":
        return {"status": "manual", "message": "remove the cron line with `crontab -e`"}
    completed = subprocess.run(["schtasks", "/Delete", "/F", "/TN", task_name(task_dir)],
                               capture_output=True, text=True)
    return {"status": "ok" if completed.returncode == 0 else "fail",
            "message": (completed.stdout or completed.stderr).strip()}


def show(task_dir):
    if platform.system() != "Windows":
        return {"status": "manual", "message": "see `crontab -l`"}
    completed = subprocess.run(["schtasks", "/Query", "/TN", task_name(task_dir)],
                               capture_output=True, text=True)
    return {"status": "ok" if completed.returncode == 0 else "not_scheduled",
            "message": (completed.stdout or completed.stderr).strip()}
