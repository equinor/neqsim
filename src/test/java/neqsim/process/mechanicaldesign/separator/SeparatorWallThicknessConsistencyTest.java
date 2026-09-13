package neqsim.process.mechanicaldesign.separator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.process.equipment.separator.GasScrubber;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.mechanicaldesign.designstandards.JointEfficiencyPlateStandard;
import neqsim.process.mechanicaldesign.designstandards.MaterialPlateDesignStandard;
import neqsim.process.mechanicaldesign.designstandards.PressureVesselDesignStandard;
import neqsim.thermo.system.SystemSrkEos;

/** Regression coverage for issue #3677: one sizing pass must describe one vessel. */
class SeparatorWallThicknessConsistencyTest extends neqsim.NeqSimTest {
  private Separator createSeparator(double initialDiameter, boolean scrubber, boolean liquidRich) {
    SystemSrkEos fluid = new SystemSrkEos(313.15, 20.0);
    fluid.addComponent("methane", liquidRich ? 0.1 : 0.8);
    fluid.addComponent("ethane", liquidRich ? 0.0 : 0.05);
    fluid.addComponent("propane", liquidRich ? 0.0 : 0.05);
    fluid.addComponent("n-decane", liquidRich ? 0.9 : 0.1);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(100000.0, "kg/hr");
    feed.run();
    Separator separator = scrubber ? new GasScrubber("scrubber", feed) : new Separator("separator", feed);
    if (!scrubber) {
      separator.setOrientation("horizontal");
    }
    separator.setInternalDiameter(initialDiameter);
    separator.addSeparatorSection("mesh", "mesh");
    separator.run();
    assertEquals(2, separator.getThermoSystem().getNumberOfPhases(), "Fixture must contain gas and liquid");
    SeparatorMechanicalDesign design = separator.getMechanicalDesign();
    design.setCompanySpecificDesignStandards("default");
    design.setMaxOperationPressure(25.0);
    design.setMaxOperationTemperature(80.0, "C");
    design.setCorrosionAllowance(0.0);
    return separator;
  }

  private void assertCoherentDesign(Separator separator) {
    SeparatorMechanicalDesign design = separator.getMechanicalDesign();
    double stress = ((MaterialPlateDesignStandard) design.getDesignStandard().get("material plate design codes"))
        .getDivisionClass();
    double efficiency = ((JointEfficiencyPlateStandard) design.getDesignStandard()
        .get("plate Joint Efficiency design codes")).getJEFactor();
    // ASME cylindrical-shell pressure term in metres; corrosion allowance is stored in mm.
    double pressureMPa = design.getMaxOperationPressure() / 10.0;
    double expectedThickness = pressureMPa * design.getInnerDiameter() / (2.0 * stress * efficiency - 1.2 * pressureMPa)
        + design.getCorrosionAllowance() / 1000.0;
    assertEquals(expectedThickness, design.getWallThickness(), 1e-12);
    assertEquals(design.getInnerDiameter() + 2.0 * expectedThickness, design.getOuterDiameter(), 1e-12);
    double expectedShellWeight = 0.032 * expectedThickness * 1e3 * design.getInnerDiameter() * 1e3
        * design.getTantanLength();
    assertEquals(expectedShellWeight, design.getWeigthVesselShell(), expectedShellWeight * 1e-12);
    double internalsWeight = separator.getSeparatorSections().stream()
        .mapToDouble(section -> section.getMechanicalDesign().getTotalWeight()).sum();
    assertEquals(internalsWeight, design.getWeigthInternals(), 1e-9);
    double vesselWeight = expectedShellWeight + internalsWeight + design.getWeightNozzle();
    assertEquals(0.4 * vesselWeight, design.getWeightPiping(), vesselWeight * 1e-12);
    assertEquals(0.1 * vesselWeight, design.getWeightStructualSteel(), vesselWeight * 1e-12);
    assertEquals(0.08 * vesselWeight, design.getWeightElectroInstrument(), vesselWeight * 1e-12);
    assertEquals(1.58 * vesselWeight, design.getWeightTotal(), vesselWeight * 1e-12);
  }

  @ParameterizedTest
  @ValueSource(doubles = { 0.4, 1.0, 4.0 })
  void firstPassAndRepeatedDesignUseFinalDiameter(double initialDiameter) {
    Separator separator = createSeparator(initialDiameter, false, false);
    SeparatorMechanicalDesign design = separator.getMechanicalDesign();
    design.calcDesign();
    assertCoherentDesign(separator);
    assertEquals(1.440917294605802, design.getInnerDiameter(), 1e-8);
    assertEquals(0.004253002640512992, design.getWallThickness(), 1e-10);
    double thickness = design.getWallThickness();
    double weight = design.getWeightTotal();
    double length = design.getTantanLength();
    design.calcDesign();
    assertCoherentDesign(separator);
    assertEquals(thickness, design.getWallThickness(), 1e-12);
    assertEquals(weight, design.getWeightTotal(), 1e-8);
    design.setDesign();
    design.calcDesign();
    assertCoherentDesign(separator);
    assertEquals(thickness, design.getWallThickness(), 1e-12);
    assertEquals(weight, design.getWeightTotal(), 1e-8);
    assertEquals(length, design.getTantanLength(), 1e-12);
  }

  @Test
  void pressureAndCorrosionAllowanceAffectFirstPassWeights() {
    Separator separator = createSeparator(4.0, false, false);
    SeparatorMechanicalDesign design = separator.getMechanicalDesign();
    design.calcDesign();
    double initialThickness = design.getWallThickness();
    double initialWeight = design.getWeightTotal();
    design.setMaxOperationPressure(50.0);
    design.calcDesign();
    assertCoherentDesign(separator);
    assertTrue(design.getWallThickness() > initialThickness);
    assertTrue(design.getWeightTotal() > initialWeight);
    double pressureThickness = design.getWallThickness();
    double pressureWeight = design.getWeightTotal();
    design.setCorrosionAllowance(3.0);
    design.calcDesign();
    assertCoherentDesign(separator);
    assertEquals(0.003, design.getWallThickness() - pressureThickness, 1e-12);
    assertTrue(design.getWeightTotal() > pressureWeight);
  }

  @Test
  void liquidSizingOverrideUsesFinalDiameter() {
    Separator separator = createSeparator(4.0, false, true);
    SeparatorMechanicalDesign design = separator.getMechanicalDesign();
    design.calcDesign();
    // The liquid/bubble sizing branch selects L = 4D, rather than the gas branch's fallback L = 5D.
    assertEquals(4.0 * design.getInnerDiameter(), design.getTantanLength(), 1e-10);
    assertCoherentDesign(separator);
  }

  @ParameterizedTest
  @ValueSource(doubles = { 0.4, 4.0 })
  void autoSizeRefreshesWallAndWeights(double initialDiameter) {
    Separator separator = createSeparator(initialDiameter, false, false);
    separator.autoSize(1.2);
    assertCoherentDesign(separator);
    double thickness = separator.getMechanicalDesign().getWallThickness();
    double weight = separator.getMechanicalDesign().getWeightTotal();
    separator.autoSize(1.2);
    assertCoherentDesign(separator);
    assertEquals(thickness, separator.getMechanicalDesign().getWallThickness(), 1e-12);
    assertEquals(weight, separator.getMechanicalDesign().getWeightTotal(), 1e-8);
  }

  @Test
  void scrubberRefreshesWallAfterItsOwnDiameterSizing() {
    Separator separator = createSeparator(4.0, true, false);
    separator.getMechanicalDesign().calcDesign();
    assertCoherentDesign(separator);
  }

  @ParameterizedTest
  @ValueSource(strings = { "ASME - Pressure Vessel Code", "BS 5500 - Pressure Vessel", "European Code", "legacy" })
  void explicitDiameterPreservesPressureCodeAndAllowance(String standardName) {
    Separator separator = createSeparator(1.0, false, false);
    SeparatorMechanicalDesign design = separator.getMechanicalDesign();
    design.setCorrosionAllowance(3.0);
    PressureVesselDesignStandard standard = new PressureVesselDesignStandard(standardName, design);
    double originalThickness = standard.calcWallThickness();
    assertEquals(originalThickness, standard.calcWallThickness(1.0), 1e-12);
    assertEquals(2.0 * (originalThickness - 0.003) + 0.003, standard.calcWallThickness(2.0), 1e-12);
    assertEquals(1.0, separator.getInternalDiameter(), 1e-12, "Trial diameter must not mutate process geometry");
  }

  @Test
  void unsupportedCodeFailsBeforeCastingProcessEquipment() {
    PressureVesselDesignStandard standard = new PressureVesselDesignStandard("ASME-VIII-Div2",
        new Pump("pump").getMechanicalDesign());
    assertThrows(UnsupportedOperationException.class, () -> standard.calcWallThickness());
    assertThrows(UnsupportedOperationException.class, () -> standard.calcWallThickness(2.0));
  }
}
