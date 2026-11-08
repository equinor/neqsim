package neqsim.pvtsimulation.reservoirproperties.materialbalance;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import org.junit.jupiter.api.Test;

/**
 * Scale, domain and analytic-limit contracts for the existing Carter-Tracy recurrence.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class CarterTracyScalingTest {
  /** Infinite-acting radius convention. */
  private static final double INFINITE = Double.POSITIVE_INFINITY;
  /** Precision of the independently evaluated first-step rational identity. */
  private static final MathContext PRECISION = new MathContext(80, RoundingMode.HALF_EVEN);

  /**
   * Checks the first-step identity without an absolute tolerance that could admit frozen zero results.
   */
  @Test
  void positiveSmallTimesCannotFreezeAtAnAbsoluteDenominatorCutoff() {
    for (double time : new double[] {Double.MIN_VALUE, 1e-300, 1e-200, 1e-100, 1e-70, 1e-50, 1e-8, 1.0}) {
      double pressure = VanEverdingenHurstAquifer.dimensionlessPressure(time, INFINITE);
      double expected = time / pressure;
      double actual = VanEverdingenHurstAquifer.cumulativeInfluxCarterTracy(new double[] {0, time}, new double[] {0, 1},
          1, INFINITE)[1];
      assertTrue(actual > 0.0);
      assertEquals(1.0, actual / expected, 1e-12);
    }
  }

  /**
   * Checks finite first-step results when the pressure/support product overflows or underflows in double arithmetic.
   */
  @Test
  void independentDecimalFirstStepSurvivesIntermediateScaleLoss() {
    for (double[] row : new double[][] {{1e-300, 1e200, 1e200}, {Double.MAX_VALUE, 1e-200, 1e-200},
        {1e-200, 1e250, 1e100}, {1e100, 1e-250, 1e-100}}) {
      double pressure = VanEverdingenHurstAquifer.dimensionlessPressure(row[0], INFINITE);
      double expected = new BigDecimal(row[0]).multiply(new BigDecimal(row[1]), PRECISION)
          .multiply(new BigDecimal(row[2]), PRECISION).divide(new BigDecimal(pressure), PRECISION).doubleValue();
      double actual = VanEverdingenHurstAquifer.cumulativeInfluxCarterTracy(new double[] {0, row[0]},
          new double[] {0, row[2]}, row[1], INFINITE)[1];
      assertTrue(Double.isFinite(actual) && actual > 0.0);
      assertEquals(1.0, actual / expected, 1e-12);
    }
  }

  /** Checks arithmetic of the retained positive-intercept bounded policy without qualifying its physics. */
  @Test
  void retainedBoundedPolicyCannotLoseItsFirstStepBeforeVolumeScaling() {
    double time = Double.MIN_VALUE;
    double pressure = VanEverdingenHurstAquifer.dimensionlessPressure(time, 100);
    double expected = new BigDecimal(time).multiply(new BigDecimal(1e200), PRECISION)
        .multiply(new BigDecimal(1e200), PRECISION).divide(new BigDecimal(pressure), PRECISION).doubleValue();
    double actual = VanEverdingenHurstAquifer.cumulativeInfluxCarterTracy(new double[] {0, time},
        new double[] {0, 1e200}, 1e200, 100)[1];
    assertEquals(1.0, actual / expected, 1e-12);
  }

  /** Tests later positive overflow separately from valid projection of an unbounded negative forcing. */
  @Test
  void laterOverflowIsDistinctFromProjectedPressureRecovery() {
    ArithmeticException error = assertThrows(ArithmeticException.class, () -> VanEverdingenHurstAquifer
        .cumulativeInfluxCarterTracy(new double[] {0, 1e-200, 1e200}, new double[] {0, 1e100, 1e100}, 1e100, INFINITE));
    assertTrue(error.getMessage().contains("index 2"));
    double[] recovered = VanEverdingenHurstAquifer.cumulativeInfluxCarterTracy(new double[] {0, 1e-300, 1e100},
        new double[] {0, 1e200, -1e200}, 1e200, INFINITE);
    assertTrue(Double.isFinite(recovered[1]) && recovered[1] > 0);
    assertEquals(0.0, recovered[2]);
  }

  /**
   * Uses the independent early-time ramp ODE limit, separate from the discrete recurrence identity.
   */
  @Test
  void refinedRampConvergesToItsAnalyticEarlyTimeLimit() {
    double previous = Double.POSITIVE_INFINITY;
    double end = 1e-70;
    double expected = 2.0 * Math.sqrt(Math.PI) / 5.0 * Math.sqrt(end);
    for (int count : new int[] {32, 64, 128, 256, 512, 1024}) {
      double[] times = new double[count + 1];
      double[] drops = new double[count + 1];
      for (int i = 1; i <= count; i++) {
        times[i] = end * i / count;
        drops[i] = times[i] / end;
      }
      double actual = VanEverdingenHurstAquifer.cumulativeInfluxCarterTracy(times, drops, 1, INFINITE)[count];
      double error = Math.abs(actual / expected - 1.0);
      assertTrue(error < previous, "Ramp must converge to the independent early-time ODE limit");
      previous = error;
    }
    assertTrue(previous < 0.02);
  }

  /** Preserves zero-support and signed pressure recovery with the existing nonnegative cumulative projection. */
  @Test
  void supportAndPressureConventionsRemainExplicit() {
    double[] times = {0, 0.1, 0.2};
    double[] drops = {0, 1, -10};
    assertArrayEquals(new double[3], VanEverdingenHurstAquifer.cumulativeInfluxCarterTracy(times, drops, 0, INFINITE));
    double[] water = VanEverdingenHurstAquifer.cumulativeInfluxCarterTracy(times, drops, 1, INFINITE);
    assertTrue(water[1] > 0);
    assertEquals(0.0, water[2]);
    assertArrayEquals(new double[] {0, 0.1, 0.2}, times);
    assertArrayEquals(new double[] {0, 1, -10}, drops);
  }

  /** Rejects invalid evidence instead of silently producing a plausible zero history. */
  @Test
  void invalidHistoryAndSupportAreRejected() {
    assertThrows(IllegalArgumentException.class,
        () -> VanEverdingenHurstAquifer.cumulativeInfluxCarterTracy(new double[0], new double[0], 1, INFINITE));
    for (double[] times : new double[][] {{0, 0}, {0.2, 0.1}, {-1, 1}, {0, Double.NaN},
        {0, Double.POSITIVE_INFINITY}}) {
      assertThrows(IllegalArgumentException.class,
          () -> VanEverdingenHurstAquifer.cumulativeInfluxCarterTracy(times, new double[] {0, 1}, 1, INFINITE));
    }
    for (double constant : new double[] {-1, Double.NaN, Double.POSITIVE_INFINITY}) {
      assertThrows(IllegalArgumentException.class, () -> VanEverdingenHurstAquifer
          .cumulativeInfluxCarterTracy(new double[] {0, 1}, new double[] {0, 1}, constant, INFINITE));
    }
    assertThrows(IllegalArgumentException.class, () -> VanEverdingenHurstAquifer
        .cumulativeInfluxCarterTracy(new double[] {0, 1}, new double[] {Double.NaN, 1}, 1, INFINITE));
  }

  /** Rejects an actually unrepresentable result; finite inputs alone do not guarantee a finite volume. */
  @Test
  void unrepresentablePositiveInfluxFailsExplicitly() {
    assertThrows(ArithmeticException.class,
        () -> VanEverdingenHurstAquifer.cumulativeInfluxCarterTracy(new double[] {0, 1e100},
            new double[] {0, Double.MAX_VALUE}, Double.MAX_VALUE, INFINITE));
  }
}
