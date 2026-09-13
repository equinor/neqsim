"""
generate_report.py - Generate Word/HTML reports and scientific papers for this task.

Usage:
    pip install python-docx matplotlib   (one-time setup)
    python step3_report/generate_report.py            # Technical report only
    python step3_report/generate_report.py --paper     # Also generate scientific paper
    python step3_report/generate_report.py --paper-only  # Scientific paper only
    python step3_report/generate_report.py --template "C:/…/company template.docx"
    python step3_report/generate_report.py --no-template  # ignore the saved template
    python step3_report/generate_report.py --keep-template-content
    python step3_report/generate_report.py --title "..." --author "..."
    python devtools/task_template/step3_report/generate_report.py --task-dir PATH

The canonical copy of this script lives in devtools/task_template/. Run it
against any task folder with `neqsim report <task folder>` (or --task-dir /
NEQSIM_TASK_DIR) so a fix here applies to task folders created earlier.

The report title is the STUDY title, and the task is stated at the top of the
report. Title/author resolution order:
    --title / --author  >  NEQSIM_REPORT_TITLE / NEQSIM_REPORT_AUTHOR  >
    study_config.yaml (study.title, study.author)  >  the first heading of
    task_spec.md  >  a task-local generate_report.py copy  >  the folder name.
The task statement comes from results.json ("task_statement" / "objective"),
else the Objective/Task Description section of task_spec.md, else study.title.

Report.docx is built from a Word template when one is configured, so company
fonts, colours, styles, headers, and footers apply. Resolution order:
--template PATH, NEQSIM_REPORT_TEMPLATE, then the saved `report_template` in
~/.neqsim/task_defaults.json (set once with `neqsim --set-report-template PATH`).
The template's own body text is dropped unless --keep-template-content is given;
page setup, headers, footers, and styles are always inherited. Paper.docx keeps
journal formatting and ignores the template.

This script AUTO-READS data from the task folder:
    - study_config.yaml                    -> defines depth, notebook plan, quality gates
  - step1_scope_and_research/task_spec.md  -> populates Scope & Standards
  - results.json (task root)               -> populates Results + Validation
  - figures/*.png                          -> embeds all plots
  - results.json "equations"               -> renders equations (KaTeX/images)
  - results.json "figure_captions"         -> custom captions for figures

It produces (file names are the report title, so a deliverable is identifiable
outside its task folder — e.g. "Hydrate margin for the export line" becomes
Hydrate_margin_for_the_export_line.docx):
  - step3_report/<Title>.docx        (Word document for formal distribution)
  - step3_report/<Title>.html        (navigable HTML with sidebar, KaTeX equations)
  - step3_report/<Title>_Paper.docx  (scientific paper in Word format, with --paper)
  - step3_report/<Title>_Paper.html  (scientific paper in HTML format, with --paper)
Report files written under an earlier title are removed, so a renamed study does
not leave a superseded deliverable beside the current one.

If results.json or task_spec.md are missing, the report uses placeholder text.
Customize MANUAL_SECTIONS below for content that can't be auto-generated.
"""
import os
import re
import sys
import glob
import json
import base64
import io
import re
import sqlite3
from datetime import date

try:
    from docx import Document
    from docx.shared import Inches, Pt, RGBColor
    from docx.enum.text import WD_ALIGN_PARAGRAPH
    from docx.enum.table import WD_TABLE_ALIGNMENT
    from docx.enum.style import WD_STYLE_TYPE
    from docx.oxml.ns import nsdecls, qn
    from docx.oxml import parse_xml
except ImportError:
    print("ERROR: python-docx not installed. Run: pip install python-docx")
    sys.exit(1)

# Optional: matplotlib for rendering equations to images (Word report)
try:
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
    HAS_MATPLOTLIB = True
except ImportError:
    HAS_MATPLOTLIB = False

# ── Word typography ──────────────────────────────────────
# Floors applied to the template's own styles (see _apply_readable_typography).
BODY_PT = 11.0
HEADING1_PT = 16.0
HEADING2_PT = 13.0
HEADING3_PT = 11.5
TABLE_PT = 10.0
CAPTION_PT = 9.5
BODY_SPACE_AFTER_PT = 6.0

# Equation images are rendered at EQ_FONT_PT and then placed at their NATURAL
# size, so the maths comes out at EQ_FONT_PT in the document. Forcing a fixed
# picture width instead magnifies a short equation to the width of the page.
EQ_FONT_PT = 13.0
EQ_RENDER_DPI = 300

# ── Paths ────────────────────────────────────────────────
def _resolve_task_dir() -> str:
    """Return the task folder: --task-dir, NEQSIM_TASK_DIR, else this file's parent.

    Allowing an external task folder lets the canonical devtools copy of this
    script serve any task, so a fix here reaches task folders that were created
    with an older vendored copy (`neqsim report <task folder>`).
    """
    if "--task-dir" in sys.argv:
        index = sys.argv.index("--task-dir") + 1
        if index >= len(sys.argv):
            print("ERROR: --task-dir requires a path")
            sys.exit(2)
        return os.path.abspath(sys.argv[index])
    env_dir = os.environ.get("NEQSIM_TASK_DIR")
    if env_dir:
        return os.path.abspath(os.path.expandvars(os.path.expanduser(env_dir)))
    return os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


TASK_DIR = _resolve_task_dir()
FIG_DIR = os.path.join(TASK_DIR, "figures")
REPORT_DIR = os.path.join(TASK_DIR, "step3_report")
REPORT_BASENAME = "Report"      # replaced by the report title in __main__
DOCX_FILE = os.path.join(REPORT_DIR, "Report.docx")
HTML_FILE = os.path.join(REPORT_DIR, "Report.html")
PAPER_DOCX_FILE = os.path.join(REPORT_DIR, "Paper.docx")
PAPER_HTML_FILE = os.path.join(REPORT_DIR, "Paper.html")
RESULTS_FILE = os.path.join(TASK_DIR, "results.json")
TASK_SPEC_FILE = os.path.join(TASK_DIR, "step1_scope_and_research", "task_spec.md")
STUDY_CONFIG_FILE = os.path.join(TASK_DIR, "study_config.yaml")
OUTPUT_MANIFEST_FILE = os.path.join(REPORT_DIR, ".report_outputs.json")
LEGACY_OUTPUT_NAMES = ("Report.docx", "Report.html", "Paper.docx", "Paper.html")
REPORT_NAME_MAX_CHARS = 120

if not os.path.isdir(REPORT_DIR):
    os.makedirs(REPORT_DIR)


def slugify_report_name(title, fallback="Report"):
    """Return a filesystem-safe file base name derived from the report title.

    Parameters
    ----------
    title : str
        The report (study) title.
    fallback : str
        Name used when the title yields nothing usable.

    Returns
    -------
    str
        Title with path-hostile characters removed and spaces as underscores.
    """
    text = str(title or "")
    text = re.sub(r"[\\/:*?\"<>|\r\n\t]+", " ", text)
    text = re.sub(r"[^0-9A-Za-z\u00C0-\u024F &()+,._-]+", " ", text)
    text = re.sub(r"\s+", " ", text).strip(" .-_")
    if not text:
        return fallback
    name = re.sub(r"_+", "_", text.replace(" ", "_")).strip("_")
    if len(name) > REPORT_NAME_MAX_CHARS:
        name = name[:REPORT_NAME_MAX_CHARS].rstrip("_-")
    return name or fallback


def apply_report_output_names(title):
    """Name the generated report files after the report title.

    Every task ships deliverables whose file name is the study title, so a
    report is identifiable outside its task folder.

    Parameters
    ----------
    title : str
        Resolved report title.

    Returns
    -------
    str
        The base name used for the generated files.
    """
    global REPORT_BASENAME, DOCX_FILE, HTML_FILE, PAPER_DOCX_FILE, PAPER_HTML_FILE
    REPORT_BASENAME = slugify_report_name(title)
    DOCX_FILE = os.path.join(REPORT_DIR, REPORT_BASENAME + ".docx")
    HTML_FILE = os.path.join(REPORT_DIR, REPORT_BASENAME + ".html")
    PAPER_DOCX_FILE = os.path.join(REPORT_DIR, REPORT_BASENAME + "_Paper.docx")
    PAPER_HTML_FILE = os.path.join(REPORT_DIR, REPORT_BASENAME + "_Paper.html")
    return REPORT_BASENAME


def prune_superseded_outputs(current_files):
    """Delete report files this generator wrote under an earlier title.

    Without this, renaming a study leaves the superseded deliverable beside the
    current one and a reader cannot tell which is live.

    Parameters
    ----------
    current_files : list of str
        Paths written by this run.
    """
    current = set(os.path.abspath(path) for path in current_files
                  if path and os.path.exists(path))
    previous = list(LEGACY_OUTPUT_NAMES)
    if os.path.exists(OUTPUT_MANIFEST_FILE):
        try:
            with open(OUTPUT_MANIFEST_FILE, encoding="utf-8-sig") as manifest:
                previous.extend(json.load(manifest).get("files", []))
        except (OSError, ValueError):
            pass
    for name in previous:
        stale = os.path.abspath(os.path.join(REPORT_DIR, os.path.basename(name)))
        if stale in current or not os.path.isfile(stale):
            continue
        try:
            os.remove(stale)
            print("Removed superseded report file: {}".format(os.path.basename(stale)))
        except OSError as error:
            print("NOTE: could not remove {}: {}".format(stale, error))
    try:
        with open(OUTPUT_MANIFEST_FILE, "w", encoding="utf-8") as manifest:
            json.dump({"files": sorted(os.path.basename(p) for p in current)},
                      manifest, indent=2)
    except OSError:
        pass

# ── Word template (corporate branding) ───────────────────
# Resolution order: --template PATH, NEQSIM_REPORT_TEMPLATE, the saved
# `report_template` in ~/.neqsim/task_defaults.json (neqsim --set-report-template),
# then built-in styling. Word documents are then built on the template so the
# company fonts, colours, styles, headers, and footers apply.
TASK_DEFAULTS_FILE = os.path.expanduser("~/.neqsim/task_defaults.json")
REPORT_TEMPLATE_EXTENSIONS = (".docx", ".dotx")
REPORT_TEMPLATE = None          # set in __main__ from CLI/env/settings
KEEP_TEMPLATE_CONTENT = False   # --keep-template-content keeps the template body
TEMPLATE_NUMBERS_HEADINGS = False  # template Heading styles carry their own numbering


def resolve_report_template(explicit=None, allow_saved=True):
    """Resolve the Word template reports are built from, or None if unset.

    Parameters
    ----------
    explicit : str or None
        Template path from --template; overrides environment and settings.
    allow_saved : bool
        When False (--no-template), the saved user setting is ignored.

    Returns
    -------
    str or None
        Absolute path to an existing .docx/.dotx file, or None.

    Raises
    ------
    ValueError
        If a template is configured but is not a readable Word file.
    """
    selected = explicit or os.environ.get("NEQSIM_REPORT_TEMPLATE")
    if not selected and allow_saved and os.path.exists(TASK_DEFAULTS_FILE):
        with open(TASK_DEFAULTS_FILE, encoding="utf-8-sig") as source:
            selected = json.load(source).get("report_template")
    if not selected:
        return None
    if not isinstance(selected, str) or not selected.strip():
        raise ValueError("Report template must be a path to a .docx or .dotx file")
    path = os.path.abspath(os.path.expandvars(os.path.expanduser(selected)))
    if os.path.splitext(path)[1].lower() not in REPORT_TEMPLATE_EXTENSIONS:
        raise ValueError("Report template must be a .docx or .dotx file: {}".format(path))
    if not os.path.isfile(path):
        raise ValueError("Report template not found: {}".format(path))
    return path


def _clear_document_body(doc):
    """Drop the template's own body content, keeping page setup and headers."""
    body = doc.element.body
    for child in list(body):
        if child.tag == qn("w:sectPr"):
            continue
        body.remove(child)


def _ensure_paragraph_style(doc, name, size_pt=None, bold=False):
    """Create a minimal stand-in when the template lacks a style we write to."""
    try:
        doc.styles[name]
        return
    except KeyError:
        pass
    style = doc.styles.add_style(name, WD_STYLE_TYPE.PARAGRAPH)
    try:
        style.base_style = doc.styles["Normal"]
    except KeyError:
        pass
    if size_pt:
        style.font.size = Pt(size_pt)
    style.font.bold = bold


def _apply_readable_typography(doc):
    """Raise style sizes that fall below the readable floor.

    Only ever increases a size, so a template whose body text and headings are
    already reasonable keeps its own design. Corporate templates built for
    dense forms often ship Normal at 9-9.5 pt with all heading levels at the
    same size, which leaves the report body small and the section hierarchy
    invisible once the template body is cleared.
    """
    floors = (
        ("Normal", BODY_PT),
        ("Heading 1", HEADING1_PT),
        ("Heading 2", HEADING2_PT),
        ("Heading 3", HEADING3_PT),
    )
    for name, floor_pt in floors:
        try:
            style = doc.styles[name]
        except KeyError:
            continue
        current = style.font.size.pt if style.font.size else None
        if current is None or current < floor_pt:
            style.font.size = Pt(floor_pt)
        if name.startswith("Heading"):
            style.font.bold = True
    # A form template often sets space_after = 0, which glues consecutive
    # paragraphs together and hides the paragraph structure entirely.
    try:
        body = doc.styles["Normal"].paragraph_format
        if body.space_after is None or body.space_after < Pt(BODY_SPACE_AFTER_PT):
            body.space_after = Pt(BODY_SPACE_AFTER_PT)
    except KeyError:
        pass


def _new_document():
    """Return a Word document based on the configured template, if any."""
    global TEMPLATE_NUMBERS_HEADINGS
    if not REPORT_TEMPLATE:
        doc = Document()
        _apply_readable_typography(doc)
        return doc
    doc = Document(REPORT_TEMPLATE)
    if not KEEP_TEMPLATE_CONTENT:
        _clear_document_body(doc)
    for name, size_pt in (("Title", 28), ("Heading 1", HEADING1_PT),
                          ("Heading 2", HEADING2_PT),
                          ("Heading 3", HEADING3_PT), ("List Bullet", None)):
        _ensure_paragraph_style(doc, name, size_pt, bold=size_pt is not None)
    _apply_readable_typography(doc)
    return doc


def _style_numbering_active(doc, style_name, _seen=None):
    """Return true when a heading style carries automatic Word numbering.

    A corporate template usually numbers its heading styles itself. Writing our
    own "7. " prefix into such a heading produces "8   7. Solution Workflow" —
    two numbering schemes that also disagree, because Word counts the cover and
    contents headings too.
    """
    if _seen is None:
        _seen = set()
    if style_name in _seen:
        return False
    _seen.add(style_name)
    try:
        style = doc.styles[style_name]
    except KeyError:
        return False
    element = style.element
    if element.find(qn("w:pPr")) is not None:
        if element.find(qn("w:pPr")).find(qn("w:numPr")) is not None:
            return True
    based_on = element.find(qn("w:basedOn"))
    if based_on is not None:
        parent = based_on.get(qn("w:val"))
        if parent:
            return _style_numbering_active(doc, parent, _seen)
    return False


_HEADING_NUMBERING_CACHE = {}
_MANUAL_HEADING_NUMBER = re.compile(r"^\s*\d+(?:\.\d+)*[.)]?\s+")


def _heading_numbering_active(doc, level):
    """Cache the numbering check per document and heading level."""
    key = (id(doc), level)
    if key not in _HEADING_NUMBERING_CACHE:
        _HEADING_NUMBERING_CACHE[key] = _style_numbering_active(
            doc, "Heading {}".format(level))
    return _HEADING_NUMBERING_CACHE[key]


def _suppress_paragraph_numbering(paragraph):
    """Remove list numbering from a single paragraph (numId 0)."""
    p_pr = paragraph._p.get_or_add_pPr()
    for existing in p_pr.findall(qn("w:numPr")):
        p_pr.remove(existing)
    p_pr.append(parse_xml(
        '<w:numPr {}><w:ilvl w:val="0"/><w:numId w:val="0"/></w:numPr>'.format(
            nsdecls("w"))))


def _add_heading(doc, text, level=1, numbered=True):
    """Add a heading that does not fight the template's own numbering.

    When the template numbers headings, our manual "N. " prefix is dropped so
    Word supplies the single authoritative number; headings that must stay
    unnumbered (contents, front matter) have numbering suppressed instead.
    """
    text = str(text)
    if _heading_numbering_active(doc, level):
        if numbered:
            text = _MANUAL_HEADING_NUMBER.sub("", text)
        heading = doc.add_heading(text, level=level)
        if not numbered:
            _suppress_paragraph_numbering(heading)
        return heading
    return doc.add_heading(text, level=level)


def _set_table_style(table, name="Table Grid"):
    """Apply a table style, falling back to explicit borders if it is missing."""
    try:
        table.style = name
        return
    except KeyError:
        pass
    borders = "".join(
        '<w:{} w:val="single" w:sz="4" w:color="999999"/>'.format(edge)
        for edge in ("top", "left", "bottom", "right", "insideH", "insideV")
    )
    table._tbl.tblPr.append(
        parse_xml('<w:tblBorders {}>{}</w:tblBorders>'.format(nsdecls("w"), borders))
    )

# ── Configuration ────────────────────────────────────────
# TITLE and AUTHOR are resolved at run time by resolve_report_identity():
#   --title / --author  >  study_config.yaml (study.title, study.author)  >
#   task_spec.md heading  >  a task-local generate_report.py copy  >  folder name.
# The values below are only the last-resort fallbacks.
TITLE = "Task Report"
AUTHOR = ""
TASK_DATE = date.today().isoformat()
TASK_STATEMENT = ""             # resolved from study_config/task_spec/results
STUDY_BADGES = []               # [(label, value)] shown under the title

# ── Paper-specific configuration (edit for scientific paper output) ──
PAPER_TITLE = ""                # <-- Leave empty to use TITLE
PAPER_AUTHORS = []              # <-- e.g. [{"name": "J. Doe", "affiliation": "NTNU"}]
PAPER_KEYWORDS = []             # <-- e.g. ["thermodynamics", "process simulation"]
PAPER_JOURNAL = ""              # <-- e.g. "Journal of Natural Gas Science and Engineering"
PAPER_ACKNOWLEDGMENTS = ""      # <-- e.g. "Funded by Research Council of Norway"

# ── Report metadata (cover page and revision history) ────
DOC_NUMBER = ""                 # <-- e.g. "REP-2026-001" (auto-generated from task folder if blank)
REVISION = "0"                  # <-- Current revision number
REVISION_HISTORY = [
    # {"rev": "0", "date": "2026-07-04", "description": "Initial issue", "author": ""},
]
CLASSIFICATION = "Open"         # <-- "Open", "Internal", "Confidential"

# ── Manual sections (edit content for your specific task) ─
# These are used when auto-read data is not available.
# If results.json exists, sections 5-6 are auto-populated.
MANUAL_SECTIONS = {
    "executive_summary": (
        "[Replace with a 3-5 sentence summary of the task, approach, "
        "and key findings.]"
    ),
    "problem_description": (
        "[Describe the engineering question or task that was solved.]"
    ),
    "approach": (
        "[Describe the methodology: EOS used, process configuration, "
        "simulation setup, key assumptions.]"
    ),
    "conclusions": (
        "[Summarize key findings and provide recommendations.]"
    ),
    "references": (
        "[List references from step1_scope_and_research/notes.md.]"
    ),
}

# ── Scientific paper sections (edit for paper output) ────
# These map to standard engineering paper sections.
# Auto-populated fields from results.json override placeholders.
PAPER_SECTIONS = {
    "abstract": (
        "[Replace with a 150-300 word abstract summarizing the problem, "
        "methodology, key results, and conclusions.]"
    ),
    "introduction": (
        "[Replace with 2-4 paragraphs covering:\n"
        "- Background and motivation\n"
        "- Brief literature review\n"
        "- Problem statement and objectives\n"
        "- Paper organization (optional)]"
    ),
    "methodology": (
        "[Replace with a description of the methodology:\n"
        "- Thermodynamic model and equation of state\n"
        "- Process simulation setup and assumptions\n"
        "- Numerical methods and convergence criteria\n"
        "- Key equations and correlations used]"
    ),
    "results_discussion": (
        "[Replace with results discussion if auto-populated "
        "data from results.json is insufficient.]"
    ),
    "conclusions": (
        "[Replace with concise, numbered conclusions and "
        "recommendations for future work.]"
    ),
    "acknowledgments": (
        ""
    ),
}


# ── Task-local overrides ─────────────────────────────────
# Hand-written report content lives in step3_report/report_sections.json, not
# in a forked copy of this script. Keys: title, author, classification,
# doc_number, revision, manual_sections, paper_sections, paper_* metadata.
REPORT_SECTIONS_FILE = os.path.join(REPORT_DIR, "report_sections.json")


def _load_report_sections():
    """Return the task's hand-written report overrides, or an empty dict."""
    if not os.path.isfile(REPORT_SECTIONS_FILE):
        return {}
    try:
        with open(REPORT_SECTIONS_FILE, "r", encoding="utf-8-sig") as source:
            data = json.load(source)
    except (OSError, ValueError) as error:
        print("WARNING: could not read {}: {}".format(REPORT_SECTIONS_FILE, error))
        return {}
    return data if isinstance(data, dict) else {}


REPORT_SECTIONS = _load_report_sections()
# Sections a human wrote by hand; these outrank auto-generated prose.
AUTHORED_SECTIONS = set()

for _key, _value in (REPORT_SECTIONS.get("manual_sections") or {}).items():
    if isinstance(_value, str) and _value.strip():
        MANUAL_SECTIONS[_key] = _value
        AUTHORED_SECTIONS.add(_key)
for _key, _value in (REPORT_SECTIONS.get("paper_sections") or {}).items():
    if isinstance(_value, str) and _value.strip():
        PAPER_SECTIONS[_key] = _value
for _key, _global in (("doc_number", "DOC_NUMBER"), ("revision", "REVISION"),
                      ("paper_title", "PAPER_TITLE"),
                      ("paper_journal", "PAPER_JOURNAL"),
                      ("paper_acknowledgments", "PAPER_ACKNOWLEDGMENTS")):
    if isinstance(REPORT_SECTIONS.get(_key), str) and REPORT_SECTIONS[_key].strip():
        globals()[_global] = REPORT_SECTIONS[_key]
for _key, _global in (("revision_history", "REVISION_HISTORY"),
                      ("paper_authors", "PAPER_AUTHORS"),
                      ("paper_keywords", "PAPER_KEYWORDS")):
    if isinstance(REPORT_SECTIONS.get(_key), list) and REPORT_SECTIONS[_key]:
        globals()[_global] = REPORT_SECTIONS[_key]


# ══════════════════════════════════════════════════════════
# Auto-read functions
# ══════════════════════════════════════════════════════════

def load_results():
    """Load results.json if it exists. Returns dict or None."""
    if os.path.exists(RESULTS_FILE):
        with open(RESULTS_FILE, "r", encoding="utf-8") as f:
            data = json.load(f)
        print("  Loaded results.json ({} keys)".format(len(data)))
        return data
    print("  No results.json found (using manual sections)")
    return None


def load_task_spec():
    """Load task_spec.md and extract standards/methods/criteria sections."""
    if not os.path.exists(TASK_SPEC_FILE):
        print("  No task_spec.md found (using placeholder for scope)")
        return None
    with open(TASK_SPEC_FILE, "r", encoding="utf-8") as f:
        content = f.read()
    print("  Loaded task_spec.md ({} chars)".format(len(content)))
    return content


def _strip_yaml_comment(line):
    """Strip YAML comments while preserving hashes inside quoted strings."""
    result = []
    quote = None
    for character in line:
        if character in ('"', "'"):
            if quote == character:
                quote = None
            elif quote is None:
                quote = character
        if character == "#" and quote is None:
            break
        result.append(character)
    return "".join(result).rstrip()


def _parse_yaml_value(value):
    """Parse the simple scalar values used by study_config.yaml."""
    cleaned = _strip_yaml_comment(value).strip()
    if not cleaned:
        return ""
    if ((cleaned.startswith('"') and cleaned.endswith('"'))
            or (cleaned.startswith("'") and cleaned.endswith("'"))):
        return cleaned[1:-1]
    lowered = cleaned.lower()
    if lowered == "true":
        return True
    if lowered == "false":
        return False
    try:
        return int(cleaned)
    except ValueError:
        return cleaned


def _section_lines(config_text, section):
    """Return lines belonging to a top-level YAML section."""
    lines = config_text.splitlines()
    section_marker = "{}:".format(section)
    capturing = False
    result = []
    for line in lines:
        stripped = line.strip()
        if stripped == section_marker and not line.startswith(" "):
            capturing = True
            continue
        if capturing and stripped and not line.startswith(" "):
            break
        if capturing:
            result.append(line)
    return result


def _parse_section_scalars(lines):
    """Parse scalar keys directly under a YAML section."""
    parsed = {}
    child_indents = [len(line) - len(line.lstrip()) for line in lines
                     if line.strip() and line.startswith(" ")]
    if not child_indents:
        return parsed
    child_indent = min(child_indents)
    for line in lines:
        cleaned = _strip_yaml_comment(line)
        if not cleaned.strip() or not cleaned.startswith(" "):
            continue
        indent = len(cleaned) - len(cleaned.lstrip())
        if indent != child_indent:
            continue
        if ":" not in cleaned:
            continue
        key, value = cleaned.strip().split(":", 1)
        value = value.strip()
        if value:
            parsed[key] = _parse_yaml_value(value)
    return parsed


def _parse_scalar_list(lines, key):
    """Parse a scalar list under an indented YAML key."""
    values = []
    capturing = False
    key_indent = 0
    for line in lines:
        cleaned = _strip_yaml_comment(line)
        stripped = cleaned.strip()
        if stripped == "{}:".format(key):
            capturing = True
            key_indent = len(cleaned) - len(cleaned.lstrip())
            continue
        if capturing:
            indent = len(cleaned) - len(cleaned.lstrip())
            if stripped and indent <= key_indent:
                break
            if stripped.startswith("- "):
                values.append(_parse_yaml_value(stripped[2:]))
    return values


def _parse_notebook_plan(lines):
    """Parse notebooks.plan entries from study_config.yaml."""
    plan = []
    current = None
    capturing = False
    plan_indent = 0
    for line in lines:
        cleaned = _strip_yaml_comment(line)
        stripped = cleaned.strip()
        if stripped == "plan:":
            capturing = True
            plan_indent = len(cleaned) - len(cleaned.lstrip())
            continue
        if capturing:
            indent = len(cleaned) - len(cleaned.lstrip())
            if stripped and indent <= plan_indent:
                break
            if stripped.startswith("- file:"):
                if current:
                    plan.append(current)
                current = {"file": _parse_yaml_value(stripped.split(":", 1)[1])}
            elif current and stripped.startswith("purpose:"):
                current["purpose"] = _parse_yaml_value(stripped.split(":", 1)[1])
    if current:
        plan.append(current)
    return plan


def _parse_mapping_list(lines, key):
    """Parse a list of simple mappings under an indented YAML key."""
    values = []
    current = None
    capturing = False
    key_indent = 0
    for line in lines:
        cleaned = _strip_yaml_comment(line)
        stripped = cleaned.strip()
        if stripped == "{}:".format(key):
            capturing = True
            key_indent = len(cleaned) - len(cleaned.lstrip())
            continue
        if capturing:
            indent = len(cleaned) - len(cleaned.lstrip())
            if stripped and indent <= key_indent:
                break
            if not stripped:
                continue
            if stripped.startswith("- "):
                if current:
                    values.append(current)
                current = {}
                item = stripped[2:].strip()
                if ":" in item:
                    item_key, item_value = item.split(":", 1)
                    current[item_key.strip()] = _parse_yaml_value(item_value)
            elif current is not None and ":" in stripped:
                item_key, item_value = stripped.split(":", 1)
                current[item_key.strip()] = _parse_yaml_value(item_value)
    if current:
        values.append(current)
    return values


def load_study_config():
    """Load study_config.yaml and return the subset used by this generator."""
    if not os.path.exists(STUDY_CONFIG_FILE):
        print("  No study_config.yaml found (using inferred task depth)")
        return {}
    with open(STUDY_CONFIG_FILE, "r", encoding="utf-8") as config_file:
        text = config_file.read()
    config = {}
    for section in ["study", "inputs", "analysis", "notebooks", "report",
                    "quality_gates"]:
        lines = _section_lines(text, section)
        config[section] = _parse_section_scalars(lines)
    config["report"]["formats"] = _parse_scalar_list(
        _section_lines(text, "report"), "formats")
    config["report"]["required_sections"] = _parse_scalar_list(
        _section_lines(text, "report"), "required_sections")
    config["notebooks"]["plan"] = _parse_notebook_plan(
        _section_lines(text, "notebooks"))
    config["inputs"]["documents"] = _parse_mapping_list(
        _section_lines(text, "inputs"), "documents")
    config["inputs"]["data_sources"] = _parse_mapping_list(
        _section_lines(text, "inputs"), "data_sources")
    config["analysis"]["scripts"] = _parse_mapping_list(
        _section_lines(text, "analysis"), "scripts")
    print("  Loaded study_config.yaml")
    return config


def extract_spec_section(spec_text, heading):
    """Extract a section from task_spec.md by heading."""
    if not spec_text:
        return ""
    lines = spec_text.split("\n")
    capturing = False
    result = []
    for line in lines:
        if line.startswith("## ") and heading.lower() in line.lower():
            capturing = True
            continue
        elif line.startswith("## ") and capturing:
            break
        elif capturing:
            result.append(line)
    text = "\n".join(result).strip()
    # Skip if still placeholder
    if text and "| | | |" not in text and "[e.g.," not in text:
        return text
    return ""


# ── Report identity (title, author, task statement) ──────

def _is_placeholder_value(value):
    """Return true for empty or bracketed scaffold values such as "[Title]"."""
    text = str(value or "").strip()
    if not text:
        return True
    return text.startswith("[") and text.endswith("]")


def _prettify_slug(folder_name):
    """Turn a task folder name into a readable title."""
    name = folder_name
    if len(name) >= 11 and name[4] == "-" and name[7] == "-":
        name = name[11:]
    name = name.replace("_", " ").replace("-", " ").strip()
    if not name:
        return ""
    return name[0].upper() + name[1:]


def _local_report_constant(name):
    """Read a constant from report_sections.json or a legacy vendored copy."""
    override = REPORT_SECTIONS.get(name.lower())
    if isinstance(override, str) and override.strip():
        return override.strip()
    local_copy = os.path.join(REPORT_DIR, "generate_report.py")
    if os.path.abspath(local_copy) == os.path.abspath(__file__):
        return ""
    if not os.path.isfile(local_copy):
        return ""
    pattern = re.compile(r'^{}\s*=\s*"([^"]*)"'.format(name), re.MULTILINE)
    with open(local_copy, "r", encoding="utf-8") as source:
        match = pattern.search(source.read())
    return match.group(1).strip() if match else ""


def _task_spec_title(task_spec):
    """Return the title from the first heading of task_spec.md."""
    if not task_spec:
        return ""
    for line in task_spec.split("\n"):
        if line.startswith("# "):
            title = line[2:].strip()
            for prefix in ("Task Specification:", "Task Spec:", "Task:"):
                if title.lower().startswith(prefix.lower()):
                    title = title[len(prefix):].strip()
            if not _is_placeholder_value(title):
                return title
            return ""
    return ""


def _cli_option(flag):
    """Return the value that follows a command-line flag, or an empty string."""
    if flag not in sys.argv:
        return ""
    index = sys.argv.index(flag) + 1
    if index >= len(sys.argv):
        print("ERROR: {} requires a value".format(flag))
        sys.exit(2)
    return sys.argv[index].strip()


def _first_paragraph(text, max_chars=700):
    """Return the first prose paragraph of a block, trimmed for a summary box."""
    for block in str(text or "").split("\n\n"):
        cleaned = " ".join(
            line.strip() for line in block.split("\n")
            if line.strip() and not line.strip().startswith(("|", "#", "-", "*"))
        ).strip()
        if cleaned:
            if len(cleaned) > max_chars:
                cleaned = cleaned[:max_chars].rsplit(" ", 1)[0] + " ..."
            return cleaned
    return ""


def resolve_task_statement(results, task_spec, study_config):
    """Return a one-paragraph statement of what the task asked for."""
    if results:
        for key in ("task_statement", "task", "objective", "problem_statement"):
            text = results.get(key)
            if text and isinstance(text, str) and not _is_placeholder_text(text):
                return _first_paragraph(text)
    for heading in ("Objective", "Task Description", "Problem Statement",
                    "Description", "Background"):
        text = extract_spec_section(task_spec, heading)
        if text and not _is_placeholder_text(text):
            statement = _first_paragraph(text)
            if statement:
                return statement
    title = (study_config or {}).get("study", {}).get("title", "")
    if not _is_placeholder_value(title):
        return "Study scope: {}.".format(str(title).rstrip("."))
    return ""


def _study_badges(study_config):
    """Return [(label, value)] describing study depth, shown under the title."""
    study = (study_config or {}).get("study", {})
    labels = (
        ("task_type", "Task type"),
        ("scale", "Scale"),
        ("mode", "Mode"),
        ("aace_class", "AACE class"),
        ("fel_stage", "FEL stage"),
    )
    badges = []
    for key, label in labels:
        value = str(study.get(key, "")).strip()
        if not value or value.lower() in ("auto", "none", "[title]"):
            continue
        badges.append((label, value))
    return badges


def resolve_report_identity(study_config, task_spec, results):
    """Set TITLE, AUTHOR, CLASSIFICATION, TASK_STATEMENT and STUDY_BADGES.

    The report title is the study title, so a report generated from the
    canonical script against any task folder is never left named "Task Report".
    """
    global TITLE, AUTHOR, CLASSIFICATION, TASK_STATEMENT, STUDY_BADGES

    study = (study_config or {}).get("study", {})
    report_cfg = (study_config or {}).get("report", {})

    title_candidates = [
        _cli_option("--title"),
        os.environ.get("NEQSIM_REPORT_TITLE", "").strip(),
        study.get("title", ""),
        _task_spec_title(task_spec),
        _local_report_constant("TITLE"),
        _prettify_slug(os.path.basename(TASK_DIR)),
    ]
    for candidate in title_candidates:
        if candidate and not _is_placeholder_value(candidate) \
                and candidate != "Task Report":
            TITLE = str(candidate).strip()
            break

    author_candidates = [
        _cli_option("--author"),
        os.environ.get("NEQSIM_REPORT_AUTHOR", "").strip(),
        study.get("author", ""),
        report_cfg.get("author", ""),
        _local_report_constant("AUTHOR"),
    ]
    for candidate in author_candidates:
        if candidate and not _is_placeholder_value(candidate):
            AUTHOR = str(candidate).strip()
            break

    for candidate in (study.get("classification", ""),
                      report_cfg.get("classification", ""),
                      _local_report_constant("CLASSIFICATION")):
        if candidate and not _is_placeholder_value(candidate):
            CLASSIFICATION = str(candidate).strip()
            break

    TASK_STATEMENT = resolve_task_statement(results, task_spec, study_config)
    STUDY_BADGES = _study_badges(study_config)
    return TITLE


def _as_bool(value):
    """Interpret YAML-like values as booleans."""
    if isinstance(value, bool):
        return value
    return str(value).strip().lower() in ("true", "yes", "required")


def _as_int(value, default=0):
    """Interpret YAML-like values as integers."""
    try:
        return int(value)
    except (TypeError, ValueError):
        return default


def _is_required(value):
    """Return true when a config value means required."""
    return str(value).strip().lower() == "required" or value is True


def _is_placeholder_text(text):
    """Return true when text still contains report-template placeholder content."""
    if not text:
        return True
    stripped = str(text).strip()
    if stripped.startswith("[") and stripped.endswith("]"):
        return True
    placeholder_markers = (
        "[replace with",
        "[describe ",
        "[summarize ",
        "[list references",
        "[auto-populated",
    )
    return any(stripped.lower().startswith(marker) for marker in placeholder_markers)


def _manual_section_filled(name):
    """Return true when a manual section has been filled in."""
    content = MANUAL_SECTIONS.get(name, "")
    return not _is_placeholder_text(content)


def _has_safety_context(results):
    """Return true when results look like a safety or governed evidence study."""
    if not results:
        return False
    safety_keys = (
        "safety_readiness",
        "readiness",
        "evidence_gaps",
        "assumptions_gaps",
        "standards_findings",
        "source_term_handoff",
        "source_basis_summary",
    )
    if any(results.get(key) for key in safety_keys):
        return True
    text = " ".join([
        str(results.get("task_type", "")),
        str(results.get("objective", "")),
        str(results.get("approach", "")),
        str(results.get("conclusions", "")),
    ]).lower()
    return any(token in text for token in ("safety", "rupture", "fire", "blowdown"))


def _validation_failures(results):
    """Return validation checks that are false and block design-grade use."""
    failures = []
    validation = results.get("validation", {}) if results else {}
    for check, outcome in validation.items():
        if outcome is False:
            failures.append(check.replace("_", " ").title())
    return failures


def infer_safety_readiness(results):
    """Infer a report-facing safety readiness label from results.json."""
    if not _has_safety_context(results):
        return None
    explicit = results.get("safety_readiness") or results.get("readiness")
    findings = []
    if isinstance(explicit, dict):
        verdict = str(explicit.get("verdict") or explicit.get("label") or "").strip()
        raw_findings = explicit.get("findings") or explicit.get("blockers") or []
        if isinstance(raw_findings, list):
            findings.extend([str(item) for item in raw_findings])
        elif raw_findings:
            findings.append(str(raw_findings))
    elif explicit:
        verdict = str(explicit).strip()
    else:
        verdict = ""

    evidence_gaps = results.get("evidence_gaps") or results.get("assumptions_gaps") or []
    validation_failures = _validation_failures(results)
    if evidence_gaps and isinstance(evidence_gaps, list):
        findings.extend([_format_list_item_text(item) for item in evidence_gaps[:5]])
    findings.extend(validation_failures)

    if not verdict:
        if evidence_gaps or validation_failures:
            verdict = "SCREENING - DESIGN-GRADE BLOCKED"
        else:
            verdict = "SCREENING / READY FOR HUMAN REVIEW"
    elif verdict.upper() == "SCREENING" and (evidence_gaps or validation_failures):
        verdict = "SCREENING - DESIGN-GRADE BLOCKED"

    return {
        "verdict": verdict,
        "findings": findings,
    }


def _format_list_item_text(item):
    """Format a result-list item as readable report text."""
    if isinstance(item, dict):
        preferred = []
        for key in ("gap", "description", "finding", "item", "action", "recommendation"):
            if item.get(key):
                preferred.append(str(item.get(key)))
        if preferred:
            return " - ".join(preferred)
        parts = []
        for key in sorted(item.keys()):
            parts.append("{}: {}".format(key.replace("_", " "), item[key]))
        return "; ".join(parts)
    return str(item)


def load_collection_manifest():
    """Return the references collection manifest, or {} when absent.

    Written by devtools/generate_sources_md.py; it is the machine-readable
    record of every document the task collected and which system it came from.
    """
    path = os.path.join(TASK_DIR, "step1_scope_and_research", "references",
                        "collection_manifest.json")
    if not os.path.exists(path):
        return {}
    try:
        with open(path, "r", encoding="utf-8-sig") as manifest_file:
            data = json.load(manifest_file)
    except (OSError, ValueError):
        return {}
    return data if isinstance(data, dict) else {}


def _document_source_counts(manifest):
    """Return [(source name, document count, description)] from the manifest."""
    rows = []
    for group in manifest.get("sources", []) or []:
        if not isinstance(group, dict):
            continue
        documents = group.get("documents") or group.get("files") or []
        if not documents:
            continue
        name = (group.get("system_name") or group.get("source")
                or group.get("name") or "other")
        rows.append((str(name), len(documents), str(group.get("description", "") or "")))
    rows.sort(key=lambda row: (-row[1], row[0].lower()))
    return rows


def _count_reference_files():
    """Count collected files directly, for tasks with no manifest."""
    references = os.path.join(TASK_DIR, "step1_scope_and_research", "references")
    if not os.path.isdir(references):
        return []
    counts = {}
    for root, dirs, files in os.walk(references):
        dirs[:] = [name for name in dirs if not name.startswith(".")]
        for name in files:
            if name in ("SOURCES.md", "collection_manifest.json"):
                continue
            folder = os.path.basename(root)
            label = "unfiled" if os.path.abspath(root) == os.path.abspath(references) \
                else folder
            counts[label] = counts.get(label, 0) + 1
    rows = [(name, count, "") for name, count in counts.items()]
    rows.sort(key=lambda row: (-row[1], row[0].lower()))
    return rows


def format_information_sources_text(config, results):
    """Format the evidence basis: how many documents came from which system.

    A reader's first question about a data-driven study is what it was built on.
    This answers it from the collected files themselves, so the count cannot be
    overstated in prose.
    """
    manifest = load_collection_manifest()
    rows = _document_source_counts(manifest) or _count_reference_files()
    inputs = (config or {}).get("inputs", {})
    data_sources = inputs.get("data_sources", []) or []
    parts = []

    if rows:
        total = sum(row[1] for row in rows)
        parts.append(
            "{} document(s) were collected from {} source system(s) and are stored "
            "with this task in step1_scope_and_research/references/.".format(
                total, len(rows)))
        parts.append("")
        table = ["| Source system | Documents | Content |",
                 "|---|---|---|"]
        for name, count, description in rows:
            table.append("| {} | {} | {} |".format(name, count, description or "-"))
        parts.append("\n".join(table))

    if data_sources:
        parts.append("")
        parts.append("Source systems read:")
        table = ["| System | Scope read | Access | Captured evidence |",
                 "|---|---|---|---|"]
        for entry in data_sources:
            if not isinstance(entry, dict):
                continue
            evidence = str(entry.get("evidence", "") or "")
            if not evidence:
                state = "not recorded"
            elif os.path.exists(_resolve_task_path(evidence)):
                state = evidence
            else:
                state = "{} (missing)".format(evidence)
            table.append("| {} | {} | {} | {} |".format(
                entry.get("system", "-"), entry.get("scope", "-"),
                entry.get("access", "read-only"), state))
        if len(table) > 2:
            parts.append("\n".join(table))

    gaps = manifest.get("data_gaps", []) or []
    if gaps:
        parts.append("")
        parts.append("Documents sought but not obtained:")
        for gap in gaps:
            if isinstance(gap, dict):
                text = (gap.get("description") or gap.get("gap")
                        or "; ".join("{}: {}".format(key, value)
                                     for key, value in sorted(gap.items())))
            else:
                text = str(gap)
            parts.append("- {}".format(text))

    if results and results.get("references"):
        parts.append("")
        parts.append("Standards and literature cited are listed in the References "
                     "section; the per-file origin, retrieval date, and relevance of "
                     "every collected document are in "
                     "step1_scope_and_research/references/SOURCES.md.")
    elif rows:
        parts.append("")
        parts.append("Per-file origin, retrieval date, and relevance: "
                     "step1_scope_and_research/references/SOURCES.md.")

    return "\n".join(parts).strip()


def format_improvements_text(results):
    """Format the tooling improvements this task delivered to NeqSim/agents/skills.

    A task is also a test of the tooling; this section makes the resulting fixes
    part of the deliverable instead of leaving them in a side file.
    """
    if not results:
        return ""
    items = results.get("improvements")
    if isinstance(items, str):
        return items.strip()
    if not isinstance(items, list) or not items:
        return ""

    rows = []
    for item in items:
        if not isinstance(item, dict):
            rows.append("- {}".format(item))
            continue
        target = item.get("target") or item.get("component") or "tooling"
        gap = item.get("gap") or item.get("problem") or ""
        change = item.get("change") or item.get("improvement") or ""
        evidence = item.get("evidence") or item.get("test") or ""
        line = "- **{}** — {}".format(target, change or gap)
        if gap and change:
            line += " (gap: {})".format(gap)
        if evidence:
            line += " [{}]".format(evidence)
        rows.append(line)
    return "\n".join(rows)


def _assumption_rows(results):
    """Return (assumption rows, data-gap rows) from any results.json spelling."""
    assumptions = []
    gaps = []
    combined = results.get("assumptions_and_gaps") or results.get("assumptions_gaps")
    if isinstance(combined, dict):
        assumptions.extend(combined.get("assumptions", []) or [])
        gaps.extend(combined.get("data_gaps", []) or combined.get("gaps", []) or [])
    elif isinstance(combined, list):
        gaps.extend(combined)
    for key in ("assumptions", "assumption_register"):
        value = results.get(key)
        if isinstance(value, list):
            assumptions.extend(value)
    for key in ("data_gaps", "evidence_gaps", "gaps"):
        value = results.get(key)
        if isinstance(value, list):
            gaps.extend(value)
    return assumptions, gaps


def _assumption_text(item, keys):
    """Pull the first present key from a dict item, else render the whole item."""
    if not isinstance(item, dict):
        return str(item)
    for key in keys:
        if item.get(key):
            return str(item[key])
    return ""


def format_assumptions_text(results):
    """Format the assumption and data-gap register.

    Every assumption a reader must accept, and every piece of information that
    could not be found, with what was assumed in its place.
    """
    if not results:
        return ""
    assumptions, gaps = _assumption_rows(results)
    if not assumptions and not gaps:
        return ""

    parts = []
    if assumptions:
        rows = []
        for item in assumptions:
            if isinstance(item, dict):
                text = _assumption_text(item, ("assumption", "description", "text", "item"))
                basis = _assumption_text(item, ("basis", "source", "rationale", "why"))
                effect = _assumption_text(
                    item, ("effect", "impact", "conservatism", "direction", "consequence"))
            else:
                text, basis, effect = str(item), "", ""
            rows.append((text, basis, effect))
        parts.append("Assumptions the results depend on:")
        parts.append("")
        # A table with two empty columns reads worse than a list; only tabulate
        # when the basis or effect was actually recorded.
        if any(basis or effect for _text, basis, effect in rows):
            table = ["| # | Assumption | Basis | Effect on the result |",
                     "|---|---|---|---|"]
            for index, (text, basis, effect) in enumerate(rows, 1):
                table.append("| A{} | {} | {} | {} |".format(
                    index, text.replace("|", "/"), basis.replace("|", "/") or "-",
                    effect.replace("|", "/") or "-"))
            parts.append("\n".join(table))
        else:
            for index, (text, _basis, _effect) in enumerate(rows, 1):
                parts.append("- A{}: {}".format(index, text))

    if gaps:
        if parts:
            parts.append("")
        parts.append("Information sought but not available, and what was assumed "
                     "in its place:")
        parts.append("")
        table = ["| # | Information sought | Source | Status | Assumed instead | "
                 "Effect if wrong |", "|---|---|---|---|---|---|"]
        for index, item in enumerate(gaps, 1):
            if isinstance(item, dict):
                sought = _assumption_text(
                    item, ("gap", "description", "information", "sought", "what",
                           "needed", "missing", "item", "text"))
                source = _assumption_text(item, ("source", "system", "document"))
                status = _assumption_text(item, ("status", "state", "reason"))
                blocker = _assumption_text(item, ("blocker", "why", "detail"))
                if not sought:
                    sought, blocker = blocker, ""
                if blocker and status:
                    status = "{} — {}".format(status, blocker)
                elif blocker:
                    status = blocker
                assumed = _assumption_text(
                    item, ("assumed", "assumption", "fallback", "substitute",
                           "workaround", "mitigation"))
                effect = _assumption_text(
                    item, ("effect", "impact", "consequence", "risk"))
            else:
                sought, source, status, assumed, effect = str(item), "", "", "", ""
            table.append("| G{} | {} | {} | {} | {} | {} |".format(
                index, sought.replace("|", "/") or "-", source.replace("|", "/") or "-",
                status.replace("|", "/") or "-", assumed.replace("|", "/") or "-",
                effect.replace("|", "/") or "-"))
        parts.append("\n".join(table))
        parts.append("")
        parts.append("Each gap above is an open item: the conclusion holds only while "
                     "the stated substitute assumption holds.")

    return "\n".join(parts).strip()


def format_list_items_text(items):
    """Format strings or dictionaries from results.json as bullet text."""
    if not items:
        return ""
    if isinstance(items, dict):
        iterable = [items]
    elif isinstance(items, list):
        iterable = items
    else:
        iterable = [items]
    return "\n".join(["- {}".format(_format_list_item_text(item)) for item in iterable])


def format_safety_readiness_text(results):
    """Format the inferred safety readiness as report text."""
    readiness = infer_safety_readiness(results)
    if not readiness:
        return ""
    lines = ["Readiness label: {}".format(readiness["verdict"])]
    findings = [item for item in readiness.get("findings", []) if item]
    if findings:
        lines.append("")
        lines.append("Design-grade blockers / review findings:")
        lines.extend(["- {}".format(item) for item in findings])
    lines.append("")
    lines.append(
        "Safety-study outputs remain screening/preparation until the listed blockers are closed "
        "and a qualified engineer reviews the controlled evidence basis."
    )
    return "\n".join(lines)


_SENTENCE_ABBREVIATIONS = (
    "e.g.", "i.e.", "cf.", "vs.", "etc.", "approx.", "ca.", "no.", "nos.",
    "fig.", "figs.", "eq.", "eqs.", "ref.", "refs.", "tab.", "sec.", "ch.",
    "dvs.", "jf.", "bl.a.", "pkt.", "ca.", "inkl.", "ekskl.",
)

# Longer than this in one unbroken run and a Word/PDF paragraph reads as a wall.
BODY_PARAGRAPH_MAX_CHARS = 650


def _split_sentences(text):
    """Split prose into sentences, keeping common abbreviations intact."""
    pieces = re.split(r'(?<=[.!?])\s+(?=[A-Z0-9\u00c6\u00d8\u00c5"\'(\[])', text)
    merged = []
    for piece in pieces:
        if merged and merged[-1].lower().endswith(_SENTENCE_ABBREVIATIONS):
            merged[-1] = merged[-1] + " " + piece
        else:
            merged.append(piece)
    return merged


def _reflow_paragraph(text, max_chars=BODY_PARAGRAPH_MAX_CHARS):
    """Break an over-long single paragraph at sentence boundaries."""
    if len(text) <= max_chars:
        return [text]
    chunks = []
    buffer = ""
    for sentence in _split_sentences(text):
        if buffer and len(buffer) + 1 + len(sentence) > max_chars:
            chunks.append(buffer)
            buffer = sentence
        else:
            buffer = sentence if not buffer else buffer + " " + sentence
    if buffer:
        chunks.append(buffer)
    return chunks


def _body_paragraphs(text):
    """Return renderable paragraphs for a plain-prose section.

    A results.json field written as one long string (approach, conclusions,
    executive_summary) otherwise renders as a single unbroken block. Blocks the
    author already broke with their own newlines are left untouched.
    """
    paragraphs = []
    for block in str(text or "").split("\n\n"):
        block = block.strip()
        if not block:
            continue
        if "\n" in block:
            paragraphs.append(block)
            continue
        paragraphs.extend(_reflow_paragraph(block))
    return paragraphs


def _prose_to_html(text):
    """Render plain prose as HTML paragraphs using the same splitting rules."""
    return "".join("<p>{}</p>".format(para.replace("\n", "<br>"))
                   for para in _body_paragraphs(text))


def auto_executive_summary(results, task_spec):
    """Generate an executive summary from available results data."""
    parts = []
    approach = ""
    if results and results.get("approach"):
        approach = results["approach"]
    if approach and not _is_placeholder_text(approach):
        first_sentence = approach.split(". ")[0].rstrip(".")
        parts.append(first_sentence + ".")
    if results and results.get("key_results"):
        findings = []
        for key, value in list(results["key_results"].items())[:5]:
            label, unit = _parse_key_name(key)
            if isinstance(value, float):
                value_text = "{:.4g}".format(value)
            else:
                value_text = str(value)
            findings.append("{} = {}{}".format(
                label, value_text, " " + unit if unit else ""))
        if findings:
            parts.append("Key findings: {}.".format(", ".join(findings)))
    readiness = infer_safety_readiness(results) if results else None
    if readiness:
        parts.append("Safety study readiness: {}.".format(readiness["verdict"]))
    evidence_rows = _document_source_counts(load_collection_manifest()) \
        or _count_reference_files()
    if evidence_rows:
        parts.append("Evidence basis: {} document(s) from {}.".format(
            sum(row[1] for row in evidence_rows),
            ", ".join("{} ({})".format(name, count)
                      for name, count, _desc in evidence_rows[:6])))
    if results:
        _assumptions, _gaps = _assumption_rows(results)
        if _gaps:
            parts.append("{} item(s) of information could not be obtained; the "
                         "substitute assumptions are listed in Assumptions and "
                         "Data Gaps.".format(len(_gaps)))
    if results and results.get("uncertainty"):
        uncertainty = results["uncertainty"]
        p50 = uncertainty.get("p50")
        output = uncertainty.get("output_parameter", "output")
        if p50 is not None:
            parts.append("Uncertainty analysis gives P50 {} = {:.4g}.".format(output, p50))
    if results and results.get("validation"):
        failures = _validation_failures(results)
        if failures:
            parts.append("Validation checks requiring attention: {}.".format(
                ", ".join(failures)))
        else:
            parts.append("Validation checks did not flag design blockers.")
    if results and results.get("conclusions") and not _is_placeholder_text(results["conclusions"]):
        parts.append(results["conclusions"])
    return "\n\n".join(parts)


def auto_problem_description(results, task_spec):
    """Generate a problem description from task_spec.md sections."""
    parts = []
    for heading in ["Objective", "Description", "Problem Statement", "Task Description", "Background"]:
        text = extract_spec_section(task_spec, heading)
        if text:
            parts.append(text)
            break
    envelope = extract_spec_section(task_spec, "Operating Envelope")
    if envelope:
        parts.append("Operating envelope: " + envelope.replace("\n", " ").strip())
    if results and results.get("objective") and not parts:
        parts.append(str(results["objective"]))
    return "\n\n".join(parts)


def _required_section_available(section, results, task_spec):
    """Check whether a configured report section has enough input data."""
    normalized = str(section).strip().lower().replace("-", "_").replace(" ", "_")
    if normalized == "executive_summary":
        return bool((results and (results.get("executive_summary")
                                  or results.get("key_results")
                                  or results.get("approach")
                                  or results.get("conclusions")))
                    or _manual_section_filled("executive_summary"))
    if normalized in ("problem_description", "problem_statement"):
        return bool(auto_problem_description(results, task_spec)
                    or _manual_section_filled("problem_description"))
    if normalized in ("scope", "scope_and_standards"):
        if not task_spec:
            return False
        return bool(extract_spec_section(task_spec, "Applicable Standards")
                    or extract_spec_section(task_spec, "Calculation Methods")
                    or extract_spec_section(task_spec, "Acceptance Criteria")
                    or extract_spec_section(task_spec, "Operating Envelope"))
    if normalized in ("methodology", "approach"):
        return bool((results and results.get("approach"))
                    or _manual_section_filled("approach"))
    if normalized in ("information_sources", "evidence_basis", "sources"):
        return bool(format_information_sources_text({}, results))
    if normalized in ("assumptions", "assumptions_and_gaps", "data_gaps"):
        return bool(format_assumptions_text(results))
    if normalized == "results":
        return bool(results and results.get("key_results"))
    if normalized == "discussion":
        return bool(results and results.get("figure_discussion"))
    if normalized == "validation":
        return bool(results and results.get("validation"))
    if normalized == "benchmark_validation":
        return bool(results and results.get("benchmark_validation"))
    if normalized in ("uncertainty", "uncertainty_analysis"):
        return bool(results and results.get("uncertainty"))
    if normalized in ("solution_workflow", "workflow", "agent_workflow"):
        return bool(results and results.get("agent_workflow_plan"))
    if normalized in ("risk", "risk_assessment", "risk_evaluation"):
        return bool(results and (results.get("risk_evaluation") or results.get("risks")))
    if normalized in ("conclusion", "conclusions", "conclusions_and_recommendations"):
        return bool((results and results.get("conclusions"))
                    or _manual_section_filled("conclusions"))
    if normalized == "references":
        return bool((results and results.get("references"))
                    or _manual_section_filled("references"))
    return True


def _load_runner_jobs(runner_db_path):
    """Return runner job rows from runner.db or an error message."""
    try:
        connection = sqlite3.connect(runner_db_path, timeout=5.0)
        connection.row_factory = sqlite3.Row
        try:
            rows = connection.execute(
                "SELECT job_id, script, job_type, status, error_message "
                "FROM jobs ORDER BY created_at"
            ).fetchall()
        finally:
            connection.close()
    except sqlite3.Error as error:
        return [], str(error)
    return [dict(row) for row in rows], None


def _runner_script_name(job_row):
    """Return the basename of a runner job script path."""
    return os.path.basename(str(job_row.get("script") or ""))


def _runner_status_summary(job_rows):
    """Return a compact status summary for report warnings."""
    counts = {}
    for job_row in job_rows:
        status = str(job_row.get("status") or "unknown")
        counts[status] = counts.get(status, 0) + 1
    return ", ".join(["{}={}".format(status, counts[status])
                      for status in sorted(counts)])


def _validate_runner_execution(notebooks, planned_notebooks, existing_notebooks):
    """Return warnings for incomplete NeqSim Runner notebook execution."""
    warnings = []
    execution_engine = str(notebooks.get("execution_engine", "")).strip().lower()
    notebook_execution_required = _as_bool(notebooks.get("execution_required", False))
    require_successful_jobs = _as_bool(notebooks.get("require_successful_jobs", True))
    if not (notebook_execution_required and execution_engine == "neqsim_runner"):
        return warnings
    if not existing_notebooks:
        return warnings

    runner_output_path = os.path.join(TASK_DIR, "runner_output")
    runner_db_path = os.path.join(TASK_DIR, "runner.db")
    if not os.path.exists(runner_output_path):
        warnings.append("Notebook execution engine is neqsim_runner, but runner_output/ is missing.")
    if not os.path.exists(runner_db_path):
        warnings.append("Notebook execution engine is neqsim_runner, but runner.db is missing.")
        return warnings

    job_rows, load_error = _load_runner_jobs(runner_db_path)
    if load_error:
        warnings.append("Could not inspect runner.db for notebook status: {}".format(
            load_error))
        return warnings
    if not job_rows:
        warnings.append("runner.db exists, but it contains no recorded runner jobs.")
        return warnings

    expected_notebooks = planned_notebooks or [os.path.basename(path)
                                               for path in existing_notebooks]
    expected_script_names = set()
    for notebook_file in expected_notebooks:
        notebook_name = os.path.basename(str(notebook_file))
        expected_script_names.add(notebook_name)
        expected_script_names.add(os.path.splitext(notebook_name)[0] + ".py")

    notebook_rows = []
    for row in job_rows:
        job_type = str(row.get("job_type") or "script")
        script_name = _runner_script_name(row)
        if job_type == "notebook" or script_name in expected_script_names:
            notebook_rows.append(row)
    if not notebook_rows:
        warnings.append(
            "runner.db contains jobs ({}), but none match the planned notebooks.".format(
                _runner_status_summary(job_rows)))
        return warnings

    if require_successful_jobs:
        unsuccessful = [row for row in notebook_rows
                        if str(row.get("status") or "") != "success"]
        if unsuccessful:
            details = []
            for row in unsuccessful[:5]:
                details.append("{}={} ({})".format(
                    row.get("job_id"), row.get("status"), _runner_script_name(row)))
            if len(unsuccessful) > 5:
                details.append("... {} more".format(len(unsuccessful) - 5))
            warnings.append("NeqSim Runner notebook jobs are not all successful: {}.".format(
                "; ".join(details)))

        successful_notebooks = set([_runner_script_name(row) for row in notebook_rows
                                    if str(row.get("status") or "") == "success"])
        for notebook_file in expected_notebooks:
            notebook_name = os.path.basename(str(notebook_file))
            script_name = os.path.splitext(notebook_name)[0] + ".py"
            notebook_path = os.path.join(TASK_DIR, "step2_analysis", str(notebook_file))
            if (os.path.exists(notebook_path)
                    and notebook_name not in successful_notebooks
                    and script_name not in successful_notebooks):
                warnings.append(
                    "Planned notebook has no successful runner job: step2_analysis/{}".format(
                        notebook_file))
    return warnings


def _resolve_task_path(path):
    """Resolve a study_config path relative to the task folder."""
    if os.path.isabs(str(path)):
        return str(path)
    return os.path.join(TASK_DIR, str(path))


def _validate_analysis_scripts(analysis):
    """Return warnings for declared analysis scripts and their artifacts."""
    warnings = []
    engine = str(analysis.get("engine", "auto")).strip().lower()
    scripts = analysis.get("scripts", [])
    if engine in ("script", "hybrid") and not scripts:
        warnings.append(
            "analysis.engine is '{}', but analysis.scripts lists no scripts.".format(engine))
    for entry in scripts:
        script_file = entry.get("file")
        if not script_file:
            continue
        script_path = os.path.join(TASK_DIR, "step2_analysis", str(script_file))
        if not os.path.exists(script_path) and not os.path.exists(
                _resolve_task_path(script_file)):
            warnings.append("Planned analysis script is missing: step2_analysis/{}".format(
                script_file))
            continue
        produces = entry.get("produces")
        if produces and not os.path.exists(_resolve_task_path(produces)):
            warnings.append("Analysis script {} declares an output that is missing: {}".format(
                script_file, produces))
    return warnings


def _validate_data_sources(inputs, quality_gates, results):
    """Return warnings for declared source systems and provenance closure."""
    warnings = []
    data_sources = inputs.get("data_sources", [])
    required = _as_bool(inputs.get("data_sources_required", False))
    if required and not data_sources:
        warnings.append(
            "inputs.data_sources_required is true, but no source systems are listed.")

    for entry in data_sources:
        system = entry.get("system", "<unnamed>")
        evidence = entry.get("evidence")
        if not evidence:
            if required or _is_required(quality_gates.get("provenance_closure")):
                warnings.append(
                    "Data source '{}' has no evidence path — captured records cannot "
                    "be traced.".format(system))
            continue
        if not os.path.exists(_resolve_task_path(evidence)):
            warnings.append("Captured evidence is missing for data source '{}': {}".format(
                system, evidence))

    if _is_required(quality_gates.get("provenance_closure")) and data_sources:
        cited = results.get("data_sources") or results.get("sources") if results else None
        if not cited:
            warnings.append(
                "Provenance closure is required, but results.json has no data_sources "
                "or sources section naming the systems the numbers came from.")
    return warnings


def _validate_work_record(report, quality_gates):
    """Return warnings when the method-and-data record is required but unusable."""
    setting = str(report.get("work_record", "auto")).strip().lower()
    gate = str(quality_gates.get("work_record", "auto")).strip().lower()
    if "skip" in (setting, gate):
        return []
    if not (_is_required(setting) or _is_required(gate)):
        return []

    record_path = os.path.join(TASK_DIR, "step3_report", "WORK_RECORD.md")
    if not os.path.exists(record_path):
        return ["Work record is required, but step3_report/WORK_RECORD.md is missing "
                "(build it with: neqsim work-record .)."]
    try:
        with open(record_path, "r", encoding="utf-8") as record_file:
            text = record_file.read()
    except OSError as error:
        return ["Work record could not be read: {}".format(error)]

    warnings = []
    blocks = re.findall(
        r"<!--\s*WORK_RECORD:NARRATIVE id=([A-Za-z0-9_\-]+)\s*-->\n?(.*?)\n?"
        r"<!--\s*/WORK_RECORD:NARRATIVE\s*-->", text, re.DOTALL)
    unfilled = [block_id for block_id, body in blocks
                if re.fullmatch(r"\[[^\]]*\]", (body or "").strip() or "[]")]
    if unfilled:
        warnings.append(
            "Work record narrative is still template text: {}.".format(
                ", ".join(unfilled)))
    if not blocks:
        warnings.append(
            "Work record has no narrative blocks — regenerate it with "
            "neqsim work-record so the method section is present.")
    return warnings


def _find_work_record_generator():
    """Locate devtools/generate_work_record.py from the environment or the tree.

    A task folder vendors its own copy of this report generator, so the search
    also walks up from the task itself — that is what lets an old task pick up
    the current work-record generator.
    """
    return _find_devtool("generate_work_record.py")


def _find_devtool(filename):
    """Locate a devtools script from NEQSIM_PROJECT_ROOT or the folder tree."""
    candidates = []
    project_root = os.environ.get("NEQSIM_PROJECT_ROOT")
    if project_root:
        candidates.append(os.path.join(project_root, "devtools", filename))
    for start in (os.path.dirname(os.path.abspath(__file__)), TASK_DIR):
        current = start
        for _ in range(6):
            candidates.append(os.path.join(current, "devtools", filename))
            candidates.append(os.path.join(current, filename))
            parent = os.path.dirname(current)
            if parent == current:
                break
            current = parent
    for candidate in candidates:
        if os.path.isfile(candidate):
            return candidate
    return None


def record_environment(results):
    """Record the software that produced this report into results.json.

    Stamped here rather than backfilled later: only the run that renders the
    deliverable can honestly claim which NeqSim produced its numbers.
    """
    if not isinstance(results, dict):
        return None
    tool = _find_devtool("task_corpus.py")
    if not tool:
        return None
    try:
        import importlib.util
        spec = importlib.util.spec_from_file_location("neqsim_task_corpus", tool)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        environment = module.capture_environment()
    except Exception as error:  # a provenance stamp must never break a report
        print("NOTE: environment not recorded ({}).".format(error))
        return None
    results["environment"] = environment
    try:
        with open(RESULTS_FILE, "r", encoding="utf-8-sig") as handle:
            stored = json.load(handle)
        if isinstance(stored, dict):
            stored["environment"] = environment
            with open(RESULTS_FILE, "w", encoding="utf-8") as handle:
                json.dump(stored, handle, indent=2, ensure_ascii=False)
                handle.write("\n")
    except (OSError, ValueError):
        pass
    return environment


def generate_work_record(config):
    """Build step3_report/WORK_RECORD.md unless the task opts out.

    The report carries the conclusion; the work record carries the method, the
    data, and the file map. It is regenerated with every report so the two
    deliverables cannot drift apart.
    """
    setting = str((config.get("report") or {}).get("work_record", "auto")).strip().lower()
    if setting == "skip":
        return
    generator = _find_work_record_generator()
    if not generator:
        print("")
        print("NOTE: work record not generated (devtools/generate_work_record.py "
              "not found). Run: neqsim work-record \"{}\"".format(TASK_DIR))
        return
    try:
        import importlib.util
        spec = importlib.util.spec_from_file_location("neqsim_work_record", generator)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        print("")
        module.main([TASK_DIR])
    except Exception as error:  # never block the report on the companion file
        print("")
        print("NOTE: work record could not be generated: {}".format(error))


def _validate_assumption_register(inputs, results):
    """Warn when information was not obtained but nothing was assumed in writing.

    A study that could not get a document still reached an answer somehow. If no
    assumption is registered, that substitution is invisible to the reader.
    """
    unobtained = []
    for entry in inputs.get("data_sources", []) or []:
        if not isinstance(entry, dict):
            continue
        evidence = str(entry.get("evidence", "") or "")
        if not evidence or not os.path.exists(_resolve_task_path(evidence)):
            unobtained.append(str(entry.get("system", "<unnamed>")))
    manifest_gaps = load_collection_manifest().get("data_gaps", []) or []
    if not unobtained and not manifest_gaps:
        return []
    if format_assumptions_text(results):
        return []
    detail = ", ".join(unobtained) if unobtained else "{} document gap(s)".format(
        len(manifest_gaps))
    return ["Information was not obtained ({}), but results.json registers no "
            "assumptions or data_gaps \u2014 state what was assumed in its place.".format(
                detail)]


def validate_study_config(config, results, task_spec):
    """Return warnings for missing deliverables required by study_config.yaml."""
    if not config:
        return []

    warnings = []
    inputs = config.get("inputs", {})
    analysis = config.get("analysis", {})
    notebooks = config.get("notebooks", {})
    report = config.get("report", {})
    quality_gates = config.get("quality_gates", {})

    documents = inputs.get("documents", [])
    documents_required = _as_bool(inputs.get("documents_required", False))
    if documents_required and not documents:
        warnings.append("inputs.documents_required is true, but no input documents are listed.")
    for document in documents:
        document_path = document.get("path")
        document_required = documents_required or _as_bool(document.get("required", False))
        if not document_path or not document_required:
            continue
        if os.path.isabs(str(document_path)):
            resolved_path = str(document_path)
        else:
            resolved_path = os.path.join(TASK_DIR, str(document_path))
        if not os.path.exists(resolved_path):
            warnings.append("Required input document path is missing: {}".format(document_path))
        elif os.path.isdir(resolved_path):
            files = [name for name in os.listdir(resolved_path)
                     if os.path.isfile(os.path.join(resolved_path, name))]
            if not files:
                warnings.append("Required input document directory is empty: {}".format(
                    document_path))

    planned_notebooks = [entry.get("file") for entry in notebooks.get("plan", [])
                         if entry.get("file")]
    existing_notebooks = glob.glob(os.path.join(TASK_DIR, "step2_analysis", "*.ipynb"))
    minimum_count = _as_int(notebooks.get("minimum_count"), 0)
    notebooks_required = _as_bool(notebooks.get("required", True))
    notebook_execution_required = _as_bool(notebooks.get("execution_required", False))
    execution_engine = str(notebooks.get("execution_engine", "")).strip().lower()
    analysis_engine = str(analysis.get("engine", "auto")).strip().lower()
    script_backed = (not notebooks_required
                     and (execution_engine == "script" or analysis_engine == "script"))
    if notebooks_required or notebook_execution_required or minimum_count:
        if minimum_count and len(existing_notebooks) < minimum_count and not script_backed:
            warnings.append(
                "Configured notebook minimum is {}, but {} notebook(s) exist.".format(
                    minimum_count, len(existing_notebooks)))
        if notebooks_required and not script_backed:
            for notebook_file in planned_notebooks:
                notebook_path = os.path.join(TASK_DIR, "step2_analysis", notebook_file)
                if not os.path.exists(notebook_path):
                    warnings.append("Planned notebook is missing: step2_analysis/{}".format(
                        notebook_file))

        warnings.extend(_validate_runner_execution(notebooks, planned_notebooks,
                                                   existing_notebooks))

    warnings.extend(_validate_analysis_scripts(analysis))
    warnings.extend(_validate_data_sources(inputs, quality_gates, results or {}))
    warnings.extend(_validate_work_record(report, quality_gates))
    warnings.extend(_validate_assumption_register(inputs, results or {}))

    if _as_bool(quality_gates.get("require_results_json", False)) and not results:
        warnings.append("quality_gates.require_results_json is true, but results.json is missing.")
    if _is_required(quality_gates.get("benchmark_validation")) and not (
            results and results.get("benchmark_validation")):
        benchmark_kind = str(quality_gates.get("benchmark_kind", "auto")).strip().lower()
        if benchmark_kind in ("", "auto", "none"):
            benchmark_kind = "independent reference"
        warnings.append(
            "Benchmark validation ({}) is required, but results.json has no "
            "benchmark_validation section.".format(benchmark_kind.replace("_", " ")))
    if _is_required(quality_gates.get("human_review")) and not (
            results and (results.get("human_review") or results.get("review"))):
        warnings.append(
            "Human review is required, but results.json has no human_review section "
            "recording the reviewer and sign-off status.")
    if _is_required(quality_gates.get("uncertainty_analysis")) and not (
            results and results.get("uncertainty")):
        warnings.append("Uncertainty analysis is required, but results.json has no uncertainty section.")
    if _is_required(quality_gates.get("risk_register")) and not (
            results and (results.get("risk_evaluation") or results.get("risks"))):
        warnings.append("Risk register is required, but results.json has no risk_evaluation or risks section.")
    if _is_required(quality_gates.get("figure_discussion")) and not (
            results and results.get("figure_discussion")):
        warnings.append("Figure discussion is required, but results.json has no figure_discussion section.")
    if _is_required(quality_gates.get("consistency_checker")):
        consistency_path = os.path.join(TASK_DIR, "consistency_report.json")
        if not os.path.exists(consistency_path):
            warnings.append("Consistency checker is required, but consistency_report.json is missing.")

    minimum_figures = _as_int(quality_gates.get("minimum_figures"), 0)
    figure_count = len(glob.glob(os.path.join(FIG_DIR, "*.png")))
    if minimum_figures and figure_count < minimum_figures:
        warnings.append("Configured figure minimum is {}, but {} PNG figure(s) exist.".format(
            minimum_figures, figure_count))

    for section in report.get("required_sections", []):
        if not _required_section_available(section, results, task_spec):
            warnings.append("Required report section lacks source data: {}".format(section))

    return warnings


def _md_table_to_html(lines):
    """Convert markdown table lines to an HTML table string."""
    if len(lines) < 2:
        return ""
    # Parse header
    header_cells = [c.strip() for c in lines[0].strip().strip("|").split("|")]
    # Skip separator line (line 1)
    html = '<table class="scope-table"><thead><tr>'
    for cell in header_cells:
        html += "<th>{}</th>".format(_md_inline(cell))
    html += "</tr></thead><tbody>\n"
    for row_line in lines[2:]:
        cells = [c.strip() for c in row_line.strip().strip("|").split("|")]
        html += "<tr>"
        for cell in cells:
            html += "<td>{}</td>".format(_md_inline(cell))
        html += "</tr>\n"
    html += "</tbody></table>"
    return html


def _md_inline(text):
    """Convert inline markdown (bold) to HTML."""
    import re as _re
    # **bold**
    text = _re.sub(r"\*\*(.+?)\*\*", r"<strong>\1</strong>", text)
    return text


def _md_list_to_html(lines):
    """Convert markdown bullet list lines to an HTML list."""
    html = "<ul>\n"
    for line in lines:
        item = line.lstrip("- ").strip()
        html += "  <li>{}</li>\n".format(_md_inline(item))
    html += "</ul>"
    return html


def scope_content_to_html(content):
    """Convert scope section content (from task_spec.md) to styled HTML.

    Handles markdown tables, bold text, bullet lists, and sub-headings.
    """
    lines = content.split("\n")
    html_parts = []
    i = 0
    while i < len(lines):
        line = lines[i]

        # Blank line
        if not line.strip():
            i += 1
            continue

        # Sub-heading (e.g., "Applicable Standards:")
        if (line.strip().endswith(":") and not line.strip().startswith("-")
                and not line.strip().startswith("|") and not line.strip().startswith("*")):
            html_parts.append("<h3>{}</h3>".format(_md_inline(line.strip())))
            i += 1
            continue

        # Markdown table (starts with |)
        if line.strip().startswith("|"):
            table_lines = []
            while i < len(lines) and lines[i].strip().startswith("|"):
                table_lines.append(lines[i])
                i += 1
            # Check if second line is separator (|---|)
            if len(table_lines) >= 2 and set(table_lines[1].replace("|", "").replace("-", "").replace(":", "").strip()) <= set(""):
                html_parts.append(_md_table_to_html(table_lines))
            else:
                # Not a real table, just text with pipes
                for tl in table_lines:
                    html_parts.append("<p>{}</p>".format(_md_inline(tl.strip())))
            continue

        # Bullet list (starts with -)
        if line.strip().startswith("- "):
            list_lines = []
            while i < len(lines) and lines[i].strip().startswith("- "):
                list_lines.append(lines[i])
                i += 1
            html_parts.append(_md_list_to_html(list_lines))
            continue

        # Regular paragraph
        html_parts.append("<p>{}</p>".format(_md_inline(line.strip())))
        i += 1

    return "\n".join(html_parts)


def render_scope_to_word(doc, content):
    """Render scope section content (from task_spec.md) into a Word document.

    Parses markdown tables into Word tables, bold text into runs, and
    bullet lists into formatted paragraphs.
    """
    lines = content.split("\n")
    i = 0
    while i < len(lines):
        line = lines[i]

        # Blank line
        if not line.strip():
            i += 1
            continue

        # Sub-heading (e.g., "Applicable Standards:")
        if (line.strip().endswith(":") and not line.strip().startswith("-")
                and not line.strip().startswith("|") and not line.strip().startswith("*")):
            _add_heading(doc, line.strip(), level=2)
            i += 1
            continue

        # Markdown table
        if line.strip().startswith("|"):
            table_lines = []
            while i < len(lines) and lines[i].strip().startswith("|"):
                table_lines.append(lines[i])
                i += 1
            if len(table_lines) >= 2:
                _md_table_to_word(doc, table_lines)
            else:
                for tl in table_lines:
                    doc.add_paragraph(tl.strip())
            continue

        # Bullet list
        if line.strip().startswith("- "):
            while i < len(lines) and lines[i].strip().startswith("- "):
                item_text = lines[i].strip()[2:]  # Remove "- "
                p = doc.add_paragraph(style="List Bullet")
                _add_bold_runs(p, item_text)
                i += 1
            continue

        # Regular paragraph
        p = doc.add_paragraph()
        _add_bold_runs(p, line.strip())
        i += 1


def _md_table_to_word(doc, table_lines):
    """Convert markdown table lines to a styled Word table."""
    header_cells = [c.strip() for c in table_lines[0].strip().strip("|").split("|")]
    data_rows = []
    for row_line in table_lines[2:]:  # skip header and separator
        cells = [c.strip() for c in row_line.strip().strip("|").split("|")]
        data_rows.append(cells)
    add_word_table(doc, header_cells, data_rows)


def _add_bold_runs(paragraph, text):
    """Add text with **bold** sections as separate runs."""
    import re as _re
    parts = _re.split(r"(\*\*.+?\*\*)", text)
    for part in parts:
        if part.startswith("**") and part.endswith("**"):
            run = paragraph.add_run(part[2:-2])
            run.bold = True
        else:
            paragraph.add_run(part)


def get_figures():
    """Collect all PNG/SVG figures from the figures/ directory."""
    pngs = sorted(glob.glob(os.path.join(FIG_DIR, "*.png")))
    svgs = sorted(glob.glob(os.path.join(FIG_DIR, "*.svg")))
    return pngs + svgs


def get_figure_caption(fig_path, results, fig_index):
    """Get a caption for a figure: custom from results.json or auto-generated."""
    fig_name = os.path.basename(fig_path)
    captions = {}
    if results:
        captions = results.get("figure_captions", {})
    if fig_name in captions:
        return "Figure {}: {}".format(fig_index, captions[fig_name])
    # Auto-generate from filename
    auto = fig_name.rsplit(".", 1)[0].replace("_", " ").replace("-", " ").title()
    return "Figure {}: {}".format(fig_index, auto)


def get_equations(results):
    """Get equations from results.json. Returns list of {label, latex}."""
    if not results:
        return []
    return results.get("equations", [])


def render_equation_to_image(latex_str, output_path):
    """Render a LaTeX equation to a PNG sized for EQ_FONT_PT in the document.

    Rendered at EQ_FONT_PT and EQ_RENDER_DPI so that placing the image at its
    natural size (pixels / EQ_RENDER_DPI inches) reproduces exactly that point
    size. Returns True if the image was created, False otherwise.
    """
    if not HAS_MATPLOTLIB:
        return False
    try:
        fig = plt.figure(figsize=(8, 1.2))
        fig.text(
            0.5, 0.5,
            "${}$".format(latex_str),
            fontsize=EQ_FONT_PT, ha="center", va="center",
            math_fontfamily="cm",
        )
        fig.savefig(output_path, dpi=EQ_RENDER_DPI, bbox_inches="tight",
                    pad_inches=0.04, facecolor="white", edgecolor="none")
        plt.close(fig)
        return True
    except Exception as e:
        print("  Warning: could not render equation: {}".format(e))
        return False


def _png_pixel_size(path):
    """Return (width, height) in pixels from the PNG IHDR chunk, else None."""
    try:
        with open(path, "rb") as handle:
            header = handle.read(24)
        if len(header) < 24 or header[:8] != b"\x89PNG\r\n\x1a\n":
            return None
        return (int.from_bytes(header[16:20], "big"),
                int.from_bytes(header[20:24], "big"))
    except Exception:
        return None


def _add_equation_picture(doc, image_path, max_width_in):
    """Insert an equation image at its natural size, capped to the text width."""
    size = _png_pixel_size(image_path)
    if size and size[0] > 0:
        width_in = min(size[0] / float(EQ_RENDER_DPI), max_width_in)
    else:
        width_in = min(3.0, max_width_in)
    doc.add_picture(image_path, width=Inches(width_in))


def _parse_key_name(key):
    """Parse a key_results key into (label, unit). Splits on last known unit suffix."""
    unit_suffixes = [
        ("_pct", "%"), ("_percent", "%"),
        ("_bar", "bar"), ("_bara", "bara"), ("_barg", "barg"),
        ("_psi", "psi"), ("_psia", "psia"),
        ("_C", "°C"), ("_K", "K"), ("_F", "°F"),
        ("_kg", "kg"), ("_g", "g"), ("_lb", "lb"),
        ("_m3", "m³"), ("_m2", "m²"), ("_m", "m"),
        ("_mm", "mm"), ("_cm", "cm"), ("_km", "km"),
        ("_ft", "ft"), ("_in", "in"),
        ("_kW", "kW"), ("_MW", "MW"), ("_W", "W"),
        ("_kJ", "kJ"), ("_MJ", "MJ"), ("_J", "J"),
        ("_kg_hr", "kg/hr"), ("_kg_s", "kg/s"),
        ("_m3_hr", "m³/hr"), ("_m3_s", "m³/s"),
        ("_Sm3_day", "Sm³/day"), ("_Sm3_hr", "Sm³/hr"),
        ("_hours", "hours"), ("_hr", "hr"), ("_min", "min"), ("_s", "s"),
        ("_rpm", "rpm"), ("_Hz", "Hz"),
    ]
    for suffix, unit in unit_suffixes:
        if key.endswith(suffix):
            name_part = key[:len(key) - len(suffix)]
            label = name_part.replace("_", " ").title()
            return label, unit
    return key.replace("_", " ").title(), ""


def format_results_table(results):
    """Format key_results dict as a text table."""
    key_results = results.get("key_results", {})
    if not key_results:
        return "[No key_results in results.json]"
    lines = []
    for key, value in key_results.items():
        label, unit = _parse_key_name(key)
        if isinstance(value, float):
            val_str = "{:.4g}".format(value)
        else:
            val_str = str(value)
        if unit:
            lines.append("{}: {} {}".format(label, val_str, unit))
        else:
            lines.append("{}: {}".format(label, val_str))
    return "\n".join(lines)


def format_validation_table(results):
    """Format validation checks as a text table."""
    validation = results.get("validation", {})
    if not validation:
        return "[No validation data in results.json]"
    lines = ["Validation Summary:", ""]
    for check, outcome in validation.items():
        label = check.replace("_", " ").title()
        if isinstance(outcome, bool):
            status = "PASS" if outcome else "FAIL"
        elif isinstance(outcome, (int, float)):
            status = "{:.4g}".format(outcome)
        else:
            status = str(outcome)
        lines.append("  {}: {}".format(label, status))
    return "\n".join(lines)


def format_validation_html(results):
    """Format validation checks as an HTML table."""
    validation = results.get("validation", {})
    if not validation:
        return "<p><em>No validation data in results.json</em></p>"
    rows = ""
    for check, outcome in validation.items():
        label = check.replace("_", " ").title()
        if isinstance(outcome, bool):
            status = "PASS" if outcome else "FAIL"
            css_class = ' class="pass"' if outcome else ' class="fail"'
        elif isinstance(outcome, (int, float)):
            status = "{:.4g}".format(outcome)
            css_class = ""
        else:
            status = str(outcome)
            css_class = ""
        rows += '<tr><td>{}</td><td{}>{}</td></tr>\n'.format(
            label, css_class, status)
    return '<table class="validation-table"><thead><tr><th>Check</th><th>Result</th></tr></thead><tbody>\n{}</tbody></table>'.format(rows)


def format_results_html(results):
    """Format key_results dict as a styled HTML table with units column."""
    key_results = results.get("key_results", {})
    if not key_results:
        return ""
    rows = ""
    for key, value in key_results.items():
        label, unit = _parse_key_name(key)
        if isinstance(value, float):
            val_str = "{:.4g}".format(value)
        else:
            val_str = str(value)
        rows += '<tr><td>{}</td><td class="num">{}</td><td>{}</td></tr>\n'.format(
            label, val_str, unit)
    return (
        '<table class="results-table"><thead>'
        '<tr><th>Parameter</th><th>Value</th><th>Unit</th></tr>'
        '</thead><tbody>\n{}</tbody></table>'.format(rows)
    )


def format_custom_tables_html(results):
    """Format custom tables from results.json 'tables' key as HTML."""
    tables = results.get("tables", [])
    if not tables:
        return ""
    html_parts = []
    for tbl in tables:
        title = tbl.get("title", "")
        headers = tbl.get("headers", [])
        data_rows = tbl.get("rows", [])
        if not headers or not data_rows:
            continue
        h = ""
        if title:
            h += '<h3>{}</h3>\n'.format(title)
        h += '<table class="custom-table"><thead><tr>'
        for col in headers:
            h += '<th>{}</th>'.format(col)
        h += '</tr></thead><tbody>\n'
        for row in data_rows:
            h += "<tr>"
            for i, cell in enumerate(row):
                css = ' class="num"' if i > 0 and isinstance(cell, (int, float)) else ""
                if isinstance(cell, float):
                    h += '<td{}>{:.4g}</td>'.format(css, cell)
                else:
                    h += '<td{}>{}</td>'.format(css, cell)
            h += "</tr>\n"
        h += "</tbody></table>"
        html_parts.append(h)
    return "\n".join(html_parts)


def format_references_html(results):
    """Format the references list from results.json as a styled HTML ordered list."""
    refs = results.get("references", [])
    if not refs:
        return ""
    h = '<ol class="reference-list">\n'
    for ref in refs:
        ref_id = ref.get("id", "")
        ref_text = ref.get("text", "")
        if ref_id:
            h += '  <li id="ref-{}"><strong>[{}]</strong> {}</li>\n'.format(
                ref_id, ref_id, ref_text)
        else:
            h += '  <li>{}</li>\n'.format(ref_text)
    h += '</ol>'
    return h


def format_risk_html(results):
    """Format risk_evaluation from results.json as a styled HTML risk table.

    Renders a summary card with overall risk level, then a professional
    table with color-coded risk levels and mitigation measures.
    """
    re_data = results.get("risk_evaluation", {})
    if not re_data:
        return ""
    risks = re_data.get("risks", [])
    matrix = re_data.get("risk_matrix_used", "5x5 (ISO 31000)")
    overall = re_data.get("overall_risk_level", "N/A")
    high_count = re_data.get("high_risk_count",
                             sum(1 for r in risks if r.get("risk_level") == "High"))
    medium_count = re_data.get("medium_risk_count",
                               sum(1 for r in risks if r.get("risk_level") == "Medium"))
    low_count = sum(1 for r in risks if r.get("risk_level") == "Low")

    h = '<div class="risk-summary-card">\n'
    h += '<p>Risk assessment performed using <strong>{}</strong> framework.</p>\n'.format(
        matrix)
    h += '<p>Overall risk level: <span class="risk-badge risk-{}">{}</span>'.format(
        overall.lower(), overall)
    h += '&nbsp;&mdash;&nbsp;'
    parts = []
    if high_count:
        parts.append('<span class="risk-badge risk-high">{} High</span>'.format(high_count))
    if medium_count:
        parts.append('<span class="risk-badge risk-medium">{} Medium</span>'.format(medium_count))
    if low_count:
        parts.append('<span class="risk-badge risk-low">{} Low</span>'.format(low_count))
    h += ", ".join(parts) if parts else ""
    h += '</p>\n</div>\n'

    if risks:
        h += '<table class="risk-table"><thead><tr>'
        h += '<th>ID</th><th>Category</th><th>Risk Description</th>'
        h += '<th>Likelihood</th><th>Consequence</th><th>Risk Level</th>'
        h += '<th>Mitigation</th>'
        h += '</tr></thead><tbody>\n'
        for risk in risks:
            level = risk.get("risk_level", "").lower()
            h += '<tr>'
            h += '<td><strong>{}</strong></td>'.format(risk.get("id", ""))
            h += '<td>{}</td>'.format(risk.get("category", ""))
            h += '<td>{}</td>'.format(risk.get("description", ""))
            h += '<td>{}</td>'.format(risk.get("likelihood", ""))
            h += '<td>{}</td>'.format(risk.get("consequence", ""))
            h += '<td class="risk-level risk-{}">{}</td>'.format(level, risk.get("risk_level", ""))
            h += '<td class="mitigation-cell">{}</td>'.format(risk.get("mitigation", ""))
            h += '</tr>\n'
        h += '</tbody></table>\n'
    return h


def format_workflow_html(results):
    """Format agent_workflow_plan from results.json as a Solution Workflow block.

    Documents *how* the task was solved: the discovered/used specialist agents,
    the workflow composition, and the rationale. Reuses the existing risk-card
    and risk-table CSS classes so no stylesheet changes are needed.
    """
    plan = results.get("agent_workflow_plan", {})
    if not plan:
        return ""
    h = '<div class="risk-summary-card">\n'
    wtype = plan.get("workflow_type", "")
    workflow = plan.get("workflow", "")
    if wtype:
        h += '<p>Workflow type: <strong>{}</strong></p>\n'.format(wtype)
    if workflow:
        h += '<p>Composition: <code>{}</code></p>\n'.format(workflow)
    disc = plan.get("discovery", {})
    if isinstance(disc, dict) and (disc.get("skill_search") or disc.get("agent_search")):
        bits = []
        if disc.get("skill_search"):
            bits.append("skill_search")
        if disc.get("agent_search"):
            bits.append("agent_search &rarr; {}".format(disc.get("agent_search")))
        h += '<p>Discovery: {}</p>\n'.format(", ".join(bits))
    h += '</div>\n'

    agents = plan.get("agents_used", [])
    if agents:
        h += '<table class="risk-table"><thead><tr>'
        h += '<th>Agent</th><th>Repo</th><th>Role</th><th>Loads skills</th>'
        h += '</tr></thead><tbody>\n'
        for a in agents:
            handle = a.get("handle") or a.get("name", "")
            skills = a.get("loads_skills", [])
            skills_txt = ", ".join(skills) if isinstance(skills, list) else str(skills)
            h += '<tr>'
            h += '<td><strong>{}</strong></td>'.format(handle)
            h += '<td>{}</td>'.format(a.get("repo", ""))
            h += '<td>{}</td>'.format(a.get("role", ""))
            h += '<td>{}</td>'.format(skills_txt)
            h += '</tr>\n'
        h += '</tbody></table>\n'

    rationale = plan.get("rationale", "")
    if rationale:
        h += '<p><em>{}</em></p>\n'.format(rationale)
    return h


def add_workflow_word_section(doc, results):
    """Add the Solution Workflow (agent_workflow_plan) as text plus a Word table."""
    plan = results.get("agent_workflow_plan", {})
    if not plan:
        return
    wtype = plan.get("workflow_type", "")
    workflow = plan.get("workflow", "")
    if wtype:
        p = doc.add_paragraph()
        p.add_run("Workflow type: ").font.size = Pt(BODY_PT)
        run = p.add_run(str(wtype))
        run.bold = True
        run.font.size = Pt(BODY_PT)
    if workflow:
        p = doc.add_paragraph()
        p.add_run("Composition: ").font.size = Pt(BODY_PT)
        p.add_run(str(workflow)).font.size = Pt(BODY_PT)
    disc = plan.get("discovery", {})
    if isinstance(disc, dict) and (disc.get("skill_search") or disc.get("agent_search")):
        bits = []
        if disc.get("skill_search"):
            bits.append("skill_search")
        if disc.get("agent_search"):
            bits.append("agent_search: {}".format(disc.get("agent_search")))
        p = doc.add_paragraph()
        p.add_run("Discovery: {}".format(", ".join(bits))).font.size = Pt(BODY_PT)

    agents = plan.get("agents_used", [])
    if agents:
        headers = ["Agent", "Repo", "Role", "Loads skills"]
        data_rows = []
        for a in agents:
            handle = a.get("handle") or a.get("name", "")
            skills = a.get("loads_skills", [])
            skills_txt = ", ".join(skills) if isinstance(skills, list) else str(skills)
            data_rows.append([
                str(handle), str(a.get("repo", "")), str(a.get("role", "")), skills_txt
            ])
        add_word_table(doc, headers, data_rows)

    rationale = plan.get("rationale", "")
    if rationale:
        p = doc.add_paragraph()
        run = p.add_run(str(rationale))
        run.italic = True
        run.font.size = Pt(BODY_PT)


def format_uncertainty_html(results):
    """Format uncertainty analysis from results.json as styled HTML tables.

    Renders: input parameters table, P10/P50/P90 output table, tornado table.
    """
    unc = results.get("uncertainty", {})
    if not unc:
        return ""
    h = ""

    # Summary paragraph
    h += '<div class="uncertainty-summary">\n'
    h += '<p><strong>{}</strong> with <strong>{}</strong> simulations'.format(
        unc.get("method", "Monte Carlo analysis"),
        unc.get("n_simulations", "N/A"))
    engine = unc.get("simulation_engine", "")
    if engine:
        h += ' using {}'.format(engine)
    h += '.</p>\n</div>\n'

    # Input parameters table
    params = unc.get("input_parameters", [])
    if params:
        h += '<h3>Input Parameter Ranges</h3>\n'
        h += '<table class="uncertainty-table"><thead><tr>'
        h += '<th>Parameter</th><th>Unit</th><th>Low</th><th>Base</th>'
        h += '<th>High</th><th>Distribution</th>'
        h += '</tr></thead><tbody>\n'
        for p in params:
            h += '<tr>'
            h += '<td>{}</td>'.format(p.get("name", ""))
            h += '<td>{}</td>'.format(p.get("unit", ""))
            h += '<td class="num">{}</td>'.format(_fmt_num(p.get("low", "")))
            h += '<td class="num">{}</td>'.format(_fmt_num(p.get("base", "")))
            h += '<td class="num">{}</td>'.format(_fmt_num(p.get("high", "")))
            h += '<td>{}</td>'.format(p.get("distribution", ""))
            h += '</tr>\n'
        h += '</tbody></table>\n'

    # Output P10/P50/P90 table — handle both single-output and multi-output formats
    out_param = unc.get("output_parameter", "")
    out_params = unc.get("output_parameters", {})
    if out_param or out_params:
        h += '<h3>Output Distribution (P10 / P50 / P90)</h3>\n'
        h += '<table class="uncertainty-table"><thead><tr>'
        h += '<th>Output Parameter</th><th>P10</th><th>P50</th><th>P90</th>'
        h += '</tr></thead><tbody>\n'
        if out_param:
            h += '<tr><td>{}</td>'.format(out_param)
            h += '<td class="num">{}</td>'.format(_fmt_num(unc.get("p10", "")))
            h += '<td class="num"><strong>{}</strong></td>'.format(_fmt_num(unc.get("p50", "")))
            h += '<td class="num">{}</td></tr>\n'.format(_fmt_num(unc.get("p90", "")))
        for key, val in out_params.items():
            label = key.replace("_", " ").title()
            h += '<tr><td>{}</td>'.format(label)
            h += '<td class="num">{}</td>'.format(_fmt_num(val.get("p10", "")))
            h += '<td class="num"><strong>{}</strong></td>'.format(_fmt_num(val.get("p50", "")))
            h += '<td class="num">{}</td></tr>\n'.format(_fmt_num(val.get("p90", "")))
        h += '</tbody></table>\n'

    # Tornado sensitivity table
    tornado = unc.get("tornado", [])
    if tornado:
        h += '<h3>Sensitivity Ranking (Tornado)</h3>\n'
        h += '<table class="tornado-table"><thead><tr>'
        # Detect column names from first tornado entry
        first = tornado[0]
        cols = [k for k in first.keys() if k != "parameter"]
        h += '<th>Parameter</th>'
        for col in cols:
            h += '<th>{}</th>'.format(col.replace("_", " ").title())
        h += '</tr></thead><tbody>\n'
        for item in tornado:
            h += '<tr><td>{}</td>'.format(item.get("parameter", ""))
            for col in cols:
                h += '<td class="num">{}</td>'.format(_fmt_num(item.get(col, "")))
            h += '</tr>\n'
        h += '</tbody></table>\n'

    return h


def format_benchmark_html(results):
    """Format benchmark_validation from results.json as a styled HTML table."""
    bv = results.get("benchmark_validation", {})
    if not bv:
        return ""
    h = '<table class="benchmark-table"><thead><tr>'
    h += '<th>Test</th><th>Description</th><th>Status</th><th>Details</th>'
    h += '</tr></thead><tbody>\n'
    for key, val in bv.items():
        if not isinstance(val, dict):
            continue
        label = key.replace("_", " ").title()
        desc = val.get("description") or val.get("reference") or key
        status = val.get("status")
        if status is None:
            p = val.get("pass")
            status = "PASS" if p is True else ("FAIL" if p is False else "N/A")
        css = ' class="pass"' if status == "PASS" else (' class="fail"' if status == "FAIL" else "")
        # Gather numeric details
        details = []
        for dk, dv in val.items():
            if dk in ("description", "status", "reference", "pass", "points"):
                continue
            dl = dk.replace("_", " ").title()
            if isinstance(dv, float):
                details.append("{}: {:.4g}".format(dl, dv))
            else:
                details.append("{}: {}".format(dl, dv))
        h += '<tr>'
        h += '<td><strong>{}</strong></td>'.format(label)
        h += '<td>{}</td>'.format(desc)
        h += '<td{}><strong>{}</strong></td>'.format(css, status)
        h += '<td>{}</td>'.format("; ".join(details) if details else "")
        h += '</tr>\n'
    h += '</tbody></table>\n'
    return h


def _fmt_num(value):
    """Format a numeric value for display in tables."""
    if isinstance(value, float):
        if abs(value) >= 1000 or (abs(value) < 0.01 and value != 0):
            return "{:.4g}".format(value)
        return "{:.4g}".format(value)
    return str(value)


def add_risk_word_table(doc, results):
    """Add risk evaluation as a styled Word table with color-coded risk levels."""
    re_data = results.get("risk_evaluation", {})
    if not re_data:
        return
    risks = re_data.get("risks", [])
    matrix = re_data.get("risk_matrix_used", "5x5 (ISO 31000)")
    overall = re_data.get("overall_risk_level", "N/A")
    high_count = re_data.get("high_risk_count",
                             sum(1 for r in risks if r.get("risk_level") == "High"))
    medium_count = re_data.get("medium_risk_count",
                               sum(1 for r in risks if r.get("risk_level") == "Medium"))

    # Summary paragraph
    p = doc.add_paragraph()
    p.add_run("Risk assessment using ").font.size = Pt(BODY_PT)
    r = p.add_run("{} framework".format(matrix))
    r.bold = True
    r.font.size = Pt(BODY_PT)
    p.add_run(". Overall risk level: ").font.size = Pt(BODY_PT)
    r2 = p.add_run(overall)
    r2.bold = True
    r2.font.size = Pt(BODY_PT)
    if overall == "High":
        r2.font.color.rgb = RGBColor(0xDC, 0x35, 0x45)
    elif overall == "Medium":
        r2.font.color.rgb = RGBColor(0xE6, 0x7E, 0x22)
    elif overall == "Low":
        r2.font.color.rgb = RGBColor(0x28, 0xA7, 0x45)
    p.add_run(". ({} High, {} Medium)".format(high_count, medium_count)).font.size = Pt(BODY_PT)

    if not risks:
        return
    headers = ["ID", "Category", "Description", "Likelihood", "Consequence",
               "Risk Level", "Mitigation"]
    data_rows = []
    for risk in risks:
        data_rows.append([
            risk.get("id", ""),
            risk.get("category", ""),
            risk.get("description", ""),
            risk.get("likelihood", ""),
            risk.get("consequence", ""),
            risk.get("risk_level", ""),
            risk.get("mitigation", ""),
        ])
    table = add_word_table(doc, headers, data_rows)
    # Color-code risk level column (column 5, 0-indexed)
    for row in table.rows[1:]:
        cell = row.cells[5]
        text = cell.text.strip()
        for paragraph in cell.paragraphs:
            for run in paragraph.runs:
                run.font.bold = True
                if text == "High":
                    run.font.color.rgb = RGBColor(0xDC, 0x35, 0x45)
                elif text == "Medium":
                    run.font.color.rgb = RGBColor(0xE6, 0x7E, 0x22)
                elif text == "Low":
                    run.font.color.rgb = RGBColor(0x28, 0xA7, 0x45)


def add_uncertainty_word_tables(doc, results):
    """Add uncertainty analysis as styled Word tables."""
    unc = results.get("uncertainty", {})
    if not unc:
        return

    # Summary paragraph
    p = doc.add_paragraph()
    p.add_run("{} with {} simulations".format(
        unc.get("method", "Monte Carlo analysis"),
        unc.get("n_simulations", "N/A"))).font.size = Pt(BODY_PT)
    engine = unc.get("simulation_engine", "")
    if engine:
        p.add_run(" using {}.".format(engine)).font.size = Pt(BODY_PT)

    # Input parameters table
    params = unc.get("input_parameters", [])
    if params:
        _add_heading(doc, "Input Parameter Ranges", level=2)
        headers = ["Parameter", "Unit", "Low", "Base", "High", "Distribution"]
        data_rows = []
        for param in params:
            data_rows.append([
                param.get("name", ""),
                param.get("unit", ""),
                _fmt_num(param.get("low", "")),
                _fmt_num(param.get("base", "")),
                _fmt_num(param.get("high", "")),
                param.get("distribution", ""),
            ])
        add_word_table(doc, headers, data_rows)

    # Output P10/P50/P90 table
    out_param = unc.get("output_parameter", "")
    out_params = unc.get("output_parameters", {})
    if out_param or out_params:
        _add_heading(doc, "Output Distribution (P10 / P50 / P90)", level=2)
        headers = ["Output Parameter", "P10", "P50", "P90"]
        data_rows = []
        if out_param:
            data_rows.append([
                out_param,
                _fmt_num(unc.get("p10", "")),
                _fmt_num(unc.get("p50", "")),
                _fmt_num(unc.get("p90", "")),
            ])
        for key, val in out_params.items():
            label = key.replace("_", " ").title()
            data_rows.append([
                label,
                _fmt_num(val.get("p10", "")),
                _fmt_num(val.get("p50", "")),
                _fmt_num(val.get("p90", "")),
            ])
        add_word_table(doc, headers, data_rows,
                       col_widths=[Inches(2.5), Inches(1.0), Inches(1.0), Inches(1.0)])

    # Tornado sensitivity table
    tornado = unc.get("tornado", [])
    if tornado:
        _add_heading(doc, "Sensitivity Ranking (Tornado)", level=2)
        first = tornado[0]
        cols = [k for k in first.keys() if k != "parameter"]
        headers = ["Parameter"] + [c.replace("_", " ").title() for c in cols]
        data_rows = []
        for item in tornado:
            row = [item.get("parameter", "")]
            for col in cols:
                row.append(_fmt_num(item.get(col, "")))
            data_rows.append(row)
        add_word_table(doc, headers, data_rows)


def add_benchmark_word_table(doc, results):
    """Add benchmark validation as a styled Word table."""
    bv = results.get("benchmark_validation", {})
    if not bv:
        return
    headers = ["Test", "Description", "Status", "Details"]
    data_rows = []
    for key, val in bv.items():
        if not isinstance(val, dict):
            continue
        label = key.replace("_", " ").title()
        desc = val.get("description") or val.get("reference") or key
        status = val.get("status")
        if status is None:
            p = val.get("pass")
            status = "PASS" if p is True else ("FAIL" if p is False else "N/A")
        details = []
        for dk, dv in val.items():
            if dk in ("description", "status", "reference", "pass", "points"):
                continue
            dl = dk.replace("_", " ").title()
            if isinstance(dv, float):
                details.append("{}: {:.4g}".format(dl, dv))
            else:
                details.append("{}: {}".format(dl, dv))
        data_rows.append([label, desc, status, "; ".join(details)])
    table = add_word_table(doc, headers, data_rows)
    # Color-code status column (column 2, 0-indexed)
    for row in table.rows[1:]:
        cell = row.cells[2]
        text = cell.text.strip()
        for paragraph in cell.paragraphs:
            for run in paragraph.runs:
                run.font.bold = True
                if text == "PASS":
                    run.font.color.rgb = RGBColor(0x28, 0xA7, 0x45)
                elif text == "FAIL":
                    run.font.color.rgb = RGBColor(0xDC, 0x35, 0x45)


def add_word_table(doc, headers, data_rows, col_widths=None):
    """Add a professionally styled table to a Word document.

    Args:
        doc: Document object.
        headers: list of column header strings.
        data_rows: list of lists (each inner list = one row of cell values).
        col_widths: optional list of Inches widths per column.
    """
    table = doc.add_table(rows=1, cols=len(headers))
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    _set_table_style(table)

    # Header row
    hdr = table.rows[0]
    for i, text in enumerate(headers):
        cell = hdr.cells[i]
        cell.text = str(text)
        # Style header: bold, white text on dark blue background
        for paragraph in cell.paragraphs:
            for run in paragraph.runs:
                run.font.bold = True
                run.font.size = Pt(TABLE_PT)
                run.font.color.rgb = RGBColor(0xFF, 0xFF, 0xFF)
        shading = parse_xml(
            '<w:shd {} w:fill="2F5496"/>'.format(nsdecls('w'))
        )
        cell._tc.get_or_add_tcPr().append(shading)

    # Data rows
    for row_data in data_rows:
        row = table.add_row()
        for i, val in enumerate(row_data):
            cell = row.cells[i]
            cell.text = str(val)
            for paragraph in cell.paragraphs:
                for run in paragraph.runs:
                    run.font.size = Pt(TABLE_PT)

    # Apply column widths if specified
    if col_widths:
        for i, width in enumerate(col_widths):
            for row in table.rows:
                row.cells[i].width = width

    doc.add_paragraph("")  # spacing after table
    return table


def add_results_word_table(doc, results):
    """Add key_results as a styled Word table with units column."""
    key_results = results.get("key_results", {})
    if not key_results:
        return
    headers = ["Parameter", "Value", "Unit"]
    data_rows = []
    for key, value in key_results.items():
        label, unit = _parse_key_name(key)
        if isinstance(value, float):
            val_str = "{:.4g}".format(value)
        else:
            val_str = str(value)
        data_rows.append([label, val_str, unit])
    add_word_table(doc, headers, data_rows,
                   col_widths=[Inches(3.0), Inches(1.5), Inches(1.5)])


def add_validation_word_table(doc, results):
    """Add validation checks as a styled Word table."""
    validation = results.get("validation", {})
    if not validation:
        return
    headers = ["Check", "Result"]
    data_rows = []
    for check, outcome in validation.items():
        label = check.replace("_", " ").title()
        if isinstance(outcome, bool):
            status = "PASS" if outcome else "FAIL"
        elif isinstance(outcome, (int, float)):
            status = "{:.4g}".format(outcome)
        else:
            status = str(outcome)
        data_rows.append([label, status])
    table = add_word_table(doc, headers, data_rows,
                           col_widths=[Inches(4.0), Inches(2.0)])
    # Color-code PASS/FAIL cells
    for row in table.rows[1:]:
        cell = row.cells[1]
        text = cell.text.strip()
        for paragraph in cell.paragraphs:
            for run in paragraph.runs:
                run.font.bold = True
                if text == "PASS":
                    run.font.color.rgb = RGBColor(0x28, 0xA7, 0x45)
                elif text == "FAIL":
                    run.font.color.rgb = RGBColor(0xDC, 0x35, 0x45)


def add_custom_word_tables(doc, results):
    """Add custom tables from results.json 'tables' key."""
    tables = results.get("tables", [])
    if not tables:
        return
    for tbl in tables:
        title = tbl.get("title", "")
        headers = tbl.get("headers", [])
        data_rows = tbl.get("rows", [])
        if not headers or not data_rows:
            continue
        if title:
            _add_heading(doc, title, level=2)
        # Format numeric values
        formatted_rows = []
        for row in data_rows:
            formatted = []
            for cell in row:
                if isinstance(cell, float):
                    formatted.append("{:.4g}".format(cell))
                else:
                    formatted.append(str(cell))
            formatted_rows.append(formatted)
        add_word_table(doc, headers, formatted_rows)


def format_discussion_html(results):
    """Format figure_discussion from results.json as styled HTML discussion blocks.

    Each discussion entry links a figure to its observation, physical mechanism,
    engineering implication, and recommendation — creating traceability from
    calculation to conclusion.
    """
    discussions = results.get("figure_discussion", [])
    if not discussions:
        return ""
    h = ""
    for i, disc in enumerate(discussions, 1):
        fig_file = disc.get("figure", "")
        title = disc.get("title", fig_file.replace("_", " ").replace(".png", "").title())
        observation = disc.get("observation", "")
        mechanism = disc.get("mechanism", "")
        implication = disc.get("implication", "")
        recommendation = disc.get("recommendation", "")
        linked = disc.get("linked_results", [])
        insight_ref = disc.get("insight_question_ref", "")

        h += '<div class="discussion-block">\n'
        h += '<h3>Discussion {}: {}</h3>\n'.format(i, title)
        if observation:
            h += '<p><strong>Observation:</strong> {}</p>\n'.format(observation)
        if mechanism:
            h += '<p><strong>Physical Mechanism:</strong> {}</p>\n'.format(mechanism)
        if implication:
            h += '<p><strong>Engineering Implication:</strong> {}</p>\n'.format(implication)
        if recommendation:
            h += '<p class="recommendation"><strong>Recommendation:</strong> {}</p>\n'.format(
                recommendation)
        # Traceability footer
        trace_parts = []
        if linked:
            trace_parts.append("Linked results: {}".format(", ".join(linked)))
        if insight_ref:
            trace_parts.append("Answers: {}".format(insight_ref))
        if trace_parts:
            h += '<p class="traceability"><em>{}</em></p>\n'.format(" | ".join(trace_parts))
        h += '</div>\n'
    return h


def add_discussion_word(doc, results):
    """Add figure discussion entries as styled Word content.

    Each discussion block has: observation, physical mechanism, engineering
    implication, and recommendation with traceability references.
    """
    discussions = results.get("figure_discussion", [])
    if not discussions:
        return
    for i, disc in enumerate(discussions, 1):
        fig_file = disc.get("figure", "")
        title = disc.get("title", fig_file.replace("_", " ").replace(".png", "").title())
        observation = disc.get("observation", "")
        mechanism = disc.get("mechanism", "")
        implication = disc.get("implication", "")
        recommendation = disc.get("recommendation", "")
        linked = disc.get("linked_results", [])
        insight_ref = disc.get("insight_question_ref", "")

        _add_heading(doc, "Discussion {}: {}".format(i, title), level=2)

        if observation:
            p = doc.add_paragraph()
            r = p.add_run("Observation: ")
            r.bold = True
            r.font.size = Pt(BODY_PT)
            p.add_run(observation).font.size = Pt(BODY_PT)

        if mechanism:
            p = doc.add_paragraph()
            r = p.add_run("Physical Mechanism: ")
            r.bold = True
            r.font.size = Pt(BODY_PT)
            p.add_run(mechanism).font.size = Pt(BODY_PT)

        if implication:
            p = doc.add_paragraph()
            r = p.add_run("Engineering Implication: ")
            r.bold = True
            r.font.size = Pt(BODY_PT)
            p.add_run(implication).font.size = Pt(BODY_PT)

        if recommendation:
            p = doc.add_paragraph()
            r = p.add_run("Recommendation: ")
            r.bold = True
            r.font.size = Pt(BODY_PT)
            r2 = p.add_run(recommendation)
            r2.font.size = Pt(BODY_PT)
            r2.font.color.rgb = RGBColor(0x1A, 0x53, 0x7A)

        # Traceability line
        trace_parts = []
        if linked:
            trace_parts.append("Linked results: {}".format(", ".join(linked)))
        if insight_ref:
            trace_parts.append("Answers: {}".format(insight_ref))
        if trace_parts:
            p = doc.add_paragraph()
            r = p.add_run(" | ".join(trace_parts))
            r.font.size = Pt(CAPTION_PT)
            r.font.italic = True
            r.font.color.rgb = RGBColor(0x66, 0x66, 0x66)

        doc.add_paragraph("")  # spacing


# ══════════════════════════════════════════════════════════
# Build sections (auto-populated where possible)
# ══════════════════════════════════════════════════════════

def _renumber_sections(sections):
    """Renumber section headings 1..N so conditional sections cannot leave gaps.

    Sections are appended conditionally, so any counter bug shows up in the
    issued report as a skipped or repeated chapter number.
    """
    for index, section in enumerate(sections, 1):
        heading = str(section.get("heading", "")).strip()
        section["heading"] = "{}. {}".format(
            index, _MANUAL_HEADING_NUMBER.sub("", heading).strip())
    return sections


def build_sections(results, task_spec, study_config_warnings=None, study_config=None):
    """Build report sections, auto-populating from results.json and task_spec.md."""
    sections = []
    if study_config_warnings is None:
        study_config_warnings = []

    # 1. Executive Summary
    exec_summary = ""
    if "executive_summary" in AUTHORED_SECTIONS:
        exec_summary = MANUAL_SECTIONS["executive_summary"]
    if _is_placeholder_text(exec_summary) and results \
            and results.get("executive_summary"):
        exec_summary = str(results["executive_summary"])
    if _is_placeholder_text(exec_summary):
        exec_summary = auto_executive_summary(results, task_spec)
    if _is_placeholder_text(exec_summary):
        exec_summary = MANUAL_SECTIONS["executive_summary"]
    sections.append({
        "heading": "1. Executive Summary",
        "content": exec_summary,
    })

    # 2. Problem Description
    problem_description = MANUAL_SECTIONS["problem_description"] \
        if "problem_description" in AUTHORED_SECTIONS else ""
    if _is_placeholder_text(problem_description):
        problem_description = auto_problem_description(results, task_spec)
    if _is_placeholder_text(problem_description):
        problem_description = MANUAL_SECTIONS["problem_description"]
    sections.append({
        "heading": "2. Problem Description",
        "content": problem_description,
    })

    next_section_num = 3

    safety_readiness = format_safety_readiness_text(results) if results else ""
    if safety_readiness:
        sections.append({
            "heading": "{}. Safety Study Readiness".format(next_section_num),
            "content": safety_readiness,
        })
        next_section_num += 1

    # Scope and Standards (auto-populated from task_spec.md)
    scope_parts = []
    standards = extract_spec_section(task_spec, "Applicable Standards")
    if standards:
        scope_parts.append("Applicable Standards:\n" + standards)
    methods = extract_spec_section(task_spec, "Calculation Methods")
    if methods:
        scope_parts.append("Calculation Methods:\n" + methods)
    criteria = extract_spec_section(task_spec, "Acceptance Criteria")
    if criteria:
        scope_parts.append("Acceptance Criteria:\n" + criteria)
    envelope = extract_spec_section(task_spec, "Operating Envelope")
    if envelope:
        scope_parts.append("Operating Envelope:\n" + envelope)

    scope_content = "\n\n".join(scope_parts) if scope_parts else (
        "[Auto-populated from task_spec.md when filled in. "
        "Edit step1_scope_and_research/task_spec.md and re-run.]"
    )
    sections.append({
        "heading": "{}. Scope and Standards".format(next_section_num),
        "content": scope_content,
        "has_scope": True,
    })
    next_section_num += 1

    # Information Sources (auto-built from the collected documents themselves)
    information_sources = format_information_sources_text(study_config, results)
    if information_sources:
        sections.append({
            "heading": "{}. Information Sources and Evidence Basis".format(
                next_section_num),
            "content": information_sources,
            "has_markdown": True,
        })
        next_section_num += 1

    # Approach
    approach = MANUAL_SECTIONS["approach"]
    if results and results.get("approach") and "approach" not in AUTHORED_SECTIONS:
        approach = results["approach"]
    sections.append({
        "heading": "{}. Approach".format(next_section_num),
        "content": approach,
        "has_equations": True,
    })
    next_section_num += 1

    # Solution Workflow (how the task was solved — discovered agents + workflow)
    if results and results.get("agent_workflow_plan"):
        sections.append({
            "heading": "{}. Solution Workflow".format(next_section_num),
            "content": "",
            "has_workflow": True,
        })
        next_section_num += 1

    # Results (auto-populated from results.json)
    if results and results.get("key_results"):
        results_text = format_results_table(results)
    else:
        results_text = (
            "[Auto-populated from results.json when created by notebook. "
            "Save results with the pattern shown in the task README.]"
        )
    sections.append({
        "heading": "{}. Results".format(next_section_num),
        "content": results_text,
        "has_figures": True,
    })
    next_section_num += 1

    # Discussion (auto-populated from results.json figure_discussion)
    if results and results.get("figure_discussion"):
        sections.append({
            "heading": "{}. Discussion".format(next_section_num),
            "content": "",
            "has_discussion": True,
        })
        next_section_num += 1

    # Validation Summary (auto-populated from results.json)
    if results and results.get("validation"):
        validation_text = format_validation_table(results)
    else:
        validation_text = (
            "[Auto-populated from results.json validation section. "
            "Add validation checks to your notebook results output.]"
        )
    sections.append({
        "heading": "{}. Validation Summary".format(next_section_num),
        "content": validation_text,
    })
    next_section_num += 1

    if study_config_warnings:
        warning_lines = ["- {}".format(warning) for warning in study_config_warnings]
        sections.append({
            "heading": "{}. Study Configuration Warnings".format(next_section_num),
            "content": "\n".join(warning_lines),
        })
        next_section_num += 1

    if results and results.get("benchmark_validation"):
        sections.append({
            "heading": "{}. Benchmark Validation".format(next_section_num),
            "content": "",
            "has_benchmark": True,
        })
        next_section_num += 1

    # N. Uncertainty Analysis (if data available)
    if results and results.get("uncertainty"):
        sections.append({
            "heading": "{}. Uncertainty Analysis".format(next_section_num),
            "content": "",
            "has_uncertainty": True,
        })
        next_section_num += 1

    # N. Risk Assessment (if data available)
    if results and results.get("risk_evaluation"):
        sections.append({
            "heading": "{}. Risk Assessment".format(next_section_num),
            "content": "",
            "has_risk": True,
        })
        next_section_num += 1

    assumptions_text = format_assumptions_text(results)
    if assumptions_text:
        sections.append({
            "heading": "{}. Assumptions and Data Gaps".format(next_section_num),
            "content": assumptions_text,
            "has_markdown": True,
        })
        next_section_num += 1
    elif results and (results.get("evidence_gaps") or results.get("assumptions_gaps")):
        sections.append({
            "heading": "{}. Evidence Gaps and Design-Grade Blockers".format(next_section_num),
            "content": format_list_items_text(
                results.get("evidence_gaps") or results.get("assumptions_gaps")),
        })
        next_section_num += 1

    if results and results.get("recommendations"):
        sections.append({
            "heading": "{}. Recommendations".format(next_section_num),
            "content": format_list_items_text(results.get("recommendations")),
        })
        next_section_num += 1

    improvements_text = format_improvements_text(results)
    if improvements_text:
        sections.append({
            "heading": "{}. Tooling Improvements Delivered".format(next_section_num),
            "content": improvements_text,
            "has_markdown": True,
        })
        next_section_num += 1

    # N. Conclusions and Recommendations
    conclusions = MANUAL_SECTIONS["conclusions"]
    if results and results.get("conclusions"):
        conclusions = results["conclusions"]
    sections.append({
        "heading": "{}. Conclusions and Recommendations".format(next_section_num),
        "content": conclusions,
    })
    next_section_num += 1

    # N. References (auto-populated from results.json if available)
    refs_content = MANUAL_SECTIONS["references"]
    if results and results.get("references"):
        ref_lines = []
        for i, ref in enumerate(results["references"], 1):
            ref_id = ref.get("id", "")
            ref_text = ref.get("text", "")
            if ref_id:
                ref_lines.append("[{}] {}".format(i, ref_text))
            else:
                ref_lines.append("[{}] {}".format(i, ref_text))
        refs_content = "\n".join(ref_lines)
    sections.append({
        "heading": "{}. References".format(next_section_num),
        "content": refs_content,
        "has_references": True,
    })

    return _renumber_sections(sections)


# ══════════════════════════════════════════════════════════
# Word report
# ══════════════════════════════════════════════════════════

def _auto_doc_number():
    """Generate a document number from the task folder name if DOC_NUMBER is blank."""
    if DOC_NUMBER:
        return DOC_NUMBER
    folder_name = os.path.basename(TASK_DIR)
    # Extract date prefix (YYYY-MM-DD) if present
    if len(folder_name) >= 10 and folder_name[4] == "-" and folder_name[7] == "-":
        date_part = folder_name[:10].replace("-", "")
        return "REP-{}".format(date_part)
    return "REP-DRAFT"


def _add_cover_page(doc):
    """Add a professional cover page with title, metadata, and revision history."""
    doc_num = _auto_doc_number()

    # Spacer
    for _ in range(4):
        doc.add_paragraph("")

    # Title
    title_para = doc.add_paragraph()
    title_para.alignment = WD_ALIGN_PARAGRAPH.CENTER
    run = title_para.add_run(TITLE)
    run.font.size = Pt(28)
    run.font.color.rgb = RGBColor(47, 84, 150)
    run.bold = True

    doc.add_paragraph("")

    # Subtitle line
    subtitle = doc.add_paragraph()
    subtitle.alignment = WD_ALIGN_PARAGRAPH.CENTER
    run = subtitle.add_run("NeqSim Engineering Report")
    run.font.size = Pt(14)
    run.font.color.rgb = RGBColor(100, 100, 100)

    if STUDY_BADGES:
        badges = doc.add_paragraph()
        badges.alignment = WD_ALIGN_PARAGRAPH.CENTER
        run = badges.add_run(
            "  |  ".join("{}: {}".format(label, value)
                         for label, value in STUDY_BADGES))
        run.font.size = Pt(9)
        run.font.color.rgb = RGBColor(130, 130, 130)

    if TASK_STATEMENT:
        doc.add_paragraph("")
        statement = doc.add_paragraph()
        statement.alignment = WD_ALIGN_PARAGRAPH.CENTER
        statement.paragraph_format.left_indent = Inches(0.8)
        statement.paragraph_format.right_indent = Inches(0.8)
        run = statement.add_run(TASK_STATEMENT)
        run.font.size = Pt(11)
        run.font.italic = True
        run.font.color.rgb = RGBColor(70, 70, 70)

    for _ in range(3):
        doc.add_paragraph("")

    # Metadata table
    meta_table = doc.add_table(rows=5, cols=2)
    _set_table_style(meta_table)
    meta_table.alignment = WD_TABLE_ALIGNMENT.CENTER
    meta_data = [
        ("Document Number", doc_num),
        ("Revision", REVISION),
        ("Date", TASK_DATE),
        ("Author", AUTHOR or "(not specified)"),
        ("Classification", CLASSIFICATION),
    ]
    for i, (label, value) in enumerate(meta_data):
        meta_table.rows[i].cells[0].text = label
        meta_table.rows[i].cells[1].text = value
        for cell in meta_table.rows[i].cells:
            for paragraph in cell.paragraphs:
                paragraph.paragraph_format.space_after = Pt(2)
                paragraph.paragraph_format.space_before = Pt(2)
        # Bold the label column
        for run in meta_table.rows[i].cells[0].paragraphs[0].runs:
            run.bold = True

    doc.add_paragraph("")

    # Revision history table (if entries exist)
    rev_entries = REVISION_HISTORY or [
        {"rev": REVISION, "date": TASK_DATE,
         "description": "Initial issue", "author": AUTHOR or ""}
    ]
    rev_heading = doc.add_paragraph()
    rev_heading.alignment = WD_ALIGN_PARAGRAPH.LEFT
    run = rev_heading.add_run("Revision History")
    run.font.size = Pt(12)
    run.bold = True
    run.font.color.rgb = RGBColor(47, 84, 150)

    rev_table = doc.add_table(rows=1 + len(rev_entries), cols=4)
    _set_table_style(rev_table)
    rev_table.alignment = WD_TABLE_ALIGNMENT.CENTER
    headers = ["Rev", "Date", "Description", "Author"]
    for j, h in enumerate(headers):
        cell = rev_table.rows[0].cells[j]
        cell.text = h
        shading = parse_xml(
            '<w:shd {} w:fill="2F5496"/>'.format(nsdecls("w"))
        )
        cell.paragraphs[0].runs[0].font.color.rgb = RGBColor(255, 255, 255)
        cell.paragraphs[0].runs[0].bold = True
        cell._tc.get_or_add_tcPr().append(shading)
    for i, entry in enumerate(rev_entries, 1):
        rev_table.rows[i].cells[0].text = str(entry.get("rev", ""))
        rev_table.rows[i].cells[1].text = str(entry.get("date", ""))
        rev_table.rows[i].cells[2].text = str(entry.get("description", ""))
        rev_table.rows[i].cells[3].text = str(entry.get("author", ""))

    doc.add_page_break()


def _suppress_paragraph_numbering(paragraph):
    """Keep a heading out of the template's automatic heading numbering."""
    p_pr = paragraph._p.get_or_add_pPr()
    for existing in p_pr.findall(qn("w:numPr")):
        p_pr.remove(existing)
    p_pr.append(parse_xml(
        '<w:numPr {}><w:ilvl w:val="0"/><w:numId w:val="0"/></w:numPr>'.format(nsdecls("w"))
    ))


def _add_word_toc(doc):
    """Add a Table of Contents field to the Word document."""
    # Add TOC heading
    _add_heading(doc, "Table of Contents", level=1, numbered=False)
    # Insert a Word TOC field (updates when user presses F9 in Word)
    paragraph = doc.add_paragraph()
    run = paragraph.add_run()
    fldChar1 = parse_xml(
        '<w:fldChar {} w:fldCharType="begin"/>'.format(nsdecls("w"))
    )
    run._r.append(fldChar1)
    run2 = paragraph.add_run()
    instrText = parse_xml(
        '<w:instrText {} xml:space="preserve"> TOC \\o "1-2" \\h \\z \\u </w:instrText>'.format(
            nsdecls("w")
        )
    )
    run2._r.append(instrText)
    run3 = paragraph.add_run()
    fldChar2 = parse_xml(
        '<w:fldChar {} w:fldCharType="separate"/>'.format(nsdecls("w"))
    )
    run3._r.append(fldChar2)
    run4 = paragraph.add_run("(Right-click and select 'Update Field' to populate)")
    run4.font.color.rgb = RGBColor(128, 128, 128)
    run4.font.italic = True
    run5 = paragraph.add_run()
    fldChar3 = parse_xml(
        '<w:fldChar {} w:fldCharType="end"/>'.format(nsdecls("w"))
    )
    run5._r.append(fldChar3)
    # Tell Word to update all fields (incl. this TOC) when the document is opened
    _set_update_fields_on_open(doc)
    doc.add_page_break()


def _set_update_fields_on_open(doc):
    """Flag the document so Word refreshes all fields (TOC, page numbers) on open."""
    settings = doc.settings.element
    update = settings.find(qn("w:updateFields"))
    if update is None:
        update = parse_xml(
            '<w:updateFields {} w:val="true"/>'.format(nsdecls("w"))
        )
        settings.append(update)
    else:
        update.set(qn("w:val"), "true")


def _add_page_number_footer(doc):
    """Add "<title> | <doc no> | Page X of Y" to the footer of every section.

    Skipped when a corporate template is used, because the template owns its
    own headers and footers.
    """
    if REPORT_TEMPLATE:
        return
    label = "{} | {} Rev {} | Page ".format(TITLE, _auto_doc_number(), REVISION)
    for section in doc.sections:
        footer = section.footer
        paragraph = footer.paragraphs[0] if footer.paragraphs \
            else footer.add_paragraph()
        paragraph.text = ""
        paragraph.alignment = WD_ALIGN_PARAGRAPH.CENTER
        run = paragraph.add_run(label)
        run.font.size = Pt(8)
        run.font.color.rgb = RGBColor(120, 120, 120)
        _add_field(paragraph, "PAGE")
        run = paragraph.add_run(" of ")
        run.font.size = Pt(8)
        run.font.color.rgb = RGBColor(120, 120, 120)
        _add_field(paragraph, "NUMPAGES")


def _add_field(paragraph, instruction):
    """Append a Word field (e.g. PAGE, NUMPAGES) to a paragraph."""
    run = paragraph.add_run()
    run.font.size = Pt(8)
    run.font.color.rgb = RGBColor(120, 120, 120)
    run._r.append(parse_xml(
        '<w:fldChar {} w:fldCharType="begin"/>'.format(nsdecls("w"))))
    run._r.append(parse_xml(
        '<w:instrText {} xml:space="preserve"> {} </w:instrText>'.format(
            nsdecls("w"), instruction)))
    run._r.append(parse_xml(
        '<w:fldChar {} w:fldCharType="end"/>'.format(nsdecls("w"))))


def _add_task_statement_block(doc):
    """State the task at the very start of the report body."""
    if not TASK_STATEMENT:
        return
    heading = doc.add_paragraph()
    run = heading.add_run("Task")
    run.bold = True
    run.font.size = Pt(12)
    run.font.color.rgb = RGBColor(47, 84, 150)

    box = doc.add_table(rows=1, cols=1)
    box.alignment = WD_TABLE_ALIGNMENT.CENTER
    cell = box.rows[0].cells[0]
    cell._tc.get_or_add_tcPr().append(
        parse_xml('<w:shd {} w:fill="F3F6FB"/>'.format(nsdecls("w"))))
    cell.text = ""
    paragraph = cell.paragraphs[0]
    run = paragraph.add_run(TASK_STATEMENT)
    run.font.size = Pt(BODY_PT)
    if STUDY_BADGES:
        meta = cell.add_paragraph()
        run = meta.add_run("  |  ".join(
            "{}: {}".format(label, value) for label, value in STUDY_BADGES))
        run.font.size = Pt(CAPTION_PT)
        run.font.italic = True
        run.font.color.rgb = RGBColor(110, 110, 110)
    doc.add_paragraph("")


def build_word_report(sections, results=None):
    """Build the Word document with cover page, TOC, numbered figures, and equations."""
    doc = _new_document()

    # Cover page with metadata and revision history
    _add_cover_page(doc)

    # Table of Contents
    _add_word_toc(doc)

    # The task this report answers, stated before any analysis
    _add_task_statement_block(doc)

    # Add all sections
    for section in sections:
        _add_heading(doc, section["heading"], level=1)

        # Results section: use Word table instead of plain text
        if section.get("has_figures") and results and results.get("key_results"):
            add_results_word_table(doc, results)
            # Custom tables
            if results.get("tables"):
                add_custom_word_tables(doc, results)
        elif section.get("has_figures"):
            # No results data — show placeholder text
            for para_text in _body_paragraphs(section["content"]):
                doc.add_paragraph(para_text)
        elif section.get("has_scope") or section.get("has_markdown"):
            # Scope section: parse markdown tables, bold, and lists
            render_scope_to_word(doc, section["content"])
        elif "Validation" in section["heading"] and results and results.get("validation"):
            # Validation section: use Word table
            add_validation_word_table(doc, results)
        elif section.get("has_benchmark") and results:
            # Benchmark Validation section: styled table
            add_benchmark_word_table(doc, results)
        elif section.get("has_uncertainty") and results:
            # Uncertainty Analysis section: styled tables
            add_uncertainty_word_tables(doc, results)
        elif section.get("has_risk") and results:
            # Risk Assessment section: styled table with color-coded levels
            add_risk_word_table(doc, results)
        elif section.get("has_workflow") and results:
            # Solution Workflow section: agents + workflow composition
            add_workflow_word_section(doc, results)
        elif section.get("has_discussion") and results:
            # Discussion section: figure-by-figure interpretation
            add_discussion_word(doc, results)
        else:
            # Regular text content
            for para_text in _body_paragraphs(section["content"]):
                doc.add_paragraph(para_text)

        # Embed figures after Results section
        if section.get("has_figures"):
            figures = get_figures()
            if figures:
                for fig_idx, fig_path in enumerate(figures, 1):
                    caption_text = get_figure_caption(fig_path, results, fig_idx)
                    doc.add_picture(fig_path, width=Inches(6.0))
                    last_para = doc.paragraphs[-1]
                    last_para.alignment = WD_ALIGN_PARAGRAPH.CENTER
                    caption = doc.add_paragraph(caption_text)
                    caption.alignment = WD_ALIGN_PARAGRAPH.CENTER
                    caption.runs[0].font.size = Pt(CAPTION_PT)
                    caption.runs[0].font.italic = True
                    doc.add_paragraph("")
            else:
                doc.add_paragraph(
                    "[No figures found in figures/ directory. "
                    "Save plots as PNG files there and re-run this script.]"
                )

        # Embed equations after Approach section
        if section.get("has_equations"):
            equations = get_equations(results)
            if equations:
                _add_heading(doc, "Key Equations", level=2)
                eq_img_dir = os.path.join(REPORT_DIR, "_eq_images")
                if not os.path.exists(eq_img_dir):
                    os.makedirs(eq_img_dir)
                for eq_idx, eq in enumerate(equations, 1):
                    label = eq.get("label", "Equation {}".format(eq_idx))
                    latex = eq.get("latex", "")
                    if not latex:
                        continue
                    # Try to render equation as image
                    eq_img_path = os.path.join(eq_img_dir, "eq_{}.png".format(eq_idx))
                    if render_equation_to_image(latex, eq_img_path):
                        doc.add_paragraph("")
                        _add_equation_picture(doc, eq_img_path, 6.0)
                        last_para = doc.paragraphs[-1]
                        last_para.alignment = WD_ALIGN_PARAGRAPH.CENTER
                        caption = doc.add_paragraph(
                            "Equation {}: {}".format(eq_idx, label)
                        )
                        caption.alignment = WD_ALIGN_PARAGRAPH.CENTER
                        caption.runs[0].font.size = Pt(CAPTION_PT)
                        caption.runs[0].font.italic = True
                    else:
                        # Fallback: text representation
                        doc.add_paragraph("{}: {}".format(label, latex))
                    doc.add_paragraph("")

    # Save
    _add_page_number_footer(doc)
    doc.save(DOCX_FILE)
    print("Word report saved: {}".format(DOCX_FILE))


# ══════════════════════════════════════════════════════════
# ══════════════════════════════════════════════════════════
# HTML report
# ══════════════════════════════════════════════════════════

def _build_rev_rows_html():
    """Build HTML table rows for revision history in the HTML report."""
    rev_entries = REVISION_HISTORY or [
        {"rev": REVISION, "date": TASK_DATE,
         "description": "Initial issue", "author": AUTHOR or ""}
    ]
    rows = ""
    for entry in rev_entries:
        rows += "<tr><td>{}</td><td>{}</td><td>{}</td><td>{}</td></tr>\n".format(
            entry.get("rev", ""), entry.get("date", ""),
            entry.get("description", ""), entry.get("author", ""))
    return rows


def _html_escape(text):
    """Escape the characters that would break generated HTML."""
    return (str(text).replace("&", "&amp;").replace("<", "&lt;")
            .replace(">", "&gt;"))


def _build_badges_html():
    """Build the study-depth badge row shown under the report title."""
    if not STUDY_BADGES:
        return ""
    spans = "".join(
        "<span>{}: {}</span>".format(_html_escape(label), _html_escape(value))
        for label, value in STUDY_BADGES)
    return '<p class="study-badges">{}</p>'.format(spans)


def _build_task_block_html():
    """Build the task statement shown at the very start of the report body."""
    if not TASK_STATEMENT:
        return ""
    return (
        '<div class="task-statement">\n'
        '    <h2>Task</h2>\n'
        '    <p>{}</p>\n'
        '</div>'.format(_html_escape(TASK_STATEMENT))
    )


def build_html_report(sections, results=None):
    """Build an HTML report with embedded figures, KaTeX equations, and navigation."""
    figures = get_figures()

    # Build figure HTML with base64-embedded images and numbered captions
    figure_html = ""
    if figures:
        for fig_idx, fig_path in enumerate(figures, 1):
            fig_name = os.path.basename(fig_path)
            caption_text = get_figure_caption(fig_path, results, fig_idx)
            # Determine MIME type
            if fig_path.endswith(".svg"):
                mime = "image/svg+xml"
            else:
                mime = "image/png"
            with open(fig_path, "rb") as f:
                img_data = base64.b64encode(f.read()).decode("utf-8")
            figure_html += """
            <div class="figure">
                <img src="data:{};base64,{}" alt="{}">
                <p class="caption">{}</p>
            </div>
            """.format(mime, img_data, caption_text, caption_text)
    else:
        figure_html = "<p><em>No figures found in figures/ directory.</em></p>"

    # Build equation HTML (KaTeX rendering with embedded image fallbacks)
    equation_html = ""
    equations = get_equations(results)
    if equations:
        equation_html += '<h3>Key Equations</h3>\n'
        # Pre-render equation images for offline fallback
        eq_img_dir = os.path.join(REPORT_DIR, "_eq_images")
        if not os.path.exists(eq_img_dir):
            os.makedirs(eq_img_dir)
        for eq_idx, eq in enumerate(equations, 1):
            label = eq.get("label", "Equation {}".format(eq_idx))
            latex = eq.get("latex", "")
            if not latex:
                continue
            # Render fallback image
            fallback_img = ""
            eq_img_path = os.path.join(eq_img_dir, "eq_{}.png".format(eq_idx))
            if render_equation_to_image(latex, eq_img_path):
                with open(eq_img_path, "rb") as imgf:
                    img_b64 = base64.b64encode(imgf.read()).decode("utf-8")
                fallback_img = (
                    '<img class="eq-fallback" '
                    'src="data:image/png;base64,{}" '
                    'alt="{}" style="display:none; max-width:90%;">'.format(
                        img_b64, label)
                )
            equation_html += """
            <div class="equation-block">
                <div class="equation katex-eq">$${}$$</div>
                {}
                <p class="equation-label">Equation {}: {}</p>
            </div>
            """.format(latex, fallback_img, eq_idx, label)

    # Build validation HTML
    validation_html = ""
    if results and results.get("validation"):
        validation_html = format_validation_html(results)

    # Build key results HTML table
    results_table_html = ""
    if results and results.get("key_results"):
        results_table_html = format_results_html(results)

    # Build custom tables HTML
    custom_tables_html = ""
    if results and results.get("tables"):
        custom_tables_html = format_custom_tables_html(results)

    # Build section HTML and navigation
    nav_items = ""
    section_html = ""
    for section in sections:
        section_id = section["heading"].lower().replace(" ", "-").replace(".", "")
        nav_items += '    <li><a href="#{}">{}</a></li>\n'.format(
            section_id, section["heading"]
        )
        # Convert scope section markdown to HTML
        if section.get("has_scope") or section.get("has_markdown"):
            content = scope_content_to_html(section["content"])
        else:
            content = _prose_to_html(section["content"])

        # Insert auto-generated HTML for special sections
        if section.get("has_figures"):
            if results_table_html:
                content = results_table_html + custom_tables_html + figure_html
            else:
                content += figure_html

        if section.get("has_equations") and equation_html:
            content += equation_html

        if "Validation" in section["heading"] and validation_html:
            content = validation_html

        if section.get("has_benchmark") and results:
            content = format_benchmark_html(results)

        if section.get("has_uncertainty") and results:
            content = format_uncertainty_html(results)

        if section.get("has_risk") and results:
            content = format_risk_html(results)

        if section.get("has_workflow") and results:
            content = format_workflow_html(results)

        if section.get("has_discussion") and results:
            content = format_discussion_html(results)

        if section.get("has_references") and results and results.get("references"):
            content = format_references_html(results)

        section_html += """
        <section id="{}">
            <h2>{}</h2>
            <div>{}</div>
        </section>
        """.format(section_id, section["heading"], content)

    # KaTeX CDN for equation rendering (only if equations exist)
    katex_head = ""
    katex_body_script = ""
    if equations:
        katex_head = """
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/katex@0.16.11/dist/katex.min.css">
    <script defer src="https://cdn.jsdelivr.net/npm/katex@0.16.11/dist/katex.min.js"></script>
    <script defer src="https://cdn.jsdelivr.net/npm/katex@0.16.11/dist/contrib/auto-render.min.js"></script>"""
        katex_body_script = """
    <script>
        document.addEventListener("DOMContentLoaded", function() {
            if (typeof renderMathInElement === "function") {
                renderMathInElement(document.body, {
                    delimiters: [
                        {left: "$$", right: "$$", display: true},
                        {left: "$", right: "$", display: false}
                    ],
                    throwOnError: false
                });
            } else {
                // KaTeX not available (offline) — show fallback images
                var eqs = document.querySelectorAll(".katex-eq");
                for (var i = 0; i < eqs.length; i++) {
                    eqs[i].style.display = "none";
                }
                var imgs = document.querySelectorAll(".eq-fallback");
                for (var j = 0; j < imgs.length; j++) {
                    imgs[j].style.display = "inline";
                }
            }
        });
    </script>"""

    html = """<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>{title}</title>{katex_head}
    <style>
        * {{ margin: 0; padding: 0; box-sizing: border-box; }}
        body {{ font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
               display: flex; line-height: 1.6; color: #333; }}
        nav {{ width: 260px; min-height: 100vh; background: #f5f5f5; padding: 1.5rem;
              position: fixed; overflow-y: auto; border-right: 1px solid #ddd; }}
        nav h3 {{ margin-bottom: 1rem; color: #555; font-size: 0.9rem;
                  text-transform: uppercase; letter-spacing: 0.05em; }}
        nav ul {{ list-style: none; }}
        nav li {{ margin-bottom: 0.5rem; }}
        nav a {{ color: #0366d6; text-decoration: none; font-size: 0.9rem; }}
        nav a:hover {{ text-decoration: underline; }}
        main {{ margin-left: 260px; max-width: 900px; padding: 2rem 3rem; }}
        h1 {{ margin-bottom: 0.5rem; color: #1a1a1a; }}
        h2 {{ margin-top: 2rem; margin-bottom: 1rem; color: #1a1a1a;
             border-bottom: 1px solid #eee; padding-bottom: 0.3rem; }}
        h3 {{ margin-top: 1.5rem; margin-bottom: 0.5rem; color: #333; }}
        .meta {{ color: #666; margin-bottom: 2rem; }}
        .cover-page {{ text-align: center; padding: 3rem 0; margin-bottom: 2rem;
                       border-bottom: 3px solid #2F5496; }}
        .cover-page h1 {{ font-size: 2.2rem; color: #2F5496; margin-bottom: 0.5rem;
                          line-height: 1.25; }}
        .cover-page .subtitle {{ font-size: 1.1rem; color: #888; margin-bottom: 0.8rem; }}
        .study-badges {{ margin-bottom: 1.5rem; }}
        .study-badges span {{ display: inline-block; margin: 0 0.25rem 0.35rem 0;
                       padding: 0.15rem 0.6rem; font-size: 0.78rem; color: #2F5496;
                       background: #eef2fa; border: 1px solid #d4ddef;
                       border-radius: 999px; }}
        .task-statement {{ border-left: 4px solid #2F5496; background: #f3f6fb;
                       padding: 1rem 1.2rem; margin: 0 0 2rem 0;
                       border-radius: 0 4px 4px 0; }}
        .task-statement h2 {{ margin: 0 0 0.4rem 0; border: none; padding: 0;
                       font-size: 1.05rem; color: #2F5496;
                       text-transform: uppercase; letter-spacing: 0.06em; }}
        .task-statement p {{ margin: 0; }}
        .cover-meta {{ display: inline-block; text-align: left; margin: 1rem auto;
                       background: #f8f9fa; padding: 1rem 2rem; border-radius: 6px;
                       border: 1px solid #e0e0e0; }}
        .cover-meta td {{ padding: 0.2rem 0.8rem; }}
        .cover-meta td:first-child {{ font-weight: bold; color: #555; }}
        .rev-table {{ margin: 1rem auto; font-size: 0.9rem; max-width: 700px; }}
        .rev-table th {{ background: #2F5496; color: #fff; padding: 0.4rem 0.8rem; }}
        .rev-table td {{ padding: 0.3rem 0.8rem; border: 1px solid #e0e0e0; }}
        section {{ margin-bottom: 2rem; }}
        .figure {{ text-align: center; margin: 1.5rem 0; }}
        .figure img {{ max-width: 100%; border: 1px solid #ddd; border-radius: 4px; }}
        .caption {{ font-size: 0.85rem; color: #666; font-style: italic;
                    margin-top: 0.3rem; }}
        .equation-block {{ margin: 1.5rem 0; text-align: center; }}
        .equation {{ font-size: 1.2rem; padding: 0.5rem 0; }}
        .equation-label {{ font-size: 0.85rem; color: #666; font-style: italic;
                           margin-top: 0.2rem; }}
        table {{ border-collapse: collapse; width: 100%; margin: 1.5rem 0;
                font-size: 0.92rem; box-shadow: 0 1px 3px rgba(0,0,0,0.08); }}
        thead th {{ background: #2F5496; color: #fff; font-weight: 600;
                    padding: 0.6rem 0.75rem; text-align: left;
                    border: 1px solid #2a4a85; }}
        tbody td {{ border: 1px solid #e0e0e0; padding: 0.5rem 0.75rem;
                    text-align: left; }}
        tbody tr:nth-child(even) {{ background: #f8f9fa; }}
        tbody tr:hover {{ background: #e9ecef; }}
        td.num {{ text-align: right; font-variant-numeric: tabular-nums; }}
        .pass {{ color: #28a745; font-weight: bold; }}
        .fail {{ color: #dc3545; font-weight: bold; }}
        .results-table {{ max-width: 600px; }}
        .validation-table {{ max-width: 500px; }}
        .custom-table {{ margin-top: 0.5rem; }}
        .scope-table {{ margin: 0.5rem 0 1rem 0; }}
        section h3 {{ color: #2F5496; margin-top: 1.2rem; margin-bottom: 0.4rem;
            font-size: 1.1rem; border-bottom: 1px solid #ddd; padding-bottom: 0.2rem; }}
        section ul {{ margin: 0.3rem 0 0.8rem 1.5rem; }}
        section ul li {{ margin-bottom: 0.3rem; }}
        .reference-list {{ list-style: none; padding-left: 0; }}
        .reference-list li {{ margin-bottom: 0.6rem; padding: 0.4rem 0.6rem;
            border-left: 3px solid #2F5496; background: #f8f9fa; }}
        .reference-list li strong {{ color: #2F5496; }}
        /* Risk assessment styles */
        .risk-summary-card {{ background: #f8f9fa; border-left: 4px solid #2F5496;
            padding: 0.8rem 1.2rem; margin-bottom: 1.2rem; border-radius: 0 4px 4px 0; }}
        .risk-badge {{ display: inline-block; padding: 0.15rem 0.6rem; border-radius: 3px;
            font-weight: 600; font-size: 0.85rem; color: #fff; }}
        .risk-badge.risk-high {{ background: #dc3545; }}
        .risk-badge.risk-medium {{ background: #e67e22; }}
        .risk-badge.risk-low {{ background: #28a745; }}
        .risk-table {{ font-size: 0.88rem; }}
        .risk-table .mitigation-cell {{ font-size: 0.85rem; color: #555; }}
        .risk-level {{ font-weight: bold; text-align: center; }}
        .risk-level.risk-high {{ color: #dc3545; }}
        .risk-level.risk-medium {{ color: #e67e22; }}
        .risk-level.risk-low {{ color: #28a745; }}
        /* Uncertainty analysis styles */
        .uncertainty-summary {{ background: #e8f4fd; border-left: 4px solid #0366d6;
            padding: 0.8rem 1.2rem; margin-bottom: 1.2rem; border-radius: 0 4px 4px 0; }}
        .uncertainty-table {{ font-size: 0.88rem; }}
        .tornado-table {{ font-size: 0.88rem; }}
        /* Benchmark validation styles */
        .benchmark-table {{ font-size: 0.88rem; }}
        /* Discussion section styles */
        .discussion-block {{ background: #f9fafb; border-left: 4px solid #2d6a4f;
            padding: 1rem 1.2rem; margin-bottom: 1.5rem; border-radius: 0 4px 4px 0; }}
        .discussion-block h3 {{ color: #2d6a4f; margin-top: 0; }}
        .discussion-block .recommendation {{ background: #e8f5e9; padding: 0.5rem 0.8rem;
            border-radius: 3px; border-left: 3px solid #28a745; }}
        .discussion-block .traceability {{ font-size: 0.8rem; color: #888;
            border-top: 1px dashed #ccc; padding-top: 0.4rem; margin-top: 0.6rem; }}
        @media (max-width: 768px) {{
            nav {{ position: static; width: 100%; min-height: auto; }}
            main {{ margin-left: 0; padding: 1rem; }}
        }}
        @media print {{
            nav {{ display: none; }}
            main {{ margin-left: 0; max-width: 100%; padding: 0; }}
            body {{ display: block; font-size: 10.5pt; color: #000; }}
            .cover-page {{ page-break-after: always; }}
            section {{ page-break-inside: avoid; }}
            .figure, table, .discussion-block {{ page-break-inside: avoid; }}
            h2 {{ page-break-after: avoid; }}
            a {{ color: #000; text-decoration: none; }}
        }}
    </style>
</head>
<body>
    <nav>
        <h3>Contents</h3>
        <ul>
{nav}
        </ul>
        <hr style="margin: 1rem 0;">
        <p style="font-size: 0.8rem; color: #999;">{doc_num}</p>
        <p style="font-size: 0.8rem; color: #999;">Rev {rev} | {date}</p>
    </nav>
    <main>
        <div class="cover-page">
            <h1>{title}</h1>
            <p class="subtitle">NeqSim Engineering Report</p>
            {badges}
            <table class="cover-meta">
                <tr><td>Document No.</td><td>{doc_num}</td></tr>
                <tr><td>Revision</td><td>{rev}</td></tr>
                <tr><td>Date</td><td>{date}</td></tr>
                <tr><td>Author</td><td>{author}</td></tr>
                <tr><td>Classification</td><td>{classification}</td></tr>
            </table>
            <h3 style="margin-top: 2rem; color: #2F5496;">Revision History</h3>
            <table class="rev-table">
                <thead><tr><th>Rev</th><th>Date</th><th>Description</th><th>Author</th></tr></thead>
                <tbody>{rev_rows}</tbody>
            </table>
        </div>
{task_block}
{sections}
    </main>{katex_body_script}
</body>
</html>""".format(
        title=TITLE,
        badges=_build_badges_html(),
        task_block=_build_task_block_html(),
        author=AUTHOR or "(not specified)",
        date=TASK_DATE,
        doc_num=_auto_doc_number(),
        rev=REVISION,
        classification=CLASSIFICATION,
        rev_rows=_build_rev_rows_html(),
        nav=nav_items,
        sections=section_html,
        katex_head=katex_head,
        katex_body_script=katex_body_script,
    )

    with open(HTML_FILE, "w", encoding="utf-8") as f:
        f.write(html)
    print("HTML report saved: {}".format(HTML_FILE))


# ══════════════════════════════════════════════════════════
# Scientific Paper Generation
# ══════════════════════════════════════════════════════════

def build_paper_sections(results, task_spec):
    """Build scientific paper sections from results.json and task_spec.md.

    Maps task data to a standard engineering paper structure:
    Abstract, Introduction, Methodology, Results & Discussion,
    Uncertainty Analysis, Conclusions, Acknowledgments, References.
    """
    sections = []
    paper_title = PAPER_TITLE or TITLE

    # Abstract
    abstract = PAPER_SECTIONS["abstract"]
    if results and results.get("approach") and results.get("conclusions"):
        # Auto-generate abstract from approach + key results + conclusions
        parts = []
        parts.append(results["approach"])
        kr = results.get("key_results", {})
        if kr:
            highlights = []
            for key, value in list(kr.items())[:5]:
                label, unit = _parse_key_name(key)
                if isinstance(value, float):
                    highlights.append("{}: {:.4g} {}".format(label, value, unit).strip())
                else:
                    highlights.append("{}: {} {}".format(label, value, unit).strip())
            parts.append("Key results: " + "; ".join(highlights) + ".")
        parts.append(results["conclusions"])
        abstract = " ".join(parts)
    sections.append({
        "type": "abstract",
        "heading": "Abstract",
        "content": abstract,
    })

    # 1. Introduction
    intro = PAPER_SECTIONS["introduction"]
    # Try to auto-populate from problem_description + task_spec background
    if MANUAL_SECTIONS.get("problem_description") and not MANUAL_SECTIONS[
            "problem_description"].startswith("["):
        intro = MANUAL_SECTIONS["problem_description"]
        background = extract_spec_section(task_spec, "Background")
        if background:
            intro = background + "\n\n" + intro
    sections.append({
        "type": "numbered",
        "number": 1,
        "heading": "Introduction",
        "content": intro,
    })

    # 2. Methodology
    methodology = PAPER_SECTIONS["methodology"]
    if results and results.get("approach"):
        methodology = results["approach"]
    # Append standards info from task_spec
    standards = extract_spec_section(task_spec, "Applicable Standards")
    methods = extract_spec_section(task_spec, "Calculation Methods")
    if standards:
        methodology += "\n\nApplicable Standards:\n" + standards
    if methods:
        methodology += "\n\nCalculation Methods:\n" + methods
    sections.append({
        "type": "numbered",
        "number": 2,
        "heading": "Methodology",
        "content": methodology,
        "has_equations": True,
    })

    # 3. Results and Discussion
    results_text = PAPER_SECTIONS["results_discussion"]
    if results and results.get("key_results"):
        results_text = format_results_table(results)
    sections.append({
        "type": "numbered",
        "number": 3,
        "heading": "Results and Discussion",
        "content": results_text,
        "has_figures": True,
        "has_discussion": bool(results and results.get("figure_discussion")),
    })

    # 3.1 Validation (sub-section if data available)
    if results and results.get("validation"):
        sections.append({
            "type": "numbered",
            "number": 3.1,
            "heading": "Validation",
            "content": format_validation_table(results),
            "is_subsection": True,
        })

    # 3.2 Benchmark comparison (sub-section if data available)
    if results and results.get("benchmark_validation"):
        bv = results["benchmark_validation"]
        bv_lines = []
        for key, val in bv.items():
            if not isinstance(val, dict):
                continue
            desc = val.get("description") or val.get("reference") or key
            status = val.get("status")
            if status is None:
                p = val.get("pass")
                status = "PASS" if p is True else ("FAIL" if p is False else "N/A")
            bv_lines.append("- {}: {}".format(desc, status))
            if "max_deviation_pct" in val:
                bv_lines.append("  Max deviation: {:.4f}%".format(
                    val["max_deviation_pct"]))
            if "deviation_pct" in val:
                bv_lines.append("  Deviation: {:.2f}%".format(
                    val["deviation_pct"]))
        sections.append({
            "type": "numbered",
            "number": 3.2,
            "heading": "Benchmark Comparison",
            "content": "\n".join(bv_lines),
            "is_subsection": True,
        })

    # 4. Uncertainty Analysis (if data available)
    if results and results.get("uncertainty"):
        unc = results["uncertainty"]
        unc_lines = []
        unc_lines.append("A {} was performed with {} simulations using {}.".format(
            unc.get("method", "Monte Carlo analysis"),
            unc.get("n_simulations", "N/A"),
            unc.get("simulation_engine", "NeqSim")))
        unc_lines.append("")
        unc_lines.append("Input parameters and ranges:")
        for param in unc.get("input_parameters", []):
            unc_lines.append("  - {} [{}]: {}-{} ({}, base={})".format(
                param["name"], param.get("unit", ""),
                param["low"], param["high"],
                param.get("distribution", "uniform"), param["base"]))
        unc_lines.append("")
        # Output P10/P50/P90
        out_param = unc.get("output_parameter", "")
        if out_param:
            unc_lines.append("Results for {}:".format(out_param))
            unc_lines.append("  P10: {}, P50: {}, P90: {}".format(
                unc.get("p10", "N/A"), unc.get("p50", "N/A"),
                unc.get("p90", "N/A")))
        # Also handle output_parameters dict (mercury-style)
        for out_key, out_val in unc.get("output_parameters", {}).items():
            unc_lines.append("Results for {}:".format(out_key))
            unc_lines.append("  P10: {}, P50: {}, P90: {}".format(
                out_val.get("p10", "N/A"), out_val.get("p50", "N/A"),
                out_val.get("p90", "N/A")))
        sections.append({
            "type": "numbered",
            "number": 4,
            "heading": "Uncertainty Analysis",
            "content": "\n".join(unc_lines),
            "has_uncertainty": True,
        })

    # 5. Risk Assessment (if data available)
    if results and results.get("risk_evaluation"):
        re_data = results["risk_evaluation"]
        re_lines = []
        re_lines.append("Risk assessment using {} framework.".format(
            re_data.get("risk_matrix_used", "5x5 (ISO 31000)")))
        re_lines.append("Overall risk level: {}.".format(
            re_data.get("overall_risk_level", "N/A")))
        re_lines.append("")
        for risk in re_data.get("risks", []):
            re_lines.append("- {} ({}): {} [{}]".format(
                risk["id"], risk["category"], risk["description"],
                risk["risk_level"]))
            re_lines.append("  Mitigation: {}".format(risk["mitigation"]))
        next_num = 5
        sections.append({
            "type": "numbered",
            "number": next_num,
            "heading": "Risk Assessment",
            "content": "\n".join(re_lines),
            "has_risk": True,
        })

    # N. Conclusions
    conclusions = PAPER_SECTIONS["conclusions"]
    if results and results.get("conclusions"):
        conclusions = results["conclusions"]
    elif not MANUAL_SECTIONS["conclusions"].startswith("["):
        conclusions = MANUAL_SECTIONS["conclusions"]
    # Determine next section number
    last_num = max((s.get("number", 0) for s in sections
                    if isinstance(s.get("number"), int)), default=3)
    sections.append({
        "type": "numbered",
        "number": last_num + 1,
        "heading": "Conclusions",
        "content": conclusions,
    })

    # Acknowledgments (unnumbered)
    ack = PAPER_SECTIONS["acknowledgments"] or PAPER_ACKNOWLEDGMENTS
    if ack:
        sections.append({
            "type": "unnumbered",
            "heading": "Acknowledgments",
            "content": ack,
        })

    # References (unnumbered)
    refs_content = ""
    if results and results.get("references"):
        ref_lines = []
        for i, ref in enumerate(results["references"], 1):
            ref_text = ref.get("text", "")
            ref_lines.append("[{}] {}".format(i, ref_text))
        refs_content = "\n".join(ref_lines)
    elif not MANUAL_SECTIONS["references"].startswith("["):
        refs_content = MANUAL_SECTIONS["references"]
    if refs_content:
        sections.append({
            "type": "references",
            "heading": "References",
            "content": refs_content,
            "has_references": True,
        })

    return sections


def _paper_section_heading(section):
    """Format a section heading with number for the paper."""
    stype = section.get("type", "numbered")
    if stype == "abstract":
        return "Abstract"
    elif stype in ("unnumbered", "references"):
        return section["heading"]
    elif section.get("is_subsection"):
        return "{} {}".format(section["number"], section["heading"])
    else:
        return "{}. {}".format(section["number"], section["heading"])


def build_paper_docx(sections, results=None):
    """Build a scientific paper in Word format.

    Uses standard academic formatting: Times New Roman, single-column,
    numbered sections, centered title/author block, italic abstract,
    numbered figures and equations.

    The corporate report template is deliberately not applied here: a journal
    manuscript follows the journal's format, not company branding.
    """
    doc = Document()

    # ── Page setup ──
    for doc_section in doc.sections:
        doc_section.top_margin = Inches(1.0)
        doc_section.bottom_margin = Inches(1.0)
        doc_section.left_margin = Inches(1.0)
        doc_section.right_margin = Inches(1.0)

    paper_title = PAPER_TITLE or TITLE

    # ── Title (centered, bold, 16pt) ──
    title_para = doc.add_paragraph()
    title_para.alignment = WD_ALIGN_PARAGRAPH.CENTER
    title_run = title_para.add_run(paper_title)
    title_run.bold = True
    title_run.font.size = Pt(16)
    title_run.font.name = "Times New Roman"
    doc.add_paragraph("")

    # ── Authors and affiliations (centered) ──
    authors = PAPER_AUTHORS
    if authors:
        author_para = doc.add_paragraph()
        author_para.alignment = WD_ALIGN_PARAGRAPH.CENTER
        names = []
        affiliations = []
        seen_aff = {}
        for i, a in enumerate(authors):
            name = a.get("name", "")
            aff = a.get("affiliation", "")
            if aff and aff not in seen_aff:
                seen_aff[aff] = len(seen_aff) + 1
                affiliations.append(aff)
            sup = str(seen_aff.get(aff, "")) if aff else ""
            names.append(name + ("" if not sup else sup))
        author_run = author_para.add_run(", ".join(names))
        author_run.font.size = Pt(12)
        author_run.font.name = "Times New Roman"
        if affiliations:
            aff_para = doc.add_paragraph()
            aff_para.alignment = WD_ALIGN_PARAGRAPH.CENTER
            for idx, aff in enumerate(affiliations, 1):
                aff_run = aff_para.add_run("{}{}".format(idx, aff))
                aff_run.font.size = Pt(10)
                aff_run.font.name = "Times New Roman"
                aff_run.font.italic = True
                if idx < len(affiliations):
                    aff_para.add_run("; ")
    elif AUTHOR:
        author_para = doc.add_paragraph()
        author_para.alignment = WD_ALIGN_PARAGRAPH.CENTER
        author_run = author_para.add_run(AUTHOR)
        author_run.font.size = Pt(12)
        author_run.font.name = "Times New Roman"

    # ── Date (centered) ──
    date_para = doc.add_paragraph()
    date_para.alignment = WD_ALIGN_PARAGRAPH.CENTER
    date_run = date_para.add_run(TASK_DATE)
    date_run.font.size = Pt(10)
    date_run.font.name = "Times New Roman"
    date_run.font.italic = True

    doc.add_paragraph("")  # spacing

    # ── Keywords (if provided) ──
    if PAPER_KEYWORDS:
        kw_para = doc.add_paragraph()
        kw_bold = kw_para.add_run("Keywords: ")
        kw_bold.bold = True
        kw_bold.font.size = Pt(10)
        kw_bold.font.name = "Times New Roman"
        kw_text = kw_para.add_run(", ".join(PAPER_KEYWORDS))
        kw_text.font.size = Pt(10)
        kw_text.font.name = "Times New Roman"
        kw_text.font.italic = True
        doc.add_paragraph("")

    # Track figure and equation counters for the whole paper
    fig_counter = [0]
    eq_counter = [0]

    # ── Sections ──
    for section in sections:
        heading_text = _paper_section_heading(section)
        is_sub = section.get("is_subsection", False)
        stype = section.get("type", "numbered")

        # Heading level
        if stype == "abstract":
            h = _add_heading(doc, heading_text, level=1, numbered=False)
        elif is_sub:
            h = _add_heading(doc, heading_text, level=2)
        else:
            h = _add_heading(doc, heading_text, level=1)

        # Style heading runs as Times New Roman
        for run in h.runs:
            run.font.name = "Times New Roman"

        # Abstract is italic
        if stype == "abstract":
            for para_text in _body_paragraphs(section["content"]):
                p = doc.add_paragraph()
                r = p.add_run(para_text)
                r.font.italic = True
                r.font.size = Pt(10)
                r.font.name = "Times New Roman"
        elif stype == "references" and results and results.get("references"):
            # Numbered reference list
            for i, ref in enumerate(results["references"], 1):
                ref_text = ref.get("text", "")
                p = doc.add_paragraph()
                p.paragraph_format.left_indent = Inches(0.3)
                p.paragraph_format.first_line_indent = Inches(-0.3)
                bracket_run = p.add_run("[{}] ".format(i))
                bracket_run.bold = True
                bracket_run.font.size = Pt(10)
                bracket_run.font.name = "Times New Roman"
                text_run = p.add_run(ref_text)
                text_run.font.size = Pt(10)
                text_run.font.name = "Times New Roman"
        elif section.get("has_figures") and results and results.get("key_results"):
            # Results section: add results table
            add_results_word_table(doc, results)
            if results.get("tables"):
                add_custom_word_tables(doc, results)
            # Discussion text
            disc = section["content"]
            if disc and not disc.startswith("["):
                for para_text in _body_paragraphs(disc):
                    doc.add_paragraph(para_text)
        elif "Validation" in section["heading"] and results and results.get("validation"):
            add_validation_word_table(doc, results)
        elif section.get("has_benchmark") and results:
            add_benchmark_word_table(doc, results)
        elif section.get("has_uncertainty") and results:
            add_uncertainty_word_tables(doc, results)
        elif section.get("has_risk") and results:
            add_risk_word_table(doc, results)
        elif section.get("has_scope", False) or section.get("has_markdown", False):
            render_scope_to_word(doc, section["content"])
        else:
            # Regular text content
            for para_text in _body_paragraphs(section["content"]):
                p = doc.add_paragraph()
                _add_bold_runs(p, para_text)
                for run in p.runs:
                    run.font.name = "Times New Roman"
                    if not run.font.size:
                        run.font.size = Pt(11)

        # Embed equations after Methodology section
        if section.get("has_equations"):
            equations = get_equations(results)
            if equations:
                eq_img_dir = os.path.join(REPORT_DIR, "_eq_images")
                if not os.path.exists(eq_img_dir):
                    os.makedirs(eq_img_dir)
                for eq in equations:
                    eq_counter[0] += 1
                    label = eq.get("label", "")
                    latex = eq.get("latex", "")
                    if not latex:
                        continue
                    eq_img_path = os.path.join(
                        eq_img_dir, "eq_{}.png".format(eq_counter[0]))
                    if render_equation_to_image(latex, eq_img_path):
                        doc.add_paragraph("")
                        _add_equation_picture(doc, eq_img_path, 5.0)
                        last_para = doc.paragraphs[-1]
                        last_para.alignment = WD_ALIGN_PARAGRAPH.CENTER
                        cap = doc.add_paragraph(
                            "({}){}".format(
                                eq_counter[0],
                                "  " + label if label else ""))
                        cap.alignment = WD_ALIGN_PARAGRAPH.RIGHT
                        for run in cap.runs:
                            run.font.size = Pt(10)
                            run.font.name = "Times New Roman"
                    else:
                        doc.add_paragraph("{}: {}".format(label, latex))

        # Embed figures after Results section
        if section.get("has_figures"):
            figures = get_figures()
            if figures:
                for fig_path in figures:
                    fig_counter[0] += 1
                    caption_text = get_figure_caption(
                        fig_path, results, fig_counter[0])
                    doc.add_paragraph("")
                    doc.add_picture(fig_path, width=Inches(5.5))
                    last_para = doc.paragraphs[-1]
                    last_para.alignment = WD_ALIGN_PARAGRAPH.CENTER
                    cap = doc.add_paragraph(caption_text)
                    cap.alignment = WD_ALIGN_PARAGRAPH.CENTER
                    for run in cap.runs:
                        run.font.size = Pt(CAPTION_PT)
                        run.font.name = "Times New Roman"
                        run.font.italic = True
                    doc.add_paragraph("")

        # Embed figure discussion after figures in Results & Discussion section
        if section.get("has_discussion") and results:
            add_discussion_word(doc, results)

    doc.save(PAPER_DOCX_FILE)
    print("Scientific paper (Word) saved: {}".format(PAPER_DOCX_FILE))


def build_paper_html(sections, results=None):
    """Build a scientific paper in HTML format with academic styling.

    Single-column layout, no sidebar, centered title/author block,
    properly numbered sections, KaTeX equations, and embedded figures.
    """
    paper_title = PAPER_TITLE or TITLE
    figures = get_figures()

    # Build author block
    author_html = ""
    authors = PAPER_AUTHORS
    if authors:
        names = []
        affiliations = []
        seen_aff = {}
        for a in authors:
            name = a.get("name", "")
            aff = a.get("affiliation", "")
            if aff and aff not in seen_aff:
                seen_aff[aff] = len(seen_aff) + 1
                affiliations.append(aff)
            sup = str(seen_aff.get(aff, "")) if aff else ""
            names.append("{}<sup>{}</sup>".format(name, sup) if sup else name)
        author_html = '<p class="authors">{}</p>\n'.format(", ".join(names))
        if affiliations:
            aff_items = []
            for idx, aff in enumerate(affiliations, 1):
                aff_items.append("<sup>{}</sup>{}".format(idx, aff))
            author_html += '<p class="affiliations">{}</p>\n'.format(
                "; ".join(aff_items))
    elif AUTHOR:
        author_html = '<p class="authors">{}</p>\n'.format(AUTHOR)

    # Keywords
    keywords_html = ""
    if PAPER_KEYWORDS:
        keywords_html = (
            '<p class="keywords"><strong>Keywords:</strong> '
            '<em>{}</em></p>\n'.format(", ".join(PAPER_KEYWORDS)))

    # Build figure HTML (reuse base64 embedding)
    fig_counter = [0]

    def _embed_figure(fig_path):
        fig_counter[0] += 1
        fig_name = os.path.basename(fig_path)
        caption = get_figure_caption(fig_path, results, fig_counter[0])
        mime = "image/svg+xml" if fig_path.endswith(".svg") else "image/png"
        with open(fig_path, "rb") as f:
            img_data = base64.b64encode(f.read()).decode("utf-8")
        return """
        <div class="paper-figure">
            <img src="data:{};base64,{}" alt="{}">
            <p class="fig-caption">{}</p>
        </div>""".format(mime, img_data, caption, caption)

    # Build equation HTML
    eq_counter = [0]

    def _embed_equation(eq):
        eq_counter[0] += 1
        label = eq.get("label", "")
        latex = eq.get("latex", "")
        if not latex:
            return ""
        fallback_img = ""
        eq_img_dir = os.path.join(REPORT_DIR, "_eq_images")
        if not os.path.exists(eq_img_dir):
            os.makedirs(eq_img_dir)
        eq_img_path = os.path.join(eq_img_dir, "eq_{}.png".format(eq_counter[0]))
        if render_equation_to_image(latex, eq_img_path):
            with open(eq_img_path, "rb") as imgf:
                img_b64 = base64.b64encode(imgf.read()).decode("utf-8")
            fallback_img = (
                '<img class="eq-fallback" src="data:image/png;base64,{}" '
                'alt="{}" style="display:none; max-width:90%;">'.format(
                    img_b64, label))
        return """
        <div class="paper-equation">
            <div class="eq-content katex-eq">$${}$$</div>
            {}
            <span class="eq-number">({}){}</span>
        </div>""".format(latex, fallback_img, eq_counter[0],
                         "  " + label if label else "")

    # Build sections HTML
    section_html = ""
    for section in sections:
        heading_text = _paper_section_heading(section)
        stype = section.get("type", "numbered")
        is_sub = section.get("is_subsection", False)
        section_id = heading_text.lower().replace(" ", "-").replace(".", "")

        # Heading tag
        if stype == "abstract":
            tag = "h2"
        elif is_sub:
            tag = "h3"
        else:
            tag = "h2"

        # Content formatting
        if stype == "abstract":
            content = '<div class="abstract-text">{}</div>'.format(
                _prose_to_html(section["content"]))
        elif stype == "references" and results and results.get("references"):
            content = format_references_html(results)
        elif section.get("has_figures") and results and results.get("key_results"):
            content = format_results_html(results)
            if results.get("tables"):
                content += format_custom_tables_html(results)
            # Add discussion text
            disc = section["content"]
            if disc and not disc.startswith("["):
                content += _prose_to_html(disc)
            # Add figures
            if figures:
                for fig_path in figures:
                    content += _embed_figure(fig_path)
        elif "Validation" in section["heading"] and results and results.get("validation"):
            content = format_validation_html(results)
        elif section.get("has_benchmark") and results:
            content = format_benchmark_html(results)
        elif section.get("has_uncertainty") and results:
            content = format_uncertainty_html(results)
        elif section.get("has_risk") and results:
            content = format_risk_html(results)
        elif section.get("has_scope", False) or section.get("has_markdown", False):
            content = scope_content_to_html(section["content"])
        else:
            content = _prose_to_html(section["content"])

        # Add figure discussion after figures in Results & Discussion
        if section.get("has_discussion") and results:
            content += format_discussion_html(results)

        # Add equations after methodology
        if section.get("has_equations"):
            equations = get_equations(results)
            if equations:
                for eq in equations:
                    content += _embed_equation(eq)

        section_html += """
        <section id="{}">
            <{} class="section-heading">{}</{}>
            <div class="section-content">{}</div>
        </section>""".format(section_id, tag, heading_text, tag, content)

    # KaTeX CDN
    equations = get_equations(results)
    katex_head = ""
    katex_body_script = ""
    if equations:
        katex_head = """
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/katex@0.16.11/dist/katex.min.css">
    <script defer src="https://cdn.jsdelivr.net/npm/katex@0.16.11/dist/katex.min.js"></script>
    <script defer src="https://cdn.jsdelivr.net/npm/katex@0.16.11/dist/contrib/auto-render.min.js"></script>"""
        katex_body_script = """
    <script>
        document.addEventListener("DOMContentLoaded", function() {{
            if (typeof renderMathInElement === "function") {{
                renderMathInElement(document.body, {{
                    delimiters: [
                        {{left: "$$", right: "$$", display: true}},
                        {{left: "$", right: "$", display: false}}
                    ],
                    throwOnError: false
                }});
            }} else {{
                var eqs = document.querySelectorAll(".katex-eq");
                for (var i = 0; i < eqs.length; i++) {{ eqs[i].style.display = "none"; }}
                var imgs = document.querySelectorAll(".eq-fallback");
                for (var j = 0; j < imgs.length; j++) {{ imgs[j].style.display = "inline"; }}
            }}
        }});
    </script>"""

    html = """<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>{title}</title>{katex_head}
    <style>
        * {{ margin: 0; padding: 0; box-sizing: border-box; }}
        body {{
            font-family: 'Times New Roman', Times, 'DejaVu Serif', Georgia, serif;
            line-height: 1.6; color: #1a1a1a;
            max-width: 800px; margin: 0 auto; padding: 2rem 2.5rem;
            background: #fff;
        }}
        /* ── Title block ── */
        .paper-header {{ text-align: center; margin-bottom: 2rem;
            border-bottom: 2px solid #333; padding-bottom: 1.5rem; }}
        .paper-header h1 {{ font-size: 1.6rem; font-weight: bold;
            margin-bottom: 0.8rem; line-height: 1.3; }}
        .authors {{ font-size: 1.05rem; margin-bottom: 0.3rem; }}
        .affiliations {{ font-size: 0.9rem; font-style: italic;
            color: #555; margin-bottom: 0.3rem; }}
        .paper-date {{ font-size: 0.9rem; color: #666;
            font-style: italic; margin-top: 0.5rem; }}
        .keywords {{ font-size: 0.9rem; margin-top: 0.8rem;
            text-align: left; padding: 0.5rem 1rem;
            background: #f9f9f9; border-left: 3px solid #333; }}
        /* ── Abstract ── */
        .abstract-text {{ font-style: italic; padding: 0.5rem 1.5rem;
            border-left: 3px solid #666; margin: 0.5rem 0 1.5rem 0;
            color: #333; font-size: 0.95rem; }}
        .abstract-text p {{ margin-bottom: 0.5rem; }}
        /* ── Section headings ── */
        h2.section-heading {{ font-size: 1.2rem; font-weight: bold;
            margin-top: 1.8rem; margin-bottom: 0.6rem; color: #1a1a1a;
            border-bottom: 1px solid #ccc; padding-bottom: 0.2rem; }}
        h3.section-heading {{ font-size: 1.05rem; font-weight: bold;
            margin-top: 1.2rem; margin-bottom: 0.4rem; color: #333; }}
        .section-content {{ margin-bottom: 1rem; }}
        .section-content p {{ margin-bottom: 0.6rem; text-align: justify; }}
        /* ── Figures ── */
        .paper-figure {{ text-align: center; margin: 1.5rem 0;
            page-break-inside: avoid; }}
        .paper-figure img {{ max-width: 100%; border: 1px solid #ddd; }}
        .fig-caption {{ font-size: 0.85rem; color: #333;
            margin-top: 0.4rem; font-style: italic; }}
        /* ── Equations ── */
        .paper-equation {{ display: flex; align-items: center;
            justify-content: center; margin: 1rem 0;
            position: relative; }}
        .eq-content {{ flex: 1; text-align: center; font-size: 1.1rem; }}
        .eq-number {{ position: absolute; right: 0; font-size: 0.95rem;
            color: #333; }}
        /* ── Tables ── */
        table {{ border-collapse: collapse; width: 100%; margin: 1rem 0;
            font-size: 0.9rem; }}
        thead th {{ background: #2F5496; color: #fff; font-weight: 600;
            padding: 0.5rem; text-align: left; border: 1px solid #2a4a85; }}
        tbody td {{ border: 1px solid #ddd; padding: 0.4rem 0.5rem; }}
        tbody tr:nth-child(even) {{ background: #f8f9fa; }}
        td.num {{ text-align: right; font-variant-numeric: tabular-nums; }}
        .pass {{ color: #28a745; font-weight: bold; }}
        .fail {{ color: #dc3545; font-weight: bold; }}
        .results-table {{ max-width: 600px; margin: 1rem auto; }}
        .validation-table {{ max-width: 500px; margin: 1rem auto; }}
        .custom-table {{ margin: 0.5rem auto; }}
        /* ── References ── */
        .reference-list {{ list-style: none; padding-left: 0; }}
        .reference-list li {{ margin-bottom: 0.5rem; padding-left: 2rem;
            text-indent: -2rem; font-size: 0.9rem; }}
        .reference-list li strong {{ color: #333; }}
        /* ── Risk assessment styles ── */
        .risk-summary-card {{ background: #f8f9fa; border-left: 4px solid #333;
            padding: 0.8rem 1.2rem; margin-bottom: 1.2rem; }}
        .risk-badge {{ display: inline-block; padding: 0.15rem 0.6rem; border-radius: 3px;
            font-weight: 600; font-size: 0.85rem; color: #fff; }}
        .risk-badge.risk-high {{ background: #dc3545; }}
        .risk-badge.risk-medium {{ background: #e67e22; }}
        .risk-badge.risk-low {{ background: #28a745; }}
        .risk-table {{ font-size: 0.88rem; }}
        .risk-table .mitigation-cell {{ font-size: 0.85rem; color: #555; }}
        .risk-level {{ font-weight: bold; text-align: center; }}
        .risk-level.risk-high {{ color: #dc3545; }}
        .risk-level.risk-medium {{ color: #e67e22; }}
        .risk-level.risk-low {{ color: #28a745; }}
        /* ── Uncertainty analysis styles ── */
        .uncertainty-summary {{ background: #f5f5f5; border-left: 4px solid #333;
            padding: 0.8rem 1.2rem; margin-bottom: 1.2rem; }}
        .uncertainty-table {{ font-size: 0.88rem; }}
        .tornado-table {{ font-size: 0.88rem; }}
        /* ── Benchmark validation styles ── */
        .benchmark-table {{ font-size: 0.88rem; }}
        /* ── Discussion section styles ── */
        .discussion-block {{ background: #f9fafb; border-left: 4px solid #2d6a4f;
            padding: 1rem 1.2rem; margin-bottom: 1.5rem; border-radius: 0 4px 4px 0; }}
        .discussion-block h3 {{ color: #2d6a4f; margin-top: 0; }}
        .discussion-block .recommendation {{ background: #e8f5e9; padding: 0.5rem 0.8rem;
            border-radius: 3px; border-left: 3px solid #28a745; }}
        .discussion-block .traceability {{ font-size: 0.8rem; color: #888;
            border-top: 1px dashed #ccc; padding-top: 0.4rem; margin-top: 0.6rem; }}
        /* ── Footer ── */
        .paper-footer {{ margin-top: 3rem; padding-top: 1rem;
            border-top: 1px solid #ccc; font-size: 0.8rem;
            color: #999; text-align: center; }}
        /* ── Print styles ── */
        @media print {{
            body {{ padding: 0; max-width: none; }}
            .paper-figure {{ page-break-inside: avoid; }}
            table {{ page-break-inside: avoid; }}
        }}
    </style>
</head>
<body>
    <div class="paper-header">
        <h1>{title}</h1>
        {author_block}
        <p class="paper-date">{date}</p>
        {keywords}
    </div>
{sections}
    <div class="paper-footer">
        <p>Generated {date} using NeqSim task-solving workflow</p>
    </div>{katex_body_script}
</body>
</html>""".format(
        title=paper_title,
        author_block=author_html,
        date=TASK_DATE,
        keywords=keywords_html,
        sections=section_html,
        katex_head=katex_head,
        katex_body_script=katex_body_script,
    )

    with open(PAPER_HTML_FILE, "w", encoding="utf-8") as f:
        f.write(html)
    print("Scientific paper (HTML) saved: {}".format(PAPER_HTML_FILE))


# ══════════════════════════════════════════════════════════
# Main
# ══════════════════════════════════════════════════════════

if __name__ == "__main__":
    generate_paper = "--paper" in sys.argv or "--paper-only" in sys.argv
    paper_only = "--paper-only" in sys.argv

    # Word template selection: --template PATH | --no-template | saved setting
    explicit_template = None
    if "--template" in sys.argv:
        template_index = sys.argv.index("--template") + 1
        if template_index >= len(sys.argv):
            print("ERROR: --template requires a path to a .docx or .dotx file")
            sys.exit(2)
        explicit_template = sys.argv[template_index]
    KEEP_TEMPLATE_CONTENT = "--keep-template-content" in sys.argv
    try:
        REPORT_TEMPLATE = resolve_report_template(
            explicit_template, allow_saved="--no-template" not in sys.argv)
    except (OSError, ValueError) as error:
        print("ERROR: {}".format(error))
        print("Fix the path, pass --template PATH, or run:")
        print("  neqsim --set-report-template \"PATH\"   (or --reset-report-template)")
        sys.exit(2)

    # Auto-read task data
    study_config = load_study_config()
    results = load_results()
    task_spec = load_task_spec()
    record_environment(results)
    resolve_report_identity(study_config, task_spec, results)
    apply_report_output_names(TITLE)

    print("")
    print("Generating outputs for: {}".format(TITLE))
    print("Report files: {}.docx / {}.html".format(REPORT_BASENAME, REPORT_BASENAME))
    if REPORT_TEMPLATE:
        print("Word template: {}".format(REPORT_TEMPLATE))
    if not TASK_STATEMENT:
        print("NOTE: no task statement found. Add study.title to study_config.yaml,")
        print("      an '## Objective' section to task_spec.md, or 'objective' to")
        print("      results.json so the report states the task up front.")

    study_config_warnings = validate_study_config(study_config, results, task_spec)
    if study_config_warnings:
        print("")
        print("Study configuration warnings:")
        for warning in study_config_warnings:
            print("  - {}".format(warning))

    if not paper_only:
        # Build report sections and generate technical report
        sections = build_sections(results, task_spec, study_config_warnings,
                                  study_config)
        print("")
        build_word_report(sections, results)
        build_html_report(sections, results)
        print("")
        print("Technical reports generated.")
        print("  Open {} in a browser for navigable view.".format(
            os.path.basename(HTML_FILE)))
        print("  Open {} for formal distribution.".format(
            os.path.basename(DOCX_FILE)))

    if generate_paper:
        # Build paper sections and generate scientific paper
        paper_sections = build_paper_sections(results, task_spec)
        print("")
        build_paper_docx(paper_sections, results)
        build_paper_html(paper_sections, results)
        print("")
        print("Scientific papers generated.")
        print("  Open {} for reading.".format(os.path.basename(PAPER_HTML_FILE)))
        print("  Open {} for journal submission / distribution.".format(
            os.path.basename(PAPER_DOCX_FILE)))

    written = []
    if not paper_only:
        written.extend([DOCX_FILE, HTML_FILE])
    if generate_paper:
        written.extend([PAPER_DOCX_FILE, PAPER_HTML_FILE])
    if written:
        prune_superseded_outputs(written)

    if not generate_paper and not paper_only:
        print("")
        print("TIP: Add --paper flag to also generate a scientific paper.")
        print("     python step3_report/generate_report.py --paper")

    if not paper_only:
        generate_work_record(study_config)

    if not results:
        print("")
        print("TIP: Create results.json in the task root to auto-populate")
        print("     the Results and Validation sections. See the task README")
        print("     for the results.json pattern.")
