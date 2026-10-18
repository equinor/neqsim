package neqsim.statistics.parameterfitting;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import Jama.Matrix;
import neqsim.statistics.parameterfitting.nonlinearparameterfitting.LevenbergMarquardt;
import neqsim.statistics.parameterfitting.nonlinearparameterfitting.LevenbergMarquardtFunction;
import neqsim.statistics.parameterfitting.nonlinearparameterfitting.LevenbergMarquardtResult;
import neqsim.statistics.parameterfitting.nonlinearparameterfitting.LevenbergMarquardtResult.ConvergenceReason;

/**
 * Compatibility coverage for released statistics APIs during the incremental Jama migration.
 *
 * @author Even Solbraa
 * @version 1.0
 */
@SuppressWarnings("deprecation")
class StatisticsJamaCompatibilityTest {
  /**
   * Verifies the original constructor descriptor and defensive copying of legacy inputs.
   */
  @Test
  void legacyMatrixConstructorCopiesInputs() {
    Matrix covariance = new Matrix(new double[][] {{4.0, 1.0}, {1.0, 9.0}});
    Matrix correlation = new Matrix(new double[][] {{1.0, 0.2}, {0.2, 1.0}});
    double[] errors = {2.0, 3.0};

    LevenbergMarquardtResult result = new LevenbergMarquardtResult(ConvergenceReason.CHI_SQUARE_TOLERANCE, 2, 0.0, 0.0,
        covariance, correlation, errors);
    covariance.set(0, 0, -1.0);
    correlation.set(0, 0, -1.0);
    errors[0] = -1.0;

    assertEquals(4.0, result.getCovarianceMatrixArray()[0][0], 0.0);
    assertEquals(1.0, result.getCorrelationMatrixArray()[0][0], 0.0);
    assertArrayEquals(new double[] {2.0, 3.0}, result.getParameterStandardErrors());
  }

  /**
   * Retains the already released array constructor together with the named factory.
   */
  @Test
  void releasedArrayConstructorAndFactoryRemainAvailable() {
    double[][] covariance = {{4.0}};
    double[][] correlation = {{1.0}};
    LevenbergMarquardtResult constructed = new LevenbergMarquardtResult(ConvergenceReason.CHI_SQUARE_TOLERANCE, 2, 0.0,
        0.0, covariance, correlation, new double[] {2.0});
    LevenbergMarquardtResult factory = LevenbergMarquardtResult.fromArrays(ConvergenceReason.CHI_SQUARE_TOLERANCE, 2,
        0.0, 0.0, covariance, correlation, new double[] {2.0});
    covariance[0][0] = -1.0;
    correlation[0][0] = -1.0;

    assertArrayEquals(new double[] {4.0}, constructed.getCovarianceMatrixArray()[0]);
    assertArrayEquals(new double[] {4.0}, factory.getCovarianceMatrixArray()[0]);
    assertArrayEquals(new double[] {1.0}, factory.getCorrelationMatrixArray()[0]);
  }

  /**
   * Null matrix data have an unambiguous named factory and typed forms for either released constructor.
   */
  @Test
  void nullMatrixInputsHaveExplicitConstructionPaths() {
    LevenbergMarquardtResult legacy = new LevenbergMarquardtResult(ConvergenceReason.NOT_RUN, 0, Double.NaN, Double.NaN,
        (Matrix) null, (Matrix) null, null);
    LevenbergMarquardtResult arrays = new LevenbergMarquardtResult(ConvergenceReason.NOT_RUN, 0, Double.NaN, Double.NaN,
        (double[][]) null, (double[][]) null, null);
    LevenbergMarquardtResult factory = LevenbergMarquardtResult.fromArrays(ConvergenceReason.NOT_RUN, 0, Double.NaN,
        Double.NaN, null, null, null);

    for (LevenbergMarquardtResult result : new LevenbergMarquardtResult[] {legacy, arrays, factory}) {
      assertNull(result.getCovarianceMatrixArray());
      assertNull(result.getCorrelationMatrixArray());
      assertNull(result.getParameterStandardErrors());
    }
  }

  /**
   * Array callers must still invoke the legacy subclass hook with row-oriented parameters.
   */
  @Test
  void arrayBoundsCallDispatchesLegacyHook() {
    LegacyHookOptimizer optimizer = optimizer();
    optimizer.getSample(0).getFunction().setBounds(new double[][] {{0.0, 1.0}, {0.0, 1.0}});
    double[] parameters = {-2.0, 2.0};

    optimizer.checkBounds(parameters);

    assertEquals(1, optimizer.boundCalls);
    assertArrayEquals(new double[] {0.5, 1.0}, parameters);
  }

  /**
   * The optimization loop must honor existing subclass-specific parameter constraints.
   */
  @Test
  void solverDispatchesLegacyBoundsOverride() {
    LegacyHookOptimizer optimizer = optimizer();
    optimizer.setMaxNumberOfIterations(5);

    optimizer.solve();

    assertTrue(optimizer.boundCalls >= 5);
    assertEquals(0.5, optimizer.getSample(0).getFunction().getFittingParams(0), 0.0);
  }

  /**
   * Existing matrix-display overrides remain reachable without opening a graphical window.
   */
  @Test
  void arrayDisplayCallDispatchesLegacyHook() {
    LegacyHookOptimizer optimizer = optimizer();

    optimizer.displayMatrix(new double[][] {{1.0, 2.0}, {3.0, 4.0}}, "covariance", 4);

    assertEquals("covariance", optimizer.displayName);
    assertArrayEquals(new double[] {1.0, 2.0}, optimizer.displayed[0]);
    assertArrayEquals(new double[] {3.0, 4.0}, optimizer.displayed[1]);
  }

  /**
   * Builds a small two-parameter fitting problem with a legacy extension hook.
   *
   * @return optimizer with samples on a line
   */
  private static LegacyHookOptimizer optimizer() {
    SampleValue[] samples = new SampleValue[3];
    for (int index = 0; index < samples.length; index++) {
      double x = index + 1.0;
      samples[index] = new SampleValue(2.0 * x + 1.0, 1.0, new double[] {x}, new double[] {0.0});
      samples[index].setFunction(new LinearFunction());
    }
    LegacyHookOptimizer optimizer = new LegacyHookOptimizer();
    optimizer.setSampleSet(new SampleSet(samples));
    return optimizer;
  }

  /**
   * Synthetic line with two fitted parameters.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  private static final class LinearFunction extends LevenbergMarquardtFunction {
    /** Initializes the line parameters. */
    private LinearFunction() {
      setInitialGuess(new double[] {0.0, 0.0});
    }

    /** {@inheritDoc} */
    @Override
    public double calcValue(double[] dependentValues) {
      return params[0] * dependentValues[0] + params[1];
    }
  }

  /**
   * Represents an external subclass written against the original Matrix hooks.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  private static final class LegacyHookOptimizer extends LevenbergMarquardt {
    private int boundCalls;
    private String displayName;
    private double[][] displayed;

    /** {@inheritDoc} */
    @Override
    public void checkBounds(Matrix parameters) {
      boundCalls++;
      assertEquals(1, parameters.getRowDimension());
      super.checkBounds(parameters);
      parameters.set(0, 0, 0.5);
    }

    /** {@inheritDoc} */
    @Override
    public void displayMatrix(Matrix matrix, String name, int digits) {
      displayName = name;
      displayed = matrix.getArrayCopy();
    }
  }
}
