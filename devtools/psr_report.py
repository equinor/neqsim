"""Word (python-docx) report builder for the process-study-report toolkit.

Builds the complete report from the extracted data set, the engineering basis,
the standards register, the balances and the diagram manifests. Every number in
narrative text is written through ``[[reference]]`` tokens that are resolved from
the data, so each quoted number is traceable (see ``Resolver``).

Token syntax (used in basis narrative strings and in generated text)::

    [[stream:S067:mass_kg_h:A]]          stream result, dotted keys allowed
    [[unit:K-100:power_kw:A|.1f]]        unit result with optional format
    [[balance:overall:A:mass_rel|.2e]]   balance value
    [[fact:power_total_A]]               derived roll-up (see ``compute_facts``)
"""
import datetime
import json
import math
import os
import re
import textwrap

from docx import Document
from docx.enum.section import WD_ORIENT, WD_SECTION
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_BREAK
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Mm, Pt, RGBColor

import psr_balances
import psr_connectivity
from psr_extract import fmt_value

REF_RE = re.compile(r"\[\[([^\]|]+)(?:\|([^\]]+))?\]\]")
NAVY = RGBColor(0x1F, 0x3A, 0x5F)
RED = RGBColor(0xB0, 0x1E, 0x1E)
GREY = RGBColor(0x55, 0x55, 0x55)
CATEGORY_TITLES = {"valve": "Valves", "mixer": "Mixers and headers", "splitter": "Splitters", "separator": "Separators and scrubbers",
                   "cooler": "Coolers", "heater": "Heaters", "exchanger": "Heat exchangers", "compressor": "Compressors",
                   "pump": "Pumps", "expander": "Expanders", "ejector": "Ejectors", "recycle": "Recycle (convergence) elements",
                   "column": "Columns", "pipe": "Pipes", "reactor": "Reactors", "other": "Other units"}


def _fmt_optional(value, fmt):
    """Format a number with ``fmt``; ``None``/non-numeric (e.g. an absolute-tolerance validation row) prints ``n/a``."""
    if value is None:
        return "n/a"
    try:
        return fmt % float(value)
    except (TypeError, ValueError):
        return str(value)


# ── reference resolution ─────────────────────────────────────

def _dotget(obj, path):
    cur = obj
    for part in path.split("."):
        if isinstance(cur, dict) and part in cur:
            cur = cur[part]
        else:
            return None
    return cur


def compute_facts(data, balances):
    """Derived roll-ups used by ``[[fact:KEY]]`` tokens (all computed from data and balances)."""
    facts = {"n_areas": len(data["areas"]), "n_units": len(data["equipment"]), "n_streams": len(data["streams"]),
             "n_components": len(data["components"]), "n_cases": len(data["cases"]),
             "n_pseudo": sum(1 for c in data["components"] if c.get("is_tbp") or c.get("is_plus")),
             "n_feeds": sum(1 for s in data["streams"] if s["kind"] == "feed"),
             "n_products": sum(1 for s in data["streams"] if s["kind"] == "product")}
    for case in data["cases"]:
        cid = case["id"]
        ov = balances["cases"][cid]["overall"]
        facts["feed_mass_" + cid] = ov["mass_in_kg_h"]
        facts["product_mass_" + cid] = ov["mass_out_kg_h"]
        facts["mass_res_" + cid] = ov["mass_res_kg_h"]
        facts["mass_rel_pct_" + cid] = 100.0 * ov["mass_rel"]
        facts["energy_res_" + cid] = ov["energy_res_kw"]
        facts["energy_rel_pct_" + cid] = 100.0 * ov["energy_rel"]
        _rows, totals = psr_balances.utility_summary(data, cid)
        facts["power_total_" + cid] = totals["power_in_kw"]
        facts["cooling_total_" + cid] = -totals["cooling_kw"]
        facts["heating_total_" + cid] = totals["heating_kw"]
        units = balances["cases"][cid]["units"]
        facts["max_unit_mass_rel_pct_" + cid] = 100.0 * max([u["mass_rel"] for u in units] or [0.0])
        facts["n_unit_fail_" + cid] = sum(1 for u in units for k in ("mass_status", "component_status", "energy_status") if u[k] == "FAIL")
        facts["n_unit_checks_" + cid] = sum(1 for u in units for k in ("mass_status", "component_status", "energy_status") if u[k] != "N/A")
        facts["n_unit_note_" + cid] = sum(1 for u in units for k in ("mass_status", "component_status", "energy_status") if u[k] == "NOTE")
    facts["n_balance_fail_total"] = balances["summary"]["FAIL"]
    facts["n_balance_note_total"] = balances["summary"].get("NOTE", 0)
    facts["n_balance_pass_total"] = balances["summary"]["PASS"]
    return facts


def significant(value, digits):
    """Fixed-point text with ``digits`` significant figures (never an exponent), e.g. 0.7517 -> 0.7517, 60012 -> 60010."""
    if value == 0:
        return "0"
    decimals = max(0, digits - 1 - int(math.floor(math.log10(abs(value)))))
    return "%.*f" % (decimals, value)


class Resolver:
    """Resolves ``[[...]]`` tokens against the data and records every use."""

    def __init__(self, data, balances, basis, facts):
        self.streams = {s["id"]: s for s in data["streams"]}
        self.units = {u["tag"]: u for u in data["equipment"]}
        self.balances, self.basis, self.facts = balances, basis, facts
        self.used = []
        self.errors = []

    def value(self, ref):
        parts = ref.split(":")
        kind = parts[0]
        try:
            if kind == "stream":
                return _dotget(self.streams[parts[1]]["results"][parts[3]], parts[2])
            if kind == "unit":
                return _dotget(self.units[parts[1]]["results"][parts[3]], parts[2])
            if kind == "balance":
                block = self.balances["cases"][parts[2]]
                return _dotget(block["overall"], parts[3]) if parts[1] == "overall" else None
            if kind == "fact":
                return self.facts[parts[1]]
            if kind == "basis":
                return _dotget(self.basis, parts[1])
        except (KeyError, IndexError):
            return None
        return None

    def text(self, ref, spec=None):
        val = self.value(ref)
        if val is None:
            self.errors.append(ref)
            return "n/a"
        if isinstance(val, (int, float)):
            out = significant(float(val), int(spec[3:])) if spec and spec.startswith("sig") else (format(val, spec) if spec else fmt_value(float(val), 4))
            if not spec and float(val).is_integer() and abs(val) < 1e7:
                out = "%d" % val
        else:
            out = str(val)
        self.used.append({"ref": ref, "value": val, "text": out})
        return out

    def expand(self, text):
        return REF_RE.sub(lambda m: self.text(m.group(1), m.group(2)), str(text or ""))


# ── low-level docx helpers ───────────────────────────────────

def _shade(cell, hex_fill):
    tc_pr = cell._tc.get_or_add_tcPr()
    shd = OxmlElement("w:shd")
    shd.set(qn("w:val"), "clear")
    shd.set(qn("w:color"), "auto")
    shd.set(qn("w:fill"), hex_fill)
    tc_pr.append(shd)


def _repeat_header(row):
    tr_pr = row._tr.get_or_add_trPr()
    el = OxmlElement("w:tblHeader")
    el.set(qn("w:val"), "true")
    tr_pr.append(el)
    cant = OxmlElement("w:cantSplit")
    tr_pr.append(cant)


def _add_field(paragraph, instr, cached="1", bold=False):
    def run_with(child):
        run = paragraph.add_run()
        run.bold = bold
        run._r.append(child)
        return run

    begin = OxmlElement("w:fldChar")
    begin.set(qn("w:fldCharType"), "begin")
    run_with(begin)
    instr_el = OxmlElement("w:instrText")
    instr_el.set(qn("xml:space"), "preserve")
    instr_el.text = " %s " % instr
    run_with(instr_el)
    sep = OxmlElement("w:fldChar")
    sep.set(qn("w:fldCharType"), "separate")
    run_with(sep)
    res = paragraph.add_run(cached)
    res.bold = bold
    end = OxmlElement("w:fldChar")
    end.set(qn("w:fldCharType"), "end")
    run_with(end)


def _set_cell(cell, text, size, bold=False, align=None, color=None):
    par = cell.paragraphs[0]
    par.style = cell.part.document.styles["PSR Table"]
    run = par.add_run(str(text))
    run.font.size = Pt(size)
    run.bold = bold
    if color is not None:
        run.font.color.rgb = color
    if align == "right":
        par.alignment = WD_ALIGN_PARAGRAPH.RIGHT
    elif align == "center":
        par.alignment = WD_ALIGN_PARAGRAPH.CENTER


class ReportBuilder:
    """Assembles the report document. Call :meth:`build` once."""

    KEYWORDS = "process study; NeqSim; PFD; stream table"
    GENERATOR_COMMENT = "Generated by the process-study-report toolkit"

    def __init__(self, data, basis, standards, balances, diagrams, validation=None, options=None):
        self.data, self.basis, self.standards = data, basis, standards
        self.balances, self.diagrams, self.validation = balances, diagrams, validation
        self.opt = options or {}
        self.facts = compute_facts(data, balances)
        self.res = Resolver(data, balances, basis, self.facts)
        self._init_document()
        self.cases = [c["id"] for c in data["cases"]]
        self.S = {s["id"]: s for s in data["streams"]}
        self.U = {u["tag"]: u for u in data["equipment"]}

    def _init_document(self):
        """Create the Word document and the counters shared by every report type (also used by subclasses)."""
        self.doc = Document()
        self.fig_n = 0
        self.tab_n = 0
        self.captions = {"Figure": [], "Table": []}
        self.headings = []
        self.exec_summary_text = []
        self._setup_styles()

    # ── structure helpers ──
    def _setup_styles(self):
        st = self.doc.styles
        normal = st["Normal"]
        normal.font.name = "Calibri"
        normal.font.size = Pt(10)
        normal.paragraph_format.space_after = Pt(5)
        for name, size in (("Heading 1", 16), ("Heading 2", 13), ("Heading 3", 11)):
            st[name].font.name = "Calibri"
            st[name].font.size = Pt(size)
            st[name].font.color.rgb = NAVY
            st[name].font.bold = True
        tbl = st.add_style("PSR Table", 1)
        tbl.base_style = normal
        tbl.paragraph_format.space_after = Pt(0)
        tbl.paragraph_format.space_before = Pt(0)
        cap = st["Caption"]
        cap.font.size = Pt(9)
        cap.font.bold = True
        cap.font.color.rgb = NAVY

    def H(self, text, level=1):
        self.headings.append((level, text))
        return self.doc.add_heading(text, level=level)

    def P(self, text, bold=False, italic=False, size=None, color=None, align=None, style=None, track=None):
        text = self.res.expand(text)
        par = self.doc.add_paragraph(style=style)
        run = par.add_run(text)
        run.bold, run.italic = bold, italic
        if size:
            run.font.size = Pt(size)
        if color is not None:
            run.font.color.rgb = color
        if align == "center":
            par.alignment = WD_ALIGN_PARAGRAPH.CENTER
        if track is not None:
            track.append(text)
        return par

    def bullets(self, items, track=None):
        for item in items:
            self.P(item, style="List Bullet", track=track)

    def caption(self, kind, text):
        if kind == "Figure":
            self.fig_n += 1
            n = self.fig_n
        else:
            self.tab_n += 1
            n = self.tab_n
        par = self.doc.add_paragraph(style="Caption")
        par.paragraph_format.keep_with_next = kind == "Table"
        par.add_run("%s " % kind)
        _add_field(par, "SEQ %s \\* ARABIC" % kind, str(n), bold=True)
        par.add_run(" - %s" % text)
        self.captions[kind].append("%s %d - %s" % (kind, n, text))
        return par

    def table(self, header, rows, caption=None, widths=None, font=8, align=None, first_bold=False, shade_header="1F3A5F"):
        if caption:
            self.caption("Table", caption)
        ncols = len(header)
        tbl = self.doc.add_table(rows=1, cols=ncols)
        tbl.style = "Table Grid"
        tbl.alignment = WD_TABLE_ALIGNMENT.CENTER
        tbl.autofit = False
        for i, h in enumerate(header):
            cell = tbl.rows[0].cells[i]
            _set_cell(cell, h, font, bold=True, align="center", color=RGBColor(0xFF, 0xFF, 0xFF))
            _shade(cell, shade_header)
        _repeat_header(tbl.rows[0])
        for r in rows:
            cells = tbl.add_row().cells
            for i, v in enumerate(r):
                _set_cell(cells[i], v, font, bold=first_bold and i == 0,
                          align=(align[i] if align else ("right" if i > 0 and _numlike(v) else None)))
        if not widths:
            widths = self._auto_widths(header, rows)
        avail = self.usable_width() * 0.995
        total = float(sum(widths))
        if total > avail:                                  # never let a table run over the page margins
            widths = [w * avail / total for w in widths]
        for row in tbl.rows:
            for i, w in enumerate(widths):
                row.cells[i].width = Mm(w)
        self.doc.add_paragraph().paragraph_format.space_after = Pt(2)
        return tbl

    def _auto_widths(self, header, rows):
        """Column widths (mm) in proportion to the content length (longest header word, 0.7 x mean cell length), scaled to the usable width."""
        weights = []
        for i, h in enumerate(header):
            lens = [len(str(r[i])) for r in rows[:300] if i < len(r)] or [0]
            word = max([len(w) for w in str(h).split()] or [4])
            weights.append(min(40.0, max(5.0, word * 1.0, 0.7 * sum(lens) / len(lens))))
        avail = self.usable_width() * 0.995
        total = sum(weights)
        return [w * avail / total for w in weights]

    def section(self, landscape=False, a3=False):
        sec = self.doc.add_section(WD_SECTION.NEW_PAGE)
        if landscape:
            sec.orientation = WD_ORIENT.LANDSCAPE
            sec.page_width, sec.page_height = (Mm(420), Mm(297)) if a3 else (Mm(297), Mm(210))
            margin = 8 if a3 else 12
        else:
            sec.orientation = WD_ORIENT.PORTRAIT
            sec.page_width, sec.page_height = Mm(210), Mm(297)
            margin = 20
        sec.left_margin = sec.right_margin = Mm(margin)
        sec.top_margin = Mm(16 if landscape else 20)
        sec.bottom_margin = Mm(14 if landscape else 18)
        sec.header_distance = Mm(6)
        sec.footer_distance = Mm(6)
        return sec

    def usable_width(self, sec=None):
        sec = sec or self.doc.sections[-1]
        return (sec.page_width - sec.left_margin - sec.right_margin) / 36000.0

    def picture(self, path, width_mm, alt, caption):
        run = self.doc.add_paragraph().add_run()
        pic = run.add_picture(path, width=Mm(width_mm))
        doc_pr = pic._inline.docPr
        doc_pr.set("descr", alt)
        doc_pr.set("title", caption[:80])
        self.doc.paragraphs[-1].alignment = WD_ALIGN_PARAGRAPH.CENTER
        self.caption("Figure", caption)
        self.alt_texts = getattr(self, "alt_texts", [])
        self.alt_texts.append(alt)

    # ── front matter ──
    def cover(self):
        d = self.basis["document"]
        sec = self.doc.sections[0]
        sec.page_width, sec.page_height = Mm(210), Mm(297)
        sec.left_margin = sec.right_margin = Mm(20)
        sec.top_margin, sec.bottom_margin = Mm(20), Mm(18)
        self.P(d.get("company", ""), bold=True, size=12, color=GREY)
        for _ in range(4):
            self.doc.add_paragraph()
        self.P(d["title"], bold=True, size=26, color=NAVY)
        self.P(d.get("subtitle", ""), size=14, color=GREY)
        self.doc.add_paragraph()
        banner = self.P("%s - %s" % (d["readiness"].upper(), d.get("readiness_note", "not for design or construction")),
                        bold=True, size=13, color=RED)
        banner.paragraph_format.space_before = Pt(18)
        self.doc.add_paragraph()
        rows = [("Document number", d["document_number"]), ("Revision", d["revision"]), ("Issue status", d["issue_status"]),
                ("Date", d["date"]), ("Project", d["project"]), ("Prepared by", d.get("prepared_by", "not assigned")),
                ("Checked by", d.get("checked_by", "not assigned")), ("Approved by", d.get("approved_by", "not assigned"))]
        self.table(["Item", "Value"], rows, widths=[45, 110], font=10, align=["left", "left"], first_bold=True)

    def document_control(self):
        d = self.basis["document"]
        self.H("Document control", 1)
        self.P("Revision history", bold=True)
        rows = [(r.get("rev"), r.get("date"), r.get("description"), r.get("by", "not assigned")) for r in d.get("revision_history", [])]
        self.table(["Rev", "Date", "Description", "By"], rows, widths=[15, 28, 100, 30], font=9, align=["left"] * 4)
        self.P("Issue status and readiness", bold=True)
        self.P("Issue status: %s. Readiness: %s. %s" % (d["issue_status"], d["readiness"], d.get("readiness_explanation", "")))
        self.P("This report documents the output of a simulation model. It is not an approved design document; "
               "results are valid only within the stated basis, assumptions and data gaps.", italic=True)

    def contents(self):
        self.H("Contents", 1)
        par = self.doc.add_paragraph()
        _add_field(par, 'TOC \\o "1-2" \\h \\z \\u', "Contents - update the field (F9) if this line is visible.")
        self.toc_par = par
        self.H("List of figures", 1)
        par = self.doc.add_paragraph()
        _add_field(par, 'TOC \\h \\z \\c "Figure"', "List of figures - update the field (F9) if this line is visible.")
        self.lof_par = par
        self.H("List of tables", 1)
        par = self.doc.add_paragraph()
        _add_field(par, 'TOC \\h \\z \\c "Table"', "List of tables - update the field (F9) if this line is visible.")
        self.lot_par = par

    # ── main sections ──
    def executive_summary(self):
        b, f = self.basis, self.facts
        self.H("1 Executive summary", 1)
        track = self.exec_summary_text
        self.P("Objective", bold=True)
        self.P(b["objective"], track=track)
        self.P("Principal results", bold=True)
        self.P("The model comprises [[fact:n_areas]] process areas, [[fact:n_units]] unit operations and [[fact:n_streams]] "
               "material streams ([[fact:n_feeds]] boundary feeds and [[fact:n_products]] boundary products) over "
               "[[fact:n_components]] components, of which [[fact:n_pseudo]] are characterised pseudo-components. "
               "All streams were solved for [[fact:n_cases]] operating cases.", track=track)
        header = ["Quantity"] + ["Case %s" % c for c in self.cases]
        rows = [
            ["Boundary feed, mass flow (kg/h)"] + ["[[fact:feed_mass_%s|sig4]]" % c for c in self.cases],
            ["Boundary products, mass flow (kg/h)"] + ["[[fact:product_mass_%s|sig4]]" % c for c in self.cases],
            ["Overall mass balance residual (% of feed)"] + ["[[fact:mass_rel_pct_%s|.2e]]" % c for c in self.cases],
            ["Overall energy balance residual (kW)"] + ["[[fact:energy_res_%s|sig4]]" % c for c in self.cases],
            ["Compressor and pump shaft power (kW)"] + ["[[fact:power_total_%s|sig4]]" % c for c in self.cases],
            ["Cooling duty (kW, removed)"] + ["[[fact:cooling_total_%s|sig4]]" % c for c in self.cases],
            ["Heating duty (kW, added)"] + ["[[fact:heating_total_%s|sig4]]" % c for c in self.cases]]
        rows = [[self.res.expand(c) for c in r] for r in rows]
        self.table(header, rows, caption="Principal results by operating case (all values computed from the extracted model data)",
                   widths=[75] + [30] * len(self.cases), font=9)
        for r in rows:
            track.append(" ".join(r))
        for item in b.get("principal_findings", []):
            self.P(item, style="List Bullet", track=track)
        self.P("Limitations", bold=True)
        gaps = b.get("data_gaps", [])
        self.P("[[fact:n_unit_fail_%s]] of [[fact:n_unit_checks_%s]] unit-level balance checks in case %s exceed the stated "
               "tolerance and [[fact:n_unit_note_%s]] are classified as NeqSim bookkeeping residuals (not closure errors); "
               "see Section 8.4." % (self.cases[0], self.cases[0], self.cases[0], self.cases[0]), track=track)
        for gap in gaps[:int(self.opt.get("exec_gap_count", 6))]:
            self.P("%s: %s" % (gap["item"].rstrip("."), gap["effect_on_conclusions"]), style="List Bullet", track=track)
        self.P("The report is issued as %s. See Section 3.6 for the complete list of data gaps." % b["document"]["readiness"], track=track)
        self.P("Recommendations", bold=True)
        for item in b.get("recommendations", []):
            self.P(item, style="List Bullet", track=track)

    def scope_and_basis(self):
        b = self.basis
        self.H("2 Scope, objectives and operating cases", 1)
        self.H("2.1 Study objective, audience and maturity", 2)
        self.P("Objective: " + b["objective"])
        self.P("Intended audience: " + b["audience"])
        self.P("Study maturity: " + b["maturity"])
        self.H("2.2 Process boundaries", 2)
        bd = b["boundaries"]
        self.P("Included in the model:", bold=True)
        self.bullets(bd["included"])
        self.P("Excluded from the model:", bold=True)
        self.bullets(bd["excluded"])
        if bd.get("unmodelled_systems"):
            self.P("Systems that exist in the plant but are not modelled (not represented in the PFDs):", bold=True)
            self.bullets(bd["unmodelled_systems"])
        self.H("2.3 Operating cases", 2)
        rows = [(c["id"], c["label"], c["role"], c["description"], c["data_source"]) for c in b["operating_cases"]]
        self.table(["Case", "Label", "Role", "Description", "Data source"], rows, caption="Operating cases", font=8,
                   widths=[12, 32, 22, 70, 36], align=["left"] * 5)
        self.H("2.4 Acceptance criteria", 2)
        rows = []
        for c in b["acceptance_criteria"]:
            rows.append((c["id"], self.res.expand(c["text"]), c["requirement_class"], c["source"],
                         self._criterion_status(c)))
        self.table(["Id", "Criterion", "Class", "Source", "Status"], rows, caption="Acceptance criteria and status", font=8,
                   widths=[12, 70, 22, 38, 30], align=["left"] * 5)

    def _criterion_status(self, crit):
        chk = crit.get("check")
        if not chk:
            return crit.get("status", "not assessed")
        metric, limit = chk["metric"], float(chk["max"])
        vals = []
        for cid in self.cases:
            blocks = self.balances["cases"][cid]
            if metric == "overall_mass_rel":
                vals.append(blocks["overall"]["mass_rel"])
            elif metric == "overall_energy_rel":
                vals.append(blocks["overall"]["energy_rel"])
            elif metric == "unit_mass_rel":
                vals.append(max([u["mass_rel"] for u in blocks["units"]] or [0.0]))
        return "met (all cases)" if vals and max(vals) <= limit else "NOT met"

    def design_basis(self):
        b = self.basis
        self.H("3 Design basis", 1)
        self.H("3.1 Sources inspected", 2)
        rows = [(s["id"], s["kind"], s["title"], s.get("version", "n/a"), s["status"], s.get("used_for", "")) for s in b["inspected_sources"]]
        self.table(["Id", "Kind", "Title", "Version / date", "Status", "Used for"], rows, caption="Sources inspected for the study",
                   font=8, widths=[10, 22, 52, 28, 28, 40], align=["left"] * 6)
        self.H("3.2 Conventions", 2)
        c = b["conventions"]
        std = c["standard_conditions"]
        rows = [("Units", c["units"]), ("Pressure", c["pressure"]), ("Composition basis", c["composition_basis"]),
                ("Standard conditions", "%.2f C, %.5f bara. %s" % (std["t_c"], std["p_bara"], std.get("definition", ""))),
                ("Phase-fraction basis", c["phase_fraction_basis"]), ("Heat sign convention", c["heat_sign"]),
                ("Work sign convention", c["work_sign"]), ("Enthalpy reference", c["enthalpy_reference"]),
                ("Number formatting", "Exact zero is printed 0; positive values below 1e-6 are printed <1e-6; "
                 "n/a = not available from the model; not applicable = quantity undefined for the stream.")]
        self.table(["Convention", "Definition"], rows, caption="Units, bases and sign conventions", font=8, widths=[40, 130],
                   align=["left", "left"], first_bold=True)
        self.H("3.3 Classification of information", 2)
        rows = [(i["item"], i["class"], i.get("note", "")) for i in b["data_classification"]]
        self.table(["Item", "Class", "Note"], rows, caption="Measured, specified, calculated, estimated and unavailable information",
                   font=8, widths=[60, 24, 86], align=["left"] * 3)
        self.H("3.4 Assumptions", 2)
        rows = [(a["id"], a["text"], a.get("effect", "")) for a in b["assumptions"]]
        self.table(["Id", "Assumption", "Effect"], rows, caption="Assumptions", font=8, widths=[12, 90, 68], align=["left"] * 3)
        self.H("3.5 Exclusions", 2)
        self.bullets(b["exclusions"])
        self.H("3.6 Data gaps and their effect on conclusions", 2)
        rows = [(g["id"], g["item"], g["effect_on_conclusions"], g.get("mitigation", "")) for g in b["data_gaps"]]
        self.table(["Id", "Missing information", "Effect on conclusions", "Mitigation"], rows, caption="Data gaps", font=8,
                   widths=[12, 60, 60, 38], align=["left"] * 4)

    def standards_section(self):
        self.H("4 Standards and conventions", 1)
        self.P("The study applies the conventions of the standards below. Editions and status were checked on the dates "
               "given; paywalled texts are not reproduced and compliance is claimed only where it can be evidenced.")
        rows = [(s["reference"], s["edition"], s["status"], s["requirement_class"], s["application"], s["compliance_statement"])
                for s in self.standards]
        self.table(["Reference", "Edition", "Status", "Class", "Application in this study", "Compliance statement"], rows,
                   caption="Standards register (summary; full register in Appendix E)", font=7.5,
                   widths=[26, 22, 22, 18, 42, 42], align=["left"] * 6)

    def process_description(self):
        self.H("5 Process description", 1)
        self.P("Each paragraph lists the unit operations of one model area in model order, with the equipment tag, the type and the "
               "numbered streams it receives and delivers. Stream numbers (S...) refer to the stream tables in Appendix B and to "
               "the PFDs in Section 7; tags refer to the equipment register in Appendix A.")
        narrative = self.basis.get("process_description", {})
        for k, area in enumerate(self.data["areas"], 1):
            self.H("5.%d %s" % (k, area["name"].capitalize()), 2)
            if area["name"] in narrative:
                self.P(narrative[area["name"]])
            units = [u for u in self.data["equipment"] if u["area"] == area["name"]]
            for u in units:
                ins = ", ".join("%s" % s for s in u["inlet_streams"]) or "-"
                outs = ", ".join("%s" % s for s in u["outlet_streams"]) or "-"
                self.P("%s (%s): in %s; out %s." % (u["tag"], u["category"], ins, outs), style="List Bullet")

    def methodology(self):
        b, m = self.basis, self.basis["model"]
        self.H("6 Simulation methodology", 1)
        self.H("6.1 Model and software", 2)
        rows = [("Model", m["name"]), ("Model version", m["version"]), ("Simulator", m["simulator"]),
                ("Python library", self.data["meta"].get("neqsim_version", "unknown")),
                ("Equation of state (from model)", self.data["meta"].get("eos", "unknown")),
                ("Equation of state (basis)", m["eos"]), ("Mixing rule", m["mixing_rule"]),
                ("Convergence", m["convergence"])]
        self.table(["Item", "Value"], rows, caption="Model identification", font=8, widths=[45, 125], align=["left", "left"], first_bold=True)
        self.H("6.2 Thermodynamic model selection and fluid characterisation", 2)
        self.P(m.get("eos_rationale", ""))
        self.P(m["fluid_characterization"])
        pseudo = [c for c in self.data["components"] if c.get("is_tbp") or c.get("is_plus")]
        rows = [(c["name"], fmt_value(c["mw_g_mol"], 4), fmt_value(c["tc_k"], 5), fmt_value(c["pc_bara"], 4), fmt_value(c["omega"], 4),
                 fmt_value(c["tb_k"], 5), fmt_value(c["rho_liq_g_cm3"], 4)) for c in pseudo]
        if rows:
            self.table(["Pseudo-component", "MW g/mol", "Tc K", "Pc bara", "Acentric factor", "Tb K", "Liquid density g/cm3"], rows,
                       caption="Characterised heavy-end pseudo-components (properties read from the solved model fluid)",
                       font=8, widths=[34, 20, 20, 20, 24, 20, 30])
        self.P("All %d components, including pure components, are tabulated in Appendix C." % len(self.data["components"]))
        self.H("6.3 Convergence and balance closure", 2)
        self.P("Mass, component and energy balances were recomputed from the extracted stream data. Tolerances: %s. "
               "Energy balance: sum(H_out) - sum(H_in) = Q + W with %s. Overall closure by case is given in Section 8.4." %
               (", ".join("%s = %s" % (k, v) for k, v in self.balances["tolerances"].items()), self.balances["sign_convention"]))
        self.H("6.4 Validation against measured data", 2)
        val = b.get("validation", {})
        self.P(val.get("statement", "No measured data comparison is part of this report."))
        rows = [(r["name"], r["window"], r["kind"], fmt_value(r["measured"], 5), fmt_value(r["model"], 5), r["unit"],
                 _fmt_optional(r.get("error_pct"), "%+.1f"), _fmt_optional(r.get("tolerance_pct"), "%.0f"), r["status"])
                for r in val.get("rows", [])]
        if rows:
            self.table(["Quantity", "Case", "Kind", "Measured", "Model", "Unit", "Error %", "Tol. %", "Status"], rows,
                       caption="Model versus measured data (quoted from the preceding calibration study; not recomputed here)",
                       font=7, widths=[52, 10, 20, 17, 17, 12, 13, 11, 12])
        self._connectivity_section()

    def _connectivity_section(self):
        audit = psr_connectivity.audit_connectivity(self.data)
        s = audit["summary"]
        wired = (self.data.get("meta") or {}).get("wiring_applied") or []
        self.H("6.5 Model connectivity", 2)
        if audit["status"] == "PASS":
            self.P("Every unit of the model has inlet and outlet streams as required by its type; no isolated streams were found.")
        else:
            self.P("The connectivity audit of the solved model found %d error(s) and %d warning(s). Drawings and stream tables show the "
                   "wiring that the model contains; units listed below are drawn with a 'not connected' note and their balances "
                   "are not meaningful until the connections are restored. The full list with repair candidates is in "
                   "data/connectivity_audit.csv." % (s["n_error"], s["n_warning"]), bold=True)
            rows = [(code, str(n), psr_connectivity.CODE_TEXT[code]) for code, n in sorted(s["by_code"].items())]
            self.table(["Finding", "Count", "Meaning"], rows, caption="Connectivity findings by type", font=7.5,
                       widths=[40, 14, 116], align=["left", "right", "left"])
            rows = [(f["area"], f["name"], f["code"], "; ".join("%s %s" % (c["stream"], c["label"]) for c in f["candidates"][:2]) or "-")
                    for f in audit["findings"] if f["kind"] == "unit"][:60]
            if rows:
                self.table(["Area", "Unit", "Finding", "Repair candidates (unverified)"], rows,
                           caption="Units with connectivity findings (first 60; candidates ranked by name similarity)", font=7,
                           widths=[28, 44, 34, 64], align=["left"] * 4)
        if wired:
            rows = [(w["unit"], w["stream"], w["method"]) for w in wired]
            self.P("Connections added by the analyst through the wiring list of cases.yaml (model repair, not source data):", bold=True)
            self.table(["Unit", "Attached stream", "Method"], rows, caption="Wiring repairs applied before the run", font=7.5,
                       widths=[60, 80, 30], align=["left"] * 3)

    def diagrams_section(self, figure_dir):
        self.H("7 Block flow diagram and process flow diagrams", 1)
        self.P("The block flow diagram (BFD) shows the model areas and the aggregated flows between them for the first operating case. "
               "The PFD set shows every modelled unit and every numbered material stream. Symbols are schematic and follow ISO 10628-2 "
               "/ ISO 14617 conventions; conformance is not claimed. Off-page connectors name the sheet that continues a stream. Editable "
               "sources (SVG, layout specification) and the A3 PDF drawing set are delivered next to this report.")
        if self.opt.get("omitted_note"):
            self.P(self.opt["omitted_note"], italic=True)
        for key, title in self.diagrams.get("pages", []):
            sec = self.section(landscape=True, a3=True)
            self.picture(key, 362, alt=title, caption=title)

    def results_section(self, figure_dir):
        data = self.data
        self.section()
        self.H("8 Results", 1)
        self.H("8.1 Boundary streams", 2)
        self.P("Boundary feeds and products by case (mass flow, kg/h). All streams are listed in Appendix B.")
        feeds = [s for s in data["streams"] if s["kind"] in ("feed", "product")]
        rows = []
        for s in feeds:
            rows.append([s["id"], s["label"][:34], s["kind"]] + [fmt_value(s["results"][c].get("mass_kg_h"), 5) for c in self.cases])
        self.table(["Stream", "Label", "Kind"] + ["Mass kg/h, %s" % c for c in self.cases], rows,
                   caption="Boundary feed and product streams", font=7.5, widths=[14, 54, 14] + [28] * len(self.cases), align=["left"] * 3 + ["right"] * len(self.cases))
        self.H("8.2 Unit-operation summaries", 2)
        for cat in ("compressor", "pump", "cooler", "heater", "exchanger", "separator", "valve", "mixer", "splitter", "ejector", "recycle"):
            units = [u for u in data["equipment"] if u["category"] == cat]
            if not units:
                continue
            self.P(CATEGORY_TITLES.get(cat, cat), bold=True)
            header, rows = self._unit_summary(cat, units)
            self.table(header, rows, caption="%s: key results by case" % CATEGORY_TITLES.get(cat, cat), font=7.5)
        self.H("8.3 Power, heating, cooling and utility summary", 2)
        rows = []
        for cid in self.cases:
            _u, totals = psr_balances.utility_summary(data, cid)
            rows.append([cid, fmt_value(totals["power_in_kw"], 5), fmt_value(-totals["cooling_kw"], 5), fmt_value(totals["heating_kw"], 5)])
        self.table(["Case", "Shaft power to fluid (kW)", "Cooling duty removed (kW)", "Heating duty added (kW)"], rows,
                   caption="Utility roll-up by case (duties as reported by the units)", font=8, widths=[20, 50, 50, 50])
        self.P("Utility media (cooling water, heating medium, electricity or fuel) are not modelled; the duties are the process-side "
               "demands only. Driver efficiencies and utility consumption are therefore not available from this model.", italic=True)
        self.H("8.4 Heat and material balances", 2)
        self._balances_tables()
        self.H("8.5 Case comparison", 2)
        self._case_comparison(figure_dir)
        self.H("8.6 Sensitivities", 2)
        for sens in self.basis.get("sensitivities", []):
            wide = len(sens.get("columns") or []) >= 9          # wide tables go to a landscape page and back
            if wide:
                self.section(landscape=True)
            self.P(sens["title"], bold=True)
            self.P(self.res.expand(sens["description"]))
            self.P("Source: " + sens["source"], italic=True, size=9)
            if sens.get("columns"):
                self.table(sens["columns"], [[str(c) for c in r] for r in sens["rows"]], caption=sens["title"], font=7.5 if wide else 8)
            if wide:
                self.section()
        self.H("8.7 Constraints and utilisation", 2)
        for tab in self.basis.get("constraint_tables", []):
            wide = len(tab.get("columns") or []) >= 9
            if wide:
                self.section(landscape=True)
            self.P(self.res.expand(tab.get("description", "")))
            self.P("Source: " + tab["source"], italic=True, size=9)
            self.table(tab["columns"], [[str(c) for c in r] for r in tab["rows"]], caption=tab["title"], font=7.5 if wide else 8)
            if wide:
                self.section()
        self.H("8.8 Engineering interpretation", 2)
        for block in self.basis.get("interpretation", []):
            self.P(block["topic"], bold=True)
            self.P(block["text"])

    def _end_result(self, unit, key, case):
        """Return the results of the first inlet or outlet stream of a unit, or an empty dict when it has none."""
        names = unit.get(key) or []
        return self.S[names[0]]["results"][case] if names else {}

    def _unit_summary(self, cat, units):
        cs = self.cases
        if cat == "compressor":
            header = ["Tag", "Case", "P in (bara)", "P out (bara)", "Pressure ratio", "Shaft power (kW)", "Polytropic eff.", "Speed (rpm)"]
            rows = []
            for u in units:
                for c in cs:
                    r = u["results"][c]
                    ex = r.get("extras") or {}
                    sin = self._end_result(u, "inlet_streams", c)
                    sout = self._end_result(u, "outlet_streams", c)
                    rows.append([u["tag"], c, fmt_value(sin.get("p_bara"), 4), fmt_value(sout.get("p_bara"), 4),
                                 fmt_value(ex.get("pressure_ratio"), 4), fmt_value(r.get("power_kw"), 5),
                                 fmt_value(ex.get("polytropic_efficiency"), 3), fmt_value(ex.get("speed_rpm"), 4)])
            return header, rows
        if cat in ("cooler", "heater", "exchanger"):
            header = ["Tag", "Case", "T in (C)", "T out (C)", "Duty (kW)", "dP (bar)"]
            rows = []
            for u in units:
                for c in cs:
                    r = u["results"][c]
                    sin = self._end_result(u, "inlet_streams", c)
                    sout = self._end_result(u, "outlet_streams", c)
                    rows.append([u["tag"], c, fmt_value(sin.get("t_c"), 4), fmt_value(sout.get("t_c"), 4), fmt_value(r.get("duty_kw"), 4),
                                 fmt_value(r.get("dp_bar"), 3)])
            return header, rows
        if cat == "pump":
            header = ["Tag", "Case", "P in (bara)", "P out (bara)", "Power (kW)"]
            rows = []
            for u in units:
                for c in cs:
                    r = u["results"][c]
                    rows.append([u["tag"], c, fmt_value(self._end_result(u, "inlet_streams", c).get("p_bara"), 4),
                                 fmt_value(self._end_result(u, "outlet_streams", c).get("p_bara"), 4), fmt_value(r.get("power_kw"), 4)])
            return header, rows
        header = ["Tag", "Type", "Case", "Inlets", "Outlets", "Throughput (kg/h)", "T out (C)", "P out (bara)"]
        rows = []
        for u in units:
            for c in cs:
                outs = [self.S[s]["results"][c] for s in u["outlet_streams"]]
                ins = [self.S[s]["results"][c] for s in u["inlet_streams"]]
                thr = sum((r.get("mass_kg_h") or 0.0) for r in ins)
                rows.append([u["tag"], u["java_class"], c, len(ins), len(outs), fmt_value(thr, 5),
                             fmt_value(outs[0].get("t_c"), 4) if outs else "n/a", fmt_value(outs[0].get("p_bara"), 4) if outs else "n/a"])
        return header, rows

    def _balances_tables(self):
        rows = []
        for cid in self.cases:
            o = self.balances["cases"][cid]["overall"]
            rows.append([cid, fmt_value(o["mass_in_kg_h"], 6), fmt_value(o["mass_out_kg_h"], 6), "%.3e" % o["mass_rel"], o["mass_status"],
                         fmt_value(o["h_in_kw"], 6), fmt_value(o["h_out_kw"], 6), fmt_value(o["power_kw"], 5), fmt_value(o["duty_kw"], 5),
                         fmt_value(o["energy_res_kw"], 4), "%.3e" % o["energy_rel"], o["energy_status"]])
        self.table(["Case", "Feed kg/h", "Product kg/h", "Mass rel. res.", "Mass", "H feed kW", "H product kW", "W kW", "Q kW", "Energy res. kW",
                    "Energy rel.", "Energy"], rows, caption="Overall mass and energy balance (boundary feeds and products)", font=7)
        rows = []
        for cid in self.cases:
            for a in self.balances["cases"][cid]["areas"]:
                rows.append([cid, a["area"], fmt_value(a["mass_in_kg_h"], 5), fmt_value(a["mass_out_kg_h"], 5), "%.2e" % a["mass_rel"],
                             a["mass_status"], fmt_value(a["energy_res_kw"], 4), "%.2e" % a["energy_rel"], a["energy_status"]])
        self.table(["Case", "Area", "In kg/h", "Out kg/h", "Mass rel.", "Mass", "Energy res. kW", "Energy rel.", "Energy"], rows,
                   caption="Area-level balances (boundary streams of each model area)", font=7)
        fails = self.balances["fail_items"]
        if fails:
            self.P("Balance checks outside tolerance (%d). They are reported, not suppressed:" % len(fails), bold=True)
            rows = [[f["case"], f["level"], f["id"], f["check"], fmt_value(f["residual"], 4)] for f in fails]
            self.table(["Case", "Level", "Item", "Check", "Residual (kg/h or kW)"], rows, caption="Balance checks outside tolerance",
                       font=7.5, widths=[14, 16, 50, 34, 36])
            self.P(self.basis.get("balance_fail_explanation", ""), italic=True)
            exc = self.basis.get("known_balance_exceptions", [])
            if exc:
                self.table(["Item", "Explanation (documented exception)"], [[str(e["id"]), e["explanation"]] for e in exc],
                           caption="Documented explanations for balance checks outside tolerance", font=7.5, widths=[40, 130],
                           align=["left", "left"])
        else:
            self.P("All balance checks are within tolerance or classified below as NeqSim bookkeeping notes.")
        notes = self.balances.get("note_items", [])
        if notes:
            self.P("Balance residuals classified as NeqSim bookkeeping (%d). The residual is shown; it is not a closure test of the "
                   "flowsheet because the equipment type does not conserve energy (separator and scrubber outlets are re-flashed "
                   "isothermally, component splitters re-flash at inlet conditions) or the residual is the recycle tear convergence "
                   "residual. A residual above the relaxed tolerance stays a FAIL." % len(notes), bold=True)
            rows = [[n["case"], n["level"], n["id"], n["check"], fmt_value(n["residual"], 4), n["note"]] for n in notes]
            self.table(["Case", "Level", "Item", "Check", "Residual (kg/h or kW)", "Reason"], rows,
                       caption="Balance residuals classified as equipment bookkeeping (NOTE)", font=7,
                       widths=[10, 14, 34, 28, 26, 60], align=["left"] * 6)

    def _case_comparison(self, figure_dir):
        import matplotlib

        matplotlib.use("Agg")
        import matplotlib.pyplot as plt

        comp = [u for u in self.data["equipment"] if u["category"] in ("compressor", "pump")]
        comp_max = max([abs(u["results"][c].get("power_kw") or 0.0) for u in comp for c in self.cases] or [0.0])
        comp_all = comp
        comp = [u for u in comp if max(abs(u["results"][c].get("power_kw") or 0.0) for c in self.cases) > 0.02 * comp_max]
        rows = []
        for u in comp:
            rows.append([u["tag"]] + [fmt_value(u["results"][c].get("power_kw"), 5) for c in self.cases])
        if rows:
            self.table(["Machine"] + ["Power kW, case %s" % c for c in self.cases], rows,
                       caption="Rotating-equipment shaft power by case (machines below 2 % of the largest machine omitted; all in Appendix A)",
                       font=8, widths=[40] + [34] * len(self.cases))
        # bar chart: boundary products by case
        prods = [s for s in self.data["streams"] if s["kind"] == "product" and max((s["results"][c].get("mass_kg_h") or 0.0) for c in self.cases) > 1.0]
        if prods:
            fig, ax = plt.subplots(figsize=(8.0, 3.6))
            width = 0.8 / len(self.cases)
            for k, c in enumerate(self.cases):
                ax.bar([i + k * width for i in range(len(prods))], [s["results"][c].get("mass_kg_h") or 0.0 for s in prods], width, label="Case %s" % c)
            ax.set_xticks([i + 0.4 - width / 2 for i in range(len(prods))])
            ax.set_xticklabels(["%s\n%s" % (s["id"], textwrap.shorten(s["label"], 20, placeholder="...")) for s in prods], fontsize=7, rotation=15)
            ax.set_ylabel("Mass flow (kg/h)")
            ax.set_yscale("log")
            ax.grid(axis="y", alpha=0.3)
            ax.legend(fontsize=8)
            ax.set_title("Boundary product streams by operating case", fontsize=10)
            fig.tight_layout()
            path = os.path.join(figure_dir, "case_comparison_products.png")
            fig.savefig(path, dpi=200)
            plt.close(fig)
            self.picture(path, 150, alt="Bar chart of the mass flow of each boundary product stream for each operating case on a logarithmic axis",
                         caption="Boundary product streams by operating case (log scale)")
        if comp:
            fig, ax = plt.subplots(figsize=(8.0, 3.4))
            width = 0.8 / len(self.cases)
            for k, c in enumerate(self.cases):
                ax.bar([i + k * width for i in range(len(comp))], [u["results"][c].get("power_kw") or 0.0 for u in comp], width, label="Case %s" % c)
            ax.set_xticks([i + 0.4 - width / 2 for i in range(len(comp))])
            ax.set_xticklabels([u["tag"] for u in comp], fontsize=8)
            ax.set_ylabel("Shaft power (kW)")
            ax.grid(axis="y", alpha=0.3)
            ax.legend(fontsize=8)
            ax.set_title("Rotating-equipment shaft power by operating case", fontsize=10)
            fig.tight_layout()
            path = os.path.join(figure_dir, "case_comparison_power.png")
            fig.savefig(path, dpi=200)
            plt.close(fig)
            self.picture(path, 150, alt="Bar chart of the shaft power of each compressor and pump for each operating case",
                         caption="Rotating-equipment shaft power by operating case")
        for fig_spec in self.basis.get("figures", []):
            p = fig_spec["path"]
            if os.path.exists(p):
                if fig_spec.get("a3_landscape"):               # drawings: own A3 landscape page, then back to portrait
                    self.section(landscape=True, a3=True)
                    self.picture(p, 355, alt=fig_spec["alt"], caption=fig_spec["caption"])
                    self.section()
                elif fig_spec.get("a4_landscape"):             # wide charts: own A4 landscape page, then back to portrait
                    self.section(landscape=True)
                    self.picture(p, 262, alt=fig_spec["alt"], caption=fig_spec["caption"])
                    self.section()
                else:
                    self.picture(p, 150, alt=fig_spec["alt"], caption=fig_spec["caption"])

    def conclusions(self):
        self.H("9 Conclusions and recommendations", 1)
        self.P("Conclusions", bold=True)
        for item in self.basis.get("conclusions", []):
            self.P(item, style="List Bullet")
        self.P("Recommendations", bold=True)
        for item in self.basis.get("recommendations", []):
            self.P(item, style="List Bullet")
        self.H("10 References", 1)
        for i, ref in enumerate(self.basis.get("references", []), 1):
            self.P("[%d] %s" % (i, ref))

    # ── appendices ──
    def appendices(self):
        data, cs = self.data, self.cases
        comps = [c["name"] for c in data["components"]]
        sec = self.section(landscape=True, a3=True)
        W = self.usable_width(sec)
        self.H("Appendix A Equipment register and unit results", 1)
        rows = []
        for u in data["equipment"]:
            des = ", ".join("%s=%s" % (k, fmt_value(v, 3)) for k, v in (u.get("design") or {}).items())
            rows.append([u["tag"], u["java_class"], u["category"] + (" (generic symbol)" if u["category"] == "other" else ""), u["area"], " ".join(u["inlet_streams"]), " ".join(u["outlet_streams"]), des or "n/a"])
        self.table(["Tag", "Model type", "Category", "Area", "Inlet streams", "Outlet streams", "Design data in model"], rows,
                   caption="Equipment register (every modelled unit operation)", font=7, widths=[48, 42, 24, 36, 86, 86, 50], align=["left"] * 7)
        rows = []
        for u in data["equipment"]:
            for c in cs:
                r = u["results"][c]
                ex = r.get("extras") or {}
                rows.append([u["tag"], c, fmt_value(r.get("duty_kw"), 5), fmt_value(r.get("power_kw"), 5), fmt_value(r.get("dp_bar"), 4),
                             "; ".join("%s=%s" % (k, fmt_value(v, 4)) for k, v in ex.items()) or "-"])
        self.table(["Tag", "Case", "Duty kW", "Shaft power kW", "dP bar", "Other results"], rows, caption="Unit results by case (all units)",
                   font=7, widths=[52, 14, 28, 32, 24, 160], align=["left", "left", "right", "right", "right", "left"])
        self.H("Appendix B Stream tables", 1)
        self.P("B.1 Stream register; B.2 stream properties; B.3 phase table; B.4 overall composition (mole fraction); B.5 phase compositions "
               "of multiphase streams. Component flow rates (kmol/h, kg/h) and all values at full precision are in the machine-readable "
               "exports (stream_components.csv, process_data.xlsx, process_data.json). Phase fractions are on a mole basis unless stated.")
        rows = [[s["id"], s["label"], s["name"], s["source"] or "boundary", s["destination"] or "boundary", s["area"], s["kind"]] for s in data["streams"]]
        self.table(["Stream", "Label", "Model name", "Source", "Destination", "Area", "Kind"], rows,
                   caption="B.1 Stream register (every material stream)", font=7, widths=[16, 62, 52, 52, 52, 36, 18], align=["left"] * 7)
        for c in cs:
            rows = []
            for s in data["streams"]:
                r = s["results"][c]
                fl = r.get("std_flash_m3_h") or {}
                phs = r.get("phases", [])
                if r.get("zero_flow"):
                    rows.append([s["id"], s["label"][:30], fmt_value(r.get("t_c"), 4), fmt_value(r.get("p_bara"), 4), fmt_value(r.get("mass_kg_h"), 5),
                                 fmt_value(r.get("mole_kmol_h"), 5), fmt_value(r.get("mw_g_mol"), 4)] + ["n/a"] * 9 + [r["state"], "n/a", "n/a", fmt_value(r.get("H_kw"), 5)])
                    continue
                gas = [p for p in phs if p["type"] == "gas"]
                liq = [p for p in phs if p["type"] != "gas"]
                vols = [p["vol_actual_m3_h"] for p in phs]
                tot_vol = sum(v for v in vols if v is not None) if all(v is not None for v in vols) and vols else None
                liq_mass = sum((p["mass_kg_h"] or 0.0) for p in liq)
                liq_vol = sum((p["vol_actual_m3_h"] or 0.0) for p in liq)
                gas_vol = gas[0]["vol_actual_m3_h"] if gas else 0.0
                vf_gas = (gas_vol / tot_vol) if (tot_vol and gas_vol is not None) else None
                vf_liq = (liq_vol / tot_vol) if tot_vol else None
                rows.append([s["id"], s["label"][:30], fmt_value(r.get("t_c"), 4), fmt_value(r.get("p_bara"), 4), fmt_value(r.get("mass_kg_h"), 5),
                             fmt_value(r.get("mole_kmol_h"), 5), fmt_value(r.get("mw_g_mol"), 4), fmt_value(liq_mass, 5), fmt_value(liq_vol, 4),
                             fmt_value(gas_vol, 4), fmt_value(gas[0]["density_kg_m3"], 4) if gas else "n/a",
                             fmt_value(vf_gas, 3), fmt_value(vf_liq, 3), fmt_value(r.get("std_vol_ideal_sm3_h"), 5),
                             fmt_value(fl.get("gas"), 4) if fl else "n/a", fmt_value((fl.get("oil", 0.0) + fl.get("aqueous", 0.0)), 4) if fl else "n/a",
                             r.get("state", "n/a"), fmt_value(r.get("density_kg_m3"), 4), fmt_value((r.get("h_kj_kg") or 0.0) / 1000.0 if r.get("h_kj_kg") is not None else None, 4),
                             fmt_value(r.get("H_kw"), 5)])
            self.table(["Stream", "Label", "T C", "P bara", "Total mass kg/h", "Total molar kmol/h", "MW kg/kmol", "Liquid mass kg/h", "Liquid actual vol m3/h",
                        "Vapour actual vol m3/h", "Vapour density kg/m3", "Vapour vol. fr.", "Liquid vol. fr.", "Ideal-gas std vol Sm3/h", "Flash gas Sm3/h",
                        "Flash liquid m3/h", "Phases", "Mixture density kg/m3", "h MJ/kg", "H kW"],
                       rows, caption="B.2 Stream properties, case %s (T, P as solved; standard volumes at %.2f C and %.5f bara; liquid = all non-gas phases; "
                       "volume fractions are actual-condition phase volumes)" % (c, data["meta"]["standard_conditions"]["t_c"], data["meta"]["standard_conditions"]["p_bara"]),
                       font=6, widths=[12, 38, 11, 13, 20, 19, 12, 19, 18, 19, 17, 13, 13, 20, 19, 17, 24, 17, 14, 18])
        for c in cs:
            rows = []
            for s in data["streams"]:
                for p in s["results"][c].get("phases", []):
                    rows.append([s["id"], p["type"], fmt_value(p["mole_fraction"], 4), fmt_value(p["mass_fraction"], 4), fmt_value(p["mass_kg_h"], 5),
                                 fmt_value(p["mole_kmol_h"], 5), fmt_value(p["vol_actual_m3_h"], 4), fmt_value(p["density_kg_m3"], 4), fmt_value(p["mw_g_mol"], 4),
                                 fmt_value(p["z_factor"], 4), fmt_value(p["viscosity_cp"], 3)])
            self.table(["Stream", "Phase", "Mole fraction", "Mass fraction", "Mass kg/h", "Molar kmol/h", "Actual vol m3/h", "Density kg/m3", "MW", "Z", "Viscosity cP"],
                       rows, caption="B.3 Phase table, case %s (phase flow rates, densities and fractions; every phase of every stream)" % c,
                       font=7, widths=[16, 20, 26, 26, 30, 28, 30, 28, 18, 18, 24])
        short = [n[:9] for n in comps]
        cw = max(9.5, (W - 16 - 34) / max(1, len(comps)))
        for c in cs:
            rows = []
            for s in data["streams"]:
                z = s["results"][c].get("z") or {}
                rows.append([s["id"], s["label"][:24]] + [fmt_value(z.get(n), 3) for n in comps])
            self.table(["Stream", "Label"] + short, rows, caption="B.4 Overall composition, mole fraction, case %s (all %d components)" % (c, len(comps)),
                       font=5.5, widths=[12, 34] + [cw] * len(comps))
        for c in cs:
            rows = []
            for s in data["streams"]:
                phs = s["results"][c].get("phases", [])
                if len(phs) < 2:
                    continue
                for p in phs:
                    rows.append([s["id"], p["type"], fmt_value(p["mole_fraction"], 3)] + [fmt_value((p.get("x") or {}).get(n), 3) for n in comps])
            self.table(["Stream", "Phase", "Mole fr."] + short, rows,
                       caption="B.5 Phase compositions (mole fraction) of multiphase streams, case %s" % c, font=5.5, widths=[12, 14, 11] + [cw] * len(comps))
        light = [c_["name"] for c_ in data["components"] if c_["name"] != "water" and (c_["mw_g_mol"] or 0.0) < 84.0]
        heavy = [c_["name"] for c_ in data["components"] if c_["name"] != "water" and (c_["mw_g_mol"] or 0.0) >= 84.0]
        has_water = any(c_["name"] == "water" for c_ in data["components"])
        for c in cs:
            rows = []
            for s in data["streams"]:
                z = s["results"][c].get("z") or {}
                if not z:
                    rows.append([s["id"], s["label"][:30]] + ["n/a"] * (len(light) + 1 + (1 if has_water else 0)))
                    continue
                rows.append([s["id"], s["label"][:30]] + ["%.2f" % (100.0 * (z.get(n) or 0.0)) if (z.get(n) or 0.0) >= 5e-5 else fmt_value((z.get(n) or 0.0) * 100.0, 2)
                                                          for n in light] + ["%.2f" % (100.0 * sum((z.get(n) or 0.0) for n in heavy))] +
                            (["%.2f" % (100.0 * (z.get("water") or 0.0))] if has_water else []))
            self.table(["Stream", "Label"] + light + ["C6+ (sum of components with MW >= 84)"] + (["water"] if has_water else []), rows,
                       caption="B.6 Compact composition in mole %%, case %s (pseudo-components summed as C6+; complete composition in B.4)" % c,
                       font=6.5)
        self.H("Appendix C Component and pseudo-component properties", 1)
        rows = [[c["index"], c["name"], fmt_value(c["mw_g_mol"], 5), fmt_value(c["tc_k"], 5), fmt_value(c["pc_bara"], 5), fmt_value(c["omega"], 4),
                 fmt_value(c["tb_k"], 5), fmt_value(c["rho_liq_g_cm3"], 4), "TBP" if c["is_tbp"] else ("plus" if c["is_plus"] else "pure")] for c in data["components"]]
        self.table(["#", "Component", "MW g/mol", "Tc K", "Pc bara", "Acentric factor", "Tb K", "Liquid density g/cm3", "Type"], rows,
                   caption="Component and pseudo-component properties read from the solved model fluid", font=8, widths=[10, 36, 26, 26, 26, 28, 26, 36, 18])
        self.H("Appendix D Unit-level balances", 1)
        for c in cs:
            rows = []
            for u in self.balances["cases"][c]["units"]:
                rows.append([u["tag"], u["category"], fmt_value(u["mass_in_kg_h"], 6), fmt_value(u["mass_out_kg_h"], 6), "%.2e" % u["mass_rel"], u["mass_status"],
                             u["component_status"], fmt_value(u["h_in_kw"], 6), fmt_value(u["h_out_kw"], 6), fmt_value(u["implied_energy_kw"], 5),
                             fmt_value(u["energy_res_kw"], 4), u["energy_status"]])
            self.table(["Tag", "Category", "Mass in kg/h", "Mass out kg/h", "Mass rel.", "Mass", "Comp.", "H in kW", "H out kW", "Delta H kW", "Energy res. kW", "Energy"],
                       rows, caption="D.%s Unit-level mass, component and energy balances, case %s" % (c, c), font=6.5)
        self.H("Appendix E Standards register", 1)
        rows = [[s["reference"], s["title"], s["edition"], s["status"], s["source_url"], s["date_checked"], s["text_available"], s["requirement_class"],
                 s["application"], s["compliance_statement"], s["deviations"]] for s in self.standards]
        self.table(["Reference", "Title", "Edition", "Status", "Source URL", "Checked", "Text available", "Class", "Application", "Compliance statement", "Deviations"],
                   rows, caption="Standards register (full)", font=6.5, widths=[22, 40, 22, 28, 62, 18, 16, 18, 50, 56, 40], align=["left"] * 11)
        self.H("Appendix F Validation report", 1)
        self._validation_appendix()
        self.H("Appendix G Requirement compliance matrices", 1)
        self._requirement_matrices()

    def _requirement_matrices(self):
        mats = self.basis.get("requirement_matrices", [])
        if not mats:
            self.P("No project or company requirement set was supplied for this study.")
            return
        for m in mats:
            self.P("%s (%s)" % (m["title"], m["source"]), bold=True)
            counts = {}
            for it in m["items"]:
                counts[it["status"]] = counts.get(it["status"], 0) + 1
            self.P("Status counts: " + ", ".join("%s %d" % (k, v) for k, v in sorted(counts.items())) + ".")
            rows = [[it["id"], it["text"], it["status"], it["evidence"]] for it in m["items"]]
            self.table(["Requirement", "Summary of requirement (paraphrased)", "Status", "Evidence or reason"], rows,
                       caption="Requirement compliance matrix: %s" % m["title"], font=7, widths=[22, 120, 24, 190], align=["left"] * 4)

    def _validation_appendix(self):
        v = self.validation
        if not v:
            self.P("Validation report not yet available at this build stage.", italic=True)
            return
        self.P("Overall result: %s. Checks run: %d; passed: %d; failed: %d; warnings: %d. Full machine-readable report: validation_report.json." %
               (v["status"], v["n_checks"], v["n_pass"], v["n_fail"], v["n_warn"]), bold=True)
        rows = [[r["id"], r["description"], r["status"], r["detail"][:300]] for r in v["checks"]]
        self.table(["Check", "Description", "Result", "Detail"], rows, caption="Validation checks", font=7, widths=[26, 120, 16, 200], align=["left"] * 4)

    # ── finish ──
    def headers_footers(self):
        d = self.basis["document"]
        for sec in self.doc.sections:
            sec.header.is_linked_to_previous = False if sec is self.doc.sections[0] else True
        sec0 = self.doc.sections[0]
        hp = sec0.header.paragraphs[0]
        hp.text = "%s  |  Rev %s  |  %s" % (d["document_number"], d["revision"], d["title"])
        hp.runs[0].font.size = Pt(8)
        fp = sec0.footer.paragraphs[0]
        r = fp.add_run("%s - %s    Page " % (d["readiness"].upper(), d.get("readiness_note", "")))
        r.font.size = Pt(8)
        _add_field(fp, "PAGE", "1")
        fp.add_run(" of ")
        _add_field(fp, "NUMPAGES", "1")
        for run in fp.runs:
            run.font.size = Pt(8)

    def properties(self):
        d = self.basis["document"]
        cp = self.doc.core_properties
        cp.title = "%s (%s rev %s)" % (d["title"], d["document_number"], d["revision"])
        cp.author = d.get("prepared_by", "not assigned")
        cp.subject = d.get("subtitle", "")
        cp.keywords = "%s; %s" % (self.KEYWORDS, d["readiness"])
        cp.comments = self.GENERATOR_COMMENT

    def static_lists(self):
        """Fill the field results of contents and lists with static text so the file is readable without a field update."""
        def fill(par, lines):
            if not lines:
                return
            for run in list(par.runs)[-2:-1]:
                run.text = "\n".join(lines)

        fill(self.toc_par, ["%s%s" % ("    " * (lvl - 1), txt) for lvl, txt in self.headings if lvl <= 2])
        fill(self.lof_par, self.captions["Figure"])
        fill(self.lot_par, self.captions["Table"])

    def build(self, out_path, figure_dir):
        os.makedirs(figure_dir, exist_ok=True)
        self.cover()
        self.section()
        self.document_control()
        self.contents()
        self.section()
        self.executive_summary()
        self.scope_and_basis()
        self.design_basis()
        self.standards_section()
        self.process_description()
        self.methodology()
        self.diagrams_section(figure_dir)
        self.results_section(figure_dir)
        self.section()
        self.conclusions()
        self.appendices()
        self.headers_footers()
        self.properties()
        self.static_lists()
        self.doc.save(out_path)
        facts_path = os.path.join(os.path.dirname(out_path), "report_facts.json")
        with open(facts_path, "w", encoding="utf-8") as fh:
            json.dump({"derived_facts": self.facts, "resolved_references": self.res.used, "unresolved_references": self.res.errors,
                       "executive_summary_text": self.exec_summary_text}, fh, indent=1, default=str)
        return {"docx": out_path, "facts": facts_path, "n_figures": self.fig_n, "n_tables": self.tab_n,
                "unresolved": list(self.res.errors)}


def _numlike(v):
    return bool(re.fullmatch(r"[<>+\-]?[0-9.]+(e[+\-]?[0-9]+)?|n/a", str(v).strip()))


# ── Word automation and rendering (optional) ──────────────────

def word_update_and_export(docx_path, pdf_path):
    """Update all fields with Microsoft Word (COM) and export a PDF. Returns True on success."""
    try:
        import win32com.client  # noqa: WPS433
    except ImportError:
        return False
    word = None
    try:
        word = win32com.client.DispatchEx("Word.Application")
        word.Visible = False
        word.DisplayAlerts = 0
        doc = word.Documents.Open(os.path.abspath(docx_path))
        doc.Fields.Update()
        for toc in doc.TablesOfContents:
            toc.Update()
        for tof in doc.TablesOfFigures:
            tof.Update()
        doc.Fields.Update()
        doc.Save()
        doc.ExportAsFixedFormat(os.path.abspath(pdf_path), 17)
        doc.Close(False)
        return os.path.exists(pdf_path)
    except Exception:  # noqa: BLE001
        return False
    finally:
        if word is not None:
            try:
                word.Quit()
            except Exception:  # noqa: BLE001
                pass


def render_pdf_pages(pdf_path, out_dir, prefix, dpi=200, pages=None):
    """Render PDF pages to PNG with pymupdf; returns the list of written paths."""
    import pymupdf

    os.makedirs(out_dir, exist_ok=True)
    doc = pymupdf.open(pdf_path)
    paths = []
    for i, page in enumerate(doc):
        if pages is not None and i not in pages:
            continue
        path = os.path.join(out_dir, "%s_%03d.png" % (prefix, i + 1))
        page.get_pixmap(dpi=int(dpi)).save(path)
        paths.append(path)
    return paths
