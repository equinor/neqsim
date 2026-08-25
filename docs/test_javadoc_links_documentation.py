"""Regression contracts for the canonical current NeqSim JavaDoc deployment."""

from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
LEGACY_PREFIX = "https://equinor.github.io/neqsimhome/javadoc/site/apidocs/"
CANONICAL_PREFIX = "https://equinor.github.io/neqsim/javadoc/"

EXPECTED_LINK_COUNTS = {
    "docs/cookbook/index.md": {
        CANONICAL_PREFIX + "index.html": 1,
    },
    "docs/tutorials/learning-paths.md": {
        CANONICAL_PREFIX + "index.html": 1,
        CANONICAL_PREFIX + "neqsim/thermo/system/SystemInterface.html": 2,
        CANONICAL_PREFIX
        + "neqsim/thermodynamicoperations/ThermodynamicOperations.html": 1,
        CANONICAL_PREFIX
        + "neqsim/process/processmodel/ProcessSystem.html": 1,
        CANONICAL_PREFIX
        + "neqsim/process/equipment/ProcessEquipmentInterface.html": 2,
    },
    "docs/quickstart/java-quickstart.md": {
        CANONICAL_PREFIX + "index.html": 2,
    },
    "README.md": {
        CANONICAL_PREFIX + "index.html": 2,
    },
    "docs/java-getting-started.md": {
        CANONICAL_PREFIX + "index.html": 1,
    },
    "docs/quickstart/python-quickstart.md": {
        CANONICAL_PREFIX + "index.html": 1,
    },
    "docs/cookbook/process-recipes.md": {
        CANONICAL_PREFIX
        + "neqsim/process/processmodel/ProcessSystem.html": 1,
    },
    "docs/cookbook/unit-conversion-recipes.md": {
        CANONICAL_PREFIX + "index.html": 1,
    },
    "docs/cookbook/thermodynamics-recipes.md": {
        CANONICAL_PREFIX + "neqsim/thermo/system/SystemInterface.html": 1,
    },
    "docs/cookbook/pipeline-recipes.md": {
        CANONICAL_PREFIX + "index.html": 1,
    },
    "docs/process/equipment/manifold_design.md": {
        CANONICAL_PREFIX + "index.html": 1,
    },
    "docs/wiki/field_development_planning.md": {
        CANONICAL_PREFIX + "index.html": 1,
    },
    "docs/development/python_extension_patterns.md": {
        CANONICAL_PREFIX + "neqsim/util/NamedInterface.html": 1,
    },
}


class JavaDocLinksDocumentationTest(unittest.TestCase):
    def _markdown_files(self):
        files = [ROOT / "README.md"]
        files.extend(sorted((ROOT / "docs").rglob("*.md")))
        return files

    def test_legacy_generated_javadoc_deployment_is_not_linked(self):
        offenders = []
        for path in self._markdown_files():
            if LEGACY_PREFIX in path.read_text(encoding="utf-8"):
                offenders.append(path.relative_to(ROOT).as_posix())
        self.assertEqual([], offenders)

    def test_all_frozen_links_use_the_canonical_current_targets(self):
        observed = 0
        for relative_path, targets in EXPECTED_LINK_COUNTS.items():
            text = (ROOT / relative_path).read_text(encoding="utf-8")
            for target, expected_count in targets.items():
                with self.subTest(path=relative_path, target=target):
                    self.assertEqual(expected_count, text.count(target))
                observed += expected_count
        self.assertEqual(21, observed)

    def test_class_links_preserve_the_generated_package_path(self):
        class_targets = [
            target
            for targets in EXPECTED_LINK_COUNTS.values()
            for target in targets
            if target != CANONICAL_PREFIX + "index.html"
        ]
        self.assertTrue(class_targets)
        for target in class_targets:
            with self.subTest(target=target):
                self.assertTrue(target.startswith(CANONICAL_PREFIX + "neqsim/"))
                self.assertTrue(target.endswith(".html"))
                self.assertNotIn("/site/apidocs/", target)

    def test_documentation_landing_page_owns_the_same_javadoc_root(self):
        landing = (ROOT / "docs/index.md").read_text(encoding="utf-8")
        self.assertIn(CANONICAL_PREFIX + "index.html", landing)

    def test_frozen_scope_covers_every_migrated_page(self):
        self.assertEqual(13, len(EXPECTED_LINK_COUNTS))
        for relative_path in EXPECTED_LINK_COUNTS:
            with self.subTest(path=relative_path):
                self.assertTrue((ROOT / relative_path).is_file())


if __name__ == "__main__":
    unittest.main()
