"""Daily engineering update for a living task, as a Word document with the newest day on top.

The living task keeps many cycle folders, digests and a growing log. Engineers need one document:
what changed today, what it means for the question being answered, what has to be decided and by whom,
then the cumulative picture and the earlier days.

Inputs (all inside ``continuous/``):

* ``daily/<YYYY-MM-DD>.json``  one entry per day, written by the reviewing agent or the engineer
  (``neqsim-living daily --template`` prints a skeleton, ``--validate`` checks the entries).
* ``daily/_standing.json``     the question, the current answer, limits and glossary (optional).
* ``cycles/*/cycle.json``, ``kpi_history.csv``  used for the KPI snapshot, trend charts and the
  monitoring-health table. A day without an entry still appears, with an automatic summary and a
  visible "no written review" flag.
* ``cycle_plan.yaml`` block ``daily_report`` (title, kpis, charts, expected_cycles_per_day).

Output: ``continuous/DAILY_REPORT.docx`` (overwritten after every cycle that has the block enabled).
"""

import csv
import json
import math
import os
import re
from collections import OrderedDict
from datetime import datetime, timedelta, timezone

from .plan import continuous_dir, load_plan

DAILY_DIR = "daily"
STANDING_FILE = "_standing.json"
OUTPUT_FILE = "DAILY_REPORT.docx"
ENTRY_LISTS = ("what_changed", "events", "new_evidence", "hypotheses", "decisions", "open_points")
_DATE = re.compile(r"^\d{4}-\d{2}-\d{2}$")
_BLUE = "1F3864"
_LIGHT = "DCE6F1"
_GREY = "F2F2F2"


def daily_dir(task_dir):
    return os.path.join(continuous_dir(task_dir), DAILY_DIR)


def template(date):
    """Return an empty entry; the reviewing agent fills it and keeps it short and factual."""
    return OrderedDict([
        ("date", date),
        ("headline", "One sentence, answer first: what is new and what it means."),
        ("verdict", OrderedDict([("root_cause", ""), ("decision", ""), ("confidence", "")])),
        ("what_changed", ["Plain-language bullet, 2-5 per day. Numbers with units."]),
        ("events", [OrderedDict([("time", "dd.mm hh:mm"), ("t_utc", "YYYY-MM-DDTHH:MMZ"), ("what", ""),
                                 ("effect", ""), ("source", ""), ("key", False)])]),
        ("new_evidence", [OrderedDict([("item", ""), ("source", ""), ("effect", "")])]),
        ("hypotheses", [OrderedDict([("id", "short_id"), ("name", ""), ("old", 0.0), ("new", 0.0),
                                     ("why", "")])]),
        ("decisions", [OrderedDict([("owner", ""), ("action", ""), ("due", "")])]),
        ("open_points", [""]),
    ])


def _read_json(path, default=None):
    try:
        with open(path, "r", encoding="utf-8") as handle:
            return json.load(handle)
    except (OSError, ValueError):
        return default


def load_entries(task_dir):
    """Return {date: entry} sorted by date, ignoring files whose name starts with an underscore."""
    folder = daily_dir(task_dir)
    entries = {}
    if os.path.isdir(folder):
        for name in sorted(os.listdir(folder)):
            stem, ext = os.path.splitext(name)
            if ext == ".json" and _DATE.match(stem):
                entry = _read_json(os.path.join(folder, name), {})
                if isinstance(entry, dict):
                    entry.setdefault("date", stem)
                    entries[stem] = entry
    return OrderedDict(sorted(entries.items()))


def load_standing(task_dir):
    return _read_json(os.path.join(daily_dir(task_dir), STANDING_FILE), {}) or {}


def validate_entry(entry):
    """Return a list of problems that would make the entry hard to read or silently incomplete."""
    problems = []
    if not _DATE.match(str(entry.get("date", ""))):
        problems.append("date must be YYYY-MM-DD")
    headline = str(entry.get("headline", "")).strip()
    if not headline or headline.startswith("One sentence"):
        problems.append("headline is missing")
    elif len(headline) > 400:
        problems.append("headline is longer than 400 characters; keep it to one sentence")
    for key in ENTRY_LISTS:
        if key in entry and not isinstance(entry[key], list):
            problems.append("{} must be a list".format(key))
    for index, hyp in enumerate(entry.get("hypotheses") or []):
        for field in ("id", "name", "new"):
            if not isinstance(hyp, dict) or field not in hyp:
                problems.append("hypotheses[{}] lacks '{}'".format(index, field))
        for field in ("old", "new"):
            if isinstance(hyp, dict) and field in hyp and not isinstance(hyp[field], (int, float)):
                problems.append("hypotheses[{}].{} must be a number".format(index, field))
    hyps = [h for h in entry.get("hypotheses") or [] if isinstance(h, dict) and isinstance(h.get("new"), (int, float))]
    if hyps and abs(sum(h["new"] for h in hyps) - 1.0) > 0.035:
        problems.append("hypothesis confidences sum to {:.2f}, not 1.00".format(sum(h["new"] for h in hyps)))
    for index, event in enumerate(entry.get("events") or []):
        stamp = event.get("t_utc") if isinstance(event, dict) else None
        if stamp and _parse_utc(stamp) is None:
            problems.append("events[{}].t_utc is not an ISO timestamp".format(index))
    return problems


def _parse_utc(value):
    try:
        text = str(value).replace("Z", "+00:00")
        stamp = datetime.fromisoformat(text)
    except ValueError:
        return None
    return stamp if stamp.tzinfo else stamp.replace(tzinfo=timezone.utc)


def _local(stamp):
    return stamp.astimezone()


def _load_cycles(task_dir):
    cycles = []
    folder = os.path.join(continuous_dir(task_dir), "cycles")
    if not os.path.isdir(folder):
        return cycles
    for name in sorted(os.listdir(folder)):
        data = _read_json(os.path.join(folder, name, "cycle.json"))
        if not isinstance(data, dict):
            continue
        started = _parse_utc(data.get("now") or data.get("started_at") or "")
        if started is None:
            continue
        agent = {}
        for stage in data.get("stages", []):
            if str(stage.get("name", "")).startswith(("script:agent", "agent")):
                agent = stage
        cycles.append({
            "id": name, "start": started, "date": _local(started).strftime("%Y-%m-%d"),
            "triggers": list(dict.fromkeys(_read_json(os.path.join(folder, name, "triggers.json"), {}).get("triggers", []))),
            "kpis": _read_json(os.path.join(folder, name, "kpis.json"), {}) or {},
            "resumed": bool(data.get("resume_count")), "degraded": bool(data.get("degraded")),
            "review": os.path.isfile(os.path.join(folder, name, "agent_review.md")),
            "agent_status": agent.get("status", ""), "agent_message": agent.get("message", ""),
        })
    cycles.sort(key=lambda item: item["start"])
    return cycles


def _kpi_series(task_dir):
    path = os.path.join(continuous_dir(task_dir), "kpi_history.csv")
    series = {}
    if not os.path.isfile(path):
        return series
    with open(path, "r", encoding="utf-8", newline="") as handle:
        for row in csv.DictReader(handle):
            stamp = _parse_utc(row.get("time", ""))
            try:
                value = float(row.get("value", ""))
            except ValueError:
                continue
            if stamp is not None:
                series.setdefault(row.get("kpi", ""), []).append((stamp, value))
    for points in series.values():
        points.sort(key=lambda item: item[0])
    return series


def _fmt(value, digits=1):
    if value is None:
        return "-"
    if isinstance(value, (int, float)):
        return "{:.{d}f}".format(value, d=digits)
    return str(value)


def _signed(change, digits):
    """Format a change with sign; '-' when unknown, '0' when it rounds to zero (never '-0.0')."""
    if change is None:
        return "-"
    rounded = round(change, digits)
    return "0" if rounded == 0 else "{:+.{d}f}".format(rounded, d=digits)


def _auto_entry(date, cycles, config=None):
    """Mechanical entry for a day without a written review; it says so on its face and lists KPI moves."""
    day = [c for c in cycles if c["date"] == date]
    triggers = list(dict.fromkeys(t for c in day for t in c["triggers"]))
    entry = OrderedDict([("date", date), ("auto", True)])
    entry["headline"] = ("No written review for this day. {} monitoring cycle(s) ran; triggers raised: {}."
                         .format(len(day), ", ".join(triggers) if triggers else "none"))
    moves = []
    rows = _snapshot(config or {}, cycles, date) if day else []
    for label, unit, now, before, change in rows:
        if change not in ("-", "0"):
            moves.append("{}: {} {} (change {} since the previous day)".format(label, now, unit, change))
    entry["what_changed"] = moves[:6] or (
        ["Open the cycle digests under continuous/cycles/ for the raw detail."] if day else [])
    return entry

def _ordered_days(entries, cycles):
    days = set(entries) | {c["date"] for c in cycles}
    return sorted(days, reverse=True)


def _day_cycles(cycles, date):
    return [c for c in cycles if c["date"] == date]


def _csv_points(task_dir, spec):
    """Read a (time, value) series from a task CSV: spec {file, time (default time_utc), value (default avg)}."""
    path = os.path.join(str(task_dir), spec["file"])
    points = []
    if not os.path.isfile(path):
        return points
    with open(path, "r", encoding="utf-8", newline="") as handle:
        for row in csv.DictReader(handle):
            stamp = _parse_utc(str(row.get(spec.get("time", "time_utc"), "")).replace(" ", "T"))
            try:
                value = float(row.get(spec.get("value", "avg"), ""))
            except ValueError:
                continue
            if stamp is not None:
                points.append((stamp, value))
    points.sort(key=lambda item: item[0])
    return points


def _charts(task_dir, config, series, entries, out_dir, days=14, bayes=None):
    """Return ([(title, path)], events) for trend charts with key-event markers and the hypothesis trajectories.

    A chart series is either a KPI name from kpi_history.csv (one point per cycle) or a CSV file with
    finer resolution, which shows steps that the cycle KPIs cannot.
    """
    try:
        import matplotlib
        matplotlib.use("Agg")
        import matplotlib.dates as mdates
        import matplotlib.pyplot as plt
    except ImportError:
        return [], []
    os.makedirs(out_dir, exist_ok=True)
    made = []
    events = [(_parse_utc(e.get("t_utc")), "{}: {}".format(e.get("time", ""), e.get("label") or e.get("what", "")))
              for entry in entries.values() for e in entry.get("events") or []
              if isinstance(e, dict) and e.get("key") and _parse_utc(e.get("t_utc"))]
    events.sort(key=lambda item: item[0])
    window = timedelta(days=int(config.get("chart_days", days)))
    for number, chart in enumerate(config.get("charts") or [], 1):
        fig, ax = plt.subplots(figsize=(7.2, 3.4))
        loaded = []
        for spec in chart.get("kpis", []):
            spec = spec if isinstance(spec, dict) else {"name": spec}
            points = _csv_points(task_dir, spec) if spec.get("file") else series.get(spec.get("name"), [])
            if points:
                loaded.append((spec, points))
        if not loaded:
            plt.close(fig)
            continue
        start = max(p[-1][0] for _, p in loaded) - window
        for spec, points in loaded:
            points = [p for p in points if p[0] >= start]
            ax.plot([p[0] for p in points], [p[1] for p in points], label=spec.get("label", spec.get("name", "")),
                    linewidth=float(spec.get("width", 1.0)), color=spec.get("color"))
        if chart.get("hline") is not None:
            ax.axhline(float(chart["hline"]), color="red", linestyle="--", linewidth=0.9,
                       label=chart.get("hline_label", "limit"))
        for index, (stamp, _) in enumerate(events, 1):
            if stamp >= start:
                ax.axvline(stamp, color="grey", linestyle=":", linewidth=0.8)
                ax.annotate(str(index), (stamp, 1.0), xycoords=("data", "axes fraction"), ha="center",
                            va="bottom", fontsize=7, color="grey")
        ax.set_ylabel(chart.get("ylabel", ""))
        ax.set_title(chart.get("title", ""), fontsize=10, pad=14)
        ax.grid(alpha=0.3)
        ax.legend(fontsize=7, loc="best")
        ax.xaxis.set_major_formatter(mdates.DateFormatter("%d.%m"))
        fig.autofmt_xdate()
        fig.tight_layout()
        path = os.path.join(out_dir, "daily_kpi_{}.png".format(number))
        fig.savefig(path, dpi=170)
        plt.close(fig)
        made.append((chart.get("title", "KPI trend"), path))
    names, track = OrderedDict(), OrderedDict()
    if bayes:
        # Exact trajectory from the hypotheses file: one point per observation date (last value of the day).
        names = OrderedDict(bayes.get("short") or bayes["names"])
        by_date = OrderedDict()
        for step in bayes["history"]:
            if step.get("date"):
                by_date[str(step["date"])] = step["probabilities"]
        for date, probs in by_date.items():
            for key, value in probs.items():
                track.setdefault(key, []).append((date, value))
    else:
        for date, entry in entries.items():
            for hyp in entry.get("hypotheses") or []:
                if isinstance(hyp, dict) and isinstance(hyp.get("new"), (int, float)):
                    names[hyp.get("id", hyp.get("name"))] = hyp.get("name", hyp.get("id"))
                    track.setdefault(hyp.get("id", hyp.get("name")), []).append((date, hyp["new"]))
    if len(next(iter(track.values()), [])) >= 2:
        fig, ax = plt.subplots(figsize=(7.2, 4.0))
        for key, points in track.items():
            ax.plot([datetime.strptime(d, "%Y-%m-%d") for d, _ in points], [v for _, v in points],
                    marker="o", markersize=3, linewidth=1.4, label=names[key])
        ax.set_ylim(0, 1.0)
        ax.set_ylabel("Probability (sums to 1)" if bayes else "Confidence (sums to 1)")
        ax.set_title("Probability of each candidate cause" + (", weighted by the evidence" if bayes else ""), fontsize=10)
        ax.grid(alpha=0.3)
        ax.legend(fontsize=7, loc="upper center", bbox_to_anchor=(0.5, -0.16), ncol=2, frameon=False)
        ax.xaxis.set_major_locator(mdates.DayLocator(interval=2))
        ax.xaxis.set_major_formatter(mdates.DateFormatter("%d.%m"))
        fig.tight_layout()
        path = os.path.join(out_dir, "daily_hypotheses.png")
        fig.savefig(path, dpi=170)
        plt.close(fig)
        made.append(("How the probability of each cause changed", path))
    return made, [(stamp, text) for stamp, text in events]


def _pretty(identifier):
    return str(identifier).replace("_", " ")


def _next_test_line(bayes):
    """One sentence naming the next most informative test and what its outcomes would do."""
    if not bayes or not bayes.get("tests"):
        return ""
    from .hypotheses import format_probability
    test = bayes["tests"][0]
    names = bayes.get("short") or bayes["names"]
    parts = ["if {} ({:.0%} likely), the leading cause would be {} at {}".format(
        _pretty(o["outcome"]), o["chance"], names.get(o["leader"], o["leader"]),
        format_probability(o["leader_probability"])) for o in test["outcomes"]]
    return "{} (owner {}). {}.".format(test["name"], test["owner"] or "-", "; ".join(parts).capitalize())


def _write_bayes(doc, bayes, problems):
    """Evidence-weighted candidate causes, the evidence that moved them, and the next most informative test."""
    from docx.shared import Pt
    from .hypotheses import format_probability
    para = doc.add_paragraph()
    para.add_run("Candidate causes, weighted by the evidence").bold = True
    para.paragraph_format.keep_with_next = True
    if problems:
        _callout(doc, "The hypotheses file has problems and was not computed", problems[:8], color="FCE4D6")
        return
    names, short, floor = bayes["names"], bayes.get("short") or bayes["names"], bayes.get("floor", 0.0)
    note = doc.add_paragraph()
    run = note.add_run(
        "Each observation is entered once with how likely it would be under every candidate cause; the probabilities "
        "below follow from these judgements by Bayes' rule. Second-hand information counts less, and repeats of the same "
        "mechanism count progressively less. A cause shown as '< {:.0%}' is unlikely on the evidence, not ruled out.".format(
            floor or 0.01))
    run.font.size = Pt(9)
    run.italic = True
    rows = [(names.get(h, h), format_probability(p, floor)) for h, p in
            sorted(bayes["posterior"].items(), key=lambda item: -item[1])]
    _table(doc, ("Candidate cause", "Probability now"), rows, (13.0, 4.0))
    if bayes.get("group_posterior"):
        doc.add_paragraph("Decision: " + ";  ".join(
            "{} {}".format(_pretty(g), format_probability(p, floor)) for g, p in bayes["group_posterior"].items()) + ".")
    if bayes.get("robustness"):
        from .hypotheses import robustness_text
        doc.add_paragraph("Robustness: " + robustness_text(bayes))
    strong = sorted(bayes.get("weights") or [], key=lambda w: -abs(math.log(max(w["odds_factor"], 1e-9))))[:8]
    if strong:
        para = doc.add_paragraph()
        para.add_run("Evidence that moved the answer most").bold = True
        para.paragraph_format.keep_with_next = True
        label = _pretty(strong[0]["odds_for"])
        _table(doc, ("Date", "Observation", "Odds for '{}'".format(label), "Counted as"),
               [(w.get("date") or "", w["description"], "x {:.2f}".format(w["odds_factor"]),
                 "{}{}".format("second-hand ({:.0%})".format(w["reliability"]) if w["reliability"] < 1 else "verified",
                               ", repeat x{:.2f}".format(w["weight"]) if w["weight"] < 1 else ""))
                for w in sorted(strong, key=lambda w: str(w.get("date")))], (2.2, 9.0, 2.6, 3.2), font=8.5)
    if bayes.get("tests"):
        para = doc.add_paragraph()
        basis = "decision" if bayes["rank_by"] == "info_decision_bits" else "cause"
        para.add_run("Next most informative tests (ranked by information on the {} per unit of effort)".format(basis)).bold = True
        para.paragraph_format.keep_with_next = True
        rows = []
        for test in bayes["tests"][:5]:
            outcomes = "; ".join("{} ({:.0%}): {} {}".format(
                _pretty(o["outcome"]), o["chance"], short.get(o["leader"], o["leader"]),
                format_probability(o["leader_probability"])) for o in test["outcomes"])
            rows.append((test["name"], test["owner"], "{:g} {}".format(test["cost"], test["cost_unit"]).strip(),
                         "{:.2f} / {:.2f}".format(test["info_decision_bits"], test["info_cause_bits"]), outcomes))
        _table(doc, ("Test", "Owner", "Effort", "Bits: decision / cause", "Outcome (chance): leading cause after"),
               rows, (4.6, 2.4, 1.6, 2.0, 6.4), font=8)


def _snapshot(config, cycles, date):
    """Rows (label, today, previous day, change) for the configured KPIs at the last cycle of each day."""
    rows = []
    days = sorted({c["date"] for c in cycles if c["kpis"]})
    if not days:
        return rows
    today = max((d for d in days if d <= date), default=days[-1])
    earlier = [d for d in days if d < today]
    last = lambda d: [c for c in cycles if c["date"] == d and c["kpis"]][-1]["kpis"]
    now_kpis = last(today)
    before_kpis = last(earlier[-1]) if earlier else {}
    for spec in config.get("kpis") or []:
        name = spec["name"] if isinstance(spec, dict) else spec
        label = spec.get("label", name) if isinstance(spec, dict) else name
        digits = spec.get("digits", 1) if isinstance(spec, dict) else 1
        unit = spec.get("unit", "") if isinstance(spec, dict) else ""
        if name not in now_kpis:
            continue
        value, prev = now_kpis[name], before_kpis.get(name)
        change = (value - prev) if isinstance(value, (int, float)) and isinstance(prev, (int, float)) else None
        rows.append((label, unit, _fmt(value, digits), _fmt(prev, digits), _signed(change, digits)))
    return rows


# ----------------------------------------------------------------------------- Word helpers

def _shade(cell, color):
    from docx.oxml import OxmlElement
    from docx.oxml.ns import qn
    props = cell._tc.get_or_add_tcPr()
    shade = OxmlElement("w:shd")
    shade.set(qn("w:val"), "clear")
    shade.set(qn("w:color"), "auto")
    shade.set(qn("w:fill"), color)
    props.append(shade)


def _repeat_header(row):
    from docx.oxml import OxmlElement
    from docx.oxml.ns import qn
    props = row._tr.get_or_add_trPr()
    flag = OxmlElement("w:tblHeader")
    flag.set(qn("w:val"), "true")
    props.append(flag)


def _no_split(row):
    from docx.oxml import OxmlElement
    from docx.oxml.ns import qn
    row._tr.get_or_add_trPr().append(OxmlElement("w:cantSplit"))


def _table(doc, header, rows, widths, font=9):
    from docx.shared import Cm, Pt, RGBColor
    table = doc.add_table(rows=1, cols=len(header))
    table.style = "Table Grid"
    table.autofit = False
    for index, text in enumerate(header):
        cell = table.rows[0].cells[index]
        cell.text = ""
        run = cell.paragraphs[0].add_run(text)
        run.bold = True
        run.font.size = Pt(font)
        run.font.color.rgb = RGBColor(0xFF, 0xFF, 0xFF)
        _shade(cell, _BLUE)
    _repeat_header(table.rows[0])
    for values in rows:
        row = table.add_row()
        _no_split(row)
        for index, text in enumerate(values):
            cell = row.cells[index]
            cell.text = ""
            run = cell.paragraphs[0].add_run(str(text))
            run.font.size = Pt(font)
    for row in table.rows:
        for index, width in enumerate(widths):
            row.cells[index].width = Cm(width)
    doc.add_paragraph().paragraph_format.space_after = 0
    return table


def _callout(doc, title, lines, color=_LIGHT):
    from docx.shared import Pt
    table = doc.add_table(rows=1, cols=1)
    table.style = "Table Grid"
    cell = table.rows[0].cells[0]
    _no_split(table.rows[0])
    _shade(cell, color)
    cell.text = ""
    head = cell.paragraphs[0]
    run = head.add_run(title)
    run.bold = True
    run.font.size = Pt(10)
    for line in lines:
        para = cell.add_paragraph()
        para.paragraph_format.space_after = Pt(3)
        run = para.add_run(line)
        run.font.size = Pt(10.5)
    doc.add_paragraph().paragraph_format.space_after = 0


def _bullets(doc, items, size=10):
    from docx.shared import Pt
    for item in items:
        para = doc.add_paragraph(style="List Bullet")
        run = para.add_run(str(item))
        run.font.size = Pt(size)
        para.paragraph_format.space_after = Pt(2)


def _heading(doc, text, level):
    heading = doc.add_heading(text, level=level)
    return heading


def _page_number_footer(section, text):
    from docx.oxml import OxmlElement
    from docx.oxml.ns import qn
    from docx.shared import Pt
    para = section.footer.paragraphs[0]
    para.text = ""
    run = para.add_run(text + "   |   page ")
    run.font.size = Pt(8)
    run2 = para.add_run()
    run2.font.size = Pt(8)
    for kind, body in (("begin", None), (None, " PAGE "), ("end", None)):
        if kind:
            element = OxmlElement("w:fldChar")
            element.set(qn("w:fldCharType"), kind)
        else:
            element = OxmlElement("w:instrText")
            element.set(qn("xml:space"), "preserve")
            element.text = body
        run2._r.append(element)


def _style_document(doc):
    from docx.shared import Pt, RGBColor
    normal = doc.styles["Normal"]
    normal.font.name = "Calibri"
    normal.font.size = Pt(10.5)
    for name, size in (("Heading 1", 16), ("Heading 2", 13), ("Heading 3", 11)):
        style = doc.styles[name]
        style.font.name = "Calibri"
        style.font.size = Pt(size)
        style.font.bold = True
        style.font.color.rgb = RGBColor.from_string(_BLUE)


def _hyp_rows(hypotheses, floor=0.02):
    """Rows for the day's cause table; a live value below ``floor`` reads '< floor', never a false 0.00."""
    def show(value):
        if isinstance(value, (int, float)) and value < floor:
            return "< {:.2f}".format(floor)
        return _fmt(value, 2)

    rows = []
    for hyp in hypotheses:
        old, new = hyp.get("old"), hyp.get("new")
        both_low = all(isinstance(v, (int, float)) and v < floor for v in (old, new))
        if both_low or not (isinstance(old, (int, float)) and isinstance(new, (int, float))):
            change = "-"
        else:
            change = _signed(new - old, 2)
        rows.append((hyp.get("name", hyp.get("id", "")), show(old), show(new), change, hyp.get("why", "")))
    return rows


def _day_title(date):
    return datetime.strptime(date, "%Y-%m-%d").strftime("%A %d %B %Y").replace(" 0", " ")


def _write_day(doc, date, entry, cycles, snapshot=None, full=True):
    from docx.shared import Pt
    day = _day_cycles(cycles, date)
    _heading(doc, _day_title(date), 2 if full else 3)
    if entry.get("auto"):
        _callout(doc, "No written review for this day", [entry["headline"]], color="FCE4D6")
    else:
        verdict = entry.get("verdict") or {}
        lines = [entry.get("headline", "")]
        for key, label in (("root_cause", "Root cause"), ("decision", "Production decision"), ("confidence", "Confidence")):
            if verdict.get(key):
                lines.append("{}: {}".format(label, verdict[key]))
        _callout(doc, "In brief", lines)
    if entry.get("author") and not entry.get("auto"):
        note = doc.add_paragraph()
        run = note.add_run("Source: " + entry["author"])
        run.italic = True
        run.font.size = Pt(8.5)
    if entry.get("what_changed"):
        para = doc.add_paragraph()
        para.add_run("What changed").bold = True
        _bullets(doc, entry["what_changed"])
    if entry.get("decisions"):
        para = doc.add_paragraph()
        para.add_run("Decisions and actions").bold = True
        _table(doc, ("Owner", "Action", "When"),
               [(d.get("owner", ""), d.get("action", ""), d.get("due", "")) for d in entry["decisions"]], (3.2, 11.0, 2.5))
    if not full:
        moved = [h for h in entry.get("hypotheses") or [] if isinstance(h.get("old"), (int, float))
                 and isinstance(h.get("new"), (int, float)) and abs(h["new"] - h["old"]) >= 0.02]
        if moved:
            _bullets(doc, ["{}: {} to {}".format(h["name"], _fmt(h["old"], 2), _fmt(h["new"], 2)) for h in moved], size=9)
        return
    if snapshot:
        para = doc.add_paragraph()
        para.add_run("Key numbers (last monitoring cycle of the day)").bold = True
        _table(doc, ("Quantity", "Unit", "Now", "Previous day", "Change"), snapshot, (6.2, 2.0, 2.4, 3.0, 2.4))
    if entry.get("events"):
        para = doc.add_paragraph()
        para.add_run("Events and plant response").bold = True
        _table(doc, ("Time", "What happened", "Plant response", "Source"),
               [(e.get("time", ""), e.get("what", ""), e.get("effect", ""), e.get("source", "")) for e in entry["events"]],
               (2.4, 5.6, 5.8, 3.2), font=8.5)
    if entry.get("new_evidence"):
        para = doc.add_paragraph()
        para.add_run("New evidence and what it does to the conclusion").bold = True
        _table(doc, ("Item", "Source", "Effect"),
               [(e.get("item", ""), e.get("source", ""), e.get("effect", "")) for e in entry["new_evidence"]],
               (6.0, 3.6, 7.4), font=8.5)
    if entry.get("hypotheses"):
        para = doc.add_paragraph()
        para.add_run("Candidate causes: confidence before and after").bold = True
        _table(doc, ("Candidate cause", "Before", "Now", "Change", "Why"), _hyp_rows(entry["hypotheses"]),
               (4.4, 1.4, 1.4, 1.5, 8.3), font=8.5)
    if entry.get("open_points"):
        para = doc.add_paragraph()
        para.add_run("Still open").bold = True
        _bullets(doc, entry["open_points"])


def build(task_dir, output=None):
    """Write the Word report and return its path, or None when python-docx is not installed."""
    try:
        from docx import Document
        from docx.shared import Cm, Pt
        from docx.enum.text import WD_ALIGN_PARAGRAPH
    except ImportError:
        return None
    plan = load_plan(task_dir)
    config = plan.get("daily_report") or {}
    entries = load_entries(task_dir)
    standing = load_standing(task_dir)
    cycles = _load_cycles(task_dir)
    series = _kpi_series(task_dir)
    days = _ordered_days(entries, cycles)
    if not days:
        return None
    title = config.get("title") or os.path.basename(os.path.normpath(str(task_dir)))
    latest = days[0]
    out_dir = os.path.join(continuous_dir(task_dir), "report", "daily")
    bayes, bayes_problems = None, []
    from . import hypotheses
    if hypotheses.load(task_dir) is not None:
        bayes, bayes_problems = hypotheses.run(task_dir)
    charts, marks = _charts(task_dir, config, series, entries, out_dir, bayes=bayes) or ([], [])

    doc = Document()
    _style_document(doc)
    section = doc.sections[0]
    section.left_margin = section.right_margin = Cm(2.0)
    section.top_margin = section.bottom_margin = Cm(1.8)
    _page_number_footer(section, "{} - daily engineering update".format(title))

    head = doc.add_paragraph()
    run = head.add_run("Daily engineering update")
    run.bold = True
    run.font.size = Pt(22)
    sub = doc.add_paragraph()
    run = sub.add_run(title)
    run.font.size = Pt(13)
    meta = doc.add_paragraph()
    run = meta.add_run("Status {}   |   {} days of monitoring   |   generated {}".format(
        _day_title(latest), len(days), datetime.now().strftime("%d.%m.%Y %H:%M")))
    run.font.size = Pt(9)

    latest_entry = entries.get(latest) or _auto_entry(latest, cycles, config)
    _heading(doc, "1. Today", 1)
    _write_day(doc, latest, latest_entry, cycles, snapshot=_snapshot(config, cycles, latest), full=True)
    if bayes and bayes.get("tests"):
        para = doc.add_paragraph()
        para.add_run("Next most informative test: ").bold = True
        para.add_run(_next_test_line(bayes))

    _heading(doc, "2. Where we stand", 1)
    if standing:
        if standing.get("question"):
            para = doc.add_paragraph()
            para.add_run("Question: ").bold = True
            para.add_run(standing["question"])
        if standing.get("answer"):
            _callout(doc, "Current answer", [standing["answer"]] + ([standing["confidence"]] if standing.get("confidence") else []))
        for key, label in (("proven_by", "What supports the answer"), ("not_proven", "What is not yet proven"),
                           ("ruled_out", "Ruled out")):
            if standing.get(key):
                para = doc.add_paragraph()
                para.add_run(label).bold = True
                _bullets(doc, standing[key])
        if standing.get("limits"):
            para = doc.add_paragraph()
            para.add_run("Operating limits and margin").bold = True
            _table(doc, ("Limit", "Value", "Status"),
                   [(i.get("item", ""), i.get("value", ""), i.get("status", "")) for i in standing["limits"]], (7.0, 4.0, 6.0))
    else:
        doc.add_paragraph("No standing summary has been written (continuous/daily/_standing.json).")
    if bayes or bayes_problems:
        _write_bayes(doc, bayes, bayes_problems)
    for caption, path in charts:
        doc.add_picture(path, width=Cm(16.0))
        cap = doc.add_paragraph()
        run = cap.add_run("Figure: " + caption)
        run.italic = True
        run.font.size = Pt(8.5)
        cap.alignment = WD_ALIGN_PARAGRAPH.CENTER
    if marks:
        para = doc.add_paragraph()
        para.add_run("Numbered dotted lines in the trend charts mark key events:").bold = True
        _table(doc, ("No.", "Event"), [(str(i), text) for i, (_, text) in enumerate(marks, 1)], (1.5, 15.5), font=8.5)

    older = days[1:]
    if older:
        _heading(doc, "3. Earlier updates (newest first)", 1)
        for date in older:
            _write_day(doc, date, entries.get(date) or _auto_entry(date, cycles, config), cycles, full=False)

    _heading(doc, "{}. Monitoring health".format(4 if older else 3), 1)
    expected = int(config.get("expected_cycles_per_day", 2))
    rows, problems = [], []
    for date in sorted({c["date"] for c in cycles}, reverse=True):
        day = _day_cycles(cycles, date)
        reviews = sum(1 for c in day if c["review"])
        failed = [c for c in day if c["agent_status"] and c["agent_status"] != "ok"]
        note = []
        if len(day) < expected:
            note.append("fewer cycles than the {} expected".format(expected))
        if failed:
            note.append("agent review failed: {}".format(failed[-1]["agent_message"] or failed[-1]["agent_status"]))
        if any(c["resumed"] for c in day):
            note.append("cycle was interrupted and resumed")
        rows.append((date, str(len(day)), str(reviews), "; ".join(note) or "ok"))
    if rows:
        _table(doc, ("Date", "Cycles run", "Written reviews", "Remarks"), rows[:21], (2.6, 2.2, 2.8, 9.4), font=8.5)
    if any(r[3] != "ok" for r in rows[:7]):
        problems.append("Some of the last seven days have gaps: the report may be missing reviews. Check the remarks above.")
    _bullets(doc, problems or ["No gaps in the last seven days."])

    _heading(doc, "How to read this report", 1)
    _bullets(doc, [
        "Newest information is first. Each day has an 'In brief' box with the answer, then what changed, what must be decided and by whom.",
        "Confidence numbers are the reviewer's judgement, not a statistical result; read them as ranking and direction, and look at the 'Why' column.",
        "A candidate cause with confidence near zero is not ruled out unless the text says it is ruled out.",
        "Second-hand information (messages, screenshots) is marked as unverified until the source record is seen.",
        "Raw material: continuous/cycles/<cycle>/ (digest, evidence brief), continuous/ROOT_CAUSE_LOG.md or the task's own log.",
    ], size=9.5)
    if standing.get("glossary"):
        _heading(doc, "Glossary", 2)
        _table(doc, ("Term", "Meaning"), [(g.get("term", ""), g.get("meaning", "")) for g in standing["glossary"]], (4.0, 13.0), font=8.5)

    path = output or os.path.join(continuous_dir(task_dir), OUTPUT_FILE)
    doc.save(path)
    return path


def update(task_dir):
    """Rebuild the report when the plan enables it; never raises (a cycle must not fail on reporting)."""
    try:
        config = load_plan(task_dir).get("daily_report") or {}
        if config.get("enabled"):
            return build(task_dir)
    except Exception:  # reporting must not break the monitoring cycle
        return None
    return None
