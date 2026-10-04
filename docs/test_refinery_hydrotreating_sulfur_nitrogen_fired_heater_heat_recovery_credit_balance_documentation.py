import pathlib
import unittest


class RefineryHydrotreatingSulfurNitrogenFiredHeaterHeatRecoveryCreditBalanceDocumentationTest(
    unittest.TestCase
):
    def setUp(self):
        self.root = pathlib.Path(__file__).resolve().parent
        self.guide = (
            self.root
            / "thermo"
            / "characterization"
            / "refinery_hydrotreating_sulfur_nitrogen_fired_heater_heat_recovery_credit_balance.md"
        )
        self.index = self.root / "thermo" / "characterization" / "README.md"

    def test_guide_records_contract_and_boundaries(self):
        text = self.guide.read_text(encoding="utf-8")
        for phrase in (
            "caller-owned utilization",
            "original fuel power = avoided fuel power + net fuel power",
            "3.473757952 kg/h avoided fuel",
            "not defaults, measurements, recommendations",
            "does not size an exchanger or HRSG",
        ):
            self.assertIn(phrase, text)

    def test_characterization_index_links_guide(self):
        text = self.index.read_text(encoding="utf-8")
        self.assertIn(
            "refinery_hydrotreating_sulfur_nitrogen_fired_heater_heat_recovery_credit_balance",
            text,
        )


if __name__ == "__main__":
    unittest.main()
