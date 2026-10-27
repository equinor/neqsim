package neqsim.process.measurementdevice;

import java.util.UUID;

/**
 * Opt-in sample-and-hold contract for one physical transient measurement scan.
 *
 * <p>
 * Sampling advances mutable instrument state exactly once for a physical-step identifier. Controllers, measurement
 * history and alarm evaluation can then read the same held value without consuming filter, delay, noise or fault state
 * again. Implementations remain disabled by default so legacy direct-read behaviour is unchanged.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public interface TransientMeasurementDevice extends MeasurementDeviceInterface {
  /**
   * Enables or disables physical-step sample-and-hold behaviour.
   *
   * @param enabled whether process transient scans may sample this device
   */
  void setTransientSamplingEnabled(boolean enabled);

  /**
   * Returns whether physical-step sample-and-hold behaviour is enabled.
   *
   * @return {@code true} when transient sampling is enabled
   */
  boolean isTransientSamplingEnabled();

  /**
   * Advances the instrument signal chain once for a physical timestep.
   *
   * @param dt finite positive sample interval in seconds
   * @param physicalStepIdentifier non-null physical-step identifier
   */
  void sampleTransient(double dt, UUID physicalStepIdentifier);

  /**
   * Reports whether a held sample belongs to the supplied physical timestep.
   *
   * @param physicalStepIdentifier physical-step identifier to test
   * @return {@code true} when the held sample belongs to that timestep
   */
  boolean hasTransientSample(UUID physicalStepIdentifier);

  /**
   * Reads the held sample in the device's configured engineering unit without advancing signal state.
   *
   * @return held sample
   */
  default double getTransientSample() {
    return getTransientSample(getUnit());
  }

  /**
   * Reads the held sample in a requested engineering unit without advancing signal state.
   *
   * @param unit requested engineering unit
   * @return held sample converted to {@code unit}
   */
  double getTransientSample(String unit);
}
