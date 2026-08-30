"""Contracts for package landing-page metadata and discoverability."""

from pathlib import Path
import re
import unittest


DOCS = Path(__file__).resolve().parent
PRIMARY_HUB = DOCS / "README.md"
REFERENCE_INDEX = DOCS / "REFERENCE_MANUAL_INDEX.md"
PACKAGE_LANDING_PAGES = (
    DOCS / "fluidmechanics/README.md",
    DOCS / "chemicalreactions/README.md",
    DOCS / "statistics/README.md",
    DOCS / "util/README.md",
    DOCS / "mathlib/README.md",
    DOCS / "development/README.md",
)


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
    """Remove fenced examples before inspecting rendered Markdown headings."""
    fence_pattern = chr(96) * 3 + r"[^\n]*\n.*?" + chr(96) * 3
    return re.sub(fence_pattern, "", source, flags=re.DOTALL)


class PackageLandingPageDocumentationContractTest(unittest.TestCase):
    """Protect package hubs from duplicate rendered titles and navigation drift."""

    @classmethod
    def setUpClass(cls):
        cls.primary_hub = PRIMARY_HUB.read_text(encoding="utf-8")
        cls.reference_index = REFERENCE_INDEX.read_text(encoding="utf-8")

    def test_package_hubs_have_searchable_front_matter(self):
        for page in PACKAGE_LANDING_PAGES:
            with self.subTest(page=page):
                title, description, _body = parse_front_matter(page.read_text(encoding="utf-8"))
                self.assertTrue(title)
                self.assertGreaterEqual(len(description.split()), 5)

    def test_front_matter_title_is_not_repeated_as_h1(self):
        for page in PACKAGE_LANDING_PAGES:
            with self.subTest(page=page):
                title, _description, body = parse_front_matter(page.read_text(encoding="utf-8"))
                rendered_markdown = remove_fenced_code(body)
                headings = re.findall(r"^#\s+(.+?)\s*$", rendered_markdown, re.MULTILINE)
                self.assertNotIn(title, headings)

    def test_package_hubs_remain_discoverable(self):
        for page in PACKAGE_LANDING_PAGES:
            with self.subTest(page=page):
                package_directory = page.parent.name
                self.assertIn("({}/)".format(package_directory), self.primary_hub)
                self.assertIn("{}/README.md".format(package_directory), self.reference_index)


if __name__ == "__main__":
    unittest.main()
