"""Regression contracts for the canonical current NeqSim JavaDoc deployment."""

from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
LEGACY_PREFIX = "https://equinor.github.io/neqsimhome/javadoc/site/apidocs/"
CANONICAL_PREFIX = "https://equinor.github.io/neqsim/javadoc/"
RETIRED_PREFIXES = (
    LEGACY_PREFIX,
    "https://equinor.github.io/neqsim/apidocs/",
    "https://htmlpreview.github.io/?https://github.com/equinor/neqsimhome/",
)
DEVELOPER_GUIDE = ROOT / "docs/development/GETTING_STARTED_DEVELOPER.md"
DEVTOOLS_PYPROJECT = ROOT / "devtools/pyproject.toml"
RELEASE_NOTES = ROOT / "docs/development/release-notes-3.27.0.md"

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
    "docs/development/GETTING_STARTED_DEVELOPER.md": {
        CANONICAL_PREFIX + "index.html": 1,
    },
    "docs/wiki/README.md": {
        CANONICAL_PREFIX + "index.html": 1,
    },
}


class JavaDocLinksDocumentationTest(unittest.TestCase):
    def _markdown_files(self):
        files = [ROOT / "README.md"]
        files.extend(sorted((ROOT / "docs").rglob("*.md")))
        return files

    def test_retired_generated_javadoc_deployments_are_not_linked(self):
        offenders = []
        for path in self._markdown_files():
            text = path.read_text(encoding="utf-8")
            for prefix in RETIRED_PREFIXES:
                if prefix in text:
                    offenders.append((path.relative_to(ROOT).as_posix(), prefix))
        self.assertEqual([], offenders)

    def test_all_frozen_links_use_the_canonical_current_targets(self):
        observed = 0
        for relative_path, targets in EXPECTED_LINK_COUNTS.items():
            text = (ROOT / relative_path).read_text(encoding="utf-8")
            for target, expected_count in targets.items():
                with self.subTest(path=relative_path, target=target):
                    self.assertEqual(expected_count, text.count(target))
                observed += expected_count
        self.assertEqual(23, observed)

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

    def test_developer_entrypoint_matches_current_runtime_contracts(self):
        guide = DEVELOPER_GUIDE.read_text(encoding="utf-8")
        pyproject = DEVTOOLS_PYPROJECT.read_text(encoding="utf-8")
        release_notes = RELEASE_NOTES.read_text(encoding="utf-8")

        self.assertIn("Java 17+ (JDK, not just JRE)", guide)
        self.assertIn("Python 3.11+ (for repository tooling", guide)
        self.assertIn("NeqSim source remains Java 8 compatible", guide)
        self.assertNotIn("Python 3.8+", guide)
        self.assertNotIn("Java 8+ (JDK, not just JRE)", guide)
        self.assertNotIn("\n# Getting Started as a NeqSim Developer\n", guide)
        self.assertIn('requires-python = ">=3.11"', pyproject)
        self.assertRegex(
            release_notes,
            r"standard core artifact\s+requires \*\*Java 17\+\*\*",
        )

    def test_frozen_scope_covers_every_migrated_page(self):
        self.assertEqual(15, len(EXPECTED_LINK_COUNTS))
        for relative_path in EXPECTED_LINK_COUNTS:
            with self.subTest(path=relative_path):
                self.assertTrue((ROOT / relative_path).is_file())


if __name__ == "__main__":
    unittest.main()
