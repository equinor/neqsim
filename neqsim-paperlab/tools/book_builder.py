"""
book_builder — Core assembly logic for scientific books.

Parses book.yaml manifests, scaffolds book projects, assembles chapters into
ordered documents, and generates table of contents / lists.
"""

import os
import re
import shutil
from datetime import datetime
from pathlib import Path

import yaml

# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------

_FRONTMATTER_TEMPLATES = {
    "title_page": "book_frontmatter/title_page.md",
    "copyright": "book_frontmatter/copyright.md",
    "dedication": "book_frontmatter/dedication.md",
    "preface": "book_frontmatter/preface.md",
    "acknowledgements": "book_frontmatter/preface.md",  # reuse preface template
}

_BACKMATTER_TEMPLATES = {
    "glossary": "book_backmatter/glossary.md",
    "author_bio": "book_backmatter/author_bio.md",
}

TEMPLATES_DIR = Path(__file__).parent.parent / "templates"


# ---------------------------------------------------------------------------
# YAML helpers
# ---------------------------------------------------------------------------

def load_book_config(book_dir):
    """Load and validate ``book.yaml`` from *book_dir*.

    Returns the parsed dict or raises ``FileNotFoundError`` / ``ValueError``.
    """
    book_dir = Path(book_dir)
    yaml_path = book_dir / "book.yaml"
    if not yaml_path.exists():
        raise FileNotFoundError(f"book.yaml not found in {book_dir}")

    with open(yaml_path, encoding="utf-8") as f:
        cfg = yaml.safe_load(f)

    if not cfg:
        raise ValueError("book.yaml is empty")

    # Minimal validation
    for key in ("title", "authors", "parts"):
        if key not in cfg:
            raise ValueError(f"book.yaml missing required key: '{key}'")
    if not isinstance(cfg["parts"], list) or len(cfg["parts"]) == 0:
        raise ValueError("book.yaml 'parts' must be a non-empty list")

    return cfg


def _load_publisher_profile(publisher_name):
    """Load a publisher YAML profile from ``books/_publisher_profiles/``."""
    profiles_dir = Path(__file__).parent.parent / "books" / "_publisher_profiles"
    path = profiles_dir / f"{publisher_name}.yaml"
    if not path.exists():
        return {}
    with open(path, encoding="utf-8") as f:
        return yaml.safe_load(f) or {}


# ---------------------------------------------------------------------------
# Scaffolding
# ---------------------------------------------------------------------------

def create_book_project(title, publisher="self", n_chapters=8, books_dir=None):
    """Scaffold a new book project directory.

    Returns the *Path* to the created book directory.
    """
    if books_dir is None:
        books_dir = Path(__file__).parent.parent / "books"
    books_dir = Path(books_dir)

    year = datetime.now().strftime("%Y")
    slug = re.sub(r"[^a-z0-9]+", "_", title.lower()).strip("_")[:50]
    book_dir = books_dir / f"{slug}_{year}"

    if book_dir.exists():
        raise FileExistsError(f"Book directory already exists: {book_dir}")

    # Directory structure
    (book_dir / "frontmatter").mkdir(parents=True, exist_ok=True)
    (book_dir / "chapters").mkdir(parents=True, exist_ok=True)
    (book_dir / "backmatter").mkdir(parents=True, exist_ok=True)
    (book_dir / "submission").mkdir(parents=True, exist_ok=True)

    # Frontmatter files from templates
    for fm_key in ("title_page", "copyright", "dedication", "preface"):
        tpl_rel = _FRONTMATTER_TEMPLATES.get(fm_key, "")
        tpl_path = TEMPLATES_DIR / tpl_rel
        dst = book_dir / "frontmatter" / f"{fm_key}.md"
        if tpl_path.exists():
            text = tpl_path.read_text(encoding="utf-8")
            # Substitute placeholders
            text = text.replace("{{TITLE}}", title)
            text = text.replace("{{YEAR}}", year)
            text = text.replace("{{PUBLISHER}}", publisher.title())
            text = text.replace("{{AUTHORS}}", "Author Name")
            text = text.replace("{{SUBTITLE}}", "")
            text = text.replace("{{AFFILIATIONS}}", "")
            text = text.replace("{{EDITION}}", "1st")
            text = text.replace("{{ISBN}}", "")
            text = text.replace("{{LICENSE}}", "")
            dst.write_text(text, encoding="utf-8")
        else:
            dst.write_text(f"# {fm_key.replace('_', ' ').title()}\n\nTODO\n",
                           encoding="utf-8")

    # Backmatter files
    for bm_key in ("glossary", "author_bio"):
        tpl_rel = _BACKMATTER_TEMPLATES.get(bm_key, "")
        tpl_path = TEMPLATES_DIR / tpl_rel
        dst = book_dir / "backmatter" / f"{bm_key}.md"
        if tpl_path.exists():
            shutil.copy2(tpl_path, dst)
        else:
            dst.write_text(f"# {bm_key.replace('_', ' ').title()}\n\nTODO\n",
                           encoding="utf-8")

    # Chapter directories
    chapter_tpl = TEMPLATES_DIR / "book_chapter.md"
    chapter_tpl_text = ""
    if chapter_tpl.exists():
        chapter_tpl_text = chapter_tpl.read_text(encoding="utf-8")

    chapters_list = []
    for i in range(1, n_chapters + 1):
        ch_name = f"ch{i:02d}"
        ch_dir = book_dir / "chapters" / ch_name
        ch_dir.mkdir(parents=True, exist_ok=True)
        (ch_dir / "notebooks").mkdir(exist_ok=True)
        (ch_dir / "figures").mkdir(exist_ok=True)

        ch_title = f"Chapter {i}"
        ch_text = chapter_tpl_text
        ch_text = ch_text.replace("{{CHAPTER_TITLE}}", ch_title)
        ch_text = ch_text.replace("{{CHAPTER_NUM}}", str(i))
        (ch_dir / "chapter.md").write_text(ch_text, encoding="utf-8")

        chapters_list.append({"dir": ch_name, "title": ch_title})

    # Empty refs.bib
    (book_dir / "refs.bib").write_text(
        "% Master bibliography for the book\n", encoding="utf-8"
    )

    # Nomenclature stub
    (book_dir / "nomenclature.yaml").write_text(
        "# Symbol definitions\nsymbols:\n"
        "  P:\n    description: Pressure\n    unit: Pa\n"
        "  T:\n    description: Temperature\n    unit: K\n",
        encoding="utf-8",
    )

    # Generate book.yaml
    profile = _load_publisher_profile(publisher)
    book_cfg = {
        "title": title,
        "subtitle": "",
        "authors": [
            {
                "name": "Author Name",
                "affiliation": "Institution",
                "email": "author@example.com",
            }
        ],
        "edition": "1st",
        "year": int(year),
        "publisher": publisher,
        "language": "en",
        "isbn": "",
        "settings": {
            "page_size": "a4",
            "font_size": profile.get("font", {}).get("size", "11pt")
            if isinstance(profile.get("font"), dict) else 11,
            "line_spacing": profile.get("line_spacing", 1.5),
            "two_sided": True,
            "chapter_numbering": True,
            "equation_numbering": "chapter",
        },
        "frontmatter": ["title_page", "copyright", "dedication", "preface"],
        "parts": [
            {
                "title": "Part I",
                "chapters": chapters_list,
            }
        ],
        "backmatter": ["glossary", "author_bio"],
        "nomenclature": {
            "file": "nomenclature.yaml",
            "position": "after_toc",
        },
        "bibliography": {
            "style": "numeric",
            "file": "refs.bib",
        },
    }

    with open(book_dir / "book.yaml", "w", encoding="utf-8") as f:
        yaml.dump(book_cfg, f, default_flow_style=False, sort_keys=False,
                  allow_unicode=True)

    return book_dir


def add_chapter(book_dir, title, part_index=0):
    """Add a new chapter directory to an existing book.

    *part_index* is the 0-based index into book.yaml ``parts``.
    Returns the new chapter directory *Path*.
    """
    book_dir = Path(book_dir)
    cfg = load_book_config(book_dir)

    if part_index < 0 or part_index >= len(cfg["parts"]):
        raise IndexError(
            f"part_index {part_index} out of range (0..{len(cfg['parts']) - 1})"
        )

    # Determine next chapter number across ALL parts
    existing = []
    for part in cfg["parts"]:
        for ch in part.get("chapters", []):
            existing.append(ch["dir"])
    next_num = len(existing) + 1

    slug = re.sub(r"[^a-z0-9]+", "_", title.lower()).strip("_")[:40]
    ch_name = f"ch{next_num:02d}_{slug}"

    ch_dir = book_dir / "chapters" / ch_name
    ch_dir.mkdir(parents=True, exist_ok=True)
    (ch_dir / "notebooks").mkdir(exist_ok=True)
    (ch_dir / "figures").mkdir(exist_ok=True)

    # Write chapter.md from template
    chapter_tpl = TEMPLATES_DIR / "book_chapter.md"
    if chapter_tpl.exists():
        text = chapter_tpl.read_text(encoding="utf-8")
    else:
        text = "# {{CHAPTER_TITLE}}\n\nTODO\n"
    text = text.replace("{{CHAPTER_TITLE}}", title)
    text = text.replace("{{CHAPTER_NUM}}", str(next_num))
    (ch_dir / "chapter.md").write_text(text, encoding="utf-8")

    # Update book.yaml
    cfg["parts"][part_index].setdefault("chapters", []).append(
        {"dir": ch_name, "title": title}
    )
    with open(book_dir / "book.yaml", "w", encoding="utf-8") as f:
        yaml.dump(cfg, f, default_flow_style=False, sort_keys=False,
                  allow_unicode=True)

    return ch_dir


# ---------------------------------------------------------------------------
# Chapter enumeration helpers
# ---------------------------------------------------------------------------

def iter_chapters(cfg):
    """Yield ``(chapter_number, chapter_dict, part_title)`` for all chapters.

    *chapter_number* is 1-based, sequential across all parts.
    """
    num = 0
    for part in cfg.get("parts", []):
        part_title = part.get("title", "")
        for ch in part.get("chapters", []):
            num += 1
            yield num, ch, part_title


def resolve_chapter_dir(book_dir, ch_dict):
    """Return the *Path* to a chapter directory from its book.yaml entry."""
    return Path(book_dir) / "chapters" / ch_dict["dir"]


# Regex that matches a leading section number like "1.1 " or "1.3.3 " at the
# start of a heading's text (after the ``#`` markers have been removed).
_HEADING_NUM_RE = re.compile(r"^\d+(?:\.\d+)+\s+")


def strip_heading_numbers(text):
    """Remove hardcoded section numbers from markdown headings.

    Headings authored as ``## 1.3 Title`` become ``## Title`` so that
    renderers with automatic outline numbering (ODF, Typst/PDF, Word)
    do not produce duplicated numbers like "1.4.3 1.3.3 Title".

    Only ``##``, ``###``, and ``####`` headings are affected — the
    top-level ``#`` heading is left untouched because the book builder
    already handles chapter-title numbering separately.
    """
    def _strip(m):
        hashes = m.group(1)
        body = m.group(2)
        body = _HEADING_NUM_RE.sub("", body)
        return f"{hashes} {body}"

    return re.sub(r"^(#{2,4})\s+(.+)", _strip, text, flags=re.MULTILINE)


# ---------------------------------------------------------------------------
# Assembly
# ---------------------------------------------------------------------------

def assemble_book(book_dir):
    """Assemble the full book text from frontmatter + chapters + backmatter.

    Returns a list of ``(section_label, markdown_text)`` tuples in order.
    """
    book_dir = Path(book_dir)
    cfg = load_book_config(book_dir)

    sections = []

    # Frontmatter
    for fm in cfg.get("frontmatter", []):
        fm_path = book_dir / "frontmatter" / f"{fm}.md"
        if fm_path.exists():
            sections.append((f"frontmatter:{fm}", fm_path.read_text(encoding="utf-8")))

    # Chapters (with part separators)
    prev_part = None
    for ch_num, ch, part_title in iter_chapters(cfg):
        if part_title and part_title != prev_part:
            sections.append((f"part:{part_title}", f"\n# {part_title}\n"))
            prev_part = part_title

        ch_dir = resolve_chapter_dir(book_dir, ch)
        ch_md = ch_dir / "chapter.md"
        if ch_md.exists():
            text = ch_md.read_text(encoding="utf-8")
            # Prepend chapter number to the first heading if not already there
            text = _ensure_chapter_heading(text, ch_num, ch.get("title", ""))
            sections.append((f"chapter:{ch_num}", text))
        else:
            sections.append(
                (f"chapter:{ch_num}", f"# Chapter {ch_num}: {ch.get('title', 'Untitled')}\n\n*Content not yet written.*\n")
            )

    # Backmatter
    for bm in cfg.get("backmatter", []):
        bm_path = book_dir / "backmatter" / f"{bm}.md"
        if bm_path.exists():
            sections.append((f"backmatter:{bm}", bm_path.read_text(encoding="utf-8")))

    return sections


def _ensure_chapter_heading(text, ch_num, title):
    """If the first heading lacks a chapter number prefix, add one."""
    lines = text.split("\n")
    for i, line in enumerate(lines):
        stripped = line.strip()
        if stripped.startswith("# ") and not stripped.startswith("## "):
            # Check if it already has "Chapter N:" prefix
            if not re.match(r"^#\s+Chapter\s+\d+", stripped):
                heading_text = stripped[2:].strip()
                lines[i] = f"# Chapter {ch_num}: {heading_text}"
            break
    return "\n".join(lines)


# ---------------------------------------------------------------------------
# TOC / LOF / LOT generation
# ---------------------------------------------------------------------------

def generate_toc(book_dir):
    """Generate a table of contents from the assembled book.

    Returns a list of ``(level, number, title, chapter_num)`` tuples.
    """
    book_dir = Path(book_dir)
    cfg = load_book_config(book_dir)
    toc = []

    # Frontmatter entries
    for fm in cfg.get("frontmatter", []):
        fm_path = book_dir / "frontmatter" / f"{fm}.md"
        if fm_path.exists():
            toc.append((0, "", fm.replace("_", " ").title(), None))

    prev_part = None
    for ch_num, ch, part_title in iter_chapters(cfg):
        if part_title and part_title != prev_part:
            toc.append((0, "", part_title, None))
            prev_part = part_title

        ch_title = ch.get("title", f"Chapter {ch_num}")
        toc.append((1, str(ch_num), ch_title, ch_num))

        # Parse sub-headings from chapter.md
        ch_dir = resolve_chapter_dir(book_dir, ch)
        ch_md = ch_dir / "chapter.md"
        if ch_md.exists():
            text = ch_md.read_text(encoding="utf-8")
            for m in re.finditer(r"^(#{2,3})\s+(.+)", text, re.MULTILINE):
                level = len(m.group(1))  # 2 = section, 3 = subsection
                heading = m.group(2).strip()
                toc.append((level, "", heading, ch_num))

    # Backmatter entries
    for bm in cfg.get("backmatter", []):
        bm_path = book_dir / "backmatter" / f"{bm}.md"
        if bm_path.exists():
            toc.append((0, "", bm.replace("_", " ").title(), None))

    return toc


def generate_figure_list(book_dir):
    """Scan all chapters for figure references.

    Returns a list of ``(chapter_num, fig_file, caption)`` tuples.
    """
    book_dir = Path(book_dir)
    cfg = load_book_config(book_dir)
    figures = []

    for ch_num, ch, _part in iter_chapters(cfg):
        ch_dir = resolve_chapter_dir(book_dir, ch)
        ch_md = ch_dir / "chapter.md"
        if not ch_md.exists():
            continue
        text = ch_md.read_text(encoding="utf-8")
        # Match: ![caption](path)
        for m in re.finditer(r"!\[([^\]]*)\]\(([^)]+)\)", text):
            caption = m.group(1) or os.path.basename(m.group(2))
            fig_file = m.group(2)
            figures.append((ch_num, fig_file, caption))

    return figures


def generate_table_list(book_dir):
    """Scan all chapters for table captions (``*Table N.M: ...*``).

    Returns a list of ``(chapter_num, table_id, caption)`` tuples.
    """
    book_dir = Path(book_dir)
    cfg = load_book_config(book_dir)
    tables = []

    for ch_num, ch, _part in iter_chapters(cfg):
        ch_dir = resolve_chapter_dir(book_dir, ch)
        ch_md = ch_dir / "chapter.md"
        if not ch_md.exists():
            continue
        text = ch_md.read_text(encoding="utf-8")
        # Match: *Table 1.2: Some caption*
        for m in re.finditer(r"\*Table\s+([\d.]+):\s*(.+?)\*", text):
            tables.append((ch_num, m.group(1), m.group(2).strip()))

    return tables


# ---------------------------------------------------------------------------
# Status
# ---------------------------------------------------------------------------

def get_book_status(book_dir):
    """Compute status summary for a book project.

    Returns a dict with per-chapter and aggregate statistics.
    """
    book_dir = Path(book_dir)
    cfg = load_book_config(book_dir)

    chapters = []
    total_words = 0
    total_todos = 0
    total_figures = 0
    total_notebooks = 0

    for ch_num, ch, part_title in iter_chapters(cfg):
        ch_dir = resolve_chapter_dir(book_dir, ch)
        ch_md = ch_dir / "chapter.md"

        words = 0
        todos = 0
        figs = 0
        nbs = 0
        exists = ch_md.exists()

        if exists:
            text = ch_md.read_text(encoding="utf-8")
            words = len(text.split())
            todos = text.upper().count("TODO")
            figs = len(re.findall(r"!\[.*?\]\(.*?\)", text))
            # Count notebooks in notebooks/ directory
            nb_dir = ch_dir / "notebooks"
            if nb_dir.exists():
                nbs = len(list(nb_dir.glob("*.ipynb")))

        total_words += words
        total_todos += todos
        total_figures += figs
        total_notebooks += nbs

        chapters.append({
            "number": ch_num,
            "dir": ch["dir"],
            "title": ch.get("title", ""),
            "part": part_title,
            "exists": exists,
            "word_count": words,
            "todo_count": todos,
            "figure_count": figs,
            "notebook_count": nbs,
            "estimated_pages": max(1, words // 250),
        })

    return {
        "title": cfg.get("title", "Untitled"),
        "publisher": cfg.get("publisher", "unknown"),
        "total_chapters": len(chapters),
        "total_words": total_words,
        "total_todos": total_todos,
        "total_figures": total_figures,
        "total_notebooks": total_notebooks,
        "estimated_pages": max(1, total_words // 250),
        "chapters": chapters,
    }


def format_toc(toc):
    """Format a TOC list into a readable string."""
    lines = []
    for level, number, title, _ch_num in toc:
        if level == 0:
            lines.append(f"\n  {title}")
        elif level == 1:
            lines.append(f"    {number}. {title}")
        elif level == 2:
            lines.append(f"        {title}")
        else:
            lines.append(f"            {title}")
    return "\n".join(lines)


# ---------------------------------------------------------------------------
# Book drafting — generate chapter content from outline
# ---------------------------------------------------------------------------

def load_chapter_outline(book_dir):
    """Load ``chapter_outlines.yaml`` from *book_dir* if it exists.

    Returns a dict mapping chapter dir names to outline dicts, or empty dict.
    """
    book_dir = Path(book_dir)
    outline_path = book_dir / "chapter_outlines.yaml"
    if not outline_path.exists():
        return {}
    with open(outline_path, encoding="utf-8") as f:
        data = yaml.safe_load(f)
    return data if isinstance(data, dict) else {}


def draft_chapter(book_dir, ch_dir_name, outline=None, force=False):
    """Generate a draft chapter.md from an outline specification.

    Parameters
    ----------
    book_dir : str or Path
        Book project directory.
    ch_dir_name : str
        Chapter directory name (e.g., ``ch01_introduction``).
    outline : dict or None
        Chapter outline with keys: ``title``, ``target_pages``,
        ``learning_objectives``, ``sections`` (list of section dicts
        with ``heading``, ``content_guidance``, ``subsections``).
        If None, loads from ``chapter_outlines.yaml``.
    force : bool
        Overwrite existing chapter.md even if it has content beyond template.

    Returns
    -------
    Path
        Path to the written chapter.md.
    """
    book_dir = Path(book_dir)
    ch_dir = book_dir / "chapters" / ch_dir_name

    if not ch_dir.exists():
        ch_dir.mkdir(parents=True, exist_ok=True)
        (ch_dir / "notebooks").mkdir(exist_ok=True)
        (ch_dir / "figures").mkdir(exist_ok=True)

    ch_md = ch_dir / "chapter.md"

    # Don't overwrite substantive content unless forced
    if ch_md.exists() and not force:
        existing = ch_md.read_text(encoding="utf-8")
        if "TODO" not in existing and len(existing.split()) > 500:
            return ch_md

    # Load outline from file if not provided
    if outline is None:
        outlines = load_chapter_outline(book_dir)
        outline = outlines.get(ch_dir_name, {})

    if not outline:
        return ch_md  # No outline to draft from

    # Determine chapter number from book.yaml
    cfg = load_book_config(book_dir)
    ch_num = 1
    for num, ch, _part in iter_chapters(cfg):
        if ch["dir"] == ch_dir_name:
            ch_num = num
            break

    title = outline.get("title", f"Chapter {ch_num}")
    target_pages = outline.get("target_pages", 20)
    objectives = outline.get("learning_objectives", [])
    sections = outline.get("sections", [])
    notebooks = outline.get("notebooks", [])

    lines = []
    lines.append(f"# {title}\n")
    lines.append(f"<!-- Chapter metadata -->")
    if notebooks:
        lines.append(f"<!-- Notebooks: {', '.join(notebooks)} -->")
    lines.append(f"<!-- Estimated pages: {target_pages} -->")
    lines.append("")

    # Learning objectives
    if objectives:
        lines.append("## Learning Objectives\n")
        lines.append("After reading this chapter, the reader will be able to:\n")
        for i, obj in enumerate(objectives, 1):
            lines.append(f"{i}. {obj}")
        lines.append("")

    # Sections
    for sec in sections:
        heading = sec.get("heading", "Section")
        content = sec.get("content_guidance", "")
        subsections = sec.get("subsections", [])
        code_examples = sec.get("code_examples", [])
        equations = sec.get("equations", [])
        figures = sec.get("figures", [])
        tables = sec.get("tables", [])

        lines.append(f"## {heading}\n")

        if content:
            lines.append(f"{content}\n")

        # Equations
        for eq in equations:
            label = eq.get("label", "")
            latex = eq.get("latex", "")
            description = eq.get("description", "")
            if latex:
                lines.append(f"$${latex}$$\n")
            if description:
                lines.append(f"{description}\n")

        # Subsections
        for sub in subsections:
            sub_heading = sub.get("heading", "Subsection")
            sub_content = sub.get("content_guidance", "")
            sub_equations = sub.get("equations", [])
            sub_code = sub.get("code_examples", [])
            sub_figures = sub.get("figures", [])
            sub_tables = sub.get("tables", [])

            lines.append(f"### {sub_heading}\n")
            if sub_content:
                lines.append(f"{sub_content}\n")

            for eq in sub_equations:
                if eq.get("latex"):
                    lines.append(f"$${eq['latex']}$$\n")
                if eq.get("description"):
                    lines.append(f"{eq['description']}\n")

            for code in sub_code:
                lang = code.get("language", "python")
                snippet = code.get("code", "# TODO")
                caption = code.get("caption", "")
                if caption:
                    lines.append(f"*{caption}*\n")
                lines.append(f"```{lang}")
                lines.append(snippet)
                lines.append("```\n")

            for fig in sub_figures:
                fname = fig.get("file", "placeholder.png")
                caption = fig.get("caption", "Figure")
                lines.append(f"![{caption}](figures/{fname})\n")
                lines.append(f"*{caption}*\n")

            for tbl in sub_tables:
                tbl_caption = tbl.get("caption", "Table")
                headers = tbl.get("headers", [])
                rows = tbl.get("rows", [])
                if headers:
                    lines.append(f"*{tbl_caption}*\n")
                    lines.append("| " + " | ".join(str(h) for h in headers) + " |")
                    lines.append("| " + " | ".join("---" for _ in headers) + " |")
                    for row in rows:
                        lines.append("| " + " | ".join(str(c) for c in row) + " |")
                    lines.append("")

        # Top-level code examples
        for code in code_examples:
            lang = code.get("language", "python")
            snippet = code.get("code", "# TODO")
            caption = code.get("caption", "")
            if caption:
                lines.append(f"*{caption}*\n")
            lines.append(f"```{lang}")
            lines.append(snippet)
            lines.append("```\n")

        # Top-level figures
        for fig in figures:
            fname = fig.get("file", "placeholder.png")
            caption = fig.get("caption", "Figure")
            lines.append(f"![{caption}](figures/{fname})\n")
            lines.append(f"*{caption}*\n")

        # Top-level tables
        for tbl in tables:
            tbl_caption = tbl.get("caption", "Table")
            headers = tbl.get("headers", [])
            rows = tbl.get("rows", [])
            if headers:
                lines.append(f"*{tbl_caption}*\n")
                lines.append("| " + " | ".join(str(h) for h in headers) + " |")
                lines.append("| " + " | ".join("---" for _ in headers) + " |")
                for row in rows:
                    lines.append("| " + " | ".join(str(c) for c in row) + " |")
                lines.append("")

    # Summary
    summary = outline.get("summary_points", [])
    if summary:
        lines.append("## Summary\n")
        lines.append("Key points from this chapter:\n")
        for pt in summary:
            lines.append(f"- {pt}")
        lines.append("")

    # Exercises
    exercises = outline.get("exercises", [])
    if exercises:
        lines.append("## Exercises\n")
        for i, ex in enumerate(exercises, 1):
            lines.append(f"{i}. **Exercise {ch_num}.{i}:** {ex}")
        lines.append("")

    # References
    lines.append("## References\n")
    lines.append("<!-- Chapter-level references are merged into master refs.bib -->\n")

    ch_md.write_text("\n".join(lines), encoding="utf-8")
    return ch_md


def draft_all_chapters(book_dir, force=False):
    """Draft all chapters in the book from ``chapter_outlines.yaml``.

    Returns list of (chapter_dir_name, path) tuples for drafted chapters.
    """
    book_dir = Path(book_dir)
    outlines = load_chapter_outline(book_dir)
    if not outlines:
        return []

    cfg = load_book_config(book_dir)
    drafted = []

    for _num, ch, _part in iter_chapters(cfg):
        ch_name = ch["dir"]
        if ch_name in outlines:
            path = draft_chapter(book_dir, ch_name, outlines[ch_name], force=force)
            drafted.append((ch_name, path))

    return drafted
