package neqsim.process.logic.hipps;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.List;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.equipment.valve.HIPPSValve;
import neqsim.process.equipment.valve.ValveTravelModel;
import neqsim.process.logic.LogicState;
import neqsim.process.logic.ProcessLogic;
import neqsim.process.logic.sis.Detector;
import neqsim.process.logic.sis.VotingLogic;

/**
 * High Integrity Pressure Protection System (HIPPS) Logic.
 *
 * <p>
 * The logic evaluates independent current-scan pressure values, latches an isolation command, and can escalate a
 * persistent pressure demand to a separate shutdown sequence. Configure the pressure limits, actuator times, and
 * degraded-voting response from the specific study basis.
 *
 * <p>
 * Example usage:
 *
 * <pre>
 * // Create HIPPS with 2oo3 voting for high reliability
 * HIPPSLogic hipps = new HIPPSLogic("HIPPS-101", VotingLogic.TWO_OUT_OF_THREE);
 *
 * // Add pressure transmitters
 * hipps.addPressureSensor(new Detector("PT-101A", DetectorType.PRESSURE, AlarmLevel.HIGH_HIGH, 95.0, "bara"));
 * hipps.addPressureSensor(new Detector("PT-101B", DetectorType.PRESSURE, AlarmLevel.HIGH_HIGH, 95.0, "bara"));
 * hipps.addPressureSensor(new Detector("PT-101C", DetectorType.PRESSURE, AlarmLevel.HIGH_HIGH, 95.0, "bara"));
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
  private static final long serialVersionUID = 1L;
  private final String name;
  private final VotingLogic votingLogic;
  private List<Detector> pressureSensors = new ArrayList<>();

  private List<ThrottlingValve> isolationValves = new ArrayList<>();
  /** Primary valve retained for streams written by the single-valve implementation. */
  private ThrottlingValve isolationValve;
  private boolean tripOnLostVotingCapacity;
  private double continuousDemandSeconds;
  private ProcessLogic escalationLogic; // ESD logic to activate if pressure remains high
  private double escalationDelay = 5.0; // Time in seconds before escalating to ESD

  private LogicState state = LogicState.IDLE;
  private boolean isTripped = false;
  private boolean isOverridden = false;
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
   * Restores the legacy single valve. Old streams omitted the detector list and must be rewired.
   *
   * @param input stored HIPPS logic
   * @throws IOException if the serialized data cannot be read
   * @throws ClassNotFoundException if a stored equipment class is unavailable
   */
  private void readObject(ObjectInputStream input) throws IOException, ClassNotFoundException {
    input.defaultReadObject();
    if (pressureSensors == null) {
      pressureSensors = new ArrayList<>();
    }
    if (isolationValves == null) {
      isolationValves = new ArrayList<>();
      if (isolationValve != null) {
        isolationValves.add(isolationValve);
        isolationValve = null;
      }
    }
  }

  /**
   * Adds a pressure sensor to the HIPPS voting group.
   *
   * @param sensor pressure detector/transmitter
   * @throws IllegalStateException if too many sensors added
   */
  public void addPressureSensor(Detector sensor) {
    if (pressureSensors.size() >= votingLogic.getTotalSensors()) {
      throw new IllegalStateException("Cannot add more than " + votingLogic.getTotalSensors() + " sensors for "
          + votingLogic.getNotation() + " voting");
    }
    if (sensor == null || pressureSensors.contains(sensor)) {
      throw new IllegalArgumentException("Each vote requires a distinct detector");
    }
    pressureSensors.add(sensor);
  }

  /**
   * Sets the isolation valve that HIPPS will close on trip.
   *
   * @param valve isolation valve (typically full-bore ball valve)
   */
  public void setIsolationValve(ThrottlingValve valve) {
    isolationValves.clear();
    if (valve != null) {
      addIsolationValve(valve);
    }
  }

  /**
   * Adds another final element driven by the same trip. Connect its material ports in the process model.
   *
   * @param valve additional modeled isolation valve
   */
  public void addIsolationValve(ThrottlingValve valve) {
    if (valve == null || isolationValves.contains(valve)) {
      throw new IllegalArgumentException("Each final element must be distinct");
    }
    isolationValves.add(valve);
    configureStroke(valve);
    if (isTripped) {
      commandIsolation(valve);
    }
  }

  /**
   * Chooses whether loss of the minimum number of valid votes itself demands isolation.
   *
   * @param enabled true to trip when the configured voting threshold cannot be attained
   */
  public void setTripOnLostVotingCapacity(boolean enabled) {
    tripOnLostVotingCapacity = enabled;
  }

  /**
   * Links HIPPS to an escalation logic (typically ESD) that activates if HIPPS fails to control pressure.
   *
   * @param escalationLogic ESD or other backup logic
   * @param delay time in seconds before escalating
   */
  public void linkToEscalationLogic(ProcessLogic escalationLogic, double delay) {
    if (!Double.isFinite(delay) || delay < 0.0) {
      throw new IllegalArgumentException("Escalation delay must be finite and nonnegative");
    }
    this.escalationLogic = escalationLogic;
    this.escalationDelay = delay;
  }

  /**
   * Sets the target valve closure time.
   *
   * @param closureTime time in seconds (typically 1-2 seconds for HIPPS)
   */
  public void setValveClosureTime(double closureTime) {
    if (!Double.isFinite(closureTime) || closureTime <= 0.0) {
      throw new IllegalArgumentException("Closure time must be finite and positive");
    }
    this.valveClosureTime = closureTime;
    for (ThrottlingValve valve : isolationValves) {
      configureStroke(valve);
    }
  }

  /**
   * Assigns the nominal full-stroke duration without replacing a configured actuator model.
   *
   * @param valve linked final element
   */
  private void configureStroke(ThrottlingValve valve) {
    if (valve instanceof HIPPSValve) {
      ((HIPPSValve) valve).setClosureTime(valveClosureTime);
    } else {
      valve.setClosingTravelTime(valveClosureTime);
      if (valve.getTravelModel() == ValveTravelModel.NONE) {
        valve.setTravelModel(ValveTravelModel.LINEAR_RATE_LIMIT);
      }
    }
  }

  /**
   * Issues a latch to a HIPPS valve or a closure command to a generic valve.
   *
   * @param valve linked final element
   */
  private void commandIsolation(ThrottlingValve valve) {
    if (valve instanceof HIPPSValve) {
      ((HIPPSValve) valve).trip();
    } else if (valve.getCalculateSteadyState()) {
      valve.setPercentValveOpening(0.0);
    } else {
      valve.setTargetPercentValveOpening(0.0);
    }
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

    int available = 0;
    for (Detector sensor : pressureSensors) {
      if (!sensor.isBypassed() && !sensor.isFaulty() && Double.isFinite(sensor.getMeasuredValue())) {
        available++;
      }
    }
    boolean insufficient = available < votingLogic.getRequiredTrips();
    boolean degraded = votingLogic.getTotalSensors() - available > maxBypassedSensors || insufficient;
    boolean liveDemand = hasLiveDemand();
    if (!liveDemand) {
      continuousDemandSeconds = 0.0;
    }
    if (!isTripped && (liveDemand || (insufficient && tripOnLostVotingCapacity))) {
      activate();
    }
    state = degraded ? LogicState.FAILED : (isTripped ? LogicState.RUNNING : LogicState.IDLE);
  }

  /**
   * Evaluates simultaneous channel pressure measurements independently of their alarm history.
   *
   * @return true when the current available samples meet the configured vote
   */
  private boolean hasLiveDemand() {
    int votes = 0;
    for (Detector sensor : pressureSensors) {
      if (sensor.isDemandPresent()) {
        votes++;
      }
    }
    return votingLogic.evaluate(votes);
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

    if (!Double.isFinite(timeStep) || timeStep < 0.0) {
      throw new IllegalArgumentException("Timestep must be finite and nonnegative");
    }
    timeSinceTrip += timeStep;
    boolean demand = hasLiveDemand();
    continuousDemandSeconds = demand ? continuousDemandSeconds + timeStep : 0.0;
    if (demand && !escalated && escalationLogic != null && continuousDemandSeconds >= escalationDelay) {
      escalated = true;
      escalationLogic.activate();
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
    if (isOverridden || pressureSensors.size() != votingLogic.getTotalSensors()) {
      return false;
    }
    for (Detector sensor : pressureSensors) {
      if (sensor.isDemandPresent() || sensor.isFaulty() || sensor.isBypassed()
          || !Double.isFinite(sensor.getMeasuredValue())) {
        return false;
      }
    }
    for (ThrottlingValve valve : isolationValves) {
      if (valve instanceof HIPPSValve && ((HIPPSValve) valve).getActiveTransmitterCount() > 0) {
        return false;
      }
    }
    for (Detector sensor : pressureSensors) {
      sensor.reset();
    }
    for (ThrottlingValve valve : isolationValves) {
      if (valve instanceof HIPPSValve) {
        ((HIPPSValve) valve).reset();
      }
    }
    isTripped = false;
    timeSinceTrip = 0.0;
    continuousDemandSeconds = 0.0;
    escalated = false;
    state = LogicState.IDLE;
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

      for (ThrottlingValve valve : isolationValves) {
        commandIsolation(valve);
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
    return new ArrayList<ProcessEquipmentInterface>(isolationValves);
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
      sb.append(state == LogicState.FAILED ? "FAILED" : "NORMAL");
    }

    if (state == LogicState.FAILED) {
      sb.append(" [DEGRADED VOTING]");
    }
    int trippedCount = 0;
    int bypassedCount = 0;
    for (Detector sensor : pressureSensors) {
      if (sensor.isDemandPresent()) {
        trippedCount++;
      }
      if (sensor.isBypassed()) {
        bypassedCount++;
      }
    }

    sb.append(" (").append(trippedCount).append("/").append(pressureSensors.size()).append(" tripped");
    if (bypassedCount > 0) {
      sb.append(", ").append(bypassedCount).append(" bypassed");
    }
    sb.append(")");

    for (ThrottlingValve valve : isolationValves) {
      sb.append(String.format(" - %s: %.0f%%", valve.getName(), valve.getPercentValveOpening()));
    }

    return sb.toString();
  }

  @Override
  public String toString() {
    return getStatusDescription();
  }
}
