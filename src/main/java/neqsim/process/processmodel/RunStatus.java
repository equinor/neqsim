package neqsim.process.processmodel;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Structured outcome of the most recent run of a {@link ProcessSystem} or {@link ProcessModel}.
 *
 * <p>
 * A {@code RunStatus} answers the question "did the last run succeed, and if not, which unit failed and why?" without
 * forcing the caller to catch and parse a {@link RuntimeException}. It records a per-unit outcome list and, on failure,
 * the first failed unit's name and error message.
 * </p>
 *
 * <p>
 * The recorder methods ({@link #reset()}, {@link #recordSuccess}, {@link #recordFailure},
 * {@link #markComplete(boolean)}) are intended to be driven by the owning process during a run; the accessor methods
 * and {@link #toJson()} are intended for agents and reporting.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class RunStatus implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Schema version for the JSON representation. */
  public static final String SCHEMA_VERSION = "1.0";

  private boolean completed = false;
  private boolean success = false;
  private String failedUnitName = null;
  private String failedUnitError = null;
  private final List<UnitRunStatus> units = new ArrayList<UnitRunStatus>();

  /**
   * Resets this status to begin recording a new run. Clears all per-unit entries and failure information.
   */
  public void reset() {
    completed = false;
    success = false;
    failedUnitName = null;
    failedUnitError = null;
    units.clear();
  }

  /**
   * Records that a unit ran successfully.
   *
   * @param unitName the unit operation name
   * @param unitType the unit operation type (simple class name), or null if unknown
   */
  public void recordSuccess(String unitName, String unitType) {
    recordSuccess(unitName, unitType, null);
  }

  /**
   * Records that a unit ran successfully within a named area.
   *
   * @param unitName the unit operation name
   * @param unitType the unit operation type (simple class name), or null if unknown
   * @param areaName the owning process area name, or null for a single-area process
   */
  public void recordSuccess(String unitName, String unitType, String areaName) {
    units.add(new UnitRunStatus(unitName, unitType, true, null, areaName));
  }

  /**
   * Records that a unit failed to run. The first recorded failure is reported as the run's failed unit.
   *
   * @param unitName     the unit operation name
   * @param unitType     the unit operation type (simple class name), or null if unknown
   * @param errorMessage the error message describing the failure
   */
  public void recordFailure(String unitName, String unitType, String errorMessage) {
    recordFailure(unitName, unitType, errorMessage, null);
  }

  /**
   * Records that a unit failed to run within a named area. The first recorded failure is reported as the run's failed
   * unit.
   *
   * @param unitName     the unit operation name
   * @param unitType     the unit operation type (simple class name), or null if unknown
   * @param errorMessage the error message describing the failure
   * @param areaName     the owning process area name, or null for a single-area process
   */
  public void recordFailure(String unitName, String unitType, String errorMessage, String areaName) {
    units.add(new UnitRunStatus(unitName, unitType, false, errorMessage, areaName));
    if (failedUnitName == null) {
      failedUnitName = unitName;
      failedUnitError = errorMessage;
    }
  }

  /**
   * Marks the run as complete with the given overall outcome. A run is considered successful only if the supplied flag
   * is true and no unit failure was recorded.
   *
   * @param overallSuccess the caller-determined overall success flag
   */
  public void markComplete(boolean overallSuccess) {
    completed = true;
    success = overallSuccess && failedUnitName == null;
  }

  /**
   * Returns whether the run has completed.
   *
   * @return true if a run has finished (successfully or not)
   */
  public boolean isCompleted() {
    return completed;
  }

  /**
   * Returns whether the most recent run succeeded.
   *
   * @return true if the run completed without any unit failure
   */
  public boolean isSuccess() {
    return success && failedUnitName == null;
  }

  /**
   * Returns the name of the first unit that failed.
   *
   * @return the failed unit name, or null if no unit failed
   */
  public String getFailedUnitName() {
    return failedUnitName;
  }

  /**
   * Returns the error message of the first unit that failed.
   *
   * @return the failed unit error message, or null if no unit failed
   */
  public String getFailedUnitError() {
    return failedUnitError;
  }

  /**
   * Returns the per-unit run status entries.
   *
   * @return an unmodifiable list of unit run statuses
   */
  public List<UnitRunStatus> getUnits() {
    return Collections.unmodifiableList(units);
  }

  /**
   * Serializes this run status to a {@link JsonObject}.
   *
   * @return a JSON object describing the run outcome
   */
  public JsonObject toJsonObject() {
    JsonObject root = new JsonObject();
    root.addProperty("schemaVersion", SCHEMA_VERSION);
    root.addProperty("completed", completed);
    root.addProperty("success", isSuccess());
    root.addProperty("failedUnitName", failedUnitName);
    root.addProperty("failedUnitError", failedUnitError);
    root.addProperty("unitCount", units.size());
    JsonArray arr = new JsonArray();
    for (UnitRunStatus u : units) {
      arr.add(u.toJsonObject());
    }
    root.add("units", arr);
    return root;
  }

  /**
   * Serializes this run status to a JSON string.
   *
   * @return a JSON string describing the run outcome
   */
  public String toJson() {
    return toJsonObject().toString();
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    if (!completed) {
      return "RunStatus[not run]";
    }
    if (isSuccess()) {
      return "RunStatus[success, " + units.size() + " units]";
    }
    return "RunStatus[FAILED at " + failedUnitName + ": " + failedUnitError + "]";
  }
}
