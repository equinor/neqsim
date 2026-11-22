package neqsim.process.mechanicaldesign.subsea;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link UmbilicalFunctionalDesign}.
 *
 * @author ESOL
 * @version 1.0
 */
class UmbilicalFunctionalDesignTest {

  /** Tube volume of a 63.5 mm, 1000 m tube. */
  @Test
  void tubeVolumeMatchesGeometry() {
    assertEquals(3.1669, UmbilicalFunctionalDesign.tubeVolumeM3(63.5, 1000.0), 1e-3);
  }

  /** Transit time and invalid rate. */
  @Test
  void transitTime() {
    assertEquals(2.5, UmbilicalFunctionalDesign.transitTimeDays(10.0, 4.0), 1e-12);
    assertThrows(IllegalArgumentException.class, () -> UmbilicalFunctionalDesign.transitTimeDays(10.0, 0.0));
  }

  /** Laminar and turbulent friction factors. */
  @Test
  void frictionFactors() {
    assertEquals(64.0 / 1000.0, UmbilicalFunctionalDesign.frictionFactor(1000.0, 0.0), 1e-12);
    double f = UmbilicalFunctionalDesign.frictionFactor(1.0e5, 0.0);
    assertTrue(f > 0.016 && f < 0.020, "smooth-pipe friction factor " + f);
    assertThrows(IllegalArgumentException.class, () -> UmbilicalFunctionalDesign.frictionFactor(0.0, 0.0));
  }

  /** Laminar pressure drop equals Hagen-Poiseuille. */
  @Test
  void pressureDropLaminarMatchesHagenPoiseuille() {
    double flowM3h = 0.05;
    double dMm = 12.7;
    double length = 1000.0;
    double mu = 0.0085;
    double d = dMm / 1000.0;
    double q = flowM3h / 3600.0;
    double expected = 128.0 * mu * length * q / (Math.PI * Math.pow(d, 4.0)) / 1.0e5;
    double dp = UmbilicalFunctionalDesign.pressureDropBar(flowM3h, dMm, length, 1000.0, mu, 0.01);
    assertEquals(expected, dp, 0.01 * expected);
  }

  /** Hydraulic time constant of a long 1/2 inch line. */
  @Test
  void hydraulicTimeConstant() {
    double tau = UmbilicalFunctionalDesign.hydraulicTimeConstantSeconds(12.7, 18750.0, 0.0085, 2.2e9);
    assertTrue(tau > 20.0 && tau < 35.0, "tau " + tau);
  }

  /** Actuation time exceeds the time constant; invalid pressures throw. */
  @Test
  void actuationTime() {
    double tau = UmbilicalFunctionalDesign.hydraulicTimeConstantSeconds(12.7, 18750.0, 0.0085, 2.2e9);
    double t = UmbilicalFunctionalDesign.actuationTimeSeconds(12.7, 18750.0, 0.0085, 2.2e9, 1.0, 345.0, 250.0);
    assertTrue(t > tau);
    assertThrows(IllegalArgumentException.class,
        () -> UmbilicalFunctionalDesign.actuationTimeSeconds(12.7, 18750.0, 0.0085, 2.2e9, 1.0, 200.0, 250.0));
  }

  /** Electrical line screening. */
  @Test
  void electricalLine() {
    double[] r = UmbilicalFunctionalDesign.electricalLine(2.8, 56.0, 18.75, 100.0, 600.0, 50.0);
    assertEquals(100.0 / 600.0, r[0], 1e-12);
    assertEquals(0.2, r[1], 0.02);
    assertEquals(Math.sqrt(r[0] * r[0] + r[1] * r[1]), r[2], 1e-12);
    assertTrue(r[3] > 5.0 && r[3] < 20.0, "drop " + r[3]);
    assertEquals(600.0 - r[3], r[4], 1e-12);
  }

  /** Optical budget is the sum of losses. */
  @Test
  void opticalBudget() {
    assertEquals(7.425, UmbilicalFunctionalDesign.opticalBudgetDb(18.75, 0.22, 3, 0.1, 4, 0.5, 1.0), 1e-9);
  }

  /** Weight ratio reproduces the Stjerne umbilical as-built value. */
  @Test
  void weightRatioStjerne() {
    double wr = UmbilicalFunctionalDesign.weightRatio(344.0, 157.0, 1023.0);
    assertTrue(wr > 1.75 && wr < 1.79, "weight ratio " + wr);
  }

  /** J-tube pull-in reproduces the Stjerne termination head tension. */
  @Test
  void jTubePullInStjerne() {
    double tonnes = UmbilicalFunctionalDesign.jTubePullInTensionKN(19.62, 40.0, 0.8, 146.2, 3.3, 7.5, 15.0, 0.3, 106.0,
        5.8, 1.1) / 9.81;
    assertTrue(tonnes > 6.3 && tonnes < 7.3, "pull-in tonnes " + tonnes);
  }
}
