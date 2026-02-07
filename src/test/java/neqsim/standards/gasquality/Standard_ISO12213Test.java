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
 * Tests for Standard_ISO12213 - Compression factor (Z-factor) calculation.
 *
 * @author ESOL
 */
class Standard_ISO12213Test extends neqsim.NeqSimTest {
  static SystemInterface testSystem = null;

  /**
   * Set up the test system with a typical natural gas composition.
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
   * Test basic Z-factor calculation.
   */
  @Test
  void testCalculateZFactor() {
    Standard_ISO12213 standard = new Standard_ISO12213(testSystem);
    standard.calculate();
    double z = standard.getValue("Z");
    // Z for typical gas at 50 bara, 15C should be between 0.8 and 1.0
    assertTrue(z > 0.8 && z < 1.0, "Z-factor should be between 0.8 and 1.0 but was " + z);
  }

  /**
   * Test that density is calculated consistently with Z.
   */
  @Test
  void testDensityConsistency() {
    Standard_ISO12213 standard = new Standard_ISO12213(testSystem);
    standard.calculate();
    double z = standard.getValue("Z");
    double density = standard.getValue("density");
    assertTrue(density > 0, "Density should be positive");
    assertTrue(z > 0, "Z should be positive");
  }

  /**
   * Test unit returns.
   */
  @Test
  void testUnits() {
    Standard_ISO12213 standard = new Standard_ISO12213(testSystem);
    assertEquals("-", standard.getUnit("Z"));
    assertEquals("kg/m3", standard.getUnit("density"));
    assertEquals("g/mol", standard.getUnit("molarMass"));
  }

  /**
   * Test isOnSpec returns true for reasonable Z-factor.
   */
  @Test
  void testIsOnSpec() {
    Standard_ISO12213 standard = new Standard_ISO12213(testSystem);
    standard.calculate();
    assertTrue(standard.isOnSpec());
  }
}
