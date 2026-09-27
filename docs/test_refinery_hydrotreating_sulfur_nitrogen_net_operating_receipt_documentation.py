import pathlib
import unittest


class RefineryHydrotreatingSulfurNitrogenNetOperatingReceiptDocumentationTest(
    unittest.TestCase
):
    def setUp(self):
        self.root = pathlib.Path(__file__).resolve().parent
        self.guide = (
            self.root
            / "thermo"
            / "characterization"
            / "refinery_hydrotreating_sulfur_nitrogen_net_operating_receipt.md"
        )
        self.index = self.root / "thermo" / "characterization" / "README.md"

    def test_guide_records_contract_results_and_boundaries(self):
        text = " ".join(self.guide.read_text(encoding="utf-8").split())
        for phrase in (
            "common liquid-feed basis",
            "total emissions = hydrogen-supply emissions + net fired-heater fuel emissions",
            "1.018063442 MWh/t",
            "223.315219137 kg CO2e/t",
            "does not establish a lifecycle boundary",
        ):
            self.assertIn(phrase, text)

    def test_characterization_index_links_guide(self):
        text = self.index.read_text(encoding="utf-8")
        self.assertIn(
            "refinery_hydrotreating_sulfur_nitrogen_net_operating_receipt",
            text,
        )


if __name__ == "__main__":
    unittest.main()
