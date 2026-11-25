"""Protect curated thermo landing-page and reference-manual navigation."""

from pathlib import Path
import re
import unittest


DOCS = Path(__file__).resolve().parent
THERMO = DOCS / "thermo"
LANDING_PAGE = THERMO / "README.md"
REFERENCE_INDEX = DOCS / "REFERENCE_MANUAL_INDEX.md"
REQUIRED_GUIDES = {
    "component_list.md",
    "fluid_classification.md",
    "reading_fluid_properties.md",
    "henry_law_reference.md",
    "empirical_solid_fugacity.md",
}
MARKDOWN_LINK = re.compile(r"\[[^\]]+\]\(([^)]+)\)")
SCOPE_NAVIGATION_PAGES = (
    THERMO / "README.md",
    DOCS / "physical_properties" / "README.md",
    DOCS / "physical_properties" / "index.md",
    DOCS / "thermodynamicoperations" / "README.md",
)


class ThermoLandingPageNavigationTest(unittest.TestCase):
    """Protect essential guide coverage and path-valid thermo navigation."""

    @classmethod
    def setUpClass(cls):
        cls.landing = LANDING_PAGE.read_text(encoding="utf-8")
        cls.reference = REFERENCE_INDEX.read_text(encoding="utf-8")
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

    def test_required_guides_are_in_reference_manual(self):
        for guide in REQUIRED_GUIDES:
            with self.subTest(guide=guide):
                self.assertIn(f"(thermo/{guide})", self.reference)

    def test_scope_navigation_uses_extensionless_published_routes(self):
        for page in SCOPE_NAVIGATION_PAGES:
            source = page.read_text(encoding="utf-8")
            for raw_target in MARKDOWN_LINK.findall(source):
                target = raw_target.split("#", 1)[0]
                if (
                    not target
                    or "://" in target
                    or target.startswith(("mailto:", "/"))
                    or target.endswith("/")
                ):
                    continue
                suffix = Path(target).suffix
                with self.subTest(page=page, target=target):
                    self.assertNotEqual(
                        suffix,
                        ".md",
                        msg=f"Published link retains .md suffix in {page}: {raw_target}",
                    )
                    if not suffix:
                        linked_source = (page.parent / (target + ".md")).resolve()
                        self.assertTrue(
                            linked_source.is_file(),
                            msg=f"Missing Markdown source from {page}: {target}",
                        )

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
