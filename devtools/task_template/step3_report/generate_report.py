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
    python step3_report/generate_report.py --language nb
    python devtools/task_template/step3_report/generate_report.py --task-dir PATH

The canonical copy of this script lives in devtools/task_template/. Run it
against any task folder with `neqsim report <task folder>` (or --task-dir /
NEQSIM_TASK_DIR) so a fix here applies to task folders created earlier.

The report language is English unless another is configured. Resolution order:
--language CODE, NEQSIM_REPORT_LANGUAGE, then `report.language` in
study_config.yaml. It sets the section headings, cover labels, and caption
prefixes the generator owns, and the document language of the .docx and .html
so Word spell-checks in that language; authored content is written in that
language by the study author. The scientific paper (--paper) stays English.

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
import hashlib
import io
import shutil
import sqlite3
import subprocess
from datetime import date

try:
    from docx import Document
    from docx.shared import Inches, Pt, RGBColor
    from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_BREAK, WD_TAB_ALIGNMENT
    from docx.enum.table import WD_TABLE_ALIGNMENT, WD_CELL_VERTICAL_ALIGNMENT
    from docx.enum.section import WD_ORIENT, WD_SECTION
    from docx.enum.style import WD_STYLE_TYPE
    from docx.text.paragraph import Paragraph
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
    from matplotlib.transforms import Bbox
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
# Inline maths ($...$ inside a sentence) is rendered at body size so it sits
# on the line like a normal word instead of towering over the surrounding text.
INLINE_EQ_FONT_PT = BODY_PT

# ── Page measure ─────────────────────────────────────────
# A corporate .dotx is frequently LANDSCAPE because it is built for forms and
# presentations. An engineering report set on that 9.5 in measure runs to ~140
# characters per line, roughly twice the readable optimum, and every figure and
# table sized for a portrait page leaves a third of the width empty.
# "template" keeps whatever the template declares.
REPORT_ORIENTATION = "portrait"   # portrait | landscape | template
MAX_MEASURE_IN = 6.7              # widest column we set continuous prose on
MIN_SIDE_MARGIN_IN = 0.79         # 20 mm — never narrower when widening margins
# Room left under a full-width figure for its caption and the following gap.
FIGURE_CAPTION_ALLOWANCE_IN = 0.9
# ISO 80000-1 digit grouping: a non-breaking space, not a comma.
THOUSANDS_SEP = "\u00a0"

# ── Report language ──────────────────────────────────────
# Resolved from --language, NEQSIM_REPORT_LANGUAGE, or study_config.yaml
# (report.language). English is the default. This sets the report furniture the
# generator owns — section headings, cover labels, caption prefixes, navigation
# — and the document language of the .docx and .html, so Word spell-checks in
# the right language. Authored content (results.json, task_spec.md, the manual
# sections) is written in that language by the study author.
DEFAULT_REPORT_LANGUAGE = "en"
REPORT_LANGUAGE = DEFAULT_REPORT_LANGUAGE

# Spellings a user may reasonably write in study_config.yaml.
LANGUAGE_ALIASES = {
    "en": "en", "eng": "en", "english": "en", "en-gb": "en", "en-us": "en",
    "no": "nb", "nb": "nb", "nb-no": "nb", "nn": "nb", "norsk": "nb",
    "norwegian": "nb", "bokmal": "nb", "bokmål": "nb",
}

# Written into the .docx (w:lang) and the HTML lang attribute.
LANGUAGE_LOCALES = {
    "en": "en-GB",
    "nb": "nb-NO",
    "da": "da-DK",
    "sv": "sv-SE",
    "de": "de-DE",
    "fr": "fr-FR",
    "nl": "nl-NL",
    "es": "es-ES",
    "pt": "pt-PT",
    "it": "it-IT",
}

# Fixed report wording, keyed by the English phrase used in the code. A language
# without a table here still gets its document language set; only the furniture
# stays English, and the generator says so.
REPORT_STRINGS = {
    "nb": {
        # Section headings
        "Executive Summary": "Sammendrag",
        "Problem Description": "Problembeskrivelse",
        "Safety Study Readiness": "Grunnlag for sikkerhetsstudie",
        "Scope and Standards": "Omfang og standarder",
        "Information Sources and Evidence Basis":
            "Informasjonskilder og dokumentasjonsgrunnlag",
        "Approach": "Fremgangsmåte",
        "Solution Workflow": "Arbeidsflyt for løsningen",
        "Results": "Resultater",
        "Discussion": "Diskusjon",
        "Analytical Depth": "Analytisk dybde",
        "Validation Summary": "Valideringssammendrag",
        "Report Consistency Review": "Konsistenskontroll av rapporten",
        "Study Configuration Warnings": "Advarsler fra studiekonfigurasjonen",
        "Benchmark Validation": "Referansevalidering",
        "Uncertainty Analysis": "Usikkerhetsanalyse",
        "Risk Assessment": "Risikovurdering",
        "Assumptions and Data Gaps": "Forutsetninger og datamangler",
        "Evidence Gaps and Design-Grade Blockers":
            "Dokumentasjonsmangler og hindringer for designgrunnlag",
        "Recommendations": "Anbefalinger",
        "Tooling Improvements Delivered": "Leverte verktøyforbedringer",
        "Conclusions and Recommendations": "Konklusjoner og anbefalinger",
        "References": "Referanser",
        # Front matter and furniture
        "NeqSim Engineering Report": "NeqSim ingeniørrapport",
        "Table of Contents": "Innholdsfortegnelse",
        "List of Figures": "Figurliste",
        "List of Tables": "Tabelliste",
        "Key Equations": "Sentrale ligninger",
        "Appendix A. Report Quality Checks": "Vedlegg A. Kvalitetskontroll av rapporten",
        "Appendix B. Report Quality Checks": "Vedlegg B. Kvalitetskontroll av rapporten",
        "Appendix A. Reproducing the Results": "Vedlegg A. Reprodusere resultatene",
        "Software and environment": "Programvare og miljø",
        "Steps": "Trinn",
        "Checks after a rerun": "Kontroller etter ny kjøring",
        "Changing a case": "Endre et tilfelle",
        "Consistency review": "Konsistenskontroll",
        "Study configuration": "Studiekonfigurasjon",
        "automatic equation typesetting unavailable":
            "automatisk ligningssetting ikke tilgjengelig",
        "Test": "Test",
        "Reference": "Referanse",
        "Reference value": "Referanseverdi",
        "NeqSim value": "NeqSim-verdi",
        "Unit": "Enhet",
        "Deviation [%]": "Avvik [%]",
        "Tolerance [%]": "Toleranse [%]",
        "Status": "Status",
        "Notes": "Merknader",
        "Contents": "Innhold",
        "Revision History": "Revisjonshistorikk",
        "Document Number": "Dokumentnummer",
        "Document No.": "Dokumentnr.",
        "Revision": "Revisjon",
        "Rev": "Rev",
        "Date": "Dato",
        "Description": "Beskrivelse",
        "Author": "Forfatter",
        "Classification": "Klassifisering",
        "Initial issue": "Første utgivelse",
        "(not specified)": "(ikke angitt)",
        "Task": "Oppgave",
        "Figure": "Figur",
        "Table": "Tabell",
        "Equation": "Ligning",
        # Sub-headings
        "Key results": "Hovedresultater",
        "Validation checks": "Valideringskontroller",
        "Applicable Standards": "Gjeldende standarder",
        "Calculation Methods": "Beregningsmetoder",
        "Acceptance Criteria": "Akseptkriterier",
        "Source systems read": "Kildesystemer som er lest",
        "Assumptions the results depend on": "Forutsetninger resultatene hviler på",
        "Information sought but not available, and what was assumed in its place":
            "Informasjon som ble søkt, men ikke funnet, og hva som ble antatt i stedet",
        "Input Parameter Ranges": "Spenn i inngangsparametere",
        "Output Distribution (P10 / P50 / P90)": "Resultatfordeling (P10 / P50 / P90)",
        "Sensitivity Ranking (Tornado)": "Sensitivitetsrangering (tornado)",
        "Contributors ranked on a common basis": "Bidragsytere rangert på felles grunnlag",
        "Which effects actually carry the result, largest first.":
            "Hvilke effekter som faktisk bærer resultatet, størst først.",
        "Verdict on each source recommendation": "Vurdering av hver kildeanbefaling",
        "Supported, supported with correction, or challenged \u2014 with the basis.":
            "Støttet, støttet med korreksjon eller utfordret \u2014 med begrunnelse.",
        "Hypotheses ruled out quantitatively": "Hypoteser utelukket kvantitativt",
        "What was excluded, by which test, and with how much margin.":
            "Hva som ble utelukket, med hvilken test og med hvor stor margin.",
        "Robustness and crossover": "Robusthet og vippepunkt",
        "How far an input can move before the conclusion flips.":
            "Hvor langt en inngangsverdi kan flytte seg før konklusjonen snur.",
        "Direction of each conservatism": "Retning på hver konservatisme",
        "Whether each assumption bounds the answer from above or below.":
            "Om hver antakelse avgrenser svaret ovenfra eller nedenfra.",
        "Cheapest discriminating test": "Billigste avgjørende test",
        "The one measurement that would separate the surviving explanations.":
            "Den ene målingen som skiller forklaringene som gjenstår.",
        "Evidence that does not fit": "Evidens som ikke passer",
        "Observations the accepted explanation does not account for.":
            "Observasjoner som den aksepterte forklaringen ikke dekker.",
    },
}


def _t(text):
    """Translate fixed report wording into REPORT_LANGUAGE.

    Returns the English phrase unchanged when the language has no table or no
    entry for it, so adding a heading never breaks a translated report.
    """
    return REPORT_STRINGS.get(REPORT_LANGUAGE, {}).get(text, text)


def _report_locale():
    """Return the document locale (``nb-NO``) for the report language."""
    return LANGUAGE_LOCALES.get(REPORT_LANGUAGE, REPORT_LANGUAGE or "en")

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
PDF_FILE = os.path.join(REPORT_DIR, "Report.pdf")
PAPER_DOCX_FILE = os.path.join(REPORT_DIR, "Paper.docx")
PAPER_HTML_FILE = os.path.join(REPORT_DIR, "Paper.html")
PAPER_PDF_FILE = os.path.join(REPORT_DIR, "Paper.pdf")
RESULTS_FILE = os.path.join(TASK_DIR, "results.json")
TASK_SPEC_FILE = os.path.join(TASK_DIR, "step1_scope_and_research", "task_spec.md")
STUDY_CONFIG_FILE = os.path.join(TASK_DIR, "study_config.yaml")
OUTPUT_MANIFEST_FILE = os.path.join(REPORT_DIR, ".report_outputs.json")
LEGACY_OUTPUT_NAMES = ("Report.docx", "Report.html", "Report.pdf",
                       "Paper.docx", "Paper.html", "Paper.pdf")
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
    global REPORT_BASENAME, DOCX_FILE, HTML_FILE, PDF_FILE
    global PAPER_DOCX_FILE, PAPER_HTML_FILE, PAPER_PDF_FILE
    REPORT_BASENAME = slugify_report_name(title)
    DOCX_FILE = os.path.join(REPORT_DIR, REPORT_BASENAME + ".docx")
    HTML_FILE = os.path.join(REPORT_DIR, REPORT_BASENAME + ".html")
    PDF_FILE = os.path.join(REPORT_DIR, REPORT_BASENAME + ".pdf")
    PAPER_DOCX_FILE = os.path.join(REPORT_DIR, REPORT_BASENAME + "_Paper.docx")
    PAPER_HTML_FILE = os.path.join(REPORT_DIR, REPORT_BASENAME + "_Paper.html")
    PAPER_PDF_FILE = os.path.join(REPORT_DIR, REPORT_BASENAME + "_Paper.pdf")
    return REPORT_BASENAME


def _docx_to_pdf_word(docx_path, pdf_path):
    """Convert through Microsoft Word COM automation (Windows only).

    Preferred backend: Word renders its own format, so the corporate template's
    fonts, headers, footers and numbering survive the conversion intact.

    Parameters
    ----------
    docx_path : str
        Absolute path of the source Word document.
    pdf_path : str
        Absolute path of the PDF to write.

    Returns
    -------
    str or None
        ``None`` on success, otherwise the reason the backend was unusable.
    """
    try:
        import pythoncom
        import win32com.client
    except ImportError:
        return "pywin32 is not installed"
    wd_export_format_pdf = 17
    pythoncom.CoInitialize()
    word = None
    document = None
    try:
        word = win32com.client.DispatchEx("Word.Application")
        word.Visible = False
        word.DisplayAlerts = 0
        document = word.Documents.Open(docx_path, ReadOnly=True, Visible=False)
        document.ExportAsFixedFormat(pdf_path, wd_export_format_pdf,
                                     CreateBookmarks=1)
    except Exception as error:  # pragma: no cover - COM surfaces many types
        return "Word automation failed ({})".format(error)
    finally:
        if document is not None:
            try:
                document.Close(0)
            except Exception:
                pass
        if word is not None:
            try:
                word.Quit()
            except Exception:
                pass
        pythoncom.CoUninitialize()
    return None


def _docx_to_pdf_libreoffice(docx_path, pdf_path):
    """Convert through a headless LibreOffice installation.

    Cross-platform fallback. Fidelity to a Word template is good but not exact,
    so this is only used when Word automation is unavailable.

    Parameters
    ----------
    docx_path : str
        Absolute path of the source Word document.
    pdf_path : str
        Absolute path of the PDF to write.

    Returns
    -------
    str or None
        ``None`` on success, otherwise the reason the backend was unusable.
    """
    executable = shutil.which("soffice") or shutil.which("libreoffice")
    if not executable:
        return "LibreOffice (soffice) is not on PATH"
    outdir = os.path.dirname(pdf_path)
    try:
        completed = subprocess.run(
            [executable, "--headless", "--convert-to", "pdf", "--outdir",
             outdir, docx_path],
            stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=300)
    except (OSError, subprocess.SubprocessError) as error:
        return "LibreOffice call failed ({})".format(error)
    if completed.returncode != 0:
        return "LibreOffice exited {}".format(completed.returncode)
    produced = os.path.join(
        outdir, os.path.splitext(os.path.basename(docx_path))[0] + ".pdf")
    if produced != pdf_path and os.path.isfile(produced):
        shutil.move(produced, pdf_path)
    return None


def convert_docx_to_pdf(docx_path, pdf_path, label="Report"):
    """Render a generated Word report to PDF for distribution.

    The PDF is produced from the DOCX rather than from the HTML so that it
    inherits the configured corporate Word template. Backends are tried in
    descending order of fidelity, and a failure is reported rather than raised:
    a missing PDF must not discard an otherwise complete report run.

    Parameters
    ----------
    docx_path : str
        Path of the Word report produced by this run.
    pdf_path : str
        Path of the PDF to write.
    label : str, optional
        Human-readable name used in console messages.

    Returns
    -------
    bool
        True when the PDF was written.
    """
    docx_path = os.path.abspath(docx_path)
    pdf_path = os.path.abspath(pdf_path)
    if not os.path.isfile(docx_path):
        print("NOTE: {} PDF skipped, source document is missing: {}".format(
            label, docx_path))
        return False
    reasons = []
    for backend in (_docx_to_pdf_word, _docx_to_pdf_libreoffice):
        reason = backend(docx_path, pdf_path)
        if reason is None and os.path.isfile(pdf_path):
            print("{} PDF saved: {}".format(label, pdf_path))
            return True
        reasons.append(reason or "backend reported success but wrote no file")
    print("NOTE: {} PDF could not be generated. Tried: {}.".format(
        label, "; ".join(reasons)))
    print("      Install Microsoft Word with pywin32, or LibreOffice, "
          "or export the .docx manually.")
    return False


def want_pdf_output(study_config):
    """Decide whether this run should also emit PDF.

    Parameters
    ----------
    study_config : dict
        Parsed ``study_config.yaml``.

    Returns
    -------
    bool
        True when ``--pdf`` was passed or ``report.formats`` lists ``pdf``.
    """
    if "--pdf" in sys.argv:
        return True
    if "--no-pdf" in sys.argv:
        return False
    formats = (study_config or {}).get("report", {}).get("formats") or []
    return any(str(fmt).strip().lower() == "pdf" for fmt in formats)


def resolve_report_orientation(study_config):
    """Resolve the page orientation for the report body.

    Order: ``--orientation VALUE`` > ``report.orientation`` in
    ``study_config.yaml`` > portrait. ``template`` keeps whatever the corporate
    template declares, which is usually landscape.
    """
    allowed = ("portrait", "landscape", "template")
    value = _cli_option("--orientation")
    if not value:
        value = (study_config or {}).get("report", {}).get("orientation")
    value = str(value or "portrait").strip().lower()
    if value not in allowed:
        print("NOTE: unknown report.orientation '{}'; using portrait.".format(value))
        return "portrait"
    return value


def resolve_report_language(study_config):
    """Resolve the language the report is written in.

    Order: ``--language CODE`` > ``NEQSIM_REPORT_LANGUAGE`` >
    ``report.language`` (then ``study.language``) in ``study_config.yaml`` >
    English.

    Parameters
    ----------
    study_config : dict
        Parsed ``study_config.yaml``.

    Returns
    -------
    str
        Normalized language code, e.g. ``en`` or ``nb``.
    """
    value = _cli_option("--language") or os.environ.get("NEQSIM_REPORT_LANGUAGE", "")
    if not value:
        config = study_config or {}
        value = (config.get("report", {}).get("language")
                 or config.get("study", {}).get("language") or "")
    value = str(value or "").strip().lower()
    if value in ("", "auto", "default"):
        return DEFAULT_REPORT_LANGUAGE
    code = LANGUAGE_ALIASES.get(value, value)
    if code != DEFAULT_REPORT_LANGUAGE and code not in REPORT_STRINGS:
        print("NOTE: no translation table for report language '{}'. Section "
              "headings and cover labels stay English; the document language "
              "is set to {}.".format(value, LANGUAGE_LOCALES.get(code, code)))
    return code


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
        except (OSError, ValueError) as error:
            print("NOTE: could not read report output manifest; "
                  "checking legacy output names only ({}).".format(error), file=sys.stderr)
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
    except OSError as error:
        print("NOTE: could not save report output manifest; cleanup tracking "
              "may be incomplete next run ({}).".format(error), file=sys.stderr)

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
        body.widow_control = True
    except KeyError:
        pass
    _ensure_caption_style(doc)


def _ensure_caption_style(doc):
    """Give figure and table captions a real Caption style.

    Captions written as ad-hoc italic runs cannot be collected into a list of
    figures, and Word is free to break the page between a figure and its
    caption. A styled caption fixes both.
    """
    _ensure_paragraph_style(doc, "Caption", CAPTION_PT)
    try:
        style = doc.styles["Caption"]
    except KeyError:
        return
    style.font.size = Pt(CAPTION_PT)
    style.font.italic = True
    style.font.bold = False
    style.font.color.rgb = RGBColor(90, 90, 90)
    style.paragraph_format.space_after = Pt(BODY_SPACE_AFTER_PT)
    style.paragraph_format.keep_with_next = False


def _set_run_language(r_pr, locale):
    """Set ``w:lang`` on a run-properties element, replacing any existing one."""
    for existing in r_pr.findall(qn("w:lang")):
        r_pr.remove(existing)
    r_pr.append(parse_xml(
        '<w:lang {} w:val="{}" w:eastAsia="{}"/>'.format(
            nsdecls("w"), locale, locale)))


def _apply_document_language(doc):
    """Set the document language so Word spell-checks in the report language.

    A template built in one language otherwise marks every word of a report
    written in another as a spelling error.
    """
    locale = _report_locale()
    styles = doc.styles.element
    defaults = styles.find(qn("w:docDefaults"))
    if defaults is not None:
        r_pr_default = defaults.find(qn("w:rPrDefault"))
        if r_pr_default is None:
            r_pr_default = parse_xml(
                '<w:rPrDefault {}/>'.format(nsdecls("w")))
            defaults.insert(0, r_pr_default)
        r_pr = r_pr_default.find(qn("w:rPr"))
        if r_pr is None:
            r_pr = parse_xml('<w:rPr {}/>'.format(nsdecls("w")))
            r_pr_default.append(r_pr)
        _set_run_language(r_pr, locale)
    try:
        normal = doc.styles["Normal"].element
    except KeyError:
        return
    r_pr = normal.find(qn("w:rPr"))
    if r_pr is None:
        r_pr = parse_xml('<w:rPr {}/>'.format(nsdecls("w")))
        normal.append(r_pr)
    _set_run_language(r_pr, locale)


def _normalize_page_setup(doc):
    """Set the body on a readable measure, whatever the template declares."""
    if REPORT_ORIENTATION == "template":
        return
    want_landscape = REPORT_ORIENTATION == "landscape"
    for section in doc.sections:
        if (section.page_width > section.page_height) != want_landscape:
            section.page_width, section.page_height = (
                section.page_height, section.page_width)
            section.orientation = (WD_ORIENT.LANDSCAPE if want_landscape
                                   else WD_ORIENT.PORTRAIT)
        measure = (section.page_width - section.left_margin
                   - section.right_margin) / 914400.0
        if measure <= MAX_MEASURE_IN:
            continue
        extra = Inches((measure - MAX_MEASURE_IN) / 2.0)
        floor = Inches(MIN_SIDE_MARGIN_IN)
        section.left_margin = max(section.left_margin + extra, floor)
        section.right_margin = max(section.right_margin + extra, floor)


def _text_width_in(doc):
    """Printable width of the current section, in inches."""
    section = doc.sections[-1]
    return max(2.0, (section.page_width - section.left_margin
                     - section.right_margin) / 914400.0)


def _text_height_in(doc):
    """Printable height of the current section, in inches."""
    section = doc.sections[-1]
    return max(2.0, (section.page_height - section.top_margin
                     - section.bottom_margin) / 914400.0)


def _new_document():
    """Return a Word document based on the configured template, if any."""
    global TEMPLATE_NUMBERS_HEADINGS
    if not REPORT_TEMPLATE:
        doc = Document()
        _apply_readable_typography(doc)
        _apply_document_language(doc)
        _normalize_page_setup(doc)
        return doc
    doc = Document(REPORT_TEMPLATE)
    if not KEEP_TEMPLATE_CONTENT:
        _clear_document_body(doc)
    for name, size_pt in (("Title", 28), ("Heading 1", HEADING1_PT),
                          ("Heading 2", HEADING2_PT),
                          ("Heading 3", HEADING3_PT), ("List Bullet", None)):
        _ensure_paragraph_style(doc, name, size_pt, bold=size_pt is not None)
    _apply_readable_typography(doc)
    _apply_document_language(doc)
    _normalize_page_setup(doc)
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


_MANUAL_SUBSECTION_STATE = {}
_LEADING_CHAPTER_NUMBER = re.compile(r"^\s*(\d+)[.)]?\s+")


def _add_heading(doc, text, level=1, numbered=True):
    """Add a heading that does not fight the template's own numbering.

    When the template numbers headings, our manual "N. " prefix is dropped so
    Word supplies the single authoritative number; headings that must stay
    unnumbered (contents, front matter) have numbering suppressed instead.
    Without template numbering, level-2 headings get a manual "N.k" so the
    built-in layout matches a numbered corporate template.
    """
    text = str(text)
    if _heading_numbering_active(doc, level):
        if numbered:
            text = _MANUAL_HEADING_NUMBER.sub("", text)
        heading = doc.add_heading(text, level=level)
        if not numbered:
            _suppress_paragraph_numbering(heading)
    else:
        state = _MANUAL_SUBSECTION_STATE.setdefault(id(doc), {"chapter": None, "sub": 0})
        if level == 1:
            match = _LEADING_CHAPTER_NUMBER.match(text) if numbered else None
            state["chapter"], state["sub"] = (match.group(1) if match else None), 0
        elif level == 2 and numbered and state["chapter"] \
                and not _MANUAL_HEADING_NUMBER.match(text):
            state["sub"] += 1
            text = "{}.{} {}".format(state["chapter"], state["sub"], text)
        heading = doc.add_heading(text, level=level)
    # A heading stranded at the foot of a page is the most visible layout fault
    # in an otherwise clean report.
    heading.paragraph_format.keep_with_next = True
    heading.paragraph_format.page_break_before = False
    return heading


def _repeat_header_row(table):
    """Mark row 1 as a header so it repeats when the table breaks across pages."""
    tr_pr = table.rows[0]._tr.get_or_add_trPr()
    if tr_pr.find(qn("w:tblHeader")) is None:
        tr_pr.append(parse_xml('<w:tblHeader {}/>'.format(nsdecls("w"))))


def _keep_rows_intact(table):
    """Stop Word splitting a single table row across a page break."""
    for row in table.rows:
        tr_pr = row._tr.get_or_add_trPr()
        if tr_pr.find(qn("w:cantSplit")) is None:
            tr_pr.append(parse_xml('<w:cantSplit {}/>'.format(nsdecls("w"))))


_NUMERIC_CELL = re.compile(
    r"^[\s\u00a0]*[<>\u2264\u2265\u00b1~]?[\s\u00a0]*[-+]?[\d\u00a0,. ]*\d"
    r"(?:[eE][-+]?\d+)?[\s\u00a0]*%?[\s\u00a0]*$")


def _align_numeric_cells(table):
    """Right-align the cells that hold numbers so digits line up by place value."""
    for row in table.rows[1:]:
        for cell in row.cells:
            text = cell.text.strip()
            if not text or not _NUMERIC_CELL.match(text):
                continue
            for paragraph in cell.paragraphs:
                paragraph.alignment = WD_ALIGN_PARAGRAPH.RIGHT


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

_PASS_STATUSES = {"PASS", "PASSED", "OK", "MET"}
_FAIL_STATUSES = {"FAIL", "FAILED", "NOT MET", "NOT_MET"}


def _status_word(status):
    """Map a validation-row status to PASS, FAIL or its own upper-cased text."""
    if isinstance(status, bool):
        return "PASS" if status else "FAIL"
    word = str(status if status is not None else "").strip().upper()
    if word in _PASS_STATUSES:
        return "PASS"
    if word in _FAIL_STATUSES:
        return "FAIL"
    return word or "N/A"


def _normalize_validation(data):
    """Accept a list of check rows as `validation` by folding it into the dict shape used here."""
    validation = data.get("validation")
    if isinstance(validation, list):
        data["validation_rows"] = validation
        folded = {}
        for index, row in enumerate(validation):
            if not isinstance(row, dict):
                continue
            name = str(row.get("check", index))
            if name in folded:
                name = "{} ({})".format(name, index + 1)
            # Status words, not booleans: a boolean would be inverted for names such as "error".
            folded[name] = _status_word(row.get("status", row.get("passed", row.get("pass"))))
        data["validation"] = folded
    elif validation is not None and not isinstance(validation, dict):
        data["validation"] = {}
    return data


def load_results():
    """Load results.json if it exists. Returns dict or None."""
    if os.path.exists(RESULTS_FILE):
        with open(RESULTS_FILE, "r", encoding="utf-8") as f:
            data = _normalize_validation(json.load(f))
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


# Norwegian task_spec.md files are common, and heading matching used to be
# English-only, so a fully written Norwegian scope section was reported as
# "lacks source data". Each canonical heading therefore carries its aliases.
SPEC_HEADING_ALIASES = {
    "applicable standards": ("gjeldende standarder", "standarder", "regelverk",
                             "scope and standards", "omfang og standarder"),
    "calculation methods": ("beregningsmetoder", "metode", "metoder",
                            "framgangsmate", "fremgangsmate"),
    "acceptance criteria": ("akseptkriterier", "akseptansekriterier"),
    "operating envelope": ("driftsomrade", "driftsvindu", "operasjonsvindu"),
    "objective": ("formal", "mal", "hensikt", "oppgave"),
    "scope": ("omfang", "avgrensning", "bakgrunn og avgrensning"),
    "data sources": ("datakilder", "kilder"),
    "deliverables": ("leveranser", "leveranse"),
}


def _heading_variants(heading):
    """Return the heading plus any language aliases registered for it."""
    key = str(heading or "").strip().lower()
    return (key,) + SPEC_HEADING_ALIASES.get(key, ())


def extract_spec_section(spec_text, heading):
    """Extract a section from task_spec.md by heading.

    Matching is case-insensitive and alias-aware, so a Norwegian heading such as
    "Akseptkriterier" satisfies a request for "Acceptance Criteria".
    """
    if not spec_text:
        return ""
    variants = _heading_variants(heading)
    lines = spec_text.split("\n")
    capturing = False
    result = []
    for line in lines:
        if line.startswith("## ") and any(v in line.lower() for v in variants):
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


# Validation keys whose NAME asserts something bad. For these, False is the
# desired outcome, so it must not be reported as a failed check. The enterprise
# skills require agents to emit `credentials_disclosed: false`, which otherwise
# reads as a blocker and flips safety readiness to DESIGN-GRADE BLOCKED.
_NEGATIVE_VALIDATION_MARKERS = (
    "disclosed", "exceeded", "violated", "breached", "failed", "failure",
    "error", "errors", "blocked", "blocker", "blockers", "leaked", "exposed",
    "inferred", "fabricated", "constructed", "overrun", "overdue",
)

# A key that already negates itself ("no_data_fabricated") is positive again.
_VALIDATION_NEGATION_PREFIXES = ("no", "not", "never", "without", "zero", "nil")


def _validation_outcome_is_failure(check, outcome):
    """True when a validation entry should be reported as a failed check.

    A key phrased as an assertion of something undesirable is inverted: for
    ``credentials_disclosed`` the passing value is False, not True. A key that
    carries its own negation prefix flips back, so
    ``no_document_number_inferred`` passes on True.
    """
    if isinstance(outcome, str):
        return outcome.strip().upper() in _FAIL_STATUSES
    if outcome not in (True, False):
        return False
    tokens = check.lower().split("_")
    negative = any(marker in tokens for marker in _NEGATIVE_VALIDATION_MARKERS)
    if negative and tokens and tokens[0] in _VALIDATION_NEGATION_PREFIXES:
        negative = False
    return outcome is True if negative else outcome is False


def _validation_failures(results):
    """Return validation checks that are false and block design-grade use."""
    failures = []
    validation = results.get("validation", {}) if results else {}
    for check, outcome in validation.items():
        if _validation_outcome_is_failure(check, outcome):
            failures.append(_label_from_key(check))
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
        parts.append(_t("Source systems read") + ":")
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
        if not any(k in combined for k in ("assumptions", "data_gaps", "gaps")):
            # Free-form {topic: text} register, the shape most task folders write.
            assumptions.extend({"assumption": "{}: {}".format(k.replace("_", " "), v)}
                               for k, v in combined.items() if v)
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
        parts.append(_t("Assumptions the results depend on") + ":")
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
        parts.append(_t("Information sought but not available, and what was assumed "
                        "in its place") + ":")
        parts.append("")
        table = [
            "| # | Information sought | Source | Status | Assumed instead | Effect if wrong |",
            "|---|---|---|---|---|---|",
        ]
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


def format_reproducibility_text(results):
    """Format results.json ``reproducibility`` as markdown for the appendix.

    Accepts a string, a list of steps, or a dict with ``environment``,
    ``steps``, ``checks`` and ``what_if`` (each a string or list).
    """
    repro = (results or {}).get("reproducibility")
    if not repro:
        return ""
    if isinstance(repro, str):
        return repro
    if isinstance(repro, list):
        return "\n".join("{}. {}".format(i, s) for i, s in enumerate(repro, 1))
    lines = []
    if repro.get("summary"):
        lines.extend([repro["summary"], ""])
    for key, title, numbered in (("environment", "Software and environment", False),
                                 ("steps", "Steps", True),
                                 ("checks", "Checks after a rerun", False),
                                 ("what_if", "Changing a case", False)):
        items = repro.get(key)
        if not items:
            continue
        lines.append("**{}**".format(_t(title)))
        if isinstance(items, str):
            lines.append(items)
        else:
            lines.extend("{} {}".format("{}.".format(i) if numbered else "-",
                                        _format_list_item_text(item))
                         for i, item in enumerate(items, 1))
        lines.append("")
    return "\n".join(lines).strip()


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


# A trailing run of 1-4 "Capital-letter + period" groups (e.g. "J.M.", "R.")
# is almost always initials in a person's name, not a sentence boundary.
_INITIALS_RE = re.compile(r'(?:^|\s)(?:[A-Z]\.){1,4}$')


def _split_sentences(text):
    """Split prose into sentences, keeping abbreviations and initials intact.

    Without the initials check, "benchmarked against the J.M. Campbell
    correlation" splits into two paragraphs at "J.M.", stranding "Campbell
    correlation." as an orphan one-line paragraph.
    """
    pieces = re.split(r'(?<=[.!?])\s+(?=[A-Z0-9\u00c6\u00d8\u00c5"\'(\[])', text)
    merged = []
    for piece in pieces:
        if merged and (merged[-1].lower().endswith(_SENTENCE_ABBREVIATIONS)
                       or _INITIALS_RE.search(merged[-1])):
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


_LIST_LINE_RE = re.compile(r"^\s*(?:[-*+\u2022]\s|\d+[.)]\s)")
_INLINE_CODE_RE = re.compile(r"`([^`\n]+)`")


def _is_line_structured(block):
    """True when a block's own line breaks carry meaning (list or table)."""
    lines = [line for line in block.split("\n") if line.strip()]
    if len(lines) < 2:
        return False
    structured = sum(1 for line in lines
                     if _LIST_LINE_RE.match(line) or line.strip().startswith("|"))
    return structured >= len(lines) / 2.0


def _prose_to_html(text):
    """Render plain prose as HTML paragraphs using the same splitting rules.

    Source markdown is hard-wrapped, and markdown treats a single newline as a
    space. Only a list or table block keeps its line breaks; wrapped prose is
    rejoined so sentences are not split mid-clause. Inline `code` spans become
    <code>, which is how tag and document numbers are written in a task spec.
    """
    out = []
    for para in _body_paragraphs(text):
        if _is_line_structured(para):
            body = "<br>".join(line.strip() for line in para.split("\n")
                               if line.strip())
        else:
            body = " ".join(line.strip() for line in para.split("\n")
                            if line.strip())
        out.append("<p>{}</p>".format(_INLINE_CODE_RE.sub(r"<code>\1</code>", body)))
    return "".join(out)


def _benchmark_tests(results):
    """Normalize legacy ``tests`` lists and named benchmark mappings for all outputs."""
    benchmark = (results or {}).get("benchmark_validation") or {}
    if not isinstance(benchmark, dict):
        return []
    if isinstance(benchmark.get("tests"), list):
        entries = (("Test {}".format(index), value)
                   for index, value in enumerate(benchmark["tests"], 1))
    else:
        entries = benchmark.items()
    tests = []
    for name, value in entries:
        if not isinstance(value, dict):
            continue
        test = dict(value)
        test.setdefault("parameter", name.replace("_", " ").title())
        if "pass" not in test:
            status = str(test.get("status", "")).upper()
            if status in ("PASS", "FAIL"):
                test["pass"] = status == "PASS"
        tests.append(test)
    return tests


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
        for label, value, unit, _note in _flatten_key_results(results["key_results"])[:5]:
            value_text = _fmt_cell(value)
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
        failures.extend("benchmark: {}".format(test["parameter"])
                        for test in _benchmark_tests(results) if test.get("pass") is False)
        if failures:
            parts.append("Validation checks requiring attention: {}.".format(
                ", ".join(failures)))
        else:
            parts.append("Validation checks did not flag design blockers.")
    benchmarks = _benchmark_tests(results)
    if benchmarks:
        passed = sum(test.get("pass") is True for test in benchmarks)
        parts.append("{} of {} benchmark comparisons passed.".format(passed, len(benchmarks)))
    if results and results.get("risk_evaluation", {}).get("overall_risk_level"):
        parts.append("Overall project risk: {}.".format(
            results["risk_evaluation"]["overall_risk_level"]))
    if results and results.get("conclusions") and not _is_placeholder_text(results["conclusions"]):
        parts.append(results["conclusions"])
    return "\n\n".join(parts)


def check_report_consistency(results):
    """Check results.json for internal contradictions and inconsistencies.

    Returns a list of dicts with keys: severity, message, fix_type.
    severity: 'ERROR', 'WARNING', or 'INFO'.
    fix_type: 'text' (auto-fixable in report), 'calculation' (needs
    agent to re-run notebook), or 'none' (informational).
    """
    if not results:
        return [{"severity": "INFO", "message": "No results.json loaded; all sections use placeholders.", "fix_type": "none"}]

    issues = []

    # --- 1. Benchmark failures vs optimistic conclusions ---
    bmk_tests = _benchmark_tests(results)
    n_fail = sum(1 for t in bmk_tests if t.get("pass") is False)
    n_total = len(bmk_tests)
    conclusions = results.get("conclusions", "")

    if n_fail > 0:
        # Check if any failure has large deviation (>20%) => calculation fix
        large_devs = []
        for t in bmk_tests:
            if t.get("pass") is False:
                dev_pct = t.get("deviation_pct")
                if dev_pct is not None and abs(dev_pct) > 20:
                    large_devs.append(t)

        failure_words = ["fail", "exceed", "deviation", "caution", "attention",
                         "issue", "concern", "discrepanc", "not met"]
        conc_lower = conclusions.lower()
        acknowledges = any(w in conc_lower for w in failure_words)

        if large_devs:
            params = [t.get("parameter", "?") for t in large_devs]
            issues.append({
                "severity": "ERROR",
                "message": "Benchmark deviation >20% for: {}. Model may need "
                           "retuning or different EOS/parameters.".format(
                               ", ".join(params)),
                "fix_type": "calculation",
                "action": "Re-run benchmark notebook with revised model "
                          "parameters (check EOS, BIPs, component characterization).",
                "parameters": params,
            })

        if not acknowledges:
            safe_words = ["safe", "confirm", "acceptable", "satisfactor",
                          "within limits", "meets"]
            if any(w in conc_lower for w in safe_words):
                issues.append({
                    "severity": "ERROR",
                    "message": "Conclusions say \'{}\' but {}/{} benchmark tests FAILED. "
                               "Conclusions must acknowledge benchmark failures or explain "
                               "why they are acceptable.".format(
                                   conclusions[:80], n_fail, n_total),
                    "fix_type": "text",
                })
            else:
                issues.append({
                    "severity": "WARNING",
                    "message": "{}/{} benchmark tests failed. Consider addressing this "
                               "in the conclusions.".format(n_fail, n_total),
                    "fix_type": "text",
                })

    # --- 2. Validation failures vs optimistic conclusions ---
    validation = results.get("validation", {})
    val_failures = []
    for check, outcome in validation.items():
        if outcome is False or (isinstance(outcome, str)
                                and outcome.strip().upper() in _FAIL_STATUSES):
            val_failures.append(check)
        elif (check.endswith(("_pct", "_percent"))
              and isinstance(outcome, (int, float)) and outcome >= 5.0):
            val_failures.append("{} ({})".format(check, outcome))
    if val_failures:
        conc_lower = conclusions.lower()
        safe_words = ["safe", "confirm", "acceptable", "satisfactor",
                      "all.*pass", "within limits"]
        if any(w in conc_lower for w in safe_words):
            issues.append({
                "severity": "ERROR",
                "message": "Conclusions claim safety/acceptability but validation checks "
                           "show issues: {}. Revise conclusions or explain why failures "
                           "are acceptable.".format(", ".join(val_failures)),
                "fix_type": "text",
            })
        # Check if validation error is large enough to need recalculation
        large_val = [value for key, value in validation.items()
                     if key.endswith(("_pct", "_percent"))
                     and isinstance(value, (int, float)) and value >= 10.0]
        if large_val:
            issues.append({
                "severity": "ERROR",
                "message": "Validation error >=10% detected ({}). Model accuracy "
                           "may be insufficient — consider retuning.".format(
                               ", ".join(val_failures)),
                "fix_type": "calculation",
                "action": "Re-run main analysis notebook with tighter convergence "
                          "tolerances or revised model setup.",
            })

    # --- 3. High risk level vs unconditionally positive conclusions ---
    risk_eval = results.get("risk_evaluation", {})
    overall_risk = risk_eval.get("overall_risk_level", "").lower()
    risks = risk_eval.get("risks", [])
    high_risks = [r for r in risks if "high" in r.get("risk_level", "").lower()
                  or "very high" in r.get("risk_level", "").lower()]
    if high_risks:
        conc_lower = conclusions.lower()
        caution_words = ["risk", "mitigat", "caution", "monitor", "contingenc",
                         "condition", "subject to", "provided that"]
        has_caution = any(w in conc_lower for w in caution_words)
        if not has_caution and any(
            w in conc_lower for w in ["safe", "confirm", "recommend proceed",
                                      "no concern"]
        ):
            issues.append({
                "severity": "WARNING",
                "message": "{} high-risk items identified ({}), but conclusions don\'t "
                           "mention risk mitigation. Consider adding caveats.".format(
                               len(high_risks),
                               ", ".join(r.get("description", "") for r in high_risks[:3])),
                "fix_type": "text",
            })

    # --- 4. High probability of negative outcome vs positive conclusions ---
    uncertainty = results.get("uncertainty", {})
    prob_neg = uncertainty.get("prob_negative_pct")
    if prob_neg is not None and prob_neg > 25:
        conc_lower = conclusions.lower()
        if any(w in conc_lower for w in ["safe", "confirm", "favourable",
                                          "recommend proceed"]):
            issues.append({
                "severity": "WARNING",
                "message": "Probability of unfavourable outcome is {:.1f}% (>25%). "
                           "Conclusions should acknowledge the significant downside "
                           "risk.".format(prob_neg),
                "fix_type": "text",
            })

    # --- 5. Discussion recommendations contradict conclusions ---
    discussions = results.get("figure_discussion", [])
    recs = [d.get("recommendation", "") for d in discussions
            if d.get("recommendation")]
    for rec in recs:
        rec_lower = rec.lower()
        conc_lower = conclusions.lower()
        # Check for direct contradictions
        if "do not proceed" in rec_lower and "proceed" in conc_lower:
            issues.append({
                "severity": "ERROR",
                "message": "Discussion recommends \'do not proceed\' but conclusions "
                           "say \'proceed\'. Resolve the contradiction.",
                "fix_type": "text",
            })
        if "further study" in rec_lower or "sensitivity" in rec_lower:
            if "no further" in conc_lower:
                issues.append({
                    "severity": "WARNING",
                    "message": "Discussion recommends further study/sensitivity analysis "
                               "but conclusions dismiss it. Ensure consistency.",
                    "fix_type": "text",
                })

    # --- 6. Missing critical sections ---
    if not results.get("key_results"):
        issues.append({
            "severity": "WARNING",
            "message": "No key_results in results.json. The Results section will be empty.",
            "fix_type": "calculation",
            "action": "Run the main analysis notebook and populate key_results in results.json.",
        })
    if not results.get("conclusions") or results["conclusions"].startswith("["):
        issues.append({
            "severity": "WARNING",
            "message": "Conclusions are still a placeholder. Fill in conclusions "
                       "before finalising the report.",
            "fix_type": "text",
        })
    if not results.get("approach") or results["approach"].startswith("["):
        issues.append({
            "severity": "WARNING",
            "message": "Approach section is still a placeholder.",
            "fix_type": "text",
        })

    # --- 7. Numerical consistency: key_results referenced in discussions ---
    key_results = results.get("key_results", {})
    for disc in discussions:
        obs = disc.get("observation", "")
        linked = disc.get("linked_results", [])
        for link_key in linked:
            if link_key in key_results:
                expected_val = key_results[link_key]
                if isinstance(expected_val, float):
                    # Check if the observation mentions a consistent number.
                    # Small magnitudes are usually written in scientific notation in
                    # prose, so accept those renderings too; and do not accept the
                    # degenerate "0.0" that %.1f produces for them, which would match
                    # almost any text.
                    val_strs = [
                        "{:.4g}".format(expected_val),
                        "{:.3g}".format(expected_val),
                        "{:.2g}".format(expected_val),
                        str(int(expected_val)) if expected_val == int(expected_val) else "",
                    ]
                    if abs(expected_val) >= 0.1:
                        val_strs.append("{:.1f}".format(expected_val))
                    for prec in (1, 2, 3):
                        sci = "{:.{p}e}".format(expected_val, p=prec)
                        mant, _, exp = sci.partition("e")
                        exp_i = int(exp)
                        val_strs.extend([
                            sci,
                            "{}e{:+03d}".format(mant, exp_i),
                            "{}e{}".format(mant, exp_i),
                            "{}E{:+03d}".format(mant, exp_i),
                        ])
                    val_strs = [v for v in val_strs if v]
                    # nb/de/fr reports write 20,1 for 20.1
                    val_strs.extend([v.replace(".", ",") for v in val_strs if "." in v])
                    if obs and not any(v in obs for v in val_strs):
                        issues.append({
                            "severity": "WARNING",
                            "message": "Discussion links to \'{}\' (value={}) but "
                                       "observation text doesn\'t mention this value. "
                                       "Verify numerical consistency.".format(
                                           link_key, expected_val),
                            "fix_type": "calculation",
                            "action": "Verify the value of \'{}\' in the notebook output "
                                      "and update either key_results or the discussion "
                                      "observation text.".format(link_key),
                        })

    # --- 8. Risk level vs uncertainty probability alignment ---
    if prob_neg is not None and overall_risk:
        if prob_neg > 40 and overall_risk in ("low",):
            issues.append({
                "severity": "WARNING",
                "message": "Probability of negative outcome is {:.0f}% but overall risk "
                           "is \'Low\'. These seem inconsistent.".format(prob_neg),
                "fix_type": "text",
            })
        if prob_neg < 5 and overall_risk in ("high", "very high"):
            issues.append({
                "severity": "INFO",
                "message": "Probability of negative outcome is only {:.0f}% but overall "
                           "risk is \'{}\'. Consider whether the risk rating is driven "
                           "by non-economic factors.".format(prob_neg, overall_risk.title()),
                "fix_type": "none",
            })

    if not issues:
        issues.append({"severity": "INFO", "message": "No consistency issues found.", "fix_type": "none"})

    return issues


def print_consistency_report(issues):
    """Print the consistency check results with visual formatting."""
    errors = [i for i in issues if i["severity"] == "ERROR"]
    warnings = [i for i in issues if i["severity"] == "WARNING"]
    infos = [i for i in issues if i["severity"] == "INFO"]

    text_fixes = [i for i in issues if i.get("fix_type") == "text"]
    calc_fixes = [i for i in issues if i.get("fix_type") == "calculation"]

    print("  ===== Report Consistency Check =====")
    if errors:
        for i in errors:
            tag = " [CALC-FIX]" if i.get("fix_type") == "calculation" else " [TEXT-FIX]" if i.get("fix_type") == "text" else ""
            print("  [ERROR{}] {}".format(tag, i["message"]))
    if warnings:
        for i in warnings:
            tag = " [CALC-FIX]" if i.get("fix_type") == "calculation" else " [TEXT-FIX]" if i.get("fix_type") == "text" else ""
            print("  [WARNING{}] {}".format(tag, i["message"]))
    if infos and not errors and not warnings:
        for i in infos:
            print("  [OK] {}".format(i["message"]))

    if errors:
        print("")
        print("  {} ERROR(s) found. Fix these before distributing the report.".format(
            len(errors)))
        print("  Errors indicate contradictions that undermine report credibility.")
    elif warnings:
        print("")
        print("  {} WARNING(s) found. Review before finalising.".format(
            len(warnings)))
    else:
        print("  Report is internally consistent.")

    if text_fixes:
        print("  {} text fix(es) require review before distribution.".format(len(text_fixes)))
    if calc_fixes:
        print("  {} calculation fix(es) need agent re-run (see fixes_needed.json).".format(
            len(calc_fixes)))

    print("  ====================================")
    return len(errors)



def auto_problem_description(results, task_spec):
    """Generate a problem description from task_spec.md sections."""
    parts = []
    for heading in ["Objective", "Description", "Problem Statement", "Task Description", "Background"]:
        text = extract_spec_section(task_spec, heading)
        if text:
            parts.append(text)
            break
    envelope = extract_spec_section(task_spec, "Operating Envelope")
    # A markdown table cannot be flattened into prose without becoming a line of
    # pipes; Scope and Standards already renders the same section as a table.
    if envelope and "|" not in envelope:
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
        if produces and not glob.glob(_resolve_task_path(produces)):
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
    except (OSError, ValueError) as error:
        print("NOTE: could not persist the report environment in results.json "
              "({}).".format(error), file=sys.stderr)
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
    """Convert inline markdown (bold, `code`) to HTML."""
    text = re.sub(r"\*\*(.+?)\*\*", r"<strong>\1</strong>", text)
    return _INLINE_CODE_RE.sub(r"<code>\1</code>", text)


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
            html_parts.append("<h3>{}</h3>".format(
                _md_inline(line.strip().rstrip(":").strip())))
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
            # "Applicable Standards:" is a label in the spec, not heading text.
            _add_heading(doc, line.strip().rstrip(":").strip(), level=2)
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


# Matches **bold** spans and $...$ inline maths (but not $$...$$ display
# maths, which is a separate results.json-driven code path) in one pass, so
# the two kinds of markup can be interleaved in a single sentence.
_INLINE_TOKEN_RE = re.compile(
    r"(\*\*.+?\*\*|`[^`\n]+`|\$(?!\$)[^$\n]+?\$(?!\$))")
_INLINE_MATH_CACHE = {}


def _inline_math_image_path(latex_str, font_pt):
    """Render (and cache) a small inline-maths PNG; returns the path or None."""
    key = (latex_str, font_pt)
    if key in _INLINE_MATH_CACHE:
        return _INLINE_MATH_CACHE[key]
    eq_img_dir = os.path.join(REPORT_DIR, "_eq_images")
    if not os.path.exists(eq_img_dir):
        os.makedirs(eq_img_dir)
    digest = hashlib.md5(latex_str.encode("utf-8")).hexdigest()[:12]
    path = os.path.join(eq_img_dir, "inline_{}.png".format(digest))
    ok = render_equation_to_image(latex_str, path, font_pt=font_pt, inline=True)
    _INLINE_MATH_CACHE[key] = path if ok else None
    return _INLINE_MATH_CACHE[key]


def _add_inline_math_run(paragraph, latex_str, font_pt=None):
    """Insert a small inline-maths image sized to sit on the text line."""
    font_pt = font_pt or INLINE_EQ_FONT_PT
    image_path = _inline_math_image_path(_sanitize_equation_latex(latex_str), font_pt)
    if not image_path:
        run = paragraph.add_run(_latex_fallback_text(latex_str))
        run.italic = True
        return
    run = paragraph.add_run()
    # Natural size at EQ_RENDER_DPI reproduces exactly the fixed ascent/descent
    # window the image was cropped to, so consecutive inline equations share
    # one baseline instead of each floating at their own ink-tight height.
    size = _png_pixel_size(image_path)
    if size and size[1] > 0:
        run.add_picture(image_path, height=Inches(size[1] / float(EQ_RENDER_DPI)))
    else:
        run.add_picture(image_path, height=Pt(font_pt))


def _add_bold_runs(paragraph, text):
    """Add text with **bold** spans and $...$ inline maths as separate runs."""
    for part in _INLINE_TOKEN_RE.split(text):
        if not part:
            continue
        if part.startswith("**") and part.endswith("**"):
            run = paragraph.add_run(part[2:-2])
            run.bold = True
        elif part.startswith("`") and part.endswith("`") and len(part) > 2:
            run = paragraph.add_run(part[1:-1])
            run.font.name = "Consolas"
        elif part.startswith("$") and part.endswith("$") and len(part) > 2:
            _add_inline_math_run(paragraph, part[1:-1])
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
        return "{} {}: {}".format(_t("Figure"), fig_index, captions[fig_name])
    for entry in (results or {}).get("figure_discussion", []) or []:
        if isinstance(entry, dict) and os.path.basename(
                str(entry.get("figure", ""))) == fig_name:
            title = str(entry.get("caption") or entry.get("title") or "").strip()
            if title:
                return "{} {}: {}".format(_t("Figure"), fig_index, title)
    # Auto-generate from filename; drop an ordering prefix such as "fig03_".
    stem = re.sub(r"^fig(ure)?[\s_-]*\d+[\s_-]*", "",
                  fig_name.rsplit(".", 1)[0], flags=re.IGNORECASE)
    stem = stem or fig_name.rsplit(".", 1)[0]
    auto = stem.replace("_", " ").replace("-", " ").strip()
    auto = auto[:1].upper() + auto[1:]
    return "{} {}: {}".format(_t("Figure"), fig_index, auto)


# A degree sign written through a codepage-437 console round-trips as the
# U+2591 light-shade block ("°C" becomes "░C"); nothing in engineering
# notation legitimately uses that glyph, so repairing it is always safe.
_MOJIBAKE_DEGREE = "\u2591"
# matplotlib's mathtext only knows the long form of these comparison
# operators, not the common LaTeX aliases authors actually type.
_MATHTEXT_ALIASES = [
    (re.compile(r"\\le\b"), r"\\leq"),
    (re.compile(r"\\ge\b"), r"\\geq"),
    (re.compile(r"\\ne\b"), r"\\neq"),
]


def _sanitize_equation_latex(latex_str):
    """Repair known encoding corruption and LaTeX/mathtext symbol mismatches."""
    text = str(latex_str or "").replace(_MOJIBAKE_DEGREE, "\u00b0")
    for pattern, replacement in _MATHTEXT_ALIASES:
        text = pattern.sub(replacement, text)
    return text


# Best-effort LaTeX-to-plain-text approximation for the rare equation mathtext
# cannot parse at all (e.g. a \begin{cases} piecewise definition -- mathtext
# has no support for LaTeX environments). Order matters: fractions and text
# runs are unwrapped before the catch-all \command stripper at the end.
_LATEX_FALLBACK_SUBS = [
    (re.compile(r"\\begin\{[a-zA-Z*]+\}"), ""),
    (re.compile(r"\\end\{[a-zA-Z*]+\}"), ""),
    (re.compile(r"\\\\"), "; "),
    (re.compile(r"\\d?frac\{([^{}]*)\}\{([^{}]*)\}"), r"(\1)/(\2)"),
    (re.compile(r"\\text\{([^{}]*)\}"), r"\1"),
    (re.compile(r"\\mathrm\{([^{}]*)\}"), r"\1"),
    (re.compile(r"\\left|\\right"), ""),
    (re.compile(r"\\quad|\\qquad|\\,|\\;|\\!"), " "),
    (re.compile(r"\\cdot"), "\u00b7"), (re.compile(r"\\times"), "\u00d7"),
    (re.compile(r"\\leq"), "\u2264"), (re.compile(r"\\geq"), "\u2265"),
    (re.compile(r"\\neq"), "\u2260"),
    (re.compile(r"\\rightarrow|\\to"), "\u2192"),
    (re.compile(r"\\pi"), "\u03c0"), (re.compile(r"\\Delta"), "\u0394"),
    (re.compile(r"\\alpha"), "\u03b1"), (re.compile(r"\\beta"), "\u03b2"),
    (re.compile(r"\\gamma"), "\u03b3"), (re.compile(r"\\eta"), "\u03b7"),
    (re.compile(r"\\rho"), "\u03c1"), (re.compile(r"\\mu"), "\u03bc"),
    (re.compile(r"\\theta"), "\u03b8"), (re.compile(r"\\omega"), "\u03c9"),
    (re.compile(r"\\phi"), "\u03c6"),
    (re.compile(r"&"), " \u2192 "),
    (re.compile(r"\\[a-zA-Z]+"), ""),
    (re.compile(r"[{}]"), ""),
    (re.compile(r"\s+"), " "),
]


def _latex_fallback_text(latex_str):
    """Readable approximation for an equation matplotlib cannot render.

    Used only when rendering fails; a stripped-down plain-text approximation
    reads as an engineer's shorthand, whereas the raw backslash-and-brace
    LaTeX source reads as a bug in a formal report.
    """
    text = _sanitize_equation_latex(latex_str)
    for pattern, replacement in _LATEX_FALLBACK_SUBS:
        text = pattern.sub(replacement, text)
    return text.strip()


def get_equations(results):
    """Get equations from results.json. Returns list of {label, latex}."""
    if not results:
        return []
    cleaned = []
    for eq in results.get("equations", []):
        if isinstance(eq, dict) and eq.get("latex"):
            eq = dict(eq, latex=_sanitize_equation_latex(eq["latex"]))
        cleaned.append(eq)
    return cleaned


# An inline equation's own ink extent varies with how many sub/superscripts
# or fractions it carries, so a per-equation tight vertical crop would leave
# every inline expression sitting at a different height on the line (Word
# aligns an inline picture's BOTTOM edge with the text baseline). Cropping to
# a fixed ascent/descent window instead -- in font-size units, generous enough
# for a simple fraction or subscript -- keeps that bottom edge, and so the
# apparent baseline, the same distance from every equation's own baseline.
INLINE_EQ_ASCENT_EM = 1.30
INLINE_EQ_DESCENT_EM = 0.50


def render_equation_to_image(latex_str, output_path, font_pt=None, inline=False):
    """Render a LaTeX equation to a PNG sized for font_pt in the document.

    Rendered at font_pt (default EQ_FONT_PT) and EQ_RENDER_DPI so that placing
    the image at its natural size (pixels / EQ_RENDER_DPI inches) reproduces
    exactly that point size. A display equation (inline=False) is cropped
    tight to its own ink on every side, which is correct for a free-standing,
    centered equation. ``inline=True`` instead crops to a fixed ascent/descent
    window so consecutive inline equations line up on the same baseline; see
    INLINE_EQ_ASCENT_EM/INLINE_EQ_DESCENT_EM. Returns True if the image was
    created, False otherwise.
    """
    if not HAS_MATPLOTLIB:
        return False
    size_pt = font_pt or EQ_FONT_PT
    text = "${}$".format(_sanitize_equation_latex(latex_str))
    try:
        if inline:
            ascent_in = INLINE_EQ_ASCENT_EM * size_pt / 72.0
            descent_in = INLINE_EQ_DESCENT_EM * size_pt / 72.0
            fig = plt.figure(figsize=(10.0, ascent_in + descent_in))
            fig.text(0.5, descent_in / (ascent_in + descent_in), text,
                      fontsize=size_pt, ha="center", va="baseline",
                      math_fontfamily="cm")
            renderer = fig.canvas.get_renderer()
            tight = fig.get_tightbbox(renderer)
            fixed = Bbox.from_extents(tight.x0, 0.0, tight.x1,
                                       ascent_in + descent_in)
            fig.savefig(output_path, dpi=EQ_RENDER_DPI, bbox_inches=fixed,
                        pad_inches=0.02, facecolor="white", edgecolor="none")
        else:
            fig = plt.figure(figsize=(8, 1.2))
            fig.text(0.5, 0.5, text, fontsize=size_pt, ha="center", va="center",
                      math_fontfamily="cm")
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


def _raise_run(run, half_points):
    """Raise a run above the baseline by the given number of half-points."""
    if half_points > 0:
        run._r.get_or_add_rPr().append(parse_xml(
            '<w:position {} w:val="{}"/>'.format(nsdecls("w"), int(half_points))))


def _add_display_equation(doc, image_path, label, max_width_in):
    """Set a display equation centred, with its number right-aligned: (n).

    The label goes on a short lead-in line above; the number is a Word SEQ
    field raised to the equation's vertical centre, as in a typeset paper.
    """
    measure = _text_width_in(doc)
    if label:
        lead = doc.add_paragraph()
        run = lead.add_run(_strip_caption_prefix(label))
        run.italic = True
        run.font.size = Pt(CAPTION_PT)
        run.font.color.rgb = RGBColor(90, 90, 90)
        lead.paragraph_format.space_before = Pt(6)
        lead.paragraph_format.space_after = Pt(0)
        lead.paragraph_format.keep_with_next = True
    size = _png_pixel_size(image_path)
    natural_in = size[0] / float(EQ_RENDER_DPI) if size and size[0] else 3.0
    # Leave room either side so the number never collides with the maths.
    width_in = min(natural_in, max_width_in, measure - 1.4)
    height_pt = (width_in * size[1] / float(size[0]) * 72.0
                 if size and size[0] else EQ_FONT_PT * 1.5)
    paragraph = doc.add_paragraph()
    fmt = paragraph.paragraph_format
    fmt.space_before = Pt(2)
    fmt.space_after = Pt(8)
    fmt.keep_together = True
    fmt.tab_stops.add_tab_stop(Inches(measure / 2.0), WD_TAB_ALIGNMENT.CENTER)
    fmt.tab_stops.add_tab_stop(Inches(measure), WD_TAB_ALIGNMENT.RIGHT)
    paragraph.add_run("\t")
    paragraph.add_run().add_picture(image_path, width=Inches(width_in))
    first_number_run = len(paragraph.runs)
    paragraph.add_run("\t(")
    _add_seq_field(paragraph, _t("Equation"))
    paragraph.add_run(")")
    # Word re-formats an updated field result from its field-code run, so every
    # run of the number (codes included) must carry the raise, not just the text.
    for run in paragraph.runs[first_number_run:]:
        _raise_run(run, height_pt - 0.7 * BODY_PT)
    return paragraph


def _add_equation_fallback_paragraph(doc, label, latex):
    """Show an unrenderable display equation as marked, readable text.

    Presenting it as ordinary body prose would read as a typo; the italic,
    muted styling and the trailing note make clear it is a known gap rather
    than broken output.
    """
    paragraph = doc.add_paragraph()
    prefix = "{}: ".format(label) if label else ""
    run = paragraph.add_run(prefix + _latex_fallback_text(latex))
    run.italic = True
    run.font.color.rgb = RGBColor(90, 90, 90)
    note = paragraph.add_run("  ({})".format(
        _t("automatic equation typesetting unavailable")))
    note.italic = True
    note.font.size = Pt(CAPTION_PT)
    note.font.color.rgb = RGBColor(140, 140, 140)
    return paragraph


def _add_figure_picture(doc, image_path):
    """Place a figure across the measure, shrunk if it would not fit the page.

    A fixed picture width makes a tall figure overflow the printable height,
    which Word resolves by pushing it onto its own page and clipping what is
    left. Scaling on the native aspect ratio keeps every figure on one page.
    """
    max_width = _text_width_in(doc)
    max_height = max(2.0, _text_height_in(doc) - FIGURE_CAPTION_ALLOWANCE_IN)
    width_in = max_width
    size = _png_pixel_size(image_path)
    if size and size[0] > 0 and size[1] > 0:
        height_in = max_width * size[1] / float(size[0])
        if height_in > max_height:
            width_in = max_width * max_height / height_in
    doc.add_picture(image_path, width=Inches(width_in))
    picture_para = doc.paragraphs[-1]
    picture_para.alignment = WD_ALIGN_PARAGRAPH.CENTER
    # The caption follows the picture, so the picture must not end a page.
    picture_para.paragraph_format.keep_with_next = True
    return picture_para


_CAPTION_PREFIX = re.compile(r"^\s*(Figure|Table|Equation)\s+\d+\s*[:.\u2013-]\s*",
                             re.IGNORECASE)


def _strip_caption_prefix(text):
    """Drop a leading "Figure 3:" so the Word SEQ field owns the numbering.

    Also strips the translated labels, otherwise a report in another language
    keeps the prefix and Word adds a second one.
    """
    cleaned = _CAPTION_PREFIX.sub("", str(text or "")).strip()
    labels = [_t(label) for label in ("Figure", "Table", "Equation")]
    labels = [label for label in labels if label]
    if labels:
        translated = re.compile(
            r"^\s*({})\s+\d+\s*[:.\u2013-]\s*".format("|".join(
                re.escape(label) for label in labels)), re.IGNORECASE)
        cleaned = translated.sub("", cleaned).strip()
    return cleaned


def _add_seq_field(paragraph, label):
    """Append a { SEQ <label> } field so Word owns the caption numbering."""
    run = paragraph.add_run()
    run._r.append(parse_xml(
        '<w:fldChar {} w:fldCharType="begin"/>'.format(nsdecls("w"))))
    run._r.append(parse_xml(
        '<w:instrText {} xml:space="preserve"> SEQ {} \\* ARABIC </w:instrText>'
        .format(nsdecls("w"), label)))
    run._r.append(parse_xml(
        '<w:fldChar {} w:fldCharType="separate"/>'.format(nsdecls("w"))))
    placeholder = paragraph.add_run(str(_SEQ_COUNTERS.setdefault(label, 0) + 1))
    _SEQ_COUNTERS[label] += 1
    run_end = paragraph.add_run()
    run_end._r.append(parse_xml(
        '<w:fldChar {} w:fldCharType="end"/>'.format(nsdecls("w"))))
    return placeholder


_SEQ_COUNTERS = {}


def _add_caption(doc, label, text, keep_with_next=False):
    """Add a numbered, styled caption that a list of figures/tables can collect.

    The number comes from a Word SEQ field, so inserting a figure renumbers the
    rest of the report instead of leaving the captions to drift out of step.
    """
    text = _strip_caption_prefix(text)
    try:
        paragraph = doc.add_paragraph(style="Caption")
    except KeyError:
        paragraph = doc.add_paragraph()
    paragraph.alignment = WD_ALIGN_PARAGRAPH.CENTER
    paragraph.paragraph_format.keep_with_next = keep_with_next
    paragraph.add_run("{} ".format(label))
    _add_seq_field(paragraph, label)
    if text:
        paragraph.add_run(": {}".format(text))
    for run in paragraph.runs:
        run.font.size = Pt(CAPTION_PT)
        run.font.italic = True
    return paragraph


def _fmt_number(value, sig=4):
    """Format a number the way an engineering report prints one.

    "{:.4g}" turns 370523 into "3.705e+05", which reads as a slip rather than a
    result. Digits are grouped with a non-breaking space per ISO 80000-1 and an
    exponent is kept only where it is genuinely the clearer form.
    """
    try:
        number = float(value)
    except (TypeError, ValueError):
        return str(value)
    if number != number or number in (float("inf"), float("-inf")):
        return str(value)
    if number == 0:
        return "0"
    magnitude = abs(number)
    # An exact count is not a measurement: rounding 370523 records to 370 500
    # reads as a sloppy figure rather than a rounded one.
    if float(number).is_integer() and magnitude < 1e12:
        return "{:,.0f}".format(number).replace(",", THOUSANDS_SEP)
    if magnitude >= 1e7 or magnitude < 1e-4:
        return "{:.{}e}".format(number, max(1, sig - 1))
    rounded = float("{:.{}g}".format(number, sig))
    if abs(rounded) >= 1000:
        return "{:,.0f}".format(rounded).replace(",", THOUSANDS_SEP)
    return "{:.{}g}".format(rounded, sig)


def _fmt_cell(value, sig=4):
    """Format one table cell, leaving non-numeric values untouched."""
    if isinstance(value, bool):
        return str(value)
    if isinstance(value, (int, float)):
        return _fmt_number(value, sig)
    return str(value)


def _label_from_key(name_part):
    """Title-case a snake_case key without destroying acronyms.

    ``.title()`` turns GFC into Gfc and LL into Ll, which reads badly in the
    headline results table. A token that is already all-uppercase is kept.
    """
    words = []
    for word in name_part.split("_"):
        if not word:
            continue
        words.append(word if word.isupper() and len(word) > 1 else word.capitalize())
    return " ".join(words)


def _parse_key_name(key):
    """Parse a key_results key into (label, unit). Splits on last known unit suffix."""
    unit_suffixes = [
        ("_pct", "%"), ("_percent", "%"),
        ("_ppm", "ppm"), ("_ppmv", "ppmv"), ("_ppb", "ppb"),
        ("_l_per_h", "l/h"), ("_l_per_min", "l/min"), ("_l_per_s", "l/s"),
        ("_degC", "\u00b0C"), ("_degc", "\u00b0C"),
        ("_MNOK", "MNOK"), ("_MUSD", "MUSD"), ("_NOK", "NOK"), ("_USD", "USD"),
        ("_days", "days"), ("_years", "years"),
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
        ("_kg_m3", "kg/m³"), ("_kg_Sm3", "kg/Sm³"), ("_g_cm3", "g/cm³"),
        ("_hours", "hours"), ("_hr", "hr"), ("_min", "min"), ("_s", "s"),
        ("_rpm", "rpm"), ("_Hz", "Hz"),
    ]
    # Checked before the suffix table below: single-letter suffixes such as
    # "_s"/"_m"/"_kg" would otherwise swallow the denominator of a
    # "..._num_per_den" key (e.g. "velocity_m_per_s" ending in "_s").
    per_match = re.match(r"^(.*)_([A-Za-z0-9]+)_per_([A-Za-z0-9]+)$", key)
    if per_match:
        name_part, num_token, den_token = per_match.groups()
        unit = "{}/{}".format(_prettify_unit_token(num_token), _prettify_unit_token(den_token))
        return _label_from_key(name_part), unit
    # Sorted longest-suffix-first so a compound suffix (e.g. "_kg_m3") always
    # wins over a shorter suffix it contains (e.g. "_m3"), regardless of the
    # order the table above lists them in. Without this a density key like
    # "..._kg_m3" resolves to unit "m³" with a stray "Kg" left in the label.
    for suffix, unit in sorted(unit_suffixes, key=lambda pair: -len(pair[0])):
        if key.endswith(suffix):
            name_part = key[:len(key) - len(suffix)]
            return _label_from_key(name_part), unit
    return _label_from_key(key), ""


_UNIT_TOKEN_MAP = {
    "kg": "kg", "g": "g", "lb": "lb",
    "m3": "m\u00b3", "m2": "m\u00b2", "m": "m", "mm": "mm", "cm": "cm", "km": "km",
    "sm3": "Sm\u00b3", "ksm3": "kSm\u00b3", "msm3": "MSm\u00b3", "nm3": "Nm\u00b3",
    "s": "s", "sec": "s", "min": "min", "h": "h", "hr": "h", "d": "d", "day": "day",
    "bar": "bar", "bara": "bara", "barg": "barg", "psi": "psi", "psia": "psia",
    "kw": "kW", "mw": "MW", "w": "W", "kj": "kJ", "mj": "MJ", "j": "J",
    "kmol": "kmol", "mol": "mol", "rpm": "rpm", "hz": "Hz",
    "degc": "\u00b0C", "degf": "\u00b0F", "c": "\u00b0C",
}



def _prettify_unit_token(token):
    """Normalize a single unit token parsed out of a ``..._per_...`` key suffix."""
    lookup = _UNIT_TOKEN_MAP.get(token.lower())
    if lookup:
        return lookup
    numeric = re.match(r"^(\d+)([A-Za-z]+)$", token)
    if numeric:
        number, unit = numeric.groups()
        return "{} {}".format(number, _UNIT_TOKEN_MAP.get(unit.lower(), unit))
    return token


def _prettify_declared_unit(unit_str):
    """Superscript/normalize an explicitly declared unit string (e.g. "kSm3/h")."""
    if not unit_str:
        return unit_str
    parts = str(unit_str).split("/")
    pretty = [_prettify_unit_token(part) if re.match(r"^[A-Za-z0-9]+$", part) else part
              for part in parts]
    return "/".join(pretty)


def _leaf_label_and_unit(key_for_leaf, declared_unit=None):
    """Resolve the display label/unit for one leaf, preferring a declared unit."""
    if key_for_leaf is None:
        return "Value", _prettify_declared_unit(declared_unit) or ""
    parsed_label, parsed_unit = _parse_key_name(key_for_leaf)
    unit = _prettify_declared_unit(declared_unit) if declared_unit else parsed_unit
    return parsed_label, unit


_LIST_SUMMARY_THRESHOLD = 8


def _summarize_dict_list(items):
    """Summarize a long list of similarly-shaped dicts (e.g. a transient time
    history) as one compact line instead of exploding every sample into its
    own row.
    """
    keys = []
    for item in items:
        if isinstance(item, dict):
            for sub_key in item:
                if sub_key not in keys:
                    keys.append(sub_key)
    parts = ["{} samples".format(len(items))]
    for sub_key in keys:
        values = [item[sub_key] for item in items
                  if isinstance(item, dict) and isinstance(item.get(sub_key), (int, float))
                  and not isinstance(item.get(sub_key), bool)]
        if values:
            parts.append("{}: {} \u2192 {}".format(
                _label_from_key(sub_key), _fmt_number(min(values)), _fmt_number(max(values))))
    return "; ".join(parts)


def _flatten_key_results(key_results):
    """Flatten a (possibly nested) key_results dict into printable leaf rows.

    Task notebooks sometimes group parameters into nested dicts, e.g.
    ``{"operating_point": {"pressure_barg": {"value": 29.1, "unit": "barg",
    "description": "..."}}}`` instead of the flat ``key -> scalar`` schema.
    Printing ``str(value)`` on those dicts puts raw Python repr straight into
    the report. This walks the structure and yields one ``(label, value,
    unit, note)`` row per leaf, honoring an explicit ``value``/``unit`` pair
    and prefixing nested leaves with their parent group name(s).
    """
    rows = []

    def walk(value, key_for_leaf, path_labels, depth):
        if isinstance(value, dict):
            if "value" in value and not isinstance(value["value"], (dict, list)):
                label_part, unit = _leaf_label_and_unit(key_for_leaf, value.get("unit"))
                label = " \u2013 ".join(path_labels + [label_part]) if path_labels else label_part
                note = value.get("description") or value.get("source") or value.get("basis")
                rows.append((label, value["value"], unit, note))
                return
            if depth >= 5:
                label = " \u2013 ".join(path_labels) or "Value"
                rows.append((label, str(value), "", None))
                return
            group_labels = path_labels + [_label_from_key(key_for_leaf)] if key_for_leaf else path_labels
            for sub_key, sub_val in value.items():
                walk(sub_val, sub_key, group_labels, depth + 1)
            return
        if isinstance(value, (list, tuple)):
            if all(not isinstance(item, (dict, list, tuple)) for item in value):
                label_part, unit = _leaf_label_and_unit(key_for_leaf)
                label = " \u2013 ".join(path_labels + [label_part]) if path_labels else label_part
                if len(value) > _LIST_SUMMARY_THRESHOLD:
                    shown = ", ".join(_fmt_cell(item) for item in value[:3])
                    text = "{}, \u2026 ({} values total)".format(shown, len(value))
                else:
                    text = ", ".join(_fmt_cell(item) for item in value)
                rows.append((label, text, unit, None))
            elif len(value) > _LIST_SUMMARY_THRESHOLD and all(
                    isinstance(item, dict) for item in value):
                # A long list of same-shaped dicts is a time series/sweep, not a
                # set of distinct results; one summary row beats one row per sample.
                label_part = _label_from_key(key_for_leaf) if key_for_leaf else "Value"
                label = " \u2013 ".join(path_labels + [label_part]) if path_labels else label_part
                rows.append((label, _summarize_dict_list(value), "",
                             "Full series retained in results.json"))
            else:
                group_labels = path_labels + [_label_from_key(key_for_leaf)] if key_for_leaf else path_labels
                for index, item in enumerate(value, 1):
                    walk(item, str(index), group_labels, depth + 1)
            return
        label_part, unit = _leaf_label_and_unit(key_for_leaf)
        if isinstance(value, str):
            # A guessed unit suffix (e.g. "..._pct") does not apply once the
            # value is free text that may already state its own units.
            unit = ""
        label = " \u2013 ".join(path_labels + [label_part]) if path_labels else label_part
        rows.append((label, value, unit, None))

    for top_key, top_val in (key_results or {}).items():
        walk(top_val, top_key, [], 1)
    return rows


def format_results_table(results):
    """Format key_results dict as a text table."""
    key_results = results.get("key_results", {})
    if not key_results:
        return "[No key_results in results.json]"
    lines = []
    for label, value, unit, note in _flatten_key_results(key_results):
        val_str = _fmt_cell(value)
        line = "{}: {} {}".format(label, val_str, unit) if unit else "{}: {}".format(label, val_str)
        if note:
            line += " ({})".format(note)
        lines.append(line)
    return "\n".join(lines)


def format_validation_table(results):
    """Format validation checks as a text table."""
    validation = results.get("validation", {})
    if not validation:
        return "[No validation data in results.json]"
    lines = ["Validation Summary:", ""]
    for check, outcome in validation.items():
        label = _label_from_key(check)
        if isinstance(outcome, bool):
            status = "FAIL" if _validation_outcome_is_failure(check, outcome) else "PASS"
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
        label = _label_from_key(check)
        if isinstance(outcome, bool):
            failed = _validation_outcome_is_failure(check, outcome)
            status = "FAIL" if failed else "PASS"
            css_class = ' class="fail"' if failed else ' class="pass"'
        elif isinstance(outcome, (int, float)):
            status = _fmt_number(outcome)
            css_class = ' class="num"'
        else:
            status = str(outcome)
            css_class = {"PASS": ' class="pass"', "FAIL": ' class="fail"'}.get(
                status.strip().upper(), "")
        rows += '<tr><td>{}</td><td{}>{}</td></tr>\n'.format(
            label, css_class, status)
    return (_html_table_caption(_t("Validation checks"))
            + '<table class="validation-table"><thead><tr><th>Check</th>'
            '<th>Result</th></tr></thead><tbody>\n{}</tbody></table>'.format(rows))


def format_results_html(results):
    """Format key_results dict as a styled HTML table with units column."""
    key_results = results.get("key_results", {})
    if not key_results:
        return ""
    flat_rows = _flatten_key_results(key_results)
    has_notes = any(note for _label, _value, _unit, note in flat_rows)
    rows = ""
    for label, value, unit, note in flat_rows:
        rows += '<tr><td>{}</td><td class="num">{}</td><td>{}</td>'.format(
            _html_escape(label), _fmt_cell(value), _html_escape(unit))
        if has_notes:
            rows += '<td>{}</td>'.format(_html_escape(note) if note else "")
        rows += '</tr>\n'
    extra_header = '<th>Source</th>' if has_notes else ""
    return (
        _html_table_caption(_t("Key results"))
        + '<table class="results-table"><thead>'
        '<tr><th>Parameter</th><th>Value</th><th>Unit</th>{}</tr>'
        '</thead><tbody>\n{}</tbody></table>'.format(extra_header, rows)
    )


_HTML_TABLE_COUNTER = {"n": 0}


def _html_table_caption(title):
    """Return a numbered table caption so HTML and Word agree on the numbering."""
    _HTML_TABLE_COUNTER["n"] += 1
    text = _strip_caption_prefix(title)
    label = "{} {}".format(_t("Table"), _HTML_TABLE_COUNTER["n"])
    if text:
        label = "{}: {}".format(label, text)
    return '<p class="table-caption">{}</p>\n'.format(_html_escape(label))


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
        h = _html_table_caption(title)
        h += '<table class="custom-table"><thead><tr>'
        for col in headers:
            h += '<th>{}</th>'.format(col)
        h += '</tr></thead><tbody>\n'
        for row in data_rows:
            h += "<tr>"
            for i, cell in enumerate(row):
                css = ' class="num"' if i > 0 and isinstance(cell, (int, float)) else ""
                h += '<td{}>{}</td>'.format(css, _fmt_cell(cell))
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
        h += '<h3>' + _t('Input Parameter Ranges') + '</h3>\n'
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
        h += '<h3>' + _t('Output Distribution (P10 / P50 / P90)') + '</h3>\n'
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
        h += '<h3>' + _t('Sensitivity Ranking (Tornado)') + '</h3>\n'
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
    source = bv.get("source", "")
    h = '<p>Reference source: {}</p>\n'.format(_html_escape(str(source))) if source else ""
    headers, rows, status_idx = _benchmark_table(results)
    h += '<table class="benchmark-table"><thead><tr>'
    h += "".join("<th>{}</th>".format(_html_escape(str(x))) for x in headers)
    h += '</tr></thead><tbody>\n'
    for row in rows:
        h += '<tr>'
        for index, cell in enumerate(row):
            text = _html_escape(str(cell))
            if index == status_idx:
                css = (' class="pass"' if cell == "PASS"
                       else (' class="fail"' if cell == "FAIL" else ""))
                h += '<td{}><strong>{}</strong></td>'.format(css, text)
            elif index == 0:
                h += '<td><strong>{}</strong></td>'.format(text)
            else:
                h += '<td>{}</td>'.format(text)
        h += '</tr>\n'
    h += '</tbody></table>\n'
    return h


def _fmt_num(value):
    """Format a numeric value for display in tables."""
    # Same formatter as every other table, so 162000.0 reads "162 000", not "1.62e+05".
    if isinstance(value, (int, float)) and not isinstance(value, bool):
        return _fmt_cell(value)
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
        _add_heading(doc, _t("Input Parameter Ranges"), level=2)
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
        _add_heading(doc, _t("Output Distribution (P10 / P50 / P90)"), level=2)
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
        _add_heading(doc, _t("Sensitivity Ranking (Tornado)"), level=2)
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


# (label, accepted keys): results.json files in use write either
# reference_value/neqsim_value or the shorter numeric reference/neqsim.
_BENCHMARK_VALUE_COLUMNS = (
    ("Reference value", ("reference_value", "expected", "reference")),
    ("NeqSim value", ("neqsim_value", "neqsim", "calculated")),
    ("Unit", ("unit",)),
    ("Deviation [%]", ("deviation_pct", "deviation_percent")),
    ("Tolerance [%]", ("tolerance_pct", "tolerance_percent")),
)
_BENCHMARK_TEXT_KEYS = {"parameter", "name", "description", "source",
                        "status", "pass", "points"}


def _benchmark_value_key(test, keys, label):
    """First key of ``keys`` present in the test; a text "reference" is a citation."""
    for key in keys:
        if key not in test:
            continue
        if key == "reference" and isinstance(test[key], str):
            continue
        return key
    return None


def _benchmark_reference_text(test):
    """The citation for a test, when one is given as text."""
    reference = test.get("reference")
    if isinstance(reference, str) and reference.strip():
        return reference
    if test.get("name") and test.get("description"):
        return test["description"]
    return test.get("source") or ""


def _benchmark_table(results):
    """Return (headers, rows, status column index) for the benchmark table.

    Each numeric field gets its own column; flattening them into one
    "Name: ...; Reference Value: ..." string made the table unreadable.
    """
    tests = _benchmark_tests(results)
    value_cols = [(label, keys) for label, keys in _BENCHMARK_VALUE_COLUMNS
                  if any(_benchmark_value_key(t, keys, label) for t in tests)]
    has_reference = any(_benchmark_reference_text(t) for t in tests)
    used_by_test = []
    for test in tests:
        used = set(_BENCHMARK_TEXT_KEYS)
        for label, keys in value_cols:
            key = _benchmark_value_key(test, keys, label)
            if key:
                used.add(key)
        if isinstance(test.get("reference"), str):
            used.add("reference")
        used_by_test.append(used)
    has_notes = any(key not in used for test, used in zip(tests, used_by_test)
                    for key in test)
    headers = ([_t("Test")] + ([_t("Reference")] if has_reference else [])
               + [_t(label) for label, _ in value_cols]
               + [_t("Status")] + ([_t("Notes")] if has_notes else []))
    rows = []
    for test, used in zip(tests, used_by_test):
        status = test.get("status")
        if status is None:
            passed = test.get("pass")
            status = "PASS" if passed is True else ("FAIL" if passed is False else "N/A")
        row = [test.get("name") or test.get("description") or test["parameter"]]
        if has_reference:
            row.append(_benchmark_reference_text(test))
        for label, keys in value_cols:
            key = _benchmark_value_key(test, keys, label)
            row.append(_fmt_cell(test[key]) if key else "")
        row.append(str(status).upper())
        if has_notes:
            row.append("; ".join("{}: {}".format(_label_from_key(key), _fmt_cell(value))
                                 for key, value in test.items() if key not in used))
        rows.append(row)
    return headers, rows, 1 + int(has_reference) + len(value_cols)


def add_benchmark_word_table(doc, results):
    """Add benchmark validation as a styled Word table."""
    bv = results.get("benchmark_validation", {})
    if not bv:
        return
    if bv.get("source"):
        doc.add_paragraph("Reference source: {}".format(bv["source"]))
    headers, data_rows, status_idx = _benchmark_table(results)
    if not data_rows:
        return
    table = add_word_table(doc, headers, data_rows)
    for row in table.rows[1:]:
        cell = row.cells[status_idx]
        text = cell.text.strip()
        for paragraph in cell.paragraphs:
            for run in paragraph.runs:
                run.font.bold = True
                if text == "PASS":
                    run.font.color.rgb = RGBColor(0x28, 0xA7, 0x45)
                elif text == "FAIL":
                    run.font.color.rgb = RGBColor(0xDC, 0x35, 0x45)


def add_word_table(doc, headers, data_rows, col_widths=None, caption=None):
    """Add a professionally styled table to a Word document.

    Args:
        doc: Document object.
        headers: list of column header strings.
        data_rows: list of lists (each inner list = one row of cell values).
        col_widths: optional list of Inches widths per column, rescaled to the
            measure so a template's page size cannot leave the table narrow.
        caption: optional caption text, numbered and placed above the table.
    """
    ncols = len(headers)
    texts = [[str(text) for text in headers]]
    texts += [[_fmt_cell(val) for val in row_data[:ncols]]
              + [""] * max(0, ncols - len(row_data)) for row_data in data_rows]
    margin_in = _TABLE_WIDE_MARGIN_IN if ncols >= 7 else _TABLE_MARGIN_IN
    body_pt, landscape = _plan_table_layout(doc, texts, margin_in, col_widths)
    if landscape:
        _set_body_orientation(doc, landscape=True)
    if caption:
        _add_caption(doc, _t("Table"), caption, keep_with_next=True)
    table = doc.add_table(rows=1, cols=ncols)
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    _set_table_style(table)
    _set_cell_margins(table, margin_in)

    hdr = table.rows[0]
    for i, text in enumerate(texts[0]):
        cell = hdr.cells[i]
        cell.text = text
        _style_cell_text(cell, body_pt, bold=True, color=RGBColor(0xFF, 0xFF, 0xFF))
        cell._tc.get_or_add_tcPr().append(parse_xml(
            '<w:shd {} w:val="clear" w:color="auto" w:fill="2F5496"/>'.format(
                nsdecls('w'))))
        cell.vertical_alignment = WD_CELL_VERTICAL_ALIGNMENT.BOTTOM

    for row_texts in texts[1:]:
        row = table.add_row()
        for i, text in enumerate(row_texts):
            cell = row.cells[i]
            cell.text = text
            _style_cell_text(cell, body_pt)

    _scale_table_to_measure(doc, table, col_widths, body_pt, margin_in)
    _repeat_header_row(table)
    _keep_rows_intact(table)
    _align_numeric_cells(table)

    if landscape:
        _set_body_orientation(doc, landscape=False)
    else:
        spacer = doc.add_paragraph("")
        spacer.paragraph_format.space_after = Pt(4)
    return table


# Word's default cell margin is 0.075 in each side; wide tables get less so the
# digits, not the padding, claim the width.
_TABLE_MARGIN_IN = 0.075
_TABLE_WIDE_MARGIN_IN = 0.045
# Smallest type a portrait table may shrink to before it is set landscape.
_TABLE_PORTRAIT_MIN_PT = 8.0
_TABLE_MIN_PT = 7.0
# Body cells narrower than this are kept on one line ("Test 1", "open (NIP-01)").
_TABLE_NOWRAP_IN = 0.85


def _style_cell_text(cell, font_pt, bold=False, color=None):
    """Set type size and compact spacing; Normal's 6 pt space-after doubles row height."""
    for paragraph in cell.paragraphs:
        fmt = paragraph.paragraph_format
        fmt.space_before = Pt(1.5)
        fmt.space_after = Pt(1.5)
        fmt.line_spacing = 1.0
        fmt.keep_with_next = False
        for run in paragraph.runs:
            run.font.size = Pt(font_pt)
            if bold:
                run.font.bold = True
            if color is not None:
                run.font.color.rgb = color


def _set_cell_margins(table, margin_in):
    """Set the table's default left/right cell margins."""
    tbl_pr = table._tbl.tblPr
    for existing in tbl_pr.findall(qn("w:tblCellMar")):
        tbl_pr.remove(existing)
    twips = int(round(margin_in * 1440))
    tbl_pr.append(parse_xml(
        '<w:tblCellMar {0}><w:top w:w="0" w:type="dxa"/>'
        '<w:left w:w="{1}" w:type="dxa"/><w:bottom w:w="0" w:type="dxa"/>'
        '<w:right w:w="{1}" w:type="dxa"/></w:tblCellMar>'.format(nsdecls("w"), twips)))


def _landscape_measure_in(doc):
    """Printable width of the current section if it were turned landscape."""
    section = doc.sections[-1]
    long_side = max(section.page_width, section.page_height)
    return max(2.0, (long_side - section.left_margin - section.right_margin) / 914400.0)


def _plan_table_layout(doc, texts, margin_in, col_widths=None):
    """Pick the largest type size at which no word or number has to break.

    Returns (font_pt, landscape). A table that does not fit the portrait measure
    even at _TABLE_PORTRAIT_MIN_PT is set on its own landscape page instead of
    splitting "9.961" over three lines.
    """
    ncols = len(texts[0]) if texts else 0
    start_pt = TABLE_PT
    if ncols >= 9:
        start_pt = TABLE_PT - 1.5
    elif ncols >= 7:
        start_pt = TABLE_PT - 1.0
    if col_widths or ncols <= 1:
        return start_pt, False
    pad = 2.0 * margin_in + 0.02
    portrait = _text_width_in(doc)
    section = doc.sections[-1]
    already_landscape = section.page_width > section.page_height

    def fits(font_pt, measure):
        minimums, _natural = _column_extents(texts, font_pt, pad)
        return sum(minimums) <= measure

    font_pt = start_pt
    while font_pt >= _TABLE_PORTRAIT_MIN_PT - 1e-9:
        if fits(font_pt, portrait):
            return font_pt, False
        font_pt -= 0.5
    if already_landscape or REPORT_ORIENTATION == "template":
        font_pt = _TABLE_PORTRAIT_MIN_PT - 0.5
        while font_pt > _TABLE_MIN_PT and not fits(font_pt, portrait):
            font_pt -= 0.5
        return max(font_pt, _TABLE_MIN_PT), False
    landscape = _landscape_measure_in(doc)
    font_pt = start_pt
    while font_pt > _TABLE_MIN_PT and not fits(font_pt, landscape):
        font_pt -= 0.5
    return max(font_pt, _TABLE_MIN_PT), True


def _set_body_orientation(doc, landscape):
    """Start a new section on a fresh page in the requested orientation.

    Headers and footers stay linked to the previous section, so page numbering
    and branding continue unchanged.
    """
    section = doc.add_section(WD_SECTION.NEW_PAGE)
    is_landscape = section.page_width > section.page_height
    if is_landscape != landscape:
        section.page_width, section.page_height = section.page_height, section.page_width
    section.orientation = WD_ORIENT.LANDSCAPE if landscape else WD_ORIENT.PORTRAIT
    section.different_first_page_header_footer = False
    return section


# Approximate advance widths in em for a sans corporate face (Arial/Calibri
# class). A flat average under-sizes "Medium" (two wide m's) and over-sizes
# "Consequence", so column minimums were wrong in both directions.
_GLYPH_EM_WIDE = set("MWmw@%")
_GLYPH_EM_NARROW = set("iljtfrI.,:;'!|()[]-/ ")
_TABLE_BOLD_FACTOR = 1.07
_TABLE_CELL_PAD_IN = 0.17
# A single token longer than this (a URL, a long formula) may break; letting
# it claim its full width would starve every other column.
_TABLE_MAX_TOKEN_IN = 1.6


def _text_width_estimate_in(text, font_pt):
    """Estimated printed width of text in inches at font_pt (bold)."""
    em = 0.0
    for char in text:
        if char in _GLYPH_EM_WIDE:
            em += 0.86
        elif char in _GLYPH_EM_NARROW:
            em += 0.30
        elif char.isupper():
            em += 0.68
        else:
            em += 0.56
    return em * font_pt * _TABLE_BOLD_FACTOR / 72.0
# Only real spaces are break points; "180 000" uses a no-break separator.
_TABLE_BREAK_RE = re.compile(r"[ \t\n]+")


def _column_extents(texts, font_pt, pad_in=_TABLE_CELL_PAD_IN):
    """Return (minimum, natural) widths per column for a text matrix.

    Row 0 is the header, which may wrap between words; a short body cell is
    kept on one line because "Test / 1" reads as two entries.
    """
    ncols = max(len(row) for row in texts) if texts else 0
    minimums, natural = [], []
    for index in range(ncols):
        min_in, natural_in = 0.0, 0.0
        for row_index, row in enumerate(texts):
            if index >= len(row):
                continue
            text = row[index].strip()
            words = [w for w in _TABLE_BREAK_RE.split(text) if w]
            if not words:
                continue
            longest = max(_text_width_estimate_in(w, font_pt) for w in words)
            whole = _text_width_estimate_in(text[:70], font_pt)
            if row_index > 0 and whole <= _TABLE_NOWRAP_IN:
                longest = whole
            min_in = max(min_in, min(longest, _TABLE_MAX_TOKEN_IN))
            natural_in = max(natural_in, whole)
        minimums.append(min_in + pad_in)
        natural.append(max(min_in, natural_in) + pad_in)
    return minimums, natural


def _content_column_widths(table, measure, font_pt=TABLE_PT, pad_in=_TABLE_CELL_PAD_IN):
    """Column widths from content, so no column has to break a word.

    Equal-width columns split "Consequence" into "Consequenc/e" while a
    two-character ID column sits half empty. Every column first gets room for
    its longest word; the rest of the measure goes to the columns whose text
    would otherwise wrap the most.
    """
    texts = [[cell.text for cell in row.cells] for row in table.rows]
    minimums, natural = _column_extents(texts, font_pt, pad_in)
    if sum(natural) <= measure:
        return natural
    spare = measure - sum(minimums)
    if spare <= 0:
        return minimums
    stretch = [want - low for want, low in zip(natural, minimums)]
    total_stretch = sum(stretch) or 1.0
    return [low + spare * extra / total_stretch
            for low, extra in zip(minimums, stretch)]


def _scale_table_to_measure(doc, table, col_widths=None, font_pt=TABLE_PT,
                            margin_in=_TABLE_MARGIN_IN):
    """Lay the table out across the full measure, keeping column proportions.

    Column widths written in absolute inches were sized for a portrait page; on
    any other page they leave the table floating in white space or push it into
    the margin.
    """
    measure = _text_width_in(doc)
    count = len(table.columns)
    if not count:
        return
    if col_widths:
        shares = [float(width.inches) if hasattr(width, "inches") else float(width)
                  for width in col_widths[:count]]
        shares += [sum(shares) / len(shares)] * (count - len(shares))
    else:
        shares = _content_column_widths(table, measure, font_pt, 2.0 * margin_in + 0.02)
    total = sum(shares) or float(count)
    table.autofit = False
    twips_total = 0
    for index, share in enumerate(shares):
        width = Inches(measure * share / total)
        twips_total += int(width.twips)
        table.columns[index].width = width
        for row in table.rows:
            if index < len(row.cells):
                row.cells[index].width = width
    # An "auto" table width lets Word and LibreOffice re-flow a fixed layout.
    tbl_pr = table._tbl.tblPr
    for existing in tbl_pr.findall(qn("w:tblW")):
        tbl_pr.remove(existing)
    tbl_pr.append(parse_xml('<w:tblW {} w:w="{}" w:type="dxa"/>'.format(
        nsdecls("w"), twips_total)))


def add_results_word_table(doc, results):
    """Add key_results as a styled Word table with units column."""
    key_results = results.get("key_results", {})
    if not key_results:
        return
    flat_rows = _flatten_key_results(key_results)
    has_notes = any(note for _label, _value, _unit, note in flat_rows)
    if has_notes:
        headers = ["Parameter", "Value", "Unit", "Source"]
        data_rows = [[label, _fmt_cell(value), unit, note or ""]
                     for label, value, unit, note in flat_rows]
        col_widths = [Inches(2.3), Inches(1.3), Inches(1.1), Inches(2.3)]
    else:
        headers = ["Parameter", "Value", "Unit"]
        data_rows = [[label, _fmt_cell(value), unit] for label, value, unit, _note in flat_rows]
        col_widths = [Inches(3.0), Inches(1.5), Inches(1.5)]
    add_word_table(doc, headers, data_rows,
                   col_widths=col_widths,
                   caption=_t("Key results"))


def add_validation_word_table(doc, results):
    """Add validation checks as a styled Word table."""
    validation = results.get("validation", {})
    if not validation:
        return
    headers = ["Check", "Result"]
    data_rows = []
    for check, outcome in validation.items():
        label = _label_from_key(check)
        if isinstance(outcome, bool):
            status = "FAIL" if _validation_outcome_is_failure(check, outcome) else "PASS"
        elif isinstance(outcome, (int, float)):
            status = _fmt_number(outcome)
        else:
            status = str(outcome)
        data_rows.append([label, status])
    table = add_word_table(doc, headers, data_rows,
                           col_widths=[Inches(4.0), Inches(2.0)],
                           caption=_t("Validation checks"))
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
        # A data table titled with Heading 2 lands in the table of contents as
        # if it were a chapter; a numbered caption is what it actually is.
        add_word_table(doc, headers, data_rows, caption=title or None)


# ── Analytical depth (the nine depth moves) ──────────────
# A report can pass every hygiene gate and still only restate its source
# document. These keys carry the analysis that separates an engineering answer
# from a summary; see the neqsim-professional-reporting skill, Principle 0.
DEPTH_MOVES = (
    ("contributor_ranking",
     "Contributors ranked on a common basis",
     "Which effects actually carry the result, largest first."),
    ("source_recommendation_assessment",
     "Verdict on each source recommendation",
     "Supported, supported with correction, or challenged \u2014 with the basis."),
    ("ruled_out",
     "Hypotheses ruled out quantitatively",
     "What was excluded, by which test, and with how much margin."),
    ("robustness",
     "Robustness and crossover",
     "How far an input can move before the conclusion flips."),
    ("conservatism",
     "Direction of each conservatism",
     "Whether each assumption bounds the answer from above or below."),
    ("discriminating_test",
     "Cheapest discriminating test",
     "The one measurement that would separate the surviving explanations."),
    ("evidence_against",
     "Evidence that does not fit",
     "Observations the accepted explanation does not account for."),
)


def _depth_entries(results):
    """Return the depth moves that the study actually produced."""
    if not results:
        return []
    return [(key, _t(title), _t(hint)) for key, title, hint in DEPTH_MOVES
            if results.get(key)]


def _depth_rows(payload):
    """Normalise a depth payload into (headers, rows) for a table, or None."""
    if isinstance(payload, dict):
        payload = [payload]
    if not isinstance(payload, list) or not payload:
        return None
    dict_items = [item for item in payload if isinstance(item, dict)]
    if len(dict_items) != len(payload):
        return None
    headers = []
    for item in dict_items:
        for key in item:
            if key not in headers:
                headers.append(key)
    rows = [[_fmt_cell(item.get(key, "")) for key in headers]
            for item in dict_items]
    return [key.replace("_", " ").title() for key in headers], rows


def _depth_bullets(payload):
    """Render a non-tabular depth payload as bullet lines."""
    if isinstance(payload, dict):
        return ["{}: {}".format(key.replace("_", " ").title(), _fmt_cell(value))
                for key, value in payload.items()]
    if isinstance(payload, list):
        return [_format_list_item_text(item) for item in payload]
    return [str(payload)]


def _depth_score_text(results):
    """Return the declared depth score, or one derived from what is present."""
    declared = results.get("depth_score") if results else None
    if declared:
        return str(declared)
    return "{}/{} depth moves reported".format(
        len(_depth_entries(results)), len(DEPTH_MOVES))


def format_depth_html(results):
    """Render the analytical-depth moves as HTML."""
    entries = _depth_entries(results)
    if not entries:
        return ""
    html = ('<p class="depth-score">Analytical depth: <strong>{}</strong></p>\n'
            .format(_html_escape(_depth_score_text(results))))
    for key, title, hint in entries:
        payload = results.get(key)
        html += '<h3>{}</h3>\n<p class="depth-hint">{}</p>\n'.format(
            _html_escape(title), _html_escape(hint))
        table = _depth_rows(payload)
        if table:
            headers, rows = table
            html += _html_table_caption(title)
            html += '<table class="custom-table"><thead><tr>'
            html += "".join('<th>{}</th>'.format(_html_escape(col))
                            for col in headers)
            html += '</tr></thead><tbody>\n'
            for row in rows:
                html += "<tr>" + "".join(
                    '<td>{}</td>'.format(_html_escape(cell)) for cell in row
                ) + "</tr>\n"
            html += "</tbody></table>\n"
        else:
            html += "<ul>\n" + "".join(
                "  <li>{}</li>\n".format(_html_escape(line))
                for line in _depth_bullets(payload)) + "</ul>\n"
    return html


def add_depth_word_section(doc, results):
    """Render the analytical-depth moves into the Word report."""
    entries = _depth_entries(results)
    if not entries:
        return
    paragraph = doc.add_paragraph()
    paragraph.add_run("Analytical depth: ").bold = True
    paragraph.add_run(_depth_score_text(results))
    for key, title, hint in entries:
        _add_heading(doc, title, level=2)
        hint_para = doc.add_paragraph(hint)
        for run in hint_para.runs:
            run.font.size = Pt(CAPTION_PT)
            run.font.italic = True
            run.font.color.rgb = RGBColor(110, 110, 110)
        table = _depth_rows(results.get(key))
        if table:
            headers, rows = table
            add_word_table(doc, headers, rows, caption=title)
        else:
            for line in _depth_bullets(results.get(key)):
                doc.add_paragraph(line, style="List Bullet")


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
        h += '<h3>{} {}: {}</h3>\n'.format(_t('Discussion'), i, title)
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

        _add_heading(doc, "{} {}: {}".format(_t("Discussion"), i, title), level=2)

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
    index = 0
    for section in sections:
        if section.get("appendix"):
            continue
        index += 1
        heading = str(section.get("heading", "")).strip()
        section["heading"] = "{}. {}".format(
            index, _MANUAL_HEADING_NUMBER.sub("", heading).strip())
    return sections


def build_sections(results, task_spec, study_config_warnings=None, study_config=None,
                   consistency_issues=None):
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
        "heading": "1. {}".format(_t("Executive Summary")),
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
        "heading": "2. {}".format(_t("Problem Description")),
        "content": problem_description,
    })

    next_section_num = 3

    safety_readiness = format_safety_readiness_text(results) if results else ""
    if safety_readiness:
        sections.append({
            "heading": "{}. {}".format(next_section_num,
                                       _t("Safety Study Readiness")),
            "content": safety_readiness,
            "has_markdown": True,
        })
        next_section_num += 1

    # Scope and Standards (auto-populated from task_spec.md)
    scope_parts = []
    standards = extract_spec_section(task_spec, "Applicable Standards")
    if standards:
        scope_parts.append(_t("Applicable Standards") + ":\n" + standards)
    methods = extract_spec_section(task_spec, "Calculation Methods")
    if methods:
        scope_parts.append(_t("Calculation Methods") + ":\n" + methods)
    criteria = extract_spec_section(task_spec, "Acceptance Criteria")
    if criteria:
        scope_parts.append(_t("Acceptance Criteria") + ":\n" + criteria)
    envelope = extract_spec_section(task_spec, "Operating Envelope")
    if envelope:
        scope_parts.append("Operating Envelope:\n" + envelope)

    scope_content = "\n\n".join(scope_parts) if scope_parts else (
        "[Auto-populated from task_spec.md when filled in. "
        "Edit step1_scope_and_research/task_spec.md and re-run.]"
    )
    sections.append({
        "heading": "{}. {}".format(next_section_num, _t("Scope and Standards")),
        "content": scope_content,
        "has_scope": True,
    })
    next_section_num += 1

    # Information Sources (auto-built from the collected documents themselves)
    information_sources = format_information_sources_text(study_config, results)
    if information_sources:
        sections.append({
            "heading": "{}. {}".format(
                next_section_num, _t("Information Sources and Evidence Basis")),
            "content": information_sources,
            "has_markdown": True,
        })
        next_section_num += 1

    # Approach
    approach = MANUAL_SECTIONS["approach"]
    if results and results.get("approach") and "approach" not in AUTHORED_SECTIONS:
        approach = results["approach"]
    sections.append({
        "heading": "{}. {}".format(next_section_num, _t("Approach")),
        "content": approach,
        "has_equations": True,
    })
    next_section_num += 1

    # Solution Workflow (how the task was solved — discovered agents + workflow)
    if results and results.get("agent_workflow_plan"):
        sections.append({
            "heading": "{}. {}".format(next_section_num, _t("Solution Workflow")),
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
        "heading": "{}. {}".format(next_section_num, _t("Results")),
        "content": results_text,
        "has_figures": True,
    })
    next_section_num += 1

    # Discussion (auto-populated from results.json figure_discussion)
    if results and results.get("figure_discussion"):
        sections.append({
            "heading": "{}. {}".format(next_section_num, _t("Discussion")),
            "content": "",
            "has_discussion": True,
        })
        next_section_num += 1

    # Analytical Depth: the moves that turn a summary into an engineering answer
    if _depth_entries(results):
        sections.append({
            "heading": "{}. {}".format(next_section_num, _t("Analytical Depth")),
            "content": "",
            "has_depth": True,
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
        "heading": "{}. {}".format(next_section_num, _t("Validation Summary")),
        "content": validation_text,
        "has_validation": True,
    })
    next_section_num += 1

    # Generator self-checks belong to the reviewer, not the engineering
    # argument: they go to an appendix instead of interrupting the chapters.
    quality_lines = []
    review_items = ["- {}: {}".format(issue["severity"], issue["message"])
                    for issue in (consistency_issues or [])
                    if issue["severity"] != "INFO"]
    if review_items:
        quality_lines.append("**{}**".format(_t("Consistency review")))
        quality_lines.extend(review_items)
    if study_config_warnings:
        quality_lines.append("**{}**".format(_t("Study configuration")))
        quality_lines.extend("- {}".format(w) for w in study_config_warnings)

    if results and results.get("benchmark_validation"):
        sections.append({
            "heading": "{}. {}".format(next_section_num, _t("Benchmark Validation")),
            "content": "",
            "has_benchmark": True,
        })
        next_section_num += 1

    # N. Uncertainty Analysis (if data available)
    if results and results.get("uncertainty"):
        sections.append({
            "heading": "{}. {}".format(next_section_num, _t("Uncertainty Analysis")),
            "content": "",
            "has_uncertainty": True,
        })
        next_section_num += 1

    # N. Risk Assessment (if data available)
    if results and results.get("risk_evaluation"):
        sections.append({
            "heading": "{}. {}".format(next_section_num, _t("Risk Assessment")),
            "content": "",
            "has_risk": True,
        })
        next_section_num += 1

    assumptions_text = format_assumptions_text(results)
    if assumptions_text:
        sections.append({
            "heading": "{}. {}".format(next_section_num,
                                       _t("Assumptions and Data Gaps")),
            "content": assumptions_text,
            "has_markdown": True,
        })
        next_section_num += 1
    elif results and (results.get("evidence_gaps") or results.get("assumptions_gaps")):
        sections.append({
            "heading": "{}. {}".format(
                next_section_num, _t("Evidence Gaps and Design-Grade Blockers")),
            "content": format_list_items_text(
                results.get("evidence_gaps") or results.get("assumptions_gaps")),
            "has_markdown": True,
        })
        next_section_num += 1

    if results and results.get("recommendations"):
        sections.append({
            "heading": "{}. {}".format(next_section_num, _t("Recommendations")),
            "content": format_list_items_text(results.get("recommendations")),
            "has_markdown": True,
        })
        next_section_num += 1

    improvements_text = format_improvements_text(results)
    if improvements_text:
        sections.append({
            "heading": "{}. {}".format(next_section_num,
                                       _t("Tooling Improvements Delivered")),
            "content": improvements_text,
            "has_markdown": True,
        })
        next_section_num += 1

    # N. Conclusions and Recommendations
    conclusions = MANUAL_SECTIONS["conclusions"]
    if results and results.get("conclusions"):
        conclusions = results["conclusions"]
    sections.append({
        "heading": "{}. {}".format(next_section_num,
                                   _t("Conclusions and Recommendations")),
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
        "heading": "{}. {}".format(next_section_num, _t("References")),
        "content": refs_content,
        "has_references": True,
    })

    reproducibility_text = format_reproducibility_text(results)
    if reproducibility_text:
        sections.append({
            "heading": _t("Appendix A. Reproducing the Results"),
            "content": reproducibility_text,
            "has_markdown": True,
            "appendix": True,
        })

    if quality_lines:
        sections.append({
            "heading": _t("Appendix B. Report Quality Checks" if reproducibility_text
                          else "Appendix A. Report Quality Checks"),
            "content": "\n".join(quality_lines),
            "has_markdown": True,
            "appendix": True,
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
    run = subtitle.add_run(_t("NeqSim Engineering Report"))
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
        (_t("Document Number"), doc_num),
        (_t("Revision"), REVISION),
        (_t("Date"), TASK_DATE),
        (_t("Author"), AUTHOR or _t("(not specified)")),
        (_t("Classification"), CLASSIFICATION),
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
         "description": _t("Initial issue"), "author": AUTHOR or ""}
    ]
    rev_heading = doc.add_paragraph()
    rev_heading.alignment = WD_ALIGN_PARAGRAPH.LEFT
    run = rev_heading.add_run(_t("Revision History"))
    run.font.size = Pt(12)
    run.bold = True
    run.font.color.rgb = RGBColor(47, 84, 150)

    rev_table = doc.add_table(rows=1 + len(rev_entries), cols=4)
    _set_table_style(rev_table)
    rev_table.alignment = WD_TABLE_ALIGNMENT.CENTER
    headers = [_t("Rev"), _t("Date"), _t("Description"), _t("Author")]
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
    # The contents heading carries page-break-before; a break paragraph after
    # this table would be an extra empty line that can spill a blank page.


def _suppress_paragraph_numbering(paragraph):
    """Keep a heading out of the template's automatic heading numbering."""
    p_pr = paragraph._p.get_or_add_pPr()
    for existing in p_pr.findall(qn("w:numPr")):
        p_pr.remove(existing)
    p_pr.append(parse_xml(
        '<w:numPr {}><w:ilvl w:val="0"/><w:numId w:val="0"/></w:numPr>'.format(nsdecls("w"))
    ))


FRONT_MATTER_STYLE = "NeqSim Front Matter Heading"


def _front_matter_heading(doc, text):
    """A heading that looks like Heading 1 but stays out of the TOC.

    As a real Heading 1 the contents page listed itself ("Table of Contents
    ... 2") and the lists of figures and tables. Outline level 9 is body text,
    so neither the \\o nor the \\u TOC switch collects it, and numId 0 keeps a
    template's heading numbering off it.
    """
    try:
        style = doc.styles[FRONT_MATTER_STYLE]
    except KeyError:
        style = doc.styles.add_style(FRONT_MATTER_STYLE, WD_STYLE_TYPE.PARAGRAPH)
        try:
            style.base_style = doc.styles["Heading 1"]
        except KeyError:
            style.font.size = Pt(HEADING1_PT)
            style.font.bold = True
        p_pr = style.element.get_or_add_pPr()
        for tag in ("w:numPr", "w:outlineLvl"):
            for existing in p_pr.findall(qn(tag)):
                p_pr.remove(existing)
        p_pr.append(parse_xml(
            '<w:numPr {}><w:ilvl w:val="0"/><w:numId w:val="0"/></w:numPr>'.format(
                nsdecls("w"))))
        p_pr.append(parse_xml('<w:outlineLvl {} w:val="9"/>'.format(nsdecls("w"))))
        style.paragraph_format.keep_with_next = True
        style.paragraph_format.page_break_before = False
    return doc.add_paragraph(text, style=style)


def _end_page(doc):
    """Start the next content on a new page without leaving a blank page.

    doc.add_page_break() puts the break in a paragraph of its own; when the
    page is already full that paragraph spills onto the next page and breaks
    again. Appending the break to the last paragraph cannot spill.
    """
    body = [child for child in doc.element.body if child.tag != qn("w:sectPr")]
    if body and body[-1].tag == qn("w:p"):
        Paragraph(body[-1], doc._body).add_run().add_break(WD_BREAK.PAGE)
    else:
        doc.add_page_break()


def _add_word_toc(doc):
    """Add a Table of Contents field to the Word document."""
    heading = _front_matter_heading(doc, _t("Table of Contents"))
    heading.paragraph_format.page_break_before = True
    _add_toc_field(doc, 'TOC \\o "1-2" \\h \\z \\u')
    # Tell Word to update all fields (incl. this TOC) when the document is opened
    _set_update_fields_on_open(doc)
    _end_page(doc)


def _add_figure_and_table_lists(doc, results):
    """Add a list of figures and a list of tables, when there is anything to list.

    Word builds these from the SEQ fields in the captions, so a reader can find
    a named figure without scrolling the whole report.
    """
    added = False
    if get_figures():
        _front_matter_heading(doc, _t("List of Figures"))
        _add_toc_field(doc, 'TOC \\h \\z \\c "{}"'.format(_t("Figure")))
        added = True
    if results and (results.get("tables") or results.get("key_results")):
        heading = _front_matter_heading(doc, _t("List of Tables"))
        if added:
            heading.paragraph_format.space_before = Pt(18)
        _add_toc_field(doc, 'TOC \\h \\z \\c "{}"'.format(_t("Table")))
        added = True
    if added:
        _end_page(doc)


def _add_toc_field(doc, instruction):
    """Insert a Word TOC-family field that populates on open or F9."""
    paragraph = doc.add_paragraph()
    run = paragraph.add_run()
    fldChar1 = parse_xml(
        '<w:fldChar {} w:fldCharType="begin"/>'.format(nsdecls("w"))
    )
    run._r.append(fldChar1)
    run2 = paragraph.add_run()
    instrText = parse_xml(
        '<w:instrText {} xml:space="preserve"> {} </w:instrText>'.format(
            nsdecls("w"), instruction
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
    return paragraph


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
    run = heading.add_run(_t("Task"))
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
    _add_figure_and_table_lists(doc, results)

    # The task this report answers, stated before any analysis
    _add_task_statement_block(doc)

    # Add all sections
    for section in sections:
        heading = _add_heading(doc, section["heading"], level=1,
                               numbered=not section.get("appendix"))
        if section.get("appendix"):
            heading.paragraph_format.page_break_before = True

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
        elif (section.get("has_validation") and not section.get("has_benchmark")
              and results and results.get("validation")):
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
        elif section.get("has_depth") and results:
            # Analytical Depth: ranking, rule-outs, robustness, crossover
            add_depth_word_section(doc, results)
        else:
            # Regular text content (bold spans and $...$ inline maths render)
            for para_text in _body_paragraphs(section["content"]):
                _add_bold_runs(doc.add_paragraph(), para_text)

        # Embed figures after Results section
        if section.get("has_figures"):
            figures = get_figures()
            if figures:
                for fig_idx, fig_path in enumerate(figures, 1):
                    caption_text = get_figure_caption(fig_path, results, fig_idx)
                    _add_figure_picture(doc, fig_path)
                    _add_caption(doc, _t("Figure"), caption_text)
            else:
                doc.add_paragraph(
                    "[No figures found in figures/ directory. "
                    "Save plots as PNG files there and re-run this script.]"
                )

        # Embed equations after Approach section
        if section.get("has_equations"):
            equations = get_equations(results)
            if equations:
                _add_heading(doc, _t("Key Equations"), level=2)
                eq_img_dir = os.path.join(REPORT_DIR, "_eq_images")
                if not os.path.exists(eq_img_dir):
                    os.makedirs(eq_img_dir)
                for eq_idx, eq in enumerate(equations, 1):
                    label = eq.get("label", "")
                    latex = eq.get("latex", "")
                    if not latex:
                        continue
                    eq_img_path = os.path.join(eq_img_dir, "eq_{}.png".format(eq_idx))
                    if render_equation_to_image(latex, eq_img_path):
                        _add_display_equation(doc, eq_img_path, label,
                                              _text_width_in(doc))
                    else:
                        _add_equation_fallback_paragraph(doc, label, latex)

    # Save
    _add_page_number_footer(doc)
    _save_docx(doc, DOCX_FILE)
    print("Word report saved: {}".format(DOCX_FILE))


def _save_docx(doc, path):
    """Save a Word document, exiting with a clear message when it is open in Word."""
    try:
        doc.save(path)
    except PermissionError:
        print("ERROR: cannot write {} - it is open in Word or locked by "
              "OneDrive. Close it and run the report again.".format(path))
        sys.exit(3)


# ══════════════════════════════════════════════════════════
# ══════════════════════════════════════════════════════════
# HTML report
# ══════════════════════════════════════════════════════════

def _build_rev_rows_html():
    """Build HTML table rows for revision history in the HTML report."""
    rev_entries = REVISION_HISTORY or [
        {"rev": REVISION, "date": TASK_DATE,
         "description": _t("Initial issue"), "author": AUTHOR or ""}
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
        '    <h2>{}</h2>\n'
        '    <p>{}</p>\n'
        '</div>'.format(_html_escape(_t("Task")),
                        _html_escape(TASK_STATEMENT))
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
        equation_html += '<h3>{}</h3>\n'.format(_t("Key Equations"))
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

        if section.get("has_validation") and validation_html:
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

        if section.get("has_depth") and results:
            content = format_depth_html(results)

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
<html lang="{lang}">
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
        .table-caption {{ font-size: 0.85rem; color: #555; font-style: italic;
                    margin: 1.4rem 0 0.2rem 0; }}
        .table-caption + table {{ margin-top: 0; }}
        .depth-score {{ background: #f3f6fb; border-left: 4px solid #2F5496;
                    padding: 0.5rem 0.9rem; margin-bottom: 1rem;
                    border-radius: 0 4px 4px 0; }}
        .depth-hint {{ font-size: 0.85rem; color: #777; font-style: italic;
                    margin-bottom: 0.4rem; }}
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
            @page {{ size: A4 portrait; margin: 20mm 18mm 18mm 20mm; }}
            nav {{ display: none; }}
            main {{ margin-left: 0; max-width: 100%; padding: 0; }}
            body {{ display: block; font-size: 10.5pt; color: #000; }}
            .cover-page {{ page-break-after: always; }}
            section {{ page-break-inside: auto; }}
            .figure, table, .discussion-block {{ page-break-inside: avoid; }}
            thead {{ display: table-header-group; }}
            tr {{ page-break-inside: avoid; }}
            h2, h3 {{ page-break-after: avoid; break-after: avoid; }}
            .table-caption {{ page-break-after: avoid; }}
            p {{ orphans: 3; widows: 3; }}
            a {{ color: #000; text-decoration: none; }}
        }}
    </style>
</head>
<body>
    <nav>
        <h3>{contents_label}</h3>
        <ul>
{nav}
        </ul>
        <hr style="margin: 1rem 0;">
        <p style="font-size: 0.8rem; color: #999;">{doc_num}</p>
        <p style="font-size: 0.8rem; color: #999;">{rev_label} {rev} | {date}</p>
    </nav>
    <main>
        <div class="cover-page">
            <h1>{title}</h1>
            <p class="subtitle">{subtitle}</p>
            {badges}
            <table class="cover-meta">
                <tr><td>{doc_num_label}</td><td>{doc_num}</td></tr>
                <tr><td>{revision_label}</td><td>{rev}</td></tr>
                <tr><td>{date_label}</td><td>{date}</td></tr>
                <tr><td>{author_label}</td><td>{author}</td></tr>
                <tr><td>{classification_label}</td><td>{classification}</td></tr>
            </table>
            <h3 style="margin-top: 2rem; color: #2F5496;">{rev_history_label}</h3>
            <table class="rev-table">
                <thead><tr><th>{rev_label}</th><th>{date_label}</th><th>{description_label}</th><th>{author_label}</th></tr></thead>
                <tbody>{rev_rows}</tbody>
            </table>
        </div>
{task_block}
{sections}
    </main>{katex_body_script}
</body>
</html>""".format(
        title=TITLE,
        lang=_report_locale(),
        subtitle=_t("NeqSim Engineering Report"),
        contents_label=_t("Contents"),
        doc_num_label=_t("Document No."),
        revision_label=_t("Revision"),
        date_label=_t("Date"),
        author_label=_t("Author"),
        classification_label=_t("Classification"),
        description_label=_t("Description"),
        rev_history_label=_t("Revision History"),
        rev_label=_t("Rev"),
        badges=_build_badges_html(),
        task_block=_build_task_block_html(),
        author=AUTHOR or _t("(not specified)"),
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
        elif ("Validation" in section["heading"] and not section.get("has_benchmark")
              and results and results.get("validation")):
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
                        _add_display_equation(doc, eq_img_path, label, 5.0)
                    else:
                        _add_equation_fallback_paragraph(doc, label, latex)

        # Embed figures after Results section
        if section.get("has_figures"):
            figures = get_figures()
            if figures:
                for fig_path in figures:
                    fig_counter[0] += 1
                    caption_text = get_figure_caption(
                        fig_path, results, fig_counter[0])
                    doc.add_paragraph("")
                    _add_figure_picture(doc, fig_path)
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

    _save_docx(doc, PAPER_DOCX_FILE)
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
    REPORT_ORIENTATION = resolve_report_orientation(study_config)
    REPORT_LANGUAGE = resolve_report_language(study_config)
    if REPORT_LANGUAGE != DEFAULT_REPORT_LANGUAGE:
        print("Report language: {} ({})".format(REPORT_LANGUAGE, _report_locale()))
    pdf_requested = want_pdf_output(study_config)
    print("Report files: {}.docx / {}.html{}".format(
        REPORT_BASENAME, REPORT_BASENAME,
        " / {}.pdf".format(REPORT_BASENAME) if pdf_requested else ""))
    if REPORT_TEMPLATE:
        print("Word template: {}".format(REPORT_TEMPLATE))
    if not TASK_STATEMENT:
        print("NOTE: no task statement found. Add study.title to study_config.yaml,")
        print("      an '## Objective' section to task_spec.md, or 'objective' to")
        print("      results.json so the report states the task up front.")

    study_config_warnings = validate_study_config(study_config, results, task_spec)
    consistency_issues = check_report_consistency(results)
    print_consistency_report(consistency_issues)
    calculation_issues = [issue for issue in consistency_issues
                          if issue.get("fix_type") == "calculation"]
    fixes_path = os.path.join(TASK_DIR, "fixes_needed.json")
    if calculation_issues or os.path.exists(fixes_path):
        with open(fixes_path, "w", encoding="utf-8") as handle:
            json.dump(calculation_issues, handle, indent=2, ensure_ascii=False)
            handle.write("\n")
    if study_config_warnings:
        print("")
        print("Study configuration warnings:")
        for warning in study_config_warnings:
            print("  - {}".format(warning))

    if not paper_only:
        # Build report sections and generate technical report
        sections = build_sections(results, task_spec, study_config_warnings,
                                  study_config, consistency_issues)
        print("")
        build_word_report(sections, results)
        build_html_report(sections, results)
        report_pdf_written = False
        if pdf_requested:
            report_pdf_written = convert_docx_to_pdf(DOCX_FILE, PDF_FILE, "Report")
        print("")
        print("Technical reports generated.")
        print("  Open {} in a browser for navigable view.".format(
            os.path.basename(HTML_FILE)))
        print("  Open {} for formal distribution.".format(
            os.path.basename(DOCX_FILE)))
        if report_pdf_written:
            print("  Open {} for read-only distribution.".format(
                os.path.basename(PDF_FILE)))

    if generate_paper:
        # Build paper sections and generate scientific paper
        paper_sections = build_paper_sections(results, task_spec)
        print("")
        build_paper_docx(paper_sections, results)
        build_paper_html(paper_sections, results)
        paper_pdf_written = False
        if pdf_requested:
            paper_pdf_written = convert_docx_to_pdf(
                PAPER_DOCX_FILE, PAPER_PDF_FILE, "Paper")
        print("")
        print("Scientific papers generated.")
        print("  Open {} for reading.".format(os.path.basename(PAPER_HTML_FILE)))
        print("  Open {} for journal submission / distribution.".format(
            os.path.basename(PAPER_DOCX_FILE)))
        if paper_pdf_written:
            print("  Open {} for read-only distribution.".format(
                os.path.basename(PAPER_PDF_FILE)))

    written = []
    if not paper_only:
        written.extend([DOCX_FILE, HTML_FILE])
        if pdf_requested:
            written.append(PDF_FILE)
    if generate_paper:
        written.extend([PAPER_DOCX_FILE, PAPER_HTML_FILE])
        if pdf_requested:
            written.append(PAPER_PDF_FILE)
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
