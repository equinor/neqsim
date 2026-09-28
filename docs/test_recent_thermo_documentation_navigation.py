from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
THERMO_INDEX = ROOT / "docs" / "thermo" / "index.md"
EXPECTED_LINKS = {
    "Component database and parameter provenance": "./component_database_guide.md",
    "Pure-water Henry database": "./henry_water_database.md",
    "Petroleum and refinery characterization": "./characterization/README.md",
}


class RecentThermoDocumentationNavigationTest(unittest.TestCase):
    """Protect discoverability of recently updated thermodynamics documentation."""

    def test_index_has_required_front_matter_and_readme_include(self):
        text = THERMO_INDEX.read_text(encoding="utf-8")

        self.assertTrue(text.startswith("---\n"))
        front_matter = text.split("---", 2)[1]
        self.assertIn('title: "Thermodynamic Documentation Set"', front_matter)
        self.assertIn("description:", front_matter)
        self.assertEqual(text.count("{% include_relative README.md %}"), 1)

    def test_recent_documentation_hubs_are_linked_once_and_resolve(self):
        text = THERMO_INDEX.read_text(encoding="utf-8")

        for label, relative_target in EXPECTED_LINKS.items():
            marker = "[{}]({})".format(label, relative_target)
            self.assertEqual(text.count(marker), 1, marker)
            target = (THERMO_INDEX.parent / relative_target).resolve()
            self.assertTrue(target.is_file(), str(target))


if __name__ == "__main__":
    unittest.main()
