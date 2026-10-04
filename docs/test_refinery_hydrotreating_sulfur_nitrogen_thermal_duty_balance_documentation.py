from pathlib import Path
import unittest


DOC = Path(__file__).parent / "thermo" / "characterization" / (
    "refinery_hydrotreating_sulfur_nitrogen_thermal_duty_balance.md"
)


class CoupledThermalDutyDocumentationTest(unittest.TestCase):
    """Verify units, caller ownership, public evidence, and stop boundaries."""

    def test_thermal_duty_receipt_is_auditable_and_bounded(self):
        source = DOC.read_text(encoding="utf-8")
        text = " ".join(source.split())

        for phrase in (
            "RefineryHydrotreatingSulfurNitrogenThermalDutyBalance",
            "caller-supplied",
            "MJ/kg removed",
            "MWh per tonne liquid feed",
            "external heating - external cooling + reaction heat = sensible heating",
            "0.128177959 MW total reaction heat",
            "0.871822041 MW external heating",
            "not defaults, measurements, recommendations",
            "DOE/OEDI Big Hill",
            "does not calculate stream enthalpy",
        ):
            with self.subTest(phrase=phrase):
                self.assertIn(phrase, text)


if __name__ == "__main__":
    unittest.main()
