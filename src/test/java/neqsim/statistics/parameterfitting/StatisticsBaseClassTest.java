package neqsim.statistics.parameterfitting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import Jama.Matrix;
import neqsim.mathlib.linearalgebra.LinearAlgebraException;
import neqsim.statistics.parameterfitting.nonlinearparameterfitting.LevenbergMarquardt;
import neqsim.statistics.parameterfitting.nonlinearparameterfitting.LevenbergMarquardtFunction;
import neqsim.statistics.parameterfitting.nonlinearparameterfitting.LevenbergMarquardtResult;
import neqsim.statistics.parameterfitting.nonlinearparameterfitting.LevenbergMarquardtResult.ConvergenceReason;

/**
 * Unit tests for {@link neqsim.statistics.parameterfitting.StatisticsBaseClass}, exercised through its concrete
 * {@link neqsim.statistics.parameterfitting.nonlinearparameterfitting.LevenbergMarquardt} subclass.
 *
 * @author asmf
 * @version 1.0
 */
class StatisticsBaseClassTest {
  /** Legacy subclass using the protected JAMA fields and bound-check hook. */
  private static class LegacyStatistics extends LevenbergMarquardt {
    private boolean boundsHookCalled;

    /** {@inheritDoc} */
    @Override
    public void checkBounds(Matrix parameters) {
      boundsHookCalled = true;
      super.checkBounds(parameters);
    }

    /**
     * Sets the legacy protected covariance fields as an external subclass would.
     *
     * @param covariance covariance values
     */
    void setLegacyCovariance(double[][] covariance) {
      coVarianceMatrix = new Matrix(covariance);
      parameterCorrelationMatrix = Matrix.identity(2, 2);
    }
  }

  /** Optimizer whose normal matrix is singular, for testing covariance failure cleanup. */
  private static class SingularStatistics extends LevenbergMarquardt {
    /** {@inheritDoc} */
    @Override
    public double[][] calcAlphaMatrix() {
      return new double[][] {{1.0, 2.0}, {2.0, 4.0}};
    }
  }

  /** Verifies legacy subclass hooks, protected field descriptors and defensive array diagnostics. */
  @Test
  void legacyMatrixApiAndSubclassHooksRemainAvailable() {
    LegacyStatistics optimizer = new LegacyStatistics();
    optimizer.setSampleSet(createOptimizer().getSampleSet());
    optimizer.getSample(0).getFunction().setBounds(new double[][] {{0.0, 1.0}, {0.0, 1.0}});
    Matrix parameters = new Matrix(new double[][] {{-0.5, 2.0}});
    optimizer.checkBounds(parameters);
    assertArrayEquals(new double[] {0.0, 1.0}, parameters.getArray()[0], 0.0);
    optimizer.boundsHookCalled = false;
    double[] values = {-0.5, 2.0};
    optimizer.checkBounds(values);
    assertTrue(optimizer.boundsHookCalled);
    assertArrayEquals(new double[] {0.0, 1.0}, values, 0.0);
    optimizer.setLegacyCovariance(new double[][] {{4.0, 1.0}, {1.0, 9.0}});
    double[][] covariance = optimizer.getCoVarianceMatrix();
    covariance[0][0] = -1.0;
    assertEquals(4.0, optimizer.getCoVarianceMatrix()[0][0], 0.0);
    double[][] correlation = optimizer.getParameterCorrelationMatrix();
    correlation[0][0] = -1.0;
    assertEquals(1.0, optimizer.getParameterCorrelationMatrix()[0][0], 0.0);
    optimizer.calcParameterStandardDeviation();
    assertArrayEquals(new double[] {2.0, 3.0}, optimizer.parameterStandardDeviation, 0.0);
  }

  /**
   * Verifies the legacy display signature without opening a GUI.
   *
   * @throws NoSuchMethodException if the legacy signature was removed
   */
  @Test
  void legacyDisplaySignatureRemainsAvailable() throws NoSuchMethodException {
    Method method = StatisticsBaseClass.class.getMethod("displayMatrix", Matrix.class, String.class, int.class);
    assertEquals(void.class, method.getReturnType());
  }

  /** Verifies that covariance failure restores the previous damping factor. */
  @Test
  void singularCovarianceRestoresDamping() {
    SingularStatistics optimizer = new SingularStatistics();
    optimizer.multiFactor = 7.0;
    assertThrows(LinearAlgebraException.class, optimizer::calcCoVarianceMatrix);
    assertEquals(7.0, optimizer.multiFactor, 0.0);
  }

  /** Verifies the old result constructor, null arguments and defensive array construction. */
  @Test
  void legacyResultConstructorAndArrayFactoryCopyValues() {
    Matrix covariance = new Matrix(new double[][] {{4.0}});
    LevenbergMarquardtResult legacy = new LevenbergMarquardtResult(ConvergenceReason.NOT_RUN, 0, 0.0, 0.0, covariance,
        null, null);
    covariance.set(0, 0, -1.0);
    assertEquals(4.0, legacy.getCovarianceMatrixArray()[0][0], 0.0);
    LevenbergMarquardtResult empty = new LevenbergMarquardtResult(ConvergenceReason.NOT_RUN, 0, 0.0, 0.0, null, null,
        null);
    assertNull(empty.getCovarianceMatrixArray());
    double[][] values = {{9.0}};
    LevenbergMarquardtResult arrayResult = LevenbergMarquardtResult.fromArrays(ConvergenceReason.NOT_RUN, 0, 0.0, 0.0,
        values, null, null);
    values[0][0] = -1.0;
    assertEquals(9.0, arrayResult.getCovarianceMatrixArray()[0][0], 0.0);
  }

  /**
   * Linear test function so that calculated values are analytically known.
   */
  private static class LinearFunction extends LevenbergMarquardtFunction {
    /**
     * Constructor for LinearFunction.
     *
     * @param initialParams an array of type double
     */
    LinearFunction(double[] initialParams) {
      setInitialGuess(initialParams);
    }

    /** {@inheritDoc} */
    @Override
    public double calcValue(double[] dependentValues) {
      return params[0] * dependentValues[0] + params[1];
    }
  }

  /**
   * Builds a sample with an attached linear function.
   *
   * @param sampleValue a double
   * @param standardDeviation a double
   * @param dependentValue a double
   * @return a {@link neqsim.statistics.parameterfitting.SampleValue} object
   */
  private static SampleValue createSample(double sampleValue, double standardDeviation, double dependentValue) {
    SampleValue sample = new SampleValue(sampleValue, standardDeviation, new double[] {dependentValue},
        new double[] {0.0});
    sample.setFunction(new LinearFunction(new double[] {2.0, 1.0}));
    return sample;
  }

  /**
   * Builds an optimizer holding two samples on the line y = 2x + 1.
   *
   * @return a {@link neqsim.statistics.parameterfitting.nonlinearparameterfitting.LevenbergMarquardt} object
   */
  private static LevenbergMarquardt createOptimizer() {
    LevenbergMarquardt optimizer = new LevenbergMarquardt();
    optimizer.setSampleSet(new SampleSet(new SampleValue[] {createSample(4.0, 1.0, 1.0), createSample(4.0, 2.0, 2.0)}));
    return optimizer;
  }

  /**
   * Verifies the default and updated number of tuning parameters.
   */
  @Test
  void numberOfTuningParametersDefaultsToOne() {
    LevenbergMarquardt optimizer = new LevenbergMarquardt();

    assertEquals(1, optimizer.getNumberOfTuningParameters());

    optimizer.setNumberOfTuningParameters(3);

    assertEquals(3, optimizer.getNumberOfTuningParameters());
  }

  /**
   * Verifies that the sample set setter and the sample accessors agree.
   */
  @Test
  void sampleSetAccessorsReturnConfiguredSamples() {
    SampleValue sample = createSample(4.0, 1.0, 1.0);
    SampleSet set = new SampleSet(new SampleValue[] {sample});
    LevenbergMarquardt optimizer = new LevenbergMarquardt();

    optimizer.setSampleSet(set);

    assertSame(set, optimizer.getSampleSet());
    assertSame(sample, optimizer.getSample(0));
  }

  /**
   * Verifies that addSampleSet appends to the already configured sample set.
   */
  @Test
  void addSampleSetExtendsExistingSampleSet() {
    LevenbergMarquardt optimizer = createOptimizer();

    optimizer.addSampleSet(new SampleSet(new SampleValue[] {createSample(7.0, 1.0, 3.0)}));

    assertEquals(3, optimizer.getSampleSet().getLength());
    assertEquals(7.0, optimizer.getSample(2).getSampleValue(), 1.0e-12);
  }

  /**
   * Verifies that calcValue delegates to the sample function.
   */
  @Test
  void calcValueEvaluatesSampleFunction() {
    LevenbergMarquardt optimizer = createOptimizer();

    assertEquals(3.0, optimizer.calcValue(optimizer.getSample(0)), 1.0e-12);
    assertEquals(5.0, optimizer.calcValue(optimizer.getSample(1)), 1.0e-12);
  }

  /**
   * Verifies that calcTrueValue is the identity transform for the default function.
   */
  @Test
  void calcTrueValueIsIdentityByDefault() {
    LevenbergMarquardt optimizer = createOptimizer();
    SampleValue sample = optimizer.getSample(0);

    assertEquals(3.0, optimizer.calcTrueValue(sample), 1.0e-12);
    assertEquals(12.5, optimizer.calcTrueValue(12.5, sample), 1.0e-12);
  }

  /**
   * Verifies the weighted sum of squared residuals for a known sample set.
   */
  @Test
  void calcChiSquareWeightsResidualsByStandardDeviation() {
    LevenbergMarquardt optimizer = createOptimizer();

    assertEquals(1.25, optimizer.calcChiSquare(), 1.0e-12);
  }

  /**
   * Verifies that setFittingParameters updates every sample function.
   */
  @Test
  void setFittingParametersUpdatesAllSamples() {
    LevenbergMarquardt optimizer = createOptimizer();

    optimizer.setFittingParameters(new double[] {3.0, -1.0});

    for (int i = 0; i < optimizer.getSampleSet().getLength(); i++) {
      assertEquals(3.0, optimizer.getSample(i).getFunction().getFittingParams(0), 1.0e-12);
      assertEquals(-1.0, optimizer.getSample(i).getFunction().getFittingParams(1), 1.0e-12);
    }
    assertEquals(2.0, optimizer.calcValue(optimizer.getSample(0)), 1.0e-12);
  }

  /**
   * Verifies that setFittingParameter updates a single index and leaves the others alone.
   */
  @Test
  void setFittingParameterUpdatesSingleIndex() {
    LevenbergMarquardt optimizer = createOptimizer();

    optimizer.setFittingParameter(1, 10.0);

    assertEquals(2.0, optimizer.getSample(0).getFunction().getFittingParams(0), 1.0e-12);
    assertEquals(10.0, optimizer.getSample(0).getFunction().getFittingParams(1), 1.0e-12);
  }

  /**
   * Verifies that checkBounds clamps parameters onto the declared bounds.
   */
  @Test
  void checkBoundsClampsParametersToBounds() {
    LevenbergMarquardt optimizer = createOptimizer();
    optimizer.getSample(0).getFunction().setBounds(new double[][] {{0.0, 1.0}, {0.0, 1.0}});
    double[] parameters = {-0.5, 2.0};

    optimizer.checkBounds(parameters);

    assertEquals(0.0, parameters[0], 1.0e-12);
    assertEquals(1.0, parameters[1], 1.0e-12);
  }

  /**
   * Verifies that checkBounds leaves parameters untouched when no bounds are declared.
   */
  @Test
  void checkBoundsIsNoOpWithoutBounds() {
    LevenbergMarquardt optimizer = createOptimizer();
    double[] parameters = {-0.5, 2.0};

    optimizer.checkBounds(parameters);

    assertEquals(-0.5, parameters[0], 1.0e-12);
    assertEquals(2.0, parameters[1], 1.0e-12);
  }

  /**
   * Verifies that clone detaches the sample set from the original instance.
   */
  @Test
  void cloneDetachesSampleSet() {
    LevenbergMarquardt optimizer = createOptimizer();

    StatisticsBaseClass copy = optimizer.clone();
    copy.setFittingParameter(0, 9.0);

    assertNotSame(optimizer.getSampleSet(), copy.getSampleSet());
    assertEquals(2.0, optimizer.getSample(0).getFunction().getFittingParams(0), 1.0e-12);
    assertEquals(9.0, copy.getSample(0).getFunction().getFittingParams(0), 1.0e-12);
  }

  /**
   * Verifies that createNewRandomClass resamples into a detached copy.
   */
  @Test
  void createNewRandomClassResamplesIntoDetachedCopy() {
    LevenbergMarquardt optimizer = createOptimizer();

    StatisticsBaseClass randomized = optimizer.createNewRandomClass();

    assertNotSame(optimizer.getSampleSet(), randomized.getSampleSet());
    assertEquals(2, randomized.getSampleSet().getLength());
    assertEquals(1.0, randomized.getSample(0).getDependentValue(0), 1.0e-12);
    assertEquals(2.0, randomized.getSample(1).getDependentValue(0), 1.0e-12);
  }
}
