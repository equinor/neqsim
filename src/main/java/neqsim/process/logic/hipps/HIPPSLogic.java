package neqsim.process.logic.hipps;

import java.util.ArrayList;
import java.util.List;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.logic.LogicState;
import neqsim.process.logic.ProcessLogic;
import neqsim.process.logic.sis.Detector;
import neqsim.process.logic.sis.VotingLogic;

/**
 * High Integrity Pressure Protection System (HIPPS) Logic.
 * 
 * <p>
 * HIPPS is a Safety Instrumented System (SIS) designed to prevent overpressure in process equipment
 * by rapidly closing isolation valves when pressure exceeds a safe limit. HIPPS acts as the first
 * line of defense before pressure relief devices or Emergency Shutdown (ESD) systems are activated.
 * 
 * <p>
 * Key features of HIPPS:
 * <ul>
 * <li>Rapid valve closure (typically &lt;2 seconds)</li>
 * <li>High reliability (SIL 2 or SIL 3 per IEC 61508/61511)</li>
 * <li>Redundant pressure sensors with voting logic</li>
 * <li>Prevents pressure relief valve (PSV) activation</li>
 * <li>Avoids flaring/venting to atmosphere</li>
 * <li>Lower operating costs than continuous PSV operation</li>
 * </ul>
 * 
 * <p>
 * HIPPS typically operates at ~95% of maximum allowable operating pressure (MAOP), while ESD
 * operates at ~98% MAOP as a backup.
 * 
 * <p>
 * Example usage:
 * 
 * <pre>
 * // Create HIPPS with 2oo3 voting for high reliability
 * HIPPSLogic hipps = new HIPPSLogic("HIPPS-101", VotingLogic.TWO_OUT_OF_THREE);
 * 
 * // Add pressure transmitters
 * hipps.addPressureSensor(
 *     new Detector("PT-101A", DetectorType.PRESSURE, AlarmLevel.HIGH_HIGH, 95.0, "bara"));
 * hipps.addPressureSensor(
 *     new Detector("PT-101B", DetectorType.PRESSURE, AlarmLevel.HIGH_HIGH, 95.0, "bara"));
 * hipps.addPressureSensor(
 *     new Detector("PT-101C", DetectorType.PRESSURE, AlarmLevel.HIGH_HIGH, 95.0, "bara"));
 * 
 * // Link isolation valve
 * hipps.setIsolationValve(isolationValve);
 * 
 * // Optionally link to ESD for escalation
 * hipps.linkToEscalationLogic(esdLogic, 5.0); // Escalate to ESD after 5 seconds
 * 
 * // In simulation loop:
 * hipps.update(pressure1, pressure2, pressure3);
 * if (hipps.isTripped()) {
 *   // HIPPS has activated
 * }
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class HIPPSLogic implements ProcessLogic {
  private final String name;
  private final VotingLogic votingLogic;
  private final List<Detector> pressureSensors = new ArrayList<>();

  private ThrottlingValve isolationValve;
  private ProcessLogic escalationLogic; // ESD logic to activate if pressure remains high
  private double escalationDelay = 5.0; // Time in seconds before escalating to ESD

  private LogicState state = LogicState.IDLE;
  private boolean isTripped = false;
  private boolean isOverridden = false;
  private double tripTime = 0.0;
  private double timeSinceTrip = 0.0;
  private boolean escalated = false;

  // Closure timing
  private double valveClosureTime = 2.0; // Target closure time in seconds (typical HIPPS)
  private int maxBypassedSensors = 1;

  /**
   * Creates a HIPPS logic instance.
   *
   * @param name HIPPS name/tag (e.g., "HIPPS-101")
   * @param votingLogic voting pattern for pressure sensors (typically 2oo3 for SIL 3)
   */
  public HIPPSLogic(String name, VotingLogic votingLogic) {
    this.name = name;
    this.votingLogic = votingLogic;
  }

  /**
   * Adds a pressure sensor to the HIPPS voting group.
   *
   * @param sensor pressure detector/transmitter
   * @throws IllegalStateException if too many sensors added
   */
  public void addPressureSensor(Detector sensor) {
    if (pressureSensors.size() >= votingLogic.getTotalSensors()) {
      throw new IllegalStateException("Cannot add more than " + votingLogic.getTotalSensors()
          + " sensors for " + votingLogic.getNotation() + " voting");
    }
    pressureSensors.add(sensor);
  }

  /**
   * Sets the isolation valve that HIPPS will close on trip.
   *
   * @param valve isolation valve (typically full-bore ball valve)
   */
  public void setIsolationValve(ThrottlingValve valve) {
    this.isolationValve = valve;
  }

  /**
   * Links HIPPS to an escalation logic (typically ESD) that activates if HIPPS fails to control
   * pressure.
   *
   * @param escalationLogic ESD or other backup logic
   * @param delay time in seconds before escalating
   */
  public void linkToEscalationLogic(ProcessLogic escalationLogic, double delay) {
    this.escalationLogic = escalationLogic;
    this.escalationDelay = delay;
  }

  /**
   * Sets the target valve closure time.
   *
   * @param closureTime time in seconds (typically 1-2 seconds for HIPPS)
   */
  public void setValveClosureTime(double closureTime) {
    this.valveClosureTime = closureTime;
  }

  /**
   * Updates all pressure sensors and evaluates voting logic.
   *
   * @param pressureValues array of pressure values from each sensor
   * @throws IllegalArgumentException if array size doesn't match sensor count
   */
  public void update(double... pressureValues) {
    if (pressureValues.length != pressureSensors.size()) {
      throw new IllegalArgumentException("Number of pressure values (" + pressureValues.length
          + ") doesn't match number of sensors (" + pressureSensors.size() + ")");
    }

    // Update each sensor
    for (int i = 0; i < pressureSensors.size(); i++) {
      pressureSensors.get(i).update(pressureValues[i]);
    }

    // Evaluate voting logic
    evaluateVoting();
  }

  /**
   * Evaluates voting logic and determines if HIPPS should trip.
   */
  private void evaluateVoting() {
    if (isOverridden) {
      return;
    }

    // Check bypass constraint
    int bypassedCount = 0;
    int faultyCount = 0;
    for (Detector sensor : pressureSensors) {
      if (sensor.isBypassed()) {
        bypassedCount++;
      }
      if (sensor.isFaulty()) {
        faultyCount++;
      }
    }

    if (bypassedCount > maxBypassedSensors) {
      state = LogicState.FAILED;
      return;
    }

    // Count tripped sensors (excluding bypassed and faulty)
    int trippedCount = 0;
    for (Detector sensor : pressureSensors) {
      if (sensor.isTripped()) {
        trippedCount++;
      }
    }

    // Evaluate voting condition
    boolean shouldTrip = votingLogic.evaluate(trippedCount);

    if (shouldTrip && !isTripped) {
      // HIPPS trips - close isolation valve immediately
      isTripped = true;
      state = LogicState.RUNNING;

      if (isolationValve != null) {
        // Rapid closure for HIPPS
        isolationValve.setPercentValveOpening(0.0);
      }
    }
  }

  /**
   * Executes HIPPS logic over a time step, including escalation if needed.
   *
   * @param timeStep time step in seconds
   */
  @Override
  public void execute(double timeStep) {
    if (!isTripped || isOverridden) {
      return;
    }

    timeSinceTrip += timeStep;

    // Check if escalation to ESD is needed
    if (!escalated && escalationLogic != null && timeSinceTrip >= escalationDelay) {
      // Check if pressure is still high (voting still true)
      int trippedCount = 0;
      for (Detector sensor : pressureSensors) {
        if (sensor.isTripped()) {
          trippedCount++;
        }
      }

      if (votingLogic.evaluate(trippedCount)) {
        // Pressure still high after delay - escalate to ESD
        escalated = true;
        escalationLogic.activate();
      }
    }
  }

  /**
   * Manually overrides HIPPS (inhibits trip function).
   *
   * @param override true to override
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
   * Resets HIPPS after trip conditions have cleared.
   *
   * @return true if reset successful
   */
  @Override
  public boolean reset() {
    // Check if all sensors are clear
    for (Detector sensor : pressureSensors) {
      if (sensor.isTripped()) {
        return false;
      }
    }

    isTripped = false;
    timeSinceTrip = 0.0;
    escalated = false;
    state = LogicState.IDLE;

    // Re-open isolation valve (requires manual action in real system)
    if (isolationValve != null) {
      isolationValve.setPercentValveOpening(100.0);
    }

    return true;
  }

  /**
   * Gets a specific pressure sensor.
   *
   * @param index sensor index
   * @return pressure sensor
   */
  public Detector getPressureSensor(int index) {
    return pressureSensors.get(index);
  }

  /**
   * Checks if HIPPS has tripped.
   *
   * @return true if tripped
   */
  public boolean isTripped() {
    return isTripped;
  }

  /**
   * Checks if HIPPS has escalated to ESD.
   *
   * @return true if escalated
   */
  public boolean hasEscalated() {
    return escalated;
  }

  /**
   * Gets time since trip.
   *
   * @return time in seconds
   */
  public double getTimeSinceTrip() {
    return timeSinceTrip;
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
      state = LogicState.RUNNING;

      if (isolationValve != null) {
        isolationValve.setPercentValveOpening(0.0);
      }
    }
  }

  @Override
  public void deactivate() {
    // HIPPS doesn't support deactivation - requires reset
  }

  @Override
  public boolean isActive() {
    return isTripped && !isOverridden;
  }

  @Override
  public boolean isComplete() {
    return false; // HIPPS never "completes" - always monitoring
  }

  @Override
  public List<ProcessEquipmentInterface> getTargetEquipment() {
    List<ProcessEquipmentInterface> equipment = new ArrayList<>();
    if (isolationValve != null) {
      equipment.add(isolationValve);
    }
    return equipment;
  }

  @Override
  public String getStatusDescription() {
    StringBuilder sb = new StringBuilder();
    sb.append(name).append(" [").append(votingLogic.getNotation()).append("] - ");

    if (isOverridden) {
      sb.append("OVERRIDDEN");
    } else if (escalated) {
      sb.append("ESCALATED TO ESD");
    } else if (isTripped) {
      sb.append(String.format("TRIPPED (%.1fs)", timeSinceTrip));
    } else {
      sb.append("NORMAL");
    }

    int trippedCount = 0;
    int bypassedCount = 0;
    for (Detector sensor : pressureSensors) {
      if (sensor.isTripped()) {
        trippedCount++;
      }
      if (sensor.isBypassed()) {
        bypassedCount++;
      }
    }

    sb.append(" (").append(trippedCount).append("/").append(pressureSensors.size())
        .append(" tripped");
    if (bypassedCount > 0) {
      sb.append(", ").append(bypassedCount).append(" bypassed");
    }
    sb.append(")");

    if (isolationValve != null) {
      sb.append(String.format(" - Valve: %.0f%%", isolationValve.getPercentValveOpening()));
    }

    return sb.toString();
  }

  @Override
  public String toString() {
    return getStatusDescription();
  }
}
