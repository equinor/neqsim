package neqsim.standards.gasquality;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Tests for SulfurSpecificationMethod.
 *
 * @author ESOL
 */
class SulfurSpecificationMethodTest extends neqsim.NeqSimTest {
  static SystemInterface testSystem = null;

  /**
   * Set up the test system with H2S.
   *
   * @throws java.lang.Exception if setup fails
   */
  @BeforeAll
  static void setUpBeforeClass() {
    testSystem = new SystemSrkEos(273.15 + 20.0, 50.0);
    testSystem.addComponent("methane", 0.95);
    testSystem.addComponent("ethane", 0.03);
    testSystem.addComponent("H2S", 0.0001);
    testSystem.addComponent("nitrogen", 0.019);
    testSystem.addComponent("CO2", 0.0009);
    testSystem.setMixingRule("classic");
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
  }

  /**
   * Test H2S concentration calculation.
   */
  @Test
  void testCalculate() {
    SulfurSpecificationMethod standard = new SulfurSpecificationMethod(testSystem);
    standard.calculate();
    double h2sValue = standard.getValue("H2S");
    assertTrue(h2sValue >= 0, "H2S value should be >= 0");
  }

  /**
   * Test isOnSpec.
   */
  @Test
  void testIsOnSpec() {
    SulfurSpecificationMethod standard = new SulfurSpecificationMethod(testSystem);
    standard.calculate();
    // Low H2S gas should be on-spec
    assertTrue(standard.isOnSpec(), "Low H2S gas should be on-spec");
  }
}
