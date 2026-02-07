package neqsim.standards.gasquality;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Tests for GasChromotograpyhBase.
 *
 * @author ESOL
 */
class GasChromotograpyhBaseTest extends neqsim.NeqSimTest {
  static SystemInterface testSystem = null;

  /**
   * Set up the test system.
   *
   * @throws java.lang.Exception if setup fails
   */
  @BeforeAll
  static void setUpBeforeClass() {
    testSystem = new SystemSrkEos(273.15 + 15.0, 1.01325);
    testSystem.addComponent("methane", 0.90);
    testSystem.addComponent("ethane", 0.05);
    testSystem.addComponent("propane", 0.03);
    testSystem.addComponent("nitrogen", 0.015);
    testSystem.addComponent("CO2", 0.005);
    testSystem.setMixingRule("classic");
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
  }

  /**
   * Test method composition retrieval.
   */
  @Test
  void testGetValue() {
    GasChromotograpyhBase gc = new GasChromotograpyhBase(testSystem);
    gc.calculate();
    double methane = gc.getValue("methane", "mol%");
    assertTrue(methane > 85.0 && methane < 95.0, "Methane mol% should be ~90 but was " + methane);
  }

  /**
   * Test mol fraction retrieval.
   */
  @Test
  void testGetValueMolFraction() {
    GasChromotograpyhBase gc = new GasChromotograpyhBase(testSystem);
    gc.calculate();
    double ethane = gc.getValue("ethane");
    assertTrue(ethane > 0.04 && ethane < 0.06,
        "Ethane mol fraction should be ~0.05 but was " + ethane);
  }

  /**
   * Test isOnSpec always true for base class.
   */
  @Test
  void testIsOnSpec() {
    GasChromotograpyhBase gc = new GasChromotograpyhBase(testSystem);
    gc.calculate();
    assertTrue(gc.isOnSpec(), "Base GC class should always be on-spec");
  }
}
