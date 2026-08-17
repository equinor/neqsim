import re
import unittest
from pathlib import Path
from urllib.parse import unquote


DOCS_DIR = Path(__file__).resolve().parent
LANDING_PAGE = DOCS_DIR / "index.md"
PACKAGE_INDEX = DOCS_DIR / "README.md"
WIKI_INDEX = DOCS_DIR / "wiki" / "index.md"
POM = DOCS_DIR.parent / "pom.xml"


def extract_fence(content, language):
    match = re.search(rf"```{language}\n(.*?)```", content, flags=re.DOTALL)
    if match is None:
        raise AssertionError(f"Missing {language} example")
    return match.group(1)


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
    elif raw_target.suffix:
        candidates.append(raw_target)
    else:
        candidates.extend((raw_target, raw_target.with_suffix(".md")))

    for candidate in candidates:
        if candidate.is_file():
            return candidate.resolve(), fragment
    raise AssertionError(f"Unresolved link from {source_path}: {destination}")


class LandingPageDocumentationContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.documents = {
            LANDING_PAGE: LANDING_PAGE.read_text(encoding="utf-8"),
            PACKAGE_INDEX: PACKAGE_INDEX.read_text(encoding="utf-8"),
            WIKI_INDEX: WIKI_INDEX.read_text(encoding="utf-8"),
        }

    def test_front_matter_title_structure_and_fences(self):
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

    def test_internal_links_and_fragments_resolve(self):
        markdown_links = re.compile(r"(?<!!)\[[^\]]+\]\(([^)]+)\)")
        html_links = re.compile(r'href="([^"]+)"')
        for source_path, content in self.documents.items():
            destinations = markdown_links.findall(content) + html_links.findall(content)
            for destination in destinations:
                if destination.startswith(("http://", "https://", "mailto:")):
                    continue
                target_path, fragment = resolve_internal_target(source_path, destination)
                if fragment:
                    with self.subTest(source=source_path.name, destination=destination):
                        self.assertIn(
                            fragment,
                            heading_slugs(target_path.read_text(encoding="utf-8")),
                        )

    def test_wiki_uses_current_release_and_api_destinations(self):
        wiki = self.documents[WIKI_INDEX]
        current_version = re.search(
            r"<revision>([^<]+)</revision>",
            POM.read_text(encoding="utf-8"),
        )
        self.assertIsNotNone(current_version)
        self.assertIn(
            f"<version>{current_version.group(1)}</version>",
            wiki,
        )
        self.assertIn(
            "https://github.com/equinor/neqsim/releases",
            wiki,
        )
        self.assertIn(
            "https://equinor.github.io/neqsim/javadoc/index.html",
            wiki,
        )
        self.assertNotIn("equinor/neqsimsource", wiki)
        self.assertNotIn("htmlpreview.github.io", wiki)
        self.assertNotIn("equinor/neqsimhome/blob", wiki)

    def test_shared_java_example_is_complete_and_repository_safe(self):
        landing_example = extract_fence(self.documents[LANDING_PAGE], "java")
        package_example = extract_fence(self.documents[PACKAGE_INDEX], "java")
        self.assertEqual(landing_example, package_example)
        self.assertIn("public final class NeqSimQuickStart", landing_example)
        self.assertIn("public static void main(String[] args)", landing_example)
        self.assertIn("LogManager.getLogger", landing_example)
        self.assertNotIn("System.out", landing_example)
        self.assertNotIn("System.err", landing_example)

    def test_python_example_uses_public_gateway(self):
        example = extract_fence(self.documents[LANDING_PAGE], "python")
        self.assertIn("from neqsim import jneqsim", example)
        self.assertIn("jneqsim.thermo.system.SystemSrkEos", example)
        self.assertIn(
            "jneqsim.thermodynamicoperations.ThermodynamicOperations",
            example,
        )
        self.assertNotIn("from neqsim.thermo", example)
        self.assertNotIn("jpype", example)


if __name__ == "__main__":
    unittest.main()
