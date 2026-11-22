import re
import unittest
from pathlib import Path


DOC_PATH = (
    Path(__file__).parent
    / "thermo"
    / "characterization"
    / "refinery_fcc_yield_regenerator_balance.md"
)


class RefineryFccYieldRegeneratorDocumentationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.text = DOC_PATH.read_text(encoding="utf-8")

    def test_front_matter_and_java_example_are_present(self):
        self.assertTrue(self.text.startswith("---\n"))
        self.assertIn("title: FCC yield and coke-regenerator screening balance", self.text)
        self.assertIn("```java", self.text)
        self.assertIn("RefineryFccYieldBalance.calculate", self.text)
        self.assertIn("RefineryFccCokeRegeneratorBalance.calculate", self.text)

    def test_acceptance_case_and_provenance_are_explicit(self):
        for value in (
            "750 kg/h converted feed",
            "75 kg/h coke",
            "5.125301807 kmol/h stoichiometric oxygen",
            "841.132994195 kg/h represented dry flue gas",
        ):
            self.assertIn(value, self.text)
        self.assertRegex(self.text, re.compile(r"Department of Energy", re.IGNORECASE))
        self.assertIn("No proprietary assay", self.text)

    def test_capability_limitations_are_not_implicit(self):
        for limitation in (
            "do not predict conversion",
            "regenerator temperature",
            "NOx",
            "emissions compliance",
            "not evidence that an FCC operating point is feasible or safe",
        ):
            self.assertIn(limitation, self.text)


if __name__ == "__main__":
    unittest.main()
