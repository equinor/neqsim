package neqsim.standards.gasquality;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Tests for BestPracticeHydrocarbonDewPoint.
 *
 * @author ESOL
 */
class BestPracticeHydrocarbonDewPointTest extends neqsim.NeqSimTest {
  static SystemInterface testSystem = null;

  /**
   * Set up the test system with heavier hydrocarbons.
   *
   * @throws java.lang.Exception if setup fails
   */
  @BeforeAll
  static void setUpBeforeClass() {
    testSystem = new SystemSrkEos(273.15 + 20.0, 70.0);
    testSystem.addComponent("methane", 0.85);
    testSystem.addComponent("ethane", 0.05);
    testSystem.addComponent("propane", 0.03);
    testSystem.addComponent("i-butane", 0.01);
    testSystem.addComponent("n-butane", 0.015);
    testSystem.addComponent("i-pentane", 0.005);
    testSystem.addComponent("n-pentane", 0.005);
    testSystem.addComponent("n-hexane", 0.003);
    testSystem.addComponent("nitrogen", 0.02);
    testSystem.addComponent("CO2", 0.012);
    testSystem.setMixingRule("classic");
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
  }

  /**
   * Test hydrocarbon dew point calculation.
   */
  @Test
  void testCalculate() {
    BestPracticeHydrocarbonDewPoint standard = new BestPracticeHydrocarbonDewPoint(testSystem);
    standard.calculate();
    double dewPoint = standard.getValue("hydrocarbondewpointTemperature", "C");
    // HC dew point for rich gas should be a real temperature
    assertTrue(dewPoint > -100 && dewPoint < 100,
        "HC dew point should be between -100 and 100 C but was " + dewPoint);
  }

  /**
   * Test pressure retrieval.
   */
  @Test
  void testPressure() {
    BestPracticeHydrocarbonDewPoint standard = new BestPracticeHydrocarbonDewPoint(testSystem);
    standard.calculate();
    double pressure = standard.getValue("pressure", "bara");
    assertTrue(pressure > 0, "Pressure should be positive but was " + pressure);
  }
}
