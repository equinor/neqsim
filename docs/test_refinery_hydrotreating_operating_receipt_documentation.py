from pathlib import Path
import unittest


DOC = Path(__file__).parent / "thermo" / "characterization" / (
    "refinery_hydrotreating_operating_receipt.md"
)


class HydrotreatingOperatingReceiptDocumentationTest(unittest.TestCase):
    """Verify units, scenario ownership, provenance, and stop boundary."""

    def test_operating_receipt_is_auditable_and_bounded(self):
        source = DOC.read_text(encoding="utf-8")
        text = " ".join(source.split())

        for phrase in (
            "RefineryHydrotreatingOperatingReceipt",
            "MWh per tonne of liquid feed",
            "5.49093756 kg CO2e/h",
            "2.196375024 currency units/h",
            "caller-owned scenario costs only",
            "DOE/OEDI Big Hill",
            "introduces no new parameter",
            "does not predict yield",
            "verified product carbon footprint",
        ):
            with self.subTest(phrase=phrase):
                self.assertIn(phrase, text)


if __name__ == "__main__":
    unittest.main()
