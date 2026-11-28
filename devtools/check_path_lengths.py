"""Fail when a tracked path is too long for a Windows clone.

Windows enforces a 260-character ``MAX_PATH`` unless ``core.longpaths`` is enabled,
and the limit applies to the *absolute* path: a repository root such as
``C:\\Users\\<id>\\Documents\\GitHub\\neqsim\\`` already consumes 50-60 characters.
A tracked path longer than roughly 200 characters therefore makes ``git pull`` /
``git checkout`` fail with ``Filename too long`` on a default Windows install, and
Maven's ``target/`` mirrors (``target/test-classes/...``, ``target/reports/apidocs/...``)
add a few more characters on top.

The check reads ``git ls-files`` so it also sees staged renames and additions. It
is wired into ``.pre-commit-config.yaml`` (pre-push stage, which the
``[Core] Pre-commit Checks`` workflow runs on every pull request).

Usage::

    python devtools/check_path_lengths.py            # repo-relative budget of 200
    python devtools/check_path_lengths.py --max-length 180
    python devtools/check_path_lengths.py --root path/to/repo
"""

from __future__ import annotations

import argparse
import subprocess
import sys
from pathlib import Path

#: Repo-relative path budget. 260 (Windows MAX_PATH) minus ~60 for a typical clone root.
DEFAULT_MAX_LENGTH = 200

REMEDIATION = (
    "Shorten the file or directory name (and the Java class it holds) so the "
    "repository-relative path fits the budget. Do not raise the budget: a default "
    "Windows clone fails with 'Filename too long' once the absolute path exceeds 260 "
    "characters. Users who already hit the error can recover with "
    "`git config --global core.longpaths true`."
)


def tracked_paths(root: Path) -> list[str]:
    """Return repository-relative paths known to git (tracked plus staged)."""
    output = subprocess.check_output(["git", "ls-files", "-z", "--cached"], cwd=str(root))
    return [entry.decode("utf-8", errors="surrogateescape") for entry in output.split(b"\0") if entry]


def find_long_paths(paths: list[str], max_length: int = DEFAULT_MAX_LENGTH) -> list[tuple[int, str]]:
    """Return ``(length, path)`` pairs longer than ``max_length``, longest first."""
    offenders = [(len(path), path) for path in paths if len(path) > max_length]
    offenders.sort(key=lambda item: (-item[0], item[1]))
    return offenders


def format_report(offenders: list[tuple[int, str]], max_length: int) -> str:
    """Render a human-readable failure report."""
    lines = [
        f"{len(offenders)} tracked path(s) exceed the {max_length}-character budget "
        "and will break Windows clones without core.longpaths:",
    ]
    for length, path in offenders:
        lines.append(f"  {length:4d}  {path}")
    lines.append("")
    lines.append(REMEDIATION)
    return "\n".join(lines)


def main(argv: list[str] | None = None) -> int:
    """CLI entry point; returns the process exit code."""
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parent.parent,
                        help="repository root (default: parent of devtools/)")
    parser.add_argument("--max-length", type=int, default=DEFAULT_MAX_LENGTH,
                        help=f"maximum repository-relative path length (default: {DEFAULT_MAX_LENGTH})")
    args = parser.parse_args(argv)

    offenders = find_long_paths(tracked_paths(args.root), args.max_length)
    if offenders:
        print(format_report(offenders, args.max_length), file=sys.stderr)
        return 1
    print(f"All tracked paths are within the {args.max_length}-character budget.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
