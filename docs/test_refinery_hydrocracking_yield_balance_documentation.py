"""Documentation contract for the hydrocracking yield and hydrogen-supply screen."""

from pathlib import Path
import unittest


DOCUMENT = (
    Path(__file__).parent
    / "thermo"
    / "characterization"
    / "refinery_hydrocracking_yield_balance.md"
)


class RefineryHydrocrackingYieldBalanceDocumentationTest(unittest.TestCase):
    def test_screen_documents_balance_provenance_and_boundary(self):
        text = DOCUMENT.read_text(encoding="utf-8")
        for phrase in (
            "RefineryHydrocrackingYieldBalance",
            "RefineryHydrocrackingHydrogenSupplyBalance",
            "82 kg/h light gas",
            "246 kg/h naphtha",
            "492 kg/h middle distillate",
            "15.326864 kg/h imported hydrogen",
            "69.346272% of demand",
            "not yield predictors",
            "Factors Affecting U.S. Petroleum Refining",
            "10.3303/CET1761110",
            "does not copy its fitted parameters",
            "heteroatom byproducts and water are outside",
        ):
            self.assertIn(phrase, text)


if __name__ == "__main__":
    unittest.main()
