from pathlib import Path
import unittest


DOC = Path(__file__).parent / "thermo" / "characterization" / (
    "refinery_hydrotreating_nitrogen_balance.md"
)


class HydrotreatingNitrogenBalanceDocumentationTest(unittest.TestCase):
    """Verify units, provenance, assumptions, and the scientific stop boundary."""

    def test_nitrogen_balance_is_auditable_and_bounded(self):
        source = DOC.read_text(encoding="utf-8")
        text = " ".join(source.split())

        for phrase in (
            "RefineryHydrotreatingNitrogenBalance",
            "0.1095129 mass%",
            "10 ppm total nitrogen",
            "1.319399583 kg",
            "illustrative caller assumptions",
            "DOE SPR/OEDI Big Hill",
            "NIST Chemistry WebBook ammonia entry",
            "does not identify organic nitrogen species",
            "heat duty",
            "regulatory compliance",
        ):
            with self.subTest(phrase=phrase):
                self.assertIn(phrase, text)


if __name__ == "__main__":
    unittest.main()
