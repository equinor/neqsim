from pathlib import Path
import unittest


DOC = Path(__file__).parent / "thermo" / "characterization" / (
    "refinery_hydrotreating_sulfur_nitrogen_hydrogen_recycle_throughput_balance.md"
)


class CoupledRecycleThroughputDocumentationTest(unittest.TestCase):
    """Verify units, public evidence, receipts, and engineering boundary."""

    def test_rate_receipt_is_auditable_and_bounded(self):
        source = DOC.read_text(encoding="utf-8")
        text = " ".join(source.split())

        for phrase in (
            "RefineryHydrotreatingSulfurNitrogenHydrogenRecycleThroughputBalance",
            "kg/h and kmol/h",
            "feed + fresh makeup = liquid product + export gas",
            "3.101471912 kg/h fresh makeup gas",
            "7.612023933 kg/h gas",
            "0.604754608 kmol/h fresh H2",
            "0.241056033 kmol/h recycle H2",
            "DOE/OEDI Big Hill",
            "introduces no fitted parameter",
            "does not predict reaction kinetics",
        ):
            with self.subTest(phrase=phrase):
                self.assertIn(phrase, text)


if __name__ == "__main__":
    unittest.main()
