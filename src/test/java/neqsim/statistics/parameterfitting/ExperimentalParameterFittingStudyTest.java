package neqsim.statistics.parameterfitting;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.statistics.parameterfitting.nonlinearparameterfitting.LevenbergMarquardtFunction;

/**
 * Tests for the experimental data parameter fitting workflow.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class ExperimentalParameterFittingStudyTest {

  /**
   * Verifies metadata preservation when converting experimental data to SampleSet.
   */
  @Test
  void dataSetConvertsToSampleSetWithMetadata() {
    ExperimentalDataSet dataSet = new ExperimentalDataSet("density data", "density", "kg/m3",
        new String[] {"temperature", "pressure"}, new String[] {"K", "bara"});
    dataSet.addPoint(725.0, 1.5, new double[] {298.15, 10.0}, "lab-1", "single phase liquid");
    LinearExperimentalFunction function = new LinearExperimentalFunction();
    function.setInitialGuess(new double[] {1.0, 1.0});

    SampleSet sampleSet = dataSet.toSampleSet(function);

    assertEquals(1, sampleSet.getLength());
    assertEquals(725.0, sampleSet.getSample(0).getSampleValue(), 0.0);
    assertArrayEquals(new double[] {298.15, 10.0}, sampleSet.getSample(0).getDependentValues(),
        0.0);
    assertEquals("lab-1", sampleSet.getSample(0).getReference());
    assertEquals("single phase liquid", sampleSet.getSample(0).getDescription());
    assertSame(function, sampleSet.getSample(0).getFunction());
  }

  /**
   * Verifies fitting, residual metrics and parameter lookup for a simple experimental data set.
   */
  @Test
  void studyFitsExperimentalDataAndReportsResiduals() {
    ExperimentalDataSet dataSet = new ExperimentalDataSet("linear calibration", "response", "-",
        new String[] {"x"}, new String[] {"-"});
    for (int i = -3; i <= 3; i++) {
      double x = i;
      dataSet.addPoint(2.5 * x - 1.2, 0.1, new double[] {x});
    }

    LinearExperimentalFunction function = new LinearExperimentalFunction();
    ParameterFittingStudy.Result result = new ParameterFittingStudy(dataSet, function)
        .setInitialGuess(new double[] {0.5, 0.0})
        .setParameterNames(new String[] {"slope", "intercept"}).setMaxNumberOfIterations(30).run();

    assertTrue(result.isConverged(), result.getOptimizerResult().getConvergenceReason().name());
    assertEquals(2.5, result.getFittedParameter("slope"), 1.0e-6);
    assertEquals(-1.2, result.getFittedParameter("intercept"), 1.0e-6);
    assertEquals(7, result.getCalculatedValues().length);
    assertEquals(7, result.getResiduals().length);
    assertEquals(7, result.getWeightedResiduals().length);
    assertTrue(result.getRootMeanSquareError() < 1.0e-5);
    assertTrue(result.getMeanAbsoluteError() < 1.0e-5);
    assertTrue(result.getWeightedRootMeanSquareError() < 1.0e-4);
    assertNotNull(result.getOptimizerResult().getCovarianceMatrix());
  }

  /**
   * Verifies the documented binary-interaction style fitting workflow.
   */
  @Test
  void studyFitsBinaryInteractionStyleParameter() {
    ExperimentalDataSet dataSet = new ExperimentalDataSet("synthetic VLE", "vapor methane fraction",
        "-", new String[] {"temperature", "liquid methane fraction"}, new String[] {"K", "-"});
    double trueKij = 0.12;
    dataSet.addPoint(0.6288, 0.001, new double[] {250.0, 0.60}, "synthetic",
        "binary interaction benchmark");
    dataSet.addPoint(0.6778, 0.001, new double[] {255.0, 0.65}, "synthetic",
        "binary interaction benchmark");
    dataSet.addPoint(0.7262, 0.001, new double[] {260.0, 0.70}, "synthetic",
        "binary interaction benchmark");
    dataSet.addPoint(0.7740, 0.001, new double[] {265.0, 0.75}, "synthetic",
        "binary interaction benchmark");
    dataSet.addPoint(0.8212, 0.001, new double[] {270.0, 0.80}, "synthetic",
        "binary interaction benchmark");

    BinaryInteractionSurrogateFunction function = new BinaryInteractionSurrogateFunction();
    ParameterFittingStudy.Result result = new ParameterFittingStudy(dataSet, function)
        .setInitialGuess(new double[] {0.0}).setParameterNames(new String[] {"kij"})
        .setParameterBounds(new double[][] {{-0.5, 0.5}}).setMaxNumberOfIterations(40).fit();

    assertTrue(result.isConverged(), result.getOptimizerResult().getConvergenceReason().name());
    assertEquals(trueKij, result.getFittedParameter("kij"), 1.0e-6);
    assertTrue(result.getRootMeanSquareError() < 1.0e-7);
  }

  /**
   * Verifies deterministic training-validation splitting.
   */
  @Test
  void splitCreatesTrainingAndValidationDataSets() {
    ExperimentalDataSet dataSet = new ExperimentalDataSet("split data", "response", "-",
        new String[] {"x"}, new String[] {"-"});
    dataSet.addPoint(1.0, 0.1, new double[] {1.0});
    dataSet.addPoint(2.0, 0.1, new double[] {2.0});
    dataSet.addPoint(3.0, 0.1, new double[] {3.0});
    dataSet.addPoint(4.0, 0.1, new double[] {4.0});

    ExperimentalDataSet[] split = dataSet.split(0.75);

    assertEquals(3, split[0].size());
    assertEquals(1, split[1].size());
    assertEquals("split data training", split[0].getName());
    assertEquals("split data validation", split[1].getName());
    assertEquals(4.0, split[1].getPoint(0).getMeasuredValue(), 0.0);
  }

  /**
   * Linear two-parameter experimental fitting function.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  private static class LinearExperimentalFunction extends LevenbergMarquardtFunction {
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
   * Synthetic binary-interaction response function for documentation-style testing.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  private static class BinaryInteractionSurrogateFunction extends LevenbergMarquardtFunction {
    /** {@inheritDoc} */
    @Override
    public double calcValue(double[] dependentValues) {
      double temperature = dependentValues[0];
      double liquidMethaneFraction = dependentValues[1];
      return liquidMethaneFraction
          + params[0] * liquidMethaneFraction * (1.0 - liquidMethaneFraction)
          + 1.0e-4 * (temperature - 250.0);
    }

    /** {@inheritDoc} */
    @Override
    public void setFittingParams(int i, double value) {
      params[i] = value;
    }
  }
}
