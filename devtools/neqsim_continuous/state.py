"""Versioned persistent state for continuous engineering tasks.

The task folder is the source of truth. State files deliberately avoid absolute paths so a
second supported agent or machine can continue from a copied or synchronised task folder.
Legacy 1.0 state files are migrated in place; a newer incompatible schema fails closed with
an actionable error instead of being silently rewritten by an older runner.
"""

import os
import platform
from datetime import datetime, timezone

from .plan import continuous_dir, read_json, write_json

STATE_SCHEMA_VERSION = "1.1"
_SUPPORTED = (1, 1)


class StateSchemaError(ValueError):
    """Raised when a persisted state requires a newer or incompatible runner."""


def _now():
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat()


def host_id():
    """Return a stable, non-secret host label for recovery provenance."""
    value = os.environ.get("NEQSIM_CONTINUOUS_HOST", "") or platform.node().split(".")[0] or "host"
    return "".join(c for c in value.lower() if c.isalnum() or c in ("_", "-")) or "host"


def _version(value):
    text = str(value or "1.0").strip()
    try:
        major, minor = text.split(".", 1)
        return int(major), int(minor)
    except (TypeError, ValueError):
        raise StateSchemaError("Invalid continuous task state schema_version '{}'".format(text))


def _normalize(task_dir, raw):
    if not isinstance(raw, dict):
        raise StateSchemaError("continuous/state.json must contain a JSON object")
    original = str(raw.get("schema_version") or "1.0")
    version = _version(original)
    if version[0] != _SUPPORTED[0] or version > _SUPPORTED:
        raise StateSchemaError(
            "continuous/state.json uses schema {} but this NeqSim runner supports up to {}. "
            "Upgrade NeqSim before resuming the task.".format(original, STATE_SCHEMA_VERSION))
    data = dict(raw)
    migrated = version < _SUPPORTED
    if migrated:
        data.setdefault("migrated_from", original)
    data["schema_version"] = STATE_SCHEMA_VERSION
    data.setdefault("task_id", os.path.basename(os.path.abspath(str(task_dir))))
    data.setdefault("state_revision", 0)
    data.setdefault("created_at", data.get("updated") or _now())
    return data, migrated


def read_state(task_dir, persist_migration=True):
    """Load and validate state, migrating legacy 1.0 files without losing unknown fields."""
    path = os.path.join(continuous_dir(task_dir), "state.json")
    raw = read_json(path, {}) or {}
    if not raw:
        return {}
    data, migrated = _normalize(task_dir, raw)
    if migrated and persist_migration:
        write_json(path, data)
    return data


def write_state(task_dir, state, merge=False):
    """Persist one state revision atomically.

    When merge is true, unspecified fields are carried forward. Every write stamps the
    schema, revision and last writer, while leaving task-specific and unknown fields intact.
    """
    previous = read_state(task_dir, persist_migration=False) or {}
    data = dict(previous) if merge else {}
    data.update(dict(state or {}))
    data["schema_version"] = STATE_SCHEMA_VERSION
    data.setdefault("task_id", os.path.basename(os.path.abspath(str(task_dir))))
    data.setdefault("created_at", previous.get("created_at") or _now())
    data["updated"] = _now()
    data["state_revision"] = int(previous.get("state_revision", 0) or 0) + 1
    data["last_writer"] = {"host": host_id(), "python": platform.python_version()}
    write_json(os.path.join(continuous_dir(task_dir), "state.json"), data)
    return data


def update_state(task_dir, **changes):
    """Merge fields into the current persisted state and create a new revision."""
    return write_state(task_dir, changes, merge=True)


__all__ = ["STATE_SCHEMA_VERSION", "StateSchemaError", "host_id", "read_state", "write_state",
           "update_state"]
