package neqsim.standards.gasquality;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Tests for Standard_AGA7 - Ultrasonic metering.
 *
 * @author ESOL
 */
class Standard_AGA7Test extends neqsim.NeqSimTest {
  static SystemInterface testSystem = null;

  /**
   * Set up the test system.
   *
   * @throws java.lang.Exception if setup fails
   */
  @BeforeAll
  static void setUpBeforeClass() {
    testSystem = new SystemSrkEos(273.15 + 15.0, 70.0);
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
   * Test speed of sound calculation.
   */
  @Test
  void testSpeedOfSound() {
    Standard_AGA7 standard = new Standard_AGA7(testSystem);
    standard.setFlowingConditions(70.0, 273.15 + 15.0);
    standard.setMeasuredVelocity(10.0);
    standard.setPipeDiameter(0.3048);
    standard.calculate();

    double sos = standard.getValue("calculatedSpeedOfSound");
    // Speed of sound in natural gas at 70 bara, 15C is typically 350-450 m/s
    assertTrue(sos > 300 && sos < 500, "Speed of sound should be 300-500 m/s but was " + sos);
  }

  /**
   * Test standard volume flow rate.
   */
  @Test
  void testStandardVolumeFlow() {
    Standard_AGA7 standard = new Standard_AGA7(testSystem);
    standard.setFlowingConditions(70.0, 273.15 + 15.0);
    standard.setMeasuredVelocity(10.0);
    standard.setPipeDiameter(0.3048);
    standard.calculate();

    double svf = standard.getValue("standardVolumeFlowRate");
    double massFlow = standard.getValue("massFlowRate");
    // Mass flow should definitely be positive
    assertTrue(massFlow > 0, "Mass flow rate should be positive");
    // Standard volume flow should be non-negative
    assertTrue(svf >= 0.0, "Standard volume flow rate should be non-negative");
  }

  /**
   * Test Reynolds number.
   */
  @Test
  void testReynoldsNumber() {
    Standard_AGA7 standard = new Standard_AGA7(testSystem);
    standard.setFlowingConditions(70.0, 273.15 + 15.0);
    standard.setMeasuredVelocity(10.0);
    standard.setPipeDiameter(0.3048);
    standard.calculate();

    double re = standard.getValue("reynoldsNumber");
    // Re at these conditions should be very high
    assertTrue(re > 1e6, "Reynolds number should be > 1e6 but was " + re);
  }

  /**
   * Test SOS diagnostic comparison.
   */
  @Test
  void testSOSDiagnostic() {
    Standard_AGA7 standard = new Standard_AGA7(testSystem);
    standard.setFlowingConditions(70.0, 273.15 + 15.0);
    standard.setMeasuredVelocity(10.0);
    standard.setPipeDiameter(0.3048);
    standard.calculate();

    double calcSOS = standard.getValue("calculatedSpeedOfSound");
    // Set measured SOS close to calculated
    standard.setMeasuredSpeedOfSound(calcSOS * 1.001);
    standard.calculate();

    double deviation = standard.getValue("sosDeviation");
    assertTrue(Math.abs(deviation) < 1.0,
        "SOS deviation should be small when measured ~ calculated");
    assertTrue(standard.isOnSpec(), "Should be on-spec when SOS deviation is small");
  }
}
