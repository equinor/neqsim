"""
discovery_registry.py - one registry of agents and skills for NeqSim discovery.

``agent_search`` and ``skill_search`` used to find their sources independently, so
an enterprise agent installed from a plugin could be found while the skills it
declares were not. Both now take their locations from here, and the registry can
be listed on its own.

Skill sources, in priority order (the first copy of an id wins; later copies are
recorded under ``other_locations``):

  * neqsim repo              : ``.github/skills/<name>/SKILL.md``
  * neqsim-community-skills  : ``skills/**/SKILL.md`` (sibling clone)
  * neqsim-enterprise-skills : ``skills/**/SKILL.md`` (sibling clone)
  * installed                : ``~/.neqsim/skills/<name>/SKILL.md``, ``~/.copilot/skills``
  * plugin:<name>            : ``<plugin>/skills/<name>/SKILL.md`` under
    ``~/.vscode/agent-plugins``, ``~/.copilot/installed-plugins`` or
    ``%APPDATA%/Code/agentPlugins``

Agent sources are the ones ``agent_search`` documents. The last two skill sources,
like the last two agent sources, are fallbacks that hold copies of what a checkout
may also provide.

Environment overrides (tests and non-default installs): ``NEQSIM_AGENTS_HOME``,
``NEQSIM_SKILLS_HOME``, ``NEQSIM_AGENT_PLUGINS_HOME``.

Usage::

    python devtools/discovery_registry.py                  # counts per source
    python devtools/discovery_registry.py --unresolved     # agents with missing skills
    python devtools/discovery_registry.py --json --kind skills
"""

from __future__ import annotations

import argparse
import json
import os
import sys
from dataclasses import asdict, dataclass, field
from pathlib import Path
from typing import Dict, Iterable, List, Optional, Tuple

sys.path.insert(0, str(Path(__file__).resolve().parent))

import agent_frontmatter as af  # noqa: E402

# (directory, source label, glob, is_fallback)
Root = Tuple[Path, str, str, bool]


@dataclass
class Entry:
    """One agent or skill, with the fields both retrievers and reports need."""

    kind: str                 # "agent" or "skill"
    id: str                   # canonical id: agent handle or skill front-matter name
    name: str
    source: str               # neqsim, neqsim-community-skills, installed, plugin:<name>, ...
    version: str
    location: str
    description: str = ""
    requires: List[str] = field(default_factory=list)
    missing: List[str] = field(default_factory=list)
    available: bool = True    # an agent is unavailable while a required skill is unresolved
    fallback: bool = False
    other_locations: List[str] = field(default_factory=list)


# ---------------------------------------------------------------- locations

def installed_agents_root() -> Path:
    """Directory ``neqsim agent install`` places agents into (``NEQSIM_AGENTS_HOME``)."""
    override = os.environ.get("NEQSIM_AGENTS_HOME")
    return Path(override) if override else Path.home() / ".neqsim" / "agents"


def installed_skills_roots() -> List[Path]:
    """Directories holding installed skills (``NEQSIM_SKILLS_HOME`` replaces both)."""
    override = os.environ.get("NEQSIM_SKILLS_HOME")
    if override:
        return [Path(override)]
    return [Path.home() / ".neqsim" / "skills", Path.home() / ".copilot" / "skills"]


def plugin_bases() -> List[Path]:
    """Folders that hold installed Agent Plugins packages."""
    override = os.environ.get("NEQSIM_AGENT_PLUGINS_HOME")
    if override:
        return [Path(override)]
    bases = [Path.home() / ".vscode" / "agent-plugins",         # VS Code marketplace
             Path.home() / ".copilot" / "installed-plugins"]    # Copilot CLI
    appdata = os.environ.get("APPDATA")
    if appdata:
        bases.append(Path(appdata) / "Code" / "agentPlugins")   # user-data cache copies
    return bases


def _plugin_manifest(plugin_dir: Path) -> Dict[str, object]:
    try:
        data = json.loads((plugin_dir / "plugin.json").read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return {}
    return data if isinstance(data, dict) else {}


def plugin_name(plugin_dir: Path) -> str:
    """Name from ``plugin.json``, else the folder name (version-hash cache copies)."""
    name = _plugin_manifest(plugin_dir).get("name")
    return str(name) if name else plugin_dir.name


def plugin_version(plugin_dir: Path) -> str:
    return str(_plugin_manifest(plugin_dir).get("version") or "")


def _plugin_content_dirs(subpath: str, require_plugin: bool) -> List[Tuple[Path, Path]]:
    """(content dir, plugin dir) pairs, one per plugin name, first location wins.

    Depth-bounded globs, not ``**``, because a plugin tree holds hundreds of skill
    folders. ``require_plugin`` guards generic folder names such as ``skills`` so an
    unrelated ``skills`` directory is not mistaken for a plugin.
    """
    parts = len(subpath.split("/"))
    found: List[Tuple[Path, Path]] = []
    seen = set()
    for base in plugin_bases():
        if not base.is_dir():
            continue
        for depth in range(1, 5):
            pattern = "/".join(["*"] * depth) + "/" + subpath
            for content in sorted(base.glob(pattern)):
                if not content.is_dir():
                    continue
                plugin_dir = content.parents[parts - 1]
                if require_plugin and not (
                        (plugin_dir / "plugin.json").is_file()
                        or (plugin_dir / "com.github.copilot").is_dir()):
                    continue
                name = plugin_name(plugin_dir)
                if name in seen:
                    continue
                seen.add(name)
                found.append((content, plugin_dir))
    return found


def plugin_agent_roots() -> List[Root]:
    """Agent folders of installed plugins - flat ``*.agent.md``."""
    return [(content, "plugin:" + plugin_name(pdir), "*.agent.md", True)
            for content, pdir in _plugin_content_dirs("com.github.copilot/agents", False)]


def plugin_skill_roots() -> List[Root]:
    """Skill folders of installed plugins - ``skills/<name>/SKILL.md``."""
    return [(content, "plugin:" + plugin_name(pdir), "*/SKILL.md", True)
            for content, pdir in _plugin_content_dirs("skills", True)]


def sibling_skill_roots(skills_root: Path) -> List[Root]:
    """Sibling ``*-skills`` repos (community + enterprise) when checked out.

    ``skills_root`` is normally ``<neqsim>/.github/skills``, so the workspace root
    is three levels up. Their layout is nested by category.
    """
    workspace_root = skills_root.parent.parent.parent
    out: List[Root] = []
    for sibling in ("neqsim-community-skills", "neqsim-enterprise-skills", "energy-project-agents"):
        cand = workspace_root / sibling / "skills"
        if cand.is_dir():
            out.append((cand, sibling, "**/SKILL.md", False))
    return out


def skill_roots(skills_root: Path) -> List[Root]:
    """Every skill source, primary first."""
    roots: List[Root] = [(skills_root, "neqsim", "*/SKILL.md", False)]
    roots.extend(sibling_skill_roots(skills_root))
    roots.extend((p, "installed", "*/SKILL.md", True) for p in installed_skills_roots())
    roots.extend(plugin_skill_roots())
    return roots


# -------------------------------------------------------------------- skills

def parse_skill_md(skill_md: Path) -> Dict[str, str]:
    """Return ``name``/``description``/``version``/``haystack``, or {} to skip."""
    try:
        text = skill_md.read_text(encoding="utf-8")
    except (OSError, UnicodeDecodeError):
        return {}
    if not text.startswith("---"):
        return {}
    end = text.find("\n---", 3)
    if end < 0:
        return {}
    name = desc = version = ""
    for line in text[3:end].splitlines():
        line = line.rstrip()
        if line.startswith("name:"):
            name = af.strip_yaml_scalar(line[5:])
        elif line.startswith("description:"):
            desc = af.strip_yaml_scalar(line[12:])
        elif line.startswith("version:"):
            version = af.strip_yaml_scalar(line[8:])
    folder = skill_md.parent.name
    name = name or folder
    # The folder name joins the haystack - it improves matching.
    return {"name": name, "description": desc, "version": version,
            "haystack": "{} {} {}".format(name, folder, desc)}


def _root_versions() -> Dict[str, str]:
    return {"plugin:" + plugin_name(p): plugin_version(p)
            for _, p in _plugin_content_dirs("skills", True)}


def skill_entries(skills_root: Path) -> List[Tuple[Entry, str]]:
    """Skills from every source as ``(entry, haystack)``; the first copy of an id wins."""
    versions = _root_versions()
    by_id: Dict[str, Entry] = {}
    out: List[Tuple[Entry, str]] = []
    for root, source, pattern, fallback in skill_roots(skills_root):
        if not root.is_dir():
            continue
        for skill_md in sorted(root.glob(pattern)):
            parsed = parse_skill_md(skill_md)
            if not parsed:
                continue
            # Dedup by front-matter name, not folder name: the neqsim mirror prefixes
            # folders with neqsim-/enterprise- while the sibling repos keep them plain.
            key = parsed["name"].lower()
            if key in by_id:
                by_id[key].other_locations.append(str(skill_md))
                continue
            entry = Entry(
                kind="skill", id=parsed["name"], name=parsed["name"], source=source,
                version=parsed["version"] or versions.get(source, ""),
                location=str(skill_md), description=parsed["description"],
                fallback=fallback)
            by_id[key] = entry
            out.append((entry, parsed["haystack"]))
    return out


def skill_ids(skills_root: Path, extra_folders: Iterable[str] = ()) -> set:
    """Lowercase ids and folder names resolvable from any source."""
    ids = set(str(f).lower() for f in extra_folders)
    for entry, _ in skill_entries(skills_root):
        ids.add(entry.id.lower())
        ids.add(Path(entry.location).parent.name.lower())
    return ids


# -------------------------------------------------------------------- agents

def missing_skills(requires: Iterable[str], known_ids: set) -> List[str]:
    return [s for s in requires if str(s).lower() not in known_ids]


def agent_entries(repo_root: Path, skills_root: Optional[Path] = None,
                  extra: Optional[List[Path]] = None) -> List[Entry]:
    """Agents from every source, each flagged with its unresolved skill dependencies."""
    import agent_search  # lazy: agent_search imports this module

    skills_root = skills_root or repo_root / ".github" / "skills"
    known = skill_ids(skills_root)
    plugin_versions = {"plugin:" + plugin_name(p): plugin_version(p)
                       for _, p in _plugin_content_dirs("com.github.copilot/agents", False)}
    out: List[Entry] = []
    for name, _hay, path, skills, repo, handle in agent_search._load_agents(repo_root, extra):
        fm: Dict[str, object] = {}
        try:
            fm = af.parse_frontmatter(Path(path).read_text(encoding="utf-8"))
        except (OSError, UnicodeDecodeError):
            pass
        miss = missing_skills(skills, known)
        out.append(Entry(
            kind="agent", id=handle, name=name, source=repo,
            version=str(fm.get("version") or plugin_versions.get(repo, "")),
            location=path, description=str(fm.get("description") or ""),
            requires=list(skills), missing=miss, available=not miss,
            fallback=repo == "installed" or repo.startswith("plugin:")))
    return out


def build_registry(repo_root: Path, skills_root: Optional[Path] = None,
                   extra_agents: Optional[List[Path]] = None) -> Dict[str, List[Entry]]:
    skills_root = skills_root or repo_root / ".github" / "skills"
    return {
        "agents": agent_entries(repo_root, skills_root, extra_agents),
        "skills": [e for e, _ in skill_entries(skills_root)],
    }


# ----------------------------------------------------------------------- CLI

def main() -> int:
    repo_root = Path(__file__).resolve().parent.parent
    parser = argparse.ArgumentParser(description="List the shared agent and skill registry.")
    parser.add_argument("--kind", choices=("agents", "skills"), default=None)
    parser.add_argument("--json", action="store_true", help="Emit full entries as JSON")
    parser.add_argument("--unresolved", action="store_true",
                        help="List agents whose declared skills are not found anywhere")
    args = parser.parse_args()

    registry = build_registry(repo_root)
    if args.kind:
        registry = {args.kind: registry[args.kind]}

    if args.unresolved:
        broken = [e for e in registry.get("agents", []) if e.missing]
        if args.json:
            print(json.dumps([asdict(e) for e in broken], indent=2))
        for e in broken:
            print("{} [{}] missing: {}".format(e.id, e.source, ", ".join(e.missing)))
        if not args.json:
            print("{} agents with unresolved skills".format(len(broken)))
        return 0

    if args.json:
        print(json.dumps({k: [asdict(e) for e in v] for k, v in registry.items()}, indent=2))
        return 0

    for kind, entries in registry.items():
        counts: Dict[str, int] = {}
        for e in entries:
            counts[e.source] = counts.get(e.source, 0) + 1
        print("{}: {}".format(kind, len(entries)))
        for source in sorted(counts):
            print("  {:<40} {}".format(source, counts[source]))
    return 0


if __name__ == "__main__":
    sys.exit(main())
