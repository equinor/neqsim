package neqsim.process.equipment.valve;

import neqsim.process.equipment.stream.StreamInterface;

/**
 * Pressure control valve (PCV) for automatic pressure regulation.
 * 
 * <p>
 * PressureControlValve automatically modulates its opening to maintain a downstream pressure
 * setpoint. It extends ControlValve and adds pressure control logic with proportional control
 * action.
 * </p>
 * 
 * <p>
 * Key features:
 * <ul>
 * <li>Automatic pressure control</li>
 * <li>Configurable pressure setpoint</li>
 * <li>Proportional control action</li>
 * <li>Configurable control gain</li>
 * <li>Min/max opening limits</li>
 * </ul>
 * 
 * <p>
 * Control modes:
 * <ul>
 * <li>DOWNSTREAM: Maintains downstream (outlet) pressure at setpoint (most common)</li>
 * <li>UPSTREAM: Maintains upstream (inlet) pressure at setpoint (back-pressure control)</li>
 * <li>DIFFERENTIAL: Maintains pressure difference across valve</li>
 * </ul>
 * 
 * <p>
 * Common applications:
 * <ul>
 * <li>Gas pressure reduction stations</li>
 * <li>Compressor anti-surge recycle control</li>
 * <li>Separator pressure control</li>
 * <li>Back-pressure control on vessels</li>
 * <li>Steam pressure regulation</li>
 * </ul>
 * 
 * <p>
 * Example usage:
 * 
 * <pre>
 * PressureControlValve pcv = new PressureControlValve("PCV-101", inletStream);
 * pcv.setPressureSetpoint(25.0); // Control to 25 bara downstream
 * pcv.setControlMode(PressureControlValve.ControlMode.DOWNSTREAM);
 * pcv.setControllerGain(5.0); // Proportional gain
 * pcv.setCv(300.0);
 * pcv.run();
 * 
 * System.out.println("Valve opening: " + pcv.getPercentValveOpening() + "%");
 * System.out.println("Process variable: " + pcv.getProcessVariable() + " bara");
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class PressureControlValve extends ControlValve {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /** Control mode enumeration. */
  public enum ControlMode {
    /** Control downstream (outlet) pressure. */
    DOWNSTREAM,
    /** Control upstream (inlet) pressure (back-pressure control). */
    UPSTREAM,
    /** Control differential pressure across valve. */
    DIFFERENTIAL
  }

  /** Pressure setpoint in bara. */
  private double pressureSetpoint = 20.0;

  /** Control mode. */
  private ControlMode controlMode = ControlMode.DOWNSTREAM;

  /** Proportional controller gain. */
  private double controllerGain = 5.0;

  /** Current process variable (measured pressure) in bara. */
  private double processVariable = 0.0;

  /** Control error (setpoint - PV). */
  private double controlError = 0.0;

  /** Enable/disable automatic control. */
  private boolean autoMode = true;

  /**
   * Constructor for PressureControlValve.
   *
   * @param name name of the pressure control valve
   */
  public PressureControlValve(String name) {
    super(name);
  }

  /**
   * Constructor for PressureControlValve.
   *
   * @param name name of the pressure control valve
   * @param inStream inlet stream
   */
  public PressureControlValve(String name, StreamInterface inStream) {
    super(name, inStream);
  }

  /**
   * Set the pressure setpoint.
   *
   * @param setpoint pressure setpoint in bara
   */
  public void setPressureSetpoint(double setpoint) {
    this.pressureSetpoint = setpoint;
  }

  /**
   * Get the pressure setpoint.
   *
   * @return pressure setpoint in bara
   */
  public double getPressureSetpoint() {
    return pressureSetpoint;
  }

  /**
   * Set the control mode.
   *
   * @param mode control mode (DOWNSTREAM, UPSTREAM, or DIFFERENTIAL)
   */
  public void setControlMode(ControlMode mode) {
    this.controlMode = mode;
  }

  /**
   * Get the control mode.
   *
   * @return current control mode
   */
  public ControlMode getControlMode() {
    return controlMode;
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
   * Get the current process variable (measured pressure).
   *
   * @return process variable in bara
   */
  public double getProcessVariable() {
    return processVariable;
  }

  /**
   * Get the control error.
   *
   * @return control error (setpoint - PV) in bara
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
   * {@inheritDoc}
   *
   * <p>
   * Override run method to implement pressure control logic.
   * </p>
   */
  @Override
  public void run() {
    if (!autoMode) {
      // Manual mode - just run parent with current opening
      super.run();
      return;
    }

    // Get inlet and outlet pressures
    double inletPressure = getInletStream().getPressure("bara");
    double outletPressure = inletPressure; // Initial estimate

    // First run to get outlet pressure
    super.run();
    if (getOutletStream() != null) {
      outletPressure = getOutletStream().getPressure("bara");
    }

    // Determine process variable based on control mode
    switch (controlMode) {
      case DOWNSTREAM:
        processVariable = outletPressure;
        break;
      case UPSTREAM:
        processVariable = inletPressure;
        break;
      case DIFFERENTIAL:
        processVariable = inletPressure - outletPressure;
        break;
      default:
        processVariable = outletPressure;
    }

    // Calculate control error
    controlError = pressureSetpoint - processVariable;

    // Proportional control action
    double currentOpening = getPercentValveOpening();
    double adjustment = controllerGain * controlError;

    // Apply adjustment (reverse action for downstream control)
    double newOpening;
    if (controlMode == ControlMode.UPSTREAM) {
      // Direct action: increase opening to increase upstream pressure
      newOpening = currentOpening + adjustment;
    } else {
      // Reverse action: increase opening to decrease downstream pressure
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
   * Returns a string representation specific to pressure control valves.
   * </p>
   */
  @Override
  public String toString() {
    return String.format(
        "%s [Pressure Control Valve] - Mode: %s, SP: %.2f bara, PV: %.2f bara, Error: %.2f bara, Opening: %.1f%%",
        getName(), autoMode ? "AUTO" : "MANUAL", pressureSetpoint, processVariable, controlError,
        getPercentValveOpening());
  }
}
