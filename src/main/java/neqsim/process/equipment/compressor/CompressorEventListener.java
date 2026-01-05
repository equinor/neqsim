package neqsim.process.equipment.compressor;

/**
 * Listener interface for compressor events in dynamic simulations.
 *
 * <p>
 * Implement this interface to receive notifications about significant compressor events such as
 * surge approach, speed limits, power limits, and state changes. This enables custom control logic
 * and alarm handling.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public interface CompressorEventListener {

  /**
   * Called when the compressor operating point approaches the surge line.
   *
   * <p>
   * This event is triggered when the surge margin falls below the warning threshold but is still
   * above the critical threshold.
   * </p>
   *
   * @param compressor the compressor triggering this event
   * @param surgeMargin the current surge margin as a ratio (e.g., 0.15 = 15% above surge)
   * @param isCritical true if the margin is below the critical threshold
   */
  void onSurgeApproach(Compressor compressor, double surgeMargin, boolean isCritical);

  /**
   * Called when a surge event occurs (operating point crosses the surge line).
   *
   * <p>
   * This indicates an actual surge event has occurred. Immediate protective action should be taken.
   * </p>
   *
   * @param compressor the compressor triggering this event
   * @param surgeMargin the surge margin at surge (negative indicates below surge line)
   */
  void onSurgeOccurred(Compressor compressor, double surgeMargin);

  /**
   * Called when the compressor speed exceeds the maximum curve speed.
   *
   * <p>
   * This indicates the compressor is operating outside its design envelope and performance
   * predictions may be unreliable.
   * </p>
   *
   * @param compressor the compressor triggering this event
   * @param currentSpeed the current speed in RPM
   * @param ratio the ratio currentSpeed/maxSpeed
   */
  void onSpeedLimitExceeded(Compressor compressor, double currentSpeed, double ratio);

  /**
   * Called when the compressor speed falls below the minimum curve speed.
   *
   * <p>
   * This indicates turndown issues or that the compressor is oversized for the current conditions.
   * </p>
   *
   * @param compressor the compressor triggering this event
   * @param currentSpeed the current speed in RPM
   * @param ratio the ratio currentSpeed/minSpeed
   */
  void onSpeedBelowMinimum(Compressor compressor, double currentSpeed, double ratio);

  /**
   * Called when the required power exceeds the driver capacity.
   *
   * <p>
   * This indicates the driver cannot provide enough power for the requested operating point.
   * </p>
   *
   * @param compressor the compressor triggering this event
   * @param currentPower the required power in kW
   * @param maxPower the available driver power in kW
   */
  void onPowerLimitExceeded(Compressor compressor, double currentPower, double maxPower);

  /**
   * Called when the compressor operating state changes.
   *
   * @param compressor the compressor triggering this event
   * @param oldState the previous state
   * @param newState the new state
   */
  void onStateChange(Compressor compressor, CompressorState oldState, CompressorState newState);

  /**
   * Called when the compressor approaches the stone wall (choke) limit.
   *
   * @param compressor the compressor triggering this event
   * @param stoneWallMargin the margin to stone wall as a ratio
   */
  void onStoneWallApproach(Compressor compressor, double stoneWallMargin);

  /**
   * Called when the startup sequence completes successfully.
   *
   * @param compressor the compressor triggering this event
   */
  void onStartupComplete(Compressor compressor);

  /**
   * Called when the shutdown sequence completes.
   *
   * @param compressor the compressor triggering this event
   */
  void onShutdownComplete(Compressor compressor);
}
