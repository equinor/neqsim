package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Tests for critical SystemThermo bug fixes: isPhase bounds check, reInitPhaseType,
 * getPhaseIndex/getPhaseNumberOfPhase logging, setBeta multi-phase, reset residuals.
 */
class SystemThermoFixesTest {

  @Test
  void testIsPhaseRejectsNegativeIndex() {
    SystemInterface fluid = new SystemSrkEos(300, 10);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();

    // Negative index should return false
    assertFalse(fluid.isPhase(-1), "isPhase(-1) should return false");
  }

  @Test
  void testIsPhaseRejectsOutOfBoundsIndex() {
    SystemInterface fluid = new SystemSrkEos(300, 10);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();

    int nPhases = fluid.getNumberOfPhases();
    // Index == numberOfPhases is valid when phase exists at that index
    assertTrue(fluid.isPhase(nPhases), "isPhase(numberOfPhases) should return true");
    assertFalse(fluid.isPhase(100), "isPhase(100) should return false");
  }

  @Test
  void testIsPhaseValidIndex() {
    SystemInterface fluid = new SystemSrkEos(300, 10);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();

    // Phase 0 should exist after flash
    assertTrue(fluid.isPhase(0), "isPhase(0) should be true after flash");
  }

  @Test
  void testReInitPhaseType() {
    // Multi-component system that produces two phases
    SystemInterface fluid = new SystemSrkEos(273.15 + 20, 50);
    fluid.addComponent("methane", 0.8);
    fluid.addComponent("n-pentane", 0.2);
    fluid.setMixingRule("classic");

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();

    // reInitPhaseType should not throw
    fluid.reInitPhaseType();
    assertTrue(fluid.getNumberOfPhases() >= 1, "Should have at least one phase");
  }

  @Test
  void testResetDoesNotAccumulateResiduals() {
    SystemInterface fluid = new SystemSrkEos(300, 10);
    fluid.addComponent("methane", 0.5);
    fluid.addComponent("ethane", 0.5);
    fluid.setMixingRule("classic");

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();

    // Reset should clean up without leaving floating-point residuals
    fluid.reset();

    // After reset, total moles should be very close to zero
    double totalMoles = fluid.getTotalNumberOfMoles();
    assertTrue(Math.abs(totalMoles) < 1e-30,
        "After reset, total moles should be effectively zero, got " + totalMoles);
  }

  @Test
  void testSetBetaTwoPhase() {
    // Two-phase system: setBeta(double) should work normally
    SystemInterface fluid = new SystemSrkEos(273.15 + 20, 50);
    fluid.addComponent("methane", 0.7);
    fluid.addComponent("n-pentane", 0.3);
    fluid.setMixingRule("classic");

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();

    if (fluid.getNumberOfPhases() >= 2) {
      double original = fluid.getBeta();
      fluid.setBeta(0.5);
      assertEquals(0.5, fluid.getBeta(), 1e-10,
          "setBeta should set value correctly for two-phase system");
    }
  }

  @Test
  void testGetPhaseIndexReturnsValidPhase() {
    SystemInterface fluid = new SystemSrkEos(300, 10);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();

    // Should return a valid index for gas phase
    int gasIdx = fluid.getPhaseIndex("gas");
    assertTrue(gasIdx >= 0, "Gas phase index should be non-negative");
  }

  @Test
  void testGetPhaseIndexForMissingPhase() {
    // Pure gas system — no aqueous phase
    SystemInterface fluid = new SystemSrkEos(300, 10);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();

    // Request aqueous phase — should return 0 (fallback) but not crash
    int idx = fluid.getPhaseIndex("aqueous");
    assertTrue(idx >= 0, "Should return a valid fallback index");
  }

  /**
   * Verifies that extracting a gas phase after density ordering preserves cached EOS derivatives.
   */
  @Test
  void testPhaseToSystemGasPreservesThermodynamicPropertiesAfterPhaseIndexSwap() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 50.0, 100.0);
    fluid.addComponent("nitrogen", 0.005);
    fluid.addComponent("CO2", 0.010);
    fluid.addComponent("methane", 0.450);
    fluid.addComponent("ethane", 0.150);
    fluid.addComponent("propane", 0.150);
    fluid.addComponent("i-butane", 0.080);
    fluid.addComponent("n-butane", 0.080);
    fluid.addComponent("i-pentane", 0.040);
    fluid.addComponent("n-pentane", 0.035);
    fluid.addComponent("n-hexane", 0.030);
    fluid.setMixingRule("classic");

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();
    fluid.init(3);

    assertEquals(2, fluid.getNumberOfPhases(), "Rich gas should be two-phase at this condition");
    assertNotEquals(0, fluid.getPhaseIndex(0),
        "Regression condition requires swapped phase storage");

    double referenceKappa = fluid.getPhase(0).getKappa();
    double referenceDensity = fluid.getPhase(0).getDensity("kg/m3");
    double referenceEnthalpy = fluid.getPhase(0).getEnthalpy();

    SystemInterface gasSystem = fluid.phaseToSystem("gas");

    assertEquals(referenceKappa, gasSystem.getPhase(0).getKappa(), 1e-10,
        "Extracted gas kappa should match the source gas phase");
    assertEquals(referenceDensity, gasSystem.getPhase(0).getDensity("kg/m3"), 1e-8,
        "Extracted gas density should match the source gas phase");
    assertEquals(referenceEnthalpy, gasSystem.getPhase(0).getEnthalpy(), 1e-6,
        "Extracted gas enthalpy should match the source gas phase");
  }
}
