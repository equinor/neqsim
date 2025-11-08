package neqsim.process.equipment.valve;

import neqsim.process.equipment.stream.StreamInterface;

/**
 * Level control valve (LCV) for automatic level regulation in vessels.
 * 
 * <p>
 * LevelControlValve automatically modulates its opening to maintain a vessel level setpoint. It
 * extends ControlValve and adds level control logic with proportional control action. This is
 * essential for separator level control and liquid discharge applications.
 * </p>
 * 
 * <p>
 * Key features:
 * <ul>
 * <li>Automatic level control</li>
 * <li>Configurable level setpoint (0-100%)</li>
 * <li>Proportional control action</li>
 * <li>Configurable control gain</li>
 * <li>Fail-open or fail-closed configurations</li>
 * </ul>
 * </p>
 * 
 * <p>
 * Control action:
 * <ul>
 * <li>DIRECT: Increase opening increases outflow, decreases level (most common for liquid
 * discharge)</li>
 * <li>REVERSE: Increase opening decreases outflow, increases level (rare)</li>
 * </ul>
 * </p>
 * 
 * <p>
 * Common applications:
 * <ul>
 * <li>Separator liquid level control</li>
 * <li>Vessel dump valves</li>
 * <li>Boot level control</li>
 * <li>Tank level regulation</li>
 * <li>Interface level control</li>
 * </ul>
 * </p>
 * 
 * <p>
 * Example usage:
 * 
 * <pre>
 * // Create separator with liquid outlet
 * Separator separator = new Separator("V-101", feedStream);
 * Stream liquidOut = new Stream("Liquid Out", separator.getLiquidOutStream());
 * 
 * // Create level control valve
 * LevelControlValve lcv = new LevelControlValve("LCV-101", liquidOut);
 * lcv.setLevelSetpoint(50.0); // Control to 50% level
 * lcv.setControllerGain(3.0); // Proportional gain
 * lcv.setMeasuredLevel(45.0); // Current level from transmitter
 * lcv.setCv(150.0);
 * lcv.run();
 * 
 * System.out.println("Valve opening: " + lcv.getPercentValveOpening() + "%");
 * System.out.println("Level error: " + lcv.getControlError() + "%");
 * </pre>
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class LevelControlValve extends ControlValve {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /** Control action enumeration. */
  public enum ControlAction {
    /**
     * Direct action: increase opening increases outflow, decreases level (normal for discharge
     * valves).
     */
    DIRECT,
    /** Reverse action: increase opening decreases outflow, increases level. */
    REVERSE
  }

  /** Level setpoint in % (0-100). */
  private double levelSetpoint = 50.0;

  /** Measured level in % (0-100). */
  private double measuredLevel = 50.0;

  /** Control action. */
  private ControlAction controlAction = ControlAction.DIRECT;

  /** Proportional controller gain. */
  private double controllerGain = 3.0;

  /** Control error (setpoint - measured level). */
  private double controlError = 0.0;

  /** Enable/disable automatic control. */
  private boolean autoMode = true;

  /** Fail-safe position on loss of instrument air or power (% opening). */
  private double failSafePosition = 0.0; // Default fail-closed

  /**
   * Constructor for LevelControlValve.
   *
   * @param name name of the level control valve
   */
  public LevelControlValve(String name) {
    super(name);
  }

  /**
   * Constructor for LevelControlValve.
   *
   * @param name name of the level control valve
   * @param inStream inlet stream
   */
  public LevelControlValve(String name, StreamInterface inStream) {
    super(name, inStream);
  }

  /**
   * Set the level setpoint.
   *
   * @param setpoint level setpoint in % (0-100)
   */
  public void setLevelSetpoint(double setpoint) {
    this.levelSetpoint = Math.max(0.0, Math.min(100.0, setpoint));
  }

  /**
   * Get the level setpoint.
   *
   * @return level setpoint in %
   */
  public double getLevelSetpoint() {
    return levelSetpoint;
  }

  /**
   * Set the measured level from level transmitter.
   *
   * @param level measured level in % (0-100)
   */
  public void setMeasuredLevel(double level) {
    this.measuredLevel = Math.max(0.0, Math.min(100.0, level));
  }

  /**
   * Get the measured level.
   *
   * @return measured level in %
   */
  public double getMeasuredLevel() {
    return measuredLevel;
  }

  /**
   * Set the control action.
   *
   * @param action control action (DIRECT or REVERSE)
   */
  public void setControlAction(ControlAction action) {
    this.controlAction = action;
  }

  /**
   * Get the control action.
   *
   * @return current control action
   */
  public ControlAction getControlAction() {
    return controlAction;
  }

  /**
   * Set the controller gain.
   *
   * @param gain proportional gain (typical range: 1.0 to 10.0)
   */
  public void setControllerGain(double gain) {
    this.controllerGain = gain;
  }

  /**
   * Get the controller gain.
   *
   * @return controller gain
   */
  public double getControllerGain() {
    return controllerGain;
  }

  /**
   * Get the control error.
   *
   * @return control error (setpoint - measured level) in %
   */
  public double getControlError() {
    return controlError;
  }

  /**
   * Enable or disable automatic control mode.
   *
   * @param auto true for automatic, false for manual
   */
  public void setAutoMode(boolean auto) {
    this.autoMode = auto;
  }

  /**
   * Check if valve is in automatic control mode.
   *
   * @return true if automatic, false if manual
   */
  public boolean isAutoMode() {
    return autoMode;
  }

  /**
   * Set the fail-safe position.
   *
   * @param position fail-safe position in % (0=fail-closed, 100=fail-open)
   */
  public void setFailSafePosition(double position) {
    this.failSafePosition = Math.max(0.0, Math.min(100.0, position));
  }

  /**
   * Get the fail-safe position.
   *
   * @return fail-safe position in %
   */
  public double getFailSafePosition() {
    return failSafePosition;
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Override run method to implement level control logic.
   * </p>
   */
  @Override
  public void run() {
    if (!autoMode) {
      // Manual mode - just run parent with current opening
      super.run();
      return;
    }

    // Calculate control error
    controlError = levelSetpoint - measuredLevel;

    // Proportional control action
    double currentOpening = getPercentValveOpening();
    double adjustment = controllerGain * controlError;

    // Apply adjustment based on control action
    double newOpening;
    if (controlAction == ControlAction.DIRECT) {
      // Direct action: high level -> open more to increase outflow
      newOpening = currentOpening + adjustment;
    } else {
      // Reverse action: high level -> close to decrease outflow
      newOpening = currentOpening - adjustment;
    }

    // Limit to 0-100%
    newOpening = Math.max(0.0, Math.min(100.0, newOpening));

    // Set new opening and run
    setPercentValveOpening(newOpening);
    super.run();
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Returns a string representation specific to level control valves.
   * </p>
   */
  @Override
  public String toString() {
    return String.format(
        "%s [Level Control Valve] - Mode: %s, Action: %s, SP: %.1f%%, PV: %.1f%%, Error: %.1f%%, Opening: %.1f%%",
        getName(), autoMode ? "AUTO" : "MANUAL", controlAction, levelSetpoint, measuredLevel,
        controlError, getPercentValveOpening());
  }
}
