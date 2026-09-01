"""Regression contracts for the source-verified separator equipment guide."""

from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]
GUIDE = ROOT / "docs" / "process" / "equipment" / "separators.md"
SEPARATOR_SOURCE = (
    ROOT
    / "src/main/java/neqsim/process/equipment/separator/Separator.java"
)
THREE_PHASE_SOURCE = (
    ROOT
    / "src/main/java/neqsim/process/equipment/separator/ThreePhaseSeparator.java"
)
DESIGN_SOURCE = (
    ROOT
    / (
        "src/main/java/neqsim/process/mechanicaldesign/separator/"
        "SeparatorMechanicalDesign.java"
    )
)


class SeparatorCoreDocumentationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.guide = GUIDE.read_text(encoding="utf-8")
        cls.guide_prose = " ".join(cls.guide.split()).lower()
        cls.separator_source = SEPARATOR_SOURCE.read_text(encoding="utf-8")
        cls.three_phase_source = THREE_PHASE_SOURCE.read_text(encoding="utf-8")
        cls.design_source = DESIGN_SOURCE.read_text(encoding="utf-8")
        cls.normalized_design_source = " ".join(cls.design_source.split())

    def test_front_matter_has_no_duplicate_body_h1(self):
        self.assertTrue(self.guide.startswith("---\ntitle:"))
        body = self.guide.split("---", 2)[2]
        body_without_fenced_code = re.sub(
            r"```.*?```", "", body, flags=re.DOTALL
        )
        self.assertNotRegex(body_without_fenced_code, r"(?m)^# ")

    def test_outlet_examples_use_declared_stream_interface_types(self):
        for source_contract in (
            "public StreamInterface getGasOutStream()",
            "public StreamInterface getLiquidOutStream()",
        ):
            with self.subTest(source_contract=source_contract):
                self.assertIn(source_contract, self.separator_source)
        for source_contract in (
            "public StreamInterface getOilOutStream()",
            "public StreamInterface getWaterOutStream()",
        ):
            with self.subTest(source_contract=source_contract):
                self.assertIn(source_contract, self.three_phase_source)
        for guide_contract in (
            "StreamInterface gasOut = separator.getGasOutStream();",
            "StreamInterface oilOut = separator.getOilOutStream();",
            "StreamInterface waterOut = separator.getWaterOutStream();",
            "StreamInterface dryGas = scrubber.getGasOutStream();",
            "StreamInterface condensate = scrubber.getLiquidOutStream();",
        ):
            with self.subTest(guide_contract=guide_contract):
                self.assertIn(guide_contract, self.guide)

    def test_water_fraction_has_an_explicit_mass_basis(self):
        self.assertNotIn("getWaterCut()", self.guide)
        self.assertIn("producedLiquidWaterMassFraction", self.guide)
        self.assertIn('waterOut.getFlowRate("kg/hr")', self.guide)
        self.assertIn('oilOut.getFlowRate("kg/hr")', self.guide)
        self.assertIn("producedLiquidMassFlow > 0.0", self.guide)

    def test_multiphase_check_is_not_documented_as_a_prerequisite(self):
        for source_contract in (
            "if (!thermoSystem2.doMultiPhaseCheck())",
            "thermoSystem2.setMultiPhaseCheck(true)",
            "thermoSystem2.setMultiPhaseCheck(false)",
        ):
            with self.subTest(source_contract=source_contract):
                self.assertIn(source_contract, self.three_phase_source)
        self.assertIn("not a separator prerequisite", self.guide_prose)
        self.assertNotIn("REQUIRED for three-phase separation", self.guide)

    def test_pressure_examples_use_explicit_bara_units(self):
        for contract in (
            'setOutletPressure(1.01325, "bara")',
            'setOutletPressure(35.0, "bara")',
            'setOutletPressure(7.0, "bara")',
            'setOutletPressure(5.0, "bara")',
        ):
            with self.subTest(contract=contract):
                self.assertIn(contract, self.guide)
        self.assertIsNone(
            re.search(r"setOutletPressure\([^,\n]+\);", self.guide)
        )

    def test_geometry_and_effective_length_apis_match_source(self):
        for stale_api in (
            "setLength(value, unit)",
            "getLengthDiameterRatio()",
            "getGasEffectiveLength()",
            "getLiquidEffectiveLength()",
        ):
            with self.subTest(stale_api=stale_api):
                self.assertNotIn(stale_api, self.guide)
        for source_contract in (
            "public double getEffectiveLengthGas()",
            "public double getEffectiveLengthLiquid()",
        ):
            with self.subTest(source_contract=source_contract):
                self.assertIn(source_contract, self.design_source)
        self.assertIn("separator.setSeparatorLength(10.0);", self.guide)
        self.assertIn(
            "separator.getSeparatorLength() / separator.getInternalDiameter()",
            self.guide,
        )

    def test_imported_design_examples_use_current_owner_and_signatures(self):
        existing_signature = (
            "public void setFromExistingDesign(double id, double tanTanLength, "
            "double wallThick, double lEffLiquid, double lEffGas, double "
            "inletNozzle, double gasNozzle, double oilNozzle, "
            "double waterNozzle)"
        )
        spec_signature = (
            "public void setFromDesignSpec(double id, double length, "
            "double lEffLiquid, double lEffGas, double inletNozzleId, "
            "double hhll, double hll, double nll, double lll, double weir, "
            "double hil, double nil, double lil)"
        )
        self.assertIn(existing_signature, self.normalized_design_source)
        self.assertIn(spec_signature, self.normalized_design_source)
        self.assertIn("design.setFromExistingDesign(", self.guide)
        self.assertIn("design.setFromDesignSpec(", self.guide)
        self.assertNotIn("separator.setFromExistingDesign(", self.guide)
        self.assertNotIn("separator.setFromDesignSpec(", self.guide)

    def test_level_defaults_weir_and_retention_basis_match_source(self):
        for source_contract in (
            "private double hhllFraction = 0.80;",
            "private double llllFraction = 0.15;",
            "private double hilFraction = 0.25;",
            "private double nilFraction = 0.20;",
            "private double lilFraction = 0.15;",
            "public void setWeirHeightAbsolute(double height)",
        ):
            with self.subTest(source_contract=source_contract):
                self.assertIn(source_contract, self.design_source)
        for guide_contract in (
            "| HHLL  | `getHHLL()` | High-High Liquid Level "
            "(alarm/shutdown) | 80%",
            "| LLLL  | `getLLLL()` | Low-Low Liquid Level "
            "(alarm/shutdown)   | 15%",
            "design.setWeirHeightAbsolute(",
            "between NIL (interface) and NLL (oil surface)",
            "fixed 150 µm water droplet",
            "Stokes-law expression",
        ):
            with self.subTest(guide_contract=guide_contract):
                self.assertIn(guide_contract, self.guide)
        self.assertIn("double dropletDiameter = 150e-6;", self.three_phase_source)

    def test_current_examples_have_executable_java_regressions(self):
        java_test = (
            ROOT
            / (
                "src/test/java/neqsim/process/equipment/separator/"
                "SeparatorGuideDocumentationTest.java"
            )
        ).read_text(encoding="utf-8")
        for contract in (
            "threePhaseExampleUsesCurrentOutletAndMassFractionApis",
            "mechanicalDesignExampleUsesCurrentGeometryApis",
            "StreamInterface gasOut",
            "producedLiquidWaterMassFraction",
            "design.setFromExistingDesign(",
            "design.setFromDesignSpec(",
            "design.setWeirHeightAbsolute(",
        ):
            with self.subTest(contract=contract):
                self.assertIn(contract, java_test)

    def test_related_separator_guide_resolves(self):
        target = GUIDE.parent / "separator-entrainment-modeling.md"
        self.assertTrue(target.is_file())


if __name__ == "__main__":
    unittest.main()
