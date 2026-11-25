"""Source-linked contracts for the produced-water treatment guide."""

from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]
GUIDE = ROOT / "docs/process/equipment/water_treatment.md"
AUDIT = ROOT / "docs/BROKEN_API_AUDIT_REPORT.md"
TRAIN_SOURCE = (
    ROOT
    / "src/main/java/neqsim/process/equipment/watertreatment/"
    "ProducedWaterTreatmentTrain.java"
)
SEPARATOR_SOURCE = (
    ROOT
    / "src/main/java/neqsim/process/equipment/separator/ThreePhaseSeparator.java"
)
JAVA_TEST = (
    ROOT
    / "src/test/java/neqsim/process/equipment/watertreatment/"
    "ProducedWaterTreatmentTrainTest.java"
)


class ProducedWaterTreatmentDocumentationTest(unittest.TestCase):
    """Keep the guide aligned with the executable Java contract."""

    @classmethod
    def setUpClass(cls):
        cls.guide = GUIDE.read_text(encoding="utf-8")
        cls.audit = AUDIT.read_text(encoding="utf-8")
        cls.train_source = TRAIN_SOURCE.read_text(encoding="utf-8")
        cls.separator_source = SEPARATOR_SOURCE.read_text(encoding="utf-8")
        cls.java_test = JAVA_TEST.read_text(encoding="utf-8")

    def test_front_matter_and_heading_contract(self):
        self.assertTrue(self.guide.startswith("---\ntitle:"))
        body = self.guide.split("---", 2)[2]
        body_without_fences = re.sub(
            r"```.*?```", "", body, flags=re.DOTALL
        )
        self.assertNotRegex(body_without_fences, r"(?m)^# ")

    def test_treatment_train_examples_use_current_result_contract(self):
        stale_calls = (
            "getOutletOilConcentration()",
            "isCompliant()",
            "getComplianceReport()",
            "prodSep.getWaterCut()",
            'train.addStage("',
            "stage.getInletOilMgL()",
            "stage.getOutletOilMgL()",
        )
        for stale_call in stale_calls:
            with self.subTest(stale_call=stale_call):
                self.assertNotIn(stale_call, self.guide)

        current_calls = (
            "getOilInWaterMgL()",
            "isDischargeCompliant()",
            "generateReport()",
            "new WaterTreatmentStage(",
            "getInletOIW()",
            "getOutletOIW()",
            "StreamInterface treatedWater",
        )
        for current_call in current_calls:
            with self.subTest(current_call=current_call):
                self.assertIn(current_call, self.guide)

    def test_separator_water_cut_is_explicitly_mass_based(self):
        expected_fragments = (
            "producedLiquidWaterMassFraction",
            'waterOut.getFlowRate("kg/hr")',
            'oilOut.getFlowRate("kg/hr")',
            "producedLiquidMassFlowKgPerHour > 0.0",
        )
        for fragment in expected_fragments:
            with self.subTest(fragment=fragment):
                self.assertIn(fragment, self.guide)

    def test_documented_calls_exist_in_source(self):
        train_signatures = (
            "public void addStage(WaterTreatmentStage stage)",
            "public double getOilInWaterMgL()",
            "public boolean isDischargeCompliant()",
            "public boolean isCompliantWith(double limitMgL)",
            "public String generateReport()",
            "public double getInletOIW()",
            "public double getOutletOIW()",
        )
        for signature in train_signatures:
            with self.subTest(signature=signature):
                self.assertIn(signature, self.train_source)

        separator_signatures = (
            "public StreamInterface getOilOutStream()",
            "public StreamInterface getWaterOutStream()",
        )
        for signature in separator_signatures:
            with self.subTest(signature=signature):
                self.assertIn(signature, self.separator_source)

    def test_java_regression_executes_guide_contract(self):
        expected_fragments = (
            "documentedSeparatorIntegrationUsesCurrentResultApis",
            "getOilInWaterMgL()",
            "isDischargeCompliant()",
            "getTreatedWaterStream()",
            "getRecoveredOilStream()",
            "generateReport()",
            "producedLiquidWaterMassFraction",
        )
        for fragment in expected_fragments:
            with self.subTest(fragment=fragment):
                self.assertIn(fragment, self.java_test)

    def test_audit_marks_produced_water_case_resolved(self):
        match = re.search(
            r"### 2\.5 .*?(?=\n### 2\.6 )",
            self.audit,
            flags=re.DOTALL,
        )
        self.assertIsNotNone(match)
        section = match.group(0)
        self.assertIn("resolved", section.lower())
        self.assertIn("test_produced_water_treatment_documentation.py", section)
        self.assertNotIn("| `docs/process/equipment/water_treatment.md`", section)


if __name__ == "__main__":
    unittest.main()
