#!/usr/bin/env python3
"""
Verify cross-references between NeqSim Copilot agents and skills.

Checks:
1. Every skill name referenced in agent files has a matching folder in .github/skills/
2. Every skill folder in .github/skills/ contains a SKILL.md file
3. Every skill name referenced in copilot-instructions.md has a matching folder
4. Reports orphaned skill folders (exist but not referenced by any agent)

Usage:
    python devtools/verify_agent_skill_refs.py

Exit codes:
    0 - All checks pass
    1 - One or more errors found
"""

import os
import re
import sys
from pathlib import Path


def find_repo_root():
    """Find the repository root by looking for pom.xml."""
    current = Path(__file__).resolve().parent
    while current != current.parent:
        if (current / "pom.xml").exists():
            return current
        current = current.parent
    # Fallback: assume devtools/ is one level below root
    return Path(__file__).resolve().parent.parent


def get_skill_folders(skills_dir):
    """Return set of skill folder names that contain a SKILL.md."""
    folders = set()
    missing_skill_md = []
    if not skills_dir.is_dir():
        return folders, missing_skill_md
    for entry in skills_dir.iterdir():
        if entry.is_dir() and entry.name != "__pycache__":
            if (entry / "SKILL.md").exists():
                folders.add(entry.name)
            else:
                missing_skill_md.append(entry.name)
    return folders, missing_skill_md


def extract_skill_refs_from_file(filepath):
    """Extract neqsim-* skill references from a markdown file."""
    refs = set()
    try:
        text = filepath.read_text(encoding="utf-8")
    except (OSError, UnicodeDecodeError):
        return refs
    # Match skill names in backticks: `neqsim-xxx`
    for match in re.finditer(r"`(neqsim-[\w-]+)`", text):
        refs.add(match.group(1))
    # Match skill folder paths: skills/neqsim-xxx/
    for match in re.finditer(r"skills/(neqsim-[\w-]+)", text):
        refs.add(match.group(1))
    # Match skill names in quotes: "neqsim-xxx"
    for match in re.finditer(r'"(neqsim-[\w-]+)"', text):
        refs.add(match.group(1))
    # Match skill names after "Load" or "load": load neqsim-xxx
    for match in re.finditer(r"[Ll]oad\s+(neqsim-[\w-]+)", text):
        refs.add(match.group(1))
    return refs


def main():
    root = find_repo_root()
    agents_dir = root / ".github" / "agents"
    skills_dir = root / ".github" / "skills"
    copilot_instructions = root / ".github" / "copilot-instructions.md"
    agents_md = root / "AGENTS.md"

    errors = []
    warnings = []

    # 1. Inventory skill folders
    skill_folders, missing_skill_md = get_skill_folders(skills_dir)
    for name in missing_skill_md:
        errors.append(
            "MISSING SKILL.md: Folder '{}' exists but has no SKILL.md".format(name)
        )

    # 2. Collect all skill references from agent files
    all_refs = set()
    ref_sources = {}  # skill_name -> list of files referencing it

    md_files = []
    if agents_dir.is_dir():
        md_files.extend(agents_dir.glob("*.md"))
    if copilot_instructions.exists():
        md_files.append(copilot_instructions)
    if agents_md.exists():
        md_files.append(agents_md)

    for md_file in md_files:
        refs = extract_skill_refs_from_file(md_file)
        all_refs.update(refs)
        for ref in refs:
            ref_sources.setdefault(ref, []).append(str(md_file.relative_to(root)))

    # 3. Check: every referenced skill has a matching folder
    for ref in sorted(all_refs):
        if ref not in skill_folders:
            sources = ref_sources.get(ref, ["unknown"])
            errors.append(
                "BROKEN REF: Skill '{}' referenced in {} but no folder found "
                "in .github/skills/".format(ref, ", ".join(sources))
            )

    # 4. Check: orphaned skill folders (exist but never referenced)
    orphaned = skill_folders - all_refs
    for name in sorted(orphaned):
        warnings.append(
            "ORPHANED: Skill folder '{}' exists but is not referenced "
            "by any agent or instruction file".format(name)
        )

    # 5. Report
    print("=" * 60)
    print("Agent/Skill Cross-Reference Verification")
    print("=" * 60)
    print()
    print("Skill folders found: {}".format(len(skill_folders)))
    print("Skill references found: {}".format(len(all_refs)))
    print("Agent files scanned: {}".format(len(md_files)))
    print()

    if errors:
        print("ERRORS ({})".format(len(errors)))
        print("-" * 40)
        for err in errors:
            print("  [ERROR] {}".format(err))
        print()

    if warnings:
        print("WARNINGS ({})".format(len(warnings)))
        print("-" * 40)
        for warn in warnings:
            print("  [WARN]  {}".format(warn))
        print()

    if not errors and not warnings:
        print("All checks passed.")

    if errors:
        print("Result: FAIL ({} errors, {} warnings)".format(
            len(errors), len(warnings)
        ))
        return 1
    else:
        print("Result: PASS ({} warnings)".format(len(warnings)))
        return 0


if __name__ == "__main__":
    sys.exit(main())
