import re
import unittest
from pathlib import Path
from urllib.parse import unquote


DOCS_DIR = Path(__file__).resolve().parent
REPOSITORY_ROOT = DOCS_DIR.parent
MIXING_RULE_GUIDE = DOCS_DIR / "thermo" / "mixing_rules_guide.md"
THERMODYNAMIC_MODELS = DOCS_DIR / "thermo" / "thermodynamic_models.md"
MIXING_RULE_HANDLER = (
    REPOSITORY_ROOT
    / "src/main/java/neqsim/thermo/mixingrule/EosMixingRuleHandler.java"
)

CLASSIC_T_EQUATION = (
    r"k_{ij}(T) = k_{ij,0} + k_{ij,T} "
    r"\left(\frac{T}{273.15\ \mathrm{K}} - 1\right)"
)
CLASSIC_T2_EQUATION = (
    r"k_{ij}(T) = k_{ij,0} + \frac{k_{ij,T}}{T}"
)


def heading_slugs(content):
    content_without_fences = re.sub(
        r"```.*?```",
        "",
        content,
        flags=re.DOTALL,
    )
    return {
        re.sub(r"[^a-z0-9 -]", "", heading.lower())
        .strip()
        .replace(" ", "-")
        for heading in re.findall(
            r"^#{1,6}\s+(.+)$",
            content_without_fences,
            flags=re.MULTILINE,
        )
    }


def resolve_internal_target(source_path, destination):
    target, _, fragment = unquote(destination).partition("#")
    if not target:
        return source_path, fragment

    target_path = (source_path.parent / target).resolve()
    if not target_path.is_file():
        raise AssertionError(
            "Unresolved link from {}: {}".format(source_path, destination)
        )
    return target_path, fragment


class MixingRulesDocumentationContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.documents = {
            MIXING_RULE_GUIDE: MIXING_RULE_GUIDE.read_text(encoding="utf-8"),
            THERMODYNAMIC_MODELS: THERMODYNAMIC_MODELS.read_text(
                encoding="utf-8"
            ),
        }
        cls.mixing_rule_handler = MIXING_RULE_HANDLER.read_text(
            encoding="utf-8"
        )

    def test_structure_and_internal_links_are_source_safe(self):
        markdown_links = re.compile(r"(?<!!)\[[^\]]+\]\(([^)]+)\)")

        for source_path, content in self.documents.items():
            content_without_fences = re.sub(
                r"```.*?```",
                "",
                content,
                flags=re.DOTALL,
            )
            with self.subTest(source=source_path.name):
                self.assertTrue(content.startswith("---\n"))
                self.assertEqual(content.count("```") % 2, 0)
                self.assertNotRegex(
                    content_without_fences,
                    re.compile(r"^# ", re.MULTILINE),
                )

            for destination in markdown_links.findall(content):
                if destination.startswith(
                    ("http://", "https://", "mailto:")
                ):
                    continue

                target, _, fragment = destination.partition("#")
                if target:
                    with self.subTest(
                        source=source_path.name,
                        destination=destination,
                    ):
                        self.assertTrue(
                            target.endswith(".md"),
                            "Documentation source links must include .md",
                        )

                target_path, resolved_fragment = resolve_internal_target(
                    source_path,
                    destination,
                )
                if fragment:
                    with self.subTest(
                        source=source_path.name,
                        destination=destination,
                    ):
                        self.assertEqual(fragment, resolved_fragment)
                        self.assertIn(
                            resolved_fragment,
                            heading_slugs(
                                target_path.read_text(encoding="utf-8")
                            ),
                        )

    def test_temperature_dependent_equations_match_current_source(self):
        source_contracts = (
            "return new ClassicSRKT();",
            "return new ClassicSRKT(1);",
            (
                "return intparam[i][j] + intparamT[i][j] "
                "* (temperature / 273.15 - 1.0);"
            ),
            (
                "return intparam[i][j] + intparamT[i][j] "
                "/ temperature;"
            ),
        )
        for source_contract in source_contracts:
            with self.subTest(source_contract=source_contract):
                self.assertIn(source_contract, self.mixing_rule_handler)

        self.assertRegex(
            self.mixing_rule_handler,
            re.compile(
                r"else if \(i == 8\).*?return new ClassicSRKT\(\);",
                flags=re.DOTALL,
            ),
        )
        self.assertRegex(
            self.mixing_rule_handler,
            re.compile(
                r"else if \(i == 12\).*?return new ClassicSRKT\(1\);",
                flags=re.DOTALL,
            ),
        )

        for source_path, content in self.documents.items():
            with self.subTest(source=source_path.name):
                self.assertIn(CLASSIC_T_EQUATION, content)
                self.assertIn(CLASSIC_T2_EQUATION, content)
                self.assertNotIn(
                    r"k_{ij,0} + k_{ij,T} \cdot T",
                    content,
                )

    def test_parameter_units_and_custom_example_are_unambiguous(self):
        guide = self.documents[MIXING_RULE_GUIDE]
        models = self.documents[THERMODYNAMIC_MODELS]

        for content in (guide, models):
            with self.subTest(document="guide" if content is guide else "models"):
                self.assertIn("273.15 K", content)
                self.assertIn("dimensionless", content)
                self.assertIn("units of kelvin", content)

        for documented_call in (
            "temperatureDependentFluid.setMixingRule("
            "EosMixingRuleType.CLASSIC_T);",
            "temperatureDependentRule.setBinaryInteractionParameter(",
            "temperatureDependentRule.setBinaryInteractionParameterT1(",
        ):
            with self.subTest(documented_call=documented_call):
                self.assertIn(documented_call, guide)

        self.assertIn(
            "fluid.setMixingRule(EosMixingRuleType.CLASSIC_T);",
            models,
        )


if __name__ == "__main__":
    unittest.main()
