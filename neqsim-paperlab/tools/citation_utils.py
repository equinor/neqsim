"""
Shared citation utilities for paper and book renderers.

Provides BibTeX parsing, citation resolution, and bibliography generation
that can be reused across HTML, Word, and PDF pipelines.
"""

import re
from pathlib import Path


def parse_bibtex(bib_path):
    """Parse a BibTeX file into a dict of key -> {author, title, journal, year, ...}.

    Parameters
    ----------
    bib_path : str or Path
        Path to the .bib file.

    Returns
    -------
    dict
        Mapping citation key -> dict of fields.
    """
    bib_path = Path(bib_path)
    if not bib_path.exists():
        return {}
    bib_text = bib_path.read_text(encoding="utf-8")
    entries = {}
    for block in re.finditer(r'@\w+\{(\w+),\s*(.*?)\n\}', bib_text, flags=re.DOTALL):
        key = block.group(1)
        fields = {}
        for fld in re.finditer(r'(\w+)\s*=\s*\{(.*?)\}', block.group(2), flags=re.DOTALL):
            fields[fld.group(1).lower()] = fld.group(2).strip()
        entries[key] = fields
    return entries


def clean_bibtex_latex(text):
    """Strip LaTeX markup from BibTeX field values to produce plain Unicode."""
    _accent_map = {
        '`': '\u0300', "'": '\u0301', '^': '\u0302', '"': '\u0308',
        '~': '\u0303', '=': '\u0304', '.': '\u0307', 'u': '\u0306',
        'v': '\u030C', 'H': '\u030B', 'c': '\u0327', 'd': '\u0323',
        'b': '\u0332', 'r': '\u030A', 'k': '\u0328',
    }
    _special_map = {
        'o': '\u00f8', 'O': '\u00d8', 'l': '\u0142', 'L': '\u0141',
        'aa': '\u00e5', 'AA': '\u00c5', 'ae': '\u00e6', 'AE': '\u00c6',
        'oe': '\u0153', 'OE': '\u0152', 'ss': '\u00df', 'i': '\u0131', 'j': '\u0237',
    }

    def _repl_accent(m):
        cmd = m.group(1)
        char = m.group(2)
        if cmd in _accent_map:
            import unicodedata
            return unicodedata.normalize('NFC', char + _accent_map[cmd])
        return char

    text = re.sub(r'\\([`\'^"~=.ubvHcdrk])\{(\w)\}', _repl_accent, text)
    text = re.sub(r'\{\\([`\'^"~=.ubvHcdrk])(\w)\}', _repl_accent, text)
    for ltx, uni in _special_map.items():
        text = text.replace('{\\' + ltx + '}', uni)
        text = text.replace('\\' + ltx + '{}', uni)
        text = text.replace('\\' + ltx + ' ', uni + ' ')
    text = re.sub(r'\{([^{}]*)\}', r'\1', text)
    text = re.sub(r'\\([&%#_])', r'\1', text)
    return text


def format_author_short(author_str):
    """Shorten 'Last, F. and Last2, F.' to 'Last et al.' or 'Last and Last2'."""
    author_str = author_str.replace("{", "").replace("}", "")
    authors = [a.strip() for a in author_str.split(" and ")]
    if len(authors) > 2:
        return authors[0].split(",")[0] + " et al."
    elif len(authors) == 2:
        return authors[0].split(",")[0] + " and " + authors[1].split(",")[0]
    else:
        return authors[0].split(",")[0]


def format_bib_entry_numbered(fields):
    """Format a BibTeX entry for numbered reference list (HTML)."""
    fields = {k: clean_bibtex_latex(v) for k, v in fields.items()}
    author = format_author_short(fields.get("author", "Unknown"))
    title = fields.get("title", "Untitled")
    journal = fields.get("journal", fields.get("booktitle", ""))
    year = fields.get("year", "n.d.")
    volume = fields.get("volume", "")
    pages = fields.get("pages", "")

    parts = [f"{author},"]
    parts.append(f'"{title},"')
    if journal:
        parts.append(f"<em>{journal}</em>,")
    if volume:
        vol_str = f"vol. {volume}"
        if pages:
            vol_str += f", pp. {pages}"
        parts.append(vol_str + ",")
    parts.append(f"{year}.")
    return " ".join(parts)


def format_bib_entry_plain(fields):
    """Format a BibTeX entry as plain text (no HTML tags) for Word/PDF."""
    fields = {k: clean_bibtex_latex(v) for k, v in fields.items()}
    author = format_author_short(fields.get("author", "Unknown"))
    title = fields.get("title", "Untitled")
    journal = fields.get("journal", fields.get("booktitle", ""))
    year = fields.get("year", "n.d.")
    volume = fields.get("volume", "")
    pages = fields.get("pages", "")
    publisher = fields.get("publisher", "")

    parts = [f"{author},"]
    parts.append(f'"{title},"')
    if journal:
        parts.append(f"{journal},")
    if volume:
        vol_str = f"vol. {volume}"
        if pages:
            vol_str += f", pp. {pages}"
        parts.append(vol_str + ",")
    if publisher:
        parts.append(f"{publisher},")
    parts.append(f"{year}.")
    return " ".join(parts)


def format_bib_entry_authoryear(fields):
    """Format a BibTeX entry for author-year reference list."""
    fields = {k: clean_bibtex_latex(v) for k, v in fields.items()}
    author = fields.get("author", "Unknown")
    title = fields.get("title", "Untitled")
    journal = fields.get("journal", fields.get("booktitle", ""))
    year = fields.get("year", "n.d.")
    volume = fields.get("volume", "")
    pages = fields.get("pages", "")

    parts = [f"{author},"]
    parts.append(f"{year}.")
    parts.append(f"{title}.")
    if journal:
        parts.append(f"<em>{journal}</em>")
        if volume:
            parts.append(f"{volume}")
            if pages:
                parts[-1] += f", {pages}"
        parts[-1] += "."
    return " ".join(parts)


# ---------------------------------------------------------------------------
# Citation resolution — numbered style
# ---------------------------------------------------------------------------

def collect_cited_keys(text):
    """Extract all citation keys from \\cite{key1, key2} in order of appearance.

    Returns a list of unique keys in order of first appearance.
    """
    keys_in_order = []
    for m in re.finditer(r'\\cite\{([^}]+)\}', text):
        for key in m.group(1).split(","):
            key = key.strip()
            if key and key not in keys_in_order:
                keys_in_order.append(key)
    return keys_in_order


def resolve_citations_numbered_html(body, bib_entries, keys_in_order=None):
    """Replace \\cite{key} with [N] hyperlinks and return (body, ref_html).

    Parameters
    ----------
    body : str
        Markdown/HTML text with \\cite{key} references.
    bib_entries : dict
        Parsed BibTeX entries.
    keys_in_order : list or None
        Pre-collected keys (from collect_cited_keys across all chapters).
        If None, collected from body text.

    Returns
    -------
    tuple
        (body_with_citations, reference_list_html)
    """
    if keys_in_order is None:
        keys_in_order = collect_cited_keys(body)
    key_to_num = {k: i + 1 for i, k in enumerate(keys_in_order)}

    def replace_cite(match):
        keys = [k.strip() for k in match.group(1).split(",")]
        nums = []
        for k in keys:
            n = key_to_num.get(k)
            if n:
                nums.append(f'<a href="#ref-{n}" class="cite">{n}</a>')
            else:
                nums.append(f'<span class="cite-unknown">{k}</span>')
        return "[" + ", ".join(nums) + "]"

    body = re.sub(r'\\cite\{([^}]+)\}', replace_cite, body)

    # Build reference list HTML
    ref_html = '\n<h2 id="references">References</h2>\n<ol class="references">\n'
    for key in keys_in_order:
        n = key_to_num[key]
        fields = bib_entries.get(key, {})
        entry = format_bib_entry_numbered(fields) if fields else f"[{key}] -- not in refs.bib"
        ref_html += f'  <li id="ref-{n}" value="{n}">{entry}</li>\n'
    ref_html += "</ol>\n"

    return body, ref_html


def resolve_citations_numbered_plain(text, key_to_num):
    """Replace \\cite{key1, key2} with [N1, N2] as plain text.

    Parameters
    ----------
    text : str
        Text with \\cite{key} references.
    key_to_num : dict
        Mapping from citation key to number.

    Returns
    -------
    str
        Text with citations replaced by [N].
    """
    def _repl(m):
        keys = [k.strip() for k in m.group(1).split(",")]
        nums = []
        for k in keys:
            if k in key_to_num:
                nums.append(str(key_to_num[k]))
            else:
                nums.append(f"?{k}")
        return "[" + ", ".join(sorted(nums, key=lambda x: int(x) if x.isdigit() else 999)) + "]"
    return re.sub(r'\\cite\{([^}]+)\}', _repl, text)


def build_key_to_num(keys_in_order):
    """Build a citation key -> number mapping from ordered keys."""
    return {k: i + 1 for i, k in enumerate(keys_in_order)}


def collect_all_cited_keys_from_chapters(book_dir, cfg):
    """Collect all cited keys across all chapters in a book, in order of appearance.

    Parameters
    ----------
    book_dir : Path
        Book project directory.
    cfg : dict
        Parsed book.yaml.

    Returns
    -------
    list
        Unique citation keys in order of first appearance.
    """
    import book_builder

    all_keys = []
    # Frontmatter (preface, acknowledgements, etc.) — scan first so cite
    # numbers in the preface come out as [1], [2], … in reading order.
    for fm in cfg.get("frontmatter", []):
        fm_path = book_dir / "frontmatter" / f"{fm}.md"
        if fm_path.exists():
            text = fm_path.read_text(encoding="utf-8")
            for key in collect_cited_keys(text):
                if key not in all_keys:
                    all_keys.append(key)
    for ch_num, ch, part_title in book_builder.iter_chapters(cfg):
        ch_dir = book_builder.resolve_chapter_dir(book_dir, ch)
        ch_md = ch_dir / "chapter.md"
        if ch_md.exists():
            text = ch_md.read_text(encoding="utf-8")
            for key in collect_cited_keys(text):
                if key not in all_keys:
                    all_keys.append(key)
    return all_keys


# ---------------------------------------------------------------------------
# Auto-injection: convert author-year prose references to \cite{key}
# ---------------------------------------------------------------------------

def _build_authoryear_patterns(bib_entries):
    """Build regex patterns to match author-year prose references.

    For each bib entry, creates patterns like:
      - "Kontogeorgis et al. (1996)"
      - "Kontogeorgis et al., 1996"
      - "Kontogeorgis and Folas (2010)"
      - "Wertheim (1984)"
      - "(Kontogeorgis et al., 1996)"

    Returns list of (compiled_regex, bib_key) tuples, longest-first to avoid
    partial matches.
    """
    patterns = []
    for key, fields in bib_entries.items():
        author_raw = fields.get("author", "")
        year = fields.get("year", "")
        if not author_raw or not year:
            continue

        author_clean = clean_bibtex_latex(author_raw)
        authors = [a.strip() for a in author_clean.split(" and ")]

        # Extract last names
        last_names = []
        for a in authors:
            if "," in a:
                last_names.append(a.split(",")[0].strip())
            else:
                parts = a.strip().split()
                last_names.append(parts[-1] if parts else a)

        if not last_names:
            continue

        # Build the name portion
        if len(last_names) == 1:
            name_part = re.escape(last_names[0])
        elif len(last_names) == 2:
            name_part = re.escape(last_names[0]) + r"\s+and\s+" + re.escape(last_names[1])
        else:
            name_part = re.escape(last_names[0]) + r"\s+et\s+al\."

        # Match forms:
        #   Name (YEAR)        ->  "Kontogeorgis et al. (1996)"
        #   Name, YEAR         ->  "Kontogeorgis et al., 1996"  (inside parens)
        #   (Name, YEAR)       ->  "(Kontogeorgis et al., 1996)"
        #   (Name et al., YEAR)->  "(Kontogeorgis et al., 1996)"
        yr = re.escape(year)

        # Form 1: Name (YEAR) or Name (YEARa) or Name (YEARb)
        pat1 = name_part + r"\s*\(" + yr + r"[a-z]?\)"
        # Form 2: (Name, YEAR) or (Name et al., YEAR)
        pat2 = r"\(" + name_part + r",?\s*" + yr + r"[a-z]?\)"

        for pat_str in [pat1, pat2]:
            try:
                patterns.append((re.compile(pat_str), key, len(pat_str)))
            except re.error:
                continue

    # Sort longest pattern first to match more specific references first
    patterns.sort(key=lambda x: -x[2])
    return [(p, k) for p, k, _ in patterns]


def inject_citations(text, bib_entries):
    r"""Replace author-year prose references with \cite{key} markup.

    Matches patterns like "Kontogeorgis et al. (1996)" against the provided
    bib_entries and replaces them with ``\cite{Kontogeorgis1996}``.

    Already-cited references (existing \cite{key}) are left untouched.

    Parameters
    ----------
    text : str
        Chapter markdown text.
    bib_entries : dict
        Parsed BibTeX entries from parse_bibtex().

    Returns
    -------
    tuple
        (modified_text, injection_count) where injection_count is the number
        of replacements made.
    """
    if not bib_entries:
        return text, 0

    patterns = _build_authoryear_patterns(bib_entries)
    count = 0

    for pattern, key in patterns:
        def _make_replacer(cite_key):
            def _replacer(m):
                return "\\cite{" + cite_key + "}"
            return _replacer

        new_text = pattern.sub(_make_replacer(key), text)
        replacements = len(pattern.findall(text))
        if replacements > 0:
            count += replacements
            text = new_text

    return text, count
