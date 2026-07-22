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

    # Extract USE WHEN clause (case-insensitive: skills use "USE WHEN:" and "Use when:")
    desc = metadata.get("description", "")
    use_when_match = re.search(r"use when:\s*(.+?)(?:\.|$)", desc, re.IGNORECASE)
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
    """Check that each agent file (except meta files) references at least one skill.

    Recognises all skill-declaration conventions (backticked ``neqsim-*`` refs,
    an inline ``Loaded skills:`` line, and ``## Skills to Load`` / ``## Loaded
    skills`` bullet headings) so agents that only use the canonical loaded-skills
    line are not falsely flagged.
    """
    issues = []
    meta_files = {"README.md", "router.agent.md"}
    if not agents_dir.is_dir():
        return issues

    agent_search, _ = _load_discovery_modules()
    for agent_file in sorted(agents_dir.glob("*.md")):
        if agent_file.name in meta_files:
            continue
        refs = extract_skill_refs_from_file(agent_file)
        if not refs and agent_search is not None:
            try:
                text = agent_file.read_text(encoding="utf-8")
            except (OSError, UnicodeDecodeError):
                text = ""
            if agent_search._extract_loaded_skills_body(text):
                continue
        if not refs:
            issues.append(
                "NO SKILLS: Agent '{}' does not reference any skills. "
                "Add skill references or a '## Skills to Load' section.".format(
                    agent_file.name
                )
            )
    return issues


def _load_discovery_modules():
    """Import agent_search + skill_search from the same devtools dir (or None)."""
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    try:
        import agent_search  # noqa: E402
        import skill_search  # noqa: E402
        return agent_search, skill_search
    except ImportError:
        return None, None


def check_combined_skill_refs(root, skill_folders):
    """Validate agent required_skills against the COMBINED cross-repo skill index.

    Uses agent_search (all agent repos) and skill_search (now spans the sibling
    *-skills repos) to classify each agent's declared skills:

    - present in a sibling *-skills repo but not in neqsim/.github/skills
      -> cross-repo (expected; reported as a count only), and
    - present in NO repo -> genuinely broken (reported as a warning, or an error
      under --strict).

    In the neqsim-only CI checkout there are no sibling repos, so agent_search
    finds only neqsim agents and this check adds nothing new. In the multi-repo
    workspace it catches real broken references without false-flagging the many
    legitimate cross-repo skill loads.

    Returns (errors, warnings, cross_repo_count).
    """
    agent_search, skill_search = _load_discovery_modules()
    if agent_search is None or skill_search is None:
        return [], [], 0
    skills_dir = root / ".github" / "skills"
    combined = set(name.lower() for name, _, _ in skill_search._load_skills(skills_dir))
    combined |= set(folder.lower() for folder in skill_folders)
    # Also recognise skills bundled in the neqsim-paperlab subsystem, which lives
    # in its own skills/ tree outside .github/skills and the *-skills repos.
    paperlab_skills = root / "neqsim-paperlab" / "skills"
    if paperlab_skills.is_dir():
        for entry in paperlab_skills.iterdir():
            if entry.is_dir() and (entry / "SKILL.md").is_file():
                combined.add(entry.name.lower())
    local = set(folder.lower() for folder in skill_folders)

    errors = []
    warnings = []
    cross_repo = 0
    for rec in agent_search._load_agents(root):
        handle = rec[5]
        repo = rec[4]
        for skill in rec[3]:
            key = str(skill).lower()
            if key in combined:
                if key not in local:
                    cross_repo += 1
            else:
                warnings.append(
                    "BROKEN REF (combined index): agent '{}' [{}] requires skill "
                    "'{}' not found in any repo".format(handle, repo, skill)
                )
    return errors, warnings, cross_repo


def combined_referenced_skills(root, md_refs):
    """Return the lowercase set of skills referenced by ANY agent across all repos.

    Combines the markdown refs gathered from neqsim agent/instruction files with
    the ``required_skills`` declared by every agent in every agent repo (via
    agent_search). This makes the orphan check aware of the multi-repo reality: a
    neqsim-mirrored skill consumed only by a sibling-repo agent is NOT an orphan.
    Degrades to just the markdown refs if agent_search cannot be imported.
    """
    referenced = set(str(r).lower() for r in md_refs)
    agent_search, _ = _load_discovery_modules()
    if agent_search is not None:
        for rec in agent_search._load_agents(root):
            for skill in rec[3]:
                referenced.add(str(skill).lower())
    return referenced


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

    # Skills mirrored from the neqsim-paperlab subsystem follow their own
    # conventions (narrative descriptions, invoked by the paperlab router or
    # directly in research notebooks rather than via the agent loaded-skills
    # graph), so they are exempt from the discovery-oriented orphan and
    # USE-WHEN checks.
    paperlab_skills_dir = root / "neqsim-paperlab" / "skills"
    paperlab_managed = set()
    if paperlab_skills_dir.is_dir():
        paperlab_managed = {
            entry.name for entry in paperlab_skills_dir.iterdir()
            if entry.is_dir() and (entry / "SKILL.md").is_file()
        }

    # 4. Check: orphaned skill folders (exist but never referenced by ANY agent).
    # Use the combined cross-repo reference set so neqsim-mirrored skills consumed
    # only by a community/enterprise agent are not falsely reported as orphans.
    combined_refs = combined_referenced_skills(root, all_refs)
    orphaned = {
        name for name in skill_folders
        if name.lower() not in combined_refs and name not in paperlab_managed
    }
    for name in sorted(orphaned):
        warnings.append(
            "ORPHANED: Skill folder '{}' exists but is not referenced "
            "by any agent (in any repo) or instruction file".format(name)
        )

    # 5. Check: every skill has a USE WHEN clause (paperlab-managed skills exempt).
    for skill_name in sorted(skill_folders):
        if skill_name in paperlab_managed:
            continue
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

    # 6b. Combined cross-repo index check: distinguish genuinely-broken agent
    # required_skills from legitimate cross-repo loads (sibling *-skills repos).
    combined_errors, combined_warnings, cross_repo_count = check_combined_skill_refs(
        root, skill_folders)
    if strict:
        errors.extend(combined_warnings)
        errors.extend(combined_errors)
    else:
        warnings.extend(combined_warnings)
        warnings.extend(combined_errors)

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
    if cross_repo_count:
        print("Cross-repo skill loads (sibling *-skills repos): {}".format(
            cross_repo_count))
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
