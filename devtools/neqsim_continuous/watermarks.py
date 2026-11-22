"""Per-source watermarks: the last timestamp successfully read from each source."""

import json
import os
from datetime import datetime, timedelta


def parse_time(value):
    """Parse an ISO-8601 timestamp, accepting a trailing 'Z'."""
    text = str(value).strip()
    if text.endswith("Z"):
        text = text[:-1] + "+00:00"
    return datetime.fromisoformat(text)


class Watermarks(object):
    """Watermarks stored as JSON at ``path`` (``continuous/watermarks.json``)."""

    def __init__(self, path):
        self.path = str(path)
        self._data = {}
        if os.path.exists(self.path):
            with open(self.path, "r", encoding="utf-8") as f:
                self._data = json.load(f)

    def get(self, source):
        return self._data.get(source)

    def window(self, source, until, overlap=timedelta(0), default_since=None):
        """Return (since, until) for the next pull, reaching back by ``overlap``."""
        mark = self.get(source)
        since = parse_time(mark) - overlap if mark else default_since
        return since, until

    def advance(self, source, result):
        """Advance the watermark only for ``ok`` or ``partial`` results; never move it back."""
        if result.status not in ("ok", "partial") or not result.watermark:
            return False
        old = self.get(source)
        if old and parse_time(result.watermark) <= parse_time(old):
            return False
        self._data[source] = result.watermark
        self.save()
        return True

    def save(self):
        directory = os.path.dirname(self.path)
        if directory:
            os.makedirs(directory, exist_ok=True)
        temporary = self.path + ".tmp"
        with open(temporary, "w", encoding="utf-8") as f:
            json.dump(self._data, f, indent=2, sort_keys=True)
        from .plan import replace_file
        replace_file(temporary, self.path)
