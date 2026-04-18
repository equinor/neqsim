"""
book_checker — Book-level quality checks.

Extends paper-level quality logic to handle multi-chapter scope:
structure, completeness, consistency, cross-references, bibliography,
figures, nomenclature, and page estimates.
"""

import re
from pathlib import Path

import yaml

import book_builder


# ---------------------------------------------------------------------------
# Individual checks
# ---------------------------------------------------------------------------

def check_structure(book_dir):
    """Verify all chapters listed in book.yaml exist on disk."""
    book_dir = Path(book_dir)
    cfg = book_builder.load_book_config(book_dir)
    issues = []

    # Check frontmatter
    for fm in cfg.get("frontmatter", []):
        fm_path = book_dir / "frontmatter" / f"{fm}.md"
        if not fm_path.exists():
            issues.append({
                "check": "structure",
                "severity": "warning",
                "message": f"Frontmatter file missing: {fm}.md",
            })

    # Check chapters
    for ch_num, ch, part_title in book_builder.iter_chapters(cfg):
        ch_dir = book_builder.resolve_chapter_dir(book_dir, ch)
        if not ch_dir.exists():
            issues.append({
                "check": "structure",
                "severity": "error",
                "message": f"Chapter directory missing: {ch['dir']}",
            })
        else:
            ch_md = ch_dir / "chapter.md"
            if not ch_md.exists():
                issues.append({
                    "check": "structure",
                    "severity": "error",
                    "message": f"chapter.md missing in {ch['dir']}",
                })

    # Check backmatter
    for bm in cfg.get("backmatter", []):
        bm_path = book_dir / "backmatter" / f"{bm}.md"
        if not bm_path.exists():
            issues.append({
                "check": "structure",
                "severity": "warning",
                "message": f"Backmatter file missing: {bm}.md",
            })

    return issues


def check_completeness(book_dir, target_words_per_chapter=5000):
    """Check word counts and TODO markers per chapter."""
    book_dir = Path(book_dir)
    cfg = book_builder.load_book_config(book_dir)
    issues = []

    for ch_num, ch, _part in book_builder.iter_chapters(cfg):
        ch_dir = book_builder.resolve_chapter_dir(book_dir, ch)
        ch_md = ch_dir / "chapter.md"
        if not ch_md.exists():
            continue

        text = ch_md.read_text(encoding="utf-8")
        words = len(text.split())
        todos = text.upper().count("TODO")

        if todos > 0:
            issues.append({
                "check": "completeness",
                "severity": "warning",
                "message": f"Chapter {ch_num} ({ch['dir']}): {todos} TODO marker(s)",
            })

        if words < target_words_per_chapter // 5:
            issues.append({
                "check": "completeness",
                "severity": "warning",
                "message": f"Chapter {ch_num} ({ch['dir']}): only {words} words "
                           f"(target ~{target_words_per_chapter})",
            })

    return issues


def check_consistency(book_dir):
    """Check notation/terminology consistency across chapters."""
    book_dir = Path(book_dir)
    cfg = book_builder.load_book_config(book_dir)
    issues = []

    # Load nomenclature if present
    nomen_path = book_dir / "nomenclature.yaml"
    known_symbols = set()
    if nomen_path.exists():
        try:
            nomen = yaml.safe_load(nomen_path.read_text(encoding="utf-8"))
            if nomen and "symbols" in nomen:
                known_symbols = set(nomen["symbols"].keys())
        except Exception:
            issues.append({
                "check": "consistency",
                "severity": "warning",
                "message": "Could not parse nomenclature.yaml",
            })

    # Check that each chapter uses consistent heading numbering
    for ch_num, ch, _part in book_builder.iter_chapters(cfg):
        ch_dir = book_builder.resolve_chapter_dir(book_dir, ch)
        ch_md = ch_dir / "chapter.md"
        if not ch_md.exists():
            continue

        text = ch_md.read_text(encoding="utf-8")

        # Check section numbering matches chapter number
        for m in re.finditer(r"^##\s+(\d+)\.(\d+)\s", text, re.MULTILINE):
            sec_ch = int(m.group(1))
            if sec_ch != ch_num:
                issues.append({
                    "check": "consistency",
                    "severity": "warning",
                    "message": f"Chapter {ch_num} ({ch['dir']}): "
                               f"section numbering {sec_ch}.{m.group(2)} "
                               f"doesn't match chapter number {ch_num}",
                })

    return issues


def check_cross_refs(book_dir):
    """Check that cross-references (Chapter X, Figure Y.Z, Eq. Z.W) resolve."""
    book_dir = Path(book_dir)
    cfg = book_builder.load_book_config(book_dir)
    issues = []

    # Collect all chapter numbers
    chapter_nums = set()
    for ch_num, ch, _part in book_builder.iter_chapters(cfg):
        chapter_nums.add(ch_num)

    # Scan all chapters for references
    for ch_num, ch, _part in book_builder.iter_chapters(cfg):
        ch_dir = book_builder.resolve_chapter_dir(book_dir, ch)
        ch_md = ch_dir / "chapter.md"
        if not ch_md.exists():
            continue

        text = ch_md.read_text(encoding="utf-8")

        # Chapter references: "Chapter 5", "chapter 3"
        for m in re.finditer(r"[Cc]hapter\s+(\d+)", text):
            ref_num = int(m.group(1))
            if ref_num not in chapter_nums:
                issues.append({
                    "check": "cross_refs",
                    "severity": "error",
                    "message": f"Chapter {ch_num} ({ch['dir']}): "
                               f"reference to non-existent Chapter {ref_num}",
                })

    return issues


def check_bibliography(book_dir):
    """Check that all \\cite{{key}} references resolve to refs.bib."""
    book_dir = Path(book_dir)
    cfg = book_builder.load_book_config(book_dir)
    issues = []

    # Read refs.bib
    bib_path = book_dir / "refs.bib"
    bib_keys = set()
    if bib_path.exists():
        bib_text = bib_path.read_text(encoding="utf-8")
        for m in re.finditer(r"@\w+\{(\w+)", bib_text):
            bib_keys.add(m.group(1))

    # Scan chapters for citation keys
    all_cited = set()
    for ch_num, ch, _part in book_builder.iter_chapters(cfg):
        ch_dir = book_builder.resolve_chapter_dir(book_dir, ch)
        ch_md = ch_dir / "chapter.md"
        if not ch_md.exists():
            continue

        text = ch_md.read_text(encoding="utf-8")
        for m in re.finditer(r"\\cite\{([^}]+)\}", text):
            keys = [k.strip() for k in m.group(1).split(",")]
            all_cited.update(keys)

    # Check for unresolved citations
    for key in sorted(all_cited - bib_keys):
        issues.append({
            "check": "bibliography",
            "severity": "error",
            "message": f"Unresolved citation: \\cite{{{key}}}",
        })

    return issues


def check_figures(book_dir):
    """Check that all referenced figures exist on disk."""
    book_dir = Path(book_dir)
    cfg = book_builder.load_book_config(book_dir)
    issues = []

    for ch_num, ch, _part in book_builder.iter_chapters(cfg):
        ch_dir = book_builder.resolve_chapter_dir(book_dir, ch)
        ch_md = ch_dir / "chapter.md"
        if not ch_md.exists():
            continue

        text = ch_md.read_text(encoding="utf-8")
        for m in re.finditer(r"!\[([^\]]*)\]\(([^)]+)\)", text):
            fig_path = m.group(2)
            # Try resolving relative to chapter directory
            full = ch_dir / fig_path
            if not full.exists():
                issues.append({
                    "check": "figures",
                    "severity": "warning",
                    "message": f"Chapter {ch_num} ({ch['dir']}): "
                               f"figure not found: {fig_path}",
                })

    return issues


def check_page_estimate(book_dir, max_pages=None):
    """Estimate total page count and compare against publisher limit."""
    book_dir = Path(book_dir)
    cfg = book_builder.load_book_config(book_dir)
    issues = []

    total_words = 0
    for ch_num, ch, _part in book_builder.iter_chapters(cfg):
        ch_dir = book_builder.resolve_chapter_dir(book_dir, ch)
        ch_md = ch_dir / "chapter.md"
        if ch_md.exists():
            total_words += len(ch_md.read_text(encoding="utf-8").split())

    estimated_pages = max(1, total_words // 250)

    # Load publisher max_pages if available
    if max_pages is None:
        profile = book_builder._load_publisher_profile(cfg.get("publisher", "self"))
        max_pages = profile.get("max_pages", 1000)

    if estimated_pages > max_pages:
        issues.append({
            "check": "page_estimate",
            "severity": "warning",
            "message": f"Estimated {estimated_pages} pages exceeds "
                       f"publisher limit of {max_pages}",
        })

    return issues


# ---------------------------------------------------------------------------
# Aggregate check runner
# ---------------------------------------------------------------------------

ALL_CHECKS = {
    "structure": check_structure,
    "completeness": check_completeness,
    "consistency": check_consistency,
    "cross_refs": check_cross_refs,
    "bibliography": check_bibliography,
    "figures": check_figures,
    "page_estimate": check_page_estimate,
}


def run_checks(book_dir, checks=None):
    """Run quality checks on a book project.

    Parameters
    ----------
    book_dir : str or Path
        Path to the book project directory.
    checks : list of str or None
        Which checks to run.  ``None`` or ``["all"]`` runs all checks.

    Returns
    -------
    list of dict
        List of issue dicts with keys: check, severity, message.
    """
    if checks is None or checks == ["all"] or "all" in checks:
        checks = list(ALL_CHECKS.keys())

    all_issues = []
    for name in checks:
        fn = ALL_CHECKS.get(name)
        if fn is None:
            print(f"[book_checker] Unknown check: {name}")
            continue
        try:
            issues = fn(book_dir)
            all_issues.extend(issues)
        except Exception as exc:
            all_issues.append({
                "check": name,
                "severity": "error",
                "message": f"Check failed with exception: {exc}",
            })

    return all_issues


def format_issues(issues):
    """Format issues into a readable report string."""
    if not issues:
        return "All checks passed."

    lines = []
    errors = [i for i in issues if i["severity"] == "error"]
    warnings = [i for i in issues if i["severity"] == "warning"]

    if errors:
        lines.append(f"\n  ERRORS ({len(errors)}):")
        for issue in errors:
            lines.append(f"    [{issue['check']}] {issue['message']}")

    if warnings:
        lines.append(f"\n  WARNINGS ({len(warnings)}):")
        for issue in warnings:
            lines.append(f"    [{issue['check']}] {issue['message']}")

    lines.append(f"\n  Summary: {len(errors)} error(s), {len(warnings)} warning(s)")
    return "\n".join(lines)
