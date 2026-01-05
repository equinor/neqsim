package neqsim.process.calibration;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Complete example demonstrating multi-well heat transfer coefficient estimation using the
 * EnKFParameterEstimator with dynamic routing for improved observability.
 *
 * <h2>Problem Description</h2>
 * <p>
 * A production network consists of 8 subsea wells connected via pipelines to 2 manifolds (HP and
 * LP). Each pipeline has an unknown heat transfer coefficient that affects the temperature drop
 * along the pipe. The goal is to estimate all 8 heat transfer coefficients using only 2 temperature
 * measurements (one at each manifold outlet).
 * </p>
 *
 * <h2>Observability Challenge</h2>
 * <p>
 * With 8 unknowns and only 2 measurements, the system is severely underdetermined in any single
 * configuration. However, by dynamically changing which wells route to which manifold, we
 * accumulate different measurement equations over time, eventually making all parameters
 * observable.
 * </p>
 *
 * <h2>Solution Approach</h2>
 * <p>
 * The example demonstrates:
 * </p>
 * <ul>
 * <li>Building a production network with ProcessSystem</li>
 * <li>Configuring EnKFParameterEstimator with tunable parameters and measurements</li>
 * <li>Pre-deployment validation using EstimationTestHarness</li>
 * <li>Dynamic routing schedule to maximize observability</li>
 * <li>Live estimation loop with result analysis</li>
 * </ul>
 *
 * @author NeqSim Development Team
 * @version 1.0
 * @see EnKFParameterEstimator
 * @see EstimationTestHarness
 */
public class WellRoutingEstimationExample {

  // ============ Configuration ============
  private static final int NUM_WELLS = 8;
  private static final double SEA_TEMPERATURE = 4.0; // °C
  private static final double WELL_TEMPERATURE = 70.0; // °C
  private static final double MEASUREMENT_NOISE_STD = 0.5; // °C

  // Ensemble Kalman Filter configuration
  private static final int ENSEMBLE_SIZE = 50;
  private static final double PROCESS_NOISE = 0.2;
  private static final double MAX_CHANGE_PER_UPDATE = 3.0;

  // ============ True values (for synthetic testing) ============
  private static final double[] TRUE_HEAT_TRANSFER_COEFFS =
      {12.0, 15.0, 18.0, 14.0, 16.0, 20.0, 13.0, 17.0};

  // Well configurations: [pressure bar-a, flowrate kg/hr, pipe length m]
  private static final double[][] WELL_CONFIGS = {{100.0, 50000.0, 8000.0}, {95.0, 45000.0, 8500.0},
      {92.0, 55000.0, 7500.0}, {88.0, 48000.0, 9000.0}, {96.0, 52000.0, 7800.0},
      {90.0, 46000.0, 8200.0}, {94.0, 51000.0, 8800.0}, {86.0, 44000.0, 9200.0}};

  // ============ Process equipment ============
  private ProcessSystem process;
  private Stream[] wellStreams;
  private PipeBeggsAndBrills[] pipes;
  private Splitter[] splitters;
  private Mixer hpManifold;
  private Mixer lpManifold;

  // Current routing state (0 = HP, 1 = LP)
  private int[] currentRouting = new int[NUM_WELLS];

  // Random number generator for measurement noise
  private Random noiseRng = new Random(123);

  /**
   * Creates a typical well fluid.
   *
   * @param temperature wellhead temperature in °C
   * @param pressure wellhead pressure in bar-a
   * @return configured fluid system
   */
  private SystemInterface createWellFluid(double temperature, double pressure) {
    SystemInterface fluid = new SystemSrkEos(273.15 + temperature, pressure);
    fluid.addComponent("methane", 0.82);
    fluid.addComponent("ethane", 0.09);
    fluid.addComponent("propane", 0.05);
    fluid.addComponent("n-butane", 0.025);
    fluid.addComponent("n-pentane", 0.015);
    fluid.setMixingRule("classic");
    return fluid;
  }

  /**
   * Builds the complete production network.
   *
   * <p>
   * Network topology:
   * </p>
   * 
   * <pre>
   * Well1 ─── Pipe1 ─── Splitter1 ──┬── HP Manifold ─── HP Outlet
   * Well2 ─── Pipe2 ─── Splitter2 ──┤                     
   * Well3 ─── Pipe3 ─── Splitter3 ──┤                     
   * Well4 ─── Pipe4 ─── Splitter4 ──┤                     
   * Well5 ─── Pipe5 ─── Splitter5 ──┤                     
   * Well6 ─── Pipe6 ─── Splitter6 ──┤                     
   * Well7 ─── Pipe7 ─── Splitter7 ──┤                     
   * Well8 ─── Pipe8 ─── Splitter8 ──┴── LP Manifold ─── LP Outlet
   * </pre>
   */
  public void buildNetwork() {
    process = new ProcessSystem();
    wellStreams = new Stream[NUM_WELLS];
    pipes = new PipeBeggsAndBrills[NUM_WELLS];
    splitters = new Splitter[NUM_WELLS];

    // Create wells, pipelines, and splitters
    for (int i = 0; i < NUM_WELLS; i++) {
      // Well stream
      SystemInterface fluid = createWellFluid(WELL_TEMPERATURE, WELL_CONFIGS[i][0]);
      wellStreams[i] = new Stream("Well" + (i + 1), fluid);
      wellStreams[i].setFlowRate(WELL_CONFIGS[i][1], "kg/hr");
      process.add(wellStreams[i]);

      // Pipeline with heat transfer
      pipes[i] = new PipeBeggsAndBrills("Pipe" + (i + 1), wellStreams[i]);
      pipes[i].setLength(WELL_CONFIGS[i][2]);
      pipes[i].setDiameter(0.1524); // 6 inch
      pipes[i].setElevation(0.0);
      pipes[i].setRunIsothermal(false);
      pipes[i].setNumberOfIncrements(20);
      pipes[i].setConstantSurfaceTemperature(SEA_TEMPERATURE, "C");
      pipes[i].setHeatTransferCoefficient(TRUE_HEAT_TRANSFER_COEFFS[i]);
      process.add(pipes[i]);

      // Splitter for routing control
      splitters[i] = new Splitter("Splitter" + (i + 1), pipes[i].getOutletStream(), 2);
      process.add(splitters[i]);
    }

    // Create manifolds
    hpManifold = new Mixer("HP Manifold");
    lpManifold = new Mixer("LP Manifold");

    for (int i = 0; i < NUM_WELLS; i++) {
      hpManifold.addStream(splitters[i].getSplitStream(0));
      lpManifold.addStream(splitters[i].getSplitStream(1));
    }

    process.add(hpManifold);
    process.add(lpManifold);

    // Initialize with default routing (all to HP)
    setRouting(new int[] {0, 0, 0, 0, 0, 0, 0, 0});
  }

  /**
   * Sets the well routing configuration.
   *
   * @param routing array of routing decisions (0 = HP, 1 = LP) for each well
   */
  public void setRouting(int[] routing) {
    for (int i = 0; i < NUM_WELLS; i++) {
      if (routing[i] == 0) {
        // Route to HP manifold
        splitters[i].setSplitFactors(new double[] {1.0, 0.0});
      } else {
        // Route to LP manifold
        splitters[i].setSplitFactors(new double[] {0.0, 1.0});
      }
      currentRouting[i] = routing[i];
    }
  }

  /**
   * Gets simulated measurements with noise.
   *
   * @return map of measurement path to noisy value
   */
  public Map<String, Double> getMeasurementsWithNoise() {
    process.run();

    Map<String, Double> measurements = new HashMap<>();

    // HP manifold temperature with noise
    double hpTemp = hpManifold.getOutletStream().getTemperature("C");
    hpTemp += MEASUREMENT_NOISE_STD * noiseRng.nextGaussian();
    measurements.put("HP Manifold.outletStream.temperature", hpTemp);

    // LP manifold temperature with noise
    double lpTemp = lpManifold.getOutletStream().getTemperature("C");
    lpTemp += MEASUREMENT_NOISE_STD * noiseRng.nextGaussian();
    measurements.put("LP Manifold.outletStream.temperature", lpTemp);

    return measurements;
  }

  /**
   * Creates and configures the EnKF estimator.
   *
   * @return configured estimator
   */
  public EnKFParameterEstimator createEstimator() {
    EnKFParameterEstimator estimator = new EnKFParameterEstimator(process);

    // Add all pipe heat transfer coefficients as tunable parameters
    for (int i = 0; i < NUM_WELLS; i++) {
      estimator.addTunableParameter("Pipe" + (i + 1) + ".heatTransferCoefficient", // path
          "W/(m2·K)", // unit
          1.0, // minimum bound
          100.0, // maximum bound
          15.0 // initial guess (biased away from true values)
      );
    }

    // Add manifold temperature measurements
    estimator.addMeasuredVariable("HP Manifold.outletStream.temperature", "C",
        MEASUREMENT_NOISE_STD);
    estimator.addMeasuredVariable("LP Manifold.outletStream.temperature", "C",
        MEASUREMENT_NOISE_STD);

    // Configure filter parameters
    estimator.setProcessNoise(PROCESS_NOISE);
    estimator.setMaxChangePerUpdate(MAX_CHANGE_PER_UPDATE);

    return estimator;
  }

  /**
   * Creates and configures the test harness.
   *
   * @return configured test harness
   */
  public EstimationTestHarness createTestHarness() {
    EstimationTestHarness harness = new EstimationTestHarness(process);
    harness.setSeed(42);

    // Add parameters with true values
    for (int i = 0; i < NUM_WELLS; i++) {
      harness.addParameter("Pipe" + (i + 1) + ".heatTransferCoefficient",
          TRUE_HEAT_TRANSFER_COEFFS[i], 1.0, 100.0);
    }

    // Add measurements
    harness.addMeasurement("HP Manifold.outletStream.temperature", "C", MEASUREMENT_NOISE_STD);
    harness.addMeasurement("LP Manifold.outletStream.temperature", "C", MEASUREMENT_NOISE_STD);

    return harness;
  }

  /**
   * Gets the routing schedule designed for maximum observability.
   *
   * <p>
   * The schedule includes:
   * </p>
   * <ul>
   * <li>Block patterns: groups of wells at each manifold</li>
   * <li>Alternating patterns: interleaved routing</li>
   * <li>Single-well isolation: most informative for individual estimates</li>
   * </ul>
   *
   * @return array of routing configurations
   */
  public int[][] getRoutingSchedule() {
    return new int[][] {
        // Block patterns
        {0, 0, 0, 0, 1, 1, 1, 1}, // Wells 1-4 HP, 5-8 LP
        {1, 1, 1, 1, 0, 0, 0, 0}, // Inverse

        // Alternating patterns
        {0, 1, 0, 1, 0, 1, 0, 1}, // Odd to HP, even to LP
        {1, 0, 1, 0, 1, 0, 1, 0}, // Inverse

        // Pairs patterns
        {0, 0, 1, 1, 0, 0, 1, 1}, {1, 1, 0, 0, 1, 1, 0, 0},

        // Single-well isolation (most informative)
        {0, 1, 1, 1, 1, 1, 1, 1}, // Only Well 1 at HP
        {1, 0, 1, 1, 1, 1, 1, 1}, // Only Well 2 at HP
        {1, 1, 0, 1, 1, 1, 1, 1}, // Only Well 3 at HP
        {1, 1, 1, 0, 1, 1, 1, 1}, // Only Well 4 at HP
        {1, 1, 1, 1, 0, 1, 1, 1}, // Only Well 5 at HP
        {1, 1, 1, 1, 1, 0, 1, 1}, // Only Well 6 at HP
        {1, 1, 1, 1, 1, 1, 0, 1}, // Only Well 7 at HP
        {1, 1, 1, 1, 1, 1, 1, 0}, // Only Well 8 at HP
    };
  }

  /**
   * Runs pre-deployment validation tests.
   *
   * @param estimator the estimator to validate
   * @param harness the test harness
   * @return true if validation passes
   */
  public boolean runValidation(EnKFParameterEstimator estimator, EstimationTestHarness harness) {
    System.out.println("╔══════════════════════════════════════════════════════════════╗");
    System.out.println("║           PRE-DEPLOYMENT VALIDATION                          ║");
    System.out.println("╚══════════════════════════════════════════════════════════════╝");
    System.out.println();

    // Run convergence test with routing changes
    System.out.println("Running convergence test with dynamic routing...");

    int[][] schedule = getRoutingSchedule();
    int stepsPerRouting = 5;
    int totalSteps = schedule.length * stepsPerRouting;

    for (int step = 0; step < totalSteps; step++) {
      // Change routing
      if (step % stepsPerRouting == 0) {
        int routeIdx = (step / stepsPerRouting) % schedule.length;
        setRouting(schedule[routeIdx]);
      }

      // Generate measurement and update
      Map<String, Double> meas = harness.generateMeasurement(1.0);
      estimator.update(meas);
    }

    // Evaluate results
    double[] estimates = estimator.getEstimates();
    double[] uncertainties = estimator.getUncertainties();

    double sumSqError = 0;
    int inCI = 0;

    System.out.println();
    System.out.printf("%-15s %10s %10s %10s %10s %10s%n", "Parameter", "True", "Estimate", "Error%",
        "Std Dev", "In 95%CI?");
    System.out.println(createSeparator(70));

    for (int i = 0; i < NUM_WELLS; i++) {
      double error = Math.abs(estimates[i] - TRUE_HEAT_TRANSFER_COEFFS[i]);
      double errorPct = 100 * error / TRUE_HEAT_TRANSFER_COEFFS[i];
      sumSqError += error * error;

      double ci95 = 1.96 * uncertainties[i];
      boolean withinCI = error <= ci95;
      if (withinCI) {
        inCI++;
      }

      System.out.printf("Pipe%d HTC      %10.2f %10.2f %9.1f%% %10.2f %10s%n", i + 1,
          TRUE_HEAT_TRANSFER_COEFFS[i], estimates[i], errorPct, uncertainties[i],
          withinCI ? "Yes" : "No");
    }

    double rmse = Math.sqrt(sumSqError / NUM_WELLS);
    double coverage = (double) inCI / NUM_WELLS;

    System.out.println(createSeparator(70));
    System.out.printf("RMSE: %.4f, Coverage: %.1f%%%n", rmse, coverage * 100);
    System.out.println();

    // Check criteria
    double maxRMSE = 2.0;
    double minCoverage = 0.75; // 75% for underdetermined system
    boolean passes = rmse <= maxRMSE && coverage >= minCoverage;

    if (passes) {
      System.out.println("✓ VALIDATION PASSED - Ready for deployment");
    } else {
      System.out.println("✗ VALIDATION FAILED - Needs tuning");
      System.out.printf("  RMSE: %.2f (max: %.2f) %s%n", rmse, maxRMSE,
          rmse <= maxRMSE ? "✓" : "✗");
      System.out.printf("  Coverage: %.1f%% (min: %.1f%%) %s%n", coverage * 100, minCoverage * 100,
          coverage >= minCoverage ? "✓" : "✗");
    }

    return passes;
  }

  /**
   * Runs the live estimation loop.
   *
   * @param estimator the estimator to use
   */
  public void runLiveEstimation(EnKFParameterEstimator estimator) {
    System.out.println();
    System.out.println("╔══════════════════════════════════════════════════════════════╗");
    System.out.println("║           LIVE ESTIMATION SIMULATION                          ║");
    System.out.println("╚══════════════════════════════════════════════════════════════╝");
    System.out.println();

    int[][] schedule = getRoutingSchedule();
    int stepsPerRouting = 5;
    int totalSteps = 100;

    System.out.println("True values: " + Arrays.toString(TRUE_HEAT_TRANSFER_COEFFS));
    System.out.println("Initial guess: 15.0 for all wells");
    System.out.println();

    for (int step = 0; step < totalSteps; step++) {
      // Change routing periodically
      if (step % stepsPerRouting == 0) {
        int routeIdx = (step / stepsPerRouting) % schedule.length;
        setRouting(schedule[routeIdx]);

        if (step % 20 == 0) {
          System.out.printf("Step %3d: Routing = %s%n", step, Arrays.toString(currentRouting));
        }
      }

      // Get measurements with noise
      Map<String, Double> measurements = getMeasurementsWithNoise();

      // Update estimator
      EnKFParameterEstimator.EnKFResult result = estimator.update(measurements);

      // Print progress every 20 steps
      if ((step + 1) % 20 == 0) {
        System.out.printf("Step %3d: RMSE=%.4f%n", step + 1, result.getRMSE());
        printEstimates(result.getEstimates(), result.getUncertainties(), "  ");
      }

      // Check for anomalies
      if (result.isAnomalyDetected()) {
        System.out.printf("  ⚠ Step %d: Anomaly detected!%n", step + 1);
      }
    }

    // Final summary
    printFinalSummary(estimator);
  }

  /**
   * Prints current estimates.
   *
   * @param estimates the current parameter estimates
   * @param uncertainties the uncertainties for each parameter
   * @param prefix the prefix to print before estimates
   */
  private void printEstimates(double[] estimates, double[] uncertainties, String prefix) {
    StringBuilder sb = new StringBuilder(prefix + "Estimates: [");
    for (int i = 0; i < estimates.length; i++) {
      sb.append(String.format("%.1f±%.1f", estimates[i], uncertainties[i]));
      if (i < estimates.length - 1) {
        sb.append(", ");
      }
    }
    sb.append("]");
    System.out.println(sb.toString());
  }

  /**
   * Prints final summary comparing estimates to true values.
   *
   * @param estimator the EnKF parameter estimator with results
   */
  private void printFinalSummary(EnKFParameterEstimator estimator) {
    System.out.println();
    System.out.println("╔══════════════════════════════════════════════════════════════╗");
    System.out.println("║                    FINAL RESULTS                              ║");
    System.out.println("╚══════════════════════════════════════════════════════════════╝");
    System.out.println();

    double[] estimates = estimator.getEstimates();
    double[] uncertainties = estimator.getUncertainties();

    System.out.printf("%-10s %12s %12s %10s %15s%n", "Well", "True Value", "Estimate", "Error%",
        "95% CI");
    System.out.println(createSeparator(65));

    double sumAbsError = 0;
    double sumSqError = 0;
    int inCI = 0;

    for (int i = 0; i < NUM_WELLS; i++) {
      double trueVal = TRUE_HEAT_TRANSFER_COEFFS[i];
      double estimate = estimates[i];
      double uncertainty = uncertainties[i];

      double error = Math.abs(estimate - trueVal);
      double errorPct = 100 * error / trueVal;
      sumAbsError += error;
      sumSqError += error * error;

      double ciLower = estimate - 1.96 * uncertainty;
      double ciUpper = estimate + 1.96 * uncertainty;
      boolean withinCI = trueVal >= ciLower && trueVal <= ciUpper;
      if (withinCI) {
        inCI++;
      }

      System.out.printf("Well %-5d %12.2f %12.2f %9.1f%% %7.1f - %-7.1f%n", i + 1, trueVal,
          estimate, errorPct, ciLower, ciUpper);
    }

    System.out.println(createSeparator(65));

    double mae = sumAbsError / NUM_WELLS;
    double rmse = Math.sqrt(sumSqError / NUM_WELLS);
    double coverage = 100.0 * inCI / NUM_WELLS;

    System.out.printf("Mean Absolute Error: %.3f W/(m²·K)%n", mae);
    System.out.printf("Root Mean Square Error: %.3f W/(m²·K)%n", rmse);
    System.out.printf("95%% CI Coverage: %.1f%% (%d/%d parameters)%n", coverage, inCI, NUM_WELLS);
    System.out.printf("Total updates: %d%n", estimator.getUpdateCount());
  }

  /**
   * Creates a separator string.
   *
   * @param length the length of the separator
   * @return a string of dashes of the specified length
   */
  private String createSeparator(int length) {
    char[] chars = new char[length];
    Arrays.fill(chars, '-');
    return new String(chars);
  }

  /**
   * Main entry point.
   *
   * @param args command line arguments (not used)
   */
  public static void main(String[] args) {
    System.out.println();
    System.out.println("╔══════════════════════════════════════════════════════════════════════╗");
    System.out.println("║  Multi-Well Heat Transfer Estimation with Dynamic Routing           ║");
    System.out.println("║  Using EnKFParameterEstimator from neqsim.process.calibration       ║");
    System.out.println("╚══════════════════════════════════════════════════════════════════════╝");
    System.out.println();

    // Create example instance
    WellRoutingEstimationExample example = new WellRoutingEstimationExample();

    // Build the production network
    System.out.println("Building production network with " + NUM_WELLS + " wells...");
    example.buildNetwork();
    System.out.println("Network built successfully.");
    System.out.println();

    // Create estimator for validation
    EnKFParameterEstimator validationEstimator = example.createEstimator();
    validationEstimator.initialize(ENSEMBLE_SIZE, 42);

    // Create test harness
    EstimationTestHarness harness = example.createTestHarness();

    // Run validation
    boolean validationPassed = example.runValidation(validationEstimator, harness);
    System.out.println();

    if (!validationPassed) {
      System.out.println("Continuing with live estimation despite validation failure...");
    }

    // Create fresh estimator for live estimation
    EnKFParameterEstimator liveEstimator = example.createEstimator();
    liveEstimator.initialize(ENSEMBLE_SIZE, 12345); // Different seed

    // Run live estimation
    example.runLiveEstimation(liveEstimator);

    // Convert to CalibrationResult for API compatibility demonstration
    System.out.println();
    System.out.println("Converting to CalibrationResult for API compatibility:");
    CalibrationResult result = liveEstimator.toCalibrationResult();
    System.out.println("  Success: " + result.isSuccess());
    System.out.println("  RMSE: " + result.getRmse());
    System.out.println("  Iterations: " + result.getIterations());
  }
}
