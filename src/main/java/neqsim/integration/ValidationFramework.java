package neqsim.integration;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Core framework for validating NeqSim simulations before execution.
 * 
 * <p>
 * <b>Purpose:</b> Detect setup errors early (missing mixing rules, invalid parameters, unconverged
 * state) before long-running simulations. Enables AI agents to self-correct and provides developers
 * with clear error messages.
 * 
 * <p>
 * <b>Usage:</b>
 * 
 * <pre>
 * SystemInterface system = new SystemSrkEos(...);
 * system.addComponent("methane", 0.5);
 * // Missing: system.setMixingRule("classic")
 * 
 * ValidationResult result = system.validate();
 * if (!result.isReady()) {
 *   System.err.println(result.getErrorsSummary());
 *   // Output: "Validation failed: Mixing rule not set for SystemSrkEos"
 * }
 * </pre>
 */
public class ValidationFramework {

  /**
   * Validation error with severity and remediation advice.
   */
  public static class ValidationError {
    public enum Severity {
      CRITICAL, // Prevents execution
      MAJOR, // May cause incorrect results
      MINOR // Unexpected but not fatal
    }

    private final Severity severity;
    private final String category; // "thermo", "equipment", "stream"
    private final String message;
    private final String remediation; // How to fix

    public ValidationError(Severity severity, String category, String message, String remediation) {
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
      return String.format("[%s] %s: %s\nFix: %s", severity, category, message, remediation);
    }
  }

  /**
   * Validation warning for potentially problematic but non-blocking issues.
   */
  public static class ValidationWarning {
    private final String category;
    private final String message;
    private final String suggestion;

    public ValidationWarning(String category, String message, String suggestion) {
      this.category = category;
      this.message = message;
      this.suggestion = suggestion;
    }

    public String getCategory() {
      return category;
    }

    public String getMessage() {
      return message;
    }

    public String getSuggestion() {
      return suggestion;
    }

    @Override
    public String toString() {
      return String.format("[%s] %s\nSuggestion: %s", category, message, suggestion);
    }
  }

  /**
   * Result of validation, containing errors, warnings, and readiness status.
   */
  public static class ValidationResult {
    private final List<ValidationError> errors;
    private final List<ValidationWarning> warnings;
    private final long validationTimeMs;
    private final String validatedObject; // Object type or name

    public ValidationResult(String validatedObject) {
      this.validatedObject = validatedObject;
      this.errors = new ArrayList<>();
      this.warnings = new ArrayList<>();
      this.validationTimeMs = System.currentTimeMillis();
    }

    public void addError(ValidationError error) {
      errors.add(error);
    }

    public void addWarning(ValidationWarning warning) {
      warnings.add(warning);
    }

    public boolean isReady() {
      // Only CRITICAL errors block execution
      return errors.stream().noneMatch(e -> e.getSeverity() == ValidationError.Severity.CRITICAL);
    }

    public List<ValidationError> getErrors() {
      return Collections.unmodifiableList(errors);
    }

    public List<ValidationWarning> getWarnings() {
      return Collections.unmodifiableList(warnings);
    }

    public List<ValidationError> getCriticalErrors() {
      return errors.stream().filter(e -> e.getSeverity() == ValidationError.Severity.CRITICAL)
          .collect(Collectors.toList());
    }

    public long getValidationTimeMs() {
      return validationTimeMs;
    }

    public String getErrorsSummary() {
      StringBuilder sb = new StringBuilder();
      sb.append("Validation failed for: ").append(validatedObject).append("\n");
      if (errors.isEmpty()) {
        sb.append("No critical errors, but warnings exist:\n");
      }
      for (ValidationError error : errors) {
        sb.append("  ").append(error).append("\n");
      }
      return sb.toString();
    }

    public String getWarningsSummary() {
      if (warnings.isEmpty()) {
        return "No warnings.";
      }
      StringBuilder sb = new StringBuilder();
      for (ValidationWarning warning : warnings) {
        sb.append("  ").append(warning).append("\n");
      }
      return sb.toString();
    }

    @Override
    public String toString() {
      return String.format(
          "ValidationResult{object=%s, ready=%s, errors=%d, warnings=%d, time=%dms}",
          validatedObject, isReady(), errors.size(), warnings.size(), validationTimeMs);
    }
  }

  /**
   * Interface for validatable objects (systems, streams, equipment).
   */
  public interface Validatable {
    /**
     * Validate this object's state and configuration.
     * 
     * @return ValidationResult with errors, warnings, and readiness status
     */
    ValidationResult validate();

    /**
     * Get a human-readable name for this object (used in error messages).
     *
     * @return the validation name
     */
    String getValidationName();
  }

  /**
   * Context for validation, allowing cross-object checks.
   */
  public static class ValidationContext {
    private final Map<String, Object> context;
    private final List<String> checkedObjects;

    public ValidationContext() {
      this.context = new HashMap<>();
      this.checkedObjects = new ArrayList<>();
    }

    public void put(String key, Object value) {
      context.put(key, value);
    }

    public Object get(String key) {
      return context.get(key);
    }

    public void recordCheck(String objectName) {
      checkedObjects.add(objectName);
    }

    public boolean hasBeenChecked(String objectName) {
      return checkedObjects.contains(objectName);
    }

    public List<String> getCheckedObjects() {
      return Collections.unmodifiableList(checkedObjects);
    }
  }

  /**
   * Standard error messages for common validation failures.
   */
  public static class CommonErrors {
    public static final String MIXING_RULE_NOT_SET = "Mixing rule not set for thermodynamic system";
    public static final String REMEDIATION_MIXING_RULE =
        "Call system.setMixingRule(\"classic\") or system.setMixingRule(int rulenumber)";

    public static final String NO_COMPONENTS = "No components added to thermodynamic system";
    public static final String REMEDIATION_NO_COMPONENTS =
        "Add at least one component: system.addComponent(\"methane\", 0.5)";

    public static final String DATABASE_NOT_CREATED = "Component database not created";
    public static final String REMEDIATION_DATABASE =
        "Call system.createDatabase(true) after adding components";

    public static final String FEED_STREAM_NOT_SET = "No feed stream connected to equipment";
    public static final String REMEDIATION_FEED_STREAM =
        "Call equipment.addFeedStream(stream) or pass stream to constructor";

    public static final String INVALID_PRESSURE = "Pressure value is invalid (negative or zero)";
    public static final String REMEDIATION_INVALID_PRESSURE =
        "Set positive pressure: stream.setPressure(value) where value > 0";

    public static final String INVALID_TEMPERATURE =
        "Temperature value is invalid (below absolute zero)";
    public static final String REMEDIATION_INVALID_TEMPERATURE =
        "Set temperature above absolute zero: stream.setTemperature(value) where value > 0 K";

    public static final String COMPOSITION_SUM_NOT_UNITY =
        "Component mole fractions do not sum to ~1.0";
    public static final String REMEDIATION_COMPOSITION =
        "Normalize mole fractions so they sum to 1.0";

    public static final String SYSTEM_NOT_INITIALIZED = "Thermodynamic system not initialized";
    public static final String REMEDIATION_SYSTEM_INIT =
        "Call system.init(0) after setting composition";

    public static final String STREAM_NOT_RUN = "Stream has not been executed";
    public static final String REMEDIATION_STREAM_RUN = "Call stream.run() to calculate properties";
  }

  /**
   * Utility for custom validation rules.
   */
  public static class ValidationBuilder {
    private final ValidationResult result;

    public ValidationBuilder(String objectName) {
      this.result = new ValidationResult(objectName);
    }

    public ValidationBuilder checkTrue(boolean condition, String errorMsg, String remediation) {
      if (!condition) {
        result.addError(new ValidationError(ValidationError.Severity.CRITICAL, "validation",
            errorMsg, remediation));
      }
      return this;
    }

    public ValidationBuilder checkNotNull(Object obj, String fieldName) {
      if (obj == null) {
        result.addError(new ValidationError(ValidationError.Severity.CRITICAL, "validation",
            fieldName + " is null", "Initialize " + fieldName + " before validation"));
      }
      return this;
    }

    public ValidationBuilder checkRange(double value, double min, double max, String fieldName) {
      if (value < min || value > max) {
        result.addError(new ValidationError(ValidationError.Severity.MAJOR, "range",
            fieldName + " is out of range [" + min + ", " + max + "]",
            "Set " + fieldName + " within valid range"));
      }
      return this;
    }

    public ValidationBuilder addWarning(String category, String message, String suggestion) {
      result.addWarning(new ValidationWarning(category, message, suggestion));
      return this;
    }

    public ValidationResult build() {
      return result;
    }
  }

  /**
   * Convenience method for creating a validation builder.
   *
   * @param objectName the name of the object being validated
   * @return a new ValidationBuilder instance
   */
  public static ValidationBuilder validate(String objectName) {
    return new ValidationBuilder(objectName);
  }

  /**
   * Composite validation for multiple validatable objects.
   */
  public static class CompositeValidator {
    private final List<Validatable> objects;
    private final String compositeId;

    public CompositeValidator(String compositeId, Validatable... objects) {
      this.compositeId = compositeId;
      this.objects = Arrays.asList(objects);
    }

    public ValidationResult validateAll() {
      ValidationResult composite = new ValidationResult(compositeId);

      for (Validatable obj : objects) {
        ValidationResult result = obj.validate();
        for (ValidationError error : result.getErrors()) {
          composite.addError(error);
        }
        for (ValidationWarning warning : result.getWarnings()) {
          composite.addWarning(warning);
        }
      }

      return composite;
    }

    public ValidationResult validateAny() {
      // Returns success if ANY object validates successfully
      ValidationResult composite = new ValidationResult(compositeId);

      boolean anySucceeded = false;
      for (Validatable obj : objects) {
        ValidationResult result = obj.validate();
        if (result.isReady()) {
          anySucceeded = true;
          break;
        }
      }

      if (!anySucceeded) {
        composite.addError(new ValidationError(ValidationError.Severity.CRITICAL, "composite",
            "No objects in the composite validation passed", "Fix errors in at least one object"));
      }

      return composite;
    }
  }
}
