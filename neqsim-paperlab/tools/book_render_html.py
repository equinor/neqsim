"""
book_render_html — Render a multi-chapter book to HTML.

Extends render_html_generic.py with:
- Multi-chapter HTML with navigation sidebar
- KaTeX for math rendering
- Syntax-highlighted code blocks
- Responsive design
- Citation resolution and bibliography generation
"""

import re
import shutil
from pathlib import Path

import book_builder
from citation_utils import (parse_bibtex, collect_all_cited_keys_from_chapters,
                            resolve_citations_numbered_html, collect_cited_keys)


# ---------------------------------------------------------------------------
# HTML building blocks
# ---------------------------------------------------------------------------

def _html_head(title):
    """Return the <head> block with KaTeX CDN and book-specific CSS."""
    return f"""<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8"/>
<meta name="viewport" content="width=device-width, initial-scale=1.0"/>
<title>{_esc(title)}</title>
<link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/katex.min.css"/>
<script defer src="https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/katex.min.js"></script>
<script defer src="https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/contrib/auto-render.min.js"
  onload="renderMathInElement(document.body, {{delimiters:[
    {{left:'$$',right:'$$',display:true}},
    {{left:'$',right:'$',display:false}}
  ]}});"></script>
<style>
* {{ box-sizing: border-box; margin: 0; padding: 0; }}
body {{
  font-family: Georgia, 'Times New Roman', serif;
  color: #222;
  background: #f9f9f9;
  display: flex;
  min-height: 100vh;
}}

/* Sidebar */
nav.sidebar {{
  width: 280px;
  min-width: 280px;
  background: #fff;
  border-right: 1px solid #ddd;
  padding: 1.5rem 1rem;
  position: sticky;
  top: 0;
  height: 100vh;
  overflow-y: auto;
  font-size: 0.9rem;
}}
nav.sidebar h2 {{
  font-size: 1.1rem;
  color: #1a5276;
  margin-bottom: 1rem;
  padding-bottom: 0.5rem;
  border-bottom: 2px solid #1a5276;
}}
nav.sidebar ul {{
  list-style: none;
  padding: 0;
}}
nav.sidebar li {{
  margin-bottom: 0.3rem;
}}
nav.sidebar li.part {{
  font-weight: bold;
  margin-top: 1rem;
  color: #1a5276;
}}
nav.sidebar a {{
  color: #444;
  text-decoration: none;
}}
nav.sidebar a:hover {{
  color: #1a5276;
  text-decoration: underline;
}}

/* Main content */
main {{
  flex: 1;
  max-width: 900px;
  margin: 0 auto;
  padding: 2rem 2.5rem;
  background: #fff;
}}
main h1 {{
  font-size: 1.8rem;
  color: #1a5276;
  margin: 2rem 0 1rem;
  padding-top: 1rem;
  border-top: 2px solid #eee;
}}
main h1:first-child {{ border-top: none; padding-top: 0; }}
main h2 {{
  font-size: 1.4rem;
  color: #2c3e50;
  margin: 1.5rem 0 0.7rem;
}}
main h3 {{
  font-size: 1.15rem;
  color: #34495e;
  margin: 1.2rem 0 0.5rem;
}}
main p {{
  line-height: 1.7;
  margin-bottom: 0.8rem;
  text-align: justify;
}}
main img {{
  max-width: 100%;
  height: auto;
  display: block;
  margin: 1rem auto;
}}
main pre {{
  background: #f5f5f5;
  padding: 1rem;
  border-radius: 4px;
  overflow-x: auto;
  font-size: 0.85rem;
  margin: 1rem 0;
}}
main code {{
  font-family: 'Consolas', 'Courier New', monospace;
  background: #f0f0f0;
  padding: 0.15em 0.35em;
  border-radius: 3px;
  font-size: 0.9em;
}}
main pre code {{
  background: none;
  padding: 0;
}}
main table {{
  border-collapse: collapse;
  margin: 1.5rem auto;
  font-size: 0.9rem;
  width: auto;
}}
main th, main td {{
  padding: 0.45rem 1rem;
  text-align: left;
  border-left: none;
  border-right: none;
}}
main thead tr {{
  border-top: 2px solid #222;
  border-bottom: 1px solid #222;
}}
main tbody tr:last-child {{
  border-bottom: 2px solid #222;
}}
main th {{
  font-weight: bold;
  color: #1a1a1a;
}}
main figure {{
  margin: 1.5rem auto;
  text-align: center;
}}
main figcaption {{
  font-size: 0.88rem;
  color: #444;
  margin-top: 0.4rem;
  font-style: italic;
  text-align: center;
}}
main .katex-display {{
  margin: 1.2rem 0;
  font-size: 1.05em;
}}
main blockquote {{
  border-left: 4px solid #1a5276;
  margin: 1rem 0;
  padding: 0.5rem 1rem;
  color: #555;
  background: #f9f9f9;
}}
main ul, main ol {{
  margin: 0.5rem 0 0.5rem 1.5rem;
  line-height: 1.7;
}}

/* ── Professional frontmatter pages ── */

.title-page {{
  min-height: 90vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 3rem 2rem;
  position: relative;
  page-break-after: always;
}}
.title-page::before {{
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 8px;
  background: linear-gradient(90deg, #0d3b66, #1a5276, #2980b9, #1a5276, #0d3b66);
}}
.title-page::after {{
  content: '';
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 4px;
  background: linear-gradient(90deg, #0d3b66, #1a5276, #2980b9, #1a5276, #0d3b66);
}}
.title-page .tp-decoration {{
  width: 120px;
  height: 120px;
  margin: 0 auto 2rem;
  position: relative;
}}
.title-page .tp-decoration svg {{
  width: 100%;
  height: 100%;
}}
.title-page .tp-title {{
  font-size: 2.2rem;
  font-weight: bold;
  color: #0d3b66;
  line-height: 1.25;
  margin-bottom: 0.8rem;
  letter-spacing: -0.02em;
  max-width: 600px;
}}
.title-page .tp-subtitle {{
  font-size: 1.15rem;
  color: #555;
  font-style: italic;
  margin-bottom: 2.5rem;
  max-width: 500px;
  line-height: 1.5;
}}
.title-page .tp-rule {{
  width: 200px;
  height: 2px;
  background: linear-gradient(90deg, transparent, #1a5276, transparent);
  margin: 0 auto 2.5rem;
}}
.title-page .tp-author {{
  font-size: 1.3rem;
  color: #222;
  margin-bottom: 0.3rem;
  font-variant: small-caps;
  letter-spacing: 0.05em;
}}
.title-page .tp-affiliation {{
  font-size: 0.95rem;
  color: #666;
  margin-bottom: 2rem;
}}
.title-page .tp-edition {{
  font-size: 0.9rem;
  color: #888;
  margin-bottom: 0.3rem;
}}
.title-page .tp-year {{
  font-size: 0.9rem;
  color: #888;
  margin-bottom: 2rem;
}}
.title-page .tp-publisher {{
  font-size: 1rem;
  color: #444;
  letter-spacing: 0.15em;
  text-transform: uppercase;
  font-weight: 600;
}}

.half-title-page {{
  min-height: 60vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 5rem 2rem;
  page-break-after: always;
}}
.half-title-page .htp-title {{
  font-size: 1.6rem;
  color: #1a5276;
  font-weight: bold;
  letter-spacing: -0.01em;
}}

.copyright-page {{
  min-height: 80vh;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  padding: 2rem 3rem 4rem;
  font-size: 0.85rem;
  color: #555;
  line-height: 1.8;
  page-break-after: always;
}}
.copyright-page .cp-title {{
  font-weight: bold;
  color: #222;
  font-size: 0.95rem;
  margin-bottom: 1rem;
}}
.copyright-page p {{
  margin-bottom: 0.8rem;
  text-align: left;
}}
.copyright-page .cp-isbn {{
  font-family: 'Consolas', monospace;
  color: #444;
}}

.dedication-page {{
  min-height: 60vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 5rem 3rem;
  page-break-after: always;
}}
.dedication-page p {{
  font-style: italic;
  color: #555;
  font-size: 1.05rem;
  max-width: 400px;
  line-height: 1.8;
}}

section.part-header {{
  min-height: 50vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  page-break-before: always;
  page-break-after: always;
}}
section.part-header h1 {{
  font-size: 1.6rem;
  color: #1a5276;
  border-top: none;
  padding-top: 0;
  letter-spacing: 0.05em;
  font-variant: small-caps;
  position: relative;
}}
section.part-header h1::after {{
  content: '';
  display: block;
  width: 100px;
  height: 2px;
  background: linear-gradient(90deg, transparent, #1a5276, transparent);
  margin: 1rem auto 0;
}}

/* Responsive */
@media (max-width: 900px) {{
  nav.sidebar {{ display: none; }}
  main {{ padding: 1rem; }}
  .title-page .tp-title {{ font-size: 1.6rem; }}
  .title-page .tp-decoration {{ width: 80px; height: 80px; }}
}}

@media print {{
  nav.sidebar {{ display: none; }}
  body {{ background: #fff; }}
  main {{ max-width: 100%; padding: 0; }}
  .title-page {{ min-height: auto; padding: 8rem 2rem; }}
  .title-page::before, .title-page::after {{ display: none; }}
}}
</style>
</head>
"""


def _esc(text):
    """HTML-escape text."""
    return (text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace('"', "&quot;"))


# ---------------------------------------------------------------------------
# Professional frontmatter page generators
# ---------------------------------------------------------------------------

_BOOK_ICON_SVG = (
    '<svg viewBox="0 0 120 120" xmlns="http://www.w3.org/2000/svg">'
    '<defs>'
    '<linearGradient id="g1" x1="0%" y1="0%" x2="100%" y2="100%">'
    '<stop offset="0%" style="stop-color:#0d3b66;stop-opacity:1"/>'
    '<stop offset="100%" style="stop-color:#2980b9;stop-opacity:1"/>'
    '</linearGradient>'
    '<linearGradient id="g2" x1="0%" y1="100%" x2="100%" y2="0%">'
    '<stop offset="0%" style="stop-color:#2980b9;stop-opacity:0.3"/>'
    '<stop offset="100%" style="stop-color:#0d3b66;stop-opacity:0.3"/>'
    '</linearGradient>'
    '</defs>'
    # Outer ring
    '<circle cx="60" cy="60" r="56" fill="none" stroke="url(#g1)" stroke-width="3"/>'
    # Inner dashed ring
    '<circle cx="60" cy="60" r="48" fill="none" stroke="#1a5276" stroke-width="0.8" '
    'stroke-dasharray="3,3" opacity="0.3"/>'
    # Subtle background fill
    '<circle cx="60" cy="60" r="47" fill="url(#g2)"/>'
    # Network connections (behind nodes)
    '<line x1="60" y1="30" x2="60" y2="60" stroke="#1a5276" stroke-width="2.2"/>'
    '<line x1="34" y1="75" x2="60" y2="60" stroke="#1a5276" stroke-width="2.2"/>'
    '<line x1="86" y1="75" x2="60" y2="60" stroke="#1a5276" stroke-width="2.2"/>'
    '<line x1="60" y1="30" x2="34" y2="75" stroke="#0d3b66" stroke-width="1.2" opacity="0.35"/>'
    '<line x1="60" y1="30" x2="86" y2="75" stroke="#0d3b66" stroke-width="1.2" opacity="0.35"/>'
    '<line x1="34" y1="75" x2="86" y2="75" stroke="#0d3b66" stroke-width="1.2" opacity="0.35"/>'
    # Outer orbit dots
    '<circle cx="60" cy="18" r="2" fill="#2980b9" opacity="0.5"/>'
    '<circle cx="24" cy="80" r="2" fill="#2980b9" opacity="0.5"/>'
    '<circle cx="96" cy="80" r="2" fill="#2980b9" opacity="0.5"/>'
    '<circle cx="95" cy="38" r="2" fill="#0d3b66" opacity="0.4"/>'
    '<circle cx="25" cy="38" r="2" fill="#0d3b66" opacity="0.4"/>'
    # Central node (AI core)
    '<circle cx="60" cy="60" r="10" fill="#1a5276"/>'
    '<circle cx="60" cy="60" r="6" fill="#fff" opacity="0.15"/>'
    # Primary nodes
    '<circle cx="60" cy="30" r="7" fill="#0d3b66"/>'
    '<circle cx="34" cy="75" r="7" fill="#2980b9"/>'
    '<circle cx="86" cy="75" r="7" fill="#2980b9"/>'
    # Node highlights
    '<circle cx="58" cy="28" r="2" fill="#fff" opacity="0.25"/>'
    '<circle cx="32" cy="73" r="2" fill="#fff" opacity="0.25"/>'
    '<circle cx="84" cy="73" r="2" fill="#fff" opacity="0.25"/>'
    # Interstitial satellite dots
    '<circle cx="47" cy="45" r="3" fill="#2980b9" opacity="0.55"/>'
    '<circle cx="73" cy="45" r="3" fill="#2980b9" opacity="0.55"/>'
    '<circle cx="47" cy="68" r="3" fill="#0d3b66" opacity="0.55"/>'
    '<circle cx="73" cy="68" r="3" fill="#0d3b66" opacity="0.55"/>'
    '</svg>'
)


def _generate_title_page(cfg):
    """Generate a professional graphical title page from book config."""
    title = cfg.get("title", "Untitled")
    subtitle = cfg.get("subtitle", "")
    authors = cfg.get("authors", [])
    edition = cfg.get("edition", "")
    year = cfg.get("year", "")
    publisher = cfg.get("publisher", "")

    parts = ['<section id="title_page" class="title-page">']
    parts.append(f'<div class="tp-decoration">{_BOOK_ICON_SVG}</div>')
    parts.append(f'<div class="tp-title">{_esc(title)}</div>')
    if subtitle:
        parts.append(f'<div class="tp-subtitle">{_esc(subtitle)}</div>')
    parts.append('<div class="tp-rule"></div>')
    for a in authors:
        name = a.get("name", "")
        aff = a.get("affiliation", "")
        parts.append(f'<div class="tp-author">{_esc(name)}</div>')
        if aff:
            parts.append(f'<div class="tp-affiliation">{_esc(aff)}</div>')
    if edition:
        parts.append(f'<div class="tp-edition">{_esc(edition)} Edition</div>')
    if year:
        parts.append(f'<div class="tp-year">{_esc(str(year))}</div>')
    if publisher:
        parts.append(f'<div class="tp-publisher">{_esc(publisher)}</div>')
    parts.append("</section>")
    return "\n".join(parts)


def _generate_half_title(cfg):
    """Generate a half-title page (just the title, minimal)."""
    title = cfg.get("title", "Untitled")
    return (
        '<section class="half-title-page">'
        f'<div class="htp-title">{_esc(title)}</div>'
        '</section>'
    )


def _generate_copyright_page(cfg, book_dir):
    """Generate a professional copyright page."""
    title = cfg.get("title", "Untitled")
    subtitle = cfg.get("subtitle", "")
    authors = cfg.get("authors", [])
    year = cfg.get("year", "")
    isbn = cfg.get("isbn", "")
    publisher = cfg.get("publisher", "")

    author_names = ", ".join(a.get("name", "") for a in authors)

    # Try to read custom copyright text
    cp_path = book_dir / "frontmatter" / "copyright.md"
    custom_text = ""
    if cp_path.exists():
        raw = cp_path.read_text(encoding="utf-8").strip()
        # Skip if it's just the template placeholder
        if "TODO" not in raw and len(raw) > 50:
            custom_text = raw

    parts = ['<section id="copyright" class="copyright-page">']
    parts.append('<div>')
    full_title = title
    if subtitle:
        full_title += f": {subtitle}"
    parts.append(f'<p class="cp-title">{_esc(full_title)}</p>')
    if author_names:
        parts.append(f'<p>Copyright &copy; {_esc(str(year))} Equinor ASA and the '
                      'Norwegian University of Science and Technology (NTNU). '
                      'All rights reserved.</p>')
    parts.append(
        '<p>This work is the intellectual property of Equinor ASA and NTNU. '
        'No part of this publication may be reproduced, stored in a '
        'retrieval system, or transmitted, in any form or by any means, '
        'electronic, mechanical, photocopying, recording, or otherwise, '
        'without the prior written permission of Equinor ASA and NTNU.</p>'
    )
    if isbn:
        parts.append(f'<p class="cp-isbn">ISBN {_esc(isbn)}</p>')
    if publisher:
        pub_display = publisher.title() if publisher.islower() else publisher
        parts.append(f'<p>Published by {_esc(pub_display)}</p>')
    parts.append(
        '<p>The NeqSim library is open-source software released under the '
        'Apache License 2.0. All code examples in this book are available at '
        '<a href="https://github.com/equinor/neqsim">'
        'https://github.com/equinor/neqsim</a> '
        'and may be freely used and modified under the terms of that '
        'license.</p>'
    )
    parts.append(
        '<p style="margin-top: 1.5rem; font-size: 0.8rem; color: #999;">'
        'Typeset using NeqSim PaperLab</p>'
    )
    parts.append('</div>')
    parts.append('</section>')
    return "\n".join(parts)


def _generate_dedication_page(book_dir):
    """Generate a dedication page from dedication.md content."""
    ded_path = book_dir / "frontmatter" / "dedication.md"
    if not ded_path.exists():
        return ""
    text = ded_path.read_text(encoding="utf-8").strip()
    if not text or "TODO" in text:
        return ""
    # Strip markdown heading if present
    text = re.sub(r"^##?\s+.*\n*", "", text).strip()
    # Remove surrounding italics markers
    text = re.sub(r"^\*(.+)\*$", r"\1", text, flags=re.DOTALL)
    return (
        '<section id="dedication" class="dedication-page">'
        f'<p>{text}</p>'
        '</section>'
    )


# ---------------------------------------------------------------------------
# Markdown → HTML conversion (lightweight)
# ---------------------------------------------------------------------------

def _md_to_html(md_text):
    """Convert simplified markdown to HTML.

    Handles headings, paragraphs, code blocks, images, lists, bold/italic,
    inline code, and tables.  Math delimiters ($...$, $$...$$) are left
    intact for KaTeX auto-render.
    """
    lines = md_text.split("\n")
    html_parts = []
    i = 0
    in_code = False
    in_list = None  # "ul" or "ol" or None

    def close_list():
        nonlocal in_list
        if in_list:
            html_parts.append(f"</{in_list}>")
            in_list = None

    while i < len(lines):
        line = lines[i]

        # Code blocks
        if line.strip().startswith("```"):
            if in_code:
                html_parts.append("</code></pre>")
                in_code = False
            else:
                close_list()
                lang = line.strip()[3:].strip()
                html_parts.append(f'<pre><code class="language-{_esc(lang)}">')
                in_code = True
            i += 1
            continue

        if in_code:
            html_parts.append(_esc(line))
            html_parts.append("\n")
            i += 1
            continue

        stripped = line.strip()

        # Empty line
        if not stripped:
            close_list()
            i += 1
            continue

        # Headings
        hm = re.match(r"^(#{1,4})\s+(.+)", line)
        if hm:
            close_list()
            level = len(hm.group(1))
            text = hm.group(2).strip()
            anchor = re.sub(r"[^a-z0-9]+", "-", text.lower()).strip("-")
            html_parts.append(f'<h{level} id="{anchor}">{_inline_fmt(text)}</h{level}>')
            i += 1
            continue

        # Images
        img_m = re.match(r"!\[([^\]]*)\]\(([^)]+)\)", stripped)
        if img_m:
            close_list()
            caption = img_m.group(1)
            src = img_m.group(2)
            html_parts.append(f'<figure><img src="{_esc(src)}" alt="{_esc(caption)}"/>')
            if caption:
                html_parts.append(f"<figcaption>{_inline_fmt(caption)}</figcaption>")
            html_parts.append("</figure>")
            i += 1
            continue

        # Italic caption: *Figure ...*
        if stripped.startswith("*") and stripped.endswith("*") and len(stripped) > 2:
            close_list()
            html_parts.append(f"<p><em>{_inline_fmt(stripped[1:-1])}</em></p>")
            i += 1
            continue

        # Bullet list
        if stripped.startswith("- ") or stripped.startswith("* "):
            if in_list != "ul":
                close_list()
                html_parts.append("<ul>")
                in_list = "ul"
            html_parts.append(f"<li>{_inline_fmt(stripped[2:])}</li>")
            i += 1
            continue

        # Numbered list
        nm = re.match(r"^\d+\.\s+(.+)", stripped)
        if nm:
            if in_list != "ol":
                close_list()
                html_parts.append("<ol>")
                in_list = "ol"
            html_parts.append(f"<li>{_inline_fmt(nm.group(1))}</li>")
            i += 1
            continue

        # Table (pipe-delimited)
        if "|" in stripped and stripped.startswith("|"):
            close_list()
            table_lines = []
            while i < len(lines) and "|" in lines[i].strip():
                table_lines.append(lines[i].strip())
                i += 1
            html_parts.append(_render_table(table_lines))
            continue

        # Paragraph
        close_list()
        para_lines = [stripped]
        i += 1
        while i < len(lines) and lines[i].strip() \
                and not lines[i].strip().startswith("#") \
                and not lines[i].strip().startswith("```") \
                and not re.match(r"^!\[", lines[i].strip()) \
                and not lines[i].strip().startswith("- ") \
                and not lines[i].strip().startswith("* ") \
                and not re.match(r"^\d+\.\s", lines[i].strip()) \
                and not ("|" in lines[i] and lines[i].strip().startswith("|")):
            para_lines.append(lines[i].strip())
            i += 1
        html_parts.append(f"<p>{_inline_fmt(' '.join(para_lines))}</p>")

    close_list()
    if in_code:
        html_parts.append("</code></pre>")

    return "\n".join(html_parts)


def _inline_fmt(text):
    """Apply inline formatting: bold, italic, code. Leave math intact."""
    # Code
    text = re.sub(r"`([^`]+)`", r'<code>\1</code>', text)
    # Bold
    text = re.sub(r"\*\*([^*]+)\*\*", r"<strong>\1</strong>", text)
    # Italic (but not ** and not math $)
    text = re.sub(r"(?<!\*)\*([^*]+)\*(?!\*)", r"<em>\1</em>", text)
    return text


def _render_table(lines):
    """Convert pipe-delimited markdown table lines to HTML."""
    if len(lines) < 2:
        return ""
    parts = ["<table>"]
    # Header
    cells = [c.strip() for c in lines[0].split("|")[1:-1]]
    parts.append("<thead><tr>")
    for c in cells:
        parts.append(f"<th>{_inline_fmt(c)}</th>")
    parts.append("</tr></thead>")
    # Body (skip separator line)
    parts.append("<tbody>")
    for row_line in lines[2:]:
        cells = [c.strip() for c in row_line.split("|")[1:-1]]
        parts.append("<tr>")
        for c in cells:
            parts.append(f"<td>{_inline_fmt(c)}</td>")
        parts.append("</tr>")
    parts.append("</tbody></table>")
    return "\n".join(parts)


# ---------------------------------------------------------------------------
# Sidebar generation
# ---------------------------------------------------------------------------

def _build_sidebar(cfg):
    """Build sidebar navigation HTML from book config."""
    items = []
    items.append(f'<h2>{_esc(cfg.get("title", "Book"))}</h2>')
    items.append("<ul>")

    prev_part = None
    for ch_num, ch, part_title in book_builder.iter_chapters(cfg):
        if part_title and part_title != prev_part:
            items.append(f'<li class="part">{_esc(part_title)}</li>')
            prev_part = part_title
        anchor = f"chapter-{ch_num}"
        ch_title = ch.get("title", f"Chapter {ch_num}")
        items.append(f'<li><a href="#{anchor}">{ch_num}. {_esc(ch_title)}</a></li>')

    # Backmatter
    for bm in cfg.get("backmatter", []):
        label = bm.replace("_", " ").title()
        items.append(f'<li><a href="#{bm}">{_esc(label)}</a></li>')

    items.append("</ul>")
    return "\n".join(items)


# ---------------------------------------------------------------------------
# Public API
# ---------------------------------------------------------------------------

def render_book_html(book_dir, chapter_filter=None):
    """Render the book to a single-page HTML with sidebar navigation.

    Parameters
    ----------
    book_dir : str or Path
        Path to the book project directory.
    chapter_filter : str or None
        If given, render only the matching chapter.

    Returns
    -------
    Path or None
        Path to the generated HTML file, or None on failure.
    """
    book_dir = Path(book_dir)
    cfg = book_builder.load_book_config(book_dir)

    title = cfg.get("title", "Untitled")

    # Load bibliography
    bib_path = book_dir / "refs.bib"
    bib_entries = parse_bibtex(bib_path)

    # Collect all cited keys across chapters (global numbering)
    all_cited_keys = collect_all_cited_keys_from_chapters(book_dir, cfg)

    # Build HTML
    parts = []
    parts.append(_html_head(title))
    parts.append("<body>")

    # Sidebar
    if not chapter_filter:
        parts.append('<nav class="sidebar">')
        parts.append(_build_sidebar(cfg))
        parts.append("</nav>")

    parts.append("<main>")

    # Frontmatter — professional pages from config, then remaining .md files
    if not chapter_filter:
        # Half-title page
        parts.append(_generate_half_title(cfg))

        # Full title page with graphical decoration
        parts.append(_generate_title_page(cfg))

        # Copyright page
        parts.append(_generate_copyright_page(cfg, book_dir))

        # Dedication page
        ded_html = _generate_dedication_page(book_dir)
        if ded_html:
            parts.append(ded_html)

        # Remaining frontmatter (preface, etc.) — skip title_page, copyright,
        # dedication since we generated them above
        _skip = {"title_page", "copyright", "dedication"}
        for fm in cfg.get("frontmatter", []):
            if fm in _skip:
                continue
            fm_path = book_dir / "frontmatter" / f"{fm}.md"
            if fm_path.exists():
                text = fm_path.read_text(encoding="utf-8")
                text = re.sub(r"<!--.*?-->", "", text, flags=re.DOTALL)
                parts.append(f'<section id="{fm}">')
                parts.append(_md_to_html(text))
                parts.append("</section>")
                parts.append("<hr/>")

    # Chapters
    prev_part = None
    for ch_num, ch, part_title in book_builder.iter_chapters(cfg):
        if chapter_filter and ch["dir"] != chapter_filter:
            continue

        if not chapter_filter and part_title and part_title != prev_part:
            parts.append(f'<section class="part-header"><h1>{_esc(part_title)}</h1></section>')
            prev_part = part_title

        ch_dir = book_builder.resolve_chapter_dir(book_dir, ch)
        ch_md = ch_dir / "chapter.md"

        parts.append(f'<section id="chapter-{ch_num}">')
        if ch_md.exists():
            text = ch_md.read_text(encoding="utf-8")
            text = re.sub(r"<!--.*?-->", "", text, flags=re.DOTALL)
            # Strip "Chapter N:" and prepend clean number for consistent style
            text = re.sub(r'^#\s+Chapter\s+\d+\s*:\s*', '# ', text, flags=re.MULTILINE)
            text = re.sub(r'^#\s+(.+)', rf'# {ch_num} \1', text, count=1, flags=re.MULTILINE)
            # Resolve citations before converting to HTML
            if bib_entries and all_cited_keys:
                text, _ = resolve_citations_numbered_html(
                    text, bib_entries, all_cited_keys)
            # Strip empty "## References" sections (will be rendered at end)
            text = re.sub(
                r'##\s+References\s*\n?(?:\s*\n)*', '', text)
            parts.append(_md_to_html(text))
        else:
            parts.append(f"<h1>Chapter {ch_num}: {_esc(ch.get('title', 'Untitled'))}</h1>")
            parts.append("<p><em>Content not yet written.</em></p>")
        parts.append("</section>")

    # Backmatter
    if not chapter_filter:
        for bm in cfg.get("backmatter", []):
            bm_path = book_dir / "backmatter" / f"{bm}.md"
            if bm_path.exists():
                text = bm_path.read_text(encoding="utf-8")
                text = re.sub(r"<!--.*?-->", "", text, flags=re.DOTALL)
                parts.append(f'<section id="{bm}">')
                parts.append(_md_to_html(text))
                parts.append("</section>")

    # Bibliography section — only if there are cited keys
    if all_cited_keys and bib_entries:
        _, ref_html = resolve_citations_numbered_html("", bib_entries, all_cited_keys)
        parts.append('<section id="bibliography">')
        parts.append(ref_html)
        parts.append("</section>")

    parts.append("</main>")
    parts.append("</body>")
    parts.append("</html>")

    # Write output
    submission_dir = book_dir / "submission"
    submission_dir.mkdir(parents=True, exist_ok=True)

    # Copy chapter figures into submission/figures/ so relative src paths work
    figures_out = submission_dir / "figures"
    figures_out.mkdir(parents=True, exist_ok=True)
    for _ch_num, ch, _pt in book_builder.iter_chapters(cfg):
        ch_fig_dir = book_builder.resolve_chapter_dir(book_dir, ch) / "figures"
        if ch_fig_dir.is_dir():
            for img_file in ch_fig_dir.iterdir():
                if img_file.suffix.lower() in (".png", ".jpg", ".jpeg", ".svg", ".gif", ".webp"):
                    shutil.copy2(str(img_file), str(figures_out / img_file.name))

    out_name = "book.html" if not chapter_filter else f"{chapter_filter}.html"
    out_path = submission_dir / out_name
    out_path.write_text("\n".join(parts), encoding="utf-8")

    size_kb = out_path.stat().st_size / 1024
    print(f"[book_render_html] HTML generated: {out_path}  ({size_kb:.0f} KB)")
    return out_path
