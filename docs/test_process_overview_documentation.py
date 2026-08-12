import re
import unittest
from pathlib import Path
from urllib.parse import unquote


DOCS_DIR = Path(__file__).resolve().parent
REPOSITORY_ROOT = DOCS_DIR.parent
PROCESS_OVERVIEW = DOCS_DIR / "process" / "README.md"
PROCESS_SYSTEM = (
    REPOSITORY_ROOT
    / "src/main/java/neqsim/process/processmodel/ProcessSystem.java"
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

    for candidate in candidates:
        if candidate.is_file():
            return candidate.resolve(), fragment
    raise AssertionError(
        "Unresolved link from {}: {}".format(source_path, destination)
    )


class ProcessOverviewDocumentationContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.overview = PROCESS_OVERVIEW.read_text(encoding="utf-8")
        cls.process_system = PROCESS_SYSTEM.read_text(encoding="utf-8")

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

            target, _, fragment = destination.partition("#")
            with self.subTest(destination=destination):
                if target and not target.endswith("/"):
                    self.assertTrue(
                        target.endswith(".md"),
                        "Documentation source links must include .md",
                    )

                target_path, resolved_fragment = resolve_internal_target(
                    PROCESS_OVERVIEW,
                    destination,
                )
                self.assertTrue(target_path.is_file())
                if fragment:
                    self.assertEqual(fragment, resolved_fragment)
                    self.assertIn(
                        resolved_fragment,
                        heading_slugs(
                            target_path.read_text(encoding="utf-8")
                        ),
                    )

    def test_process_system_claims_match_current_source(self):
        for signature in (
            "public void runOptimized()",
            "public void runParallel() throws InterruptedException",
            "public synchronized void runHybrid(UUID id)",
            "public boolean hasRecycleLoops()",
            "public String getExecutionPartitionInfo()",
            "public String[][] reportResults()",
            "public String getStreamSummaryTable()",
            "public String getReport_json()",
        ):
            with self.subTest(signature=signature):
                self.assertIn(signature, self.process_system)

        for documented_call in (
            "process.run();",
            "process.hasRecycleLoops()",
            "process.getExecutionPartitionInfo()",
            "process.getReport_json()",
            "process.reportResults()",
            "process.getStreamSummaryTable()",
        ):
            with self.subTest(documented_call=documented_call):
                self.assertIn(documented_call, self.overview)

    def test_stale_execution_and_report_patterns_do_not_return(self):
        for stale_pattern in (
            "process.runHybrid();",
            "getUnitOperationsAsTable()",
            "runTransient(time, dt)",
            "28-40%",
            "40-57%",
            "`getReport()`",
        ):
            with self.subTest(stale_pattern=stale_pattern):
                self.assertNotIn(stale_pattern, self.overview)

        self.assertIn("public final class ProcessSystemQuickStart", self.overview)
        self.assertIn("public static void main(String[] args)", self.overview)
        quick_start = self.overview.split(
            "public final class ProcessSystemQuickStart", 1
        )[1].split("```", 1)[0]
        self.assertNotIn("System.out.println", quick_start)


if __name__ == "__main__":
    unittest.main()
