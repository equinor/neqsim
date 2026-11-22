"""Contracts for semantic destinations in the Jekyll navigation configuration."""

import re
import unittest
from pathlib import Path


DOCS = Path(__file__).resolve().parent
CONFIG = DOCS / "_config.yml"
NAV_ENTRY = re.compile(
    r"(?m)^  - title: (?P<title>[^\n]+)\n    url: (?P<url>[^\n]+)$"
)


class JekyllNavigationDocumentationTest(unittest.TestCase):
    """Keep the documentation and generated Java API routes distinct."""

    @classmethod
    def setUpClass(cls):
        cls.config = CONFIG.read_text(encoding="utf-8")
        cls.entries = {
            match.group("title"): match.group("url")
            for match in NAV_ENTRY.finditer(cls.config)
        }

    def test_reference_destinations_have_truthful_labels(self):
        self.assertEqual(
            self.entries["Reference Manual"],
            "/manual/neqsim_reference_manual.html",
        )
        self.assertEqual(
            self.entries["Documentation Index"],
            "/REFERENCE_MANUAL_INDEX",
        )
        self.assertEqual(
            self.entries["API Reference"],
            "/javadoc/index.html",
        )

    def test_navigation_titles_and_destinations_are_unique(self):
        matches = list(NAV_ENTRY.finditer(self.config))
        titles = [match.group("title") for match in matches]
        destinations = [match.group("url") for match in matches]

        self.assertEqual(len(titles), len(set(titles)))
        self.assertEqual(len(destinations), len(set(destinations)))

    def test_documentation_index_source_exists(self):
        self.assertTrue((DOCS / "REFERENCE_MANUAL_INDEX.md").is_file())


if __name__ == "__main__":
    unittest.main()
