"""Hermetic contracts for the transient slug/separator-control example guide."""

import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DOC = ROOT / "docs" / "examples" / "transient_slug_separator_control_example.md"
SOURCE = (
    ROOT
    / "examples"
    / "neqsim"
    / "process"
    / "controllerdevice"
    / "TransientSlugSeparatorControlExample.java"
)
EXAMPLES_INDEX = ROOT / "docs" / "examples" / "index.md"
REFERENCE_INDEX = ROOT / "docs" / "REFERENCE_MANUAL_INDEX.md"


class TransientSlugSeparatorControlDocumentationContractTest(unittest.TestCase):
    """Guard source accuracy, structure, discoverability, and model boundaries."""

    @classmethod
    def setUpClass(cls):
        cls.doc = DOC.read_text(encoding="utf-8")
        cls.source = SOURCE.read_text(encoding="utf-8")

    def test_jekyll_structure_and_internal_links(self):
        self.assertRegex(
            self.doc,
            r'^---\ntitle: "[^"]+"\ndescription: "[^"]+"\n---\n',
        )
        self.assertNotRegex(self.doc, r"(?m)^# ")
        self.assertNotIn("【F:", self.doc)
        self.assertNotIn("t...", self.doc)

        for target in re.findall(r"\[[^\]]+\]\(([^)#]+\.md)(?:#[^)]+)?\)", self.doc):
            resolved = (DOC.parent / target).resolve()
            self.assertTrue(resolved.is_file(), target)

    def test_documented_configuration_matches_current_source(self):
        source_patterns = (
            "new SystemSrkEos(288.15, 55.0)",
            "double baseFlowRate = 20.0;",
            "double slugPeriod = 60.0;",
            "double slugDuration = 15.0;",
            "double slugAmplitude = 0.8;",
            "inletSeparator.setInternalDiameter(2.2);",
            "inletSeparator.setSeparatorLength(7.0);",
            "double levelSetpoint = 0.50;",
            "double pressureSetpoint = 52.0;",
            "double timeStep = 1.0;",
            "int numberOfSteps = 3600;",
            "double pipeDiameter = 0.25;",
            "double pipeLength = 3000.0;",
            "process.runTransient();",
            '"--series".equals(arg)',
            '"--noplot".equals(arg)',
            "int startIndex = Math.max(0, n - 600);",
        )
        for pattern in source_patterns:
            self.assertIn(pattern, self.source)

        doc_patterns = (
            "288.15 K and 55 bara",
            "20 kg/s",
            "60-second cycle",
            "15-second event",
            "80% of base flow",
            "2.2 m internal diameter",
            "7.0 m length",
            "0.50 liquid-level setpoint",
            "52 bar transmitter setpoint",
            "3,600 transient steps at 1 s per step",
            "0.25 m diameter",
            "3,000 m `pipeLength`",
            "3,601 samples",
            "10 minutes",
        )
        normalized_doc = re.sub(r"\s+", " ", self.doc)
        for pattern in doc_patterns:
            self.assertIn(pattern, normalized_doc)
        self.assertRegex(self.doc, r"does not\s+use `pipeLength`")

    def test_model_boundaries_and_cli_are_explicit(self):
        required = (
            "not a mechanistic",
            "does not contain an inlet choke, a discretized pipe, or an elevation profile",
            "diagnostic constructions outside the NeqSim process flowsheet",
            "The standard Maven test lifecycle does not currently execute this example",
            "does not claim that the 3,600-step simulation was executed",
            "`--series`",
            "`--noplot`",
            "stores no governed reference output",
            "not universal operating targets",
        )
        normalized_doc = re.sub(r"\s+", " ", self.doc)
        for pattern in required:
            self.assertIn(pattern, normalized_doc)

        rejected = (
            "1.5 km",
            "0.2 m diameter",
            "20 sections",
            "sinusoidal elevation profile",
            "10 kg/s and 80 bara",
            "70 bara",
            "45% level",
            "10 transient steps at 0.5 s",
            "peaks above 120 bara",
            "TransientSlugSeparatorControlExampleTest",
            "src/main/java/neqsim/process/controllerdevice/TransientSlugSeparatorControlExample.java",
        )
        for pattern in rejected:
            self.assertNotIn(pattern, self.doc)

    def test_actual_source_and_both_indexes_are_linked(self):
        source_url = (
            "https://github.com/equinor/neqsim/blob/master/examples/neqsim/process/"
            "controllerdevice/TransientSlugSeparatorControlExample.java"
        )
        self.assertIn(source_url, self.doc)

        examples_index = EXAMPLES_INDEX.read_text(encoding="utf-8")
        reference_index = REFERENCE_INDEX.read_text(encoding="utf-8")
        self.assertIn("transient_slug_separator_control_example.md", examples_index)
        self.assertIn(
            "docs/examples/transient_slug_separator_control_example.md",
            reference_index,
        )


if __name__ == "__main__":
    unittest.main()
