"""Verifies docs/development/FLOW_ASSURANCE_STUDY_REPORT.md.

Blocks marked ``<!-- run -->`` are executed (PowerShell ``neqsim flow-assurance-report`` lines through the real CLI
entry point, Python blocks with ``exec``); ``<!-- parse -->`` PowerShell blocks must use only documented options and
exist as parser invocations. Every checked block must carry a marker. Links must resolve.
"""
import os
import re
import shlex
import sys
from pathlib import Path

import pytest

DOCS_DIR = Path(__file__).resolve().parent
REPO = DOCS_DIR.parent
DEVTOOLS = REPO / "devtools"
PAGE = DOCS_DIR / "development" / "FLOW_ASSURANCE_STUDY_REPORT.md"
sys.path.insert(0, str(DEVTOOLS))

import flow_assurance_report  # noqa: E402

FENCE_RE = re.compile(r"(?:<!--\s*(?P<marker>[^>]*?)\s*-->\s*\n)?(?P<fence>`{3})(?P<lang>\w*)\n(?P<code>.*?)`{3}", re.DOTALL)
CHECKED = ("python", "powershell", "yaml", "json")


def blocks():
    text = PAGE.read_text(encoding="utf-8")
    return [(m.group("marker"), m.group("lang"), m.group("code")) for m in FENCE_RE.finditer(text)]


def cli_lines(code):
    out = []
    for line in code.splitlines():
        line = line.strip()
        if line.startswith("neqsim flow-assurance-report"):
            out.append(shlex.split(line)[2:])
    return out


def test_front_matter_and_markers():
    text = PAGE.read_text(encoding="utf-8")
    assert text.startswith("---\ntitle: ") and "\ndescription: " in text.split("---", 2)[1]
    assert "\\(" not in text and "\\[" not in text
    for marker, lang, code in blocks():
        if lang in CHECKED:
            assert marker and marker.strip() in ("run", "parse"), "unmarked %s block: %s" % (lang, code[:60])


def test_documented_commands_use_existing_options():
    parser = flow_assurance_report.build_parser()
    for marker, lang, code in blocks():
        if lang == "powershell":
            lines = cli_lines(code)
            assert lines
            for argv in lines:
                parser.parse_args(argv)


def test_run_blocks_execute(tmp_path, monkeypatch):
    monkeypatch.chdir(tmp_path)
    monkeypatch.syspath_prepend(str(DEVTOOLS))
    monkeypatch.chdir(REPO)
    for marker, lang, code in blocks():
        if (marker or "").strip() != "run":
            continue
        if lang == "powershell":
            for argv in cli_lines(code):
                argv = [str(tmp_path / a) if a == "study_inputs" else a for a in argv]
                assert flow_assurance_report.main(argv) == 0
            assert (tmp_path / "study_inputs" / "route.yaml").exists()
        elif lang == "python":
            exec(compile(code, "<doc-snippet>", "exec"), {"__name__": "__doc__"})


def test_relative_links_resolve():
    text = PAGE.read_text(encoding="utf-8")
    for target in re.findall(r"\]\((\.\./?[^)#]+|[A-Z_]+\.md)(?:#[^)]*)?\)", text):
        assert (PAGE.parent / target).resolve().exists(), target


def test_documented_output_layout_matches_the_code():
    src = "".join((DEVTOOLS / n).read_text(encoding="utf-8") for n in ("flow_assurance_report.py", "fa_diagrams.py", "fa_report.py"))
    for token in ("handoff", "dataset", "drawings", "figures", "cache", "report_facts.json", "layout_spec.json", "flow_assurance_study.json"):
        assert token in src, token
