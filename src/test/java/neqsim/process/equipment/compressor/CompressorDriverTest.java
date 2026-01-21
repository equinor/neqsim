package neqsim.process.equipment.compressor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for the CompressorDriver class, including speed-dependent max power curve functionality.
 *
 * @author esol
 * @version 1.0
 */
public class CompressorDriverTest {
  private CompressorDriver driver;

  @BeforeEach
  public void setUp() {
    driver = new CompressorDriver(DriverType.VFD_MOTOR, 1000.0);
    driver.setRatedSpeed(5000.0);
    driver.setMaxSpeed(6000.0);
    driver.setMinSpeed(1000.0);
  }

  @Test
  public void testDefaultMaxPowerIsConstant() {
    // Without setting power curve, max power should be constant
    double maxAt1000 = driver.getMaxAvailablePowerAtSpeed(1000.0);
    double maxAt5000 = driver.getMaxAvailablePowerAtSpeed(5000.0);
    double maxAt6000 = driver.getMaxAvailablePowerAtSpeed(6000.0);

    // All should equal the base max power (1000 * 1.1 = 1100 kW)
    assertEquals(1100.0, maxAt1000, 0.1, "Max power at 1000 RPM should be 1100 kW");
    assertEquals(1100.0, maxAt5000, 0.1, "Max power at 5000 RPM should be 1100 kW");
    assertEquals(1100.0, maxAt6000, 0.1, "Max power at 6000 RPM should be 1100 kW");
  }

  @Test
  public void testLinearPowerCurve() {
    // Set linear power curve: P_max = maxPower * (N / N_rated)
    // At rated speed (5000 RPM): factor = 1.0, P_max = 1100 kW
    // At 50% speed (2500 RPM): factor = 0.5, P_max = 550 kW
    driver.setMaxPowerCurveCoefficients(0.0, 1.0, 0.0);

    assertTrue(driver.isMaxPowerCurveEnabled(), "Power curve should be enabled");

    double maxAtRated = driver.getMaxAvailablePowerAtSpeed(5000.0);
    double maxAtHalf = driver.getMaxAvailablePowerAtSpeed(2500.0);
    double maxAt120Pct = driver.getMaxAvailablePowerAtSpeed(6000.0);

    assertEquals(1100.0, maxAtRated, 0.1, "Max power at rated speed should be 1100 kW");
    assertEquals(550.0, maxAtHalf, 0.1, "Max power at 50% speed should be 550 kW");
    assertEquals(1320.0, maxAt120Pct, 0.1, "Max power at 120% speed should be 1320 kW");
  }

  @Test
  public void testQuadraticPowerCurve() {
    // Typical motor torque-limited curve where power increases with speed but
    // levels off: P_max = maxPower * (0.2 + 0.6*(N/N_rated) + 0.2*(N/N_rated)^2)
    // At rated speed: factor = 0.2 + 0.6 + 0.2 = 1.0
    // At 50% speed: factor = 0.2 + 0.3 + 0.05 = 0.55
    driver.setMaxPowerCurveCoefficients(0.2, 0.6, 0.2);

    double maxAtRated = driver.getMaxAvailablePowerAtSpeed(5000.0);
    double maxAtHalf = driver.getMaxAvailablePowerAtSpeed(2500.0);

    assertEquals(1100.0, maxAtRated, 0.1, "Max power at rated speed should be 1100 kW");
    assertEquals(605.0, maxAtHalf, 0.1, "Max power at 50% speed should be 605 kW");
  }

  @Test
  public void testPowerCurveWithOffset() {
    // Power curve with base offset: P_max = maxPower * (0.5 + 0.5*(N/N_rated))
    // At rated speed: factor = 1.0, P_max = 1100 kW
    // At 0% speed: factor = 0.5, P_max = 550 kW
    // At 50% speed: factor = 0.75, P_max = 825 kW
    driver.setMaxPowerCurveCoefficients(0.5, 0.5, 0.0);

    double maxAtRated = driver.getMaxAvailablePowerAtSpeed(5000.0);
    double maxAtHalf = driver.getMaxAvailablePowerAtSpeed(2500.0);
    double maxAtZero = driver.getMaxAvailablePowerAtSpeed(0.0);

    assertEquals(1100.0, maxAtRated, 0.1, "Max power at rated speed should be 1100 kW");
    assertEquals(825.0, maxAtHalf, 0.1, "Max power at 50% speed should be 825 kW");
    assertEquals(550.0, maxAtZero, 0.1, "Max power at 0 speed should be 550 kW");
  }

  @Test
  public void testDisableAndEnablePowerCurve() {
    driver.setMaxPowerCurveCoefficients(0.0, 1.0, 0.0);
    assertTrue(driver.isMaxPowerCurveEnabled(), "Curve should be enabled after setting");

    // With curve: power at half speed = 50%
    assertEquals(550.0, driver.getMaxAvailablePowerAtSpeed(2500.0), 0.1);

    // Disable the curve
    driver.disableMaxPowerCurve();
    assertFalse(driver.isMaxPowerCurveEnabled(), "Curve should be disabled");

    // Without curve: power at half speed = 100%
    assertEquals(1100.0, driver.getMaxAvailablePowerAtSpeed(2500.0), 0.1);

    // Re-enable the curve
    driver.enableMaxPowerCurve();
    assertTrue(driver.isMaxPowerCurveEnabled(), "Curve should be re-enabled");
    assertEquals(550.0, driver.getMaxAvailablePowerAtSpeed(2500.0), 0.1);
  }

  @Test
  public void testGetMaxPowerCurveCoefficients() {
    // Initially null
    assertNull(driver.getMaxPowerCurveCoefficients(), "Coefficients should be null initially");

    driver.setMaxPowerCurveCoefficients(0.1, 0.8, 0.1);
    double[] coeffs = driver.getMaxPowerCurveCoefficients();

    assertNotNull(coeffs, "Coefficients should not be null after setting");
    assertEquals(3, coeffs.length, "Should have 3 coefficients");
    assertEquals(0.1, coeffs[0], 0.001, "Coefficient a should be 0.1");
    assertEquals(0.8, coeffs[1], 0.001, "Coefficient b should be 0.8");
    assertEquals(0.1, coeffs[2], 0.001, "Coefficient c should be 0.1");
  }

  @Test
  public void testCanDeliverPowerAtSpeed() {
    driver.setMaxPowerCurveCoefficients(0.0, 1.0, 0.0);

    // At rated speed (5000 RPM), max power = 1100 kW
    assertTrue(driver.canDeliverPowerAtSpeed(1000.0, 5000.0), "Should deliver 1000 kW at rated");
    assertTrue(driver.canDeliverPowerAtSpeed(1100.0, 5000.0), "Should deliver 1100 kW at rated");
    assertFalse(driver.canDeliverPowerAtSpeed(1200.0, 5000.0), "Cannot deliver 1200 kW at rated");

    // At half speed (2500 RPM), max power = 550 kW
    assertTrue(driver.canDeliverPowerAtSpeed(500.0, 2500.0), "Should deliver 500 kW at half speed");
    assertFalse(driver.canDeliverPowerAtSpeed(600.0, 2500.0),
        "Cannot deliver 600 kW at half speed");
  }

  @Test
  public void testGetPowerMarginAtSpeed() {
    driver.setMaxPowerCurveCoefficients(0.0, 1.0, 0.0);

    // At rated speed (5000 RPM), max power = 1100 kW
    // Using 800 kW, margin = 300 kW
    assertEquals(300.0, driver.getPowerMarginAtSpeed(800.0, 5000.0), 0.1);

    // At half speed (2500 RPM), max power = 550 kW
    // Using 400 kW, margin = 150 kW
    assertEquals(150.0, driver.getPowerMarginAtSpeed(400.0, 2500.0), 0.1);
  }

  @Test
  public void testGasTurbineWithPowerCurve() {
    CompressorDriver gtDriver = new CompressorDriver(DriverType.GAS_TURBINE, 5000.0);
    gtDriver.setRatedSpeed(10000.0);
    gtDriver.setMaxPower(5500.0); // 110% overload

    // Set linear power curve
    gtDriver.setMaxPowerCurveCoefficients(0.0, 1.0, 0.0);

    // At ISO conditions (15°C = 288.15K), no derating
    gtDriver.setAmbientTemperature(288.15);
    double maxAtRated = gtDriver.getMaxAvailablePowerAtSpeed(10000.0);
    assertEquals(5500.0, maxAtRated, 0.1, "Max power at ISO conditions");

    // At hot day (30°C = 303.15K), 15K above ISO
    // Derating factor = 1 - 15 * 0.005 = 0.925
    gtDriver.setAmbientTemperature(303.15);
    maxAtRated = gtDriver.getMaxAvailablePowerAtSpeed(10000.0);
    assertEquals(5500.0 * 0.925, maxAtRated, 1.0, "Max power derated for hot day");

    // At half speed on hot day: power curve * temperature derate
    // = 0.5 * 0.925 * 5500 = 2543.75 kW
    double maxAtHalf = gtDriver.getMaxAvailablePowerAtSpeed(5000.0);
    assertEquals(5500.0 * 0.5 * 0.925, maxAtHalf, 1.0, "Max power at half speed on hot day");
  }

  @Test
  public void testPowerCurveBoundsChecking() {
    // Test that extreme coefficients are bounded (factor limited to 0.1-1.5)
    driver.setMaxPowerCurveCoefficients(-1.0, 0.0, 0.0); // Would give negative factor

    double maxPower = driver.getMaxAvailablePowerAtSpeed(5000.0);
    // Factor should be clamped to 0.1 minimum
    assertEquals(1100.0 * 0.1, maxPower, 0.1, "Factor should be clamped to 0.1 minimum");

    // Test upper bound
    driver.setMaxPowerCurveCoefficients(2.0, 0.0, 0.0); // Would give factor of 2.0
    maxPower = driver.getMaxAvailablePowerAtSpeed(5000.0);
    // Factor should be clamped to 1.5 maximum
    assertEquals(1100.0 * 1.5, maxPower, 0.1, "Factor should be clamped to 1.5 maximum");
  }
}
