package neqsim.process.calibration;

import java.io.Serializable;
import java.util.Collections;
import java.util.Map;

/**
 * Represents the result of a calibration operation.
 *
 * <p>
 * Contains the calibrated parameters, quality metrics, and status information.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class CalibrationResult implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final boolean success;
  private final Map<String, Double> parameters;
  private final double rmse;
  private final int iterations;
  private final int samplesUsed;
  private final String errorMessage;

  /**
   * Private constructor - use factory methods.
   */
  private CalibrationResult(boolean success, Map<String, Double> parameters, double rmse,
      int iterations, int samplesUsed, String errorMessage) {
    this.success = success;
    this.parameters = parameters != null ? Collections.unmodifiableMap(parameters) : null;
    this.rmse = rmse;
    this.iterations = iterations;
    this.samplesUsed = samplesUsed;
    this.errorMessage = errorMessage;
  }

  /**
   * Creates a successful calibration result.
   *
   * @param parameters the calibrated parameters
   * @param rmse the root mean square error achieved
   * @param iterations number of iterations used
   * @param samplesUsed number of samples used
   * @return successful calibration result
   */
  public static CalibrationResult success(Map<String, Double> parameters, double rmse,
      int iterations, int samplesUsed) {
    return new CalibrationResult(true, parameters, rmse, iterations, samplesUsed, null);
  }

  /**
   * Creates a failed calibration result.
   *
   * @param errorMessage description of the failure
   * @return failed calibration result
   */
  public static CalibrationResult failure(String errorMessage) {
    return new CalibrationResult(false, null, Double.NaN, 0, 0, errorMessage);
  }

  /**
   * Checks if calibration was successful.
   *
   * @return true if successful
   */
  public boolean isSuccess() {
    return success;
  }

  /**
   * Alias for isSuccess() for compatibility.
   *
   * @return true if successful
   */
  public boolean isSuccessful() {
    return success;
  }

  /**
   * Gets the calibrated parameters.
   *
   * @return map of parameter names to values, or null if failed
   */
  public Map<String, Double> getParameters() {
    return parameters;
  }

  /**
   * Alias for getParameters() for compatibility.
   *
   * @return map of parameter names to values, or null if failed
   */
  public Map<String, Double> getCalibratedParameters() {
    return parameters;
  }

  /**
   * Gets the RMSE achieved.
   *
   * @return root mean square error
   */
  public double getRmse() {
    return rmse;
  }

  /**
   * Gets the number of iterations used.
   *
   * @return iteration count
   */
  public int getIterations() {
    return iterations;
  }

  /**
   * Gets the number of samples used.
   *
   * @return sample count
   */
  public int getSamplesUsed() {
    return samplesUsed;
  }

  /**
   * Gets the error message if calibration failed.
   *
   * @return error message or null if successful
   */
  public String getErrorMessage() {
    return errorMessage;
  }

  /**
   * Alias for getErrorMessage() for compatibility.
   *
   * @return error message or null if successful
   */
  public String getMessage() {
    return errorMessage;
  }

  @Override
  public String toString() {
    if (success) {
      return String.format("CalibrationResult[success, RMSE=%.4f, params=%d, samples=%d]", rmse,
          parameters != null ? parameters.size() : 0, samplesUsed);
    } else {
      return String.format("CalibrationResult[failed: %s]", errorMessage);
    }
  }
}
