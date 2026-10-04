from pathlib import Path
import unittest


DOC = Path(__file__).parent / "thermo" / "characterization" / (
    "refinery_hydrotreating_sulfur_nitrogen_fired_heater_utility_balance.md"
)


class CoupledFiredHeaterUtilityDocumentationTest(unittest.TestCase):
    """Verify units, caller ownership, public evidence, and stop boundaries."""

    def test_fired_heater_receipt_is_auditable_and_bounded(self):
        source = DOC.read_text(encoding="utf-8")
        text = " ".join(source.split())

        for phrase in (
            "RefineryHydrotreatingSulfurNitrogenFiredHeaterUtilityBalance",
            "caller-owned",
            "delivered heat divided by fuel LHV input",
            "fuel chemical power * furnace efficiency = delivered external heating duty",
            "73.848455268 kg/h fuel",
            "221.545365804 kg CO2e/h",
            "not defaults, measurements, recommendations",
            "DOE/OEDI Big Hill",
            "does not select a fuel",
            "calculate fuel composition or combustion stoichiometry",
        ):
            with self.subTest(phrase=phrase):
                self.assertIn(phrase, text)


if __name__ == "__main__":
    unittest.main()
