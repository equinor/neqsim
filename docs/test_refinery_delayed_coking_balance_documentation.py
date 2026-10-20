import re
import unittest
from pathlib import Path


DOC_PATH = (
    Path(__file__).parent
    / "thermo"
    / "characterization"
    / "refinery_delayed_coking_balance.md"
)


class RefineryDelayedCokingBalanceDocumentationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.text = DOC_PATH.read_text(encoding="utf-8")

    def test_front_matter_and_java_example_are_present(self):
        self.assertTrue(self.text.startswith("---\n"))
        self.assertIn("title: Delayed-coking yield and utility screening balances", self.text)
        self.assertIn("```java", self.text)
        self.assertIn("RefineryDelayedCokingYieldBalance.calculate", self.text)
        self.assertIn("RefineryDelayedCokingUtilityBalance.calculate", self.text)

    def test_acceptance_case_and_provenance_are_explicit(self):
        for value in (
            "700 kg/h converted feed",
            "140 kg/h coke",
            "0.555555556 MW useful process duty",
            "52.287581699 kg/h fuel",
        ):
            self.assertIn(value, self.text)
        self.assertRegex(self.text, re.compile(r"U\.S\. EPA", re.IGNORECASE))
        self.assertRegex(self.text, re.compile(r"Department of Energy", re.IGNORECASE))
        self.assertIn("No proprietary assay", self.text)

    def test_capability_limitations_are_not_implicit(self):
        for limitation in (
            "do not predict conversion",
            "drum cycle",
            "emissions",
            "not a furnace design",
            "not evidence that a delayed coker operating point is feasible or safe",
        ):
            self.assertIn(limitation, self.text)


if __name__ == "__main__":
    unittest.main()
