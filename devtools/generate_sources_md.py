#!/usr/bin/env python3
"""Organize collected task documents into per-source folders and generate a
distributable ``SOURCES.md`` summary.

When solving a PEPR task or a general engineering task, documents are collected
from many sources (PEPR action attachments, STID, TR2000, SAP/Maintenance,
ServiceNow, plant historian, Seeq, Rigga, literature, vendor datasheets, manual
uploads). This script makes the collected set easy to distribute and reuse by:

1. Organizing every file under ``step1_scope_and_research/references/`` into a
   per-source subfolder (``pepr/``, ``stid/``, ``tr2000/`` ...), so the whole
   ``references/`` folder — or any single source folder — can be handed to
   someone else.
2. Writing a canonical machine-readable ``references/collection_manifest.json``.
3. Writing a human-readable ``references/SOURCES.md`` that lists, per source,
   every collected file with its origin (document number / tag / action / RITM /
   historian tag), retrieval date, classification, relevance, review status, and
   a one-line summary — plus a data-gaps section and a provenance note.

The script has NO third-party dependencies and works for both the ``task_solve/``
(NeqSim repo) and ``tasks/`` (engineering-tasks-splits repo) layouts.

Usage
-----
    python devtools/generate_sources_md.py <task_dir> [--organize] [--default-source manual]

``<task_dir>`` is the task folder root (it must contain, or will get, a
``step1_scope_and_research/references/`` folder). ``--organize`` moves loose
files sitting directly in ``references/`` into a source subfolder inferred from
the manifests, filename, or ``--default-source`` (default ``other``). Without
``--organize`` the file tree is left untouched and only the summary is written.

Metadata is merged (matched by file name) from any of these manifests if present
under the task folder: ``references/collection_manifest.json``,
``step1_scope_and_research/retrieval_manifest.json``,
``step1_scope_and_research/document_evidence_manifest.json``,
``references/manifest.json``, ``references/manifest_*.json``.
"""

from __future__ import annotations

import argparse
import datetime as _dt
import hashlib
import json
import os
import re
import shutil
import sys
from pathlib import Path

SCHEMA = "references_collection_manifest.v1"

# Source key -> (display name, description). The key is also the subfolder name.
SOURCE_CATALOG = [
    ("pepr", "PEPR", "PEPR action file attachments"),
    ("stid", "STID", "STID controlled documents (datasheets, P&IDs, drawings)"),
    ("tr2000", "TR2000", "Piping-class, valve, material and rating data"),
    ("maintenance", "SAP / Maintenance", "Work orders, failure reports, linked documents"),
    ("servicenow", "ServiceNow", "Referenced records (RITM / INC / CHG / SCTASK)"),
    ("tagreader", "Plant historian", "PI / IP.21 (tagreader) signal exports"),
    ("seeq", "Seeq", "Seeq signal and capsule exports"),
    ("rigga", "Rigga / PDM", "Measured production volumes"),
    ("vendor", "Vendor", "Vendor datasheets, manuals, performance maps"),
    ("lab", "Lab / PVT", "Lab, PVT and gas-sample reports"),
    ("literature", "Literature", "Papers, standards, textbooks"),
    ("web", "Web", "Saved web pages, article extracts, online references"),
    ("manual", "Manual upload", "User-provided documents"),
    ("other", "Other", "Uncategorised / needs filing"),
]
SOURCE_KEYS = [key for key, _name, _desc in SOURCE_CATALOG]
SOURCE_NAME = {key: name for key, name, _desc in SOURCE_CATALOG}
SOURCE_DESC = {key: desc for key, _name, desc in SOURCE_CATALOG}

# Generated / bookkeeping artifacts that are not "collected documents".
SKIP_FILES = {
    "SOURCES.md",
    "collection_manifest.json",
    "manifest.json",
    "retrieval_manifest.json",
    "document_evidence_manifest.json",
    "related_peprs.json",
}


def _now_utc_iso() -> str:
    return _dt.datetime.now(_dt.timezone.utc).replace(microsecond=0).isoformat()


def _today() -> str:
    return _dt.datetime.now(_dt.timezone.utc).date().isoformat()


def _sha256_short(path: Path, length: int = 12) -> str:
    try:
        digest = hashlib.sha256()
        with path.open("rb") as stream:
            for chunk in iter(lambda: stream.read(65536), b""):
                digest.update(chunk)
        return digest.hexdigest()[:length]
    except OSError:
        return ""


def _human_size(num_bytes: int) -> str:
    size = float(num_bytes)
    for unit in ("B", "KB", "MB", "GB"):
        if size < 1024.0 or unit == "GB":
            return f"{size:.0f} {unit}" if unit == "B" else f"{size:.1f} {unit}"
        size /= 1024.0
    return f"{size:.1f} GB"


def _infer_source_from_name(name: str) -> str:
    lower = name.lower()
    patterns = [
        ("pepr", r"pepr|action[_-]?\d{6,}|related_pepr"),
        ("servicenow", r"\britm\d|\binc\d|\bchg\d|\bsctask\d|servicenow"),
        ("tagreader", r"tagreader|historian|\bpi[_-]|ip21|ip\.21|_trend|timeseries"),
        ("seeq", r"seeq"),
        ("rigga", r"rigga|pdm|production_volume"),
        ("tr2000", r"tr2000|\bpcs[_-]|\bvds[_-]|\bmds[_-]|pipe[_-]?class"),
        ("stid", r"stid|p&id|pid[_-]|_pid|datasheet|drawing|\bds[_-]|\baa[_-]|\bmd[_-]"),
        ("vendor", r"vendor|performance|curve|\bmap\b|manual"),
        ("lab", r"\bpvt\b|lab[_-]|gas[_-]?sample|assay"),
        ("web", r"\bweb|https?[_-]|www[._-]|\.html?$|wikipedia|webpage|webbook"),
        ("literature", r"paper|standard|norsok|\bapi[_-]|\biso[_-]|\bieee\b|doi|journal"),
        ("maintenance", r"work[_-]?order|failure[_-]?report|notification|\bwo[_-]|maintenance|sap[_-]"),
    ]
    for source, pattern in patterns:
        if re.search(pattern, lower):
            return source
    return ""


def _load_json(path: Path):
    try:
        with path.open("r", encoding="utf-8-sig") as stream:
            return json.load(stream)
    except (OSError, ValueError):
        return None


def _index_metadata(task_dir: Path, references_dir: Path) -> dict:
    """Return {basename_lower: metadata dict} merged from any known manifests."""
    index: dict = {}
    candidates = [
        references_dir / "collection_manifest.json",
        task_dir / "step1_scope_and_research" / "retrieval_manifest.json",
        task_dir / "step1_scope_and_research" / "document_evidence_manifest.json",
        references_dir / "manifest.json",
    ]
    candidates.extend(sorted(references_dir.glob("manifest_*.json")))

    for path in candidates:
        if not path.exists():
            continue
        data = _load_json(path)
        if data is None:
            continue
        for record in _iter_manifest_records(data):
            name = _record_filename(record)
            if not name:
                continue
            merged = index.get(name.lower(), {})
            merged.update({k: v for k, v in record.items() if v not in (None, "", [])})
            index[name.lower()] = merged
    return index


def _iter_manifest_records(data):
    """Yield document-like dicts from the various manifest schemas."""
    if isinstance(data, dict):
        for key in (
            "documents",
            "documents_retrieved",
            "documents_filtered_out",
            "records",
            "attachments",
            "files",
        ):
            value = data.get(key)
            if isinstance(value, list):
                for item in value:
                    if isinstance(item, dict):
                        yield item
        # document_evidence_manifest.json style: sources -> documents
        sources = data.get("sources")
        if isinstance(sources, list):
            for src in sources:
                if isinstance(src, dict):
                    docs = src.get("documents") or src.get("files")
                    src_key = src.get("source") or src.get("system")
                    if isinstance(docs, list):
                        for item in docs:
                            if isinstance(item, dict):
                                if src_key and "source" not in item:
                                    item = {**item, "source": src_key}
                                yield item
    elif isinstance(data, list):
        for item in data:
            if isinstance(item, dict):
                yield item


def _record_filename(record: dict) -> str:
    for key in ("file", "filename", "file_name", "path", "name", "source_path"):
        value = record.get(key)
        if value:
            return Path(str(value)).name
    return ""


def _record_source(record: dict) -> str:
    value = str(record.get("source") or record.get("system") or "").strip().lower()
    return value if value in SOURCE_KEYS else ""


def _record_source_id(record: dict) -> str:
    for key in ("source_id", "doc_no", "docNo", "document", "tag", "action_id", "id"):
        value = record.get(key)
        if value:
            return str(value)
    return ""


def _record_summary(record: dict) -> str:
    for key in ("summary", "description", "title", "note"):
        value = record.get(key)
        if value:
            return str(value).replace("\n", " ").strip()
    return ""


def scan_references(task_dir: Path, references_dir: Path) -> dict:
    """Scan the references tree and build the canonical collection record."""
    metadata = _index_metadata(task_dir, references_dir)
    by_source: dict = {key: [] for key in SOURCE_KEYS}

    for root, _dirs, files in os.walk(references_dir):
        root_path = Path(root)
        for filename in sorted(files):
            if filename in SKIP_FILES or filename.startswith("."):
                continue
            file_path = root_path / filename
            rel = file_path.relative_to(references_dir)
            rel_parts = rel.parts

            record = dict(metadata.get(filename.lower(), {}))
            # Source precedence: subfolder > manifest > filename inference > other.
            if len(rel_parts) > 1 and rel_parts[0] in SOURCE_KEYS:
                source = rel_parts[0]
            else:
                source = _record_source(record) or _infer_source_from_name(filename) or "other"

            try:
                size = file_path.stat().st_size
            except OSError:
                size = 0

            entry = {
                "file": str(rel).replace(os.sep, "/"),
                "name": filename,
                "source": source,
                "source_id": _record_source_id(record),
                "title": str(record.get("title") or "").strip(),
                "doc_type": str(record.get("doc_type") or "").strip(),
                "classification": str(record.get("classification") or record.get("doc_type_label") or "").strip(),
                "relevance": record.get("relevance", ""),
                "retrieved": str(record.get("retrieved_utc") or record.get("retrieval_date") or record.get("retrieved") or "").strip(),
                "review_status": str(record.get("review_status") or record.get("status") or "").strip(),
                "summary": _record_summary(record),
                "bytes": size,
                "sha256": _sha256_short(file_path),
            }
            by_source[source].append(entry)

    gaps = _collect_gaps(task_dir, references_dir)

    return {
        "schema": SCHEMA,
        "generated_utc": _now_utc_iso(),
        "task_dir": str(task_dir),
        "references_dir": str(references_dir),
        "sources": [
            {
                "source": key,
                "system_name": SOURCE_NAME[key],
                "description": SOURCE_DESC[key],
                "documents": by_source[key],
            }
            for key in SOURCE_KEYS
            if by_source[key]
        ],
        "data_gaps": gaps,
        "totals": {
            "documents": sum(len(v) for v in by_source.values()),
            "sources": sum(1 for v in by_source.values() if v),
        },
    }


def _collect_gaps(task_dir: Path, references_dir: Path) -> list:
    gaps: list = []
    seen = set()
    for path in [
        references_dir / "collection_manifest.json",
        task_dir / "step1_scope_and_research" / "retrieval_manifest.json",
        task_dir / "step1_scope_and_research" / "document_evidence_manifest.json",
    ]:
        data = _load_json(path) if path.exists() else None
        if not isinstance(data, dict):
            continue
        for key in ("data_gaps", "gaps", "missing_documents", "missing_requested_doc_types"):
            value = data.get(key)
            if isinstance(value, list):
                for item in value:
                    text = item if isinstance(item, str) else (
                        item.get("description") or item.get("identifier") or item.get("reason") or json.dumps(item)
                        if isinstance(item, dict) else str(item)
                    )
                    text = str(text).strip()
                    if text and text.lower() not in seen:
                        seen.add(text.lower())
                        gaps.append(text)
    return gaps


def organize(task_dir: Path, references_dir: Path, default_source: str) -> list:
    """Move loose files in references/ root into per-source subfolders. Returns moves."""
    metadata = _index_metadata(task_dir, references_dir)
    moves = []
    for entry in sorted(references_dir.iterdir()):
        if not entry.is_file() or entry.name in SKIP_FILES or entry.name.startswith("."):
            continue
        record = metadata.get(entry.name.lower(), {})
        source = _record_source(record) or _infer_source_from_name(entry.name) or default_source
        if source not in SOURCE_KEYS:
            source = "other"
        target_dir = references_dir / source
        target_dir.mkdir(parents=True, exist_ok=True)
        target = target_dir / entry.name
        if target.exists():
            continue
        shutil.move(str(entry), str(target))
        moves.append((entry.name, source))
    return moves


def _fmt_cell(value) -> str:
    text = "" if value in (None, "") else str(value)
    return text.replace("|", "\\|").replace("\n", " ")


def render_sources_md(record: dict) -> str:
    task_name = Path(record["task_dir"]).name
    lines = []
    lines.append(f"# Collected Documents — {task_name}")
    lines.append("")
    lines.append(
        "This folder is a self-contained, distributable collection of every source "
        "document gathered while solving this task. Each file is filed under a "
        "per-source subfolder; the tables below record where each document came from."
    )
    lines.append("")
    lines.append(f"- Generated: {record['generated_utc']}")
    lines.append(f"- Documents: {record['totals']['documents']}  |  Sources: {record['totals']['sources']}")
    lines.append("")
    lines.append("## Contents")
    lines.append("")
    for src in record["sources"]:
        lines.append(
            f"- [{src['system_name']}](#{src['source']}) — {len(src['documents'])} file(s): {src['description']}"
        )
    if not record["sources"]:
        lines.append("- (no documents collected yet)")
    lines.append("")

    for src in record["sources"]:
        lines.append(f"## {src['system_name']}")
        lines.append("")
        lines.append(f"_{src['description']}. Folder: `references/{src['source']}/`_")
        lines.append("")
        lines.append("| File | Source ID | Type | Relevance | Retrieved | Review | Summary |")
        lines.append("| --- | --- | --- | --- | --- | --- | --- |")
        for doc in src["documents"]:
            lines.append(
                "| {file} | {sid} | {typ} | {rel} | {ret} | {rev} | {summ} |".format(
                    file=f"`{_fmt_cell(doc['file'])}`",
                    sid=_fmt_cell(doc.get("source_id")),
                    typ=_fmt_cell(doc.get("classification") or doc.get("doc_type")),
                    rel=_fmt_cell(doc.get("relevance")),
                    ret=_fmt_cell(doc.get("retrieved")),
                    rev=_fmt_cell(doc.get("review_status")),
                    summ=_fmt_cell(doc.get("summary") or doc.get("title")),
                )
            )
        lines.append("")

    lines.append("## Data gaps / not retrieved")
    lines.append("")
    if record["data_gaps"]:
        for gap in record["data_gaps"]:
            lines.append(f"- {gap}")
    else:
        lines.append("- None recorded.")
    lines.append("")

    lines.append("## Provenance & distribution notes")
    lines.append("")
    lines.append(
        "- All documents were retrieved **read-only** from their source systems for "
        "this task only. Redistribute only in line with each source system's data "
        "classification and access rules."
    )
    lines.append(
        "- A document title or type code is not proof the content was reviewed; the "
        "`Review` column reflects the recorded review status."
    )
    lines.append(
        "- `collection_manifest.json` (next to this file) is the machine-readable "
        "record of the same collection."
    )
    lines.append("")
    return "\n".join(lines)


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("task_dir", help="Task folder root")
    parser.add_argument(
        "--organize",
        action="store_true",
        help="Move loose files in references/ into per-source subfolders",
    )
    parser.add_argument(
        "--default-source",
        default="other",
        choices=SOURCE_KEYS,
        help="Source folder for files that cannot be classified (default: other)",
    )
    args = parser.parse_args(argv)

    task_dir = Path(args.task_dir).resolve()
    if not task_dir.exists():
        print(f"ERROR: task dir not found: {task_dir}", file=sys.stderr)
        return 2
    references_dir = task_dir / "step1_scope_and_research" / "references"
    references_dir.mkdir(parents=True, exist_ok=True)

    if args.organize:
        moves = organize(task_dir, references_dir, args.default_source)
        for name, source in moves:
            print(f"  moved {name} -> {source}/")
        print(f"Organized {len(moves)} file(s) into source folders.")

    record = scan_references(task_dir, references_dir)

    manifest_path = references_dir / "collection_manifest.json"
    with manifest_path.open("w", encoding="utf-8") as stream:
        json.dump(record, stream, indent=2)

    sources_md_path = references_dir / "SOURCES.md"
    with sources_md_path.open("w", encoding="utf-8") as stream:
        stream.write(render_sources_md(record))

    print(f"Wrote {sources_md_path}")
    print(f"Wrote {manifest_path}")
    print(
        f"  {record['totals']['documents']} document(s) across "
        f"{record['totals']['sources']} source(s)."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
