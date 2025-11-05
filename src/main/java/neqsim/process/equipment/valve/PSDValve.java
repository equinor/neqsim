package neqsim.process.equipment.valve;

import java.util.UUID;
import neqsim.process.alarm.AlarmLevel;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.measurementdevice.MeasurementDeviceInterface;

/**
 * Process Shutdown (PSD) Valve that automatically closes on High-High Alarm (PAHH).
 * 
 * <p>
 * A PSD valve is a fast-acting isolation valve that provides emergency shutdown protection. It
 * monitors a pressure transmitter and closes rapidly when a High-High (HIHI) alarm is triggered,
 * preventing overpressure conditions from propagating through the process.
 * 
 * <p>
 * Key features:
 * <ul>
 * <li>Automatic closure on HIHI alarm from linked pressure transmitter</li>
 * <li>Configurable fast closure time (default 2 seconds)</li>
 * <li>Manual reset capability after alarm clears</li>
 * <li>Trip state tracking for safety interlock logic</li>
 * </ul>
 * 
 * <p>
 * Typical usage:
 * 
 * <pre>
 * // Create pressure transmitter with alarm configuration
 * PressureTransmitter PT = new PressureTransmitter("PT-101", separatorInlet);
 * PT.setAlarmConfig(
 *     AlarmConfig.builder().highHighLimit(55.0).deadband(1.0).delay(0.5).unit("bara").build());
 * 
 * // Create PSD valve linked to transmitter
 * PSDValve psdValve = new PSDValve("PSD-101", feedStream);
 * psdValve.linkToPressureTransmitter(PT);
 * psdValve.setClosureTime(2.0); // 2 seconds fast closure
 * 
 * // In dynamic simulation loop
 * system.runTransient(dt, UUID.randomUUID());
 * // Valve automatically closes if PT-101 goes into HIHI alarm
 * </pre>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class PSDValve extends ThrottlingValve {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /** Pressure transmitter monitored for HIHI alarm. */
  private MeasurementDeviceInterface pressureTransmitter;

  /** Indicates if valve has tripped due to HIHI alarm. */
  private boolean hasTripped = false;

  /** Time required for valve to close (seconds). */
  private double closureTime = 2.0; // Fast closure - 2 seconds default

  /** Flag to enable/disable automatic trip on HIHI. */
  private boolean tripEnabled = true;

  /**
   * Constructor for PSDValve.
   *
   * @param name name of PSD valve
   */
  public PSDValve(String name) {
    super(name);
    // PSD valves start fully open
    setPercentValveOpening(100.0);
  }

  /**
   * Constructor for PSDValve.
   *
   * @param name name of PSD valve
   * @param inletStream inlet stream to valve
   */
  public PSDValve(String name, StreamInterface inletStream) {
    super(name, inletStream);
    // PSD valves start fully open
    setPercentValveOpening(100.0);
  }

  /**
   * Links this PSD valve to a pressure transmitter for alarm monitoring.
   *
   * @param transmitter pressure transmitter to monitor for HIHI alarm
   */
  public void linkToPressureTransmitter(MeasurementDeviceInterface transmitter) {
    this.pressureTransmitter = transmitter;
  }

  /**
   * Sets the valve closure time.
   *
   * @param closureTime time in seconds for valve to close completely
   */
  public void setClosureTime(double closureTime) {
    this.closureTime = Math.max(0.1, closureTime); // Minimum 0.1 seconds
    // Use the valve closing travel time mechanism for fast closure
    setClosingTravelTime(closureTime);
  }

  /**
   * Gets the configured closure time.
   *
   * @return closure time in seconds
   */
  public double getClosureTime() {
    return closureTime;
  }

  /**
   * Checks if valve has tripped.
   *
   * @return true if valve has tripped on HIHI alarm
   */
  public boolean hasTripped() {
    return hasTripped;
  }

  /**
   * Resets the trip state, allowing valve to be reopened.
   * 
   * <p>
   * In real operations, this would require operator action and verification that the alarm
   * condition has cleared. The valve will not automatically reopen after reset - it must be
   * manually opened via setPercentValveOpening().
   * </p>
   */
  public void reset() {
    hasTripped = false;
  }

  /**
   * Enables or disables automatic trip on HIHI alarm.
   *
   * @param enabled true to enable trip on HIHI, false to disable
   */
  public void setTripEnabled(boolean enabled) {
    this.tripEnabled = enabled;
  }

  /**
   * Checks if trip is enabled.
   *
   * @return true if trip on HIHI is enabled
   */
  public boolean isTripEnabled() {
    return tripEnabled;
  }

  /**
   * Performs dynamic simulation step with automatic trip logic.
   * 
   * <p>
   * This method overrides the base class to add HIHI alarm monitoring. If the linked pressure
   * transmitter reports a HIHI alarm and trip is enabled, the valve will automatically command
   * closure.
   * </p>
   *
   * @param dt time step in seconds
   * @param id unique identifier for this calculation
   */
  @Override
  public void runTransient(double dt, UUID id) {
    if (pressureTransmitter != null && tripEnabled && !hasTripped) {
      // Check if HIHI alarm is active
      if (pressureTransmitter.getAlarmState() != null
          && pressureTransmitter.getAlarmState().isActive()) {
        AlarmLevel activeLevel = pressureTransmitter.getAlarmState().getActiveLevel();
        if (activeLevel == AlarmLevel.HIHI) {
          // HIHI alarm detected - initiate trip
          hasTripped = true;
          // Command valve to close
          setPercentValveOpening(0.0);
        }
      }
    }

    // If tripped, ensure valve stays closed
    if (hasTripped) {
      setPercentValveOpening(0.0);
    }

    // Run base class transient calculation
    super.runTransient(dt, id);
  }

  /**
   * Gets the linked pressure transmitter.
   *
   * @return pressure transmitter being monitored, or null if not linked
   */
  public MeasurementDeviceInterface getPressureTransmitter() {
    return pressureTransmitter;
  }

  /**
   * Overrides setPercentValveOpening to prevent opening when tripped.
   * 
   * <p>
   * If the valve has tripped, it cannot be opened until reset() is called. This prevents
   * inadvertent reopening during an alarm condition.
   * </p>
   *
   * @param opening desired valve opening percentage (0-100)
   */
  @Override
  public void setPercentValveOpening(double opening) {
    if (hasTripped && opening > 0.0) {
      // Cannot open valve while tripped - must reset first
      super.setPercentValveOpening(0.0);
    } else {
      super.setPercentValveOpening(opening);
    }
  }

  /**
   * Gets a string representation of the PSD valve state.
   *
   * @return string describing valve state including trip status
   */
  @Override
  public String toString() {
    return getName() + " [PSD Valve] - Opening: " + String.format("%.1f", getPercentValveOpening())
        + "%, Tripped: " + (hasTripped ? "YES" : "NO") + ", Trip Enabled: "
        + (tripEnabled ? "YES" : "NO");
  }
}
