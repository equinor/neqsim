"""Contracts for thermodynamic documentation metadata and math rendering."""

from pathlib import Path
import re
import unittest


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
    """Remove fenced examples before inspecting rendered Markdown."""
    fence_pattern = chr(96) * 3 + r"[^\n]*\n.*?" + chr(96) * 3
    return re.sub(fence_pattern, "", source, flags=re.DOTALL)


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
                title, _description, body = parse_front_matter(page.read_text(encoding="utf-8"))
                rendered_markdown = remove_fenced_code(body)
                headings = re.findall(r"^#\s+(.+?)\s*$", rendered_markdown, re.MULTILINE)
                self.assertNotIn(title, headings)

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
