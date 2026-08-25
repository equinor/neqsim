import re
import unittest
from pathlib import Path
from urllib.parse import unquote


ROOT = Path(__file__).resolve().parents[1]
INDEX_PATH = ROOT / "docs" / "REFERENCE_MANUAL_INDEX.md"


class ReferenceManualIndexDocumentationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.index = INDEX_PATH.read_text(encoding="utf-8")
        cls.body = cls.index.split("---", 2)[2]
        cls.targets = re.findall(r"\[[^\]]+\]\(([^)\s]+)\)", cls.index)

    def test_front_matter_is_not_duplicated_as_h1(self):
        self.assertNotRegex(self.body, r"(?m)^# ")
        self.assertIn("Curated source-level navigation", self.index)

    def test_scope_claim_is_truthful_and_not_count_bound(self):
        self.assertIn("This curated index groups the principal NeqSim guides", self.index)
        self.assertIn("not an inventory of every Markdown", self.index)
        self.assertNotIn("maps all 360+", self.index)
        self.assertNotRegex(self.index, r"Chapter \d+:[^\n]*\(NEW!\)")

    def test_every_internal_target_names_an_explicit_source_file(self):
        internal = []
        for target in self.targets:
            path_text = target.split("#", 1)[0].split("?", 1)[0]
            if not path_text or re.match(r"^[a-z][a-z0-9+.-]*:", path_text, re.IGNORECASE):
                continue
            internal.append(path_text)
            self.assertTrue(
                Path(unquote(path_text)).suffix,
                "Internal link must name an explicit source file: {}".format(target),
            )
        self.assertGreater(len(internal), 600)

    def test_every_internal_target_resolves_in_repository(self):
        failures = []
        for target in self.targets:
            path_text = target.split("#", 1)[0].split("?", 1)[0]
            if not path_text or re.match(r"^[a-z][a-z0-9+.-]*:", path_text, re.IGNORECASE):
                continue
            resolved = (INDEX_PATH.parent / unquote(path_text)).resolve()
            try:
                resolved.relative_to(ROOT.resolve())
            except ValueError:
                failures.append("{} escapes the repository".format(target))
                continue
            if not resolved.is_file():
                failures.append("{} -> {}".format(target, resolved.relative_to(ROOT)))
        self.assertEqual([], failures)

    def test_maintenance_claims_are_generated_not_date_or_count_bound(self):
        self.assertIn("not a hand-counted inventory", self.index)
        self.assertIn("reports the audited Markdown", self.index)
        self.assertNotIn("## Document Statistics", self.index)
        self.assertNotIn("**NEW**", self.index)
        self.assertNotIn("Last updated:", self.index)

    def test_appendices_are_in_alphabetical_navigation_order(self):
        appendices = re.findall(r"(?m)^### Appendix ([A-Z]):", self.index)
        self.assertEqual(list("ABCDEFGHI"), appendices)

    def test_source_path_labels_match_their_internal_targets(self):
        source_links = re.findall(r"\[([^\]]+)\]\(([^)\s]+)\)", self.index)
        checked = 0
        mismatches = []
        for label, target in source_links:
            if not label.startswith("docs/"):
                continue
            path_text = target.split("#", 1)[0].split("?", 1)[0]
            if re.match(r"^[a-z][a-z0-9+.-]*:", path_text, re.IGNORECASE):
                continue
            checked += 1
            expected = label[len("docs/") :]
            actual = unquote(path_text)
            if expected != actual:
                mismatches.append("{} -> {}".format(label, target))
        self.assertGreater(checked, 600)
        self.assertEqual([], mismatches)

    def test_fragment_and_landing_targets_are_source_safe(self):
        self.assertIn(
            "(process/plant-data-tagreader.md#step-5-neqsimapi-cloud-rest)",
            self.index,
        )
        for target in (
            "(thermo/README.md)",
            "(process/README.md)",
            "(risk/README.md)",
            "(quickstart/index.md)",
            "(tutorials/index.md)",
        ):
            self.assertIn(target, self.index)


if __name__ == "__main__":
    unittest.main()
