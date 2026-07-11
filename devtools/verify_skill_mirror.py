#!/usr/bin/env python3
"""Detect drift between the core ``.github/skills`` mirror and the source repos.

The core ``neqsim`` repo keeps a flat ``.github/skills/`` folder that acts as the
workspace **discovery layer**. It contains core ``neqsim-*`` skills (which live
only here) plus mirrored copies of the community and enterprise skills whose
source of truth is the dedicated repositories:

* ``neqsim-community-skills/skills/<domain>/<skill>/SKILL.md``
* ``neqsim-enterprise-skills/skills/<domain>/<skill>/SKILL.md``

Because the mirror is maintained by hand it can silently drift from the source.
This check pairs each source skill with its mirror **by the front-matter
``name``** (not by path, since the source is domain-foldered and the mirror is
flat) and reports:

* skills present in a source repo but missing from the mirror;
* skills whose mirrored ``SKILL.md`` content differs from the source.

Core ``neqsim-*`` skills that exist only in the mirror are ignored (they have no
upstream source). The check is non-fatal when a sibling source repo is not
checked out - those repos own their content and this is only a convenience
guard for the multi-root workspace.

Usage:
    python devtools/verify_skill_mirror.py
    python devtools/verify_skill_mirror.py --strict   # treat drift as an error

Exit codes:
    0 - mirror in sync (or drift only, without --strict)
    1 - drift detected and --strict given (or a missing sibling under --strict)
    2 - the core mirror folder was not found
"""

import argparse
import re
import sys
from pathlib import Path

FRONTMATTER_NAME_RE = re.compile(r"^name:\s*(.+?)\s*$", re.MULTILINE | re.IGNORECASE)


def repo_root():
    current = Path(__file__).resolve().parent
    while current != current.parent:
        if (current / "pom.xml").exists():
            return current
        current = current.parent
    return Path(__file__).resolve().parent.parent


def frontmatter_name(text):
    """Return the front-matter ``name`` field, or None."""
    if not text.startswith("---"):
        return None
    parts = text.split("---", 2)
    if len(parts) < 3:
        return None
    match = FRONTMATTER_NAME_RE.search(parts[1])
    return match.group(1).strip().strip('"').strip("'") if match else None


def normalize(text):
    """Normalize for content comparison: unify newlines, strip trailing spaces."""
    lines = [line.rstrip() for line in text.replace("\r\n", "\n").split("\n")]
    while lines and not lines[-1]:
        lines.pop()
    return "\n".join(lines)


def collect(skill_root, pattern):
    """Return {name: (path, normalized_text)} for SKILL.md files under skill_root."""
    found = {}
    if not skill_root.is_dir():
        return found
    for skill_md in skill_root.glob(pattern):
        try:
            text = skill_md.read_text(encoding="utf-8")
        except OSError:
            continue
        name = frontmatter_name(text) or skill_md.parent.name
        found[name] = (skill_md, normalize(text))
    return found


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--strict",
        action="store_true",
        help="Treat drift (or a missing sibling source repo) as an error.",
    )
    args = parser.parse_args(argv)

    root = repo_root()
    mirror_dir = root / ".github" / "skills"
    if not mirror_dir.is_dir():
        print("ERROR: core skill mirror not found at {}".format(mirror_dir))
        return 2

    mirror = collect(mirror_dir, "*/SKILL.md")

    sources = {
        "community": root.parent / "neqsim-community-skills" / "skills",
        "enterprise": root.parent / "neqsim-enterprise-skills" / "skills",
    }

    missing_mirror = []
    drifted = []
    missing_sibling = []
    checked = 0

    for label, source_root in sources.items():
        if not source_root.is_dir():
            missing_sibling.append(label)
            continue
        source = collect(source_root, "*/*/SKILL.md")
        for name, (src_path, src_text) in sorted(source.items()):
            checked += 1
            if name not in mirror:
                missing_mirror.append("[{}] {} (source: {})".format(label, name, src_path))
                continue
            _, mir_text = mirror[name]
            if mir_text != src_text:
                drifted.append("[{}] {}".format(label, name))

    for label in missing_sibling:
        print("NOTE: {} source repo not checked out - skipped.".format(label))
    for item in missing_mirror:
        print("WARNING: not mirrored into .github/skills: {}".format(item))
    for item in drifted:
        print("WARNING: mirror content differs from source: {}".format(item))

    drift_count = len(missing_mirror) + len(drifted)
    print(
        "\nChecked {} source skills against the mirror: "
        "{} not mirrored, {} drifted.".format(checked, len(missing_mirror), len(drifted))
    )

    failed = args.strict and (drift_count > 0 or missing_sibling)
    if failed:
        print("Skill mirror verification FAILED (--strict).")
        return 1
    print("Skill mirror verification passed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
