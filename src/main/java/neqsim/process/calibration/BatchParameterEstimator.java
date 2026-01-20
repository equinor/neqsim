package neqsim.process.calibration;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.statistics.parameterfitting.SampleSet;
import neqsim.statistics.parameterfitting.SampleValue;
import neqsim.statistics.parameterfitting.nonlinearparameterfitting.LevenbergMarquardt;

/**
 * Batch parameter estimator using Levenberg-Marquardt optimization.
 *
 * <p>
 * This class provides batch (offline) parameter estimation for process models using historical or
 * experimental data. It bridges the process calibration framework with NeqSim's existing
 * Levenberg-Marquardt optimizer in the statistics package.
 * </p>
 *
 * <p>
 * <b>When to use BatchParameterEstimator vs EnKFParameterEstimator:</b>
 * </p>
 * <ul>
 * <li><b>Multiple parameters, batch/historical data:</b> BatchParameterEstimator (this class)</li>
 * <li><b>Live streaming data, uncertainty tracking:</b> EnKFParameterEstimator</li>
 * <li><b>Single parameter tuning:</b> Adjuster class</li>
 * </ul>
 *
 * <h2>Usage Example:</h2>
 * 
 * <pre>
 * {@code
 * // 1. Build your process
 * ProcessSystem process = buildProductionNetwork();
 * 
 * // 2. Create batch estimator
 * BatchParameterEstimator estimator = new BatchParameterEstimator(process);
 * 
 * // 3. Define parameters to estimate
 * estimator.addTunableParameter("Pipe1.heatTransferCoefficient", "W/(m2·K)", 1.0, 100.0, 15.0);
 * estimator.addTunableParameter("Pipe2.heatTransferCoefficient", "W/(m2·K)", 1.0, 100.0, 15.0);
 * 
 * // 4. Define measurements
 * estimator.addMeasuredVariable("Manifold.outletStream.temperature", "C", 0.5);
 * 
 * // 5. Add historical data points
 * for (HistoricalRecord record : historicalData) {
 *   Map<String, Double> conditions = new HashMap<>();
 *   conditions.put("feedStream.flowRate", record.getFlowRate());
 * 
 *   Map<String, Double> measurements = new HashMap<>();
 *   measurements.put("Manifold.outletStream.temperature", record.getOutletTemp());
 * 
 *   estimator.addDataPoint(conditions, measurements);
 * }
 * 
 * // 6. Configure and solve
 * estimator.setMaxIterations(100);
 * BatchResult result = estimator.solve();
 * 
 * // 7. Use results
 * result.printSummary();
 * double[] estimates = result.getEstimates();
 * double[] uncertainties = result.getUncertainties();
 * }
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 * @see ProcessSimulationFunction
 * @see BatchResult
 * @see LevenbergMarquardt
 */
public class BatchParameterEstimator implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(BatchParameterEstimator.class);

  /** The process system to calibrate. */
  private final ProcessSystem processSystem;

  /** Tunable parameters. */
  private final List<TunableParameter> tunableParameters;

  /** Measured variables. */
  private final List<MeasuredVariable> measuredVariables;

  /** Data points for calibration. */
  private final List<DataPoint> dataPoints;

  /** Maximum number of iterations. */
  private int maxIterations = 100;

  /** Whether to use analytical Jacobian from ProcessSensitivityAnalyzer. */
  private boolean useAnalyticalJacobian = false;

  /** The underlying function for the optimizer. */
  private ProcessSimulationFunction function;

  /** The Levenberg-Marquardt optimizer. */
  private LevenbergMarquardt optimizer;

  /** Result from the last solve. */
  private BatchResult lastResult;

  /**
   * Represents a tunable parameter.
   */
  public static class TunableParameter implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String path;
    private final String unit;
    private final double lowerBound;
    private final double upperBound;
    private final double initialGuess;

    /**
     * Creates a tunable parameter.
     *
     * @param path path to the parameter
     * @param unit unit of the parameter
     * @param lowerBound minimum value
     * @param upperBound maximum value
     * @param initialGuess initial guess for optimization
     */
    public TunableParameter(String path, String unit, double lowerBound, double upperBound,
        double initialGuess) {
      this.path = path;
      this.unit = unit;
      this.lowerBound = lowerBound;
      this.upperBound = upperBound;
      this.initialGuess = initialGuess;
    }

    public String getPath() {
      return path;
    }

    public String getUnit() {
      return unit;
    }

    public double getLowerBound() {
      return lowerBound;
    }

    public double getUpperBound() {
      return upperBound;
    }

    public double getInitialGuess() {
      return initialGuess;
    }
  }

  /**
   * Represents a measured variable.
   */
  public static class MeasuredVariable implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String path;
    private final String unit;
    private final double standardDeviation;

    /**
     * Creates a measured variable.
     *
     * @param path path to the measurement
     * @param unit unit of the measurement
     * @param standardDeviation measurement uncertainty (noise std dev)
     */
    public MeasuredVariable(String path, String unit, double standardDeviation) {
      this.path = path;
      this.unit = unit;
      this.standardDeviation = standardDeviation;
    }

    public String getPath() {
      return path;
    }

    public String getUnit() {
      return unit;
    }

    public double getStandardDeviation() {
      return standardDeviation;
    }
  }

  /**
   * Represents a data point with conditions and measurements.
   */
  public static class DataPoint implements Serializable {
    private static final long serialVersionUID = 1L;
    private final Map<String, Double> conditions;
    private final Map<String, Double> measurements;

    /**
     * Creates a data point.
     *
     * @param conditions operating conditions (path -&gt; value)
     * @param measurements measured values (path -&gt; value)
     */
    public DataPoint(Map<String, Double> conditions, Map<String, Double> measurements) {
      this.conditions = new HashMap<>(conditions);
      this.measurements = new HashMap<>(measurements);
    }

    public Map<String, Double> getConditions() {
      return conditions;
    }

    public Map<String, Double> getMeasurements() {
      return measurements;
    }
  }

  /**
   * Creates a new batch parameter estimator.
   *
   * @param processSystem the process system to calibrate
   */
  public BatchParameterEstimator(ProcessSystem processSystem) {
    this.processSystem = processSystem;
    this.tunableParameters = new ArrayList<>();
    this.measuredVariables = new ArrayList<>();
    this.dataPoints = new ArrayList<>();
  }

  // ==================== Fluent Configuration API ====================

  /**
   * Adds a tunable parameter.
   *
   * @param path path to the parameter (e.g., "Pipe1.heatTransferCoefficient")
   * @param unit unit of the parameter
   * @param lowerBound minimum allowed value
   * @param upperBound maximum allowed value
   * @param initialGuess initial guess for optimization
   * @return this estimator for chaining
   */
  public BatchParameterEstimator addTunableParameter(String path, String unit, double lowerBound,
      double upperBound, double initialGuess) {
    tunableParameters.add(new TunableParameter(path, unit, lowerBound, upperBound, initialGuess));
    return this;
  }

  /**
   * Adds a measured variable.
   *
   * @param path path to the measurement (e.g., "Manifold.outletStream.temperature")
   * @param unit unit of the measurement
   * @param standardDeviation measurement uncertainty (noise standard deviation)
   * @return this estimator for chaining
   */
  public BatchParameterEstimator addMeasuredVariable(String path, String unit,
      double standardDeviation) {
    measuredVariables.add(new MeasuredVariable(path, unit, standardDeviation));
    return this;
  }

  /**
   * Adds a data point for calibration.
   *
   * @param conditions operating conditions to apply (path -&gt; value)
   * @param measurements measured values (path -&gt; value)
   * @return this estimator for chaining
   */
  public BatchParameterEstimator addDataPoint(Map<String, Double> conditions,
      Map<String, Double> measurements) {
    dataPoints.add(new DataPoint(conditions, measurements));
    return this;
  }

  /**
   * Adds a data point with only measurements (no condition changes).
   *
   * @param measurements measured values (path -&gt; value)
   * @return this estimator for chaining
   */
  public BatchParameterEstimator addDataPoint(Map<String, Double> measurements) {
    return addDataPoint(new HashMap<>(), measurements);
  }

  /**
   * Sets the maximum number of iterations.
   *
   * @param maxIterations maximum iterations
   * @return this estimator for chaining
   */
  public BatchParameterEstimator setMaxIterations(int maxIterations) {
    this.maxIterations = maxIterations;
    return this;
  }

  /**
   * Enables or disables analytical Jacobian computation.
   *
   * <p>
   * When enabled, uses {@link neqsim.process.util.sensitivity.ProcessSensitivityAnalyzer} for more
   * efficient derivative computation, potentially reusing Broyden Jacobians from recycle
   * convergence.
   * </p>
   *
   * @param useAnalytical true to use analytical Jacobian
   * @return this estimator for chaining
   */
  public BatchParameterEstimator setUseAnalyticalJacobian(boolean useAnalytical) {
    this.useAnalyticalJacobian = useAnalytical;
    return this;
  }

  // ==================== Solve ====================

  /**
   * Runs the Levenberg-Marquardt optimization.
   *
   * @return the optimization result
   */
  public BatchResult solve() {
    validateConfiguration();

    // Build the objective function
    function = buildFunction();

    // Build the sample set
    SampleSet sampleSet = buildSampleSet();

    // Create and configure optimizer
    optimizer = new LevenbergMarquardt();
    optimizer.setSampleSet(sampleSet);
    optimizer.setMaxNumberOfIterations(maxIterations);

    // Solve
    logger.info("Starting Levenberg-Marquardt optimization with {} parameters and {} data points",
        tunableParameters.size(), dataPoints.size());

    optimizer.solve();

    // Compute statistics
    optimizer.calcAbsDev();

    // Build result
    lastResult = buildResult();

    logger.info("Optimization completed. Converged: {}, Chi-square: {}", lastResult.isConverged(),
        lastResult.getChiSquare());

    return lastResult;
  }

  /**
   * Validates the configuration before solving.
   */
  private void validateConfiguration() {
    if (tunableParameters.isEmpty()) {
      throw new IllegalStateException("No tunable parameters defined. "
          + "Use addTunableParameter() to add parameters to estimate.");
    }
    if (measuredVariables.isEmpty()) {
      throw new IllegalStateException(
          "No measured variables defined. " + "Use addMeasuredVariable() to add measurements.");
    }
    if (dataPoints.isEmpty()) {
      throw new IllegalStateException(
          "No data points defined. " + "Use addDataPoint() to add calibration data.");
    }
  }

  /**
   * Builds the ProcessSimulationFunction.
   *
   * @return the constructed ProcessSimulationFunction
   */
  private ProcessSimulationFunction buildFunction() {
    ProcessSimulationFunction func = new ProcessSimulationFunction(processSystem);

    // Add parameters
    for (TunableParameter param : tunableParameters) {
      func.addParameter(param.getPath(), param.getLowerBound(), param.getUpperBound());
    }

    // Add measurements
    for (MeasuredVariable meas : measuredVariables) {
      func.addMeasurement(meas.getPath());
    }

    // Add conditions for each data point
    for (DataPoint dp : dataPoints) {
      func.addDataPointConditions(dp.getConditions());
    }

    // Set initial guess
    double[] initialGuess = new double[tunableParameters.size()];
    for (int i = 0; i < tunableParameters.size(); i++) {
      initialGuess[i] = tunableParameters.get(i).getInitialGuess();
    }
    func.setInitialGuess(initialGuess);

    // Configure Jacobian
    func.setUseAnalyticalJacobian(useAnalyticalJacobian);

    return func;
  }

  /**
   * Builds the SampleSet for the optimizer.
   *
   * @return the constructed SampleSet
   */
  private SampleSet buildSampleSet() {
    List<SampleValue> samples = new ArrayList<>();

    // For each data point and each measurement, create a SampleValue
    for (int dpIdx = 0; dpIdx < dataPoints.size(); dpIdx++) {
      DataPoint dp = dataPoints.get(dpIdx);

      for (int measIdx = 0; measIdx < measuredVariables.size(); measIdx++) {
        MeasuredVariable meas = measuredVariables.get(measIdx);
        String measPath = meas.getPath();

        // Get the experimental measurement value
        Double expValue = dp.getMeasurements().get(measPath);
        if (expValue == null) {
          logger.warn("Data point {} missing measurement for {}. Skipping.", dpIdx, measPath);
          continue;
        }

        // Create dependent values array: [dataPointIndex, measurementIndex]
        double[] dependentValues = new double[] {dpIdx, measIdx};

        // Create sample value with experimental value, std dev, and dependent values
        SampleValue sample =
            new SampleValue(expValue, meas.getStandardDeviation(), dependentValues);
        sample.setFunction(function);

        samples.add(sample);
      }
    }

    // Convert List to ArrayList for SampleSet constructor
    return new SampleSet(new ArrayList<>(samples));
  }

  /**
   * Builds the result from the optimizer output.
   *
   * @return the constructed BatchResult
   */
  private BatchResult buildResult() {
    // Get parameter names
    String[] parameterNames = new String[tunableParameters.size()];
    for (int i = 0; i < tunableParameters.size(); i++) {
      parameterNames[i] = tunableParameters.get(i).getPath();
    }

    // Get estimates from function
    double[] estimates = function.getFittingParams().clone();

    // Compute covariance and uncertainties
    double[] uncertainties;
    double[][] covarianceMatrix = null;
    double[][] correlationMatrix = null;

    try {
      optimizer.calcCoVarianceMatrix();
      optimizer.calcParameterStandardDeviation();
      optimizer.calcParameterUncertainty();
      uncertainties = getParameterStandardDeviations();
      covarianceMatrix = getCoVarianceMatrix();
      correlationMatrix = getCorrelationMatrix();
    } catch (Exception e) {
      logger.warn("Could not compute parameter uncertainties: " + e.getMessage());
      uncertainties = new double[estimates.length];
    }

    // Get chi-square
    double chiSquare =
        optimizer.getSampleSet().getSample(0).getFunction().getSystem() != null ? computeChiSquare()
            : getChiSquareFromOptimizer();

    // Get iterations (approximate - L-M doesn't expose this directly)
    int iterations = maxIterations; // Use max as upper bound

    // Determine convergence (if chi-square is small, we converged)
    boolean converged = !Double.isNaN(chiSquare) && chiSquare < 1e10;

    // Compute additional statistics
    double[] stats = computeAdditionalStatistics();

    return new BatchResult(parameterNames, estimates, uncertainties, chiSquare, iterations,
        dataPoints.size() * measuredVariables.size(), converged, covarianceMatrix,
        correlationMatrix, stats[0], stats[1], stats[2]);
  }

  /**
   * Gets the chi-square value from the optimizer.
   *
   * @return the chi-square value
   */
  private double getChiSquareFromOptimizer() {
    try {
      java.lang.reflect.Field field =
          optimizer.getClass().getSuperclass().getDeclaredField("chiSquare");
      field.setAccessible(true);
      return field.getDouble(optimizer);
    } catch (Exception e) {
      return Double.NaN;
    }
  }

  /**
   * Computes chi-square from the sample set.
   *
   * @return the computed chi-square value
   */
  private double computeChiSquare() {
    double chiSquare = 0;
    SampleSet sampleSet = optimizer.getSampleSet();
    for (int i = 0; i < sampleSet.getLength(); i++) {
      SampleValue sample = sampleSet.getSample(i);
      double calc = function.calcValue(sample.getDependentValues());
      double exp = sample.getSampleValue();
      double sigma = sample.getStandardDeviation();
      chiSquare += Math.pow((exp - calc) / sigma, 2);
    }
    return chiSquare;
  }

  /**
   * Gets parameter standard deviations from the optimizer.
   *
   * @return array of parameter standard deviations
   */
  private double[] getParameterStandardDeviations() {
    try {
      java.lang.reflect.Field field =
          optimizer.getClass().getSuperclass().getDeclaredField("parameterStandardDeviation");
      field.setAccessible(true);
      return (double[]) field.get(optimizer);
    } catch (Exception e) {
      return new double[tunableParameters.size()];
    }
  }

  /**
   * Gets the covariance matrix from the optimizer.
   *
   * @return the covariance matrix
   */
  private double[][] getCoVarianceMatrix() {
    try {
      java.lang.reflect.Field field =
          optimizer.getClass().getSuperclass().getDeclaredField("coVarianceMatrix");
      field.setAccessible(true);
      Object matrix = field.get(optimizer);
      if (matrix != null) {
        java.lang.reflect.Method method = matrix.getClass().getMethod("getArrayCopy");
        return (double[][]) method.invoke(matrix);
      }
    } catch (Exception e) {
      // Ignore
    }
    return null;
  }

  /**
   * Gets the correlation matrix from the optimizer.
   *
   * @return the correlation matrix
   */
  private double[][] getCorrelationMatrix() {
    try {
      optimizer.calcCorrelationMatrix();
      java.lang.reflect.Field field =
          optimizer.getClass().getSuperclass().getDeclaredField("parameterCorrelationMatrix");
      field.setAccessible(true);
      Object matrix = field.get(optimizer);
      if (matrix != null) {
        java.lang.reflect.Method method = matrix.getClass().getMethod("getArrayCopy");
        return (double[][]) method.invoke(matrix);
      }
    } catch (Exception e) {
      // Ignore
    }
    return null;
  }

  /**
   * Computes additional statistics: [MAD, bias, R-squared].
   *
   * @return array of additional statistics
   */
  private double[] computeAdditionalStatistics() {
    double sumAbsDev = 0;
    double sumDev = 0;
    double sumSqRes = 0;
    double sumSqTot = 0;
    double meanExp = 0;

    SampleSet sampleSet = optimizer.getSampleSet();
    int n = sampleSet.getLength();

    // First pass: compute mean
    for (int i = 0; i < n; i++) {
      meanExp += sampleSet.getSample(i).getSampleValue();
    }
    meanExp /= n;

    // Second pass: compute statistics
    for (int i = 0; i < n; i++) {
      SampleValue sample = sampleSet.getSample(i);
      double calc = function.calcValue(sample.getDependentValues());
      double exp = sample.getSampleValue();

      double residual = exp - calc;
      sumAbsDev += Math.abs(residual);
      sumDev += residual;
      sumSqRes += residual * residual;
      sumSqTot += Math.pow(exp - meanExp, 2);
    }

    double mad = sumAbsDev / n;
    double bias = sumDev / n;
    double rSquared = sumSqTot > 0 ? 1.0 - sumSqRes / sumSqTot : Double.NaN;

    return new double[] {mad, bias, rSquared};
  }

  // ==================== Results Access ====================

  /**
   * Gets the result from the last solve.
   *
   * @return the last result, or null if not yet solved
   */
  public BatchResult getLastResult() {
    return lastResult;
  }

  /**
   * Converts the last result to a CalibrationResult for API compatibility.
   *
   * @return calibration result, or failure if not yet solved
   */
  public CalibrationResult toCalibrationResult() {
    if (lastResult == null) {
      return CalibrationResult.failure("Not yet solved. Call solve() first.");
    }
    return lastResult.toCalibrationResult();
  }

  /**
   * Gets the parameter names.
   *
   * @return array of parameter paths
   */
  public String[] getParameterNames() {
    String[] names = new String[tunableParameters.size()];
    for (int i = 0; i < tunableParameters.size(); i++) {
      names[i] = tunableParameters.get(i).getPath();
    }
    return names;
  }

  /**
   * Gets the measurement names.
   *
   * @return array of measurement paths
   */
  public String[] getMeasurementNames() {
    String[] names = new String[measuredVariables.size()];
    for (int i = 0; i < measuredVariables.size(); i++) {
      names[i] = measuredVariables.get(i).getPath();
    }
    return names;
  }

  /**
   * Gets the number of data points.
   *
   * @return number of data points
   */
  public int getDataPointCount() {
    return dataPoints.size();
  }

  /**
   * Gets the underlying process system.
   *
   * @return the process system
   */
  public ProcessSystem getProcessSystem() {
    return processSystem;
  }

  /**
   * Gets the underlying optimizer (for advanced use).
   *
   * @return the L-M optimizer, or null if not yet solved
   */
  public LevenbergMarquardt getOptimizer() {
    return optimizer;
  }

  /**
   * Clears all data points.
   *
   * @return this estimator for chaining
   */
  public BatchParameterEstimator clearDataPoints() {
    dataPoints.clear();
    return this;
  }

  /**
   * Resets the estimator to initial state.
   *
   * @return this estimator for chaining
   */
  public BatchParameterEstimator reset() {
    tunableParameters.clear();
    measuredVariables.clear();
    dataPoints.clear();
    function = null;
    optimizer = null;
    lastResult = null;
    return this;
  }

  /**
   * Displays the curve fit using the optimizer's built-in visualization.
   */
  public void displayCurveFit() {
    if (optimizer != null) {
      optimizer.displayCurveFit();
    }
  }

  /**
   * Displays the result using the optimizer's built-in visualization.
   */
  public void displayResult() {
    if (optimizer != null) {
      optimizer.displayResult();
    }
  }

  /**
   * Runs Monte Carlo simulation for uncertainty analysis.
   *
   * @param numRuns number of Monte Carlo runs
   */
  public void runMonteCarloSimulation(int numRuns) {
    if (optimizer != null) {
      optimizer.runMonteCarloSimulation(numRuns);
    }
  }
}
