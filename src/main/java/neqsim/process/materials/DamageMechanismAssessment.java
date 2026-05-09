package neqsim.process.materials;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Result from one material degradation or compatibility mechanism check.
 *
 * <p>
 * Examples include CO2 corrosion, sour service, chloride SCC, oxygen corrosion, dense CO2, hydrogen
 * embrittlement, HTHA, ammonia SCC, CUI, erosion-corrosion, MIC, galvanic corrosion, and
 * fatigue/FIV screening.
 * </p>
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public class DamageMechanismAssessment implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Mechanism name. */
  private final String mechanism;

  /** Standard or recommended practice used for the assessment. */
  private final String standard;

  /** Status string: PASS, WARNING, FAIL, or INFO. */
  private final String status;

  /** Severity string: LOW, MEDIUM, HIGH, or CRITICAL. */
  private final String severity;

  /** Human-readable finding message. */
  private final String message;

  /** Recommended action or mitigation. */
  private final String recommendation;

  /** Calculation details and trace values. */
  private final Map<String, Object> details = new LinkedHashMap<String, Object>();

  /**
   * Creates a mechanism assessment.
   *
   * @param mechanism mechanism name
   * @param standard standard or recommended practice
   * @param status assessment status
   * @param severity severity level
   * @param message finding message
   * @param recommendation recommended action
   */
  public DamageMechanismAssessment(String mechanism, String standard, String status,
      String severity, String message, String recommendation) {
    this.mechanism = mechanism == null ? "" : mechanism;
    this.standard = standard == null ? "" : standard;
    this.status = status == null ? "INFO" : status;
    this.severity = severity == null ? "LOW" : severity;
    this.message = message == null ? "" : message;
    this.recommendation = recommendation == null ? "" : recommendation;
  }

  /**
   * Creates a passing assessment.
   *
   * @param mechanism mechanism name
   * @param standard standard or recommended practice
   * @param message finding message
   * @param recommendation recommended action
   * @return passing assessment
   */
  public static DamageMechanismAssessment pass(String mechanism, String standard, String message,
      String recommendation) {
    return new DamageMechanismAssessment(mechanism, standard, "PASS", "LOW", message,
        recommendation);
  }

  /**
   * Creates an informational assessment.
   *
   * @param mechanism mechanism name
   * @param standard standard or recommended practice
   * @param message finding message
   * @param recommendation recommended action
   * @return informational assessment
   */
  public static DamageMechanismAssessment info(String mechanism, String standard, String message,
      String recommendation) {
    return new DamageMechanismAssessment(mechanism, standard, "INFO", "LOW", message,
        recommendation);
  }

  /**
   * Creates a warning assessment.
   *
   * @param mechanism mechanism name
   * @param standard standard or recommended practice
   * @param severity severity level
   * @param message finding message
   * @param recommendation recommended action
   * @return warning assessment
   */
  public static DamageMechanismAssessment warning(String mechanism, String standard,
      String severity, String message, String recommendation) {
    return new DamageMechanismAssessment(mechanism, standard, "WARNING", severity, message,
        recommendation);
  }

  /**
   * Creates a failing assessment.
   *
   * @param mechanism mechanism name
   * @param standard standard or recommended practice
   * @param severity severity level
   * @param message finding message
   * @param recommendation recommended action
   * @return failing assessment
   */
  public static DamageMechanismAssessment fail(String mechanism, String standard, String severity,
      String message, String recommendation) {
    return new DamageMechanismAssessment(mechanism, standard, "FAIL", severity, message,
        recommendation);
  }

  /**
   * Adds a trace detail to this assessment.
   *
   * @param key detail key
   * @param value detail value
   * @return this assessment for fluent construction
   */
  public DamageMechanismAssessment addDetail(String key, Object value) {
    if (key != null && !key.trim().isEmpty() && value != null) {
      details.put(key, value);
    }
    return this;
  }

  /**
   * Gets the mechanism name.
   *
   * @return mechanism name
   */
  public String getMechanism() {
    return mechanism;
  }

  /**
   * Gets the standard reference.
   *
   * @return standard reference
   */
  public String getStandard() {
    return standard;
  }

  /**
   * Gets the status.
   *
   * @return status string
   */
  public String getStatus() {
    return status;
  }

  /**
   * Tests if the assessment is failing.
   *
   * @return true if status is FAIL
   */
  public boolean isFailing() {
    return "FAIL".equals(status);
  }

  /**
   * Tests if the assessment is a warning.
   *
   * @return true if status is WARNING
   */
  public boolean isWarning() {
    return "WARNING".equals(status);
  }

  /**
   * Converts the assessment to a JSON-ready map.
   *
   * @return map representation of the assessment
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("mechanism", mechanism);
    map.put("standard", standard);
    map.put("status", status);
    map.put("severity", severity);
    map.put("message", message);
    map.put("recommendation", recommendation);
    map.put("details", new LinkedHashMap<String, Object>(details));
    return map;
  }
}
