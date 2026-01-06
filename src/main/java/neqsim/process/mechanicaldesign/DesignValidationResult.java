package neqsim.process.mechanicaldesign;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents validation results from design calculations.
 *
 * <p>
 * Collects warnings, errors, and info messages during design validation. Provides methods to check
 * overall status and generate reports.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public class DesignValidationResult implements Serializable {

  private static final long serialVersionUID = 1L;

  /**
   * Severity levels for validation messages.
   */
  public enum Severity {
    /** Informational message. */
    INFO,
    /** Warning - design may need review. */
    WARNING,
    /** Error - design does not meet requirements. */
    ERROR,
    /** Critical - design cannot proceed. */
    CRITICAL
  }

  /**
   * Represents a single validation message.
   */
  public static class ValidationMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    private final Severity severity;
    private final String category;
    private final String equipmentName;
    private final String message;
    private final String remediation;

    /**
     * Constructor.
     *
     * @param severity message severity
     * @param category message category (e.g., "Pressure Rating", "Standards Compliance")
     * @param equipmentName name of affected equipment
     * @param message the validation message
     * @param remediation suggested fix or action
     */
    public ValidationMessage(Severity severity, String category, String equipmentName,
        String message, String remediation) {
      this.severity = severity;
      this.category = category;
      this.equipmentName = equipmentName;
      this.message = message;
      this.remediation = remediation;
    }

    /**
     * Get message severity.
     *
     * @return severity level
     */
    public Severity getSeverity() {
      return severity;
    }

    /**
     * Get message category.
     *
     * @return category string
     */
    public String getCategory() {
      return category;
    }

    /**
     * Get affected equipment name.
     *
     * @return equipment name
     */
    public String getEquipmentName() {
      return equipmentName;
    }

    /**
     * Get the validation message.
     *
     * @return message text
     */
    public String getMessage() {
      return message;
    }

    /**
     * Get suggested remediation.
     *
     * @return remediation text
     */
    public String getRemediation() {
      return remediation;
    }

    @Override
    public String toString() {
      return String.format("[%s] %s - %s: %s", severity, equipmentName, category, message);
    }
  }

  private final List<ValidationMessage> messages = new ArrayList<ValidationMessage>();
  private final Map<String, Object> metrics = new LinkedHashMap<String, Object>();
  private boolean hasRun = false;

  /**
   * Default constructor.
   */
  public DesignValidationResult() {
    // Default constructor
  }

  /**
   * Add a validation message.
   *
   * @param severity message severity
   * @param category message category
   * @param equipmentName affected equipment
   * @param message the message
   * @param remediation suggested fix
   * @return this instance for chaining
   */
  public DesignValidationResult addMessage(Severity severity, String category, String equipmentName,
      String message, String remediation) {
    messages.add(new ValidationMessage(severity, category, equipmentName, message, remediation));
    hasRun = true;
    return this;
  }

  /**
   * Add an info message.
   *
   * @param equipmentName affected equipment
   * @param message the message
   * @return this instance for chaining
   */
  public DesignValidationResult addInfo(String equipmentName, String message) {
    return addMessage(Severity.INFO, "General", equipmentName, message, "");
  }

  /**
   * Add a warning message.
   *
   * @param category message category
   * @param equipmentName affected equipment
   * @param message the message
   * @param remediation suggested fix
   * @return this instance for chaining
   */
  public DesignValidationResult addWarning(String category, String equipmentName, String message,
      String remediation) {
    return addMessage(Severity.WARNING, category, equipmentName, message, remediation);
  }

  /**
   * Add an error message.
   *
   * @param category message category
   * @param equipmentName affected equipment
   * @param message the message
   * @param remediation suggested fix
   * @return this instance for chaining
   */
  public DesignValidationResult addError(String category, String equipmentName, String message,
      String remediation) {
    return addMessage(Severity.ERROR, category, equipmentName, message, remediation);
  }

  /**
   * Add a critical error message.
   *
   * @param category message category
   * @param equipmentName affected equipment
   * @param message the message
   * @param remediation suggested fix
   * @return this instance for chaining
   */
  public DesignValidationResult addCritical(String category, String equipmentName, String message,
      String remediation) {
    return addMessage(Severity.CRITICAL, category, equipmentName, message, remediation);
  }

  /**
   * Add a metric value.
   *
   * @param name metric name
   * @param value metric value
   * @return this instance for chaining
   */
  public DesignValidationResult addMetric(String name, Object value) {
    metrics.put(name, value);
    return this;
  }

  /**
   * Get all validation messages.
   *
   * @return list of messages
   */
  public List<ValidationMessage> getMessages() {
    return new ArrayList<ValidationMessage>(messages);
  }

  /**
   * Get messages filtered by severity.
   *
   * @param severity severity to filter by
   * @return list of messages with specified severity
   */
  public List<ValidationMessage> getMessages(Severity severity) {
    List<ValidationMessage> filtered = new ArrayList<ValidationMessage>();
    for (ValidationMessage msg : messages) {
      if (msg.getSeverity() == severity) {
        filtered.add(msg);
      }
    }
    return filtered;
  }

  /**
   * Get metrics map.
   *
   * @return metrics as map
   */
  public Map<String, Object> getMetrics() {
    return new LinkedHashMap<String, Object>(metrics);
  }

  /**
   * Check if validation passed (no errors or critical issues).
   *
   * @return true if no errors or critical issues
   */
  public boolean isValid() {
    for (ValidationMessage msg : messages) {
      if (msg.getSeverity() == Severity.ERROR || msg.getSeverity() == Severity.CRITICAL) {
        return false;
      }
    }
    return true;
  }

  /**
   * Check if there are any warnings.
   *
   * @return true if warnings exist
   */
  public boolean hasWarnings() {
    for (ValidationMessage msg : messages) {
      if (msg.getSeverity() == Severity.WARNING) {
        return true;
      }
    }
    return false;
  }

  /**
   * Check if there are any errors.
   *
   * @return true if errors exist
   */
  public boolean hasErrors() {
    for (ValidationMessage msg : messages) {
      if (msg.getSeverity() == Severity.ERROR || msg.getSeverity() == Severity.CRITICAL) {
        return true;
      }
    }
    return false;
  }

  /**
   * Check if validation has been run.
   *
   * @return true if validation has run
   */
  public boolean hasRun() {
    return hasRun;
  }

  /**
   * Get count of messages by severity.
   *
   * @param severity severity level
   * @return count of messages
   */
  public int getCount(Severity severity) {
    int count = 0;
    for (ValidationMessage msg : messages) {
      if (msg.getSeverity() == severity) {
        count++;
      }
    }
    return count;
  }

  /**
   * Get summary counts of all severities.
   *
   * @return map of severity to count
   */
  public Map<Severity, Integer> getSummaryCounts() {
    Map<Severity, Integer> counts = new LinkedHashMap<Severity, Integer>();
    for (Severity severity : Severity.values()) {
      counts.put(severity, getCount(severity));
    }
    return counts;
  }

  /**
   * Merge another result into this one.
   *
   * @param other the other result to merge
   * @return this instance for chaining
   */
  public DesignValidationResult merge(DesignValidationResult other) {
    if (other != null) {
      messages.addAll(other.messages);
      metrics.putAll(other.metrics);
      if (other.hasRun) {
        hasRun = true;
      }
    }
    return this;
  }

  /**
   * Generate a summary string.
   *
   * @return summary of validation results
   */
  public String getSummary() {
    StringBuilder sb = new StringBuilder();
    sb.append("Design Validation Results:\n");
    sb.append(String.format("  Status: %s\n", isValid() ? "VALID" : "INVALID"));
    sb.append(String.format("  Critical: %d, Errors: %d, Warnings: %d, Info: %d\n",
        getCount(Severity.CRITICAL), getCount(Severity.ERROR), getCount(Severity.WARNING),
        getCount(Severity.INFO)));
    return sb.toString();
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(getSummary());
    if (!messages.isEmpty()) {
      sb.append("\nMessages:\n");
      for (ValidationMessage msg : messages) {
        sb.append("  ").append(msg.toString()).append("\n");
        if (msg.getRemediation() != null && !msg.getRemediation().isEmpty()) {
          sb.append("    Fix: ").append(msg.getRemediation()).append("\n");
        }
      }
    }
    return sb.toString();
  }
}
