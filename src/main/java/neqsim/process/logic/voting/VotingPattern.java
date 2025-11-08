package neqsim.process.logic.voting;

/**
 * Standard voting patterns for redundant sensors or conditions.
 * 
 * <p>
 * This enum defines common voting patterns used across all logic contexts (not just SIS). It's
 * compatible with the SIS-specific VotingLogic enum but more generic.
 * 
 * @author ESOL
 * @version 1.0
 */
public enum VotingPattern {
  /**
   * 1 out of 1 - Single input must be true.
   */
  ONE_OUT_OF_ONE("1oo1", 1, 1),

  /**
   * 1 out of 2 - At least 1 of 2 inputs must be true.
   */
  ONE_OUT_OF_TWO("1oo2", 1, 2),

  /**
   * 2 out of 2 - Both inputs must be true.
   */
  TWO_OUT_OF_TWO("2oo2", 2, 2),

  /**
   * 2 out of 3 - At least 2 of 3 inputs must be true (standard for high reliability).
   */
  TWO_OUT_OF_THREE("2oo3", 2, 3),

  /**
   * 2 out of 4 - At least 2 of 4 inputs must be true.
   */
  TWO_OUT_OF_FOUR("2oo4", 2, 4),

  /**
   * 3 out of 4 - At least 3 of 4 inputs must be true.
   */
  THREE_OUT_OF_FOUR("3oo4", 3, 4);

  private final String notation;
  private final int required;
  private final int total;

  VotingPattern(String notation, int required, int total) {
    this.notation = notation;
    this.required = required;
    this.total = total;
  }

  /**
   * Gets the standard notation.
   *
   * @return notation (e.g., "2oo3")
   */
  public String getNotation() {
    return notation;
  }

  /**
   * Gets the required number of true inputs.
   *
   * @return required count
   */
  public int getRequiredTrue() {
    return required;
  }

  /**
   * Gets the total number of inputs expected.
   *
   * @return total sensor count
   */
  public int getTotalSensors() {
    return total;
  }

  /**
   * Evaluates if voting condition is met.
   *
   * @param trueCount number of inputs that are true
   * @return true if voting satisfied
   */
  public boolean evaluate(int trueCount) {
    return trueCount >= required;
  }

  @Override
  public String toString() {
    return notation;
  }
}
