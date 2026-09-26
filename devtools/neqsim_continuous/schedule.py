"""Schedule monitor cycles: Windows Task Scheduler (schtasks) or a cron line for servers."""

import os
import platform
import re
import subprocess
import sys

DEVTOOLS = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def task_name(task_dir):
    return "NeqSim living task " + re.sub(r"[^A-Za-z0-9_.-]", "_", os.path.basename(
        os.path.abspath(str(task_dir))))[:180]


def build(task_dir, daily="05:00", mode="monitor", python=None):
    """Return the scheduled command for Windows and a cron line for Linux servers."""
    if not re.match(r"^\d{2}:\d{2}$", daily):
        raise ValueError("daily must be HH:MM")
    python = python or sys.executable
    task_dir = os.path.abspath(str(task_dir))
    cli = os.path.join(DEVTOOLS, "neqsim_cli.py")
    command = '"{}" "{}" task-cycle "{}" --mode {}'.format(python, cli, task_dir, mode)
    hour, minute = daily.split(":")
    name = task_name(task_dir)
    return {"name": name, "command": command,
            "windows": ["schtasks", "/Create", "/F", "/SC", "DAILY", "/TN", name,
                        "/TR", command, "/ST", daily],
            "cron": "{} {} * * * {}".format(int(minute), int(hour), command)}


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
