import re
import unittest
from pathlib import Path
from urllib.parse import unquote


DOCS_DIR = Path(__file__).resolve().parent
REPOSITORY_ROOT = DOCS_DIR.parent
OVERVIEW = DOCS_DIR / "fielddevelopment" / "README.md"
FIELD_CONCEPT = (
    REPOSITORY_ROOT
    / "src/main/java/neqsim/process/fielddevelopment/concept/FieldConcept.java"
)
OPTION_RANKER = (
    REPOSITORY_ROOT
    / "src/main/java/neqsim/process/fielddevelopment/evaluation"
    / "DevelopmentOptionRanker.java"
)
PROCESS_LINKER = (
    REPOSITORY_ROOT
    / "src/main/java/neqsim/process/fielddevelopment/facility"
    / "ConceptToProcessLinker.java"
)
RESERVOIR_EXPORTER = (
    REPOSITORY_ROOT
    / "src/main/java/neqsim/process/fielddevelopment/reservoir"
    / "ReservoirCouplingExporter.java"
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

    raw_target = source_path.parent / target
    candidates = [raw_target]
    if target.endswith("/"):
        candidates = [raw_target / "README.md", raw_target / "index.md"]
    elif not Path(target).suffix:
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
    raise AssertionError(
        "Unresolved link from {}: {}".format(source_path, destination)
    )


class FieldDevelopmentOverviewDocumentationContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.overview = OVERVIEW.read_text(encoding="utf-8")
        cls.field_concept = FIELD_CONCEPT.read_text(encoding="utf-8")
        cls.option_ranker = OPTION_RANKER.read_text(encoding="utf-8")
        cls.process_linker = PROCESS_LINKER.read_text(encoding="utf-8")
        cls.reservoir_exporter = RESERVOIR_EXPORTER.read_text(
            encoding="utf-8"
        )

    def test_structure_and_internal_links_are_source_safe(self):
        self.assertTrue(self.overview.startswith("---\n"))
        self.assertEqual(self.overview.count("```") % 2, 0)

        content_without_fences = re.sub(
            r"```.*?```",
            "",
            self.overview,
            flags=re.DOTALL,
        )
        self.assertNotRegex(
            content_without_fences,
            re.compile(r"^# ", re.MULTILINE),
        )

        markdown_links = re.compile(r"(?<!!)\[[^\]]+\]\(([^)]+)\)")
        for destination in markdown_links.findall(self.overview):
            if destination.startswith(("http://", "https://", "mailto:")):
                continue
            target_path, fragment = resolve_internal_target(
                OVERVIEW,
                destination,
            )
            with self.subTest(destination=destination):
                self.assertTrue(target_path.is_file())
                if fragment:
                    self.assertIn(
                        fragment,
                        heading_slugs(
                            target_path.read_text(encoding="utf-8")
                        ),
                    )

    def test_documented_calls_match_current_source(self):
        for signature in (
            "public static FieldConcept gasTieback(",
            "public static FieldConcept oilDevelopment(",
        ):
            with self.subTest(field_concept_signature=signature):
                self.assertIn(signature, self.field_concept)

        for declaration in (
            "public enum Criterion",
            "public static class DevelopmentOption",
            "public static class RankingResult",
            "public DevelopmentOption addOption(String name)",
            "public RankingResult rank()",
        ):
            with self.subTest(ranker_declaration=declaration):
                self.assertIn(declaration, self.option_ranker)

        self.assertIn(
            "public ProcessSystem generateProcessSystem("
            "FieldConcept concept, FidelityLevel fidelity)",
            self.process_linker.replace("\n", " "),
        )
        for signature in (
            "public VfpTable generateVfpProd("
            "String wellName, SystemInterface baseFluid, int tableNumber)",
            "public void setFormat(ExportFormat format)",
            "public String getEclipseKeywords()",
            "public void exportToFile(String filePath) throws IOException",
        ):
            with self.subTest(exporter_signature=signature):
                self.assertIn(
                    signature,
                    self.reservoir_exporter.replace("\n", " "),
                )

        for documented_call in (
            'FieldConcept.gasTieback("Demo gas tieback", 30.0, 2, 0.8)',
            "new ConceptEvaluator().evaluate(concept)",
            'ranker.addOption("FPSO")',
            "fpsoOption.setScore(Criterion.NPV, 1200.0)",
            "new TieInCapacityPlanner(host)",
            "linker.generateProcessSystem(concept, FidelityLevel.CONCEPT)",
            "process.run();",
            'exporter.generateVfpProd("PROD-A1", baseFluid, 1)',
            "exporter.exportToFile(output.toString());",
            "cost.calculateTreeCost(",
            "cost.calculateManifoldCost(",
            "cost.calculateUmbilicalCost(",
            "cost.calculateFlexiblePipeCost(",
        ):
            with self.subTest(documented_call=documented_call):
                self.assertIn(documented_call, self.overview)

    def test_stale_calls_claims_and_output_patterns_do_not_return(self):
        for stale_pattern in (
            "SystemSrkCPAstatoil(95, 320)",
            "new ReservoirCouplingExporter(processModel)",
            'generateVfpProd(1, "PROD-A1")',
            'exportToFile("vfp.inc", ExportFormat.ECLIPSE_100)',
            "System.out.",
            "same EoS parameters tuned once, used everywhere",
            "VFP tables ensure the same thermodynamics",
            "design standard compliance",
        ):
            with self.subTest(stale_pattern=stale_pattern):
                self.assertNotIn(stale_pattern, self.overview)

        self.assertIn(
            "public final class FieldDevelopmentOverviewExample",
            self.overview,
        )
        self.assertIn(
            "public static void main(String[] args) throws Exception",
            self.overview,
        )
        self.assertNotIn("import neqsim.process.fielddevelopment.*", self.overview)
        self.assertIn("screening hydrostatic/friction correlation", self.overview)
        self.assertIn(
            "do not automatically propagate a tuned fluid",
            self.overview,
        )


if __name__ == "__main__":
    unittest.main()
