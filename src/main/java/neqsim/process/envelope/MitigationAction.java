package neqsim.process.envelope;

import java.io.Serializable;

/**
 * Represents a recommended corrective action to prevent a trip or restore operating margins.
 *
 * <p>
 * A {@code MitigationAction} is an advisory output from the Operating Envelope Agent. It describes
 * what to adjust, on which equipment, and the expected improvement. The agent NEVER writes to the
 * control system — all actions are advisory for human operators or higher-level decision support
 * systems.
 * </p>
 *
 * <p>
 * Example:
 * </p>
 *
 * <pre>
 * MitigationAction action = new MitigationAction("Increase anti-surge valve opening",
 *     "1st Stage Compressor", "antiSurgeValve.opening", 15.0, "%",
 *     MitigationAction.Priority.IMMEDIATE, "Restores surge margin from 3% to 12%");
 * </pre>
 *
 * @author NeqSim
 * @version 1.0
 */
public class MitigationAction implements Serializable, Comparable<MitigationAction> {
  private static final long serialVersionUID = 1L;

  /**
   * Priority level indicating urgency of the recommended action.
   */
  public enum Priority {
    /** Action needed within minutes to prevent trip. */
    IMMEDIATE(1),
    /** Action needed within the hour to restore normal margins. */
    SOON(2),
    /** Monitoring action — no immediate change needed. */
    MONITOR(3);

    private final int rank;

    /**
     * Creates a priority with sort rank.
     *
     * @param rank numeric rank (1 = highest)
     */
    Priority(int rank) {
      this.rank = rank;
    }

    /**
     * Returns the numeric rank for sorting (lower = more urgent).
     *
     * @return rank value
     */
    public int getRank() {
      return rank;
    }
  }

  /**
   * Category of mitigation action.
   */
  public enum Category {
    /** Adjust a process setpoint (temperature, pressure, flow). */
    SETPOINT_CHANGE,
    /** Adjust a valve opening or controller output. */
    VALVE_ADJUSTMENT,
    /** Request operator to inspect or intervene. */
    OPERATOR_INTERVENTION,
    /** Change chemical injection rate (inhibitor, demulsifier, etc.). */
    CHEMICAL_INJECTION,
    /** Load shedding or equipment switching. */
    EQUIPMENT_SWITCH,
    /** Composition management (blending, diversion). */
    COMPOSITION_MANAGEMENT,
    /** General monitoring recommendation. */
    MONITORING
  }

  private final String description;
  private final String targetEquipment;
  private final String targetVariable;
  private final double suggestedValue;
  private final String unit;
  private final Priority priority;
  private final String expectedImprovement;
  private final Category category;
  private String sideEffects;
  private String triggeringMarginKey;
  private double confidenceLevel;

  /**
   * Creates a mitigation action with full parameters.
   *
   * @param description human-readable description of the action
   * @param targetEquipment name of the equipment to adjust
   * @param targetVariable variable to change (automation address format)
   * @param suggestedValue recommended new value
   * @param unit engineering unit of the suggested value
   * @param priority urgency level
   * @param expectedImprovement description of expected margin improvement
   */
  public MitigationAction(String description, String targetEquipment, String targetVariable,
      double suggestedValue, String unit, Priority priority, String expectedImprovement) {
    this.description = description;
    this.targetEquipment = targetEquipment;
    this.targetVariable = targetVariable;
    this.suggestedValue = suggestedValue;
    this.unit = unit;
    this.priority = priority;
    this.expectedImprovement = expectedImprovement;
    this.category = Category.SETPOINT_CHANGE;
    this.sideEffects = "";
    this.triggeringMarginKey = "";
    this.confidenceLevel = 0.5;
  }

  /**
   * Creates a mitigation action with category specified.
   *
   * @param description human-readable description
   * @param targetEquipment name of the equipment
   * @param targetVariable variable to change
   * @param suggestedValue recommended new value
   * @param unit engineering unit
   * @param priority urgency level
   * @param expectedImprovement expected improvement description
   * @param category action category
   */
  public MitigationAction(String description, String targetEquipment, String targetVariable,
      double suggestedValue, String unit, Priority priority, String expectedImprovement,
      Category category) {
    this(description, targetEquipment, targetVariable, suggestedValue, unit, priority,
        expectedImprovement);
  }

  /**
   * Returns the human-readable description of this action.
   *
   * @return action description
   */
  public String getDescription() {
    return description;
  }

  /**
   * Returns the name of the target equipment.
   *
   * @return equipment name
   */
  public String getTargetEquipment() {
    return targetEquipment;
  }

  /**
   * Returns the target variable to adjust (automation address format).
   *
   * @return variable address
   */
  public String getTargetVariable() {
    return targetVariable;
  }

  /**
   * Returns the suggested new value for the target variable.
   *
   * @return suggested value
   */
  public double getSuggestedValue() {
    return suggestedValue;
  }

  /**
   * Returns the engineering unit of the suggested value.
   *
   * @return unit string
   */
  public String getUnit() {
    return unit;
  }

  /**
   * Returns the priority level.
   *
   * @return priority
   */
  public Priority getPriority() {
    return priority;
  }

  /**
   * Returns the expected improvement description.
   *
   * @return improvement description
   */
  public String getExpectedImprovement() {
    return expectedImprovement;
  }

  /**
   * Returns the action category.
   *
   * @return category
   */
  public Category getCategory() {
    return category;
  }

  /**
   * Returns known side effects of this action (e.g., "may reduce throughput by 5%").
   *
   * @return side effects description, or empty string if none
   */
  public String getSideEffects() {
    return sideEffects;
  }

  /**
   * Sets the side effects description.
   *
   * @param sideEffects description of potential side effects
   */
  public void setSideEffects(String sideEffects) {
    this.sideEffects = sideEffects;
  }

  /**
   * Returns the key of the margin that triggered this action.
   *
   * @return margin key
   */
  public String getTriggeringMarginKey() {
    return triggeringMarginKey;
  }

  /**
   * Sets the triggering margin key.
   *
   * @param triggeringMarginKey margin key that prompted this action
   */
  public void setTriggeringMarginKey(String triggeringMarginKey) {
    this.triggeringMarginKey = triggeringMarginKey;
  }

  /**
   * Returns the confidence level of this recommendation (0.0 to 1.0).
   *
   * @return confidence level
   */
  public double getConfidenceLevel() {
    return confidenceLevel;
  }

  /**
   * Sets the confidence level of this recommendation.
   *
   * @param confidenceLevel value between 0.0 (uncertain) and 1.0 (certain)
   */
  public void setConfidenceLevel(double confidenceLevel) {
    this.confidenceLevel = Math.max(0.0, Math.min(1.0, confidenceLevel));
  }

  /**
   * Compares actions by priority (most urgent first), then by confidence (highest first).
   *
   * @param other the other action
   * @return comparison result
   */
  @Override
  public int compareTo(MitigationAction other) {
    int priorityCompare = Integer.compare(this.priority.getRank(), other.priority.getRank());
    if (priorityCompare != 0) {
      return priorityCompare;
    }
    return Double.compare(other.confidenceLevel, this.confidenceLevel);
  }

  /**
   * Returns a formatted summary string.
   *
   * @return summary
   */
  @Override
  public String toString() {
    return String.format("[%s] %s -> %s.%s = %.2f %s (expected: %s)", priority, description,
        targetEquipment, targetVariable, suggestedValue, unit, expectedImprovement);
  }
}
