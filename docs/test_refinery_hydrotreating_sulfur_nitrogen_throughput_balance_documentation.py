from pathlib import Path
import unittest


DOC = Path(__file__).parent / "thermo" / "characterization" / (
    "refinery_hydrotreating_sulfur_nitrogen_throughput_balance.md"
)


class HydrotreatingSulfurNitrogenThroughputDocumentationTest(unittest.TestCase):
    """Verify units, provenance, conservation, and the engineering boundary."""

    def test_throughput_receipt_is_auditable_and_bounded(self):
        source = DOC.read_text(encoding="utf-8")
        text = " ".join(source.split())

        for phrase in (
            "RefineryHydrotreatingSulfurNitrogenThroughputBalance",
            "positive fresh liquid feed rate in kg/h",
            "basis scale in 1/h",
            "feed + H2 consumed = liquid product + H2S + NH3",
            "995.489447979 kg/h",
            "1.136701836 kg/h",
            "15 ppm sulfur",
            "10 ppm total nitrogen",
            "DOE SPR/OEDI Big Hill",
            "illustrative caller assumptions",
            "does not identify organic sulfur or nitrogen species",
            "heat duty",
            "compliance",
        ):
            with self.subTest(phrase=phrase):
                self.assertIn(phrase, text)


if __name__ == "__main__":
    unittest.main()
