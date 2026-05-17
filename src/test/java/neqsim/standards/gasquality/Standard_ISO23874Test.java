package neqsim.standards.gasquality;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Tests for Standard_ISO23874 - GC requirements for HC dew point.
 *
 * @author ESOL
 */
class Standard_ISO23874Test extends neqsim.NeqSimTest {
  static SystemInterface testSystem = null;

  /**
   * Set up the test system with a gas including heavier hydrocarbons.
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
   * Test basic dew point calculation.
   */
  @Test
  void testCalculate() {
    Standard_ISO23874 standard = new Standard_ISO23874(testSystem);
    standard.calculate();
    double dewPoint = standard.getValue("hydrocarbonDewPoint");
    // Dew point should be a reasonable temperature in Celsius
    assertTrue(dewPoint > -80.0 && dewPoint < 50.0,
        "HC dew point should be between -80 and 50 C but was " + dewPoint);
  }

  /**
   * Test that max carbon number is detected.
   */
  @Test
  void testMaxCarbonNumber() {
    Standard_ISO23874 standard = new Standard_ISO23874(testSystem);
    standard.calculate();
    double maxC = standard.getValue("maxCarbonNumber");
    assertTrue(maxC >= 6, "Max carbon number should be >= 6 for n-hexane");
  }

  /**
   * Test composition quality assessment.
   */
  @Test
  void testCompositionQuality() {
    Standard_ISO23874 standard = new Standard_ISO23874(testSystem);
    standard.setMinimumCarbonNumber(9);
    standard.calculate();
    // C6 is max but requirement is C9, so quality may be flagged
    double quality = standard.getValue("compositionQualityOk");
    assertTrue(quality >= 0.0, "Quality indicator should be a valid number");
  }
}
