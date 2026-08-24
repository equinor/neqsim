import re
import unittest
from pathlib import Path
from urllib.parse import unquote


DOCS_DIR = Path(__file__).resolve().parent
REPOSITORY_ROOT = DOCS_DIR.parent
GUIDE = DOCS_DIR / "safety" / "relief_valve_sizing_api.md"
SAFETY_INDEX = DOCS_DIR / "safety" / "README.md"
SOURCE = (
    REPOSITORY_ROOT
    / "src/main/java/neqsim/process/util/fire/ReliefValveSizing.java"
)


def java_fences(content):
    return re.findall(r"\x60\x60\x60java\n(.*?)\x60\x60\x60", content, re.DOTALL)


def heading_slugs(content):
    content_without_fences = re.sub(
        r"\x60\x60\x60.*?\x60\x60\x60", "", content, flags=re.DOTALL
    )
    return {
        re.sub(r"[^a-z0-9 -]", "", heading.lower())
        .strip()
        .replace(" ", "-")
        for heading in re.findall(
            r"^#{1,6}\s+(.+)$", content_without_fences, flags=re.MULTILINE
        )
    }


class ReliefValveSizingDocumentationContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.guide = GUIDE.read_text(encoding="utf-8")
        cls.safety_index = SAFETY_INDEX.read_text(encoding="utf-8")
        cls.source = SOURCE.read_text(encoding="utf-8")

    def test_structure_math_fences_and_internal_links(self):
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

    def test_documented_static_signatures_match_source(self):
        contracts = (
            "private ReliefValveSizing()",
            "public static PSVSizingResult calculateRequiredArea(",
            "public static double calculateMassFlowCapacity(",
            "public static LiquidPSVSizingResult calculateLiquidReliefArea(",
            "public static double calculateTwoPhaseReliefArea(",
            "public static double calculateAPI521FireHeatInput(",
            "double wettedAreaM2, boolean hasDrainage, boolean hasFireFighting",
        )
        for contract in contracts:
            with self.subTest(contract=contract):
                self.assertIn(contract, self.source)

        self.assertIn("static screening utility", self.guide)
        self.assertIn("does not change the numerical factor", self.guide)

    def test_stale_object_api_and_result_getters_are_absent(self):
        stale_calls = (
            "new ReliefValveSizing(",
            ".setReliefPressure(",
            ".setBackPressure(",
            ".setReliefTemperature(",
            ".setReliefMassRate(",
            ".setInletVapourMassFraction(",
            ".getRequiredArea() + \" m2\"",
            ".getKd(",
            ".getKw(",
            ".getKc(",
            ".getKv(",
        )
        for block in java_fences(self.guide):
            for stale_call in stale_calls:
                with self.subTest(stale_call=stale_call):
                    self.assertNotIn(stale_call, block)

        self.assertIn("getRequiredAreaM2()", self.guide)
        self.assertIn("getViscosityCorrectionFactor()", self.guide)
        self.assertIn("does not contain a $K_c$ field", self.guide)

    def test_complete_java_example_exercises_every_primary_helper(self):
        blocks = java_fences(self.guide)
        self.assertEqual(1, len(blocks))
        example = blocks[0]
        for contract in (
            "public final class ReliefValveSizingExample",
            "public static void main(String[] args)",
            "ReliefValveSizing.calculateRequiredArea(",
            "ReliefValveSizing.calculateLiquidReliefArea(",
            "ReliefValveSizing.calculateTwoPhaseReliefArea(",
            "ReliefValveSizing.calculateAPI521FireHeatInput(80.0, true, true)",
        ):
            with self.subTest(contract=contract):
                self.assertIn(contract, example)

        self.assertNotIn("System.out", example)

    def test_safety_index_discovers_the_guide(self):
        self.assertIn(
            "[Relief-Valve Sizing Screening](relief_valve_sizing_api.md)",
            self.safety_index,
        )


if __name__ == "__main__":
    unittest.main()
