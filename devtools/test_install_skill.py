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

    def test_fetch_github_bytes_prefers_gh_cli_when_requested(self):
        """Catalog entries can request browser/SSO-backed gh before raw HTTP."""
        with mock.patch.object(install_skill, "_github_request") as mocked_http, \
                mock.patch.object(install_skill.subprocess, "check_output", return_value=b"skill") as mocked_gh:
            content, branch, source = install_skill._fetch_github_bytes(
                "owner/private", "skills/demo/SKILL.md", branch="main", auth="github-cli")

        self.assertEqual(b"skill", content)
        self.assertEqual("main", branch)
        self.assertIn("gh api", source)
        mocked_http.assert_not_called()
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
            mock.patch.object(install_skill, "_local_repository_root", return_value=None), \
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

        def fake_fetch_text(_repo, path, branch=None, auth=None):
            if path == "community-skills.yaml":
                raise RuntimeError("remote catalog missing")
            return skill_md

        repository = {
            "repo": "owner/repo",
            "skill_path_glob": "skills/**/SKILL.md",
            "tags": ["community"],
        }

        with mock.patch.object(install_skill, "_get_default_github_branch", return_value="main"), \
            mock.patch.object(install_skill, "_local_repository_root", return_value=None), \
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

        def fake_fetch_text(_repo, path, branch=None, auth=None):
            if path == "community-skills.yaml":
                raise RuntimeError("remote catalog missing")
            return skill_md

        repository = {
            "repo": "owner/private",
            "skill_path_glob": "skills/**/SKILL.md",
            "name_prefix": "enterprise-",
        }

        with mock.patch.object(install_skill, "_get_default_github_branch", return_value="main"), \
            mock.patch.object(install_skill, "_local_repository_root", return_value=None), \
                mock.patch.object(install_skill, "_list_github_tree_paths",
                                  return_value=("main", ["skills/demo/SKILL.md"])), \
                mock.patch.object(install_skill, "_fetch_github_text", side_effect=fake_fetch_text):
            skills = install_skill._discover_github_repository_skills(repository)

        self.assertEqual("enterprise-hydrate-screening", skills[0]["name"])

    def test_git_repository_discovery_reads_catalog_without_tokens(self):
        """Git sources use git credentials and do not need GitHub tokens."""
        import shutil
        import tempfile
        from pathlib import Path

        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            fixture_repo = tmp_path / "fixture"
            fixture_repo.mkdir()
            (fixture_repo / "enterprise-skills.yaml").write_text(
                "skills:\n"
                "  - name: hydrate-screening\n"
                "    description: Private hydrate screening.\n"
                "    source: git\n"
                "    path: skills/hydrate/SKILL.md\n",
                encoding="utf-8",
            )

            def fake_clone(_entry, destination):
                shutil.copytree(str(fixture_repo), str(destination))
                return "main"

            repository = {
                "source": "git",
                "auth": "git-credential-manager",
                "url": "https://git.internal/skills.git",
                "catalog_path": "enterprise-skills.yaml",
                "name_prefix": "enterprise-",
            }
            with mock.patch.object(install_skill, "_clone_git_repository", side_effect=fake_clone):
                skills = install_skill._discover_git_repository_skills(repository)

        self.assertEqual(1, len(skills))
        self.assertEqual("enterprise-hydrate-screening", skills[0]["name"])
        self.assertEqual("git", skills[0]["source"])
        self.assertEqual("git-credential-manager", skills[0]["auth"])
        self.assertEqual("https://git.internal/skills.git", skills[0]["url"])

    def test_github_repository_discovery_prefers_local_sibling_catalog(self):
        """GitHub catalog entries should use a checked-out sibling repo when present."""
        import tempfile
        from pathlib import Path

        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            neqsim_root = tmp_path / "neqsim"
            sibling = tmp_path / "neqsim-enterprise-skills"
            neqsim_root.mkdir()
            sibling.mkdir()
            (sibling / "README.md").write_text("Enterprise skills.\n", encoding="utf-8")
            skill_file = sibling / "skills" / "engineering-data" / "enterprise-stid-live-lookup" / "SKILL.md"
            skill_file.parent.mkdir(parents=True)
            skill_file.write_text(
                "---\nname: enterprise-stid-live-lookup\ndescription: STID lookup.\n---\nBody.\n",
                encoding="utf-8",
            )
            (sibling / "enterprise-skills.yaml").write_text(
                "skills:\n"
                "  - name: enterprise-stid-live-lookup\n"
                "    description: STID lookup.\n"
                "    path: skills/engineering-data/enterprise-stid-live-lookup/SKILL.md\n",
                encoding="utf-8",
            )
            repository = {
                "repo": "equinor/neqsim-enterprise-skills",
                "catalog_path": "",
                "skill_path_glob": "skills/**/SKILL.md",
            }

            with mock.patch.object(install_skill, "REPO_ROOT", neqsim_root), \
                    mock.patch.object(install_skill, "_get_default_github_branch") as mocked_branch:
                skills = install_skill._discover_github_repository_skills(repository)

        self.assertEqual(1, len(skills))
        self.assertEqual("enterprise-stid-live-lookup", skills[0]["name"])
        self.assertEqual("local", skills[0]["source"])
        self.assertEqual(str(skill_file), skills[0]["path"])
        mocked_branch.assert_not_called()

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

    def test_resolve_vscode_skills_dir_user_scope(self):
        """User scope targets a private prompts skills folder."""
        from pathlib import Path

        with mock.patch.object(install_skill, "_vscode_user_dir", return_value=Path("user-root")):
            result = install_skill.resolve_vscode_skills_dir(scope="user")
        self.assertEqual(Path("user-root") / "prompts" / "skills", result)

    def test_resolve_vscode_skills_dir_workspace_scope_is_explicit(self):
        """Workspace scope targets <workspace>/.github/skills only when requested."""
        import tempfile
        from pathlib import Path

        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / ".github").mkdir()
            with mock.patch.object(install_skill, "find_workspace_root", return_value=root):
                result = install_skill.resolve_vscode_skills_dir(scope="workspace")
            self.assertEqual((root / ".github" / "skills").resolve(), result.resolve())

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

    def test_cmd_install_with_generic_target_exports_and_remove_cleans_up(self):
        """A generic target install exports to a tool-neutral ~/.neqsim layout."""
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
            export_root = tmp_path / "generic-export"
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
                    vscode=False,
                    vscode_dir=None,
                    target=["generic"],
                    export_dir=str(export_root),
                )
                install_skill.cmd_install(catalog, args)
                manifest = json.loads(
                    manifest_file.read_text(encoding="utf-8"))
                exported = export_root / "skills" / "neqsim-demo" / "SKILL.md"
                generic_manifest = export_root / "manifest.json"
                self.assertTrue(exported.exists())
                self.assertTrue(generic_manifest.exists())
                self.assertEqual(
                    str(export_root / "skills" / "neqsim-demo"),
                    manifest["neqsim-demo"]["exports"]["generic"])

                remove_args = argparse.Namespace(name="neqsim-demo")
                install_skill.cmd_remove(catalog, remove_args)
                self.assertFalse(
                    (export_root / "skills" / "neqsim-demo").exists())

    def test_cmd_export_installed_skill_to_generic(self):
        """An installed skill can be exported later without reinstalling."""
        import argparse
        import json
        import tempfile
        from pathlib import Path

        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            install_dir = tmp_path / "installed-skills"
            source_dir = install_dir / "neqsim-demo"
            source_dir.mkdir(parents=True)
            skill_file = source_dir / "SKILL.md"
            skill_file.write_text("# Demo skill body with enough content.\n", encoding="utf-8")
            manifest_file = install_dir / "installed.json"
            manifest_file.write_text(json.dumps({
                "neqsim-demo": {"path": str(skill_file), "source": "private"}
            }), encoding="utf-8")
            export_root = tmp_path / "generic-export"

            with mock.patch.object(install_skill, "MANIFEST_FILE", manifest_file):
                args = argparse.Namespace(
                    name="neqsim-demo",
                    target=["generic"],
                    vscode=False,
                    vscode_dir=None,
                    export_dir=str(export_root),
                )
                install_skill.cmd_export([], args)
                exported = export_root / "skills" / "neqsim-demo" / "SKILL.md"
                self.assertTrue(exported.exists())

    def test_cmd_install_existing_skill_reconciles_requested_export(self):
        """Installing an existing skill with a target restores the export."""
        import argparse
        import json
        import tempfile
        from pathlib import Path

        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            install_dir = tmp_path / "installed-skills"
            source_dir = install_dir / "neqsim-demo"
            source_dir.mkdir(parents=True)
            skill_file = source_dir / "SKILL.md"
            skill_file.write_text("# Demo skill body.\n", encoding="utf-8")
            manifest_file = install_dir / "installed.json"
            manifest_file.write_text(json.dumps({
                "neqsim-demo": {"path": str(skill_file), "source": "private"}
            }), encoding="utf-8")
            export_root = tmp_path / "generic-export"
            catalog = [{"name": "neqsim-demo", "source": "local", "path": str(skill_file)}]

            with mock.patch.object(install_skill, "MANIFEST_FILE", manifest_file):
                args = argparse.Namespace(
                    name="neqsim-demo",
                    force=False,
                    vscode=False,
                    vscode_dir=None,
                    target=["generic"],
                    export_dir=str(export_root),
                )
                install_skill.cmd_install(catalog, args)
                self.assertTrue((export_root / "skills" / "neqsim-demo" / "SKILL.md").exists())

    def test_cmd_export_exits_when_requested_export_fails(self):
        """Explicit export failures should return a non-zero command result."""
        import argparse
        import json
        import tempfile
        from pathlib import Path

        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            install_dir = tmp_path / "installed-skills"
            source_dir = install_dir / "neqsim-demo"
            source_dir.mkdir(parents=True)
            skill_file = source_dir / "SKILL.md"
            skill_file.write_text("# Demo skill body.\n", encoding="utf-8")
            manifest_file = install_dir / "installed.json"
            manifest_file.write_text(json.dumps({
                "neqsim-demo": {"path": str(skill_file), "source": "private"}
            }), encoding="utf-8")

            with mock.patch.object(install_skill, "MANIFEST_FILE", manifest_file), \
                    mock.patch.object(install_skill, "export_skill_to_generic", side_effect=OSError("boom")):
                args = argparse.Namespace(
                    name="neqsim-demo",
                    target=["generic"],
                    vscode=False,
                    vscode_dir=None,
                    export_dir=str(tmp_path / "generic-export"),
                )
                with self.assertRaises(SystemExit):
                    install_skill.cmd_export([], args)

    def test_cmd_install_supports_git_source(self):
        """A direct source: git skill installs through the git helper."""
        import argparse
        import json
        import tempfile
        from pathlib import Path

        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            install_dir = tmp_path / "installed-skills"
            manifest_file = install_dir / "installed.json"
            catalog = [{
                "name": "neqsim-git-demo",
                "description": "Demo git skill",
                "author": "tests",
                "source": "git",
                "auth": "git-credential-manager",
                "url": "https://git.internal/skills.git",
                "path": "skills/demo/SKILL.md",
                "_source": "private",
            }]
            args = argparse.Namespace(
                name="neqsim-git-demo", force=False, vscode=False, vscode_dir=None)
            content = (
                b"---\nname: neqsim-git-demo\ndescription: Demo.\n---\n"
                b"# Demo skill body with enough content.\n")

            with mock.patch.object(install_skill, "INSTALL_DIR", install_dir), \
                    mock.patch.object(install_skill, "MANIFEST_FILE", manifest_file), \
                    mock.patch.object(install_skill, "_read_git_repository_file", return_value=(content, "main")):
                install_skill.cmd_install(catalog, args)

            manifest = json.loads(manifest_file.read_text(encoding="utf-8"))
            self.assertEqual("git", manifest["neqsim-git-demo"]["source_type"])
            self.assertEqual("git-credential-manager", manifest["neqsim-git-demo"]["auth"])
            self.assertEqual("https://git.internal/skills.git", manifest["neqsim-git-demo"]["url"])

    def test_cmd_doctor_reports_sso_brokers_without_secrets(self):
        """Doctor output should mention brokers, not token values."""
        import argparse
        import io
        from contextlib import redirect_stdout

        status = {
            "github_cli": {"available": True, "detail": "available"},
            "git": {"available": True, "detail": "available"},
            "github_token_env": {"available": True, "detail": "set"},
            "private_skill_token_env": {"available": False, "detail": "not set"},
            "private_catalog": {"available": True, "detail": "~/.neqsim/private-skills.yaml"},
        }
        stream = io.StringIO()
        with mock.patch.object(install_skill, "get_enterprise_auth_status", return_value=status), \
                redirect_stdout(stream):
            install_skill.cmd_doctor([], argparse.Namespace())

        output = stream.getvalue()
        self.assertIn("gh auth login --web", output)
        self.assertIn("git-credential-manager", output)
        self.assertIn("GitHub CLI / browser SSO available", output)
        self.assertNotIn("GITHUB_TOKEN", output)
        self.assertNotIn("secret", output.lower())


class InstallAllSkillsTest(unittest.TestCase):
    """Regression tests for `neqsim skill install --all` and source filtering."""

    @staticmethod
    def _args(**overrides):
        import argparse
        base = dict(
            name=None, all=False, source="all", force=False,
            vscode=False, target=None, vscode_scope="user",
            vscode_dir=None, export_dir=None,
        )
        base.update(overrides)
        return argparse.Namespace(**base)

    def test_all_installs_every_catalog_skill(self):
        """--all iterates the whole catalog and installs each skill once."""
        skills = [
            {"name": "alpha", "_source": "community"},
            {"name": "beta", "_source": "community"},
        ]
        with mock.patch.object(install_skill, "load_manifest", return_value={}), \
                mock.patch.object(install_skill, "_install_skill_record", return_value=True) as record:
            install_skill.cmd_install(skills, self._args(all=True))
        installed = [call.args[0]["name"] for call in record.call_args_list]
        self.assertEqual(["alpha", "beta"], installed)

    def test_all_source_filter_limits_to_matching_source(self):
        """--source private installs only private-sourced skills."""
        skills = [
            {"name": "alpha", "_source": "community"},
            {"name": "priv", "_source": "private"},
        ]
        with mock.patch.object(install_skill, "load_manifest", return_value={}), \
                mock.patch.object(install_skill, "_install_skill_record", return_value=True) as record:
            install_skill.cmd_install(skills, self._args(all=True, source="private"))
        installed = [call.args[0]["name"] for call in record.call_args_list]
        self.assertEqual(["priv"], installed)

    def test_all_deduplicates_repeated_names(self):
        """A skill appearing twice in the catalog is installed only once."""
        skills = [
            {"name": "alpha", "_source": "community"},
            {"name": "alpha", "_source": "community"},
        ]
        with mock.patch.object(install_skill, "load_manifest", return_value={}), \
                mock.patch.object(install_skill, "_install_skill_record", return_value=True) as record:
            install_skill.cmd_install(skills, self._args(all=True))
        self.assertEqual(1, record.call_count)

    def test_all_nonzero_exit_when_a_skill_fails(self):
        """A failing skill makes the bulk install exit non-zero."""
        skills = [{"name": "alpha", "_source": "community"}]
        with mock.patch.object(install_skill, "load_manifest", return_value={}), \
                mock.patch.object(install_skill, "_install_skill_record", return_value=False):
            with self.assertRaises(SystemExit) as ctx:
                install_skill.cmd_install(skills, self._args(all=True))
        self.assertEqual(1, ctx.exception.code)

    def test_missing_name_without_all_errors_with_guidance(self):
        """Omitting the name without --all fails with a helpful message."""
        import io
        from contextlib import redirect_stdout

        stream = io.StringIO()
        with redirect_stdout(stream):
            with self.assertRaises(SystemExit) as ctx:
                install_skill.cmd_install([], self._args())
        self.assertEqual(1, ctx.exception.code)
        self.assertIn("--all", stream.getvalue())


if __name__ == "__main__":
    unittest.main()
