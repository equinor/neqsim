package neqsim.process.mpc;

import java.io.Serializable;

/**
 * Represents a step response from a single input to a single output.
 *
 * <p>
 * A step response captures how a controlled variable responds over time to a step change in a
 * manipulated variable. This data is used for system identification and model-based control.
 * </p>
 *
 * <p>
 * The class provides methods to:
 * </p>
 * <ul>
 * <li>Access raw step response data</li>
 * <li>Fit first-order plus dead-time (FOPDT) models</li>
 * <li>Fit second-order plus dead-time (SOPDT) models</li>
 * <li>Export step coefficients for DMC-style controllers</li>
 * </ul>
 *
 * @author Even Solbraa
 * @version 1.0
 * @since 3.0
 */
public class StepResponse implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Name of the manipulated variable. */
  private final String mvName;

  /** Name of the controlled variable. */
  private final String cvName;

  /** Time points (in seconds). */
  private final double[] time;

  /** CV response values at each time point. */
  private final double[] response;

  /** Step size applied to MV. */
  private final double stepSize;

  /** Baseline CV value before step. */
  private final double baselineValue;

  /** Sample time interval. */
  private final double sampleTime;

  /** Unit for the CV. */
  private final String cvUnit;

  /** Unit for the MV. */
  private final String mvUnit;

  // Fitted FOPDT parameters
  private double fittedGain = Double.NaN;
  private double fittedTimeConstant = Double.NaN;
  private double fittedDeadTime = Double.NaN;
  private double fittedBias = Double.NaN;
  private double fitError = Double.NaN;

  /**
   * Construct a step response.
   *
   * @param mvName name of the manipulated variable
   * @param cvName name of the controlled variable
   * @param time array of time points (seconds)
   * @param response array of CV response values
   * @param stepSize the step size applied to MV
   * @param baselineValue CV value before the step
   * @param sampleTime sample interval in seconds
   * @param mvUnit unit for MV
   * @param cvUnit unit for CV
   */
  public StepResponse(String mvName, String cvName, double[] time, double[] response,
      double stepSize, double baselineValue, double sampleTime, String mvUnit, String cvUnit) {
    this.mvName = mvName;
    this.cvName = cvName;
    this.time = time != null ? time.clone() : new double[0];
    this.response = response != null ? response.clone() : new double[0];
    this.stepSize = stepSize;
    this.baselineValue = baselineValue;
    this.sampleTime = sampleTime;
    this.mvUnit = mvUnit;
    this.cvUnit = cvUnit;
  }

  /**
   * Get the MV name.
   *
   * @return manipulated variable name
   */
  public String getMvName() {
    return mvName;
  }

  /**
   * Get the CV name.
   *
   * @return controlled variable name
   */
  public String getCvName() {
    return cvName;
  }

  /**
   * Get the time array.
   *
   * @return copy of time points
   */
  public double[] getTime() {
    return time.clone();
  }

  /**
   * Get the response array.
   *
   * @return copy of response values
   */
  public double[] getResponse() {
    return response.clone();
  }

  /**
   * Get the normalized response (change from baseline per unit MV change).
   *
   * @return normalized step response coefficients
   */
  public double[] getNormalizedResponse() {
    double[] normalized = new double[response.length];
    for (int i = 0; i < response.length; i++) {
      if (Math.abs(stepSize) > 1e-12) {
        normalized[i] = (response[i] - baselineValue) / stepSize;
      }
    }
    return normalized;
  }

  /**
   * Get the step size.
   *
   * @return the MV step magnitude
   */
  public double getStepSize() {
    return stepSize;
  }

  /**
   * Get the baseline CV value.
   *
   * @return CV value before step
   */
  public double getBaselineValue() {
    return baselineValue;
  }

  /**
   * Get the sample time.
   *
   * @return sample interval in seconds
   */
  public double getSampleTime() {
    return sampleTime;
  }

  /**
   * Get the number of samples.
   *
   * @return length of response array
   */
  public int getNumSamples() {
    return response.length;
  }

  /**
   * Get the steady-state gain (Kp).
   *
   * <p>
   * The gain is calculated as the final response change divided by the step size.
   * </p>
   *
   * @return the steady-state gain
   */
  public double getGain() {
    if (Double.isFinite(fittedGain)) {
      return fittedGain;
    }
    if (response.length == 0 || Math.abs(stepSize) < 1e-12) {
      return 0.0;
    }
    // Use last value as steady-state
    double finalValue = response[response.length - 1];
    return (finalValue - baselineValue) / stepSize;
  }

  /**
   * Fit a first-order plus dead-time (FOPDT) model to the response.
   *
   * <p>
   * The FOPDT model is: y(t) = K * (1 - exp(-(t-θ)/τ)) for t &gt; θ, else 0 where K is gain, τ is
   * time constant, θ is dead time.
   * </p>
   *
   * @return this step response with fitted parameters
   */
  public StepResponse fitFOPDT() {
    if (response.length < 3) {
      return this;
    }

    // Calculate steady-state gain
    double deltaY = response[response.length - 1] - baselineValue;
    fittedGain = Math.abs(stepSize) > 1e-12 ? deltaY / stepSize : 0.0;
    fittedBias = baselineValue;

    if (Math.abs(deltaY) < 1e-12) {
      fittedTimeConstant = 1.0;
      fittedDeadTime = 0.0;
      return this;
    }

    // Find dead time: time to reach 5% of final change
    fittedDeadTime = 0.0;
    double threshold5 = baselineValue + 0.05 * deltaY;
    for (int i = 0; i < response.length; i++) {
      boolean reached = deltaY > 0 ? response[i] > threshold5 : response[i] < threshold5;
      if (reached) {
        fittedDeadTime = i > 0 ? time[i - 1] : 0.0;
        break;
      }
    }

    // Find time constant: time from dead time to reach 63.2% of final change
    double threshold63 = baselineValue + 0.632 * deltaY;
    fittedTimeConstant = time[time.length - 1] / 4; // Default
    for (int i = 0; i < response.length; i++) {
      boolean reached = deltaY > 0 ? response[i] > threshold63 : response[i] < threshold63;
      if (reached) {
        fittedTimeConstant = Math.max(0.1, time[i] - fittedDeadTime);
        break;
      }
    }

    // Calculate fit error
    fitError = calculateFitError();

    return this;
  }

  /**
   * Calculate the mean squared error of the FOPDT fit.
   *
   * @return the MSE
   */
  private double calculateFitError() {
    if (!Double.isFinite(fittedGain) || !Double.isFinite(fittedTimeConstant)) {
      return Double.NaN;
    }

    double sumSquaredError = 0.0;
    int count = 0;

    for (int i = 0; i < response.length; i++) {
      double t = time[i];
      double predicted;
      if (t <= fittedDeadTime) {
        predicted = baselineValue;
      } else {
        double tEffective = t - fittedDeadTime;
        predicted = baselineValue
            + fittedGain * stepSize * (1.0 - Math.exp(-tEffective / fittedTimeConstant));
      }
      double error = response[i] - predicted;
      sumSquaredError += error * error;
      count++;
    }

    return count > 0 ? sumSquaredError / count : Double.NaN;
  }

  /**
   * Get the fitted time constant.
   *
   * @return τ in seconds
   */
  public double getTimeConstant() {
    if (!Double.isFinite(fittedTimeConstant)) {
      fitFOPDT();
    }
    return fittedTimeConstant;
  }

  /**
   * Get the fitted dead time.
   *
   * @return θ in seconds
   */
  public double getDeadTime() {
    if (!Double.isFinite(fittedDeadTime)) {
      fitFOPDT();
    }
    return fittedDeadTime;
  }

  /**
   * Get the FOPDT fit error.
   *
   * @return mean squared error
   */
  public double getFitError() {
    if (!Double.isFinite(fitError)) {
      fitFOPDT();
    }
    return fitError;
  }

  /**
   * Get step response coefficients for DMC-style controllers.
   *
   * <p>
   * Returns the normalized step response at each sample time, which can be used directly in Dynamic
   * Matrix Control algorithms.
   * </p>
   *
   * @param numCoefficients number of coefficients to return
   * @return array of step response coefficients
   */
  public double[] getStepCoefficients(int numCoefficients) {
    double[] coefficients = new double[numCoefficients];
    double[] normalized = getNormalizedResponse();

    for (int i = 0; i < numCoefficients; i++) {
      if (i < normalized.length) {
        coefficients[i] = normalized[i];
      } else if (normalized.length > 0) {
        // Extend with steady-state value
        coefficients[i] = normalized[normalized.length - 1];
      }
    }
    return coefficients;
  }

  /**
   * Simulate the CV response to an MV trajectory using convolution.
   *
   * @param mvMoves array of MV move increments
   * @return predicted CV trajectory
   */
  public double[] convolve(double[] mvMoves) {
    double[] stepCoeffs = getNormalizedResponse();
    int horizon = mvMoves.length + stepCoeffs.length - 1;
    double[] cvTrajectory = new double[horizon];

    for (int k = 0; k < horizon; k++) {
      cvTrajectory[k] = baselineValue;
      for (int i = 0; i <= k && i < mvMoves.length; i++) {
        int coeffIndex = k - i;
        if (coeffIndex < stepCoeffs.length) {
          cvTrajectory[k] += stepCoeffs[coeffIndex] * mvMoves[i];
        } else if (stepCoeffs.length > 0) {
          cvTrajectory[k] += stepCoeffs[stepCoeffs.length - 1] * mvMoves[i];
        }
      }
    }
    return cvTrajectory;
  }

  /**
   * Get the settling time (time to reach 95% of final value).
   *
   * @return settling time in seconds
   */
  public double getSettlingTime() {
    if (response.length == 0) {
      return 0.0;
    }

    double deltaY = response[response.length - 1] - baselineValue;
    if (Math.abs(deltaY) < 1e-12) {
      return 0.0;
    }

    double threshold95 = baselineValue + 0.95 * deltaY;
    for (int i = 0; i < response.length; i++) {
      boolean reached = deltaY > 0 ? response[i] >= threshold95 : response[i] <= threshold95;
      if (reached) {
        return time[i];
      }
    }
    return time[time.length - 1];
  }

  /**
   * Get the rise time (time from 10% to 90% of final value).
   *
   * @return rise time in seconds
   */
  public double getRiseTime() {
    if (response.length == 0) {
      return 0.0;
    }

    double deltaY = response[response.length - 1] - baselineValue;
    if (Math.abs(deltaY) < 1e-12) {
      return 0.0;
    }

    double threshold10 = baselineValue + 0.10 * deltaY;
    double threshold90 = baselineValue + 0.90 * deltaY;
    double time10 = 0.0;
    double time90 = time[time.length - 1];

    for (int i = 0; i < response.length; i++) {
      boolean reached10 = deltaY > 0 ? response[i] >= threshold10 : response[i] <= threshold10;
      if (reached10) {
        time10 = time[i];
        break;
      }
    }

    for (int i = 0; i < response.length; i++) {
      boolean reached90 = deltaY > 0 ? response[i] >= threshold90 : response[i] <= threshold90;
      if (reached90) {
        time90 = time[i];
        break;
      }
    }

    return time90 - time10;
  }

  /**
   * Check if the response shows inverse response (wrong-way behavior).
   *
   * @return true if inverse response detected
   */
  public boolean hasInverseResponse() {
    if (response.length < 3) {
      return false;
    }

    double finalChange = response[response.length - 1] - baselineValue;
    if (Math.abs(finalChange) < 1e-12) {
      return false;
    }

    // Check if initial response goes opposite to final
    for (int i = 1; i < Math.min(response.length / 4, 10); i++) {
      double initialChange = response[i] - baselineValue;
      if (Math.abs(initialChange) > 0.05 * Math.abs(finalChange)) {
        // Significant initial change - check direction
        if (initialChange * finalChange < 0) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("StepResponse {\n");
    sb.append("  MV: ").append(mvName);
    if (mvUnit != null) {
      sb.append(" [").append(mvUnit).append("]");
    }
    sb.append("\n");
    sb.append("  CV: ").append(cvName);
    if (cvUnit != null) {
      sb.append(" [").append(cvUnit).append("]");
    }
    sb.append("\n");
    sb.append("  stepSize: ").append(String.format("%.4f", stepSize)).append("\n");
    sb.append("  gain: ").append(String.format("%.4f", getGain())).append("\n");
    sb.append("  timeConstant: ").append(String.format("%.2f", getTimeConstant())).append(" s\n");
    sb.append("  deadTime: ").append(String.format("%.2f", getDeadTime())).append(" s\n");
    sb.append("  settlingTime: ").append(String.format("%.2f", getSettlingTime())).append(" s\n");
    sb.append("  numSamples: ").append(getNumSamples()).append("\n");
    sb.append("}");
    return sb.toString();
  }
}
