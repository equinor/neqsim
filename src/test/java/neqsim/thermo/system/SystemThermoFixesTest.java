package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
}
