"""
book_render_pdf — Render a multi-chapter book to PDF via Typst.

Extends the paper-level render_pdf.py pipeline with:
- Book/report document class with chapters
- Automatic table of contents
- Chapter-aware equation numbering (Eq. 3.1, 3.2, …)
- Part pages
- Page headers with chapter titles
- Frontmatter/backmatter sections
"""

import re
import shutil
import subprocess
import sys
from pathlib import Path

import book_builder
from render_pdf import strip_tags_and_comments, postprocess_typst
from citation_utils import (parse_bibtex, collect_all_cited_keys_from_chapters,
                            resolve_citations_numbered_plain, build_key_to_num,
                            format_bib_entry_plain)

# ---------------------------------------------------------------------------
# Typst preamble for a book
# ---------------------------------------------------------------------------

def build_book_typst_preamble(cfg, profile=None):
    """Return a typst preamble string with book styling.

    Parameters
    ----------
    cfg : dict
        Parsed book.yaml configuration.
    profile : dict or None
        Publisher profile (from _publisher_profiles/*.yaml).
    """
    title = cfg.get("title", "Untitled").replace('"', '\\"')
    authors = cfg.get("authors", [])
    author_str = ", ".join(a.get("name", "") for a in authors).replace('"', '\\"')

    settings = cfg.get("settings", {})
    font_size = settings.get("font_size", 11)
    if isinstance(font_size, str):
        font_size = font_size.replace("pt", "")
    line_spacing = settings.get("line_spacing", 1.5)
    leading = f"{0.65 * float(line_spacing):.2f}em"

    # Publisher profile overrides
    if profile:
        trim = profile.get("trim_size", "210x297mm")
        margins = profile.get("margins", {})
        m_top = margins.get("top", "25mm")
        m_bot = margins.get("bottom", "25mm")
        m_inner = margins.get("inner", "25mm")
        m_outer = margins.get("outer", "25mm")
        font_family = profile.get("font", {}).get("family", "New Computer Modern") \
            if isinstance(profile.get("font"), dict) else "New Computer Modern"
        p_font_size = profile.get("font", {}).get("size", f"{font_size}pt") \
            if isinstance(profile.get("font"), dict) else f"{font_size}pt"
        brand = profile.get("brand_colors", {}) if isinstance(profile.get("brand_colors"), dict) else {}
        brand_color = brand.get("primary", "#0d3b66")
    else:
        trim = "210x297mm"
        m_top = "25mm"
        m_bot = "25mm"
        m_inner = "25mm"
        m_outer = "25mm"
        font_family = "New Computer Modern"
        p_font_size = f"{font_size}pt"
        brand_color = "#0d3b66"

    eq_numbering = settings.get("equation_numbering", "chapter")
    # Note: for chapter-scoped numbering we still use the "(1.1)" pattern
    # but tie the leading number to the heading counter via a #show rule
    # below so it actually resets per chapter.
    if eq_numbering == "chapter":
        eq_num_str = '"(1.1)"'
    else:
        eq_num_str = '"(1)"'

    # Two-sided / binding
    two_sided = bool(settings.get("two_sided", True))
    binding_str = 'left'
    chapter_pagebreak = (
        'pagebreak(weak: true, to: "odd")' if two_sided
        else 'pagebreak(weak: true)'
    )

    # Convert profile trim_size like "155x235mm" into Typst tuple (W, H)
    def _parse_trim(t):
        try:
            wh, unit = re.match(r"\s*(\d+(?:\.\d+)?)x(\d+(?:\.\d+)?)\s*([a-zA-Z]+)\s*", t).groups()  # type: ignore[union-attr]
            return None  # unused; we keep raw string parsing inline below
        except Exception:
            return None

    # Build width/height tuple for #set page
    m = re.match(r"\s*(\d+(?:\.\d+)?)x(\d+(?:\.\d+)?)\s*([a-zA-Z]+)\s*", trim or "")
    if m:
        page_w, page_h, page_unit = m.group(1), m.group(2), m.group(3)
        page_size_typst = f'width: {page_w}{page_unit}, height: {page_h}{page_unit}'
    else:
        # Fallback to a4
        page_size_typst = 'paper: "a4"'

    # Title page variables
    subtitle = cfg.get("subtitle", "").replace('"', '\\"')
    publisher = cfg.get("publisher", "")
    publisher_upper = publisher.upper() if publisher else ""
    edition = cfg.get("edition", "")
    year = cfg.get("year", "")
    edition_year_parts = []
    if edition:
        edition_year_parts.append("{} Edition".format(edition))
    if year:
        edition_year_parts.append(str(year))
    edition_year = "  ·  ".join(edition_year_parts)

    # Build author Typst blocks
    author_blocks = []
    for a in authors:
        name = a.get("name", "").replace('"', '\\"')
        aff = a.get("affiliation", "").replace('"', '\\"')
        author_blocks.append(
            '  #align(center)[#text(size: 14pt, fill: luma(40), '
            'tracking: 1pt)[#smallcaps[{}]]]\n'.format(name))
        if aff:
            author_blocks.append(
                '  #align(center)[#text(size: 10pt, style: "italic", '
                'fill: luma(110))[{}]]\n  #v(0.3cm)\n'.format(aff))
    author_typst = "".join(author_blocks) if author_blocks else ""

    # Copyright page data
    copyright_full_title = title
    if subtitle:
        copyright_full_title += ": " + subtitle
    isbn = cfg.get("isbn", "")
    isbn_typst = "\n\n  ISBN {}\n".format(isbn) if isbn else ""

    # Select emblem style based on cover config
    cover = cfg.get("cover", {})
    emblem_style = cover.get("emblem", "network") if isinstance(cover, dict) else "network"

    if emblem_style == "molecular":
        emblem_typst = r'''  // Decorative emblem — CPA molecular association diagram
  // Molecules with association sites connected by dashed hydrogen-bond lines.
  #align(center)[
    #box(width: 4.6cm, height: 4.6cm)[
      // Outer ring — equation of state framework
      #place(center + horizon)[
        #circle(radius: 2.2cm, fill: none, stroke: 1.8pt + rgb("#0d3b66"))
      ]
      #place(center + horizon)[
        #circle(radius: 2.0cm, fill: none, stroke: 0.5pt + rgb("#1a5276").lighten(50%))
      ]

      // ── Molecule A (top-center, water-like) ──
      #place(center + top, dx: 0cm, dy: 0.55cm)[
        #circle(radius: 0.36cm, fill: rgb("#0d3b66").lighten(85%), stroke: 1.6pt + rgb("#0d3b66"))
      ]
      // Donor site (top-left of molecule A)
      #place(center + top, dx: -0.28cm, dy: 0.32cm)[
        #circle(radius: 0.1cm, fill: rgb("#c0392b"))
      ]
      // Acceptor site (top-right of molecule A)
      #place(center + top, dx: 0.28cm, dy: 0.32cm)[
        #circle(radius: 0.1cm, fill: rgb("#2980b9"))
      ]

      // ── Molecule B (bottom-left, glycol-like) ──
      #place(left + bottom, dx: 0.55cm, dy: -0.85cm)[
        #circle(radius: 0.36cm, fill: rgb("#1a5276").lighten(85%), stroke: 1.6pt + rgb("#1a5276"))
      ]
      // Donor site (upper-right of molecule B)
      #place(left + bottom, dx: 0.86cm, dy: -1.12cm)[
        #circle(radius: 0.1cm, fill: rgb("#c0392b"))
      ]
      // Acceptor site (lower of molecule B)
      #place(left + bottom, dx: 0.55cm, dy: -0.58cm)[
        #circle(radius: 0.1cm, fill: rgb("#2980b9"))
      ]

      // ── Molecule C (bottom-right, methanol-like) ──
      #place(right + bottom, dx: -0.55cm, dy: -0.85cm)[
        #circle(radius: 0.36cm, fill: rgb("#2980b9").lighten(85%), stroke: 1.6pt + rgb("#2980b9"))
      ]
      // Acceptor site (upper-left of molecule C)
      #place(right + bottom, dx: -0.86cm, dy: -1.12cm)[
        #circle(radius: 0.1cm, fill: rgb("#2980b9"))
      ]
      // Donor site (lower of molecule C)
      #place(right + bottom, dx: -0.55cm, dy: -0.58cm)[
        #circle(radius: 0.1cm, fill: rgb("#c0392b"))
      ]

      // ── Molecule D (center, hydrocarbon — no association sites) ──
      #place(center + horizon, dy: 0.2cm)[
        #circle(radius: 0.28cm, fill: rgb("#7f8c8d").lighten(70%), stroke: 1.2pt + rgb("#7f8c8d"))
      ]

      // ── Small satellite molecules (non-associating species) ──
      #place(left + horizon, dx: 0.38cm, dy: -0.25cm)[
        #circle(radius: 0.15cm, fill: rgb("#bdc3c7"), stroke: 0.8pt + rgb("#95a5a6"))
      ]
      #place(right + horizon, dx: -0.38cm, dy: -0.25cm)[
        #circle(radius: 0.15cm, fill: rgb("#bdc3c7"), stroke: 0.8pt + rgb("#95a5a6"))
      ]

      // ── Hydrogen bonds (dashed lines between association sites) ──
      // A donor → C acceptor
      #place(center + top, dy: 0.9cm)[
        #line(start: (0.28cm, 0cm), end: (1.05cm, 1.65cm),
              stroke: (paint: rgb("#c0392b").lighten(20%), thickness: 1.2pt, dash: "dashed"))
      ]
      // A acceptor → B donor
      #place(center + top, dy: 0.9cm)[
        #line(start: (-0.28cm, 0cm), end: (-1.05cm, 1.65cm),
              stroke: (paint: rgb("#2980b9").lighten(20%), thickness: 1.2pt, dash: "dashed"))
      ]
      // B donor → D (weak interaction)
      #place(center + horizon, dy: 0.1cm)[
        #line(start: (-0.7cm, 0.55cm), end: (-0.3cm, 0.2cm),
              stroke: (paint: rgb("#7f8c8d").lighten(30%), thickness: 0.8pt, dash: "dotted"))
      ]
      // C acceptor → D (weak interaction)
      #place(center + horizon, dy: 0.1cm)[
        #line(start: (0.7cm, 0.55cm), end: (0.3cm, 0.2cm),
              stroke: (paint: rgb("#7f8c8d").lighten(30%), thickness: 0.8pt, dash: "dotted"))
      ]

      // ── Labels (tiny, subtle) ──
      #place(center + top, dx: 0cm, dy: 0.12cm)[
        #text(size: 5pt, fill: rgb("#0d3b66").lighten(20%), weight: "bold")[H#sub[2]O]
      ]
      #place(left + bottom, dx: 0.32cm, dy: -0.42cm)[
        #text(size: 5pt, fill: rgb("#1a5276").lighten(20%), weight: "bold")[MEG]
      ]
      #place(right + bottom, dx: -0.28cm, dy: -0.42cm)[
        #text(size: 5pt, fill: rgb("#2980b9").lighten(20%), weight: "bold")[MeOH]
      ]
      #place(center + horizon, dy: 0.62cm)[
        #text(size: 4.5pt, fill: rgb("#7f8c8d"), weight: "bold")[CH#sub[4]]
      ]
    ]
  ]'''
    else:
        emblem_typst = r'''  // Decorative geometric emblem (network)
  #align(center)[
    #box(width: 2.8cm, height: 2.8cm)[
      #circle(radius: 1.35cm, fill: none, stroke: 2pt + rgb("#0d3b66"))
      #place(center + horizon)[
        #circle(radius: 1.1cm, fill: none, stroke: 0.6pt + rgb("#1a5276").lighten(40%))
      ]
      #place(center + horizon)[
        #circle(radius: 0.22cm, fill: rgb("#1a5276"))
      ]
      #place(center + top, dy: 0.4cm)[
        #circle(radius: 0.16cm, fill: rgb("#0d3b66"))
      ]
      #place(left + bottom, dx: 0.45cm, dy: -0.5cm)[
        #circle(radius: 0.16cm, fill: rgb("#2980b9"))
      ]
      #place(right + bottom, dx: -0.45cm, dy: -0.5cm)[
        #circle(radius: 0.16cm, fill: rgb("#2980b9"))
      ]
    ]
  ]'''

    preamble = rf'''// Book styling — auto-generated by book_render_pdf.py
#set document(
  title: "{title}",
  author: "{author_str}",
)

// Pandoc compatibility
#let horizontalrule = line(length: 100%, stroke: 0.5pt + luma(180))

// ── Page geometry ──
// Trim size and margins come from the publisher profile.
// Two-sided books use "inside"/"outside" so the binding margin alternates.
#set page(
  {page_size_typst},
  margin: (top: {m_top}, bottom: {m_bot}, inside: {m_inner}, outside: {m_outer}),
  binding: {binding_str},
  numbering: "1",
  number-align: center,
  header: context {{
    let pn = counter(page).get().first()
    if pn <= 1 {{ return [] }}
    let chapters = query(heading.where(level: 1).before(here()))
    if chapters.len() == 0 {{ return [] }}
    let title = chapters.last().body
    set text(size: 8.5pt, fill: luma(110), tracking: 0.5pt)
    if calc.even(pn) {{
      align(left)[#smallcaps[#title]]
    }} else {{
      align(right)[#smallcaps[#title]]
    }}
  }},
)

// ── Text & language ──
#set text(
  font: "{font_family}",
  size: {p_font_size},
  lang: "en",
  hyphenate: true,
  number-type: "lining",
  ligatures: true,
)

// ── Paragraph typography ──
// Justified, controlled leading, first-line indent except after headings.
#set par(
  justify: true,
  leading: {leading},
  first-line-indent: (amount: 1.2em, all: false),
  spacing: 0.65em,
)

// ── Heading numbering ──
#set heading(numbering: "1.1")

// Reset equation counter at every chapter (level-1 heading)
#show heading.where(level: 1): it => {{
  counter(math.equation).update(0)
  {chapter_pagebreak}
  v(1.5em)
  block[
    #text(size: 9pt, tracking: 2pt, fill: luma(120), weight: "regular")[
      #smallcaps[Chapter #counter(heading).display("1")]
    ]
    #v(0.4em)
    #line(length: 35%, stroke: 1pt + rgb("#0d3b66"))
    #v(0.6em)
    #text(weight: "bold", size: 22pt, fill: rgb("#0d3b66"))[#it.body]
  ]
  v(2em)
}}

#show heading.where(level: 2): it => {{
  v(1.4em)
  block(text(weight: "bold", size: 13pt, fill: rgb("#1a5276"))[#it])
  v(0.4em)
}}

#show heading.where(level: 3): it => {{
  v(1.0em)
  block(text(weight: "bold", size: 11.5pt)[#it])
  v(0.3em)
}}

#show heading.where(level: 4): it => {{
  v(0.7em)
  block(text(weight: "semibold", style: "italic", size: 11pt)[#it])
  v(0.2em)
}}

// ── Equation numbering ──
// Display as (chapter.eq) with the chapter number coming from the heading
// counter. The counter is reset by the level-1 #show rule above.
#set math.equation(numbering: n => {{
  let chs = counter(heading).get()
  let ch = if chs.len() > 0 {{ chs.first() }} else {{ 0 }}
  "(" + str(ch) + "." + str(n) + ")"
}}, supplement: [Eq.])

// ── Tables: booktabs style (top / mid / bottom horizontal rules only) ──
// Stroke is applied via show rule on the first/last row hlines from pandoc.
#set table(
  stroke: none,
  inset: (x: 8pt, y: 5pt),
)

// Booktabs-style horizontal rules: thicker at top and bottom, thin under
// the header row. Pandoc emits standard #table; we recolor the outer rules.
#show table: it => {{
  set text(size: 9pt)
  block(breakable: false, stroke: (top: 1pt + black, bottom: 1pt + black), it)
}}

// ── Figures ──
#show figure: it => {{
  v(0.5em)
  align(center)[#it.body]
  v(0.3em)
  align(center)[
    #set text(size: 9pt, style: "italic", fill: luma(80))
    #it.caption
  ]
  v(0.8em)
}}

// ── Code blocks ──
#show raw.where(block: true): it => {{
  set text(size: 8.5pt, font: "DejaVu Sans Mono")
  block(
    width: 100%,
    inset: 9pt,
    fill: luma(247),
    radius: 3pt,
    stroke: (left: 2.5pt + rgb("#1a5276")),
    breakable: true,
    it
  )
}}

#show raw.where(block: false): it => {{
  box(
    fill: luma(244),
    inset: (x: 3pt, y: 1pt),
    outset: (y: 1pt),
    radius: 2pt,
    text(size: 0.92em, font: "DejaVu Sans Mono")[#it]
  )
}}

// ── Block quotes ──
#show quote.where(block: true): it => {{
  block(
    inset: (left: 12pt, y: 6pt),
    stroke: (left: 2pt + rgb("#1a5276").lighten(20%)),
    text(style: "italic", fill: luma(70))[#it.body]
  )
}}

// ── Title Page ──
#page(numbering: none, margin: (top: 3cm, bottom: 3cm, left: 2.5cm, right: 2.5cm))[
  #v(1fr)

  // Top decorative rule
  #align(center)[
    #line(length: 85%, stroke: 2.5pt + rgb("#0d3b66"))
  ]

  #v(0.8cm)

{emblem_typst}

  #v(0.6cm)

  // Title
  #align(center)[
    #text(size: 24pt, weight: "bold", fill: rgb("#0d3b66"), tracking: -0.3pt)[
      {title}
    ]
  ]

  #v(0.4cm)

  // Subtitle
  #align(center)[
    #text(size: 12pt, style: "italic", fill: luma(100))[
      {subtitle}
    ]
  ]

  #v(0.6cm)

  // Decorative middle rule
  #align(center)[
    #line(length: 40%, stroke: 1pt + rgb("#1a5276").lighten(30%))
  ]

  #v(0.8cm)

  // Authors
  {author_typst}

  #v(1fr)

  // Edition and year
  #align(center)[
    #text(size: 9pt, fill: luma(130))[
      {edition_year}
    ]
  ]

  #v(0.3cm)

  // Publisher
  #align(center)[
    #text(size: 10pt, fill: luma(80), tracking: 2pt, weight: "semibold")[
      {publisher_upper}
    ]
  ]

  #v(0.5cm)

  // Bottom decorative rule
  #align(center)[
    #line(length: 85%, stroke: 2.5pt + rgb("#0d3b66"))
  ]

  #v(0.5cm)
]

#pagebreak()

// ── Copyright Page ──
#page(numbering: none, margin: (top: 2.5cm, bottom: 3cm, left: 3cm, right: 3cm))[
  #v(1fr)

  #text(weight: "bold", size: 9.5pt)[{copyright_full_title}]

  #v(0.5cm)

  #set text(size: 8.5pt, fill: luma(80))
  #set par(justify: false, leading: 0.8em)

  Copyright \u00a9 {year} Equinor ASA and the Norwegian University of Science and Technology (NTNU). All rights reserved.

  #v(0.3cm)

  This work is the intellectual property of Equinor ASA and NTNU. No part of this publication may be reproduced, stored in a retrieval system, or transmitted, in any form or by any means, electronic, mechanical, photocopying, recording, or otherwise, without the prior written permission of Equinor ASA and NTNU.

  #v(0.3cm)

  The NeqSim library is open-source software released under the Apache License 2.0. All code examples in this book are available at #link("https://github.com/equinor/neqsim")[github.com/equinor/neqsim] and may be freely used and modified under the terms of that license.{isbn_typst}

  #v(0.5cm)

  Published by {publisher_upper}

  #v(0.8cm)

  #text(size: 7.5pt, fill: luma(160))[Typeset using NeqSim PaperLab]
]

#pagebreak()

// ── Table of Contents ──
#outline(title: "Table of Contents", depth: 3)
#pagebreak()

'''
    # Apply publisher brand colour to all hardcoded accent uses.
    if brand_color != "#0d3b66":
        preamble = preamble.replace("#0d3b66", brand_color)
    return preamble


# ---------------------------------------------------------------------------
# Chapter preprocessing
# ---------------------------------------------------------------------------

def _preprocess_chapter(text, ch_num, figures_dir=None, key_to_num=None,
                        chapter_dir=None, submission_dir=None):
    """Clean chapter markdown for typst conversion.

    - Strip HTML comments and \\tag{}
    - Fix figure paths to point to the copy in submission/
    - Resolve \\cite{key} to [N]
    - Strip empty ## References placeholder
    """
    text = strip_tags_and_comments(text)

    # Strip "Chapter N:" prefix from top-level headings (Typst auto-numbers)
    text = re.sub(r'^#\s+Chapter\s+\d+\s*:\s*', '# ', text, flags=re.MULTILINE)

    # Strip hardcoded section numbers (auto-numbering via Typst)
    text = book_builder.strip_heading_numbers(text)

    # Resolve citations
    if key_to_num:
        text = resolve_citations_numbered_plain(text, key_to_num)
    # Strip empty "## References" placeholder
    text = re.sub(r'##\s+References\s*\n?(?:\s*\n)*', '', text)

    # Rewrite relative figure paths: figures/foo.png -> figures_chNN/foo.png
    if figures_dir:
        text = re.sub(
            r"!\[(.*?)\]\(figures/([^)]+)\)",
            rf"![\1](figures_ch{ch_num:02d}/\2)",
            text,
            flags=re.DOTALL,
        )

    # Typst's Python compiler sandboxes file reads to the compiled document
    # tree. Lecture figures referenced as ../../figures/... therefore need to
    # be copied into submission/ and rewritten before Pandoc converts Markdown
    # to Typst.
    if chapter_dir is not None and submission_dir is not None:
        chapter_dir = Path(chapter_dir)
        submission_dir = Path(submission_dir)
        shared_fig_dir = submission_dir / "figures"
        shared_fig_dir.mkdir(parents=True, exist_ok=True)

        def _rewrite_external_image(match):
            alt = match.group(1)
            target = match.group(2).strip()
            target_lower = target.lower()
            if (target_lower.startswith("http://")
                    or target_lower.startswith("https://")
                    or target_lower.startswith("data:")
                    or target.startswith("figures_ch")):
                return match.group(0)

            source = (chapter_dir / target).resolve()
            if not source.exists() or not source.is_file():
                return match.group(0)

            safe_name = re.sub(r"[^A-Za-z0-9_.-]+", "_", target.replace("\\", "/"))
            safe_name = safe_name.strip("._")
            dest = shared_fig_dir / f"ch{ch_num:02d}_{safe_name}"
            shutil.copy2(source, dest)
            return f"![{alt}](figures/{dest.name})"

        text = re.sub(r"!\[(.*?)\]\(([^)]+)\)", _rewrite_external_image, text,
                      flags=re.DOTALL)

    return text


# ---------------------------------------------------------------------------
# Public API
# ---------------------------------------------------------------------------

def render_book_pdf(book_dir, chapter_filter=None):
    """Render the book (or a single chapter) to PDF via Typst.

    Parameters
    ----------
    book_dir : str or Path
        Path to the book project directory.
    chapter_filter : str or None
        If given, render only the chapter whose ``dir`` matches this string.
        Produces a single-chapter preview PDF.

    Returns
    -------
    Path or None
        Path to the generated PDF, or None on failure.
    """
    book_dir = Path(book_dir)

    # Check dependencies
    if shutil.which("pandoc") is None:
        print("[book_render_pdf] Error: pandoc not found on PATH")
        return None
    try:
        import typst as _typst  # noqa: F401
    except ImportError:
        print("[book_render_pdf] Error: typst package not installed. Run: pip install typst")
        return None

    cfg = book_builder.load_book_config(book_dir)
    profile = book_builder._load_publisher_profile(cfg.get("publisher", "self"))

    submission_dir = book_dir / "submission"
    submission_dir.mkdir(parents=True, exist_ok=True)

    # Load bibliography and build citation numbering
    bib_path = book_dir / "refs.bib"
    bib_entries = parse_bibtex(bib_path)
    all_cited_keys = collect_all_cited_keys_from_chapters(book_dir, cfg)
    key_to_num = build_key_to_num(all_cited_keys)

    # Build preamble
    preamble = build_book_typst_preamble(cfg, profile)

    # Append print-shop extras (bleed, crop marks, PDF/UA hint) when the
    # publisher profile requests them.
    try:
        from book_print_extras import build_print_extras_typst
        extras = build_print_extras_typst(profile)
        if extras:
            preamble = preamble + "\n" + extras
    except Exception as _e:
        print(f"[book_render_pdf] print extras skipped: {_e}")

    # ── Frontmatter pages (dedication, preface, etc.) ──
    frontmatter_fragments = []
    if not chapter_filter:
        _skip_fm = {"title_page", "copyright"}
        for fm in cfg.get("frontmatter", []):
            if fm in _skip_fm:
                continue
            fm_path = book_dir / "frontmatter" / f"{fm}.md"
            if not fm_path.exists():
                continue
            if fm == "dedication":
                # Centred italic page
                ded_text = fm_path.read_text(encoding="utf-8").strip()
                ded_text = re.sub(r"^##?\s+.*\n*", "", ded_text).strip()
                ded_text = re.sub(r"^\*(.+)\*$", r"\1", ded_text,
                                  flags=re.DOTALL)
                ded_text = (ded_text.replace("#", "\\#")
                                    .replace("@", "\\@"))
                frontmatter_fragments.append(
                    '#page(numbering: none)[\n'
                    '  #v(1fr)\n'
                    '  #align(center)[\n'
                    '    #text(style: "italic", fill: luma(100), '
                    f'size: 11pt)[{ded_text}]\n'
                    '  ]\n'
                    '  #v(1fr)\n'
                    ']\n'
                )
            else:
                # Convert markdown via pandoc (preface, etc.)
                text = fm_path.read_text(encoding="utf-8")
                text = strip_tags_and_comments(text)
                clean_md = submission_dir / f"_fm_{fm}_clean.md"
                clean_md.write_text(text, encoding="utf-8")
                typ_frag = submission_dir / f"_fm_{fm}.typ"
                result = subprocess.run(
                    ["pandoc", str(clean_md), "-o", str(typ_frag),
                     "--wrap=none"],
                    capture_output=True, text=True,
                )
                if result.returncode == 0 and typ_frag.exists():
                    frag_text = typ_frag.read_text(encoding="utf-8")
                    frag_text = postprocess_typst(frag_text)
                    # Wrap in a scope that disables heading numbering
                    frag_text = (
                        '#set heading(numbering: none)\n'
                        + frag_text
                        + '\n#set heading(numbering: "1.1")\n'
                    )
                    frontmatter_fragments.append(frag_text)
                _cleanup(clean_md, typ_frag)

    # Collect chapter typst fragments
    chapter_fragments = []
    prev_part = None

    for ch_num, ch, part_title in book_builder.iter_chapters(cfg):
        if chapter_filter and ch["dir"] != chapter_filter:
            continue

        # Part header page
        if not chapter_filter and part_title and part_title != prev_part:
            safe_pt = (part_title.replace("#", "\\#")
                                 .replace("@", "\\@"))
            chapter_fragments.append(
                '#page(numbering: none)[\n'
                '  #v(1fr)\n'
                '  #align(center)[\n'
                '    #text(size: 20pt, weight: "bold", '
                'fill: rgb("#1a5276"), tracking: 0.5pt)'
                f'[#smallcaps[{safe_pt}]]\n'
                '  ]\n'
                '  #align(center)[\n'
                '    #v(0.5cm)\n'
                '    #line(length: 30%, stroke: 1pt + '
                'rgb("#1a5276").lighten(30%))\n'
                '  ]\n'
                '  #v(1fr)\n'
                ']\n'
            )
            prev_part = part_title

        ch_dir = book_builder.resolve_chapter_dir(book_dir, ch)
        ch_md = ch_dir / "chapter.md"

        if not ch_md.exists():
            print(f"[book_render_pdf] Warning: {ch_md} not found, skipping")
            continue

        # Copy chapter figures into submission/
        fig_src = ch_dir / "figures"
        fig_dst = submission_dir / f"figures_ch{ch_num:02d}"
        if fig_src.exists():
            if fig_dst.exists():
                shutil.rmtree(fig_dst)
            shutil.copytree(fig_src, fig_dst)

        # Preprocess chapter markdown (includes citation resolution)
        text = ch_md.read_text(encoding="utf-8")
        text = _preprocess_chapter(
            text, ch_num,
            figures_dir=fig_src if fig_src.exists() else None,
            key_to_num=key_to_num if key_to_num else None,
            chapter_dir=ch_dir,
            submission_dir=submission_dir)

        # Write cleaned markdown to temp file
        clean_md = submission_dir / f"_ch{ch_num:02d}_clean.md"
        clean_md.write_text(text, encoding="utf-8")

        # Convert to typst via pandoc
        typ_fragment = submission_dir / f"_ch{ch_num:02d}.typ"
        result = subprocess.run(
            ["pandoc", str(clean_md), "-o", str(typ_fragment), "--wrap=none",
             f"--resource-path={ch_dir}"],
            capture_output=True, text=True,
        )
        if result.returncode != 0:
            print(f"[book_render_pdf] pandoc error for chapter {ch_num}: {result.stderr}")
            _cleanup(clean_md)
            continue

        if typ_fragment.exists():
            frag_text = typ_fragment.read_text(encoding="utf-8")
            frag_text = postprocess_typst(frag_text)
            chapter_fragments.append(frag_text)

        # Cleanup temp
        _cleanup(clean_md, typ_fragment)

    if not chapter_fragments:
        print("[book_render_pdf] No chapters to render")
        return None

    # Build bibliography typst fragment if there are citations.
    # Rendered as a hanging-indent reference list (professional book style).
    bib_fragment = ""
    if all_cited_keys and bib_entries:
        bib_lines = [
            '#heading("References", level: 1)\n',
            '#set par(justify: false, first-line-indent: 0pt, hanging-indent: 1.6em, leading: 0.6em)\n',
            '#set text(size: 9pt)\n',
        ]
        for key in all_cited_keys:
            num = key_to_num[key]
            fields = bib_entries.get(key, {})
            if fields:
                entry_text = format_bib_entry_plain(fields)
            else:
                entry_text = "-- not in refs.bib".format(key)
            # Escape special typst characters in entry text. Bibliography
            # entries can contain LaTeX-style `$_2$`, `{CO`, etc. which Typst
            # would interpret as math/code/content delimiters.
            safe_text = entry_text
            for ch in ("\\", "#", "$", "{", "}", "_", "*", "@", "<", ">", "[", "]", "`"):
                safe_text = safe_text.replace(ch, "\\" + ch)
            # Use a labelled paragraph with bold number followed by entry
            bib_lines.append(
                "#par[#text(weight: \"bold\")[[{}]] #h(0.5em) {}]\n".format(num, safe_text)
            )
        bib_fragment = "\n".join(bib_lines)

    # Assemble full typst document
    all_fragments = frontmatter_fragments + chapter_fragments
    if bib_fragment:
        all_fragments = all_fragments + [bib_fragment]
    full_typst = preamble + "\n\n".join(all_fragments)

    # Final postprocessing on the assembled document (catches bibliography
    # fragments and any cross-fragment issues missed by per-chapter passes).
    full_typst = postprocess_typst(full_typst)

    # Write master file
    master_typ = submission_dir / "book.typ"
    master_typ.write_text(full_typst, encoding="utf-8")

    # Compile PDF
    pdf_name = "book.pdf" if not chapter_filter else f"{chapter_filter}.pdf"
    pdf_file = submission_dir / pdf_name

    import typst as typst_mod
    try:
        pdf_bytes = typst_mod.compile(str(master_typ))
        pdf_file.write_bytes(pdf_bytes)
    except Exception as exc:
        print(f"[book_render_pdf] typst compilation failed: {exc}")
        return None

    size_kb = pdf_file.stat().st_size / 1024
    print(f"[book_render_pdf] PDF generated: {pdf_file}  ({size_kb:.0f} KB)")

    # Optional PDF/X or PDF/A post-pass driven by publisher print_specs
    try:
        from book_pdfx_postpass import apply_pdfx_postpass
        apply_pdfx_postpass(pdf_file, profile)
    except Exception as exc:
        print(f"[book_render_pdf] PDF/X post-pass skipped: {exc}")

    return pdf_file


def _cleanup(*paths):
    for p in paths:
        try:
            Path(p).unlink(missing_ok=True)
        except OSError:
            pass
