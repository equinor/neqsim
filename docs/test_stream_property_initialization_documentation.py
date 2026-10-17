"""Contracts for stream transport-property initialization guidance."""

import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
GUIDE = ROOT / "docs/thermo/reading_fluid_properties.md"
PROCESS = ROOT / "docs/process/processmodel/process_system.md"


class StreamPropertyInitializationDocumentationTest(unittest.TestCase):
    """Keep the property-availability boundary explicit in both entry points."""

    def test_fluid_guide_explains_stream_modes_and_recovery(self):
        text = " ".join(GUIDE.read_text(encoding="utf-8").split())
        for marker in (
            "Stream.PropertyInitLevel.FULL",
            "DENSITY_ONLY",
            "zero or stale",
            "current stream fluid",
            "stream.getFluid().initProperties()",
            "does not itself recalculate",
        ):
            with self.subTest(marker=marker):
                self.assertIn(marker, text)
        self.assertNotIn("called internally by each equipment's", text)

    def test_process_guide_explains_override_and_null(self):
        text = " ".join(PROCESS.read_text(encoding="utf-8").split())
        for marker in (
            "default `Stream.PropertyInitLevel.FULL`",
            "zero or stale",
            "does not reset existing stream levels",
            "re-applied at the start of execution",
            "does not itself recalculate",
        ):
            with self.subTest(marker=marker):
                self.assertIn(marker, text)
        self.assertNotIn("null = never configured, streams stay on FULL", text)
        self.assertNotIn("Every `Stream.run()` ends with `initProperties()`", text)

    def test_linked_initialization_section_exists(self):
        guide = GUIDE.read_text(encoding="utf-8")
        self.assertIn(
            "../process/processmodel/process_system.md#physical-property-initialization-level",
            guide,
        )
        self.assertIn(
            "### Physical-Property Initialization Level",
            PROCESS.read_text(encoding="utf-8"),
        )


if __name__ == "__main__":
    unittest.main()
