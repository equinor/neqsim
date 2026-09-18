"""Packaging invariants and STDIO harness tests; no Docker or Java required."""

import hashlib
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
import zipfile

try:
    from devtools import build_copilot_plugin as builder
    from devtools.smoke_copilot_plugin import smoke
except ModuleNotFoundError:
    import build_copilot_plugin as builder
    from smoke_copilot_plugin import smoke


class CopilotPluginTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="plugin tests with spaces ")
        self.base = Path(self.temporary.name)
        self.root = self.base / "source"
        self.output = self.base / "installed" / "neqsim"
        for path, text in {
            ".github/skills/example_skill/SKILL.md": (
                '---\nname: example_skill\ndescription: "Example workflow for plugin packaging tests."\n'
                'last_verified: "2026-09-18"\n---\n'
                'Use @thermo.fluid and [data](data.json) or [source](../../../docs/guide.md).\n'
            ),
            ".github/skills/example_skill/data.json": '{"value": 42}\n',
            ".github/agents/thermo.fluid.agent.md": (
                '---\nname: Fluid\ndescription: "Configure a fluid with the example skill."\n'
                'argument-hint: "Fluid description"\n---\n'
                'Loaded skills: example_skill\n[skill](../skills/example_skill/SKILL.md)\n'
            ),
            "docs/guide.md": "# Source guide\n",
            "docs/integration/copilot_plugin.md": "# Installation\n",
            "LICENSE": "Apache License, Version 2.0\n",
        }.items():
            destination = self.root / path
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_text(text, encoding="utf-8")
        self.revision = patch.object(builder, "source_revision", return_value="a" * 40)
        self.revision.start()
        self.addCleanup(self.revision.stop)
        self.addCleanup(self.temporary.cleanup)

    def test_relocated_bundle_preserves_catalog_resources_and_links(self):
        builder.build(self.root, self.output)
        # Move the bundle away from both source and build directory.
        relocated = self.base / "elsewhere" / "neqsim"
        relocated.parent.mkdir()
        self.output.rename(relocated)
        skill = relocated / "skills/example-skill/SKILL.md"
        agent = relocated / "com.github.copilot/agents/neqsim-thermo-fluid.agent.md"
        self.assertIn('name: "example-skill"', skill.read_text())
        self.assertIn("@neqsim-thermo-fluid", skill.read_text())
        self.assertIn("metadata:\n", skill.read_text())
        self.assertIn("blob/" + "a" * 40 + "/docs/guide.md", skill.read_text())
        self.assertIn("../../skills/example-skill/SKILL.md", agent.read_text())
        self.assertIn("Loaded skills: example-skill", agent.read_text())
        self.assertEqual(json.loads((skill.parent / "data.json").read_text()), {"value": 42})
        inventory = json.loads((relocated / "bundle-inventory.json").read_text())
        self.assertEqual((inventory["agentCount"], inventory["skillCount"]), (1, 1))
        for entry in inventory["files"]:
            self.assertEqual(hashlib.sha256((relocated / entry["path"]).read_bytes()).hexdigest(), entry["sha256"])
        self.assertIn("example_skill", (self.root / ".github/skills/example_skill/SKILL.md").read_text())

    def test_rejects_colliding_names_and_does_not_create_partial_output(self):
        duplicate = self.root / ".github/skills/example-skill"
        duplicate.mkdir()
        (duplicate / "SKILL.md").write_text("---\nname: x\ndescription: y\n---\n")
        with self.assertRaisesRegex(ValueError, "collide"):
            builder.build(self.root, self.output)
        self.assertFalse(self.output.exists())

    def test_existing_output_and_source_tree_are_preserved(self):
        self.output.mkdir(parents=True)
        sentinel = self.output / "user-file"
        sentinel.write_text("keep")
        with self.assertRaisesRegex(ValueError, "already exists"):
            builder.build(self.root, self.output)
        self.assertEqual(sentinel.read_text(), "keep")
        with self.assertRaisesRegex(ValueError, "under target"):
            builder.build(self.root, self.root / ".github/generated-plugin")

    def test_embedded_jar_and_deterministic_archive(self):
        jar = self.base / "mcp server.jar"
        with zipfile.ZipFile(jar, "w") as archive:
            archive.writestr("META-INF/MANIFEST.MF", "Main-Class: fixture.Only\n")
            archive.writestr("neqsim/mcp/server/NeqSimTools.class", b"fixture, not executable")
        builder.build(self.root, self.output, jar=jar)
        config = json.loads((self.output / "mcp.json").read_text())["mcpServers"]["neqsim"]
        self.assertEqual(config["command"], "java")
        self.assertIn("-Dquarkus.profile=stdio", config["args"])
        self.assertEqual(config["args"][-1], "${PLUGIN_ROOT}/server/neqsim-mcp-server.jar")
        self.assertEqual((self.output / "server/neqsim-mcp-server.jar").read_bytes(), jar.read_bytes())
        first, second = self.base / "first.zip", self.base / "second.zip"
        builder.archive_bundle(self.output, first)
        builder.archive_bundle(self.output, second)
        self.assertEqual(first.read_bytes(), second.read_bytes())
        with zipfile.ZipFile(first) as archive:
            self.assertTrue(all(name.startswith("neqsim/") for name in archive.namelist()))

    def test_invalid_server_inputs_fail_before_output(self):
        with self.assertRaisesRegex(ValueError, "image reference"):
            builder.build(self.root, self.output, image="--privileged")
        with self.assertRaisesRegex(ValueError, "jar archive"):
            builder.build(self.root, self.output, jar=self.base / "missing.jar")
        self.assertFalse(self.output.exists())

    def test_every_canonical_agent_and_skill_is_packaged(self):
        builder.build(builder.ROOT, self.output)
        expected_skills = len(list((builder.ROOT / ".github/skills").glob("*/SKILL.md")))
        expected_agents = len(list((builder.ROOT / ".github/agents").glob("*.agent.md")))
        self.assertEqual(len(list((self.output / "skills").glob("*/SKILL.md"))), expected_skills)
        self.assertEqual(len(list((self.output / "com.github.copilot/agents").glob("*.agent.md"))), expected_agents)
        for path in (self.output / "skills").glob("*/SKILL.md"):
            fields = builder.parse_front_matter(path.read_text())
            self.assertEqual(fields["name"], path.parent.name)
            self.assertRegex(fields["name"], r"^[a-z0-9]+(-[a-z0-9]+)*$")
            self.assertLessEqual(len(fields["description"]), 1024)

    def test_smoke_harness_uses_configured_process_and_expands_paths(self):
        builder.build(self.root, self.output)
        responder = self.output / "fixture server.py"
        responder.write_text(
            "import json, sys\n"
            "for line in sys.stdin:\n"
            "    request = json.loads(line)\n"
            "    if 'id' not in request: continue\n"
            "    method = request['method']\n"
            "    if method == 'initialize': result = {'serverInfo': {'name': 'fixture'}}\n"
            "    elif method == 'tools/list': result = {'tools': [{'name': n} for n in ['runFlash', 'runProcess', 'getCapabilities']]}\n"
            "    else: result = {'content': [{'type': 'text', 'text': json.dumps({'status': 'success'})}]}\n"
            "    print(json.dumps({'jsonrpc': '2.0', 'id': request['id'], 'result': result}), flush=True)\n"
        )
        builder.write_json(self.output / "mcp.json", {"mcpServers": {"neqsim": {
            "type": "stdio", "command": sys.executable, "args": ["${PLUGIN_ROOT}/fixture server.py"],
        }}})
        result = smoke(self.output, timeout=5)
        self.assertEqual(result["toolCount"], 3)
        self.assertEqual(result["getCapabilities"], "passed")


if __name__ == "__main__":
    unittest.main()
