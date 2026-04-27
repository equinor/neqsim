"""
book_writer — long-running prose orchestrator for full-length book drafting.

Why this exists
---------------
A single LLM call cannot write a 1000-page book. Even a 30-page chapter is
already over the reliable single-shot output budget for current models. This
module turns *one* user command into hundreds of small, independent LLM calls
— one per section — with checkpointing, resume, and per-section retries.

Pipeline
--------
1. ``expand_outlines(book_dir)`` — for every chapter without a fine-grained
   outline, ask the LLM to expand ``book.yaml`` chapter title + target_pages
   into a list of sections (each with id, heading, target_words, key_points).
   The result is written / merged into ``chapter_outlines.yaml``.

2. ``write_book(book_dir)`` — the long loop. For each ``(chapter, section)``:

   * skip if checkpoint says ``done`` (unless ``--force``);
   * call the LLM with: chapter context, previous-section tail (continuity),
     current section spec, refs.bib slice, neqsim_in_writing rules;
   * append the returned markdown to ``chapters/<ch>/sections/<id>.md``;
   * mark ``done`` in ``.book_write_progress.json``;
   * on transient failure, log to checkpoint and continue (or stop with
     ``--stop-on-error``).

3. ``stitch_chapter(book_dir, ch_dir)`` — assembles all section files into
   the chapter's ``chapter.md``, optionally calls the LLM once more to write
   the chapter introduction and summary that bridge across sections.

The orchestrator is interruptible: ``Ctrl-C`` at any point is safe; rerun
with ``--resume`` (default) and it picks up at the next pending section.
"""

from __future__ import annotations

import json
import re
import sys
import time
from dataclasses import dataclass, field, asdict
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

import yaml

# Local imports – tools/ is on sys.path when invoked via paperflow.py
from llm_client import chat, has_any_provider, LLMError, estimate_tokens
from book_builder import load_book_config, iter_chapters


PROGRESS_FILENAME = ".book_write_progress.json"
SECTIONS_DIRNAME = "sections"
DEFAULT_WORDS_PER_SECTION = 800
DEFAULT_PAGE_WORDS = 400  # ~400 words / printed page


# ---------------------------------------------------------------------------
# Data classes
# ---------------------------------------------------------------------------

@dataclass
class SectionSpec:
    """A single drafting unit — typically 300–1500 words."""
    id: str                  # e.g. "4.3"
    heading: str
    target_words: int = DEFAULT_WORDS_PER_SECTION
    key_points: List[str] = field(default_factory=list)
    must_cite: List[str] = field(default_factory=list)
    figures: List[Dict[str, str]] = field(default_factory=list)
    notes: str = ""


@dataclass
class ChapterSpec:
    dir: str
    number: int
    title: str
    target_pages: int = 25
    learning_objectives: List[str] = field(default_factory=list)
    sections: List[SectionSpec] = field(default_factory=list)


# ---------------------------------------------------------------------------
# Progress / checkpoint
# ---------------------------------------------------------------------------

def _progress_path(book_dir: Path) -> Path:
    return book_dir / PROGRESS_FILENAME


def load_progress(book_dir: Path) -> Dict[str, Any]:
    """Load checkpoint dict; missing file → empty skeleton."""
    p = _progress_path(book_dir)
    if not p.exists():
        return {"started": None, "sections": {}, "totals": {}}
    try:
        return json.loads(p.read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        return {"started": None, "sections": {}, "totals": {}}


def save_progress(book_dir: Path, progress: Dict[str, Any]) -> None:
    _progress_path(book_dir).write_text(
        json.dumps(progress, indent=2), encoding="utf-8"
    )


def _section_key(ch_dir: str, sec_id: str) -> str:
    return f"{ch_dir}/{sec_id}"


# ---------------------------------------------------------------------------
# Outline expansion
# ---------------------------------------------------------------------------

_OUTLINE_SYSTEM = (
    "You are a senior textbook editor expanding a chapter outline into a "
    "fine-grained section list. Each section will be drafted independently "
    "by another LLM call, so each section MUST be self-contained and have "
    "clear scope. Output strictly valid YAML — no prose outside the YAML."
)

_OUTLINE_USER_TEMPLATE = """\
Book title: {book_title}
Chapter {ch_num}: {ch_title}
Target chapter length: {target_pages} printed pages (~{target_words} words).

Produce a YAML list named `sections` with {n_sections}–{n_sections_max} entries.
Each entry MUST have:

  id            : "{ch_num}.k" — 1-based, sequential
  heading       : a short numbered title, e.g. "{ch_num}.1 Introduction"
  target_words  : integer, 300–1500, summing across the chapter to ≈{target_words}
  key_points    : list of 3–6 short bullets the section must cover
  must_cite     : list of refs.bib keys the section should cite (use [] if unsure)
  figures       : list of figure entries (or [] if none). Each entry has
                    file    : filename under chapters/<dir>/figures/, e.g.
                              "phase_envelope.png". Use snake_case.
                    caption : one-sentence caption.
                    notebook: the notebook (under chapters/<dir>/notebooks/)
                              that should produce this PNG, e.g. "4_3_envelope.ipynb".
                  Plan 1–3 figures for theoretical/method sections and 2–4 for
                  worked-example sections; introductions and summaries usually [].

Also produce:

  learning_objectives : list of 3–6 short outcome statements

The first section should be an introduction that motivates the chapter,
the last should be a summary; if appropriate, include a worked-example
section toward the end. Distribute pedagogically: theory → method → example.

Output:

```yaml
learning_objectives:
  - ...
sections:
  - id: "{ch_num}.1"
    heading: "{ch_num}.1 Introduction"
    target_words: 600
    key_points:
      - ...
    must_cite: []
    figures: []
  - id: "{ch_num}.4"
    heading: "{ch_num}.4 Worked example"
    target_words: 1200
    key_points:
      - ...
    must_cite: [some_ref]
    figures:
      - file: "{ch_num}_4_phase_envelope.png"
        caption: "Phase envelope of the example mixture."
        notebook: "{ch_num}_4_envelope.ipynb"
```
"""


def _strip_yaml_fence(text: str) -> str:
    text = text.strip()
    text = re.sub(r"^```(?:yaml|yml)?\s*", "", text)
    text = re.sub(r"\s*```$", "", text)
    return text.strip()


def _section_count_target(target_pages: int) -> Tuple[int, int]:
    """Return (min, max) section count for the chapter length."""
    target_words = target_pages * DEFAULT_PAGE_WORDS
    n = max(4, target_words // DEFAULT_WORDS_PER_SECTION)
    return max(4, n - 2), n + 4


def expand_chapter_outline(
    book_dir: Path,
    ch_spec: ChapterSpec,
    *,
    provider: str,
    model: str,
) -> Dict[str, Any]:
    """Ask the LLM to produce a fine-grained section outline for one chapter.

    Returns a dict ready to be written under that chapter's key in
    ``chapter_outlines.yaml``.
    """
    cfg = load_book_config(book_dir)
    book_title = cfg.get("title", "")

    target_words = ch_spec.target_pages * DEFAULT_PAGE_WORDS
    n_min, n_max = _section_count_target(ch_spec.target_pages)

    user = _OUTLINE_USER_TEMPLATE.format(
        book_title=book_title,
        ch_num=ch_spec.number,
        ch_title=ch_spec.title,
        target_pages=ch_spec.target_pages,
        target_words=target_words,
        n_sections=n_min,
        n_sections_max=n_max,
    )

    raw = chat(
        [
            {"role": "system", "content": _OUTLINE_SYSTEM},
            {"role": "user", "content": user},
        ],
        provider=provider,
        model=model,
        max_tokens=6000,
        temperature=0.4,
    )
    parsed = yaml.safe_load(_strip_yaml_fence(raw))
    if not isinstance(parsed, dict):
        raise LLMError(f"Outline expansion returned non-dict YAML for "
                       f"{ch_spec.dir}: {parsed!r}")
    parsed.setdefault("title", ch_spec.title)
    parsed.setdefault("target_pages", ch_spec.target_pages)
    return parsed


def expand_outlines(
    book_dir: Path,
    *,
    provider: str = "litellm",
    model: str = "gpt-4o",
    chapters: Optional[List[str]] = None,
    force: bool = False,
    target_pages_default: int = 25,
) -> Path:
    """Write or update ``chapter_outlines.yaml`` with fine-grained outlines.

    Skips chapters that already have a ``sections:`` block unless ``force``.
    """
    book_dir = Path(book_dir)
    cfg = load_book_config(book_dir)
    out_path = book_dir / "chapter_outlines.yaml"

    existing: Dict[str, Any] = {}
    if out_path.exists():
        existing = yaml.safe_load(out_path.read_text(encoding="utf-8")) or {}

    for num, ch, _part in iter_chapters(cfg):
        ch_dir = ch["dir"]
        if chapters and ch_dir not in chapters:
            continue
        cur = existing.get(ch_dir, {}) or {}
        if not force and isinstance(cur, dict) and cur.get("sections"):
            print(f"  skip {ch_dir} (already has sections)")
            continue
        ch_spec = ChapterSpec(
            dir=ch_dir,
            number=num,
            title=ch.get("title", f"Chapter {num}"),
            target_pages=int(cur.get("target_pages", target_pages_default)),
        )
        print(f"  expanding {ch_dir} (target {ch_spec.target_pages} pages)...")
        outline = expand_chapter_outline(
            book_dir, ch_spec, provider=provider, model=model
        )
        # Merge: keep any user-edited fields from cur
        merged = {**cur, **outline}
        existing[ch_dir] = merged

    out_path.write_text(
        yaml.safe_dump(existing, sort_keys=False, allow_unicode=True),
        encoding="utf-8",
    )
    print(f"Wrote {out_path}")
    return out_path


# ---------------------------------------------------------------------------
# Section drafting
# ---------------------------------------------------------------------------

_SECTION_SYSTEM = """\
You are an expert technical book author writing one section of a chapter for
a graduate-level engineering textbook. Follow these strict rules:

1. SCOPE: write only the section requested — never repeat earlier sections.
2. UNITS: SI units throughout. K, Pa, J, kg, m, mol. Bar acceptable for pressure.
3. EQUATIONS: use $...$ for inline and $$...$$ for display. No \\tag{}.
4. CITATIONS: every factual claim, equation origin, and historical attribution
   uses \\cite{key}. Cite only keys present in the provided refs.bib slice.
5. STYLE: precise, professional, third person. No filler ('it is important to
   note that', 'as is well known'). No marketing language. Active voice preferred.
6. STRUCTURE: open with one short orientation sentence; develop the key points
   in order; close with a one-sentence bridge to the next section if logical.
7. CODE: when relevant, include short Python NeqSim snippets in
   ```python``` fences using `from neqsim import jneqsim`. SI / bar / K only.
8. FIGURES: if a `figures` list is provided, you MUST insert each figure at the
   point in the prose where it is first discussed, using the exact markdown
   form `![<caption>](figures/<file>)` on its own line, with a sentence above
   that introduces it ("Figure X.Y shows ...") and a sentence below that
   interprets it. Do NOT invent figures that are not in the list. Do NOT add
   `<figure>` HTML or LaTeX `\\begin{figure}` — markdown only.
9. LENGTH: hit the target word count within ±15%.
10. OUTPUT: return ONLY the section markdown, starting with the section heading
   `## <heading>`. No prefatory text, no closing remarks, no JSON wrapper.
"""


_SECTION_USER_TEMPLATE = """\
Book: {book_title}
Chapter {ch_num}: {ch_title}
Section to write: {sec_id} — {sec_heading}
Target length: {target_words} words

Section key points (cover all):
{key_points_block}

Suggested citations (use any that fit; you may also cite other keys present
in the refs.bib excerpt below):
{must_cite_block}

Figures to embed in this section (insert each as
`![<caption>](figures/<file>)` at the point where it is first discussed; if
the list is empty, do not invent any figure references):
{figures_block}

Continuity — last paragraph of the previous section (do NOT repeat it,
but pick up the thread):
---
{prev_tail}
---

Chapter learning objectives (the chapter as a whole — your section
contributes to these):
{objectives_block}

refs.bib excerpt (cite by key):
---
{refs_excerpt}
---

Now write section {sec_id}. Output starts with `## {sec_heading}`.
"""


def _build_refs_excerpt(book_dir: Path, max_chars: int = 6000) -> str:
    """Return the first ``max_chars`` of refs.bib (cheap context)."""
    bib = book_dir / "refs.bib"
    if not bib.exists():
        return ""
    txt = bib.read_text(encoding="utf-8", errors="ignore")
    return txt[:max_chars]


def _previous_section_tail(
    sections_dir: Path, prev_id: Optional[str], n_chars: int = 600
) -> str:
    if not prev_id:
        return "(this is the first section of the chapter)"
    prev_path = sections_dir / f"{_safe_id(prev_id)}.md"
    if not prev_path.exists():
        return "(previous section not yet drafted)"
    body = prev_path.read_text(encoding="utf-8")
    return body[-n_chars:].strip()


def _safe_id(sec_id: str) -> str:
    return sec_id.replace(".", "_").replace("/", "_")


def discover_chapter_figures(book_dir: Path, ch_dir: str) -> List[str]:
    """List PNG files already present under ``chapters/<ch_dir>/figures/``.

    These are typically produced by chapter notebooks. The result is used as a
    fallback when ``chapter_outlines.yaml`` does not assign figures to specific
    sections \u2014 every discovered PNG will then be appended to ``chapter.md``
    in a `## Figures` section by ``stitch_chapter``.
    """
    fig_dir = book_dir / "chapters" / ch_dir / "figures"
    if not fig_dir.exists():
        return []
    return sorted(p.name for p in fig_dir.glob("*.png"))


def _figures_referenced_in_text(text: str) -> set:
    """Return the set of figure filenames already referenced via ``![...](figures/foo.png)``."""
    return set(re.findall(r"!\[[^\]]*\]\(figures/([^)]+)\)", text))


def write_section(
    book_dir: Path,
    ch_spec: ChapterSpec,
    sec: SectionSpec,
    prev_sec_id: Optional[str],
    *,
    provider: str,
    model: str,
    max_tokens: int = 4000,
) -> Path:
    """Draft one section by calling the LLM. Writes to sections/<id>.md."""
    cfg = load_book_config(book_dir)
    book_title = cfg.get("title", "")

    ch_dir_path = book_dir / "chapters" / ch_spec.dir
    sections_dir = ch_dir_path / SECTIONS_DIRNAME
    sections_dir.mkdir(parents=True, exist_ok=True)

    refs_excerpt = _build_refs_excerpt(book_dir)
    prev_tail = _previous_section_tail(sections_dir, prev_sec_id)

    key_points_block = "\n".join(f"  - {p}" for p in sec.key_points) or "  - (use chapter context)"
    must_cite_block = ", ".join(sec.must_cite) if sec.must_cite else "(none specified)"
    objectives_block = "\n".join(f"  - {o}" for o in ch_spec.learning_objectives) or "  - (none)"

    if sec.figures:
        figures_block = "\n".join(
            f"  - file: {f.get('file','')}  caption: {f.get('caption','')}"
            for f in sec.figures
        )
    else:
        figures_block = "  (none — do not insert any figure references)"

    user = _SECTION_USER_TEMPLATE.format(
        book_title=book_title,
        ch_num=ch_spec.number,
        ch_title=ch_spec.title,
        sec_id=sec.id,
        sec_heading=sec.heading,
        target_words=sec.target_words,
        key_points_block=key_points_block,
        must_cite_block=must_cite_block,
        figures_block=figures_block,
        prev_tail=prev_tail,
        objectives_block=objectives_block,
        refs_excerpt=refs_excerpt,
    )

    body = chat(
        [
            {"role": "system", "content": _SECTION_SYSTEM},
            {"role": "user", "content": user},
        ],
        provider=provider,
        model=model,
        max_tokens=max_tokens,
        temperature=0.3,
    )
    body = body.strip()
    if not body.startswith("## "):
        # Models occasionally drop the heading; force one.
        body = f"## {sec.heading}\n\n{body}"

    out_path = sections_dir / f"{_safe_id(sec.id)}.md"
    out_path.write_text(body + "\n", encoding="utf-8")
    return out_path


# ---------------------------------------------------------------------------
# Chapter stitching
# ---------------------------------------------------------------------------

_STITCH_SYSTEM = (
    "You are a senior textbook editor. You will write a one-paragraph "
    "chapter introduction (200–350 words) and a 'Chapter summary' "
    "section (150–250 words) that bridge the supplied section list. "
    "Do not repeat content; preview and conclude. Use the same citation "
    "style \\cite{key}. Return JSON with keys 'introduction' and 'summary' "
    "only — both as markdown strings."
)


def _stitch_intro_and_summary(
    book_dir: Path,
    ch_spec: ChapterSpec,
    *,
    provider: str,
    model: str,
) -> Tuple[str, str]:
    bullets = "\n".join(
        f"  {s.id}: {s.heading} — {', '.join(s.key_points[:3])}"
        for s in ch_spec.sections
    )
    user = (
        f"Chapter {ch_spec.number}: {ch_spec.title}\n\n"
        f"Sections:\n{bullets}\n\n"
        f"Learning objectives:\n"
        + "\n".join(f"  - {o}" for o in ch_spec.learning_objectives)
        + "\n\nReturn JSON only."
    )
    raw = chat(
        [
            {"role": "system", "content": _STITCH_SYSTEM},
            {"role": "user", "content": user},
        ],
        provider=provider,
        model=model,
        max_tokens=2000,
        temperature=0.3,
    )
    txt = raw.strip()
    txt = re.sub(r"^```(?:json)?\s*", "", txt)
    txt = re.sub(r"\s*```$", "", txt)
    try:
        data = json.loads(txt)
        return data.get("introduction", ""), data.get("summary", "")
    except json.JSONDecodeError:
        return "", ""


def stitch_chapter(
    book_dir: Path,
    ch_spec: ChapterSpec,
    *,
    provider: str,
    model: str,
    write_intro_summary: bool = True,
) -> Path:
    """Concatenate sections/<id>.md into chapters/<ch>/chapter.md.

    Optionally prepends a generated introduction and appends a summary.
    """
    ch_dir_path = book_dir / "chapters" / ch_spec.dir
    sections_dir = ch_dir_path / SECTIONS_DIRNAME

    parts: List[str] = []
    parts.append(f"# {ch_spec.title}\n")
    parts.append(f"<!-- chapter {ch_spec.number} — drafted by book_writer -->\n")

    intro = ""
    summary = ""
    if write_intro_summary:
        try:
            intro, summary = _stitch_intro_and_summary(
                book_dir, ch_spec, provider=provider, model=model
            )
        except LLMError as exc:
            print(f"    [warn] stitch failed for {ch_spec.dir}: {exc}")

    if intro:
        parts.append(intro.strip() + "\n")

    if ch_spec.learning_objectives:
        parts.append("## Learning objectives\n")
        parts.append("After reading this chapter, the reader will be able to:\n")
        for i, o in enumerate(ch_spec.learning_objectives, 1):
            parts.append(f"{i}. {o}")
        parts.append("")

    for sec in ch_spec.sections:
        sec_path = sections_dir / f"{_safe_id(sec.id)}.md"
        if sec_path.exists():
            parts.append(sec_path.read_text(encoding="utf-8").rstrip() + "\n")
        else:
            parts.append(f"## {sec.heading}\n\n<!-- TODO: section not drafted -->\n")

    if summary:
        parts.append("## Chapter summary\n")
        parts.append(summary.strip() + "\n")

    # Figure injection: append any PNGs in figures/ that aren't already
    # referenced inline. This preserves figure inclusion when the LLM didn't
    # cite them (e.g. outline had no figures field) or when notebooks were
    # run after drafting and produced additional plots.
    drafted_text = "\n".join(parts)
    referenced = _figures_referenced_in_text(drafted_text)
    on_disk = discover_chapter_figures(book_dir, ch_spec.dir)
    orphan = [f for f in on_disk if f not in referenced]
    if orphan:
        parts.append("## Figures\n")
        parts.append(
            "The following figures, generated by this chapter's notebooks, "
            "are referenced in the analyses above:\n"
        )
        for fname in orphan:
            stem = fname.rsplit(".", 1)[0].replace("_", " ")
            parts.append(f"![{stem}](figures/{fname})\n")

    out = ch_dir_path / "chapter.md"
    out.write_text("\n".join(parts), encoding="utf-8")
    return out


# ---------------------------------------------------------------------------
# Main loop
# ---------------------------------------------------------------------------

def _load_chapter_specs(book_dir: Path) -> List[ChapterSpec]:
    cfg = load_book_config(book_dir)
    outlines_path = book_dir / "chapter_outlines.yaml"
    outlines: Dict[str, Any] = {}
    if outlines_path.exists():
        outlines = yaml.safe_load(outlines_path.read_text(encoding="utf-8")) or {}

    specs: List[ChapterSpec] = []
    for num, ch, _part in iter_chapters(cfg):
        ch_dir = ch["dir"]
        outline = outlines.get(ch_dir, {}) or {}
        sections = []
        for s in outline.get("sections", []) or []:
            sections.append(
                SectionSpec(
                    id=str(s.get("id", "")),
                    heading=s.get("heading", "Section"),
                    target_words=int(s.get("target_words", DEFAULT_WORDS_PER_SECTION)),
                    key_points=list(s.get("key_points", []) or []),
                    must_cite=list(s.get("must_cite", []) or []),
                    figures=list(s.get("figures", []) or []),
                    notes=s.get("notes", "") or "",
                )
            )
        specs.append(
            ChapterSpec(
                dir=ch_dir,
                number=num,
                title=outline.get("title", ch.get("title", f"Chapter {num}")),
                target_pages=int(outline.get("target_pages", 25)),
                learning_objectives=list(outline.get("learning_objectives", []) or []),
                sections=sections,
            )
        )
    return specs


def estimate_book_cost(specs: List[ChapterSpec]) -> Dict[str, Any]:
    """Rough estimate: tokens, calls, USD at typical rates."""
    n_sections = sum(len(c.sections) for c in specs)
    total_words = sum(s.target_words for c in specs for s in c.sections)
    # in: ~2k prompt + refs, out: target_words / 0.75 tokens/word ≈ * 1.33
    tokens_in = n_sections * 3000
    tokens_out = int(total_words * 1.4)
    # Mid-range model (~$2.50 / Mtok in, $10 / Mtok out)
    usd = tokens_in * 2.5e-6 + tokens_out * 10e-6
    return {
        "n_chapters": len(specs),
        "n_sections": n_sections,
        "target_words": total_words,
        "approx_pages": total_words // DEFAULT_PAGE_WORDS,
        "tokens_in": tokens_in,
        "tokens_out": tokens_out,
        "usd_estimate": round(usd, 2),
    }


def write_book(
    book_dir: Path,
    *,
    provider: str = "litellm",
    model: str = "gpt-4o",
    chapters: Optional[List[str]] = None,
    sections: Optional[List[str]] = None,
    resume: bool = True,
    stop_on_error: bool = False,
    skip_stitch: bool = False,
    max_tokens_per_section: int = 4000,
    sleep_between: float = 0.0,
) -> Dict[str, Any]:
    """Run the long drafting loop.

    Parameters
    ----------
    book_dir
        Path to the book project (containing ``book.yaml`` and
        ``chapter_outlines.yaml``).
    chapters
        Optional list of chapter dir names to restrict the run.
    sections
        Optional list of section ids (e.g. ``["4.3", "4.4"]``) to restrict to.
    resume
        If True (default), skip sections already marked ``done`` in the
        checkpoint. If False, redraft them.
    stop_on_error
        If True, stop on first failed section. Otherwise log and continue.
    skip_stitch
        If True, only draft section files; do not assemble ``chapter.md``.
    max_tokens_per_section
        Output budget for the LLM. 4000 fits ~3000 words of dense prose.
    sleep_between
        Optional delay (seconds) between calls — useful to dodge rate limits.

    Returns
    -------
    dict
        Summary with ``done``, ``failed``, ``skipped`` counts.
    """
    if not has_any_provider():
        raise LLMError(
            "No LLM SDK installed. `pip install litellm` (recommended) or "
            "`pip install openai` or `pip install anthropic`."
        )

    book_dir = Path(book_dir)
    specs = _load_chapter_specs(book_dir)

    if chapters:
        specs = [c for c in specs if c.dir in chapters]
    if not any(c.sections for c in specs):
        raise RuntimeError(
            "No section outlines found. Run `book-expand-outline` first, or "
            "edit chapter_outlines.yaml to add a `sections:` block."
        )

    progress = load_progress(book_dir)
    if not progress.get("started"):
        progress["started"] = time.strftime("%Y-%m-%dT%H:%M:%S")
    progress.setdefault("sections", {})

    summary = {"done": 0, "failed": 0, "skipped": 0,
               "started_at": progress["started"]}
    chapters_touched: List[ChapterSpec] = []

    for ch in specs:
        chapters_touched.append(ch)
        prev_sec_id: Optional[str] = None
        print(f"\n[ch {ch.number}] {ch.dir} — {len(ch.sections)} sections")
        for sec in ch.sections:
            if sections and sec.id not in sections:
                prev_sec_id = sec.id
                continue
            key = _section_key(ch.dir, sec.id)
            entry = progress["sections"].get(key, {})
            if resume and entry.get("status") == "done":
                summary["skipped"] += 1
                prev_sec_id = sec.id
                continue

            t0 = time.time()
            try:
                out_path = write_section(
                    book_dir, ch, sec, prev_sec_id,
                    provider=provider, model=model,
                    max_tokens=max_tokens_per_section,
                )
                wc = len(out_path.read_text(encoding="utf-8").split())
                progress["sections"][key] = {
                    "status": "done",
                    "path": str(out_path.relative_to(book_dir)),
                    "words": wc,
                    "duration_s": round(time.time() - t0, 1),
                }
                save_progress(book_dir, progress)
                summary["done"] += 1
                print(f"  [ok ] {sec.id} {sec.heading}  ({wc} w, "
                      f"{progress['sections'][key]['duration_s']}s)")
            except Exception as exc:  # noqa: BLE001
                progress["sections"][key] = {
                    "status": "failed",
                    "error": str(exc)[:300],
                    "duration_s": round(time.time() - t0, 1),
                }
                save_progress(book_dir, progress)
                summary["failed"] += 1
                print(f"  [err] {sec.id}: {exc}")
                if stop_on_error:
                    return summary

            prev_sec_id = sec.id
            if sleep_between > 0:
                time.sleep(sleep_between)

    # Stitch each touched chapter
    if not skip_stitch:
        print("\nStitching chapters...")
        for ch in chapters_touched:
            try:
                p = stitch_chapter(book_dir, ch, provider=provider, model=model)
                print(f"  [ok ] {ch.dir} -> {p.relative_to(book_dir)}")
            except Exception as exc:  # noqa: BLE001
                print(f"  [err] stitch {ch.dir}: {exc}")

    progress["totals"] = summary
    save_progress(book_dir, progress)
    return summary


# ---------------------------------------------------------------------------
# CLI fallback (module run directly)
# ---------------------------------------------------------------------------

if __name__ == "__main__":  # pragma: no cover
    import argparse
    ap = argparse.ArgumentParser(description="Long-running book drafting orchestrator")
    ap.add_argument("book_dir")
    ap.add_argument("--expand", action="store_true",
                    help="Expand outlines first (or only)")
    ap.add_argument("--write", action="store_true",
                    help="Run the drafting loop")
    ap.add_argument("--provider", default="litellm")
    ap.add_argument("--model", default="gpt-4o")
    ap.add_argument("--chapter", action="append", default=None)
    ap.add_argument("--section", action="append", default=None)
    ap.add_argument("--no-resume", dest="resume", action="store_false")
    ap.add_argument("--stop-on-error", action="store_true")
    ap.add_argument("--skip-stitch", action="store_true")
    ap.add_argument("--force-outline", action="store_true")
    args = ap.parse_args()

    bd = Path(args.book_dir)
    if args.expand or not (bd / "chapter_outlines.yaml").exists():
        expand_outlines(
            bd, provider=args.provider, model=args.model,
            chapters=args.chapter, force=args.force_outline,
        )
    if args.write:
        s = write_book(
            bd,
            provider=args.provider, model=args.model,
            chapters=args.chapter, sections=args.section,
            resume=args.resume, stop_on_error=args.stop_on_error,
            skip_stitch=args.skip_stitch,
        )
        print("\nSummary:", json.dumps(s, indent=2))
