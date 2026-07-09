package neqsim.process.corrosion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;

/**
 * Tests for {@link NorsokM506ElectrolyteBridge}, verifying that a rigorous electrolyte in-situ pH is coupled into the
 * NORSOK M-506 CO2 corrosion model.
 *
 * @author ESOL
 * @version 1.0
 */
public class NorsokM506ElectrolyteBridgeTest {

  /**
   * Builds a CO2 / water / methane / NaCl electrolyte fluid at the given conditions.
   *
   * @param temperatureC temperature in degrees Celsius
   * @param pressureBara total pressure in bara
   * @return a configured (but not yet flashed) electrolyte fluid
   */
  private SystemInterface buildBrine(double temperatureC, double pressureBara) {
    SystemInterface fluid = new SystemElectrolyteCPAstatoil(temperatureC + 273.15, pressureBara);
    // Mirrors the proven CO2CorrosionAnalyzer brine case (small ion fractions avoid numerical issues).
    fluid.addComponent("CO2", 0.10);
    fluid.addComponent("water", 0.88);
    fluid.addComponent("Na+", 0.01);
    fluid.addComponent("Cl-", 0.01);
    fluid.chemicalReactionInit();
    fluid.createDatabase(true);
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);
    return fluid;
  }

  /**
   * Verifies that the bridge extracts a free aqueous phase, uses the rigorous in-situ pH, and produces a physically
   * plausible NORSOK M-506 corrosion rate.
   */
  @Test
  void bridgeUsesRigorousPHAndProducesRate() {
    SystemInterface fluid = buildBrine(60.0, 50.0);

    NorsokM506ElectrolyteBridge bridge = new NorsokM506ElectrolyteBridge(fluid);
    bridge.setFlowVelocityMs(3.0);
    bridge.setPipeDiameterM(0.254);
    bridge.run();

    assertTrue(bridge.isFreeWaterPresent(), "a free water phase should be present");
    assertTrue(bridge.isPHFromElectrolyteModel(), "the rigorous electrolyte pH should be used");

    double pH = bridge.getInSituPH();
    assertTrue(pH > 2.0 && pH < 7.0, "in-situ pH should be in a plausible CO2-brine band, was " + pH);

    NorsokM506CorrosionRate model = bridge.getModel();
    assertNotNull(model, "the corrosion model should be created");

    double rate = model.getCorrectedCorrosionRate();
    assertTrue(rate > 0.0, "corrosion rate should be positive, was " + rate);
    assertTrue(rate < 1000.0, "corrosion rate should be a finite, sane magnitude, was " + rate);

    // The model must have adopted the bridge's in-situ pH (effective-pH override) rather than its own correlation.
    assertEquals(pH, model.getEffectivePH(), 1.0e-6, "the model effective pH should equal the rigorous in-situ pH");
  }

  /**
   * Verifies that the caller's fluid is not mutated by the bridge (the flash runs on a clone).
   */
  @Test
  void bridgeDoesNotMutateInputFluid() {
    SystemInterface fluid = buildBrine(60.0, 50.0);
    int phasesBefore = fluid.getNumberOfPhases();

    NorsokM506ElectrolyteBridge bridge = new NorsokM506ElectrolyteBridge(fluid);
    bridge.run();

    assertEquals(phasesBefore, fluid.getNumberOfPhases(),
        "the input fluid's phase count should be unchanged after run()");
    assertNotNull(bridge.toJson());
  }

  /**
   * Verifies that a dry fluid (no free water) falls back to the model's own pH correlation.
   */
  @Test
  void dryFluidFallsBackToCorrelation() {
    SystemInterface fluid = new SystemElectrolyteCPAstatoil(60.0 + 273.15, 100.0);
    fluid.addComponent("CO2", 0.02);
    fluid.addComponent("methane", 0.98);
    fluid.createDatabase(true);
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);

    NorsokM506ElectrolyteBridge bridge = new NorsokM506ElectrolyteBridge(fluid);
    bridge.run();

    assertFalse(bridge.isFreeWaterPresent(), "no free water phase should be present in a dry fluid");
    assertFalse(bridge.isPHFromElectrolyteModel(), "the correlation pH should be used when there is no free water");
    assertNotNull(bridge.getModel());
  }
}
