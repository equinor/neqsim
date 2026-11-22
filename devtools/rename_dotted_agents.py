#!/usr/bin/env python
"""Rename dotted ``.github/agents/<a.b>.agent.md`` files to kebab-case ids.

Agent-plugin marketplaces and ``enabledPlugins`` keys reject dots in agent ids,
and ``install_agent.py`` already has to normalise ``.`` to ``-`` when deriving
an id. This script makes the filename the canonical kebab-case id:

1. ``git mv`` ``<a.b>.agent.md`` -> ``<a-b>.agent.md``;
2. rewrites ``<a.b>.agent.md`` path references and ``@a.b`` chat handles in
   tracked text files (notebooks excluded). The human-readable frontmatter
   ``name`` is left untouched.

Usage:
    python devtools/rename_dotted_agents.py          # dry run
    python devtools/rename_dotted_agents.py --apply
"""
from __future__ import annotations

import argparse
import re
import subprocess
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
AGENTS_DIR = REPO_ROOT / ".github" / "agents"
TEXT_SUFFIXES = (".md", ".py", ".json", ".yaml", ".yml", ".toml", ".txt")


def _git(*args: str) -> str:
    return subprocess.run(
        ["git", *args], cwd=str(REPO_ROOT), capture_output=True, text=True, check=True
    ).stdout


def collect_renames() -> list[tuple[str, str]]:
    pairs = []
    for agent_md in sorted(AGENTS_DIR.glob("*.agent.md")):
        handle = agent_md.name[: -len(".agent.md")]
        if "." in handle:
            pairs.append((handle, handle.replace(".", "-")))
    return pairs


def rewrite_references(pairs: list[tuple[str, str]], apply: bool) -> int:
    mapping = dict(pairs)
    alternation = "|".join(re.escape(old) for old, _ in pairs)
    file_ref = re.compile(r"\b(" + alternation + r")\.agent\.md\b")
    handle_ref = re.compile(r"@(" + alternation + r")(?![\w.-])")
    touched = 0
    for rel in _git("ls-files").splitlines():
        if not rel.lower().endswith(TEXT_SUFFIXES):
            continue
        path = REPO_ROOT / rel
        try:
            text = path.read_text(encoding="utf-8")
        except (UnicodeDecodeError, OSError):
            continue
        updated = file_ref.sub(lambda m: mapping[m.group(1)] + ".agent.md", text)
        updated = handle_ref.sub(lambda m: "@" + mapping[m.group(1)], updated)
        if updated != text:
            touched += 1
            print("rewrite {}".format(rel))
            if apply:
                path.write_text(updated, encoding="utf-8")
    return touched


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply", action="store_true")
    args = parser.parse_args(argv)

    pairs = collect_renames()
    if not pairs:
        print("no dotted agent filenames under .github/agents")
        return 0
    for old, new in pairs:
        src = AGENTS_DIR / (old + ".agent.md")
        dst = AGENTS_DIR / (new + ".agent.md")
        if dst.exists():
            print("ERROR: {} already exists".format(dst), file=sys.stderr)
            return 1
        print("git mv {} {}".format(src.name, dst.name))
        if args.apply:
            _git("mv", str(src), str(dst))
    touched = rewrite_references(pairs, args.apply)
    print("{} {} agents, {} {} files".format(
        "renamed" if args.apply else "would rename", len(pairs),
        "rewrote" if args.apply else "would rewrite", touched))
    if not args.apply:
        print("dry run - re-run with --apply")
    return 0


if __name__ == "__main__":
    sys.exit(main())
