from pathlib import Path
import unittest


DOC = Path(__file__).parent / "thermo" / "characterization" / (
    "refinery_hydrotreating_sulfur_nitrogen_fired_heater_combustion_balance.md"
)


class CoupledFiredHeaterCombustionDocumentationTest(unittest.TestCase):
    """Verify equations, units, provenance, caller ownership, and stop boundaries."""

    def test_combustion_receipt_is_auditable_and_bounded(self):
        source = DOC.read_text(encoding="utf-8")
        text = " ".join(source.split())

        for phrase in (
            "RefineryHydrotreatingSulfurNitrogenFiredHeaterCombustionBalance",
            "caller supplies dry, ash-free CHSON fuel mass fractions",
            "O2 stoichiometric = nC + nH / 4 + nS - nO / 2",
            "fuel mass + dry-air mass = wet-flue-gas mass",
            "202.580357034 kg/h direct CO2",
            "3.006457785% dry oxygen",
            "not defaults, measurements, recommendations",
            "NIST Chemistry WebBook",
            "U.S. EPA AP-42 Section 1.4",
            "does not predict CO, NOx, unburned hydrocarbons",
            "not reconciled to direct stack CO2",
        ):
            with self.subTest(phrase=phrase):
                self.assertIn(phrase, text)


if __name__ == "__main__":
    unittest.main()
