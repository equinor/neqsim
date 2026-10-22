Warning: truncated output (original token count: 91169)
Total output lines: 8485

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
  - results.json "lifecycle_envelope_evidence" -> guarded per-period envelope evidence
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
Hand-written content goes in step3_report/report_sections.json (see
_load_report_sections); do not fork or edit this script per task.
"""
import os
import re
import sys
import glob
import json
import base64
import hashlib
import io
import math
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
    from docx.oxml import OxmlElement, parse_xml
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


def _load_report_kit():
    """Import report_kit.py from beside this script, else from the NeqSim toolkit.

    A task folder may hold a lone copy of this script, so the helper module is
    also looked up through NEQSIM_PROJECT_ROOT, the saved project root, the
    pip-installed toolkit and a walk up to a NeqSim checkout.
    """
    import importlib.util
    here = os.path.dirname(os.path.abspath(__file__))
    relative = os.path.join("devtools", "task_template", "step3_report")
    candidates = [here]
    saved = ""
    try:
        with open(os.path.join(os.path.expanduser("~"), ".neqsim", "task_defaults.json"),
                  "r", encoding="utf-8-sig") as handle:
            saved = json.load(handle).get("project_root") or ""
    except (OSError, ValueError, AttributeError):
        pass
    for root in (os.environ.get("NEQSIM_PROJECT_ROOT"), saved):
        if root:
            candidates.append(os.path.join(root, relative))
    try:
        spec = importlib.util.find_spec("task_template")
        for location in (spec.submodule_search_locations or []) if spec else []:
            candidates.append(os.path.join(location, "step3_report"))
    except (ImportError, ValueError):
        pass
    parent = here
    for _ in range(8):
        parent = os.path.dirname(parent)
        if os.path.isfile(os.path.join(parent, "pom.xml")):
            candidates.append(os.path.join(parent, relative))
    for folder in candidates:
        path = os.path.join(folder, "report_kit.py")
        if os.path.isfile(path):
            spec = importlib.util.spec_from_file_location("neqsim_report_kit", path)
            module = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(module)
            return module
    print("ERROR: report_kit.py not found beside generate_report.py or in the NeqSim "
          "toolkit. Copy it from devtools/task_template/step3_report/ or set "
          "NEQSIM_PROJECT_ROOT.")
    sys.exit(1)


KIT = _load_report_kit()
# Word equations as editable OMML; set False to keep rendered images only.
EQUATIONS_AS_OMML = True

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
REPORT_FRONT_LISTS = "auto"      # auto | always | never (lists of figures and tables)
FRONT_LISTS_MIN_ITEMS = 20        # auto: only list when figures + tables reach this
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
        "Appendix": "Vedlegg",
        "Report Quality Checks": "Kvalitetskontroll av rapporten",
        "Reproducing the Results": "Reprodusere resultatene",
        "Complete Numerical Results": "Alle numeriske resultater",
        "Report build": "Rapportbygg",
        "Complete numerical results": "Alle numeriske resultater",
        "See": "Se",
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
        # Table furniture inside generated sections
        "Operating Envelope": "Driftsområde",
        "Operating envelope": "Driftsområde",
        "System": "System",
        "Scope read": "Omfang lest",
        "Access": "Tilgang",
        "Captured evidence": "Lagret dokumentasjon",
        "not recorded": "ikke registrert",
        "missing": "mangler",
        "Source system": "Kildesystem",
        "Documents": "Dokumenter",
        "Content": "Innhold",
        "Documents sought but not obtained:": "Dokumenter som ble søkt etter, men ikke funnet:",
        "Assumption": "Forutsetning",
        "Basis": "Grunnlag",
        "Effect on the result": "Effekt på resultatet",
        "Information sought": "Informasjon søkt",
        "Source": "Kilde",
        "Assumed instead": "Antatt i stedet",
        "Effect if wrong": "Effekt hvis feil",
        "Each gap above is an open item: the conclusion holds only while the stated substitute assumption holds.":
            "Hver mangel over er et åpent punkt: konklusjonen gjelder bare så lenge den angitte erstatningsforutsetningen holder.",
        "Observation": "Observasjon",
        "Physical Mechanism": "Fysisk mekanisme",
        "Engineering Implication": "Ingeniørmessig betydning",
        "Recommendation": "Anbefaling",
        "Linked results": "Koblede resultater",
        "Parameter": "Parameter",
        "Value": "Verdi",
        "Check": "Kontroll",
        "Result": "Resultat",
        "{} document(s) were collected from {} source system(s) and are stored with this task in step1_scope_and_research/references/.":
            "{} dokument(er) ble hentet fra {} kildesystem(er) og er lagret med oppgaven i step1_scope_and_research/references/.",
        "Standards and literature cited are listed in the References section; the per-file origin, retrieval date, and relevance of every collected document are in step1_scope_and_research/references/SOURCES.md.":
            "Standarder og litteratur er listet under Referanser; opprinnelse, hentedato og relevans for hvert dokument står i step1_scope_and_research/references/SOURCES.md.",
        "(Right-click and select 'Update Field' to populate)":
            "(Høyreklikk og velg «Oppdater felt» for å fylle ut)",
        "Reference source": "Referansekilde",
        "Task type": "Oppgavetype",
        "Scale": "Omfang",
        "Mode": "Modus",
        "AACE class": "AACE-klasse",
        "FEL stage": "FEL-fase",
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


def _resolve_output_dir(task_report_dir):
    """Return ``--output-dir`` or the task's ordinary report directory.

    Continuous-task finalization uses this option to run the exact canonical
    renderer into an immutable revision folder without replacing the ordinary
    current-best Task Solver report.
    """
    if "--output-dir" not in sys.argv:
        return task_report_dir
    index = sys.argv.index("--output-dir") + 1
    if index >= len(sys.argv):
        print("ERROR: --output-dir requires a path")
        sys.exit(2)
    return os.path.abspath(sys.argv[index])


TASK_DIR = _resolve_task_dir()
FIG_DIR = os.path.join(TASK_DIR, "figures")
TASK_REPORT_DIR = os.path.join(TASK_DIR, "step3_report")
REPORT_DIR = _resolve_output_dir(TASK_REPORT_DIR)
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
                                     CreateBookmarks=1, IncludeDocProps=True,
                                     DocStructureTags=True)
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
            [executable, "--headless", "--convert-to",
             'pdf:writer_pdf_Export:{"UseTaggedPDF":{"type":"boolean","value":"true"}}',
             "--outdir", outdir, docx_path],
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


def resolve_front_matter_lists(study_config):
    """``report.front_matter_lists``: auto (long reports only) | always | never."""
    value = str((study_config or {}).get("report", {}).get("front_matter_lists") or "auto").strip().lower()
    return value if value in ("auto", "always", "never") else "auto"


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


def resolve_report_template(explicit=None, allow_saved=True, configured=None):
    """Resolve the Word template reports are built from, or None if unset.

    Parameters
    ----------
    explicit : str or None
        Template path from --template; overrides everything else.
    allow_saved : bool
        When False (--no-template), study_config, environment and the saved
        user setting are all ignored.
    configured : str or None
        ``report.template`` from study_config.yaml; relative paths resolve from
        the task folder and ``none`` selects the built-in styling. Overrides
        the environment and the saved user setting.

    Returns
    -------
    str or None
        Absolute path to an existing .docx/.dotx file, or None.

    Raises
    ------
    ValueError
        If a template is configured but is not a readable Word file.
    """
    configured = (configured or "").strip()
    if not explicit and allow_saved and configured:
        if configured.lower() in ("none", "off", "builtin", "built-in"):
            return None
        explicit = (configured if os.path.isabs(os.path.expanduser(configured))
                    else os.path.join(TASK_DIR, configured))
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


def _anchor_header_drawings_to_right_margin(doc):
    """Keep right-hand header/footer drawings (logo) on the page after a flip.

    Corporate templates place the logo with a fixed offset from the left
    column. Rotating a landscape template to portrait leaves that offset
    beyond the page edge, so the logo vanishes. Re-expressing the offset
    relative to the right margin keeps the same gap in either orientation.
    """
    seen = set()
    for section in doc.sections:
        left = section.left_margin or 0
        right = section.right_margin or 0
        page_w = section.page_width
        parts = (section.header, section.first_page_header,
                 section.even_page_header, section.footer,
                 section.first_page_footer, section.even_page_footer)
        for part in parts:
            if part.is_linked_to_previous:
                continue
            root = part._element
            if id(root) in seen:
                continue
            seen.add(id(root))
            for anchor in root.iter(qn("wp:anchor")):
                pos_h = anchor.find(qn("wp:positionH"))
                extent = anchor.find(qn("wp:extent"))
                if pos_h is None or extent is None:
                    continue
                offset = pos_h.find(qn("wp:posOffset"))
                if offset is None:
                    continue
                base = {"column": left, "margin": left, "leftMargin": left,
                        "page": 0}.get(pos_h.get("relativeFrom"))
                if base is None:
                    continue
                x_left = base + int(offset.text)
                if x_left + int(extent.get("cx")) / 2.0 < page_w / 2.0:
                    continue
                pos_h.set("relativeFrom", "rightMargin")
                offset.text = str(int(x_left - (page_w - right)))


def _normalize_page_setup(doc):
    """Set the body on a readable measure, whatever the template declares."""
    if REPORT_ORIENTATION == "template":
        return
    want_landscape = REPORT_ORIENTATION == "landscape"
    _anchor_header_drawings_to_right_margin(doc)
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
# doc_number, revision, manual_sections, appendix_sections, paper_sections, paper_* metadata.
REPORT_SECTIONS_FILE = os.path.join(TASK_REPORT_DIR, "report_sections.json")


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


def _expand_pvt_tuning_tables(data):
    """Turn results['pvt_tuning_quality'] into report tables (before/after, tuned parameters, exclusions).

    The tables are appended to ``data['tables']`` so that the HTML and Word renderers show the fluid-tuning
    quality without block-specific code. Existing tables with the same title are kept as they are.
    """
    block = data.get("pvt_tuning_quality") if isinstance(data, dict) else None
    if not isinstance(block, dict):
        return data
    tables = data.setdefault("tables", [])
    if not isinstance(tables, list):
        return data
    have = {t.get("title") for t in tables if isinstance(t, dict)}

    def fmt(value):
        if value is None or value == "":
            return "n/a"
        if isinstance(value, bool):
            return "yes" if value else "no"
        if isinstance(value, float):
            return "{:.4g}".format(value)
        return str(value)

    exps = [e for e in block.get("experiments", []) if isinstance(e, dict)]
    title = "PVT tuning quality: error before and after tuning"
    if exps and title not in have:
        tables.append({"title": title,
                       "headers": ["Sample", "Experiment", "Metric", "Before", "After", "Unit"],
                       "rows": [[fmt(e.get("sample")), fmt(e.get("type")), fmt(e.get("metric")), fmt(e.get("before")),
                                 fmt(e.get("after")), fmt(e.get("unit"))] for e in exps]})
    params = [p for p in block.get("parameters", []) if isinstance(p, dict)]
    title = "PVT tuning quality: tuned parameters and bounds"
    if params and title not in have:
        tables.append({"title": title,
                       "headers": ["Parameter", "Initial", "Tuned", "Lower", "Upper", "At bound"],
                       "rows": [[fmt(p.get("name")), fmt(p.get("initial")), fmt(p.get("tuned")), fmt(p.get("lower")),
                                 fmt(p.get("upper")), fmt(p.get("at_bound"))] for p in params]})
    excl = [x for x in block.get("exclusions", []) if isinstance(x, dict)]
    title = "PVT tuning quality: data excluded from the tuning"
    if excl and title not in have:
        tables.append({"title": title, "headers": ["Sample or data", "Reason"],
                       "rows": [[fmt(x.get("sample")), fmt(x.get("reason"))] for x in excl]})
    return data


def load_results():
    """Load results.json if it exists. Returns dict or None."""
    if os.path.exists(RESULTS_FILE):
        with open(RESULTS_FILE, "r", encoding="utf-8") as f:
            data = _expand_pvt_tuning_tables(_normalize_validation(json.load(f)))
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
    local_copy = os.path.join(TASK_REPORT_DIR, "generate_report.py")
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
        badges.append((_t(label), value))
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


def _validation_value(outcome):
    """Return the scalar of a validation entry; entries may be {value, unit, label} dicts."""
    if isinstance(outcome, dict) and "value" in outcome:
        return outcome["value"]
    return outcome


def _validation_failures(results):
    """Return validation checks that are false and block design-grade use."""
    failures = []
    validation = results.get("validation", {}) if results else {}
    for check, outcome in validation.items():
        if _validation_outcome_is_failure(check, _validation_value(outcome)):
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
            _t("{} document(s) were collected from {} source system(s) and are stored "
               "with this task in step1_scope_and_research/references/.").format(
                total, len(rows)))
        parts.append("")
        table = ["| {} | {} | {} |".format(_t("Source system"), _t("Documents"), _t("Content")),
                 "|---|---|---|"]
        for name, count, description in rows:
            table.append("| {} | {} | {} |".format(name, count, description or "-"))
        parts.append("\n".join(table))

    if data_sources:
        parts.append("")
        parts.append(_t("Source systems read") + ":")
        table = ["| {} | {} | {} | {} |".format(
                     _t("System"), _t("Scope read"), _t("Access"), _t("Captured evidence")),
                 "|---|---|---|---|"]
        for entry in data_sources:
            if not isinstance(entry, dict):
                continue
            evidence = str(entry.get("evidence", "") or "")
            if not evidence:
                state = _t("not recorded")
            elif os.path.exists(_resolve_task_path(evidence)):
                state = evidence
            else:
                state = "{} ({})".format(evidence, _t("missing"))
            table.append("| {} | {} | {} | {} |".format(
                entry.get("system", "-"), entry.get("scope", "-"),
                entry.get("access", "read-only"), state))
        if len(table) > 2:
            parts.append("\n".join(table))

    gaps = manifest.get("data_gaps", []) or []
    if gaps:
        parts.append("")
        parts.append(_t("Documents sought but not obtained:"))
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
        parts.append(_t("Standards and literature cited are listed in the References "
                        "section; the per-file origin, retrieval date, and relevance of "
                        "every collected document are in "
                        "step1_scope_and_research/references/SOURCES.md."))
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
        target = (item.get("target") or item.get("component") or item.get("file")
                  or item.get("repo") or "tooling")
        if item.get("repo") and item.get("file") and not item.get("target"):
            target = "{}: {}".format(item["repo"], item["file"])
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
            table = ["| # | {} | {} | {} |".format(
                         _t("Assumption"), _t("Basis"), _t("Effect on the result")),
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
            "| # | {} | {} | {} | {} | {} |".format(
                _t("Information sought"), _t("Source"), _t("Status"),
                _t("Assumed instead"), _t("Effect if wrong")),
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
        parts.append(_t("Each gap above is an open item: the conclusion holds only while "
                        "the stated substitute assumption holds."))

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


def _build_fingerprint():
    """Short hashes of results.json and of the generator, to tie a report to its inputs."""
    def digest(paths):
        sha = hashlib.sha256()
        found = False
        for path in paths:
            try:
                with open(path, "rb") as handle:
                    sha.update(handle.read())
                found = True
            except OSError:
                pass
        return sha.hexdigest()[:12] if found else ""
    return {"results": digest([RESULTS_FILE]),
            "generator": digest([os.path.abspath(__file__), KIT.__file__])}


def format_reproducibility_text(results):
    """Format results.json ``reproducibility`` as markdown for the appendix.

    Accepts a string, a list of steps, or a dict with ``environment``,
    ``steps``, ``checks`` and ``what_if`` (each a string or list). A build
    line ties the report to the exact results.json and generator it came from.
    """
    text = _reproducibility_body(results)
    stamp = _build_fingerprint()
    if text and stamp["results"]:
        text += "\n\n**{}**\n- results.json SHA-256 {}\u2026; report generator {}\u2026".format(
            _t("Report build"), stamp["results"], stamp["generator"])
    return text


_LIFECYCLE_IDENTITY_FIELDS = (
    "classification", "source", "qualified_constraint_name", "applicable_limit",
    "physical_unit", "applicable_limit_role", "applicable_limit_provenance",
)


def _report_number(value):
    """Return true only for finite report-facing numbers (booleans are not numbers)."""
    if not isinstance(value, (int, float)) or isinstance(value, bool):
        return False
    try:
        return math.isfinite(float(value))
    except OverflowError:
        return False


def _report_text(value):
    """Return true only for non-empty report-facing text."""
    return isinstance(value, str) and bool(value.strip())


def _md_cell(value):
    """Escape one value for a compact Markdown table cell."""
    return str(value if value is not None else "-").replace("|", "/").replace("\n", " ")


def _constraint_identity(row):
    """Return the exact schema-v8 identity subset from one ranked summary row."""
    return {field: row.get(field) for field in _LIFECYCLE_IDENTITY_FIELDS}


def _exact_report_difference(first, second):
    """Return exact ordered rows in first but not second, retaining multiplicity."""
    remaining = list(second)
    difference = []
    for row in first:
        if row in remaining:
            remaining.remove(row)
        else:
            difference.append(row)
    return difference


def _validate_representative_constraint(entry, representative, feasible):
    """Validate one exact representative row against its ranked identity and class."""
    if not isinstance(representative, dict):
        return False
    if representative.get("feasible") is not feasible:
        return False
    if any(representative.get(field) != entry.get(field) for field in (
            "source", "qualified_constraint_name", "applicable_limit", "physical_unit",
            "applicable_limit_role", "applicable_limit_provenance")):
        return False
    return all(_report_number(representative.get(field)) for field in (
        "current_value", "physical_margin", "normalized_margin"))


def _validate_lifecycle_report_evidence(evidence, expected_cycle_id=None):
    """Validate the report-facing schema-v8 subset and independently replay identity deltas."""
    reasons = []
    if not isinstance(evidence, dict):
        return ["evidence is not an object"]
    if evidence.get("schema_version") != 8:
        reasons.append("schema_version must be 8")
    if evidence.get("advisory") is not True:
        reasons.append("advisory flag is missing")
    if not _report_text(evidence.get("cycle_id")):
        reasons.append("cycle_id is missing")
    elif expected_cycle_id and evidence.get("cycle_id") != expected_cycle_id:
        reasons.append("cycle_id does not match the promoted current-best cycle")
    if not all(_report_text(evidence.get(field)) for field in (
            "lifecycle_study_id", "lifecycle_study_name", "lifecycle_study_provenance", "time_unit")):
        reasons.append("lifecycle identity, provenance, or time unit is incomplete")
    requested = evidence.get("requested_point_count")
    completed = evidence.get("completed_point_count")
    if (not _report_number(requested) or not _report_number(completed)
            or int(requested) != requested or int(completed) != completed
            or requested < 1 or requested != completed):
        reasons.append("sampled lifecycle points are incomplete")
    if (not _report_number(evidence.get("total_evaluation_count"))
            or evidence.get("total_evaluation_count") < 1
            or not _report_number(evidence.get("envelope_runtime_seconds"))
            or evidence.get("envelope_runtime_seconds") < 0):
        reasons.append("evaluation count or runtime is invalid")

    periods = evidence.get("periods")
    if (not isinstance(periods, list) or not periods
            or evidence.get("period_count") != len(periods)):
        reasons.append("period_count does not match the period evidence")
        return reasons
    period_identities = []
    previous_elapsed = None
    seen_period_ids = set()
    for index, period in enumerate(periods):
        prefix = "period {}".format(index + 1)
        if not isinstance(period, dict):
            reasons.append(prefix + " is not an object")
            period_identities.append([])
            continue
        period_id = period.get("period_id")
        elapsed = period.get("elapsed_time")
        if not _report_text(period_id) or period_id in seen_period_ids:
            reasons.append(prefix + " has a missing or duplicate period_id")
        else:
            seen_period_ids.add(period_id)
        if (not _report_text(period.get("period_name"))
                or not _report_text(period.get("configuration_provenance"))):
            reasons.append(prefix + " has incomplete report metadata")
        if (not _report_number(elapsed) or elapsed < 0
                or (previous_elapsed is not None and elapsed <= previous_elapsed)):
            reasons.append(prefix + " elapsed_time is not strictly increasing")
        else:
            previous_elapsed = elapsed
        if period.get("time_unit") != evidence.get("time_unit"):
            reasons.append(prefix + " time unit does not match the lifecycle")
        envelope = period.get("envelope_evidence")
        if not isinstance(envelope, dict) or envelope.get("schema_version") != 2:
            reasons.append(prefix + " is missing schema-v2 envelope evidence")
            period_identities.append([])
            continue
        slices = envelope.get("slices")
        if (not isinstance(slices, list) or not slices
                or any(not isinstance(row, dict) or row.get("complete") is not True
                       or row.get("outcome") != "COMPLETE" for row in slices)):
            reasons.append(prefix + " contains incomplete operating-envelope slices")
        summary = envelope.get("cross_slice_constraint_summary")
        ranked = summary.get("ranked_constraints") if isinstance(summary, dict) else None
        if (not isinstance(summary, dict) or summary.get("summary_version") != 1
                or summary.get("advisory") is not True or not isinstance(ranked, list)
                or summary.get("ranked_constraint_count") != len(ranked)):
            reasons.append(prefix + " has an invalid cross-slice constraint summary")
            period_identities.append([])
            continue
        identities = []
        for row_index, row in enumerate(ranked):
            row_prefix = "{} ranked constraint {}".format(prefix, row_index + 1)
            if (not isinstance(row, dict)
                    or row.get("rank") != row_index + 1
                    or row.get("classification") not in ("REJECTING", "NEAR_ACTIVE")
                    or not all(_report_text(row.get(field)) for field in (
                        "source", "qualified_constraint_name", "physical_unit",
                        "applicable_limit_role", "applicable_limit_provenance"))
                    or (row.get("applicable_limit") is not None
                        and not _report_number(row.get("applicable_limit")))):
                reasons.append(row_prefix + " has an incomplete identity")
                continue
            representative_key = ("representative_rejecting_constraint"
                                  if row["classification"] == "REJECTING"
                                  else "representative_feasible_constraint")
            representative = row.get(representative_key)
            if not _validate_representative_constraint(
                    row, representative, row["classification"] == "NEAR_ACTIVE"):
                reasons.append(row_prefix + " has incomplete or mismatched representative evidence")
            identity = _constraint_identity(row)
            if identity in identities:
                reasons.append(row_prefix + " duplicates an exact identity")
            identities.append(identity)
        period_identities.append(identities)

    if expected_cycle_id and expected_cycle_id not in seen_period_ids:
        reasons.append("promoted current-best cycle is missing from lifecycle periods")

    changes = evidence.get("period_change_diagnostics")
    if not isinstance(changes, list) or len(changes) != max(0, len(periods) - 1):
        reasons.append("period_change_diagnostics does not match adjacent periods")
        return reasons
    for index, change in enumerate(changes):
        prefix = "period change {}".format(index + 1)
        if not isinstance(change, dict):
            reasons.append(prefix + " is not an object")
            continue
        removed = _exact_report_difference(period_identities[index], period_identities[index + 1])
        added = _exact_report_difference(period_identities[index + 1], period_identities[index])
        retained = len(period_identities[index]) - len(removed)
        if (change.get("from_period_id") != periods[index].get("period_id")
                or change.get("to_period_id") != periods[index + 1].get("period_id")
                or change.get("from_ranked_constraint_identity_count") != len(period_identities[index])
                or change.get("to_ranked_constraint_identity_count") != len(period_identities[index + 1])
                or change.get("retained_ranked_constraint_identity_count") != retained
                or change.get("removed_ranked_constraint_identities") != removed
                or change.get("added_ranked_constraint_identities") != added):
            reasons.append(prefix + " identity migration does not reconstruct from period summaries")
    return reasons


def _promoted_cycle_id():
    """Return the reviewed current-best source cycle when this is a living task."""
    path = os.path.join(TASK_DIR, "continuous", "baseline", "baseline.json")
    try:
        with open(path, "r", encoding="utf-8-sig") as handle:
            baseline = json.load(handle)
    except (OSError, ValueError):
        return None
    return baseline.get("source_cycle") if isinstance(baseline, dict) else None


def _identity_text(identity):
    """Render one exact identity without comparing physical margins across units."""
    limit = identity.get("applicable_limit")
    limit_text = _fmt_number(limit) if _report_number(limit) else "not applicable"
    return "{} `{}` — {} {}, role {}, source {}; basis: {}".format(
        identity.get("classification"), identity.get("qualified_constraint_name"),
        limit_text, identity.get("physical_unit") or "", identity.get("applicable_limit_role"),
        identity.get("source"), identity.get("applicable_limit_provenance"))


def format_lifecycle_envelope_text(results, expected_cycle_id=None):
    """Render guarded schema-v8 lifecycle evidence for current-best and final reports."""
    evidence = (results or {}).get("lifecycle_envelope_evidence")
    if evidence is None:
        return ""
    reasons = _validate_lifecycle_report_evidence(evidence, expected_cycle_id)
    if reasons:
        return ("**Lifecycle evidence withheld.** The report did not accept this evidence because: {}. "
                "No bottleneck, headroom, or identity-migration claim is rendered."
                .format("; ".join(reasons)))

    lines = [
        "**Evidence basis.** {} (`{}`), cycle `{}`; {} complete sampled point(s), {} model evaluation(s), "
        "runtime {} s. {}".format(
            evidence["lifecycle_study_name"], evidence["lifecycle_study_id"], evidence["cycle_id"],
            evidence["completed_point_count"], evidence["total_evaluation_count"],
            _fmt_number(evidence["envelope_runtime_seconds"]), evidence["lifecycle_study_provenance"]),
        "",
        "Rows below retain exact per-period engineering units and representative physical headroom. "
        "Rank applies only inside one period; margins are never ranked or compared across unlike units.",
        "",
        "| Period | Time | Class | Exact constraint | Representative value | Physical headroom | Limit basis |",
        "|---|---|---|---|---|---|---|",
    ]
    for period in evidence["periods"]:
        summary = period["envelope_evidence"]["cross_slice_constraint_summary"]
        ranked = summary["ranked_constraints"]
        if not ranked:
            lines.append("| {} | {} {} | - | No rejecting or near-active identity | - | - | {} |".format(
                _md_cell(period["period_name"]), _fmt_number(period["elapsed_time"]),
                _md_cell(period["time_unit"]), _md_cell(period["configuration_provenance"])))
            continue
        for row in ranked:
            representative = row[("representative_rejecting_constraint"
                                  if row["classification"] == "REJECTING"
                                  else "representative_feasible_constraint")]
            lines.append("| {} | {} {} | {} | `{}` | {} {} | {} {} | {}: {} {}; {} |".format(
                _md_cell(period["period_name"]), _fmt_number(period["elapsed_time"]),
                _md_cell(period["time_unit"]), row["classification"],
                _md_cell(row["qualified_constraint_name"]),
                _fmt_number(representative["current_value"]), _md_cell(row["physical_unit"]),
                _fmt_number(representative["physical_margin"]), _md_cell(row["physical_unit"]),
                _md_cell(row["applicable_limit_role"]),
                _fmt_number(row["applicable_limit"]) if row["applicable_limit"] is not None else "n/a",
                _md_cell(row["physical_unit"]), _md_cell(row["applicable_limit_provenance"])))

    lines += ["", "**Exact adjacent-period identity migration.** Classification or limit-basis changes "
              "appear as one removed and one added identity; this is descriptive evidence, not causal attribution."]
    for change in evidence["period_change_diagnostics"]:
        lines += ["", "- **{} → {}:** {} retained, {} removed, {} added.".format(
            _md_cell(change["from_period_id"]), _md_cell(change["to_period_id"]),
            change["retained_ranked_constraint_identity_count"],
            len(change["removed_ranked_constraint_identities"]),
            len(change["added_ranked_constraint_identities"]))]
        lines += ["  - Removed: {}".format(_identity_text(identity))
                  for identity in change["removed_ranked_constraint_identities"]]
        lines += ["  - Added: {}".format(_identity_text(identity))
                  for identity in change["added_ranked_constraint_identities"]]
    lines += ["", "**Qualification boundary.** This is exact sampled/refined advisory evidence. It is not a "
              "continuous forecast, shadow price, causal diagnosis, equipment qualification, operating approval, "
              "or permission to relax a physical constraint."]
    return "\n".join(lines)


def _load_final_report_metadata():
    """Load the continuous-task finalization record supplied by its orchestrator."""
    path = _cli_option("--final-metadata")
    if not path:
        return {}
    try:
        with open(path, "r", encoding="utf-8-sig") as handle:
            data = json.load(handle)
    except (OSError, ValueError) as error:
        print("ERROR: could not read final-report metadata {}: {}".format(path, error))
        sys.exit(2)
    if not isinstance(data, dict) or not data.get("revision"):
        print("ERROR: final-report metadata must contain a revision")
        sys.exit(2)
    return data


def get_live_figures():
    """Figures a living task's final report adds (kept outside the task's own figures/ folder)."""
    metadata = _load_final_report_metadata()
    folder = os.path.join(TASK_DIR, str(metadata.get("figures_dir") or ""))
    if not metadata.get("figures_dir"):
        return []
    return [os.path.join(folder, name) for name in metadata.get("figures") or []
            if os.path.isfile(os.path.join(folder, name))]


def format_live_summary(metadata):
    """The plain-language live-task summary written by the continuous-task orchestrator."""
    return str((metadata or {}).get("summary_markdown") or "")


def format_final_report_record(metadata):
    """Render the immutable final-delivery identity and audit references as Markdown."""
    if not metadata:
        return ""
    source = metadata.get("source") or {}
    audit = metadata.get("audit_trail") or {}
    lines = [
        "This standalone deliverable is final-report revision **{}**, generated from "
        "the latest reviewed Task Solver baseline. Transient cycle history is deliberately "
        "not reproduced here; the task-folder records below remain the authoritative audit trail. "
        "The task's original report in step3_report/ is not changed by this revision; this is a separate "
        "document that contains the same engineering content plus the live-study summary and figures."
        .format(metadata.get("revision")),
        "",
        "- Finalized: {}".format(metadata.get("finalized_at") or "-"),
        "- Reviewed by: {}".format(metadata.get("reviewer") or "-"),
        "- Promoted baseline: {}".format(source.get("baseline_id") or "-"),
        "- Source cycle: {}".format(source.get("source_cycle") or "-"),
    ]
    if metadata.get("note"):
        lines.append("- Finalization note: {}".format(metadata.get("note")))
    lines.extend(["", "**Audit trail and reproducibility companions**"])
    for label, key in (("Work record", "work_record"),
                       ("Living Report", "living_report"),
                       ("Decision ledger", "ledger"),
                       ("Promoted cycle manifest", "cycle")):
        if audit.get(key):
            lines.append("- {}: `{}`".format(label, audit[key]))
    return "\n".join(lines)


def _reproducibility_body(results):
    """The author-written reproducibility content as markdown, or an empty string."""
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


def _word_paragraphs(text):
    """Paragraphs for Word: wrapped prose rejoined, each list item or table row on its own.

    Word keeps a literal newline as a line break, so hard-wrapped markdown would
    otherwise print with ragged breaks mid-sentence.
    """
    paragraphs = []
    for para in _body_paragraphs(text):
        lines = [line.strip() for line in para.split("\n") if line.strip()]
        if len(lines) > 1 and _is_line_structured(para):
            items = []
            for line in lines:
                starts_item = _LIST_LINE_RE.match(line) or line.startswith("|")
                if items and not starts_item:
                    items[-1] += " " + line  # wrapped continuation of a list item
                else:
                    items.append(line)
            paragraphs.extend(items)
        else:
            paragraphs.append(" ".join(lines))
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
        out.append("<p>{}</p>".format(
            _html_xrefs(_INLINE_CODE_RE.sub(r"<code>\1</code>", body))))
    return "".join(out)


def _benchmark_tests(results):
    """Normalize legacy ``tests`` lists and named benchmark mappings for all outputs."""
    benchmark = (results or {}).get("benchmark_validation") or {}
    if isinstance(benchmark, list):
        benchmark = {"tests": benchmark}
    if not isinstance(benchmark, dict):
        return []
    listed = next((benchmark[key] for key in ("tests", "points")
                   if isinstance(benchmark.get(key), list)), None)
    if listed is not None:
        entries …31169 tokens truncated…d_in)
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


def _group_caption(group):
    """Caption for one headline table: "Key results" or "Key results - <group>"."""
    return "{} \u2013 {}".format(_t("Key results"), group) if group else _t("Key results")


def add_results_word_table(doc, results, full=False):
    """Add key_results as a styled Word table with units column.

    With headline results marked, the Results section shows only those (one
    table per group); ``full=True`` prints every row for the appendix.
    """
    key_results = results.get("key_results", {})
    headline = [] if full else _headline_rows(results)
    if headline:
        for group, rows in _grouped(headline):
            has_notes = any(note for _l, _v, _u, note in rows)
            headers = [_t("Parameter"), _t("Value"), _t("Unit")] + (
                [_t("Source")] if has_notes else [])
            data_rows = [[label, _fmt_cell(value), unit] + ([note or ""] if has_notes else [])
                         for label, value, unit, note in rows]
            add_word_table(doc, headers, data_rows, caption=_group_caption(group))
        return
    if not key_results:
        return
    flat_rows = _flatten_key_results(key_results)
    has_notes = any(note for _label, _value, _unit, note in flat_rows)
    if has_notes:
        headers = [_t("Parameter"), _t("Value"), _t("Unit"), _t("Source")]
        data_rows = [[label, _fmt_cell(value), unit, note or ""]
                     for label, value, unit, note in flat_rows]
        col_widths = [Inches(2.3), Inches(1.3), Inches(1.1), Inches(2.3)]
    else:
        headers = [_t("Parameter"), _t("Value"), _t("Unit")]
        data_rows = [[label, _fmt_cell(value), unit] for label, value, unit, _note in flat_rows]
        col_widths = [Inches(3.0), Inches(1.5), Inches(1.5)]
    add_word_table(doc, headers, data_rows,
                   col_widths=col_widths,
                   caption=_t("Complete numerical results") if full else _t("Key results"))


def add_validation_word_table(doc, results):
    """Add validation checks as a styled Word table."""
    validation = results.get("validation", {})
    if not validation:
        return
    headers = [_t("Check"), _t("Result")]
    data_rows = []
    for check, outcome in validation.items():
        label = _label_from_key(check)
        if isinstance(outcome, dict) and "value" in outcome:
            label = outcome.get("label") or label
            unit = outcome.get("unit") or ""
            outcome = outcome["value"]
            if isinstance(outcome, (int, float)) and not isinstance(outcome, bool) and unit:
                data_rows.append([label, "{} {}".format(_fmt_number(outcome), unit)])
                continue
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


def add_references_word(doc, results):
    """Numbered reference list, one paragraph per entry with a hanging indent."""
    for index, ref in enumerate(results.get("references") or [], 1):
        text = _reference_text(ref)
        para = doc.add_paragraph()
        fmt = para.paragraph_format
        fmt.left_indent = Inches(0.4)
        fmt.first_line_indent = Inches(-0.4)
        fmt.space_after = Pt(3)
        para.add_run("[{}]\t".format(index))
        _add_bold_runs(para, text)


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


def _key_result_index(results):
    """Map each key_results key (nested keys too) to its (label, value, unit, note) row."""
    index = {}

    def visit(mapping):
        for key, value in (mapping or {}).items():
            rows = _flatten_key_results({key: value})
            if len(rows) == 1:
                index[key] = rows[0]
            if isinstance(value, dict) and not (
                    "value" in value and not isinstance(value["value"], (dict, list))):
                visit(value)

    visit((results or {}).get("key_results"))
    return index


def _linked_result_text(name, index):
    """Readable form of a linked result: "Label = value unit", never a raw key."""
    name = str(name)
    if name in index:
        label, value, unit, _note = index[name]
        return "{} = {}{}".format(label, _fmt_cell(value), " " + unit if unit else "")
    return _label_from_key(name) if re.fullmatch(r"[A-Za-z0-9_]+", name) else name


def _discussion_trace(disc, index):
    """Traceability footer for one discussion entry (links and the question answered)."""
    parts = []
    linked = disc.get("linked_results", [])
    if linked:
        parts.append("{}: {}".format(
            _t("Linked results"), "; ".join(_linked_result_text(k, index) for k in linked)))
    if disc.get("insight_question_ref"):
        parts.append("Answers: {}".format(disc["insight_question_ref"]))
    return " | ".join(parts)


def format_discussion_html(results):
    """Format figure_discussion from results.json as styled HTML discussion blocks.

    Each discussion entry links a figure to its observation, physical mechanism,
    engineering implication, and recommendation — creating traceability from
    calculation to conclusion. The figure itself is shown inside its block.
    """
    discussions = results.get("figure_discussion", [])
    if not discussions:
        return ""
    plan = get_figure_plan(results)
    index = _key_result_index(results)
    h = ""
    for i, disc in enumerate(discussions, 1):
        fig_file = disc.get("figure", "")
        title = disc.get("title", fig_file.replace("_", " ").replace(".png", "").title())
        observation = disc.get("observation", "")
        mechanism = disc.get("mechanism", "")
        implication = disc.get("implication", "")
        recommendation = disc.get("recommendation", "")

        h += '<div class="discussion-block">\n'
        h += '<h3>{} {}: {}</h3>\n'.format(_t("Discussion"), i, title)
        if (i - 1) in plan.discussion_figure:
            h += _figure_html(plan.discussion_figure[i - 1], results, plan,
                              alt_extra=observation)
        elif (i - 1) in plan.discussion_ref:
            number = plan.number_of(plan.discussion_ref[i - 1])
            h += '<p>{} <a class="xref" href="#fig-{}">{} {}</a>.</p>\n'.format(
                _t("See"), number, _t("Figure"), number)
        if observation:
            h += '<p><strong>{}:</strong> {}</p>\n'.format(
                _t("Observation"), _html_xrefs(observation))
        if mechanism:
            h += '<p><strong>{}:</strong> {}</p>\n'.format(
                _t("Physical Mechanism"), _html_xrefs(mechanism))
        if implication:
            h += '<p><strong>{}:</strong> {}</p>\n'.format(
                _t("Engineering Implication"), _html_xrefs(implication))
        if recommendation:
            h += '<p class="recommendation"><strong>{}:</strong> {}</p>\n'.format(
                _t("Recommendation"), _html_xrefs(recommendation))
        trace = _discussion_trace(disc, index)
        if trace:
            h += '<p class="traceability"><em>{}</em></p>\n'.format(_html_escape(trace))
        h += '</div>\n'
    return h


def add_discussion_word(doc, results):
    """Add figure discussion entries as styled Word content.

    Each discussion block has: its figure, observation, physical mechanism,
    engineering implication, and recommendation with traceability references.
    """
    discussions = results.get("figure_discussion", [])
    if not discussions:
        return
    plan = get_figure_plan(results)
    index = _key_result_index(results)
    for i, disc in enumerate(discussions, 1):
        fig_file = disc.get("figure", "")
        title = disc.get("title", fig_file.replace("_", " ").replace(".png", "").title())
        observation = disc.get("observation", "")
        mechanism = disc.get("mechanism", "")
        implication = disc.get("implication", "")
        recommendation = disc.get("recommendation", "")

        _add_heading(doc, "{} {}: {}".format(_t("Discussion"), i, title), level=2)

        if (i - 1) in plan.discussion_figure:
            _add_figure_block(doc, plan.discussion_figure[i - 1], results, plan,
                              alt_extra=observation)
        elif (i - 1) in plan.discussion_ref:
            para = doc.add_paragraph()
            para.add_run(_t("See") + " ")
            ref_path = plan.discussion_ref[i - 1]
            _add_ref_field(para, _figure_bookmark(ref_path),
                           "{} {}".format(_t("Figure"), plan.number_of(ref_path)))
            para.add_run(".")

        for label, text in ((_t("Observation"), observation),
                            (_t("Physical Mechanism"), mechanism),
                            (_t("Engineering Implication"), implication),
                            (_t("Recommendation"), recommendation)):
            if not text:
                continue
            p = doc.add_paragraph()
            r = p.add_run(label + ": ")
            r.bold = True
            r.font.size = Pt(BODY_PT)
            first = len(p.runs)
            _add_bold_runs(p, text)
            for run in p.runs[first:]:
                run.font.size = Pt(BODY_PT)
                if label == _t("Recommendation"):
                    run.font.color.rgb = RGBColor(0x1A, 0x53, 0x7A)

        trace = _discussion_trace(disc, index)
        if trace:
            p = doc.add_paragraph()
            r = p.add_run(trace)
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

    live_summary = format_live_summary(_load_final_report_metadata())
    if live_summary:
        sections.append({
            "heading": "{}. {}".format(next_section_num, "How the Live Study Developed"),
            "content": live_summary,
            "has_markdown": True,
        })
        next_section_num += 1

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
        scope_parts.append(_t("Operating Envelope") + ":\n" + envelope)

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

    lifecycle_text = format_lifecycle_envelope_text(results, _promoted_cycle_id())
    if lifecycle_text:
        sections.append({
            "heading": "{}. Lifecycle Operating Envelope".format(next_section_num),
            "content": lifecycle_text,
            "has_markdown": True,
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
            ref_id = ref.get("id", "") if isinstance(ref, dict) else ""
            ref_text = _reference_text(ref)
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
    final_report_record = format_final_report_record(_load_final_report_metadata())
    appendices = []
    for _authored in (REPORT_SECTIONS.get("appendix_sections") or []):
        if isinstance(_authored, dict) and str(_authored.get("title", "")).strip() and str(_authored.get("content", "")).strip():
            appendices.append((str(_authored["title"]).strip(), {"content": str(_authored["content"]), "has_markdown": True}))
    if final_report_record:
        appendices.append(("Final Report Record", {
            "content": final_report_record, "has_markdown": True}))
    if results and _headline_rows(results) and results.get("key_results"):
        appendices.append(("Complete Numerical Results", {
            "content": "", "has_all_results": True}))
    if reproducibility_text:
        appendices.append(("Reproducing the Results", {
            "content": reproducibility_text, "has_markdown": True}))
    if quality_lines:
        appendices.append(("Report Quality Checks", {
            "content": "\n".join(quality_lines), "has_markdown": True}))
    for position, (title, extra) in enumerate(appendices):
        section = {"heading": "{} {}. {}".format(
            _t("Appendix"), chr(ord("A") + position), _t(title)), "appendix": True}
        section.update(extra)
        sections.append(section)

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
    a named figure without scrolling the whole report. Short reports skip them
    (``report.front_matter_lists``) so the reader reaches the summary at once.
    """
    n_items = len(get_figures() or []) + len((results or {}).get("tables") or [])
    if REPORT_FRONT_LISTS == "never" or (REPORT_FRONT_LISTS == "auto" and n_items < FRONT_LISTS_MIN_ITEMS):
        return
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
    run4 = paragraph.add_run(_t("(Right-click and select 'Update Field' to populate)"))
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


def _heading_levels_by_style(doc):
    """Map paragraph style id -> heading level for 'Heading 1..9' styles."""
    levels = {}
    for style in doc.styles:
        match = re.match(r"^heading\s+(\d)$", (style.name or "").strip(), re.IGNORECASE)
        if match and style.style_id:
            levels[style.style_id] = int(match.group(1))
    return levels


def _collect_toc_entries(doc):
    """Return (headings [(level, text)], captions {seq label: [text]}) in document order."""
    levels = _heading_levels_by_style(doc)
    headings, captions = [], {}
    for p in doc.element.body.iter(qn("w:p")):
        instr = "".join(t.text or "" for t in p.iter(qn("w:instrText")))
        if instr.strip().startswith("TOC"):
            continue
        text = "".join(t.text or "" for t in p.iter(qn("w:t"))).strip()
        if not text:
            continue
        seq = re.search(r"\bSEQ\s+\"?([^\s\"\\]+)", instr)
        if seq:
            captions.setdefault(seq.group(1), []).append(text)
            continue
        style = p.find(qn("w:pPr") + "/" + qn("w:pStyle"))
        level = levels.get(style.get(qn("w:val"))) if style is not None else None
        if level:
            headings.append((level, text))
    return headings, captions


def _write_field_result(doc, field_p, entries, style_for_level):
    """Replace a TOC field's placeholder result with one paragraph per entry.

    The field keeps its begin/instr/separate runs in the first paragraph and its
    end run moves to the last one, so Word still sees one field and F9 or the
    automatic update replaces the whole result (adding page numbers).
    """
    runs = list(field_p.iter(qn("w:r")))
    kinds = [(r, r.find(qn("w:fldChar"))) for r in runs]
    sep = next(r for r, f in kinds if f is not None and f.get(qn("w:fldCharType")) == "separate")
    end = next(r for r, f in kinds if f is not None and f.get(qn("w:fldCharType")) == "end")
    for r in runs[runs.index(sep) + 1:runs.index(end) + 1]:
        field_p.remove(r)
    previous = field_p
    for index, (level, text) in enumerate(entries):
        if index == 0:
            p_el = field_p
        else:
            p_el = OxmlElement("w:p")
            previous.addnext(p_el)
        paragraph = Paragraph(p_el, doc._body)
        style_name = style_for_level(level)
        try:
            paragraph.style = doc.styles[style_name]
        except KeyError:
            paragraph.paragraph_format.left_indent = Inches(0.25 * (level - 1))
        paragraph.paragraph_format.space_after = Pt(2)
        paragraph.add_run(text)
        previous = p_el
    previous.append(end)


def _prefill_toc_fields(doc):
    """Write the current headings and captions into every TOC-family field result.

    Without this the contents, list of figures and list of tables show only a
    placeholder until someone updates fields in Word; viewers that never update
    fields (Word Online, Teams/SharePoint preview, LibreOffice) show nothing.
    Page numbers are added when Word updates the fields.
    """
    headings, captions = _collect_toc_entries(doc)
    for p in list(doc.element.body.iter(qn("w:p"))):
        instr = "".join(t.text or "" for t in p.iter(qn("w:instrText"))).strip()
        if not instr.startswith("TOC"):
            continue
        caption = re.search(r'\\c\s+"([^"]+)"', instr)
        if caption:
            entries = [(1, text) for text in captions.get(caption.group(1), [])]
            style_for_level = lambda level: "Table of Figures"  # noqa: E731
        else:
            outline = re.search(r'\\o\s+"(\d)-(\d)"', instr)
            deepest = int(outline.group(2)) if outline else 3
            entries = [(level, text) for level, text in headings if level <= deepest]
            style_for_level = lambda level: "TOC {}".format(level)  # noqa: E731
        if entries:
            _write_field_result(doc, p, entries, style_for_level)


_WORD_FIELD_UPDATE_SCRIPT = r'''
import sys
import pythoncom
import win32com.client
pythoncom.CoInitialize()
word = win32com.client.DispatchEx("Word.Application")
word.Visible = False
word.DisplayAlerts = 0
doc = word.Documents.Open(sys.argv[1], ConfirmConversions=False, ReadOnly=False,
                          AddToRecentFiles=False, Visible=False)
try:
    doc.Fields.Update()
    for i in range(1, doc.TablesOfContents.Count + 1):
        doc.TablesOfContents(i).Update()
    for i in range(1, doc.TablesOfFigures.Count + 1):
        doc.TablesOfFigures(i).Update()
    doc.Repaginate()
    # Filling the lists moves the body, so refresh page numbers once more.
    for i in range(1, doc.TablesOfContents.Count + 1):
        doc.TablesOfContents(i).UpdatePageNumbers()
    for i in range(1, doc.TablesOfFigures.Count + 1):
        doc.TablesOfFigures(i).UpdatePageNumbers()
    doc.Fields.Update()
    doc.Save()
finally:
    doc.Close(0)
    word.Quit()
    pythoncom.CoUninitialize()
'''


def _clear_update_fields_flag(docx_path):
    """Remove w:updateFields so Word does not ask to update an already-updated document."""
    import zipfile
    tmp_path = docx_path + ".tmp"
    with zipfile.ZipFile(docx_path) as src, zipfile.ZipFile(tmp_path, "w", zipfile.ZIP_DEFLATED) as dst:
        for item in src.infolist():
            data = src.read(item.filename)
            if item.filename == "word/settings.xml":
                data = re.sub(rb"<w:updateFields\b[^>]*/>", b"", data)
            dst.writestr(item, data)
    os.replace(tmp_path, docx_path)


def update_word_fields(docx_path, timeout=240):
    """Let Microsoft Word fill the TOC, lists of figures/tables and page numbers.

    Runs by default on Windows when Word and pywin32 are available; disable with
    ``--no-field-update`` or ``NEQSIM_REPORT_FIELD_UPDATE=0``. Word runs in a
    child process on a %TEMP% copy (Word automation can hang on OneDrive paths),
    and any failure leaves the pre-filled document, which still carries the
    update-on-open flag.

    Returns
    -------
    str or None
        ``None`` when Word updated the document, otherwise why it did not.
    """
    if "--no-field-update" in sys.argv or os.environ.get("NEQSIM_REPORT_FIELD_UPDATE", "1") == "0":
        return "disabled"
    if os.name != "nt":
        return "Microsoft Word automation is only available on Windows"
    try:
        import win32com.client  # noqa: F401
    except ImportError:
        return "pywin32 is not installed"
    import tempfile
    workdir = tempfile.mkdtemp(prefix="neqsim_fields_")
    work = os.path.join(workdir, "report.docx")
    try:
        shutil.copy2(docx_path, work)
        _clear_update_fields_flag(work)
        try:
            completed = subprocess.run([sys.executable, "-c", _WORD_FIELD_UPDATE_SCRIPT, work],
                                       stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                       timeout=timeout)
        except subprocess.TimeoutExpired:
            return "Word did not finish within {} s".format(timeout)
        if completed.returncode != 0:
            tail = completed.stdout.decode("utf-8", "replace").strip().splitlines()[-1:]
            return "Word automation failed ({})".format(tail[0] if tail else completed.returncode)
        try:
            shutil.copy2(work, docx_path)
        except PermissionError:
            return "{} is open in Word or locked by OneDrive".format(os.path.basename(docx_path))
        return None
    finally:
        shutil.rmtree(workdir, ignore_errors=True)


def finalize_word_fields(docx_path, label="Report"):
    """Update fields through Word and report the outcome in one line."""
    reason = update_word_fields(docx_path)
    if reason is None:
        print("{}: table of contents, lists of figures/tables and page numbers filled by Word.".format(label))
    elif reason != "disabled":
        print("{}: contents and lists pre-filled without page numbers ({}); "
              "Word adds page numbers when the file is opened.".format(label, reason))
    return reason is None


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


def _set_document_properties(doc, results):
    """Fill the document properties that PDF viewers and search indexes read."""
    props = doc.core_properties
    # python-docx rejects any property longer than 255 characters.
    props.title = str(TITLE)[:250]
    props.subject = "{} {}".format(_t("NeqSim Engineering Report"), _auto_doc_number()).strip()[:250]
    if AUTHOR:
        props.author = str(AUTHOR)[:250]
        props.last_modified_by = str(AUTHOR)[:250]
    props.keywords = KIT.report_keywords(
        ["NeqSim"], (value for _label, value in (STUDY_BADGES or [])),
        (results or {}).get("standards_applied") if isinstance(
            (results or {}).get("standards_applied"), list) else [])[:250]
    props.comments = KIT.plain_alt_text(TASK_STATEMENT, 250)
    props.category = str(CLASSIFICATION)[:250]
    props.identifier = str(_auto_doc_number())[:250]
    stamp = _build_fingerprint()
    if stamp["results"]:
        props.version = "results.json sha256:{}".format(stamp["results"])


def _add_figure_block(doc, fig_path, results, plan, alt_extra=""):
    """Picture, alt text and numbered caption (with a bookmark for cross-references)."""
    number = plan.number_of(fig_path)
    caption_text = get_figure_caption(fig_path, results, number)
    alt = KIT.plain_alt_text("{} {}".format(_strip_caption_prefix(caption_text), alt_extra))
    _add_figure_picture(doc, fig_path, alt=alt)
    _add_caption(doc, _t("Figure"), caption_text, bookmark=_figure_bookmark(fig_path))


def _add_display_omml(doc, label, latex):
    """Numbered display equation as an editable Word equation; False when unsupported."""
    if not EQUATIONS_AS_OMML:
        return False
    xml = KIT.latex_to_omml(_sanitize_equation_latex(latex))
    if not xml:
        return False
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
    paragraph = doc.add_paragraph()
    fmt = paragraph.paragraph_format
    fmt.space_before = Pt(2)
    fmt.space_after = Pt(8)
    fmt.keep_together = True
    fmt.tab_stops.add_tab_stop(Inches(measure / 2.0), WD_TAB_ALIGNMENT.CENTER)
    fmt.tab_stops.add_tab_stop(Inches(measure), WD_TAB_ALIGNMENT.RIGHT)
    paragraph.add_run("\t")
    try:
        paragraph._p.append(parse_xml(xml))
    except Exception:
        doc.element.body.remove(paragraph._p)
        return False
    paragraph.add_run("\t(")
    _add_seq_field(paragraph, _t("Equation"))
    paragraph.add_run(")")
    return True


def build_word_report(sections, results=None):
    """Build the Word document with cover page, TOC, numbered figures, and equations."""
    doc = _new_document()
    plan = get_figure_plan(results)
    _set_document_properties(doc, results)

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
        if section.get("has_all_results") and results:
            add_results_word_table(doc, results, full=True)
        elif section.get("has_figures") and results and results.get("key_results"):
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
        elif section.get("has_references") and results and results.get("references"):
            add_references_word(doc, results)
        else:
            # Regular text content (bold spans and $...$ inline maths render)
            for para_text in _word_paragraphs(section["content"]):
                _add_bold_runs(doc.add_paragraph(), para_text)

        # Embed figures after Results section (the discussed ones sit in Discussion)
        if section.get("has_figures"):
            if plan.results_figures:
                for fig_path in plan.results_figures:
                    _add_figure_block(doc, fig_path, results, plan)
            elif not plan.order:
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
                    if _add_display_omml(doc, label, latex):
                        continue
                    eq_img_path = os.path.join(eq_img_dir, "eq_{}.png".format(eq_idx))
                    if render_equation_to_image(latex, eq_img_path):
                        _add_display_equation(doc, eq_img_path, label,
                                              _text_width_in(doc))
                        _set_last_picture_alt(doc.paragraphs[-1], _latex_fallback_text(latex))
                    else:
                        _add_equation_fallback_paragraph(doc, label, latex)

    # Save
    _prefill_toc_fields(doc)
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


def _figure_html(fig_path, results, plan, alt_extra=""):
    """One numbered figure with an anchor, embedded image, alt text and caption."""
    number = plan.number_of(fig_path)
    caption_text = get_figure_caption(fig_path, results, number)
    mime = "image/svg+xml" if fig_path.endswith(".svg") else "image/png"
    with open(fig_path, "rb") as handle:
        img_data = base64.b64encode(handle.read()).decode("utf-8")
    alt = _html_escape(KIT.plain_alt_text(
        "{} {}".format(_strip_caption_prefix(caption_text), alt_extra)))
    return (
        '<figure class="figure" id="fig-{}">\n'
        '    <img src="data:{};base64,{}" alt="{}">\n'
        '    <figcaption class="caption">{}</figcaption>\n'
        '</figure>\n'.format(number, mime, img_data, alt, _html_escape(caption_text)))


def build_html_report(sections, results=None):
    """Build an HTML report with embedded figures, KaTeX equations, and navigation."""
    plan = get_figure_plan(results)

    # Figures without a discussion stay in Results; discussed ones sit in Discussion
    figure_html = ""
    if plan.results_figures:
        figure_html = "".join(_figure_html(path, results, plan)
                              for path in plan.results_figures)
    elif not plan.order:
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

        if section.get("has_all_results") and results:
            content = format_results_html(results, full=True)

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

    # KaTeX for equations and inline maths: bundled so the report also reads offline
    katex_head = ""
    katex_body_script = ""
    needs_math = bool(equations) or any(
        re.search(r"\$[^$\n]+\$", str(section.get("content", ""))) for section in sections)
    if needs_math:
        assets = KIT.katex_assets()
        if assets:
            css, katex_js, auto_js = assets
            katex_head = ("\n    <style>{}</style>\n    <script>{}</script>\n    <script>{}</script>"
                          .format(css, katex_js.replace("</script", "<\\/script"),
                                  auto_js.replace("</script", "<\\/script")))
        else:
            katex_head = """
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/katex@0.16.11/dist/katex.min.css">
    <script defer src="https://cdn.jsdelivr.net/npm/katex@0.16.11/dist/katex.min.js"></script>
    <script defer src="https://cdn.jsdelivr.net/npm/katex@0.16.11/dist/contrib/auto-render.min.js"></script>"""
    if needs_math:
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
    <title>{title}</title>
    <meta name="author" content="{meta_author}">
    <meta name="description" content="{meta_description}">
    <meta name="neqsim-results-sha256" content="{meta_fingerprint}">{katex_head}
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
        a.xref {{ color: #2F5496; text-decoration: none; border-bottom: 1px dotted #2F5496; }}
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
        meta_author=_html_escape(AUTHOR or "").replace('"', "&quot;"),
        meta_fingerprint=_build_fingerprint()["results"],
        meta_description=_html_escape(KIT.plain_alt_text(TASK_STATEMENT, 300)).replace('"', "&quot;"),
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
            ref_text = _reference_text(ref)
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
                ref_text = _reference_text(ref)
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
    study_config = load_study_config()
    try:
        REPORT_TEMPLATE = resolve_report_template(
            explicit_template, allow_saved="--no-template" not in sys.argv,
            configured=study_config.get("report", {}).get("template"))
    except (OSError, ValueError) as error:
        print("ERROR: {}".format(error))
        print("Fix the path, set report.template in study_config.yaml, pass --template PATH, or run:")
        print("  neqsim --set-report-template \"PATH\"   (or --reset-report-template)")
        sys.exit(2)

    # Auto-read task data
    results = load_results()
    task_spec = load_task_spec()
    record_environment(results)
    REPORT_LANGUAGE = resolve_report_language(study_config)  # before identity: badges are translated
    resolve_report_identity(study_config, task_spec, results)
    apply_report_output_names(TITLE)

    print("")
    print("Generating outputs for: {}".format(TITLE))
    REPORT_ORIENTATION = resolve_report_orientation(study_config)
    REPORT_FRONT_LISTS = resolve_front_matter_lists(study_config)
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
        finalize_word_fields(DOCX_FILE, "Report")
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
