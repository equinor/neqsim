package neqsim.process.util.heattransfer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link BoilOffCalculator}.
 *
 * <p>
 * Validates the steady-state cryogenic boil-off screening model: the effective heat-transfer coefficient and boil-off
 * rate fall as insulation thickness grows, the heat-ingress definition holds, and the insulation sweep returns a
 * well-formed table.
 *
 * @author ESOL
 * @version 1.0
 */
public class BoilOffCalculatorTest {

  /** Effective coefficient must decrease as insulation thickness grows. */
  @Test
  public void effectiveCoefficientDecreasesWithThickness() {
    BoilOffCalculator calc = new BoilOffCalculator();
    double thin = calc.effectiveHeatTransferCoefficient(0.05);
    double thick = calc.effectiveHeatTransferCoefficient(0.30);
    assertTrue(thin > thick, "Thicker insulation must lower the effective coefficient");
    assertTrue(thick > 0.0);
  }

  /** Heat ingress must equal hEff*A*(Tamb-Tfluid). */
  @Test
  public void heatIngressMatchesDefinition() {
    BoilOffCalculator calc = new BoilOffCalculator().setSurfaceArea(2.0).setAmbientTemperatureK(288.15)
        .setFluidTemperatureK(111.65);
    double t = 0.10;
    double hEff = calc.effectiveHeatTransferCoefficient(t);
    double expected = hEff * 2.0 * (288.15 - 111.65);
    assertEquals(expected, calc.heatIngressW(t), Math.abs(expected) * 1.0e-9);
  }

  /** Boil-off rate must drop as insulation thickness grows and stay positive. */
  @Test
  public void boilOffRateDecreasesWithThickness() {
    BoilOffCalculator calc = new BoilOffCalculator();
    double thin = calc.boilOffRateKgPerS(0.05);
    double thick = calc.boilOffRateKgPerS(0.30);
    assertTrue(thin > thick);
    assertTrue(thick > 0.0);
  }

  /** Per-hour rate must equal per-second rate times 3600. */
  @Test
  public void perHourMatchesPerSecond() {
    BoilOffCalculator calc = new BoilOffCalculator();
    double s = calc.boilOffRateKgPerS(0.10);
    assertEquals(s * 3600.0, calc.boilOffRateKgPerH(0.10), s * 3600.0 * 1.0e-9);
  }

  /** The insulation sweep must return a two-row table of decreasing boil-off. */
  @Test
  public void sweepReturnsWellFormedTable() {
    BoilOffCalculator calc = new BoilOffCalculator();
    double[][] sweep = calc.sweepInsulationThickness(0.02, 0.30, 8);
    assertEquals(2, sweep.length);
    assertEquals(8, sweep[0].length);
    assertEquals(8, sweep[1].length);
    assertEquals(0.02, sweep[0][0], 1.0e-12);
    assertEquals(0.30, sweep[0][7], 1.0e-12);
    assertTrue(sweep[1][0] > sweep[1][7], "Boil-off must fall across the sweep");
  }

  /** Non-physical setters must be rejected. */
  @Test
  public void rejectsInvalidInputs() {
    assertThrows(IllegalArgumentException.class, () -> new BoilOffCalculator().setLatentHeat(0.0));
    assertThrows(IllegalArgumentException.class, () -> new BoilOffCalculator().setSurfaceArea(-1.0));
    assertThrows(IllegalArgumentException.class, () -> new BoilOffCalculator().effectiveHeatTransferCoefficient(-0.01));
  }
}
