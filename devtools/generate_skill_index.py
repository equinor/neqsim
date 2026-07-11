#!/usr/bin/env python3
"""Generate the shared skill-id index consumed by the agent manifest validators.

Builds the authoritative set of known skill ids by scanning, when present:

* core skills in the NeqSim repo    (.github/skills/*/SKILL.md, neqsim-*)
* community skills                  (neqsim-community-skills/skills/*/*/SKILL.md, neqsim-*)
* enterprise skills                 (neqsim-enterprise-skills/skills/*/*/SKILL.md, enterprise-*)

The repos are expected to be checked out side by side (the multi-root workspace
layout). Writes a ``skills.index.json`` snapshot into each agent repo's
``schemas/`` folder so the agent validators can turn ``required_skills`` from a
namespace check into a true existence check even when the skill repos are not
present (standalone CI).

Community agents can reference core + community (neqsim-*) skills.
Enterprise agents can additionally reference internal (enterprise-*) skills.

Usage (from the core neqsim repo):
    python devtools/generate_skill_index.py
    python devtools/generate_skill_index.py --check   # verify snapshots are current
"""

import argparse
import json
import re
import sys
from pathlib import Path


def core_root():
    current = Path(__file__).resolve().parent
    while current != current.parent:
        if (current / "pom.xml").exists():
            return current
        current = current.parent
    return Path(__file__).resolve().parent.parent


def _names(paths):
    names = set()
    for path in paths:
        try:
            text = path.read_text(encoding="utf-8")
        except OSError:
            continue
        if not text.startswith("---"):
            continue
        frontmatter = text.split("---", 2)[1]
        match = re.search(r"^name:\s*(.+)$", frontmatter, re.MULTILINE)
        if match:
            names.add(match.group(1).strip().strip('"').strip("'"))
    return names


def collect(root):
    workspace = root.parent
    core = _names((root / ".github" / "skills").glob("*/SKILL.md"))
    community = _names((workspace / "neqsim-community-skills" / "skills").glob("*/*/SKILL.md"))
    enterprise = _names((workspace / "neqsim-enterprise-skills" / "skills").glob("*/*/SKILL.md"))
    return core, community, enterprise


def build_index(sources, scope):
    return {
        "_comment": (
            "Snapshot of known skill ids for offline agent required_skills "
            "existence checks. Regenerate with devtools/generate_skill_index.py "
            "in the core neqsim repo when skills change."
        ),
        "scope": scope,
        "sources": sources,
        "skills": sorted(set().union(*sources.values())),
    }


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--check",
        action="store_true",
        help="Do not write; exit 1 if any vendored snapshot is missing or stale.",
    )
    args = parser.parse_args(argv)

    root = core_root()
    workspace = root.parent
    core, community, enterprise = collect(root)
    if not (core or community or enterprise):
        print("ERROR: no skills found - are the skill repos checked out alongside core?")
        return 1

    community_index = build_index(
        {"core": sorted(core), "community": sorted(community)}, scope="core+community"
    )
    enterprise_index = build_index(
        {
            "core": sorted(core),
            "community": sorted(community),
            "enterprise": sorted(enterprise),
        },
        scope="core+community+enterprise",
    )

    targets = [
        (workspace / "neqsim-community-agents" / "schemas" / "skills.index.json", community_index),
        (workspace / "neqsim-enterprise-agents" / "schemas" / "skills.index.json", enterprise_index),
    ]

    stale = []
    for path, index in targets:
        payload = json.dumps(index, indent=2) + "\n"
        if args.check:
            if not path.exists() or path.read_text(encoding="utf-8") != payload:
                stale.append(path)
            continue
        if not path.parent.parent.exists():
            print("SKIP: {} (agent repo not checked out)".format(path))
            continue
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(payload, encoding="utf-8")
        print("wrote {} ({} skills)".format(path, len(index["skills"])))

    if args.check:
        if stale:
            for path in stale:
                print("STALE: {}".format(path))
            print("\nSkill index snapshots are out of date - run generate_skill_index.py.")
            return 1
        print("Skill index snapshots are current.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
