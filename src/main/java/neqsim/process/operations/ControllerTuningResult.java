package neqsim.process.operations;

import com.google.gson.GsonBuilder;
import java.io.Serializable;

/**
 * Quantitative result from a controller tuning or performance screening study.
 *
 * <p>
 * The metrics are calculated from time, process-value, and controller-output histories and can be
 * used with existing NeqSim controllers, measurement devices, or imported tagreader data.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class ControllerTuningResult implements Serializable {
  private static final long serialVersionUID = 1L;

  private final String controllerName;
  private final double setPoint;
  private final double meanAbsoluteError;
  private final double maxAbsoluteError;
  private final double integralAbsoluteError;
  private final double integralSquaredError;
  private final double overshootPercent;
  private final double settlingTimeSeconds;
  private final double outputSaturationFraction;
  private final boolean stableAtEnd;
  private final String recommendation;

  /**
   * Creates a controller tuning result.
   *
   * @param controllerName controller name
   * @param setPoint controller set point
   * @param meanAbsoluteError mean absolute control error
   * @param maxAbsoluteError maximum absolute control error
   * @param integralAbsoluteError integral absolute error
   * @param integralSquaredError integral squared error
   * @param overshootPercent overshoot as percent of step magnitude
   * @param settlingTimeSeconds settling time in seconds, or NaN if not settled
   * @param outputSaturationFraction fraction of samples at output limits
   * @param stableAtEnd true if the final response is within tolerance with low variation
   * @param recommendation short tuning recommendation
   */
  ControllerTuningResult(String controllerName, double setPoint, double meanAbsoluteError,
      double maxAbsoluteError, double integralAbsoluteError, double integralSquaredError,
      double overshootPercent, double settlingTimeSeconds, double outputSaturationFraction,
      boolean stableAtEnd, String recommendation) {
    this.controllerName = controllerName;
    this.setPoint = setPoint;
    this.meanAbsoluteError = meanAbsoluteError;
    this.maxAbsoluteError = maxAbsoluteError;
    this.integralAbsoluteError = integralAbsoluteError;
    this.integralSquaredError = integralSquaredError;
    this.overshootPercent = overshootPercent;
    this.settlingTimeSeconds = settlingTimeSeconds;
    this.outputSaturationFraction = outputSaturationFraction;
    this.stableAtEnd = stableAtEnd;
    this.recommendation = recommendation;
  }

  /**
   * Returns the controller name.
   *
   * @return controller name
   */
  public String getControllerName() {
    return controllerName;
  }

  /**
   * Returns the set point.
   *
   * @return set point value
   */
  public double getSetPoint() {
    return setPoint;
  }

  /**
   * Returns the mean absolute error.
   *
   * @return mean absolute error
   */
  public double getMeanAbsoluteError() {
    return meanAbsoluteError;
  }

  /**
   * Returns the maximum absolute error.
   *
   * @return maximum absolute error
   */
  public double getMaxAbsoluteError() {
    return maxAbsoluteError;
  }

  /**
   * Returns the integral absolute error.
   *
   * @return integral absolute error
   */
  public double getIntegralAbsoluteError() {
    return integralAbsoluteError;
  }

  /**
   * Returns the integral squared error.
   *
   * @return integral squared error
   */
  public double getIntegralSquaredError() {
    return integralSquaredError;
  }

  /**
   * Returns overshoot as percent of step magnitude.
   *
   * @return overshoot percent
   */
  public double getOvershootPercent() {
    return overshootPercent;
  }

  /**
   * Returns the settling time.
   *
   * @return settling time in seconds, or NaN if not settled
   */
  public double getSettlingTimeSeconds() {
    return settlingTimeSeconds;
  }

  /**
   * Returns the output saturation fraction.
   *
   * @return fraction of samples at output limits from 0 to 1
   */
  public double getOutputSaturationFraction() {
    return outputSaturationFraction;
  }

  /**
   * Checks whether the final response is stable by the study tolerance.
   *
   * @return true when stable at the end of the data window
   */
  public boolean isStableAtEnd() {
    return stableAtEnd;
  }

  /**
   * Returns a short tuning recommendation.
   *
   * @return recommendation text
   */
  public String getRecommendation() {
    return recommendation;
  }

  /**
   * Serializes the metrics to formatted JSON.
   *
   * @return JSON result
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().create().toJson(this);
  }
}