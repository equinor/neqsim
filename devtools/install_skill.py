#!/usr/bin/env python3
"""Install community skills from the NeqSim skill catalog.

Usage:
    python devtools/install_skill.py list              # list catalog
    python devtools/install_skill.py search <query>    # search by name/tag
    python devtools/install_skill.py install <name>    # install a skill
    python devtools/install_skill.py installed          # show installed skills
    python devtools/install_skill.py remove <name>     # remove an installed skill
    python devtools/install_skill.py info <name>       # show skill details
    python devtools/install_skill.py publish <repo>    # publish your skill to catalog

Skills are installed to ~/.neqsim/skills/<name>/SKILL.md so they don't
pollute the core repo. AI tools can be configured to read from that path.
"""

import argparse
import json
import os
import sys
import textwrap
from pathlib import Path

try:
    import yaml
except ImportError:
    yaml = None

# ── Paths ──────────────────────────────────────────────────────────────
REPO_ROOT = Path(__file__).resolve().parent.parent
CATALOG_FILE = REPO_ROOT / "community-skills.yaml"
INSTALL_DIR = Path.home() / ".neqsim" / "skills"
MANIFEST_FILE = INSTALL_DIR / "installed.json"


def load_catalog():
    """Load the community-skills.yaml catalog."""
    if not CATALOG_FILE.exists():
        print(f"  [!!] Catalog not found: {CATALOG_FILE}")
        sys.exit(1)

    text = CATALOG_FILE.read_text(encoding="utf-8")

    if yaml is not None:
        data = yaml.safe_load(text)
    else:
        # Minimal YAML-like parser for the simple catalog format
        data = _parse_catalog_fallback(text)

    return data.get("skills", [])


def _parse_catalog_fallback(text):
    """Fallback parser when PyYAML is not installed.

    Only handles the flat list-of-dicts format used in community-skills.yaml.
    """
    skills = []
    current = None
    for raw_line in text.splitlines():
        line = raw_line.strip()
        if line.startswith("#") or not line:
            continue
        if line.startswith("- name:"):
            if current:
                skills.append(current)
            current = {"name": line.split(":", 1)[1].strip().strip('"')}
        elif current and ":" in line:
            key, val = line.split(":", 1)
            key = key.strip()
            val = val.strip().strip('"')
            if val.startswith("[") and val.endswith("]"):
                val = [v.strip().strip('"') for v in val[1:-1].split(",")]
            current[key] = val
    if current:
        skills.append(current)
    return {"skills": skills}


def load_manifest():
    """Load the installed-skills manifest."""
    if MANIFEST_FILE.exists():
        return json.loads(MANIFEST_FILE.read_text(encoding="utf-8"))
    return {}


def save_manifest(manifest):
    """Save the installed-skills manifest."""
    MANIFEST_FILE.parent.mkdir(parents=True, exist_ok=True)
    MANIFEST_FILE.write_text(json.dumps(manifest, indent=2), encoding="utf-8")


# ── Commands ───────────────────────────────────────────────────────────

def cmd_list(skills, args):
    """List all skills in the catalog."""
    manifest = load_manifest()
    print(f"\n  Community Skill Catalog ({len(skills)} skills)\n")
    print(f"  {'Name':<35} {'Tags':<25} {'Installed'}")
    print(f"  {'-'*35} {'-'*25} {'-'*9}")
    for s in skills:
        name = s.get("name", "?")
        tags = ", ".join(s.get("tags", [])) if isinstance(s.get("tags"), list) else str(s.get("tags", ""))
        installed = "yes" if name in manifest else ""
        print(f"  {name:<35} {tags:<25} {installed}")
    print()


def cmd_search(skills, args):
    """Search skills by name, tag, or description."""
    query = args.query.lower()
    matches = []
    for s in skills:
        searchable = " ".join([
            s.get("name", ""),
            s.get("description", ""),
            " ".join(s.get("tags", [])) if isinstance(s.get("tags"), list) else str(s.get("tags", "")),
        ]).lower()
        if query in searchable:
            matches.append(s)

    if not matches:
        print(f"\n  No skills matching '{args.query}'.\n")
        return

    print(f"\n  {len(matches)} skill(s) matching '{args.query}':\n")
    for s in matches:
        print(f"  {s.get('name', '?')}")
        print(f"    {s.get('description', '')}")
        tags = s.get("tags", [])
        if tags:
            print(f"    Tags: {', '.join(tags) if isinstance(tags, list) else tags}")
        print()


def cmd_info(skills, args):
    """Show details for a specific skill."""
    name = args.name
    skill = next((s for s in skills if s.get("name") == name), None)
    if not skill:
        print(f"\n  Skill '{name}' not found in catalog.\n")
        sys.exit(1)

    manifest = load_manifest()
    print(f"\n  Skill: {skill.get('name')}")
    print(f"  Description: {skill.get('description', '-')}")
    print(f"  Author: {skill.get('author', '-')}")
    print(f"  Repo: https://github.com/{skill.get('repo', '-')}")
    print(f"  Path: {skill.get('path', 'SKILL.md')}")
    tags = skill.get("tags", [])
    print(f"  Tags: {', '.join(tags) if isinstance(tags, list) else tags}")
    print(f"  Installed: {'yes' if name in manifest else 'no'}")
    if name in manifest:
        print(f"  Local path: {manifest[name].get('path', '-')}")
    print()


def cmd_install(skills, args):
    """Install a skill from the catalog."""
    name = args.name
    skill = next((s for s in skills if s.get("name") == name), None)
    if not skill:
        print(f"\n  Skill '{name}' not found in catalog.")
        print(f"  Run: python devtools/install_skill.py list\n")
        sys.exit(1)

    manifest = load_manifest()
    if name in manifest and not args.force:
        print(f"\n  Skill '{name}' already installed at {manifest[name]['path']}")
        print(f"  Use --force to reinstall.\n")
        return

    repo = skill.get("repo", "")
    path = skill.get("path", "SKILL.md")

    if not repo:
        print(f"\n  [!!] No repo specified for '{name}'.\n")
        sys.exit(1)

    # Download the SKILL.md from GitHub raw
    raw_url = f"https://raw.githubusercontent.com/{repo}/main/{path}"
    print(f"\n  Downloading: {raw_url}")

    try:
        import urllib.request
        import urllib.error

        dest_dir = INSTALL_DIR / name
        dest_dir.mkdir(parents=True, exist_ok=True)
        dest_file = dest_dir / "SKILL.md"

        try:
            urllib.request.urlretrieve(raw_url, str(dest_file))
        except urllib.error.HTTPError:
            # Try 'master' branch if 'main' fails
            raw_url_master = f"https://raw.githubusercontent.com/{repo}/master/{path}"
            print(f"  Retrying: {raw_url_master}")
            urllib.request.urlretrieve(raw_url_master, str(dest_file))

        # Verify we got a real file (not a 404 HTML page)
        content = dest_file.read_text(encoding="utf-8", errors="replace")
        if len(content) < 20 or "<html" in content.lower()[:200]:
            dest_file.unlink()
            dest_dir.rmdir()
            print(f"  [!!] Downloaded content doesn't look like a SKILL.md file.")
            print(f"  Check repo/path: {repo} / {path}\n")
            sys.exit(1)

        manifest[name] = {
            "path": str(dest_file),
            "repo": repo,
            "remote_path": path,
            "author": skill.get("author", ""),
        }
        save_manifest(manifest)

        print(f"  [OK] Installed to: {dest_file}")
        print(f"\n  To use in your AI tool, point it at: {dest_dir}\n")

    except Exception as e:
        print(f"  [!!] Download failed: {e}")
        print(f"  You can manually download from: https://github.com/{repo}\n")
        sys.exit(1)


def cmd_installed(skills, args):
    """Show installed skills."""
    manifest = load_manifest()
    if not manifest:
        print("\n  No community skills installed.")
        print(f"  Install dir: {INSTALL_DIR}")
        print(f"  Run: python devtools/install_skill.py list\n")
        return

    print(f"\n  Installed Community Skills ({len(manifest)}):\n")
    print(f"  {'Name':<35} {'Author':<20} {'Path'}")
    print(f"  {'-'*35} {'-'*20} {'-'*40}")
    for name, info in sorted(manifest.items()):
        print(f"  {name:<35} {info.get('author', '-'):<20} {info.get('path', '-')}")
    print()


def cmd_remove(skills, args):
    """Remove an installed skill."""
    name = args.name
    manifest = load_manifest()
    if name not in manifest:
        print(f"\n  Skill '{name}' is not installed.\n")
        sys.exit(1)

    skill_dir = INSTALL_DIR / name
    if skill_dir.exists():
        import shutil
        shutil.rmtree(skill_dir)

    del manifest[name]
    save_manifest(manifest)
    print(f"\n  [OK] Removed skill '{name}'.\n")


def cmd_publish(skills, args):
    """Publish a skill to the community catalog via a draft PR."""
    import subprocess
    import urllib.request
    import urllib.error
    import re

    repo = args.repo  # e.g. "username/neqsim-my-skill"
    skill_path = args.path  # e.g. "SKILL.md"

    if "/" not in repo:
        print(f"\n  [!!] Repo must be 'owner/repo' format, got: {repo}")
        sys.exit(1)

    # Derive skill name from repo name
    repo_name = repo.split("/", 1)[1]
    skill_name = repo_name if repo_name.startswith("neqsim-") else f"neqsim-{repo_name}"

    # Check not already in catalog
    existing = [s for s in skills if s.get("name") == skill_name]
    if existing:
        print(f"\n  [!!] Skill '{skill_name}' already in catalog.")
        print(f"  To update it, edit community-skills.yaml directly.\n")
        sys.exit(1)

    # Validate the SKILL.md exists on GitHub
    print(f"\n  Validating {repo} / {skill_path} ...")
    content = None
    for branch in ["main", "master"]:
        raw_url = f"https://raw.githubusercontent.com/{repo}/{branch}/{skill_path}"
        try:
            resp = urllib.request.urlopen(raw_url)
            content = resp.read().decode("utf-8", errors="replace")
            break
        except urllib.error.HTTPError:
            continue

    if not content or len(content) < 50:
        print(f"  [!!] Could not fetch {skill_path} from {repo}.")
        print(f"  Make sure the file exists on the main or master branch.\n")
        sys.exit(1)

    print(f"  [OK] Found SKILL.md ({len(content)} bytes)")

    # Extract description from first paragraph or YAML frontmatter
    description = ""
    lines = content.splitlines()
    for line in lines:
        stripped = line.strip()
        if stripped.startswith("description:"):
            description = stripped.split(":", 1)[1].strip().strip('"').strip("'")
            break
    if not description:
        # Use first non-heading, non-empty line
        for line in lines:
            stripped = line.strip()
            if stripped and not stripped.startswith(("#", "---", ">")):
                description = stripped[:120]
                break
    if not description:
        description = f"Community skill from {repo}"

    # Extract tags from content keywords
    tags = []
    tag_patterns = [
        "thermodynamic", "process", "pvt", "flow assurance", "hydrate",
        "pipeline", "compressor", "separator", "distillation", "heat exchanger",
        "ccs", "hydrogen", "subsea", "well", "economics", "safety",
        "electrolyte", "reaction", "power", "emissions", "corrosion",
    ]
    content_lower = content.lower()
    for tag in tag_patterns:
        if tag in content_lower:
            tags.append(tag.replace(" ", "-"))
    tags = tags[:5]  # limit to 5 auto-detected tags
    if not tags:
        tags = ["community"]

    author = repo.split("/")[0]

    # Show the entry for confirmation
    entry_lines = [
        f"  - name: {skill_name}",
        f'    description: "{description}"',
        f'    author: "{author}"',
        f'    repo: "{repo}"',
        f'    path: "{skill_path}"',
        f"    tags: [{', '.join(tags)}]",
    ]
    entry_text = "\n".join(entry_lines)

    print(f"\n  Generated catalog entry:\n")
    for line in entry_lines:
        print(f"    {line}")

    # Ask for confirmation
    if not args.yes:
        try:
            answer = input("\n  Add to catalog and create PR? [y/N] ").strip().lower()
        except (EOFError, KeyboardInterrupt):
            answer = "n"
        if answer not in ("y", "yes"):
            print("  Cancelled.\n")
            return

    # Append to community-skills.yaml
    catalog_text = CATALOG_FILE.read_text(encoding="utf-8")
    catalog_text = catalog_text.rstrip() + "\n\n" + entry_text + "\n"
    CATALOG_FILE.write_text(catalog_text, encoding="utf-8")
    print(f"\n  [OK] Added entry to community-skills.yaml")

    # Try to create branch + PR via GitHub CLI
    try:
        subprocess.run(["gh", "--version"], capture_output=True, check=True)
        has_gh = True
    except (FileNotFoundError, subprocess.CalledProcessError):
        has_gh = False

    if not has_gh:
        print(f"\n  GitHub CLI (gh) not found — skipping PR creation.")
        print(f"  To complete publishing, commit the change and open a PR manually:")
        print(f"    git checkout -b community-skill/{skill_name}")
        print(f"    git add community-skills.yaml")
        print(f'    git commit -m "Add community skill: {skill_name}"')
        print(f"    git push -u origin community-skill/{skill_name}")
        print(f"    gh pr create --draft --title \"Add community skill: {skill_name}\"\n")
        return

    branch_name = f"community-skill/{skill_name}"
    print(f"  Creating branch: {branch_name}")

    try:
        subprocess.run(["git", "checkout", "-b", branch_name],
                       cwd=str(REPO_ROOT), capture_output=True, check=True)
        subprocess.run(["git", "add", "community-skills.yaml"],
                       cwd=str(REPO_ROOT), capture_output=True, check=True)
        subprocess.run(["git", "commit", "-m", f"Add community skill: {skill_name}"],
                       cwd=str(REPO_ROOT), capture_output=True, check=True)
        subprocess.run(["git", "push", "-u", "origin", branch_name],
                       cwd=str(REPO_ROOT), capture_output=True, check=True)

        pr_body = (
            f"## New Community Skill: `{skill_name}`\n\n"
            f"**Repo:** https://github.com/{repo}\n"
            f"**Author:** @{author}\n"
            f"**Description:** {description}\n"
            f"**Tags:** {', '.join(tags)}\n\n"
            f"Auto-generated by `python devtools/install_skill.py publish`.\n"
        )
        result = subprocess.run(
            ["gh", "pr", "create", "--draft",
             "--title", f"Add community skill: {skill_name}",
             "--body", pr_body],
            cwd=str(REPO_ROOT), capture_output=True, text=True,
        )
        if result.returncode == 0:
            pr_url = result.stdout.strip()
            print(f"  [OK] Draft PR created: {pr_url}")
        else:
            print(f"  [!!] PR creation failed: {result.stderr.strip()}")
            print(f"  Push succeeded — create PR manually from: {branch_name}")

    except subprocess.CalledProcessError as e:
        print(f"  [!!] Git error: {e}")
        print(f"  The catalog entry was saved. Create the branch/PR manually.")

    print()


# ── Main ───────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(
        description="Install community skills from the NeqSim skill catalog.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=textwrap.dedent("""\
            Examples:
              python devtools/install_skill.py list
              python devtools/install_skill.py search "flow assurance"
              python devtools/install_skill.py install neqsim-example-skill
              python devtools/install_skill.py installed
              python devtools/install_skill.py remove neqsim-example-skill
              python devtools/install_skill.py publish user/neqsim-my-skill
        """),
    )
    sub = parser.add_subparsers(dest="command")

    sub.add_parser("list", help="List all skills in the catalog")

    p_search = sub.add_parser("search", help="Search skills by keyword")
    p_search.add_argument("query", help="Search term")

    p_info = sub.add_parser("info", help="Show details for a skill")
    p_info.add_argument("name", help="Skill name")

    p_install = sub.add_parser("install", help="Install a skill")
    p_install.add_argument("name", help="Skill name from catalog")
    p_install.add_argument("--force", action="store_true", help="Reinstall if exists")

    sub.add_parser("installed", help="Show installed skills")

    p_remove = sub.add_parser("remove", help="Remove an installed skill")
    p_remove.add_argument("name", help="Skill name to remove")

    p_publish = sub.add_parser("publish", help="Publish your skill to the catalog")
    p_publish.add_argument("repo", help="GitHub repo (owner/repo) containing the skill")
    p_publish.add_argument("--path", default="SKILL.md", help="Path to SKILL.md in the repo")
    p_publish.add_argument("--yes", "-y", action="store_true", help="Skip confirmation prompt")

    args = parser.parse_args()
    if not args.command:
        parser.print_help()
        sys.exit(0)

    skills = load_catalog()

    commands = {
        "list": cmd_list,
        "search": cmd_search,
        "info": cmd_info,
        "install": cmd_install,
        "installed": cmd_installed,
        "remove": cmd_remove,
        "publish": cmd_publish,
    }
    commands[args.command](skills, args)


if __name__ == "__main__":
    main()
