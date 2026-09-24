"""Report language tests for the task report generator.

The report language is configured in study_config.yaml (report.language) and
defaults to English. These tests pin the resolution order, the translation of
the report furniture the generator owns, and the document language written into
the .docx and .html so Word spell-checks in the right language.
"""
import importlib.util
import json
import shutil
import subprocess
import sys
from pathlib import Path

import pytest

GENERATOR = Path(__file__).resolve().parent / "task_template" / "step3_report" / "generate_report.py"


def _load_generator():
    """Import generate_report.py as a module without running it."""
    spec = importlib.util.spec_from_file_location("generate_report_lang_test", GENERATOR)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def _make_task(root, language=None):
    """Create a minimal task folder, optionally asking for a report language."""
    task = root / "2026-09-16_language_task"
    (task / "step3_report").mkdir(parents=True)
    (task / "step1_scope_and_research").mkdir()
    shutil.copy(str(GENERATOR), str(task / "step3_report" / "generate_report.py"))
    (task / "results.json").write_text(json.dumps({
        "key_results": {"outlet_temperature_C": -18.5},
        "validation": {"acceptance_criteria_met": True},
        "conclusions": "Language smoke test.",
    }), encoding="utf-8")
    config = ["study:", "  title: \"Language Task\"", "report:",
              "  formats:", "    - docx", "    - html"]
    if language:
        config.append("  language: {}".format(language))
    (task / "study_config.yaml").write_text("\n".join(config) + "\n",
                                            encoding="utf-8")
    return task


def test_language_defaults_to_english(monkeypatch):
    """No configuration means an English report."""
    module = _load_generator()
    monkeypatch.setattr(module.sys, "argv", ["generate_report.py"])
    monkeypatch.delenv("NEQSIM_REPORT_LANGUAGE", raising=False)
    assert module.resolve_report_language({}) == "en"
    assert module.resolve_report_language({"report": {"language": "auto"}}) == "en"


def test_language_from_study_config(monkeypatch):
    """report.language selects the language, and spellings are normalized."""
    module = _load_generator()
    monkeypatch.setattr(module.sys, "argv", ["generate_report.py"])
    monkeypatch.delenv("NEQSIM_REPORT_LANGUAGE", raising=False)
    assert module.resolve_report_language({"report": {"language": "no"}}) == "nb"
    assert module.resolve_report_language({"report": {"language": "Norsk"}}) == "nb"
    assert module.resolve_report_language({"study": {"language": "nb-NO"}}) == "nb"


def test_language_flag_and_env_override_config(monkeypatch):
    """--language wins over the environment, which wins over study_config."""
    module = _load_generator()
    monkeypatch.setattr(module.sys, "argv", ["generate_report.py", "--language", "nb"])
    assert module.resolve_report_language({"report": {"language": "en"}}) == "nb"
    monkeypatch.setattr(module.sys, "argv", ["generate_report.py"])
    monkeypatch.setenv("NEQSIM_REPORT_LANGUAGE", "no")
    assert module.resolve_report_language({"report": {"language": "en"}}) == "nb"


def test_unknown_language_keeps_english_wording(monkeypatch, capsys):
    """An untranslated language still sets the locale and says what it did."""
    module = _load_generator()
    monkeypatch.setattr(module.sys, "argv", ["generate_report.py"])
    monkeypatch.delenv("NEQSIM_REPORT_LANGUAGE", raising=False)
    code = module.resolve_report_language({"report": {"language": "de"}})
    assert code == "de"
    assert "no translation table" in capsys.readouterr().out
    module.REPORT_LANGUAGE = code
    assert module._t("Results") == "Results"
    assert module._report_locale() == "de-DE"


def test_section_headings_follow_the_language(monkeypatch):
    """Section headings are translated when a phrase table exists."""
    module = _load_generator()
    monkeypatch.setattr(module, "REPORT_LANGUAGE", "nb")
    sections = module.build_sections({"key_results": {"duty_kW": 12.0}}, "")
    headings = [section["heading"] for section in sections]
    assert headings[0] == "1. Sammendrag"
    assert any(heading.endswith("Resultater") for heading in headings)
    assert any(heading.endswith("Referanser") for heading in headings)


def test_caption_prefix_is_not_duplicated(monkeypatch):
    """A translated "Figur 1:" prefix is stripped before Word adds its own."""
    module = _load_generator()
    monkeypatch.setattr(module, "REPORT_LANGUAGE", "nb")
    assert module._strip_caption_prefix("Figur 3: Trykkprofil") == "Trykkprofil"
    assert module._strip_caption_prefix("Figure 3: Pressure") == "Pressure"


def test_generated_report_is_written_in_the_configured_language(tmp_path):
    """End-to-end: the HTML carries the locale and the translated headings."""
    pytest.importorskip("docx")
    task = _make_task(tmp_path, language="no")
    script = task / "step3_report" / "generate_report.py"
    result = subprocess.run([sys.executable, str(script)],
                            cwd=str(task), capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    html = next((task / "step3_report").glob("*.html")).read_text(encoding="utf-8")
    assert '<html lang="nb-NO">' in html
    assert "Sammendrag" in html
    assert "Innhold" in html


def test_generated_report_defaults_to_english(tmp_path):
    """Without report.language the report stays English."""
    pytest.importorskip("docx")
    task = _make_task(tmp_path)
    script = task / "step3_report" / "generate_report.py"
    result = subprocess.run([sys.executable, str(script)],
                            cwd=str(task), capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    html = next((task / "step3_report").glob("*.html")).read_text(encoding="utf-8")
    assert '<html lang="en-GB">' in html
    assert "Executive Summary" in html


def test_subheadings_and_depth_moves_are_translated(monkeypatch):
    """Sub-headings and analytical-depth titles follow the report language."""
    module = _load_generator()
    monkeypatch.setattr(module, "REPORT_LANGUAGE", "nb")
    titles = [title for _, title, _ in module._depth_entries(
        {key: ["x"] for key, _, _ in module.DEPTH_MOVES})]
    assert titles[0] == "Bidragsytere rangert på felles grunnlag"
    for phrase in ("Applicable Standards", "Input Parameter Ranges", "Key results",
                   "Sensitivity Ranking (Tornado)", "Source systems read"):
        assert module._t(phrase) != phrase


def test_consistency_check_accepts_comma_decimals():
    """A Norwegian observation writing 20,1 matches the linked value 20.1."""
    module = _load_generator()
    results = {
        "key_results": {"capex_MNOK": 20.1},
        "figure_discussion": [{"figure": "f.png", "observation": "Investeringen er 20,1 MNOK.",
                               "linked_results": ["capex_MNOK"]}],
    }
    issues = module.check_report_consistency(results)
    assert not any("capex_MNOK" in issue.get("message", "") for issue in issues)
