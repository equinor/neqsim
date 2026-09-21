"""Tests for the Agent Plugins 1.0 packager (``build_agent_plugin.py``).

Uses a synthetic mini-repo so the tests do not depend on sibling checkouts.
"""
from __future__ import annotations

import base64
import json
import os
import re
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
    # setuptools output from `pip install <skill>` duplicates the package; never shipped
    _write(skills / "cat" / "neqsim-beta" / "build" / "lib" / "beta" / "__init__.py", "")
    _write(skills / "cat" / "neqsim-beta" / "dist" / "beta-0.tar.gz", "")
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
        self.assertIn("scripts/install_skill_packages.ps1", decoded)
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


class BumpTest(unittest.TestCase):
    def test_bump(self):
        self.assertEqual(bap.bump("1.2.3", "patch"), "1.2.4")
        self.assertEqual(bap.bump("1.2.3", "minor"), "1.3.0")
        self.assertEqual(bap.bump("1.2.3", "major"), "2.0.0")


if __name__ == "__main__":
    unittest.main()
