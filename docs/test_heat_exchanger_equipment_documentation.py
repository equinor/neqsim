import re
import unittest
from pathlib import Path


DOCS_DIR = Path(__file__).resolve().parent
REPOSITORY_ROOT = DOCS_DIR.parent
GUIDE = DOCS_DIR / "process" / "equipment" / "heat_exchangers.md"
HEATER = (
    REPOSITORY_ROOT
    / "src/main/java/neqsim/process/equipment/heatexchanger/Heater.java"
)
EXCHANGER = (
    REPOSITORY_ROOT
    / "src/main/java/neqsim/process/equipment/heatexchanger/HeatExchanger.java"
)
CONDENSER = (
    REPOSITORY_ROOT
    / "src/main/java/neqsim/process/equipment/distillation/Condenser.java"
)
SIZING_RESULT = (
    REPOSITORY_ROOT
    / "src/main/java/neqsim/process/mechanicaldesign/heatexchanger/HeatExchangerSizingResult.java"
)


class HeatExchangerEquipmentDocumentationContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.guide = GUIDE.read_text(encoding="utf-8")
        cls.heater = HEATER.read_text(encoding="utf-8")
        cls.exchanger = EXCHANGER.read_text(encoding="utf-8")
        cls.condenser = CONDENSER.read_text(encoding="utf-8")
        cls.sizing_result = SIZING_RESULT.read_text(encoding="utf-8")

    def test_front_matter_heading_and_fences_are_complete(self):
        self.assertTrue(self.guide.startswith("---\n"))
        self.assertIn("Source-anchored guide", self.guide.split("---", 2)[1])
        self.assertEqual(1, len(re.findall(r"^# ", self.guide, re.MULTILINE)))
        self.assertEqual(0, self.guide.count("```") % 2)

    def test_complete_quick_start_uses_current_two_stream_api(self):
        java_blocks = re.findall(
            r"```java\n(.*?)```", self.guide, flags=re.DOTALL
        )
        self.assertEqual(1, len(java_blocks))
        example = java_blocks[0]
        for contract in (
            "public final class HeatExchangerGuideExample",
            "public static void main(String[] args)",
            'new HeatExchanger("E-100", hot, cold)',
            "exchanger.setUAvalue(5000.0);",
            'exchanger.setGuessOutTemperature(70.0, "C");',
            "exchanger.getOutStream(0)",
            "exchanger.getOutStream(1)",
            "exchanger.getThermalEffectiveness()",
            "exchanger.getApproachTemperature()",
        ):
            with self.subTest(contract=contract):
                self.assertIn(contract, example)

    def test_heater_temperature_and_duty_contract_matches_source(self):
        self.assertIn(
            "setOutletTemperature(double temperature, String unit)",
            self.heater,
        )
        self.assertIn("public void setdT(double dT)", self.heater)
        self.assertIn("public void setEnergyInput(double energyInput)", self.heater)
        self.assertNotIn(
            "setOutTemperature(double temperature, String unit)", self.heater
        )
        for stale_call in (
            'heater.setOutTemperature(80.0, "C")',
            'cooler.setOutTemperature(30.0, "C")',
            'heater.setdT(50.0, "C")',
        ):
            self.assertNotIn(stale_call, self.guide)

    def test_exchanger_results_and_dynamic_contract_match_source(self):
        for source_contract in (
            "public double getThermalEffectiveness()",
            "public double getApproachTemperature()",
            "public void setDynamicModelEnabled(boolean enabled)",
            "public void runTransient(double dt, UUID id)",
            "public void autoSize(double safetyFactor)",
        ):
            with self.subTest(source_contract=source_contract):
                self.assertIn(source_contract, self.exchanger)
        self.assertNotIn("public double getLMTD()", self.exchanger)
        self.assertNotIn("public double getNTU()", self.exchanger)
        self.assertIn("does not expose `getLMTD()` or `getNTU()`", self.guide)
        self.assertNotIn("setThermalMass", self.guide)

    def test_condenser_and_sizing_boundaries_match_source(self):
        self.assertIn(
            "package neqsim.process.equipment.distillation;", self.condenser
        )
        self.assertIn("public Condenser(String name)", self.condenser)
        self.assertNotIn("setDewPointTemperature", self.condenser)
        self.assertNotIn("setSubCooling", self.condenser)
        self.assertIn("public double getRequiredArea()", self.sizing_result)
        self.assertNotIn("public double getArea()", self.sizing_result)
        self.assertIn("There is no `HeatExchangerSizingResult.getArea()`", self.guide)

    def test_display_math_is_compact_and_internal_targets_exist(self):
        blocks = re.findall(r"\$\$(.*?)\$\$", self.guide, flags=re.DOTALL)
        self.assertEqual(2, len(blocks))
        for block in blocks:
            with self.subTest(equation=block):
                self.assertFalse(block[0].isspace())
                self.assertFalse(block[-1].isspace())

        targets = (
            GUIDE.parent / "multistream_heat_exchanger.md",
            GUIDE.parent / "water_cooler_reboiler.md",
            GUIDE.parent.parent / "mechanical_design" / "thermal_hydraulic_design.md",
            GUIDE.parent.parent / "mechanical_design" / "two_phase_heat_transfer.md",
            DOCS_DIR / "wiki" / "heat_exchanger_mechanical_design.md",
            GUIDE.parent.parent / "DESIGN_FRAMEWORK.md",
        )
        for target in targets:
            with self.subTest(target=target):
                self.assertTrue(target.is_file())


if __name__ == "__main__":
    unittest.main()
