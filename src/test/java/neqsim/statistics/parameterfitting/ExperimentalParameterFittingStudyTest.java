package neqsim.statistics.parameterfitting;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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
   * Verifies specification-driven fitting with transforms, robust objective and validation metrics.
   *
   * @throws Exception if report serialization fails
   */
  @Test
  void studyUsesSpecTransformsRobustObjectiveMultiStartAndReport() throws Exception {
    ExperimentalDataSet training = new ExperimentalDataSet("positive slope", "response", "-",
        new String[] {"x"}, new String[] {"-"});
    training.addPoint(3.0, 0.05, new double[] {1.0});
    training.addPoint(6.0, 0.05, new double[] {2.0});
    training.addPoint(9.0, 0.05, new double[] {3.0});

    ExperimentalDataSet validation = new ExperimentalDataSet("positive slope validation",
        "response", "-", new String[] {"x"}, new String[] {"-"});
    validation.addPoint(12.0, 0.05, new double[] {4.0});

    ParameterFittingSpec spec = new ParameterFittingSpec("positive slope spec");
    spec.setObjectiveFunctionType(ObjectiveFunctionType.HUBER);
    spec.setMaxRobustIterations(2);
    spec.setMultiStartCount(3);
    spec.setRandomSeed(7L);
    spec.setMaxNumberOfIterations(40);
    spec.addParameter(new FittingParameter("gain", 1.0, 0.1, 10.0, "-", ParameterTransform.LOG,
        "scale", Double.NaN, Double.NaN));

    ParameterFittingStudy.Result result =
        new ParameterFittingStudy(training, new PositiveGainFunction(), spec)
            .setValidationDataSet(validation).fit();

    assertTrue(result.isConverged(), result.getOptimizerResult().getConvergenceReason().name());
    assertEquals(ObjectiveFunctionType.HUBER, result.getObjectiveFunctionType());
    assertEquals(2, result.getRobustIterations());
    assertEquals(3.0, result.getFittedParameter("gain"), 1.0e-6);
    assertTrue(result.getValidationRootMeanSquareError() < 1.0e-5);
    assertFalse(new ParameterFittingStudy(training, new PositiveGainFunction(), spec).getSpec()
        .getParameters().isEmpty());

    ParameterFittingReport report =
        new ParameterFittingStudy(training, new PositiveGainFunction(), spec)
            .setValidationDataSet(validation).fitAndCreateReport();
    assertTrue(report.toJson().contains("positive slope spec"));
    assertTrue(report.toMarkdown().contains("gain"));
  }

  /**
   * Verifies JSON and YAML round trips for fitting specifications.
   *
   * @throws Exception if serialization fails
   */
  @Test
  void fittingSpecRoundTripsJsonAndYaml() throws Exception {
    ParameterFittingSpec spec = new ParameterFittingSpec("round trip");
    spec.setExperimentType(ExperimentType.VLE);
    spec.setObjectiveFunctionType(ObjectiveFunctionType.CAUCHY);
    spec.addParameter(new FittingParameter("kij", 0.1, -0.5, 0.5));

    ParameterFittingSpec json = ParameterFittingSpec.fromJson(spec.toJson());
    ParameterFittingSpec yaml = ParameterFittingSpec.fromYaml(spec.toYaml());

    assertEquals("round trip", json.getName());
    assertEquals(ExperimentType.VLE, yaml.getExperimentType());
    assertEquals(ObjectiveFunctionType.CAUCHY, json.getObjectiveFunctionType());
    assertEquals("kij", yaml.getParameterNames()[0]);
  }

  /**
   * Verifies CSV, JSON and YAML data readers including temperature and pressure conversion.
   *
   * @param tempDir temporary directory provided by JUnit
   * @throws Exception if file I/O fails
   */
  @Test
  void dataReadersLoadCsvJsonAndYaml(@TempDir Path tempDir) throws Exception {
    Path csv = tempDir.resolve("data.csv");
    Files.write(csv,
        ("temperature_C,pressure_psi,response,standardDeviation,reference,description\n"
            + "25.0,14.5037738,5.0,0.1,lab,row one\n").getBytes(StandardCharsets.UTF_8));
    ExperimentalDataReader.CsvOptions options = new ExperimentalDataReader.CsvOptions("csv data",
        "response", "-", new String[] {"temperature", "pressure"}, new String[] {"K", "bara"});
    options.setDependentVariableColumns(new String[] {"temperature_C", "pressure_psi"});
    options.setSourceDependentVariableUnits(new String[] {"C", "psi"});
    ExperimentalDataSet csvData = ExperimentalDataSet.fromCsv(csv.toFile(), options);

    assertEquals(298.15, csvData.getPoint(0).getDependentValue(0), 1.0e-10);
    assertEquals(1.0, csvData.getPoint(0).getDependentValue(1), 1.0e-6);
    assertEquals("lab", csvData.getPoint(0).getReference());

    String json = "{\"name\":\"json data\",\"responseName\":\"density\","
        + "\"responseUnit\":\"kg/m3\",\"dependentVariableNames\":[\"temperature\"],"
        + "\"dependentVariableUnits\":[\"K\"],\"points\":[{\"measuredValue\":700.0,"
        + "\"standardDeviation\":1.0,\"dependentValues\":[300.0]}]}";
    ExperimentalDataSet jsonData = ExperimentalDataSet.fromJson(json);
    String yaml = "name: yaml data\nresponseName: density\nresponseUnit: kg/m3\n"
        + "dependentVariableNames:\n  - temperature\ndependentVariableUnits:\n  - K\n"
        + "points:\n  - measuredValue: 701.0\n    standardDeviation: 1.0\n"
        + "    dependentValues:\n      - 301.0\n";
    ExperimentalDataSet yamlData = ExperimentalDataSet.fromYaml(yaml);

    assertEquals("json data", jsonData.getName());
    assertEquals(700.0, jsonData.getPoint(0).getMeasuredValue(), 0.0);
    assertEquals(1, yamlData.size());
  }

  /**
   * Verifies that the documentation CSV and YAML example files are loadable.
   *
   * @throws Exception if example files cannot be read or parsed
   */
  @Test
  void documentationExampleFilesLoad() throws Exception {
    Path examples = Paths.get("docs", "statistics", "examples");
    ExperimentalDataReader.CsvOptions options =
        new ExperimentalDataReader.CsvOptions("documentation csv", "response", "-",
            new String[] {"temperature", "pressure"}, new String[] {"K", "bara"});
    options.setDependentVariableColumns(new String[] {"temperature_C", "pressure_psi"});
    options.setSourceDependentVariableUnits(new String[] {"C", "psi"});

    ExperimentalDataSet csvData = ExperimentalDataSet
        .fromCsv(examples.resolve("parameter_fitting_data.csv").toFile(), options);
    ExperimentalDataSet yamlData =
        ExperimentalDataSet.fromYaml(examples.resolve("parameter_fitting_data.yaml").toFile());
    ParameterFittingSpec spec =
        ParameterFittingSpec.fromYaml(examples.resolve("parameter_fitting_spec.yaml").toFile());

    assertEquals(3, csvData.size());
    assertEquals(3, yamlData.size());
    assertEquals(298.15, csvData.getPoint(0).getDependentValue(0), 1.0e-10);
    assertEquals(1.0, csvData.getPoint(0).getDependentValue(1), 1.0e-6);
    assertEquals(ObjectiveFunctionType.HUBER, spec.getObjectiveFunctionType());
    assertEquals("gain", spec.getParameterNames()[0]);
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

  /**
   * Positive one-parameter gain fitting function.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  private static class PositiveGainFunction extends LevenbergMarquardtFunction {
    /** {@inheritDoc} */
    @Override
    public double calcValue(double[] dependentValues) {
      return params[0] * dependentValues[0];
    }

    /** {@inheritDoc} */
    @Override
    public void setFittingParams(int i, double value) {
      params[i] = value;
    }
  }
}
