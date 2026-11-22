from pathlib import Path
import unittest


DOC = Path(__file__).parent / "thermo" / "characterization" / (
    "refinery_hydrotreating_sulfur_nitrogen_balance.md"
)


class HydrotreatingSulfurNitrogenDocumentationTest(unittest.TestCase):
    """Verify coupled basis, units, provenance, and the scientific stop boundary."""

    def test_coupled_balance_is_auditable_and_bounded(self):
        source = DOC.read_text(encoding="utf-8")
        text = " ".join(source.split())

        for phrase in (
            "RefineryHydrotreatingSulfurNitrogenBalance",
            "same calculated liquid product mass",
            "0.40867518 mass%",
            "0.1095129 mass%",
            "995.489447979 kg",
            "1.136701836 kg",
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
