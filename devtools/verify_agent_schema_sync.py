#!/usr/bin/env python3
"""Guard the shared agent- and skill-manifest schemas against cross-repo drift.

The canonical JSON Schemas live in this core repo at
``docs/integration/schemas/agent-manifest.schema.json`` and
``docs/integration/schemas/skill-manifest.schema.json``. The community and
enterprise agent/skill repositories vendor a copy under their own ``schemas/``
folder so they can validate standalone. This check compares each vendored copy
against the canonical one whenever those sibling repos are checked out next to
this repo (the normal multi-root workspace / CI layout).

It is intentionally non-fatal when a sibling repo is absent: those repos run
their own copy of the check in their own CI. Run it from the core repo to catch
drift early during local development.

Usage:
    python devtools/verify_agent_schema_sync.py
    python devtools/verify_agent_schema_sync.py --strict   # fail if a sibling is missing

Exit codes:
    0 - vendored copies match (or siblings absent, non-strict)
    1 - drift detected (or sibling absent under --strict)
"""

import argparse
import json
import sys
from pathlib import Path


def repo_root():
    current = Path(__file__).resolve().parent
    while current != current.parent:
        if (current / "pom.xml").exists():
            return current
        current = current.parent
    return Path(__file__).resolve().parent.parent


CANONICAL_DIR = Path("docs/integration/schemas")
VENDORED_REL = Path("schemas")

# Each canonical schema and the sibling repos that vendor a copy of it.
SCHEMA_TARGETS = {
    "agent-manifest.schema.json": ["neqsim-community-agents", "neqsim-enterprise-agents"],
    "skill-manifest.schema.json": ["neqsim-community-skills", "neqsim-enterprise-skills"],
}


def load_json(path):
    return json.loads(path.read_text(encoding="utf-8"))


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--strict",
        action="store_true",
        help="Treat a missing sibling repo as an error instead of a skip.",
    )
    args = parser.parse_args(argv)

    root = repo_root()
    errors = []
    checked = 0

    for schema_name, siblings in SCHEMA_TARGETS.items():
        canonical_path = root / CANONICAL_DIR / schema_name
        if not canonical_path.exists():
            print("ERROR: canonical schema not found at {}".format(canonical_path))
            return 1
        canonical = load_json(canonical_path)
        for sibling in siblings:
            vendored_path = root.parent / sibling / VENDORED_REL / schema_name
            if not vendored_path.exists():
                message = "{}: vendored {} not found at {}".format(
                    sibling, schema_name, vendored_path
                )
                if args.strict:
                    errors.append(message)
                else:
                    print("SKIP: {} ({} sibling not checked out)".format(sibling, schema_name))
                continue
            checked += 1
            if load_json(vendored_path) != canonical:
                errors.append(
                    "{}: vendored {} differs from canonical {}".format(
                        sibling, schema_name, canonical_path
                    )
                )
            else:
                print("OK: {} vendored {} matches canonical".format(sibling, schema_name))

    for error in errors:
        print("ERROR: {}".format(error))
    if errors:
        print("\nSchema sync FAILED ({} issues, {} checked).".format(len(errors), checked))
        return 1
    print("\nSchema sync passed ({} vendored copies checked).".format(checked))
    return 0


if __name__ == "__main__":
    sys.exit(main())
