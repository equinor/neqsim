"""Built-in, dependency-free source adapter for public and offline use.

``file`` reads CSV files dropped into a folder (exports from any historian, a lab
system, or a public data set) and appends the rows inside the requested window to
the task's append-only store. Site-specific adapters (historian APIs, production
databases) are supplied by community or enterprise packages through entry points.
"""

import csv
import glob
import os

from .contracts import SourceResult, register
from .watermarks import parse_time


class FileDropAdapter(object):
    """Pull rows from ``folder/pattern`` whose ``time_column`` lies in [since, until)."""

    def __init__(self, folder, time_column="timestamp", pattern="*.csv", name="file"):
        self.folder = folder
        self.time_column = time_column
        self.pattern = pattern
        self.name = name

    def pull(self, since, until, out_dir):
        files = sorted(glob.glob(os.path.join(self.folder, self.pattern)))
        if not files:
            return SourceResult("stale", message="no files in {}".format(self.folder))
        rows, header, latest = [], None, None
        for path in files:
            with open(path, "r", encoding="utf-8-sig", newline="") as f:
                reader = csv.DictReader(f)
                if self.time_column not in (reader.fieldnames or []):
                    return SourceResult("failed", message="{} has no column '{}'".format(
                        os.path.basename(path), self.time_column))
                header = header or reader.fieldnames
                for row in reader:
                    stamp = parse_time(row[self.time_column])
                    if (since is None or stamp >= since) and stamp < until:
                        rows.append(row)
                        latest = stamp if latest is None or stamp > latest else latest
        if not rows:
            return SourceResult("ok", rows=0, message="no new rows")
        target = os.path.join(out_dir, self.name, "{:%Y}".format(until), "{:%m}".format(until))
        os.makedirs(target, exist_ok=True)
        part = os.path.join(target, "part_{:%Y%m%dT%H%M%S}.csv".format(until))
        with open(part, "w", encoding="utf-8", newline="") as f:
            writer = csv.DictWriter(f, fieldnames=header)
            writer.writeheader()
            writer.writerows(rows)
        return SourceResult("ok", rows=len(rows), watermark=latest.isoformat(),
                            outputs=[part], records=rows)


register("adapters", "file", FileDropAdapter)
