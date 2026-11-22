package neqsim.process.equipment.reservoir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.reservoir.InflowControlDevice.Mixture;

/**
 * Tests for {@link InflowControlDevice}.
 *
 * @author NeqSim
 * @version 1.0
 */
public class InflowControlDeviceTest {
  /**
   * Builds a mixture with the given water and gas fractions.
   *
   * @param water water volume fraction
   * @param gas gas volume fraction
   * @return mixture
   */
  private static Mixture mixture(double water, double gas) {
    return new Mixture(800.0, 1030.0, 150.0, 0.002, 0.0005, 2.0e-5, water, gas);
  }

  /** Mixture density and viscosity follow the stated mixing rules. */
  @Test
  void mixtureProperties() {
    Mixture m = mixture(0.3, 0.1);
    assertEquals(0.6 * 800.0 + 0.3 * 1030.0 + 0.1 * 150.0, m.getDensity(), 1e-9);
    double expected = Math.exp(0.6 * Math.log(0.002) + 0.3 * Math.log(0.0005) + 0.1 * Math.log(2.0e-5));
    assertEquals(expected, m.getViscosity(), 1e-12);
    assertEquals(0.6, m.getOilFraction(), 1e-12);
    assertThrows(IllegalArgumentException.class, () -> mixture(0.8, 0.4));
  }

  /** A passive nozzle follows the orifice equation and does not depend on viscosity. */
  @Test
  void icdMatchesOrificeEquation() {
    InflowControlDevice icd = InflowControlDevice.icd("ICD", 10, 4.0, 2, 0.8);
    Mixture oil = mixture(0.0, 0.0);
    double q = 500.0;
    double area = 2 * Math.PI * 0.004 * 0.004 / 4.0;
    double v = q / 86400.0 / 10.0 / area;
    double expected = 800.0 * v * v / (2.0 * 0.64) / 1.0e5;
    assertEquals(expected, icd.pressureDropBar(q, oil), 1e-9);
    assertEquals(4.0 * icd.pressureDropBar(q, oil), icd.pressureDropBar(2.0 * q, oil), 1e-9);
    assertEquals(0.0, icd.pressureDropBar(0.0, oil), 0.0);
  }

  /** The inverse function returns the flow that gives the pressure drop. */
  @Test
  void flowRateInvertsPressureDrop() {
    InflowControlDevice aicd = InflowControlDevice.aicd("AICD", 8, 1.0, 2.0, 2.0, 0.5, 800.0, 0.002);
    Mixture m = mixture(0.2, 0.05);
    double q = aicd.flowRateM3PerDay(3.0, m);
    assertEquals(3.0, aicd.pressureDropBar(q, m), 1e-6);
    assertEquals(0.0, aicd.flowRateM3PerDay(0.0, m), 0.0);
  }

  /** An AICD reproduces its calibration point and restricts water more than oil at the same flow. */
  @Test
  void aicdCalibrationAndSelectivity() {
    InflowControlDevice aicd = InflowControlDevice.aicd("AICD", 10, 1.0, 2.0, 2.0, 0.5, 800.0, 0.002);
    double qRef = 1.0 * 24.0 * 10.0;
    assertEquals(2.0, aicd.pressureDropBar(qRef, mixture(0.0, 0.0)), 1e-9);
    assertTrue(aicd.pressureDropBar(qRef, mixture(0.7, 0.0)) > 1.5 * aicd.pressureDropBar(qRef, mixture(0.0, 0.0)));
    assertTrue(aicd.pressureDropBar(qRef, mixture(0.0, 0.2)) > aicd.pressureDropBar(qRef, mixture(0.0, 0.0)));
  }

  /** An AICV is open for design oil and closes for a low-viscosity mixture. */
  @Test
  void aicvClosesOnLowViscosity() {
    InflowControlDevice aicv = InflowControlDevice.aicv("AICV", 4, 8.0, 0.8, 0.002, 0.5, 0.05);
    assertTrue(aicv.openFraction(mixture(0.0, 0.0)) > 0.9);
    assertTrue(aicv.openFraction(mixture(0.9, 0.0)) < 0.3);
    double q = 400.0;
    assertTrue(aicv.pressureDropBar(q, mixture(0.9, 0.0)) > 5.0 * aicv.pressureDropBar(q, mixture(0.0, 0.0)));
  }

  /** A DAR is open at the design density and closes outside the window. */
  @Test
  void darClosesOutsideDensityWindow() {
    InflowControlDevice dar = InflowControlDevice.dar("DAR", 4, 8.0, 0.8, 800.0, 0.1, 0.05);
    assertEquals(1.0, dar.openFraction(mixture(0.0, 0.0)), 1e-9);
    assertTrue(dar.openFraction(mixture(0.8, 0.0)) < 0.2);
    assertTrue(dar.openFraction(mixture(0.0, 0.5)) < 0.2);
  }

  /** Invalid input is rejected. */
  @Test
  void rejectsInvalidInput() {
    assertThrows(IllegalArgumentException.class,
        () -> new InflowControlDevice("x", InflowControlDevice.DeviceType.ICD, 0));
    assertThrows(IllegalArgumentException.class, () -> InflowControlDevice.icd("x", 1, -1.0, 1, 0.8));
    assertThrows(IllegalArgumentException.class,
        () -> InflowControlDevice.aicd("x", 1, 1.0, 0.0, 2.0, 0.5, 800.0, 0.002));
  }
}
