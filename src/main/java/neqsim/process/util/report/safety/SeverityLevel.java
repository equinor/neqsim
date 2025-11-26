package neqsim.process.util.report.safety;

/**
 * Represents the severity level of a deviation detected in a safety report.
 */
public enum SeverityLevel {
  /** Metric is within acceptable limits. */
  NORMAL,
  /** Metric is drifting towards the configured limits. */
  WARNING,
  /** Metric is outside the configured limits and requires immediate action. */
  CRITICAL;

  /**
   * Combine two severities keeping the most critical one.
   *
   * @param other severity to combine with
   * @return the highest severity
   */
  public SeverityLevel combine(SeverityLevel other) {
    if (other == null) {
      return this;
    }
    return values()[Math.max(ordinal(), other.ordinal())];
  }
}
