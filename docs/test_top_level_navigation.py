"""Contracts for root and first-level documentation landing pages."""

import re
from pathlib import Path
from urllib.parse import unquote, urlsplit

ROOT = Path(__file__).resolve().parents[1]
DOCS = ROOT / "docs"
REFERENCE_INDEX = DOCS / "REFERENCE_MANUAL_INDEX.md"

FRONT_MATTER = re.compile(r"\A---\n(?P<fields>.*?)\n---\n", re.DOTALL)
MARKDOWN_LINK = re.compile(r"\[[^\]]+\]\(([^)]+)\)")
HTML_LINK = re.compile(r'href=["\']([^"\']+)["\']')


def _landing_pages() -> tuple[Path, ...]:
    pages = [DOCS / "index.md", DOCS / "README.md"]
    for directory in DOCS.iterdir():
        if not directory.is_dir():
            continue
        for name in ("index.md", "README.md"):
            candidate = directory / name
            if candidate.exists():
                pages.append(candidate)
    return tuple(sorted(set(pages)))


def _front_matter(page: Path) -> tuple[str, str, str]:
    text = page.read_text(encoding="utf-8")
    match = FRONT_MATTER.match(text)
    assert match is not None, f"{page}: missing front matter"
    fields = match.group("fields")
    title = re.search(r"^title:\s*(.+)$", fields, re.MULTILINE)
    description = re.search(r"^description:\s*(.+)$", fields, re.MULTILINE)
    assert title is not None, f"{page}: missing title"
    assert description is not None, f"{page}: missing description"
    return text, title.group(1).strip(), description.group(1).strip()


def _without_fenced_code(text: str) -> str:
    visible: list[str] = []
    fence_character: str | None = None
    for line in text.splitlines():
        marker = re.match(r"^\s*((?:\x60){3,}|~{3,})", line)
        if marker is not None:
            character = marker.group(1)[0]
            if fence_character is None:
                fence_character = character
            elif character == fence_character:
                fence_character = None
            continue
        if fence_character is None:
            visible.append(line)
    assert fence_character is None, "unclosed fenced code block"
    return "\n".join(visible)


def _link_targets(text: str) -> tuple[str, ...]:
    visible = _without_fenced_code(text)
    return tuple(MARKDOWN_LINK.findall(visible) + HTML_LINK.findall(visible))


def _target_candidates(source: Path, target: str) -> tuple[Path, ...]:
    parsed = urlsplit(target.strip().strip("<>"))
    if parsed.scheme or parsed.netloc or target.startswith("#"):
        return ()
    relative = unquote(parsed.path)
    if not relative or relative.startswith("/"):
        return ()

    destination = (source.parent / relative).resolve()
    candidates = [destination]
    if destination.suffix == ".html":
        candidates.append(destination.with_suffix(".md"))
    if relative.endswith("/") or not destination.suffix:
        candidates.extend(
            (
                destination / "index.md",
                destination / "README.md",
                destination.with_suffix(".md"),
            )
        )
    return tuple(candidates)


def test_top_level_landing_metadata_and_rendered_titles() -> None:
    pages = _landing_pages()
    assert len(pages) == 50
    for page in pages:
        text, _title, description = _front_matter(page)
        words = re.findall(r"[A-Za-z0-9][A-Za-z0-9_-]*", description)
        assert len(words) >= 5, f"{page}: description has {len(words)} words"

        body = FRONT_MATTER.sub("", text, count=1)
        visible = _without_fenced_code(body)
        assert re.search(r"^#\s+", visible, re.MULTILINE) is None, (
            f"{page}: front-matter title must not be repeated as H1"
        )


def test_top_level_landing_relative_targets_resolve() -> None:
    for page in _landing_pages():
        text = page.read_text(encoding="utf-8")
        for target in _link_targets(text):
            candidates = _target_candidates(page, target)
            if not candidates:
                continue
            assert any(candidate.exists() for candidate in candidates), (
                f"{page}: unresolved relative target {target}"
            )


def test_single_landing_directories_are_discoverable() -> None:
    navigation_sources = (
        DOCS / "index.md",
        DOCS / "README.md",
        REFERENCE_INDEX,
    )
    linked: set[Path] = set()
    for source in navigation_sources:
        text = source.read_text(encoding="utf-8")
        for target in _link_targets(text):
            for candidate in _target_candidates(source, target):
                if candidate.exists():
                    linked.add(candidate.resolve())

    for directory in DOCS.iterdir():
        if not directory.is_dir():
            continue
        landings = [
            candidate
            for candidate in (directory / "index.md", directory / "README.md")
            if candidate.exists()
        ]
        if len(landings) == 1:
            assert landings[0].resolve() in linked, (
                f"{landings[0]}: single landing page is absent from root/reference navigation"
            )
