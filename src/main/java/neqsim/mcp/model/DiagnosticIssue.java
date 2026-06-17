package neqsim.mcp.model;

import com.google.gson.JsonObject;

/**
 * A diagnostic issue found during validation or simulation.
 *
 * <p>
 * Unifies the issue format used by {@code Validator.Issue} and {@code SimulationResult.ErrorDetail}
 * into a single model for MCP responses. Each issue has a severity, machine-readable code,
 * human-readable message, and an optional remediation hint.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class DiagnosticIssue {

  private final String severity;
  private final String code;
  private final String message;
  private final String remediation;

  /**
   * Creates a diagnostic issue.
   *
   * @param severity "error" or "warning"
   * @param code machine-readable issue code (e.g., "UNKNOWN_COMPONENT")
   * @param message human-readable description
   * @param remediation how to fix the issue, or null
   */
  public DiagnosticIssue(String severity, String code, String message, String remediation) {
    this.severity = severity;
    this.code = code;
    this.message = message;
    this.remediation = remediation;
  }

  /**
   * Creates an error-level issue.
   *
   * @param code the issue code
   * @param message the description
   * @param remediation how to fix
   * @return the issue
   */
  public static DiagnosticIssue error(String code, String message, String remediation) {
    return new DiagnosticIssue("error", code, message, remediation);
  }

  /**
   * Creates a warning-level issue.
   *
   * @param code the issue code
   * @param message the description
   * @param remediation how to fix
   * @return the issue
   */
  public static DiagnosticIssue warning(String code, String message, String remediation) {
    return new DiagnosticIssue("warning", code, message, remediation);
  }

  /**
   * Gets the severity level.
   *
   * @return "error" or "warning"
   */
  public String getSeverity() {
    return severity;
  }

  /**
   * Gets the machine-readable issue code.
   *
   * @return the code
   */
  public String getCode() {
    return code;
  }

  /**
   * Gets the human-readable message.
   *
   * @return the message
   */
  public String getMessage() {
    return message;
  }

  /**
   * Gets the remediation hint.
   *
   * @return the remediation, or null
   */
  public String getRemediation() {
    return remediation;
  }

  /**
   * Returns true if this is an error-level issue.
   *
   * @return true if severity is "error"
   */
  public boolean isError() {
    return "error".equals(severity);
  }

  /**
   * Converts this issue to a JsonObject.
   *
   * @return JSON representation
   */
  public JsonObject toJson() {
    JsonObject obj = new JsonObject();
    obj.addProperty("severity", severity);
    obj.addProperty("code", code);
    obj.addProperty("message", message);
    if (remediation != null) {
      obj.addProperty("remediation", remediation);
    }
    return obj;
  }

  @Override
  public String toString() {
    return "[" + severity + "] " + code + ": " + message
        + (remediation != null ? " | Fix: " + remediation : "");
  }
}
