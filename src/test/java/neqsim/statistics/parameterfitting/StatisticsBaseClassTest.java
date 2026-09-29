package neqsim.statistics.parameterfitting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import org.junit.jupiter.api.Test;
import Jama.Matrix;
import neqsim.statistics.parameterfitting.nonlinearparameterfitting.LevenbergMarquardt;
import neqsim.statistics.parameterfitting.nonlinearparameterfitting.LevenbergMarquardtFunction;

/**
 * Unit tests for {@link neqsim.statistics.parameterfitting.StatisticsBaseClass}, exercised through its concrete
 * {@link neqsim.statistics.parameterfitting.nonlinearparameterfitting.LevenbergMarquardt} subclass.
 *
 * @author asmf
 * @version 1.0
 */
class StatisticsBaseClassTest {
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
    Matrix parameters = new Matrix(new double[][] {{-0.5, 2.0}});

    optimizer.checkBounds(parameters);

    assertEquals(0.0, parameters.get(0, 0), 1.0e-12);
    assertEquals(1.0, parameters.get(0, 1), 1.0e-12);
  }

  /**
   * Verifies that checkBounds leaves parameters untouched when no bounds are declared.
   */
  @Test
  void checkBoundsIsNoOpWithoutBounds() {
    LevenbergMarquardt optimizer = createOptimizer();
    Matrix parameters = new Matrix(new double[][] {{-0.5, 2.0}});

    optimizer.checkBounds(parameters);

    assertEquals(-0.5, parameters.get(0, 0), 1.0e-12);
    assertEquals(2.0, parameters.get(0, 1), 1.0e-12);
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
