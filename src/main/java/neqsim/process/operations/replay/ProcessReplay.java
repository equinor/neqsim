package neqsim.process.operations.replay;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.automation.ProcessAutomation;
import neqsim.process.measurementdevice.InstrumentTagRole;
import neqsim.process.operations.OperationalTagBinding;
import neqsim.process.operations.OperationalTagMap;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.processmodel.RunStatus;
import neqsim.process.util.reconciliation.SteadyStateDetector;

/**
 * Replays a time series of plant data through a {@link ProcessSystem} or {@link ProcessModel}.
 *
 * <p>
 * For every row of a {@link ReplayTimeSeries} the replay
 * </p>
 * <ol>
 * <li>optionally filters the row (steady-state detector or user filter) and records it as skipped;</li>
 * <li>converts and validates the input values and writes them with
 * {@link ProcessAutomation#setVariableValueSafe(String, double, String)};</li>
 * <li>runs the model with {@code runUntilConverged(maxIterations)} and reads the {@link RunStatus};</li>
 * <li>on non-convergence calls the optional {@link RetryHook} and re-runs, up to {@link #setMaxRetries(int)};</li>
 * <li>reads the outputs with {@link ProcessAutomation#getVariableValueSafe(String, String)} and records predictions,
 * measurements and residuals.</li>
 * </ol>
 *
 * <p>
 * A bad row never aborts the replay: it is recorded with status {@link ReplayStepRecord.Status#INPUT_INVALID},
 * {@link ReplayStepRecord.Status#NOT_CONVERGED} or {@link ReplayStepRecord.Status#FAILED} and the next row is
 * processed.
 * </p>
 *
 * <p>
 * <b>State between steps.</b> With warm start (default) the same model instance is used for all rows, so recycle tear
 * streams, adjusters and controllers start from the previous step's solution; this is fast and mirrors a continuously
 * running plant. After a failed or non-converged step the model is restored to its state before the replay unless
 * {@link #setRestoreAfterFailure(boolean)} is false. With cold start every row runs on a fresh deep copy of the model
 * as it was when {@link #run(ReplayTimeSeries)} was called, so rows are independent and order does not matter. The
 * model passed to the constructor is never modified in cold-start mode; in warm-start mode it holds the last step's
 * state afterwards.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class ProcessReplay implements Serializable {
  private static final long serialVersionUID = 1L;
  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(ProcessReplay.class);

  /** Hook called before a retry of a non-converged step. */
  public interface RetryHook {
    /**
     * Prepares a retry, for example by perturbing an initial guess or loosening a controller.
     *
     * @param attempt retry number, starting at 1
     * @param automation automation facade of the model that will be re-run
     * @param record record of the current step (inputs already applied)
     * @return true to retry, false to give up on this step
     */
    boolean beforeRetry(int attempt, ProcessAutomation automation, ReplayStepRecord record);
  }

  /** Filter deciding whether a row is replayed. */
  public interface StepFilter {
    /**
     * Decides whether a row should be replayed.
     *
     * @param index row index
     * @param row all column values of the row
     * @return true to replay the row, false to record it as skipped
     */
    boolean accept(int index, Map<String, Double> row);
  }

  private ProcessSystem processSystem;
  private ProcessModel processModel;
  private final List<ReplayBinding> inputs = new ArrayList<ReplayBinding>();
  private final List<ReplayBinding> outputs = new ArrayList<ReplayBinding>();
  private int maxIterations = 10;
  private int maxRetries = 0;
  private boolean warmStart = true;
  private boolean restoreAfterFailure = true;
  private boolean allowAutoCorrection = false;
  private SteadyStateDetector steadyStateDetector;
  private transient RetryHook retryHook;
  private transient StepFilter stepFilter;

  /**
   * Creates a replay for a single process system.
   *
   * @param process the process system; must not be null
   */
  public ProcessReplay(ProcessSystem process) {
    if (process == null) {
      throw new IllegalArgumentException("process must not be null");
    }
    this.processSystem = process;
  }

  /**
   * Creates a replay for a multi-area process model. Automation addresses must be area-qualified
   * ({@code "Area::unit.property"}).
   *
   * @param model the process model; must not be null
   */
  public ProcessReplay(ProcessModel model) {
    if (model == null) {
      throw new IllegalArgumentException("model must not be null");
    }
    this.processModel = model;
  }

  /**
   * Creates a replay from an {@link OperationalTagMap}. Bindings with role {@link InstrumentTagRole#INPUT} and an
   * automation address become inputs and {@link InstrumentTagRole#BENCHMARK} bindings with an automation address become
   * outputs. The data column is the historian tag when set, otherwise the logical tag.
   *
   * @param process the process system
   * @param tagMap the tag map
   * @return the configured replay
   */
  public static ProcessReplay fromTagMap(ProcessSystem process, OperationalTagMap tagMap) {
    ProcessReplay replay = new ProcessReplay(process);
    replay.addBindingsFromTagMap(tagMap);
    return replay;
  }

  /**
   * Adds input and output bindings from an {@link OperationalTagMap}; see
   * {@link #fromTagMap(ProcessSystem, OperationalTagMap)}.
   *
   * @param tagMap the tag map
   * @return this replay
   */
  public ProcessReplay addBindingsFromTagMap(OperationalTagMap tagMap) {
    if (tagMap == null) {
      throw new IllegalArgumentException("tagMap must not be null");
    }
    for (OperationalTagBinding binding : tagMap.getBindings()) {
      if (!binding.hasAutomationAddress()) {
        continue;
      }
      String column = binding.hasHistorianTag() ? binding.getHistorianTag() : binding.getLogicalTag();
      if (binding.getRole() == InstrumentTagRole.INPUT) {
        addInput(ReplayBinding.input(column, binding.getAutomationAddress(), binding.getUnit()));
      } else if (binding.getRole() == InstrumentTagRole.BENCHMARK) {
        addOutput(ReplayBinding.output(binding.getAutomationAddress(), column, binding.getUnit()));
      }
    }
    return this;
  }

  /**
   * Adds an input binding.
   *
   * @param binding input binding created with {@link ReplayBinding#input(String, String, String)}
   * @return this replay
   */
  public ProcessReplay addInput(ReplayBinding binding) {
    if (binding == null || binding.getDirection() != ReplayBinding.Direction.INPUT) {
      throw new IllegalArgumentException("an input binding is required");
    }
    inputs.add(binding);
    return this;
  }

  /**
   * Adds an output binding.
   *
   * @param binding output binding created with {@link ReplayBinding#output(String, String, String)}
   * @return this replay
   */
  public ProcessReplay addOutput(ReplayBinding binding) {
    if (binding == null || binding.getDirection() != ReplayBinding.Direction.OUTPUT) {
      throw new IllegalArgumentException("an output binding is required");
    }
    outputs.add(binding);
    return this;
  }

  /**
   * Returns the input bindings.
   *
   * @return unmodifiable list of input bindings
   */
  public List<ReplayBinding> getInputs() {
    return Collections.unmodifiableList(inputs);
  }

  /**
   * Returns the output bindings.
   *
   * @return unmodifiable list of output bindings
   */
  public List<ReplayBinding> getOutputs() {
    return Collections.unmodifiableList(outputs);
  }

  /**
   * Sets the pass budget given to {@code runUntilConverged} for each attempt.
   *
   * @param maxIterations maximum number of passes, at least 1 (default 10)
   * @return this replay
   */
  public ProcessReplay setMaxIterations(int maxIterations) {
    if (maxIterations < 1) {
      throw new IllegalArgumentException("maxIterations must be at least 1");
    }
    this.maxIterations = maxIterations;
    return this;
  }

  /**
   * Sets the number of retries of a non-converged or failed step.
   *
   * @param maxRetries retries after the first attempt, at least 0 (default 0)
   * @return this replay
   */
  public ProcessReplay setMaxRetries(int maxRetries) {
    if (maxRetries < 0) {
      throw new IllegalArgumentException("maxRetries must not be negative");
    }
    this.maxRetries = maxRetries;
    return this;
  }

  /**
   * Selects warm start (state carries over between steps, default) or cold start (each step on a fresh copy).
   *
   * @param warmStart true for warm start
   * @return this replay
   */
  public ProcessReplay setWarmStart(boolean warmStart) {
    this.warmStart = warmStart;
    return this;
  }

  /**
   * In warm-start mode, restores the model to its pre-replay state after a failed or non-converged step (default true),
   * so one bad step does not poison the following ones.
   *
   * @param restoreAfterFailure true to restore after failure
   * @return this replay
   */
  public ProcessReplay setRestoreAfterFailure(boolean restoreAfterFailure) {
    this.restoreAfterFailure = restoreAfterFailure;
    return this;
  }

  /**
   * Allows the automation facade to auto-correct mistyped addresses (default false). Auto-correction uses fuzzy
   * matching, so it is off by default to avoid silently writing to a different variable.
   *
   * @param allow true to accept auto-corrected addresses
   * @return this replay
   */
  public ProcessReplay setAllowAutoCorrection(boolean allow) {
    this.allowAutoCorrection = allow;
    return this;
  }

  /**
   * Uses a {@link SteadyStateDetector} as step filter. All columns of each row are fed to the detector (variables that
   * are not registered are ignored); rows are skipped while the detector does not report steady state.
   *
   * @param detector configured detector, or null to disable
   * @return this replay
   */
  public ProcessReplay setSteadyStateDetector(SteadyStateDetector detector) {
    this.steadyStateDetector = detector;
    return this;
  }

  /**
   * Sets a user filter; rows it rejects are recorded as skipped. Not serialised.
   *
   * @param filter the filter, or null to disable
   * @return this replay
   */
  public ProcessReplay setStepFilter(StepFilter filter) {
    this.stepFilter = filter;
    return this;
  }

  /**
   * Sets the retry hook called before each retry. Not serialised.
   *
   * @param hook the hook, or null to retry without changes
   * @return this replay
   */
  public ProcessReplay setRetryHook(RetryHook hook) {
    this.retryHook = hook;
    return this;
  }

  /**
   * Replays a time series.
   *
   * @param series the data; must contain every input column (missing values make the step invalid)
   * @return the replay result
   */
  public ReplayResult run(ReplayTimeSeries series) {
    if (series == null) {
      throw new IllegalArgumentException("series must not be null");
    }
    if (inputs.isEmpty() && outputs.isEmpty()) {
      throw new IllegalStateException("add at least one input or output binding before running");
    }
    List<String> inputTags = new ArrayList<String>();
    for (ReplayBinding binding : inputs) {
      inputTags.add(binding.getTag());
    }
    List<String> outputTags = new ArrayList<String>();
    for (ReplayBinding binding : outputs) {
      outputTags.add(binding.getTag());
    }
    ReplayResult result = new ReplayResult(inputTags, outputTags, warmStart);

    byte[] template = null;
    if (!warmStart || restoreAfterFailure) {
      template = serialize(currentModel());
    }
    Object model = warmStart ? currentModel() : null;

    for (int row = 0; row < series.size(); row++) {
      ReplayStepRecord record = new ReplayStepRecord(row, series.getTime(row));
      result.addStep(record);
      try {
        if (!acceptRow(row, series)) {
          record.setStatus(ReplayStepRecord.Status.SKIPPED, "rejected by step filter");
          continue;
        }
        if (!warmStart) {
          model = deserialize(template);
        }
        replayStep(model, row, series, record);
      } catch (RuntimeException ex) {
        record.setStatus(ReplayStepRecord.Status.FAILED, ex.getClass().getSimpleName() + ": " + ex.getMessage());
        logger.debug("Replay step {} failed", row, ex);
      }
      if (warmStart && restoreAfterFailure && template != null && !record.isConverged()
          && record.getStatus() != ReplayStepRecord.Status.SKIPPED) {
        try {
          model = deserialize(template);
          setCurrentModel(model);
        } catch (RuntimeException ex) {
          logger.warn("Could not restore the model after failed replay step {}: {}", row, ex.getMessage());
        }
      }
    }
    logger.info("{}", result.getSummary());
    return result;
  }

  /**
   * Applies the step filters to a row.
   *
   * @param row row index
   * @param series the data
   * @return true when the row should be replayed
   */
  private boolean acceptRow(int row, ReplayTimeSeries series) {
    Map<String, Double> values = series.getRow(row);
    boolean accept = true;
    if (steadyStateDetector != null) {
      for (Map.Entry<String, Double> entry : values.entrySet()) {
        if (steadyStateDetector.getVariable(entry.getKey()) != null && entry.getValue() != null
            && ReplayBinding.isFinite(entry.getValue())) {
          steadyStateDetector.updateVariable(entry.getKey(), entry.getValue());
        }
      }
      accept = steadyStateDetector.evaluate().isAtSteadyState();
    }
    if (accept && stepFilter != null) {
      accept = stepFilter.accept(row, values);
    }
    return accept;
  }

  /**
   * Replays one row on the given model.
   *
   * @param model process system or process model
   * @param row row index
   * @param series the data
   * @param record record to fill
   */
  private void replayStep(Object model, int row, ReplayTimeSeries series, ReplayStepRecord record) {
    ProcessAutomation automation = automation(model);
    for (ReplayBinding binding : inputs) {
      double value = binding.convert(series.getValue(binding.getTag(), row));
      record.putInput(binding.getTag(), value);
      if (!binding.isValid(value)) {
        record.setStatus(ReplayStepRecord.Status.INPUT_INVALID,
            "input '" + binding.getTag() + "' is missing or outside its valid range: " + value);
        return;
      }
      String error = checkAutomationResult(
          automation.setVariableValueSafe(binding.getAutomationAddress(), value, binding.getUnit()));
      if (error != null) {
        record.setStatus(ReplayStepRecord.Status.INPUT_INVALID,
            "could not set '" + binding.getAutomationAddress() + "': " + error);
        return;
      }
    }

    boolean converged = false;
    String failure = null;
    int attempts = 0;
    for (int attempt = 0; attempt <= maxRetries; attempt++) {
      if (attempt > 0 && retryHook != null && !retryHook.beforeRetry(attempt, automation, record)) {
        break;
      }
      attempts++;
      try {
        converged = runModel(model);
        failure = null;
        RunStatus status = runStatus(model);
        if (status != null) {
          record.setTerminationReason(status.getTerminationReason());
          if (!status.isSuccess() && status.getFailedUnitName() != null) {
            converged = false;
            failure = "unit '" + status.getFailedUnitName() + "' failed: " + status.getFailedUnitError();
          }
        }
      } catch (RuntimeException ex) {
        converged = false;
        failure = ex.getClass().getSimpleName() + ": " + ex.getMessage();
      }
      if (converged) {
        break;
      }
    }
    record.setAttempts(attempts);
    if (!converged) {
      if (failure != null) {
        record.setStatus(ReplayStepRecord.Status.FAILED, failure);
      } else {
        record.setStatus(ReplayStepRecord.Status.NOT_CONVERGED,
            "model did not converge within " + maxIterations + " passes and " + maxRetries + " retries");
      }
      return;
    }

    StringBuilder warnings = new StringBuilder();
    for (ReplayBinding binding : outputs) {
      double predicted = readOutput(automation, binding, warnings);
      double measured = binding.convert(series.getValue(binding.getTag(), row));
      if (!binding.isValid(measured)) {
        measured = Double.NaN;
      }
      record.putOutput(binding.getTag(), predicted, measured);
    }
    record.setStatus(ReplayStepRecord.Status.CONVERGED, warnings.toString().trim());
  }

  /**
   * Reads one output value.
   *
   * @param automation automation facade
   * @param binding output binding
   * @param warnings collector for read errors
   * @return predicted value, NaN when it could not be read
   */
  private double readOutput(ProcessAutomation automation, ReplayBinding binding, StringBuilder warnings) {
    try {
      String json = automation.getVariableValueSafe(binding.getAutomationAddress(), binding.getUnit());
      String error = checkAutomationResult(json);
      if (error != null) {
        warnings.append("cannot read '").append(binding.getAutomationAddress()).append("': ").append(error).append(' ');
        return Double.NaN;
      }
      JsonObject object = JsonParser.parseString(json).getAsJsonObject();
      return object.get("value").getAsDouble();
    } catch (RuntimeException ex) {
      warnings.append("cannot read '").append(binding.getAutomationAddress()).append("': ").append(ex.getMessage())
          .append(' ');
      return Double.NaN;
    }
  }

  /**
   * Interprets a JSON result of the safe automation accessors.
   *
   * @param json JSON returned by the automation facade
   * @return null on success, otherwise an error description
   */
  private String checkAutomationResult(String json) {
    try {
      JsonObject object = JsonParser.parseString(json).getAsJsonObject();
      JsonElement status = object.get("status");
      if (status != null && "success".equals(status.getAsString())) {
        return null;
      }
      if (status != null && "auto_corrected".equals(status.getAsString())) {
        return allowAutoCorrection ? null
            : "address would be auto-corrected to '" + object.get("correctedAddress").getAsString()
                + "'; fix the binding or enable auto-correction";
      }
      JsonElement message = object.get("errorMessage");
      if (message == null || message.isJsonNull()) {
        message = object.get("remediation");
      }
      JsonElement category = object.get("category");
      return (category != null && !category.isJsonNull() ? category.getAsString() + ": " : "")
          + (message != null && !message.isJsonNull() ? message.getAsString() : json);
    } catch (RuntimeException ex) {
      return "unreadable automation result: " + json;
    }
  }

  /**
   * Returns the model currently held by the replay.
   *
   * @return process system or process model
   */
  private Object currentModel() {
    return processSystem != null ? processSystem : processModel;
  }

  /**
   * Replaces the model held by the replay (after a restore in warm-start mode).
   *
   * @param model process system or process model
   */
  private void setCurrentModel(Object model) {
    if (model instanceof ProcessSystem) {
      processSystem = (ProcessSystem) model;
    } else {
      processModel = (ProcessModel) model;
    }
  }

  /**
   * Returns the model the replay ran on last. In warm-start mode this is the live model with the last step's state;
   * after a restore it is a restored copy rather than the instance passed to the constructor.
   *
   * @return process system or process model
   */
  public Object getModel() {
    return currentModel();
  }

  /**
   * Returns the automation facade of a model.
   *
   * @param model process system or process model
   * @return automation facade
   */
  private static ProcessAutomation automation(Object model) {
    return model instanceof ProcessSystem ? ((ProcessSystem) model).getAutomation()
        : ((ProcessModel) model).getAutomation();
  }

  /**
   * Runs a model with the convergence gate.
   *
   * @param model process system or process model
   * @return true when converged
   */
  private boolean runModel(Object model) {
    return model instanceof ProcessSystem ? ((ProcessSystem) model).runUntilConverged(maxIterations)
        : ((ProcessModel) model).runUntilConverged(maxIterations);
  }

  /**
   * Returns the run status of a model.
   *
   * @param model process system or process model
   * @return run status, or null when unavailable
   */
  private static RunStatus runStatus(Object model) {
    return model instanceof ProcessSystem ? ((ProcessSystem) model).getRunStatus()
        : ((ProcessModel) model).getRunStatus();
  }

  /**
   * Serialises a model to bytes for later deep copies.
   *
   * @param model process system or process model
   * @return serialised bytes
   */
  private static byte[] serialize(Object model) {
    try {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
        out.writeObject(model);
      }
      return bytes.toByteArray();
    } catch (java.io.IOException ex) {
      throw new IllegalStateException("model cannot be copied for replay: " + ex.getMessage(), ex);
    }
  }

  /**
   * Creates a deep copy from serialised bytes.
   *
   * @param bytes serialised model
   * @return copied process system or process model
   */
  private static Object deserialize(byte[] bytes) {
    try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
      return in.readObject();
    } catch (java.io.IOException ex) {
      throw new IllegalStateException("model copy failed: " + ex.getMessage(), ex);
    } catch (ClassNotFoundException ex) {
      throw new IllegalStateException("model copy failed: " + ex.getMessage(), ex);
    }
  }
}
