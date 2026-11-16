"""Protect curated thermo landing-page navigation and local link targets."""

from pathlib import Path
import re
import unittest


DOCS = Path(__file__).resolve().parent
THERMO = DOCS / "thermo"
LANDING_PAGE = THERMO / "README.md"
REQUIRED_GUIDES = {
    "component_list.md",
    "fluid_classification.md",
    "reading_fluid_properties.md",
    "henry_law_reference.md",
    "empirical_solid_fugacity.md",
}
MARKDOWN_LINK = re.compile(r"\[[^\]]+\]\(([^)]+)\)")


class ThermoLandingPageNavigationTest(unittest.TestCase):
    """Protect essential guide coverage and path-valid thermo navigation."""

    @classmethod
    def setUpClass(cls):
        cls.landing = LANDING_PAGE.read_text(encoding="utf-8")
        cls.targets = [
            target.split("#", 1)[0]
            for target in MARKDOWN_LINK.findall(cls.landing)
            if target.split("#", 1)[0]
        ]

    def test_required_guides_are_linked(self):
        linked_stems = {
            Path(target).stem
            for target in self.targets
            if "://" not in target
        }
        for guide in REQUIRED_GUIDES:
            with self.subTest(guide=guide):
                self.assertTrue((THERMO / guide).is_file())
                self.assertIn(Path(guide).stem, linked_stems)

    def test_every_local_landing_page_target_exists(self):
        for target in self.targets:
            if "://" in target:
                continue
            candidate = THERMO / target
            choices = [
                candidate,
                Path(str(candidate) + ".md"),
                candidate / "README.md",
                candidate / "index.md",
            ]
            with self.subTest(target=target):
                self.assertTrue(
                    any(choice.is_file() for choice in choices),
                    msg=f"Broken thermo landing-page target: {target}",
                )


if __name__ == "__main__":
    unittest.main()
