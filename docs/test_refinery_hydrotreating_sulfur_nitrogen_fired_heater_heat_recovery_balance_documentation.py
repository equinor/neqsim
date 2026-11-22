from pathlib import Path
import unittest


DOC = Path(__file__).parent / "thermo" / "characterization" / (
    "refinery_hydrotreating_sulfur_nitrogen_fired_heater_heat_recovery_balance.md"
)


class CoupledFiredHeaterHeatRecoveryDocumentationTest(unittest.TestCase):
    """Verify equations, units, provenance, caller ownership, and stop boundaries."""

    def test_heat_recovery_receipt_is_auditable_and_bounded(self):
        source = DOC.read_text(encoding="utf-8")
        text = " ".join(source.split())

        for phrase in (
            "RefineryHydrotreatingSulfurNitrogenFiredHeaterHeatRecoveryBalance",
            "caller-owned recovery fraction",
            "recovered heat [MW] = stack sensible loss * recovery fraction",
            "original furnace loss = recovered heat + post-recovery furnace loss",
            "0.054679523 MW recovered heat",
            "0.099171425 MW post-recovery furnace loss",
            "not a measured or recommended recovery efficiency",
            "U.S. Department of Energy process-heating guidance",
            "does not claim avoided fuel",
            "does not size heat-recovery equipment",
            "does not establish dew-point or corrosion limits",
        ):
            with self.subTest(phrase=phrase):
                self.assertIn(phrase, text)


if __name__ == "__main__":
    unittest.main()
