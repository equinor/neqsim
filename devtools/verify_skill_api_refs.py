#!/usr/bin/env python
"""Detect NeqSim API drift in skill and agent instructions.

A skill that names a class NeqSim no longer has will send an agent down a dead
end that costs a whole trial-and-error loop to discover. This linter resolves
every fully-qualified ``neqsim.*`` class reference in ``.github/skills/`` and
``.github/agents/`` against the Java source tree and fails on the ones that do
not exist.

Checks performed:
  1. Fully-qualified NeqSim class references resolve to a class in
     ``src/main/java/neqsim/`` (error when they do not).
  2. Skills record ``last_verified`` in their front matter (warning when absent).
  3. ``last_verified`` is not older than ``--max-age-days`` (warning, or error
     with ``--strict``).

Only fully-qualified references are resolved. Bare class names in prose are
ignored on purpose: they produce false failures and a noisy gate that people
learn to bypass is worse than no gate.

Usage:
    python devtools/verify_skill_api_refs.py
    python devtools/verify_skill_api_refs.py --max-age-days 180 --strict
    python devtools/verify_skill_api_refs.py --json

Exit codes:
    0 - no unresolved references (warnings allowed unless --strict)
    1 - at least one unresolved reference, or a warning in --strict mode
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from datetime import date, datetime
from pathlib import Path
from typing import Dict, List, Set, Tuple

REPO_ROOT = Path(__file__).resolve().parent.parent
SOURCE_DIR = REPO_ROOT / "src" / "main" / "java" / "neqsim"
TEST_SOURCE_DIR = REPO_ROOT / "src" / "test" / "java" / "neqsim"
SCAN_DIRS = (
    REPO_ROOT / ".github" / "skills",
    REPO_ROOT / ".github" / "agents",
)

# neqsim.process.equipment.separator.Separator - package segments are lower
# case, the class starts upper case. Trailing #method / .method is stripped.
FQN_RE = re.compile(r"\bneqsim(?:\.[a-z][A-Za-z0-9_]*)+\.([A-Z][A-Za-z0-9_]*)")
FRONT_MATTER_RE = re.compile(r"^---\s*\n(.*?)\n---\s*\n", re.DOTALL)
LAST_VERIFIED_RE = re.compile(r'^last_verified:\s*"?([0-9]{4}-[0-9]{2}-[0-9]{2})"?',
                              re.MULTILINE)

# Packages that exist only as documentation placeholders or are resolved at
# runtime (jneqsim aliases in Python examples) rather than as Java files.
IGNORED_PREFIXES = ("neqsim.jneqsim",)

# Illustrative placeholders in authoring guidance, not real references.
PLACEHOLDER_CLASSES = ("ClassName", "MyClass", "SomeClass", "YourClass", "Xxx")


def collect_java_classes() -> Set[str]:
    """Return every fully-qualified NeqSim class name found in the source tree.

    Includes nested classes declared inside a file, because skills legitimately
    reference things like ``ProcessAutomation.SCHEMA_VERSION`` holders and
    inner result classes.

    Returns
    -------
    set of str
        Fully-qualified class names, e.g. ``neqsim.thermo.system.SystemSrkEos``.
    """
    classes: Set[str] = set()
    for root in (SOURCE_DIR, TEST_SOURCE_DIR):
        if not root.is_dir():
            continue
        package_root = root.parent
        for path in root.rglob("*.java"):
            package = ".".join(path.relative_to(package_root).parts[:-1])
            top_level = path.stem
            classes.add("{}.{}".format(package, top_level))
            try:
                text = path.read_text(encoding="utf-8", errors="ignore")
            except OSError:
                continue
            for nested in re.findall(
                r"\b(?:class|interface|enum)\s+([A-Z][A-Za-z0-9_]*)", text
            ):
                classes.add("{}.{}.{}".format(package, top_level, nested))
                classes.add("{}.{}".format(package, nested))
    return classes


def extract_references(text: str) -> Set[str]:
    """Return the fully-qualified NeqSim class references used in ``text``.

    Parameters
    ----------
    text : str
        Markdown content of a SKILL.md or agent file.

    Returns
    -------
    set of str
        Fully-qualified class names referenced by the document.
    """
    refs: Set[str] = set()
    for match in FQN_RE.finditer(text):
        fqn = match.group(0)
        if any(fqn.startswith(p) for p in IGNORED_PREFIXES):
            continue
        if match.group(1) in PLACEHOLDER_CLASSES:
            continue
        refs.add(fqn)
    return refs


def _last_verified(text: str):
    """Return the ``last_verified`` date from front matter, or None."""
    fm = FRONT_MATTER_RE.match(text)
    if not fm:
        return None
    found = LAST_VERIFIED_RE.search(fm.group(1))
    if not found:
        return None
    try:
        return datetime.strptime(found.group(1), "%Y-%m-%d").date()
    except ValueError:
        return None


def scan(max_age_days: int) -> Tuple[List[Dict], List[Dict]]:
    """Scan skills and agents for unresolved references and stale metadata.

    Parameters
    ----------
    max_age_days : int
        Age above which a ``last_verified`` date is reported as stale.

    Returns
    -------
    tuple of (list, list)
        ``(errors, warnings)`` as dicts with ``file`` and ``message`` keys.
    """
    known = collect_java_classes()
    errors: List[Dict] = []
    warnings: List[Dict] = []
    today = date.today()

    for scan_dir in SCAN_DIRS:
        if not scan_dir.is_dir():
            continue
        patterns = ("**/SKILL.md",) if scan_dir.name == "skills" else ("*.md",)
        for pattern in patterns:
            for path in sorted(scan_dir.glob(pattern)):
                try:
                    text = path.read_text(encoding="utf-8", errors="ignore")
                except OSError as exc:
                    errors.append({"file": str(path), "message": "cannot read: {}".format(exc)})
                    continue
                rel = str(path.relative_to(REPO_ROOT)).replace("\\", "/")

                for ref in sorted(extract_references(text)):
                    if ref not in known:
                        errors.append({
                            "file": rel,
                            "message": "unresolved NeqSim class reference: {}".format(ref),
                        })

                if path.name != "SKILL.md":
                    continue
                verified = _last_verified(text)
                if verified is None:
                    warnings.append({
                        "file": rel,
                        "message": "no last_verified date in front matter",
                    })
                else:
                    age = (today - verified).days
                    if age > max_age_days:
                        warnings.append({
                            "file": rel,
                            "message": "last_verified is {} days old (limit {})".format(
                                age, max_age_days),
                        })
    return errors, warnings


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Detect NeqSim API drift in skills and agents")
    parser.add_argument("--max-age-days", type=int, default=365,
                        help="Age at which last_verified is reported stale (default: 365)")
    parser.add_argument("--strict", action="store_true",
                        help="Treat warnings as errors")
    parser.add_argument("--json", action="store_true",
                        help="Emit a JSON report instead of text")
    args = parser.parse_args()

    if not SOURCE_DIR.is_dir():
        print("Java source tree not found at {} - skipping API reference check."
              .format(SOURCE_DIR))
        return 0

    errors, warnings = scan(args.max_age_days)

    if args.json:
        print(json.dumps({"errors": errors, "warnings": warnings}, indent=2))
    else:
        for item in errors:
            print("ERROR   {}: {}".format(item["file"], item["message"]))
        for item in warnings:
            print("WARN    {}: {}".format(item["file"], item["message"]))
        print("\nSummary: {} error(s), {} warning(s).".format(len(errors), len(warnings)))
        if errors:
            print("Fix: update the reference, or remove it if the class was renamed.")

    if errors:
        return 1
    if args.strict and warnings:
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
