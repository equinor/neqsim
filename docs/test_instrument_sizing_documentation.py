"""Keep instrument entry-point and cabinet-sizing guidance aligned."""

import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


class InstrumentSizingDocumentationTest(unittest.TestCase):
    """Guard the bounded overview and sizing documentation contract."""

    def test_overview_routes_features_to_the_correct_discipline(self):
        text = (ROOT / "docs/process/README.md").read_text(encoding="utf-8")
        electrical = text.split("### Electrical Design Documentation", 1)[1].split("### ", 1)[0]
        instrument = text.split("### Instrument Design Documentation", 1)[1].split("### ", 1)[0]
        for marker in ("SystemElectricalDesign", "getSystemElectricalDesign()"):
            with self.subTest(marker=marker):
                self.assertIn(marker, electrical)
                self.assertNotIn(marker, instrument)
        for marker in ("getSystemInstrumentDesign()", "DCS/SIS", "I/O", "cost estimates"):
            with self.subTest(marker=marker):
                self.assertIn(marker, instrument)
        self.assertIn("[instrument-design.md](instrument-design)", instrument)

    def test_javadoc_matches_card_capacities_and_minimums(self):
        path = ROOT / "src/main/java/neqsim/process/instrumentdesign/system/SystemInstrumentDesign.java"
        source = path.read_text(encoding="utf-8")
        javadoc = source.split("public class SystemInstrumentDesign", 1)[0]
        doc = " ".join(re.sub(r"(?m)^\s*\*\s?", "", javadoc).split())
        for marker in (
            "DCS: 16 channels per I/O card, 16 cards per cabinet",
            "SIS: 8 channels per I/O card, 8 cards per cabinet",
            "rounded up", "minimum of one DCS", "zero SIS cabinets",
            "not vendor hardware selection", "functional-safety certification",
        ):
            with self.subTest(marker=marker):
                self.assertIn(marker, doc)
        self.assertNotIn("~64", doc)
        self.assertNotIn("~32", doc)
        self.assertIn("Math.ceil(dcsIO / 16.0)", source)
        self.assertIn("Math.ceil(totalSafetyIO / 8.0)", source)

    def test_guide_explains_rounding_minimums_and_validity(self):
        text = (ROOT / "docs/process/instrument-design.md").read_text(encoding="utf-8")
        section = text.split("### DCS and SIS Cabinet Sizing", 1)[1].split("### Cost", 1)[0]
        flat = " ".join(section.split())
        self.assertRegex(section, r"\| DCS \| 16 \| 16 \|")
        self.assertRegex(section, r"\| SIS \| 8 \| 8 \|")
        for marker in (
            "rounded up", "minimum of one DCS", "zero SIS cabinets",
            "not vendor hardware selection", "functional-safety certification",
            "AI, DI and DO", "200", "InstrumentSizingDocumentationTest",
        ):
            with self.subTest(marker=marker):
                self.assertIn(marker, flat)
        self.assertNotIn("(redundant)", section)

    def test_changed_sections_keep_existing_targets_and_no_new_code(self):
        for name in ("README.md", "instrument-design.md"):
            text = (ROOT / "docs/process" / name).read_text(encoding="utf-8")
            self.assertTrue(text.startswith("---\n"))
            self.assertRegex(text.split("---", 2)[1], r"(?m)^title:")
            self.assertRegex(text.split("---", 2)[1], r"(?m)^description:")
        guide = ROOT / "docs/process/instrument-design.md"
        self.assertTrue(guide.is_file())
        section = guide.read_text(encoding="utf-8").split("### DCS and SIS Cabinet Sizing", 1)[1].split("### Cost", 1)[0]
        self.assertNotIn("```", section)
        self.assertFalse(re.search(r"\\[\[(]", section))


if __name__ == "__main__":
    unittest.main()
