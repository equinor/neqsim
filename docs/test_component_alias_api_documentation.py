"""Contracts for the documented component-name alias API."""

import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
COMPONENT_LIST = ROOT / "docs" / "thermo" / "component_list.md"
COMPONENT_PACKAGE = ROOT / "docs" / "thermo" / "component" / "README.md"
PHASE_SOURCE = ROOT / "src" / "main" / "java" / "neqsim" / "thermo" / "phase" / "Phase.java"
SYSTEM_INTERFACE = (
    ROOT
    / "src"
    / "main"
    / "java"
    / "neqsim"
    / "thermo"
    / "system"
    / "SystemInterface.java"
)
EXECUTABLE_REGRESSION = (
    ROOT
    / "src"
    / "test"
    / "java"
    / "neqsim"
    / "thermo"
    / "component"
    / "ComponentAliasApiSymmetryTest.java"
)


class ComponentAliasApiDocumentationContractTest(unittest.TestCase):
    """Keep the component guides aligned with the executable alias contract."""

    @classmethod
    def setUpClass(cls) -> None:
        cls.component_list = COMPONENT_LIST.read_text(encoding="utf-8")
        cls.component_package = COMPONENT_PACKAGE.read_text(encoding="utf-8")
        cls.phase_source = PHASE_SOURCE.read_text(encoding="utf-8")
        cls.system_interface = SYSTEM_INTERFACE.read_text(encoding="utf-8")
        cls.executable_regression = EXECUTABLE_REGRESSION.read_text(encoding="utf-8")

    def test_guides_state_symmetric_lookup_and_fail_closed_behavior(self) -> None:
        for token in (
            "Name resolution is symmetric",
            'fluid.getComponent("2,2,4-trimethylpentane")',
            'fluid.getPhase(0).getComponent("isooctane")',
            'fluid.hasComponent("ISOOCTANE")',
            "setComponentCriticalParameters",
            "setBinaryInteractionParameter",
            "removeComponent",
            'getComponent("methan")',
            "returns `null`",
        ):
            with self.subTest(token=token):
                self.assertIn(token, self.component_list)

        for token in (
            "Recognized aliases, systematic names, and case variants",
            'fluid.addComponent("2,2,4-trimethylpentane", 1.0)',
            'fluid.getComponent("isooctane")',
            'fluid.getPhase(0).getComponent("ISOOCTANE")',
            "`hasComponent(String)`",
            "near-miss inputs are not guessed",
            "../component_list.md#component-name-resolution",
        ):
            with self.subTest(token=token):
                self.assertIn(token, self.component_package)

    def test_guides_remain_renderable(self) -> None:
        for path, content in (
            (COMPONENT_LIST, self.component_list),
            (COMPONENT_PACKAGE, self.component_package),
        ):
            with self.subTest(path=path):
                self.assertTrue(content.startswith("---\n"))
                front_matter = content.split("---", 2)[1]
                self.assertRegex(front_matter, r"(?m)^title:\s*\S")
                self.assertRegex(front_matter, r"(?m)^description:\s*\S")
                self.assertEqual(0, content.count("```") % 2)
                visible_markdown = re.sub(
                    r"```.*?```",
                    "",
                    content,
                    flags=re.DOTALL,
                )
                self.assertEqual(
                    0,
                    len(re.findall(r"(?m)^#\s+", visible_markdown)),
                    "Jekyll supplies the visible page title; a Markdown H1 duplicates it",
                )

    def test_documented_resolution_matches_current_source(self) -> None:
        for token in (
            "public ComponentInterface getComponent(String name)",
            "ComponentInterface.getComponentNameFromAlias(name)",
            "if (!resolved.equals(name))",
            "return null;",
            "public boolean hasComponent(String name, boolean normalized)",
        ):
            with self.subTest(token=token):
                self.assertIn(token, self.phase_source)

        self.assertIn(
            "return getPhase(0).getComponent(name);",
            self.system_interface,
        )

    def test_existing_executable_regression_covers_every_documented_api(self) -> None:
        for token in (
            "aSynonymReachesTheComponentItCreated",
            "hasComponentAndGetComponentAgree",
            "anUnknownNameIsNotGuessed",
            "otherNameTakingMethodsAcceptTheSynonym",
            "setComponentCriticalParameters",
            "setBinaryInteractionParameter",
            "removeComponent",
        ):
            with self.subTest(token=token):
                self.assertIn(token, self.executable_regression)


if __name__ == "__main__":
    unittest.main()
