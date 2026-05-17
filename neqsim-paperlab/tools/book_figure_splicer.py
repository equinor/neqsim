"""Anchor figures into the right section of a book chapter.md.

Problem this solves:
    `tools.book_notebook_runner.inject_figures_into_chapter` appends every
    unreferenced figure into a single `## Figures` block at the end of a
    chapter, regardless of subject. Readers see figures detached from the
    prose that explains them.

What this tool does:
    1. Reads `chapter.md` and parses its `^## ` headings into sections.
    2. Reads `figures/*.png` and, for each figure, scores every section by
       keyword overlap between the filename and the section title + body.
    3. Moves the figure reference from the orphan `## Figures` block to the
       end of the best-matching section, written as:

           ![caption](figures/X.png)

           *Figure N.k — caption.*

           <!-- TODO: figure_discussion via `paperflow.py generate-context` -->

    4. Deletes the now-empty `## Figures` block.

Modes:
    - default (no-vision): placement + filename-derived caption + TODO marker.
    - --vision: also calls `tools.result_evaluator.generate_figure_context`
      to populate observation / mechanism / implication / recommendation.

CLI:
    python -m tools.book_figure_splicer <chapter_dir>            # one chapter
    python -m tools.book_figure_splicer --book <book_dir>        # all chapters
    add --dry-run to preview placements without writing.
"""

from __future__ import annotations

import argparse
import re
import shutil
import sys
from pathlib import Path

STOP = {
    "the", "a", "and", "or", "for", "of", "to", "in", "on",
    "fig", "figure", "ch", "s", "ex",
}

FIGURE_BLOCK_RE = re.compile(
    r"\n## Figures\b.*?(?=\n## |\Z)", re.DOTALL
)
INLINE_FIG_RE = re.compile(r"!\[[^\]]*\]\(figures/([^)]+)\)")
HEADING_RE = re.compile(r"^(#{2,3})\s+(.+)$", re.MULTILINE)


def keywords_from_filename(fname: str) -> set:
    """Extract topic keywords from a figure filename."""
    stem = Path(fname).stem.lower()
    # strip chapter / section / example prefixes
    stem = re.sub(r"^(fig_)?ch\d+_", "", stem)
    stem = re.sub(r"^s\d+_", "", stem)
    stem = re.sub(r"^ex\d+_", "", stem)
    tokens = re.split(r"[_\-]+", stem)
    return {t for t in tokens if t and t not in STOP and not t.isdigit()}


def parse_sections(text: str):
    """Return list of (level, title, start, end) for each ## or ### heading."""
    matches = list(HEADING_RE.finditer(text))
    sections = []
    for i, m in enumerate(matches):
        end = matches[i + 1].start() if i + 1 < len(matches) else len(text)
        sections.append({
            "level": len(m.group(1)),
            "title": m.group(2).strip(),
            "start": m.start(),
            "body_start": m.end(),
            "end": end,
        })
    return sections


def score_section(section: dict, kws: set, text: str) -> int:
    """Keyword overlap between figure keywords and section title + body."""
    if not kws:
        return 0
    title = section["title"].lower()
    # don't anchor into Figures, Exercises, Summary, etc.
    skip = {"figures", "exercises", "summary", "learning objectives",
            "self-test questions", "key terms", "further reading",
            "chapter summary", "theoretical foundations and literature",
            "worked examples and computer experiments"}
    if any(s in title for s in skip):
        return -1
    body = text[section["body_start"]:section["end"]].lower()
    title_words = set(re.findall(r"\b[a-z]+\b", title))
    body_words = set(re.findall(r"\b[a-z]+\b", body))
    score = 0
    for kw in kws:
        if kw in title_words:
            score += 3
        if kw in body_words:
            score += 1
    # prefer ## over ### (top-level sections)
    if section["level"] == 2:
        score += 1
    return score


def best_section_for(fname: str, sections: list, text: str):
    kws = keywords_from_filename(fname)
    scored = [(score_section(s, kws, text), i, s) for i, s in enumerate(sections)]
    scored.sort(key=lambda x: (-x[0], x[1]))
    if not scored or scored[0][0] <= 0:
        return None, kws
    return scored[0][2], kws


def caption_from_filename(fname: str, fig_num: str) -> str:
    stem = Path(fname).stem
    stem = re.sub(r"^(fig_)?ch\d+_", "", stem)
    stem = re.sub(r"^s\d+_", "", stem)
    stem = re.sub(r"^ex\d+_", "", stem)
    text = stem.replace("_", " ").strip()
    # title-case but preserve common abbreviations
    words = []
    for w in text.split():
        if w.upper() in {"IPR", "VLP", "PVT", "GOR", "API", "BHP", "MEG",
                         "TEG", "NPV", "IRR", "CO2", "H2S", "LNG", "FPSO",
                         "NCS", "NORSOK"}:
            words.append(w.upper())
        else:
            words.append(w.capitalize())
    return " ".join(words)


def render_figure_block(fname: str, fig_num: str, caption: str,
                        vision_data: dict | None = None) -> str:
    """Build the markdown block to splice into a section."""
    lines = [
        "",
        f"![Figure {fig_num} — {caption}.](figures/{fname})",
        "",
        f"*Figure {fig_num} — {caption}.*",
        "",
    ]
    if vision_data:
        lines.append(f"**Discussion (Figure {fig_num}).**")
        if vision_data.get("observation"):
            lines.append(f"*Observation.* {vision_data['observation']}")
        if vision_data.get("mechanism"):
            lines.append(f"*Mechanism.* {vision_data['mechanism']}")
        if vision_data.get("implication"):
            lines.append(f"*Implication.* {vision_data['implication']}")
        if vision_data.get("recommendation"):
            lines.append(f"*Recommendation.* {vision_data['recommendation']}")
        lines.append("")
    else:
        lines.append(
            "<!-- TODO: figure_discussion via "
            "`paperflow.py generate-context` -->"
        )
        lines.append("")
    return "\n".join(lines)


def splice_chapter(ch_dir: Path, dry_run: bool = False,
                   use_vision: bool = False) -> dict:
    """Anchor figures into chapter sections. Returns a placement report."""
    md = ch_dir / "chapter.md"
    fig_dir = ch_dir / "figures"
    if not md.exists() or not fig_dir.exists():
        return {"chapter": ch_dir.name, "placements": [], "skipped": "no md/figures"}

    text = md.read_text(encoding="utf-8")
    figures = sorted(f.name for f in fig_dir.glob("*.png"))
    if not figures:
        return {"chapter": ch_dir.name, "placements": []}

    # vision data lookup (optional)
    vision_lookup = {}
    if use_vision:
        try:
            sys.path.insert(0, str(Path(__file__).parent))
            from result_evaluator import generate_figure_context  # type: ignore
            ctxs = generate_figure_context(str(ch_dir))
            for c in ctxs:
                vision_lookup[c.figure_name] = {
                    "observation": getattr(c, "observation", "") or "",
                    "mechanism": getattr(c, "mechanism", "") or "",
                    "implication": getattr(c, "implication", "") or "",
                    "recommendation": getattr(c, "recommendation", "") or "",
                    "caption": getattr(c, "generated_caption", "") or "",
                }
        except Exception as e:
            print(f"  [warn] vision unavailable: {e}", file=sys.stderr)

    # detect chapter number for figure numbering
    m = re.match(r"ch(\d+)", ch_dir.name)
    ch_num = int(m.group(1)) if m else 0

    # find inline-referenced figures (skip those — already placed correctly)
    inline_refs = set(INLINE_FIG_RE.findall(text))
    # but exclude refs that are inside the orphan ## Figures block
    fig_block_match = FIGURE_BLOCK_RE.search(text)
    if fig_block_match:
        orphan_refs = set(INLINE_FIG_RE.findall(fig_block_match.group(0)))
        inline_refs -= orphan_refs

    sections = parse_sections(text)
    placements = []
    insertions = []  # list of (insert_pos, block_text) to apply later

    for i, fname in enumerate(figures, 1):
        fig_num = f"{ch_num}.{i}" if ch_num else str(i)
        if fname in inline_refs:
            placements.append({
                "figure": fname, "fig_num": fig_num,
                "section": "(already inline — skipped)",
                "score": None,
            })
            continue
        target, kws = best_section_for(fname, sections, text)
        vd = vision_lookup.get(fname)
        caption = (vd or {}).get("caption") or caption_from_filename(fname, fig_num)
        if target is None:
            placements.append({
                "figure": fname, "fig_num": fig_num,
                "section": "(no match — left in orphan block)",
                "score": 0, "keywords": sorted(kws),
            })
            continue
        block = render_figure_block(fname, fig_num, caption, vd)
        # insert at the end of the matched section (just before next heading)
        insert_pos = target["end"]
        # avoid splitting blank-line paragraphs
        while insert_pos > 0 and text[insert_pos - 1] == "\n" and \
                text[insert_pos - 2:insert_pos] == "\n\n":
            insert_pos -= 1
        insertions.append((insert_pos, block + "\n"))
        placements.append({
            "figure": fname, "fig_num": fig_num,
            "section": target["title"],
            "score": score_section(target, kws, text),
            "keywords": sorted(kws),
        })

    if dry_run:
        return {"chapter": ch_dir.name, "placements": placements,
                "would_remove_orphan": bool(fig_block_match)}

    # apply insertions in reverse position order (so earlier offsets stay valid)
    new_text = text
    for pos, block in sorted(insertions, key=lambda x: -x[0]):
        new_text = new_text[:pos] + block + new_text[pos:]

    # remove orphan ## Figures block
    if fig_block_match:
        # recompute against new_text (positions changed); match again
        new_text = FIGURE_BLOCK_RE.sub("\n", new_text, count=1)

    if new_text != text:
        backup = md.with_suffix(".md.bak")
        if not backup.exists():
            shutil.copy2(md, backup)
        md.write_text(new_text, encoding="utf-8")

    return {"chapter": ch_dir.name, "placements": placements,
            "removed_orphan": bool(fig_block_match),
            "wrote": new_text != text}


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("chapter_dir", nargs="?",
                    help="Path to a chapters/chNN_*/ directory.")
    ap.add_argument("--book", help="Path to a book root (process every chapter).")
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument("--vision", action="store_true",
                    help="Use vision LLM via generate-context for full discussion.")
    args = ap.parse_args()

    if not args.chapter_dir and not args.book:
        ap.error("provide chapter_dir or --book")

    targets = []
    if args.book:
        book_root = Path(args.book) / "chapters"
        targets = sorted(p for p in book_root.iterdir() if p.is_dir())
    else:
        targets = [Path(args.chapter_dir)]

    for ch in targets:
        report = splice_chapter(ch, dry_run=args.dry_run, use_vision=args.vision)
        print(f"\n=== {report['chapter']} ===")
        for p in report.get("placements", []):
            line = f"  [{p['fig_num']}] {p['figure']}  →  {p['section']}"
            if p.get("score") is not None:
                line += f"  (score={p['score']})"
            print(line)
        if "wrote" in report:
            print(f"  wrote: {report['wrote']}, "
                  f"orphan-removed: {report.get('removed_orphan')}")


if __name__ == "__main__":
    main()
