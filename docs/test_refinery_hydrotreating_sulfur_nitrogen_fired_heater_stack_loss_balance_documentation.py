from pathlib import Path
import unittest


DOC = Path(__file__).parent / "thermo" / "characterization" / (
    "refinery_hydrotreating_sulfur_nitrogen_fired_heater_stack_loss_balance.md"
)


class CoupledFiredHeaterStackLossDocumentationTest(unittest.TestCase):
    """Verify equations, units, provenance, caller ownership, and stop boundaries."""

    def test_stack_loss_receipt_is_auditable_and_bounded(self):
        source = DOC.read_text(encoding="utf-8")
        text = " ".join(source.split())

        for phrase in (
            "RefineryHydrotreatingSulfurNitrogenFiredHeaterStackLossBalance",
            "caller-owned stack temperature, reference temperature",
            "stack sensible loss [MW] = wet flue gas * mean Cp",
            "total furnace loss = stack sensible loss + residual furnace loss",
            "0.091132539 MW stack sensible loss",
            "8.885145634% of fuel chemical power",
            "not defaults, measurements, recommendations",
            "NIST Chemistry WebBook",
            "U.S. Department of Energy process-heating guidance",
            "does not embed species coefficients",
            "fails closed if the caller's sensible-loss scenario exceeds",
            "does not predict stack temperature",
        ):
            with self.subTest(phrase=phrase):
                self.assertIn(phrase, text)


if __name__ == "__main__":
    unittest.main()
