from pathlib import Path
import unittest


DOC = Path(__file__).parent / "thermo" / "characterization" / (
    "refinery_hydrotreating_throughput_balance.md"
)


class HydrotreatingThroughputBalanceDocumentationTest(unittest.TestCase):
    """Verify units, public evidence, receipts, and engineering boundary."""

    def test_rate_receipt_is_auditable_and_bounded(self):
        source = DOC.read_text(encoding="utf-8")
        text = " ".join(source.split())

        for phrase in (
            "RefineryHydrotreatingThroughputBalance",
            "kg/h and kmol/h",
            "feed + fresh makeup = liquid product + export gas",
            "4.071809037 kg/h sulfur",
            "1.396916654 kg/h fresh makeup gas",
            "1.440246511 kg/h internal recycle gas",
            "5.212737927 kg/h gas",
            "0.272384148 kmol/h",
            "DOE/OEDI Big Hill",
            "introduces no fitted parameter",
            "does not predict reaction kinetics",
        ):
            with self.subTest(phrase=phrase):
                self.assertIn(phrase, text)


if __name__ == "__main__":
    unittest.main()
