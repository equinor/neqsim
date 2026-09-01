import re
import unittest
from pathlib import Path
from urllib.parse import unquote


DOCS_DIR = Path(__file__).resolve().parent
REPOSITORY_ROOT = DOCS_DIR.parent
THERMO_OVERVIEW = DOCS_DIR / "thermo" / "physical_properties.md"
THERMO_INDEX = DOCS_DIR / "thermo" / "README.md"
PACKAGE_OVERVIEW = DOCS_DIR / "physical_properties" / "README.md"
DIFFUSIVITY_GUIDE = DOCS_DIR / "physical_properties" / "diffusivity_models.md"
SYSTEM_INTERFACE = (
    REPOSITORY_ROOT
    / "src/main/java/neqsim/thermo/system/SystemInterface.java"
)
PHYSICAL_PROPERTIES = (
    REPOSITORY_ROOT
    / "src/main/java/neqsim/physicalproperties/system/PhysicalProperties.java"
)


def heading_slugs(content):
    content_without_fences = re.sub(r"```.*?```", "", content, flags=re.DOTALL)
    return {
        re.sub(r"[^a-z0-9 -]", "", heading.lower()).strip().replace(" ", "-")
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

    raw_target = source_path.parent / target
    candidates = []
    if target.endswith(".html"):
        candidates.append(raw_target.with_suffix(".md"))
    elif target.endswith("/"):
        candidates.extend((raw_target / "README.md", raw_target / "index.md"))
    else:
        candidates.append(raw_target)
        if not Path(target).suffix:
            candidates.extend(
                (
                    Path("{}.md".format(raw_target)),
                    raw_target / "README.md",
                    raw_target / "index.md",
                )
            )

    for candidate in candidates:
        if candidate.is_file():
            return candidate.resolve(), fragment
    raise AssertionError(f"Unresolved link from {source_path}: {destination}")


class PhysicalPropertiesDocumentationContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.documents = {
            THERMO_OVERVIEW: THERMO_OVERVIEW.read_text(encoding="utf-8"),
            THERMO_INDEX: THERMO_INDEX.read_text(encoding="utf-8"),
            PACKAGE_OVERVIEW: PACKAGE_OVERVIEW.read_text(encoding="utf-8"),
            DIFFUSIVITY_GUIDE: DIFFUSIVITY_GUIDE.read_text(encoding="utf-8"),
        }

    def test_front_matter_links_fragments_and_fences_are_safe(self):
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
                if destination.startswith(("http://", "https://", "mailto:")):
                    continue
                target_path, fragment = resolve_internal_target(
                    source_path,
                    destination,
                )
                if fragment:
                    with self.subTest(
                        source=source_path.name,
                        destination=destination,
                    ):
                        self.assertIn(
                            fragment,
                            heading_slugs(
                                target_path.read_text(encoding="utf-8")
                            ),
                        )

    def test_documented_api_boundary_matches_current_source(self):
        system_source = SYSTEM_INTERFACE.read_text(encoding="utf-8")
        properties_source = PHYSICAL_PROPERTIES.read_text(encoding="utf-8")
        thermo = self.documents[THERMO_OVERVIEW]
        package = self.documents[PACKAGE_OVERVIEW]
        diffusivity = self.documents[DIFFUSIVITY_GUIDE]

        for method in (
            "getInterphaseProperties()",
            "initPhysicalProperties()",
            "setPhysicalPropertyModel(PhysicalPropertyModel",
        ):
            with self.subTest(system_method=method):
                self.assertIn(method, system_source)
                self.assertIn(method, thermo + package + diffusivity)

        for method in (
            "getDiffusionCoefficient(int i, int j)",
            "getDiffusionCoefficient(String comp1, String comp2)",
            "calcEffectiveDiffusionCoefficients()",
            "getEffectiveDiffusionCoefficient(String compName)",
        ):
            with self.subTest(properties_method=method):
                self.assertIn(method, properties_source)

        self.assertIn(
            'getDiffusionCoefficient("methane", "nitrogen")',
            diffusivity,
        )
        self.assertIn("calcEffectiveDiffusionCoefficients()", diffusivity)
        self.assertIn("getEffectiveDiffusionCoefficient(component)", diffusivity)
        self.assertIn(
            "getInterphaseProperties().getSurfaceTension(0, 1)",
            thermo,
        )

    def test_stale_api_patterns_do_not_return(self):
        combined = "\n".join(self.documents.values())
        for stale_pattern in (
            'initPhysicalProperties("AMINE")',
            "getDiffusivityCalc(",
            ".diffusivityCalc",
            "getDiffusionCoefficient()",
            "getInterfacialTension(",
        ):
            with self.subTest(stale_pattern=stale_pattern):
                self.assertNotIn(stale_pattern, combined)

        self.assertNotIn("setMixingRule(7)", self.documents[THERMO_OVERVIEW])
        self.assertIn(
            "../physical_properties/README",
            self.documents[THERMO_INDEX],
        )


if __name__ == "__main__":
    unittest.main()
