package neqsim.process.processmodel;

import java.io.Serializable;
import com.google.gson.JsonObject;

/**
 * Immutable record of the outcome of running a single unit operation within a {@link ProcessSystem}
 * or {@link ProcessModel}.
 *
 * <p>
 * Each instance captures whether the unit ran successfully, the unit's type, an optional error
 * message when it failed, and the owning process area (for multi-area {@link ProcessModel} runs).
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class UnitRunStatus implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  private final String unitName;
  private final String unitType;
  private final boolean success;
  private final String errorMessage;
  private final String areaName;

  /**
   * Creates a new unit run status record.
   *
   * @param unitName the unit operation name
   * @param unitType the unit operation type (simple class name), or null if unknown
   * @param success true if the unit ran without error
   * @param errorMessage the error message if the unit failed, or null on success
   * @param areaName the owning process area name, or null for a single-area process
   */
  public UnitRunStatus(String unitName, String unitType, boolean success, String errorMessage,
      String areaName) {
    this.unitName = unitName;
    this.unitType = unitType;
    this.success = success;
    this.errorMessage = errorMessage;
    this.areaName = areaName;
  }

  /**
   * Returns the unit operation name.
   *
   * @return the unit name
   */
  public String getUnitName() {
    return unitName;
  }

  /**
   * Returns the unit operation type (simple class name).
   *
   * @return the unit type, or null if unknown
   */
  public String getUnitType() {
    return unitType;
  }

  /**
   * Returns whether the unit ran successfully.
   *
   * @return true if successful
   */
  public boolean isSuccess() {
    return success;
  }

  /**
   * Returns the error message if the unit failed.
   *
   * @return the error message, or null on success
   */
  public String getErrorMessage() {
    return errorMessage;
  }

  /**
   * Returns the owning process area name.
   *
   * @return the area name, or null for a single-area process
   */
  public String getAreaName() {
    return areaName;
  }

  /**
   * Serializes this record to a {@link JsonObject}.
   *
   * @return a JSON object describing this unit run status
   */
  public JsonObject toJsonObject() {
    JsonObject obj = new JsonObject();
    obj.addProperty("unitName", unitName);
    obj.addProperty("unitType", unitType);
    obj.addProperty("success", success);
    obj.addProperty("errorMessage", errorMessage);
    if (areaName != null) {
      obj.addProperty("areaName", areaName);
    }
    return obj;
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    return (areaName == null ? "" : areaName + "::") + unitName + " [" + (success ? "OK" : "FAILED")
        + "]";
  }
}
