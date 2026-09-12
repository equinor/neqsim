#!/usr/bin/env python3
"""Generate ``step3_report/WORK_RECORD.md`` for a task folder.

The final report answers *what the engineering conclusion is*. The work record
answers the other half a reader needs in order to trust, repeat, or extend the
study: **what was actually done, how, with which data, and where every file
lives**. It is a plain markdown companion to the report, built from the task
folder itself so it cannot drift from the files it describes.

Usage::

    python devtools/generate_work_record.py <task_dir>
    neqsim work-record <task_dir>            # same thing through the CLI
    python devtools/generate_work_record.py <task_dir> --check   # gate only

What is auto-built from the folder:

* study identity and depth from ``study_config.yaml``
* the method sequence from ``analysis.scripts`` and ``notebooks.plan``
* every script and notebook in ``step2_analysis/`` with purpose, outputs, size,
  last-modified time, and its re-run command
* declared source systems with their captured evidence and whether it exists
* collected reference documents from ``references/collection_manifest.json``
* key results, figures, and figure captions from ``results.json``
* an annotated folder map and a numbered reproduction sequence

Hand-written narrative is preserved. Anything between a
``<!-- WORK_RECORD:NARRATIVE id=... -->`` /
``<!-- /WORK_RECORD:NARRATIVE -->`` pair is carried over verbatim when the file
is regenerated, so an agent or engineer can explain the reasoning once and keep
regenerating the mechanical sections around it.
"""
from __future__ import annotations

import argparse
import datetime as _dt
import json
import os
import re
import sys
from pathlib import Path

WORK_RECORD_NAME = "WORK_RECORD.md"
NARRATIVE_OPEN = "<!-- WORK_RECORD:NARRATIVE id={} -->"
NARRATIVE_CLOSE = "<!-- /WORK_RECORD:NARRATIVE -->"
NARRATIVE_PATTERN = re.compile(
    r"<!--\s*WORK_RECORD:NARRATIVE id=([A-Za-z0-9_\-]+)\s*-->\n?(.*?)\n?<!--\s*/WORK_RECORD:NARRATIVE\s*-->",
    re.DOTALL,
)

TASK_TYPE_NAMES = {
    "A": "Property",
    "B": "Process",
    "C": "PVT",
    "D": "Standards",
    "E": "Feature",
    "F": "Design",
    "G": "Workflow",
}

# Files in step2_analysis that are tooling noise rather than analysis steps.
SKIP_SCRIPT_NAMES = {"__init__.py", "conftest.py"}
# Scaffold-shipped starters are templates, not work that was done.
SKIP_SCRIPT_DIRS = {"starters", "__pycache__", ".ipynb_checkpoints"}
DATA_SUFFIXES = (".json", ".csv", ".xlsx", ".xls", ".parquet", ".db", ".sqlite",
                 ".txt", ".yaml", ".yml")


# ---------------------------------------------------------------------------
# Minimal YAML subset parser (study_config.yaml only — no PyYAML dependency)
# ---------------------------------------------------------------------------

def _strip_comment(line: str) -> str:
    """Drop a trailing YAML comment, ignoring hashes inside quotes."""
    quote = None
    for index, char in enumerate(line):
        if char in ('"', "'"):
            if quote == char:
                quote = None
            elif quote is None:
                quote = char
        elif char == "#" and quote is None:
            return line[:index].rstrip()
    return line.rstrip()


def _scalar(text: str):
    """Parse the scalar forms used by study_config.yaml."""
    value = text.strip()
    if not value:
        return ""
    if len(value) > 1 and value[0] == value[-1] and value[0] in ('"', "'"):
        return value[1:-1]
    lowered = value.lower()
    if lowered in ("true", "yes"):
        return True
    if lowered in ("false", "no"):
        return False
    if lowered in ("null", "~"):
        return None
    try:
        return int(value)
    except ValueError:
        pass
    try:
        return float(value)
    except ValueError:
        return value


def _yaml_lines(text: str):
    """Return (indent, content) for every meaningful line."""
    lines = []
    for raw in text.splitlines():
        stripped = _strip_comment(raw)
        if not stripped.strip():
            continue
        lines.append((len(stripped) - len(stripped.lstrip()), stripped.strip()))
    return lines


def _parse_block(lines, index, indent):
    """Parse a mapping or sequence block starting at ``index``."""
    if index < len(lines) and lines[index][1].startswith("- "):
        return _parse_sequence(lines, index, indent)
    return _parse_mapping(lines, index, indent)


def _parse_mapping(lines, index, indent):
    result = {}
    while index < len(lines):
        line_indent, content = lines[index]
        if line_indent < indent or content.startswith("- "):
            break
        if line_indent > indent:  # tolerate ragged input
            index += 1
            continue
        if ":" not in content:
            index += 1
            continue
        key, _, rest = content.partition(":")
        key = key.strip()
        rest = rest.strip()
        index += 1
        if rest:
            result[key] = _scalar(rest)
            continue
        if index < len(lines) and lines[index][0] > line_indent:
            value, index = _parse_block(lines, index, lines[index][0])
            result[key] = value
        else:
            result[key] = {}
    return result, index


def _parse_sequence(lines, index, indent):
    items = []
    while index < len(lines):
        line_indent, content = lines[index]
        if line_indent != indent or not content.startswith("- "):
            break
        item_text = content[2:].strip()
        index += 1
        if ":" in item_text and not item_text.endswith(":"):
            key, _, rest = item_text.partition(":")
            item = {key.strip(): _scalar(rest)}
            while index < len(lines) and lines[index][0] > indent \
                    and not lines[index][1].startswith("- "):
                sub_indent, sub_content = lines[index]
                if ":" in sub_content:
                    sub_key, _, sub_rest = sub_content.partition(":")
                    item[sub_key.strip()] = _scalar(sub_rest)
                index += 1
                del sub_indent
            items.append(item)
        elif item_text:
            items.append(_scalar(item_text))
        else:
            value, index = _parse_block(lines, index, lines[index][0]) \
                if index < len(lines) else ({}, index)
            items.append(value)
    return items, index


def load_study_config(task_dir: Path) -> dict:
    """Return study_config.yaml as nested dicts, or {} when absent."""
    path = task_dir / "study_config.yaml"
    if not path.is_file():
        return {}
    try:
        text = path.read_text(encoding="utf-8-sig")
    except OSError:
        return {}
    config, _ = _parse_mapping(_yaml_lines(text), 0, 0)
    return config


# ---------------------------------------------------------------------------
# Folder inspection
# ---------------------------------------------------------------------------

def _now() -> str:
    return _dt.datetime.now(_dt.timezone.utc).replace(microsecond=0).isoformat()


def _mtime(path: Path) -> str:
    try:
        stamp = _dt.datetime.fromtimestamp(path.stat().st_mtime, _dt.timezone.utc)
        return stamp.strftime("%Y-%m-%d %H:%M")
    except OSError:
        return "-"


def _size(path: Path) -> str:
    try:
        num = float(path.stat().st_size)
    except OSError:
        return "-"
    for unit in ("B", "KB", "MB", "GB"):
        if num < 1024.0 or unit == "GB":
            return "{:.0f} {}".format(num, unit) if unit == "B" else "{:.1f} {}".format(num, unit)
        num /= 1024.0
    return "-"


def _load_json(path: Path):
    try:
        return json.loads(path.read_text(encoding="utf-8-sig"))
    except (OSError, ValueError):
        return None


def _script_purpose(path: Path) -> str:
    """First docstring line, else first comment line, else empty."""
    try:
        head = path.read_text(encoding="utf-8", errors="replace")[:4000]
    except OSError:
        return ""
    match = re.search(r'^\s*(?:[rubRUB]{0,2})("""|\'\'\')(.*?)\1', head, re.DOTALL)
    if match:
        body = match.group(2).strip().splitlines()
        if body:
            return body[0].strip()
    for line in head.splitlines():
        text = line.strip()
        if text.startswith("#!") or text.startswith("# -*-"):
            continue
        if text.startswith("#"):
            return text.lstrip("# ").strip()
        if text:
            break
    return ""


def _notebook_purpose(path: Path) -> str:
    """First markdown heading or line of the notebook."""
    data = _load_json(path)
    if not isinstance(data, dict):
        return ""
    for cell in data.get("cells", []):
        if cell.get("cell_type") != "markdown":
            continue
        for line in "".join(cell.get("source", [])).splitlines():
            text = line.strip().lstrip("#").strip()
            if text:
                return text
    return ""


def _notebook_executed(path: Path) -> bool:
    data = _load_json(path)
    if not isinstance(data, dict):
        return False
    for cell in data.get("cells", []):
        if cell.get("cell_type") == "code" and cell.get("outputs"):
            return True
    return False


def _md_escape(text) -> str:
    return str(text).replace("|", "\\|").replace("\n", " ").strip()


def _table(headers, rows) -> list:
    if not rows:
        return []
    out = ["| " + " | ".join(headers) + " |",
           "|" + "|".join(["---"] * len(headers)) + "|"]
    for row in rows:
        out.append("| " + " | ".join(_md_escape(cell) for cell in row) + " |")
    out.append("")
    return out


def _rel(path: Path, task_dir: Path) -> str:
    try:
        return path.relative_to(task_dir).as_posix()
    except ValueError:
        return path.as_posix()


def _extract_section(text: str, *headings) -> str:
    """Return the body under the first matching markdown heading."""
    if not text:
        return ""
    lines = text.splitlines()
    wanted = [h.lower() for h in headings]
    capture = False
    body = []
    for line in lines:
        if line.startswith("#"):
            title = line.lstrip("#").strip().lower()
            if capture:
                break
            capture = any(w in title for w in wanted)
            continue
        if capture:
            body.append(line)
    text_out = "\n".join(body).strip()
    if "[" in text_out and "]" in text_out and len(text_out) < 200:
        # Still a template placeholder such as "[Summary of the context]".
        if re.fullmatch(r"\[[^\]]*\]", text_out.strip()):
            return ""
    return text_out


def _read(path: Path) -> str:
    try:
        return path.read_text(encoding="utf-8-sig")
    except OSError:
        return ""


# ---------------------------------------------------------------------------
# Section builders
# ---------------------------------------------------------------------------

PLACEHOLDER_MARKERS = ("[paste ", "[e.g.", "[summary of", "[describe", "[title]",
                       "yyyy-mm-dd", "original_user_prompt", "| | |")


def _is_placeholder(text: str) -> bool:
    """Return true when text is still unfilled template content."""
    stripped = (text or "").strip()
    if not stripped:
        return True
    if re.fullmatch(r"\[[^\]]*\]", stripped):
        return True
    lowered = stripped.lower()
    return any(marker in lowered for marker in PLACEHOLDER_MARKERS)


def _narrative(block_id: str, seed: str) -> list:
    """Emit a preserved narrative block with a seed placeholder."""
    return [NARRATIVE_OPEN.format(block_id), seed.rstrip(), NARRATIVE_CLOSE, ""]


def _collect_scripts(task_dir: Path, config: dict) -> list:
    """Return declared + discovered analysis scripts and notebooks."""
    analysis_dir = task_dir / "step2_analysis"
    declared = {}
    for entry in (config.get("analysis") or {}).get("scripts") or []:
        if isinstance(entry, dict) and entry.get("file"):
            declared[str(entry["file"]).strip()] = entry
    for entry in (config.get("notebooks") or {}).get("plan") or []:
        if isinstance(entry, dict) and entry.get("file"):
            declared[str(entry["file"]).strip()] = entry

    found = []
    if analysis_dir.is_dir():
        for path in sorted(analysis_dir.rglob("*")):
            if not path.is_file() or path.suffix.lower() not in (".py", ".ipynb"):
                continue
            if path.name in SKIP_SCRIPT_NAMES:
                continue
            if SKIP_SCRIPT_DIRS.intersection(path.parts):
                continue
            found.append(path)

    rows = []
    seen = set()
    for path in found:
        rel = _rel(path, task_dir)
        key = path.name
        entry = declared.get(rel) or declared.get(key) or {}
        seen.add(rel)
        seen.add(key)
        is_notebook = path.suffix.lower() == ".ipynb"
        purpose = entry.get("purpose") or (
            _notebook_purpose(path) if is_notebook else _script_purpose(path))
        produces = entry.get("produces", "")
        if produces and not (task_dir / str(produces)).exists():
            produces = "{} (missing)".format(produces)
        status = ""
        if is_notebook:
            status = "executed" if _notebook_executed(path) else "no stored outputs"
        rows.append({
            "rel": rel,
            "kind": "notebook" if is_notebook else "script",
            "purpose": purpose or "-",
            "produces": produces or "-",
            "modified": _mtime(path),
            "status": status,
            "declared": bool(entry),
        })

    for name, entry in declared.items():
        if name in seen or Path(name).name in seen:
            continue
        rows.append({
            "rel": name,
            "kind": "notebook" if name.endswith(".ipynb") else "script",
            "purpose": entry.get("purpose", "-") or "-",
            "produces": entry.get("produces", "-") or "-",
            "modified": "MISSING",
            "status": "planned but not present",
            "declared": True,
        })

    # Declared steps first, then documented helpers, then undocumented probes —
    # so the table reads as the method rather than as a directory listing.
    def _rank(row):
        if row["declared"]:
            return (0, row["rel"].lower())
        if row["purpose"] != "-":
            return (1, row["rel"].lower())
        return (2, row["rel"].lower())

    rows.sort(key=_rank)
    return rows


def _data_files(task_dir: Path) -> list:
    analysis_dir = task_dir / "step2_analysis"
    if not analysis_dir.is_dir():
        return []
    rows = []
    for path in sorted(analysis_dir.rglob("*")):
        if not path.is_file() or path.suffix.lower() not in DATA_SUFFIXES:
            continue
        if "__pycache__" in path.parts or ".ipynb_checkpoints" in path.parts:
            continue
        if path.name.endswith(".log"):
            continue
        rows.append((_rel(path, task_dir), _size(path), _mtime(path)))
    return rows


def _reference_summary(task_dir: Path) -> tuple:
    """Return (per-source counts, total, sources_md_exists, data_gaps)."""
    references = task_dir / "step1_scope_and_research" / "references"
    manifest = _load_json(references / "collection_manifest.json")
    counts = {}
    total = 0
    gaps = []
    if isinstance(manifest, dict):
        for group in manifest.get("sources", []) or []:
            if not isinstance(group, dict):
                continue
            name = (group.get("system_name") or group.get("source")
                    or group.get("name") or "other")
            files = group.get("documents") or group.get("files") or []
            counts[name] = counts.get(name, 0) + len(files)
            total += len(files)
        for gap in manifest.get("data_gaps", []) or []:
            if isinstance(gap, dict):
                gaps.append(gap.get("description") or gap.get("gap") or json.dumps(gap))
            else:
                gaps.append(str(gap))
    if not counts and references.is_dir():
        for path in sorted(references.rglob("*")):
            if not path.is_file() or path.name in ("SOURCES.md",
                                                   "collection_manifest.json"):
                continue
            parent = path.parent.name if path.parent != references else "unfiled"
            counts[parent] = counts.get(parent, 0) + 1
            total += 1
    return counts, total, (references / "SOURCES.md").is_file(), gaps


def _folder_map(task_dir: Path, max_entries: int = 12) -> list:
    """Annotated one-level-deep folder map of the task."""
    notes = {
        "study_config.yaml": "input contract: depth, data sources, gates",
        "user_input.md": "the original request and clarifying Q&A",
        "results.json": "machine-readable results — the interface for downstream tasks",
        "consistency_report.json": "cross-notebook numeric consistency check",
        "README.md": "task status and navigation",
        "step1_scope_and_research": "scope, standards, capability assessment, collected documents",
        "step2_analysis": "scripts, notebooks, cached source data, intermediate results",
        "step3_report": "generated deliverables (this file, <title>.docx/html)",
        "figures": "figures referenced by the report and results.json",
    }
    lines = ["```"]
    lines.append("{}/".format(task_dir.name))
    entries = sorted(task_dir.iterdir(), key=lambda p: (p.is_file(), p.name.lower()))
    for path in entries:
        if path.name.startswith(".") or path.name == "__pycache__":
            continue
        note = notes.get(path.name, "")
        suffix = "/" if path.is_dir() else ""
        lines.append("  {}{}{}".format(
            path.name, suffix, "   # " + note if note else ""))
        if path.is_dir():
            children = [c for c in sorted(path.iterdir())
                        if not c.name.startswith(".") and c.name != "__pycache__"]
            for child in children[:max_entries]:
                child_note = notes.get(child.name, "")
                lines.append("    {}{}{}".format(
                    child.name, "/" if child.is_dir() else "",
                    "   # " + child_note if child_note else ""))
            if len(children) > max_entries:
                lines.append("    ... {} more".format(len(children) - max_entries))
    lines.append("```")
    lines.append("")
    return lines


def build_work_record(task_dir: Path, preserved: dict) -> str:
    """Build the full WORK_RECORD.md text for a task folder."""
    config = load_study_config(task_dir)
    study = config.get("study") or {}
    inputs = config.get("inputs") or {}
    analysis = config.get("analysis") or {}
    report_cfg = config.get("report") or {}
    results = _load_json(task_dir / "results.json") or {}

    title = study.get("title") or task_dir.name.replace("_", " ")
    task_type = str(study.get("task_type", "")).strip().upper()
    type_label = "{} — {}".format(task_type, TASK_TYPE_NAMES[task_type]) \
        if task_type in TASK_TYPE_NAMES else (task_type or "-")

    readme = _read(task_dir / "README.md")
    status_match = re.search(r"\*\*Status:\*\*\s*(.*)", readme)
    status = status_match.group(1).strip() if status_match else "-"

    spec = _read(task_dir / "step1_scope_and_research" / "task_spec.md")
    user_input = _read(task_dir / "user_input.md")

    out = []
    out.append("# Work Record — {}".format(title))
    out.append("")
    out.append("> What was done, how, with which data, and where every file lives.")
    out.append("> Companion to the engineering report: the report carries the")
    out.append("> conclusion, this file carries the method and the provenance.")
    out.append(">")
    out.append("> Regenerate with `neqsim work-record \"{}\"`. Text inside the".format(
        task_dir.name))
    out.append("> NARRATIVE blocks is hand-written and is preserved on regeneration;")
    out.append("> everything else is rebuilt from the folder.")
    out.append("")

    out.extend(_table(
        ["Field", "Value"],
        [
            ["Task", title],
            ["Task folder", str(task_dir)],
            ["Task type", type_label],
            ["Scale / mode", "{} / {}".format(study.get("scale", "auto"),
                                              study.get("mode", "auto"))],
            ["AACE class / FEL stage", "{} / {}".format(study.get("aace_class", "auto"),
                                                        study.get("fel_stage", "auto"))],
            ["Analysis engine", analysis.get("engine", "auto")],
            ["Status", status],
            ["Record generated", _now()],
        ]))

    # 1 — Background -------------------------------------------------------
    out.append("## 1. Background and objective")
    out.append("")
    seed = _extract_section(spec, "objective", "problem statement", "scope") \
        or _extract_section(user_input, "original request", "request", "prompt")
    if _is_placeholder(seed):
        seed = ("[Why this task exists, what question it answers, and the operating "
                "context a reader needs before the method. The verbatim request is "
                "in `user_input.md`.]")
    out.extend(_narrative("background", preserved.get("background", seed)))

    document_root = inputs.get("document_root") or ""
    if document_root:
        out.append("Source document library: `{}` (read-only; documents actually "
                   "used are copied into this task).".format(document_root))
        out.append("")

    # 2 — Method -----------------------------------------------------------
    out.append("## 2. What was done")
    out.append("")
    out.extend(_narrative(
        "method",
        preserved.get("method",
                      "[The method in prose: the approach chosen, why it was chosen "
                      "over the alternatives, what each step established, and the "
                      "decisions taken along the way.]")))

    steps = []
    for entry in analysis.get("scripts") or []:
        if isinstance(entry, dict) and entry.get("file"):
            steps.append((entry["file"], entry.get("purpose", "")))
    for entry in (config.get("notebooks") or {}).get("plan") or []:
        if isinstance(entry, dict) and entry.get("file"):
            steps.append((entry["file"], entry.get("purpose", "")))
    if steps:
        out.append("Planned sequence (from `study_config.yaml`):")
        out.append("")
        for number, (name, purpose) in enumerate(steps, 1):
            out.append("{}. `{}` — {}".format(number, name, purpose or "-"))
        out.append("")

    # 3 — Scripts and notebooks -------------------------------------------
    out.append("## 3. Scripts and notebooks")
    out.append("")
    script_rows = _collect_scripts(task_dir, config)
    if script_rows:
        out.extend(_table(
            ["File", "Kind", "Purpose", "Produces", "Last modified", "Status"],
            [["`{}`".format(row["rel"]), row["kind"], row["purpose"],
              row["produces"], row["modified"], row["status"] or "-"]
             for row in script_rows]))
    else:
        out.append("No scripts or notebooks found in `step2_analysis/`.")
        out.append("")

    # 4 — Data -------------------------------------------------------------
    out.append("## 4. Data used")
    out.append("")
    out.append("### 4.1 Source systems")
    out.append("")
    data_sources = inputs.get("data_sources") or []
    if data_sources:
        rows = []
        for entry in data_sources:
            if not isinstance(entry, dict):
                continue
            evidence = str(entry.get("evidence", "") or "")
            if not evidence:
                state = "no evidence path declared"
            elif (task_dir / evidence).exists():
                state = "captured"
            else:
                state = "MISSING"
            rows.append([entry.get("system", "-"), entry.get("scope", "-"),
                         entry.get("access", "-"),
                         "`{}`".format(evidence) if evidence else "-", state])
        out.extend(_table(
            ["System", "Scope read", "Access", "Captured evidence", "Status"], rows))
    else:
        out.append("No source systems declared in `study_config.yaml` "
                   "(`inputs.data_sources`).")
        out.append("")

    out.append("### 4.2 Reference documents")
    out.append("")
    counts, total, has_sources, gaps = _reference_summary(task_dir)
    if total:
        out.append("{} document(s) collected under "
                   "`step1_scope_and_research/references/`:".format(total))
        out.append("")
        out.extend(_table(["Source", "Files"],
                          [[name, count] for name, count in sorted(counts.items())]))
        if has_sources:
            out.append("Per-file origin, retrieval date, and relevance: "
                       "`step1_scope_and_research/references/SOURCES.md`.")
            out.append("")
    else:
        out.append("No reference documents collected.")
        out.append("")
    if gaps:
        out.append("Declared document gaps:")
        out.append("")
        for gap in gaps:
            out.append("- {}".format(gap))
        out.append("")

    out.append("### 4.3 Data files produced or cached in the task")
    out.append("")
    data_rows = _data_files(task_dir)
    if data_rows:
        out.extend(_table(["File", "Size", "Last modified"],
                          [["`{}`".format(rel), size, mtime]
                           for rel, size, mtime in data_rows]))
    else:
        out.append("No cached data files in `step2_analysis/`.")
        out.append("")

    # 5 — Results ----------------------------------------------------------
    out.append("## 5. Results and artifacts")
    out.append("")
    key_results = results.get("key_results") or {}
    if isinstance(key_results, dict) and key_results:
        out.extend(_table(["Key result", "Value"],
                          [[name, value] for name, value in key_results.items()]))
    else:
        out.append("`results.json` has no `key_results` section.")
        out.append("")

    figures_dir = task_dir / "figures"
    captions = results.get("figure_captions") or {}
    figure_files = sorted(figures_dir.glob("*.png")) if figures_dir.is_dir() else []
    if figure_files:
        out.append("Figures (`figures/`):")
        out.append("")
        out.extend(_table(["Figure", "Caption"],
                          [[name.name, captions.get(name.name, "-")]
                           for name in figure_files]))

    deliverables = []
    report_dir = task_dir / "step3_report"
    if report_dir.is_dir():
        for path in sorted(report_dir.iterdir()):
            if path.name == WORK_RECORD_NAME:
                continue
            if path.is_file() and path.suffix.lower() in (".docx", ".html", ".pdf", ".md"):
                deliverables.append([_rel(path, task_dir), _size(path), _mtime(path)])
    if deliverables:
        out.append("Deliverables:")
        out.append("")
        out.extend(_table(["File", "Size", "Last modified"], deliverables))

    # 6 — Folder map -------------------------------------------------------
    out.append("## 6. Where to find everything")
    out.append("")
    out.extend(_folder_map(task_dir))

    # 7 — Reproduction -----------------------------------------------------
    out.append("## 7. How to reproduce")
    out.append("")
    declared_order = [str(entry["file"]) for entry in analysis.get("scripts") or []
                      if isinstance(entry, dict) and entry.get("file")]
    declared_order.extend(
        str(entry["file"]) for entry in (config.get("notebooks") or {}).get("plan") or []
        if isinstance(entry, dict) and entry.get("file"))
    present = set(row["rel"] for row in script_rows if row["modified"] != "MISSING")
    present.update(Path(row["rel"]).name for row in script_rows
                   if row["modified"] != "MISSING")
    declared_order = [name for name in declared_order
                      if name in present or Path(name).name in present]
    if declared_order:
        out.append("Declared sequence from `study_config.yaml` — the steps needed to "
                   "rebuild the result. Run from the task folder with the shared "
                   "Python environment.")
    else:
        out.append("No sequence is declared in `study_config.yaml` "
                   "(`analysis.scripts` / `notebooks.plan`), so the steps below are "
                   "every script and notebook found, in name order — check the "
                   "dependencies before running them.")
    out.append("")
    out.append("```bash")
    if declared_order:
        for name in declared_order:
            target = name if name.startswith("step2_analysis") \
                else "step2_analysis/{}".format(name)
            if target.endswith(".ipynb"):
                out.append("# notebook: {} (NeqSim Runner or Jupyter)".format(target))
            else:
                out.append("python {}".format(target))
    else:
        ordered = [row for row in script_rows if row["modified"] != "MISSING"]
        for row in sorted(ordered, key=lambda item: item["rel"]):
            if row["kind"] == "script":
                out.append("python {}".format(row["rel"]))
            else:
                out.append("# notebook: {} (NeqSim Runner or Jupyter)".format(row["rel"]))
    out.append("python <neqsim>/devtools/consistency_checker.py .")
    out.append("neqsim report .")
    out.append("```")
    out.append("")
    out.append("`neqsim report` regenerates this work record as well, so the two "
               "deliverables stay in step.")
    out.append("")
    if declared_order:
        undeclared = [row["rel"] for row in script_rows
                      if row["modified"] != "MISSING"
                      and row["rel"] not in declared_order
                      and Path(row["rel"]).name not in declared_order]
        if undeclared:
            out.append("The remaining {} file(s) in `step2_analysis/` are probes, "
                       "inspections, and one-off checks listed in section 3; they "
                       "document how the answer was arrived at but are not needed to "
                       "reproduce it.".format(len(undeclared)))
            out.append("")

    # 8 — Assumptions and gaps --------------------------------------------
    out.append("## 8. Assumptions, limitations and gaps")
    out.append("")
    for key, label in (("assumptions", "Assumptions"), ("gaps", "Data gaps"),
                       ("limitations", "Limitations")):
        items = results.get(key)
        if isinstance(items, list) and items:
            out.append("**{}** (from `results.json`):".format(label))
            out.append("")
            for item in items:
                if isinstance(item, dict):
                    text = item.get("description") or item.get("text") or json.dumps(item)
                else:
                    text = str(item)
                out.append("- {}".format(text))
            out.append("")
    out.extend(_narrative(
        "limitations",
        preserved.get("limitations",
                      "[What this study does not establish, which numbers are "
                      "screening-level, and what evidence would change the answer.]")))

    # 9 — Provenance and review -------------------------------------------
    out.append("## 9. Provenance and review")
    out.append("")
    provenance_rows = []
    cited = results.get("data_sources") or results.get("sources")
    provenance_rows.append(["results.json cites its sources",
                            "yes" if cited else "no"])
    provenance_rows.append(["Benchmark validation",
                            "present" if results.get("benchmark_validation") else "absent"])
    provenance_rows.append(["Uncertainty analysis",
                            "present" if results.get("uncertainty") else "absent"])
    provenance_rows.append(["Risk register",
                            "present" if (results.get("risk_evaluation")
                                          or results.get("risks")) else "absent"])
    review = results.get("human_review") or results.get("review")
    provenance_rows.append(["Human review",
                            json.dumps(review) if isinstance(review, dict)
                            else (str(review) if review else "not recorded")])
    provenance_rows.append(["Consistency report",
                            "present" if (task_dir / "consistency_report.json").is_file()
                            else "absent"])
    out.extend(_table(["Check", "State"], provenance_rows))

    out.append("---")
    out.append("")
    out.append("Generated by `devtools/generate_work_record.py` on {}.".format(_now()))
    out.append("Report deliverable settings: formats={}, work_record={}.".format(
        report_cfg.get("formats", "-"), report_cfg.get("work_record", "auto")))
    out.append("")
    return "\n".join(out)


def _preserved_narratives(path: Path) -> dict:
    """Return {id: text} for hand-written blocks in an existing work record."""
    if not path.is_file():
        return {}
    text = _read(path)
    preserved = {}
    for block_id, body in NARRATIVE_PATTERN.findall(text):
        if not _is_placeholder(body):
            preserved[block_id] = body.strip()
    return preserved


def check_work_record(task_dir: Path) -> list:
    """Return problems that make the work record unusable as a deliverable."""
    path = task_dir / "step3_report" / WORK_RECORD_NAME
    if not path.is_file():
        return ["Work record is missing: step3_report/{}".format(WORK_RECORD_NAME)]
    text = _read(path)
    problems = []
    blocks = NARRATIVE_PATTERN.findall(text)
    unfilled = [block_id for block_id, body in blocks if _is_placeholder(body)]
    if unfilled:
        problems.append(
            "Work record narrative still holds template text: {}.".format(
                ", ".join(unfilled)))
    if "MISSING" in text:
        problems.append(
            "Work record lists missing artifacts (search for MISSING in "
            "step3_report/{}).".format(WORK_RECORD_NAME))
    return problems


def _is_task_dir(path: Path) -> bool:
    return any((path / marker).exists() for marker in
               ("study_config.yaml", "results.json", "step1_scope_and_research",
                "step3_report"))


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(
        description="Generate step3_report/WORK_RECORD.md for a task folder.")
    parser.add_argument("task_dir", nargs="?", default=".",
                        help="Task folder (default: current directory).")
    parser.add_argument("--check", action="store_true",
                        help="Do not write; report whether the work record is usable.")
    parser.add_argument("--stdout", action="store_true",
                        help="Print the generated markdown instead of writing it.")
    args = parser.parse_args(argv)

    task_dir = Path(os.path.abspath(os.path.expanduser(args.task_dir)))
    if not task_dir.is_dir():
        print("ERROR: not a folder: {}".format(task_dir))
        return 2
    if not _is_task_dir(task_dir):
        print("ERROR: {} does not look like a task folder.".format(task_dir))
        return 2

    if args.check:
        problems = check_work_record(task_dir)
        for problem in problems:
            print("WARNING: {}".format(problem))
        if not problems:
            print("Work record OK: step3_report/{}".format(WORK_RECORD_NAME))
        return 1 if problems else 0

    target = task_dir / "step3_report" / WORK_RECORD_NAME
    preserved = _preserved_narratives(target)
    text = build_work_record(task_dir, preserved)

    if args.stdout:
        sys.stdout.write(text)
        return 0

    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(text, encoding="utf-8")
    print("Wrote {}".format(target))
    if preserved:
        print("Preserved narrative block(s): {}".format(", ".join(sorted(preserved))))
    else:
        print("Fill the NARRATIVE blocks (background, method, limitations) — "
              "they are preserved on the next regeneration.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
