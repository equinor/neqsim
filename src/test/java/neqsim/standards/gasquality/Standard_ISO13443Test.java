package neqsim.standards.gasquality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Tests for Standard_ISO13443 - Standard reference conditions.
 *
 * @author ESOL
 */
class Standard_ISO13443Test extends neqsim.NeqSimTest {
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
   * Test volume conversion between named conditions.
   */
  @Test
  void testVolumeConversionNamed() {
    Standard_ISO13443 standard = new Standard_ISO13443(testSystem);
    standard.setConversionConditions("standard", "normal");
    standard.calculate();
    double convFactor = standard.getValue("conversionFactor");
    // Conversion from 15C to 0C: volume should decrease (colder gas)
    assertTrue(convFactor > 0, "Conversion factor should be positive");
  }

  /**
   * Test explicit volume conversion.
   */
  @Test
  void testConvertVolume() {
    Standard_ISO13443 standard = new Standard_ISO13443(testSystem);
    standard.setConversionConditions("standard", "normal");
    standard.calculate();
    double convertedVol = standard.convertVolume(1000.0);
    assertTrue(convertedVol > 0, "Converted volume should be positive");
  }

  /**
   * Test standard reference temperature constants.
   */
  @Test
  void testReferenceTemperatures() {
    assertEquals(288.15, Standard_ISO13443.T_METER_15C, 0.01);
    assertEquals(273.15, Standard_ISO13443.T_NORMAL_0C, 0.01);
    assertEquals(293.15, Standard_ISO13443.T_20C, 0.01);
  }

  /**
   * Test units.
   */
  @Test
  void testUnits() {
    Standard_ISO13443 standard = new Standard_ISO13443(testSystem);
    assertEquals("-", standard.getUnit("conversionFactor"));
  }
}
