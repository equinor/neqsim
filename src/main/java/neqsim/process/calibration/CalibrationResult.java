package neqsim.process.calibration;

import java.io.Serializable;
import java.time.Instant;
import java.util.Map;

/**
 * Result of an online calibration operation.
 *
 * @author ESOL
 * @version 1.0
 */
public class CalibrationResult implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final Instant timestamp;
  private final boolean successful;
  private final Map<String, Double> calibratedParameters;
  private final double objectiveValue;
  private final double improvementPercent;
  private final int iterations;
  private final String message;

  /**
   * Creates a calibration result.
   *
   * @param successful whether calibration succeeded
   * @param calibratedParameters the calibrated parameter values
   * @param objectiveValue final objective function value
   * @param improvementPercent improvement in objective function
   * @param iterations number of iterations used
   * @param message status message
   */
  public CalibrationResult(boolean successful, Map<String, Double> calibratedParameters,
      double objectiveValue, double improvementPercent, int iterations, String message) {
    this.timestamp = Instant.now();
    this.successful = successful;
    this.calibratedParameters = calibratedParameters;
    this.objectiveValue = objectiveValue;
    this.improvementPercent = improvementPercent;
    this.iterations = iterations;
    this.message = message;
  }

  /**
   * Creates a successful calibration result.
   *
   * @param parameters calibrated parameters
   * @param objectiveValue final objective value
   * @param improvement improvement percentage
   * @param iterations iterations used
   * @return successful result
   */
  public static CalibrationResult success(Map<String, Double> parameters, double objectiveValue,
      double improvement, int iterations) {
    return new CalibrationResult(true, parameters, objectiveValue, improvement, iterations,
        "Calibration successful");
  }

  /**
   * Creates a failed calibration result.
   *
   * @param message failure message
   * @return failed result
   */
  public static CalibrationResult failure(String message) {
    return new CalibrationResult(false, null, Double.NaN, 0, 0, message);
  }

  /**
   * Gets the calibration timestamp.
   *
   * @return timestamp
   */
  public Instant getTimestamp() {
    return timestamp;
  }

  /**
   * Checks if calibration was successful.
   *
   * @return true if successful
   */
  public boolean isSuccessful() {
    return successful;
  }

  /**
   * Gets the calibrated parameter values.
   *
   * @return map of parameter names to values
   */
  public Map<String, Double> getCalibratedParameters() {
    return calibratedParameters;
  }

  /**
   * Gets a specific calibrated parameter.
   *
   * @param name parameter name
   * @return parameter value or NaN if not found
   */
  public double getParameter(String name) {
    if (calibratedParameters != null) {
      return calibratedParameters.getOrDefault(name, Double.NaN);
    }
    return Double.NaN;
  }

  /**
   * Gets the final objective function value.
   *
   * @return objective value
   */
  public double getObjectiveValue() {
    return objectiveValue;
  }

  /**
   * Gets the improvement percentage.
   *
   * @return improvement in percent
   */
  public double getImprovementPercent() {
    return improvementPercent;
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
   * Gets the status message.
   *
   * @return message
   */
  public String getMessage() {
    return message;
  }

  @Override
  public String toString() {
    return String.format("CalibrationResult[%s, objective=%.6f, improvement=%.2f%%, iters=%d]",
        successful ? "SUCCESS" : "FAILED", objectiveValue, improvementPercent, iterations);
  }
}
