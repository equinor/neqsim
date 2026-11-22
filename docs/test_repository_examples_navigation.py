"""Keep the repository examples entry point linked to maintained resources."""

import re
import unittest
from pathlib import Path
from urllib.parse import unquote, urlsplit


ROOT = Path(__file__).resolve().parents[1]
README = ROOT / "examples" / "README.md"
LINK = re.compile(r"\[[^\]]+\]\(([^)]+)\)")
TOPIC_GUIDES = {
    "Process Simulation": "../docs/tutorials/index.md",
    "Thermodynamics & Properties": "../docs/examples/ReadingFluidProperties.md",
    "PVT Analysis": "../docs/pvtsimulation/pvt_lab_tests.md",
    "Field Development & Subsea": "../docs/fielddevelopment/README.md",
    "Standards & Gas Quality": "../docs/standards/iso6976_calorific_values.md",
    "Safety & Depressurization": "../docs/safety/index.md",
}


class RepositoryExamplesNavigationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.text = README.read_text(encoding="utf-8")

    def test_all_relative_destinations_exist_in_checkout(self):
        targets = LINK.findall(self.text)
        self.assertTrue(targets, "The examples entry point must contain links")
        for target in targets:
            parsed = urlsplit(target)
            if parsed.scheme or parsed.netloc or not parsed.path:
                continue
            destination = (README.parent / unquote(parsed.path)).resolve()
            with self.subTest(target=target):
                self.assertTrue(destination.is_relative_to(ROOT))
                self.assertTrue(destination.exists(), f"Missing target: {target}")

    def test_each_topic_has_a_direct_maintained_guide(self):
        topics = self.text.split("## Examples by Topic\n", 1)[1].split(
            "## Case Studies\n", 1
        )[0]
        for heading, target in TOPIC_GUIDES.items():
            with self.subTest(topic=heading):
                section = topics.split(f"### {heading}\n", 1)[1].split(
                    "\n### ", 1
                )[0]
                self.assertIn(target, LINK.findall(section))
                self.assertTrue((README.parent / target).is_file())
        self.assertNotIn("Look for files with", topics)

    def test_task_api_and_execution_boundaries_remain_discoverable(self):
        targets = LINK.findall(self.text)
        for target in (
            "../docs/tutorials/solve-engineering-task.md",
            "../docs/java-getting-started.md",
            "../src/main/java/neqsim/",
            "../docs/examples/index.md",
        ):
            with self.subTest(target=target):
                self.assertIn(target, targets)
        self.assertNotIn("../task_solve/", targets)
        self.assertNotIn("htmlpreview.github.io", self.text)
        self.assertIn("not a claim that every example has been executed", self.text)
        self.assertIn("local output directory", self.text)


if __name__ == "__main__":
    unittest.main()
