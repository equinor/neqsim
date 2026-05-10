package neqsim.process.safety.opendrain;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One requirement-level assessment in an open-drain review.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public class OpenDrainAssessment implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Assessment status values used in report output. */
  public enum Status {
    /** Requirement is met. */
    PASS,
    /** Requirement has missing evidence or a minor concern. */
    WARNING,
    /** Requirement is not met. */
    FAIL,
    /** Requirement is informational only. */
    INFO,
    /** Requirement is not applicable to the reviewed area. */
    NOT_APPLICABLE
  }

  /** Requirement identifier. */
  private final String requirementId;
  /** Standard identifier. */
  private final String standard;
  /** Clause identifier. */
  private final String clause;
  /** Assessment status. */
  private final Status status;
  /** Severity string. */
  private final String severity;
  /** Human-readable finding message. */
  private final String message;
  /** Recommended action. */
  private final String recommendation;
  /** Calculation and evidence details. */
  private final Map<String, Object> details = new LinkedHashMap<String, Object>();

  /**
   * Creates an assessment.
   *
   * @param requirementId stable requirement identifier
   * @param standard standard identifier
   * @param clause clause identifier
   * @param status requirement status
   * @param severity severity string such as LOW, MEDIUM, HIGH, or CRITICAL
   * @param message finding message
   * @param recommendation recommended action
   */
  public OpenDrainAssessment(String requirementId, String standard, String clause, Status status,
      String severity, String message, String recommendation) {
    this.requirementId = normalize(requirementId);
    this.standard = normalize(standard);
    this.clause = normalize(clause);
    this.status = status == null ? Status.INFO : status;
    this.severity = normalize(severity).isEmpty() ? "LOW" : normalize(severity);
    this.message = normalize(message);
    this.recommendation = normalize(recommendation);
  }

  /**
   * Creates a passing assessment.
   *
   * @param requirementId stable requirement identifier
   * @param clause clause identifier
   * @param message finding message
   * @param recommendation recommended action
   * @return passing assessment
   */
  public static OpenDrainAssessment pass(String requirementId, String clause, String message,
      String recommendation) {
    return new OpenDrainAssessment(requirementId, OpenDrainReviewEngine.NORSOK_S001, clause,
        Status.PASS, "LOW", message, recommendation);
  }

  /**
   * Creates a warning assessment.
   *
   * @param requirementId stable requirement identifier
   * @param clause clause identifier
   * @param severity severity string
   * @param message finding message
   * @param recommendation recommended action
   * @return warning assessment
   */
  public static OpenDrainAssessment warning(String requirementId, String clause, String severity,
      String message, String recommendation) {
    return new OpenDrainAssessment(requirementId, OpenDrainReviewEngine.NORSOK_S001, clause,
        Status.WARNING, severity, message, recommendation);
  }

  /**
   * Creates a failing assessment.
   *
   * @param requirementId stable requirement identifier
   * @param clause clause identifier
   * @param severity severity string
   * @param message finding message
   * @param recommendation recommended action
   * @return failing assessment
   */
  public static OpenDrainAssessment fail(String requirementId, String clause, String severity,
      String message, String recommendation) {
    return new OpenDrainAssessment(requirementId, OpenDrainReviewEngine.NORSOK_S001, clause,
        Status.FAIL, severity, message, recommendation);
  }

  /**
   * Creates an informational assessment.
   *
   * @param requirementId stable requirement identifier
   * @param clause clause identifier
   * @param message finding message
   * @param recommendation recommended action
   * @return informational assessment
   */
  public static OpenDrainAssessment info(String requirementId, String clause, String message,
      String recommendation) {
    return new OpenDrainAssessment(requirementId, OpenDrainReviewEngine.NORSOK_S001, clause,
        Status.INFO, "LOW", message, recommendation);
  }

  /**
   * Creates a not-applicable assessment.
   *
   * @param requirementId stable requirement identifier
   * @param clause clause identifier
   * @param message not-applicable reason
   * @return not-applicable assessment
   */
  public static OpenDrainAssessment notApplicable(String requirementId, String clause,
      String message) {
    return new OpenDrainAssessment(requirementId, OpenDrainReviewEngine.NORSOK_S001, clause,
        Status.NOT_APPLICABLE, "LOW", message, "No action required for this review item.");
  }

  /**
   * Adds a detail value.
   *
   * @param key detail key
   * @param value detail value
   * @return this assessment for fluent construction
   */
  public OpenDrainAssessment addDetail(String key, Object value) {
    if (key != null && !key.trim().isEmpty() && value != null && isJsonSafe(value)) {
      details.put(key, value);
    }
    return this;
  }

  /**
   * Tests whether a detail value can be written as standards-compliant JSON.
   *
   * @param value value to test
   * @return true when the value is safe for JSON serialization
   */
  private static boolean isJsonSafe(Object value) {
    if (value instanceof Double) {
      Double number = (Double) value;
      return !number.isNaN() && !number.isInfinite();
    }
    if (value instanceof Float) {
      Float number = (Float) value;
      return !number.isNaN() && !number.isInfinite();
    }
    return true;
  }

  /**
   * Gets the requirement identifier.
   *
   * @return requirement identifier
   */
  public String getRequirementId() {
    return requirementId;
  }

  /**
   * Gets the standard identifier.
   *
   * @return standard identifier
   */
  public String getStandard() {
    return standard;
  }

  /**
   * Gets the clause identifier.
   *
   * @return clause identifier
   */
  public String getClause() {
    return clause;
  }

  /**
   * Gets the assessment status.
   *
   * @return assessment status
   */
  public Status getStatus() {
    return status;
  }

  /**
   * Tests whether this assessment is failing.
   *
   * @return true when status is FAIL
   */
  public boolean isFailing() {
    return Status.FAIL.equals(status);
  }

  /**
   * Tests whether this assessment is a warning.
   *
   * @return true when status is WARNING
   */
  public boolean isWarning() {
    return Status.WARNING.equals(status);
  }

  /**
   * Converts this assessment to a JSON-ready map.
   *
   * @return ordered map representation
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("requirementId", requirementId);
    map.put("standard", standard);
    map.put("clause", clause);
    map.put("status", status.name());
    map.put("severity", severity);
    map.put("message", message);
    map.put("recommendation", recommendation);
    map.put("details", new LinkedHashMap<String, Object>(details));
    return map;
  }

  /**
   * Normalizes nullable text.
   *
   * @param value value to normalize
   * @return trimmed text or an empty string
   */
  private static String normalize(String value) {
    return value == null ? "" : value.trim();
  }
}
