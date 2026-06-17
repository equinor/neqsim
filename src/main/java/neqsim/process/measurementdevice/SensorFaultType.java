package neqsim.process.measurementdevice;

/**
 * Types of sensor faults that can be injected into a measurement device for dynamic simulation
 * testing. These faults model common transmitter failure modes encountered in industrial process
 * control systems.
 *
 * <p>
 * Fault injection is configured on a {@link MeasurementDeviceBaseClass} using
 * {@link MeasurementDeviceBaseClass#setFault(SensorFaultType, double)} and cleared with
 * {@link MeasurementDeviceBaseClass#clearFault()}.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public enum SensorFaultType {
  /** No fault present; normal operation. */
  NONE,
  /** Output is frozen at a fixed value regardless of the actual process measurement. */
  STUCK_AT_VALUE,
  /**
   * Output drifts linearly over time. The fault parameter specifies the drift rate in engineering
   * units per second.
   */
  LINEAR_DRIFT,
  /** A constant bias (offset) is added to the true measurement. */
  BIAS,
  /**
   * A burst of Gaussian noise with amplitude given by the fault parameter is added to the
   * measurement. Unlike the baseline noise configured via
   * {@link MeasurementDeviceBaseClass#setNoiseStdDev(double)}, noise-burst faults are intended for
   * transient disturbance testing.
   */
  NOISE_BURST,
  /**
   * The measurement is clamped at the fault parameter value (saturation). Values above the
   * saturation limit are reported at the limit; values below pass through unchanged. Use a negative
   * fault parameter for low-side saturation.
   */
  SATURATION
}
