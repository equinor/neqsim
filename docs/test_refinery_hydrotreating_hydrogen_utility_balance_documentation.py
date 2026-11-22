from pathlib import Path
import unittest


DOC = Path(__file__).parent / "thermo" / "characterization" / (
    "refinery_hydrotreating_hydrogen_utility_balance.md"
)


class HydrotreatingHydrogenUtilityBalanceDocumentationTest(unittest.TestCase):
    """Verify units, public evidence, scenario ownership, and stop boundary."""

    def test_utility_receipt_is_auditable_and_bounded(self):
        source = DOC.read_text(encoding="utf-8")
        text = " ".join(source.split())

        for phrase in (
            "RefineryHydrotreatingHydrogenUtilityBalance",
            "hydrogen lower heating value in MJ/kg",
            "caller-owned currency units per kg",
            "fresh H2 = consumed H2 + exported H2",
            "0.549093756 kg/h fresh H2",
            "0.018303125 MW",
            "1.647281268 currency units per tonne",
            "DOE/OEDI Big Hill",
            "introduces no fitted parameter",
            "does not predict reaction enthalpy",
            "hydrogen-production emissions",
        ):
            with self.subTest(phrase=phrase):
                self.assertIn(phrase, text)


if __name__ == "__main__":
    unittest.main()
