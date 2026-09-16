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


def _report_docx(task):
    """Return the generated Word report — its file name is the report title."""
    produced = sorted((task / "step3_report").glob("*.docx"))
    assert len(produced) == 1, produced
    return produced[0]


def test_report_file_is_named_after_the_title(tmp_path):
    task = _make_task(tmp_path)
    _run(task, "--no-template", "--title", "Hydrate margin: export line")

    assert _report_docx(task).name == "Hydrate_margin_export_line.docx"
    assert (task / "step3_report" / "Hydrate_margin_export_line.html").is_file()


def test_renaming_the_study_removes_the_superseded_report(tmp_path):
    task = _make_task(tmp_path)
    _run(task, "--no-template", "--title", "First title")
    _run(task, "--no-template", "--title", "Second title")

    assert _report_docx(task).name == "Second_title.docx"
    assert not (task / "step3_report" / "First_title.docx").exists()
    assert not (task / "step3_report" / "First_title.html").exists()


def test_corrupt_cleanup_manifest_warns_and_recovers(tmp_path):
    task = _make_task(tmp_path)
    report_dir = task / "step3_report"
    manifest = report_dir / ".report_outputs.json"
    manifest.write_text("{invalid", encoding="utf-8")
    (report_dir / "Report.html").write_text("legacy report", encoding="utf-8")

    result = _run(task, "--no-template", "--title", "Recovered report")

    assert "could not read report output manifest" in result.stderr
    assert not (report_dir / "Report.html").exists()
    assert _report_docx(task).name == "Recovered_report.docx"
    assert json.loads(manifest.read_text(encoding="utf-8"))["files"] == [
        "Recovered_report.docx", "Recovered_report.html",
    ]


def test_unwritable_cleanup_manifest_warns_without_losing_report(tmp_path):
    task = _make_task(tmp_path)
    (task / "step3_report" / ".report_outputs.json").mkdir()

    result = _run(task, "--no-template", "--title", "Preserved report")

    assert "could not save report output manifest" in result.stderr
    assert _report_docx(task).name == "Preserved_report.docx"


@pytest.mark.parametrize("schema", ["tests_list", "named_mapping"])
def test_benchmarks_and_consistency_survive_canonical_generation(tmp_path, schema):
    task = _make_task(tmp_path)
    results_path = task / "results.json"
    results = json.loads(results_path.read_text(encoding="utf-8"))
    comparison = {"neqsim": 3.2, "reference": 2.8, "unit": "bar", "deviation_pct": 25.0}
    if schema == "tests_list":
        comparison.update(parameter="Pressure drop", **{"pass": False})
        benchmark = {"source": "Independent fixture", "tests": [comparison]}
    else:
        comparison["status"] = "FAIL"
        benchmark = {"source": "Independent fixture", "pressure_drop": comparison}
    results.update(benchmark_validation=benchmark, conclusions="The operation is confirmed safe.")
    results_path.write_text(json.dumps(results), encoding="utf-8")

    _run(task, "--no-template", "--title", "Benchmark parity")

    report = docx.Document(str(_report_docx(task)))
    benchmark_tables = [table for table in report.tables
                        if [cell.text for cell in table.rows[0].cells]
                        == ["Test", "Description", "Status", "Details"]]
    assert len(benchmark_tables) == 1
    row = [cell.text for cell in benchmark_tables[0].rows[1].cells]
    assert row[0].lower() == "pressure drop"
    assert row[2] == "FAIL"
    assert "3.2" in row[3]
    # The reference value and its unit must remain available in either schema.
    assert "2.8" in " ".join(row)
    assert "bar" in row[3]
    body = "\n".join(paragraph.text for paragraph in report.paragraphs)
    assert "Independent fixture" in body
    assert "Report Consistency Review" in body
    assert "benchmark tests FAILED" in body
    html = (task / "step3_report" / "Benchmark_parity.html").read_text(encoding="utf-8")
    assert "Independent fixture" in html
    assert 'class="fail"><strong>FAIL</strong>' in html

    fixes_path = task / "fixes_needed.json"
    fixes = json.loads(fixes_path.read_text(encoding="utf-8"))
    assert any(issue["fix_type"] == "calculation" and issue["severity"] == "ERROR"
               for issue in fixes)
    stored = json.loads(results_path.read_text(encoding="utf-8"))
    assert stored["conclusions"] == results["conclusions"]
    assert stored["benchmark_validation"] == benchmark

    # A corrected rerun clears the generated calculation findings.
    stored.pop("benchmark_validation")
    stored["approach"] = "Checked the model against the fixture."
    results_path.write_text(json.dumps(stored), encoding="utf-8")
    _run(task, "--no-template", "--title", "Benchmark parity")
    assert json.loads(fixes_path.read_text(encoding="utf-8")) == []


def test_report_inherits_template_styling(tmp_path):
    template = _make_template(tmp_path / "company template.docx")
    task = _make_task(tmp_path)
    _run(task, "--template", str(template))

    report = docx.Document(str(_report_docx(task)))
    assert report.styles["Normal"].font.name == "Garamond"
    assert "ACME Engineering" in report.sections[0].header.paragraphs[0].text
    body = "\n".join(p.text for p in report.paragraphs)
    assert "Template boilerplate" not in body
    assert "Table of Contents" in body


def test_keep_template_content_retains_boilerplate(tmp_path):
    template = _make_template(tmp_path / "company template.docx")
    task = _make_task(tmp_path)
    _run(task, "--template", str(template), "--keep-template-content")

    report = docx.Document(str(_report_docx(task)))
    body = "\n".join(p.text for p in report.paragraphs)
    assert "Template boilerplate" in body


def test_saved_template_is_used_and_can_be_bypassed(tmp_path, monkeypatch):
    template = _make_template(tmp_path / "saved.docx", font_name="Rockwell")
    task = _make_task(tmp_path)
    monkeypatch.setenv("NEQSIM_REPORT_TEMPLATE", str(template))

    _run(task)
    assert docx.Document(str(_report_docx(task))
                         ).styles["Normal"].font.name == "Rockwell"

    monkeypatch.delenv("NEQSIM_REPORT_TEMPLATE")
    _run(task, "--no-template")
    assert docx.Document(str(_report_docx(task))
                         ).styles["Normal"].font.name != "Rockwell"


def test_missing_template_fails_loudly(tmp_path):
    task = _make_task(tmp_path)
    script = task / "step3_report" / "generate_report.py"
    result = subprocess.run(
        [sys.executable, str(script), "--template", str(tmp_path / "gone.docx")],
        cwd=str(task), capture_output=True, text=True)
    assert result.returncode == 2
    assert "not found" in result.stdout
    assert not list((task / "step3_report").glob("*.docx"))


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
    assert _report_docx(task).is_file()
