package neqsim.process.operations;

import java.io.Serializable;

/**
 * Describes one plant-agnostic operation to apply to a NeqSim process model.
 *
 * <p>
 * Actions are intentionally small and map to existing NeqSim capabilities: automation writes,
 * valve-opening logic actions, steady-state runs, field-input application, and transient steps.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class OperationalAction implements Serializable {
  private static final long serialVersionUID = 1L;

  /** Supported operational action types. */
  public enum ActionType {
    /** Write a value through ProcessAutomation. */
    SET_VARIABLE,
    /** Set a throttling valve opening percentage. */
    SET_VALVE_OPENING,
    /** Apply field-input measurement values to the process. */
    APPLY_FIELD_INPUTS,
    /** Run a steady-state process calculation. */
    RUN_STEADY_STATE,
    /** Run one or more transient process time steps. */
    RUN_TRANSIENT
  }

  private final ActionType type;
  private final String target;
  private final double value;
  private final String unit;
  private final double durationSeconds;
  private final double timeStepSeconds;
  private final String description;

  /**
   * Creates an action.
   *
   * @param type action type
   * @param target action target such as an automation address or valve name
   * @param value action value
   * @param unit engineering unit for the value
   * @param durationSeconds transient duration in seconds
   * @param timeStepSeconds transient time step in seconds
   * @param description human-readable description
   */
  private OperationalAction(ActionType type, String target, double value, String unit,
      double durationSeconds, double timeStepSeconds, String description) {
    if (type == null) {
      throw new IllegalArgumentException("type must not be null");
    }
    this.type = type;
    this.target = clean(target);
    this.value = value;
    this.unit = clean(unit);
    this.durationSeconds = durationSeconds;
    this.timeStepSeconds = timeStepSeconds;
    this.description = clean(description);
  }

  /**
   * Creates an action that writes an automation variable.
   *
   * @param address automation address
   * @param value value to set
   * @param unit engineering unit
   * @return operational action
   */
  public static OperationalAction setVariable(String address, double value, String unit) {
    return new OperationalAction(ActionType.SET_VARIABLE, address, value, unit, 0.0, 0.0,
        "Set " + address + " to " + value + " " + clean(unit));
  }

  /**
   * Creates an action that sets a throttling valve opening percentage.
   *
   * @param valveName valve unit name
   * @param percentOpen target opening in percent from 0 to 100
   * @return operational action
   */
  public static OperationalAction setValveOpening(String valveName, double percentOpen) {
    return new OperationalAction(ActionType.SET_VALVE_OPENING, valveName, percentOpen, "%", 0.0,
        0.0, "Set valve " + clean(valveName) + " opening to " + percentOpen + "%");
  }

  /**
   * Creates an action that applies field input devices to the model.
   *
   * @return operational action
   */
  public static OperationalAction applyFieldInputs() {
    return new OperationalAction(ActionType.APPLY_FIELD_INPUTS, "", Double.NaN, "", 0.0, 0.0,
        "Apply field input measurements");
  }

  /**
   * Creates an action that runs a steady-state calculation.
   *
   * @return operational action
   */
  public static OperationalAction runSteadyState() {
    return new OperationalAction(ActionType.RUN_STEADY_STATE, "", Double.NaN, "", 0.0, 0.0,
        "Run steady-state calculation");
  }

  /**
   * Creates an action that runs transient process steps.
   *
   * @param durationSeconds total duration in seconds
   * @param timeStepSeconds time step in seconds
   * @return operational action
   */
  public static OperationalAction runTransient(double durationSeconds, double timeStepSeconds) {
    return new OperationalAction(ActionType.RUN_TRANSIENT, "", Double.NaN, "", durationSeconds,
        timeStepSeconds, "Run transient simulation for " + durationSeconds + " s");
  }

  /**
   * Returns the action type.
   *
   * @return action type
   */
  public ActionType getType() {
    return type;
  }

  /**
   * Returns the action target.
   *
   * @return target text
   */
  public String getTarget() {
    return target;
  }

  /**
   * Returns the action value.
   *
   * @return value, or NaN for actions that do not use one
   */
  public double getValue() {
    return value;
  }

  /**
   * Returns the value unit.
   *
   * @return unit string or empty string
   */
  public String getUnit() {
    return unit;
  }

  /**
   * Returns the transient duration.
   *
   * @return duration in seconds
   */
  public double getDurationSeconds() {
    return durationSeconds;
  }

  /**
   * Returns the transient time step.
   *
   * @return time step in seconds
   */
  public double getTimeStepSeconds() {
    return timeStepSeconds;
  }

  /**
   * Returns the action description.
   *
   * @return description
   */
  public String getDescription() {
    return description;
  }

  /**
   * Cleans nullable text to a trimmed non-null value.
   *
   * @param text text to clean
   * @return trimmed text or empty string
   */
  private static String clean(String text) {
    return text == null ? "" : text.trim();
  }
}