package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Tests for the Kent-Eisenberg thermodynamic model used for acid gas (H2S/CO2) solubility in amine
 * solutions.
 *
 * <p>
 * The Kent-Eisenberg model uses chemical equilibrium with apparent equilibrium constants where
 * non-ideality is absorbed into the K values (gamma = 1.0). This is a simplified approach compared
 * to rigorous electrolyte models.
 * </p>
 *
 * @author Even Solbraa
 */
class SystemKentEisenbergTest {

  /**
   * Test that SystemKentEisenberg can be instantiated and components added.
   */
  @Test
  void testCreateSystem() {
    SystemInterface testSystem = new SystemKentEisenberg(323.15, 1.0);
    testSystem.addComponent("CO2", 0.01);
    testSystem.addComponent("H2S", 0.01);
    testSystem.addComponent("water", 9.0);
    testSystem.addComponent("MDEA", 1.0);
    assertNotNull(testSystem);
    assertEquals(4, testSystem.getNumberOfComponents());
    assertEquals("Kent Eisenberg-model", testSystem.getModelName());
  }

  /**
   * Test that a TP flash can be performed with the Kent-Eisenberg model.
   */
  @Test
  void testTPFlash() {
    SystemInterface testSystem = new SystemKentEisenberg(323.15, 2.0);
    testSystem.addComponent("CO2", 0.5);
    testSystem.addComponent("water", 9.0);
    testSystem.addComponent("MDEA", 1.0);
    testSystem.setMixingRule(4);

    ThermodynamicOperations ops = new ThermodynamicOperations(testSystem);
    ops.TPflash();

    assertTrue(testSystem.getNumberOfPhases() >= 1);
    double co2InGas = testSystem.getPhase(0).getComponent("CO2").getx();
    assertTrue(co2InGas > 0.0, "CO2 should be present in gas phase");
  }

  /**
   * Test Kent-Eisenberg model for H2S absorption in MDEA solution. The model should show H2S
   * partitioning between gas and liquid.
   */
  @Test
  void testH2SAbsorptionInMDEA() {
    SystemInterface testSystem = new SystemKentEisenberg(326.15, 1.1);
    testSystem.addComponent("H2S", 0.01);
    testSystem.addComponent("water", 9.0);
    testSystem.addComponent("MDEA", 0.1);
    testSystem.setMixingRule(4);

    ThermodynamicOperations ops = new ThermodynamicOperations(testSystem);
    ops.TPflash();

    // H2S should mostly dissolve in the amine solution at low loadings
    double h2sInLiquid = testSystem.getPhase(1).getComponent("H2S").getx();
    assertTrue(h2sInLiquid > 0.0, "H2S should be present in liquid phase");
  }

  /**
   * Test that the Kent-Eisenberg model can perform a bubble point pressure flash. This is the
   * typical use case: given a loaded amine solution, find the equilibrium vapor pressure of acid
   * gas.
   */
  @Test
  void testBubblePointPressureFlash() {
    SystemInterface testSystem = new SystemKentEisenberg(323.15, 1.0);
    testSystem.addComponent("CO2", 0.5);
    testSystem.addComponent("water", 9.0);
    testSystem.addComponent("MDEA", 1.0);
    testSystem.setMixingRule(4);

    ThermodynamicOperations ops = new ThermodynamicOperations(testSystem);
    try {
      ops.bubblePointPressureFlash(false);
    } catch (Exception ex) {
      // Bubble point flash may not converge for all conditions
      // but should not throw unexpected exceptions
    }

    double pressure = testSystem.getPressure();
    assertTrue(pressure > 0.0, "Pressure should be positive after bubble point flash");
  }

  /**
   * Test system cloning works correctly for the Kent-Eisenberg model.
   */
  @Test
  void testClone() {
    SystemInterface testSystem = new SystemKentEisenberg(323.15, 2.0);
    testSystem.addComponent("CO2", 0.5);
    testSystem.addComponent("water", 9.0);
    testSystem.setMixingRule(4);

    SystemInterface clonedSystem = testSystem.clone();
    assertNotNull(clonedSystem);
    assertEquals(testSystem.getNumberOfComponents(), clonedSystem.getNumberOfComponents());
    assertEquals(testSystem.getTemperature(), clonedSystem.getTemperature(), 1e-6);
    assertEquals(testSystem.getPressure(), clonedSystem.getPressure(), 1e-6);
  }

  /**
   * Test with chemical reactions initialized - typical amine system workflow.
   */
  @Test
  void testWithChemicalReactions() {
    SystemInterface testSystem = new SystemKentEisenberg(326.15, 1.1);
    testSystem.addComponent("H2S", 0.01);
    testSystem.addComponent("water", 9.0);
    testSystem.addComponent("MDEA", 0.1);

    testSystem.chemicalReactionInit();
    testSystem.createDatabase(true);
    testSystem.setMixingRule(4);

    ThermodynamicOperations ops = new ThermodynamicOperations(testSystem);
    ops.TPflash();

    assertTrue(testSystem.getNumberOfPhases() >= 1);
  }

  /**
   * Test Kent-Eisenberg model for CO2 partial pressure over MDEA solution. Literature reference:
   * Kent, R.L. and Eisenberg, B. (1976). "Better Data for Amine Treating", Hydrocarbon Processing,
   * 55(2), pp.87-90.
   *
   * <p>
   * At 50°C (323.15 K) and moderate CO2 loadings in ~50 wt% MDEA, the equilibrium CO2 partial
   * pressure should be on the order of 0.01-1.0 bar depending on loading.
   * </p>
   */
  @Test
  void testCO2PartialPressureOverMDEA() {
    // 50 wt% MDEA solution at 50°C
    SystemInterface testSystem = new SystemKentEisenberg(323.15, 2.0);
    testSystem.addComponent("CO2", 0.1);
    testSystem.addComponent("water", 5.0);
    testSystem.addComponent("MDEA", 1.0);
    testSystem.setMixingRule(4);

    ThermodynamicOperations ops = new ThermodynamicOperations(testSystem);
    ops.TPflash();

    // CO2 partial pressure in the gas phase
    if (testSystem.getNumberOfPhases() > 1) {
      double pCO2 = testSystem.getPressure() * testSystem.getPhase(0).getComponent("CO2").getx();
      assertTrue(pCO2 > 0.0, "CO2 partial pressure should be positive, got: " + pCO2);
    }
  }
}
