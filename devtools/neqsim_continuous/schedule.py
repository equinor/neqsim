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


def build(task_dir, daily="05:00", every_hours=None, mode="monitor", python=None,
          standard_first=True):
    """Return the scheduled command for Windows and a cron line for Linux servers.

    Either a fixed daily time (``daily``, default) or a recurring interval in
    hours (``every_hours``, e.g. ``2`` for every 2 hours) may be used.
    """
    python = python or sys.executable
    task_dir = os.path.abspath(str(task_dir))
    cli = os.path.join(DEVTOOLS, "neqsim_cli.py")
    command = '"{}" "{}" task-cycle "{}" --mode {}'.format(python, cli, task_dir, mode)
    if standard_first:
        command += " --standard-first"
    name = task_name(task_dir)
    # schtasks' /TR argument is capped at 261 characters, which a long task path
    # (common under OneDrive) easily exceeds, so /TR always targets a short wrapper
    # script that runs the real command instead of the raw command itself.
    wrapper_path = os.path.join(task_dir, "continuous", "run_cycle.cmd")
    wrapper_script = "@echo off\r\n{}\r\n".format(command)
    if every_hours is not None:
        modifier = int(every_hours)
        if modifier < 1:
            raise ValueError("every_hours must be >= 1")
        windows = ["schtasks", "/Create", "/F", "/SC", "HOURLY", "/MO", str(modifier),
                   "/TN", name, "/TR", wrapper_path]
        cron = "0 */{} * * * {}".format(modifier, command)
    else:
        if not re.match(r"^\d{2}:\d{2}$", daily):
            raise ValueError("daily must be HH:MM")
        hour, minute = daily.split(":")
        windows = ["schtasks", "/Create", "/F", "/SC", "DAILY", "/TN", name,
                   "/TR", wrapper_path, "/ST", daily]
        cron = "{} {} * * * {}".format(int(minute), int(hour), command)
    return {"name": name, "command": command, "windows": windows, "cron": cron,
            "wrapper_path": wrapper_path, "wrapper_script": wrapper_script}


def install(spec):
    if platform.system() != "Windows":
        return {"status": "manual", "message": "add this line with `crontab -e`: " + spec["cron"]}
    if spec.get("wrapper_path") and spec.get("wrapper_script"):
        os.makedirs(os.path.dirname(spec["wrapper_path"]), exist_ok=True)
        with open(spec["wrapper_path"], "w", encoding="utf-8") as f:
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
