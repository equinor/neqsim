package neqsim.process.operations;

/**
 * Utility methods for screening controller tuning from simulated or historian time series.
 *
 * <p>
 * The study is deliberately independent of any specific controller implementation. Existing
 * NeqSim controllers can generate the histories, and this class computes portable performance
 * metrics for P&amp;ID-driven operating studies.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class ControllerTuningStudy {

  /**
   * Private constructor for utility class.
   */
  private ControllerTuningStudy() {}

  /**
   * Evaluates a controller response using standard time-domain performance metrics.
   *
   * @param controllerName controller name
   * @param setPoint target value for the controlled variable
   * @param timeSeconds monotonically increasing time samples in seconds
   * @param processValue controlled variable samples
   * @param controllerOutput controller output samples, typically valve opening or speed demand
   * @param outputMin lower output limit
   * @param outputMax upper output limit
   * @param settlingTolerance absolute tolerance for settling and final stability
   * @return controller tuning result
   * @throws IllegalArgumentException if arrays are null, too short, mismatched, or non-monotonic
   */
  public static ControllerTuningResult evaluateStepResponse(String controllerName,
      double setPoint, double[] timeSeconds, double[] processValue, double[] controllerOutput,
      double outputMin, double outputMax, double settlingTolerance) {
    validateInputs(timeSeconds, processValue, controllerOutput, outputMin, outputMax,
        settlingTolerance);

    double meanAbsoluteError = meanAbsoluteError(setPoint, processValue);
    double maxAbsoluteError = maxAbsoluteError(setPoint, processValue);
    double integralAbsoluteError = integrateError(setPoint, timeSeconds, processValue, false);
    double integralSquaredError = integrateError(setPoint, timeSeconds, processValue, true);
    double overshootPercent = overshootPercent(setPoint, processValue);
    double settlingTimeSeconds = settlingTime(setPoint, timeSeconds, processValue,
        settlingTolerance);
    double outputSaturationFraction = saturationFraction(controllerOutput, outputMin, outputMax);
    boolean stableAtEnd = stableAtEnd(setPoint, processValue, settlingTolerance);
    String recommendation = recommendation(stableAtEnd, overshootPercent, settlingTimeSeconds,
        outputSaturationFraction);

    return new ControllerTuningResult(controllerName == null ? "" : controllerName, setPoint,
        meanAbsoluteError, maxAbsoluteError, integralAbsoluteError, integralSquaredError,
        overshootPercent, settlingTimeSeconds, outputSaturationFraction, stableAtEnd,
        recommendation);
  }

  /**
   * Validates study inputs.
   *
   * @param timeSeconds time samples
   * @param processValue process-value samples
   * @param controllerOutput controller-output samples
   * @param outputMin lower output limit
   * @param outputMax upper output limit
   * @param settlingTolerance absolute settling tolerance
   * @throws IllegalArgumentException if input data is invalid
   */
  private static void validateInputs(double[] timeSeconds, double[] processValue,
      double[] controllerOutput, double outputMin, double outputMax, double settlingTolerance) {
    if (timeSeconds == null || processValue == null || controllerOutput == null) {
      throw new IllegalArgumentException("Input arrays must not be null");
    }
    if (timeSeconds.length < 2) {
      throw new IllegalArgumentException("At least two time samples are required");
    }
    if (processValue.length != timeSeconds.length || controllerOutput.length != timeSeconds.length) {
      throw new IllegalArgumentException("Input arrays must have equal length");
    }
    if (outputMax <= outputMin) {
      throw new IllegalArgumentException("outputMax must be greater than outputMin");
    }
    if (settlingTolerance <= 0.0) {
      throw new IllegalArgumentException("settlingTolerance must be positive");
    }
    for (int i = 1; i < timeSeconds.length; i++) {
      if (timeSeconds[i] <= timeSeconds[i - 1]) {
        throw new IllegalArgumentException("Time samples must be strictly increasing");
      }
    }
  }

  /**
   * Calculates mean absolute error.
   *
   * @param setPoint target value
   * @param processValue process-value samples
   * @return mean absolute error
   */
  private static double meanAbsoluteError(double setPoint, double[] processValue) {
    double sum = 0.0;
    for (double value : processValue) {
      sum += Math.abs(setPoint - value);
    }
    return sum / processValue.length;
  }

  /**
   * Calculates maximum absolute error.
   *
   * @param setPoint target value
   * @param processValue process-value samples
   * @return maximum absolute error
   */
  private static double maxAbsoluteError(double setPoint, double[] processValue) {
    double max = 0.0;
    for (double value : processValue) {
      max = Math.max(max, Math.abs(setPoint - value));
    }
    return max;
  }

  /**
   * Integrates absolute or squared error using the trapezoidal rule.
   *
   * @param setPoint target value
   * @param timeSeconds time samples
   * @param processValue process-value samples
   * @param squared true to integrate squared error, false for absolute error
   * @return integrated error
   */
  private static double integrateError(double setPoint, double[] timeSeconds, double[] processValue,
      boolean squared) {
    double integral = 0.0;
    for (int i = 1; i < timeSeconds.length; i++) {
      double dt = timeSeconds[i] - timeSeconds[i - 1];
      double e0 = Math.abs(setPoint - processValue[i - 1]);
      double e1 = Math.abs(setPoint - processValue[i]);
      if (squared) {
        e0 *= e0;
        e1 *= e1;
      }
      integral += 0.5 * (e0 + e1) * dt;
    }
    return integral;
  }

  /**
   * Calculates overshoot relative to the requested step magnitude.
   *
   * @param setPoint target value
   * @param processValue process-value samples
   * @return overshoot percent
   */
  private static double overshootPercent(double setPoint, double[] processValue) {
    double initial = processValue[0];
    double step = Math.abs(setPoint - initial);
    double denominator = step > 1.0e-12 ? step : Math.max(Math.abs(setPoint), 1.0);
    double overshoot = 0.0;
    if (setPoint >= initial) {
      for (double value : processValue) {
        overshoot = Math.max(overshoot, value - setPoint);
      }
    } else {
      for (double value : processValue) {
        overshoot = Math.max(overshoot, setPoint - value);
      }
    }
    return Math.max(0.0, overshoot) / denominator * 100.0;
  }

  /**
   * Finds the first time after which all samples stay inside tolerance.
   *
   * @param setPoint target value
   * @param timeSeconds time samples
   * @param processValue process-value samples
   * @param tolerance absolute tolerance
   * @return settling time, or NaN when not settled
   */
  private static double settlingTime(double setPoint, double[] timeSeconds, double[] processValue,
      double tolerance) {
    for (int i = 0; i < timeSeconds.length; i++) {
      boolean settled = true;
      for (int j = i; j < timeSeconds.length; j++) {
        if (Math.abs(setPoint - processValue[j]) > tolerance) {
          settled = false;
          break;
        }
      }
      if (settled) {
        return timeSeconds[i];
      }
    }
    return Double.NaN;
  }

  /**
   * Calculates the fraction of output samples at actuator limits.
   *
   * @param controllerOutput controller-output samples
   * @param outputMin lower output limit
   * @param outputMax upper output limit
   * @return fraction from 0 to 1
   */
  private static double saturationFraction(double[] controllerOutput, double outputMin,
      double outputMax) {
    double tolerance = Math.max(Math.abs(outputMax - outputMin) * 1.0e-9, 1.0e-12);
    int saturated = 0;
    for (double value : controllerOutput) {
      if (value <= outputMin + tolerance || value >= outputMax - tolerance) {
        saturated++;
      }
    }
    return ((double) saturated) / controllerOutput.length;
  }

  /**
   * Checks whether the final fifth of samples is stable.
   *
   * @param setPoint target value
   * @param processValue process-value samples
   * @param tolerance absolute tolerance
   * @return true when final error and variation are within tolerance
   */
  private static boolean stableAtEnd(double setPoint, double[] processValue, double tolerance) {
    int start = Math.max(0, processValue.length - Math.max(2, processValue.length / 5));
    double mean = 0.0;
    int count = 0;
    for (int i = start; i < processValue.length; i++) {
      mean += processValue[i];
      count++;
    }
    mean /= count;
    double variance = 0.0;
    for (int i = start; i < processValue.length; i++) {
      double diff = processValue[i] - mean;
      variance += diff * diff;
    }
    double standardDeviation = Math.sqrt(variance / count);
    double finalError = Math.abs(setPoint - processValue[processValue.length - 1]);
    return finalError <= tolerance && standardDeviation <= tolerance;
  }

  /**
   * Generates a short recommendation from metrics.
   *
   * @param stableAtEnd true if final response is stable
   * @param overshootPercent overshoot percent
   * @param settlingTimeSeconds settling time or NaN
   * @param outputSaturationFraction actuator saturation fraction
   * @return recommendation text
   */
  private static String recommendation(boolean stableAtEnd, double overshootPercent,
      double settlingTimeSeconds, double outputSaturationFraction) {
    if (outputSaturationFraction > 0.20) {
      return "CHECK_ACTUATOR_LIMITS_OR_PROCESS_CAPACITY";
    }
    if (overshootPercent > 20.0) {
      return "REDUCE_GAIN_OR_INCREASE_DAMPING";
    }
    if (Double.isNaN(settlingTimeSeconds) || !stableAtEnd) {
      return "REVIEW_INTEGRAL_ACTION_AND_DISTURBANCE_REJECTION";
    }
    return "ACCEPTABLE_SCREENING_RESULT";
  }
}