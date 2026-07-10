/*
 * ControllerPerformanceMetrics.java
 */

package neqsim.process.controllerdevice;

import java.io.Serializable;
import java.util.List;

/**
 * Loop-tuning key performance indicators (KPIs) computed from a controller event log or from raw time / process-value /
 * set-point / controller-output arrays.
 *
 * <p>
 * The helper computes the standard metrics used when comparing PID tunings on a control loop: the integral error
 * criteria (IAE, ISE, ITAE), process-value variability, controller-output (valve) travel and reversals, and the
 * settling time. It lets tuning studies report consistent numbers without re-implementing the definitions in every
 * task.
 * </p>
 *
 * <p>
 * Instances are immutable and are created through the static factory methods {@link #fromEventLog(List)} and
 * {@link #fromArrays(double[], double[], double[], double[])}.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public final class ControllerPerformanceMetrics implements Serializable {
  private static final long serialVersionUID = 1L;

  /** Default settling band as a fraction of max(|setpoint|, 1). */
  public static final double DEFAULT_SETTLING_TOLERANCE = 0.02;

  private final int sampleCount;
  private final double duration;
  private final double integralAbsoluteError;
  private final double integralSquaredError;
  private final double integralTimeAbsoluteError;
  private final double meanProcessValue;
  private final double processValueStandardDeviation;
  private final double peakAbsoluteError;
  private final double controllerOutputTravel;
  private final int controllerOutputReversals;
  private final double settlingTime;
  private final double settlingTolerance;

  private ControllerPerformanceMetrics(int sampleCount, double duration, double integralAbsoluteError,
      double integralSquaredError, double integralTimeAbsoluteError, double meanProcessValue,
      double processValueStandardDeviation, double peakAbsoluteError, double controllerOutputTravel,
      int controllerOutputReversals, double settlingTime, double settlingTolerance) {
    this.sampleCount = sampleCount;
    this.duration = duration;
    this.integralAbsoluteError = integralAbsoluteError;
    this.integralSquaredError = integralSquaredError;
    this.integralTimeAbsoluteError = integralTimeAbsoluteError;
    this.meanProcessValue = meanProcessValue;
    this.processValueStandardDeviation = processValueStandardDeviation;
    this.peakAbsoluteError = peakAbsoluteError;
    this.controllerOutputTravel = controllerOutputTravel;
    this.controllerOutputReversals = controllerOutputReversals;
    this.settlingTime = settlingTime;
    this.settlingTolerance = settlingTolerance;
  }

  /**
   * Compute loop KPIs from a controller event log using the default settling tolerance.
   *
   * @param eventLog list of {@link ControllerEvent} entries in chronological order
   * @return computed performance metrics
   */
  public static ControllerPerformanceMetrics fromEventLog(List<ControllerEvent> eventLog) {
    return fromEventLog(eventLog, DEFAULT_SETTLING_TOLERANCE);
  }

  /**
   * Compute loop KPIs from a controller event log.
   *
   * @param eventLog list of {@link ControllerEvent} entries in chronological order
   * @param settlingTolerance settling band as a fraction of max(|setpoint|, 1); negative values are clamped to 0
   * @return computed performance metrics
   */
  public static ControllerPerformanceMetrics fromEventLog(List<ControllerEvent> eventLog, double settlingTolerance) {
    if (eventLog == null || eventLog.isEmpty()) {
      return new ControllerPerformanceMetrics(0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0, 0.0,
          Math.max(0.0, settlingTolerance));
    }
    int n = eventLog.size();
    double[] time = new double[n];
    double[] pv = new double[n];
    double[] sp = new double[n];
    double[] op = new double[n];
    for (int i = 0; i < n; i++) {
      ControllerEvent event = eventLog.get(i);
      time[i] = event.getTime();
      pv[i] = event.getMeasuredValue();
      sp[i] = event.getSetPoint();
      op[i] = event.getResponse();
    }
    return fromArrays(time, pv, sp, op, settlingTolerance);
  }

  /**
   * Compute loop KPIs from raw arrays using the default settling tolerance.
   *
   * @param time simulation time samples [s]
   * @param processValue measured process value samples
   * @param setPoint set-point samples
   * @param controllerOutput controller output (valve) samples
   * @return computed performance metrics
   */
  public static ControllerPerformanceMetrics fromArrays(double[] time, double[] processValue, double[] setPoint,
      double[] controllerOutput) {
    return fromArrays(time, processValue, setPoint, controllerOutput, DEFAULT_SETTLING_TOLERANCE);
  }

  /**
   * Compute loop KPIs from raw arrays.
   *
   * @param time simulation time samples [s]
   * @param processValue measured process value samples
   * @param setPoint set-point samples
   * @param controllerOutput controller output (valve) samples
   * @param settlingTolerance settling band as a fraction of max(|setpoint|, 1); negative values are clamped to 0
   * @return computed performance metrics
   * @throws IllegalArgumentException if the input arrays are null or of differing length
   */
  public static ControllerPerformanceMetrics fromArrays(double[] time, double[] processValue, double[] setPoint,
      double[] controllerOutput, double settlingTolerance) {
    if (time == null || processValue == null || setPoint == null || controllerOutput == null) {
      throw new IllegalArgumentException("Input arrays must not be null.");
    }
    int n = time.length;
    if (processValue.length != n || setPoint.length != n || controllerOutput.length != n) {
      throw new IllegalArgumentException("Input arrays must all have the same length.");
    }
    double tolerance = Math.max(0.0, settlingTolerance);
    if (n == 0) {
      return new ControllerPerformanceMetrics(0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0, 0.0, tolerance);
    }

    double startTime = time[0];
    double duration = time[n - 1] - startTime;

    double iae = 0.0;
    double ise = 0.0;
    double itae = 0.0;
    double travel = 0.0;
    int reversals = 0;
    double peakAbsError = 0.0;
    double pvSum = 0.0;
    double lastTimeOutsideBand = 0.0;
    double previousTravelStep = 0.0;

    for (int i = 0; i < n; i++) {
      double error = processValue[i] - setPoint[i];
      double absError = Math.abs(error);
      if (absError > peakAbsError) {
        peakAbsError = absError;
      }
      pvSum += processValue[i];

      double band = tolerance * Math.max(Math.abs(setPoint[i]), 1.0);
      if (absError > band) {
        lastTimeOutsideBand = time[i] - startTime;
      }

      if (i > 0) {
        double dt = time[i] - time[i - 1];
        // Trapezoidal integration of the error criteria over the interval.
        double prevError = processValue[i - 1] - setPoint[i - 1];
        double prevAbsError = Math.abs(prevError);
        iae += 0.5 * (absError + prevAbsError) * dt;
        ise += 0.5 * (error * error + prevError * prevError) * dt;
        double relTime = time[i] - startTime;
        double prevRelTime = time[i - 1] - startTime;
        itae += 0.5 * (relTime * absError + prevRelTime * prevAbsError) * dt;

        double travelStep = controllerOutput[i] - controllerOutput[i - 1];
        travel += Math.abs(travelStep);
        if (travelStep != 0.0 && previousTravelStep != 0.0
            && Math.signum(travelStep) != Math.signum(previousTravelStep)) {
          reversals++;
        }
        if (travelStep != 0.0) {
          previousTravelStep = travelStep;
        }
      }
    }

    double meanPv = pvSum / n;
    double varianceSum = 0.0;
    for (int i = 0; i < n; i++) {
      double d = processValue[i] - meanPv;
      varianceSum += d * d;
    }
    double pvStdDev = Math.sqrt(varianceSum / n);

    return new ControllerPerformanceMetrics(n, duration, iae, ise, itae, meanPv, pvStdDev, peakAbsError, travel,
        reversals, lastTimeOutsideBand, tolerance);
  }

  /**
   * Number of samples used in the calculation.
   *
   * @return sample count
   */
  public int getSampleCount() {
    return sampleCount;
  }

  /**
   * Total time span of the samples.
   *
   * @return duration [s]
   */
  public double getDuration() {
    return duration;
  }

  /**
   * Integral of the absolute error (IAE).
   *
   * @return IAE in (process-value unit).s
   */
  public double getIntegralAbsoluteError() {
    return integralAbsoluteError;
  }

  /**
   * Integral of the squared error (ISE).
   *
   * @return ISE in (process-value unit)^2.s
   */
  public double getIntegralSquaredError() {
    return integralSquaredError;
  }

  /**
   * Integral of time multiplied by the absolute error (ITAE), time referenced to the first sample.
   *
   * @return ITAE in (process-value unit).s^2
   */
  public double getIntegralTimeAbsoluteError() {
    return integralTimeAbsoluteError;
  }

  /**
   * Mean process value over the samples.
   *
   * @return mean process value
   */
  public double getMeanProcessValue() {
    return meanProcessValue;
  }

  /**
   * Process-value variability, expressed as the population standard deviation of the process value.
   *
   * @return process-value standard deviation
   */
  public double getProcessValueStandardDeviation() {
    return processValueStandardDeviation;
  }

  /**
   * Peak absolute control error observed.
   *
   * @return peak absolute error
   */
  public double getPeakAbsoluteError() {
    return peakAbsoluteError;
  }

  /**
   * Total controller-output (valve) travel, the sum of the absolute output increments.
   *
   * @return total controller-output travel in output units
   */
  public double getControllerOutputTravel() {
    return controllerOutputTravel;
  }

  /**
   * Number of controller-output (valve) direction reversals.
   *
   * @return reversal count
   */
  public int getControllerOutputReversals() {
    return controllerOutputReversals;
  }

  /**
   * Settling time, the time of the last sample where the error exceeded the settling band, relative to the first
   * sample.
   *
   * @return settling time [s]
   */
  public double getSettlingTime() {
    return settlingTime;
  }

  /**
   * Settling band tolerance used for the settling-time calculation.
   *
   * @return settling tolerance (fraction of max(|setpoint|, 1))
   */
  public double getSettlingTolerance() {
    return settlingTolerance;
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    return "ControllerPerformanceMetrics{" + "samples=" + sampleCount + ", duration=" + duration + " s" + ", IAE="
        + integralAbsoluteError + ", ISE=" + integralSquaredError + ", ITAE=" + integralTimeAbsoluteError + ", meanPV="
        + meanProcessValue + ", PVstd=" + processValueStandardDeviation + ", peakError=" + peakAbsoluteError
        + ", valveTravel=" + controllerOutputTravel + ", valveReversals=" + controllerOutputReversals
        + ", settlingTime=" + settlingTime + " s}";
  }
}
