import re
import unittest
from pathlib import Path
from urllib.parse import unquote


DOCS_DIR = Path(__file__).resolve().parent
REPOSITORY_ROOT = DOCS_DIR.parent
PROCESS_OVERVIEW = DOCS_DIR / "process" / "README.md"
EQUIPMENT_OVERVIEW = DOCS_DIR / "process" / "equipment" / "README.md"
PROCESS_SYSTEM = (
    REPOSITORY_ROOT
    / "src/main/java/neqsim/process/processmodel/ProcessSystem.java"
)
PROCESS_EQUIPMENT_INTERFACE = (
    REPOSITORY_ROOT
    / "src/main/java/neqsim/process/equipment/ProcessEquipmentInterface.java"
)
PROCESS_EQUIPMENT_BASE = (
    REPOSITORY_ROOT
    / "src/main/java/neqsim/process/equipment/ProcessEquipmentBaseClass.java"
)
TWO_PORT_INTERFACE = (
    REPOSITORY_ROOT
    / "src/main/java/neqsim/process/equipment/TwoPortInterface.java"
)
TWO_PORT_EQUIPMENT = (
    REPOSITORY_ROOT
    / "src/main/java/neqsim/process/equipment/TwoPortEquipment.java"
)
SEPARATOR = (
    REPOSITORY_ROOT
    / "src/main/java/neqsim/process/equipment/separator/Separator.java"
)
SAFETY_VALVE = (
    REPOSITORY_ROOT
    / "src/main/java/neqsim/process/equipment/valve/SafetyValve.java"
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


class ProcessOverviewDocumentationContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.pages = {
            PROCESS_OVERVIEW: PROCESS_OVERVIEW.read_text(encoding="utf-8"),
            EQUIPMENT_OVERVIEW: EQUIPMENT_OVERVIEW.read_text(
                encoding="utf-8"
            ),
        }
        cls.overview = cls.pages[PROCESS_OVERVIEW]
        cls.equipment_overview = cls.pages[EQUIPMENT_OVERVIEW]
        cls.process_system = PROCESS_SYSTEM.read_text(encoding="utf-8")
        cls.process_equipment_interface = (
            PROCESS_EQUIPMENT_INTERFACE.read_text(encoding="utf-8")
        )
        cls.process_equipment_base = PROCESS_EQUIPMENT_BASE.read_text(
            encoding="utf-8"
        )
        cls.two_port_interface = TWO_PORT_INTERFACE.read_text(
            encoding="utf-8"
        )
        cls.two_port_equipment = TWO_PORT_EQUIPMENT.read_text(
            encoding="utf-8"
        )
        cls.separator = SEPARATOR.read_text(encoding="utf-8")
        cls.safety_valve = SAFETY_VALVE.read_text(encoding="utf-8")

    def test_structure_and_internal_links_are_source_safe(self):
        markdown_links = re.compile(r"(?<!!)\[[^\]]+\]\(([^)]+)\)")

        for source_path, content in self.pages.items():
            with self.subTest(source_path=source_path):
                self.assertTrue(content.startswith("---\n"))
                self.assertEqual(content.count("```") % 2, 0)

                content_without_fences = re.sub(
                    r"```.*?```",
                    "",
                    content,
                    flags=re.DOTALL,
                )
                self.assertNotRegex(
                    content_without_fences,
                    re.compile(r"^# ", re.MULTILINE),
                )

            for destination in markdown_links.findall(content):
                if destination.startswith(
                    ("http://", "https://", "mailto:")
                ):
                    continue

                _target, _, fragment = destination.partition("#")
                with self.subTest(
                    source_path=source_path,
                    destination=destination,
                ):
                    target_path, resolved_fragment = resolve_internal_target(
                        source_path,
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
            "public String getStreamSummaryTable()",
            "public String getReport_json()",
            "public synchronized void runTransient(double dt, UUID id)",
        ):
            with self.subTest(signature=signature):
                self.assertIn(signature, self.process_system)

        for documented_call in (
            "process.run();",
            "process.hasRecycleLoops()",
            "process.getExecutionPartitionInfo()",
            "process.getReport_json()",
            "process.getStreamSummaryTable()",
        ):
            with self.subTest(documented_call=documented_call):
                self.assertIn(documented_call, self.overview)

    def test_equipment_api_ownership_matches_current_source(self):
        self.assertIn(
            "implements ProcessEquipmentInterface",
            self.process_equipment_base,
        )
        self.assertIn(
            "implements TwoPortInterface",
            self.two_port_equipment,
        )
        for signature in (
            "StreamInterface getInletStream();",
            "StreamInterface getOutletStream();",
        ):
            with self.subTest(signature=signature):
                self.assertIn(signature, self.two_port_interface)

        for signature in (
            "public StreamInterface getInletStream()",
            "public StreamInterface getOutletStream()",
        ):
            with self.subTest(signature=signature):
                self.assertIn(signature, self.two_port_equipment)

        for signature in (
            "public StreamInterface getGasOutStream()",
            "public StreamInterface getLiquidOutStream()",
            "public void addStream(StreamInterface newStream)",
        ):
            with self.subTest(signature=signature):
                self.assertIn(signature, self.separator)

        for signature in (
            "public SafetyValve(String name, StreamInterface inletStream)",
            "public void setPressureSpec(double pressureSpec)",
            "public void setBlowdown(double blowdownPercent)",
        ):
            with self.subTest(signature=signature):
                self.assertIn(signature, self.safety_valve)

        for claim in (
            "`ProcessEquipmentInterface`",
            "`ProcessEquipmentBaseClass`",
            "`TwoPortInterface`",
            "`TwoPortEquipment`",
            "`Separator`",
        ):
            with self.subTest(claim=claim):
                self.assertIn(claim, self.equipment_overview)

    def test_stale_fragments_and_pseudo_code_do_not_return(self):
        combined = self.overview + self.equipment_overview
        for stale_pattern in (
            "process.runHybrid();",
            "getUnitOperationsAsTable()",
            "process.reportResults()",
            "runTransient(time, dt)",
            "EquipmentType equipment =",
            "equipment.setParameter(value)",
            "System.out.println",
            "psv.setSetPressure(",
            "new SafetyValve(\"PSV-100\", vessel)",
            "All equipment follows similar pattern",
        ):
            with self.subTest(stale_pattern=stale_pattern):
                self.assertNotIn(stale_pattern, combined)

        self.assertEqual(
            self.overview.count(
                "public final class ProcessSystemQuickStart"
            ),
            1,
        )
        self.assertIn(
            "public static void main(String[] args)",
            self.overview,
        )
        self.assertIn(
            "[process-package quick start](../README)",
            self.equipment_overview,
        )


if __name__ == "__main__":
    unittest.main()
