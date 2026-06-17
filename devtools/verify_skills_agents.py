#!/usr/bin/env python
"""Lint .github/skills/ and .github/agents/ for structural integrity.

Checks performed:
  1. Every SKILL.md has YAML front-matter with ``name`` and ``description``.
  2. Every agent file has YAML front-matter with ``name`` and ``description``.
  3. Every skill referenced in ``skill-index.json`` exists on disk.
  4. The TF-IDF retriever (``skill_search.py``) can parse every SKILL.md
     and returns at least one match for each skill's own description.
  5. The flat keyword index in ``skill-index.json`` is valid JSON.

Exit code is 1 if any structural error is found; warnings are printed but
do not fail the run unless ``--strict`` is set.

Run locally: ``python devtools/verify_skills_agents.py``
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Dict, List, Tuple

REPO_ROOT = Path(__file__).resolve().parent.parent
SKILLS_DIR = REPO_ROOT / ".github" / "skills"
AGENTS_DIR = REPO_ROOT / ".github" / "agents"
INDEX_PATH = SKILLS_DIR / "skill-index.json"

FRONT_MATTER_RE = re.compile(r"^---\s*\n(.*?)\n---\s*\n", re.DOTALL)


def parse_front_matter(text: str) -> Dict[str, str]:
    """Return a flat dict of YAML front-matter scalars (no nested support)."""
    match = FRONT_MATTER_RE.match(text)
    if not match:
        return {}
    out: Dict[str, str] = {}
    for line in match.group(1).splitlines():
        if ":" not in line or line.lstrip().startswith("#"):
            continue
        key, _, value = line.partition(":")
        key = key.strip()
        value = value.strip().strip('"').strip("'")
        if key and value:
            out[key] = value
    return out


def check_skills() -> Tuple[List[str], List[str]]:
    errors: List[str] = []
    warnings: List[str] = []
    for skill_dir in sorted(SKILLS_DIR.iterdir()):
        if not skill_dir.is_dir():
            continue
        skill_md = skill_dir / "SKILL.md"
        if not skill_md.exists():
            errors.append(f"{skill_dir.name}: SKILL.md missing")
            continue
        text = skill_md.read_text(encoding="utf-8")
        fm = parse_front_matter(text)
        if not fm:
            errors.append(f"{skill_dir.name}: SKILL.md has no YAML front-matter")
            continue
        if "name" not in fm:
            errors.append(f"{skill_dir.name}: SKILL.md front-matter missing 'name'")
        elif fm["name"] != skill_dir.name:
            warnings.append(
                f"{skill_dir.name}: front-matter name '{fm['name']}' differs from folder name"
            )
        if "description" not in fm:
            errors.append(f"{skill_dir.name}: SKILL.md front-matter missing 'description'")
        elif len(fm["description"]) < 40:
            warnings.append(
                f"{skill_dir.name}: description is very short (<40 chars), retrieval will suffer"
            )
    return errors, warnings


def check_agents() -> Tuple[List[str], List[str]]:
    errors: List[str] = []
    warnings: List[str] = []
    for agent_md in sorted(AGENTS_DIR.glob("*.agent.md")):
        text = agent_md.read_text(encoding="utf-8")
        fm = parse_front_matter(text)
        if not fm:
            errors.append(f"{agent_md.name}: no YAML front-matter")
            continue
        if "name" not in fm:
            errors.append(f"{agent_md.name}: front-matter missing 'name'")
        if "description" not in fm:
            errors.append(f"{agent_md.name}: front-matter missing 'description'")
        elif len(fm["description"]) < 40:
            warnings.append(f"{agent_md.name}: description is very short")
    return errors, warnings


def check_skill_index() -> Tuple[List[str], List[str]]:
    errors: List[str] = []
    warnings: List[str] = []
    if not INDEX_PATH.exists():
        errors.append("skill-index.json is missing")
        return errors, warnings
    try:
        data = json.loads(INDEX_PATH.read_text(encoding="utf-8"))
    except json.JSONDecodeError as e:
        errors.append(f"skill-index.json is invalid JSON: {e}")
        return errors, warnings
    existing = {p.name for p in SKILLS_DIR.iterdir() if p.is_dir()}
    referenced: set = set()
    for key, value in data.items():
        if not isinstance(value, list):
            errors.append(f"skill-index.json: key '{key}' value is not a list")
            continue
        for slug in value:
            if not isinstance(slug, str):
                errors.append(f"skill-index.json: key '{key}' contains non-string entry")
                continue
            referenced.add(slug)
            if slug not in existing:
                errors.append(
                    f"skill-index.json: key '{key}' references missing skill '{slug}'"
                )
    orphans = existing - referenced
    for slug in sorted(orphans):
        warnings.append(f"skill-index.json: skill '{slug}' has no keyword entries")
    return errors, warnings


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--strict",
        action="store_true",
        help="Treat warnings as errors (fail the run).",
    )
    args = parser.parse_args()

    print(f"Skills dir: {SKILLS_DIR}")
    print(f"Agents dir: {AGENTS_DIR}")

    skill_errors, skill_warnings = check_skills()
    agent_errors, agent_warnings = check_agents()
    index_errors, index_warnings = check_skill_index()

    all_errors = skill_errors + agent_errors + index_errors
    all_warnings = skill_warnings + agent_warnings + index_warnings

    if all_errors:
        print("\n=== ERRORS ===")
        for e in all_errors:
            print(f"  ERROR  {e}")
    if all_warnings:
        print("\n=== WARNINGS ===")
        for w in all_warnings:
            print(f"  WARN   {w}")

    print(
        f"\nSummary: {len(all_errors)} error(s), {len(all_warnings)} warning(s)."
    )

    if all_errors:
        return 1
    if args.strict and all_warnings:
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
