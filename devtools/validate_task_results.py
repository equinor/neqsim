"""
validate_task_results.py - Lightweight Python validator for task_solve/**/results.json.

Mirrors the schema rules from neqsim.util.agentic.TaskResultValidator (Java) so it
can run in CI without building the JAR or invoking the JVM.

For stricter validation use the Java TaskResultValidator at runtime (called from
notebooks via JPype). This script is the CI gate that catches the most common
schema violations on PRs that add or modify task folders.

Usage:
    python devtools/validate_task_results.py task_solve/2026-04-21_my_task
    python devtools/validate_task_results.py task_solve/2026-04-21_my_task task_solve/2026-04-22_other
    python devtools/validate_task_results.py --all
    python devtools/validate_task_results.py --changed  # via env vars CHANGED_FILES

Exit codes:
    0 — all results.json files valid (warnings allowed)
    1 — at least one results.json has schema errors
    2 — usage / I/O error
"""

from __future__ import annotations

import argparse
import json
import os
import sys
from pathlib import Path
from typing import List, Tuple

REQUIRED_KEYS = ["key_results", "validation", "approach", "conclusions"]
RECOMMENDED_KEYS = [
    "figure_captions",
    "figure_discussion",
    "equations",
    "tables",
    "references",
    "uncertainty",
    "risk_evaluation",
    "standards_applied",
]


def validate(results: dict) -> Tuple[List[str], List[str]]:
    """Return (errors, warnings) for a parsed results.json dict."""
    errors: List[str] = []
    warnings: List[str] = []

    if not isinstance(results, dict):
        errors.append("root: results.json must be a JSON object")
        return errors, warnings

    for k in REQUIRED_KEYS:
        if k not in results:
            errors.append(f"{k}: required key is missing")
    for k in RECOMMENDED_KEYS:
        if k not in results:
            warnings.append(f"{k}: recommended key is missing")

    # key_results
    kr = results.get("key_results")
    if kr is not None:
        if not isinstance(kr, dict):
            errors.append("key_results: must be a JSON object")
        elif not kr:
            warnings.append("key_results: empty — add numerical outputs")
        else:
            for k, v in kr.items():
                if not isinstance(v, (int, float, str, bool)):
                    warnings.append(
                        f"key_results.{k}: value should be a primitive (number or string)"
                    )

    # validation
    val = results.get("validation")
    if val is not None and not isinstance(val, dict):
        errors.append("validation: must be a JSON object")

    # approach
    ap = results.get("approach")
    if ap is not None:
        if not isinstance(ap, str):
            errors.append("approach: must be a string")
        elif not ap.strip():
            warnings.append("approach: is empty")

    # conclusions
    co = results.get("conclusions")
    if co is not None:
        if not isinstance(co, str):
            errors.append("conclusions: must be a string")
        elif not co.strip():
            warnings.append("conclusions: is empty")

    # uncertainty section (Standard/Comprehensive scope)
    unc = results.get("uncertainty")
    if unc is not None:
        if not isinstance(unc, dict):
            errors.append("uncertainty: must be a JSON object")
        else:
            for need in ("p10", "p50", "p90"):
                if need not in unc:
                    warnings.append(f"uncertainty.{need}: missing percentile")

    # risk_evaluation
    risk = results.get("risk_evaluation")
    if risk is not None and not isinstance(risk, dict):
        errors.append("risk_evaluation: must be a JSON object")

    # figure_discussion entries
    fd = results.get("figure_discussion")
    if fd is not None:
        if not isinstance(fd, list):
            errors.append("figure_discussion: must be a JSON array")
        else:
            for i, item in enumerate(fd):
                if not isinstance(item, dict):
                    errors.append(f"figure_discussion[{i}]: must be an object")
                    continue
                for need in ("figure", "observation", "mechanism", "implication", "recommendation"):
                    if need not in item:
                        warnings.append(f"figure_discussion[{i}].{need}: missing field")

    return errors, warnings


def check_capability_assessment(task_folder: Path) -> List[str]:
    """Return warning list if capability_assessment.md is missing or empty.

    Mandatory for Standard/Comprehensive tasks; the validator emits a warning
    rather than an error so Quick tasks are not blocked.
    """
    warnings: List[str] = []
    cap = task_folder / "step1_scope_and_research" / "capability_assessment.md"
    if not cap.exists():
        warnings.append(
            f"{task_folder.name}: capability_assessment.md is missing — run @capability.scout (mandatory for Standard/Comprehensive)"
        )
        return warnings
    try:
        text = cap.read_text(encoding="utf-8")
    except OSError:
        warnings.append(f"{task_folder.name}: capability_assessment.md is unreadable")
        return warnings
    # Strip the template scaffolding to detect actual content
    stripped = "\n".join(
        line for line in text.splitlines() if not line.lstrip().startswith("<!--")
    )
    # Heuristic: must have at least one non-empty table row beyond the header
    if "| 1 |" in stripped and stripped.count("| 1 |") == 1 and "|  |" in stripped:
        # Looks like the unfilled template — only template placeholders
        if all(s in stripped for s in ["| 1 |  |", "| 2 |  |"]):
            warnings.append(
                f"{task_folder.name}: capability_assessment.md is the unfilled template — populate sections 2 and 3"
            )
    return warnings


def find_results_files(roots: List[Path]) -> List[Path]:
    out: List[Path] = []
    for root in roots:
        if root.is_file() and root.name == "results.json":
            out.append(root)
            continue
        if root.is_dir():
            out.extend(sorted(root.glob("**/results.json")))
    # dedupe
    seen = set()
    unique = []
    for p in out:
        rp = p.resolve()
        if rp not in seen:
            seen.add(rp)
            unique.append(p)
    return unique


def validate_file(path: Path) -> Tuple[List[str], List[str]]:
    try:
        with open(path, "r", encoding="utf-8") as f:
            data = json.load(f)
    except json.JSONDecodeError as e:
        return [f"root: invalid JSON: {e}"], []
    except OSError as e:
        return [f"root: cannot read file: {e}"], []
    return validate(data)


def main() -> int:
    parser = argparse.ArgumentParser(description="Validate task_solve results.json files")
    parser.add_argument("paths", nargs="*", help="Task folders or results.json files")
    parser.add_argument(
        "--all", action="store_true", help="Validate every results.json under task_solve/"
    )
    parser.add_argument(
        "--changed",
        action="store_true",
        help="Read changed paths from env CHANGED_FILES (newline separated)",
    )
    parser.add_argument(
        "--strict-warnings",
        action="store_true",
        help="Treat warnings as errors (exits 1 on any warning)",
    )
    args = parser.parse_args()

    repo_root = Path(__file__).resolve().parent.parent

    roots: List[Path] = []
    if args.all:
        ts = repo_root / "task_solve"
        if ts.is_dir():
            roots.append(ts)
    if args.changed:
        changed = os.environ.get("CHANGED_FILES", "")
        for line in changed.splitlines():
            line = line.strip()
            if not line:
                continue
            p = repo_root / line
            # if a file inside a task folder, include the task folder
            if "task_solve/" in line.replace("\\", "/"):
                # walk upward to the task folder (immediate child of task_solve)
                rel = Path(line.replace("\\", "/"))
                parts = rel.parts
                if "task_solve" in parts:
                    idx = parts.index("task_solve")
                    if idx + 1 < len(parts):
                        roots.append(repo_root / Path(*parts[: idx + 2]))
    for p in args.paths:
        roots.append(Path(p))

    if not roots:
        print("No paths supplied. Use positional args, --all, or --changed.", file=sys.stderr)
        return 2

    files = find_results_files(roots)
    if not files:
        print("No results.json files found in supplied paths. Skipping validation.")
        return 0

    total_errors = 0
    total_warnings = 0
    failures: List[str] = []
    capability_warnings_seen: set = set()

    for f in files:
        rel = f.relative_to(repo_root) if f.is_absolute() else f
        errors, warnings = validate_file(f)
        # Add capability_assessment check (once per task folder)
        task_folder = f.parent
        if str(task_folder) not in capability_warnings_seen:
            capability_warnings_seen.add(str(task_folder))
            warnings.extend(check_capability_assessment(task_folder))
        total_errors += len(errors)
        total_warnings += len(warnings)
        if errors or warnings:
            print(f"\n--- {rel} ---")
            for e in errors:
                print(f"  ERROR   {e}")
            for w in warnings:
                print(f"  WARN    {w}")
        else:
            print(f"OK      {rel}")
        if errors:
            failures.append(str(rel))

    print(
        f"\nSummary: {len(files)} file(s) checked, "
        f"{total_errors} error(s), {total_warnings} warning(s)."
    )
    if failures:
        print("Failed files:")
        for fp in failures:
            print(f"  - {fp}")
        return 1
    if args.strict_warnings and total_warnings > 0:
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
