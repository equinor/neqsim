package neqsim.process.logic.sis;

/**
 * Represents a voting logic pattern for Safety Instrumented Systems (SIS).
 * 
 * <p>
 * Voting logic determines how multiple redundant sensors or detectors are combined to make a safety
 * decision. Common patterns include:
 * <ul>
 * <li>1oo1 (1 out of 1): Single sensor must trip</li>
 * <li>1oo2 (1 out of 2): At least 1 of 2 sensors must trip</li>
 * <li>2oo2 (2 out of 2): Both sensors must trip</li>
 * <li>2oo3 (2 out of 3): At least 2 of 3 sensors must trip (common for high reliability)</li>
 * <li>2oo4 (2 out of 4): At least 2 of 4 sensors must trip</li>
 * </ul>
 * 
 * <p>
 * Voting patterns balance:
 * <ul>
 * <li>Spurious trip rate (false alarms)</li>
 * <li>Probability of failure on demand (missed real events)</li>
 * <li>System availability vs. safety integrity</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
public enum VotingLogic {
  /**
   * 1 out of 1 - Single sensor/detector. High spurious trip rate, lowest cost.
   */
  ONE_OUT_OF_ONE("1oo1", 1, 1),

  /**
   * 1 out of 2 - At least one of two must trip. Lower spurious trips, higher safety.
   */
  ONE_OUT_OF_TWO("1oo2", 1, 2),

  /**
   * 2 out of 2 - Both sensors must trip. Very low spurious trips, but lower safety integrity.
   */
  TWO_OUT_OF_TWO("2oo2", 2, 2),

  /**
   * 2 out of 3 - Standard for high reliability applications. Good balance of safety and
   * availability.
   */
  TWO_OUT_OF_THREE("2oo3", 2, 3),

  /**
   * 2 out of 4 - High redundancy, very high availability.
   */
  TWO_OUT_OF_FOUR("2oo4", 2, 4),

  /**
   * 3 out of 4 - Higher safety integrity than 2oo4.
   */
  THREE_OUT_OF_FOUR("3oo4", 3, 4);

  private final String notation;
  private final int requiredTrips;
  private final int totalSensors;

  /**
   * Creates a voting logic pattern.
   *
   * @param notation standard notation (e.g., "2oo3")
   * @param requiredTrips number of sensors that must trip
   * @param totalSensors total number of sensors
   */
  VotingLogic(String notation, int requiredTrips, int totalSensors) {
    this.notation = notation;
    this.requiredTrips = requiredTrips;
    this.totalSensors = totalSensors;
  }

  /**
   * Gets the standard notation for this voting pattern.
   *
   * @return notation string (e.g., "2oo3")
   */
  public String getNotation() {
    return notation;
  }

  /**
   * Gets the number of sensors required to trip for activation.
   *
   * @return required trip count
   */
  public int getRequiredTrips() {
    return requiredTrips;
  }

  /**
   * Gets the total number of sensors in the voting group.
   *
   * @return total sensor count
   */
  public int getTotalSensors() {
    return totalSensors;
  }

  /**
   * Evaluates if the voting condition is met given the number of tripped sensors.
   *
   * @param trippedCount number of sensors currently tripped
   * @return true if voting condition is satisfied
   */
  public boolean evaluate(int trippedCount) {
    return trippedCount >= requiredTrips;
  }

  @Override
  public String toString() {
    return notation + " (" + requiredTrips + " out of " + totalSensors + " must trip)";
  }
}
