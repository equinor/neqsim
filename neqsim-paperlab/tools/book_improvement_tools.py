"""Book improvement tooling for PaperLab course books.

The functions in this module turn the proposal in
``BOOK_IMPROVEMENT_PROPOSAL_2026.md`` into repeatable artifacts:

* ``source_manifest.json`` and ``source_manifest.md``
* ``figure_dossier.json`` and ``figure_dossier.html``
* ``coverage_audit.md``
* ``lecture_topic_coverage.json`` and ``lecture_topic_coverage.md``
* ``evidence_report.json`` and ``evidence_report.md``
* ``conciseness_audit.json`` and ``conciseness_audit.md``

The implementation intentionally works without LLM credentials. When a vision
model is available, ``book_figure_dossier`` can enrich local chapter figures by
calling the existing ``result_evaluator.generate_figure_context`` helper.
"""

from __future__ import annotations

import hashlib
import html
import json
import re
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path
from typing import Dict, Iterable, List, Optional, Tuple

import book_builder


DEFAULT_EXCLUDED_PARTS = {
    ".git",
    ".hg",
    ".svn",
    ".mypy_cache",
    ".pytest_cache",
    ".ruff_cache",
    ".venv",
    "venv",
    "env",
    "__pycache__",
    ".ipynb_checkpoints",
    "node_modules",
    "site-packages",
    "dist",
    "build",
    "target",
}

IMAGE_EXTENSIONS = {".png", ".jpg", ".jpeg", ".tif", ".tiff", ".svg", ".gif", ".webp"}
DOCUMENT_EXTENSIONS = {".pdf", ".docx", ".doc", ".pptx", ".ppt"}
SPREADSHEET_EXTENSIONS = {".xlsx", ".xlsm", ".xls", ".csv"}
NOTEBOOK_EXTENSIONS = {".ipynb"}

FIGURE_RE = re.compile(r"!\[([^\]]*)\]\(([^)]+)\)")
HEADING_RE = re.compile(r"^(#{2,3})\s+(.+)$", re.MULTILINE)
DISCUSSION_RE = re.compile(r"\*Observation\.\*|\*Mechanism\.\*|Discussion \(Figure", re.IGNORECASE)
CONCISENESS_BOILERPLATE_MARKERS = (
    "generated source appendix has been condensed",
    "full provenance is retained in coverage matrix",
    "for repeated background material use the canonical discussion",
)
GENERATED_TRACE_BLOCK_RE = re.compile(
    r"<!-- LECTURE_(?:TOPICS|COVERAGE)_START -->.*?<!-- LECTURE_(?:TOPICS|COVERAGE)_END -->",
    re.DOTALL,
)

STOPWORDS = {
    "about", "above", "after", "again", "against", "also", "although", "among",
    "and", "another", "any", "are", "because", "been", "before", "being", "between",
    "both", "can", "chapter", "chapters", "could", "course", "development", "does",
    "during", "each", "field", "figure", "for", "from", "has", "have", "into",
    "its", "may", "more", "must", "not", "oil", "one", "only", "other", "over",
    "process", "production", "should", "such", "than", "that", "the", "their",
    "there", "these", "this", "through", "too", "use", "used", "using", "was",
    "were", "when", "where", "which", "while", "with", "within", "would", "you",
    "your",
}

LECTURE_FIGURE_SIGNAL_TERMS = {
    "architecture", "case", "chart", "comparison", "concept", "curve",
    "diagram", "envelope", "equipment", "example", "facilities", "facility",
    "field", "figure", "flow", "layout", "loads", "map", "matrix", "motions",
    "network", "photo", "platform", "process", "profile", "response", "schematic",
    "selection", "spectrum", "statistics", "structure", "types",
}

LECTURE_FIGURE_LOW_VALUE_TERMS = {
    "agenda", "introduction", "part", "summary", "thank", "title", "topics",
}


def _now_iso() -> str:
    """Return a UTC timestamp for generated artifacts."""
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat()


def _rel(path: Path, root: Path) -> str:
    """Return a POSIX relative path when possible."""
    try:
        return path.relative_to(root).as_posix()
    except ValueError:
        return path.as_posix()


def _is_excluded(path: Path, root: Path) -> bool:
    """Return true when *path* is inside a skipped dependency/build folder."""
    try:
        parts = path.relative_to(root).parts
    except ValueError:
        parts = path.parts
    return any(part.lower() in DEFAULT_EXCLUDED_PARTS for part in parts)


def _classify_extension(path: Path) -> str:
    """Classify a source file by extension."""
    ext = path.suffix.lower()
    if ext in IMAGE_EXTENSIONS:
        return "image"
    if ext in DOCUMENT_EXTENSIONS:
        return "document"
    if ext in SPREADSHEET_EXTENSIONS:
        return "spreadsheet"
    if ext in NOTEBOOK_EXTENSIONS:
        return "notebook"
    if ext in {".md", ".txt", ".rst"}:
        return "text"
    if ext in {".py", ".ps1", ".sh", ".bat", ".cmd"}:
        return "script"
    if ext in {".json", ".yaml", ".yml", ".xml", ".toml"}:
        return "metadata"
    return "other"


def _file_sha256(path: Path, hash_limit_mb: float) -> Optional[str]:
    """Return SHA-256 for files under *hash_limit_mb*, else ``None``."""
    try:
        if path.stat().st_size > hash_limit_mb * 1024 * 1024:
            return None
        digest = hashlib.sha256()
        with path.open("rb") as handle:
            for chunk in iter(lambda: handle.read(1024 * 1024), b""):
                digest.update(chunk)
        return digest.hexdigest()
    except OSError:
        return None


def _source_area(rel_path: str) -> str:
    """Return the top-level source area for a relative path."""
    first = rel_path.split("/", 1)[0].lower()
    if first in {"lectures", "exercises", "exams", "graphics"}:
        return first
    return "other"


def build_source_inventory(book_dir: Path | str, source_root: Path | str,
                           hash_limit_mb: float = 20.0) -> Dict:
    """Inventory a course/source folder and write manifest artifacts."""
    book_dir = Path(book_dir)
    source_root = Path(source_root)
    if not source_root.exists():
        raise FileNotFoundError(f"source root not found: {source_root}")

    files: List[Dict] = []
    skipped = 0
    for path in sorted(source_root.rglob("*")):
        if not path.is_file():
            continue
        if _is_excluded(path, source_root):
            skipped += 1
            continue
        rel_path = _rel(path, source_root)
        stat = path.stat()
        sha = _file_sha256(path, hash_limit_mb)
        files.append({
            "rel_path": rel_path,
            "name": path.name,
            "extension": path.suffix.lower() or "(none)",
            "category": _classify_extension(path),
            "area": _source_area(rel_path),
            "size_bytes": stat.st_size,
            "mtime_utc": datetime.fromtimestamp(stat.st_mtime, timezone.utc)
            .replace(microsecond=0).isoformat(),
            "sha256": sha,
            "hashed": sha is not None,
        })

    by_area = Counter(item["area"] for item in files)
    by_extension = Counter(item["extension"] for item in files)
    by_category = Counter(item["category"] for item in files)
    digest_to_paths: Dict[str, List[str]] = defaultdict(list)
    for item in files:
        if item.get("sha256"):
            digest_to_paths[item["sha256"]].append(item["rel_path"])
    duplicates = [paths for paths in digest_to_paths.values() if len(paths) > 1]

    manifest = {
        "generated_at": _now_iso(),
        "book_dir": str(book_dir),
        "source_root": str(source_root),
        "hash_limit_mb": hash_limit_mb,
        "total_files": len(files),
        "skipped_files": skipped,
        "summary": {
            "by_area": dict(sorted(by_area.items())),
            "by_category": dict(sorted(by_category.items())),
            "by_extension": dict(by_extension.most_common()),
            "duplicate_groups": len(duplicates),
        },
        "duplicates": duplicates[:200],
        "files": files,
    }

    (book_dir / "source_manifest.json").write_text(
        json.dumps(manifest, indent=2, ensure_ascii=False), encoding="utf-8")
    (book_dir / "source_manifest.md").write_text(
        _format_source_manifest_md(manifest), encoding="utf-8")
    return manifest


def _format_source_manifest_md(manifest: Dict) -> str:
    """Render source manifest summary as Markdown."""
    lines = [
        "# Source Manifest",
        "",
        f"Generated: {manifest['generated_at']}",
        f"Source root: `{manifest['source_root']}`",
        "",
        "## Summary",
        "",
        f"- Total files: {manifest['total_files']}",
        f"- Skipped dependency/cache files: {manifest['skipped_files']}",
        f"- Duplicate hash groups: {manifest['summary']['duplicate_groups']}",
        "",
        "### By Area",
        "",
        "| Area | Files |",
        "|------|------:|",
    ]
    for area, count in manifest["summary"]["by_area"].items():
        lines.append(f"| {area} | {count} |")
    lines.extend(["", "### By Category", "", "| Category | Files |", "|----------|------:|"])
    for category, count in manifest["summary"]["by_category"].items():
        lines.append(f"| {category} | {count} |")
    lines.extend(["", "### Top Extensions", "", "| Extension | Files |", "|-----------|------:|"])
    for ext, count in list(manifest["summary"]["by_extension"].items())[:25]:
        lines.append(f"| {ext} | {count} |")
    if manifest["duplicates"]:
        lines.extend(["", "## Duplicate Groups", ""])
        for idx, paths in enumerate(manifest["duplicates"][:20], 1):
            lines.append(f"{idx}. " + "; ".join(f"`{p}`" for p in paths[:5]))
    return "\n".join(lines) + "\n"


def _chapter_texts(book_dir: Path) -> Iterable[Tuple[int, Dict, Path, str]]:
    """Yield chapter metadata and Markdown text."""
    cfg = book_builder.load_book_config(book_dir)
    for ch_num, chapter, _part_title in book_builder.iter_chapters(cfg):
        chapter_dir = book_builder.resolve_chapter_dir(book_dir, chapter)
        chapter_md = chapter_dir / "chapter.md"
        if chapter_md.exists():
            yield ch_num, chapter, chapter_dir, chapter_md.read_text(encoding="utf-8")


def _section_for_position(text: str, pos: int) -> str:
    """Return the nearest preceding H2/H3 title for a character position."""
    current = ""
    for match in HEADING_RE.finditer(text):
        if match.start() > pos:
            break
        current = match.group(2).strip()
    return current


def _line_number(text: str, pos: int) -> int:
    """Return one-based line number for a character position."""
    return text.count("\n", 0, pos) + 1


def _has_nearby_discussion(text: str, end_pos: int) -> bool:
    """Return true when a figure is followed by a discussion block nearby."""
    next_heading = re.search(r"\n##\s+", text[end_pos:])
    next_figure = re.search(r"\n!\[", text[end_pos:])
    stop = len(text)
    for match in (next_heading, next_figure):
        if match:
            stop = min(stop, end_pos + match.start())
    window = text[end_pos:min(stop, end_pos + 2500)]
    return bool(DISCUSSION_RE.search(window))


def _is_cover_or_decorative(target: str, caption: str, line_number: int) -> bool:
    """Return true for cover/decorative figures exempt from discussion blocks."""
    lower = f"{target} {caption}".lower()
    if "lectures/covers" in lower or "cover illustration" in lower:
        return True
    # The first chapter image is usually a course-cover visual when sourced from lectures.
    if line_number <= 25 and "../../figures/lectures" in target:
        return True
    return False


def _visual_type(target: str, caption: str) -> str:
    """Heuristically classify a figure by filename and caption."""
    text = f"{Path(target).stem} {caption}".lower()
    if any(token in text for token in ["flow_pattern", "flow pattern", "regime map"]):
        return "flow map"
    if any(token in text for token in ["pfd", "flowsheet", "contactor", "separator", "column"]):
        return "process diagram"
    if any(token in text for token in ["layout", "map", "route"]):
        return "layout/map"
    if any(token in text for token in ["matrix", "decision", "screen", "tree"]):
        return "decision matrix"
    if any(token in text for token in ["profile", "curve", "histogram", "tornado", "envelope", "npv", "ipr", "vlp", "pressure", "temperature"]):
        return "plot"
    if "cover" in text:
        return "cover/decorative art"
    return "unknown"


def _caption_quality(caption: str) -> str:
    """Return a coarse caption quality label."""
    clean = caption.strip()
    if len(clean) < 28:
        return "weak"
    if re.search(r"\bS\d{2}\b|[_-]|\bFig(?:ure)?\s*\d+(?:\.\d+)?\s*[:\-]\s*[A-Za-z0-9 ]{1,20}$", clean):
        return "review"
    return "ok"


def _load_curated_source_lookup(book_dir: Path) -> Dict[str, Dict]:
    """Load curated lecture-figure source metadata when present."""
    manifest_path = book_dir / "_curated_figures_manifest.json"
    if not manifest_path.exists():
        return {}
    try:
        data = json.loads(manifest_path.read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        return {}
    lookup = {}
    for item in data:
        rel = item.get("rel", "")
        if rel:
            lookup[Path(rel).name] = item
    return lookup


def build_figure_dossier(book_dir: Path | str, source_root: Optional[Path | str] = None,
                         use_vision: bool = False, provider: str = "openai",
                         model: str = "gpt-4o") -> Dict:
    """Build a book-level figure dossier and contact sheet."""
    book_dir = Path(book_dir)
    source_lookup = _load_curated_source_lookup(book_dir)
    records: List[Dict] = []

    # Optional vision/context generation is scoped to each local chapter figures dir.
    vision_by_chapter: Dict[str, Dict[str, Dict]] = {}
    if use_vision:
        try:
            from result_evaluator import generate_figure_context
            for _ch_num, chapter, chapter_dir, _text in _chapter_texts(book_dir):
                contexts = generate_figure_context(chapter_dir, provider=provider,
                                                   model=model, use_vision=True)
                vision_by_chapter[chapter["dir"]] = {
                    ctx.figure_name: {
                        "generated_caption": ctx.generated_caption,
                        "observation": ctx.observation,
                        "mechanism": ctx.mechanism,
                        "implication": ctx.implication,
                        "recommendation": ctx.recommendation,
                        "linked_claims": ctx.linked_claims,
                        "linked_results": ctx.linked_results,
                    }
                    for ctx in contexts
                }
        except Exception as exc:  # pragma: no cover - depends on optional LLM deps
            vision_by_chapter["_error"] = {"message": str(exc)}

    for ch_num, chapter, chapter_dir, text in _chapter_texts(book_dir):
        for match in FIGURE_RE.finditer(text):
            caption = match.group(1).strip()
            target = match.group(2).strip()
            line = _line_number(text, match.start())
            section = _section_for_position(text, match.start())
            candidate_path = (chapter_dir / target).resolve()
            exists = candidate_path.exists()
            file_name = Path(target).name
            source_meta = source_lookup.get(file_name, {})
            exempt = _is_cover_or_decorative(target, caption, line)
            nearby_discussion = _has_nearby_discussion(text, match.end())
            vision = vision_by_chapter.get(chapter["dir"], {}).get(file_name, {})
            record = {
                "figure": file_name,
                "target": target,
                "figure_path": _rel(candidate_path, book_dir) if exists else target,
                "exists": exists,
                "chapter": chapter["dir"],
                "chapter_number": ch_num,
                "line": line,
                "section": section,
                "visual_type": _visual_type(target, caption),
                "caption": caption,
                "caption_quality": _caption_quality(caption),
                "discussion_present": nearby_discussion,
                "exempt": exempt,
                "source": "lecture" if "../../figures/lectures" in target else "chapter-local",
                "source_file": source_meta.get("src", ""),
                "lecture": source_meta.get("lecture", ""),
                "generated_caption": vision.get("generated_caption", ""),
                "observation": vision.get("observation", ""),
                "mechanism": vision.get("mechanism", ""),
                "implication": vision.get("implication", ""),
                "recommendation": vision.get("recommendation", ""),
                "confidence": "medium" if exists else "low",
                "review_status": "exempt" if exempt else ("approved" if nearby_discussion else "needs-human-check"),
            }
            records.append(record)

    dossier = {
        "generated_at": _now_iso(),
        "book_dir": str(book_dir),
        "source_root": str(source_root) if source_root else "",
        "figure_count": len(records),
        "summary": {
            "missing_files": sum(1 for record in records if not record["exists"]),
            "without_discussion": sum(1 for record in records if not record["discussion_present"] and not record["exempt"]),
            "weak_captions": sum(1 for record in records if record["caption_quality"] != "ok"),
            "exempt": sum(1 for record in records if record["exempt"]),
        },
        "figures": records,
    }
    (book_dir / "figure_dossier.json").write_text(
        json.dumps(dossier, indent=2, ensure_ascii=False), encoding="utf-8")
    (book_dir / "figure_dossier.html").write_text(
        _format_figure_dossier_html(dossier), encoding="utf-8")
    return dossier


def _format_figure_dossier_html(dossier: Dict) -> str:
    """Render a compact HTML review sheet for figure records."""
    rows = []
    for record in dossier["figures"]:
        status = record["review_status"]
        rows.append(
            "<tr>"
            f"<td>{html.escape(record['chapter'])}</td>"
            f"<td>{html.escape(record['section'])}</td>"
            f"<td>{html.escape(record['figure'])}</td>"
            f"<td>{html.escape(record['visual_type'])}</td>"
            f"<td>{html.escape(record['caption_quality'])}</td>"
            f"<td>{html.escape(status)}</td>"
            f"<td>{html.escape(record['caption'])}</td>"
            "</tr>"
        )
    return """<!doctype html>
<html lang=\"en\">
<head>
<meta charset=\"utf-8\">
<title>Figure Dossier</title>
<style>
body {{ font-family: Arial, sans-serif; margin: 2rem; }}
table {{ border-collapse: collapse; width: 100%; font-size: 0.9rem; }}
th, td {{ border: 1px solid #ccc; padding: 0.35rem; vertical-align: top; }}
th {{ background: #f3f3f3; }}
</style>
</head>
<body>
<h1>Figure Dossier</h1>
<p>Generated: {generated}</p>
<p>Figures: {count}; missing: {missing}; without discussion: {without_discussion}; weak captions: {weak}</p>
<table>
<thead><tr><th>Chapter</th><th>Section</th><th>Figure</th><th>Type</th><th>Caption</th><th>Status</th><th>Caption text</th></tr></thead>
<tbody>
{rows}
</tbody>
</table>
</body>
</html>
""".format(
        generated=html.escape(dossier["generated_at"]),
        count=dossier["figure_count"],
        missing=dossier["summary"]["missing_files"],
        without_discussion=dossier["summary"]["without_discussion"],
        weak=dossier["summary"]["weak_captions"],
        rows="\n".join(rows),
    )


def apply_figure_context(book_dir: Path | str, reviewed_only: bool = True,
                         dry_run: bool = False) -> Dict:
    """Insert dossier discussion blocks into chapters when approved."""
    book_dir = Path(book_dir)
    dossier_path = book_dir / "figure_dossier.json"
    if not dossier_path.exists():
        raise FileNotFoundError(f"figure dossier not found: {dossier_path}")
    dossier = json.loads(dossier_path.read_text(encoding="utf-8"))
    by_chapter: Dict[str, List[Dict]] = defaultdict(list)
    for record in dossier.get("figures", []):
        has_context = all(record.get(key) for key in (
            "observation", "mechanism", "implication", "recommendation"))
        approved = record.get("review_status") == "approved"
        if has_context and (approved or not reviewed_only):
            by_chapter[record["chapter"]].append(record)

    changed = []
    cfg = book_builder.load_book_config(book_dir)
    for _ch_num, chapter, _part in book_builder.iter_chapters(cfg):
        records = by_chapter.get(chapter["dir"], [])
        if not records:
            continue
        chapter_dir = book_builder.resolve_chapter_dir(book_dir, chapter)
        chapter_md = chapter_dir / "chapter.md"
        text = chapter_md.read_text(encoding="utf-8")
        new_text = text
        inserted = 0
        for record in records:
            if record.get("discussion_present"):
                continue
            needle = f"]({record['target']})"
            pos = new_text.find(needle)
            if pos < 0:
                continue
            end = pos + len(needle)
            block = _discussion_block(record)
            new_text = new_text[:end] + "\n\n" + block + new_text[end:]
            inserted += 1
        if inserted and not dry_run:
            chapter_md.write_text(new_text, encoding="utf-8")
        if inserted:
            changed.append({"chapter": chapter["dir"], "inserted": inserted})
    return {"dry_run": dry_run, "changed": changed, "inserted": sum(c["inserted"] for c in changed)}


def _discussion_block(record: Dict) -> str:
    """Render a Markdown discussion block from a dossier record."""
    return (
        f"**Discussion ({record['figure']}).**\n"
        f"*Observation.* {record['observation']}\n"
        f"*Mechanism.* {record['mechanism']}\n"
        f"*Implication.* {record['implication']}\n"
        f"*Recommendation.* {record['recommendation']}\n"
    )


def build_coverage_audit(book_dir: Path | str, source_root: Path | str) -> Dict:
    """Build a lightweight source-to-book coverage audit."""
    book_dir = Path(book_dir)
    source_root = Path(source_root)
    coverage_text = (book_dir / "coverage_matrix.md").read_text(encoding="utf-8") \
        if (book_dir / "coverage_matrix.md").exists() else ""
    cfg = book_builder.load_book_config(book_dir)
    chapter_dirs = [chapter["dir"] for _num, chapter, _part in book_builder.iter_chapters(cfg)]

    coverage_lectures = _extract_coverage_lectures(coverage_text)
    lecture_root = source_root / "lectures"
    lecture_rows = []
    if lecture_root.exists():
        for folder in sorted(path for path in lecture_root.iterdir() if path.is_dir()):
            match = _match_lecture_folder(folder.name, coverage_lectures)
            mapped = match is not None
            lecture_rows.append({
                "source": f"lectures/{folder.name}",
                "mapped": mapped,
                "status": "mapped" if mapped else "needs-review",
                "matched_date": match.get("date", "") if match else "",
                "matched_title": match.get("title", "") if match else "",
                "chapters": match.get("chapters", "") if match else "",
            })

    exercise_root = source_root / "exercises"
    exercise_count = sum(1 for _ in exercise_root.rglob("*.pdf")) if exercise_root.exists() else 0
    exam_root = source_root / "exams"
    exam_count = sum(1 for _ in exam_root.rglob("*.pdf")) if exam_root.exists() else 0

    audit = {
        "generated_at": _now_iso(),
        "book_dir": str(book_dir),
        "source_root": str(source_root),
        "chapter_count": len(chapter_dirs),
        "lecture_folders": lecture_rows,
        "unmapped_lecture_folders": [row for row in lecture_rows if not row["mapped"]],
        "exercise_pdf_count": exercise_count,
        "exam_pdf_count": exam_count,
    }
    (book_dir / "coverage_audit.md").write_text(
        _format_coverage_audit_md(audit), encoding="utf-8")
    return audit


def _extract_coverage_lectures(coverage_text: str) -> List[Dict[str, str]]:
    """Extract lecture-map rows from ``coverage_matrix.md``."""
    rows = []
    for line in coverage_text.splitlines():
        parts = [part.strip() for part in line.strip().strip("|").split("|")]
        if len(parts) != 3:
            continue
        date, title, chapters = parts
        if not re.match(r"\d{2}-\d{2}-\d{4}$", date):
            continue
        rows.append({"date": date, "title": title, "chapters": chapters})
    return rows


def _normalise_text(value: str) -> str:
    """Normalise text for coarse folder-title matching."""
    value = value.lower().replace("co₂", "co2")
    value = re.sub(r"[^a-z0-9]+", " ", value)
    return " ".join(value.split())


def _match_lecture_folder(folder_name: str, coverage_lectures: List[Dict[str, str]]) -> Optional[Dict[str, str]]:
    """Match a lecture source folder to a coverage-matrix lecture row."""
    normalised_folder = _normalise_text(folder_name)
    date_match = re.search(r"\d{2}[-.]\d{2}[-.]\d{4}", folder_name)
    folder_date = date_match.group(0).replace(".", "-") if date_match else ""
    folder_day_month = folder_date[:5] if folder_date else ""
    best = None
    best_score = 0.0
    for lecture in coverage_lectures:
        if folder_date and folder_date == lecture["date"]:
            return lecture
        if folder_day_month and folder_day_month == lecture["date"][:5]:
            best = lecture
            best_score = max(best_score, 0.8)
        title_norm = _normalise_text(lecture["title"])
        title_tokens = set(token for token in title_norm.split() if len(token) > 2)
        folder_tokens = set(normalised_folder.split())
        if not title_tokens:
            continue
        overlap = len(title_tokens & folder_tokens) / float(len(title_tokens))
        if title_norm and title_norm in normalised_folder:
            overlap = 1.0
        if overlap > best_score:
            best = lecture
            best_score = overlap
    if best_score >= 0.6:
        return best
    return None


def _format_coverage_audit_md(audit: Dict) -> str:
    """Render coverage audit as Markdown."""
    lines = [
        "# Coverage Audit",
        "",
        f"Generated: {audit['generated_at']}",
        f"Source root: `{audit['source_root']}`",
        "",
        f"- Chapter count: {audit['chapter_count']}",
        f"- Lecture folders: {len(audit['lecture_folders'])}",
        f"- Unmapped lecture folders: {len(audit['unmapped_lecture_folders'])}",
        f"- Exercise PDFs: {audit['exercise_pdf_count']}",
        f"- Exam PDFs: {audit['exam_pdf_count']}",
        "",
        "## Lecture Folder Mapping",
        "",
        "| Source | Status | Match |",
        "|--------|--------|-------|",
    ]
    for row in audit["lecture_folders"]:
        detail = row.get("chapters", "") if row["mapped"] else ""
        if row.get("matched_title"):
            detail = f"{row['matched_title']} -> {row['chapters']}"
        lines.append(f"| `{row['source']}` | {row['status']} | {detail} |")
    if audit["unmapped_lecture_folders"]:
        lines.extend(["", "## Required Review", ""])
        for row in audit["unmapped_lecture_folders"]:
            lines.append(f"- `{row['source']}` is not explicitly listed in `coverage_matrix.md`.")
    return "\n".join(lines) + "\n"


def build_lecture_topic_coverage(book_dir: Path | str, apply_checkpoints: bool = False,
                                 max_topics_per_deck: int = 120) -> Dict:
    """Build a slide-topic coverage report from ``_lecture_topic_manifest.json``.

    When ``apply_checkpoints`` is true, concise lecture-coverage checkpoint blocks
    are inserted into the mapped chapters before coverage is scored. This makes
    the book carry the lecture provenance explicitly without expanding full slide
    text into the main chapter narrative.
    """
    book_dir = Path(book_dir)
    manifest = _load_lecture_topic_manifest(book_dir)
    chapters = _collect_chapter_records(book_dir)
    chapter_lookup = _chapter_lookup(chapters)
    grouped_decks = _group_lecture_decks_by_chapter(manifest, chapter_lookup)

    if apply_checkpoints:
        _apply_lecture_coverage_checkpoints(book_dir, chapters, grouped_decks,
                                            max_topics_per_deck=max_topics_per_deck)
        chapters = _collect_chapter_records(book_dir)
        chapter_lookup = _chapter_lookup(chapters)

    deck_rows = []
    total_topics = 0
    covered_topics = 0
    for raw_chapter, decks in grouped_decks.items():
        chapter = chapter_lookup.get(raw_chapter)
        chapter_text = chapter["plain_text"] if chapter else ""
        for deck in decks:
            topic_rows = []
            for slide in deck.get("slides", []):
                topic = _slide_topic(slide)
                if not topic:
                    continue
                keywords = _topic_keywords(topic + " " + " ".join(slide.get("body", [])))
                score = _keyword_coverage_score(keywords, chapter_text)
                is_covered = score >= 0.45 or _normalise_text(topic) in _normalise_text(chapter_text)
                total_topics += 1
                covered_topics += 1 if is_covered else 0
                topic_rows.append({
                    "slide": slide.get("idx", 0),
                    "topic": topic,
                    "keywords": keywords,
                    "coverage_score": round(score, 3),
                    "status": "covered" if is_covered else "needs-review",
                })
            deck_rows.append({
                "lecture": deck.get("lecture", ""),
                "chapter": raw_chapter,
                "pptx": deck.get("pptx", ""),
                "topics": topic_rows,
                "topic_count": len(topic_rows),
                "covered_topic_count": sum(1 for row in topic_rows if row["status"] == "covered"),
            })

    report = {
        "generated_at": _now_iso(),
        "book_dir": str(book_dir),
        "applied_checkpoints": apply_checkpoints,
        "summary": {
            "lecture_decks": len(deck_rows),
            "chapters_with_lecture_decks": len(grouped_decks),
            "topics": total_topics,
            "covered_topics": covered_topics,
            "topics_needing_review": total_topics - covered_topics,
            "coverage_pct": round((covered_topics / total_topics) * 100.0, 1) if total_topics else 100.0,
        },
        "decks": deck_rows,
    }
    (book_dir / "lecture_topic_coverage.json").write_text(
        json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")
    (book_dir / "lecture_topic_coverage.md").write_text(
        _format_lecture_topic_coverage_md(report), encoding="utf-8")
    _write_lecture_coverage_appendix(book_dir, report)
    return report


def build_lecture_figure_plan(book_dir: Path | str, max_slides_per_deck: int = 12) -> Dict:
    """Build a review plan for turning lecture slides into textbook figures.

    The plan is intentionally conservative: it does not edit chapters. It
    identifies slide renders with figure-like titles or body text, records
    whether the rendered PNG exists in ``_pptx_slides_cache``, and writes a
    Markdown checklist that a figure-context editor can review before copying
    the selected figures into a chapter.
    """
    book_dir = Path(book_dir)
    manifest = _load_lecture_topic_manifest(book_dir)
    cache_dir = book_dir / "_pptx_slides_cache"

    decks = []
    total_candidates = 0
    rendered_candidates = 0
    for deck in manifest:
        pptx_name = deck.get("pptx", "")
        pptx_stem = Path(pptx_name).stem if pptx_name else ""
        lecture = deck.get("lecture", "")
        candidates = []
        for slide in deck.get("slides", []):
            score, reasons = _score_lecture_figure_slide(slide)
            if score <= 0:
                continue
            slide_idx = int(slide.get("idx", 0) or 0)
            rendered_path = None
            rendered_rel = ""
            if lecture and pptx_stem and slide_idx > 0:
                rendered_path = cache_dir / lecture / f"{pptx_stem}__slide_{slide_idx:03d}.png"
                if rendered_path.exists():
                    rendered_rel = _rel(rendered_path, book_dir)
            topic = _slide_topic(slide)
            candidates.append({
                "slide": slide_idx,
                "topic": topic,
                "score": score,
                "reasons": reasons,
                "rendered": bool(rendered_rel),
                "rendered_rel": rendered_rel,
                "recommended_use": _recommended_lecture_figure_use(topic),
            })
        candidates.sort(key=lambda row: (-row["score"], row["slide"]))
        candidates = candidates[:max_slides_per_deck]
        total_candidates += len(candidates)
        rendered_candidates += sum(1 for row in candidates if row["rendered"])
        decks.append({
            "lecture": lecture,
            "chapter": deck.get("chapter", ""),
            "pptx": pptx_name,
            "candidate_count": len(candidates),
            "candidates": candidates,
        })

    report = {
        "generated_at": _now_iso(),
        "book_dir": str(book_dir),
        "max_slides_per_deck": max_slides_per_deck,
        "summary": {
            "lecture_decks": len(decks),
            "candidate_slides": total_candidates,
            "rendered_candidate_slides": rendered_candidates,
        },
        "decks": decks,
    }
    (book_dir / "lecture_figure_plan.json").write_text(
        json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")
    (book_dir / "lecture_figure_plan.md").write_text(
        _format_lecture_figure_plan_md(report), encoding="utf-8")
    return report


def _score_lecture_figure_slide(slide: Dict) -> Tuple[int, List[str]]:
    """Return a heuristic figure-review score and human-readable reasons."""
    topic = _slide_topic(slide)
    body = " ".join(slide.get("body", []))
    text = _normalise_text(topic + " " + body)
    words = set(text.split())
    signal_terms = sorted(words & LECTURE_FIGURE_SIGNAL_TERMS)
    low_value_terms = sorted(words & LECTURE_FIGURE_LOW_VALUE_TERMS)

    score = len(signal_terms) * 2
    reasons = []
    if signal_terms:
        reasons.append("figure-like terms: " + ", ".join(signal_terms[:6]))
    if re.search(r"\b(fig|figure|diagram|photo|illustration|source:)\b", text):
        score += 3
        reasons.append("explicit visual/source language")
    if re.search(r"\b(example|case|field|platform|process|loads|motions?)\b", text):
        score += 2
        reasons.append("case or engineering-decision content")
    if len(body.split()) >= 8:
        score += 1
        reasons.append("has explanatory body text")
    if low_value_terms:
        score -= len(low_value_terms) * 2
        reasons.append("lower-value terms: " + ", ".join(low_value_terms[:4]))
    if topic.lower().startswith(("part ", "topics", "summary", "introduction")):
        score -= 3
        reasons.append("likely divider or summary slide")
    return max(score, 0), reasons


def _recommended_lecture_figure_use(topic: str) -> str:
    """Return a concise suggested use for a lecture figure candidate."""
    text = topic.lower()
    if any(term in text for term in ("choose", "selection", "concept")):
        return "Use near the concept-selection workflow with decision criteria."
    if any(term in text for term in ("load", "metocean", "state", "response")):
        return "Use in the loads/design-method section with verification discussion."
    if any(term in text for term in ("motion", "period", "wave", "spectrum")):
        return "Use in the floater-dynamics section with mechanism and operability discussion."
    if any(term in text for term in ("field", "case", "example", "yggdrasil")):
        return "Use as a case example and connect it to area architecture or NCS practice."
    if any(term in text for term in ("process", "facility", "facilities", "equipment")):
        return "Use to connect facility layout, topside function and structure choice."
    return "Review visually and add observation, mechanism, implication and recommendation."


def _format_lecture_figure_plan_md(report: Dict) -> str:
    """Render the lecture-figure review plan as Markdown."""
    summary = report["summary"]
    lines = [
        "# Lecture Figure Review Plan",
        "",
        f"Generated: {report['generated_at']}",
        f"Book: `{report['book_dir']}`",
        "",
        f"- Lecture decks: {summary['lecture_decks']}",
        f"- Candidate slides: {summary['candidate_slides']}",
        f"- Candidate slides with rendered PNGs: {summary['rendered_candidate_slides']}",
        "",
        "Use this plan with the `technical_figure_understanding` skill. Review each",
        "candidate image before insertion, copy only high-value figures into the",
        "chapter-local `figures/` folder, and add a discussion block with Observation,",
        "Mechanism, Implication and Recommendation.",
        "",
    ]
    for deck in report["decks"]:
        if deck["candidate_count"] == 0:
            continue
        lines.extend([
            f"## {deck['lecture']} — {deck['pptx']}",
            "",
            f"Mapped chapter: `{deck['chapter']}`",
            "",
            "| Slide | Score | Rendered PNG | Topic | Suggested use |",
            "|-------|-------|--------------|-------|---------------|",
        ])
        for row in deck["candidates"]:
            rendered = f"`{row['rendered_rel']}`" if row["rendered_rel"] else "missing"
            topic = row["topic"].replace("|", "\\|")
            use = row["recommended_use"].replace("|", "\\|")
            lines.append(f"| {row['slide']} | {row['score']} | {rendered} | {topic} | {use} |")
        lines.append("")
    return "\n".join(lines) + "\n"


def _load_lecture_topic_manifest(book_dir: Path) -> List[Dict]:
    """Load the lecture topic manifest generated from lecture decks."""
    manifest_path = book_dir / "_lecture_topic_manifest.json"
    if not manifest_path.exists():
        raise FileNotFoundError(f"lecture topic manifest not found: {manifest_path}")
    data = json.loads(manifest_path.read_text(encoding="utf-8"))
    if not isinstance(data, list):
        raise ValueError("lecture topic manifest must be a list of lecture deck records")
    return data


def _chapter_lookup(chapters: List[Dict]) -> Dict[str, Dict]:
    """Return lookup keys for full chapter dir and short chNN aliases."""
    lookup = {}
    for chapter in chapters:
        lookup[chapter["dir"]] = chapter
        lookup[f"ch{chapter['number']:02d}"] = chapter
    return lookup


def _group_lecture_decks_by_chapter(manifest: List[Dict], chapter_lookup: Dict[str, Dict]) -> Dict[str, List[Dict]]:
    """Group lecture deck records by resolved chapter directory."""
    grouped: Dict[str, List[Dict]] = defaultdict(list)
    for deck in manifest:
        raw = deck.get("chapter", "")
        chapter = chapter_lookup.get(raw)
        if chapter is None and re.match(r"ch\d{2}$", raw):
            chapter = chapter_lookup.get(raw)
        if chapter is None:
            grouped[raw or "unmapped"].append(deck)
        else:
            grouped[chapter["dir"]].append(deck)
    return dict(sorted(grouped.items()))


def _slide_topic(slide: Dict) -> str:
    """Return the best topic label for a slide."""
    title = " ".join(str(slide.get("title", "")).split()).strip(" -|\t")
    if title and title.lower() not in {"untitled", "no title"}:
        return title
    body = [" ".join(str(item).split()) for item in slide.get("body", []) if str(item).strip()]
    return body[0] if body else ""


def _topic_keywords(text: str, limit: int = 8) -> List[str]:
    """Return stable content keywords for a lecture topic."""
    keywords = []
    seen = set()
    for token in _content_tokens(_normalise_text(text)):
        if token in seen:
            continue
        seen.add(token)
        keywords.append(token)
        if len(keywords) >= limit:
            break
    return keywords


def _keyword_coverage_score(keywords: List[str], chapter_text: str) -> float:
    """Return the fraction of topic keywords present in chapter text."""
    if not keywords:
        return 1.0
    chapter_tokens = set(_content_tokens(chapter_text))
    return len([keyword for keyword in keywords if keyword in chapter_tokens]) / float(len(keywords))


def _apply_lecture_coverage_checkpoints(book_dir: Path, chapters: List[Dict],
                                        grouped_decks: Dict[str, List[Dict]],
                                        max_topics_per_deck: int) -> None:
    """Insert or replace lecture-coverage checkpoint blocks in mapped chapters."""
    chapter_by_dir = {chapter["dir"]: chapter for chapter in chapters}
    for chapter_dir_name, decks in grouped_decks.items():
        chapter = chapter_by_dir.get(chapter_dir_name)
        if chapter is None:
            continue
        chapter_md = chapter["chapter_dir"] / "chapter.md"
        text = chapter_md.read_text(encoding="utf-8")
        block = _format_lecture_checkpoint_block(decks, max_topics_per_deck=max_topics_per_deck)
        start_marker = "<!-- LECTURE_COVERAGE_START -->"
        end_marker = "<!-- LECTURE_COVERAGE_END -->"
        start = text.find(start_marker)
        end = text.find(end_marker)
        if start >= 0 and end > start:
            end += len(end_marker)
            new_text = text[:start] + block + text[end:]
        else:
            lecture_topics_start = text.find("<!-- LECTURE_TOPICS_START -->")
            insertion = block + "\n"
            if lecture_topics_start >= 0:
                new_text = text[:lecture_topics_start] + insertion + text[lecture_topics_start:]
            else:
                new_text = text.rstrip() + "\n\n" + insertion
        chapter_md.write_text(new_text, encoding="utf-8")


def _format_lecture_checkpoint_block(decks: List[Dict], max_topics_per_deck: int) -> str:
    """Render a concise chapter-level lecture coverage checkpoint."""
    lines = [
        "<!-- LECTURE_COVERAGE_START -->",
        "## Lecture coverage checkpoint",
        "",
        "This checkpoint records the mapped lecture-deck topics covered by this chapter. It is intentionally concise: the chapter prose explains the engineering logic, while the checkpoint preserves source traceability back to the lecture material.",
        "",
    ]
    for deck in decks:
        title = deck.get("lecture", "Lecture")
        pptx = deck.get("pptx", "")
        lines.append(f"**{title} — {pptx}.**")
        lines.append("")
        topics = []
        for slide in deck.get("slides", []):
            topic = _slide_topic(slide)
            if not topic:
                continue
            keywords = _topic_keywords(topic + " " + " ".join(slide.get("body", [])), limit=5)
            suffix = f" Key terms: {', '.join(keywords)}." if keywords else ""
            topics.append(f"- Slide {slide.get('idx', 0)}: {topic}.{suffix}")
        for item in topics[:max_topics_per_deck]:
            lines.append(item)
        if len(topics) > max_topics_per_deck:
            lines.append(f"- Additional slide topics retained in `lecture_topic_coverage.json`: {len(topics) - max_topics_per_deck}.")
        lines.append("")
    lines.append("<!-- LECTURE_COVERAGE_END -->")
    return "\n".join(lines) + "\n"


def _format_lecture_topic_coverage_md(report: Dict) -> str:
    """Render lecture topic coverage as Markdown."""
    summary = report["summary"]
    lines = [
        "# Lecture Topic Coverage",
        "",
        f"Generated: {report['generated_at']}",
        "",
        "## Summary",
        "",
        f"- Lecture decks: {summary['lecture_decks']}",
        f"- Chapters with lecture decks: {summary['chapters_with_lecture_decks']}",
        f"- Slide topics: {summary['topics']}",
        f"- Covered slide topics: {summary['covered_topics']}",
        f"- Topics needing review: {summary['topics_needing_review']}",
        f"- Coverage: {summary['coverage_pct']}%",
        "",
        "## Deck Coverage",
        "",
        "| Chapter | Lecture | Deck | Covered | Needs review |",
        "|---------|---------|------|--------:|-------------:|",
    ]
    for deck in report["decks"]:
        needs_review = deck["topic_count"] - deck["covered_topic_count"]
        lines.append(
            f"| {deck['chapter']} | {deck['lecture']} | {deck['pptx']} | "
            f"{deck['covered_topic_count']}/{deck['topic_count']} | {needs_review} |")
    review_rows = [
        (deck, topic)
        for deck in report["decks"]
        for topic in deck["topics"]
        if topic["status"] != "covered"
    ]
    if review_rows:
        lines.extend(["", "## Topics Needing Review", ""])
        for deck, topic in review_rows[:200]:
            lines.append(
                f"- {deck['chapter']} / {deck['pptx']} slide {topic['slide']}: "
                f"{topic['topic']} (score {topic['coverage_score']})")
    return "\n".join(lines) + "\n"


def _write_lecture_coverage_appendix(book_dir: Path, report: Dict) -> None:
    """Write a concise backmatter appendix for lecture source traceability."""
    lines = [
        "# Lecture Coverage and Source Traceability",
        "",
        "This appendix records how the lecture decks are covered in the textbook. The detailed slide-topic checklist is generated in each mapped chapter under `Lecture coverage checkpoint`, while this appendix provides the course-level audit view.",
        "",
        "## Coverage Summary",
        "",
        f"- Lecture decks: {report['summary']['lecture_decks']}",
        f"- Slide topics: {report['summary']['topics']}",
        f"- Covered slide topics: {report['summary']['covered_topics']}",
        f"- Coverage: {report['summary']['coverage_pct']}%",
        "",
        "## Lecture Deck Map",
        "",
        "| Chapter | Lecture | Deck | Slide topics | Coverage |",
        "|---------|---------|------|-------------:|---------:|",
    ]
    for deck in report["decks"]:
        pct = round((deck["covered_topic_count"] / deck["topic_count"]) * 100.0, 1) if deck["topic_count"] else 100.0
        lines.append(
            f"| {deck['chapter']} | {deck['lecture']} | {deck['pptx']} | "
            f"{deck['topic_count']} | {pct}% |")
    appendix = book_dir / "backmatter" / "lecture_coverage.md"
    appendix.parent.mkdir(parents=True, exist_ok=True)
    appendix.write_text("\n".join(lines) + "\n", encoding="utf-8")


def build_evidence_report(book_dir: Path | str) -> Dict:
    """Build a release-gate evidence report for a PaperLab book."""
    book_dir = Path(book_dir)
    issues = []

    # Reuse book checker for existing structural gates.
    try:
        from book_checker import run_checks
        for issue in run_checks(book_dir):
            if issue["severity"] in {"error", "warning"}:
                issues.append({
                    "severity": issue["severity"],
                    "category": issue["check"],
                    "message": issue["message"],
                })
    except Exception as exc:
        issues.append({"severity": "error", "category": "book_check", "message": str(exc)})

    figure_records = build_figure_dossier(book_dir).get("figures", [])
    for record in figure_records:
        location = f"{record['chapter']}:{record['line']}"
        if not record["exists"]:
            issues.append({
                "severity": "warning",
                "category": "figure_missing",
                "message": f"{location}: figure target not found: {record['target']}",
            })
        if not record["exempt"] and not record["discussion_present"]:
            issues.append({
                "severity": "warning",
                "category": "figure_discussion",
                "message": f"{location}: non-cover figure lacks nearby discussion: {record['figure']}",
            })
        if record["caption_quality"] != "ok":
            issues.append({
                "severity": "info",
                "category": "caption_quality",
                "message": f"{location}: caption should be reviewed: {record['caption']}",
            })

    severity_counts = Counter(issue["severity"] for issue in issues)
    report = {
        "generated_at": _now_iso(),
        "book_dir": str(book_dir),
        "summary": dict(severity_counts),
        "issue_count": len(issues),
        "issues": issues,
    }
    (book_dir / "evidence_report.json").write_text(
        json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")
    (book_dir / "evidence_report.md").write_text(
        _format_evidence_report_md(report), encoding="utf-8")
    return report


def _format_evidence_report_md(report: Dict) -> str:
    """Render evidence report as Markdown."""
    lines = [
        "# Evidence Report",
        "",
        f"Generated: {report['generated_at']}",
        "",
        "## Summary",
        "",
        f"- Issues: {report['issue_count']}",
    ]
    for severity, count in sorted(report["summary"].items()):
        lines.append(f"- {severity}: {count}")
    lines.extend(["", "## Issues", ""])
    if not report["issues"]:
        lines.append("No evidence issues found.")
    else:
        for issue in report["issues"]:
            lines.append(f"- **{issue['severity']} / {issue['category']}**: {issue['message']}")
    return "\n".join(lines) + "\n"


CHAPTER_SKILL_RULES = [
    (range(1, 3), ["neqsim-field-development", "neqsim-capability-map"],
     "Link reservoir, wells, facilities, exports, economics, regulation, and operations."),
    (range(3, 5), ["neqsim-api-patterns", "neqsim-eos-regression",
                   "neqsim-physics-explanations", "figure-discussion"],
     "Tie PVT and flow-performance figures to equations, operating points, and notebooks."),
    (range(5, 11), ["neqsim-platform-modeling", "neqsim-flow-assurance",
                    "neqsim-distillation-design", "neqsim-electrolyte-systems",
                    "neqsim-controllability-operability"],
     "Strengthen process-train logic, flow assurance, dehydration, and operability checks."),
    (range(11, 15), ["neqsim-subsea-and-wells", "neqsim-power-generation",
                     "neqsim-utilities-specification", "neqsim-standards-lookup",
                     "neqsim-equipment-cost-estimation"],
     "Add standards-backed equipment checks, electrification context, and cost drivers."),
    (range(15, 21), ["neqsim-model-calibration-and-data-reconciliation",
                     "neqsim-production-optimization", "neqsim-optimization-and-doe",
                     "neqsim-field-economics"],
     "Add uncertainty workflows, ensemble updating, optimization, and economic framing."),
    (range(21, 22), ["neqsim-standards-lookup", "neqsim-process-safety"],
     "Build a standards-to-chapter table with NCS regulatory traceability."),
    (range(22, 24), ["neqsim-ccs-hydrogen", "neqsim-technical-document-reading",
                     "neqsim-standards-lookup"],
     "Deepen gas quality, CO2 impurity, injection, line-pack, and hydrogen sections."),
    (range(24, 27), ["neqsim_in_writing", "neqsim-professional-reporting",
                     "technical_figure_understanding", "paperlab-exam-alignment"],
     "Make computational workflow auditable and align review questions with exams."),
]

STANDARD_RULES = [
    ("separator", ["NORSOK P-001", "API 12J", "ASME VIII"], "Separator sizing and vessel design"),
    ("dehydration", ["ISO 18453", "NORSOK P-001", "API 12J"],
     "Water-dew-point and dehydration design context"),
    ("flow assurance", ["DNV-RP-F110", "NORSOK P-001", "ISO 13623"],
     "Hydrate, wax, corrosion, slugging, and pipeline operability"),
    ("subsea", ["DNV-ST-F101", "ISO 13628", "NORSOK U-001"],
     "Subsea production system and SURF design"),
    ("well", ["NORSOK D-010", "API 5CT", "API TR 5C3", "ISO 11960"],
     "Well integrity, casing, tubing, and barriers"),
    ("co2", ["ISO 27913", "DNV-ST-F101", "NORSOK D-010"],
     "CO2 transport, storage, and injection integrity"),
    ("power", ["IEC 61892", "NORSOK E-001", "NORSOK S-001"],
     "Offshore power generation, electrification, and electrical systems"),
    ("safety", ["ISO 31000", "IEC 61511", "NORSOK Z-013"],
     "Risk, safety barriers, SIL, and decision governance"),
]

EXAM_TOPIC_RULES = [
    ("field-development-framing", ["concept", "pdo", "value chain", "development"]),
    ("pvt-and-flow-performance", ["pvt", "ipr", "vlp", "fluid", "reservoir"]),
    ("processing-and-separation", ["separator", "processing", "dehydration", "acid gas"]),
    ("subsea-wells-and-surf", ["subsea", "well", "surf", "drilling"]),
    ("economics-and-scheduling", ["npv", "irr", "cost", "schedule", "cash flow"]),
    ("optimization-and-uncertainty", ["optimization", "optimisation", "uncertainty", "monte carlo"]),
    ("regulation-and-standards", ["regulation", "norsok", "dnv", "api", "standard"]),
    ("ccs-and-gas-quality", ["co2", "ccs", "gas quality", "wobbe", "hydrogen"]),
]


def build_skill_stack_plan(book_dir: Path | str) -> Dict:
    """Build a systematic skill-stack adoption plan for a book."""
    book_dir = Path(book_dir)
    chapters = _collect_chapter_records(book_dir)
    figure_dossier = _load_json(book_dir / "figure_dossier.json") or build_figure_dossier(book_dir)
    standards_map = _load_json(book_dir / "standards_map.json")
    exam_alignment = _load_json(book_dir / "exam_alignment.json")
    source_plan = _load_json(book_dir / "source_pdf_html_plan.json")

    claim_markers = sum(chapter["text"].count("@neqsim:claim") for chapter in chapters)
    figure_markers = sum(chapter["text"].count("@neqsim:figure") for chapter in chapters)
    eq_markers = sum(chapter["text"].count("@neqsim:eq") for chapter in chapters)
    notebook_count = sum(len(list(chapter["chapter_dir"].rglob("*.ipynb"))) for chapter in chapters)
    figure_summary = figure_dossier.get("summary", {})

    dimensions = [
        _dimension_record(
            "figure_intelligence",
            ["technical_figure_understanding", "figure-discussion", "generate_publication_figures"],
            figure_summary.get("without_discussion", 0) == 0 and figure_summary.get("weak_captions", 0) == 0,
            f"{figure_summary.get('without_discussion', 0)} figures lack nearby discussion; "
            f"{figure_summary.get('weak_captions', 0)} captions need review.",
            "Prioritize the top non-cover figures in figure_dossier.json for reviewed discussion blocks."),
        _dimension_record(
            "traceability",
            ["neqsim-professional-reporting", "neqsim_in_writing", "neqsim-notebook-patterns"],
            claim_markers > 0 and figure_markers > 0,
            f"Found {claim_markers} claim markers, {figure_markers} figure markers, "
            f"{eq_markers} equation markers, and {notebook_count} notebooks.",
            "Add @neqsim:claim, @neqsim:figure, and @neqsim:eq markers for high-value numerical claims."),
        _dimension_record(
            "standards_mapping",
            ["neqsim-standards-lookup", "neqsim-process-safety", "neqsim-subsea-and-wells"],
            bool(standards_map),
            _artifact_status(standards_map, "standards_map.json"),
            "Run book-standards-map and review chapter-level NORSOK/DNV/API/ISO mappings."),
        _dimension_record(
            "notebook_backed_claims",
            ["neqsim-notebook-patterns", "neqsim-api-patterns", "neqsim-regression-baselines"],
            notebook_count == 0 or claim_markers > 0,
            f"{notebook_count} notebooks are available; {claim_markers} notebook-backed claim markers were found.",
            "Use claim markers for the most important notebook-derived figures and tables."),
        _dimension_record(
            "exam_alignment",
            ["paperlab-exam-alignment", "technical_figure_understanding"],
            bool(exam_alignment),
            _artifact_status(exam_alignment, "exam_alignment.json"),
            "Run book-exam-alignment and patch weakly covered exam topics in Chapter 26."),
        _dimension_record(
            "source_conversion",
            ["paperlab-source-pdf-to-html", "neqsim-pdf-ocr", "neqsim-technical-document-reading"],
            bool(source_plan),
            _artifact_status(source_plan, "source_pdf_html_plan.json"),
            "Run book-source-pdf-html-plan for searchable source conversion and figure extraction."),
    ]

    chapter_profiles = [_chapter_skill_profile(chapter) for chapter in chapters]
    plan = {
        "generated_at": _now_iso(),
        "book_dir": str(book_dir),
        "summary": {
            "chapters": len(chapters),
            "notebooks": notebook_count,
            "figures": figure_dossier.get("figure_count", 0),
            "dimensions_ready": sum(1 for item in dimensions if item["status"] == "ready"),
            "dimensions_total": len(dimensions),
        },
        "dimensions": dimensions,
        "chapter_profiles": chapter_profiles,
    }
    (book_dir / "skill_stack_plan.json").write_text(
        json.dumps(plan, indent=2, ensure_ascii=False), encoding="utf-8")
    (book_dir / "skill_stack_plan.md").write_text(
        _format_skill_stack_plan_md(plan), encoding="utf-8")
    return plan


def build_standards_map(book_dir: Path | str) -> Dict:
    """Build chapter-level standards mapping from chapter text and topic rules."""
    book_dir = Path(book_dir)
    chapters = _collect_chapter_records(book_dir)
    chapter_records = []
    all_standards: Counter = Counter()
    for chapter in chapters:
        text = f"{chapter['title']} {chapter['plain_text']}".lower()
        matches = []
        for keyword, standards, scope in STANDARD_RULES:
            if keyword in text or (keyword == "co2" and "co<sub>2</sub>" in text):
                for standard in standards:
                    all_standards[standard] += 1
                matches.append({
                    "keyword": keyword,
                    "scope": scope,
                    "standards": standards,
                    "status": "candidate",
                    "action": "Confirm exact clause references before using as compliance evidence.",
                })
        chapter_records.append({
            "chapter": chapter["dir"],
            "chapter_number": chapter["number"],
            "title": chapter["title"],
            "matches": matches,
        })
    report = {
        "generated_at": _now_iso(),
        "book_dir": str(book_dir),
        "summary": {
            "chapters_with_standards": sum(1 for item in chapter_records if item["matches"]),
            "unique_standards": len(all_standards),
        },
        "standards": [
            {"code": code, "chapter_count": count}
            for code, count in all_standards.most_common()
        ],
        "chapters": chapter_records,
    }
    (book_dir / "standards_map.json").write_text(
        json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")
    (book_dir / "standards_map.md").write_text(
        _format_standards_map_md(report), encoding="utf-8")
    return report


def build_exam_alignment(book_dir: Path | str, source_root: Optional[Path | str] = None) -> Dict:
    """Build a lightweight exam and exercise alignment report."""
    book_dir = Path(book_dir)
    chapters = _collect_chapter_records(book_dir)
    source_files = _source_files_from_manifest_or_root(book_dir, source_root)
    exam_files = [item for item in source_files if item.get("area") == "exams"]
    exercise_files = [item for item in source_files if item.get("area") == "exercises"]
    topic_rows = []
    for topic, keywords in EXAM_TOPIC_RULES:
        matching_chapters = []
        exercise_chapters = []
        for chapter in chapters:
            haystack = f"{chapter['title']} {chapter['plain_text']}".lower()
            if any(keyword in haystack for keyword in keywords):
                matching_chapters.append(chapter["dir"])
                if re.search(r"^##+\s+(?:\d+(?:\.\d+)*\s+)?Exercises", chapter["text"], re.MULTILINE):
                    exercise_chapters.append(chapter["dir"])
        topic_rows.append({
            "topic": topic,
            "keywords": keywords,
            "chapters": matching_chapters,
            "chapters_with_exercises": exercise_chapters,
            "status": "covered" if matching_chapters and exercise_chapters else "needs-review",
        })
    report = {
        "generated_at": _now_iso(),
        "book_dir": str(book_dir),
        "source_root": str(source_root) if source_root else "",
        "summary": {
            "exam_source_files": len(exam_files),
            "exercise_source_files": len(exercise_files),
            "topics": len(topic_rows),
            "topics_needing_review": sum(1 for row in topic_rows if row["status"] != "covered"),
        },
        "exam_files": exam_files[:200],
        "exercise_files": exercise_files[:200],
        "topics": topic_rows,
    }
    (book_dir / "exam_alignment.json").write_text(
        json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")
    (book_dir / "exam_alignment.md").write_text(
        _format_exam_alignment_md(report), encoding="utf-8")
    return report


def build_source_pdf_html_plan(book_dir: Path | str,
                               source_root: Optional[Path | str] = None) -> Dict:
    """Build a conversion plan for making source PDFs searchable as HTML."""
    book_dir = Path(book_dir)
    source_files = _source_files_from_manifest_or_root(book_dir, source_root)
    pdf_files = [item for item in source_files if item.get("extension", "").lower() == ".pdf"]
    conversions = []
    for item in pdf_files:
        rel_path = item.get("rel_path", item.get("path", ""))
        pdf_path = Path(rel_path)
        area = item.get("area", _source_area(rel_path))
        html_name = pdf_path.with_suffix(".html").name
        output_rel = f"{area}/_HTML/{html_name}" if area else f"_HTML/{html_name}"
        conversions.append({
            "source": rel_path,
            "area": area,
            "category": item.get("category", "document"),
            "output_html": output_rel,
            "status": "planned",
            "notes": "Convert with paperlab-source-pdf-to-html; use OCR first if text extraction is poor.",
        })
    plan = {
        "generated_at": _now_iso(),
        "book_dir": str(book_dir),
        "source_root": str(source_root) if source_root else "",
        "summary": {
            "pdf_files": len(conversions),
            "areas": dict(Counter(item["area"] for item in conversions)),
        },
        "conversions": conversions,
    }
    (book_dir / "source_pdf_html_plan.json").write_text(
        json.dumps(plan, indent=2, ensure_ascii=False), encoding="utf-8")
    (book_dir / "source_pdf_html_plan.md").write_text(
        _format_source_pdf_html_plan_md(plan), encoding="utf-8")
    return plan


def build_book_knowledge_graph(book_dir: Path | str) -> Dict:
    """Build a book knowledge graph connecting chapters, skills, figures, and sources."""
    book_dir = Path(book_dir)
    chapters = _collect_chapter_records(book_dir)
    figure_dossier = _load_json(book_dir / "figure_dossier.json") or build_figure_dossier(book_dir)
    standards_map = _load_json(book_dir / "standards_map.json") or build_standards_map(book_dir)
    skill_plan = _load_json(book_dir / "skill_stack_plan.json") or build_skill_stack_plan(book_dir)
    source_manifest = _load_json(book_dir / "source_manifest.json") or {}

    nodes = [{"id": "book", "label": "Book", "type": "book"}]
    edges = []
    seen_nodes = {"book"}

    def add_node(node_id: str, label: str, node_type: str, **extra: str) -> None:
        if node_id in seen_nodes:
            return
        seen_nodes.add(node_id)
        node = {"id": node_id, "label": label, "type": node_type}
        node.update(extra)
        nodes.append(node)

    def add_edge(source: str, target: str, relation: str) -> None:
        edges.append({"source": source, "target": target, "relation": relation})

    for chapter in chapters:
        chapter_id = chapter["dir"]
        add_node(chapter_id, f"Ch. {chapter['number']}: {chapter['title']}", "chapter")
        add_edge("book", chapter_id, "contains")
        for skill in _skills_for_chapter(chapter["number"]):
            skill_id = f"skill:{skill}"
            add_node(skill_id, skill, "skill")
            add_edge(chapter_id, skill_id, "uses_skill")
        for notebook in sorted(chapter["chapter_dir"].rglob("*.ipynb")):
            nb_id = f"notebook:{_rel(notebook, book_dir)}"
            add_node(nb_id, notebook.name, "notebook", path=_rel(notebook, book_dir))
            add_edge(chapter_id, nb_id, "has_notebook")

    for record in figure_dossier.get("figures", []):
        figure_id = f"figure:{record.get('chapter', '')}:{record.get('figure', '')}"
        add_node(figure_id, record.get("figure", "figure"), "figure",
                 status=record.get("review_status", ""))
        if record.get("chapter"):
            add_edge(record["chapter"], figure_id, "has_figure")

    for chapter in standards_map.get("chapters", []):
        for match in chapter.get("matches", []):
            for standard in match.get("standards", []):
                standard_id = f"standard:{standard}"
                add_node(standard_id, standard, "standard")
                add_edge(chapter["chapter"], standard_id, "references_standard")

    for item in source_manifest.get("files", [])[:300]:
        area_id = f"source-area:{item.get('area', 'other')}"
        add_node(area_id, item.get("area", "other"), "source_area")
        add_edge("book", area_id, "has_source_area")

    graph = {
        "generated_at": _now_iso(),
        "book_dir": str(book_dir),
        "summary": {
            "nodes": len(nodes),
            "edges": len(edges),
            "skill_dimensions_ready": skill_plan.get("summary", {}).get("dimensions_ready", 0),
        },
        "nodes": nodes,
        "edges": edges,
    }
    (book_dir / "book_knowledge_graph.json").write_text(
        json.dumps(graph, indent=2, ensure_ascii=False), encoding="utf-8")
    (book_dir / "book_knowledge_graph.html").write_text(
        _format_book_knowledge_graph_html(graph), encoding="utf-8")
    return graph


def _dimension_record(dimension: str, skills: List[str], ready: bool, evidence: str,
                      next_action: str) -> Dict:
    """Return a skill-stack dimension record."""
    return {
        "dimension": dimension,
        "skills": skills,
        "status": "ready" if ready else "needs-work",
        "evidence": evidence,
        "next_action": next_action,
    }


def _artifact_status(data: Optional[Dict], artifact: str) -> str:
    """Return a short artifact status string."""
    if data:
        generated = data.get("generated_at", "unknown time")
        return f"{artifact} exists (generated {generated})."
    return f"{artifact} has not been generated yet."


def _load_json(path: Path) -> Optional[Dict]:
    """Load JSON from path when it exists and is valid."""
    if not path.exists():
        return None
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        return None


def _skills_for_chapter(chapter_number: int) -> List[str]:
    """Return recommended skills for a chapter number."""
    for chapter_range, skills, _action in CHAPTER_SKILL_RULES:
        if chapter_number in chapter_range:
            return skills
    return []


def _chapter_skill_profile(chapter: Dict) -> Dict:
    """Return a chapter-level skill profile."""
    skills = []
    action = "Review chapter against book-level skill-stack plan."
    for chapter_range, rule_skills, rule_action in CHAPTER_SKILL_RULES:
        if chapter["number"] in chapter_range:
            skills = rule_skills
            action = rule_action
            break
    return {
        "chapter": chapter["dir"],
        "chapter_number": chapter["number"],
        "title": chapter["title"],
        "skills": skills,
        "action": action,
        "notebooks": len(list(chapter["chapter_dir"].rglob("*.ipynb"))),
        "figures": len(chapter["figures"]),
        "claim_markers": chapter["text"].count("@neqsim:claim"),
        "figure_markers": chapter["text"].count("@neqsim:figure"),
    }


def _source_files_from_manifest_or_root(book_dir: Path,
                                        source_root: Optional[Path | str]) -> List[Dict]:
    """Return source-file records from source_manifest.json or a source root."""
    manifest = _load_json(book_dir / "source_manifest.json")
    if manifest and manifest.get("files"):
        return manifest["files"]
    if not source_root:
        return []
    root = Path(source_root)
    if not root.exists():
        return []
    records = []
    for path in sorted(root.rglob("*")):
        if not path.is_file() or _is_excluded(path, root):
            continue
        rel_path = _rel(path, root)
        records.append({
            "rel_path": rel_path,
            "name": path.name,
            "extension": path.suffix.lower(),
            "category": _classify_extension(path),
            "area": _source_area(rel_path),
        })
    return records


def _format_skill_stack_plan_md(plan: Dict) -> str:
    """Render a skill-stack plan as Markdown."""
    summary = plan["summary"]
    lines = [
        "# Skill Stack Plan",
        "",
        f"Generated: {plan['generated_at']}",
        "",
        "## Summary",
        "",
        f"- Chapters: {summary['chapters']}",
        f"- Figures: {summary['figures']}",
        f"- Notebooks: {summary['notebooks']}",
        f"- Ready dimensions: {summary['dimensions_ready']} / {summary['dimensions_total']}",
        "",
        "## Workflow Dimensions",
        "",
        "| Dimension | Status | Skills | Evidence | Next action |",
        "|-----------|--------|--------|----------|-------------|",
    ]
    for item in plan["dimensions"]:
        lines.append(
            f"| {item['dimension']} | {item['status']} | {', '.join(item['skills'])} | "
            f"{item['evidence']} | {item['next_action']} |")
    lines.extend(["", "## Chapter Skill Profiles", "", "| Chapter | Skills | Action |", "|---------|--------|--------|"])
    for chapter in plan["chapter_profiles"]:
        lines.append(
            f"| {chapter['chapter']} | {', '.join(chapter['skills'])} | {chapter['action']} |")
    return "\n".join(lines) + "\n"


def _format_standards_map_md(report: Dict) -> str:
    """Render standards map as Markdown."""
    lines = [
        "# Standards Map",
        "",
        f"Generated: {report['generated_at']}",
        "",
        "## Summary",
        "",
        f"- Chapters with candidate standards: {report['summary']['chapters_with_standards']}",
        f"- Unique standards: {report['summary']['unique_standards']}",
        "",
        "## Candidate Standards",
        "",
        "| Standard | Chapters |",
        "|----------|---------:|",
    ]
    for standard in report["standards"]:
        lines.append(f"| {standard['code']} | {standard['chapter_count']} |")
    lines.extend(["", "## Chapter Mapping", ""])
    for chapter in report["chapters"]:
        if not chapter["matches"]:
            continue
        lines.append(f"### Ch. {chapter['chapter_number']} {chapter['title']}")
        for match in chapter["matches"]:
            lines.append(
                f"- **{match['scope']}**: {', '.join(match['standards'])}. "
                f"Action: {match['action']}")
        lines.append("")
    return "\n".join(lines) + "\n"


def _format_exam_alignment_md(report: Dict) -> str:
    """Render exam alignment as Markdown."""
    summary = report["summary"]
    lines = [
        "# Exam Alignment",
        "",
        f"Generated: {report['generated_at']}",
        "",
        "## Summary",
        "",
        f"- Exam source files: {summary['exam_source_files']}",
        f"- Exercise source files: {summary['exercise_source_files']}",
        f"- Topics: {summary['topics']}",
        f"- Topics needing review: {summary['topics_needing_review']}",
        "",
        "## Topic Matrix",
        "",
        "| Topic | Status | Chapters | Chapters with exercises |",
        "|-------|--------|----------|-------------------------|",
    ]
    for row in report["topics"]:
        lines.append(
            f"| {row['topic']} | {row['status']} | {', '.join(row['chapters'])} | "
            f"{', '.join(row['chapters_with_exercises'])} |")
    lines.extend(["", "## Recommended Follow-up", ""])
    lines.append("- Add worked examples or review prompts for topics marked `needs-review`.")
    lines.append("- Use exam PDFs as source evidence only after text/OCR extraction quality is checked.")
    return "\n".join(lines) + "\n"


def _format_source_pdf_html_plan_md(plan: Dict) -> str:
    """Render source PDF-to-HTML plan as Markdown."""
    lines = [
        "# Source PDF-to-HTML Plan",
        "",
        f"Generated: {plan['generated_at']}",
        "",
        f"PDF files: {plan['summary']['pdf_files']}",
        "",
        "## By Area",
        "",
        "| Area | PDFs |",
        "|------|-----:|",
    ]
    for area, count in sorted(plan["summary"]["areas"].items()):
        lines.append(f"| {area} | {count} |")
    lines.extend(["", "## Conversion Items", "", "| Source | Output HTML | Notes |", "|--------|-------------|-------|"])
    for item in plan["conversions"][:200]:
        lines.append(f"| `{item['source']}` | `{item['output_html']}` | {item['notes']} |")
    return "\n".join(lines) + "\n"


def _format_book_knowledge_graph_html(graph: Dict) -> str:
    """Render a self-contained HTML knowledge graph review page."""
    data = html.escape(json.dumps(graph, ensure_ascii=False))
    node_rows = []
    for node in graph["nodes"][:500]:
        node_rows.append(
            f"<tr><td>{html.escape(node['id'])}</td><td>{html.escape(node['type'])}</td>"
            f"<td>{html.escape(node['label'])}</td></tr>")
    edge_rows = []
    for edge in graph["edges"][:800]:
        edge_rows.append(
            f"<tr><td>{html.escape(edge['source'])}</td><td>{html.escape(edge['relation'])}</td>"
            f"<td>{html.escape(edge['target'])}</td></tr>")
    return f"""<!doctype html>
<html lang=\"en\">
<head>
<meta charset=\"utf-8\">
<title>Book Knowledge Graph</title>
<style>
body {{ font-family: Arial, sans-serif; margin: 2rem; color: #222; }}
table {{ border-collapse: collapse; width: 100%; margin-bottom: 2rem; font-size: 0.9rem; }}
th, td {{ border: 1px solid #ddd; padding: 0.35rem; vertical-align: top; }}
th {{ background: #f4f4f4; }}
.summary {{ display: flex; gap: 1rem; margin: 1rem 0; }}
.summary div {{ border: 1px solid #ddd; padding: 0.75rem; min-width: 8rem; }}
</style>
</head>
<body>
<h1>Book Knowledge Graph</h1>
<p>Generated: {html.escape(graph['generated_at'])}</p>
<div class=\"summary\">
<div><strong>{graph['summary']['nodes']}</strong><br>nodes</div>
<div><strong>{graph['summary']['edges']}</strong><br>edges</div>
<div><strong>{graph['summary']['skill_dimensions_ready']}</strong><br>ready skill dimensions</div>
</div>
<h2>Nodes</h2>
<table><thead><tr><th>ID</th><th>Type</th><th>Label</th></tr></thead><tbody>
{''.join(node_rows)}
</tbody></table>
<h2>Edges</h2>
<table><thead><tr><th>Source</th><th>Relation</th><th>Target</th></tr></thead><tbody>
{''.join(edge_rows)}
</tbody></table>
<script type=\"application/json\" id=\"graph-data\">{data}</script>
</body>
</html>
"""


def build_conciseness_audit(book_dir: Path | str, min_words: int = 18,
                            paragraph_similarity_threshold: float = 0.82,
                            chapter_similarity_threshold: float = 0.44,
                            max_pairs: int = 80) -> Dict:
    """Detect repeated text, figures, headings, and possible chapter merges.

    The audit is intentionally conservative: it writes a restructuring plan for
    author review rather than rewriting chapters directly.
    """
    book_dir = Path(book_dir)
    chapters = _collect_chapter_records(book_dir)
    paragraphs = _collect_paragraph_records(chapters, min_words=min_words)
    exact_groups = _find_exact_duplicate_paragraphs(paragraphs)
    near_pairs = _find_near_duplicate_paragraphs(
        paragraphs, threshold=paragraph_similarity_threshold, max_pairs=max_pairs)
    duplicate_figures = _find_duplicate_figures(chapters, book_dir)
    repeated_headings = _find_repeated_headings(chapters)
    chapter_pairs = _rank_chapter_similarity(chapters)
    merge_candidates = _suggest_chapter_merges(chapter_pairs, near_pairs,
                                               threshold=chapter_similarity_threshold)
    estimated_words = _estimate_removable_words(exact_groups, near_pairs)

    audit = {
        "generated_at": _now_iso(),
        "book_dir": str(book_dir),
        "parameters": {
            "min_words": min_words,
            "paragraph_similarity_threshold": paragraph_similarity_threshold,
            "chapter_similarity_threshold": chapter_similarity_threshold,
            "max_pairs": max_pairs,
        },
        "summary": {
            "chapter_count": len(chapters),
            "word_count": sum(chapter["word_count"] for chapter in chapters),
            "paragraphs_analyzed": len(paragraphs),
            "exact_duplicate_paragraph_groups": len(exact_groups),
            "near_duplicate_paragraph_pairs": len(near_pairs),
            "duplicate_figure_groups": len(duplicate_figures),
            "repeated_heading_groups": len(repeated_headings),
            "chapter_merge_candidates": len(merge_candidates),
            "estimated_removable_words": estimated_words,
            "suggested_target_chapters": _suggested_target_chapter_count(
                len(chapters), merge_candidates),
        },
        "chapter_word_counts": [
            {"chapter": chapter["dir"], "title": chapter["title"], "words": chapter["word_count"]}
            for chapter in chapters
        ],
        "exact_duplicate_paragraphs": exact_groups,
        "near_duplicate_paragraphs": near_pairs,
        "duplicate_figures": duplicate_figures,
        "repeated_headings": repeated_headings,
        "chapter_similarity": chapter_pairs[:50],
        "chapter_merge_candidates": merge_candidates,
        "restructure_recommendations": _build_restructure_recommendations(
            exact_groups, near_pairs, duplicate_figures, repeated_headings, merge_candidates),
    }
    (book_dir / "conciseness_audit.json").write_text(
        json.dumps(audit, indent=2, ensure_ascii=False), encoding="utf-8")
    (book_dir / "conciseness_audit.md").write_text(
        _format_conciseness_audit_md(audit), encoding="utf-8")
    return audit


def apply_conciseness_edits(book_dir: Path | str, min_block_words: int = 250,
                            max_topics: int = 10, dry_run: bool = False) -> Dict:
    """Compress generated lecture-topic appendices into short summaries.

    The operation only edits blocks delimited by ``LECTURE_TOPICS_START`` and
    ``LECTURE_TOPICS_END``. These blocks are generated source traces, not core
    authored chapter text, which makes them a safe first conciseness target.
    """
    book_dir = Path(book_dir)
    changed = []
    total_before = 0
    total_after = 0
    for _ch_num, chapter, chapter_dir, text in _chapter_texts(book_dir):
        start = text.find("<!-- LECTURE_TOPICS_START -->")
        end = text.find("<!-- LECTURE_TOPICS_END -->")
        if start < 0 or end <= start:
            continue
        end += len("<!-- LECTURE_TOPICS_END -->")
        original_block = text[start:end]
        before_words = len(_strip_markdown_for_similarity(original_block).split())
        if before_words < min_block_words:
            continue
        topics = _extract_lecture_topics(original_block, max_topics=max_topics)
        new_block = _format_concise_lecture_topics_block(chapter["dir"], topics)
        after_words = len(_strip_markdown_for_similarity(new_block).split())
        total_before += before_words
        total_after += after_words
        changed.append({
            "chapter": chapter["dir"],
            "before_words": before_words,
            "after_words": after_words,
            "removed_words": max(0, before_words - after_words),
            "topics_retained": topics,
        })
        if not dry_run:
            chapter_md = chapter_dir / "chapter.md"
            chapter_md.write_text(text[:start] + new_block + text[end:], encoding="utf-8")
    return {
        "dry_run": dry_run,
        "chapters_changed": len(changed),
        "words_before": total_before,
        "words_after": total_after,
        "removed_words": max(0, total_before - total_after),
        "changed": changed,
    }


def _extract_lecture_topics(block: str, max_topics: int) -> List[str]:
    """Extract unique bold topic headings from a generated lecture block."""
    topics = []
    seen = set()
    for match in re.finditer(r"\*\*([^*]+)\*\*", block):
        topic = " ".join(match.group(1).strip().split())
        topic = topic.strip(" .:-|")
        if len(topic) < 4:
            continue
        key = _normalise_text(topic)
        if key in seen:
            continue
        seen.add(key)
        topics.append(topic)
        if len(topics) >= max_topics:
            break
    return topics


def _format_concise_lecture_topics_block(chapter_dir: str, topics: List[str]) -> str:
    """Render a compact replacement for a generated lecture-topic block."""
    lines = [
        "<!-- LECTURE_TOPICS_START -->",
        "## Further topics covered in the course",
        "",
        "This generated source appendix has been condensed to avoid repeating slide",
        "text that is already explained in the main chapter or in neighbouring",
        "chapters. Full provenance is retained in `coverage_matrix.md`,",
        "`source_manifest.json`, and `conciseness_audit.md`.",
        "",
    ]
    if topics:
        lines.append("Key source topics retained for traceability:")
        lines.append("")
        for topic in topics:
            lines.append(f"- {topic}.")
    else:
        lines.append("No distinct source-topic headings were detected in the generated block.")
    lines.extend([
        "",
        f"For repeated background material, use the canonical discussion in `{chapter_dir}`",
        "or the chapter cross-references instead of duplicating the source-slide text.",
        "<!-- LECTURE_TOPICS_END -->",
    ])
    return "\n".join(lines) + "\n"


def _collect_chapter_records(book_dir: Path) -> List[Dict]:
    """Collect chapter text, headings, figures, and word counts."""
    chapters = []
    for ch_num, chapter, chapter_dir, text in _chapter_texts(book_dir):
        title = chapter.get("title", chapter["dir"])
        plain = _strip_markdown_for_similarity(text)
        concise_source = _blank_generated_trace_blocks(text)
        concise_plain = _strip_markdown_for_similarity(concise_source)
        headings = [
            {"text": match.group(2).strip(), "line": _line_number(text, match.start())}
            for match in HEADING_RE.finditer(text)
        ]
        figures = [
            {
                "caption": match.group(1).strip(),
                "target": match.group(2).strip(),
                "line": _line_number(text, match.start()),
            }
            for match in FIGURE_RE.finditer(text)
        ]
        chapters.append({
            "number": ch_num,
            "dir": chapter["dir"],
            "title": title,
            "chapter_dir": chapter_dir,
            "text": text,
            "concise_text": concise_source,
            "plain_text": plain,
            "tokens": _content_tokens(concise_plain),
            "word_count": len(re.findall(r"\b\w+\b", concise_plain)),
            "headings": headings,
            "figures": figures,
        })
    return chapters


def _blank_generated_trace_blocks(text: str) -> str:
    """Blank generated lecture trace blocks while preserving line numbers."""
    def _replacement(match: re.Match) -> str:
        return "\n" * match.group(0).count("\n")

    return GENERATED_TRACE_BLOCK_RE.sub(_replacement, text)


def _strip_markdown_for_similarity(text: str) -> str:
    """Strip Markdown constructs that should not dominate text similarity."""
    text = re.sub(r"```.*?```", " ", text, flags=re.DOTALL)
    text = re.sub(r"!\[[^\]]*\]\([^)]+\)", " ", text)
    text = re.sub(r"\[([^\]]+)\]\([^)]+\)", r"\1", text)
    text = re.sub(r"\\cite\{[^}]+\}", " ", text)
    text = re.sub(r"`([^`]+)`", r"\1", text)
    text = re.sub(r"^#+\s+", "", text, flags=re.MULTILINE)
    text = re.sub(r"<[^>]+>", " ", text)
    return text


def _content_tokens(text: str) -> List[str]:
    """Return normalized content tokens for similarity checks."""
    tokens = re.findall(r"[a-z0-9]+", text.lower())
    return [token for token in tokens if len(token) > 2 and token not in STOPWORDS]


def _normalise_paragraph(text: str) -> str:
    """Normalize a paragraph for duplicate detection."""
    text = _strip_markdown_for_similarity(text)
    text = re.sub(r"[^a-z0-9]+", " ", text.lower())
    return " ".join(text.split())


def _collect_paragraph_records(chapters: List[Dict], min_words: int) -> List[Dict]:
    """Collect paragraph-level records above the minimum word count."""
    records = []
    for chapter in chapters:
        section = ""
        line_start = 1
        buffer: List[str] = []
        in_code = False
        lines = chapter.get("concise_text", chapter["text"]).splitlines()
        for idx, line in enumerate(lines + [""], 1):
            if line.strip().startswith("```"):
                in_code = not in_code
            heading = HEADING_RE.match(line)
            if heading:
                if buffer:
                    _append_paragraph_record(records, chapter, section, line_start,
                                             "\n".join(buffer), min_words)
                    buffer = []
                section = heading.group(2).strip()
                continue
            if in_code:
                continue
            if not line.strip():
                if buffer:
                    _append_paragraph_record(records, chapter, section, line_start,
                                             "\n".join(buffer), min_words)
                    buffer = []
                line_start = idx + 1
            else:
                if not buffer:
                    line_start = idx
                buffer.append(line)
    return records


def _append_paragraph_record(records: List[Dict], chapter: Dict, section: str,
                             line_start: int, text: str, min_words: int) -> None:
    """Append a paragraph record when it is substantive enough."""
    stripped = text.strip()
    if not stripped or stripped.startswith("|") or stripped.startswith("!["):
        return
    normalized = _normalise_paragraph(stripped)
    if any(marker in normalized for marker in CONCISENESS_BOILERPLATE_MARKERS):
        return
    tokens = _content_tokens(normalized)
    if len(tokens) < min_words:
        return
    records.append({
        "chapter": chapter["dir"],
        "chapter_number": chapter["number"],
        "section": section,
        "line": line_start,
        "word_count": len(tokens),
        "text": _shorten(stripped, 360),
        "normalized": normalized,
        "tokens": tokens,
        "token_set": set(tokens),
    })


def _shorten(text: str, limit: int) -> str:
    """Return a compact single-line text sample."""
    text = " ".join(text.split())
    return text if len(text) <= limit else text[:limit - 3].rstrip() + "..."


def _find_exact_duplicate_paragraphs(paragraphs: List[Dict]) -> List[Dict]:
    """Find exact duplicate normalized paragraphs."""
    groups: Dict[str, List[Dict]] = defaultdict(list)
    for record in paragraphs:
        groups[record["normalized"]].append(record)
    results = []
    for normalized, records in groups.items():
        chapters = {record["chapter"] for record in records}
        if len(records) > 1 and len(chapters) > 1:
            results.append({
                "fingerprint": hashlib.sha1(normalized.encode("utf-8")).hexdigest()[:12],
                "word_count": max(record["word_count"] for record in records),
                "occurrences": [_paragraph_location(record) for record in records],
                "sample": records[0]["text"],
            })
    return sorted(results, key=lambda item: item["word_count"] * len(item["occurrences"]), reverse=True)


def _paragraph_location(record: Dict) -> Dict:
    """Return public paragraph location metadata."""
    return {
        "chapter": record["chapter"],
        "line": record["line"],
        "section": record["section"],
    }


def _find_near_duplicate_paragraphs(paragraphs: List[Dict], threshold: float,
                                    max_pairs: int) -> List[Dict]:
    """Find near-duplicate paragraph pairs across chapters."""
    pairs = []
    for idx, left in enumerate(paragraphs):
        left_set = left["token_set"]
        if not left_set:
            continue
        for right in paragraphs[idx + 1:]:
            if left["chapter"] == right["chapter"]:
                continue
            right_set = right["token_set"]
            if not right_set:
                continue
            intersection = len(left_set & right_set)
            if intersection < min(len(left_set), len(right_set)) * 0.55:
                continue
            union = len(left_set | right_set)
            jaccard = intersection / float(union)
            containment = intersection / float(min(len(left_set), len(right_set)))
            score = max(jaccard, containment)
            if score >= threshold:
                pairs.append({
                    "score": round(score, 3),
                    "jaccard": round(jaccard, 3),
                    "containment": round(containment, 3),
                    "left": _paragraph_location(left),
                    "right": _paragraph_location(right),
                    "left_words": left["word_count"],
                    "right_words": right["word_count"],
                    "left_sample": left["text"],
                    "right_sample": right["text"],
                })
    pairs.sort(key=lambda item: (item["score"], min(item["left_words"], item["right_words"])),
               reverse=True)
    return pairs[:max_pairs]


def _find_duplicate_figures(chapters: List[Dict], book_dir: Path) -> List[Dict]:
    """Find figures reused by filename, target path, or file digest."""
    groups: Dict[str, List[Dict]] = defaultdict(list)
    for chapter in chapters:
        for figure in chapter["figures"]:
            target = figure["target"].split("#", 1)[0]
            figure_path = (chapter["chapter_dir"] / target).resolve()
            digest = _file_sha256(figure_path, 10.0) if figure_path.exists() else None
            keys = {f"name:{Path(target).name.lower()}", f"target:{target.lower()}"}
            if digest:
                keys.add(f"sha256:{digest}")
            for key in keys:
                groups[key].append({
                    "chapter": chapter["dir"],
                    "line": figure["line"],
                    "target": figure["target"],
                    "caption": figure["caption"],
                })
    results = []
    seen = set()
    for key, occurrences in groups.items():
        chapters_in_group = {item["chapter"] for item in occurrences}
        if len(occurrences) < 2 or len(chapters_in_group) < 2:
            continue
        signature = tuple(sorted((item["chapter"], item["line"], item["target"]) for item in occurrences))
        if signature in seen:
            continue
        seen.add(signature)
        results.append({
            "match_key": key,
            "occurrences": occurrences,
            "recommendation": "Keep one canonical figure and replace repeats with a cross-reference or a shorter summary.",
        })
    return sorted(results, key=lambda item: len(item["occurrences"]), reverse=True)


def _find_repeated_headings(chapters: List[Dict]) -> List[Dict]:
    """Find repeated section headings across chapters."""
    groups: Dict[str, List[Dict]] = defaultdict(list)
    for chapter in chapters:
        for heading in chapter["headings"]:
            normalized = _normalise_text(re.sub(r"^\d+(?:\.\d+)*\s+", "", heading["text"]))
            if len(normalized) < 6:
                continue
            groups[normalized].append({
                "chapter": chapter["dir"],
                "line": heading["line"],
                "heading": heading["text"],
            })
    results = []
    for normalized, occurrences in groups.items():
        if len(occurrences) > 1 and len({item["chapter"] for item in occurrences}) > 1:
            results.append({"heading_key": normalized, "occurrences": occurrences})
    return sorted(results, key=lambda item: len(item["occurrences"]), reverse=True)


def _rank_chapter_similarity(chapters: List[Dict]) -> List[Dict]:
    """Rank chapter pairs by content-token overlap."""
    pairs = []
    for idx, left in enumerate(chapters):
        left_set = set(left["tokens"])
        if not left_set:
            continue
        for right in chapters[idx + 1:]:
            right_set = set(right["tokens"])
            if not right_set:
                continue
            intersection = left_set & right_set
            union = left_set | right_set
            jaccard = len(intersection) / float(len(union))
            containment = len(intersection) / float(min(len(left_set), len(right_set)))
            overlap_terms = sorted(intersection, key=lambda token: left["tokens"].count(token) + right["tokens"].count(token),
                                   reverse=True)[:12]
            pairs.append({
                "left": left["dir"],
                "right": right["dir"],
                "left_number": left["number"],
                "right_number": right["number"],
                "left_title": left["title"],
                "right_title": right["title"],
                "adjacent": right["number"] == left["number"] + 1,
                "jaccard": round(jaccard, 3),
                "containment": round(containment, 3),
                "score": round(max(jaccard, containment), 3),
                "overlap_terms": overlap_terms,
            })
    pairs.sort(key=lambda item: item["score"], reverse=True)
    return pairs


def _suggest_chapter_merges(chapter_pairs: List[Dict], near_pairs: List[Dict],
                            threshold: float) -> List[Dict]:
    """Suggest adjacent chapter merges with supporting evidence."""
    duplicate_pair_counts: Counter = Counter()
    for pair in near_pairs:
        key = tuple(sorted([pair["left"]["chapter"], pair["right"]["chapter"]]))
        duplicate_pair_counts[key] += 1
    candidates = []
    for pair in chapter_pairs:
        if not pair["adjacent"]:
            continue
        key = tuple(sorted([pair["left"], pair["right"]]))
        duplicate_count = duplicate_pair_counts.get(key, 0)
        if pair["score"] >= threshold or duplicate_count >= 2:
            candidates.append({
                "chapters": [pair["left"], pair["right"]],
                "titles": [pair["left_title"], pair["right_title"]],
                "score": pair["score"],
                "near_duplicate_paragraph_pairs": duplicate_count,
                "overlap_terms": pair["overlap_terms"],
                "suggestion": "Review as a possible combined chapter or split into one core chapter plus a short case/application section.",
            })
    return candidates


def _suggested_target_chapter_count(chapter_count: int, merge_candidates: List[Dict]) -> int:
    """Return a conservative chapter-count target from merge candidates."""
    max_reduction = max(1, chapter_count // 4)
    reduction = min(len(merge_candidates), max_reduction)
    return max(1, chapter_count - reduction)


def _estimate_removable_words(exact_groups: List[Dict], near_pairs: List[Dict]) -> int:
    """Estimate removable words from duplicate text findings."""
    exact_words = sum(group["word_count"] * (len(group["occurrences"]) - 1)
                      for group in exact_groups)
    near_words = sum(min(pair["left_words"], pair["right_words"]) for pair in near_pairs)
    return int(exact_words + near_words * 0.5)


def _build_restructure_recommendations(exact_groups: List[Dict], near_pairs: List[Dict],
                                       duplicate_figures: List[Dict], repeated_headings: List[Dict],
                                       merge_candidates: List[Dict]) -> List[Dict]:
    """Build high-level restructuring recommendations from audit findings."""
    recommendations = []
    if exact_groups or near_pairs:
        recommendations.append({
            "priority": "high",
            "action": "Replace repeated explanatory text with one canonical explanation and cross-references.",
            "evidence": f"{len(exact_groups)} exact paragraph groups and {len(near_pairs)} near-duplicate paragraph pairs.",
        })
    if duplicate_figures:
        recommendations.append({
            "priority": "high",
            "action": "Keep duplicated figures in one canonical location; elsewhere reference the figure or use a one-sentence reminder.",
            "evidence": f"{len(duplicate_figures)} duplicate figure groups.",
        })
    if repeated_headings:
        recommendations.append({
            "priority": "medium",
            "action": "Review repeated headings and decide whether they are intentional recurring templates or merge candidates.",
            "evidence": f"{len(repeated_headings)} repeated heading groups.",
        })
    if merge_candidates:
        recommendations.append({
            "priority": "medium",
            "action": "Review adjacent chapter merge candidates before reducing chapter count.",
            "evidence": f"{len(merge_candidates)} adjacent chapter pairs exceed the similarity threshold or share duplicate text.",
        })
    recommendations.append({
        "priority": "workflow",
        "action": "Make rewriting a two-pass process: first generate a restructure plan, then have an editor compress chapters with citation and figure checks enabled.",
        "evidence": "Avoids deleting legitimate pedagogical repetition while still making large books shorter.",
    })
    return recommendations


def _format_conciseness_audit_md(audit: Dict) -> str:
    """Render the conciseness audit as Markdown."""
    summary = audit["summary"]
    lines = [
        "# Conciseness Audit",
        "",
        f"Generated: {audit['generated_at']}",
        "",
        "## Summary",
        "",
        f"- Chapters: {summary['chapter_count']}",
        f"- Suggested target chapters from merge candidates: {summary['suggested_target_chapters']}",
        f"- Words: {summary['word_count']:,}",
        f"- Paragraphs analyzed: {summary['paragraphs_analyzed']}",
        f"- Exact duplicate paragraph groups: {summary['exact_duplicate_paragraph_groups']}",
        f"- Near-duplicate paragraph pairs: {summary['near_duplicate_paragraph_pairs']}",
        f"- Duplicate figure groups: {summary['duplicate_figure_groups']}",
        f"- Repeated heading groups: {summary['repeated_heading_groups']}",
        f"- Adjacent chapter merge candidates: {summary['chapter_merge_candidates']}",
        f"- Estimated removable duplicate words: {summary['estimated_removable_words']:,}",
        "",
        "## Restructuring Recommendations",
        "",
    ]
    for rec in audit["restructure_recommendations"]:
        lines.append(f"- **{rec['priority']}**: {rec['action']} ({rec['evidence']})")
    lines.extend(["", "## Adjacent Chapter Merge Candidates", ""])
    if audit["chapter_merge_candidates"]:
        lines.extend(["| Chapters | Score | Shared terms | Suggestion |", "|----------|------:|--------------|------------|"])
        for candidate in audit["chapter_merge_candidates"]:
            chapters = " + ".join(candidate["chapters"])
            terms = ", ".join(candidate["overlap_terms"][:8])
            lines.append(f"| {chapters} | {candidate['score']:.3f} | {terms} | {candidate['suggestion']} |")
    else:
        lines.append("No adjacent chapter merge candidates exceeded the configured threshold.")
    lines.extend(["", "## Duplicate Figures", ""])
    if audit["duplicate_figures"]:
        for group in audit["duplicate_figures"][:20]:
            lines.append(f"- `{group['match_key']}` appears {len(group['occurrences'])} times.")
            for occ in group["occurrences"][:6]:
                lines.append(f"  - {occ['chapter']}:{occ['line']} -> `{occ['target']}`")
    else:
        lines.append("No duplicate figure groups found across chapters.")
    lines.extend(["", "## Near-Duplicate Paragraphs", ""])
    if audit["near_duplicate_paragraphs"]:
        for pair in audit["near_duplicate_paragraphs"][:20]:
            left = pair["left"]
            right = pair["right"]
            lines.append(
                f"- Score {pair['score']:.3f}: {left['chapter']}:{left['line']} "
                f"and {right['chapter']}:{right['line']}"
            )
            lines.append(f"  - Left: {pair['left_sample']}")
            lines.append(f"  - Right: {pair['right_sample']}")
    else:
        lines.append("No near-duplicate paragraph pairs exceeded the configured threshold.")
    lines.extend(["", "## Repeated Headings", ""])
    if audit["repeated_headings"]:
        for group in audit["repeated_headings"][:30]:
            locations = "; ".join(
                f"{item['chapter']}:{item['line']} ({item['heading']})"
                for item in group["occurrences"][:6])
            lines.append(f"- `{group['heading_key']}`: {locations}")
    else:
        lines.append("No repeated heading groups found across chapters.")
    return "\n".join(lines) + "\n"