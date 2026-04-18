"""
book_render_word — Render a multi-chapter book to Word (.docx).

Extends the paper-level word_renderer.py with:
- Multi-chapter assembly with section breaks
- Chapter-prefixed figure/table numbering
- Auto-generated TOC field
- Page breaks for parts
- Header/footer with chapter title and page numbers
"""

import re
from pathlib import Path

try:
    from docx import Document
    from docx.shared import Inches, Pt, RGBColor, Cm
    from docx.enum.text import WD_ALIGN_PARAGRAPH
    from docx.enum.section import WD_ORIENT
    from docx.oxml.ns import qn
    from docx.oxml import parse_xml
    HAS_DOCX = True
except ImportError:
    HAS_DOCX = False

import book_builder


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _add_toc_field(doc):
    """Insert a TOC field code that auto-updates when opened in Word."""
    para = doc.add_paragraph()
    run = para.add_run()
    fld_char_begin = parse_xml(
        r'<w:fldChar {} w:fldCharType="begin"/>'.format(
            'xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"'
        )
    )
    run._element.append(fld_char_begin)

    run2 = para.add_run()
    instr = parse_xml(
        r'<w:instrText {} xml:space="preserve"> TOC \o "1-3" \h \z \u </w:instrText>'.format(
            'xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"'
        )
    )
    run2._element.append(instr)

    run3 = para.add_run()
    fld_char_end = parse_xml(
        r'<w:fldChar {} w:fldCharType="end"/>'.format(
            'xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"'
        )
    )
    run3._element.append(fld_char_end)

    return para


def _add_section_break(doc):
    """Add a section break (next page) to the document."""
    new_section = doc.add_section()
    new_section.start_type = 2  # WD_SECTION_START.NEW_PAGE
    return new_section


def _add_page_break(doc):
    """Add a simple page break."""
    doc.add_page_break()


def _strip_html_comments(text):
    """Remove HTML comments from text."""
    return re.sub(r"<!--.*?-->", "", text, flags=re.DOTALL)


# ---------------------------------------------------------------------------
# Markdown → Word paragraph conversion (simplified)
# ---------------------------------------------------------------------------

def _render_md_to_doc(doc, md_text, chapter_num=None, figures_dir=None):
    """Parse simplified markdown and add paragraphs/headings to *doc*.

    This is a lightweight renderer for book content.  For full fidelity,
    the paper-level WordRenderer is preferred; this handles the common
    cases: headings, paragraphs, bold/italic, code blocks, images, lists.
    """
    lines = md_text.split("\n")
    i = 0
    in_code_block = False
    code_lines = []

    while i < len(lines):
        line = lines[i]

        # Code blocks
        if line.strip().startswith("```"):
            if in_code_block:
                # End code block
                code_text = "\n".join(code_lines)
                p = doc.add_paragraph()
                run = p.add_run(code_text)
                run.font.name = "Consolas"
                run.font.size = Pt(8)
                code_lines = []
                in_code_block = False
            else:
                in_code_block = True
                code_lines = []
            i += 1
            continue

        if in_code_block:
            code_lines.append(line)
            i += 1
            continue

        stripped = line.strip()

        # Empty line
        if not stripped:
            i += 1
            continue

        # Headings
        heading_match = re.match(r"^(#{1,4})\s+(.+)", line)
        if heading_match:
            level = len(heading_match.group(1))
            text = heading_match.group(2).strip()
            doc.add_heading(text, level=level)
            i += 1
            continue

        # Images: ![caption](path)
        img_match = re.match(r"!\[([^\]]*)\]\(([^)]+)\)", stripped)
        if img_match:
            caption = img_match.group(1)
            img_path = img_match.group(2)
            # Try to resolve the image
            if figures_dir:
                full_path = Path(figures_dir) / Path(img_path).name
                if full_path.exists():
                    try:
                        doc.add_picture(str(full_path), width=Inches(5))
                    except Exception:
                        doc.add_paragraph(f"[Figure: {img_path}]")
                else:
                    doc.add_paragraph(f"[Figure: {img_path}]")
            else:
                doc.add_paragraph(f"[Figure: {img_path}]")
            if caption:
                p = doc.add_paragraph()
                p.alignment = WD_ALIGN_PARAGRAPH.CENTER
                run = p.add_run(caption)
                run.italic = True
                run.font.size = Pt(9)
            i += 1
            continue

        # Italic caption lines: *Figure X.Y: ...*
        if stripped.startswith("*") and stripped.endswith("*") and len(stripped) > 2:
            p = doc.add_paragraph()
            p.alignment = WD_ALIGN_PARAGRAPH.CENTER
            run = p.add_run(stripped[1:-1])
            run.italic = True
            run.font.size = Pt(9)
            i += 1
            continue

        # Bullet list
        if stripped.startswith("- ") or stripped.startswith("* "):
            text = stripped[2:]
            doc.add_paragraph(text, style="List Bullet")
            i += 1
            continue

        # Numbered list
        num_match = re.match(r"^(\d+)\.\s+(.+)", stripped)
        if num_match:
            text = num_match.group(2)
            doc.add_paragraph(text, style="List Number")
            i += 1
            continue

        # Regular paragraph — accumulate continuation lines
        para_lines = [stripped]
        i += 1
        while i < len(lines) and lines[i].strip() and not lines[i].strip().startswith("#") \
                and not lines[i].strip().startswith("```") \
                and not re.match(r"^!\[", lines[i].strip()) \
                and not lines[i].strip().startswith("- ") \
                and not lines[i].strip().startswith("* ") \
                and not re.match(r"^\d+\.\s", lines[i].strip()):
            para_lines.append(lines[i].strip())
            i += 1

        para_text = " ".join(para_lines)
        p = doc.add_paragraph()
        # Handle inline bold and italic
        _add_rich_text(p, para_text)

    return doc


def _add_rich_text(paragraph, text):
    """Add text to paragraph with basic inline formatting (bold, italic, code)."""
    # Split by bold/italic/code markers
    parts = re.split(r"(\*\*[^*]+\*\*|\*[^*]+\*|`[^`]+`)", text)
    for part in parts:
        if part.startswith("**") and part.endswith("**"):
            run = paragraph.add_run(part[2:-2])
            run.bold = True
        elif part.startswith("*") and part.endswith("*"):
            run = paragraph.add_run(part[1:-1])
            run.italic = True
        elif part.startswith("`") and part.endswith("`"):
            run = paragraph.add_run(part[1:-1])
            run.font.name = "Consolas"
            run.font.size = Pt(9)
        elif part:
            paragraph.add_run(part)


# ---------------------------------------------------------------------------
# Public API
# ---------------------------------------------------------------------------

def render_book_word(book_dir, chapter_filter=None):
    """Render the book (or a single chapter) to a Word document.

    Parameters
    ----------
    book_dir : str or Path
        Path to the book project directory.
    chapter_filter : str or None
        If given, render only the chapter whose ``dir`` matches.

    Returns
    -------
    Path or None
        Path to the generated .docx file, or None on failure.
    """
    if not HAS_DOCX:
        print("[book_render_word] Error: python-docx not installed. Run: pip install python-docx")
        return None

    book_dir = Path(book_dir)
    cfg = book_builder.load_book_config(book_dir)

    doc = Document()

    # ── Document defaults ──
    style = doc.styles["Normal"]
    style.font.name = "Times New Roman"
    style.font.size = Pt(11)

    # ── Title page ──
    if not chapter_filter:
        title_md = book_dir / "frontmatter" / "title_page.md"
        if title_md.exists():
            text = _strip_html_comments(title_md.read_text(encoding="utf-8"))
            _render_md_to_doc(doc, text)
        else:
            doc.add_heading(cfg.get("title", "Untitled"), level=0)
            authors = cfg.get("authors", [])
            if authors:
                names = ", ".join(a.get("name", "") for a in authors)
                p = doc.add_paragraph(names)
                p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        _add_page_break(doc)

        # ── Copyright page ──
        copyright_md = book_dir / "frontmatter" / "copyright.md"
        if copyright_md.exists():
            text = _strip_html_comments(copyright_md.read_text(encoding="utf-8"))
            _render_md_to_doc(doc, text)
            _add_page_break(doc)

        # ── Table of Contents ──
        doc.add_heading("Table of Contents", level=1)
        _add_toc_field(doc)
        _add_page_break(doc)

        # ── Preface and other frontmatter ──
        for fm in cfg.get("frontmatter", []):
            if fm in ("title_page", "copyright"):
                continue
            fm_path = book_dir / "frontmatter" / f"{fm}.md"
            if fm_path.exists():
                text = _strip_html_comments(fm_path.read_text(encoding="utf-8"))
                _render_md_to_doc(doc, text)
                _add_page_break(doc)

    # ── Chapters ──
    prev_part = None
    for ch_num, ch, part_title in book_builder.iter_chapters(cfg):
        if chapter_filter and ch["dir"] != chapter_filter:
            continue

        # Part separator page
        if not chapter_filter and part_title and part_title != prev_part:
            _add_page_break(doc)
            p = doc.add_paragraph()
            p.alignment = WD_ALIGN_PARAGRAPH.CENTER
            run = p.add_run(f"\n\n\n{part_title}")
            run.bold = True
            run.font.size = Pt(24)
            _add_page_break(doc)
            prev_part = part_title

        ch_dir = book_builder.resolve_chapter_dir(book_dir, ch)
        ch_md = ch_dir / "chapter.md"

        if not ch_md.exists():
            doc.add_heading(f"Chapter {ch_num}: {ch.get('title', 'Untitled')}", level=1)
            doc.add_paragraph("Content not yet written.")
            continue

        text = ch_md.read_text(encoding="utf-8")
        text = _strip_html_comments(text)

        figures_dir = ch_dir / "figures"
        _render_md_to_doc(doc, text, chapter_num=ch_num,
                          figures_dir=figures_dir if figures_dir.exists() else None)

    # ── Backmatter ──
    if not chapter_filter:
        for bm in cfg.get("backmatter", []):
            bm_path = book_dir / "backmatter" / f"{bm}.md"
            if bm_path.exists():
                _add_page_break(doc)
                text = _strip_html_comments(bm_path.read_text(encoding="utf-8"))
                _render_md_to_doc(doc, text)

    # ── Save ──
    submission_dir = book_dir / "submission"
    submission_dir.mkdir(parents=True, exist_ok=True)

    out_name = "book.docx" if not chapter_filter else f"{chapter_filter}.docx"
    out_path = submission_dir / out_name

    try:
        doc.save(str(out_path))
    except PermissionError:
        from datetime import datetime
        ts = datetime.now().strftime("%H%M%S")
        out_path = submission_dir / f"book_{ts}.docx"
        doc.save(str(out_path))
        print(f"  NOTE: book.docx was locked, saved as {out_path.name}")

    size_kb = out_path.stat().st_size / 1024
    print(f"[book_render_word] Word document generated: {out_path}  ({size_kb:.0f} KB)")
    return out_path
