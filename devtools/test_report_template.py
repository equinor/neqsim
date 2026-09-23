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
                        == ["Test", "Reference value", "NeqSim value", "Unit",
                            "Deviation [%]", "Status"]]
    assert len(benchmark_tables) == 1
    row = [cell.text for cell in benchmark_tables[0].rows[1].cells]
    assert row[0].lower() == "pressure drop"
    # A numeric "reference" is the reference value, not a citation.
    assert row[1] == "2.8"
    assert row[2] == "3.2"
    assert row[3] == "bar"
    assert row[5] == "FAIL"
    body = "\n".join(paragraph.text for paragraph in report.paragraphs)
    assert "Independent fixture" in body
    # Generator self-checks move to an unnumbered appendix after the chapters.
    assert "Appendix A. Report Quality Checks" in body
    assert "Report Consistency Review" not in body
    assert body.index("Appendix A.") > body.index("References")
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


W_NS = "{http://schemas.openxmlformats.org/wordprocessingml/2006/main}"


@pytest.mark.parametrize("use_template", [False, True])
def test_front_matter_equations_and_tables_are_typeset(tmp_path, use_template):
    """Layout rules from the 2026-09-22 review, for built-in and custom templates."""
    task = _make_task(tmp_path)
    results_path = task / "results.json"
    results = json.loads(results_path.read_text(encoding="utf-8"))
    results["equations"] = [{"label": "Shaft power", "latex": r"W = \frac{\dot{m} H_p}{\eta_p}"}]
    results["approach"] = "Head uses `setUsePolytropicCalc(true)` with $\\eta_p = 0.78$."
    results["risk_evaluation"] = {"risks": [{
        "id": "R1", "category": "Technical", "description": "Cooling medium too warm",
        "likelihood": "Likely", "consequence": "Moderate", "risk_level": "Medium",
        "mitigation": "Add margin"}]}
    results_path.write_text(json.dumps(results), encoding="utf-8")
    args = ["--template", str(_make_template(tmp_path / "t.docx"))] if use_template \
        else ["--no-template"]
    _run(task, *args)
    report = docx.Document(str(_report_docx(task)))

    # Contents headings look like Heading 1 but never list themselves in the TOC.
    toc_heading = next(p for p in report.paragraphs if p.text == "Table of Contents")
    assert toc_heading.style.name == "NeqSim Front Matter Heading"
    outline = toc_heading.style.element.pPr.find(W_NS + "outlineLvl")
    assert outline is not None and outline.get(W_NS + "val") == "9"
    # No paragraph exists only to carry a page break (that is what spills a blank page).
    for paragraph in report.paragraphs:
        breaks = paragraph._p.findall(".//" + W_NS + "br")
        if any(b.get(W_NS + "type") == "page" for b in breaks):
            assert paragraph.text.strip() or paragraph._p.findall(".//" + W_NS + "instrText"), \
                "page break sits in an otherwise empty paragraph"

    # A display equation is one paragraph: centred picture, right-aligned SEQ number.
    equation = next(p for p in report.paragraphs
                    if any("SEQ Equation" in (t.text or "")
                           for t in p._p.findall(".//" + W_NS + "instrText")))
    assert equation._p.findall(".//" + W_NS + "drawing")
    stops = [stop.alignment for stop in equation.paragraph_format.tab_stops]
    assert len(stops) == 2
    assert equation.text.strip().endswith(")")

    # Inline code is monospace, not literal backticks.
    body = "\n".join(p.text for p in report.paragraphs)
    assert "`" not in body
    assert any(run.font.name == "Consolas" and "setUsePolytropicCalc" in run.text
               for p in report.paragraphs for run in p.runs)

    # Scope sub-headings carry no trailing colon.
    assert not any(p.style.name.startswith("Heading") and p.text.endswith(":")
                   for p in report.paragraphs)

    # Table columns fit their longest word instead of being split evenly.
    risk = next(t for t in report.tables if t.rows[0].cells[0].text == "ID")
    widths = [cell.width.inches for cell in risk.rows[0].cells]
    assert widths[0] < min(widths[1:]), widths


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


def _landscape_template(path):
    """A corporate template built for forms: A4 landscape, like the real one."""
    doc = docx.Document()
    section = doc.sections[0]
    section.page_width, section.page_height = section.page_height, section.page_width
    section.orientation = docx.enum.section.WD_ORIENT.LANDSCAPE
    doc.save(str(path))
    return path


def test_landscape_template_is_typeset_on_a_portrait_measure(tmp_path):
    """A landscape template must not force the report onto a 9.5 in measure."""
    template = _landscape_template(tmp_path / "landscape.docx")
    task = _make_task(tmp_path)
    _run(task, "--template", str(template))

    section = docx.Document(str(_report_docx(task))).sections[0]
    assert section.page_width < section.page_height
    measure = (section.page_width - section.left_margin - section.right_margin) / 914400
    assert measure <= 6.75, measure


def test_orientation_can_be_kept_as_the_template_declares(tmp_path):
    template = _landscape_template(tmp_path / "landscape.docx")
    task = _make_task(tmp_path)
    _run(task, "--template", str(template), "--orientation", "template")

    section = docx.Document(str(_report_docx(task))).sections[0]
    assert section.page_width > section.page_height


def test_tables_are_captioned_and_repeat_their_header_row(tmp_path):
    task = _make_task(tmp_path)
    results = json.loads((task / "results.json").read_text(encoding="utf-8"))
    results["tables"] = [{
        "title": "Stage duties",
        "headers": ["Stage", "Duty (MW)"],
        "rows": [["1", 3.5], ["2", 4.25]],
    }]
    (task / "results.json").write_text(json.dumps(results), encoding="utf-8")
    _run(task)

    doc = docx.Document(str(_report_docx(task)))
    captions = [p.text for p in doc.paragraphs if p.style.name == "Caption"]
    assert any(c.startswith("Table 1") for c in captions), captions
    assert any("Stage duties" in c for c in captions), captions
    # A data table titled as Heading 2 would show up in the table of contents.
    assert "Stage duties" not in [p.text for p in doc.paragraphs
                                  if p.style.name.startswith("Heading")]
    from docx.oxml.ns import qn
    repeating = [t for t in doc.tables
                 if (t.rows[0]._tr.find(qn("w:trPr")) is not None
                     and t.rows[0]._tr.find(qn("w:trPr")).find(qn("w:tblHeader"))
                     is not None)]
    assert repeating


def test_large_counts_are_not_printed_in_scientific_notation(tmp_path):
    task = _make_task(tmp_path)
    results = json.loads((task / "results.json").read_text(encoding="utf-8"))
    results["key_results"]["records_pulled"] = 370523
    (task / "results.json").write_text(json.dumps(results), encoding="utf-8")
    _run(task)

    text = "\n".join(
        cell.text for table in docx.Document(str(_report_docx(task))).tables
        for row in table.rows for cell in row.cells)
    assert "370\u00a0523" in text
    assert "3.705e+05" not in text


def test_wide_table_gets_a_landscape_page_and_unbroken_numbers(tmp_path):
    """A 14-column comparison must not split "9.961" over three lines."""
    task = _make_task(tmp_path)
    results = json.loads((task / "results.json").read_text(encoding="utf-8"))
    headers = ["case"] + ["NeqSim dP after {}".format(i) for i in range(11)] \
        + ["OLGA regime", "NeqSim regime"]
    rows = [["2P_GO_RISER"] + [9.961] * 11 + ["SLUG:45,STRATIFIED:1", "SINGLE_PHASE_GAS:80"]
            for _ in range(4)]
    results["tables"] = [{"title": "Steady state", "headers": headers, "rows": rows}]
    results["risk_evaluation"] = {"risks": [{
        "id": "R1", "category": "Technical", "description": "x", "likelihood": "Likely",
        "consequence": "Moderate", "risk_level": "Medium", "mitigation": "y"}]}
    (task / "results.json").write_text(json.dumps(results), encoding="utf-8")
    _run(task, "--no-template")

    doc = docx.Document(str(_report_docx(task)))
    from docx.oxml.ns import qn
    orientation = []
    body = doc.element.body
    section_index = 0
    for child in body.iterchildren():
        if child.tag == qn("w:tbl"):
            orientation.append((child, section_index))
        elif child.tag == qn("w:p") and child.find(qn("w:pPr")) is not None \
                and child.find(qn("w:pPr")).find(qn("w:sectPr")) is not None:
            section_index += 1

    def section_of(table):
        return doc.sections[next(i for el, i in orientation if el is table._tbl)]

    wide = next(t for t in doc.tables if len(t.columns) == 14)
    section = section_of(wide)
    assert section.page_width > section.page_height
    # The report returns to portrait after the wide table.
    assert doc.sections[-1].page_width < doc.sections[-1].page_height
    number_width = wide.rows[1].cells[1].width.inches
    assert number_width >= 0.35, number_width
    # Short tables stay on the portrait body.
    risk = next(t for t in doc.tables if t.rows[0].cells[0].text == "ID")
    risk_section = section_of(risk)
    assert risk_section.page_width < risk_section.page_height
    # Cells are compact: no inherited 6 pt space-after in table cells.
    assert wide.rows[1].cells[1].paragraphs[0].paragraph_format.space_after.pt <= 2


def test_analytical_depth_moves_are_reported(tmp_path):
    task = _make_task(tmp_path)
    results = json.loads((task / "results.json").read_text(encoding="utf-8"))
    results["contributor_ranking"] = [
        {"contributor": "Fouling", "share_pct": 62, "basis": "duty deficit"},
        {"contributor": "Ambient", "share_pct": 21, "basis": "duty deficit"},
    ]
    results["ruled_out"] = ["Tube leak: excluded, chloride below 5 mg/l"]
    (task / "results.json").write_text(json.dumps(results), encoding="utf-8")
    _run(task)

    doc = docx.Document(str(_report_docx(task)))
    headings = [p.text for p in doc.paragraphs if p.style.name.startswith("Heading")]
    assert any("Analytical Depth" in h for h in headings), headings
    body = "\n".join(p.text for p in doc.paragraphs)
    assert "2/7 depth moves reported" in body
    html = next((task / "step3_report").glob("*.html")).read_text(encoding="utf-8")
    assert "Contributors ranked on a common basis" in html
