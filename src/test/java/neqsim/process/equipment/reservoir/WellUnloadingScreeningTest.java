package neqsim.process.equipment.reservoir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link WellUnloadingScreening}, using the C-16 B start-up basis (317 bara, 2400 m TVD, 1030 kg/m3 brine,
 * CV10 choke Cv 1.9 at 51 % opening).
 *
 * @author NeqSim
 * @version 1.0
 */
public class WellUnloadingScreeningTest {
  /**
   * A liquid-full brine column balances the reservoir at the hand-calculated wellhead pressure.
   */
  @Test
  public void liquidFullStaticPressureMatchesHandCalculation() {
    double p = WellUnloadingScreening.staticWellheadPressure(317.0, 2400.0, 1030.0, 150.0, 0.0);
    assertEquals(317.0 - 1030.0 * 9.80665 * 2400.0 / 1.0e5, p, 1.0e-9);
    assertEquals(74.58, p, 0.01);
  }

  /**
   * A deeper gas cap raises the static wellhead pressure monotonically up to the gas-filled value.
   */
  @Test
  public void deeperGasCapRaisesStaticPressure() {
    double previous = -1.0;
    for (double depth = 0.0; depth <= 2400.0; depth += 400.0) {
      double p = WellUnloadingScreening.staticWellheadPressure(317.0, 2400.0, 1030.0, 200.0, depth);
      assertTrue(p > previous);
      previous = p;
    }
    assertEquals(317.0 - 200.0 * 9.80665 * 2400.0 / 1.0e5, previous, 1.0e-9);
    assertThrows(IllegalArgumentException.class,
        () -> WellUnloadingScreening.staticWellheadPressure(317.0, 2400.0, 1030.0, 200.0, 2500.0));
  }

  /**
   * The choke-limited rate reproduces the hand-solved quadratic and grows when the separator pressure is lowered.
   */
  @Test
  public void chokeLimitedRateGrowsWhenSeparatorIsLowered() {
    double kv = WellUnloadingScreening.cvToKv(1.9);
    double at38 = WellUnloadingScreening.chokeLimitedLiquidRate(317.0, 2400.0, 1030.0, 39.013, kv, 15.0);
    double at15 = WellUnloadingScreening.chokeLimitedLiquidRate(317.0, 2400.0, 1030.0, 16.013, kv, 15.0);
    assertEquals(7.78, at38, 0.03);
    assertTrue(at15 > at38);
    assertEquals(0.345, at15 / at38 - 1.0, 0.01);
  }

  /**
   * No flow is returned when the reservoir cannot lift the column or the choke is closed.
   */
  @Test
  public void noFlowWhenColumnCannotBeLiftedOrChokeClosed() {
    double kv = WellUnloadingScreening.cvToKv(1.9);
    assertEquals(0.0, WellUnloadingScreening.chokeLimitedLiquidRate(317.0, 2400.0, 1030.0, 80.0, kv, 15.0), 0.0);
    assertEquals(0.0, WellUnloadingScreening.chokeLimitedLiquidRate(317.0, 2400.0, 1030.0, 20.0, 0.0, 15.0), 0.0);
    assertTrue(Double.isInfinite(WellUnloadingScreening.unloadingTimeHours(60.0, 0.0)));
    assertEquals(7.7, WellUnloadingScreening.unloadingTimeHours(60.0, 7.78), 0.05);
  }

  /**
   * The liquid share recovered from the gauge pressure returns the share used to build it.
   */
  @Test
  public void liquidShareRoundTrips() {
    double wellhead = 41.0;
    double tvd = 2216.3;
    double share = 0.5;
    double mean = share * 1030.0 + (1.0 - share) * 130.0;
    double gauge = wellhead + mean * 9.80665 * tvd / 1.0e5;
    assertEquals(share, WellUnloadingScreening.liquidShare(gauge, wellhead, tvd, 1030.0, 130.0), 1.0e-9);
    assertEquals(1.0, WellUnloadingScreening.liquidShare(gauge + 500.0, wellhead, tvd, 1030.0, 130.0), 0.0);
    assertThrows(IllegalArgumentException.class,
        () -> WellUnloadingScreening.liquidShare(100.0, 10.0, 0.0, 1030.0, 130.0));
  }
}
