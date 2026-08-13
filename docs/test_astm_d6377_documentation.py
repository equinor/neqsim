"""Hermetic contracts for the ASTM D6377 vapor-pressure documentation."""

import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DOC = ROOT / "docs" / "standards" / "astm_d6377_rvp.md"
SOURCE = (
    ROOT
    / "src"
    / "main"
    / "java"
    / "neqsim"
    / "standards"
    / "oilquality"
    / "Standard_ASTM_D6377.java"
)
JAVA_TEST = (
    ROOT
    / "src"
    / "test"
    / "java"
    / "neqsim"
    / "standards"
    / "oilquality"
    / "AstmD6377DocumentationTest.java"
)


class AstmD6377DocumentationContractTest(unittest.TestCase):
    """Guard source accuracy, structure, and executable example coverage."""

    @classmethod
    def setUpClass(cls):
        cls.doc = DOC.read_text(encoding="utf-8")
        cls.source = SOURCE.read_text(encoding="utf-8")
        cls.java_test = JAVA_TEST.read_text(encoding="utf-8")

    def test_jekyll_structure_links_and_fences(self):
        self.assertRegex(
            self.doc,
            r'^---\ntitle: "[^"]+"\ndescription: "[^"]+"\n---\n',
        )
        self.assertNotRegex(self.doc, r"(?m)^# ")
        self.assertEqual(2, self.doc.count("```"))
        self.assertEqual(1, self.doc.count("```java"))

        for target in re.findall(r"\[[^\]]+\]\(([^)#]+\.md)(?:#[^)]+)?\)", self.doc):
            resolved = (DOC.parent / target).resolve()
            self.assertTrue(resolved.is_file(), target)

        self.assertNotIn(r"\[", self.doc)
        self.assertNotIn(r"\(", self.doc)
        self.assertEqual(0, self.doc.count("$$") % 2)

    def test_documented_contract_matches_current_source(self):
        source_patterns = (
            "public void setMethodRVP(RvpMethod method)",
            "public RvpResult getRvpResult()",
            "public RvpResult getRvpResult(RvpMethod method)",
            'if ("RVP".equals(returnParameter))',
            'if ("TVP".equals(returnParameter))',
            "this.thermoSystem.setTemperature(referenceTemperature",
            "this.thermoSystem.setPressure(",
            "this.thermoOps.TVfractionFlash(0.8)",
            "RVP_ASTM_D6377 = 0.834 * VPCR4",
            "public boolean isOnSpec()",
        )
        for pattern in source_patterns:
            self.assertIn(pattern, self.source)

        required_doc_patterns = (
            "SystemInterface workingFluid = sourceOil.clone();",
            "new Standard_ASTM_D6377(workingFluid)",
            "setMethodRVP(RvpMethod.RVP_ASTM_D6377)",
            "getRvpResult(RvpMethod.VPCR4)",
            "getRvpResult(RvpMethod.VPCR4_NO_WATER)",
            'getValue("TVP", "bara")',
            'getValue("RVP", "kPa")',
            "The standard does not force SRK",
            "current `isOnSpec()` implementation always returns",
        )
        for pattern in required_doc_patterns:
            self.assertIn(pattern, self.doc)

    def test_stale_and_unsafe_patterns_are_rejected(self):
        rejected = (
            'getValue("VPCR4", "bara")',
            "Uses SRK-EoS",
            "Typical Uncertainty",
            "Approximate relationship:",
            "Typical RVP Limit",
            "System.out",
            "System.err",
            "import neqsim.standards.oilquality.*",
        )
        for pattern in rejected:
            self.assertNotIn(pattern, self.doc)

        self.assertNotRegex(self.doc, r"(?m)^\s*Standard_ASTM_D6377\s+\w+\s*=.*thermoSystem")
        self.assertIn("public final class AstmD6377Example", self.doc)

    def test_focused_java_regression_exercises_every_documented_call(self):
        calls = (
            "sourceOil.clone()",
            "setReferenceTemperature(37.8, \"C\")",
            "setMethodRVP(RvpMethod.RVP_ASTM_D6377)",
            "vaporPressure.calculate()",
            "vaporPressure.getRvpResult()",
            "vaporPressure.getRvpResult(RvpMethod.VPCR4)",
            "vaporPressure.getRvpResult(RvpMethod.VPCR4_NO_WATER)",
            'vaporPressure.getValue("TVP", "bara")',
            'vaporPressure.getValue("RVP", "kPa")',
            "selected.toJson()",
            "vaporPressure.getMethodRVP()",
        )
        for call in calls:
            self.assertIn(call, self.java_test)


if __name__ == "__main__":
    unittest.main()
