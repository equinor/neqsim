package neqsim.process.mechanicaldesign;

/**
 * Enumeration of field development design phases with associated accuracy requirements.
 *
 * <p>
 * Each phase represents a stage in the project lifecycle with specific deliverables and accuracy
 * expectations for sizing, weight, and cost estimates.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public enum DesignPhase {

  /**
   * Screening/Opportunity phase. Rough order of magnitude estimates. Accuracy: ±40-50%.
   */
  SCREENING("Screening", 0.40, 0.50, "Order of magnitude estimates for initial screening"),

  /**
   * Concept Select phase. Preliminary sizing and layout. Accuracy: ±30%.
   */
  CONCEPT_SELECT("Concept Select", 0.25, 0.35, "Preliminary sizing for concept evaluation"),

  /**
   * Pre-FEED phase. More refined estimates for concept selection. Accuracy: ±25%.
   */
  PRE_FEED("Pre-FEED", 0.20, 0.30, "Refined estimates for concept selection"),

  /**
   * FEED (Front End Engineering Design) phase. Detailed sizing for sanction. Accuracy: ±15-20%.
   */
  FEED("FEED", 0.15, 0.20, "Detailed design for project sanction"),

  /**
   * Detail Design phase. Final design for procurement. Accuracy: ±10%.
   */
  DETAIL_DESIGN("Detail Design", 0.10, 0.15, "Final design for procurement and construction"),

  /**
   * As-Built phase. Actual installed equipment data. Accuracy: ±5%.
   */
  AS_BUILT("As-Built", 0.00, 0.05, "Actual installed equipment data");

  private final String displayName;
  private final double minAccuracy;
  private final double maxAccuracy;
  private final String description;

  /**
   * Constructor.
   *
   * @param displayName human-readable name
   * @param minAccuracy minimum expected accuracy (as fraction, e.g., 0.15 = 15%)
   * @param maxAccuracy maximum expected accuracy
   * @param description phase description
   */
  DesignPhase(String displayName, double minAccuracy, double maxAccuracy, String description) {
    this.displayName = displayName;
    this.minAccuracy = minAccuracy;
    this.maxAccuracy = maxAccuracy;
    this.description = description;
  }

  /**
   * Get human-readable display name.
   *
   * @return display name
   */
  public String getDisplayName() {
    return displayName;
  }

  /**
   * Get minimum expected accuracy as fraction.
   *
   * @return minimum accuracy (e.g., 0.15 for 15%)
   */
  public double getMinAccuracy() {
    return minAccuracy;
  }

  /**
   * Get maximum expected accuracy as fraction.
   *
   * @return maximum accuracy (e.g., 0.20 for 20%)
   */
  public double getMaxAccuracy() {
    return maxAccuracy;
  }

  /**
   * Get accuracy range as a formatted string.
   *
   * @return formatted accuracy range (e.g., "±15-20%")
   */
  public String getAccuracyRange() {
    if (minAccuracy == maxAccuracy || minAccuracy == 0) {
      return String.format("±%.0f%%", maxAccuracy * 100);
    }
    return String.format("±%.0f-%.0f%%", minAccuracy * 100, maxAccuracy * 100);
  }

  /**
   * Get phase description.
   *
   * @return description
   */
  public String getDescription() {
    return description;
  }

  /**
   * Check if this phase requires detailed design standards compliance.
   *
   * @return true if detailed compliance checking is required
   */
  public boolean requiresDetailedCompliance() {
    return this == FEED || this == DETAIL_DESIGN || this == AS_BUILT;
  }

  /**
   * Check if this phase requires full mechanical design calculations.
   *
   * @return true if full calculations are required
   */
  public boolean requiresFullMechanicalDesign() {
    return this == FEED || this == DETAIL_DESIGN;
  }

  @Override
  public String toString() {
    return String.format("%s (%s)", displayName, getAccuracyRange());
  }
}
