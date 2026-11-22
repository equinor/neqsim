"""Verifies docs/development/PROCESS_STUDY_REPORT.md.

Blocks marked ``<!-- file: NAME -->`` are written to a work folder (``; run`` also executes a Python file), blocks marked
``<!-- run -->`` are PowerShell blocks whose ``neqsim process-report`` lines are executed in document order, and
``<!-- parse -->`` blocks must parse. Every python, powershell, yaml and json block must carry one of the markers, the
embedded worked example must equal the shipped fixtures, every documented option must exist, and the output table must
match the folder produced by the first run.
"""
import glob
import json
import os
import re
import shlex
import sys
from pathlib import Path

import pytest
import yaml

DOCS_DIR = Path(__file__).resolve().parent
REPO = DOCS_DIR.parent
DEVTOOLS = REPO / "devtools"
PAGE = DOCS_DIR / "development" / "PROCESS_STUDY_REPORT.md"
sys.path.insert(0, str(DEVTOOLS))

FENCE_RE = re.compile(r"(?:<!--\s*(?P<marker>[^>]*?)\s*-->\s*\n)?(?P<fence>`{3})(?P<lang>\w*)\n(?P<code>.*?)`{3}", re.DOTALL)
CHECKED_LANGS = ("python", "powershell", "yaml", "json")


def page_text():
    return PAGE.read_text(encoding="utf-8")


def code_blocks():
    return [(m.group("marker"), m.group("lang"), m.group("code")) for m in FENCE_RE.finditer(page_text())]


def _norm(text):
    return "\n".join(line.rstrip() for line in text.replace("\r\n", "\n").strip().split("\n"))


def test_front_matter_and_every_code_block_is_marked():
    text = page_text()
    assert text.startswith("---\ntitle: ") and "\ndescription: " in text.split("---", 2)[1]
    assert "\\(" not in text and "\\[" not in text
    for marker, lang, code in code_blocks():
        if lang in CHECKED_LANGS:
            assert marker and re.match(r"(file: [\w./-]+(; run)?|run|parse)$", marker.strip()), "unmarked %s block: %s" % (lang, code[:60])


def test_worked_example_equals_the_shipped_fixtures():
    fixtures = {"gas_plant_model.py": "gas_plant_model.py", "cases.yaml": "cases.yaml", "oil_stabilisation.json": "oil_stabilisation.json",
                "oil_cases.yaml": "oil_stabilisation_cases.yaml"}
    found = {}
    for marker, _lang, code in code_blocks():
        match = re.match(r"file: ([\w./-]+)", marker or "")
        if match and match.group(1) in fixtures:
            found[match.group(1)] = code
    assert set(found) == set(fixtures)
    for name, fixture in fixtures.items():
        assert _norm(found[name]) == _norm((DEVTOOLS / "psr_examples" / fixture).read_text(encoding="utf-8")), name


def test_structured_blocks_parse():
    for marker, lang, code in code_blocks():
        if lang == "yaml":
            assert yaml.safe_load(code)
        elif lang == "json":
            assert json.loads(code)
        elif lang == "python":
            compile(code, marker or "block", "exec")


def test_relative_links_resolve_and_page_is_indexed():
    for target in re.findall(r"\]\((?!https?://|#|mailto:)([^)\s]+)\)", page_text()):
        path = (PAGE.parent / target.split("#")[0]).resolve()
        assert path.exists(), target
    assert "PROCESS_STUDY_REPORT.md" in (DOCS_DIR / "REFERENCE_MANUAL_INDEX.md").read_text(encoding="utf-8")
    assert "PROCESS_STUDY_REPORT" in (DOCS_DIR / "development" / "README.md").read_text(encoding="utf-8")
    assert "PROCESS_STUDY_REPORT" in (DEVTOOLS / "README.md").read_text(encoding="utf-8")
    assert "PROCESS_STUDY_REPORT" in (REPO / ".github" / "skills" / "process-study-report" / "SKILL.md").read_text(encoding="utf-8")
    assert "PROCESS_STUDY_REPORT" in (REPO / ".github" / "agents" / "process-study-report.agent.md").read_text(encoding="utf-8")


def test_documented_options_match_the_command():
    import process_report

    real = {opt for action in process_report.build_parser()._actions for opt in action.option_strings if opt.startswith("--")}
    documented = set(re.findall(r"(?<![\w-])(--[a-z][a-z-]+)", page_text()))
    other_commands = {"--set-report-template"}
    assert documented - real - other_commands == set(), documented - real
    assert real - documented - {"--help"} == set(), real - documented


@pytest.fixture(scope="module")
def doc_run(tmp_path_factory):
    """Write the files, then run every documented command in document order."""
    pytest.importorskip("jneqsim")
    import process_report

    work = tmp_path_factory.mktemp("docrun")
    blocks = code_blocks()
    for marker, _lang, code in blocks:
        match = re.match(r"file: ([\w./-]+)", marker or "")
        if match:
            (work / match.group(1)).write_text(code, encoding="utf-8")
    old_cwd = os.getcwd()
    os.chdir(work)
    results = []
    try:
        for marker, lang, code in blocks:
            marker = (marker or "").strip()
            if lang == "python" and marker.endswith("; run"):
                exec(compile(code, marker, "exec"), {"__name__": "__main__"})
                results.append(("python", marker, 0))
            elif lang == "powershell" and marker == "run":
                for line in code.strip().splitlines():
                    line = line.strip()
                    if not line.startswith("neqsim process-report"):
                        continue
                    argv = shlex.split(line)[2:]
                    if "--quiet" not in argv:
                        argv.append("--quiet")
                    if "--no-word" not in argv:
                        argv.append("--no-word")
                    results.append(("cli", line, process_report.main(argv)))
    finally:
        os.chdir(old_cwd)
    return work, results


def test_every_documented_command_runs(doc_run):
    _work, results = doc_run
    cli = [r for r in results if r[0] == "cli"]
    assert len(cli) >= 6
    for kind, line, code in results:
        assert code == 0, line


def test_documented_outputs_exist(doc_run):
    work, _ = doc_run
    section = page_text().split("## 5. Outputs", 1)[1].split("\n## ", 1)[0]
    paths = re.findall(r"^\| `([^`]+)` \|", section, re.MULTILINE)
    assert len(paths) >= 20
    for rel in paths:
        assert glob.glob(str(work / "report" / rel)), rel


def test_documented_variants_produced_their_outputs(doc_run):
    work, _ = doc_run
    assert (work / "gas_inputs" / "inputs" / "basis.yaml").exists() and not (work / "gas_inputs" / "report").exists()
    assert (work / "gas_report" / "report" / "validation_report.json").exists()
    assert (work / "oil_report" / "data" / "process_data.json").exists()
    assert (work / "serialized_report" / "report" / "validation_report.json").exists()
    assert (work / "from_data" / "report" / "validation_report.json").exists()
    assert (work / "api_report" / "report" / "validation_report.json").exists()
    summary = json.loads((work / "report" / "run_summary.json").read_text(encoding="utf-8"))
    assert summary["readiness"] == "SCREENING - basis not confirmed" and summary["validation"] != "FAIL"
