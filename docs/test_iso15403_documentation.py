"""Hermetic contracts for the ISO 15403 CNG-quality documentation."""

import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DOC = ROOT / "docs" / "standards" / "iso15403_cng_quality.md"
SOURCE = (
    ROOT
    / "src"
    / "main"
    / "java"
    / "neqsim"
    / "standards"
    / "gasquality"
    / "Standard_ISO15403.java"
)
JAVA_TEST = (
    ROOT
    / "src"
    / "test"
    / "java"
    / "neqsim"
    / "standards"
    / "gasquality"
    / "StandardISO15403DocumentationTest.java"
)
STANDARDS_INDEX = ROOT / "docs" / "standards" / "README.md"
REFERENCE_INDEX = ROOT / "docs" / "REFERENCE_MANUAL_INDEX.md"


class Iso15403DocumentationContractTest(unittest.TestCase):
    """Guard source accuracy, structure, discoverability, and example coverage."""

    @classmethod
    def setUpClass(cls):
        cls.doc = DOC.read_text(encoding="utf-8")
        cls.source = SOURCE.read_text(encoding="utf-8")
        cls.java_test = JAVA_TEST.read_text(encoding="utf-8")

    def test_jekyll_structure_links_math_and_fences(self):
        self.assertRegex(
            self.doc,
            r'^---\ntitle: "[^"]+"\ndescription: "[^"]+"\n---\n',
        )
        self.assertNotRegex(self.doc, r"(?m)^# ")
        self.assertEqual(2, self.doc.count("```"))
        self.assertEqual(1, self.doc.count("```java"))

        for target in re.findall(r"\[[^\]]+\]\(([^)#]+\.md)(?:#[^)]+)?\)", self.doc):
            resolved = (DOC.parent / target).resolve()
            self.assertTrue(resolved.is_file(), target)

        self.assertNotIn(r"\[", self.doc)
        self.assertNotIn(r"\(", self.doc)
        self.assertEqual(0, self.doc.count("$$") % 2)
        math_segments = self.doc.split("$$")[1::2]
        for segment in math_segments:
            self.assertTrue(segment)
            self.assertFalse(segment[0].isspace())
            self.assertFalse(segment[-1].isspace())

    def test_documented_contract_matches_current_source(self):
        source_patterns = (
            'getMolefraction("methane")',
            'getMolefraction("ethane")',
            'getMolefraction("propane")',
            'getMolefraction("n-butane")',
            'getMolefraction("i-butane")',
            'getMolefraction("CO2")',
            'getMolefraction("nitrogen")',
            "NM = 1.445 * MON - 103.42",
            'returnParameter.equals("MON")',
            'returnParameter.equals("NM")',
            "public boolean isOnSpec()",
            "return true;",
        )
        for pattern in source_patterns:
            self.assertIn(pattern, self.source)

        required_doc_patterns = (
            "MON = 137.78",
            "NM = 95.6721",
            "Hydrogen, C5+ hydrocarbons",
            "same one-mole composition basis",
            "public final class Iso15403Example",
            "public static void main(String[] args)",
            'base.getValue("MON")',
            'base.getValue("NM")',
            "isOnSpec()` | Always returns `true`",
            "does not perform a complete conformity assessment",
        )
        for pattern in required_doc_patterns:
            self.assertIn(pattern, self.doc)

    def test_stale_and_unsafe_patterns_are_rejected(self):
        rejected = (
            'getValue("MN")',
            "MN = 100: Pure methane",
            "MN = 0: Pure hydrogen",
            "CO2 and N2 improve methane number",
            "System.out",
            "System.err",
            "Minimum MN",
            "Typical MN",
        )
        for pattern in rejected:
            self.assertNotIn(pattern, self.doc)

    def test_focused_java_regression_exercises_every_documented_call(self):
        calls = (
            "new Standard_ISO15403(createCng(0.92, 0.01, 0.01))",
            "base.calculate()",
            'base.getValue("MON")',
            'base.getValue("NM")',
            'base.getUnit("MON")',
            "base.isOnSpec()",
            'standard.getValue("MN")',
        )
        for call in calls:
            self.assertIn(call, self.java_test)

    def test_page_is_discoverable_from_both_indexes(self):
        standards_index = STANDARDS_INDEX.read_text(encoding="utf-8")
        reference_index = REFERENCE_INDEX.read_text(encoding="utf-8")
        self.assertIn("iso15403_cng_quality", standards_index)
        self.assertIn("standards/iso15403_cng_quality", reference_index)


if __name__ == "__main__":
    unittest.main()
