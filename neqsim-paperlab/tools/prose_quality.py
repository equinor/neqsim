"""
Prose Quality Analyzer — Sentence-level writing feedback for scientific manuscripts.

Checks readability (Flesch-Kincaid), sentence length, passive voice,
hedging language, and academic style metrics.

Usage::

    from tools.prose_quality import analyze_prose

    report = analyze_prose("papers/my_paper/paper.md")
    print_prose_report(report)
"""

import re
from pathlib import Path
from typing import Dict, List, Optional

try:
    import textstat
    _HAS_TEXTSTAT = True
except ImportError:
    _HAS_TEXTSTAT = False


# ── Passive voice patterns ────────────────────────────────────────────
# Match "is/was/were/been/being/are + past participle"
_PASSIVE_RE = re.compile(
    r'\b(is|are|was|were|been|being|be|get|gets|got|gotten)\s+'
    r'(\w+ed|built|chosen|done|drawn|driven|eaten|fallen|found|given|gone|'
    r'grown|known|made|run|said|seen|shown|taken|thought|written|broken|'
    r'frozen|spoken|stolen|worn|torn|begun|drunk|rung|sung|sunk|swum)\b',
    re.IGNORECASE,
)

# ── Hedging / weak language ──────────────────────────────────────────
_HEDGE_WORDS = [
    r'\bsomewhat\b', r'\bslightly\b', r'\bperhaps\b', r'\bapparently\b',
    r'\bseems?\s+to\b', r'\bappears?\s+to\b', r'\bmight\b', r'\bcould\s+be\b',
    r'\bit\s+is\s+possible\b', r'\bgenerally\b', r'\brelatively\b',
    r'\bquite\b', r'\brather\b', r'\bfairly\b',
]
_HEDGE_RE = re.compile('|'.join(_HEDGE_WORDS), re.IGNORECASE)

# ── Wordy phrases ────────────────────────────────────────────────────
_WORDY_PAIRS = [
    (r'\bin order to\b', 'to'),
    (r'\bdue to the fact that\b', 'because'),
    (r'\bat this point in time\b', 'now'),
    (r'\bin the event that\b', 'if'),
    (r'\bfor the purpose of\b', 'to/for'),
    (r'\bwith regard to\b', 'regarding/about'),
    (r'\bin spite of the fact that\b', 'although'),
    (r'\bit is important to note that\b', '[delete]'),
    (r'\bit should be noted that\b', '[delete]'),
    (r'\ba large number of\b', 'many'),
    (r'\ba small number of\b', 'few'),
    (r'\bhas the ability to\b', 'can'),
    (r'\bis able to\b', 'can'),
    (r'\bprior to\b', 'before'),
    (r'\bsubsequent to\b', 'after'),
    (r'\bin close proximity to\b', 'near'),
    (r'\btake into consideration\b', 'consider'),
]


def _extract_body_text(paper_text):
    """Extract prose-only body from markdown, skipping metadata and code."""
    # Remove HTML comments
    text = re.sub(r'<!--.*?-->', '', paper_text, flags=re.DOTALL)
    # Remove code blocks
    text = re.sub(r'```.*?```', '', text, flags=re.DOTALL)
    # Remove inline code
    text = re.sub(r'`[^`]+`', '', text)
    # Remove markdown images
    text = re.sub(r'!\[.*?\]\(.*?\)', '', text)
    # Remove markdown links but keep text
    text = re.sub(r'\[([^\]]+)\]\([^)]+\)', r'\1', text)
    # Remove markdown headers (keep text for sentence detection)
    text = re.sub(r'^#{1,6}\s+', '', text, flags=re.MULTILINE)
    # Remove table separators
    text = re.sub(r'^\|[\s:\-|]+\|$', '', text, flags=re.MULTILINE)
    # Remove table rows
    text = re.sub(r'^\|.*\|$', '', text, flags=re.MULTILINE)
    # Remove bold/italic markers
    text = re.sub(r'\*{1,3}', '', text)
    # Remove horizontal rules
    text = re.sub(r'^---+$', '', text, flags=re.MULTILINE)
    # Remove LaTeX display math
    text = re.sub(r'\$\$.*?\$\$', '', text, flags=re.DOTALL)
    # Remove inline math
    text = re.sub(r'\$[^$]+\$', 'X', text)
    # Remove \cite{} references
    text = re.sub(r'\\cite\{[^}]+\}', '', text)
    # Collapse whitespace
    text = re.sub(r'\n{2,}', '\n', text)
    return text.strip()


def _split_sentences(text):
    """Split text into sentences (simple rule-based)."""
    # Split on . ! ? followed by space or end
    raw = re.split(r'(?<=[.!?])\s+', text)
    sentences = []
    for s in raw:
        s = s.strip()
        if len(s) > 10:  # Skip fragments
            sentences.append(s)
    return sentences


def analyze_prose(paper_path, max_sentence_words=35, target_grade=12.0):
    """Analyze prose quality of a manuscript.

    Parameters
    ----------
    paper_path : str or Path
        Path to paper.md.
    max_sentence_words : int
        Sentences with more words than this are flagged.
    target_grade : float
        Target Flesch-Kincaid grade level (12 = college level, typical for journals).

    Returns
    -------
    dict
        Comprehensive prose quality report.
    """
    paper_path = Path(paper_path)
    if not paper_path.exists():
        return {"error": f"File not found: {paper_path}"}

    raw_text = paper_path.read_text(encoding="utf-8")
    body = _extract_body_text(raw_text)
    sentences = _split_sentences(body)

    report = {
        "file": str(paper_path),
        "word_count": len(body.split()),
        "sentence_count": len(sentences),
        "readability": {},
        "issues": [],
        "summary_scores": {},
    }

    if not sentences:
        report["issues"].append({
            "type": "NO_CONTENT",
            "severity": "FAIL",
            "message": "No prose content found in manuscript",
        })
        return report

    # ── Readability metrics (textstat) ────────────────────────────────
    if _HAS_TEXTSTAT:
        fk_grade = textstat.flesch_kincaid_grade(body)
        fk_ease = textstat.flesch_reading_ease(body)
        gunning = textstat.gunning_fog(body)
        ari = textstat.automated_readability_index(body)

        report["readability"] = {
            "flesch_kincaid_grade": round(fk_grade, 1),
            "flesch_reading_ease": round(fk_ease, 1),
            "gunning_fog_index": round(gunning, 1),
            "automated_readability_index": round(ari, 1),
        }

        # Grade level check
        if fk_grade > target_grade + 4:
            report["issues"].append({
                "type": "READABILITY",
                "severity": "WARNING",
                "message": (f"Flesch-Kincaid grade {fk_grade:.1f} is very high "
                           f"(target: {target_grade:.0f}). Consider shorter sentences."),
            })
        elif fk_grade > target_grade + 2:
            report["issues"].append({
                "type": "READABILITY",
                "severity": "INFO",
                "message": f"Flesch-Kincaid grade {fk_grade:.1f} (target: {target_grade:.0f})",
            })
        else:
            report["issues"].append({
                "type": "READABILITY",
                "severity": "OK",
                "message": f"Flesch-Kincaid grade {fk_grade:.1f} — good readability",
            })

    # ── Sentence length analysis ──────────────────────────────────────
    long_sentences = []
    sentence_lengths = []
    for s in sentences:
        word_count = len(s.split())
        sentence_lengths.append(word_count)
        if word_count > max_sentence_words:
            # Show first 80 chars
            preview = s[:80] + "..." if len(s) > 80 else s
            long_sentences.append({
                "words": word_count,
                "preview": preview,
            })

    avg_len = sum(sentence_lengths) / len(sentence_lengths) if sentence_lengths else 0
    report["sentence_stats"] = {
        "average_words": round(avg_len, 1),
        "max_words": max(sentence_lengths) if sentence_lengths else 0,
        "min_words": min(sentence_lengths) if sentence_lengths else 0,
        "long_sentence_count": len(long_sentences),
    }

    if long_sentences:
        report["issues"].append({
            "type": "LONG_SENTENCES",
            "severity": "WARNING",
            "count": len(long_sentences),
            "message": (f"{len(long_sentences)} sentences exceed {max_sentence_words} words. "
                       f"Consider splitting."),
            "examples": long_sentences[:5],
        })

    # ── Passive voice detection ───────────────────────────────────────
    passive_matches = []
    for s in sentences:
        matches = _PASSIVE_RE.findall(s)
        if matches:
            preview = s[:80] + "..." if len(s) > 80 else s
            passive_matches.append(preview)

    passive_pct = len(passive_matches) / len(sentences) * 100 if sentences else 0
    report["passive_voice"] = {
        "count": len(passive_matches),
        "percent": round(passive_pct, 1),
    }

    if passive_pct > 30:
        report["issues"].append({
            "type": "PASSIVE_VOICE",
            "severity": "WARNING",
            "message": (f"{passive_pct:.0f}% of sentences use passive voice "
                       f"(aim for <25%). Consider active constructions."),
            "examples": passive_matches[:5],
        })
    elif passive_pct > 20:
        report["issues"].append({
            "type": "PASSIVE_VOICE",
            "severity": "INFO",
            "message": f"{passive_pct:.0f}% passive voice — acceptable for scientific writing",
        })
    else:
        report["issues"].append({
            "type": "PASSIVE_VOICE",
            "severity": "OK",
            "message": f"{passive_pct:.0f}% passive voice — good balance",
        })

    # ── Hedging language ──────────────────────────────────────────────
    hedge_matches = _HEDGE_RE.findall(body)
    hedge_count = len(hedge_matches)
    report["hedging"] = {
        "count": hedge_count,
        "examples": list(set(h.lower() for h in hedge_matches))[:10],
    }

    if hedge_count > 10:
        report["issues"].append({
            "type": "HEDGING",
            "severity": "WARNING",
            "message": (f"{hedge_count} hedging phrases found (somewhat, perhaps, "
                       f"seems to...). Be more assertive with evidence-backed claims."),
        })
    elif hedge_count > 5:
        report["issues"].append({
            "type": "HEDGING",
            "severity": "INFO",
            "message": f"{hedge_count} hedging phrases — review for unnecessary qualifiers",
        })

    # ── Wordy phrases ─────────────────────────────────────────────────
    wordy_found = []
    for pattern, replacement in _WORDY_PAIRS:
        matches = re.findall(pattern, body, re.IGNORECASE)
        if matches:
            wordy_found.append({
                "phrase": matches[0],
                "replacement": replacement,
                "count": len(matches),
            })

    if wordy_found:
        report["issues"].append({
            "type": "WORDY_PHRASES",
            "severity": "INFO",
            "count": sum(w["count"] for w in wordy_found),
            "message": f"{len(wordy_found)} wordy phrase types found — consider tighter wording",
            "suggestions": wordy_found[:10],
        })

    # ── Summary scores (0-100) ────────────────────────────────────────
    # Readability score
    read_score = 100
    if _HAS_TEXTSTAT:
        fk = report["readability"]["flesch_kincaid_grade"]
        if fk > 18:
            read_score = 40
        elif fk > 16:
            read_score = 60
        elif fk > 14:
            read_score = 75
        elif fk > 12:
            read_score = 85
        else:
            read_score = 95

    # Sentence score
    long_pct = len(long_sentences) / len(sentences) * 100 if sentences else 0
    sent_score = max(0, 100 - long_pct * 3)

    # Passive score
    passive_score = max(0, 100 - max(0, passive_pct - 15) * 3)

    # Conciseness score
    wordy_total = sum(w["count"] for w in wordy_found)
    concise_score = max(0, 100 - wordy_total * 5 - hedge_count * 3)

    overall = round((read_score + sent_score + passive_score + concise_score) / 4)
    report["summary_scores"] = {
        "readability": round(read_score),
        "sentence_structure": round(sent_score),
        "active_voice": round(passive_score),
        "conciseness": round(concise_score),
        "overall": overall,
    }

    return report


def print_prose_report(report):
    """Print a formatted prose quality report."""
    if "error" in report:
        print(f"Error: {report['error']}")
        return

    print("=" * 60)
    print("PROSE QUALITY REPORT")
    print("=" * 60)
    print(f"  Words: {report['word_count']:,}    Sentences: {report['sentence_count']}")

    # Readability
    r = report.get("readability", {})
    if r:
        print(f"\n  Readability:")
        print(f"    Flesch-Kincaid Grade: {r['flesch_kincaid_grade']}")
        print(f"    Flesch Reading Ease:  {r['flesch_reading_ease']}")
        print(f"    Gunning Fog Index:    {r['gunning_fog_index']}")

    # Sentence stats
    ss = report.get("sentence_stats", {})
    if ss:
        print(f"\n  Sentence Length:")
        print(f"    Average: {ss['average_words']} words    "
              f"Max: {ss['max_words']}    Long: {ss['long_sentence_count']}")

    # Passive voice
    pv = report.get("passive_voice", {})
    if pv:
        print(f"    Passive voice: {pv['percent']}% of sentences")

    # Issues
    issues = report.get("issues", [])
    if issues:
        print(f"\n  Issues:")
        for issue in issues:
            icon = {"OK": "  [OK]", "INFO": "  [..]", "WARNING": "  [!!]",
                    "FAIL": "  [XX]"}.get(issue.get("severity", "INFO"), "  [??]")
            print(f"    {icon} {issue['message']}")

            # Show examples for long sentences
            if issue.get("type") == "LONG_SENTENCES":
                for ex in issue.get("examples", [])[:3]:
                    print(f"         → ({ex['words']}w) {ex['preview']}")

            # Show wordy phrase suggestions
            if issue.get("type") == "WORDY_PHRASES":
                for sug in issue.get("suggestions", [])[:5]:
                    print(f"         → \"{sug['phrase']}\" → \"{sug['replacement']}\"")

    # Summary scores
    scores = report.get("summary_scores", {})
    if scores:
        print(f"\n  Scores (0-100):")
        for key, val in scores.items():
            bar_len = val // 5
            bar = "█" * bar_len + "░" * (20 - bar_len)
            label = key.replace("_", " ").title()
            print(f"    {label:20s} {bar} {val}")

    print()
