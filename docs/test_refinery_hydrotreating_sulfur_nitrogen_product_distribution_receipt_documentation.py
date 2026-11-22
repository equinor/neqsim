from pathlib import Path
import unittest


DOC = Path(__file__).parent / "thermo" / "characterization" / (
    "refinery_hydrotreating_sulfur_nitrogen_product_distribution_receipt.md"
)


class CoupledProductDistributionDocumentationTest(unittest.TestCase):
    """Verify units, public evidence, closures, and engineering boundary."""

    def test_distribution_receipt_is_auditable_and_bounded(self):
        source = DOC.read_text(encoding="utf-8")
        text = " ".join(source.split())

        for phrase in (
            "RefineryHydrotreatingSulfurNitrogenProductDistributionReceipt",
            "Internal recycle is excluded",
            "kg/h and kg per tonne liquid feed",
            "total external products = liquid product + export gas",
            "export gas = H2 + H2S + NH3 + non-H2 makeup gas",
            "995.489447979 kg/h liquid product",
            "7.612023933 kg/h export gas",
            "DOE/OEDI Big Hill",
            "introduces no fitted parameter",
            "does not predict hydrocarbon yield",
        ):
            with self.subTest(phrase=phrase):
                self.assertIn(phrase, text)


if __name__ == "__main__":
    unittest.main()
