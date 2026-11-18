package neqsim.process.logic.procedure;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.logic.LogicAction;
import neqsim.process.logic.LogicCondition;
import neqsim.process.logic.LogicState;
import neqsim.process.logic.ProcessLogic;

/**
 * Executes an ordered operating procedure with physical feedback and timestamped evidence.
 *
 * <p>
 * A step can be automatic or require an explicit operator authorization. The action issues a command, while a separate
 * condition reads measured completion. Step prerequisites, timeout, first-out failure, optional recovery action and
 * alarm text are retained in the execution record. The process model remains responsible for advancing physical
 * equipment between procedure scans.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
@SuppressFBWarnings(value = "SE_BAD_FIELD", justification = "Procedure configuration intentionally retains the caller's live action and condition graph; durable portable evidence is the immutable ExecutionResult JSON record")
public final class OperatingProcedure implements ProcessLogic {
  private static final long serialVersionUID = 1L;

  /** Declares who owns the command that starts a procedure step. */
  public enum CommandMode {
    /** Procedure issues the command when prerequisites are met. */
    AUTOMATIC,
    /** An operator authorization is required before the command is issued. */
    OPERATOR
  }

  /** Stable event types emitted by a procedure execution. */
  public enum EventType {
    /** Procedure activation was requested. */
    ACTIVATED,
    /** All procedure-level prerequisites were satisfied. */
    READY,
    /** A step became the active step. */
    STEP_ENTERED,
    /** The procedure is waiting for operator authorization. */
    WAITING_OPERATOR,
    /** The operator authorized a step. */
    OPERATOR_AUTHORIZED,
    /** A step command was issued. */
    COMMAND_ISSUED,
    /** Measured step completion was confirmed. */
    STEP_COMPLETED,
    /** A recovery command was issued. */
    RECOVERY_COMMAND,
    /** The procedure completed successfully. */
    COMPLETED,
    /** The procedure aborted or failed. */
    ABORTED
  }

  /** Immutable timestamped procedure event. */
  public static final class ExecutionEvent implements Serializable {
    private static final long serialVersionUID = 1L;
    private final double timeSeconds;
    private final EventType type;
    private final String stepId;
    private final String message;
    private final String actualValue;
    private final String expectedValue;

    /**
     * Creates an execution event.
     *
     * @param timeSeconds elapsed procedure time in seconds
     * @param type event type
     * @param stepId stable step identifier, or empty for a procedure-level event
     * @param message human-readable event message
     * @param actualValue measured value at the event
     * @param expectedValue expected completion value at the event
     */
    private ExecutionEvent(double timeSeconds, EventType type, String stepId, String message, String actualValue,
        String expectedValue) {
      this.timeSeconds = timeSeconds;
      this.type = type;
      this.stepId = stepId;
      this.message = message;
      this.actualValue = actualValue;
      this.expectedValue = expectedValue;
    }

    /**
     * Converts this event to an ordered JSON-ready record.
     *
     * @return ordered event fields
     */
    public Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("timeSeconds", timeSeconds);
      result.put("type", type.name());
      result.put("stepId", stepId);
      result.put("message", message);
      result.put("actualValue", actualValue);
      result.put("expectedValue", expectedValue);
      return result;
    }

    /**
     * Gets event time.
     *
     * @return elapsed seconds
     */
    public double getTimeSeconds() {
      return timeSeconds;
    }

    /**
     * Gets event type.
     *
     * @return event type
     */
    public EventType getType() {
      return type;
    }

    /**
     * Gets stable step identifier.
     *
     * @return step identifier or empty string
     */
    public String getStepId() {
      return stepId;
    }
  }

  /** Immutable execution result suitable for durable evidence. */
  public static final class ExecutionResult implements Serializable {
    private static final long serialVersionUID = 1L;
    private final Map<String, Object> record;

    /**
     * Creates an immutable execution result.
     *
     * @param record complete ordered result record
     */
    private ExecutionResult(Map<String, Object> record) {
      this.record = Collections.unmodifiableMap(record);
    }

    /**
     * Returns the JSON-ready execution record.
     *
     * @return unmodifiable result record
     */
    public Map<String, Object> toMap() {
      return record;
    }

    /**
     * Serializes the execution result deterministically.
     *
     * @return pretty-printed JSON
     */
    public String toJson() {
      Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
      return gson.toJson(record);
    }
  }

  /** Internal immutable procedure step configuration. */
  private static final class Step implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String id;
    private final CommandMode mode;
    private final LogicCondition prerequisite;
    private final LogicAction action;
    private final LogicCondition completion;
    private final double timeoutSeconds;
    private final LogicAction recoveryAction;

    /**
     * Creates a procedure step.
     *
     * @param id stable step identifier
     * @param mode command ownership
     * @param prerequisite optional step prerequisite
     * @param action commanded action
     * @param completion optional measured completion condition
     * @param timeoutSeconds maximum step duration
     * @param recoveryAction optional action issued after failure
     */
    private Step(String id, CommandMode mode, LogicCondition prerequisite, LogicAction action,
        LogicCondition completion, double timeoutSeconds, LogicAction recoveryAction) {
      this.id = id;
      this.mode = mode;
      this.prerequisite = prerequisite;
      this.action = action;
      this.completion = completion;
      this.timeoutSeconds = timeoutSeconds;
      this.recoveryAction = recoveryAction;
    }
  }

  private final String procedureId;
  private final String revision;
  private final String name;
  private final List<LogicCondition> prerequisites = new ArrayList<LogicCondition>();
  private final List<Step> steps = new ArrayList<Step>();
  private final List<ExecutionEvent> events = new ArrayList<ExecutionEvent>();
  private final Set<String> operatorAuthorizations = new HashSet<String>();
  private LogicState state = LogicState.IDLE;
  private int currentStepIndex;
  private double elapsedTimeSeconds;
  private double stepElapsedTimeSeconds;
  private double prerequisiteTimeoutSeconds = 300.0;
  private boolean stepEntered;
  private boolean commandIssued;
  private boolean operatorWaitRecorded;
  private String failureReason = "";
  private String firstOutStepId = "";

  /**
   * Creates an operating procedure.
   *
   * @param procedureId stable engineering procedure identifier
   * @param revision controlled procedure revision
   * @param name human-readable procedure name
   */
  public OperatingProcedure(String procedureId, String revision, String name) {
    this.procedureId = requireText("procedureId", procedureId);
    this.revision = requireText("revision", revision);
    this.name = requireText("name", name);
  }

  /**
   * Adds a procedure-level prerequisite.
   *
   * @param prerequisite condition required before the first step
   * @return this procedure
   */
  public OperatingProcedure addPrerequisite(LogicCondition prerequisite) {
    if (prerequisite == null) {
      throw new IllegalArgumentException("Procedure prerequisite cannot be null");
    }
    prerequisites.add(prerequisite);
    return this;
  }

  /**
   * Sets the maximum wait for procedure-level prerequisites.
   *
   * @param timeoutSeconds finite positive timeout in seconds
   * @return this procedure
   */
  public OperatingProcedure setPrerequisiteTimeout(double timeoutSeconds) {
    prerequisiteTimeoutSeconds = requirePositive("prerequisite timeout", timeoutSeconds);
    return this;
  }

  /**
   * Adds an automatically commanded step.
   *
   * @param stepId stable step identifier
   * @param prerequisite optional step prerequisite
   * @param action command action
   * @param completion optional measured completion condition; action completion is used when null
   * @param timeoutSeconds finite positive timeout
   * @param recoveryAction optional action issued on failure
   * @return this procedure
   */
  public OperatingProcedure addAutomaticStep(String stepId, LogicCondition prerequisite, LogicAction action,
      LogicCondition completion, double timeoutSeconds, LogicAction recoveryAction) {
    return addStep(stepId, CommandMode.AUTOMATIC, prerequisite, action, completion, timeoutSeconds, recoveryAction);
  }

  /**
   * Adds an operator-authorized step.
   *
   * @param stepId stable step identifier
   * @param prerequisite optional step prerequisite
   * @param action command action
   * @param completion optional measured completion condition; action completion is used when null
   * @param timeoutSeconds finite positive timeout including operator wait
   * @param recoveryAction optional action issued on failure
   * @return this procedure
   */
  public OperatingProcedure addOperatorStep(String stepId, LogicCondition prerequisite, LogicAction action,
      LogicCondition completion, double timeoutSeconds, LogicAction recoveryAction) {
    return addStep(stepId, CommandMode.OPERATOR, prerequisite, action, completion, timeoutSeconds, recoveryAction);
  }

  /**
   * Authorizes the matching operator-owned step without executing its command immediately.
   *
   * @param stepId stable step identifier
   */
  public void authorizeOperatorStep(String stepId) {
    String checked = requireText("stepId", stepId);
    Step step = currentStep();
    if (state != LogicState.RUNNING || step == null || step.mode != CommandMode.OPERATOR || !step.id.equals(checked)) {
      throw new IllegalStateException("Operator authorization must match the active operator-owned step");
    }
    operatorAuthorizations.add(checked);
    record(EventType.OPERATOR_AUTHORIZED, checked, "Operator authorization received", null);
  }

  /**
   * Aborts the procedure and issues the active step recovery action when configured.
   *
   * @param reason explicit abort reason
   */
  public void abort(String reason) {
    fail(currentStep(), requireText("abort reason", reason));
  }

  /** {@inheritDoc} */
  @Override
  public void activate() {
    if (state == LogicState.RUNNING || state == LogicState.WAITING_PERMISSIVES) {
      return;
    }
    resetExecution();
    state = LogicState.WAITING_PERMISSIVES;
    record(EventType.ACTIVATED, "", "Procedure activated", null);
  }

  /** {@inheritDoc} */
  @Override
  public void deactivate() {
    if (isActive()) {
      state = LogicState.PAUSED;
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean reset() {
    resetExecution();
    state = LogicState.IDLE;
    return true;
  }

  /** {@inheritDoc} */
  @Override
  public void execute(double timeStep) {
    if (!Double.isFinite(timeStep) || timeStep < 0.0) {
      throw new IllegalArgumentException("Procedure scan interval must be finite and nonnegative");
    }
    if (!isActive()) {
      return;
    }
    elapsedTimeSeconds += timeStep;
    if (state == LogicState.WAITING_PERMISSIVES) {
      if (allProcedurePrerequisitesMet()) {
        state = LogicState.RUNNING;
        record(EventType.READY, "", "Procedure prerequisites satisfied", null);
      } else if (elapsedTimeSeconds >= prerequisiteTimeoutSeconds) {
        fail(null, "Timeout waiting for procedure prerequisites");
        return;
      } else {
        return;
      }
    }
    executeCurrentStep(timeStep);
  }

  /**
   * Captures the current execution result.
   *
   * @return immutable JSON-ready result
   */
  public ExecutionResult snapshot() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    List<Map<String, Object>> eventRecords = new ArrayList<Map<String, Object>>();
    for (ExecutionEvent event : events) {
      eventRecords.add(Collections.unmodifiableMap(event.toMap()));
    }
    result.put("procedureId", procedureId);
    result.put("revision", revision);
    result.put("name", name);
    result.put("state", state.name());
    result.put("completed", isComplete());
    result.put("elapsedTimeSeconds", elapsedTimeSeconds);
    result.put("firstOutStepId", firstOutStepId);
    result.put("failureReason", failureReason);
    result.put("events", Collections.unmodifiableList(eventRecords));
    return new ExecutionResult(result);
  }

  /**
   * Gets the timestamped events recorded so far.
   *
   * @return unmodifiable event list
   */
  public List<ExecutionEvent> getEvents() {
    return Collections.unmodifiableList(events);
  }

  /**
   * Gets the failure or abort reason.
   *
   * @return reason, or empty when no failure occurred
   */
  public String getFailureReason() {
    return failureReason;
  }

  /**
   * Gets the first failed step.
   *
   * @return stable step identifier, or empty for a procedure-level failure
   */
  public String getFirstOutStepId() {
    return firstOutStepId;
  }

  /** {@inheritDoc} */
  @Override
  public String getName() {
    return name;
  }

  /** {@inheritDoc} */
  @Override
  public LogicState getState() {
    return state;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isActive() {
    return state == LogicState.RUNNING || state == LogicState.WAITING_PERMISSIVES;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isComplete() {
    return state == LogicState.COMPLETED;
  }

  /** {@inheritDoc} */
  @Override
  public List<ProcessEquipmentInterface> getTargetEquipment() {
    Set<ProcessEquipmentInterface> targets = new LinkedHashSet<ProcessEquipmentInterface>();
    for (LogicCondition condition : prerequisites) {
      addConditionTarget(targets, condition);
    }
    for (Step step : steps) {
      addConditionTarget(targets, step.prerequisite);
      addConditionTarget(targets, step.completion);
      targets.addAll(step.action.getTargetEquipment());
      if (step.recoveryAction != null) {
        targets.addAll(step.recoveryAction.getTargetEquipment());
      }
    }
    return new ArrayList<ProcessEquipmentInterface>(targets);
  }

  /** {@inheritDoc} */
  @Override
  public String getStatusDescription() {
    if (state == LogicState.RUNNING && currentStepIndex < steps.size()) {
      return name + " - RUNNING " + steps.get(currentStepIndex).id + " at " + elapsedTimeSeconds + " s";
    }
    if (state == LogicState.FAILED) {
      return name + " - FAILED: " + failureReason;
    }
    return name + " - " + state.name();
  }

  /** Adds and validates one configured step. */
  private OperatingProcedure addStep(String stepId, CommandMode mode, LogicCondition prerequisite, LogicAction action,
      LogicCondition completion, double timeoutSeconds, LogicAction recoveryAction) {
    String checkedId = requireText("stepId", stepId);
    if (action == null || mode == null) {
      throw new IllegalArgumentException("Procedure step requires mode and action");
    }
    for (Step configured : steps) {
      if (configured.id.equals(checkedId)) {
        throw new IllegalArgumentException("Duplicate procedure step id: " + checkedId);
      }
    }
    steps.add(new Step(checkedId, mode, prerequisite, action, completion,
        requirePositive("step timeout", timeoutSeconds), recoveryAction));
    return this;
  }

  /** Advances the active procedure step by one scan. */
  private void executeCurrentStep(double timeStep) {
    Step step = currentStep();
    if (step == null) {
      completeProcedure();
      return;
    }
    if (!stepEntered) {
      stepEntered = true;
      record(EventType.STEP_ENTERED, step.id, "Step entered", step.completion);
    }
    stepElapsedTimeSeconds += timeStep;
    if (step.prerequisite != null && !step.prerequisite.evaluate()) {
      if (commandIssued) {
        fail(step, "Step prerequisite lost: " + step.prerequisite.getDescription());
      } else if (stepElapsedTimeSeconds >= step.timeoutSeconds) {
        fail(step, "Timeout waiting for step prerequisite: " + step.prerequisite.getDescription());
      }
      return;
    }
    if (step.mode == CommandMode.OPERATOR && !operatorAuthorizations.contains(step.id)) {
      if (!operatorWaitRecorded) {
        operatorWaitRecorded = true;
        record(EventType.WAITING_OPERATOR, step.id, "Waiting for operator authorization", step.completion);
      }
      if (stepElapsedTimeSeconds >= step.timeoutSeconds) {
        fail(step, "Timeout waiting for operator authorization");
      }
      return;
    }
    if (!commandIssued) {
      step.action.execute();
      commandIssued = true;
      record(EventType.COMMAND_ISSUED, step.id, step.action.getDescription(), step.completion);
    }
    boolean completed = step.completion == null ? step.action.isComplete() : step.completion.evaluate();
    if (completed) {
      record(EventType.STEP_COMPLETED, step.id, "Measured completion confirmed", step.completion);
      currentStepIndex++;
      stepElapsedTimeSeconds = 0.0;
      stepEntered = false;
      commandIssued = false;
      operatorWaitRecorded = false;
      if (currentStepIndex >= steps.size()) {
        completeProcedure();
      }
    } else if (stepElapsedTimeSeconds >= step.timeoutSeconds) {
      fail(step, "Step completion timeout");
    }
  }

  /** Completes the procedure and records its terminal event. */
  private void completeProcedure() {
    state = LogicState.COMPLETED;
    record(EventType.COMPLETED, "", "Procedure completed", null);
  }

  /** Fails the procedure, records first-out and issues configured recovery. */
  private void fail(Step step, String reason) {
    if (state == LogicState.FAILED) {
      return;
    }
    firstOutStepId = step == null ? "" : step.id;
    failureReason = reason;
    if (step != null && step.recoveryAction != null) {
      step.recoveryAction.execute();
      record(EventType.RECOVERY_COMMAND, step.id, step.recoveryAction.getDescription(), step.completion);
    }
    state = LogicState.FAILED;
    record(EventType.ABORTED, firstOutStepId, reason, step == null ? null : step.completion);
  }

  /** Clears execution state without changing physical equipment. */
  private void resetExecution() {
    currentStepIndex = 0;
    elapsedTimeSeconds = 0.0;
    stepElapsedTimeSeconds = 0.0;
    stepEntered = false;
    commandIssued = false;
    operatorWaitRecorded = false;
    failureReason = "";
    firstOutStepId = "";
    events.clear();
    operatorAuthorizations.clear();
    for (Step step : steps) {
      step.action.reset();
      if (step.recoveryAction != null) {
        step.recoveryAction.reset();
      }
    }
  }

  /** Evaluates all procedure-level prerequisites. */
  private boolean allProcedurePrerequisitesMet() {
    for (LogicCondition prerequisite : prerequisites) {
      if (!prerequisite.evaluate()) {
        return false;
      }
    }
    return true;
  }

  /** Returns the active step or null after the final step. */
  private Step currentStep() {
    return currentStepIndex < steps.size() ? steps.get(currentStepIndex) : null;
  }

  /** Adds a non-null condition target to the ordered target set. */
  private static void addConditionTarget(Set<ProcessEquipmentInterface> targets, LogicCondition condition) {
    if (condition != null && condition.getTargetEquipment() != null) {
      targets.add(condition.getTargetEquipment());
    }
  }

  /** Records an event and current completion feedback. */
  private void record(EventType type, String stepId, String message, LogicCondition completion) {
    String actual = completion == null ? "" : completion.getCurrentValue();
    String expected = completion == null ? "" : completion.getExpectedValue();
    events.add(new ExecutionEvent(elapsedTimeSeconds, type, stepId, message, actual, expected));
  }

  /** Requires a non-blank value. */
  private static String requireText(String field, String value) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(field + " cannot be blank");
    }
    return value.trim();
  }

  /** Requires a finite positive number. */
  private static double requirePositive(String field, double value) {
    if (!Double.isFinite(value) || value <= 0.0) {
      throw new IllegalArgumentException(field + " must be finite and positive");
    }
    return value;
  }
}
