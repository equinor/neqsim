"""Contracts for root and first-level documentation landing pages."""

import re
import unittest
from pathlib import Path
from urllib.parse import unquote, urlsplit

ROOT = Path(__file__).resolve().parents[1]
DOCS = ROOT / "docs"
REFERENCE_INDEX = DOCS / "REFERENCE_MANUAL_INDEX.md"
LANDING_PAGE = DOCS / "index.md"
LAYOUT = DOCS / "_layouts" / "default.html"
ENHANCEMENTS = DOCS / "assets" / "js" / "enhancements.js"
GENERATED_ROUTES = {"/javadoc/index.html"}
FOUNDATIONAL_PACKAGE_TARGETS = {
    "chemicalreactions/README.html": DOCS / "chemicalreactions" / "README.md",
    "statistics/README.html": DOCS / "statistics" / "README.md",
}

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


def test_main_landing_routes_to_foundational_package_guides() -> None:
    targets = _link_targets(LANDING_PAGE.read_text(encoding="utf-8"))
    for destination, package_landing in FOUNDATIONAL_PACKAGE_TARGETS.items():
        assert destination in targets, f"{LANDING_PAGE}: missing {destination}"
        assert package_landing in _target_candidates(LANDING_PAGE, destination)
        assert package_landing.is_file()



def _layout_routes(text: str) -> tuple[str, ...]:
    return tuple(
        re.findall(r'href="{{ \'([^\']+)\' \| relative_url }}"', text)
    )


def _route_candidates(route: str) -> tuple[Path, ...]:
    destination = DOCS / route.lstrip("/")
    candidates = [destination]
    if route.endswith("/"):
        candidates.extend(
            (
                destination / "index.md",
                destination / "README.md",
                destination.with_suffix(".md"),
            )
        )
    elif destination.suffix == ".html":
        candidates.append(destination.with_suffix(".md"))
    elif not destination.suffix:
        candidates.extend(
            (
                destination.with_suffix(".md"),
                destination / "index.md",
                destination / "README.md",
            )
        )
    return tuple(candidates)


def test_global_navigation_is_task_oriented_and_complete() -> None:
    layout = LAYOUT.read_text(encoding="utf-8")
    routes = _layout_routes(layout)
    expected_groups = {
        "nav-start-button": "nav-start-menu",
        "nav-modeling-button": "nav-modeling-menu",
        "nav-workflows-button": "nav-workflows-menu",
        "nav-reference-button": "nav-reference-menu",
    }
    expected_routes = {
        "/wiki/getting_started",
        "/java-getting-started",
        "/docker-getting-started",
        "/tutorials/",
        "/cookbook/",
        "/troubleshooting/",
        "/thermo/",
        "/thermodynamicoperations/",
        "/physical_properties/",
        "/process/",
        "/simulation/dynamic_simulation_guide",
        "/pvtsimulation/",
        "/pvtsimulation/flowassurance/",
        "/engineering/",
        "/standards/",
        "/examples/",
        "/integration/",
        "/fielddevelopment/",
        "/process/optimization/",
        "/safety/",
        "/risk/",
        "/emissions/",
        "/REFERENCE_MANUAL_INDEX",
        "/manual/neqsim_reference_manual.html",
        "/javadoc/index.html",
        "/modules.html",
        "/search/",
    }

    assert '<nav class="site-nav" aria-label="Primary">' in layout
    assert ">\n            Documentation\n" not in layout
    assert ">\n            Guides\n" not in layout
    for button_id, menu_id in expected_groups.items():
        button = re.search(
            rf'<button[^>]*id="{button_id}"[^>]*>',
            layout,
        )
        assert button is not None, button_id
        button_markup = button.group(0)
        assert 'type="button"' in button_markup
        assert 'aria-expanded="false"' in button_markup
        assert f'aria-controls="{menu_id}"' in button_markup
        assert (
            f'id="{menu_id}" class="nav-dropdown-content" '
            f'aria-labelledby="{button_id}"'
        ) in layout

    assert expected_routes.issubset(set(routes))
    internal_routes = tuple(route for route in routes if route != "/")
    assert len(internal_routes) == len(set(internal_routes))
    for route in internal_routes:
        if route in GENERATED_ROUTES:
            continue
        assert any(candidate.exists() for candidate in _route_candidates(route)), route


def test_global_navigation_script_synchronizes_accessible_state() -> None:
    script = ENHANCEMENTS.read_text(encoding="utf-8")
    required_behavior = (
        "btn.setAttribute('aria-expanded', isOpen ? 'true' : 'false')",
        "closeOthers(dropdown)",
        "closeAndFocus(dropdown)",
        "buttonFor(dropdown).focus()",
        "event.key === 'ArrowDown'",
        "event.key !== 'ArrowDown' && event.key !== 'ArrowUp'",
        "event.key === 'Escape'",
        "links.indexOf(document.activeElement)",
        "document.querySelector('.nav-dropdown.is-open')",
    )
    for marker in required_behavior:
        assert marker in script, marker



def test_sis_navigation_describes_screening_and_review_boundary() -> None:
    routes = {
        DOCS / "index.md": 'href="risk/sis-integration.html"',
        DOCS / "risk" / "index.md": "[P2: SIS/SIF Integration](sis-integration)",
        REFERENCE_INDEX: "[docs/risk/sis-integration.md](risk/sis-integration.md)",
    }
    bounded_description = "PFD and LOPA screening; independent assessment required"

    for source, route in routes.items():
        visible = _without_fenced_code(source.read_text(encoding="utf-8"))
        matching_lines = [line for line in visible.splitlines() if route in line]
        assert len(matching_lines) == 1, f"{source}: expected one maintained SIS route"
        description = matching_lines[0]
        assert bounded_description in description, f"{source}: missing SIS assessment boundary"
        assert "SIL verification" not in description
        assert "IEC 61508/61511 compliance" not in description


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

def load_tests(
    loader: unittest.TestLoader,
    tests: unittest.TestSuite,
    pattern: str | None,
) -> unittest.TestSuite:
    """Expose the module-level contracts to the repository's unittest gate."""
    del loader, tests, pattern
    return unittest.TestSuite(
        unittest.FunctionTestCase(contract)
        for contract in (
            test_top_level_landing_metadata_and_rendered_titles,
            test_top_level_landing_relative_targets_resolve,
            test_main_landing_routes_to_foundational_package_guides,
            test_global_navigation_is_task_oriented_and_complete,
            test_global_navigation_script_synchronizes_accessible_state,
            test_sis_navigation_describes_screening_and_review_boundary,
            test_single_landing_directories_are_discoverable,
        )
    )

