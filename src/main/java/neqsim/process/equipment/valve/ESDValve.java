package neqsim.process.equipment.valve;

import java.util.UUID;
import neqsim.process.equipment.stream.StreamInterface;

/**
 * Emergency Shutdown (ESD) Valve / Isolation Valve (XV) for process safety systems.
 * 
 * <p>
 * An ESD valve is a normally-open isolation valve that closes automatically during emergency
 * shutdown events. These valves are critical safety elements designed to isolate process equipment
 * or stop flow during hazardous conditions.
 * 
 * <p>
 * Key features:
 * <ul>
 * <li>Fail-safe design: Fails to closed position (fail-closed)</li>
 * <li>Solenoid/actuator control: Energized to stay open, de-energized to close</li>
 * <li>Configurable stroke time (closure time)</li>
 * <li>Partial stroke testing capability</li>
 * <li>Emergency closure on ESD signal</li>
 * <li>Status feedback for monitoring systems</li>
 * <li>Suitable for SIL-rated safety instrumented functions</li>
 * </ul>
 * 
 * <p>
 * Design philosophy:
 * <ul>
 * <li><b>Normally open</b>: Allows normal process flow</li>
 * <li><b>Spring-return actuator</b>: Closes automatically on loss of power/signal</li>
 * <li><b>Fast acting</b>: Closes within defined stroke time (typically 5-30 seconds)</li>
 * <li><b>Tight shutoff</b>: Provides complete isolation when closed</li>
 * </ul>
 * 
 * <p>
 * Typical usage in ESD system:
 * 
 * <pre>
 * // Create process stream
 * Stream feedStream = new Stream("Feed", thermoSystem);
 * feedStream.setFlowRate(10000.0, "kg/hr");
 * feedStream.setPressure(50.0, "bara");
 * 
 * // Create ESD inlet valve (normally open)
 * ESDValve esdValve = new ESDValve("ESD-XV-101", feedStream);
 * esdValve.setStrokeTime(10.0); // 10 seconds to close
 * esdValve.setCv(500.0); // Large Cv for minimal pressure drop when open
 * 
 * // Normal operation
 * esdValve.energize(); // Keep valve open
 * esdValve.run();
 * 
 * // Emergency shutdown
 * esdValve.deEnergize(); // Triggers closure
 * 
 * // In transient simulation loop
 * esdValve.runTransient(dt, UUID.randomUUID());
 * // Valve closes progressively over stroke time
 * </pre>
 * 
 * <p>
 * Integration with ESD controller:
 * 
 * <pre>
 * // ESD controller monitors process conditions
 * if (pressure &gt; highPressureSetpoint || fireDetected || manualESDActivated) {
 *   esdValve.deEnergize(); // Initiate emergency closure
 * }
 * </pre>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class ESDValve extends ThrottlingValve {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /** Energization state of the valve solenoid/actuator. */
  private boolean isEnergized = true; // Normally energized (open)

  /** Time required for valve to fully close (seconds). */
  private double strokeTime = 10.0; // Default 10 seconds

  /** Time elapsed since de-energization started (seconds). */
  private double timeElapsedSinceTrip = 0.0;

  /** Opening percentage when trip started (for linear closure calculation). */
  private double openingAtTripStart = 100.0;

  /** Indicates if valve is currently closing. */
  private boolean isClosing = false;

  /** Fail-safe position (0.0 = closed, 100.0 = open). Default is fail-closed. */
  private double failSafePosition = 0.0;

  /** Indicates if partial stroke test is in progress. */
  private boolean partialStrokeTestActive = false;

  /** Target position for partial stroke test (typically 80-90%). */
  private double partialStrokeTestPosition = 80.0;

  /** Flag to track if valve has fully closed during current trip. */
  private boolean hasTripCompleted = false;

  /**
   * Constructor for ESDValve.
   *
   * @param name name of ESD valve
   */
  public ESDValve(String name) {
    super(name);
    // ESD valves start fully open (energized)
    setPercentValveOpening(100.0);
  }

  /**
   * Constructor for ESDValve.
   *
   * @param name name of ESD valve
   * @param inletStream inlet stream to valve
   */
  public ESDValve(String name, StreamInterface inletStream) {
    super(name, inletStream);
    // ESD valves start fully open (energized)
    setPercentValveOpening(100.0);
  }

  /**
   * Sets the valve stroke time (closure time).
   *
   * @param strokeTime time in seconds for valve to close completely
   */
  public void setStrokeTime(double strokeTime) {
    this.strokeTime = Math.max(0.5, strokeTime); // Minimum 0.5 seconds for safety
  }

  /**
   * Gets the configured stroke time.
   *
   * @return stroke time in seconds
   */
  public double getStrokeTime() {
    return strokeTime;
  }

  /**
   * Sets the fail-safe position (position valve moves to on loss of power).
   *
   * @param failSafePosition position as percentage (0.0 = closed, 100.0 = open)
   */
  public void setFailSafePosition(double failSafePosition) {
    this.failSafePosition = Math.max(0.0, Math.min(100.0, failSafePosition));
  }

  /**
   * Gets the fail-safe position.
   *
   * @return fail-safe position as percentage
   */
  public double getFailSafePosition() {
    return failSafePosition;
  }

  /**
   * Checks if valve is energized (solenoid/actuator powered).
   *
   * @return true if valve is energized
   */
  public boolean isEnergized() {
    return isEnergized;
  }

  /**
   * Energizes the valve (allows it to open/maintain open position).
   * 
   * <p>
   * In normal operation, ESD valves are energized to maintain the open position. This simulates the
   * electrical/pneumatic signal that keeps the valve actuator in the open state.
   * </p>
   */
  public void energize() {
    if (!isEnergized) {
      isEnergized = true;
      isClosing = false;
      hasTripCompleted = false;
      // Valve can now be opened (requires separate command or controller action)
    }
  }

  /**
   * De-energizes the valve (initiates emergency closure).
   * 
   * <p>
   * This simulates the ESD signal that removes power from the valve actuator, causing the
   * spring-return mechanism to close the valve. This is the primary safety action during emergency
   * shutdown.
   * </p>
   */
  public void deEnergize() {
    if (isEnergized) {
      isEnergized = false;
      isClosing = true;
      timeElapsedSinceTrip = 0.0;
      openingAtTripStart = getPercentValveOpening(); // Store current opening
      hasTripCompleted = false;
      // Spring-return actuator starts closing the valve
    }
  }

  /**
   * Trips the valve (same as de-energizing - forces closure).
   * 
   * <p>
   * Convenience method that is semantically clearer for emergency shutdown scenarios.
   * </p>
   */
  public void trip() {
    deEnergize();
  }

  /**
   * Resets the valve after emergency shutdown.
   * 
   * <p>
   * After an ESD event, the valve must be manually reset by operations personnel. This simulates
   * the reset process after the emergency condition has been resolved.
   * </p>
   */
  public void reset() {
    isEnergized = true;
    isClosing = false;
    timeElapsedSinceTrip = 0.0;
    openingAtTripStart = 100.0;
    hasTripCompleted = false;
    partialStrokeTestActive = false;
    // Valve can now be reopened
    setPercentValveOpening(100.0);
  }

  /**
   * Checks if valve is currently closing.
   *
   * @return true if valve is in the process of closing
   */
  public boolean isClosing() {
    return isClosing;
  }

  /**
   * Checks if emergency closure has completed.
   *
   * @return true if valve has fully closed after de-energization
   */
  public boolean hasTripCompleted() {
    return hasTripCompleted;
  }

  /**
   * Gets time elapsed since de-energization.
   *
   * @return time in seconds since valve started closing
   */
  public double getTimeElapsedSinceTrip() {
    return timeElapsedSinceTrip;
  }

  /**
   * Initiates a partial stroke test (PST).
   * 
   * <p>
   * Partial stroke testing is a proof test method that verifies valve functionality without causing
   * a full process shutdown. The valve closes to a specified position (e.g., 80%) and then reopens.
   * </p>
   * 
   * <p>
   * This is important for SIL-rated valves to verify functionality between full proof test
   * intervals, as required by IEC 61511.
   * </p>
   *
   * @param targetPosition target position for test (typically 80-90%)
   */
  public void startPartialStrokeTest(double targetPosition) {
    if (isEnergized && !isClosing) {
      partialStrokeTestActive = true;
      partialStrokeTestPosition = Math.max(0.0, Math.min(95.0, targetPosition));
      // Test typically doesn't close beyond 90-95% to maintain some flow
    }
  }

  /**
   * Completes the partial stroke test and returns valve to normal operation.
   */
  public void completePartialStrokeTest() {
    partialStrokeTestActive = false;
    setPercentValveOpening(100.0);
  }

  /**
   * Checks if partial stroke test is active.
   *
   * @return true if PST is in progress
   */
  public boolean isPartialStrokeTestActive() {
    return partialStrokeTestActive;
  }

  /**
   * Performs dynamic simulation step with automatic closure logic.
   * 
   * <p>
   * If the valve has been de-energized, it will close progressively according to the configured
   * stroke time until reaching the fail-safe position.
   * </p>
   *
   * @param dt time step in seconds
   * @param id unique identifier for this calculation
   */
  @Override
  public void runTransient(double dt, UUID id) {
    if (!isEnergized && isClosing) {
      // Valve is de-energized and closing
      timeElapsedSinceTrip += dt;

      // Calculate closure fraction based on stroke time
      double closureFraction = Math.min(1.0, timeElapsedSinceTrip / strokeTime);

      // Calculate current position (linear interpolation from opening at trip start to fail-safe)
      double newOpening =
          openingAtTripStart - (openingAtTripStart - failSafePosition) * closureFraction;

      setPercentValveOpening(newOpening);

      // Check if closure is complete
      if (closureFraction >= 1.0) {
        isClosing = false;
        hasTripCompleted = true;
        setPercentValveOpening(failSafePosition);
      }
    } else if (partialStrokeTestActive) {
      // Partial stroke test - close to test position then return
      double currentOpening = getPercentValveOpening();
      if (currentOpening > partialStrokeTestPosition) {
        // Closing to test position
        double closureRate = 100.0 / strokeTime; // Same rate as full closure
        double newOpening = Math.max(partialStrokeTestPosition, currentOpening - closureRate * dt);
        setPercentValveOpening(newOpening);
      }
      // Note: Test completion must be triggered externally via completePartialStrokeTest()
    }

    // Call parent runTransient to perform valve flow calculations
    super.runTransient(dt, id);
  }

  /**
   * Gets a string representation of the ESD valve state.
   *
   * @return string describing valve state
   */
  @Override
  public String toString() {
    String status;
    if (!isEnergized) {
      status = hasTripCompleted ? "TRIPPED (CLOSED)" : "TRIPPING";
    } else if (partialStrokeTestActive) {
      status = "PARTIAL STROKE TEST";
    } else {
      status = "ENERGIZED";
    }

    return String.format("%s [ESD Valve] - Status: %s, Opening: %.1f%%, Stroke Time: %.1fs%s",
        getName(), status, getPercentValveOpening(), strokeTime,
        isClosing ? String.format(", Elapsed: %.1fs", timeElapsedSinceTrip) : "");
  }
}
