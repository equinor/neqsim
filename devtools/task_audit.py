#!/usr/bin/env python3
"""
task_audit.py - Quality dashboard for task_solve/ folders.

Scans all task folders and reports:
  - Tasks missing results.json
  - Tasks with empty/template-only notes.md
  - Tasks without executed notebooks (no cell outputs)
  - Tasks without figures
  - Tasks missing TASK_LOG entries

Usage:
    python devtools/task_audit.py
    python devtools/task_audit.py --verbose
    python devtools/task_audit.py --json  # machine-readable output
"""
import os
import sys
import json
import glob
import argparse
from pathlib import Path
from datetime import datetime

REPO_ROOT = Path(__file__).resolve().parent.parent
TASK_SOLVE_DIR = REPO_ROOT / "task_solve"
TASK_LOG_FILE = REPO_ROOT / "docs" / "development" / "TASK_LOG.md"

# Folders to skip
SKIP_FOLDERS = {"TASK_TEMPLATE"}


def find_task_folders():
    """Find all task folders (exclude TASK_TEMPLATE)."""
    folders = []
    if not TASK_SOLVE_DIR.exists():
        return folders
    for entry in sorted(TASK_SOLVE_DIR.iterdir()):
        if entry.is_dir() and entry.name not in SKIP_FOLDERS:
            folders.append(entry)
    return folders


def check_results_json(task_dir):
    """Check if results.json exists and has meaningful content."""
    rj = task_dir / "results.json"
    if not rj.exists():
        return {"status": "MISSING", "detail": "No results.json found"}
    try:
        with open(rj, "r", encoding="utf-8") as f:
            data = json.load(f)
        if not data.get("key_results"):
            return {"status": "EMPTY", "detail": "results.json has no key_results"}
        keys = list(data.get("key_results", {}).keys())
        return {"status": "OK", "detail": "{} key results".format(len(keys))}
    except (json.JSONDecodeError, Exception) as e:
        return {"status": "ERROR", "detail": "Parse error: {}".format(str(e))}


def check_notes(task_dir):
    """Check if notes.md has substantive content (not just template)."""
    notes_path = task_dir / "step1_scope_and_research" / "notes.md"
    if not notes_path.exists():
        return {"status": "MISSING", "detail": "No notes.md"}
    try:
        content = notes_path.read_text(encoding="utf-8")
    except Exception:
        return {"status": "ERROR", "detail": "Cannot read notes.md"}
    # Strip template placeholders
    stripped = content.replace("[", "").replace("]", "").strip()
    lines = [l for l in stripped.split("\n") if l.strip() and not l.strip().startswith("#")]
    if len(lines) < 5:
        return {"status": "SPARSE", "detail": "Only {} content lines".format(len(lines))}
    return {"status": "OK", "detail": "{} content lines".format(len(lines))}


def check_notebooks(task_dir):
    """Check if notebooks exist and have been executed."""
    nb_dir = task_dir / "step2_analysis"
    if not nb_dir.exists():
        return {"status": "MISSING", "detail": "No step2_analysis/ directory"}
    notebooks = list(nb_dir.glob("*.ipynb"))
    if not notebooks:
        return {"status": "MISSING", "detail": "No .ipynb files in step2_analysis/"}

    results = []
    for nb_path in notebooks:
        try:
            with open(nb_path, "r", encoding="utf-8") as f:
                nb = json.load(f)
            cells = nb.get("cells", [])
            code_cells = [c for c in cells if c.get("cell_type") == "code"]
            executed = [c for c in code_cells if c.get("execution_count") is not None
                        and c.get("execution_count", 0) > 0]
            error_cells = [c for c in code_cells if any(
                o.get("output_type") == "error" for o in c.get("outputs", []))]
            results.append({
                "name": nb_path.name,
                "total_code": len(code_cells),
                "executed": len(executed),
                "errors": len(error_cells),
            })
        except Exception as e:
            results.append({
                "name": nb_path.name,
                "total_code": 0,
                "executed": 0,
                "errors": 0,
                "parse_error": str(e),
            })

    total_code = sum(r["total_code"] for r in results)
    total_exec = sum(r["executed"] for r in results)
    total_err = sum(r["errors"] for r in results)

    if total_code == 0:
        return {"status": "EMPTY", "detail": "Notebooks have no code cells",
                "notebooks": results}
    if total_exec == 0:
        return {"status": "NOT_EXECUTED", "detail": "0/{} cells executed".format(total_code),
                "notebooks": results}
    if total_err > 0:
        return {"status": "HAS_ERRORS",
                "detail": "{}/{} executed, {} errors".format(total_exec, total_code, total_err),
                "notebooks": results}
    if total_exec < total_code:
        return {"status": "PARTIAL",
                "detail": "{}/{} cells executed".format(total_exec, total_code),
                "notebooks": results}
    return {"status": "OK",
            "detail": "{}/{} cells executed".format(total_exec, total_code),
            "notebooks": results}


def check_figures(task_dir):
    """Check if task has generated figures."""
    fig_dir = task_dir / "figures"
    if not fig_dir.exists():
        return {"status": "MISSING", "detail": "No figures/ directory"}
    figs = list(fig_dir.glob("*.png")) + list(fig_dir.glob("*.jpg")) + list(fig_dir.glob("*.svg"))
    if not figs:
        return {"status": "EMPTY", "detail": "No image files in figures/"}
    return {"status": "OK", "detail": "{} figures".format(len(figs))}


def check_task_log_entry(task_dir):
    """Check if this task has an entry in TASK_LOG.md."""
    if not TASK_LOG_FILE.exists():
        return {"status": "NO_LOG", "detail": "TASK_LOG.md not found"}
    try:
        log_content = TASK_LOG_FILE.read_text(encoding="utf-8").lower()
    except Exception:
        return {"status": "ERROR", "detail": "Cannot read TASK_LOG.md"}
    # Extract date and keywords from folder name
    folder_name = task_dir.name
    parts = folder_name.split("_", 3)
    if len(parts) >= 2:
        date_str = parts[0]
        slug_words = folder_name.replace(date_str + "_", "").split("_")[:3]
        # Check if date and at least 2 slug words appear in log
        matches = sum(1 for w in slug_words if w.lower() in log_content)
        if date_str in log_content and matches >= 2:
            return {"status": "OK", "detail": "Found in TASK_LOG.md"}
    return {"status": "MISSING", "detail": "No matching entry in TASK_LOG.md"}


def check_task_spec(task_dir):
    """Check if task_spec.md exists and is filled in."""
    spec = task_dir / "step1_scope_and_research" / "task_spec.md"
    if not spec.exists():
        return {"status": "MISSING", "detail": "No task_spec.md"}
    try:
        content = spec.read_text(encoding="utf-8")
    except Exception:
        return {"status": "ERROR", "detail": "Cannot read task_spec.md"}
    if len(content) < 200:
        return {"status": "SPARSE", "detail": "Only {} chars".format(len(content))}
    return {"status": "OK", "detail": "{} chars".format(len(content))}


def audit_task(task_dir):
    """Run all checks on a single task folder."""
    return {
        "folder": task_dir.name,
        "results_json": check_results_json(task_dir),
        "notes": check_notes(task_dir),
        "task_spec": check_task_spec(task_dir),
        "notebooks": check_notebooks(task_dir),
        "figures": check_figures(task_dir),
        "task_log": check_task_log_entry(task_dir),
    }


def severity(check_result):
    """Map status to severity for summary counting."""
    status = check_result["status"]
    if status == "OK":
        return "pass"
    elif status in ("MISSING", "NOT_EXECUTED", "ERROR", "NO_LOG"):
        return "fail"
    else:
        return "warn"


def print_report(audits, verbose=False):
    """Print human-readable audit report."""
    checks = ["results_json", "notes", "task_spec", "notebooks", "figures", "task_log"]
    check_labels = {
        "results_json": "Results",
        "notes": "Notes",
        "task_spec": "TaskSpec",
        "notebooks": "Notebooks",
        "figures": "Figures",
        "task_log": "TaskLog",
    }

    # Summary counts
    total = len(audits)
    print("=" * 80)
    print("TASK SOLVE QUALITY AUDIT")
    print("=" * 80)
    print("Date: {}".format(datetime.now().strftime("%Y-%m-%d %H:%M")))
    print("Tasks scanned: {}".format(total))
    print()

    # Per-check summary
    print("{:<12} {:>6} {:>6} {:>6}".format("Check", "Pass", "Warn", "Fail"))
    print("-" * 36)
    for check in checks:
        p = sum(1 for a in audits if severity(a[check]) == "pass")
        w = sum(1 for a in audits if severity(a[check]) == "warn")
        f = sum(1 for a in audits if severity(a[check]) == "fail")
        print("{:<12} {:>6} {:>6} {:>6}".format(check_labels[check], p, w, f))
    print()

    # Per-task details
    if verbose:
        print("-" * 80)
        print("DETAILED RESULTS")
        print("-" * 80)
        for audit in audits:
            issues = []
            for check in checks:
                sev = severity(audit[check])
                if sev != "pass":
                    issues.append("[{}] {}: {}".format(
                        sev.upper(), check_labels[check], audit[check]["detail"]))
            if issues:
                print("\n{}".format(audit["folder"]))
                for issue in issues:
                    print("  {}".format(issue))
    else:
        # Condensed: only show tasks with failures
        problem_tasks = [a for a in audits
                         if any(severity(a[c]) == "fail" for c in checks)]
        if problem_tasks:
            print("TASKS WITH ISSUES ({}/{}):\n".format(len(problem_tasks), total))
            for audit in problem_tasks:
                fails = [check_labels[c] for c in checks if severity(audit[c]) == "fail"]
                print("  {} -- missing: {}".format(
                    audit["folder"][:60], ", ".join(fails)))
        else:
            print("All tasks pass quality checks.")

    # Overall score
    total_checks = total * len(checks)
    passes = sum(1 for a in audits for c in checks if severity(a[c]) == "pass")
    score = (passes / total_checks * 100) if total_checks > 0 else 0
    print("\n{} OVERALL QUALITY SCORE: {:.0f}% ({}/{} checks pass)".format(
        ">>>" if score < 70 else "   ", score, passes, total_checks))


def main():
    parser = argparse.ArgumentParser(description="Quality audit for task_solve/ folders")
    parser.add_argument("--verbose", "-v", action="store_true",
                        help="Show detailed per-task results")
    parser.add_argument("--json", action="store_true",
                        help="Output machine-readable JSON")
    args = parser.parse_args()

    folders = find_task_folders()
    if not folders:
        print("No task folders found in {}".format(TASK_SOLVE_DIR))
        sys.exit(1)

    audits = [audit_task(f) for f in folders]

    if args.json:
        print(json.dumps(audits, indent=2))
    else:
        print_report(audits, verbose=args.verbose)


if __name__ == "__main__":
    main()
