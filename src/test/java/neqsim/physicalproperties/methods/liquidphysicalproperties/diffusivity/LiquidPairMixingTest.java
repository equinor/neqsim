package neqsim.physicalproperties.methods.liquidphysicalproperties.diffusivity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;
import neqsim.physicalproperties.system.PhysicalProperties;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Independent limiting-coefficient and ownership contracts for liquid pair mixing.
 *
 * @author NeqSim
 * @version 1.0
 */
class LiquidPairMixingTest {
  /** Public synthetic component slate. */
  private static final String[] NAMES = {"methane", "ethane", "propane", "n-butane", "n-pentane", "n-heptane"};

  /**
   * Controlled infinite-dilution oracle; the inherited production method performs the mixing.
   *
   * @author NeqSim
   * @version 1.0
   */
  private static class Controlled extends Diffusivity {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1L;
    private final double[][] limiting;

    /**
     * Construct an independent ordered limiting-coefficient oracle.
     *
     * @param fractions prescribed mole fractions for the algebraic mixing test
     * @param limits independent ordered limiting coefficients in m2/s
     */
    Controlled(double[] fractions, double[][] limits) {
      super(context(fractions));
      limiting = limits;
    }

    /** {@inheritDoc} */
    @Override
    public double calcBinaryDiffusionCoefficient(int i, int j, int method) {
      return limiting[i][j];
    }
  }

  /**
   * Build a property context, then prescribe exact algebraic fractions without invoking another EOS calculation.
   *
   * @param fractions exact fractions
   * @return property context owned by the test
   */
  private static PhysicalProperties context(double[] fractions) {
    SystemInterface fluid = new SystemSrkEos(300.0, 10.0);
    for (int i = 0; i < fractions.length; i++) {
      fluid.addComponent(NAMES[i], 1.0 / fractions.length);
    }
    fluid.setMixingRule("classic");
    fluid.init(0);
    fluid.init(3);
    fluid.initProperties();
    for (int i = 0; i < fractions.length; i++) {
      fluid.getPhase(0).getComponent(i).setx(fractions[i]);
    }
    return fluid.getPhase(0).getPhysicalProperties();
  }

  /**
   * Create a constant off-diagonal limiting-coefficient matrix.
   *
   * @param size number of components
   * @param value constant off-diagonal limiting value
   * @return controlled limiting matrix with untouched zero diagonal
   */
  private static double[][] constant(int size, double value) {
    double[][] result = new double[size][size];
    for (int i = 0; i < size; i++) {
      for (int j = 0; j < size; j++) {
        result[i][j] = i == j ? 0.0 : value;
      }
    }
    return result;
  }

  /** Constant limiting transport must remain constant for any number of components. */
  @Test
  void preservesConstantLimitingCoefficient() {
    for (int n : new int[] {2, 3, 6}) {
      double[] x = new double[n];
      java.util.Arrays.fill(x, 1.0 / n);
      double[][] actual = new Controlled(x, constant(n, 1.0e-9)).calcDiffusionCoefficients(0, 0);
      for (int i = 0; i < n; i++) {
        Assertions.assertEquals(0.0, actual[i][i], 0.0);
        for (int j = 0; j < n; j++) {
          if (i != j)
            Assertions.assertEquals(1.0e-9, actual[i][j], 1.0e-21);
        }
      }
    }
  }

  /** Binary Vignes interpolation must use the original limiting values in both directions. */
  @Test
  void recoversBinaryVignesAndExactSymmetry() {
    double[][] limits = {{0.0, 4.0e-9}, {9.0e-9, 0.0}};
    double[][] actual = new Controlled(new double[] {0.2, 0.8}, limits).calcDiffusionCoefficients(0, 0);
    double expected = Math.pow(4.0e-9, 0.8) * Math.pow(9.0e-9, 0.2);
    Assertions.assertEquals(expected, actual[0][1], expected * 1.0e-12);
    Assertions.assertEquals(actual[0][1], actual[1][0], 0.0);
  }

  /** The absent-solute limit must recover its independently specified infinite-dilution coefficient. */
  @Test
  void recoversInfiniteDilutionLimit() {
    double[][] limits = {{0.0, 4.0e-9}, {9.0e-9, 0.0}};
    double[][] actual = new Controlled(new double[] {0.0, 1.0}, limits).calcDiffusionCoefficients(0, 0);
    Assertions.assertEquals(4.0e-9, actual[0][1], 4.0e-21);
    Assertions.assertEquals(actual[0][1], actual[1][0], 0.0);
  }

  /** Reference product includes a separately specified third-solvent limiting friction. */
  @Test
  void matchesIndependentThirdSolventProduct() {
    double[][] limits = {{0.0, 4.0e-9, 1.0e-9}, {9.0e-9, 0.0, 2.0e-9}, {3.0e-9, 5.0e-9, 0.0}};
    double[][] actual = new Controlled(new double[] {0.2, 0.3, 0.5}, limits).calcDiffusionCoefficients(0, 0);
    // 60-digit independent decimal product: (4e-9)^.3 * (9e-9)^.2 * (6e-9)^.5.
    double expected = 5.761587004753308e-9;
    Assertions.assertEquals(expected, actual[0][1], expected * 1.0e-12);
  }

  /** Changing the numeric diffusion unit must scale every pair by the same factor. */
  @Test
  void preservesDimensionalScaleCovariance() {
    double[][] small = {{0.0, 4.0e-9, 1.0e-9}, {9.0e-9, 0.0, 2.0e-9}, {3.0e-9, 5.0e-9, 0.0}};
    double[][] large = new double[3][3];
    for (int i = 0; i < 3; i++)
      for (int j = 0; j < 3; j++)
        large[i][j] = small[i][j] * 1.0e4;
    double[][] a = new Controlled(new double[] {0.2, 0.3, 0.5}, small).calcDiffusionCoefficients(0, 0);
    double[][] b = new Controlled(new double[] {0.2, 0.3, 0.5}, large).calcDiffusionCoefficients(0, 0);
    for (int i = 0; i < 3; i++)
      for (int j = 0; j < 3; j++) {
        if (i != j)
          Assertions.assertEquals(a[i][j] * 1.0e4, b[i][j], b[i][j] * 1.0e-12);
      }
  }

  /** Recalculation must restart from the limiting values instead of reusing mixed coefficients. */
  @Test
  void repeatedEvaluationIsIdentical() {
    Controlled model = new Controlled(new double[] {0.2, 0.8}, new double[][] {{0.0, 4.0e-9}, {9.0e-9, 0.0}});
    double first = model.calcDiffusionCoefficients(0, 0)[0][1];
    Assertions.assertEquals(first, model.calcDiffusionCoefficients(0, 0)[0][1], 0.0);
  }

  /** Clone-owned matrices and effective coefficients must not modify the original. */
  @Test
  void cloneHasIndependentPairAndEffectiveArrays() {
    Controlled original = new Controlled(new double[] {0.2, 0.8}, constant(2, 1.0e-9));
    original.calcDiffusionCoefficients(0, 0);
    original.calcEffectiveDiffusionCoefficients();
    Diffusivity copy = original.clone();
    copy.binaryDiffusionCoefficients[0][1] = 7.0e-9;
    copy.effectiveDiffusionCoefficient[0] = 8.0e-9;
    Assertions.assertEquals(1.0e-9, original.getMaxwellStefanBinaryDiffusionCoefficient(0, 1), 1.0e-21);
    Assertions.assertEquals(1.0e-9, original.getEffectiveDiffusionCoefficient(0), 1.0e-21);
  }

  /** Invalid limiting transport must be rejected rather than replaced by a clipped or fabricated coefficient. */
  @Test
  void rejectsInvalidLimitingCoefficients() {
    for (double invalid : new double[] {0.0, -1.0e-9, Double.NaN, Double.POSITIVE_INFINITY}) {
      Controlled model = new Controlled(new double[] {0.2, 0.8}, new double[][] {{0.0, invalid}, {4.0e-9, 0.0}});
      Assertions.assertThrows(IllegalStateException.class, () -> model.calcDiffusionCoefficients(0, 0));
    }
  }

  /**
   * Flash the public synthetic rich gas in either component insertion order.
   *
   * @param reverse whether to reverse insertion
   * @return initialized hydrocarbon liquid properties
   */
  private PhysicalProperties richOil(boolean reverse) {
    SystemInterface fluid = new SystemSrkEos(277.15, 50.0);
    double[] z = {0.916, 0.050, 0.020, 0.007, 0.003, 0.004};
    for (int k = 0; k < z.length; k++) {
      int i = reverse ? z.length - k - 1 : k;
      fluid.addComponent(NAMES[i], z[i]);
    }
    fluid.setMixingRule("classic");
    new ThermodynamicOperations(fluid).TPflash();
    fluid.initProperties();
    return fluid.getPhase("oil").getPhysicalProperties();
  }

  /** Real EOS states must retain named-pair transport under reversed insertion order. */
  @Test
  void richGasLiquidIsSymmetricAndInsertionOrderIndependent() {
    PhysicalProperties a = richOil(false);
    PhysicalProperties b = richOil(true);
    a.calcEffectiveDiffusionCoefficients();
    b.calcEffectiveDiffusionCoefficients();
    for (int i = 0; i < 6; i++)
      for (int j = 0; j < 6; j++) {
        if (i == j)
          continue;
        double value = a.diffusivityCalc.getMaxwellStefanBinaryDiffusionCoefficient(i, j);
        Assertions.assertTrue(value > 0.0 && Double.isFinite(value));
        Assertions.assertEquals(value, a.diffusivityCalc.getMaxwellStefanBinaryDiffusionCoefficient(j, i), 0.0);
        double reverse = b.diffusivityCalc.getMaxwellStefanBinaryDiffusionCoefficient(5 - i, 5 - j);
        Assertions.assertEquals(value, reverse, value * 1.0e-10);
      }
  }
}
