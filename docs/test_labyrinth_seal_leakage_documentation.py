"""Protect the rendered labyrinth-seal guide and its executable example."""

import re
import unittest
from pathlib import Path
from urllib.parse import unquote, urlsplit


ROOT = Path(__file__).resolve().parents[1]
GUIDE = ROOT / "docs" / "process" / "equipment" / "labyrinth_seal_leakage.md"
SECTION_INDEX = GUIDE.parent / "README.md"
REFERENCE_INDEX = ROOT / "docs" / "REFERENCE_MANUAL_INDEX.md"
JAVA_REGRESSION = ROOT / "src" / "test" / "java" / "neqsim" / "DocExamplesCompilationTest.java"
LINK = re.compile(r"\[[^\]]+\]\(([^)]+)\)")
DISPLAY_MATH = re.compile(r"\$\$(.*?)\$\$", re.DOTALL)


class LabyrinthSealLeakageDocumentationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.text = GUIDE.read_text(encoding="utf-8")

    def test_front_matter_supplies_the_only_page_title(self):
        lines = self.text.splitlines()
        self.assertEqual("---", lines[0])
        self.assertIn("title: Labyrinth Seal Leakage", lines[:8])
        self.assertTrue(
            any(line.startswith("description:") for line in lines[:8]),
            "Searchable description is required",
        )
        headings = re.findall(r"^#\s+(.+)$", self.text, re.MULTILINE)
        self.assertNotIn(
            "Labyrinth seal leakage",
            headings,
            "Jekyll renders the front-matter title; do not duplicate it as H1",
        )

    def test_display_math_uses_compact_katex_delimiters(self):
        blocks = DISPLAY_MATH.findall(self.text)
        self.assertGreaterEqual(len(blocks), 4)
        self.assertEqual(2 * len(blocks), self.text.count("$$"))
        for block in blocks:
            with self.subTest(block=block[:40]):
                self.assertEqual(block, block.strip())
                self.assertFalse(block.startswith(("\n", "\r")))
                self.assertFalse(block.endswith(("\n", "\r")))
        self.assertNotIn(r"\[", self.text)
        self.assertNotIn(r"\]", self.text)
        self.assertNotIn(r"\(", self.text)
        self.assertNotIn(r"\)", self.text)

    def test_example_calls_are_exercised_by_java_regression(self):
        examples = re.findall(r"```java\n(.*?)\n```", self.text, re.DOTALL)
        self.assertEqual(1, len(examples))
        example = examples[0]
        regression = JAVA_REGRESSION.read_text(encoding="utf-8")
        self.assertIn("testLabyrinthSealLeakageExample", regression)
        for call in (
            "setShaftDiameter",
            "setRadialClearance",
            "setNumberOfTeeth",
            "setInletPressure",
            "setOutletPressure",
            "calculateAllMethods",
            "getToothPressures",
            "getToothTemperatures",
        ):
            with self.subTest(call=call):
                self.assertIn(call, example)
                self.assertIn(call, regression)

    def test_relative_links_resolve_and_indexes_discover_the_guide(self):
        for target in LINK.findall(self.text):
            parsed = urlsplit(target)
            if parsed.scheme or parsed.netloc or not parsed.path:
                continue
            destination = (GUIDE.parent / unquote(parsed.path)).resolve()
            if destination.suffix == "":
                destination = destination.with_suffix(".md")
            with self.subTest(target=target):
                self.assertTrue(destination.is_relative_to(ROOT))
                self.assertTrue(destination.is_file(), f"Missing target: {target}")
        self.assertIn("labyrinth_seal_leakage", SECTION_INDEX.read_text(encoding="utf-8"))
        self.assertIn(
            "process/equipment/labyrinth_seal_leakage.md",
            REFERENCE_INDEX.read_text(encoding="utf-8"),
        )


if __name__ == "__main__":
    unittest.main()
