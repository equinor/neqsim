import re
import unittest
from pathlib import Path
from urllib.parse import unquote


DOCS_DIR = Path(__file__).resolve().parent
REPOSITORY_ROOT = DOCS_DIR.parent
OVERVIEW = DOCS_DIR / "process" / "safety" / "README.md"
SAFETY_VALVE = REPOSITORY_ROOT / (
    "src/main/java/neqsim/process/equipment/valve/SafetyValve.java"
)
RUPTURE_DISK = REPOSITORY_ROOT / (
    "src/main/java/neqsim/process/equipment/valve/RuptureDisk.java"
)
BLOWDOWN_VALVE = REPOSITORY_ROOT / (
    "src/main/java/neqsim/process/equipment/valve/BlowdownValve.java"
)
ESD_VALVE = REPOSITORY_ROOT / (
    "src/main/java/neqsim/process/equipment/valve/ESDValve.java"
)
ESD_LOGIC = REPOSITORY_ROOT / (
    "src/main/java/neqsim/process/logic/esd/ESDLogic.java"
)
HIPPS_LOGIC = REPOSITORY_ROOT / (
    "src/main/java/neqsim/process/logic/hipps/HIPPSLogic.java"
)


def heading_slugs(content):
    without_fences = re.sub(r"```.*?```", "", content, flags=re.DOTALL)
    return {
        re.sub(r"[^a-z0-9 -]", "", heading.lower())
        .strip()
        .replace(" ", "-")
        for heading in re.findall(
            r"^#{1,6}\s+(.+)$",
            without_fences,
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


class ProcessSafetyOverviewDocumentationContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.overview = OVERVIEW.read_text(encoding="utf-8")
        cls.sources = {
            "SafetyValve": SAFETY_VALVE.read_text(encoding="utf-8"),
            "RuptureDisk": RUPTURE_DISK.read_text(encoding="utf-8"),
            "BlowdownValve": BLOWDOWN_VALVE.read_text(encoding="utf-8"),
            "ESDValve": ESD_VALVE.read_text(encoding="utf-8"),
            "ESDLogic": ESD_LOGIC.read_text(encoding="utf-8"),
            "HIPPSLogic": HIPPS_LOGIC.read_text(encoding="utf-8"),
        }

    def test_structure_and_internal_links_are_source_safe(self):
        self.assertTrue(self.overview.startswith("---\n"))
        self.assertEqual(self.overview.count("```") % 2, 0)
        without_fences = re.sub(
            r"```.*?```",
            "",
            self.overview,
            flags=re.DOTALL,
        )
        self.assertNotRegex(without_fences, re.compile(r"^# ", re.MULTILINE))

        markdown_links = re.compile(r"(?<!!)\[[^\]]+\]\(([^)]+)\)")
        for destination in markdown_links.findall(self.overview):
            if destination.startswith(("http://", "https://", "mailto:")):
                continue
            target, _, fragment = destination.partition("#")
            with self.subTest(destination=destination):
                if target and not target.endswith("/"):
                    self.assertTrue(target.endswith(".md"))
                target_path, resolved_fragment = resolve_internal_target(
                    OVERVIEW,
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

    def test_documented_api_matches_current_source(self):
        expected_signatures = {
            "SafetyValve": (
                "public SafetyValve(String name, StreamInterface inletStream)",
                "public void setPressureSpec(double pressureSpec)",
                "public void setFullOpenPressure(double fullOpenPressure)",
                "public void setBlowdown(double blowdownPercent)",
            ),
            "RuptureDisk": (
                "public RuptureDisk(String name, StreamInterface inletStream)",
                "public void setBurstPressure(double burstPressure)",
                "public void setFullOpenPressure(double fullOpenPressure)",
                "public void reset()",
            ),
            "BlowdownValve": (
                "public BlowdownValve(String name, StreamInterface inletStream)",
                "public void setOpeningTime(double openingTime)",
                "public void activate()",
                "public void runTransient(double dt, UUID id)",
            ),
            "ESDValve": (
                "public ESDValve(String name, StreamInterface inletStream)",
                "public void setStrokeTime(double strokeTime)",
                "public void energize()",
                "public void trip()",
            ),
            "ESDLogic": (
                "public ESDLogic(String name)",
                "public void addAction(LogicAction action, double delay)",
                "public void activate()",
                "public void execute(double timeStep)",
            ),
            "HIPPSLogic": (
                "public HIPPSLogic(String name, VotingLogic votingLogic)",
                "public void addPressureSensor(Detector sensor)",
                "public void setIsolationValve(ThrottlingValve valve)",
                "public void update(double... pressureValues)",
            ),
        }
        for class_name, signatures in expected_signatures.items():
            for signature in signatures:
                with self.subTest(class_name=class_name, signature=signature):
                    self.assertIn(signature, self.sources[class_name])

        for documented_call in (
            "safetyValve.setPressureSpec(75.0);",
            "safetyValve.setBlowdown(7.0);",
            "ruptureDisk.setBurstPressure(85.0);",
            "inletIsolation.setStrokeTime(2.0);",
            "blowdownValve.setOpeningTime(2.0);",
            "esdLogic.activate();",
            "esdLogic.execute(timeStepSeconds);",
        ):
            with self.subTest(documented_call=documented_call):
                self.assertIn(documented_call, self.overview)

    def test_quick_start_and_engineering_boundaries_are_retained(self):
        self.assertEqual(
            self.overview.count(
                "public final class ProcessSafetyOverviewQuickStart"
            ),
            1,
        )
        self.assertIn("new SafetyValve(\"PSV-100\", feed)", self.overview)
        self.assertIn("new RuptureDisk(\"RD-100\", feed)", self.overview)
        self.assertIn("new ESDLogic(\"ESD level 1\")", self.overview)
        self.assertIn("stable `UUID`", self.overview)
        self.assertIn("accountable review", self.overview)
        self.assertIn("does not size a relief device", self.overview)

    def test_stale_or_unsafe_fragments_do_not_return(self):
        for stale_pattern in (
            "System.out.println",
            "System.err.println",
            "new SafetyValve(\"PSV-100\", vessel)",
            "new RuptureDisk(\"RD-100\", vessel)",
            "setOpeningPressure(",
            "setDownstreamPressure(",
            "setOrificeSize(",
            "setDiameter(",
            "runTransient();",
            "getReliefRate(",
            "getRequiredOrificeArea(",
            "getAPIOrificeLetter(",
            "import neqsim.process.safety.HIPPS;",
            "43200 * Math.pow",
            "15 minute rule",
            "ESD-0",
        ):
            with self.subTest(stale_pattern=stale_pattern):
                self.assertNotIn(stale_pattern, self.overview)


if __name__ == "__main__":
    unittest.main()
