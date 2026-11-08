package neqsim.pvtsimulation.reservoirproperties.materialbalance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import org.junit.jupiter.api.Test;

/**
 * Independent physical-unit and exact-binary arithmetic controls for radial aquifer inputs.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class AquiferPhysicalInputTest {
  /** Precision fixed independently of the production scaling implementation. */
  private static final MathContext PRECISION = new MathContext(80, RoundingMode.HALF_EVEN);

  /** Checks dimensionless-time conversion without intermediate overflow, underflow or subnormal precision loss. */
  @Test
  void timeConversionRetainsRepresentableExactBinaryResults() {
    for (double[] row : new double[][] {{1e-100, 1e100, 1, 1e-200, 1e-200, 1e200},
        {1e200, 1e200, 1, 1e100, 1e100, 1e100}, {1, 1, 1, 1e200, 1e200, 1e-200}, {1e-200, 1e-200, 1, 1e-200, 1e-200, 1},
        {1, 1, 1, 1e-160, 1e-160, 1e160}, {Double.MIN_VALUE, 1, 1, 1, 1, 1}}) {
      double expected = quotient(new double[] {row[0], row[1]}, new double[] {row[2], row[3], row[4], row[5], row[5]});
      assertScaled(expected, time(row));
    }
  }

  /** Checks the storage constant with an exact-binary oracle on the same declared pi constant. */
  @Test
  void storageConversionRetainsRepresentableExactBinaryResults() {
    for (double[] row : new double[][] {{1e-200, 1e-200, 1e200, 1e100, 360}, {1, 1e200, 1e200, 1e-200, 360},
        {1, 1, 1e200, 1e50, Double.MIN_VALUE}, {1, 1e-160, 1e-160, 1e80, 360}, {1, Double.MIN_VALUE, 1, 1, 360}}) {
      double expected = quotient(new double[] {2 * Math.PI, row[4], row[0], row[1], row[2], row[3], row[3]},
          new double[] {360});
      assertScaled(expected, storage(row));
    }
  }

  /** Checks final rounding on both sides of the half-minimum and maximum-finite representability boundaries. */
  @Test
  void finalRepresentabilityUsesExactBinaryFactors() {
    double[][] rows = {{Double.MIN_VALUE, 1, 0.7, 2.0 / 0.7, 1, 1}, {Double.MIN_VALUE, 1, 0.73, 2.0 / 0.73, 1, 1},
        {Double.MIN_VALUE, 1, 1, 2, 1, 1},
        {Double.MIN_VALUE, 1, 0.9171076874610481, 4.892793041582901, 0.44571044916080677, 1},
        {Double.MAX_VALUE, 1, 0.8061243467211738, 4.871567827453244, 0.25464151433530974, 1},
        {Math.scalb((1L << 27) - 1.0, 485), Math.scalb((1L << 27) + 1.0, 485), 1, 1, 1, 1},
        {Double.MIN_VALUE, 3, 1, 2, 1, 1}, {Double.MIN_VALUE, 5, 1, 2, 1, 1}, {Double.MIN_VALUE, 7, 1, 2, 1, 1},
        {Double.MAX_VALUE, 1, 1, 1, 1, 1}, {Double.MAX_VALUE, 1, 1, 2, 1, 1},
        {Math.nextDown(Double.MAX_VALUE), 1, 1, 1, 1, 1}};
    // Exact binary midpoint parity is independent of any rounded decimal quotient.
    double[] expectations = {Double.MIN_VALUE, Double.MIN_VALUE, 0, 0, Double.POSITIVE_INFINITY,
        Double.POSITIVE_INFINITY, 2 * Double.MIN_VALUE, 2 * Double.MIN_VALUE, 4 * Double.MIN_VALUE, Double.MAX_VALUE,
        Double.MAX_VALUE / 2, Math.nextDown(Double.MAX_VALUE)};
    for (int i = 0; i < rows.length; i++) {
      double[] row = rows[i];
      double expected = expectations[i];
      if (expected == 0 || !Double.isFinite(expected)) {
        assertThrows(ArithmeticException.class, () -> time(row));
      } else {
        assertEquals(Double.doubleToLongBits(expected), Double.doubleToLongBits(time(row)));
      }
    }
  }

  /** Verifies the independent joint identity and explicit Pa/bar compressibility bases at nearby physical states. */
  @Test
  void jointStorageTimeIdentityPreservesPressureBasisAndRadiusScaling() {
    for (double radius : new double[] {100, 1000, 3000}) {
      for (double angle : new double[] {30, 180, 360}) {
        for (double porosity : new double[] {0.05, 0.2, 0.8}) {
          for (double permeability : new double[] {1e-14, 1e-12}) {
            double seconds = 86400;
            double viscosity = 5e-4;
            double compressibilityPa = 1e-9;
            double thickness = 30;
            double td = VanEverdingenHurstAquifer.dimensionlessTime(permeability, seconds, porosity, viscosity,
                compressibilityPa, radius);
            double u = VanEverdingenHurstAquifer.aquiferConstant(porosity, 1e5 * compressibilityPa, thickness, radius,
                angle);
            double independent = 2 * Math.PI * (angle / 360) * permeability * seconds * thickness * 1e5 / viscosity;
            assertScaled(independent, td * u);
          }
        }
      }
    }
  }

  /** Preserves ordinary evaluation bits, including the established operator order. */
  @Test
  void ordinaryPhysicalInputsRetainEvaluationBits() {
    for (double radius : new double[] {100, 1000, 3000}) {
      double expectedTime = 1e-13 * 8.64e7 / (0.2 * 5e-4 * 1e-9 * radius * radius);
      double expectedStorage = 2 * Math.PI * (180.0 / 360) * 0.2 * 1e-4 * 30 * radius * radius;
      assertEquals(Double.doubleToLongBits(expectedTime),
          Double.doubleToLongBits(VanEverdingenHurstAquifer.dimensionlessTime(1e-13, 8.64e7, 0.2, 5e-4, 1e-9, radius)));
      assertEquals(Double.doubleToLongBits(expectedStorage),
          Double.doubleToLongBits(VanEverdingenHurstAquifer.aquiferConstant(0.2, 1e-4, 30, radius, 180)));
    }
  }

  /** Separates physical zero limits from unrepresentable strictly positive conversions. */
  @Test
  void zeroAndUnrepresentableLimitsAreExplicit() {
    assertEquals(0, time(new double[] {0, 1, 0.2, 1, 1, 1}));
    assertEquals(0, time(new double[] {1, 0, 0.2, 1, 1, 1}));
    for (int axis : new int[] {0, 1, 2, 4}) {
      double[] row = {0.2, 1e-4, 30, 100, 180};
      row[axis] = 0;
      assertEquals(0, storage(row));
    }
    assertThrows(ArithmeticException.class, () -> time(new double[] {Double.MIN_VALUE, Double.MIN_VALUE, 1, 1, 1, 1}));
    assertThrows(ArithmeticException.class, () -> time(new double[] {Double.MAX_VALUE, Double.MAX_VALUE, 1, 1, 1, 1}));
    assertThrows(ArithmeticException.class,
        () -> storage(new double[] {Double.MIN_VALUE, Double.MIN_VALUE, 1, 1, 360}));
    assertThrows(ArithmeticException.class,
        () -> storage(new double[] {1, Double.MAX_VALUE, Double.MAX_VALUE, 1, 360}));
  }

  /** Rejects invalid physical inputs before zero-limit shortcuts can mask them. */
  @Test
  void invalidPhysicalAxesFailExplicitly() {
    double[] ordinaryTime = {1e-13, 86400, 0.2, 5e-4, 1e-9, 1000};
    for (int axis = 0; axis < ordinaryTime.length; axis++) {
      for (double bad : new double[] {-1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
        double[] row = ordinaryTime.clone();
        row[axis] = bad;
        assertThrows(IllegalArgumentException.class, () -> time(row));
      }
    }
    for (int axis : new int[] {2, 3, 4, 5}) {
      double[] row = ordinaryTime.clone();
      row[axis] = 0;
      assertThrows(IllegalArgumentException.class, () -> time(row));
    }
    assertThrows(IllegalArgumentException.class, () -> time(new double[] {0, 1, 1.01, 1, 1, 1}));
    double[] ordinaryStorage = {0.2, 1e-4, 30, 1000, 180};
    for (int axis = 0; axis < ordinaryStorage.length; axis++) {
      for (double bad : new double[] {-1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
        double[] row = ordinaryStorage.clone();
        row[axis] = bad;
        assertThrows(IllegalArgumentException.class, () -> storage(row));
      }
    }
    assertThrows(IllegalArgumentException.class, () -> storage(new double[] {1.01, 1, 1, 1, 360}));
    assertThrows(IllegalArgumentException.class, () -> storage(new double[] {0, 1, 1, 0, 360}));
    assertThrows(IllegalArgumentException.class, () -> storage(new double[] {0, 1, 1, 1, 361}));
  }

  /**
   * Evaluates the existing time API from an explicit input vector.
   *
   * @param row permeability, time, porosity, viscosity, compressibility and radius
   * @return dimensionless time
   */
  private static double time(double[] row) {
    return VanEverdingenHurstAquifer.dimensionlessTime(row[0], row[1], row[2], row[3], row[4], row[5]);
  }

  /**
   * Evaluates the existing storage API from an explicit input vector.
   *
   * @param row porosity, compressibility, thickness, radius and angle
   * @return reservoir cubic metres per bar
   */
  private static double storage(double[] row) {
    return VanEverdingenHurstAquifer.aquiferConstant(row[0], row[1], row[2], row[3], row[4]);
  }

  /**
   * Computes an independent exact-binary product ratio at fixed decimal precision.
   *
   * @param numerator positive numerator factors
   * @param denominator positive denominator factors
   * @return rounded double result
   */
  private static double quotient(double[] numerator, double[] denominator) {
    BigDecimal value = BigDecimal.ONE;
    for (double factor : numerator) {
      value = value.multiply(new BigDecimal(factor), PRECISION);
    }
    for (double factor : denominator) {
      value = value.divide(new BigDecimal(factor), PRECISION);
    }
    return value.doubleValue();
  }

  /**
   * Applies the frozen relative plus final-ULP arithmetic bound.
   *
   * @param expected independently computed positive finite result
   * @param actual production result
   */
  private static void assertScaled(double expected, double actual) {
    assertTrue(Double.isFinite(actual) && actual > 0);
    assertEquals(expected, actual, 1e-12 * expected + Math.ulp(expected));
  }
}
