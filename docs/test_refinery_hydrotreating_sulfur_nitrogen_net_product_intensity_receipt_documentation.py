import pathlib
import unittest


class RefineryHydrotreatingSulfurNitrogenNetProductIntensityReceiptDocumentationTest(
    unittest.TestCase
):
    def setUp(self):
        self.root = pathlib.Path(__file__).resolve().parent
        self.guide = (
            self.root
            / "thermo"
            / "characterization"
            / "refinery_hydrotreating_sulfur_nitrogen_net_product_intensity_receipt.md"
        )
        self.index = self.root / "thermo" / "characterization" / "README.md"

    def test_guide_records_contract_results_and_boundaries(self):
        text = " ".join(self.guide.read_text(encoding="utf-8").split())
        for phrase in (
            "external liquid-product reporting basis",
            "1.022676277 MWh/t liquid product",
            "224.327058002 kg CO2e/t liquid product",
            "do not allocate energy, emissions, or cost",
            "do not represent a product carbon footprint",
        ):
            self.assertIn(phrase, text)

    def test_characterization_index_links_guide(self):
        text = self.index.read_text(encoding="utf-8")
        self.assertIn(
            "refinery_hydrotreating_sulfur_nitrogen_net_product_intensity_receipt",
            text,
        )


if __name__ == "__main__":
    unittest.main()
