package neqsim.process.mechanicaldesign.distillation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.absorber.AbsorptionColumn;
import neqsim.process.equipment.absorber.StrippingColumn;
import neqsim.process.equipment.distillation.DistillationColumn;
import neqsim.process.equipment.distillation.PackedColumn;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests the shared mechanical-design contract across the gas-liquid column family.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class DistillationColumnMechanicalDesignTest {

  /**
   * Checks first-call sizing against independently initialized bottom-tray liquid properties.
   */
  @Test
  void absorberSizingInitializesOutletProperties() {
    SystemSrkEos gas = new SystemSrkEos(303.15, 15.0);
    String[] components = {"methane", "ethane", "propane", "n-butane", "n-pentane", "n-heptane"};
    double[] fractions = {0.920, 0.040, 0.025, 0.010, 0.005, 0.0};
    for (int i = 0; i < components.length; i++) {
      gas.addComponent(components[i], fractions[i]);
    }
    gas.setMixingRule(2);
    Stream richGas = new Stream("gas", gas);
    richGas.setFlowRate(2000.0, "kg/hr");
    richGas.run();

    SystemSrkEos solvent = new SystemSrkEos(293.15, 15.0);
    for (String component : components) {
      solvent.addComponent(component, "n-heptane".equals(component) ? 1.0 : 0.0);
    }
    solvent.setMixingRule(2);
    Stream leanOil = new Stream("solvent", solvent);
    leanOil.setFlowRate(600.0, "kg/hr");
    leanOil.run();

    AbsorptionColumn absorber = new AbsorptionColumn("absorber", 5);
    absorber.addGasInStream(richGas);
    absorber.addSolventInStream(leanOil);
    absorber.setTopPressure(15.0);
    absorber.setBottomPressure(15.0);
    for (int trayNumber = 0; trayNumber < absorber.getNumberOfTrays(); trayNumber++) {
      absorber.getTray(trayNumber).setOutletTemperature(298.15);
    }
    absorber.setTemperatureTolerance(1.0e-4);
    ProcessSystem process = new ProcessSystem();
    process.add(richGas);
    process.add(leanOil);
    process.add(absorber);
    process.run();
    assertTrue(absorber.solved(), absorber.getConvergenceDiagnostics());

    SystemInterface liquidReference = absorber.getTray(0).getLiquidOutStream().getFluid().clone();
    liquidReference.initProperties();
    double liquidDensity = liquidReference.getDensity("kg/m3");
    double liquidMassFlow = absorber.getTray(0).getLiquidOutStream().getFlowRate("kg/hr");
    assertTrue(liquidDensity > 500.0 && liquidDensity < 900.0, "Lean-oil density must be physical");

    DistillationColumnMechanicalDesign design = new DistillationColumnMechanicalDesign(absorber);
    design.calcDesign();
    double firstLoading = design.getWeirLoading();
    double firstPressureDrop = design.getTrayPressureDrop();
    assertTrue(Double.isFinite(firstLoading) && firstLoading > 0.0);
    // Default weir length is 0.7 times the preliminary tray-sizing diameter; obtain it from JSON
    // because the integrated internals rating may subsequently update the public column diameter.
    double weirLength = com.google.gson.JsonParser.parseString(design.toJson()).getAsJsonObject()
        .getAsJsonObject("trayDesign").get("weirLength_m").getAsDouble();
    assertEquals(liquidMassFlow / liquidDensity / weirLength, firstLoading, 1.0e-8);
    assertEquals(5.0 + 0.05 * liquidDensity * 9.81 / 100.0, firstPressureDrop, 1.0e-8);
    assertTrue(Double.isFinite(design.getFloodingFactor()));

    design.calcDesign();
    assertEquals(firstLoading, design.getWeirLoading(), 1.0e-8, "Sizing must not depend on call order");
    assertEquals(firstPressureDrop, design.getTrayPressureDrop(), 1.0e-8);
  }

  /**
   * Confirms absorbers, strippers, packed contactors, and distillation columns share the capacity design API.
   */
  @Test
  void columnFamilyUsesSharedMechanicalDesign() {
    DistillationColumn distillation = new DistillationColumn("distillation", 3, false, false);
    AbsorptionColumn absorber = new AbsorptionColumn("absorber", 3);
    StrippingColumn stripper = new StrippingColumn("stripper", 3);
    PackedColumn packedContactor = new PackedColumn("packed contactor", false, false);

    assertInstanceOf(DistillationColumnMechanicalDesign.class, distillation.getMechanicalDesign());
    assertInstanceOf(DistillationColumnMechanicalDesign.class, absorber.getMechanicalDesign());
    assertInstanceOf(DistillationColumnMechanicalDesign.class, stripper.getMechanicalDesign());
    assertInstanceOf(DistillationColumnMechanicalDesign.class, packedContactor.getMechanicalDesign());

    DistillationColumnMechanicalDesign stripperDesign = (DistillationColumnMechanicalDesign) stripper
        .getMechanicalDesign();
    stripperDesign.setContactorInternalsType("valve");
    assertEquals("valve", stripperDesign.getContactorInternalsType());
  }
}
