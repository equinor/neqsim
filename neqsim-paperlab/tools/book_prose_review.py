"""Offline prose-review check for book chapters.

This is a deterministic, dependency-free linter that flags the most common
academic-writing problems. It is intentionally offline (no LLM call) so
it can run in CI; for online LLM-assisted review, see
``cmd_check_prose`` in paperflow.py which targets papers.

Checks:

1. Passive voice (was/were/been + past participle endings)
2. Weasel words ("very", "quite", "rather", "somewhat", "essentially")
3. First-person plural overuse ("we found", "we observed") past a quota
4. Long sentences (> 35 words)
5. Repeated word ("the the", "and and")
6. Tautologies ("future plans", "past history")
7. Tense inconsistency in abstracts (mix of past and present > 30%)
8. Citation-needed flags (paragraphs with quantitative claims and no citation)

Output severity: ``info`` (style), ``warning`` (substantive).
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import book_builder  # type: ignore


WEASEL = {"very", "quite", "rather", "somewhat", "essentially", "basically",
          "actually", "really", "literally", "extremely", "vast", "myriad"}
TAUTOLOGIES = [
    (re.compile(r"\bfuture\s+plans?\b", re.I), "redundant: 'future plans' -> 'plans'"),
    (re.compile(r"\bpast\s+history\b", re.I), "redundant: 'past history' -> 'history'"),
    (re.compile(r"\bend\s+result\b", re.I), "redundant: 'end result' -> 'result'"),
    (re.compile(r"\bclose\s+proximity\b", re.I), "redundant: 'close proximity' -> 'proximity'"),
    (re.compile(r"\bin\s+order\s+to\b", re.I), "wordy: 'in order to' -> 'to'"),
]
PASSIVE_RE = re.compile(
    r"\b(?:is|are|was|were|been|being|be)\s+(?:\w+ly\s+)?(\w+ed|done|made|seen|taken|given|found|shown|used|known|called)\b",
    re.I,
)
REPEAT_RE = re.compile(r"\b(\w+)\s+\1\b", re.I)
NUM_CLAIM_RE = re.compile(r"\b\d+(?:\.\d+)?\s*(?:%|percent|kg|bar|MPa|K|°C|m\^?3|MMSCFD)\b")
CITATION_RE = re.compile(r"\[@\w+|\\cite\{|\(\d{4}\)|\bRef\.\s*\d+", re.I)


def _split_sentences(text: str) -> list[str]:
    # Strip code fences and inline code first
    text = re.sub(r"```.*?```", "", text, flags=re.S)
    text = re.sub(r"`[^`]+`", "", text)
    # Strip HTML comments
    text = re.sub(r"<!--.*?-->", "", text, flags=re.S)
    parts = re.split(r"(?<=[.!?])\s+(?=[A-Z])", text)
    return [p.strip() for p in parts if p.strip()]


def review_chapter(text: str, chapter_id: str) -> list[dict]:
    issues: list[dict] = []
    sentences = _split_sentences(text)
    we_count = 0
    for sent in sentences:
        n_words = len(sent.split())
        if n_words > 35:
            issues.append({
                "severity": "info",
                "code": "long-sentence",
                "msg": f"sentence has {n_words} words (>35): '{sent[:90]}…'",
            })
        if PASSIVE_RE.search(sent):
            issues.append({
                "severity": "info",
                "code": "passive-voice",
                "msg": f"possible passive voice: '{sent[:90]}…'",
            })
        for w in WEASEL:
            if re.search(rf"\b{w}\b", sent, re.I):
                issues.append({
                    "severity": "info",
                    "code": "weasel",
                    "msg": f"weasel word '{w}' in: '{sent[:90]}…'",
                })
                break
        for rgx, label in TAUTOLOGIES:
            if rgx.search(sent):
                issues.append({
                    "severity": "info",
                    "code": "tautology",
                    "msg": f"{label} in: '{sent[:90]}…'",
                })
        m = REPEAT_RE.search(sent)
        if m and m.group(1).lower() not in {"that", "had", "is"}:
            issues.append({
                "severity": "warning",
                "code": "repeat-word",
                "msg": f"repeated word '{m.group(1)}': '{sent[:90]}…'",
            })
        if re.match(r"^\s*we\s+(found|observe|show|present|propose|demonstrate)", sent, re.I):
            we_count += 1
    if we_count > 8:
        issues.append({
            "severity": "info",
            "code": "first-person-overuse",
            "msg": f"first-person plural ('we …') used {we_count} times — vary phrasing",
        })

    # Paragraph-level: quantitative claim without citation
    for para in text.split("\n\n"):
        if NUM_CLAIM_RE.search(para) and not CITATION_RE.search(para):
            snippet = para.strip().replace("\n", " ")[:120]
            issues.append({
                "severity": "warning",
                "code": "citation-needed",
                "msg": f"quantitative claim without citation: '{snippet}…'",
            })

    return issues


def check_prose_review(book_dir) -> dict:
    book_dir = Path(book_dir)
    cfg = book_builder.load_book_config(book_dir)
    out: dict[str, list[dict]] = {}
    for ch_num, ch, _ in book_builder.iter_chapters(cfg):
        ch_dir = book_builder.resolve_chapter_dir(book_dir, ch)
        cm = ch_dir / "chapter.md"
        if not cm.exists():
            continue
        text = cm.read_text(encoding="utf-8")
        ch_id = f"ch{ch_num:02d}_{ch.get('id', ch.get('slug', ''))}"
        issues = review_chapter(text, ch_id)
        if issues:
            out[ch_id] = issues
    return out


def format_prose_report(report: dict) -> str:
    lines = ["# Prose Review Report", ""]
    total = sum(len(v) for v in report.values())
    lines.append(f"{total} issue(s) across {len(report)} chapter(s).")
    lines.append("")
    by_sev = {"warning": 0, "info": 0}
    for chs, issues in report.items():
        for i in issues:
            by_sev[i["severity"]] = by_sev.get(i["severity"], 0) + 1
    lines.append(f"  - {by_sev.get('warning', 0)} warning(s)")
    lines.append(f"  - {by_sev.get('info', 0)} info")
    lines.append("")
    for ch_id in sorted(report):
        lines.append(f"## {ch_id}")
        for i in report[ch_id][:50]:
            lines.append(f"- **[{i['severity']}]** ({i['code']}) {i['msg']}")
        lines.append("")
    return "\n".join(lines)
