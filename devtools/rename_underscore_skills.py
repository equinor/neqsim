#!/usr/bin/env python
"""Rename underscore-named skills in ``.github/skills`` to kebab-case.

Agent Skills / Agent Plugins loaders require ``name`` to match
``^[a-z0-9]+(-[a-z0-9]+)*$`` and to equal the skill folder name; skills that
violate this are silently skipped when packaged as a plugin. This script:

1. ``git mv``-renames each ``.github/skills/<a_b_c>`` folder (and its
   ``neqsim-paperlab/skills/<a_b_c>`` source when present) to ``<a-b-c>``;
2. rewrites the frontmatter ``name`` in every moved ``SKILL.md``;
3. rewrites every whole-word textual reference in tracked ``.md``, ``.py``,
   ``.json``, ``.yaml``/``.yml`` and ``.toml`` files (notebooks excluded).

Usage:
    python devtools/rename_underscore_skills.py          # dry run
    python devtools/rename_underscore_skills.py --apply
"""
from __future__ import annotations

import argparse
import re
import subprocess
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
SKILLS_DIR = REPO_ROOT / ".github" / "skills"
PAPERLAB_SKILLS_DIR = REPO_ROOT / "neqsim-paperlab" / "skills"
TEXT_SUFFIXES = (".md", ".py", ".json", ".yaml", ".yml", ".toml", ".txt")
# figure_discussion duplicates figure-discussion and shares its name with the
# results.json key, so a whole-word rewrite would corrupt schema code.
EXCLUDE = {"figure_discussion"}


def _git(*args: str) -> str:
    return subprocess.run(
        ["git", *args], cwd=str(REPO_ROOT), capture_output=True, text=True, check=True
    ).stdout


def collect_renames(include_paperlab_internal: bool) -> list[tuple[str, str]]:
    roots = [SKILLS_DIR] + ([PAPERLAB_SKILLS_DIR] if include_paperlab_internal else [])
    seen = set()
    pairs = []
    for root in roots:
        if not root.is_dir():
            continue
        for skill_dir in sorted(root.iterdir()):
            name = skill_dir.name
            if skill_dir.is_dir() and "_" in name and name not in EXCLUDE and name not in seen:
                seen.add(name)
                pairs.append((name, name.replace("_", "-")))
    return pairs


def rewrite_frontmatter_name(skill_md: Path, new_name: str) -> None:
    text = skill_md.read_text(encoding="utf-8")
    updated = re.sub(
        r"^(name:\s*)[\"']?[A-Za-z0-9_.-]+[\"']?\s*$",
        lambda m: "{}{}".format(m.group(1), new_name),
        text,
        count=1,
        flags=re.MULTILINE,
    )
    if updated != text:
        skill_md.write_text(updated, encoding="utf-8")


def rewrite_references(pairs: list[tuple[str, str]], apply: bool) -> int:
    pattern = re.compile(r"\b(" + "|".join(re.escape(old) for old, _ in pairs) + r")\b")
    mapping = dict(pairs)
    touched = 0
    for rel in _git("ls-files").splitlines():
        if not rel.lower().endswith(TEXT_SUFFIXES):
            continue
        path = REPO_ROOT / rel
        try:
            text = path.read_text(encoding="utf-8")
        except (UnicodeDecodeError, OSError):
            continue
        updated = pattern.sub(lambda m: mapping[m.group(1)], text)
        if updated != text:
            touched += 1
            print("rewrite {}".format(rel))
            if apply:
                path.write_text(updated, encoding="utf-8")
    return touched


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply", action="store_true")
    parser.add_argument("--paperlab-internal", action="store_true",
                        help="also rename skills that exist only under neqsim-paperlab/skills")
    args = parser.parse_args(argv)

    pairs = collect_renames(args.paperlab_internal)
    if not pairs:
        print("no underscore-named skills under .github/skills")
        return 0

    for old, new in pairs:
        for base in (SKILLS_DIR, PAPERLAB_SKILLS_DIR):
            src = base / old
            if not src.is_dir():
                continue
            dst = base / new
            if dst.exists():
                print("ERROR: {} already exists".format(dst), file=sys.stderr)
                return 1
            print("git mv {} {}".format(src.relative_to(REPO_ROOT).as_posix(), new))
            if args.apply:
                _git("mv", str(src), str(dst))
                skill_md = dst / "SKILL.md"
                if skill_md.exists():
                    rewrite_frontmatter_name(skill_md, new)

    touched = rewrite_references(pairs, args.apply)
    print("{} {} skills, {} {} files".format(
        "renamed" if args.apply else "would rename", len(pairs),
        "rewrote" if args.apply else "would rewrite", touched))
    if not args.apply:
        print("dry run - re-run with --apply")
    return 0


if __name__ == "__main__":
    sys.exit(main())
