"""EPUB 3 renderer for PaperLab books.

Uses Pandoc to assemble all frontmatter, chapters, and backmatter into a
single EPUB 3.2 file with KaTeX-rendered math, embedded fonts, and a
table of contents.

Requirements
------------
* `pandoc` on PATH (system package).
* Optional: `--csl=path/to/style.csl` for citation styling.

Usage
-----
    python paperflow.py book-render books/<dir> --format epub
"""
from __future__ import annotations

import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import book_builder  # type: ignore


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _collect_chapter_md(book_dir: Path, cfg: dict) -> list[Path]:
    """Return ordered list of markdown files for the EPUB body."""
    parts: list[Path] = []

    fm_dir = book_dir / "frontmatter"
    for entry in cfg.get("frontmatter", []) or []:
        cand = fm_dir / f"{entry}.md"
        if cand.exists():
            parts.append(cand)

    for ch_num, ch, part_title in book_builder.iter_chapters(cfg):
        ch_dir = book_builder.resolve_chapter_dir(book_dir, ch)
        cm = ch_dir / "chapter.md"
        if cm.exists():
            parts.append(cm)

    bm_dir = book_dir / "backmatter"
    for entry in cfg.get("backmatter", []) or []:
        cand = bm_dir / f"{entry}.md"
        if cand.exists():
            parts.append(cand)

    return parts


def _build_metadata(cfg: dict) -> dict:
    md = {
        "title": cfg.get("title", "Untitled"),
        "subtitle": cfg.get("subtitle", ""),
        "lang": cfg.get("language", "en"),
        "rights": cfg.get("copyright", ""),
        "publisher": cfg.get("publisher", ""),
        "date": str(cfg.get("year", "")),
    }
    authors = cfg.get("authors", [])
    if authors:
        md["author"] = [a.get("name", "") for a in authors if a.get("name")]
    return md


# ---------------------------------------------------------------------------
# Public API
# ---------------------------------------------------------------------------

def render_book_epub(book_dir, chapter_filter: str | None = None):
    """Render the book as a single EPUB 3 file via Pandoc."""
    book_dir = Path(book_dir).resolve()
    if shutil.which("pandoc") is None:
        print("[book_render_epub] pandoc not found on PATH")
        return None

    cfg = book_builder.load_book_config(book_dir)
    if chapter_filter:
        print("[book_render_epub] note: --chapter is ignored for EPUB (single artifact)")

    md_files = _collect_chapter_md(book_dir, cfg)
    if not md_files:
        print("[book_render_epub] no chapter content found")
        return None

    submission = book_dir / "submission"
    submission.mkdir(exist_ok=True)
    out = submission / "book.epub"

    # Concatenate all markdown into a single staging file so Pandoc's
    # heading levels stay coherent and figures resolve.
    with tempfile.TemporaryDirectory() as tmp:
        tmp_dir = Path(tmp)
        combined = tmp_dir / "book.md"
        with combined.open("w", encoding="utf-8") as fh:
            meta = _build_metadata(cfg)
            fh.write("---\n")
            for k, v in meta.items():
                if isinstance(v, list):
                    fh.write(f"{k}:\n")
                    for item in v:
                        fh.write(f"  - {item}\n")
                elif v:
                    fh.write(f"{k}: \"{v}\"\n")
            fh.write("---\n\n")
            for src in md_files:
                # Rewrite figure paths to absolute for pandoc resource resolution
                txt = src.read_text(encoding="utf-8")
                fh.write(f"\n\n<!-- source: {src.relative_to(book_dir)} -->\n\n")
                fh.write(txt)
                fh.write("\n")

        cmd = [
            "pandoc", str(combined),
            "-o", str(out),
            "-f", "markdown+tex_math_dollars+pipe_tables+raw_html",
            "-t", "epub3",
            "--toc", "--toc-depth=2",
            "--mathml",
            "--wrap=none",
        ]
        # Optional CSL styling
        csl = cfg.get("bibliography", {}).get("csl") if isinstance(cfg.get("bibliography"), dict) else None
        bib_file = book_dir / cfg.get("bibliography", {}).get("file", "refs.bib") \
            if isinstance(cfg.get("bibliography"), dict) else book_dir / "refs.bib"
        if bib_file.exists():
            cmd += ["--citeproc", "--bibliography", str(bib_file)]
            if csl:
                csl_path = (book_dir / csl).resolve()
                if csl_path.exists():
                    cmd += ["--csl", str(csl_path)]

        # Resource path for figures (chapter dirs)
        resource_paths = [str(book_dir)]
        for src in md_files:
            resource_paths.append(str(src.parent))
        cmd += ["--resource-path=" + (";" if sys.platform == "win32" else ":").join(resource_paths)]

        # Cover image (optional)
        cover = book_dir / "cover.png"
        if cover.exists():
            cmd += ["--epub-cover-image", str(cover)]

        result = subprocess.run(cmd, capture_output=True, text=True)
        if result.returncode != 0:
            print(f"[book_render_epub] pandoc failed:\n{result.stderr}")
            return None

    size_kb = out.stat().st_size // 1024
    print(f"[book_render_epub] EPUB generated: {out}  ({size_kb} KB)")
    return out
