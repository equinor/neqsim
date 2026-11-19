"""Protect navigation and qualification boundaries for the CO2-ion Furst ablation note."""

import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DOCS = ROOT / "docs"
GUIDE = DOCS / "thermo" / "co2_divalent_furst_ablation.md"
THERMO_README = DOCS / "thermo" / "README.md"
REFERENCE_INDEX = DOCS / "REFERENCE_MANUAL_INDEX.md"
ROUTES = (
    (THERMO_README, "co2_divalent_furst_ablation"),
    (REFERENCE_INDEX, "thermo/co2_divalent_furst_ablation.md"),
)


def resolve_markdown_target(source: Path, route: str) -> Path:
    """Resolve extensionless Jekyll links as Markdown source paths."""
    target = source.parent / route
    if target.is_file():
        return target.resolve()
    return target.with_suffix(".md").resolve()


class Co2FurstNavigationTest(unittest.TestCase):
    """Keep the diagnostic reachable without overstating its qualification."""

    def test_navigation_routes_are_unique_and_resolve(self) -> None:
        for source, route in ROUTES:
            with self.subTest(source=source, route=route):
                text = source.read_text(encoding="utf-8")
                self.assertEqual(text.count(f"]({route})"), 1)
                self.assertEqual(resolve_markdown_target(source, route), GUIDE.resolve())
                self.assertTrue(GUIDE.is_file())

    def test_guide_preserves_front_matter_without_duplicate_h1(self) -> None:
        text = GUIDE.read_text(encoding="utf-8")
        self.assertTrue(text.startswith("---\n"))
        self.assertRegex(text, r"(?m)^title: .+")
        self.assertRegex(text, r"(?m)^description: .+")
        body = text.split("---", 2)[2]
        self.assertIsNone(re.search(r"(?m)^# ", body))

    def test_navigation_preserves_diagnostic_hold_boundary(self) -> None:
        for source, _ in ROUTES:
            with self.subTest(source=source):
                text = source.read_text(encoding="utf-8").lower()
                self.assertIn("diagnostic", text)
                self.assertIn("hold", text)
                self.assertIn("production parameter qualification", text)

        guide = GUIDE.read_text(encoding="utf-8").lower()
        self.assertIn("does not qualify a production parameter change", guide)
        self.assertNotIn("qualified for production", guide)


if __name__ == "__main__":
    unittest.main()
