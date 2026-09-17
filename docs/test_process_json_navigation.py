"""Contracts for discovering distinct JSON process workflows from primary landing pages."""

import unittest
from pathlib import Path


DOCS = Path(__file__).resolve().parent
SITE_HOME = DOCS / "index.md"
DOCS_HUB = DOCS / "README.md"
PROCESS_HUB = DOCS / "process" / "README.md"
BUILDER_GUIDE = DOCS / "process" / "json_process_models_and_systems.md"
EXPORT_GUIDE = DOCS / "process" / "process_json_export_and_e300_fluids.md"


class ProcessJsonNavigationDocumentationTest(unittest.TestCase):
    """Protect the builder/export distinction and stream-wiring boundary."""

    @classmethod
    def setUpClass(cls):
        cls.site_home = SITE_HOME.read_text(encoding="utf-8")
        cls.docs_hub = DOCS_HUB.read_text(encoding="utf-8")
        cls.process_hub = PROCESS_HUB.read_text(encoding="utf-8")
        cls.builder_guide = BUILDER_GUIDE.read_text(encoding="utf-8")
        cls.export_guide = EXPORT_GUIDE.read_text(encoding="utf-8")

    def test_primary_landings_offer_distinct_builder_and_export_routes(self):
        routes = {
            "site home": (
                self.site_home,
                'href="process/README.html"',
                'href="process/json_process_models_and_systems.html"',
                'href="process/process_json_export_and_e300_fluids.html"',
                "Build Processes from JSON",
                "Export Processes to JSON",
            ),
            "documentation hub": (
                self.docs_hub,
                "[process/](process/)",
                (
                    "[process/json_process_models_and_systems.md]"
                    "(process/json_process_models_and_systems.md)"
                ),
                (
                    "[process/process_json_export_and_e300_fluids.md]"
                    "(process/process_json_export_and_e300_fluids.md)"
                ),
                "JSON builder input",
                "JSON model export",
            ),
            "process hub": (
                self.process_hub,
                "[processmodel/](processmodel/)",
                "[json_process_models_and_systems.md](json_process_models_and_systems)",
                (
                    "[process_json_export_and_e300_fluids.md]"
                    "(process_json_export_and_e300_fluids)"
                ),
                "Process JSON builder input",
                "Process JSON model export",
            ),
        }

        for landing, required in routes.items():
            with self.subTest(landing=landing):
                source = required[0]
                for marker in required[1:]:
                    self.assertEqual(source.count(marker), 1)

    def test_guides_exist_and_have_searchable_front_matter(self):
        for guide, source in (
            (BUILDER_GUIDE, self.builder_guide),
            (EXPORT_GUIDE, self.export_guide),
        ):
            with self.subTest(guide=guide):
                self.assertTrue(guide.is_file())
                self.assertTrue(source.startswith("---\n"))
                front_matter = source.split("---\n", 2)[1]
                self.assertIn("\ntitle:", "\n" + front_matter)
                self.assertIn("\ndescription:", "\n" + front_matter)

    def test_builder_route_preserves_stream_wiring_boundary(self):
        self.assertIn(
            "A `Stream` without `inlet` or `inlets` is a standalone source.",
            self.builder_guide,
        )
        self.assertIn(
            "Use `inlet` or `inlets` for physical process wiring.",
            self.builder_guide,
        )
        self.assertIn(
            "it does not replace those wiring fields.",
            self.builder_guide,
        )


if __name__ == "__main__":
    unittest.main()
