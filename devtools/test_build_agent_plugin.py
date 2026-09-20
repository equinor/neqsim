"""Tests for the Agent Plugins 1.0 packager (``build_agent_plugin.py``).

Uses a synthetic mini-repo so the tests do not depend on sibling checkouts.
"""
from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace

sys.path.insert(0, str(Path(__file__).resolve().parent))

import build_agent_plugin as bap  # noqa: E402


def _write(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


def _mini_repo(root: Path) -> bap.PluginSpec:
    skills = root / "skills"
    agents = root / "agents"
    _write(skills / "neqsim-alpha" / "SKILL.md",
           "---\nname: neqsim-alpha\ndescription: A. USE WHEN: x\n---\nbody\n")
    _write(skills / "cat" / "neqsim-beta" / "SKILL.md",
           "---\nname: neqsim-beta\ndescription: B. USE WHEN: y\n---\nbody\n")
    _write(skills / "cat" / "neqsim-beta" / "src" / "beta" / "__init__.py", "")
    _write(skills / "cat" / "neqsim-beta" / "src" / "beta" / "__pycache__" / "x.pyc", "")
    # credential caches a live API run leaves behind must never be packaged
    _write(skills / "cat" / "neqsim-beta" / "token_cache.bin", "DPAPI")
    _write(skills / "cat" / "neqsim-beta" / "src" / "beta" / "token_cache.bin", "DPAPI")
    # live-path extras the way the skills repos declare them (offline-safe import,
    # API clients only in optional groups); one direct-URL spec, one duplicate
    _write(skills / "neqsim-alpha" / "pyproject.toml",
           "[project]\nname = 'alpha'\nversion = '0'\ndependencies = [\"stidapi\", \"requests>=2.28\"]\n"
           "[project.optional-dependencies]\ndev = [\"pytest>=8.0\"]\n")
    _write(skills / "cat" / "neqsim-beta" / "pyproject.toml",
           "[project]\nname = 'beta'\nversion = '0'\ndependencies = [\"alpha\", \"msal\"]\n"
           "[project.optional-dependencies]\ndev = [\"pytest>=8.0\"]\n"
           "live = [\n  \"requests>=2.28\",\n  \"msal>=1.24\",\n"
           "  \"pypdm @ git+https://github.com/equinor/PyPDM.git\",\n]\n"
           "network = [\"pepr-client>=1.0.2\"]\n")
    _write(agents / "demo-agent" / "AGENT.md",
           "---\nname: demo-agent\ndescription: Demo agent.\nrequired_skills:\n- neqsim-alpha\n"
           "- neqsim-missing\n---\nbody\n")
    _write(agents / "core.agent.md",
           "---\nname: core agent\ndescription: Core.\nrequired_skills: []\n---\nbody\n")
    _write(root / "pyproject.toml", "[project]\nname='x'\nversion='0'\n")
    return bap.PluginSpec("demo", "Demo plugin", [skills], [agents], [], False, [root])


def _args(**overrides):
    base = dict(set_version=None, bump=None, check=False, python="", mcp_version="9.9.9",
                toolkit_ref=None)
    base.update(overrides)
    return SimpleNamespace(**base)


class BuildPluginTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        self.spec = _mini_repo(self.root / "src")
        self.out = self.root / "out"

    def tearDown(self):
        self.tmp.cleanup()

    def _build(self, **kw):
        known = {n for n, _ in bap.iter_skills(self.spec.skills_roots)}
        return bap.build_plugin(self.spec, self.out, known, _args(**kw))

    def test_layout_and_manifest(self):
        result = self._build()
        self.assertEqual(result["errors"], [])
        plugin = self.out / "demo"
        manifest = json.loads((plugin / "plugin.json").read_text(encoding="utf-8"))
        self.assertEqual(manifest["$schema"], bap.PLUGIN_SCHEMA)
        self.assertEqual(manifest["name"], "demo")
        self.assertEqual(manifest["version"], "0.1.0")
        # skills flattened to skills/<name>, categories dropped, caches skipped
        self.assertTrue((plugin / "skills" / "neqsim-alpha" / "SKILL.md").exists())
        self.assertTrue((plugin / "skills" / "neqsim-beta" / "src" / "beta" / "__init__.py").exists())
        self.assertFalse((plugin / "skills" / "neqsim-beta" / "src" / "beta" / "__pycache__").exists())
        self.assertEqual(list(plugin.rglob("token_cache.bin")), [])
        # agents rendered as kebab-case ids with frontmatter name == id
        agents_dir = plugin / "com.github.copilot" / "agents"
        self.assertTrue((agents_dir / "demo-agent.agent.md").exists())
        core = (agents_dir / "core.agent.md").read_text(encoding="utf-8")
        self.assertTrue(core.startswith("---\nname: core\n"))
        # hook + packaging files for the editable install
        hooks = json.loads((plugin / "com.github.copilot" / "hooks" / "hooks.json").read_text())
        entry = hooks["hooks"]["SessionStart"][0]
        self.assertIn("scripts/install_skill_packages.sh", entry["command"])
        self.assertIn("agent-plugins/*/*/*/demo", entry["command"])
        # Windows: PowerShell does not expand ${PLUGIN_ROOT} (it is an empty PowerShell
        # variable there), so the root must be resolved inside the command - placeholder,
        # then the PLUGIN_ROOT environment variable, then the plugin's own install folder
        # - never via -File "${PLUGIN_ROOT}/..."
        self.assertNotIn('-File "${PLUGIN_ROOT}', entry["windows"])
        self.assertIn("'${PLUGIN_ROOT}'", entry["windows"])
        self.assertIn("$env:PLUGIN_ROOT", entry["windows"])
        self.assertIn("-eq 'demo'", entry["windows"])
        self.assertIn("scripts/install_skill_packages.ps1", entry["windows"])
        self.assertNotIn("{{", entry["windows"])
        scripts = plugin / "scripts"
        for name in ("install_skill_packages.sh", "install_skill_packages.ps1",
                     "install_skill_packages.py"):
            self.assertTrue((scripts / name).exists(), name)
        hook_py = (scripts / "install_skill_packages.py").read_text(encoding="utf-8")
        # portable by default: nothing from the build machine is baked in
        self.assertIn("PINNED_PYTHON = ''", hook_py)
        self.assertNotIn(sys.executable, hook_py)
        self.assertIn('os.environ.get("NEQSIM_PYTHON")', hook_py)
        # the emitted hook is valid Python and installs in a detached worker
        compile(hook_py, "install_skill_packages.py", "exec")
        self.assertIn('"--run"', hook_py)
        self.assertIn("plugin-install", hook_py)
        self.assertTrue((plugin / "pyproject.toml").exists())
        self.assertEqual(result["skills"], 2)
        self.assertEqual(result["agents"], 2)

    def test_live_requirements_aggregated_and_installed_by_hook(self):
        result = self._build()
        self.assertEqual(result["errors"], [])
        plugin = self.out / "demo"
        req_file = plugin / bap.LIVE_REQUIREMENTS_FILE
        self.assertTrue(req_file.exists())
        lines = [l for l in req_file.read_text(encoding="utf-8").splitlines()
                 if l and not l.startswith("#")]
        # dependencies + live + network, dev excluded, direct URL -> name, the sibling
        # skill 'alpha' dropped (the editable root install provides it), bare 'msal'
        # superseded by its specified form
        self.assertEqual(lines, ["msal>=1.24", "pepr-client>=1.0.2", "pypdm", "requests>=2.28",
                                 "stidapi"])
        self.assertNotIn("pytest", req_file.read_text(encoding="utf-8"))
        self.assertIn("git+https://github.com/equinor/PyPDM.git", req_file.read_text(encoding="utf-8"))
        self.assertEqual(result["live_requirements"], 5)
        hook_py = (plugin / "scripts" / "install_skill_packages.py").read_text(encoding="utf-8")
        self.assertIn("LIVE_REQUIREMENTS = 'requirements-live.txt'", hook_py)
        self.assertIn("IMPORT_OK=", hook_py)
        compile(hook_py, "install_skill_packages.py", "exec")
        manifest = json.loads((plugin / "BUILD_MANIFEST.json").read_text(encoding="utf-8"))
        self.assertEqual(manifest["live_requirements"], 5)

    def test_core_plugin_has_no_live_requirements(self):
        spec = bap.PluginSpec("core", "Core", self.spec.skills_roots, self.spec.agents_roots, [],
                              False, [], pip_targets=[bap.VENDORED_TOOLKIT_REQUIREMENT])
        known = {n for n, _ in bap.iter_skills(spec.skills_roots)}
        result = bap.build_plugin(spec, self.out, known, _args())
        self.assertEqual(result["errors"], [])
        self.assertFalse((self.out / "core" / bap.LIVE_REQUIREMENTS_FILE).exists())
        hook_py = (self.out / "core" / "scripts" / "install_skill_packages.py").read_text(
            encoding="utf-8")
        self.assertIn("LIVE_REQUIREMENTS = ''", hook_py)

    def test_vendored_toolkit(self):
        spec = bap.PluginSpec("core", "Core", self.spec.skills_roots, self.spec.agents_roots, [],
                              False, [], pip_targets=[bap.VENDORED_TOOLKIT_REQUIREMENT])
        known = {n for n, _ in bap.iter_skills(spec.skills_roots)}
        result = bap.build_plugin(spec, self.out, known, _args())
        self.assertEqual(result["errors"], [])
        toolkit = self.out / "core" / bap.TOOLKIT_SUBDIR
        self.assertTrue((toolkit / "pyproject.toml").exists())
        self.assertTrue((toolkit / "neqsim_cli.py").exists())
        self.assertTrue((toolkit / "neqsim_runner" / "__init__.py").exists())
        self.assertTrue((toolkit / "task_template" / "step3_report" / "generate_report.py").exists())
        self.assertFalse((toolkit / "test_new_task.py").exists())
        self.assertFalse((toolkit / "unisim_reader.py").exists())
        hook_py = (self.out / "core" / "scripts" / "install_skill_packages.py").read_text(
            encoding="utf-8")
        self.assertIn("'${PLUGIN_ROOT}/toolkit'", hook_py)
        self.assertNotIn("git+https", hook_py)

    def test_toolkit_ref_switches_to_git_requirement(self):
        spec = bap.PluginSpec("core", "Core", self.spec.skills_roots, self.spec.agents_roots, [],
                              False, [], pip_targets=[bap.VENDORED_TOOLKIT_REQUIREMENT])
        known = {n for n, _ in bap.iter_skills(spec.skills_roots)}
        result = bap.build_plugin(spec, self.out, known, _args(toolkit_ref="v9.9.9"))
        self.assertEqual(result["errors"], [])
        self.assertFalse((self.out / "core" / bap.TOOLKIT_SUBDIR).exists())
        hook_py = (self.out / "core" / "scripts" / "install_skill_packages.py").read_text(
            encoding="utf-8")
        self.assertIn("neqsim.git@v9.9.9#subdirectory=devtools", hook_py)

    def test_core_plugin_installs_and_verifies_neqsim_pypi_package(self):
        spec = bap.PluginSpec("core", "Core", self.spec.skills_roots, self.spec.agents_roots, [],
                              False, [],
                              pip_targets=[bap.VENDORED_TOOLKIT_REQUIREMENT, "neqsim"],
                              verify_imports=["neqsim"])
        known = {n for n, _ in bap.iter_skills(spec.skills_roots)}
        result = bap.build_plugin(spec, self.out, known, _args())
        self.assertEqual(result["errors"], [])
        hook_py = (self.out / "core" / "scripts" / "install_skill_packages.py").read_text(
            encoding="utf-8")
        self.assertIn("'${PLUGIN_ROOT}/toolkit'", hook_py)
        self.assertIn("'neqsim'", hook_py)
        self.assertIn("VERIFY_IMPORTS = ['neqsim']", hook_py)
        compile(hook_py, "install_skill_packages.py", "exec")

    def test_default_specs_core_plugin_verifies_neqsim(self):
        core = next(s for s in bap.default_specs() if s.name == "neqsim")
        self.assertIn("neqsim", core.pip_targets)
        self.assertEqual(core.verify_imports, ["neqsim"])

    def test_unresolved_required_skill_is_a_warning(self):
        result = self._build()
        self.assertTrue(any("neqsim-missing" in w for w in result["warnings"]))

    def test_version_gate(self):
        self.assertEqual(self._build()["errors"], [])
        # unchanged -> no bump needed
        second = self._build()
        self.assertFalse(second["changed"])
        self.assertEqual(second["version"], "0.1.0")
        # change content without bump -> error, output untouched
        _write(self.root / "src" / "skills" / "neqsim-alpha" / "SKILL.md",
               "---\nname: neqsim-alpha\ndescription: A2. USE WHEN: x\n---\nbody\n")
        third = self._build()
        self.assertTrue(any("not bumped" in e for e in third["errors"]))
        manifest = json.loads((self.out / "demo" / "plugin.json").read_text(encoding="utf-8"))
        self.assertEqual(manifest["version"], "0.1.0")
        # bump -> accepted
        fourth = self._build(bump="minor")
        self.assertEqual(fourth["errors"], [])
        self.assertEqual(fourth["version"], "0.2.0")

    def test_skill_dir_name_mismatch_is_error(self):
        _write(self.root / "src" / "skills" / "wrong-dir" / "SKILL.md",
               "---\nname: neqsim-gamma\ndescription: G. USE WHEN: z\n---\nbody\n")
        result = self._build()
        self.assertTrue(any("wrong-dir" in e for e in result["errors"]))
        self.assertFalse((self.out / "demo").exists())

    def test_mcp_plugin_tracks_latest_and_prefetches(self):
        spec = bap.PluginSpec("core", "Core", self.spec.skills_roots, self.spec.agents_roots, [],
                              True, [], pip_targets=[bap.VENDORED_TOOLKIT_REQUIREMENT])
        known = {n for n, _ in bap.iter_skills(spec.skills_roots)}
        result = bap.build_plugin(spec, self.out, known, _args(mcp_version=bap.MCP_LATEST))
        self.assertEqual(result["errors"], [])
        plugin = self.out / "core"
        self.assertTrue((plugin / "mcp.json").exists())
        self.assertTrue((plugin / "servers" / "NeqsimMcpLauncher.java").exists())
        props = (plugin / "servers" / "neqsim-mcp-server.properties").read_text(encoding="utf-8")
        self.assertIn("version=latest\n", props)
        # a tracked build must not carry a fixed jar name the launcher would trust
        self.assertNotIn("jar=", props)
        self.assertNotIn("download=", props)
        hook_py = (plugin / "scripts" / "install_skill_packages.py").read_text(encoding="utf-8")
        compile(hook_py, "install_skill_packages.py", "exec")
        self.assertIn("PREFETCH_MCP = True", hook_py)
        self.assertIn('"--prefetch"', hook_py)
        self.assertIn("latest-release.txt", hook_py)
        # offline bundle: wheelhouse first, package index as fallback
        self.assertIn('"--no-index", "--find-links"', hook_py)
        self.assertIn('root / "wheels"', hook_py)

    def test_mcp_plugin_pinned_version(self):
        errors = []
        staging = self.out / "pinned"
        staging.mkdir(parents=True)
        bap.write_mcp(staging, "3.21.0", errors)
        self.assertEqual(errors, [])
        props = (staging / "servers" / "neqsim-mcp-server.properties").read_text(encoding="utf-8")
        self.assertIn("version=3.21.0\n", props)
        self.assertIn("jar=neqsim-mcp-server-3.21.0-runner.jar\n", props)
        self.assertIn("download=https://github.com/equinor/neqsim/releases/download/v3.21.0/\n", props)

    def test_hook_without_mcp_has_prefetch_disabled(self):
        self._build()
        hook_py = (self.out / "demo" / "scripts" / "install_skill_packages.py").read_text(
            encoding="utf-8")
        self.assertIn("PREFETCH_MCP = False", hook_py)


class BumpTest(unittest.TestCase):
    def test_bump(self):
        self.assertEqual(bap.bump("1.2.3", "patch"), "1.2.4")
        self.assertEqual(bap.bump("1.2.3", "minor"), "1.3.0")
        self.assertEqual(bap.bump("1.2.3", "major"), "2.0.0")


if __name__ == "__main__":
    unittest.main()
