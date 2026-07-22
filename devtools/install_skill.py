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
    neqsim skill private-init      # create private catalog (optionally register
                                   #   a repo + browser SSO in one step, e.g.
                                   #   --repo org/repo --login); prints file path
    neqsim skill add-repo --repo O/R [--login]  # add a private skill repo later

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
import hashlib
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
EXPORT_PROFILE_FILE = EXPORT_DIR / "export-profile.json"
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


def _local_repository_root(repository):
    """Return a local sibling repository path for a GitHub catalog entry if present."""
    if not isinstance(repository, dict):
        return None
    repo = repository.get("repo", "")
    if not repo or "/" not in repo:
        return None
    repo_name = repo.rsplit("/", 1)[1]
    candidates = [
        REPO_ROOT.parent / repo_name,
        REPO_ROOT.parent.parent / repo_name,
        Path.cwd() / repo_name,
    ]
    for candidate in candidates:
        if (candidate / ".git").exists() or (candidate / "README.md").exists():
            return candidate.resolve()
    return None


def _discover_skills_from_local_repository(repository, repo_root):
    """Discover skills from a checked-out sibling repository."""
    catalog_path = repository.get("catalog_path", "community-skills.yaml")
    catalog_candidates = []
    if catalog_path:
        catalog_candidates.append(catalog_path)
    catalog_candidates.extend(["community-skills.yaml", "enterprise-skills.yaml"])

    for candidate in catalog_candidates:
        path = repo_root / candidate
        if not path.is_file():
            continue
        data = _load_catalog_file(path)
        discovered = []
        for skill in (data.get("skills") or []):
            if not isinstance(skill, dict) or not skill.get("name"):
                continue
            normalized = _normalize_discovered_skill(skill, repository)
            local_path = repo_root / normalized.get("path", "SKILL.md")
            normalized["source"] = "local"
            normalized["path"] = str(local_path)
            discovered.append(normalized)
        if discovered:
            return discovered

    pattern = repository.get("skill_path_glob") or repository.get("path_glob") or DEFAULT_SKILL_PATH_GLOB
    discovered = []
    for path in sorted(repo_root.glob(pattern)):
        if not path.is_file() or path.name != "SKILL.md":
            continue
        content = path.read_text(encoding="utf-8", errors="replace")
        metadata = _extract_frontmatter(content)
        name = metadata.get("name")
        if not name:
            folder_name = path.parent.name
            name = folder_name if folder_name.startswith("neqsim-") else f"neqsim-{folder_name}"
        skill = {
            "name": name,
            "version": metadata.get("version", repository.get("version", "")),
            "description": metadata.get("description", f"Local skill from {repo_root}/{path}"),
            "path": str(path),
            "source": "local",
        }
        discovered.append(_normalize_discovered_skill(skill, repository, content=content))
    return discovered


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
    local_root = _local_repository_root(repository)
    if local_root is not None:
        local_skills = _discover_skills_from_local_repository(repository, local_root)
        if local_skills:
            return local_skills
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


def _requested_export_targets(args):
    """Return requested export targets from parsed CLI arguments.

    @param args parsed CLI arguments
    @return list of requested target names
    """
    targets = list(getattr(args, "target", []) or [])
    if getattr(args, "vscode", False) and "vscode" not in targets:
        targets.append("vscode")
    return targets


def _sha256_file(path):
    """Return the SHA-256 digest for a file.

    @param path file path to hash
    @return hexadecimal SHA-256 digest
    """
    digest = hashlib.sha256()
    with Path(path).open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _sha256_tree(root_dir):
    """Return a deterministic SHA-256 digest for all files in a directory.

    @param root_dir directory path to hash recursively
    @return hexadecimal SHA-256 digest for file names and contents
    """
    root = Path(root_dir)
    digest = hashlib.sha256()
    for file_path in sorted(path for path in root.rglob("*") if path.is_file()):
        rel_path = file_path.relative_to(root).as_posix().encode("utf-8")
        digest.update(rel_path)
        digest.update(b"\0")
        with file_path.open("rb") as handle:
            for chunk in iter(lambda: handle.read(1024 * 1024), b""):
                digest.update(chunk)
        digest.update(b"\0")
    return digest.hexdigest()


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


def _vscode_user_dir():
    """Return the VS Code User config directory, auto-detecting the flavor.

    Both stable ("Code") and Insiders ("Code - Insiders") are supported. The
    flavor can be forced with the NEQSIM_VSCODE_FLAVOR env var ("stable" or
    "insiders"); otherwise the first existing User directory is used, preferring
    stable. This prevents a --vscode export from silently landing in the wrong
    (or a non-existent) folder when the user only runs Insiders.

    @return the platform-specific VS Code User directory Path
    """
    if sys.platform.startswith("win"):
        appdata = os.environ.get("APPDATA", "")
        base = Path(appdata) if appdata else Path.home() / "AppData" / "Roaming"
    elif sys.platform == "darwin":
        base = Path.home() / "Library" / "Application Support"
    else:
        base = Path.home() / ".config"

    stable = base / "Code" / "User"
    insiders = base / "Code - Insiders" / "User"

    flavor = os.environ.get("NEQSIM_VSCODE_FLAVOR", "").strip().lower()
    if flavor == "insiders":
        return insiders
    if flavor == "stable":
        return stable

    # Auto-detect: prefer stable, fall back to Insiders when only it exists.
    if stable.exists():
        return stable
    if insiders.exists():
        print("  [??] VS Code stable User dir not found; using Insiders: "
              "{p}".format(p=insiders))
        print("       Set NEQSIM_VSCODE_FLAVOR=stable (or NEQSIM_VSCODE_SKILLS_DIR) "
              "to override.")
        return insiders
    return stable



def resolve_vscode_skills_dir(explicit_dir=None, scope="user"):
    """Resolve the VS Code skills directory for --vscode export.

    Resolution order: explicit dir, NEQSIM_VSCODE_SKILLS_DIR env var, then the
    scope default. 'user' scope targets the personal ~/.copilot/skills folder,
    which VS Code (and the GitHub Copilot CLI) scan for user-global skills in
    every workspace; 'workspace' scope targets <workspace>/.github/skills for
    explicit core workspace exports.

    Note: VS Code does NOT discover skills from its User "prompts" folder. The
    prompts folder is scanned only for agents, instructions, and prompt files;
    skills must live in ~/.copilot/skills (personal) or a workspace
    .github/skills folder, so this resolver targets ~/.copilot/skills for the
    user scope.

    @param explicit_dir an explicit target directory (overrides detection)
    @param scope 'user' (default) for the personal ~/.copilot/skills folder, or 'workspace'
    @return the resolved skills directory Path, or None when none can be found
    """
    if explicit_dir:
        return Path(explicit_dir).expanduser().resolve()
    env_dir = os.environ.get("NEQSIM_VSCODE_SKILLS_DIR", "")
    if env_dir:
        return Path(env_dir).expanduser().resolve()
    if scope != "workspace":
        return Path.home() / ".copilot" / "skills"
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


def _manifest_export_path(info, target):
    """Return the recorded export path for a manifest entry and target.

    @param info installed manifest entry
    @param target export target name
    @return recorded export path, or an empty string when not exported
    """
    return info.get("exports", {}).get(target) or info.get(f"{target}_path", "")


def _path_is_under(path, root):
    """Return true when path is the root itself or inside root.

    @param path path to test
    @param root expected parent/root path
    @return true if path resolves under root, otherwise false
    """
    try:
        resolved_path = Path(path).expanduser().resolve()
        resolved_root = Path(root).expanduser().resolve()
    except Exception:
        return False
    return resolved_path == resolved_root or resolved_root in resolved_path.parents


def _read_json_file(path):
    """Read a JSON file and return an object, or None if unreadable.

    @param path JSON file path
    @return decoded object, or None on read/parse failure
    """
    try:
        return json.loads(Path(path).read_text(encoding="utf-8"))
    except Exception:
        return None


def _load_export_profile(args=None):
    """Load an optional export expectation profile.

    @param args optional parsed CLI arguments with profile
    @return decoded export profile mapping, or None when no profile exists
    """
    profile_path = Path(getattr(args, "profile", "") or EXPORT_PROFILE_FILE)
    if not profile_path.exists():
        return None
    return _read_json_file(profile_path) or {}


def _expected_exports(profile, kind, target):
    """Return expected export names for a kind/target from a profile.

    @param profile decoded export profile mapping or None
    @param kind agents or skills
    @param target export target name
    @return set of expected names, or None when the profile does not constrain it
    """
    if not profile:
        return None
    target_names = profile.get(kind, {}).get(target)
    if target_names is None:
        return None
    return set(target_names)


def _check_generic_manifest_fresh(kind, root_dir, installed_manifest, failures):
    """Append failures when a generic manifest is missing or stale.

    @param kind exported content kind, for example skills
    @param root_dir generic export root directory
    @param installed_manifest installed skills manifest
    @param failures list receiving failure messages
    """
    manifest_path = Path(root_dir) / "manifest.json"
    payload = _read_json_file(manifest_path)
    if payload is None:
        failures.append(f"Generic {kind} manifest is missing or unreadable: {manifest_path}")
        return
    exported = payload.get(kind, {})
    expected = {}
    checked_root = Path(root_dir).expanduser().resolve()
    for name, info in installed_manifest.items():
        export_path = _manifest_export_path(info, "generic")
        if export_path and _path_is_under(export_path, checked_root):
            expected[name] = info
    exported = {
        name: info for name, info in exported.items()
        if isinstance(info, dict)
        and (not info.get("path") or _path_is_under(info.get("path", ""), checked_root))
    }
    missing = sorted(name for name in expected if name not in exported)
    extra = sorted(name for name in exported if name not in expected)
    if missing:
        failures.append(f"Generic {kind} manifest is missing: {', '.join(missing)}")
    if extra:
        failures.append(f"Generic {kind} manifest has stale entries: {', '.join(extra)}")


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
    vscode_dir = resolve_vscode_skills_dir(
        getattr(args, "vscode_dir", None), getattr(args, "vscode_scope", "user"))
    if vscode_dir is None:
        print("  [!!] Could not locate a VS Code workspace for --vscode export.")
        print("       Use --vscode-scope user (default), run inside a workspace,")
        print("       set NEQSIM_VSCODE_SKILLS_DIR, or pass --vscode-dir <path>.")
        return False
    try:
        dest = export_skill_to_vscode(name, source_dir, vscode_dir)
    except Exception as exc:
        print("  [!!] VS Code export failed: {exc}".format(exc=exc))
        return False
    _record_export(manifest, name, "vscode", dest)
    save_manifest(manifest)
    print("  [OK] Exported to VS Code skills ({scope}): {dest}".format(
        scope=getattr(args, "vscode_scope", "user"), dest=dest))
    print("       Reload VS Code (Developer: Reload Window) to pick it up.")
    return True


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
        return False
    save_manifest(manifest)
    print("  [OK] Exported to generic skills: {dest}".format(dest=dest))
    return True


def _export_installed_skill(name, source_dir, args, manifest):
    """Export an installed skill to the requested compatibility target.

    @param name the installed skill name
    @param source_dir the installed skill folder under ~/.neqsim/skills/<name>
    @param args parsed CLI arguments
    @param manifest the installed-skills manifest
    @return None
    """
    targets = _requested_export_targets(args)
    success = True
    for target in targets:
        if target == "vscode":
            success = _export_installed_skill_to_vscode(name, source_dir, args, manifest) and success
        elif target == "generic":
            success = _export_installed_skill_to_generic(name, source_dir, args, manifest) and success
    return success


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
    """Install a skill (or every skill with --all) from the catalog."""
    if getattr(args, "all", False) or args.name == "*":
        _install_all_skills(skills, args)
        return

    name = args.name
    if not name:
        print("\n  Specify a skill name, or use --all to install every skill.")
        print("  Run: neqsim skill list\n")
        sys.exit(1)
    skill = next((s for s in skills if s.get("name") == name), None)
    if not skill:
        print(f"\n  Skill '{name}' not found in catalog.")
        print(f"  Run: neqsim skill list\n")
        sys.exit(1)

    manifest = load_manifest()
    if not _install_skill_record(skill, args, manifest):
        sys.exit(1)


def _install_all_skills(skills, args):
    """Install every catalog skill, continuing past individual failures."""
    source_filter = getattr(args, "source", "all")
    seen = set()
    unique = []
    for skill in skills:
        name = skill.get("name", "")
        if not name or name in seen:
            continue
        if source_filter != "all" and skill.get("_source") != source_filter:
            continue
        seen.add(name)
        unique.append(skill)

    total = len(unique)
    if total == 0:
        print("\n  No skill(s) matched source '{source}'.\n".format(source=source_filter))
        return
    print("\n  Installing {total} {source} skill(s) from the catalog...\n".format(
        total=total, source=source_filter))
    manifest = load_manifest()
    installed = []
    failed = []
    for index, skill in enumerate(unique, start=1):
        name = skill.get("name", "")
        print("  [{index}/{total}] {name}".format(index=index, total=total, name=name))
        if _install_skill_record(skill, args, manifest):
            installed.append(name)
        else:
            failed.append(name)

    print("\n  ==== Install summary ====")
    print("  Installed/OK: {count}".format(count=len(installed)))
    print("  Failed: {count}".format(count=len(failed)))
    if failed:
        print("  Failed skills: {names}".format(names=", ".join(failed)))
        sys.exit(1)


def _install_skill_record(skill, args, manifest):
    """Install a single resolved skill record.

    @param skill the resolved catalog skill mapping to install
    @param args the parsed CLI arguments (force + export target options)
    @param manifest the loaded install manifest (mutated and saved on success)
    @return True on success, False on failure (never calls sys.exit)
    """
    name = skill.get("name")
    if name in manifest and not args.force:
        print(f"\n  Skill '{name}' already installed at {manifest[name]['path']}")
        print(f"  Use --force to reinstall.")
        if _requested_export_targets(args):
            source_dir = Path(manifest[name].get("path", "")).parent
            if not source_dir.exists():
                print(f"  [!!] Installed skill folder is missing: {source_dir}\n")
                return False
            if not _export_installed_skill(name, source_dir, args, manifest):
                return False
        print()
        return True

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
            return False

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
            "installed_at": datetime.now(timezone.utc).isoformat(),
            "content_sha256": _sha256_file(dest_file),
        }
        save_manifest(manifest)

        print(f"  [OK] Installed to: {dest_file}")
        print(f"\n  To use in your AI tool, point it at: {dest_dir}\n")

        if not _export_installed_skill(name, dest_dir, args, manifest):
            return False
        return True

    except Exception as e:
        print(f"  [!!] Download failed: {e}")
        print(f"  You can manually download from: https://github.com/{skill.get('repo', '')}\n")
        return False


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
    if not _export_installed_skill(name, source_dir, args, manifest):
        sys.exit(1)


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
    """Show enterprise auth readiness or export target health without handling secrets."""
    target = getattr(args, "target", None)
    if target:
        _check_export_target(target, args)
        return

    status = get_enterprise_auth_status()
    print("\n  NeqSim Enterprise Auth Doctor\n")
    print("  NeqSim does not request, inspect, or store credentials.")
    print("  Preferred GitHub Enterprise login: gh auth login --web")
    print("  Preferred internal Git login: Git Credential Manager / your configured git SSO broker.\n")
    checks = [
        ("github_cli", "GitHub CLI / browser SSO available"),
        ("git", "git available for Git Credential Manager / SSO"),
        ("private_catalog", "Private skill catalog exists"),
    ]
    for name, label in checks:
        item = status.get(name, {"available": False, "detail": "unknown"})
        marker = "OK" if item["available"] else "--"
        print(f"  [{marker}] {label:<48} {item['detail']}")
    private_skill_token = status.get(
        "private_skill_token_env", {"available": False, "detail": "not set"})
    marker = "OK" if private_skill_token["available"] else "--"
    print("\n  Optional non-interactive fallback status:")
    print(f"  [{marker}] PRIVATE_SKILL_TOKEN set      {private_skill_token['detail']}")
    print("\n  Supported catalog auth modes:")
    print("    source: github  auth: github-cli (browser SSO)")
    print("    source: git     auth: git-credential-manager")
    print("    source: local   auth: none")
    print("    source: url     optional PRIVATE_SKILL_TOKEN fallback for non-interactive endpoints\n")


def _check_export_target(target, args):
    """Check installed skill exports for a target.

    @param target export target name, either vscode or generic
    @param args parsed CLI arguments
    """
    manifest = load_manifest()
    failures = []
    warnings = []
    checked_skills = []
    profile = _load_export_profile(args)
    expected_skills = _expected_exports(profile, "skills", target)

    for name, info in sorted(manifest.items()):
        export_path = _manifest_export_path(info, target)
        if not export_path:
            if expected_skills is None:
                warnings.append(f"Skill is installed but not exported to {target}: {name}")
            elif name in expected_skills:
                failures.append(f"Expected skill is not exported to {target}: {name}")
            continue
        if expected_skills is not None and name not in expected_skills:
            warnings.append(f"Skill is exported to {target} but not listed in profile: {name}")
        checked_skills.append(name)
        path = Path(export_path)
        if not path.exists():
            failures.append(f"Skill export is missing: {name} -> {path}")
            continue
        if not (path / "SKILL.md").exists():
            failures.append(f"Skill export lacks SKILL.md: {name} -> {path}")

    if target == "generic":
        generic_root = resolve_generic_skills_dir(getattr(args, "export_dir", None)).parent
        _check_generic_manifest_fresh("skills", generic_root, manifest, failures)

    print(f"\n  NeqSim skill export doctor ({target})\n")
    print(f"  Checked exported skills: {len(checked_skills)}")
    if checked_skills:
        print(f"  Skills: {', '.join(checked_skills)}")
    if profile:
        print(f"  Export profile: {Path(getattr(args, 'profile', '') or EXPORT_PROFILE_FILE)}")
    for warning in warnings:
        print(f"  WARN: {warning}")
    for failure in failures:
        print(f"  ERROR: {failure}")
    if failures:
        print("\n  Result: FAIL\n")
        sys.exit(1)
    print("\n  Result: PASS\n")


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


def ensure_private_catalog(catalog_file, template):
    """Create the private catalog file from a template if it does not exist.

    @param catalog_file the ``Path`` to the private catalog YAML file
    @param template the template string with a ``{today}`` placeholder
    @return ``True`` if the file was created, ``False`` if it already existed
    """
    if catalog_file.exists():
        return False
    from datetime import date

    catalog_file.parent.mkdir(parents=True, exist_ok=True)
    catalog_file.write_text(template.format(today=date.today().isoformat()), encoding="utf-8")
    return True


def _repo_entry_yaml(entry, key_order):
    """Serialize a repository dict into a 2-space-indented YAML list item.

    @param entry the repository mapping (string/list values only)
    @param key_order preferred key ordering; unknown keys are appended after
    @return the YAML block text (no trailing newline)
    """
    keys = [k for k in key_order if k in entry]
    keys += [k for k in entry if k not in keys]
    lines = []
    for index, key in enumerate(keys):
        value = entry[key]
        if isinstance(value, (list, tuple)):
            # Quote string items so globs with YAML-special leading characters
            # (e.g. "*.agent.md", which YAML would read as an alias) stay valid.
            items = []
            for item in value:
                if isinstance(item, str):
                    items.append('"{}"'.format(item))
                else:
                    items.append(str(item))
            rendered = "[" + ", ".join(items) + "]"
        elif isinstance(value, bool):
            rendered = "true" if value else "false"
        elif isinstance(value, str):
            rendered = '"{}"'.format(value)
        else:
            rendered = str(value)
        if index == 0:
            lines.append("  - {}: {}".format(key, rendered))
        else:
            lines.append("    {}: {}".format(key, rendered))
    return "\n".join(lines)


def _repository_already_registered(text, identifier):
    """Return True only if ``identifier`` is an ACTIVE (non-commented) repo entry.

    Parses the catalog and compares against real ``repo``/``url``/``path`` values
    so commented-out template examples never cause a false "already registered".

    @param text the raw catalog YAML text
    @param identifier the repo/url/path identifier to look for
    @return ``True`` if an active repository entry already uses the identifier
    """
    try:
        data = _parse_catalog_text(text) or {}
        for repo in data.get("repositories") or []:
            if not isinstance(repo, dict):
                continue
            if identifier in (repo.get("repo"), repo.get("url"), repo.get("path")):
                return True
        return False
    except Exception:  # noqa: BLE001 - fall back to a conservative text check
        return identifier in text


def append_repository_entry(catalog_file, entry, key_order):
    """Append a repository entry under the catalog's ``repositories:`` key.

    Idempotent: if the repo/url/path identifier already appears as an ACTIVE
    (non-commented) entry the catalog is left unchanged. Preserves existing
    comments by inserting text rather than re-serializing the whole document.

    @param catalog_file the ``Path`` to the private catalog YAML file
    @param entry the repository mapping to add
    @param key_order preferred key ordering for the serialized entry
    @return ``"appended"`` if added, ``"exists"`` if already present
    """
    text = catalog_file.read_text(encoding="utf-8")
    identifier = entry.get("repo") or entry.get("url") or entry.get("path") or ""
    if identifier and _repository_already_registered(text, identifier):
        return "exists"

    block = _repo_entry_yaml(entry, key_order)
    out_lines = []
    inserted = False
    for line in text.splitlines():
        out_lines.append(line)
        if not inserted and line.rstrip() == "repositories:":
            out_lines.append(block)
            inserted = True
    if not inserted:
        out_lines.append("repositories:")
        out_lines.append(block)
    catalog_file.write_text("\n".join(out_lines) + "\n", encoding="utf-8")
    return "appended"


def run_provider_login(source, auth=None):
    """Launch the provider's browser-based SSO sign-in for a private repo.

    NeqSim never requests, inspects, or stores credentials: it shells out to the
    vendor CLI, which opens a normal browser sign-in. For internal Git it just
    prints guidance because Git Credential Manager / SSO handles login on the
    first clone.

    @param source the repository source ("github", "git", or other)
    @param auth optional auth-broker hint (unused beyond messaging)
    @return ``True`` if a browser sign-in flow was launched, else ``False``
    """
    import shutil as _shutil
    import subprocess

    src = (source or "").lower()
    if src == "github":
        gh = _shutil.which("gh")
        if not gh:
            print("  [!!] GitHub CLI 'gh' not found. Install from https://cli.github.com/,")
            print("       then run:  gh auth login --web")
            return False
        print("  Launching GitHub sign-in in your browser:  gh auth login --web")
        try:
            subprocess.run([gh, "auth", "login", "--web"], check=False)
        except OSError as exc:
            print("  [!!] Could not launch gh: {}".format(exc))
            return False
        return True
    if src == "git":
        print("  Internal Git uses your OS Git Credential Manager / SSO.")
        print("  Sign-in happens in a browser on the first clone - no extra step needed.")
        print("  Verify access with:  git ls-remote <your-repo-url>")
        return False
    print("  No SSO step is needed for source '{}'.".format(source or "local"))
    return False


def _register_repo_from_args(args):
    """Append a repository entry to the private skill catalog from parsed args.

    Shared by ``private-init`` and ``add-repo`` so both stay in sync.

    @param args parsed namespace with repo/url/source/auth/branch/etc.
    @return ``True`` if a ``--repo``/``--url`` was provided and processed
    """
    repo = getattr(args, "repo", None)
    url = getattr(args, "url", None)
    if not (repo or url):
        return False

    source = getattr(args, "source", None) or ("github" if repo else "git")
    auth = getattr(args, "auth", None) or (
        "github-cli" if source == "github" else "git-credential-manager")
    entry = {}
    if repo:
        entry["repo"] = repo
    if url:
        entry["url"] = url
    entry["source"] = source
    if source in ("github", "git"):
        entry["auth"] = auth
    entry["branch"] = getattr(args, "branch", None) or "main"
    entry["catalog_path"] = getattr(args, "catalog_path", None) or ""
    entry["skill_path_glob"] = getattr(args, "skill_path_glob", None) or "skills/**/SKILL.md"
    prefix = getattr(args, "name_prefix", None)
    if prefix:
        entry["name_prefix"] = prefix
    entry["tags"] = ["enterprise", "private"]
    order = ["repo", "url", "source", "auth", "branch", "catalog_path",
             "skill_path_glob", "name_prefix", "tags"]
    result = append_repository_entry(PRIVATE_CATALOG_FILE, entry, order)
    if result == "appended":
        print("  [OK] Registered private repository: {}".format(repo or url))
    else:
        print("  Repository already registered: {}".format(repo or url))

    if getattr(args, "login", False):
        print("\n  Setting up sign-in (SSO)...")
        run_provider_login(source, auth)
    return True


def _print_private_catalog_footer():
    """Print the private catalog path and the verify/install hints."""
    print("\n  Private catalog file (edit to add or adjust entries):")
    print("    {}".format(PRIVATE_CATALOG_FILE))
    print("  Verify:  neqsim skill list --private")
    print("  Install: neqsim skill install <name> --target vscode\n")


def cmd_add_repo(skills, args):
    """Register a private skill repository in ~/.neqsim/private-skills.yaml.

    Creates the catalog from the template if it does not exist yet, appends the
    repository entry, optionally launches browser SSO, and prints the file path.

    @param skills unused (kept for the command dispatch signature)
    @param args parsed namespace; ``--repo`` or ``--url`` is required
    @return ``None``
    """
    if not (getattr(args, "repo", None) or getattr(args, "url", None)):
        print("\n  [!!] Provide a repository to add: --repo OWNER/REPO or --url GIT_URL")
        print("  Example: neqsim skill add-repo --repo my-org/neqsim-enterprise-skills --login\n")
        sys.exit(1)
    created = ensure_private_catalog(PRIVATE_CATALOG_FILE, PRIVATE_CATALOG_TEMPLATE)
    if created:
        print("\n  [OK] Created private catalog: {}".format(PRIVATE_CATALOG_FILE))
    _register_repo_from_args(args)
    _print_private_catalog_footer()


def cmd_private_init(skills, args):
    """Create the private skill catalog template at ~/.neqsim/private-skills.yaml.

    Optionally registers a private repository (``--repo`` / ``--url``) directly
    into the catalog and launches browser SSO sign-in (``--login``) in one step,
    then always prints the catalog file location so it can be edited afterwards.

    @param skills unused (kept for the command dispatch signature)
    @param args parsed argparse namespace with optional repo/url/login options
    @return ``None``
    """
    created = ensure_private_catalog(PRIVATE_CATALOG_FILE, PRIVATE_CATALOG_TEMPLATE)
    if created:
        print("\n  [OK] Created private catalog: {}".format(PRIVATE_CATALOG_FILE))
    else:
        print("\n  Private catalog already exists: {}".format(PRIVATE_CATALOG_FILE))

    _register_repo_from_args(args)
    _print_private_catalog_footer()



# ── Main ───────────────────────────────────────────────────────────────

def _add_repo_options(parser, kind):
    """Add the shared private-repo registration options to an argparse parser.

    Used by both the ``private-init`` and ``add-repo`` subcommands so their
    options stay identical.

    @param parser the argparse subparser to extend
    @param kind ``"skill"`` or ``"agent"`` (selects the path-glob option name)
    @return ``None``
    """
    parser.add_argument("--repo", help="Private GitHub repo (owner/repo) to register")
    parser.add_argument("--url", help="Internal Git repo URL to register")
    parser.add_argument(
        "--source", choices=["github", "git", "local"],
        help="Repository source (default: github when --repo, git when --url)")
    parser.add_argument(
        "--auth", choices=["github-cli", "git-credential-manager"],
        help="Auth broker (default matches the source)")
    parser.add_argument("--branch", default=None, help="Branch to use (default: main)")
    parser.add_argument(
        "--catalog-path", dest="catalog_path", default=None,
        help="Path to the catalog file in the repo, or empty to scan")
    if kind == "agent":
        parser.add_argument(
            "--agent-path-glob", dest="agent_path_glob", default=None,
            help="Glob for agent discovery (default: agents/**/AGENT.md + *.agent.md)")
    else:
        parser.add_argument(
            "--skill-path-glob", dest="skill_path_glob", default=None,
            help="Glob for SKILL.md discovery (default: skills/**/SKILL.md)")
    parser.add_argument(
        "--name-prefix", dest="name_prefix", default=None,
        help="Optional name prefix to avoid public/private clashes (e.g. enterprise-)")
    parser.add_argument(
        "--login", action="store_true",
        help="Launch browser SSO sign-in (gh auth login --web) after registering")


def main():
    examples = "\n".join([
        "Examples:",
        "  neqsim skill list",
        "  neqsim skill list --private",
        "  neqsim skill search \"flow assurance\"",
        "  neqsim skill install neqsim-example-skill",
        "  neqsim skill install neqsim-example-skill --target vscode",
        "  neqsim skill install neqsim-example-skill --target generic",
        "  neqsim skill install --all --target vscode",
        "  neqsim skill install --all --source community --target vscode",
        "  neqsim skill export neqsim-example-skill --target vscode",
        "  neqsim skill export neqsim-example-skill --target generic",
        "  neqsim skill installed",
        "  neqsim skill remove neqsim-example-skill",
        "  neqsim skill publish user/neqsim-my-skill",
        "  neqsim skill private-init",
        "  neqsim skill doctor",
        "  neqsim skill doctor --target vscode",
        "  neqsim skill doctor --target generic",
        "",
        "Private skills:",
        "  neqsim skill private-init    # create ~/.neqsim/private-skills.yaml",
        "  neqsim skill private-init --repo my-org/neqsim-enterprise-skills --login  # register a repo + SSO",
        "  neqsim skill add-repo --repo my-org/neqsim-enterprise-skills --login      # add a repo later",
        "  # ...or edit the file by hand to add private skills, then:",
        "  neqsim skill list --private   # verify entries",
        "  neqsim skill install <name> --target vscode   # install and export privately for VS Code",
        "",
        "Preferred private GitHub access uses `gh auth login --web`.",
        "Internal git sources use your normal Git Credential Manager / SSO login.",
        "Token environment variables remain optional non-interactive fallbacks.",
    ])
    parser = argparse.ArgumentParser(
        description="Install community and private skills from NeqSim skill catalogs.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=examples,
    )
    sub = parser.add_subparsers(dest="command")

    p_list = sub.add_parser("list", help="List all skills in the catalog")
    p_list.add_argument("--private", action="store_true", help="List private catalog only")

    p_search = sub.add_parser("search", help="Search skills by keyword")
    p_search.add_argument("query", help="Search term")

    p_info = sub.add_parser("info", help="Show details for a skill")
    p_info.add_argument("name", help="Skill name")

    p_install = sub.add_parser("install", help="Install a skill (or every skill with --all)")
    p_install.add_argument(
        "name", nargs="?",
        help="Skill name from catalog (omit when using --all)")
    p_install.add_argument(
        "--all", action="store_true", help="Install every skill in the catalog")
    p_install.add_argument(
        "--source", choices=["all", "community", "private"], default="all",
        help="When used with --all, install all sources, community only, or private/enterprise only")
    p_install.add_argument("--force", action="store_true", help="Reinstall if exists")
    p_install.add_argument(
        "--vscode", action="store_true",
        help="Also export the skill to the personal ~/.copilot/skills folder (VS Code + Copilot CLI)")
    p_install.add_argument(
        "--target", action="append", choices=SUPPORTED_EXPORT_TARGETS,
        help="Also export to a compatibility target (repeatable): vscode or generic")
    p_install.add_argument(
        "--vscode-scope", choices=["user", "workspace"], default="user",
        help="Skill export scope: personal ~/.copilot/skills (default) or explicit workspace .github/skills")
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
        "--vscode-scope", choices=["user", "workspace"], default="user",
        help="Skill export scope: user prompts skills folder (default) or explicit workspace .github/skills")
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

    p_priv = sub.add_parser(
        "private-init",
        help="Create/extend private catalog at ~/.neqsim/ (optionally register a repo + SSO)")
    _add_repo_options(p_priv, "skill")
    p_addrepo = sub.add_parser(
        "add-repo",
        help="Add a private skill repository to ~/.neqsim/private-skills.yaml (+ optional SSO)")
    _add_repo_options(p_addrepo, "skill")
    p_doctor = sub.add_parser(
        "doctor", help="Check enterprise auth broker readiness or export target health")
    p_doctor.add_argument(
        "--target", choices=SUPPORTED_EXPORT_TARGETS,
        help="Check installed skill exports for this target")
    p_doctor.add_argument(
        "--export-dir", default=None,
        help="Generic export root for --target generic (default: ~/.neqsim/export/generic)")
    p_doctor.add_argument(
        "--profile", default=None,
        help="Optional export profile JSON (default: ~/.neqsim/export/export-profile.json if present)")

    args = parser.parse_args()
    if not args.command:
        parser.print_help()
        sys.exit(0)

    # private-init / add-repo don't need the catalog loaded
    if args.command == "private-init":
        cmd_private_init([], args)
        return
    if args.command == "add-repo":
        cmd_add_repo([], args)
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
