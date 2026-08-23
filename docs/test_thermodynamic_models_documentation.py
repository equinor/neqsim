import re
import unittest
from pathlib import Path


DOCS_DIR = Path(__file__).resolve().parent
REPOSITORY_ROOT = DOCS_DIR.parent
GUIDE = DOCS_DIR / "thermo" / "thermodynamic_models.md"
SYSTEM_SOURCE_DIR = (
    REPOSITORY_ROOT / "src/main/java/neqsim/thermo/system"
)
SYSTEM_THERMO = SYSTEM_SOURCE_DIR / "SystemThermo.java"


class ThermodynamicModelsDocumentationContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.guide = GUIDE.read_text(encoding="utf-8")
        cls.system_thermo = SYSTEM_THERMO.read_text(encoding="utf-8")

    def test_front_matter_is_complete_and_scoped(self):
        front_matter = self.guide.split("---", 2)[1]
        self.assertIn(
            "Source-anchored guide to NeqSim thermodynamic model families",
            front_matter,
        )
        self.assertNotIn("...", front_matter)
        self.assertIn("not accuracy guarantees", self.guide)

    def test_display_math_uses_compact_katex_delimiters(self):
        blocks = re.findall(r"\$\$(.*?)\$\$", self.guide, flags=re.DOTALL)
        self.assertEqual(23, len(blocks))
        for block in blocks:
            with self.subTest(equation=block[:40]):
                self.assertTrue(block)
                self.assertFalse(block[0].isspace())
                self.assertFalse(block[-1].isspace())

    def test_auto_selection_table_matches_source_order(self):
        source_contracts = (
            'hasComponent("MDEA")',
            'hasComponent("water")',
            'return setModel("Electrolyte-ScRK-EOS");',
            'hasComponent("Na+")',
            'return setModel("Electrolyte-CPA-EOS-statoil");',
            'return setModel("CPAs-SRK-EOS-statoil");',
            'return setModel("SRK-TwuCoon-Statoil-EOS");',
            'return setModel("SRK-EOS");',
        )
        method_start = self.system_thermo.index(
            "public SystemInterface autoSelectModel()"
        )
        method_end = self.system_thermo.index(
            "/** {@inheritDoc} */", method_start + 1
        )
        auto_select_method = self.system_thermo[method_start:method_end]
        positions = []
        for contract in source_contracts:
            with self.subTest(source_contract=contract):
                self.assertIn(contract, auto_select_method)
                positions.append(auto_select_method.index(contract))
        self.assertEqual(sorted(positions), positions)

        documented_contracts = (
            "callers must retain the returned object",
            "current pure-water",
            "returns `CPAs-SRK-EOS-statoil`, not `ScRK-EOS`",
            "not a validation or model-ranking service",
            "optimizedFluid.autoSelectMixingRule();",
        )
        for contract in documented_contracts:
            with self.subTest(documented_contract=contract):
                self.assertIn(contract, self.guide)

    def test_nonexistent_generic_ionic_activity_call_is_not_an_example(self):
        self.assertNotIn(
            'getMeanIonicActivityCoefficient("Na+", "Cl-")',
            self.guide,
        )
        self.assertIn(
            "does not expose a general "
            "`getMeanIonicActivityCoefficient(...)` method",
            self.guide,
        )

    def test_documented_system_classes_exist(self):
        documented_classes = set(
            re.findall(r"`(System[A-Z][A-Za-z0-9]+)`", self.guide)
        )
        self.assertGreaterEqual(len(documented_classes), 50)
        for class_name in documented_classes:
            with self.subTest(class_name=class_name):
                self.assertTrue(
                    (SYSTEM_SOURCE_DIR / (class_name + ".java")).is_file(),
                    "Missing source for documented class " + class_name,
                )


if __name__ == "__main__":
    unittest.main()
