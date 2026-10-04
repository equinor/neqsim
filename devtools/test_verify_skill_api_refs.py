"""Tests for the NeqSim API-drift checker used by the skills/agents CI gate."""
import os
import subprocess
import sys
from datetime import date, timedelta

DEVTOOLS = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.dirname(DEVTOOLS)
SCRIPT = os.path.join(DEVTOOLS, "verify_skill_api_refs.py")

sys.path.insert(0, DEVTOOLS)
import verify_skill_api_refs as checker  # noqa: E402


def test_extract_references_finds_fully_qualified_classes():
    text = "Use `neqsim.thermo.system.SystemSrkEos` and neqsim.process.equipment.stream.Stream."
    refs = checker.extract_references(text)
    assert "neqsim.thermo.system.SystemSrkEos" in refs
    assert "neqsim.process.equipment.stream.Stream" in refs


def test_extract_references_skips_placeholders():
    assert checker.extract_references("see neqsim.package.ClassName") == set()


def test_extract_references_ignores_bare_class_names():
    # Bare names are deliberately not resolved - they cause false failures.
    assert checker.extract_references("Use the Separator class") == set()


def test_known_classes_include_core_types():
    known = checker.collect_java_classes()
    assert "neqsim.thermo.system.SystemSrkEos" in known
    assert "neqsim.process.processmodel.ProcessSystem" in known
    assert "neqsim.process.equipment.reactor.Reactor" not in known


def test_last_verified_parsing():
    text = '---\nname: demo\nlast_verified: "2026-01-15"\n---\n\nBody\n'
    assert checker._last_verified(text) == date(2026, 1, 15)
    assert checker._last_verified("no front matter") is None


def test_repository_has_no_unresolved_references():
    proc = subprocess.run(
        [sys.executable, SCRIPT], capture_output=True, text=True, cwd=REPO_ROOT
    )
    assert proc.returncode == 0, proc.stdout + proc.stderr
    assert "ERROR" not in proc.stdout


def test_stale_last_verified_is_reported(tmp_path, monkeypatch):
    skills = tmp_path / ".github" / "skills" / "demo"
    skills.mkdir(parents=True)
    old = (date.today() - timedelta(days=400)).isoformat()
    (skills / "SKILL.md").write_text(
        '---\nname: demo\ndescription: d\nlast_verified: "{}"\n---\n\nBody\n'.format(old),
        encoding="utf-8",
    )
    monkeypatch.setattr(checker, "SCAN_DIRS", (tmp_path / ".github" / "skills",))
    monkeypatch.setattr(checker, "REPO_ROOT", tmp_path)

    errors, warnings = checker.scan(max_age_days=365)
    assert not errors
    assert any("days old" in w["message"] for w in warnings)
