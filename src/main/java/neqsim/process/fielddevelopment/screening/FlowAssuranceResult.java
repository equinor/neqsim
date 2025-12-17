package neqsim.process.fielddevelopment.screening;

/**
 * Result classification for flow assurance screening.
 *
 * <p>
 * Represents a three-tier envelope classification for flow assurance parameters such as hydrate
 * risk, wax deposition, and corrosion.
 *
 * @author ESOL
 * @version 1.0
 */
public enum FlowAssuranceResult {
  /**
   * Operating conditions are clearly within safe envelope. No flow assurance interventions
   * required.
   */
  PASS("Pass", "Within safe operating envelope"),

  /**
   * Operating conditions are near envelope boundaries. Flow assurance mitigation may be required
   * under some scenarios.
   */
  MARGINAL("Marginal", "Near envelope boundary - mitigation may be required"),

  /**
   * Operating conditions exceed safe envelope. Flow assurance mitigation is mandatory.
   */
  FAIL("Fail", "Outside safe envelope - mitigation required");

  private final String displayName;
  private final String description;

  FlowAssuranceResult(String displayName, String description) {
    this.displayName = displayName;
    this.description = description;
  }

  /**
   * Gets the display name.
   *
   * @return display name
   */
  public String getDisplayName() {
    return displayName;
  }

  /**
   * Gets the description.
   *
   * @return description
   */
  public String getDescription() {
    return description;
  }

  /**
   * Checks if this result indicates safe operation.
   *
   * @return true if PASS
   */
  public boolean isSafe() {
    return this == PASS;
  }

  /**
   * Checks if this result requires attention.
   *
   * @return true if MARGINAL or FAIL
   */
  public boolean needsAttention() {
    return this != PASS;
  }

  /**
   * Checks if this result blocks development.
   *
   * @return true if FAIL
   */
  public boolean isBlocking() {
    return this == FAIL;
  }

  /**
   * Combines two results, returning the more severe.
   *
   * @param other other result
   * @return combined (worst-case) result
   */
  public FlowAssuranceResult combine(FlowAssuranceResult other) {
    if (this == FAIL || other == FAIL) {
      return FAIL;
    }
    if (this == MARGINAL || other == MARGINAL) {
      return MARGINAL;
    }
    return PASS;
  }
}
