#!/usr/bin/env python3
"""Install PaperLab agents and skills for VS Code Chat.

Examples:
    neqsim paperlab install --vscode
    neqsim paperlab install --vscode --include-internal
    neqsim paperlab install --vscode --agents-only
    neqsim paperlab install --vscode --vscode-scope workspace
"""

import argparse
import sys
from pathlib import Path

import install_agent
import install_skill


DEVTOOLS_DIR = Path(__file__).resolve().parent
PROJECT_ROOT = DEVTOOLS_DIR.parent
PAPERLAB_DIR = PROJECT_ROOT / "neqsim-paperlab"
PAPERLAB_AGENTS_DIR = PAPERLAB_DIR / "agents"
PAPERLAB_SKILLS_DIR = PAPERLAB_DIR / "skills"
PAPERLAB_GATEWAY_AGENT = PROJECT_ROOT / ".github" / "agents" / "paperlab.agent.md"


def _frontmatter_name(path, fallback):
    """Return the YAML frontmatter name from a markdown file.

    @param path the markdown file to inspect
    @param fallback the value to return if no name field exists
    @return the frontmatter name or fallback
    """
    try:
        lines = path.read_text(encoding="utf-8").splitlines()
    except OSError:
        return fallback
    if not lines or lines[0].strip() != "---":
        return fallback
    for line in lines[1:]:
        stripped = line.strip()
        if stripped == "---":
            break
        if stripped.startswith("name:"):
            value = stripped.split(":", 1)[1].strip()
            return value.strip("\"'") or fallback
    return fallback


def _extract_loaded_skills(path):
    """Extract comma-separated Loaded skills from an agent markdown file.

    @param path the agent markdown file to inspect
    @return a list of skill names declared by the agent
    """
    try:
        lines = Path(path).read_text(encoding="utf-8").splitlines()
    except OSError:
        return []
    for line in lines:
        stripped = line.strip()
        if stripped.lower().startswith("loaded skills:"):
            value = stripped.split(":", 1)[1]
            return [item.strip() for item in value.split(",") if item.strip()]
    return []


def _has_frontmatter(text):
    """Return True when the text starts with YAML frontmatter.

    @param text the markdown text to inspect
    @return True if YAML frontmatter exists
    """
    return text.startswith("---\n") or text.startswith("---\r\n")


def _infer_description(text, name):
    """Infer a VS Code skill description from PaperLab markdown.

    @param text the skill markdown body
    @param name the skill name
    @return a description string suitable for YAML frontmatter
    """
    lines = text.splitlines()
    for index, line in enumerate(lines):
        if line.strip().lower() == "## purpose":
            for candidate in lines[index + 1:]:
                value = candidate.strip()
                if value and not value.startswith("#"):
                    return value
        if line.strip().lower().startswith("description:"):
            value = line.split(":", 1)[1].strip().strip("\"'")
            if value:
                return value
    title = name.replace("_", " ").replace("-", " ")
    return "PaperLab skill for {title} workflows in scientific writing and book production.".format(
        title=title)


def _yaml_quote(value):
    """Quote a scalar value for the simple frontmatter used here.

    @param value the scalar value to quote
    @return a double-quoted YAML scalar
    """
    return '"{value}"'.format(value=value.replace('"', '\\"'))


def _ensure_skill_frontmatter(skill_dir, name):
    """Ensure an exported PaperLab skill has required YAML frontmatter.

    @param skill_dir the exported skill directory
    @param name the skill folder/name to write in frontmatter
    @return None
    """
    skill_file = Path(skill_dir) / "SKILL.md"
    text = skill_file.read_text(encoding="utf-8")
    if _has_frontmatter(text):
        return
    description = _infer_description(text, name)
    frontmatter = "---\nname: {name}\ndescription: {description}\n---\n\n".format(
        name=_yaml_quote(name), description=_yaml_quote(description))
    skill_file.write_text(frontmatter + text, encoding="utf-8")


def iter_paperlab_agents(include_internal=False):
    """Yield PaperLab agent install names and source files.

    @param include_internal when True, include internal specialist agents from neqsim-paperlab
    @return an iterator of (name, source_file) tuples
    """
    if PAPERLAB_GATEWAY_AGENT.exists():
        yield _frontmatter_name(PAPERLAB_GATEWAY_AGENT, "paperlab"), PAPERLAB_GATEWAY_AGENT
    if not include_internal or not PAPERLAB_AGENTS_DIR.exists():
        return
    for agent_file in sorted(PAPERLAB_AGENTS_DIR.glob("*.paperlab.md")):
        name = _frontmatter_name(agent_file, agent_file.name.replace(".paperlab.md", ""))
        if name != "paperlab":
            yield name, agent_file


def iter_paperlab_skills(include_internal=False):
    """Yield PaperLab skills required by the selected PaperLab export surface.

    @param include_internal when True, include all canonical PaperLab skills
    @return an iterator of (name, source_dir) tuples
    """
    if not PAPERLAB_SKILLS_DIR.exists():
        return
    required = set(_extract_loaded_skills(PAPERLAB_GATEWAY_AGENT))
    for skill_dir in sorted(PAPERLAB_SKILLS_DIR.iterdir()):
        skill_file = skill_dir / "SKILL.md"
        if skill_dir.is_dir() and skill_file.exists():
            name = _frontmatter_name(skill_file, skill_dir.name)
            if include_internal or name in required:
                yield name, skill_dir


def _export_agents(args):
    """Export PaperLab agents to a VS Code agents directory.

    @param args parsed CLI arguments
    @return the number of agents exported
    """
    target_dir = install_agent.resolve_vscode_agents_dir(
        args.vscode_scope, args.vscode_agents_dir)
    if target_dir is None:
        print("[!!] Could not locate a VS Code agents directory.")
        print("     Use --vscode-scope user, --vscode-scope workspace,")
        print("     NEQSIM_VSCODE_AGENTS_DIR, or --vscode-agents-dir <path>.")
        return 0

    count = 0
    for name, source_file in iter_paperlab_agents(args.include_internal):
        if args.dry_run:
            print("[DRY] agent {name} -> {target}".format(
                name=name, target=target_dir / (name + ".agent.md")))
        else:
            install_agent.export_agent_to_vscode(name, source_file, target_dir)
        count += 1
    print("[OK] PaperLab agents exported: {count} -> {target}".format(
        count=count, target=target_dir))
    return count


def _export_skills(args):
    """Export PaperLab skills to a VS Code skills directory.

    @param args parsed CLI arguments
    @return the number of skills exported
    """
    target_dir = install_skill.resolve_vscode_skills_dir(
        args.vscode_skills_dir, args.vscode_scope)
    if target_dir is None:
        print("[!!] Could not locate a VS Code skills directory.")
        print("     Use --vscode-scope user, --vscode-scope workspace,")
        print("     NEQSIM_VSCODE_SKILLS_DIR, or --vscode-skills-dir <path>.")
        return 0

    count = 0
    for name, source_dir in iter_paperlab_skills(args.include_internal):
        if args.dry_run:
            print("[DRY] skill {name} -> {target}".format(
                name=name, target=target_dir / name))
        else:
            dest = install_skill.export_skill_to_vscode(name, source_dir, target_dir)
            _ensure_skill_frontmatter(dest, name)
        count += 1
    print("[OK] PaperLab skills exported: {count} -> {target}".format(
        count=count, target=target_dir))
    return count


def cmd_install(args):
    """Install PaperLab assets for VS Code Chat.

    @param args parsed CLI arguments
    @return None
    """
    if not args.vscode:
        print("PaperLab install currently supports VS Code export only.")
        print("Run: neqsim paperlab install --vscode")
        sys.exit(1)

    if not PAPERLAB_DIR.exists():
        print("[!!] PaperLab directory not found: {path}".format(path=PAPERLAB_DIR))
        sys.exit(1)

    agent_count = 0
    skill_count = 0
    if not args.skills_only:
        agent_count = _export_agents(args)
    if not args.agents_only:
        skill_count = _export_skills(args)

    print("\nPaperLab VS Code install complete.")
    print("  Agents: {count}".format(count=agent_count))
    print("  Skills: {count}".format(count=skill_count))
    if not args.dry_run:
        print("Reload VS Code (Developer: Reload Window) to pick up new agents and skills.")
    if args.include_internal:
        print("Internal PaperLab agents are opt-in compatibility exports; @paperlab remains the default gateway.")


def cmd_list(args):
    """List PaperLab agents and skills available for export.

    @param args parsed CLI arguments
    @return None
    """
    agents = list(iter_paperlab_agents(args.include_internal))
    skills = list(iter_paperlab_skills(args.include_internal))
    print("PaperLab agents ({count}):".format(count=len(agents)))
    for name, _ in agents:
        print("  - {name}".format(name=name))
    print("\nPaperLab skills ({count}):".format(count=len(skills)))
    for name, _ in skills:
        print("  - {name}".format(name=name))


def main():
    """Run the PaperLab installer CLI."""
    parser = argparse.ArgumentParser(
        description="Install PaperLab agents and skills for VS Code Chat.")
    sub = parser.add_subparsers(dest="command")

    p_install = sub.add_parser(
        "install", help="Install PaperLab agents and skills")
    p_install.add_argument(
        "--vscode", action="store_true",
        help="Export PaperLab agents and skills to VS Code Chat locations")
    p_install.add_argument(
        "--agents-only", action="store_true",
        help="Export only PaperLab agents")
    p_install.add_argument(
        "--skills-only", action="store_true",
        help="Export only PaperLab skills")
    p_install.add_argument(
        "--include-internal", action="store_true",
        help="Also export internal PaperLab specialist agents and skills for legacy/direct use")
    p_install.add_argument(
        "--vscode-scope", choices=["user", "workspace"], default="user",
        help="VS Code export scope: user prompts folders (default) or explicit workspace .github folders")
    p_install.add_argument(
        "--vscode-agents-dir", default=None,
        help="Explicit VS Code agents/prompts directory")
    p_install.add_argument(
        "--vscode-skills-dir", default=None,
        help="Explicit VS Code skills directory")
    p_install.add_argument(
        "--dry-run", action="store_true",
        help="Print export destinations without copying files")

    p_list = sub.add_parser("list", help="List PaperLab agents and skills")
    p_list.add_argument(
        "--include-internal", action="store_true",
        help="Also list internal PaperLab specialist agents and skills")

    args = parser.parse_args()
    if not args.command:
        parser.print_help()
        sys.exit(0)
    if args.command == "install":
        if args.agents_only and args.skills_only:
            parser.error("--agents-only and --skills-only cannot be combined")
        cmd_install(args)
    elif args.command == "list":
        cmd_list(args)


if __name__ == "__main__":
    main()