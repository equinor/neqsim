package neqsim.statistics.parameterfitting.nonlinearparameterfitting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import Jama.Matrix;
import neqsim.statistics.parameterfitting.NumericalDerivative;
import neqsim.statistics.parameterfitting.SampleSet;
import neqsim.statistics.parameterfitting.SampleValue;

/**
 * Regression tests for numerical derivative and Levenberg-Marquardt fitting behavior.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class LevenbergMarquardtRegressionTest {

  /**
   * Verifies that numerical derivatives use Richardson refinement accurately.
   */
  @Test
  void numericalDerivativeUsesRichardsonExtrapolation() {
    CubicParameterFunction function = new CubicParameterFunction(2.0);
    SampleValue sample = createSample(0.0, 1.0, new double[] {3.0}, function);

    LevenbergMarquardt optimizer = new LevenbergMarquardt();
    optimizer.setSampleSet(new SampleSet(listOf(sample)));

    double derivative = NumericalDerivative.calcDerivative(optimizer, 0, 0);

    assertEquals(36.0, derivative, 1.0e-9);
    assertEquals(2.0, function.getFittingParams(0), 0.0);
  }

  /**
   * Verifies that LM converges for a linear least-squares problem and reports fitting statistics.
   */
  @Test
  void solveReportsConvergedResultForLinearLeastSquares() {
    LinearParameterFunction function = new LinearParameterFunction(0.5, 0.5);
    List<SampleValue> samples = new ArrayList<SampleValue>();
    for (int i = -3; i <= 3; i++) {
      double x = i;
      samples.add(createSample(2.5 * x - 1.2, 0.1, new double[] {x}, function));
    }

    LevenbergMarquardt optimizer = new LevenbergMarquardt();
    optimizer.setSampleSet(new SampleSet(new ArrayList<SampleValue>(samples)));
    optimizer.setMaxNumberOfIterations(30);
    optimizer.solve();

    LevenbergMarquardtResult result = optimizer.getResult();
    double[] parameterStandardErrors = result.getParameterStandardErrors();
    Matrix covariance = result.getCovarianceMatrix();
    Matrix correlation = result.getCorrelationMatrix();

    assertTrue(result.isConverged(), result.getConvergenceReason().name());
    assertEquals(2.5, function.getFittingParams(0), 1.0e-6);
    assertEquals(-1.2, function.getFittingParams(1), 1.0e-6);
    assertTrue(result.getIterations() >= 5);
    assertTrue(result.getFinalChiSquare() < 1.0e-8);
    assertTrue(Double.isFinite(result.getGradientNorm()));
    assertTrue(result.getGradientNorm() < 1.0e-2);
    assertNotNull(covariance);
    assertNotNull(correlation);
    assertEquals(2, covariance.getRowDimension());
    assertEquals(2, correlation.getRowDimension());
    assertNotNull(parameterStandardErrors);
    assertEquals(2, parameterStandardErrors.length);
    assertTrue(parameterStandardErrors[0] > 0.0);
    assertTrue(parameterStandardErrors[1] > 0.0);
  }

  /**
   * Verifies that the legacy external-project calling pattern remains source compatible.
   */
  @Test
  void legacyParameterFittingCallingPatternStillWorks() {
    LinearParameterFunction function = new LinearParameterFunction(0.2, 0.0);
    List<SampleValue> samples = new ArrayList<SampleValue>();
    samples.add(createSample(1.0, 0.1, new double[] {1.0}, function));
    samples.add(createSample(3.0, 0.1, new double[] {2.0}, function));
    samples.add(createSample(5.0, 0.1, new double[] {3.0}, function));

    LevenbergMarquardt optim = new LevenbergMarquardt();
    SampleSet sampleSet = new SampleSet(new ArrayList<SampleValue>(samples));
    optim.setSampleSet(sampleSet);

    optim.solve();
    optim.calcDeviation();

    assertTrue(optim.isSolved());
    assertNotNull(optim.getResult());
    assertEquals(2.0, function.getFittingParams(0), 2.0e-6);
    assertEquals(-1.0, function.getFittingParams(1), 5.0e-6);
  }

  /**
   * Verifies that absolute-deviation beta uses each sample's residual sign.
   */
  @Test
  void absoluteDeviationBetaUsesCurrentSampleResidualSign() {
    ConstantParameterFunction function = new ConstantParameterFunction(0.0);
    List<SampleValue> samples = new ArrayList<SampleValue>();
    samples.add(createSample(2.0, 1.0, new double[] {0.0}, function));
    samples.add(createSample(-1.0, 1.0, new double[] {1.0}, function));

    LevenbergMarquardtAbsDev optimizer = new LevenbergMarquardtAbsDev();
    optimizer.setSampleSet(new SampleSet(new ArrayList<SampleValue>(samples)));
    optimizer.init();

    double[] beta = optimizer.calcBetaMatrix();

    assertEquals(1.0, beta[0], 1.0e-8);
  }

  /**
   * Creates a sample value with the supplied function.
   *
   * @param value measured sample value
   * @param standardDeviation measured sample standard deviation
   * @param dependentValues independent variable values
   * @param function fitting function to evaluate
   * @return sample value configured with the function
   */
  private static SampleValue createSample(double value, double standardDeviation,
      double[] dependentValues, LevenbergMarquardtFunction function) {
    SampleValue sample = new SampleValue(value, standardDeviation, dependentValues);
    sample.setFunction(function);
    return sample;
  }

  /**
   * Creates a single-item sample list.
   *
   * @param sample sample to add to the list
   * @return list containing the supplied sample
   */
  private static ArrayList<SampleValue> listOf(SampleValue sample) {
    ArrayList<SampleValue> samples = new ArrayList<SampleValue>();
    samples.add(sample);
    return samples;
  }

  /**
   * Cubic one-parameter function for derivative accuracy testing.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  private static class CubicParameterFunction extends LevenbergMarquardtFunction {
    /**
     * Creates a cubic function.
     *
     * @param initialParameter initial value of the fitted parameter
     */
    CubicParameterFunction(double initialParameter) {
      params = new double[] {initialParameter};
    }

    /** {@inheritDoc} */
    @Override
    public double calcValue(double[] dependentValues) {
      return dependentValues[0] * params[0] * params[0] * params[0];
    }

    /** {@inheritDoc} */
    @Override
    public void setFittingParams(int i, double value) {
      params[i] = value;
    }
  }

  /**
   * Linear two-parameter function for optimizer convergence testing.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  private static class LinearParameterFunction extends LevenbergMarquardtFunction {
    /**
     * Creates a linear function.
     *
     * @param initialSlope initial slope value
     * @param initialIntercept initial intercept value
     */
    LinearParameterFunction(double initialSlope, double initialIntercept) {
      params = new double[] {initialSlope, initialIntercept};
    }

    /** {@inheritDoc} */
    @Override
    public double calcValue(double[] dependentValues) {
      return params[0] * dependentValues[0] + params[1];
    }

    /** {@inheritDoc} */
    @Override
    public void setFittingParams(int i, double value) {
      params[i] = value;
    }
  }

  /**
   * Constant one-parameter function for absolute-deviation beta testing.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  private static class ConstantParameterFunction extends LevenbergMarquardtFunction {
    /**
     * Creates a constant function.
     *
     * @param initialParameter initial constant value
     */
    ConstantParameterFunction(double initialParameter) {
      params = new double[] {initialParameter};
    }

    /** {@inheritDoc} */
    @Override
    public double calcValue(double[] dependentValues) {
      return params[0];
    }

    /** {@inheritDoc} */
    @Override
    public void setFittingParams(int i, double value) {
      params[i] = value;
    }
  }
}
