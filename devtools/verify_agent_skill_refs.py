#!/usr/bin/env python3
"""
Verify cross-references between NeqSim Copilot agents and skills.

Checks:
1. Every skill name referenced in agent files has a matching folder in .github/skills/
2. Every skill folder in .github/skills/ contains a SKILL.md file
3. Every skill name referenced in copilot-instructions.md has a matching folder
4. Reports orphaned skill folders (exist but not referenced by any agent)
5. Every skill has a USE WHEN clause in its frontmatter
6. Every agent file (except README.md/router) references at least one skill
7. Generates a skill-keyword index for agent discovery (--generate-index)

Usage:
    python devtools/verify_agent_skill_refs.py
    python devtools/verify_agent_skill_refs.py --generate-index
    python devtools/verify_agent_skill_refs.py --strict

Exit codes:
    0 - All checks pass
    1 - One or more errors found
"""

import json
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


def extract_skill_metadata(skill_md_path):
    """Extract name, description, USE WHEN clause, and keywords from SKILL.md."""
    try:
        text = skill_md_path.read_text(encoding="utf-8")
    except (OSError, UnicodeDecodeError):
        return {}

    metadata = {}
    # Extract YAML frontmatter
    fm_match = re.match(r"^---\s*\n(.*?)\n---", text, re.DOTALL)
    if fm_match:
        fm = fm_match.group(1)
        for key in ("name", "description", "last_verified"):
            m = re.search(r'^{}\s*:\s*"?(.+?)"?\s*$'.format(key), fm, re.MULTILINE)
            if m:
                metadata[key] = m.group(1).strip().strip('"')

    # Extract USE WHEN clause
    desc = metadata.get("description", "")
    use_when_match = re.search(r"USE WHEN:\s*(.+?)(?:\.|$)", desc)
    if use_when_match:
        metadata["use_when"] = use_when_match.group(1).strip()

    # Extract keywords from USE WHEN for indexing
    if "use_when" in metadata:
        # Split on commas and common conjunctions
        raw = metadata["use_when"]
        keywords = set()
        for part in re.split(r",\s*|\s+or\s+|\s+and\s+", raw):
            part = part.strip().lower()
            # Remove common filler words
            for filler in ("any ", "for ", "when ", "the "):
                if part.startswith(filler):
                    part = part[len(filler):]
            if len(part) > 2:
                keywords.add(part)
        metadata["keywords"] = sorted(keywords)

    return metadata


def check_agents_reference_skills(agents_dir, root):
    """Check that each agent file (except meta files) references at least one skill."""
    issues = []
    meta_files = {"README.md", "router.agent.md"}
    if not agents_dir.is_dir():
        return issues

    for agent_file in sorted(agents_dir.glob("*.md")):
        if agent_file.name in meta_files:
            continue
        refs = extract_skill_refs_from_file(agent_file)
        if not refs:
            issues.append(
                "NO SKILLS: Agent '{}' does not reference any skills. "
                "Add skill references or a '## Skills to Load' section.".format(
                    agent_file.name
                )
            )
    return issues


def generate_skill_index(skills_dir):
    """Generate a keyword-to-skill index for agent discovery."""
    index = {}
    for entry in sorted(skills_dir.iterdir()):
        if not entry.is_dir() or entry.name == "__pycache__":
            continue
        skill_md = entry / "SKILL.md"
        if not skill_md.exists():
            continue
        meta = extract_skill_metadata(skill_md)
        skill_name = meta.get("name", entry.name)
        keywords = meta.get("keywords", [])
        for kw in keywords:
            index.setdefault(kw, []).append(skill_name)
        # Also index the skill name itself
        index.setdefault(skill_name, []).append(skill_name)
    return index


def main():
    root = find_repo_root()
    agents_dir = root / ".github" / "agents"
    skills_dir = root / ".github" / "skills"
    copilot_instructions = root / ".github" / "copilot-instructions.md"
    agents_md = root / "AGENTS.md"

    strict = "--strict" in sys.argv
    gen_index = "--generate-index" in sys.argv

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

    # 5. Check: every skill has a USE WHEN clause
    for skill_name in sorted(skill_folders):
        skill_md = skills_dir / skill_name / "SKILL.md"
        meta = extract_skill_metadata(skill_md)
        if "use_when" not in meta:
            warnings.append(
                "NO TRIGGER: Skill '{}' has no 'USE WHEN:' clause in its "
                "description. Agents can't auto-discover it.".format(skill_name)
            )

    # 6. Check: every agent references at least one skill
    agent_issues = check_agents_reference_skills(agents_dir, root)
    if strict:
        errors.extend(agent_issues)
    else:
        warnings.extend(agent_issues)

    # 7. Generate skill keyword index if requested
    if gen_index:
        index = generate_skill_index(skills_dir)
        index_path = root / ".github" / "skills" / "skill-index.json"
        with open(str(index_path), "w", encoding="utf-8") as f:
            json.dump(index, f, indent=2, sort_keys=True)
        print("Generated skill index: {}".format(index_path.relative_to(root)))
        print("  {} keywords mapped to {} skills".format(
            len(index), len(skill_folders)))
        print()

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
