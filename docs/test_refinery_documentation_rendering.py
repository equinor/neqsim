"""Protect refinery guide rendering, executable examples, and navigation."""

import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
ASSAY_GUIDE = ROOT / "docs" / "thermo" / "characterization" / "refinery_assay.md"
WORKFLOW_GUIDE = (
    ROOT
    / "docs"
    / "thermo"
    / "characterization"
    / "refinery_big_hill_atmospheric_vacuum_workflow.md"
)
SECTION_INDEX = ROOT / "docs" / "thermo" / "characterization" / "README.md"
REFERENCE_INDEX = ROOT / "docs" / "REFERENCE_MANUAL_INDEX.md"
INTEGRATION_TEST = (
    ROOT
    / "src"
    / "test"
    / "java"
    / "neqsim"
    / "process"
    / "equipment"
    / "distillation"
    / "DoeBigHillAtmosphericVacuumIntegrationTest.java"
)


def _front_matter_and_body(text):
    """Return front matter and body for a documentation page."""
    match = re.match(r"\A---\n(.*?)\n---\n(.*)\Z", text, re.DOTALL)
    if match is None:
        raise AssertionError("documentation page must have YAML front matter")
    return match.group(1), match.group(2)


def _math_fragments(text):
    """Return inline and display math after excluding fenced examples."""
    prose = re.sub(r"(?:```|~~~).*?(?:```|~~~)", "", text, flags=re.DOTALL)
    return re.findall(r"\$\$.*?\$\$|(?<!\$)\$(?!\$).*?(?<!\$)\$(?!\$)", prose, re.DOTALL)


class RefineryDocumentationRenderingTest(unittest.TestCase):
    """Keep merged refinery guides renderable, executable, and discoverable."""

    @classmethod
    def setUpClass(cls):
        cls.assay = ASSAY_GUIDE.read_text(encoding="utf-8")
        cls.workflow = WORKFLOW_GUIDE.read_text(encoding="utf-8")
        cls.section_index = SECTION_INDEX.read_text(encoding="utf-8")
        cls.reference_index = REFERENCE_INDEX.read_text(encoding="utf-8")
        cls.integration_test = INTEGRATION_TEST.read_text(encoding="utf-8")

    def test_front_matter_supplies_each_page_title(self):
        """Reject a body H1 when searchable front matter already owns the title."""
        for page in (self.assay, self.workflow):
            front_matter, body = _front_matter_and_body(page)
            self.assertRegex(front_matter, r"(?m)^title:\s+.+$")
            self.assertRegex(front_matter, r"(?m)^description:\s+.+$")
            self.assertNotRegex(body, r"(?m)^#\s+")

    def test_temperature_moment_math_uses_valid_compact_katex(self):
        """Protect the four repaired moment and Watson expressions."""
        expected = (
            r"$$M_{T,V}=\sum_i \Delta V_i \overline{T}_i$$",
            r"$\overline{T}_i=(T_{\mathrm{lower},i}+T_{\mathrm{upper},i})/2$",
            (
                r"$$\overline{T}_{V,\mathrm{range}}="
                r"\frac{M_{T,V}}{\sum_i \Delta V_i}$$"
            ),
            (
                r"$K_W=(1.8\overline{T}_{V,\mathrm{range}})^{1/3}"
                r"/SG_{\mathrm{range}}$"
            ),
        )
        for expression in expected:
            self.assertIn(expression, self.assay)

        for page in (self.assay, self.workflow):
            self.assertNotRegex(page, r"\\\(|\\\)|\\\[|\\\]")
            for fragment in _math_fragments(page):
                if fragment.startswith("$$"):
                    self.assertFalse(fragment.startswith("$$\n"))
                    self.assertFalse(fragment.endswith("\n$$"))
                self.assertNotRegex(
                    fragment,
                    r"(?<!\\)\b(?:sum|Delta|overline)\b",
                )

    def test_workflow_examples_are_anchored_in_java_regression(self):
        """Keep every public call in the workflow tied to merged executable coverage."""
        for call in ("createFromAtmosphericBottoms(", "vacuum.getColumn().run("):
            self.assertIn(call, self.workflow)
            self.assertIn(call, self.integration_test)
        sensitivity_call = (
            r"DoeBigHillVacuumReboilerTemperatureSensitivity\s*\.run\("
        )
        self.assertRegex(self.workflow, sensitivity_call)
        self.assertRegex(self.integration_test, sensitivity_call)
        self.assertIn(
            "connectedFrontEndClosesMaterialEnergyAndUtilityReceipts",
            self.integration_test,
        )
        self.assertIn(
            "connectedVacuumSensitivityQualifiesIndependentPoints",
            self.integration_test,
        )

    def test_both_guides_are_discoverable_from_both_indexes(self):
        """Require section and global navigation to resolve to maintained pages."""
        links = (
            (
                "refinery_assay",
                "thermo/characterization/refinery_assay.md",
                ASSAY_GUIDE,
            ),
            (
                "refinery_big_hill_atmospheric_vacuum_workflow",
                (
                    "thermo/characterization/"
                    "refinery_big_hill_atmospheric_vacuum_workflow.md"
                ),
                WORKFLOW_GUIDE,
            ),
        )
        for section_target, reference_target, destination in links:
            self.assertIn(f"]({section_target})", self.section_index)
            self.assertIn(f"]({reference_target})", self.reference_index)
            self.assertTrue(destination.is_file())


if __name__ == "__main__":
    unittest.main()

