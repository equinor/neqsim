"""Local (no-LLM) chapter expander for paperflow books.

Augments each chapter.md with deterministic, locally-sourced content blocks:
  - Worked examples linked to executed notebooks (with extracted snippet preview)
  - Self-test questions derived from H2/H3 headings
  - Key terms glossary harvested from bold-emphasized text
  - Chapter summary built from each section's opening sentence
  - Further reading pulled from refs.bib + cross-chapter links

Idempotent: each block is delimited by HTML sentinel comments and rewritten on
re-run. The original chapter prose (between frontmatter and the first sentinel)
is left untouched. No API keys required.

CLI:
    python -m book_local_expander BOOK_DIR [--chapter ch01_introduction ...]
    python -m book_local_expander BOOK_DIR --strip      # remove all blocks
"""
from __future__ import annotations

import argparse
import json
import re
from pathlib import Path
from typing import Dict, List, Optional, Tuple

try:
    import yaml
except ImportError:  # pragma: no cover
    yaml = None

# ---------------------------------------------------------------------------
# Sentinel markers (idempotency)
# ---------------------------------------------------------------------------

BEGIN = "<!-- begin: local-expander -->"
END = "<!-- end: local-expander -->"

FENCE_RE = re.compile(r"```python\n(.*?)\n```", re.DOTALL)
HEADING_RE = re.compile(r"^(##+)\s+(.+)$", re.MULTILINE)
FRONTMATTER_RE = re.compile(r"^---\n(.*?)\n---\n", re.DOTALL)
BOLD_RE = re.compile(r"\*\*([^*\n]{2,40})\*\*")
BIB_ENTRY_RE = re.compile(
    r"@\w+\{(?P<key>[^,]+),(?P<body>.*?)\n\}", re.DOTALL
)
BIB_FIELD_RE = re.compile(
    r'(\w+)\s*=\s*[\{"](.+?)["\}]\s*[,\n]', re.DOTALL
)


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def strip_block(text: str) -> str:
    """Remove an existing local-expander block (idempotent prep)."""
    pattern = re.compile(
        re.escape(BEGIN) + r".*?" + re.escape(END) + r"\n*",
        re.DOTALL,
    )
    return pattern.sub("", text).rstrip() + "\n"


def split_frontmatter(text: str) -> Tuple[str, str]:
    """Return (frontmatter_block, body)."""
    m = FRONTMATTER_RE.match(text)
    if not m:
        return "", text
    return text[: m.end()], text[m.end():]


def parse_sections(body: str) -> List[Tuple[int, str, int, int]]:
    """Find H2 sections.

    Returns list of (level, heading_text, start_offset, end_offset).
    """
    matches = list(HEADING_RE.finditer(body))
    sections: List[Tuple[int, str, int, int]] = []
    for i, m in enumerate(matches):
        level = len(m.group(1))
        if level != 2:
            continue
        heading = m.group(2).strip()
        start = m.end()
        # find next H2 to bound the section
        end = len(body)
        for n in matches[i + 1:]:
            if len(n.group(1)) == 2:
                end = n.start()
                break
        sections.append((level, heading, start, end))
    return sections


def first_sentence(paragraph: str) -> str:
    p = paragraph.strip()
    if not p:
        return ""
    # Drop leading list markers / quotes / bold-only lines
    lines = [ln for ln in p.splitlines() if ln.strip() and not ln.startswith(">")]
    if not lines:
        return ""
    text = " ".join(lines)
    # Strip markdown emphasis
    text = re.sub(r"\*+([^*]+)\*+", r"\1", text)
    text = re.sub(r"`([^`]+)`", r"\1", text)
    # First sentence — split on period followed by space + capital
    m = re.search(r"^(.{40,400}?[.!?])(\s|$)", text)
    if m:
        return m.group(1).strip()
    return text[:300].strip() + ("…" if len(text) > 300 else "")


def harvest_section_opening(body: str, start: int, end: int) -> str:
    """Return first non-empty paragraph after a heading."""
    chunk = body[start:end].lstrip("\n")
    paragraphs = re.split(r"\n\s*\n", chunk)
    for p in paragraphs:
        p = p.strip()
        if not p:
            continue
        if p.startswith("```") or p.startswith("|") or p.startswith("!["):
            continue
        if p.startswith("#"):
            continue
        return first_sentence(p)
    return ""


def harvest_key_terms(body: str, max_terms: int = 20) -> List[str]:
    """Extract bold-emphasized phrases that look like key terms."""
    seen: Dict[str, int] = {}
    for m in BOLD_RE.finditer(body):
        term = m.group(1).strip()
        # Skip headings-as-bold or numeric-only
        if re.fullmatch(r"[\d\s.,/-]+", term):
            continue
        if len(term.split()) > 6:
            continue
        key = term.lower()
        seen[key] = seen.get(key, 0) + 1
    # Rank by frequency, keep first occurrence casing
    casings: Dict[str, str] = {}
    for m in BOLD_RE.finditer(body):
        term = m.group(1).strip()
        casings.setdefault(term.lower(), term)
    ranked = sorted(seen.items(), key=lambda kv: -kv[1])
    out: List[str] = []
    for key, _ in ranked:
        out.append(casings[key])
        if len(out) >= max_terms:
            break
    return out


def parse_bibtex(bib_path: Path) -> Dict[str, Dict[str, str]]:
    if not bib_path.exists():
        return {}
    text = bib_path.read_text(encoding="utf-8", errors="replace")
    out: Dict[str, Dict[str, str]] = {}
    for m in BIB_ENTRY_RE.finditer(text):
        key = m.group("key").strip()
        body = m.group("body")
        fields = {f.lower(): v.strip() for f, v in BIB_FIELD_RE.findall(body + ",")}
        out[key] = fields
    return out


def find_cited_keys(body: str) -> List[str]:
    keys: List[str] = []
    for m in re.finditer(r"\\cite[ptn]?\{([^}]+)\}", body):
        for k in m.group(1).split(","):
            k = k.strip()
            if k and k not in keys:
                keys.append(k)
    return keys


# ---------------------------------------------------------------------------
# Block builders
# ---------------------------------------------------------------------------

def build_theory(
    ch_dir_name: str,
    theory_pack: Dict,
    bib: Optional[Dict[str, Dict[str, str]]] = None,
) -> str:
    """Build a 'Theoretical foundations and literature' block.

    Reads from chapter_theory.yaml entry. Renders equations as $$ KaTeX
    blocks with description, theory prose, literature review (with embedded
    \\cite keys), and an annotated bibliography of cited keys.
    """
    if not theory_pack:
        return ""
    lines = ["## Theoretical foundations and literature", ""]
    intro = theory_pack.get("intro", "").strip()
    if intro:
        lines.append(intro)
        lines.append("")

    eqs = theory_pack.get("equations") or []
    if eqs:
        lines.append("### Key equations")
        lines.append("")
        for i, eq in enumerate(eqs, 1):
            label = eq.get("label", f"Equation {i}").strip()
            latex = eq.get("latex", "").strip()
            desc = eq.get("description", "").strip()
            lines.append(f"**Equation {i} — {label}**")
            lines.append("")
            if latex:
                lines.append("$$")
                lines.append(latex)
                lines.append("$$")
                lines.append("")
            if desc:
                lines.append(desc)
                lines.append("")

    theory = theory_pack.get("theory", "").strip()
    if theory:
        lines.append("### Discussion")
        lines.append("")
        lines.append(theory)
        lines.append("")

    lit = theory_pack.get("literature_review", "").strip()
    if lit:
        lines.append("### Literature review")
        lines.append("")
        lines.append(lit)
        lines.append("")

    cites = theory_pack.get("citations") or []
    if cites:
        lines.append("### Annotated bibliography for this chapter")
        lines.append("")
        lines.append(
            "The following primary references are recommended for deeper "
            "study; full bibliographic details are in the back-matter "
            "bibliography."
        )
        lines.append("")
        bib = bib or {}
        for k in cites:
            entry = bib.get(k, {})
            title = entry.get("title", "").replace("{", "").replace("}", "").strip()
            author = entry.get("author", "").replace("{", "").replace("}", "").replace(" and ", "; ")
            year = entry.get("year", "").strip()
            if title:
                first_author = author.split(";")[0].strip() if author else ""
                bits = [b for b in [first_author, year] if b]
                tag = f" ({', '.join(bits)})" if bits else ""
                lines.append(f"- `\\cite{{{k}}}` — *{title}*{tag}.")
            else:
                lines.append(f"- `\\cite{{{k}}}`")
        lines.append("")

    return "\n".join(lines).rstrip() + "\n"


_BOILERPLATE_RE = re.compile(
    r"this notebook is auto-generated|replace any placeholder|all figures? "
    r"written to|produces? the figures? referenced",
    re.IGNORECASE,
)


def _summarise_notebook(data: dict) -> Tuple[str, List[str], List[str]]:
    """Return (title, descriptions, output_figures) for a notebook.

    - title: text of the first H1 markdown heading, or empty.
    - descriptions: section subheadings (H2) found in markdown cells, with
      "Figure — " prefixes stripped, used to describe what the calculation
      produces.
    - output_figures: file names (basename) of any png/svg/pdf written by the
      code cells (detected via string literals).
    """
    title = ""
    descriptions: List[str] = []
    figures: List[str] = []
    for cell in data.get("cells", []):
        ctype = cell.get("cell_type")
        src = "".join(cell.get("source", []))
        if ctype == "markdown":
            if not title:
                m = re.search(r"^#\s+(.+)$", src, re.MULTILINE)
                if m:
                    title = m.group(1).strip()
            for fm in re.finditer(r"^##\s+(.+?)\s*$", src, re.MULTILINE):
                desc = fm.group(1).strip().rstrip(".")
                # Strip leading "Figure — " or "Figure - " labels
                desc = re.sub(r"^Figure\s*[—\-–]\s*", "", desc, flags=re.I)
                if desc and not _BOILERPLATE_RE.search(desc):
                    descriptions.append(desc)
        elif ctype == "code":
            for m in re.finditer(
                r"""['"]([\w./\-]+\.(?:png|svg|pdf))['"]""", src
            ):
                fname = Path(m.group(1)).name
                if fname not in figures:
                    figures.append(fname)
    return title, descriptions, figures


def build_worked_examples(ch_dir: Path) -> str:
    nb_dir = ch_dir / "notebooks"
    if not nb_dir.is_dir():
        return ""
    notebooks = sorted(nb_dir.glob("*.ipynb"))
    if not notebooks:
        return ""
    lines = ["## Worked examples and computer experiments", ""]
    lines.append(
        "Each example below summarises a computational case provided as a "
        "runnable Jupyter notebook. We describe what the calculation does, "
        "what assumptions enter, and what the numerical result tells us "
        "about the underlying physics; the full source listings, including "
        "all NeqSim API calls and plotting code, are collected in "
        "**Appendix A — Computational notebooks** at the back of the book."
    )
    lines.append("")
    for i, nb in enumerate(notebooks, 1):
        try:
            data = json.loads(nb.read_text(encoding="utf-8"))
        except Exception:
            continue
        title, descriptions, figures = _summarise_notebook(data)
        if not title:
            title = nb.stem.replace("_", " ")
        short_title = title
        for sep in (" — ", " -- ", " - "):
            if sep in short_title:
                short_title = short_title.split(sep)[-1].strip()
        short_title = re.sub(r"^\d+(\.\d+)*\s+", "", short_title).strip()
        rel = nb.relative_to(ch_dir).as_posix()
        # Emit as a styled HTML callout (passthrough block)
        lines.append("")
        lines.append('<div class="worked-example">')
        lines.append(f"<h4>Example {i} — {short_title}</h4>")
        if descriptions:
            topic = descriptions[0]
            topic_lc = topic[0].lower() + topic[1:] if topic else ""
            lines.append(
                f"<p>Computes {topic_lc}.</p>"
            )
            if len(descriptions) > 1:
                rest = "; ".join(
                    (d[0].lower() + d[1:]) for d in descriptions[1:]
                )
                lines.append(f"<p>Additional outputs: {rest}.</p>")
        else:
            lines.append(
                f"<p>Reproduces the numerical case associated with "
                f"<em>{short_title}</em>.</p>"
            )
        meta_bits = []
        if figures:
            fig_list = ", ".join(f"<code>{f}</code>" for f in figures)
            meta_bits.append(f"<strong>Outputs:</strong> {fig_list}")
        meta_bits.append(
            f"<strong>Notebook:</strong> "
            f"<code>chapters/{ch_dir.name}/{rel}</code> "
            f"(full listing in Appendix A)"
        )
        lines.append(
            '<p class="we-meta">' + " &middot; ".join(meta_bits) + "</p>"
        )
        lines.append("</div>")
        lines.append("")
    return "\n".join(lines).rstrip() + "\n"


def build_self_test(headings: List[str]) -> str:
    if not headings:
        return ""
    lines = ["## Self-test questions", ""]
    lines.append(
        "These questions are intended for self-assessment and seminar "
        "discussion. They exercise both qualitative understanding and "
        "quantitative reasoning at the level expected of TPG4230 students."
    )
    lines.append("")
    templates = [
        "Define **{h}** in your own words and explain why it matters for field development decisions.",
        "List the principal assumptions, governing equations, or input data required for **{h}**.",
        "Describe a realistic operating scenario in which **{h}** would be the dominant design driver.",
        "Identify two failure modes or error sources associated with **{h}** and propose mitigating actions.",
        "Compare **{h}** with an alternative approach discussed earlier in the chapter; under which conditions does each one win?",
        "Sketch the qualitative trend of the key output of **{h}** as one main input is varied; justify the shape from first principles.",
    ]
    for i, h in enumerate(headings, 1):
        # Strip leading numbers like "1.3"
        clean = re.sub(r"^\d+(\.\d+)*\s+", "", h).strip()
        tmpl = templates[(i - 1) % len(templates)]
        lines.append(f"{i}. {tmpl.format(h=clean)}")
    lines.append("")
    return "\n".join(lines).rstrip() + "\n"


def build_glossary(terms: List[str]) -> str:
    if not terms:
        return ""
    lines = ["## Key terms", ""]
    lines.append(
        "The following key terms appeared in the chapter. Readers should be "
        "able to define each term, give a representative numerical range "
        "where applicable, and state its role in the field-development "
        "workflow. Definitions can be cross-checked against the book "
        "glossary in the back-matter and the cited primary references."
    )
    lines.append("")
    for t in terms:
        lines.append(f"- **{t}**")
    lines.append("")
    return "\n".join(lines).rstrip() + "\n"


def build_summary(section_titles: List[str], openings: List[str]) -> str:
    if not section_titles:
        return ""
    lines = ["## Chapter summary", ""]
    lines.append(
        "The chapter developed the following ideas in sequence:"
    )
    lines.append("")
    for h, op in zip(section_titles, openings):
        clean_h = re.sub(r"^\d+(\.\d+)*\s+", "", h).strip()
        if op:
            lines.append(f"- **{clean_h}.** {op}")
        else:
            lines.append(f"- **{clean_h}.**")
    lines.append("")
    lines.append(
        "Taken together, these ideas form the conceptual scaffolding that "
        "subsequent chapters build on. Where the chapter introduced a NeqSim "
        "workflow, the worked-example notebooks above can be used to convert "
        "the qualitative narrative into quantitative engineering deliverables."
    )
    lines.append("")
    return "\n".join(lines).rstrip() + "\n"


def build_further_reading(
    body: str, bib: Dict[str, Dict[str, str]], chapter_index: List[Tuple[int, str, str]],
    ch_number: int,
) -> str:
    cited = find_cited_keys(body)
    lines = ["## Further reading", ""]
    if cited:
        lines.append(
            "The following references were cited in the chapter; full "
            "bibliographic details are listed in the back-matter "
            "bibliography."
        )
        lines.append("")
        for key in cited:
            entry = bib.get(key, {})
            title = entry.get("title", "").replace("{", "").replace("}", "")
            author = entry.get("author", "").replace("{", "").replace("}", "").replace(" and ", "; ")
            year = entry.get("year", "")
            if title:
                authors = author.split(";")[0].strip() if author else ""
                bits = [b for b in [authors, year] if b]
                tag = f" ({', '.join(bits)})" if bits else ""
                lines.append(f"- `\\cite{{{key}}}` — *{title}*{tag}.")
            else:
                lines.append(f"- `\\cite{{{key}}}`")
        lines.append("")
    if chapter_index:
        lines.append("**Related chapters in this book:**")
        lines.append("")
        # Suggest neighbouring chapters
        neighbours = [
            (n, t, d) for (n, t, d) in chapter_index
            if abs(n - ch_number) <= 2 and n != ch_number
        ]
        for n, t, d in neighbours:
            lines.append(f"- Chapter {n} — *{t}* (`chapters/{d}/chapter.md`)")
        lines.append("")
    return "\n".join(lines).rstrip() + "\n"


# ---------------------------------------------------------------------------
# Driver
# ---------------------------------------------------------------------------

def load_chapter_index(book_dir: Path) -> List[Tuple[int, str, str]]:
    """Return [(number, title, dir_name)] from book.yaml."""
    if yaml is None:
        return []
    cfg_path = book_dir / "book.yaml"
    if not cfg_path.exists():
        return []
    cfg = yaml.safe_load(cfg_path.read_text(encoding="utf-8")) or {}
    out: List[Tuple[int, str, str]] = []
    n = 0
    for part in cfg.get("parts", []) or []:
        for ch in part.get("chapters", []) or []:
            n += 1
            out.append((n, ch.get("title", f"Chapter {n}"), ch.get("dir", "")))
    if not out:
        for ch in cfg.get("chapters", []) or []:
            n += 1
            out.append((n, ch.get("title", f"Chapter {n}"), ch.get("dir", "")))
    return out


def load_chapter_theory(book_dir: Path) -> Dict[str, Dict]:
    """Load chapter_theory.yaml mapping chapter_dir → theory pack."""
    if yaml is None:
        return {}
    p = book_dir / "chapter_theory.yaml"
    if not p.exists():
        return {}
    data = yaml.safe_load(p.read_text(encoding="utf-8")) or {}
    return data if isinstance(data, dict) else {}


def expand_chapter(
    ch_dir: Path,
    bib: Dict[str, Dict[str, str]],
    chapter_index: List[Tuple[int, str, str]],
    ch_number: int,
    theory_packs: Optional[Dict[str, Dict]] = None,
    strip_only: bool = False,
) -> Tuple[bool, int]:
    md_path = ch_dir / "chapter.md"
    if not md_path.exists():
        return False, 0
    text = md_path.read_text(encoding="utf-8")
    text = strip_block(text)
    if strip_only:
        md_path.write_text(text, encoding="utf-8")
        return True, 0

    fm, body = split_frontmatter(text)
    sections = parse_sections(body)
    section_headings = [h for _, h, _, _ in sections]
    section_openings = [harvest_section_opening(body, s, e) for _, _, s, e in sections]

    blocks: List[str] = []
    pack = (theory_packs or {}).get(ch_dir.name)
    th = build_theory(ch_dir.name, pack or {}, bib)
    if th:
        blocks.append(th)
    we = build_worked_examples(ch_dir)
    if we:
        blocks.append(we)
    st = build_self_test(section_headings)
    if st:
        blocks.append(st)
    gl = build_glossary(harvest_key_terms(body))
    if gl:
        blocks.append(gl)
    sm = build_summary(section_headings, section_openings)
    if sm:
        blocks.append(sm)
    fr = build_further_reading(body, bib, chapter_index, ch_number)
    if fr:
        blocks.append(fr)

    if not blocks:
        return False, 0

    appended = "\n".join(["", BEGIN, ""] + blocks + [END, ""])
    new_text = fm + body.rstrip() + "\n" + appended
    md_path.write_text(new_text, encoding="utf-8")
    added_words = sum(len(b.split()) for b in blocks)
    return True, added_words


def expand_book(
    book_dir: Path,
    chapters: Optional[List[str]] = None,
    strip_only: bool = False,
) -> None:
    chapters_root = book_dir / "chapters"
    bib = parse_bibtex(book_dir / "refs.bib")
    chapter_index = load_chapter_index(book_dir)
    name_to_num = {d: n for (n, _, d) in chapter_index}
    theory_packs = load_chapter_theory(book_dir)

    total_added = 0
    touched = 0
    for ch in sorted(chapters_root.iterdir()):
        if not ch.is_dir():
            continue
        if chapters and ch.name not in chapters:
            continue
        ch_number = name_to_num.get(ch.name, 0)
        ok, added = expand_chapter(
            ch, bib, chapter_index, ch_number,
            theory_packs=theory_packs, strip_only=strip_only,
        )
        if ok:
            touched += 1
            total_added += added
            tag = "stripped" if strip_only else f"+{added} words"
            print(f"  {ch.name}: {tag}")
    action = "stripped" if strip_only else "expanded"
    print(f"\n{action} {touched} chapter(s); total added words: {total_added}")

    # Regenerate the notebooks appendix in the backmatter (idempotent).
    if not strip_only and not chapters:
        try:
            n = build_notebooks_appendix(book_dir, chapter_index)
            print(f"  notebooks appendix: {n} notebook(s) listed")
        except Exception as exc:  # pragma: no cover
            print(f"  notebooks appendix: skipped ({exc})")


def build_notebooks_appendix(
    book_dir: Path,
    chapter_index: List[Tuple[int, str, str]],
) -> int:
    """Write backmatter/notebooks_appendix.md listing every chapter notebook.

    Each notebook gets its title, a one-line summary derived from markdown
    cells, the list of output figures, and the relative path. The file is
    overwritten on every run so it stays in sync with the chapter notebooks.
    """
    backmatter = book_dir / "backmatter"
    backmatter.mkdir(exist_ok=True)
    out_path = backmatter / "notebooks_appendix.md"

    lines: List[str] = []
    lines.append("# Appendix A — Computational notebooks")
    lines.append("")
    lines.append(
        "This appendix lists every Jupyter notebook bundled with the book, "
        "grouped by chapter. The notebooks are the authoritative source for "
        "the numerical results discussed in the *Worked examples and "
        "computer experiments* sections; the chapter prose summarises what "
        "each calculation does, while the listings here let the reader run, "
        "modify, and extend the cases."
    )
    lines.append("")
    lines.append(
        "All notebooks execute end-to-end against the public NeqSim release "
        "installed via `pip install neqsim`. Paths are given relative to the "
        "book root."
    )
    lines.append("")

    total = 0
    for ch_num, ch_title, ch_dir_name in chapter_index:
        ch_dir = book_dir / "chapters" / ch_dir_name
        nb_dir = ch_dir / "notebooks"
        if not nb_dir.is_dir():
            continue
        notebooks = sorted(nb_dir.glob("*.ipynb"))
        if not notebooks:
            continue
        lines.append(f"## Chapter {ch_num}. {ch_title}")
        lines.append("")
        for nb in notebooks:
            try:
                data = json.loads(nb.read_text(encoding="utf-8"))
            except Exception:
                continue
            title, descriptions, figures = _summarise_notebook(data)
            if not title:
                title = nb.stem.replace("_", " ")
            short_title = title
            for sep in (" — ", " -- ", " - "):
                if sep in short_title:
                    short_title = short_title.split(sep)[-1].strip()
            short_title = re.sub(
                r"^\d+(\.\d+)*\s+", "", short_title
            ).strip()
            rel = nb.relative_to(book_dir).as_posix()
            lines.append(f"**{short_title}**")
            lines.append("")
            if descriptions:
                summary = "; ".join(descriptions)
                lines.append(f"Computes {summary[0].lower() + summary[1:]}.")
            if figures:
                fig_list = ", ".join(f"`{f}`" for f in figures)
                lines.append("")
                lines.append(f"Output figures: {fig_list}.")
            lines.append("")
            lines.append(f"Notebook: `{rel}`.")
            lines.append("")
            total += 1
    out_path.write_text("\n".join(lines).rstrip() + "\n", encoding="utf-8")
    return total



def main(argv: Optional[List[str]] = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("book_dir", type=Path)
    ap.add_argument(
        "--chapter", action="append", default=None,
        help="Limit to one chapter directory (repeatable)."
    )
    ap.add_argument(
        "--strip", action="store_true",
        help="Remove existing local-expander blocks instead of adding them."
    )
    args = ap.parse_args(argv)
    expand_book(args.book_dir, chapters=args.chapter, strip_only=args.strip)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
