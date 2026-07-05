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

Community catalogs can list individual skills or GitHub repositories to
discover online. Repository discovery first reads the remote
community-skills.yaml catalog, then falls back to scanning for SKILL.md files.

Private skills are listed in ~/.neqsim/private-skills.yaml (gitignored,
never committed). They support local file paths, network shares, private
GitHub repos via browser/SSO-backed gh CLI, and internal git remotes via
Git Credential Manager.
"""

import argparse
from datetime import datetime, timezone
import fnmatch
import json
import os
import shutil
import subprocess
import sys
import tempfile
import textwrap
import urllib.error
import urllib.parse
import urllib.request
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
EXPORT_DIR = Path.home() / ".neqsim" / "export"
GITHUB_TIMEOUT_SECONDS = 20
DEFAULT_SKILL_PATH_GLOB = "**/SKILL.md"

TAG_PATTERNS = [
    "thermodynamic", "process", "pvt", "flow assurance", "hydrate",
    "pipeline", "compressor", "separator", "distillation", "heat exchanger",
    "ccs", "hydrogen", "subsea", "well", "economics", "safety",
    "electrolyte", "reaction", "power", "emissions", "corrosion",
]

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
#   - Private GitHub repos (source: github, auth: github-cli)
#   - Internal Git servers (source: git, auth: git-credential-manager)
#   - Internal raw URLs (source: url, optional PRIVATE_SKILL_TOKEN fallback)
#
# Install with: neqsim skill install <name>

catalog_version: "1.0"
last_updated: "{today}"
organisation: "your-company"

repositories:
    # -- Private GitHub repository discovery --
    # Preferred: run `gh auth login --web` once and let GitHub/SSO handle login.
    # NeqSim does not request, inspect, or store credentials.
    # Use catalog_path: "" when the repo has no community-skills.yaml and should
    # be scanned for SKILL.md files directly.
    # - repo: "your-org/neqsim-enterprise-skills"
    #   source: github
    #   auth: github-cli
    #   branch: "main"
    #   catalog_path: ""
    #   skill_path_glob: "skills/**/SKILL.md"
    #   name_prefix: "enterprise-"  # optional, avoids public/private name clashes
    #   tags: [enterprise, private]

    # -- Internal Git repository discovery --
    # Preferred when your enterprise uses Git Credential Manager / SSO for HTTPS.
    # Run your normal `git clone` login flow once; NeqSim shells out to git and
    # never handles secrets itself.
    # - source: git
    #   auth: git-credential-manager
    #   url: "https://git.internal.company.com/neqsim/enterprise-skills.git"
    #   branch: "main"
    #   catalog_path: "enterprise-skills.yaml"
    #   skill_path_glob: "skills/**/SKILL.md"
    #   name_prefix: "enterprise-"
    #   tags: [enterprise, private]

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
    #   auth: github-cli
  #   repo: "your-org/neqsim-plant-skills"
  #   path: "skills/neqsim-plant-data-mapping/SKILL.md"
  #   tags: [pi, historian, plant-data]

    # ── Internal Git repo using Git Credential Manager / SSO ──
    # - name: neqsim-internal-git-skill
    #   description: "Internal skill fetched through git credentials"
    #   author: "engineering-team"
    #   source: git
    #   auth: git-credential-manager
    #   url: "https://git.internal.company.com/neqsim/enterprise-skills.git"
    #   branch: "main"
    #   path: "skills/neqsim-internal-git-skill/SKILL.md"
    #   tags: [workflow, internal]

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
        data = _load_catalog_file(CATALOG_FILE)
        _add_catalog_entries(skills, data, "community")

    # Load private catalog if it exists
    if PRIVATE_CATALOG_FILE.exists():
        data = _load_catalog_file(PRIVATE_CATALOG_FILE)
        _add_catalog_entries(skills, data, "private")

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


def _load_catalog_file(path):
    """Load a YAML catalog file from disk."""
    return _parse_catalog_text(path.read_text(encoding="utf-8"))


def _parse_catalog_text(text):
    """Parse catalog YAML text with PyYAML or the local fallback parser."""
    if yaml is not None:
        return yaml.safe_load(text) or {}
    return _parse_catalog_fallback(text)


def _add_catalog_entries(skills, data, source):
    """Add direct and discovered catalog entries to the skills list."""
    for skill in (data.get("skills") or []):
        _append_skill(skills, skill, source)

    for repository in (data.get("repositories") or []):
        try:
            if isinstance(repository, dict) and repository.get("source") == "git":
                discovered = _discover_git_repository_skills(repository)
            else:
                discovered = _discover_github_repository_skills(repository)
            for skill in discovered:
                _append_skill(skills, skill, source)
        except Exception as exc:
            repo = repository.get("repo", "?") if isinstance(repository, dict) else "?"
            print(f"  [!!] Could not discover skills from {repo}: {exc}", file=sys.stderr)


def _append_skill(skills, skill, source):
    """Append a skill entry if it is valid and not already present."""
    if not isinstance(skill, dict):
        return
    name = skill.get("name")
    if not name:
        return
    if any(existing.get("name") == name for existing in skills):
        return
    normalized = dict(skill)
    normalized["_source"] = source
    skills.append(normalized)


def _parse_catalog_fallback(text):
    """Fallback parser when PyYAML is not installed.

    Handles the simple list-of-dicts format used in community-skills.yaml,
    including the top-level ``skills`` and ``repositories`` sections.
    """
    data = {"skills": [], "repositories": []}
    section = None
    current = None
    for raw_line in text.splitlines():
        line = raw_line.strip()
        if line.startswith("#") or not line:
            continue
        if not raw_line.startswith((" ", "\t")) and line.endswith(":"):
            if current and section in data:
                data[section].append(current)
            section = line[:-1]
            current = None
        elif line.startswith("- "):
            if current and section in data:
                data[section].append(current)
            current = {}
            remainder = line[2:].strip()
            if ":" in remainder:
                key, val = remainder.split(":", 1)
                current[key.strip()] = _parse_scalar_value(val.strip())
        elif current and ":" in line:
            key, val = line.split(":", 1)
            current[key.strip()] = _parse_scalar_value(val.strip())
    if current and section in data:
        data[section].append(current)
    return data


def _parse_scalar_value(value):
    """Parse a simple YAML scalar or inline list value."""
    value = value.strip()
    if value.startswith("[") and value.endswith("]"):
        items = value[1:-1].split(",")
        return [item.strip().strip('"').strip("'") for item in items if item.strip()]
    return value.strip('"').strip("'")


def _normalize_auth_mode(auth):
    """Normalize a catalog auth mode into a stable lowercase token."""
    return str(auth or "auto").strip().lower().replace("_", "-")


def _prefer_gh_auth(auth=None):
    """Return true when a GitHub request should prefer gh CLI authentication."""
    return _normalize_auth_mode(auth) in set([
        "gh", "github-cli", "sso", "browser-sso", "github-sso"
    ])


def _github_token_present():
    """Return true when a GitHub token fallback is present in the environment."""
    return bool(os.environ.get("GITHUB_TOKEN", ""))


def _github_entry_auth(entry):
    """Return the GitHub auth mode requested by a catalog entry."""
    if not isinstance(entry, dict):
        return "auto"
    return entry.get("auth") or entry.get("authentication") or "auto"


def _extract_frontmatter(content):
    """Extract YAML frontmatter from a SKILL.md document."""
    lines = content.splitlines()
    if not lines or lines[0].strip() != "---":
        return {}
    end = None
    for index, line in enumerate(lines[1:], start=1):
        if line.strip() == "---":
            end = index
            break
    if end is None:
        return {}
    frontmatter = "\n".join(lines[1:end])
    if yaml is not None:
        return yaml.safe_load(frontmatter) or {}

    result = {}
    for raw_line in frontmatter.splitlines():
        if raw_line.startswith((" ", "\t")):
            continue
        line = raw_line.strip()
        if not line or line.startswith("#") or ":" not in line:
            continue
        key, value = line.split(":", 1)
        value = value.strip()
        if value:
            result[key.strip()] = _parse_scalar_value(value)
    return result


def _github_request(url, accept="application/vnd.github+json"):
    """Return bytes from a GitHub URL, adding auth headers when available."""
    req = urllib.request.Request(url)
    req.add_header("User-Agent", "neqsim-skill-installer")
    if accept:
        req.add_header("Accept", accept)
    token = os.environ.get("GITHUB_TOKEN", "")
    if token:
        req.add_header("Authorization", f"token {token}")
    with urllib.request.urlopen(req, timeout=GITHUB_TIMEOUT_SECONDS) as resp:
        return resp.read()


def _gh_api_request(endpoint, accept="application/vnd.github+json"):
    """Return bytes from GitHub CLI using the user's active gh login."""
    cmd = ["gh", "api", endpoint]
    if accept:
        cmd.extend(["--header", f"Accept: {accept}"])
    return subprocess.check_output(cmd, stderr=subprocess.PIPE)


def _github_api_endpoint(repo, path="", query=""):
    """Build a GitHub API endpoint for use with gh api."""
    encoded_repo = "/".join(urllib.parse.quote(part, safe="") for part in repo.split("/"))
    endpoint = f"repos/{encoded_repo}"
    if path:
        endpoint = f"{endpoint}/{path}"
    if query:
        endpoint = f"{endpoint}?{query}"
    return endpoint


def _github_contents_endpoint(repo, path, branch):
    """Build a GitHub contents API endpoint for a repository file."""
    encoded_path = urllib.parse.quote(path, safe="/")
    encoded_branch = urllib.parse.quote(branch, safe="")
    return _github_api_endpoint(
        repo, f"contents/{encoded_path}", f"ref={encoded_branch}")


def _fetch_github_contents_with_gh(repo, path, branch):
    """Fetch repository file bytes using GitHub CLI authentication."""
    endpoint = _github_contents_endpoint(repo, path, branch)
    return _gh_api_request(endpoint, accept="application/vnd.github.raw")


def _github_access_error(repo, operation, http_error, gh_error):
    """Return an access error that explains both auth paths."""
    if gh_error is None:
        return http_error
    return RuntimeError(
        f"Could not {operation} for GitHub repo {repo}. "
        "Preferred enterprise path: run `gh auth login --web` and complete your browser/SSO login. "
        "Token fallback: set GITHUB_TOKEN with repo read access. "
        f"HTTP error: {http_error}. gh error: {gh_error}"
    )


def _get_default_github_branch(repo, auth=None):
    """Return the default branch for a GitHub repo, or None if unavailable."""
    encoded_repo = "/".join(urllib.parse.quote(part, safe="") for part in repo.split("/"))
    url = f"https://api.github.com/repos/{encoded_repo}"
    if _prefer_gh_auth(auth):
        try:
            endpoint = _github_api_endpoint(repo)
            data = json.loads(_gh_api_request(endpoint).decode("utf-8"))
            return data.get("default_branch")
        except Exception:
            pass
    try:
        data = json.loads(_github_request(url).decode("utf-8"))
        return data.get("default_branch")
    except Exception:
        try:
            endpoint = _github_api_endpoint(repo)
            data = json.loads(_gh_api_request(endpoint).decode("utf-8"))
            return data.get("default_branch")
        except Exception:
            return None


def _branch_candidates(branch=None):
    """Return GitHub branch candidates without duplicates."""
    candidates = []
    for candidate in [branch, "main", "master"]:
        if candidate and candidate not in candidates:
            candidates.append(candidate)
    return candidates


def _raw_github_url(repo, path, branch):
    """Build a raw.githubusercontent.com URL for a repo path."""
    encoded_path = urllib.parse.quote(path, safe="/")
    return f"https://raw.githubusercontent.com/{repo}/{branch}/{encoded_path}"


def _fetch_github_bytes(repo, path, branch=None, auth=None):
    """Fetch a file from GitHub raw content using branch fallback."""
    last_error = None
    last_gh_error = None
    for candidate in _branch_candidates(branch):
        if _prefer_gh_auth(auth):
            try:
                content = _fetch_github_contents_with_gh(repo, path, candidate)
                source = f"gh api {_github_contents_endpoint(repo, path, candidate)}"
                return content, candidate, source
            except Exception as gh_exc:
                last_gh_error = gh_exc
        raw_url = _raw_github_url(repo, path, candidate)
        try:
            content = _github_request(raw_url, accept="text/plain")
            return content, candidate, raw_url
        except urllib.error.HTTPError as exc:
            last_error = exc
            if not _prefer_gh_auth(auth):
                try:
                    content = _fetch_github_contents_with_gh(repo, path, candidate)
                    source = f"gh api {_github_contents_endpoint(repo, path, candidate)}"
                    return content, candidate, source
                except Exception as gh_exc:
                    last_gh_error = gh_exc
    if last_error is not None:
        raise _github_access_error(repo, f"fetch {path}", last_error, last_gh_error)
    raise RuntimeError(f"No branch candidates available for {repo}")


def _fetch_github_text(repo, path, branch=None, auth=None):
    """Fetch a UTF-8 text file from GitHub raw content."""
    content, _branch, _url = _fetch_github_bytes(repo, path, branch=branch, auth=auth)
    return content.decode("utf-8", errors="replace")


def _list_github_tree_paths(repo, branch=None, auth=None):
    """List files in a GitHub repository tree using the online GitHub API."""
    last_error = None
    last_gh_error = None
    for candidate in _branch_candidates(branch):
        encoded_repo = "/".join(urllib.parse.quote(part, safe="") for part in repo.split("/"))
        encoded_branch = urllib.parse.quote(candidate, safe="")
        url = f"https://api.github.com/repos/{encoded_repo}/git/trees/{encoded_branch}?recursive=1"
        if _prefer_gh_auth(auth):
            try:
                endpoint = _github_api_endpoint(
                    repo, f"git/trees/{encoded_branch}", "recursive=1")
                data = json.loads(_gh_api_request(endpoint).decode("utf-8"))
                paths = [item.get("path", "") for item in data.get("tree", [])
                         if item.get("type") == "blob" and item.get("path")]
                if data.get("truncated"):
                    print(f"  [!!] GitHub tree for {repo}@{candidate} is truncated.", file=sys.stderr)
                return candidate, paths
            except Exception as gh_exc:
                last_gh_error = gh_exc
        try:
            data = json.loads(_github_request(url).decode("utf-8"))
            paths = [item.get("path", "") for item in data.get("tree", [])
                     if item.get("type") == "blob" and item.get("path")]
            if data.get("truncated"):
                print(f"  [!!] GitHub tree for {repo}@{candidate} is truncated.", file=sys.stderr)
            return candidate, paths
        except urllib.error.HTTPError as exc:
            last_error = exc
            if not _prefer_gh_auth(auth):
                try:
                    endpoint = _github_api_endpoint(
                        repo, f"git/trees/{encoded_branch}", "recursive=1")
                    data = json.loads(_gh_api_request(endpoint).decode("utf-8"))
                    paths = [item.get("path", "") for item in data.get("tree", [])
                             if item.get("type") == "blob" and item.get("path")]
                    if data.get("truncated"):
                        print(f"  [!!] GitHub tree for {repo}@{candidate} is truncated.", file=sys.stderr)
                    return candidate, paths
                except Exception as gh_exc:
                    last_gh_error = gh_exc
    if last_error is not None:
        raise _github_access_error(repo, "list tree paths", last_error, last_gh_error)
    raise RuntimeError(f"No branch candidates available for {repo}")


def _merge_tags(*tag_sets):
    """Merge tag lists while preserving order and avoiding duplicates."""
    merged = []
    for tag_set in tag_sets:
        if isinstance(tag_set, str):
            tag_set = [tag_set]
        for tag in tag_set or []:
            if tag and tag not in merged:
                merged.append(tag)
    return merged


def _infer_tags(content, path=""):
    """Infer searchable tags from skill content and path."""
    tags = []
    searchable = f"{path}\n{content}".lower()
    for tag in TAG_PATTERNS:
        if tag in searchable:
            tags.append(tag.replace(" ", "-"))
    return tags[:5]


def _normalize_discovered_skill(skill, repository, default_branch=None, content=""):
    """Normalize a skill discovered from a repository-level catalog or scan."""
    normalized = dict(skill)
    name_prefix = repository.get("name_prefix", "")
    if name_prefix and normalized.get("name") and not str(normalized["name"]).startswith(name_prefix):
        normalized["name"] = f"{name_prefix}{normalized['name']}"
    repo = normalized.get("repo") or repository.get("repo", "")
    path = normalized.get("path") or repository.get("default_path", "SKILL.md")
    normalized["repo"] = repo
    normalized["path"] = path
    normalized["source"] = normalized.get("source", repository.get("source", "github"))
    if "auth" not in normalized and repository.get("auth"):
        normalized["auth"] = repository.get("auth")
    if "url" not in normalized and repository.get("url"):
        normalized["url"] = repository.get("url")
    if default_branch and "branch" not in normalized:
        normalized["branch"] = default_branch
    if "author" not in normalized:
        normalized["author"] = repository.get("author") or (repo.split("/", 1)[0] if repo else "private")
    if "min_neqsim_version" not in normalized and repository.get("min_neqsim_version"):
        normalized["min_neqsim_version"] = repository.get("min_neqsim_version")
    inferred_tags = _infer_tags(content, path) if content else []
    normalized["tags"] = _merge_tags(normalized.get("tags"), repository.get("tags"), inferred_tags)
    normalized["_discovered_from"] = repository.get("repo", repo)
    return normalized


def _discover_skills_from_remote_catalog(repository, branch):
    """Discover skills from a remote community-skills.yaml file."""
    repo = repository.get("repo", "")
    catalog_path = repository.get("catalog_path", "community-skills.yaml")
    if not catalog_path:
        return []
    text = _fetch_github_text(repo, catalog_path, branch=branch, auth=_github_entry_auth(repository))
    data = _parse_catalog_text(text)
    discovered = []
    for skill in (data.get("skills") or []):
        if not isinstance(skill, dict) or not skill.get("name"):
            continue
        discovered.append(_normalize_discovered_skill(skill, repository, branch))
    return discovered


def _discover_skills_by_scanning_repo(repository, branch):
    """Discover skills by scanning an online GitHub repo for SKILL.md files."""
    repo = repository.get("repo", "")
    pattern = repository.get("skill_path_glob") or repository.get("path_glob") or DEFAULT_SKILL_PATH_GLOB
    used_branch, paths = _list_github_tree_paths(repo, branch=branch, auth=_github_entry_auth(repository))
    discovered = []
    for path in sorted(paths):
        if not fnmatch.fnmatch(path, pattern):
            continue
        if not path.endswith("SKILL.md"):
            continue
        content = _fetch_github_text(repo, path, branch=used_branch, auth=_github_entry_auth(repository))
        metadata = _extract_frontmatter(content)
        name = metadata.get("name")
        if not name:
            folder_name = Path(path).parent.name
            name = folder_name if folder_name.startswith("neqsim-") else f"neqsim-{folder_name}"
        skill = {
            "name": name,
            "version": metadata.get("version", repository.get("version", "")),
            "description": metadata.get("description", f"Community skill from {repo}/{path}"),
            "path": path,
        }
        discovered.append(_normalize_discovered_skill(skill, repository, used_branch, content))
    return discovered


def _discover_github_repository_skills(repository):
    """Discover every skill available from an online GitHub repository."""
    if not isinstance(repository, dict):
        return []
    if repository.get("source", "github") != "github":
        return []
    repo = repository.get("repo", "")
    if not repo:
        return []
    branch = repository.get("branch") or _get_default_github_branch(
        repo, auth=_github_entry_auth(repository))
    try:
        catalog_skills = _discover_skills_from_remote_catalog(repository, branch)
        if catalog_skills:
            return catalog_skills
    except Exception:
        pass
    return _discover_skills_by_scanning_repo(repository, branch)


def _git_repository_url(entry):
    """Return a clone URL for a git-backed catalog entry."""
    url = entry.get("url", "") if isinstance(entry, dict) else ""
    if url:
        return url
    repo = entry.get("repo", "") if isinstance(entry, dict) else ""
    if repo:
        return f"https://github.com/{repo}.git"
    return ""


def _clone_git_repository(entry, destination):
    """Clone a repository using the user's configured git credential broker."""
    url = _git_repository_url(entry)
    if not url:
        raise RuntimeError("git source requires url or repo")
    cmd = ["git", "clone", "--depth", "1"]
    branch = entry.get("branch") if isinstance(entry, dict) else None
    if branch:
        cmd.extend(["--branch", str(branch)])
    cmd.extend([url, str(destination)])
    subprocess.check_output(cmd, stderr=subprocess.PIPE)
    try:
        used_branch = subprocess.check_output(
            ["git", "rev-parse", "--abbrev-ref", "HEAD"],
            cwd=str(destination), stderr=subprocess.PIPE,
        ).decode("utf-8", errors="replace").strip()
    except Exception:
        used_branch = branch or ""
    return used_branch


def _read_git_repository_file(entry, path):
    """Read a file from a git repository through a temporary clone."""
    with tempfile.TemporaryDirectory() as tmp:
        repo_dir = Path(tmp) / "repo"
        used_branch = _clone_git_repository(entry, repo_dir)
        file_path = repo_dir / path
        if not file_path.exists() or not file_path.is_file():
            raise RuntimeError(f"Path not found in git source: {path}")
        return file_path.read_bytes(), used_branch


def _discover_git_repository_skills(repository):
    """Discover skills from a git repository using git's credential broker."""
    if not isinstance(repository, dict) or repository.get("source") != "git":
        return []
    pattern = repository.get("skill_path_glob") or repository.get("path_glob") or DEFAULT_SKILL_PATH_GLOB
    with tempfile.TemporaryDirectory() as tmp:
        repo_dir = Path(tmp) / "repo"
        used_branch = _clone_git_repository(repository, repo_dir)
        catalog_path = repository.get("catalog_path", "community-skills.yaml")
        if catalog_path:
            catalog_file = repo_dir / catalog_path
            if catalog_file.exists():
                data = _parse_catalog_text(catalog_file.read_text(encoding="utf-8"))
                discovered = []
                for skill in (data.get("skills") or []):
                    if isinstance(skill, dict) and skill.get("name"):
                        discovered.append(_normalize_discovered_skill(
                            skill, repository, used_branch))
                if discovered:
                    return discovered
        discovered = []
        for file_path in sorted(repo_dir.rglob("SKILL.md")):
            rel_path = file_path.relative_to(repo_dir).as_posix()
            if not fnmatch.fnmatch(rel_path, pattern):
                continue
            content = file_path.read_text(encoding="utf-8", errors="replace")
            metadata = _extract_frontmatter(content)
            name = metadata.get("name")
            if not name:
                folder_name = file_path.parent.name
                name = folder_name if folder_name.startswith("neqsim-") else f"neqsim-{folder_name}"
            skill = {
                "name": name,
                "version": metadata.get("version", repository.get("version", "")),
                "description": metadata.get(
                    "description", f"Private skill from {_git_repository_url(repository)}/{rel_path}"),
                "path": rel_path,
            }
            discovered.append(_normalize_discovered_skill(skill, repository, used_branch, content))
        return discovered


def load_manifest():
    """Load the installed-skills manifest."""
    if MANIFEST_FILE.exists():
        return json.loads(MANIFEST_FILE.read_text(encoding="utf-8"))
    return {}


def save_manifest(manifest):
    """Save the installed-skills manifest."""
    MANIFEST_FILE.parent.mkdir(parents=True, exist_ok=True)
    MANIFEST_FILE.write_text(json.dumps(manifest, indent=2), encoding="utf-8")


# ── Export targets ──────────────────────────────────────────
VSCODE_WORKSPACE_MARKERS = (".github", ".git", "pom.xml", "package.json")
SUPPORTED_EXPORT_TARGETS = ("vscode", "generic")


def find_workspace_root(start=None):
    """Find the nearest workspace root by scanning upward for markers.

    @param start the directory to start scanning from (defaults to CWD)
    @return the workspace root Path, or None when no marker is found
    """
    start_path = Path(start).resolve() if start else Path.cwd().resolve()
    for candidate in [start_path] + list(start_path.parents):
        for marker in VSCODE_WORKSPACE_MARKERS:
            if (candidate / marker).exists():
                return candidate
    return None


def resolve_vscode_skills_dir(explicit_dir=None):
    """Resolve the VS Code skills directory for --vscode export.

    VS Code discovers skills from <workspace>/.github/skills/**/SKILL.md, so the
    export target is always workspace-scoped. Resolution order: explicit dir,
    NEQSIM_VSCODE_SKILLS_DIR env var, then the nearest workspace root.

    @param explicit_dir an explicit target directory (overrides detection)
    @return the resolved skills directory Path, or None when none can be found
    """
    if explicit_dir:
        return Path(explicit_dir).expanduser().resolve()
    env_dir = os.environ.get("NEQSIM_VSCODE_SKILLS_DIR", "")
    if env_dir:
        return Path(env_dir).expanduser().resolve()
    root = find_workspace_root()
    if root is None:
        return None
    return root / ".github" / "skills"


def export_skill_to_vscode(name, source_dir, vscode_dir):
    """Copy an installed skill folder into a VS Code skills directory.

    @param name the skill name (used as the destination subfolder)
    @param source_dir the installed skill folder containing SKILL.md
    @param vscode_dir the VS Code skills directory to export into
    @return the destination skill folder Path
    """
    import shutil
    dest = Path(vscode_dir) / name
    dest.mkdir(parents=True, exist_ok=True)
    for item in Path(source_dir).iterdir():
        target = dest / item.name
        if item.is_dir():
            shutil.copytree(str(item), str(target), dirs_exist_ok=True)
        else:
            shutil.copy2(str(item), str(target))
    return dest


def resolve_generic_skills_dir(explicit_dir=None):
    """Resolve the generic, tool-neutral skills export directory.

    @param explicit_dir an explicit export root directory (overrides default)
    @return the directory where generic skill folders should be written
    """
    if explicit_dir:
        return Path(explicit_dir).expanduser().resolve() / "skills"
    return EXPORT_DIR / "generic" / "skills"


def export_skill_to_generic(name, source_dir, generic_skills_dir):
    """Copy an installed skill folder into the generic export layout.

    @param name the skill name (used as the destination subfolder)
    @param source_dir the installed skill folder containing SKILL.md
    @param generic_skills_dir the generic skills export directory
    @return the destination skill folder Path
    """
    return export_skill_to_vscode(name, source_dir, generic_skills_dir)


def _record_export(manifest, name, target, dest):
    """Record an exported copy in the installed-skills manifest.

    @param manifest the installed-skills manifest to update
    @param name the installed skill name
    @param target the export target name
    @param dest the generated export path
    @return None
    """
    manifest[name].setdefault("exports", {})[target] = str(dest)
    if target == "vscode":
        manifest[name]["vscode_path"] = str(dest)
    elif target == "generic":
        manifest[name]["generic_path"] = str(dest)


def _write_generic_manifest(kind, root_dir, manifest):
    """Write a lightweight manifest for tool-neutral exports.

    @param kind the exported content kind, for example "skills"
    @param root_dir the target-specific export root directory
    @param manifest the installed manifest to summarize
    @return the generated manifest path
    """
    root = Path(root_dir)
    root.mkdir(parents=True, exist_ok=True)
    exports = {}
    for name, info in sorted(manifest.items()):
        generic_path = info.get("exports", {}).get("generic") or info.get("generic_path", "")
        if generic_path:
            exports[name] = {
                "path": generic_path,
                "installed_path": info.get("path", ""),
                "source": info.get("source", ""),
                "source_type": info.get("source_type", ""),
                "version": info.get("version", ""),
                "description": info.get("description", ""),
                "tags": info.get("tags", []),
                "author": info.get("author", ""),
                "min_neqsim_version": info.get("min_neqsim_version", ""),
            }
    dest = root / "manifest.json"
    payload = {}
    if dest.exists():
        try:
            payload = json.loads(dest.read_text(encoding="utf-8"))
        except Exception:
            payload = {}
    payload.update({
        "schema_version": "1.0",
        "target": "generic",
        "exported_at": datetime.now(timezone.utc).isoformat(),
    })
    payload[kind] = exports
    dest.write_text(json.dumps(payload, indent=2), encoding="utf-8")
    return dest


def _export_installed_skill_to_vscode(name, source_dir, args, manifest):
    """Export a freshly installed skill into the VS Code skills directory.

    @param name the installed skill name
    @param source_dir the installed skill folder under ~/.neqsim/skills/<name>
    @param args parsed CLI arguments (reads vscode_dir)
    @param manifest the installed-skills manifest, updated with vscode_path
    @return None
    """
    vscode_dir = resolve_vscode_skills_dir(getattr(args, "vscode_dir", None))
    if vscode_dir is None:
        print("  [!!] Could not locate a VS Code workspace for --vscode export.")
        print("       Run from inside a workspace, set NEQSIM_VSCODE_SKILLS_DIR,")
        print("       or pass --vscode-dir <path-to/.github/skills>.")
        return
    try:
        dest = export_skill_to_vscode(name, source_dir, vscode_dir)
    except Exception as exc:
        print("  [!!] VS Code export failed: {exc}".format(exc=exc))
        return
    _record_export(manifest, name, "vscode", dest)
    save_manifest(manifest)
    print("  [OK] Exported to VS Code skills: {dest}".format(dest=dest))
    print("       Reload VS Code (Developer: Reload Window) to pick it up.")


def _export_installed_skill_to_generic(name, source_dir, args, manifest):
    """Export a freshly installed skill into the generic target layout.

    @param name the installed skill name
    @param source_dir the installed skill folder under ~/.neqsim/skills/<name>
    @param args parsed CLI arguments (reads export_dir)
    @param manifest the installed-skills manifest, updated with generic export path
    @return None
    """
    generic_skills_dir = resolve_generic_skills_dir(getattr(args, "export_dir", None))
    try:
        dest = export_skill_to_generic(name, source_dir, generic_skills_dir)
        _record_export(manifest, name, "generic", dest)
        _write_generic_manifest("skills", generic_skills_dir.parent, manifest)
    except Exception as exc:
        print("  [!!] Generic export failed: {exc}".format(exc=exc))
        return
    save_manifest(manifest)
    print("  [OK] Exported to generic skills: {dest}".format(dest=dest))


def _export_installed_skill(name, source_dir, args, manifest):
    """Export an installed skill to the requested compatibility target.

    @param name the installed skill name
    @param source_dir the installed skill folder under ~/.neqsim/skills/<name>
    @param args parsed CLI arguments
    @param manifest the installed-skills manifest
    @return None
    """
    targets = list(getattr(args, "target", []) or [])
    if getattr(args, "vscode", False) and "vscode" not in targets:
        targets.append("vscode")
    for target in targets:
        if target == "vscode":
            _export_installed_skill_to_vscode(name, source_dir, args, manifest)
        elif target == "generic":
            _export_installed_skill_to_generic(name, source_dir, args, manifest)


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
    repo = skill.get("repo", "")
    path = skill.get("path", "SKILL.md")
    branch = skill.get("branch")
    if not repo:
        print(f"  [!!] No repo specified for '{skill.get('name')}'.")
        sys.exit(1)

    content, used_branch, raw_url = _fetch_github_bytes(
        repo, path, branch=branch, auth=_github_entry_auth(skill))
    print(f"  Downloading: {raw_url}")
    dest_file.write_bytes(content)
    skill["branch"] = used_branch


def _install_from_git(skill, dest_file):
    """Install a skill from a git repository using configured git credentials."""
    path = skill.get("path", "SKILL.md")
    content, used_branch = _read_git_repository_file(skill, path)
    print(f"  Downloading via git: {_git_repository_url(skill)}:{path}")
    dest_file.write_bytes(content)
    skill["branch"] = used_branch


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
        elif source_type == "git":
            _install_from_git(skill, dest_file)
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
            "auth": skill.get("auth", ""),
            "url": skill.get("url", ""),
            "repo": skill.get("repo", ""),
            "remote_path": skill.get("path", ""),
            "branch": skill.get("branch", ""),
            "version": skill.get("version", ""),
            "description": skill.get("description", ""),
            "tags": skill.get("tags", []),
            "author": skill.get("author", ""),
            "min_neqsim_version": skill.get("min_neqsim_version", ""),
        }
        save_manifest(manifest)

        print(f"  [OK] Installed to: {dest_file}")
        print(f"\n  To use in your AI tool, point it at: {dest_dir}\n")

        _export_installed_skill(name, dest_dir, args, manifest)

    except Exception as e:
        print(f"  [!!] Download failed: {e}")
        print(f"  You can manually download from: https://github.com/{skill.get('repo', '')}\n")
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


def cmd_export(skills, args):
    """Export an already-installed skill to one or more compatibility targets."""
    manifest = load_manifest()
    name = args.name
    if name not in manifest:
        print(f"\n  Skill '{name}' is not installed.")
        print(f"  Install it first with: neqsim skill install {name}\n")
        sys.exit(1)
    source_dir = Path(manifest[name].get("path", "")).parent
    if not source_dir.exists():
        print(f"\n  Installed skill folder is missing: {source_dir}\n")
        sys.exit(1)
    _export_installed_skill(name, source_dir, args, manifest)


def _command_available(name):
    """Return true when a command is available on PATH."""
    return shutil.which(name) is not None


def _run_status_command(command):
    """Run a status command without exposing any credential material."""
    if not _command_available(command[0]):
        return False, "not installed"
    try:
        subprocess.check_output(command, stderr=subprocess.STDOUT)
        return True, "available"
    except subprocess.CalledProcessError as exc:
        output = exc.output.decode("utf-8", errors="replace").strip().splitlines()
        detail = output[-1] if output else "not authenticated or unavailable"
        return False, detail


def get_enterprise_auth_status():
    """Return non-secret status for supported enterprise auth brokers."""
    gh_ok, gh_detail = _run_status_command(["gh", "auth", "status"])
    git_ok = _command_available("git")
    return {
        "github_cli": {"available": gh_ok, "detail": gh_detail},
        "git": {"available": git_ok, "detail": "available" if git_ok else "not installed"},
        "github_token_env": {"available": _github_token_present(), "detail": "set" if _github_token_present() else "not set"},
        "private_skill_token_env": {
            "available": bool(os.environ.get("PRIVATE_SKILL_TOKEN", "")),
            "detail": "set" if os.environ.get("PRIVATE_SKILL_TOKEN", "") else "not set",
        },
        "private_catalog": {
            "available": PRIVATE_CATALOG_FILE.exists(),
            "detail": str(PRIVATE_CATALOG_FILE),
        },
    }


def cmd_doctor(skills, args):
    """Show enterprise auth readiness without handling secrets."""
    status = get_enterprise_auth_status()
    print("\n  NeqSim Enterprise Auth Doctor\n")
    print("  NeqSim does not request, inspect, or store credentials.")
    print("  Preferred GitHub Enterprise login: gh auth login --web\n")
    for name, item in status.items():
        marker = "OK" if item["available"] else "--"
        print(f"  [{marker}] {name:<24} {item['detail']}")
    print("\n  Supported catalog auth modes:")
    print("    source: github  auth: github-cli")
    print("    source: git     auth: git-credential-manager")
    print("    source: local   auth: none")
    print("    source: url     PRIVATE_SKILL_TOKEN fallback, if your URL requires it\n")


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

    vscode_path = manifest.get(name, {}).get("vscode_path", "")
    if vscode_path:
        vp = Path(vscode_path)
        if vp.exists():
            import shutil
            shutil.rmtree(str(vp), ignore_errors=True)
            print(f"  [OK] Removed VS Code copy: {vp}")

    generic_export_root = None
    for target, export_path in manifest.get(name, {}).get("exports", {}).items():
        ep = Path(export_path)
        if ep.exists():
            import shutil
            if ep.is_dir():
                shutil.rmtree(str(ep), ignore_errors=True)
            else:
                ep.unlink()
            print(f"  [OK] Removed {target} export: {ep}")
        if target == "generic":
            generic_export_root = ep.parent.parent

    del manifest[name]
    if generic_export_root:
        _write_generic_manifest("skills", generic_export_root, manifest)
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
              neqsim skill install neqsim-example-skill --vscode
              neqsim skill install neqsim-example-skill --target generic
              neqsim skill export neqsim-example-skill --target generic
              neqsim skill installed
              neqsim skill remove neqsim-example-skill
              neqsim skill publish user/neqsim-my-skill
              neqsim skill private-init
              neqsim skill doctor

            Private skills:
              neqsim skill private-init    # create ~/.neqsim/private-skills.yaml
              # Edit the file to add your private skills, then:
              neqsim skill list --private   # verify entries
              neqsim skill install <name>   # install from any catalog

            Preferred private GitHub access uses `gh auth login --web`.
            Internal git sources use your normal Git Credential Manager / SSO login.
            Token environment variables remain optional fallbacks.
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
    p_install.add_argument(
        "--vscode", action="store_true",
        help="Also export the skill to a VS Code workspace .github/skills folder")
    p_install.add_argument(
        "--target", action="append", choices=SUPPORTED_EXPORT_TARGETS,
        help="Also export to a compatibility target (repeatable): vscode or generic")
    p_install.add_argument(
        "--vscode-dir", default=None,
        help="Explicit VS Code skills directory for --vscode export")
    p_install.add_argument(
        "--export-dir", default=None,
        help="Generic export root for --target generic (default: ~/.neqsim/export/generic)")

    sub.add_parser("installed", help="Show installed skills")

    p_export = sub.add_parser("export", help="Export an installed skill to an AI-tool target")
    p_export.add_argument("name", help="Installed skill name")
    p_export.add_argument(
        "--target", action="append", choices=SUPPORTED_EXPORT_TARGETS, required=True,
        help="Export target (repeatable): vscode or generic")
    p_export.add_argument(
        "--vscode-dir", default=None,
        help="Explicit VS Code skills directory for --target vscode")
    p_export.add_argument(
        "--export-dir", default=None,
        help="Generic export root for --target generic (default: ~/.neqsim/export/generic)")

    p_remove = sub.add_parser("remove", help="Remove an installed skill")
    p_remove.add_argument("name", help="Skill name to remove")

    p_publish = sub.add_parser("publish", help="Publish your skill to the catalog")
    p_publish.add_argument("repo", help="GitHub repo (owner/repo) containing the skill")
    p_publish.add_argument("--path", default="SKILL.md", help="Path to SKILL.md in the repo")
    p_publish.add_argument("--yes", "-y", action="store_true", help="Skip confirmation prompt")

    sub.add_parser("private-init", help="Create private catalog template at ~/.neqsim/")
    sub.add_parser("doctor", help="Check enterprise auth broker readiness")

    args = parser.parse_args()
    if not args.command:
        parser.print_help()
        sys.exit(0)

    # private-init doesn't need catalog loaded
    if args.command == "private-init":
        cmd_private_init([], args)
        return
    if args.command == "doctor":
        cmd_doctor([], args)
        return

    private_only = getattr(args, "private", False)
    skills = load_catalog(private_only=private_only)

    commands = {
        "list": cmd_list,
        "search": cmd_search,
        "info": cmd_info,
        "install": cmd_install,
        "export": cmd_export,
        "installed": cmd_installed,
        "remove": cmd_remove,
        "publish": cmd_publish,
        "doctor": cmd_doctor,
    }
    commands[args.command](skills, args)


if __name__ == "__main__":
    main()
