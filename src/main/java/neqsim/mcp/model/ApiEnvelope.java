package neqsim.mcp.model;

import java.util.ArrayList;
import java.util.List;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Standard API envelope for all MCP runner responses.
 *
 * <p>
 * Wraps a typed result payload with status, warnings, and errors in a consistent format. This
 * provides a dual interface — call {@link #toJson()} for the string-based MCP protocol, or use
 * {@link #getData()} for typed Java access.
 * </p>
 *
 * <h2>Success envelope:</h2>
 *
 * <pre>{@code { "status": "success", "data": { ... }, "warnings": [] } }</pre>
 *
 * <h2>Error envelope:</h2>
 *
 * <pre>{@code { "status": "error", "errors": [{"severity": "error", "code": "...", "message":
 * "..."}], "warnings": [] } }</pre>
 *
 * @param <T> the type of the data payload @author Even Solbraa @version 1.0
 */
public class ApiEnvelope<T> {

  /** Contract version emitted by MCP runners. */
  public static final String API_VERSION = "1.0";

  private static final Gson GSON =
      new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();

  private final String status;
  private final T data;
  private final List<DiagnosticIssue> errors;
  private final List<String> warnings;
  private ResultProvenance provenance;
  private JsonObject validation;
  private JsonObject qualityGate;

  /**
   * Private constructor — use factory methods.
   *
   * @param status "success" or "error"
   * @param data the data payload, or null on error
   * @param errors the error list
   * @param warnings the warning list
   */
  private ApiEnvelope(String status, T data, List<DiagnosticIssue> errors, List<String> warnings) {
    this.status = status;
    this.data = data;
    this.errors = errors != null ? errors : new ArrayList<DiagnosticIssue>();
    this.warnings = warnings != null ? warnings : new ArrayList<String>();
  }

  /**
   * Adds standard contract fields to a JSON response object.
   *
   * @param response the response object to mutate
   * @param toolName the MCP tool name that produced the response
   * @param provenance optional calculation provenance
   * @param validation optional validation block
   * @param qualityGate optional quality-gate block
   */
  public static void applyStandardFields(JsonObject response, String toolName,
      ResultProvenance provenance, JsonObject validation, JsonObject qualityGate) {
    response.addProperty("apiVersion", API_VERSION);
    if (toolName != null && !toolName.trim().isEmpty()) {
      response.addProperty("tool", toolName);
    }

    if (provenance != null && !response.has("provenance")) {
      response.add("provenance", GSON.toJsonTree(provenance));
    }

    if (!response.has("validation")) {
      boolean success = isSuccessfulResponse(response);
      response.add("validation",
          validation != null ? validation
              : validationStatus(success, "runner", success ? "Runner input checks completed"
                  : "Runner returned an error before validation completed"));
    }

    if (!response.has("qualityGate")) {
      boolean success = isSuccessfulResponse(response);
      response.add("qualityGate",
          qualityGate != null ? qualityGate
              : qualityGate(success ? "passed" : "failed",
                  success ? "Calculation completed" : "Calculation failed", true));
    }

    if (!response.has("warnings")) {
      response.add("warnings", new JsonArray());
    }
  }

  /**
   * Builds a standard validation block.
   *
   * @param valid true when validation passed
   * @param phase validation phase name
   * @param message validation summary
   * @return validation JSON object
   */
  public static JsonObject validationStatus(boolean valid, String phase, String message) {
    JsonObject validation = new JsonObject();
    validation.addProperty("valid", valid);
    validation.addProperty("phase", phase);
    validation.addProperty("message", message);
    return validation;
  }

  /**
   * Builds a standard quality-gate block.
   *
   * @param verdict gate verdict such as {@code passed}, {@code warning}, or {@code failed}
   * @param summary short gate summary
   * @param engineeringReviewRequired true if qualified engineering review is still required
   * @return quality-gate JSON object
   */
  public static JsonObject qualityGate(String verdict, String summary,
      boolean engineeringReviewRequired) {
    JsonObject qualityGate = new JsonObject();
    qualityGate.addProperty("verdict", verdict);
    qualityGate.addProperty("summary", summary);
    qualityGate.addProperty("engineeringReviewRequired", engineeringReviewRequired);
    return qualityGate;
  }

  /**
   * Determines if a response object carries a success status.
   *
   * @param response the response object to inspect
   * @return true if {@code status} is {@code success}
   */
  private static boolean isSuccessfulResponse(JsonObject response) {
    return response.has("status") && "success".equals(response.get("status").getAsString());
  }

  /**
   * Creates a success envelope with the given data.
   *
   * @param data the result payload
   * @param <T> the payload type
   * @return the success envelope
   */
  public static <T> ApiEnvelope<T> success(T data) {
    return new ApiEnvelope<T>("success", data, new ArrayList<DiagnosticIssue>(),
        new ArrayList<String>());
  }

  /**
   * Creates a success envelope with data and warnings.
   *
   * @param data the result payload
   * @param warnings the warning messages
   * @param <T> the payload type
   * @return the success envelope
   */
  public static <T> ApiEnvelope<T> success(T data, List<String> warnings) {
    return new ApiEnvelope<T>("success", data, new ArrayList<DiagnosticIssue>(), warnings);
  }

  /**
   * Creates an error envelope with a single issue.
   *
   * @param code the error code
   * @param message the error message
   * @param remediation the fix suggestion
   * @param <T> the payload type
   * @return the error envelope
   */
  public static <T> ApiEnvelope<T> error(String code, String message, String remediation) {
    List<DiagnosticIssue> errors = new ArrayList<DiagnosticIssue>();
    errors.add(DiagnosticIssue.error(code, message, remediation));
    return new ApiEnvelope<T>("error", null, errors, new ArrayList<String>());
  }

  /**
   * Creates an error envelope with multiple issues.
   *
   * @param issues the diagnostic issues
   * @param <T> the payload type
   * @return the error envelope
   */
  public static <T> ApiEnvelope<T> errors(List<DiagnosticIssue> issues) {
    return new ApiEnvelope<T>("error", null, issues, new ArrayList<String>());
  }

  /**
   * Gets the status string.
   *
   * @return "success" or "error"
   */
  public String getStatus() {
    return status;
  }

  /**
   * Returns true if this is a success envelope.
   *
   * @return true if status is "success"
   */
  public boolean isSuccess() {
    return "success".equals(status);
  }

  /**
   * Gets the data payload.
   *
   * @return the data, or null if this is an error envelope
   */
  public T getData() {
    return data;
  }

  /**
   * Gets the error list.
   *
   * @return the errors
   */
  public List<DiagnosticIssue> getErrors() {
    return errors;
  }

  /**
   * Gets the warning list.
   *
   * @return the warnings
   */
  public List<String> getWarnings() {
    return warnings;
  }

  /**
   * Gets the response contract version.
   *
   * @return the API version string
   */
  public String getApiVersion() {
    return API_VERSION;
  }

  /**
   * Gets the calculation provenance.
   *
   * @return the provenance, or null if not attached
   */
  public ResultProvenance getProvenance() {
    return provenance;
  }

  /**
   * Gets the validation block.
   *
   * @return the validation block, or null if not attached
   */
  public JsonObject getValidation() {
    return validation;
  }

  /**
   * Gets the quality-gate block.
   *
   * @return the quality-gate block, or null if not attached
   */
  public JsonObject getQualityGate() {
    return qualityGate;
  }

  /**
   * Adds a warning to this envelope.
   *
   * @param warning the warning message
   */
  public void addWarning(String warning) {
    warnings.add(warning);
  }

  /**
   * Attaches provenance to this envelope.
   *
   * @param provenance the calculation provenance
   * @return this envelope for fluent use
   */
  public ApiEnvelope<T> withProvenance(ResultProvenance provenance) {
    this.provenance = provenance;
    return this;
  }

  /**
   * Attaches a validation block to this envelope.
   *
   * @param validation the validation block
   * @return this envelope for fluent use
   */
  public ApiEnvelope<T> withValidation(JsonObject validation) {
    this.validation = validation;
    return this;
  }

  /**
   * Attaches a quality-gate block to this envelope.
   *
   * @param qualityGate the quality-gate block
   * @return this envelope for fluent use
   */
  public ApiEnvelope<T> withQualityGate(JsonObject qualityGate) {
    this.qualityGate = qualityGate;
    return this;
  }

  /**
   * Converts this envelope to a JSON string.
   *
   * @return JSON string
   */
  public String toJson() {
    JsonObject root = new JsonObject();
    root.addProperty("apiVersion", API_VERSION);
    root.addProperty("status", status);

    if (data != null) {
      root.add("data", GSON.toJsonTree(data));
    }

    if (provenance != null) {
      root.add("provenance", GSON.toJsonTree(provenance));
    }

    if (validation != null) {
      root.add("validation", validation);
    }

    if (qualityGate != null) {
      root.add("qualityGate", qualityGate);
    }

    if (!errors.isEmpty()) {
      JsonArray errArray = new JsonArray();
      for (DiagnosticIssue issue : errors) {
        errArray.add(issue.toJson());
      }
      root.add("errors", errArray);
    }

    JsonArray warnArray = new JsonArray();
    for (String warning : warnings) {
      warnArray.add(warning);
    }
    root.add("warnings", warnArray);

    return GSON.toJson(root);
  }
}
