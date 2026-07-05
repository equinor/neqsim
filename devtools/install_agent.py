#!/usr/bin/env python3
"""Install community and private agents from NeqSim agent catalogs.

Usage:
    neqsim agent list
    neqsim agent list --private
    neqsim agent search <query>
    neqsim agent install <name>
    neqsim agent install --all
    neqsim agent installed
    neqsim agent remove <name>
    neqsim agent info <name>
    neqsim agent validate <name-or-path>
    neqsim agent schema
    neqsim agent private-init

Agents are installed to ~/.neqsim/agents/<name>/ and are never executed during
installation. An agent package may be a single .agent.md file, a folder with
AGENT.md, or a folder with AGENT.md plus agent.yaml and supporting resources.
Private GitHub repositories can be read through an authenticated gh CLI browser
SSO session. Internal git repositories can be read through Git Credential
Manager or another configured git credential broker.
"""

import argparse
from datetime import datetime, timezone
import fnmatch
import json
import os
import re
import shutil
import sys
import textwrap
import urllib.error
import urllib.request
from pathlib import Path

import install_skill

try:
    import yaml
except ImportError:
    yaml = None


REPO_ROOT = Path(__file__).resolve().parent.parent
CATALOG_FILE = REPO_ROOT / "community-agents.yaml"
PRIVATE_CATALOG_FILE = Path.home() / ".neqsim" / "private-agents.yaml"
INSTALL_DIR = Path.home() / ".neqsim" / "agents"
MANIFEST_FILE = INSTALL_DIR / "installed.json"
CORE_SKILLS_DIR = REPO_ROOT / ".github" / "skills"
INSTALLED_SKILLS_DIR = Path.home() / ".neqsim" / "skills"
EXPORT_DIR = Path.home() / ".neqsim" / "export"

DEFAULT_AGENT_PATH_GLOBS = ["agents/**/*.agent.md",
                            "agents/**/AGENT.md", "**/*.agent.md"]
AGENT_MANIFEST_SCHEMA_VERSION = "1.0"
SEMVER_RE = re.compile(r"^\d+\.\d+\.\d+([-.+][0-9A-Za-z.-]+)?$")

ALLOWED_MANIFEST_FIELDS = set([
    "agent_manifest_schema_version",
    "name",
    "display_name",
    "description",
    "version",
    "argument-hint",
    "argument_hint",
    "required_skills",
    "skills",
    "supported_domains",
    "coordinated_agents",
    "referenced_skills",
    "reviewed_skill_outputs",
    "inputs",
    "outputs",
    "requires_mcp_tools",
    "human_review_required",
    "trust_level",
    "agent_type",
    "status",
    "tags",
    "author",
    "license",
    "source",
    "repo",
    "path",
    "folder",
    "agent_yaml_path",
])
LIST_MANIFEST_FIELDS = set([
    "required_skills",
    "skills",
    "supported_domains",
    "coordinated_agents",
    "referenced_skills",
    "reviewed_skill_outputs",
    "inputs",
    "outputs",
    "requires_mcp_tools",
    "tags",
])
TRUST_LEVELS = set(["core", "community", "private", "experimental"])

TAG_PATTERNS = [
    "thermodynamic", "process", "pvt", "flow assurance", "hydrate",
    "pipeline", "compressor", "separator", "distillation", "heat exchanger",
    "ccs", "hydrogen", "subsea", "well", "economics", "safety",
    "electrolyte", "reaction", "power", "emissions", "corrosion",
    "document", "p&id", "pid", "optimization", "control", "report",
]

PRIVATE_CATALOG_TEMPLATE = """\
# Private / Internal Agent Catalog
#
# This file lists agents that are private to your organisation.
# It is stored at ~/.neqsim/private-agents.yaml and should NEVER
# be committed to a public repository.
#
# Agents can be sourced from:
#   - Local file paths or folders (source: local)
#   - Network shares (source: local, with UNC path)
#   - Private GitHub repos (source: github, auth: github-cli)
#   - Internal Git servers (source: git, auth: git-credential-manager)
#   - Internal raw URLs (source: url, optional PRIVATE_AGENT_TOKEN fallback)
#
# Install with: neqsim agent install <name>

catalog_version: "1.0"
last_updated: "{today}"
organisation: "your-company"

repositories:
    # -- Private GitHub repository discovery --
    # Preferred: run `gh auth login --web` once and let GitHub/SSO handle login.
    # NeqSim does not request, inspect, or store credentials.
    # Use catalog_path: "" when the repo has no community-agents.yaml and should
    # be scanned for AGENT.md / *.agent.md files directly.
    # - repo: "your-org/neqsim-enterprise-agents"
    #   source: github
    #   auth: github-cli
    #   branch: "main"
    #   catalog_path: ""
    #   agent_path_glob: ["agents/**/AGENT.md", "agents/**/*.agent.md"]
    #   name_prefix: "enterprise-"  # optional, avoids public/private name clashes
    #   tags: [enterprise, private]

    # -- Internal Git repository discovery --
    # Preferred when your enterprise uses Git Credential Manager / SSO for HTTPS.
    # - source: git
    #   auth: git-credential-manager
    #   url: "https://git.internal.company.com/neqsim/enterprise-agents.git"
    #   branch: "main"
    #   catalog_path: "enterprise-agents.yaml"
    #   agent_path_glob: ["agents/**/AGENT.md", "agents/**/*.agent.md"]
    #   name_prefix: "enterprise-"
    #   tags: [enterprise, private]

agents:
  # -- Local folder package --
  # - name: company-tie-in-screening-agent
  #   description: "Internal tie-in screening workflow using company standards"
  #   author: "your-team"
  #   source: local
    #   path: "C:/Users/you/company-agents/company-tie-in-screening-agent"
  #   required_skills: [neqsim-flow-assurance, neqsim-standards-lookup]
    #   supported_domains: [process, flow-assurance]
    #   inputs: [feed_composition, operating_conditions]
    #   outputs: [results_json, report]
    #   requires_mcp_tools: []
    #   human_review_required: true
    #   trust_level: private
  #   tags: [tie-in, screening, internal]

  # -- Single private GitHub agent file --
  # - name: company-process-agent
  #   description: "Internal process-modeling assistant"
  #   author: "your-team"
  #   source: github
    #   auth: github-cli
  #   repo: "your-org/neqsim-company-agents"
  #   path: "agents/company-process-agent/AGENT.md"
  #   folder: "agents/company-process-agent"
  #   required_skills: [neqsim-api-patterns, neqsim-platform-modeling]

    # -- Internal Git agent package using Git Credential Manager / SSO --
    # - name: company-git-agent
    #   description: "Internal agent fetched through git credentials"
    #   author: "your-team"
    #   source: git
    #   auth: git-credential-manager
    #   url: "https://git.internal.company.com/neqsim/enterprise-agents.git"
    #   branch: "main"
    #   path: "agents/company-git-agent/AGENT.md"
    #   folder: "agents/company-git-agent"
    #   required_skills: [neqsim-api-patterns]

  # -- Direct URL --
  # - name: company-review-agent
  #   description: "Internal review workflow"
  #   author: "your-team"
  #   source: url
  #   url: "https://git.internal.company.com/raw/repo/main/agents/review/AGENT.md"
  #   required_skills: [neqsim-professional-reporting]
"""


def load_catalog(private_only=False):
    """Load agent catalogs. Merges community and private by default."""
    agents = []

    if not private_only and CATALOG_FILE.exists():
        data = _load_catalog_file(CATALOG_FILE)
        _add_catalog_entries(agents, data, "community")

    if PRIVATE_CATALOG_FILE.exists():
        data = _load_catalog_file(PRIVATE_CATALOG_FILE)
        _add_catalog_entries(agents, data, "private")

    if not agents:
        if private_only:
            if not PRIVATE_CATALOG_FILE.exists():
                print("  [!!] Private catalog not found: {path}".format(
                    path=PRIVATE_CATALOG_FILE
                ))
                print("  Run: neqsim agent private-init")
            else:
                print("\n  Private catalog has no agents yet.")
                print("  Edit: {path}".format(path=PRIVATE_CATALOG_FILE))
            sys.exit(1)
        print("  [!!] No agent catalogs found.")
        sys.exit(1)

    return agents


def _load_catalog_file(path):
    """Load a YAML catalog file from disk."""
    return _parse_catalog_text(path.read_text(encoding="utf-8"))


def _parse_catalog_text(text):
    """Parse an agent catalog YAML document."""
    if yaml is not None:
        return yaml.safe_load(text) or {}
    return _parse_catalog_fallback(text)


def _parse_catalog_fallback(text):
    """Fallback parser for the simple agent catalog format."""
    data = {"agents": [], "repositories": []}
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
                key, value = remainder.split(":", 1)
                current[key.strip()] = install_skill._parse_scalar_value(
                    value.strip())
        elif current is not None and ":" in line:
            key, value = line.split(":", 1)
            current[key.strip()] = install_skill._parse_scalar_value(
                value.strip())
    if current and section in data:
        data[section].append(current)
    return data


def _add_catalog_entries(agents, data, source):
    """Add direct and discovered catalog entries to the agent list."""
    for agent in (data.get("agents") or []):
        _append_agent(agents, agent, source)

    for repository in (data.get("repositories") or []):
        try:
            if isinstance(repository, dict) and repository.get("source") == "git":
                discovered = _discover_git_repository_agents(repository)
            else:
                discovered = _discover_github_repository_agents(repository)
            for agent in discovered:
                _append_agent(agents, agent, source)
        except Exception as exc:
            repo = repository.get("repo", "?") if isinstance(
                repository, dict) else "?"
            print("  [!!] Could not discover agents from {repo}: {exc}".format(
                repo=repo, exc=exc
            ), file=sys.stderr)


def _append_agent(agents, agent, source):
    """Append an agent entry if it is valid and not already present."""
    if not isinstance(agent, dict):
        return
    name = agent.get("name")
    if not name:
        return
    if any(existing.get("name") == name for existing in agents):
        return
    normalized = dict(agent)
    normalized["_source"] = source
    normalized["required_skills"] = _normalize_list(
        normalized.get("required_skills"))
    agents.append(normalized)


def _normalize_list(value):
    """Normalize a YAML scalar or list to a list of strings."""
    if value is None:
        return []
    if isinstance(value, list):
        return [str(item).strip() for item in value if str(item).strip()]
    if isinstance(value, str):
        stripped = value.strip()
        if stripped.startswith("[") and stripped.endswith("]"):
            stripped = stripped[1:-1]
        return [item.strip().strip('"').strip("'") for item in stripped.split(",")
                if item.strip()]
    return [str(value).strip()]


def _coerce_bool(value):
    """Return a boolean for simple YAML-style truth values, or None."""
    if isinstance(value, bool):
        return value
    if isinstance(value, str):
        lowered = value.strip().lower()
        if lowered in ("true", "yes", "1"):
            return True
        if lowered in ("false", "no", "0"):
            return False
    return None


def _normalize_manifest_metadata(metadata):
    """Normalize known manifest fields while preserving unknown fields."""
    normalized = dict(metadata)
    for field in LIST_MANIFEST_FIELDS:
        if field in normalized:
            value = normalized[field]
            if isinstance(value, list):
                normalized[field] = value
            else:
                normalized[field] = _normalize_list(value)
    if "human_review_required" in normalized:
        coerced = _coerce_bool(normalized["human_review_required"])
        if coerced is not None:
            normalized["human_review_required"] = coerced
    return normalized


def _dedupe_strings(values):
    """Return non-empty strings in first-seen order."""
    deduped = []
    for value in values:
        text = str(value).strip()
        if text and text not in deduped:
            deduped.append(text)
    return deduped


def _catalog_manifest_metadata(agent):
    """Return schema-related metadata from a catalog entry."""
    return {key: agent[key] for key in ALLOWED_MANIFEST_FIELDS if key in agent}


def _merge_manifest_metadata(package_metadata, catalog_metadata):
    """Merge catalog compatibility metadata with package metadata.

    Package metadata wins for scalar fields because it travels with the installed
    agent. List fields are combined so catalogs can supplement simple .agent.md
    files with required skills, supported domains, inputs, and outputs.
    """
    catalog_subset = _catalog_manifest_metadata(catalog_metadata or {})
    merged = dict(catalog_subset)
    merged.update(package_metadata or {})
    for field in LIST_MANIFEST_FIELDS:
        combined = []
        combined.extend(_normalize_list(catalog_subset.get(field)))
        combined.extend(_normalize_list((package_metadata or {}).get(field)))
        if combined:
            merged[field] = _dedupe_strings(combined)
    return _validate_manifest_metadata(merged, check_unknown_fields=False)


def _validate_manifest_metadata(metadata, check_unknown_fields=False):
    """Validate agent manifest metadata against the NeqSim agent schema."""
    errors = []
    warnings = []
    normalized = _normalize_manifest_metadata(metadata)

    schema_version = normalized.get("agent_manifest_schema_version")
    if schema_version and str(schema_version) != AGENT_MANIFEST_SCHEMA_VERSION:
        warnings.append(
            "agent_manifest_schema_version '{version}' is newer/unknown; current schema is {schema}".format(
                version=schema_version, schema=AGENT_MANIFEST_SCHEMA_VERSION
            )
        )

    version = normalized.get("version")
    if version and not SEMVER_RE.match(str(version)):
        warnings.append("version should use semver, for example '1.0.0'")

    if "human_review_required" in normalized and not isinstance(
            normalized["human_review_required"], bool):
        errors.append("human_review_required must be true or false")

    trust_level = normalized.get("trust_level")
    if trust_level and str(trust_level) not in TRUST_LEVELS:
        errors.append("trust_level must be one of: {levels}".format(
            levels=", ".join(sorted(TRUST_LEVELS))
        ))

    for field in LIST_MANIFEST_FIELDS:
        if field in normalized and not isinstance(normalized[field], list):
            errors.append("{field} must be a list".format(field=field))

    for collection_name in ("inputs", "outputs"):
        for index, entry in enumerate(normalized.get(collection_name, []) or []):
            if isinstance(entry, dict):
                if not entry.get("name"):
                    warnings.append("{field}[{index}] should include a name".format(
                        field=collection_name, index=index
                    ))
                if not entry.get("type"):
                    warnings.append("{field}[{index}] should include a type".format(
                        field=collection_name, index=index
                    ))
            elif not isinstance(entry, str):
                errors.append("{field}[{index}] must be a string or mapping".format(
                    field=collection_name, index=index
                ))

    if check_unknown_fields:
        for field in sorted(normalized):
            if field not in ALLOWED_MANIFEST_FIELDS and not field.startswith("_"):
                warnings.append(
                    "unknown agent manifest field: {field}".format(field=field))

    return errors, warnings, normalized


def _manifest_schema_text():
    """Return a human-readable agent manifest schema summary."""
    return textwrap.dedent("""\
        NeqSim agent manifest schema {schema}

        Required fields:
          name: Stable agent name or display name
          description: One-sentence summary of the workflow

        Recommended agent.yaml fields:
          agent_manifest_schema_version: "{schema}"
          version: "1.0.0"
          required_skills: [neqsim-api-patterns]
          supported_domains: [process, flow-assurance]
          inputs: [feed_composition, operating_conditions]
          outputs: [results_json, report]
          requires_mcp_tools: [runProcess, runFlash]
          human_review_required: true
          trust_level: community   # core | community | private | experimental

        Install validates this metadata but never executes agent code.
    """).format(schema=AGENT_MANIFEST_SCHEMA_VERSION)


def _format_list(value):
    """Format a scalar or list for CLI display."""
    items = _normalize_list(value)
    return ", ".join(items) if items else "-"


def _agent_id_from_path(path):
    """Return a stable install id for an agent path."""
    path_obj = Path(path)
    name = path_obj.name
    if name == "AGENT.md":
        return path_obj.parent.name.replace(".", "-")
    if name.endswith(".agent.md"):
        return name[:-len(".agent.md")].replace(".", "-")
    return path_obj.stem.replace(".", "-")


_LOADED_SKILLS_INLINE_RE = re.compile(
    r"(?im)^\s*(?:[-*]\s*)?(?:\*\*)?Loaded skills(?:\*\*)?\s*[:\-]\s*(.+)$")
_LOADED_SKILLS_BLOCK_HEADER_RE = re.compile(
    r"^\s{0,3}#{1,6}\s+(?:\*\*)?Loaded skills(?:\*\*)?\s*$",
    re.IGNORECASE)
_MARKDOWN_HEADING_RE = re.compile(r"^\s{0,3}#{1,6}\s+\S")


def _extract_loaded_skill_block_entries(content):
    """Return skill entries listed under markdown Loaded skills headings."""
    entries = []
    lines = content.splitlines()
    for line_number, line in enumerate(lines):
        if not _LOADED_SKILLS_BLOCK_HEADER_RE.match(line):
            continue
        for next_line in lines[line_number + 1:]:
            if _MARKDOWN_HEADING_RE.match(next_line):
                break
            stripped = next_line.strip()
            if not stripped and entries:
                break
            if stripped.startswith(("-", "*")):
                entries.extend(_normalize_list(stripped[1:].strip()))
    return entries


def _clean_required_skill_name(skill):
    """Normalize a required skill name from inline or markdown-list text."""
    cleaned = skill.strip().strip("`").lstrip("@").rstrip(".")
    if not cleaned:
        return ""
    return re.split(r"\s+", cleaned, maxsplit=1)[0].strip("`").rstrip(".")


def _extract_required_skills(content, metadata=None):
    """Extract required skills from metadata and Loaded skills declarations."""
    required = []
    if metadata:
        required.extend(_normalize_list(metadata.get("required_skills")))
        required.extend(_normalize_list(metadata.get("skills")))
    for match in _LOADED_SKILLS_INLINE_RE.finditer(content):
        required.extend(_normalize_list(match.group(1)))
    required.extend(_extract_loaded_skill_block_entries(content))

    deduped = []
    for skill in required:
        cleaned = _clean_required_skill_name(skill)
        if cleaned and cleaned not in deduped:
            deduped.append(cleaned)
    return deduped


def _infer_tags(content, path=""):
    """Infer searchable tags from agent content and path."""
    tags = []
    searchable = "{path}\n{content}".format(path=path, content=content).lower()
    for tag in TAG_PATTERNS:
        if tag in searchable:
            tags.append(tag.replace(" ", "-"))
    return tags[:6]


def _agent_globs(repository):
    """Return path glob patterns for repository scanning."""
    patterns = repository.get("agent_path_glob") or repository.get("path_glob")
    if not patterns:
        return DEFAULT_AGENT_PATH_GLOBS
    return _normalize_list(patterns)


def _normalize_discovered_agent(agent, repository, default_branch=None, content=""):
    """Normalize an agent discovered from a repository catalog or scan."""
    normalized = dict(agent)
    name_prefix = repository.get("name_prefix", "")
    if name_prefix and normalized.get("name") and not str(normalized["name"]).startswith(name_prefix):
        normalized["name"] = "{prefix}{name}".format(
            prefix=name_prefix, name=normalized["name"])
    repo = normalized.get("repo") or repository.get("repo", "")
    path = normalized.get("path") or repository.get("default_path", "AGENT.md")
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
        normalized["author"] = repository.get(
            "author") or (repo.split("/", 1)[0] if repo else "private")
    if "display_name" not in normalized and normalized.get("name"):
        normalized["display_name"] = normalized.get(
            "display_name", normalized.get("name"))
    required = _extract_required_skills(content, normalized) if content else normalized.get(
        "required_skills", []
    )
    normalized["required_skills"] = _normalize_list(required)
    inferred_tags = _infer_tags(content, path) if content else []
    normalized["tags"] = install_skill._merge_tags(
        normalized.get("tags"), repository.get("tags"), inferred_tags
    )
    normalized["_discovered_from"] = repository.get("repo", repo)
    return normalized


def _discover_agents_from_remote_catalog(repository, branch):
    """Discover agents from a remote community-agents.yaml file."""
    repo = repository.get("repo", "")
    catalog_path = repository.get("catalog_path", "community-agents.yaml")
    if not catalog_path:
        return []
    text = install_skill._fetch_github_text(
        repo, catalog_path, branch=branch, auth=install_skill._github_entry_auth(repository))
    data = _parse_catalog_text(text)
    discovered = []
    for agent in (data.get("agents") or []):
        if not isinstance(agent, dict) or not agent.get("name"):
            continue
        discovered.append(_normalize_discovered_agent(
            agent, repository, branch))
    return discovered


def _parse_remote_agent_yaml(text):
    """Parse remote agent.yaml text into a metadata mapping."""
    if yaml is not None:
        data = yaml.safe_load(text) or {}
    else:
        data = _parse_flat_yaml(text)
    return data if isinstance(data, dict) else {}


def _sibling_agent_yaml_path(path, paths):
    """Return the sibling agent.yaml path for an AGENT.md package, if present."""
    path_obj = Path(path)
    yaml_path = str(path_obj.parent / "agent.yaml").replace("\\", "/")
    return yaml_path if yaml_path in paths else ""


def _agent_folder_path(path, paths, yaml_path):
    """Return the package folder for an AGENT.md entry, if it has one."""
    path_obj = Path(path)
    if path_obj.name != "AGENT.md":
        return ""
    folder = str(path_obj.parent).replace("\\", "/")
    if not folder or folder == ".":
        return ""
    prefix = folder.rstrip("/") + "/"
    has_package_files = yaml_path or any(
        other_path != path and other_path.startswith(prefix) for other_path in paths)
    return folder if has_package_files else ""


def _fetch_remote_agent_yaml(repo, yaml_path, branch, auth=None):
    """Fetch and parse a remote agent.yaml file."""
    if not yaml_path:
        return {}
    text = install_skill._fetch_github_text(repo, yaml_path, branch=branch, auth=auth)
    return _parse_remote_agent_yaml(text)


def _discover_agents_by_scanning_repo(repository, branch):
    """Discover agents by scanning an online GitHub repo for agent files."""
    repo = repository.get("repo", "")
    auth = install_skill._github_entry_auth(repository)
    used_branch, paths = install_skill._list_github_tree_paths(
        repo, branch=branch, auth=auth)
    patterns = _agent_globs(repository)
    discovered = []
    for path in sorted(paths):
        if not any(fnmatch.fnmatch(path, pattern) for pattern in patterns):
            continue
        if not (path.endswith(".agent.md") or path.endswith("AGENT.md")):
            continue
        content = install_skill._fetch_github_text(
            repo, path, branch=used_branch, auth=auth)
        frontmatter = install_skill._extract_frontmatter(content)
        yaml_path = _sibling_agent_yaml_path(path, paths)
        agent_yaml = _fetch_remote_agent_yaml(repo, yaml_path, used_branch, auth=auth)
        metadata = dict(frontmatter)
        metadata.update(agent_yaml)
        agent_id = frontmatter.get("agent_id") or frontmatter.get(
            "id") or agent_yaml.get("name") or _agent_id_from_path(path)
        display_name = metadata.get("display_name") or frontmatter.get(
            "name") or agent_yaml.get("display_name") or agent_yaml.get("name") or agent_id
        agent = dict(metadata)
        agent.update({
            "name": agent_id,
            "display_name": display_name,
            "version": metadata.get("version", repository.get("version", "")),
            "description": metadata.get(
                "description", "Community agent from {repo}/{path}".format(
                    repo=repo, path=path)
            ),
            "path": path,
            "required_skills": _extract_required_skills(content, metadata),
        })
        folder = _agent_folder_path(path, paths, yaml_path)
        if folder:
            agent["folder"] = folder
        if yaml_path:
            agent["agent_yaml_path"] = yaml_path
        discovered.append(_normalize_discovered_agent(
            agent, repository, used_branch, content))
    return discovered


def _discover_github_repository_agents(repository):
    """Discover every agent available from an online GitHub repository."""
    if not isinstance(repository, dict):
        return []
    if repository.get("source", "github") != "github":
        return []
    repo = repository.get("repo", "")
    if not repo:
        return []
    branch = repository.get(
        "branch") or install_skill._get_default_github_branch(
            repo, auth=install_skill._github_entry_auth(repository))
    try:
        catalog_agents = _discover_agents_from_remote_catalog(
            repository, branch)
        if catalog_agents:
            return catalog_agents
    except Exception:
        pass
    return _discover_agents_by_scanning_repo(repository, branch)


def _discover_agents_from_git_catalog(repository, repo_dir, branch):
    """Discover agents from an agent catalog in a cloned git repository."""
    catalog_path = repository.get("catalog_path", "community-agents.yaml")
    if not catalog_path:
        return []
    catalog_file = repo_dir / catalog_path
    if not catalog_file.exists():
        return []
    data = _parse_catalog_text(catalog_file.read_text(encoding="utf-8"))
    discovered = []
    for agent in (data.get("agents") or []):
        if isinstance(agent, dict) and agent.get("name"):
            discovered.append(_normalize_discovered_agent(agent, repository, branch))
    return discovered


def _discover_agents_by_scanning_git_repo(repository, repo_dir, branch):
    """Discover agents by scanning a cloned git repository."""
    paths = [path.relative_to(repo_dir).as_posix() for path in repo_dir.rglob("*") if path.is_file()]
    patterns = _agent_globs(repository)
    discovered = []
    for path in sorted(paths):
        if not any(fnmatch.fnmatch(path, pattern) for pattern in patterns):
            continue
        if not (path.endswith(".agent.md") or path.endswith("AGENT.md")):
            continue
        file_path = repo_dir / path
        content = file_path.read_text(encoding="utf-8", errors="replace")
        frontmatter = install_skill._extract_frontmatter(content)
        yaml_path = _sibling_agent_yaml_path(path, paths)
        agent_yaml = {}
        if yaml_path:
            agent_yaml = _parse_remote_agent_yaml(
                (repo_dir / yaml_path).read_text(encoding="utf-8", errors="replace"))
        metadata = dict(frontmatter)
        metadata.update(agent_yaml)
        agent_id = frontmatter.get("agent_id") or frontmatter.get(
            "id") or agent_yaml.get("name") or _agent_id_from_path(path)
        display_name = metadata.get("display_name") or frontmatter.get(
            "name") or agent_yaml.get("display_name") or agent_yaml.get("name") or agent_id
        agent = dict(metadata)
        agent.update({
            "name": agent_id,
            "display_name": display_name,
            "version": metadata.get("version", repository.get("version", "")),
            "description": metadata.get(
                "description", "Private agent from {url}/{path}".format(
                    url=install_skill._git_repository_url(repository), path=path)
            ),
            "path": path,
            "required_skills": _extract_required_skills(content, metadata),
        })
        folder = _agent_folder_path(path, paths, yaml_path)
        if folder:
            agent["folder"] = folder
        if yaml_path:
            agent["agent_yaml_path"] = yaml_path
        discovered.append(_normalize_discovered_agent(agent, repository, branch, content))
    return discovered


def _discover_git_repository_agents(repository):
    """Discover every agent available from a git repository."""
    if not isinstance(repository, dict) or repository.get("source") != "git":
        return []
    with install_skill.tempfile.TemporaryDirectory() as tmp:
        repo_dir = Path(tmp) / "repo"
        used_branch = install_skill._clone_git_repository(repository, repo_dir)
        catalog_agents = _discover_agents_from_git_catalog(repository, repo_dir, used_branch)
        if catalog_agents:
            return catalog_agents
        return _discover_agents_by_scanning_git_repo(repository, repo_dir, used_branch)


def load_manifest():
    """Load the installed-agents manifest."""
    if MANIFEST_FILE.exists():
        return json.loads(MANIFEST_FILE.read_text(encoding="utf-8"))
    return {}


def save_manifest(manifest):
    """Save the installed-agents manifest."""
    MANIFEST_FILE.parent.mkdir(parents=True, exist_ok=True)
    MANIFEST_FILE.write_text(json.dumps(manifest, indent=2), encoding="utf-8")


# ── Export targets ─────────────────────────────────────────────────────
SUPPORTED_EXPORT_TARGETS = ("vscode", "generic")


def _requested_agent_export_targets(args):
    """Return requested export targets from parsed CLI arguments.

    @param args parsed CLI arguments
    @return list of requested target names
    """
    targets = list(getattr(args, "target", []) or [])
    if getattr(args, "vscode", False) and "vscode" not in targets:
        targets.append("vscode")
    return targets


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

def _vscode_user_dir():
    """Return the VS Code stable User config directory for this platform.

    @return the platform-specific VS Code User directory Path
    """
    if sys.platform.startswith("win"):
        appdata = os.environ.get("APPDATA", "")
        base = Path(appdata) if appdata else Path.home() / "AppData" / "Roaming"
        return base / "Code" / "User"
    if sys.platform == "darwin":
        return Path.home() / "Library" / "Application Support" / "Code" / "User"
    return Path.home() / ".config" / "Code" / "User"


def resolve_vscode_agents_dir(scope="user", explicit_dir=None):
    """Resolve the VS Code agents directory for --vscode export.

    Resolution order: explicit dir, NEQSIM_VSCODE_AGENTS_DIR env var, then the
    scope default. 'user' scope targets the global prompts folder (available in
    every workspace); 'workspace' scope targets <workspace>/.github/agents.

    @param scope 'user' (default) for the global prompts folder, or 'workspace'
    @param explicit_dir an explicit target directory (overrides detection)
    @return the resolved agents directory Path, or None when none can be found
    """
    if explicit_dir:
        return Path(explicit_dir).expanduser().resolve()
    env_dir = os.environ.get("NEQSIM_VSCODE_AGENTS_DIR", "")
    if env_dir:
        return Path(env_dir).expanduser().resolve()
    if scope == "workspace":
        root = install_skill.find_workspace_root()
        if root is None:
            return None
        return root / ".github" / "agents"
    return _vscode_user_dir() / "prompts"


def export_agent_to_vscode(name, main_file, vscode_dir):
    """Copy an installed agent's main definition into a VS Code agents dir.

    VS Code discovers agents from *.agent.md files, so the main definition is
    copied and renamed to <name>.agent.md.

    @param name the agent name (used for the destination filename)
    @param main_file the installed agent's main markdown definition
    @param vscode_dir the VS Code agents directory to export into
    @return the destination *.agent.md Path
    """
    vscode_dir = Path(vscode_dir)
    vscode_dir.mkdir(parents=True, exist_ok=True)
    dest = vscode_dir / "{name}.agent.md".format(name=name)
    shutil.copy2(str(main_file), str(dest))
    return dest


def resolve_generic_agents_dir(explicit_dir=None):
    """Resolve the generic, tool-neutral agents export directory.

    @param explicit_dir an explicit export root directory (overrides default)
    @return the directory where generic agent folders should be written
    """
    if explicit_dir:
        return Path(explicit_dir).expanduser().resolve() / "agents"
    return EXPORT_DIR / "generic" / "agents"


def export_agent_to_generic(name, source_dir, generic_agents_dir):
    """Copy an installed agent package into the generic export layout.

    @param name the agent name (used as the destination subfolder)
    @param source_dir the installed agent folder under ~/.neqsim/agents/<name>
    @param generic_agents_dir the generic agents export directory
    @return the destination agent folder Path
    """
    dest = Path(generic_agents_dir) / name
    if dest.exists():
        shutil.rmtree(str(dest))
    shutil.copytree(str(source_dir), str(dest))
    main_file = _find_agent_main_file(dest)
    if main_file:
        canonical = dest / "AGENT.md"
        if main_file.resolve() != canonical.resolve():
            shutil.copy2(str(main_file), str(canonical))
    return dest


def _record_export(manifest, name, target, dest):
    """Record an exported copy in the installed-agents manifest.

    @param manifest the installed-agents manifest to update
    @param name the installed agent name
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

    @param kind the exported content kind, for example "agents"
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
                "main_file": str(Path(generic_path) / "AGENT.md"),
                "source": info.get("source", ""),
                "source_type": info.get("source_type", ""),
                "version": info.get("version", ""),
                "display_name": info.get("display_name", ""),
                "description": info.get("description", ""),
                "tags": info.get("tags", []),
                "author": info.get("author", ""),
                "required_skills": info.get("required_skills", []),
                "supported_domains": info.get("supported_domains", []),
                "requires_mcp_tools": info.get("requires_mcp_tools", []),
                "human_review_required": info.get("human_review_required", ""),
                "trust_level": info.get("trust_level", ""),
                "installed_at": info.get("installed_at", ""),
                "content_sha256": info.get("content_sha256", ""),
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


def _export_installed_agent_to_vscode(name, main_file, args, manifest):
    """Export a freshly installed agent into the VS Code agents directory.

    @param name the installed agent name
    @param main_file the installed agent's main markdown definition path
    @param args parsed CLI arguments (reads vscode_scope, vscode_dir)
    @param manifest the installed-agents manifest, updated with vscode_path
    @return true if export succeeds, otherwise false
    """
    if not main_file or not Path(main_file).exists():
        print("  [!!] VS Code export skipped: agent main file not found.")
        return False
    scope = getattr(args, "vscode_scope", "user")
    vscode_dir = resolve_vscode_agents_dir(
        scope, getattr(args, "vscode_dir", None))
    if vscode_dir is None:
        print("  [!!] Could not locate a VS Code workspace for --vscode export.")
        print("       Use --vscode-scope user (default), run inside a workspace,")
        print("       set NEQSIM_VSCODE_AGENTS_DIR, or pass --vscode-dir <path>.")
        return False
    try:
        dest = export_agent_to_vscode(name, main_file, vscode_dir)
    except Exception as exc:
        print("  [!!] VS Code export failed: {exc}".format(exc=exc))
        return False
    _record_export(manifest, name, "vscode", dest)
    save_manifest(manifest)
    print("  [OK] Exported to VS Code agents ({scope}): {dest}".format(
        scope=scope, dest=dest))
    print("       Reload VS Code (Developer: Reload Window) to pick it up.")
    return True


def _export_installed_agent_to_generic(name, source_dir, args, manifest):
    """Export a freshly installed agent into the generic target layout.

    @param name the installed agent name
    @param source_dir the installed agent folder under ~/.neqsim/agents/<name>
    @param args parsed CLI arguments (reads export_dir)
    @param manifest the installed-agents manifest, updated with generic export path
    @return true if export succeeds, otherwise false
    """
    if not source_dir or not Path(source_dir).exists():
        print("  [!!] Generic export skipped: agent package not found.")
        return False
    generic_agents_dir = resolve_generic_agents_dir(getattr(args, "export_dir", None))
    try:
        dest = export_agent_to_generic(name, source_dir, generic_agents_dir)
        _record_export(manifest, name, "generic", dest)
        _write_generic_manifest("agents", generic_agents_dir.parent, manifest)
    except Exception as exc:
        print("  [!!] Generic export failed: {exc}".format(exc=exc))
        return False
    save_manifest(manifest)
    print("  [OK] Exported to generic agents: {dest}".format(dest=dest))
    return True


def _export_installed_agent(name, source_dir, main_file, args, manifest):
    """Export an installed agent to the requested compatibility target.

    @param name the installed agent name
    @param source_dir the installed agent folder under ~/.neqsim/agents/<name>
    @param main_file the installed agent's main markdown definition path
    @param args parsed CLI arguments
    @param manifest the installed-agents manifest
    @return true when every requested export succeeds, otherwise false
    """
    targets = _requested_agent_export_targets(args)
    success = True
    for target in targets:
        if target == "vscode":
            success = _export_installed_agent_to_vscode(name, main_file, args, manifest) and success
        elif target == "generic":
            success = _export_installed_agent_to_generic(name, source_dir, args, manifest) and success
    return success


def _validate_safe_name(name):
    """Exit if an install name is not safe for a local folder."""
    if not name or name in (".", "..") or "/" in name or "\\" in name or ".." in name:
        print("\n  [!!] Unsafe agent name: {name}\n".format(name=name))
        sys.exit(1)


def _find_agent_main_file(agent_dir):
    """Find the primary agent markdown file in an installed/local package."""
    candidates = [agent_dir / "AGENT.md"]
    candidates.extend(sorted(agent_dir.glob("*.agent.md")))
    candidates.extend(sorted(agent_dir.glob("*.md")))
    for candidate in candidates:
        if candidate.is_file() and candidate.name.upper() != "README.MD":
            return candidate
    return None


def _load_agent_yaml(agent_dir):
    """Load agent.yaml metadata if present."""
    agent_yaml = agent_dir / "agent.yaml"
    if not agent_yaml.exists():
        return {}
    text = agent_yaml.read_text(encoding="utf-8")
    if yaml is not None:
        data = yaml.safe_load(text) or {}
    else:
        data = _parse_flat_yaml(text)
    return data if isinstance(data, dict) else {}


def _parse_flat_yaml(text):
    """Parse simple flat YAML key/value and one-level lists without PyYAML."""
    data = {}
    current_list_key = None
    for raw_line in text.splitlines():
        is_indented = raw_line.startswith((" ", "\t"))
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        if is_indented and current_list_key and line.startswith("- "):
            data.setdefault(current_list_key, []).append(
                line[2:].strip().strip('"').strip("'"))
            continue
        current_list_key = None
        if ":" not in line:
            continue
        key, value = line.split(":", 1)
        key = key.strip()
        value = value.strip()
        if value:
            data[key] = install_skill._parse_scalar_value(value)
        else:
            data[key] = []
            current_list_key = key
    return data


def validate_agent_dir(agent_dir):
    """Validate an agent package directory and return a report dict."""
    agent_dir = Path(agent_dir)
    errors = []
    warnings = []
    metadata = {}
    required_skills = []

    if not agent_dir.exists():
        return {
            "valid": False,
            "errors": ["Agent path does not exist: {path}".format(path=agent_dir)],
            "warnings": [],
            "metadata": {},
            "required_skills": [],
            "main_file": "",
        }

    if agent_dir.is_file():
        main_file = agent_dir
        agent_yaml = {}
    else:
        main_file = _find_agent_main_file(agent_dir)
        agent_yaml = _load_agent_yaml(agent_dir)

    if main_file is None:
        errors.append("No AGENT.md or *.agent.md file found")
        content = ""
        frontmatter = {}
    else:
        content = main_file.read_text(encoding="utf-8", errors="replace")
        if len(content.strip()) < 20 or "<html" in content.lower()[:200]:
            errors.append(
                "Agent markdown does not look like a valid agent definition")
        frontmatter = install_skill._extract_frontmatter(content)
        if not frontmatter and not agent_yaml:
            errors.append(
                "Agent definition needs YAML frontmatter or agent.yaml")

    metadata.update(frontmatter)
    metadata.update(agent_yaml)
    manifest_errors, manifest_warnings, metadata = _validate_manifest_metadata(
        metadata, check_unknown_fields=bool(agent_yaml)
    )
    errors.extend(manifest_errors)
    warnings.extend(manifest_warnings)
    if "name" not in metadata:
        errors.append("Agent metadata missing 'name'")
    if "description" not in metadata:
        errors.append("Agent metadata missing 'description'")
    required_skills = _extract_required_skills(content, metadata)

    missing_skills = _find_missing_required_skills(required_skills)
    if missing_skills:
        warnings.append("Missing required skills: {skills}".format(
            skills=", ".join(missing_skills)
        ))

    return {
        "valid": not errors,
        "errors": errors,
        "warnings": warnings,
        "metadata": metadata,
        "manifest_schema_version": AGENT_MANIFEST_SCHEMA_VERSION,
        "required_skills": required_skills,
        "main_file": str(main_file) if main_file else "",
    }


def _available_skill_names():
    """Return core and locally installed skill names."""
    names = set()
    if CORE_SKILLS_DIR.exists():
        names.update(
            path.name for path in CORE_SKILLS_DIR.iterdir() if path.is_dir())
    if INSTALLED_SKILLS_DIR.exists():
        names.update(
            path.name for path in INSTALLED_SKILLS_DIR.iterdir() if path.is_dir())
    try:
        names.update(install_skill.load_manifest().keys())
    except Exception:
        pass
    return names


def _skill_name_candidates(skill_name):
    """Return normalized candidate names for a required skill identifier."""
    normalized = str(skill_name or "").strip()
    if not normalized:
        return []

    candidates = [normalized]
    if normalized.startswith("neqsim-"):
        unprefixed = normalized[len("neqsim-"):]
        if unprefixed:
            candidates.append(unprefixed)
    else:
        candidates.append("neqsim-{name}".format(name=normalized))

    deduped = []
    for candidate in candidates:
        if candidate not in deduped:
            deduped.append(candidate)
    return deduped


def _resolve_skill_name(skill_name, available_names):
    """Resolve a skill identifier against available names, handling common aliases."""
    for candidate in _skill_name_candidates(skill_name):
        if candidate in available_names:
            return candidate
    return ""


def _find_missing_required_skills(required_skills):
    """Return required skills that are not available locally."""
    available = _available_skill_names()
    return [
        skill for skill in required_skills
        if not _resolve_skill_name(skill, available)
    ]


def _requested_skill_export_targets(install_args):
    """Return compatibility targets requested for required skill exports."""
    if install_args is None:
        return []
    targets = list(getattr(install_args, "target", []) or [])
    if getattr(install_args, "vscode", False) and "vscode" not in targets:
        targets.append("vscode")
    return targets


def _skill_install_args(skill_name, install_args):
    """Build skill installer args that preserve agent export target options."""
    return argparse.Namespace(
        name=skill_name,
        force=False,
        vscode=getattr(install_args, "vscode", False),
        target=list(getattr(install_args, "target", []) or []),
        vscode_scope=getattr(install_args, "vscode_scope", "user"),
        vscode_dir=getattr(install_args, "vscode_skills_dir", None),
        export_dir=getattr(install_args, "export_dir", None),
    )


def _required_skill_export_path(skill_name, skill_info, target, args=None, agent_export_path=None):
    """Return the expected required-skill export path for a target.

    @param skill_name the resolved skill name
    @param skill_info the installed skill manifest entry
    @param target the export target to check
    @param args optional parsed CLI arguments
    @param agent_export_path optional exported agent path used to infer VS Code sibling paths
    @return the expected export path string, or an empty string when unknown
    """
    source_path = Path(skill_info.get("path", "")) if skill_info.get("path") else None
    if target == "vscode" and source_path is not None and _path_is_under(source_path, CORE_SKILLS_DIR):
        return str(source_path.parent if source_path.name == "SKILL.md" else source_path)

    if target != "vscode":
        return _manifest_export_path(skill_info, target)

    if agent_export_path:
        agent_path = Path(agent_export_path)
        agent_dir = agent_path.parent if agent_path.suffix else agent_path
        if agent_dir.name == "agents" and agent_dir.parent.name == ".github":
            return str(agent_dir.parent / "skills" / skill_name)
        return str(agent_dir / "skills" / skill_name)

    skills_dir = install_skill.resolve_vscode_skills_dir(
        getattr(args, "vscode_skills_dir", None), getattr(args, "vscode_scope", "user"))
    if skills_dir is None:
        return ""
    return str(Path(skills_dir) / skill_name)


def _required_skill_exports_missing(skill_name, skill_info, targets, install_args=None):
    """Return requested targets with missing or stale required-skill exports."""
    missing = []
    for target in targets:
        export_path = _required_skill_export_path(skill_name, skill_info, target, install_args)
        if not export_path or not Path(export_path).exists():
            missing.append(target)
    return missing


def _ensure_required_skill_exports(required_skills, install_args):
    """Export installed required skills to the agent's requested targets."""
    targets = _requested_skill_export_targets(install_args)
    if not targets:
        return []

    try:
        skill_manifest = install_skill.load_manifest()
    except Exception:
        skill_manifest = {}

    unresolved = []
    catalog_by_name = None
    for skill_name in required_skills:
        resolved_name = _resolve_skill_name(skill_name, set(skill_manifest.keys()))
        if not resolved_name:
            try:
                if catalog_by_name is None:
                    skill_catalog = install_skill.load_catalog()
                    catalog_by_name = {
                        str(skill.get("name", "")).strip(): skill for skill in skill_catalog
                        if str(skill.get("name", "")).strip()
                    }
                resolved_name = _resolve_skill_name(skill_name, set(catalog_by_name.keys()))
                if resolved_name:
                    print("  Installing required skill for export: {name}".format(
                        name=resolved_name))
                    install_skill.cmd_install(
                        list(catalog_by_name.values()),
                        _skill_install_args(resolved_name, install_args))
                    skill_manifest = install_skill.load_manifest()
            except SystemExit:
                resolved_name = ""
            except Exception:
                resolved_name = ""
        if not resolved_name:
            available_core = _available_skill_names()
            resolved_name = _resolve_skill_name(skill_name, available_core)
            core_skill_file = CORE_SKILLS_DIR / resolved_name / "SKILL.md" if resolved_name else None
            if core_skill_file is not None and core_skill_file.exists():
                skill_manifest[resolved_name] = {
                    "path": str(core_skill_file),
                    "source": "core",
                }
        if not resolved_name:
            unresolved.append(skill_name)
            continue

        skill_info = skill_manifest.get(resolved_name, {})
        if not _required_skill_exports_missing(resolved_name, skill_info, targets, install_args):
            continue
        source_path = Path(skill_info.get("path", ""))
        source_dir = source_path.parent if source_path else Path("")
        if not source_dir.exists():
            unresolved.append(skill_name)
            continue
        print("  Exporting required skill: {name}".format(name=resolved_name))
        if not install_skill._export_installed_skill(
                resolved_name, source_dir, _skill_install_args(resolved_name, install_args), skill_manifest):
            unresolved.append(skill_name)
            continue
        skill_manifest = install_skill.load_manifest()
    return unresolved


def _print_required_skill_guidance(required_skills, install_missing=False, install_args=None):
    """Print required skill status and optionally install missing catalog skills."""
    if not required_skills:
        return []
    missing = _find_missing_required_skills(required_skills)
    if not missing:
        print("  [OK] Required skills available: {skills}".format(
            skills=", ".join(required_skills)
        ))
        unresolved_exports = _ensure_required_skill_exports(required_skills, install_args)
        if unresolved_exports:
            print("  [!!] Could not export required skills: {skills}".format(
                skills=", ".join(unresolved_exports)))
        return unresolved_exports

    print("  [!!] Missing required skills: {skills}".format(
        skills=", ".join(missing)))
    if not install_missing:
        print("  Install them with: neqsim skill install <skill-name>")
        return missing

    try:
        skill_catalog = install_skill.load_catalog()
    except SystemExit:
        return missing

    catalog_by_name = {
        str(skill.get("name", "")).strip(): skill for skill in skill_catalog
        if str(skill.get("name", "")).strip()
    }

    unresolved = []
    installed_now = []
    for skill_name in missing:
        resolved_name = _resolve_skill_name(
            skill_name, set(catalog_by_name.keys()))
        if not resolved_name:
            unresolved.append(skill_name)
            continue
        print("  Installing missing skill: {name}".format(name=resolved_name))
        args = _skill_install_args(resolved_name, install_args)
        try:
            install_skill.cmd_install(skill_catalog, args)
            installed_now.append(skill_name)
        except SystemExit:
            unresolved.append(skill_name)
    export_check_skills = [skill for skill in required_skills if skill not in installed_now]
    unresolved_exports = _ensure_required_skill_exports(export_check_skills, install_args)
    for skill_name in unresolved_exports:
        if skill_name not in unresolved:
            unresolved.append(skill_name)
    if unresolved:
        print("  [!!] Could not install: {skills}".format(
            skills=", ".join(unresolved)))
    return unresolved


def cmd_list(agents, args):
    """List all agents in the catalog."""
    manifest = load_manifest()
    private_only = getattr(args, "private", False)
    source_filter = "private" if private_only else None
    filtered = [agent for agent in agents if not source_filter or agent.get(
        "_source") == source_filter]

    label = "Private" if private_only else "All"
    print("\n  {label} Agent Catalog ({count} agents)\n".format(
        label=label, count=len(filtered)
    ))
    print("  {name:<35} {source:<10} {tags:<24} {installed}".format(
        name="Name", source="Source", tags="Tags", installed="Installed"
    ))
    print("  {a} {b} {c} {d}".format(
        a="-" * 35, b="-" * 10, c="-" * 24, d="-" * 9))
    for agent in filtered:
        name = agent.get("name", "?")
        source = agent.get("_source", "?")
        tags = agent.get("tags", [])
        tag_text = ", ".join(tags) if isinstance(tags, list) else str(tags)
        installed = "yes" if name in manifest else ""
        print("  {name:<35} {source:<10} {tags:<24} {installed}".format(
            name=name, source=source, tags=tag_text[:24], installed=installed
        ))
    print()


def cmd_search(agents, args):
    """Search agents by name, tag, description, or required skill."""
    query = args.query.lower()
    matches = []
    for agent in agents:
        tags = agent.get("tags", [])
        required = agent.get("required_skills", [])
        searchable = " ".join([
            agent.get("name", ""),
            agent.get("display_name", ""),
            agent.get("description", ""),
            " ".join(tags) if isinstance(tags, list) else str(tags),
            " ".join(required) if isinstance(
                required, list) else str(required),
            _format_list(agent.get("supported_domains", [])),
            _format_list(agent.get("inputs", [])),
            _format_list(agent.get("outputs", [])),
            _format_list(agent.get("requires_mcp_tools", [])),
            str(agent.get("trust_level", "")),
        ]).lower()
        if query in searchable:
            matches.append(agent)

    if not matches:
        print("\n  No agents matching '{query}'.\n".format(query=args.query))
        return

    print("\n  {count} agent(s) matching '{query}':\n".format(
        count=len(matches), query=args.query
    ))
    for agent in matches:
        print("  {name}".format(name=agent.get("name", "?")))
        if agent.get("display_name") and agent.get("display_name") != agent.get("name"):
            print("    Display: {display}".format(
                display=agent.get("display_name")))
        print("    {desc}".format(desc=agent.get("description", "")))
        required = agent.get("required_skills", [])
        if required:
            print("    Required skills: {skills}".format(
                skills=", ".join(required)))
        print()


def cmd_info(agents, args):
    """Show details for a specific agent."""
    name = args.name
    agent = next((item for item in agents if item.get("name") == name), None)
    if not agent:
        print("\n  Agent '{name}' not found in catalog.\n".format(name=name))
        sys.exit(1)

    manifest = load_manifest()
    source_type = agent.get("source", "github")
    print("\n  Agent: {name}".format(name=agent.get("name")))
    if agent.get("display_name") and agent.get("display_name") != agent.get("name"):
        print("  Display name: {name}".format(name=agent.get("display_name")))
    print("  Source: {source}".format(
        source=agent.get("_source", "community")))
    print("  Description: {desc}".format(desc=agent.get("description", "-")))
    print("  Author: {author}".format(author=agent.get("author", "-")))
    if source_type == "local":
        print("  Path: {path}".format(path=agent.get("path", "-")))
    elif source_type == "url":
        print("  URL: {url}".format(url=agent.get("url", "-")))
    else:
        print(
            "  Repo: https://github.com/{repo}".format(repo=agent.get("repo", "-")))
        print("  Path: {path}".format(path=agent.get("path", "AGENT.md")))
        if agent.get("folder"):
            print("  Folder: {folder}".format(folder=agent.get("folder")))
    required = agent.get("required_skills", [])
    if required:
        print("  Required skills: {skills}".format(skills=", ".join(required)))
    if agent.get("supported_domains"):
        print("  Supported domains: {domains}".format(
            domains=_format_list(agent.get("supported_domains"))
        ))
    if agent.get("inputs"):
        print("  Inputs: {inputs}".format(
            inputs=_format_list(agent.get("inputs"))))
    if agent.get("outputs"):
        print("  Outputs: {outputs}".format(
            outputs=_format_list(agent.get("outputs"))))
    if agent.get("requires_mcp_tools"):
        print("  MCP tools: {tools}".format(
            tools=_format_list(agent.get("requires_mcp_tools"))
        ))
    if "human_review_required" in agent:
        print("  Human review required: {value}".format(
            value=str(agent.get("human_review_required")).lower()
        ))
    if agent.get("trust_level"):
        print("  Trust level: {value}".format(value=agent.get("trust_level")))
    tags = agent.get("tags", [])
    print("  Tags: {tags}".format(tags=", ".join(
        tags) if isinstance(tags, list) else tags))
    print("  Installed: {value}".format(
        value="yes" if name in manifest else "no"))
    if name in manifest:
        print("  Local path: {path}".format(
            path=manifest[name].get("path", "-")))
    print()


def _copy_local_file(src_path, dest_dir):
    """Copy a local single-file agent package."""
    dest_name = src_path.name if src_path.name.endswith(
        ".agent.md") else "AGENT.md"
    dest_file = dest_dir / dest_name
    shutil.copy2(str(src_path), str(dest_file))
    sibling_yaml = src_path.parent / "agent.yaml"
    if sibling_yaml.exists():
        shutil.copy2(str(sibling_yaml), str(dest_dir / "agent.yaml"))
    return dest_file


def _install_from_local(agent, dest_dir):
    """Install an agent from a local file path, folder, or network share."""
    src_path = Path(agent["path"])
    if not src_path.exists():
        print("  [!!] Source path not found: {path}".format(path=src_path))
        sys.exit(1)
    if src_path.is_dir():
        shutil.copytree(str(src_path), str(dest_dir), dirs_exist_ok=True)
        print("  [OK] Copied folder from: {path}".format(path=src_path))
        return _find_agent_main_file(dest_dir)
    main_file = _copy_local_file(src_path, dest_dir)
    print("  [OK] Copied from: {path}".format(path=src_path))
    return main_file


def _install_from_url(agent, dest_dir):
    """Install an agent from a direct URL."""
    url = agent.get("url", "")
    if not url:
        print("  [!!] No URL specified for '{name}'.".format(
            name=agent.get("name")))
        sys.exit(1)
    print("  Downloading: {url}".format(url=url))

    req = urllib.request.Request(url)
    token = os.environ.get("PRIVATE_AGENT_TOKEN", "")
    if token:
        req.add_header("Authorization", "Bearer {token}".format(token=token))
    with urllib.request.urlopen(req) as resp:
        content = resp.read()
    dest_file = dest_dir / "AGENT.md"
    dest_file.write_bytes(content)
    return dest_file


def _github_paths_under_folder(repo, folder, branch, auth=None):
    """Return GitHub blob paths under a folder."""
    used_branch, paths = install_skill._list_github_tree_paths(
        repo, branch=branch, auth=auth)
    prefix = folder.rstrip("/") + "/"
    selected = [path for path in paths if path.startswith(prefix)]
    return used_branch, selected


def _install_github_folder(agent, dest_dir):
    """Install an agent package folder from GitHub."""
    repo = agent.get("repo", "")
    folder = agent.get("folder") or agent.get("copy_root")
    branch = agent.get("branch")
    auth = install_skill._github_entry_auth(agent)
    used_branch, paths = _github_paths_under_folder(repo, folder, branch, auth=auth)
    if not paths:
        print(
            "  [!!] No files found under {repo}/{folder}".format(repo=repo, folder=folder))
        sys.exit(1)
    for path in paths:
        content, _branch, _url = install_skill._fetch_github_bytes(
            repo, path, branch=used_branch, auth=auth)
        relative = Path(path).relative_to(folder)
        dest_file = dest_dir / relative
        dest_file.parent.mkdir(parents=True, exist_ok=True)
        dest_file.write_bytes(content)
    agent["branch"] = used_branch
    print("  [OK] Downloaded folder: https://github.com/{repo}/tree/{branch}/{folder}".format(
        repo=repo, branch=used_branch, folder=folder
    ))
    return _find_agent_main_file(dest_dir)


def _install_from_github(agent, dest_dir):
    """Install an agent from a GitHub repo."""
    repo = agent.get("repo", "")
    path = agent.get("path", "AGENT.md")
    branch = agent.get("branch")
    if not repo:
        print("  [!!] No repo specified for '{name}'.".format(
            name=agent.get("name")))
        sys.exit(1)

    if agent.get("folder") or agent.get("copy_root"):
        return _install_github_folder(agent, dest_dir)

    content, used_branch, raw_url = install_skill._fetch_github_bytes(
        repo, path, branch=branch, auth=install_skill._github_entry_auth(agent))
    print("  Downloading: {url}".format(url=raw_url))
    dest_name = Path(path).name if Path(
        path).name.endswith(".agent.md") else "AGENT.md"
    dest_file = dest_dir / dest_name
    dest_file.write_bytes(content)
    agent["branch"] = used_branch

    yaml_path = agent.get("agent_yaml_path")
    if yaml_path:
        yaml_content, _branch, yaml_url = install_skill._fetch_github_bytes(
            repo, yaml_path, branch=used_branch, auth=install_skill._github_entry_auth(agent)
        )
        print("  Downloading: {url}".format(url=yaml_url))
        (dest_dir / "agent.yaml").write_bytes(yaml_content)
    return dest_file


def _install_from_git(agent, dest_dir):
    """Install an agent from a git repository using configured git credentials."""
    path = agent.get("path", "AGENT.md")
    folder = agent.get("folder") or agent.get("copy_root")
    with install_skill.tempfile.TemporaryDirectory() as tmp:
        repo_dir = Path(tmp) / "repo"
        used_branch = install_skill._clone_git_repository(agent, repo_dir)
        if folder:
            source_dir = repo_dir / folder
            if not source_dir.exists() or not source_dir.is_dir():
                print("  [!!] No files found under git folder {folder}".format(folder=folder))
                sys.exit(1)
            shutil.copytree(str(source_dir), str(dest_dir), dirs_exist_ok=True)
            agent["branch"] = used_branch
            print("  [OK] Downloaded git folder: {url}:{folder}".format(
                url=install_skill._git_repository_url(agent), folder=folder))
            return _find_agent_main_file(dest_dir)

        source_file = repo_dir / path
        if not source_file.exists() or not source_file.is_file():
            print("  [!!] Path not found in git source: {path}".format(path=path))
            sys.exit(1)
        dest_name = Path(path).name if Path(path).name.endswith(".agent.md") else "AGENT.md"
        dest_file = dest_dir / dest_name
        shutil.copy2(str(source_file), str(dest_file))
        agent["branch"] = used_branch
        print("  Downloading via git: {url}:{path}".format(
            url=install_skill._git_repository_url(agent), path=path))

        yaml_path = agent.get("agent_yaml_path")
        if yaml_path:
            yaml_file = repo_dir / yaml_path
            if yaml_file.exists():
                shutil.copy2(str(yaml_file), str(dest_dir / "agent.yaml"))
        return dest_file


def cmd_install(agents, args):
    """Install an agent (or every agent) from the catalog."""
    if getattr(args, "all", False) or args.name == "*":
        _install_all_agents(agents, args)
        return

    name = args.name
    if not name:
        print("\n  Specify an agent name, or use --all to install every agent.")
        print("  Run: neqsim agent list\n")
        sys.exit(1)
    _validate_safe_name(name)
    agent = next((item for item in agents if item.get("name") == name), None)
    if not agent:
        print("\n  Agent '{name}' not found in catalog.".format(name=name))
        print("  Run: neqsim agent list\n")
        sys.exit(1)

    manifest = load_manifest()
    if not _install_agent_record(agent, args, manifest):
        sys.exit(1)


def _install_all_agents(agents, args):
    """Install every catalog agent, continuing past individual failures."""
    source_filter = getattr(args, "source", "all")
    seen = set()
    unique = []
    for agent in agents:
        name = agent.get("name", "")
        if not name or name in seen:
            continue
        if source_filter != "all" and agent.get("_source") != source_filter:
            continue
        seen.add(name)
        unique.append(agent)

    total = len(unique)
    if total == 0:
        print("\n  No agent(s) matched source '{source}'.\n".format(source=source_filter))
        return
    print("\n  Installing {total} {source} agent(s) from the catalog...\n".format(
        total=total, source=source_filter))
    manifest = load_manifest()
    installed = []
    failed = []
    for index, agent in enumerate(unique, start=1):
        name = agent.get("name", "")
        print("  [{index}/{total}] {name}".format(
            index=index, total=total, name=name))
        try:
            _validate_safe_name(name)
        except SystemExit:
            failed.append(name)
            continue
        if _install_agent_record(agent, args, manifest):
            installed.append(name)
        else:
            failed.append(name)

    print("\n  ==== Install summary ====")
    print("  Installed/OK: {count}".format(count=len(installed)))
    print("  Failed: {count}".format(count=len(failed)))
    if failed:
        print("  Failed agents: {names}".format(names=", ".join(failed)))
        sys.exit(1)


def _install_agent_record(agent, args, manifest):
    """Install a single resolved agent record.

    @param agent the resolved catalog agent mapping to install
    @param args the parsed CLI arguments (force, install_missing_skills)
    @param manifest the loaded installed-agents manifest, updated in place
    @return True if the agent is installed (or already present), False on failure
    """
    name = agent.get("name", "")
    previous_entry = dict(manifest.get(name, {}))
    if name in manifest and not args.force:
        print("\n  Agent '{name}' already installed at {path}".format(
            name=name, path=manifest[name]["path"]
        ))
        print("  Use --force to reinstall.")
        success = True
        if _requested_agent_export_targets(args):
            source_dir = Path(manifest[name].get("path", ""))
            main_file = manifest[name].get("main_file", "")
            if not source_dir.exists():
                print("  [!!] Installed agent folder is missing: {path}\n".format(
                    path=source_dir))
                return False
            unresolved = _ensure_required_skill_exports(
                manifest[name].get("required_skills", []), args)
            if unresolved:
                print("  [!!] Could not export required skills: {skills}".format(
                    skills=", ".join(unresolved)))
                success = False
            success = _export_installed_agent(
                name, source_dir, main_file, args, manifest) and success
        print()
        return success
        return True

    source_type = agent.get("source", "github")
    dest_dir = INSTALL_DIR / name
    if source_type == "local":
        source_path = Path(agent.get("path", "")).expanduser().resolve()
        dest_path = dest_dir.expanduser().resolve()
        if source_path == dest_path or dest_path in source_path.parents:
            print("\n  [!!] Local source is inside the install target: {path}".format(
                path=source_path
            ))
            print("  Move the source package outside ~/.neqsim/agents/ and retry.\n")
            return False
    if dest_dir.exists():
        shutil.rmtree(str(dest_dir))
    dest_dir.mkdir(parents=True, exist_ok=True)

    try:
        print("\n  Installing '{name}' (source: {source})...".format(
            name=name, source=source_type
        ))
        if source_type == "local":
            main_file = _install_from_local(agent, dest_dir)
        elif source_type == "url":
            main_file = _install_from_url(agent, dest_dir)
        elif source_type == "git":
            main_file = _install_from_git(agent, dest_dir)
        else:
            main_file = _install_from_github(agent, dest_dir)

        report = validate_agent_dir(dest_dir)
        if not report["valid"]:
            shutil.rmtree(str(dest_dir), ignore_errors=True)
            print("  [!!] Installed content is not a valid agent package:")
            for error in report["errors"]:
                print("    - {error}".format(error=error))
            return False

        merge_errors, merge_warnings, merged_metadata = _merge_manifest_metadata(
            report.get("metadata", {}), agent
        )
        if merge_errors:
            shutil.rmtree(str(dest_dir), ignore_errors=True)
            print("  [!!] Agent manifest metadata is invalid:")
            for error in merge_errors:
                print("    - {error}".format(error=error))
            return False

        required_skills = _dedupe_strings(
            _normalize_list(merged_metadata.get("required_skills"))
            + _normalize_list(merged_metadata.get("skills"))
            + report.get("required_skills", [])
            + _normalize_list(agent.get("required_skills"))
        )
        install_missing_skills = getattr(args, "install_missing_skills", True)
        unresolved = _print_required_skill_guidance(
            required_skills, install_missing=install_missing_skills, install_args=args
        )
        for warning in report["warnings"]:
            if not warning.startswith("Missing required skills"):
                print("  [!!] Warning: {warning}".format(warning=warning))
        for warning in merge_warnings:
            print("  [!!] Warning: {warning}".format(warning=warning))

        manifest[name] = {
            "path": str(dest_dir),
            "main_file": report.get("main_file") or str(main_file or ""),
            "manifest_schema_version": report.get(
                "manifest_schema_version", AGENT_MANIFEST_SCHEMA_VERSION
            ),
            "source": agent.get("_source", "community"),
            "source_type": source_type,
            "repo": agent.get("repo", ""),
            "auth": agent.get("auth", ""),
            "url": agent.get("url", ""),
            "remote_path": agent.get("path", ""),
            "branch": agent.get("branch", ""),
            "version": merged_metadata.get("version", agent.get("version", "")),
            "display_name": merged_metadata.get(
                "display_name", agent.get("display_name", "")),
            "description": merged_metadata.get(
                "description", agent.get("description", "")),
            "tags": merged_metadata.get("tags", agent.get("tags", [])),
            "author": agent.get("author", ""),
            "required_skills": required_skills,
            "supported_domains": merged_metadata.get("supported_domains", []),
            "requires_mcp_tools": merged_metadata.get("requires_mcp_tools", []),
            "human_review_required": merged_metadata.get(
                "human_review_required", ""
            ),
            "trust_level": merged_metadata.get("trust_level", ""),
            "inputs": merged_metadata.get("inputs", []),
            "outputs": merged_metadata.get("outputs", []),
            "missing_required_skills": unresolved,
            "installed_at": datetime.now(timezone.utc).isoformat(),
            "content_sha256": install_skill._sha256_tree(dest_dir),
        }
        for key in ("exports", "vscode_path", "generic_path"):
            if key in previous_entry:
                manifest[name][key] = previous_entry[key]
        save_manifest(manifest)

        print("  [OK] Installed to: {path}".format(path=dest_dir))
        print("\n  Agent tools can read the installed package from: {path}\n".format(
            path=dest_dir
        ))
        if not _export_installed_agent(
                name, dest_dir, manifest[name].get("main_file", ""), args, manifest):
            return False
        return not unresolved
    except Exception as exc:
        shutil.rmtree(str(dest_dir), ignore_errors=True)
        print("  [!!] Download failed: {exc}".format(exc=exc))
        if agent.get("repo"):
            print("  You can manually download from: https://github.com/{repo}\n".format(
                repo=agent.get("repo")
            ))
        return False



def cmd_installed(agents, args):
    """Show installed agents."""
    manifest = load_manifest()
    if not manifest:
        print("\n  No community agents installed.")
        print("  Install dir: {path}".format(path=INSTALL_DIR))
        print("  Run: neqsim agent list\n")
        return

    print("\n  Installed Community Agents ({count}):\n".format(
        count=len(manifest)))
    print("  {name:<35} {author:<20} {path}".format(
        name="Name", author="Author", path="Path"
    ))
    print("  {a} {b} {c}".format(a="-" * 35, b="-" * 20, c="-" * 40))
    for name, info in sorted(manifest.items()):
        print("  {name:<35} {author:<20} {path}".format(
            name=name, author=info.get("author", "-"), path=info.get("path", "-")
        ))
    print()


def cmd_export(agents, args):
    """Export an already-installed agent to one or more compatibility targets."""
    manifest = load_manifest()
    name = args.name
    if name not in manifest:
        print("\n  Agent '{name}' is not installed.".format(name=name))
        print("  Install it first with: neqsim agent install {name}\n".format(
            name=name))
        sys.exit(1)
    source_dir = Path(manifest[name].get("path", ""))
    main_file = manifest[name].get("main_file", "")
    if not source_dir.exists():
        print("\n  Installed agent folder is missing: {path}\n".format(
            path=source_dir))
        sys.exit(1)
    if not _export_installed_agent(name, source_dir, main_file, args, manifest):
        sys.exit(1)


def cmd_remove(agents, args):
    """Remove an installed agent."""
    name = args.name
    manifest = load_manifest()
    if name not in manifest:
        print("\n  Agent '{name}' is not installed.\n".format(name=name))
        sys.exit(1)

    agent_dir = INSTALL_DIR / name
    if agent_dir.exists():
        shutil.rmtree(str(agent_dir))

    vscode_path = manifest.get(name, {}).get("vscode_path", "")
    if vscode_path:
        vp = Path(vscode_path)
        if vp.exists():
            if vp.is_dir():
                shutil.rmtree(str(vp), ignore_errors=True)
            else:
                vp.unlink()
            print("  [OK] Removed VS Code copy: {path}".format(path=vp))

    generic_export_root = None
    for target, export_path in manifest.get(name, {}).get("exports", {}).items():
        ep = Path(export_path)
        if ep.exists():
            if ep.is_dir():
                shutil.rmtree(str(ep), ignore_errors=True)
            else:
                ep.unlink()
            print("  [OK] Removed {target} export: {path}".format(
                target=target, path=ep))
        if target == "generic":
            generic_export_root = ep.parent.parent

    del manifest[name]
    if generic_export_root:
        _write_generic_manifest("agents", generic_export_root, manifest)
    save_manifest(manifest)
    print("\n  [OK] Removed agent '{name}'.\n".format(name=name))


def cmd_validate(agents, args):
    """Validate an installed agent or local agent path."""
    target = Path(args.target)
    if not target.exists():
        manifest = load_manifest()
        info = manifest.get(args.target)
        if info:
            target = Path(info.get("path", ""))
        else:
            print(
                "\n  Agent path/name not found: {target}\n".format(target=args.target))
            sys.exit(1)

    report = validate_agent_dir(target)
    print("\n  Agent validation: {target}".format(target=target))
    print("  Valid: {valid}".format(valid="yes" if report["valid"] else "no"))
    if report.get("main_file"):
        print("  Main file: {path}".format(path=report["main_file"]))
    required = report.get("required_skills", [])
    if required:
        print("  Required skills: {skills}".format(skills=", ".join(required)))
    for error in report["errors"]:
        print("  ERROR: {error}".format(error=error))
    for warning in report["warnings"]:
        print("  WARN: {warning}".format(warning=warning))
    print()
    if not report["valid"]:
        sys.exit(1)
    if getattr(args, "strict", False) and report["warnings"]:
        sys.exit(1)


def _manifest_export_path(info, target):
    """Return the recorded export path for a manifest entry and target."""
    return info.get("exports", {}).get(target) or info.get("{target}_path".format(
        target=target), "")


def _read_json_file(path):
    """Read a JSON file and return an object, or None if unreadable."""
    try:
        return json.loads(Path(path).read_text(encoding="utf-8"))
    except Exception:
        return None


def _check_generic_manifest_fresh(kind, root_dir, installed_manifest, failures):
    """Append failures when a generic manifest is missing or stale."""
    manifest_path = Path(root_dir) / "manifest.json"
    payload = _read_json_file(manifest_path)
    if payload is None:
        failures.append("Generic {kind} manifest is missing or unreadable: {path}".format(
            kind=kind, path=manifest_path))
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
        if isinstance(info, dict) and (not info.get("path") or _path_is_under(info.get("path", ""), checked_root))
    }
    missing = sorted(name for name in expected if name not in exported)
    extra = sorted(name for name in exported if name not in expected)
    if missing:
        failures.append("Generic {kind} manifest is missing: {names}".format(
            kind=kind, names=", ".join(missing)))
    if extra:
        failures.append("Generic {kind} manifest has stale entries: {names}".format(
            kind=kind, names=", ".join(extra)))


def _check_export_target(target, args):
    """Check installed agent exports and required skill exports for a target."""
    agent_manifest = load_manifest()
    try:
        skill_manifest = install_skill.load_manifest()
    except Exception:
        skill_manifest = {}

    failures = []
    warnings = []
    checked_agents = []
    available_skill_names = _available_skill_names()

    for name, info in sorted(agent_manifest.items()):
        export_path = _manifest_export_path(info, target)
        if not export_path:
            warnings.append("Agent is installed but not exported to {target}: {name}".format(
                target=target, name=name))
            continue
        checked_agents.append(name)
        path = Path(export_path)
        if not path.exists():
            failures.append("Agent export is missing: {name} -> {path}".format(
                name=name, path=path))
        if target == "generic" and not (path / "AGENT.md").exists():
            failures.append("Generic agent export lacks AGENT.md: {name} -> {path}".format(
                name=name, path=path))

        for required in info.get("required_skills", []):
            resolved = _resolve_skill_name(required, available_skill_names)
            if not resolved:
                failures.append(
                    "Agent {agent} requires skill not installed: {skill}".format(
                        agent=name, skill=required))
                continue
            skill_info = skill_manifest.get(resolved, {})
            if not skill_info and (CORE_SKILLS_DIR / resolved).is_dir():
                skill_info = {"path": str(CORE_SKILLS_DIR / resolved / "SKILL.md")}
            skill_export = _required_skill_export_path(
                resolved, skill_info, target, args, export_path)
            if not skill_export:
                failures.append(
                    "Agent {agent} requires skill not exported to {target}: {skill}".format(
                        agent=name, target=target, skill=resolved))
                continue
            if not Path(skill_export).exists():
                failures.append(
                    "Required skill export is missing: {skill} -> {path}".format(
                        skill=resolved, path=skill_export))

    if target == "generic":
        generic_root = resolve_generic_agents_dir(getattr(args, "export_dir", None)).parent
        _check_generic_manifest_fresh("agents", generic_root, agent_manifest, failures)
        skill_generic_root = install_skill.resolve_generic_skills_dir(
            getattr(args, "export_dir", None)).parent
        _check_generic_manifest_fresh("skills", skill_generic_root, skill_manifest, failures)

    print("\n  NeqSim agent export doctor ({target})\n".format(target=target))
    print("  Checked exported agents: {count}".format(count=len(checked_agents)))
    if checked_agents:
        print("  Agents: {names}".format(names=", ".join(checked_agents)))
    for warning in warnings:
        print("  WARN: {warning}".format(warning=warning))
    for failure in failures:
        print("  ERROR: {failure}".format(failure=failure))
    if failures:
        print("\n  Result: FAIL\n")
        sys.exit(1)
    print("\n  Result: PASS\n")


def cmd_run(agents, args):
    """Print safe launch guidance for an installed agent."""
    manifest = load_manifest()
    info = manifest.get(args.name)
    if not info:
        print("\n  Agent '{name}' is not installed.".format(name=args.name))
        print("  Install it first with: neqsim agent install {name}\n".format(
            name=args.name))
        sys.exit(1)
    print("\n  NeqSim does not execute installed agents directly.")
    print("  Installed package: {path}".format(path=info.get("path", "-")))
    print("  Main definition:  {path}".format(path=info.get("main_file", "-")))
    print("  Open this definition in your AI agent tool and run it explicitly there.\n")


def cmd_private_init(agents, args):
    """Create the private agent catalog template at ~/.neqsim/private-agents.yaml."""
    if PRIVATE_CATALOG_FILE.exists():
        print("\n  Private catalog already exists: {path}".format(
            path=PRIVATE_CATALOG_FILE))
        print("  Edit it to add your private agents.\n")
        return

    from datetime import date
    content = PRIVATE_CATALOG_TEMPLATE.format(today=date.today().isoformat())
    PRIVATE_CATALOG_FILE.parent.mkdir(parents=True, exist_ok=True)
    PRIVATE_CATALOG_FILE.write_text(content, encoding="utf-8")
    print("\n  [OK] Created private catalog: {path}".format(
        path=PRIVATE_CATALOG_FILE))
    print("  Edit it to add your internal/company agents.")
    print("  Then: neqsim agent list --private\n")


def cmd_schema(agents, args):
    """Print the supported agent manifest schema."""
    print(_manifest_schema_text())


def cmd_doctor(agents, args):
    """Print enterprise auth broker readiness for agent installation."""
    target = getattr(args, "target", None)
    if target:
        _check_export_target(target, args)
        return

    status = install_skill.get_enterprise_auth_status()
    private_agent_token_available = bool(os.environ.get("PRIVATE_AGENT_TOKEN"))
    print("\n  NeqSim agent enterprise access check\n")
    print("  NeqSim does not request, inspect, or store credentials, cookies, tokens, or MFA codes.")
    print("  Preferred GitHub Enterprise login: gh auth login --web")
    print("  Preferred internal Git login: Git Credential Manager / your configured git SSO broker.\n")

    checks = [
        ("github_cli", "GitHub CLI / browser SSO available"),
        ("git", "git available for Git Credential Manager / SSO"),
        ("private_agent_catalog", "Private agent catalog exists"),
    ]
    for key, label in checks:
        if key == "private_agent_catalog":
            item = {"available": PRIVATE_CATALOG_FILE.exists(), "detail": str(PRIVATE_CATALOG_FILE)}
        else:
            item = status.get(key, {})
        if isinstance(item, dict):
            available = bool(item.get("available", False))
            detail = item.get("detail", "")
        else:
            available = bool(item)
            detail = ""
        suffix = " ({detail})".format(detail=detail) if detail else ""
        print("  [{mark}] {label}{suffix}".format(
            mark="OK" if available else "--", label=label, suffix=suffix))

    print("\n  Optional non-interactive fallback status:")
    print("  [{mark}] PRIVATE_AGENT_TOKEN set".format(
        mark="OK" if private_agent_token_available else "--"))

    print("\n  Supported private source modes:")
    print("    - source: github, auth: github-cli (browser SSO)")
    print("    - source: git, auth: git-credential-manager")
    print("    - source: local, auth: none")
    print("    - source: url, optional PRIVATE_AGENT_TOKEN fallback for non-interactive endpoints\n")


def main():
    """Run the agent installer CLI."""
    examples = "\n".join([
        "Examples:",
        "  neqsim agent list",
        "  neqsim agent list --private",
        "  neqsim agent search \"tie-in\"",
        "  neqsim agent install neqsim-example-agent",
        "  neqsim agent install neqsim-example-agent --no-install-missing-skills",
        "  neqsim agent install neqsim-example-agent --target vscode",
        "  neqsim agent install neqsim-example-agent --target generic",
        "  neqsim agent export neqsim-example-agent --target vscode",
        "  neqsim agent export neqsim-example-agent --target generic",
        "  neqsim agent install --all --target vscode",
        "  neqsim agent install --all --source community --target vscode",
        "  neqsim agent install --all --source private --target vscode",
        "  neqsim agent installed",
        "  neqsim agent info neqsim-example-agent",
        "  neqsim agent validate neqsim-example-agent",
        "  neqsim agent schema",
        "  neqsim agent doctor",
        "  neqsim agent doctor --target vscode",
        "  neqsim agent remove neqsim-example-agent",
        "  neqsim agent private-init",
        "",
        "Preferred private access uses `gh auth login --web` or Git Credential Manager.",
        "Internal raw URLs can use PRIVATE_AGENT_TOKEN env var as a non-interactive fallback.",
        "Install never executes agent code.",
    ])
    parser = argparse.ArgumentParser(
        description="Install community and private agents from NeqSim agent catalogs.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=examples,
    )
    sub = parser.add_subparsers(dest="command")

    p_list = sub.add_parser("list", help="List all agents in the catalog")
    p_list.add_argument("--private", action="store_true",
                        help="List private catalog only")

    p_search = sub.add_parser("search", help="Search agents by keyword")
    p_search.add_argument("query", help="Search term")

    p_info = sub.add_parser("info", help="Show details for an agent")
    p_info.add_argument("name", help="Agent name")
    p_inspect = sub.add_parser("inspect", help="Alias for info")
    p_inspect.add_argument("name", help="Agent name")

    p_install = sub.add_parser("install", help="Install an agent")
    p_install.add_argument(
        "name", nargs="?", help="Agent name from catalog (omit when using --all)")
    p_install.add_argument("--all", action="store_true",
                           help="Install every agent in the catalog")
    p_install.add_argument(
        "--source", choices=["all", "community", "private"], default="all",
        help="When used with --all, install all sources, community only, or private/enterprise only")
    p_install.add_argument("--force", action="store_true",
                           help="Reinstall if exists")
    p_install.set_defaults(install_missing_skills=True)
    p_install.add_argument(
        "--no-install-missing-skills",
        dest="install_missing_skills",
        action="store_false",
        help="Do not auto-install required skills",
    )
    p_install.add_argument(
        "--install-missing-skills",
        dest="install_missing_skills",
        action="store_true",
        help="Deprecated alias; auto-install is enabled by default",
    )
    p_install.add_argument(
        "--vscode", action="store_true",
        help="Also export the agent to a VS Code agents location")
    p_install.add_argument(
        "--target", action="append", choices=SUPPORTED_EXPORT_TARGETS,
        help="Also export to a compatibility target (repeatable): vscode or generic")
    p_install.add_argument(
        "--vscode-scope", choices=["user", "workspace"], default="user",
        help="VS Code export scope: 'user' (all workspaces, default) or 'workspace'")
    p_install.add_argument(
        "--vscode-dir", default=None,
        help="Explicit VS Code agents directory for --vscode export")
    p_install.add_argument(
        "--vscode-skills-dir", default=None,
        help="Explicit VS Code skills directory for required skill exports")
    p_install.add_argument(
        "--export-dir", default=None,
        help="Generic export root for --target generic (default: ~/.neqsim/export/generic)")

    sub.add_parser("installed", help="Show installed agents")

    p_export = sub.add_parser("export", help="Export an installed agent to an AI-tool target")
    p_export.add_argument("name", help="Installed agent name")
    p_export.add_argument(
        "--target", action="append", choices=SUPPORTED_EXPORT_TARGETS, required=True,
        help="Export target (repeatable): vscode or generic")
    p_export.add_argument(
        "--vscode-scope", choices=["user", "workspace"], default="user",
        help="VS Code export scope for --target vscode")
    p_export.add_argument(
        "--vscode-dir", default=None,
        help="Explicit VS Code agents directory for --target vscode")
    p_export.add_argument(
        "--vscode-skills-dir", default=None,
        help="Explicit VS Code skills directory for required skill exports")
    p_export.add_argument(
        "--export-dir", default=None,
        help="Generic export root for --target generic (default: ~/.neqsim/export/generic)")

    p_remove = sub.add_parser("remove", help="Remove an installed agent")
    p_remove.add_argument("name", help="Agent name to remove")

    p_validate = sub.add_parser(
        "validate", help="Validate an installed agent or local path")
    p_validate.add_argument(
        "target", help="Installed agent name or local path")
    p_validate.add_argument("--strict", action="store_true",
                            help="Fail on validation warnings")

    p_run = sub.add_parser(
        "run", help="Show safe launch guidance for an installed agent")
    p_run.add_argument("name", help="Installed agent name")

    sub.add_parser(
        "schema", help="Show the supported agent.yaml manifest schema")

    p_doctor = sub.add_parser(
        "doctor", help="Check enterprise auth broker readiness or export target health")
    p_doctor.add_argument(
        "--target", choices=SUPPORTED_EXPORT_TARGETS,
        help="Check installed agent exports and required skill exports for this target")
    p_doctor.add_argument(
        "--export-dir", default=None,
        help="Generic export root for --target generic (default: ~/.neqsim/export/generic)")

    sub.add_parser(
        "private-init", help="Create private catalog template at ~/.neqsim/")

    args = parser.parse_args()
    if not args.command:
        parser.print_help()
        sys.exit(0)

    if args.command == "private-init":
        cmd_private_init([], args)
        return
    if args.command in ("validate", "run", "schema", "doctor"):
        commands_without_catalog = {
            "validate": cmd_validate,
            "run": cmd_run,
            "schema": cmd_schema,
            "doctor": cmd_doctor,
        }
        commands_without_catalog[args.command]([], args)
        return

    private_only = getattr(args, "private", False)
    agents = load_catalog(private_only=private_only)
    commands = {
        "list": cmd_list,
        "search": cmd_search,
        "info": cmd_info,
        "inspect": cmd_info,
        "install": cmd_install,
        "export": cmd_export,
        "installed": cmd_installed,
        "remove": cmd_remove,
    }
    commands[args.command](agents, args)


if __name__ == "__main__":
    main()
