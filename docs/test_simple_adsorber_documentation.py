"""Source-linked contracts for the legacy SimpleAdsorber documentation boundary."""

import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
GUIDE = ROOT / "docs" / "process" / "equipment" / "adsorbers.md"
EQUIPMENT_INDEX = ROOT / "docs" / "process" / "equipment" / "README.md"
REFERENCE_INDEX = ROOT / "docs" / "REFERENCE_MANUAL_INDEX.md"
ADSORPTION_GUIDE = ROOT / "docs" / "process" / "equipment" / "adsorption_bed.md"
SIMPLE_SOURCE = (
    ROOT
    / "src"
    / "main"
    / "java"
    / "neqsim"
    / "process"
    / "equipment"
    / "adsorber"
    / "SimpleAdsorber.java"
)
SIMPLE_TEST = (
    ROOT
    / "src"
    / "test"
    / "java"
    / "neqsim"
    / "process"
    / "equipment"
    / "adsorber"
    / "SimpleAdsorberTest.java"
)
BED_SOURCE = SIMPLE_SOURCE.with_name("AdsorptionBed.java")
BED_TEST = SIMPLE_TEST.with_name("AdsorptionBedTest.java")


class SimpleAdsorberDocumentationContractTest(unittest.TestCase):
    """Keep the legacy boundary and maintained replacement routes source-accurate."""

    @classmethod
    def setUpClass(cls):
        cls.guide = GUIDE.read_text(encoding="utf-8")
        cls.equipment_index = EQUIPMENT_INDEX.read_text(encoding="utf-8")
        cls.reference_index = REFERENCE_INDEX.read_text(encoding="utf-8")
        cls.adsorption_guide = ADSORPTION_GUIDE.read_text(encoding="utf-8")
        cls.simple_source = SIMPLE_SOURCE.read_text(encoding="utf-8")
        cls.simple_test = SIMPLE_TEST.read_text(encoding="utf-8")
        cls.bed_source = BED_SOURCE.read_text(encoding="utf-8")
        cls.bed_test = BED_TEST.read_text(encoding="utf-8")

    def test_guide_is_a_non_executable_legacy_boundary(self):
        self.assertRegex(
            self.guide,
            r'^---\ntitle: "[^"]+"\ndescription: "[^"]+"\n---\n',
        )
        self.assertNotRegex(self.guide, r"(?m)^# ")
        self.assertNotIn("```", self.guide)

        required = (
            "legacy, currently unqualified MDEA-loading prototype",
            "It is not NeqSim's solid-adsorption model",
            "Do not use it for new engineering work",
            "outlet 0 must not be labelled treated or sweet gas",
            "setAproachToEquilibrium(double)",
            "does not read those fields",
            "intentionally contains no runnable `SimpleAdsorber` example",
            "[Adsorption beds](adsorption_bed)",
            "[Absorbers and strippers](absorbers)",
        )
        for phrase in required:
            with self.subTest(phrase=phrase):
                self.assertIn(phrase, self.guide)

    def test_stale_calls_and_engineering_claims_are_rejected(self):
        rejected = (
            "setAbsorptionEfficiency(",
            ".getOutStream(",
            "System.out",
            "StreamInterface treatedGas",
            "StreamInterface sweetGas",
            "AdsorberMechanicalDesign design =",
            "Vessel diameter:",
            "Vessel height:",
        )
        for phrase in rejected:
            with self.subTest(phrase=phrase):
                self.assertNotIn(phrase, self.guide)

    def test_legacy_status_matches_current_source_and_test(self):
        source_contracts = (
            "public StreamInterface getOutletStream(int i)",
            "@Deprecated\n  public StreamInterface getOutStream(int i)",
            "public void setAproachToEquilibrium(double eff)",
            "this.inStream[0] = inStream1;",
            "this.inStream[1] = inStream1;",
            "outStream[0] = inStream1.clone();",
            "outStream[1] = inStream1.clone();",
            "return new AdsorberMechanicalDesign(this);",
        )
        for contract in source_contracts:
            with self.subTest(contract=contract):
                self.assertIn(contract, self.simple_source)

        self.assertNotIn("setAbsorptionEfficiency(", self.simple_source)
        self.assertIn("@Disabled(", self.simple_test)
        self.assertIn("SimpleAdsorber is fixed", self.simple_test)
        self.assertIn("void testRun()", self.simple_test)

        run_body = self.simple_source.split("public void run(UUID id)", 1)[1].split(
            "public void displayResult()", 1
        )[0]
        for inactive in (
            "numberOfStages",
            "numberOfTheoreticalStages",
            "stageEfficiency",
            "HTU",
            "NTU",
        ):
            with self.subTest(inactive=inactive):
                self.assertNotIn(inactive, run_body)

    def test_maintained_replacement_is_source_and_test_backed(self):
        self.assertIn(
            "public class AdsorptionBed extends TwoPortEquipment", self.bed_source
        )
        self.assertIn(
            "public AdsorptionBed(String name, StreamInterface inletStream)",
            self.bed_source,
        )
        self.assertIn(
            "public void setAdsorbentMaterial(String material)", self.bed_source
        )
        self.assertIn("class AdsorptionBedTest", self.bed_test)
        self.assertIn("@Test", self.bed_test)
        self.assertNotIn("@Disabled", self.bed_test)

    def test_navigation_labels_preserve_the_boundary(self):
        self.assertRegex(
            self.equipment_index,
            r"\| Adsorbers \(legacy boundary\) \| "
            r"\[adsorbers\.md\]\(adsorbers\) \|",
        )
        self.assertIn(
            "| Legacy SimpleAdsorber boundary     | "
            "[docs/process/equipment/adsorbers.md]",
            self.reference_index,
        )
        self.assertIn(
            "[Legacy SimpleAdsorber boundary](adsorbers.md)",
            self.adsorption_guide,
        )

    def test_repository_links_resolve(self):
        for target in re.findall(r"\[[^\]]+\]\(([^)#]+)(?:#[^)]+)?\)", self.guide):
            resolved = (GUIDE.parent / target).resolve()
            candidates = (
                resolved,
                resolved.with_suffix(".md"),
                resolved / "README.md",
                resolved / "index.md",
            )
            with self.subTest(target=target):
                self.assertTrue(
                    any(candidate.is_file() for candidate in candidates), target
                )


if __name__ == "__main__":
    unittest.main()
