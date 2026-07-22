package neqsim.standards.gasquality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Tests for Standard_ISO15112 - Energy determination in natural gas.
 *
 * @author ESOL
 */
class Standard_ISO15112Test extends neqsim.NeqSimTest {
  static SystemInterface testSystem = null;

  /**
   * Set up the test system.
   *
   * @throws java.lang.Exception if setup fails
   */
  @BeforeAll
  static void setUpBeforeClass() {
    testSystem = new SystemSrkEos(273.15 + 15.0, 50.0);
    testSystem.addComponent("methane", 0.90);
    testSystem.addComponent("ethane", 0.05);
    testSystem.addComponent("propane", 0.02);
    testSystem.addComponent("nitrogen", 0.02);
    testSystem.addComponent("CO2", 0.01);
    testSystem.setMixingRule("classic");
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
  }

  /**
   * Test basic energy determination.
   */
  @Test
  void testCalculate() {
    Standard_ISO15112 standard = new Standard_ISO15112(testSystem);
    standard.setVolumeFlowRate(10000.0); // 10,000 Sm3/h
    standard.calculate();
    double gcv = standard.getValue("GCV");
    double energyFlowRate = standard.getValue("energyFlowRate");
    assertTrue(gcv > 30.0, "GCV should be > 30 MJ/m3");
    assertTrue(energyFlowRate > 0, "Energy flow rate should be positive");
  }

  /**
   * Test accumulated energy calculation.
   */
  @Test
  void testAccumulatedEnergy() {
    Standard_ISO15112 standard = new Standard_ISO15112(testSystem);
    standard.setVolumeFlowRate(10000.0);
    standard.setAccumulationPeriod(24.0);
    standard.calculate();
    double accumulated = standard.getValue("accumulatedEnergy");
    assertTrue(accumulated > 0, "Accumulated energy should be positive");
    // 24h of 10000 Sm3/h at ~38 MJ/m3 should be a large number
    assertTrue(accumulated > 1000.0, "24h energy should be > 1000 GJ");
  }

  /**
   * Test units.
   */
  @Test
  void testUnits() {
    Standard_ISO15112 standard = new Standard_ISO15112(testSystem);
    assertEquals("MJ/m3", standard.getUnit("GCV"));
    assertEquals("MJ/h", standard.getUnit("energyFlowRate"));
    assertEquals("GJ", standard.getUnit("accumulatedEnergy"));
  }
}
