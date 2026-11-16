"""Keep every process-safety guide discoverable from both documentation indexes."""

from pathlib import Path
import re
import unittest


DOCS = Path(__file__).resolve().parent
SAFETY = DOCS / "process" / "safety"
SECTION_INDEX = SAFETY / "index.md"
REFERENCE_INDEX = DOCS / "REFERENCE_MANUAL_INDEX.md"
EXCLUDED = {"README.md", "index.md"}
MARKDOWN_LINK = re.compile(r"\[[^\]]+\]\(([^)#]+)(?:#[^)]+)?\)")


class SafetyDocumentationNavigationTest(unittest.TestCase):
    """Protect complete and path-valid navigation for the safety guide set."""

    @classmethod
    def setUpClass(cls):
        cls.pages = sorted(
            page
            for page in SAFETY.glob("*.md")
            if page.name not in EXCLUDED
        )
        cls.section = SECTION_INDEX.read_text(encoding="utf-8")
        cls.reference = REFERENCE_INDEX.read_text(encoding="utf-8")

    def test_every_safety_page_is_in_section_index(self):
        self.assertTrue(self.pages)
        for page in self.pages:
            with self.subTest(page=page.name):
                self.assertIn(f"({page.name})", self.section)

    def test_every_safety_page_is_in_reference_index(self):
        for page in self.pages:
            target = f"(process/safety/{page.name})"
            with self.subTest(page=page.name):
                self.assertIn(target, self.reference)

    def test_section_index_targets_exist(self):
        for target in MARKDOWN_LINK.findall(self.section):
            with self.subTest(target=target):
                self.assertTrue(
                    (SAFETY / target).is_file(),
                    msg=f"Broken safety-index target: {target}",
                )


if __name__ == "__main__":
    unittest.main()
