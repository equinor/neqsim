package neqsim.process.diagnostics.restart;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A single step in a restart sequence.
 *
 * <p>
 * Each step represents an action to take during restart, such as opening a valve, starting a
 * compressor, or waiting for a condition. Steps can have target values, durations, and ramp rates.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public class RestartStep implements Serializable {
  private static final long serialVersionUID = 1000L;

  /**
   * Type of restart action.
   */
  public enum ActionType {
    /** Open or close a valve to a target position. */
    VALVE_ACTION("Valve action"),
    /** Start a compressor. */
    COMPRESSOR_START("Compressor start"),
    /** Ramp a compressor to target speed or load. */
    COMPRESSOR_RAMP("Compressor ramp"),
    /** Adjust a controller setpoint. */
    SETPOINT_CHANGE("Setpoint change"),
    /** Wait for a specific duration. */
    WAIT_DURATION("Wait for duration"),
    /** Wait for a condition to be met (e.g. level > 30%). */
    WAIT_CONDITION("Wait for condition"),
    /** Manual operator action required. */
    OPERATOR_ACTION("Operator action"),
    /** Verify a condition before proceeding. */
    VERIFICATION("Verification check");

    private final String displayName;

    ActionType(String displayName) {
      this.displayName = displayName;
    }

    /**
     * Returns the display name.
     *
     * @return display name
     */
    public String getDisplayName() {
      return displayName;
    }
  }

  private final int stepNumber;
  private final String description;
  private final ActionType actionType;
  private final String targetEquipment;
  private final String parameterName;
  private final double targetValue;
  private final String unit;
  private double rampRate;
  private double durationSeconds;
  private String conditionExpression;
  private boolean completed;

  /**
   * Constructs a restart step.
   *
   * @param stepNumber the step number in the sequence
   * @param description human-readable description
   * @param actionType the type of action
   * @param targetEquipment name of the equipment this step targets
   * @param parameterName name of the parameter to change
   * @param targetValue target value for the parameter
   * @param unit unit of the target value
   */
  public RestartStep(int stepNumber, String description, ActionType actionType,
      String targetEquipment, String parameterName, double targetValue, String unit) {
    this.stepNumber = stepNumber;
    this.description = description;
    this.actionType = actionType;
    this.targetEquipment = targetEquipment;
    this.parameterName = parameterName;
    this.targetValue = targetValue;
    this.unit = unit;
    this.completed = false;
  }

  /**
   * Returns the step number.
   *
   * @return step number
   */
  public int getStepNumber() {
    return stepNumber;
  }

  /**
   * Returns the description.
   *
   * @return description
   */
  public String getDescription() {
    return description;
  }

  /**
   * Returns the action type.
   *
   * @return action type
   */
  public ActionType getActionType() {
    return actionType;
  }

  /**
   * Returns the target equipment name.
   *
   * @return target equipment name
   */
  public String getTargetEquipment() {
    return targetEquipment;
  }

  /**
   * Returns the parameter name.
   *
   * @return parameter name
   */
  public String getParameterName() {
    return parameterName;
  }

  /**
   * Returns the target value.
   *
   * @return target value
   */
  public double getTargetValue() {
    return targetValue;
  }

  /**
   * Returns the unit.
   *
   * @return unit
   */
  public String getUnit() {
    return unit;
  }

  /**
   * Returns the ramp rate (units per second).
   *
   * @return ramp rate
   */
  public double getRampRate() {
    return rampRate;
  }

  /**
   * Sets the ramp rate (units per second) for gradual changes.
   *
   * @param rampRate ramp rate in units per second
   */
  public void setRampRate(double rampRate) {
    this.rampRate = rampRate;
  }

  /**
   * Returns the step duration in seconds.
   *
   * @return duration in seconds
   */
  public double getDurationSeconds() {
    return durationSeconds;
  }

  /**
   * Sets the duration for WAIT_DURATION steps.
   *
   * @param durationSeconds duration in seconds
   */
  public void setDurationSeconds(double durationSeconds) {
    this.durationSeconds = durationSeconds;
  }

  /**
   * Returns the condition expression for WAIT_CONDITION steps.
   *
   * @return condition expression
   */
  public String getConditionExpression() {
    return conditionExpression;
  }

  /**
   * Sets the condition expression for WAIT_CONDITION steps.
   *
   * @param conditionExpression a human-readable condition description
   */
  public void setConditionExpression(String conditionExpression) {
    this.conditionExpression = conditionExpression;
  }

  /**
   * Returns whether the step has been completed.
   *
   * @return true if completed
   */
  public boolean isCompleted() {
    return completed;
  }

  /**
   * Marks the step as completed.
   */
  public void markCompleted() {
    this.completed = true;
  }

  /**
   * Converts the step to a map for JSON serialisation.
   *
   * @return map of step properties
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("step", stepNumber);
    map.put("action", actionType.name());
    map.put("description", description);
    map.put("equipment", targetEquipment);
    map.put("parameter", parameterName);
    map.put("targetValue", targetValue);
    map.put("unit", unit);
    if (rampRate > 0) {
      map.put("rampRate", rampRate);
    }
    if (durationSeconds > 0) {
      map.put("duration_s", durationSeconds);
    }
    if (conditionExpression != null) {
      map.put("condition", conditionExpression);
    }
    map.put("completed", completed);
    return map;
  }

  @Override
  public String toString() {
    return String.format("Step %d: %s [%s] %s=%s %s", stepNumber, description, actionType.name(),
        parameterName != null ? parameterName : "", targetValue, unit != null ? unit : "");
  }
}
