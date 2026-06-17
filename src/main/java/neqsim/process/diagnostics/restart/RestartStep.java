package neqsim.process.diagnostics.restart;

import java.io.Serializable;

/**
 * A single step in a restart sequence.
 *
 * <p>
 * Each step describes one action to take during restart, including the target equipment, the
 * action to perform, any preconditions that must be satisfied, and the recommended timing.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class RestartStep implements Serializable {

  private static final long serialVersionUID = 1000L;

  /**
   * Priority level for restart steps.
   */
  public enum Priority {
    /** Must be done first — safety-critical precondition. */
    CRITICAL,
    /** High priority — required for process flow to resume. */
    HIGH,
    /** Normal priority — standard restart action. */
    NORMAL,
    /** Low priority — can be deferred after initial restart. */
    LOW
  }

  private final int sequenceNumber;
  private final String equipmentName;
  private final String action;
  private final String precondition;
  private final double recommendedDelaySeconds;
  private final Priority priority;
  private final String notes;

  /**
   * Creates a restart step.
   *
   * @param sequenceNumber order in the restart sequence (1-based)
   * @param equipmentName target equipment name
   * @param action description of the action to take
   * @param precondition precondition that must be met before this step (may be null)
   * @param recommendedDelaySeconds recommended delay after the previous step completes
   * @param priority priority level
   * @param notes additional notes or warnings (may be null)
   */
  public RestartStep(int sequenceNumber, String equipmentName, String action, String precondition,
      double recommendedDelaySeconds, Priority priority, String notes) {
    this.sequenceNumber = sequenceNumber;
    this.equipmentName = equipmentName;
    this.action = action;
    this.precondition = precondition;
    this.recommendedDelaySeconds = recommendedDelaySeconds;
    this.priority = priority;
    this.notes = notes;
  }

  /**
   * Gets the sequence number.
   *
   * @return 1-based sequence number
   */
  public int getSequenceNumber() {
    return sequenceNumber;
  }

  /**
   * Gets the target equipment name.
   *
   * @return equipment name
   */
  public String getEquipmentName() {
    return equipmentName;
  }

  /**
   * Gets the action description.
   *
   * @return action
   */
  public String getAction() {
    return action;
  }

  /**
   * Gets the precondition, if any.
   *
   * @return precondition text, or null
   */
  public String getPrecondition() {
    return precondition;
  }

  /**
   * Gets the recommended delay in seconds after the previous step.
   *
   * @return delay in seconds
   */
  public double getRecommendedDelaySeconds() {
    return recommendedDelaySeconds;
  }

  /**
   * Gets the priority level.
   *
   * @return priority
   */
  public Priority getPriority() {
    return priority;
  }

  /**
   * Gets additional notes.
   *
   * @return notes, or null
   */
  public String getNotes() {
    return notes;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(String.format("Step %d [%s]: %s — %s", sequenceNumber, priority, equipmentName,
        action));
    if (precondition != null && !precondition.trim().isEmpty()) {
      sb.append(" (requires: ").append(precondition).append(")");
    }
    if (recommendedDelaySeconds > 0) {
      sb.append(String.format(" [wait %.0fs]", recommendedDelaySeconds));
    }
    if (notes != null && !notes.trim().isEmpty()) {
      sb.append(" NOTE: ").append(notes);
    }
    return sb.toString();
  }
}
