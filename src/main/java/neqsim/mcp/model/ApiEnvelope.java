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
 * <p> Wraps a typed result payload with status, warnings, and errors in a consistent format. This
 * provides a dual interface — call {@link #toJson()} for the string-based MCP protocol, or use
 * {@link #getData()} for typed Java access. </p>
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

  private static final Gson GSON =
      new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();

  private final String status;
  private final T data;
  private final List<DiagnosticIssue> errors;
  private final List<String> warnings;

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
   * Adds a warning to this envelope.
   *
   * @param warning the warning message
   */
  public void addWarning(String warning) {
    warnings.add(warning);
  }

  /**
   * Converts this envelope to a JSON string.
   *
   * @return JSON string
   */
  public String toJson() {
    JsonObject root = new JsonObject();
    root.addProperty("status", status);

    if (data != null) {
      root.add("data", GSON.toJsonTree(data));
    }

    if (!errors.isEmpty()) {
      JsonArray errArray = new JsonArray();
      for (DiagnosticIssue issue : errors) {
        errArray.add(issue.toJson());
      }
      root.add("errors", errArray);
    }

    if (!warnings.isEmpty()) {
      JsonArray warnArray = new JsonArray();
      for (String w : warnings) {
        warnArray.add(w);
      }
      root.add("warnings", warnArray);
    }

    return GSON.toJson(root);
  }
}
