"""PDF output regression tests for the task report generator.

The generator's `report.formats` list has always accepted `pdf`, but nothing
acted on it, so a task that asked for PDF silently received only DOCX and HTML.
These tests pin the resolution rules and the file naming. The conversion itself
needs Microsoft Word or LibreOffice and is skipped when neither is installed.
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
    spec = importlib.util.spec_from_file_location("generate_report_pdf_test", GENERATOR)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def _pdf_backend_available():
    """True when a DOCX-to-PDF backend exists on this machine."""
    if shutil.which("soffice") or shutil.which("libreoffice"):
        return True
    if sys.platform != "win32":
        return False
    try:
        import win32com.client  # noqa: F401
    except ImportError:
        return False
    return True


def _make_task(root):
    """Create a minimal task folder whose study_config requests PDF."""
    task = root / "2026-09-15_pdf_task"
    (task / "step3_report").mkdir(parents=True)
    (task / "step1_scope_and_research").mkdir()
    shutil.copy(str(GENERATOR), str(task / "step3_report" / "generate_report.py"))
    (task / "results.json").write_text(json.dumps({
        "key_results": {"outlet_temperature_C": -18.5},
        "validation": {"acceptance_criteria_met": True},
        "conclusions": "PDF smoke test.",
    }), encoding="utf-8")
    (task / "study_config.yaml").write_text(
        "study:\n"
        "  title: \"PDF Format Task\"\n"
        "report:\n"
        "  formats:\n"
        "    - docx\n"
        "    - html\n"
        "    - pdf\n",
        encoding="utf-8")
    return task


def test_want_pdf_output_honours_config_formats(monkeypatch):
    """A `pdf` entry in report.formats turns PDF generation on."""
    module = _load_generator()
    monkeypatch.setattr(module.sys, "argv", ["generate_report.py"])
    assert module.want_pdf_output({"report": {"formats": ["docx", "html", "pdf"]}})
    assert module.want_pdf_output({"report": {"formats": ["PDF"]}})
    assert not module.want_pdf_output({"report": {"formats": ["docx", "html"]}})
    assert not module.want_pdf_output({})


def test_want_pdf_output_flags_override_config(monkeypatch):
    """--pdf enables PDF without config, and --no-pdf overrides the config."""
    module = _load_generator()
    monkeypatch.setattr(module.sys, "argv", ["generate_report.py", "--pdf"])
    assert module.want_pdf_output({"report": {"formats": ["docx"]}})
    monkeypatch.setattr(module.sys, "argv", ["generate_report.py", "--no-pdf"])
    assert not module.want_pdf_output({"report": {"formats": ["pdf"]}})


def test_pdf_named_after_report_title():
    """The PDF is named after the study title, like the DOCX and HTML."""
    module = _load_generator()
    basename = module.apply_report_output_names("Hydrate margin for the export line")
    assert module.PDF_FILE.endswith(basename + ".pdf")
    assert module.PAPER_PDF_FILE.endswith(basename + "_Paper.pdf")


def test_missing_source_document_does_not_raise(tmp_path):
    """A PDF failure must report, not abort an otherwise complete report run."""
    module = _load_generator()
    assert module.convert_docx_to_pdf(
        str(tmp_path / "absent.docx"), str(tmp_path / "out.pdf")) is False


@pytest.mark.skipif(not _pdf_backend_available(),
                    reason="needs Microsoft Word (pywin32) or LibreOffice")
def test_generator_writes_pdf_when_config_requests_it(tmp_path):
    """End-to-end: report.formats containing pdf produces a real PDF file."""
    pytest.importorskip("docx")
    task = _make_task(tmp_path)
    script = task / "step3_report" / "generate_report.py"
    result = subprocess.run([sys.executable, str(script)],
                            cwd=str(task), capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    pdfs = list((task / "step3_report").glob("*.pdf"))
    assert pdfs, "no PDF written: " + result.stdout
    assert pdfs[0].stat().st_size > 1000
    assert pdfs[0].read_bytes()[:4] == b"%PDF"
