"""Regression contracts for the safety documentation hubs."""

from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]
SAFETY_HUB = ROOT / "docs" / "safety" / "README.md"
PROCESS_SAFETY_HUB = ROOT / "docs" / "process" / "safety" / "README.md"
REFERENCE_INDEX = ROOT / "docs" / "REFERENCE_MANUAL_INDEX.md"


class SafetyHubNavigationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.hubs = {
            SAFETY_HUB: SAFETY_HUB.read_text(encoding="utf-8"),
            PROCESS_SAFETY_HUB: PROCESS_SAFETY_HUB.read_text(encoding="utf-8"),
        }
        cls.reference_index = REFERENCE_INDEX.read_text(encoding="utf-8")

    def test_hubs_have_front_matter_without_duplicate_body_h1(self):
        for path, content in self.hubs.items():
            with self.subTest(path=path):
                self.assertTrue(content.startswith("---\ntitle:"))
                body = content.split("---", 2)[2]
                body_without_fences = re.sub(
                    r"```.*?```", "", body, flags=re.DOTALL
                )
                self.assertNotRegex(body_without_fences, r"(?m)^# ")

    def test_local_links_use_explicit_markdown_targets(self):
        for path, content in self.hubs.items():
            for target in re.findall(r"\[[^\]]+\]\(([^)]+)\)", content):
                target_path = target.split("#", 1)[0]
                if "://" in target_path or target_path.startswith("#"):
                    continue
                with self.subTest(path=path, target=target):
                    self.assertTrue(target_path.endswith(".md"))

    def test_every_local_target_resolves(self):
        for path, content in self.hubs.items():
            for target in re.findall(r"\[[^\]]+\]\(([^)]+)\)", content):
                target_path = target.split("#", 1)[0]
                if "://" in target_path or target_path.startswith("#"):
                    continue
                resolved = (path.parent / target_path).resolve()
                with self.subTest(path=path, target=target):
                    self.assertTrue(resolved.is_file(), resolved)

    def test_link_labels_are_human_readable(self):
        for path, content in self.hubs.items():
            with self.subTest(path=path):
                self.assertIsNone(re.search(r"\[[^\]]+\.md\]\(", content))

    def test_safety_hub_exposes_core_risk_and_consequence_guides(self):
        safety_hub = self.hubs[SAFETY_HUB]
        for target in (
            "HAZOP.md",
            "FMEA.md",
            "event_fault_trees.md",
            "depressurization_per_API_521.md",
            "mdmt_assessment.md",
            "dispersion_and_consequence.md",
            "../process/safety/scenario-generation.md",
            "../process/safety/release-dispersion-scenarios.md",
        ):
            with self.subTest(target=target):
                self.assertIn(f"]({target})", safety_hub)

    def test_hubs_cross_link_and_remain_indexed(self):
        self.assertIn(
            "](../process/safety/README.md)", self.hubs[SAFETY_HUB]
        )
        self.assertIn(
            "](../../safety/README.md)", self.hubs[PROCESS_SAFETY_HUB]
        )
        self.assertIn(
            "[docs/safety/README.md](safety/README.md)", self.reference_index
        )
        self.assertIn(
            "[docs/process/safety/README.md](process/safety/README.md)",
            self.reference_index,
        )


if __name__ == "__main__":
    unittest.main()
