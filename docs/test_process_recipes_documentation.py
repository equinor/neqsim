"""Regression tests for the process-equipment cookbook contracts."""

import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
GUIDE = ROOT / "docs" / "cookbook" / "process-recipes.md"
ADJUSTER_SOURCE = (
    ROOT / "src" / "main" / "java" / "neqsim" / "process" / "equipment" / "util" / "Adjuster.java"
)
VALVE_DESIGN_SOURCE = (
    ROOT
    / "src"
    / "main"
    / "java"
    / "neqsim"
    / "process"
    / "mechanicaldesign"
    / "valve"
    / "ValveMechanicalDesign.java"
)


class ProcessRecipesDocumentationTest(unittest.TestCase):
    """Keep the cookbook aligned with the current public Java API."""

    @classmethod
    def setUpClass(cls):
        cls.guide = GUIDE.read_text(encoding="utf-8")

    def test_front_matter_markdown_and_internal_links(self):
        self.assertRegex(self.guide, r"\A---\n(?:.|\n)*?\n---\n")
        prose = re.sub(r"```.*?```", "", self.guide, flags=re.DOTALL)
        self.assertNotRegex(prose, r"(?m)^# ")
        self.assertEqual(self.guide.count("```"), 2 * self.guide.count("```python"))

        for href in re.findall(r"\[[^]]+\]\(([^)]+)\)", self.guide):
            target = href.split("#", maxsplit=1)[0]
            if not target or target.startswith(("http://", "https://", "mailto:")):
                continue
            self.assertTrue(target.endswith(".md"), target)
            self.assertTrue((GUIDE.parent / target).resolve().exists(), target)

    def test_common_setup_makes_focused_recipes_composable(self):
        first_python_block = re.findall(r"```python\n(.*?)```", self.guide, re.DOTALL)[0]
        for required in (
            "ProcessSystem =",
            "feed = Stream(",
            "process = ProcessSystem()",
            "process.add(feed)",
        ):
            self.assertIn(required, first_python_block)

        for stale_name in (
            "gas_stream",
            "high_pressure_stream",
            "inlet_stream",
            "mixer_inlet",
            "outlet_stream",
        ):
            self.assertNotIn(stale_name, self.guide)

    def test_temperature_and_pressure_examples_are_unit_explicit(self):
        self.assertNotIn("setOutTemperature(", self.guide)
        self.assertIn('setOutletTemperature(40.0, "C")', self.guide)

        pressure_calls = re.findall(r"setOutletPressure\(([^\n]+)\)", self.guide)
        self.assertGreater(len(pressure_calls), 0)
        for arguments in pressure_calls:
            self.assertIn(",", arguments)
            self.assertRegex(arguments, r"[\"'](?:bara|barg|Pa|kPa|MPa|psia|psig)[\"']")

    def test_polytropic_recipe_enables_the_polytropic_calculation(self):
        self.assertIn("compressor.setUsePolytropicCalc(True)", self.guide)
        self.assertIn("compressor.setPolytropicEfficiency(0.80)", self.guide)
        self.assertLess(
            self.guide.index("compressor.setUsePolytropicCalc(True)"),
            self.guide.index("compressor.setPolytropicEfficiency(0.80)"),
        )

    def test_adjuster_recipe_matches_stream_resolution_behavior(self):
        source = ADJUSTER_SOURCE.read_text(encoding="utf-8")
        self.assertIn("getStreamFromEquipment", source)
        self.assertIn("instanceof TwoPortInterface", source)
        self.assertIn("getOutletStream()", source)

        self.assertIn('adjuster.setAdjustedVariable(feed, "flow", "kg/hr")', self.guide)
        self.assertIn("generic `Adjuster` changes a stream property", self.guide)
        self.assertNotIn("compressor.getOutletPressure()", self.guide)

    def test_valve_sizing_correlations_are_complete(self):
        source = VALVE_DESIGN_SOURCE.read_text(encoding="utf-8")
        for standard in (
            "default",
            "IEC 60534",
            "IEC 60534 full",
            "prod choke",
            "Sachdeva",
            "Gilbert",
            "Baxendell",
            "Ros",
            "Achong",
        ):
            self.assertIn(f'"{standard}"', self.guide)
            if standard != "default":
                self.assertIn(f'"{standard}"', source)

    def test_cookbook_is_discoverable_from_both_indexes(self):
        for index in (
            ROOT / "docs" / "cookbook" / "index.md",
            ROOT / "docs" / "REFERENCE_MANUAL_INDEX.md",
        ):
            self.assertIn("process-recipes", index.read_text(encoding="utf-8"), str(index))


if __name__ == "__main__":
    unittest.main()
