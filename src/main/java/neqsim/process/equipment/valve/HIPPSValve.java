package neqsim.process.equipment.valve;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import neqsim.process.alarm.AlarmLevel;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.measurementdevice.MeasurementDeviceInterface;

/**
 * High Integrity Pressure Protection System (HIPPS) Valve.
 * 
 * <p>
 * HIPPS is a Safety Instrumented System (SIS) designed to prevent overpressure by shutting down the
 * source of pressure rather than relieving it. It provides an alternative or complement to
 * traditional pressure relief devices (PSVs/rupture disks).
 * 
 * <p>
 * <b>Key Features:</b>
 * <ul>
 * <li>SIL-rated (Safety Integrity Level) protection, typically SIL 2 or SIL 3</li>
 * <li>Redundant pressure transmitters (voting logic: 1oo2, 2oo3, etc.)</li>
 * <li>Fast-acting isolation valve(s) with partial stroke testing capability</li>
 * <li>Closes on high pressure to prevent overpressure before PSV setpoint</li>
 * <li>Prevents loss of containment and flaring/venting</li>
 * <li>Diagnostic monitoring with proof testing support</li>
 * </ul>
 * 
 * <p>
 * <b>HIPPS vs. PSV:</b>
 * <table border="1">
 * <caption>Comparison between HIPPS and PSV protection systems</caption>
 * <tr>
 * <th>Aspect</th>
 * <th>HIPPS</th>
 * <th>PSV</th>
 * </tr>
 * <tr>
 * <td>Action</td>
 * <td>Stops flow (isolation)</td>
 * <td>Relieves pressure (venting)</td>
 * </tr>
 * <tr>
 * <td>Set Point</td>
 * <td>Below MAWP (e.g., 90%)</td>
 * <td>At/above MAWP</td>
 * </tr>
 * <tr>
 * <td>Emissions</td>
 * <td>Prevents flaring</td>
 * <td>Releases to flare</td>
 * </tr>
 * <tr>
 * <td>SIL Rating</td>
 * <td>SIL 2 or SIL 3</td>
 * <td>Mechanical (not SIL)</td>
 * </tr>
 * <tr>
 * <td>Testing</td>
 * <td>Partial stroke, diagnostics</td>
 * <td>Periodic inspection</td>
 * </tr>
 * <tr>
 * <td>Response</td>
 * <td>2-5 seconds typical</td>
 * <td>Instantaneous (spring)</td>
 * </tr>
 * </table>
 * 
 * <p>
 * <b>Typical Applications:</b>
 * <ul>
 * <li>Subsea pipelines (prevents overpressure at receiving platform)</li>
 * <li>Blocked outlet scenarios (pump/compressor deadhead protection)</li>
 * <li>Thermal expansion protection (isolated segments)</li>
 * <li>High-pressure gas injection systems</li>
 * <li>Where flaring is environmentally/economically undesirable</li>
 * </ul>
 * 
 * <p>
 * <b>Voting Logic:</b>
 * <ul>
 * <li><b>1oo1</b> (1 out of 1): Single transmitter trips (simple, lower SIL)</li>
 * <li><b>1oo2</b> (1 out of 2): Any one of two transmitters trips (high availability)</li>
 * <li><b>2oo2</b> (2 out of 2): Both transmitters must trip (low spurious trips)</li>
 * <li><b>2oo3</b> (2 out of 3): Any two of three transmitters trip (balanced, common for SIL
 * 3)</li>
 * </ul>
 * 
 * <p>
 * <b>Usage Example:</b>
 * 
 * <pre>
 * // Create redundant pressure transmitters with alarm configuration
 * PressureTransmitter PT1 = new PressureTransmitter("PT-101A", upstreamStream);
 * PressureTransmitter PT2 = new PressureTransmitter("PT-101B", upstreamStream);
 * PressureTransmitter PT3 = new PressureTransmitter("PT-101C", upstreamStream);
 * 
 * AlarmConfig alarmConfig = AlarmConfig.builder().highHighLimit(90.0) // HIPPS trip at 90 bara
 *                                                                     // (below 100 bara MAWP)
 *     .deadband(2.0).delay(0.5).unit("bara").build();
 * 
 * PT1.setAlarmConfig(alarmConfig);
 * PT2.setAlarmConfig(alarmConfig);
 * PT3.setAlarmConfig(alarmConfig);
 * 
 * // Create HIPPS valve with 2oo3 voting (SIL 3 typical)
 * HIPPSValve hippsValve = new HIPPSValve("HIPPS-XV-101", feedStream);
 * hippsValve.addPressureTransmitter(PT1);
 * hippsValve.addPressureTransmitter(PT2);
 * hippsValve.addPressureTransmitter(PT3);
 * hippsValve.setVotingLogic(HIPPSValve.VotingLogic.TWO_OUT_OF_THREE);
 * hippsValve.setClosureTime(3.0); // 3 seconds SIL-rated actuator
 * hippsValve.setSILRating(3); // SIL 3 system
 * 
 * // In dynamic simulation
 * system.runTransient(dt, UUID.randomUUID());
 * // Valve automatically closes when 2 out of 3 transmitters reach HIHI
 * 
 * // Check status
 * if (hippsValve.hasTripped()) {
 *   System.out.println("HIPPS activated - pressure source isolated");
 *   System.out.println("Active transmitters: " + hippsValve.getActiveTransmitterCount());
 * }
 * 
 * // Perform partial stroke test (required for SIL validation)
 * hippsValve.performPartialStrokeTest(0.15); // 15% stroke test
 * </pre>
 * 
 * <p>
 * <b>Safety Simulation Considerations:</b>
 * <ul>
 * <li>Model both successful operation and failure modes (spurious trip, fail to close)</li>
 * <li>Account for response time delay (typically 2-5 seconds)</li>
 * <li>Validate pressure never exceeds MAWP before HIPPS closes</li>
 * <li>Consider transmitter failure scenarios and voting logic</li>
 * <li>Model interaction with downstream PSV (HIPPS should prevent PSV from lifting)</li>
 * <li>Account for water hammer/surge when valve closes rapidly</li>
 * <li>Proof test intervals affect availability (typical: 1-2 years)</li>
 * </ul>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class HIPPSValve extends ThrottlingValve {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /** List of pressure transmitters providing redundancy. */
  private final List<MeasurementDeviceInterface> pressureTransmitters = new ArrayList<>();

  /** Voting logic for transmitter redundancy. */
  private VotingLogic votingLogic = VotingLogic.TWO_OUT_OF_THREE;

  /** Indicates if HIPPS has tripped. */
  private boolean hasTripped = false;

  /** Time required for valve to close (seconds). */
  private double closureTime = 3.0; // Typical SIL-rated actuator: 2-5 seconds

  /** Safety Integrity Level (1, 2, or 3). */
  private int silRating = 3; // SIL 3 typical for HIPPS

  /** Flag to enable/disable automatic trip. */
  private boolean tripEnabled = true;

  /** Timestamp of last trip event (for diagnostics). */
  private double lastTripTime = -1.0;

  /** Cumulative time in simulation (for diagnostics). */
  private double cumulativeTime = 0.0;

  /** Count of spurious trips (for diagnostics). */
  private int spuriousTripCount = 0;

  /** Flag indicating if partial stroke test is active. */
  private boolean partialStrokeTestActive = false;

  /** Target opening for partial stroke test. */
  private double partialStrokeTestTarget = 0.0;

  /** Time when partial stroke test started. */
  private double partialStrokeTestStartTime = 0.0;

  /** Duration of partial stroke test. */
  private double partialStrokeTestDuration = 5.0; // seconds

  /** Proof test interval (hours) - required for SIL validation. */
  private double proofTestInterval = 8760.0; // 1 year default

  /** Time since last proof test (hours). */
  private double timeSinceProofTest = 0.0;

  /**
   * Voting logic options for redundant pressure transmitters.
   */
  public enum VotingLogic {
    /** 1 out of 1: Single transmitter (simplest, used for SIL 1). */
    ONE_OUT_OF_ONE("1oo1"),
    /** 1 out of 2: Any one trips (high availability, some spurious trips). */
    ONE_OUT_OF_TWO("1oo2"),
    /** 2 out of 2: Both must trip (low spurious, lower availability). */
    TWO_OUT_OF_TWO("2oo2"),
    /** 2 out of 3: Any two trip (balanced, typical for SIL 2/3). */
    TWO_OUT_OF_THREE("2oo3"),
    /** 2 out of 4: Any two trip (higher availability). */
    TWO_OUT_OF_FOUR("2oo4");

    private final String notation;

    VotingLogic(String notation) {
      this.notation = notation;
    }

    public String getNotation() {
      return notation;
    }
  }

  /**
   * Constructor for HIPPSValve.
   *
   * @param name name of HIPPS valve
   */
  public HIPPSValve(String name) {
    super(name);
    // HIPPS valves start fully open
    setPercentValveOpening(100.0);
  }

  /**
   * Constructor for HIPPSValve.
   *
   * @param name name of HIPPS valve
   * @param inletStream inlet stream to valve
   */
  public HIPPSValve(String name, StreamInterface inletStream) {
    super(name, inletStream);
    // HIPPS valves start fully open
    setPercentValveOpening(100.0);
  }

  /**
   * Adds a pressure transmitter to the redundant array.
   *
   * @param transmitter pressure transmitter for HIPPS monitoring
   */
  public void addPressureTransmitter(MeasurementDeviceInterface transmitter) {
    if (transmitter != null && !pressureTransmitters.contains(transmitter)) {
      pressureTransmitters.add(transmitter);
    }
  }

  /**
   * Removes a pressure transmitter from the array (e.g., failed transmitter).
   *
   * @param transmitter pressure transmitter to remove
   */
  public void removePressureTransmitter(MeasurementDeviceInterface transmitter) {
    pressureTransmitters.remove(transmitter);
  }

  /**
   * Gets the list of configured pressure transmitters.
   *
   * @return list of pressure transmitters
   */
  public List<MeasurementDeviceInterface> getPressureTransmitters() {
    return new ArrayList<>(pressureTransmitters);
  }

  /**
   * Sets the voting logic for transmitter redundancy.
   *
   * @param logic voting logic to use
   */
  public void setVotingLogic(VotingLogic logic) {
    this.votingLogic = logic;
  }

  /**
   * Gets the current voting logic.
   *
   * @return voting logic
   */
  public VotingLogic getVotingLogic() {
    return votingLogic;
  }

  /**
   * Sets the valve closure time.
   *
   * @param closureTime time in seconds for valve to close completely
   */
  public void setClosureTime(double closureTime) {
    this.closureTime = Math.max(0.1, closureTime);
    // Use the valve closing travel time mechanism
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
   * Sets the Safety Integrity Level (SIL) rating.
   *
   * @param sil SIL rating (1, 2, or 3)
   */
  public void setSILRating(int sil) {
    if (sil >= 1 && sil <= 3) {
      this.silRating = sil;
    }
  }

  /**
   * Gets the SIL rating.
   *
   * @return SIL rating
   */
  public int getSILRating() {
    return silRating;
  }

  /**
   * Checks if HIPPS has tripped.
   *
   * @return true if HIPPS has tripped
   */
  public boolean hasTripped() {
    return hasTripped;
  }

  /**
   * Gets the time of last trip event.
   *
   * @return time in seconds, or -1 if never tripped
   */
  public double getLastTripTime() {
    return lastTripTime;
  }

  /**
   * Gets the number of spurious trips recorded.
   *
   * @return spurious trip count
   */
  public int getSpuriousTripCount() {
    return spuriousTripCount;
  }

  /**
   * Manually marks a trip as spurious (for diagnostics).
   */
  public void recordSpuriousTrip() {
    spuriousTripCount++;
  }

  /**
   * Resets the trip state, allowing valve to be reopened.
   * 
   * <p>
   * In real operations, this requires:
   * <ul>
   * <li>Operator authorization</li>
   * <li>Verification that alarm condition has cleared</li>
   * <li>Safety system approval</li>
   * <li>Documentation of trip cause</li>
   * </ul>
   * 
   * <p>
   * The valve will not automatically reopen after reset - it must be manually opened via
   * setPercentValveOpening().
   */
  public void reset() {
    hasTripped = false;
  }

  /**
   * Enables or disables automatic trip on HIHI alarm.
   *
   * @param enabled true to enable trip, false to disable (bypass)
   */
  public void setTripEnabled(boolean enabled) {
    this.tripEnabled = enabled;
  }

  /**
   * Checks if trip is enabled.
   *
   * @return true if trip is enabled
   */
  public boolean isTripEnabled() {
    return tripEnabled;
  }

  /**
   * Sets the proof test interval (required for SIL validation).
   *
   * @param hours hours between proof tests (typical: 8760 for annual)
   */
  public void setProofTestInterval(double hours) {
    this.proofTestInterval = Math.max(0.0, hours);
  }

  /**
   * Gets the proof test interval.
   *
   * @return proof test interval in hours
   */
  public double getProofTestInterval() {
    return proofTestInterval;
  }

  /**
   * Gets time since last proof test.
   *
   * @return hours since last proof test
   */
  public double getTimeSinceProofTest() {
    return timeSinceProofTest;
  }

  /**
   * Checks if proof test is due.
   *
   * @return true if proof test interval has been exceeded
   */
  public boolean isProofTestDue() {
    return timeSinceProofTest >= proofTestInterval;
  }

  /**
   * Performs a proof test (resets the timer).
   */
  public void performProofTest() {
    timeSinceProofTest = 0.0;
  }

  /**
   * Initiates a partial stroke test (required for SIL validation).
   * 
   * <p>
   * Partial stroke testing verifies valve operation without full closure, allowing testing during
   * operation. Typical test strokes: 10-20% of full travel.
   *
   * @param strokeFraction fraction of full stroke to test (0.0-0.9, e.g., 0.15 for 15%)
   */
  public void performPartialStrokeTest(double strokeFraction) {
    if (strokeFraction < 0.0 || strokeFraction >= 1.0) {
      throw new IllegalArgumentException("Stroke fraction must be between 0.0 and 1.0");
    }
    partialStrokeTestActive = true;
    partialStrokeTestTarget = 100.0 * (1.0 - strokeFraction); // e.g., 85% open for 15% stroke
    partialStrokeTestStartTime = cumulativeTime;
  }

  /**
   * Checks if partial stroke test is active.
   *
   * @return true if test is in progress
   */
  public boolean isPartialStrokeTestActive() {
    return partialStrokeTestActive;
  }

  /**
   * Counts how many transmitters are currently in HIHI alarm state.
   *
   * @return number of transmitters in HIHI alarm
   */
  public int getActiveTransmitterCount() {
    int count = 0;
    for (MeasurementDeviceInterface transmitter : pressureTransmitters) {
      if (transmitter.getAlarmState() != null && transmitter.getAlarmState().isActive()) {
        AlarmLevel activeLevel = transmitter.getAlarmState().getActiveLevel();
        if (activeLevel == AlarmLevel.HIHI) {
          count++;
        }
      }
    }
    return count;
  }

  /**
   * Evaluates voting logic to determine if HIPPS should trip.
   *
   * @return true if voting logic condition is met
   */
  private boolean evaluateVotingLogic() {
    int activeCount = getActiveTransmitterCount();
    int totalCount = pressureTransmitters.size();

    // Handle case where no transmitters configured
    if (totalCount == 0) {
      return false;
    }

    switch (votingLogic) {
      case ONE_OUT_OF_ONE:
        return totalCount >= 1 && activeCount >= 1;
      case ONE_OUT_OF_TWO:
        return totalCount >= 2 && activeCount >= 1;
      case TWO_OUT_OF_TWO:
        return totalCount >= 2 && activeCount >= 2;
      case TWO_OUT_OF_THREE:
        return totalCount >= 3 && activeCount >= 2;
      case TWO_OUT_OF_FOUR:
        return totalCount >= 4 && activeCount >= 2;
      default:
        return false;
    }
  }

  /**
   * Performs dynamic simulation step with HIPPS logic.
   * 
   * <p>
   * This method implements:
   * <ul>
   * <li>Redundant transmitter monitoring with voting logic</li>
   * <li>Automatic trip on voting condition</li>
   * <li>Partial stroke test execution</li>
   * <li>Proof test tracking</li>
   * <li>Trip state enforcement</li>
   * </ul>
   *
   * @param dt time step in seconds
   * @param id unique identifier for this calculation
   */
  @Override
  public void runTransient(double dt, UUID id) {
    // Update cumulative time for diagnostics
    cumulativeTime += dt;
    timeSinceProofTest += dt / 3600.0; // Convert to hours

    // Handle partial stroke test
    if (partialStrokeTestActive) {
      double testElapsedTime = cumulativeTime - partialStrokeTestStartTime;

      if (testElapsedTime < partialStrokeTestDuration / 2.0) {
        // First half: close to test position
        setPercentValveOpening(partialStrokeTestTarget);
      } else if (testElapsedTime < partialStrokeTestDuration) {
        // Second half: return to full open
        setPercentValveOpening(100.0);
      } else {
        // Test complete
        partialStrokeTestActive = false;
        setPercentValveOpening(100.0);
      }
    } else if (tripEnabled && !hasTripped) {
      // Normal operation: check voting logic for trip condition
      if (evaluateVotingLogic()) {
        // Trip condition met - initiate shutdown
        hasTripped = true;
        lastTripTime = cumulativeTime;
        // Command valve to close
        setPercentValveOpening(0.0);
      }
    }

    // If tripped, ensure valve stays closed
    if (hasTripped && !partialStrokeTestActive) {
      setPercentValveOpening(0.0);
    }

    // Run base class transient calculation
    super.runTransient(dt, id);
  }

  /**
   * Overrides setPercentValveOpening to prevent opening when tripped.
   * 
   * <p>
   * If HIPPS has tripped, it cannot be opened until reset() is called. This prevents inadvertent
   * reopening during an alarm condition.
   *
   * @param opening desired valve opening percentage (0-100)
   */
  @Override
  public void setPercentValveOpening(double opening) {
    if (hasTripped && opening > 0.0 && !partialStrokeTestActive) {
      // Cannot open valve while tripped (unless doing partial stroke test)
      super.setPercentValveOpening(0.0);
    } else {
      super.setPercentValveOpening(opening);
    }
  }

  /**
   * Gets a comprehensive status string for the HIPPS valve.
   *
   * @return string describing HIPPS state including diagnostics
   */
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(getName()).append(" [HIPPS Valve]\n");
    sb.append("  Opening: ").append(String.format("%.1f", getPercentValveOpening())).append("%\n");
    sb.append("  Status: ").append(hasTripped ? "TRIPPED" : "NORMAL").append("\n");
    sb.append("  Trip Enabled: ").append(tripEnabled ? "YES" : "NO (BYPASSED)").append("\n");
    sb.append("  SIL Rating: ").append(silRating).append("\n");
    sb.append("  Voting Logic: ").append(votingLogic.getNotation()).append("\n");
    sb.append("  Transmitters: ").append(getActiveTransmitterCount()).append(" active / ")
        .append(pressureTransmitters.size()).append(" total\n");
    sb.append("  Closure Time: ").append(String.format("%.1f", closureTime)).append(" s\n");
    sb.append("  Spurious Trips: ").append(spuriousTripCount).append("\n");
    sb.append("  Proof Test Due: ").append(isProofTestDue() ? "YES" : "NO").append(" (")
        .append(String.format("%.1f", timeSinceProofTest)).append("/")
        .append(String.format("%.1f", proofTestInterval)).append(" hrs)\n");
    if (partialStrokeTestActive) {
      sb.append("  PARTIAL STROKE TEST IN PROGRESS\n");
    }
    return sb.toString();
  }

  /**
   * Gets diagnostic information for safety analysis.
   *
   * @return diagnostic string with detailed HIPPS metrics
   */
  public String getDiagnostics() {
    StringBuilder sb = new StringBuilder();
    sb.append("=== HIPPS DIAGNOSTICS ===\n");
    sb.append("System: ").append(getName()).append("\n");
    sb.append("SIL Rating: SIL ").append(silRating).append("\n");
    sb.append("Configuration: ").append(votingLogic.getNotation()).append(" voting\n");
    sb.append("Closure Time: ").append(closureTime).append(" s\n");
    sb.append("\nTransmitter Status:\n");
    for (int i = 0; i < pressureTransmitters.size(); i++) {
      MeasurementDeviceInterface pt = pressureTransmitters.get(i);
      boolean inAlarm = pt.getAlarmState() != null && pt.getAlarmState().isActive()
          && pt.getAlarmState().getActiveLevel() == AlarmLevel.HIHI;
      sb.append("  PT-").append(i + 1).append(": ").append(inAlarm ? "ALARM" : "OK").append(" (")
          .append(String.format("%.2f", pt.getMeasuredValue("bara"))).append(" bara)\n");
    }
    sb.append("\nOperational History:\n");
    sb.append("  Total Trips: ").append(hasTripped ? 1 : 0).append("\n");
    sb.append("  Spurious Trips: ").append(spuriousTripCount).append("\n");
    sb.append("  Last Trip: ")
        .append(lastTripTime > 0 ? String.format("%.1f s", lastTripTime) : "Never").append("\n");
    sb.append("  Runtime: ").append(String.format("%.1f", cumulativeTime)).append(" s\n");
    sb.append("\nMaintenance:\n");
    sb.append("  Proof Test Interval: ").append(String.format("%.0f", proofTestInterval))
        .append(" hrs\n");
    sb.append("  Time Since Proof Test: ").append(String.format("%.1f", timeSinceProofTest))
        .append(" hrs\n");
    sb.append("  Status: ").append(isProofTestDue() ? "OVERDUE" : "OK").append("\n");
    return sb.toString();
  }
}
