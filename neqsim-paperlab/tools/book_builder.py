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
