#!/usr/bin/env python
"""Mirror ``context_skills`` and ``coordinated_agents`` from ``agent.yaml`` into ``AGENT.md``.

Community and enterprise agents keep their relationships in two files: the
machine manifest ``agent.yaml`` (read by the orchestration and skill maps) and
the ``AGENT.md`` frontmatter (the only file the agent plugin ships and VS Code
reads). When the two drift, plugin users silently lose the skills and the
hand-off partners an agent was designed to work with. ``agent.yaml`` is the
source of truth; this script copies its lists into the ``AGENT.md`` frontmatter.

Usage:
    python devtools/sync_agent_relationships.py                 # dry run, both agent repos
    python devtools/sync_agent_relationships.py --apply
    python devtools/sync_agent_relationships.py --check         # CI: exit 1 if stale
    python devtools/sync_agent_relationships.py --repo PATH ... # explicit agent repos
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path
from typing import Iterator, List, Sequence, Tuple

import yaml

sys.path.insert(0, str(Path(__file__).resolve().parent))

import agent_frontmatter as af  # noqa: E402

REPO_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_REPOS = [
    REPO_ROOT.parent / "neqsim-community-agents",
    REPO_ROOT.parent / "neqsim-enterprise-agents",
]
KEYS = ("context_skills", "coordinated_agents")


def _as_list(value) -> List[str]:
    """Normalise a YAML scalar or list into a list of strings."""
    if isinstance(value, str):
        return [value] if value else []
    return [str(v) for v in (value or [])]


def planned_updates(repo: Path) -> Iterator[Tuple[Path, str, str, List[str]]]:
    """Yield ``(agent_md, text, key, values)`` for every stale frontmatter key."""
    for agent_dir in sorted((repo / "agents").iterdir()):
        agent_md = agent_dir / "AGENT.md"
        manifest = agent_dir / "agent.yaml"
        if not (agent_md.exists() and manifest.exists()):
            continue
        data = yaml.safe_load(manifest.read_text(encoding="utf-8")) or {}
        text = agent_md.read_text(encoding="utf-8")
        front = af.parse_frontmatter(text)
        for key in KEYS:
            wanted = _as_list(data.get(key))
            if wanted and _as_list(front.get(key)) != wanted:
                yield agent_md, text, key, wanted


def main(argv: Sequence[str] = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", nargs="*", type=Path, default=DEFAULT_REPOS)
    parser.add_argument("--apply", action="store_true")
    parser.add_argument("--check", action="store_true", help="exit 1 when any agent is stale")
    args = parser.parse_args(argv)

    stale = 0
    for repo in args.repo:
        if not (repo / "agents").is_dir():
            print("skip {} (no agents/ folder)".format(repo))
            continue
        pending = {}
        for agent_md, text, key, values in planned_updates(repo):
            stale += 1
            pending.setdefault(agent_md, (text, []))[1].append((key, values))
        for agent_md, (text, updates) in pending.items():
            print("{}: {}".format(agent_md.parent.name,
                                  "; ".join("{} ({})".format(k, len(v)) for k, v in updates)))
            if args.apply:
                for key, values in updates:
                    text = af.set_frontmatter_list(text, key, values)
                agent_md.write_text(text, encoding="utf-8", newline="")
    if stale == 0:
        print("AGENT.md frontmatter already matches agent.yaml")
        return 0
    if args.check:
        print("{} stale key(s); run devtools/sync_agent_relationships.py --apply".format(stale))
        return 1
    print("updated {} key(s)".format(stale) if args.apply
          else "dry run - re-run with --apply ({} key(s))".format(stale))
    return 0


if __name__ == "__main__":
    sys.exit(main())
