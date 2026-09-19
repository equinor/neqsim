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
    _write(agents / "demo-agent" / "AGENT.md",
           "---\nname: demo-agent\ndescription: Demo agent.\nrequired_skills:\n- neqsim-alpha\n"
           "- neqsim-missing\n---\nbody\n")
    _write(agents / "core.agent.md",
           "---\nname: core agent\ndescription: Core.\nrequired_skills: []\n---\nbody\n")
    _write(root / "pyproject.toml", "[project]\nname='x'\nversion='0'\n")
    return bap.PluginSpec("demo", "Demo plugin", [skills], [agents], [], False, [root])


def _args(**overrides):
    base = dict(set_version=None, bump=None, check=False, python="", mcp_version="9.9.9",
                toolkit_ref="master")
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
        # agents rendered as kebab-case ids with frontmatter name == id
        agents_dir = plugin / "com.github.copilot" / "agents"
        self.assertTrue((agents_dir / "demo-agent.agent.md").exists())
        core = (agents_dir / "core.agent.md").read_text(encoding="utf-8")
        self.assertTrue(core.startswith("---\nname: core\n"))
        # hook + packaging files for the editable install
        hooks = json.loads((plugin / "com.github.copilot" / "hooks" / "hooks.json").read_text())
        entry = hooks["hooks"]["SessionStart"][0]
        self.assertIn("${PLUGIN_ROOT}/scripts/install_skill_packages.sh", entry["command"])
        self.assertIn("${PLUGIN_ROOT}/scripts/install_skill_packages.ps1", entry["windows"])
        scripts = plugin / "scripts"
        for name in ("install_skill_packages.sh", "install_skill_packages.ps1",
                     "install_skill_packages.py"):
            self.assertTrue((scripts / name).exists(), name)
        hook_py = (scripts / "install_skill_packages.py").read_text(encoding="utf-8")
        # portable by default: nothing from the build machine is baked in
        self.assertIn("PINNED_PYTHON = ''", hook_py)
        self.assertNotIn(sys.executable, hook_py)
        self.assertIn('os.environ.get("NEQSIM_PYTHON")', hook_py)
        self.assertTrue((plugin / "pyproject.toml").exists())
        self.assertEqual(result["skills"], 2)
        self.assertEqual(result["agents"], 2)

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


class BumpTest(unittest.TestCase):
    def test_bump(self):
        self.assertEqual(bap.bump("1.2.3", "patch"), "1.2.4")
        self.assertEqual(bap.bump("1.2.3", "minor"), "1.3.0")
        self.assertEqual(bap.bump("1.2.3", "major"), "2.0.0")


if __name__ == "__main__":
    unittest.main()
