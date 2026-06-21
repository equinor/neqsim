"""
Render paper.md to PDF with proper math rendering via typst.

Usage:
    python render_pdf.py <paper_dir>
    python render_pdf.py papers/site_symmetry_reduction_wertheim_2026

Produces in <paper_dir>/submission/:
    paper.pdf — Typeset PDF with correct math, tables, and figures

Pipeline:
    1. Strip \\tag{N} and HTML comments (incompatible with pandoc typst writer)
    2. Convert markdown to typst source via pandoc
    3. Add academic styling preamble and fix table column widths
    4. Compile to PDF using the typst Python package

Requirements:
    pip install typst
    pandoc >= 3.0 must be on PATH
"""
import json
import re
import shutil
import subprocess
import sys
from pathlib import Path


# ---------------------------------------------------------------------------
# Preprocessing
# ---------------------------------------------------------------------------

def strip_tags_and_comments(md_text):
    r"""Remove ``\tag{N}`` from display math and strip HTML comments.

    Pandoc's typst writer does not support ``\tag{}`` — it produces broken
    output.  We strip the tags before conversion; typst's own
    ``math.equation(numbering: ...)`` handles equation numbering instead.
    """
    text = re.sub(r'<!--.*?-->', '', md_text, flags=re.DOTALL)
    text = re.sub(r'\s*\\tag\{[^}]+\}', '', text)
    return text


def resolve_citations_and_references(md_text, paper_dir, journal_profile=None):
    r"""Resolve ``\cite{}`` and inject the reference list from ``refs.bib``.

    The PDF (typst) pipeline renders ``paper.md`` directly, but the actual
    reference entries live in ``refs.bib`` and the body uses ``\cite{key}``.
    This mirrors what the Word renderer does so the citation style and the
    reference list are identical across the Word document and the PDF.

    The citation style is taken from the journal profile (``citation_style``)
    and may be ``numbered`` (``[1, 2]``), ``numbered_superscript`` (superscript
    ``1,2`` rendered via pandoc ``^...^``), or ``authoryear``
    (``(Surname et al., Year)`` with an unnumbered reference list). Numbering is
    sequential and alphabetical by BibTeX key (the same scheme the Word renderer
    uses). The formatted reference list is inserted after the ``## References``
    heading (or appended if no such heading exists).

    Parameters
    ----------
    md_text : str
        Raw markdown source of the paper.
    paper_dir : str or pathlib.Path
        Paper directory containing ``refs.bib``.
    journal_profile : dict, optional
        Journal profile; the ``citation_style`` key selects the citation format.

    Returns
    -------
    str
        Markdown with ``\cite{}`` resolved and references injected. If
        ``refs.bib`` is missing or the shared helpers cannot be imported, the
        input text is returned unchanged.
    """
    refs_bib = Path(paper_dir) / "refs.bib"
    if not refs_bib.exists():
        return md_text
    try:
        from word_renderer import (
            build_citation_map,
            build_authoryear_map,
            resolve_citations,
            parse_bibtex_entries,
            format_bibtex_reference,
            SUPERSCRIPT_CITE_OPEN,
            SUPERSCRIPT_CITE_CLOSE,
        )
    except ImportError:
        return md_text

    style = (journal_profile or {}).get("citation_style", "numbered")
    authoryear = style == "authoryear"

    cite_map = build_citation_map(refs_bib)
    authoryear_map = build_authoryear_map(refs_bib)
    if cite_map or authoryear_map:
        md_text = resolve_citations(md_text, cite_map, style, authoryear_map)
    # Convert Word superscript markers into pandoc superscript syntax (^...^).
    if style == "numbered_superscript":
        md_text = re.sub(
            re.escape(SUPERSCRIPT_CITE_OPEN) + r'([^' + re.escape(SUPERSCRIPT_CITE_CLOSE)
            + r']*)' + re.escape(SUPERSCRIPT_CITE_CLOSE),
            lambda m: '^' + m.group(1).replace(' ', '') + '^',
            md_text,
        )

    entries = parse_bibtex_entries(refs_bib)
    if not entries:
        return md_text

    # One reference per paragraph (blank line between) so pandoc keeps them
    # as separate list entries.
    ref_block = "\n\n".join(
        format_bibtex_reference(idx + 1, etype, fields, numbered=not authoryear)
        for idx, (key, etype, fields) in enumerate(entries)
    )

    new_text, n = re.subn(
        r'(^##\s+References\s*$)',
        lambda m: m.group(0) + "\n\n" + ref_block + "\n",
        md_text,
        count=1,
        flags=re.MULTILINE,
    )
    if n == 0:
        new_text = md_text.rstrip() + "\n\n## References\n\n" + ref_block + "\n"
    return new_text


# ---------------------------------------------------------------------------
# Typst preamble builder
# ---------------------------------------------------------------------------

def build_typst_preamble(title="Untitled", author=""):
    """Return a typst preamble string with academic paper styling.

    Parameters
    ----------
    title : str
        Document title (shown in PDF metadata).
    author : str
        Author name(s) for PDF metadata.
    """
    # Escape typst special characters in strings
    safe_title = title.replace('"', '\\"')
    safe_author = author.replace('"', '\\"')

    return rf'''// Academic paper styling — auto-generated by render_pdf.py
#set document(
  title: "{safe_title}",
  author: "{safe_author}",
)

// Pandoc compatibility: define horizontalrule (pandoc emits this, typst does not)
#let horizontalrule = line(length: 100%, stroke: 0.5pt + luma(180))

#set page(
  paper: "a4",
  margin: (top: 2.5cm, bottom: 2.5cm, left: 2.5cm, right: 2.5cm),
  numbering: "1",
  number-align: center,
)

#set text(
  font: "New Computer Modern",
  size: 11pt,
  lang: "en",
)

#set par(
  justify: true,
  leading: 0.65em,
)

#set heading(numbering: "1.1")

#show heading.where(level: 1): it => {{
  v(1.2em)
  block(text(weight: "bold", size: 14pt, it))
  v(0.6em)
}}

#show heading.where(level: 2): it => {{
  v(0.8em)
  block(text(weight: "bold", size: 12pt, it))
  v(0.4em)
}}

#show heading.where(level: 3): it => {{
  v(0.6em)
  block(text(weight: "bold", size: 11pt, it))
  v(0.3em)
}}

// Equation numbering
#set math.equation(numbering: "(1)")

// Table styling
#set table(
  stroke: 0.5pt + luma(160),
  inset: 6pt,
)

// Raw block styling (code blocks, ASCII art figures)
#show raw.where(block: true): it => {{
  set text(size: 7.5pt)
  block(
    width: 100%,
    inset: 8pt,
    fill: luma(245),
    radius: 3pt,
    it
  )
}}

'''


# ---------------------------------------------------------------------------
# Typst post-processing
# ---------------------------------------------------------------------------

def postprocess_typst(typ_text):
    """Fix pandoc-generated typst issues.

    - Replace fixed-percentage column widths with auto-sizing so that
      math-heavy table columns are not squeezed.
    - Replace underscores in Typst labels with hyphens (Typst labels
      cannot contain underscores).
    - Remove BibTeX case-protection braces that pandoc leaves in text.
    """

    def fix_columns(m):
        pcts = re.findall(r'[\d.]+%', m.group(0))
        return f'columns: {len(pcts)}'

    typ_text = re.sub(
        r'columns:\s*\([^)]*%[^)]*\)',
        fix_columns,
        typ_text,
    )

    # Fix underscores in Typst labels: <some_label> -> <some-label>
    # Pandoc generates labels from headings; labels cannot contain underscores.
    # Labels may contain letters, digits, hyphens, dots, and underscores.
    def fix_label(m):
        return '<' + m.group(1).replace('_', '-') + '>'

    typ_text = re.sub(r'<([a-zA-Z][a-zA-Z0-9_.:-]*_[a-zA-Z0-9_.:-]*)>', fix_label, typ_text)

    # Remove BibTeX case-protection braces that survive pandoc conversion.
    # These are bare { } in text that Typst interprets as content blocks.
    # Only target reference-like lines (starting with [number]) to avoid
    # breaking legitimate Typst syntax.
    # Also convert LaTeX subscript math ($_{N}$ or $_N$) to Unicode
    # subscript digits, because Typst math requires a base before `_`.
    _sub_map = {
        '0': '\u2080', '1': '\u2081', '2': '\u2082', '3': '\u2083',
        '4': '\u2084', '5': '\u2085', '6': '\u2086', '7': '\u2087',
        '8': '\u2088', '9': '\u2089', '+': '\u208A', '-': '\u208B',
    }

    def _sub_repl(m):
        return ''.join(_sub_map.get(c, c) for c in m.group(1))

    out_lines = []
    in_code = False
    for line in typ_text.split('\n'):
        s = line.lstrip()
        if s.startswith('```'):
            in_code = not in_code
        if not in_code and re.match(r'\[\d+\]', s):
            # Reference line: strip BibTeX braces
            line = line.replace('{', '').replace('}', '')
            # Convert $_{digits}$ and $_digits$ to Unicode subscripts
            line = re.sub(r'\$_\{?([0-9+-]+)\}?\$', _sub_repl, line)
            # Convert $^{digits}$ to Unicode superscripts
            line = re.sub(r'\$\^\{?([0-9+-]+)\}?\$', _sub_repl, line)
        out_lines.append(line)
    typ_text = '\n'.join(out_lines)

    return typ_text


# ---------------------------------------------------------------------------
# Metadata extraction helpers
# ---------------------------------------------------------------------------

def _extract_title_from_md(md_text):
    """Get the first H1 title from markdown text."""
    m = re.search(r'^#\s+(.+)$', md_text, re.MULTILINE)
    return m.group(1).strip() if m else "Untitled"


def _extract_author_from_md(md_text):
    """Best-effort author extraction from markdown front-matter or body."""
    # Try YAML front-matter
    fm = re.match(r'^---\s*\n(.*?)\n---', md_text, re.DOTALL)
    if fm:
        for line in fm.group(1).split('\n'):
            if line.strip().lower().startswith('author'):
                val = line.split(':', 1)[-1].strip().strip('"').strip("'")
                return val
    # Try **Author:** pattern in body
    m = re.search(r'\*\*(?:Author|Authors?)(?:\(s\))?:\*\*\s*(.+)', md_text)
    if m:
        return m.group(1).strip()
    return ""


def _extract_metadata_from_plan(paper_dir):
    """Read title/author from plan.json if present."""
    plan_file = Path(paper_dir) / "plan.json"
    if plan_file.exists():
        try:
            plan = json.loads(plan_file.read_text(encoding="utf-8"))
            return plan.get("title", ""), plan.get("author", "")
        except (json.JSONDecodeError, OSError):
            pass
    return "", ""


# ---------------------------------------------------------------------------
# Public API
# ---------------------------------------------------------------------------

def render_pdf(paper_dir, journal_profile=None):
    """Render ``paper.md`` inside *paper_dir* to a typeset PDF.

    Parameters
    ----------
    paper_dir : str or pathlib.Path
        Paper directory containing ``paper.md`` and ``refs.bib``.
    journal_profile : dict, optional
        Journal profile; the ``citation_style`` key selects the citation format
        used for the in-body citations and the reference list.

    Returns
    -------
    pathlib.Path or None
        Path to the generated PDF (``<paper_dir>/submission/paper.pdf``), or
        ``None`` on failure.
    """
    paper_dir = Path(paper_dir)
    paper_md = paper_dir / "paper.md"

    if not paper_md.exists():
        print(f"[render_pdf] Error: {paper_md} not found")
        return None

    # Check dependencies --------------------------------------------------
    if shutil.which("pandoc") is None:
        print("[render_pdf] Error: pandoc not found on PATH.  Install from https://pandoc.org")
        return None
    try:
        import typst as _typst  # noqa: F401
    except ImportError:
        print("[render_pdf] Error: typst Python package not installed.  Run: pip install typst")
        return None

    submission_dir = paper_dir / "submission"
    submission_dir.mkdir(parents=True, exist_ok=True)

    # Temp files (inside submission/ to keep paper_dir clean) -------------
    clean_md = submission_dir / "_paper_clean.md"
    typ_file = submission_dir / "paper.typ"
    pdf_file = submission_dir / "paper.pdf"

    # 1. Read & preprocess ------------------------------------------------
    md_text = paper_md.read_text(encoding="utf-8")
    md_text = resolve_citations_and_references(md_text, paper_dir, journal_profile)
    clean_text = strip_tags_and_comments(md_text)
    clean_md.write_text(clean_text, encoding="utf-8")

    # Stage figures into submission/ so relative markdown links (figures/*.png)
    # resolve during typst compilation without accessing parent directories.
    figures_src = paper_dir / "figures"
    figures_dst = submission_dir / "figures"
    if figures_src.exists():
        if figures_dst.exists():
            shutil.rmtree(figures_dst)
        shutil.copytree(figures_src, figures_dst)

    # 2. Extract metadata for preamble ------------------------------------
    plan_title, plan_author = _extract_metadata_from_plan(paper_dir)
    title = plan_title or _extract_title_from_md(md_text)
    author = plan_author or _extract_author_from_md(md_text)

    # 3. Pandoc: markdown → typst -----------------------------------------
    result = subprocess.run(
        [
            "pandoc",
            str(clean_md),
            "-o",
            str(typ_file),
            "--wrap=none",
            f"--resource-path={paper_dir}",
        ],
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        print(f"[render_pdf] pandoc error:\n{result.stderr}")
        _cleanup(clean_md)
        return None

    if result.stderr:
        nwarn = result.stderr.strip().count('\n') + 1
        print(f"[render_pdf] pandoc finished with {nwarn} warning(s)")

    if not typ_file.exists():
        print("[render_pdf] pandoc did not produce a .typ file")
        _cleanup(clean_md)
        return None

    # 4. Post-process typst source -----------------------------------------
    typ_text = typ_file.read_text(encoding="utf-8")
    typ_text = postprocess_typst(typ_text)
    full_typ = build_typst_preamble(title=title, author=author) + typ_text
    typ_file.write_text(full_typ, encoding="utf-8")

    # 5. Compile PDF -------------------------------------------------------
    import typst as typst_mod
    try:
        pdf_bytes = typst_mod.compile(str(typ_file))
        pdf_file.write_bytes(pdf_bytes)
    except Exception as exc:
        print(f"[render_pdf] typst compilation failed: {exc}")
        _cleanup(clean_md)
        return None

    # 6. Cleanup temps -----------------------------------------------------
    _cleanup(clean_md)

    size_kb = pdf_file.stat().st_size / 1024
    print(f"[render_pdf] PDF generated: {pdf_file}  ({size_kb:.0f} KB)")
    return pdf_file


def _cleanup(*paths):
    for p in paths:
        try:
            Path(p).unlink(missing_ok=True)
        except OSError:
            pass


# ---------------------------------------------------------------------------
# CLI entry point
# ---------------------------------------------------------------------------

def main():
    if len(sys.argv) < 2:
        print("Usage: python render_pdf.py <paper_dir> [journal]")
        sys.exit(1)

    paper_dir = sys.argv[1]

    # Resolve the journal profile so the standalone CLI honors citation style.
    # The journal is taken from argv[2] if given, otherwise from plan.json.
    journal_profile = _load_journal_profile_for_pdf(paper_dir,
                                                    sys.argv[2] if len(sys.argv) > 2 else None)

    result = render_pdf(paper_dir, journal_profile=journal_profile)
    if result is None:
        sys.exit(1)
    print(f"\nDone. PDF at: {result}")


def _load_journal_profile_for_pdf(paper_dir, journal=None):
    """Best-effort load of a journal profile for the standalone PDF CLI.

    Parameters
    ----------
    paper_dir : str or pathlib.Path
        Paper directory (used to read ``plan.json`` for ``target_journal``).
    journal : str, optional
        Explicit journal name; overrides ``plan.json``.

    Returns
    -------
    dict or None
        The journal profile, or ``None`` if it cannot be resolved.
    """
    if not journal:
        plan_path = Path(paper_dir) / "plan.json"
        if plan_path.exists():
            try:
                import json
                journal = json.loads(plan_path.read_text(encoding="utf-8")).get("target_journal")
            except (ValueError, OSError):
                journal = None
    if not journal:
        return None
    try:
        from paper_renderer import load_journal_profile
        journals_dir = Path(__file__).resolve().parent.parent / "journals"
        return load_journal_profile(journal, str(journals_dir))
    except Exception:
        return None


if __name__ == "__main__":
    main()
