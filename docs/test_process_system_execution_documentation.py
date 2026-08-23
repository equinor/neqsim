import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
GUIDE_PATH = ROOT / "docs" / "process" / "processmodel" / "process_system.md"
SOURCE_PATH = (
    ROOT
    / "src"
    / "main"
    / "java"
    / "neqsim"
    / "process"
    / "processmodel"
    / "ProcessSystem.java"
)
JAVA_TEST_PATH = (
    ROOT
    / "src"
    / "test"
    / "java"
    / "neqsim"
    / "process"
    / "processmodel"
    / "ProcessSystemTest.java"
)


class ProcessSystemExecutionDocumentationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.guide = GUIDE_PATH.read_text(encoding="utf-8")
        cls.source = SOURCE_PATH.read_text(encoding="utf-8")
        cls.java_tests = JAVA_TEST_PATH.read_text(encoding="utf-8")

    def test_front_matter_title_is_not_duplicated_as_h1(self):
        body = self.guide.split("---", 2)[2]
        prose = re.sub(r"```.*?```", "", body, flags=re.DOTALL)
        self.assertNotRegex(prose, r"(?m)^# ")

    def test_direct_execution_method_arities_match_source(self):
        for signature in (
            r"public synchronized void runDataflow\(UUID id\)",
            r"public synchronized void runHybrid\(UUID id\)",
            r"public void runSequential\(UUID id\)",
            r"public synchronized void runTransient\(double dt, UUID id\)",
        ):
            self.assertRegex(self.source, signature)

        for invalid_call in (
            "process.runDataflow();",
            "process.runHybrid();",
            "process.runSequential();",
            "process.runTransient(dt);",
            "process.runTransient(dt, (time)",
        ):
            self.assertNotIn(invalid_call, self.guide)

        self.assertIn(
            "There are no zero-argument `runDataflow()`, `runHybrid()`, or "
            "`runSequential()` overloads.",
            self.guide,
        )

    def test_optimized_dispatch_rules_are_source_anchored(self):
        for source_contract in (
            "if (hasAdjusters())",
            "} else if (hasRecycles())",
            "if (shouldUseDataflowExecution())",
            "runParallel(id);",
        ):
            self.assertIn(source_contract, self.source)

        for guide_contract in (
            "An `Adjuster` or `MultiVariableAdjuster` selects",
            "A `Recycle` with no adjuster selects",
            "A feed-forward graph that is sufficiently large",
            "Other feed-forward graphs select",
            "getExecutionStrategyExplanation()",
        ):
            self.assertIn(guide_contract, self.guide)

    def test_shared_stream_serialization_matches_regressions(self):
        for regression in (
            "testRunParallelSerializesSingleInputConsumersSharingStream",
            "testRunDataflowSerializesSingleInputConsumersSharingStream",
            "testRunParallelKeepsDistinctInputConsumersParallel",
        ):
            self.assertIn(regression, self.java_tests)

        for guide_contract in (
            "share the same mutable `StreamInterface` object",
            "run sequentially",
            "distinct output stream objects",
            "thermodynamic cloning and initialization",
        ):
            self.assertIn(guide_contract, self.guide)

    def test_parallel_transient_is_documented_as_opt_in(self):
        self.assertIn("private boolean parallelTransientEnabled = false;", self.source)
        self.assertIn(
            "public synchronized void setParallelTransientEnabled(boolean enabled)",
            self.source,
        )
        self.assertIn("Transient equipment execution is sequential by default.", self.guide)
        self.assertIn("Parallel transient equipment stepping is opt-in:", self.guide)
        self.assertIn("process.setParallelTransientEnabled(true);", self.guide)
        self.assertIn("process.runTransient(timestepSeconds, calculationId);", self.guide)

    def test_unreproducible_performance_table_is_removed(self):
        for stale_claim in (
            "Performance gains",
            "| Regular `run()` | 464 ms",
            "| `runOptimized()` | 286 ms",
            "28% graph-based",
        ):
            self.assertNotIn(stale_claim, self.guide)
        self.assertIn(
            "Benchmark the actual flowsheet on its target JVM and hardware",
            self.guide,
        )


if __name__ == "__main__":
    unittest.main()
