"""Regression contracts for the absorber and stripper documentation."""

import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
GUIDE = ROOT / "docs" / "process" / "equipment" / "absorbers.md"
SIMPLE_SOURCE = (
    ROOT
    / "src"
    / "main"
    / "java"
    / "neqsim"
    / "process"
    / "equipment"
    / "absorber"
    / "SimpleAbsorber.java"
)
ABSORPTION_SOURCE = SIMPLE_SOURCE.with_name("AbsorptionColumn.java")
STRIPPING_SOURCE = SIMPLE_SOURCE.with_name("StrippingColumn.java")
ABSORPTION_TEST = (
    ROOT
    / "src"
    / "test"
    / "java"
    / "neqsim"
    / "process"
    / "equipment"
    / "absorber"
    / "AbsorptionColumnTest.java"
)
STRIPPING_TEST = ABSORPTION_TEST.with_name("StrippingColumnTest.java")


class AbsorberDocumentationTest(unittest.TestCase):
    """Keep the tray-column guide aligned with source and executable regressions."""

    @classmethod
    def setUpClass(cls):
        cls.guide = GUIDE.read_text(encoding="utf-8")
        cls.simple_source = SIMPLE_SOURCE.read_text(encoding="utf-8")
        cls.absorption_source = ABSORPTION_SOURCE.read_text(encoding="utf-8")
        cls.stripping_source = STRIPPING_SOURCE.read_text(encoding="utf-8")
        cls.absorption_test = ABSORPTION_TEST.read_text(encoding="utf-8")
        cls.stripping_test = STRIPPING_TEST.read_text(encoding="utf-8")
        cls.scoped_guide = cls.guide.split("## Simple TEG Absorber", maxsplit=1)[0]

    def test_jekyll_structure_headings_and_fences(self):
        self.assertRegex(self.guide, r"\A---\n(?:.|\n)*?\n---\n")
        prose = re.sub(r"```.*?```", "", self.guide, flags=re.DOTALL)
        self.assertNotRegex(prose, r"(?m)^# ")
        self.assertEqual(0, self.guide.count("```") % 2)

        scoped_prose = re.sub(r"```.*?```", "", self.scoped_guide, flags=re.DOTALL)
        headings = re.findall(r"(?m)^##+ (.+)$", scoped_prose)
        self.assertEqual(len(headings), len(set(headings)))

    def test_legacy_simple_absorber_boundary_matches_source(self):
        self.assertIn(
            "public SimpleAbsorber(String name, StreamInterface inStream1)",
            self.simple_source,
        )
        self.assertNotRegex(
            self.simple_source,
            r"public SimpleAbsorber\(String name,\s*StreamInterface \w+,\s*"
            r"StreamInterface \w+\)",
        )
        self.assertNotIn("new SimpleAbsorber(", self.scoped_guide)
        self.assertIn("legacy single-feed MDEA/CO2 shortcut", self.scoped_guide)
        self.assertIn(
            "there is no three-argument gas-plus-solvent constructor",
            self.scoped_guide,
        )
        self.assertIn(
            "current `run()` method does not turn those values",
            self.scoped_guide,
        )

    def test_rigorous_examples_define_every_feed_and_unit(self):
        for required in (
            "SystemSrkCPAstatoil gasFluid =",
            "SystemSrkCPAstatoil tegFluid =",
            'Stream wetGas = new Stream("wet feed gas", gasFluid)',
            'Stream leanTeg = new Stream("lean TEG", tegFluid)',
            "SystemSrkCPA gasFluid =",
            "SystemSrkCPA liquidFluid =",
            'Stream strippingGas = new Stream("methanol stripping gas", gasFluid)',
            'Stream richLiquid = new Stream("methanol rich water", liquidFluid)',
        ):
            self.assertIn(required, self.scoped_guide)

        for call in re.findall(
            r"set(?:FlowRate|Temperature|Pressure)\(([^\n]+)\)",
            self.scoped_guide,
        ):
            self.assertIn(",", call)

        self.assertNotIn("System.out.", self.scoped_guide)

    def test_absorption_example_matches_public_api_and_regression(self):
        for token in (
            "public AbsorptionColumn(String name, int numberOfTrays)",
            "public void addGasInStream(StreamInterface stream)",
            "public void addSolventInStream(StreamInterface stream)",
            "public void setComponentMurphreeEfficiency(int trayNumber, "
            "String componentName, double efficiency)",
        ):
            self.assertIn(token, self.absorption_source)

        for token in (
            'gasFluid.setMixingRule(10)',
            'tegFluid.setMixingRule(10)',
            'absorber.setComponentMurphreeEfficiency(trayNumber, "water", '
            "0.70)",
            "absorber.getConvergenceDiagnostics()",
            'absorber.getMassBalance("kg/hr")',
            "treatedGasWater < wetGasWater",
        ):
            self.assertIn(token, self.scoped_guide)

        for token in (
            "assertConvergedAndConservative",
            "componentFlow(treatedGas, componentName)",
            "componentMoleFraction",
        ):
            self.assertIn(token, self.absorption_test)

    def test_stripping_example_matches_public_api_and_regression(self):
        for token in (
            "public StrippingColumn(String name, int numberOfTrays)",
            "public void addStrippingGasStream(StreamInterface stream)",
            "public void addRichLiquidStream(StreamInterface stream)",
            "public StreamInterface getOverheadGasStream()",
            "public StreamInterface getLeanLiquidStream()",
        ):
            self.assertIn(token, self.stripping_source)

        for token in (
            "DistillationColumn.SolverType.MESH_RESIDUAL",
            'stripper.setComponentMurphreeEfficiency(trayNumber, "methanol", '
            "0.70)",
            "stripper.getConvergenceDiagnostics()",
            'stripper.getMassBalance("kg/hr")',
            "overheadMethanol > inletGasMethanol",
        ):
            self.assertIn(token, self.scoped_guide)

        for token in (
            "assertAcceptedAndConservative",
            "getLastMeshResidualNorm()",
            "componentFlow(overheadGas, componentName)",
            "strippedFraction",
        ):
            self.assertIn(token, self.stripping_test)

    def test_model_selection_states_solver_and_duty_boundaries(self):
        for statement in (
            "No tray hydraulics, reactions, entrainment, or flooding",
            "Fixed tray temperatures imply unreported heating or cooling",
            "Removal efficiencies are inputs, not reaction-rate predictions",
            "`getEnergyBalanceError()` is a convergence diagnostic, not an "
            "equipment-duty result",
        ):
            self.assertIn(statement, self.scoped_guide)


if __name__ == "__main__":
    unittest.main()
