import re
import unittest
from pathlib import Path
from urllib.parse import unquote


DOCS_DIR = Path(__file__).resolve().parent
REPOSITORY_ROOT = DOCS_DIR.parent
GUIDE = DOCS_DIR / "standards" / "oil_quality_standards.md"
STANDARDS_INDEX = DOCS_DIR / "standards" / "README.md"
REFERENCE_INDEX = DOCS_DIR / "REFERENCE_MANUAL_INDEX.md"
SYSTEM_INTERFACE = (
    REPOSITORY_ROOT / "src/main/java/neqsim/thermo/system/SystemInterface.java"
)
OIL_QUALITY_SOURCE_DIR = (
    REPOSITORY_ROOT / "src/main/java/neqsim/standards/oilquality"
)


def fenced_blocks(content, language):
    pattern = rf"\x60\x60\x60{language}\n(.*?)\x60\x60\x60"
    return re.findall(pattern, content, re.DOTALL)


def heading_slugs(content):
    content_without_fences = re.sub(
        r"\x60\x60\x60.*?\x60\x60\x60",
        "",
        content,
        flags=re.DOTALL,
    )
    return {
        re.sub(r"[^a-z0-9 -]", "", heading.lower())
        .strip()
        .replace(" ", "-")
        for heading in re.findall(
            r"^#{1,6}\s+(.+)$",
            content_without_fences,
            flags=re.MULTILINE,
        )
    }


class OilQualityStandardsDocumentationContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.guide = GUIDE.read_text(encoding="utf-8")
        cls.standards_index = STANDARDS_INDEX.read_text(encoding="utf-8")
        cls.reference_index = REFERENCE_INDEX.read_text(encoding="utf-8")
        cls.system_interface = SYSTEM_INTERFACE.read_text(encoding="utf-8")
        cls.sources = {
            path.stem: path.read_text(encoding="utf-8")
            for path in OIL_QUALITY_SOURCE_DIR.glob("Standard_*.java")
        }

    def test_structure_compact_math_fences_and_internal_links(self):
        self.assertTrue(self.guide.startswith("---\n"))
        self.assertEqual(self.guide.count("\x60\x60\x60") % 2, 0)
        self.assertEqual(self.guide.count("$$") % 2, 0)

        without_fences = re.sub(
            r"\x60\x60\x60.*?\x60\x60\x60",
            "",
            self.guide,
            flags=re.DOTALL,
        )
        self.assertNotRegex(without_fences, re.compile(r"^# ", re.MULTILINE))
        self.assertNotIn(r"\[", without_fences)
        self.assertNotIn(r"\(", without_fences)
        for line in without_fences.splitlines():
            if "$$" in line:
                self.assertEqual(2, line.count("$$"))
                self.assertNotIn("$$ ", line)
                self.assertNotIn(" $$", line)

        link_pattern = re.compile(r"(?<!!)\[[^\]]+\]\(([^)]+)\)")
        for destination in link_pattern.findall(self.guide):
            if destination.startswith(("http://", "https://", "mailto:")):
                continue
            target, _, fragment = unquote(destination).partition("#")
            target_path = (GUIDE.parent / target).resolve()
            with self.subTest(destination=destination):
                self.assertTrue(target.endswith(".md"))
                self.assertTrue(target_path.is_file())
                if fragment:
                    self.assertIn(
                        fragment,
                        heading_slugs(target_path.read_text(encoding="utf-8")),
                    )

    def test_tbp_fraction_units_and_complete_examples_are_copyable(self):
        calls = re.findall(
            r'addTBPfraction\("[^"]+",\s*[0-9.]+,\s*([0-9.]+),\s*([0-9.]+)\)',
            self.guide,
        )
        self.assertEqual(14, len(calls))
        for molar_mass, density in calls:
            with self.subTest(molar_mass=molar_mass, density=density):
                self.assertGreater(float(molar_mass), 0.05)
                self.assertLessEqual(float(molar_mass), 0.65)
                self.assertGreaterEqual(float(density), 0.65)
                self.assertLessEqual(float(density), 1.1)

        self.assertIn(
            "@param molarMass molar mass of the component in kg/mol",
            self.system_interface,
        )
        self.assertIn("TBP molar mass is kg/mol", self.guide)

        java_blocks = fenced_blocks(self.guide, "java")
        python_blocks = fenced_blocks(self.guide, "python")
        self.assertGreaterEqual(len(java_blocks), 10)
        self.assertEqual(1, len(python_blocks))
        self.assertIn('oil.addTBPfraction("C7", 0.15, 0.095, 0.72)', java_blocks[0])
        self.assertIn('apiStd.getValue("API")', java_blocks[0])
        self.assertIn('distStd.getValue("T50", "C")', java_blocks[0])
        self.assertIn('oil.addTBPfraction("C7", 0.15, 0.095, 0.72)', python_blocks[0])
        self.assertIn("d4052.getValue('API')", python_blocks[0])

    def test_documented_parameter_keys_match_current_sources(self):
        contracts = {
            "Standard_ASTM_D86": (
                'case "IBP":',
                'case "T50":',
                'case "FBP":',
                'case "WatsonK":',
            ),
            "Standard_ASTM_D4052": (
                'case "density":',
                'case "SG":',
                'case "API":',
            ),
            "Standard_ASTM_D445": (
                'case "KV40":',
                'case "KV100":',
                'case "VI":',
            ),
            "Standard_ASTM_D4294": (
                'case "sulfur":',
                'case "totalSulfur":',
            ),
            "Standard_BSW": (
                'case "BSW":',
                'case "waterCut":',
            ),
        }
        for class_name, signatures in contracts.items():
            source = self.sources[class_name]
            for signature in signatures:
                with self.subTest(class_name=class_name, signature=signature):
                    self.assertIn(signature, source)

        stale_contracts = (
            'getValue("API gravity")',
            "getValue('API gravity')",
            '"sulfurContent"',
            "setMinAPIGravity(",
            "setMaxAPIGravity(",
            "`waterVolumeFraction`",
            "`oilVolumeFraction`",
        )
        for stale_contract in stale_contracts:
            with self.subTest(stale_contract=stale_contract):
                self.assertNotIn(stale_contract, self.guide)

    def test_result_and_specification_boundaries_are_explicit(self):
        d4052_source = self.sources["Standard_ASTM_D4052"]
        bsw_source = self.sources["Standard_BSW"]
        self.assertNotIn("setMinAPIGravity", d4052_source)
        self.assertNotIn("setMaxAPIGravity", d4052_source)
        self.assertIn("`isOnSpec()` currently means only", self.guide)
        self.assertIn(
            "the class does not store minimum or maximum product limits",
            self.guide,
        )
        self.assertIn("no sediment model", self.guide)
        self.assertIn("exposes no separate oil-volume getter", self.guide)
        self.assertIn('case "waterCut":', bsw_source)
        self.assertIn(
            "`IBP`, `Txx`, `FBP`, `VABP`, "
            "`MABP`, `WABP`, `CABP`, and `MeABP` "
            "are temperatures",
            self.guide,
        )

    def test_standards_indexes_discover_the_guide(self):
        link = "[Oil-quality methods](oil_quality_standards.md)"
        self.assertIn(link, self.standards_index)
        self.assertIn("standards/oil_quality_standards.md", self.reference_index)


if __name__ == "__main__":
    unittest.main()
