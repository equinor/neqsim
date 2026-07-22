package neqsim.process.safety.overpressure;

import java.io.Serializable;

/**
 * Immutable record of a single TR3001 / API STD 521 compliance finding produced by {@link TR3001ComplianceChecker}.
 *
 * <p>
 * Each finding ties a normative requirement (identified by its TR3001 special-requirement number and/or API STD 521
 * section) to a {@link ComplianceStatus} verdict and a human-readable explanation, so the overpressure-protection study
 * produces an auditable compliance record.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class ComplianceFinding implements Serializable {
  private static final long serialVersionUID = 1L;

  private final String requirementId;
  private final String title;
  private final ComplianceStatus status;
  private final String detail;

  /**
   * Creates an immutable compliance finding.
   *
   * @param requirementId the requirement identifier (for example "TR3001 SR-26504 / API 521 4.4.13"); not null
   * @param title a short title for the requirement; not null
   * @param status the compliance verdict; not null
   * @param detail a human-readable explanation of the verdict; not null
   */
  public ComplianceFinding(String requirementId, String title, ComplianceStatus status, String detail) {
    this.requirementId = requirementId;
    this.title = title;
    this.status = status;
    this.detail = detail;
  }

  /**
   * Gets the requirement identifier.
   *
   * @return the requirement identifier
   */
  public String getRequirementId() {
    return requirementId;
  }

  /**
   * Gets the requirement title.
   *
   * @return the requirement title
   */
  public String getTitle() {
    return title;
  }

  /**
   * Gets the compliance verdict.
   *
   * @return the compliance status
   */
  public ComplianceStatus getStatus() {
    return status;
  }

  /**
   * Gets the explanation of the verdict.
   *
   * @return the detail string
   */
  public String getDetail() {
    return detail;
  }

  /**
   * Returns a single-line text summary of the finding.
   *
   * @return the formatted finding
   */
  @Override
  public String toString() {
    return "[" + status.getLabel() + "] " + requirementId + " - " + title + ": " + detail;
  }
}
