package neqsim.thermo.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * @author ESOL
 */
class ComponentHydrateGFTest extends neqsim.NeqSimTest {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(ComponentHydrateGFTest.class);

  static SystemInterface thermoSystem = null;

  /**
   * @throws java.lang.Exception
   */
  @BeforeAll
  static void setUpBeforeClass() {
    thermoSystem = new SystemSrkCPAstatoil(298.0, 100.0);
    thermoSystem.addComponent("methane", 11.0);
    thermoSystem.addComponent("CO2", 1.0);
    thermoSystem.addComponent("water", 11.0);
    thermoSystem.setMixingRule(10);
  }

  /**
   * Test method for
   * {@link neqsim.thermo.component.ComponentHydrateGF#ComponentHydrateGF(java.lang.String, double, double, int)}.
   */
  @Test
  void testComponentHydrateGFStringDoubleDoubleInt() {
    ThermodynamicOperations testOps = new ThermodynamicOperations(thermoSystem);
    try {
      thermoSystem.setHydrateCheck(true);
      testOps.hydrateFormationTemperature();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
      assertTrue(false);
      return;
    }
    assertEquals(286.4105348955611, thermoSystem.getTemperature("K"), 0.01);
  }
}
