from pathlib import Path
import unittest


DOC = Path(__file__).parent / "thermo" / "characterization" / (
    "refinery_hydrotreating_hydrogen_emissions_balance.md"
)


class HydrotreatingHydrogenEmissionsBalanceDocumentationTest(unittest.TestCase):
    """Verify units, scenario ownership, provenance, and stop boundary."""

    def test_emissions_receipt_is_auditable_and_bounded(self):
        source = DOC.read_text(encoding="utf-8")
        text = " ".join(source.split())

        for phrase in (
            "RefineryHydrotreatingHydrogenEmissionsBalance",
            "kg CO2e per kg fresh H2",
            "caller-owned currency units per tonne CO2e",
            "5.49093756 kg CO2e/h",
            "0.549093756 currency units per tonne",
            "DOE/OEDI Big Hill",
            "introduces no fitted parameter",
            "does not select a hydrogen-production pathway",
            "verified product carbon footprint",
        ):
            with self.subTest(phrase=phrase):
                self.assertIn(phrase, text)


if __name__ == "__main__":
    unittest.main()
