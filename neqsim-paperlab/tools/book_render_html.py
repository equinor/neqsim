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
from collections import Counter
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
    overflow-x: hidden;
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
    min-width: 0;
    width: 100%;
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
  background: #f6f8fa;
  border: 1px solid #e1e4e8;
  padding: 0.9rem 1.1rem;
  border-radius: 6px;
  overflow-x: auto;
  font-size: 0.82rem;
  line-height: 1.45;
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
  font-size: inherit;
  line-height: inherit;
  white-space: pre;
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
main figure.numbered-figure {{
    max-width: 82%;
}}
main figure.numbered-figure img {{
    max-width: 100%;
    max-height: 460px;
    object-fit: contain;
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
  width: 150px;
  height: 150px;
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

/* Image-cover variant: full-bleed front-cover artwork */
.title-page.cover-image {{
  padding: 0;
  background: #0d3b66;
  min-height: 100vh;
  align-items: stretch;
  justify-content: flex-start;
}}
.title-page.cover-image::before,
.title-page.cover-image::after {{
  display: none;
}}
.title-page.cover-image img.cover-art {{
  display: block;
  width: 100%;
  height: auto;
  max-height: 100vh;
  object-fit: contain;
  margin: 0 auto;
  background: #0d3b66;
}}
.title-page.cover-image .cover-publisher {{
  position: absolute;
  bottom: 1.2rem;
  left: 0;
  right: 0;
  text-align: center;
  font-size: 0.82rem;
  color: #ffffff;
  letter-spacing: 0.25em;
  text-transform: uppercase;
  font-weight: 600;
  text-shadow: 0 1px 3px rgba(0,0,0,0.6);
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
  min-height: 60vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  page-break-before: always;
  page-break-after: always;
  padding: 4rem 2rem;
  border-top: 1px solid #e5e5e5;
  border-bottom: 1px solid #e5e5e5;
  margin: 2rem 0;
}}
section.part-header .part-eyebrow {{
  font-size: 0.85rem;
  color: #1a5276;
  letter-spacing: 0.25em;
  text-transform: uppercase;
  font-weight: 600;
  margin-bottom: 1.2rem;
}}
section.part-header h1 {{
  font-size: 2.1rem;
  color: #0d3b66;
  border-top: none;
  padding-top: 0;
  margin: 0;
  letter-spacing: -0.01em;
  max-width: 700px;
  line-height: 1.25;
}}
section.part-header h1::after {{
  content: '';
  display: block;
  width: 140px;
  height: 2px;
  background: linear-gradient(90deg, transparent, #1a5276, transparent);
  margin: 1.4rem auto 0;
}}

/* ── Chapter opener (book-style chapter heading with hero illustration) ── */
section.chapter > .chapter-opener {{
  margin: 2.5rem 0 2rem;
  padding-bottom: 1.5rem;
  border-bottom: 1px solid #e5e5e5;
  page-break-before: always;
}}
.chapter-opener .ch-eyebrow {{
  font-size: 0.8rem;
  color: #1a5276;
  letter-spacing: 0.3em;
  text-transform: uppercase;
  font-weight: 600;
  margin-bottom: 0.6rem;
}}
.chapter-opener .ch-number {{
  font-family: Georgia, 'Times New Roman', serif;
  font-size: 4.5rem;
  font-weight: 300;
  color: #1a5276;
  line-height: 1;
  margin: 0 0 0.4rem;
  letter-spacing: -0.04em;
}}
.chapter-opener h1.ch-title {{
  font-size: 2rem;
  color: #0d3b66;
  border-top: none;
  padding-top: 0;
  margin: 0 0 1rem;
  letter-spacing: -0.01em;
  line-height: 1.2;
}}
.chapter-opener .ch-rule {{
  width: 90px;
  height: 2px;
  background: #1a5276;
  margin: 0.8rem 0 1.4rem;
  border: none;
}}
.chapter-opener figure.ch-hero {{
  margin: 1.5rem 0 0;
  text-align: center;
}}
.chapter-opener figure.ch-hero img {{
  max-width: 100%;
  max-height: 360px;
  width: auto;
  display: block;
  margin: 0 auto;
  border-radius: 4px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.08);
}}
.chapter-opener figure.ch-hero figcaption {{
  font-size: 0.85rem;
  color: #555;
  font-style: italic;
  margin-top: 0.6rem;
  text-align: center;
}}

/* Drop cap on first paragraph after chapter opener */
section.chapter > .chapter-opener + p::first-letter {{
  font-size: 3.2rem;
  font-family: Georgia, 'Times New Roman', serif;
  font-weight: bold;
  color: #1a5276;
  float: left;
  line-height: 0.9;
  margin: 0.15rem 0.5rem 0 0;
}}

/* Inline lecture figures (auto-extracted from course slide decks) */
figure.lecture-fig {{
  margin: 1.8rem auto;
  padding: 0.9rem 1rem 0.7rem;
  background: #fafbfc;
    border: none;
    border-radius: 4px;
  max-width: 92%;
  page-break-inside: avoid;
  break-inside: avoid;
}}
figure.lecture-fig img {{
  display: block;
  max-width: 100%;
  max-height: 420px;
  width: auto;
  height: auto;
  margin: 0 auto 0.5rem;
  background: #fff;
}}
figure.lecture-fig figcaption {{
  font-size: 0.86rem;
  color: #444;
  line-height: 1.45;
  text-align: left;
  padding: 0 0.2rem;
}}
figure.lecture-fig figcaption strong {{
  color: #0d3b66;
  margin-right: 0.3rem;
}}

p.figure-discussion {{
    max-width: 92%;
    margin: 1.2rem auto 0.35rem;
    color: #2c3e50;
    font-size: 0.95rem;
    line-height: 1.65;
}}
p.figure-discussion strong {{
    color: #0d3b66;
}}
p.figure-discussion em {{
    color: #475569;
}}

.figure-inset {{
    page-break-inside: avoid;
    break-inside: avoid;
}}

/* Worked-example callout */
.worked-example {{
  background: #f7faff;
  border: 1px solid #d6e4f5;
  border-left: 4px solid #1a5276;
  border-radius: 0 6px 6px 0;
  padding: 0.85rem 1.15rem 0.7rem;
  margin: 1.4rem 0;
  page-break-inside: avoid;
  break-inside: avoid;
}}
.worked-example h4 {{
  margin: 0 0 0.45rem 0;
  font-size: 1.02rem;
  color: #0d3b66;
  border: none;
  padding: 0;
}}
.worked-example p {{
  margin: 0.35rem 0;
  font-size: 0.95rem;
}}
.worked-example .we-meta {{
  font-size: 0.85rem;
  color: #555;
  margin-top: 0.45rem;
}}
.worked-example .we-meta code {{
  background: #eaf1fb;
  padding: 0.05rem 0.3rem;
  border-radius: 3px;
}}

/* Responsive */
@media (max-width: 900px) {{
    nav.sidebar {{ display: none; }}
    main {{ padding: 1rem; }}
    main figure.numbered-figure {{ max-width: 100%; }}
    .title-page .tp-title {{ font-size: 1.6rem; }}
    .title-page .tp-decoration {{ width: 100px; height: 100px; }}
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


def _number_headings(md_text, chapter_num):
    """Prepend section numbers to ##/###/#### headings.

    Numbering scheme (textbook style):
      ## Title       -> ## <chapter>.<sec> Title           (e.g. 3.1)
      ### Title      -> ### <chapter>.<sec>.<sub> Title    (e.g. 3.1.2)
      #### Title     -> #### <chapter>.<sec>.<sub>.<sss>   (e.g. 3.1.2.1)

    Behaviour:

    * If the chapter already has manually-numbered headings (e.g. ``## 3.4
      Title``), those numbers are preserved verbatim and unnumbered headings
      are left unnumbered. This keeps existing in-chapter cross-references
      (``§3.4``) valid and matches the convention that conventional sections
      such as *Learning Objectives*, *Summary* or *Exercises* often remain
      unnumbered.
    * If no heading in the chapter has a leading section number, every
      ``##`` / ``###`` / ``####`` heading is numbered sequentially.

    Headings inside fenced code blocks are skipped.
    """
    sec_pat = re.compile(r"^(#{2,4})\s+(.+?)\s*$")
    existing_num = re.compile(r"^\d+(?:\.\d+)+\b")
    fence_pat = re.compile(r"^\s*(?:```|~~~)")

    # First pass: detect whether the chapter already uses manual numbering.
    has_manual = False
    in_fence = False
    for line in md_text.split("\n"):
        if fence_pat.match(line):
            in_fence = not in_fence
            continue
        if in_fence:
            continue
        m = sec_pat.match(line)
        if m and existing_num.match(m.group(2)):
            has_manual = True
            break

    if has_manual:
        # Preserve existing numbering exactly as authored.
        return md_text

    # Otherwise, number sequentially from 1.
    sec = sub = ssub = 0
    in_fence = False
    out = []
    for line in md_text.split("\n"):
        if fence_pat.match(line):
            in_fence = not in_fence
            out.append(line)
            continue
        if in_fence:
            out.append(line)
            continue
        m = sec_pat.match(line)
        if not m:
            out.append(line)
            continue
        hashes, body = m.group(1), m.group(2)
        if hashes == "##":
            sec += 1
            sub = 0
            ssub = 0
            num = f"{chapter_num}.{sec}"
        elif hashes == "###":
            if sec == 0:
                sec = 1
            sub += 1
            ssub = 0
            num = f"{chapter_num}.{sec}.{sub}"
        else:  # ####
            if sec == 0:
                sec = 1
            if sub == 0:
                sub = 1
            ssub += 1
            num = f"{chapter_num}.{sec}.{sub}.{ssub}"
        out.append(f"{hashes} {num} {body}")
    return "\n".join(out)


# Filenames whose names suggest a generic / decorative chapter-opener
# illustration. We prefer these over plot-style figures when available.
_HERO_HINT_RE = re.compile(
    r"(s01|01_|_overview|overview|hero|cover|opener|map|layout|gates|"
    r"value_chain|envelope|topic_map|regulatory_hierarchy|cashflow|"
    r"decline|aace_classes|tbp_curve|water_depth|well_cost|pfd|"
    r"separator|teg_contactor|hydrate|cycle|profile)",
    re.IGNORECASE,
)


def _chapter_hero_html(ch_dir, ch_title):
    """Pick a lead illustration from chapter figures and return <figure> HTML.

    Prefers files whose names match common 'opener' hints (e.g. *_s01_*,
    overview, layout); falls back to the alphabetically-first PNG/SVG.
    Returns an empty string when no figure exists.
    """
    fig_dir = ch_dir / "figures"
    if not fig_dir.is_dir():
        return ""
    candidates = sorted(
        f for f in fig_dir.iterdir()
        if f.suffix.lower() in (".png", ".svg", ".jpg", ".jpeg", ".webp")
    )
    if not candidates:
        return ""
    hero = next((f for f in candidates if _HERO_HINT_RE.search(f.name)),
                candidates[0])
    rel = f"../figures/{hero.name}"
    return (
        '<figure class="ch-hero">'
        f'<img src="{rel}" alt="{_esc(ch_title)} — chapter illustration"/>'
        f'<figcaption>{_esc(ch_title)}</figcaption>'
        '</figure>'
    )


# Maximum lecture figures injected per chapter (some chapters have 70+ raw
# extractions; capping keeps the chapter readable).
_MAX_INLINE_FIGS_PER_CHAPTER = 8


def _humanise_deck(name: str) -> str:
    """Turn 'Flow_Performance_handouts.pdf' into a readable title fragment."""
    stem = re.sub(r"\.(pdf|pptx)$", "", name, flags=re.IGNORECASE)
    stem = stem.replace("_", " ").replace("-", " ")
    stem = re.sub(r"\s+handouts?$", "", stem, flags=re.IGNORECASE)
    stem = re.sub(r"\s+", " ", stem).strip()
    return stem or name


def _figure_summary(entry):
    """Return a concise prose summary from slide title/body metadata.

    When the manifest carries the slide's own title and bullet text, use it so
    the book figure reflects the actual engineering content of the source
    slide rather than only its file name.
    """
    slide_title = (entry.get("slide_title") or "").strip()
    slide_body = (entry.get("slide_body") or "").strip()
    if slide_body:
        # Convert bullet-separator into commas for prose flow.
        prose = slide_body.replace(" \u2022 ", "; ")
        prose = re.sub(r"\bB\s*\?\?", "B", prose)
        prose = re.sub(r"\?{2,}|!{2,}", "", prose)
        prose = re.sub(r"(-?\d+(?:\.\d+)?)\s*;\s*o\s*;\s*C\b", r"\1 °C", prose)
        prose = re.sub(r"\b(?:Source|Ref)\s*:\s*[^.;]+[.;]?", "", prose, flags=re.IGNORECASE)
        prose = re.sub(r"\s+([,.;:])", r"\1", prose)
        prose = re.sub(r"\s*;\s*", "; ", prose)
        prose = re.sub(r"\s+", " ", prose).strip()
        return prose.rstrip(".") + "."
    if slide_title:
        title = slide_title
        if title.isupper():
            title = title.title()
        return title.rstrip(".") + "."
    return ""


def _figure_discussion(entry, section_topic):
    """Generate a short contextual caption for a lecture-derived figure."""
    summary = _figure_summary(entry)
    if summary:
        return _esc(summary)
    return "Visual evidence used in the course material."


def _figure_text_reference(entry, section_topic, n_label):
    """Return an in-text reference paragraph for an inserted figure."""
    topic = re.sub(r"<[^>]+>", "", section_topic or "").strip()
    summary = _figure_summary(entry)
    if topic:
        first = (f"<strong>Figure {n_label}</strong> connects this section on "
                 f"<em>{_esc(topic)}</em> to the visual evidence used in the "
                 "course material.")
    else:
        first = (f"<strong>Figure {n_label}</strong> provides a visual anchor "
                 "for the field-development discussion in this chapter.")
    if summary:
        second = f" It highlights this point: {_esc(summary)}"
    else:
        second = " It should be read as supporting evidence for the surrounding engineering argument."
    third = (" The field-development question is which uncertainty, interface, "
             "bottleneck, or value driver the figure makes visible, and how that "
             "should affect concept selection or operation.")
    return f'<p class="figure-discussion">{first}{second}{third}</p>'


def _render_inline_figure(entry, section_topic, n_label):
    """Render a single inline lecture figure with discussion caption."""
    rel = "../" + entry["file"]
    return (
        '<div class="figure-inset">' +
        _figure_text_reference(entry, section_topic, n_label) +
        '<figure class="lecture-fig">'
        f'<img src="{_esc(rel)}" loading="lazy" '
        f'alt="Figure {n_label}"/>'
        '<figcaption>'
        f'<strong>Figure {n_label}.</strong> '
        f'{_figure_discussion(entry, section_topic)}'
        '</figcaption>'
        '</figure>'
        '</div>'
    )


def _inject_lecture_figures(md_text, entries, ch_num, max_figures=8,
                            book_dir=None, seen_files=None,
                            seen_hashes=None):
    """Insert lecture figures inline between H2 sections of chapter markdown.

    Distributes figures roughly evenly across the chapter's top-level sections
    (lines starting with '## '). Each figure is rendered as an HTML <figure>
    block with a contextual caption derived from the section heading it
    follows. Returns the modified markdown text (mixed markdown + HTML; the
    HTML <figure> blocks are passed through by the markdown parser).

    seen_files / seen_hashes are mutable sets shared across chapters used
    to prevent the same image (by relative path or by byte-hash) from
    being injected twice anywhere in the book.
    """
    if not entries:
        return md_text

    # Cross-chapter dedup: drop entries whose file path was already used
    # elsewhere in the book or whose bytes match an already-injected image.
    if seen_files is None:
        seen_files = set()
    if seen_hashes is None:
        seen_hashes = set()

    import hashlib

    def _file_hash(rel_path):
        if not book_dir:
            return None
        try:
            p = book_dir / rel_path
            if p.is_file():
                return hashlib.md5(p.read_bytes()).hexdigest()
        except Exception:
            return None
        return None

    def _phash(rel_path):
        """8x8 average-hash perceptual fingerprint (64-bit int) or None."""
        if not book_dir:
            return None
        try:
            from PIL import Image
        except ImportError:
            return None
        try:
            p = book_dir / rel_path
            if not p.is_file():
                return None
            img = Image.open(p).convert("L").resize((8, 8))
            px = list(img.getdata())
            avg = sum(px) / len(px)
            bits = 0
            for v in px:
                bits = (bits << 1) | (1 if v >= avg else 0)
            return bits
        except Exception:
            return None

    def _phash_dup(h, threshold=5):
        """True if h is within Hamming distance ``threshold`` of any seen phash."""
        if h is None:
            return False
        for prev in seen_hashes:
            # seen_hashes mixes md5 strings and phash ints — only compare ints.
            if isinstance(prev, int) and bin(h ^ prev).count("1") <= threshold:
                return True
        return False

    deduped = []
    local_md5 = set()
    local_phash = []
    for e in entries:
        f = e.get("file", "")
        if not f or f in seen_files:
            continue
        h = _file_hash(f)
        if h and (h in seen_hashes or h in local_md5):
            seen_files.add(f)  # still mark path as consumed
            continue
        ph = _phash(f)
        if ph is not None:
            if _phash_dup(ph):
                seen_files.add(f)
                continue
            if any(bin(ph ^ q).count("1") <= 5 for q in local_phash):
                seen_files.add(f)
                continue
        # Stash both hashes so later chapters skip this image.
        e["_md5"] = h
        e["_phash"] = ph
        if h:
            local_md5.add(h)
        if ph is not None:
            local_phash.append(ph)
        deduped.append(e)
    entries = deduped
    if not entries:
        return md_text

    # Largest images first, then cap at max_figures.
    def _area(e):
        return int(e.get("width", 0)) * int(e.get("height", 0))

    # Drop figures with no real slide text — they're almost always pure
    # decorative images (cover photos, dividers, logos that survived the
    # logo filter). A figure with only a 1–2 word slide title and no body
    # is also dropped.
    def _has_text(e):
        body = (e.get("slide_body") or "").strip()
        title = (e.get("slide_title") or "").strip()
        if len(body) >= 20:
            return True
        # Title-only: keep if it's a meaningful phrase (≥3 words and ≥15 chars).
        if title and len(title) >= 15 and len(title.split()) >= 3:
            return True
        return False

    # Drop deck cover/title pages. PDF page 1 (or PPTX slide 1) is almost
    # always a title card ("Flow Assurance and / Introduction to Processing")
    # — short body that survives _has_text but contributes no engineering
    # content. Heuristic: drop any entry on the first page/slide whose body
    # is shorter than ~10 words, OR whose body looks like a course intro
    # phrase ("Introduction to ...", "Course ...", "Lecture ...").
    _TITLE_BODY_RE = re.compile(
        r"^\s*(introduction to|introduksjon|course|lecture|module|"
        r"chapter|outline|agenda|contents|table of contents|welcome)\b",
        re.IGNORECASE,
    )

    def _is_title_page(e):
        page = e.get("page")
        if page not in (0, 1):
            return False
        body = (e.get("slide_body") or "").strip()
        body_words = len(body.split())
        if body_words < 10:
            return True
        if _TITLE_BODY_RE.match(body):
            return True
        return False

    entries = [e for e in entries if _has_text(e) and not _is_title_page(e)]
    if not entries:
        return md_text
    selected = sorted(entries, key=_area, reverse=True)[:max_figures]

    # Record the chosen images in the cross-chapter dedup sets so later
    # chapters can't re-use them (by path, by md5, and by perceptual hash).
    for e in selected:
        f = e.get("file", "")
        if f:
            seen_files.add(f)
        h = e.get("_md5") or _file_hash(f)
        if h:
            seen_hashes.add(h)
        ph = e.get("_phash")
        if ph is None:
            ph = _phash(f)
        if ph is not None:
            seen_hashes.add(ph)

    # Find H2 sections.
    h2_re = re.compile(r"^## +(.+?)$", flags=re.MULTILINE)
    headings = [(m.start(), m.end(), m.group(1).strip())
                for m in h2_re.finditer(md_text)]

    if not headings:
        # No subsections — append figures at end of chapter.
        blocks = [
            _render_inline_figure(
                e, "", f"{ch_num}.{i + 1}")
            for i, e in enumerate(selected)
        ]
        return md_text + "\n\n" + "\n\n".join(blocks) + "\n\n"

    # Compute section spans: each H2 owns text from its end up to the next H2.
    spans = []
    for i, (h_start, h_end, title) in enumerate(headings):
        end = headings[i + 1][0] if i + 1 < len(headings) else len(md_text)
        spans.append((h_end, end, title))

    # Allocate figures to sections by keyword overlap between the slide
    # text (title+body) and each section title+body. Sections that look
    # like generic admin blocks (Learning objectives, Self-test, Summary,
    # Exercises, Theoretical foundations, Worked examples, Key terms,
    # Further reading, Chapter summary) are excluded from being a target
    # so figures land in the technical sections that actually discuss
    # them. Falls back to round-robin only when no section scores > 0.
    n_sec = len(spans)
    _stop = {
        "the", "a", "an", "and", "or", "for", "of", "to", "in", "on",
        "at", "by", "with", "from", "is", "are", "as", "this", "that",
        "be", "it", "its", "we", "you", "your", "their", "ref", "https",
        "www", "com", "slide", "fig", "figure",
    }
    _admin_keywords = (
        "learning objectives", "self-test", "self test", "exercises",
        "summary", "key terms", "further reading", "chapter summary",
        "theoretical foundations", "worked examples and computer experiments",
        "annotated bibliography", "literature review", "key equations",
        "discussion",
    )

    def _tokenise(s):
        if not s:
            return set()
        return {
            t for t in re.findall(r"[a-zA-Z][a-zA-Z0-9]+", s.lower())
            if len(t) > 2 and t not in _stop
        }

    def _is_admin(title):
        tl = title.lower()
        return any(k in tl for k in _admin_keywords)

    section_kws = []
    for (s_start, s_end, title) in spans:
        body = md_text[s_start:s_end]
        section_kws.append(_tokenise(title + " " + body))

    alloc = [[] for _ in range(n_sec)]

    def _score(entry, idx):
        title = spans[idx][2]
        if _is_admin(title):
            return -1
        slide_kws = _tokenise(
            (entry.get("slide_title") or "") + " " +
            (entry.get("slide_body") or "")
        )
        if not slide_kws:
            return 0
        title_kws = _tokenise(title)
        sec_body_kws = section_kws[idx]
        score = 0
        for kw in slide_kws:
            if kw in title_kws:
                score += 3
            elif kw in sec_body_kws:
                score += 1
        return score

    for e in selected:
        scores = [(_score(e, i), -len(alloc[i]), i) for i in range(n_sec)]
        scores.sort(reverse=True)
        best_score, _, best_idx = scores[0]
        if best_score <= 0:
            # No keyword hit anywhere — do not force the figure into an
            # arbitrary section. It is better to omit an uncertain figure than
            # to place it where the surrounding text cannot discuss it.
            continue
        alloc[best_idx].append(e)

    # Walk md_text and rebuild with figures inserted at the END of each
    # section (before the next H2 / end of text).
    out_parts = []
    cursor = 0
    fig_counter = 0
    for (sec_start, sec_end, title), figs in zip(spans, alloc):
        # Emit text up to end of this section.
        out_parts.append(md_text[cursor:sec_end])
        cursor = sec_end
        # Inline figures for this section.
        for e in figs:
            fig_counter += 1
            label = f"{ch_num}.{fig_counter}"
            block = _render_inline_figure(e, title, label)
            out_parts.append("\n\n" + block + "\n\n")
    # Tail (anything after the last section span — usually empty).
    out_parts.append(md_text[cursor:])
    return "".join(out_parts)


# ---------------------------------------------------------------------------
# Professional frontmatter page generators
# ---------------------------------------------------------------------------

# Default emblem: generic network diagram (used when cover.emblem != "molecular")
_BOOK_ICON_SVG_NETWORK = (
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
    '<circle cx="60" cy="60" r="56" fill="none" stroke="url(#g1)" stroke-width="3"/>'
    '<circle cx="60" cy="60" r="48" fill="none" stroke="#1a5276" stroke-width="0.8" '
    'stroke-dasharray="3,3" opacity="0.3"/>'
    '<circle cx="60" cy="60" r="47" fill="url(#g2)"/>'
    '<line x1="60" y1="30" x2="60" y2="60" stroke="#1a5276" stroke-width="2.2"/>'
    '<line x1="34" y1="75" x2="60" y2="60" stroke="#1a5276" stroke-width="2.2"/>'
    '<line x1="86" y1="75" x2="60" y2="60" stroke="#1a5276" stroke-width="2.2"/>'
    '<line x1="60" y1="30" x2="34" y2="75" stroke="#0d3b66" stroke-width="1.2" opacity="0.35"/>'
    '<line x1="60" y1="30" x2="86" y2="75" stroke="#0d3b66" stroke-width="1.2" opacity="0.35"/>'
    '<line x1="34" y1="75" x2="86" y2="75" stroke="#0d3b66" stroke-width="1.2" opacity="0.35"/>'
    '<circle cx="60" cy="18" r="2" fill="#2980b9" opacity="0.5"/>'
    '<circle cx="24" cy="80" r="2" fill="#2980b9" opacity="0.5"/>'
    '<circle cx="96" cy="80" r="2" fill="#2980b9" opacity="0.5"/>'
    '<circle cx="95" cy="38" r="2" fill="#0d3b66" opacity="0.4"/>'
    '<circle cx="25" cy="38" r="2" fill="#0d3b66" opacity="0.4"/>'
    '<circle cx="60" cy="60" r="10" fill="#1a5276"/>'
    '<circle cx="60" cy="60" r="6" fill="#fff" opacity="0.15"/>'
    '<circle cx="60" cy="30" r="7" fill="#0d3b66"/>'
    '<circle cx="34" cy="75" r="7" fill="#2980b9"/>'
    '<circle cx="86" cy="75" r="7" fill="#2980b9"/>'
    '<circle cx="58" cy="28" r="2" fill="#fff" opacity="0.25"/>'
    '<circle cx="32" cy="73" r="2" fill="#fff" opacity="0.25"/>'
    '<circle cx="84" cy="73" r="2" fill="#fff" opacity="0.25"/>'
    '<circle cx="47" cy="45" r="3" fill="#2980b9" opacity="0.55"/>'
    '<circle cx="73" cy="45" r="3" fill="#2980b9" opacity="0.55"/>'
    '<circle cx="47" cy="68" r="3" fill="#0d3b66" opacity="0.55"/>'
    '<circle cx="73" cy="68" r="3" fill="#0d3b66" opacity="0.55"/>'
    '</svg>'
)

# CPA molecular association emblem: molecules with association sites
# connected by dashed hydrogen-bond lines, within a subtle outer ring
_BOOK_ICON_SVG_MOLECULAR = (
    '<svg viewBox="0 0 160 160" xmlns="http://www.w3.org/2000/svg">'
    '<defs>'
    '<linearGradient id="mg1" x1="0%" y1="0%" x2="100%" y2="100%">'
    '<stop offset="0%" style="stop-color:#0d3b66;stop-opacity:1"/>'
    '<stop offset="100%" style="stop-color:#2980b9;stop-opacity:1"/>'
    '</linearGradient>'
    '<linearGradient id="mg2" x1="0%" y1="100%" x2="100%" y2="0%">'
    '<stop offset="0%" style="stop-color:#2980b9;stop-opacity:0.08"/>'
    '<stop offset="100%" style="stop-color:#0d3b66;stop-opacity:0.08"/>'
    '</linearGradient>'
    '</defs>'
    # Outer ring — EoS framework
    '<circle cx="80" cy="80" r="75" fill="none" stroke="url(#mg1)" stroke-width="2.5"/>'
    '<circle cx="80" cy="80" r="70" fill="none" stroke="#1a5276" stroke-width="0.6" '
    'stroke-dasharray="2,4" opacity="0.25"/>'
    '<circle cx="80" cy="80" r="69" fill="url(#mg2)"/>'
    # ── Hydrogen bonds (dashed, behind molecules) ──
    # A(donor) → C(acceptor): red-tinted dashed line
    '<line x1="93" y1="38" x2="113" y2="92" stroke="#c0392b" stroke-width="1.8" '
    'stroke-dasharray="5,3" opacity="0.55"/>'
    # A(acceptor) → B(donor): blue-tinted dashed line
    '<line x1="67" y1="38" x2="47" y2="92" stroke="#2980b9" stroke-width="1.8" '
    'stroke-dasharray="5,3" opacity="0.55"/>'
    # B → D: weak dotted interaction
    '<line x1="52" y1="95" x2="70" y2="80" stroke="#7f8c8d" stroke-width="1" '
    'stroke-dasharray="2,3" opacity="0.4"/>'
    # C → D: weak dotted interaction
    '<line x1="108" y1="95" x2="90" y2="80" stroke="#7f8c8d" stroke-width="1" '
    'stroke-dasharray="2,3" opacity="0.4"/>'
    # B → C: weak inter-molecular
    '<line x1="55" y1="104" x2="105" y2="104" stroke="#1a5276" stroke-width="0.8" '
    'stroke-dasharray="3,4" opacity="0.25"/>'
    # ── Molecule A: Water (top center) ──
    '<circle cx="80" cy="30" r="16" fill="#e8f0f8" stroke="#0d3b66" stroke-width="2.2"/>'
    # Donor site (bottom-left of A)
    '<circle cx="67" cy="40" r="4.5" fill="#c0392b"/>'
    # Acceptor site (bottom-right of A)
    '<circle cx="93" cy="40" r="4.5" fill="#2980b9"/>'
    # Label
    '<text x="80" y="30" text-anchor="middle" dominant-baseline="central" '
    'font-family="serif" font-size="8" font-weight="bold" fill="#0d3b66">'
    'H&#x2082;O</text>'
    # ── Molecule B: MEG (bottom-left) ──
    '<circle cx="44" cy="100" r="16" fill="#eaf0f5" stroke="#1a5276" stroke-width="2.2"/>'
    # Donor site (upper-right of B)
    '<circle cx="56" cy="90" r="4.5" fill="#c0392b"/>'
    # Acceptor site (bottom of B)
    '<circle cx="44" cy="117" r="4.5" fill="#2980b9"/>'
    # Label
    '<text x="44" y="101" text-anchor="middle" dominant-baseline="central" '
    'font-family="serif" font-size="7" font-weight="bold" fill="#1a5276">'
    'MEG</text>'
    # ── Molecule C: Methanol (bottom-right) ──
    '<circle cx="116" cy="100" r="16" fill="#e8f2fa" stroke="#2980b9" stroke-width="2.2"/>'
    # Acceptor site (upper-left of C)
    '<circle cx="104" cy="90" r="4.5" fill="#2980b9"/>'
    # Donor site (bottom of C)
    '<circle cx="116" cy="117" r="4.5" fill="#c0392b"/>'
    # Label
    '<text x="116" y="101" text-anchor="middle" dominant-baseline="central" '
    'font-family="serif" font-size="6.5" font-weight="bold" fill="#2980b9">'
    'MeOH</text>'
    # ── Molecule D: Methane (center, non-associating) ──
    '<circle cx="80" cy="78" r="12" fill="#ecf0f1" stroke="#7f8c8d" stroke-width="1.5"/>'
    '<text x="80" y="78" text-anchor="middle" dominant-baseline="central" '
    'font-family="serif" font-size="6.5" font-weight="bold" fill="#7f8c8d">'
    'CH&#x2084;</text>'
    # ── Small satellite molecules (non-associating) ──
    '<circle cx="30" cy="55" r="7" fill="#dfe6e9" stroke="#95a5a6" stroke-width="1"/>'
    '<text x="30" y="56" text-anchor="middle" dominant-baseline="central" '
    'font-family="serif" font-size="4.5" fill="#95a5a6">C&#x2082;</text>'
    '<circle cx="130" cy="55" r="7" fill="#dfe6e9" stroke="#95a5a6" stroke-width="1"/>'
    '<text x="130" y="56" text-anchor="middle" dominant-baseline="central" '
    'font-family="serif" font-size="4.5" fill="#95a5a6">C&#x2083;</text>'
    # ── Legend: tiny association site key ──
    '<circle cx="27" cy="145" r="3" fill="#c0392b"/>'
    '<text x="33" y="146" font-family="sans-serif" font-size="5" fill="#555">'
    'donor</text>'
    '<circle cx="55" cy="145" r="3" fill="#2980b9"/>'
    '<text x="61" y="146" font-family="sans-serif" font-size="5" fill="#555">'
    'acceptor</text>'
    '<line x1="82" y1="145" x2="96" y2="145" stroke="#c0392b" stroke-width="1.5" '
    'stroke-dasharray="4,2" opacity="0.6"/>'
    '<text x="99" y="146" font-family="sans-serif" font-size="5" fill="#555">'
    'H-bond</text>'
    '</svg>'
)

_BOOK_ICON_SVG = _BOOK_ICON_SVG_NETWORK  # default for backwards compatibility


def _get_book_icon_svg(cfg):
    """Return the appropriate SVG emblem based on book config."""
    cover = cfg.get("cover", {})
    emblem = cover.get("emblem", "network") if isinstance(cover, dict) else "network"
    if emblem == "molecular":
        return _BOOK_ICON_SVG_MOLECULAR
    return _BOOK_ICON_SVG_NETWORK


def _generate_title_page(cfg, book_dir=None):
    """Generate a professional title page.

    If ``figures/front_cover.png`` (or front_cover.jpg) exists in the book
    directory, render it as a full-bleed cover image with the publisher
    line below. Otherwise fall back to the SVG emblem layout.
    """
    title = cfg.get("title", "Untitled")
    subtitle = cfg.get("subtitle", "")
    authors = cfg.get("authors", [])
    edition = cfg.get("edition", "")
    year = cfg.get("year", "")
    publisher = cfg.get("publisher", "")

    # Locate a cover image if available.
    cover_rel = None
    if book_dir is not None:
        for name in ("front_cover.png", "front_cover.jpg",
                     "front_cover.jpeg", "cover.png", "cover.jpg"):
            p = book_dir / "figures" / name
            if p.is_file():
                # The rendered HTML lives in <book_dir>/submission/, so the
                # cover image (in <book_dir>/figures/) is one level up — use
                # the same `../figures/...` prefix that lecture figures use.
                cover_rel = f"../figures/{name}"
                break

    if cover_rel:
        # Image-cover layout: full-bleed cover image + publisher banner.
        parts = ['<section id="title_page" class="title-page cover-image">']
        parts.append(
            f'<img class="cover-art" src="{_esc(cover_rel)}" '
            f'alt="{_esc(title)} — front cover"/>'
        )
        if publisher:
            parts.append(
                f'<div class="cover-publisher">{_esc(publisher)}</div>'
            )
        parts.append("</section>")
        return "\n".join(parts)

    # Fallback: SVG emblem layout (legacy).
    icon_svg = _get_book_icon_svg(cfg)
    parts = ['<section id="title_page" class="title-page">']
    parts.append(f'<div class="tp-decoration">{icon_svg}</div>')
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
    # Safety-net: convert stray LaTeX text-mode sub/superscript commands
    # (``\textsubscript{2}`` / ``\textsuperscript{3}``) into HTML so they
    # don't render literally in tables or paragraphs.
    md_text = re.sub(r"\\textsubscript\{([^{}]*)\}", r"<sub>\1</sub>", md_text)
    md_text = re.sub(r"\\textsuperscript\{([^{}]*)\}", r"<sup>\1</sup>", md_text)

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
            i += 1
            continue

        stripped = line.strip()

        # Empty line
        if not stripped:
            close_list()
            i += 1
            continue

        # Blockquotes
        if stripped.startswith(">"):
            close_list()
            quote_lines = []
            while i < len(lines) and lines[i].strip().startswith(">"):
                quote_lines.append(re.sub(r"^>\s?", "", lines[i].strip()))
                i += 1
            html_parts.append(
                f"<blockquote><p>{_inline_fmt(' '.join(quote_lines).strip())}</p></blockquote>"
            )
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
            fig_class = (
                ' class="numbered-figure"'
                if caption.strip().lower().startswith("figure ") else ""
            )
            html_parts.append(
                f'<figure{fig_class}><img src="{_esc(src)}" alt="{_esc(caption)}"/>'
            )
            if caption:
                html_parts.append(f"<figcaption>{_inline_fmt(caption)}</figcaption>")
            html_parts.append("</figure>")
            i += 1
            continue

        # Italic caption: *Figure ...*  (must be a single * on each side, not ** ... **)
        if (
            stripped.startswith("*")
            and stripped.endswith("*")
            and len(stripped) > 2
            and not stripped.startswith("**")
            and not stripped.endswith("**")
        ):
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
            item_text = stripped[2:]
            i += 1
            # Fold indented continuation lines into the same <li>
            while i < len(lines):
                cont = lines[i]
                cont_stripped = cont.strip()
                if not cont_stripped:
                    break
                if cont.startswith(" ") or cont.startswith("\t"):
                    if (cont_stripped.startswith("- ") or cont_stripped.startswith("* ")
                            or re.match(r"^\d+\.\s", cont_stripped)):
                        break
                    item_text += " " + cont_stripped
                    i += 1
                    continue
                break
            html_parts.append(f"<li>{_inline_fmt(item_text)}</li>")
            continue

        # Numbered list
        nm = re.match(r"^\d+\.\s+(.+)", stripped)
        if nm:
            if in_list != "ol":
                close_list()
                html_parts.append("<ol>")
                in_list = "ol"
            item_text = nm.group(1)
            i += 1
            # Fold indented continuation lines into the same <li>
            while i < len(lines):
                cont = lines[i]
                cont_stripped = cont.strip()
                if not cont_stripped:
                    break
                if cont.startswith(" ") or cont.startswith("\t"):
                    if (cont_stripped.startswith("- ") or cont_stripped.startswith("* ")
                            or re.match(r"^\d+\.\s", cont_stripped)):
                        break
                    item_text += " " + cont_stripped
                    i += 1
                    continue
                break
            html_parts.append(f"<li>{_inline_fmt(item_text)}</li>")
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

        # Raw HTML block passthrough (e.g. <div class="worked-example">...</div>)
        html_block_m = re.match(r"^<(div|section|aside|figure|table)\b", stripped)
        if html_block_m:
            close_list()
            tag = html_block_m.group(1)
            depth = 0
            block_lines = []
            open_re = re.compile(rf"<{tag}\b", re.IGNORECASE)
            close_re = re.compile(rf"</{tag}\s*>", re.IGNORECASE)
            while i < len(lines):
                cur = lines[i]
                depth += len(open_re.findall(cur))
                depth -= len(close_re.findall(cur))
                block_lines.append(cur)
                i += 1
                if depth <= 0:
                    break
            html_parts.append("\n".join(block_lines))
            continue

        # Paragraph
        close_list()
        para_lines = [stripped]
        i += 1
        while i < len(lines) and lines[i].strip() \
                and not lines[i].strip().startswith("#") \
                and not lines[i].strip().startswith("```") \
                and not re.match(r"^!\[", lines[i].strip()) \
                and not lines[i].strip().startswith(">") \
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


def _deduplicate_heading_ids(html_text):
    """Return HTML with duplicate heading id attributes made unique."""
    all_id_counts = Counter(re.findall(r'\bid="([^"]+)"', html_text))
    heading_ids = re.findall(r'<h[1-6][^>]*\bid="([^"]+)"', html_text)
    for heading_id in heading_ids:
        all_id_counts[heading_id] -= 1
    seen_ids = {id_value for id_value, count in all_id_counts.items() if count > 0}

    def replace_heading_id(match):
        prefix, base_id, suffix = match.groups()
        unique_id = base_id
        counter = 2
        while unique_id in seen_ids:
            unique_id = f"{base_id}-{counter}"
            counter += 1
        seen_ids.add(unique_id)
        return f'{prefix}{unique_id}{suffix}'

    return re.sub(r'(<h[1-6][^>]*\bid=")([^"]+)(")', replace_heading_id, html_text)


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

def _build_sidebar(cfg, include_references=False):
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

    if include_references:
        items.append('<li><a href="#references">References</a></li>')

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
    include_references = bool(all_cited_keys and bib_entries)

    # Cross-chapter de-dup state for optional lecture-figure injection.
    _seen_lecture_files: set = set()
    _seen_lecture_hashes: set = set()
    inject_lecture_figures = bool(
        cfg.get("settings", {}).get("inject_lecture_figures", False))

    # Load lecture-figure manifest only when explicitly enabled. Auto-extracted
    # slide images can contain OCR artifacts, duplicate labels, decorative
    # placeholders, or stale source text, so production books should rely on
    # curated chapter figures by default.
    lecture_manifest_path = (book_dir / "figures" / "lectures" / "auto"
                             / "manifest.json")
    lecture_entries_by_chapter: dict = {}
    if inject_lecture_figures and lecture_manifest_path.exists():
        try:
            import json as _json
            mf = _json.loads(
                lecture_manifest_path.read_text(encoding="utf-8"))
            # Build chapter -> [entry,...] using the entry list (so we keep
            # source/page info, not just the file path).
            id_to_entry = {e["id"]: e for e in mf.get("entries", [])}
            for slug, files in mf.get("by_chapter", {}).items():
                entries = []
                for fp in files:
                    # match by file -> id
                    for e in mf["entries"]:
                        if e["file"] == fp:
                            entries.append(e)
                            break
                lecture_entries_by_chapter[slug] = entries
        except Exception as exc:
            print(f"  ! could not load lecture manifest: {exc}")
            lecture_entries_by_chapter = {}

    # Build HTML
    parts = []
    parts.append(_html_head(title))
    parts.append("<body>")

    # Sidebar
    if not chapter_filter:
        parts.append('<nav class="sidebar">')
        parts.append(_build_sidebar(cfg, include_references=include_references))
        parts.append("</nav>")

    parts.append("<main>")

    # Frontmatter — professional pages from config, then remaining .md files
    if not chapter_filter:
        # Half-title page — suppressed when a full-bleed cover image is used,
        # since the cover artwork already carries the title prominently.
        _has_cover_image = False
        if book_dir is not None:
            for _name in ("front_cover.png", "front_cover.jpg",
                          "front_cover.jpeg", "cover.png", "cover.jpg"):
                if (book_dir / "figures" / _name).is_file():
                    _has_cover_image = True
                    break
        if not _has_cover_image:
            parts.append(_generate_half_title(cfg))

        # Full title page with graphical decoration
        parts.append(_generate_title_page(cfg, book_dir))

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
                # Resolve \cite{key} → numbered links so frontmatter
                # (preface, acknowledgements, etc.) renders citations the
                # same way chapters do.
                if bib_entries and all_cited_keys:
                    text, _ = resolve_citations_numbered_html(
                        text, bib_entries, all_cited_keys)
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
            # Split "Part I — Foundations of Field Development" into eyebrow + title.
            eyebrow, sep, title = part_title.partition(" — ")
            if not sep:
                eyebrow, sep, title = part_title.partition(" - ")
            if sep:
                pe_html = f'<div class="part-eyebrow">{_esc(eyebrow.strip())}</div>'
                pt_html = f'<h1>{_esc(title.strip())}</h1>'
            else:
                pe_html = ''
                pt_html = f'<h1>{_esc(part_title)}</h1>'
            parts.append(
                f'<section class="part-header">{pe_html}{pt_html}</section>'
            )
            prev_part = part_title

        ch_dir = book_builder.resolve_chapter_dir(book_dir, ch)
        ch_md = ch_dir / "chapter.md"

        parts.append(f'<section class="chapter" id="chapter-{ch_num}">')

        # Build the chapter opener (eyebrow + number + title + rule).
        # Hero illustration intentionally omitted — chapter-level decorative
        # images are not part of this book's design.
        ch_title = ch.get("title", "Untitled")
        opener_html = (
            '<div class="chapter-opener">'
            '<div class="ch-eyebrow">Chapter</div>'
            f'<div class="ch-number">{ch_num}</div>'
            f'<h1 class="ch-title">{_esc(ch_title)}</h1>'
            '<hr class="ch-rule"/>'
            '</div>'
        )
        parts.append(opener_html)

        if ch_md.exists():
            text = ch_md.read_text(encoding="utf-8")
            # Strip UTF-8 BOM if present so leading-anchor regexes match.
            text = text.lstrip("\ufeff")
            text = re.sub(r"<!--.*?-->", "", text, flags=re.DOTALL)
            # Strip leading YAML front matter (chapter_authors, estimated_pages,
            # notebooks, etc. — internal metadata, not for the reader).
            text = re.sub(r'^---\s*\n.*?\n---\s*\n+', '', text,
                          count=1, flags=re.DOTALL)
            # The chapter opener above has replaced the markdown H1, so strip
            # the first H1 heading from the chapter prose to avoid duplication.
            text = re.sub(
                r'^#\s+(?:Chapter\s+\d+\s*:\s*)?.+\n+',
                '',
                text,
                count=1,
                flags=re.MULTILINE,
            )
            # Strip the leading "Material drawn from..." / "Lectured by..."
            # source blockquote that immediately follows the H1 — internal
            # provenance note, not reader-facing.
            text = re.sub(
                r'^\s*(?:>[^\n]*\n){1,6}\s*\n',
                '',
                text,
                count=1,
            )
            # Resolve citations before converting to HTML
            if bib_entries and all_cited_keys:
                text, _ = resolve_citations_numbered_html(
                    text, bib_entries, all_cited_keys)
            # Strip empty "## References" sections (will be rendered at end)
            text = re.sub(
                r'##\s+References\s*\n?(?:\s*\n)*', '', text)
            # Optionally inject auto-extracted lecture figures inline between
            # sections, each wrapped in a <figure> with a discussion caption
            # tied to the surrounding heading. This is off by default for
            # release builds; enable with settings.inject_lecture_figures.
            inline_entries = lecture_entries_by_chapter.get(ch["dir"], [])
            if inline_entries:
                text = _inject_lecture_figures(
                    text, inline_entries, ch_num, max_figures=8,
                    book_dir=book_dir,
                    seen_files=_seen_lecture_files,
                    seen_hashes=_seen_lecture_hashes)
            # Auto-number ##/###/#### headings (textbook style: 3.1, 3.1.2)
            text = _number_headings(text, ch_num)
            parts.append(_md_to_html(text))
        else:
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
    frontmatter_figures = book_dir / "frontmatter" / "figures"
    if frontmatter_figures.is_dir():
        for img_file in frontmatter_figures.rglob("*"):
            if img_file.suffix.lower() in (".png", ".jpg", ".jpeg", ".svg", ".gif", ".webp"):
                rel_img = img_file.relative_to(frontmatter_figures)
                out_img = figures_out / rel_img
                out_img.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(str(img_file), str(out_img))
    for _ch_num, ch, _pt in book_builder.iter_chapters(cfg):
        ch_fig_dir = book_builder.resolve_chapter_dir(book_dir, ch) / "figures"
        if ch_fig_dir.is_dir():
            for img_file in ch_fig_dir.rglob("*"):
                if img_file.suffix.lower() in (".png", ".jpg", ".jpeg", ".svg", ".gif", ".webp"):
                    rel_img = img_file.relative_to(ch_fig_dir)
                    out_img = figures_out / rel_img
                    out_img.parent.mkdir(parents=True, exist_ok=True)
                    shutil.copy2(str(img_file), str(out_img))

    # Copy auto-extracted lecture figures preserving the per-chapter
    # subdirectory so manifest paths (figures/lectures/auto/<slug>/<file>)
    # resolve from submission/book.html (one level under book root).
    lectures_src = book_dir / "figures" / "lectures"
    if lectures_src.is_dir():
        lectures_dst = figures_out / "lectures"
        if lectures_dst.exists():
            shutil.rmtree(lectures_dst)
        shutil.copytree(str(lectures_src), str(lectures_dst))

    # Copy backmatter asset folders so links such as
    # review_exam_preparation_assets/figures/example.png resolve from
    # submission/book.html and can be embedded into standalone HTML.
    backmatter_dir = book_dir / "backmatter"
    if backmatter_dir.is_dir():
        for asset_dir in backmatter_dir.iterdir():
            if not asset_dir.is_dir() or not asset_dir.name.endswith("_assets"):
                continue
            asset_dst = submission_dir / asset_dir.name
            if asset_dst.exists():
                shutil.rmtree(asset_dst)
            shutil.copytree(str(asset_dir), str(asset_dst))

    # Rewrite "../../figures/..." (chapter-section relative) to "../figures/..."
    # so that submission/book.html (one level under book root) resolves correctly.
    html_text = "\n".join(parts)
    html_text = html_text.replace('src="../../figures/', 'src="../figures/')
    html_text = _deduplicate_heading_ids(html_text)

    out_name = "book.html" if not chapter_filter else f"{chapter_filter}.html"
    out_path = submission_dir / out_name
    out_path.write_text(html_text, encoding="utf-8")

    size_kb = out_path.stat().st_size / 1024
    print(f"[book_render_html] HTML generated: {out_path}  ({size_kb:.0f} KB)")
    return out_path
