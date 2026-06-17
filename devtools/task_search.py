#!/usr/bin/env python3
"""Cross-task keyword search across all task_solve/ folders.

Searches results.json, notes.md, task_spec.md, and notebook sources for keywords.
Useful for finding prior work, reusable patterns, and avoiding duplicate effort.

Usage:
    python devtools/task_search.py "hydrate"
    python devtools/task_search.py "SRK CPA" --type results
    python devtools/task_search.py "compressor" --json
"""

import argparse
import json
import os
import re
import sys
from pathlib import Path


TASK_ROOT = Path(__file__).resolve().parent.parent / "task_solve"

# Files to search inside each task folder
SEARCHABLE_FILES = [
    "results.json",
    "step1_scope_and_research/task_spec.md",
    "step1_scope_and_research/notes.md",
    "step1_scope_and_research/analysis.md",
]

SEARCHABLE_GLOBS = [
    "step2_analysis/*.ipynb",
]


def search_text_file(filepath, pattern, case_insensitive=True):
    """Search a text file for a regex pattern, return matching lines."""
    flags = re.IGNORECASE if case_insensitive else 0
    matches = []
    try:
        with open(filepath, "r", encoding="utf-8", errors="replace") as f:
            for i, line in enumerate(f, 1):
                if re.search(pattern, line, flags):
                    matches.append((i, line.strip()[:200]))
    except (OSError, UnicodeDecodeError):
        pass
    return matches


def search_notebook(filepath, pattern, case_insensitive=True):
    """Search notebook cell sources for a regex pattern."""
    flags = re.IGNORECASE if case_insensitive else 0
    matches = []
    try:
        with open(filepath, "r", encoding="utf-8") as f:
            nb = json.load(f)
        for ci, cell in enumerate(nb.get("cells", []), 1):
            source = "".join(cell.get("source", []))
            for li, line in enumerate(source.split("\n"), 1):
                if re.search(pattern, line, flags):
                    matches.append(
                        (ci, li, cell.get("cell_type", "?"), line.strip()[:200])
                    )
    except (OSError, json.JSONDecodeError):
        pass
    return matches


def search_results_json(filepath, pattern, case_insensitive=True):
    """Search results.json values for a pattern, return matching keys."""
    flags = re.IGNORECASE if case_insensitive else 0
    matches = []
    try:
        with open(filepath, "r", encoding="utf-8") as f:
            data = json.load(f)

        def walk(obj, path=""):
            if isinstance(obj, dict):
                for k, v in obj.items():
                    walk(v, path + "." + k if path else k)
            elif isinstance(obj, list):
                for i, v in enumerate(obj):
                    walk(v, "{}[{}]".format(path, i))
            else:
                s = str(obj)
                if re.search(pattern, s, flags):
                    matches.append((path, s[:200]))

        walk(data)
    except (OSError, json.JSONDecodeError):
        pass
    return matches


def find_task_folders():
    """Find all task folders (exclude TASK_TEMPLATE)."""
    if not TASK_ROOT.exists():
        return []
    folders = []
    for entry in sorted(TASK_ROOT.iterdir()):
        if entry.is_dir() and entry.name != "TASK_TEMPLATE":
            folders.append(entry)
    return folders


def run_search(query, search_type="all", as_json=False):
    """Run search across all task folders."""
    pattern = re.escape(query) if not any(c in query for c in r".*+?[](){}|\\^$") else query
    folders = find_task_folders()

    if not folders:
        print("No task folders found in {}".format(TASK_ROOT))
        return

    all_results = {}

    for folder in folders:
        task_name = folder.name
        task_hits = []

        # Search fixed files
        if search_type in ("all", "spec", "notes"):
            for rel in SEARCHABLE_FILES:
                fp = folder / rel
                if fp.exists():
                    if fp.suffix == ".json":
                        hits = search_results_json(fp, pattern)
                        for path, val in hits:
                            task_hits.append({
                                "file": rel,
                                "type": "json_value",
                                "key": path,
                                "match": val,
                            })
                    else:
                        hits = search_text_file(fp, pattern)
                        for line_no, text in hits:
                            task_hits.append({
                                "file": rel,
                                "type": "text",
                                "line": line_no,
                                "match": text,
                            })

        # Search notebooks
        if search_type in ("all", "notebooks"):
            for glob_pat in SEARCHABLE_GLOBS:
                for nb_path in folder.glob(glob_pat):
                    rel = str(nb_path.relative_to(folder))
                    hits = search_notebook(nb_path, pattern)
                    for cell_no, line_no, cell_type, text in hits:
                        task_hits.append({
                            "file": rel,
                            "type": "notebook",
                            "cell": cell_no,
                            "cell_type": cell_type,
                            "line": line_no,
                            "match": text,
                        })

        # Search results.json specifically
        if search_type in ("all", "results"):
            rj = folder / "results.json"
            if rj.exists() and search_type == "results":
                hits = search_results_json(rj, pattern)
                for path, val in hits:
                    task_hits.append({
                        "file": "results.json",
                        "type": "json_value",
                        "key": path,
                        "match": val,
                    })

        if task_hits:
            all_results[task_name] = task_hits

    # Output
    if as_json:
        print(json.dumps(all_results, indent=2))
        return

    if not all_results:
        print("No matches for '{}' across {} tasks.".format(query, len(folders)))
        return

    total_hits = sum(len(v) for v in all_results.values())
    print("Found {} matches across {} tasks for '{}'\n".format(
        total_hits, len(all_results), query
    ))

    for task_name, hits in all_results.items():
        print("== {} ({} hits) ==".format(task_name, len(hits)))
        for h in hits[:5]:  # Show max 5 per task
            if h["type"] == "notebook":
                print("  [{}] cell {} ({}): {}".format(
                    h["file"], h["cell"], h["cell_type"], h["match"]
                ))
            elif h["type"] == "json_value":
                print("  [{}] {}: {}".format(h["file"], h["key"], h["match"]))
            else:
                print("  [{}] L{}: {}".format(h["file"], h["line"], h["match"]))
        if len(hits) > 5:
            print("  ... and {} more".format(len(hits) - 5))
        print()


def main():
    parser = argparse.ArgumentParser(
        description="Search across all task_solve/ folders for keywords."
    )
    parser.add_argument("query", help="Search term or regex pattern")
    parser.add_argument(
        "--type",
        choices=["all", "results", "spec", "notes", "notebooks"],
        default="all",
        help="Restrict search to specific file types (default: all)",
    )
    parser.add_argument(
        "--json", action="store_true", help="Output results as JSON"
    )
    args = parser.parse_args()
    run_search(args.query, args.type, args.json)


if __name__ == "__main__":
    main()
