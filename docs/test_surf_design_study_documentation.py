"""Verifies docs/development/SURF_DESIGN_STUDY.md.

``<!-- run -->`` Python blocks are executed from the repository root; ``<!-- parse -->`` PowerShell blocks must
use only documented options of ``neqsim surf-study``. Every checked block must carry a marker. Links must resolve.
"""
import re
import shlex
import sys
from pathlib import Path

DOCS_DIR = Path(__file__).resolve().parent
REPO = DOCS_DIR.parent
DEVTOOLS = REPO / "devtools"
PAGE = DOCS_DIR / "development" / "SURF_DESIGN_STUDY.md"
sys.path.insert(0, str(DEVTOOLS))

import surf_study  # noqa: E402

FENCE_RE = re.compile(r"(?:<!--\s*(?P<marker>[^>]*?)\s*-->\s*\n)?(?P<fence>`{3})(?P<lang>\w*)\n(?P<code>.*?)`{3}", re.DOTALL)
CHECKED = ("python", "powershell", "yaml", "json")


def blocks():
    text = PAGE.read_text(encoding="utf-8")
    return [(m.group("marker"), m.group("lang"), m.group("code")) for m in FENCE_RE.finditer(text)]


def test_front_matter_and_markers():
    text = PAGE.read_text(encoding="utf-8")
    assert text.startswith("---\ntitle: ") and "\ndescription: " in text.split("---", 2)[1]
    assert "\\(" not in text and "\\[" not in text
    for marker, lang, code in blocks():
        if lang in CHECKED:
            assert marker and marker.strip() in ("run", "parse"), "unmarked %s block: %s" % (lang, code[:60])


def test_documented_commands_use_existing_options():
    parser = surf_study.build_parser()
    seen = 0
    for _marker, lang, code in blocks():
        if lang != "powershell":
            continue
        for line in code.splitlines():
            line = line.strip()
            if line.startswith("neqsim surf-study"):
                parser.parse_args(shlex.split(line)[2:])
                seen += 1
    assert seen >= 3


def test_run_blocks_execute(monkeypatch):
    monkeypatch.chdir(REPO)
    for marker, lang, code in blocks():
        if (marker or "").strip() == "run" and lang == "python":
            exec(compile(code, "<doc-snippet>", "exec"), {"__name__": "__doc__"})


def test_relative_links_resolve():
    text = PAGE.read_text(encoding="utf-8")
    for target in re.findall(r"\]\((\.\.?/[^)#]+|[A-Z_]+\.md)(?:#[^)]*)?\)", text):
        assert (PAGE.parent / target).resolve().exists(), target


def test_documented_output_layout_matches_the_code():
    src = "".join((DEVTOOLS / n).read_text(encoding="utf-8") for n in ("surf_study.py", "surf_diagrams.py", "surf_model.py", "surf_validate.py"))
    for token in ("handoff", "dataset", "drawings", "figures", "cache", "layout_spec.json", "mapping_manifest.json", "validation_report"):
        assert token in src, token
