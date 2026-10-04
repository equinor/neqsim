import re
import unittest
from pathlib import Path
from urllib.parse import unquote


DOCS_DIR = Path(__file__).resolve().parent
REPOSITORY_ROOT = DOCS_DIR.parent
SAFETY_DIR = DOCS_DIR / "safety"

PAGES = {
    "summary": SAFETY_DIR / "HIPPS_SUMMARY.md",
    "implementation": SAFETY_DIR / "hipps_implementation.md",
    "logic": SAFETY_DIR / "hipps_safety_logic.md",
}

SOURCES = {
    "HIPPSValve": REPOSITORY_ROOT
    / "src/main/java/neqsim/process/equipment/valve/HIPPSValve.java",
    "HIPPSLogic": REPOSITORY_ROOT
    / "src/main/java/neqsim/process/logic/hipps/HIPPSLogic.java",
    "VotingLogic": REPOSITORY_ROOT
    / "src/main/java/neqsim/process/logic/sis/VotingLogic.java",
    "Detector": REPOSITORY_ROOT
    / "src/main/java/neqsim/process/logic/sis/Detector.java",
}

TESTS = {
    "HIPPSValveTest": REPOSITORY_ROOT
    / "src/test/java/neqsim/process/equipment/valve/HIPPSValveTest.java",
    "ProcessSafetyOverviewDocumentationTest": REPOSITORY_ROOT
    / (
        "src/test/java/neqsim/process/safety/"
        "ProcessSafetyOverviewDocumentationTest.java"
    ),
}


def heading_slugs(content):
    return {
        re.sub(r"[^a-z0-9 -]", "", heading.lower())
        .strip()
        .replace(" ", "-")
        for heading in re.findall(
            r"^#{1,6}\s+(.+)$",
            content,
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


class HippsDocumentationContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.pages = {
            name: path.read_text(encoding="utf-8")
            for name, path in PAGES.items()
        }
        cls.sources = {
            name: path.read_text(encoding="utf-8")
            for name, path in SOURCES.items()
        }
        cls.tests = {
            name: path.read_text(encoding="utf-8")
            for name, path in TESTS.items()
        }
        cls.all_pages = "\n".join(cls.pages.values())

    def test_front_matter_and_page_structure(self):
        for name, page in self.pages.items():
            with self.subTest(page=name):
                self.assertTrue(page.startswith("---\n"))
                match = re.match(r"\A---\n(.*?)\n---\n", page, re.DOTALL)
                self.assertIsNotNone(match)
                front_matter = match.group(1)
                self.assertRegex(front_matter, r"(?m)^title: .+$")
                self.assertRegex(front_matter, r"(?m)^description: .+$")
                description = re.search(
                    r"(?m)^description: (.+)$",
                    front_matter,
                ).group(1)
                self.assertNotIn("...", description)
                self.assertNotRegex(page, re.compile(r"^# ", re.MULTILINE))
                self.assertNotIn("```", page)

    def test_internal_links_resolve(self):
        markdown_links = re.compile(r"(?<!!)\[[^\]]+\]\(([^)]+)\)")
        for name, page in self.pages.items():
            source_path = PAGES[name]
            for destination in markdown_links.findall(page):
                if destination.startswith(("http://", "https://", "mailto:")):
                    continue
                with self.subTest(page=name, destination=destination):
                    target_path, fragment = resolve_internal_target(
                        source_path,
                        destination,
                    )
                    self.assertTrue(target_path.is_file())
                    if fragment:
                        self.assertIn(
                            fragment,
                            heading_slugs(
                                target_path.read_text(encoding="utf-8")
                            ),
                        )

    def test_current_api_signatures_are_source_backed(self):
        expected = {
            "HIPPSValve": (
                "public HIPPSValve(String name, StreamInterface inletStream)",
                (
                    "public void addPressureTransmitter("
                    "MeasurementDeviceInterface transmitter)"
                ),
                "public void setVotingLogic(VotingLogic logic)",
                "public void runTransient(double dt, UUID id)",
                "public void setSILRating(int sil)",
                "public void reset()",
            ),
            "HIPPSLogic": (
                "public HIPPSLogic(String name, VotingLogic votingLogic)",
                "public void addPressureSensor(Detector sensor)",
                (
                    "public void setIsolationValve("
                    "ThrottlingValve valve)"
                ),
                "public void update(double... pressureValues)",
                "public void execute(double timeStep)",
                "public void setOverride(boolean override)",
                "public boolean reset()",
            ),
            "VotingLogic": (
                "public int getRequiredTrips()",
                "public int getTotalSensors()",
                "public boolean evaluate(int trippedCount)",
            ),
            "Detector": (
                "public void update(double measuredValue)",
                "public void setBypass(boolean bypass)",
                "public void setFaulty(boolean faulty)",
                "public void reset()",
            ),
        }

        for source_name, signatures in expected.items():
            for signature in signatures:
                with self.subTest(
                    source=source_name,
                    signature=signature,
                ):
                    self.assertIn(signature, self.sources[source_name])

    def test_enabled_regressions_are_named_as_executable_evidence(self):
        for test_name, source in self.tests.items():
            with self.subTest(test=test_name):
                self.assertIn("@Test", source)
                self.assertNotIn("@Disabled", source)
                self.assertIn(test_name, self.all_pages)

        self.assertIn(
            "public final class ProcessSafetyOverviewQuickStart",
            (
                DOCS_DIR / "process" / "safety" / "README.md"
            ).read_text(encoding="utf-8"),
        )

    def test_model_selection_and_lifecycle_boundaries_are_explicit(self):
        required_phrases = (
            "equipment-centric `HIPPSValve`",
            "logic-centric `HIPPSLogic`",
            "Do not mix the two voting enums",
            "Configuration does not demonstrate SIL achievement",
            "project-specific",
            "independent functional-safety assessment",
            "accountable approval",
            "does not calculate valve hydraulics",
            "commands the linked valve to 100-percent opening",
            "reopening is a simulation convenience",
        )
        normalized = " ".join(self.all_pages.split())
        for phrase in required_phrases:
            with self.subTest(phrase=phrase):
                self.assertIn(phrase, normalized)

    def test_stale_or_unsupported_claims_do_not_return(self):
        rejected = (
            "System.out.println",
            "System.err.println",
            "Production-ready",
            "Complete SIS modeling",
            "Full test coverage",
            "API RP 14C compliant",
            "eliminating flaring",
            "industry standard for HIPPS",
            "docs/hipps_implementation.md",
            "~700 lines",
            "~550 lines",
            "~800 lines",
            "~300 lines",
        )
        for fragment in rejected:
            with self.subTest(fragment=fragment):
                self.assertNotIn(fragment, self.all_pages)

        self.assertNotRegex(
            self.all_pages,
            re.compile(r"\b(90|95|98)%\s+(?:of\s+)?MAOP\b", re.IGNORECASE),
        )


if __name__ == "__main__":
    unittest.main()
