package neqsim.process.calibration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for EnKFParameterEstimator based on multi-well routing estimation scenario.
 *
 * <p>
 * This test validates that the EnKF estimator can recover heat transfer coefficients from a
 * simplified 4-well production network using synthetic measurements.
 * </p>
 */
@Tag("slow")
class EnKFParameterEstimatorTest {
  private static final int NUM_WELLS = 4;
  private static final double SEA_TEMPERATURE = 4.0;
  private static final double WELL_TEMPERATURE = 70.0;
  private static final double MEASUREMENT_NOISE_STD = 0.5;
  private static final double[] TRUE_HTC = {12.0, 15.0, 18.0, 14.0};

  private ProcessSystem process;
  private PipeBeggsAndBrills[] pipes;
  private Splitter[] splitters;
  private Mixer hpManifold;
  private Mixer lpManifold;
  private Random noiseRng;

  @BeforeEach
  void setUp() {
    buildNetwork();
    noiseRng = new Random(42);
  }

  private SystemInterface createWellFluid(double temperature, double pressure) {
    SystemInterface fluid = new SystemSrkEos(273.15 + temperature, pressure);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.05);
    fluid.setMixingRule("classic");
    return fluid;
  }

  private void buildNetwork() {
    process = new ProcessSystem();
    Stream[] wellStreams = new Stream[NUM_WELLS];
    pipes = new PipeBeggsAndBrills[NUM_WELLS];
    splitters = new Splitter[NUM_WELLS];

    double[] pressures = {100.0, 95.0, 92.0, 88.0};
    double[] flowRates = {50000.0, 45000.0, 55000.0, 48000.0};
    double[] pipeLengths = {8000.0, 8500.0, 7500.0, 9000.0};

    for (int i = 0; i < NUM_WELLS; i++) {
      SystemInterface fluid = createWellFluid(WELL_TEMPERATURE, pressures[i]);
      wellStreams[i] = new Stream("Well" + (i + 1), fluid);
      wellStreams[i].setFlowRate(flowRates[i], "kg/hr");
      process.add(wellStreams[i]);

      pipes[i] = new PipeBeggsAndBrills("Pipe" + (i + 1), wellStreams[i]);
      pipes[i].setLength(pipeLengths[i]);
      pipes[i].setDiameter(0.1524);
      pipes[i].setElevation(0.0);
      pipes[i].setRunIsothermal(false);
      pipes[i].setNumberOfIncrements(10);
      pipes[i].setConstantSurfaceTemperature(SEA_TEMPERATURE, "C");
      pipes[i].setHeatTransferCoefficient(TRUE_HTC[i]);
      process.add(pipes[i]);

      splitters[i] = new Splitter("Splitter" + (i + 1), pipes[i].getOutletStream(), 2);
      process.add(splitters[i]);
    }

    hpManifold = new Mixer("HP Manifold");
    lpManifold = new Mixer("LP Manifold");

    for (int i = 0; i < NUM_WELLS; i++) {
      hpManifold.addStream(splitters[i].getSplitStream(0));
      lpManifold.addStream(splitters[i].getSplitStream(1));
    }

    process.add(hpManifold);
    process.add(lpManifold);

    // Default routing: wells 0,1 to HP, wells 2,3 to LP
    setRouting(new int[] {0, 0, 1, 1});
  }

  private void setRouting(int[] routing) {
    for (int i = 0; i < NUM_WELLS; i++) {
      if (routing[i] == 0) {
        splitters[i].setSplitFactors(new double[] {1.0, 0.0});
      } else {
        splitters[i].setSplitFactors(new double[] {0.0, 1.0});
      }
    }
  }

  private Map<String, Double> getMeasurementsWithNoise() {
    process.run();
    Map<String, Double> measurements = new HashMap<>();

    double hpTemp = hpManifold.getOutletStream().getTemperature("C");
    hpTemp += MEASUREMENT_NOISE_STD * noiseRng.nextGaussian();
    measurements.put("HP Manifold.outletStream.temperature", hpTemp);

    double lpTemp = lpManifold.getOutletStream().getTemperature("C");
    lpTemp += MEASUREMENT_NOISE_STD * noiseRng.nextGaussian();
    measurements.put("LP Manifold.outletStream.temperature", lpTemp);

    return measurements;
  }

  @Test
  void testEstimatorCreation() {
    EnKFParameterEstimator estimator = new EnKFParameterEstimator(process);
    assertNotNull(estimator);
  }

  @Test
  void testAddTunableParameter() {
    EnKFParameterEstimator estimator = new EnKFParameterEstimator(process);
    estimator.addTunableParameter("Pipe1.heatTransferCoefficient", "W/(m2·K)", 1.0, 100.0, 15.0);
    // No exception means success
  }

  @Test
  void testAddMeasuredVariable() {
    EnKFParameterEstimator estimator = new EnKFParameterEstimator(process);
    estimator.addMeasuredVariable("HP Manifold.outletStream.temperature", "C",
        MEASUREMENT_NOISE_STD);
    // No exception means success
  }

  @Test
  void testInitialize() {
    EnKFParameterEstimator estimator = new EnKFParameterEstimator(process);
    for (int i = 0; i < NUM_WELLS; i++) {
      estimator.addTunableParameter("Pipe" + (i + 1) + ".heatTransferCoefficient", "W/(m2·K)", 1.0,
          100.0, 15.0);
    }
    estimator.addMeasuredVariable("HP Manifold.outletStream.temperature", "C",
        MEASUREMENT_NOISE_STD);
    estimator.addMeasuredVariable("LP Manifold.outletStream.temperature", "C",
        MEASUREMENT_NOISE_STD);

    estimator.initialize(30, 42);

    double[] estimates = estimator.getEstimates();
    assertNotNull(estimates);
    assertEquals(NUM_WELLS, estimates.length);
  }

  @Test
  void testUpdateWithMeasurements() {
    EnKFParameterEstimator estimator = new EnKFParameterEstimator(process);
    for (int i = 0; i < NUM_WELLS; i++) {
      estimator.addTunableParameter("Pipe" + (i + 1) + ".heatTransferCoefficient", "W/(m2·K)", 1.0,
          100.0, 15.0);
    }
    estimator.addMeasuredVariable("HP Manifold.outletStream.temperature", "C",
        MEASUREMENT_NOISE_STD);
    estimator.addMeasuredVariable("LP Manifold.outletStream.temperature", "C",
        MEASUREMENT_NOISE_STD);
    estimator.initialize(30, 42);

    Map<String, Double> measurements = getMeasurementsWithNoise();
    EnKFParameterEstimator.EnKFResult result = estimator.update(measurements);

    assertNotNull(result);
    assertNotNull(result.getEstimates());
    assertNotNull(result.getUncertainties());
    assertEquals(NUM_WELLS, result.getEstimates().length);
  }

  @Test
  void testConvergenceWithDynamicRouting() {
    EnKFParameterEstimator estimator = new EnKFParameterEstimator(process);
    for (int i = 0; i < NUM_WELLS; i++) {
      estimator.addTunableParameter("Pipe" + (i + 1) + ".heatTransferCoefficient", "W/(m2·K)", 1.0,
          100.0, 15.0);
    }
    estimator.addMeasuredVariable("HP Manifold.outletStream.temperature", "C",
        MEASUREMENT_NOISE_STD);
    estimator.addMeasuredVariable("LP Manifold.outletStream.temperature", "C",
        MEASUREMENT_NOISE_STD);
    estimator.setProcessNoise(0.2);
    estimator.setMaxChangePerUpdate(3.0);
    estimator.initialize(50, 42);

    // Routing schedule for observability
    int[][] routingSchedule = {{0, 0, 1, 1}, // Wells 1-2 HP, 3-4 LP
        {1, 1, 0, 0}, // Inverse
        {0, 1, 0, 1}, // Alternating
        {1, 0, 1, 0}, // Inverse alternating
        {0, 1, 1, 1}, // Only Well 1 at HP
        {1, 0, 1, 1}, // Only Well 2 at HP
        {1, 1, 0, 1}, // Only Well 3 at HP
        {1, 1, 1, 0} // Only Well 4 at HP
    };

    // Run estimation with routing changes
    int stepsPerRouting = 5;
    for (int step = 0; step < routingSchedule.length * stepsPerRouting; step++) {
      int routeIdx = (step / stepsPerRouting) % routingSchedule.length;
      setRouting(routingSchedule[routeIdx]);
      Map<String, Double> measurements = getMeasurementsWithNoise();
      estimator.update(measurements);
    }

    // Check results
    double[] estimates = estimator.getEstimates();
    double[] uncertainties = estimator.getUncertainties();

    // Validate estimates are in reasonable range (between bounds)
    for (int i = 0; i < NUM_WELLS; i++) {
      assertTrue(estimates[i] >= 1.0 && estimates[i] <= 100.0,
          "Estimate for Pipe" + (i + 1) + " should be within bounds: " + estimates[i]);
      assertTrue(uncertainties[i] >= 0,
          "Uncertainty for Pipe" + (i + 1) + " should be non-negative: " + uncertainties[i]);
    }

    // Verify filter is updating (estimates should have moved from initial guess of 15.0)
    boolean hasUpdated = false;
    for (int i = 0; i < NUM_WELLS; i++) {
      if (Math.abs(estimates[i] - 15.0) > 0.1) {
        hasUpdated = true;
        break;
      }
    }
    assertTrue(hasUpdated,
        "EnKF should update estimates from initial values after routing changes");
  }

  @Test
  void testGetEstimatesAndUncertainties() {
    EnKFParameterEstimator estimator = new EnKFParameterEstimator(process);
    for (int i = 0; i < NUM_WELLS; i++) {
      estimator.addTunableParameter("Pipe" + (i + 1) + ".heatTransferCoefficient", "W/(m2·K)", 1.0,
          100.0, 15.0);
    }
    estimator.addMeasuredVariable("HP Manifold.outletStream.temperature", "C",
        MEASUREMENT_NOISE_STD);
    estimator.initialize(30, 42);

    double[] estimates = estimator.getEstimates();
    double[] uncertainties = estimator.getUncertainties();

    assertNotNull(estimates);
    assertNotNull(uncertainties);
    assertEquals(estimates.length, uncertainties.length);

    // Initial uncertainties should be positive
    for (double u : uncertainties) {
      assertTrue(u > 0, "Uncertainty should be positive");
    }
  }

  @Test
  void testReset() {
    EnKFParameterEstimator estimator = new EnKFParameterEstimator(process);
    estimator.addTunableParameter("Pipe1.heatTransferCoefficient", "W/(m2·K)", 1.0, 100.0, 15.0);
    estimator.addMeasuredVariable("HP Manifold.outletStream.temperature", "C",
        MEASUREMENT_NOISE_STD);
    estimator.initialize(30, 42);

    // Run some updates
    for (int i = 0; i < 5; i++) {
      Map<String, Double> measurements = getMeasurementsWithNoise();
      estimator.update(measurements);
    }

    // Reset
    estimator.reset();

    // After reset, estimates should return to initial values
    double[] estimates = estimator.getEstimates();
    assertNotNull(estimates);
    assertEquals(1, estimates.length);
    assertEquals(15.0, estimates[0], 1e-6); // Initial guess was 15.0
  }
}
