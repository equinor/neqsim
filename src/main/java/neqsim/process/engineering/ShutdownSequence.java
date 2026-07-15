package neqsim.process.engineering;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Traceable cause-and-effect shutdown sequence with timing and safe-state validation.
 *
 * <p>
 * The sequence is deterministic design input. NeqSim checks completeness and response-time allocation; dynamic process
 * validation may then use the existing ESD test runner. The sequence does not become approved merely by calculating it.
 * </p>
 */
public final class ShutdownSequence implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** One commanded shutdown action. */
  public static final class Action implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String actionTag;
    private final String action;
    private final String safePosition;
    private final double delaySeconds;
    private final double executionSeconds;

    public Action(String actionTag, String action, String safePosition, double delaySeconds, double executionSeconds) {
      this.actionTag = requireText(actionTag, "actionTag");
      this.action = requireText(action, "action");
      this.safePosition = requireText(safePosition, "safePosition");
      if (!Double.isFinite(delaySeconds) || delaySeconds < 0.0 || !Double.isFinite(executionSeconds)
          || executionSeconds < 0.0) {
        throw new IllegalArgumentException("action timing must be non-negative");
      }
      this.delaySeconds = delaySeconds;
      this.executionSeconds = executionSeconds;
    }

    public double getCompletionTimeSeconds() {
      return delaySeconds + executionSeconds;
    }

    public Map<String, Object> toMap() {
      Map<String, Object> map = new LinkedHashMap<String, Object>();
      map.put("actionTag", actionTag);
      map.put("action", action);
      map.put("safePosition", safePosition);
      map.put("delaySeconds", delaySeconds);
      map.put("executionSeconds", executionSeconds);
      map.put("completionTimeSeconds", getCompletionTimeSeconds());
      return map;
    }
  }

  private final String sequenceId;
  private final String initiatingCause;
  private final List<Action> actions = new ArrayList<Action>();
  private String protectedEquipmentTag = "";
  private String safeState = "";
  private String hazopReference = "";
  private String srsReference = "";
  private double responseTimeBudgetSeconds = Double.NaN;
  private boolean resetAndRestartDefined = false;
  private EngineeringApprovalStatus approvalStatus = EngineeringApprovalStatus.REVIEW_REQUIRED;

  public ShutdownSequence(String sequenceId, String initiatingCause) {
    this.sequenceId = requireText(sequenceId, "sequenceId");
    this.initiatingCause = requireText(initiatingCause, "initiatingCause");
  }

  private static String requireText(String value, String name) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return value.trim();
  }

  public ShutdownSequence setProtectedEquipmentTag(String value) {
    protectedEquipmentTag = requireText(value, "protectedEquipmentTag");
    return this;
  }

  public ShutdownSequence setSafeState(String value) {
    safeState = requireText(value, "safeState");
    return this;
  }

  public ShutdownSequence setHazopReference(String value) {
    hazopReference = requireText(value, "hazopReference");
    return this;
  }

  public ShutdownSequence setSrsReference(String value) {
    srsReference = requireText(value, "srsReference");
    return this;
  }

  public ShutdownSequence setResponseTimeBudgetSeconds(double value) {
    if (!Double.isFinite(value) || value <= 0.0) {
      throw new IllegalArgumentException("responseTimeBudgetSeconds must be positive");
    }
    responseTimeBudgetSeconds = value;
    return this;
  }

  public ShutdownSequence setResetAndRestartDefined(boolean value) {
    resetAndRestartDefined = value;
    return this;
  }

  public ShutdownSequence addAction(Action action) {
    if (action == null) {
      throw new IllegalArgumentException("action must not be null");
    }
    actions.add(action);
    return this;
  }

  public ShutdownSequence approve(String record) {
    setSrsReference(record);
    approvalStatus = EngineeringApprovalStatus.APPROVED;
    return this;
  }

  public double getTotalResponseTimeSeconds() {
    double total = 0.0;
    for (Action action : actions) {
      total = Math.max(total, action.getCompletionTimeSeconds());
    }
    return total;
  }

  public List<String> getMissingFields() {
    List<String> missing = new ArrayList<String>();
    if (protectedEquipmentTag.isEmpty()) {
      missing.add("protectedEquipmentTag");
    }
    if (safeState.isEmpty()) {
      missing.add("safeState");
    }
    if (hazopReference.isEmpty()) {
      missing.add("hazopReference");
    }
    if (srsReference.isEmpty()) {
      missing.add("srsReference");
    }
    if (!Double.isFinite(responseTimeBudgetSeconds)) {
      missing.add("responseTimeBudget");
    }
    if (actions.isEmpty()) {
      missing.add("shutdownActions");
    }
    if (!resetAndRestartDefined) {
      missing.add("resetAndRestartLogic");
    }
    return missing;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    List<Map<String, Object>> actionMaps = new ArrayList<Map<String, Object>>();
    for (Action action : actions) {
      actionMaps.add(action.toMap());
    }
    double total = getTotalResponseTimeSeconds();
    map.put("sequenceId", sequenceId);
    map.put("initiatingCause", initiatingCause);
    map.put("protectedEquipmentTag", protectedEquipmentTag);
    map.put("safeState", safeState);
    map.put("hazopReference", hazopReference);
    map.put("srsReference", srsReference);
    map.put("responseTimeBudgetSeconds", responseTimeBudgetSeconds);
    map.put("totalResponseTimeSeconds", total);
    map.put("responseTimeMarginSeconds",
        Double.isFinite(responseTimeBudgetSeconds) ? responseTimeBudgetSeconds - total : Double.NaN);
    map.put("withinResponseTimeBudget",
        Double.isFinite(responseTimeBudgetSeconds) && total <= responseTimeBudgetSeconds);
    map.put("resetAndRestartDefined", resetAndRestartDefined);
    map.put("approvalStatus", approvalStatus.name());
    map.put("actions", actionMaps);
    map.put("missingFields", getMissingFields());
    return map;
  }

  public String getSequenceId() {
    return sequenceId;
  }

  public List<Action> getActions() {
    return Collections.unmodifiableList(actions);
  }

  public EngineeringApprovalStatus getApprovalStatus() {
    return approvalStatus;
  }
}
