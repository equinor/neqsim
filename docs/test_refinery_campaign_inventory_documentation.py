import unittest
from pathlib import Path


DOC_PATH = (
    Path(__file__).parent
    / "thermo"
    / "characterization"
    / "refinery_assay.md"
)


class RefineryCampaignInventoryDocumentationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.text = DOC_PATH.read_text(encoding="utf-8")

    def test_inventory_names_merged_reference_and_conversion_capabilities(self):
        for name in (
            "SarirAtmosphericFractionationCase",
            "DoeBigHillVacuumFractionationCase",
            "RefineryHydrotreating*Balance",
            "RefineryCatalyticReformingStoichiometricBalance",
            "RefineryHydrocrackingYieldBalance",
            "RefineryFccYieldBalance",
            "RefineryDelayedCokingYieldBalance",
        ):
            self.assertIn(name, self.text)

    def test_stale_campaign_claims_do_not_return(self):
        for stale_claim in (
            "No campaign benchmark yet",
            "No campaign-qualified refinery conversion models",
            "Open after atmospheric case",
        ):
            self.assertNotIn(stale_claim, self.text)

    def test_next_step_preserves_one_public_case_identity(self):
        self.assertIn("one assay identity", self.text)
        self.assertIn(
            "Do not mix Sarir operating or plant-yield evidence into the DOE case",
            self.text,
        )
        self.assertIn("ASTM D1160 qualification", self.text)


if __name__ == "__main__":
    unittest.main()
