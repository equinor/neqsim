package neqsim.process.calibration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for BatchParameterEstimator.
 *
 * <p>
 * These tests verify the batch parameter estimation framework using simple process models.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
class BatchParameterEstimatorTest extends neqsim.NeqSimTest {

  private ProcessSystem process;
  private Heater heater1;
  private Heater heater2;

  @BeforeEach
  void setUp() {
    // Create a simple process with two heaters feeding into a mixer
    SystemInterface fluid = new SystemSrkEos(300.0, 50.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule("classic");

    Stream feed1 = new Stream("feed1", fluid);
    feed1.setFlowRate(100.0, "kg/hr");
    feed1.setTemperature(20.0, "C");
    feed1.setPressure(50.0, "bara");

    heater1 = new Heater("heater1", feed1);
    heater1.setOutTemperature(50.0, "C"); // Will be a "true" parameter

    Stream feed2 = new Stream("feed2", fluid.clone());
    feed2.setFlowRate(100.0, "kg/hr");
    feed2.setTemperature(20.0, "C");
    feed2.setPressure(50.0, "bara");

    heater2 = new Heater("heater2", feed2);
    heater2.setOutTemperature(60.0, "C"); // Will be a "true" parameter

    Mixer mixer = new Mixer("mixer");
    mixer.addStream(heater1.getOutletStream());
    mixer.addStream(heater2.getOutletStream());

    process = new ProcessSystem();
    process.add(feed1);
    process.add(heater1);
    process.add(feed2);
    process.add(heater2);
    process.add(mixer);
  }

  @Test
  void testConstructor() {
    BatchParameterEstimator estimator = new BatchParameterEstimator(process);
    assertNotNull(estimator);
    assertEquals(process, estimator.getProcessSystem());
  }

  @Test
  void testFluentConfiguration() {
    BatchParameterEstimator estimator = new BatchParameterEstimator(process);

    estimator.addTunableParameter("heater1.outTemperature", "C", 30.0, 80.0, 45.0)
        .addTunableParameter("heater2.outTemperature", "C", 40.0, 90.0, 55.0)
        .addMeasuredVariable("mixer.outletStream.temperature", "C", 0.5).setMaxIterations(50);

    assertEquals(2, estimator.getParameterNames().length);
    assertEquals(1, estimator.getMeasurementNames().length);
  }

  @Test
  void testAddDataPoint() {
    BatchParameterEstimator estimator = new BatchParameterEstimator(process);

    estimator.addTunableParameter("heater1.outTemperature", "C", 30.0, 80.0, 45.0)
        .addMeasuredVariable("mixer.outletStream.temperature", "C", 0.5);

    // Add a data point
    Map<String, Double> conditions = new HashMap<>();
    conditions.put("feed1.flowRate", 100.0);

    Map<String, Double> measurements = new HashMap<>();
    measurements.put("mixer.outletStream.temperature", 55.0);

    estimator.addDataPoint(conditions, measurements);

    assertEquals(1, estimator.getDataPointCount());
  }

  @Test
  void testBatchResultCreation() {
    String[] names = {"param1", "param2"};
    double[] estimates = {10.0, 20.0};
    double[] uncertainties = {0.5, 1.0};
    double chiSquare = 5.0;
    int iterations = 25;
    int dataPoints = 50;
    boolean converged = true;

    BatchResult result = new BatchResult(names, estimates, uncertainties, chiSquare, iterations,
        dataPoints, converged);

    assertNotNull(result);
    assertEquals(2, result.getEstimates().length);
    assertEquals(10.0, result.getEstimate(0), 1e-6);
    assertEquals(20.0, result.getEstimate("param2"), 1e-6);
    assertEquals(0.5, result.getUncertainty(0), 1e-6);
    assertEquals(5.0, result.getChiSquare(), 1e-6);
    assertTrue(result.isConverged());
    assertEquals(25, result.getIterations());
    assertEquals(50, result.getDataPointCount());
    assertEquals(48, result.getDegreesOfFreedom());
  }

  @Test
  void testBatchResultConfidenceIntervals() {
    String[] names = {"param1"};
    double[] estimates = {10.0};
    double[] uncertainties = {1.0}; // std dev = 1.0

    BatchResult result = new BatchResult(names, estimates, uncertainties, 1.0, 10, 20, true);

    // 95% CI should be estimate ± 1.96 * std
    double[] lower = result.getConfidenceIntervalLower();
    double[] upper = result.getConfidenceIntervalUpper();

    assertEquals(10.0 - 1.96, lower[0], 1e-6);
    assertEquals(10.0 + 1.96, upper[0], 1e-6);
  }

  @Test
  void testBatchResultToMap() {
    String[] names = {"param1", "param2"};
    double[] estimates = {10.0, 20.0};
    double[] uncertainties = {0.5, 1.0};

    BatchResult result = new BatchResult(names, estimates, uncertainties, 1.0, 10, 20, true);

    Map<String, Double> map = result.toMap();

    assertEquals(2, map.size());
    assertEquals(10.0, map.get("param1"), 1e-6);
    assertEquals(20.0, map.get("param2"), 1e-6);
  }

  @Test
  void testBatchResultToCalibrationResult() {
    String[] names = {"param1"};
    double[] estimates = {10.0};
    double[] uncertainties = {0.5};

    BatchResult result = new BatchResult(names, estimates, uncertainties, 1.0, 10, 20, true);

    CalibrationResult calResult = result.toCalibrationResult();

    assertTrue(calResult.isSuccess());
    assertNotNull(calResult.getParameters());
    assertEquals(10.0, calResult.getParameters().get("param1"), 1e-6);
  }

  @Test
  void testProcessSimulationFunctionConfiguration() {
    ProcessSimulationFunction function = new ProcessSimulationFunction(process);

    function.addParameter("heater1.outTemperature", 30.0, 80.0)
        .addParameter("heater2.outTemperature", 40.0, 90.0)
        .addMeasurement("mixer.outletStream.temperature");

    assertEquals(2, function.getParameterCount());
    assertEquals(1, function.getMeasurementCount());
    assertEquals("heater1.outTemperature", function.getParameterPaths().get(0));
    assertEquals("mixer.outletStream.temperature", function.getMeasurementPaths().get(0));
  }

  @Test
  void testProcessSimulationFunctionBounds() {
    ProcessSimulationFunction function = new ProcessSimulationFunction(process);

    function.addParameter("heater1.outTemperature", 30.0, 80.0);

    double[][] bounds = function.getBounds();
    assertNotNull(bounds);
    assertEquals(1, bounds.length);
    assertEquals(30.0, bounds[0][0], 1e-6);
    assertEquals(80.0, bounds[0][1], 1e-6);
  }

  @Test
  void testTunableParameterClass() {
    BatchParameterEstimator.TunableParameter param =
        new BatchParameterEstimator.TunableParameter("path.to.param", "C", 0.0, 100.0, 50.0);

    assertEquals("path.to.param", param.getPath());
    assertEquals("C", param.getUnit());
    assertEquals(0.0, param.getLowerBound(), 1e-6);
    assertEquals(100.0, param.getUpperBound(), 1e-6);
    assertEquals(50.0, param.getInitialGuess(), 1e-6);
  }

  @Test
  void testMeasuredVariableClass() {
    BatchParameterEstimator.MeasuredVariable meas =
        new BatchParameterEstimator.MeasuredVariable("path.to.meas", "C", 0.5);

    assertEquals("path.to.meas", meas.getPath());
    assertEquals("C", meas.getUnit());
    assertEquals(0.5, meas.getStandardDeviation(), 1e-6);
  }

  @Test
  void testDataPointClass() {
    Map<String, Double> conditions = new HashMap<>();
    conditions.put("feed.flowRate", 100.0);

    Map<String, Double> measurements = new HashMap<>();
    measurements.put("outlet.temperature", 55.0);

    BatchParameterEstimator.DataPoint dp =
        new BatchParameterEstimator.DataPoint(conditions, measurements);

    assertEquals(100.0, dp.getConditions().get("feed.flowRate"), 1e-6);
    assertEquals(55.0, dp.getMeasurements().get("outlet.temperature"), 1e-6);
  }

  @Test
  void testReset() {
    BatchParameterEstimator estimator = new BatchParameterEstimator(process);

    estimator.addTunableParameter("heater1.outTemperature", "C", 30.0, 80.0, 45.0)
        .addMeasuredVariable("mixer.outletStream.temperature", "C", 0.5)
        .addDataPoint(new HashMap<>(), new HashMap<>());

    assertEquals(1, estimator.getParameterNames().length);
    assertEquals(1, estimator.getMeasurementNames().length);
    assertEquals(1, estimator.getDataPointCount());

    estimator.reset();

    assertEquals(0, estimator.getParameterNames().length);
    assertEquals(0, estimator.getMeasurementNames().length);
    assertEquals(0, estimator.getDataPointCount());
  }

  @Test
  void testClearDataPoints() {
    BatchParameterEstimator estimator = new BatchParameterEstimator(process);

    estimator.addDataPoint(new HashMap<>(), new HashMap<>()).addDataPoint(new HashMap<>(),
        new HashMap<>());

    assertEquals(2, estimator.getDataPointCount());

    estimator.clearDataPoints();

    assertEquals(0, estimator.getDataPointCount());
  }
}
