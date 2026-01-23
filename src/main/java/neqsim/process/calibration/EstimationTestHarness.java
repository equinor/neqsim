package neqsim.process.calibration;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;
import org.apache.commons.lang3.StringUtils;
import neqsim.process.mpc.ProcessVariableAccessor;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Test harness for validating parameter estimation systems before deployment.
 *
 * <p>
 * This class provides a framework for testing estimation algorithms (EnKF, Gauss-Newton, etc.)
 * using synthetic data with known ground truth, ensuring the estimator works correctly before being
 * deployed with real plant data.
 * </p>
 *
 * <p>
 * <b>Key Testing Capabilities:</b>
 * </p>
 * <ul>
 * <li>Convergence testing - verify estimator converges to true values</li>
 * <li>Noise robustness - test performance under various noise levels</li>
 * <li>Outlier robustness - verify estimator handles bad data</li>
 * <li>Drift tracking - test detection of parameter changes over time</li>
 * <li>Monte Carlo validation - statistical confidence in estimates</li>
 * </ul>
 *
 * <p>
 * <b>Usage Example:</b>
 * </p>
 * 
 * <pre>
 * {@code
 * // Create test harness
 * EstimationTestHarness harness = new EstimationTestHarness(processSystem);
 * 
 * // Define parameters and measurements
 * harness.addParameter("Pipe1.heatTransferCoefficient", 12.0); // true value = 12.0
 * harness.addParameter("Pipe2.heatTransferCoefficient", 18.0);
 * harness.addMeasurement("HPManifold.temperature", "C", 0.5);
 * 
 * // Run convergence test
 * TestReport report = harness.runConvergenceTest(estimator, 50);
 * 
 * // Check results
 * if (report.passes(criteria)) {
 *   System.out.println("Ready for deployment!");
 * }
 * }
 * </pre>
 *
 * @author NeqSim Development Team
 * @version 1.0
 * @see EnKFParameterEstimator
 */
public class EstimationTestHarness implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** The process system to test with. */
  private ProcessSystem processSystem;

  /** Variable accessor for reading/writing. */
  private transient ProcessVariableAccessor variableAccessor;

  /** Parameters with true values. */
  private List<ParameterWithTruth> parameters = new ArrayList<>();

  /** Measurements to generate. */
  private List<MeasurementSpec> measurements = new ArrayList<>();

  /** Random number generator. */
  private transient Random rng;

  /**
   * Parameter specification with ground truth.
   */
  public static class ParameterWithTruth implements Serializable {
    private static final long serialVersionUID = 1000L;

    public final String path;
    public final double trueValue;
    public final double minBound;
    public final double maxBound;

    public ParameterWithTruth(String path, double trueValue, double minBound, double maxBound) {
      this.path = path;
      this.trueValue = trueValue;
      this.minBound = minBound;
      this.maxBound = maxBound;
    }
  }

  /**
   * Measurement specification.
   */
  public static class MeasurementSpec implements Serializable {
    private static final long serialVersionUID = 1000L;

    public final String path;
    public final String unit;
    public final double noiseStd;

    public MeasurementSpec(String path, String unit, double noiseStd) {
      this.path = path;
      this.unit = unit;
      this.noiseStd = noiseStd;
    }
  }

  /**
   * Test report containing results and metrics.
   */
  public static class TestReport implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final String testName;
    private final int numSteps;
    private final double[] finalEstimates;
    private final double[] trueValues;
    private final double[] finalUncertainties;
    private final double rmse;
    private final double meanAbsoluteError;
    private final double maxError;
    private final int stepsToConverge;
    private final double coverageRate;
    private final List<double[]> estimateHistory;
    private final List<double[]> rmseHistory;

    /**
     * Constructor.
     *
     * @param testName the name of the test
     * @param numSteps the number of estimation steps
     * @param finalEstimates the final parameter estimates
     * @param trueValues the true parameter values
     * @param finalUncertainties the final uncertainties
     * @param estimateHistory history of estimates at each step
     * @param rmseHistory history of RMSE values at each step
     */
    public TestReport(String testName, int numSteps, double[] finalEstimates, double[] trueValues,
        double[] finalUncertainties, List<double[]> estimateHistory, List<double[]> rmseHistory) {
      this.testName = testName;
      this.numSteps = numSteps;
      this.finalEstimates = finalEstimates.clone();
      this.trueValues = trueValues.clone();
      this.finalUncertainties = finalUncertainties.clone();
      this.estimateHistory = new ArrayList<>(estimateHistory);
      this.rmseHistory = new ArrayList<>(rmseHistory);

      // Calculate metrics
      double sumAbsError = 0;
      double sumSqError = 0;
      double maxErr = 0;
      int inCI = 0;

      for (int i = 0; i < finalEstimates.length; i++) {
        double error = Math.abs(finalEstimates[i] - trueValues[i]);
        sumAbsError += error;
        sumSqError += error * error;
        maxErr = Math.max(maxErr, error);

        // Check if true value is within 95% CI
        double ci95 = 1.96 * finalUncertainties[i];
        if (error <= ci95) {
          inCI++;
        }
      }

      this.meanAbsoluteError = sumAbsError / finalEstimates.length;
      this.rmse = Math.sqrt(sumSqError / finalEstimates.length);
      this.maxError = maxErr;
      this.coverageRate = (double) inCI / finalEstimates.length;

      // Find convergence step (when error drops below threshold)
      this.stepsToConverge = findConvergenceStep(0.1);
    }

    private int findConvergenceStep(double threshold) {
      for (int step = 0; step < estimateHistory.size(); step++) {
        double[] est = estimateHistory.get(step);
        double maxRelError = 0;
        for (int i = 0; i < est.length; i++) {
          if (Math.abs(trueValues[i]) > 1e-10) {
            maxRelError = Math.max(maxRelError, Math.abs(est[i] - trueValues[i]) / trueValues[i]);
          }
        }
        if (maxRelError < threshold) {
          return step + 1;
        }
      }
      return numSteps; // Didn't converge
    }

    /**
     * Gets the test name.
     *
     * @return the test name
     */
    public String getTestName() {
      return testName;
    }

    /**
     * Gets the RMSE of final estimates.
     *
     * @return the RMSE value
     */
    public double getRMSE() {
      return rmse;
    }

    /**
     * Gets mean absolute error.
     *
     * @return the mean absolute error
     */
    public double getMeanAbsoluteError() {
      return meanAbsoluteError;
    }

    /**
     * Gets maximum error.
     *
     * @return the maximum error
     */
    public double getMaxError() {
      return maxError;
    }

    /**
     * Gets steps to convergence.
     *
     * @return the number of steps to converge
     */
    public int getStepsToConverge() {
      return stepsToConverge;
    }

    /**
     * Gets coverage rate (fraction of true values in 95% CI).
     *
     * @return the coverage rate
     */
    public double getCoverageRate() {
      return coverageRate;
    }

    /**
     * Gets final estimates.
     *
     * @return the final estimates array
     */
    public double[] getFinalEstimates() {
      return finalEstimates.clone();
    }

    /**
     * Gets true values.
     *
     * @return the true values array
     */
    public double[] getTrueValues() {
      return trueValues.clone();
    }

    /**
     * Checks if test passes given criteria.
     *
     * @param maxRMSE maximum allowed RMSE
     * @param minCoverage minimum coverage rate
     * @param maxConvergenceSteps maximum steps to converge
     * @return true if all criteria pass
     */
    public boolean passes(double maxRMSE, double minCoverage, int maxConvergenceSteps) {
      return rmse <= maxRMSE && coverageRate >= minCoverage
          && stepsToConverge <= maxConvergenceSteps;
    }

    /**
     * Prints summary to console.
     */
    public void printSummary() {
      System.out.println("\n=== Test Report: " + testName + " ===");
      System.out.printf("Steps: %d, Converged at: %d%n", numSteps, stepsToConverge);
      System.out.printf("RMSE: %.4f, MAE: %.4f, Max Error: %.4f%n", rmse, meanAbsoluteError,
          maxError);
      System.out.printf("Coverage (95%% CI): %.1f%%%n", coverageRate * 100);

      System.out.println("\nPer-parameter results:");
      System.out.printf("%-30s %10s %10s %10s %10s%n", "Parameter", "True", "Estimate", "Error%",
          "In CI?");
      System.out.println(StringUtils.repeat("-", 75));

      for (int i = 0; i < finalEstimates.length; i++) {
        double errorPct =
            100 * Math.abs(finalEstimates[i] - trueValues[i]) / Math.max(1e-10, trueValues[i]);
        double ci95 = 1.96 * finalUncertainties[i];
        boolean inCI = Math.abs(finalEstimates[i] - trueValues[i]) <= ci95;
        System.out.printf("%-30s %10.3f %10.3f %9.1f%% %10s%n", "Param " + (i + 1), trueValues[i],
            finalEstimates[i], errorPct, inCI ? "Yes" : "No");
      }
    }
  }

  /**
   * Creates a test harness for a process system.
   *
   * @param processSystem the process system to test with
   */
  public EstimationTestHarness(ProcessSystem processSystem) {
    this.processSystem = processSystem;
    this.variableAccessor = new ProcessVariableAccessor(processSystem);
    this.rng = new Random(42);
  }

  /**
   * Sets the random seed for reproducibility.
   *
   * @param seed random seed
   * @return this harness for chaining
   */
  public EstimationTestHarness setSeed(long seed) {
    this.rng = new Random(seed);
    return this;
  }

  /**
   * Adds a parameter with known true value.
   *
   * @param path variable path
   * @param trueValue ground truth value
   * @return this harness for chaining
   */
  public EstimationTestHarness addParameter(String path, double trueValue) {
    return addParameter(path, trueValue, trueValue * 0.1, trueValue * 2.0);
  }

  /**
   * Adds a parameter with known true value and bounds.
   *
   * @param path variable path
   * @param trueValue ground truth value
   * @param minBound minimum bound
   * @param maxBound maximum bound
   * @return this harness for chaining
   */
  public EstimationTestHarness addParameter(String path, double trueValue, double minBound,
      double maxBound) {
    parameters.add(new ParameterWithTruth(path, trueValue, minBound, maxBound));
    return this;
  }

  /**
   * Adds a measurement variable.
   *
   * @param path variable path
   * @param unit unit of measurement
   * @param noiseStd measurement noise standard deviation
   * @return this harness for chaining
   */
  public EstimationTestHarness addMeasurement(String path, String unit, double noiseStd) {
    measurements.add(new MeasurementSpec(path, unit, noiseStd));
    return this;
  }

  /**
   * Generates synthetic measurements using true parameter values.
   *
   * @param noiseMultiplier multiplier for noise level
   * @return map of measurement path to noisy value
   */
  public Map<String, Double> generateMeasurement(double noiseMultiplier) {
    // Set true parameters
    for (ParameterWithTruth param : parameters) {
      variableAccessor.setValue(param.path, param.trueValue);
    }

    // Run simulation
    processSystem.run();

    // Read measurements with noise
    Map<String, Double> meas = new HashMap<>();
    for (MeasurementSpec spec : measurements) {
      double value = variableAccessor.getValue(spec.path, spec.unit);
      value += noiseMultiplier * spec.noiseStd * rng.nextGaussian();
      meas.put(spec.path, value);
    }

    return meas;
  }

  /**
   * Runs a convergence test.
   *
   * @param estimator the estimator to test
   * @param numSteps number of update steps
   * @return test report
   */
  public TestReport runConvergenceTest(EnKFParameterEstimator estimator, int numSteps) {
    return runConvergenceTest(estimator, numSteps, 1.0, null);
  }

  /**
   * Runs a convergence test with optional progress callback.
   *
   * @param estimator the estimator to test
   * @param numSteps number of update steps
   * @param noiseMultiplier noise level multiplier
   * @param progressCallback optional callback for progress updates
   * @return test report
   */
  public TestReport runConvergenceTest(EnKFParameterEstimator estimator, int numSteps,
      double noiseMultiplier, Consumer<Integer> progressCallback) {
    List<double[]> estimateHistory = new ArrayList<>();
    List<double[]> rmseHistory = new ArrayList<>();

    for (int step = 1; step <= numSteps; step++) {
      // Generate measurement with true parameters
      Map<String, Double> meas = generateMeasurement(noiseMultiplier);

      // Update estimator
      EnKFParameterEstimator.EnKFResult result = estimator.update(meas);

      // Record history
      estimateHistory.add(result.getEstimates());
      rmseHistory.add(new double[] {result.getRMSE()});

      if (progressCallback != null) {
        progressCallback.accept(step);
      }
    }

    // Get true values
    double[] trueValues = parameters.stream().mapToDouble(p -> p.trueValue).toArray();

    return new TestReport("Convergence Test", numSteps, estimator.getEstimates(), trueValues,
        estimator.getUncertainties(), estimateHistory, rmseHistory);
  }

  /**
   * Runs a noise robustness test at multiple noise levels.
   *
   * @param estimator the estimator to test
   * @param stepsPerLevel steps at each noise level
   * @param noiseLevels array of noise multipliers to test
   * @return map of noise level to test report
   */
  public Map<Double, TestReport> runNoiseRobustnessTest(EnKFParameterEstimator estimator,
      int stepsPerLevel, double[] noiseLevels) {
    Map<Double, TestReport> reports = new HashMap<>();

    for (double noiseLevel : noiseLevels) {
      estimator.reset();

      TestReport report = runConvergenceTest(estimator, stepsPerLevel, noiseLevel, null);
      reports.put(noiseLevel, report);
    }

    return reports;
  }

  /**
   * Runs a parameter drift tracking test.
   *
   * @param estimator the estimator to test
   * @param numSteps number of steps
   * @param driftingParamIndex which parameter drifts
   * @param driftRate rate of drift per step
   * @return test report with tracking accuracy
   */
  public TestReport runDriftTrackingTest(EnKFParameterEstimator estimator, int numSteps,
      int driftingParamIndex, double driftRate) {
    List<double[]> estimateHistory = new ArrayList<>();
    List<double[]> rmseHistory = new ArrayList<>();

    // Start with true values
    double[] currentTrueValues = parameters.stream().mapToDouble(p -> p.trueValue).toArray();

    for (int step = 1; step <= numSteps; step++) {
      // Apply drift
      currentTrueValues[driftingParamIndex] += driftRate;

      // Set drifting parameters
      for (int i = 0; i < parameters.size(); i++) {
        variableAccessor.setValue(parameters.get(i).path, currentTrueValues[i]);
      }

      // Generate measurement
      processSystem.run();
      Map<String, Double> meas = new HashMap<>();
      for (MeasurementSpec spec : measurements) {
        double value = variableAccessor.getValue(spec.path, spec.unit);
        value += spec.noiseStd * rng.nextGaussian();
        meas.put(spec.path, value);
      }

      // Update estimator
      EnKFParameterEstimator.EnKFResult result = estimator.update(meas);

      estimateHistory.add(result.getEstimates());
      rmseHistory.add(new double[] {result.getRMSE()});
    }

    return new TestReport("Drift Tracking Test", numSteps, estimator.getEstimates(),
        currentTrueValues, estimator.getUncertainties(), estimateHistory, rmseHistory);
  }

  /**
   * Runs Monte Carlo validation with multiple trials.
   *
   * @param estimatorFactory factory to create fresh estimator instances
   * @param numTrials number of Monte Carlo trials
   * @param stepsPerTrial steps per trial
   * @return summary statistics across all trials
   */
  public MonteCarloReport runMonteCarloValidation(
      java.util.function.Supplier<EnKFParameterEstimator> estimatorFactory, int numTrials,
      int stepsPerTrial) {
    List<Double> rmseValues = new ArrayList<>();
    List<Double> coverageValues = new ArrayList<>();
    int successCount = 0;

    for (int trial = 0; trial < numTrials; trial++) {
      // Fresh estimator for each trial
      EnKFParameterEstimator estimator = estimatorFactory.get();

      // Fresh random seed for measurements
      rng = new Random(42 + trial);

      TestReport report = runConvergenceTest(estimator, stepsPerTrial, 1.0, null);

      rmseValues.add(report.getRMSE());
      coverageValues.add(report.getCoverageRate());

      if (report.getCoverageRate() >= 0.9) {
        successCount++;
      }
    }

    return new MonteCarloReport(numTrials, stepsPerTrial, rmseValues, coverageValues, successCount);
  }

  /**
   * Monte Carlo validation report.
   */
  public static class MonteCarloReport implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final int numTrials;
    private final int stepsPerTrial;
    private final double meanRMSE;
    private final double stdRMSE;
    private final double percentile95RMSE;
    private final double meanCoverage;
    private final double successRate;

    public MonteCarloReport(int numTrials, int stepsPerTrial, List<Double> rmseValues,
        List<Double> coverageValues, int successCount) {
      this.numTrials = numTrials;
      this.stepsPerTrial = stepsPerTrial;

      // Calculate RMSE statistics
      double sumRMSE = 0;
      for (Double r : rmseValues) {
        sumRMSE += r;
      }
      this.meanRMSE = sumRMSE / numTrials;

      double sumSqRMSE = 0;
      for (Double r : rmseValues) {
        sumSqRMSE += (r - meanRMSE) * (r - meanRMSE);
      }
      this.stdRMSE = Math.sqrt(sumSqRMSE / numTrials);

      // Sort for percentile
      List<Double> sortedRMSE = new ArrayList<>(rmseValues);
      sortedRMSE.sort(Double::compareTo);
      int idx95 = (int) (0.95 * numTrials);
      this.percentile95RMSE = sortedRMSE.get(Math.min(idx95, sortedRMSE.size() - 1));

      // Coverage statistics
      double sumCov = 0;
      for (Double c : coverageValues) {
        sumCov += c;
      }
      this.meanCoverage = sumCov / numTrials;
      this.successRate = (double) successCount / numTrials;
    }

    /**
     * Gets mean RMSE across all trials.
     *
     * @return the mean RMSE value
     */
    public double getMeanRMSE() {
      return meanRMSE;
    }

    /**
     * Gets standard deviation of RMSE.
     *
     * @return the standard deviation of RMSE
     */
    public double getStdRMSE() {
      return stdRMSE;
    }

    /**
     * Gets 95th percentile RMSE.
     *
     * @return the 95th percentile RMSE
     */
    public double getPercentile95RMSE() {
      return percentile95RMSE;
    }

    /**
     * Gets mean coverage rate.
     *
     * @return the mean coverage rate
     */
    public double getMeanCoverage() {
      return meanCoverage;
    }

    /**
     * Gets success rate (trials with &gt;=90% coverage).
     *
     * @return the success rate
     */
    public double getSuccessRate() {
      return successRate;
    }

    /** Prints summary. */
    public void printSummary() {
      System.out.println("\n=== Monte Carlo Validation Report ===");
      System.out.printf("Trials: %d, Steps per trial: %d%n", numTrials, stepsPerTrial);
      System.out.printf("Mean RMSE: %.4f ± %.4f%n", meanRMSE, stdRMSE);
      System.out.printf("95th percentile RMSE: %.4f%n", percentile95RMSE);
      System.out.printf("Mean coverage: %.1f%%%n", meanCoverage * 100);
      System.out.printf("Success rate: %.1f%%%n", successRate * 100);
    }
  }
}
