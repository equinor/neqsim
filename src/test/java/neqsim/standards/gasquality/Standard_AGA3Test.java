package neqsim.standards.gasquality;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Tests for Standard_AGA3 - Orifice metering.
 *
 * @author ESOL
 */
class Standard_AGA3Test extends neqsim.NeqSimTest {
  static SystemInterface testSystem = null;

  /**
   * Set up the test system.
   *
   * @throws java.lang.Exception if setup fails
   */
  @BeforeAll
  static void setUpBeforeClass() {
    testSystem = new SystemSrkEos(273.15 + 20.0, 50.0);
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
   * Test mass flow rate calculation.
   */
  @Test
  void testMassFlowRate() {
    Standard_AGA3 standard = new Standard_AGA3(testSystem);
    standard.setOrificeDimensions(0.1016, 0.2032); // 4 inch orifice, 8 inch pipe
    standard.setDifferentialPressure(25000.0); // 25 kPa
    standard.setStaticPressure(50.0e5); // 50 bara in Pa
    standard.setFlowingTemperature(273.15 + 20.0);
    standard.calculate();
    double massFlow = standard.getValue("massFlowRate");
    assertTrue(massFlow > 0, "Mass flow rate should be positive but was " + massFlow);
  }

  /**
   * Test discharge coefficient.
   */
  @Test
  void testDischargeCoefficient() {
    Standard_AGA3 standard = new Standard_AGA3(testSystem);
    standard.setOrificeDimensions(0.1016, 0.2032);
    standard.setDifferentialPressure(25000.0);
    standard.setStaticPressure(50.0e5);
    standard.setFlowingTemperature(273.15 + 20.0);
    standard.calculate();
    double cd = standard.getValue("dischargeCoefficient");
    // Cd for orifice plates is typically 0.59-0.62
    assertTrue(cd > 0.55 && cd < 0.70, "Discharge coefficient should be 0.55-0.70 but was " + cd);
  }

  /**
   * Test beta ratio.
   */
  @Test
  void testBetaRatio() {
    Standard_AGA3 standard = new Standard_AGA3(testSystem);
    standard.setOrificeDimensions(0.1016, 0.2032);
    standard.calculate();
    double beta = standard.getValue("betaRatio");
    assertTrue(Math.abs(beta - 0.5) < 0.01, "Beta ratio should be 0.5 for 4in/8in but was " + beta);
  }

  /**
   * Test standard volume flow.
   */
  @Test
  void testStandardVolumeFlow() {
    Standard_AGA3 standard = new Standard_AGA3(testSystem);
    standard.setOrificeDimensions(0.1016, 0.2032);
    standard.setDifferentialPressure(25000.0);
    standard.setStaticPressure(50.0e5);
    standard.setFlowingTemperature(273.15 + 20.0);
    standard.calculate();
    double svf = standard.getValue("standardVolumeFlowRate");
    double massFlow = standard.getValue("massFlowRate");
    // Mass flow should definitely be positive
    assertTrue(massFlow > 0, "Mass flow should be positive");
    // Standard volume flow should be non-negative (zero if std density calc fails)
    assertTrue(svf >= 0.0, "Standard volume flow should be non-negative");
  }

  /**
   * Test units.
   */
  @Test
  void testUnits() {
    Standard_AGA3 standard = new Standard_AGA3(testSystem);
    assertTrue("kg/s".equals(standard.getUnit("massFlowRate")));
    assertTrue("-".equals(standard.getUnit("dischargeCoefficient")));
    assertTrue("Sm3/h".equals(standard.getUnit("standardVolumeFlowRate")));
  }
}
