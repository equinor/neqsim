"""
book_render_odf — Render a multi-chapter book to ODF (.odt).

Uses odfpy to produce OpenDocument Text files with:
- Multi-chapter assembly with page breaks
- Headings, paragraphs, bold/italic/code formatting
- Image embedding from chapter figures directories
- Centered figure captions
- Bullet and numbered lists
"""

import re
from pathlib import Path

try:
    from odf.opendocument import OpenDocumentText
    from odf.style import (Style, TextProperties, ParagraphProperties,
                           GraphicProperties, PageLayoutProperties, MasterPage,
                           PageLayout)
    from odf.text import (H, P, Span, List, ListItem, ListLevelStyleBullet,
                          ListLevelStyleNumber, ListStyle)
    from odf.draw import Frame, Image
    HAS_ODF = True
except ImportError:
    HAS_ODF = False

import book_builder

try:
    from math_utils import latex_to_unicode
    HAS_MATH = True
except ImportError:
    HAS_MATH = False


# ---------------------------------------------------------------------------
# Style setup
# ---------------------------------------------------------------------------

def _setup_styles(doc):
    """Create reusable styles for the book document."""
    styles = {}

    # Normal text style
    normal = Style(name="BookNormal", family="paragraph")
    normal.addElement(TextProperties(fontsize="11pt", fontname="Times New Roman"))
    normal.addElement(ParagraphProperties(marginbottom="0.2cm", margintop="0.1cm"))
    doc.styles.addElement(normal)
    styles["normal"] = normal

    # Bold character style
    bold = Style(name="Bold", family="text")
    bold.addElement(TextProperties(fontweight="bold"))
    doc.styles.addElement(bold)
    styles["bold"] = bold

    # Italic character style
    italic = Style(name="Italic", family="text")
    italic.addElement(TextProperties(fontstyle="italic"))
    doc.styles.addElement(italic)
    styles["italic"] = italic

    # Code character style
    code = Style(name="Code", family="text")
    code.addElement(TextProperties(fontname="Courier New", fontsize="9pt"))
    doc.styles.addElement(code)
    styles["code"] = code

    # Code block paragraph style
    code_block = Style(name="CodeBlock", family="paragraph")
    code_block.addElement(TextProperties(fontname="Courier New", fontsize="8pt"))
    code_block.addElement(ParagraphProperties(
        marginbottom="0.3cm", margintop="0.3cm",
        marginleft="1cm",
    ))
    doc.styles.addElement(code_block)
    styles["code_block"] = code_block

    # Caption style (centered, italic, small)
    caption = Style(name="Caption", family="paragraph")
    caption.addElement(TextProperties(fontsize="9pt", fontstyle="italic"))
    caption.addElement(ParagraphProperties(textalign="center",
                                           marginbottom="0.3cm"))
    doc.styles.addElement(caption)
    styles["caption"] = caption

    # Title style
    title_style = Style(name="BookTitle", family="paragraph")
    title_style.addElement(TextProperties(fontsize="24pt", fontweight="bold"))
    title_style.addElement(ParagraphProperties(textalign="center",
                                                marginbottom="1cm"))
    doc.styles.addElement(title_style)
    styles["title"] = title_style

    # Part title style
    part_style = Style(name="PartTitle", family="paragraph")
    part_style.addElement(TextProperties(fontsize="24pt", fontweight="bold"))
    part_style.addElement(ParagraphProperties(textalign="center",
                                               margintop="5cm"))
    doc.styles.addElement(part_style)
    styles["part_title"] = part_style

    # Equation style (centered, Cambria Math / italic serif)
    equation = Style(name="Equation", family="paragraph")
    equation.addElement(TextProperties(fontsize="11pt", fontstyle="italic"))
    equation.addElement(ParagraphProperties(textalign="center",
                                             margintop="0.3cm",
                                             marginbottom="0.3cm"))
    doc.styles.addElement(equation)
    styles["equation"] = equation

    # Blockquote style
    blockquote = Style(name="Blockquote", family="paragraph")
    blockquote.addElement(TextProperties(fontsize="10pt", fontstyle="italic",
                                          color="#555555"))
    blockquote.addElement(ParagraphProperties(marginleft="1cm",
                                               margintop="0.2cm",
                                               marginbottom="0.2cm"))
    doc.styles.addElement(blockquote)
    styles["blockquote"] = blockquote

    # Bullet list style
    bullet_list = ListStyle(name="BulletList")
    bullet_props = ListLevelStyleBullet(level="1", bulletchar="\u2022")
    bullet_props.addElement(TextProperties(fontname="Times New Roman"))
    bullet_list.addElement(bullet_props)
    doc.automaticstyles.addElement(bullet_list)
    styles["bullet_list"] = bullet_list

    # Numbered list style
    num_list = ListStyle(name="NumberedList")
    num_props = ListLevelStyleNumber(level="1", numformat="1", numsuffix=".")
    num_list.addElement(num_props)
    doc.automaticstyles.addElement(num_list)
    styles["num_list"] = num_list

    return styles


# ---------------------------------------------------------------------------
# Page break helper
# ---------------------------------------------------------------------------

def _add_page_break(doc, styles):
    """Add a paragraph that forces a page break before it."""
    pb_style = Style(name="PageBreak", family="paragraph")
    pb_style.addElement(ParagraphProperties(breakbefore="page"))
    doc.automaticstyles.addElement(pb_style)
    p = P(stylename=pb_style)
    doc.text.addElement(p)
    return p


# ---------------------------------------------------------------------------
# Rich text helpers
# ---------------------------------------------------------------------------

def _add_rich_text(element, text, styles):
    """Add text with inline bold, italic, code, and math formatting."""
    # Split inline $...$ math first, then apply formatting to non-math parts
    segments = _split_inline_math_odf(text)
    for content, is_math in segments:
        if is_math:
            display = content
            if HAS_MATH:
                display = latex_to_unicode(content)
            span = Span(stylename=styles["italic"])
            span.addText(display)
            element.addElement(span)
        else:
            _add_rich_text_no_math(element, content, styles)


def _add_rich_text_no_math(element, text, styles):
    """Add text with inline bold, italic, and code formatting (no math)."""
    parts = re.split(r"(\*\*[^*]+\*\*|\*[^*]+\*|`[^`]+`)", text)
    for part in parts:
        if part.startswith("**") and part.endswith("**"):
            span = Span(stylename=styles["bold"])
            span.addText(part[2:-2])
            element.addElement(span)
        elif part.startswith("*") and part.endswith("*"):
            span = Span(stylename=styles["italic"])
            span.addText(part[1:-1])
            element.addElement(span)
        elif part.startswith("`") and part.endswith("`"):
            span = Span(stylename=styles["code"])
            span.addText(part[1:-1])
            element.addElement(span)
        elif part:
            element.addText(part)


def _split_inline_math_odf(text):
    """Split text into (content, is_math) pairs for inline $...$ math."""
    result = []
    pos = 0
    while pos < len(text):
        idx = text.find("$", pos)
        if idx == -1:
            result.append((text[pos:], False))
            break
        if idx + 1 < len(text) and text[idx + 1] == "$":
            result.append((text[pos:idx + 2], False))
            pos = idx + 2
            continue
        if idx > 0 and text[idx - 1] == "\\":
            result.append((text[pos:idx + 1], False))
            pos = idx + 1
            continue
        if idx > pos:
            result.append((text[pos:idx], False))
        close = text.find("$", idx + 1)
        if close == -1:
            result.append((text[idx:], False))
            break
        math_content = text[idx + 1:close]
        if len(math_content) < 200 and "\n" not in math_content:
            result.append((math_content, True))
        else:
            result.append((text[idx:close + 1], False))
        pos = close + 1
    return result


def _strip_html_comments(text):
    """Remove HTML comments from text."""
    return re.sub(r"<!--.*?-->", "", text, flags=re.DOTALL)


# ---------------------------------------------------------------------------
# Markdown → ODF conversion
# ---------------------------------------------------------------------------

def _render_md_to_odf(doc, md_text, styles, chapter_num=None, figures_dir=None):
    """Parse simplified markdown and add elements to the ODF document."""
    lines = md_text.split("\n")
    i = 0
    in_code_block = False
    code_lines = []

    while i < len(lines):
        line = lines[i]

        # Code blocks
        if line.strip().startswith("```"):
            if in_code_block:
                code_text = "\n".join(code_lines)
                p = P(stylename=styles["code_block"])
                p.addText(code_text)
                doc.text.addElement(p)
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

        # Display math: $$ on its own line
        if stripped.startswith("$$"):
            if stripped == "$$":
                eq_lines = []
                i += 1
                while i < len(lines):
                    if lines[i].strip() == "$$":
                        break
                    eq_lines.append(lines[i])
                    i += 1
                i += 1
                latex = "\n".join(eq_lines).strip()
            elif stripped.endswith("$$") and len(stripped) > 4:
                latex = stripped[2:-2].strip()
                i += 1
            else:
                eq_lines = [stripped[2:]]
                i += 1
                while i < len(lines):
                    if lines[i].strip().endswith("$$"):
                        eq_lines.append(lines[i].strip()[:-2])
                        break
                    eq_lines.append(lines[i])
                    i += 1
                i += 1
                latex = "\n".join(eq_lines).strip()

            display_text = latex
            if HAS_MATH:
                display_text = latex_to_unicode(latex)
            p = P(stylename=styles["equation"])
            p.addText(display_text)
            doc.text.addElement(p)
            continue

        # Blockquote
        if stripped.startswith("> "):
            bq_lines = []
            while i < len(lines) and lines[i].strip().startswith("> "):
                bq_lines.append(lines[i].strip()[2:])
                i += 1
            bq_text = " ".join(bq_lines)
            p = P(stylename=styles.get("blockquote", styles["normal"]))
            _add_rich_text(p, bq_text, styles)
            doc.text.addElement(p)
            continue

        # Headings
        heading_match = re.match(r"^(#{1,4})\s+(.+)", line)
        if heading_match:
            level = len(heading_match.group(1))
            text = heading_match.group(2).strip()
            h = H(outlinelevel=level)
            h.addText(text)
            doc.text.addElement(h)
            i += 1
            continue

        # Images: ![caption](path)
        img_match = re.match(r"!\[([^\]]*)\]\(([^)]+)\)", stripped)
        if img_match:
            caption = img_match.group(1)
            img_path = img_match.group(2)
            if figures_dir:
                full_path = Path(figures_dir) / Path(img_path).name
                if full_path.exists():
                    try:
                        href = doc.addPicture(str(full_path))
                        frame = Frame(width="15cm", height="10cm")
                        frame.addElement(Image(href=href))
                        p = P()
                        p.addElement(frame)
                        doc.text.addElement(p)
                    except Exception:
                        p = P(stylename=styles["normal"])
                        p.addText("[Figure: {}]".format(img_path))
                        doc.text.addElement(p)
                else:
                    p = P(stylename=styles["normal"])
                    p.addText("[Figure: {}]".format(img_path))
                    doc.text.addElement(p)
            else:
                p = P(stylename=styles["normal"])
                p.addText("[Figure: {}]".format(img_path))
                doc.text.addElement(p)
            if caption:
                cap_p = P(stylename=styles["caption"])
                cap_p.addText(caption)
                doc.text.addElement(cap_p)
            i += 1
            continue

        # Italic caption lines: *Figure X.Y: ...*
        if stripped.startswith("*") and stripped.endswith("*") and len(stripped) > 2:
            p = P(stylename=styles["caption"])
            p.addText(stripped[1:-1])
            doc.text.addElement(p)
            i += 1
            continue

        # Bullet list
        if stripped.startswith("- ") or stripped.startswith("* "):
            text = stripped[2:]
            lst = List(stylename=styles["bullet_list"])
            item = ListItem()
            item_p = P()
            _add_rich_text(item_p, text, styles)
            item.addElement(item_p)
            lst.addElement(item)
            # Consume consecutive bullet items
            i += 1
            while i < len(lines):
                next_stripped = lines[i].strip()
                if next_stripped.startswith("- ") or next_stripped.startswith("* "):
                    item = ListItem()
                    item_p = P()
                    _add_rich_text(item_p, next_stripped[2:], styles)
                    item.addElement(item_p)
                    lst.addElement(item)
                    i += 1
                else:
                    break
            doc.text.addElement(lst)
            continue

        # Numbered list
        num_match = re.match(r"^(\d+)\.\s+(.+)", stripped)
        if num_match:
            text = num_match.group(2)
            lst = List(stylename=styles["num_list"])
            item = ListItem()
            item_p = P()
            _add_rich_text(item_p, text, styles)
            item.addElement(item_p)
            lst.addElement(item)
            # Consume consecutive numbered items
            i += 1
            while i < len(lines):
                next_match = re.match(r"^(\d+)\.\s+(.+)", lines[i].strip())
                if next_match:
                    item = ListItem()
                    item_p = P()
                    _add_rich_text(item_p, next_match.group(2), styles)
                    item.addElement(item_p)
                    lst.addElement(item)
                    i += 1
                else:
                    break
            doc.text.addElement(lst)
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
        p = P(stylename=styles["normal"])
        _add_rich_text(p, para_text, styles)
        doc.text.addElement(p)


# ---------------------------------------------------------------------------
# Public API
# ---------------------------------------------------------------------------

def render_book_odf(book_dir, chapter_filter=None):
    """Render the book (or a single chapter) to an ODF document (.odt).

    Parameters
    ----------
    book_dir : str or Path
        Path to the book project directory.
    chapter_filter : str or None
        If given, render only the chapter whose ``dir`` matches.

    Returns
    -------
    Path or None
        Path to the generated .odt file, or None on failure.
    """
    if not HAS_ODF:
        print("[book_render_odf] Error: odfpy not installed. Run: pip install odfpy")
        return None

    book_dir = Path(book_dir)
    cfg = book_builder.load_book_config(book_dir)

    doc = OpenDocumentText()
    styles = _setup_styles(doc)

    # ── Title page ──
    if not chapter_filter:
        title_md = book_dir / "frontmatter" / "title_page.md"
        if title_md.exists():
            text = _strip_html_comments(title_md.read_text(encoding="utf-8"))
            _render_md_to_odf(doc, text, styles)
        else:
            p = P(stylename=styles["title"])
            p.addText(cfg.get("title", "Untitled"))
            doc.text.addElement(p)
            authors = cfg.get("authors", [])
            if authors:
                names = ", ".join(a.get("name", "") for a in authors)
                p = P(stylename=styles["caption"])
                p.addText(names)
                doc.text.addElement(p)
        _add_page_break(doc, styles)

        # ── Preface and other frontmatter ──
        for fm in cfg.get("frontmatter", []):
            if fm in ("title_page", "copyright"):
                continue
            fm_path = book_dir / "frontmatter" / f"{fm}.md"
            if fm_path.exists():
                text = _strip_html_comments(fm_path.read_text(encoding="utf-8"))
                _render_md_to_odf(doc, text, styles)
                _add_page_break(doc, styles)

    # ── Chapters ──
    prev_part = None
    for ch_num, ch, part_title in book_builder.iter_chapters(cfg):
        if chapter_filter and ch["dir"] != chapter_filter:
            continue

        # Part separator page
        if not chapter_filter and part_title and part_title != prev_part:
            _add_page_break(doc, styles)
            p = P(stylename=styles["part_title"])
            p.addText(part_title)
            doc.text.addElement(p)
            _add_page_break(doc, styles)
            prev_part = part_title

        ch_dir = book_builder.resolve_chapter_dir(book_dir, ch)
        ch_md = ch_dir / "chapter.md"

        if not ch_md.exists():
            h = H(outlinelevel=1)
            h.addText("Chapter {}: {}".format(ch_num, ch.get("title", "Untitled")))
            doc.text.addElement(h)
            p = P(stylename=styles["normal"])
            p.addText("Content not yet written.")
            doc.text.addElement(p)
            continue

        text = ch_md.read_text(encoding="utf-8")
        text = _strip_html_comments(text)

        figures_dir = ch_dir / "figures"
        _render_md_to_odf(doc, text, styles, chapter_num=ch_num,
                          figures_dir=figures_dir if figures_dir.exists() else None)

    # ── Backmatter ──
    if not chapter_filter:
        for bm in cfg.get("backmatter", []):
            bm_path = book_dir / "backmatter" / f"{bm}.md"
            if bm_path.exists():
                _add_page_break(doc, styles)
                text = _strip_html_comments(bm_path.read_text(encoding="utf-8"))
                _render_md_to_odf(doc, text, styles)

    # ── Save ──
    submission_dir = book_dir / "submission"
    submission_dir.mkdir(parents=True, exist_ok=True)

    out_name = "book.odt" if not chapter_filter else f"{chapter_filter}.odt"
    out_path = submission_dir / out_name

    try:
        doc.save(str(out_path))
    except PermissionError:
        from datetime import datetime
        ts = datetime.now().strftime("%H%M%S")
        out_path = submission_dir / f"book_{ts}.odt"
        doc.save(str(out_path))
        print(f"  NOTE: book.odt was locked, saved as {out_path.name}")

    size_kb = out_path.stat().st_size / 1024
    print("[book_render_odf] ODF document generated: {}  ({:.0f} KB)".format(out_path, size_kb))
    return out_path
