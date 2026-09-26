"""Append-only improvement ledger (JSON Lines).

Each line is one event. The current state of every opportunity is the fold of its
events, so history cannot be rewritten, and two copies of a ledger (laptop and
server) merge by taking the union of events.
"""

import json
import os
import tempfile
import uuid
from datetime import datetime, timezone

STATUSES = ("proposed", "under_review", "accepted", "rejected", "implemented",
            "verified", "not_verified", "superseded")
TRANSITIONS = {
    "proposed": ("under_review", "accepted", "rejected", "superseded"),
    "under_review": ("accepted", "rejected", "superseded"),
    "accepted": ("implemented", "rejected", "superseded"),
    "implemented": ("verified", "not_verified"),
    "not_verified": ("under_review", "superseded"),
    "verified": (),
    "rejected": (),
    "superseded": (),
}
CATEGORIES = ("operational", "maintenance", "modification", "model", "data", "tooling")


class LedgerError(ValueError):
    """Raised for an invalid ledger event."""


def _now():
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


class Ledger(object):
    """An improvement ledger stored at ``path`` (``continuous/ledger/events.jsonl``)."""

    def __init__(self, path):
        self.path = str(path)

    def events(self):
        if not os.path.exists(self.path):
            return []
        with open(self.path, "r", encoding="utf-8") as f:
            return [json.loads(line) for line in f if line.strip()]

    def current(self):
        """Return {opportunity id: state dict} folded from all events."""
        items = {}
        for event in self.events():
            key = event.get("id")
            kind = event.get("event")
            if kind == "created":
                items[key] = {k: v for k, v in event.items() if k not in ("event", "event_id")}
                items[key].setdefault("status", "proposed")
                items[key]["history"] = []
            elif key in items:
                item = items[key]
                if kind == "status_changed":
                    item["status"] = event["to"]
                elif kind == "value_updated":
                    item["value"] = event["value"]
                item["history"].append({k: v for k, v in event.items() if k != "id"})
        return items

    def _append(self, event):
        event.setdefault("event_id", uuid.uuid4().hex)
        event.setdefault("at", _now())
        directory = os.path.dirname(self.path)
        if directory:
            os.makedirs(directory, exist_ok=True)
        with open(self.path, "a", encoding="utf-8") as f:
            f.write(json.dumps(event, sort_keys=True) + "\n")
        return event

    def next_id(self):
        numbers = [int(k.split("-")[1]) for k in self.current() if str(k).startswith("OPP-")]
        return "OPP-{:04d}".format(max(numbers, default=0) + 1)

    def create(self, title, category, value=None, evidence=None, **fields):
        if category not in CATEGORIES:
            raise LedgerError("Unknown category '{}'. Valid: {}".format(category, ", ".join(CATEGORIES)))
        event = dict(fields, event="created", id=fields.get("id") or self.next_id(),
                     title=title, category=category, status="proposed",
                     value=value, evidence=list(evidence or []))
        return self._append(event)

    def set_status(self, key, to, by, note=""):
        items = self.current()
        if key not in items:
            raise LedgerError("Unknown opportunity '{}'".format(key))
        current = items[key]["status"]
        if to not in TRANSITIONS.get(current, ()):
            raise LedgerError("Transition {} -> {} is not allowed".format(current, to))
        return self._append({"event": "status_changed", "id": key, "from": current,
                             "to": to, "by": by, "note": note})

    def update_value(self, key, value, cycle=""):
        if key not in self.current():
            raise LedgerError("Unknown opportunity '{}'".format(key))
        return self._append({"event": "value_updated", "id": key, "value": value, "cycle": cycle})

    def merge(self, other_path):
        """Union this ledger with another copy; returns the number of new events."""
        mine = self.events()
        seen = {e["event_id"] for e in mine}
        added = [e for e in Ledger(other_path).events() if e.get("event_id") not in seen]
        if not added:
            return 0
        merged = sorted(mine + added, key=lambda e: (e.get("at", ""), e["event_id"]))
        directory = os.path.dirname(self.path) or "."
        os.makedirs(directory, exist_ok=True)
        handle, temporary = tempfile.mkstemp(dir=directory, suffix=".jsonl")
        with os.fdopen(handle, "w", encoding="utf-8") as f:
            for event in merged:
                f.write(json.dumps(event, sort_keys=True) + "\n")
        from .plan import replace_file
        replace_file(temporary, self.path)
        return len(added)
