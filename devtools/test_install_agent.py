"""Tests for NeqSim community agent catalog loading and validation."""
import argparse
import os
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

sys.path.insert(0, os.path.dirname(__file__))

import install_agent


class InstallAgentDiscoveryTest(unittest.TestCase):
    """Regression tests for repository-based agent discovery."""

    def test_remote_catalog_discovery_normalizes_agents(self):
        """Remote repository catalogs should become installable agent entries."""
        remote_catalog = """
catalog_version: "1.0"
agents:
  - name: tie-in-screening-agent
    display_name: "Tie-in Screening Agent"
    version: "0.1.0"
    description: "Early-stage tie-in screening."
    author: "neqsim-community"
    repo: "equinor/neqsim-community-agents"
    path: "agents/tie-in-screening-agent/AGENT.md"
    folder: "agents/tie-in-screening-agent"
    required_skills: [neqsim-flow-assurance, neqsim-standards-lookup]
    tags: [screening, flow-assurance]
"""
        repository = {
            "repo": "equinor/neqsim-community-agents",
            "catalog_path": "community-agents.yaml",
            "tags": ["community"],
        }

        with mock.patch.object(install_agent.install_skill, "_get_default_github_branch",
                               return_value="main"), \
                mock.patch.object(install_agent.install_skill, "_fetch_github_text",
                                  return_value=remote_catalog):
            agents = install_agent._discover_github_repository_agents(repository)

        self.assertEqual(1, len(agents))
        self.assertEqual("tie-in-screening-agent", agents[0]["name"])
        self.assertEqual("Tie-in Screening Agent", agents[0]["display_name"])
        self.assertEqual("equinor/neqsim-community-agents", agents[0]["repo"])
        self.assertEqual("agents/tie-in-screening-agent/AGENT.md", agents[0]["path"])
        self.assertEqual("main", agents[0]["branch"])
        self.assertIn("neqsim-flow-assurance", agents[0]["required_skills"])
        self.assertIn("flow-assurance", agents[0]["tags"])
        self.assertIn("community", agents[0]["tags"])

    def test_fallback_parser_reads_repository_and_agent_entries(self):
        """The no-PyYAML parser should understand agent catalog sections."""
        catalog = """
catalog_version: "1.0"
repositories:
  - repo: "owner/repo"
    catalog_path: "community-agents.yaml"
    agent_path_glob: ["agents/**/*.agent.md", "agents/**/AGENT.md"]
    tags: [community, public]
agents:
  - name: direct-agent
    description: "Direct agent"
    required_skills: [neqsim-api-patterns]
    tags: [direct]
"""

        parsed = install_agent._parse_catalog_fallback(catalog)

        self.assertEqual("owner/repo", parsed["repositories"][0]["repo"])
        self.assertEqual(["community", "public"], parsed["repositories"][0]["tags"])
        self.assertEqual("direct-agent", parsed["agents"][0]["name"])
        self.assertEqual(["neqsim-api-patterns"], parsed["agents"][0]["required_skills"])

    def test_repository_discovery_falls_back_to_scanning_agent_files(self):
        """If no remote catalog is present, .agent.md frontmatter is enough."""
        agent_md = """---
name: Run Example Agent
description: "Example scanned agent for process and flow assurance work."
argument-hint: "Describe the engineering task."
---
You are an example NeqSim agent.

Loaded skills: neqsim-api-patterns, neqsim-flow-assurance
"""

        def fake_fetch_text(_repo, path, branch=None):
            if path == "community-agents.yaml":
                raise RuntimeError("remote catalog missing")
            return agent_md

        repository = {
            "repo": "owner/repo",
            "agent_path_glob": "agents/**/*.agent.md",
            "tags": ["community"],
        }

        with mock.patch.object(install_agent.install_skill, "_get_default_github_branch",
                               return_value="main"), \
                mock.patch.object(install_agent.install_skill, "_list_github_tree_paths",
                                  return_value=("main", ["agents/demo/process.agent.md"])), \
                mock.patch.object(install_agent.install_skill, "_fetch_github_text",
                                  side_effect=fake_fetch_text):
            agents = install_agent._discover_github_repository_agents(repository)

        self.assertEqual(1, len(agents))
        self.assertEqual("process", agents[0]["name"])
        self.assertEqual("Run Example Agent", agents[0]["display_name"])
        self.assertEqual("agents/demo/process.agent.md", agents[0]["path"])
        self.assertEqual("owner/repo", agents[0]["repo"])
        self.assertIn("neqsim-api-patterns", agents[0]["required_skills"])
        self.assertIn("neqsim-flow-assurance", agents[0]["required_skills"])
        self.assertIn("process", agents[0]["tags"])
        self.assertIn("community", agents[0]["tags"])

    def test_validate_agent_dir_accepts_agent_yaml_package(self):
        """Folder packages may define metadata in agent.yaml."""
        with tempfile.TemporaryDirectory() as tmp:
            agent_dir = Path(tmp) / "tie-in-screening-agent"
            agent_dir.mkdir()
            (agent_dir / "AGENT.md").write_text(
                "You are a tie-in screening agent.\n",
                encoding="utf-8",
            )
            (agent_dir / "agent.yaml").write_text(
                "agent_manifest_schema_version: \"1.0\"\n"
                "name: tie-in-screening-agent\n"
                "description: Early-stage tie-in screening.\n"
                "version: \"0.1.0\"\n"
                "required_skills:\n"
                "  - neqsim-api-patterns\n"
                "supported_domains: [flow-assurance, process]\n"
                "inputs: [feed_composition, operating_conditions]\n"
                "outputs: [results_json, report]\n"
                "requires_mcp_tools: [runProcess]\n"
                "human_review_required: true\n"
                "trust_level: community\n",
                encoding="utf-8",
            )

            with mock.patch.object(install_agent, "yaml", None):
                report = install_agent.validate_agent_dir(agent_dir)

        self.assertTrue(report["valid"])
        self.assertEqual([], report["errors"])
        self.assertEqual([], report["warnings"])
        self.assertIn("neqsim-api-patterns", report["required_skills"])
        self.assertEqual(["flow-assurance", "process"], report["metadata"]["supported_domains"])
        self.assertTrue(report["metadata"]["human_review_required"])
        self.assertEqual("community", report["metadata"]["trust_level"])

    def test_validate_agent_dir_rejects_bad_manifest_values(self):
        """Manifest validation should reject unsafe compatibility metadata."""
        with tempfile.TemporaryDirectory() as tmp:
            agent_dir = Path(tmp)
            (agent_dir / "AGENT.md").write_text(
                "You are a malformed manifest agent.\n",
                encoding="utf-8",
            )
            (agent_dir / "agent.yaml").write_text(
                "name: malformed-agent\n"
                "description: Agent with invalid manifest values.\n"
                "human_review_required: maybe\n"
                "trust_level: unknown\n",
                encoding="utf-8",
            )

            report = install_agent.validate_agent_dir(agent_dir)

        self.assertFalse(report["valid"])
        self.assertTrue(any("human_review_required" in error for error in report["errors"]))
        self.assertTrue(any("trust_level" in error for error in report["errors"]))

    def test_validate_agent_dir_warns_for_missing_required_skills(self):
        """Validation should report missing dependent skills without failing metadata."""
        with tempfile.TemporaryDirectory() as tmp:
            agent_dir = Path(tmp)
            (agent_dir / "AGENT.md").write_text(
                "---\n"
                "name: missing-skill-agent\n"
                "description: Agent with a missing skill dependency.\n"
                "required_skills: [neqsim-does-not-exist]\n"
                "---\n"
                "Body.\n",
                encoding="utf-8",
            )

            report = install_agent.validate_agent_dir(agent_dir)

        self.assertTrue(report["valid"])
        self.assertTrue(any("neqsim-does-not-exist" in warning
                            for warning in report["warnings"]))

    def test_local_file_install_writes_manifest(self):
        """Installing a local .agent.md file should copy it and register metadata."""
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            source = tmp_path / "local.agent.md"
            source.write_text(
                "---\n"
                "name: Local Test Agent\n"
                "description: Local agent used by the installer tests.\n"
                "---\n"
                "Body.\n",
                encoding="utf-8",
            )
            install_dir = tmp_path / "installed-agents"
            manifest_file = install_dir / "installed.json"
            catalog = [{
                "name": "local-test-agent",
                "description": "Local test agent",
                "author": "tests",
                "source": "local",
                "path": str(source),
                "_source": "private",
                "required_skills": [],
            }]

            with mock.patch.object(install_agent, "INSTALL_DIR", install_dir), \
                    mock.patch.object(install_agent, "MANIFEST_FILE", manifest_file):
                args = argparse.Namespace(
                    name="local-test-agent",
                    force=False,
                    install_missing_skills=False,
                )
                install_agent.cmd_install(catalog, args)
                manifest = json_load(manifest_file)
                self.assertIn("local-test-agent", manifest)
                self.assertTrue(Path(manifest["local-test-agent"]["main_file"]).exists())

            self.assertIn("local-test-agent", manifest)

    def test_install_merges_catalog_compatibility_metadata(self):
        """Catalog compatibility fields should persist for simple agent files."""
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            source = tmp_path / "catalog-only.agent.md"
            source.write_text(
                "---\n"
                "name: Catalog Only Agent\n"
                "description: Local agent with catalog-declared compatibility.\n"
                "---\n"
                "Body.\n",
                encoding="utf-8",
            )
            install_dir = tmp_path / "installed-agents"
            manifest_file = install_dir / "installed.json"
            catalog = [{
                "name": "catalog-only-agent",
                "description": "Catalog compatibility metadata",
                "author": "tests",
                "source": "local",
                "path": str(source),
                "_source": "community",
                "required_skills": ["neqsim-api-patterns"],
                "supported_domains": ["process", "flow-assurance"],
                "inputs": ["feed_composition", "operating_conditions"],
                "outputs": ["results_json", "report"],
                "requires_mcp_tools": ["runProcess"],
                "human_review_required": "true",
                "trust_level": "community",
            }]

            with mock.patch.object(install_agent, "INSTALL_DIR", install_dir), \
                    mock.patch.object(install_agent, "MANIFEST_FILE", manifest_file), \
                    mock.patch.object(install_agent, "_print_required_skill_guidance",
                                      return_value=[]):
                args = argparse.Namespace(
                    name="catalog-only-agent",
                    force=False,
                    install_missing_skills=False,
                )
                install_agent.cmd_install(catalog, args)
                manifest = json_load(manifest_file)

        installed = manifest["catalog-only-agent"]
        self.assertEqual(["neqsim-api-patterns"], installed["required_skills"])
        self.assertEqual(["process", "flow-assurance"], installed["supported_domains"])
        self.assertEqual(["feed_composition", "operating_conditions"], installed["inputs"])
        self.assertEqual(["results_json", "report"], installed["outputs"])
        self.assertEqual(["runProcess"], installed["requires_mcp_tools"])
        self.assertTrue(installed["human_review_required"])
        self.assertEqual("community", installed["trust_level"])

    def test_local_install_refuses_source_inside_target(self):
        """Installer should not delete a local source package under install dir."""
        with tempfile.TemporaryDirectory() as tmp:
            install_dir = Path(tmp) / "agents"
            source_dir = install_dir / "same-agent"
            source_dir.mkdir(parents=True)
            (source_dir / "AGENT.md").write_text(
                "---\n"
                "name: Same Agent\n"
                "description: Local source inside install target.\n"
                "---\n"
                "Body.\n",
                encoding="utf-8",
            )
            catalog = [{
                "name": "same-agent",
                "description": "Local source inside target",
                "author": "tests",
                "source": "local",
                "path": str(source_dir),
                "_source": "private",
                "required_skills": [],
            }]

            with mock.patch.object(install_agent, "INSTALL_DIR", install_dir), \
                    mock.patch.object(install_agent, "MANIFEST_FILE",
                                      install_dir / "installed.json"):
                args = argparse.Namespace(
                    name="same-agent",
                    force=True,
                    install_missing_skills=False,
                )
                with self.assertRaises(SystemExit):
                    install_agent.cmd_install(catalog, args)

            self.assertTrue((source_dir / "AGENT.md").exists())


def json_load(path):
    """Load JSON from a path."""
    import json

    return json.loads(Path(path).read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()