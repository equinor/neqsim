"""Contracts for thermodynamic documentation metadata and math rendering."""

from pathlib import Path
import re
import unittest
from urllib.parse import unquote, urlsplit


DOCS = Path(__file__).resolve().parent
SCOPE_DIRECTORIES = (
    DOCS / "fluidmechanics",
    DOCS / "physical_properties",
    DOCS / "thermo",
    DOCS / "thermodynamicoperations",
)
EXCLUDED_FILE = DOCS / "thermodynamicoperations/TPflash_algorithm.md"
EXCLUDED_DIRECTORY = DOCS / "thermo/characterization"


def scoped_pages():
    """Return the stable rendering-contract scope in deterministic order."""
    pages = []
    for directory in SCOPE_DIRECTORIES:
        for page in directory.rglob("*.md"):
            if page == EXCLUDED_FILE or EXCLUDED_DIRECTORY in page.parents:
                continue
            pages.append(page)
    return tuple(sorted(pages))


def parse_front_matter(source):
    """Return normalized title, description, and body from one Markdown page."""
    match = re.match(r"\A---\n(?P<metadata>.*?)\n---\n(?P<body>.*)\Z", source, re.DOTALL)
    if match is None:
        raise AssertionError("Missing Jekyll front matter")

    metadata = match.group("metadata")
    title_match = re.search(r"^title:\s*(.+?)\s*$", metadata, re.MULTILINE)
    description_match = re.search(r"^description:\s*(.+?)\s*$", metadata, re.MULTILINE)
    if title_match is None or description_match is None:
        raise AssertionError("Front matter must define title and description")

    title = title_match.group(1).strip().strip("'\"")
    description = description_match.group(1).strip().strip("'\"")
    return title, description, match.group("body")


def remove_fenced_code(source):
    """Remove fenced examples and reject unmatched backtick or tilde fences."""
    visible = []
    fence_character = None
    for line in source.splitlines():
        marker = re.match(r"^\s*(\x60{3,}|~{3,})", line)
        if marker is not None:
            character = marker.group(1)[0]
            if fence_character is None:
                fence_character = character
            elif character == fence_character:
                fence_character = None
            continue
        if fence_character is None:
            visible.append(line)

    if fence_character is not None:
        raise AssertionError("Unclosed fenced code block")
    return "\n".join(visible)


def target_candidates(source, target):
    """Return repository-relative file candidates for one Markdown target."""
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


class ThermodynamicDocumentationRenderingContractTest(unittest.TestCase):
    """Protect the bounded thermo/fluid/property documentation rendering surface."""

    @classmethod
    def setUpClass(cls):
        cls.pages = scoped_pages()

    def test_scope_remains_substantial(self):
        self.assertGreaterEqual(len(self.pages), 66)

    def test_pages_have_searchable_front_matter(self):
        for page in self.pages:
            with self.subTest(page=page):
                title, description, _body = parse_front_matter(page.read_text(encoding="utf-8"))
                self.assertTrue(title)
                self.assertGreaterEqual(len(description.split()), 5)

    def test_front_matter_title_is_not_repeated_as_h1(self):
        for page in self.pages:
            with self.subTest(page=page):
                _title, _description, body = parse_front_matter(
                    page.read_text(encoding="utf-8")
                )
                rendered_markdown = remove_fenced_code(body)
                headings = re.findall(r"^#\s+(.+?)\s*$", rendered_markdown, re.MULTILINE)
                self.assertEqual([], headings)

    def test_pages_have_balanced_fenced_code(self):
        for page in self.pages:
            with self.subTest(page=page):
                _title, _description, body = parse_front_matter(
                    page.read_text(encoding="utf-8")
                )
                remove_fenced_code(body)

    def test_repository_relative_targets_resolve(self):
        markdown_link = re.compile(r"\[[^\]]*\]\(([^)\s]+)\)")
        html_link = re.compile(r"""href=["']([^"']+)["']""")
        for page in self.pages:
            _title, _description, body = parse_front_matter(
                page.read_text(encoding="utf-8")
            )
            rendered_markdown = remove_fenced_code(body)
            targets = markdown_link.findall(rendered_markdown)
            targets.extend(html_link.findall(rendered_markdown))
            for target in targets:
                candidates = target_candidates(page, target)
                if not candidates:
                    continue
                with self.subTest(page=page, target=target):
                    self.assertTrue(
                        any(candidate.exists() for candidate in candidates),
                        "Unresolved repository-relative target",
                    )

    def test_pages_use_supported_math_delimiters(self):
        unsupported = re.compile(r"\\\[|\\\]|\\\(|\\\)")
        for page in self.pages:
            with self.subTest(page=page):
                _title, _description, body = parse_front_matter(
                    page.read_text(encoding="utf-8")
                )
                rendered_markdown = remove_fenced_code(body)
                self.assertIsNone(unsupported.search(rendered_markdown))


if __name__ == "__main__":
    unittest.main()
