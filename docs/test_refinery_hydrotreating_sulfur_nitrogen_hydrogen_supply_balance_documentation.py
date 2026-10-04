from pathlib import Path
import unittest


DOC = Path(__file__).parent / "thermo" / "characterization" / (
    "refinery_hydrotreating_sulfur_nitrogen_hydrogen_supply_balance.md"
)


class CoupledHydrotreatingHydrogenSupplyDocumentationTest(unittest.TestCase):
    """Verify units, provenance, closure, assumptions, and stop boundary."""

    def test_coupled_gas_supply_receipt_is_auditable_and_bounded(self):
        source = DOC.read_text(encoding="utf-8")
        text = " ".join(source.split())

        for phrase in (
            "RefineryHydrotreatingSulfurNitrogenHydrogenSupplyBalance",
            "H2, H2S, NH3",
            "fH2 >= 1",
            "0 < yH2 <= 1",
            "liquid feed plus makeup gas",
            "4.337722954 kg makeup gas",
            "8.848274975 kg outlet gas",
            "DOE SPR/OEDI Big Hill",
            "illustrative caller assumptions",
            "does not predict phase equilibrium",
            "recycle/purge",
            "compliance",
        ):
            with self.subTest(phrase=phrase):
                self.assertIn(phrase, text)


if __name__ == "__main__":
    unittest.main()
