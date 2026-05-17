package neqsim.standards.gasquality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Tests for Standard_EN16726 - European H-gas quality corridor.
 *
 * @author ESOL
 */
class Standard_EN16726Test extends neqsim.NeqSimTest {
  static SystemInterface testSystem = null;
  static SystemInterface offSpecSystem = null;

  /**
   * Set up the test systems.
   *
   * @throws java.lang.Exception if setup fails
   */
  @BeforeAll
  static void setUpBeforeClass() {
    // On-spec natural gas
    testSystem = new SystemSrkEos(273.15 + 15.0, 1.01325);
    testSystem.addComponent("methane", 0.90);
    testSystem.addComponent("ethane", 0.05);
    testSystem.addComponent("propane", 0.02);
    testSystem.addComponent("nitrogen", 0.02);
    testSystem.addComponent("CO2", 0.01);
    testSystem.setMixingRule("classic");
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();

    // Off-spec gas with high CO2
    offSpecSystem = new SystemSrkEos(273.15 + 15.0, 1.01325);
    offSpecSystem.addComponent("methane", 0.80);
    offSpecSystem.addComponent("ethane", 0.02);
    offSpecSystem.addComponent("CO2", 0.15);
    offSpecSystem.addComponent("nitrogen", 0.03);
    offSpecSystem.setMixingRule("classic");
    testOps = new ThermodynamicOperations(offSpecSystem);
    testOps.TPflash();
  }

  /**
   * Test Wobbe index calculation for on-spec gas.
   */
  @Test
  void testCalculateOnSpec() {
    Standard_EN16726 standard = new Standard_EN16726(testSystem);
    standard.calculate();
    double wobbe = standard.getValue("SuperiorWobbeIndex");
    // Typical H-gas Wobbe: 46.44-54.72 MJ/m3
    assertTrue(wobbe > 40.0, "Wobbe should be > 40 MJ/m3 but was " + wobbe);
  }

  /**
   * Test isOnSpec for on-spec gas (transmission).
   */
  @Test
  void testIsOnSpec() {
    Standard_EN16726 standard = new Standard_EN16726(testSystem);
    standard.setNetworkType("transmission");
    standard.calculate();
    assertTrue(standard.isOnSpec(), "On-spec gas should pass EN 16726");
  }

  /**
   * Test isOnSpec for off-spec gas (high CO2).
   */
  @Test
  void testOffSpecHighCO2() {
    Standard_EN16726 standard = new Standard_EN16726(offSpecSystem);
    standard.calculate();
    assertFalse(standard.isOnSpec(), "High CO2 gas should fail EN 16726");
  }

  /**
   * Test units.
   */
  @Test
  void testUnits() {
    Standard_EN16726 standard = new Standard_EN16726(testSystem);
    assertEquals("MJ/m3", standard.getUnit("SuperiorWobbeIndex"));
  }
}
