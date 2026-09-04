"""Contracts for root-level standalone documentation rendering and navigation."""

import re
from pathlib import Path
from urllib.parse import unquote, urlsplit


ROOT = Path(__file__).resolve().parents[1]
DOCS = ROOT / "docs"
PAGES = tuple(
    DOCS / name
    for name in (
        "BROKEN_API_AUDIT_REPORT.md",
        "GITHUB_PAGES_SETUP.md",
        "co2_impurity_kinetics_guide.md",
        "compressor_thermal_model.md",
        "docker-getting-started.md",
        "java-getting-started.md",
        "modules.md",
        "pid-design-synthesis.md",
    )
)
MODULES = DOCS / "modules.md"

FRONT_MATTER = re.compile(r"\A---\n(?P<fields>.*?)\n---\n", re.DOTALL)
MARKDOWN_LINK = re.compile(r"(?<!!)\[[^\]]+\]\(([^)]+)\)")
HTML_LINK = re.compile(r'href=["\']([^"\']+)["\']')


def _visible_markdown(content: str) -> str:
    visible: list[str] = []
    fence_character: str | None = None
    for line in content.splitlines():
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


def _targets(content: str) -> tuple[str, ...]:
    visible = _visible_markdown(content)
    return tuple(MARKDOWN_LINK.findall(visible) + HTML_LINK.findall(visible))


def _target_candidates(source: Path, target: str) -> tuple[Path, ...]:
    parsed = urlsplit(target.strip().strip("<>"))
    if parsed.scheme or parsed.netloc or target.startswith("#"):
        return ()
    path_text = unquote(parsed.path)
    if not path_text or path_text.startswith("/"):
        return ()

    destination = (source.parent / path_text).resolve()
    if destination.suffix:
        return (destination,)
    return (
        destination,
        destination.with_suffix(".md"),
        destination / "index.md",
        destination / "README.md",
    )


def test_root_standalone_pages_render_one_title_from_front_matter() -> None:
    assert len(PAGES) == 8
    for page in PAGES:
        content = page.read_text(encoding="utf-8")
        match = FRONT_MATTER.match(content)
        assert match is not None, f"{page}: missing front matter"
        fields = match.group("fields")
        title = re.search(r"(?m)^title:\s*(.+)$", fields)
        description = re.search(r"(?m)^description:\s*(.+)$", fields)
        assert title is not None, f"{page}: missing title"
        assert description is not None, f"{page}: missing description"
        description_text = description.group(1).strip().strip("\"'")
        assert not description_text.endswith("..."), f"{page}: truncated description"

        visible = _visible_markdown(content[match.end() :])
        assert re.search(r"(?m)^#\s+", visible) is None, (
            f"{page}: Jekyll front-matter title must not be repeated as an H1"
        )


def test_root_standalone_page_links_use_published_urls_and_resolve() -> None:
    for page in PAGES:
        content = page.read_text(encoding="utf-8")
        for target in _targets(content):
            parsed = urlsplit(target.strip().strip("<>"))
            if parsed.scheme or parsed.netloc or target.startswith("#"):
                continue
            assert not unquote(parsed.path).endswith(".md"), (
                f"{page}: published internal link retains .md: {target}"
            )
            candidates = _target_candidates(page, target)
            if candidates:
                assert any(candidate.exists() for candidate in candidates), (
                    f"{page}: unresolved internal target {target}"
                )


def test_module_inventory_distinguishes_foundations_from_safety() -> None:
    content = MODULES.read_text(encoding="utf-8")
    assert "seven foundational module areas" in content
    assert "process safety is a cross-cutting capability" in content
    assert "## Cross-cutting process safety capabilities" in content

    for package_path in (
        "thermo",
        "thermodynamicoperations",
        "physicalproperties",
        "fluidmechanics",
        "process/equipment",
        "chemicalreactions",
        "statistics/parameterfitting",
        "process",
        "process/safety",
        "process/equipment/tank",
        "process/util/fire",
    ):
        assert (ROOT / "src/main/java/neqsim" / package_path).is_dir(), package_path
