import re
import unittest
from pathlib import Path
from urllib.parse import unquote


DOCS_DIR = Path(__file__).resolve().parent
REPOSITORY_ROOT = DOCS_DIR.parent
EXTERNAL_GUIDE = DOCS_DIR / "integration" / "EXTERNAL_OPTIMIZER_INTEGRATION.md"
OVERVIEW = DOCS_DIR / "process" / "optimization" / "OPTIMIZATION_OVERVIEW.md"
PROCESS_EVALUATOR = (
    REPOSITORY_ROOT
    / "src/main/java/neqsim/process/util/optimizer/ProcessSimulationEvaluator.java"
)
MODEL_EVALUATOR = (
    REPOSITORY_ROOT
    / "src/main/java/neqsim/process/util/optimizer/ProcessModelSimulationEvaluator.java"
)


class OptimizerDocumentationContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.documents = {
            EXTERNAL_GUIDE: EXTERNAL_GUIDE.read_text(encoding="utf-8"),
            OVERVIEW: OVERVIEW.read_text(encoding="utf-8"),
        }

    def test_front_matter_links_and_fences_are_repository_safe(self):
        link_pattern = re.compile(r"\[[^\]]+\]\(([^)]+)\)")
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

            for destination in link_pattern.findall(content):
                if destination.startswith(("http://", "https://", "mailto:")):
                    continue

                target_path, separator, fragment = destination.partition("#")
                if target_path:
                    relative_path = unquote(target_path)
                    resolved = (source_path.parent / relative_path).resolve()
                    with self.subTest(
                        source=source_path.name,
                        destination=destination,
                    ):
                        self.assertEqual(Path(relative_path).suffix, ".md")
                        self.assertTrue(resolved.is_file(), str(resolved))
                    target_content = resolved.read_text(encoding="utf-8")
                else:
                    target_content = content

                if separator:
                    target_without_fences = re.sub(
                        r"```.*?```",
                        "",
                        target_content,
                        flags=re.DOTALL,
                    )
                    heading_slugs = {
                        re.sub(
                            r"[^a-z0-9 -]",
                            "",
                            heading.lower(),
                        )
                        .strip()
                        .replace(" ", "-")
                        for heading in re.findall(
                            r"^#{1,6}\s+(.+)$",
                            target_without_fences,
                            flags=re.MULTILINE,
                        )
                    }
                    with self.subTest(
                        source=source_path.name,
                        destination=destination,
                    ):
                        self.assertIn(fragment, heading_slugs)

    def test_python_examples_use_the_public_neqsim_gateway(self):
        combined = "\n".join(self.documents.values())
        self.assertIn("from neqsim import jneqsim", combined)
        self.assertNotIn("neqsim.neqsimpython", combined)
        self.assertNotIn("jpype.startJVM", combined)
        self.assertNotIn("neqsim.jar", combined)
        self.assertNotIn("pip install jpype1", combined)

    def test_framework_and_evaluator_boundaries_remain_truthful(self):
        external = self.documents[EXTERNAL_GUIDE]
        self.assertNotIn("from pyomo.environ import *", external)
        self.assertNotIn("m.x[i].value", external)
        self.assertIn("does not create a live connection", external)
        self.assertIn("they do not enable result caching", external)

        process_section = external.split("### ProcessSimulationEvaluator", 1)[1]
        process_section, model_section = process_section.split(
            "### ProcessModelSimulationEvaluator",
            1,
        )
        model_section = model_section.split("### EvaluationResult", 1)[0]
        self.assertNotIn("estimateSensitivitiesWithQuality", process_section)
        self.assertIn("estimateSensitivitiesWithQuality", model_section)

        process_source = PROCESS_EVALUATOR.read_text(encoding="utf-8")
        model_source = MODEL_EVALUATOR.read_text(encoding="utf-8")
        self.assertNotIn("estimateSensitivitiesWithQuality", process_source)
        self.assertIn("estimateSensitivitiesWithQuality", model_source)


if __name__ == "__main__":
    unittest.main()
