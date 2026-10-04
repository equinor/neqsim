"""Schedule monitor cycles: Windows Task Scheduler (schtasks) or a cron line for servers."""

import math
import os
import platform
import re
import subprocess
import sys

DEVTOOLS = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def task_name(task_dir):
    return "NeqSim living task " + re.sub(r"[^A-Za-z0-9_.-]", "_", os.path.basename(
        os.path.abspath(str(task_dir))))[:180]


def build(task_dir, daily=None, mode="monitor", python=None,
          standard_first=True, every_hours=None):
    """Return the scheduled command for Windows and a cron line for Linux servers.

    Use a daily clock time (05:00 by default) or whole-hour intervals dividing
    24, anchored at midnight. Cron cannot express other uniform hour intervals.
    """
    python = python or sys.executable
    task_dir = os.path.abspath(str(task_dir))
    cli = os.path.join(DEVTOOLS, "neqsim_cli.py")
    command = '"{}" "{}" task-cycle "{}" --mode {}'.format(python, cli, task_dir, mode)
    if standard_first:
        command += " --standard-first"
    name = task_name(task_dir)
    # A wrapper shortens /TR; install() still checks the actual path length.
    wrapper_path = os.path.join(task_dir, "continuous", "run_cycle.cmd")
    wrapper_script = "@echo off\r\n{}\r\n".format(command)
    if every_hours is not None:
        if daily is not None:
            raise ValueError("choose daily or every_hours, not both")
        if not math.isfinite(every_hours) or every_hours not in (1, 2, 3, 4, 6, 8, 12, 24):
            raise ValueError("every_hours must be one of 1, 2, 3, 4, 6, 8, 12, 24")
        modifier = int(every_hours)
        windows = ["schtasks", "/Create", "/F", "/SC", "HOURLY", "/MO", str(modifier),
                   "/TN", name, "/TR", '"{}"'.format(wrapper_path), "/ST", "00:00"]
        hour_field = "0" if modifier == 24 else "*/{}".format(modifier)
        cron = "0 {} * * * {}".format(hour_field, command)
    else:
        daily = "05:00" if daily is None else daily
        if not re.fullmatch(r"(?:[01]\d|2[0-3]):[0-5]\d", daily):
            raise ValueError("daily must be HH:MM")
        hour, minute = daily.split(":")
        windows = ["schtasks", "/Create", "/F", "/SC", "DAILY", "/TN", name,
                   "/TR", '"{}"'.format(wrapper_path), "/ST", daily]
        cron = "{} {} * * * {}".format(int(minute), int(hour), command)
    return {"name": name, "command": command, "windows": windows, "cron": cron,
            "wrapper_path": wrapper_path, "wrapper_script": wrapper_script}


def install(spec):
    if platform.system() != "Windows":
        return {"status": "manual", "message": "add this line with `crontab -e`: " + spec["cron"]}
    target = spec["windows"][spec["windows"].index("/TR") + 1]
    if len(target) > 261:
        return {"status": "fail", "message": "Task wrapper path exceeds the Windows /TR limit; "
                "move the task to a shorter path before installing."}
    if spec.get("wrapper_path") and spec.get("wrapper_script"):
        os.makedirs(os.path.dirname(spec["wrapper_path"]), exist_ok=True)
        with open(spec["wrapper_path"], "w", encoding="utf-8", newline="") as f:
            f.write(spec["wrapper_script"])
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
