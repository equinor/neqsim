package neqsim.process.safety.overpressure;

/**
 * Outcome classification for a single TR3001 overpressure-protection compliance check.
 *
 * @author ESOL
 * @version 1.0
 */
public enum ComplianceStatus {
  /** The requirement is demonstrably satisfied by the study result. */
  PASS("Pass"),
  /** The requirement is not satisfied and the design is non-compliant. */
  FAIL("Fail"),
  /** Informational finding that records context without a pass/fail verdict. */
  INFO("Info"),
  /** The requirement needs engineering review or additional evidence to close. */
  NEEDS_REVIEW("Needs review");

  private final String label;

  /**
   * Creates a compliance status.
   *
   * @param label the human-readable label
   */
  ComplianceStatus(String label) {
    this.label = label;
  }

  /**
   * Gets the human-readable label.
   *
   * @return the label
   */
  public String getLabel() {
    return label;
  }
}
