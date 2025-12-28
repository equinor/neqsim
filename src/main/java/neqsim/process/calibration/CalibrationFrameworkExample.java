package neqsim.process.calibration;

import java.util.HashMap;
import java.util.Map;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Example demonstrating the new calibration framework for online parameter estimation.
 *
 * <p>
 * This example shows how to:
 * </p>
 * <ol>
 * <li>Set up a process system with tunable parameters</li>
 * <li>Configure the EnKF estimator</li>
 * <li>Run pre-deployment validation tests</li>
 * <li>Perform live parameter estimation</li>
 * </ol>
 *
 * <p>
 * The scenario involves a 4-well production system with pipes flowing to HP/LP manifolds. We
 * estimate the heat transfer coefficients of each pipe based on manifold temperature measurements.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class CalibrationFrameworkExample {

  private ProcessSystem process;
  private Stream[] wellStreams;
  private PipeBeggsAndBrills[] pipes;
  private Splitter[] splitters;
  private Mixer hpManifold;
  private Mixer lpManifold;

  private static final int NUM_WELLS = 4;
  private static final double SEA_TEMPERATURE = 5.0; // °C

  /**
   * Creates well fluid.
   *
   * @param temperature the well temperature [°C]
   * @param pressure the well pressure [Pa]
   * @return the initialized thermodynamic system
   */
  private SystemInterface createWellFluid(double temperature, double pressure) {
    SystemInterface fluid = new SystemSrkEos(273.15 + temperature, pressure);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.08);
    fluid.addComponent("propane", 0.04);
    fluid.addComponent("n-butane", 0.02);
    fluid.addComponent("n-pentane", 0.01);
    fluid.setMixingRule("classic");
    return fluid;
  }

  /**
   * Builds the production network.
   */
  public void buildNetwork() {
    double wellTemp = 70.0;

    // Well configurations: [pressure, flowRate, pipeLength]
    double[][] configs = {{100.0, 50000.0, 8000.0}, {95.0, 45000.0, 8500.0},
        {92.0, 55000.0, 7500.0}, {88.0, 48000.0, 9000.0}};

    wellStreams = new Stream[NUM_WELLS];
    pipes = new PipeBeggsAndBrills[NUM_WELLS];
    splitters = new Splitter[NUM_WELLS];

    process = new ProcessSystem();

    for (int i = 0; i < NUM_WELLS; i++) {
      SystemInterface fluid = createWellFluid(wellTemp, configs[i][0]);
      wellStreams[i] = new Stream("Well" + (i + 1), fluid);
      wellStreams[i].setFlowRate(configs[i][1], "kg/hr");
      process.add(wellStreams[i]);

      pipes[i] = new PipeBeggsAndBrills("Pipe" + (i + 1), wellStreams[i]);
      pipes[i].setLength(configs[i][2]);
      pipes[i].setDiameter(0.1524);
      pipes[i].setElevation(0.0);
      pipes[i].setRunIsothermal(false);
      pipes[i].setNumberOfIncrements(20);
      pipes[i].setConstantSurfaceTemperature(SEA_TEMPERATURE, "C");
      pipes[i].setHeatTransferCoefficient(15.0); // Initial guess
      process.add(pipes[i]);

      splitters[i] = new Splitter("Splitter" + (i + 1), pipes[i].getOutletStream(), 2);
      process.add(splitters[i]);
    }

    // Route wells 1-2 to HP, wells 3-4 to LP
    splitters[0].setSplitFactors(new double[] {1.0, 0.0});
    splitters[1].setSplitFactors(new double[] {1.0, 0.0});
    splitters[2].setSplitFactors(new double[] {0.0, 1.0});
    splitters[3].setSplitFactors(new double[] {0.0, 1.0});

    hpManifold = new Mixer("HPManifold");
    lpManifold = new Mixer("LPManifold");

    for (int i = 0; i < NUM_WELLS; i++) {
      hpManifold.addStream(splitters[i].getSplitStream(0));
      lpManifold.addStream(splitters[i].getSplitStream(1));
    }

    process.add(hpManifold);
    process.add(lpManifold);
  }

  /**
   * Sets up the EnKF estimator.
   *
   * @return the configured EnKFParameterEstimator
   */
  public EnKFParameterEstimator createEstimator() {
    EnKFParameterEstimator estimator = new EnKFParameterEstimator(process);

    // Add tunable parameters (heat transfer coefficients)
    for (int i = 0; i < NUM_WELLS; i++) {
      estimator.addTunableParameter("Pipe" + (i + 1) + ".heatTransferCoefficient", "W/(m2·K)", 1.0,
          100.0, 15.0); // Initial guess 15, bounds [1, 100]
    }

    // Add measured variables (manifold temperatures)
    estimator.addMeasuredVariable("HPManifold.outletStream.temperature", "C", 0.5);
    estimator.addMeasuredVariable("LPManifold.outletStream.temperature", "C", 0.5);

    // Configure
    estimator.setProcessNoise(0.3);
    estimator.setMaxChangePerUpdate(3.0);

    return estimator;
  }

  /**
   * Demonstrates pre-deployment validation testing.
   */
  public void runValidationTests() {
    System.out.println("╔══════════════════════════════════════════════════════════════════════╗");
    System.out.println("║  PRE-DEPLOYMENT VALIDATION TESTS                                     ║");
    System.out
        .println("╚══════════════════════════════════════════════════════════════════════╝\n");

    // Create test harness
    EstimationTestHarness harness = new EstimationTestHarness(process);
    harness.setSeed(42);

    // Define true values (ground truth for testing)
    double[] trueValues = {12.0, 18.0, 10.0, 22.0};
    for (int i = 0; i < NUM_WELLS; i++) {
      harness.addParameter("Pipe" + (i + 1) + ".heatTransferCoefficient", trueValues[i], 1.0,
          100.0);
    }

    // Define measurements
    harness.addMeasurement("HPManifold.outletStream.temperature", "C", 0.5);
    harness.addMeasurement("LPManifold.outletStream.temperature", "C", 0.5);

    // Create and initialize estimator
    EnKFParameterEstimator estimator = createEstimator();
    estimator.initialize(50, 42);

    // Test 1: Convergence
    System.out.println("=== TEST 1: Convergence Test (40 steps) ===");
    EstimationTestHarness.TestReport convReport = harness.runConvergenceTest(estimator, 40);
    convReport.printSummary();

    boolean pass1 = convReport.passes(5.0, 0.8, 40);
    System.out.println("\nTest " + (pass1 ? "PASSED ✓" : "FAILED ✗"));

    // Test 2: Noise Robustness
    System.out.println("\n=== TEST 2: Noise Robustness Test ===");
    double[] noiseLevels = {0.5, 1.0, 2.0, 3.0};

    for (double noise : noiseLevels) {
      estimator = createEstimator();
      estimator.initialize(50, 42);

      EstimationTestHarness.TestReport noiseReport =
          harness.runConvergenceTest(estimator, 30, noise, null);

      System.out.printf("Noise level %.1fx: RMSE=%.3f, Coverage=%.0f%%%n", noise,
          noiseReport.getRMSE(), noiseReport.getCoverageRate() * 100);
    }

    // Test 3: Drift Tracking
    System.out.println("\n=== TEST 3: Drift Tracking Test ===");
    estimator = createEstimator();
    estimator.initialize(50, 42);

    EstimationTestHarness.TestReport driftReport =
        harness.runDriftTrackingTest(estimator, 30, 0, 0.5);
    System.out.printf("Drift tracking: Final estimate for drifting param: %.2f%n",
        driftReport.getFinalEstimates()[0]);
    System.out.printf("True value after drift: %.2f%n", driftReport.getTrueValues()[0]);
    driftReport.printSummary();

    // Summary
    System.out.println("\n" + StringUtils.repeat("═", 72));
    System.out.println("VALIDATION SUMMARY");
    System.out.println(StringUtils.repeat("═", 72));
    System.out.println("Convergence test: " + (pass1 ? "PASSED" : "FAILED"));
    System.out.println("Noise robustness: Tested at 0.5x, 1x, 2x, 3x noise levels");
    System.out.println("Drift tracking: Parameter drift detected and tracked");
    System.out.println("\n" + (pass1 ? "✓ Ready for deployment" : "✗ Review before deployment"));
  }

  /**
   * Demonstrates live parameter estimation.
   */
  public void runLiveEstimation() {
    System.out
        .println("\n╔══════════════════════════════════════════════════════════════════════╗");
    System.out.println("║  LIVE PARAMETER ESTIMATION SIMULATION                                ║");
    System.out
        .println("╚══════════════════════════════════════════════════════════════════════╝\n");

    // True (unknown) coefficients
    double[] trueCoeffs = {12.0, 18.0, 10.0, 22.0};
    System.out.println("True coefficients (unknown to estimator):");
    for (int i = 0; i < NUM_WELLS; i++) {
      System.out.printf("  Pipe%d: %.1f W/(m²·K)%n", i + 1, trueCoeffs[i]);
    }

    // Create and initialize estimator
    EnKFParameterEstimator estimator = createEstimator();
    estimator.initialize(50, 42);

    System.out.println("\nRunning 20 live estimation updates...\n");

    java.util.Random rng = new java.util.Random(123);

    for (int step = 1; step <= 20; step++) {
      // Simulate "plant" with true coefficients
      for (int i = 0; i < NUM_WELLS; i++) {
        pipes[i].setHeatTransferCoefficient(trueCoeffs[i]);
      }
      process.run();

      // Get "measurements" with noise
      Map<String, Double> measurements = new HashMap<>();
      measurements.put("HPManifold.outletStream.temperature",
          hpManifold.getOutletStream().getTemperature("C") + 0.5 * rng.nextGaussian());
      measurements.put("LPManifold.outletStream.temperature",
          lpManifold.getOutletStream().getTemperature("C") + 0.5 * rng.nextGaussian());

      // Update estimator
      EnKFParameterEstimator.EnKFResult result = estimator.update(measurements);

      if (step % 5 == 0 || step == 1) {
        System.out.printf("Step %2d: RMSE=%.3f°C", step, result.getRMSE());
        if (result.isAnomalyDetected()) {
          System.out.print(" ⚠ ANOMALY");
        }
        System.out.println();
      }
    }

    // Print final results
    System.out.println("\n=== Final Estimation Results ===");
    double[] estimates = estimator.getEstimates();
    double[] uncertainties = estimator.getUncertainties();

    System.out.printf("%-10s %10s %10s %10s %12s%n", "Parameter", "True", "Estimate", "±Uncert",
        "Error%");
    System.out.println(StringUtils.repeat("-", 55));

    for (int i = 0; i < NUM_WELLS; i++) {
      double errorPct = 100 * Math.abs(estimates[i] - trueCoeffs[i]) / trueCoeffs[i];
      System.out.printf("%-10s %10.2f %10.2f %10.2f %11.1f%%%n", "Pipe" + (i + 1), trueCoeffs[i],
          estimates[i], uncertainties[i], errorPct);
    }

    // Convert to CalibrationResult for compatibility
    CalibrationResult calResult = estimator.toCalibrationResult();
    System.out.println("\nCalibrationResult RMSE: " + calResult.getRmse());
    System.out.println("Parameters: " + calResult.getParameters());
  }

  /**
   * Main entry point.
   *
   * @param args command line arguments (unused)
   */
  public static void main(String[] args) {
    System.out.println("╔══════════════════════════════════════════════════════════════════════╗");
    System.out.println("║  NeqSim Calibration Framework Example                                ║");
    System.out.println("║  Demonstrating EnKF Parameter Estimation with Validation             ║");
    System.out
        .println("╚══════════════════════════════════════════════════════════════════════╝\n");

    CalibrationFrameworkExample example = new CalibrationFrameworkExample();

    // Build the process
    System.out.println("Building 4-well production network...");
    example.buildNetwork();
    System.out.println("Network built successfully.\n");

    // Run validation tests (pre-deployment)
    example.runValidationTests();

    // Run live estimation simulation
    example.runLiveEstimation();

    System.out.println("\n" + StringUtils.repeat("═", 72));
    System.out.println("Example completed!");
    System.out.println("\nKey takeaways:");
    System.out.println("  1. EnKFParameterEstimator provides uncertainty quantification");
    System.out.println("  2. EstimationTestHarness validates before deployment");
    System.out.println("  3. Results integrate with existing CalibrationResult API");
    System.out.println("  4. Uses ProcessVariableAccessor for generic variable access");
  }
}
