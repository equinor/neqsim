import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
GUIDE_PATH = ROOT / "docs" / "development" / "python_extension_patterns.md"
NAMED_INTERFACE_PATH = (
    ROOT / "src" / "main" / "java" / "neqsim" / "util" / "NamedInterface.java"
)


class PythonExtensionPatternsDocumentationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.guide = GUIDE_PATH.read_text(encoding="utf-8")
        cls.named_interface = NAMED_INTERFACE_PATH.read_text(encoding="utf-8")

    def test_front_matter_title_is_not_duplicated_as_h1(self):
        body = self.guide.split("---", 2)[2]
        prose = re.sub(r"```.*?```", "", body, flags=re.DOTALL)
        self.assertNotRegex(prose, r"(?m)^# ")

    def test_internal_see_also_links_are_source_safe_and_resolve(self):
        targets = [
            target
            for target in re.findall(r"\]\(([^)]+)\)", self.guide)
            if not target.startswith(("#", "http://", "https://"))
        ]
        self.assertEqual(len(targets), 3)
        for target in targets:
            self.assertTrue(target.endswith(".md"), target)
            self.assertTrue((GUIDE_PATH.parent / target).is_file(), target)

    def test_removed_proxy_examples_do_not_reference_invalid_contracts(self):
        self.assertNotIn(
            "@JImplements('neqsim.util.CalculationInterface')",
            self.guide,
        )
        self.assertIn(
            "`neqsim.util.CalculationInterface` does not exist",
            self.guide,
        )
        self.assertNotIn(
            "@JImplements('neqsim.thermo.component.ComponentInterface')",
            self.guide,
        )
        self.assertNotIn(
            "@JImplements('neqsim.physicalproperties.ViscosityInterface')",
            self.guide,
        )
        self.assertIn(
            "neqsim.physicalproperties.methods.methodinterface.ViscosityInterface",
            self.guide,
        )

    def test_named_interface_proxy_implements_every_abstract_method(self):
        abstract_methods = set(
            re.findall(
                r"(?m)^  public (?:String|void) (\w+)\(",
                self.named_interface,
            )
        )
        self.assertEqual(
            abstract_methods,
            {"getName", "setName", "getTagNumber", "setTagNumber"},
        )
        self.assertIn(
            '@JImplements("neqsim.util.NamedInterface")',
            self.guide,
        )
        for method in abstract_methods:
            self.assertIn(f"def {method}(", self.guide)

    def test_proxy_boundary_is_explicit(self):
        for contract in (
            "must implement every abstract method",
            "cannot extend a concrete Java class",
            "It is not a process unit",
            "Implement reusable components",
        ):
            self.assertIn(contract, self.guide)


if __name__ == "__main__":
    unittest.main()
