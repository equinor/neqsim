"""Verify and publish executable code examples from book chapters.

The original mode extracts fenced `````python`` blocks from chapter markdown and
runs them in isolated subprocesses. The extended modes turn those same blocks
into one companion Jupyter notebook per chapter and can inject captured notebook
outputs back into ``chapter.md`` so the rendered book shows expected results.

Usage::

    python tools/verify_book_code_blocks.py books/<book_dir>
    python tools/verify_book_code_blocks.py books/<book_dir> --write-notebooks
    python tools/verify_book_code_blocks.py books/<book_dir> --write-notebooks --execute-notebooks --inject-results

A block is SKIPPED (not executed) if it contains an obvious snippet marker:
    - the literal "..." on its own line (continuation placeholder)
    - a leading "# pseudo" / "# snippet" / "# not runnable" comment
    - it is preceded by an HTML comment <!-- noexec --> on the previous line
"""
from __future__ import annotations

import argparse
import base64
import html
import json
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path
from typing import Any

FENCE_RE = re.compile(r"(?ms)^([ \t]*)```([A-Za-z0-9_+-]+)[ \t]*\n(.*?)^\1```",
                      re.MULTILINE)
NOEXEC_RE = re.compile(r"<!--\s*noexec\s*-->", re.IGNORECASE)
NOTEBOOK_RESULTS_START = "<!-- notebook-results:start -->"
NOTEBOOK_RESULTS_END = "<!-- notebook-results:end -->"
GENERATED_NOTEBOOK_NAME = "chapter_scripts.ipynb"


def _source_lines(text: str) -> list[str]:
    """Return notebook source lines with stable trailing newlines."""
    if not text:
        return [""]
    return text.splitlines(keepends=True)


def _join_output_text(value: Any) -> str:
    """Return notebook output text regardless of nbformat list/string shape."""
    if isinstance(value, list):
        return "".join(str(item) for item in value)
    return str(value or "")


def _chapter_filter_matches(path: Path, pattern: str | None) -> bool:
    """Return True when a chapter path matches the optional chapter pattern."""
    if not pattern:
        return True
    return any(Path(part).match(pattern) or part == pattern for part in path.parts)


def chapter_markdown_files(book_dir: Path, chapter: str | None = None) -> list[Path]:
    """Return ``chapter.md`` files for each chapter in a book."""
    chapters_dir = book_dir / "chapters"
    if not chapters_dir.is_dir():
        return []
    files = []
    for chapter_md in sorted(chapters_dir.glob("*/chapter.md")):
        if _chapter_filter_matches(chapter_md.parent, chapter):
            files.append(chapter_md)
    return files


def _first_heading(md_path: Path) -> str:
    """Read the first H1 heading from a chapter file."""
    text = md_path.read_text(encoding="utf-8", errors="replace")
    match = re.search(r"^#\s+(.+)$", text, flags=re.MULTILINE)
    return match.group(1).strip() if match else md_path.parent.name


def _cell(cell_type: str, source: str, language: str, metadata: dict[str, Any] | None = None) -> dict[str, Any]:
    """Build a notebook cell with the language metadata expected by VS Code."""
    meta = dict(metadata or {})
    meta.setdefault("language", language)
    item: dict[str, Any] = {
        "cell_type": cell_type,
        "metadata": meta,
        "source": _source_lines(source),
    }
    if cell_type == "code":
        item["outputs"] = []
        item["execution_count"] = None
    return item


_CAPTURE_SETUP = r'''
from pathlib import Path
import re
import shutil

try:
    from IPython.display import Image, display
except Exception:
    Image = None
    display = None

_CHAPTER_FIGURES_DIR = Path("../figures")
_CHAPTER_FIGURES_DIR.mkdir(parents=True, exist_ok=True)
_PAPERLAB_PATTERNS = ("*.png", "*.jpg", "*.jpeg", "*.svg", "*.webp", "*.csv", "*.json", "*.txt", "*.html")
_PAPERLAB_IMAGE_SUFFIXES = {".png", ".jpg", ".jpeg"}


def _paperlab_stamp(path):
    try:
        stat = path.stat()
        return (stat.st_mtime_ns, stat.st_size)
    except OSError:
        return None


def _paperlab_scan_files():
    files = {}
    roots = [Path("."), _CHAPTER_FIGURES_DIR]
    for root in roots:
        if not root.exists():
            continue
        for pattern in _PAPERLAB_PATTERNS:
            for path in root.glob(pattern):
                if path.is_file():
                    files[path.resolve()] = _paperlab_stamp(path)
    return files


def _paperlab_safe_name(name):
    cleaned = re.sub(r"[^A-Za-z0-9_.-]+", "_", name).strip("._")
    return cleaned or "notebook_output"


_paperlab_seen_files = _paperlab_scan_files()


def _paperlab_capture_new_files(label):
    global _paperlab_seen_files
    current = _paperlab_scan_files()
    changed = []
    for path, stamp in current.items():
        if _paperlab_seen_files.get(path) != stamp:
            changed.append(Path(path))
    _paperlab_seen_files = current

    for path in sorted(changed):
        suffix = path.suffix.lower()
        if suffix in _PAPERLAB_IMAGE_SUFFIXES:
            if path.parent.resolve() == _CHAPTER_FIGURES_DIR.resolve():
                dest = path
            else:
                dest = _CHAPTER_FIGURES_DIR / _paperlab_safe_name(f"{label}_{path.name}")
                shutil.copy2(path, dest)
            print(f"Captured figure: figures/{dest.name}")
            if Image is not None and display is not None:
                display(Image(filename=str(dest)))
        else:
            print(f"Generated result file: {path}")
'''.strip()


def extract_blocks(md_path: Path, lang: str):
    """Yield (start_line, code, skip_reason_or_None)."""
    text = md_path.read_text(encoding="utf-8", errors="replace")
    lines = text.splitlines()
    for m in FENCE_RE.finditer(text):
        if m.group(2).lower() != lang.lower():
            continue
        code = m.group(3)
        # 1-based start line of the opening fence
        start_line = text.count("\n", 0, m.start()) + 1
        # Look at the line right before the fence for a noexec marker
        prev = lines[start_line - 2] if start_line >= 2 else ""
        skip = None
        if NOEXEC_RE.search(prev):
            skip = "noexec marker"
        elif re.search(r"^\s*\.\.\.\s*$", code, re.MULTILINE):
            skip = "contains '...' placeholder"
        elif re.search(r"^\s*#\s*(pseudo|snippet|not runnable|noexec)\b",
                       code, re.IGNORECASE | re.MULTILINE):
            skip = "marked as snippet"
        yield start_line, code, skip


def build_chapter_notebook(md_path: Path, book_dir: Path, lang: str = "python") -> dict[str, Any] | None:
    """Build one chapter companion notebook from fenced code blocks."""
    blocks = list(extract_blocks(md_path, lang))
    if not blocks:
        return None

    chapter_title = _first_heading(md_path)
    rel_md = md_path.relative_to(book_dir).as_posix()
    cells: list[dict[str, Any]] = [
        _cell(
            "markdown",
            f"# Reproducible scripts for {chapter_title}\n\n"
            f"This companion notebook was generated from fenced `{lang}` code blocks in "
            f"`{rel_md}`. Blocks marked `noexec` in the chapter are preserved for "
            "reading but are not executed here.\n",
            "markdown",
            {"book_source": rel_md},
        ),
        _cell(
            "code",
            _CAPTURE_SETUP,
            "python",
            {"book_generated": True, "book_setup": True},
        ),
    ]

    for index, (start_line, code, skip) in enumerate(blocks, 1):
        title = f"Example {index} from `{rel_md}` line {start_line}"
        cells.append(_cell("markdown", f"## {title}\n", "markdown"))
        metadata = {
            "book_source": rel_md,
            "book_block_index": index,
            "book_source_line": start_line,
        }
        if skip:
            escaped = code.replace("````", "` ` ` `")
            cells.append(_cell(
                "markdown",
                f"This block is preserved but not executed because it is {skip}.\n\n"
                f"````{lang}\n{escaped}\n````\n",
                "markdown",
                {**metadata, "book_skip_reason": skip},
            ))
            continue

        cells.append(_cell("code", code, "python", metadata))
        cells.append(_cell(
            "code",
            f'_paperlab_capture_new_files("example_{index:03d}")',
            "python",
            {**metadata, "book_capture_for_block": index, "tags": ["paperlab-capture"]},
        ))

    return {
        "cells": cells,
        "metadata": {
            "kernelspec": {
                "display_name": "Python 3",
                "language": "python",
                "name": "python3",
            },
            "language_info": {"name": "python", "version": "3"},
            "paperlab": {
                "generated_from": rel_md,
                "generator": "verify_book_code_blocks.py",
                "language": lang,
            },
        },
        "nbformat": 4,
        "nbformat_minor": 5,
    }


def write_chapter_notebooks_from_code_blocks(
    book_dir: Path | str,
    *,
    lang: str = "python",
    chapter: str | None = None,
    force: bool = True,
) -> list[Path]:
    """Write one generated notebook per chapter containing chapter scripts."""
    book_dir = Path(book_dir)
    written: list[Path] = []
    for md_path in chapter_markdown_files(book_dir, chapter):
        notebook = build_chapter_notebook(md_path, book_dir, lang=lang)
        if notebook is None:
            continue
        out_dir = md_path.parent / "notebooks"
        out_dir.mkdir(parents=True, exist_ok=True)
        out_path = out_dir / GENERATED_NOTEBOOK_NAME
        if out_path.exists() and not force:
            continue
        out_path.write_text(json.dumps(notebook, indent=1), encoding="utf-8")
        written.append(out_path)
    return written


def discover_generated_notebooks(book_dir: Path | str, chapter: str | None = None) -> list[Path]:
    """Return generated per-chapter script notebooks."""
    book_dir = Path(book_dir)
    notebooks = []
    for md_path in chapter_markdown_files(book_dir, chapter):
        nb_path = md_path.parent / "notebooks" / GENERATED_NOTEBOOK_NAME
        if nb_path.exists():
            notebooks.append(nb_path)
    return notebooks


def _write_image_output(data: Any, figures_dir: Path, stem: str, cell_index: int, output_index: int) -> str:
    """Write a base64 image output to the chapter figures directory."""
    figures_dir.mkdir(parents=True, exist_ok=True)
    raw = "".join(data) if isinstance(data, list) else str(data)
    payload = base64.b64decode(raw)
    name = f"{stem}_cell{cell_index:02d}_output{output_index:02d}.png"
    path = figures_dir / name
    path.write_bytes(payload)
    return name


def _output_items_from_cell(
    cell: dict[str, Any],
    *,
    figures_dir: Path,
    notebook_stem: str,
    cell_index: int,
    include_errors: bool = False,
    capture_cell: bool = False,
) -> list[dict[str, str]]:
    """Extract markdown-ready output items from one executed notebook cell."""
    items: list[dict[str, str]] = []
    for output_index, out in enumerate(cell.get("outputs", []), 1):
        output_type = out.get("output_type")
        if output_type == "stream":
            text = _join_output_text(out.get("text", "")).strip()
            captured = re.findall(r"Captured figure:\s+figures/([^\s]+)", text)
            for filename in captured:
                items.append({"type": "image", "value": filename})
            if captured:
                text = re.sub(r"^Captured figure:\s+figures/[^\s]+\s*$", "", text,
                              flags=re.MULTILINE).strip()
            if text:
                items.append({"type": "text", "value": text})
            continue
        if output_type == "error":
            if include_errors:
                tb = "\n".join(out.get("traceback", [])) or out.get("evalue", "")
                items.append({"type": "error", "value": tb})
            continue
        data = out.get("data", {})
        if "image/png" in data:
            # The generated notebooks run a capture cell after each executable
            # block. That cell displays the saved image and prints
            # "Captured figure: figures/<name>". Prefer that stable file name
            # for duplicate suppression when it exists. Inline-only figures are
            # still written below and kept by collect_notebook_outputs().
            if capture_cell:
                continue
            filename = _write_image_output(
                data["image/png"], figures_dir, notebook_stem, cell_index, output_index,
            )
            items.append({"type": "image", "value": filename})
        elif "text/plain" in data:
            text = _join_output_text(data.get("text/plain", "")).strip()
            if text and text != "None":
                items.append({"type": "text", "value": text})
        elif "text/html" in data:
            html_text = _join_output_text(data.get("text/html", "")).strip()
            if html_text:
                items.append({"type": "html", "value": html_text})
    return items


def collect_notebook_outputs(
    notebook_path: Path,
    *,
    book_dir: Path,
    include_errors: bool = False,
) -> list[dict[str, Any]]:
    """Collect outputs from an executed generated notebook."""
    notebook = json.loads(notebook_path.read_text(encoding="utf-8"))
    ch_dir = notebook_path.parents[1]
    figures_dir = ch_dir / "figures"
    rel_notebook = notebook_path.relative_to(book_dir).as_posix()
    records: list[dict[str, Any]] = []

    for cell_index, cell in enumerate(notebook.get("cells", []), 1):
        if cell.get("cell_type") != "code":
            continue
        meta = cell.get("metadata", {}) or {}
        if meta.get("book_setup"):
            continue
        block_index = meta.get("book_block_index") or meta.get("book_capture_for_block")
        if not block_index:
            continue
        items = _output_items_from_cell(
            cell,
            figures_dir=figures_dir,
            notebook_stem=notebook_path.stem,
            cell_index=cell_index,
            include_errors=include_errors,
            capture_cell=bool(meta.get("book_capture_for_block")),
        )
        if items:
            records.append({
                "notebook": rel_notebook,
                "block_index": int(block_index),
                "source_line": int(meta.get("book_source_line") or 0),
                "items": items,
                "capture_cell": bool(meta.get("book_capture_for_block")),
            })

    blocks_with_saved_figures = {
        rec["block_index"] for rec in records
        if rec.get("capture_cell")
        and any(item.get("type") == "image" for item in rec.get("items", []))
    }
    filtered: list[dict[str, Any]] = []
    for rec in records:
        if rec.get("capture_cell") or rec["block_index"] not in blocks_with_saved_figures:
            filtered.append(rec)
            continue
        items = [item for item in rec.get("items", []) if item.get("type") != "image"]
        if items:
            new_rec = dict(rec)
            new_rec["items"] = items
            filtered.append(new_rec)
    return filtered


def _trim_text_for_markdown(text: str, limit: int = 4000) -> str:
    """Bound large notebook output before inserting it into chapter markdown."""
    text = text.strip()
    if len(text) <= limit:
        return text
    return text[:limit].rstrip() + "\n... [output truncated]"


def _records_to_markdown(records: list[dict[str, Any]]) -> str:
    """Render collected notebook outputs as a markdown section."""
    if not records:
        return ""
    lines = [
        NOTEBOOK_RESULTS_START,
        "## Reproducible Notebook Results",
        "",
        "The outputs below were captured from the companion Jupyter notebook generated from this chapter's code blocks. They show the expected figures and text results when the examples are run.",
        "",
    ]
    for rec in records:
        line_ref = f" line {rec['source_line']}" if rec.get("source_line") else ""
        lines.append(f"### Example {rec['block_index']}{line_ref}")
        lines.append(f"Notebook: `{rec['notebook']}`")
        lines.append("")
        for item in rec.get("items", []):
            kind = item.get("type")
            value = item.get("value", "")
            if kind == "image":
                caption = f"Expected figure output from Example {rec['block_index']}"
                lines.append(f"![{caption}](figures/{value})")
                lines.append(f"*{caption}*")
                lines.append("")
            elif kind == "html":
                lines.append("<div class=\"notebook-result\">")
                lines.append(value)
                lines.append("</div>")
                lines.append("")
            elif kind == "error":
                lines.append("```text")
                lines.append(_trim_text_for_markdown(value))
                lines.append("```")
                lines.append("")
            else:
                lines.append("```text")
                lines.append(_trim_text_for_markdown(value))
                lines.append("```")
                lines.append("")
    lines.append(NOTEBOOK_RESULTS_END)
    return "\n".join(lines).rstrip() + "\n"


def _insert_notebook_results_section(text: str, section: str) -> str:
    """Insert notebook result markdown before exercises when present."""
    if not section:
        return text
    exercise_match = re.search(r"(?m)^##\s+(?:\d+(?:\.\d+)?\s+)?Exercises\b.*$", text)
    if not exercise_match:
        return text.rstrip() + "\n\n" + section
    return (
        text[:exercise_match.start()].rstrip()
        + "\n\n"
        + section.rstrip()
        + "\n\n"
        + text[exercise_match.start():].lstrip()
    )


def inject_notebook_outputs_into_chapter(
    book_dir: Path | str,
    chapter_dir: Path,
    *,
    include_errors: bool = False,
) -> int:
    """Inject generated notebook outputs into a single ``chapter.md``."""
    book_dir = Path(book_dir)
    chapter_md = chapter_dir / "chapter.md"
    if not chapter_md.exists():
        return 0

    notebooks = sorted((chapter_dir / "notebooks").glob(GENERATED_NOTEBOOK_NAME))
    records: list[dict[str, Any]] = []
    for notebook in notebooks:
        records.extend(collect_notebook_outputs(
            notebook, book_dir=book_dir, include_errors=include_errors,
        ))

    text = chapter_md.read_text(encoding="utf-8")
    pattern = re.compile(
        r"\n?" + re.escape(NOTEBOOK_RESULTS_START) + r".*?" + re.escape(NOTEBOOK_RESULTS_END) + r"\n?",
        re.DOTALL,
    )
    text = pattern.sub("\n", text).rstrip() + "\n"
    section = _records_to_markdown(records)
    if section:
        text = _insert_notebook_results_section(text, section)
    chapter_md.write_text(text, encoding="utf-8")
    return len(records)


def inject_notebook_outputs_into_chapters(
    book_dir: Path | str,
    *,
    chapter: str | None = None,
    include_errors: bool = False,
) -> dict[str, int]:
    """Inject generated notebook outputs into all matching chapters."""
    book_dir = Path(book_dir)
    injected: dict[str, int] = {}
    for md_path in chapter_markdown_files(book_dir, chapter):
        count = inject_notebook_outputs_into_chapter(
            book_dir, md_path.parent, include_errors=include_errors,
        )
        if count:
            injected[md_path.parent.name] = count
    return injected


def run_block(code: str, timeout: int) -> tuple[bool, str]:
    with tempfile.NamedTemporaryFile("w", suffix=".py", delete=False,
                                     encoding="utf-8") as f:
        f.write(code)
        path = f.name
    try:
        proc = subprocess.run(
            [sys.executable, path],
            capture_output=True, text=True, timeout=timeout,
        )
        if proc.returncode == 0:
            return True, ""
        # Capture last few lines of stderr for the error summary
        err = (proc.stderr or proc.stdout).strip().splitlines()
        tail = "\n".join(err[-6:])
        return False, tail
    except subprocess.TimeoutExpired:
        return False, f"TIMEOUT after {timeout}s"
    finally:
        try:
            Path(path).unlink()
        except OSError:
            pass


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("book_dir", type=Path)
    ap.add_argument("--lang", default="python")
    ap.add_argument("--timeout", type=int, default=120)
    ap.add_argument("--report", type=Path, default=None,
                    help="write JSON report to this path")
    ap.add_argument("--chapter", default=None,
                    help="run only blocks from chapter dirs matching this glob "
                         "(e.g. ch05_*)")
    ap.add_argument("--mode", choices=["isolated", "concat"], default="isolated",
                    help="isolated: each block runs alone (default). "
                         "concat: all blocks in one .md file are concatenated "
                         "and run as a single script.")
    ap.add_argument("--write-notebooks", action="store_true",
                    help="write one generated companion notebook per chapter")
    ap.add_argument("--execute-notebooks", action="store_true",
                    help="execute generated companion notebooks after writing/discovery")
    ap.add_argument("--inject-results", action="store_true",
                    help="inject captured generated-notebook outputs into chapter.md")
    ap.add_argument("--force", action="store_true", default=True,
                    help="overwrite generated companion notebooks (default: true)")
    ap.add_argument("--no-force", dest="force", action="store_false",
                    help="do not overwrite existing generated companion notebooks")
    ap.add_argument("--compile", action="store_true",
                    help="compile NeqSim before executing generated notebooks")
    ap.add_argument("--include-errors", action="store_true",
                    help="include notebook error outputs in injected result sections")
    args = ap.parse_args()

    chapters_dir = args.book_dir / "chapters"
    if not chapters_dir.is_dir():
        print(f"No chapters/ dir in {args.book_dir}", file=sys.stderr)
        return 2

    notebook_mode = args.write_notebooks or args.execute_notebooks or args.inject_results
    notebook_failures = 0
    if args.write_notebooks:
        written = write_chapter_notebooks_from_code_blocks(
            args.book_dir, lang=args.lang, chapter=args.chapter, force=args.force,
        )
        print(f"Wrote {len(written)} generated chapter notebook(s).")
        for path in written:
            print(f"  {path.relative_to(args.book_dir)}")

    if args.execute_notebooks:
        sys.path.insert(0, str(Path(__file__).resolve().parent))
        from book_notebook_runner import compile_neqsim, run_notebook

        if args.compile:
            compile_neqsim()
        notebooks = discover_generated_notebooks(args.book_dir, args.chapter)
        print(f"Executing {len(notebooks)} generated chapter notebook(s).")
        for notebook in notebooks:
            result = run_notebook(notebook, timeout=args.timeout)
            errors = result.get("errors", [])
            error = result.get("error")
            rel = notebook.relative_to(args.book_dir)
            if errors or error:
                notebook_failures += 1
                print(f"FAIL  {rel}")
                if error:
                    print(f"      {error}")
                for err in errors[:5]:
                    print(f"      Cell {err.get('cell')}: {err.get('ename')}: {err.get('evalue')}")
            else:
                print(f"PASS  {rel}")

    if args.inject_results:
        injected = inject_notebook_outputs_into_chapters(
            args.book_dir, chapter=args.chapter, include_errors=args.include_errors,
        )
        if injected:
            print("Injected notebook results:")
            for chapter_name, count in injected.items():
                print(f"  {chapter_name}: {count} output block(s)")
        else:
            print("No notebook outputs found to inject.")

    if notebook_mode:
        return 1 if notebook_failures else 0

    md_files = sorted(chapters_dir.rglob("*.md"))
    if args.chapter:
        md_files = [p for p in md_files
                    if any(part for part in p.parts if Path(part).match(args.chapter))]

    results = []
    total = passed = failed = skipped = 0
    for md in md_files:
        rel = md.relative_to(args.book_dir)
        blocks = list(extract_blocks(md, args.lang))
        if not blocks:
            continue

        if args.mode == "concat":
            # Build one script from all non-skipped blocks; runnable blocks
            # share state, matching the reader's experience.
            parts = []
            runnable_count = 0
            for start_line, code, skip in blocks:
                total += 1
                if skip:
                    skipped += 1
                    print(f"SKIP  {rel}:{start_line}  ({skip})")
                    results.append({"file": str(rel).replace("\\", "/"),
                                    "line": start_line, "status": "SKIP",
                                    "error": skip})
                    continue
                runnable_count += 1
                parts.append(f"# --- block at line {start_line} ---\n{code}")
            if runnable_count == 0:
                continue
            big = "\n\n".join(parts)
            ok, err = run_block(big, args.timeout)
            entry = {"file": str(rel).replace("\\", "/"),
                     "line": blocks[0][0],
                     "status": "PASS" if ok else "FAIL",
                     "error": err if not ok else None,
                     "mode": "concat",
                     "block_count": runnable_count}
            if ok:
                passed += runnable_count
                print(f"PASS  {rel}  ({runnable_count} blocks concatenated)")
            else:
                failed += runnable_count
                print(f"FAIL  {rel}  ({runnable_count} blocks concatenated)")
                for line in err.splitlines():
                    print(f"        {line}")
            results.append(entry)
            continue

        # isolated mode (default)
        for start_line, code, skip in blocks:
            total += 1
            entry = {"file": str(rel).replace("\\", "/"),
                     "line": start_line,
                     "status": None,
                     "error": None}
            if skip:
                entry["status"] = "SKIP"
                entry["error"] = skip
                skipped += 1
                print(f"SKIP  {entry['file']}:{start_line}  ({skip})")
            else:
                ok, err = run_block(code, args.timeout)
                if ok:
                    entry["status"] = "PASS"
                    passed += 1
                    print(f"PASS  {entry['file']}:{start_line}")
                else:
                    entry["status"] = "FAIL"
                    entry["error"] = err
                    failed += 1
                    print(f"FAIL  {entry['file']}:{start_line}")
                    for line in err.splitlines():
                        print(f"        {line}")
            results.append(entry)

    print()
    print(f"Total: {total}  PASS: {passed}  FAIL: {failed}  SKIP: {skipped}")

    if args.report:
        args.report.write_text(json.dumps({
            "total": total, "passed": passed, "failed": failed,
            "skipped": skipped, "results": results,
        }, indent=2), encoding="utf-8")
        print(f"Report written to {args.report}")

    return 0 if failed == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
