#!/usr/bin/env python3
"""Install community and private skills from NeqSim skill catalogs.

Usage:
    neqsim skill list              # list all catalogs
    neqsim skill list --private    # list private catalog only
    neqsim skill search <query>    # search by name/tag
    neqsim skill install <name>    # install a skill
    neqsim skill installed         # show installed skills
    neqsim skill remove <name>     # remove an installed skill
    neqsim skill info <name>       # show skill details
    neqsim skill publish <repo>    # publish your skill to catalog
    neqsim skill private-init      # create private catalog template

Skills are installed to ~/.neqsim/skills/<name>/SKILL.md so they don't
pollute the core repo. AI tools can be configured to read from that path.

Private skills are listed in ~/.neqsim/private-skills.yaml (gitignored,
never committed). They support local file paths, network shares, and
private GitHub repos.
"""""

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
PRIVATE_CATALOG_FILE = Path.home() / ".neqsim" / "private-skills.yaml"
INSTALL_DIR = Path.home() / ".neqsim" / "skills"
MANIFEST_FILE = INSTALL_DIR / "installed.json"

# ── Private catalog template ───────────────────────────────────────────
PRIVATE_CATALOG_TEMPLATE = """\
# Private / Internal Skill Catalog
#
# This file lists skills that are private to your organisation.
# It is stored at ~/.neqsim/private-skills.yaml and should NEVER
# be committed to a public repository.
#
# Skills can be sourced from:
#   - Local file paths (source: local)
#   - Network shares (source: local, with UNC path)
#   - Private GitHub repos (source: github, requires GITHUB_TOKEN)
#   - Internal Git servers (source: url, with direct URL)
#
# Install with: neqsim skill install <name>

catalog_version: "1.0"
last_updated: "{today}"
organisation: "your-company"

skills:
  # ── Local file path ──
  # - name: neqsim-company-stid
  #   description: "STID document retrieval for our installations"
  #   author: "your-name"
  #   source: local
  #   path: "C:/Users/you/.neqsim/skills/neqsim-company-stid/SKILL.md"
  #   tags: [stid, documents, internal]

  # ── Network share ──
  # - name: neqsim-company-standards
  #   description: "Company TR requirements for equipment design"
  #   author: "engineering-team"
  #   source: local
  #   path: "//server/share/neqsim-skills/neqsim-company-standards/SKILL.md"
  #   tags: [standards, tr, internal]

  # ── Private GitHub repo ──
  # - name: neqsim-plant-data-mapping
  #   description: "PI tag mappings for our platforms"
  #   author: "data-team"
  #   source: github
  #   repo: "your-org/neqsim-plant-skills"
  #   path: "skills/neqsim-plant-data-mapping/SKILL.md"
  #   tags: [pi, historian, plant-data]

  # ── Direct URL (internal Git server, Azure DevOps, etc.) ──
  # - name: neqsim-internal-workflow
  #   description: "Internal simulation workflow for project X"
  #   author: "project-team"
  #   source: url
  #   url: "https://git.internal.company.com/raw/repo/main/SKILL.md"
  #   tags: [workflow, internal]
"""


def load_catalog(private_only=False):
    """Load skill catalogs. Merges community + private by default."""
    skills = []

    # Load community catalog (unless private-only requested)
    if not private_only and CATALOG_FILE.exists():
        text = CATALOG_FILE.read_text(encoding="utf-8")
        if yaml is not None:
            data = yaml.safe_load(text)
        else:
            data = _parse_catalog_fallback(text)
        for s in data.get("skills", []):
            s["_source"] = "community"
            skills.append(s)

    # Load private catalog if it exists
    if PRIVATE_CATALOG_FILE.exists():
        text = PRIVATE_CATALOG_FILE.read_text(encoding="utf-8")
        if yaml is not None:
            data = yaml.safe_load(text) or {}
        else:
            data = _parse_catalog_fallback(text)
        for s in (data.get("skills") or []):
            s["_source"] = "private"
            skills.append(s)

    if not skills:
        if private_only:
            if not PRIVATE_CATALOG_FILE.exists():
                print(f"  [!!] Private catalog not found: {PRIVATE_CATALOG_FILE}")
                print(f"  Run: neqsim skill private-init")
            else:
                print(f"\n  Private catalog has no skills yet.")
                print(f"  Edit: {PRIVATE_CATALOG_FILE}")
                print(f"  Uncomment and fill in the template entries.\n")
            sys.exit(1)
        else:
            print(f"  [!!] No catalogs found.")
            sys.exit(1)

    return skills


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
    private_only = getattr(args, "private", False)
    source_filter = "private" if private_only else None
    filtered = [s for s in skills if not source_filter or s.get("_source") == source_filter]

    label = "Private" if private_only else "All"
    print(f"\n  {label} Skill Catalog ({len(filtered)} skills)\n")
    print(f"  {'Name':<35} {'Source':<10} {'Tags':<20} {'Installed'}")
    print(f"  {'-'*35} {'-'*10} {'-'*20} {'-'*9}")
    for s in filtered:
        name = s.get("name", "?")
        source = s.get("_source", "?")
        tags = ", ".join(s.get("tags", [])) if isinstance(s.get("tags"), list) else str(s.get("tags", ""))
        installed = "yes" if name in manifest else ""
        print(f"  {name:<35} {source:<10} {tags:<20} {installed}")
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
    source = skill.get("_source", "community")
    print(f"\n  Skill: {skill.get('name')}")
    print(f"  Source: {source}")
    print(f"  Description: {skill.get('description', '-')}")
    print(f"  Author: {skill.get('author', '-')}")
    src_type = skill.get("source", "github")
    if src_type == "local":
        print(f"  Path: {skill.get('path', '-')}")
    elif src_type == "url":
        print(f"  URL: {skill.get('url', '-')}")
    else:
        print(f"  Repo: https://github.com/{skill.get('repo', '-')}")
        print(f"  Path: {skill.get('path', 'SKILL.md')}")
    tags = skill.get("tags", [])
    print(f"  Tags: {', '.join(tags) if isinstance(tags, list) else tags}")
    print(f"  Installed: {'yes' if name in manifest else 'no'}")
    if name in manifest:
        print(f"  Local path: {manifest[name].get('path', '-')}")
    print()


def _install_from_local(skill, dest_file):
    """Install a skill from a local file path or network share."""
    import shutil
    src_path = Path(skill["path"])
    if not src_path.exists():
        print(f"  [!!] Source file not found: {src_path}")
        sys.exit(1)
    shutil.copy2(str(src_path), str(dest_file))
    print(f"  [OK] Copied from: {src_path}")


def _install_from_url(skill, dest_file):
    """Install a skill from a direct URL (internal Git, Azure DevOps, etc.)."""
    import urllib.request
    import urllib.error
    url = skill.get("url", "")
    if not url:
        print(f"  [!!] No URL specified for '{skill.get('name')}'.")
        sys.exit(1)
    print(f"  Downloading: {url}")

    req = urllib.request.Request(url)
    # Support token-based auth via environment variable
    token = os.environ.get("PRIVATE_SKILL_TOKEN", "")
    if token:
        req.add_header("Authorization", f"Bearer {token}")

    with urllib.request.urlopen(req) as resp:
        dest_file.write_bytes(resp.read())


def _install_from_github(skill, dest_file):
    """Install a skill from a GitHub repo (public or private)."""
    import urllib.request
    import urllib.error

    repo = skill.get("repo", "")
    path = skill.get("path", "SKILL.md")
    if not repo:
        print(f"  [!!] No repo specified for '{skill.get('name')}'.")
        sys.exit(1)

    raw_url = f"https://raw.githubusercontent.com/{repo}/main/{path}"
    print(f"  Downloading: {raw_url}")

    req = urllib.request.Request(raw_url)
    # Private repos need GITHUB_TOKEN
    token = os.environ.get("GITHUB_TOKEN", "")
    if token:
        req.add_header("Authorization", f"token {token}")

    try:
        with urllib.request.urlopen(req) as resp:
            dest_file.write_bytes(resp.read())
    except urllib.error.HTTPError:
        # Try 'master' branch if 'main' fails
        raw_url_master = f"https://raw.githubusercontent.com/{repo}/master/{path}"
        print(f"  Retrying: {raw_url_master}")
        req2 = urllib.request.Request(raw_url_master)
        if token:
            req2.add_header("Authorization", f"token {token}")
        with urllib.request.urlopen(req2) as resp:
            dest_file.write_bytes(resp.read())


def cmd_install(skills, args):
    """Install a skill from the catalog (community or private)."""
    name = args.name
    skill = next((s for s in skills if s.get("name") == name), None)
    if not skill:
        print(f"\n  Skill '{name}' not found in catalog.")
        print(f"  Run: neqsim skill list\n")
        sys.exit(1)

    manifest = load_manifest()
    if name in manifest and not args.force:
        print(f"\n  Skill '{name}' already installed at {manifest[name]['path']}")
        print(f"  Use --force to reinstall.\n")
        return

    dest_dir = INSTALL_DIR / name
    dest_dir.mkdir(parents=True, exist_ok=True)
    dest_file = dest_dir / "SKILL.md"

    try:
        source_type = skill.get("source", "github")
        print(f"\n  Installing '{name}' (source: {source_type})...")

        if source_type == "local":
            _install_from_local(skill, dest_file)
        elif source_type == "url":
            _install_from_url(skill, dest_file)
        else:
            _install_from_github(skill, dest_file)

        # Verify we got a real SKILL.md
        content = dest_file.read_text(encoding="utf-8", errors="replace")
        if len(content) < 20 or "<html" in content.lower()[:200]:
            dest_file.unlink()
            dest_dir.rmdir()
            print(f"  [!!] Downloaded content doesn't look like a SKILL.md file.")
            sys.exit(1)

        manifest[name] = {
            "path": str(dest_file),
            "source": skill.get("_source", "community"),
            "source_type": source_type,
            "repo": skill.get("repo", ""),
            "remote_path": skill.get("path", ""),
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
        print(f"  Run: neqsim skill list\n")
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
            f"Auto-generated by `neqsim skill publish`.\n"
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


def cmd_private_init(skills, args):
    """Create the private skill catalog template at ~/.neqsim/private-skills.yaml."""
    if PRIVATE_CATALOG_FILE.exists():
        print(f"\n  Private catalog already exists: {PRIVATE_CATALOG_FILE}")
        print(f"  Edit it to add your private skills.\n")
        return

    from datetime import date
    content = PRIVATE_CATALOG_TEMPLATE.format(today=date.today().isoformat())
    PRIVATE_CATALOG_FILE.parent.mkdir(parents=True, exist_ok=True)
    PRIVATE_CATALOG_FILE.write_text(content, encoding="utf-8")
    print(f"\n  [OK] Created private catalog: {PRIVATE_CATALOG_FILE}")
    print(f"  Edit it to add your internal/company skills.")
    print(f"  Then: neqsim skill list --private\n")

    print()


# ── Main ───────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(
        description="Install community and private skills from NeqSim skill catalogs.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=textwrap.dedent("""\
            Examples:
              neqsim skill list
              neqsim skill list --private
              neqsim skill search "flow assurance"
              neqsim skill install neqsim-example-skill
              neqsim skill installed
              neqsim skill remove neqsim-example-skill
              neqsim skill publish user/neqsim-my-skill
              neqsim skill private-init

            Private skills:
              neqsim skill private-init    # create ~/.neqsim/private-skills.yaml
              # Edit the file to add your private skills, then:
              neqsim skill list --private   # verify entries
              neqsim skill install <name>   # install from any catalog

            Private repos require GITHUB_TOKEN env var.
            Internal URLs can use PRIVATE_SKILL_TOKEN env var.
        """),
    )
    sub = parser.add_subparsers(dest="command")

    p_list = sub.add_parser("list", help="List all skills in the catalog")
    p_list.add_argument("--private", action="store_true", help="List private catalog only")

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

    sub.add_parser("private-init", help="Create private catalog template at ~/.neqsim/")

    args = parser.parse_args()
    if not args.command:
        parser.print_help()
        sys.exit(0)

    # private-init doesn't need catalog loaded
    if args.command == "private-init":
        cmd_private_init([], args)
        return

    private_only = getattr(args, "private", False)
    skills = load_catalog(private_only=private_only)

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
