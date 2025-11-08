package neqsim.process.logic.sis;

import java.util.ArrayList;
import java.util.List;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.logic.LogicState;
import neqsim.process.logic.ProcessLogic;

/**
 * Safety Instrumented Function (SIF) implementing fire and gas detection with voting logic.
 * 
 * <p>
 * A SIF is a safety function designed to prevent or mitigate hazardous events. It consists of:
 * <ul>
 * <li>Sensors/detectors (input elements)</li>
 * <li>Logic solver (voting and logic)</li>
 * <li>Final elements (valves, alarms, etc.)</li>
 * </ul>
 * 
 * <p>
 * This implementation follows IEC 61511 principles:
 * <ul>
 * <li>Voting logic (1oo1, 1oo2, 2oo3, etc.)</li>
 * <li>Bypass management (max 1 bypassed detector)</li>
 * <li>Fault detection and alarming</li>
 * <li>Manual override capability</li>
 * <li>Reset permissives</li>
 * </ul>
 * 
 * <p>
 * Example usage:
 * 
 * <pre>
 * // Create fire detection SIF with 2oo3 voting
 * SafetyInstrumentedFunction fireSIF =
 *     new SafetyInstrumentedFunction("Fire Detection SIF", VotingLogic.TWO_OUT_OF_THREE);
 * 
 * // Add detectors
 * fireSIF.addDetector(new Detector("FD-101", DetectorType.FIRE, AlarmLevel.HIGH, 60.0, "°C"));
 * fireSIF.addDetector(new Detector("FD-102", DetectorType.FIRE, AlarmLevel.HIGH, 60.0, "°C"));
 * fireSIF.addDetector(new Detector("FD-103", DetectorType.FIRE, AlarmLevel.HIGH, 60.0, "°C"));
 * 
 * // Link to ESD logic
 * fireSIF.linkToLogic(esdLogic);
 * 
 * // In simulation loop:
 * fireSIF.update(temp1, temp2, temp3); // Update detector values
 * if (fireSIF.isTripped()) {
 *   // SIF has activated
 * }
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class SafetyInstrumentedFunction implements ProcessLogic {
  private final String name;
  private final VotingLogic votingLogic;
  private final List<Detector> detectors = new ArrayList<>();
  private final List<ProcessLogic> linkedLogics = new ArrayList<>();

  private LogicState state = LogicState.IDLE;
  private boolean isTripped = false;
  private boolean isOverridden = false;
  private long tripTime = 0;
  private int maxBypassedDetectors = 1; // Maximum detectors that can be bypassed simultaneously

  /**
   * Creates a Safety Instrumented Function.
   *
   * @param name SIF name/tag
   * @param votingLogic voting pattern for detectors
   */
  public SafetyInstrumentedFunction(String name, VotingLogic votingLogic) {
    this.name = name;
    this.votingLogic = votingLogic;
  }

  /**
   * Adds a detector to this SIF.
   * 
   * <p>
   * The number of detectors added must match the voting logic total sensors requirement.
   * </p>
   *
   * @param detector detector to add
   * @throws IllegalStateException if too many detectors are added
   */
  public void addDetector(Detector detector) {
    if (detectors.size() >= votingLogic.getTotalSensors()) {
      throw new IllegalStateException("Cannot add more than " + votingLogic.getTotalSensors()
          + " detectors for " + votingLogic.getNotation() + " voting");
    }
    detectors.add(detector);
  }

  /**
   * Links this SIF to a process logic sequence that will be activated when SIF trips.
   *
   * @param logic process logic to activate
   */
  public void linkToLogic(ProcessLogic logic) {
    if (!linkedLogics.contains(logic)) {
      linkedLogics.add(logic);
    }
  }

  /**
   * Updates all detectors with new measured values and evaluates voting logic.
   *
   * @param measuredValues array of values corresponding to each detector
   * @throws IllegalArgumentException if array size doesn't match detector count
   */
  public void update(double... measuredValues) {
    if (measuredValues.length != detectors.size()) {
      throw new IllegalArgumentException("Number of values (" + measuredValues.length
          + ") doesn't match number of detectors (" + detectors.size() + ")");
    }

    // Update each detector
    for (int i = 0; i < detectors.size(); i++) {
      detectors.get(i).update(measuredValues[i]);
    }

    // Evaluate voting logic
    evaluateVoting();
  }

  /**
   * Evaluates the voting logic and determines if SIF should trip.
   */
  private void evaluateVoting() {
    if (isOverridden) {
      return; // Don't evaluate if manually overridden
    }

    // Check bypass constraint
    int bypassedCount = 0;
    int faultyCount = 0;
    for (Detector detector : detectors) {
      if (detector.isBypassed()) {
        bypassedCount++;
      }
      if (detector.isFaulty()) {
        faultyCount++;
      }
    }

    if (bypassedCount > maxBypassedDetectors) {
      // Too many bypassed - should alarm/inhibit operation
      state = LogicState.FAILED;
      return;
    }

    // Count tripped detectors (excluding bypassed and faulty)
    int trippedCount = 0;
    for (Detector detector : detectors) {
      if (detector.isTripped()) {
        trippedCount++;
      }
    }

    // Evaluate voting condition
    boolean shouldTrip = votingLogic.evaluate(trippedCount);

    if (shouldTrip && !isTripped) {
      // SIF trips
      isTripped = true;
      tripTime = System.currentTimeMillis();
      state = LogicState.RUNNING;

      // Activate all linked logic sequences
      for (ProcessLogic logic : linkedLogics) {
        logic.activate();
      }
    }
  }

  /**
   * Manually overrides the SIF (for testing or bypass).
   * 
   * <p>
   * Override should be used with extreme caution and typically requires management approval.
   * </p>
   *
   * @param override true to override (inhibit), false to restore
   */
  public void setOverride(boolean override) {
    this.isOverridden = override;
    if (override) {
      state = LogicState.PAUSED;
    } else {
      state = isTripped ? LogicState.RUNNING : LogicState.IDLE;
    }
  }

  /**
   * Resets the SIF after trip conditions have cleared.
   * 
   * <p>
   * Reset requires all detectors to be in non-trip condition.
   * </p>
   *
   * @return true if reset successful, false if conditions not met
   */
  @Override
  public boolean reset() {
    // Check if all detectors are clear
    for (Detector detector : detectors) {
      if (detector.isTripped()) {
        return false; // Cannot reset while detectors still tripped
      }
    }

    isTripped = false;
    tripTime = 0;
    state = LogicState.IDLE;

    // Reset linked logic sequences
    for (ProcessLogic logic : linkedLogics) {
      logic.reset();
    }

    return true;
  }

  /**
   * Gets a specific detector by index.
   *
   * @param index detector index (0-based)
   * @return detector at index
   */
  public Detector getDetector(int index) {
    return detectors.get(index);
  }

  /**
   * Gets all detectors.
   *
   * @return list of detectors (unmodifiable)
   */
  public List<Detector> getDetectors() {
    return new ArrayList<>(detectors);
  }

  /**
   * Checks if SIF is tripped.
   *
   * @return true if tripped
   */
  public boolean isTripped() {
    return isTripped;
  }

  /**
   * Checks if SIF is overridden.
   *
   * @return true if overridden
   */
  public boolean isOverridden() {
    return isOverridden;
  }

  /**
   * Gets the voting logic pattern.
   *
   * @return voting logic
   */
  public VotingLogic getVotingLogic() {
    return votingLogic;
  }

  /**
   * Sets the maximum number of detectors that can be bypassed simultaneously.
   *
   * @param max maximum bypass count (typically 1)
   */
  public void setMaxBypassedDetectors(int max) {
    this.maxBypassedDetectors = max;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public LogicState getState() {
    return state;
  }

  @Override
  public void activate() {
    if (!isOverridden) {
      isTripped = true;
      tripTime = System.currentTimeMillis();
      state = LogicState.RUNNING;

      for (ProcessLogic logic : linkedLogics) {
        logic.activate();
      }
    }
  }

  @Override
  public void deactivate() {
    // SIF doesn't support deactivation - requires reset after trip
  }

  @Override
  public void execute(double timeStep) {
    // SIF execution is event-driven (update() method), not time-step based
    // This method exists for ProcessLogic interface compatibility
  }

  @Override
  public boolean isActive() {
    return isTripped && !isOverridden;
  }

  @Override
  public boolean isComplete() {
    return false; // SIF never "completes" - it's always monitoring
  }

  @Override
  public List<ProcessEquipmentInterface> getTargetEquipment() {
    return new ArrayList<>(); // SIF targets are in linked logic sequences
  }

  @Override
  public String getStatusDescription() {
    StringBuilder sb = new StringBuilder();
    sb.append(name).append(" [").append(votingLogic.getNotation()).append("] - ");

    if (isOverridden) {
      sb.append("OVERRIDDEN");
    } else if (isTripped) {
      sb.append("TRIPPED");
    } else {
      sb.append("NORMAL");
    }

    int trippedCount = 0;
    int bypassedCount = 0;
    int faultyCount = 0;
    for (Detector detector : detectors) {
      if (detector.isTripped()) {
        trippedCount++;
      }
      if (detector.isBypassed()) {
        bypassedCount++;
      }
      if (detector.isFaulty()) {
        faultyCount++;
      }
    }

    sb.append(" (").append(trippedCount).append("/").append(detectors.size()).append(" tripped");
    if (bypassedCount > 0) {
      sb.append(", ").append(bypassedCount).append(" bypassed");
    }
    if (faultyCount > 0) {
      sb.append(", ").append(faultyCount).append(" faulty");
    }
    sb.append(")");

    return sb.toString();
  }

  @Override
  public String toString() {
    return getStatusDescription();
  }
}
