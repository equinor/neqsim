"""Tests for the Agent Plugins 1.0 packager (``build_agent_plugin.py``).

Uses a synthetic mini-repo so the tests do not depend on sibling checkouts.
"""
from __future__ import annotations

import base64
import io
import json
import os
import re
import sys
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest import mock

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
    # setuptools output from `pip install <skill>` duplicates the package; never shipped
    _write(skills / "cat" / "neqsim-beta" / "build" / "lib" / "beta" / "__init__.py", "")
    _write(skills / "cat" / "neqsim-beta" / "dist" / "beta-0.tar.gz", "")
    # live-path extras the way the skills repos declare them (offline-safe import,
    # API clients only in optional groups); one direct-URL spec, one duplicate
    _write(skills / "neqsim-alpha" / "pyproject.toml",
           "[project]\nname = 'alpha'\nversion = '0'\ndependencies = [\"stidapi\", \"requests>=2.28\"]\n"
           "[project.optional-dependencies]\ndev = [\"pytest>=8.0\"]\n")
    _write(skills / "cat" / "neqsim-beta" / "pyproject.toml",
           "[project]\nname = 'beta'\nversion = '0'\ndependencies = [\"alpha\", \"msal\", \"neqsim-skill-gamma\"]\n"
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
        self.assertFalse((plugin / "skills" / "neqsim-beta" / "build").exists())
        self.assertFalse((plugin / "skills" / "neqsim-beta" / "dist").exists())
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
        # Windows: whatever resolves the hooks.json ``windows`` string before running
        # it strips any literal ``$identifier`` token (not just ``${PLUGIN_ROOT}``),
        # mangling an inline ``-Command "..."`` script. The command must therefore
        # carry no literal ``$`` at all - the real script travels as a base64
        # (UTF-16LE) ``-EncodedCommand`` payload, decoded here for assertions.
        self.assertNotIn('-File "${PLUGIN_ROOT}', entry["windows"])
        self.assertNotIn("$", entry["windows"])
        match = re.search(r"-EncodedCommand (\S+)", entry["windows"])
        self.assertIsNotNone(match, entry["windows"])
        decoded = base64.b64decode(match.group(1)).decode("utf-16-le")
        self.assertIn("$env:PLUGIN_ROOT", decoded)
        self.assertIn("$env:CLAUDE_PLUGIN_ROOT", decoded)
        self.assertIn("-eq 'demo'", decoded)
        # The worker must be the .py: a Group Policy execution policy blocks an
        # unsigned .ps1 even under -ExecutionPolicy Bypass.
        self.assertIn("scripts/install_skill_packages.py", decoded)
        self.assertNotIn("install_skill_packages.ps1", decoded)
        # Python off PATH is still found; the Store alias stub is skipped.
        self.assertIn("-notlike '*\\WindowsApps\\*'", decoded)
        self.assertIn("Programs\\Python\\Python3*\\python.exe", decoded)
        self.assertNotIn("{{", decoded)
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
        # dependencies + live + network, dev excluded, the direct URL moved to the internal file,
        # the sibling skill 'alpha' dropped (the editable root install provides it), bare
        # 'msal' superseded by its specified form
        self.assertEqual(lines, ["msal>=1.24", "pepr-client>=1.0.2", "requests>=2.28", "stidapi"])
        self.assertNotIn("pytest", req_file.read_text(encoding="utf-8"))
        internal = [l for l in (plugin / bap.INTERNAL_REQUIREMENTS_FILE).read_text(
            encoding="utf-8").splitlines() if l and not l.startswith("#")]
        self.assertEqual(internal, ["pypdm @ git+https://github.com/equinor/PyPDM.git"])
        self.assertEqual(result["live_requirements"], 4)
        hook_py = (plugin / "scripts" / "install_skill_packages.py").read_text(encoding="utf-8")
        self.assertIn("LIVE_REQUIREMENTS = 'requirements-live.txt'", hook_py)
        self.assertIn("install_internal_requirements(python, out)", hook_py)
        self.assertIn("IMPORT_OK=", hook_py)
        compile(hook_py, "install_skill_packages.py", "exec")
        manifest = json.loads((plugin / "BUILD_MANIFEST.json").read_text(encoding="utf-8"))
        self.assertEqual(manifest["live_requirements"], 4)

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

    def test_windows_live_dependency_reaches_plugin_installer(self):
        skill = self.spec.skills_roots[0] / "cat" / "neqsim-beta"
        _write(skill / "pyproject.toml",
               "[project]\nname = 'beta'\nversion = '0'\ndependencies = []\n"
               "[project.optional-dependencies]\n"
               "live = [\"pywin32>=306; sys_platform == 'win32'\"]\n"
               "outlook = [\"pywin32>=306; sys_platform == 'win32'\"]\n")
        result = self._build()
        self.assertEqual(result["errors"], [])
        plugin = self.out / "demo"
        requirements = (plugin / bap.LIVE_REQUIREMENTS_FILE).read_text(encoding="utf-8")
        self.assertEqual(requirements.count("pywin32>=306; sys_platform == 'win32'"), 1)
        hook = (plugin / "scripts" / "install_skill_packages.py").read_text(encoding="utf-8")
        self.assertIn("LIVE_REQUIREMENTS = 'requirements-live.txt'", hook)
        self.assertIn('base + ["-r", str(req_file)]', hook)

    def test_vendored_toolkit(self):
        spec = bap.PluginSpec("core", "Core", self.spec.skills_roots, self.spec.agents_roots,
                              [bap.CORE_INSTRUCTIONS],
                              False, [], pip_targets=[bap.VENDORED_TOOLKIT_REQUIREMENT])
        known = {n for n, _ in bap.iter_skills(spec.skills_roots)}
        result = bap.build_plugin(spec, self.out, known, _args())
        self.assertEqual(result["errors"], [])
        toolkit = self.out / "core" / bap.TOOLKIT_SUBDIR
        self.assertTrue((toolkit / "pyproject.toml").exists())
        self.assertTrue((toolkit / "neqsim_cli.py").exists())
        self.assertTrue((toolkit / "docker_runtime.py").exists())
        self.assertTrue((toolkit / "neqsim_runner" / "__init__.py").exists())
        self.assertTrue((toolkit / "task_template" / "step3_report" / "generate_report.py").exists())
        self.assertFalse((toolkit / "test_new_task.py").exists())
        self.assertFalse((toolkit / "build_agent_plugin.py").exists())
        self.assertTrue((toolkit / "unisim_reader.py").exists())
        rule = self.out / "core" / "com.github.copilot" / "rules" / "neqsim-calculation-routing.instructions.md"
        self.assertIn("neqsim docker ensure --linux", rule.read_text(encoding="utf-8"))
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
                              verify_imports=["neqsim", "neqsim_cli", "neqsim_runner"])
        known = {n for n, _ in bap.iter_skills(spec.skills_roots)}
        result = bap.build_plugin(spec, self.out, known, _args())
        self.assertEqual(result["errors"], [])
        hook_py = (self.out / "core" / "scripts" / "install_skill_packages.py").read_text(
            encoding="utf-8")
        self.assertIn("'${PLUGIN_ROOT}/toolkit'", hook_py)
        self.assertIn("'neqsim'", hook_py)
        self.assertIn("VERIFY_IMPORTS = ['neqsim', 'neqsim_cli', 'neqsim_runner']", hook_py)
        compile(hook_py, "install_skill_packages.py", "exec")

    def test_default_specs_core_plugin_verifies_neqsim(self):
        core = next(s for s in bap.default_specs() if s.name == "neqsim")
        self.assertIn("neqsim", core.pip_targets)
        self.assertEqual(core.verify_imports, ["neqsim", "neqsim_cli", "neqsim_runner"])

    def test_unresolved_required_skill_is_a_warning(self):
        result = self._build()
        self.assertTrue(any("neqsim-missing" in w for w in result["warnings"]))

    def test_only_keeps_skills_of_other_plugins_known(self):
        other_skills = self.root / "other" / "skills"
        _write(other_skills / "neqsim-missing" / "SKILL.md",
               "---\nname: neqsim-missing\ndescription: M. USE WHEN: z\n---\nbody\n")
        other = bap.PluginSpec("other", "Other", [other_skills], [], [], False, [])
        specs, known = bap.select_specs([self.spec, other], ["demo"])
        self.assertEqual([s.name for s in specs], ["demo"])
        self.assertIn("neqsim-missing", known)
        result = bap.build_plugin(self.spec, self.out, known, _args())
        self.assertFalse(any("neqsim-missing" in w for w in result["warnings"]))

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

    def test_hook_children_never_open_console_windows(self):
        # A console-less (DETACHED_PROCESS) worker makes Windows pop up a window for every
        # pip/python/java child; the worker needs a hidden console and children CREATE_NO_WINDOW.
        self._build()
        hook_py = (self.out / "demo" / "scripts" / "install_skill_packages.py").read_text(
            encoding="utf-8")
        self.assertIn("NO_WINDOW = 0x08000000", hook_py)
        self.assertNotIn("0x00000008", hook_py)
        runs = re.findall(r"subprocess\.run\((?:[^()]|\([^()]*\))*\)", hook_py, re.S)
        self.assertTrue(runs)
        for call in runs:
            self.assertIn("creationflags=NO_WINDOW", call)


class HookJavaResolutionTest(unittest.TestCase):
    """The hook must find a JDK 21+ even when an old ``java`` is first on PATH.

    Regression for two Equinor installs where JDK 8 (Oracle javapath / Software
    Center) shadowed a Temurin 21+/25: ``java NeqsimMcpLauncher.java`` failed before
    the launcher's own version check, so the MCP server never started and no
    ``neqsim_*`` tools appeared - with nothing in chat to say why.
    """

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        self.jdk8 = self._fake_jdk("jdk1.8.0_392", '1.8.0_392')
        self.jdk21 = self._fake_jdk("jdk-21.0.4+7-hotspot", "21.0.4")
        self.jdk25 = self._fake_jdk("jdk-25.0.4.7-hotspot", "25.0.4")
        # Render the hook and load its functions without running main().
        src = bap.HOOK_PY.format(pinned="", editable_self=False, requirements=[],
                                 prefetch_mcp=True, live_requirements="", verify_imports=[])
        (self.root / "plugin.json").write_text('{"name": "neqsim", "version": "0"}', encoding="utf-8")
        (self.root / "servers").mkdir()
        (self.root / "servers" / "NeqsimMcpLauncher.java").write_text("// stub", encoding="utf-8")
        self.launcher = str(self.root / "servers" / "NeqsimMcpLauncher.java")
        self.ns = {"__file__": str(self.root / "scripts" / "install_skill_packages.py")}
        self._env = dict(os.environ)
        os.environ["PLUGIN_ROOT"] = str(self.root)
        os.environ.pop("JAVA_HOME", None)
        os.environ.pop("NEQSIM_MCP_JAVA", None)
        exec(src.replace("sys.exit(main())", ""), self.ns)  # noqa: S102 - generated code under test
        self.ns["state"] = self.root / "state"
        self.ns["mcp_log"] = self.root / "state" / "mcp-prefetch.log"
        self.ns["java_install_roots"] = lambda: [self.root / "jdks"]
        # Keep the machine's real JDKs (VS Code Java extension JRE, registry) out of the test.
        self.registry_java_candidates = self.ns["registry_java_candidates"]
        self.ns["vscode_java_candidates"] = lambda: iter(())
        self.ns["registry_java_candidates"] = lambda: iter(())
        # Behave like VS Code < 1.141 (no ${PLUGIN_ROOT} expansion) unless a test says otherwise.
        self.ns["vscode_version"] = lambda: (1, 138)
        os.environ.pop("NEQSIM_SKIP_USER_MCP", None)

    def test_user_mcp_json_untouched_when_vscode_expands_plugin_root(self):
        """VS Code 1.141+ resolves ${PLUGIN_ROOT}; writing the user mcp.json would only
        trigger its 'migrate legacy MCP server' prompt."""
        user = self.root / "User"
        user.mkdir()
        mcp = user / "mcp.json"
        self.ns["vscode_user_mcp_files"] = lambda: [mcp]
        java25 = str(self._java(self.jdk25))
        for version in ((1, 141), (1, 144), (2, 0)):
            self.ns["vscode_version"] = lambda v=version: v
            self.assertEqual(self.ns["ensure_mcp_entry"](java25, 25), ([], []))
            self.assertFalse(mcp.exists())
        self.ns["vscode_version"] = lambda: (1, 140)
        self.assertEqual(self.ns["ensure_mcp_entry"](java25, 25), ([str(mcp)], []))
        mcp.unlink()
        self.ns["vscode_version"] = lambda: None
        self.assertEqual(self.ns["ensure_mcp_entry"](java25, 25), ([str(mcp)], []))
        mcp.unlink()
        os.environ["NEQSIM_SKIP_USER_MCP"] = "1"
        self.assertEqual(self.ns["ensure_mcp_entry"](java25, 25), ([], []))
        self.assertFalse(mcp.exists())
    def test_registry_jdk_found_without_path_or_java_home(self):
        """An MSI-installed JDK is found from the registry when PATH and JAVA_HOME are unset."""
        tree = {(2, "SOFTWARE\\Eclipse Adoptium\\JDK"): (["21.0.4.7"], {}),
                (2, "SOFTWARE\\Eclipse Adoptium\\JDK\\21.0.4.7"): (["hotspot"], {}),
                (2, "SOFTWARE\\Eclipse Adoptium\\JDK\\21.0.4.7\\hotspot"): (["MSI"], {}),
                (2, "SOFTWARE\\Eclipse Adoptium\\JDK\\21.0.4.7\\hotspot\\MSI"):
                    ([], {"Path": str(self.jdk21)})}

        class Handle:
            def __init__(self, node):
                self.node = node

            def __enter__(self):
                return self

            def __exit__(self, *exc):
                return False

        def open_key(hive, key):
            if (hive, key) not in tree:
                raise OSError(key)
            return Handle(tree[(hive, key)])

        def query_value(handle, name):
            if name not in handle.node[1]:
                raise OSError(name)
            return handle.node[1][name], 1

        def enum_key(handle, index):
            if index >= len(handle.node[0]):
                raise OSError(index)
            return handle.node[0][index]

        fake = SimpleNamespace(HKEY_CURRENT_USER=1, HKEY_LOCAL_MACHINE=2, OpenKey=open_key,
                               QueryValueEx=query_value, EnumKey=enum_key)
        real_os = self.ns["os"]
        self.ns["os"] = SimpleNamespace(name="nt", environ=os.environ)
        try:
            with mock.patch.dict(sys.modules, {"winreg": fake}):
                found = list(self.registry_java_candidates())
        finally:
            self.ns["os"] = real_os
        self.assertEqual(found, [self.jdk21 / "bin" / self.ns["JAVA_EXE"]])

    def tearDown(self):
        os.environ.clear()
        os.environ.update(self._env)
        self.tmp.cleanup()

    def _fake_jdk(self, folder: str, java_version: str) -> Path:
        home = self.root / "jdks" / folder
        (home / "bin").mkdir(parents=True)
        java = home / "bin" / ("java.exe" if os.name == "nt" else "java")
        java.write_bytes(b"")
        java.chmod(0o755)  # shutil.which() skips non-executable files on POSIX
        (home / "release").write_text('JAVA_VERSION="{}"\n'.format(java_version), encoding="utf-8")
        return home

    def _java(self, home: Path) -> Path:
        return home / "bin" / ("java.exe" if os.name == "nt" else "java")

    def _path_first(self, home: Path) -> None:
        os.environ["PATH"] = str(home / "bin") + os.pathsep + self._env.get("PATH", "")

    def test_java_major_from_release_file(self):
        self.assertEqual(self.ns["java_major"](self._java(self.jdk8)), 8)
        self.assertEqual(self.ns["java_major"](self._java(self.jdk21)), 21)
        self.assertEqual(self.ns["java_major"](self._java(self.jdk25)), 25)

    def test_java8_on_path_falls_back_to_newest_installed_jdk(self):
        self._path_first(self.jdk8)
        java, major, path_java, path_major = self.ns["find_mcp_java"]()
        self.assertEqual(path_major, 8)
        self.assertEqual(Path(path_java).parent.parent.resolve(), self.jdk8.resolve())
        self.assertEqual(major, 25)
        self.assertEqual(Path(java), self._java(self.jdk25))

    def test_explicit_choice_wins_over_newer_installed(self):
        self._path_first(self.jdk8)
        os.environ["JAVA_HOME"] = str(self.jdk21)
        java, major, _, _ = self.ns["find_mcp_java"]()
        self.assertEqual((Path(java), major), (self._java(self.jdk21), 21))
        os.environ["NEQSIM_MCP_JAVA"] = str(self.jdk25)
        java, major, _, _ = self.ns["find_mcp_java"]()
        self.assertEqual((Path(java), major), (self._java(self.jdk25), 25))

    def test_no_usable_jdk_gives_chat_warning(self):
        self._path_first(self.jdk8)
        self.ns["java_install_roots"] = lambda: []
        started, note = self.ns["prefetch_mcp"]()
        self.assertFalse(started)
        self.assertIn("needs a JDK 21+", note)
        self.assertIn("Java 8", note)
        self.assertIn("neqsim_* tools will not appear", note)

    def test_pins_own_mcp_entry_only_when_bare_java_is_unusable(self):
        mcp = self.root / "User" / "mcp.json"
        mcp.parent.mkdir()
        cfg = {"servers": {"neqsim": {"type": "stdio", "command": "java", "args": [self.launcher]},
                           "other": {"type": "stdio", "command": "java", "args": ["x.py"]}},
               "inputs": []}
        mcp.write_text(json.dumps(cfg), encoding="utf-8")
        self.ns["vscode_user_mcp_files"] = lambda: [mcp]
        java25 = str(self._java(self.jdk25))
        ensure = self.ns["ensure_mcp_entry"]
        # PATH java is fine: a bare "java" entry is left untouched
        self.assertEqual(ensure(java25, 21), ([], []))
        # PATH java is Java 8: our entry is pinned, the foreign one is not
        self.assertEqual(ensure(java25, 8), ([], [str(mcp)]))
        patched = json.loads(mcp.read_text(encoding="utf-8"))
        self.assertEqual(patched["servers"]["neqsim"]["command"], java25)
        self.assertEqual(patched["servers"]["other"]["command"], "java")
        self.assertTrue(mcp.with_suffix(".json.bak").is_file())
        self.assertEqual(ensure(java25, 8), ([], []))  # idempotent
        # a pinned JDK that was upgraded/removed (folder renamed) is re-pinned
        patched["servers"]["neqsim"]["command"] = str(self.root / "gone" / "bin" / "java.exe")
        mcp.write_text(json.dumps(patched), encoding="utf-8")
        self.assertEqual(ensure(java25, 8), ([], [str(mcp)]))
        # a launcher path from a moved/reinstalled plugin is re-pointed at this plugin
        patched["servers"]["neqsim"]["args"] = [str(self.root / "old-plugin" / "servers" / "NeqsimMcpLauncher.java")]
        mcp.write_text(json.dumps(patched), encoding="utf-8")
        self.assertEqual(ensure(java25, 25), ([], [str(mcp)]))
        self.assertEqual(json.loads(mcp.read_text(encoding="utf-8"))["servers"]["neqsim"]["args"], [self.launcher])
        # a foreign 'neqsim' server (not the plugin launcher) is never modified
        mcp.write_text(json.dumps({"servers": {"neqsim": {"command": "java", "args": ["mine.jar"]}}}),
                       encoding="utf-8")
        self.assertEqual(ensure(java25, 8), ([], []))

    def test_registers_missing_entry_with_absolute_launcher(self):
        """VS Code does not expand ${PLUGIN_ROOT} (microsoft/vscode#336882): the plugin's own
        mcp.json entry dies with 'Could not find or load main class ${PLUGIN_ROOT}.servers...',
        so the hook must register a working absolute-path entry itself."""
        user = self.root / "User"
        user.mkdir()
        mcp = user / "mcp.json"
        self.ns["vscode_user_mcp_files"] = lambda: [mcp]
        self.ns["mcp_needs_prefetch"] = lambda: False
        java25 = str(self._java(self.jdk25))
        # no mcp.json at all, PATH java fine -> portable bare java + absolute launcher
        self.assertEqual(self.ns["ensure_mcp_entry"](java25, 25), ([str(mcp)], []))
        cfg = json.loads(mcp.read_text(encoding="utf-8"))
        self.assertEqual(cfg["servers"]["neqsim"], {"type": "stdio", "command": "java", "args": [self.launcher]})
        self.assertEqual(cfg["inputs"], [])
        # existing file with other servers, PATH java is Java 8 -> created with the pinned JDK
        mcp.write_text(json.dumps({"servers": {"maintenance-api": {"type": "http", "url": "https://x"}}}),
                       encoding="utf-8")
        self._path_first(self.jdk8)
        started, note = self.ns["prefetch_mcp"]()
        cfg = json.loads(mcp.read_text(encoding="utf-8"))
        self.assertEqual(cfg["servers"]["neqsim"]["command"], java25)
        self.assertEqual(cfg["servers"]["neqsim"]["args"], [self.launcher])
        self.assertIn("maintenance-api", cfg["servers"])
        self.assertIn("registered in", note)
        self.assertIn("${PLUGIN_ROOT}", note)
        self.assertIn("JDK 25", note)
        self.assertIn("Start a new chat", note)
        # JSONC (comments) is left alone rather than destroyed
        mcp.write_text('{\n  // my servers\n  "servers": {}\n}\n', encoding="utf-8")
        self.assertEqual(self.ns["ensure_mcp_entry"](java25, 25), ([], []))

    def test_prefetch_with_java8_on_path_reports_the_pin(self):
        self._path_first(self.jdk8)
        mcp = self.root / "User" / "mcp.json"
        mcp.parent.mkdir()
        mcp.write_text(json.dumps({"servers": {"neqsim": {"command": "java", "args": [self.launcher]}}}),
                       encoding="utf-8")
        self.ns["vscode_user_mcp_files"] = lambda: [mcp]
        self.ns["mcp_needs_prefetch"] = lambda: False
        started, note = self.ns["prefetch_mcp"]()
        self.assertFalse(started)
        self.assertIn("was pinned to the JDK 25", note)
        self.assertIn("Start a new chat", note)
        self.assertEqual(json.loads(mcp.read_text(encoding="utf-8"))["servers"]["neqsim"]["command"],
                         str(self._java(self.jdk25)))
        log = (self.root / "state" / "mcp-prefetch.log").read_text(encoding="utf-8")
        self.assertIn("java:", log)

    def test_java_note_shown_once_per_finding(self):
        self.assertTrue(self.ns["java_note_is_new"]("A"))
        self.assertFalse(self.ns["java_note_is_new"]("A"))
        self.assertTrue(self.ns["java_note_is_new"]("B"))
        self.assertFalse(self.ns["java_note_is_new"](None))
        self.assertTrue(self.ns["java_note_is_new"]("B"))  # relapse after a healthy session


class HookPythonSelectionTest(unittest.TestCase):
    """The hook must not install into an interpreter older than the toolkit supports."""

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        _write(self.root / "plugin.json", '{"name": "neqsim", "version": "0"}')
        self.env = mock.patch.dict(os.environ, {"PLUGIN_ROOT": str(self.root)})
        self.env.start()
        os.environ.pop("NEQSIM_PYTHON", None)
        src = bap.HOOK_PY.format(pinned="", editable_self=False, requirements=[],
                                 prefetch_mcp=False, live_requirements="", verify_imports=[])
        self.ns = {"__file__": str(self.root / "scripts" / "install_skill_packages.py")}
        exec(src.replace("sys.exit(main())", ""), self.ns)  # noqa: S102 - generated code under test

    def tearDown(self):
        self.env.stop()
        self.tmp.cleanup()

    def test_floor_is_python_311_with_no_upper_limit(self):
        """MIN_PYTHON is 3.11 and newer interpreters are never rejected."""
        self.assertEqual(self.ns["MIN_PYTHON"], (3, 11))
        self.assertGreaterEqual(max(self.ns["PREFERRED_MINORS"]), 15)

    def test_current_interpreter_is_kept_when_new_enough(self):
        """A 3.11+ default interpreter is returned without searching for alternatives."""
        self.ns["MIN_PYTHON"] = tuple(sys.version_info[:2])
        self.ns["alternate_pythons"] = mock.Mock(side_effect=AssertionError("no search needed"))
        self.assertEqual(self.ns["pick_python"](), sys.executable)

    def test_old_default_falls_back_to_newer_interpreter(self):
        """A too-old default interpreter is skipped for a versioned one that is new enough."""
        newer = str(Path(sys.executable).resolve())
        self.ns["MIN_PYTHON"] = (sys.version_info[0], sys.version_info[1] + 1)
        self.ns["alternate_pythons"] = lambda: iter([newer])
        self.ns["python_version"] = lambda python: (
            (sys.version_info[0], sys.version_info[1]) if python == sys.executable
            else (sys.version_info[0], sys.version_info[1] + 1))
        self.assertEqual(self.ns["pick_python"](), newer)

    def test_no_new_enough_interpreter_returns_default_for_the_log(self):
        """When nothing qualifies the default is returned so pip's real error is logged."""
        self.ns["MIN_PYTHON"] = (3, 99)
        self.ns["alternate_pythons"] = lambda: iter(())
        self.assertEqual(self.ns["pick_python"](), sys.executable)

    def test_explicit_neqsim_python_is_always_honoured(self):
        """NEQSIM_PYTHON wins even when it is older than MIN_PYTHON."""
        with mock.patch.dict(os.environ, {"NEQSIM_PYTHON": sys.executable}):
            self.ns["MIN_PYTHON"] = (3, 99)
            self.assertEqual(self.ns["pick_python"](), sys.executable)


class HookPluginOnlyTest(unittest.TestCase):
    """Exercise the generated hook without a checkout, pip installs or network access."""

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name) / "plugin with spaces"
        self.root.mkdir()
        _write(self.root / "plugin.json", '{"name":"neqsim","version":"1.0.0"}')
        self.env = mock.patch.dict(os.environ, {"PLUGIN_ROOT": str(self.root)})
        self.env.start()
        src = bap.HOOK_PY.format(pinned="", editable_self=False, requirements=[],
                                 prefetch_mcp=False, live_requirements="", verify_imports=["json"])
        self.ns = {"__file__": str(self.root / "scripts" / "install_skill_packages.py")}
        exec(src.replace("sys.exit(main())", ""), self.ns)
        state = self.root / "state"
        state.mkdir()
        for name, filename in (("state", ""), ("stamp", "installed_version"),
                               ("lock", "install.lock"), ("log", "install.log")):
            self.ns[name] = state / filename
        self.ns["pick_python"] = lambda: sys.executable
        self.ns["spawn_worker"] = mock.Mock()
        self.ns["java_note_is_new"] = lambda note: False

    def tearDown(self):
        self.env.stop()
        self.tmp.cleanup()

    def _main_output(self):
        with mock.patch.object(sys, "argv", ["install_skill_packages.py"]), \
                mock.patch("sys.stdout", new_callable=io.StringIO) as output:
            self.assertEqual(self.ns["main"](), 0)
        return json.loads(output.getvalue())

    def test_pip_lock_serializes_and_reclaims_stale(self):
        shared = self.ns["state"].parent / "pip.lock"
        shared.write_text("1", encoding="utf-8")
        self.assertIsNone(self.ns["acquire_pip_lock"](timeout_s=0.1))
        self.assertTrue(shared.exists())
        old = shared.stat().st_mtime - self.ns["LOCK_MAX_AGE_S"] - 60
        os.utime(str(shared), (old, old))
        self.assertEqual(self.ns["acquire_pip_lock"](timeout_s=1), shared)
        shared.unlink()

    def test_import_check_waits_for_sibling_pip_stage_but_not_import_check(self):
        sibling = self.ns["state"].parent / "sibling"
        sibling.mkdir()
        (sibling / "install.lock").write_text("1234", encoding="utf-8")
        slept = []

        def fake_sleep(seconds):
            slept.append(seconds)
            (sibling / "install.lock").write_text("check", encoding="utf-8")

        with mock.patch.object(self.ns["time"], "sleep", side_effect=fake_sleep):
            self.ns["wait_for_other_installs"](timeout_s=30)
        self.assertEqual(len(slept), 1)
        with mock.patch.object(self.ns["time"], "sleep") as sleep:
            self.ns["wait_for_other_installs"](timeout_s=30)
        sleep.assert_not_called()

    def test_run_install_releases_shared_lock(self):
        self.assertEqual(self.ns["run_install"](sys.executable), 0)
        self.assertFalse((self.ns["state"].parent / "pip.lock").exists())

    def test_internal_requirements_never_prompt_and_never_fail_install(self):
        (self.root / "requirements-internal.txt").write_text(
            "# private\nprivpkg @ git+https://github.com/example/private.git\n", encoding="utf-8")
        with mock.patch.object(self.ns["subprocess"], "run",
                               return_value=SimpleNamespace(returncode=1)) as run, \
                self.ns["log"].open("w", encoding="utf-8") as out:
            skipped = self.ns["install_internal_requirements"](sys.executable, out)
        self.assertEqual(skipped, ["privpkg"])
        command = run.call_args.args[0]
        self.assertEqual(command[-1], "privpkg @ git+https://github.com/example/private.git")
        env = run.call_args.kwargs["env"]
        self.assertEqual(env["GIT_TERMINAL_PROMPT"], "0")
        self.assertEqual(env["GCM_INTERACTIVE"], "never")
        self.assertIn("1 skipped (privpkg", self.ns["log"].read_text(encoding="utf-8"))

    def test_internal_requirements_absent_is_noop(self):
        with self.ns["log"].open("w", encoding="utf-8") as out:
            self.assertEqual(self.ns["install_internal_requirements"](sys.executable, out), [])

    def test_import_check_executes_and_reports_failure(self):
        self.ns["VERIFY_IMPORTS"] = ["json", "_neqsim_test_nonexistent_package_"]
        with self.ns["log"].open("w", encoding="utf-8") as out:
            self.assertFalse(self.ns["import_check"](sys.executable, out))
        text = self.ns["log"].read_text(encoding="utf-8")
        self.assertIn("IMPORT_OK=1 IMPORT_FAILED=1", text)
        self.assertIn("FAILED _neqsim_test_nonexistent_package_", text)

    def test_worker_stamps_only_verified_install(self):
        self.assertEqual(self.ns["run_install"](sys.executable), 0)
        self.assertEqual(self.ns["stamp"].read_text(encoding="utf-8"),
                         self.ns["stamp_text"](sys.executable))
        self.assertIn("IMPORT_OK=1 IMPORT_FAILED=0",
                      self.ns["log"].read_text(encoding="utf-8"))

    def test_failed_import_removes_success_stamp_and_retries_next_session(self):
        self.ns["stamp"].write_text("old success", encoding="utf-8")
        self.ns["lock"].write_text("123", encoding="utf-8")
        self.ns["VERIFY_IMPORTS"] = ["_neqsim_test_nonexistent_package_"]
        self.assertEqual(self.ns["run_install"](sys.executable), 1)
        self.assertFalse(self.ns["stamp"].exists())
        self.assertFalse(self.ns["lock"].exists())
        self.assertIn("FAILED", self.ns["log"].read_text(encoding="utf-8"))
        self._main_output()
        self.ns["spawn_worker"].assert_called_once_with(sys.executable)

    def test_stamp_tracks_editable_plugin_location(self):
        before = self.ns["stamp_text"](sys.executable)
        self.ns["root"] = self.root.parent / "moved plugin"
        self.assertNotEqual(before, self.ns["stamp_text"](sys.executable))

    def test_ready_session_always_supplies_plugin_only_context(self):
        self.ns["stamp"].write_text(self.ns["stamp_text"](sys.executable), encoding="utf-8")
        result = self._main_output()
        hook = result["hookSpecificOutput"]
        self.assertEqual(hook["hookEventName"], "SessionStart")
        context = hook["additionalContext"]
        for expected in (str(self.root), sys.executable, "setup=verified", "-m neqsim_cli",
                         "-m NAME", "load skills by name", "No source checkout"):
            self.assertIn(expected, context)
        self.ns["spawn_worker"].assert_not_called()

    def test_pending_session_does_not_claim_toolkit_ready(self):
        self.ns["lock"].write_text("123", encoding="utf-8")
        result = self._main_output()
        self.assertIn("setup=pending or failed",
                      result["hookSpecificOutput"]["additionalContext"])
        self.ns["spawn_worker"].assert_not_called()

    def test_missing_python_context_limits_work_to_mcp(self):
        self.ns["pick_python"] = lambda: None
        result = self._main_output()
        self.assertIn("Python is unavailable",
                      result["hookSpecificOutput"]["additionalContext"])
        self.ns["spawn_worker"].assert_not_called()

    def test_skills_plugins_do_not_duplicate_core_workflow_context(self):
        self.ns["plugin_name"] = "neqsim-enterprise"
        context = self.ns["runtime_context"](sys.executable, True)
        self.assertIn("neqsim-enterprise", context)
        self.assertIn("setup=verified", context)
        self.assertNotIn("-m neqsim_cli", context)

    def test_runtime_commands_quote_interpreter_paths(self):
        self.ns["os"] = SimpleNamespace(name="nt")
        windows = self.ns["runtime_context"](r"C:\O'Brien\Python Tools\python.exe", True)
        self.assertIn(r"& 'C:\O''Brien\Python Tools\python.exe' -m neqsim_cli", windows)
        self.ns["os"] = SimpleNamespace(name="posix")
        posix = self.ns["runtime_context"]("/opt/Python Tools/python", True)
        self.assertIn("'/opt/Python Tools/python' -m neqsim_cli", posix)

    def test_core_import_check_resolves_hidden_java(self):
        self.ns["VERIFY_IMPORTS"] = ["neqsim"]
        with mock.patch.object(self.ns["subprocess"], "run",
                               return_value=SimpleNamespace(returncode=0)) as run:
            self.assertTrue(self.ns["import_check"](sys.executable, io.StringIO()))
        code = run.call_args.args[0][2]
        self.assertLess(code.index("ensure_java_home()"), code.index("importlib.import_module"))


class LocalizeCommandsTest(unittest.TestCase):
    modules = {"skill_search", "generate_sources_md", "pdf_ocr"}

    def test_uv_and_python_commands_become_module_calls(self):
        text = ("uv run --project devtools --locked devtools/generate_sources_md.py <task> --organize\n"
                "uv run --project devtools devtools/skill_search.py \"x\"\n"
                "python devtools/pdf_ocr.py a.pdf --pid\n"
                "python3 devtools/skill_search.py y\n")
        out = bap.localize_commands(text, self.modules)
        self.assertIn("<python> -m generate_sources_md <task> --organize", out)
        self.assertIn("<python> -m skill_search \"x\"", out)
        self.assertIn("<python> -m pdf_ocr a.pdf --pid", out)
        self.assertIn("<python> -m skill_search y", out)
        self.assertNotIn("uv run", out)

    def test_scripts_outside_the_toolkit_are_left_alone(self):
        text = "uv run --project devtools --locked devtools/build_agent_plugin.py --check\n"
        self.assertEqual(bap.localize_commands(text, self.modules), text)

    def test_every_rewritten_module_is_installed_and_runnable(self):
        modules = bap.toolkit_modules()
        for name in ("unisim_reader", "pdf_ocr", "skill_search", "generate_sources_md"):
            self.assertIn(name, modules)
            source = (Path(bap.DEVTOOLS) / (name + ".py")).read_text(encoding="utf-8")
            self.assertIn("__main__", source, name + " must run as python -m " + name)

    def test_markdown_tree_only_changes_markdown(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            _write(root / "a.md", "uv run --project devtools devtools/skill_search.py q\n")
            _write(root / "a.py", "uv run --project devtools devtools/skill_search.py q\n")
            self.assertEqual(bap.localize_markdown_tree(root, self.modules), 1)
            self.assertIn("<python> -m skill_search q", (root / "a.md").read_text(encoding="utf-8"))
            self.assertIn("uv run", (root / "a.py").read_text(encoding="utf-8"))


class RewriteRepoLinksTest(unittest.TestCase):
    def test_repo_links_become_github_urls_and_local_links_stay(self):
        with tempfile.TemporaryDirectory() as tmp:
            repo = Path(tmp) / "repo"
            skill = repo / ".github" / "skills" / "demo"
            _write(repo / "docs" / "guide.md", "guide")
            _write(repo / "src" / "Thing.java", "class Thing {}")
            _write(skill / "reference.md", "ref")
            _write(skill / "SKILL.md",
                   "[a](../../../docs/guide.md#sec) [b](../../../src/Thing.java) "
                   "[c](docs/guide.md) [d](reference.md) [e](figures/x.png) "
                   "[f](https://example.com/x.md) [g](../../../docs/missing.md)\n")
            dest = Path(tmp) / "out" / "demo"
            import shutil
            shutil.copytree(str(skill), str(dest))
            count = bap.rewrite_repo_links(skill, dest, repo_root=repo)
            text = (dest / "SKILL.md").read_text(encoding="utf-8")
            self.assertEqual(count, 3)
            self.assertIn("(" + bap.REPO_BLOB_URL + "docs/guide.md#sec)", text)
            self.assertIn("(" + bap.REPO_BLOB_URL + "src/Thing.java)", text)
            self.assertIn("(" + bap.REPO_BLOB_URL + "docs/guide.md)", text)
            self.assertIn("(reference.md)", text)
            self.assertIn("(figures/x.png)", text)
            self.assertIn("(../../../docs/missing.md)", text)

    def test_skill_outside_repo_is_not_rewritten(self):
        with tempfile.TemporaryDirectory() as tmp:
            repo = Path(tmp) / "repo"
            _write(repo / "docs" / "guide.md", "guide")
            other = Path(tmp) / "community" / "skills" / "demo"
            _write(other / "SKILL.md", "[c](docs/guide.md)\n")
            dest = Path(tmp) / "out" / "demo"
            import shutil
            shutil.copytree(str(other), str(dest))
            self.assertEqual(bap.rewrite_repo_links(other, dest, repo_root=repo), 0)


class ToolkitPackagingTest(unittest.TestCase):
    def test_shipped_modules_only_import_shipped_local_modules(self):
        import ast
        devtools = Path(bap.DEVTOOLS)
        text = (devtools / "pyproject.toml").read_text(encoding="utf-8")
        modules = set(bap._toml_string_list(text, "py-modules"))
        packages = set(bap._toml_string_list(text, "packages"))
        local_modules = {p.stem for p in devtools.glob("*.py")}
        local_packages = {p.name for p in devtools.iterdir()
                          if p.is_dir() and (p / "__init__.py").exists()}
        files = [devtools / (m + ".py") for m in modules]
        for package in packages:
            files += devtools.joinpath(*package.split(".")).rglob("*.py")
        missing = {}
        for path in files:
            if "tests" in path.parts or path.name.startswith("test_"):
                continue
            for node in ast.walk(ast.parse(path.read_text(encoding="utf-8"))):
                if isinstance(node, ast.Import):
                    names = [a.name.split(".")[0] for a in node.names]
                elif isinstance(node, ast.ImportFrom) and node.level == 0 and node.module:
                    names = [node.module.split(".")[0]]
                else:
                    continue
                for name in names:
                    if (name in local_modules and name not in modules) or (
                            name in local_packages
                            and not any(p == name or p.startswith(name + ".") for p in packages)):
                        missing.setdefault(name, set()).add(path.name)
        self.assertEqual(missing, {}, "modules imported by the toolkit but not packaged")

    def test_every_subpackage_of_a_declared_package_is_declared(self):
        devtools = Path(bap.DEVTOOLS)
        text = (devtools / "pyproject.toml").read_text(encoding="utf-8")
        declared = set(bap._toml_string_list(text, "packages"))
        missing = []
        for package in sorted(declared):
            base = devtools / package.replace(".", os.sep)
            for init in base.rglob("__init__.py"):
                name = ".".join(init.parent.relative_to(devtools).parts)
                if name not in declared and not name.endswith(".tests") \
                        and "__pycache__" not in name:
                    missing.append(name)
        self.assertEqual(missing, [], "subpackages missing from [tool.setuptools] packages")


class BumpTest(unittest.TestCase):
    def test_bump(self):
        self.assertEqual(bap.bump("1.2.3", "patch"), "1.2.4")
        self.assertEqual(bap.bump("1.2.3", "minor"), "1.3.0")
        self.assertEqual(bap.bump("1.2.3", "major"), "2.0.0")


if __name__ == "__main__":
    unittest.main()
