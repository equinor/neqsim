"""Back-of-book index extractor.

Scans every chapter for ``<!-- @index: term -->`` markers and inline
``\\index{term}`` LaTeX-style markers, builds a sorted index with chapter
references, and emits ``backmatter/index.md`` automatically.

Marker forms supported in chapter.md:

    Lorem ipsum dolor sit amet.<!-- @index: equation of state -->

    The Soave alpha function \\index{alpha function!Soave} is monotonic …

    \\index{methane}                # implicit primary entry
    \\index{methane!critical point} # subentry

The emitted appendix is plain markdown (alphabetised, with chapter
numbers as locators) so it works in every renderer including EPUB.
"""
from __future__ import annotations

import re
import sys
from collections import defaultdict
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import book_builder  # type: ignore


HTML_RE = re.compile(r"<!--\s*@index:\s*([^>]+?)\s*-->", re.IGNORECASE)
LATEX_RE = re.compile(r"\\index\{([^}]+)\}")


def _normalize(term: str) -> tuple[str, str | None]:
    """Return (primary, sub-entry-or-None). Splits on the LaTeX ``!``."""
    if "!" in term:
        a, _, b = term.partition("!")
        return a.strip(), b.strip()
    return term.strip(), None


def collect_index(book_dir) -> dict[str, dict[str | None, list[int]]]:
    book_dir = Path(book_dir)
    cfg = book_builder.load_book_config(book_dir)
    idx: dict[str, dict[str | None, list[int]]] = defaultdict(lambda: defaultdict(list))
    for ch_num, ch, _ in book_builder.iter_chapters(cfg):
        ch_dir = book_builder.resolve_chapter_dir(book_dir, ch)
        cm = ch_dir / "chapter.md"
        if not cm.exists():
            continue
        text = cm.read_text(encoding="utf-8")
        terms = list(HTML_RE.findall(text)) + list(LATEX_RE.findall(text))
        for raw in terms:
            primary, sub = _normalize(raw)
            if not primary:
                continue
            chs = idx[primary][sub]
            if ch_num not in chs:
                chs.append(ch_num)
    return idx


def render_index_md(idx: dict[str, dict[str | None, list[int]]]) -> str:
    if not idx:
        return "# Index\n\n*(no entries)*\n"
    lines = ["# Index", ""]
    last_letter = ""
    for primary in sorted(idx.keys(), key=str.lower):
        first = primary[:1].upper()
        if first != last_letter:
            lines.append(f"\n## {first}\n")
            last_letter = first
        subs = idx[primary]
        # primary line: chapters where the primary is the only entry
        main_chs = subs.get(None, [])
        if main_chs:
            locs = ", ".join(str(c) for c in sorted(main_chs))
            lines.append(f"- **{primary}**, ch. {locs}")
        else:
            lines.append(f"- **{primary}**")
        for sub in sorted([s for s in subs if s is not None], key=str.lower):
            locs = ", ".join(str(c) for c in sorted(subs[sub]))
            lines.append(f"  - {sub}, ch. {locs}")
    lines.append("")
    return "\n".join(lines)


def write_index(book_dir, force: bool = True) -> Path:
    book_dir = Path(book_dir)
    idx = collect_index(book_dir)
    bm = book_dir / "backmatter"
    bm.mkdir(exist_ok=True)
    out = bm / "index.md"
    if out.exists() and not force:
        # Only overwrite if marker present
        existing = out.read_text(encoding="utf-8")
        if "<!-- @auto-generated: book_index -->" not in existing:
            print(f"[book_index] {out} is hand-edited; skipping (use force=True)")
            return out
    body = render_index_md(idx)
    out.write_text("<!-- @auto-generated: book_index -->\n" + body, encoding="utf-8")
    n_primary = len(idx)
    n_sub = sum(len(s) - (1 if None in s else 0) for s in idx.values())
    print(f"[book_index] wrote {out}  ({n_primary} primary entries, {n_sub} sub-entries)")
    return out
