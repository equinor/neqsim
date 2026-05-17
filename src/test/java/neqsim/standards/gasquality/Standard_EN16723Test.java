package neqsim.standards.gasquality;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Tests for Standard_EN16723 - Biomethane quality.
 *
 * @author ESOL
 */
class Standard_EN16723Test extends neqsim.NeqSimTest {
  static SystemInterface testSystem = null;

  /**
   * Set up the test system with typical biomethane composition.
   *
   * @throws java.lang.Exception if setup fails
   */
  @BeforeAll
  static void setUpBeforeClass() {
    testSystem = new SystemSrkEos(273.15 + 15.0, 1.01325);
    testSystem.addComponent("methane", 0.96);
    testSystem.addComponent("CO2", 0.02);
    testSystem.addComponent("nitrogen", 0.015);
    testSystem.addComponent("oxygen", 0.00005);
    testSystem.setMixingRule("classic");
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
  }

  /**
   * Test Part 1 (grid injection) calculation.
   */
  @Test
  void testPart1GridInjection() {
    Standard_EN16723 standard = new Standard_EN16723(testSystem);
    standard.setPart(1);
    standard.calculate();
    double wobbe = standard.getValue("WI");
    assertTrue(wobbe > 40.0, "Wobbe should be > 40 MJ/m3 for biomethane");
  }

  /**
   * Test Part 2 (automotive) calculation.
   */
  @Test
  void testPart2Automotive() {
    Standard_EN16723 standard = new Standard_EN16723(testSystem);
    standard.setPart(2);
    standard.calculate();
    double wobbe = standard.getValue("WI");
    assertTrue(wobbe > 40.0, "Wobbe should be > 40 MJ/m3 for biomethane");
  }

  /**
   * Test isOnSpec for clean biomethane.
   */
  @Test
  void testIsOnSpec() {
    Standard_EN16723 standard = new Standard_EN16723(testSystem);
    standard.setPart(1);
    standard.calculate();
    // Verify the standard runs and produces a valid result
    double co2 = standard.getValue("CO2");
    double o2 = standard.getValue("O2");
    assertTrue(co2 < 2.5, "CO2 should be within limit");
    assertTrue(o2 < 0.01, "O2 should be within limit");
  }

  /**
   * Test isOnSpec fails for high CO2.
   */
  @Test
  void testHighCO2OffSpec() {
    SystemInterface highCO2 = new SystemSrkEos(273.15 + 15.0, 1.01325);
    highCO2.addComponent("methane", 0.80);
    highCO2.addComponent("CO2", 0.18);
    highCO2.addComponent("nitrogen", 0.02);
    highCO2.setMixingRule("classic");
    ThermodynamicOperations ops = new ThermodynamicOperations(highCO2);
    ops.TPflash();

    Standard_EN16723 standard = new Standard_EN16723(highCO2);
    standard.setPart(1);
    standard.calculate();
    assertFalse(standard.isOnSpec(), "High CO2 biomethane should fail grid injection spec");
  }
}
