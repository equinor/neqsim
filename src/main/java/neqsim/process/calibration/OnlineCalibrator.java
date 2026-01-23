package neqsim.process.calibration;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Online calibrator for continuously updating model parameters based on real-time data.
 *
 * <p>
 * Designed for integration with AI platforms that require auto-calibrating physics models. Supports
 * incremental updates for real-time applications and full recalibration for periodic maintenance.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class OnlineCalibrator implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final ProcessSystem processSystem;
  private final List<String> tunableParameters;
  private final List<DataPoint> calibrationHistory;

  private double deviationThreshold = 0.1; // 10% default
  private int maxHistorySize = 1000;
  private Instant lastCalibration;
  private CalibrationQuality lastQuality;

  /**
   * Represents a data point for calibration.
   */
  public static class DataPoint implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final Instant timestamp;
    private final Map<String, Double> measurements;
    private final Map<String, Double> predictions;
    private final Map<String, Double> conditions;

    public DataPoint(Map<String, Double> measurements, Map<String, Double> predictions,
        Map<String, Double> conditions) {
      this.timestamp = Instant.now();
      this.measurements = new HashMap<>(measurements);
      this.predictions = new HashMap<>(predictions);
      this.conditions = new HashMap<>(conditions);
    }

    public Instant getTimestamp() {
      return timestamp;
    }

    public Map<String, Double> getMeasurements() {
      return measurements;
    }

    public Map<String, Double> getPredictions() {
      return predictions;
    }

    public Map<String, Double> getConditions() {
      return conditions;
    }

    public double getError(String variable) {
      Double meas = measurements.get(variable);
      Double pred = predictions.get(variable);
      if (meas != null && pred != null) {
        return meas - pred;
      }
      return Double.NaN;
    }

    public double getRelativeError(String variable) {
      Double meas = measurements.get(variable);
      if (meas != null && Math.abs(meas) > 1e-10) {
        return getError(variable) / meas;
      }
      return Double.NaN;
    }
  }

  /**
   * Creates an online calibrator for a process system.
   *
   * @param processSystem the process system to calibrate
   */
  public OnlineCalibrator(ProcessSystem processSystem) {
    this.processSystem = processSystem;
    this.tunableParameters = new ArrayList<>();
    this.calibrationHistory = new ArrayList<>();
  }

  /**
   * Configures which parameters can be tuned online.
   *
   * @param params list of tunable parameter names
   */
  public void setTunableParameters(List<String> params) {
    tunableParameters.clear();
    tunableParameters.addAll(params);
  }

  /**
   * Sets the deviation threshold that triggers recalibration.
   *
   * @param threshold relative deviation threshold (e.g., 0.1 for 10%)
   */
  public void setDeviationThreshold(double threshold) {
    this.deviationThreshold = threshold;
  }

  /**
   * Sets the maximum history size for calibration data.
   *
   * @param size maximum number of data points to retain
   */
  public void setMaxHistorySize(int size) {
    this.maxHistorySize = size;
  }

  /**
   * Records a data point and checks if calibration is needed.
   *
   * @param measurements actual measured values
   * @param predictions model predicted values
   * @return true if deviation exceeds threshold
   */
  public boolean recordDataPoint(Map<String, Double> measurements,
      Map<String, Double> predictions) {
    return recordDataPoint(measurements, predictions, new HashMap<>());
  }

  /**
   * Records a data point with operating conditions.
   *
   * @param measurements actual measured values
   * @param predictions model predicted values
   * @param conditions operating conditions (P, T, etc.)
   * @return true if deviation exceeds threshold
   */
  public boolean recordDataPoint(Map<String, Double> measurements, Map<String, Double> predictions,
      Map<String, Double> conditions) {
    DataPoint point = new DataPoint(measurements, predictions, conditions);
    calibrationHistory.add(point);

    // Trim history if needed
    while (calibrationHistory.size() > maxHistorySize) {
      calibrationHistory.remove(0);
    }

    // Check deviation
    return checkDeviation(point);
  }

  /**
   * Checks if any variable exceeds the deviation threshold.
   *
   * @param point the data point to check
   * @return true if deviation exceeds threshold, false otherwise
   */
  private boolean checkDeviation(DataPoint point) {
    for (String variable : point.getMeasurements().keySet()) {
      double relError = Math.abs(point.getRelativeError(variable));
      if (!Double.isNaN(relError) && relError > deviationThreshold) {
        return true;
      }
    }
    return false;
  }

  /**
   * Performs incremental calibration update (fast, for real-time).
   *
   * @param measurements current measured values
   * @param predictions current predicted values
   * @return calibration result
   */
  public CalibrationResult incrementalUpdate(Map<String, Double> measurements,
      Map<String, Double> predictions) {
    if (tunableParameters.isEmpty()) {
      return CalibrationResult.failure("No tunable parameters configured");
    }

    // Simple gain adjustment based on error
    Map<String, Double> adjustedParams = new HashMap<>();
    double totalError = 0;
    int errorCount = 0;

    for (String variable : measurements.keySet()) {
      Double meas = measurements.get(variable);
      Double pred = predictions.get(variable);
      if (meas != null && pred != null && Math.abs(pred) > 1e-10) {
        double ratio = meas / pred;
        totalError += Math.abs(1 - ratio);
        errorCount++;
      }
    }

    if (errorCount == 0) {
      return CalibrationResult.failure("No valid measurement-prediction pairs");
    }

    double avgError = totalError / errorCount;

    // Apply simple multiplicative correction to first tunable parameter
    if (!tunableParameters.isEmpty()) {
      String param = tunableParameters.get(0);
      double correction = 1.0 + (avgError * 0.1); // Conservative adjustment
      adjustedParams.put(param, correction);
    }

    return CalibrationResult.success(adjustedParams, avgError, 1, 1);
  }

  /**
   * Performs full recalibration using historical data.
   *
   * @return calibration result
   */
  public CalibrationResult fullRecalibration() {
    if (calibrationHistory.size() < 10) {
      return CalibrationResult.failure("Insufficient data for full recalibration (need >= 10)");
    }

    // Collect training data
    List<double[]> features = new ArrayList<>();
    List<Double> targets = new ArrayList<>();

    for (DataPoint point : calibrationHistory) {
      for (String variable : point.getMeasurements().keySet()) {
        double error = point.getError(variable);
        if (!Double.isNaN(error)) {
          // Simple feature: conditions
          double[] feature = new double[point.getConditions().size()];
          int i = 0;
          for (Double val : point.getConditions().values()) {
            feature[i++] = val;
          }
          features.add(feature);
          targets.add(error);
        }
      }
    }

    if (features.isEmpty()) {
      return CalibrationResult.failure("No valid training data");
    }

    // Simple least-squares calibration
    double sumError = 0;
    for (Double error : targets) {
      sumError += error;
    }
    double meanError = sumError / targets.size();

    // Calculate RMSE
    double sumSquaredError = 0;
    for (Double error : targets) {
      sumSquaredError += error * error;
    }
    double rmse = Math.sqrt(sumSquaredError / targets.size());

    // Simple bias correction
    Map<String, Double> calibratedParams = new HashMap<>();
    calibratedParams.put("bias_correction", meanError);

    lastCalibration = Instant.now();
    lastQuality = calculateQuality(features.size(), rmse);

    return CalibrationResult.success(calibratedParams, rmse, 0, features.size());
  }

  /**
   * Calculates calibration quality metrics.
   *
   * @param samples the number of samples used
   * @param rmse the root mean square error
   * @return the calibration quality assessment
   */
  private CalibrationQuality calculateQuality(int samples, double rmse) {
    // Calculate R2
    double sumSquaredTotal = 0;
    double sumSquaredResidual = 0;
    double mean = 0;
    int count = 0;

    for (DataPoint point : calibrationHistory) {
      for (String variable : point.getMeasurements().keySet()) {
        Double meas = point.getMeasurements().get(variable);
        if (meas != null) {
          mean += meas;
          count++;
        }
      }
    }
    mean = (count > 0) ? mean / count : 0;

    for (DataPoint point : calibrationHistory) {
      for (String variable : point.getMeasurements().keySet()) {
        Double meas = point.getMeasurements().get(variable);
        Double pred = point.getPredictions().get(variable);
        if (meas != null && pred != null) {
          sumSquaredTotal += (meas - mean) * (meas - mean);
          sumSquaredResidual += (meas - pred) * (meas - pred);
        }
      }
    }

    double r2 = (sumSquaredTotal > 0) ? 1 - (sumSquaredResidual / sumSquaredTotal) : 0;

    // Estimate coverage (simplified)
    double coverage = Math.min(100, samples * 2);

    return new CalibrationQuality(Instant.now(), rmse, rmse, rmse * 2, r2, samples, coverage);
  }

  /**
   * Gets the calibration quality metrics.
   *
   * @return calibration quality or null if never calibrated
   */
  public CalibrationQuality getQualityMetrics() {
    return lastQuality;
  }

  /**
   * Gets the last calibration timestamp.
   *
   * @return last calibration time or null
   */
  public Instant getLastCalibrationTime() {
    return lastCalibration;
  }

  /**
   * Gets the calibration history size.
   *
   * @return number of data points in history
   */
  public int getHistorySize() {
    return calibrationHistory.size();
  }

  /**
   * Clears the calibration history.
   */
  public void clearHistory() {
    calibrationHistory.clear();
  }

  /**
   * Exports calibration history for external analysis.
   *
   * @return list of data points
   */
  public List<DataPoint> exportHistory() {
    return new ArrayList<>(calibrationHistory);
  }
}
