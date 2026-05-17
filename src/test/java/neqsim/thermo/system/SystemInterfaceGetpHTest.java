package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Tests for the getpH() convenience method on SystemInterface.
 *
 * @author Copilot
 */
class SystemInterfaceGetpHTest extends neqsim.NeqSimTest {

  /**
   * Test pH of pure water system with electrolytes (should be near 7.0).
   */
  @Test
  void testPureWaterpH() {
    SystemInterface water = new SystemElectrolyteCPAstatoil(273.15 + 25.0, 1.0);
    water.addComponent("water", 10.0);
    water.addComponent("Na+", 0.001);
    water.addComponent("Cl-", 0.001);
    water.chemicalReactionInit();
    water.createDatabase(true);
    water.setMixingRule(10);
    water.init(0);

    ThermodynamicOperations ops = new ThermodynamicOperations(water);
    ops.TPflash();
    water.init(2);

    double pH = water.getpH();
    // Dilute NaCl solution pH should be around 7.0
    assertTrue(pH > 4.0, "Dilute NaCl water pH should be > 4 but was " + pH);
    assertTrue(pH < 10.0, "Dilute NaCl water pH should be < 10 but was " + pH);
  }

  /**
   * Test pH with dissolved CO2 (should be acidic, pH &lt; 7).
   */
  @Test
  void testCO2WaterpH() {
    SystemInterface system = new SystemElectrolyteCPAstatoil(273.15 + 25.0, 10.0);
    system.addComponent("CO2", 0.1);
    system.addComponent("water", 10.0);
    system.chemicalReactionInit();
    system.createDatabase(true);
    system.setMixingRule(10);
    system.setMultiPhaseCheck(true);
    system.init(0);

    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    system.init(2);

    double pH = system.getpH();
    // CO2 dissolved in water should lower pH
    assertFalse(Double.isNaN(pH), "pH should not be NaN when aqueous phase exists");
    assertTrue(pH > 1.0, "CO2 water pH should be > 1 but was " + pH);
    assertTrue(pH < 8.0, "CO2 water pH should be < 8 but was " + pH);
  }

  /**
   * Test pH returns NaN when no aqueous phase exists.
   */
  @Test
  void testNonAqueousSystemReturnsNaN() {
    SystemInterface gas = new SystemSrkEos(273.15 + 25.0, 50.0);
    gas.addComponent("methane", 0.90);
    gas.addComponent("ethane", 0.10);
    gas.setMixingRule("classic");

    ThermodynamicOperations ops = new ThermodynamicOperations(gas);
    ops.TPflash();

    double pH = gas.getpH();
    assertTrue(Double.isNaN(pH), "Non-aqueous system should return NaN for pH");
  }
}
