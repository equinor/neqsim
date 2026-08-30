"""Regression contracts for the experimental solid Helmholtz guide."""

from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
GUIDE = ROOT / "docs" / "thermo" / "solid_helmholtz_models.md"


class SolidHelmholtzDocumentationTest(unittest.TestCase):
    def test_guide_has_complete_front_matter_without_duplicate_h1(self):
        text = GUIDE.read_text(encoding="utf-8")
        self.assertTrue(text.startswith("---\ntitle:"))
        self.assertIn('description: "', text.split("---", 2)[1])
        self.assertNotIn("\n# Experimental Solid Helmholtz Models", text)

    def test_guide_records_units_ranges_and_experimental_boundaries(self):
        text = GUIDE.read_text(encoding="utf-8")
        for contract in (
            "160,000 bara (16 GPa)",
            "100,000 bara (10 GPa)",
            "m3/mol",
            "J/mol",
            "J/(mol K)",
            "experimental",
            "one component only",
            "not statements of quantified",
            "nucleation",
            "accountable engineering review",
        ):
            with self.subTest(contract=contract):
                self.assertIn(contract, text)

    def test_documented_api_is_anchored_in_current_source(self):
        sources = {
            "src/main/java/neqsim/thermo/system/SystemArgonSolidHelmholtzEos.java": (
                "SystemArgonSolidHelmholtzEos(double temperature, double pressure)",
                "ArgonSolidHelmholtzEquation getArgonSolidEquation()",
            ),
            "src/main/java/neqsim/thermo/phase/PhaseSolidHelmholtzEos.java": (
                "SolidHelmholtzState getSolidState()",
            ),
            "src/main/java/neqsim/thermo/system/SystemLeachmanEos.java": (
                "SystemLeachmanEos(double T, double P, String hydrogenComponentName, boolean checkForSolids)",
            ),
            "src/main/java/neqsim/thermodynamicoperations/ThermodynamicOperations.java": (
                "FreezingPointResult freezingPointTemperatureFlashResult()",
            ),
            "src/main/java/neqsim/thermodynamicoperations/flashops/saturationops/FreezingPointResult.java": (
                "boolean isConverged()",
                "double getTemperature(String unit)",
                "double getResidual()",
                "String getFailureReason()",
            ),
        }
        for relative_path, signatures in sources.items():
            source = (ROOT / relative_path).read_text(encoding="utf-8")
            for signature in signatures:
                with self.subTest(path=relative_path, signature=signature):
                    self.assertIn(signature, source)

    def test_both_examples_have_executable_java_regressions(self):
        source = (
            ROOT
            / "src"
            / "test"
            / "java"
            / "neqsim"
            / "thermo"
            / "SolidHelmholtzDocumentationTest.java"
        ).read_text(encoding="utf-8")
        for contract in (
            "argonDirectStateExample",
            "paraHydrogenFreezingPointExample",
            "new SystemArgonSolidHelmholtzEos(70.0, 10.0)",
            'new SystemLeachmanEos(13.6, 0.07042, "para-hydrogen", true)',
            "freezingPointTemperatureFlashResult()",
        ):
            with self.subTest(contract=contract):
                self.assertIn(contract, source)

    def test_new_guide_is_discoverable_from_both_indexes(self):
        targets = {
            "docs/thermo/README.md": "(solid_helmholtz_models)",
            "docs/REFERENCE_MANUAL_INDEX.md": "(thermo/solid_helmholtz_models.md)",
            "docs/thermo/thermodynamic_models.md": "(solid_helmholtz_models.md)",
        }
        for relative_path, target in targets.items():
            text = (ROOT / relative_path).read_text(encoding="utf-8")
            with self.subTest(path=relative_path):
                self.assertIn(target, text)

    def test_model_catalog_names_both_public_system_classes(self):
        text = (ROOT / "docs" / "thermo" / "thermodynamic_models.md").read_text(
            encoding="utf-8"
        )
        self.assertGreaterEqual(text.count("SystemArgonSolidHelmholtzEos"), 2)
        self.assertGreaterEqual(text.count("SystemSolidHelmholtzEos"), 2)


if __name__ == "__main__":
    unittest.main()
