package neqsim.process.automation;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Outcome of a call to {@link ProcessAutomation#setValuesTransactional(java.util.Map, String)}.
 * Carries per-write validation results, a global commit/rollback flag, the rollback reason when
 * applicable, and the JSON schema version.
 *
 * <p>
 * A transactional batch follows three phases:
 * </p>
 * <ol>
 * <li><strong>Validate</strong> — every requested write is sent through the
 * {@link WriteValidatorRegistry}. If any validator returns an
 * {@link WriteValidationResult.Severity#ERROR ERROR} result, the batch is rejected without touching
 * the simulation.</li>
 * <li><strong>Apply</strong> — every write is applied via
 * {@link ProcessAutomation#setVariableValue(String, double, String)}.</li>
 * <li><strong>Run and verify</strong> — the underlying {@code ProcessSystem} or
 * {@code ProcessModel} is run. If {@code run()} throws, the previous values (snapshotted in phase
 * 0) are restored and a final {@code run()} is attempted to leave the simulation in a coherent
 * state.</li>
 * </ol>
 *
 * <p>
 * The {@link #toJson()} method renders this result with stable field names ({@code schemaVersion},
 * {@code committed}, {@code rolledBack}, {@code rollbackReason}, {@code rollbackCategory},
 * {@code writes}) so agents and MCP clients can branch on the outcome without parsing free text.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class TransactionalBatchResult implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Classifies why a batch was rolled back (or null when committed). */
  public enum RollbackCategory {
    /** Pre-flight validation failed for one or more writes. */
    VALIDATION_FAILED,
    /** A write was syntactically valid but applying it threw an exception. */
    APPLY_FAILED,
    /** Writes applied successfully but {@code run()} threw an exception. */
    RUN_FAILED
  }

  /** Per-write outcome inside a transactional batch. */
  public static final class WriteOutcome implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final String address;
    private final double requestedValue;
    private final String unit;
    private final Double previousValue;
    private final WriteValidationResult validation;
    private final boolean applied;
    private final String error;

    /**
     * Creates a write outcome.
     *
     * @param address the address that was requested
     * @param requestedValue the value that was requested
     * @param unit the unit of measure of {@code requestedValue}, or null for the default
     * @param previousValue the value read before the batch began, or null when unreadable
     * @param validation the validation result for this write; never null
     * @param applied true if the write was actually pushed into the simulation
     * @param error the error message if the apply phase threw, otherwise null
     */
    public WriteOutcome(String address, double requestedValue, String unit, Double previousValue,
        WriteValidationResult validation, boolean applied, String error) {
      this.address = address;
      this.requestedValue = requestedValue;
      this.unit = unit;
      this.previousValue = previousValue;
      this.validation = validation;
      this.applied = applied;
      this.error = error;
    }

    /**
     * Returns the address that was requested.
     *
     * @return the address
     */
    public String getAddress() {
      return address;
    }

    /**
     * Returns the value that was requested.
     *
     * @return the requested value
     */
    public double getRequestedValue() {
      return requestedValue;
    }

    /**
     * Returns the unit of measure of {@link #getRequestedValue()}, or {@code null} for the default.
     *
     * @return the unit or null
     */
    public String getUnit() {
      return unit;
    }

    /**
     * Returns the value read from the simulation before the batch began, or {@code null} when the
     * value was unreadable (for example a new INPUT variable that had not yet been set).
     *
     * @return the previous value or null
     */
    public Double getPreviousValue() {
      return previousValue;
    }

    /**
     * Returns the validation result for this write; never null.
     *
     * @return the validation result
     */
    public WriteValidationResult getValidation() {
      return validation;
    }

    /**
     * Returns whether the write was actually pushed into the simulation. A write may pass
     * validation and still be {@code false} when an earlier write in the same batch failed.
     *
     * @return true if the write was applied
     */
    public boolean isApplied() {
      return applied;
    }

    /**
     * Returns the error message if the apply phase threw an exception for this write, otherwise
     * {@code null}.
     *
     * @return the error or null
     */
    public String getError() {
      return error;
    }

    /**
     * Renders this outcome as a JSON object.
     *
     * @return the JSON object
     */
    public JsonObject toJson() {
      JsonObject obj = new JsonObject();
      obj.addProperty("address", address);
      obj.addProperty("requestedValue", requestedValue);
      if (unit != null) {
        obj.addProperty("unit", unit);
      }
      if (previousValue != null) {
        obj.addProperty("previousValue", previousValue);
      }
      obj.add("validation", validation.toJson());
      obj.addProperty("applied", applied);
      if (error != null) {
        obj.addProperty("error", error);
      }
      return obj;
    }
  }

  private final boolean committed;
  private final RollbackCategory rollbackCategory;
  private final String rollbackReason;
  private final List<WriteOutcome> writes;

  /**
   * Creates a transactional batch result.
   *
   * @param committed true when all phases succeeded and the writes are persisted
   * @param rollbackCategory the rollback category when {@code committed} is false, otherwise null
   * @param rollbackReason a human-readable explanation when {@code committed} is false, otherwise
   *        null
   * @param writes the per-write outcomes in the order they were requested; never null
   */
  public TransactionalBatchResult(boolean committed, RollbackCategory rollbackCategory,
      String rollbackReason, List<WriteOutcome> writes) {
    this.committed = committed;
    this.rollbackCategory = rollbackCategory;
    this.rollbackReason = rollbackReason;
    this.writes = writes != null ? Collections.unmodifiableList(new ArrayList<WriteOutcome>(writes))
        : Collections.<WriteOutcome>emptyList();
  }

  /**
   * Returns whether the batch was committed (true) or rolled back (false).
   *
   * @return true when the writes are persisted
   */
  public boolean isCommitted() {
    return committed;
  }

  /**
   * Convenience inverse of {@link #isCommitted()}.
   *
   * @return true when the batch was rolled back
   */
  public boolean isRolledBack() {
    return !committed;
  }

  /**
   * Returns the rollback category when the batch was rolled back, otherwise {@code null}.
   *
   * @return the rollback category or null
   */
  public RollbackCategory getRollbackCategory() {
    return rollbackCategory;
  }

  /**
   * Returns the rollback reason when the batch was rolled back, otherwise {@code null}.
   *
   * @return the rollback reason or null
   */
  public String getRollbackReason() {
    return rollbackReason;
  }

  /**
   * Returns the per-write outcomes in the order they were requested.
   *
   * @return an unmodifiable list of write outcomes
   */
  public List<WriteOutcome> getWrites() {
    return writes;
  }

  /**
   * Renders this batch result as a JSON object.
   *
   * @return a JSON object with stable field names
   */
  public JsonObject toJson() {
    JsonObject obj = new JsonObject();
    obj.addProperty("schemaVersion", ProcessAutomation.SCHEMA_VERSION);
    obj.addProperty("committed", committed);
    obj.addProperty("rolledBack", !committed);
    if (rollbackCategory != null) {
      obj.addProperty("rollbackCategory", rollbackCategory.name());
    }
    if (rollbackReason != null) {
      obj.addProperty("rollbackReason", rollbackReason);
    }
    JsonArray arr = new JsonArray();
    for (WriteOutcome wo : writes) {
      arr.add(wo.toJson());
    }
    obj.add("writes", arr);
    return obj;
  }

  /**
   * Convenience constructor for a successful commit.
   *
   * @param writes per-write outcomes
   * @return a committed batch result
   */
  public static TransactionalBatchResult committed(List<WriteOutcome> writes) {
    return new TransactionalBatchResult(true, null, null, writes);
  }

  /**
   * Convenience constructor for a rollback.
   *
   * @param category the rollback category; never null
   * @param reason the rollback reason; never null
   * @param writes per-write outcomes
   * @return a rolled-back batch result
   */
  public static TransactionalBatchResult rolledBack(RollbackCategory category, String reason,
      List<WriteOutcome> writes) {
    return new TransactionalBatchResult(false, category, reason, writes);
  }

  /**
   * Returns an empty mutable map intended for callers that want to build their own outcome list
   * without depending on internal LinkedHashMap usage.
   *
   * @return a new empty map
   */
  public static Map<String, Double> newRequestMap() {
    return new LinkedHashMap<String, Double>();
  }
}
