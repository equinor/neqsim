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
DISTILLATION_SOURCE = (
    ROOT
    / "src"
    / "main"
    / "java"
    / "neqsim"
    / "process"
    / "equipment"
    / "distillation"
    / "DistillationColumn.java"
)
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
        cls.distillation_source = DISTILLATION_SOURCE.read_text(encoding="utf-8")
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


    def test_rigorous_capacity_methods_and_units_match_source(self):
        for token in (
            "public double getGasSuperficialVelocity()",
            "public double getGasLoadFactor()",
            "public double getMaxAllowableGasLoadFactor()",
            "public void setMaxAllowableGasLoadFactor(double",
            "public double getGasLoadFactorUtilization()",
            "public boolean isGasLoadFactorWithinDesignLimit()",
            "public double getMinimumDiameterForGasLoadLimit()",
            "public double getWettingRate()",
        ):
            self.assertIn(token, self.absorption_source)

        for token in (
            "public double getFsFactor()",
            "public double getMaxAllowableFsFactor()",
            "public void setMaxAllowableFsFactor(double",
            "public double getFsFactorUtilization()",
            "public boolean isFsFactorWithinDesignLimit()",
            "public double getMinimumDiameterForFsLimit()",
        ):
            self.assertIn(token, self.distillation_source)

        for token in (
            "getGasSuperficialVelocity()",
            "getMinimumDiameterForFsLimit()",
            "getMinimumDiameterForGasLoadLimit()",
            "liquid m³/h per m²",
            "m/s·sqrt(kg/m³)",
            "returns m/s",
        ):
            self.assertIn(token, self.scoped_guide)

    def test_capacity_equations_defaults_and_fallback_are_explicit(self):
        self.assertIn("DEFAULT_MAX_ALLOWABLE_FS_FACTOR = 3.0", self.absorption_source)
        self.assertIn(
            "DEFAULT_MAX_ALLOWABLE_GAS_LOAD_FACTOR = 0.15",
            self.absorption_source,
        )
        self.assertIn("DEFAULT_LIQUID_DENSITY = 1000.0", self.absorption_source)
        self.assertIn(
            "MIN_LIQUID_GAS_DENSITY_DIFFERENCE = 10.0",
            self.absorption_source,
        )

        for token in (
            "$F_s=v_s\\sqrt{\\rho_g}$",
            "$K_s=v_s\\sqrt{\\frac{\\rho_g}{\\rho_\\ell-\\rho_g}}$",
            "software screening defaults, not vendor guarantees",
            "substitutes 1000 kg/m³",
            "below 10 kg/m³",
        ):
            self.assertIn(token, self.scoped_guide)

    def test_capacity_sentinels_and_constraint_semantics_are_documented(self):
        for token in (
            'return 0.0;',
            '"gasLoadFactor", "m/s"',
            'setDataSource("equipment")',
        ):
            self.assertIn(token, self.absorption_source)
        for token in (
            '"fsFactor", "m/s*sqrt(kg/m3)"',
            'setDataSource("equipment")',
        ):
            self.assertIn(token, self.distillation_source)

        for token in (
            "unavailable-result sentinel, not proof of spare capacity",
            "can be `true` for an unavailable zero result",
            "update the corresponding live SOFT constraint immediately",
            '`fsFactor` and `gasLoadFactor`',
            'dataSource = "equipment"',
            "do not resize the column or rerun the process",
        ):
            self.assertIn(token, self.scoped_guide)

    def test_capacity_example_is_covered_by_executable_regression(self):
        for token in (
            "gasLoadFactorAndFsFactorAreNativelyAvailable",
            "getGasSuperficialVelocity()",
            "getMinimumDiameterForFsLimit()",
            "getMinimumDiameterForGasLoadLimit()",
            'getCapacityConstraints().containsKey("fsFactor")',
            'getCapacityConstraints().containsKey("gasLoadFactor")',
        ):
            self.assertIn(token, self.absorption_test)


if __name__ == "__main__":
    unittest.main()
