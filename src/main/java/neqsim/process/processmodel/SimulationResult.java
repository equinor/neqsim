package neqsim.process.processmodel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Structured result of a simulation build or execution.
 *
 * <p> Provides a standardized response format for web API integration including status, errors,
 * warnings, and simulation results in JSON. Designed for external Python callers and web services.
 * </p>
 *
 * <h3>Success Response:</h3>
 *
 * <pre>{@code { "status": "success", "processSystemName": "json-process", "report": { ... },
 * "warnings": [] } }</pre>
 *
 * <h3>Error Response:</h3>
 *
 * <pre>{@code { "status": "error", "errors": [ { "code": "MISSING_INLET", "message": "Inlet
 * reference 'HP Sep' not found", "unit": "Compressor-1", "remediation": "Ensure referenced unit is
 * defined before this unit" } ], "warnings": ["Property 'power' not found on Cooler-1"] } }</pre>
 *
 * @author Even Solbraa @version 1.0
 */
public class SimulationResult {

  /**
   * Status of the simulation result.
   */
  public enum Status {
    /** Build and/or run completed successfully. */
    SUCCESS,
    /** Build or run failed with errors. */
    ERROR
  }

  /**
   * Detailed error information for a specific issue.
   */
  public static class ErrorDetail {
    private final String code;
    private final String message;
    private final String unit;
    private final String remediation;

    /**
     * Creates an error detail.
     *
     * @param code error code (e.g., "MISSING_INLET", "FLUID_ERROR")
     * @param message human-readable error description
     * @param unit name of the equipment that caused the error (nullable)
     * @param remediation actionable fix description
     */
    public ErrorDetail(String code, String message, String unit, String remediation) {
      this.code = code;
      this.message = message;
      this.unit = unit;
      this.remediation = remediation;
    }

    /**
     * Gets the error code.
     *
     * @return the error code
     */
    public String getCode() {
      return code;
    }

    /**
     * Gets the error message.
     *
     * @return the error message
     */
    public String getMessage() {
      return message;
    }

    /**
     * Gets the unit name that caused the error.
     *
     * @return the unit name, or null if not unit-specific
     */
    public String getUnit() {
      return unit;
    }

    /**
     * Gets the remediation advice.
     *
     * @return the remediation string
     */
    public String getRemediation() {
      return remediation;
    }

    /**
     * Converts this error to a JSON object.
     *
     * @return JSON representation
     */
    public JsonObject toJsonObject() {
      JsonObject obj = new JsonObject();
      obj.addProperty("code", code);
      obj.addProperty("message", message);
      if (unit != null) {
        obj.addProperty("unit", unit);
      }
      if (remediation != null) {
        obj.addProperty("remediation", remediation);
      }
      return obj;
    }

    @Override
    public String toString() {
      return "[" + code + "] " + message + (unit != null ? " (unit: " + unit + ")" : "")
          + (remediation != null ? " | Fix: " + remediation : "");
    }
  }

  private final Status status;
  private final transient ProcessSystem processSystem;
  private final String reportJson;
  private final List<ErrorDetail> errors;
  private final List<String> warnings;

  /**
   * Private constructor — use static factory methods.
   *
   * @param status the result status
   * @param processSystem the built process system (nullable on error)
   * @param reportJson the simulation report JSON (nullable if not run)
   * @param errors list of errors
   * @param warnings list of warnings
   */
  private SimulationResult(Status status, ProcessSystem processSystem, String reportJson,
      List<ErrorDetail> errors, List<String> warnings) {
    this.status = status;
    this.processSystem = processSystem;
    this.reportJson = reportJson;
    this.errors = errors != null ? new ArrayList<>(errors) : new ArrayList<>();
    this.warnings = warnings != null ? new ArrayList<>(warnings) : new ArrayList<>();
  }

  /**
   * Creates a success result.
   *
   * @param process the built and optionally run process system
   * @param reportJson the simulation report JSON (nullable if not run)
   * @param warnings list of non-fatal warnings
   * @return the success result
   */
  public static SimulationResult success(ProcessSystem process, String reportJson,
      List<String> warnings) {
    return new SimulationResult(Status.SUCCESS, process, reportJson,
        Collections.<ErrorDetail>emptyList(), warnings);
  }

  /**
   * Creates a failure result with no process system.
   *
   * @param errors the list of errors
   * @param warnings the list of warnings
   * @return the failure result
   */
  public static SimulationResult failure(List<ErrorDetail> errors, List<String> warnings) {
    return new SimulationResult(Status.ERROR, null, null, errors, warnings);
  }

  /**
   * Creates a failure result with a partially built process system.
   *
   * @param process the partially built process system
   * @param errors the list of errors
   * @param warnings the list of warnings
   * @return the failure result
   */
  public static SimulationResult failure(ProcessSystem process, List<ErrorDetail> errors,
      List<String> warnings) {
    return new SimulationResult(Status.ERROR, process, null, errors, warnings);
  }

  /**
   * Creates a single-error failure result.
   *
   * @param code error code
   * @param message error message
   * @param remediation how to fix
   * @return the failure result
   */
  public static SimulationResult error(String code, String message, String remediation) {
    List<ErrorDetail> errors = new ArrayList<>();
    errors.add(new ErrorDetail(code, message, null, remediation));
    return failure(errors, Collections.<String>emptyList());
  }

  /**
   * Returns true if the simulation completed successfully.
   *
   * @return true if status is SUCCESS
   */
  public boolean isSuccess() {
    return status == Status.SUCCESS;
  }

  /**
   * Returns true if the simulation had errors.
   *
   * @return true if status is ERROR
   */
  public boolean isError() {
    return status == Status.ERROR;
  }

  /**
   * Gets the result status.
   *
   * @return the status
   */
  public Status getStatus() {
    return status;
  }

  /**
   * Gets the built ProcessSystem.
   *
   * @return the process system, or null if build failed completely
   */
  public ProcessSystem getProcessSystem() {
    return processSystem;
  }

  /**
   * Gets the simulation report JSON.
   *
   * @return the report JSON string, or null if simulation was not run
   */
  public String getReportJson() {
    return reportJson;
  }

  /**
   * Gets the list of errors.
   *
   * @return unmodifiable list of errors
   */
  public List<ErrorDetail> getErrors() {
    return Collections.unmodifiableList(errors);
  }

  /**
   * Gets the list of warnings.
   *
   * @return unmodifiable list of warnings
   */
  public List<String> getWarnings() {
    return Collections.unmodifiableList(warnings);
  }

  /**
   * Returns true if there are any warnings.
   *
   * @return true if warnings exist
   */
  public boolean hasWarnings() {
    return !warnings.isEmpty();
  }

  /**
   * Converts this result to a structured JSON string suitable for web API responses.
   *
   * @return JSON string
   */
  public String toJson() {
    JsonObject root = new JsonObject();
    root.addProperty("status", status.name().toLowerCase());

    if (processSystem != null) {
      root.addProperty("processSystemName", processSystem.getName());
    }

    // Errors
    if (!errors.isEmpty()) {
      JsonArray errArray = new JsonArray();
      for (ErrorDetail err : errors) {
        errArray.add(err.toJsonObject());
      }
      root.add("errors", errArray);
    }

    // Warnings
    if (!warnings.isEmpty()) {
      JsonArray warnArray = new JsonArray();
      for (String w : warnings) {
        warnArray.add(w);
      }
      root.add("warnings", warnArray);
    }

    // Report (embedded as parsed JSON to avoid double-escaping)
    if (reportJson != null) {
      try {
        root.add("report", com.google.gson.JsonParser.parseString(reportJson));
      } catch (Exception e) {
        root.addProperty("report", reportJson);
      }
    }

    Gson gson =
        new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();
    return gson.toJson(root);
  }

  @Override
  public String toString() {
    if (isSuccess()) {
      return "SimulationResult[SUCCESS"
          + (hasWarnings() ? ", " + warnings.size() + " warnings" : "") + "]";
    } else {
      return "SimulationResult[ERROR, " + errors.size() + " errors"
          + (hasWarnings() ? ", " + warnings.size() + " warnings" : "") + "]";
    }
  }
}
