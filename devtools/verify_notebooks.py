#!/usr/bin/env python3
"""Verify Jupyter notebooks are executed and error-free.

Scans notebooks for unexecuted cells, error outputs, and missing execution counts.
Useful as a pre-commit check to ensure no unrun notebooks are delivered.

Usage:
    python devtools/verify_notebooks.py                    # scan all task_solve/
    python devtools/verify_notebooks.py path/to/notebook.ipynb  # single file
    python devtools/verify_notebooks.py --examples         # scan examples/notebooks/
    python devtools/verify_notebooks.py --json             # JSON output
"""

import argparse
import json
import os
import sys
from pathlib import Path


def check_notebook(filepath):
    """Check a single notebook for execution issues.

    Returns a dict with:
        - total_code_cells: int
        - executed_cells: int
        - unexecuted_cells: list of cell indices (1-based)
        - error_cells: list of (cell_index, error_name, error_value)
        - empty_output_cells: list of cell indices with code but no output
    """
    try:
        with open(filepath, "r", encoding="utf-8") as f:
            nb = json.load(f)
    except (OSError, json.JSONDecodeError) as e:
        return {"error": str(e)}

    cells = nb.get("cells", [])
    total_code = 0
    executed = 0
    unexecuted = []
    error_cells = []
    empty_output = []

    for i, cell in enumerate(cells, 1):
        if cell.get("cell_type") != "code":
            continue

        source = "".join(cell.get("source", [])).strip()
        if not source:
            continue  # Skip empty code cells

        total_code += 1
        exec_count = cell.get("execution_count")
        outputs = cell.get("outputs", [])

        if exec_count is None:
            unexecuted.append(i)
        else:
            executed += 1

        # Check for error outputs
        for out in outputs:
            if out.get("output_type") == "error":
                error_cells.append((
                    i,
                    out.get("ename", "Unknown"),
                    out.get("evalue", "")[:200],
                ))

        # Check for code cells with no output at all
        if exec_count is not None and not outputs:
            # Some cells legitimately produce no output (assignments, imports)
            # Only flag if cell doesn't look like pure assignment/import
            if not _is_silent_cell(source):
                empty_output.append(i)

    return {
        "total_code_cells": total_code,
        "executed_cells": executed,
        "unexecuted_cells": unexecuted,
        "error_cells": error_cells,
        "empty_output_cells": empty_output,
    }


def _is_silent_cell(source):
    """Heuristic: cells that typically produce no output."""
    lines = [l.strip() for l in source.split("\n") if l.strip() and not l.strip().startswith("#")]
    if not lines:
        return True
    # All lines are imports, assignments, or function/class defs
    silent_patterns = ("import ", "from ", "def ", "class ", "NOTEBOOK_DIR", "TASK_DIR", "FIGURES_DIR")
    return all(
        any(l.startswith(p) for p in silent_patterns) or "=" in l.split("#")[0]
        for l in lines
    )


def scan_directory(root_dir, recursive=True):
    """Find all .ipynb files under a directory."""
    root = Path(root_dir)
    if recursive:
        return sorted(root.rglob("*.ipynb"))
    return sorted(root.glob("*.ipynb"))


def format_report(results, as_json=False):
    """Format verification results."""
    if as_json:
        # Convert tuples to lists for JSON
        serializable = {}
        for path, data in results.items():
            d = dict(data)
            if "error_cells" in d:
                d["error_cells"] = [
                    {"cell": c, "error": n, "message": v}
                    for c, n, v in d["error_cells"]
                ]
            serializable[str(path)] = d
        return json.dumps(serializable, indent=2)

    lines = []
    total_files = len(results)
    issues_count = 0

    for path, data in results.items():
        if "error" in data:
            lines.append("ERROR  {}  -- {}".format(path, data["error"]))
            issues_count += 1
            continue

        problems = []
        if data["unexecuted_cells"]:
            problems.append("{} unexecuted cells: {}".format(
                len(data["unexecuted_cells"]),
                data["unexecuted_cells"]
            ))
        if data["error_cells"]:
            for c, n, v in data["error_cells"]:
                problems.append("cell {} error: {} - {}".format(c, n, v))

        if problems:
            issues_count += 1
            status = "FAIL"
        elif data["total_code_cells"] == 0:
            status = "SKIP"
        else:
            status = "OK  "

        exec_ratio = "{}/{}".format(data["executed_cells"], data["total_code_cells"])
        lines.append("{} {} [executed {}]".format(status, path, exec_ratio))
        for p in problems:
            lines.append("       {}".format(p))

    summary = "\n{} notebooks checked, {} with issues".format(total_files, issues_count)
    lines.append(summary)
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(
        description="Verify Jupyter notebooks are executed and error-free."
    )
    parser.add_argument(
        "path", nargs="?", default=None,
        help="Path to a notebook file or directory (default: task_solve/)"
    )
    parser.add_argument(
        "--examples", action="store_true",
        help="Scan examples/notebooks/ instead of task_solve/"
    )
    parser.add_argument(
        "--json", action="store_true", help="Output as JSON"
    )
    args = parser.parse_args()

    repo_root = Path(__file__).resolve().parent.parent

    if args.path:
        target = Path(args.path)
        if target.is_file():
            notebooks = [target]
        elif target.is_dir():
            notebooks = scan_directory(target)
        else:
            print("Not found: {}".format(target))
            sys.exit(1)
    elif args.examples:
        notebooks = scan_directory(repo_root / "examples" / "notebooks")
    else:
        notebooks = scan_directory(repo_root / "task_solve")

    if not notebooks:
        print("No notebooks found.")
        sys.exit(0)

    # Filter out checkpoint files
    notebooks = [nb for nb in notebooks if ".ipynb_checkpoints" not in str(nb)]

    results = {}
    for nb_path in notebooks:
        rel = nb_path.relative_to(repo_root) if str(nb_path).startswith(str(repo_root)) else nb_path
        results[str(rel)] = check_notebook(nb_path)

    output = format_report(results, as_json=args.json)
    print(output)

    # Exit with error if any issues found
    has_issues = any(
        "error" in d or d.get("unexecuted_cells") or d.get("error_cells")
        for d in results.values()
        if not isinstance(d.get("error"), str)
    )
    sys.exit(1 if has_issues else 0)


if __name__ == "__main__":
    main()
