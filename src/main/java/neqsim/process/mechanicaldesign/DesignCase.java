package neqsim.process.mechanicaldesign;

/**
 * Enumeration of design cases for process equipment sizing.
 *
 * <p>
 * Different operating scenarios require different sizing considerations. This enum defines standard
 * design cases used in field development projects.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public enum DesignCase {

  /**
   * Normal operating conditions. Steady-state production at expected rates.
   */
  NORMAL("Normal", "Steady-state production at expected rates", 1.0),

  /**
   * Maximum operating conditions. Highest expected throughput within design envelope.
   */
  MAXIMUM("Maximum", "Highest expected throughput within design envelope", 1.15),

  /**
   * Minimum operating conditions. Lowest stable operating point (turndown).
   */
  MINIMUM("Minimum", "Lowest stable operating point (turndown)", 0.4),

  /**
   * Startup conditions. Transient conditions during plant startup.
   */
  STARTUP("Startup", "Transient conditions during plant startup", 0.0),

  /**
   * Shutdown conditions. Controlled shutdown scenario.
   */
  SHUTDOWN("Shutdown", "Controlled shutdown scenario", 0.0),

  /**
   * Upset conditions. Abnormal operation within safety limits.
   */
  UPSET("Upset", "Abnormal operation within safety limits", 1.25),

  /**
   * Emergency conditions. Emergency operations or relief scenarios.
   */
  EMERGENCY("Emergency", "Emergency operations or relief scenarios", 1.5),

  /**
   * Winter conditions. Cold weather operations.
   */
  WINTER("Winter", "Cold weather operations", 1.0),

  /**
   * Summer conditions. Hot weather operations.
   */
  SUMMER("Summer", "Hot weather operations", 1.0),

  /**
   * Early life production. Beginning of field life with maximum rates.
   */
  EARLY_LIFE("Early Life", "Beginning of field life with maximum rates", 1.2),

  /**
   * Late life production. Declining field with reduced rates.
   */
  LATE_LIFE("Late Life", "Declining field with reduced rates", 0.6);

  private final String displayName;
  private final String description;
  private final double typicalLoadFactor;

  /**
   * Constructor.
   *
   * @param displayName human-readable name
   * @param description case description
   * @param typicalLoadFactor typical load factor relative to normal (1.0 = 100%)
   */
  DesignCase(String displayName, String description, double typicalLoadFactor) {
    this.displayName = displayName;
    this.description = description;
    this.typicalLoadFactor = typicalLoadFactor;
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
   * Get case description.
   *
   * @return description
   */
  public String getDescription() {
    return description;
  }

  /**
   * Get typical load factor relative to normal conditions.
   *
   * @return load factor (1.0 = 100% of normal)
   */
  public double getTypicalLoadFactor() {
    return typicalLoadFactor;
  }

  /**
   * Check if this case requires safety relief sizing.
   *
   * @return true if relief sizing is needed
   */
  public boolean requiresReliefSizing() {
    return this == UPSET || this == EMERGENCY;
  }

  /**
   * Check if this is a sizing-critical case.
   *
   * @return true if this case drives equipment sizing
   */
  public boolean isSizingCritical() {
    return this == MAXIMUM || this == EARLY_LIFE || this == UPSET;
  }

  /**
   * Check if this is a turndown case.
   *
   * @return true if this is a turndown scenario
   */
  public boolean isTurndownCase() {
    return this == MINIMUM || this == LATE_LIFE;
  }

  @Override
  public String toString() {
    return String.format("%s (%.0f%% load)", displayName, typicalLoadFactor * 100);
  }
}
