"""Tests for NeqSim community skill catalog loading and online discovery."""
import os
import sys
import unittest
from unittest import mock

sys.path.insert(0, os.path.dirname(__file__))

import install_skill


class InstallSkillDiscoveryTest(unittest.TestCase):
    """Regression tests for repository-based skill discovery."""

    def test_fetch_github_bytes_falls_back_to_gh_cli_auth(self):
        """Private repo file fetches should work with an active gh login."""
        http_error = install_skill.urllib.error.HTTPError(
            "https://raw.githubusercontent.com/owner/private/main/SKILL.md",
            404,
            "Not Found",
            None,
            None,
        )

        with mock.patch.object(install_skill, "_github_request", side_effect=http_error), \
                mock.patch.object(install_skill.subprocess, "check_output", return_value=b"skill") as mocked_gh:
            content, branch, source = install_skill._fetch_github_bytes(
                "owner/private", "skills/demo/SKILL.md", branch="main")

        self.assertEqual(b"skill", content)
        self.assertEqual("main", branch)
        self.assertIn("gh api", source)
        self.assertTrue(mocked_gh.called)

    def test_list_github_tree_paths_falls_back_to_gh_cli_auth(self):
        """Private repo tree scans should work with an active gh login."""
        http_error = install_skill.urllib.error.HTTPError(
            "https://api.github.com/repos/owner/private/git/trees/main?recursive=1",
            404,
            "Not Found",
            None,
            None,
        )
        gh_response = b'{"tree":[{"type":"blob","path":"skills/demo/SKILL.md"}]}'

        with mock.patch.object(install_skill, "_github_request", side_effect=http_error), \
                mock.patch.object(install_skill.subprocess, "check_output", return_value=gh_response):
            branch, paths = install_skill._list_github_tree_paths(
                "owner/private", branch="main")

        self.assertEqual("main", branch)
        self.assertEqual(["skills/demo/SKILL.md"], paths)

    def test_remote_catalog_discovery_normalizes_skills(self):
        """Remote repository catalogs should become installable skill entries."""
        remote_catalog = """
catalog_version: "1.0"
skills:
  - name: neqsim-fluid-quality-check
    version: "0.1.0"
    description: "Fluid checks. USE WHEN: validating compositions."
    author: "neqsim-community"
    repo: "equinor/neqsim-community-skills"
    path: "skills/pvt/fluid-quality-check/SKILL.md"
    tags: [pvt, validation]
"""
        repository = {
            "repo": "equinor/neqsim-community-skills",
            "catalog_path": "community-skills.yaml",
            "tags": ["community"],
            "min_neqsim_version": "3.7.0",
        }

        with mock.patch.object(install_skill, "_get_default_github_branch", return_value="main"), \
                mock.patch.object(install_skill, "_fetch_github_text", return_value=remote_catalog):
            skills = install_skill._discover_github_repository_skills(repository)

        self.assertEqual(1, len(skills))
        self.assertEqual("neqsim-fluid-quality-check", skills[0]["name"])
        self.assertEqual("equinor/neqsim-community-skills", skills[0]["repo"])
        self.assertEqual("skills/pvt/fluid-quality-check/SKILL.md", skills[0]["path"])
        self.assertEqual("main", skills[0]["branch"])
        self.assertIn("pvt", skills[0]["tags"])
        self.assertIn("community", skills[0]["tags"])
        self.assertEqual("3.7.0", skills[0]["min_neqsim_version"])

    def test_repository_discovery_falls_back_to_scanning_skill_files(self):
        """If no remote catalog is present, SKILL.md frontmatter is enough."""
        skill_md = """---
name: neqsim-scanned-skill
version: "0.2.0"
description: "Scanned skill. USE WHEN: testing repository scanning."
last_verified: "2026-05-31"
---

# Scanned Skill

Use this process skill for tests.
"""

        def fake_fetch_text(_repo, path, branch=None):
            if path == "community-skills.yaml":
                raise RuntimeError("remote catalog missing")
            return skill_md

        repository = {
            "repo": "owner/repo",
            "skill_path_glob": "skills/**/SKILL.md",
            "tags": ["community"],
        }

        with mock.patch.object(install_skill, "_get_default_github_branch", return_value="main"), \
                mock.patch.object(install_skill, "_list_github_tree_paths",
                                  return_value=("main", ["skills/demo/SKILL.md", "README.md"])), \
                mock.patch.object(install_skill, "_fetch_github_text", side_effect=fake_fetch_text):
            skills = install_skill._discover_github_repository_skills(repository)

        self.assertEqual(1, len(skills))
        self.assertEqual("neqsim-scanned-skill", skills[0]["name"])
        self.assertEqual("0.2.0", skills[0]["version"])
        self.assertEqual("skills/demo/SKILL.md", skills[0]["path"])
        self.assertEqual("owner/repo", skills[0]["repo"])
        self.assertIn("process", skills[0]["tags"])
        self.assertIn("community", skills[0]["tags"])

    def test_repository_discovery_can_prefix_private_skill_names(self):
        """Private repository scans may prefix discovered install ids."""
        skill_md = """---
name: hydrate-screening
description: "Hydrate screening skill."
---

# Hydrate Screening
"""

        def fake_fetch_text(_repo, path, branch=None):
            if path == "community-skills.yaml":
                raise RuntimeError("remote catalog missing")
            return skill_md

        repository = {
            "repo": "owner/private",
            "skill_path_glob": "skills/**/SKILL.md",
            "name_prefix": "enterprise-",
        }

        with mock.patch.object(install_skill, "_get_default_github_branch", return_value="main"), \
                mock.patch.object(install_skill, "_list_github_tree_paths",
                                  return_value=("main", ["skills/demo/SKILL.md"])), \
                mock.patch.object(install_skill, "_fetch_github_text", side_effect=fake_fetch_text):
            skills = install_skill._discover_github_repository_skills(repository)

        self.assertEqual("enterprise-hydrate-screening", skills[0]["name"])

    def test_fallback_parser_reads_repository_entries(self):
        """The no-PyYAML parser should understand repository sections."""
        catalog = """
catalog_version: "1.0"
repositories:
  - repo: "owner/repo"
    catalog_path: "community-skills.yaml"
    tags: [community, public]
skills:
  - name: neqsim-direct
    description: "Direct skill"
    tags: [direct]
"""
        parsed = install_skill._parse_catalog_fallback(catalog)

        self.assertEqual("owner/repo", parsed["repositories"][0]["repo"])
        self.assertEqual(["community", "public"], parsed["repositories"][0]["tags"])
        self.assertEqual("neqsim-direct", parsed["skills"][0]["name"])
        self.assertEqual(["direct"], parsed["skills"][0]["tags"])


class SkillVsCodeExportTest(unittest.TestCase):
    """Tests for the --vscode export of installed skills."""

    def test_find_workspace_root_detects_marker(self):
        """A directory containing a marker is detected as the workspace root."""
        import tempfile
        from pathlib import Path

        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / ".github").mkdir()
            nested = root / "a" / "b"
            nested.mkdir(parents=True)
            found = install_skill.find_workspace_root(nested)
            self.assertEqual(root.resolve(), found.resolve())

    def test_resolve_vscode_skills_dir_prefers_explicit(self):
        """An explicit directory overrides workspace/env detection."""
        from pathlib import Path

        result = install_skill.resolve_vscode_skills_dir("custom/skills")
        self.assertEqual(Path("custom/skills").expanduser().resolve(), result)

    def test_resolve_vscode_skills_dir_uses_env(self):
        """NEQSIM_VSCODE_SKILLS_DIR is honoured when no explicit dir is given."""
        from pathlib import Path

        with mock.patch.dict(os.environ, {"NEQSIM_VSCODE_SKILLS_DIR": "env/skills"}):
            result = install_skill.resolve_vscode_skills_dir()
        self.assertEqual(Path("env/skills").expanduser().resolve(), result)

    def test_export_skill_to_vscode_copies_folder(self):
        """Exporting copies the whole skill folder into <vscode_dir>/<name>."""
        import tempfile
        from pathlib import Path

        with tempfile.TemporaryDirectory() as tmp:
            src = Path(tmp) / "src"
            src.mkdir()
            (src / "SKILL.md").write_text("# demo skill", encoding="utf-8")
            (src / "extra.txt").write_text("data", encoding="utf-8")
            vscode_dir = Path(tmp) / "vscode-skills"

            dest = install_skill.export_skill_to_vscode(
                "neqsim-demo", src, vscode_dir)

            self.assertEqual((vscode_dir / "neqsim-demo").resolve(),
                             dest.resolve())
            self.assertTrue((dest / "SKILL.md").exists())
            self.assertTrue((dest / "extra.txt").exists())

    def test_cmd_install_with_vscode_exports_and_remove_cleans_up(self):
        """A --vscode install exports the skill and remove deletes the copy."""
        import argparse
        import json
        import tempfile
        from pathlib import Path

        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            source = tmp_path / "SKILL.md"
            source.write_text(
                "---\nname: neqsim-demo\ndescription: Demo skill.\n---\n"
                "# Demo skill body with enough content.\n",
                encoding="utf-8")
            install_dir = tmp_path / "installed-skills"
            manifest_file = install_dir / "installed.json"
            vscode_dir = tmp_path / "vscode-skills"
            catalog = [{
                "name": "neqsim-demo",
                "description": "Demo skill",
                "author": "tests",
                "source": "local",
                "path": str(source),
                "_source": "private",
            }]

            with mock.patch.object(install_skill, "INSTALL_DIR", install_dir), \
                    mock.patch.object(install_skill, "MANIFEST_FILE", manifest_file):
                args = argparse.Namespace(
                    name="neqsim-demo",
                    force=False,
                    vscode=True,
                    vscode_dir=str(vscode_dir),
                )
                install_skill.cmd_install(catalog, args)
                manifest = json.loads(
                    manifest_file.read_text(encoding="utf-8"))
                exported = vscode_dir / "neqsim-demo" / "SKILL.md"
                self.assertTrue(exported.exists())
                self.assertEqual(
                    str(vscode_dir / "neqsim-demo"),
                    manifest["neqsim-demo"]["vscode_path"])

                remove_args = argparse.Namespace(name="neqsim-demo")
                install_skill.cmd_remove(catalog, remove_args)
                self.assertFalse(
                    (vscode_dir / "neqsim-demo").exists())


if __name__ == "__main__":
    unittest.main()
