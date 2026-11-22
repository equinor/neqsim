#!/usr/bin/env python
"""Write ``required_skills`` frontmatter into every ``.github/agents/*.agent.md``.

Core agents historically declared their skills as a prose line
(``Loaded skills: a, b, c``). Community and enterprise agents use the
frontmatter key ``required_skills``. This script migrates the core agents to
the same key so one reader (``agent_frontmatter.extract_required_skills``)
serves every repo and the agent-plugin build can validate agent -> skill
resolution from frontmatter alone. The prose line is kept: it is what the
agent reads at run time to know which SKILL.md files to open.

Usage:
    python devtools/sync_agent_required_skills.py            # dry run
    python devtools/sync_agent_required_skills.py --apply
    python devtools/sync_agent_required_skills.py --check    # CI: exit 1 if stale
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import agent_frontmatter as af  # noqa: E402

REPO_ROOT = Path(__file__).resolve().parent.parent
AGENTS_DIR = REPO_ROOT / ".github" / "agents"


def planned_updates():
    for agent_md in sorted(AGENTS_DIR.glob("*.agent.md")):
        text = agent_md.read_text(encoding="utf-8")
        skills = af.extract_required_skills(text)
        fm = af.parse_frontmatter(text)
        current = fm.get("required_skills")
        if isinstance(current, str):
            current = [current] if current else []
        if isinstance(current, list) and list(current) == skills:
            continue
        yield agent_md, text, skills


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply", action="store_true")
    parser.add_argument("--check", action="store_true", help="exit 1 when any agent is stale")
    args = parser.parse_args(argv)

    stale = 0
    for agent_md, text, skills in planned_updates():
        stale += 1
        print("{}: required_skills -> {}".format(agent_md.name, ", ".join(skills) or "(none)"))
        if args.apply:
            agent_md.write_text(af.set_frontmatter_list(text, "required_skills", skills),
                                encoding="utf-8")
    if stale == 0:
        print("all agents already carry required_skills frontmatter")
        return 0
    if args.check:
        print("{} agent(s) stale; run devtools/sync_agent_required_skills.py --apply".format(stale))
        return 1
    if not args.apply:
        print("dry run - re-run with --apply ({} agents)".format(stale))
    else:
        print("updated {} agents".format(stale))
    return 0


if __name__ == "__main__":
    sys.exit(main())
