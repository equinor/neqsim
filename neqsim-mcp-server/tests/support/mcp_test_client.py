"""Shared, dependency-free helpers for packaged MCP qualification tests."""

import json


def require(condition, message, detail=None):
    """Raise a compact assertion with optional JSON detail."""
    if condition:
        return
    suffix = ""
    if detail is not None:
        suffix = "\n" + json.dumps(detail, indent=2, sort_keys=True)
    raise AssertionError(message + suffix)
