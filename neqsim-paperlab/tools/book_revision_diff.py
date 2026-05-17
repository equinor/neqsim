"""Manuscript revision diff — produces a track-changes report between two
git revisions of a book or paper directory.

Usage::

    diff = revision_diff(book_dir, "v1", "v2")
    write_diff_report(book_dir, "v1", "v2", out_path="revisions/v1_to_v2.md")

The report is plain markdown with three sections per file:

* **Added paragraphs** — paragraphs in v2 not present in v1
* **Removed paragraphs** — paragraphs in v1 not present in v2
* **Changed lines** — unified diff context

Useful for journal R&R responses ("Reviewer 2 requested ...; addressed in
Section 3.2 — see diff below").
"""
from __future__ import annotations

import difflib
import subprocess
from pathlib import Path


def _git_show(repo: Path, ref: str, rel: str) -> str | None:
    try:
        out = subprocess.run(
            ["git", "-C", str(repo), "show", f"{ref}:{rel}"],
            capture_output=True,
            text=True,
            timeout=30,
            check=False,
        )
        if out.returncode != 0:
            return None
        return out.stdout
    except Exception:
        return None


def _git_ls(repo: Path, ref: str, sub: str) -> list[str]:
    try:
        out = subprocess.run(
            ["git", "-C", str(repo), "ls-tree", "-r", "--name-only", ref, sub],
            capture_output=True,
            text=True,
            timeout=30,
            check=False,
        )
        if out.returncode != 0:
            return []
        return [ln.strip() for ln in out.stdout.splitlines() if ln.strip().endswith(".md")]
    except Exception:
        return []


def _split_paragraphs(text: str) -> list[str]:
    return [p.strip() for p in text.split("\n\n") if p.strip()]


def revision_diff(book_dir, ref_old: str, ref_new: str) -> dict:
    book_dir = Path(book_dir).resolve()
    # Find git repo root
    repo = book_dir
    while repo != repo.parent and not (repo / ".git").exists():
        repo = repo.parent
    if not (repo / ".git").exists():
        raise RuntimeError(f"No .git found above {book_dir}")
    rel_book = book_dir.relative_to(repo).as_posix()

    files_old = _git_ls(repo, ref_old, rel_book)
    files_new = _git_ls(repo, ref_new, rel_book)
    all_files = sorted(set(files_old) | set(files_new))

    report: dict[str, dict] = {}
    for rel in all_files:
        old_text = _git_show(repo, ref_old, rel) or ""
        new_text = _git_show(repo, ref_new, rel) or ""
        if old_text == new_text:
            continue
        old_p = _split_paragraphs(old_text)
        new_p = _split_paragraphs(new_text)
        added = [p for p in new_p if p not in old_p]
        removed = [p for p in old_p if p not in new_p]
        unified = list(
            difflib.unified_diff(
                old_text.splitlines(keepends=False),
                new_text.splitlines(keepends=False),
                fromfile=f"{ref_old}:{rel}",
                tofile=f"{ref_new}:{rel}",
                n=2,
                lineterm="",
            )
        )
        report[rel] = {"added": added, "removed": removed, "unified": unified}
    return report


def render_diff_report(report: dict, ref_old: str, ref_new: str) -> str:
    lines = [
        f"# Revision diff: `{ref_old}` → `{ref_new}`",
        "",
        f"{len(report)} file(s) changed.",
        "",
    ]
    for rel, info in sorted(report.items()):
        lines.append(f"## `{rel}`")
        lines.append("")
        if info["added"]:
            lines.append(f"### Added ({len(info['added'])})")
            for p in info["added"][:30]:
                snippet = p.replace("\n", " ")
                if len(snippet) > 280:
                    snippet = snippet[:277] + "…"
                lines.append(f"- {snippet}")
            lines.append("")
        if info["removed"]:
            lines.append(f"### Removed ({len(info['removed'])})")
            for p in info["removed"][:30]:
                snippet = p.replace("\n", " ")
                if len(snippet) > 280:
                    snippet = snippet[:277] + "…"
                lines.append(f"- {snippet}")
            lines.append("")
        if info["unified"]:
            lines.append("<details><summary>Unified diff</summary>")
            lines.append("")
            lines.append("```diff")
            lines.extend(info["unified"][:400])
            lines.append("```")
            lines.append("")
            lines.append("</details>")
            lines.append("")
    return "\n".join(lines)


def write_diff_report(book_dir, ref_old: str, ref_new: str, out_path=None) -> Path:
    rep = revision_diff(book_dir, ref_old, ref_new)
    md = render_diff_report(rep, ref_old, ref_new)
    book_dir = Path(book_dir)
    out = Path(out_path) if out_path else book_dir / "revisions" / f"{ref_old}_to_{ref_new}.md"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(md, encoding="utf-8")
    print(f"[revision_diff] wrote {out}  ({len(rep)} changed file(s))")
    return out
