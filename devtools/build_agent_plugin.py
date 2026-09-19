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
      servers/NeqsimMcpLauncher.java   # `java` source-launch: fetch release jar, run it
      servers/neqsim-mcp-server.properties  # pinned server version (from pom <revision>)
      toolkit/                    # core only: devtools/ packaged as neqsim-dev-setup (vendored)
      com.github.copilot/
        agents/<id>.agent.md      # rendered with install_agent.render_vscode_agent
        rules/*.instructions.md   # copied from .github/instructions
        hooks/hooks.json          # SessionStart (sh / PowerShell launchers below)
      scripts/install_skill_packages.{sh,ps1,py}  # pip install the bundled packages (background)
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
import re
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
MCP_SERVERS_DIR = REPO_ROOT / ".github" / "mcp" / "servers"
ROOT_POM = REPO_ROOT / "pom.xml"
CORE_INSTRUCTIONS = REPO_ROOT / ".github" / "instructions"
DEVTOOLS = REPO_ROOT / "devtools"
# Sub-folder of the core plugin that carries the vendored toolkit (devtools/ packaged as
# `neqsim-dev-setup`). The hook pip-installs it from disk, so a new user needs neither
# git nor a clone of the NeqSim repository.
TOOLKIT_SUBDIR = "toolkit"
# Optional interpreter pinned into the hook for site-specific builds (--python).
# Unset by default so the package is portable and the content hash does not
# depend on the build machine; the hook then resolves NEQSIM_PYTHON at run time.
DEFAULT_PYTHON = os.environ.get("NEQSIM_PLUGIN_PYTHON") or ""
# The task-solving toolkit (neqsim CLI, neqsim_dev_setup, runner, validators, report
# generator) is devtools/ packaged as `neqsim-dev-setup`; it pulls the `neqsim` wheel
# so plugin users get a packaged JAR without a source checkout. By default the toolkit
# is vendored into the plugin (TOOLKIT_SUBDIR) and installed from disk. `--toolkit-ref`
# switches to a git requirement on the given ref instead (needs git on the user's PATH).
# Override the whole spec with NEQSIM_TOOLKIT_REQUIREMENT once published to PyPI.
TOOLKIT_REQUIREMENT_TEMPLATE = (
    "neqsim-dev-setup @ git+https://github.com/equinor/neqsim.git@{ref}#subdirectory=devtools")
TOOLKIT_REQUIREMENT = os.environ.get("NEQSIM_TOOLKIT_REQUIREMENT") or TOOLKIT_REQUIREMENT_TEMPLATE
# Placeholder in a pip target that the hook replaces with the plugin root at run time.
PLUGIN_ROOT_TOKEN = "${PLUGIN_ROOT}"
VENDORED_TOOLKIT_REQUIREMENT = PLUGIN_ROOT_TOKEN + "/" + TOOLKIT_SUBDIR
# `--mcp-version` value that makes the launcher follow the newest NeqSim release.
MCP_LATEST = "latest"
SKIP_DIRS = {"__pycache__", ".pytest_cache", "node_modules", ".git"}
SKIP_SUFFIXES = {".pyc"}


class PluginSpec:
    """Where a plugin's content comes from."""

    def __init__(self, name: str, description: str, skills_roots: List[Path],
                 agents_roots: List[Path], rules_roots: List[Path],
                 include_mcp: bool, pip_install_roots: List[Path],
                 pip_targets: Optional[List[str]] = None) -> None:
        self.name = name
        self.description = description
        self.skills_roots = skills_roots
        self.agents_roots = agents_roots
        self.rules_roots = rules_roots
        self.include_mcp = include_mcp
        self.pip_install_roots = pip_install_roots
        # Extra pip requirement specs the SessionStart hook installs (non-editable).
        self.pip_targets = pip_targets or []


def default_specs() -> List[PluginSpec]:
    community_skills = WORKSPACE / "neqsim-community-skills" / "skills"
    community_agents = WORKSPACE / "neqsim-community-agents" / "agents"
    enterprise_skills = WORKSPACE / "neqsim-enterprise-skills" / "skills"
    enterprise_agents = WORKSPACE / "neqsim-enterprise-agents" / "agents"
    return [
        PluginSpec(
            "neqsim",
            "NeqSim core: thermodynamics and process-simulation skills, specialist "
            "agents, the NeqSim MCP server, and the task-solving toolkit (neqsim CLI, "
            "runner, validators, report generator) installed on first session.",
            [REPO_ROOT / ".github" / "skills"], [REPO_ROOT / ".github" / "agents"],
            [CORE_INSTRUCTIONS], True, [],
            pip_targets=[VENDORED_TOOLKIT_REQUIREMENT],
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


HOOK_PY = '''"""SessionStart hook: install this plugin's Python packages.

Targets: the bundled skills (editable, when this plugin ships a pyproject) and
any pinned requirement specs such as the NeqSim task-solving toolkit (vendored
under ${{PLUGIN_ROOT}}/toolkit, so no git clone is needed). Target interpreter, in
order: NEQSIM_PYTHON, a pinned build-time interpreter (if any), the interpreter
running this script. Never selects or creates an environment.

The install runs in a detached background process so the first chat prompt is
not delayed; progress is written to ~/.neqsim/plugin-install/<plugin>/install.log
and a version stamp marks success. Exits 0 on every path so the hook cannot block
a chat session; stdout carries only the JSON that VS Code parses.

When the plugin ships the NeqSim MCP server launcher, the hook also prefetches
the server jar in the background (``java servers/NeqsimMcpLauncher.java
--prefetch``): on the first session this overlaps the ~90 MB download with the
user's first prompt, and afterwards it fetches a newer release once a day so the
next server start is instant. Skipped when java is missing or the cache is fresh.
"""
import json
import os
import subprocess
import sys
import time
from pathlib import Path

PINNED_PYTHON = {pinned!r}
EDITABLE_SELF = {editable_self!r}
REQUIREMENTS = {requirements!r}
PREFETCH_MCP = {prefetch_mcp!r}
LOCK_MAX_AGE_S = 45 * 60
MCP_REFRESH_S = 24 * 3600

root = Path(os.environ.get("PLUGIN_ROOT") or Path(__file__).resolve().parents[1])
plugin_name = json.loads((root / "plugin.json").read_text(encoding="utf-8")).get("name", root.name)
version = json.loads((root / "plugin.json").read_text(encoding="utf-8")).get("version", "")
state = Path.home() / ".neqsim" / "plugin-install" / plugin_name
stamp = state / "installed_version"
lock = state / "install.lock"
log = state / "install.log"
mcp_data = Path(os.environ.get("PLUGIN_DATA") or Path.home() / ".neqsim" / "mcp-server")
mcp_log = state / "mcp-prefetch.log"


def detached(cmd, out=None):
    """Start ``cmd`` fully detached from the hook process (no window, survives exit)."""
    kwargs = dict(stdin=subprocess.DEVNULL, stdout=out or subprocess.DEVNULL,
                  stderr=out or subprocess.DEVNULL, close_fds=True)
    if os.name == "nt":
        # detached + new process group + no window; try to leave the caller's job object too
        flags = 0x00000008 | 0x00000200 | 0x08000000
        try:
            subprocess.Popen(cmd, creationflags=flags | 0x01000000, **kwargs)
            return
        except OSError:
            pass
        kwargs["creationflags"] = flags
    else:
        kwargs["start_new_session"] = True
    subprocess.Popen(cmd, **kwargs)


def mcp_needs_prefetch():
    """True when no server jar is cached or the daily release check is due."""
    if not PREFETCH_MCP or not (root / "servers" / "NeqsimMcpLauncher.java").exists():
        return False
    if os.environ.get("NEQSIM_MCP_JAR"):
        return False
    if not any(mcp_data.glob("neqsim-mcp-server-*-runner.jar")):
        return True
    marker = mcp_data / "latest-release.txt"
    if not marker.exists():
        return False  # pinned version, jar present: nothing to refresh
    try:
        checked_ms = int(marker.read_text(encoding="utf-8").split()[1])
    except (IndexError, ValueError, OSError):
        return True
    return time.time() * 1000 - checked_ms > MCP_REFRESH_S * 1000


def prefetch_mcp():
    """Kick off the launcher's --prefetch in the background; never raises."""
    java = None
    home = os.environ.get("JAVA_HOME")
    if home:
        cand = Path(home) / "bin" / ("java.exe" if os.name == "nt" else "java")
        if cand.exists():
            java = str(cand)
    if java is None:
        from shutil import which
        java = which("java")
    if java is None:
        return False
    try:
        state.mkdir(parents=True, exist_ok=True)
        out = open(str(mcp_log), "a", encoding="utf-8")
        out.write("== {{}} prefetch\\n".format(time.strftime("%Y-%m-%d %H:%M:%S")))
        out.flush()
        detached([java, str(root / "servers" / "NeqsimMcpLauncher.java"), "--root", str(root),
                  "--data", str(mcp_data), "--prefetch"], out)
        return True
    except OSError:
        return False


def pick_python():
    for cand in (os.environ.get("NEQSIM_PYTHON"), PINNED_PYTHON, sys.executable):
        if cand and Path(cand).exists():
            return cand
    return None


def targets():
    for req in REQUIREMENTS:
        yield req.replace("${{PLUGIN_ROOT}}", str(root))

def stamp_text(python):
    """Version + interpreter, so a changed NEQSIM_PYTHON triggers a fresh install."""
    return version + "\\n" + str(Path(python).resolve())

def pip_install(python, req, out):
    """pip install one requirement, preferring a bundled ${{PLUGIN_ROOT}}/wheels folder.

    The offline bundle ships a wheelhouse (toolkit wheel + dependencies); try it
    first with --no-index so an unzipped folder installs without PyPI, then fall
    back to the normal index. A local-directory requirement is installed by its
    distribution name from the wheelhouse, so nothing has to be built offline.
    """
    wheels = root / "wheels"
    if wheels.is_dir() and any(wheels.glob("*.whl")):
        target = req
        pyproject = Path(req) / "pyproject.toml"
        if pyproject.is_file():
            import re
            m = re.search(r'^name\\s*=\\s*["\\']([^"\\']+)', pyproject.read_text(encoding="utf-8"),
                          re.M)
            if m:
                target = m.group(1)
        rc = subprocess.run([python, "-m", "pip", "install", "--no-index", "--find-links",
                             str(wheels), target], stdout=out, stderr=out, check=False).returncode
        if rc == 0:
            return True
        out.write("== wheelhouse install failed (rc={{}}); retrying against the package index\\n"
                  .format(rc))
        out.flush()
    return subprocess.run([python, "-m", "pip", "install", req],
                          stdout=out, stderr=out, check=False).returncode == 0


def run_install(python):
    """Foreground worker (called with --run in the detached process)."""
    state.mkdir(parents=True, exist_ok=True)
    with open(str(log), "a", encoding="utf-8") as out:
        out.write("== {{}} {{}} -> {{}}\\n".format(time.strftime("%Y-%m-%d %H:%M:%S"), plugin_name, python))
        out.flush()
        ok = True
        if EDITABLE_SELF:
            ok &= subprocess.run([python, "-m", "pip", "install", "-e", str(root), "--no-deps"],
                                 stdout=out, stderr=out, check=False).returncode == 0
        for req in targets():
            ok &= pip_install(python, req, out)
        out.write("== {{}} {{}}\\n".format(time.strftime("%Y-%m-%d %H:%M:%S"), "OK" if ok else "FAILED"))
    if ok:
        stamp.write_text(stamp_text(python), encoding="utf-8")
    try:
        lock.unlink()
    except OSError:
        pass
    return 0 if ok else 1


def spawn_worker(python):
    state.mkdir(parents=True, exist_ok=True)
    lock.write_text(str(os.getpid()), encoding="utf-8")
    detached([python, str(Path(__file__).resolve()), "--run"])


def main():
    python = pick_python()
    if "--run" in sys.argv:
        return run_install(python) if python else 1
    mcp_started = mcp_needs_prefetch() and prefetch_mcp()
    if python is None:
        marker = state / "no_python_notified"
        if marker.exists():
            return 0
        state.mkdir(parents=True, exist_ok=True)
        marker.write_text(version, encoding="utf-8")
        print(json.dumps({{"systemMessage": "NeqSim plugin '{{}}': no Python interpreter found "
                          "(set NEQSIM_PYTHON or put python on PATH); MCP tools still work, the task "
                          "toolkit is not installed.".format(plugin_name)}}))
        return 0
    if stamp.exists() and stamp.read_text(encoding="utf-8") == stamp_text(python):
        return 0
    if lock.exists() and time.time() - lock.stat().st_mtime < LOCK_MAX_AGE_S:
        return 0
    spawn_worker(python)
    mcp_note = (" The NeqSim MCP server jar (~90 MB) is downloading alongside; its tools appear "
                "as soon as the server reports running." if mcp_started else "")
    print(json.dumps({{"systemMessage": "NeqSim plugin '{{}}' v{{}}: installing its Python packages into "
                      "{{}} in the background (first session only, a few minutes). Log: {{}}. Run "
                      "/neqsim-setup afterwards to verify.{{}}".format(plugin_name, version, python, log,
                                                                      mcp_note)}}))
    return 0


sys.exit(main())
'''

# POSIX launcher: first interpreter that exists wins; silent no-op otherwise.
HOOK_SH = '''#!/bin/sh
# SessionStart launcher; the Python script picks the pip target interpreter.
root="${PLUGIN_ROOT:-$(cd "$(dirname "$0")/.." && pwd)}"
for py in "$NEQSIM_PYTHON" python3 python; do
  [ -n "$py" ] || continue
  if command -v "$py" >/dev/null 2>&1; then
    exec "$py" "$root/scripts/install_skill_packages.py"
  fi
done
exit 0
'''

# Windows launcher: `py -3` before `python` so the Store alias stub is not hit first.
HOOK_PS1 = '''# SessionStart launcher; the Python script picks the pip target interpreter.
$root = if ($env:PLUGIN_ROOT) { $env:PLUGIN_ROOT } else { Split-Path -Parent $PSScriptRoot }
$script = Join-Path $root "scripts/install_skill_packages.py"
if ($env:NEQSIM_PYTHON -and (Test-Path $env:NEQSIM_PYTHON)) { & $env:NEQSIM_PYTHON $script; exit 0 }
if (Get-Command py -ErrorAction SilentlyContinue) { & py -3 $script; exit 0 }
if (Get-Command python -ErrorAction SilentlyContinue) { & python $script; exit 0 }
exit 0
'''


def write_hooks(dest_root: Path, pip_roots: List[Path], python: str,
                requirements: Optional[List[str]] = None, prefetch_mcp: bool = False) -> None:
    """SessionStart hook that installs the plugin's Python packages.

    The hook is portable: OS-specific launchers (``sh`` / PowerShell) find an
    interpreter at run time (``NEQSIM_PYTHON``, then PATH) and the Python script
    chooses the pip target the same way. ``python`` optionally pins a
    site-specific interpreter as a fallback candidate. ``${PLUGIN_ROOT}`` is
    expanded by the client. Idempotent via a version stamp in ``${PLUGIN_DATA}``.
    With ``prefetch_mcp`` the hook also warms/refreshes the MCP server jar cache.
    """
    requirements = list(requirements or [])
    if not pip_roots and not requirements and not prefetch_mcp:
        return
    hooks_dir = dest_root / "com.github.copilot" / "hooks"
    hooks_dir.mkdir(parents=True, exist_ok=True)
    scripts = dest_root / "scripts"
    scripts.mkdir(parents=True, exist_ok=True)
    (scripts / "install_skill_packages.py").write_text(
        HOOK_PY.format(pinned=python or "", editable_self=bool(pip_roots),
                       requirements=requirements, prefetch_mcp=bool(prefetch_mcp)),
        encoding="utf-8")
    (scripts / "install_skill_packages.sh").write_text(HOOK_SH, encoding="utf-8", newline="\n")
    (scripts / "install_skill_packages.ps1").write_text(HOOK_PS1, encoding="utf-8")
    hooks = {
        "hooks": {
            "SessionStart": [
                {
                    "type": "command",
                    "command": "sh \"${PLUGIN_ROOT}/scripts/install_skill_packages.sh\"",
                    "windows": "powershell -NoProfile -ExecutionPolicy Bypass -File "
                               "\"${PLUGIN_ROOT}/scripts/install_skill_packages.ps1\"",
                    "timeout": 60,
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


def _toml_string_list(text: str, key: str) -> List[str]:
    """Values of a top-level ``key = [ ... ]`` array of strings in a TOML document."""
    match = re.search(r"^\s*" + re.escape(key) + r"\s*=\s*\[(.*?)\]", text, re.S | re.M)
    if not match:
        return []
    return re.findall(r"\"([^\"]+)\"", match.group(1))


def toolkit_files(devtools: Path = DEVTOOLS) -> List[Path]:
    """Files that make up the ``neqsim-dev-setup`` distribution, per its pyproject.

    Only the declared ``py-modules`` and ``packages`` travel (plus ``pyproject.toml``);
    the many one-off maintenance scripts and tests in devtools/ stay behind.
    """
    pyproject = devtools / "pyproject.toml"
    text = pyproject.read_text(encoding="utf-8")
    files = [pyproject]
    for module in _toml_string_list(text, "py-modules"):
        path = devtools / (module + ".py")
        if not path.exists():
            raise SystemExit("toolkit module listed in pyproject is missing: {}".format(path))
        files.append(path)
    for package in _toml_string_list(text, "packages"):
        pkg_dir = devtools.joinpath(*package.split("."))
        if not pkg_dir.is_dir():
            raise SystemExit("toolkit package listed in pyproject is missing: {}".format(pkg_dir))
        for path in sorted(pkg_dir.rglob("*")):
            if path.is_file() and not any(part in SKIP_DIRS for part in path.parts) \
                    and path.suffix not in SKIP_SUFFIXES:
                files.append(path)
    return files


def write_toolkit(dest_root: Path, devtools: Path = DEVTOOLS) -> int:
    """Vendor the toolkit distribution into ``<plugin>/toolkit`` for a local pip install.

    Returns the number of files copied.
    """
    out = dest_root / TOOLKIT_SUBDIR
    count = 0
    for src in toolkit_files(devtools):
        dest = out / src.relative_to(devtools)
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(str(src), str(dest))
        count += 1
    return count


# ----------------------------------------------------------------------------
# hashing / versioning
# ----------------------------------------------------------------------------

def neqsim_release_version() -> str:
    """Release version from the root pom ``<revision>`` (``-SNAPSHOT`` stripped)."""
    match = re.search(r"<revision>\s*([^<\s]+)\s*</revision>", ROOT_POM.read_text(encoding="utf-8"))
    if not match:
        raise SystemExit("cannot read <revision> from {}".format(ROOT_POM))
    return match.group(1).replace("-SNAPSHOT", "")


def write_mcp(staging: Path, mcp_version: str, errors: List[str]) -> None:
    """Copy the canonical mcp.json plus the launcher, pinning (or tracking) the server release.

    ``mcp_version`` is a release such as ``3.21.0`` or ``latest``. With a fixed
    version the launcher downloads ``neqsim-mcp-server-<version>-runner.jar`` from
    that GitHub release on first start; with ``latest`` it resolves the newest
    release itself (see the launcher), so the plugin only needs ``java``.
    """
    if not CANONICAL_MCP.exists():
        errors.append("canonical MCP definition missing: {}".format(CANONICAL_MCP))
        return
    shutil.copy2(str(CANONICAL_MCP), str(staging / "mcp.json"))
    servers_out = staging / "servers"
    if MCP_SERVERS_DIR.is_dir():
        shutil.copytree(str(MCP_SERVERS_DIR), str(servers_out), ignore=_ignore)
    else:
        servers_out.mkdir()
    if mcp_version == MCP_LATEST:
        body = ("# The launcher resolves the newest equinor/neqsim release (checked daily,\n"
                "# cached offline). Pin with NEQSIM_MCP_VERSION=X.Y.Z or a rebuild --mcp-version.\n"
                "version=latest\n")
    else:
        body = ("version={v}\n"
                "jar=neqsim-mcp-server-{v}-runner.jar\n"
                "download=https://github.com/equinor/neqsim/releases/download/v{v}/\n"
                .format(v=mcp_version))
    (servers_out / "neqsim-mcp-server.properties").write_text(
        "# Written by build_agent_plugin.py; read by NeqsimMcpLauncher.java.\n" + body,
        encoding="utf-8")


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
        write_mcp(staging, args.mcp_version, errors)

    pip_targets = list(spec.pip_targets)
    if VENDORED_TOOLKIT_REQUIREMENT in pip_targets:
        if args.toolkit_ref:
            pip_targets = [TOOLKIT_REQUIREMENT.format(ref=args.toolkit_ref)
                           if t == VENDORED_TOOLKIT_REQUIREMENT else t for t in pip_targets]
        else:
            write_toolkit(staging)
    write_hooks(staging, spec.pip_install_roots, args.python, pip_targets,
                prefetch_mcp=spec.include_mcp)
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
    """List every plugin present in ``out_root`` (a ``--only`` build must not drop the others)."""
    built = {r["name"]: r["version"] for r in results if not r["errors"]}
    entries = []
    for spec in default_specs():
        manifest = out_root / spec.name / "plugin.json"
        if spec.name in built:
            version = built[spec.name]
        elif manifest.exists():
            version = json.loads(manifest.read_text(encoding="utf-8")).get("version")
        else:
            continue
        entries.append({"name": spec.name, "version": version,
                        "source": "./{}".format(spec.name), "description": spec.description})
    marketplace = {
        "$schema": MARKETPLACE_SCHEMA,
        "name": "neqsim-copilot-plugin",
        "owner": {"name": "Equinor / NeqSim"},
        "plugins": entries,
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
                        help="optional absolute interpreter pinned into the SessionStart hook as a "
                             "fallback after NEQSIM_PYTHON (default: none; env NEQSIM_PLUGIN_PYTHON)")
    parser.add_argument("--check", action="store_true",
                        help="build to staging only; exit 1 on errors or unbumped changes")
    parser.add_argument("--mcp-version", default=MCP_LATEST,
                        help="NeqSim release whose MCP server jar the plugin downloads: a version "
                             "such as 3.21.0 to pin, or 'latest' (default) to track the newest "
                             "GitHub release; 'pom' takes the root pom <revision>")
    parser.add_argument("--toolkit-ref", default=None,
                        help="install the task toolkit (devtools/) from this git ref of equinor/neqsim "
                             "instead of the copy vendored into the plugin (default: vendored, "
                             "no git needed on the user's machine)")
    args = parser.parse_args(argv)
    if args.mcp_version == "pom":
        args.mcp_version = neqsim_release_version()

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
