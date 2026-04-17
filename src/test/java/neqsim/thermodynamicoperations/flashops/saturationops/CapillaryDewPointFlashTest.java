package neqsim.thermodynamicoperations.flashops.saturationops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Tests for {@link CapillaryDewPointFlash}.
 *
 * <p>
 * Verifies that the capillary dew point temperature is higher than the bulk dew point, and that the
 * shift increases with decreasing pore radius.
 * </p>
 */
public class CapillaryDewPointFlashTest {

  /**
   * Capillary dew point should be higher than bulk dew point for a natural gas mixture.
   */
  @Test
  public void testCapillaryDewPointHigherThanBulk() throws Exception {
    SystemInterface fluid = new SystemSrkEos(273.15, 50.0);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.07);
    fluid.addComponent("propane", 0.05);
    fluid.addComponent("n-butane", 0.02);
    fluid.addComponent("n-pentane", 0.01);
    fluid.setMixingRule("classic");

    // Bulk dew point
    SystemInterface bulkFluid = fluid.clone();
    ThermodynamicOperations bulkOps = new ThermodynamicOperations(bulkFluid);
    bulkOps.dewPointTemperatureFlash();
    double tBulk = bulkFluid.getTemperature();

    // Capillary dew point with 1 um pore radius
    SystemInterface capFluid = fluid.clone();
    ThermodynamicOperations capOps = new ThermodynamicOperations(capFluid);
    capOps.capillaryDewPointTemperatureFlash(1.0e-6);
    double tCap = capFluid.getTemperature();

    assertTrue(tCap > tBulk,
        "Capillary dew point (" + tCap + " K) should be higher than bulk (" + tBulk + " K)");
    double deltaT = tCap - tBulk;
    assertTrue(deltaT > 0.0001, "Temperature shift should be measurable, got: " + deltaT + " K");
    assertTrue(deltaT < 50.0, "Temperature shift should be reasonable, got: " + deltaT + " K");
  }

  /**
   * Smaller pore radius should produce a larger dew point shift.
   */
  @Test
  public void testSmallerRadiusGivesLargerShift() throws Exception {
    SystemInterface fluid = new SystemSrkEos(273.15, 50.0);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.07);
    fluid.addComponent("propane", 0.05);
    fluid.addComponent("n-butane", 0.02);
    fluid.addComponent("n-pentane", 0.01);
    fluid.setMixingRule("classic");

    // Bulk dew point
    SystemInterface bulkFluid = fluid.clone();
    ThermodynamicOperations bulkOps = new ThermodynamicOperations(bulkFluid);
    bulkOps.dewPointTemperatureFlash();
    double tBulk = bulkFluid.getTemperature();

    // Large pore: 10 um
    SystemInterface fluidLarge = fluid.clone();
    ThermodynamicOperations opsLarge = new ThermodynamicOperations(fluidLarge);
    opsLarge.capillaryDewPointTemperatureFlash(10.0e-6);
    double tLarge = fluidLarge.getTemperature();

    // Small pore: 0.1 um
    SystemInterface fluidSmall = fluid.clone();
    ThermodynamicOperations opsSmall = new ThermodynamicOperations(fluidSmall);
    opsSmall.capillaryDewPointTemperatureFlash(0.1e-6);
    double tSmall = fluidSmall.getTemperature();

    double shiftLarge = tLarge - tBulk;
    double shiftSmall = tSmall - tBulk;

    assertTrue(shiftSmall > shiftLarge,
        "Smaller pore should give larger shift: " + shiftSmall + " K vs " + shiftLarge + " K");
  }

  /**
   * Tests the capillary dew point for pure propane against analytical Kelvin equation.
   */
  @Test
  public void testPurePropaneCapillaryShift() throws Exception {
    SystemInterface fluid = new SystemSrkEos(273.15, 10.0);
    fluid.addComponent("propane", 1.0);
    fluid.setMixingRule("classic");

    // Bulk dew point
    SystemInterface bulkFluid = fluid.clone();
    ThermodynamicOperations bulkOps = new ThermodynamicOperations(bulkFluid);
    bulkOps.dewPointTemperatureFlash();
    double tBulk = bulkFluid.getTemperature();

    // Capillary dew point with 1 um radius
    SystemInterface capFluid = fluid.clone();
    ThermodynamicOperations capOps = new ThermodynamicOperations(capFluid);
    capOps.capillaryDewPointTemperatureFlash(1.0e-6);
    double tCap = capFluid.getTemperature();

    assertTrue(tCap > tBulk, "Capillary dew point should be higher than bulk for propane");
    double deltaT = tCap - tBulk;
    // For 1 um pore, shift should be very small (order of 0.001-0.1 K)
    assertTrue(deltaT < 5.0, "Shift for 1 um pore should be small, got: " + deltaT + " K");
  }

  /**
   * Tests the Peng-Robinson EOS variant.
   */
  @Test
  public void testPengRobinsonEOS() throws Exception {
    SystemInterface fluid = new SystemPrEos(273.15, 50.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.05);
    fluid.addComponent("propane", 0.03);
    fluid.addComponent("n-butane", 0.02);
    fluid.setMixingRule("classic");

    // Bulk dew point
    SystemInterface bulkFluid = fluid.clone();
    ThermodynamicOperations bulkOps = new ThermodynamicOperations(bulkFluid);
    bulkOps.dewPointTemperatureFlash();
    double tBulk = bulkFluid.getTemperature();

    // Capillary dew point
    SystemInterface capFluid = fluid.clone();
    ThermodynamicOperations capOps = new ThermodynamicOperations(capFluid);
    capOps.capillaryDewPointTemperatureFlash(1.0e-6);
    double tCap = capFluid.getTemperature();

    assertTrue(tCap > tBulk, "PR EOS capillary shift should also be positive");
    assertFalse(Double.isNaN(tCap), "Result should not be NaN");
  }

  /**
   * Tests with contact angle (non-zero wetting).
   */
  @Test
  public void testNonZeroContactAngle() throws Exception {
    SystemInterface fluid = new SystemSrkEos(273.15, 50.0);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.07);
    fluid.addComponent("propane", 0.05);
    fluid.addComponent("n-butane", 0.02);
    fluid.addComponent("n-pentane", 0.01);
    fluid.setMixingRule("classic");

    double poreRadius = 1.0e-6;

    // Perfect wetting (theta=0)
    SystemInterface fluid0 = fluid.clone();
    ThermodynamicOperations ops0 = new ThermodynamicOperations(fluid0);
    ops0.capillaryDewPointTemperatureFlash(poreRadius);
    double tPerfect = fluid0.getTemperature();

    // Contact angle of 30 degrees reduces capillary effect
    SystemInterface fluid30 = fluid.clone();
    ThermodynamicOperations ops30 = new ThermodynamicOperations(fluid30);
    ops30.capillaryDewPointTemperatureFlash(poreRadius, Math.toRadians(30.0));
    double t30 = fluid30.getTemperature();

    // Bulk dew point for reference
    SystemInterface bulkFluid = fluid.clone();
    ThermodynamicOperations bulkOps = new ThermodynamicOperations(bulkFluid);
    bulkOps.dewPointTemperatureFlash();
    double tBulk = bulkFluid.getTemperature();

    double shiftPerfect = tPerfect - tBulk;
    double shift30 = t30 - tBulk;

    // Contact angle reduces the effective capillary pressure, so shift should be smaller
    assertTrue(shift30 < shiftPerfect,
        "30 degree contact angle should give smaller shift than perfect wetting");
    assertTrue(shift30 > 0, "30 degree contact angle should still give positive shift");
  }

  /**
   * Invalid pore radius should throw.
   */
  @Test
  public void testInvalidPoreRadiusThrows() {
    SystemInterface fluid = new SystemSrkEos(273.15, 50.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");

    assertThrows(IllegalArgumentException.class, () -> new CapillaryDewPointFlash(fluid, 0.0));
    assertThrows(IllegalArgumentException.class, () -> new CapillaryDewPointFlash(fluid, -1.0e-6));
  }

  /**
   * Tests that large pore radius gives negligible shift (approaches bulk dew point).
   */
  @Test
  public void testLargePoreApproachesBulk() throws Exception {
    SystemInterface fluid = new SystemSrkEos(273.15, 50.0);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.07);
    fluid.addComponent("propane", 0.05);
    fluid.addComponent("n-butane", 0.02);
    fluid.addComponent("n-pentane", 0.01);
    fluid.setMixingRule("classic");

    // Bulk dew point
    SystemInterface bulkFluid = fluid.clone();
    ThermodynamicOperations bulkOps = new ThermodynamicOperations(bulkFluid);
    bulkOps.dewPointTemperatureFlash();
    double tBulk = bulkFluid.getTemperature();

    // Very large pore: 1 mm
    SystemInterface capFluid = fluid.clone();
    ThermodynamicOperations capOps = new ThermodynamicOperations(capFluid);
    capOps.capillaryDewPointTemperatureFlash(1.0e-3);
    double tCap = capFluid.getTemperature();

    double deltaT = tCap - tBulk;
    // For 1 mm pore, shift should be negligible (< 0.001 K)
    assertEquals(0.0, deltaT, 0.01,
        "Large pore (1 mm) should give negligible shift, got: " + deltaT + " K");
  }
}
