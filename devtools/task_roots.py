#!/usr/bin/env python3
"""Resolve the folders that hold solved task folders.

Task folders do not have to live in the repository: `neqsim --set-task-root`
stores a destination in ~/.neqsim/task_defaults.json and NEQSIM_TASK_ROOT
overrides it per shell.  Cross-task tooling (search, audit, index, bulk
validation) must look in the same places the task creator writes to, otherwise
it silently reports on a stale corpus.

Resolution order, most specific first:
  1. explicit roots passed by the caller (``--task-root``)
  2. NEQSIM_TASK_ROOT (may list several roots separated by os.pathsep)
  3. ``task_root`` and ``task_roots`` in ~/.neqsim/task_defaults.json
  4. <repository>/task_solve

Every existing root is kept, so a corpus split across several destinations is
searched as one.

Usage:
    from task_roots import resolve_task_roots, find_task_folders
    folders = find_task_folders(resolve_task_roots(args.task_root))
"""
import argparse
import json
import os
from pathlib import Path

SETTINGS_FILE = Path.home() / ".neqsim" / "task_defaults.json"
REPO_ROOT = Path(__file__).resolve().parent.parent

# Support folders inside a task root that are not themselves tasks.
NON_TASK_FOLDERS = {"TASK_TEMPLATE", ".task_template", "runner_output", "__pycache__"}


def _settings():
    """Return the saved task settings, or an empty dict when unreadable."""
    try:
        with open(SETTINGS_FILE, "r", encoding="utf-8-sig") as handle:
            data = json.load(handle)
        return data if isinstance(data, dict) else {}
    except (OSError, ValueError):
        return {}


def _split(value):
    """Split a settings or environment value that may list several roots."""
    if not value:
        return []
    if isinstance(value, (list, tuple)):
        return [str(item) for item in value if str(item).strip()]
    return [part for part in str(value).split(os.pathsep) if part.strip()]


def _normalize(value):
    """Expand a configured root; '.' means the current working directory."""
    text = str(value).strip().strip('"')
    if not text:
        return None
    if text in (".", "cwd", "$PWD"):
        return Path.cwd()
    return Path(os.path.expandvars(os.path.expanduser(text)))


def resolve_task_roots(explicit=None, include_repo=True):
    """Return every existing task root, most specific first, deduplicated."""
    candidates = []
    candidates.extend(_split(explicit))
    candidates.extend(_split(os.environ.get("NEQSIM_TASK_ROOT")))
    settings = _settings()
    candidates.extend(_split(settings.get("task_root")))
    candidates.extend(_split(settings.get("task_roots")))
    if include_repo:
        candidates.append(str(REPO_ROOT / "task_solve"))

    roots = []
    seen = set()
    for candidate in candidates:
        path = _normalize(candidate)
        if path is None:
            continue
        try:
            resolved = path.resolve()
        except OSError:
            continue
        key = str(resolved).lower() if os.name == "nt" else str(resolved)
        if key in seen or not resolved.is_dir():
            continue
        seen.add(key)
        roots.append(resolved)
    return roots


def is_task_folder(path):
    """Return True when a folder looks like a task folder rather than clutter."""
    if not path.is_dir() or path.name.startswith(".") or path.name in NON_TASK_FOLDERS:
        return False
    markers = ("results.json", "study_config.yaml", "step1_scope_and_research",
               "step2_analysis", "step3_report", "task_spec.md")
    return any((path / marker).exists() for marker in markers)


def find_task_folders(roots=None, explicit=None):
    """Return all task folders across the given roots, newest name last.

    When the same folder name exists in several roots the first root wins, so
    an explicit ``--task-root`` shadows a duplicate in the saved default.
    """
    if roots is None:
        roots = resolve_task_roots(explicit)
    folders = []
    by_name = {}
    for root in roots:
        try:
            entries = sorted(root.iterdir())
        except OSError:
            continue
        for entry in entries:
            if not is_task_folder(entry):
                continue
            if entry.name in by_name:
                continue
            by_name[entry.name] = entry
            folders.append(entry)
    folders.sort(key=lambda p: p.name)
    return folders


def duplicate_task_folders(roots=None, explicit=None):
    """Return {folder name: [paths]} for task folders present in several roots."""
    if roots is None:
        roots = resolve_task_roots(explicit)
    seen = {}
    for root in roots:
        try:
            entries = sorted(root.iterdir())
        except OSError:
            continue
        for entry in entries:
            if is_task_folder(entry):
                seen.setdefault(entry.name, []).append(entry)
    return {name: paths for name, paths in seen.items() if len(paths) > 1}


def add_task_root_argument(parser):
    """Register the shared --task-root option on an argparse parser."""
    parser.add_argument(
        "--task-root",
        action="append",
        metavar="PATH",
        help="Folder holding task folders (repeatable). Defaults to "
             "NEQSIM_TASK_ROOT, the saved neqsim --set-task-root value, "
             "then <repo>/task_solve.",
    )
    return parser


def describe(roots):
    """Return a one-line human summary of the roots being used."""
    if not roots:
        return "no task root found"
    return ", ".join(str(root) for root in roots)


def main():
    """Print the resolved roots and the task folders found in them."""
    parser = argparse.ArgumentParser(
        description="Show which task roots the cross-task tooling will use.")
    add_task_root_argument(parser)
    parser.add_argument("--json", action="store_true", help="Machine-readable output")
    args = parser.parse_args()

    roots = resolve_task_roots(args.task_root)
    folders = find_task_folders(roots)
    duplicates = duplicate_task_folders(roots)

    if args.json:
        print(json.dumps({
            "roots": [str(root) for root in roots],
            "task_count": len(folders),
            "tasks": [str(folder) for folder in folders],
            "duplicates": {name: [str(p) for p in paths]
                           for name, paths in duplicates.items()},
        }, indent=2))
        return 0

    print("Task roots ({}):".format(len(roots)))
    for root in roots:
        count = sum(1 for entry in root.iterdir() if is_task_folder(entry))
        print("  {}  ({} tasks)".format(root, count))
    print("\n{} unique task folders".format(len(folders)))
    if duplicates:
        print("\n{} folder name(s) exist in more than one root — the copies will "
              "diverge:".format(len(duplicates)))
        for name in sorted(duplicates)[:20]:
            print("  {}".format(name))
            for path in duplicates[name]:
                print("      {}".format(path))
        if len(duplicates) > 20:
            print("  ... and {} more".format(len(duplicates) - 20))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
