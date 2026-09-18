#!/usr/bin/env python
"""Build VS Code / Copilot CLI **Agent Plugins 1.0** packages from the NeqSim repos.

One plugin is emitted per source (core, community, enterprise) so public and
plant-specific content never share a package, plus a ``marketplace.json`` that
lists them so a single ``chat.plugins.marketplaces`` entry exposes all three.

Layout emitted per plugin (https://agent-plugins.org):

    <out>/<plugin>/
      plugin.json                 # $schema + name/version/description
      skills/<name>/SKILL.md ...  # portable; folder name == frontmatter name
      mcp.json                    # portable; copied from .github/mcp/mcp.json
      com.github.copilot/
        agents/<id>.agent.md      # rendered with install_agent.render_vscode_agent
        rules/*.instructions.md   # copied from .github/instructions
        hooks/hooks.json          # SessionStart: pip install -e the skills repo
      automations/                # reserved (empty)
      BUILD_MANIFEST.json         # content hash + inputs, drives the version gate

Sources of truth are the existing repos; nothing is hand-edited in the output.
Agents are read through ``agent_frontmatter`` and skills are copied verbatim,
so this script stays a thin packager.

Version gate: ``plugin.json`` ``version`` is read from ``<out>/<plugin>/plugin.json``
when present. If the content hash changed but the version did not, the build
fails unless ``--bump {patch,minor,major}`` or ``--set-version X.Y.Z`` is given.
This mirrors VS Code's update rule (a plugin only updates on a version change).

Usage:
    python devtools/build_agent_plugin.py --out ../neqsim-copilot-plugin
    python devtools/build_agent_plugin.py --out ../neqsim-copilot-plugin --bump patch
    python devtools/build_agent_plugin.py --out ../neqsim-copilot-plugin --check   # CI
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import shutil
import sys
from pathlib import Path
from typing import Dict, Iterable, List, Optional, Tuple

sys.path.insert(0, str(Path(__file__).resolve().parent))

import agent_frontmatter as af  # noqa: E402
import install_agent  # noqa: E402

REPO_ROOT = Path(__file__).resolve().parent.parent
WORKSPACE = REPO_ROOT.parent
PLUGIN_SCHEMA = "https://agent-plugins.org/schemas/1.0.0/plugin.schema.json"
MARKETPLACE_SCHEMA = "https://agent-plugins.org/schemas/1.0.0/marketplace.schema.json"
CANONICAL_MCP = REPO_ROOT / ".github" / "mcp" / "mcp.json"
CORE_INSTRUCTIONS = REPO_ROOT / ".github" / "instructions"
# Shared NeqSim interpreter (see AGENTS.md "Python Environment Reuse"). Baked
# into the hook at build time; override with --python or NEQSIM_PYTHON.
DEFAULT_PYTHON = os.environ.get("NEQSIM_PYTHON") or sys.executable
SKIP_DIRS = {"__pycache__", ".pytest_cache", "node_modules", ".git"}
SKIP_SUFFIXES = {".pyc"}


class PluginSpec:
    """Where a plugin's content comes from."""

    def __init__(self, name: str, description: str, skills_roots: List[Path],
                 agents_roots: List[Path], rules_roots: List[Path],
                 include_mcp: bool, pip_install_roots: List[Path]) -> None:
        self.name = name
        self.description = description
        self.skills_roots = skills_roots
        self.agents_roots = agents_roots
        self.rules_roots = rules_roots
        self.include_mcp = include_mcp
        self.pip_install_roots = pip_install_roots


def default_specs() -> List[PluginSpec]:
    community_skills = WORKSPACE / "neqsim-community-skills" / "skills"
    community_agents = WORKSPACE / "neqsim-community-agents" / "agents"
    enterprise_skills = WORKSPACE / "neqsim-enterprise-skills" / "skills"
    enterprise_agents = WORKSPACE / "neqsim-enterprise-agents" / "agents"
    return [
        PluginSpec(
            "neqsim",
            "NeqSim core: thermodynamics and process-simulation skills, specialist "
            "agents, and the NeqSim MCP server.",
            [REPO_ROOT / ".github" / "skills"], [REPO_ROOT / ".github" / "agents"],
            [CORE_INSTRUCTIONS], True, [],
        ),
        PluginSpec(
            "neqsim-community",
            "Public NeqSim community skills and agents (screening-level engineering "
            "methods with runnable Python packages).",
            [community_skills], [community_agents], [], False,
            [community_skills.parent],
        ),
        PluginSpec(
            "neqsim-enterprise",
            "Enterprise-only NeqSim skills and agents (governed data sources and "
            "company policies). Internal use only.",
            [enterprise_skills], [enterprise_agents], [], False,
            [enterprise_skills.parent],
        ),
    ]


# ----------------------------------------------------------------------------
# discovery
# ----------------------------------------------------------------------------

def iter_skills(roots: Iterable[Path]) -> Iterable[Tuple[str, Path]]:
    """Yield ``(name, skill_dir)`` for every SKILL.md under the given roots.

    Handles both flat (``skills/<name>/SKILL.md``) and categorised
    (``skills/<category>/<name>/SKILL.md``) layouts.
    """
    for root in roots:
        if not root.is_dir():
            continue
        for skill_md in sorted(list(root.glob("*/SKILL.md")) + list(root.glob("*/*/SKILL.md"))):
            fm = af.parse_frontmatter(skill_md.read_text(encoding="utf-8"))
            name = fm.get("name")
            if not isinstance(name, str) or not name:
                raise SystemExit("skill without frontmatter name: {}".format(skill_md))
            yield name, skill_md.parent


def iter_agents(roots: Iterable[Path]) -> Iterable[Tuple[str, Path]]:
    for root in roots:
        for md in af.iter_agent_files(root):
            yield af.agent_id_for_path(md), md


# ----------------------------------------------------------------------------
# copying
# ----------------------------------------------------------------------------

def _ignore(_dir: str, names: List[str]) -> List[str]:
    return [n for n in names if n in SKIP_DIRS or n.endswith(".egg-info")
            or Path(n).suffix in SKIP_SUFFIXES]


def copy_skill(name: str, source: Path, dest_root: Path, errors: List[str]) -> None:
    if source.name != name:
        errors.append("skill dir '{}' != name '{}' ({})".format(source.name, name, source))
    dest = dest_root / name
    if dest.exists():
        errors.append("duplicate skill name '{}' from {}".format(name, source))
        return
    shutil.copytree(str(source), str(dest), ignore=_ignore)


def write_agent(agent_id: str, source: Path, dest_root: Path, known_skills: set,
                errors: List[str], warnings: List[str]) -> None:
    if not af.KEBAB_RE.match(agent_id):
        errors.append("agent id '{}' is not kebab-case ({})".format(agent_id, source))
    dest = dest_root / (agent_id + ".agent.md")
    if dest.exists():
        errors.append("duplicate agent id '{}' from {}".format(agent_id, source))
        return
    content = install_agent.render_vscode_agent(agent_id, source)
    for skill in af.extract_required_skills(content):
        if skill not in known_skills:
            warnings.append("{}: required skill '{}' not in any bundled plugin".format(
                agent_id, skill))
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_text(content, encoding="utf-8")


def write_hooks(dest_root: Path, pip_roots: List[Path], python: str) -> None:
    """SessionStart hook that makes the skills' Python packages importable.

    ``${PLUGIN_ROOT}`` is expanded by the client; the interpreter comes from
    ``NEQSIM_PYTHON`` or the shared NeqSim environment. The hook is idempotent
    (``pip install -e`` on an unchanged tree is a no-op).
    """
    if not pip_roots:
        return
    hooks_dir = dest_root / "com.github.copilot" / "hooks"
    hooks_dir.mkdir(parents=True, exist_ok=True)
    script = dest_root / "scripts" / "install_skill_packages.py"
    script.parent.mkdir(parents=True, exist_ok=True)
    script.write_text(
        '"""SessionStart hook: install this plugin\'s skill packages (editable).\n\n'
        "Uses NEQSIM_PYTHON if set, else the shared NeqSim interpreter; never\n"
        "selects or creates an environment. Skips silently when the interpreter\n"
        "is unavailable so a missing Python never blocks the chat session.\n"
        '"""\n'
        "import os, subprocess, sys\n"
        "from pathlib import Path\n\n"
        "root = Path(os.environ.get('PLUGIN_ROOT') or Path(__file__).resolve().parents[1])\n"
        "python = os.environ.get('NEQSIM_PYTHON') or r'{python}'\n"
        "if not Path(python).exists():\n"
        "    sys.exit(0)\n"
        "stamp = Path(os.environ.get('PLUGIN_DATA') or root) / '.skills_installed_version'\n"
        "version = (root / 'plugin.json').read_text(encoding='utf-8')\n"
        "if stamp.exists() and stamp.read_text(encoding='utf-8') == version:\n"
        "    sys.exit(0)\n"
        "subprocess.run([python, '-m', 'pip', 'install', '-e', str(root), '--no-deps', '-q'],\n"
        "               check=False)\n"
        "stamp.parent.mkdir(parents=True, exist_ok=True)\n"
        "stamp.write_text(version, encoding='utf-8')\n".format(python=python),
        encoding="utf-8",
    )
    # Hooks run through a shell, so the launcher must be the shared absolute
    # interpreter (never bare `python`); the script re-reads NEQSIM_PYTHON for pip.
    hooks = {
        "hooks": {
            "SessionStart": [
                {
                    "type": "command",
                    "command": "\"{python}\" \"${{PLUGIN_ROOT}}/scripts/install_skill_packages.py\"".format(
                        python=python),
                }
            ]
        }
    }
    (hooks_dir / "hooks.json").write_text(json.dumps(hooks, indent=2) + "\n", encoding="utf-8")
    # The editable install needs the repo's packaging files beside skills/
    # (pyproject.toml declares readme = "README.md", so it must travel too).
    for pip_root in pip_roots:
        for name in ("pyproject.toml", "setup.py", "README.md"):
            src = pip_root / name
            if src.exists():
                shutil.copy2(str(src), str(dest_root / name))


# ----------------------------------------------------------------------------
# hashing / versioning
# ----------------------------------------------------------------------------

def content_hash(root: Path) -> str:
    digest = hashlib.sha256()
    for path in sorted(p for p in root.rglob("*") if p.is_file()):
        if path.name in ("plugin.json", "BUILD_MANIFEST.json"):
            continue
        digest.update(path.relative_to(root).as_posix().encode("utf-8"))
        digest.update(path.read_bytes())
    return digest.hexdigest()


def bump(version: str, part: str) -> str:
    major, minor, patch = (int(x) for x in version.split("."))
    if part == "major":
        return "{}.0.0".format(major + 1)
    if part == "minor":
        return "{}.{}.0".format(major, minor + 1)
    return "{}.{}.{}".format(major, minor, patch + 1)


def previous_state(plugin_dir: Path) -> Tuple[Optional[str], Optional[str]]:
    manifest = plugin_dir / "BUILD_MANIFEST.json"
    plugin_json = plugin_dir / "plugin.json"
    version = None
    digest = None
    if plugin_json.exists():
        version = json.loads(plugin_json.read_text(encoding="utf-8")).get("version")
    if manifest.exists():
        digest = json.loads(manifest.read_text(encoding="utf-8")).get("content_sha256")
    return version, digest


# ----------------------------------------------------------------------------
# build
# ----------------------------------------------------------------------------

def build_plugin(spec: PluginSpec, out_root: Path, known_skills: set, args) -> Dict[str, object]:
    plugin_dir = out_root / spec.name
    old_version, old_digest = previous_state(plugin_dir)
    staging = out_root / (".staging-" + spec.name)
    if staging.exists():
        shutil.rmtree(str(staging))
    staging.mkdir(parents=True)

    errors: List[str] = []
    warnings: List[str] = []

    skills_out = staging / "skills"
    skills_out.mkdir()
    skill_count = 0
    for name, source in iter_skills(spec.skills_roots):
        copy_skill(name, source, skills_out, errors)
        skill_count += 1

    agents_out = staging / "com.github.copilot" / "agents"
    agent_count = 0
    for agent_id, source in iter_agents(spec.agents_roots):
        write_agent(agent_id, source, agents_out, known_skills, errors, warnings)
        agent_count += 1

    for rules_root in spec.rules_roots:
        if rules_root.is_dir():
            rules_out = staging / "com.github.copilot" / "rules"
            rules_out.mkdir(parents=True, exist_ok=True)
            for rule in sorted(rules_root.glob("*.instructions.md")):
                shutil.copy2(str(rule), str(rules_out / rule.name))

    if spec.include_mcp:
        if not CANONICAL_MCP.exists():
            errors.append("canonical MCP definition missing: {}".format(CANONICAL_MCP))
        else:
            shutil.copy2(str(CANONICAL_MCP), str(staging / "mcp.json"))

    write_hooks(staging, spec.pip_install_roots, args.python)
    (staging / "automations").mkdir(exist_ok=True)

    digest = content_hash(staging)
    version = args.set_version or old_version or "0.1.0"
    changed = old_digest != digest
    if changed and old_digest is not None and not args.set_version:
        if args.bump:
            version = bump(old_version or "0.1.0", args.bump)
        else:
            errors.append(
                "{}: content changed but version {} was not bumped "
                "(use --bump patch|minor|major or --set-version)".format(spec.name, version))

    plugin_json = {
        "$schema": PLUGIN_SCHEMA,
        "name": spec.name,
        "version": version,
        "description": spec.description,
        "author": {"name": "Equinor / NeqSim"},
        "homepage": "https://github.com/equinor/neqsim",
        "repository": "https://github.com/equinor/neqsim",
        "license": "Apache-2.0" if spec.name != "neqsim-enterprise" else "LicenseRef-Internal",
        "keywords": ["neqsim", "thermodynamics", "process-simulation", "oil-and-gas"],
    }
    (staging / "plugin.json").write_text(json.dumps(plugin_json, indent=2) + "\n", encoding="utf-8")
    (staging / "BUILD_MANIFEST.json").write_text(json.dumps({
        "content_sha256": digest,
        "skills": skill_count,
        "agents": agent_count,
        "sources": [str(p) for p in spec.skills_roots + spec.agents_roots],
    }, indent=2) + "\n", encoding="utf-8")

    result = {"name": spec.name, "version": version, "skills": skill_count,
              "agents": agent_count, "changed": changed, "errors": errors,
              "warnings": warnings, "sha256": digest}
    if errors or args.check:
        shutil.rmtree(str(staging))
        return result
    if plugin_dir.exists():
        shutil.rmtree(str(plugin_dir))
    staging.rename(plugin_dir)
    return result


def write_marketplace(out_root: Path, results: List[Dict[str, object]]) -> None:
    marketplace = {
        "$schema": MARKETPLACE_SCHEMA,
        "name": "neqsim-copilot-plugin",
        "owner": {"name": "Equinor / NeqSim"},
        "plugins": [
            {
                "name": r["name"],
                "version": r["version"],
                "source": "./{}".format(r["name"]),
                "description": next(s.description for s in default_specs() if s.name == r["name"]),
            }
            for r in results if not r["errors"]
        ],
    }
    (out_root / ".claude-plugin").mkdir(exist_ok=True)
    text = json.dumps(marketplace, indent=2) + "\n"
    (out_root / ".claude-plugin" / "marketplace.json").write_text(text, encoding="utf-8")
    (out_root / "marketplace.json").write_text(text, encoding="utf-8")


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--out", type=Path, default=WORKSPACE / "neqsim-copilot-plugin")
    parser.add_argument("--only", nargs="*", help="plugin names to build (default all)")
    parser.add_argument("--bump", choices=["patch", "minor", "major"])
    parser.add_argument("--set-version")
    parser.add_argument("--python", default=DEFAULT_PYTHON,
                        help="absolute interpreter baked into the SessionStart hook")
    parser.add_argument("--check", action="store_true",
                        help="build to staging only; exit 1 on errors or unbumped changes")
    args = parser.parse_args(argv)

    specs = [s for s in default_specs() if not args.only or s.name in args.only]
    specs = [s for s in specs if any(r.is_dir() for r in s.skills_roots + s.agents_roots)]
    known_skills = {name for s in specs for name, _ in iter_skills(s.skills_roots)}

    out_root = args.out.resolve()
    out_root.mkdir(parents=True, exist_ok=True)
    results = [build_plugin(spec, out_root, known_skills, args) for spec in specs]
    if not args.check:
        write_marketplace(out_root, results)

    failed = False
    for r in results:
        status = "ERROR" if r["errors"] else ("changed" if r["changed"] else "unchanged")
        print("{:<20} v{:<8} skills={:<4} agents={:<4} {}".format(
            r["name"], r["version"], r["skills"], r["agents"], status))
        for w in r["warnings"]:
            print("  WARN  {}".format(w))
        for e in r["errors"]:
            print("  ERROR {}".format(e))
            failed = True
    print("output: {}".format(out_root))
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
