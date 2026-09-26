"""Load the living-task files: ``cycle_plan.yaml``, ``goal.yaml`` and the baseline."""

import hashlib
import json
import os
import time

DEFAULT_STAGES = ["sense", "refresh", "kpis", "drift", "goal", "diff", "ledger", "digest",
                  "notify", "agent"]
PLAN_FILE = "cycle_plan.yaml"
GOAL_FILE = "goal.yaml"


def continuous_dir(task_dir):
    return os.path.join(str(task_dir), "continuous")


def is_living(task_dir):
    return os.path.isfile(os.path.join(continuous_dir(task_dir), PLAN_FILE))


def _read_yaml(path):
    if not os.path.exists(path):
        return {}
    import yaml  # lazy: the package must import without PyYAML installed

    with open(path, "r", encoding="utf-8") as f:
        return yaml.safe_load(f) or {}


def _read_json(path, default=None):
    if not os.path.exists(path):
        return default
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def load_plan(task_dir):
    """Return the cycle plan with defaults for every optional section."""
    plan = _read_yaml(os.path.join(continuous_dir(task_dir), PLAN_FILE))
    plan.setdefault("schema_version", "1.0")
    plan.setdefault("stages", list(DEFAULT_STAGES))
    for key in ("sources", "kpis", "scripts", "triggers", "notify", "agent", "solve",
                "backtest", "drift", "report"):
        plan.setdefault(key, {})
    if not isinstance(plan["stages"], list):
        raise ValueError("cycle_plan.yaml: 'stages' must be a list")
    return plan


def load_goal(task_dir):
    return _read_yaml(os.path.join(continuous_dir(task_dir), GOAL_FILE))


def load_baseline(task_dir):
    base = os.path.join(continuous_dir(task_dir), "baseline")
    return {"meta": _read_json(os.path.join(base, "baseline.json"), {}),
            "kpis": _read_json(os.path.join(base, "kpis.json"), {})}


def file_sha256(path):
    if not path or not os.path.isfile(path):
        return None
    digest = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            digest.update(chunk)
    return digest.hexdigest()


def replace_file(temporary, path, attempts=20):
    """``os.replace`` that tolerates the transient Windows locks of virus scanners and OneDrive."""
    for attempt in range(attempts):
        try:
            os.replace(temporary, path)
            return
        except PermissionError:
            if attempt == attempts - 1:
                raise
            time.sleep(0.05 * (attempt + 1))


def write_json(path, data):
    directory = os.path.dirname(path)
    if directory:
        os.makedirs(directory, exist_ok=True)
    temporary = path + ".tmp"
    with open(temporary, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, sort_keys=True, default=str)
    replace_file(temporary, path)


def read_json(path, default=None):
    return _read_json(path, default)
