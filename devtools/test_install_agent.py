"""Tests for NeqSim community agent catalog loading and validation."""
import install_agent
import argparse
import io
import json
import os
import shutil
import sys
import tempfile
import unittest
from contextlib import redirect_stdout
from pathlib import Path
from unittest import mock

sys.path.insert(0, os.path.dirname(__file__))


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
            agents = install_agent._discover_github_repository_agents(
                repository)

        self.assertEqual(1, len(agents))
        self.assertEqual("tie-in-screening-agent", agents[0]["name"])
        self.assertEqual("Tie-in Screening Agent", agents[0]["display_name"])
        self.assertEqual("equinor/neqsim-community-agents", agents[0]["repo"])
        self.assertEqual(
            "agents/tie-in-screening-agent/AGENT.md", agents[0]["path"])
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
        self.assertEqual(["community", "public"],
                         parsed["repositories"][0]["tags"])
        self.assertEqual("direct-agent", parsed["agents"][0]["name"])
        self.assertEqual(["neqsim-api-patterns"],
                         parsed["agents"][0]["required_skills"])

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

        def fake_fetch_text(_repo, path, branch=None, auth=None):
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
            agents = install_agent._discover_github_repository_agents(
                repository)

        self.assertEqual(1, len(agents))
        self.assertEqual("process", agents[0]["name"])
        self.assertEqual("Run Example Agent", agents[0]["display_name"])
        self.assertEqual("agents/demo/process.agent.md", agents[0]["path"])
        self.assertEqual("owner/repo", agents[0]["repo"])
        self.assertIn("neqsim-api-patterns", agents[0]["required_skills"])
        self.assertIn("neqsim-flow-assurance", agents[0]["required_skills"])
        self.assertIn("process", agents[0]["tags"])
        self.assertIn("community", agents[0]["tags"])

    def test_required_skills_reads_markdown_loaded_skills_block(self):
        """Loaded skills blocks should populate required skill metadata."""
        agent_md = """---
name: Consequence Agent
description: "Example agent with markdown-list skills."
---
You are an example NeqSim agent.

## Loaded skills
- neqsim-consequence-analysis
- `neqsim-process-safety` barrier context
- neqsim-agent-handoff

## Primary Objective
Do engineering work.
"""

        required = install_agent._extract_required_skills(agent_md)

        self.assertEqual([
            "neqsim-consequence-analysis",
            "neqsim-process-safety",
            "neqsim-agent-handoff",
        ], required)

    def test_repository_discovery_can_prefix_private_agent_names(self):
        """Private repository scans may prefix discovered install ids."""
        agent_md = """---
name: PVT Agent
description: "Private PVT agent."
---
You are a private PVT agent.
"""

        def fake_fetch_text(_repo, path, branch=None, auth=None):
            if path == "community-agents.yaml":
                raise RuntimeError("remote catalog missing")
            return agent_md

        repository = {
            "repo": "owner/private",
            "agent_path_glob": "agents/**/AGENT.md",
            "name_prefix": "enterprise-",
        }

        with mock.patch.object(install_agent.install_skill, "_get_default_github_branch",
                               return_value="main"), \
                mock.patch.object(install_agent.install_skill, "_list_github_tree_paths",
                                  return_value=("main", ["agents/pvt-agent/AGENT.md"])), \
                mock.patch.object(install_agent.install_skill, "_fetch_github_text",
                                  side_effect=fake_fetch_text):
            agents = install_agent._discover_github_repository_agents(
                repository)

        self.assertEqual("enterprise-pvt-agent", agents[0]["name"])
        self.assertEqual("PVT Agent", agents[0]["display_name"])

    def test_repository_discovery_reads_sibling_agent_yaml(self):
        """AGENT.md package scans should preserve agent.yaml metadata."""
        agent_md = "You are a packaged private agent.\n"
        agent_yaml = """name: demo-agent
display_name: "Demo Agent"
description: Private package agent.
version: "0.1.0"
required_skills: [enterprise-hydrate-screening]
supported_domains: [flow-assurance, process]
inputs: [feed data]
outputs: [screening report]
human_review_required: true
trust_level: private
"""

        def fake_fetch_text(_repo, path, branch=None, auth=None):
            if path == "community-agents.yaml":
                raise RuntimeError("remote catalog missing")
            if path == "agents/demo-agent/AGENT.md":
                return agent_md
            if path == "agents/demo-agent/agent.yaml":
                return agent_yaml
            raise AssertionError("unexpected path: {path}".format(path=path))

        repository = {
            "repo": "owner/private",
            "agent_path_glob": "agents/**/AGENT.md",
            "name_prefix": "enterprise-",
        }
        paths = [
            "agents/demo-agent/AGENT.md",
            "agents/demo-agent/agent.yaml",
            "agents/demo-agent/prompts/system_prompt.md",
        ]

        with mock.patch.object(install_agent.install_skill, "_get_default_github_branch",
                               return_value="main"), \
                mock.patch.object(install_agent.install_skill, "_list_github_tree_paths",
                                  return_value=("main", paths)), \
                mock.patch.object(install_agent.install_skill, "_fetch_github_text",
                                  side_effect=fake_fetch_text):
            agents = install_agent._discover_github_repository_agents(
                repository)

        self.assertEqual("enterprise-demo-agent", agents[0]["name"])
        self.assertEqual("Demo Agent", agents[0]["display_name"])
        self.assertEqual("agents/demo-agent", agents[0]["folder"])
        self.assertEqual("agents/demo-agent/agent.yaml",
                         agents[0]["agent_yaml_path"])
        self.assertEqual(["enterprise-hydrate-screening"],
                         agents[0]["required_skills"])
        self.assertEqual(["flow-assurance", "process"],
                         agents[0]["supported_domains"])
        self.assertTrue(agents[0]["human_review_required"])
        self.assertEqual("private", agents[0]["trust_level"])

    def test_git_repository_discovery_reads_catalog_without_tokens(self):
        """Git repository discovery should use git credentials, not token plumbing."""
        with tempfile.TemporaryDirectory() as tmp:
            fixture_repo = Path(tmp) / "source"
            fixture_repo.mkdir()
            (fixture_repo / "enterprise-agents.yaml").write_text(
                "agents:\n"
                "  - name: git-agent\n"
                "    description: Git discovered agent.\n"
                "    path: agents/git-agent/AGENT.md\n",
                encoding="utf-8",
            )

            def fake_clone(_entry, destination):
                install_agent.shutil.copytree(str(fixture_repo), str(destination))
                return "main"

            repository = {
                "source": "git",
                "auth": "git-credential-manager",
                "url": "https://git.example.test/neqsim/enterprise-agents.git",
                "catalog_path": "enterprise-agents.yaml",
                "name_prefix": "enterprise-",
            }

            with mock.patch.object(install_agent.install_skill, "_clone_git_repository",
                                   side_effect=fake_clone):
                agents = install_agent._discover_git_repository_agents(repository)

        self.assertEqual(1, len(agents))
        self.assertEqual("enterprise-git-agent", agents[0]["name"])
        self.assertEqual("git", agents[0]["source"])
        self.assertEqual("git-credential-manager", agents[0]["auth"])
        self.assertEqual(
            "https://git.example.test/neqsim/enterprise-agents.git", agents[0]["url"])

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
        self.assertEqual(["flow-assurance", "process"],
                         report["metadata"]["supported_domains"])
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
        self.assertTrue(
            any("human_review_required" in error for error in report["errors"]))
        self.assertTrue(
            any("trust_level" in error for error in report["errors"]))

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

    def test_required_skill_alias_resolves_prefixed_catalog_name(self):
        """Missing skill should auto-resolve between unprefixed/prefixed names."""
        required = ["fluid-quality-check"]
        catalog = [{
            "name": "neqsim-fluid-quality-check",
            "description": "Alias resolution test",
            "source": "local",
            "path": "/tmp/neqsim-fluid-quality-check/SKILL.md",
        }]

        with mock.patch.object(install_agent, "_find_missing_required_skills",
                               return_value=["fluid-quality-check"]), \
                mock.patch.object(install_agent.install_skill, "load_catalog",
                                  return_value=catalog), \
                mock.patch.object(install_agent.install_skill, "cmd_install") as mocked_install:
            install_args = argparse.Namespace(
                vscode=True,
                target=["generic"],
                vscode_dir="/tmp/vscode-agents",
                vscode_skills_dir="/tmp/vscode-skills",
                export_dir="/tmp/generic-export",
            )
            unresolved = install_agent._print_required_skill_guidance(
                required,
                install_missing=True,
                install_args=install_args,
            )

        self.assertEqual([], unresolved)
        mocked_install.assert_called_once()
        args_obj = mocked_install.call_args[0][1]
        self.assertEqual("neqsim-fluid-quality-check", args_obj.name)
        self.assertTrue(args_obj.vscode)
        self.assertEqual(["generic"], args_obj.target)
        self.assertEqual("/tmp/vscode-skills", args_obj.vscode_dir)
        self.assertEqual("/tmp/generic-export", args_obj.export_dir)

    def test_required_skill_export_runs_for_already_installed_skill(self):
        """Agent target exports should include already-installed required skills."""
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            skill_dir = tmp_path / "installed-skills" / "neqsim-demo"
            skill_dir.mkdir(parents=True)
            skill_file = skill_dir / "SKILL.md"
            skill_file.write_text("# Demo skill body.\n", encoding="utf-8")
            required = ["neqsim-demo"]
            skill_manifest = {
                "neqsim-demo": {
                    "path": str(skill_file),
                    "source": "community",
                }
            }
            install_args = argparse.Namespace(
                vscode=False,
                target=["generic"],
                vscode_dir=None,
                export_dir=str(tmp_path / "generic-export"),
            )

            with mock.patch.object(install_agent, "_find_missing_required_skills",
                                   return_value=[]), \
                    mock.patch.object(install_agent.install_skill, "load_manifest",
                                      return_value=skill_manifest), \
                    mock.patch.object(install_agent.install_skill,
                                      "_export_installed_skill") as mocked_export:
                unresolved = install_agent._print_required_skill_guidance(
                    required,
                    install_missing=True,
                    install_args=install_args,
                )

        self.assertEqual([], unresolved)
        mocked_export.assert_called_once()
        self.assertEqual("neqsim-demo", mocked_export.call_args[0][0])
        self.assertEqual(skill_dir, mocked_export.call_args[0][1])

    def test_required_skill_export_runs_for_core_workspace_skill(self):
        """Agent target exports should include required core workspace skills."""
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            core_skills_dir = tmp_path / ".github" / "skills"
            skill_dir = core_skills_dir / "neqsim-core-demo"
            skill_dir.mkdir(parents=True)
            skill_file = skill_dir / "SKILL.md"
            skill_file.write_text("# Core skill body.\n", encoding="utf-8")
            install_args = argparse.Namespace(
                vscode=False,
                target=["generic"],
                vscode_dir=None,
                export_dir=str(tmp_path / "generic-export"),
            )

            with mock.patch.object(install_agent, "CORE_SKILLS_DIR", core_skills_dir), \
                    mock.patch.object(install_agent, "_find_missing_required_skills",
                                      return_value=[]), \
                    mock.patch.object(install_agent.install_skill, "load_manifest",
                                      return_value={}), \
                    mock.patch.object(install_agent.install_skill,
                                      "_export_installed_skill") as mocked_export:
                unresolved = install_agent._print_required_skill_guidance(
                    ["neqsim-core-demo"],
                    install_missing=True,
                    install_args=install_args,
                )

        self.assertEqual([], unresolved)
        mocked_export.assert_called_once()
        self.assertEqual("neqsim-core-demo", mocked_export.call_args[0][0])
        self.assertEqual(skill_dir, mocked_export.call_args[0][1])
        self.assertEqual(str(skill_file), mocked_export.call_args[0][3]["neqsim-core-demo"]["path"])

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
                self.assertTrue(
                    Path(manifest["local-test-agent"]["main_file"]).exists())

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
        self.assertEqual(["process", "flow-assurance"],
                         installed["supported_domains"])
        self.assertEqual(
            ["feed_composition", "operating_conditions"], installed["inputs"])
        self.assertEqual(["results_json", "report"], installed["outputs"])
        self.assertEqual(["runProcess"], installed["requires_mcp_tools"])
        self.assertTrue(installed["human_review_required"])
        self.assertEqual("community", installed["trust_level"])

    def test_reinstall_preserves_existing_export_metadata(self):
        """Refreshing one target should not drop another target's export metadata."""
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            source = tmp_path / "export-preserve.agent.md"
            source.write_text(
                "---\n"
                "name: Export Preserve Agent\n"
                "description: Agent to test export manifest preservation.\n"
                "---\n"
                "Body.\n",
                encoding="utf-8",
            )
            install_dir = tmp_path / "installed-agents"
            manifest_file = install_dir / "installed.json"
            existing_generic = tmp_path / "generic" / "agents" / "export-preserve-agent"
            existing_vscode = tmp_path / "prompts" / "export-preserve-agent.agent.md"
            manifest_file.parent.mkdir(parents=True)
            manifest_file.write_text(json.dumps({
                "export-preserve-agent": {
                    "path": str(install_dir / "export-preserve-agent"),
                    "exports": {
                        "generic": str(existing_generic),
                        "vscode": str(existing_vscode),
                    },
                    "generic_path": str(existing_generic),
                    "vscode_path": str(existing_vscode),
                }
            }), encoding="utf-8")
            catalog = [{
                "name": "export-preserve-agent",
                "description": "Export preservation test agent",
                "author": "tests",
                "source": "local",
                "path": str(source),
                "_source": "community",
                "required_skills": [],
            }]

            with mock.patch.object(install_agent, "INSTALL_DIR", install_dir), \
                    mock.patch.object(install_agent, "MANIFEST_FILE", manifest_file), \
                    mock.patch.object(install_agent, "_print_required_skill_guidance",
                                      return_value=[]), \
                    mock.patch.object(install_agent, "_export_installed_agent",
                                      return_value=True):
                args = argparse.Namespace(
                    name="export-preserve-agent",
                    force=True,
                    install_missing_skills=False,
                    target=["vscode"],
                    vscode=False,
                )
                install_agent.cmd_install(catalog, args)
                manifest = json_load(manifest_file)

        installed = manifest["export-preserve-agent"]
        self.assertEqual(str(existing_generic), installed["exports"]["generic"])
        self.assertEqual(str(existing_vscode), installed["exports"]["vscode"])
        self.assertEqual(str(existing_generic), installed["generic_path"])
        self.assertEqual(str(existing_vscode), installed["vscode_path"])

    def test_install_defaults_to_auto_install_missing_skills(self):
        """Agent install should auto-install required skills when flag is omitted."""
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            source = tmp_path / "auto-skill.agent.md"
            source.write_text(
                "---\n"
                "name: Auto Skill Agent\n"
                "description: Agent to test default skill auto-install behavior.\n"
                "---\n"
                "Body.\n",
                encoding="utf-8",
            )
            install_dir = tmp_path / "installed-agents"
            manifest_file = install_dir / "installed.json"
            catalog = [{
                "name": "auto-skill-agent",
                "description": "Auto skill test agent",
                "author": "tests",
                "source": "local",
                "path": str(source),
                "_source": "community",
                "required_skills": ["neqsim-api-patterns"],
            }]

            with mock.patch.object(install_agent, "INSTALL_DIR", install_dir), \
                    mock.patch.object(install_agent, "MANIFEST_FILE", manifest_file), \
                    mock.patch.object(install_agent, "_print_required_skill_guidance",
                                      return_value=[]) as mocked_guidance:
                args = argparse.Namespace(
                    name="auto-skill-agent",
                    force=False,
                )
                install_agent.cmd_install(catalog, args)

                self.assertTrue(mocked_guidance.called)
                self.assertTrue(
                    mocked_guidance.call_args.kwargs["install_missing"])

    def test_install_can_opt_out_of_auto_install_missing_skills(self):
        """Explicit opt-out should disable automatic required skill installation."""
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            source = tmp_path / "no-auto-skill.agent.md"
            source.write_text(
                "---\n"
                "name: No Auto Skill Agent\n"
                "description: Agent to test opt-out skill behavior.\n"
                "---\n"
                "Body.\n",
                encoding="utf-8",
            )
            install_dir = tmp_path / "installed-agents"
            manifest_file = install_dir / "installed.json"
            catalog = [{
                "name": "no-auto-skill-agent",
                "description": "No auto skill test agent",
                "author": "tests",
                "source": "local",
                "path": str(source),
                "_source": "community",
                "required_skills": ["neqsim-api-patterns"],
            }]

            with mock.patch.object(install_agent, "INSTALL_DIR", install_dir), \
                    mock.patch.object(install_agent, "MANIFEST_FILE", manifest_file), \
                    mock.patch.object(install_agent, "_print_required_skill_guidance",
                                      return_value=[]) as mocked_guidance:
                args = argparse.Namespace(
                    name="no-auto-skill-agent",
                    force=False,
                    install_missing_skills=False,
                )
                install_agent.cmd_install(catalog, args)

                self.assertTrue(mocked_guidance.called)
                self.assertFalse(
                    mocked_guidance.call_args.kwargs["install_missing"])

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

    def test_cmd_install_supports_git_source(self):
        """Installing a git-source agent should copy content and record provenance."""
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            fixture_repo = tmp_path / "source"
            package_dir = fixture_repo / "agents" / "git-agent"
            package_dir.mkdir(parents=True)
            (package_dir / "AGENT.md").write_text(
                "---\n"
                "name: Git Agent\n"
                "description: Git agent used by installer tests.\n"
                "---\n"
                "Body.\n",
                encoding="utf-8",
            )
            install_dir = tmp_path / "installed-agents"
            manifest_file = install_dir / "installed.json"
            catalog = [{
                "name": "git-agent",
                "description": "Git source agent",
                "author": "tests",
                "source": "git",
                "auth": "git-credential-manager",
                "url": "https://git.example.test/neqsim/enterprise-agents.git",
                "path": "agents/git-agent/AGENT.md",
                "_source": "private",
                "required_skills": [],
            }]

            def fake_clone(_entry, destination):
                install_agent.shutil.copytree(str(fixture_repo), str(destination))
                return "main"

            with mock.patch.object(install_agent, "INSTALL_DIR", install_dir), \
                    mock.patch.object(install_agent, "MANIFEST_FILE", manifest_file), \
                    mock.patch.object(install_agent.install_skill, "_clone_git_repository",
                                      side_effect=fake_clone):
                args = argparse.Namespace(
                    name="git-agent",
                    force=False,
                    install_missing_skills=False,
                )
                install_agent.cmd_install(catalog, args)
                manifest = json_load(manifest_file)

        installed = manifest["git-agent"]
        self.assertEqual("git", installed["source_type"])
        self.assertEqual("git-credential-manager", installed["auth"])
        self.assertEqual(
            "https://git.example.test/neqsim/enterprise-agents.git", installed["url"])
        self.assertEqual("AGENT.md", Path(installed["main_file"]).name)

    def test_cmd_install_all_exports_every_agent_to_vscode(self):
        """Bulk install should install all catalog agents and export them to VS Code."""
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            community = tmp_path / "community.agent.md"
            private = tmp_path / "private.agent.md"
            community.write_text(
                "---\n"
                "name: Community Agent\n"
                "description: Community bulk install test agent.\n"
                "---\n"
                "Body.\n",
                encoding="utf-8",
            )
            private.write_text(
                "---\n"
                "name: Private Agent\n"
                "description: Private bulk install test agent.\n"
                "---\n"
                "Body.\n",
                encoding="utf-8",
            )
            install_dir = tmp_path / "installed-agents"
            manifest_file = install_dir / "installed.json"
            vscode_dir = tmp_path / "prompts"
            catalog = [{
                "name": "community-agent",
                "description": "Community bulk install test agent",
                "author": "tests",
                "source": "local",
                "path": str(community),
                "_source": "community",
                "required_skills": [],
            }, {
                "name": "private-agent",
                "description": "Private bulk install test agent",
                "author": "tests",
                "source": "local",
                "path": str(private),
                "_source": "private",
                "required_skills": [],
            }]

            with mock.patch.object(install_agent, "INSTALL_DIR", install_dir), \
                    mock.patch.object(install_agent, "MANIFEST_FILE", manifest_file):
                args = argparse.Namespace(
                    name=None,
                    all=True,
                    source="all",
                    force=False,
                    install_missing_skills=False,
                    vscode=True,
                    vscode_scope="user",
                    vscode_dir=str(vscode_dir),
                )
                install_agent.cmd_install(catalog, args)
                manifest = json_load(manifest_file)
                community_exported = (vscode_dir / "community-agent.agent.md").exists()
                private_exported = (vscode_dir / "private-agent.agent.md").exists()

        self.assertIn("community-agent", manifest)
        self.assertIn("private-agent", manifest)
        self.assertTrue(community_exported)
        self.assertTrue(private_exported)

    def test_cmd_install_all_can_filter_private_agents(self):
        """Bulk install source filter should support enterprise/private-only installs."""
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            community = tmp_path / "community.agent.md"
            private = tmp_path / "private.agent.md"
            community.write_text(
                "---\n"
                "name: Community Agent\n"
                "description: Community bulk filter test agent.\n"
                "---\n"
                "Body.\n",
                encoding="utf-8",
            )
            private.write_text(
                "---\n"
                "name: Private Agent\n"
                "description: Private bulk filter test agent.\n"
                "---\n"
                "Body.\n",
                encoding="utf-8",
            )
            install_dir = tmp_path / "installed-agents"
            manifest_file = install_dir / "installed.json"
            catalog = [{
                "name": "community-agent",
                "description": "Community bulk filter test agent",
                "author": "tests",
                "source": "local",
                "path": str(community),
                "_source": "community",
                "required_skills": [],
            }, {
                "name": "private-agent",
                "description": "Private bulk filter test agent",
                "author": "tests",
                "source": "local",
                "path": str(private),
                "_source": "private",
                "required_skills": [],
            }]

            with mock.patch.object(install_agent, "INSTALL_DIR", install_dir), \
                    mock.patch.object(install_agent, "MANIFEST_FILE", manifest_file):
                args = argparse.Namespace(
                    name=None,
                    all=True,
                    source="private",
                    force=False,
                    install_missing_skills=False,
                    vscode=False,
                )
                install_agent.cmd_install(catalog, args)
                manifest = json_load(manifest_file)

        self.assertNotIn("community-agent", manifest)
        self.assertIn("private-agent", manifest)

    def test_cmd_doctor_reports_sso_brokers_without_secret_values(self):
        """Doctor output should describe broker readiness without leaking values."""
        temp_dir = Path(tempfile.mkdtemp(prefix="agent-doctor-"))
        self.addCleanup(shutil.rmtree, temp_dir, ignore_errors=True)
        agent_catalog = temp_dir / "private-agents.yaml"
        agent_catalog.write_text("repositories: []\n", encoding="utf-8")
        status = {
            "github_cli": {"available": True, "detail": "available"},
            "git": {"available": True, "detail": "available"},
            "github_token_env": {"available": False, "detail": "not set"},
            "private_skill_token_env": {"available": False, "detail": "not set"},
            "private_catalog": {"available": True, "detail": "~/.neqsim/private-skills.yaml"},
        }
        with mock.patch.object(install_agent.install_skill,
                               "get_enterprise_auth_status", return_value=status), \
                mock.patch.object(install_agent, "PRIVATE_CATALOG_FILE", agent_catalog), \
                mock.patch.dict(os.environ, {"PRIVATE_AGENT_TOKEN": "redacted-value"}):
            output = io.StringIO()
            with redirect_stdout(output):
                install_agent.cmd_doctor([], argparse.Namespace())

        text = output.getvalue()
        self.assertIn("gh auth login --web", text)
        self.assertIn("git-credential-manager", text)
        self.assertIn("GitHub CLI / browser SSO available", text)
        self.assertIn("private-agents.yaml", text)
        self.assertNotIn("private-skills.yaml", text)
        self.assertNotIn("GITHUB_TOKEN", text)
        self.assertNotIn("redacted-value", text)


def json_load(path):
    """Load JSON from a path."""
    import json

    return json.loads(Path(path).read_text(encoding="utf-8"))


class AgentVsCodeExportTest(unittest.TestCase):
    """Tests for the --vscode export of installed agents."""

    def test_vscode_user_dir_is_platform_specific(self):
        """The user dir resolves to a Code/User path for the platform."""
        result = install_agent._vscode_user_dir()
        self.assertEqual("User", result.name)
        self.assertEqual("Code", result.parent.name)

    def test_resolve_vscode_agents_dir_user_scope(self):
        """User scope targets the global prompts folder."""
        result = install_agent.resolve_vscode_agents_dir(scope="user")
        self.assertEqual("prompts", result.name)

    def test_resolve_vscode_agents_dir_prefers_explicit(self):
        """An explicit directory overrides scope/env detection."""
        result = install_agent.resolve_vscode_agents_dir(
            scope="user", explicit_dir="custom/agents")
        self.assertEqual(
            Path("custom/agents").expanduser().resolve(), result)

    def test_resolve_vscode_agents_dir_uses_env(self):
        """NEQSIM_VSCODE_AGENTS_DIR is honoured when no explicit dir is given."""
        with mock.patch.dict(os.environ, {"NEQSIM_VSCODE_AGENTS_DIR": "env/agents"}):
            result = install_agent.resolve_vscode_agents_dir(scope="user")
        self.assertEqual(Path("env/agents").expanduser().resolve(), result)

    def test_resolve_vscode_agents_dir_workspace_scope(self):
        """Workspace scope targets <workspace>/.github/agents."""
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / ".github").mkdir()
            with mock.patch.object(install_agent.install_skill,
                                   "find_workspace_root", return_value=root):
                result = install_agent.resolve_vscode_agents_dir(scope="workspace")
            self.assertEqual((root / ".github" / "agents").resolve(),
                             result.resolve())

    def test_export_agent_to_vscode_renames_to_agent_md(self):
        """Exporting copies the main file renamed to <name>.agent.md."""
        with tempfile.TemporaryDirectory() as tmp:
            main_file = Path(tmp) / "AGENT.md"
            main_file.write_text(
                "---\nname: demo\ndescription: x\n---\n# Demo", encoding="utf-8")
            vscode_dir = Path(tmp) / "prompts"

            dest = install_agent.export_agent_to_vscode(
                "neqsim-demo", main_file, vscode_dir)

            self.assertEqual("neqsim-demo.agent.md", dest.name)
            self.assertTrue(dest.exists())
            self.assertIn("# Demo", dest.read_text(encoding="utf-8"))

    def test_cmd_install_with_vscode_exports_and_remove_cleans_up(self):
        """A --vscode install exports the agent and remove deletes the copy."""
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
            vscode_dir = tmp_path / "prompts"
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
                    vscode=True,
                    vscode_scope="user",
                    vscode_dir=str(vscode_dir),
                )
                install_agent.cmd_install(catalog, args)
                manifest = json_load(manifest_file)
                exported = vscode_dir / "local-test-agent.agent.md"
                self.assertTrue(exported.exists())
                self.assertEqual(
                    str(exported),
                    manifest["local-test-agent"]["vscode_path"])

                remove_args = argparse.Namespace(name="local-test-agent")
                install_agent.cmd_remove(catalog, remove_args)
                self.assertFalse(exported.exists())

    def test_cmd_install_with_generic_target_exports_and_remove_cleans_up(self):
        """A generic target install exports a tool-neutral agent package."""
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
            export_root = tmp_path / "generic-export"
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
                    vscode=False,
                    vscode_scope="user",
                    vscode_dir=None,
                    target=["generic"],
                    export_dir=str(export_root),
                )
                install_agent.cmd_install(catalog, args)
                manifest = json_load(manifest_file)
                exported = export_root / "agents" / "local-test-agent"
                self.assertTrue((exported / "AGENT.md").exists())
                self.assertTrue((export_root / "manifest.json").exists())
                self.assertEqual(
                    str(exported),
                    manifest["local-test-agent"]["exports"]["generic"])

                remove_args = argparse.Namespace(name="local-test-agent")
                install_agent.cmd_remove(catalog, remove_args)
                self.assertFalse(exported.exists())

    def test_cmd_export_installed_agent_to_generic(self):
        """An installed agent can be exported later without reinstalling."""
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            install_dir = tmp_path / "installed-agents"
            source_dir = install_dir / "local-test-agent"
            source_dir.mkdir(parents=True)
            main_file = source_dir / "local.agent.md"
            main_file.write_text(
                "---\n"
                "name: Local Test Agent\n"
                "description: Local agent used by export tests.\n"
                "---\n"
                "Body.\n",
                encoding="utf-8",
            )
            manifest_file = install_dir / "installed.json"
            manifest_file.write_text(json.dumps({
                "local-test-agent": {
                    "path": str(source_dir),
                    "main_file": str(main_file),
                    "source": "private",
                    "required_skills": [],
                }
            }), encoding="utf-8")
            export_root = tmp_path / "generic-export"

            with mock.patch.object(install_agent, "MANIFEST_FILE", manifest_file):
                args = argparse.Namespace(
                    name="local-test-agent",
                    target=["generic"],
                    vscode=False,
                    vscode_scope="user",
                    vscode_dir=None,
                    export_dir=str(export_root),
                )
                install_agent.cmd_export([], args)
                exported = export_root / "agents" / "local-test-agent" / "AGENT.md"
                self.assertTrue(exported.exists())

    def test_cmd_install_existing_agent_reconciles_requested_export(self):
        """Installing an existing agent with a target restores the export."""
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            install_dir = tmp_path / "installed-agents"
            source_dir = install_dir / "local-test-agent"
            source_dir.mkdir(parents=True)
            main_file = source_dir / "local.agent.md"
            main_file.write_text(
                "---\n"
                "name: Local Test Agent\n"
                "description: Local agent used by export tests.\n"
                "---\n"
                "Body.\n",
                encoding="utf-8",
            )
            manifest_file = install_dir / "installed.json"
            manifest_file.write_text(json.dumps({
                "local-test-agent": {
                    "path": str(source_dir),
                    "main_file": str(main_file),
                    "source": "private",
                    "required_skills": [],
                }
            }), encoding="utf-8")
            export_root = tmp_path / "generic-export"
            catalog = [{
                "name": "local-test-agent",
                "source": "local",
                "path": str(main_file),
                "required_skills": [],
            }]

            with mock.patch.object(install_agent, "MANIFEST_FILE", manifest_file):
                args = argparse.Namespace(
                    name="local-test-agent",
                    force=False,
                    install_missing_skills=False,
                    vscode=False,
                    vscode_scope="user",
                    vscode_dir=None,
                    target=["generic"],
                    export_dir=str(export_root),
                )
                install_agent.cmd_install(catalog, args)
                exported = export_root / "agents" / "local-test-agent" / "AGENT.md"
                self.assertTrue(exported.exists())

    def test_cmd_export_exits_when_requested_export_fails(self):
        """Explicit export failures should return a non-zero command result."""
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            install_dir = tmp_path / "installed-agents"
            source_dir = install_dir / "local-test-agent"
            source_dir.mkdir(parents=True)
            main_file = source_dir / "local.agent.md"
            main_file.write_text(
                "---\n"
                "name: Local Test Agent\n"
                "description: Local agent used by export tests.\n"
                "---\n"
                "Body.\n",
                encoding="utf-8",
            )
            manifest_file = install_dir / "installed.json"
            manifest_file.write_text(json.dumps({
                "local-test-agent": {
                    "path": str(source_dir),
                    "main_file": str(main_file),
                    "source": "private",
                    "required_skills": [],
                    "required_skills": [],
                }
            }), encoding="utf-8")

            with mock.patch.object(install_agent, "MANIFEST_FILE", manifest_file), \
                    mock.patch.object(install_agent, "export_agent_to_generic", side_effect=OSError("boom")):
                args = argparse.Namespace(
                    name="local-test-agent",
                    target=["generic"],
                    vscode=False,
                    vscode_scope="user",
                    vscode_dir=None,
                    export_dir=str(tmp_path / "generic-export"),
                )
                with self.assertRaises(SystemExit):
                    install_agent.cmd_export([], args)

    def test_cmd_doctor_generic_passes_when_agent_and_required_skill_exported(self):
        """Generic doctor should pass when exported agents can see required skills."""
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            export_root = tmp_path / "generic-export"
            agent_export = export_root / "agents" / "local-test-agent"
            skill_export = export_root / "skills" / "neqsim-demo"
            agent_export.mkdir(parents=True)
            skill_export.mkdir(parents=True)
            (agent_export / "AGENT.md").write_text("Agent body.\n", encoding="utf-8")
            (skill_export / "SKILL.md").write_text("# Skill body.\n", encoding="utf-8")
            (export_root / "manifest.json").write_text(
                '{"agents":{"local-test-agent":{}},"skills":{"neqsim-demo":{}}}',
                encoding="utf-8")

            agent_manifest = {
                "local-test-agent": {
                    "path": str(tmp_path / "installed-agents" / "local-test-agent"),
                    "exports": {"generic": str(agent_export)},
                    "required_skills": ["neqsim-demo"],
                }
            }
            skill_manifest = {
                "neqsim-demo": {
                    "path": str(tmp_path / "installed-skills" / "neqsim-demo" / "SKILL.md"),
                    "exports": {"generic": str(skill_export)},
                }
            }
            args = argparse.Namespace(target="generic", export_dir=str(export_root))

            with mock.patch.object(install_agent, "load_manifest", return_value=agent_manifest), \
                    mock.patch.object(install_agent.install_skill, "load_manifest",
                                      return_value=skill_manifest):
                with redirect_stdout(io.StringIO()) as output:
                    install_agent.cmd_doctor([], args)

            self.assertIn("Result: PASS", output.getvalue())

    def test_cmd_doctor_vscode_uses_current_user_skill_folder_not_stale_manifest(self):
        """VS Code doctor should ignore stale workspace skill export paths."""
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            prompts_dir = tmp_path / "prompts"
            agent_export = prompts_dir / "local-test-agent.agent.md"
            skill_export = prompts_dir / "skills" / "neqsim-demo"
            agent_export.parent.mkdir(parents=True)
            skill_export.mkdir(parents=True)
            agent_export.write_text("Agent body.\n", encoding="utf-8")
            (skill_export / "SKILL.md").write_text("# Skill body.\n", encoding="utf-8")

            agent_manifest = {
                "local-test-agent": {
                    "exports": {"vscode": str(agent_export)},
                    "required_skills": ["neqsim-demo"],
                }
            }
            stale_workspace_export = tmp_path / ".github" / "skills" / "neqsim-demo"
            skill_manifest = {
                "neqsim-demo": {
                    "path": str(tmp_path / "installed-skills" / "neqsim-demo" / "SKILL.md"),
                    "exports": {"vscode": str(stale_workspace_export)},
                }
            }
            args = argparse.Namespace(target="vscode", export_dir=None)

            with mock.patch.object(install_agent, "load_manifest", return_value=agent_manifest), \
                    mock.patch.object(install_agent.install_skill, "load_manifest",
                                      return_value=skill_manifest):
                with redirect_stdout(io.StringIO()) as output:
                    install_agent.cmd_doctor([], args)

            text = output.getvalue()
            self.assertIn("Result: PASS", text)
            self.assertNotIn(str(stale_workspace_export), text)

    def test_cmd_doctor_vscode_accepts_core_workspace_skill(self):
        """VS Code doctor should accept skills discoverable from core .github/skills."""
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            prompts_dir = tmp_path / "prompts"
            agent_export = prompts_dir / "local-test-agent.agent.md"
            core_skills_dir = tmp_path / ".github" / "skills"
            core_skill = core_skills_dir / "neqsim-core-demo"
            agent_export.parent.mkdir(parents=True)
            core_skill.mkdir(parents=True)
            agent_export.write_text("Agent body.\n", encoding="utf-8")
            (core_skill / "SKILL.md").write_text("# Core skill body.\n", encoding="utf-8")

            agent_manifest = {
                "local-test-agent": {
                    "exports": {"vscode": str(agent_export)},
                    "required_skills": ["neqsim-core-demo"],
                }
            }
            args = argparse.Namespace(target="vscode", export_dir=None)

            with mock.patch.object(install_agent, "CORE_SKILLS_DIR", core_skills_dir), \
                    mock.patch.object(install_agent, "load_manifest", return_value=agent_manifest), \
                    mock.patch.object(install_agent.install_skill, "load_manifest", return_value={}):
                with redirect_stdout(io.StringIO()) as output:
                    install_agent.cmd_doctor([], args)

            self.assertIn("Result: PASS", output.getvalue())

    def test_cmd_doctor_generic_fails_when_required_skill_not_exported(self):
        """Generic doctor should fail if an exported agent's skill is not exported."""
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            export_root = tmp_path / "generic-export"
            agent_export = export_root / "agents" / "local-test-agent"
            agent_export.mkdir(parents=True)
            (agent_export / "AGENT.md").write_text("Agent body.\n", encoding="utf-8")
            (export_root / "manifest.json").write_text(
                '{"agents":{"local-test-agent":{}}}', encoding="utf-8")

            agent_manifest = {
                "local-test-agent": {
                    "exports": {"generic": str(agent_export)},
                    "required_skills": ["neqsim-demo"],
                }
            }
            skill_manifest = {
                "neqsim-demo": {
                    "path": str(tmp_path / "installed-skills" / "neqsim-demo" / "SKILL.md"),
                }
            }
            args = argparse.Namespace(target="generic", export_dir=str(export_root))

            with mock.patch.object(install_agent, "load_manifest", return_value=agent_manifest), \
                    mock.patch.object(install_agent.install_skill, "load_manifest",
                                      return_value=skill_manifest):
                with self.assertRaises(SystemExit), redirect_stdout(io.StringIO()) as output:
                    install_agent.cmd_doctor([], args)

            self.assertIn("requires skill not exported", output.getvalue())


if __name__ == "__main__":
    unittest.main()
