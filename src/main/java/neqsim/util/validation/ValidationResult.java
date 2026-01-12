package neqsim.util.validation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Common validation result container for NeqSim components.
 * 
 * <p>
 * Used to collect validation errors and warnings with remediation advice. Compatible with AI agents
 * that can use error messages to self-correct simulation setup.
 * </p>
 * 
 * <h2>Usage:</h2>
 * 
 * <pre>
 * {@code
 * ValidationResult result = system.validate();
 * if (!result.isValid()) {
 *   System.out.println(result.getReport());
 *   // AI can parse errors and apply fixes
 * }
 * }
 * </pre>
 * 
 * @author NeqSim
 * @version 1.0
 */
public class ValidationResult {

  /**
   * Severity level for validation issues.
   */
  public enum Severity {
    /** Prevents execution - must be fixed. */
    CRITICAL,
    /** May cause incorrect results. */
    MAJOR,
    /** Unexpected but not fatal. */
    MINOR,
    /** Informational note. */
    INFO
  }

  /**
   * A single validation issue.
   */
  public static class ValidationIssue {
    private final Severity severity;
    private final String category;
    private final String message;
    private final String remediation;

    /**
     * Create a validation issue.
     * 
     * @param severity issue severity
     * @param category issue category (e.g., "thermo", "equipment", "stream")
     * @param message description of the issue
     * @param remediation how to fix the issue
     */
    public ValidationIssue(Severity severity, String category, String message, String remediation) {
      this.severity = severity;
      this.category = category;
      this.message = message;
      this.remediation = remediation;
    }

    public Severity getSeverity() {
      return severity;
    }

    public String getCategory() {
      return category;
    }

    public String getMessage() {
      return message;
    }

    public String getRemediation() {
      return remediation;
    }

    @Override
    public String toString() {
      return String.format("[%s] %s: %s | Fix: %s", severity, category, message, remediation);
    }
  }

  private final List<ValidationIssue> issues = new ArrayList<>();
  private final String validatedObject;
  private long validationTimeMs = 0;

  /**
   * Create a validation result for the specified object.
   * 
   * @param validatedObject name or description of validated object
   */
  public ValidationResult(String validatedObject) {
    this.validatedObject = validatedObject;
  }

  /**
   * Create a validation result with default object name.
   */
  public ValidationResult() {
    this("Unknown");
  }

  /**
   * Add a critical error.
   * 
   * @param category issue category
   * @param message error description
   * @param remediation how to fix
   */
  public void addError(String category, String message, String remediation) {
    issues.add(new ValidationIssue(Severity.CRITICAL, category, message, remediation));
  }

  /**
   * Add an error with simple message (no remediation).
   * 
   * @param message error description
   */
  public void addError(String message) {
    issues.add(new ValidationIssue(Severity.CRITICAL, "general", message, ""));
  }

  /**
   * Add a warning.
   * 
   * @param category issue category
   * @param message warning description
   * @param suggestion how to improve
   */
  public void addWarning(String category, String message, String suggestion) {
    issues.add(new ValidationIssue(Severity.MAJOR, category, message, suggestion));
  }

  /**
   * Add a warning with simple message.
   * 
   * @param message warning description
   */
  public void addWarning(String message) {
    issues.add(new ValidationIssue(Severity.MAJOR, "general", message, ""));
  }

  /**
   * Add an informational note.
   * 
   * @param message info description
   */
  public void addInfo(String message) {
    issues.add(new ValidationIssue(Severity.INFO, "info", message, ""));
  }

  /**
   * Check if validation passed (no critical or major errors).
   * 
   * @return true if valid
   */
  public boolean isValid() {
    return issues.stream().noneMatch(i -> i.getSeverity() == Severity.CRITICAL);
  }

  /**
   * Check if validation is ready for execution (no errors, warnings OK).
   * 
   * @return true if ready
   */
  public boolean isReady() {
    return isValid();
  }

  /**
   * Check if validation has any warnings.
   * 
   * @return true if warnings present
   */
  public boolean hasWarnings() {
    return issues.stream().anyMatch(i -> i.getSeverity() == Severity.MAJOR);
  }

  /**
   * Get all validation issues.
   * 
   * @return unmodifiable list of issues
   */
  public List<ValidationIssue> getIssues() {
    return Collections.unmodifiableList(issues);
  }

  /**
   * Get only error issues.
   * 
   * @return list of errors
   */
  public List<ValidationIssue> getErrors() {
    List<ValidationIssue> errors = new ArrayList<>();
    for (ValidationIssue issue : issues) {
      if (issue.getSeverity() == Severity.CRITICAL) {
        errors.add(issue);
      }
    }
    return errors;
  }

  /**
   * Get only warning issues.
   * 
   * @return list of warnings
   */
  public List<ValidationIssue> getWarnings() {
    List<ValidationIssue> warnings = new ArrayList<>();
    for (ValidationIssue issue : issues) {
      if (issue.getSeverity() == Severity.MAJOR) {
        warnings.add(issue);
      }
    }
    return warnings;
  }

  /**
   * Set the validation time.
   * 
   * @param timeMs time in milliseconds
   */
  public void setValidationTimeMs(long timeMs) {
    this.validationTimeMs = timeMs;
  }

  /**
   * Get the validation time.
   * 
   * @return time in milliseconds
   */
  public long getValidationTimeMs() {
    return validationTimeMs;
  }

  /**
   * Get a formatted validation report.
   * 
   * @return formatted report string
   */
  public String getReport() {
    StringBuilder sb = new StringBuilder();
    sb.append("=== Validation Report: ").append(validatedObject).append(" ===\n\n");
    sb.append("Status: ");
    if (!isValid()) {
      sb.append("FAILED\n\n");
    } else if (hasWarnings()) {
      sb.append("PASSED WITH WARNINGS\n\n");
    } else {
      sb.append("PASSED\n\n");
    }

    if (!issues.isEmpty()) {
      for (ValidationIssue issue : issues) {
        sb.append(issue).append("\n");
      }
    } else {
      sb.append("No issues found.\n");
    }

    if (validationTimeMs > 0) {
      sb.append("\nValidation time: ").append(validationTimeMs).append(" ms\n");
    }

    return sb.toString();
  }

  @Override
  public String toString() {
    return getReport();
  }
}
