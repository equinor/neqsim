"""Word report template regression tests for the task report generator."""
import json
import shutil
import subprocess
import sys
from pathlib import Path

import pytest

docx = pytest.importorskip("docx")

GENERATOR = Path(__file__).resolve().parent / "task_template" / "step3_report" / "generate_report.py"


def _make_template(path, font_name="Garamond", header_text="ACME Engineering"):
    """Create a minimal branded Word template with a distinctive font and header."""
    doc = docx.Document()
    doc.styles["Normal"].font.name = font_name
    doc.sections[0].header.paragraphs[0].text = header_text
    doc.add_paragraph("Template boilerplate that reports must not inherit.")
    doc.save(str(path))
    return path


def _make_task(root):
    """Create a task folder with the generator, a figure-free results.json."""
    task = root / "2026-09-12_template_task"
    (task / "step3_report").mkdir(parents=True)
    (task / "step1_scope_and_research").mkdir()
    shutil.copy(str(GENERATOR), str(task / "step3_report" / "generate_report.py"))
    (task / "results.json").write_text(json.dumps({
        "key_results": {"outlet_temperature_C": -18.5},
        "validation": {"mass_balance_error_pct": 0.01},
        "conclusions": "Template smoke test.",
    }), encoding="utf-8")
    return task


def _run(task, *args):
    script = task / "step3_report" / "generate_report.py"
    result = subprocess.run([sys.executable, str(script)] + list(args),
                            cwd=str(task), capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    return result


def test_report_inherits_template_styling(tmp_path):
    template = _make_template(tmp_path / "company template.docx")
    task = _make_task(tmp_path)
    _run(task, "--template", str(template))

    report = docx.Document(str(task / "step3_report" / "Report.docx"))
    assert report.styles["Normal"].font.name == "Garamond"
    assert "ACME Engineering" in report.sections[0].header.paragraphs[0].text
    body = "\n".join(p.text for p in report.paragraphs)
    assert "Template boilerplate" not in body
    assert "Table of Contents" in body


def test_keep_template_content_retains_boilerplate(tmp_path):
    template = _make_template(tmp_path / "company template.docx")
    task = _make_task(tmp_path)
    _run(task, "--template", str(template), "--keep-template-content")

    report = docx.Document(str(task / "step3_report" / "Report.docx"))
    body = "\n".join(p.text for p in report.paragraphs)
    assert "Template boilerplate" in body


def test_saved_template_is_used_and_can_be_bypassed(tmp_path, monkeypatch):
    template = _make_template(tmp_path / "saved.docx", font_name="Rockwell")
    task = _make_task(tmp_path)
    monkeypatch.setenv("NEQSIM_REPORT_TEMPLATE", str(template))

    _run(task)
    assert docx.Document(str(task / "step3_report" / "Report.docx")
                         ).styles["Normal"].font.name == "Rockwell"

    monkeypatch.delenv("NEQSIM_REPORT_TEMPLATE")
    _run(task, "--no-template")
    assert docx.Document(str(task / "step3_report" / "Report.docx")
                         ).styles["Normal"].font.name != "Rockwell"


def test_missing_template_fails_loudly(tmp_path):
    task = _make_task(tmp_path)
    script = task / "step3_report" / "generate_report.py"
    result = subprocess.run(
        [sys.executable, str(script), "--template", str(tmp_path / "gone.docx")],
        cwd=str(task), capture_output=True, text=True)
    assert result.returncode == 2
    assert "not found" in result.stdout
    assert not (task / "step3_report" / "Report.docx").exists()


def test_template_without_builtin_styles_still_renders(tmp_path):
    """A template stripped of Heading/Table Grid styles must not crash the report."""
    template = tmp_path / "bare.docx"
    doc = docx.Document()
    doc.save(str(template))
    stripped = docx.Document(str(template))
    for name in ("Heading 1", "Heading 2", "Heading 3", "Title", "List Bullet", "Table Grid"):
        try:
            stripped.styles[name].element.getparent().remove(stripped.styles[name].element)
        except KeyError:
            pass
    stripped.save(str(template))

    task = _make_task(tmp_path)
    _run(task, "--template", str(template))
    assert (task / "step3_report" / "Report.docx").is_file()
