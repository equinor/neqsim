"""Contract tests for the GOSP screening tutorial."""

import re
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[1]
TUTORIAL = REPO_ROOT / "docs" / "tutorials" / "gosp_tutorial.md"
TUTORIAL_INDEX = REPO_ROOT / "docs" / "tutorials" / "index.md"
REFERENCE_INDEX = REPO_ROOT / "docs" / "REFERENCE_MANUAL_INDEX.md"
SYSTEM_INTERFACE = (
    REPO_ROOT / "src" / "main" / "java" / "neqsim" / "thermo" / "system"
    / "SystemInterface.java"
)
STREAM_INTERFACE = (
    REPO_ROOT / "src" / "main" / "java" / "neqsim" / "process" / "equipment"
    / "stream" / "StreamInterface.java"
)
JAVA_TEST = (
    REPO_ROOT / "src" / "test" / "java" / "neqsim" / "process" / "equipment"
    / "separator" / "GospTutorialDocumentationTest.java"
)


class GospTutorialDocumentationTest(unittest.TestCase):
    """Keep the tutorial executable, discoverable, and explicit about its limits."""

    @classmethod
    def setUpClass(cls):
        cls.tutorial = TUTORIAL.read_text(encoding="utf-8")
        cls.tutorial_index = TUTORIAL_INDEX.read_text(encoding="utf-8")
        cls.reference_index = REFERENCE_INDEX.read_text(encoding="utf-8")
        cls.system_interface = SYSTEM_INTERFACE.read_text(encoding="utf-8")
        cls.stream_interface = STREAM_INTERFACE.read_text(encoding="utf-8")
        cls.java_test = JAVA_TEST.read_text(encoding="utf-8")

    def test_front_matter_and_fences_are_well_formed(self):
        self.assertTrue(self.tutorial.startswith("---\n"))
        closing = self.tutorial.find("\n---\n", 4)
        self.assertGreater(closing, 4)
        body = self.tutorial[closing + 5 :]
        self.assertIsNone(re.search(r"(?m)^# ", body))
        self.assertEqual(4, len(re.findall(r"(?m)^```", self.tutorial)))
        self.assertEqual(1, len(re.findall(r"(?m)^```java$", self.tutorial)))

    def test_tbp_and_vpcr4_api_contracts_match_current_source(self):
        self.assertRegex(
            self.system_interface,
            r"(?s)@param molarMass.*?kg/mol.*?void addTBPfraction",
        )
        self.assertIn(
            "double getRVP(double referenceTemperature, String unit, String returnUnit);",
            self.stream_interface,
        )
        for call in (
            'addTBPfraction("C11", 0.050, 0.150, 0.78)',
            'addTBPfraction("C15", 0.040, 0.210, 0.82)',
            'addTBPfraction("C20", 0.060, 0.350, 0.88)',
            'getRVP(37.8, "C", "bara")',
        ):
            self.assertIn(call, self.tutorial)
            self.assertIn(call, self.java_test)

    def test_example_has_focused_execution_coverage(self):
        for token in (
            "SystemSrkCPAstatoil",
            "ThreePhaseSeparator",
            "ThrottlingValve",
            "ProcessSystem",
            "relativeMassBalanceError",
            "getWaterOutStream",
            "getGasOutStream",
            "getOilOutStream",
        ):
            self.assertIn(token, self.tutorial)
            self.assertIn(token, self.java_test)
        self.assertIn("relativeMassBalanceError <= 1.0e-3", self.java_test)

    def test_removed_fragments_and_unsupported_claims_do_not_return(self):
        stale_patterns = (
            r"System\.(?:out|err)",
            r'addTBPfraction\("C11",\s*0\.05,\s*150\.0',
            r"RVP \(est\)",
            r"Typical North Sea oil",
            r"<\s*30\s*mg/L",
        )
        for pattern in stale_patterns:
            self.assertIsNone(re.search(pattern, self.tutorial, re.IGNORECASE))
        self.assertIn("does not replace a qualified laboratory result", self.tutorial)
        self.assertIn("Do not apply generic RVP", self.tutorial)

    def test_relative_links_resolve_to_repository_files(self):
        link_targets = re.findall(r"\[[^]]+\]\(([^)]+)\)", self.tutorial)
        self.assertGreaterEqual(len(link_targets), 5)
        for target in link_targets:
            if "://" in target or target.startswith("#"):
                continue
            path_text = target.split("#", 1)[0]
            resolved = (TUTORIAL.parent / path_text).resolve()
            self.assertTrue(resolved.is_file(), f"Broken tutorial link: {target}")

    def test_tutorial_is_discoverable_with_exact_file_links(self):
        self.assertIn(
            "[Gas-Oil Separation Plant (GOSP)](gosp_tutorial.md)",
            self.tutorial_index,
        )
        self.assertIn(
            "[docs/tutorials/gosp_tutorial.md](tutorials/gosp_tutorial.md)",
            self.reference_index,
        )


if __name__ == "__main__":
    unittest.main()
