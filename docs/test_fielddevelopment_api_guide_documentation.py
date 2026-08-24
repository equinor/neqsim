import re
import unittest
from pathlib import Path
from urllib.parse import unquote


DOCS_DIR = Path(__file__).resolve().parent
REPOSITORY_ROOT = DOCS_DIR.parent
GUIDE = DOCS_DIR / "fielddevelopment" / "API_GUIDE.md"
OVERVIEW = DOCS_DIR / "fielddevelopment" / "README.md"
REFERENCE_INDEX = DOCS_DIR / "REFERENCE_MANUAL_INDEX.md"
FIELD_CONCEPT = (
    REPOSITORY_ROOT
    / "src/main/java/neqsim/process/fielddevelopment/concept/FieldConcept.java"
)
RESERVOIR_INPUT = (
    REPOSITORY_ROOT
    / "src/main/java/neqsim/process/fielddevelopment/concept/ReservoirInput.java"
)
WELLS_INPUT = (
    REPOSITORY_ROOT
    / "src/main/java/neqsim/process/fielddevelopment/concept/WellsInput.java"
)
INFRASTRUCTURE_INPUT = (
    REPOSITORY_ROOT
    / "src/main/java/neqsim/process/fielddevelopment/concept/InfrastructureInput.java"
)
CONCEPT_EVALUATOR = (
    REPOSITORY_ROOT
    / "src/main/java/neqsim/process/fielddevelopment/evaluation/ConceptEvaluator.java"
)
CONCEPT_KPIS = (
    REPOSITORY_ROOT
    / "src/main/java/neqsim/process/fielddevelopment/evaluation/ConceptKPIs.java"
)
OPTION_RANKER = (
    REPOSITORY_ROOT
    / "src/main/java/neqsim/process/fielddevelopment/evaluation"
    / "DevelopmentOptionRanker.java"
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
    raw_target = source_path if not target else source_path.parent / target
    candidates = [raw_target]
    if target.endswith("/"):
        candidates = [raw_target / "README.md", raw_target / "index.md"]
    for candidate in candidates:
        if candidate.is_file():
            return candidate.resolve(), fragment
    raise AssertionError(
        "Unresolved link from {}: {}".format(source_path, destination)
    )


class FieldDevelopmentApiGuideDocumentationContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.guide = GUIDE.read_text(encoding="utf-8")
        cls.field_concept = FIELD_CONCEPT.read_text(encoding="utf-8")
        cls.reservoir_input = RESERVOIR_INPUT.read_text(encoding="utf-8")
        cls.wells_input = WELLS_INPUT.read_text(encoding="utf-8")
        cls.infrastructure_input = INFRASTRUCTURE_INPUT.read_text(
            encoding="utf-8"
        )
        cls.concept_evaluator = CONCEPT_EVALUATOR.read_text(encoding="utf-8")
        cls.concept_kpis = CONCEPT_KPIS.read_text(encoding="utf-8")
        cls.option_ranker = OPTION_RANKER.read_text(encoding="utf-8")

    def test_front_matter_fences_headings_and_compact_math_are_valid(self):
        self.assertTrue(self.guide.startswith("---\n"))
        self.assertEqual(self.guide.count("```"), 10)
        self.assertEqual(self.guide.count("```java"), 5)
        self.assertEqual(self.guide.count("```") % 2, 0)
        self.assertEqual(
            len(re.findall(r"^# ", self.guide, flags=re.MULTILINE)),
            1,
        )
        self.assertNotIn("\\[", self.guide)
        self.assertNotIn("\\]", self.guide)
        self.assertEqual(self.guide.count("$$"), 2)
        display_equations = re.findall(r"\$\$(.*?)\$\$", self.guide, re.DOTALL)
        self.assertEqual(len(display_equations), 1)
        for equation in display_equations:
            self.assertEqual(equation, equation.strip())

    def test_all_internal_links_and_fragments_resolve(self):
        markdown_links = re.compile(r"(?<!!)\[[^\]]+\]\(([^)]+)\)")
        for destination in markdown_links.findall(self.guide):
            if destination.startswith(("http://", "https://", "mailto:")):
                continue
            target_path, fragment = resolve_internal_target(GUIDE, destination)
            with self.subTest(destination=destination):
                self.assertTrue(target_path.is_file())
                if fragment:
                    self.assertIn(
                        fragment,
                        heading_slugs(target_path.read_text(encoding="utf-8")),
                    )

    def test_complete_example_uses_current_java8_safe_contract(self):
        required_fragments = (
            "public final class FieldDevelopmentApiGuideExample",
            "private static final Logger logger =",
            "public static void main(String[] args)",
            'FieldConcept.gasTieback("Gas tieback", 30.0, 2, 0.8)',
            'FieldConcept.oilDevelopment("Oil development", 6, 5000.0, 0.15)',
            "new ConceptEvaluator()",
            'ranker.setWeightProfile("balanced")',
            "ranking.getBestOption().getName()",
        )
        for fragment in required_fragments:
            with self.subTest(fragment=fragment):
                self.assertIn(fragment, self.guide)
        self.assertNotIn("System.out.", self.guide)
        self.assertNotIn("var ", self.guide)

    def test_core_builder_calls_are_anchored_to_current_source(self):
        declarations = {
            self.field_concept: (
                "public static FieldConcept gasTieback(",
                "public static FieldConcept oilDevelopment(",
                "public Builder id(String id)",
            ),
            self.reservoir_input: (
                "public static Builder gasCondensate()",
                "public Builder resourceUncertainty(",
                "public Builder recoveryFactor(double factor)",
            ),
            self.wells_input: (
                "public Builder tubeheadPressure(double bara)",
                "public Builder ratePerWell(double rate, String unit)",
                "public double getRatePerWellSm3d()",
            ),
            self.infrastructure_input: (
                "public static Builder subseaTieback()",
                "public Builder exportPipeline(double lengthKm, double diameterInches)",
                "public Builder hostCapacityAvailable(double fraction)",
                "public Builder exportPressure(double bara)",
            ),
        }
        for source, signatures in declarations.items():
            flattened = source.replace("\n", " ")
            for signature in signatures:
                with self.subTest(signature=signature):
                    self.assertIn(signature, flattened)

    def test_evaluator_kpi_and_ranking_ownership_matches_source(self):
        evaluator_flat = self.concept_evaluator.replace("\n", " ")
        self.assertIn(
            "public ConceptKPIs evaluate(FieldConcept concept)",
            evaluator_flat,
        )
        self.assertIn(
            "public ConceptKPIs quickScreen(FieldConcept concept)",
            evaluator_flat,
        )
        self.assertNotIn("builder.npv10(", self.concept_evaluator)
        self.assertNotIn("builder.breakEvenPrice(", self.concept_evaluator)

        for getter in (
            "getPlateauRateMsm3d()",
            "getEstimatedRecoveryPercent()",
            "getTotalCapexMUSD()",
            "getAnnualOpexMUSD()",
            "getCo2IntensityKgPerBoe()",
            "getNpv10MUSD()",
            "getBreakEvenOilPriceUSD()",
        ):
            with self.subTest(getter=getter):
                self.assertIn(getter, self.concept_kpis)

        for declaration in (
            "public void setWeightProfile(String profile)",
            "public RankingResult rank()",
            "public DevelopmentOption getBestOption()",
            "public double getWeightedScore()",
        ):
            with self.subTest(declaration=declaration):
                self.assertIn(declaration, self.option_ranker)

    def test_stale_api_catalog_claims_do_not_return_and_navigation_is_truthful(self):
        stale_patterns = (
            "all components added in the field development framework PR",
            "Detailed usage examples for all components",
            "Detailed usage examples for every class and method",
            "FieldConcept.gasDevelopment(",
            ".startYear(2028)",
            ".wellType(",
            ".completionType(CompletionType.OPEN_HOLE)",
            ".processingLocation(ProcessingLocation.PLATFORM)",
            ".powerSupply(PowerSupply.SHORE_POWER)",
            "evaluator.setOilPrice(",
            "kpis.getNpv()",
            "result.getWeightedScore(opt)",
            "new ReservoirCouplingExporter(process)",
            "new TransientWellModel()",
        )
        for stale_pattern in stale_patterns:
            with self.subTest(stale_pattern=stale_pattern):
                self.assertNotIn(stale_pattern, self.guide)
        overview = OVERVIEW.read_text(encoding="utf-8")
        reference_index = REFERENCE_INDEX.read_text(encoding="utf-8")
        self.assertNotIn(
            "Detailed usage examples for every class and method",
            overview,
        )
        self.assertNotIn(
            "Detailed usage examples for all components",
            reference_index,
        )
        self.assertIn(
            "Source-anchored concept, screening-KPI, option-ranking, unit, and engineering-boundary guide",
            overview,
        )
        self.assertIn(
            "Current concept inputs, screening KPIs, option ranking, units, and boundaries",
            reference_index,
        )


if __name__ == "__main__":
    unittest.main()
