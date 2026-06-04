#!/usr/bin/env python3
"""Install community and private agents from NeqSim agent catalogs.

Usage:
    neqsim agent list
    neqsim agent list --private
    neqsim agent search <query>
    neqsim agent install <name>
    neqsim agent installed
    neqsim agent remove <name>
    neqsim agent info <name>
    neqsim agent validate <name-or-path>
    neqsim agent schema
    neqsim agent private-init

Agents are installed to ~/.neqsim/agents/<name>/ and are never executed during
installation. An agent package may be a single .agent.md file, a folder with
AGENT.md, or a folder with AGENT.md plus agent.yaml and supporting resources.
Private GitHub repositories can be read with GITHUB_TOKEN or an authenticated gh
CLI session.
"""

import argparse
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
    "inputs",
    "outputs",
    "requires_mcp_tools",
    "human_review_required",
    "trust_level",
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
#   - Private GitHub repos (source: github, requires GITHUB_TOKEN or gh auth)
#   - Internal Git servers (source: url, with direct URL)
#
# Install with: neqsim agent install <name>

catalog_version: "1.0"
last_updated: "{today}"
organisation: "your-company"

repositories:
    # -- Private GitHub repository discovery --
    # Requires either GITHUB_TOKEN in the shell or `gh auth login` with repo access.
    # Use catalog_path: "" when the repo has no community-agents.yaml and should
    # be scanned for AGENT.md / *.agent.md files directly.
    # - repo: "your-org/neqsim-enterprise-agents"
    #   source: github
    #   branch: "main"
    #   catalog_path: ""
    #   agent_path_glob: ["agents/**/AGENT.md", "agents/**/*.agent.md"]
    #   name_prefix: "enterprise-"  # optional, avoids public/private name clashes
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
  #   repo: "your-org/neqsim-company-agents"
  #   path: "agents/company-process-agent/AGENT.md"
  #   folder: "agents/company-process-agent"
  #   required_skills: [neqsim-api-patterns, neqsim-platform-modeling]

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
            for agent in _discover_github_repository_agents(repository):
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
    return re.split(r"\s+", cleaned, 1)[0].strip("`").rstrip(".")


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
    normalized["source"] = normalized.get("source", "github")
    if default_branch and "branch" not in normalized:
        normalized["branch"] = default_branch
    if "author" not in normalized:
        normalized["author"] = repository.get(
            "author") or repo.split("/", 1)[0]
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
    text = install_skill._fetch_github_text(repo, catalog_path, branch=branch)
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


def _fetch_remote_agent_yaml(repo, yaml_path, branch):
    """Fetch and parse a remote agent.yaml file."""
    if not yaml_path:
        return {}
    text = install_skill._fetch_github_text(repo, yaml_path, branch=branch)
    return _parse_remote_agent_yaml(text)


def _discover_agents_by_scanning_repo(repository, branch):
    """Discover agents by scanning an online GitHub repo for agent files."""
    repo = repository.get("repo", "")
    used_branch, paths = install_skill._list_github_tree_paths(
        repo, branch=branch)
    patterns = _agent_globs(repository)
    discovered = []
    for path in sorted(paths):
        if not any(fnmatch.fnmatch(path, pattern) for pattern in patterns):
            continue
        if not (path.endswith(".agent.md") or path.endswith("AGENT.md")):
            continue
        content = install_skill._fetch_github_text(
            repo, path, branch=used_branch)
        frontmatter = install_skill._extract_frontmatter(content)
        yaml_path = _sibling_agent_yaml_path(path, paths)
        agent_yaml = _fetch_remote_agent_yaml(repo, yaml_path, used_branch)
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
        "branch") or install_skill._get_default_github_branch(repo)
    try:
        catalog_agents = _discover_agents_from_remote_catalog(
            repository, branch)
        if catalog_agents:
            return catalog_agents
    except Exception:
        pass
    return _discover_agents_by_scanning_repo(repository, branch)


def load_manifest():
    """Load the installed-agents manifest."""
    if MANIFEST_FILE.exists():
        return json.loads(MANIFEST_FILE.read_text(encoding="utf-8"))
    return {}


def save_manifest(manifest):
    """Save the installed-agents manifest."""
    MANIFEST_FILE.parent.mkdir(parents=True, exist_ok=True)
    MANIFEST_FILE.write_text(json.dumps(manifest, indent=2), encoding="utf-8")


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


def _print_required_skill_guidance(required_skills, install_missing=False):
    """Print required skill status and optionally install missing catalog skills."""
    if not required_skills:
        return []
    missing = _find_missing_required_skills(required_skills)
    if not missing:
        print("  [OK] Required skills available: {skills}".format(
            skills=", ".join(required_skills)
        ))
        return []

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
    for skill_name in missing:
        resolved_name = _resolve_skill_name(
            skill_name, set(catalog_by_name.keys()))
        if not resolved_name:
            unresolved.append(skill_name)
            continue
        print("  Installing missing skill: {name}".format(name=resolved_name))
        args = argparse.Namespace(name=resolved_name, force=False)
        try:
            install_skill.cmd_install(skill_catalog, args)
        except SystemExit:
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


def _github_paths_under_folder(repo, folder, branch):
    """Return GitHub blob paths under a folder."""
    used_branch, paths = install_skill._list_github_tree_paths(
        repo, branch=branch)
    prefix = folder.rstrip("/") + "/"
    selected = [path for path in paths if path.startswith(prefix)]
    return used_branch, selected


def _install_github_folder(agent, dest_dir):
    """Install an agent package folder from GitHub."""
    repo = agent.get("repo", "")
    folder = agent.get("folder") or agent.get("copy_root")
    branch = agent.get("branch")
    used_branch, paths = _github_paths_under_folder(repo, folder, branch)
    if not paths:
        print(
            "  [!!] No files found under {repo}/{folder}".format(repo=repo, folder=folder))
        sys.exit(1)
    for path in paths:
        content, _branch, _url = install_skill._fetch_github_bytes(
            repo, path, branch=used_branch)
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
        repo, path, branch=branch)
    print("  Downloading: {url}".format(url=raw_url))
    dest_name = Path(path).name if Path(
        path).name.endswith(".agent.md") else "AGENT.md"
    dest_file = dest_dir / dest_name
    dest_file.write_bytes(content)
    agent["branch"] = used_branch

    yaml_path = agent.get("agent_yaml_path")
    if yaml_path:
        yaml_content, _branch, yaml_url = install_skill._fetch_github_bytes(
            repo, yaml_path, branch=used_branch
        )
        print("  Downloading: {url}".format(url=yaml_url))
        (dest_dir / "agent.yaml").write_bytes(yaml_content)
    return dest_file


def cmd_install(agents, args):
    """Install an agent from the catalog."""
    name = args.name
    _validate_safe_name(name)
    agent = next((item for item in agents if item.get("name") == name), None)
    if not agent:
        print("\n  Agent '{name}' not found in catalog.".format(name=name))
        print("  Run: neqsim agent list\n")
        sys.exit(1)

    manifest = load_manifest()
    if name in manifest and not args.force:
        print("\n  Agent '{name}' already installed at {path}".format(
            name=name, path=manifest[name]["path"]
        ))
        print("  Use --force to reinstall.\n")
        return

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
            sys.exit(1)
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
        else:
            main_file = _install_from_github(agent, dest_dir)

        report = validate_agent_dir(dest_dir)
        if not report["valid"]:
            shutil.rmtree(str(dest_dir), ignore_errors=True)
            print("  [!!] Installed content is not a valid agent package:")
            for error in report["errors"]:
                print("    - {error}".format(error=error))
            sys.exit(1)

        merge_errors, merge_warnings, merged_metadata = _merge_manifest_metadata(
            report.get("metadata", {}), agent
        )
        if merge_errors:
            shutil.rmtree(str(dest_dir), ignore_errors=True)
            print("  [!!] Agent manifest metadata is invalid:")
            for error in merge_errors:
                print("    - {error}".format(error=error))
            sys.exit(1)

        required_skills = _dedupe_strings(
            _normalize_list(merged_metadata.get("required_skills"))
            + _normalize_list(merged_metadata.get("skills"))
            + report.get("required_skills", [])
            + _normalize_list(agent.get("required_skills"))
        )
        install_missing_skills = getattr(args, "install_missing_skills", True)
        unresolved = _print_required_skill_guidance(
            required_skills, install_missing=install_missing_skills
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
            "remote_path": agent.get("path", ""),
            "branch": agent.get("branch", ""),
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
        }
        save_manifest(manifest)

        print("  [OK] Installed to: {path}".format(path=dest_dir))
        print("\n  Agent tools can read the installed package from: {path}\n".format(
            path=dest_dir
        ))
    except Exception as exc:
        shutil.rmtree(str(dest_dir), ignore_errors=True)
        print("  [!!] Download failed: {exc}".format(exc=exc))
        if agent.get("repo"):
            print("  You can manually download from: https://github.com/{repo}\n".format(
                repo=agent.get("repo")
            ))
        sys.exit(1)


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
    del manifest[name]
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


def main():
    """Run the agent installer CLI."""
    parser = argparse.ArgumentParser(
        description="Install community and private agents from NeqSim agent catalogs.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=textwrap.dedent("""\
            Examples:
              neqsim agent list
              neqsim agent search "tie-in"
              neqsim agent install neqsim-example-agent
                            neqsim agent install neqsim-example-agent --no-install-missing-skills
              neqsim agent installed
              neqsim agent info neqsim-example-agent
              neqsim agent validate neqsim-example-agent
              neqsim agent schema
              neqsim agent remove neqsim-example-agent
              neqsim agent private-init

            Private repos require GITHUB_TOKEN or `gh auth login` with repo access.
            Internal URLs can use PRIVATE_AGENT_TOKEN env var.
            Install never executes agent code.
        """),
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
    p_install.add_argument("name", help="Agent name from catalog")
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

    sub.add_parser("installed", help="Show installed agents")

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

    sub.add_parser(
        "private-init", help="Create private catalog template at ~/.neqsim/")

    args = parser.parse_args()
    if not args.command:
        parser.print_help()
        sys.exit(0)

    if args.command == "private-init":
        cmd_private_init([], args)
        return
    if args.command in ("validate", "run", "schema"):
        commands_without_catalog = {
            "validate": cmd_validate, "run": cmd_run, "schema": cmd_schema}
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
        "installed": cmd_installed,
        "remove": cmd_remove,
    }
    commands[args.command](agents, args)


if __name__ == "__main__":
    main()
