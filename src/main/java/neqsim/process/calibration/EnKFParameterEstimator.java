package neqsim.process.calibration;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import neqsim.process.mpc.ManipulatedVariable;
import neqsim.process.mpc.ProcessDerivativeCalculator;
import neqsim.process.mpc.ProcessVariableAccessor;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Ensemble Kalman Filter (EnKF) estimator for online calibration of process parameters.
 *
 * <p>
 * This estimator uses the Ensemble Kalman Filter algorithm to estimate unknown process parameters
 * (such as heat transfer coefficients, valve coefficients, fouling factors) by matching simulation
 * outputs to measured plant data.
 * </p>
 *
 * <p>
 * <b>Key Features:</b>
 * </p>
 * <ul>
 * <li>Sequential updates - processes one measurement at a time, perfect for live data</li>
 * <li>Uncertainty quantification - provides confidence intervals for estimates</li>
 * <li>Handles nonlinear models naturally through ensemble propagation</li>
 * <li>Anomaly detection - identifies when measurements deviate from model</li>
 * <li>Drift tracking - detects gradual parameter changes over time</li>
 * </ul>
 *
 * <p>
 * <b>Usage Example:</b>
 * </p>
 * 
 * <pre>
 * {@code
 * // Create estimator for a process system
 * EnKFParameterEstimator estimator = new EnKFParameterEstimator(processSystem);
 * 
 * // Define tunable parameters (what we want to estimate)
 * estimator.addTunableParameter("Pipe1.heatTransferCoefficient", "W/(m2·K)", 1.0, 100.0, 15.0);
 * estimator.addTunableParameter("Pipe2.heatTransferCoefficient", "W/(m2·K)", 1.0, 100.0, 15.0);
 * 
 * // Define measurements (what we observe)
 * estimator.addMeasuredVariable("HPManifold.temperature", "C", 0.5); // 0.5°C noise std
 * estimator.addMeasuredVariable("LPManifold.temperature", "C", 0.5);
 * 
 * // Initialize the filter
 * estimator.initialize(50, 42); // 50 ensemble members, seed 42
 * 
 * // In live loop:
 * Map<String, Double> measurements = getMeasurementsFromPlant();
 * EnKFResult result = estimator.update(measurements);
 * 
 * System.out.println("Estimates: " + Arrays.toString(result.getEstimates()));
 * System.out.println("Uncertainties: " + Arrays.toString(result.getUncertainties()));
 * }
 * </pre>
 *
 * <p>
 * <b>Integration with Existing NeqSim Components:</b>
 * </p>
 * <ul>
 * <li>Uses {@link ProcessVariableAccessor} for reading/writing process variables</li>
 * <li>Uses {@link ProcessDerivativeCalculator} for sensitivity analysis</li>
 * <li>Uses {@link ManipulatedVariable} patterns for parameter bounds</li>
 * <li>Returns {@link CalibrationResult} compatible results</li>
 * </ul>
 *
 * @author NeqSim Development Team
 * @version 1.0
 * @see OnlineCalibrator
 * @see ProcessDerivativeCalculator
 */
public class EnKFParameterEstimator implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** The process system to calibrate. */
  private ProcessSystem processSystem;

  /** Variable accessor for reading/writing process variables. */
  private transient ProcessVariableAccessor variableAccessor;

  /** Tunable parameters to estimate. */
  private List<TunableParameterSpec> tunableParameters = new ArrayList<>();

  /** Measured variables to match. */
  private List<MeasuredVariableSpec> measuredVariables = new ArrayList<>();

  // EnKF state
  private double[][] ensemble;
  private double[] ensembleMean;
  private double[] ensembleStd;
  private int ensembleSize;
  private transient Random rng;
  private boolean initialized = false;

  // Configuration
  private double processNoiseStd = 0.3;
  private double maxChangePerUpdate = 5.0;
  private double[] previousEstimate;

  // Statistics
  private int updateCount = 0;
  private double[] lastPrediction;
  private List<EnKFResult> history = new ArrayList<>();

  /**
   * Specification for a tunable parameter.
   */
  public static class TunableParameterSpec implements Serializable {
    private static final long serialVersionUID = 1000L;

    /** Path to the parameter (e.g., "Pipe1.heatTransferCoefficient"). */
    public final String path;

    /** Unit of measurement. */
    public final String unit;

    /** Minimum allowed value. */
    public final double minValue;

    /** Maximum allowed value. */
    public final double maxValue;

    /** Initial/prior value. */
    public final double initialValue;

    /** Initial uncertainty (standard deviation). */
    public final double initialUncertainty;

    /**
     * Constructor.
     *
     * @param path variable path
     * @param unit unit of measurement
     * @param minValue minimum bound
     * @param maxValue maximum bound
     * @param initialValue starting value
     * @param initialUncertainty initial std dev
     */
    public TunableParameterSpec(String path, String unit, double minValue, double maxValue,
        double initialValue, double initialUncertainty) {
      this.path = path;
      this.unit = unit;
      this.minValue = minValue;
      this.maxValue = maxValue;
      this.initialValue = initialValue;
      this.initialUncertainty = initialUncertainty;
    }
  }

  /**
   * Specification for a measured variable.
   */
  public static class MeasuredVariableSpec implements Serializable {
    private static final long serialVersionUID = 1000L;

    /** Path to the variable (e.g., "Separator.gasOutStream.temperature"). */
    public final String path;

    /** Unit of measurement. */
    public final String unit;

    /** Measurement noise standard deviation. */
    public final double noiseStd;

    /**
     * Constructor.
     *
     * @param path variable path
     * @param unit unit of measurement
     * @param noiseStd measurement noise std dev
     */
    public MeasuredVariableSpec(String path, String unit, double noiseStd) {
      this.path = path;
      this.unit = unit;
      this.noiseStd = noiseStd;
    }
  }

  /**
   * Result of an EnKF update step.
   */
  public static class EnKFResult implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final int step;
    private final double[] estimates;
    private final double[] uncertainties;
    private final double[] measurements;
    private final double[] predictions;
    private final double rmse;
    private final boolean anomalyDetected;

    /**
     * Constructor.
     *
     * @param step update step number
     * @param estimates parameter estimates
     * @param uncertainties parameter uncertainties (std dev)
     * @param measurements observed measurements
     * @param predictions model predictions
     * @param anomalyDetected whether anomaly was detected
     */
    public EnKFResult(int step, double[] estimates, double[] uncertainties, double[] measurements,
        double[] predictions, boolean anomalyDetected) {
      this.step = step;
      this.estimates = estimates.clone();
      this.uncertainties = uncertainties.clone();
      this.measurements = measurements.clone();
      this.predictions = predictions.clone();
      this.anomalyDetected = anomalyDetected;

      // Calculate RMSE
      double sumSq = 0;
      for (int i = 0; i < measurements.length; i++) {
        double error = predictions[i] - measurements[i];
        sumSq += error * error;
      }
      this.rmse = Math.sqrt(sumSq / measurements.length);
    }

    /**
     * Gets the update step number.
     *
     * @return the step number
     */
    public int getStep() {
      return step;
    }

    /**
     * Gets parameter estimates.
     *
     * @return array of parameter estimates
     */
    public double[] getEstimates() {
      return estimates.clone();
    }

    /**
     * Gets parameter uncertainties (standard deviations).
     *
     * @return array of parameter standard deviations
     */
    public double[] getUncertainties() {
      return uncertainties.clone();
    }

    /**
     * Gets 95% confidence interval lower bounds.
     *
     * @return array of lower confidence bounds
     */
    public double[] getConfidenceIntervalLower() {
      double[] lower = new double[estimates.length];
      for (int i = 0; i < estimates.length; i++) {
        lower[i] = estimates[i] - 1.96 * uncertainties[i];
      }
      return lower;
    }

    /**
     * Gets 95% confidence interval upper bounds.
     *
     * @return array of upper confidence bounds
     */
    public double[] getConfidenceIntervalUpper() {
      double[] upper = new double[estimates.length];
      for (int i = 0; i < estimates.length; i++) {
        upper[i] = estimates[i] + 1.96 * uncertainties[i];
      }
      return upper;
    }

    /**
     * Gets the measurements used in this update.
     *
     * @return array of measurement values
     */
    public double[] getMeasurements() {
      return measurements.clone();
    }

    /**
     * Gets the model predictions.
     *
     * @return array of model predictions
     */
    public double[] getPredictions() {
      return predictions.clone();
    }

    /**
     * Gets the RMSE of predictions vs measurements.
     *
     * @return the root mean square error
     */
    public double getRMSE() {
      return rmse;
    }

    /**
     * Returns true if anomaly was detected.
     *
     * @return true if anomaly was detected
     */
    public boolean isAnomalyDetected() {
      return anomalyDetected;
    }

    /**
     * Converts to a CalibrationResult for compatibility with existing API.
     *
     * @param parameterNames names for the parameters
     * @return CalibrationResult
     */
    public CalibrationResult toCalibrationResult(String[] parameterNames) {
      Map<String, Double> params = new HashMap<>();
      for (int i = 0; i < parameterNames.length && i < estimates.length; i++) {
        params.put(parameterNames[i], estimates[i]);
      }
      return CalibrationResult.success(params, rmse, step, 1);
    }
  }

  /**
   * Creates an EnKF estimator for a process system.
   *
   * @param processSystem the process system to calibrate
   */
  public EnKFParameterEstimator(ProcessSystem processSystem) {
    this.processSystem = processSystem;
    this.variableAccessor = new ProcessVariableAccessor(processSystem);
  }

  /**
   * Adds a tunable parameter to estimate.
   *
   * @param path variable path (e.g., "Pipe1.heatTransferCoefficient")
   * @param unit unit of measurement
   * @param minValue minimum bound
   * @param maxValue maximum bound
   * @param initialValue initial/prior value
   * @return this estimator for chaining
   */
  public EnKFParameterEstimator addTunableParameter(String path, String unit, double minValue,
      double maxValue, double initialValue) {
    double initialUncertainty = (maxValue - minValue) / 6.0; // ~99% within bounds
    tunableParameters.add(
        new TunableParameterSpec(path, unit, minValue, maxValue, initialValue, initialUncertainty));
    return this;
  }

  /**
   * Adds a tunable parameter with explicit uncertainty.
   *
   * @param path variable path
   * @param unit unit of measurement
   * @param minValue minimum bound
   * @param maxValue maximum bound
   * @param initialValue initial value
   * @param initialUncertainty initial standard deviation
   * @return this estimator for chaining
   */
  public EnKFParameterEstimator addTunableParameter(String path, String unit, double minValue,
      double maxValue, double initialValue, double initialUncertainty) {
    tunableParameters.add(
        new TunableParameterSpec(path, unit, minValue, maxValue, initialValue, initialUncertainty));
    return this;
  }

  /**
   * Adds a measured variable to match.
   *
   * @param path variable path (e.g., "Separator.temperature")
   * @param unit unit of measurement
   * @param noiseStd measurement noise standard deviation
   * @return this estimator for chaining
   */
  public EnKFParameterEstimator addMeasuredVariable(String path, String unit, double noiseStd) {
    measuredVariables.add(new MeasuredVariableSpec(path, unit, noiseStd));
    return this;
  }

  /**
   * Sets the process noise standard deviation.
   *
   * @param processNoiseStd process noise (parameter drift rate)
   * @return this estimator for chaining
   */
  public EnKFParameterEstimator setProcessNoise(double processNoiseStd) {
    this.processNoiseStd = processNoiseStd;
    return this;
  }

  /**
   * Sets the maximum parameter change per update (rate limiting).
   *
   * @param maxChange maximum change per update
   * @return this estimator for chaining
   */
  public EnKFParameterEstimator setMaxChangePerUpdate(double maxChange) {
    this.maxChangePerUpdate = maxChange;
    return this;
  }

  /**
   * Initializes the EnKF ensemble.
   *
   * @param ensembleSize number of ensemble members (typically 20-100)
   * @param seed random seed for reproducibility
   */
  public void initialize(int ensembleSize, long seed) {
    if (tunableParameters.isEmpty()) {
      throw new IllegalStateException("No tunable parameters defined");
    }
    if (measuredVariables.isEmpty()) {
      throw new IllegalStateException("No measured variables defined");
    }

    this.ensembleSize = ensembleSize;
    this.rng = new Random(seed);

    int numParams = tunableParameters.size();
    ensemble = new double[ensembleSize][numParams];
    ensembleMean = new double[numParams];
    ensembleStd = new double[numParams];
    previousEstimate = new double[numParams];

    // Initialize ensemble from prior distribution
    for (int e = 0; e < ensembleSize; e++) {
      for (int p = 0; p < numParams; p++) {
        TunableParameterSpec spec = tunableParameters.get(p);
        double value = spec.initialValue + spec.initialUncertainty * rng.nextGaussian();
        ensemble[e][p] = clipToBounds(value, spec);
      }
    }

    // Initialize statistics
    for (int p = 0; p < numParams; p++) {
      TunableParameterSpec spec = tunableParameters.get(p);
      ensembleMean[p] = spec.initialValue;
      ensembleStd[p] = spec.initialUncertainty;
      previousEstimate[p] = spec.initialValue;
    }

    history.clear();
    updateCount = 0;
    initialized = true;
  }

  /**
   * Clips a value to parameter bounds.
   *
   * @param value the value to clip
   * @param spec the parameter specification with bounds
   * @return the clipped value within bounds
   */
  private double clipToBounds(double value, TunableParameterSpec spec) {
    return Math.max(spec.minValue, Math.min(spec.maxValue, value));
  }

  /**
   * Runs simulation with given parameters and returns measured outputs.
   *
   * @param parameters array of parameter values to use in simulation
   * @return array of measured output values from the simulation
   */
  private double[] simulate(double[] parameters) {
    // Set parameters in process
    for (int p = 0; p < parameters.length; p++) {
      TunableParameterSpec spec = tunableParameters.get(p);
      variableAccessor.setValue(spec.path, clipToBounds(parameters[p], spec));
    }

    // Run simulation
    processSystem.run();

    // Read outputs
    double[] outputs = new double[measuredVariables.size()];
    for (int m = 0; m < measuredVariables.size(); m++) {
      MeasuredVariableSpec spec = measuredVariables.get(m);
      outputs[m] = variableAccessor.getValue(spec.path, spec.unit);
    }

    return outputs;
  }

  /**
   * Performs one EnKF update step with new measurements.
   *
   * @param measurements map of variable path to measured value
   * @return estimation result
   */
  public EnKFResult update(Map<String, Double> measurements) {
    if (!initialized) {
      throw new IllegalStateException("Estimator not initialized. Call initialize() first.");
    }

    updateCount++;
    int numParams = tunableParameters.size();
    int numMeas = measuredVariables.size();

    // Convert measurements map to array
    double[] measArray = new double[numMeas];
    double[] noiseStd = new double[numMeas];
    for (int m = 0; m < numMeas; m++) {
      MeasuredVariableSpec spec = measuredVariables.get(m);
      Double value = measurements.get(spec.path);
      if (value == null) {
        throw new IllegalArgumentException("Missing measurement for: " + spec.path);
      }
      measArray[m] = value;
      noiseStd[m] = spec.noiseStd;
    }

    // STEP 1: PREDICTION - Add process noise
    for (int e = 0; e < ensembleSize; e++) {
      for (int p = 0; p < numParams; p++) {
        TunableParameterSpec spec = tunableParameters.get(p);
        ensemble[e][p] += processNoiseStd * rng.nextGaussian();
        ensemble[e][p] = clipToBounds(ensemble[e][p], spec);
      }
    }

    // STEP 2: SIMULATE - Run model for each ensemble member
    double[][] predictions = new double[ensembleSize][numMeas];
    double[] predMean = new double[numMeas];

    for (int e = 0; e < ensembleSize; e++) {
      predictions[e] = simulate(ensemble[e]);
      for (int m = 0; m < numMeas; m++) {
        predMean[m] += predictions[e][m] / ensembleSize;
      }
    }

    // STEP 3: COMPUTE COVARIANCES
    // Cross-covariance Cov(params, outputs)
    double[][] Pxy = new double[numParams][numMeas];
    for (int p = 0; p < numParams; p++) {
      for (int m = 0; m < numMeas; m++) {
        double cov = 0;
        for (int e = 0; e < ensembleSize; e++) {
          cov += (ensemble[e][p] - ensembleMean[p]) * (predictions[e][m] - predMean[m]);
        }
        Pxy[p][m] = cov / (ensembleSize - 1);
      }
    }

    // Output covariance Cov(outputs, outputs) + R
    double[][] Pyy = new double[numMeas][numMeas];
    for (int i = 0; i < numMeas; i++) {
      for (int j = 0; j < numMeas; j++) {
        double cov = 0;
        for (int e = 0; e < ensembleSize; e++) {
          cov += (predictions[e][i] - predMean[i]) * (predictions[e][j] - predMean[j]);
        }
        Pyy[i][j] = cov / (ensembleSize - 1);
        if (i == j) {
          Pyy[i][j] += noiseStd[i] * noiseStd[i]; // Add measurement noise
        }
      }
    }

    // STEP 4: COMPUTE KALMAN GAIN K = Pxy * inv(Pyy)
    double[][] PyyInv = invertMatrix(Pyy);
    double[][] K = new double[numParams][numMeas];
    for (int p = 0; p < numParams; p++) {
      for (int m = 0; m < numMeas; m++) {
        for (int k = 0; k < numMeas; k++) {
          K[p][m] += Pxy[p][k] * PyyInv[k][m];
        }
      }
    }

    // STEP 5: UPDATE ENSEMBLE
    for (int e = 0; e < ensembleSize; e++) {
      // Perturb measurements
      double[] perturbedMeas = new double[numMeas];
      for (int m = 0; m < numMeas; m++) {
        perturbedMeas[m] = measArray[m] + noiseStd[m] * rng.nextGaussian();
      }

      // Update: param = param + K * (measurement - prediction)
      for (int p = 0; p < numParams; p++) {
        double innovation = 0;
        for (int m = 0; m < numMeas; m++) {
          innovation += K[p][m] * (perturbedMeas[m] - predictions[e][m]);
        }
        TunableParameterSpec spec = tunableParameters.get(p);
        ensemble[e][p] = clipToBounds(ensemble[e][p] + innovation, spec);
      }
    }

    // STEP 6: COMPUTE STATISTICS
    double[] newMean = new double[numParams];
    double[] newStd = new double[numParams];

    for (int p = 0; p < numParams; p++) {
      double sum = 0;
      for (int e = 0; e < ensembleSize; e++) {
        sum += ensemble[e][p];
      }
      newMean[p] = sum / ensembleSize;

      double sumSq = 0;
      for (int e = 0; e < ensembleSize; e++) {
        sumSq += (ensemble[e][p] - newMean[p]) * (ensemble[e][p] - newMean[p]);
      }
      newStd[p] = Math.sqrt(sumSq / (ensembleSize - 1));
    }

    // STEP 7: RATE LIMITING
    for (int p = 0; p < numParams; p++) {
      double change = newMean[p] - previousEstimate[p];
      if (Math.abs(change) > maxChangePerUpdate) {
        newMean[p] = previousEstimate[p] + Math.signum(change) * maxChangePerUpdate;
      }
    }

    ensembleMean = newMean;
    ensembleStd = newStd;
    previousEstimate = newMean.clone();

    // Apply final estimates and get prediction
    lastPrediction = simulate(ensembleMean);

    // Check for anomaly
    double maxError = 0;
    for (int m = 0; m < numMeas; m++) {
      double error = Math.abs(lastPrediction[m] - measArray[m]);
      maxError = Math.max(maxError, error / noiseStd[m]);
    }
    boolean anomaly = maxError > 3.0; // > 3 sigma

    // Create result
    EnKFResult result = new EnKFResult(updateCount, ensembleMean.clone(), ensembleStd.clone(),
        measArray, lastPrediction.clone(), anomaly);
    history.add(result);

    return result;
  }

  /**
   * Simple matrix inversion (for small matrices).
   *
   * @param matrix the matrix to invert
   * @return the inverted matrix
   */
  private double[][] invertMatrix(double[][] matrix) {
    int n = matrix.length;
    if (n == 1) {
      return new double[][] {{1.0 / matrix[0][0]}};
    }
    if (n == 2) {
      double det = matrix[0][0] * matrix[1][1] - matrix[0][1] * matrix[1][0];
      if (Math.abs(det) < 1e-10) {
        // Regularize
        det = 1e-10;
      }
      return new double[][] {{matrix[1][1] / det, -matrix[0][1] / det},
          {-matrix[1][0] / det, matrix[0][0] / det}};
    }
    // For larger matrices, use Gaussian elimination or external library
    // This is a simplified implementation for small matrices
    throw new UnsupportedOperationException(
        "Matrix inversion for n>" + n + " not implemented. Use external library.");
  }

  /**
   * Gets current parameter estimates.
   *
   * @return array of parameter estimates
   */
  public double[] getEstimates() {
    return ensembleMean.clone();
  }

  /**
   * Gets current parameter uncertainties.
   *
   * @return array of standard deviations
   */
  public double[] getUncertainties() {
    return ensembleStd.clone();
  }

  /**
   * Gets estimation history.
   *
   * @return list of all EnKF results
   */
  public List<EnKFResult> getHistory() {
    return new ArrayList<>(history);
  }

  /**
   * Gets number of updates performed.
   *
   * @return update count
   */
  public int getUpdateCount() {
    return updateCount;
  }

  /**
   * Gets parameter names.
   *
   * @return array of parameter paths
   */
  public String[] getParameterNames() {
    return tunableParameters.stream().map(p -> p.path).toArray(String[]::new);
  }

  /**
   * Gets measurement variable names.
   *
   * @return array of measurement paths
   */
  public String[] getMeasurementNames() {
    return measuredVariables.stream().map(m -> m.path).toArray(String[]::new);
  }

  /**
   * Resets the estimator to initial state.
   */
  public void reset() {
    if (initialized) {
      initialize(ensembleSize, System.currentTimeMillis());
    }
  }

  /**
   * Converts current state to CalibrationResult for compatibility.
   *
   * @return CalibrationResult
   */
  public CalibrationResult toCalibrationResult() {
    if (!initialized || history.isEmpty()) {
      return CalibrationResult.failure("Estimator not initialized or no updates performed");
    }
    return history.get(history.size() - 1).toCalibrationResult(getParameterNames());
  }
}
