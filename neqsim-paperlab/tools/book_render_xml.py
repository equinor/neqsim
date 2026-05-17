"""JATS / DocBook XML renderers.

JATS (Journal Article Tag Suite) is required by many publishers; DocBook
is widely used for technical books. Both are Pandoc output formats.

Usage::

    render_book_jats(book_dir)
    render_book_docbook(book_dir)
"""
from __future__ import annotations

import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import book_builder  # type: ignore
import book_render_epub  # type: ignore


def _concat_md(book_dir: Path) -> tuple[Path, str]:
    """Reuse EPUB renderer's chapter collection."""
    cfg = book_builder.load_book_config(book_dir)
    md_files = book_render_epub._collect_chapter_md(book_dir, cfg)
    md_concat = []
    for p in md_files:
        md_concat.append(p.read_text(encoding="utf-8"))
        md_concat.append("\n\n")
    fd = tempfile.NamedTemporaryFile(
        mode="w", encoding="utf-8", delete=False, suffix=".md"
    )
    meta = book_render_epub._build_metadata(cfg)
    fd.write("---\n")
    for k, v in meta.items():
        if isinstance(v, list):
            fd.write(f"{k}:\n")
            for item in v:
                fd.write(f"  - {item}\n")
        elif v:
            fd.write(f"{k}: \"{v}\"\n")
    fd.write("---\n\n")
    fd.write("\n".join(md_concat))
    fd.close()
    return Path(fd.name), cfg.get("title", "Book")


def _run_pandoc(input_md: Path, out_path: Path, fmt: str, book_dir: Path):
    if shutil.which("pandoc") is None:
        raise RuntimeError("pandoc not found on PATH")
    figures_dir = book_dir / "figures"
    sep = ";" if sys.platform.startswith("win") else ":"
    resource_path = f"{book_dir}{sep}{figures_dir}"
    cmd = [
        "pandoc",
        str(input_md),
        "-f",
        "markdown+tex_math_dollars+pipe_tables+raw_html",
        "-t",
        fmt,
        "-o",
        str(out_path),
        "--standalone",
        "--wrap=none",
        f"--resource-path={resource_path}",
    ]
    bib = book_dir / "refs.bib"
    if bib.exists():
        cmd += ["--citeproc", f"--bibliography={bib}"]
    subprocess.run(cmd, check=True)


def render_book_jats(book_dir, out: Path | None = None) -> Path:
    book_dir = Path(book_dir)
    out = out or book_dir / "build" / f"{book_dir.name}.jats.xml"
    out.parent.mkdir(parents=True, exist_ok=True)
    md, _ = _concat_md(book_dir)
    try:
        _run_pandoc(md, out, "jats", book_dir)
    finally:
        try:
            md.unlink()
        except Exception:
            pass
    print(f"[jats] wrote {out}")
    return out


def render_book_docbook(book_dir, out: Path | None = None) -> Path:
    book_dir = Path(book_dir)
    out = out or book_dir / "build" / f"{book_dir.name}.docbook.xml"
    out.parent.mkdir(parents=True, exist_ok=True)
    md, _ = _concat_md(book_dir)
    try:
        _run_pandoc(md, out, "docbook5", book_dir)
    finally:
        try:
            md.unlink()
        except Exception:
            pass
    print(f"[docbook] wrote {out}")
    return out
